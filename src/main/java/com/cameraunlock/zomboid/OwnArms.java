package com.cameraunlock.zomboid;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;

/**
 * The mod's own arms on what the viewer holds, drawn in place of the game's, which are made
 * to be seen from a rooftop: a mesh with a hand that has fingers, hung on bones and bent to
 * what it holds, made by tools/models/arms.py and kept in the jar.
 *
 * They are drawn as the game's own hands are, unlit, into the picture of the viewer's own
 * body: the colour of the skin, the way each point of it faces and what it is, for the pass
 * that lights people to light.
 *
 * All of it is the render thread's.
 */
final class OwnArms {
    private OwnArms() {}

    private static final String MESH = "/models/arms.mesh.gz", COLOUR = "/models/arms.colour.bc", SURFACE = "/models/arms.surface.bc";
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0;
    /** The units the mod's own guns keep their pictures on (OwnGun), each bound afresh by whoever draws. */
    private static final int COLOUR_UNIT = 30, SURFACE_UNIT = 31;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406, GL_UNSIGNED_BYTE = 0x1401, GL_UNSIGNED_INT = 0x1405, GL_TRIANGLES = 0x0004;
    private static final int GL_VERTEX_SHADER = 0x8B31, GL_FRAGMENT_SHADER = 0x8B30, GL_COMPILE_STATUS = 0x8B81, GL_LINK_STATUS = 0x8B82;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LESS = 0x0201, GL_BACK = 0x0405, GL_FRONT = 0x0404;
    private static final int[] FIRST = {0x8CE0}, BOTH = {0x8CE0, 0x8CE1};
    private static final String[] ATTRIBUTES = {"aPlace", "aNormal", "aTangent", "aUv", "aFemale", "aBones", "aWeights", "aAlong"};
    private static final int[] SIZES = {3, 3, 4, 2, 3, 4, 4, 1};
    private static final int[] OFFSETS = {0, ArmModel.NORMAL_AT, ArmModel.TANGENT_AT, ArmModel.UV_AT, ArmModel.FEMALE_AT, ArmModel.BONES_AT, ArmModel.WEIGHTS_AT, ArmModel.ALONG_AT};
    private static final int BONES = 5, WEIGHTS = 6;
    /** The most bones the program has room for. */
    private static final int MOST_BONES = 24;

    /**
     * What the game's five skins of the living mostly are, palest first, as tools/skins/bake.py
     * measured them: the arm is the colour of the body it belongs to.
     */
    private static final float[][] TONES = {{0.98f, 0.886f, 0.725f}, {0.937f, 0.788f, 0.553f}, {0.812f, 0.659f, 0.467f}, {0.522f, 0.369f, 0.239f},
        {0.349f, 0.255f, 0.141f}};

    private static final String VERTEX = "#version 120\n" + TimedActs.SHEET_BENT + """
            attribute vec3 aPlace;
            attribute vec3 aNormal;
            attribute vec4 aTangent;
            attribute vec2 aUv;
            attribute vec3 aFemale;
            attribute vec4 aBones;
            attribute vec4 aWeights;
            attribute float aAlong;
            uniform mat4 uBones[24];
            uniform mat4 uProjection;
            // 0 for a man's arm, 1 for a woman's.
            uniform float uFemale;
            uniform vec3 uUpSeen;
            // 1 for the held map's sheet, which is bent as TimedActs.bend has it.
            uniform float uSheet;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying float vWay;
            varying vec2 vUv;
            varying vec3 vRest;
            varying float vAlong;
            varying vec3 vSeen;
            varying vec3 vDown;
            // The sheet's axes as seen: across it, up it and out of its face, by its own measure.
            varying vec3 vAcross;
            varying vec3 vUpSheet;
            varying vec3 vOut;

            void main() {
                vec3 rest = aPlace + aFemale * uFemale;
                if (uSheet > 0.5) {
                    rest.z = sheetBend(aUv - 0.5);
                }
                mat4 bent = uBones[int(aBones.x + 0.5)] * aWeights.x + uBones[int(aBones.y + 0.5)] * aWeights.y
                        + uBones[int(aBones.z + 0.5)] * aWeights.z + uBones[int(aBones.w + 0.5)] * aWeights.w;
                vec4 seen = bent * vec4(rest, 1.0);
                vNormal = mat3(bent) * aNormal;
                vTangent = mat3(bent) * aTangent.xyz;
                vWay = aTangent.w;
                vUv = aUv;
                vRest = rest;
                vAlong = aAlong;
                vSeen = seen.xyz;
                // The world's down in the arm's own axes, where its water runs.
                vDown = normalize(transpose(mat3(bent)) * -uUpSeen);
                vAcross = mat3(bent) * vec3(1.0, 0.0, 0.0);
                vUpSheet = mat3(bent) * vec3(0.0, 1.0, 0.0);
                vOut = mat3(bent) * vec3(0.0, 0.0, 1.0);
                gl_Position = uProjection * seen;
            }
            """;

    /**
     * How the held map's sheet looks: printed paper. The game's map is printed inside a margin,
     * its colours multiplied onto the paper, a little soft and faded, and worn off along the
     * creases; a ruled border, a scale bar and a north arrow are printed round it. The paper is
     * mottled, yellowed at its edges and where the folds cross, and its corners are round. The
     * back carries the cover on its last panel. Held against the sun, out of doors, the paper
     * glows and what is printed on the other side shows through.
     */
    private static final String PAPER_GLSL = """
            // The sun as the eye sees it, as long as it is strong: nought when it gives no light.
            uniform vec3 uSunSeen;
            // How long the scale bar is, metres of the sheet, or nought for none.
            uniform float uScaleBar;
            const vec2 SHEET = vec2(%1$s, %2$s);
            // Where the print is on the sheet, metres from its lower left corner: its left, foot, right and top.
            const vec4 PRINT = vec4(%3$s, %4$s, %1$s - %3$s, %2$s - %3$s);
            const float CORNER = 0.0025;
            const vec3 INK = vec3(0.13, 0.11, 0.09);

            bool onSheet(vec2 uv) {
                vec2 m = uv * SHEET;
                return length(m - clamp(m, vec2(CORNER), SHEET - CORNER)) <= CORNER;
            }

            bool inPrint(vec2 uv) {
                vec2 m = uv * SHEET;
                return all(greaterThan(m, PRINT.xy)) && all(lessThan(m, PRINT.zw));
            }

            // How near a crease uv is, 1 on one, and in crossing how near a place where two cross.
            // The sheet was folded in four panels across and once along: see TimedActs.bend.
            float creaseNear(vec2 uv, out float crossing) {
                float across = abs(fract(uv.x * 4.0 + 0.5) - 0.5) * 0.25 * SHEET.x, along = abs(uv.y - 0.5) * SHEET.y;
                crossing = exp(-(across * across + along * along) / 0.00005);
                return max(exp(-across * across / 0.0000015), exp(-along * along / 0.0000015));
            }

            vec3 paperTone(vec2 uv) {
                vec2 m = uv * SHEET;
                float mottle = cloud(vec3(m * 60.0, 3.0)), grain = cloud(vec3(m * 500.0, 9.0));
                vec2 rim = min(m, SHEET - m);
                float edge = 1.0 - smoothstep(0.0, 0.025, min(rim.x, rim.y));
                float crossing;
                float crease = creaseNear(uv, crossing);
                float aged = clamp(0.55 * edge * (0.6 + 0.8 * mottle) + 0.45 * crossing + 0.12 * crease, 0.0, 1.0);
                vec3 tone = uCloth * (0.955 + 0.05 * mottle + 0.03 * grain) * (1.0 - 0.12 * crease);
                return tone * mix(vec3(1.0), vec3(0.9, 0.82, 0.64), aged);
            }

            // What the print lets of the paper through at uv, 1 where nothing is printed.
            vec3 printed(vec2 uv) {
                vec3 ink = clamp(texture2D(uColour, uv, 0.7).rgb / uCloth, 0.0, 1.0);
                ink = mix(vec3(dot(ink, vec3(0.299, 0.587, 0.114))), ink, 0.85);
                float crossing;
                float crease = creaseNear(uv, crossing);
                float worn = clamp(0.5 * crease + 0.6 * crossing, 0.0, 1.0) * (0.5 + 0.5 * cloud(vec3(uv * SHEET * 500.0, 9.0)));
                return mix(ink, vec3(1.0), worn * 0.55);
            }

            float ruled(float d, float wide) {
                float soft = fwidth(d);
                return 1.0 - smoothstep(wide * 0.5 - soft, wide * 0.5 + soft, abs(d));
            }

            float stroke(vec2 p, vec2 a, vec2 b) {
                vec2 pa = p - a, ba = b - a;
                return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
            }

            // How much dark ink the border, the scale bar and the north arrow lay at uv, 0 to 1.
            float furniture(vec2 uv) {
                vec2 m = uv * SHEET;
                vec2 off = abs(m - (PRINT.xy + PRINT.zw) * 0.5) - (PRINT.zw - PRINT.xy) * 0.5;
                float box = max(off.x, off.y);
                float ink = max(ruled(box, 0.0004), ruled(box - 0.0035, 0.0009));
                float middle = PRINT.y * 0.5;
                if (uScaleBar > 0.0) {
                    vec2 barOff = abs(m - vec2(PRINT.x + uScaleBar * 0.5, middle)) - vec2(uScaleBar * 0.5, 0.0011);
                    float bar = max(barOff.x, barOff.y);
                    ink = max(ink, ruled(bar, 0.0003));
                    if (bar < 0.0 && mod(floor((m.x - PRINT.x) / (uScaleBar * 0.25)), 2.0) < 0.5) {
                        ink = 1.0;
                    }
                }
                // The arrow: its left half filled and its right half drawn round, the N beside it.
                vec2 a = m - vec2(PRINT.z - 0.006, middle - 0.0045);
                float edges = min(min(stroke(a, vec2(-0.0022, 0.0), vec2(0.0, 0.009)), stroke(a, vec2(0.0022, 0.0), vec2(0.0, 0.009))),
                        stroke(a, vec2(-0.0022, 0.0), vec2(0.0022, 0.0)));
                ink = max(ink, ruled(edges, 0.0003));
                if (a.x <= 0.0 && a.y >= 0.0 && -a.x < 0.0022 * (1.0 - a.y / 0.009)) {
                    ink = 1.0;
                }
                vec2 letter = m - vec2(PRINT.z - 0.0145, middle - 0.002);
                float n = min(min(stroke(letter, vec2(0.0), vec2(0.0, 0.004)), stroke(letter, vec2(0.003, 0.0), vec2(0.003, 0.004))),
                        stroke(letter, vec2(0.0, 0.004), vec2(0.003, 0.0)));
                return max(ink, ruled(n, 0.0005));
            }

            // What the cover on the back's last panel lets of the paper through at uv, 1 where nothing is printed.
            vec3 cover(vec2 uv) {
                vec2 m = uv * SHEET;
                float from = SHEET.x * 0.75;
                vec2 off = abs(m - vec2((from + SHEET.x) * 0.5, SHEET.y * 0.5)) - vec2(SHEET.x * 0.125 - 0.004, SHEET.y * 0.5 - 0.004);
                // Ruled before anything is left out: fwidth needs every pixel of the four beside each other to come this far.
                vec3 ink = mix(vec3(1.0), INK / uCloth, ruled(max(off.x, off.y), 0.0006));
                if (m.x < from) {
                    return vec3(1.0);
                }
                bool within = m.x > from + 0.007 && m.x < SHEET.x - 0.007;
                if (within && m.y > SHEET.y * 0.45 && m.y < SHEET.y - 0.008 && (m.y < SHEET.y * 0.62 || m.y > SHEET.y * 0.66)) {
                    ink = vec3(0.22, 0.32, 0.55) / uCloth;
                } else if (within && m.y > SHEET.y * 0.4 && m.y < SHEET.y * 0.42) {
                    ink = vec3(0.7, 0.18, 0.12) / uCloth;
                }
                return ink;
            }

            // How much sun comes through the paper from behind a side facing the eye as n faces, out of doors.
            float backlit(vec3 n) {
                float strength = length(uSunSeen);
                return uOutside < 0.5 || strength < 0.001 ? 0.0 : strength * smoothstep(0.0, 0.6, -dot(n, uSunSeen / strength));
            }

            // The colour of the paper at uv seen from its front or its back, n the way the side the eye sees faces.
            vec3 paperShown(vec2 uv, bool back, bool pictured, vec3 n) {
                vec3 front = (pictured ? printed(uv) : vec3(1.0)) * mix(vec3(1.0), INK / uCloth, furniture(uv));
                vec3 behind = cover(uv);
                float through = backlit(n);
                vec3 seen = back ? behind * mix(vec3(1.0), front, 0.06 + 0.22 * through) : front * mix(vec3(1.0), behind, 0.08 * through);
                return paperTone(uv) * seen * (1.0 + 0.3 * through);
            }

            // The paper's fibre in the way it faces, where it is fine enough to be seen.
            vec3 paperFacing(vec3 n, vec3 up, vec2 uv) {
                vec2 m = uv * SHEET * 400.0;
                float shown = 1.0 - smoothstep(0.4, 0.9, max(length(dFdx(m)), length(dFdy(m))));
                float here = cloud(vec3(m, 5.0));
                vec3 side = normalize(cross(up, n)), upward = cross(n, side);
                vec3 tilt = side * (cloud(vec3(m + vec2(0.3, 0.0), 5.0)) - here) + upward * (cloud(vec3(m + vec2(0.0, 0.3), 5.0)) - here);
                return normalize(n - tilt * (0.25 * shown));
            }
            """.formatted(TimedActs.SHEET_WIDE, TimedActs.SHEET_TALL, TimedActs.SHEET_MARGIN, TimedActs.SHEET_FOOT);

    /**
     * chance and chance3 are hash12 and hash13 of David Hoskins's Hash without Sine, used under
     * its MIT licence: the notice is in THIRD-PARTY-NOTICES.md.
     */
    private static final String FRAGMENT = "#version 120\n" + CharacterDraw.KEPT + SnowShader.ON_PEOPLE + Soaked.GLSL + TimedActs.SHEET_BENT + """
            uniform sampler2D uColour;
            uniform sampler2D uSurface;
            // What is drawn: 0 for skin, and for what is worn what CharacterDraw calls it, a weave, a knit or leather.
            uniform float uWear;
            // The colour of what is worn.
            uniform vec3 uCloth;
            // Skin is drawn from the first of these to the second, as the model counts along the arm, and what is worn the same.
            uniform vec2 uSpan;
            // How bloody the hand, the forearm and the upper arm are, 0 to 1, and how dirty.
            uniform vec3 uBlood;
            uniform vec3 uDirt;
            uniform vec3 uTone;
            // For a sheet of paper (uWear PAPER), whether uColour holds its picture.
            uniform float uPictured;
            // The way the viewer faces on the sheet's map, across it and up it, and the sheet's width and height, metres.
            uniform vec4 uMarker;
            // Where the viewer is on the sheet, across it and up it from its lower left corner as shares.
            uniform vec2 uPointerAt;

            // Whether a place on the sheet, metres from the viewer's place, is inside the pointer there, grown by grow:
            // an arrowhead twice as long as it is wide, with a notch in its back, so that no turn of it reads as another way.
            bool pointer(vec2 at, float grow) {
                float ahead = dot(at, uMarker.xy), aside = abs(dot(at, vec2(-uMarker.y, uMarker.x)));
                float tip = 0.0065 * grow, back = 0.0035 * grow, wide = 0.0026 * grow, notch = 0.002 * grow;
                return ahead < tip && aside < wide * (tip - ahead) / (tip + back) && ahead > -back + notch * (1.0 - aside / wide);
            }
            // Whether the viewer stands out of doors, and the light where they stand, less than nought
            // where none is said: written beside the colour, as CharacterDraw writes it of a person.
            uniform float uOutside;
            uniform float uStood;
            // -1 for the left arm, whose matrices mirror the model and turn a surface's own axes inside out.
            uniform float uHanded;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying float vWay;
            varying vec2 vUv;
            varying vec3 vRest;
            varying float vAlong;
            varying vec3 vSeen;
            varying vec3 vDown;
            varying vec3 vAcross;
            varying vec3 vUpSheet;
            varying vec3 vOut;

            float chance(vec2 p) {
                vec3 q = fract(vec3(p.xyx) * 0.1031);
                q += dot(q, q.yzx + 33.33);
                return fract((q.x + q.y) * q.z);
            }

            // How high a cloth's threads stand at a place on it: a weave's over and under, a twill's slanted ribs, a knit's rows of loops.
            float threads(vec2 at) {
                if (uWear > 4.5) {
                    // Leather: a grain of little cells.
                    vec2 cell = floor(at * 0.35), within = fract(at * 0.35) - 0.5;
                    return 0.6 * chance(cell) + 0.4 * (1.0 - 2.0 * length(within));
                }
                if (uWear > 3.5) {
                    vec2 loop = fract(vec2(at.x + 0.5 * floor(at.y * 0.5), at.y * 0.5)) - 0.5;
                    return 1.0 - 2.0 * abs(loop.x) * (0.6 + 0.8 * loop.y);
                }
                if (uWear > 2.5) {
                    return 0.5 + 0.5 * sin((at.x + at.y) * 3.14159265);
                }
                return 0.5 + 0.25 * sin(at.x * 6.2831853) * sin(at.y * 6.2831853) + 0.25 * sin(at.x * 3.14159265) * sin(at.y * 3.14159265);
            }

            float chance3(vec3 p) {
                p = fract(p * 0.1031);
                p += dot(p, p.zyx + 31.32);
                return fract((p.x + p.y) * p.z);
            }

            // Cloudy, 0 to 1, the same at a place on the arm however the arm is bent.
            float cloud(vec3 p) {
                vec3 cell = floor(p), within = fract(p);
                within = within * within * (3.0 - 2.0 * within);
                float low = mix(mix(chance3(cell), chance3(cell + vec3(1.0, 0.0, 0.0)), within.x),
                        mix(chance3(cell + vec3(0.0, 1.0, 0.0)), chance3(cell + vec3(1.0, 1.0, 0.0)), within.x), within.y);
                float high = mix(mix(chance3(cell + vec3(0.0, 0.0, 1.0)), chance3(cell + vec3(1.0, 0.0, 1.0)), within.x),
                        mix(chance3(cell + vec3(0.0, 1.0, 1.0)), chance3(cell + vec3(1.0, 1.0, 1.0)), within.x), within.y);
                return mix(low, high, within.z);
            }

            // How much of what is there, 0 to 1, a place along the arm has: the hand's share, the forearm's or the upper arm's.
            float share(vec3 of) {
                return mix(mix(of.z, of.y, smoothstep(0.9, 1.1, vAlong)), of.x, smoothstep(1.9, 2.05, vAlong));
            }

            // Blood lies in a few broad splashes with ragged edges, drawn out along the arm where it
            // has run, and dirt in smears: each covers as much of a place as there is of it.
            vec2 stained() {
                if (uBlood == vec3(0.0) && uDirt == vec3(0.0)) {
                    return vec2(0.0);
                }
                // The arm at rest runs this way: along it a splash is three times as long as across.
                vec3 run = vec3(-0.5, -0.71, -0.47);
                vec3 drawn = vRest - run * (dot(vRest, run) * 0.66);
                float splash = 0.62 * cloud(drawn * 34.0) + 0.26 * cloud(drawn * 90.0 + 7.0) + 0.12 * cloud(vRest * 300.0);
                float smear = 0.6 * cloud(vRest * 40.0 + 19.0) + 0.4 * cloud(vRest * 150.0 + 3.0);
                float blood = share(uBlood), dirt = share(uDirt);
                float edge = 0.83 - 0.4 * blood;
                return vec2(smoothstep(0.02, 0.1, blood) * smoothstep(edge - 0.02, edge + 0.05, splash),
                        dirt * smoothstep(0.75 - 0.6 * dirt, 0.95 - 0.5 * dirt, smear));
            }

""" + PAPER_GLSL + """
            void main() {
                if (vAlong < uSpan.x || vAlong > uSpan.y) {
                    discard;
                }
                float stood = (uStood < 0.0 ? 255.0 : 128.0 + floor(clamp(uStood, 0.0, 1.0) * 126.0 + 0.5)) / 255.0;
                if (uWear > 6.5) {
                    // The sheet's own facing at this point, from its bend, so that a crease is sharp wherever it falls.
                    vec2 at = vUv - 0.5, e = vec2(0.002, 0.0);
                    float slopeAcross = (sheetBend(at + e) - sheetBend(at - e)) / (2.0 * e.x);
                    float slopeUp = (sheetBend(at + e.yx) - sheetBend(at - e.yx)) / (2.0 * e.x);
                    vec3 upward = vUpSheet + vOut * slopeUp;
                    vec3 n = normalize(cross(vAcross + vOut * slopeAcross, upward));
                    // A view that mirrors turns the cross product round: the face is the side vOut leaves.
                    n *= sign(dot(n, vOut));
                    // Looked at from behind, the eye sees the back of the paper.
                    bool back = dot(n, vSeen) > 0.0;
                    vec3 paint = paperShown(vUv, back, uPictured > 0.5, back ? -n : n);
                    if (uPictured > 0.5 && !back && inPrint(vUv)) {
                        vec2 fromPointer = (vUv - uPointerAt) * uMarker.zw;
                        if (pointer(fromPointer, 1.0)) {
                            paint = vec3(0.86, 0.2, 0.06) * paperTone(vUv);
                        } else if (pointer(fromPointer, 1.6)) {
                            paint = vec3(0.07, 0.06, 0.05);
                        }
                    }
                    vec3 facing = paperFacing(back ? -n : n, normalize(upward), vUv);
                    // Left out last, after everything that takes the change from pixel to pixel.
                    if (!onSheet(vUv)) {
                        discard;
                    }
                    vec2 kept = floor(folded(facing) * 255.0 + 0.5) / 255.0;
                    float what = 128.0 + 64.0 + (uOutside > 0.5 ? 16.0 : 0.0) + 6.0;
                    gl_FragData[0] = vec4(clamp(paint, 0.0, 1.0), stood);
                    gl_FragData[1] = vec4(kept, 0.04, what / 255.0);
                    return;
                }
                vec2 stain = stained();
                vec3 gore = vec3(0.34, 0.025, 0.02), grime = vec3(0.23, 0.18, 0.12);
                vec3 n = normalize(vNormal);
                vec3 along = normalize(vTangent - n * dot(n, vTangent));
                vec3 other = cross(n, along) * (vWay * uHanded);
                if (uWear > 0.5) {
                    // The texture is laid at one size all over the arm: so many threads to its width.
                    float count = uWear > 4.5 ? 900.0 : uWear > 3.5 ? 520.0 : 1100.0;
                    vec2 at = vUv * count;
                    float fine = max(length(dFdx(at)), length(dFdy(at)));
                    // Steel, brass and a shell's hull have no weave.
                    float shown = uWear > 5.5 ? 0.0 : 1.0 - smoothstep(0.35, 0.8, fine);
                    float here = 0.0, across = 0.0, down = 0.0;
                    if (shown > 0.0) {
                        here = threads(at);
                        across = (threads(at + vec2(0.05, 0.0)) - here) / 0.05;
                        down = (threads(at + vec2(0.0, 0.05)) - here) / 0.05;
                    }
                    vec3 facing = normalize(n - (along * across + other * down) * (0.12 * shown));
                    vec3 paint = uCloth * (0.86 + 0.2 * shown * here);
                    paint = mix(mix(paint, paint * grime * 2.2, stain.y), gore * 0.8, stain.x * 0.9);
                    // How glossy what is worn is, and how much nap it has, as CharacterDraw has them.
                    vec2 finish = uWear < 2.5 ? vec2(0.05, 0.5) : uWear < 3.5 ? vec2(0.07, 0.3) : uWear < 4.5 ? vec2(0.02, 0.9) : uWear < 5.5 ? vec2(0.6, 0.0) : vec2(0.45, 0.0);
                    // Snow settled on the sleeve or the glove, where it faces up: see snowOnPerson.
                    float snowLies = snowOnPerson(dot(n, uUpSeen), vRest.xz);
                    paint = mix(paint, SNOW_WHITE, snowLies);
                    facing = normalize(facing + uUpSeen * snowLies);
                    finish = mix(finish, vec2(0.0, 0.9), step(0.5, snowLies));
                    // Rain on the viewer (Soaked): cloth darkens and its nap lies flat, leather shines,
                    // and water stands in beads on leather and steel, less on a knit, least on a weave.
                    if (uWet.y > 0.0) {
                        float bare = 1.0 - step(0.5, snowLies);
                        paint *= 1.0 - (uWear > 5.5 ? 0.0 : uWear > 4.5 ? 0.2 : 0.4) * uWet.y * bare;
                        finish = vec2(max(finish.x, (uWear > 4.5 ? 0.7 : 0.25) * uWet.x * bare), finish.y * (1.0 - 0.8 * uWet.y * bare));
                        float water = waterOn(vRest, vDown, dot(n, uUpSeen)) * (uWear > 4.5 ? 1.0 : uWear > 3.5 ? 0.6 : 0.3) * bare;
                        facing = wetFacing(facing, vSeen, vRest, water);
                        finish.x = max(finish.x, 0.95 * smoothstep(0.0, 0.3, water));
                    }
                    vec2 kept = floor(folded(facing) * 255.0 + 0.5) / 255.0;
                    float what = 128.0 + 64.0 + (uOutside > 0.5 ? 16.0 : 0.0) + floor(finish.y * 15.0 + 0.5);
                    gl_FragData[0] = vec4(clamp(paint, 0.0, 1.0), stood);
                    gl_FragData[1] = vec4(kept, max(finish.x, 0.5 * stain.x), what / 255.0);
                    return;
                }
                vec4 colour = texture2D(uColour, vUv);
                vec4 surface = texture2D(uSurface, vUv);
                vec2 tilt = surface.xy * 2.0 - 1.0;
                vec3 facing = normalize(along * tilt.x + other * tilt.y + n * sqrt(max(1.0 - dot(tilt, tilt), 0.0)));
                // The pictures are painted as one skin, and turned to the body's own by as much as each texel follows it.
                vec3 paint = colour.rgb * mix(vec3(1.0), uTone / vec3(0.937, 0.788, 0.553), colour.a);
                // Between the fingers and in the folds of a joint less of the sky's light gets in.
                paint *= 0.55 + 0.45 * surface.a;
                paint = mix(mix(paint, paint * grime * 2.4, stain.y), gore, stain.x * 0.92);
                // Fingers are thin enough for light to come through.
                float thin = 0.7 * smoothstep(2.35, 2.75, vAlong) * (1.0 - stain.x);
                // A nail shines and skin does not, and blood shines.
                float gloss = max(0.6 * smoothstep(0.55, 0.75, surface.b), 0.45 * stain.x);
                // Snow settled on the back of the hand: where it lies thick it is what the surface is.
                float snowLies = snowOnPerson(dot(n, uUpSeen), vRest.xz);
                paint = mix(paint, SNOW_WHITE, snowLies);
                facing = normalize(facing + uUpSeen * snowLies);
                float snowy = step(0.5, snowLies);
                gloss *= 1.0 - snowy;
                // Rain on the viewer (Soaked): wet skin is a little darker and shines, and water beads on the back of the hand.
                if (uWet.y > 0.0) {
                    paint *= 1.0 - 0.1 * uWet.y * (1.0 - snowy);
                    gloss = max(gloss, 0.5 * uWet.x * (1.0 - snowy));
                    float water = waterOn(vRest, vDown, dot(n, uUpSeen)) * (1.0 - snowy);
                    facing = wetFacing(facing, vSeen, vRest, water);
                    gloss = max(gloss, 0.95 * smoothstep(0.0, 0.3, water));
                }
                vec2 kept = floor(folded(facing) * 255.0 + 0.5) / 255.0;
                float what = 128.0 + 32.0 + 32.0 * snowy + (uOutside > 0.5 ? 16.0 : 0.0) + floor(mix(thin, 0.9, snowy) * 15.0 + 0.5);
                gl_FragData[0] = vec4(clamp(paint, 0.0, 1.0), stood);
                gl_FragData[1] = vec4(kept, gloss, what / 255.0);
            }
            """;

    private static boolean tried;
    private static ArmModel model;
    private static int program, vertices, indices, colour, surface;
    private static int projectionAt, femaleAt, toneAt, outsideAt, stoodAt, handedAt, wearAt, clothAt, spanAt, bloodAt, dirtAt, snowAt, upAt, wetAt, wetClockAt, picturedAt, markerAt, sheetAt, stirAt, clockAt, pointerAt, sunSeenAt, scaleBarAt;
    /** How much snow has settled on the viewer, 0 to 1: see SnowCarried. */
    private static float snowOn;
    /** How wet the viewer is, by Soaked's WATER, SOAK and CLOCK. */
    private static float[] wetOn = new float[Soaked.FLOATS];
    /** The first of uBones: the rest follow it, so one call sets them all. */
    private static int bonesAt;
    private static final FloatBuffer loaded = ByteBuffer.allocateDirect(MOST_BONES * 16 * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();

    /** The arm's bones and poses, or null if the jar's file or the driver would not have it, which the log then says once. */
    static ArmModel model() {
        if (!tried) {
            tried = true;
            try (InputStream packed = OwnArms.class.getResourceAsStream(MESH)) {
                if (packed == null) {
                    throw new IOException("the jar has no " + MESH);
                }
                ArmModel read = new ArmModel(packed);
                if (read.bones.length > MOST_BONES) {
                    throw new IOException("the arms file has " + read.bones.length + " bones and the program has room for " + MOST_BONES);
                }
                if (refused != null) {
                    throw new IllegalStateException(refused);
                }
                if (program == 0) {
                    link();
                }
                make(read);
                makeProps();
                colour = texture(COLOUR);
                surface = texture(SURFACE);
                model = read;
                Log.info("the arms on a held weapon are the mod's own: " + read.vertexCount + " corners, " + read.indexCount / 3 + " triangles");
            } catch (IOException | IllegalStateException e) {
                giveBack();
                Log.info("the arms on a held weapon are the game's own: " + e.getMessage());
            }
        }
        return model;
    }

    /**
     * Gives the arms' buffers and pictures back to the graphics card, and whatever was unpacked
     * ahead for a making that will not now happen. The program is kept. Render thread.
     */
    static void letGo() {
        for (String path : new String[] {COLOUR, SURFACE}) {
            Ahead.forget(path);
        }
        giveBack();
        model = null;
        tried = false;
    }

    private static void giveBack() {
        GL11.glDeleteTextures(colour);
        GL11.glDeleteTextures(surface);
        GL15.glDeleteBuffers(vertices);
        GL15.glDeleteBuffers(indices);
        GL15.glDeleteBuffers(props);
        GL15.glDeleteBuffers(propIndices);
        colour = 0;
        surface = 0;
        vertices = 0;
        indices = 0;
        props = 0;
        propIndices = 0;
    }

    /** What the driver refused when the program was linked, said when the arms are first made. */
    private static String refused;

    /**
     * Links the program, as a piece of the core's shaders, whether or not the arms are switched
     * on. Always null: a refusal is said when the arms are first made, and the game's own arms
     * are drawn, as when it was linked then.
     */
    static String linkProgram() {
        if (program == 0 && refused == null) {
            try {
                link();
            } catch (IllegalStateException e) {
                refused = e.getMessage();
            }
        }
        return null;
    }

    /** Links the program, once: it is kept when the buffers and pictures are given back. */
    private static void link() {
        long start = System.nanoTime();
        try {
            int id = GL20.glCreateProgram();
            int[] kinds = {GL_VERTEX_SHADER, GL_FRAGMENT_SHADER};
            String[] sources = {VERTEX, FRAGMENT};
            for (int stage = 0; stage < 2; stage++) {
                int shader = GL20.glCreateShader(kinds[stage]);
                GL20.glShaderSource(shader, sources[stage]);
                GL20.glCompileShader(shader);
                if (GL20.glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
                    String log = GL20.glGetShaderInfoLog(shader);
                    GL20.glDeleteShader(shader);
                    GL20.glDeleteProgram(id);
                    throw new IllegalStateException("stage " + (stage + 1) + " of the arms shader did not compile: " + log);
                }
                GL20.glAttachShader(id, shader);
                // The program keeps what it was linked from: the shader goes when the program does.
                GL20.glDeleteShader(shader);
            }
            for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
                GL20.glBindAttribLocation(id, attribute, ATTRIBUTES[attribute]);
            }
            GL20.glLinkProgram(id);
            if (GL20.glGetProgrami(id, GL_LINK_STATUS) == 0) {
                String log = GL20.glGetProgramInfoLog(id);
                GL20.glDeleteProgram(id);
                throw new IllegalStateException("the arms shader did not link: " + log);
            }
            program = id;
            projectionAt = GL20.glGetUniformLocation(id, "uProjection");
            femaleAt = GL20.glGetUniformLocation(id, "uFemale");
            toneAt = GL20.glGetUniformLocation(id, "uTone");
            outsideAt = GL20.glGetUniformLocation(id, "uOutside");
            stoodAt = GL20.glGetUniformLocation(id, "uStood");
            handedAt = GL20.glGetUniformLocation(id, "uHanded");
            wearAt = GL20.glGetUniformLocation(id, "uWear");
            clothAt = GL20.glGetUniformLocation(id, "uCloth");
            spanAt = GL20.glGetUniformLocation(id, "uSpan");
            bloodAt = GL20.glGetUniformLocation(id, "uBlood");
            dirtAt = GL20.glGetUniformLocation(id, "uDirt");
            snowAt = GL20.glGetUniformLocation(id, "uSnowOn");
            upAt = GL20.glGetUniformLocation(id, "uUpSeen");
            wetAt = GL20.glGetUniformLocation(id, "uWet");
            wetClockAt = GL20.glGetUniformLocation(id, "uWetClock");
            picturedAt = GL20.glGetUniformLocation(id, "uPictured");
            markerAt = GL20.glGetUniformLocation(id, "uMarker");
            sheetAt = GL20.glGetUniformLocation(id, "uSheet");
            stirAt = GL20.glGetUniformLocation(id, "uStir");
            clockAt = GL20.glGetUniformLocation(id, "uClock");
            pointerAt = GL20.glGetUniformLocation(id, "uPointerAt");
            sunSeenAt = GL20.glGetUniformLocation(id, "uSunSeen");
            scaleBarAt = GL20.glGetUniformLocation(id, "uScaleBar");
            Program.useOther(id);
            GL20.glUniform1i(GL20.glGetUniformLocation(id, "uColour"), COLOUR_UNIT);
            GL20.glUniform1i(GL20.glGetUniformLocation(id, "uSurface"), SURFACE_UNIT);
            Program.useOther(0);
            bonesAt = GL20.glGetUniformLocation(id, "uBones[0]");
        } finally {
            Shaders.linked("arms", start);
        }
    }

    private static void make(ArmModel read) {
        vertices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
        GL15.glBufferData(GL_ARRAY_BUFFER, read.vertices, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        indices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, read.indices, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
        read.vertices = null;
        read.indices = null;
    }

    /**
     * What a hand carries, turned out of its measurements: each piece a run of the indices,
     * with its colour. A magazine is a box of blued steel. A shell is a red hull on a brass
     * head, and a cartridge a brass case with a copper bullet in it.
     */
    private static int props, propIndices;
    private static final int[] pieceFirst = new int[8], pieceCount = new int[8];
    /** The pieces a watch and a sheet are made of: a box of side one, a disc of radius one and one thick, and a square of side one facing +z. */
    private static final int BOX = 5, DISC = 6, SQUARE = 7;
    /** How many cells the square is divided into across and up. */
    private static final int SQUARE_ACROSS = 24, SQUARE_UP = 16;
    /** What a sheet of paper is drawn as, past what CharacterDraw names. */
    private static final int PAPER = 7;
    private static final int[][] PIECES = {{}, {0}, {1, 2}, {3, 4}};
    private static final float[][] PIECE_COLOUR = {{0.07f, 0.07f, 0.08f}, {0.5f, 0.07f, 0.06f}, {0.62f, 0.47f, 0.2f}, {0.62f, 0.47f, 0.2f}, {0.6f, 0.35f, 0.2f}};
    private static final int ROUND_SIDES = 16;

    private static void makeProps() {
        OffHeap.Block cornerMemory = OffHeap.take(4096 * ArmModel.VERTEX_BYTES), triangleMemory = OffHeap.take(8192 * Integer.BYTES);
        try {
            makeProps(cornerMemory.bytes, triangleMemory.bytes);
        } finally {
            cornerMemory.free();
            triangleMemory.free();
        }
    }

    private static void makeProps(ByteBuffer corners, ByteBuffer triangles) {
        int[] count = {0};
        // The magazine: its length up y.
        pieceFirst[0] = triangles.position() / Integer.BYTES;
        float w = 0.011f, d = 0.016f, h = ArmHolds.MAGAZINE_LONG;
        float[][] faces = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (float[] n : faces) {
            float[] u = {n[1], n[2], n[0]}, v = {n[2], n[0], n[1]};
            int first = count[0];
            for (int corner = 0; corner < 4; corner++) {
                float a = corner == 0 || corner == 3 ? -1f : 1f, b = corner < 2 ? -1f : 1f;
                float x = n[0] + u[0] * a + v[0] * b, y = n[1] + u[1] * a + v[1] * b, z = n[2] + u[2] * a + v[2] * b;
                corner(corners, count, x * w, (y + 1f) * h / 2f, z * d, n[0], n[1], n[2]);
            }
            triangles.putInt(first).putInt(first + 1).putInt(first + 2).putInt(first).putInt(first + 2).putInt(first + 3);
        }
        pieceCount[0] = triangles.position() / Integer.BYTES - pieceFirst[0];
        // The shell's hull, its head, a cartridge's case and its bullet: each turned about y.
        float[][][] turned = {
            {{0.0100f, 0.014f}, {0.0100f, 0.058f}, {0.0085f, 0.062f}},
            {{0.0112f, 0f}, {0.0112f, 0.002f}, {0.0103f, 0.003f}, {0.0103f, 0.015f}},
            {{0.0058f, 0f}, {0.0058f, 0.038f}, {0.0042f, 0.044f}},
            {{0.0040f, 0.043f}, {0.0036f, 0.052f}, {0.0012f, 0.062f}},
        };
        for (int piece = 0; piece < turned.length; piece++) {
            pieceFirst[piece + 1] = triangles.position() / Integer.BYTES;
            float[][] profile = turned[piece];
            for (int ring = 0; ring + 1 < profile.length; ring++) {
                float slope = (profile[ring][0] - profile[ring + 1][0]) / (profile[ring + 1][1] - profile[ring][1]);
                int first = count[0];
                for (int side = 0; side <= ROUND_SIDES; side++) {
                    double round = 2 * Math.PI * side / ROUND_SIDES;
                    float cos = (float) Math.cos(round), sin = (float) Math.sin(round);
                    corner(corners, count, cos * profile[ring][0], profile[ring][1], sin * profile[ring][0], cos, slope, sin);
                    corner(corners, count, cos * profile[ring + 1][0], profile[ring + 1][1], sin * profile[ring + 1][0], cos, slope, sin);
                }
                for (int side = 0; side < ROUND_SIDES; side++) {
                    int at = first + side * 2;
                    triangles.putInt(at).putInt(at + 1).putInt(at + 2).putInt(at + 2).putInt(at + 1).putInt(at + 3);
                }
            }
            for (int end = 0; end < 2; end++) {
                float[] ring = profile[end == 0 ? 0 : profile.length - 1];
                int middle = count[0];
                corner(corners, count, 0f, ring[1], 0f, 0f, end == 0 ? -1f : 1f, 0f);
                for (int side = 0; side <= ROUND_SIDES; side++) {
                    double round = 2 * Math.PI * side / ROUND_SIDES;
                    corner(corners, count, (float) Math.cos(round) * ring[0], ring[1], (float) Math.sin(round) * ring[0], 0f, end == 0 ? -1f : 1f, 0f);
                }
                for (int side = 0; side < ROUND_SIDES; side++) {
                    triangles.putInt(middle).putInt(middle + 1 + side).putInt(middle + 2 + side);
                }
            }
            pieceCount[piece + 1] = triangles.position() / Integer.BYTES - pieceFirst[piece + 1];
        }
        pieceFirst[BOX] = triangles.position() / Integer.BYTES;
        for (float[] n : faces) {
            float[] u = {n[1], n[2], n[0]}, v = {n[2], n[0], n[1]};
            int first = count[0];
            for (int corner = 0; corner < 4; corner++) {
                float a = corner == 0 || corner == 3 ? -1f : 1f, b = corner < 2 ? -1f : 1f;
                corner(corners, count, (n[0] + u[0] * a + v[0] * b) / 2f, (n[1] + u[1] * a + v[1] * b) / 2f, (n[2] + u[2] * a + v[2] * b) / 2f, n[0], n[1], n[2]);
            }
            triangles.putInt(first).putInt(first + 1).putInt(first + 2).putInt(first).putInt(first + 2).putInt(first + 3);
        }
        pieceCount[BOX] = triangles.position() / Integer.BYTES - pieceFirst[BOX];
        pieceFirst[DISC] = triangles.position() / Integer.BYTES;
        int sides = 2 * ROUND_SIDES, rim = count[0];
        for (int side = 0; side <= sides; side++) {
            double round = 2 * Math.PI * side / sides;
            float cos = (float) Math.cos(round), sin = (float) Math.sin(round);
            corner(corners, count, cos, -0.5f, sin, cos, 0f, sin);
            corner(corners, count, cos, 0.5f, sin, cos, 0f, sin);
        }
        for (int side = 0; side < sides; side++) {
            int at = rim + side * 2;
            triangles.putInt(at).putInt(at + 1).putInt(at + 2).putInt(at + 2).putInt(at + 1).putInt(at + 3);
        }
        for (int end = 0; end < 2; end++) {
            float y = end == 0 ? -0.5f : 0.5f, ny = end == 0 ? -1f : 1f;
            int middle = count[0];
            corner(corners, count, 0f, y, 0f, 0f, ny, 0f);
            for (int side = 0; side <= sides; side++) {
                double round = 2 * Math.PI * side / sides;
                corner(corners, count, (float) Math.cos(round), y, (float) Math.sin(round), 0f, ny, 0f);
            }
            for (int side = 0; side < sides; side++) {
                triangles.putInt(middle).putInt(middle + 1 + side).putInt(middle + 2 + side);
            }
        }
        pieceCount[DISC] = triangles.position() / Integer.BYTES - pieceFirst[DISC];
        // The square is a grid, for the sheet to be bent on, its lines on the creases (TimedActs.bend): every sixth across and the eighth up.
        pieceFirst[SQUARE] = triangles.position() / Integer.BYTES;
        int square = count[0];
        for (int row = 0; row <= SQUARE_UP; row++) {
            for (int column = 0; column <= SQUARE_ACROSS; column++) {
                float u = column / (float) SQUARE_ACROSS, v = row / (float) SQUARE_UP;
                corner(corners, count, u - 0.5f, v - 0.5f, 0f, 0f, 0f, 1f, u, v);
            }
        }
        for (int row = 0; row < SQUARE_UP; row++) {
            for (int column = 0; column < SQUARE_ACROSS; column++) {
                int at = square + row * (SQUARE_ACROSS + 1) + column, above = at + SQUARE_ACROSS + 1;
                triangles.putInt(at).putInt(at + 1).putInt(above + 1).putInt(at).putInt(above + 1).putInt(above);
            }
        }
        pieceCount[SQUARE] = triangles.position() / Integer.BYTES - pieceFirst[SQUARE];
        corners.flip();
        triangles.flip();
        props = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, props);
        GL15.glBufferData(GL_ARRAY_BUFFER, corners, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        propIndices = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, propIndices);
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, triangles, GL_STATIC_DRAW);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    /** One corner in the arm's own layout, hung whole on the first bone. */
    private static void corner(ByteBuffer corners, int[] count, float x, float y, float z, float nx, float ny, float nz) {
        corner(corners, count, x, y, z, nx, ny, nz, 0f, 0f);
    }

    /** One corner with a place on a picture. */
    private static void corner(ByteBuffer corners, int[] count, float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        corners.putFloat(x).putFloat(y).putFloat(z).putFloat(nx / length).putFloat(ny / length).putFloat(nz / length);
        // Along the texture: any line in the surface will do for what has no weave.
        corners.putFloat(ny / length).putFloat(nz / length).putFloat(nx / length).putFloat(1f);
        corners.putFloat(u).putFloat(v).putFloat(0f).putFloat(0f).putFloat(0f);
        corners.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 255).put((byte) 0).put((byte) 0).put((byte) 0);
        corners.putFloat(0f);
        count[0]++;
    }

    /** How much snow has settled on the viewer whose arms are drawn next, 0 to 1. */
    static void snowed(float dust) {
        snowOn = dust;
    }

    /** How wet the viewer whose arms are drawn next is, as Soaked.on has it: not changed here. */
    static void wetted(float[] wet) {
        wetOn = wet;
    }

    /** Tells the program in use how much snow is on the viewer and how wet they are, and the world's up as the eye of the view sees it. */
    private static void snowed() {
        float[] up = CharacterDraw.upSeen();
        GL20.glUniform1f(snowAt, snowOn);
        GL20.glUniform3f(upAt, up[0], up[1], up[2]);
        GL20.glUniform2f(wetAt, wetOn[Soaked.WATER], wetOn[Soaked.SOAK]);
        GL20.glUniform1f(wetClockAt, wetOn[Soaked.CLOCK]);
    }

    /**
     * Draws what a hand carries, as an arm is drawn.
     *
     * @param what one of ArmHolds' things carried
     * @param at   the matrix from its own measure, metres with its length along y, to the view
     * @param light the light what the viewer holds is in, as draw takes it
     */
    static void carried(float[] projection, int what, float[] at, float light, boolean outside) {
        if (what == ArmHolds.NOTHING) {
            return;
        }
        beginProps(projection, light, outside);
        bone(at, 0);
        for (int piece : PIECES[what]) {
            GL20.glUniform3f(clothAt, PIECE_COLOUR[piece][0], PIECE_COLOUR[piece][1], PIECE_COLOUR[piece][2]);
            GL11.glDrawElements(GL_TRIANGLES, pieceCount[piece], GL_UNSIGNED_INT, (long) pieceFirst[piece] * Integer.BYTES);
        }
        endProps();
    }

    /** The watch as last laid out, and what it was laid out for: its look, the minute of the day it shows, the wrist it is on and how thick that is. */
    private static final WatchFace.Pieces watchPieces = new WatchFace.Pieces();
    private static String watchType;
    private static boolean watchLeft, watchDigital, watchTwentyFour;
    private static int watchMinute = -1;
    private static float watchUp, watchAround;
    private static final float[] watchAt = new float[16], pieceAt = new float[16];

    /**
     * Draws the watch on the wrist of an arm drawn with skins, over whatever is worn there.
     *
     * @param skins as draw takes them, for the arm the watch is on
     */
    static void watch(float[] projection, float[] skins, ViewModel.Watch watch, boolean female, Worn worn, float light, boolean outside) {
        float up = 0f, around = 0f;
        for (int part = ArmModel.ARM; part <= ArmModel.GLOVE; part++) {
            boolean covers = part == ArmModel.ARM || part == ArmModel.SLEEVE && worn.sleeve() && worn.sleeveTo() >= model.watchAlong
                    || part == ArmModel.GLOVE && worn.glove() && GLOVE_FROM <= model.watchAlong;
            if (covers) {
                up = Math.max(up, model.watchRadii[part * 4 + (female ? 2 : 0)]);
                around = Math.max(around, model.watchRadii[part * 4 + (female ? 3 : 1)]);
            }
        }
        int minute = (int) Math.floor(watch.timeOfDay() * 60f);
        if (minute != watchMinute || !watch.type().equals(watchType) || watch.left() != watchLeft || watch.digital() != watchDigital
                || watch.twentyFour() != watchTwentyFour || up != watchUp || around != watchAround) {
            WatchFace.lay(WatchFace.look(watch.type(), watch.digital()), watch.digital(), watch.left(), minute / 60f, watch.twentyFour(), up, around, watchPieces);
            watchMinute = minute;
            watchType = watch.type();
            watchLeft = watch.left();
            watchDigital = watch.digital();
            watchTwentyFour = watch.twentyFour();
            watchUp = up;
            watchAround = around;
        }
        ArmPose.multiply(skins, model.foreTwist * 16, model.watchRest, 0, watchAt, 0);
        beginProps(projection, light, outside);
        for (int piece = 0; piece < watchPieces.count; piece++) {
            ArmPose.multiply(watchAt, 0, watchPieces.matrices, piece * 16, pieceAt, 0);
            bone(pieceAt, 0);
            float[] colour = watchPieces.colour[piece];
            GL20.glUniform3f(clothAt, colour[0], colour[1], colour[2]);
            GL20.glUniform1f(wearAt, watchPieces.wear[piece]);
            int shape = watchPieces.kind[piece] == WatchFace.BOX ? BOX : DISC;
            GL11.glDrawElements(GL_TRIANGLES, pieceCount[shape], GL_UNSIGNED_INT, (long) pieceFirst[shape] * Integer.BYTES);
        }
        endProps();
    }

    /** The sun's way in the world's axes and how strong it is, 0 to 1, for the frame's drawing of the viewer: see sun. */
    private static float sunX, sunY, sunUp, sunStrength;

    /** Says the sun the viewer's held things are drawn in this frame: its way in the world's axes, and how strong it is, 0 to 1. */
    static void sun(float x, float y, float up, float strength) {
        sunX = x;
        sunY = y;
        sunUp = up;
        sunStrength = strength;
    }

    /** The lengths a scale bar may be, in tiles: the one nearest SCALE_BAR_LONG metres of the sheet is printed. */
    private static final float[] SCALE_TILES = {10f, 20f, 25f, 50f, 100f, 200f, 250f, 500f, 1000f, 2000f};
    private static final float SCALE_BAR_LONG = 0.08f;

    /** How long the scale bar is on a sheet whose width covers tilesAcross tiles, metres of the sheet, or nought for none. */
    static float scaleBar(float tilesAcross) {
        if (!(tilesAcross > 0f)) {
            return 0f;
        }
        float best = 0f;
        for (float tiles : SCALE_TILES) {
            float metres = tiles / tilesAcross * TimedActs.SHEET_WIDE;
            if (Math.abs(metres - SCALE_BAR_LONG) < Math.abs(best - SCALE_BAR_LONG)) {
                best = metres;
            }
        }
        return best;
    }

    /**
     * Draws the held map's sheet of paper, bent as TimedActs.bend has it, with the map printed on it.
     *
     * @param at      the matrix from a square of side one, across x and up y, facing +z, to the view
     * @param picture the texture of the picture on it, or nought for blank paper
     * @param east    with north, the way the viewer faces on the map, drawn as a pointer where they are
     * @param task    the hands' task, which says where the viewer is on the sheet, its scale and how the walk stirs it
     */
    static void sheet(float[] projection, float[] at, int picture, float east, float north, ViewModel.Task task, float light, boolean outside) {
        beginProps(projection, light, outside);
        bone(at, 0);
        GL20.glUniform1f(wearAt, PAPER);
        GL20.glUniform3f(clothAt, TimedActs.PAPER[0], TimedActs.PAPER[1], TimedActs.PAPER[2]);
        GL20.glUniform1f(picturedAt, picture != 0 ? 1f : 0f);
        GL20.glUniform4f(markerAt, east, north, TimedActs.SHEET_WIDE, TimedActs.SHEET_TALL);
        GL20.glUniform2f(pointerAt, task.pointerU(), task.pointerV());
        GL20.glUniform1f(scaleBarAt, scaleBar(task.tilesAcross()));
        GL20.glUniform1f(sheetAt, 1f);
        GL20.glUniform1f(stirAt, task.stir());
        GL20.glUniform1f(clockAt, (float) ((System.nanoTime() / 1e9) % 3600.0));
        // The view's matrix by columns: the world's axes as the eye sees them (CharacterDraw.looking).
        float[] seen = CharacterDraw.axesSeen();
        float x = sunX * seen[0] + sunY * seen[3] + sunUp * seen[6], y = sunX * seen[1] + sunY * seen[4] + sunUp * seen[7],
                z = sunX * seen[2] + sunY * seen[5] + sunUp * seen[8];
        float length = (float) Math.sqrt(x * x + y * y + z * z), strong = length > 0f ? sunStrength / length : 0f;
        GL20.glUniform3f(sunSeenAt, x * strong, y * strong, z * strong);
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, picture);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glDrawElements(GL_TRIANGLES, pieceCount[SQUARE], GL_UNSIGNED_INT, (long) pieceFirst[SQUARE] * Integer.BYTES);
        GL20.glUniform1f(picturedAt, 0f);
        GL20.glUniform1f(sheetAt, 0f);
        endProps();
    }

    /** Sets the first bone, which every corner of a prop hangs on, to a matrix. */
    private static void bone(float[] matrix, int at) {
        loaded.clear();
        loaded.put(matrix, at, 16).flip();
        GL20.glUniformMatrix4fv(bonesAt, false, loaded);
    }

    /** Readies the program to draw props, in what SLEEK is unless a piece says otherwise. */
    private static void beginProps(float[] projection, float light, boolean outside) {
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LESS);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        Program.useOther(program);
        GL20.glUniformMatrix4fv(projectionAt, false, projection);
        GL20.glUniform1f(femaleAt, 0f);
        GL20.glUniform1f(outsideAt, outside ? 1f : 0f);
        GL20.glUniform1f(stoodAt, light);
        GL20.glUniform1f(handedAt, 1f);
        GL20.glUniform1f(wearAt, CharacterDraw.SLEEK);
        GL20.glUniform2f(spanAt, -1f, 4f);
        GL20.glUniform3f(bloodAt, 0f, 0f, 0f);
        GL20.glUniform3f(dirtAt, 0f, 0f, 0f);
        snowed();
        GL20.glDrawBuffers(BOTH);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, props);
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            boolean bytes = attribute == BONES || attribute == WEIGHTS;
            GL20.glVertexAttribPointer(attribute, SIZES[attribute], bytes ? GL_UNSIGNED_BYTE : GL_FLOAT, attribute == WEIGHTS, ArmModel.VERTEX_BYTES, OFFSETS[attribute]);
            GL20.glEnableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, propIndices);
    }

    private static void endProps() {
        GL20.glDrawBuffers(FIRST);
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void part(int which) {
        GL11.glDrawElements(GL_TRIANGLES, model.partCount[which], GL_UNSIGNED_INT, (long) model.partFirst[which] * Integer.BYTES);
    }

    /** Begins reading the arms' pictures on other threads, for {@link #prepare} to find them read. */
    static void readAhead() {
        ahead = Own.arms();
        for (String path : ahead ? new String[] {COLOUR, SURFACE} : new String[0]) {
            Ahead.begin(path, () -> Pictures.packed(path, false));
        }
    }

    private static boolean ahead;

    /** Whether everything {@link #readAhead} began is read. */
    static boolean isRead() {
        return !ahead || Ahead.done(COLOUR) && Ahead.done(SURFACE);
    }

    /**
     * Makes the arms ready, if they are switched on: switched on later, they are made on the
     * frame that first draws them. Always null: without them the game's own arms are drawn,
     * which the log then says, and the rest of the picture is none the worse.
     */
    static String prepare() {
        if (ahead) {
            model();
        }
        return null;
    }

    private static int texture(String path) throws IOException {
        OffHeap.Block file = Ahead.take(path, () -> Pictures.packed(path, false));
        try {
            // A forearm is seen along its length, so it is read with 8 samples along the slant.
            int made = Pictures.texture(file, GL_TEXTURE_2D, false, 8, path);
            GL11.glBindTexture(GL_TEXTURE_2D, 0);
            return made;
        } finally {
            file.free();
        }
    }

    /**
     * What an arm wears.
     *
     * @param sleeve      whether anything is worn over the arm, and how far down it comes as the model counts along the arm: 1 the elbow, 2 the wrist
     * @param sleeveWear  what it is made of, as CharacterDraw calls it
     * @param sleeveColour its colour
     * @param glove       whether a glove is worn, what of, and its colour
     */
    record Worn(boolean sleeve, float sleeveTo, int sleeveWear, float[] sleeveColour, boolean glove, int gloveWear, float[] gloveColour) {}

    static final Worn BARE = new Worn(false, 0f, 0, null, false, 0, null);
    /** Where tools/models/arms.py cut a long sleeve and a glove (ArmModel.sleeveEnd is where the sleeve ends), and where a short sleeve ends. */
    static final float CUFF_AT = 1.93f, GLOVE_FROM = 1.86f, SHORT_TO = 0.55f;
    /** Skin under what is worn is not drawn to within this of its edge: the cloth is smoothed, and a muscle would come through it. */
    private static final float UNDER = 0.06f;

    /**
     * Draws one arm into whatever target is bound, which has a second picture for the way
     * surfaces face.
     *
     * @param skins   for each bone, the matrix from the model at rest to the view, as ArmPose found it
     * @param left    whether it is the left arm, whose matrices mirror the model
     * @param female  whether it is a woman's
     * @param skin    which of the game's five skins the body wears, 0 the palest
     * @param light   the light what the viewer holds is in (HeldLight), or less than nought where the pass that lights them gives the light
     * @param outside whether the viewer stands out of doors
     * @param stains  how bloody and how dirty what shows of each arm is, as Cabins.stains has it
     */
    static void draw(float[] projection, float[] skins, boolean left, boolean female, int skin, float light, boolean outside, Worn worn, float[] stains) {
        GL11.glEnable(GL_CULL_FACE);
        GL11.glCullFace(left ? GL_FRONT : GL_BACK);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LESS);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_BLEND);
        Program.useOther(program);
        GL20.glUniformMatrix4fv(projectionAt, false, projection);
        loaded.clear();
        loaded.put(skins, 0, model.bones.length * 16).flip();
        GL20.glUniformMatrix4fv(bonesAt, false, loaded);
        GL20.glUniform1f(femaleAt, female ? 1f : 0f);
        float[] tone = TONES[Math.clamp(skin, 0, TONES.length - 1)];
        GL20.glUniform3f(toneAt, tone[0], tone[1], tone[2]);
        GL20.glUniform1f(outsideAt, outside ? 1f : 0f);
        GL20.glUniform1f(stoodAt, light);
        GL20.glUniform1f(handedAt, left ? -1f : 1f);
        int at = left ? 6 : 0;
        GL20.glUniform3f(bloodAt, stains[at], stains[at + 2], stains[at + 4]);
        GL20.glUniform3f(dirtAt, stains[at + 1], stains[at + 3], stains[at + 5]);
        snowed();
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, colour);
        GL13.glActiveTexture(GL_TEXTURE0 + SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, surface);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL20.glDrawBuffers(BOTH);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, vertices);
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            boolean bytes = attribute == BONES || attribute == WEIGHTS;
            GL20.glVertexAttribPointer(attribute, SIZES[attribute], bytes ? GL_UNSIGNED_BYTE : GL_FLOAT, attribute == WEIGHTS, ArmModel.VERTEX_BYTES, OFFSETS[attribute]);
            GL20.glEnableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, indices);
        float from = worn.sleeve() ? Math.min(worn.sleeveTo(), model.sleeveEnd) - UNDER : -1f, to = worn.glove() ? GLOVE_FROM + UNDER : 4f;
        // A glove under a long sleeve leaves no skin to show between them.
        if (!(worn.glove() && worn.sleeve() && worn.sleeveTo() > GLOVE_FROM)) {
            GL20.glUniform1f(wearAt, 0f);
            GL20.glUniform2f(spanAt, from, to);
            part(ArmModel.ARM);
        }
        if (worn.sleeve()) {
            GL20.glUniform1f(wearAt, worn.sleeveWear());
            GL20.glUniform3f(clothAt, worn.sleeveColour()[0], worn.sleeveColour()[1], worn.sleeveColour()[2]);
            GL20.glUniform2f(spanAt, -1f, worn.sleeveTo());
            part(ArmModel.SLEEVE);
        }
        if (worn.glove()) {
            GL20.glUniform1f(wearAt, worn.gloveWear());
            GL20.glUniform3f(clothAt, worn.gloveColour()[0], worn.gloveColour()[1], worn.gloveColour()[2]);
            GL20.glUniform2f(spanAt, -1f, 4f);
            part(ArmModel.GLOVE);
        }
        GL20.glDrawBuffers(FIRST);
        for (int attribute = 0; attribute < ATTRIBUTES.length; attribute++) {
            GL20.glDisableVertexAttribArray(attribute);
        }
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }
}
