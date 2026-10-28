package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.function.LongPredicate;
import zombie.characters.IsoGameCharacter;
import zombie.core.textures.ColorInfo;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoMovingObject;
import zombie.vehicles.BaseVehicle;

/**
 * The light each figure a frame draws stands in: people, zombies and animals, by the square they
 * stand on (WorldScene.drawnLight); vehicles, by the game's light on theirs and that light with
 * the viewer's torch left out (TorchLess); corpses, the held figures of the dying and the pieces
 * cut off, by the square they lie or stand on.
 *
 * The game's light of a square is read on the logic thread alone. Each of its reads of a square
 * (IsoGridSquare.isSeen and isCanSee, the lighting's lightInfo and the rest) first brings the
 * square up to date from the lighting engine, through one scratch array the game keeps for every
 * square and into the square's own list of lights. Read from the render thread as well, the two
 * threads wrote over each other's squares, and once the game's own update threw on a light it had
 * not yet made. So a frame gathers it as it is built, for every figure it may draw, and the
 * render thread draws the frame's figures by what was gathered.
 *
 * What is gathered: the square each character the game holds stands on, as its current square and
 * where it is; each vehicle's current square; the square of each corpse the frame's builds lay as
 * a model; each piece kept and still to be drawn; each figure held of a dying character; and the
 * squares the characters of the last WorldScene.SCENES frames stood on. A piece and a held figure
 * are made on the render thread from the frame it draws, which may have been gathered before the
 * piece or the figure was there to be found: they stand where their character stood in it, and
 * the frames that are drawn before one gathered after them have those squares from the characters.
 * A figure asked after that was not gathered is a fault in the gathering, and is thrown.
 *
 * Each frame has its own, filled on the logic thread as it is built and read on the render
 * thread as it is drawn: the frame is filled again only once the render thread has drawn it.
 */
final class FigureLights {

    /** The light on one square. */
    static final class Lit {
        /** The square, or null where there is none: nothing is said of a figure's light there. */
        IsoGridSquare square;
        /** How bright the world draws the square (WorldScene.drawnLight), or NaN where it says nothing. */
        float light;
        boolean outside;
    }

    /** The light on the square a vehicle stands on, the game's and without the torch, where the torch is on it. */
    static final class CarLit {
        /** Whether the square is seen and the viewer's torch, or a vehicle's lamp, is on it. */
        boolean torched;
        float withRed, withGreen, withBlue, withoutRed, withoutGreen, withoutBlue;
    }

    /** No square: nothing is said. */
    static final Lit NONE = new Lit();

    static {
        NONE.light = Float.NaN;
        NONE.outside = true;
    }

    /**
     * The squares the characters of each of the last WorldScene.SCENES gatherings stood on, as keys,
     * oldest first from the one to be filled next. One for each view. Logic thread.
     */
    static final class Stood {
        private final long[][] keys = new long[WorldScene.SCENES][64];
        private final int[] counts = new int[WorldScene.SCENES];
        private int next;

        /** Begins a gathering: the oldest is let go of. */
        void begin() {
            next = (next + 1) % keys.length;
            counts[next] = 0;
        }

        void add(long key) {
            if (counts[next] == keys[next].length) {
                keys[next] = Arrays.copyOf(keys[next], keys[next].length * 2);
            }
            keys[next][counts[next]++] = key;
        }

        /** Lets go of every one, as the map they were on is left. */
        void forget() {
            Arrays.fill(counts, 0);
        }
    }

    private final IdentityHashMap<IsoGameCharacter, Lit> people = new IdentityHashMap<>();
    private final IdentityHashMap<BaseVehicle, CarLit> cars = new IdentityHashMap<>();
    private final LongTable<Lit> squares = new LongTable<>();
    /** What a gathering fills, kept from one to the next and taken in turn. */
    private final List<Lit> lits = new ArrayList<>();
    private final List<CarLit> carLits = new ArrayList<>();
    private int litsTaken, carLitsTaken;
    private final ColorInfo given = new ColorInfo(), withoutTorch = new ColorInfo();
    private final LongTable<Boolean> roomsSeen = new LongTable<>();
    private final List<IsoGridSquare> held = new ArrayList<>();
    private int playerIndex;
    private IsoCell cell;
    private WorldScene scene;
    private final LongPredicate roomSeen = room -> {
        Boolean known = roomsSeen.get(room);
        if (known == null) {
            known = WorldScene.roomSeen(cell, room, playerIndex);
            roomsSeen.put(room, known);
        }
        return known;
    };

    /** A square's place as one number: x in the top half, y in the next three bytes, the level in the last. */
    static long key(int x, int y, int z) {
        return (long) x << 32 | (y & 0xFFFFFFL) << 8 | z & 0xFFL;
    }

    /**
     * Logic thread, as a frame is built, once its builds are chosen: gathers the light of every
     * figure the frame may draw.
     *
     * @param shown the builds the frame draws, whose corpses are drawn as models
     * @return how many squares' light was read
     */
    int gather(WorldScene scene, IsoCell cell, int playerIndex, List<WorldScene.ChunkMeshes> shown, Stood stood) {
        this.scene = scene;
        this.cell = cell;
        this.playerIndex = playerIndex;
        people.clear();
        cars.clear();
        squares.clear();
        roomsSeen.clear();
        litsTaken = 0;
        carLitsTaken = 0;
        stood.begin();
        for (IsoMovingObject mover : cell.getObjectList()) {
            if (mover instanceof IsoGameCharacter character) {
                IsoGridSquare square = character.getCurrentSquare();
                people.put(character, at(square));
                if (square != null) {
                    stood.add(key(square.getX(), square.getY(), square.getZ()));
                }
                // Where it is as well: a piece cut off it is drawn where the frame drew it, which is there.
                long where = key((int) Math.floor(character.getX()), (int) Math.floor(character.getY()), (int) Math.floor(character.getZ()));
                at(where);
                stood.add(where);
            }
        }
        for (BaseVehicle vehicle : cell.getVehicles()) {
            cars.put(vehicle, carAt(vehicle.getCurrentSquare()));
        }
        for (int i = 0; i < shown.size(); i++) {
            List<Corpses.Lying> lying = shown.get(i).lying;
            for (int j = 0; j < lying.size(); j++) {
                at(lying.get(j).square());
            }
        }
        List<Pieces.Piece> pieces = Pieces.all();
        for (int i = 0; i < pieces.size(); i++) {
            Pieces.Piece piece = pieces.get(i);
            at(key((int) Math.floor(piece.x), (int) Math.floor(piece.y), (int) Math.floor(piece.z)));
        }
        Dying.squares(held);
        for (int i = 0; i < held.size(); i++) {
            at(held.get(i));
        }
        held.clear();
        for (int frame = 0; frame < stood.keys.length; frame++) {
            long[] keys = stood.keys[frame];
            for (int i = 0; i < stood.counts[frame]; i++) {
                at(keys[i]);
            }
        }
        // Held past the gathering, they would keep a save that is left alive.
        this.cell = null;
        this.scene = null;
        return litsTaken;
    }

    /** Lets go of everything gathered, as the save is left: the next frame gathers again before it is drawn. */
    void forget() {
        people.clear();
        cars.clear();
        squares.clear();
        for (int i = 0; i < lits.size(); i++) {
            lits.get(i).square = null;
        }
    }

    private Lit at(IsoGridSquare square) {
        if (square == null) {
            return NONE;
        }
        long key = key(square.getX(), square.getY(), square.getZ());
        Lit lit = squares.get(key);
        return lit != null ? lit : read(key, square);
    }

    private void at(long key) {
        if (squares.get(key) == null) {
            read(key, cell.getGridSquare((int) (key >> 32), (int) (key >>> 8 & 0xFFFFFFL), (byte) key));
        }
    }

    private Lit read(long key, IsoGridSquare square) {
        if (litsTaken == lits.size()) {
            lits.add(new Lit());
        }
        Lit lit = lits.get(litsTaken++);
        lit.square = square;
        if (square == null) {
            lit.light = Float.NaN;
            lit.outside = true;
        } else {
            lit.light = scene.drawnLight(square, given, roomSeen);
            lit.outside = square.isOutside();
        }
        squares.put(key, lit);
        return lit;
    }

    private CarLit carAt(IsoGridSquare square) {
        if (carLitsTaken == carLits.size()) {
            carLits.add(new CarLit());
        }
        CarLit lit = carLits.get(carLitsTaken++);
        lit.torched = false;
        if (square == null || !square.isSeen(playerIndex)) {
            return lit;
        }
        ColorInfo with = square.lighting[playerIndex].lightInfo();
        ColorInfo without = scene.view.torchLess.light(square, with, withoutTorch);
        if (without == with) {
            return lit;
        }
        lit.torched = true;
        lit.withRed = with.r;
        lit.withGreen = with.g;
        lit.withBlue = with.b;
        lit.withoutRed = without.r;
        lit.withoutGreen = without.g;
        lit.withoutBlue = without.b;
        return lit;
    }

    /** Render thread: the light a character the frame draws stands in. */
    Lit of(IsoGameCharacter character) {
        Lit lit = people.get(character);
        if (lit == null) {
            throw new IllegalStateException("the frame draws " + character.getClass().getSimpleName() + " " + character.getID()
                    + " at " + character.getX() + ", " + character.getY() + ", " + character.getZ() + ", which the game did not hold as it was built");
        }
        return lit;
    }

    /** Render thread: the light on a square a corpse lies on, or a held figure stands on, gathered as the frame was built. */
    Lit at(IsoGridSquare square, String what) {
        return square == null ? NONE : gathered(key(square.getX(), square.getY(), square.getZ()), what);
    }

    /** Render thread: the light on the square at (x, y, z), gathered as the frame was built. */
    Lit at(int x, int y, int z, String what) {
        return gathered(key(x, y, z), what);
    }

    private Lit gathered(long key, String what) {
        Lit lit = squares.get(key);
        if (lit == null) {
            throw new IllegalStateException("the frame draws " + what + " on the square at " + (int) (key >> 32) + ", " + (int) (key >>> 8 & 0xFFFFFFL) + ", " + (byte) key
                    + ", whose light was not gathered as it was built");
        }
        return lit;
    }

    /** Render thread: the light on the square a vehicle the frame draws stands on. */
    CarLit of(BaseVehicle vehicle) {
        CarLit lit = cars.get(vehicle);
        if (lit == null) {
            throw new IllegalStateException("the frame draws vehicle " + vehicle.vehicleId + " at " + vehicle.getX() + ", " + vehicle.getY()
                    + ", which the game did not hold as it was built");
        }
        return lit;
    }
}
