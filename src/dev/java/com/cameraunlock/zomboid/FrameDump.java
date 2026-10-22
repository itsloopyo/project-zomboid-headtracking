package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferUShort;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import zombie.GameTime;
import zombie.ZomboidFileSystem;
import zombie.core.Core;
import zombie.core.SpriteRenderer;
import zombie.core.textures.TextureDraw;

/**
 * Pause, in a development build: one frame written out whole, for looking at what the
 * picture is made of. Into the game's cache folder, framedump/<time>: the frame before the
 * game's interface (clean.png) and with it (hud.png), each player's sun shadow maps (near,
 * far, roof, and the torch's when it is lit) as 16 bit pictures stretched over the depths they
 * hold and as raw little endian floats, and state.txt with what the frame was drawn at.
 */
final class FrameDump {
    /** GL_DEPTH_COMPONENT, GL_FLOAT, GL_RGB, GL_UNSIGNED_BYTE, GL_TEXTURE_2D, its binding and GL_TEXTURE_WIDTH. */
    private static final int DEPTH_COMPONENT = 0x1902, FLOAT = 0x1406, RGB = 0x1907, UNSIGNED_BYTE = 0x1401, TEXTURE_2D = 0x0DE1, TEXTURE_BINDING_2D = 0x8069,
            TEXTURE_WIDTH = 0x1000;
    private static final int READ_FRAMEBUFFER = 0x8CA8, READ_FRAMEBUFFER_BINDING = 0x8CAA, PACK_ALIGNMENT = 0x0D05;
    private static final String[] MAPS = {"nearMap", "farMap", "roofMap", "torchMap"};

    private static volatile boolean asked;
    private static volatile int reading;
    private static volatile String told;
    private static Path folder;

    private FrameDump() {}

    /** Logic thread: dump the next frame drawn. */
    static void ask() {
        asked = true;
    }

    /** Whether a dump is asked and its frame not yet read, while the mod's own words are kept off the screen. */
    static boolean pending() {
        return asked || reading > 0;
    }

    /** Logic thread: where the last dump was written, once, for the screen; or null. */
    static String told() {
        String once = told;
        told = null;
        return once;
    }

    /** Logic thread, before the interface is drawn: see FrameRecorder.beforeInterface. */
    static void beforeInterface() {
        if (!asked) {
            return;
        }
        asked = false;
        folder = Path.of(ZomboidFileSystem.instance.getCacheDir(), "framedump", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        StringBuilder state = new StringBuilder();
        state.append(String.format(Locale.ROOT, "screen %dx%d, time of day %.3f%n", Core.getInstance().getScreenWidth(), Core.getInstance().getScreenHeight(),
                GameTime.getInstance().getTimeOfDay()));
        for (int i = 0; i < View.COUNT; i++) {
            if (View.of(i) != null) {
                state.append(String.format(Locale.ROOT, "player %d: graphics %s, shadow maps %d%n", i + 1, Quality.name(i), Quality.shadowSize(i)));
            }
        }
        Log.info("frame dump: asked, into " + folder);
        reading = 2;
        SpriteRenderer.instance.drawGeneric(new Grab(folder, "clean", state.toString()));
    }

    /** Logic thread, after the interface is drawn. */
    static void afterInterface() {
        if (folder == null) {
            return;
        }
        SpriteRenderer.instance.drawGeneric(new Grab(folder, "hud", null));
        folder = null;
    }

    /** Render thread: reads the window, and for the clean grab the shadow maps, then writes them on a thread of its own. */
    private static final class Grab extends TextureDraw.GenericDrawer {
        private final Path folder;
        private final String name;
        private final String state;

        Grab(Path folder, String name, String state) {
            this.folder = folder;
            this.name = name;
            this.state = state;
        }

        @Override
        public void render() {
            int width = Core.getInstance().getScreenWidth(), height = Core.getInstance().getScreenHeight();
            ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 3);
            int previousRead = GL11.glGetInteger(READ_FRAMEBUFFER_BINDING);
            GL30.glBindFramebuffer(READ_FRAMEBUFFER, 0);
            GL11.glPixelStorei(PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, width, height, RGB, UNSIGNED_BYTE, pixels);
            GL30.glBindFramebuffer(READ_FRAMEBUFFER, previousRead);
            List<Depths> maps = new ArrayList<>();
            if (state != null) {
                int previousTexture = GL11.glGetInteger(TEXTURE_BINDING_2D);
                for (int i = 0; i < View.COUNT; i++) {
                    View view = View.of(i);
                    // nearMap is 0 while the pipeline has given its maps back; the torch's map is given back on its own.
                    if (view != null && intField(view.pipeline, "nearMap") != 0) {
                        for (String map : MAPS) {
                            int texture = intField(view.pipeline, map);
                            if (!map.equals("torchMap") || torchLent(view.pipeline)) {
                                maps.add(Depths.read("player" + (i + 1) + "-" + map, texture));
                            }
                        }
                    }
                }
                GL11.glBindTexture(TEXTURE_2D, previousTexture);
            }
            reading--;
            Thread writer = new Thread(() -> write(pixels, width, height, maps), "frame dump " + name);
            writer.setDaemon(true);
            writer.start();
        }

        private void write(ByteBuffer pixels, int width, int height, List<Depths> maps) {
            try {
                Files.createDirectories(folder);
                BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
                byte[] into = ((DataBufferByte) picture.getRaster().getDataBuffer()).getData();
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int from = ((height - 1 - y) * width + x) * 3, to = (y * width + x) * 3;
                        into[to] = pixels.get(from + 2);
                        into[to + 1] = pixels.get(from + 1);
                        into[to + 2] = pixels.get(from);
                    }
                }
                ImageIO.write(picture, "png", folder.resolve(name + ".png").toFile());
                StringBuilder said = new StringBuilder();
                for (Depths map : maps) {
                    said.append(map.write(folder)).append('\n');
                }
                if (state != null) {
                    Files.writeString(folder.resolve("state.txt"), state + said);
                }
                Log.info("frame dump: " + name + " written to " + folder + (maps.isEmpty() ? "" : ", " + maps.size() + " shadow maps"));
                if (state != null) {
                    told = "Frame dumped to " + folder;
                }
            } catch (IOException failure) {
                Log.error("frame dump: " + name + " was not written to " + folder, failure);
            }
        }
    }

    private static boolean torchLent(Pipeline pipeline) {
        try {
            Field field = Pipeline.class.getDeclaredField("torched");
            field.setAccessible(true);
            Object lent = field.get(pipeline);
            Method made = lent.getClass().getDeclaredMethod("made");
            made.setAccessible(true);
            return (boolean) made.invoke(lent);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("frame dump: Pipeline.torched has no made()", failure);
        }
    }

    private static int intField(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(owner);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("frame dump: Pipeline has no int field " + name, failure);
        }
    }

    /** One depth texture read back whole. */
    private record Depths(String name, int size, FloatBuffer depths) {
        /** Render thread. */
        static Depths read(String name, int texture) {
            GL11.glBindTexture(TEXTURE_2D, texture);
            int size = GL11.glGetTexLevelParameteri(TEXTURE_2D, 0, TEXTURE_WIDTH);
            ByteBuffer bytes = ByteBuffer.allocateDirect(size * size * 4).order(ByteOrder.nativeOrder());
            GL11.glPixelStorei(PACK_ALIGNMENT, 4);
            GL11.glGetTexImage(TEXTURE_2D, 0, DEPTH_COMPONENT, FLOAT, bytes);
            return new Depths(name, size, bytes.asFloatBuffer());
        }

        /**
         * The picture stretches the nearest depth to black and the farthest drawn to white; a
         * texel still at the clear depth (1.0, nothing drawn) is white too. Says the range.
         */
        String write(Path folder) throws IOException {
            float least = Float.MAX_VALUE, most = -Float.MAX_VALUE;
            int drawn = 0;
            for (int i = 0; i < size * size; i++) {
                float depth = depths.get(i);
                if (depth < 1f) {
                    least = Math.min(least, depth);
                    most = Math.max(most, depth);
                    drawn++;
                }
            }
            float span = most > least ? most - least : 1f;
            BufferedImage picture = new BufferedImage(size, size, BufferedImage.TYPE_USHORT_GRAY);
            short[] into = ((DataBufferUShort) picture.getRaster().getDataBuffer()).getData();
            ByteBuffer raw = ByteBuffer.allocate(size * size * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float depth = depths.get((size - 1 - y) * size + x);
                    into[y * size + x] = (short) (depth >= 1f ? 65535 : Math.round(Math.max(0f, (depth - least) / span) * 65535f));
                }
            }
            for (int i = 0; i < size * size; i++) {
                raw.putFloat(depths.get(i));
            }
            ImageIO.write(picture, "png", folder.resolve(name + ".png").toFile());
            Files.write(folder.resolve(name + ".f32"), raw.array());
            return String.format(Locale.ROOT, "%s: %dx%d, %d texels drawn, depths %.7f to %.7f (raw .f32 bottom row first)", name, size, size, drawn,
                    drawn == 0 ? 1f : least, drawn == 0 ? 1f : most);
        }
    }
}
