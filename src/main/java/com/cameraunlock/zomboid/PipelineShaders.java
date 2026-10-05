package com.cameraunlock.zomboid;

/**
 * The GLSL of the first-person picture's own passes, which Pipeline compiles and runs: the
 * world and its shadows, the rain, the light on figures, and what turns the lit picture into
 * what a camera would have recorded.
 *
 * A Java string constant holds 65535 bytes at most, so the two longest shaders are kept as
 * parts that Pipeline joins when it loads.
 */
final class PipelineShaders {
    private PipelineShaders() {}

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

    /**
     * Where a vertex of one of the mod's models stands: see Models.Instances, whose floats
     * these uniforms are. The picture and every shadow map share it, so a piece's shadow is
     * cast from where the piece is drawn.
     */
    private static final String MODEL_STANDS = """
            uniform float uModel;
            uniform vec4 uPlace;
            uniform vec3 uFoot;
            uniform vec4 uTurn;

            vec2 turned(vec2 way) {
                return vec2(dot(uTurn.xy, way), dot(uTurn.zw, way));
            }

            // Where over the square that lights the piece the vertex is, and how high.
            vec3 over(vec3 at) {
                return vec3(uFoot.xy + turned(at.xy) * uPlace.z, uFoot.z + at.z * uPlace.w);
            }
            """;

    static final String WORLD_VERTEX = """
            #version 120
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;
            varying float vHung;
            uniform float uCutAbove;
            varying vec3 vPlace;
            uniform vec3 uWind;
            uniform float uLaid;
            """ + MODEL_STANDS + """
            uniform vec3 uLit;
            uniform vec3 uLitAlong;
            uniform vec3 uLitDown;
            uniform vec3 uLitAcross;
            uniform vec3 uSurfaces;
            uniform vec4 uDye;
            uniform vec4 uPicture;
            uniform vec3 uEye;

            // Whether the surface's bit is set in a whole number of them. The half is there for a
            // power of two worked out a hair too large, which would otherwise divide the
            // surface's own bit to just under 1 and lose it.
            bool has(float bits, float surface) {
                return mod(floor((bits + 0.5) / exp2(surface)), 2.0) > 0.5;
            }

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
                vec4 stands = gl_Vertex;
                vec3 looks = gl_Normal;
                // Where a model's vertex is from the eye: see the end of main.
                vec3 fromEye = vec3(0.0);
                vUv = gl_MultiTexCoord0.xy;
                vColor = gl_Color;
                if (uModel > 0.5) {
                    // A model's vertex is in the model's own metres, and carries its surface and
                    // how much light gets to it past the rest of the model where a sprite's
                    // carries a texel. It is made here into what a vertex of the frame's own
                    // arrays is: stood on the map, lit by where over its square it is, and
                    // marked with its surface, which a dyed one adds 18 more to.
                    vec3 on = over(gl_Vertex.xyz);
                    stands = vec4(uPlace.xy + on.xy, on.z, 1.0);
                    fromEye = vec3((uPlace.xy - uEye.xy) + on.xy, on.z - uEye.z);
                    looks = vec3(turned(gl_Normal.xy), gl_Normal.z);
                    vec2 share = clamp(on.xy, 0.0, 1.0);
                    float surface = floor(gl_MultiTexCoord0.x + 0.5);
                    bool dyed = has(uSurfaces.y, surface), also = has(uSurfaces.z, surface);
                    vUv = dyed ? uDye.xy : also ? uDye.zw : vec2(0.0);
                    // What a frame holds shows the sprite itself, laid over the model's face: each
                    // vertex looks at its own place on it.
                    if (dyed && surface > 15.5 && surface < 16.5) {
                        vUv += gl_Vertex.x * uPicture.xy + gl_Vertex.z * uPicture.zw;
                    }
                    vColor = vec4(gl_MultiTexCoord0.y * (uLit + share.x * uLitAlong + share.y * uLitDown + share.x * share.y * uLitAcross),
                            uSurfaces.x + 32.0 * ((dyed || also ? 34.0 : 16.0) + surface));
                }
                vNormal = looks;
                vHung = uModel > 0.5 && uFoot.z > uCutAbove - 0.45 ? 1.0 : 0.0;
                // What is laid on the ground does not blow about: there the normal's length says
                // how much of the layer shows at the vertex.
                // Nor does water: there the length says how far the vertex is from the bank.
                // The colour's alpha carries what kind of thing the vertex is, and in the
                // thirty-twos above that what it is made of.
                vMaterial = floor((vColor.a + 0.5) / 32.0);
                vKind = vColor.a - 32.0 * vMaterial;
                // What stands only to throw a crown's shadow is in no picture but the sun's.
                if (vMaterial > 79.5 && vMaterial < 80.5) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                // Nor what lies under trees or clings to a bole, though a fern, a weed and a
                // flower growing out of the ground do.
                bool still = uLaid > 0.5 && vKind < 7.5 && vMaterial < 59.5 || vKind > 3.5 && vKind < 7.5;
                vec4 at = still ? stands : blown(stands, looks);
                vCover = 2.0 - length(looks);
                vWorld = at.xyz;
                // The map is thousands of tiles across and a number here holds seven digits, so
                // a place on it is known to a millimetre and no finer. Multiplied as it stands
                // by a matrix that takes the eye's own place off again, a vertex's depth came out
                // wrong by centimetres a few paces off, differently at every vertex: a picture
                // was drawn through the wall it hangs on in torn patches, and a clock's face
                // through its hands. The eye's place is taken off first, which is exact, and the
                // matrix only turns what is left. A model's vertex never is put on the map at all.
                vec3 seen = uModel > 0.5 ? fromEye : at.xyz - uEye;
                // The same place counted from a corner of the map near the eye, for what is
                // drawn finer than a millimetre: made from vWorld, a board's grain is steps.
                // The corner moves sixty tiles at a time, which is a whole number of boards.
                vPlace = seen + mod(uEye, 60.0);
                gl_Position = gl_ModelViewProjectionMatrix * vec4(seen, 1.0);
            }
            """;

    /** In two parts joined when the class loads: as one constant the shader is longer than a class file can hold. */
    static final String WORLD_FRAGMENT_PARTS = "#version 120\n#extension GL_EXT_texture_array : require\n#extension GL_ARB_shader_texture_lod : require\n" + Sky.ATMOSPHERE + """
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
            uniform float uRangeFrom;
            uniform float uCutAbove;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform float uSeconds;
            uniform float uCut;
            uniform vec2 uTexel;
            uniform vec4 uTint;
            uniform float uLaid;
            uniform float uWet;
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;
            varying float vHung;
            uniform vec4 uCutBox;
            uniform vec3 uCutNear;
            uniform float uLid;
            uniform vec3 uHole;
            uniform float uHoleUp;
            uniform vec4 uHoleFrom;
            varying vec3 vPlace;

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
            // How wide a board of a boarded wall is, in tiles: about 15 cm.
            const float BOARD_WIDE = 0.15;
            // 1 while timber() is making a post or a brace and not a wall of boards.
            float arcWhole = 0.0;

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
            """ + BEAMS + MuzzleFlash.GLSL + """
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
            uniform sampler2DArray uBark;
            uniform sampler2D uPlanks;
            uniform sampler2D uCarpet;
            uniform vec3 uTimberGain;
            uniform float uLeafGain;
            uniform sampler2D uCloth;
            uniform float uClothGain;
            uniform sampler2D uLitter;
            uniform sampler2D uRock;
            uniform sampler2D uFrond;
            uniform sampler2D uIvy;
            uniform sampler2D uSprigs;
            uniform vec2 uNatureGain;

            // The light and dark of a material's picture on a rounded thing. A sprite's face is
            // flat and takes the picture along itself. Round a cushion there is no one such way,
            // so the picture is laid on from each of the three axes and the three are mixed by
            // how squarely the surface faces each: the grain holds still as the surface turns.
            float wrapped(sampler2D picture, vec3 at, vec3 facing, float size) {
                vec3 share = facing * facing;
                share *= share;
                share /= share.x + share.y + share.z;
                vec3 tone = vec3(0.299, 0.587, 0.114);
                return dot(texture2D(picture, at.yz * size).rgb, tone) * share.x + dot(texture2D(picture, at.xz * size).rgb, tone) * share.y
                        + dot(texture2D(picture, at.xy * size).rgb, tone) * share.z;
            }

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

            // The light and dark of a picture of boards or of courses of shingle at a place on a
            // wall, a floor or a roof. Laid on as it is, the picture comes round again every pace
            // or two, and the same knot is seen all the way down a fence. So it is cut into
            // strips along its grain, at cut, which is a joint between two of its boards, and
            // each strip is slid along the grain its own amount, half of them turned end for
            // end, and is a shade lighter or darker than the next. upright is for a picture
            // whose grain runs up it. A strip is the same again every strips of them: the place
            // is counted from a corner that moves a whole number of pictures at a time.
            float laid(sampler2D picture, vec2 at, float cut, bool upright, float strips) {
                vec2 grain = upright ? at.yx : at;
                float strip = mod(floor(grain.y - cut), strips);
                vec2 own = fract(sin(vec2(strip * 12.9898 + 4.1, strip * 78.233 + 1.7)) * 43758.5453);
                grain.x = (own.y > 0.5 ? -grain.x : grain.x) + own.x;
                // How fast the place changes is asked of the place as it was: slid, it jumps from one strip to the next.
                vec3 seen = texture2DGradARB(picture, upright ? grain.yx : grain, dFdx(at), dFdy(at)).rgb;
                return dot(seen, vec3(0.299, 0.587, 0.114)) * (0.93 + 0.14 * fract(own.x * 5.0));
            }

            // Whichever of two pictures of bark is the lighter at a place, where share is a half,
            // and all one or all the other where it is 0 or 1: bark gives way to other bark
            // along the edges of its ridges, and one faded into another is neither.
            vec3 ridged(vec3 one, vec3 other, float share) {
                float lighter = dot(other - one, vec3(0.299, 0.587, 0.114));
                return mix(one, other, smoothstep(-0.12, 0.12, (share - 0.5) * 1.24 + lighter * (1.0 - abs(2.0 * share - 1.0))));
            }

            // The bark which at a place on its picture. The picture comes round again every hand
            // or two up the bole, so in patches a second piece of it, larger and from elsewhere,
            // takes its place, and no stretch of the bole is the stretch below it again. With
            // soft above 0 it is the picture as it is so many halvings smaller: its ridges
            // without the grain on them.
            vec3 barkAt(vec2 on, float which, float soft) {
                return ridged(texture2DArray(uBark, vec3(on, which), soft).rgb, texture2DArray(uBark, vec3(on * 0.71 + vec2(0.37, 0.19), which), soft).rgb,
                        smoothstep(0.3, 0.7, patches(on * vec2(2.3, 1.1) + which * 7.0)));
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

            // A number from 0 to 1 that is different at every pixel and different again every
            // frame, sixty-four frames round. What is let through by it at one pixel in so
            // many, the picture's steadying of one frame into the next makes an even veil of.
            float sifted() {
                float frame = mod(floor(uSeconds * 60.0), 64.0);
                return fract(52.9829189 * fract(dot(gl_FragCoord.xy + 5.588238 * frame, vec2(0.06711056, 0.00583715))));
            }

            // What a pane of glass throws back along a line: the sky, and below a ragged line
            // a little over the horizon the roofs and trees across the street, dark against
            // it. Which roofs depends on where the pane is, so the line moves over the glass
            // as the eye walks past.
            vec3 surroundings(vec3 line, vec3 at) {
                vec3 above = normalize(vec3(line.xy, abs(line.z) + 0.02));
                vec3 sky = ridges(above, atmosphere(above));
                float round = atan(line.y, line.x) * 2.5 + dot(at.xy, vec2(0.23, 0.17));
                float roofs = 0.26 * patches(vec2(round * 1.9, 3.0)) * patches(vec2(round * 0.7 + 9.0, 1.0));
                vec3 land = atmosphere(normalize(vec3(line.xy + 1e-4, 0.03))) * vec3(0.2, 0.23, 0.17);
                return mix(sky, land, smoothstep(roofs + 0.02, roofs - 0.02, line.z));
            }

            // Sawn boards stood side by side, in the colour given: where along the wall the
            // place is, and how high. Each board is a hand and a half wide and its own shade
            // of the colour, a little warmer or cooler than the next. Its grain is the tree's
            // rings cut through: fine dark lines a few to the finger's width that wander up
            // the board, close and straight in one board and opening into long arches in
            // another, with the saw's fibres across them. Between two boards is a dark open
            // joint, and each board's edge is eased off towards it: lean says how far the
            // surface turns along the wall there, so the sun picks the edges out. Two nails
            // hold each board to every rail. Whatever is finer than a pixel is left as its
            // mean, since drawn it would only crawl as the eye moves.
            vec3 timber(vec3 wood, float run, float up, out float lean) {
                float board = run / BOARD_WIDE;
                float which = mod(floor(board), 400.0), part = fract(board);
                // A post or a brace is one piece of wood however wide: no joints, no nails,
                // and its rings go on across it without a break.
                if (arcWhole > 0.5) {
                    which = 7.0;
                    part = abs(fract(board * 0.5) * 2.0 - 1.0);
                }
                float wide = fwidth(board);
                float r1 = grain(vec2(which, 3.0)), r2 = grain(vec2(which, 11.0)), r3 = grain(vec2(which, 23.0));
                vec3 made = wood * (0.88 + 0.22 * r1) * mix(vec3(1.04, 1.0, 0.94), vec3(0.97, 1.0, 1.04), r2);
                float along = up * 0.5 + r3 * 37.0;
                // Heartwood and sapwood: broad soft bands of lighter and darker up the board.
                made *= 0.93 + 0.14 * patches(vec2(part * 2.6 + which * 2.3, along * 0.6));
                // A knot now and then.
                float length0 = 2.3 + 1.5 * r1;
                float span = up / length0 + r2 * 9.0;
                float knotted = step(0.72, grain(vec2(which + 31.0, floor(span))));
                vec2 from = vec2((part - 0.3 - 0.4 * grain(vec2(floor(span), which + 5.0))) * BOARD_WIDE, (fract(span) - 0.5) * length0 * 0.5);
                float away = length(from);
                float near = 1.0 - smoothstep(0.2, 0.6, wide * 6.0);
                // The rings. A board cut from the side of the log shows them as arches about
                // a line up the board: the further the cut from the heart, the wider they
                // open. No two are the same distance apart, and they bend out round a knot.
                float heart = part - 0.5 + (r2 - 0.5) * 0.8;
                float bow = patches(vec2(along * 0.7, which * 7.13)) - 0.5;
                float open = mix(0.2, 1.8, r3 * r3);
                float rings = sqrt(heart * heart + 0.004) * mix(13.0, 26.0, r1) * (1.0 + 0.3 * bow)
                        + open * (patches(vec2(along * 1.9, which * 3.7)) + 0.5 * patches(vec2(along * 5.3 + 9.0, which * 1.9 + heart * 3.0)))
                        + 1.6 * patches(vec2(heart * 7.0 + which, along * 0.3)) + knotted * near * 2.5 * exp(-away * away * 900.0);
                float ring = fract(rings);
                float strong = 0.35 + 0.9 * patches(vec2(rings * 0.37 + which, along * 0.8));
                // A ring a few pixels wide is drawn as a soft wave and not as a line: a line
                // that fine breaks into dashes down the board.
                float fine = fwidth(rings);
                float late = mix(smoothstep(0.55, 0.92, ring) * (1.0 - smoothstep(0.94, 1.0, ring)),
                        0.2 + 0.2 * cos(6.2831853 * (ring - 0.75)), smoothstep(0.1, 0.24, fine)) * strong;
                late = mix(late, 0.16, smoothstep(0.26, 0.5, fine));
                float fibre = (patches(vec2(part * 67.0 + which * 5.0, up * 3.0)) - 0.5) * (1.0 - smoothstep(0.2, 0.5, wide * 67.0));
                made *= (1.0 - 0.2 * late) * (1.0 + 0.09 * fibre);
                made *= 1.0 - 0.5 * knotted * near * (1.0 - smoothstep(0.006, 0.02, away));
                float edge = min(part, 1.0 - part);
                float seen = (1.0 - smoothstep(0.3, 0.8, wide)) * (1.0 - arcWhole);
                float joint = 1.0 - smoothstep(0.018, 0.018 + wide, edge);
                float eased = 1.0 - smoothstep(0.0, 0.09, edge);
                lean = (part < 0.5 ? -1.0 : 1.0) * eased * seen;
                made *= mix(0.95, (1.0 - 0.8 * joint) * (1.0 - 0.1 * eased), seen);
                // The nails: two to a board at every rail, a rail a little over a pace above the last.
                float rail = (fract(up / 1.15 + 0.3) - 0.5) * 1.15;
                float nail = length(vec2((abs(part - 0.5) - 0.3) * BOARD_WIDE, rail));
                float soft = fwidth(up);
                float head = (1.0 - smoothstep(0.005, 0.005 + soft, nail)) * (1.0 - smoothstep(0.004, 0.012, soft)) * (1.0 - arcWhole);
                // Under each nail the rain has drawn a little rust down the board.
                float streak = (1.0 - smoothstep(0.003, 0.006 + soft, abs(abs(part - 0.5) - 0.3) * BOARD_WIDE)) * smoothstep(0.0, 0.01, -rail)
                        * (1.0 - smoothstep(0.0, 0.11, -rail)) * (1.0 - smoothstep(0.004, 0.012, soft)) * (1.0 - arcWhole);
                made = mix(made, made * vec3(0.8, 0.66, 0.52), 0.35 * streak);
                return mix(made, vec3(0.2, 0.19, 0.18), 0.85 * head);
            }

            // A wall seen from the side its sprite was not painted for is given that side's
            // colour, and one flat colour over a whole building is a cut-out of card. Paint
            // weathers: broad patches a shade lighter and darker, and faint runs down it. All
            // of it is far coarser than a pixel at any distance a wall is seen from, so
            // none of it crawls as the eye moves.
            vec3 arcTone(vec3 tint, vec3 at) {
                float broad = patches(at.xy * 0.45 + at.z * 0.31) - 0.5;
                float run = patches(vec2((at.x + at.y) * 1.7, at.z * 0.18)) - 0.5;
                return tint * (1.0 + 0.1 * broad + 0.05 * run);
            }

            // Cloth that hangs, hangs in folds: a curtain drawn as one flat sheet is a blind.
            // How light the cloth is at a place along it, the folds a hand apart and none
            // quite like the next: light on each fold's crown and dark in the hollow between.
            // Where a fold is finer than a pixel the cloth is the mean of the two.
            float arcFolds(float run) {
                float turn = run * 34.0 + 1.6 * sin(run * 9.0) + 0.7 * sin(run * 23.0 + 2.0);
                float fold = 0.5 + 0.5 * cos(turn);
                return mix(0.74 + 0.3 * fold * fold, 0.86, smoothstep(0.5, 1.6, fwidth(turn)));
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

            """;

    static final String WORLD_FRAGMENT_MAIN_START = """
            void main() {
                vec4 texel = texture2D(uTexture, vUv);
                // A wall's sprite is drawn on the slant, each column of its dots a step below the
                // last, and sharpening across those columns rules the wall with stripes: walls
                // and doors are read plainly.
                bool slanted = vKind < 1.5 && abs(normalize(vNormal).z) < 0.5;
                if (uTexel.x > 0.0 && !slanted) {
                    // The game's sprites are drawn for a screen seen from far above, and from
                    // the ground each of their dots is a hand across. Read through a curve
                    // over the four nearest dots each way, and drawn a little away from the
                    // plain blend, a leaf or a plank has an edge and not a smear. Where a sprite
                    // is cut away the plain reading decides, so its outline is as it was.
                    vec2 size = 1.0 / uTexel;
                    vec2 spot = vUv * size - 0.5;
                    vec2 whole = floor(spot), part = spot - whole;
                    vec2 w0 = part * (-0.5 + part * (1.0 - 0.5 * part));
                    vec2 w1 = 1.0 + part * part * (-2.5 + 1.5 * part);
                    vec2 w2 = part * (0.5 + part * (2.0 - 1.5 * part));
                    vec2 w3 = part * part * (-0.5 + 0.5 * part);
                    vec2 w12 = w1 + w2;
                    vec2 t0 = (whole - 0.5) * uTexel, t3 = (whole + 2.5) * uTexel, t12 = (whole + 0.5 + w2 / w12) * uTexel;
                    vec4 a = texture2D(uTexture, vec2(t12.x, t0.y)), b = texture2D(uTexture, vec2(t0.x, t12.y)), c = texture2D(uTexture, t12);
                    vec4 d = texture2D(uTexture, vec2(t3.x, t12.y)), e = texture2D(uTexture, vec2(t12.x, t3.y));
                    // Only what is painted counts: a dot past the sprite's edge has no colour to give.
                    vec4 weights = vec4(w12.x * w0.y * a.a, w0.x * w12.y * b.a, w3.x * w12.y * d.a, w12.x * w3.y * e.a);
                    float middle = w12.x * w12.y * c.a;
                    float total = weights.x + weights.y + weights.z + weights.w + middle;
                    if (total > 0.2 && texel.a > 0.5) {
                        vec3 curved = (a.rgb * weights.x + b.rgb * weights.y + d.rgb * weights.z + e.rgb * weights.w + c.rgb * middle) / total;
                        // Only where a dot of the sprite is spread over several pixels. Further
                        // off, where a pixel takes in a dot or more, drawing the colour out
                        // would make every edge sparkle as the eye moves.
                        vec2 footX = dFdx(vUv) * size, footY = dFdy(vUv) * size;
                        float spread = sqrt(max(dot(footX, footX), dot(footY, footY)));
                        float near = 1.0 - smoothstep(0.35, 0.8, spread);
                        texel.rgb = mix(texel.rgb, clamp(curved + (curved - texel.rgb) * 0.7, 0.0, 1.0), near);
                    }
                }
                // The game's sprites have no smaller copies of themselves for when they are far
                // off, and a pixel of the picture then lands on one texel of several, a
                // different one each frame as the eye moves: leaves and fences sparkle. So a
                // pixel that covers more than a texel takes four samples spread over what it
                // covers, each weighed by how solid it is, and is as solid as their mean.
                vec2 stepX = dFdx(vUv), stepY = dFdy(vUv);
                bool small = uTexel.x > 0.0 && max(dot(stepX, stepX), dot(stepY, stepY)) > 1.3 * uTexel.x * uTexel.x;
                if (small) {
                    // Nine samples over what the pixel covers, each itself a blend of four
                    // dots: enough for a house several streets off, where a pixel takes in
                    // half a dozen dots of its sprite each way.
                    vec3 sum = vec3(0.0);
                    float solid = 0.0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            vec4 s = texture2D(uTexture, vUv + (float(i) * stepX + float(j) * stepY) * 0.34);
                            sum += s.rgb * s.a;
                            solid += s.a;
                        }
                    }
                    texel = vec4(sum / max(solid, 0.001), solid / 9.0);
                }
                // The skin of a wall that faces away from the side its sprite was painted for
                // keeps the sprite's outline, its windows and doors, and nothing else of it:
                // the sprite's light and dark are the other side's wallpaper or boards. It is
                // the colour of the surface its own side has. uTint.a is 1 for paint, 2 for
                // boards lapped one over the next, and 0 for everything else.
                if (uTint.a > 0.5) {
                    texel.rgb = arcTone(uTint.rgb, vWorld);
                    if (uTint.a > 1.5) {
                        // Each board stands out at its foot and is in the shadow of the one
                        // above at its head. Far off, where a board is under a pixel high,
                        // the lines would crawl as the eye moves, and the wall is their mean.
                        float board = vWorld.z / 0.144;
                        float up = fract(board);
                        float lapped = (0.62 + 0.38 * smoothstep(0.0, 0.14, 1.0 - up)) * (1.04 - 0.1 * up);
                        texel.rgb *= mix(lapped, 0.94, smoothstep(0.35, 0.9, fwidth(board)));
                    }
                }
                // Cloth standing upright in a sheet is a curtain, or the valance of a bed.
                if (vMaterial > 7.5 && vMaterial < 8.5 && vKind < 1.5 && abs(normalize(vNormal).z) < 0.3) {
                    vec3 hangs = normalize(vNormal);
                    texel.rgb *= arcFolds(dot(vWorld.xy, vec2(hangs.y, -hangs.x)));
                }
                // A wall of upright boards. The game paints one with four boards to a square
                // and a few long strokes of grain, and from a pace away each stroke is a
                // streak as long as an arm. The sprite gives the wood its colour, taken from
                // well round the place so the strokes are gone, and the boards are made here:
                // see timber. Only where the sprite is wood: a window's frame and its glass
                // stay as painted.
                // How far the surface leans along the wall at a board's edge, and how much of
                // the place is board: both are used once the surface's facing is known.
                float lean = 0.0, timbered = 0.0;
                arcWhole = vMaterial > 65.5 && vMaterial < 66.5 ? 1.0 : 0.0;
                if ((vMaterial > 51.5 && vMaterial < 52.5 || arcWhole > 0.5) && uTint.a < 0.5 && uTexel.x > 0.0) {
                    vec3 tone = vec3(0.299, 0.587, 0.114);
                    // The strokes run up the boards, so the colour is taken from far to either
                    // side of the place and a little above and below it.
                    vec3 round = texel.rgb;
                    float found = 1.0;
                    for (int i = 0; i < 12; i++) {
                        float far = i < 8 ? (i < 2 ? 3.0 : i < 4 ? 7.0 : i < 6 ? 11.0 : 16.0) : (i < 10 ? 6.0 : 14.0);
                        vec2 off = (i < 8 ? vec2(far, 0.0) : vec2(0.0, far)) * (mod(float(i), 2.0) < 0.5 ? 1.0 : -1.0) * uTexel;
                        vec4 beside = texture2D(uTexture, vUv + off);
                        float counts = step(0.5, beside.a);
                        round += beside.rgb * counts;
                        found += counts;
                    }
                    round /= found;
                    timbered = step(0.03, round.r - round.b) * step(round.b, round.g + 0.02)
                            * (1.0 - smoothstep(0.2, 0.34, abs(dot(texel.rgb - round, tone))));
                    vec3 facing = normalize(vNormal);
                    texel.rgb = mix(texel.rgb, timber(round, dot(vPlace.xy, vec2(facing.y, -facing.x)), vPlace.z, lean), timbered);
                    lean *= timbered;
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
                // Where leaves and blades are thinned out, it is by a pattern fixed to the plant
                // and not to the screen: the picture is shifted a little each frame to smooth it,
                // and a pattern on the screen would leave out different leaves every frame.
                bool bushy = vMaterial > 62.5 && vMaterial < 63.5;
                bool crown = vMaterial > 71.5 && vMaterial < 79.5;
                if (crown) {
                    // A sprig of a crown: a branch tip in leaf, cut out of its card. Its texture
                    // coordinate is two things at once. The whole number is where on the
                    // plant's sprite the sprig takes its colour, so the crown is the green,
                    // gold or red the game has the tree at this time of year, and the part
                    // over it is where on the sprig the pixel is. See Flora.addCrown.
                    vec2 cell = floor(vUv);
                    vec2 on = clamp((vUv - cell - 0.25) * 2.0, 0.0, 1.0);
                    float each = grain(cell);
                    // Standing in a tree, the sprigs within reach are gone, a whole sprig at
                    // a time, and the view is of the wood beyond them.
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.45 + 0.8 * each) {
                        discard;
                    }
                    // A card seen along its own flat is a smear of leaves a pixel deep. It is left
                    // out before it gets there: the sprigs turned other ways are the crown from here.
                    vec3 lies = cross(dFdx(vPlace), dFdy(vPlace));
                    if (abs(dot(normalize(lies + vec3(1e-12)), normalize(uEye - vWorld))) < 0.22 + 0.16 * each) {
                        discard;
                    }
                    float which = floor(vMaterial - 71.5);
                    vec4 sprig = texture2D(uSprigs, (vec2(mod(which, 4.0), floor(which / 4.0)) + on) * vec2(0.25, 0.5));
                    // Far off, the smaller copies of the picture have every leaf part solid,
                    // and cut at half the crown would thin to twigs.
                    float fine = max(fwidth(on.x), fwidth(on.y));
                    if (sprig.a < mix(0.5, 0.4, smoothstep(0.004, 0.02, fine))) {
                        discard;
                    }
                    vec2 at = (cell + 0.5) / 2048.0;
                    vec2 wide = uTexel * 4.0;
                    vec4 sum = vec4(0.0);
                    for (int i = 0; i < 5; i++) {
                        vec2 off = i == 0 ? vec2(0.0) : i == 1 ? vec2(wide.x, 0.0) : i == 2 ? vec2(-wide.x, 0.0) : i == 3 ? vec2(0.0, wide.y) : vec2(0.0, -wide.y);
                        vec4 painted = texture2D(uTexture, at + off);
                        sum += vec4(painted.rgb * painted.a, painted.a);
                    }
                    // A sprig whose place on the sprite turns out to have nothing painted round it
                    // has no colour to take, and is not drawn black: it is not drawn.
                    if (sum.a < 0.5) {
                        discard;
                    }
                    vec3 season = sum.rgb / sum.a;
                    // The game paints a crown in a few flat tones, darker at each mass's rim
                    // and foot. Here the light does that, so the tone is evened out and only
                    // the hue is taken.
                    float painted = dot(season, vec3(0.299, 0.587, 0.114));
                    season *= mix(1.0, 0.4 / max(painted, 0.05), 0.6);
                    // The sprig's own light and dark, its veins and its twig, over that colour:
                    // every sprig's picture is the same green on average, which is divided out.
                    vec3 own = sprig.rgb / vec3(0.26, 0.42, 0.12);
                    float tone = dot(own, vec3(0.299, 0.587, 0.114));
                    vec3 leafy = season * mix(vec3(tone), own, 0.55) * (0.8 + 0.4 * each);
                    // No two sprigs quite one colour: some yellower, some bluer.
                    leafy *= mix(vec3(1.08, 1.0, 0.8), vec3(0.92, 1.0, 1.1), grain(cell + 17.0));
                    float grey = dot(leafy, vec3(0.299, 0.587, 0.114));
                    texel = vec4(clamp(mix(vec3(grey), leafy, 1.18), 0.0, 1.0), 1.0);
                    small = false;
                }
                if (vMaterial > 4.5 && vMaterial < 5.5 || bushy) {
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
                    // A bush's leaves are less than half the size of a tree's.
                    float leaves = bushy ? 1.9 : 0.85;
                    vec4 leaf = texture2D(uLeaves, along * leaves);
                    vec4 behind = texture2D(uLeaves, along * (leaves * 1.61) + 0.37);
                    if (leaf.a < 0.5) {
                        leaf = vec4(behind.rgb * 0.7, behind.a);
                    }
                    // Seen at a slant the leaves run together into one flat green sheet. Where a
                    // pixel takes in more than a sixth of the leaf picture the card is left out,
                    // thinning away first: the other cards of the fan are the tree from here.
                    // Nor is a leaf right against the eye drawn: standing in a tree, the leaves
                    // within reach thin away and the view is of the wood beyond them.
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.9 + 0.7 * grain(floor(vWorld.xy * 23.0) + floor(vWorld.z * 23.0) * 7.0)) {
                        discard;
                    }
                    vec2 spread = fwidth(along * 0.85);
                    if (max(spread.x, spread.y) > 0.1 + 0.17 * grain(floor(vWorld.xy * 23.0) + floor(vWorld.z * 23.0) * 7.0)) {
                        discard;
                    }
                    // A leaf smaller than a pixel is there in one frame and gone in the next,
                    // and a far crown cut out leaf by leaf is a crawl of dots. As the leaves
                    // shrink towards a pixel the cut goes, and the crown is the game's outline.
                    float cut = 0.5 * (1.0 - smoothstep(0.012, 0.04, max(spread.x, spread.y)));
                    if (broad.a < 0.35 || leaf.a < cut) {
                        discard;
                    }
                    float shade = dot(leaf.rgb, vec3(0.299, 0.587, 0.114)) * uLeafGain;
                    // Each leaf has its own light and dark, and the leaf picture's own greens show
                    // through the tree's colour, so a crown is leaves and not a tinted cloud.
                    vec3 season = broad.rgb / max(broad.a, 0.001);
                    vec3 own = leaf.rgb * uLeafGain * dot(season, vec3(0.333)) * 1.6;
                    texel = vec4(mix(season * mix(0.4, 1.45, clamp(shade * 0.7, 0.0, 1.0)), own * season / max(dot(season, vec3(0.333)), 0.05), 0.35), 1.0);
                    small = false;
                }
                // What a blade of grass is like where the pixel is, for the light on it further down:
                // how far up the blade, which way it is turned, and the way its card runs.
                float bladeUp = 0.0, bladeTurn = 0.5;
                vec2 bladeRun = vec2(1.0, 0.0);
                if (grass) {
                    // A card of grass: blades side by side along it, each its own height, width,
                    // lean and shade, narrowing to a point. Further off they are shorter, and at
                    // the edge of where grass stands they are gone, so no line shows where it ends.
                    // A blade right against the eye is a green wall across the picture. Within
                    // arm's reach the grass thins away to nothing.
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.45 + 0.5 * grain(floor(vWorld.xy * 23.0) + floor(vWorld.z * 23.0) * 7.0)) {
                        discard;
                    }
                    float up = 1.0 - vCover;
                    // Blades are counted along the card, whichever way it is turned.
                    vec2 run = normalize(vec2(-vNormal.y, vNormal.x));
                    // How well the grass does here, 1 on open lawn and towards 0 at the edge of a
                    // path and under trees: the tuft's normal leans up by as much. See
                    // SpriteGeometry.upShare, which has the same two numbers.
                    float vigour = clamp((normalize(vNormal).z - 0.3) / 0.6, 0.0, 1.0);
                    // Blades narrower than a pixel cannot be drawn: each would be there in one
                    // frame and gone in the next. Further off, the same card is fewer and
                    // broader blades, halving in number each time they would get too fine.
                    float fine = fwidth(dot(vWorld.xy, run)) * 78.0;
                    float fewer = exp2(-ceil(log2(max(fine / 0.4, 1.0))));
                    float along = dot(vWorld.xy, run) * 78.0 * fewer;
                    float blade = floor(along);
                    float kind = grain(vec2(blade, 7.0));
                    // Where grass does poorly there are fewer blades. Which are missing is told by
                    // the blade's own number, which is its place in the world.
                    if (grain(vec2(blade, 19.0)) > 0.3 + 0.7 * vigour) {
                        discard;
                    }
                    bool rank = vKind > 15.5;
                    // Grass grows in clumps a hand or two across, taller in the middle of one.
                    float clump = patches(vWorld.xy * 2.7 + 5.0);
                    // Lawn dies away with distance and is gone where it stops being grown. Long grass,
                    // which is grown as far as the eye sees, only thins.
                    float tall = (0.25 + 0.75 * kind * kind) * (0.72 + 0.28 * clump) * (rank ? 1.0 : 1.0 - smoothstep(8.0, 15.5, (length(uEye - vWorld) - uRangeFrom)));
                    // A blade bows over further the higher up it, to one side or the other.
                    float bow = (grain(vec2(blade, 3.0)) - 0.5) * 1.3 * up * up;
                    float reach = up / max(tall, 0.001);
                    // No blade is narrower at its root than the narrowest here, which is nearly
                    // three quarters of the widest: a finer one would be under a pixel sooner.
                    float wide = 0.36 + 0.14 * grain(vec2(blade, 17.0));
                    // The tallest have gone to seed: the top quarter of such a blade is a head,
                    // broader than the stalk under it and the colour of straw. Few on a lawn,
                    // more in grass left to grow.
                    float head = kind > (rank ? 0.88 : 0.965) ? sin(3.14159 * clamp((reach - 0.74) / 0.26, 0.0, 1.0)) : 0.0;
                    if (reach > 1.0 || abs(fract(along) - 0.5 + bow) > max(wide * (1.0 - reach * reach), 0.24 * head)) {
                        discard;
                    }
                    // Dark where the blades crowd at the root, light at the tip where the sun
                    // gets to them, and here and there a blade gone to straw, more of them where
                    // the grass does poorly.
                    float dry = smoothstep(0.82 - 0.4 * (1.0 - vigour), 1.0, grain(vec2(blade, 11.0)));
                    texel.rgb = mix(texel.rgb, vec3(0.62, 0.55, 0.3), dry * 0.6);
                    // A lawn is one green with every blade a little its own, not a quilt of the
                    // greens the game happened to paint each square. Some blades are yellower
                    // and some bluer, a clump here and there is paler than the rest, and poor
                    // grass is brown.
                    float lit = dot(texel.rgb, vec3(0.3, 0.55, 0.15));
                    float hue = grain(vec2(blade, 23.0));
                    vec3 lawn = hue < 0.33 ? vec3(0.3, 0.47, 0.17) : hue < 0.66 ? vec3(0.36, 0.5, 0.17) : vec3(0.43, 0.52, 0.19);
                    lawn = mix(lawn, vec3(0.44, 0.47, 0.2), 0.6 * smoothstep(0.5, 0.85, patches(vWorld.xy * 0.9 + 13.0)));
                    lawn = mix(lawn, vec3(0.5, 0.42, 0.24), 0.55 * (1.0 - vigour));
                    texel.rgb = mix(texel.rgb, lawn * (lit / 0.38), 0.55);
                    texel.rgb = mix(texel.rgb, vec3(0.5, 0.45, 0.3) * (lit / 0.38), 0.8 * step(0.001, head));
                    texel.rgb *= (0.5 + 0.85 * reach) * (0.8 + 0.4 * grain(vec2(blade, 13.0)));
                    texel.a = 1.0;
                    bladeUp = reach;
                    bladeTurn = grain(vec2(blade, 29.0));
                    bladeRun = run;
                }
                // What Flora builds has no sprite. Each is drawn with the plain white texture,
                // and what it is made of says what it looks like.
                bool grown = vMaterial > 55.5 && vMaterial < 62.5;
                bool litter = false;
                vec3 dent = vec3(0.0);
                if (grown) {
                    if (vMaterial < 56.5) {
                        // Fallen leaves, laid by where the ground is on the map at two sizes
                        // turned against each other, as the ground's own pictures are.
                        litter = true;
                        vec2 p = vWorld.xy;
                        float which = smoothstep(0.35, 0.65, patches(p * 0.23 + 51.0));
                        vec3 fallen = mix(texture2D(uLitter, p * 0.61).rgb,
                                texture2D(uLitter, vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 0.47 + 0.37).rgb, which) * uNatureGain.x;
                        // Damp and dark in some places, dry and pale in others, and less red
                        // than leaves fresh down.
                        float tone = dot(fallen, vec3(0.299, 0.587, 0.114));
                        texel = vec4(clamp(mix(vec3(tone), fallen, 0.7) * (0.25 + 0.13 * patches(p * 0.31 + 9.0)), 0.0, 1.0), 1.0);
                    } else if (vMaterial < 57.5) {
                        // A bole. Its texture coordinates say, in whole steps above where on
                        // the bark's picture the place is, which bark it has, how much of the
                        // picture goes round it, which way up the picture is and where on it
                        // the bole starts: see Flora.addTrunk.
                        float across = floor(vUv.x / 8.0);
                        float along = vUv.x - 8.0 * across;
                        float which = floor(vUv.y / 32.0);
                        float climbed = vUv.y - 32.0 * which;
                        float shifted = floor(across / 32.0);
                        across -= 32.0 * shifted;
                        float turned = step(15.5, across);
                        float wraps = (across - 16.0 * turned + 1.0) * 0.25;
                        vec2 own = fract(sin(vec2(which * 12.9898 + across * 78.233 + shifted * 37.719, which * 39.346 + across * 11.135 + shifted * 83.155)) * 43758.5453);
                        float way = 1.0 - 2.0 * turned;
                        vec2 on = (vec2(along, climbed) + own) * way;
                        // Where the picture's end comes round to its start it is not the start
                        // that follows: over a hand's width before the join the bark that would
                        // have followed is laid in, ridge by ridge.
                        vec2 next = on - vec2(wraps * way, 0.0);
                        float join = smoothstep(wraps - min(0.11, 0.4 * wraps), wraps, along);
                        vec3 bark = ridged(barkAt(on, which, 0.0), barkAt(next, which, 0.0), join);
                        float here = dot(bark, vec3(0.299, 0.587, 0.114));
                        // Its light and dark are the bark's ridges and furrows: the surface is
                        // tilted by how fast the bark gets lighter over it, which is read off
                        // the pixels beside this one. The picture is read softened for it:
                        // sharp, its grain is taken for pits a finger deep. Only near: further
                        // off the ridges are finer than a pixel.
                        float high = dot(ridged(barkAt(on, which, 3.0), barkAt(next, which, 3.0), join), vec3(0.299, 0.587, 0.114));
                        vec3 outward = normalize(vNormal);
                        vec3 acrossScreen = dFdx(vPlace), downScreen = dFdy(vPlace);
                        vec3 one = cross(downScreen, outward), other = cross(outward, acrossScreen);
                        float area = dot(acrossScreen, one);
                        dent = (dFdx(high) * one + dFdy(high) * other) * (sign(area) / max(abs(area), 1e-9))
                                * (0.004 * (1.0 - smoothstep(10.0, 28.0, (length(uEye - vWorld) - uRangeFrom))));
                        // Moss on the roots and the foot of the bole, in patches, higher up
                        // some trees than others, and each tree a little lighter or darker
                        // and warmer or greyer than the next of its kind.
                        float mossy = (1.0 - smoothstep(0.15, 0.5 + 1.4 * own.x * own.x, climbed)) * smoothstep(0.4, 0.7, patches(vec2(along, climbed) * vec2(2.6, 1.6) + own * 9.0));
                        vec3 toned = bark * (0.7 + 0.32 * own.y) * mix(vec3(1.03, 1.0, 0.95), vec3(1.1, 1.0, 0.86), fract(own.x * 7.0));
                        texel = vec4(clamp(mix(toned, vec3(0.24, 0.3, 0.12) * (0.5 + 1.3 * here), 0.7 * mossy), 0.0, 1.0), 1.0);
                    } else if (vMaterial < 58.5) {
                        // Ivy: leaves cut out of a sleeve round the bole. It is thick at the foot
                        // and gives out towards its top a spray at a time, never at a line: the
                        // higher up, the fewer patches of it are left. vCover is 1 at the foot
                        // of the sleeve and 0 at its top.
                        float thin = smoothstep(0.3, 1.0, 1.0 - vCover);
                        vec4 ivy = texture2D(uIvy, vUv);
                        ivy.a *= step(thin, patches(vUv * vec2(4.6, 3.1) + 3.0));
                        // Leaves over leaves: where the first picture has a gap a second, laid
                        // on larger and to one side, shows through it, in the first's shade.
                        vec4 under = texture2D(uIvy, vUv * 0.77 + 0.41);
                        if (ivy.a < 0.5) {
                            ivy = vec4(under.rgb * 0.7, under.a * step(thin * 1.3 + 0.15, patches(vUv * vec2(3.3, 2.3) + 11.0)));
                        }
                        if (ivy.a < 0.5) {
                            discard;
                        }
                        texel = vec4(min(ivy.rgb * 1.5, 1.0), 1.0);
                    } else if (vMaterial < 59.5) {
                        // A boulder: rock laid on from the three axes by how squarely each face
                        // looks along them, and moss in patches that keep to no face's edge,
                        // more of it on what faces up and none underneath.
                        vec3 facing = normalize(vNormal);
                        vec3 share = facing * facing;
                        vec3 at = vWorld * 0.55;
                        vec3 stone = (texture2D(uRock, at.yz).rgb * share.x + texture2D(uRock, at.xz).rgb * share.y + texture2D(uRock, at.xy).rgb * share.z)
                                * uNatureGain.y;
                        vec2 over = vWorld.xy + vWorld.z * 0.6;
                        float moss = smoothstep(0.62, 0.8, 0.3 * facing.z + 0.7 * patches(over * 2.3 + 4.0) + 0.25 * patches(over * 9.0))
                                * smoothstep(-0.2, 0.3, facing.z);
                        vec3 green = vec3(0.26, 0.33, 0.12) * (0.6 + 0.8 * patches(vWorld.xy * 37.0));
                        texel = vec4(clamp(mix(stone * 0.5, green, moss * 0.85), 0.0, 1.0), 1.0);
                    } else if (vMaterial < 60.5) {
                        // A fern's frond, cut out of its card.
                        vec4 frond = texture2D(uFrond, vUv);
                        if (frond.a < 0.5) {
                            discard;
                        }
                        texel = vec4(frond.rgb, 1.0);
                    } else {
                        // A leaf or a flower, drawn here. The card's coordinate across says
                        // which, in the twos above the 0 to 1 it runs over: see Flora.
                        float pick = floor(vUv.x / 2.0);
                        vec2 q = clamp(vec2(vUv.x - 2.0 * pick, vUv.y), 0.0, 1.0);
                        if (vMaterial < 61.5) {
                            float off = abs(q.x - 0.5) * 2.0;
                            if (pick > 3.5) {
                                // A twig: a brown stick along the middle of its card.
                                if (off > 0.4) {
                                    discard;
                                }
                                texel = vec4(vec3(0.2, 0.15, 0.11) * (0.8 + 0.4 * patches(vec2(q.y * 9.0, 0.5))), 1.0);
                            } else {
                                // A leaf is pointed at both ends and broadest below its middle,
                                // with a rib down it and veins off the rib.
                                float broad = pow(max(sin(3.14159 * pow(q.y, 0.75)), 0.0), 0.8);
                                if (off > broad * 0.95) {
                                    discard;
                                }
                                vec3 own = pick < 0.5 ? vec3(0.25, 0.42, 0.14) : pick < 1.5 ? vec3(0.58, 0.36, 0.14) : pick < 2.5 ? vec3(0.4, 0.26, 0.14)
                                        : vec3(0.64, 0.52, 0.2);
                                texel = vec4(own * (0.75 + 0.25 * smoothstep(0.0, 0.07, off)) * (0.9 + 0.1 * sin((q.y - off * 0.3) * 38.0)), 1.0);
                            }
                        } else {
                            // A flower: petals round a heart. They are small, and from far off
                            // would be specks that come and go, so with distance fewer are
                            // drawn. Which are left out is told by where each stands.
                            if ((length(uEye - vWorld) - uRangeFrom) > 9.0 + 5.0 * grain(floor(vWorld.xy * 3.0))) {
                                discard;
                            }
                            vec2 c = (q - 0.5) * 2.0;
                            float r = length(c);
                            bool yellow = pick > 0.5 && pick < 1.5;
                            if (r > 0.55 + 0.45 * abs(cos(atan(c.y, c.x) * (yellow ? 5.0 : 3.0)))) {
                                discard;
                            }
                            vec3 own = pick < 0.5 ? vec3(0.95, 0.95, 0.9) : yellow ? vec3(0.95, 0.8, 0.12) : vec3(0.55, 0.38, 0.8);
                            vec3 heart = yellow ? vec3(0.9, 0.6, 0.05) : vec3(0.95, 0.78, 0.15);
                            texel = vec4(mix(heart, own * (0.85 + 0.15 * r), smoothstep(0.22, 0.3, r)), 1.0);
                        }
                    }
                    small = false;
                }
                // The sprites were drawn for a view in which every level line runs two pixels
                // across for one down, so the edge of a door or a wall is a staircase of
                // two-pixel steps. Seen square on and close, the steps show as a saw edge. Taken
                // as the mean of two samples one texel apart across, each step is averaged with
                // the next and the edge comes out straight.
                vec2 half = vec2(0.5 * uTexel.x, 0.0);
                float cover = uTexel.x > 0.0 && !small && !crown ? 0.5 * (texture2D(uTexture, vUv - half).a + texture2D(uTexture, vUv + half).a) : texel.a;
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
                if (vMaterial > 14.5 && vMaterial < 15.5) {
                    // Far off, where a pixel takes in several wires, the fence is a veil: each
                    // pixel is wire as often as that much of it is wire, a different set of
                    // pixels every frame, and the frames together are an even grey gauze. Cut
                    // at one level, the wires that happened to fall on a pixel's middle were a
                    // coarse lattice of dots that crawled.
                    float spread = uTexel.x > 0.0 ? sqrt(max(dot(stepX, stepX), dot(stepY, stepY))) / uTexel.x : 0.0;
                    if (cover < mix(0.55, max(sifted(), 0.04), smoothstep(0.7, 1.5, spread))) {
                        discard;
                    }
                    cover = 1.0;
                    texel.rgb = mix(texel.rgb, vec3(0.52, 0.54, 0.55), 0.75);
                }
                // Glass. The game paints a pane as a pale blue film half seen through, and cut
                // out at one level a pane was either nothing at all or a flat blue card. A
                // pane lets most of the light behind it through and throws back a little of
                // what is in front of it, more the more it is seen on the slant: so of the
                // pixels on a pane some are the glass, showing the sky and the roofs across
                // the street, and the rest are let through to what is behind, a different set
                // every frame. Out of doors by day what is behind is a dim room, and the
                // glass takes more of the pixels, which is how a window looks dark from the
                // street. A window's sheet faces into its room.
                // A window the picture has built has a sheet of glass of its own in each light: all
                // of it is pane, with no sprite to tell the glass from.
                bool sheet = vMaterial > 66.5 && vMaterial < 68.5;
                bool lamplit = vMaterial > 64.5 && vMaterial < 65.5 || vMaterial > 67.5 && vMaterial < 68.5;
                bool window = lamplit || sheet || vMaterial > 12.5 && vMaterial < 13.5;
                float glazed = 0.0, thrown = 0.0, veil = 1.0, glow = 0.0;
                bool film = uLaid < 0.5 && vKind < 1.5 && uTexel.x > 0.0 && cover > 0.22 && cover < 0.8 && texel.b > texel.r + 0.05 && texel.g > texel.r
                        && abs(vNormal.z) < 0.5 * length(vNormal);
                if (film && small) {
                    // Far off, where a pixel is a mean of many dots, only a window is taken for glass.
                    film = window;
                } else if (film) {
                    // A pane is film all over. The soft edge of anything pale and blue is as
                    // thin a film for a dot's width, with something solid or nothing beside it.
                    for (int i = 0; i < 4; i++) {
                        vec2 off = (i == 0 ? vec2(1.5, 0.0) : i == 1 ? vec2(-1.5, 0.0) : i == 2 ? vec2(0.0, 1.5) : vec2(0.0, -1.5)) * uTexel;
                        float beside = texture2D(uTexture, vUv + off).a;
                        film = film && beside > 0.2 && beside < 0.85;
                    }
                }
                film = film || sheet;
                float soiled = 0.0;
                if (film) {
                    vec3 face = normalize(vNormal), sight = normalize(uEye - vWorld);
                    float outside = window ? step(dot(face, sight), 0.0) : 0.5;
                    thrown = min(1.0, 1.6 * (0.13 + 0.87 * pow(1.0 - abs(dot(face, sight)), 5.0)));
                    float through = mix(mix(0.9, 0.5, outside), 0.92, uDark);
                    glow = lamplit ? outside * uDark : 0.0;
                    veil = max(thrown + (1.0 - thrown) * (1.0 - through), 0.34 * glow);
                    if (sheet) {
                        // No pane is clean. Dust lies on it evenly, rain has dried on the street side
                        // in runs down it, and what a cloth never reaches gathers in the corners and
                        // along the foot of each light. vUv is where on its own pane the place is.
                        vec2 on = abs(vUv - 0.5) * 2.0;
                        float corners = smoothstep(0.55, 1.0, max(on.x, on.y)) * smoothstep(0.2, 1.0, min(on.x, on.y) + 0.35 * (1.0 - vUv.y));
                        float along = dot(vPlace.xy, vec2(face.y, -face.x));
                        float runs = patches(vec2(along * 90.0, vPlace.z * 3.0)) * patches(vec2(along * 23.0 + 4.0, vPlace.z * 0.9));
                        float bloom = 0.6 * patches(vec2(along * 19.0, vPlace.z * 19.0) + 31.0) + 0.4 * patches(vec2(along * 53.0, vPlace.z * 47.0) + 7.0);
                        soiled = 0.012 + 0.025 * bloom + 0.09 * corners * (0.4 + 0.6 * bloom) + outside * 0.04 * runs;
                        veil = veil + (1.0 - veil) * soiled;
                    }
                    // A built pane is mixed in over what is behind it by how much of a veil it is,
                    // and needs no pixels left out.
                    if (!sheet && sifted() >= veil) {
                        discard;
                    }
                    glazed = 1.0;
                    cover = 1.0;
                    // Of the pixels that are the pane, the share that is the dust on it is lit as dust is.
                    texel = vec4(mix(vec3(0.02, 0.025, 0.03), vec3(0.62, 0.6, 0.55), soiled / veil), 1.0);
                }
                // The mod's own furniture is a model and has no sprite. Its surface says what it
                // is made of, and that is its colour: oak, walnut, cloth, linen, a pillow's
                // cotton, bare metal, paint, the enamel of a fridge, steel, plastic, the glass
                // of a screen, china, the spines of books, a lamp's shade, rubber, a worktop,
                // what a frame holds, vinyl.
                bool model = vMaterial > 15.5 && vMaterial < 51.5;
                float surface = -1.0;
                // How far a model's wood is painted over, and whether what its frame holds is a picture.
                float painted = 0.0;
                bool pictured = false;
                if (model) {
                    bool dyed = vMaterial > 33.5;
                    surface = vMaterial - (dyed ? 34.0 : 16.0);
                    vec3 own = vec3(0.66, 0.5, 0.32);
                    if (surface > 0.5) { own = vec3(0.33, 0.22, 0.15); }
                    if (surface > 1.5) { own = vec3(0.46, 0.48, 0.52); }
                    if (surface > 2.5) { own = vec3(0.88, 0.86, 0.8); }
                    if (surface > 3.5) { own = vec3(0.95, 0.95, 0.93); }
                    if (surface > 4.5) { own = vec3(0.62, 0.6, 0.56); }
                    if (surface > 5.5) { own = vec3(0.9, 0.89, 0.86); }
                    if (surface > 6.5) { own = vec3(0.93, 0.93, 0.9); }
                    if (surface > 7.5) { own = vec3(0.66, 0.67, 0.68); }
                    if (surface > 8.5) { own = vec3(0.3, 0.3, 0.31); }
                    if (surface > 9.5) { own = vec3(0.035, 0.04, 0.045); }
                    if (surface > 10.5) { own = vec3(0.94, 0.94, 0.92); }
                    if (surface > 12.5) { own = vec3(0.93, 0.88, 0.76); }
                    if (surface > 13.5) { own = vec3(0.07, 0.07, 0.075); }
                    if (surface > 14.5) { own = vec3(0.8, 0.76, 0.68); }
                    if (surface > 15.5) { own = vec3(0.74, 0.79, 0.82); }
                    if (surface > 16.5) { own = vec3(0.42, 0.13, 0.11); }
                    if (surface > 11.5 && surface < 12.5) {
                        // A shelf of books is a row of spines, each its own colour. Which book a
                        // place is on is told by how far along the shelf it is.
                        float along = abs(vNormal.x) > abs(vNormal.y) ? vWorld.y : vWorld.x;
                        vec2 book = vec2(floor(along * 30.0), floor(vWorld.z * 2.6));
                        float which = grain(book);
                        own = which < 0.2 ? vec3(0.45, 0.12, 0.1) : which < 0.4 ? vec3(0.12, 0.2, 0.36) : which < 0.6 ? vec3(0.16, 0.3, 0.18)
                                : which < 0.8 ? vec3(0.62, 0.5, 0.3) : vec3(0.75, 0.72, 0.66);
                        own *= 0.75 + 0.5 * grain(book + 7.0);
                    }
                    if (dyed && surface > 15.5 && surface < 16.5) {
                        // A picture in its frame is the sprite's own painting of it. Where the
                        // sprite has nothing painted, at its ragged edge, the pane is the board
                        // behind a picture.
                        pictured = true;
                        own = texel.a > 0.5 ? texel.rgb : vec3(0.2, 0.17, 0.14);
                    } else if (dyed) {
                        // Where the game's sprite of the piece is to say what colour it is, a green
                        // sofa or a blue fridge, the vertex looks at one place on that sprite and
                        // the colour is taken from nine texels round it: the mean of those that
                        // are no darker than the nine are together. The game shades its sprites
                        // and draws a dark line round every part, and one texel may be the line,
                        // a handle or the dark side of a panel; the paler half of nine is the
                        // stuff the piece is made of, in the light. The piece is solid whatever
                        // the sprite is there.
                        vec3 tap[9];
                        float tone[9];
                        float sum = 0.0, painted = 0.0;
                        for (int i = 0; i < 9; i++) {
                            int row = i / 3;
                            vec4 near = texture2D(uTexture, vUv + vec2(float(i - 3 * row) - 1.0, float(row) - 1.0) * 6.0 * uTexel);
                            tap[i] = near.rgb;
                            tone[i] = near.a > 0.5 ? dot(near.rgb, vec3(0.299, 0.587, 0.114)) : -1.0;
                            sum += max(tone[i], 0.0);
                            painted += step(0.0, tone[i]);
                        }
                        if (painted > 0.5) {
                            float mean = sum / painted - 0.002;
                            vec4 pale = vec4(0.0);
                            for (int i = 0; i < 9; i++) {
                                pale += vec4(tap[i], 1.0) * step(mean, tone[i]);
                            }
                            own = pale.rgb / max(pale.a, 1.0);
                        }
                        if (surface < 0.5) {
                            // Wood whose sprite is pale and has next to no colour is painted, not
                            // bare: a white wardrobe. The sprite shades its white to a grey, and
                            // taken as it is the piece is a grey one; the paint is as white as
                            // paint is, keeping the cast the sprite gives it.
                            float most = max(own.r, max(own.g, own.b)), least = min(own.r, min(own.g, own.b));
                            painted = smoothstep(0.5, 0.65, most) * (1.0 - smoothstep(0.12, 0.25, (most - least) / max(most, 0.001)));
                            own = mix(own, own * (0.92 / max(most, 0.001)), painted);
                        }
                    }
                    texel = vec4(own, 1.0);
                } else if (cover < (upright ? min(uCut, 0.1) : uCut)) {
                    discard;
                }
                // Seen from overhead with the viewer indoors, the walls of that floor are cut
                // down to a low course, or the room is hidden behind its own near walls.
                // Whatever stands between the overhead eye and the viewer is opened in a round
                // window about them, or a building across the street hides them altogether.
                if (uHole.z > 0.0 && vWorld.z > uHoleUp && dot(vWorld, uHoleFrom.xyz) < uHoleFrom.w
                        && distance(gl_FragCoord.xy, uHole.xy) < uHole.z * (0.9 + 0.1 * grain(floor(gl_FragCoord.xy / 3.0)))) {
                    discard;
                }
                // Only in the building the viewer is in, and only what is of the walls or hangs on
                // them: a wardrobe or a fridge stands whole, as do the things drawn as cards.
                // And only what stands between the viewer and the eye: the walls behind them
                // hide nothing, and keep their windows and pictures.
                bool boxed = vWorld.x > uCutBox.x && vWorld.y > uCutBox.y && vWorld.x < uCutBox.z && vWorld.y < uCutBox.w;
                // The storeys over the viewer's, and the roof, while the camera is above them:
                // on its way down to the viewer it has the room to look into.
                if (boxed && vWorld.z > uLid) {
                    discard;
                }
                if (vWorld.z > uCutAbove && dot(vWorld.xy, uCutNear.xy) > uCutNear.z && boxed) {
                    bool ofModel = vMaterial > 15.5 && vMaterial < 51.5;
                    bool standing = vKind > 1.5 && vKind < 3.5;
                    if (ofModel ? vHung > 0.5 : !standing) {
                        discard;
                    }
                }
            """;

    /** The rest of main: the whole of it in one constant is longer than a class file can hold. */
    static final String WORLD_FRAGMENT_MAIN_REST = """
                vec3 toEye = uEye - vWorld;
                float away = length(toEye);
                vec3 view = toEye / away;
                float range = max(away - uRangeFrom, 0.0);
                // A sprite is one sheet with no back: the side being looked at is the lit one.
                vec3 n = normalize(vNormal);
                // A sprig of a crown faces the way the crown's outside does there, whichever side of its card is seen.
                if (dot(n, view) < 0.0 && !crown) {
                    n = -n;
                }
                // A tuft of grass is lit as the ground it grows from is, from above. Lit as the
                // card it is drawn on, each tuft is a flat pane brighter or darker than the
                // next, and a lawn is a heap of green blocks.
                if (grass) {
                    n = normalize(vec3(n.xy * 0.2, 1.0));
                }
                n = normalize(n - dent);
                if (lean != 0.0) {
                    // A board's eased edge turns the surface along the wall, towards the joint.
                    n = normalize(n + vec3(vNormal.y, -vNormal.x, 0.0) / max(length(vNormal.xy), 0.001) * (0.55 * lean));
                }

                // Open ground. The game has a handful of squares of each ground and lays them
                // side by side, so a road or a yard is the same square over and over. Real
                // ground is not: it is paler here and darker there over many paces, and close
                // to it is made of stones and grit that the sun catches. Both are worked out
                // from where the ground is on the map, so they never repeat and they stay put.
                float grit = 0.0;
                if (!model && !litter && vKind < 1.5 && vKind > 0.5 && n.z > 0.8) {
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
                    // Gravel only where the game says the ground is gravel. Told from tarmac by
                    // how light the ground is, every street in the sun was a bed of pebbles.
                    float loose = vMaterial > 53.5 && vMaterial < 54.5 ? stony : 0.0;
                    // The pale floor of pavements, squares and car parks is laid slabs: it has
                    // neither the stones of a drive nor the grain and the colour of tarmac.
                    float paved = vMaterial > 54.5 && vMaterial < 55.5 ? stony : 0.0;
                    float dark = stony - loose - paved;
                    // A picture is about a pace and a half of ground: its stones come out the size of gravel.
                    vec2 one = p * 0.83;
                    vec2 two = vec2(p.x * 0.8 - p.y * 0.6, p.x * 0.6 + p.y * 0.8) * 0.37 + 0.37;
                    float which = smoothstep(0.35, 0.65, patches(p * 0.23 + 51.0));
                    vec3 grain3 = dark * uMaterialGain.x * mix(texture2D(uAsphalt, one).rgb, texture2D(uAsphalt, two).rgb, which)
                            + loose * uMaterialGain.y * mix(texture2D(uGravel, one).rgb, texture2D(uGravel, two).rgb, which)
                            + paved * vec3(0.95 + 0.1 * patches(p * 7.3 + 13.0))
                            // Earth only where the game says the ground is earth: what is brown may as well be planks.
                            + (1.0 - stony - green) * (vMaterial > 3.5 && vMaterial < 51.5 ? uMaterialGain.z * mix(texture2D(uSoil, one).rgb, texture2D(uSoil, two).rgb, which) : vec3(1.0))
                            + green * uMaterialGain.w * mix(texture2D(uLawn, one).rgb, texture2D(uLawn, two).rgb, which);
                    // The picture's light and dark go over the game's colour everywhere. Where
                    // the ground is stone, which has no colour of its own to lose, the picture's
                    // colour comes too, as bright as the game painted the spot: tarmac and gravel
                    // are then the real thing and not a painting of it. Grass keeps the game's
                    // colour, which is the season's.
                    float fine = dot(grain3, vec3(0.299, 0.587, 0.114));
                    float bright = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                    texel.rgb = mix(texel.rgb * mix(1.0, fine, 0.9), grain3 * bright, 0.8 * (stony - paved));
                    if (paved > 0.0) {
                        // Two slabs to a tile, each row laid half a slab along from the last,
                        // no two quite the same grey, with a dark joint between them. Joints
                        // finer than a pixel are left out: they would only crawl.
                        vec2 slab = p * 2.0;
                        slab.x += 0.5 * mod(floor(slab.y), 2.0);
                        vec2 toJoint = min(fract(slab), 1.0 - fract(slab)) * 0.5;
                        vec2 soft = fwidth(p);
                        float seen = paved * (1.0 - smoothstep(0.06, 0.2, max(soft.x, soft.y)));
                        float joint = max(1.0 - smoothstep(0.008, 0.008 + soft.x, toJoint.x), 1.0 - smoothstep(0.008, 0.008 + soft.y, toJoint.y));
                        float tone = grain(floor(slab));
                        texel.rgb *= (1.0 + (tone - 0.5) * 0.08 * seen) * (1.0 - 0.3 * joint * seen);
                    }
                    float near = 1.0 - smoothstep(5.0, 16.0, range);
                    if (near > 0.0) {
                        float e = 0.012;
                        float here = stones(p), east = stones(p + vec2(e, 0.0)), south = stones(p + vec2(0.0, e));
                        // Tarmac and concrete are close to flat: only loose stone stands up in lumps.
                        float depth = (0.05 - 0.035 * stony + 0.135 * loose) * near;
                        n = normalize(n - vec3(east - here, south - here, 0.0) * (depth / e));
                        texel.rgb *= 1.0 + (here - 0.5) * (0.25 - 0.18 * stony + 0.48 * loose) * near;
                        grit = (stony - paved) * near * here;
                    }
                }

                // What a building is made of. The game's sprite says what colour a roof or a
                // wall is and where its boards and windows are; a picture of the stuff itself,
                // laid over it by where the surface is in the world, gives it the grain of
                // shingle, of old paint, of floorboards. Its light and dark only.
                // A sheet with no sprite that is marked as shingle is the mod's own roofing: it
                // is the grey of shingle, in sun as in shade.
                // The cap along a ridge or a hip is the roof's own covering.
                bool capping = vMaterial > 52.5 && vMaterial < 53.5;
                bool roofing = capping || vMaterial > 0.5 && vMaterial < 1.5;
                if (roofing && min(texel.r, min(texel.g, texel.b)) > 0.99) {
                    texel.rgb = vec3(0.3, 0.3, 0.32);
                }
                // So are the mod's own kerbs, of concrete, and the walls of its quays, of dark
                // wet timber.
                if (vMaterial > 8.5 && vMaterial < 10.5) {
                    texel.rgb = vMaterial < 9.5 ? vec3(0.86, 0.86, 0.83) : vec3(0.2, 0.17, 0.14);
                }
                // A kerb is cast in lengths: pale concrete, a little uneven in its grey, with
                // a joint where one length ends and the next begins.
                if (vMaterial > 63.5 && vMaterial < 64.5) {
                    float along = dot(vWorld.xy, vec2(n.y, -n.x));
                    float poured = patches(vec2(along * 2.3, vWorld.z * 11.0)) * 0.6 + patches(vec2(along * 19.0, vWorld.z * 37.0) + 3.0) * 0.4;
                    float toJoint = abs(fract(along / 1.5) - 0.5) * 1.5;
                    float joint = (1.0 - smoothstep(0.012, 0.012 + fwidth(along), 0.75 - toJoint)) * (1.0 - smoothstep(0.06, 0.2, fwidth(along)));
                    texel.rgb = vec3(0.8, 0.79, 0.75) * (0.9 + 0.2 * poured) * (1.0 - 0.35 * joint);
                }
                if (roofing || vMaterial > 1.5 && vMaterial < 3.5 || vMaterial > 5.5 && vMaterial < 8.5) {
                    // The place is taken within sixty tiles, not on the whole map: ten thousand
                    // tiles out a number holds a thousandth of a tile and no finer, and a grain
                    // read at such places is ruled with stripes a pixel or two wide. Every
                    // picture here repeats a whole number of times in sixty tiles.
                    vec2 across = abs(n.z) > 0.7 ? vPlace.xy : vec2(dot(vPlace.xy, vec2(n.y, -n.x)), vPlace.z);
                    float fine = roofing ? laid(uShingles, across * 0.55, 0.1, false, 33.0) * uSurfaceGain.x
                            : vMaterial < 2.5 ? dot(texture2D(uPaint, across * 0.8).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.y
                            : vMaterial < 3.5 ? laid(uWood, across * 0.45, 0.44, false, 27.0) * uSurfaceGain.z
                            : vMaterial < 6.5 ? dot(texture2DArray(uBark, vec3(across * vec2(1.6, 0.8), 0.0)).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.x
                            : vMaterial < 7.5 ? laid(uPlanks, across * 0.7, 0.648, true, 42.0) * uTimberGain.y
                            : dot(texture2D(uCarpet, across * 1.6).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.z;
                    float much = roofing ? 0.85 : vMaterial < 2.5 ? 0.3 : vMaterial < 3.5 ? 0.7 : 0.8;
                    // What is painted white is paint over the wood: its grain barely shows.
                    if (vMaterial > 2.5 && vMaterial < 3.5) {
                        much *= 1.0 - 0.75 * smoothstep(0.6, 0.85, min(texel.r, min(texel.g, texel.b)));
                    }
                    texel.rgb *= mix(1.0, fine, much);
                }
                // It has had the weather longest, and is a shade darker than the slopes it joins.
                if (capping) {
                    texel.rgb *= 0.72;
                }
                if (model && (surface < 4.5 || surface > 14.5 && surface < 15.5)) {
                    // Furniture is seen from nearer than a floor is, so its grain is finer: the
                    // wood's a hand across, the cloth's a weave. Linen and a pillow show less
                    // of it than a blanket does, and a worktop only a trace.
                    bool wooden = surface < 1.5 || surface > 14.5;
                    float fine = wooden ? wrapped(uWood, vWorld, n, 0.9) * uSurfaceGain.z : wrapped(uCloth, vWorld, n, 6.0) * uClothGain;
                    float much = surface < 1.5 ? 0.45 : surface < 2.5 ? 0.4 : surface < 3.5 ? 0.25 : surface < 4.5 ? 0.15 : 0.12;
                    // What is painted white is paint over the wood: its grain barely shows.
                    if (surface < 1.5) {
                        much *= 1.0 - 0.75 * smoothstep(0.6, 0.85, min(texel.r, min(texel.g, texel.b)));
                    }
                    // Paint fills the grain: under it the wood shows as a trace and no more.
                    much *= 1.0 - 0.8 * painted;
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
                float relief = water || grass ? 0.0 : RELIEF * (1.0 - smoothstep(14.0, 38.0, range)) * (1.0 - 0.6 * timbered);
                // A boulder's faces are flat only because it is built of few of them: the rock's
                // own cracks and ledges stand out of each far more than paint does from a wall.
                if (vMaterial > 58.5 && vMaterial < 59.5) {
                    relief *= 3.0;
                }
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
                if (card && !grass && !grown && !crown && length(vNormal.xy) > 0.1 * length(vNormal)) {
                    // A card seen edge on is a streak of smeared leaves. It thins out and goes
                    // before it gets there: the other cards of the fan are what is seen then.
                    float square = abs(dot(normalize(vNormal.xy + vec2(1e-5)), normalize(toEye.xy + vec2(1e-5))));
                    if (square < 0.2 + 0.25 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                }
                float open = grass ? vKind - (vKind > 15.5 ? 16.0 : 8.0) : water ? vKind - 4.0 : card ? vKind - 2.0 : vKind;
                float facing = clamp(dot(n, uLight), 0.0, 1.0);
                if (crown) {
                    // A crown is a round mass of leaves: bright where it is turned to the sun,
                    // going over to shade round its side, and never black, for the light
                    // comes through and round the leaves.
                    facing = 0.3 + 0.7 * pow(clamp(0.5 + 0.5 * dot(n, uLight), 0.0, 1.0), 1.5);
                } else if (card) {
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
                    if (crown) {
                        // Inside a crown the sun still arrives, through the leaves above and
                        // between them: the shade a tree gives itself is green and never deep.
                        reached = mix(0.42, 1.0, reached);
                    }
                    sun = reached * facing;
                }

                // The game's light for the square is the light that is there without the sun
                // falling straight on it. Out of doors that light comes from the sky, more of it
                // onto what faces up and bluer than the sun.
                vec3 fromSky = mix(vec3(1.0), vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), open);
                // A crown is the leaves the sky is seen through, not something under them.
                if (open > 0.5 && range < 40.0 && !crown) {
                    // Under trees the sky's light is thinned and green, not gone.
                    fromSky *= mix(max(skySeen(vWorld + n * 0.1), 0.38), 1.0, smoothstep(25.0, 40.0, range));
                }
                if (model) {
                    // The game's light for a square comes from no direction, and a sprite needs
                    // none: its shading is painted on. A model lit the same from every side is
                    // a flat shape of one colour. A room's light comes mostly from above, off
                    // the ceiling and down through the windows, and a little more from one side
                    // than the other, so a model takes most of it on what faces up.
                    fromSky *= mix(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8)), 1.0, open);
                }
                vec3 lightHere = pow(vColor.rgb, vec3(2.2)) * fromSky + pow(uLightColor, vec3(2.2)) * (2.1 * sun);
                // The ground in the sun throws its light back up at whatever stands on it. A
                // wall turned from the sun is lit by the street in front of it, and without
                // that the shaded side of every building is a flat dark slab.
                lightHere += pow(uLightColor, vec3(2.2)) * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - abs(n.z)) * open);
                vec3 color = pow(texel.rgb, vec3(2.2)) * lightHere;
                if (card) {
                    // A leaf lets the sun through. Looked at against the light, foliage and
                    // grass glow from behind, yellower than they are lit from in front, and
                    // that glow is most of what a tree looks like on a bright day.
                    float against = pow(max(dot(-view, uLight), 0.0), 1.5);
                    vec3 leaf = pow(texel.rgb, vec3(2.2)) * vec3(1.25, 1.2, 0.55);
                    // A blade lets most through near its tip, where it is thinnest.
                    float through = grass ? 0.55 + 0.9 * bladeUp : 1.0;
                    color += leaf * pow(uLightColor, vec3(2.2)) * (2.8 * through * against * reached * step(0.0, uLight.z));
                    if (grass && reached > 0.0) {
                        // A blade of grass is glossy, and throws the sun off its flat as a
                        // sheen. Each is turned its own way round its stalk and leans back more
                        // towards its tip, so in a low sun a lawn glitters blade by blade. The
                        // sheen is one for the whole of a blade, which is never under a pixel.
                        vec3 flatWay = vec3(bladeRun.y, -bladeRun.x, 0.0);
                        if (dot(flatWay, view) < 0.0) {
                            flatWay = -flatWay;
                        }
                        vec3 facet = normalize(flatWay * 0.7 + vec3(bladeRun * ((bladeTurn - 0.5) * 1.2), 0.35 + 0.5 * bladeUp));
                        color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * (0.25 + 0.75 * bladeUp) * glossed(facet, view, uLight, 0.42, 0.04));
                    }
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
                    // Bark, rock and dry leaves are as dull as anything is, however pale.
                    if (grown) {
                        rough = 0.9;
                    }
                    // Of the mod's own furniture the wood is planed and waxed and the cloth dull.
                    // Enamel and china are glazed, a screen is glass, a worktop is wiped smooth,
                    // rubber and a lamp's shade are matt. A handle and a steel sink are bare
                    // metal, which mirrors far more than anything else does.
                    float mirrors = 0.04;
                    if (model) {
                        rough = surface < 1.5 ? 0.38 : surface < 4.5 ? 0.9 : surface < 5.5 ? 0.3 : surface < 6.5 ? 0.5 : surface < 7.5 ? 0.22
                                : surface < 8.5 ? 0.28 : surface < 9.5 ? 0.45 : surface < 10.5 ? 0.08 : surface < 11.5 ? 0.15 : surface < 12.5 ? 0.6
                                : surface < 13.5 ? 0.85 : surface < 14.5 ? 0.75 : surface < 15.5 ? 0.3 : surface < 16.5 ? (pictured ? 0.6 : 0.05) : 0.36;
                        // Gloss paint is smoother than waxed wood.
                        rough = mix(rough, 0.3, painted);
                        mirrors = surface > 4.5 && surface < 5.5 || surface > 7.5 && surface < 8.5 ? 0.55 : surface > 9.5 && surface < 10.5 ? 0.06
                                : surface > 15.5 && surface < 16.5 && !pictured ? 0.9 : 0.04;
                    }
                    rough = mix(rough, 0.18, shine);
                    color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * glossed(n, view, uLight, rough, mirrors));
                }
                color += pow(texel.rgb, vec3(2.2)) * beams(vWorld, n, card ? 1.0 : 0.0);
                color += pow(texel.rgb, vec3(2.2)) * flashLight(vWorld, n);
                // A lamp that is lit is brighter than anything it lights. The game lights the
                // square a burning lamp stands on strongly, and in the dark that is how a lamp
                // that is on is told: its pale parts, the shade and the glass, then give light
                // of their own, enough to bloom.
                // The shade of the mod's own lamp is such a pale part.
                if ((vMaterial > 13.5 && vMaterial < 14.5 || surface > 12.5 && surface < 13.5) && uDark > 0.0) {
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
                    // Far off a pixel takes in many ripples, and what it shows is their mean,
                    // which is level water. Tilted by whichever ripple the pixel's middle fell
                    // on, the far shore's reflection breaks up into dashes that crawl.
                    wave *= 1.0 - smoothstep(14.0, 55.0, range);
                    vec3 surface = normalize(vec3(n.xy + wave * ripple + rings, n.z));
                    vec3 mirrored = reflect(-view, surface);
                    mirrored.z = abs(mirrored.z);
                    float glance = 0.03 + 0.97 * pow(1.0 - max(dot(view, surface), 0.0), 5.0);
                    color = mix(color, ridges(mirrored, atmosphere(mirrored)) * (0.35 + 0.65 * open), glance * shine);
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

                if (glazed > 0.5) {
                    // No pane is quite flat or quite square in its frame, so each throws back
                    // a slightly different piece of the street, and it slides over the glass
                    // as the eye moves.
                    vec2 bowed = vec2(patches(vPlace.xy * 1.3 + vPlace.z * 2.1), patches(vPlace.xy * 1.7 + vPlace.z * 1.3 + 11.0)) - 0.5;
                    vec3 pane = normalize(n + vec3(vNormal.y, -vNormal.x, 0.0) / max(length(vNormal.xy), 0.001) * (0.07 * bowed.x) + vec3(0.0, 0.0, 0.07 * bowed.y));
                    // Only the pixels that are glass carry what the whole pane throws back, so
                    // each carries the more for being one of few.
                    // Dust is grey whatever lights it: under an open sky its share of the pane came out blue.
                    color = vec3(dot(color, vec3(0.299, 0.587, 0.114))) * step(0.0001, soiled) + (surroundings(reflect(-view, pane), vPlace) * thrown
                            + pow(uLightColor, vec3(2.2)) * (2.1 * reached * glossed(pane, view, uLight, 0.07, 0.08))) / veil;
                    // A window with a lamp burning behind it is a warm bright shape in the
                    // night: the lamp's light is in the glass itself, in the dust on it and
                    // the curtain behind.
                    color += vec3(1.0, 0.72, 0.4) * (1.5 * glow);
                }
                // Towards a low sun the air itself is lit: what stands far off in that quarter
                // is veiled in gold, and the nearer for the lower the sun is.
                float sunward = pow(max(dot(-view, uLight), 0.0), 3.0) * (1.0 - smoothstep(0.1, 0.75, uLight.z)) * step(0.0, uLight.z);
                color = mix(color, atmosphere(-view), 1.0 - exp(-range * (0.0022 + 0.016 * sunward)));
                // The world ends by fading into what stands behind it, which is the hills and
                // not bare air: faded to air, its last tiles show as dashes against them.
                color = mix(color, ridges(-view, atmosphere(-view)), smoothstep(uFogStart, uFogEnd, range));
                // Ground spreading over its neighbour thins out towards its edge, and the edge
                // is ragged: tufts and patches, not a ruled fade.
                float shown = 1.0;
                if (uLaid > 0.5 && !grass && vMaterial < 56.5 && vCover < 0.999) {
                    float ragged = patches(vWorld.xy * 1.9) * 0.6 + patches(vWorld.xy * 6.1 + 17.0) * 0.4 - 0.5;
                    shown = smoothstep(0.38, 0.62, vCover + ragged * 3.0 * vCover * (1.0 - vCover));
                    // Where no fallen leaves show, nothing is drawn: left in, they would still
                    // hide whatever is laid on the same ground after them.
                    if (litter && shown < 0.03) {
                        discard;
                    }
                }
                // What is laid over the ground is mixed in by its alpha. For everything else the
                // alpha says how much of a mirror the surface is, 1 for none, which the last
                // pass reads to trace what the mirror shows.
                // A varnished floor mirrors a little too: the window across the room, the legs of
                // a chair.
                float polish = vMaterial > 2.5 && vMaterial < 3.5 ? 0.22 : vMaterial > 10.5 && vMaterial < 11.5 ? 0.3 : 0.0;
                // A window pane mirrors whichever way it faces, and so does the glass of a screen.
                // So does a mirror in its frame, and more than either.
                float pane = glazed > 0.5 ? 0.6 : surface > 9.5 && surface < 10.5 ? 0.4
                        : surface > 15.5 && surface < 16.5 && !pictured ? 0.55 : 0.0;
                gl_FragColor = vec4(color, sheet ? veil : uLaid > 0.5 ? texel.a * shown : 1.0 - max(max(shine, polish) * step(0.8, n.z), pane));
            }
            """;

    static final String WORLD_FRAGMENT_MAIN = WORLD_FRAGMENT_MAIN_START.concat(WORLD_FRAGMENT_MAIN_REST);

    static final String SHADOW_VERTEX = """
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
            varying float vLeaves;
            varying vec3 vAt;
            """ + MODEL_STANDS + """
            void main() {
                if (uModel > 0.5) {
                    // A model is solid all over and nothing of it blows about. It is drawn with
                    // the white texture, of which any place will do.
                    vec3 on = over(gl_Vertex.xyz);
                    vUv = vec2(0.0);
                    vLeaves = 0.0;
                    vAt = vec3(uPlace.xy + on.xy, on.z);
                    gl_Position = uMatrix * vec4(vAt, 1.0);
                    return;
                }
                vUv = gl_MultiTexCoord0.xy;
                float material = floor((gl_Color.a + 0.5) / 32.0);
                vLeaves = material > 4.5 && material < 5.5 || material > 62.5 && material < 63.5 || material > 79.5 && material < 80.5 ? 1.0 : 0.0;
                // A crown's sprigs take their colour from the sprite and are no part of its outline: the fan that stands with them throws the shadow.
                if (material > 71.5 && material < 79.5) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                vAt = gl_Vertex.xyz;
                gl_Position = uMatrix * blown(gl_Vertex, gl_Normal);
            }
            """;

    static final String SHADOW_FRAGMENT = """
            #version 120
            uniform sampler2D uTexture;
            varying vec2 vUv;
            varying float vLeaves;
            varying vec3 vAt;
            uniform vec3 uSunWay;
            void main() {
                if (texture2D(uTexture, vUv).a < 0.5) {
                    discard;
                }
                // A crown is not a lid. The sun comes through the gaps between its boughs in
                // patches the size of a hand to the size of a table, and the ground under a
                // tree is dappled, not dark.
                if (vLeaves > 0.5) {
                    // A gap is where the ray of light meets the ground, so that it is a gap
                    // through every bough the ray passes and not through one of them.
                    vec2 lands = vAt.xy - uSunWay.xy * (vAt.z / max(uSunWay.z, 0.15));
                    vec2 cell = floor(lands * 1.3), fine = floor(lands * 4.1);
                    float gap = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453) * 0.6
                            + fract(sin(dot(fine, vec2(39.346, 11.135))) * 43758.5453) * 0.4;
                    if (gap > 0.6) {
                        discard;
                    }
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
    static final String RAIN_VERTEX = """
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

    static final String RAIN_FRAGMENT = """
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
                if (uSeated > 0.5 && distance(vWorld.xy, uEye.xy) < 0.9 && vWorld.z < uEye.z + 0.3) {
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
    static final String SCREEN_VERTEX = """
            #version 120
            void main() {
                gl_Position = vec4(gl_Vertex.xy, 1.0, 1.0);
            }
            """;

    static final String BLOOM_DOWN = """
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

    static final String BLOOM_UP = """
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
    static final String OCCLUSION = """
            #version 120
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            uniform vec2 uFull;
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
            vec3 placeAt(vec2 at) {
                // This pass is half the picture's size, so the middle of each of its pixels is
                // the corner of four of the picture's, and which of the four a read there
                // returns goes by the last digit of the sum. It went one way for some columns
                // and the other for the next, and on ground that slopes across the picture
                // each change was a step in the ground: a grid of lines of shade. The place is
                // moved to the middle of the pixel it is in first.
                vec2 uv = (floor(at * uFull) + 0.5) / uFull;
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                return vec3((uv * 2.0 - 1.0) * uTan * ahead, ahead);
            }

            // Whether a place is in the picture. Read past its edge, the picture's depth comes
            // round again from the far edge, and whatever is there is taken for something
            // standing next to this point: with the head rolled, when the two edges are at
            // different distances, the ground near every edge was ruled with lines of shade.
            float shown(vec2 at) {
                return step(0.0, at.x) * step(at.x, 1.0) * step(0.0, at.y) * step(at.y, 1.0);
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
                    blocked += max(dot(v, n) - 0.04, 0.0) / (far2 + 0.03) * step(far2, RADIUS * RADIUS * 3.0) * shown(uv + off);
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
                    broad += max(dot(v, n) - 0.05, 0.0) / (far2 + 0.15) * step(far2, ROOM_RADIUS * ROOM_RADIUS * 2.0) * shown(uv + off);
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
                    float faces = max(dot(v, n), 0.0) / sqrt(far2 + 0.0001) * shown(at);
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
                        if (behind > 0.02 && behind < 0.35 && shown(at) > 0.5) {
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
    static final String SOFTEN = """
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
    static final String VOLUME = """
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
                // A low sun shines through far more air than a high one, and the air towards
                // it glows broadly, with a hot heart.
                float low = 1.0 - smoothstep(0.1, 0.75, uLight.z);
                float towards = 0.3 * (1.0 - 0.6 * low) + (1.2 + 1.4 * low) * pow(mu, 2.5) + (2.4 + 3.0 * low) * pow(mu, 9.0);
                // Lit air is paler than the light that lights it: it is seen against the sky's
                // own blue, and a sunset's air is gold and rose, not the red of the sun's disc.
                vec3 glow = pow(uLightColor, vec3(2.2));
                glow = mix(glow, vec3(dot(glow, vec3(0.3, 0.5, 0.2))) * vec3(1.0, 0.82, 0.62), 0.65);
                vec3 caught = glow * (lit / float(STEPS) * ahead * along * uMist * towards);
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
     *
     * The source is held in two halves and joined when the class loads: a class file holds no
     * one string of more than 65535 bytes, and the whole of it is longer.
     */
    static final String FIGURE_LIGHT_PARTS = "#version 120\n" + Sky.ATMOSPHERE + """
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
            uniform float uRangeFrom;
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
            uniform sampler2D uPanes;
            uniform float uHavePanes;
            uniform float uInstruments;
            uniform vec4 uWheel;
            uniform vec3 uWheelAxis;
            uniform vec3 uMirror;
            // The driver's place of the vehicle sat in: the wheel's size, its rim, the layout of
            // its spokes and their width; the hub's half-size and roundness; the kind of
            // instrument cluster, its dials' radius, how many, and the hood's half-size; the
            // colours of dash and seats, the instruments' light, and the gear lever.
            uniform vec4 uKit;
            uniform vec3 uHub;
            uniform vec3 uCluster;
            uniform vec2 uHood;
            uniform vec3 uDashColor;
            uniform vec3 uSeatColor;
            uniform vec3 uGlowColor;
            uniform float uShift;
            // The driver's place is fixed to the vehicle: uSeat is where the eye is in it, the
            // head leaning or not. uSteer is how far the wheel is turned, to the right. uTrim
            // is the wood on the dash, its vents, the cover of the seats and what stands
            // between them, and uFittings what is set in the dash. uScreen is the foot of the
            // windscreen, along and up, the sill of the driver's window and how far out from
            // the wheel its glass is. uDash is how far across the vehicle's middle line is from
            // the wheel, by sign, and the top of the dashboard. uLamps is whether the engine
            // runs and whether the headlights are on.
            uniform vec3 uSeat;
            uniform float uSteer;
            uniform vec4 uTrim;
            uniform float uFittings;
            uniform vec4 uScreen;
            uniform vec2 uDash;
            uniform vec3 uDoorMirror;
            uniform vec3 uPaint;
            uniform vec2 uLamps;
            // The glasshouse the picture builds over the driver. uHouse is the rail over the
            // screen, along and up, and how thick the pillars are. uPad is where the shelf of
            // the dashboard begins and where the pillar behind the driver's door stands, both
            // along. uTrade is what the vehicle carries for its work. uArms is whether the
            // driver's hands are on the wheel, and uSkin and uSleeve what they are covered in,
            // the sleeve's fourth part being how far down the arm it comes: 1 to the wrist,
            // a half to the elbow, and nought for a bare arm.
            uniform vec3 uHouse;
            uniform vec2 uPad;
            uniform float uTrade;
            uniform float uArms;
            uniform vec3 uSkin;
            uniform vec4 uSleeve;
            uniform vec3 uBare;
            // How brightly the lamp in the roof is lit: it comes on with an open door.
            uniform float uDome;
            uniform sampler2D uCarpet;
            uniform float uCarpetGain;
            // What part of the cabin the nearest of the body's own triangles is at each pixel,
            // its glass left out, and how far off it is. uParted says whether that was drawn,
            // and whether the seats in it were told from the shell.
            uniform sampler2D uParts;
            uniform sampler2D uPartsDepth;
            uniform vec2 uParted;
            uniform sampler2D uWood;
            uniform float uWoodGain;
            uniform sampler2D uBehind;
            uniform vec3 uGauges;
            uniform sampler2D uVinyl;
            uniform sampler2D uCloth;
            uniform sampler2D uJersey;
            uniform vec2 uWearGain;
            uniform vec4 uRoot[16];
            uniform float uRoots;
            uniform vec2 uCabinGain;
            uniform vec3 uOwnAmbient;
            uniform float uFigureAmbient;
            uniform sampler2D uFacing;
            uniform float uHaveFacing;
            uniform float uRaining;
            uniform float uTime;
            """ + BEAMS + MuzzleFlash.GLSL + """
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
            // The driver's place is laid out at one and a half times the size a vehicle is
            // drawn at, and is brought down to it where it is traced.
            const float CABIN = 0.6667;

            float block(vec3 v, vec3 half, float round) {
                vec3 d = abs(v) - half;
                return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0) - round;
            }

            float rod(vec3 v, vec3 a, vec3 b, float width) {
                vec3 along = b - a;
                return length(v - a - along * clamp(dot(v - a, along) / dot(along, along), 0.0, 1.0)) - width;
            }

            // The driver's end of a cabin, which the game's model has as a six-sided ring on
            // a flat board: a wheel with a padded rim, a hub and three spokes, the stalks
            // behind it, the shroud of its column, a hooded binnacle for the instruments and
            // the mirror on the screen. p is measured from the eye along the vehicle, across
            // it and up; uWheel is the wheel's middle and radius and uWheelAxis the way its
            // column runs. Returns how far p is from the nearest of them, and in part which:
            // 1 rim, 2 hub, 3 spoke, 4 stalk, 5 shroud, 6 binnacle, 7 mirror.
            // A spoke from the hub out to the rim at a turn, flat and dished like the rim.
            float spokeAt(vec3 w, vec2 way, float radius, float dish) {
                float along = clamp(dot(w.xy, way), 0.0, radius);
                vec2 off = w.xy - way * along;
                // Flattened, it is nearer than this says by as much as it is flattened: taken
                // whole, a line of sight steps through a thin spoke and leaves it in pieces.
                return (length(vec3(off, (w.z - dish) * 2.6)) - uKit.w) / 2.6;
            }

            // A place on the wheel's face as it is with the wheel turned by that much, to the
            // right as the driver sees it.
            vec2 gSteered = vec2(1.0, 0.0);

            vec2 steered(vec2 v) {
                return vec2(v.x * gSteered.x - v.y * gSteered.y, v.y * gSteered.x + v.x * gSteered.y);
            }

            void poseArms();

            float built(vec3 p, float nearest, inout float part);
            vec3 builtPaint(vec3 p, float part);
            // What the part last painted is made of: 0 grained like leather, 1 woven cloth,
            // 2 the nap of a roof's lining, 3 carpet; how strongly its grain shows; and how
            // much of a mirror it is.
            float gWoven = 0.0, gGrain = 0.6, gGloss = 0.4;
            // How much of it is a lit display, which glows with the instruments.
            float gGlow = 0.0;

            // Parts past the first seven: 8 the door mirror, 9 what stands between the seats,
            // 10 the boot of a lever on the floor, 11 a post up the middle of the screen. The
            // rest are the glasshouse and the furnishings the picture builds.
            // While set, what is far from a place is only said to be at least so far off,
            // which is all a line of sight walking through the cabin asks of it: at 1, what
            // is more than 0.04 off, and at less, what is further off in proportion.
            float gRoughly = 0.0;

            float cabinAt(vec3 p, out float part) {
                vec3 q = p - uWheel.xyz;
                vec3 rise = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                vec3 w = vec3(q.y, dot(q, rise), dot(q, uWheelAxis));
                // Which way across the middle of the vehicle is from the wheel.
                float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                float radius = uWheel.w * uKit.x;
                float nearest = 1000.0;
                part = 0.0;
                // The wheel, its column and the instruments behind it are all within a
                // ball round the column's head.
                float round1 = length(w - vec3(0.0, 0.05, 0.1)) - 0.42;
                if (gRoughly * round1 > 0.04) {
                    nearest = round1;
                } else {
                    float outward = length(w.xy);
                    float rim = length(vec2(outward - radius, w.z * 0.82)) - uKit.y;
                    float dish = 0.032 * (1.0 - min(outward / radius, 1.0));
                    // The hub and the spokes turn with the steering, about the column.
                    vec2 spun = steered(w.xy);
                    float hub = block(vec3(spun.x, spun.y + 0.012, w.z - 0.02), vec3(uHub.xy - uHub.z, 0.012), uHub.z);
                    vec3 s = vec3(spun.x, spun.y + 0.012, w.z);
                    float spoke;
                    if (uKit.z < 2.5) {
                        spoke = min(spokeAt(s, vec2(0.9950, -0.0998), radius, dish), spokeAt(s, vec2(-0.9950, -0.0998), radius, dish));
                    } else if (uKit.z < 3.5) {
                        spoke = min(min(spokeAt(s, vec2(1.0000, 0.0000), radius, dish), spokeAt(s, vec2(-1.0000, -0.0000), radius, dish)), spokeAt(s, vec2(0.0000, -1.0000), radius, dish));
                    } else if (uKit.z < 4.5) {
                        spoke = min(min(spokeAt(s, vec2(0.9553, -0.2955), radius, dish), spokeAt(s, vec2(-0.9553, -0.2955), radius, dish)), min(spokeAt(s, vec2(0.5403, -0.8415), radius, dish), spokeAt(s, vec2(-0.5403, -0.8415), radius, dish)));
                    } else if (uKit.z < 5.5) {
                        spoke = min(min(spokeAt(s, vec2(0.8525, 0.5227), radius, dish), spokeAt(s, vec2(-0.8517, 0.5240), radius, dish)), min(spokeAt(s, vec2(0.8525, -0.5227), radius, dish), spokeAt(s, vec2(-0.8534, -0.5213), radius, dish)));
                    } else {
                        spoke = min(min(spokeAt(s, vec2(0.8678, 0.4969), radius, dish), spokeAt(s, vec2(-0.8670, 0.4983), radius, dish)), spokeAt(s, vec2(0.0000, -1.0000), radius, dish));
                    }
                    vec3 mirrored = vec3(abs(w.x), w.y, w.z);
                    float stalk = min(rod(mirrored, vec3(0.05, 0.004, 0.09), vec3(0.17, 0.03, 0.076), 0.006), length(mirrored - vec3(0.17, 0.03, 0.076)) - 0.01);
                    if (uShift > 0.5 && uShift < 1.5) {
                        // A lever on the column, up and towards the middle of the vehicle, with a
                        // ball. It is laid out with the middle of the vehicle on the low side of across.
                        vec3 inboard = vec3(-w.x * toward, w.y, w.z);
                        stalk = min(stalk, min(rod(inboard, vec3(-0.04, 0.02, 0.11), vec3(-0.24, 0.12, 0.05), 0.007), length(inboard - vec3(-0.24, 0.12, 0.05)) - 0.02));
                    }
                    float shroud = max(length(vec2(w.x, w.y + 0.012)) - 0.058, abs(w.z - 0.14) - 0.1) - 0.006;
                    vec3 hood = w - vec3(0.0, 0.112, 0.2);
                    float binnacle;
                    if (uHood.y > 0.001) {
                        binnacle = max(block(hood, vec3(uHood.x, uHood.y, 0.065), 0.022), -block(hood - vec3(0.0, -0.006, -0.03), vec3(uHood.x - 0.017, uHood.y - 0.016, 0.075), 0.012));
                    } else {
                        // No hood: the instruments stand in a flat plate.
                        binnacle = block(hood - vec3(0.0, 0.0, 0.075), vec3(uHood.x, 0.07, 0.006), 0.004);
                    }
                    nearest = rim;
                    part = 1.0;
                    if (hub < nearest) { nearest = hub; part = 2.0; }
                    if (spoke < nearest) { nearest = spoke; part = 3.0; }
                    if (stalk < nearest) { nearest = stalk; part = 4.0; }
                    if (shroud < nearest) { nearest = shroud; part = 5.0; }
                    if (binnacle < nearest) { nearest = binnacle; part = 6.0; }
                }
                float round2 = length(p - uMirror - vec3(0.025, 0.0, 0.005)) - 0.15;
                if (gRoughly * round2 > 0.04) {
                    nearest = min(nearest, round2);
                } else {
                    float mirror = block(p - uMirror, vec3(0.01, 0.115, 0.032), 0.01);
                    if (mirror < nearest) { nearest = mirror; part = 7.0; }
                    float hung = rod(p, uMirror, uMirror + vec3(0.05, 0.0, 0.014), 0.009);
                    if (hung < nearest) { nearest = hung; part = 22.0; }
                }
                // The mirror on the driver's door: a housing outside the glass at the foot of
                // the screen's pillar, on an arm from the door.
                vec3 housed = p - uDoorMirror;
                float round3 = length(housed - vec3(0.02, toward * 0.05, -0.02)) - 0.18;
                if (gRoughly * round3 > 0.04) {
                    nearest = min(nearest, round3);
                } else {
                    float doorMirror = min(block(housed, vec3(0.02, 0.066, 0.04), 0.014),
                            rod(housed, vec3(0.01, toward * 0.06, -0.03), vec3(0.04, toward * 0.17, -0.055), 0.012));
                    if (doorMirror < nearest) { nearest = doorMirror; part = 8.0; }
                }
                // What stands on the floor is all below the wheel's middle.
                float over = q.z + 0.11;
                if (gRoughly * over > 0.04) {
                    nearest = min(nearest, over);
                } else {
                    if (uShift > 1.5) {
                        // A lever out of the floor beside the driver's knee, with a ball.
                        vec3 inboard = vec3(-w.x * toward, w.y, w.z);
                        float lever = min(rod(inboard, vec3(-0.42, -0.5, -0.05), vec3(-0.4, -0.22, -0.12), 0.01), length(inboard - vec3(-0.4, -0.22, -0.12)) - 0.028);
                        if (lever < nearest) { nearest = lever; part = 4.0; }
                    }
                    if (uTrim.w > 0.5) {
                        // The lever's foot is where the floor lever above stands.
                        vec3 foot = q - vec3(-0.213, toward * 0.42, -0.44);
                        if (uTrim.w < 2.5) {
                            float boot = length(foot * vec3(1.0, 1.0, 1.5)) - 0.055;
                            if (boot < nearest) { nearest = boot; part = 10.0; }
                        }
                        float between = 1000.0;
                        if (uTrim.w < 1.5) {
                            // A console from under the dash back between the seats.
                            between = block(q - vec3(-0.45, toward * 0.42, -0.6), vec3(0.5, 0.085, 0.13), 0.02);
                        } else if (uTrim.w > 2.5) {
                            // A van's engine stands between its seats under a padded cover.
                            between = block(q - vec3(-0.25, uDash.x, -0.62), vec3(0.55, 0.19, 0.2), 0.07);
                        }
                        if (between < nearest) { nearest = between; part = 9.0; }
                    }
                }
                if (uTrim.w > 3.5) {
                    // A step van's screen is two flat panes with a post between them.
                    float post = rod(p, vec3(uScreen.x, uWheel.y + uDash.x, uScreen.y - 0.05), vec3(uScreen.x - 0.06, uWheel.y + uDash.x, uScreen.y + 0.9), 0.02);
                    if (post < nearest) { nearest = post; part = 11.0; }
                }
                return built(p, nearest, part);
            }

            float wheelAt(vec3 p) {
                float part;
                return cabinAt(p, part);
            }

            // Wood for a dash: the grain of the picture's planed wood, stained walnut and
            // lacquered dark.
            vec3 walnut(vec2 on) {
                float grain = dot(texture2D(uWood, on).rgb, vec3(0.299, 0.587, 0.114)) * uWoodGain;
                return vec3(0.33, 0.18, 0.08) * (0.25 + 0.75 * grain * grain);
            }

            // What a part of the cabin is made of at p, as a colour for the picture to light.
            vec3 cabinPaint(vec3 p, float part) {
                gWoven = 0.0;
                gGrain = 0.6;
                gGloss = 0.4;
                gGlow = 0.0;
                if (part > 11.5) {
                    return builtPaint(p, part);
                }
                vec3 q = p - uWheel.xyz;
                vec3 rise = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                vec3 w = vec3(q.y, dot(q, rise), dot(q, uWheelAxis));
                float radius = uWheel.w * uKit.x;
                vec3 paint = uDashColor;
                if (part < 1.5) {
                    // A thick rim is leather sewn round it, the seam on its inner side. A thin
                    // one is hard plastic in the colour of the dash.
                    float outward = length(w.xy);
                    float round = atan(w.z, radius - outward);
                    float seam = step(0.0195, uKit.y) * (1.0 - smoothstep(0.12, 0.2, abs(round))) * step(0.5, fract(atan(w.y, w.x) * radius / 0.011));
                    paint = mix(uDashColor * 0.9, uDashColor * 2.2 + 0.08, seam);
                } else if (part < 2.5) {
                    // The pad over the horn, with the maker's badge pressed into it.
                    vec2 spun = steered(w.xy);
                    float badge = abs(spun.x) / 0.022 + abs(spun.y + 0.012) / 0.015;
                    paint = mix(uDashColor * 0.85, uDashColor * 1.6 + 0.12, step(0.78, badge) * step(badge, 1.0));
                } else if (part < 3.5) {
                    // Thin spokes are bare metal, drilled. Broad ones are padded like the hub.
                    // A hole every so far out along the middle of each of the three.
                    vec2 spun = steered(w.xy) + vec2(0.0, 0.012);
                    float reach = length(spun);
                    float turn = atan(spun.y, spun.x);
                    float beside = min(abs(sin(turn - 0.52)), min(abs(sin(turn - 2.62)), abs(sin(turn - 4.7124)))) * reach;
                    float holes = step(length(vec2((fract(reach / 0.045) - 0.5) * 0.045, beside)), 0.0065);
                    paint = uKit.z > 5.5 ? mix(vec3(0.52, 0.52, 0.54), vec3(0.08), holes * step(0.06, reach)) : uDashColor * 0.8;
                    if (uKit.z > 5.5) {
                        gGrain = 0.0;
                        gGloss = 0.9;
                    }
                } else if (part < 4.5) {
                    // Stalks and levers are black, with a pale ball on a gear lever.
                    vec3 inboard = vec3(uDash.x < 0.0 ? w.x : -w.x, w.y, w.z);
                    paint = length(inboard - vec3(-0.24, 0.12, 0.05)) < 0.024 && uShift > 0.5 && uShift < 1.5 ? vec3(0.7, 0.68, 0.62) : vec3(0.06, 0.06, 0.065);
                    gGrain = 0.0;
                    gGloss = 0.6;
                } else if (part < 5.5) {
                    paint = uDashColor * 0.6;
                } else if (part < 6.5) {
                    // A plate with no hood is bare metal; a hood is the dash's own stuff.
                    paint = uHood.y > 0.001 ? uDashColor * 0.7 : vec3(0.5, 0.5, 0.52);
                } else if (part < 8.5) {
                    paint = vec3(0.07, 0.07, 0.075);
                    gGrain = 0.0;
                    gGloss = 0.5;
                } else if (part < 9.5) {
                    if (uTrim.w < 1.5) {
                        // A console in the dash's own stuff, its top in wood where the dash has wood.
                        bool top = q.z > -0.475 && abs(abs(q.y) - 0.42) < 0.07;
                        paint = top && uTrim.x > 0.0 ? walnut(q.xy * vec2(2.0, 9.0)) : uDashColor * 0.7;
                    } else {
                        // An engine cover in ribbed black rubber, with a tray pressed into its top.
                        float ribs = 0.75 + 0.25 * step(0.5, fract(q.x / 0.03));
                        vec2 tray = abs(vec2(q.x + 0.25, q.y - uDash.x)) - vec2(0.2, 0.1);
                        float edge = 1.0 - smoothstep(0.0, 0.012, abs(max(tray.x, tray.y)));
                        paint = vec3(0.05, 0.05, 0.055) * mix(ribs, 0.5, edge * step(-0.36, q.z));
                    }
                } else if (part < 10.5) {
                    // The boot is soft leather, in folds round the lever.
                    paint = vec3(0.045, 0.04, 0.04) * (0.7 + 0.3 * sin(length(q.xy - vec2(-0.213, (uDash.x < 0.0 ? -1.0 : 1.0) * 0.42)) * 260.0));
                } else {
                    paint = uDashColor * 0.5;
                }
                return paint;
            }

            // How near p is to the line from the middle of a dial out to its edge at a turn.
            float needle(vec2 p, float turn, float reach) {
                vec2 way = vec2(cos(turn), sin(turn));
                float along = clamp(dot(p, way), -0.12, reach);
                return length(p - way * along);
            }

            // The face of an instrument, at p from its middle with its rim at 1. Left of the
            // column is the speedometer, right of it the rev counter with the fuel gauge in
            // its foot. uGauges is the road speed, the engine's speed and the fuel, each as a
            // part of the gauge's full scale. Glow is how much of the colour is lit from behind.
            // A strip speedometer: a wide slot with its scale along the bottom and a needle
            // that slides, a square fuel gauge at its left end, and warning lamps beneath.
            // p runs -1 to 1 across the slot and up it.
            vec3 stripFace(vec2 p, out float glow) {
                glow = 0.0;
                vec3 face = vec3(0.03, 0.03, 0.035);
                float x = (p.x + 0.55) / 1.45;
                if (x >= 0.0 && x <= 1.0 && p.y > -0.3) {
                    float major = 1.0 - smoothstep(0.004, 0.008, abs(fract(x * 8.0 + 0.5) - 0.5) / 8.0);
                    float minor = 1.0 - smoothstep(0.002, 0.005, abs(fract(x * 40.0 + 0.5) - 0.5) / 40.0);
                    float marks = max(major * step(p.y, 0.5), minor * step(p.y, 0.1));
                    face = mix(face, vec3(0.88, 0.92, 0.86), marks);
                    float hand = 1.0 - smoothstep(0.006, 0.012, abs(x - clamp(uGauges.x, 0.0, 1.0)));
                    face = mix(face, vec3(0.98, 0.38, 0.08), hand);
                    glow = max(marks, hand);
                } else if (p.x < -0.62 && p.y > -0.3) {
                    vec2 q = vec2((p.x + 0.8) / 0.16, (p.y - 0.1) / 0.6);
                    float level = step(q.y, clamp(uGauges.z, 0.0, 1.0) * 2.0 - 1.0) * step(abs(q.x), 0.5);
                    face = mix(face, vec3(0.9, 0.6, 0.15), level * 0.9);
                    glow = level;
                } else if (p.y < -0.45) {
                    float lamp = step(abs(fract(p.x * 4.0) - 0.5), 0.3) * step(-0.85, p.y) * step(abs(p.x), 0.5);
                    // Four warning lamps: fuel nearly out, headlights on, engine running, and
                    // one the picture has nothing to light with.
                    float which = floor(p.x * 4.0 + 2.0);
                    vec3 lens = which < 0.5 ? vec3(0.95, 0.5, 0.08) : which < 1.5 ? vec3(0.25, 0.45, 1.0) : which < 2.5 ? vec3(0.2, 0.9, 0.3) : vec3(0.9, 0.12, 0.06);
                    float lamped = which < 0.5 ? uLamps.x * step(uGauges.z, 0.12) : which < 1.5 ? uLamps.y : which < 2.5 ? uLamps.x : 0.0;
                    face = mix(face, mix(lens * 0.13, lens, lamped), lamp);
                    glow = lamp * lamped;
                }
                return face;
            }

            vec3 dialFace(vec2 p, float right, out float glow) {
                float out2 = length(p);
                glow = 0.0;
                if (out2 > 0.9) {
                    // A bright ring round the glass.
                    return vec3(0.55, 0.55, 0.57) * (0.6 + 0.4 * sin(atan(p.y, p.x) * 2.0 + 1.0));
                }
                vec3 face = vec3(0.035, 0.035, 0.04);
                // The scale runs three quarters of the way round, from lower left over the top.
                const float FROM = 3.927, SWEEP = 4.712;
                float turn = atan(p.y, p.x);
                float at = fract((FROM - turn) / 6.2831853) * 6.2831853 / SWEEP;
                if (at <= 1.0) {
                    float major = 1.0 - smoothstep(0.012, 0.02, abs(fract(at * 8.0 + 0.5) - 0.5) / 8.0 * SWEEP * out2);
                    float minor = 1.0 - smoothstep(0.006, 0.012, abs(fract(at * 40.0 + 0.5) - 0.5) / 40.0 * SWEEP * out2);
                    float marks = max(major * step(0.66, out2) * step(out2, 0.86), minor * step(0.78, out2) * step(out2, 0.86));
                    vec3 ink = right > 0.5 && at > 0.8 ? vec3(0.95, 0.2, 0.1) : vec3(0.88, 0.92, 0.86);
                    face = mix(face, ink, marks);
                    glow = marks;
                }
                if (right > 1.5) {
                    // A small gauge: one needle on a quarter of a turn across its top.
                    float hand = 1.0 - smoothstep(0.05, 0.09, needle(p, 2.356 - 1.571 * clamp(right > 2.5 ? 0.5 : uGauges.z, 0.0, 1.0), 0.8));
                    face = mix(vec3(0.035, 0.035, 0.04), vec3(0.88, 0.92, 0.86), step(0.7, out2) * (1.0 - smoothstep(0.03, 0.06, abs(fract((2.356 - turn) / 1.571 * 4.0 + 0.5) - 0.5) / 4.0 * 1.571 * out2)) * step(0.0, p.y));
                    face = mix(face, vec3(0.98, 0.38, 0.08), hand);
                    glow = max(hand, step(0.7, out2) * step(0.0, p.y) * 0.5);
                    return face;
                }
                if (right > 0.5) {
                    // Fuel: a short scale in the foot of the dial, empty to the left.
                    vec2 q = (p - vec2(0.0, -0.42)) / 0.34;
                    float turn2 = atan(q.y, q.x);
                    float span = (2.356 - turn2) / 1.571;
                    float far2 = length(q);
                    if (span >= 0.0 && span <= 1.0 && far2 < 1.0) {
                        float marks = (1.0 - smoothstep(0.03, 0.06, abs(fract(span * 4.0 + 0.5) - 0.5) / 4.0 * 1.571 * far2)) * step(0.7, far2);
                        face = mix(face, span < 0.15 ? vec3(0.95, 0.2, 0.1) : vec3(0.88, 0.92, 0.86), marks);
                        glow = max(glow, marks);
                    }
                    float hand2 = 1.0 - smoothstep(0.03, 0.06, needle(q, 2.356 - 1.571 * clamp(uGauges.z, 0.0, 1.0), 0.9));
                    face = mix(face, vec3(0.95, 0.35, 0.08), hand2);
                    glow = max(glow, hand2);
                }
                float shown = clamp(right > 0.5 ? uGauges.y : uGauges.x, 0.0, 1.05);
                float hand = 1.0 - smoothstep(0.022, 0.04, needle(p, FROM - SWEEP * shown, 0.8));
                face = mix(face, vec3(0.98, 0.38, 0.08), hand);
                glow = max(glow, hand);
                face = mix(face, vec3(0.02, 0.02, 0.022), 1.0 - smoothstep(0.1, 0.12, out2));
                return face;
            }

            float hash21(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            // Half the width of the front screen, in tiles. A place on it is counted across
            // from its middle, the driver's side below nought, and up from 0.3 under its foot.
            float gScreenHalf = 0.72;

            // Where a place on the front screen is to its two wipers. They are parked along
            // its foot, pointing away from the driver's side, and sweep up together through
            // a little over a right angle and back. x is 1 where a blade wipes, y 1 on the
            // line the blades leave at the end of their reach, z how long since a blade
            // last passed, 0 just now to 1 long enough for the rain to be back, and w 1 on
            // a blade itself while it rains.
            vec4 sweepAt(vec2 on) {
                const float SWEEP = 1.92, BEAT = 1.5;
                float reach = 0.66 * gScreenHalf;
                float phase = fract(uTime / BEAT);
                float at = phase < 0.5 ? 2.0 * phase : 2.0 - 2.0 * phase;
                vec4 found = vec4(0.0, 0.0, 1.0, 0.0);
                for (int i = 0; i < 2; i++) {
                    vec2 rel = on - vec2((i == 0 ? -0.62 : 0.06) * gScreenHalf, -0.30);
                    float far2 = length(rel);
                    float q = atan(rel.y, rel.x) / SWEEP;
                    if (q < 0.02 || q > 1.0 || far2 < 0.05) {
                        continue;
                    }
                    found.y = max(found.y, exp(-pow((far2 - reach) / 0.012, 2.0)));
                    if (far2 < reach) {
                        found.x = 1.0;
                        float since = phase < 0.5 ? (q < at ? phase - q * 0.5 : phase + q * 0.5) : (q > at ? phase - (1.0 - q * 0.5) : phase - q * 0.5);
                        found.z = min(found.z, clamp(since * BEAT / 1.1, 0.0, 1.0));
                        found.w = max(found.w, 1.0 - smoothstep(0.004, 0.011, abs(q - at) * SWEEP * far2));
                    }
                }
                found.y *= 1.0 - found.x;
                return found;
            }

            vec4 flared(vec4 glass, vec3 look, float dirt);

            vec4 pane(vec2 on, vec3 n, vec3 view, float swept) {
                float dust = smoothstep(0.35, 0.85, noise2(on * 5.0) * 0.55 + noise2(on * 15.0 + 3.0) * 0.3 + noise2(on * 61.0) * 0.15);
                float runs = smoothstep(0.62, 0.95, noise2(vec2(on.x * 46.0, on.y * 2.2)));
                float dirt = clamp(dust * 0.75 + runs * 0.45, 0.0, 1.0);
                // The wipers keep their two arcs of the front screen clean and push what they
                // clear to the arcs' edges, where it dries as a line.
                vec4 wiper = vec4(0.0);
                if (swept > 0.5) {
                    wiper = sweepAt(on);
                    float inside = wiper.x;
                    dirt = dirt * (1.0 - 0.75 * inside) + 0.55 * wiper.y + 0.45 * (1.0 - inside);
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
                    float fresh = swept > 0.5 ? wiper.z : 1.0;
                    float blade = wiper.w;
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
                    // On the panes no wiper reaches the rain gathers and runs down in
                    // trickles, and the glass mists over from its foot where the warm air
                    // inside meets it.
                    float still = 1.0 - (swept > 0.5 ? wiper.x : 0.0);
                    float trickle = smoothstep(0.8, 0.97, noise2(vec2(on.x * 70.0, on.y * 2.5 + uTime * 0.35))) * smoothstep(0.35, 0.6, noise2(vec2(on.x * 9.0 + 4.0, on.y * 1.5 - uTime * 0.2)));
                    float running = trickle * uRaining * still * (1.0 - 0.7 * swept);
                    glass.rgb = mix(glass.rgb, held, running * 0.6 / (running * 0.6 + glass.a));
                    glass.a = min(glass.a + running * 0.28, 0.9);
                    float mist = uRaining * still * (1.0 - smoothstep(-0.32, 0.02 + 0.12 * noise2(on * 3.0), on.y)) * (0.5 + 0.5 * noise2(on * 11.0));
                    glass.rgb = mix(glass.rgb, atmosphere(vec3(0.0, 0.0, 1.0)) * 0.9 + 0.01, mist * 0.3 / (mist * 0.3 + glass.a));
                    glass.a = min(glass.a + mist * 0.3, 0.92);
                    glass = mix(glass, vec4(0.004, 0.004, 0.005, 1.0), blade);
                }
                return flared(glass, -view, dirt);
            }

            // The pane a line of sight from the seat leaves the cabin by, told by which way
            // the line goes: the screens lean back and the side windows stand nearly upright.
            vec4 paneSeen(vec3 out3) {
                vec2 side = vec2(-uAhead.y, uAhead.x);
                float along = dot(out3.xy, uAhead), across = dot(out3.xy, side);
                bool ends = abs(along) > abs(across);
                vec3 faces = ends ? normalize(vec3(-sign(along) * uAhead, 0.75)) : normalize(vec3(-sign(across) * side, 0.12));
                float reach = ends ? 0.9 / max(abs(along), 0.2) : 0.5 / max(abs(across), 0.2);
                vec3 to = out3 * reach;
                return pane(vec2(ends ? dot(to.xy, side) : dot(to.xy, uAhead) + 7.0, to.z), faces, -out3, ends && along > 0.0 ? 1.0 : 0.0);
            }

            vec4 paneBuilt(vec3 out3);
            vec3 domeLit(vec3 o, vec3 m);
            vec3 domeLamp(vec3 o, float part);
            vec3 litFace(vec2 p, out float glow);
            float cabinReach(vec3 o, vec3 d);
            // How far off along the line of sight the pane last asked for is, as the driver's place is laid out.
            float gPaneFar = 0.0;

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

            // How surely a pixel is glass by the body's triangles. The frame of a window is
            // painted round the edge of its pane's triangles, so the glass is those triangles
            // drawn in by a frame's width. reach is how far off the pane is.
            float paned(vec2 uv, float reach) {
                float frame = 0.032 / (reach * uTan.y) * 0.5;
                // The panes of the front of the glasshouse are drawn at a half: the picture
                // builds its own there, and they frame nothing.
                float within = step(0.75, texture2D(uPanes, uv).r);
                for (int i = 0; i < 12; i++) {
                    float turn = 0.5236 * float(i);
                    within = min(within, step(0.75, texture2D(uPanes, uv + vec2(cos(turn) * uSize.y / uSize.x, sin(turn)) * frame).r));
                }
                return within;
            }

            // Where the nearest of the body's own triangles is at uv, its glass left out.
            vec3 partAt(vec2 uv) {
                float z = texture2D(uPartsDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                vec2 ndc = uv * 2.0 - 1.0;
                return uEye + (uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y)) * ahead;
            }

            // Which way the body faces at uv, as normalAt tells it, from its own triangles.
            vec3 partNormalAt(vec2 uv, vec3 at, float pixels) {
                vec2 step = pixels / uSize;
                vec3 east = partAt(uv + vec2(step.x, 0.0)) - at, west = at - partAt(uv - vec2(step.x, 0.0));
                vec3 north = partAt(uv + vec2(0.0, step.y)) - at, south = at - partAt(uv - vec2(0.0, step.y));
                vec3 across = dot(east, east) < dot(west, west) ? east : west;
                vec3 up = dot(north, north) < dot(south, south) ? north : south;
                vec3 square = cross(across, up);
                float size = length(square);
                return size > 1e-12 ? square / size : vec3(0.0);
            }

            // What the picture of the world behind the vehicle shows at a place in it, and
            // the sky where the world stops.
            vec3 behindAt(vec2 on) {
                vec3 seen = texture2D(uBehind, on).rgb;
                if (seen.r < -0.5) {
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    seen = atmosphere(normalize(vec3(-uAhead + side * ((0.5 - on.x) * 0.6), (on.y - 0.5) * 0.17 + 0.02)));
                }
                // One pixel of the small picture gone wrong would be a spark in the glass.
                return min(max(seen, vec3(0.0)), vec3(6.0)) * 0.82;
            }

            // What is set in the flat dashboard the game's body has, at a place on its face:
            // c is how far across from the wheel towards the passenger's side, h how high,
            // and paint the dash's own colour there. glows comes back as how much of the
            // answer is lit with the instruments, and bare as 1 where it has no grain.
            vec3 dashAt(float c, float h, vec3 paint, out float glows, out float bare) {
                glows = 0.0;
                bare = 0.0;
                float mid = abs(uDash.x);
                // Everything is hung from the height of the instruments, which the body's own
                // panel is at, and kept below the shelf under the screen.
                float band = min(uWheel.z + uWheelAxis.z * 0.25 + uWheelAxis.x * 0.106, uDash.y - 0.06);
                if (uTrim.x > 0.0 && (uTrim.x > 0.5 || abs(h - band) < uTrim.x)) {
                    // Wood across the dash at the height of the instruments, between two
                    // bright lines, or over the whole face.
                    paint = walnut(vec2(c * 1.3, h * 6.0));
                    bare = 1.0;
                    if (uTrim.x < 0.5 && abs(h - band) > uTrim.x - 0.004) {
                        paint = vec3(0.6, 0.6, 0.62);
                    }
                }
                if (uTrim.y > 0.5) {
                    // An air vent either side of the middle and one at each end of the dash.
                    float ends = uScreen.w - 0.2;
                    float vent = abs(c - mid + 0.13) < abs(c - mid - 0.13) ? mid - 0.13 : mid + 0.13;
                    vent = abs(c - 2.0 * mid - ends) < abs(c - vent) ? 2.0 * mid + ends : vent;
                    vent = abs(c + ends) < abs(c - vent) ? -ends : vent;
                    vec2 d = vec2(c - vent, h - band);
                    vec3 slats = vec3(mix(0.02, 0.11, step(0.5, fract(d.y / 0.016))));
                    if (uTrim.y < 1.5) {
                        float off = length(d);
                        if (off < 0.05) {
                            paint = off > 0.041 ? vec3(0.62, 0.62, 0.64) : slats;
                            bare = 1.0;
                        }
                    } else if (abs(d.x) < 0.085 && abs(d.y) < 0.034) {
                        bool surround = abs(d.x) > 0.078 || abs(d.y) > 0.027;
                        paint = surround ? paint * 0.45 : abs(d.x) < 0.006 ? vec3(0.1) : slats;
                        bare = 1.0;
                    }
                }
                if (mod(uFittings, 2.0) > 0.5) {
                    // A radio in the middle: a bright bezel, a lit scale with its pointer, a
                    // row of station keys and a knob at each end.
                    vec2 d = vec2(c - mid, h - band + 0.12);
                    if (abs(d.x) < 0.125 && abs(d.y) < 0.04) {
                        bare = 1.0;
                        paint = abs(d.x) > 0.119 || abs(d.y) > 0.034 ? vec3(0.58, 0.58, 0.6) : vec3(0.03, 0.03, 0.034);
                        if (abs(d.x) < 0.062 && d.y > 0.004 && d.y < 0.024) {
                            float ticks = step(0.8, fract(d.x / 0.0105)) * step(0.014, d.y);
                            paint = mix(vec3(0.62, 0.58, 0.42), vec3(0.08), ticks);
                            paint = mix(paint, vec3(0.9, 0.15, 0.05), step(abs(d.x - 0.018), 0.0025));
                            glows = 1.0;
                        } else if (abs(d.x) < 0.062 && d.y < -0.006 && d.y > -0.024) {
                            paint = vec3(0.02) + vec3(0.1) * step(0.14, fract(d.x / 0.0248 + 0.5));
                        }
                        float knob = length(vec2(abs(d.x) - 0.092, d.y));
                        if (knob < 0.021) {
                            float knurls = step(0.6, fract(atan(d.y, abs(d.x) - 0.092) * 3.5)) * step(0.008, knob);
                            paint = knob > 0.016 ? vec3(0.5, 0.5, 0.52) : vec3(0.07 + 0.16 * knurls);
                        }
                    }
                }
                if (uFittings > 1.5) {
                    // A glove box before the passenger: the line of its door and a bright button.
                    vec2 from = vec2(c - 2.0 * mid - 0.1, h - band + 0.2);
                    vec2 d = abs(from) - vec2(0.25, 0.09);
                    if (abs(max(d.x, d.y)) < 0.004) {
                        paint *= 0.3;
                    }
                    if (length(from - vec2(0.0, 0.06)) < 0.013) {
                        paint = vec3(0.6, 0.6, 0.62);
                        bare = 1.0;
                    }
                }
                return paint;
            }

            // The card of a door: the dash's stuff with a panel of the seats' cloth let into
            // it, an armrest under that, a handle and the winder of the window. x is how far
            // along the vehicle and h how high. woven comes back as 1 on the cloth and bare
            // as 1 on bright metal.
            vec3 doorAt(float x, float h, vec3 paint, out float woven, out float bare) {
                woven = 0.0;
                bare = 0.0;
                float sill = uScreen.z;
                if (x > uWheel.x - 0.8 && x < uScreen.x - 0.14 && h < sill - 0.13 && h > sill - 0.36) {
                    paint = uSeatColor * 0.9;
                    woven = 1.0;
                }
                if (h < sill - 0.38 && h > sill - 0.44) {
                    paint *= mix(1.3, 0.5, (sill - 0.38 - h) / 0.06);
                }
                vec2 winder = vec2(x - uWheel.x - 0.22, h - sill + 0.27);
                bool arm = winder.x > 0.0 && winder.x < 0.07 && abs(winder.y + winder.x * 0.4) < 0.007;
                if ((abs(x - uWheel.x + 0.02) < 0.055 && abs(h - sill + 0.075) < 0.011) || length(winder) < 0.02 || arm) {
                    paint = vec3(0.6, 0.6, 0.62);
                    woven = 0.0;
                    bare = 1.0;
                }
                if (length(winder - vec2(0.07, -0.028)) < 0.012) {
                    paint = vec3(0.05);
                }
                return paint;
            }

            // What a seat is covered in: ribs down it, pleats across it, plaid or two fine
            // stripes, in the seats' colour. c is how far across from the driver's place and
            // run how far along the cushion or up the back.
            vec3 seatAt(float c, float run) {
                vec3 cover = uSeatColor;
                if (uTrim.z > 0.5 && uTrim.z < 1.5) {
                    cover *= 0.7 + 0.3 * sqrt(abs(sin(3.1416 * c / 0.05)));
                } else if (uTrim.z > 1.5 && uTrim.z < 2.5) {
                    cover *= 0.7 + 0.3 * sqrt(abs(sin(3.1416 * run / 0.07)));
                } else if (uTrim.z > 2.5 && uTrim.z < 3.5) {
                    float broad = step(0.62, fract(c / 0.12)) + step(0.62, fract(run / 0.12));
                    float thin = max(step(0.93, fract(c / 0.12 + 0.3)), step(0.93, fract(run / 0.12 + 0.3)));
                    cover = mix(cover * (1.0 - 0.28 * broad), vec3(0.62, 0.52, 0.34), 0.7 * thin);
                } else if (uTrim.z > 3.5) {
                    float from = min(abs(c), abs(c - 2.0 * abs(uDash.x)));
                    cover = mix(cover, vec3(0.55, 0.08, 0.07), step(0.03, from) * step(from, 0.042));
                    cover = mix(cover, vec3(0.1, 0.16, 0.45), step(0.055, from) * step(from, 0.067));
                }
                return cover;
            }

            """;

    static final String FIGURE_LIGHT_MAIN = """
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 figure = texture2D(uFigure, uv);
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                // Behind the wheel, two round instruments in the dash, seen through its rim.
                bool wheel = false;
                bool dial = false;
                vec2 dialAt = vec2(0.0);
                float dialRight = 0.0;
                float dialGlow = 0.0;
                float dashGlow = 0.0;
                float dialFar = 0.0;
                bool traced = uInside > 0.5 && uWheel.w > 0.0 && uGlassPass < 0.5;
                if (traced) {
                    vec3 d = normalize(ray);
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 line = vec3(dot(d.xy, uAhead), dot(d.xy, side), d.z);
                    vec3 rise = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                    vec3 middle = uWheel.xyz + uWheelAxis * 0.25 + rise * 0.106;
                    float towards = dot(line, uWheelAxis);
                    if (towards > 0.05) {
                        float t = dot(middle - uSeat, uWheelAxis) * CABIN / towards;
                        vec3 q = uSeat + line * t / CABIN - middle;
                        vec2 on = vec2(q.y, dot(q, rise));
                        // Where the vehicle's instruments are: a strip, two dials side by
                        // side, one large dial between two small, a row of small ones, or
                        // one dial with a small one beside it.
                        vec2 at = on;
                        if (uCluster.x < 0.5 || uCluster.x > 5.5) {
                            vec2 face = at / vec2(uHood.x - 0.03, uHood.y - 0.018);
                            if (abs(face.x) < 1.0 && abs(face.y) < 1.0) {
                                dial = true;
                                dialAt = face;
                                dialRight = uCluster.x > 5.5 ? -2.0 : -1.0;
                                dialFar = t;
                            }
                        } else {
                            float count = uCluster.z;
                            for (int i = 0; i < 6; i++) {
                                if (float(i) >= count) {
                                    break;
                                }
                                float size = uCluster.y;
                                float gap = size * 2.0 + 0.016;
                                float middleX = (float(i) - (count - 1.0) * 0.5) * gap;
                                float kind = i == 0 ? 0.0 : i == 1 ? 1.0 : i == 2 ? 2.0 : 3.0;
                                if (uCluster.x > 1.5 && uCluster.x < 2.5) {
                                    // One large dial between two small.
                                    size = i == 1 ? uCluster.y : uCluster.y * 0.68;
                                    middleX = (float(i) - 1.0) * (uCluster.y * 1.68 + 0.014);
                                    kind = i == 1 ? 0.0 : i == 0 ? 2.0 : 1.0;
                                } else if (uCluster.x > 4.5) {
                                    // One dial dead ahead and a small one beside it.
                                    size = i == 0 ? uCluster.y : uCluster.y * 0.55;
                                    middleX = i == 0 ? -0.02 : uCluster.y * 1.55 + 0.0;
                                    kind = i == 0 ? 0.0 : 2.0;
                                }
                                vec2 face = (at - vec2(middleX, 0.0)) / size;
                                if (dot(face, face) < 1.0) {
                                    dial = true;
                                    dialAt = face;
                                    dialRight = kind;
                                    dialFar = t;
                                }
                            }
                        }
                    }
                }
                // What part of the cabin the nearest of the body's own triangles is here, and
                // how far ahead it is.
                float kind = 0.0;
                float bodyAhead = uFar;
                // Whether any of the body that is not glass is here at all, and whether what
                // the game painted here is of something the picture builds its own in place of.
                float shell = 0.0, unseen = 0.0;
                if (uInside > 0.5 && uParted.x > 0.5) {
                    vec4 told = texture2D(uParts, uv);
                    kind = floor(told.r * 8.0 + 0.5);
                    unseen = told.g;
                    shell = told.a;
                    float bodyZ = texture2D(uPartsDepth, uv).r * 2.0 - 1.0;
                    bodyAhead = 2.0 * uNear * uFar / (uFar + uNear - bodyZ * (uFar - uNear));
                }
                // With its wheel and its triangles known, a cabin is the picture's own from
                // the sills up, and what is glass in it is told by the triangles alone.
                bool builtCabin = uInside > 0.5 && uParted.x > 0.5 && uWheel.w > 0.0;
                // The steering wheel is found by walking each line of sight up to it.
                float wheelFar = 0.0;
                float wheelPart = 0.0;
                vec3 wheelPlace = vec3(0.0);
                vec3 wheelFaces = vec3(0.0);
                if (traced) {
                    vec3 d = normalize(ray);
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 line = vec3(dot(d.xy, uAhead), dot(d.xy, side), d.z);
                    float t = 0.05;
                    float most = cabinReach(uSeat, line) * CABIN;
                    gSteered = vec2(cos(uSteer), sin(uSteer));
                    poseArms();
                    float met = 0.0;
                    gRoughly = 1.0;
                    for (int i = 0; i < 64; i++) {
                        float gap = cabinAt(uSeat + line * t / CABIN, met) * CABIN;
                        if (gap < 0.0008) {
                            wheel = true;
                            break;
                        }
                        t += gap;
                        if (t > most) {
                            break;
                        }
                    }
                    if (wheel) {
                        // The wheel and the instruments are before everything. What is built
                        // further off, between the seats or outside the glass, is hidden by
                        // whatever of the body stands before it.
                        // The driver's own hands and sleeves are before everything too, and
                        // the pad over the dashboard is the top of the board it lies on.
                        bool always = met > 22.5 && met < 24.5 || met > 27.5 || met > 18.5 && met < 19.5 && kind > 1.5 && kind < 2.5;
                        if (met > 7.5 && !always && bodyAhead < t / length(ray) - 0.02) {
                            wheel = false;
                        }
                    }
                    // From a seat behind, the backs of the seats in front hide what is built
                    // before them, the wheel and the instruments too.
                    bool seatBefore = kind > 0.5 && kind < 1.5 && uArms < 0.5;
                    if (wheel && seatBefore && bodyAhead < t / length(ray) - 0.05) {
                        wheel = false;
                    }
                    if (dial && seatBefore && bodyAhead < dialFar / length(ray) - 0.05) {
                        dial = false;
                    }
                    if (wheel && dial && dialFar < t) {
                        wheel = false;
                    } else if (wheel) {
                        dial = false;
                    }
                    if (wheel) {
                        vec3 p = uSeat + line * t / CABIN;
                        // Which way it faces, from how far off it is at four places round the point.
                        vec3 slope = vec3(0.0);
                        for (int corner = 0; corner < 4; corner++) {
                            vec3 e = corner == 0 ? vec3(0.002, -0.002, -0.002) : corner == 1 ? vec3(-0.002, -0.002, 0.002) : corner == 2 ? vec3(-0.002, 0.002, -0.002) : vec3(0.002);
                            slope += e * wheelAt(p + e);
                        }
                        slope = normalize(slope);
                        wheelFaces = vec3(uAhead * slope.x + side * slope.y, slope.z);
                        wheelFar = t;
                        wheelPlace = p;
                        wheelPart = met;
                    }
                    // What is asked of the parts from here on is asked up to a hand's breadth
                    // off them, where what is far is still only said to be far.
                    gRoughly = 0.44;
                }
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                float ahead = 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
                // The game paints the windscreen over the bonnet beyond it. Where the nearest
                // of the body that is not glass is the bonnet, and what the game drew there is
                // a pane or nothing, the bonnet is seen through the glass and is drawn here.
                bool bonnetSeen = false;
                bool framed = false, blind = false;
                if (builtCabin && !wheel && !dial) {
                    bool open = shell < 0.5;
                    if (open && uHavePanes > 0.5 && figure.a >= 0.5 && texture2D(uPanes, uv).r > 0.75 && paned(uv, ahead * length(ray)) < 0.5) {
                        // The frame the game paints round the edge of a pane behind the driver.
                        open = false;
                        framed = true;
                    }
                    if (uGlassPass > 0.5) {
                        vec3 out3 = normalize(ray);
                        if (!open && kind < 3.5 || out3.z < -0.3 && open) {
                            discard;
                        }
                        gl_FragColor = paneBuilt(out3);
                        // The pane is as far off as it is, in the picture's own measure of
                        // depth: whatever of the cabin is before it hides it.
                        float paneAhead = gPaneFar * CABIN / length(ray);
                        gl_FragDepth = 0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / paneAhead) / (uFar - uWriteNear);
                        return;
                    }
                    if (open) {
                        discard;
                    }
                    bonnetSeen = kind > 3.5;
                    if (framed) {
                        figure = vec4(uDashColor * 0.55, 1.0);
                    } else {
                        ahead = bodyAhead;
                        if (unseen > 0.5 || figure.a < 0.5) {
                            // What the game painted here is not of what stands here.
                            figure = vec4(0.2, 0.2, 0.2, 1.0);
                            blind = true;
                        }
                    }
                } else if (kind > 3.5 && !wheel && !dial) {
                    if (uGlassPass < 0.5) {
                        bonnetSeen = figure.a < 0.5 || (uHavePanes > 0.5 ? paned(uv, ahead * length(ray)) > 0.5 : glass(uv) > 0.5);
                    }
                    if (!bonnetSeen && figure.a >= 0.5 && uHavePanes > 0.5 && texture2D(uPanes, uv).r > 0.5) {
                        // The frame of the pane, or the pane itself on the glass's own pass.
                        kind = 0.0;
                    }
                }
                float bonnet = kind > 3.5 && !wheel && !dial ? 1.0 : 0.0;
                if (figure.a < 0.5 && !wheel && !bonnetSeen) {
                    // From a seat the vehicle is all round the eye, so where the game drew
                    // none of it above the sills there is an opening with a pane in it.
                    vec3 out3 = normalize(ray);
                    if (uGlassPass < 0.5 || out3.z < -0.3) {
                        discard;
                    }
                    gl_FragColor = paneSeen(out3);
                    gl_FragDepth = 0.0;
                    return;
                }
                if (bonnetSeen) {
                    ahead = bodyAhead;
                    figure = vec4(uPaint, 1.0);
                }
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
                float close = uInside * step(ahead * length(ray), 2.2);
                // What the picture builds says which way it faces itself.
                vec3 found = wheel || dial ? vec3(0.0) : builtCabin && !framed ? partNormalAt(uv, at, 5.0) + partNormalAt(uv, at, 11.0)
                        : close > 0.5 ? normalAt(uv, at, 5.0) + normalAt(uv, at, 11.0) : normalAt(uv, at, 1.5) + normalAt(uv, at, 4.0);
                vec3 n = length(found) > 0.1 ? normalize(found) : view;
                if (bonnetSeen) {
                    // The depth the game drew here is the glass's. The bonnet's own is read,
                    // and evened towards level: it is a broad panel that faces up.
                    vec3 lies = partAt(uv);
                    vec3 square = cross(dFdx(lies), dFdy(lies));
                    square = length(square) > 1e-12 ? normalize(square) : vec3(0.0, 0.0, 1.0);
                    n = normalize(square * (dot(square, view) < 0.0 ? -0.5 : 0.5) + vec3(0.0, 0.0, 0.5));
                }
                // A person the picture drew itself comes with the way each point of them
                // faces, smooth over the whole body, and unlit: the light is all the picture's.
                float bare = 0.0;
                // What such a person's surface is at this point: 0 hair, 1 skin, 2 what is
                // worn, 3 nothing said. With it come how glossy it is, how thin or how
                // napped, which way hair's strands run, and the game's light where the person
                // stands, less than nought when that was not said.
                float worn = 3.0, gloss = 0.0, low = 0.0, outdoors = 1.0, stoodIn = -1.0;
                vec3 strand = vec3(0.0);
                if (uHaveFacing > 0.5) {
                    vec4 facing = texture2D(uFacing, uv);
                    if (facing.a > 0.5) {
                        vec3 run;
                        vec3 seen = surfaceOf(facing, worn, gloss, low, outdoors, run);
                        n = normalize(uRight * seen.x + uUp * seen.y - uForward * seen.z);
                        strand = uRight * run.x + uUp * run.y - uForward * run.z;
                        bare = 1.0;
                        if (worn < 2.5) {
                            // Beside such a surface's colour is the light, not how solid it is.
                            float said = floor(figure.a * 255.0 + 0.5);
                            stoodIn = said > 254.5 ? -1.0 : (said - 128.0) / 126.0;
                            figure.a = 1.0;
                        }
                    }
                }
                bool ghost = false;
                if (dial) {
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    n = -vec3(uAhead * uWheelAxis.x + side * uWheelAxis.y, uWheelAxis.z);
                    close = 1.0;
                    figure = vec4(dialRight < -1.5 ? litFace(dialAt, dialGlow) : dialRight < -0.5 ? stripFace(dialAt, dialGlow) : dialFace(dialAt, dialRight, dialGlow), 1.0);
                } else if (wheel) {
                    n = wheelFaces;
                    close = 1.0;
                    figure = vec4(cabinPaint(wheelPlace, wheelPart), 1.0);
                } else if (close > 0.5 && uWheel.w > 0.0 && !builtCabin) {
                    // The game's own six-sided wheel is still in its model, wider than the
                    // round one drawn over it. Where it shows it is painted out as the dark
                    // of the footwell behind it.
                    vec3 to = at - uEye;
                    vec3 q = uSeat + vec3(dot(to.xy, uAhead), dot(to.xy, vec2(-uAhead.y, uAhead.x)), to.z) / CABIN - uWheel.xyz;
                    float deep = dot(q, uWheelAxis);
                    float outward = length(q - uWheelAxis * deep);
                    ghost = abs(deep) < 0.07 && outward > uWheel.w - 0.07 && outward < uWheel.w + 0.07 && outward > uWheel.w * uKit.x - 0.1;
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
                float sheen = dial ? 0.5 : 0.4;
                if (cabin > 0.5) {
                    // The game draws a vehicle's skin without the smaller copies a texture
                    // needs to be seen at a slant, and from the seat every panel is at a slant:
                    // the weave of its trim comes out as rings of dots. Evened out over the
                    // pixels round about, it is cloth and plastic again.
                    if (!wheel && !dial && !bonnetSeen && !blind && !framed) {
                        vec4 sum = figure * 2.0 + east + west + north + south;
                        for (int i = 0; i < 4; i++) {
                            vec2 corner = vec2(i < 2 ? -1.0 : 1.0, mod(float(i), 2.0) < 0.5 ? -1.0 : 1.0);
                            sum += texture2D(uFigure, uv + corner * px * 1.5) + texture2D(uFigure, uv + corner.yx * vec2(-2.5, 2.5) * px) * 0.5;
                        }
                        if (sum.a > 6.0) {
                            figure = vec4(sum.rgb / sum.a, 1.0);
                        }
                    }
                    if (ghost) {
                        // Against a pale dash the dark of a footwell would be a ring as plain
                        // as the wheel it paints out: the dash's own colour, in shade.
                        figure = vec4(max(vec3(0.085, 0.09, 0.105), uDashColor * 0.4), 1.0);
                    }
                    // The game paints a cabin in flat colours. What it is made of is laid over
                    // them, fixed to the vehicle: grained vinyl on the dash, the doors and the
                    // wheel, woven cloth on the seats.
                    vec3 to = (at - uEye) / CABIN;
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 local = uSeat + vec3(dot(to.xy, uAhead), dot(to.xy, side), to.z);
                    vec3 faces = abs(vec3(dot(n.xy, uAhead), dot(n.xy, side), n.z));
                    vec2 lie = faces.z > faces.x && faces.z > faces.y ? local.xy : faces.x > faces.y ? local.yz : local.xz;
                    float most = max(figure.r, max(figure.g, figure.b)), least = min(figure.r, min(figure.g, figure.b));
                    // Pale parts are painted metal, the pillars and the door tops, and have no grain.
                    float dark = 1.0 - smoothstep(0.4, 0.65, most);
                    float woven = 0.0;
                    float grained = 0.0;
                    if (wheel) {
                        woven = gWoven;
                        grained = gGrain;
                        sheen = gGloss;
                        dashGlow = max(dashGlow, gGlow);
                    }
                    if (!dial && !wheel && kind > 0.5) {
                        // Which part of the cabin this is, the body's own triangles say, and
                        // each is given what the vehicle's kind has there, in the game's shading.
                        float c = (local.y - uWheel.y) * (uDash.x < 0.0 ? -1.0 : 1.0);
                        float plain = 0.0;
                        // In a cabin the picture lights itself, the game's own shading of its
                        // panels counts for little: it is painted on, and the light is not.
                        float painted = builtCabin ? clamp(0.8 + most * 0.6, 0.85, 1.1) : clamp(0.35 + most * 3.2, 0.4, 1.2);
                        if (kind < 1.5) {
                            figure.rgb = seatAt(c, faces.z > 0.6 ? local.x : local.z) * (builtCabin ? painted : clamp(most * 2.2, 0.5, 1.2));
                            woven = 1.0;
                        } else if (kind < 2.5) {
                            figure.rgb = uDashColor * painted;
                            // The top of the dash is a plain shelf: what is set in it is in its face.
                            if (faces.z < 0.9) {
                                figure.rgb = dashAt(c, local.z, figure.rgb, dashGlow, plain);
                            }
                        } else if (kind < 3.5) {
                            figure.rgb = doorAt(local.x, local.z, uDashColor * painted, woven, plain);
                        }
                        grained = bonnet > 0.5 || plain > 0.5 ? 0.0 : woven > 0.5 ? (builtCabin ? 0.7 : 0.45) : 0.8;
                        sheen = plain > 0.5 ? 0.9 : woven > 0.5 ? 0.05 : 0.4;
                    } else if (!dial && !wheel) {
                        // The rest of the shell. In a body whose seats are not pieces of their
                        // own they are part of it, and are told as the game paints them: with
                        // colour in them, below the glass, and not changing with the body's
                        // paint as its pillars and door tops do.
                        if (uParted.y < 0.5) {
                            woven = smoothstep(0.2, 0.35, (most - least) / max(most, 0.001)) * step(0.2, most);
                            if (uParted.x > 0.5) {
                                woven *= step(local.z, uScreen.z + 0.12) * step(distance(texture2D(uFigure, uv).rgb, texture2D(uRepainted, uv).rgb), 0.06);
                            }
                            figure.rgb = mix(figure.rgb, uSeatColor * (most * 2.2), woven * 0.7);
                        }
                        // The floor and the sills are trimmed like the dash where they are
                        // dark. The pillars and the roof stay as they are.
                        float low = uParted.x > 0.5 ? step(local.z, uScreen.y - 0.02) : step(at.z, uEye.z - 0.06);
                        figure.rgb = mix(figure.rgb, uDashColor * (0.35 + most * 3.2), (1.0 - woven) * dark * 0.6 * low);
                        grained = mix(0.9 * dark, 0.45, woven);
                    }
                    vec3 grain = texture2D(uVinyl, lie * 2.6).rgb * uCabinGain.x;
                    if (builtCabin && woven > 0.5) {
                        // Cloth is a nap of short fibres, and on a seat or a door a weave of
                        // threads over it, which is let go where a thread is finer than a pixel.
                        float nap = dot(texture2D(uCarpet, lie * (woven > 2.5 ? 5.0 : woven > 1.5 ? 12.0 : 8.0)).rgb, vec3(0.299, 0.587, 0.114)) * uCarpetGain;
                        vec2 thread = lie / 0.0045;
                        float fine = clamp(1.0 - 1.4 * max(fwidth(thread.x), fwidth(thread.y)), 0.0, 1.0);
                        float weave = woven < 1.5 ? 1.0 - 0.3 * fine * abs(sin(3.1416 * thread.x) * sin(3.1416 * thread.y)) : 1.0;
                        grain = vec3(nap * weave);
                    } else if (woven > 0.5) {
                        grain = texture2D(uCloth, lie * 11.0).rgb * uCabinGain.y;
                    }
                    if (!dial) {
                        figure.rgb *= mix(vec3(1.0), grain, grained);
                    }
                    if (!dial && !wheel) {
                        // The game's bodies have no bulkhead: past the dash the eye would see
                        // into the wheel arches. It is the dark of a footwell there.
                        // What the triangles say is dash, door or seat is not that gap.
                        float gap = kind > 0.5 ? 0.0 : 1.0;
                        figure.rgb *= 1.0 - gap * 0.93 * smoothstep(0.72, 0.8, local.x) * (1.0 - smoothstep(-0.52, -0.44, local.z));
                    }
                } else if (worn > 0.5 && min(min(east.a, west.a), min(north.a, south.a)) > 0.5) {
                    vec3 grey = vec3(0.299, 0.587, 0.114);
                    vec2 slope = vec2(dot(east.rgb - west.rgb, grey), dot(north.rgb - south.rgb, grey));
                    slope *= min(1.0, 0.25 / max(length(slope), 0.001));
                    n = normalize(n - (uRight * slope.x + uUp * slope.y) * 2.2);
                }
                if (uPainted < 0.5 && cabin < 0.5 && uRoots > 0.5 && worn > 2.5) {
                    // The game's skins are a few dozen dots across a whole shirt. What a shirt
                    // or a forearm is made of is laid over them: woven cloth, and skin with its
                    // pores, fixed to the body they are on so that they move as it moves.
                    vec3 rel = at;
                    float turned = 0.0, nearest = 0.8;
                    for (int i = 0; i < 16; i++) {
                        if (float(i) < uRoots) {
                            float off = distance(at.xy, uRoot[i].xy);
                            if (off < nearest) {
                                nearest = off;
                                rel = at - vec3(uRoot[i].xy, 0.0);
                                turned = uRoot[i].w;
                            }
                        }
                    }
                    float c = cos(turned), s = sin(turned);
                    vec3 local = vec3(rel.x * c + rel.y * s, rel.y * c - rel.x * s, rel.z);
                    vec3 faces = abs(vec3(n.x * c + n.y * s, n.y * c - n.x * s, n.z));
                    vec2 lie = faces.z > faces.x && faces.z > faces.y ? local.xy : faces.x > faces.y ? local.yz : local.xz;
                    vec3 paint = figure.rgb / max(figure.a, 0.001);
                    float most = max(paint.r, max(paint.g, paint.b)), least = min(paint.r, min(paint.g, paint.b));
                    // Skin, living or dead, is redder than it is blue and not strongly coloured.
                    float flesh = step(paint.b, paint.g + 0.02) * step(paint.g, paint.r + 0.02) * smoothstep(0.25, 0.4, most)
                            * (1.0 - smoothstep(0.35, 0.6, (most - least) / max(most, 0.001)));
                    vec2 fine = lie * mix(13.0, 9.0, flesh);
                    float e = 1.5 / 1024.0;
                    float here = mix(texture2D(uJersey, fine).g * uWearGain.x, texture2D(uVinyl, fine).g * uWearGain.y, flesh);
                    float east = mix(texture2D(uJersey, fine + vec2(e, 0.0)).g * uWearGain.x, texture2D(uVinyl, fine + vec2(e, 0.0)).g * uWearGain.y, flesh);
                    float north = mix(texture2D(uJersey, fine + vec2(0.0, e)).g * uWearGain.x, texture2D(uVinyl, fine + vec2(0.0, e)).g * uWearGain.y, flesh);
                    // Close by it is all there, and it thins out with distance, where it would
                    // only be noise.
                    float shown = 1.0 - smoothstep(4.0, 11.0, ahead * length(ray));
                    figure.rgb *= mix(1.0, here, 0.55 * shown);
                    vec3 slope = uRight * (east - here) + uUp * (north - here);
                    n = normalize(n - slope * (5.0 * shown));
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
                // The bonnet is out in the open, and is painted metal like the rest of the outside.
                float roofed = cabin * (1.0 - bonnet);
                sun = mix(sun, 0.22, roofed);
                // From a seat the game's vehicle is all round the eye. Its windows are painted
                // on its one body, as panes that show the sky: pale and nearly without colour,
                // where the paint, the trim and the seats are dark or coloured. Above the sills,
                // what is pale and grey is glass and is looked through; the pillars, the frames
                // of the windows and the roof stay.
                if (cabin > 0.5 && !builtCabin && (uHavePanes > 0.5 || at.z > uEye.z - 0.23) && !wheel && !dial && !bonnetSeen) {
                    // Taken over the pixel and its neighbours a few pixels off: a pane's edge
                    // against a pillar is then a clean line and not the ragged one where each
                    // pixel's shade of sky falls one side or other of the rule.
                    // The window is painted on a skin of few dots, and its edge from the seat
                    // is a staircase a finger wide: asked over a width greater than a stair, the
                    // edge is the straight line the stairs follow.
                    // A stair is as wide on screen as the skin is near, so the width asked over
                    // follows the distance: broad on the pillar by the eye, narrow on the far door.
                    float wide = uSize.y / 720.0 * clamp(0.6 / (ahead * length(ray)), 0.5, 2.2);
                    float panes = glass(uv) * 2.0;
                    for (int i = 0; i < 8; i++) {
                        float turn = 0.7854 * float(i);
                        vec2 off = vec2(cos(turn), sin(turn)) * wide / uSize;
                        panes += glass(uv + off * 5.0) + glass(uv + off.yx * vec2(-11.0, 11.0)) + glass(uv + off * 17.0);
                    }
                    // Told by its triangles, a pane's edge is already the line it should be.
                    // Told by its triangles, a pane's edge is the straight line it should be.
                    // What the triangles cannot tell is whether a pillar or a seat stands in
                    // front of a pane further off: there the colours round about are not glass's.
                    // The frame of a window is painted round the edge of its pane's triangles,
                    // so the glass is those triangles drawn in by a frame's width.
                    bool glazed = panes > 13.0;
                    if (uHavePanes > 0.5) {
                        glazed = paned(uv, ahead * length(ray)) > 0.5 && panes > 5.0;
                        if (!glazed && texture2D(uPanes, uv).r > 0.5 && panes > 5.0) {
                            // The frame itself: trim in the cabin's colour with a dark seal
                            // against the glass, in place of the few dots the game paints there.
                            figure = vec4(uDashColor * 0.55, 1.0);
                        }
                    }
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
                        vec3 to = (at - uEye) / CABIN;
                        vec2 side = vec2(-uAhead.y, uAhead.x);
                        float along = dot(to.xy, uAhead);
                        bool ends = abs(dot(n.xy, uAhead)) > abs(dot(n.xy, side));
                        gl_FragColor = pane(vec2(ends ? dot(to.xy, side) : along + 7.0, to.z), n, view, ends && along > 0.0 ? 1.0 : 0.0);
                        gl_FragDepth = 0.0;
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
                // to nothing on a dark night. The game's own light for the seat is what it gives a
                // closed room, and taken whole it left dark trim black at noon, so it counts for
                // under half.
                float skyGives = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 lightHere = fromSky * mix(vec3(1.0), mix(pow(uOwnAmbient, vec3(2.2)), vec3(1.0), 0.55) * (2.6 * skyGives), roofed) * mix(mix(pow(uFigureAmbient, 2.2) * 1.7, 0.8, bare) * skyGives, 1.0, roofed) + sunLight * (mix(2.1, 1.5, bare) * sun * facing);
                vec3 color = pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * lightHere;
                float housed = builtCabin && roofed > 0.5 ? 1.0 : 0.0;
                if (housed > 0.5) {
                    // Whether the sun reaches the vehicle at all is asked well outside it,
                    // towards the sun: nearer, the answer is the vehicle's own shadow.
                    vec3 over = (uNearMatrix * vec4(at + uLight * 3.5, 1.0)).xyz * 0.5 + 0.5;
                    float sunOut = 1.0;
                    if (inside(over)) {
                        sunOut = shadow2D(uShadowNear, over).r;
                    } else {
                        over = (uFarMatrix * vec4(at + uLight * 3.5, 1.0)).xyz * 0.5 + 0.5;
                        if (inside(over)) {
                            sunOut = shadow2D(uShadowFar, over).r;
                        }
                    }
                    vec3 to = (at - uEye) / CABIN;
                    vec2 side = vec2(-uAhead.y, uAhead.x);
                    vec3 place = uSeat + vec3(dot(to.xy, uAhead), dot(to.xy, side), to.z);
                    vec3 turned = vec3(dot(n.xy, uAhead), dot(n.xy, side), n.z);
                    float shade = 1.0;
                    if (wheel) {
                        // What stands close before a built part keeps the light off it.
                        vec3 out3 = vec3(dot(wheelFaces.xy, uAhead), dot(wheelFaces.xy, side), wheelFaces.z);
                        shade = 0.4;
                        for (int probe = 0; probe < 2; probe++) {
                            float off = probe == 0 ? 0.025 : 0.08;
                            shade += 0.3 * clamp(wheelAt(wheelPlace + out3 * off) / off, 0.0, 1.0);
                        }
                    }
                    color = cabinLit(place, turned, vec3(dot(view.xy, uAhead), dot(view.xy, side), view.z), pow(figure.rgb, vec3(2.2)), sheen, skyGives, sunOut, shade,
                            sunLight, vec3(dot(uLight.xy, uAhead), dot(uLight.xy, side), uLight.z));
                    if (uDome > 0.0) {
                        color += (pow(figure.rgb, vec3(2.2)) * 0.92 + 0.014) * domeLit(place, turned) * shade + domeLamp(place, wheel ? wheelPart : 0.0);
                    }
                }
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
                vec3 lamps = beams(at, n, 0.25) * (1.0 - cabin) + flashLight(at, n);
                color += pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * lamps;
                if (wheel && wheelPart > 6.5 && wheelPart < 7.5 && abs(wheelPlace.y - uMirror.y) < 0.112 && abs(wheelPlace.z - uMirror.z) < 0.029 && wheelPlace.x < uMirror.x) {
                    // The glass shows the world behind, left for right, and the sky where the
                    // world stops.
                    vec2 on = vec2(0.5 - (wheelPlace.y - uMirror.y) / 0.224, 0.5 + (wheelPlace.z - uMirror.z) / 0.058);
                    // The world behind is seen out of the back of the vehicle: through its
                    // back window, narrower at the top, between the pillars either side of
                    // it and over the shelf below it, all in the dark of the cabin.
                    vec2 across = vec2((on.x - 0.5) * 3.86 / (1.0 - 0.12 * on.y), on.y - 0.54);
                    vec2 past = abs(across) - vec2(1.22, 0.3);
                    float frame = length(max(past, 0.0)) + min(max(past.x, past.y), 0.0) - 0.12;
                    vec3 lining = pow(uDashColor, vec3(2.2)) * (0.012 + 0.05 * skyGives) * (1.0 - 0.6 * smoothstep(0.0, 0.05, frame));
                    color = mix(behindAt(on), lining, smoothstep(-0.015, 0.015, frame));
                }
                if (wheel && wheelPart > 7.5 && wheelPart < 8.5) {
                    vec3 housed = wheelPlace - uDoorMirror;
                    if (abs(housed.y) < 0.058 && abs(housed.z) < 0.033 && housed.x < 0.0) {
                        // The door mirror's glass: the road behind on its own side, out of
                        // the same picture as the mirror on the screen, and the vehicle's
                        // own flank down its inner edge.
                        float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                        vec2 on = vec2(0.5 - toward * 0.24 + housed.y / 0.058 * 0.21, 0.5 + housed.z / 0.033 * 0.42);
                        float flank = smoothstep(0.03, 0.036, housed.y * toward + (housed.z + 0.033) * 0.12);
                        color = mix(behindAt(on), pow(uPaint, vec3(2.2)) * fromSky * (0.1 + 0.7 * skyGives), flank);
                    }
                }
                if (dial && uInstruments > 0.0) {
                    // Lit from behind when the engine runs: the numerals and needles glow.
                    color += pow(figure.rgb, vec3(2.2)) * uGlowColor * (5.0 * uInstruments * dialGlow);
                }
                if (dashGlow > 0.0 && uInstruments > 0.0) {
                    color += pow(figure.rgb, vec3(2.2)) * uGlowColor * (3.0 * uInstruments * dashGlow);
                }
                if (roofed > 0.5 && uInstruments > 0.0) {
                    // With the engine running the instruments are lit, and in the dark their
                    // green light is on the wheel, the dash round them and the driver's knees.
                    vec3 dials = uEye + vec3(uAhead * 0.62, -0.46) * CABIN;
                    vec3 to = dials - at;
                    float far2 = dot(to, to);
                    float given = 0.022 / (0.009 + far2) * (0.25 + 0.75 * max(dot(n, to * inversesqrt(max(far2, 0.0001))), 0.0));
                    color += (pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * 0.6 + 0.12) * uGlowColor * (given * uInstruments);
                }
                // The trim of a cabin is cloth and dull plastic, whatever the body outside is.
                float lacquer = uPainted * (1.0 - roofed);
                float glancing = 0.04 + 0.96 * pow(clamp(1.0 - dot(n, view), 0.0, 1.0), 5.0);
                // The bonnet is seen from the seat at a glance, where lacquer mirrors most and
                // shows least of the paint under it.
                color *= 1.0 - 0.45 * glancing * bonnet;
                color += skyBack * mix(0.22 * glance, 0.1 + 0.6 * glance, lacquer) * (1.0 - 0.7 * roofed) * (1.0 - 0.6 * bonnet) * (1.0 - housed);
                // Cloth and skin scatter the sun and do not mirror it: with a hard glint a sleeve is a
                // length of rubber hose.
                color += sunLight * (pow(max(dot(back, uLight), 0.0), mix(48.0, 260.0, lacquer)) * mix(0.45 * (1.0 - 0.85 * bare), 3.0, lacquer) * sun) * (1.0 - housed);
                // The sky lies along it, and the sun in it is a broad soft light round the
                // small hard one.
                color += bonnet * (skyBack * (0.3 * glancing) + sunLight * (pow(max(dot(back, uLight), 0.0), 22.0) * 0.5 * sun));
                if (worn < 2.5) {
                    // A person the picture drew, by what each point of them is made of, in
                    // the light the world round them has: the game's light for the square
                    // they stand on, from the sky out of doors and mostly from above within,
                    // the sun where it reaches, and the sunlit ground's light thrown back up.
                    vec3 around = fromSky * (0.8 * skyGives);
                    if (stoodIn >= 0.0) {
                        around = pow(stoodIn, 2.2) * mix(vec3(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8))),
                                vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z), outdoors);
                        around += sunLight * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - max(n.z, 0.0)) * outdoors);
                    }
                    color = dressed(pow(figure.rgb, vec3(2.2)), n, view, strand, worn, gloss, low, uLight, sunLight * sun, around, lamps, skyBack);
                }

                float range = max(ahead * length(ray) - uRangeFrom, 0.0);
                float haze = max(smoothstep(uFogStart, uFogEnd, range), 1.0 - exp(-range * 0.0022));
                color = mix(color, atmosphere(normalize(ray)), haze);
                // Alpha says how much of a mirror the surface is, 1 for none: the last pass
                // traces what the bonnet's lacquer shows of the world over it.
                gl_FragColor = vec4(color, 1.0 - 0.5 * bonnet);
                // The viewer's own body and vehicle are drawn with a depth of their own. Here
                // it is put into the picture's depth, in the picture's own measure, so that
                // what is worked out from depth afterwards, the shade in corners and the
                // steadying over time, sees them and not the ground behind them.
                gl_FragDepth = uWriteNear > 0.0 ? 0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear) : gl_FragCoord.z;
            }
            """;

    static final String DEVELOP = """
            #version 120
            uniform sampler2D uScene;
            uniform sampler2D uBloom;
            uniform sampler2D uDepth;
            uniform vec3 uWithout;
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
            vec3 walked(vec2 uv, vec4 scene) {
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
                // Read off depth, the way a level surface faces is only roughly up, and with
                // the head rolled each column of the picture reads it a little differently:
                // the mirror is ruled with lines. Whatever faces more up than sideways is level.
                if (dot(facing, uUp) > 0.6) {
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
                        // A ray that goes far behind what the picture shows has not been
                        // stopped by it, as far as the picture can tell: over far water the
                        // depth is in coarse steps, and a ray counted as stopped at each step
                        // left the far shore's reflection full of holes.
                        if (behind < 0.6 + reach * 1.5) {
                            float edge = min(min(at.x, 1.0 - at.x), min(at.y, 1.0 - at.y));
                            vec4 found = texture2D(uScene, at);
                            // A ray that comes down on the water again has met nothing: it is
                            // the water's own coarse depth it has gone behind, and it goes on.
                            if (found.a >= 0.5) {
                                float sure = smoothstep(0.0, 0.08, edge);
                                return mix(scene.rgb, found.rgb, min(1.0, glance * 1.6) * mirror * sure);
                            }
                        } else {
                            // Near by, a ray that goes deep behind something has been stopped
                            // by it, and what it would have shown is not in the picture: the
                            // mirror keeps what the world gave it. Sent on to the sky, a puddle
                            // showed a house with squares of sky cut out of it.
                            if (p.z < 40.0) {
                                last = uv;
                            }
                            break;
                        }
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
                        // Just over the horizon the picture has sky in one pixel and the
                        // world's far edge in the next. There the water keeps the sky the
                        // world's shader worked out for it, which has no such edge in it.
                        float clear = smoothstep(0.02, 0.07, dot(thrown, uUp));
                        return mix(scene.rgb, texture2D(uScene, sky).rgb, min(1.0, glance * 1.6) * mirror * smoothstep(0.0, 0.08, edge) * clear);
                    }
                }
                return scene.rgb;
            }

            // Far water is a pixel or two a tile, and a ray walked from it lands behind
            // something in one pixel and misses in the next: the far shore is a band of
            // dashes. Out there the water shows the sky the world's shader gave it.
            vec3 mirrored(vec2 uv, vec4 scene) {
                float near = 1.0 - smoothstep(45.0, 80.0, placeAt(uv).z);
                return near > 0.0 ? mix(scene.rgb, walked(uv, scene), near) : scene.rgb;
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
                vec4 around = mix(texture2D(uOcclusion, uv), vec4(1.0), uWithout.x);
                vec4 here = texture2D(uScene, uv);
                vec3 c = mirrored(uv, here) * mix(1.0, around.a, 0.62);
                // The light lent by what stands near, taken on by the surface in its own colour.
                vec3 lent = around.rgb / max(1.0 - around.rgb, vec3(0.02));
                float own = max(here.r, max(here.g, here.b));
                c += lent * (here.rgb / max(own, 0.02)) * 0.22;
                if (uRayColor.r + uRayColor.g + uRayColor.b > 0.0) {
                    c += shafts(uv);
                }
                c += texture2D(uVolume, uv).rgb * (1.0 - uWithout.y);
                c += flare(uv);
                c += texture2D(uBloom, uv).rgb * (0.1 * (1.0 - uWithout.z));
                c = film(c * uExposure);
                c = pow(c, vec3(1.0 / 2.2));
                c = mix(c, c * c * (3.0 - 2.0 * c), 0.34);
                // A clear day photographs warm where the sun falls and cool in the shade, and
                // with its greens and blues full.
                float grey = dot(c, vec3(0.2126, 0.7152, 0.0722));
                c = mix(vec3(grey), c, 1.08);
                c *= mix(vec3(0.96, 0.99, 1.05), vec3(1.05, 1.01, 0.95), smoothstep(0.25, 0.8, grey));
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
    static final String STEADY = """
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

            // The picture kept from before, read at a place between its pixels. Read as the
            // mean of the four pixels round the place, each frame of a moving head blurs what
            // the last one kept, and the whole picture goes soft while the head moves and
            // sharp when it stops: with a tracked head, which never quite stops, it pulses.
            // Read through a curve fitted to the sixteen pixels round the place, it keeps
            // its edges. The sixteen are had from nine reads by letting the card mix pairs.
            vec3 sharply(vec2 at) {
                vec2 place = at * uSize;
                vec2 middle = floor(place - 0.5) + 0.5;
                vec2 f = place - middle;
                vec2 w0 = f * (-0.5 + f * (1.0 - 0.5 * f));
                vec2 w1 = 1.0 + f * f * (-2.5 + 1.5 * f);
                vec2 w2 = f * (0.5 + f * (2.0 - 1.5 * f));
                vec2 w3 = f * f * (-0.5 + 0.5 * f);
                vec2 w12 = w1 + w2;
                vec2 t0 = (middle - 1.0) / uSize, t3 = (middle + 2.0) / uSize, t12 = (middle + w2 / w12) / uSize;
                vec3 sum = texture2D(uBefore, vec2(t0.x, t0.y)).rgb * (w0.x * w0.y)
                        + texture2D(uBefore, vec2(t12.x, t0.y)).rgb * (w12.x * w0.y)
                        + texture2D(uBefore, vec2(t3.x, t0.y)).rgb * (w3.x * w0.y)
                        + texture2D(uBefore, vec2(t0.x, t12.y)).rgb * (w0.x * w12.y)
                        + texture2D(uBefore, vec2(t12.x, t12.y)).rgb * (w12.x * w12.y)
                        + texture2D(uBefore, vec2(t3.x, t12.y)).rgb * (w3.x * w12.y)
                        + texture2D(uBefore, vec2(t0.x, t3.y)).rgb * (w0.x * w3.y)
                        + texture2D(uBefore, vec2(t12.x, t3.y)).rgb * (w12.x * w3.y)
                        + texture2D(uBefore, vec2(t3.x, t3.y)).rgb * (w3.x * w3.y);
                return max(sum, vec3(0.0));
            }

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
                vec3 held = clamp(sharply(before), least, most);
                gl_FragColor = vec4(mix(now.rgb, held, uKept), now.a);
            }
            """;

    static final String COPY = """
            #version 120
            uniform sampler2D uSource;
            uniform vec2 uSize;
            void main() {
                gl_FragColor = texture2D(uSource, gl_FragCoord.xy / uSize);
            }
            """;

    /**
     * Smooths the stair-steps of edges, looking along each edge for how far to blend. The blend
     * is the FXAA pass of glsl-fxaa, used under its BSD licence: the notice is in
     * THIRD-PARTY-NOTICES.md. The sharpening after it is this project's own.
     */
    static final String SMOOTH = """
            #version 120
            uniform sampler2D uImage;
            uniform vec2 uSize;
            uniform vec2 uShown;
            uniform vec2 uOrigin;

            float luma(vec3 c) {
                return dot(c, vec3(0.299, 0.587, 0.114));
            }

            void main() {
                vec2 px = 1.0 / uSize;
                vec2 uv = (gl_FragCoord.xy - uOrigin) / uShown;
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
    static final String PANE_VERTEX = """
            #version 120
            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
            }
            """;
    static final String PANE_FRAGMENT = """
            #version 120
            uniform float uPane;
            void main() {
                gl_FragColor = vec4(uPane);
            }
            """;
    static final String PART_FRAGMENT = """
            #version 120
            uniform float uKind;
            void main() {
                gl_FragColor = vec4(uKind / 8.0, 1.0, 0.0, 1.0);
            }
            """;
}
