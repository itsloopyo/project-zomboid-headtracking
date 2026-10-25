package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL42;
import zombie.core.textures.Texture;

/**
 * A square of rug drawn sharp.
 *
 * The game paints a rug a square at a time, each square a diamond about 64 dots across at the
 * scale it is seen at in its own view. Laid on the floor in first person that is a dot every
 * centimetre and a half, and a rug's border, its medallion and a hide's outline are drawn out
 * into blurs. Here each square of rug is read off its sprite flat, as Flats reads a floor, a
 * little past its square on every side, and drawn again UP times as fine:
 *
 * A woven rug is a few flat colours. They are found, each dot read is given the nearest, and
 * each dot drawn takes the colour the dots read round it mostly have, weighed as a smooth curve
 * through them: a border is drawn as a straight line and a medallion's edge as a curve, where
 * the sprite drew both as steps a centimetre and a half high. A fur rug and a hide are painted
 * in shades, which are drawn smooth, and the strands are the mod's own picture of fur or of hair
 * laid over them. A rug's outline is the smooth curve through how much of each dot read it
 * covers, cut at half.
 *
 * A square drawn sharp is 2 MB on the graphics card and takes tens of milliseconds to draw, and a
 * town holds hundreds of rugs. So a build is given a number for the square, not a picture, and
 * the picture is chosen as each frame draws it (texture): the square as it was read, at the
 * density it was read at, which is the sprite's own or finer, and its sharp picture once one is
 * made. Only the copying of the read is done on the thread that draws, where the sprite's sheet
 * is: what is made of it is made on a thread of its own and put on the graphics card a few
 * pictures a frame. The squares frames draw are ranked by how near the eye a place they lie at
 * is, and the nearest MOST_SHARP have sharp pictures: a square that comes nearer takes the slot
 * of the furthest, and one no frame has drawn for KEPT_NANOS gives its slot back. What a square
 * was read as is kept, so its sharp picture can be made again without the sprite's sheet.
 */
final class Rugs {
    /** Dots a tile is read at: as Flats lays a floor. */
    static final int SIDE = Flats.SIDE;
    /** How many dots past its square a square of rug is read: a fur rug's tufts go past it. */
    static final int MARGIN = 16;
    static final int READ = SIDE + 2 * MARGIN;
    /** How many dots of the picture a dot read stands for, each way. */
    static final int UP = 4;
    static final int DRAWN = READ * UP;
    /** Where in the picture the square's (0, 0) corner is, and how much of the picture the square takes, both ways alike. */
    static final float FROM = (float) MARGIN / READ, SPAN = (float) SIDE / READ;
    /** How far past its square, in tiles, a square of rug may reach: no further than it was read. */
    static final float REACH = (float) MARGIN / SIDE;
    /** What of says of a square whose picture as read is not on the graphics card yet. */
    static final int PENDING = Integer.MIN_VALUE;
    /** What a rug is made of: woven in flat colours, a fur rug, or an animal's hide. */
    static final int WOVEN = 0, FUR = 1, HIDE = 2;
    /** A dot read is the rug where its dots cover this much of it. */
    private static final float COVERED = 0.5f;
    /** A square of rug paints at least this much of its square, or it is not read as one. */
    private static final float LEAST_PAINTED = 0.02f;
    /** The most flat colours a woven rug is read as. */
    private static final int MOST_COLOURS = 8;
    /**
     * The mean squared error a dot, in steps of 255 a channel summed over three, of colours that
     * are the rug's: the speckle the game paints over a flat colour is 10 to 20 of it. Or no
     * more than NEAR_BEST times what the most colours leave, on a rug of shaded circles.
     */
    private static final float SPECKLE = 30f, NEAR_BEST = 1.25f;
    /** How many dots in from a square's edge the sprite's staircase leaves empty at most, as Flats reads a floor. */
    private static final int STAIR = 3;
    private static final int ROUNDS = 10;
    /**
     * How far, in dots read, each colour's share of a dot is spread before it is drawn: the
     * sprite's lines step a dot from side to side where the diamond is laid flat, and drawn
     * from where each dot's colour falls alone they come out in beads.
     */
    private static final float SOFTEN = 0.8f;
    /** How sharply two colours meet: the change in their share of the dots round a dot drawn that goes from one to the other. */
    private static final float MEETING = 1.2f;
    /** How strongly the strands of fur or hair show over a fur rug's or a hide's own shades, and how many times a picture of them repeats across a tile. */
    private static final float GRAIN_STRENGTH = 0.55f;
    private static final float GRAIN_REPEAT = 1f;
    /** How many pictures as read, and how many sharp ones, are put on the graphics card a frame at most, and how many sharp ones are kept on it at most. */
    private static final int READ_A_FRAME = 8, SHARP_A_FRAME = 2, MOST_SHARP = 48;
    /** How long a sharp picture no frame has drawn is kept, and how often the squares are ranked. */
    private static final long KEPT_NANOS = 3_000_000_000L, RANKED_NANOS = 250_000_000L;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC, GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;

    /** One square of rug: the sprite, what the rug is made of, where it sits in its frame, and how high over the floor its top is, in levels. */
    private record Want(Texture texture, int kind, float originU, float originV, float scale, float left, float top, float width, float height, float lifted) {}

    /** A square of rug as read: its colour, three numbers a dot from 0 to 255, and how much of each dot it covers. */
    record Read(float[] colour, float[] cover) {}

    /** A picture made for a square, its levels from the whole picture down to one dot (null where it could not be made), whether it is the sharp one, and the round it was asked for in. */
    private record Made(Square square, byte[][] levels, boolean sharp, int round) {}

    /** A square of rug that has been read, and the pictures it has on the graphics card. */
    static final class Square {
        /** The number a build is given for it: below zero, see ChunkBuffers.bound. */
        final int number;
        final int kind;
        /** What it was read as, four bytes a dot: red, green, blue and how much of the dot it covers. */
        final byte[] read;
        final String name;
        /** Its picture as read: 0 until it is on the graphics card, -1 where it could not be made. Set on the render thread, asked by builds. */
        volatile int asRead;
        /** Its sharp picture, 0 while it has none, and whether that was put on the card at half size (Quality.smallerPictures). Render thread. */
        int sharp;
        boolean sharpSmaller;
        /** Whether a sharp picture is being drawn for it, or could not be: it is not asked for again. Render thread. */
        boolean drawing, failed;
        /** When a frame last drew it. Render thread. */
        long drawnAt;
        /** Where builds have laid it, each square as x, y and z packed by place: see near. */
        final Set<Long> places = ConcurrentHashMap.newKeySet();

        Square(int number, int kind, byte[] read, String name) {
            this.number = number;
            this.kind = kind;
            this.read = read;
            this.name = name;
        }
    }

    private static final ConcurrentHashMap<Texture, Set<Want>> asked = new ConcurrentHashMap<>();
    /** Each square read, or NONE for one that paints too little of its square to be one. */
    private static final ConcurrentHashMap<Want, Square> squares = new ConcurrentHashMap<>();
    private static final Square NONE = new Square(0, WOVEN, null, null);
    /**
     * Each square of this round, the one numbered -1 - firstOfRound first. Render thread: a frame
     * looks a square up for every range of rug it draws. Numbers go on from one round to the next,
     * so a number a build kept past letGo names no square of the next round.
     */
    private static final ArrayList<Square> numbered = new ArrayList<>();
    private static int firstOfRound;
    private static final ConcurrentLinkedQueue<Made> made = new ConcurrentLinkedQueue<>();
    /** Counts the times every picture has been given back: what was being made for an earlier round is dropped. */
    private static volatile int round;
    /** Render thread: how many sharp pictures are on the card or being drawn, when this frame began, and when the squares were last ranked. */
    private static int sharpOrDrawing;
    private static long frameNanos, rankedAt;
    /**
     * Render thread: the eye of each view, three numbers a view, and which views have drawn a
     * frame since the squares were last ranked, a bit a view. In split screen the same view comes
     * to rank the squares every time, so each square is measured from the nearest of the eyes.
     */
    private static final float[] eyes = new float[View.COUNT * 3];
    private static int eyesSinceRanked;
    private static final ExecutorService drawer = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "CameraUnlock rugs");
        thread.setDaemon(true);
        return thread;
    });
    /** The strands of a fur rug and the hair of a hide: see Grain. Read on the rugs' own thread when first wanted, let go with every picture. */
    private static volatile Grain grain;
    /** Why the grain could not be read, once it could not: logged once, and every fur rug and hide is drawn as read after. */
    private static volatile IllegalStateException grainFailed;

    /** Square pictures of each dot's light over the mean, side dots to a side. */
    private record Grain(float[] fur, int furSide, float[] hide, int hideSide) {}

    private Rugs() {}

    /** What a sprite of that name is a square of rug made of, or -1 for one that is no rug. */
    static int kindOf(String name) {
        if (name == null) {
            return -1;
        }
        if (name.startsWith("floors_rugs_02")) {
            return FUR;
        }
        if (name.startsWith("floors_rugs")) {
            return WOVEN;
        }
        return name.startsWith("rugs_animals") ? HIDE : -1;
    }

    /**
     * The number of the square of rug the sprite paints, as the frame holds it and laid lifted
     * levels over the floor at (x, y, z), for a build to key its faces by: PENDING until its
     * picture as read is on the graphics card, 0 for a kind of -1, no rug, and for a sprite that
     * paints too little of its square or whose picture could not be made.
     */
    static int of(Texture texture, SpriteGeometry.Frame frame, int kind, float lifted, int x, int y, int z) {
        if (kind < 0) {
            return 0;
        }
        Want want = new Want(texture, kind, frame.originU, frame.originV, frame.scale, frame.left, frame.top, frame.width, frame.height, lifted);
        Square square = squares.get(want);
        if (square == null) {
            if (asked.computeIfAbsent(texture, sprite -> ConcurrentHashMap.newKeySet()).add(want)) {
                SpriteColours.readAgain(texture);
            }
            return PENDING;
        }
        if (square == NONE || square.asRead < 0) {
            return 0;
        }
        square.places.add(place(x, y, z));
        return square.asRead == 0 ? PENDING : square.number;
    }

    /**
     * What a square of rug is made of for the picture's shader. A woven rug's pile and a fur
     * rug's are the carpet's, which the shader lays fine over the picture, a fur rug's curls are
     * in its own picture too, and a hide's short hair is in its own picture alone: the carpet's
     * pile on a cowhide is a cowhide carpeted.
     */
    static float material(int kind, float given) {
        return kind == HIDE ? 0f : given;
    }

    /** A square of the map as one number, for Square.places. */
    static long place(int x, int y, int z) {
        return (long) (x & 0xffffff) << 40 | (long) (y & 0xffffff) << 16 | z & 0xffff;
    }

    /** Notes the eye a view is about to draw a frame from, for the next ranking. */
    static void seeFrom(int view, float eyeX, float eyeY, float eyeUp) {
        eyes[view * 3] = eyeX;
        eyes[view * 3 + 1] = eyeY;
        eyes[view * 3 + 2] = eyeUp;
        eyesSinceRanked |= 1 << view;
    }

    /** The squared distance, in tiles, from the nearest eye noted since the last ranking to the nearest place a square lies at. */
    static float distanceToEyes(Set<Long> places) {
        float least = Float.MAX_VALUE;
        for (int view = 0; view < View.COUNT; view++) {
            if ((eyesSinceRanked & 1 << view) != 0) {
                least = Math.min(least, distanceTo(places, eyes[view * 3], eyes[view * 3 + 1], eyes[view * 3 + 2]));
            }
        }
        return least;
    }

    /** Starts noting the eyes for the next ranking. */
    static void eyesRanked() {
        eyesSinceRanked = 0;
    }

    /** The squared distance, in tiles, from the eye to the nearest place a square lies at. The map's squares are at x and y from 0. */
    static float distanceTo(Set<Long> places, float eyeX, float eyeY, float eyeUp) {
        float least = Float.MAX_VALUE;
        for (long at : places) {
            float dx = (int) (at >>> 40) + 0.5f - eyeX, dy = (int) (at >>> 16 & 0xffffff) + 0.5f - eyeY;
            float dz = (short) at * ViewCamera.LEVEL_HEIGHT - eyeUp;
            least = Math.min(least, dx * dx + dy * dy + dz * dz);
        }
        return least;
    }

    /**
     * Reads every square of rug the sprite was asked for as, and has its picture as read made on
     * the rugs' own thread. On the thread that draws: only the copying of the square is done here.
     */
    static void read(Texture texture, ByteBuffer sheet, int width, int left, int top, int right, int bottom) {
        Set<Want> wants = asked.get(texture);
        if (wants == null) {
            return;
        }
        for (Want want : wants) {
            if (squares.containsKey(want)) {
                continue;
            }
            byte[] read = sampled(sheet, width, left, top, right, bottom, want);
            if (read == null) {
                squares.put(want, NONE);
                continue;
            }
            Square square = new Square(-1 - firstOfRound - numbered.size(), want.kind, read, texture.getName());
            numbered.add(square);
            squares.put(want, square);
            make(square, false);
            if (FirstPerson.TRACE) {
                Log.info("rugs: " + square.name + " read as square " + square.number);
            }
        }
    }

    /**
     * Has a picture of the square made on the rugs' own thread: as read, or sharp. A picture asked
     * for in a round that has ended is not made, nor handed in.
     */
    private static void make(Square square, boolean sharp) {
        int now = round;
        drawer.execute(() -> {
            byte[][] levels = null;
            try {
                if (round != now) {
                    return;
                }
                Read read = unpacked(square.read);
                if (sharp) {
                    Grain strands = square.kind == WOVEN ? null : grain();
                    float[] lights = strands == null ? null : square.kind == FUR ? strands.fur : strands.hide;
                    int side = strands == null ? 0 : square.kind == FUR ? strands.furSide : strands.hideSide;
                    levels = levels(drawn(read, square.kind, lights, side), DRAWN);
                } else {
                    levels = levels(asRead(straightened(read, square.kind)), READ);
                }
            } catch (Throwable thrown) {
                if (thrown != grainFailed) {
                    Log.error("a square of rug, " + square.name + ", could not be drawn " + (sharp ? "sharp and is drawn as it was read" : "as read and is drawn from its sprite"), thrown);
                }
            } finally {
                if (round == now) {
                    made.add(new Made(square, levels, sharp, now));
                }
            }
        });
    }

    /**
     * The OpenGL name of the picture a frame draws the square of rug of that number with: its
     * sharp picture where it has one, and its picture as read until then. Render thread, as a
     * frame draws: see ChunkBuffers.bound.
     */
    static int texture(int number) {
        Square square = square(number);
        square.drawnAt = frameNanos;
        return square.sharp != 0 ? square.sharp : square.asRead;
    }

    /** The square of this round with that number. A number from an earlier round is a build kept past letGo, which never happens and is said loudly. */
    static Square square(int number) {
        int at = -1 - number - firstOfRound;
        if (at < 0 || at >= numbered.size()) {
            throw new IllegalStateException("no square of rug of this round has the number " + number + ": a build kept one past Rugs.letGo");
        }
        return numbered.get(at);
    }

    /**
     * Render thread, as each frame begins, with the view about to be drawn and its eye: puts the
     * pictures made since the last frame on the graphics card, a few a frame, and now and then
     * ranks the squares frames draw by how near the nearest view's eye they lie and hands out
     * sharp pictures to the nearest.
     */
    static void putOnCard(int view, float eyeX, float eyeY, float eyeUp) {
        frameNanos = System.nanoTime();
        seeFrom(view, eyeX, eyeY, eyeUp);
        int reads = 0, sharps = 0;
        for (Made one = made.peek(); one != null && reads < READ_A_FRAME && sharps < SHARP_A_FRAME; one = made.peek()) {
            if (one.round != round) {
                made.poll();
                continue;
            }
            Square square = one.square;
            if (!one.sharp) {
                int name = one.levels == null ? -1 : picture(one.levels, 0);
                if (name == NOT_TAKEN) {
                    break;
                }
                made.poll();
                reads++;
                square.asRead = name;
                SpriteColours.answered();
                continue;
            }
            if (one.levels == null) {
                made.poll();
                sharps++;
                square.drawing = false;
                square.failed = true;
                sharpOrDrawing--;
                continue;
            }
            boolean smaller = Quality.smallerPictures();
            int name = picture(one.levels, smaller ? 1 : 0);
            if (name == NOT_TAKEN) {
                break;
            }
            made.poll();
            sharps++;
            square.drawing = false;
            square.sharpSmaller = smaller;
            square.sharp = name;
            if (FirstPerson.TRACE) {
                Log.info("rugs: square " + square.number + " drawn sharp, " + sharpOrDrawing + " sharp or being drawn");
            }
        }
        if (frameNanos - rankedAt >= RANKED_NANOS) {
            rankedAt = frameNanos;
            rank();
            eyesRanked();
        }
    }

    /**
     * Gives back the sharp pictures of squares no frame has drawn for KEPT_NANOS, and of those put
     * on the card at the other size, then asks for sharp pictures for the nearest of the squares
     * frames draw, taking the slot of the furthest that has one when every slot is taken.
     */
    private static void rank() {
        boolean smaller = Quality.smallerPictures();
        int gave = 0;
        dropSparesOtherThan(smaller ? DRAWN / 2 : DRAWN);
        ArrayList<Square> drawn = new ArrayList<>();
        for (Square square : numbered) {
            boolean stale = frameNanos - square.drawnAt > KEPT_NANOS;
            if (square.sharp != 0 && (stale || square.sharpSmaller != smaller)) {
                giveBack(square);
                gave++;
            }
            if (!stale && square.asRead > 0 && !square.failed) {
                drawn.add(square);
            }
        }
        float[] distance = new float[drawn.size()];
        for (int i = 0; i < distance.length; i++) {
            distance[i] = distanceToEyes(drawn.get(i).places);
        }
        boolean[] wanted = nearestOf(distance, MOST_SHARP);
        for (int i = 0; i < distance.length; i++) {
            Square square = drawn.get(i);
            if (!wanted[i] || square.sharp != 0 || square.drawing) {
                continue;
            }
            if (sharpOrDrawing >= MOST_SHARP) {
                int furthest = -1;
                for (int j = 0; j < distance.length; j++) {
                    if (!wanted[j] && drawn.get(j).sharp != 0 && (furthest < 0 || distance[j] > distance[furthest])) {
                        furthest = j;
                    }
                }
                if (furthest < 0) {
                    break;
                }
                giveBack(drawn.get(furthest));
                gave++;
            }
            square.drawing = true;
            sharpOrDrawing++;
            make(square, true);
        }
        if (gave > 0) {
            if (FirstPerson.TRACE) {
                Log.info("rugs: " + gave + " sharp pictures given back");
            }
        }
    }

    /** Which of the squares at those squared distances are among the most nearest: ties go to the one listed first. */
    static boolean[] nearestOf(float[] distance, int most) {
        Integer[] order = new Integer[distance.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> Float.compare(distance[a], distance[b]));
        boolean[] wanted = new boolean[distance.length];
        for (int i = 0; i < Math.min(most, order.length); i++) {
            wanted[order[i]] = true;
        }
        return wanted;
    }

    private static void giveBack(Square square) {
        if (Uploads.ringed() && Uploads.stored() && square.sharpSmaller == Quality.smallerPictures()) {
            spare.computeIfAbsent(square.sharpSmaller ? DRAWN / 2 : DRAWN, side -> new ArrayDeque<>()).addLast(new Spare(square.sharp, Pipeline.frame()));
        } else {
            GL11.glDeleteTextures(square.sharp);
            Pipeline.forgetTexel(square.sharp);
        }
        square.sharp = 0;
        sharpOrDrawing--;
    }

    /** Render thread, as Flats.letGo, once every build has been let go: gives every picture back to the graphics card and lets go of what was read. A build made after this asks again. */
    static void letGo() {
        int[] names = forget();
        for (int name : names) {
            GL11.glDeleteTextures(name);
        }
        for (ArrayDeque<Spare> kept : spare.values()) {
            for (Spare each : kept) {
                GL11.glDeleteTextures(each.name());
            }
        }
        spare.clear();
        Log.info(names.length + " pictures of rugs are given back to the graphics card");
    }

    /** Ends the round: what is being made for it is dropped, and the next round's numbers go on from this one's. The OpenGL names of every picture this round put on the card. */
    static int[] forget() {
        round++;
        int[] names = new int[numbered.size() * 2];
        int count = 0;
        for (Square square : numbered) {
            if (square.asRead > 0) {
                names[count++] = square.asRead;
            }
            if (square.sharp != 0) {
                names[count++] = square.sharp;
            }
        }
        firstOfRound += numbered.size();
        numbered.clear();
        squares.clear();
        asked.clear();
        made.clear();
        sharpOrDrawing = 0;
        grain = null;
        return Arrays.copyOf(names, count);
    }

    /** Adds a square to this round as read does, for the tests: its picture as read is the OpenGL name given. */
    static Square adopted(int asRead) {
        Square square = new Square(-1 - firstOfRound - numbered.size(), WOVEN, new byte[READ * READ * 4], "test");
        square.asRead = asRead;
        numbered.add(square);
        return square;
    }

    private static Read unpacked(byte[] bytes) {
        float[] colour = new float[READ * READ * 3], cover = new float[READ * READ];
        for (int at = 0; at < READ * READ; at++) {
            for (int c = 0; c < 3; c++) {
                colour[at * 3 + c] = bytes[at * 4 + c] & 0xff;
            }
            cover[at] = (bytes[at * 4 + 3] & 0xff) / 255f;
        }
        return new Read(colour, cover);
    }

    /** A square as read, READ dots to a side, four bytes a dot, solid where the rug covers half a dot: what a frame draws until the sharp picture is made. */
    static byte[] asRead(Read read) {
        float[] colour = filled(read.colour, read.cover);
        byte[] dots = new byte[READ * READ * 4];
        for (int at = 0; at < READ * READ; at++) {
            for (int c = 0; c < 3; c++) {
                dots[at * 4 + c] = (byte) Math.round(Math.clamp(colour[at * 3 + c], 0f, 255f));
            }
            dots[at * 4 + 3] = (byte) (read.cover[at] >= COVERED ? 255 : 0);
        }
        return dots;
    }

    /** The strands of fur and the hair of hides, read from the jar the first time either is wanted. A jar without them is a fault of its packing: said once, and thrown each time after. On the rugs' own thread. */
    private static Grain grain() {
        if (grainFailed != null) {
            throw grainFailed;
        }
        Grain read = grain;
        if (read == null) {
            try {
                float[] fur = lights("/rugs/fur.jpg"), hide = lights("/rugs/hide.jpg");
                read = new Grain(fur, (int) Math.round(Math.sqrt(fur.length)), hide, (int) Math.round(Math.sqrt(hide.length)));
            } catch (RuntimeException failed) {
                grainFailed = new IllegalStateException("the strands of fur and hair for rugs could not be read from the jar, so fur rugs and hides are drawn as read", failed);
                Log.error(grainFailed.getMessage(), failed);
                throw grainFailed;
            }
            grain = read;
        }
        return read;
    }

    /** A square picture in the jar as the light of each dot over the picture's mean light. */
    private static float[] lights(String path) {
        BufferedImage image;
        try (InputStream in = Rugs.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("the jar has no " + path);
            }
            image = ImageIO.read(in);
        } catch (IOException failed) {
            throw new IllegalStateException("could not read " + path, failed);
        }
        int side = image.getWidth();
        if (image.getHeight() != side) {
            throw new IllegalStateException(path + " is not square");
        }
        float[] light = new float[side * side];
        double sum = 0;
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                int rgb = image.getRGB(x, y);
                float lit = 0.299f * (rgb >> 16 & 0xff) + 0.587f * (rgb >> 8 & 0xff) + 0.114f * (rgb & 0xff);
                light[y * side + x] = lit;
                sum += lit;
            }
        }
        float mean = (float) (sum / light.length);
        for (int at = 0; at < light.length; at++) {
            light[at] /= mean;
        }
        return light;
    }

    /**
     * The square of rug a sprite paints, read flat a MARGIN past each edge of its square, as
     * Flats.laid reads a floor: each dot the blend of the four dots of the sprite round the
     * place the game draws that point at, each counted by how solid it is, as four bytes, red,
     * green, blue and how much of the dot it covers. Null for a sprite that paints too little of it.
     */
    private static byte[] sampled(ByteBuffer sheet, int width, int left, int top, int right, int bottom, Want want) {
        int wide = right - left, high = bottom - top;
        if (wide <= 0 || high <= 0) {
            return null;
        }
        byte[] read = new byte[READ * READ * 4];
        float[] blended = new float[4];
        float perU = wide / want.width, perV = high / want.height;
        int painted = 0;
        for (int j = 0; j < READ; j++) {
            for (int i = 0; i < READ; i++) {
                float a = (i + 0.5f - MARGIN) / SIDE, b = (j + 0.5f - MARGIN) / SIDE;
                float u = ((a - b) * SpriteGeometry.PIXELS_ACROSS_PER_TILE * want.scale + want.originU - want.left) * perU - 0.5f;
                float v = (((a + b) * SpriteGeometry.PIXELS_DOWN_PER_TILE - want.lifted * SpriteGeometry.PIXELS_PER_LEVEL) * want.scale + want.originV - want.top) * perV - 0.5f;
                Flats.blend(sheet, width, left, top, wide, high, u, v, blended);
                float covered = blended[3];
                int at = (j * READ + i) * 4;
                if (covered > 0f) {
                    read[at] = (byte) Math.round(blended[0] / covered);
                    read[at + 1] = (byte) Math.round(blended[1] / covered);
                    read[at + 2] = (byte) Math.round(blended[2] / covered);
                }
                read[at + 3] = (byte) Math.round(Math.min(covered, 1f) * 255f);
                painted += covered >= COVERED && a >= 0f && a < 1f && b >= 0f && b < 1f ? 1 : 0;
            }
        }
        return painted >= LEAST_PAINTED * SIDE * SIDE ? read : null;
    }

    /**
     * A square of rug drawn again, DRAWN dots to a side, four bytes a dot: red, green, blue and
     * 255 where it is rug, 0 where it is not. Pure: the tests call it.
     *
     * @param grain the light of the mod's strands of fur or hair over their mean, side dots square, or null for a woven rug
     */
    static byte[] drawn(Read given, int kind, float[] grain, int side) {
        Read read = straightened(given, kind);
        float[] colour = filled(read.colour, read.cover);
        int[] label = null;
        float[][] fields = null;
        float[] palette = null;
        int colours = 0;
        if (kind == WOVEN) {
            palette = palette(colour, read.cover);
            colours = palette.length / 3;
            label = despeckled(labels(colour, palette));
            fields = softened(label, colours);
        }
        byte[] dots = new byte[DRAWN * DRAWN * 4];
        float[] wx = new float[4], wy = new float[4], share = new float[Math.max(colours, 1)];
        int span = grain == null ? 1 : Math.max(1, Math.round(side * GRAIN_REPEAT / (SIDE * UP)));
        for (int y = 0; y < DRAWN; y++) {
            float at = (y + 0.5f) / UP - 0.5f;
            int y0 = (int) Math.floor(at);
            catmullRom(at - y0, wy);
            for (int x = 0; x < DRAWN; x++) {
                float across = (x + 0.5f) / UP - 0.5f;
                int x0 = (int) Math.floor(across);
                catmullRom(across - x0, wx);
                float covered = 0f, red = 0f, green = 0f, blue = 0f;
                if (label != null) {
                    Arrays.fill(share, 0f);
                }
                for (int tj = 0; tj < 4; tj++) {
                    int row = Math.clamp(y0 - 1 + tj, 0, READ - 1) * READ;
                    for (int ti = 0; ti < 4; ti++) {
                        int dot = row + Math.clamp(x0 - 1 + ti, 0, READ - 1);
                        float weight = wx[ti] * wy[tj];
                        covered += weight * read.cover[dot];
                        if (label != null) {
                            for (int c = 0; c < colours; c++) {
                                share[c] += weight * fields[c][dot];
                            }
                        } else {
                            red += weight * colour[dot * 3];
                            green += weight * colour[dot * 3 + 1];
                            blue += weight * colour[dot * 3 + 2];
                        }
                    }
                }
                if (label != null) {
                    int first = 0, second = -1;
                    for (int c = 1; c < colours; c++) {
                        if (share[c] > share[first]) {
                            second = first;
                            first = c;
                        } else if (second < 0 || share[c] > share[second]) {
                            second = c;
                        }
                    }
                    float firstShare = second < 0 ? 1f : Math.clamp(0.5f + MEETING * (share[first] - share[second]), 0f, 1f);
                    int other = second < 0 ? first : second;
                    red = palette[first * 3] * firstShare + palette[other * 3] * (1f - firstShare);
                    green = palette[first * 3 + 1] * firstShare + palette[other * 3 + 1] * (1f - firstShare);
                    blue = palette[first * 3 + 2] * firstShare + palette[other * 3 + 2] * (1f - firstShare);
                }
                if (grain != null) {
                    // A dot drawn spans span dots of the grain each way: it takes their mean, or the grain's finest strands alias.
                    int gx = (int) Math.floor(((float) x / UP - MARGIN) / SIDE * GRAIN_REPEAT * side), gy = (int) Math.floor(((float) y / UP - MARGIN) / SIDE * GRAIN_REPEAT * side);
                    float lit = 0f;
                    for (int sy = 0; sy < span; sy++) {
                        for (int sx = 0; sx < span; sx++) {
                            lit += grain[Math.floorMod(gy + sy, side) * side + Math.floorMod(gx + sx, side)];
                        }
                    }
                    lit /= span * span;
                    float by = 1f + GRAIN_STRENGTH * (lit - 1f);
                    red *= by;
                    green *= by;
                    blue *= by;
                }
                int out = (y * DRAWN + x) * 4;
                dots[out] = (byte) Math.round(Math.clamp(red, 0f, 255f));
                dots[out + 1] = (byte) Math.round(Math.clamp(green, 0f, 255f));
                dots[out + 2] = (byte) Math.round(Math.clamp(blue, 0f, 255f));
                dots[out + 3] = (byte) (covered >= COVERED ? 255 : 0);
            }
        }
        return dots;
    }

    /** The weights of the four dots round a point t of the way from the second to the third, on a curve through them. */
    private static void catmullRom(float t, float[] weights) {
        float t2 = t * t, t3 = t2 * t;
        weights[0] = 0.5f * (-t3 + 2f * t2 - t);
        weights[1] = 0.5f * (3f * t3 - 5f * t2 + 2f);
        weights[2] = 0.5f * (-3f * t3 + 4f * t2 + t);
        weights[3] = 0.5f * (t3 - t2);
    }

    /** The colours with the dots the rug does not cover given the colour of the nearest it does, so no dot drawn at the outline is darkened by an empty one. */
    private static float[] filled(float[] colour, float[] cover) {
        float[] out = colour.clone();
        boolean[] has = new boolean[READ * READ];
        int count = 0;
        for (int at = 0; at < has.length; at++) {
            has[at] = cover[at] >= COVERED;
            count += has[at] ? 1 : 0;
        }
        if (count == 0) {
            return out;
        }
        boolean[] next = new boolean[has.length];
        while (count < has.length) {
            System.arraycopy(has, 0, next, 0, has.length);
            for (int at = 0; at < has.length; at++) {
                if (has[at]) {
                    continue;
                }
                int i = at % READ, j = at / READ, beside = 0;
                float red = 0f, green = 0f, blue = 0f;
                for (int side = 0; side < 4; side++) {
                    int ni = i + (side == 0 ? -1 : side == 1 ? 1 : 0), nj = j + (side == 2 ? -1 : side == 3 ? 1 : 0);
                    if (ni < 0 || nj < 0 || ni >= READ || nj >= READ || !has[nj * READ + ni]) {
                        continue;
                    }
                    int from = (nj * READ + ni) * 3;
                    red += out[from];
                    green += out[from + 1];
                    blue += out[from + 2];
                    beside++;
                }
                if (beside > 0) {
                    out[at * 3] = red / beside;
                    out[at * 3 + 1] = green / beside;
                    out[at * 3 + 2] = blue / beside;
                    next[at] = true;
                    count++;
                }
            }
            boolean[] swap = has;
            has = next;
            next = swap;
        }
        return out;
    }

    /** Whether the dot at in a square as read is on the square, not in the margin read past it. */
    private static boolean onSquare(int at) {
        int a = at % READ - MARGIN, b = at / READ - MARGIN;
        return a >= 0 && b >= 0 && a < SIDE && b < SIDE;
    }

    /**
     * The flat colours of a woven rug: the fewest, up to MOST_COLOURS, that leave the dots it
     * covers no further from them than the game's speckle (SPECKLE), or than NEAR_BEST times what
     * the most colours leave, three numbers a colour. Measured on the rugs' sprites, the error
     * does not always fall with each colour more: a rug's field and border can be one colour
     * apart only once its spots have one of their own. Only the square's own dots are counted:
     * past it is its edge carried on (straightened), which would weigh the edge's colours again.
     */
    static float[] palette(float[] colour, float[] cover) {
        int count = 0;
        for (int at = 0; at < cover.length; at++) {
            count += onSquare(at) && cover[at] >= COVERED ? 1 : 0;
        }
        float[] dots = new float[count * 3];
        int k = 0;
        for (int at = 0; at < cover.length; at++) {
            if (onSquare(at) && cover[at] >= COVERED) {
                System.arraycopy(colour, at * 3, dots, k++ * 3, 3);
            }
        }
        if (count == 0) {
            return new float[] {128f, 128f, 128f};
        }
        float[][] tried = new float[MOST_COLOURS + 1][];
        float[] error = new float[MOST_COLOURS + 1];
        for (int colours = 1; colours <= MOST_COLOURS; colours++) {
            tried[colours] = means(dots, colours);
            error[colours] = error(dots, tried[colours]) / count;
        }
        float enough = Math.max(SPECKLE, NEAR_BEST * error[MOST_COLOURS]);
        int colours = 1;
        while (colours < MOST_COLOURS && error[colours] > enough) {
            colours++;
        }
        return tried[colours];
    }

    /** colours means of the dots, by Lloyd's rounds from starts each as far as can be from those before it. */
    private static float[] means(float[] dots, int colours) {
        int count = dots.length / 3;
        float[] centre = new float[colours * 3];
        for (int at = 0; at < count; at++) {
            for (int c = 0; c < 3; c++) {
                centre[c] += dots[at * 3 + c] / count;
            }
        }
        for (int made = 1; made < colours; made++) {
            int furthest = 0;
            float far = -1f;
            for (int at = 0; at < count; at++) {
                float near = nearestDistance(dots, at, centre, made);
                if (near > far) {
                    far = near;
                    furthest = at;
                }
            }
            System.arraycopy(dots, furthest * 3, centre, made * 3, 3);
        }
        int[] of = new int[count];
        for (int pass = 0; pass < ROUNDS; pass++) {
            for (int at = 0; at < count; at++) {
                of[at] = nearestColour(dots, at, centre, colours);
            }
            float[] sum = new float[colours * 3];
            int[] in = new int[colours];
            for (int at = 0; at < count; at++) {
                in[of[at]]++;
                for (int c = 0; c < 3; c++) {
                    sum[of[at] * 3 + c] += dots[at * 3 + c];
                }
            }
            for (int m = 0; m < colours; m++) {
                for (int c = 0; in[m] > 0 && c < 3; c++) {
                    centre[m * 3 + c] = sum[m * 3 + c] / in[m];
                }
            }
        }
        return centre;
    }

    /** The nearest of the first colours of centre to the dot at. */
    private static int nearestColour(float[] dots, int at, float[] centre, int colours) {
        int which = 0;
        float least = Float.MAX_VALUE;
        for (int m = 0; m < colours; m++) {
            float d = squaredDistance(dots, at, centre, m);
            if (d < least) {
                least = d;
                which = m;
            }
        }
        return which;
    }

    /** The squared distance from the dot at to the nearest of the first colours of centre. */
    private static float nearestDistance(float[] dots, int at, float[] centre, int colours) {
        float least = Float.MAX_VALUE;
        for (int m = 0; m < colours; m++) {
            float d = squaredDistance(dots, at, centre, m);
            if (d < least) {
                least = d;
            }
        }
        return least;
    }

    private static float squaredDistance(float[] dots, int at, float[] centre, int m) {
        float dr = dots[at * 3] - centre[m * 3], dg = dots[at * 3 + 1] - centre[m * 3 + 1], db = dots[at * 3 + 2] - centre[m * 3 + 2];
        return dr * dr + dg * dg + db * db;
    }

    private static float error(float[] dots, float[] centre) {
        float sum = 0f;
        for (int at = 0; at < dots.length / 3; at++) {
            sum += nearestDistance(dots, at, centre, centre.length / 3);
        }
        return sum;
    }

    /** How much of each colour each dot read is, spread by SOFTEN round where each dot of it lies. */
    private static float[][] softened(int[] label, int colours) {
        int reach = (int) Math.ceil(3f * SOFTEN);
        float[] kernel = new float[2 * reach + 1];
        float sum = 0f;
        for (int k = -reach; k <= reach; k++) {
            kernel[k + reach] = (float) Math.exp(-0.5 * k * k / (SOFTEN * SOFTEN));
            sum += kernel[k + reach];
        }
        for (int k = 0; k < kernel.length; k++) {
            kernel[k] /= sum;
        }
        float[][] fields = new float[colours][];
        float[] across = new float[READ * READ];
        for (int c = 0; c < colours; c++) {
            float[] field = new float[READ * READ];
            for (int j = 0; j < READ; j++) {
                for (int i = 0; i < READ; i++) {
                    float total = 0f;
                    for (int k = -reach; k <= reach; k++) {
                        total += label[j * READ + Math.clamp(i + k, 0, READ - 1)] == c ? kernel[k + reach] : 0f;
                    }
                    across[j * READ + i] = total;
                }
            }
            for (int j = 0; j < READ; j++) {
                for (int i = 0; i < READ; i++) {
                    float total = 0f;
                    for (int k = -reach; k <= reach; k++) {
                        total += across[Math.clamp(j + k, 0, READ - 1) * READ + i] * kernel[k + reach];
                    }
                    field[j * READ + i] = total;
                }
            }
            fields[c] = field;
        }
        return fields;
    }

    /** Each dot's nearest colour of the palette. */
    private static int[] labels(float[] colour, float[] palette) {
        int[] label = new int[READ * READ];
        for (int at = 0; at < label.length; at++) {
            label[at] = nearestColour(colour, at, palette, palette.length / 3);
        }
        return label;
    }

    /**
     * Each dot that no more than one of the eight round it shares a colour with given the colour
     * most of them have: a lone dot of the game's speckle is not a spot on the rug, and a spot two
     * dots across, the smallest the game paints on one, is kept.
     */
    private static int[] despeckled(int[] label) {
        int[] out = label.clone();
        int[] votes = new int[MOST_COLOURS];
        for (int j = 0; j < READ; j++) {
            for (int i = 0; i < READ; i++) {
                Arrays.fill(votes, 0);
                for (int dj = -1; dj <= 1; dj++) {
                    for (int di = -1; di <= 1; di++) {
                        if (di != 0 || dj != 0) {
                            votes[label[Math.clamp(j + dj, 0, READ - 1) * READ + Math.clamp(i + di, 0, READ - 1)]]++;
                        }
                    }
                }
                int own = label[j * READ + i];
                if (votes[own] > 1) {
                    continue;
                }
                int most = own;
                for (int c = 0; c < MOST_COLOURS; c++) {
                    most = votes[c] > votes[most] ? c : most;
                }
                out[j * READ + i] = most;
            }
        }
        return out;
    }

    /**
     * The square of rug with its outline cut straight where the rug runs on into the next square:
     * the sprite paints a square as a diamond whose edges are staircases of dots, so read flat the
     * rug stops in teeth a few dots short of its square's edge and sends teeth past it. A dot in
     * the STAIR dots along an edge whose dot STAIR dots further in is rug is rug, of that dot's
     * colour. Past the square a woven rug or a hide is its own edge carried on, dot for dot: the
     * face stops at the square's edge, and a picture that was empty there let the filtering of
     * every sample along the seam take in empty dots, which showed the floor between two squares
     * of one rug. A fur rug's tufts stand past it.
     */
    static Read straightened(Read read, int kind) {
        float[] colour = read.colour.clone(), cover = read.cover.clone();
        for (int j = 0; j < READ; j++) {
            for (int i = 0; i < READ; i++) {
                int at = j * READ + i;
                int a = i - MARGIN, b = j - MARGIN;
                boolean past = a < 0 || b < 0 || a >= SIDE || b >= SIDE;
                if (past || cover[at] >= COVERED) {
                    continue;
                }
                // In a corner the dot STAIR in along one edge is still on the other edge's staircase: it steps in along both.
                int toWest = a, toEast = SIDE - 1 - a, toNorth = b, toSouth = SIDE - 1 - b;
                int stepI = toWest < STAIR ? STAIR + 1 - toWest : toEast < STAIR ? -(STAIR + 1 - toEast) : 0;
                int stepJ = toNorth < STAIR ? STAIR + 1 - toNorth : toSouth < STAIR ? -(STAIR + 1 - toSouth) : 0;
                if (stepI == 0 && stepJ == 0) {
                    continue;
                }
                int inward = (j + stepJ) * READ + i + stepI;
                if (read.cover[inward] >= COVERED) {
                    cover[at] = 1f;
                    System.arraycopy(read.colour, inward * 3, colour, at * 3, 3);
                }
            }
        }
        if (kind != FUR) {
            for (int j = 0; j < READ; j++) {
                for (int i = 0; i < READ; i++) {
                    int a = i - MARGIN, b = j - MARGIN;
                    if (a >= 0 && b >= 0 && a < SIDE && b < SIDE) {
                        continue;
                    }
                    int at = j * READ + i, edge = (Math.clamp(b, 0, SIDE - 1) + MARGIN) * READ + Math.clamp(a, 0, SIDE - 1) + MARGIN;
                    cover[at] = cover[edge];
                    System.arraycopy(colour, edge * 3, colour, at * 3, 3);
                }
            }
        }
        return new Read(colour, cover);
    }

    /** The picture and each half of the one before it down to a dot, side dots square at the first: a dot the mean of the four it stands for. */
    static byte[][] levels(byte[] dots, int side) {
        int count = 1;
        for (int s = side; s > 1; s /= 2) {
            count++;
        }
        byte[][] levels = new byte[count][];
        levels[0] = dots;
        for (int level = 1, from = side; level < count; level++, from /= 2) {
            int to = Math.max(1, from / 2);
            byte[] above = levels[level - 1], here = new byte[to * to * 4];
            for (int y = 0; y < to; y++) {
                for (int x = 0; x < to; x++) {
                    for (int part = 0; part < 4; part++) {
                        int sum = 0;
                        for (int k = 0; k < 4; k++) {
                            int ax = Math.min(2 * x + (k & 1), from - 1), ay = Math.min(2 * y + (k >> 1), from - 1);
                            sum += above[(ay * from + ax) * 4 + part] & 0xff;
                        }
                        here[(y * to + x) * 4 + part] = (byte) (sum + 2 >> 2);
                    }
                }
            }
            levels[level] = here;
        }
        return levels;
    }

    /** What picture gives when the graphics card is still reading the ring there is room in: asked again the next frame. No texture is named 0. */
    private static final int NOT_TAKEN = 0;

    /** Puts a picture and its smaller copies on the graphics card from the level first on, and gives its name, or NOT_TAKEN. */
    private static int picture(byte[][] levels, int first) {
        if (!Uploads.ringed() || !Uploads.stored()) {
            return pictureFromMemory(levels, first);
        }
        int bytes = 0;
        for (int level = first; level < levels.length; level++) {
            bytes += levels[level].length;
        }
        long offset = Uploads.take(bytes);
        if (offset < 0) {
            return NOT_TAKEN;
        }
        ByteBuffer ring = Uploads.at(offset, bytes);
        for (int level = first; level < levels.length; level++) {
            ring.put(levels[level]);
        }
        int side = sideOf(levels[first]);
        ArrayDeque<Spare> kept = spare.get(side);
        int name = kept == null || kept.isEmpty() || Pipeline.frame() - kept.peekFirst().frame() < SPARE_FRAMES ? made(side, levels.length - first)
                : kept.pollFirst().name();
        fill(name, levels, first, Uploads.place(offset));
        return name;
    }

    private static int sideOf(byte[] level) {
        return (int) Math.round(Math.sqrt(level.length / 4.0));
    }

    /**
     * Sharp pictures given back, kept by their size for the next square to be drawn sharp. A
     * texture made anew cost 15 to 25 ms of the driver's in the middle of a frame however its
     * dots reached it; one kept is only written over.
     */
    private static final HashMap<Integer, ArrayDeque<Spare>> spare = new HashMap<>();

    /** A sharp picture given back and the frame it was: written over only SPARE_FRAMES on, when the card has finished drawing with it. */
    private record Spare(int name, int frame) {}

    private static final int SPARE_FRAMES = 4;

    /** Lets go of the sharp pictures kept at the size not in use: after a change of Quality.smallerPictures none of them is taken again. */
    private static void dropSparesOtherThan(int side) {
        for (var each = spare.entrySet().iterator(); each.hasNext();) {
            var entry = each.next();
            if (entry.getKey() != side) {
                for (Spare kept : entry.getValue()) {
                    GL11.glDeleteTextures(kept.name());
                    Pipeline.forgetTexel(kept.name());
                }
                each.remove();
            }
        }
    }

    /** A texture with room for a picture side dots square and its smaller copies, all made at once. */
    private static int made(int side, int levels) {
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, name);
        GL42.glTexStorage2D(GL_TEXTURE_2D, levels, GL_RGBA8, side, side);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        Pictures.sharpenAtSlant();
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        return name;
    }

    /** Writes the levels from first on into the texture, out of the ring at its place there. */
    private static void fill(int name, byte[][] levels, int first, long at) {
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        int unpackBuffer = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        Uploads.bind();
        Pictures.unpackTight();
        GL11.glBindTexture(GL_TEXTURE_2D, name);
        for (int level = first; level < levels.length; level++) {
            int side = sideOf(levels[level]);
            GL11.glTexSubImage2D(GL_TEXTURE_2D, level - first, 0, 0, side, side, GL_RGBA, GL_UNSIGNED_BYTE, at);
            at += levels[level].length;
        }
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
    }

    private static int pictureFromMemory(byte[][] levels, int first) {
        int was = GL11.glGetInteger(GL_TEXTURE_BINDING_2D);
        // With a buffer bound to unpack from, OpenGL would read the picture out of that buffer.
        int unpackBuffer = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        Pictures.unpackTight();
        int name = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, name);
        for (int level = first; level < levels.length; level++) {
            int side = (int) Math.round(Math.sqrt(levels[level].length / 4.0));
            ByteBuffer bytes = ByteBuffer.allocateDirect(levels[level].length).order(ByteOrder.nativeOrder());
            bytes.put(levels[level]).flip();
            GL11.glTexImage2D(GL_TEXTURE_2D, level - first, GL_RGBA8, side, side, 0, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
        }
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        Pictures.sharpenAtSlant();
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        GL11.glBindTexture(GL_TEXTURE_2D, was);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        return name;
    }
}
