package com.cameraunlock.zomboid;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import zombie.core.opengl.SmartShader;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.textures.SmartTexture;
import zombie.core.textures.Texture;
import zombie.core.textures.TextureCombiner;
import zombie.core.textures.TextureCombinerCommand;
import zombie.core.textures.TextureCombinerShaderParam;

/**
 * Skins finer than the game's, for the people the picture draws near the eye. The game
 * composes what a person wears from pictures on disk: skin, dirt, blood, clothes painted on
 * the body, the holes in them. A folder on the player's own machine may hold the same
 * pictures at a higher resolution, and where it does the same composition is run again from
 * those, by the game's own combiner, and the picture's draw wears the result. The game's own
 * textures and its own view are untouched.
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

        static Path folder() {
            return Path.of(System.getProperty("user.home"), "Zomboid", "CameraUnlock", "skins");
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
            // it is under names nothing in the pack.
            for (String piece : relative.split("/", -1)) {
                if (piece.isEmpty() || piece.equals(".") || piece.equals("..")) {
                    return null;
                }
            }
            return relative;
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
        /** The count of textures handed back when this was noted. */
        final long noted;
        /** Set once it is known that composing this again would give nothing finer. */
        boolean plain;

        Recipe(ArrayList<TextureCombinerCommand> commands) {
            steps = new Step[commands.size()];
            for (int step = 0; step < steps.length; step++) {
                steps[step] = new Step(commands.get(step));
            }
            noted = handedBack;
        }
    }

    /** A finer texture, and when it was last drawn. */
    private static final class Fine {
        final Texture texture;
        long used;

        Fine(Texture texture, long used) {
            this.texture = texture;
            this.used = used;
        }
    }

    /** A 1024 by 1024 skin is 4 MB on the graphics card: no more of them than this are kept. */
    private static final int MOST = 64;
    /** A finer skin drawn this recently is not given up to make room for another. */
    private static final long KEPT_NANOS = 2_000_000_000L;
    /** One composition a frame: a crowd coming into view gets its skins over a second, not in one long frame. */
    private static final long BETWEEN_NANOS = 4_000_000L;

    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF, GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    private static final int GL_ATTRIB_STACK_DEPTH = 0x0BB0, GL_CLIENT_ATTRIB_STACK_DEPTH = 0x0BB1;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9, GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6, GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D, GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_ELEMENT_ARRAY_BUFFER_BINDING = 0x8895;

    private static boolean started, off;
    private static Path folder;
    /** How the game composed each texture it has not yet handed back. */
    private static final IdentityHashMap<Texture, Recipe> recipes = new IdentityHashMap<>();
    /** When each texture the combiner was handed back was handed back, by the count of them. */
    private static final IdentityHashMap<Texture, Long> handedBackAt = new IdentityHashMap<>();
    private static long handedBack;
    /** The finer texture for each of the game's, the one drawn longest ago first. The game's textures are equal only to themselves. */
    private static final LinkedHashMap<Texture, Fine> fine = new LinkedHashMap<>(MOST * 2, 0.75f, true);
    /** The pack's picture for each name the game has shown, null for a name the pack has nothing for. */
    private static final HashMap<String, Texture> packed = new HashMap<>();
    private static boolean composing;
    /** What the last look through a recipe found: a picture still loading, or one the combiner has taken back. */
    private static boolean waiting, gone;
    private static boolean goneSaid;
    private static long composedAt;

    /**
     * What the picture's draw should wear in place of the texture the game gives a part: a
     * finer one where the pack has what it takes, or the game's own. Composing a finer one
     * runs the game's combiner, which binds its own targets, programs and buffers and ends by
     * putting back the state the game remembers, not the picture's: all of it is put back
     * here, and the caller sets its own state after this, not before. Render thread only.
     */
    private static final int SIZE_HELD = 128;
    private static int said;

    static Texture of(Texture bound) {
        try {
            return finer(bound);
        } catch (Exception | LinkageError failure) {
            refuse("a finer skin could not be made", failure);
            return bound;
        }
    }

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
            Path at = Pack.folder();
            if (!Files.isDirectory(at)) {
                off = true;
                Log.info("no fine skins at " + at + ": characters wear the game's own");
                return false;
            }
            String gap = Agent.fineSkinsGap();
            if (gap != null) {
                off = true;
                Log.info("fine skins are off, and characters wear the game's own: " + gap);
                return false;
            }
            folder = at;
            Log.info("fine skins are read from " + at);
        }
        return !off;
    }

    private static synchronized void refuse(String why, Throwable cause) {
        if (!off) {
            Log.error("fine skins are off for this session, and characters wear the game's own: " + why, cause);
        }
        off = true;
        recipes.clear();
        handedBackAt.clear();
        fine.clear();
        packed.clear();
    }

    private static synchronized void note(Texture result, ArrayList<TextureCombinerCommand> commands) {
        if (composing || !on()) {
            return;
        }
        recipes.put(result, new Recipe(commands));
        // Should the game compose anew into a texture it never handed back, what was made
        // after the old composition is no longer this texture's.
        Fine stale = fine.remove(result);
        if (stale != null) {
            TextureCombiner.instance.releaseTexture(stale.texture);
        }
    }

    private static synchronized void forget(Texture texture) {
        if (!on()) {
            return;
        }
        handedBackAt.put(texture, ++handedBack);
        recipes.remove(texture);
        Fine made = fine.remove(texture);
        if (made != null) {
            TextureCombiner.instance.releaseTexture(made.texture);
        }
    }

    private static synchronized Texture finer(Texture bound) throws Exception {
        if (!on()) {
            return bound;
        }
        waiting = false;
        gone = false;
        if (!(bound instanceof SmartTexture smart)) {
            Texture pack = packed(bound);
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
        boolean any = anyPacked(recipe);
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
        if (now - composedAt < BETWEEN_NANOS) {
            return bound;
        }
        if (fine.size() >= MOST) {
            Iterator<Fine> longestAgo = fine.values().iterator();
            Fine least = longestAgo.next();
            if (now - least.used < KEPT_NANOS) {
                return bound;
            }
            longestAgo.remove();
            TextureCombiner.instance.releaseTexture(least.texture);
        }
        Texture made = composedWithStateKept(recipe);
        composedAt = System.nanoTime();
        fine.put(result, new Fine(made, composedAt));
        return made;
    }

    /** Whether anything a recipe reads has a finer picture in the pack. Sets waiting and gone. */
    private static boolean anyPacked(Recipe recipe) {
        boolean any = false;
        for (Step step : recipe.steps) {
            any |= step.texFrom != null ? anyPacked(step.texFrom) : packedIfStill(step.tex, recipe);
            any |= step.maskFrom != null ? anyPacked(step.maskFrom) : packedIfStill(step.mask, recipe);
        }
        return any;
    }

    private static boolean packedIfStill(Texture read, Recipe recipe) {
        if (read == null) {
            return false;
        }
        // A texture the combiner made by a way this does not note, and has since taken
        // back, now holds some other picture or none.
        Long back = handedBackAt.get(read);
        if (back != null && back > recipe.noted) {
            gone = true;
            return false;
        }
        return packed(read) != null;
    }

    /** The pack's picture for one of the game's, or null with none or with one still loading, which sets waiting. */
    private static Texture packed(Texture game) {
        String name = game.getName();
        if (name == null) {
            return null;
        }
        Texture pack;
        if (packed.containsKey(name)) {
            pack = packed.get(name);
        } else {
            pack = load(name);
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

    private static Texture load(String name) {
        String relative = Pack.relative(name);
        if (relative == null) {
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
            throw new IllegalStateException("the game would not load " + file + ", and its own log says why");
        }
        return pack;
    }

    private static Texture composedWithStateKept(Recipe recipe) throws Exception {
        int drawTo = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), readFrom = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int arrays = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int vertices = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING), elements = GL11.glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int pushed = GL11.glGetInteger(GL_ATTRIB_STACK_DEPTH), clientPushed = GL11.glGetInteger(GL_CLIENT_ATTRIB_STACK_DEPTH);
        int remembered = Texture.lastTextureID;
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        composing = true;
        try {
            return composed(recipe);
        } finally {
            composing = false;
            // The combiner pushes state of its own and pops it as it ends. One that threw
            // on the way has not, and what it left is popped with what was pushed here.
            while (GL11.glGetInteger(GL_CLIENT_ATTRIB_STACK_DEPTH) > clientPushed) {
                GL11.glPopClientAttrib();
            }
            while (GL11.glGetInteger(GL_ATTRIB_STACK_DEPTH) > pushed) {
                GL11.glPopAttrib();
            }
            GL30.glBindVertexArray(arrays);
            GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
            GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elements);
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTo);
            GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readFrom);
            GL20.glUseProgram(program);
            Texture.lastTextureID = remembered;
        }
    }

    /**
     * Runs a recipe again with the pack's pictures in place of the game's. Where a step sits
     * and how large is given in 256ths of the result whatever its size, so the steps go to
     * the combiner as they were; the result is as large as the largest picture read.
     */
    private static Texture composed(Recipe recipe) throws Exception {
        ArrayList<TextureCombinerCommand> commands = new ArrayList<>(recipe.steps.length);
        ArrayList<Texture> onTheWay = new ArrayList<>();
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
            command.tex = read(step.texFrom, step.tex, onTheWay);
            command.mask = read(step.maskFrom, step.mask, onTheWay);
            commands.add(command);
        }
        Texture made = TextureCombiner.instance.combine(commands);
        if (said++ < 3) {
            Log.info("a skin composed at " + made.getWidth() + "x" + made.getHeight() + " from " + commands.size() + " layers");
        }
        for (Texture each : onTheWay) {
            TextureCombiner.instance.releaseTexture(each);
        }
        return made;
    }

    private static Texture read(Recipe from, Texture game, ArrayList<Texture> onTheWay) throws Exception {
        if (from != null) {
            // The game handed its own back long ago, so it is made again whether or not
            // the pack has anything for it.
            Texture made = composed(from);
            onTheWay.add(made);
            return made;
        }
        if (game == null) {
            return null;
        }
        Texture pack = packed(game);
        return pack != null ? pack : game;
    }
}
