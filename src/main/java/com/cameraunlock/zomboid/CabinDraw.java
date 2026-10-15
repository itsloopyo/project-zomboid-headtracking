package com.cameraunlock.zomboid;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Draws the mod's own interior of the vehicle a viewer sits in: its shell lit in the light of the
 * frame, the sun in it through the glass by a shadow map of its own, the wheel turned as the
 * vehicle is steered, the needles turned to what the instruments read, the mirrors showing the
 * road behind, and the glass over it all.
 *
 * All of it is the render thread's.
 */
final class CabinDraw {
    private static final int GL_TEXTURE_2D = 0x0DE1, GL_TEXTURE0 = 0x84C0;
    private static final int GL_ARRAY_BUFFER = 0x8892, GL_ELEMENT_ARRAY_BUFFER = 0x8893, GL_STATIC_DRAW = 0x88E4;
    private static final int GL_FLOAT = 0x1406, GL_SHORT = 0x1402, GL_UNSIGNED_SHORT = 0x1403, GL_UNSIGNED_BYTE = 0x1401, GL_UNSIGNED_INT = 0x1405;
    private static final int GL_TRIANGLES = 0x0004, GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_BLEND = 0x0BE2, GL_LEQUAL = 0x0203, GL_LESS = 0x0201;
    private static final int GL_FRAMEBUFFER = 0x8D40, GL_DEPTH_ATTACHMENT = 0x8D00, GL_FRAMEBUFFER_COMPLETE = 0x8CD5;
    private static final int GL_DEPTH_COMPONENT24 = 0x81A6, GL_DEPTH_COMPONENT = 0x1902;
    private static final int GL_TEXTURE_MIN_FILTER = 0x2801, GL_TEXTURE_MAG_FILTER = 0x2800, GL_LINEAR = 0x2601, GL_TEXTURE_WRAP_S = 0x2802, GL_TEXTURE_WRAP_T = 0x2803;
    private static final int GL_CLAMP_TO_EDGE = 0x812F, GL_TEXTURE_COMPARE_MODE = 0x884C, GL_COMPARE_REF_TO_TEXTURE = 0x884E, GL_TEXTURE_COMPARE_FUNC = 0x884D;
    private static final int GL_NONE = 0, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303, GL_ZERO = 0, GL_ONE = 1;
    private static final int GL_DEPTH_BUFFER_BIT = 0x100;

    /** Units the picture binds afresh for each pass that reads them. */
    static final int COLOUR_UNIT = 30, SURFACE_UNIT = 31, CABIN_SHADOW_UNIT = 29, TORCH_UNIT = 27, DETAIL_SURFACE_UNIT = 28;
    private static final int GL_TEXTURE_2D_ARRAY = 0x8C1A;
    /** The repeating close-up surfaces every interior shares, read once. */
    private int detailSurface;
    /** The size of the shadow map the sun casts in the cabin, and how far round the cabin's middle it takes in. */
    private static final int SHADOW_SIZE = 2048;
    private static final float SHADOW_REACH = 1.9f;
    /** The cabin's middle in its own frame, which the shadow map is centred on. */
    private static final float[] MIDDLE = {-0.35f, -0.32f, -0.30f};

    private static final String VERTEX = """
            #version 120
            attribute vec3 aPlace;
            attribute vec3 aNormal;
            attribute vec3 aTangent;
            attribute float aWay;
            attribute vec2 aUv;
            attribute float aSky;
            attribute float aNear;
            attribute float aFinish;
            attribute float aKind;
            // From the interior's own frame to the world, less the eye: the vehicle's axes and where its wheel is.
            uniform mat4 uModel;
            // From there to the screen, or to the sun's shadow map.
            uniform mat4 uSeen;
            // How the part drawn is turned about its own pivot, in the interior's frame: the wheel, a needle.
            uniform mat3 uTurn;
            uniform vec3 uPivot;
            // How far the parts are pushed away from the eye: only in the envelope, below.
            uniform float uPush;
            varying vec3 vRel;
            varying vec3 vLocal;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying vec3 vOther;
            varying vec2 vUv;
            varying float vSky;
            varying vec2 vFinish;
            varying float vNear;
            varying float vKind;
            varying vec3 vLocalNormal;

            void main() {
                vNear = aNear;
                vKind = floor(aKind * 255.0 + 0.5);
                vLocalNormal = uTurn * aNormal;
                // The finish byte: how much the part takes the trim's tint, low four bits, and how metal it is, high four.
                float finish = floor(aFinish * 255.0 + 0.5);
                vFinish = vec2(mod(finish, 16.0), floor(finish / 16.0)) / 15.0;
                vec3 local = uPivot + uTurn * (aPlace - uPivot);
                vec3 n = uTurn * aNormal;
                vec3 t = uTurn * aTangent;
                vec4 rel = uModel * vec4(local, 1.0);
                rel.xyz += normalize(rel.xyz) * uPush;
                vRel = rel.xyz;
                vLocal = local;
                vNormal = mat3(uModel) * n;
                vTangent = mat3(uModel) * t;
                // Worked out in the interior's frame and turned with it: the world's map is a mirror, which would turn a
                // cross product taken there the other way.
                vOther = mat3(uModel) * (cross(n, t) * aWay);
                vUv = aUv;
                vSky = aSky;
                gl_Position = uSeen * rel;
            }
            """;

    /** The shadow map's own: where each part is, as the sun sees it. */
    private static final String SHADOW_FRAGMENT = """
            #version 120
            void main() {
                gl_FragColor = vec4(1.0);
            }
            """;

    private static final String FRAGMENT = "#version 120\n#extension GL_EXT_texture_array : require\n" + Sky.ATMOSPHERE + MuzzleFlash.GLSL + PipelineShaders.BEAMS + """
            uniform sampler2D uColour;
            uniform sampler2D uSurface;
            // The repeating close-up surfaces, their tiles to a repeat, and the interior's materials drawn in one alone:
            // which surface, and its colour and roughness.
            uniform sampler2DArray uDetailSurface;
            // From the interior's frame to the world, which the fine relief is turned by.
            uniform mat4 uModel;
            uniform float uRepeat[8];
            uniform float uTiledLayer[8];
            uniform vec4 uTiled[8];
            uniform sampler2DShadow uCabinShadow;
            uniform mat4 uCabinShadowMatrix;
            uniform float uCabinTexel;
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            // The colour of the vehicle's trim, linear.
            uniform vec3 uTint;
            // The light behind the instruments and how strongly it is on, and the lamp in the roof, its place from the eye and how strong it is.
            uniform vec3 uGlowColor;
            uniform float uInstruments;
            // Where the instruments are, from the eye: their light falls on the wheel and the hands near them.
            uniform vec3 uGlowAt;
            uniform vec4 uDome;
            // Whether the part drawn is the glass of a mirror, and the picture of the road behind that it shows.
            uniform float uMirrorPass;
            uniform sampler2D uBehind;
            uniform float uBehindShown;
            uniform vec3 uDoorView;
            // A door mirror's glass's height for its width, times the picture behind's width for its height.
            uniform float uDoorTall;
            uniform vec3 uBodyAhead;
            uniform vec3 uBodyAside;
            uniform vec3 uBodyAbove;
            varying vec3 vRel;
            varying vec3 vLocal;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying vec3 vOther;
            varying vec2 vUv;
            varying float vSky;
            varying vec2 vFinish;
            varying float vNear;
            varying float vKind;
            varying vec3 vLocalNormal;

            const float PI = 3.14159265;
            // Where a door mirror's view is centred up the picture behind, and so the most of its height it can show.
            const float DOOR_LEVEL = 0.36, DOOR_HIGHEST = 2.0 * DOOR_LEVEL;

            // The fine relief of a repeating surface laid on a part from three sides, by the way it faces: a tilt of
            // its normal in the interior's own frame.
            vec3 relief(float layer, vec3 local, vec3 facing) {
                vec3 w = pow(abs(facing), vec3(4.0));
                w /= max(w.x + w.y + w.z, 1e-4);
                float r = uRepeat[int(layer + 0.5)];
                vec2 sx = texture2DArray(uDetailSurface, vec3(local.yz / r, layer)).xy * 2.0 - 1.0;
                vec2 sy = texture2DArray(uDetailSurface, vec3(local.xz / r, layer)).xy * 2.0 - 1.0;
                vec2 sz = texture2DArray(uDetailSurface, vec3(local.xy / r, layer)).xy * 2.0 - 1.0;
                return vec3(0.0, sx.x, sx.y) * w.x + vec3(sy.x, 0.0, sy.y) * w.y + vec3(sz.x, sz.y, 0.0) * w.z;
            }
            // The grey the parts that take the trim's tint were baked in, linear.
            const float TRIM_GREY = 0.42;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            // Whether the sun reaches the vehicle at all, asked well outside it towards the sun:
            // nearer, the answer is the vehicle's own shadow in the world's map.
            float sunOut(vec3 at) {
                vec3 over = (uNearMatrix * vec4(at + uLight * 3.5, 1.0)).xyz * 0.5 + 0.5;
                if (inside(over)) {
                    return shadow2D(uShadowNear, over).r;
                }
                over = (uFarMatrix * vec4(at + uLight * 3.5, 1.0)).xyz * 0.5 + 0.5;
                return inside(over) ? shadow2D(uShadowFar, over).r : 1.0;
            }

            // How much of the sun the cabin's own parts let through to a place in it.
            float sunIn(vec3 local) {
                vec4 c = uCabinShadowMatrix * vec4(local, 1.0);
                vec3 s = c.xyz;
                if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0) {
                    return 1.0;
                }
                float e = 1.25 * uCabinTexel;
                float sum = 0.0;
                for (int i = 0; i < 4; i++) {
                    vec2 off = vec2(i == 0 || i == 3 ? e : -e, i < 2 ? e : -e);
                    sum += shadow2D(uCabinShadow, s + vec3(off, 0.0)).r;
                }
                return sum * 0.25;
            }

            vec3 behindAt(vec2 on) {
                if (uBehindShown < 0.5) {
                    return atmosphere(normalize(-uBodyAhead + uBodyAbove * 0.25)) * 0.3;
                }
                vec3 seen = texture2D(uBehind, on).rgb;
                if (seen.r < -0.5) {
                    seen = atmosphere(normalize(-uBodyAhead + uBodyAside * ((0.5 - on.x) * 0.6) + uBodyAbove * ((on.y - 0.5) * 0.17 + 0.02)));
                }
                return min(max(seen, vec3(0.0)), vec3(6.0)) * 0.82;
            }

            void main() {
                vec3 at = uEye + vRel;
                vec3 view = normalize(-vRel);
                vec3 sunLight = pow(uLightColor, vec3(2.2));
                float outside = sunOut(at);
                if (uMirrorPass > 0.5) {
                    // Which mirror and where on it: the one on the screen, then the doors', driver's first.
                    float which = floor(vUv.x);
                    vec2 on = vec2(fract(vUv.x), vUv.y);
                    if (which > 0.5) {
                        float side = which < 1.5 ? 1.0 : -1.0;
                        // A door mirror is set to show the road beside and behind, below the horizon the screen's shows,
                        // as much of the picture as keeps it in the glass's own proportions: a tall glass shows less across.
                        float wide = uDoorView.y;
                        float high = min(2.0 * wide * uDoorTall, DOOR_HIGHEST);
                        wide = high / (2.0 * uDoorTall);
                        on.x = 0.5 - side * uDoorView.x + (on.x - 0.5) * 2.0 * wide;
                        on.y = DOOR_LEVEL + (on.y - 0.5) * high;
                    }
                    vec3 n = normalize(vNormal);
                    float glancing = 0.04 + 0.96 * pow(clamp(1.0 - dot(n, view), 0.0, 1.0), 5.0);
                    gl_FragData[0] = vec4(behindAt(on) * (0.9 + 0.1 * glancing), 1.0);
                    gl_FragData[1] = vec4(0.0);
                    return;
                }
                vec4 colour, surface;
                vec3 n;
                if (vKind > 127.5) {
                    // A part with a place in the interior's texture, and the fine relief of its surface over it.
                    colour = texture2D(uColour, vUv);
                    surface = texture2D(uSurface, vUv);
                    vec2 bend = surface.xy * 2.0 - 1.0;
                    n = normalize(vTangent) * bend.x + normalize(vOther) * bend.y + normalize(vNormal) * sqrt(max(1.0 - dot(bend, bend), 0.0));
                    float layer = vKind - 128.0;
                    if (layer < 126.5) {
                        n += mat3(uModel) * relief(layer, vLocal, normalize(vLocalNormal)) * 0.6;
                    }
                    n = normalize(n);
                } else {
                    // A part drawn in a repeating surface alone, laid on it by its texture place.
                    int which = int(vKind + 0.5);
                    float layer = uTiledLayer[which];
                    vec4 fine = texture2DArray(uDetailSurface, vec3(vUv, layer));
                    colour = vec4(uTiled[which].rgb * (fine.a * 2.0), 1.0);
                    surface = vec4(fine.xy, uTiled[which].a * (fine.z / 0.9), 0.0);
                    vec2 bend = fine.xy * 2.0 - 1.0;
                    n = normalize(normalize(vTangent) * bend.x + normalize(vOther) * bend.y + normalize(vNormal) * sqrt(max(1.0 - dot(bend, bend), 0.0)));
                }
                vec3 albedo = mix(colour.rgb, colour.rgb * uTint / TRIM_GREY, vFinish.x);
                float near = colour.a * vNear;
                if (dot(n, view) < 0.0 && dot(normalize(vNormal), view) < 0.0) {
                    n = -n;
                }
                float nv = max(dot(n, view), 0.02);
                float rough = clamp(surface.b, 0.06, 1.0);
                float metal = vFinish.y;
                float glow = vKind > 127.5 ? surface.a : 0.0;
                vec3 dull = albedo * (1.0 - metal);
                vec3 mirror = mix(vec3(0.04), albedo, metal);
                float sky = vSky;

                // The sun, through the glass and past whatever stands between.
                // Looked up a little way out along the smoothed surface: a coarse part's flat facets stand behind the
                // surface it is shaded as, and from the facet itself every other triangle shades the next.
                float sun = sunIn(vLocal + normalize(vLocalNormal) * 0.012) * outside;
                vec3 sunHere = sunLight * (1.5 * sun);
                float nl = max(dot(n, uLight), 0.0);
                vec3 halfway = normalize(uLight + view);
                float nh = max(dot(n, halfway), 0.0);
                float a = rough * rough;
                float a2 = a * a;
                float spread = nh * nh * (a2 - 1.0) + 1.0;
                float glint = a2 / (PI * spread * spread);
                float seenBoth = 0.5 / (nl * sqrt(nv * nv * (1.0 - a2) + a2) + nv * sqrt(nl * nl * (1.0 - a2) + a2) + 0.0001);
                vec3 thrown = mirror + (1.0 - mirror) * pow(1.0 - clamp(dot(halfway, view), 0.0, 1.0), 5.0);
                vec3 color = (dull + thrown * min(glint * seenBoth * PI, 60.0)) * sunHere * nl;

                // The sky through the windows: what of it a place sees was baked into its vertices.
                vec3 above = atmosphere(vec3(0.0, 0.0, 1.0));
                vec3 level = atmosphere(normalize(vec3(n.xy + vec2(1e-4), 0.12)));
                vec3 skyLight = mix(level, above, 0.5 + 0.5 * n.z) * 2.0;
                // The sunlit road and land seen through the glass below the horizon, and what the sunlit
                // cabin throws back onto the rest of itself.
                vec3 ground = (vec3(0.30, 0.29, 0.25) * sunLight * (1.5 * max(uLight.z, 0.0) * outside) + above * 0.4) * (0.5 - 0.5 * n.z);
                // The sunlit cabin throws light back onto itself, more where the sky's own light gets in.
                vec3 bounced = sunLight * ((0.06 + 0.10 * sky) * max(uLight.z, 0.0) * outside);
                // On the darkest night what is outside still gives a cabin a little: the eye makes out its shapes.
                vec3 dark = vec3(0.0022, 0.0026, 0.0036);
                vec3 around = (skyLight + ground) * sky + bounced + dark;
                vec3 lamps = beams(at, n, 0.0) * sky + flashLight(at, n);
                if (uDome.w > 0.0) {
                    vec3 to = uDome.xyz - vRel;
                    float far2 = dot(to, to);
                    lamps += vec3(1.0, 0.86, 0.62) * (uDome.w * (0.2 + 0.8 * max(dot(n, to * inversesqrt(max(far2, 1e-4))), 0.0)) / (0.05 + far2));
                }
                if (uInstruments > 0.0) {
                    vec3 to = uGlowAt - vRel;
                    float far2 = dot(to, to);
                    lamps += uGlowColor * (uInstruments * 0.012 * (0.3 + 0.7 * max(dot(n, to * inversesqrt(max(far2, 1e-4))), 0.0)) / (0.01 + far2));
                }
                color += dull * (around + lamps) * near;

                // What the surface mirrors: mostly the cabin itself, and the sky where the glass shows it.
                vec3 back = reflect(-view, n);
                vec3 shown = back.z > 0.0 ? atmosphere(back) * 2.0 : ground * 2.0;
                shown = mix(shown, around, clamp(rough * 1.5, 0.0, 1.0));
                vec4 fit = rough * vec4(-1.0, -0.0275, -0.572, 0.022) + vec4(1.0, 0.0425, 1.04, -0.04);
                float edge = min(fit.x * fit.x, exp2(-9.28 * nv)) * fit.x + fit.y;
                vec2 share = vec2(-1.04, 1.04) * edge + fit.zw;
                color += (mirror * share.x + share.y) * shown * (min(sky * 1.6, 1.0) * near) * mix(0.5, 1.0, metal);

                // The instruments' markings and needles, lit from behind with the engine running.
                color += albedo * uGlowColor * (6.0 * glow * uInstruments);
                gl_FragData[0] = vec4(color, 1.0);
                // Nothing of the interior moves by itself, for the steadying pass beside the picture.
                gl_FragData[1] = vec4(0.0);
            }
            """;

    /**
     * The glass: what the game's own glass is drawn with (CabinGlass), on the panes of the
     * interior. Each vertex's texture place is its place on its pane, in tiles, and its sky
     * says whether the wipers sweep that pane.
     */
    private static final String GLASS_FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + PipelineShaders.BEAMS + """
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uTime;
            uniform float uRaining;
            // Half the windscreen's width, in tiles: the wipers' arcs are laid out on it.
            uniform float uScreenHalf;
            uniform float uWiperReach;
            varying vec3 vRel;
            varying vec3 vLocal;
            varying vec3 vNormal;
            varying vec3 vTangent;
            varying vec3 vOther;
            varying vec2 vUv;
            varying float vSky;
            """ + CabinGlass.NOISE + CabinGlass.PANES + CabinGlass.FLARED + """

            void main() {
                gScreenHalf = uScreenHalf;
                gWiperReach = uWiperReach;
                vec3 view = normalize(-vRel);
                vec3 n = normalize(vNormal);
                n = dot(n, view) < 0.0 ? -n : n;
                // The texture place is the place on the pane, in tiles.
                vec2 on = vUv;
                gl_FragData[0] = pane(on, n, view, vSky > 0.5 ? 1.0 : 0.0);
            }
            """;

    private static Program program, shadowProgram, glassProgram;
    /** The vertex attributes, in the places both programs have them. */
    private static final String[] ATTRIBUTES = {"aPlace", "aNormal", "aTangent", "aWay", "aUv", "aSky", "aFinish", "aNear", "aKind"};
    private int shadowTarget, shadowDepth;
    /** The interior fitted to each body it has been sat in, by the body's script. */
    private final HashMap<String, Fitted> fitted = new HashMap<>();
    private final HashMap<String, Textures> textures = new HashMap<>();
    private final java.util.HashSet<String> unfitted = new java.util.HashSet<>();

    /** An interior's pictures on the graphics card. */
    private static final class Textures {
        int colour, surface;
    }

    /** An interior fitted to one body, on the graphics card. */
    static final class Fitted {
        final CabinModel model;
        final float[] measures;
        final float[] wheel = new float[7], needles = new float[36], mirrors, lamps = new float[6];
        int array, vertexBuffer, indexBuffer;
        Textures pictures;

        Fitted(CabinModel model, float[] measures) {
            this.model = model;
            this.measures = measures.clone();
            mirrors = new float[model.mirrors.length];
        }
    }

    /**
     * The interior of the vehicle of that script fitted to its body as measured, or null where the mod has none for
     * it, which is said once. Thrown where it cannot be made.
     *
     * @param measured the body as VehicleMeshes.Parts measures it, across counted to the driver's right
     */
    Fitted fittedFor(String script, float[] measured) throws IOException {
        String name = CabinModel.nameFor(script);
        // The interiors count across to the driver's left (CabinModel).
        float[] measures = measured.clone();
        measures[4] = -measures[4];
        if (name == null || measures[4] >= 0f) {
            if (unfitted.add(script)) {
                Log.info(name == null ? "the mod has no interior of its own for " + script + ": the game's is drawn"
                        : "the body of " + script + " has its wheel on the right, which the mod's interiors are not built for: the game's is drawn");
            }
            return null;
        }
        Fitted have = fitted.get(script);
        if (have != null && java.util.Arrays.equals(have.measures, measures)) {
            return have;
        }
        if (have != null) {
            letGo(have);
        }
        ensurePrograms();
        CabinModel model = CabinModel.named(name);
        Fitted made = new Fitted(model, measures);
        ByteBuffer vertices = model.fitted(measures, made.wheel, made.needles, made.mirrors, made.lamps);
        upload(made, vertices);
        if (detailSurface == 0) {
            detailSurface = Pictures.loadLayers("/cabins/cabins-detail.surface.bc");
        }
        made.pictures = textures.get(model.pictures);
        if (made.pictures == null) {
            Textures t = new Textures();
            t.colour = Pictures.load("/cabins/" + model.pictures + ".colour.bc", false);
            t.surface = Pictures.load("/cabins/" + model.pictures + ".surface.bc", false);
            textures.put(model.pictures, t);
            made.pictures = t;
        }
        fitted.put(script, made);
        if (DevBuild.ON) {
            Log.info("the interior " + name + " is fitted to " + script + ": " + model.vertices + " vertices, " + model.indices / 3 + " triangles");
        }
        return made;
    }

    private static void ensurePrograms() throws IOException {
        if (program == null) {
            StringBuilder problem = new StringBuilder();
            program = Program.link("cabin", VERTEX, FRAGMENT, problem, ATTRIBUTES);
            if (program == null) {
                throw new IOException(problem.toString());
            }
            shadowProgram = Program.link("cabin shadow", VERTEX, SHADOW_FRAGMENT, problem, ATTRIBUTES);
            if (shadowProgram == null) {
                throw new IOException(problem.toString());
            }
            glassProgram = Program.link("cabin glass", VERTEX, GLASS_FRAGMENT, problem, ATTRIBUTES);
            if (glassProgram == null) {
                throw new IOException(problem.toString());
            }
        }
    }

    private void upload(Fitted made, ByteBuffer vertices) {
        made.array = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(made.array);
        made.vertexBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ARRAY_BUFFER, made.vertexBuffer);
        GL15.glBufferData(GL_ARRAY_BUFFER, vertices, GL_STATIC_DRAW);
        made.indexBuffer = GL15.glGenBuffers();
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, made.indexBuffer);
        ByteBuffer indices = ByteBuffer.allocateDirect(made.model.indexBytes.capacity()).order(java.nio.ByteOrder.nativeOrder());
        byte[] copy = new byte[made.model.indexBytes.capacity()];
        made.model.indexBytes.get(0, copy);
        indices.put(copy).flip();
        GL15.glBufferData(GL_ELEMENT_ARRAY_BUFFER, indices, GL_STATIC_DRAW);
        // Both programs bind the attributes to the same places: see ATTRIBUTES.
        attribute(0, 3, GL_FLOAT, false, 0);
        attribute(1, 3, GL_SHORT, true, CabinModel.NORMAL_AT);
        attribute(2, 3, GL_SHORT, true, CabinModel.TANGENT_AT);
        attribute(3, 1, GL_SHORT, false, CabinModel.WAY_AT);
        attribute(4, 2, GL_FLOAT, false, CabinModel.UV_AT);
        attribute(5, 1, GL_UNSIGNED_BYTE, true, CabinModel.SKY_AT);
        attribute(6, 1, GL_UNSIGNED_BYTE, true, CabinModel.FINISH_AT);
        attribute(7, 1, GL_UNSIGNED_BYTE, true, CabinModel.NEAR_AT);
        attribute(8, 1, GL_UNSIGNED_BYTE, true, CabinModel.KIND_AT);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0);
    }

    private static void attribute(int at, int size, int type, boolean normalized, int offset) {
        GL20.glEnableVertexAttribArray(at);
        GL20.glVertexAttribPointer(at, size, type, normalized, CabinModel.VERTEX_BYTES, offset);
    }

    private void letGo(Fitted f) {
        GL15.glDeleteBuffers(f.vertexBuffer);
        GL15.glDeleteBuffers(f.indexBuffer);
        GL30.glDeleteVertexArrays(f.array);
    }

    /**
     * Gives back everything on the graphics card but the programs: first person lets go of its
     * pictures, or the mod's own interiors are switched off. Made again as it was the first time.
     */
    void forget() {
        if (fitted.isEmpty() && textures.isEmpty() && detailSurface == 0 && shadowTarget == 0) {
            return;
        }
        int bodies = fitted.size(), styles = textures.size();
        boolean detail = detailSurface != 0, shadow = shadowTarget != 0;
        for (Fitted f : fitted.values()) {
            letGo(f);
        }
        fitted.clear();
        for (Textures t : textures.values()) {
            GL11.glDeleteTextures(t.colour);
            GL11.glDeleteTextures(t.surface);
        }
        textures.clear();
        CabinModel.picturesLetGo();
        GL11.glDeleteTextures(detailSurface);
        detailSurface = 0;
        if (shadowTarget != 0) {
            GL30.glDeleteFramebuffers(shadowTarget);
            GL11.glDeleteTextures(shadowDepth);
            shadowTarget = 0;
            shadowDepth = 0;
        }
        Log.info("the mod's interiors are given back to the graphics card: " + bodies + " fitted bodies, the pictures of " + styles + " styles"
                + (detail ? ", the shared close-up surfaces" : "") + (shadow ? ", the cabin's shadow map" : ""));
    }

    /** What one frame draws the interior with. */
    static final class Frame {
        Fitted fitted;
        /** From the interior's frame to the world less the eye, and from there to the screen. */
        final float[] model = new float[16], seen = new float[16];
        /** The sun's map: from the world less the eye to the map, and from the interior's frame to it. */
        final float[] shadowSeen = new float[16], shadowMatrix = new float[16];
        /** The sun's map read from the world less the eye, for what is lit in the cabin by another pass: the driver's arms. */
        final float[] relShadow = new float[16];
        float steer;
        /** Speed in km/h, revs as a share of full, fuel and heat as shares. */
        float speed, revs, fuel, heat;
        float[] tint = new float[3];
    }

    private final float[] identityTurn = {1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    private final float[] turn = new float[9];

    /**
     * Works out what the frame draws the interior with: where it stands, how the sun's map of it
     * is laid, how far the wheel and the needles are turned.
     *
     * @param wheelAt where the body's own wheel is in the world this frame
     * @param axes    the vehicle's way ahead, its left and its up, in the world
     */
    void frame(Frame f, ViewCamera.Pose camera, float[] wheelAt, float[] axes, float tanHalf, float aspect, float near, float far, float[] sunWay) {
        Fitted fit = f.fitted;
        float wx = fit.wheel[0], wy = fit.wheel[1], wz = fit.wheel[2];
        // The game's map is a mirror of a right-handed frame: the side its axes call aside is the vehicle's right,
        // and the interior's across is its left.
        float[] turned = {axes[0], axes[1], axes[2], -axes[3], -axes[4], -axes[5], axes[6], axes[7], axes[8]};
        axes = turned;
        // Column by column: the vehicle's ahead, left and up, and where the interior's origin lands, less the eye.
        for (int column = 0; column < 3; column++) {
            f.model[column * 4] = axes[column * 3];
            f.model[column * 4 + 1] = axes[column * 3 + 1];
            f.model[column * 4 + 2] = axes[column * 3 + 2];
            f.model[column * 4 + 3] = 0f;
        }
        float ox = wheelAt[0] - camera.eyeX - (axes[0] * wx + axes[3] * wy + axes[6] * wz);
        float oy = wheelAt[1] - camera.eyeY - (axes[1] * wx + axes[4] * wy + axes[7] * wz);
        float oz = wheelAt[2] - camera.eyeUp - (axes[2] * wx + axes[5] * wy + axes[8] * wz);
        f.model[12] = ox;
        f.model[13] = oy;
        f.model[14] = oz;
        f.model[15] = 1f;
        float[] view = {camera.rightX, camera.upX, -camera.forwardX, 0f, camera.rightY, camera.upY, -camera.forwardY, 0f, camera.rightUp, camera.upUp,
            -camera.forwardUp, 0f, 0f, 0f, 0f, 1f};
        float[] projection = ViewCamera.projection(tanHalf, aspect, near, far);
        multiply(projection, view, f.seen);
        // The sun's map: an orthographic view down the sun's way onto the cabin's middle, in the interior's own frame.
        float sx = sunWay[0] * axes[0] + sunWay[1] * axes[1] + sunWay[2] * axes[2];
        float sy = sunWay[0] * axes[3] + sunWay[1] * axes[4] + sunWay[2] * axes[5];
        float sz = sunWay[0] * axes[6] + sunWay[1] * axes[7] + sunWay[2] * axes[8];
        float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= length;
        sy /= length;
        sz /= length;
        // Up on the map is whatever of the interior's up is square to the sun, or its ahead where the sun is overhead.
        float ux = 0f, uy = 0f, uz = 1f;
        if (Math.abs(sz) > 0.95f) {
            ux = 1f;
            uz = 0f;
        }
        float d = ux * sx + uy * sy + uz * sz;
        ux -= sx * d;
        uy -= sy * d;
        uz -= sz * d;
        float ul = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        ux /= ul;
        uy /= ul;
        uz /= ul;
        float rx = uy * sz - uz * sy, ry = uz * sx - ux * sz, rz = ux * sy - uy * sx;
        float r = 1f / SHADOW_REACH;
        // From the interior's frame to the map's -1..1 cube, the sun's way pointing out of the map towards the viewer.
        float[] toMap = {rx * r, ux * r, -sx * r, 0f, ry * r, uy * r, -sy * r, 0f, rz * r, uz * r, -sz * r, 0f,
            -(rx * MIDDLE[0] + ry * MIDDLE[1] + rz * MIDDLE[2]) * r, -(ux * MIDDLE[0] + uy * MIDDLE[1] + uz * MIDDLE[2]) * r,
            (sx * MIDDLE[0] + sy * MIDDLE[1] + sz * MIDDLE[2]) * r, 1f};
        // To draw the map, it is reached through the model matrix: undo it first.
        float[] fromRel = rigidInverse(f.model);
        multiply(toMap, fromRel, f.shadowSeen);
        // To read it, from the interior's frame to 0..1, a little towards the sun so that no surface shades itself.
        float[] bias = {0.5f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0f, 0f, 0f, 0.5f, 0f, 0.5f, 0.5f, 0.5f - 0.0018f, 1f};
        multiply(bias, toMap, f.shadowMatrix);
        multiply(f.shadowMatrix, fromRel, f.relShadow);
    }

    /** The sun's map of the interior, for another pass to read: see Frame.relShadow. */
    int shadowMap() {
        return shadowDepth;
    }

    /** The sun's map of the interior: every solid part, as the sun sees it. */
    void drawShadow(Frame f) {
        if (shadowTarget == 0) {
            shadowDepth = GL11.glGenTextures();
            GL11.glBindTexture(GL_TEXTURE_2D, shadowDepth);
            GL11.glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, SHADOW_SIZE, SHADOW_SIZE, 0, GL_DEPTH_COMPONENT, GL_FLOAT, (ByteBuffer) null);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
            GL11.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);
            shadowTarget = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL_FRAMEBUFFER, shadowTarget);
            GL30.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, shadowDepth, 0);
            GL11.glDrawBuffer(GL_NONE);
            GL11.glReadBuffer(GL_NONE);
            int status = GL30.glCheckFramebufferStatus(GL_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("the driver would not make the cabin's shadow map (status 0x" + Integer.toHexString(status) + ")");
            }
        }
        GL30.glBindFramebuffer(GL_FRAMEBUFFER, shadowTarget);
        GL11.glViewport(0, 0, SHADOW_SIZE, SHADOW_SIZE);
        GL11.glDepthMask(true);
        GL11.glClearDepth(1.0);
        GL11.glClear(GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LESS);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        shadowProgram.use();
        shadowProgram.set("uModel", f.model);
        shadowProgram.set("uSeen", f.shadowSeen);
        GL30.glBindVertexArray(f.fitted.array);
        drawGroups(shadowProgram, f, false, false);
        GL30.glBindVertexArray(0);
    }

    /**
     * Draws the interior's depth alone into the target bound, its glass with it: where it is
     * nearer than anything of the body, the body is inside it and not seen.
     */
    void drawEnvelope(Frame f) {
        shadowProgram.use();
        shadowProgram.set("uModel", f.model);
        shadowProgram.set("uSeen", f.seen);
        GL11.glColorMask(false, false, false, false);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LESS);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL30.glBindVertexArray(f.fitted.array);
        // The parts pushed back 4 cm: the body's skin lies close behind the lining, the dash's top and the door
        // cards, and what of it is not at least this far behind them is cut away with what is inside, where left it
        // fought them for the nearest place and broke through in a ragged edge. The glass is not pushed: the bonnet
        // is seen through it from close behind.
        shadowProgram.set("uPush", ENVELOPE_PUSH);
        drawGroups(shadowProgram, f, false, false);
        shadowProgram.set("uPush", 0f);
        turned(shadowProgram, identityTurn, 0f, 0f, 0f);
        elements(f.fitted.model, CabinModel.GLASS);
        elements(f.fitted.model, CabinModel.MIRROR);
        GL30.glBindVertexArray(0);
        GL11.glColorMask(true, true, true, true);
    }

    private static final float ENVELOPE_PUSH = 0.04f;

    /** Begins drawing the glass: its program is in use, for the picture to give it the sky, the sun, the lamps and the rain. */
    Program beginGlass() {
        glassProgram.use();
        return glassProgram;
    }

    /** Draws the panes over the picture, behind whatever of the interior and the world stands before them, their depth left alone. */
    void drawGlass(Frame f) {
        glassProgram.set("uModel", f.model);
        glassProgram.set("uSeen", f.seen);
        glassProgram.set("uScreenHalf", f.fitted.model.screenHalf);
        glassProgram.set("uWiperReach", f.fitted.model.wiperReach);
        turnedIn(glassProgram);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(false);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        // The picture's alpha is left alone: it says what mirrors.
        GL14.glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        GL30.glBindVertexArray(f.fitted.array);
        elements(f.fitted.model, CabinModel.GLASS);
        GL30.glBindVertexArray(0);
        GL11.glDisable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_DEPTH_TEST);
    }

    private void turnedIn(Program p) {
        turned(p, identityTurn, 0f, 0f, 0f);
    }

    /** Begins the drawing in the light of the frame: the program is in use for the picture to give it the sky, the sun, the lamps. */
    Program begin() {
        program.use();
        return program;
    }

    /** Draws the solid interior into the target bound, against the depth already in it. */
    void draw(Frame f, float instruments, float[] dome) {
        Fitted fit = f.fitted;
        program.set("uModel", f.model);
        program.set("uSeen", f.seen);
        program.set("uCabinShadowMatrix", f.shadowMatrix);
        program.set("uCabinTexel", 1f / SHADOW_SIZE);
        program.set("uColour", COLOUR_UNIT);
        program.set("uSurface", SURFACE_UNIT);
        program.set("uCabinShadow", CABIN_SHADOW_UNIT);
        program.set("uDetailSurface", DETAIL_SURFACE_UNIT);
        program.setEach("uRepeat", fit.model.repeats);
        program.setEach("uTiledLayer", fit.model.tiledLayer);
        program.setEach4("uTiled", fit.model.tiledColour);
        GL13.glActiveTexture(GL_TEXTURE0 + DETAIL_SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D_ARRAY, detailSurface);
        program.set("uTint", f.tint[0], f.tint[1], f.tint[2]);
        program.set("uInstruments", instruments);
        program.set("uDome", dome[0], dome[1], dome[2], dome[3]);
        GL13.glActiveTexture(GL_TEXTURE0 + COLOUR_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, fit.pictures.colour);
        GL13.glActiveTexture(GL_TEXTURE0 + SURFACE_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, fit.pictures.surface);
        GL13.glActiveTexture(GL_TEXTURE0 + CABIN_SHADOW_UNIT);
        GL11.glBindTexture(GL_TEXTURE_2D, shadowDepth);
        GL13.glActiveTexture(GL_TEXTURE0);
        GL11.glEnable(GL_DEPTH_TEST);
        GL11.glDepthFunc(GL_LEQUAL);
        GL11.glDepthMask(true);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glDisable(GL_BLEND);
        GL30.glBindVertexArray(fit.array);
        program.set("uMirrorPass", 0f);
        drawGroups(program, f, false, false);
        float[] mirrors = f.fitted.model.mirrors;
        program.set("uDoorTall", mirrors[11 + 10] / mirrors[11 + 9] * Pipeline.MIRROR_WIDTH / Pipeline.MIRROR_HEIGHT);
        program.set("uMirrorPass", 1f);
        drawGroups(program, f, false, true);
        program.set("uMirrorPass", 0f);
        GL30.glBindVertexArray(0);
        GL11.glDisable(GL_DEPTH_TEST);
    }

    /**
     * Draws the parts: the shell, the wheel turned about its column, each needle about its pivot.
     *
     * @param glass   with the glass too, which casts no shadow and is otherwise drawn by the glass's own pass
     * @param mirrors the mirrors' glass alone
     */
    private void drawGroups(Program p, Frame f, boolean glass, boolean mirrors) {
        CabinModel m = f.fitted.model;
        if (mirrors) {
            turned(p, identityTurn, 0f, 0f, 0f);
            elements(m, CabinModel.MIRROR);
            return;
        }
        turned(p, identityTurn, 0f, 0f, 0f);
        elements(m, CabinModel.SHELL);
        float[] w = f.fitted.wheel;
        about(w[3], w[4], w[5], f.steer, turn);
        turned(p, turn, w[0], w[1], w[2]);
        elements(m, CabinModel.WHEEL);
        float[] values = {f.speed, f.revs, f.fuel, f.heat};
        for (int n = 0; n < 4; n++) {
            int g = CabinModel.NEEDLE_SPEED + n;
            if (m.count[g] == 0) {
                continue;
            }
            float[] needle = f.fitted.needles;
            int at = n * 9;
            float share = Math.clamp(values[n] / needle[at + 8], 0f, 1f);
            // The sweep is clockwise as the driver sees it; turning about an axis towards the driver, that is the negative way.
            float angle = needle[at + 6] + (needle[at + 7] - needle[at + 6]) * share;
            about(needle[at + 3], needle[at + 4], needle[at + 5], -angle, turn);
            turned(p, turn, needle[at], needle[at + 1], needle[at + 2]);
            elements(m, g);
        }
        turned(p, identityTurn, 0f, 0f, 0f);
        if (glass) {
            elements(m, CabinModel.GLASS);
            elements(m, CabinModel.MIRROR);
        }
    }

    private static void turned(Program p, float[] t, float x, float y, float z) {
        p.set3x3("uTurn", t);
        p.set("uPivot", x, y, z);
    }

    private static void elements(CabinModel m, int group) {
        if (m.count[group] > 0) {
            GL11.glDrawElements(GL_TRIANGLES, m.count[group], GL_UNSIGNED_INT, (long) m.first[group] * Integer.BYTES);
        }
    }

    /** A turn of angle radians about the axis (x, y, z) of length one, by columns. */
    static void about(float x, float y, float z, float angle, float[] out) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle), k = 1f - c;
        out[0] = c + x * x * k;
        out[1] = y * x * k + z * s;
        out[2] = z * x * k - y * s;
        out[3] = x * y * k - z * s;
        out[4] = c + y * y * k;
        out[5] = z * y * k + x * s;
        out[6] = x * z * k + y * s;
        out[7] = y * z * k - x * s;
        out[8] = c + z * z * k;
    }

    private static void multiply(float[] a, float[] b, float[] out) {
        float[] r = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[column * 4 + k];
                }
                r[column * 4 + row] = sum;
            }
        }
        System.arraycopy(r, 0, out, 0, 16);
    }

    /** The inverse of a matrix that only turns and moves, by columns. */
    private static float[] rigidInverse(float[] m) {
        float[] out = new float[16];
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                out[column * 4 + row] = m[row * 4 + column];
            }
        }
        for (int row = 0; row < 3; row++) {
            out[12 + row] = -(out[row] * m[12] + out[4 + row] * m[13] + out[8 + row] * m[14]);
        }
        out[15] = 1f;
        return out;
    }
}
