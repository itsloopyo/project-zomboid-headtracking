package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLongArray;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import zombie.ZomboidFileSystem;
import zombie.iso.BuildingDef;
import zombie.iso.IsoMetaCell;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoWorld;
import zombie.iso.RoomDef;

/**
 * The land beyond what the game has loaded. The game keeps the map about the player for some
 * seventy tiles each way, and the picture was built of that and ended there: from a roof
 * twenty floors up there was a disc of town and nothing round it. The game also ships a picture
 * of its whole map, a pixel a tile, for the map the player unfolds, and the rooms of every
 * building on it. The far land is that picture laid over the lie of the land the built world
 * has (Terrain.far), with each building a plain block as high as its floors: no props, no
 * trees, nobody. The built world thins out into it (PipelineShaders.ENDS).
 *
 * It is kept a cell of the map at a time, for AROUND cells each way of the eye's. A cell is
 * asked for by the logic thread, which reads its buildings from the game, made on a thread of
 * its own, and handed to the graphics card by the render thread.
 *
 * One of these is the land round one view's eye: two players may be anywhere on the map.
 * The map's pictures and the thread that makes cells from them serve every view.
 */
final class FarLand {
    /** Cells a side of what the graphics card holds. A power of two: a cell's place there is the low bits of its own number, so the picture needs no moving as the eye moves. */
    private static final int WINDOW = 8;
    /** How many cells each way of the eye's own are kept: with the eye's, one fewer than WINDOW, so no two of them want one place. */
    private static final int AROUND = 3;
    /** Land is there at least this far from the eye every way, in tiles, and the picture ends there. */
    static final float REACH = AROUND * FarCells.CELL;
    /** Tiles a side of the whole picture the graphics card holds. */
    static final int PICTURE = WINDOW * FarCells.CELL;
    private static final long NONE = Long.MIN_VALUE;
    /** How many cells are asked for and not yet on the graphics card at once: each is a third of a megabyte and more outside the Java heap until it is. */
    private static final int WAITING_AT_ONCE = 4;
    /** How many cells a frame are handed to the graphics card: each is a third of a megabyte of picture. */
    private static final int LANDED_A_FRAME = 2;

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401, GL_FLOAT = 0x1406;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_REPEAT = 0x2901, GL_TEXTURE_MAX_LEVEL = 0x813D;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_STATIC_DRAW = 0x88E4, GL_TRIANGLES = 0x0004, GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_VERTEX_ARRAY = 0x8074, GL_NORMAL_ARRAY = 0x8075, GL_COLOR_ARRAY = 0x8076, GL_TEXTURE_COORD_ARRAY = 0x8078, GL_TEXTURE0 = 0x84C0;

    /** A picture of the map the game ships: where the file is and which tiles it is of. */
    private record Source(Path zip, int minX, int minY, int maxX, int maxY) {}

    /**
     * What has been asked for since the view last let go of what it held. A new one takes its
     * place each time it does, so whatever was asked for before is known by all three threads to
     * be of a map, or a holding, that is over.
     */
    private static final class Asked {
        /** The cell each place of the window was last asked to hold. Set by the logic thread, read by the render thread. */
        final AtomicLongArray cells = new AtomicLongArray(WINDOW * WINDOW);
        /** How many cells are asked for and not yet handed to the graphics card. */
        final AtomicInteger waiting = new AtomicInteger();
        /** What stopped the far land, once anything has. */
        volatile String failure;
        /** The cell the eye was in when cells were last asked for round it, NONE until they first are. Set by the logic thread. */
        volatile long round = NONE;
        /** When the first cell was asked for. */
        volatile long since;
        /** Whether it has been said that every cell round the eye is on the graphics card. Render thread only. */
        boolean wholeSaid;

        Asked() {
            for (int place = 0; place < WINDOW * WINDOW; place++) {
                cells.set(place, NONE);
            }
        }
    }

    /** A cell as its thread made it: its picture, then its land, then its buildings, then its trees, in one block the render thread frees. */
    private record Made(Asked asked, long cell, OffHeap.Block block, int blockVertices, int trees) {}

    /** Logic thread. Whether the map's pictures have been looked for in this save, and what was found. */
    private static boolean looked;
    private static volatile List<Source> sources = List.of();
    /** Logic thread, read by the render thread: the kinds of the far trees in this save, or null where it has none. */
    static volatile FarTreeKinds treeKinds;
    /** Replaced by the logic thread, read by all three. */
    private volatile Asked asked = new Asked();
    /** What was asked for and the cell it was asked round, once every cell kept round it is on the graphics card: see whole. */
    private record Whole(Asked asked, long round) {}
    /** Set by the render thread, read by the logic thread. */
    private volatile Whole whole;
    private final ConcurrentLinkedQueue<Made> made = new ConcurrentLinkedQueue<>();
    /** Steps from the eye's cell to each cell kept, as pairs, the nearest first. */
    private static final int[] NEAREST_FIRST = nearestFirst();

    /** The cells' thread only. */
    private static final HashMap<Path, ZipFile> open = new HashMap<>();
    private static final HashMap<Long, int[]> waters = new HashMap<>();
    private static final int WATERS_KEPT = 1024;

    /** Render thread only. */
    private int picture;
    private final long[] shown = new long[WINDOW * WINDOW];
    private final int[] buffers = new int[WINDOW * WINDOW], blockVertices = new int[WINDOW * WINDOW];
    private final int[] treeBuffers = new int[WINDOW * WINDOW], trees = new int[WINDOW * WINDOW];
    private boolean covered;
    /** What the cells on the graphics card were asked for by. */
    private Asked held = asked;

    FarLand() {
        Arrays.fill(shown, NONE);
    }

    /** One thread, a step under the game's own, that goes when there has been nothing to make for a while. */
    private static final ThreadPoolExecutor WORKER = worker();

    private static ThreadPoolExecutor worker() {
        ThreadPoolExecutor pool = new ThreadPoolExecutor(1, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), work -> {
            Thread thread = new Thread(work, "CameraUnlock far land");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }


    private static int[] nearestFirst() {
        int side = 2 * AROUND + 1;
        Integer[] order = new Integer[side * side];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (a, b) -> {
            int ax = a % side - AROUND, ay = a / side - AROUND, bx = b % side - AROUND, by = b / side - AROUND;
            return Integer.compare(ax * ax + ay * ay, bx * bx + by * by);
        });
        int[] steps = new int[order.length * 2];
        for (int i = 0; i < order.length; i++) {
            steps[i * 2] = order[i] % side - AROUND;
            steps[i * 2 + 1] = order[i] / side - AROUND;
        }
        return steps;
    }

    private static long key(int cellX, int cellY) {
        return (long) cellX << 32 | cellY & 0xffffffffL;
    }

    private static int place(int cellX, int cellY) {
        return (cellY & WINDOW - 1) * WINDOW + (cellX & WINDOW - 1);
    }

    /**
     * Logic thread, each frame the picture is built from the eye of someone standing in the
     * world: asks for whichever cells round the eye are not yet asked for, the nearest first.
     */
    void tend(float eyeX, float eyeY) {
        Asked asked = this.asked;
        if (asked.failure != null) {
            return;
        }
        if (!looked) {
            looked = true;
            sources = find(IsoWorld.instance.getMetaGrid());
            try {
                treeKinds = FarTrees.read();
                Log.info(treeKinds == null ? "far trees: this save has no world generation to say what its trees are, and the land beyond the loaded map has none"
                        : "far trees: " + treeKinds.species.size() + " species in " + treeKinds.biomes.size() + " biomes");
            } catch (ReflectiveOperationException | RuntimeException thrown) {
                treeKinds = null;
                Log.error("far trees: the game's lists of trees could not be read, and the land beyond the loaded map has none", thrown);
            }
        }
        if (sources.isEmpty()) {
            return;
        }
        int eyeCellX = Math.floorDiv((int) Math.floor(eyeX), FarCells.CELL), eyeCellY = Math.floorDiv((int) Math.floor(eyeY), FarCells.CELL);
        if (asked.round == NONE) {
            asked.since = System.nanoTime();
        }
        asked.round = key(eyeCellX, eyeCellY);
        for (int step = 0; step < NEAREST_FIRST.length && asked.waiting.get() < WAITING_AT_ONCE; step += 2) {
            int cellX = eyeCellX + NEAREST_FIRST[step], cellY = eyeCellY + NEAREST_FIRST[step + 1];
            long cell = key(cellX, cellY);
            int place = place(cellX, cellY);
            if (asked.cells.get(place) == cell) {
                continue;
            }
            int[] buildings = buildings(IsoWorld.instance.getMetaGrid(), cellX, cellY);
            List<Source> from = sources;
            FarTreeKinds kinds = treeKinds;
            FarTrees.Lot lot = kinds == null ? null : FarTrees.lot(cellX, cellY);
            asked.cells.set(place, cell);
            asked.waiting.incrementAndGet();
            WORKER.execute(() -> make(asked, from, cellX, cellY, buildings, kinds, lot));
        }
    }

    /** The pictures of the map this save is played on, the first to be asked the one the game itself puts first. */
    private static List<Source> find(IsoMetaGrid grid) {
        ArrayList<Source> found = new ArrayList<>();
        for (String directory : grid.getLotDirectories()) {
            Path zip = Path.of(ZomboidFileSystem.instance.getString("media/maps/" + directory + "/pyramid.zip"));
            if (!Files.isRegularFile(zip)) {
                continue;
            }
            try (ZipFile file = new ZipFile(zip.toFile())) {
                Source source = source(zip, file);
                if (source != null) {
                    found.add(source);
                }
            } catch (IOException | RuntimeException thrown) {
                Log.error("far land: " + zip + " cannot be read, and the land beyond the loaded map is drawn without it", thrown);
            }
        }
        if (found.isEmpty()) {
            Log.info("far land: this map ships no picture of itself a pixel a tile, so nothing is drawn beyond what the game has loaded");
        }
        for (Source source : found) {
            Log.info(String.format(Locale.ROOT, "far land: the land beyond the loaded map is taken from %s, tiles %d, %d to %d, %d", source.zip, source.minX, source.minY, source.maxX, source.maxY));
        }
        return List.copyOf(found);
    }

    /** What a picture's own index says it is of, or null where it is not a pixel a tile from the corner of a cell. */
    private static Source source(Path zip, ZipFile file) throws IOException {
        ZipEntry index = file.getEntry("pyramid.txt");
        if (index == null) {
            Log.info("far land: " + zip + " has no pyramid.txt and is left out");
            return null;
        }
        int[] bounds = null, size = null;
        String text;
        try (InputStream in = file.getInputStream(index)) {
            text = indexText(in, zip);
        }
        for (String line : text.split("\\R")) {
            if (line.startsWith("bounds=")) {
                bounds = numbers(line.substring("bounds=".length()));
            } else if (line.startsWith("imageSize=")) {
                size = numbers(line.substring("imageSize=".length()));
            }
        }
        if (bounds == null || size == null || bounds.length != 4 || size.length != 2) {
            Log.info("far land: the pyramid.txt of " + zip + " gives no bounds and imageSize, and it is left out");
            return null;
        }
        if (size[0] != bounds[2] - bounds[0] || size[1] != bounds[3] - bounds[1] || bounds[0] % FarCells.CELL != 0 || bounds[1] % FarCells.CELL != 0) {
            Log.info(String.format(Locale.ROOT, "far land: %s is %d by %d pixels for tiles %d, %d to %d, %d, which is not a pixel a tile from the corner of a cell, and it is left out",
                    zip, size[0], size[1], bounds[0], bounds[1], bounds[2], bounds[3]));
            return null;
        }
        return new Source(zip, bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    /** The game's own are under a hundred bytes. */
    static final int MOST_INDEX_BYTES = 64 * 1024;

    /** A pyramid.txt as text, read no further than a sane index runs: a zip entry can say it is any size and unpack to gigabytes. */
    static String indexText(InputStream in, Path zip) throws IOException {
        byte[] bytes = in.readNBytes(MOST_INDEX_BYTES + 1);
        if (bytes.length > MOST_INDEX_BYTES) {
            throw new IOException("the pyramid.txt of " + zip + " is larger than " + MOST_INDEX_BYTES + " bytes");
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static int[] numbers(String text) {
        String[] parts = text.trim().split("\\s+");
        int[] numbers = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
        }
        return numbers;
    }

    /** The buildings the game has in a cell, as FarCells.blocks takes them. */
    private static int[] buildings(IsoMetaGrid grid, int cellX, int cellY) {
        IsoMetaCell cell = grid.getCellData(cellX, cellY);
        if (cell == null) {
            return new int[0];
        }
        int pieces = 0;
        for (BuildingDef building : cell.buildings) {
            for (RoomDef room : building.rooms) {
                pieces += room.rects.size();
            }
        }
        int[] listed = new int[cell.buildings.size() * 2 + pieces * 5];
        int at = 0;
        for (BuildingDef building : cell.buildings) {
            listed[at++] = Long.hashCode(building.id);
            int count = at++;
            for (RoomDef room : building.rooms) {
                for (RoomDef.RoomRect piece : room.rects) {
                    listed[at++] = piece.x;
                    listed[at++] = piece.y;
                    listed[at++] = piece.w;
                    listed[at++] = piece.h;
                    listed[at++] = room.level;
                    listed[count]++;
                }
            }
        }
        return listed;
    }

    /** The cells' thread: makes a cell and hands it over, or stops the far land and says why. */
    private void make(Asked asked, List<Source> from, int cellX, int cellY, int[] buildings, FarTreeKinds kinds, FarTrees.Lot lot) {
        OffHeap.Block block = null;
        try {
            int[][] around = new int[9][];
            int[] pixels = null;
            for (int j = -1; j <= 1; j++) {
                for (int i = -1; i <= 1; i++) {
                    long near = key(cellX + i, cellY + j);
                    int[] known = waters.get(near);
                    if (known == null || i == 0 && j == 0) {
                        int[] read = read(from, cellX + i, cellY + j);
                        if (known == null) {
                            if (waters.size() >= WATERS_KEPT) {
                                waters.clear();
                            }
                            known = FarCells.waters(read);
                            waters.put(near, known);
                        }
                        if (i == 0 && j == 0) {
                            pixels = read;
                        }
                    }
                    around[(j + 1) * 3 + i + 1] = known;
                }
            }
            ByteBuffer blocks = FarCells.blocks(buildings, around, cellX, cellY);
            ByteBuffer treesMade = lot == null ? ByteBuffer.allocate(0) : FarTrees.make(kinds, lot, cellX, cellY, around);
            int land = FarCells.LAND_VERTICES * FarCells.VERTEX_BYTES;
            block = OffHeap.take(FarCells.PICTURE_BYTES + land + blocks.remaining() + treesMade.remaining());
            ByteBuffer bytes = block.bytes;
            bytes.clear();
            FarCells.picture(pixels, bytes);
            FarCells.land(around, cellX, cellY, bytes);
            int vertices = blocks.remaining() / FarCells.VERTEX_BYTES;
            bytes.put(blocks);
            int treeCount = treesMade.remaining() / FarTrees.TREE_BYTES;
            bytes.put(treesMade);
            made.add(new Made(asked, key(cellX, cellY), block, vertices, treeCount));
        } catch (IOException | RuntimeException | LinkageError thrown) {
            if (block != null) {
                block.free();
            }
            asked.waiting.decrementAndGet();
            asked.failure = "the cell " + cellX + ", " + cellY + " could not be made: " + thrown;
            Log.error("far land: " + asked.failure + ". No more of the land beyond the loaded map is made until the view has next let go of what it holds", thrown);
        }
    }

    /** The picture of a cell, a row of tiles at a time, or null where no picture of the map has it. */
    private static int[] read(List<Source> from, int cellX, int cellY) throws IOException {
        int x = cellX * FarCells.CELL, y = cellY * FarCells.CELL;
        for (Source source : from) {
            if (x < source.minX || y < source.minY || x >= source.maxX || y >= source.maxY) {
                continue;
            }
            ZipFile file = open.get(source.zip);
            if (file == null) {
                file = new ZipFile(source.zip.toFile());
                open.put(source.zip, file);
            }
            ZipEntry tile = file.getEntry("0/tile" + (x - source.minX) / FarCells.CELL + "x" + (y - source.minY) / FarCells.CELL + ".png");
            if (tile == null) {
                continue;
            }
            BufferedImage image;
            try (InputStream in = file.getInputStream(tile)) {
                image = tile(in, tile.getName() + " of " + source.zip);
            }
            int[] pixels = new int[FarCells.CELL * FarCells.CELL];
            Arrays.fill(pixels, FarCells.GRASS);
            // The last tile of a map that is not a whole number of cells across is the narrower.
            int wide = Math.min(image.getWidth(), FarCells.CELL), high = Math.min(image.getHeight(), FarCells.CELL);
            image.getRGB(0, 0, wide, high, pixels, 0, FarCells.CELL);
            if (FarCells.nothing(pixels)) {
                continue;
            }
            return pixels;
        }
        return null;
    }

    /**
     * A cell's picture, its size read before its dots: a header can claim any size, and Java's
     * readers make room for all of it before reading a dot, so a file of a few hundred bytes
     * could take the heap the game runs in.
     */
    static BufferedImage tile(InputStream from, String what) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(from)) {
            Iterator<ImageReader> decoders = in == null ? null : ImageIO.getImageReaders(in);
            if (decoders == null || !decoders.hasNext()) {
                throw new IOException(what + " is not a picture this Java can read");
            }
            ImageReader decoder = decoders.next();
            try {
                decoder.setInput(in);
                int wide = decoder.getWidth(0), high = decoder.getHeight(0);
                if (wide <= 0 || high <= 0 || wide > FarCells.CELL || high > FarCells.CELL) {
                    throw new IOException(what + " is " + wide + " by " + high + " pixels, where a cell is at most " + FarCells.CELL + " a side");
                }
                return decoder.read(0);
            } finally {
                decoder.dispose();
            }
        }
    }

    /**
     * Logic thread, as the view lets go of what it holds or the save is left: nothing is asked
     * for any more and what comes back from the cells' thread is not wanted. The graphics
     * card's share is catchUp's.
     */
    void forget() {
        asked = new Asked();
    }

    /**
     * Logic thread: whether every cell kept round the eye the far land was last tended round is
     * on the graphics card, or there is nothing more to wait for: a map with no picture of
     * itself, or a far land that has stopped.
     */
    boolean ready() {
        Asked now = asked;
        if (now.failure != null || looked && sources.isEmpty()) {
            return true;
        }
        Whole known = whole;
        return known != null && known.asked == now && known.round == now.round;
    }

    /** Logic thread, once no view holds any of the far land: the map's pictures are looked for afresh by the next save. */
    static void forgetTheMap() {
        looked = false;
        sources = List.of();
        treeKinds = null;
        FarTreeLooks.forget();
        WORKER.execute(() -> {
            waters.clear();
            FarTrees.forget();
            for (ZipFile file : open.values()) {
                try {
                    file.close();
                } catch (IOException thrown) {
                    Log.error("far land: a picture of the map could not be closed", thrown);
                }
            }
            open.clear();
        });
    }

    /**
     * Render thread, once a frame before anything is drawn: hands the graphics card the cells
     * that have been made, and says whether the far land is there to be drawn round this eye.
     * It is once the eye's own cell and the eight round it are, so the built world never thins
     * out into a cell that has not come yet.
     */
    boolean load(float eyeX, float eyeY) {
        hand();
        if (picture == 0) {
            return false;
        }
        int eyeCellX = Math.floorDiv((int) Math.floor(eyeX), FarCells.CELL), eyeCellY = Math.floorDiv((int) Math.floor(eyeY), FarCells.CELL);
        boolean all = true;
        for (int j = -1; j <= 1 && all; j++) {
            for (int i = -1; i <= 1 && all; i++) {
                all = shown[place(eyeCellX + i, eyeCellY + j)] == key(eyeCellX + i, eyeCellY + j);
            }
        }
        if (all != covered) {
            covered = all;
            Log.info(all ? "far land: the land beyond the loaded map is on the graphics card round the viewer and is drawn"
                    : "far land: the land round the viewer is not all on the graphics card, and the picture ends at the loaded map until it is");
        }
        return all;
    }

    /**
     * Render thread, once a frame while first person is asked for or drawn: hands the graphics
     * card the cells that have been made, and notes whether every cell kept round the eye they
     * were asked round is there (ready).
     */
    void hand() {
        for (int landed = 0; landed < LANDED_A_FRAME; landed++) {
            Made cell = made.poll();
            if (cell == null) {
                break;
            }
            // Looked at after the cell was taken: what is asked for now is what the cell was asked for by, or something since.
            catchUp();
            if (cell.asked == held) {
                land(cell);
            } else {
                cell.block.free();
            }
        }
        catchUp();
        Asked now = held;
        long round = now.round;
        if (round == NONE || picture == 0) {
            return;
        }
        int roundX = (int) (round >> 32), roundY = (int) round;
        for (int step = 0; step < NEAREST_FIRST.length; step += 2) {
            int cellX = roundX + NEAREST_FIRST[step], cellY = roundY + NEAREST_FIRST[step + 1];
            if (shown[place(cellX, cellY)] != key(cellX, cellY)) {
                return;
            }
        }
        Whole known = whole;
        if (known == null || known.asked != now || known.round != round) {
            whole = new Whole(now, round);
        }
        if (!now.wholeSaid) {
            now.wholeSaid = true;
            Log.info(String.format(Locale.ROOT, "far land: all %d cells round the viewer are on the graphics card, %.0f ms after the first was asked for",
                    NEAREST_FIRST.length / 2, (System.nanoTime() - now.since) / 1e6));
        }
    }

    private void land(Made cell) {
        int cellX = (int) (cell.cell >> 32), cellY = (int) cell.cell;
        int place = place(cellX, cellY);
        ByteBuffer bytes = cell.block.bytes;
        cell.asked.waiting.decrementAndGet();
        if (cell.asked.cells.get(place) != cell.cell) {
            cell.block.free();
            return;
        }
        if (picture == 0) {
            picture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, picture);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, FarCells.LEVELS - 1);
            Pictures.sharpenAtSlant();
            // Every place starts as open grass: a cell that has come is smoothed into the ones beside it that have not.
            OffHeap.Block grass = OffHeap.take(FarCells.PICTURE_BYTES);
            grass.bytes.clear();
            FarCells.picture(null, grass.bytes);
            for (int level = 0, side = PICTURE; level < FarCells.LEVELS; level++, side /= 2) {
                GL11.glTexImage2D(GL_TEXTURE_2D, level, GL_RGBA8, side, side, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            for (int at = 0; at < WINDOW * WINDOW; at++) {
                picture(at, grass.bytes);
            }
            grass.free();
        }
        GL11.glBindTexture(GL_TEXTURE_2D, picture);
        picture(place, bytes);
        GL11.glBindTexture(GL_TEXTURE_2D, 0);
        if (buffers[place] == 0) {
            buffers[place] = GL15.glGenBuffers();
        }
        int end = FarCells.PICTURE_BYTES + (FarCells.LAND_VERTICES + cell.blockVertices) * FarCells.VERTEX_BYTES;
        bytes.position(FarCells.PICTURE_BYTES).limit(end);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, buffers[place]);
        GL15.glBufferData(GL_ARRAY_BUFFER, bytes, GL_STATIC_DRAW);
        if (cell.trees > 0) {
            if (treeBuffers[place] == 0) {
                treeBuffers[place] = GL15.glGenBuffers();
            }
            bytes.position(end).limit(end + cell.trees * FarTrees.TREE_BYTES);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, treeBuffers[place]);
            GL15.glBufferData(GL_ARRAY_BUFFER, bytes, GL_STATIC_DRAW);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        shown[place] = cell.cell;
        blockVertices[place] = cell.blockVertices;
        trees[place] = cell.trees;
        cell.block.free();
    }

    /** Puts a cell's picture and its halvings, as FarCells.picture wrote them from the start of bytes, at its place in the texture bound. */
    private static void picture(int place, ByteBuffer bytes) {
        int from = 0;
        for (int level = 0, side = FarCells.CELL; level < FarCells.LEVELS; level++, side /= 2) {
            bytes.position(from).limit(from + side * side * 4);
            GL11.glTexSubImage2D(GL_TEXTURE_2D, level, place % WINDOW * side, place / WINDOW * side, side, side, GL_RGBA, GL_UNSIGNED_BYTE, bytes);
            from += side * side * 4;
        }
    }

    /**
     * Render thread, after load has said the land is there: draws every cell kept round the
     * eye with whatever program is in use, the picture on the first texture unit. The vertex
     * arrays are left switched off and nothing is left bound.
     */
    void draw(float eyeX, float eyeY) {
        int eyeCellX = Math.floorDiv((int) Math.floor(eyeX), FarCells.CELL), eyeCellY = Math.floorDiv((int) Math.floor(eyeY), FarCells.CELL);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, picture);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        GL11.glEnableClientState(GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        for (int step = 0; step < NEAREST_FIRST.length; step += 2) {
            int cellX = eyeCellX + NEAREST_FIRST[step], cellY = eyeCellY + NEAREST_FIRST[step + 1];
            int place = place(cellX, cellY);
            if (shown[place] != key(cellX, cellY)) {
                continue;
            }
            GL15.glBindBuffer(GL_ARRAY_BUFFER, buffers[place]);
            GL11.glVertexPointer(3, GL_FLOAT, FarCells.VERTEX_BYTES, 0L);
            GL11.glNormalPointer(GL_FLOAT, FarCells.VERTEX_BYTES, 12L);
            GL11.glColorPointer(4, GL_UNSIGNED_BYTE, FarCells.VERTEX_BYTES, 24L);
            GL11.glTexCoordPointer(1, GL_FLOAT, FarCells.VERTEX_BYTES, 28L);
            GL11.glDrawArrays(GL_TRIANGLE_STRIP, 0, FarCells.LAND_VERTICES);
            if (blockVertices[place] > 0) {
                GL11.glDrawArrays(GL_TRIANGLES, FarCells.LAND_VERTICES, blockVertices[place]);
            }
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glBindTexture(GL_TEXTURE_2D, 0);
    }

    /** How far from the eye, in tiles, a cell of trees is drawn with the finer of the two tree meshes. */
    private static final float FINER_TREES_WITHIN = 200f;

    /**
     * Render thread, after draw: the trees of every cell kept round the eye that comes within
     * reach tiles of it, with the program in use, which reads each tree from the attribute given
     * (FarTrees.TREE_BYTES a tree). meshes and vertices are the finer and the coarser tree mesh,
     * three floats a vertex. Nothing is left bound.
     */
    void drawTrees(float eyeX, float eyeY, float reach, int attribute, int[] meshes, int[] vertices) {
        int eyeCellX = Math.floorDiv((int) Math.floor(eyeX), FarCells.CELL), eyeCellY = Math.floorDiv((int) Math.floor(eyeY), FarCells.CELL);
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL20.glEnableVertexAttribArray(attribute);
        GL33.glVertexAttribDivisor(attribute, 1);
        for (int step = 0; step < NEAREST_FIRST.length; step += 2) {
            int cellX = eyeCellX + NEAREST_FIRST[step], cellY = eyeCellY + NEAREST_FIRST[step + 1];
            int place = place(cellX, cellY);
            if (shown[place] != key(cellX, cellY) || trees[place] == 0) {
                continue;
            }
            float minX = cellX * FarCells.CELL, minY = cellY * FarCells.CELL;
            float awayX = Math.max(0f, Math.max(minX - eyeX, eyeX - minX - FarCells.CELL));
            float awayY = Math.max(0f, Math.max(minY - eyeY, eyeY - minY - FarCells.CELL));
            float away = (float) Math.hypot(awayX, awayY);
            if (away > reach) {
                continue;
            }
            int lod = away < FINER_TREES_WITHIN ? 0 : 1;
            GL15.glBindBuffer(GL_ARRAY_BUFFER, meshes[lod]);
            GL11.glVertexPointer(3, GL_FLOAT, 3 * Float.BYTES, 0L);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, treeBuffers[place]);
            GL20.glVertexAttribPointer(attribute, 4, GL_FLOAT, false, FarTrees.TREE_BYTES, 0L);
            GL31.glDrawArraysInstanced(GL_TRIANGLES, 0, vertices[lod], trees[place]);
        }
        GL33.glVertexAttribDivisor(attribute, 0);
        GL20.glDisableVertexAttribArray(attribute);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Render thread. Once the logic thread has forgotten what it asked for, what the graphics
     * card holds was asked for by something that is over: the picture and every cell are given
     * back, and whatever was made for it and never handed over is freed. Pipeline.LET_GO calls
     * this as the view lets go, and load before it hands anything over.
     */
    void catchUp() {
        Asked now = asked;
        if (now == held) {
            return;
        }
        held = now;
        ArrayList<Made> wanted = new ArrayList<>();
        for (Made cell = made.poll(); cell != null; cell = made.poll()) {
            if (cell.asked == asked) {
                wanted.add(cell);
            } else {
                cell.block.free();
            }
        }
        made.addAll(wanted);
        if (picture == 0) {
            return;
        }
        GL11.glDeleteTextures(picture);
        picture = 0;
        for (int place = 0; place < WINDOW * WINDOW; place++) {
            if (buffers[place] != 0) {
                GL15.glDeleteBuffers(buffers[place]);
                buffers[place] = 0;
            }
            if (treeBuffers[place] != 0) {
                GL15.glDeleteBuffers(treeBuffers[place]);
                treeBuffers[place] = 0;
            }
            trees[place] = 0;
            shown[place] = NONE;
        }
        covered = false;
        Log.info("far land: the land beyond the loaded map is given back to the graphics card");
    }
}
