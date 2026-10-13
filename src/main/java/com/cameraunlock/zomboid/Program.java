package com.cameraunlock.zomboid;

import java.util.HashMap;
import org.lwjgl.opengl.GL20;

/** One compiled program and the places its uniforms live. */
final class Program {
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;

    final int id;
    private final HashMap<String, Uniform> uniforms = new HashMap<>();

    private Program(int id) {
        this.id = id;
    }

    /** Compiles and links a program. Null if the driver refused it, with the reason added to problem. */
    static Program link(String name, String vertexSource, String fragmentSource, StringBuilder problem) {
        return link(name, vertexSource, fragmentSource, problem, new String[0]);
    }

    /**
     * As link, with the vertex attributes named bound to the places 0, 1, 2 and on in that order, so
     * that programs sharing a vertex shader share one layout of vertices whatever each reads.
     */
    static Program link(String name, String vertexSource, String fragmentSource, StringBuilder problem, String... attributesInOrder) {
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
        for (int i = 0; i < attributesInOrder.length; i++) {
            GL20.glBindAttribLocation(id, i, attributesInOrder[i]);
        }
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

    /**
     * Where a uniform lives, for a caller that writes it with GL20 itself. What the program holds
     * there is then not known here, and set always sends that uniform from then on.
     */
    int at(String name) {
        Uniform uniform = uniform(name);
        uniform.raw = true;
        uniform.held = null;
        return uniform.location;
    }

    private Uniform uniform(String name) {
        Uniform uniform = uniforms.get(name);
        if (uniform == null) {
            uniform = new Uniform(GL20.glGetUniformLocation(id, name));
            uniforms.put(name, uniform);
        }
        return uniform;
    }

    void use() {
        GL20.glUseProgram(id);
        inUse = id;
    }

    private final HashMap<String, Integer> attributes = new HashMap<>();

    /** Where a vertex attribute the program reads is bound. Its shader declares it, so one it does not use is a fault in the mod. */
    int attribute(String name) {
        Integer location = attributes.get(name);
        if (location == null) {
            location = GL20.glGetAttribLocation(id, name);
            if (location < 0) {
                throw new IllegalStateException("the program has no attribute " + name);
            }
            attributes.put(name, location);
        }
        return location;
    }

    void set(String name, int value) {
        Uniform uniform = changed(name, 1, value, 0, 0, 0);
        if (uniform != null) {
            GL20.glUniform1i(uniform.location, value);
        }
    }

    void set(String name, float x) {
        Uniform uniform = changed(name, 1, bits(x), 0, 0, 0);
        if (uniform != null) {
            GL20.glUniform1f(uniform.location, x);
        }
    }

    void set(String name, float x, float y) {
        Uniform uniform = changed(name, 2, bits(x), bits(y), 0, 0);
        if (uniform != null) {
            GL20.glUniform2f(uniform.location, x, y);
        }
    }

    void set(String name, float x, float y, float z) {
        Uniform uniform = changed(name, 3, bits(x), bits(y), bits(z), 0);
        if (uniform != null) {
            GL20.glUniform3f(uniform.location, x, y, z);
        }
    }

    void set(String name, float x, float y, float z, float w) {
        Uniform uniform = changed(name, 4, bits(x), bits(y), bits(z), bits(w));
        if (uniform != null) {
            GL20.glUniform4f(uniform.location, x, y, z, w);
        }
    }

    void set(String name, float[] matrix) {
        Uniform uniform = changed(name, matrix);
        if (uniform != null) {
            GL20.glUniformMatrix4fv(uniform.location, false, matrix);
        }
    }

    /** A mat3, nine floats by columns. */
    void set3x3(String name, float[] matrix) {
        Uniform uniform = changed(name, matrix);
        if (uniform != null) {
            GL20.glUniformMatrix3fv(uniform.location, false, matrix);
        }
    }

    /** Sets an array of floats, every element of it. */
    void setEach(String name, float[] values) {
        Uniform uniform = changed(name, values);
        if (uniform != null) {
            GL20.glUniform1fv(uniform.location, values);
        }
    }

    /** A uniform array of vec4, four floats each. */
    void setEach4(String name, float[] values) {
        Uniform uniform = changed(name, values);
        if (uniform != null) {
            GL20.glUniform4fv(uniform.location, values);
        }
    }

    /** A uniform array of vec3, three floats each. */
    void setEach3(String name, float[] values) {
        Uniform uniform = changed(name, values);
        if (uniform != null) {
            GL20.glUniform3fv(uniform.location, values);
        }
    }

    /**
     * The program that use made current last. Programs keep their uniforms while others are in
     * use, so a value sent once stands until it is sent again, and set sends only what differs from
     * it. That holds while every value is sent with its own program current: set to a program that
     * is not would land in the one that is.
     */
    private static int inUse;

    private static int bits(float x) {
        return Float.floatToRawIntBits(x);
    }

    /** The uniform to send these values to, or null where the program has no such uniform or holds them already. */
    private Uniform changed(String name, int count, int a, int b, int c, int d) {
        Uniform uniform = sendable(name);
        if (uniform == null) {
            return null;
        }
        int[] held = uniform.held;
        if (held != null && held.length == count && held[0] == a && (count < 2 || held[1] == b) && (count < 3 || held[2] == c)
                && (count < 4 || held[3] == d)) {
            return null;
        }
        if (!uniform.raw) {
            if (held == null || held.length != count) {
                held = new int[count];
                uniform.held = held;
            }
            held[0] = a;
            if (count > 1) {
                held[1] = b;
            }
            if (count > 2) {
                held[2] = c;
            }
            if (count > 3) {
                held[3] = d;
            }
        }
        return uniform;
    }

    private Uniform changed(String name, float[] values) {
        Uniform uniform = sendable(name);
        if (uniform == null) {
            return null;
        }
        int[] held = uniform.held;
        if (held != null && held.length == values.length) {
            int i = 0;
            while (i < values.length && held[i] == bits(values[i])) {
                i++;
            }
            if (i == values.length) {
                return null;
            }
        }
        if (!uniform.raw) {
            if (held == null || held.length != values.length) {
                held = new int[values.length];
                uniform.held = held;
            }
            for (int i = 0; i < values.length; i++) {
                held[i] = bits(values[i]);
            }
        }
        return uniform;
    }

    private Uniform sendable(String name) {
        Uniform uniform = uniform(name);
        if (uniform.location < 0) {
            return null;
        }
        if (inUse != id) {
            throw new IllegalStateException("a uniform " + name + " was set to a program that is not the one in use");
        }
        return uniform;
    }

    /** Where a uniform lives and the values it was last sent, as raw bits: null until it is sent, and always for one written outside this class. */
    private static final class Uniform {
        final int location;
        boolean raw;
        int[] held;

        Uniform(int location) {
            this.location = location;
        }
    }
}
