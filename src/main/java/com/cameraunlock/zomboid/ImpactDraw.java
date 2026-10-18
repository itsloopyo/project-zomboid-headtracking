package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Draws where shots struck (Impacts) into one view's picture: the holes they left, laid over
 * the surfaces as they are drawn, and what flies out of each surface for the first second or
 * two after a shot.
 *
 * What flies is lit by the surface it came from, as the picture drew that surface where the
 * shot struck in the first frame after it: dust and chips are of the stuff of the wall, in the
 * light the wall is in, and fog over the wall is over them. Blood is lit by the same light. Sparks
 * give light of their own. A hole darkens and lightens the surface under it, so it takes the
 * surface's light and colour as they are.
 *
 * Render thread.
 */
final class ImpactDraw {
    /**
     * What flies out of a surface of one kind, a group of it: pieces, puffs or sparks.
     *
     * @param count      how many, for a handgun's round
     * @param speed      how fast they leave, least and most, in tiles a second
     * @param spread     how far from the way out each goes its own way: 0 none, 1 anywhere in front
     * @param drag       how fast the air slows them, a time constant's reciprocal
     * @param out        the way out: how much of it is along the face's normal, along the shot's glance off the face, and along the shot's own way
     * @param keptOut    whether none goes back into the surface
     * @param life       how long each lasts, least and most, in seconds
     * @param fall       how much of gravity pulls them down
     * @param rests      whether they come to rest on the floor under them
     * @param size       half their width, least and most, in tiles, for a handgun's round
     * @param grow       how much wider a puff gets each second, as a share of what it was
     * @param streak     a spark or a drop is drawn as long as it goes in this many seconds; 0 for none
     * @param longer     how many times longer than wide a piece is: a splinter's
     * @param tint       its colour against the surface's, or blood's colour where blood is set
     * @param alpha      how much of what is behind it it hides
     * @param blood      whether its colour is blood's, lit as the surface was
     * @param glow       light of its own, in the picture's measure of light, which the exposure develops
     * @param puff       whether it is a soft round puff and not a hard piece
     */
    record Spray(int count, float speedLeast, float speedMost, float spread, float drag, float outNormal, float outGlance, float outWay,
            boolean keptOut, float lifeLeast, float lifeMost, float fall, boolean rests, float sizeLeast, float sizeMost, float grow,
            float streak, float longer, float tintRed, float tintGreen, float tintBlue, float alpha, boolean blood,
            float glowRed, float glowGreen, float glowBlue, boolean puff) {}

    private static final int PIECES = 0, PUFFS = 1, SPARKS = 2, GROUPS = 3;
    /** The most of each group one impact has, and where each group's begin in the vertices. */
    static final int[] MOST = {32, 12, 24};
    private static final int[] FIRST = {0, 32, 44};
    static final int PARTICLES = 68;

    private static Spray chips(float speed, float life, float size, float longer, float tint, int count) {
        return new Spray(count, 0.6f * speed, speed, 0.7f, 0.8f, 1f, 0.45f, 0f, true, 0.7f * life, life, 1f, true, 0.45f * size, size, 0f,
                0f, longer, tint, tint, tint, 1f, false, 0f, 0f, 0f, false);
    }

    private static Spray dust(float speed, float life, float size, float grow, float tint, float alpha, int count) {
        return new Spray(count, 0.3f * speed, speed, 0.6f, 3.5f, 1f, 0.3f, 0f, true, 0.55f * life, life, 0.05f, false, 0.55f * size, size, grow,
                0f, 1f, tint, tint, tint, alpha, false, 0f, 0f, 0f, true);
    }

    private static final Spray NOTHING = new Spray(0, 0f, 0f, 0f, 1f, 1f, 0f, 0f, true, 1f, 1f, 0f, false, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, false,
            0f, 0f, 0f, false);

    /** For each kind of surface in Impacts, what flies out of it: pieces, puffs and sparks. */
    static final Spray[][] SPRAYS = new Spray[Impacts.KINDS][];

    static {
        // Brick, block, plaster, concrete: chips of the face and a cloud of its dust, paler than the face.
        SPRAYS[Impacts.MASONRY] = new Spray[] {chips(4f, 1.4f, 0.022f, 1f, 1.05f, 20), dust(1.4f, 2.2f, 0.14f, 1.8f, 2.2f, 0.5f, 8), NOTHING};
        // Wood: pale splinters, long and thin, and a little of its dust.
        SPRAYS[Impacts.WOOD] = new Spray[] {chips(3.2f, 1.5f, 0.011f, 4f, 1.4f, 16), dust(0.9f, 1.4f, 0.08f, 1.2f, 1.4f, 0.3f, 5), NOTHING};
        // Metal: sparks thrown off along the glance of the shot, and a puff of what was on the face.
        SPRAYS[Impacts.METAL] = new Spray[] {NOTHING, dust(0.6f, 0.9f, 0.06f, 1f, 1f, 0.25f, 4),
                new Spray(22, 3f, 8f, 0.5f, 1.5f, 0.6f, 1f, 0f, true, 0.12f, 0.4f, 1f, false, 0.004f, 0.007f, 0f,
                        0.03f, 1f, 0f, 0f, 0f, 1f, false, 10f, 5.3f, 1.8f, false)};
        // Flesh: a mist of blood both ways along the shot, and drops of it that fall.
        SPRAYS[Impacts.FLESH] = new Spray[] {
                new Spray(20, 1.5f, 3.5f, 0.6f, 1.2f, 0.35f, 0f, 1f, false, 0.5f, 0.9f, 1f, true, 0.006f, 0.01f, 0f,
                        0.02f, 1f, 0.35f, 0.02f, 0.015f, 1f, true, 0f, 0f, 0f, false),
                new Spray(10, 0.3f, 1.2f, 1f, 4f, 0.4f, 0f, 0.3f, false, 0.35f, 0.7f, 0.15f, false, 0.06f, 0.12f, 3f,
                        0f, 1f, 0.45f, 0.03f, 0.025f, 0.6f, true, 0f, 0f, 0f, true),
                NOTHING};
        // Earth: clods kicked up and on along the shot, and a plume of dust.
        SPRAYS[Impacts.SOIL] = new Spray[] {chips(3.5f, 1f, 0.02f, 1f, 0.9f, 16), dust(1.6f, 1.8f, 0.12f, 1.8f, 1.8f, 0.4f, 8), NOTHING};
        SPRAYS[Impacts.SNOW] = new Spray[] {chips(2.5f, 0.9f, 0.022f, 1f, 1.1f, 12), dust(1.8f, 2f, 0.18f, 2.4f, 1.1f, 0.55f, 10), NOTHING};
        // Water: a column of drops thrown up, and a little spray.
        SPRAYS[Impacts.WATER] = new Spray[] {
                new Spray(20, 1.5f, 3.5f, 0.35f, 0.6f, 1f, 0.2f, 0f, true, 0.4f, 0.8f, 1f, true, 0.006f, 0.01f, 0f,
                        0.02f, 1f, 1.6f, 1.6f, 1.6f, 0.8f, false, 0f, 0f, 0f, false),
                dust(1.2f, 0.8f, 0.1f, 2f, 1.5f, 0.3f, 5), NOTHING};
        // Glass: glittering bits.
        SPRAYS[Impacts.GLASS] = new Spray[] {chips(3f, 1f, 0.014f, 1.5f, 1.8f, 16), dust(0.6f, 0.8f, 0.05f, 1f, 1.3f, 0.15f, 3), NOTHING};
        // Cloth, carpet, plastic: bits of it and a little dust.
        SPRAYS[Impacts.SOFT] = new Spray[] {chips(2f, 1f, 0.01f, 1.5f, 1.1f, 10), dust(0.8f, 1.4f, 0.08f, 1f, 1.3f, 0.3f, 5), NOTHING};
    }

    /** What the surface of each kind looks like where nothing is known of how it was drawn: a dim light on its usual colour. */
    private static final float[][] UNSEEN = {
            {0.12f, 0.115f, 0.105f}, {0.12f, 0.085f, 0.05f}, {0.1f, 0.1f, 0.11f}, {0.09f, 0.07f, 0.065f}, {0.07f, 0.055f, 0.035f},
            {0.3f, 0.31f, 0.33f}, {0.035f, 0.04f, 0.04f}, {0.14f, 0.16f, 0.16f}, {0.1f, 0.1f, 0.1f}};

    /** How far from the eye impacts are drawn, in tiles. Past it a shot's dust is a pixel or two and its hole none. */
    static final float REACH = 40f, HOLE_REACH = 25f;
    /** Each impact's block of the surface it struck: this many pixels square, read round where it struck. */
    private static final int BLOCK = 4;
    /** How far off a face's plane a pixel can be and still be marked by a hole in it, in tiles. */
    static final float HOLE_THICK = 0.03f;
    /**
     * How wide a hole's mark is in each kind of surface for a handgun's round, in tiles: a broken crater in masonry, splinters in wood, a ring in metal.
     * Larger than a round leaves, as games draw them: at the round's own size a hole a few tiles off was a pixel or two.
     */
    static final float HOLE_MASONRY = 0.08f, HOLE_WOOD = 0.06f, HOLE_METAL = 0.04f, HOLE_SOFT = 0.045f;
    /** A hole whose mark would be fewer pixels across than this is left out. */
    private static final float HOLE_PIXELS = 1.5f;

    private static final String DEBRIS_VERTEX = """
            #version 120
            uniform vec3 uAt;
            uniform vec3 uNormal;
            uniform vec3 uWay;
            uniform vec3 uEye;
            uniform float uAge;
            uniform float uSeed;
            uniform float uFloor;
            uniform float uScale;
            uniform vec4 uOut;
            uniform vec4 uSpeed;
            uniform vec4 uLife;
            uniform vec4 uSize;
            uniform float uLonger;
            uniform float uPuff;
            varying vec2 vIn;
            varying float vFade;
            varying float vShade;
            varying float vLonger;

            float rnd(float i, float k) {
                return fract(sin(i * 12.9898 + k * 78.233 + uSeed * 0.3719) * 43758.5453);
            }

            // Where something that left uAt at v0 is after t seconds, slowed by the air at k and pulled down by g.
            vec3 place(vec3 v0, float k, float g, float t) {
                float s = (1.0 - exp(-k * t)) / k;
                vec3 p = uAt + v0 * s;
                p.z += g / k * (s - t);
                return p;
            }

            void main() {
                float i = gl_Vertex.x;
                float corner = gl_Vertex.y;
                float life = mix(uLife.x, uLife.y, rnd(i, 5.0));
                float t = uAge - 0.03 * rnd(i, 9.0);
                if (t <= 0.0 || t >= life) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vec3 n = uNormal;
                vec3 glance = uWay - 2.0 * dot(uWay, n) * n;
                vec3 way = n * uOut.x + glance * uOut.y + uWay * uOut.z;
                vec3 own = vec3(rnd(i, 1.0), rnd(i, 2.0), rnd(i, 3.0)) * 2.0 - 1.0;
                vec3 dir = normalize(normalize(way + vec3(1e-4)) + own * uSpeed.z);
                if (uOut.w > 0.5) {
                    dir = normalize(dir + n * max(0.15 - dot(dir, n), 0.0));
                }
                float k = max(uSpeed.w, 0.05);
                float g = 9.8 * uLife.z;
                vec3 v0 = dir * mix(uSpeed.x, uSpeed.y, rnd(i, 4.0)) * sqrt(uScale);
                vec3 p = place(v0, k, g, t);
                float landed = 0.0;
                if (uLife.w > 0.5 && p.z < uFloor) {
                    float low = 0.0, high = t;
                    for (int j = 0; j < 8; j++) {
                        float middle = 0.5 * (low + high);
                        if (place(v0, k, g, middle).z < uFloor) {
                            high = middle;
                        } else {
                            low = middle;
                        }
                    }
                    p = place(v0, k, g, high);
                    p.z = uFloor;
                    landed = 1.0;
                }
                float size = mix(uSize.x, uSize.y, rnd(i, 7.0)) * uScale * (1.0 + uSize.z * t);
                p.z += landed * size;
                vec3 toEye = normalize(uEye - p);
                float e = exp(-k * t);
                vec3 v = v0 * e;
                v.z += g / k * (e - 1.0);
                vec2 c = vec2(corner > 0.5 && corner < 2.5 ? 1.0 : -1.0, corner > 1.5 ? 1.0 : -1.0);
                vec3 at;
                vec3 seen = v - toEye * dot(v, toEye);
                float going = length(seen);
                if (uSize.w > 0.0 && landed < 0.5 && going > 1e-4) {
                    // A streak along the way it is going, as long as it goes in uSize.w seconds.
                    vec3 side = seen / going;
                    vec3 over = cross(toEye, side);
                    at = p + side * (c.x > 0.0 ? size : -(size + going * uSize.w)) + over * (c.y * size);
                } else {
                    // Turned to the eye at an angle of its own, which turns as it tumbles and stops when it lands.
                    float turn = rnd(i, 6.0) * 6.2832 + (1.0 - landed) * t * 9.0 * (rnd(i, 8.0) - 0.5);
                    vec3 a = normalize(cross(toEye, abs(toEye.z) < 0.95 ? vec3(0.0, 0.0, 1.0) : vec3(1.0, 0.0, 0.0)));
                    vec3 b = cross(toEye, a);
                    vec3 side = a * cos(turn) + b * sin(turn);
                    vec3 over = cross(toEye, side);
                    at = p + side * (c.x * size * uLonger) + over * (c.y * size);
                }
                vIn = c;
                vLonger = uLonger;
                vFade = uPuff > 0.5 ? pow(1.0 - t / life, 1.5) * smoothstep(0.0, 0.06, t) : 1.0 - smoothstep(0.65 * life, life, t);
                vShade = 0.75 + 0.5 * rnd(i, 10.0);
                gl_Position = gl_ModelViewProjectionMatrix * vec4(at, 1.0);
            }
            """;

    private static final String DEBRIS_FRAGMENT = """
            #version 120
            uniform sampler2D uLit;
            uniform vec4 uLitAt;
            uniform vec3 uTint;
            uniform float uBlood;
            uniform vec3 uGlow;
            uniform float uAlpha;
            uniform float uPuff;
            varying vec2 vIn;
            varying float vFade;
            varying float vShade;
            varying float vLonger;

            void main() {
                float r2 = dot(vIn, vIn);
                // A puff thins to nothing at its edge. A long piece is a sliver pointed at both ends, and a short one a chip with its corners broken off.
                float cover = uPuff > 0.5 ? exp(-2.5 * r2) * (1.0 - smoothstep(0.6, 1.0, r2))
                        : vLonger > 1.2 ? (abs(vIn.y) < 0.6 * (1.0 - vIn.x * vIn.x) ? 1.0 : 0.0) : (abs(vIn.x) + abs(vIn.y) < 1.45 ? 1.0 : 0.0);
                float a = cover * uAlpha * vFade;
                if (a < 0.004) {
                    discard;
                }
                // The surface where the shot struck as the picture drew it: the mean of the block read round the place.
                vec3 lit = 0.25 * (texture2D(uLit, uLitAt.xy + uLitAt.zw * vec2(-1.0, -1.0)).rgb + texture2D(uLit, uLitAt.xy + uLitAt.zw * vec2(1.0, -1.0)).rgb
                        + texture2D(uLit, uLitAt.xy + uLitAt.zw * vec2(-1.0, 1.0)).rgb + texture2D(uLit, uLitAt.xy + uLitAt.zw * vec2(1.0, 1.0)).rgb);
                // Blood is lit as the surface was: the surface's brightness over the share of light a surface gives back. Dust is
                // paler and greyer than the face it came off, whatever the face was painted: a puff struck from a yellow road line was yellow.
                float bright = dot(lit, vec3(0.2126, 0.7152, 0.0722));
                vec3 colour = uBlood > 0.5 ? uTint * bright / 0.3 : mix(vec3(bright), lit, uPuff > 0.5 ? 0.5 : 0.8) * uTint;
                // A spark cools from yellow white to red as it dies.
                colour = colour * vShade + uGlow * vec3(1.0, 0.35 + 0.65 * vFade, 0.1 + 0.9 * vFade * vFade) * vFade;
                gl_FragData[0] = vec4(colour * a, a);
                // For the steadying pass: what is here moves by itself.
                gl_FragData[1] = vec4(a);
            }
            """;

    /**
     * A corner of a hole's square, every hole in one array: the corner, the kind of mark and its
     * seed in the vertex, the face's normal in the normal, where the shot struck and the mark's
     * radius in the first texture coordinate and the grain in the second. See holes.
     */
    private static final String HOLE_VERTEX = """
            #version 120
            uniform vec3 uEye;
            uniform vec3 uForward;
            uniform float uNear;
            uniform float uReach;
            // How wide a mark a tile from the eye has to be to be drawn.
            uniform float uSmallest;
            // Where the shot struck from the eye, and the rest of what the fragments read of the hole: the same at every corner.
            varying vec3 vAt;
            varying vec3 vNormal;
            varying vec3 vAlong;
            varying float vRadius;
            varying float vKind;
            varying float vSeed;

            void main() {
                vec3 to = gl_MultiTexCoord0.xyz - uEye;
                float radius = gl_MultiTexCoord0.w;
                vAt = to;
                vNormal = gl_Normal;
                vAlong = gl_MultiTexCoord1.xyz;
                vRadius = radius;
                vKind = gl_Vertex.z;
                vSeed = gl_Vertex.w;
                float ahead = dot(to, uForward);
                // Behind the eye, too far, too small to see, or seen from behind the face it is in: so at every
                // corner alike, and the square is drawn as a point off the picture.
                if (ahead < -radius || dot(to, to) > uReach * uReach || radius < uSmallest * max(ahead, uNear) || dot(to, gl_Normal) >= 0.0) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vec3 across = cross(gl_Normal, vAlong);
                // Drawn a little larger than the mark and a little off the face, so that it covers the mark however the face is seen.
                vec3 at = gl_MultiTexCoord0.xyz + (vAlong * gl_Vertex.x + across * gl_Vertex.y) * (radius * 1.3) + gl_Normal * 0.01;
                gl_Position = gl_ModelViewProjectionMatrix * vec4(at, 1.0);
            }
            """;

    /**
     * A hole, over the surface the picture drew: each pixel of the face round where the shot
     * struck is found from the picture's depth, and darkened or lightened by where it is in the
     * mark. Anything else at that pixel, in front of the face or behind it, is left alone.
     */
    private static final String HOLE_FRAGMENT = """
            #version 120
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform vec2 uTan;
            uniform vec2 uAside;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform float uThick;
            varying vec3 vAt;
            varying vec3 vNormal;
            varying vec3 vAlong;
            varying float vRadius;
            varying float vKind;
            varying float vSeed;

            // The seed is carried in hundredths, as a whole number: carried across a square as it
            // is, its last bits could differ from pixel to pixel, and so would the grit it seeds.
            float holeSeed() {
                return floor(vSeed + 0.5) / 100.0;
            }

            float aheadAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
            }

            float rnd(vec2 cell) {
                return fract(sin(dot(cell, vec2(12.9898, 78.233)) + holeSeed()) * 43758.5453);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                float ahead = aheadAt(uv);
                vec2 ndc = uv * 2.0 - 1.0 + uAside;
                vec3 d = (uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y)) * ahead - vAt;
                if (abs(dot(d, vNormal)) > uThick) {
                    discard;
                }
                vec3 across = cross(vNormal, vAlong);
                vec2 q = vec2(dot(d, vAlong), dot(d, across)) / vRadius;
                float r = length(q);
                if (r >= 1.0) {
                    discard;
                }
                float angle = atan(q.y, q.x);
                float rough = 0.8 + 0.12 * sin(angle * 5.0 + holeSeed()) + 0.08 * sin(angle * 11.0 + holeSeed() * 2.3);
                float grit = 0.88 + 0.24 * rnd(floor(q * 9.0));
                float m;
                if (vKind < 0.5) {
                    // Masonry: a dark hole, a crater of broken stuff round it, and a pale lip where the face broke away.
                    float rr = r / rough;
                    m = rr < 0.18 ? 0.05 : rr < 0.55 ? mix(0.4, 0.85, (rr - 0.18) / 0.37) * grit : rr < 0.85 ? 1.2 * grit : 1.0;
                } else if (vKind < 1.5) {
                    // Wood: a dark hole, and pale splinters lifted along the grain either side of it.
                    float lifted = 0.22 * (1.0 - abs(q.x)) * (0.5 + rnd(vec2(floor(q.x * 7.0), 3.0)));
                    m = r < 0.2 * rough ? 0.07 : abs(q.y) < lifted ? 1.35 : r < 0.38 ? 0.65 : 1.0;
                } else if (vKind < 2.5) {
                    // Metal: a clean dark hole, a ring of bright bare metal, and scorched paint round that.
                    m = r < 0.32 ? 0.08 : r < 0.55 ? 1.9 : r < 0.75 * rough ? 0.75 : 1.0;
                } else {
                    // Cloth, carpet, plastic: a dark hole with a frayed edge.
                    m = r < 0.3 * rough ? 0.12 : r < 0.6 ? 0.7 * grit : 1.0;
                }
                m = mix(m, 1.0, smoothstep(0.85, 1.0, r));
                // Laid on as twice its colour times what is there: one half leaves the surface as it was.
                gl_FragColor = vec4(vec3(0.5 * m), 1.0);
            }
            """;

    private static final SmallProgram debris = new SmallProgram("debris of shots", DEBRIS_VERTEX, DEBRIS_FRAGMENT);
    private static final SmallProgram holeProgram = new SmallProgram("bullet holes", HOLE_VERTEX, HOLE_FRAGMENT);

    private static final int GL_TEXTURE_2D = 0x0DE1, GL_RGBA16F = 0x881A, GL_RGBA = 0x1908, GL_FLOAT = 0x1406;
    private static final int GL_VERTEX_ARRAY = 0x8074, GL_NORMAL_ARRAY = 0x8075, GL_COLOR_ARRAY = 0x8076, GL_TEXTURE_COORD_ARRAY = 0x8078;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_TEXTURE0 = 0x84C0;
    private static final int GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803, GL_CLAMP_TO_EDGE = 0x812F;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8, GL_FRAMEBUFFER = 0x8D40, GL_COLOR_ATTACHMENT0 = 0x8CE0;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_STATIC_DRAW = 0x88E4, GL_TRIANGLES = 0x0004, GL_TRIANGLE_STRIP = 0x0005;
    private static final int GL_BLEND = 0x0BE2, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_LEQUAL = 0x0203;
    private static final int GL_ONE = 1, GL_ZERO = 0, GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_SRC_ALPHA = 0x0302, GL_DST_COLOR = 0x0306, GL_SRC_COLOR = 0x0300;

    /** The vertices every impact is drawn from: for each of its PARTICLES, its number and which corner each of six vertices is. */
    private static int particles;
    /** The blocks of the surfaces struck, one for each place in Impacts' ring, as this view's picture drew them. */
    private int litTexture;
    /** For each place in the ring, the number of the impact whose block has been read there. */
    private final int[] read = new int[Impacts.MOST];
    private final float[] ndc = new float[2];
    private final FloatBuffer unseen = ByteBuffer.allocateDirect(BLOCK * BLOCK * 4 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();

    /** Whether impacts are drawn in a view's picture at all: never in the game's own look, which has none. */
    static boolean shown(int view) {
        return !Quality.plain(view);
    }

    /**
     * Reads, for each impact this view has not yet read, the surface the shot struck as the
     * picture drew it: a small block of the picture round the place, copied on the graphics card.
     * Where the place is not in the picture its kind's usual colour in a dim light stands in.
     *
     * @param from the target the world has just been drawn into
     */
    void read(int from, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, int width, int height) {
        if (litTexture == 0) {
            litTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, litTexture);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, Impacts.MOST * BLOCK, BLOCK, 0, GL_RGBA, GL_FLOAT, (FloatBuffer) null);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        boolean bound = false;
        for (int slot = 0; slot < Impacts.MOST; slot++) {
            Impacts.Struck struck = Impacts.held[slot];
            if (struck == null || read[slot] == Impacts.serial[slot]) {
                continue;
            }
            read[slot] = Impacts.serial[slot];
            if (!bound) {
                GL30.glBindFramebuffer(GL_READ_FRAMEBUFFER, from);
                GL11.glReadBuffer(GL_COLOR_ATTACHMENT0);
                GL11.glBindTexture(GL_TEXTURE_2D, litTexture);
                bound = true;
            }
            boolean seen = ViewCamera.project(camera, tanHalfFieldOfView, aspect, struck.x(), struck.y(), struck.up(), ndc)
                    && Math.abs(ndc[0]) < 1f && Math.abs(ndc[1]) < 1f;
            if (seen) {
                int x = Math.clamp(Math.round((ndc[0] * 0.5f + 0.5f) * width) - BLOCK / 2, 0, width - BLOCK);
                int y = Math.clamp(Math.round((ndc[1] * 0.5f + 0.5f) * height) - BLOCK / 2, 0, height - BLOCK);
                GL11.glCopyTexSubImage2D(GL_TEXTURE_2D, 0, slot * BLOCK, 0, x, y, BLOCK, BLOCK);
            } else {
                float[] colour = UNSEEN[struck.kind()];
                unseen.clear();
                for (int i = 0; i < BLOCK * BLOCK; i++) {
                    unseen.put(colour[0]).put(colour[1]).put(colour[2]).put(1f);
                }
                unseen.flip();
                GL11.glTexSubImage2D(GL_TEXTURE_2D, 0, slot * BLOCK, 0, BLOCK, BLOCK, GL_RGBA, GL_FLOAT, unseen);
            }
        }
    }

    /**
     * Lays the holes over the surfaces of the picture drawn so far. The GL matrices are the
     * picture's; `target` draws into its colour alone, and `depth` is its depth.
     *
     * @param asideX how far the picture was drawn aside this frame, in its own measure: see Pipeline.drawWorld
     */
    void holes(int target, int depth, ViewCamera.Pose camera, float tanHalfFieldOfView, float aspect, float near, float far, int width, int height,
            float asideX, float asideY) {
        if (Impacts.holeCount == 0 || !holeProgram.use()) {
            return;
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, target);
        GL11.glViewport(0, 0, width, height);
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // Twice the colour given times the colour there. The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_DST_COLOR, GL_SRC_COLOR, GL_ZERO, GL_ONE);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, depth);
        GL20.glUniform1i(holeProgram.at("uDepth"), 0);
        GL20.glUniform2f(holeProgram.at("uSize"), width, height);
        GL20.glUniform2f(holeProgram.at("uTan"), tanHalfFieldOfView * aspect, tanHalfFieldOfView);
        GL20.glUniform2f(holeProgram.at("uAside"), asideX, asideY);
        GL20.glUniform1f(holeProgram.at("uNear"), near);
        GL20.glUniform1f(holeProgram.at("uFar"), far);
        GL20.glUniform1f(holeProgram.at("uThick"), HOLE_THICK);
        GL20.glUniform3f(holeProgram.at("uEye"), camera.eyeX, camera.eyeY, camera.eyeUp);
        GL20.glUniform3f(holeProgram.at("uRight"), camera.rightX, camera.rightY, camera.rightUp);
        GL20.glUniform3f(holeProgram.at("uUp"), camera.upX, camera.upY, camera.upUp);
        GL20.glUniform3f(holeProgram.at("uForward"), camera.forwardX, camera.forwardY, camera.forwardUp);
        GL20.glUniform1f(holeProgram.at("uReach"), HOLE_REACH);
        GL20.glUniform1f(holeProgram.at("uSmallest"), HOLE_PIXELS * 2f * tanHalfFieldOfView / height);
        if (holesMade != Impacts.holesChanged || holeCorners == 0) {
            makeHoles();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, holeCorners);
        GL11.glDisableClientState(GL_COLOR_ARRAY);
        GL11.glEnableClientState(GL_VERTEX_ARRAY);
        GL11.glVertexPointer(4, GL_FLOAT, HOLE_STRIDE, 0L);
        GL11.glEnableClientState(GL_NORMAL_ARRAY);
        GL11.glNormalPointer(GL_FLOAT, HOLE_STRIDE, 4L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(3, GL_FLOAT, HOLE_STRIDE, 11L * Float.BYTES);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        GL11.glTexCoordPointer(4, GL_FLOAT, HOLE_STRIDE, 7L * Float.BYTES);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glDrawArrays(GL_TRIANGLES, 0, Impacts.holeCount * 6);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0 + 1);
        GL11.glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        GL13.glClientActiveTexture(GL_TEXTURE0);
        GL11.glDisableClientState(GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL_VERTEX_ARRAY);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
    }

    /** The corners of every hole, six to a hole, and the count of Impacts.holesChanged they were made at. Render thread. */
    private static int holeCorners, holesMade;
    private static final int HOLE_FLOATS = 14, HOLE_STRIDE = HOLE_FLOATS * Float.BYTES;
    private static final FloatBuffer holeVertices = ByteBuffer.allocateDirect(Impacts.MOST_HOLES * 6 * HOLE_STRIDE).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final float[] HOLE_CORNERS = {-1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f, 1f, -1f, 1f, 1f};

    /** Gives the graphics card the corners of the holes Impacts holds now, which the hole program reads. */
    private static void makeHoles() {
        holeVertices.clear();
        for (int i = 0; i < Impacts.holeCount; i++) {
            Impacts.Struck hole = Impacts.holes[i];
            float radius = holeRadius(hole.kind()) * hole.size();
            for (int corner = 0; corner < HOLE_CORNERS.length; corner += 2) {
                holeVertices.put(HOLE_CORNERS[corner]).put(HOLE_CORNERS[corner + 1]).put(holeKind(hole.kind())).put(seedHundredths(hole));
                holeVertices.put(hole.normalX()).put(hole.normalY()).put(hole.normalUp());
                holeVertices.put(hole.x()).put(hole.y()).put(hole.up()).put(radius);
                holeVertices.put(hole.grainX()).put(hole.grainY()).put(hole.grainUp());
            }
        }
        holeVertices.flip();
        if (holeCorners == 0) {
            holeCorners = GL15.glGenBuffers();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, holeCorners);
        GL15.glBufferData(GL_ARRAY_BUFFER, holeVertices, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        holesMade = Impacts.holesChanged;
    }

    /** How wide a hole's mark is in a kind of surface for a handgun's round, in tiles. */
    static float holeRadius(int kind) {
        return switch (kind) {
            case Impacts.WOOD -> HOLE_WOOD;
            case Impacts.METAL -> HOLE_METAL;
            case Impacts.SOFT -> HOLE_SOFT;
            default -> HOLE_MASONRY;
        };
    }

    /** Which of the hole program's marks a kind of surface takes: masonry 0, wood 1, metal 2, the soft ones 3. */
    private static float holeKind(int kind) {
        return switch (kind) {
            case Impacts.WOOD -> 1f;
            case Impacts.METAL -> 2f;
            case Impacts.SOFT -> 3f;
            default -> 0f;
        };
    }

    /** A number of its own for each impact, 0 to 100, from where it struck. */
    private static float seed(Impacts.Struck struck) {
        return seedHundredths(struck) / 100f;
    }

    private static int seedHundredths(Impacts.Struck struck) {
        int hash = Float.floatToIntBits(struck.x()) * 31 + Float.floatToIntBits(struck.y()) * 17 + Float.floatToIntBits(struck.up());
        return (hash & 0x7fffffff) % 10000;
    }

    /**
     * Draws what flies from each impact into the target bound, tested against its depth and
     * leaving it alone. The GL matrices are the picture's, and only the vertex array is
     * switched on.
     *
     * @param now  when the frame began
     * @param fogStart where the fog starts, and fogEnd where the world has faded into it, in tiles: sparks fade into it too
     */
    void debris(ViewCamera.Pose camera, long now, float fogStart, float fogEnd) {
        if (!debris.use()) {
            return;
        }
        if (particles == 0) {
            makeParticles();
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, particles);
        GL11.glVertexPointer(4, GL_FLOAT, 0, 0L);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glBindTexture(GL_TEXTURE_2D, litTexture);
        GL20.glUniform1i(debris.at("uLit"), 0);
        GL20.glUniform3f(debris.at("uEye"), camera.eyeX, camera.eyeY, camera.eyeUp);
        // The pieces and the puffs first, laid over what is there, and the sparks after, added to it.
        for (int pass = 0; pass < 2; pass++) {
            GL14.glBlendFuncSeparate(GL_ONE, pass == 0 ? GL_ONE_MINUS_SRC_ALPHA : GL_ONE, GL_ZERO, GL_ONE);
            for (int slot = 0; slot < Impacts.MOST; slot++) {
                Impacts.Struck struck = Impacts.held[slot];
                if (struck == null || read[slot] != Impacts.serial[slot]) {
                    continue;
                }
                float age = (now - Impacts.born[slot]) / 1e9f;
                float toX = struck.x() - camera.eyeX, toY = struck.y() - camera.eyeY, toUp = struck.up() - camera.eyeUp;
                float distance = (float) Math.sqrt(toX * toX + toY * toY + toUp * toUp);
                if (age > Impacts.LONGEST || distance > REACH) {
                    continue;
                }
                GL20.glUniform3f(debris.at("uAt"), struck.x() + struck.normalX() * 0.01f, struck.y() + struck.normalY() * 0.01f, struck.up() + struck.normalUp() * 0.01f);
                GL20.glUniform3f(debris.at("uNormal"), struck.normalX(), struck.normalY(), struck.normalUp());
                GL20.glUniform3f(debris.at("uWay"), struck.wayX(), struck.wayY(), struck.wayUp());
                GL20.glUniform1f(debris.at("uAge"), age);
                GL20.glUniform1f(debris.at("uSeed"), seed(struck));
                GL20.glUniform1f(debris.at("uFloor"), struck.floor());
                GL20.glUniform1f(debris.at("uScale"), struck.size());
                GL20.glUniform4f(debris.at("uLitAt"), (slot * BLOCK + BLOCK / 2f) / (Impacts.MOST * BLOCK), 0.5f, 1f / (Impacts.MOST * BLOCK), 1f / BLOCK);
                float fog = 1f - Terrain.smooth((distance - fogStart) / Math.max(fogEnd - fogStart, 1e-3f));
                Spray[] sprays = SPRAYS[struck.kind()];
                for (int group = 0; group < GROUPS; group++) {
                    Spray spray = sprays[group];
                    boolean sparks = spray.glowRed() > 0f;
                    int count = Math.min(Math.round(spray.count() * Math.min(struck.size(), 1f)), MOST[group]);
                    if (count == 0 || sparks != (pass == 1)) {
                        continue;
                    }
                    GL20.glUniform4f(debris.at("uOut"), spray.outNormal(), spray.outGlance(), spray.outWay(), spray.keptOut() ? 1f : 0f);
                    GL20.glUniform4f(debris.at("uSpeed"), spray.speedLeast(), spray.speedMost(), spray.spread(), spray.drag());
                    GL20.glUniform4f(debris.at("uLife"), spray.lifeLeast(), spray.lifeMost(), spray.fall(), spray.rests() ? 1f : 0f);
                    GL20.glUniform4f(debris.at("uSize"), spray.sizeLeast(), spray.sizeMost(), spray.grow(), spray.streak());
                    GL20.glUniform1f(debris.at("uLonger"), spray.longer());
                    GL20.glUniform1f(debris.at("uPuff"), spray.puff() ? 1f : 0f);
                    GL20.glUniform3f(debris.at("uTint"), spray.tintRed(), spray.tintGreen(), spray.tintBlue());
                    GL20.glUniform1f(debris.at("uBlood"), spray.blood() ? 1f : 0f);
                    GL20.glUniform3f(debris.at("uGlow"), spray.glowRed() * fog, spray.glowGreen() * fog, spray.glowBlue() * fog);
                    GL20.glUniform1f(debris.at("uAlpha"), spray.alpha());
                    GL11.glDrawArrays(GL_TRIANGLES, FIRST[group] * 6, count * 6);
                }
            }
        }
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
    }

    private static void makeParticles() {
        FloatBuffer vertices = ByteBuffer.allocateDirect(PARTICLES * 6 * 4 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
        float[] corners = {0f, 1f, 2f, 0f, 2f, 3f};
        for (int particle = 0; particle < PARTICLES; particle++) {
            for (float corner : corners) {
                vertices.put(particle).put(corner).put(0f).put(1f);
            }
        }
        particles = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, particles);
        GL15.glBufferData(GL_ARRAY_BUFFER, vertices.flip(), GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}
