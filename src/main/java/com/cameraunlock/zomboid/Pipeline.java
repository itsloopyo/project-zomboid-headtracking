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
import org.lwjgl.opengl.GL14;
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
    private static final int GL_TEXTURE_WIDTH = 0x1000;
    private static final int GL_TEXTURE_HEIGHT = 0x1001;
    /**
     * How covered a pixel of a solid sprite has to be to be drawn. A shade under a half: the
     * cover is a mean over two texels, and a bar one texel wide reaches a half and no more.
     */
    private static final float SOLID_CUT = 0.48f;
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
    private static final int GL_LINEAR_MIPMAP_LINEAR = 0x2703;
    private static final int GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_LEQUAL = 0x0203;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_NONE = 0;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_CULL_FACE = 0x0B44;
    private static final int GL_ZERO = 0;
    private static final int GL_ALWAYS = 0x0207;
    private static final int GL_ONE = 1;
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
    private static final int UNITS = 24;
    /** The texture unit the headlights' shadow map stays bound to through the frame. */
    private static final int BEAM_UNIT = 20;

    /** The headlights that are on, for every shader they light: the world, the figures, the air and the rain. */
    private static final String BEAMS = """
            uniform vec4 uBeamAt[2];
            uniform vec3 uBeamWay[2];
            uniform sampler2DShadow uBeamShadow;
            uniform mat4 uBeamMatrix;

            // How much of the nearest vehicle's headlights reaches p past what stands in the
            // beam, from a picture of the scene drawn from the lamps.
            float beamReaches(vec3 p) {
                vec4 c = uBeamMatrix * vec4(p, 1.0);
                if (c.w <= 0.0) {
                    return 1.0;
                }
                vec3 s = c.xyz / c.w * 0.5 + 0.5;
                if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) {
                    return 1.0;
                }
                float e = 1.5 / 4096.0;
                return 0.25 * (shadow2D(uBeamShadow, s + vec3(e, e, 0.0)).r + shadow2D(uBeamShadow, s + vec3(-e, e, 0.0)).r
                        + shadow2D(uBeamShadow, s + vec3(e, -e, 0.0)).r + shadow2D(uBeamShadow, s + vec3(-e, -e, 0.0)).r);
            }

            // What the headlights that are on give the air at p: a broad spill and a hot
            // middle, thinning with the square of the distance.
            float beamAt(int i, vec3 p, out vec3 dir) {
                vec3 to = p - uBeamAt[i].xyz;
                float far2 = dot(to, to);
                dir = to * inversesqrt(max(far2, 0.0001));
                float within = dot(dir, uBeamWay[i]);
                float cone = 0.5 * smoothstep(0.78, 0.96, within) + smoothstep(0.93, 0.995, within);
                float given = cone * uBeamAt[i].w / (1.0 + 0.045 * far2) * (1.0 - smoothstep(20.0, 30.0, sqrt(far2)));
                if (i == 0 && given > 0.0) {
                    // Asked a little way back towards the lamps: a surface is not in its own shadow.
                    given *= beamReaches(p - dir * 0.22);
                }
                return given;
            }

            // And what they give a surface there that faces n. Leaves and grass take light
            // from any side.
            vec3 beams(vec3 p, vec3 n, float leafy) {
                vec3 sum = vec3(0.0);
                for (int i = 0; i < 2; i++) {
                    if (uBeamAt[i].w > 0.0) {
                        vec3 dir;
                        float given = beamAt(i, p, dir);
                        sum += vec3(1.0, 0.92, 0.76) * (given * mix(0.12 + 0.88 * max(dot(n, -dir), 0.0), 0.7, leafy));
                    }
                }
                return sum;
            }
            """;

    private static final String WORLD_VERTEX = """
            #version 120
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;
            uniform vec3 uWind;
            uniform float uLaid;

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
                // What is laid on the ground does not blow about: there the normal's length says
                // how much of the layer shows at the vertex.
                // Nor does water: there the length says how far the vertex is from the bank.
                // The colour's alpha carries what kind of thing the vertex is, and in the
                // thirty-twos above that what it is made of.
                vMaterial = floor((gl_Color.a + 0.5) / 32.0);
                vKind = gl_Color.a - 32.0 * vMaterial;
                bool still = uLaid > 0.5 && vKind < 7.5 || vKind > 3.5 && vKind < 7.5;
                vec4 at = still ? gl_Vertex : blown(gl_Vertex, gl_Normal);
                vCover = 2.0 - length(gl_Normal);
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
            uniform sampler2D uShadowDepth;
            uniform sampler2D uHeights;
            uniform mat4 uRoofMatrix;
            uniform float uCover;
            uniform vec2 uDrift;
            uniform float uSoftPerDepth;
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uSeconds;
            uniform float uCut;
            uniform vec2 uTexel;
            uniform float uLaid;
            uniform float uWet;
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;

            float shadowAt(sampler2DShadow map, vec3 c, float soft) {
                // The sun is not a point, so a shadow has a soft edge. Sixteen samples in a
                // spiral over a disc, the spiral turned a different way at every pixel: the
                // edge comes out as a fine grain that the picture's last pass smooths away,
                // where the same sixteen everywhere would show as stripes.
                float turn = 6.2831853 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                float lit = 0.0;
                for (int i = 0; i < 16; i++) {
                    float a = turn + float(i) * 2.3999632;
                    float away = sqrt((float(i) + 0.5) / 16.0) * soft * uShadowTexel;
                    lit += shadow2D(map, c + vec3(cos(a) * away, sin(a) * away, 0.0)).r;
                }
                return lit / 16.0;
            }

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            const float RELIEF = 0.05;

            // How much of the sun a surface throws at the eye: the microfacet model every
            // modern renderer uses (GGX facets, Schlick's Fresnel, Smith's shadowing). rough
            // is how rough the surface is, 0 a mirror and 1 chalk; f0 is how much it mirrors
            // seen square on, 0.04 for anything that is not metal.
            float glossed(vec3 n, vec3 v, vec3 l, float rough, float f0) {
                vec3 h = normalize(v + l);
                float nl = max(dot(n, l), 0.0), nv = max(dot(n, v), 0.001), nh = max(dot(n, h), 0.0), vh = max(dot(v, h), 0.0);
                float a = rough * rough, a2 = a * a;
                float d = nh * nh * (a2 - 1.0) + 1.0;
                float facets = a2 / (3.14159265 * d * d);
                float k = (rough + 1.0) * (rough + 1.0) / 8.0;
                float seen = 1.0 / ((nv * (1.0 - k) + k) * (nl * (1.0 - k) + k));
                float fresnel = f0 + (1.0 - f0) * pow(1.0 - vh, 5.0);
                return facets * seen * fresnel * 0.25 * nl;
            }
            uniform float uRaining;
            uniform float uDark;
            """ + BEAMS + """
            uniform sampler2D uAsphalt;
            uniform sampler2D uGravel;
            uniform sampler2D uSoil;
            uniform sampler2D uLawn;
            uniform vec4 uMaterialGain;
            uniform sampler2D uShingles;
            uniform sampler2D uPaint;
            uniform sampler2D uWood;
            uniform vec3 uSurfaceGain;
            uniform sampler2D uLeaves;
            uniform sampler2D uBark;
            uniform sampler2D uPlanks;
            uniform sampler2D uCarpet;
            uniform vec3 uTimberGain;
            uniform float uLeafGain;


            float grain(vec2 p) {
                // Map positions are in the thousands, and scaled up for fine detail they pass
                // what a float can count singly. Wrapped, the pattern repeats only far away.
                p = mod(p, 4096.0);
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            // Smooth noise over the ground, for where the puddles lie.
            float patches(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(grain(cell), grain(cell + vec2(1.0, 0.0)), f.x),
                        mix(grain(cell + vec2(0.0, 1.0)), grain(cell + vec2(1.0, 1.0)), f.x), f.y);
            }

            // The height of a bed of stones of three sizes, from coarse to fine.
            float stones(vec2 p) {
                return patches(p * 9.0) * 0.5 + patches(p * 23.0 + 5.0) * 0.32 + patches(p * 61.0 + 11.0) * 0.18;
            }

            // The height of water's surface: three sizes of ripple, each drifting its own way.
            float swell(vec2 p) {
                float t = uSeconds;
                return patches(p * 1.3 + vec2(t * 0.21, t * 0.13)) * 0.5
                        + patches(p * 3.1 + vec2(-t * 0.33, t * 0.27)) * 0.3
                        + patches(p * 7.7 + vec2(t * 0.41, -t * 0.52)) * 0.2;
            }

            // How much of the sky a point can see. The land round the eye is kept as a map of
            // heights seen from straight above, and from the point rays are sent out along
            // the ground six ways, three lengths each: how high the land stands over the
            // point at each says how much of the sky that way is hidden. A path between two
            // houses is dim, the ground under a tree is dim, a lawn in the open is not.
            float skySeen(vec3 at) {
                float hidden = 0.0;
                for (int way = 0; way < 6; way++) {
                    float a = float(way) * 1.0471976 + 0.4;
                    vec2 dir = vec2(cos(a), sin(a));
                    float highest = 0.0;
                    for (int far = 0; far < 3; far++) {
                        float d = far == 0 ? 0.7 : far == 1 ? 1.7 : 3.6;
                        vec3 c = (uRoofMatrix * vec4(at.xy + dir * d, at.z, 1.0)).xyz * 0.5 + 0.5;
                        if (c.x < 0.0 || c.x > 1.0 || c.y < 0.0 || c.y > 1.0) {
                            continue;
                        }
                        float over = (c.z - texture2D(uHeights, c.xy).r) * 80.0 - 0.2;
                        highest = max(highest, over / d);
                    }
                    hidden += clamp(highest * 0.6, 0.0, 1.0);
                }
                return 1.0 - hidden / 6.0 * 0.75;
            }

            // Where rain lands. The ground is cut into cells and each cell is hit again and
            // again, at a place and a moment of its own. x is the height of the ring that
            // spreads from the hit, y the splash thrown up at the hit itself.
            vec2 landing(vec2 p) {
                vec2 cell = floor(p * 2.4);
                vec2 within = fract(p * 2.4);
                float turn = fract(uSeconds * (1.1 + 0.6 * grain(cell + 3.0)) + grain(cell + 7.0));
                float round = floor(uSeconds * (1.1 + 0.6 * grain(cell + 3.0)) + grain(cell + 7.0));
                vec2 hit = 0.25 + 0.5 * vec2(grain(cell + round), grain(cell + round + 19.0));
                float away = length(within - hit);
                float ring = sin((away - turn * 0.42) * 46.0) * exp(-pow((away - turn * 0.42) * 11.0, 2.0)) * (1.0 - turn);
                float splash = smoothstep(0.07, 0.0, away) * smoothstep(0.22, 0.0, turn);
                return vec2(ring, splash);
            }

            void main() {
                vec4 texel = texture2D(uTexture, vUv);
                // The game's sprites have no smaller copies of themselves for when they are far
                // off, and a pixel of the picture then lands on one texel of several, a
                // different one each frame as the eye moves: leaves and fences sparkle. So a
                // pixel that covers more than a texel takes four samples spread over what it
                // covers, each weighed by how solid it is, and is as solid as their mean.
                vec2 stepX = dFdx(vUv), stepY = dFdy(vUv);
                bool small = uTexel.x > 0.0 && max(dot(stepX, stepX), dot(stepY, stepY)) > 1.3 * uTexel.x * uTexel.x;
                if (small) {
                    vec4 a = texture2D(uTexture, vUv + 0.125 * stepX + 0.375 * stepY);
                    vec4 b = texture2D(uTexture, vUv - 0.375 * stepX + 0.125 * stepY);
                    vec4 c = texture2D(uTexture, vUv + 0.375 * stepX - 0.125 * stepY);
                    vec4 d = texture2D(uTexture, vUv - 0.125 * stepX - 0.375 * stepY);
                    float solid = a.a + b.a + c.a + d.a;
                    texel = vec4((a.rgb * a.a + b.rgb * b.a + c.rgb * c.a + d.rgb * d.a) / max(solid, 0.001), solid * 0.25);
                }
                bool grass = vKind > 7.5;
                // The game draws its furniture with a dark line round every part, which is what
                // makes a chair a drawing of a chair. A texel much darker than what lies round
                // it is such a line, and takes the colour round it, a little darkened: the
                // edge is still there, as a shade and not as ink. The same for whatever else
                // stands as a card: a mailbox, a lamp, a sign.
                if ((vMaterial > 2.5 && vMaterial < 3.5 || vMaterial > 7.5 && vMaterial < 8.5 || vMaterial < 0.5 && vKind > 1.5 && vKind < 3.5) && uTexel.x > 0.0) {
                    float ink = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                    vec3 round = vec3(0.0);
                    float found = 0.0;
                    for (int i = 0; i < 4; i++) {
                        vec2 off = (i == 0 ? vec2(3.0, 0.0) : i == 1 ? vec2(-3.0, 0.0) : i == 2 ? vec2(0.0, 3.0) : vec2(0.0, -3.0)) * uTexel;
                        vec4 near = texture2D(uTexture, vUv + off);
                        float light = dot(near.rgb, vec3(0.299, 0.587, 0.114));
                        float counts = step(0.5, near.a) * step(ink + 0.12, light);
                        round += near.rgb * counts;
                        found += counts;
                    }
                    if (found > 0.5) {
                        texel.rgb = mix(texel.rgb, round / found * 0.78, 0.85);
                    }
                }
                if (vMaterial > 4.5 && vMaterial < 5.5) {
                    // The leaves of a tree. The game's sprite gives the tree its outline and the
                    // colour it is at this time of year, taken broadly so that the painted
                    // edges of its leaves do not come through. A picture of real leaves, laid
                    // over the card by where it is in the world, gives it its leaves: their
                    // shape against the sky, and their light and dark.
                    vec2 wide = uTexel * 5.0;
                    vec4 broad = (texture2D(uTexture, vUv + vec2(wide.x, 0.0)) + texture2D(uTexture, vUv - vec2(wide.x, 0.0))
                            + texture2D(uTexture, vUv + vec2(0.0, wide.y)) + texture2D(uTexture, vUv - vec2(0.0, wide.y)) + texel) / 5.0;
                    vec2 along = vec2(dot(vWorld.xy, normalize(vec2(vNormal.y, -vNormal.x) + vec2(1e-5))), vWorld.z);
                    // Two layers of leaves, the second smaller and offset: a crown is leaves
                    // behind leaves, and one layer alone is a sparse spray.
                    vec4 leaf = texture2D(uLeaves, along * 0.5);
                    vec4 behind = texture2D(uLeaves, along * 0.83 + 0.37);
                    if (leaf.a < 0.5) {
                        leaf = vec4(behind.rgb * 0.7, behind.a);
                    }
                    // Seen at a slant the leaves run together into one flat green sheet. Where a
                    // pixel takes in more than a sixth of the leaf picture the card is left out,
                    // thinning away first: the other cards of the fan are the tree from here.
                    // Nor is a leaf right against the eye drawn: standing in a tree, the leaves
                    // within reach thin away and the view is of the wood beyond them.
                    if (length(uEye - vWorld) < 0.9 + 0.7 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                    vec2 spread = fwidth(along * 0.5);
                    if (max(spread.x, spread.y) > 0.06 + 0.1 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                    if (broad.a < 0.35 || leaf.a < 0.5) {
                        discard;
                    }
                    float shade = dot(leaf.rgb, vec3(0.299, 0.587, 0.114)) * uLeafGain;
                    texel = vec4(broad.rgb / max(broad.a, 0.001) * mix(0.55, 1.25, clamp(shade * 0.7, 0.0, 1.0)), 1.0);
                    small = false;
                }
                if (grass) {
                    // A card of grass: blades side by side along it, each its own height, lean
                    // and shade, narrowing to a point. Further off they are shorter, and at the
                    // edge of where grass stands they are gone, so no line shows where it ends.
                    // A blade right against the eye is a green wall across the picture. Within
                    // arm's reach the grass thins away to nothing.
                    if (length(uEye - vWorld) < 0.45 + 0.5 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                    float up = 1.0 - vCover;
                    // Blades are counted along the card, whichever way it is turned.
                    vec2 run = normalize(vec2(-vNormal.y, vNormal.x));
                    float along = dot(vWorld.xy, run) * 46.0;
                    float blade = floor(along);
                    float kind = grain(vec2(blade, 7.0));
                    // Lawn dies away with distance and is gone where it stops being grown. Long grass,
                    // which is grown as far as the eye sees, only thins.
                    float tall = (0.25 + 0.75 * kind * kind) * (vKind > 15.5 ? 1.0 : 1.0 - smoothstep(8.0, 15.5, length(uEye - vWorld)));
                    // A blade bows over further the higher up it, to one side or the other.
                    float bow = (grain(vec2(blade, 3.0)) - 0.5) * 1.3 * up * up;
                    float reach = up / max(tall, 0.001);
                    if (reach > 1.0 || abs(fract(along) - 0.5 + bow) > 0.46 * (1.0 - reach * reach)) {
                        discard;
                    }
                    // Dark where the blades crowd at the root, light at the tip where the sun
                    // gets to them, and here and there a blade gone to straw.
                    float dry = smoothstep(0.82, 1.0, grain(vec2(blade, 11.0)));
                    texel.rgb = mix(texel.rgb, vec3(0.62, 0.55, 0.3), dry * 0.6);
                    texel.rgb *= (0.6 + 0.7 * reach) * (0.8 + 0.4 * grain(vec2(blade, 13.0)));
                    texel.a = 1.0;
                }
                // The sprites were drawn for a view in which every level line runs two pixels
                // across for one down, so the edge of a door or a wall is a staircase of
                // two-pixel steps. Seen square on and close, the steps show as a saw edge. Taken
                // as the mean of two samples one texel apart across, each step is averaged with
                // the next and the edge comes out straight.
                vec2 half = vec2(0.5 * uTexel.x, 0.0);
                float cover = uTexel.x > 0.0 && !small ? 0.5 * (texture2D(uTexture, vUv - half).a + texture2D(uTexture, vUv + half).a) : texel.a;
                // A wire or an aerial painted one texel wide never covers more than half of two
                // texels, and at the cut for solid things it comes out as a row of dashes. What
                // stands as a card is cut lower, so a thin line stays whole.
                bool upright = vKind > 1.5 && vKind < 3.5;
                // An overhead wire is a hair of paint with nothing round it, and mixed with that
                // nothing it comes out a pale ghost. It is a black cable, wherever any of it shows.
                if (vMaterial > 11.5 && vMaterial < 12.5) {
                    if (cover < 0.04) {
                        discard;
                    }
                    texel = vec4(0.03, 0.03, 0.035, 1.0);
                    cover = 1.0;
                }
                // Chain link is thin galvanised wire. The game paints it as heavy dark strokes,
                // which up close are a black lattice: here the wire is half as thick and the
                // grey of zinc.
                if (vMaterial > 14.5) {
                    // Far off, where a pixel takes in several wires, the fence is a veil and is
                    // let through by how much of the pixel is wire.
                    if (cover < (small ? 0.12 + 0.3 * grain(gl_FragCoord.xy) : 0.72)) {
                        discard;
                    }
                    cover = 1.0;
                    texel.rgb = mix(texel.rgb, vec3(0.52, 0.54, 0.55), 0.75);
                }
                if (cover < (upright ? min(uCut, 0.1) : uCut)) {
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
                // A tuft of grass is lit as the ground it grows from is, from above. Lit as the
                // card it is drawn on, each tuft is a flat pane brighter or darker than the
                // next, and a lawn is a heap of green blocks.
                if (grass) {
                    n = normalize(vec3(n.xy * 0.2, 1.0));
                }

                // Open ground. The game has a handful of squares of each ground and lays them
                // side by side, so a road or a yard is the same square over and over. Real
                // ground is not: it is paler here and darker there over many paces, and close
                // to it is made of stones and grit that the sun catches. Both are worked out
                // from where the ground is on the map, so they never repeat and they stay put.
                float grit = 0.0;
                if (vKind < 1.5 && vKind > 0.5 && n.z > 0.8) {
                    vec2 p = vWorld.xy;
                    float wide = patches(p * 0.11) * 0.55 + patches(p * 0.47 + 31.0) * 0.3 + patches(p * 1.9 + 7.0) * 0.15;
                    texel.rgb *= 0.78 + 0.44 * wide;
                    float most = max(texel.r, max(texel.g, texel.b));
                    float least = min(texel.r, min(texel.g, texel.b));
                    // Stone, gravel and tarmac are grey; earth and grass are not.
                    float stony = 1.0 - smoothstep(0.12, 0.3, (most - least) / max(most, 0.001));
                    // What the ground is made of is read off its colour, and a picture of that
                    // stuff is laid over it by its place on the map: twice, at two sizes and
                    // turned against each other, so that neither copy's repeat shows. The
                    // game's own colour stays; the picture gives it its grain.
                    float green = (1.0 - stony) * smoothstep(-0.06, 0.04, (texel.g - texel.r) / max(most, 0.001));
                    float dark = stony * (1.0 - smoothstep(0.38, 0.56, most));
                    // A picture is about a pace and a half of ground: its stones come out the size of gravel.
                    vec2 one = p * 0.83;
                    vec2 two = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 0.37 + 0.37;
                    float which = smoothstep(0.35, 0.65, patches(p * 0.23 + 51.0));
                    vec3 grain3 = dark * uMaterialGain.x * mix(texture2D(uAsphalt, one).rgb, texture2D(uAsphalt, two).rgb, which)
                            + (stony - dark) * uMaterialGain.y * mix(texture2D(uGravel, one).rgb, texture2D(uGravel, two).rgb, which)
                            // Earth only where the game says the ground is earth: what is brown may as well be planks.
                            + (1.0 - stony - green) * (vMaterial > 3.5 ? uMaterialGain.z * mix(texture2D(uSoil, one).rgb, texture2D(uSoil, two).rgb, which) : vec3(1.0))
                            + green * uMaterialGain.w * mix(texture2D(uLawn, one).rgb, texture2D(uLawn, two).rgb, which);
                    // The picture's light and dark go over the game's colour everywhere. Where
                    // the ground is stone, which has no colour of its own to lose, the picture's
                    // colour comes too, as bright as the game painted the spot: tarmac and gravel
                    // are then the real thing and not a painting of it. Grass keeps the game's
                    // colour, which is the season's.
                    float fine = dot(grain3, vec3(0.299, 0.587, 0.114));
                    float bright = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                    texel.rgb = mix(texel.rgb * mix(1.0, fine, 0.9), grain3 * bright, 0.8 * stony);
                    float near = 1.0 - smoothstep(5.0, 16.0, range);
                    if (near > 0.0) {
                        float e = 0.012;
                        float here = stones(p), east = stones(p + vec2(e, 0.0)), south = stones(p + vec2(0.0, e));
                        float depth = (0.05 + 0.1 * stony) * near;
                        n = normalize(n - vec3(east - here, south - here, 0.0) * (depth / e));
                        texel.rgb *= 1.0 + (here - 0.5) * (0.25 + 0.3 * stony) * near;
                        grit = stony * near * here;
                    }
                }

                // What a building is made of. The game's sprite says what colour a roof or a
                // wall is and where its boards and windows are; a picture of the stuff itself,
                // laid over it by where the surface is in the world, gives it the grain of
                // shingle, of old paint, of floorboards. Its light and dark only.
                // A sheet with no sprite that is marked as shingle is the mod's own roofing: it
                // is the grey of shingle, in sun as in shade.
                if (vMaterial > 0.5 && vMaterial < 1.5 && min(texel.r, min(texel.g, texel.b)) > 0.99) {
                    texel.rgb = vec3(0.3, 0.3, 0.32);
                }
                // So are the mod's own kerbs, of concrete, and the walls of its quays, of dark
                // wet timber.
                if (vMaterial > 8.5 && vMaterial < 10.5) {
                    texel.rgb = vMaterial < 9.5 ? vec3(0.86, 0.86, 0.83) : vec3(0.2, 0.17, 0.14);
                }
                if (vMaterial > 0.5 && vMaterial < 3.5 || vMaterial > 5.5 && vMaterial < 8.5) {
                    vec2 across = abs(n.z) > 0.7 ? vWorld.xy : vec2(dot(vWorld.xy, vec2(n.y, -n.x)), vWorld.z);
                    float fine = vMaterial < 1.5 ? dot(texture2D(uShingles, across * 0.55).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.x
                            : vMaterial < 2.5 ? dot(texture2D(uPaint, across * 0.8).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.y
                            : vMaterial < 3.5 ? dot(texture2D(uWood, across * 0.45).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.z
                            : vMaterial < 6.5 ? dot(texture2D(uBark, across * vec2(1.6, 0.8)).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.x
                            : vMaterial < 7.5 ? dot(texture2D(uPlanks, across * 0.7).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.y
                            : dot(texture2D(uCarpet, across * 1.6).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.z;
                    float much = vMaterial < 1.5 ? 0.85 : vMaterial < 2.5 ? 0.3 : vMaterial < 3.5 ? 0.7 : 0.8;
                    // What is painted white is paint over the wood: its grain barely shows.
                    if (vMaterial > 2.5 && vMaterial < 3.5) {
                        much *= 1.0 - 0.75 * smoothstep(0.6, 0.85, min(texel.r, min(texel.g, texel.b)));
                    }
                    texel.rgb *= mix(1.0, fine, much);
                }

                // A card stands in for something with depth and leaves, a bush or a tree, which
                // the sun gets into from any side: it is lit whichever way the card faces.
                bool water = vKind > 3.5 && !grass;
                float foam = 0.0;
                if (water) {
                    // Water is made here, not painted. It runs the way the land under it falls,
                    // and as fast as the fall is steep: on level ground it is a still pond, on a
                    // slope a stream. It is pale and clear in the shallows at the bank and dark
                    // in the middle, froths where it runs fast, and laps white at its edge.
                    float bank = 1.0 - vCover;
                    vec2 fall = vNormal.xy / max(length(vNormal), 0.001);
                    vec2 flow = fall * 32.0;
                    float speed = length(flow);
                    vec2 p = vWorld.xy - flow * uSeconds;
                    float churn = patches(p * vec2(5.1, 5.1)) * 0.5 + patches(p * 13.7 + 3.0) * 0.3 + patches(p * 31.0 + 9.0) * 0.2;
                    float lap = smoothstep(0.84, 1.0, bank + 0.06 * sin(uSeconds * 0.9 + churn * 5.0)) * smoothstep(0.35, 0.7, churn);
                    // A broad river slides along without breaking. Only water on a real fall froths.
                    foam = clamp(smoothstep(0.58, 0.78, churn) * smoothstep(0.9, 1.6, speed) + lap * 0.8, 0.0, 1.0);
                    // Deep water is dark and shows nothing under it. In the shallows at the bank
                    // the bed shows through, its stones shifting as the ripples above bend the
                    // light, and the water over it only tints it.
                    vec2 bent = vWorld.xy + vec2(swell(vWorld.xy), swell(vWorld.xy + 3.7)) * 0.18;
                    vec3 bed = texture2D(uSoil, bent * 0.6).rgb * uMaterialGain.z * vec3(0.5, 0.56, 0.46);
                    vec3 body = mix(vec3(0.16, 0.3, 0.33), bed, smoothstep(0.3, 1.0, bank) * 0.85);
                    texel.rgb = mix(body, vec3(0.95), foam) / 0.74;
                }
                // A sprite is paint on a flat sheet. Its light and dark say where the thing it
                // shows stands proud and where it is sunk, the mortar between bricks, the gaps
                // between boards, the blades of grass, so the sheet is lit as if it were
                // raised where it is light. The sun then picks the surface out as it moves, and
                // a wall or a lawn stops being a photograph of one. It fades with distance,
                // where it would only shimmer.
                float relief = water || grass ? 0.0 : RELIEF * (1.0 - smoothstep(14.0, 38.0, range));
                if (relief > 0.0) {
                    float height = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                    vec3 acrossX = dFdx(vWorld), acrossY = dFdy(vWorld);
                    vec3 r1 = cross(acrossY, n), r2 = cross(n, acrossX);
                    float det = dot(acrossX, r1);
                    // Seen edge on, a sheet has no width on the screen to measure a slope across.
                    if (abs(det) > 1e-9) {
                        vec3 slope = (dFdx(height) * r1 + dFdy(height) * r2) / det;
                        float steep = length(slope) * relief;
                        n = normalize(n - slope * (relief * min(1.0, 0.7 / max(steep, 1e-4))));
                    }
                }
                bool card = !water && vKind > 1.5;
                if (card && !grass && length(vNormal.xy) > 0.1 * length(vNormal)) {
                    // A card seen edge on is a streak of smeared leaves. It thins out and goes
                    // before it gets there: the other cards of the fan are what is seen then.
                    float square = abs(dot(normalize(vNormal.xy + vec2(1e-5)), normalize(toEye.xy + vec2(1e-5))));
                    if (square < 0.2 + 0.25 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                }
                float open = grass ? vKind - (vKind > 15.5 ? 16.0 : 8.0) : water ? vKind - 4.0 : card ? vKind - 2.0 : vKind;
                float facing = clamp(dot(n, uLight), 0.0, 1.0);
                if (card) {
                    facing = 0.5 + 0.4 * facing;
                }
                float sun = 0.0;
                float reached = 0.0;
                if (facing > 0.0) {
                    // The cards of a tree's fan stand in each other's shade and move in the wind,
                    // and their shadows on one another would flicker all over the tree. A card
                    // asks about the shade a little way towards the sun, past its own fan.
                    // Further off the surface than the soft edge is wide, or a level surface shades itself.
                    vec4 from = vec4(vWorld + n * 0.16 + (card ? uLight * 0.9 : vec3(0.0)), 1.0);
                    vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                    if (inside(c)) {
                        // A shadow is sharp where the thing that casts it touches the ground and
                        // soft far from it: the foot of a post against the top of its shadow. How
                        // far off the caster is, is found by looking round in the map for what is
                        // nearer the sun than this point.
                        float turn = 6.2831853 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                        float nearer = 0.0, casters = 0.0;
                        for (int i = 0; i < 8; i++) {
                            float a = turn + float(i) * 2.3999632;
                            float d = texture2D(uShadowDepth, c.xy + vec2(cos(a), sin(a)) * (sqrt((float(i) + 0.5) / 8.0) * 10.0 * uShadowTexel)).r;
                            if (d < c.z - 0.0008) {
                                nearer += d;
                                casters += 1.0;
                            }
                        }
                        float soft = casters > 0.0 ? clamp((c.z - nearer / casters) * uSoftPerDepth, 1.2, 14.0) : 1.2;
                        reached = shadowAt(uShadowNear, c, soft);
                    } else {
                        c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                        reached = inside(c) ? shadowAt(uShadowFar, c, 1.6) : 1.0;
                    }
                    // The clouds overhead pass between the sun and the ground, and their
                    // shadows cross the land with the wind: fields go dull and bright by turns.
                    vec2 under = (vWorld.xy + uLight.xy / max(uLight.z, 0.25) * 30.0) / 22.0 + uDrift * 6.0;
                    float body = patches(under) * 0.6 + patches(under * 2.7 + 7.0) * 0.4;
                    reached *= 1.0 - 0.65 * smoothstep(-0.06, 0.06, body - (1.12 - uCover));
                    sun = reached * facing;
                }

                // The game's light for the square is the light that is there without the sun
                // falling straight on it. Out of doors that light comes from the sky, more of it
                // onto what faces up and bluer than the sun.
                vec3 fromSky = mix(vec3(1.0), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), open);
                if (open > 0.5 && range < 40.0) {
                    fromSky *= mix(skySeen(vWorld + n * 0.1), 1.0, smoothstep(25.0, 40.0, range));
                }
                vec3 lightHere = pow(vColor.rgb, vec3(2.2)) * fromSky + pow(uLightColor, vec3(2.2)) * (2.1 * sun);
                vec3 color = pow(texel.rgb, vec3(2.2)) * lightHere;
                if (card) {
                    // A leaf lets the sun through. Looked at against the light, foliage and
                    // grass glow from behind, yellower than they are lit from in front, and
                    // that glow is most of what a tree looks like on a bright day.
                    float against = pow(max(dot(-view, uLight), 0.0), 2.0);
                    vec3 leaf = pow(texel.rgb, vec3(2.2)) * vec3(1.25, 1.2, 0.55);
                    color += leaf * pow(uLightColor, vec3(2.2)) * (1.6 * against * reached * step(0.0, uLight.z));
                    // Deep in the plant the leaves stand in each other's light: darker towards
                    // the foot of a card than at its top.
                    color *= 0.72 + 0.28 * clamp(vCover < 1.0 ? 1.0 - vCover : 1.0, 0.0, 1.0);
                }

                // Water, and ground that rain has wetted, show the sky: little of it looking
                // straight down, nearly all of it at a glancing angle, and the sun as a streak
                // of light. Wet ground is darker than dry, and holds the most water where it
                // pools.
                float shine = 0.0;
                float ripple = 0.0;
                if (water) {
                    shine = 1.0 - foam;
                    ripple = 0.05;
                    color *= 0.55;
                } else if (!card && uWet > 0.0 && open > 0.5 && n.z > 0.85) {
                    float pool = smoothstep(0.52, 0.7, patches(vWorld.xy * 0.45));
                    shine = uWet * (0.22 + 0.78 * pool);
                    ripple = 0.012 * pool;
                    color *= 1.0 - 0.3 * uWet;
                }
                vec2 rings = vec2(0.0);
                if (uRaining > 0.0 && shine > 0.0 && range < 30.0) {
                    // Rain rings on whatever holds water, by how hard it is raining.
                    float e = 0.01;
                    float here = landing(vWorld.xy).x;
                    rings = vec2(landing(vWorld.xy + vec2(e, 0.0)).x - here, landing(vWorld.xy + vec2(0.0, e)).x - here) * (uRaining * 0.012 / e);
                }
                if (uRaining > 0.0 && !card && open > 0.5 && n.z > 0.8 && range < 22.0) {
                    color += atmosphere(vec3(0.0, 0.0, 1.0)) * (landing(vWorld.xy).y * uRaining * 0.6);
                }
                if (!card && !water && reached > 0.0) {
                    // Every surface has a gloss of its own: dull for earth and render, more for
                    // paint and planed wood, and much more for anything with water lying on it.
                    float pale = max(texel.r, max(texel.g, texel.b));
                    float rough = mix(0.82, 0.5, smoothstep(0.55, 0.95, pale));
                    if (vMaterial > 2.5 && vMaterial < 3.5) {
                        rough = 0.32;
                    }
                    // Glazed tile is glossier than a varnished board.
                    if (vMaterial > 10.5 && vMaterial < 11.5) {
                        rough = 0.22;
                    }
                    rough = mix(rough, 0.18, shine);
                    color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * glossed(n, view, uLight, rough, 0.04));
                }
                color += pow(texel.rgb, vec3(2.2)) * beams(vWorld, n, card ? 1.0 : 0.0);
                // A lamp that is lit is brighter than anything it lights. The game lights the
                // square a burning lamp stands on strongly, and in the dark that is how a lamp
                // that is on is told: its pale parts, the shade and the glass, then give light
                // of their own, enough to bloom.
                if (vMaterial > 13.5 && vMaterial < 14.5 && uDark > 0.0) {
                    float lit = smoothstep(0.55, 0.95, max(vColor.r, max(vColor.g, vColor.b)));
                    float pale = smoothstep(0.45, 0.85, max(texel.r, max(texel.g, texel.b)));
                    color += pow(texel.rgb, vec3(2.2)) * vec3(1.0, 0.86, 0.62) * (9.0 * lit * pale * uDark);
                }
                if (grit > 0.0 && sun > 0.0) {
                    // Grit glints: each stone has a face or two that throws the sun at the eye.
                    vec3 back = reflect(-view, n);
                    color += pow(uLightColor, vec3(2.2)) * (pow(max(dot(back, uLight), 0.0), 24.0) * 0.5 * grit * sun);
                }
                if (shine > 0.0) {
                    // The surface of water is many trains of ripples of every size crossing one
                    // another, none of them regular. Its slope here is the difference between
                    // its height here and a little way off each way.
                    float e = 0.04;
                    float level = swell(vWorld.xy);
                    vec2 wave = vec2(swell(vWorld.xy + vec2(e, 0.0)) - level, swell(vWorld.xy + vec2(0.0, e)) - level) * (9.0 / e) * 0.1;
                    vec3 surface = normalize(vec3(n.xy + wave * ripple + rings, n.z));
                    vec3 mirrored = reflect(-view, surface);
                    mirrored.z = abs(mirrored.z);
                    float glance = 0.03 + 0.97 * pow(1.0 - max(dot(view, surface), 0.0), 5.0);
                    color = mix(color, atmosphere(mirrored) * (0.35 + 0.65 * open), glance * shine);
                    color += pow(uLightColor, vec3(2.2)) * (2.1 * shine * reached * glossed(surface, view, uLight, water ? 0.09 : 0.2, 0.02));
                    // Headlights on a wet road are a long streak of glare towards the eye.
                    for (int i = 0; i < 2; i++) {
                        if (uBeamAt[i].w > 0.0) {
                            vec3 dir;
                            float given = beamAt(i, vWorld, dir);
                            color += vec3(1.0, 0.92, 0.76) * (given * shine * glossed(surface, view, -dir, water ? 0.09 : 0.2, 0.02));
                        }
                    }
                }

                float haze = max(smoothstep(uFogStart, uFogEnd, range), 1.0 - exp(-range * 0.0035));
                color = mix(color, atmosphere(-view), haze);
                // Ground spreading over its neighbour thins out towards its edge, and the edge
                // is ragged: tufts and patches, not a ruled fade.
                float shown = 1.0;
                if (uLaid > 0.5 && !grass && vCover < 0.999) {
                    float ragged = patches(vWorld.xy * 1.9) * 0.6 + patches(vWorld.xy * 6.1 + 17.0) * 0.4 - 0.5;
                    shown = smoothstep(0.38, 0.62, vCover + ragged * 3.0 * vCover * (1.0 - vCover));
                }
                // What is laid over the ground is mixed in by its alpha. For everything else the
                // alpha says how much of a mirror the surface is, 1 for none, which the last
                // pass reads to trace what the mirror shows.
                // A varnished floor mirrors a little too: the window across the room, the legs of
                // a chair.
                float polish = vMaterial > 2.5 && vMaterial < 3.5 ? 0.22 : vMaterial > 10.5 && vMaterial < 11.5 ? 0.3 : 0.0;
                // A window pane mirrors whichever way it faces.
                float pane = vMaterial > 12.5 && vMaterial < 13.5 ? 0.45 : 0.0;
                gl_FragColor = vec4(color, uLaid > 0.5 ? texel.a * shown : 1.0 - max(max(shine, polish) * step(0.8, n.z), pane));
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

    /**
     * Rain as drops in the world. Each is a streak as long as the drop falls while the eye
     * takes it in, somewhere in a box of air that goes with the eye: a drop that falls out of
     * the bottom of the box, or is left behind, comes back in on the other side. A vertex
     * carries where in the box its drop started, and which corner of the streak it is.
     */
    private static final String RAIN_VERTEX = """
            #version 120
            uniform vec3 uEye;
            uniform float uTime;
            uniform vec2 uWind;
            uniform float uRain;
            varying vec3 vWorld;
            varying vec2 vIn;

            const float BOX = 14.0;
            const float HIGH = 12.0;

            void main() {
                vec3 seed = gl_Vertex.xyz;
                float corner = gl_Vertex.w;
                float speed = 11.0 + 5.0 * fract(seed.x * 37.0 + seed.y * 91.0);
                vec3 moving = vec3(uWind * 2.5, -speed);
                vec3 p = vec3(seed.xy * 2.0 * BOX, seed.z * HIGH) + moving * uTime;
                p.xy = mod(p.xy - uEye.xy + BOX, 2.0 * BOX) - BOX + uEye.xy;
                p.z = mod(p.z - uEye.z + HIGH * 0.35, HIGH) - HIGH * 0.35 + uEye.z;
                vec3 way = normalize(moving);
                vec3 side = normalize(cross(way, uEye - p));
                float along = corner > 1.5 ? 1.0 : 0.0;
                float across = corner > 0.5 && corner < 2.5 ? 1.0 : -1.0;
                vec3 at = p - way * (0.032 * speed * along) + side * (across * 0.008);
                vWorld = at;
                vIn = vec2(across, along);
                // The harder it rains the more of the drops there are.
                gl_Position = fract(seed.z * 17.0 + seed.x * 5.0) > uRain ? vec4(2.0, 2.0, 2.0, 1.0) : gl_ModelViewProjectionMatrix * vec4(at, 1.0);
            }
            """;

    private static final String RAIN_FRAGMENT = """
            #version 120
            uniform sampler2DShadow uRoof;
            uniform mat4 uRoofMatrix;
            uniform vec3 uTint;
            uniform vec3 uEye;
            uniform float uSeated;
            varying vec3 vWorld;
            varying vec2 vIn;
            """ + BEAMS + """

            void main() {
                // The vehicle the viewer sits in has a roof the map of roofs knows nothing of.
                if (uSeated > 0.5 && distance(vWorld.xy, uEye.xy) < 1.3 && vWorld.z < uEye.z + 0.45) {
                    discard;
                }
                // The map of what is overhead, seen from straight above: a drop under a roof
                // never fell there.
                vec3 c = (uRoofMatrix * vec4(vWorld, 1.0)).xyz * 0.5 + 0.5;
                float open = c.x > 0.0 && c.x < 1.0 && c.y > 0.0 && c.y < 1.0 ? shadow2D(uRoof, c).r : 1.0;
                float body = (1.0 - vIn.x * vIn.x) * smoothstep(0.0, 0.25, vIn.y) * smoothstep(1.0, 0.5, vIn.y);
                // A drop falling through a beam is a bright streak: it is most of what rain
                // looks like from behind the wheel at night.
                vec3 dir;
                float lit = 0.0;
                for (int i = 0; i < 2; i++) {
                    if (uBeamAt[i].w > 0.0) {
                        lit += beamAt(i, vWorld, dir);
                    }
                }
                gl_FragColor = vec4(uTint + vec3(1.0, 0.92, 0.76) * (lit * 1.4), body * open * 0.55);
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
            const int BOUNCE_SAMPLES = 10;
            const int ROOM_SAMPLES = 8;
            const float ROOM_RADIUS = 1.8;
            const float BOUNCE_RADIUS = 2.2;
            uniform sampler2D uScene;
            uniform vec3 uSunEye;

            // Where the pixel at uv is, seen from the eye: x to the right, y up, z ahead, in tiles.
            vec3 placeAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                return vec3((uv * 2.0 - 1.0) * uTan * ahead, ahead);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                if (texture2D(uDepth, uv).r >= 0.9999995) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
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
                // The same again over the width of a room: the corner where two walls and a
                // ceiling meet, the ground under a porch, the foot of a hedge. It is what makes
                // a room a hollow and not six lit cards.
                float broad = 0.0;
                float roomy = ROOM_RADIUS / (p.z * uTan.y) * 0.5;
                for (int i = 0; i < ROOM_SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(ROOM_SAMPLES);
                    float a = turn + 2.1 + along * 19.3;
                    vec2 off = vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (sqrt(along) * roomy);
                    vec3 v = placeAt(uv + off) - p;
                    float far2 = dot(v, v);
                    broad += max(dot(v, n) - 0.05, 0.0) / (far2 + 0.15) * step(far2, ROOM_RADIUS * ROOM_RADIUS * 2.0);
                }
                open *= clamp(1.0 - broad * (2.0 / float(ROOM_SAMPLES)), 0.4, 1.0);
                // Light does not stop at the first thing it falls on. A sunlit lawn throws
                // green up under the eaves, a red wall warms the path beside it. What stands
                // in the picture within a few paces of this point, on the side it faces, lends
                // it some of its own light.
                vec3 lent = vec3(0.0);
                float wide = BOUNCE_RADIUS / (p.z * uTan.y) * 0.5;
                for (int i = 0; i < BOUNCE_SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(BOUNCE_SAMPLES);
                    float a = turn + 1.3 + along * 23.7;
                    vec2 at = uv + vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (sqrt(along) * wide);
                    vec3 v = placeAt(at) - p;
                    float far2 = dot(v, v);
                    float faces = max(dot(v, n), 0.0) / sqrt(far2 + 0.0001);
                    lent += min(texture2D(uScene, at).rgb, vec3(4.0)) * (faces * step(far2, BOUNCE_RADIUS * BOUNCE_RADIUS * 4.0) / (1.0 + far2));
                }
                lent *= 1.6 / float(BOUNCE_SAMPLES) * (1.0 - smoothstep(25.0, 50.0, p.z));
                // The shadow maps cannot hold what is small and close: the shade a blade of grass
                // or the leg of a chair throws where it meets the ground. A ray is walked from
                // this point towards the sun through the picture's depth, and if it passes
                // behind something near by, that something is in the sun's way.
                if (uSunEye.z > -2.0) {
                    float reach = min(0.5, 0.03 * p.z + 0.12);
                    float begin = fract(turn * 0.159 + 0.37);
                    for (int i = 0; i < 10; i++) {
                        vec3 q = p + n * 0.03 + uSunEye * (reach * (float(i) + begin) / 10.0);
                        vec2 at = q.xy / (q.z * uTan) * 0.5 + 0.5;
                        float behind = q.z - placeAt(at).z;
                        if (behind > 0.02 && behind < 0.35) {
                            open *= 0.62;
                            break;
                        }
                    }
                }
                // Kept as a part of a whole: the target holds numbers from 0 to 1.
                gl_FragColor = vec4(lent / (1.0 + lent), mix(open, 1.0, smoothstep(35.0, 65.0, p.z)));
            }
            """;

    /** Averages the occlusion over the four by four pixels its sampling pattern repeats in, which takes the grain out. */
    private static final String SOFTEN = """
            #version 120
            uniform sampler2D uSource;
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform float uNear;
            uniform float uFar;

            float aheadAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
            }

            void main() {
                vec2 texel = 1.0 / uSize;
                vec2 uv = gl_FragCoord.xy * texel;
                float here = aheadAt(uv);
                vec4 sum = vec4(0.0);
                float weight = 0.0;
                // Only what is at this pixel's own distance is averaged in: the shade under a
                // table does not leak onto the wall seen past its edge.
                for (int x = -2; x < 2; x++) {
                    for (int y = -2; y < 2; y++) {
                        vec2 at = uv + (vec2(float(x), float(y)) + 0.5) * texel;
                        float w = 1.0 / (1.0 + 40.0 * abs(aheadAt(at) - here) / here) ;
                        w *= w;
                        sum += texture2D(uSource, at) * w;
                        weight += w;
                    }
                }
                gl_FragColor = sum / weight;
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
            """ + BEAMS + """

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
                float beamed = 0.0;
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
                    for (int b = 0; b < 2; b++) {
                        if (uBeamAt[b].w > 0.0) {
                            vec3 dir;
                            beamed += beamAt(b, p.xyz, dir);
                        }
                    }
                }
                float along = length(ray);
                float mu = max(dot(ray / along, uLight), 0.0);
                // Mist sends most of what it catches on the way the light was going.
                float towards = 0.3 + 2.4 * pow(mu, 5.0);
                vec3 caught = pow(uLightColor, vec3(2.2)) * (lit / float(STEPS) * ahead * along * uMist * towards);
                // A beam shows in clear night air too, on the dust in it, and far more in mist.
                caught += vec3(1.0, 0.92, 0.76) * (beamed / float(STEPS) * ahead * along * (0.0012 + 1.2 * uMist));
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
            uniform float uInside;
            uniform float uPainted;
            uniform float uGlassPass;
            uniform float uInstruments;
            uniform vec4 uWheel;
            uniform vec3 uWheelAxis;
            uniform sampler2D uVinyl;
            uniform sampler2D uCloth;
            uniform sampler2D uDials;
            uniform vec2 uCabinGain;
            uniform vec3 uOwnAmbient;
            uniform float uFigureAmbient;
            uniform float uRaining;
            uniform float uTime;
            """ + BEAMS + """
            uniform vec2 uAhead;
            uniform sampler2D uRepainted;
            uniform float uWriteNear;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            float noise2(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
                float b = fract(sin(dot(cell + vec2(1.0, 0.0), vec2(12.9898, 78.233))) * 43758.5453);
                float c = fract(sin(dot(cell + vec2(0.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                float d = fract(sin(dot(cell + vec2(1.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            // A pane seen from the seat, over the world behind it: the sky it mirrors, more at
            // a glancing angle, and the dust and dried rain on it, which stay put on the glass
            // as the car moves. on is the place on the pane, in tiles.
            // How far p is from the steering wheel: a round rim, a hub and three spokes, where
            // the game's model has a six-sided ring. p is measured from the eye along the
            // vehicle, across it and up. uWheel is the wheel's middle and its radius, and
            // uWheelAxis the way its column runs, away from the driver.
            float wheelAt(vec3 p) {
                vec3 q = p - uWheel.xyz;
                float deep = dot(q, uWheelAxis);
                float outward = length(q - uWheelAxis * deep);
                float rim = length(vec2(outward - uWheel.w, deep)) - 0.019;
                vec3 up = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                // The spokes dish in from the rim to the hub.
                float sunk = deep - 0.03 * (1.0 - outward / uWheel.w);
                float across = q.y, along = dot(q, up);
                float thin = abs(sunk) - 0.008;
                float bar = max(max(abs(along) - 0.017, thin), abs(across) - uWheel.w) - 0.005;
                float down = max(max(abs(across) - 0.017, thin), max(along, -along - uWheel.w)) - 0.005;
                float hub = max(outward - 0.05, abs(deep - 0.03) - 0.022) - 0.01;
                return min(min(rim, hub), min(bar, down));
            }

            float hash21(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            vec4 pane(vec2 on, vec3 n, vec3 view, float swept) {
                float dust = smoothstep(0.35, 0.85, noise2(on * 5.0) * 0.55 + noise2(on * 15.0 + 3.0) * 0.3 + noise2(on * 61.0) * 0.15);
                float runs = smoothstep(0.62, 0.95, noise2(vec2(on.x * 46.0, on.y * 2.2)));
                float dirt = clamp(dust * 0.75 + runs * 0.45, 0.0, 1.0);
                // The wipers keep their two arcs of the front screen clean and push what they
                // clear to the arcs' edges, where it dries as a line.
                if (swept > 0.5) {
                    float nearest = min(length(on - vec2(0.05, -0.30)), min(length(on - vec2(0.75, -0.30)), length(on - vec2(-0.65, -0.30))));
                    float inside = 1.0 - smoothstep(0.5, 0.54, nearest);
                    dirt = dirt * (1.0 - 0.75 * inside) + 0.55 * exp(-pow((nearest - 0.53) / 0.012, 2.0)) + 0.45 * (1.0 - inside);
                    dirt = clamp(dirt, 0.0, 1.0);
                }
                float glancing = 0.04 + 0.96 * pow(clamp(1.0 - abs(dot(n, view)), 0.0, 1.0), 5.0);
                vec3 thrown = reflect(-view, n);
                // Glass seen from inside mirrors the inside: where the mirrored ray goes down it
                // is the sunlit top of the dashboard, pale on the screen above it.
                vec3 sunLight = pow(uLightColor, vec3(2.2));
                vec3 sheen = thrown.z > 0.0 ? atmosphere(thrown) * 0.6 : vec3(0.16, 0.16, 0.17) * sunLight * (0.4 + max(uLight.z, 0.0));
                // Dust on glass shows most where the sun is behind it.
                float lit = 0.45 + 2.0 * pow(max(dot(-view, uLight), 0.0), 5.0);
                vec3 grime = vec3(0.36, 0.33, 0.28) * sunLight * lit;
                float dusty = (0.04 + 0.96 * dirt) * (0.16 + 0.2 * lit);
                float cover = clamp(0.06 + 0.5 * glancing + dusty, 0.0, 0.8);
                vec4 glass = vec4(mix(sheen, grime, clamp(dusty / cover, 0.0, 1.0)), cover);
                if (uRaining > 0.0) {
                    // Rain stands on the glass as drops. On the front screen the wipers go to
                    // and fro and the drops come back behind the blade.
                    float fresh = 1.0;
                    float blade = 0.0;
                    if (swept > 0.5) {
                        vec2 pivot = vec2(0.05, -0.30);
                        if (length(on - vec2(0.75, -0.30)) < length(on - pivot)) {
                            pivot = vec2(0.75, -0.30);
                        }
                        if (length(on - vec2(-0.65, -0.30)) < length(on - pivot)) {
                            pivot = vec2(-0.65, -0.30);
                        }
                        vec2 rel = on - pivot;
                        float reach = length(rel);
                        if (reach < 0.52 && rel.y > 0.0) {
                            const float BEAT = 1.5;
                            float q = atan(rel.y, rel.x) / 3.14159;
                            float phase = fract(uTime / BEAT);
                            float at = phase < 0.5 ? 2.0 * phase : 2.0 - 2.0 * phase;
                            float since = phase < 0.5 ? (q < at ? phase - q * 0.5 : phase + q * 0.5) : (q > at ? phase - (1.0 - q * 0.5) : phase - q * 0.5);
                            fresh = clamp(since * BEAT / 1.1, 0.0, 1.0);
                            blade = (1.0 - smoothstep(0.004, 0.011, abs(q - at) * 3.14159 * reach)) * step(0.06, reach);
                        }
                    }
                    vec2 grid = on * 46.0;
                    vec2 cell = floor(grid);
                    float chance = hash21(cell);
                    vec2 middle = (vec2(hash21(cell + 3.1), hash21(cell + 7.7)) - 0.5) * 0.5;
                    float size = 0.14 + 0.22 * chance;
                    float off = length(fract(grid) - 0.5 - middle);
                    float there = step(1.0 - uRaining * fresh * 0.8, hash21(cell + floor(uTime * 0.3 + chance * 9.0)));
                    float bead = there * (1.0 - smoothstep(size * 0.7, size, off));
                    float rim = there * smoothstep(size * 0.45, size * 0.9, off) * step(off, size);
                    float wet = bead * 0.22 + rim * 0.5;
                    // A drop is a lens: it holds the sky and, at night, the glare of the lamps.
                    vec3 held = atmosphere(vec3(0.0, 0.0, 1.0)) * 1.3 + vec3(1.0, 0.92, 0.76) * (0.1 * swept * step(0.5, uBeamAt[0].w));
                    glass.rgb = mix(glass.rgb, held, wet / (wet + glass.a));
                    glass.a = min(glass.a + wet * 0.6, 0.9);
                    glass = mix(glass, vec4(0.004, 0.004, 0.005, 1.0), blade);
                }
                return glass;
            }

            // 1 where a pixel of a vehicle is one of its panes: pale and nearly without colour,
            // or the pale blue of the sky a pane shows.
            float glass(vec2 at) {
                vec4 f = texture2D(uFigure, at);
                if (f.a < 0.5) {
                    return 1.0;
                }
                // Bodywork changes with the paint. Glass does not.
                if (distance(f.rgb, texture2D(uRepainted, at).rgb) > 0.06) {
                    return 0.0;
                }
                vec3 paint = f.rgb / f.a;
                float most = max(paint.r, max(paint.g, paint.b)), least = min(paint.r, min(paint.g, paint.b));
                bool grey = most > 0.34 && most - least < 0.3 * most;
                bool skyBlue = most > 0.45 && paint.b >= paint.g && paint.g > paint.r && most - least < 0.6 * most;
                return grey || skyBlue ? 1.0 : 0.0;
            }

            vec3 placeAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                vec2 ndc = uv * 2.0 - 1.0;
                return uEye + (uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y)) * ahead;
            }

            // Which way the surface at uv faces, from where its neighbours a few pixels off are.
            // Of the two neighbours along each line the nearer in depth is used: the other may
            // be past the figure's edge, on the wall behind it.
            vec3 normalAt(vec2 uv, vec3 at, float pixels) {
                vec2 step = pixels / uSize;
                vec3 east = placeAt(uv + vec2(step.x, 0.0)) - at, west = at - placeAt(uv - vec2(step.x, 0.0));
                vec3 north = placeAt(uv + vec2(0.0, step.y)) - at, south = at - placeAt(uv - vec2(0.0, step.y));
                vec3 across = dot(east, east) < dot(west, west) ? east : west;
                vec3 up = dot(north, north) < dot(south, south) ? north : south;
                vec3 square = cross(across, up);
                float size = length(square);
                return size > 1e-12 ? square / size : vec3(0.0);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 figure = texture2D(uFigure, uv);
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                // The steering wheel is found by walking each line of sight up to it.
                bool wheel = false;
                float wheelFar = 0.0;
                vec3 wheelFaces = vec3(0.0);
                if (uInside > 0.5 && uWheel.w > 0.0) {
                    vec3 d = normalize(ray);
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 line = vec3(dot(d.xy, uAhead), dot(d.xy, side), d.z);
                    float t = 0.2;
                    for (int i = 0; i < 48; i++) {
                        float gap = wheelAt(line * t);
                        if (gap < 0.0012) {
                            wheel = true;
                            break;
                        }
                        t += gap;
                        if (t > 1.4) {
                            break;
                        }
                    }
                    if (wheel) {
                        if (uGlassPass > 0.5) {
                            discard;
                        }
                        vec3 p = line * t;
                        vec2 e = vec2(0.002, 0.0);
                        vec3 slope = normalize(vec3(wheelAt(p + e.xyy) - wheelAt(p - e.xyy), wheelAt(p + e.yxy) - wheelAt(p - e.yxy), wheelAt(p + e.yyx) - wheelAt(p - e.yyx)));
                        wheelFaces = vec3(uAhead * slope.x + side * slope.y, slope.z);
                        wheelFar = t;
                    }
                }
                // Behind the wheel, two round instruments in the dash, seen through its rim.
                bool dial = false;
                vec2 dialAt = vec2(0.0);
                float dialFar = 0.0;
                if (!wheel && uInside > 0.5 && uWheel.w > 0.0 && figure.a > 0.5) {
                    vec3 d = normalize(ray);
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 line = vec3(dot(d.xy, uAhead), dot(d.xy, side), d.z);
                    vec3 rise = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                    vec3 middle = uWheel.xyz + uWheelAxis * 0.24 + rise * 0.105;
                    float towards = dot(line, uWheelAxis);
                    if (towards > 0.05) {
                        float t = dot(middle, uWheelAxis) / towards;
                        vec3 q = line * t - middle;
                        vec2 on = vec2(q.y, dot(q, rise));
                        // Two faces, side by side.
                        vec2 face = vec2(abs(on.x) - 0.075, on.y) / 0.068;
                        float z0 = texture2D(uDepth, uv).r * 2.0 - 1.0;
                        float behind = 2.0 * uNear * uFar / (uFar + uNear - z0 * (uFar - uNear)) * length(ray);
                        if (dot(face, face) < 1.0 && t < behind + 0.12) {
                            if (uGlassPass > 0.5) {
                                discard;
                            }
                            dial = true;
                            dialAt = face * vec2(on.x < 0.0 ? -0.47 : 0.47, 0.47) + 0.5;
                            dialFar = t;
                        }
                    }
                }
                if (figure.a < 0.5 && !wheel) {
                    // From a seat the vehicle is all round the eye, so where the game drew
                    // none of it above the sills there is an opening with a pane in it.
                    vec3 out3 = normalize(ray);
                    if (uGlassPass < 0.5 || out3.z < -0.3) {
                        discard;
                    }
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    float along = dot(out3.xy, uAhead), across = dot(out3.xy, side);
                    bool ends = abs(along) > abs(across);
                    // The screens lean back and the side windows stand nearly upright.
                    vec3 faces = ends ? normalize(vec3(-sign(along) * uAhead, 0.75)) : normalize(vec3(-sign(across) * side, 0.12));
                    float reach = ends ? 0.9 / max(abs(along), 0.2) : 0.5 / max(abs(across), 0.2);
                    vec3 to = out3 * reach;
                    gl_FragColor = pane(vec2(ends ? dot(to.xy, side) : dot(to.xy, uAhead) + 7.0, to.z), faces, -out3, ends && along > 0.0 ? 1.0 : 0.0);
                    gl_FragDepth = gl_FragCoord.z;
                    return;
                }
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                if (wheel) {
                    ahead = wheelFar / length(ray);
                }
                if (dial) {
                    ahead = dialFar / length(ray);
                }
                vec3 at = uEye + ray * ahead;

                // The game's models are few flat faces and the game lights them all alike, so
                // a body is a cut-out and a car a toy. Which way the surface faces is read
                // back off the picture's depth, close by for the fine shape and from further
                // off for the round of a limb or a wing, and the two together are smooth where
                // the model has edges. With that the sun lights one side and the sky the
                // other, the edge of a figure catches the sky, and paint and wet skin shine.
                vec3 view = normalize(uEye - at);
                // Right against the eye a figure's neighbouring pixels are at the same place and
                // say nothing of which way it faces: there it is taken to face the eye.
                // From the seat a panel is at arm's length and the depth it was drawn with
                // comes in steps finer than a pixel is wide: read close by, the steps show as
                // rings. Read from further off they do not.
                float close = uInside * step(ahead * length(ray), 3.2);
                vec3 found = close > 0.5 ? normalAt(uv, at, 5.0) + normalAt(uv, at, 11.0) : normalAt(uv, at, 1.5) + normalAt(uv, at, 4.0);
                vec3 n = length(found) > 0.1 ? normalize(found) : view;
                bool ghost = false;
                if (dial) {
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    n = -vec3(uAhead * uWheelAxis.x + side * uWheelAxis.y, uWheelAxis.z);
                    close = 1.0;
                    figure = vec4(texture2D(uDials, vec2(dialAt.x, 1.0 - dialAt.y)).rgb, 1.0);
                } else if (wheel) {
                    n = wheelFaces;
                    close = 1.0;
                    figure = vec4(0.21, 0.2, 0.195, 1.0);
                } else if (close > 0.5 && uWheel.w > 0.0) {
                    // The game's own six-sided wheel is still in its model, wider than the
                    // round one drawn over it. Where it shows it is painted out as the dark
                    // of the footwell behind it.
                    vec3 to = at - uEye;
                    vec3 q = vec3(dot(to.xy, uAhead), dot(to.xy, vec2(-uAhead.y, uAhead.x)), to.z) - uWheel.xyz;
                    float deep = dot(q, uWheelAxis);
                    float outward = length(q - uWheelAxis * deep);
                    ghost = abs(deep) < 0.07 && outward > uWheel.w - 0.07 && outward < uWheel.w + 0.07;
                    if (ghost) {
                        n = view;
                    }
                }
                // The models' skins are flat paint. Their light and dark say where the cloth
                // folds and the flesh is torn, and the surface is lit as raised where it is
                // light, so a shirt has creases the sun rakes across and not a picture of them.
                vec2 px = 1.0 / uSize;
                vec4 east = texture2D(uFigure, uv + vec2(px.x, 0.0)), west = texture2D(uFigure, uv - vec2(px.x, 0.0));
                vec4 north = texture2D(uFigure, uv + vec2(0.0, px.y)), south = texture2D(uFigure, uv - vec2(0.0, px.y));
                float cabin = close;
                if (cabin > 0.5) {
                    // The game draws a vehicle's skin without the smaller copies a texture
                    // needs to be seen at a slant, and from the seat every panel is at a slant:
                    // the weave of its trim comes out as rings of dots. Evened out over the
                    // pixels round about, it is cloth and plastic again.
                    vec4 sum = figure * 2.0 + east + west + north + south;
                    for (int i = 0; i < 4; i++) {
                        vec2 corner = vec2(i < 2 ? -1.0 : 1.0, mod(float(i), 2.0) < 0.5 ? -1.0 : 1.0);
                        sum += texture2D(uFigure, uv + corner * px * 1.5) + texture2D(uFigure, uv + corner.yx * vec2(-2.5, 2.5) * px) * 0.5;
                    }
                    if (sum.a > 6.0 && !wheel && !dial) {
                        figure = vec4(sum.rgb / sum.a, 1.0);
                    }
                    if (ghost) {
                        figure = vec4(0.085, 0.09, 0.105, 1.0);
                    }
                    // The game paints a cabin in flat colours. What it is made of is laid over
                    // them, fixed to the vehicle: grained vinyl on the dash, the doors and the
                    // wheel, woven cloth on the seats, which are the parts with colour in them.
                    vec3 to = at - uEye;
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 local = vec3(dot(to.xy, uAhead), dot(to.xy, side), to.z);
                    vec3 faces = abs(vec3(dot(n.xy, uAhead), dot(n.xy, side), n.z));
                    vec2 lie = faces.z > faces.x && faces.z > faces.y ? local.xy : faces.x > faces.y ? local.yz : local.xz;
                    float most = max(figure.r, max(figure.g, figure.b)), least = min(figure.r, min(figure.g, figure.b));
                    float woven = smoothstep(0.2, 0.35, (most - least) / max(most, 0.001)) * step(0.2, most);
                    vec3 grain = mix(texture2D(uVinyl, lie * 2.6).rgb * uCabinGain.x, texture2D(uCloth, lie * 11.0).rgb * uCabinGain.y, woven);
                    // Pale parts are painted metal, the pillars and the door tops, and have no grain.
                    if (!dial) {
                        figure.rgb *= mix(vec3(1.0), grain, mix(0.9 * (1.0 - smoothstep(0.4, 0.65, most)), 0.45, woven));
                    }
                } else if (min(min(east.a, west.a), min(north.a, south.a)) > 0.5) {
                    vec3 grey = vec3(0.299, 0.587, 0.114);
                    vec2 slope = vec2(dot(east.rgb - west.rgb, grey), dot(north.rgb - south.rgb, grey));
                    slope *= min(1.0, 0.25 / max(length(slope), 0.001));
                    n = normalize(n - (uRight * slope.x + uUp * slope.y) * 2.2);
                }
                if (dot(n, view) < 0.0) {
                    n = -n;
                }

                // Stepped a little towards the light: the character is in the shadow map itself,
                // and its lit side must not be shaded by its own skin.
                vec4 from = vec4(at + uLight * 0.18, 1.0);
                vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                float sun = 1.0;
                if (inside(c)) {
                    // Over a few places round the point: a shadow's edge across a face or a
                    // sleeve is soft.
                    float e = 2.0 / 4096.0;
                    sun = 0.2 * (shadow2D(uShadowNear, c).r + shadow2D(uShadowNear, c + vec3(e, e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, e, 0.0)).r
                            + shadow2D(uShadowNear, c + vec3(e, -e, 0.0)).r + shadow2D(uShadowNear, c + vec3(-e, -e, 0.0)).r);
                } else {
                    c = (uFarMatrix * from).xyz * 0.5 + 0.5;
                    if (inside(c)) {
                        sun = shadow2D(uShadowFar, c).r;
                    }
                }
                // The vehicle in the shadow map is the one the game draws from outside, and
                // from the seat its panels lie right on their own shadow: the sun comes and
                // goes across them in rings. Under its roof the cabin has the sky's light only.
                sun = mix(sun, 0.22, cabin);
                // From a seat the game's vehicle is all round the eye. Its windows are painted
                // on its one body, as panes that show the sky: pale and nearly without colour,
                // where the paint, the trim and the seats are dark or coloured. Above the sills,
                // what is pale and grey is glass and is looked through; the pillars, the frames
                // of the windows and the roof stay.
                if (cabin > 0.5 && at.z > uEye.z - 0.35 && !wheel && !dial) {
                    // Taken over the pixel and its neighbours a few pixels off: a pane's edge
                    // against a pillar is then a clean line and not the ragged one where each
                    // pixel's shade of sky falls one side or other of the rule.
                    // The window is painted on a skin of few dots, and its edge from the seat
                    // is a staircase a finger wide: asked over a width greater than a stair, the
                    // edge is the straight line the stairs follow.
                    // A stair is as wide on screen as the skin is near, so the width asked over
                    // follows the distance: broad on the pillar by the eye, narrow on the far door.
                    float wide = uSize.y / 720.0 * clamp(0.9 / (ahead * length(ray)), 0.5, 2.2);
                    float panes = glass(uv) * 2.0;
                    for (int i = 0; i < 8; i++) {
                        float turn = 0.7854 * float(i);
                        vec2 off = vec2(cos(turn), sin(turn)) * wide / uSize;
                        panes += glass(uv + off * 5.0) + glass(uv + off.yx * vec2(-11.0, 11.0)) + glass(uv + off * 17.0);
                    }
                    bool glazed = panes > 13.0;
                    if (uGlassPass < 0.5) {
                        if (glazed) {
                            discard;
                        }
                    } else {
                        if (!glazed) {
                            discard;
                        }
                        // The glass itself, over the world seen through it: the sky it mirrors,
                        // more at a glancing angle, and the dust and dried rain on it, which
                        // stay put on the pane as the car moves.
                        vec3 to = at - uEye;
                        vec2 side = vec2(-uAhead.y, uAhead.x);
                        float along = dot(to.xy, uAhead);
                        bool ends = abs(dot(n.xy, uAhead)) > abs(dot(n.xy, side));
                        gl_FragColor = pane(vec2(ends ? dot(to.xy, side) : along + 7.0, to.z), n, view, ends && along > 0.0 ? 1.0 : 0.0);
                        gl_FragDepth = gl_FragCoord.z;
                        return;
                    }
                } else if (uGlassPass > 0.5) {
                    discard;
                }
                vec3 sunLight = pow(uLightColor, vec3(2.2));
                float facing = clamp((dot(n, uLight) + 0.25) / 1.25, 0.0, 1.0);
                vec3 fromSky = mix(vec3(0.42, 0.40, 0.36), vec3(0.72, 0.80, 0.95), 0.5 + 0.5 * n.z);
                // The cabin is lit through its windows from every side, though its own roof
                // keeps the sun off it.
                // A cabin is lit through its windows by the sky there is: a great deal by day, next
                // to nothing on a dark night.
                float skyGives = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 lightHere = fromSky * mix(vec3(1.0), pow(uOwnAmbient, vec3(2.2)) * (2.6 * skyGives), cabin) * mix(pow(uFigureAmbient, 2.2) * 1.7 * skyGives, 1.0, cabin) + sunLight * (2.1 * sun * facing);
                vec3 color = pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * lightHere;
                float glance = pow(clamp(1.0 - dot(n, view), 0.0, 1.0), 3.0);
                if (uPainted < 0.5 && cabin < 0.5) {
                    // Skin and cloth let a little light through at the edge of the shade: the
                    // line between lit and unlit on a cheek or a shoulder is warm, not grey.
                    float edge = smoothstep(0.0, 0.25, facing) * (1.0 - smoothstep(0.25, 0.6, facing));
                    color += pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * sunLight * vec3(0.55, 0.16, 0.08) * (edge * sun);
                    // And the nap of cloth and the down on skin catch the sky all round the outline.
                    color += pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * fromSky * (0.9 * pow(clamp(1.0 - dot(n, view), 0.0, 1.0), 4.0));
                }
                // Paint under lacquer mirrors the sky all over, more at a glance, and throws
                // the sun back as a small hard light. Cloth and skin only catch the sky at
                // their edges.
                vec3 back = reflect(-view, n);
                vec3 skyBack = atmosphere(back * vec3(1.0, 1.0, sign(back.z)));
                color += pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * beams(at, n, 0.25) * (1.0 - cabin);
                if (dial && uInstruments > 0.0) {
                    // Lit from behind when the engine runs: the numerals and needles glow.
                    vec3 face = pow(figure.rgb, vec3(2.2));
                    color += face * vec3(0.55, 1.0, 0.8) * (3.5 * uInstruments * smoothstep(0.15, 0.6, max(face.r, max(face.g, face.b))));
                }
                if (cabin > 0.5 && uInstruments > 0.0) {
                    // With the engine running the instruments are lit, and in the dark their
                    // green light is on the wheel, the dash round them and the driver's knees.
                    vec3 dials = uEye + vec3(uAhead * 0.62, -0.46);
                    vec3 to = dials - at;
                    float far2 = dot(to, to);
                    float given = 0.05 / (0.02 + far2) * (0.25 + 0.75 * max(dot(n, to * inversesqrt(max(far2, 0.0001))), 0.0));
                    color += (pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * 0.6 + 0.12) * vec3(0.35, 1.0, 0.72) * (given * uInstruments);
                }
                // The trim of a cabin is cloth and dull plastic, whatever the body outside is.
                float lacquer = uPainted * (1.0 - cabin);
                color += skyBack * mix(0.22 * glance, 0.1 + 0.6 * glance, lacquer) * (1.0 - 0.7 * cabin);
                color += sunLight * (pow(max(dot(back, uLight), 0.0), mix(48.0, 260.0, lacquer)) * mix(0.45, 3.0, lacquer) * sun);

                float range = ahead * length(ray);
                float haze = max(smoothstep(uFogStart, uFogEnd, range), 1.0 - exp(-range * 0.0035));
                color = mix(color, atmosphere(normalize(ray)), haze);
                gl_FragColor = vec4(color, 1.0);
                // The viewer's own body and vehicle are drawn with a depth of their own. Here
                // it is put into the picture's depth, in the picture's own measure, so that
                // what is worked out from depth afterwards, the shade in corners and the
                // steadying over time, sees them and not the ground behind them.
                gl_FragDepth = uWriteNear > 0.0 ? 0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear) : gl_FragCoord.z;
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

            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uUp;

            const int RAY_SAMPLES = 48;
            const float WHITE = 3.2;
            const int MIRROR_STEPS = 40;

            // Where the pixel at uv is, seen from the eye: x to the right, y up, z ahead, in tiles.
            vec3 placeAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                return vec3((uv * 2.0 - 1.0) * uTan * ahead, ahead);
            }

            // What a wet or watery surface shows of the world. The world's own shader has put
            // the sky in it already. Here a ray is sent from the surface the way a mirror would
            // throw the eye's line, and walked through the picture's own depth: where it goes
            // behind something the picture shows, that thing is what the mirror shows there.
            // A ray that leaves the picture, or passes behind something too far to be sure of,
            // finds nothing and the sky stays.
            vec3 mirrored(vec2 uv, vec4 scene) {
                float mirror = 1.0 - scene.a;
                if (mirror < 0.02) {
                    return scene.rgb;
                }
                vec3 p = placeAt(uv);
                vec3 line = normalize(p);
                // Which way the mirror faces is read off the picture's depth: a floor faces up,
                // a window pane faces out of its wall.
                vec3 facing = normalize(cross(dFdx(p), dFdy(p)));
                if (dot(facing, line) > 0.0) {
                    facing = -facing;
                }
                if (dot(facing, uUp) > 0.9) {
                    facing = uUp;
                }
                vec3 thrown = reflect(line, facing);
                float glance = 0.04 + 0.96 * pow(1.0 - max(dot(-line, facing), 0.0), 5.0);
                float reach = 0.12 + 0.02 * p.z;
                float start = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                float along = reach * start;
                vec2 last = uv;
                for (int i = 0; i < MIRROR_STEPS; i++) {
                    along += reach;
                    reach *= 1.14;
                    vec3 q = p + thrown * along;
                    if (q.z <= uNear) {
                        break;
                    }
                    vec2 at = q.xy / (q.z * uTan) * 0.5 + 0.5;
                    if (at.x < 0.0 || at.x > 1.0 || at.y < 0.0 || at.y > 1.0) {
                        break;
                    }
                    last = at;
                    float behind = q.z - placeAt(at).z;
                    if (behind > 0.0) {
                        last = uv;
                        if (behind < 0.6 + reach * 1.5) {
                            float edge = min(min(at.x, 1.0 - at.x), min(at.y, 1.0 - at.y));
                            vec4 found = texture2D(uScene, at);
                            // A mirror seen in a mirror is the sky both times.
                            float sure = smoothstep(0.0, 0.08, edge) * step(0.5, found.a);
                            return mix(scene.rgb, found.rgb, min(1.0, glance * 1.6) * mirror * sure);
                        }
                        break;
                    }
                }
                // A ray that met nothing went on into the sky. The sky it went into is in the
                // picture at the last place the ray was seen, clouds and all, and that is what
                // the water shows: without this, water mirrors clouds far off and not near by,
                // with a line across it where the one gives way to the other.
                // Where in the picture the sky the ray went into is: the place its direction
                // points at, however far it went. Taken from where the ray was last seen, the
                // steps of the walk show as rings on the water.
                if (last != uv && thrown.z > 0.001) {
                    vec2 sky = thrown.xy / (thrown.z * uTan) * 0.5 + 0.5;
                    if (sky.x > 0.0 && sky.x < 1.0 && sky.y > 0.0 && sky.y < 1.0 && texture2D(uDepth, sky).r >= 0.9999995) {
                        float edge = min(min(sky.x, 1.0 - sky.x), 1.0 - sky.y);
                        return mix(scene.rgb, texture2D(uScene, sky).rgb, min(1.0, glance * 1.6) * mirror * smoothstep(0.0, 0.08, edge));
                    }
                }
                return scene.rgb;
            }

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
                // Film and the eye both lose colour as light gets strong: a sunlit wall is paler
                // than its paint, not a brighter version of it.
                c = mix(c, vec3(shown), 0.4 * smoothstep(0.55, 1.0, shown));
                // What is brighter than white in one colour alone spills into the others.
                float over = max(max(c.r, max(c.g, c.b)) - 1.0, 0.0);
                return clamp(c + over * 0.6, 0.0, 1.0);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 around = texture2D(uOcclusion, uv);
                vec4 here = texture2D(uScene, uv);
                vec3 c = mirrored(uv, here) * mix(1.0, around.a, 0.62);
                // The light lent by what stands near, taken on by the surface in its own colour.
                vec3 lent = around.rgb / max(1.0 - around.rgb, vec3(0.02));
                float own = max(here.r, max(here.g, here.b));
                c += lent * (here.rgb / max(own, 0.02)) * 0.22;
                if (uRayColor.r + uRayColor.g + uRayColor.b > 0.0) {
                    c += shafts(uv);
                }
                c += texture2D(uVolume, uv).rgb;
                c += flare(uv);
                c += texture2D(uBloom, uv).rgb * 0.045;
                c = film(c * uExposure);
                c = pow(c, vec3(1.0 / 2.2));
                c = mix(c, c * c * (3.0 - 2.0 * c), 0.18);
                // The game's sprites are painted in strong colours, to be read from far above.
                // Seen at eye level in daylight the world is quieter than that.
                c = mix(vec3(dot(c, vec3(0.2126, 0.7152, 0.0722))), c, 0.9);
                vec2 fromCentre = uv - 0.5;
                c *= 1.0 - dot(fromCentre, fromCentre) * 0.32;
                c += (hash(gl_FragCoord.xy + uTime * 61.0) - 0.5) / 160.0;
                gl_FragColor = vec4(c, 1.0);
            }
            """;

    /**
     * The picture steadied over time. Leaves in the wind, the grain of the soft shadows and of
     * the thin edges of cards all change from one frame to the next, and what changes every
     * frame is seen as flicker. Each pixel is mixed with what was at the same place in the
     * world a frame ago: where that place was on the last frame's picture is worked out from
     * the pixel's depth and where the eye then was. What the last frame had there is held to
     * the range of colours round the pixel now, so a thing that has moved leaves no ghost.
     */
    private static final String STEADY = """
            #version 120
            uniform sampler2D uScene;
            uniform sampler2D uBefore;
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uEye;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec3 uEyeBefore;
            uniform vec3 uRightBefore;
            uniform vec3 uUpBefore;
            uniform vec3 uForwardBefore;
            uniform float uKept;
            uniform float uOwnWithin;

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 now = texture2D(uScene, uv);
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 at = uEye + (uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y)) * ahead;
                vec3 from = at - uEyeBefore;
                float was = dot(from, uForwardBefore);
                vec2 before = vec2(dot(from, uRightBefore) / (was * uTan.x), dot(from, uUpBefore) / (was * uTan.y)) * 0.5 + 0.5;
                // The vehicle the viewer rides in goes with the eye: where it was in the world a
                // frame ago says nothing of where it was in the picture.
                if (uKept <= 0.0 || ahead < uOwnWithin || was <= uNear || before.x < 0.0 || before.x > 1.0 || before.y < 0.0 || before.y > 1.0) {
                    gl_FragColor = now;
                    return;
                }
                vec3 least = now.rgb, most = now.rgb;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        vec3 near = texture2D(uScene, uv + vec2(float(x), float(y)) / uSize).rgb;
                        least = min(least, near);
                        most = max(most, near);
                    }
                }
                vec3 held = clamp(texture2D(uBefore, before).rgb, least, most);
                gl_FragColor = vec4(mix(now.rgb, held, uKept), now.a);
            }
            """;

    private static final String COPY = """
            #version 120
            uniform sampler2D uSource;
            uniform vec2 uSize;
            void main() {
                gl_FragColor = texture2D(uSource, gl_FragCoord.xy / uSize);
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

        void set(String name, float x, float y, float z, float w) {
            GL20.glUniform4f(at(name), x, y, z, w);
        }

        void set(String name, float[] matrix) {
            GL20.glUniformMatrix4fv(at(name), false, matrix);
        }
    }

    /** Why the picture cannot be drawn on this machine, or null. Set once, on the render thread. */
    private static volatile String failure;
    private static boolean prepared;
    private static Program rain;
    private static Program steady, copy;
    /** The steadied picture of the last frame and of this one, taking turns. */
    private static final int[] keptColor = new int[2], keptTarget = new int[2];
    private static int keptNow;
    private static boolean keptAny;
    private static final ViewCamera.Pose before = new ViewCamera.Pose();
    /** How much of the last frame's picture goes into this one. */
    private static final float KEPT = 0.86f;
    /** Where in its pixel each frame of the cycle is drawn, from the middle, in pixels times two: the Halton points of bases 2 and 3. */
    private static final float[] JITTER_X = {0f, -0.5f, 0.5f, -0.75f, 0.25f, -0.25f, 0.75f, -0.875f};
    private static final float[] JITTER_Y = {-0.333f, 0.333f, -0.778f, -0.111f, 0.556f, -0.556f, 0.111f, 0.778f};
    private static int jitterAt;
    private static Program world, shadow, figureLight, sky, clouds, occlusion, soften, volume, bloomDown, bloomUp, develop, smooth;
    private static int whiteTexture, noiseTexture;
    private static int nearMap, farMap, nearTarget, farTarget;
    /** What is overhead near the eye, seen from straight above: where rain cannot fall. */
    private static int roofMap, roofTarget;
    private static final float[] roofMatrix = new float[16];
    private static final float ROOF_EXTENT = 20f, ROOF_RANGE = 40f;
    private static final int RAIN_DROPS = 9000;
    private static FloatBuffer rainDrops;
    /**
     * What open ground is made of, as pictures of the stuff itself that join up with themselves
     * at their edges: tarmac, gravel, earth, grass. They are laid over the ground by where it
     * is on the map, so the game's square of ground repeated a thousand times becomes one
     * unbroken surface. Made for this mod and shipped in its jar.
     */
    private static final String[] MATERIALS = {"asphalt", "gravel", "soil", "grass", "shingles", "paint", "wood", "bark", "planks", "carpet", "leaves", "vinyl", "cloth", "dials"};
    /** The materials from here on are cut-outs: four bytes a pixel, the last how solid it is. */
    private static final int FIRST_CUTOUT = 10;
    private static final int MATERIAL_SIZE = 1024;
    private static final int FIRST_MATERIAL_UNIT = 3;
    private static final int[] materialTexture = new int[MATERIALS.length];
    /** What each material's picture is multiplied by to be 1 on average: it changes the ground's texture, not how light it is. */
    private static final float[] materialGain = new float[MATERIALS.length];
    private static int width, height;
    private static int sceneTarget, sceneColor, sceneDepth, shownTarget, shownColor, figureTarget, figureColor;
    /** The viewer's own body, drawn apart from everything else: see Characters.drawViewer. */
    private static int ownTarget, ownColor, ownDepth;
    private static int repaintedTarget, repaintedColor, repaintedDepth;
    private static final float OWN_NEAR = 0.3f;
    private static int occlusionTarget, occlusionColor, softTarget, softColor;
    private static int volumeTarget, volumeColor, softVolumeTarget, softVolumeColor;
    private static int spriteSampler;
    private static int skinSampler;
    private static boolean glazed;
    private static final int SKIN_UNITS = 8;
    private static int depthSampler;
    /** How wide the sun is, as the width of a shadow's soft edge for each tile between caster and ground. Wider than the real sun's: the real one's edge is too fine to read at this scale. */
    private static final float SUN_WIDTH = 0.06f;
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
    private static final float[] beamMatrix = new float[16];
    private static int beamMap, beamTarget;
    private static final ViewCamera.Pose lamp = new ViewCamera.Pose();
    /** Half the angle the headlights' shadow map takes in, as a tangent, and how near and far it sees. */
    private static final float BEAM_TAN = 0.84f, BEAM_NEAR = 0.35f, BEAM_FAR = 32f;
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
            glazed = false;
            drawShadows(state, camera, figures);
            stage(STAGE_SHADOWS);
            drawWorld(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            stage(STAGE_WORLD);
            if (figures) {
                drawFigures(camera, state, tanHalfFieldOfView, aspect, near, far, fogStart, fogEnd);
            }
            stage(STAGE_FIGURES);
            drawSky(camera, state, tanHalfFieldOfView, aspect);
            if (state.rain > 0f) {
                drawRain(camera, state, tanHalfFieldOfView, aspect, near, far);
            }
            if (glazed) {
                drawGlass();
            }
            drawSteady(camera, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_SKY);
            drawOcclusion(camera, state, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_OCCLUSION);
            drawVolume(camera, state, tanHalfFieldOfView, aspect, near, far);
            stage(STAGE_AIR);
            drawBloom(exposure);
            stage(STAGE_GLOW);
            drawDeveloped(camera, state, tanHalfFieldOfView, aspect, exposure, near, far);
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

    /** Draws the ranges telling the program the size of a texel of each one's texture. */
    private static void drawRanges(int from, int to, Program program) {
        for (int i = from; i < to; i++) {
            GL11.glBindTexture(GL_TEXTURE_2D, rangeTexture[i]);
            program.set("uTexel", 1f / GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_WIDTH),
                    1f / GL11.glGetTexLevelParameteri(GL_TEXTURE_2D, 0, GL_TEXTURE_HEIGHT));
            GL11.glDrawArrays(GL_TRIANGLES, rangeFirst[i], rangeCount[i]);
        }
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
        // The scene as the nearest lit headlights see it, so that what stands in their beam
        // throws its shadow down the road.
        float[] beams = Headlights.beams;
        if (beams[6] > 0f) {
            lamp.eyeX = beams[0];
            lamp.eyeY = beams[1];
            lamp.eyeUp = beams[2];
            lamp.forwardX = beams[3];
            lamp.forwardY = beams[4];
            lamp.forwardUp = beams[5];
            float flat = (float) Math.hypot(beams[3], beams[4]);
            lamp.rightX = beams[4] / flat;
            lamp.rightY = -beams[3] / flat;
            lamp.rightUp = 0f;
            lamp.upX = lamp.rightY * lamp.forwardUp - lamp.rightUp * lamp.forwardY;
            lamp.upY = lamp.rightUp * lamp.forwardX - lamp.rightX * lamp.forwardUp;
            lamp.upUp = lamp.rightX * lamp.forwardY - lamp.rightY * lamp.forwardX;
            float[] seen = ViewCamera.view(lamp), cast = ViewCamera.projection(BEAM_TAN, 1f, BEAM_NEAR, BEAM_FAR);
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float sum = 0f;
                    for (int k = 0; k < 4; k++) {
                        sum += cast[k * 4 + row] * seen[column * 4 + k];
                    }
                    beamMatrix[column * 4 + row] = sum;
                }
            }
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, beamTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.use();
            shadow.set("uMatrix", beamMatrix);
            GL13.glActiveTexture(GL_TEXTURE0);
            drawRanges(0, solidRanges);
            if (figures) {
                Characters.castCharacters(beamMatrix, camera);
                arrays();
                GL13.glActiveTexture(GL_TEXTURE0);
                GL11.glDisable(GL_CULL_FACE);
                GL11.glDisable(GL_BLEND);
                GL11.glDisable(GL_ALPHA_TEST);
                GL11.glEnable(GL_DEPTH_TEST);
                GL11.glDepthFunc(GL_LEQUAL);
                GL11.glDepthMask(true);
            }
        }
        GL13.glActiveTexture(GL_TEXTURE0 + BEAM_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, beamMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        // Drawn whatever the weather: the rain is kept off by it, and the light of the sky is
        // traced against it.
        {
            float texel = 2f * ROOF_EXTENT / SHADOW_SIZE;
            java.util.Arrays.fill(roofMatrix, 0f);
            roofMatrix[0] = 1f / ROOF_EXTENT;
            roofMatrix[12] = -(float) Math.floor(camera.eyeX / texel) * texel / ROOF_EXTENT;
            roofMatrix[5] = 1f / ROOF_EXTENT;
            roofMatrix[13] = -(float) Math.floor(camera.eyeY / texel) * texel / ROOF_EXTENT;
            roofMatrix[10] = -1f / ROOF_RANGE;
            roofMatrix[14] = camera.eyeUp / ROOF_RANGE;
            roofMatrix[15] = 1f;
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, roofTarget);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_DEPTH_BUFFER_BIT);
            shadow.use();
            shadow.set("uMatrix", roofMatrix);
            drawRanges(0, solidRanges);
        }
        GL11.glDisable(GL_POLYGON_OFFSET_FILL);
        GL11.glColorMask(true, true, true, true);
    }

    private static void drawSteady(ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far) {
        int next = 1 - keptNow;
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(false);
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, keptTarget[next]);
        GL11.glViewport(0, 0, width, height);
        steady.use();
        steady.set("uScene", 0);
        steady.set("uBefore", 1);
        steady.set("uDepth", 2);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, keptColor[keptNow]);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        steady.set("uSize", (float) width, (float) height);
        steady.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        steady.set("uNear", near);
        steady.set("uFar", far);
        steady.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        steady.set("uRight", camera.rightX, camera.rightY, camera.rightUp);
        steady.set("uUp", camera.upX, camera.upY, camera.upUp);
        steady.set("uForward", camera.forwardX, camera.forwardY, camera.forwardUp);
        steady.set("uEyeBefore", before.eyeX, before.eyeY, before.eyeUp);
        steady.set("uRightBefore", before.rightX, before.rightY, before.rightUp);
        steady.set("uUpBefore", before.upX, before.upY, before.upUp);
        steady.set("uForwardBefore", before.forwardX, before.forwardY, before.forwardUp);
        steady.set("uKept", keptAny ? KEPT : 0f);
        steady.set("uOwnWithin", FirstPerson.seated ? 3.2f : 0f);
        quad();
        // The rest of the frame is made from the steadied picture.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        copy.use();
        GL11.glBindTexture(GL_TEXTURE_2D, keptColor[next]);
        copy.set("uSource", 0);
        copy.set("uSize", (float) width, (float) height);
        quad();
        keptNow = next;
        keptAny = true;
        before.set(camera);
        GL11.glDepthMask(true);
    }

    /** The rain, into the picture after the world and the sky and before anything is made of the picture. */
    private static void drawRain(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(ViewCamera.projection(tanHalfFieldOfView, aspect, near, far));
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(ViewCamera.view(camera));
        arrays();
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glVertexPointer(4, GL_FLOAT, 0, rainDrops);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // Added to what is there. The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ZERO, GL_ONE);
        rain.use();
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        rain.set("uRoof", 0);
        rain.set("uRoofMatrix", roofMatrix);
        rain.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        rain.set("uTime", state.seconds);
        rain.set("uWind", state.windX, state.windY);
        rain.set("uRain", state.rain);
        rain.set("uSeated", FirstPerson.seated ? 1f : 0f);
        setBeams(rain);
        // A drop shows the sky it falls through.
        rain.set("uTint", 1.6f * (float) Math.pow(state.horizonRed, 2.2), 1.6f * (float) Math.pow(state.horizonGreen, 2.2),
                1.6f * (float) Math.pow(state.horizonBlue, 2.2));
        GL11.glDrawArrays(GL_TRIANGLES, 0, RAIN_DROPS * 6);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
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
        // Each frame the world is drawn a fraction of a pixel to one side of the last, a
        // different way each time round a short cycle. The steadying pass mixes the frames,
        // and an edge that falls between pixels comes out as the mean of where it fell: smooth,
        // and with more detail than one frame's pixels hold.
        float[] projection = ViewCamera.projection(tanHalfFieldOfView, aspect, near, far).clone();
        jitterAt = (jitterAt + 1) % JITTER_X.length;
        projection[8] += JITTER_X[jitterAt] / width;
        projection[9] += JITTER_Y[jitterAt] / height;
        Characters.aside(JITTER_X[jitterAt] / width, JITTER_Y[jitterAt] / height);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadMatrixf(projection);
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadMatrixf(ViewCamera.view(camera));

        world.use();
        world.set("uTexture", 0);
        world.set("uShadowNear", 1);
        world.set("uShadowFar", 2);
        for (int material = 0; material < MATERIALS.length; material++) {
            GL13.glActiveTexture(GL_TEXTURE0 + FIRST_MATERIAL_UNIT + material);
            GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[material]);
        }
        world.set("uAsphalt", FIRST_MATERIAL_UNIT);
        world.set("uGravel", FIRST_MATERIAL_UNIT + 1);
        world.set("uSoil", FIRST_MATERIAL_UNIT + 2);
        world.set("uLawn", FIRST_MATERIAL_UNIT + 3);
        world.set("uMaterialGain", materialGain[0], materialGain[1], materialGain[2], materialGain[3]);
        world.set("uShingles", FIRST_MATERIAL_UNIT + 4);
        world.set("uPaint", FIRST_MATERIAL_UNIT + 5);
        world.set("uWood", FIRST_MATERIAL_UNIT + 6);
        world.set("uSurfaceGain", materialGain[4], materialGain[5], materialGain[6]);
        world.set("uBark", FIRST_MATERIAL_UNIT + 7);
        world.set("uPlanks", FIRST_MATERIAL_UNIT + 8);
        world.set("uCarpet", FIRST_MATERIAL_UNIT + 9);
        world.set("uTimberGain", materialGain[7], materialGain[8], materialGain[9]);
        world.set("uLeaves", FIRST_MATERIAL_UNIT + 10);
        world.set("uLeafGain", materialGain[10]);
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL13.glActiveTexture(GL_TEXTURE0 + 2);
        GL11.glBindTexture(GL_TEXTURE_2D, farMap);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uNearMatrix", nearMatrix);
        world.set("uFarMatrix", farMatrix);
        world.set("uShadowTexel", 1f / SHADOW_SIZE);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 2);
        GL11.glBindTexture(GL_TEXTURE_2D, nearMap);
        GL33.glBindSampler(UNITS - 2, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uShadowDepth", UNITS - 2);
        GL13.glActiveTexture(GL_TEXTURE0 + UNITS - 1);
        GL11.glBindTexture(GL_TEXTURE_2D, roofMap);
        GL33.glBindSampler(UNITS - 1, depthSampler);
        GL13.glActiveTexture(GL_TEXTURE0);
        world.set("uHeights", UNITS - 1);
        world.set("uRoofMatrix", roofMatrix);
        world.set("uCover", state.cover);
        world.set("uDrift", state.driftX, state.driftY);
        // Texels of soft edge for each unit of the map's depth between caster and ground.
        world.set("uSoftPerDepth", 2f * NEAR_RANGE * SUN_WIDTH * SHADOW_SIZE / (2f * NEAR_EXTENT));
        world.set("uEye", camera.eyeX, camera.eyeY, camera.eyeUp);
        world.set("uLight", state.lightX, state.lightY, state.lightUp);
        setBeams(world);
        world.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
        world.set("uFogStart", fogStart);
        world.set("uFogEnd", fogEnd);
        world.set("uWind", state.windX, state.windY, state.seconds);
        world.set("uWet", state.wet);
        world.set("uSeconds", state.seconds);
        world.set("uRaining", state.rain);
        world.set("uDark", state.night);
        atmosphere(world, state);
        world.set("uCut", SOLID_CUT);
        world.set("uLaid", 0f);
        drawRanges(0, solidRanges, world);
        // What is laid over the floors, mixed in by how see-through each pixel is, and the grass
        // that stands on them.
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        world.set("uCut", 0.02f);
        world.set("uLaid", 1f);
        world.set("uTexel", 0f, 0f);
        drawRanges(solidRanges, ranges);
        GL11.glDisable(GL_BLEND);
        GL11.glDepthMask(true);
        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(UNITS - 2, 0);
        GL33.glBindSampler(UNITS - 1, 0);

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

    private static void drawOcclusion(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float near, float far) {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, occlusionTarget);
        GL11.glViewport(0, 0, levelWidth[0], levelHeight[0]);
        occlusion.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneDepth);
        occlusion.set("uDepth", 0);
        occlusion.set("uScene", 1);
        // The way to the light as the eye sees it: across, up and ahead. With no light above
        // the horizon there is nothing to trace towards.
        if (state.lightUp > 0.02f) {
            occlusion.set("uSunEye", state.lightX * camera.rightX + state.lightY * camera.rightY + state.lightUp * camera.rightUp,
                    state.lightX * camera.upX + state.lightY * camera.upY + state.lightUp * camera.upUp,
                    state.lightX * camera.forwardX + state.lightY * camera.forwardY + state.lightUp * camera.forwardUp);
        } else {
            occlusion.set("uSunEye", 0f, 0f, -3f);
        }
        occlusion.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        occlusion.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        occlusion.set("uNear", near);
        occlusion.set("uFar", far);
        quad();
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, softTarget);
        soften.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, occlusionColor);
        GL13.glActiveTexture(GL_TEXTURE0);
        soften.set("uSource", 1);
        soften.set("uDepth", 0);
        soften.set("uNear", near);
        soften.set("uFar", far);
        soften.set("uSize", (float) levelWidth[0], (float) levelHeight[0]);
        quad();
        GL13.glActiveTexture(GL_TEXTURE0 + 1);
        GL11.glBindTexture(GL_TEXTURE_2D, sceneColor);
        GL13.glActiveTexture(GL_TEXTURE0);
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
        setBeams(volume);
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

    private static void drawDeveloped(ViewCamera.Pose camera, SkyState state, float tanHalfFieldOfView, float aspect, float exposure,
            float near, float far) {
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
        develop.set("uTan", tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        develop.set("uNear", near);
        develop.set("uFar", far);
        // The way up in the world, as the eye sees it: across, up and ahead.
        develop.set("uUp", camera.rightUp, camera.upUp, camera.forwardUp);
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
        // Flesh and cloth first, then painted metal, which is lit as lacquer.
        for (int pass = 0; pass < 2; pass++) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, figureTarget);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL_COLOR_BUFFER_BIT);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDepthMask(true);
            GL11.glDisable(GL_BLEND);
            smoothSkins(true);
            Characters.draw(camera, tanHalfFieldOfView, aspect, near, far, pass == 1);
            smoothSkins(false);
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
            setBeams(figureLight);
            figureLight.set("uLightColor", state.lightRed, state.lightGreen, state.lightBlue);
            figureLight.set("uFogStart", fogStart);
            figureLight.set("uFogEnd", fogEnd);
            figureLight.set("uWriteNear", 0f);
            // Only the viewer's own vehicle is cut away from inside, not who stands beside it.
            figureLight.set("uInside", 0f);
            figureLight.set("uOwnAmbient", 1f, 1f, 1f);
            figureLight.set("uFigureAmbient", Characters.ambient);
            atmosphere(figureLight, state);
            figureLight.set("uPainted", pass == 1 ? 1f : 0f);
            quad();
        }

        if (FirstPerson.seated) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, repaintedTarget);
            GL11.glDepthMask(true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClearDepth(1.0);
            GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_BLEND);
            Characters.repainted = true;
            smoothSkins(true);
            Characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
            smoothSkins(false);
            Characters.repainted = false;
        }
        // The viewer's own body, the same way: drawn by the game, lit here.
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, ownTarget);
        GL11.glDepthMask(true);
        GL11.glClearColor(0f, 0f, 0f, 0f);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDisable(GL_BLEND);
        smoothSkins(FirstPerson.seated);
        boolean own = Characters.drawViewer(camera, tanHalfFieldOfView, aspect, OWN_NEAR, far);
        smoothSkins(false);
        Characters.done();
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL11.glDisable(GL_ALPHA_TEST);
        GL11.glDepthMask(false);
        if (own) {
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
            figureLight.use();
            GL13.glActiveTexture(GL_TEXTURE0 + 4);
            GL11.glBindTexture(GL_TEXTURE_2D, repaintedColor);
            figureLight.set("uRepainted", 4);
            GL13.glActiveTexture(GL_TEXTURE0 + 3);
            GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
            GL13.glActiveTexture(GL_TEXTURE0);
            GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
            figureLight.set("uNear", OWN_NEAR);
            figureLight.set("uWriteNear", near);
            figureLight.set("uInside", FirstPerson.seated ? 1f : 0f);
            figureLight.set("uVinyl", FIRST_MATERIAL_UNIT + 11);
            figureLight.set("uCloth", FIRST_MATERIAL_UNIT + 12);
            figureLight.set("uDials", FIRST_MATERIAL_UNIT + 13);
            figureLight.set("uCabinGain", materialGain[11], materialGain[12]);
            figureLight.set("uOwnAmbient", Characters.ownAmbient[0], Characters.ownAmbient[1], Characters.ownAmbient[2]);
            GL11.glEnable(GL_DEPTH_TEST);
            GL11.glDepthFunc(GL_ALWAYS);
            GL11.glDepthMask(true);
            figureLight.set("uGlassPass", 0f);
            figureLight.set("uInstruments", FirstPerson.instrumentsLit ? 0.9f * state.night : 0f);
            float[] wheel = FirstPerson.seated ? Cabins.wheel(FirstPerson.seatedIn) : null;
            if (wheel == null) {
                figureLight.set("uWheel", 0f, 0f, 0f, 0f);
            } else {
                figureLight.set("uWheel", wheel[0], wheel[1], wheel[2], wheel[3]);
                figureLight.set("uWheelAxis", wheel[4], wheel[5], wheel[6]);
            }
            figureLight.set("uRaining", state.rain);
            figureLight.set("uTime", state.seconds);
            figureLight.set("uAhead", FirstPerson.aheadX, FirstPerson.aheadY);
            quad();
            glazed = FirstPerson.seated;
            GL11.glDepthFunc(GL_LEQUAL);
            GL11.glDisable(GL_DEPTH_TEST);
        }
        GL11.glDepthMask(true);
    }

    /**
     * The game draws a vehicle's skin with each of its dots a hard square, which is right from
     * its own distance. From the seat a dot is a hand across and the edge of a painted window
     * is a staircase, so there the skin is read with its dots run together.
     */
    private static void smoothSkins(boolean smooth) {
        for (int unit = 0; unit < SKIN_UNITS; unit++) {
            GL33.glBindSampler(unit, smooth ? skinSampler : 0);
        }
    }

    /**
     * The panes of the vehicle the viewer sits in, over everything seen through them: the
     * world, the sky and the rain, which are all outside the glass.
     */
    private static void drawGlass() {
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, sceneTarget);
        GL11.glViewport(0, 0, width, height);
        GL11.glMatrixMode(GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glMatrixMode(GL_MODELVIEW);
        GL11.glLoadIdentity();
        figureLight.use();
        GL13.glActiveTexture(GL_TEXTURE0 + 4);
        GL11.glBindTexture(GL_TEXTURE_2D, repaintedColor);
        GL13.glActiveTexture(GL_TEXTURE0 + 3);
        GL11.glBindTexture(GL_TEXTURE_2D, ownDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, ownColor);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDepthMask(false);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        figureLight.set("uGlassPass", 1f);
        quad();
        figureLight.set("uGlassPass", 0f);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
    }

    /** How bright a headlight is beside the sun at 2.1. */
    private static final float BEAM_STRENGTH = 9f;

    private static void setBeams(Program program) {
        float[] beams = Headlights.beams;
        for (int i = 0; i < Headlights.BEAMS; i++) {
            int at = i * Headlights.STRIDE;
            program.set("uBeamAt[" + i + "]", beams[at], beams[at + 1], beams[at + 2], beams[at + 6] * BEAM_STRENGTH);
            program.set("uBeamWay[" + i + "]", beams[at + 3], beams[at + 4], beams[at + 5]);
        }
        program.set("uBeamShadow", BEAM_UNIT);
        program.set("uBeamMatrix", beamMatrix);
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
        rain = program("rain", RAIN_VERTEX, RAIN_FRAGMENT, problem);
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
        steady = program("steady", SCREEN_VERTEX, STEADY, problem);
        copy = program("copy", SCREEN_VERTEX, COPY, problem);
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

        for (int material = 0; material < MATERIALS.length; material++) {
            String missing = loadMaterial(material);
            if (missing != null) {
                return missing;
            }
        }

        // Reads a shadow map's depth as a number, where the map's own settings compare it.
        depthSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        GL33.glSamplerParameteri(depthSampler, GL_TEXTURE_COMPARE_MODE, GL_NONE);

        skinSampler = GL33.glGenSamplers();
        GL33.glSamplerParameteri(skinSampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        GL33.glSamplerParameteri(skinSampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL33.glSamplerParameteri(skinSampler, GL_TEXTURE_WRAP_S, GL_REPEAT);
        GL33.glSamplerParameteri(skinSampler, GL_TEXTURE_WRAP_T, GL_REPEAT);
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
        String roof = shadowMap(made);
        roofMap = made[0];
        roofTarget = made[1];
        String beam = shadowMap(made);
        beamMap = made[0];
        beamTarget = made[1];
        if (near != null || far != null || roof != null || beam != null) {
            return near != null ? near : far != null ? far : roof != null ? roof : beam;
        }
        // Where each drop starts in its box of air, and which corner of its streak each of its
        // six vertices is. The same every run: rain needs no surprises.
        rainDrops = ByteBuffer.allocateDirect(RAIN_DROPS * 6 * 4 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        java.util.Random random = new java.util.Random(1987);
        float[] corners = {0f, 1f, 2f, 0f, 2f, 3f};
        for (int drop = 0; drop < RAIN_DROPS; drop++) {
            float x = random.nextFloat(), y = random.nextFloat(), z = random.nextFloat();
            for (float corner : corners) {
                rainDrops.put(x).put(y).put(z).put(corner);
            }
        }
        rainDrops.flip();
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
        for (int i = 0; i < 2; i++) {
            keptColor[i] = texture(GL_RGBA16F, width, height, GL_RGBA, GL_FLOAT, GL_LINEAR);
            keptTarget[i] = target(keptColor[i]);
        }
        keptAny = false;
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
        repaintedColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        repaintedDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        repaintedTarget = target(repaintedColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, repaintedDepth, 0);
        if (problem == null) {
            problem = complete("repainted vehicle");
        }
        ownColor = texture(GL_RGBA8, width, height, GL_RGBA, GL_UNSIGNED_BYTE, GL_NEAREST);
        ownDepth = texture(GL_DEPTH_COMPONENT24, width, height, GL_DEPTH_COMPONENT, GL_FLOAT, GL_NEAREST);
        ownTarget = target(ownColor);
        GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, ownDepth, 0);
        if (problem == null) {
            problem = complete("viewer");
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

    /** Reads a material's picture out of the jar and gives it to the graphics card. Null, or what went wrong. */
    private static String loadMaterial(int material) {
        boolean cutout = material == FIRST_CUTOUT;
        int each = cutout ? 4 : 3;
        String path = "/materials/" + MATERIALS[material] + (cutout ? ".rgba.gz" : ".rgb.gz");
        ByteBuffer pixels = ByteBuffer.allocateDirect(MATERIAL_SIZE * MATERIAL_SIZE * each);
        long light = 0;
        long solid;
        try (java.io.InputStream packed = Pipeline.class.getResourceAsStream(path)) {
            if (packed == null) {
                return "the jar has no " + path;
            }
            byte[] bytes = new java.util.zip.GZIPInputStream(packed).readAllBytes();
            if (bytes.length != pixels.capacity()) {
                return path + " holds " + bytes.length + " bytes, not " + pixels.capacity();
            }
            long counted = 0;
            for (int i = 0; i < bytes.length; i += each) {
                // A cut-out's mean is over what is solid.
                if (!cutout || (bytes[i + 3] & 0xff) > 127) {
                    light += 299 * (bytes[i] & 0xff) + 587 * (bytes[i + 1] & 0xff) + 114 * (bytes[i + 2] & 0xff);
                    counted++;
                }
            }
            solid = counted;
            pixels.put(bytes).flip();
        } catch (java.io.IOException e) {
            return path + " could not be read: " + e;
        }
        materialGain[material] = 255f * 1000f * solid / light;
        materialTexture[material] = GL11.glGenTextures();
        GL11.glBindTexture(GL_TEXTURE_2D, materialTexture[material]);
        GL11.glTexImage2D(GL_TEXTURE_2D, 0, cutout ? GL_RGBA8 : GL_RGB8, MATERIAL_SIZE, MATERIAL_SIZE, 0, cutout ? GL_RGBA : GL_RGB, GL_UNSIGNED_BYTE, pixels);
        GL30.glGenerateMipmap(GL_TEXTURE_2D);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
        GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
        return null;
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
