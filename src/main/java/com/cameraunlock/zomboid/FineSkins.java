package com.cameraunlock.zomboid;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.ZomboidFileSystem;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.core.opengl.RenderThread;
import zombie.core.opengl.SmartShader;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.visual.HumanVisual;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureCombiner;
import zombie.core.textures.TextureCombinerCommand;
import zombie.core.textures.TextureCombinerShaderParam;
import zombie.core.textures.TextureFBO;
import zombie.core.textures.TextureID;
import zombie.iso.IsoWorld;

/**
 * Skins finer than the game's, for the people the picture draws near the eye. The game
 * composes what a person wears from pictures on disk: skin, dirt, blood, clothes painted on
 * the body, the holes in them. The mod has pictures of its own for some of those, the bodies
 * of zombies and of the living, and a folder on the player's own machine may hold more of the same pictures at
 * a higher resolution. Where either has one the same composition is run again from those, by
 * the game's own combiner, and the picture's draw wears the result. The game's own textures
 * and its own view are untouched.
 *
 * The game forgets how it composed a texture as soon as it has: its list of steps is emptied,
 * and the textures made on the way are handed back. So the steps are noted as the combiner
 * finishes each composition, and a finer one is only made when the picture first draws the
 * person who wears it.
 */
public final class FineSkins {
    private FineSkins() {}

    /** Where the finer pictures are, and which of them stands for one of the game's. */
    static final class Pack {
        private Pack() {}

        private static final String TEXTURES = "media/textures/";

        /**
         * Under the game's own Zomboid folder (ZomboidFileSystem.getCacheDir), which -cachedir
         * moves. The game loads a file only from under that folder, its install or a mod's
         * folder (ZomboidFileSystem.validatePrefix): for any other path Texture.getSharedTexture
         * answers null.
         */
        static Path folder(String zomboid) {
            return Path.of(zomboid, "CameraUnlock", "skins");
        }

        /**
         * The file in the pack that stands for the game's texture of this name, as a path
         * under the pack's folder, or null for a name that is not a picture under a
         * media/textures folder. The game names a texture read from a file by the file's
         * whole path, with either slash and in whatever case the file system gave.
         */
        static String relative(String name) {
            if (name == null) {
                return null;
            }
            String path = name.replace('\\', '/');
            int from = -1;
            for (int at = path.length() - TEXTURES.length(); at >= 0; at--) {
                if (path.regionMatches(true, at, TEXTURES, 0, TEXTURES.length()) && (at == 0 || path.charAt(at - 1) == '/')) {
                    from = at + TEXTURES.length();
                    break;
                }
            }
            if (from < 0) {
                return null;
            }
            String relative = path.substring(from);
            if (relative.length() <= 4 || !relative.regionMatches(true, relative.length() - 4, ".png", 0, 4)) {
                return null;
            }
            // A name is the game's or a mod's to choose: one that climbs out of the folder
            // it is under names nothing in the pack. Windows resolves a piece that starts
            // with a drive letter and a colon against that drive, not against the folder.
            for (String piece : relative.split("/", -1)) {
                if (piece.isEmpty() || piece.equals(".") || piece.equals("..") || piece.indexOf(':') >= 0) {
                    return null;
                }
            }
            return relative;
        }
    }

    /** The mod's own pictures, in its jar, and which of them stands for one of the game's. */
    static final class Jar {
        private Jar() {}

        /**
         * Where in the jar the mod's picture is that would stand for the game's texture at this
         * path under a media/textures folder. The game spells a name as the file system or a
         * script did, so the jar's are all in small letters.
         */
        static String resource(String relative) {
            return "/skins/" + relative.substring(0, relative.length() - 4).toLowerCase(Locale.ROOT) + ".jpg";
        }

        /** How the game's names of the skins of animals begin, in small letters, under a media/textures folder. */
        private static final String[] ANIMALS = {"body/cow", "body/bull", "body/calf", "body/farmpig", "body/pig", "body/sheep", "body/deer", "body/chick",
            "body/cockerel", "body/turkey", "body/rabbit", "body/raccoon", "body/rat", "body/mouse"};

        /**
         * Which row of [Content] the mod's picture for the game's texture at this path under a
         * media/textures folder answers to, as one of Own's bits: a zombie's body, an animal's,
         * what people wear (Tailor), or the body of the living.
         */
        static int row(String relative) {
            String small = relative.toLowerCase(Locale.ROOT);
            if (small.contains("zedbody")) {
                return Own.ZOMBIES;
            }
            for (String animal : ANIMALS) {
                if (small.startsWith(animal)) {
                    return Own.ANIMALS;
                }
            }
            return Tailor.of(relative) != null ? Own.CLOTHES : Own.PEOPLE;
        }

        /** Where the picture is that says what of the skin at this place in the jar is see-through. */
        static String cut(String resource) {
            return resource.substring(0, resource.length() - 4) + ".cut.png";
        }
    }

    /**
     * What own answers for a picture of the mod's that is not read yet: the player's folder is
     * not asked in its place, since the mod's own comes first and would take over a frame later.
     */
    private static final Read PENDING = new Read();

    /** One of the mod's own pictures: read on a thread of its own, then made a texture of the game's on the render thread. */
    private static final class Read {
        /** The picture as read, until the render thread has made the texture of it. Handed over, and let go, under this Read's lock. */
        OffHeap.Block memory;
        volatile ByteBuffer pixels;
        volatile IOException failure;
        int wide, high;
        Texture texture;
        /** The texture being filled, a strip at a time (makeSome), and how many of its rows are in: worn as texture once all are. */
        TextureID making;
        int rowsMade;
        /** What of AHEAD_HELD_MOST it holds while it is read ahead and not yet filled. */
        long aheadHeld;
        /** How long the render thread spent filling it, and over how many frames: said for the first few. */
        long makingNanos;
        int makingFrames;
        /** Read ahead of being asked for (preload), and not worn since. */
        boolean ahead;
        /** When the picture was last asked for: to compose a skin from, or to be worn as it is. */
        long used;
        /** Set when the picture is no longer wanted before its reader has done: the reader then frees what it read. */
        boolean dropped;
        /** Made by Tailor from the game's own picture, not read out of the jar. */
        boolean tailored;
        /** Set by the reader of a tailored picture that needs no making: the game's is fine enough as it is. */
        volatile boolean fineAsItIs;
        /** Set by the reader of a tailored picture whose file Java could not read as a picture. */
        volatile IOException unreadable;
        /** What the picture is counted as holding of the card, from when it is asked for until it is let go. */
        long bytes;

        /** Takes the picture a reader made, or frees it where this was let go of while it was being made. Under this Read's lock. */
        synchronized void hand(OffHeap.Block made, int madeWide, int madeHigh) {
            if (dropped) {
                made.free();
                return;
            }
            wide = madeWide;
            high = madeHigh;
            memory = made;
            pixels = made.bytes;
        }
    }

    /** One step of a composition, as the combiner was given it. */
    private static final class Step {
        final int x, y, w, h;
        final int blendSrc, blendDest, blendSrcA, blendDestA;
        final SmartShader shader;
        final ArrayList<TextureCombinerShaderParam> shaderParams;
        /** A picture this step reads, or how it was composed when it was itself a composition. */
        final Texture tex, mask;
        final Recipe texFrom, maskFrom;
        /** Set when the combiner is handed back a picture this step reads as it is: it now holds some other picture or none. */
        boolean texBack, maskBack;

        Step(TextureCombinerCommand command) {
            x = command.x;
            y = command.y;
            w = command.w;
            h = command.h;
            blendSrc = command.blendSrc;
            blendDest = command.blendDest;
            blendSrcA = command.blendSrcA;
            blendDestA = command.blendDestA;
            shader = command.shader;
            // The game is free to refill a list it has handed over.
            shaderParams = command.shaderParams == null ? null : new ArrayList<>(command.shaderParams);
            tex = command.tex;
            mask = command.mask;
            texFrom = tex == null ? null : recipes.get(tex);
            maskFrom = mask == null ? null : recipes.get(mask);
        }
    }

    /** How the game composed one texture. */
    private static final class Recipe {
        final Step[] steps;
        /** Set once it is known that composing this again would give nothing finer. */
        boolean plain;

        Recipe(ArrayList<TextureCombinerCommand> commands) {
            steps = new Step[commands.size()];
            for (int step = 0; step < steps.length; step++) {
                steps[step] = new Step(commands.get(step));
                readFrom(steps[step]);
            }
        }
    }

    /**
     * For each picture a step reads as it is (not one composed by a recipe noted here), the
     * steps that read it. The game hands a picture back to its combiner a picture at a time,
     * dozens in the frame a crowd comes into view, and each was looked for through every
     * step of every recipe: half of a 240 ms frame. Steps of recipes no longer noted stay
     * listed until the list is made again from those that are, once it has grown to twice
     * what it held then (rereadFrom).
     */
    private static final IdentityHashMap<Texture, ArrayList<Step>> readers = new IdentityHashMap<>();
    private static int readersHeld, readersAtLastReading;

    private static void readFrom(Step step) {
        if (step.texFrom == null && step.tex != null) {
            readers.computeIfAbsent(step.tex, texture -> new ArrayList<>()).add(step);
            readersHeld++;
        }
        if (step.maskFrom == null && step.mask != null) {
            readers.computeIfAbsent(step.mask, texture -> new ArrayList<>()).add(step);
            readersHeld++;
        }
    }

    /** Lists again the steps of every recipe noted, and of the recipes they are composed from. */
    private static void rereadFrom() {
        readers.clear();
        readersHeld = 0;
        IdentityHashMap<Recipe, Boolean> listed = new IdentityHashMap<>();
        for (Recipe recipe : recipes.values()) {
            readFrom(recipe, listed);
        }
        readersAtLastReading = readersHeld;
    }

    private static void readFrom(Recipe recipe, IdentityHashMap<Recipe, Boolean> listed) {
        if (listed.put(recipe, Boolean.TRUE) != null) {
            return;
        }
        for (Step step : recipe.steps) {
            readFrom(step);
            if (step.texFrom != null) {
                readFrom(step.texFrom, listed);
            }
            if (step.maskFrom != null) {
                readFrom(step.maskFrom, listed);
            }
        }
    }

    /** A finer texture, how much of the graphics card it takes, and when it was last drawn. */
    private static final class Fine {
        final Texture texture;
        final long bytes;
        long used;

        Fine(Texture texture, long used) {
            this.texture = texture;
            this.bytes = 4L * texture.getWidth() * texture.getHeight();
            this.used = used;
        }
    }

    /**
     * One of the mod's own pictures is kept for the session, and given back only when another
     * is wanted that its cap has no room for, and then only once nothing has composed a skin
     * from it or worn it as it is for this long.
     */
    private static final long OWN_KEPT_NANOS = 5_000_000_000L, OWN_LOOKED_EVERY_NANOS = 1_000_000_000L;
    private static long ownLookedAt;
    /** Set when a picture of the jar's, or one of Tailor's, was wanted and its cap had no room: tendOwn then makes some. */
    private static boolean jarRoomWanted, tailoredRoomWanted;
    /**
     * The skins the characters round the player wear, as paths under media/textures, the most
     * worn first: worked out by the logic thread as the save's world loads (rank), and read
     * ahead by the render thread once the skins are worn (preload). Null once that is done.
     */
    private static volatile List<String> ranked;
    private static volatile boolean rankedThisSave;
    /** Every body's skin is composed with these over it. */
    private static final List<String> OVERLAYS = List.of("BloodTextures/BloodOverlay.png", "BloodTextures/GrimeOverlay.png");
    /** The places in the jar of the pictures read ahead whose textures are still to be filled, and of those ranked to be read ahead and not yet asked for. */
    private static final ArrayList<String> ahead = new ArrayList<>(), aheadQueue = new ArrayList<>();
    /**
     * What the pictures read ahead and not yet filled hold outside the Java heap: no more are
     * asked for than come to AHEAD_HELD_MOST, and one left unfilled for AHEAD_KEPT_NANOS in play
     * is let go (tendOwn).
     */
    private static long aheadHeld;
    private static final long AHEAD_HELD_MOST = 64L << 20, AHEAD_KEPT_NANOS = 10_000_000_000L;
    /** The last frame a picture was asked for that was not yet filled: the frame after one is left to what was asked for. */
    private static int askedUnfilledFrame = -1;
    private static int aheadRead, aheadWorn, aheadThisSave;
    private static long aheadBegan;
    private static boolean aheadSaid;
    /**
     * Whether the loading screen waits for the skins read ahead (holdsLoading): until they are
     * all on the card, they can come no further, or the loading screen's wait is over. Once it
     * has stopped waiting it is released for the rest of the save's loading.
     */
    private static volatile boolean holding;
    private static boolean released;
    private static int stuckFrames;
    private static final long LOADING_NANOS = 12_000_000L;
    /** One of the mod's own pictures is filled a frame, for no more than MAKING_NANOS once its first strip is in: see makeSome. */
    private static int ownMadeFrame = -1;
    private static final long MAKING_NANOS = 3_000_000L;
    /** A strip is this many dots. */
    private static final int STRIP_DOTS = 1 << 16;
    /** TextureFlags.FBO: a TextureID made as the game's combiner makes its own, filtered GL_LINEAR, repeated, with no smaller copies and no copy of a picture. */
    private static final int FBO = 16;
    private static final int GL_RGBA8 = 0x8058, GL_RGBA = 0x1908, GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_REPEAT = 0x2901;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC, GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_OUT_OF_MEMORY = 0x0505;
    /** Whether the mod's own pictures now kept were read at half size: see Quality.smallerPictures. */
    private static boolean ownSmall;
    /** A finer skin drawn this recently is not given up to make room for another. */
    private static final long KEPT_NANOS = 2_000_000_000L;
    /** One composition a frame: a crowd coming into view gets its skins over a second, not in one long frame. */
    private static int composedFrame = -1;
    /**
     * The skin being composed, a combine a frame: one of 2048 square took 10 to 30 ms whole.
     * The recipes it is composed from are composed first, each on a frame of its own, and kept
     * in parts until the skin is. Others wait for it while it is asked for on any of the last
     * PARTS_KEPT_FRAMES frames; one no longer asked for is given up.
     */
    private static Recipe making;
    private static int makingAsked;
    private static final IdentityHashMap<Recipe, Texture> parts = new IdentityHashMap<>();
    private static final int PARTS_KEPT_FRAMES = 30;

    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF, GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    private static final int GL_ATTRIB_STACK_DEPTH = 0x0BB0, GL_CLIENT_ATTRIB_STACK_DEPTH = 0x0BB1;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D, GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_BINDING_2D = 0x8069;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;

    private static boolean started, off;
    private static Path folder;
    /** How the game composed each texture it has not yet handed back. */
    private static final IdentityHashMap<Texture, Recipe> recipes = new IdentityHashMap<>();
    private static final int READERS_LEAST = 4096;
    /** The finer texture for each of the game's, the one drawn longest ago first. The game's textures are equal only to themselves. */
    private static final LinkedHashMap<Texture, Fine> fine = new LinkedHashMap<>(64, 0.75f, true);
    private static long fineBytes;
    /** Which of the mod's own skins were worn when the finer textures now kept were composed: the [Content] rows for zombies, for the living, for what they wear and for animals, as Own's bits. */
    private static final int WORN = Own.ZOMBIES | Own.PEOPLE | Own.CLOTHES | Own.ANIMALS;
    private static int ownWorn = WORN;
    /** The mod's own picture at each place in the jar that has been asked for, null where the jar has none. */
    private static final HashMap<String, Read> own = new HashMap<>();
    /** A 2048 by 2048 picture takes a tenth of a second to read: not on the thread that draws. */
    private static final ExecutorService reader = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "CameraUnlock skins");
        thread.setDaemon(true);
        return thread;
    });
    /** Tailor's work has a thread of its own: a crowd wears hundreds of pictures, and the bodies' skins do not wait behind them. */
    private static final ExecutorService tailor = Executors.newSingleThreadExecutor(work -> {
        Thread thread = new Thread(work, "CameraUnlock tailor");
        thread.setDaemon(true);
        return thread;
    });
    /** The paths the jar has no picture at, and each texture name's key in own for what Tailor makes of it: asked of every part of every frame. */
    private static final HashSet<String> jarless = new HashSet<>();
    private static final HashMap<String, String> tailoredKeys = new HashMap<>();
    /** How much of the card the pictures Tailor made or is making hold: no more than VideoMemory.tailoredSkinBytes. */
    private static long tailoredBytes;
    /**
     * The same for the jar's own pictures, each counted as the largest the jar holds until it
     * is read: a crowd's bodies are each a 2048 by 2048 picture, and with every one of the
     * jar's read at once they would hold nearly two gigabytes. No more than VideoMemory.jarSkinBytes.
     */
    private static long jarBytes;
    private static final int JAR_LARGEST = 2048;
    /**
     * The jar's pictures one skin, or one texture worn as it is, still wants read: all of them
     * are read or none, so that no skin holds room on the card for a part of what it needs
     * while the rest waits for room that skins like it are holding.
     */
    private static final ArrayList<String> wanted = new ArrayList<>();
    /** Who is let room among the jar's pictures first, by the recipe or texture that asked. */
    private static final RoomLine line = new RoomLine(1_000_000_000L, 10_000_000_000L);
    /** How far from the eye whoever asks now stands, in tiles. */
    private static float asking;
    /** The mod's own pictures the texture being answered for was found to read: kept from being let go only once they are worn. */
    private static final ArrayList<Read> asked = new ArrayList<>();

    /** The pack's picture for each name the game has shown, null for a name the pack has nothing for. */
    private static final HashMap<String, Texture> packed = new HashMap<>();
    private static boolean composing;
    /** What the last look through a recipe found: a picture still loading, or one the combiner has taken back. */
    private static boolean waiting, gone;
    private static boolean goneSaid;

    private static final int SIZE_HELD = 128;
    private static int said, tailorSaid, unreadableSaid;
    /** How many of the jar's pictures in each of its folders have been said to be worn: the first three of each are. */
    private static final HashMap<String, Integer> ownSaid = new HashMap<>();

    /**
     * What the picture's draw should wear in place of the texture the game gives a part: a
     * finer one where the pack has what it takes, or the game's own. Composing a finer one
     * runs the game's combiner, which binds its own targets, programs and buffers and ends by
     * putting back the state the game remembers, not the picture's: all of it is put back
     * here, and the caller sets its own state after this, not before. Render thread only.
     *
     * @param away how far from the viewer's eye the wearer stands, in tiles (Characters.fromViewer), and nought for the viewer: see RoomLine
     */
    static synchronized Texture of(Texture bound, float away) {
        if (wornFrame != Pipeline.frame()) {
            wornFrame = Pipeline.frame();
            worn.clear();
        }
        Texture answer = worn.get(bound);
        if (answer != null) {
            return answer;
        }
        asking = away;
        try {
            answer = finer(bound);
        } catch (Exception | LinkageError failure) {
            refuse("a finer skin could not be made", failure);
            return bound;
        }
        worn.put(bound, answer);
        return answer;
    }

    /**
     * What of answered for each texture this frame: a crowd's parts ask thousands of times a
     * frame for a few dozen textures. Emptied whenever a skin or picture it may hold is let go.
     */
    private static final IdentityHashMap<Texture, Texture> worn = new IdentityHashMap<>();
    private static int wornFrame = -1;

    /** Called by the game's combiner as it hands back what it composed from these steps. */
    public static Texture combined(Texture result, ArrayList<TextureCombinerCommand> commands) {
        try {
            note(result, commands);
        } catch (RuntimeException | LinkageError failure) {
            refuse("the game's composition of a texture could not be noted", failure);
        }
        return result;
    }

    /** Called by the game's combiner as it takes back a texture it composed. */
    public static void released(Texture texture) {
        try {
            forget(texture);
        } catch (RuntimeException | LinkageError failure) {
            refuse("a texture the game's combiner took back could not be let go", failure);
        }
    }

    private static boolean on() {
        if (!started) {
            started = true;
            String gap = Agent.fineSkinsGap();
            if (gap != null) {
                off = true;
                Log.info("fine skins are off, and characters wear the game's own: " + gap);
                return false;
            }
            Path at = Pack.folder(ZomboidFileSystem.instance.getCacheDir());
            if (Files.isDirectory(at)) {
                folder = at;
                Log.info("fine skins are the mod's own, and whatever more is at " + at);
            } else {
                Log.info("fine skins are the mod's own: there are no more at " + at);
            }
        }
        return !off;
    }

    private static synchronized void refuse(String why, Throwable cause) {
        if (!off) {
            Log.error("fine skins are off for this session, and characters wear the game's own: " + why, cause);
        }
        off = true;
        worn.clear();
        for (Texture each : parts.values()) {
            each.destroy();
        }
        parts.clear();
        making = null;
        // Destroyed, not handed to the combiner, which would keep each for a next skin of its size: with the skins off there is none.
        for (Fine each : fine.values()) {
            each.texture.destroy();
        }
        recipes.clear();
        readers.clear();
        readersHeld = readersAtLastReading = 0;
        fine.clear();
        fineBytes = 0;
        packed.clear();
        for (Read read : own.values()) {
            if (read != null) {
                letGoOf(read);
            }
        }
        own.clear();
        ahead.clear();
        aheadQueue.clear();
        aheadHeld = 0;
    }

    /** The driver had no room on the graphics card for a texture of the mod's own skins. */
    private static boolean noRoomSaid;

    private static final class NoRoom extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        NoRoom(String message) {
            super(message);
        }
    }

    /**
     * Gives back every finer skin and every one of the mod's own pictures, and has each skin
     * looked at afresh the next time it is worn. Render thread, under the class's lock.
     */
    private static void letGoOfAll() {
        worn.clear();
        spendParts();
        for (Spent each : spent) {
            each.texture().destroy();
        }
        spent.clear();
        spentBytes = 0;
        // Destroyed, not handed back to the combiner: it keeps what it is handed for the next skin of that size,
        // and after a change of size or with the view off there is none.
        for (Fine each : fine.values()) {
            each.texture.destroy();
        }
        fine.clear();
        fineBytes = 0;
        for (Recipe each : recipes.values()) {
            each.plain = false;
        }
        for (Read read : own.values()) {
            if (read != null) {
                letGoOf(read);
            }
        }
        own.clear();
        ahead.clear();
        aheadQueue.clear();
        aheadHeld = 0;
        ownSmall = Quality.smallerPictures();
    }

    /** Gives back one of the mod's own pictures: its texture, or what was read of it, or what its reader is still reading when that is done. */
    private static void letGoOf(Read read) {
        worn.clear();
        synchronized (read) {
            read.dropped = true;
            if (read.memory != null) {
                read.memory.free();
                read.memory = null;
                read.pixels = null;
            }
        }
        if (read.tailored) {
            tailoredBytes -= read.bytes;
        } else {
            jarBytes -= read.bytes;
        }
        read.bytes = 0;
        aheadHeld -= read.aheadHeld;
        read.aheadHeld = 0;
        if (read.texture != null) {
            read.texture.destroy();
            read.texture = null;
        } else if (read.making != null) {
            read.making.destroy();
        }
        read.making = null;
    }

    /** What a picture of this size holds of the card: four bytes a dot of its texture, padded to a power of two each way (Pictures.padded), with no smaller copies. */
    private static long bytesOf(int wide, int high) {
        return 4L * Pictures.encompassing(wide) * Pictures.encompassing(high);
    }

    /**
     * Once a second, gives back each of the mod's own pictures that nothing has asked for in
     * OWN_KEPT_NANOS, read and waiting or made a texture. Render thread, under the class's lock.
     */
    private static void tendOwn() {
        long looked = System.nanoTime();
        if (looked - ownLookedAt < OWN_LOOKED_EVERY_NANOS) {
            return;
        }
        ownLookedAt = looked;
        if (jarRoomWanted || tailoredRoomWanted) {
            for (Iterator<Read> each = own.values().iterator(); each.hasNext();) {
                Read read = each.next();
                if (read != null && (read.tailored ? tailoredRoomWanted : jarRoomWanted) && looked - read.used > OWN_KEPT_NANOS) {
                    letGoOf(read);
                    each.remove();
                }
            }
        }
        jarRoomWanted = false;
        tailoredRoomWanted = false;
        for (Iterator<String> each = ahead.iterator(); each.hasNext();) {
            String path = each.next();
            Read read = own.get(path);
            if (read != null && read.texture == null && read.pixels != null && looked - read.used > AHEAD_KEPT_NANOS) {
                letGoOf(read);
                own.remove(path);
                each.remove();
                aheadThisSave--;
            }
        }
    }

    /** Called for each picture drawn, before it is, whether or not anyone in it wears a finer skin. Render thread. */
    static synchronized void tend() {
        if (spentFrame != Pipeline.frame()) {
            spentFrame = Pipeline.frame();
            spentGiven = 0;
        }
        for (Spent first = spent.peek(); first != null && Pipeline.frame() - first.frame() >= SPENT_FRAMES
                && (spentGiven < SPENT_A_FRAME || spentBytes > SPENT_MOST_BYTES); first = spent.peek()) {
            spent.poll();
            spentBytes -= first.bytes();
            first.texture().destroy();
            spentGiven++;
        }
        if (ranked != null && on()) {
            try {
                preload();
            } catch (RuntimeException | LinkageError failure) {
                refuse("the mod's own skins could not be read ahead", failure);
            }
        }
        if (started && !off) {
            tendOwn();
            try {
                fillAhead();
            } catch (RuntimeException | LinkageError failure) {
                refuse("a skin of the mod's own read ahead could not be made", failure);
            }
        }
    }

    /**
     * Logic thread, on the loading screen's frames once the save's world is loaded
     * (Hooks.worldLoaded), once a save: which skins the zombies in the cell and the players
     * wear, the most worn first after the overlays every body is composed with. A zombie whose
     * rot the game has not yet picked is left out: asking its skin would have it picked now.
     */
    static void rank() {
        if (rankedThisSave) {
            return;
        }
        rankedThisSave = true;
        HashMap<String, Integer> worn = new HashMap<>();
        for (IsoZombie zombie : IsoWorld.instance.currentCell.getZombieList()) {
            HumanVisual visual = zombie.getHumanVisual();
            if (visual != null && visual.zombieRotStage >= 0) {
                count(visual, worn);
            }
        }
        for (IsoPlayer player : IsoPlayer.players) {
            if (player != null && player.getHumanVisual() != null) {
                count(player.getHumanVisual(), worn);
            }
        }
        LinkedHashSet<String> order = new LinkedHashSet<>(OVERLAYS);
        worn.entrySet().stream().sorted((one, other) -> other.getValue() - one.getValue()).forEach(each -> order.add(each.getKey()));
        // Then every zombie's body, those as far rotted as the zombies seen first, the most seen first: more of them will come.
        LinkedHashSet<String> rots = new LinkedHashSet<>();
        for (String each : order) {
            // A mod's skin need not be named as the game's are, ZedBody0, a body's digit, then its rot: one ending at ZedBody0 has none.
            int at = each.indexOf("ZedBody0"), from = at + "ZedBody0".length() + 1, to = each.length() - ".png".length();
            if (at >= 0 && from <= to) {
                rots.add(each.substring(from, to));
            }
        }
        rots.addAll(ROTS);
        for (String rot : rots) {
            for (int body = 1; body <= ZOMBIE_BODIES; body++) {
                order.add("Body/M_ZedBody0" + body + rot + ".png");
                order.add("Body/F_ZedBody0" + body + rot + ".png");
            }
        }
        synchronized (FineSkins.class) {
            ranked = new ArrayList<>(order);
        }
    }

    /** The zombies' bodies: four of each sex, each at three stages of rot, as the game names them (PopTemplateManager). */
    private static final int ZOMBIE_BODIES = 4;
    private static final List<String> ROTS = List.of("_level1", "_level2", "_level3");

    private static void count(HumanVisual visual, HashMap<String, Integer> worn) {
        String skin = visual.getSkinTexture();
        if (skin != null && !skin.isEmpty()) {
            worn.merge("Body/" + skin + ".png", 1, Integer::sum);
        }
    }

    /**
     * As the game leaves a save: the next save's skins are ranked afresh, and the render thread
     * gives back the skins and pictures this one wore. Logic thread.
     */
    static synchronized void leftSave() {
        ranked = null;
        rankedThisSave = false;
        holding = false;
        released = false;
        stuckFrames = 0;
        ahead.clear();
        aheadQueue.clear();
        aheadThisSave = 0;
        RenderThread.queueInvokeOnRenderContext(LEFT_SAVE);
    }

    private static final Runnable LEFT_SAVE = () -> {
        synchronized (FineSkins.class) {
            if (!started) {
                return;
            }
            int pictures = 0;
            for (Read read : own.values()) {
                pictures += read == null ? 0 : 1;
            }
            int skins = fine.size();
            letGoOfAll();
            Log.info("the save was left: the mod's own skins gave back " + pictures + " pictures and " + skins + " skins composed from them");
        }
    };

    /**
     * Has the jar's pictures for the skins ranked read, in that order, as many as VideoMemory.jarSkinBytes
     * has room for at the largest they could be, once the graphics mode wears the mod's own
     * skins and the bodies' see-through is begun. Render thread, under the class's lock.
     */
    private static void preload() {
        if ((Own.anywhere() & WORN) == 0) {
            return;
        }
        if (!SkinCuts.begin()) {
            if (SkinCuts.bodiesLoaded()) {
                ranked = null;
                Log.info("no skins are read ahead: the shapes of the game's body models, which the skins are cut for, could not be read");
            }
            return;
        }
        List<String> order = ranked;
        ranked = null;
        wornAsNow();
        long most = jarMost(), planned = jarBytes, cap = VideoMemory.jarSkinBytes();
        for (String relative : order) {
            if ((ownWorn & rows.computeIfAbsent(relative, Jar::row)) == 0) {
                continue;
            }
            String path = resources.computeIfAbsent(relative, Jar::resource);
            if (jarless.contains(path) || own.containsKey(path)) {
                continue;
            }
            if (FineSkins.class.getResource(path) == null) {
                jarless.add(path);
                continue;
            }
            if (planned + most > cap) {
                break;
            }
            planned += most;
            aheadQueue.add(path);
        }
        aheadRead += aheadQueue.size();
        aheadThisSave = aheadQueue.size();
        aheadBegan = System.nanoTime();
        aheadSaid = false;
        Log.info("the mod's own skins read ahead: " + aheadThisSave + " of the " + order.size() + " the characters about the player are composed from, "
                + ((planned - jarBytes) >> 20) + " MB of the graphics card");
        readAhead();
    }

    /**
     * Has the next of the pictures queued to be read ahead read, while those read and not yet
     * filled hold no more than AHEAD_HELD_MOST. Should play have taken the room under the cap,
     * the rest of the queue is given up. Render thread, under the class's lock.
     */
    private static void readAhead() {
        long most = jarMost(), now = System.nanoTime(), cap = VideoMemory.jarSkinBytes();
        while (!aheadQueue.isEmpty() && aheadHeld + most <= AHEAD_HELD_MOST) {
            String path = aheadQueue.remove(0);
            if (own.containsKey(path)) {
                continue;
            }
            if (jarBytes + most > cap) {
                aheadThisSave -= aheadQueue.size() + 1;
                aheadQueue.clear();
                break;
            }
            Read reading = new Read();
            reading.bytes = most;
            reading.used = now;
            reading.ahead = true;
            reading.aheadHeld = most;
            jarBytes += most;
            aheadHeld += most;
            own.put(path, reading);
            ahead.add(path);
            reader.execute(() -> read(path, reading));
        }
    }

    /** Says, once a save, when every picture read ahead is on the card. */
    private static void aheadDone() {
        if (!aheadSaid && aheadThisSave > 0 && ahead.isEmpty() && aheadQueue.isEmpty()) {
            aheadSaid = true;
            Log.info(String.format(Locale.ROOT, "the mod's own skins read ahead are on the graphics card: %d in %.1f s", aheadThisSave, (System.nanoTime() - aheadBegan) / 1e9));
        }
    }

    /**
     * Whether the save's start waits for the skins read ahead (Hooks.worldLoaded), asked on each
     * of the loading screen's frames once the game has read the map round the player: while they
     * can still come and are not all on the card, until giveUp at most. Once it has said no it
     * says no until the next save. Logic thread.
     *
     * @param contentComing whether the mod's own content is still being made for some view, the skins being read ahead only once it is
     */
    static synchronized boolean holdsLoading(boolean contentComing, long giveUp) {
        if (released || !rankedThisSave) {
            return holding = false;
        }
        String stop = notHeld(contentComing);
        boolean left = ranked != null || !ahead.isEmpty() || !aheadQueue.isEmpty();
        if (stop == null && left && System.nanoTime() > giveUp) {
            stop = "they are not all on the graphics card after as long as the loading screen waits, and the rest are filled as the save plays";
        }
        if (stop == null && left) {
            return holding = true;
        }
        released = true;
        if (stop != null && !stop.isEmpty()) {
            Log.info("the save's start no longer waits for the mod's own skins: " + stop);
        }
        return holding = false;
    }

    /**
     * Why the save's start does not wait for the skins read ahead: "" where they are not worn,
     * a reason to log where they cannot come, and null while they can. Content that is no
     * longer being made and still not worn is taken as never coming only on a second frame
     * running: the render thread marks it made a moment after it marks it no longer making.
     */
    private static String notHeld(boolean contentComing) {
        if (off || (Own.rows() & WORN) == 0 || Quality.plainEverywhere() || Pipeline.contentRefused()) {
            return "";
        }
        String stuck = null;
        if (ranked != null && !contentComing && (Own.anywhere() & WORN) == 0) {
            stuck = "the mod's own content is not being made, and the skins are read ahead only once it is";
        } else if (ranked != null && (Own.anywhere() & WORN) != 0 && !SkinCuts.bodiesLoaded()) {
            stuck = "the game has not loaded its bodies' models, which the skins are cut for";
        }
        stuckFrames = stuck == null ? 0 : stuckFrames + 1;
        return stuckFrames >= 2 ? stuck : null;
    }

    /** "Preparing skins 12 / 40" while the save waits for them, or null. Logic thread. */
    static synchronized String progress() {
        if (!holding) {
            return null;
        }
        return ranked != null ? "Preparing skins" : "Preparing skins " + (aheadThisSave - ahead.size() - aheadQueue.size()) + " / " + aheadThisSave;
    }

    /**
     * Render thread, queued on each of the loading screen's frames (FirstPerson.whileLoading):
     * begins the reading ahead once it can, and fills what is read for LOADING_NANOS, there
     * being no play to keep smooth.
     */
    static synchronized void whileLoading() {
        try {
            if (ranked != null && on()) {
                preload();
            }
            if (!started || off) {
                return;
            }
            long from = System.nanoTime();
            for (Iterator<String> each = ahead.iterator(); each.hasNext();) {
                long left = LOADING_NANOS - (System.nanoTime() - from);
                if (left <= 0) {
                    break;
                }
                String path = each.next();
                Read read = own.get(path);
                if (read == null || read.texture != null) {
                    each.remove();
                } else if (read.failure != null) {
                    made(read, path, path);
                } else if (read.pixels != null) {
                    fill(read, path, path, left);
                    if (read.texture != null) {
                        each.remove();
                    }
                }
            }
            readAhead();
            aheadDone();
        } catch (RuntimeException | LinkageError failure) {
            refuse("the mod's own skins could not be read ahead", failure);
        }
    }

    /**
     * Fills the texture of a picture read ahead, on a frame after one in which nothing asked for
     * was waiting to be filled. Render thread, under the class's lock.
     */
    private static void fillAhead() {
        if (ahead.isEmpty() && aheadQueue.isEmpty() || ownMadeFrame == Pipeline.frame() || askedUnfilledFrame >= Pipeline.frame() - 1) {
            return;
        }
        for (Iterator<String> each = ahead.iterator(); each.hasNext();) {
            String path = each.next();
            Read read = own.get(path);
            if (read == null || read.texture != null) {
                each.remove();
                continue;
            }
            if (read.pixels != null || read.failure != null) {
                made(read, path, path);
                if (read.texture != null) {
                    each.remove();
                }
                break;
            }
        }
        readAhead();
        aheadDone();
    }

    /** Lets go of everything made with the graphics mode or the mod's own skins as they were, should either have changed. */
    private static void wornAsNow() {
        if ((Own.anywhere() & WORN) != ownWorn || ownSmall != Quality.smallerPictures()) {
            // The player has switched some of the mod's own skins on or off, or gone to a graphics mode that reads them at
            // another size: what was composed with things as they were is composed again.
            ownWorn = Own.anywhere() & WORN;
            letGoOfAll();
        }
    }

    /**
     * A texture a composition drew from, and the frame it did. Destroyed at once, the driver
     * waited for the card to finish the composition's draws before it let it go: 20 to 45 ms
     * a skin. Some frames on, the card has long finished with it.
     */
    private record Spent(Texture texture, int frame, long bytes) {}

    private static final ArrayDeque<Spent> spent = new ArrayDeque<>();
    private static final int SPENT_FRAMES = 4;
    /**
     * With a crowd's skins composed, letting go of all a composition drew from at once held
     * frames for 10 to 20 ms in glDeleteTextures, and three a frame for 10 to 16 ms: no more
     * than this many are let go a frame, unless those waiting hold more than SPENT_MOST_BYTES.
     */
    private static final int SPENT_A_FRAME = 1;
    private static final long SPENT_MOST_BYTES = 256L << 20;
    private static long spentBytes;
    private static int spentFrame = -1, spentGiven;

    /**
     * Render thread, once first person has been off a while for every view that drew: the skins
     * are worn in every view's picture, and with every view switched off there is none. See
     * Characters.letGo.
     */
    static synchronized void letGo() {
        if (started) {
            letGoOfAll();
        }
    }

    private static synchronized void note(Texture result, ArrayList<TextureCombinerCommand> commands) {
        if (composing || !on()) {
            return;
        }
        recipes.put(result, new Recipe(commands));
        if (readersHeld > 2 * readersAtLastReading + READERS_LEAST) {
            rereadFrom();
        }
        // Should the game compose anew into a texture it never handed back, what was made
        // after the old composition is no longer this texture's.
        destroy(fine.remove(result));
    }

    /**
     * Destroyed, not handed back to the combiner, which keeps what it is handed for the next
     * skin of that size and counts none of it against VideoMemory.skinBytes: when a crowd has gone there
     * is no next skin.
     */
    private static void destroy(Fine made) {
        if (made != null) {
            worn.clear();
            fineBytes -= made.bytes;
            made.texture.destroy();
        }
    }

    private static synchronized void forget(Texture texture) {
        if (!on()) {
            return;
        }
        ArrayList<Step> reading = readers.remove(texture);
        if (reading != null) {
            readersHeld -= reading.size();
            for (Step step : reading) {
                step.texBack |= step.tex == texture;
                step.maskBack |= step.mask == texture;
            }
        }
        recipes.remove(texture);
        destroy(fine.remove(texture));
    }

    private static synchronized Texture finer(Texture bound) throws Exception {
        if (!on()) {
            return bound;
        }
        waiting = false;
        gone = false;
        wanted.clear();
        asked.clear();
        wornAsNow();
        if (!(bound instanceof SmartTexture smart)) {
            Texture pack = packed(bound);
            settle(bound);
            if (pack != null) {
                wornNow();
            }
            return pack != null ? pack : bound;
        }
        Texture result = smart.result;
        Recipe recipe = result == null ? null : recipes.get(result);
        if (recipe == null) {
            return bound;
        }
        long now = System.nanoTime();
        Fine have = fine.get(result);
        if (have != null) {
            have.used = now;
            return have.texture;
        }
        if (recipe.plain) {
            return bound;
        }
        // Whether a finer one could be composed now is asked before what it would be composed
        // of: that is read out of the names of every texture the recipe uses, and with a crowd
        // on the screen and no room for more skins it was read for every part of every one of
        // them on every frame, which held a frame for a fifteenth of a second. It is asked before
        // any of the jar's pictures are read for it, too: read for a skin that then finds no room,
        // they would be let go unused and read again.
        if (making != null && making != recipe) {
            if (Pipeline.frame() - makingAsked <= PARTS_KEPT_FRAMES) {
                return bound;
            }
            spendParts();
        }
        long skinCap = VideoMemory.skinBytes();
        if (composedFrame == Pipeline.frame() || !roomForASkin(now, skinCap)) {
            return bound;
        }
        boolean any = anyPacked(recipe);
        if (!settle(recipe)) {
            recipe.plain = true;
            Log.info("the finer skin for " + result.getName() + " would need more of the mod's own pictures than they may hold on the card: it stays the game's own");
            return bound;
        }
        if (gone) {
            recipe.plain = true;
            if (!goneSaid) {
                goneSaid = true;
                Log.info("a skin is composed from a texture the game's combiner has since taken back: it and any like it stay the game's own");
            }
            return bound;
        }
        if (waiting) {
            return bound;
        }
        if (!any) {
            recipe.plain = true;
            return bound;
        }
        while (fineBytes >= skinCap) {
            Iterator<Fine> longestAgo = fine.values().iterator();
            Fine least = longestAgo.next();
            if (now - least.used < KEPT_NANOS) {
                return bound;
            }
            longestAgo.remove();
            worn.clear();
            // Handed back to the combiner, which gives it to the next skin composed at that size: one is about to be.
            fineBytes -= least.bytes;
            TextureCombiner.instance.releaseTexture(least.texture);
        }
        wornNow();
        making = recipe;
        makingAsked = Pipeline.frame();
        Texture made = composedWithStateKept(recipe);
        composedFrame = Pipeline.frame();
        if (made == null) {
            return bound;
        }
        Fine kept = new Fine(made, System.nanoTime());
        fine.put(result, kept);
        fineBytes += kept.bytes;
        return made;
    }

    /** Whether a skin composed now would find room: under the cap, or with enough of those kept not drawn for KEPT_NANOS to give up. */
    private static boolean roomForASkin(long now, long cap) {
        long kept = fineBytes;
        for (Iterator<Fine> longestAgo = fine.values().iterator(); kept >= cap;) {
            Fine least = longestAgo.next();
            if (now - least.used < KEPT_NANOS) {
                return false;
            }
            kept -= least.bytes;
        }
        return true;
    }

    /** Whether anything a recipe reads has a finer picture in the pack. Sets waiting and gone. */
    private static boolean anyPacked(Recipe recipe) {
        boolean any = false;
        for (Step step : recipe.steps) {
            any |= step.texFrom != null ? anyPacked(step.texFrom) : packedIfStill(step.tex, step.texBack);
            any |= step.maskFrom != null ? anyPacked(step.maskFrom) : packedIfStill(step.mask, step.maskBack);
        }
        return any;
    }

    private static boolean packedIfStill(Texture read, boolean back) {
        if (read == null) {
            return false;
        }
        // A texture the combiner made by a way this does not note, and has since taken
        // back, now holds some other picture or none.
        if (back) {
            gone = true;
            return false;
        }
        return packed(read) != null;
    }

    /**
     * The finer picture for one of the game's, the mod's own before the player's folder's, or
     * null with none or with one still loading, which sets waiting.
     */
    private static Texture packed(Texture game) {
        String name = game.getName();
        if (name == null) {
            return null;
        }
        String relative = relativeOf(name);
        if (relative == null) {
            return null;
        }
        Read mine = (ownWorn & rows.computeIfAbsent(relative, Jar::row)) != 0 ? own(relative, name) : null;
        if (mine == PENDING) {
            waiting = true;
            askedUnfilledFrame = Pipeline.frame();
            return null;
        }
        if (mine != null) {
            if (mine.texture == null) {
                waiting = true;
                askedUnfilledFrame = Pipeline.frame();
            } else if (mine.ahead) {
                mine.ahead = false;
                aheadWorn++;
                if (DevBuild.ON) {
                    Log.info("a skin of the mod's own read ahead is worn, " + aheadWorn + " of the " + aheadRead + " read ahead: " + resources.get(relative));
                }
            }
            asked.add(mine);
            return mine.texture;
        }
        Texture pack;
        if (packed.containsKey(name)) {
            pack = packed.get(name);
        } else {
            pack = load(relative);
            packed.put(name, pack);
        }
        if (pack == null) {
            return null;
        }
        if (pack.isFailure()) {
            packed.put(name, null);
            Log.info("the game could not read the fine skin for " + name + ": that picture stays the game's own");
            return null;
        }
        if (!pack.isReady()) {
            waiting = true;
            return null;
        }
        return pack;
    }

    /** Pack.relative of each texture name asked about, which is worked out of the name letter by letter. */
    private static final HashMap<String, String> relatives = new HashMap<>();
    private static final HashMap<String, String> resources = new HashMap<>();
    /** Jar.row of each path asked about: asked for every plainly textured part of every frame, and worked out by making a string. */
    private static final HashMap<String, Integer> rows = new HashMap<>();

    private static String relativeOf(String name) {
        String relative = relatives.get(name);
        if (relative == null && !relatives.containsKey(name)) {
            relative = Pack.relative(name);
            relatives.put(name, relative);
        }
        return relative;
    }

    /**
     * The mod's own picture for the game's texture of this name at this path under
     * media/textures: the jar's, or with none there the game's own made finer by Tailor, or
     * null where there is neither. Its texture is null until it has been read or made.
     */
    private static Read own(String relative, String name) {
        String path = resources.computeIfAbsent(relative, Jar::resource);
        if (jarless.contains(path)) {
            return tailored(relative, name);
        }
        if (!own.containsKey(path)) {
            if (FineSkins.class.getResource(path) == null) {
                jarless.add(path);
                return tailored(relative, name);
            }
            // Made while a save loads (Pipeline's core), and here should a body be worn before that is done.
            if (SkinCuts.bodyOf(path) != SkinCuts.NO_BODY && !SkinCuts.begin()) {
                return PENDING;
            }
            if (!wanted.contains(path)) {
                wanted.add(path);
            }
            return PENDING;
        }
        return made(own.get(path), path, name);
    }

    /** The most one of the jar's pictures can hold of the card. */
    private static long jarMost() {
        int across = Quality.smallerPictures() ? JAR_LARGEST / 2 : JAR_LARGEST;
        return bytesOf(across, across);
    }

    /**
     * Has the jar's pictures that whoever asked still wants read, all of them, when there is
     * room for them all and nobody nearer waits for room: see RoomLine. Room is made as
     * pictures nothing has worn for OWN_KEPT_NANOS are let go (tendOwn). False, and nothing
     * read, when what is asked for comes to more than the cap could ever hold: the jar's
     * pictures it already has, at their size, and those still to be read, at the largest.
     */
    private static boolean settle(Object who) {
        if (wanted.isEmpty()) {
            return true;
        }
        long need = jarMost() * wanted.size();
        for (int i = 0; i < asked.size(); i++) {
            Read read = asked.get(i);
            if (!read.tailored && asked.indexOf(read) == i) {
                need += read.bytes;
            }
        }
        long cap = VideoMemory.jarSkinBytes();
        if (need > cap) {
            wanted.clear();
            return false;
        }
        long now = System.nanoTime();
        boolean fits = jarBytes + jarMost() * wanted.size() <= cap;
        jarRoomWanted |= !fits;
        if (line.ask(who, asking, now, fits)) {
            for (String path : wanted) {
                Read reading = new Read();
                reading.bytes = jarMost();
                reading.used = now;
                jarBytes += reading.bytes;
                own.put(path, reading);
                reader.execute(() -> read(path, reading));
            }
        }
        wanted.clear();
        return true;
    }

    /** The mod's own pictures just asked for are worn: kept from being let go for OWN_KEPT_NANOS more. */
    private static void wornNow() {
        long now = System.nanoTime();
        for (Read read : asked) {
            read.used = now;
        }
        asked.clear();
    }

    /**
     * The game's own picture of this name made finer by Tailor, or null for one Tailor leaves
     * alone or that is no file of its own. Kept by the texture's name, not by its path under
     * media/textures: the game's file and another mod's of the same path are two pictures.
     * With the card holding as much of what is worn as it is let, no more is made until some
     * is let go, and whoever asked is told to wait, not that there is none.
     */
    private static Read tailored(String relative, String name) {
        String key = tailoredKeys.computeIfAbsent(name, each -> "tailored " + each);
        if (!own.containsKey(key)) {
            Tailor.Fabric fabric = Tailor.of(relative);
            Path file = fabric == null ? null : fileOf(name);
            if (file == null) {
                own.put(key, null);
                return null;
            }
            boolean small = Quality.smallerPictures();
            int across = small ? Tailor.FINE / 2 : Tailor.FINE;
            long most = bytesOf(across, across);
            if (tailoredBytes + most > VideoMemory.tailoredSkinBytes()) {
                tailoredRoomWanted = true;
                return PENDING;
            }
            Read making = new Read();
            making.tailored = true;
            making.bytes = most;
            making.used = System.nanoTime();
            tailoredBytes += most;
            own.put(key, making);
            tailor.execute(() -> tailor(file, fabric, small, making));
        }
        return made(own.get(key), key, name);
    }

    /** A picture asked for, made a texture of the game's once it has been read. */
    private static Read made(Read read, String path, String name) {
        if (read == null || read.texture != null) {
            return read;
        }
        if (read.fineAsItIs) {
            tailoredBytes -= read.bytes;
            own.put(path, null);
            return null;
        }
        if (read.unreadable != null) {
            // The game's file, or another mod's: one that Java cannot read is no fault of the mod's.
            tailoredBytes -= read.bytes;
            own.put(path, null);
            if (unreadableSaid++ < 8) {
                Log.info("what is worn from " + name + " could not be read to be made finer and stays the game's own: " + read.unreadable);
            }
            return null;
        }
        if (read.failure != null) {
            throw new IllegalStateException("the mod's own skin at " + path + " could not be made", read.failure);
        }
        if (read.pixels != null && ownMadeFrame != Pipeline.frame()) {
            ownMadeFrame = Pipeline.frame();
            fill(read, path, name, MAKING_NANOS);
        }
        return read;
    }

    /** Fills some of a read picture's texture (makeSome), and once it is all in counts it as made and lets go of what was read. */
    private static void fill(Read read, String path, String name, long nanos) {
        try {
            makeSome(read, read.pixels, path, nanos);
        } catch (NoRoom full) {
            // Asked for again, it is read afresh under the caps the graphics have stepped down to.
            Pipeline.noted(GL_OUT_OF_MEMORY);
            letGoOf(read);
            own.remove(path);
            if (!noRoomSaid) {
                noRoomSaid = true;
                Log.info(full.getMessage() + ": it is let go, and the graphics step down (said once)");
            }
            return;
        }
        if (read.texture != null) {
            // Counted as the most it could be when it was asked for, and as what it is now that it is made.
            long is = bytesOf(read.wide, read.high);
            aheadHeld -= read.aheadHeld;
            read.aheadHeld = 0;
            if (read.tailored) {
                tailoredBytes += is - read.bytes;
            } else {
                jarBytes += is - read.bytes;
            }
            read.bytes = is;
            read.pixels = null;
            synchronized (read) {
                read.memory.free();
                read.memory = null;
            }
            read.used = System.nanoTime();
            if (read.tailored ? tailorSaid++ < 3 : ownSaid.merge(path.substring(0, path.lastIndexOf('/')), 1, Integer::sum) <= 3) {
                if (DevBuild.ON) {
                    Log.info((read.tailored ? "what is worn from " + name + " is made finer, " : "the mod's own skin " + path + " is worn, ") + read.texture.getWidth() + "x" + read.texture.getHeight()
                            + String.format(Locale.ROOT, ", filled over %d frames in %.1f ms", read.makingFrames, read.makingNanos / 1e6));
                }
            }
        }
    }

    /**
     * Fills the texture of a picture read a strip at a time, as many strips as so many
     * nanoseconds of this frame allow, and makes it the Read's texture once every row is in.
     * The game's own way, a TextureID made from the whole picture, hands it to glTexImage2D in
     * one go, and with the game's textures kept compressed the driver packed all of it before
     * it returned: 50 to 130 ms a frame on the render thread for a 2048 square. The texture is
     * made here as RGBA8, which the driver takes as it is, from the picture padded to the
     * texture's size (Pictures.padded). Every call to OpenGL is checked: an
     * error is thrown, and no room on the card as NoRoom, which fill takes. Render thread.
     */
    private static void makeSome(Read read, ByteBuffer pixels, String path, long nanos) {
        long from = System.nanoTime();
        // What OpenGL has flagged before anything here is asked of it is not this filling's, and is not to be lost.
        int flagged = GL11.glGetError();
        if (flagged != 0) {
            throw new IllegalStateException(String.format(Locale.ROOT, "OpenGL error 0x%X was left by the drawing before a skin's texture was filled", flagged));
        }
        int bound = GL11.glGetInteger(GL_TEXTURE_BINDING_2D), remembered = Texture.lastTextureID;
        int unpackBuffer = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        int across = Pictures.encompassing(read.wide), down = Pictures.encompassing(read.high);
        try {
            // With a buffer bound to unpack from, OpenGL would read the picture out of that buffer.
            GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            Pictures.unpackTight();
            if (read.making == null) {
                // The game's TextureID, for the game's Texture to wear, made with nothing in it and no copy of a picture.
                read.making = new TextureID(read.wide, read.high, FBO, true);
                checked("making the texture of", path);
                GL11.glBindTexture(GL_TEXTURE_2D, read.making.getID());
                GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, across, down, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
                GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
                checked("making room for", path);
            } else {
                GL11.glBindTexture(GL_TEXTURE_2D, read.making.getID());
            }
            int strip = Math.max(1, STRIP_DOTS / across);
            do {
                int rows = Math.min(strip, down - read.rowsMade);
                GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, read.rowsMade, across, rows, GL_RGBA, GL_UNSIGNED_BYTE, pixels.slice(read.rowsMade * across * 4, rows * across * 4));
                checked("filling the texture of", path);
                read.rowsMade += rows;
            } while (read.rowsMade < down && System.nanoTime() - from < nanos);
        } finally {
            GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
            GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
            GL11.glBindTexture(GL_TEXTURE_2D, bound);
            Texture.lastTextureID = remembered;
        }
        read.makingNanos += System.nanoTime() - from;
        read.makingFrames++;
        if (read.rowsMade == down) {
            read.texture = new Texture(read.making, "CameraUnlock" + path);
        }
    }

    /** Throws what OpenGL flagged in the call just made for a texture of the mod's own skin at this path. */
    private static void checked(String doing, String path) {
        int error = GL11.glGetError();
        if (error == GL_OUT_OF_MEMORY) {
            throw new NoRoom("the graphics card has no room left for the texture of the mod's own skin " + path + " (OpenGL out of memory " + doing + " it)");
        }
        if (error != 0) {
            throw new IllegalStateException(String.format(Locale.ROOT, "OpenGL error 0x%X %s the mod's own skin %s", error, doing, path));
        }
    }

    /** The file a texture of the game's was read from, by its name, or null for one that was not read from a file of its own. */
    private static Path fileOf(String name) {
        try {
            Path file = Path.of(name);
            return Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException unnameable) {
            return null;
        }
    }

    /** Makes the game's picture in this file finer, and lays this fabric on it, into four bytes a dot. On the tailor's thread. */
    private static void tailor(Path file, Tailor.Fabric fabric, boolean small, Read into) {
        OffHeap.Block[] taken = new OffHeap.Block[1];
        synchronized (into) {
            if (into.dropped) {
                return;
            }
        }
        try {
            // The game's file, or another mod's. Its size is read before its dots: one too large to need making finer is
            // not read whole to learn that. Whatever Java's readers make of a file that is not what they expect is that
            // file's fault, not the mod's.
            BufferedImage image;
            int times;
            try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
                Iterator<ImageReader> decoders = in == null ? null : ImageIO.getImageReaders(in);
                if (decoders == null || !decoders.hasNext()) {
                    into.unreadable = new IOException(file + " is not a picture this Java can read");
                    return;
                }
                ImageReader decoder = decoders.next();
                try {
                    decoder.setInput(in);
                    times = Tailor.times(decoder.getWidth(0), decoder.getHeight(0), small);
                    if (times == 0) {
                        into.fineAsItIs = true;
                        return;
                    }
                    image = decoder.read(0);
                } finally {
                    decoder.dispose();
                }
            } catch (IOException failure) {
                into.unreadable = failure;
                return;
            } catch (RuntimeException | OutOfMemoryError failure) {
                // A picture whose size passed can still ask the decoder for more than the heap has, in a chunk past its pixels.
                into.unreadable = new IOException(file + " could not be read as a picture", failure);
                return;
            }
            int wide = image.getWidth(), high = image.getHeight();
            int[] source = Tailor.dots(image);
            float[] laid = fabric.name() == null ? null : Tailor.tile(Tailor.light(fabric.name()), Tailor.FABRIC_SIZE, Math.max(wide, high) * times, fabric.repeats());
            wide *= times;
            high *= times;
            OffHeap.Block memory = OffHeap.take(wide * high * 4);
            taken[0] = memory;
            ByteBuffer pixels = memory.bytes;
            Tailor.fine(source, wide / times, high / times, times, laid, Tailor.edged(source), pixels);
            pixels.flip();
            memory = Pictures.padded(memory, wide, high);
            taken[0] = null;
            into.hand(memory, wide, high);
        } catch (IOException | RuntimeException | Error failure) {
            // The fabric is missing from the jar, or Tailor is wrong: the mod's own fault, and said as one.
            into.failure = new IOException(file + " could not be made finer", failure);
        } finally {
            if (taken[0] != null) {
                taken[0].free();
            }
        }
    }

    /**
     * Reads one of the jar's pictures into four bytes a dot. A body model has sheets for a skirt
     * to be painted on, which a skin leaves see-through, a print has a shape, and a stain fades
     * out, and a JPEG is solid all over: how much of a body's skin shows is SkinCuts', an
     * animal's shows all over, and of the rest it is the blue of the picture beside it, of the
     * same size, black for none. On the reader's thread.
     */
    private static final String BODIES = "/skins/body/";

    private static void read(String path, Read into) {
        // What has been taken and not yet handed to the Read or given back: given back if the reading ends early.
        OffHeap.Block[] taken = new OffHeap.Block[1];
        synchronized (into) {
            // Let go of while it waited its turn: there is nothing to read it for.
            if (into.dropped) {
                return;
            }
        }
        try {
            int body = SkinCuts.bodyOf(path);
            String cutPath = body == SkinCuts.NO_BODY && !path.startsWith(BODIES) ? Jar.cut(path) : null;
            read(path, into, body, cutPath, taken);
        } catch (IOException failure) {
            into.failure = failure;
        } catch (RuntimeException | Error failure) {
            // Left uncaught it ends the reader's task with neither a picture nor a failure, and whoever waits for this one waits for good.
            into.failure = new IOException(path + " could not be read", failure);
        } finally {
            if (taken[0] != null) {
                taken[0].free();
            }
        }
    }

    private static void read(String path, Read into, int body, String cutPath, OffHeap.Block[] taken) throws IOException {
        try (InputStream in = FineSkins.class.getResourceAsStream(path); InputStream cutIn = cutPath == null ? null : FineSkins.class.getResourceAsStream(cutPath)) {
            if (cutPath != null && cutIn == null) {
                throw new IOException("the jar has no " + cutPath);
            }
            BufferedImage image = ImageIO.read(in), cut = cutIn == null ? null : ImageIO.read(cutIn);
            if (image == null || cutIn != null && cut == null) {
                throw new IOException(path + (cutPath == null ? "" : " or " + cutPath) + " is not a picture this Java can read");
            }
            int wide = image.getWidth(), high = image.getHeight();
            if (cut != null && (cut.getWidth() != wide || cut.getHeight() != high)) {
                throw new IOException(cutPath + " is not the size of " + path);
            }
            int[] row = new int[wide], cutRow = new int[wide];
            boolean greyCut = cut != null && cut.getType() == BufferedImage.TYPE_BYTE_GRAY;
            if (cut != null && !greyCut && cut.getColorModel().getColorSpace().getType() == ColorSpace.TYPE_GRAY) {
                throw new IOException(cutPath + " is stored as greys that are not a byte each: what fades in it would be read lightened");
            }
            byte[] bodyCut = body == SkinCuts.NO_BODY ? null : SkinCuts.of(body, wide, high);
            if (cut == null && bodyCut == null) {
                Arrays.fill(cutRow, 255);
            }
            OffHeap.Block memory = OffHeap.take(wide * high * 4);
            taken[0] = memory;
            ByteBuffer pixels = memory.bytes;
            for (int y = 0; y < high; y++) {
                image.getRGB(0, y, wide, 1, row, 0, wide);
                // Asked for as colours, a picture stored as greys is handed over lightened: its numbers are read as they are.
                if (greyCut) {
                    cut.getRaster().getSamples(0, y, wide, 1, 0, cutRow);
                } else if (cut != null) {
                    cut.getRGB(0, y, wide, 1, cutRow, 0, wide);
                }
                for (int x = 0; x < wide; x++) {
                    int argb = row[x];
                    pixels.put((byte) (argb >> 16)).put((byte) (argb >> 8)).put((byte) argb).put(bodyCut == null ? (byte) cutRow[x] : bodyCut[y * wide + x]);
                }
            }
            pixels.flip();
            if (Quality.smallerPictures() && wide % 2 == 0 && high % 2 == 0) {
                OffHeap.Block half = Pictures.halved(pixels, wide, high);
                taken[0] = half;
                memory.free();
                memory = half;
                wide /= 2;
                high /= 2;
            }
            memory = Pictures.padded(memory, wide, high);
            taken[0] = null;
            into.hand(memory, wide, high);
        }
    }

    /** The player's folder's picture for the game's texture at this path under media/textures, or null with none. */
    private static Texture load(String relative) {
        if (folder == null) {
            return null;
        }
        Path file;
        try {
            file = folder.resolve(relative);
        } catch (InvalidPathException unnameable) {
            return null;
        }
        if (!Files.isRegularFile(file)) {
            return null;
        }
        // With the flags the game reads its own skins with. It loads the file on another
        // thread, so what comes back is often not ready yet.
        // The game holds a texture loaded as a model's to the size its options allow,
        // 256 dots by default. The flag that says so is left off, and the rest kept.
        Texture pack = Texture.getSharedTexture(file.toString(), ModelManager.instance.getTextureFlags() & ~SIZE_HELD);
        if (pack == null) {
            // The game catches what went wrong and logs it only with its FileIO debug log on. The file is the
            // player's, so it costs that picture alone, as a file the game reads and fails on does (isFailure).
            Log.info("the game would not load the fine skin " + file + " (Texture.getSharedTexture gave none): that picture stays the game's own");
        }
        return pack;
    }

    private static Texture composedWithStateKept(Recipe recipe) throws Exception {
        return withCombinerStateKept(() -> {
            composing = true;
            try {
                return composed(recipe);
            } finally {
                composing = false;
            }
        });
    }

    /**
     * Runs work that drives the game's texture combiner on the render thread, wherever in a
     * frame it runs, and puts back the GL state the combiner leaves changed. Render thread.
     */
    static <T> T withCombinerStateKept(Callable<T> work) throws Exception {
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int arrays = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int vertices = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING), elements = GL11.glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int pushed = GL11.glGetInteger(GL_ATTRIB_STACK_DEPTH), clientPushed = GL11.glGetInteger(GL_CLIENT_ATTRIB_STACK_DEPTH);
        int remembered = Texture.lastTextureID;
        int believed = TextureFBO.lastID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        // The combiner pushes the draw buffers of the framebuffer the game believes is bound,
        // TextureFBO.lastID, and pops them onto the one it believes in as it ends. The game's
        // logic thread sets that belief to none as it destroys the game's own framebuffer for a
        // window of another size (TextureFBO.destroy, from Core.setScreenSize), whatever the
        // render thread is in the middle of. A composition that write lands in pops a colour
        // attachment onto the window's framebuffer, which is GL_INVALID_OPERATION. So the
        // combiner is run believing none is bound, with none bound: that write changes nothing.
        TextureFBO.lastID = 0;
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, 0);
        try {
            return work.call();
        } finally {
            TextureFBO.lastID = believed;
            // The combiner pushes state of its own and pops it as it ends. One that threw
            // on the way has not, and what it left is popped before what was pushed here.
            while (GL11.glGetInteger(GL_CLIENT_ATTRIB_STACK_DEPTH) > clientPushed) {
                GL11.glPopClientAttrib();
            }
            while (GL11.glGetInteger(GL_ATTRIB_STACK_DEPTH) > pushed + 1) {
                GL11.glPopAttrib();
            }
            // What was pushed here has the draw buffers of the picture's framebuffer in it, and
            // they are popped onto whichever is bound, so the picture's is bound first. The
            // combiner ends with another bound: popped there, a colour attachment onto the
            // window's framebuffer, they are GL_INVALID_OPERATION.
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
            GL11.glPopAttrib();
            GL30.glBindVertexArray(arrays);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elements);
            // The program current before, so Program's note of which of its own is current holds again.
            GL20.glUseProgram(program);
            Texture.lastTextureID = remembered;
        }
    }

    /**
     * Runs a recipe again with the pack's pictures in place of the game's. Where a step sits
     * and how large is given in 256ths of the result whatever its size, so the steps go to
     * the combiner as they were; the result is as large as the largest picture read. Null on
     * a frame that composed only one of the recipes it is composed from, into parts.
     */
    private static Texture composed(Recipe recipe) throws Exception {
        Recipe part = unmade(recipe);
        if (part != null) {
            parts.put(part, combined(part));
            return null;
        }
        try {
            return combined(recipe);
        } finally {
            spendParts();
        }
    }

    /** The first recipe this one is composed from, the deepest first, that is not composed yet, or null once all are. */
    private static Recipe unmade(Recipe recipe) {
        for (Step step : recipe.steps) {
            Recipe from = step.texFrom != null && !parts.containsKey(step.texFrom) ? step.texFrom
                    : step.maskFrom != null && !parts.containsKey(step.maskFrom) ? step.maskFrom : null;
            if (from != null) {
                Recipe deeper = unmade(from);
                return deeper != null ? deeper : from;
            }
        }
        return null;
    }

    /**
     * Made or not, what was composed for the skin on the way is done with. Spent, and in time
     * destroyed, as the combiner itself destroys what its pool has no room for: handed back, it
     * keeps up to a hundred textures of each size for good, and one clothed body's skins left
     * sixty of them there, 240 MB at 1024 square.
     */
    private static void spendParts() {
        for (Texture each : parts.values()) {
            Spent done = new Spent(each, Pipeline.frame(), 4L * each.getWidth() * each.getHeight());
            spent.add(done);
            spentBytes += done.bytes();
        }
        parts.clear();
        making = null;
    }

    private static Texture combined(Recipe recipe) throws Exception {
        ArrayList<TextureCombinerCommand> commands = new ArrayList<>(recipe.steps.length);
        for (Step step : recipe.steps) {
            TextureCombinerCommand command = new TextureCombinerCommand();
            command.x = step.x;
            command.y = step.y;
            command.w = step.w;
            command.h = step.h;
            command.blendSrc = step.blendSrc;
            command.blendDest = step.blendDest;
            command.blendSrcA = step.blendSrcA;
            command.blendDestA = step.blendDestA;
            command.shader = step.shader;
            command.shaderParams = step.shaderParams;
            command.tex = read(step.texFrom, step.tex);
            command.mask = read(step.maskFrom, step.mask);
            commands.add(command);
        }
        long from = System.nanoTime();
        Texture made = TextureCombiner.instance.combine(commands);
        if (DevBuild.ON && said++ < 3) {
            Log.info(String.format(Locale.ROOT, "a skin%s composed at %dx%d from %d layers in %.2f ms", recipe == making ? "" : "'s part",
                    made.getWidth(), made.getHeight(), commands.size(), (System.nanoTime() - from) / 1e6));
        }
        return made;
    }

    private static Texture read(Recipe from, Texture game) {
        if (from != null) {
            // The game handed its own back long ago, so it is made again whether or not
            // the pack has anything for it, a frame before.
            return parts.get(from);
        }
        if (game == null) {
            return null;
        }
        Texture pack = packed(game);
        return pack != null ? pack : game;
    }
}
