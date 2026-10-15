package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL42;

/**
 * The mod's own pictures for what hangs on a wall: a photograph in its frame, a painting, a
 * poster. The game's sprite of one is some thirty pixels across, painted on the slant its view
 * sees the wall at, and stretched over a frame a pace from the eye it is a smear. Each such
 * sprite the mod knows shows one of the pictures in the jar instead, made with
 * tools/pictures and listed in the jar's index of them.
 *
 * The index is lines of text. A picture is its name, a tab, how wide it is in pixels, a tab
 * and how high: it is /pictures/name.jpg. What shows a picture is a line that starts with =,
 * or with + where only a piece that lies on several squares shows it, then the key, a tab and
 * the picture's name. A key is a sprite's name, or what the game calls the piece and its
 * group with a bar between: "Painting|Lighthouse".
 */
final class Decor {
    static final int NONE = -1;

    private static final String FOLDER = "/pictures/";
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA = 0x1908, GL_RGBA8 = 0x8058, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_LINEAR = 0x2601, GL_LINEAR_MIPMAP_LINEAR = 0x2703, GL_CLAMP_TO_EDGE = 0x812F;
    /**
     * Where down a picture too tall for what it hangs in the part shown has its middle, 0 the
     * top and 1 the foot: a portrait keeps its sitter's head.
     */
    private static final float SHOWN_DOWN = 0.4f;

    /** The index, read by the logic thread the first time a sprite is asked about. */
    private static final class Index {
        final List<String> names = new ArrayList<>();
        final List<float[]> sizes = new ArrayList<>();
        final Map<String, Integer> shows = new HashMap<>(), showsLong = new HashMap<>();
    }

    private static volatile Index index;
    /** The pixels of each picture read and not yet given to the graphics card, and the texture of each that has been. Both under the class's lock. */
    private static Read[] waiting;
    private static int[] textures;
    /**
     * The read of each picture asked for on the pictures' own thread and not yet come, how many
     * times each picture has been given back, and how many times every picture has: a read
     * asked for before its picture was given back is let go when it comes, and asked for again
     * where the picture was wanted meanwhile. All under the class's lock.
     */
    private static Runnable[] reading;
    private static int[] givenBack, readAskedBack, readAskedAll;
    private static boolean[] wantedAgain;
    private static int allGivenBack;
    /** Why a picture could not be read, thrown once on the render thread the next time a picture is drawn. */
    private static final AtomicReference<IllegalStateException> readFailed = new AtomicReference<>();
    /**
     * Pictures to be read, those a frame is drawing first. A picture takes 4 to 50 ms to read,
     * which no frame waits on: what is drawn before its read comes shows nothing.
     */
    private static final LinkedBlockingDeque<Runnable> toRead = new LinkedBlockingDeque<>();

    static {
        Thread reader = new Thread(() -> {
            while (true) {
                try {
                    toRead.take().run();
                } catch (InterruptedException stop) {
                    return;
                }
            }
        }, "CameraUnlock pictures");
        reader.setDaemon(true);
        reader.start();
    }
    /** Whether each picture's texture was made at half size. */
    private static boolean[] madeSmall;
    /** Until when no picture is made after the graphics card had no room for one, or 0 if it always had. Render thread. */
    private static long noRoomUntilNanos;
    private static final long NO_ROOM_RESTS_NANOS = 10_000_000_000L;
    private static final int GL_OUT_OF_MEMORY = 0x0505;

    /** A picture read out of the jar, at the size it was read at: half the file's in a graphics mode that shows smaller pictures. */
    record Read(OffHeap.Block memory, int wide, int high) {}

    /** Where a model's pane is on it, in its own metres: the least and greatest x, then the least and greatest z. */
    private static final IdentityHashMap<Models.Model, float[]> panes = new IdentityHashMap<>();

    private Decor() {}

    private static Index index() {
        Index read = index;
        if (read == null) {
            try {
                read = read(FOLDER + "index.txt");
            } catch (IOException e) {
                throw new UncheckedIOException("the jar's index of pictures could not be read", e);
            }
            synchronized (Decor.class) {
                waiting = new Read[read.names.size()];
                madeSmall = new boolean[read.names.size()];
                textures = new int[read.names.size()];
                reading = new Runnable[read.names.size()];
                givenBack = new int[read.names.size()];
                readAskedBack = new int[read.names.size()];
                readAskedAll = new int[read.names.size()];
                wantedAgain = new boolean[read.names.size()];
            }
            index = read;
        }
        return read;
    }

    private static Index read(String path) throws IOException {
        Index read = new Index();
        Map<String, Integer> numbers = new HashMap<>();
        try (InputStream in = Decor.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("the jar has no " + path);
            }
            BufferedReader lines = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = lines.readLine(); line != null; line = lines.readLine()) {
                String[] parts = line.split("\t");
                if (line.startsWith("=") || line.startsWith("+")) {
                    Integer picture = parts.length == 2 ? numbers.get(parts[1]) : null;
                    if (picture == null) {
                        throw new IOException(path + " has a line that shows no picture listed before it: " + line);
                    }
                    (line.startsWith("+") ? read.showsLong : read.shows).put(parts[0].substring(1), picture);
                } else {
                    if (parts.length != 3) {
                        throw new IOException(path + " has a line that is no picture and shows none: " + line);
                    }
                    numbers.put(parts[0], read.names.size());
                    read.names.add(parts[0]);
                    read.sizes.add(new float[] {Integer.parseInt(parts[1]), Integer.parseInt(parts[2])});
                }
            }
        }
        return read;
    }

    /**
     * The picture a sprite shows, by its name or failing that by what the game calls the piece
     * and its group, or NONE. onSeveral says the piece lies on more squares than one.
     */
    static int of(String sprite, String called, String group, boolean onSeveral) {
        Index read = index();
        Integer picture = read.shows.get(sprite);
        if (picture == null) {
            String key = called + "|" + group;
            picture = read.shows.get(key);
            if (picture == null && onSeveral) {
                picture = read.showsLong.get(key);
            }
        }
        return picture == null ? NONE : picture;
    }

    /** The picture of a name in the index. Thrown when the jar has none of that name. */
    static int named(String name) {
        int picture = index().names.indexOf(name);
        if (picture < 0) {
            throw new IllegalStateException("the jar's index of pictures has no " + name);
        }
        return picture;
    }

    /** How many times as wide as it is high a picture is. */
    static float wideForHigh(int picture) {
        float[] size = index().sizes.get(picture);
        return size[0] / size[1];
    }

    /**
     * What a piece that shows the picture carries where another carries its sprite's texture:
     * a number below zero, which texture turns into the picture's texture. The picture is read
     * from the jar the first time something shows it, on the pictures' own thread, so that the
     * render thread has only to hand it to the graphics card.
     */
    static int held(int picture) {
        synchronized (Decor.class) {
            // One made at the other size for another graphics mode is read again, for the render thread to put in its place.
            boolean otherSize = textures[picture] != 0 && madeSmall[picture] != Quality.smallerPictures();
            if ((textures[picture] == 0 || otherSize) && waiting[picture] == null) {
                read(picture, false);
            }
        }
        return -1 - picture;
    }

    /**
     * Under the class's lock: has the picture read on the pictures' own thread, ahead of every
     * read asked for before where a frame is drawing it now.
     */
    private static void read(int picture, boolean drawing) {
        Runnable asked = reading[picture];
        if (asked != null) {
            if (readAskedBack[picture] != givenBack[picture] || readAskedAll[picture] != allGivenBack) {
                wantedAgain[picture] = true;
            } else if (drawing && toRead.remove(asked)) {
                toRead.offerFirst(asked);
            }
            return;
        }
        readAskedBack[picture] = givenBack[picture];
        readAskedAll[picture] = allGivenBack;
        String path = FOLDER + index.names.get(picture) + ".jpg";
        float[] size = index.sizes.get(picture);
        Runnable read = () -> {
            Read made = null;
            try {
                made = pixels(path, size);
            } catch (Throwable thrown) {
                readFailed.compareAndSet(null, new IllegalStateException(path + " could not be read", thrown));
            } finally {
                synchronized (Decor.class) {
                    reading[picture] = null;
                    boolean stale = readAskedBack[picture] != givenBack[picture] || readAskedAll[picture] != allGivenBack;
                    if (made != null && stale) {
                        made.memory.free();
                    } else if (made != null) {
                        waiting[picture] = made;
                    }
                    if (stale && wantedAgain[picture]) {
                        read(picture, false);
                    }
                }
            }
        };
        reading[picture] = read;
        wantedAgain[picture] = false;
        if (drawing) {
            toRead.offerFirst(read);
        } else {
            toRead.offerLast(read);
        }
    }

    static Read pixels(String path, float[] size) throws IOException {
        BufferedImage image;
        try (InputStream in = Decor.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("the jar has no " + path);
            }
            image = ImageIO.read(in);
        }
        if (image == null) {
            throw new IOException(path + " is not a picture this Java can read");
        }
        int wide = image.getWidth(), high = image.getHeight();
        if (wide != (int) size[0] || high != (int) size[1]) {
            throw new IOException(path + " is " + wide + " by " + high + ", and the index says " + (int) size[0] + " by " + (int) size[1]);
        }
        if (image.getType() != BufferedImage.TYPE_3BYTE_BGR) {
            throw new IOException(path + " is read as image type " + image.getType() + ", where every picture tools/pictures makes is read as TYPE_3BYTE_BGR");
        }
        // Read off the raster a row at a time: getRGB takes each dot through the colour model, and is as slow again as the decoding.
        byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        byte[] row = new byte[wide * 4];
        OffHeap.Block memory = OffHeap.take(wide * high * 4);
        ByteBuffer pixels = memory.bytes;
        for (int y = 0, from = 0; y < high; y++) {
            for (int to = 0; to < row.length; from += 3, to += 4) {
                row[to] = bgr[from + 2];
                row[to + 1] = bgr[from + 1];
                row[to + 2] = bgr[from];
                row[to + 3] = (byte) 0xff;
            }
            pixels.put(row);
        }
        pixels.flip();
        if (!Quality.smallerPictures() || wide < 2 || high < 2) {
            return new Read(memory, wide, high);
        }
        try {
            // An odd last column or row is left out of the half size picture: it is a dot of a frame's edge.
            return new Read(Pictures.halved(pixels, wide, high), wide / 2, high / 2);
        } finally {
            memory.free();
        }
    }

    /**
     * Render thread, once first person has been off a while and every build has been let go:
     * gives every picture back to the graphics card. A build made after this reads its picture
     * again.
     */
    static void letGo() {
        synchronized (Decor.class) {
            if (textures == null) {
                return;
            }
            allGivenBack++;
            for (int picture = 0; picture < textures.length; picture++) {
                if (textures[picture] != 0) {
                    GL11.glDeleteTextures(textures[picture]);
                    textures[picture] = 0;
                }
                if (waiting[picture] != null) {
                    waiting[picture].memory.free();
                    waiting[picture] = null;
                }
            }
        }
        Screens.letGo();
    }

    /** Render thread: gives one picture back to the graphics card. What shows it next reads it again. */
    static void forget(int picture) {
        synchronized (Decor.class) {
            givenBack[picture]++;
            if (textures[picture] != 0) {
                GL11.glDeleteTextures(textures[picture]);
                Pipeline.forgetTexel(textures[picture]);
                textures[picture] = 0;
            }
            if (waiting[picture] != null) {
                waiting[picture].memory.free();
                waiting[picture] = null;
            }
        }
    }

    /** The texture of the picture a piece carries as held left it, made the first time it is drawn after it is read. On the render thread. */
    static int texture(int held) {
        int picture = -1 - held;
        IllegalStateException failed = readFailed.getAndSet(null);
        if (failed != null) {
            throw failed;
        }
        synchronized (Decor.class) {
            if (textures[picture] == 0 && noRoomUntilNanos != 0L && System.nanoTime() < noRoomUntilNanos) {
                return Pipeline.none();
            }
            if (textures[picture] == 0 || waiting[picture] != null) {
                Read read = waiting[picture];
                if (read == null) {
                    // Not read yet, or given back between a build's asking for it and this, its first drawing: it shows nothing till it is read.
                    read(picture, true);
                    return Pipeline.none();
                }
                if (textures[picture] != 0) {
                    GL11.glDeleteTextures(textures[picture]);
                    Pipeline.forgetTexel(textures[picture]);
                    textures[picture] = 0;
                }
                // What the frame raised before this is the frame's, and is not laid to the picture.
                int before = GL11.glGetError();
                if (before != 0) {
                    Pipeline.noted(before);
                    throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(before) + " was raised in the frame before the picture " + index.names.get(picture) + " was made");
                }
                int texture = GL11.glGenTextures();
                GL11.glBindTexture(GL_TEXTURE_2D, texture);
                if (Uploads.stored()) {
                    int levels = 32 - Integer.numberOfLeadingZeros(Math.max(read.wide, read.high));
                    GL42.glTexStorage2D(GL_TEXTURE_2D, levels, GL_RGBA8, read.wide, read.high);
                    GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, read.wide, read.high, GL_RGBA, GL_UNSIGNED_BYTE, read.memory.bytes);
                } else {
                    GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, read.wide, read.high, 0, GL_RGBA, GL_UNSIGNED_BYTE, read.memory.bytes);
                }
                GL30.glGenerateMipmap(GL_TEXTURE_2D);
                int error = GL11.glGetError();
                Pipeline.noted(error);
                if (error != 0) {
                    GL11.glBindTexture(GL_TEXTURE_2D, Pipeline.none());
                    GL11.glDeleteTextures(texture);
                }
                if (error == GL_OUT_OF_MEMORY) {
                    // The card has no room for it. What was read is kept, and no picture is tried again for a while.
                    if (noRoomUntilNanos == 0L) {
                        Log.info("the graphics card has no room for the picture " + index.names.get(picture) + ": pictures it has no room for are left blank and tried again every "
                                + NO_ROOM_RESTS_NANOS / 1_000_000_000L + " seconds");
                    }
                    noRoomUntilNanos = System.nanoTime() + NO_ROOM_RESTS_NANOS;
                    return Pipeline.none();
                }
                read.memory.free();
                waiting[picture] = null;
                if (error != 0) {
                    throw new IllegalStateException("OpenGL error 0x" + Integer.toHexString(error) + " making the texture of the picture " + index.names.get(picture));
                }
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
                Pictures.sharpenAtSlant();
                textures[picture] = texture;
                madeSmall[picture] = read.wide < (int) index.sizes.get(picture)[0];
            }
            return textures[picture];
        }
    }

    /** Where a model's pane is on it: see panes. Thrown for a model that has none. */
    static float[] pane(Models.Model model) {
        float[] pane = panes.get(model);
        if (pane == null) {
            int from = model.first[Models.PANE], to = model.first[Models.PANE + 1];
            if (from == to) {
                throw new IllegalStateException("the model " + model.name + " has no pane to show a picture in");
            }
            pane = new float[] {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int vertex = from; vertex < to; vertex++) {
                float x = model.vertices[vertex * Models.VERTEX_FLOATS], z = model.vertices[vertex * Models.VERTEX_FLOATS + 2];
                pane[0] = Math.min(pane[0], x);
                pane[1] = Math.max(pane[1], x);
                pane[2] = Math.min(pane[2], z);
                pane[3] = Math.max(pane[3], z);
            }
            panes.put(model, pane);
        }
        return pane;
    }

    /**
     * Lays a picture over an upright rectangle of a model, x0 to x1 across it and z0 to z1 up
     * it in the model's metres, x1 on the right of whoever faces it: into out what
     * Models.Instances.picture takes, where on the picture the model's origin is, then what a
     * metre along the model's x adds to that and what a metre up it adds.
     *
     * The picture fills the rectangle and keeps its shape. Of one that is not the shape of the
     * rectangle, which is wide tiles by high as it stands in the world, the middle is shown
     * across and the part about SHOWN_DOWN of the way down.
     *
     * With a rail, the picture stops that many tiles inside the rectangle all round, and what
     * is left round it looks outside the picture: the picture's program paints a frame there.
     * Such a picture is shown whole, edge to edge of what the rail leaves, since a part of it
     * would run on under the rail. It is made the shape it hangs in: see tools/pictures.
     */
    static void lay(float x0, float x1, float z0, float z1, float wide, float high, float rail, float pictureWideForHigh, float[] out) {
        float railX = rail * (x1 - x0) / wide, railZ = rail * (z1 - z0) / high;
        x0 += railX;
        x1 -= railX;
        z0 += railZ;
        z1 -= railZ;
        float wideForHigh = (wide - 2f * rail) / (high - 2f * rail);
        float across = rail > 0f ? 1f : Math.min(wideForHigh / pictureWideForHigh, 1f);
        float down = rail > 0f ? 1f : Math.min(pictureWideForHigh / wideForHigh, 1f);
        float left = (1f - across) / 2f, top = (1f - down) * SHOWN_DOWN;
        float perX = across / (x1 - x0), perZ = down / (z1 - z0);
        out[0] = left - x0 * perX;
        out[1] = top + down + z0 * perZ;
        out[2] = perX;
        out[3] = 0f;
        out[4] = 0f;
        out[5] = -perZ;
    }
}
