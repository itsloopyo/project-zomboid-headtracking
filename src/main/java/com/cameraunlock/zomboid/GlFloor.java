package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjglx.opengl.Display;

/**
 * What the graphics card's OpenGL must have for first person to be drawn at all: version 3.3
 * (framebuffers, vertex arrays, instancing, fences, sampler objects, attribute divisors) with
 * EXT_texture_array, which the world shaders read their arrays through.
 * Everything newer the mod uses is asked for where it is used, with a way round it.
 *
 * Below this first person stays off and the game plays as it does without the mod. Nothing above
 * it is called first: LWJGL calls a function the context does not have through a null pointer,
 * which takes the whole game down with it.
 */
final class GlFloor {
    /** The most samplers one fragment stage reads (the world's), checked against GL_MAX_TEXTURE_IMAGE_UNITS. ShaderTests holds every program to it. */
    static final int FRAGMENT_SAMPLERS = 27;
    /** The most uniform components one fragment stage holds (the world's, a vec4 to each uniform), checked against GL_MAX_FRAGMENT_UNIFORM_COMPONENTS. ShaderTests holds every program to it. */
    static final int FRAGMENT_UNIFORM_COMPONENTS = 2836;

    private static final int GL_MAX_TEXTURE_IMAGE_UNITS = 0x8872, GL_MAX_FRAGMENT_UNIFORM_COMPONENTS = 0x8B49, GL_VERSION = 0x1F02, GL_RENDERER = 0x1F01;

    private static volatile boolean asked;
    private static volatile String missing;

    private GlFloor() {
    }

    /** What the game's OpenGL context lacks of the floor, or null. Any thread: it reads the capabilities the game took when it made its window. */
    static String missing() {
        if (!asked) {
            ask();
        }
        return missing;
    }

    private static synchronized void ask() {
        if (asked) {
            return;
        }
        GLCapabilities card = Display.capabilities;
        if (card == null) {
            throw new IllegalStateException("the floor was asked for before the game made its OpenGL context: org.lwjglx.opengl.Display.capabilities is not set");
        }
        missing = shortOf(card.OpenGL33, card.GL_EXT_texture_array);
        if (missing != null) {
            Log.info(missing);
        }
        asked = true;
    }

    /** Why a context with these is below the floor, or null. */
    static String shortOf(boolean version33, boolean textureArray) {
        if (version33 && textureArray) {
            return null;
        }
        String shortOf = !version33 ? "is older than 3.3" + (!textureArray ? " and has no EXT_texture_array" : "") : "has no EXT_texture_array";
        return "the graphics card's OpenGL " + shortOf + ", which first person needs, so the game is drawn as it is without the mod";
    }

    /**
     * What the card holds too few of for the world's shaders, or null. Render thread, before the
     * first program is compiled: a driver short of either refuses the world at link, and this
     * says which in a line of its own.
     */
    static String limits() {
        int samplers = GL11.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS), uniforms = GL11.glGetInteger(GL_MAX_FRAGMENT_UNIFORM_COMPONENTS);
        Log.info("OpenGL " + GL11.glGetString(GL_VERSION) + " on " + GL11.glGetString(GL_RENDERER) + ": " + samplers
                + " textures and " + uniforms + " uniform components to a fragment shader, of the " + FRAGMENT_SAMPLERS + " and " + FRAGMENT_UNIFORM_COMPONENTS + " first person needs");
        if (samplers < FRAGMENT_SAMPLERS || uniforms < FRAGMENT_UNIFORM_COMPONENTS) {
            return "the graphics card reads " + samplers + " textures and holds " + uniforms + " uniform components in a fragment shader, and first person's world needs "
                    + FRAGMENT_SAMPLERS + " and " + FRAGMENT_UNIFORM_COMPONENTS + ", so the game is drawn as it is without the mod";
        }
        return null;
    }
}
