package com.cameraunlock.zomboid;

import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * What grows in a chunk being built: the grass, the flowers and the litter strewn on its ground, and the boles and
 * crowns of its trees. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class PlantBuilder {
    private static final int LONG_TUFTS = 13;
    /** How far either side of where it stands a tuft of long grass far off reaches, in tiles. */
    private static final float LONG_TUFT_HALF = 0.34f;
    /** How far from a bush's middle its outer cards stand, in tiles. */
    private static final float BUSH_SPREAD = 0.16f;
    /** How far from the line of a wall or a fence on the edge of its square a plant beside it ends, in tiles. */
    private static final float ROOM_CLEAR = WorldScene.WALL_HALF + 0.02f;
    /** A bole is no thinner or thicker than this, in tiles, whatever its sprite says. */
    private static final float BOLE_THINNEST = 0.05f, BOLE_THICKEST = 0.4f;
    private static final float GRASS_SPILL = 0.35f;
    /** How far in from a side where the lawn ends its plants keep, in tiles. */
    private static final float LAWN_KEEPS_IN = 0.05f;
    private static final int CROWN_LEAST_PLACES = 12;
    /** Whether the square, if there is one, has a wall or a fence on its north edge, or on its west edge. */
    private static boolean edged(IsoGridSquare square, boolean north) {
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            IsoSprite sprite = objects.get(i).getSprite();
            if (sprite != null && (north ? WorldScene.onNorthEdge(sprite.getProperties()) : WorldScene.onWestEdge(sprite.getProperties()))) {
                return true;
            }
        }
        return false;
    }

    private final WorldScene world;
    private final WorldScene.Frame build;

    private final float[] crownLeft = new float[Flora.CROWN_BANDS], crownRight = new float[Flora.CROWN_BANDS], crownSizes = new float[Flora.SIZES];
    private final Flora flora = new Flora();
    private final float[] littered = new float[4];
    /** The rectangle and texels of the frame as long grass found it, put back once the grass is grown. */
    private final SpriteGeometry.Frame heldTexels = new SpriteGeometry.Frame();
    /** The tree whose bole was stood last: its square, how thick and tall the bole is, and its bark. Its crown, which comes after it, grows limbs from it. */
    private int boleX = Integer.MIN_VALUE, boleY, boleBark;
    private float boleThick, boleTall;

    PlantBuilder(WorldScene world, WorldScene.Frame build) {
        this.world = world;
        this.build = build;
    }

    /** Says that what is being added is made by the band's own distance: the build is then another in every other band. */
    private void alikeOnly() {
        build.alike &= 1 << build.building.band;
    }

    /** How deep in fallen leaves the point (a, b) of a square of the chunk being built is. */
    float woodedAt(int localX, int localY, float a, float b) {
        int at = localX * Terrain.CORNERS + localY;
        return (build.wooded[at] * (1f - a) + build.wooded[at + Terrain.CORNERS] * a) * (1f - b)
                + (build.wooded[at + 1] * (1f - a) + build.wooded[at + Terrain.CORNERS + 1] * a) * b;
    }

    private boolean isLawn(int at) {
        return build.groundKind[at] == Terrain.NATURAL && build.lightOpen[at] == 1 && build.groundTexture[at] != null && (build.groundRank[at] - 1) % 64 >= 33;
    }

    /**
     * Grass the game paints flat on the ground stands up near the eye: every square of open
     * grass grows its own plants, each the colour of the ground it stands on. See Grass.
     */
    void growGrass(int x, int y, int localX, int localY) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        if (!isLawn(own)) {
            return;
        }
        // Over more grass a blade may hang past its square. Over a path it is untidy, and
        // through the wall of a house it is wrong, so on a side where the lawn ends the
        // plants keep in from the edge.
        build.lawnWest = isLawn(own - WorldScene.LIGHT_SPAN);
        build.lawnEast = isLawn(own + WorldScene.LIGHT_SPAN);
        build.lawnNorth = isLawn(own - 1);
        build.lawnSouth = isLawn(own + 1);
        build.grownLocalX = localX;
        build.grownLocalY = localY;
        build.grownUnder = 0.85f;
        build.frameOf(build.groundObject[own], build.groundTexture[own]);
        // Looking no way, as long grass is grown: a lawn left out behind the view was bare when the head turned to it.
        Grass.lawn(build.geometry, WorldScene.laid.get(build.groundTexture[own].getID()), build.frame, x, y, build.camera.eyeX, build.camera.eyeY, 0f, 0f,
                build.lawnWest ? 0f : LAWN_KEEPS_IN, build.lawnEast ? 1f : 1f - LAWN_KEEPS_IN, build.lawnNorth ? 0f : LAWN_KEEPS_IN, build.lawnSouth ? 1f : 1f - LAWN_KEEPS_IN, build);
    }

    /**
     * What stands in and lies on open ground besides its grass. Under trees the ground is
     * fallen leaves, thick in a wood and a thin scatter round one tree on a lawn, with ferns
     * growing out of them and a leaf or a twig standing proud. A lawn has flowers in drifts,
     * and here and there a weed. The leaves are laid as far as the land is drawn; the rest
     * only near the eye, where it can be told from the ground it stands on.
     */
    void strew(int x, int y, int localX, int localY, float away2) {
        int own = (localX + 1) * WorldScene.LIGHT_SPAN + localY + 1;
        if (build.groundKind[own] != Terrain.NATURAL || build.lightOpen[own] != 1) {
            return;
        }
        float deepest = 0f;
        for (int corner = 0; corner < 4; corner++) {
            int i = localX + (corner == 1 || corner == 2 ? 1 : 0), j = localY + (corner >= 2 ? 1 : 0);
            // Leaves are swept off a road and a path, and none fall indoors: a corner that touches either has none.
            boolean open = true;
            for (int ci = 0; ci <= 1; ci++) {
                for (int cj = 0; cj <= 1; cj++) {
                    int touching = (i + ci) * WorldScene.LIGHT_SPAN + j + cj;
                    open &= build.groundKind[touching] == Terrain.NATURAL && build.lightOpen[touching] == 1;
                }
            }
            littered[corner] = open ? build.wooded[i * Terrain.CORNERS + j] : 0f;
            deepest = Math.max(deepest, littered[corner]);
        }
        SpriteGeometry.Mesh cards = WorldScene.laid.get(WorldScene.PLAIN);
        if (deepest > 0.02f) {
            WorldScene.layers.lie(SpriteGeometry.LAYERS_UP, build.floorLayer + build.spreadLayers + 1);
            Flora.addLitter(cards, x, y, build.geometry.lift, littered, build.frame);
            WorldScene.layers.none();
        }
        float deep = (littered[0] + littered[1] + littered[2] + littered[3]) / 4f;
        boolean tree = build.treed[(localX + Flora.WOOD_RING) * WorldScene.WOOD_SPAN + localY + Flora.WOOD_RING];
        if (away2 < WorldScene.FERN_REACH * WorldScene.FERN_REACH && !tree && isLawn(own)) {
            int ferns = Flora.ferns(x, y, deep);
            if (ferns > 0) {
                roomFor(x, y, 0);
            }
            for (int fern = 0; fern < ferns; fern++) {
                int seed = Flora.mix(x, y, 40 + fern);
                float size = 0.4f + 0.3f * Flora.unit(seed, 16), reach = Flora.fernReach(size);
                // Its root is held in from a wall or a fence on the square's edge by as far as its fronds reach.
                float a = Flora.within(0.15f + 0.7f * Flora.unit(seed, 0), reach, build.room[0] - x, build.room[1] - x);
                float b = Flora.within(0.15f + 0.7f * Flora.unit(seed, 8), reach, build.room[2] - y, build.room[3] - y);
                if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                    continue;
                }
                int fronds = away2 < WorldScene.FERN_NEAR * WorldScene.FERN_NEAR ? 5 + (seed >>> 24 & 1) : 4;
                Flora.addFern(cards, x + a, y + b, build.geometry.liftAt(a, b), size, fronds, seed, build.frame);
            }
        }
        if (away2 < WorldScene.FALLEN_REACH * WorldScene.FALLEN_REACH) {
            int fallen = Flora.fallen(x, y, deep);
            for (int leaf = 0; leaf < fallen; leaf++) {
                int seed = Flora.mix(x, y, 50 + leaf);
                float a = 0.05f + 0.9f * Flora.unit(seed, 0), b = 0.05f + 0.9f * Flora.unit(seed, 8);
                double turn = Flora.unit(seed, 16) * 2.0 * Math.PI;
                float tall = Flora.unit(seed, 24);
                if (leaf == 0 && (seed & 3) == 0) {
                    Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.03f, 0.22f + 0.2f * tall, 0.035f, turn, Math.toRadians(3.0 + 5.0 * tall),
                            Flora.TWIG, 0f, build.frame);
                } else {
                    float length = 0.09f + 0.05f * tall;
                    Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.02f, length, length * 0.7f, turn, Math.toRadians(8.0 + 22.0 * tall),
                            Flora.FALLEN_ORANGE + (seed >>> 20) % 3, 0f, build.frame);
                }
            }
        }
        if (away2 < WorldScene.FLOWER_REACH * WorldScene.FLOWER_REACH && isLawn(own) && deep < 0.35f) {
            addFlowers(cards, x, y, 0.1f, 0.14f, 0.022f);
            if (Flora.weed(x, y)) {
                int seed = Flora.mix(x, y, 70);
                float a = 0.2f + 0.6f * Flora.unit(seed, 0), b = 0.2f + 0.6f * Flora.unit(seed, 8);
                if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                    return;
                }
                for (int leaf = 0; leaf < Flora.WEED_LEAVES; leaf++) {
                    int each = Flora.mix(seed, leaf, 71);
                    float length = 0.13f + 0.07f * Flora.unit(each, 0);
                    Flora.addLeaf(cards, x + a, y + b, build.geometry.liftAt(a, b) + 0.01f, length, length * 0.5f,
                            (leaf + 0.6 * Flora.unit(each, 8)) * 2.0 * Math.PI / Flora.WEED_LEAVES, Math.toRadians(15.0 + 25.0 * Flora.unit(each, 16)),
                            Flora.WEED, 0.05f, build.frame);
                }
            }
        }
    }

    /** The flowers of a square of grass: heads at least lowest above the ground and up to taller more, the smallest of them least across. */
    private void addFlowers(SpriteGeometry.Mesh cards, int x, int y, float lowest, float taller, float least) {
        int flowers = Flora.flowers(x, y);
        for (int flower = 0; flower < flowers; flower++) {
            int seed = Flora.mix(x, y, 60 + flower);
            float a = 0.08f + 0.84f * Flora.unit(seed, 0), b = 0.08f + 0.84f * Flora.unit(seed, 8);
            if (build.geometry.kerb != null && build.geometry.onStreet(a, b)) {
                continue;
            }
            Flora.addFlower(cards, x + a, y + b, build.geometry.liftAt(a, b), lowest + taller * Flora.unit(seed, 16), least * (1f + 0.65f * Flora.unit(seed, 24)),
                    Flora.unit(seed, 4) * 2.0 * Math.PI, Flora.flowerKind(x, y, flower), (seed >>> 12) % Flora.FLOWER_THINNINGS, build.frame);
        }
    }

    private boolean spillsTo(int at) {
        return build.groundKind[at] == Terrain.NATURAL && build.lightOpen[at] == 1;
    }

    boolean growLongGrass(int x, int y) {
        int own = (build.waterLocalX + 1) * WorldScene.LIGHT_SPAN + build.waterLocalY + 1;
        if (build.groundTexture[own] == null) {
            return false;
        }
        float originU = build.frame.originU, originV = build.frame.originV;
        heldTexels.texelsOf(build.frame);
        build.frameOf(build.groundObject[own], build.groundTexture[own]);
        // Nor does it spill through a wall or a fence on the square's edge, and its tufts keep to their own side of one.
        roomFor(x, y, 0);
        float roomLowA = build.room[0] - x, roomHighA = build.room[1] - x, roomLowB = build.room[2] - y, roomHighB = build.room[3] - y;
        float lowA = spillsTo(own - WorldScene.LIGHT_SPAN) && roomLowA < 0f ? -GRASS_SPILL : 0.12f, highA = spillsTo(own + WorldScene.LIGHT_SPAN) && roomHighA > 1f ? 1f + GRASS_SPILL : 0.88f;
        float lowB = spillsTo(own - 1) && roomLowB < 0f ? -GRASS_SPILL : 0.12f, highB = spillsTo(own + 1) && roomHighB > 1f ? 1f + GRASS_SPILL : 0.88f;
        SpriteGeometry.Mesh mesh = WorldScene.laid.get(build.groundTexture[own].getID());
        float away = build.bandAway;
        // Near the eye it is grown plant by plant. Far off, where a blade is finer than the
        // picture can draw, it is cards with the blades cut out of them, and between the two
        // the cards rise out of the ground as the plants sink into it.
        // Past where the plants end and the flowers too, it is the same cards from any distance.
        if (build.within(Grass.RANK_ENDS, false) | build.within(WorldScene.FLOWER_REACH, false)) {
            alikeOnly();
        }
        if (away < Grass.RANK_ENDS) {
            build.lawnWest = build.lawnEast = build.lawnNorth = build.lawnSouth = true;
            build.grownLocalX = build.waterLocalX;
            build.grownLocalY = build.waterLocalY;
            build.grownUnder = 0.7f;
            // Grown as from an eye the band's distance off and looking no way: none of it is left out as behind the view.
            Grass.rank(build.geometry, mesh, build.frame, x, y, x + 0.5f - away, y + 0.5f, 0f, 0f, lowA, highA, lowB, highB, build);
        }
        float carded = Math.clamp((away - Grass.RANK_THINS) / (Grass.RANK_ENDS - Grass.RANK_THINS), 0f, 1f);
        for (int tuft = 0; tuft < LONG_TUFTS && carded > 0f; tuft++) {
            int hash = x * 19349663 ^ y * 83492791 ^ tuft * 73856093;
            hash = (hash ^ hash >>> 13) * 0x5bd1e995;
            hash ^= hash >>> 15;
            // A clump of long grass is not the shape of the square the game put it on. Towards
            // open ground its tufts spill a third of a square past the edge, shorter the
            // further out they stand, so a patch has a ragged outline. Towards a road, a
            // path or a floor it keeps inside.
            double turn = (hash >>> 16 & 0xff) / 255.0 * Math.PI;
            float cos = (float) Math.cos(turn), sin = (float) Math.sin(turn);
            float a = Flora.within(lowA + (highA - lowA) * (hash & 0xff) / 255f, Math.abs(cos) * LONG_TUFT_HALF, roomLowA, roomHighA);
            float b = Flora.within(lowB + (highB - lowB) * (hash >>> 8 & 0xff) / 255f, Math.abs(sin) * LONG_TUFT_HALF, roomLowB, roomHighB);
            float out = Math.max(Math.max(-a, a - 1f), Math.max(-b, b - 1f));
            float tall = (0.45f + 0.5f * (hash >>> 24 & 0xff) / 255f) * (out > 0f ? 1f - 1.8f * out : 1f) * carded;
            float vigour = 1f - 0.7f * woodedAt(build.waterLocalX, build.waterLocalY, Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f));
            build.geometry.addTuft(mesh, build.frame, x, y, a, b, cos, sin, LONG_TUFT_HALF, tall, SpriteGeometry.LONG_GRASS, vigour);
        }
        if (away < WorldScene.FLOWER_REACH) {
            // Flowers in long grass stand as tall as it does.
            addFlowers(WorldScene.laid.get(WorldScene.PLAIN), x, y, 0.4f, 0.35f, 0.03f);
        }
        build.frame.originU = originU;
        build.frame.originV = originV;
        build.frame.texelsOf(heldTexels);
        return true;
    }

    /**
     * A tree or a bush is not a picture of one that turns to face whoever looks at it.
     * It stands in the world: the sprite is set up several times through the plant's
     * middle, fanned round it, every other one mirrored, so that from any side there
     * are leaves in front of leaves and they pass each other as the eye moves. Which
     * way the fan is turned comes from where the plant stands, so no two are alike.
     */
    void addGrown(SpriteGeometry.Mesh mesh, IsoSprite sprite, IsoObjectType type, Texture texture, int x, int y, int z) {
        int cards = type == IsoObjectType.tree ? 5 : 3;
        // A tree's bole is round. A tree in leaf shows nothing else of its trunk's sprite,
        // so the bole stands in for all its cards; a bare one keeps them for its branches.
        if (type == IsoObjectType.tree && build.overLayer == 0 && Own.plants() && addBole(sprite, texture, x, y, z) && build.geometry.keep < 1f) {
            return;
        }
        roomFor(x, y, z);
        // A tree's cards are its whole crown, which over a wall's top reaches across it: only its crown of sprigs is kept to its room.
        if (type != IsoObjectType.tree) {
            build.geometry.roomLowA = build.room[0] - x;
            build.geometry.roomHighA = build.room[1] - x;
            build.geometry.roomLowB = build.room[2] - y;
            build.geometry.roomHighB = build.room[3] - y;
        }
        // A bush is leaves all over, as a tree's crown is.
        String grown = sprite.getName();
        // A bush's leaves are smaller than a tree's.
        boolean bush = grown != null && type != IsoObjectType.tree
                && (grown.startsWith("f_bushes") || grown.startsWith("vegetation_foliage") || grown.startsWith("vegetation_ornamental"));
        // So is every other plant the game paints with a dark line round each leaf: the
        // line is what makes it a drawing. A bare tree's trunk is the one thing left as it is.
        if (grown != null && type != IsoObjectType.tree && Own.plants()) {
            build.frame.material = bush ? Flora.BUSH : WorldScene.LEAVES;
        }
        // The leaves of a tree and a bush, near enough to be seen as leaves, are a crown of
        // sprigs. The fan then stands only in the sun's own pictures, to throw the shadow.
        float fanned = build.frame.material;
        boolean crowned = grown != null && (bush || type == IsoObjectType.tree && build.overLayer > 0) && Own.plants() && addCrown(texture, grown, bush, x, y, z);
        if (crowned) {
            build.frame.material = Flora.SHADE;
        }
        double turn = ((x * 73856093 ^ y * 19349663) >>> 8 & 0xff) / 255.0 * Math.PI;
        for (int card = 0; card < cards; card++) {
            double angle = turn + card * Math.PI / cards;
            float side = (card & 1) == 0 ? 1f : -1f;
            float alongX = (float) Math.cos(angle), alongY = (float) Math.sin(angle);
            // Leaves lie on the same card as their trunk, and each layer has to stand
            // clear of the one under it on the side the eye is on, square to this card:
            // a move straight towards the eye does nothing for a card seen at a slant.
            // Which side that is, the picture finds as it draws: see PipelineShaders.FACED.
            build.geometry.clear = build.overLayer;
            build.geometry.addUpright(mesh, build.frame, x, y, z, 0.5f, 0.5f, 0f, side * alongX, side * alongY, false, true);
            build.geometry.clear = 0f;
        }
        build.frame.material = fanned;
        if (bush && !crowned && Own.plants() && build.within(WorldScene.BUSH_REACH, false)) {
            // A fan of cards through one line is thin seen along that line, and from
            // above it is a star. A bush is a clump: three more cards stand round its
            // middle, each across the way out to it, so there are leaves on every side.
            for (int outer = 0; outer < 3; outer++) {
                double out = turn + (outer + 0.5) * 2.0 * Math.PI / 3.0;
                float outX = (float) Math.cos(out), outY = (float) Math.sin(out);
                float side = (outer & 1) == 0 ? 1f : -1f;
                build.geometry.nudgeX = outX * BUSH_SPREAD;
                build.geometry.nudgeY = outY * BUSH_SPREAD;
                build.geometry.addUpright(mesh, build.frame, x, y, z, 0.5f, 0.5f, 0f, -outY * side, outX * side, false, true);
            }
        }
        build.geometry.nudgeX = build.geometry.nudgeY = 0f;
        build.geometry.roomLowA = build.geometry.roomLowB = -Float.MAX_VALUE;
        build.geometry.roomHighA = build.geometry.roomHighB = Float.MAX_VALUE;
    }

    /**
     * Sets room to the room of a plant on the square (see Flora.ROOM_UNDER): its own side of
     * a wall or a fence on any edge of the square. The other side, and a room indoors behind
     * a house's wall, shows nothing of it.
     */
    private void roomFor(int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        build.room[0] = edged(square, false) ? x + ROOM_CLEAR : -Float.MAX_VALUE;
        build.room[1] = edged(build.cell.getGridSquare(x + 1, y, z), false) ? x + 1f - ROOM_CLEAR : Float.MAX_VALUE;
        build.room[2] = edged(square, true) ? y + ROOM_CLEAR : -Float.MAX_VALUE;
        build.room[3] = edged(build.cell.getGridSquare(x, y + 1, z), true) ? y + 1f - ROOM_CLEAR : Float.MAX_VALUE;
    }

    /**
     * Stands a crown of sprigs for the plant whose leaves the frame is set to, in the mesh of
     * the leaves' own texture, which gives the sprigs their colour. False when the plant is
     * too far off for one or its texture shows too little to make one of.
     */
    private boolean addCrown(Texture texture, String name, boolean bush, int x, int y, int z) {
        if (!world.crownedToSight && !build.within(Crowns.REACH, true)) {
            return false;
        }
        float away = build.bandAway;
        float[] places = WorldScene.leafPlaces.get(texture);
        if (places == null) {
            places = Flora.leafPlaces(texture::isMaskSet, build.frame);
            // A texture that shows nothing yet may only not have its outline read yet.
            if (places.length < CROWN_LEAST_PLACES * 2) {
                return false;
            }
            WorldScene.leafPlaces.put(texture, places);
        }
        // The bole stood last may be another tree's, in another chunk: only its own tree's crown grows limbs from it.
        boolean boled = !bush && boleX == x && boleY == y;
        int seed = Flora.mix(x, y, 83);
        float base = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(0.5f, 0.5f) + build.geometry.raised * ViewCamera.LEVEL_HEIGHT;
        float footU = SpriteGeometry.frameU(build.frame, 0.5f, 0.5f), footV = SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f);
        int bark = Flora.barkOf(name, seed), sprig = Flora.sprigOf(name, bush);
        if (build.crownsInline) {
            boolean limbed = boled && Crowns.limbed(away);
            return Flora.addCrown(WorldScene.solid.get(texture.getID()), WorldScene.solid.get(WorldScene.PLAIN), build.frame, places, x + 0.5f, y + 0.5f, base, footU, footV, Crowns.detail(away), Crowns.close(away),
                    limbed ? boleThick : 0f, limbed ? boleTall : 0f, limbed ? boleBark : bark, sprig, seed, build.room);
        }
        if (!Flora.measure(build.frame, places, footU, footV, crownLeft, crownRight, crownSizes)) {
            return false;
        }
        (build.afresh ? WorldScene.crownsChecked : world.crownsRead).add(texture.getID(), build.frame, places, x + 0.5f, y + 0.5f, base, footU, footV, away, boled ? boleThick : 0f, boled ? boleTall : 0f,
                boled ? boleBark : bark, bark, sprig, seed, build.room);
        return true;
    }

    /**
     * Stands a boulder on the square, as big as its sprite's outline says it is. False for a
     * sprite whose outline is too flat to say: a stone lying in the grass, which stays as it is.
     */
    boolean addRock(int x, int y, int z) {
        if (!SpriteGeometry.boxOf(build.frame, build.box)) {
            return false;
        }
        float a = Math.clamp(build.box[0], 0.2f, 0.8f), b = Math.clamp(build.box[1], 0.2f, 0.8f);
        flora.addRock(WorldScene.solid.get(WorldScene.PLAIN), x + a, y + b, z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a, b), Math.clamp(build.box[2] * 1.15f, 0.15f, 0.6f),
                Math.clamp(build.box[3] * ViewCamera.LEVEL_HEIGHT, 0.12f, 1.4f), build.within(WorldScene.ROCK_NEAR, false), Flora.mix(x, y, 80), build.frame);
        return true;
    }

    /**
     * Stands a round bole where a tree's sprite shows its trunk, with ivy up some. False when
     * the tree is too far off for one, or its sprite has no bole that can be read.
     */
    private boolean addBole(IsoSprite sprite, Texture texture, int x, int y, int z) {
        if (!world.crownedToSight && !build.within(WorldScene.TRUNK_REACH, true)) {
            return false;
        }
        float[] bole = WorldScene.boles.get(sprite);
        if (bole == null) {
            bole = measureBole(texture);
            WorldScene.boles.put(sprite, bole);
        }
        if (bole.length == 0) {
            return false;
        }
        boolean near = build.within(WorldScene.TRUNK_NEAR, false);
        int seed = Flora.mix(x, y, 81);
        float base = z * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(0.5f, 0.5f);
        boleBark = Flora.barkOf(sprite.getName(), seed);
        flora.addTrunk(WorldScene.solid.get(WorldScene.PLAIN), x + 0.5f, y + 0.5f, base, bole[0], bole[1], near, boleBark, seed, build.frame);
        boleX = x;
        boleY = y;
        boleThick = bole[0];
        boleTall = bole[1];
        if (near && Flora.ivied(x, y)) {
            flora.addIvy(WorldScene.laid.get(WorldScene.PLAIN), x + 0.5f, y + 0.5f, base, bole[0], bole[1], bole[1] * (0.5f + 0.5f * Flora.unit(seed, 16)), seed, build.frame);
        }
        return true;
    }

    /**
     * Reads a bole off the tree sprite the frame is set to: how tall it is, which is as much of
     * the sprite as a tree in leaf shows, and how thick, which is the narrower of what is
     * painted across the sprite at two heights below where the boughs start. NO_BOLE for a
     * sprite painted right across at those heights, which is no trunk, and for one painted at
     * neither.
     */
    private float[] measureBole(Texture texture) {
        float topRow = build.frame.top + build.frame.height * (1f - WorldScene.BOLE);
        float footRow = Math.min(SpriteGeometry.frameV(build.frame, 0.5f, 0.5f, 0f), build.frame.top + build.frame.height - 1f);
        float tall = SpriteGeometry.heightDrawnAt(build.frame, 0.5f, 0.5f, topRow) * ViewCamera.LEVEL_HEIGHT;
        if (tall < 0.3f) {
            return WorldScene.NO_BOLE;
        }
        int narrowest = Integer.MAX_VALUE;
        for (int row = 0; row < 2; row++) {
            int v = Math.round(footRow + (topRow - footRow) * (row == 0 ? 0.2f : 0.45f));
            int first = -1, last = -1;
            for (int u = (int) build.frame.left; u < build.frame.left + build.frame.width; u++) {
                if (texture.isMaskSet(u, v)) {
                    if (first < 0) {
                        first = u;
                    }
                    last = u;
                }
            }
            if (first >= 0) {
                narrowest = Math.min(narrowest, last - first + 1);
            }
        }
        if (narrowest == Integer.MAX_VALUE || narrowest >= build.frame.width * 0.6f) {
            return WorldScene.NO_BOLE;
        }
        float radius = narrowest / 2f * SpriteGeometry.TILES_PER_PIXEL_ACROSS / build.frame.scale;
        return new float[] {Math.clamp(radius, BOLE_THINNEST, BOLE_THICKEST), tall};
    }
}
