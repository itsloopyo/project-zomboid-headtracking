package com.cameraunlock.zomboid;

import org.lwjgl.opengl.GL20;

/**
 * A shader program for something small drawn over the picture, made the first time it is
 * wanted. One the driver refuses is said in the log once and what it draws is left out: the
 * picture itself does not depend on it.
 *
 * Render thread only.
 */
final class SmallProgram {
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;

    /** Draws a pair of triangles given in the picture's own measure, -1 to 1 each way. */
    static final String FLAT_VERTEX = """
            #version 120
            void main() {
                gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
            }
            """;

    private final String name, vertex, fragment;
    private boolean made;
    private int id;

    SmallProgram(String name, String vertex, String fragment) {
        this.name = name;
        this.vertex = vertex;
        this.fragment = fragment;
    }

    /** Makes the program current. False if the driver refused it, with nothing changed. */
    boolean use() {
        if (!made) {
            made = true;
            id = link();
        }
        if (id == 0) {
            return false;
        }
        GL20.glUseProgram(id);
        return true;
    }

    int at(String uniform) {
        return GL20.glGetUniformLocation(id, uniform);
    }

    private int link() {
        int program = GL20.glCreateProgram();
        int[] kinds = {GL_VERTEX_SHADER, GL_FRAGMENT_SHADER};
        String[] sources = {vertex, fragment};
        for (int stage = 0; stage < 2; stage++) {
            int shader = GL20.glCreateShader(kinds[stage]);
            GL20.glShaderSource(shader, sources[stage]);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
                Log.info("the " + name + " is not drawn: stage " + (stage + 1) + " of its shader did not compile: " + GL20.glGetShaderInfoLog(shader));
                return 0;
            }
            GL20.glAttachShader(program, shader);
        }
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL_LINK_STATUS) == 0) {
            Log.info("the " + name + " is not drawn: its shader did not link: " + GL20.glGetProgramInfoLog(program));
            return 0;
        }
        return program;
    }
}
