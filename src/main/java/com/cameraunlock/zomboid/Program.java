package com.cameraunlock.zomboid;

import java.util.HashMap;
import org.lwjgl.opengl.GL20;

/** One compiled program and the places its uniforms live. */
final class Program {
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;

    final int id;
    private final HashMap<String, Integer> uniforms = new HashMap<>();

    private Program(int id) {
        this.id = id;
    }

    /** Compiles and links a program. Null if the driver refused it, with the reason added to problem. */
    static Program link(String name, String vertexSource, String fragmentSource, StringBuilder problem) {
        int vertex = compile(name + " vertex", GL_VERTEX_SHADER, vertexSource, problem);
        int fragment = compile(name + " fragment", GL_FRAGMENT_SHADER, fragmentSource, problem);
        if (vertex == 0 || fragment == 0) {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            return null;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, fragment);
        GL20.glLinkProgram(id);
        GL20.glDetachShader(id, vertex);
        GL20.glDetachShader(id, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(id, GL_LINK_STATUS) == 0) {
            problem.append("the ").append(name).append(" shader did not link: ").append(GL20.glGetProgramInfoLog(id)).append(' ');
            GL20.glDeleteProgram(id);
            return null;
        }
        return new Program(id);
    }

    private static int compile(String name, int kind, String source, StringBuilder problem) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            problem.append("the ").append(name).append(" shader did not compile: ").append(GL20.glGetShaderInfoLog(shader)).append(' ');
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    int at(String name) {
        Integer location = uniforms.get(name);
        if (location == null) {
            location = GL20.glGetUniformLocation(id, name);
            uniforms.put(name, location);
        }
        return location;
    }

    void use() {
        GL20.glUseProgram(id);
    }

    void set(String name, int value) {
        GL20.glUniform1i(at(name), value);
    }

    void set(String name, float x) {
        GL20.glUniform1f(at(name), x);
    }

    void set(String name, float x, float y) {
        GL20.glUniform2f(at(name), x, y);
    }

    void set(String name, float x, float y, float z) {
        GL20.glUniform3f(at(name), x, y, z);
    }

    void set(String name, float x, float y, float z, float w) {
        GL20.glUniform4f(at(name), x, y, z, w);
    }

    void set(String name, float[] matrix) {
        GL20.glUniformMatrix4fv(at(name), false, matrix);
    }
}
