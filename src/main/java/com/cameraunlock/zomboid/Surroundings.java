package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * A picture of the world all round the eye, a cube map SIDE texels a face, for what a wet or
 * shiny surface mirrors where the develop pass's walk through the eye's own picture finds
 * nothing: the ray left the screen, or went behind something nearer. Pipeline draws the world
 * into it a face at a time (SurroundingsSchedule) as cheaply as the vehicle's mirror draws it:
 * no people or vehicles, no surface depth, no reflections of its own, no far land, and no
 * further than SIGHT, with the fog brought in to end there, and the sky behind. Each texel's
 * alpha is how far off what it shows is, which the develop pass reads to look into it from
 * the mirror's own place and not from where it was taken.
 *
 * Only while something on the screen mirrors (askShine): with nothing that does, no face is
 * drawn, and after Pipeline.LENT_FOR_NANOS the pictures are given back.
 *
 * Render thread only.
 */
final class Surroundings {
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE_CUBE_MAP = 0x8513, GL_TEXTURE_CUBE_MAP_POSITIVE_X = 0x8515;
    private static final int GL_TEXTURE_CUBE_MAP_SEAMLESS = 0x884F;
    private static final int GL_RGBA16F = 0x881A, GL_RGBA = 0x1908, GL_FLOAT = 0x1406, GL_DEPTH_COMPONENT24 = 0x81A6, GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803,
            GL_TEXTURE_WRAP_R = 0x8072;
    private static final int GL_LINEAR = 0x2601, GL_NEAREST = 0x2600, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_TEXTURE0 = 0x84C0, GL_DEPTH_TEST = 0x0B71, GL_BLEND = 0x0BE2, GL_TRIANGLE_STRIP = 0x0005;

    /** How many texels a face is across. */
    static final int SIDE = 128;
    /** How far the picture shows, and how near, in tiles. */
    static final float SIGHT = 40f, NEAR = 0.05f;
    /** The distance written for the sky: so far that a ray looked into it from anywhere near the eye goes the way it was thrown. */
    static final float SKY = 1000f;
    /** What the two pictures and the depth they are drawn against take on the graphics card. */
    static final long BYTES = 2L * SurroundingsSchedule.FACES * SIDE * SIDE * 8 + (long) SIDE * SIDE * 4;

    /** The develop pass's look into the picture: see surroundingsSeen. Joined into PipelineShaders.DEVELOP. */
    static final String GLSL = """
            uniform samplerCube uSurroundings;
            // Where the picture of the surroundings was taken, counted from the eye in the
            // world's axes, and in w how much of it the reflections take: 0 for none.
            uniform vec4 uSurroundingsAt;

            vec4 surroundingsTexel(vec3 way) {
                return textureCube(uSurroundings, vec3(way.x, way.y, -way.z));
            }

            // What the picture of the surroundings shows of a ray thrown from the mirror at
            // from (counted from the eye) the way way, both in the world's axes. The picture was
            // taken from somewhere else, so the ray is not looked up the way it goes but towards
            // where it meets what the picture shows: each texel's alpha is how far off that is,
            // and twice over the ray is taken out to a ball of that size round where the picture
            // was taken and looked up where it comes out. Looked up the way it goes, it would show
            // what is that way from where the picture was taken, and a puddle at the feet would
            // mirror the street as seen from the eye's height and not from the ground.
            vec3 surroundingsSeen(vec3 from, vec3 way) {
                vec3 s = from - uSurroundingsAt.xyz;
                float b = dot(s, way), c = dot(s, s);
                vec4 seen = surroundingsTexel(way);
                for (int i = 0; i < 2; i++) {
                    float t = max(-b + sqrt(max(b * b - c + seen.a * seen.a, 0.0)), 0.0);
                    seen = surroundingsTexel(s + way * t);
                }
                return seen.rgb;
            }
            """;

    /** Writes how far off each texel's thing is into the alpha of a face just drawn, from the face's depth. */
    static final SmallProgram distance = new SmallProgram("surroundings' distances", "reflections show nothing off the screen",
            SmallProgram.FLAT_VERTEX, """
            #version 120
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform float uNear;
            uniform float uFar;
            uniform float uSky;
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float depth = texture2D(uDepth, uv).r;
                if (depth >= 1.0) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, uSky);
                    return;
                }
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - (depth * 2.0 - 1.0) * (uFar - uNear));
                gl_FragColor = vec4(0.0, 0.0, 0.0, ahead * length(vec3(uv * 2.0 - 1.0, 1.0)));
            }
            """);

    /**
     * Passes where any of sixteen places of the picture under a pixel mirrors, as the develop
     * pass's walk counts it (alpha under 0.98), and nowhere else: drawn under a query, it says
     * whether anything on the screen mirrors.
     */
    static final SmallProgram shine = new SmallProgram("check for shine", "reflections show nothing off the screen", SmallProgram.FLAT_VERTEX, """
            #version 120
            uniform sampler2D uScene;
            uniform vec2 uSize;
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float least = 1.0;
                for (int i = 0; i < 16; i++) {
                    vec2 at = vec2(mod(float(i), 4.0), floor(float(i) / 4.0)) * 0.25 - 0.375;
                    least = min(least, texture2D(uScene, uv + at / uSize).a);
                }
                if (least >= 0.98) {
                    discard;
                }
                gl_FragColor = vec4(0.0);
            }
            """);

    final SurroundingsSchedule schedule = new SurroundingsSchedule();
    private final SightQuery shineQuery = new SightQuery();
    private final int[] cubes = new int[2];
    /** The target each face of each picture is drawn into, against depth. */
    private final int[][] targets = new int[2][SurroundingsSchedule.FACES];
    /** The depth every face is drawn against, and the target the distances of the face just drawn are written through. */
    private int depth, distanceTarget;

    /**
     * Whether a view's mode has reflections take what the screen does not show from here: where
     * surfaces mirror, at High's reflection resolution or finer, with SurroundingsInReflections on.
     */
    static boolean wanted(int view) {
        return Part.SURROUNDINGS_IN_REFLECTIONS.on() && Quality.surfacesMirror(view) && Quality.reflectionScale(view) >= Quality.HIGH_PICTURE.reflectionScale();
    }

    /**
     * Makes the two pictures, their depth and their targets, noting each texture and target in
     * the lists given. Null, or what the driver refused.
     */
    String make(List<Integer> textures, List<Integer> made) {
        for (int picture = 0; picture < 2; picture++) {
            cubes[picture] = GL11.glGenTextures();
            textures.add(cubes[picture]);
            GL11.glBindTexture(GL_TEXTURE_CUBE_MAP, cubes[picture]);
            for (int face = 0; face < SurroundingsSchedule.FACES; face++) {
                GL11.glTexImage2D(GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, 0, GL_RGBA16F, SIDE, SIDE, 0, GL_RGBA, GL_FLOAT, (ByteBuffer) null);
            }
            GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_CUBE_MAP, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
        }
        GL11.glBindTexture(GL_TEXTURE_CUBE_MAP, 0);
        depth = GL11.glGenTextures();
        textures.add(depth);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SIDE, SIDE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        String problem = null;
        for (int picture = 0; picture < 2; picture++) {
            for (int face = 0; face < SurroundingsSchedule.FACES; face++) {
                int target = GL30.glGenFramebuffers();
                made.add(target);
                targets[picture][face] = target;
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
                GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, cubes[picture], 0);
                GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depth, 0);
                if (problem == null) {
                    problem = complete("surroundings' face");
                }
            }
        }
        // Its colour is the face whose distances are written, attached as each is.
        distanceTarget = GL30.glGenFramebuffers();
        made.add(distanceTarget);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, distanceTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X, cubes[0], 0);
        if (problem == null) {
            problem = complete("surroundings' distances");
        }
        return problem;
    }

    private static String complete(String what) {
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        return status == GL_FRAMEBUFFER_COMPLETE ? null : "the driver would not make the " + what + " target (status 0x" + Integer.toHexString(status) + ")";
    }

    /** The target the face the schedule last said to draw is drawn into. */
    int target() {
        return targets[schedule.into][schedule.face];
    }

    /** The depth that face is drawn against, as a texture, for the sky. */
    int depth() {
        return depth;
    }

    /** The view from where the schedule last said, the way its face looks, into pose. */
    void face(ViewCamera.Pose pose) {
        float[] b = SurroundingsSchedule.BASES[schedule.face];
        pose.eyeAt(schedule.atX, schedule.atY, schedule.atUp);
        pose.rightX = b[0];
        pose.rightY = b[1];
        pose.rightUp = b[2];
        pose.upX = b[3];
        pose.upY = b[4];
        pose.upUp = b[5];
        pose.forwardX = b[6];
        pose.forwardY = b[7];
        pose.forwardUp = b[8];
    }

    /** Whether the faces can be drawn: false once the driver has refused the program that writes their distances. */
    boolean drawable() {
        return distance.use();
    }

    /** Writes how far off each texel's thing is into the alpha of the face just drawn, world and sky. */
    void distances(float near, float far) {
        distance.use();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, distanceTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_CUBE_MAP_POSITIVE_X + schedule.face, cubes[schedule.into], 0);
        GL11.glViewport(0, 0, SIDE, SIDE);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(false);
        GL11.glColorMask(false, false, false, true);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL20.glUniform1i(distance.at("uDepth"), 0);
        GL20.glUniform2f(distance.at("uSize"), SIDE, SIDE);
        GL20.glUniform1f(distance.at("uNear"), near);
        GL20.glUniform1f(distance.at("uFar"), far);
        GL20.glUniform1f(distance.at("uSky"), SKY);
        flat();
        GL11.glColorMask(true, true, true, true);
        GL11.glDepthMask(true);
    }

    /**
     * Asks whether anything in the picture mirrors, read from scene, drawn through target (which
     * must not hold scene) at its size of wide by high. Taken up by shineSeen in a frame to come.
     */
    void askShine(int scene, int target, int wide, int high) {
        if (!shine.use()) {
            return;
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, wide, high);
        if (!shineQuery.begin()) {
            return;
        }
        GL11.glDisable(GL_DEPTH_TEST);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, scene);
        GL20.glUniform1i(shine.at("uScene"), 0);
        GL20.glUniform2f(shine.at("uSize"), wide, high);
        flat();
        shineQuery.end();
    }

    /** Whether anything in the picture has mirrored in the last few frames, as far as the answers that have come in say. */
    boolean shineSeen(int frame) {
        return shineQuery.mayShow(true, false, frame);
    }

    /** Has the develop pass read the shown picture on unit, weighted by weight, where it was taken counted from the eye (x, y, up). */
    void tellDevelop(Program develop, int unit, boolean shown, float weight, float x, float y, float up) {
        GL13.glActiveTexture(GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL_TEXTURE_CUBE_MAP, shown ? cubes[schedule.showing()] : 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        develop.set("uSurroundings", unit);
        develop.set("uSurroundingsAt", x, y, up, shown ? weight : 0f);
    }

    /** Lets a cube map's faces meet without a seam where the develop pass reads it; off again once it has. */
    static void seamless(boolean on) {
        if (on) {
            GL11.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
        } else {
            GL11.glDisable(GL_TEXTURE_CUBE_MAP_SEAMLESS);
        }
    }

    private static void flat() {
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(-1f, -1f);
        GL11.glVertex2f(1f, -1f);
        GL11.glVertex2f(-1f, 1f);
        GL11.glVertex2f(1f, 1f);
        GL11.glEnd();
    }
}
