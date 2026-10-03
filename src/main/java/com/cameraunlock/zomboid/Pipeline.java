package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * How one frame of the first-person picture is drawn.
 *
 * The world's triangles are drawn twice from the sun's side into two shadow
 * maps, a near one and a far one, then once from the eye into a picture that
 * holds light as it is rather than as a screen shows it: each pixel is the
 * sprite's own colour under the game's light for its square, the sun where
 * the shadow maps say the sun reaches, and the sky's light from above, fading
 * into the sky with distance. The sky is drawn into the pixels the world left
 * empty. That picture is then turned into what a camera would have recorded:
 * shafts of light past whatever stands before the sun, a glow around what is
 * brighter than the screen can show, an exposure, a film curve, and smoothed
 * edges, written over the game's own picture of the world.
 *
 * Nothing here runs unless the first-person view is on, so the game's own
 * view is drawn by the game alone. Render thread only. The game's OpenGL state
 * is saved before the frame and put back after it.
 */
final class Pipeline {
    private static final int SHADOW_SIZE = 4096;
    /** Half the width of the ground each shadow map covers, in tiles, and how far along the light it reaches. */
    private static final float NEAR_EXTENT = 24f, FAR_EXTENT = 96f;
    private static final float NEAR_RANGE = 120f, FAR_RANGE = 220f;
    private static final int BLOOM_LEVELS = 5;
    private static final int NOISE_SIZE = 64;
    /** Floats a vertex takes in the buffer handed to OpenGL: what SpriteGeometry writes. */
    private static final int STRIDE = SpriteGeometry.VERTEX_FLOATS * Float.BYTES;

    private static final int GL_TRIANGLES = 0x0004;
    private static final int GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_FLOAT = 0x1406;
    private static final int GL_UNSIGNED_BYTE = 0x1401;
    private static final int GL_RGB = 0x1907;
    private static final int GL_RGBA = 0x1908;
    private static final int GL_RGB8 = 0x8051;
    private static final int GL_RGBA8 = 0x8058;
    private static final int GL_RGBA16F = 0x881A;
    private static final int GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6;
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_3D = 0x806F;
    private static final int GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE_MAG_FILTER = 0x2800;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801;
    private static final int GL_TEXTURE_WRAP_S = 0x2802;
    private static final int GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_TEXTURE_WRAP_R = 0x8072;
    private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
    private static final int GL_TEXTURE_COMPARE_FUNC = 0x884D;
    private static final int GL_COMPARE_REF_TO_TEXTURE = 0x884E;
    private static final int GL_NEAREST = 0x2600;
    private static final int GL_LINEAR = 0x2601;
    private static final int GL_REPEAT = 0x2901;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_LEQUAL = 0x0203;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_NONE = 0;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_ALPHA_TEST = 0x0BC0;
    private static final int GL_SCISSOR_TEST = 0x0C11;
    private static final int GL_STENCIL_TEST = 0x0B90;
    private static final int GL_LIGHTING = 0x0B50;
    private static final int GL_FOG = 0x0B60;
    private static final int GL_POLYGON_OFFSET_FILL = 0x8037;
    private static final int GL_DEPTH_BUFFER_BIT = 0x0100;
    private static final int GL_COLOR_BUFFER_BIT = 0x4000;
    private static final int GL_MODELVIEW = 0x1700;
    private static final int GL_PROJECTION = 0x1701;
    private static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;
    private static final int GL_CLIENT_ALL_ATTRIB_BITS = 0xFFFFFFFF;
    private static final int GL_VERTEX_ARRAY = 0x8074;
    private static final int GL_NORMAL_ARRAY = 0x8075;
    private static final int GL_COLOR_ARRAY = 0x8076;
    private static final int GL_TEXTURE_COORD_ARRAY = 0x8078;
    /** Index, edge flag, fog coordinate and secondary colour: the arrays this frame does not use. */
    private static final int[] OTHER_ARRAYS = {0x8077, 0x8079, 0x8457, 0x845E};
    /** Every OpenGL has at least this many generic vertex attributes and old-style texture coordinate sets. */
    private static final int VERTEX_ATTRIBUTES = 16;
    private static final int TEXTURE_COORD_UNITS = 8;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_SHADER = 0x8B31;
    private static final int GL_FRAGMENT_SHADER = 0x8B30;
    private static final int GL_COMPILE_STATUS = 0x8B81;
    private static final int GL_LINK_STATUS = 0x8B82;
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    private static final int GL_READ_FRAMEBUFFER_BINDING = 0x8CAA;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_ARRAY_BUFFER = 0x8892;
    private static final int GL_ARRAY_BUFFER_BINDING = 0x8894;
    private static final int GL_PIXEL_UNPACK_BUFFER = 0x88EC;
    private static final int GL_PIXEL_UNPACK_BUFFER_BINDING = 0x88EF;
    private static final int GL_SAMPLER_BINDING = 0x8919;
    private static final int GL_UNPACK_ALIGNMENT = 0x0CF5;
    private static final int GL_UNPACK_ROW_LENGTH = 0x0CF2;
    private static final int GL_UNPACK_SKIP_ROWS = 0x0CF3;
    private static final int GL_UNPACK_SKIP_PIXELS = 0x0CF4;
    private static final int GL_ERROR_FLAGS = 8;
    /** The texture units the frame uses, each of which the game may have left a sampler object on. */
    private static final int UNITS = 6;

    private static final String WORLD_VERTEX = """
            #version 120
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            uniform vec3 uWind;

            // Where the wind has moved a vertex to. The normal's length says how much of the
            // wind the vertex takes: nothing at a plant's root, all of it at its top.
            vec4 blown(vec4 at, vec3 normal) {
                float take = length(normal) - 1.0;
                if (take <= 0.001) {
                    return at;
                }
                float beat = dot(at.xy, vec2(0.37, 0.29)) + uWind.z;
                float lean = 0.05 + 0.045 * sin(beat * 1.7) + 0.02 * sin(beat * 4.3 + 1.3);
                return vec4(at.xy + uWind.xy * (take * lean), at.zw);
            }
            void main() {
                vUv = gl_MultiTexCoord0.xy;
                vColor = gl_Color;
                vNormal = gl_Normal;
                vec4 at = blown(gl_Vertex, gl_Normal);
                vWorld = at.xyz;
                gl_Position = gl_ModelViewProjectionMatrix * at;
            }
            """;

    private static final String WORLD_FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + """
            uniform sampler2D uTexture;
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform float uShadowTexel;
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uCut;
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;

            float shadowAt(sampler2DShadow map, vec3 c) {
                float lit = 0.0;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        lit += shadow2D(map, c + vec3(vec2(float(x), float(y)) * (1.3 * uShadowTexel), 0.0)).r;
                    }
                }
                return lit / 9.0;
            }

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            void main() {
                vec4 texel = texture2D(uTexture, vUv);
                if (texel.a < uCut) {
                    discard;
                }
                vec3 toEye = uEye - vWorld;
                float range = length(toEye);
                vec3 view = toEye / range;
                // A sprite is one sheet with no back: the side being looked at is the lit one.
                vec3 n = normalize(vNormal);
                if (dot(n, view) < 0.0) {
                    n = -n;
                }

                // A card stands in for something with depth and leaves, a bush or a tree, which
                // the sun gets into from any side: it is lit whichever way the card faces.
                bool card = vColor.a > 1.5;
                float open = card ? vColor.a - 2.0 : vColor.a;
                float facing = clamp(dot(n, uLight), 0.0, 1.0);
                if (card) {
                    facing = 0.5 + 0.4 * facing;
                }
                float sun = 0.0;
                if (facing > 0.0) {
                    vec4 from = vec4(vWorld + n * 0.07, 1.0);
                    vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                    if (inside(c)) {
                        sun = shadowAt(uShadowNear, c);
                    } else {
                        c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                        sun = inside(c) ? shadowAt(uShadowFar, c) : 1.0;
                    }
                    sun *= facing;
                }

                // The game's light for the square is the light that is there without the sun
                // falling straight on it. Out of doors that light comes from the sky, more of it
                // onto what faces up and bluer than the sun.
                vec3 fromSky = mix(vec3(1.0), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), open);
                vec3 lightHere = pow(vColor.rgb, vec3(2.2)) * fromSky + pow(uLightColor, vec3(2.2)) * (2.1 * sun);
                vec3 color = pow(texel.rgb, vec3(2.2)) * lightHere;

                float haze = max(smoothstep(uFogStart, uFogEnd, range), 1.0 - exp(-range * 0.0035));
                color = mix(color, atmosphere(-view), haze);
                gl_FragColor = vec4(color, texel.a);
            }
            """;

    private static final String SHADOW_VERTEX = """
            #version 120
            uniform mat4 uMatrix;
            varying vec2 vUv;
            uniform vec3 uWind;

            // Where the wind has moved a vertex to. The normal's length says how much of the
            // wind the vertex takes: nothing at a plant's root, all of it at its top.
            vec4 blown(vec4 at, vec3 normal) {
                float take = length(normal) - 1.0;
                if (take <= 0.001) {
                    return at;
                }
                float beat = dot(at.xy, vec2(0.37, 0.29)) + uWind.z;
                float lean = 0.05 + 0.045 * sin(beat * 1.7) + 0.02 * sin(beat * 4.3 + 1.3);
                return vec4(at.xy + uWind.xy * (take * lean), at.zw);
            }
            void main() {
                vUv = gl_MultiTexCoord0.xy;
                gl_Position = uMatrix * blown(gl_Vertex, gl_Normal);
            }
            """;

    private static final String SHADOW_FRAGMENT = """
            #version 120
            uniform sampler2D uTexture;
            varying vec2 vUv;
            void main() {
                if (texture2D(uTexture, vUv).a < 0.5) {
                    discard;
                }
                gl_FragColor = vec4(1.0);
            }
            """;

    /** A quad over the whole picture at the far plane: with the depth test on, only pixels nothing was drawn to pass. */
    private static final String SCREEN_VERTEX = """
            #version 120
            void main() {
                gl_Position = vec4(gl_Vertex.xy, 1.0, 1.0);
            }
            """;

    private static final String BLOOM_DOWN = """
            #version 120
            uniform sampler2D uSource;
            uniform vec2 uSize;
            uniform vec2 uSourceTexel;
            uniform float uThreshold;
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec3 c = texture2D(uSource, uv).rgb * 4.0;
                c += texture2D(uSource, uv + uSourceTexel * vec2(-1.0, -1.0)).rgb;
                c += texture2D(uSource, uv + uSourceTexel * vec2(1.0, -1.0)).rgb;
                c += texture2D(uSource, uv + uSourceTexel * vec2(-1.0, 1.0)).rgb;
                c += texture2D(uSource, uv + uSourceTexel * vec2(1.0, 1.0)).rgb;
                c /= 8.0;
                if (uThreshold > 0.0) {
                    // Only what is brighter than the screen can show glows, and one pixel of
                    // the sun's disc must not outshine the whole picture.
                    c = min(c, vec3(uThreshold * 30.0));
                    c *= smoothstep(uThreshold, uThreshold * 2.5, max(c.r, max(c.g, c.b)));
                }
                gl_FragColor = vec4(c, 1.0);
            }
            """;

    private static final String BLOOM_UP = """
            #version 120
            uniform sampler2D uLow;
            uniform sampler2D uHigh;
            uniform vec2 uSize;
            uniform vec2 uLowTexel;
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec3 c = texture2D(uLow, uv + uLowTexel * vec2(-1.0, -1.0)).rgb * 2.0;
                c += texture2D(uLow, uv + uLowTexel * vec2(1.0, -1.0)).rgb * 2.0;
                c += texture2D(uLow, uv + uLowTexel * vec2(-1.0, 1.0)).rgb * 2.0;
                c += texture2D(uLow, uv + uLowTexel * vec2(1.0, 1.0)).rgb * 2.0;
                c += texture2D(uLow, uv + uLowTexel * vec2(-2.0, 0.0)).rgb;
                c += texture2D(uLow, uv + uLowTexel * vec2(2.0, 0.0)).rgb;
                c += texture2D(uLow, uv + uLowTexel * vec2(0.0, -2.0)).rgb;
                c += texture2D(uLow, uv + uLowTexel * vec2(0.0, 2.0)).rgb;
                gl_FragColor = vec4(c / 12.0 + texture2D(uHigh, uv).rgb, 1.0);
            }
            """;

    /**
     * How much of the sky's light reaches each pixel past what stands close to it: the dimness
     * in a corner, under a table, where a wall meets the floor. Worked out from the depth of the
     * pixels round about, at half the picture's size.
     */
    private static final String OCCLUSION = """
            #version 120
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;

            const int SAMPLES = 14;
            const float RADIUS = 0.5;

            // Where the pixel at uv is, seen from the eye: x to the right, y up, z ahead, in tiles.
            vec3 placeAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                return vec3((uv * 2.0 - 1.0) * uTan * ahead, ahead);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                if (texture2D(uDepth, uv).r >= 0.9999995) {
                    gl_FragColor = vec4(1.0);
                    return;
                }
                vec3 p = placeAt(uv);
                vec3 n = normalize(cross(dFdx(p), dFdy(p)));
                if (n.z > 0.0) {
                    n = -n;
                }
                float turn = 6.2831853 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                float reach = RADIUS / (p.z * uTan.y) * 0.5;
                float blocked = 0.0;
                for (int i = 0; i < SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(SAMPLES);
                    float a = turn + along * 15.1;
                    vec2 off = vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (along * reach);
                    vec3 v = placeAt(uv + off) - p;
                    float far2 = dot(v, v);
                    blocked += max(dot(v, n) - 0.04, 0.0) / (far2 + 0.03) * step(far2, RADIUS * RADIUS * 3.0);
                }
                float open = clamp(1.0 - blocked * (1.5 / float(SAMPLES)), 0.0, 1.0);
                gl_FragColor = vec4(vec3(mix(open, 1.0, smoothstep(35.0, 65.0, p.z))), 1.0);
            }
            """;

    /** Averages the occlusion over the four by four pixels its sampling pattern repeats in, which takes the grain out. */
    private static final String SOFTEN = """
            #version 120
            uniform sampler2D uSource;
            uniform vec2 uSize;
            void main() {
                vec2 texel = 1.0 / uSize;
                vec2 uv = gl_FragCoord.xy * texel;
                vec4 sum = vec4(0.0);
                for (int x = -2; x < 2; x++) {
                    for (int y = -2; y < 2; y++) {
                        sum += texture2D(uSource, uv + (vec2(float(x), float(y)) + 0.5) * texel);
                    }
                }
                gl_FragColor = sum / 16.0;
            }
            """;

    /**
     * The light the air itself sends to the eye: at each of a few steps along the line of sight,
     * whether the sun reaches that point of air, from the shadow maps. Where it does and the
     * air beside it is in shadow, the difference shows as a shaft: through a window into a
     * room, under a tree, past a roof's edge. Drawn at half the picture's size.
     */
    private static final String VOLUME = """
            #version 120
            uniform sampler2D uDepth;
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uEye;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uMist;

            const int STEPS = 24;
            const float REACH = 48.0;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = min(2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear)), REACH);
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                float stride = ahead / float(STEPS);
                float start = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                float lit = 0.0;
                for (int i = 0; i < STEPS; i++) {
                    vec4 p = vec4(uEye + ray * ((float(i) + start) * stride), 1.0);
                    vec3 c = (uNearMatrix * p).xyz * 0.5 + 0.5;
                    float sun = 1.0;
                    if (inside(c)) {
                        sun = shadow2D(uShadowNear, c).r;
                    } else {
                        c = (uFarMatrix * p).xyz * 0.5 + 0.5;
                        if (inside(c)) {
                            sun = shadow2D(uShadowFar, c).r;
                        }
                    }
                    // The air thins with height, and there is none to speak of under the ground.
                    lit += sun * exp(-max(p.z, 0.0) * 0.12);
                }
                float along = length(ray);
                float mu = max(dot(ray / along, uLight), 0.0);
                // Mist sends most of what it catches on the way the light was going.
                float towards = 0.3 + 2.4 * pow(mu, 5.0);
                vec3 caught = pow(uLightColor, vec3(2.2)) * (lit / float(STEPS) * ahead * along * uMist * towards);
                gl_FragColor = vec4(caught, 1.0);
            }
            """;

    /**
     * The game draws its characters with its own shader, in the colours a screen shows, under
     * the game's light for where they stand. They are drawn into a picture of their own first,
     * against the world's depth, and this pass then puts them into the picture that holds
     * light: under the sun where the shadow maps say it reaches them, behind the haze.
     */
    private static final String FIGURE_LIGHT = "#version 120\n" + Sky.ATMOSPHERE + """
            uniform sampler2D uFigure;
            uniform sampler2D uDepth;
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uEye;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uFogStart;
            uniform float uFogEnd;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 figure = texture2D(uFigure, uv);
                if (figure.a < 0.5) {
                    discard;
                }
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                vec3 at = uEye + ray * ahead;

                // Stepped a little towards the light: the character is in the shadow map itself,
                // and its lit side must not be shaded by its own skin.
                vec4 from = vec4(at + uLight * 0.18, 1.0);
                vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                float sun = 1.0;
                if (inside(c)) {
                    sun = shadow2D(uShadowNear, c).r;
                } else {
                    c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                    if (inside(c)) {
                        sun = shadow2D(uShadowFar, c).r;
                    }
                }
                vec3 lightHere = vec3(0.72, 0.78, 0.90) + pow(uLightColor, vec3(2.2)) * (1.5 * sun);
                vec3 color = pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * lightHere;

                float range = ahead * length(ray);
                float haze = max(smoothstep(uFogStart, uFogEnd, range), 1.0 - exp(-range * 0.0035));
                color = mix(color, atmosphere(normalize(ray)), haze);
                gl_FragColor = vec4(color, 1.0);
            }
            """;

    private static final String DEVELOP = """
            #version 120
            uniform sampler2D uScene;
            uniform sampler2D uBloom;
            uniform sampler2D uDepth;
            uniform sampler2D uOcclusion;
            uniform sampler2D uVolume;
            uniform vec2 uSize;
            uniform float uExposure;
            uniform vec2 uSunAt;
            uniform vec3 uRayColor;
            uniform float uAspect;
            uniform float uTime;

            const int RAY_SAMPLES = 48;
            const float WHITE = 3.2;

            float hash(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            // What is left of the sun's glow along the line from this pixel to the sun: a pixel
            // the world covers blocks it, one still at the far plane lets it through.
            vec3 shafts(vec2 uv) {
                vec2 toSun = (uSunAt - uv) * vec2(uAspect, 1.0);
                float reach = exp(-dot(toSun, toSun) * 2.2);
                if (reach < 0.004) {
                    return vec3(0.0);
                }
                vec2 stride = (uSunAt - uv) / float(RAY_SAMPLES);
                vec2 p = uv + stride * hash(gl_FragCoord.xy);
                float sum = 0.0;
                float weight = 1.0;
                for (int i = 0; i < RAY_SAMPLES; i++) {
                    vec2 off = (p - uSunAt) * vec2(uAspect, 1.0);
                    sum += step(0.9999995, texture2D(uDepth, p).r) * exp(-dot(off, off) * 9.0) * weight;
                    weight *= 0.975;
                    p += stride;
                }
                return uRayColor * (sum / float(RAY_SAMPLES) * reach);
            }

            // What a lens does with the sun in front of it: faint copies of it strung along the
            // line through the middle of the picture. Only while the sun itself is not behind
            // anything.
            vec3 flare(vec2 uv) {
                if (uRayColor.r + uRayColor.g + uRayColor.b <= 0.0 || uSunAt.x < 0.0 || uSunAt.x > 1.0 || uSunAt.y < 0.0 || uSunAt.y > 1.0) {
                    return vec3(0.0);
                }
                float seen = 0.0;
                for (int i = 0; i < 5; i++) {
                    vec2 tap = uSunAt + vec2(float(i - 2) * 0.004, float(i == 1 || i == 3 ? 1 : 0) * 0.004);
                    seen += step(0.9999995, texture2D(uDepth, tap).r);
                }
                if (seen == 0.0) {
                    return vec3(0.0);
                }
                vec2 axis = vec2(0.5) - uSunAt;
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 4; i++) {
                    float along = 0.6 + float(i) * 0.55;
                    vec2 off = (uv - (uSunAt + axis * along)) * vec2(uAspect, 1.0);
                    float size = 0.035 + 0.03 * float(i);
                    float disc = smoothstep(size, size * 0.6, length(off));
                    sum += disc * vec3(0.9 - 0.2 * float(i), 0.7, 0.5 + 0.15 * float(i));
                }
                return sum * uRayColor * (seen / 5.0) * 0.09;
            }

            // The way film answers light: what is dim comes out as it went in, and the
            // brightest light rolls off towards white instead of being cut there. It works on
            // the brightness alone, so a sprite keeps the colours it was painted in.
            vec3 film(vec3 x) {
                float light = dot(x, vec3(0.2126, 0.7152, 0.0722));
                float shown = light * (1.0 + light / (WHITE * WHITE)) / (1.0 + light);
                vec3 c = x * (shown / max(light, 1e-5));
                // What is brighter than white in one colour alone spills into the others.
                float over = max(max(c.r, max(c.g, c.b)) - 1.0, 0.0);
                return clamp(c + over * 0.6, 0.0, 1.0);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec3 c = texture2D(uScene, uv).rgb * mix(1.0, texture2D(uOcclusion, uv).r, 0.62);
                if (uRayColor.r + uRayColor.g + uRayColor.b > 0.0) {
                    c += shafts(uv);
                }
                c += texture2D(uVolume, uv).rgb;
                c += flare(uv);
                c += texture2D(uBloom, uv).rgb * 0.045;
                c = film(c * uExposure);
                c = pow(c, vec3(1.0 / 2.2));
                c = mix(c, c * c * (3.0 - 2.0 * c), 0.18);
                c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, 1.12);
                vec2 fromCentre = uv - 0.5;
                c *= 1.0 - dot(fromCentre, fromCentre) * 0.32;
                c += (hash(gl_FragCoord.xy + uTime * 61.0) - 0.5) / 160.0;
                gl_FragColor = vec4(c, 1.0);
            }
            """;

    /** Smooths the stair-steps of edges, looking along each edge for how far to blend. */
    private static final String SMOOTH = """
            #version 120
            uniform sampler2D uImage;
            uniform vec2 uSize;
            uniform vec2 uOrigin;

            float luma(vec3 c) {
                return dot(c, vec3(0.299, 0.587, 0.114));
            }

            void main() {
                vec2 px = 1.0 / uSize;
                vec2 uv = (gl_FragCoord.xy - uOrigin) * px;
                vec3 middle = texture2D(uImage, uv).rgb;
                float nw = luma(texture2D(uImage, uv + vec2(-1.0, -1.0) * px).rgb);
                float ne = luma(texture2D(uImage, uv + vec2(1.0, -1.0) * px).rgb);
                float sw = luma(texture2D(uImage, uv + vec2(-1.0, 1.0) * px).rgb);
                float se = luma(texture2D(uImage, uv + vec2(1.0, 1.0) * px).rgb);
                float m = luma(middle);
                float lowest = min(m, min(min(nw, ne), min(sw, se)));
                float highest = max(m, max(max(nw, ne), max(sw, se)));
                vec2 dir = vec2(-((nw + ne) - (sw + se)), (nw + sw) - (ne + se));
                float reduce = max((nw + ne + sw + se) * 0.03125, 0.0078125);
                dir = clamp(dir / (min(abs(dir.x), abs(dir.y)) + reduce), vec2(-8.0), vec2(8.0)) * px;
                vec3 a = 0.5 * (texture2D(uImage, uv + dir * (1.0 / 3.0 - 0.5)).rgb + texture2D(uImage, uv + dir * (2.0 / 3.0 - 0.5)).rgb);
                vec3 b = a * 0.5 + 0.25 * (texture2D(uImage, uv - dir * 0.5).rgb + texture2D(uImage, uv + dir * 0.5).rgb);
                float lumaB = luma(b);
                vec3 smoothed = lumaB < lowest || lumaB > highest ? a : b;
                // Sprites drawn larger than they were painted come out soft. A little of the
                // difference between each pixel and its surroundings is put back.
                vec3 around = 0.25 * (texture2D(uImage, uv + vec2(px.x, 0.0)).rgb + texture2D(uImage, uv - vec2(px.x, 0.0)).rgb
                        + texture2D(uImage, uv + vec2(0.0, px.y)).rgb + texture2D(uImage, uv - vec2(0.0, px.y)).rgb);
                gl_FragColor = vec4(clamp(smoothed + (smoothed - around) * 0.45, 0.0, 1.0), 1.0);
            }
            """;

    /** One compiled program and the places its uniforms live. */
    private static final class Program {
        final int id;
        private final HashMap<String, Integer> uniforms = new HashMap<>();

        Program(int id) {
            this.id = id;
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

        void set(String name, float[] matrix) {
            GL20.glUniformMatrix4fv(at(name), false, matrix);
        }
    }

    /** Why the picture cannot be drawn on this machine, or null. Set once, on the render thread. */
    private static volatile String failure;
    private static boolean prepared;
    private static Program world, shadow, figureLight, sky, clouds, occlusion, soften, volume, bloomDown, bloomUp, develop, smooth;
    private static int whiteTexture, noiseTexture;
    private static int nearMap, farMap, nearTarget, farTarget;
    private static int width, height;
    private static int sceneTarget, sceneColor, sceneDepth, shownTarget, shownColor, figureTarget, figureColor;
    private static int occlusionTarget, occlusionColor, softTarget, softColor;
    private static int volumeTarget, volumeColor, softVolumeTarget, softVolumeColor;
    private static int spriteSampler;
    private static int cloudTarget, cloudColor, softCloudTarget, softCloudColor;

    private static final boolean TIMES = Boolean.getBoolean("cameraunlock.zomboid.gpuTimes");
    private static final String[] STAGE_NAMES = {"load", "shadows", "world", "clouds", "sky", "occlusion", "air", "glow", "develop", "figures", "smooth"};
    private static final int STAGE_LOAD = 0, STAGE_SHADOWS = 1, STAGE_WORLD = 2, STAGE_CLOUDS = 3, STAGE_SKY = 4, STAGE_OCCLUSION = 5,
            STAGE_AIR = 6, STAGE_GLOW = 7, STAGE_DEVELOP = 8, STAGE_FIGURES = 9, STAGE_SMOOTH = 10;
    private static final long[] stageNanos = new long[STAGE_NAMES.length];
    private static long stageStart;
    private static int stageFrames;
    private static final int[] downTarget = new int[BLOOM_LEVELS], downColor = new int[BLOOM_LEVELS];
    private static final int[] upTarget = new int[BLOOM_LEVELS - 1], upColor = new int[BLOOM_LEVELS - 1];
    private static final int[] levelWidth = new int[BLOOM_LEVELS], levelHeight = new int[BLOOM_LEVELS];

    private static FloatBuffer vertices = buffer(1 << 20);
    private static int[] rangeTexture = new int[256], rangeFirst = new int[256], rangeCount = new int[256];
    private static int ranges, solidRanges;
    private static final float[] nearMatrix = new float[16], farMatrix = new float[16];
    private static final float[] sunNdc = new float[2];
    private static final int[] samplers = new int[UNITS];
    private static volatile float lastMillis;

    private Pipeline() {}

    /** Why first person cannot be drawn here, for the log and to keep the view off, or null. */
    static String failure() {
        return failure;
    }

    static float lastMillis() {
        return lastMillis;
    }

    private static FloatBuffer buffer(int floats) {
        return ByteBuffer.allocateDirect(floats * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    /**
     * @param viewport   the part of the game's picture the frame is written to: x, y, width, height
     * @param exposure   how much light the eye is letting in: lower in the open at noon than in a room
     * @param fogEnd     the distance, in tiles, at which the world has faded into the sky
     */
    static void draw(ArrayList<WorldScene.ChunkMeshes> chunks, ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView,
            float near, float far, float fogStart, float fogEnd, float exposure, int[] viewport) {
        if (failure != null) {
            return;
        }
        long start = System.nanoTime();
        clearErrors();

        int program = GL11.glGetInteger(GL_CURRENT_PROGRAM);
        int drawTarget = GL11.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int readTarget = GL11.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int vertexArray = GL11.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer = GL11.glGetInteger(GL_ARRAY_BUFFER_BINDING);
        int unpackBuffer = GL11.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        GL11.glPushAttrib(GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL_CLIENT_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPushMatrix();
        // The game's own buffers and samplers would change what this frame's pointers and
        // textures mean. They are set aside for the frame.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        for (int unit = 0; unit < UNITS; unit++) {
            GL13.glActiveTexture(GL_TEXTURE0 + unit);
            samplers[unit] = GL11.glGetInteger(GL_SAMPLER_BINDING);
            GL33.glBindSampler(unit, 0);
        }
        GL13.glActiveTexture(GL_TEXTURE0);

        if (!prepared) {
            prepared = true;
            failure = prepare();
        }
        if (failure == null && (viewport[2] != width || viewport[3] != height)) {
            failure = resize(viewport[2], viewport[3]);
        }
        if (failure == null) {
            float aspect = (float) viewport[2] / viewport[3];
            if (TIMES) {
                GL11.glFinish();
                stageStart = System.nanoTime();
            }
            load(chunks);
            stage(STAGE_LOAD);
            boolean figures = Characters.ready();
            drawShadows(state, camera, figures);
            stage(STAGE_SHADOWS);
            drawWorld(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            stage(STAGE_WORLD);
            if (figures) {
                drawFigures(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            stage(STAGE_FIGURES);
            drawSky(camera, state, tanHalfFieldOfView, aspect);
            stage(STAGE_SKY);
            drawOcclusion(tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_OCCLUSION);
            drawVolume(camera, state, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_AIR);
            drawBloom(exposure);
            stage(STAGE_GLOW);
            drawDeveloped(camera, state, tanHalfFieldOfView, aspect, exposure);
            stage(STAGE_DEVELOP);
            GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTarget);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            smooth.use();
            GL11.glBindTexture(GL_TEXTURE_2D, shownColor);
            smooth.set("uImage", 0);
            smooth.set("uSize", (float) width, (float) height);
            smooth.set("uOrigin", (float) viewport[0], (float) viewport[1]);
            quad();
            stage(STAGE_SMOOTH);
            reportStages();
            int error = GL11.glGetError();
            if (error != 0) {
                failure = "OpenGL error 0x" + Integer.toHexString(error) + " drawing the picture";
            }
        }
        if (failure != null) {
            clearErrors();
            Log.info("first person cannot be drawn on this machine: " + failure);
        }

        for (int unit = 0; unit < UNITS; unit++) {
            GL33.glBindSampler(unit, samplers[unit]);
        }
        GL30.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawTarget);
        GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, readTarget);
        GL15.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpackBuffer);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        GL30.glBindVertexArray(vertexArray);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopClientAttrib();
        GL11.glPopAttrib();
        GL20.glUseProgram(program);
        lastMillis = (System.nanoTime() - start) / 1e6f;
    }

    /**
     * Copies every chunk's meshes into the one buffer OpenGL draws from, all the triangles
     * that wear one texture together whichever chunk they came from, so a texture costs one
     * draw however many chunks show it. What is solid comes first, then what is laid over it.
     */
    private static void load(ArrayList<WorldScene.ChunkMeshes> chunks) {
        solidRuns.clear();
        laidRuns.clear();
        for (int i = 0; i < chunks.size(); i++) {
            solidRuns.count(chunks.get(i).solid.all);
            laidRuns.count(chunks.get(i).laid.all);
        }
        int most = solidRuns.used + laidRuns.used;
        if (most > rangeTexture.length) {
            rangeTexture = new int[most * 2];
            rangeFirst = new int[most * 2];
            rangeCount = new int[most * 2];
        }
        ranges = 0;
        int floats = solidRuns.place(0);
        solidRanges = ranges;
        floats = laidRuns.place(floats);
        if (floats > vertices.capacity()) {
            vertices = buffer(Integer.highestOneBit(floats) * 2);
        }
        vertices.clear();
        vertices.limit(floats);
        for (int i = 0; i < chunks.size(); i++) {
            solidRuns.copy(chunks.get(i).solid.all);
            laidRuns.copy(chunks.get(i).laid.all);
        }
        arrays();
    }

    /** The runs of the vertex buffer by texture: how long each is, where it starts, and where the next mesh for it goes. */
    private static final class Runs {
        private int[] texture = new int[512], floats = new int[512], cursor = new int[512];
        private boolean[] taken = new boolean[512];
        int used;

        void clear() {
            java.util.Arrays.fill(taken, false);
            used = 0;
        }

        private int slot(int name) {
            int mask = texture.length - 1;
            int slot = (name * 0x9E3779B1) >>> 16 & mask;
            while (taken[slot] && texture[slot] != name) {
                slot = (slot + 1) & mask;
            }
            return slot;
        }

        void count(ArrayList<SpriteGeometry.Mesh> meshes) {
            for (int i = 0; i < meshes.size(); i++) {
                SpriteGeometry.Mesh mesh = meshes.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                if (used * 2 >= texture.length) {
                    grow();
                }
                int slot = slot(mesh.texture);
                if (!taken[slot]) {
                    taken[slot] = true;
                    texture[slot] = mesh.texture;
                    floats[slot] = 0;
                    used++;
                }
                floats[slot] += mesh.floats;
            }
        }

        private void grow() {
            int[] oldTexture = texture, oldFloats = floats;
            boolean[] oldTaken = taken;
            texture = new int[oldTexture.length * 2];
            floats = new int[oldTexture.length * 2];
            cursor = new int[oldTexture.length * 2];
            taken = new boolean[oldTexture.length * 2];
            for (int i = 0; i < oldTexture.length; i++) {
                if (oldTaken[i]) {
                    int slot = slot(oldTexture[i]);
                    taken[slot] = true;
                    texture[slot] = oldTexture[i];
                    floats[slot] = oldFloats[i];
                }
            }
        }

        /** Gives each texture its run of the buffer from the float at start, notes it as a range to draw, and returns the float after the last. */
        int place(int start) {
            for (int slot = 0; slot < texture.length; slot++) {
                if (!taken[slot]) {
                    continue;
                }
                cursor[slot] = start;
                rangeTexture[ranges] = texture[slot] == 0 ? whiteTexture : texture[slot];
                rangeFirst[ranges] = start / SpriteGeometry.VERTEX_FLOATS;
                rangeCount[ranges] = floats[slot] / SpriteGeometry.VERTEX_FLOATS;
                ranges++;
                start += floats[slot];
            }
            return start;
        }

        void copy(ArrayList<SpriteGeometry.Mesh> meshes) {
            for (int i = 0; i < meshes.size(); i++) {
                SpriteGeometry.Mesh mesh = meshes.get(i);
                if (mesh.floats == 0) {
                    continue;
                }
                int slot = slot(mesh.texture);
                vertices.put(cursor[slot], mesh.vertices, 0, mesh.floats);
                cursor[slot] += mesh.floats;
            }
        }
    }

    private static final Runs solidRuns = new Runs(), laidRuns = new Runs();

    /** Points OpenGL at the frame's vertices. Done again after the game has drawn anything, which leaves its own arrays set. */
    private static void arrays() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        // Any other array the game left switched on would be read for every vertex drawn here,
        // from wherever the game last pointed it and for as many vertices as this frame has.
        for (int attribute = 0; attribute < VERTEX_ATTRIBUTES; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        for (int array : OTHER_ARRAYS) {
            GL11.glDisableClientState(array);
        }
        for (int unit = TEXTURE_COORD_UNITS - 1; unit >= 0; unit--) {
            GL13.glClientActiveTexture(GL_TEXTURE0 + unit);
            GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        }
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glEnableClientState(GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        GL11.glVertexPointer(3, GL_FLOAT, STRIDE, at(0));
        GL11.glTexCoordPointer(2, GL_FLOAT, STRIDE, at(3));
        GL11.glColorPointer(4, GL_FLOAT, STRIDE, at(5));
        GL11.glNormalPointer(GL_FLOAT, STRIDE, at(9));
    }

    private static FloatBuffer at(int offset) {
        FloatBuffer view = vertices.duplicate();
        view.position(Math.min(offset, view.limit()));
        return view;
    }

    private static void drawRanges(int from, int to) {
        for (int i = from; i < to; i++) {
            GL11.glBindTexture(GL_TEXTURE_2D, rangeTexture[i]);
            GL11.glDrawArrays(GL_TRIANGLES, rangeFirst[i], rangeCount[i]);
        }
    }

    private static void drawShadows(SkyState state, ViewCamera.Pose camera, boolean figures) {
        lightMatrix(nearMatrix, state, camera, NEAR_EXTENT, NEAR_RANGE);
        lightMatrix(farMatrix, state, camera, FAR_EXTENT, FAR_RANGE);
        GL11.glDisable(GL_SCISSOR_TEST);
        GL11.glDisable(GL_STENCIL_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glDisable(GL_LIGHTING);
        GL11.glDisable(GL_FOG);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glColorMask(false, false, false, false);
        // A surface would otherwise shade itself where the map's depth is a hair nearer the
        // light than the surface it was drawn from.
        GL11.glEnable(GL_POLYGON_OFFSET_FILL);
        GL11.glPolygonOffset(2.5f, 6f);
        GL11.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        shadow.use();
        shadow.set("uTexture", 0);
        shadow.set("uWind", state.windX, state.windY, state.seconds);
        // The game magnifies its sprites pixel by pixel, which up close is a wall of squares.
        // This frame reads them through a sampler of its own that blends between pixels, and
        // leaves the textures' own settings as the game set them.
        GL33.glBindSampler(0, spriteSampler);
        for (int map = 0; map < 2; map++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.set("uMatrix", map == 0 ? nearMatrix : farMatrix);
            drawRanges(0, solidRanges);
        }
        if (figures) {
            // The game's own draw of each character, with the light's matrix where its camera
            // would be. It sets its own program, buffers and culling, so the frame's are put back.
            for (int map = 0; map < 2; map++) {
                GL30.glBindFramebuffer(GL_FRAMEBUFFER, map == 0 ? nearTarget : farTarget);
                Characters.cast(map == 0 ? nearMatrix : farMatrix, camera);
            }
            arrays();
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glDisable(GL_CULL_FACE);
            GL11.glDisable(GL_BLEND);
            GL11.glDisable(GL_ALPHA_TEST);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
        }
        GL11.glDisable(GL_POLYGON_OFFSET_FILL);
        GL11.glColorMask(true, true, true, true);
    }

    /**
     * The matrix that looks along the light at a square of ground centred on the eye. The
     * centre moves a whole texel of the map at a time, or the shadows' edges would crawl as
     * the eye moves.
     */
    private static void lightMatrix(float[] m, SkyState state, ViewCamera.Pose camera, float extent, float range) {
        float lx = state.lightX, ly = state.lightY, lu = state.lightUp;
        float rx = -ly, ry = lx;
        float flat = (float) Math.sqrt(rx * rx + ry * ry);
        if (flat < 1e-4f) {
            rx = 1f;
            ry = 0f;
        } else {
            rx /= flat;
            ry /= flat;
        }
        float ux = -lu * ry, uy = lu * rx, uu = lx * ry - ly * rx;
        float texel = 2f * extent / SHADOW_SIZE;
        float centreRight = (float) Math.floor((rx * camera.eyeX + ry * camera.eyeY) / texel) * texel;
        float centreUp = (float) Math.floor((ux * camera.eyeX + uy * camera.eyeY + uu * camera.eyeUp) / texel) * texel;
        float centreAlong = lx * camera.eyeX + ly * camera.eyeY + lu * camera.eyeUp;
        m[0] = rx / extent; m[4] = ry / extent; m[8] = 0f; m[12] = -centreRight / extent;
        m[1] = ux / extent; m[5] = uy / extent; m[9] = uu / extent; m[13] = -centreUp / extent;
        m[2] = -lx / range; m[6] = -ly / range; m[10] = -lu / range; m[14] = centreAlong / range;
        m[3] = 0f; m[7] = 0f; m[11] = 0f; m[15] = 1f;
    }

    private static void drawWorld(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0f, 0f, 0f, 1f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far));
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(ViewCamera.view(camera));

        world.use();
        world.set("uTexture", 0);
        world.set("uShadowNear", 1);
        world.set("uShadowFar", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uNearMatrix", nearMatrix);
        world.set("uFarMatrix", farMatrix);
        world.set("uShadowTexel", 1f / SHADOW_SIZE);
        world.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        world.set("uLight", state.lightX, state.lightY, state.lightUp);
        world.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        world.set("uFogStart", fogStart);
        world.set("uFogEnd", fogEnd);
        world.set("uWind", state.windX, state.windY, state.seconds);
        atmosphere(world, state);
        world.set("uCut", 0.5f);
        drawRanges(0, solidRanges);
        // What is laid over the floors, mixed in by how see-through each pixel is. It writes no
        // depth: it lies on a floor that already has.
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        world.set("uCut", 0.02f);
        drawRanges(solidRanges, ranges);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(true);
        GL33.glBindSampler(0, 0);

        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
    }

    private static void atmosphere(Program program, SkyState state) {
        program.set("uSun", state.sunX, state.sunY, state.sunUp);
        program.set("uSunColor", state.sunRed, state.sunGreen, state.sunBlue);
        program.set("uZenith", state.zenithRed, state.zenithGreen, state.zenithBlue);
        program.set("uHorizon", state.horizonRed, state.horizonGreen, state.horizonBlue);
        program.set("uGlow", state.glowRed, state.glowGreen, state.glowBlue);
    }

    private static void drawSky(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect) {
        GL11.glDepthMask(false);
        GL11.glDisable(GL_DEPTH_TEST);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, cloudTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        clouds.use();
        GL11.glBindTexture(GL_TEXTURE_3D, noiseTexture);
        clouds.set("uNoise", 0);
        clouds.set("uRight", camera.rightX, camera.rightY, camera.rightUp);
        clouds.set("uUp", camera.upX, camera.upY, camera.upUp);
        clouds.set("uForward", camera.forwardX, camera.forwardY, camera.forwardUp);
        clouds.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        clouds.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        clouds.set("uLight", state.lightX, state.lightY, state.lightUp);
        clouds.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        clouds.set("uZenith", state.zenithRed, state.zenithGreen, state.zenithBlue);
        clouds.set("uHorizon", state.horizonRed, state.horizonGreen, state.horizonBlue);
        clouds.set("uCover", state.cover);
        clouds.set("uDrift", state.driftX, state.driftY);
        quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, softCloudTarget);
        soften.use();
        GL11.glBindTexture(GL_TEXTURE_2D, cloudColor);
        soften.set("uSource", 0);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
        stage(STAGE_CLOUDS);

        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glEnable(GL_DEPTH_TEST);
        // Said again here: the game's own draw of the characters leaves its depth test behind.
        GL11.glDepthFunc(GL_LEQUAL);
        sky.use();
        GL11.glBindTexture(GL_TEXTURE_2D, softCloudColor);
        sky.set("uClouds", 0);
        sky.set("uRight", camera.rightX, camera.rightY, camera.rightUp);
        sky.set("uUp", camera.upX, camera.upY, camera.upUp);
        sky.set("uForward", camera.forwardX, camera.forwardY, camera.forwardUp);
        sky.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        sky.set("uSize", (float) width, (float) height);
        sky.set("uMoon", state.moonX, state.moonY, state.moonUp);
        sky.set("uNight", state.night);
        sky.set("uTime", state.seconds);
        atmosphere(sky, state);
        quad();
        GL11.glDisable(GL_DEPTH_TEST);
    }

    /**
     * With -Dcameraunlock.zomboid.gpuTimes=true, waits for the graphics card to finish what it
     * has been given and charges the time since the last stage to this one. The wait itself
     * slows the frame, so the figures are for comparing stages, not for the frame rate.
     */
    private static void stage(int stage) {
        if (!TIMES) {
            return;
        }
        GL11.glFinish();
        long now = System.nanoTime();
        stageNanos[stage] += now - stageStart;
        stageStart = now;
    }

    private static void reportStages() {
        if (!TIMES || ++stageFrames < 240) {
            return;
        }
        StringBuilder line = new StringBuilder("first person frame stages, ms:");
        for (int i = 0; i < STAGE_NAMES.length; i++) {
            line.append(String.format(Locale.ROOT, " %s %.2f", STAGE_NAMES[i], stageNanos[i] / 1e6 / stageFrames));
            stageNanos[i] = 0;
        }
        stageFrames = 0;
        Log.info(line.toString());
    }

    private static void drawOcclusion(float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, occlusionTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        occlusion.use();
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        occlusion.set("uDepth", 0);
        occlusion.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        occlusion.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        occlusion.set("uNear", near);
        occlusion.set("uFar", far);
        quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, softTarget);
        soften.use();
        GL11.glBindTexture(GL_TEXTURE_2D, occlusionColor);
        soften.set("uSource", 0);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
    }

    private static void drawVolume(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, volumeTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        volume.use();
        volume.set("uDepth", 0);
        volume.set("uShadowNear", 1);
        volume.set("uShadowFar", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        volume.set("uNearMatrix", nearMatrix);
        volume.set("uFarMatrix", farMatrix);
        volume.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        volume.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        volume.set("uNear", near);
        volume.set("uFar", far);
        volume.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        volume.set("uRight", camera.rightX, camera.rightY, camera.rightUp);
        volume.set("uUp", camera.upX, camera.upY, camera.upUp);
        volume.set("uForward", camera.forwardX, camera.forwardY, camera.forwardUp);
        volume.set("uLight", state.lightX, state.lightY, state.lightUp);
        volume.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        volume.set("uMist", state.mist);
        quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, softVolumeTarget);
        soften.use();
        GL11.glBindTexture(GL_TEXTURE_2D, volumeColor);
        soften.set("uSource", 0);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
    }

    /** Halves the picture five times, keeping only what is too bright to show, then adds the halves back up. */
    private static void drawBloom(float exposure) {
        bloomDown.use();
        bloomDown.set("uSource", 0);
        for (int level = 0; level < BLOOM_LEVELS; level++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, downTarget[level]);
            GL11.glViewport(0, 0, levelWidth[level], levelHeight[level]);
            GL11.glBindTexture(GL_TEXTURE_2D, level == 0 ? sceneColor : downColor[level - 1]);
            int sourceWidth = level == 0 ? width : levelWidth[level - 1];
            int sourceHeight = level == 0 ? height : levelHeight[level - 1];
            bloomDown.set("uSize", (float) levelWidth[level], (float) levelHeight[level]);
            bloomDown.set("uSourceTexel", 1f / sourceWidth, 1f / sourceHeight);
            bloomDown.set("uThreshold", level == 0 ? 1.1f / exposure : 0f);
            quad();
        }
        bloomUp.use();
        bloomUp.set("uLow", 0);
        bloomUp.set("uHigh", 1);
        for (int level = BLOOM_LEVELS - 2; level >= 0; level--) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, upTarget[level]);
            GL11.glViewport(0, 0, levelWidth[level], levelHeight[level]);
            GL13.glActiveTexture(GL_TEXTURE0 + 1);
            GL11.glBindTexture(GL_TEXTURE_2D, downColor[level]);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, level == BLOOM_LEVELS - 2 ? downColor[level + 1] : upColor[level + 1]);
            bloomUp.set("uSize", (float) levelWidth[level], (float) levelHeight[level]);
            bloomUp.set("uLowTexel", 1f / levelWidth[level + 1], 1f / levelHeight[level + 1]);
            quad();
        }
    }

    private static void drawDeveloped(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float exposure) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, shownTarget);
        GL11.glViewport(0, 0, width, height);
        develop.use();
        develop.set("uScene", 0);
        develop.set("uBloom", 1);
        develop.set("uDepth", 2);
        develop.set("uOcclusion", 3);
        develop.set("uVolume", 4);
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, softVolumeColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, softColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, upColor[0]);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        develop.set("uSize", (float) width, (float) height);
        develop.set("uExposure", exposure);
        develop.set("uAspect", aspect);
        develop.set("uTime", state.seconds);
        boolean sunAhead = ViewCamera.project(camera, tanHalfFieldOfView, aspect, camera.eyeX + state.sunX, camera.eyeY + state.sunY,
                camera.eyeUp + state.sunUp, sunNdc);
        if (sunAhead) {
            develop.set("uSunAt", sunNdc[0] * 0.5f + 0.5f, sunNdc[1] * 0.5f + 0.5f);
            develop.set("uRayColor", state.rayRed, state.rayGreen, state.rayBlue);
        } else {
            develop.set("uRayColor", 0f, 0f, 0f);
        }
        quad();
    }

    private static void drawFigures(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect,
            float near, float far, float fogStart, float fogEnd) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClear(GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        Characters.draw(camera, tanHalfFieldOfView, aspect, near, far);
        // The game's draw leaves its own program, buffers and arrays bound.
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glDepthMask(false);

        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        figureLight.use();
        figureLight.set("uFigure", 0);
        figureLight.set("uDepth", 3);
        figureLight.set("uShadowNear", 1);
        figureLight.set("uShadowFar", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, figureColor);
        figureLight.set("uNearMatrix", nearMatrix);
        figureLight.set("uFarMatrix", farMatrix);
        figureLight.set("uSize", (float) width, (float) height);
        figureLight.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        figureLight.set("uNear", near);
        figureLight.set("uFar", far);
        figureLight.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        figureLight.set("uRight", camera.rightX, camera.rightY, camera.rightUp);
        figureLight.set("uUp", camera.upX, camera.upY, camera.upUp);
        figureLight.set("uForward", camera.forwardX, camera.forwardY, camera.forwardUp);
        figureLight.set("uLight", state.lightX, state.lightY, state.lightUp);
        figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        figureLight.set("uFogStart", fogStart);
        figureLight.set("uFogEnd", fogEnd);
        atmosphere(figureLight, state);
        quad();
        GL11.glDepthMask(true);
    }

    private static void quad() {
        GL11.glBegin(GL_TRIANGLE_STRIP);
        GL11.glVertex2f(-1f, -1f);
        GL11.glVertex2f(1f, -1f);
        GL11.glVertex2f(-1f, 1f);
        GL11.glVertex2f(1f, 1f);
        GL11.glEnd();
    }

    /** Builds the programs and the textures that do not depend on the picture's size. Null, or what the driver refused. */
    private static String prepare() {
        StringBuilder problem = new StringBuilder();
        world = program("world", WORLD_VERTEX, WORLD_FRAGMENT, problem);
        shadow = program("shadow", SHADOW_VERTEX, SHADOW_FRAGMENT, problem);
        sky = program("sky", SCREEN_VERTEX, Sky.FRAGMENT, problem);
        clouds = program("clouds", SCREEN_VERTEX, Sky.CLOUDS, problem);
        figureLight = program("character light", SCREEN_VERTEX, FIGURE_LIGHT, problem);
        occlusion = program("occlusion", SCREEN_VERTEX, OCCLUSION, problem);
        soften = program("occlusion soften", SCREEN_VERTEX, SOFTEN, problem);
        volume = program("lit air", SCREEN_VERTEX, VOLUME, problem);
        bloomDown = program("glow down", SCREEN_VERTEX, BLOOM_DOWN, problem);
        bloomUp = program("glow up", SCREEN_VERTEX, BLOOM_UP, problem);
        develop = program("develop", SCREEN_VERTEX, DEVELOP, problem);
        smooth = program("smooth", SCREEN_VERTEX, SMOOTH, problem);
        if (problem.length() > 0) {
            return problem.toString();
        }

        long start = System.nanoTime();
        GL11.glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL_UNPACK_SKIP_PIXELS, 0);
        noiseTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_3D, noiseTexture);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_REPEAT);
        GL12.glTexImage3D(GL_TEXTURE_3D, 0, GL_RGB8, NOISE_SIZE, NOISE_SIZE, NOISE_SIZE, 0, GL_RGB, GL_UNSIGNED_BYTE,
                CloudNoise.build(NOISE_SIZE));

        // What a thing with no sprite is drawn with: the vertex colour alone.
        whiteTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, whiteTexture);
        ByteBuffer white = ByteBuffer.allocateDirect(4);
        white.put((byte) -1).put((byte) -1).put((byte) -1).put((byte) -1).flip();
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, white);
        filter(GL_NEAREST);

        spriteSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(spriteSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL33.glSamplerParameteri(spriteSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL33.glSamplerParameteri(spriteSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(spriteSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);

        int[] made = new int[2];
        String near = shadowMap(made);
        nearMap = made[0];
        nearTarget = made[1];
        String far = shadowMap(made);
        farMap = made[0];
        farTarget = made[1];
        if (near != null || far != null) {
            return near != null ? near : far;
        }
        int error = GL11.glGetError();
        if (error != 0) {
            return "OpenGL error 0x" + Integer.toHexString(error) + " making the textures";
        }
        Log.info(String.format(Locale.ROOT, "first person picture ready: %d shaders, two %d shadow maps, cloud noise %d cubed, in %.0f ms",
                12, SHADOW_SIZE, NOISE_SIZE, (System.nanoTime() - start) / 1e6));
        return null;
    }

    private static String shadowMap(int[] made) {
        int map = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, map);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
        filter(GL_LINEAR);
        // The texture answers "is this point nearer the light than what the map holds" itself,
        // and blends the four answers around a sample, which is what softens the shadow's edge.
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
        int target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, map, 0);
        GL11.glDrawBuffer(GL_NONE);
        GL11.glReadBuffer(GL_NONE);
        made[0] = map;
        made[1] = target;
        return complete("shadow map");
    }

    /** Makes the pictures the frame is drawn through, at the size of the game's own. Null, or what the driver refused. */
    private static String resize(int newWidth, int newHeight) {
        // The old ones are left to the driver: the size changes when the window does, a few
        // times a session, and each set is a few tens of megabytes.
        width = newWidth;
        height = newHeight;
        sceneColor = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
        sceneDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        sceneTarget = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColor, 0);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        String problem = complete("scene");
        if (problem != null) {
            return problem;
        }
        shownColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
        shownTarget = target(shownColor);
        problem = complete("developed picture");
        figureColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        figureTarget = target(figureColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, sceneDepth, 0);
        if (problem == null) {
            problem = complete("character");
        }
        int levelW = width, levelH = height;
        for (int level = 0; level < BLOOM_LEVELS && problem == null; level++) {
            levelW = Math.max(1, levelW / 2);
            levelH = Math.max(1, levelH / 2);
            levelWidth[level] = levelW;
            levelHeight[level] = levelH;
            downColor[level] = texture(GL_RGBA16F, levelW, levelH, GL_RGBA, GL_FLOAT, GL_LINEAR);
            downTarget[level] = target(downColor[level]);
            problem = complete("glow");
            if (level < BLOOM_LEVELS - 1 && problem == null) {
                upColor[level] = texture(GL_RGBA16F, levelW, levelH, GL_RGBA, GL_FLOAT, GL_LINEAR);
                upTarget[level] = target(upColor[level]);
                problem = complete("glow");
            }
        }
        if (problem == null) {
            occlusionColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
            occlusionTarget = target(occlusionColor);
            softColor = texture(GL_RGBA8, levelWidth[0], levelHeight[0], GL_RGBA, GL_UNSIGNED_BYTE, GL_LINEAR);
            softTarget = target(softColor);
            volumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            volumeTarget = target(volumeColor);
            softVolumeColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            softVolumeTarget = target(softVolumeColor);
            cloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            cloudTarget = target(cloudColor);
            softCloudColor = texture(GL_RGBA16F, levelWidth[0], levelHeight[0], GL_RGBA, GL_FLOAT, GL_LINEAR);
            softCloudTarget = target(softCloudColor);
            problem = complete("occlusion");
        }
        if (problem == null) {
            Log.info("first person picture sized " + width + "x" + height);
        }
        return problem;
    }

    private static int texture(int internal, int w, int h, int format, int type, int filter) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, texture);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, format, type, (ByteBuffer) null);
        filter(filter);
        return texture;
    }

    private static void filter(int filter) {
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static int target(int color) {
        int target = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color, 0);
        return target;
    }

    private static String complete(String what) {
        int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
        return status == GL_FRAMEBUFFER_COMPLETE ? null : "the driver would not make the " + what + " target (status 0x" + Integer.toHexString(status) + ")";
    }

    /** Null, with the driver's message added to problem, when a shader does not compile or the program does not link. */
    private static Program program(String name, String vertexSource, String fragmentSource, StringBuilder problem) {
        int vertex = shader(name + " vertex", GL_VERTEX_SHADER, vertexSource, problem);
        int fragment = shader(name + " fragment", GL_FRAGMENT_SHADER, fragmentSource, problem);
        if (vertex == 0 || fragment == 0) {
            return null;
        }
        int id = GL20.glCreateProgram();
        GL20.glAttachShader(id, vertex);
        GL20.glAttachShader(id, fragment);
        GL20.glLinkProgram(id);
        if (GL20.glGetProgrami(id, GL_LINK_STATUS) == 0) {
            problem.append("the ").append(name).append(" shader did not link: ").append(GL20.glGetProgramInfoLog(id)).append(' ');
            return null;
        }
        return new Program(id);
    }

    private static int shader(String name, int kind, String source, StringBuilder problem) {
        int shader = GL20.glCreateShader(kind);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            problem.append("the ").append(name).append(" shader did not compile: ").append(GL20.glGetShaderInfoLog(shader)).append(' ');
            return 0;
        }
        return shader;
    }

    private static void clearErrors() {
        for (int flag = 0; flag < GL_ERROR_FLAGS && GL11.glGetError() != 0; flag++) {
        }
    }
}
