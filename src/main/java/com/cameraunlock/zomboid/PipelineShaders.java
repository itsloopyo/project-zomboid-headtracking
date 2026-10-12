package com.cameraunlock.zomboid;

import java.util.Locale;

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

    /**
     * Where the built world ends, for every shader that draws it or what lies beyond it: the
     * world, the figures and the far land (FarLand).
     */
    static final String ENDS = """
            uniform float uFogStart;
            uniform float uFogEnd;
            uniform vec3 uLoaded;
            // 0 where nothing is drawn beyond the built world, which then fades into the hills.
            // Otherwise the far land lies behind it, and this is 1 more than a number that is
            // another each frame the frames are mixed, and 0 each frame they are not.
            uniform float uBeyond;

            // How far gone the built world is at a place this far from the eye along the
            // ground: 0 where it is whole, 1 where nothing of it is left. It ends at the sight
            // the graphics mode has, and where the loaded map does, which at the wheel of a car
            // at speed is further off ahead than behind: the game loads ahead of it.
            float gone(vec2 fromEye) {
                return max(smoothstep(uFogStart, uFogEnd, length(fromEye)), smoothstep(uLoaded.z - (uFogEnd - uFogStart), uLoaded.z, length(fromEye - uLoaded.xy)));
            }

            // A number from 0 to 1 for the pixel, as many of them under any number as that
            // number is of 1, over any patch of the picture.
            float sieve() {
                return fract(52.9829189 * fract(dot(gl_FragCoord.xy + 5.588238 * (uBeyond - 1.0), vec2(0.06711056, 0.00583715))));
            }

            // With the far land behind it the built world does not fade: it thins out, pixel by
            // pixel, over the further half of where it ends, and the far land has come in under
            // it, the same way, over the nearer half. So there is never a pixel with neither,
            // and the far land, which does not lie quite where the built ground does, is not
            // seen to cut across it. Whether this pixel of the built world is one left out:
            bool thinnedOut(vec2 fromEye) {
                return uBeyond > 0.5 && sieve() < smoothstep(0.5, 1.0, gone(fromEye));
            }

            // And whether this pixel of the far land is one that has not yet come in. Its
            // buildings come in as the built world's go, pixel for pixel: come in sooner,
            // a block and the building it stands for were both there, one through the other.
            bool notYetIn(vec2 fromEye, bool built) {
                return sieve() >= (built ? smoothstep(0.5, 1.0, gone(fromEye)) : smoothstep(0.0, 0.5, gone(fromEye)));
            }
            """;

    /**
     * What autumn does to a deciduous tree's leaves: the colour its kind turns, one of
     * Flora.HUES, and then brown as they fall. Turned is 0 green to 1 brown (Seasons.turned).
     * Only leaves the game paints green take the kind's colour: where the game's sprite has
     * the tree turned already, its own colour stays, and goes brown as well.
     */
    static final String AUTUMN = String.format(Locale.ROOT, """
            // The colour a crown's leaves turn, and how far they have, 0 to 1, as its sprigs and inside carry them in their tree's square: see Flora.AUTUMN_CODES.
            vec2 autumnOf(vec2 root) {
                if (root.x < 0.0) {
                    return vec2(0.0);
                }
                float code = floor((root.y - floor(root.y + 0.5)) * %1$.1f + 0.5) + %2$d.0;
                float hue = floor((code + 0.5) / %3$d.0);
                return vec2(hue, (code - hue * %3$d.0) / %4$d.0);
            }

            vec3 autumnHue(float hue) {
                return hue < 0.5 ? vec3(0.86, 0.70, 0.16) : hue < 1.5 ? vec3(0.88, 0.52, 0.12) : hue < 2.5 ? vec3(0.86, 0.34, 0.08)
                        : hue < 3.5 ? vec3(0.76, 0.14, 0.07) : vec3(0.52, 0.11, 0.15);
            }

            vec3 autumnal(vec3 painted, float hue, float turned) {
                if (turned <= 0.0) {
                    return painted;
                }
                vec3 weigh = vec3(0.299, 0.587, 0.114);
                float light = dot(painted, weigh);
                vec3 to = autumnHue(hue);
                vec3 brown = vec3(0.42, 0.28, 0.15);
                float green = clamp((painted.g - max(painted.r, painted.b)) / max(painted.g, 0.05) * 4.0, 0.0, 1.0);
                vec3 coloured = mix(painted, to * (light / dot(to, weigh)), green * smoothstep(0.0, 0.5, turned));
                return mix(coloured, brown * (0.85 * light / dot(brown, weigh)), smoothstep(0.6, 1.0, turned));
            }
            """, Flora.AUTUMN_SPACING, Flora.AUTUMN_CODES / 2, Flora.TURNS, Flora.TURNS - 1);

    /** Noise that lies on the ground, the same wherever it is asked for: the clouds' shadows cross the built world and the far land as one. */
    static final String GROUND_NOISE = """
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
            """;

    /** The headlights that are on and the torch the viewer carries, for every shader they light: the world, the figures, the air and the rain. */
    private static final String BEAMS = """
            uniform vec4 uBeamAt[2];
            uniform vec3 uBeamWay[2];
            uniform sampler2DShadow uBeamShadow;
            uniform mat4 uBeamMatrix;
            // Where the viewer's torch is held and how far it reaches, 0 when none throws a
            // beam, the way it points, and the scene as the torch sees it.
            uniform vec4 uTorchAt;
            uniform vec3 uTorchWay;
            uniform sampler2DShadow uTorchShadow;
            uniform mat4 uTorchMatrix;
            uniform float uTorchTexel;

            // How bright the torch makes what the middle of its beam is on at arm's length.
            const float TORCH_GIVES = 1.6;

            // What the torch gives p: a hot middle and a dim spill round it, less with the
            // square of the distance, and nothing past what stands in the beam. The game
            // lights whole squares with a torch, by where they lie on the ground of the
            // level the character is on: none of that is in the picture (see TorchLess).
            float torchAt(vec3 p, out vec3 dir) {
                vec3 to = p - uTorchAt.xyz;
                float far2 = dot(to, to);
                dir = to * inversesqrt(max(far2, 0.0001));
                float within = dot(dir, uTorchWay);
                float far = sqrt(far2);
                // The torch is in the viewer's hand, and what the same arms hold beside it,
                // a weapon, the other hand, is not in the beam, whichever way it points, but
                // in a little that comes back off the room: the beam itself begins past them.
                float cone = mix(0.12, 0.14 * smoothstep(0.55, 0.8, within) + 0.86 * smoothstep(0.8, 0.965, within), smoothstep(0.3, 0.7, far));
                float given = cone / (1.0 + 0.035 * far2) * (1.0 - smoothstep(uTorchAt.w, uTorchAt.w + 3.0, far));
                if (given <= 0.0) {
                    return 0.0;
                }
                // Asked a little way back towards the torch: a surface is not in its own shadow.
                // The map takes in more than the cone, so what the cone lights is in front of
                // it, and what is beside the hand is not asked of it at all.
                vec4 c = uTorchMatrix * vec4(p - dir * 0.06, 1.0);
                if (far < 0.7 || c.w <= 0.0) {
                    return given;
                }
                vec3 s = c.xyz / c.w * 0.5 + 0.5;
                float e = 1.5 * uTorchTexel;
                return given * 0.25 * (shadow2D(uTorchShadow, s + vec3(e, e, 0.0)).r + shadow2D(uTorchShadow, s + vec3(-e, e, 0.0)).r
                        + shadow2D(uTorchShadow, s + vec3(e, -e, 0.0)).r + shadow2D(uTorchShadow, s + vec3(-e, -e, 0.0)).r);
            }

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

            // What they give the air at p, for the eye to see as a beam. Dipped lamps throw
            // nothing above their own height, so the lit air lies low over the road.
            float beamInAir(int i, vec3 p) {
                vec3 dir;
                float given = beamAt(i, p, dir);
                return given * (1.0 - smoothstep(-0.03, 0.04, dir.z - uBeamWay[i].z));
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
                if (uTorchAt.w > 0.0) {
                    vec3 dir;
                    float given = torchAt(p, dir);
                    sum += vec3(1.0, 0.93, 0.8) * (TORCH_GIVES * given * mix(0.12 + 0.88 * max(dot(n, -dir), 0.0), 0.7, leafy));
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

    /**
     * The wind in grass. A blade is stiff at its root and limp at its tip, so it is pushed
     * over by the square of its height near the ground and evenly above that, and sinks as it
     * goes over, since it is no longer than it was. The wind does not blow evenly: gusts the
     * size of a yard drift over the ground with the grass lying over under each and standing
     * again behind it, smaller swells ride on those, ripples run through both more slowly,
     * and every blade shivers at a rate of its own.
     *
     * None of it is worked out from the way the wind blows. The gusts are a pattern laid over
     * the map, and uGust is how far the wind has carried it, which only ever moves on: see
     * Wind, which has the numbers the pattern repeats over. The push, uWind, eases after the
     * game's wind. So a blade moves no faster than the air does, whatever the game's wind does.
     */
    private static final String GRASS_WIND = """
            uniform vec3 uGust;
            // How much snow lies on what grows, 0 to 1: uSnowLying, and none in Low, which draws
            // what grows as the game does, bare whatever lies on the ground.
            uniform float uPlantSnow;

            // Smooth noise that repeats every so many of its cells.
            float gustAt(vec2 q, float cells) {
                vec2 cell = floor(q), part = fract(q);
                part = part * part * (3.0 - 2.0 * part);
                vec2 low = mod(cell, cells), high = mod(cell + 1.0, cells);
                float a = fract(sin(dot(low, vec2(12.9898, 78.233))) * 43758.5453);
                float b = fract(sin(dot(vec2(high.x, low.y), vec2(12.9898, 78.233))) * 43758.5453);
                float c = fract(sin(dot(vec2(low.x, high.y), vec2(12.9898, 78.233))) * 43758.5453);
                float d = fract(sin(dot(high, vec2(12.9898, 78.233))) * 43758.5453);
                return mix(mix(a, b, part.x), mix(c, d, part.x), part.y);
            }

            vec3 swayed(vec3 at, float high, float seed) {
                float blows = length(uWind.xy);
                if (blows < 0.001 || high <= 0.0) {
                    return at;
                }
                float strength = min(blows, 1.5);
                vec2 way = uWind.xy / blows;
                vec2 beside = vec2(-way.y, way.x);
                vec2 carried = at.xy - uGust.xy;
                float gust = smoothstep(0.2, 0.85, gustAt(carried * 0.125, 8.0));
                float swell = gustAt(carried * 0.5 + 3.7, 32.0);
                float ripple = gustAt((at.xy - 0.25 * uGust.xy) * 2.0, 128.0) - 0.5;
                // A whole number of shivers in the 160 seconds the clock runs before it starts
                // again: one every ten or twenty seconds. Faster than that and a lawn looks restless.
                float shiver = sin(uGust.z * 0.03926991 * (8.0 + floor(seed * 12.0)) + seed * 37.0);
                float limp = high * high / (high + 0.12);
                float push = strength * (0.1 + 0.5 * gust + 0.22 * swell + 0.16 * ripple) * 0.75 * limp;
                at.xy += way * push + beside * (shiver * 0.03 * strength * limp);
                at.z -= min(push * push / (2.0 * max(high, 0.02)), 0.6 * high);
                return at;
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
                // A bough with snow on it is heavier and swings less far.
                return vec4(at.xy + uWind.xy * (take * lean * (1.0 - 0.35 * uPlantSnow)), at.zw);
            }

            // What a vertex of an overhead wire carries where a texel's place would be: a way
            // across the wire on the map, the same for the whole of a span, whose length is
            // which corner of the wire's two strips the vertex is (1 and 2 either side of the
            // level strip, 3 and 4 the top and the bottom of the upright one) and, in what is
            // left over a tenth, where in its swing the span is. See WorldScene.cable.
            vec3 wireOff(vec2 carried, out vec2 across, out float swing) {
                float size = length(carried);
                float corner = floor(size);
                across = carried / size;
                swing = (size - corner - 0.1) * 7.853982;
                return corner < 1.5 ? vec3(across, 0.0) : corner < 2.5 ? vec3(-across, 0.0) : vec3(0.0, 0.0, corner < 3.5 ? 1.0 : -1.0);
            }

            // Where the wind has swung a vertex of an overhead wire to. A wire is held at
            // its two insulators and hangs between them, and it swings as a whole about the
            // line from one to the other, as a pendulum does: so a vertex is carried round
            // that line at the end of an arm as long as the wire hangs below it there,
            // nothing at an insulator and most in the middle of the span, and rises as it
            // goes. The wind leans the wire over by how squarely it blows across it, and it
            // swings to and fro about that lean, a heavy cable taking five seconds over one
            // swing, with every third swing or so a smaller one. The clock, uWind.z, starts
            // again after an hour, in which both rates come to a whole number of swings. At
            // the hardest the plants are ever pushed, 1.5, a wire leans 0.12 of a radian and
            // swings 0.105 either side of that: where it hangs 0.9 of a tile, the most it
            // does, two wires swinging against each other close by 0.19 of a tile, and
            // those of one run are no nearer than 0.36.
            vec3 swung(vec3 at, float hangs, vec2 across, float swing) {
                float blows = length(uWind.xy);
                if (hangs <= 0.0 || blows < 0.001) {
                    return at;
                }
                float square = dot(uWind.xy, across) / blows;
                float beat = sin(uWind.z * 1.2566371 + swing) * (0.65 + 0.35 * sin(uWind.z * 0.4799655 + 1.7 * swing));
                float turn = min(blows, 1.5) * (0.08 * square + 0.07 * (0.4 + 0.6 * abs(square)) * beat);
                return at + hangs * vec3(across * sin(turn), 1.0 - cos(turn));
            }
            """;

    /** How much of the sky a place can see, for the world's fragment shader and for the vertex shaders that stand grass in lying snow. It uses uHeights and uRoofMatrix, which the shader it is joined into declares. */
    static final String SKY_SEEN = """
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
                        float above = (c.z - texture2D(uHeights, c.xy).r) * 80.0 - 0.2;
                        highest = max(highest, above / d);
                    }
                    hidden += clamp(highest * 0.6, 0.0, 1.0);
                }
                return 1.0 - hidden / 6.0 * 0.75;
            }

            // How much of the sky's light gets to a place that far from the eye. Under trees it
            // is thinned and green, not gone. The sky is asked about only near the eye, and
            // counts for less towards where it stops being asked.
            float skyOverAt(vec3 at, float range) {
                return range < 16.0 ? mix(max(skySeen(at), 0.38), 1.0, smoothstep(10.0, 16.0, range)) : 1.0;
            }
            """;

    /**
     * Where a vertex of the frame's own arrays stands once its card is turned to the camera,
     * and how far off its plane it lies. Its second texture coordinate says how far along its
     * card it is or how many layers it lies off, how many layers of cards it stands towards the
     * camera, and which of those it is: see SpriteGeometry.VERTEX_FLOATS.
     *
     * A layer is as thick as the first person camera's depth buffer needs to tell two
     * surfaces apart that far from it, which grows by the square of the distance (uLayer: the
     * least, and how much more for each tile squared; then how far a layer of cards stands
     * apart besides). The distance is from uLayerFrom, the eye the scene is built round, the
     * player's own, in every pass that draws the vertex: the view, the mirror and a scope,
     * which look from there or beside it; the sun's and the lamps' pictures, so that what
     * casts a shadow is where the view has it; and the picture seen from outside and on the
     * flight down to the eye. That camera is thousands of tiles off behind a near plane of
     * its own, tells surfaces apart far finer than the eye's does, and is not what the square
     * of a distance stands for: measured from it, a layer would be hundreds of tiles thick.
     * A card turns to uFaced, the camera the picture is drawn from. What lies off its plane
     * is moved in lies, which the caller adds once the eye's place has been taken off: added
     * to a place on the map, a move this small is rounded to the map's millimetre.
     */
    static final String FACED = """
            uniform vec2 uFaced;
            uniform vec2 uAcross;
            uniform vec3 uLayer;
            uniform vec3 uLayerFrom;
            vec4 faced(vec4 at, out vec2 right, out vec3 lies) {
                vec2 from = at.xy - uFaced;
                float off = length(from);
                // On the camera's own square there is no way to it, and a card lies across the view.
                right = off > 0.5 ? vec2(-from.y, from.x) / off : uAcross;
                vec3 away = at.xyz - uLayerFrom;
                float layer = uLayer.x + uLayer.y * dot(away, away);
                float card = uLayer.z + 4.0 * layer;
                float kind = gl_MultiTexCoord1.z;
                vec2 stands = at.xy - from / max(off, 0.5) * (gl_MultiTexCoord1.y * card);
                lies = vec3(0.0);
                if (kind > 2.5) {
                    lies = (kind < 3.5 ? vec3(1.0, 0.0, 0.0) : kind < 4.5 ? vec3(0.0, 1.0, 0.0) : vec3(0.0, 0.0, 1.0)) * (gl_MultiTexCoord1.x * layer);
                } else if (kind > 1.5) {
                    // A card that does not turn, standing clear of its own plane on the camera's side.
                    vec2 square = normalize(gl_Normal.xy);
                    stands -= square * (dot(square, from) > 0.0 ? 1.0 : -1.0) * (gl_MultiTexCoord1.x * card);
                } else {
                    stands += right * gl_MultiTexCoord1.x;
                }
                return vec4(stands, at.zw);
            }
            """;

    /** How the world's fragment shader says it draws colour, and what Pipeline puts in its place for the program that draws depth alone. */
    static final String DEPTH_ONLY_ASKED = "const bool depthOnly = false;";
    static final String DEPTH_ONLY_ALWAYS = "const bool depthOnly = true;";

    static final String WORLD_VERTEX = """
            #version 120
            invariant gl_Position;
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;
            // For a floor of boards the picture lays: how many to a tile, and 1 where they run along y. 0 for anything else.
            varying float vBoards;
            varying float vLie;
            varying float vHung;
            // The square of the tree the vertex is part of, below 0 for none: see SpriteGeometry.VERTEX_FLOATS.
            varying vec2 vRoot;
            uniform float uCutAbove;
            varying vec3 vPlace;
            varying vec4 vBlade;
            uniform vec3 uWind;
            uniform float uLaid;
            uniform float uSnowLying;
            uniform vec3 uEye;
            uniform float uPixel;
            uniform float uRangeFrom;
            uniform sampler2D uHeights;
            uniform mat4 uRoofMatrix;
            """ + GRASS_WIND + MODEL_STANDS + FACED + SKY_SEEN + SnowShader.COVER + SnowShader.STOOD + """
            uniform vec3 uLit;
            uniform vec3 uLitAlong;
            uniform vec3 uLitDown;
            uniform vec3 uLitAcross;
            uniform vec3 uSurfaces;
            uniform vec4 uDye;
            uniform vec4 uPicture;

            // Whether the surface's bit is set in a whole number of them. The half is there for a
            // power of two worked out a hair too large, which would otherwise divide the
            // surface's own bit to just under 1 and lose it.
            bool has(float bits, float surface) {
                return mod(floor((bits + 0.5) / exp2(surface)), 2.0) > 0.5;
            }
            void main() {
                vec4 stands = gl_Vertex;
                vec3 looks = gl_Normal;
                vec3 lies = vec3(0.0);
                // A strip of grass is no card: where a card's vertex carries how it stands off its plane, a strip's carries the way to its root.
                bool strip = uModel < 0.5 && mod(gl_Color.a, 32.0) > 23.5;
                if (uModel < 0.5 && !strip) {
                    vec2 right;
                    stands = faced(gl_Vertex, right, lies);
                    if (gl_MultiTexCoord1.z > 0.5 && gl_MultiTexCoord1.z < 1.5) {
                        looks = normalize(vec3(right.y, -right.x, 0.9)) * length(gl_Normal);
                    }
                }
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
                    // So does the glass of a set that is on.
                    if (dyed && (surface > 15.5 && surface < 16.5 || surface > 9.5 && surface < 10.5)) {
                        vUv += gl_Vertex.x * uPicture.xy + gl_Vertex.z * uPicture.zw;
                    }
                    vColor = vec4(gl_MultiTexCoord0.y * (uLit + share.x * uLitAlong + share.y * uLitDown + share.x * share.y * uLitAcross),
                            uSurfaces.x + 32.0 * ((dyed || also ? 34.0 : 16.0) + surface));
                }
                // A strip of grass has no facing. What its vertex carries in a facing's place is
                // taken apart here: see SpriteGeometry.addStrip.
                vBlade = vec4(0.0);
                float high = 0.0;
                // How deep the snow lies that a strip of grass stands in: see snowStood.
                float deep = 0.0;
                if (strip) {
                    float edge = floor((gl_Normal.x + 3.0) * 0.5) - 1.0;
                    float form = floor(gl_Normal.y * 0.5);
                    float level = floor(gl_Normal.z);
                    // Not asked for the part of what is laid that grass is left out of: see uLaid below.
                    deep = uLaid > 2.5 || uPlantSnow <= 0.0 ? 0.0 : snowStood(vec3(gl_Vertex.xy + gl_MultiTexCoord1.xy, gl_Vertex.z - (gl_Normal.z - level)), mod(gl_Color.a, 32.0) > 24.5);
                    // Only what stands out of the snow blows about and is trodden down.
                    high = max(gl_Normal.z - level - deep, 0.0);
                    float along = gl_Normal.y - 2.0 * form;
                    // How far to one side the vertex is, as a share of the strip's full width:
                    // a strip that narrows or widens is then cut and shaded along true lines
                    // from foot to tip, where the edge alone would bend them at every corner.
                    float breadth = form > 6.5 ? 0.3 + 0.7 * along : form < 2.5 ? 1.0 - along * along : 1.0;
                    vBlade = vec4(edge * breadth, along, gl_Normal.x - 2.0 * edge, form + 0.05 + 0.06 * level);
                    looks = vec3(0.0, 0.0, 1.0);
                }
                vNormal = looks;
                vHung = uModel > 0.5 && uFoot.z > uCutAbove - 0.45 ? 1.0 : 0.0;
                vRoot = uModel > 0.5 ? vec2(-1.0) : gl_MultiTexCoord2.xy;
                // What is laid on the ground does not blow about: there the normal's length says
                // how much of the layer shows at the vertex.
                // Nor does water: there the length says how far the vertex is from the bank.
                // The colour's alpha carries what kind of thing the vertex is, and in the
                // thirty-twos above that what it is made of.
                vMaterial = floor((vColor.a + 0.5) / 32.0);
                vKind = vColor.a - 32.0 * vMaterial;
                // WorldScene.boardsOf: wood all the same to everything else that asks what a surface is made of.
                // A model's materials are numbered from 16 too (Models.FIRST_MATERIAL): only the world's faces are boards.
                bool boarded = uModel < 0.5 && vMaterial > 15.5 && vMaterial < 31.5;
                vBoards = boarded ? 3.0 + floor((vMaterial - 16.0) / 2.0) : 0.0;
                vLie = boarded ? mod(vMaterial - 16.0, 2.0) : 0.0;
                vMaterial = boarded ? 3.0 : vMaterial;
                // What is laid may be drawn in two parts, its grass and the rest: see
                // Pipeline.drawWorld. A vertex of the part not being drawn is in no picture.
                if (uLaid > 1.5 && (vKind > 7.5) == (uLaid > 2.5)) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                // What stands only to throw a crown's shadow is in no picture but the sun's.
                if (vMaterial > 79.5 && vMaterial < 80.5) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                // Nor what lies under trees or clings to a bole, though a fern, a weed and a
                // flower growing out of the ground do.
                // Nor paint on a road, whose faces lie tilted on the land with a facing longer than one,
                // nor blood, whose facing is as long as it is see-through.
                bool still = uLaid > 0.5 && vKind < 7.5 && (vMaterial < 59.5 || vMaterial > 82.5 && vMaterial < 85.5) || vKind > 3.5 && vKind < 7.5;
                // An overhead wire: the length of its facing says how far it hangs below the line between its insulators, see swung.
                // What hangs on a cord, a bulb of a string of lights, carries the same and swings with its cord. One that
                // burns is of the stuff of a lamp and hangs below a line, which no lamp that stands does, and one that is
                // out is of a stuff of its own. See Lights.round.
                bool hung = uModel < 0.5 && !still && (vMaterial > 81.5 && vMaterial < 82.5 || vMaterial > 13.5 && vMaterial < 14.5 && dot(gl_Normal, gl_Normal) > 1.004);
                bool wire = uModel < 0.5 && vMaterial > 11.5 && vMaterial < 12.5 || hung;
                vec4 at = strip ? vec4(snowLaden(swayed(snowedIn(stands.xyz, deep), high, vBlade.z), high, deep, vBlade.z), 1.0) : still || wire ? stands : blown(stands, looks);
                if (strip) {
                    // Grass parts round whoever stands in it. The eye is over the viewer's
                    // feet: what grows within a pace of them is pressed outward and down, the
                    // more the nearer, so a look down shows the grass trodden where one stands
                    // and springing back behind as one walks on.
                    vec2 from = at.xy - uEye.xy;
                    float off = length(from);
                    float under = uEye.z - at.z;
                    float trodden = (1.0 - smoothstep(0.12, 0.6, off)) * step(0.8, under) * step(under, 2.3);
                    at.xy += from / max(off, 0.02) * (trodden * high * 1.1);
                    at.z -= trodden * high * 0.65;
                }
                // Snow lies deeper than a kerb is high and buries its face in a bank that runs
                // out into the street, broad in one place and narrow in the next as the wind
                // left it: the foot of the face goes out across the street as far as the bank
                // runs, and the face becomes the bank's slope. The top stays on the edge of
                // the pavement. Its shadow is the one its top edge throws either way.
                if (uModel < 0.5 && vMaterial > 63.5 && vMaterial < 64.5 && gl_MultiTexCoord0.y < 0.5) {
                    float banked = snowStood(at.xyz, vKind > 0.5);
                    if (banked > 0.0) {
                        at.xy += normalize(gl_Normal.xy) * (banked * (0.8 + 1.2 * snowLayer(mod(at.xy, 60.0), 1.5, 21.0).x));
                    }
                }
                vCover = strip ? 1.0 - vBlade.y : 2.0 - length(looks);
                // An overhead wire is finer than a pixel from a street away, and what is finer
                // than a pixel is drawn only where it crosses the middle of one: a row of
                // dashes that crawl as the head moves. A wire is two strips, one upright and
                // one level, and takes no texel: where a texel's place would be, its vertex
                // carries which corner of which strip it is, see wireOff. Here it is stood
                // off the middle as far as makes the wire a pixel thick from where the eye
                // is. 0.015 is half of WorldScene.WIRE_THICK.
                if (wire) {
                    vec2 across;
                    float swing;
                    vec3 off = wireOff(gl_MultiTexCoord0.xy, across, swing);
                    at.xyz = swung(at.xyz, length(gl_Normal) - 1.0, across, swing);
                    if (!hung) {
                        at.xyz += off * max(0.0, 0.6 * uPixel * length(at.xyz - uEye) - 0.015);
                    }
                    vUv = vec2(0.0);
                    vNormal = normalize(looks);
                    vCover = 1.0;
                }
                vWorld = at.xyz + lies;
                // The map is thousands of tiles across and a number here holds seven digits, so
                // a place on it is known to a millimetre and no finer. Multiplied as it stands
                // by a matrix that takes the eye's own place off again, a vertex's depth came out
                // wrong by centimetres a few paces off, differently at every vertex: a picture
                // was drawn through the wall it hangs on in torn patches, and a clock's face
                // through its hands. The eye's place is taken off first, which is exact, and the
                // matrix only turns what is left. A model's vertex never is put on the map at all.
                vec3 seen = uModel > 0.5 ? fromEye : at.xyz - uEye + lies;
                // The same place counted from a corner of the map near the eye, for what is
                // drawn finer than a millimetre: made from vWorld, a board's grain is steps.
                // The corner moves sixty tiles at a time, which is a whole number of boards.
                vPlace = seen + mod(uEye, 60.0);
                gl_Position = gl_ModelViewProjectionMatrix * vec4(seen, 1.0);
            }
            """;

    /**
     * The world's fragment shader up to its main. Pipeline joins WORLD_FRAGMENT_RELIEF, SnowShader.GLSL and
     * WORLD_FRAGMENT_MAIN on after it: as one constant the shader is longer than a class file can hold.
     *
     * facHash is hash12 of David Hoskins's Hash without Sine, used under its MIT licence: the
     * notice is in THIRD-PARTY-NOTICES.md.
     */
    static final String WORLD_FRAGMENT_PARTS = "#version 120\n#extension GL_EXT_texture_array : require\n#extension GL_ARB_shader_texture_lod : require\n"
            + "#define CUT_FLOATS " + Cutaways.FLOATS + "\n#define CUT_FLOATS_A_ROW " + Cutaways.FLOATS_A_ROW + "\n#define CUT_SIDE " + Cutaways.SIDE + ".0\n"
            + CanopyWindow.GLSL
            + Sky.ATMOSPHERE + ENDS + AUTUMN + """
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
            // For sunAsked: how much height a texel of the near sun map and of the far one spans under a
            // level sheet, which is a texel times the cosine of the sun's elevation; how many texels of its
            // slope every map holds a surface behind itself; twice the depth of the map from above, in tiles;
            // and how far the game's pitched roofs rise over a tile.
            uniform vec2 uSunTexelUp;
            uniform float uSlopeOffset;
            uniform float uRoofDeep;
            uniform float uRoofPitch;
            uniform vec3 uEye;
            uniform float uRangeFrom;
            uniform float uCutAbove;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform float uSeconds;
            // For the glass of a set that is on: where across and down its picture the middle of the glass
            // is and how much of the picture the glass shows, and 1 for a tube with no colour, 2 for snow.
            uniform vec4 uTube;
            uniform float uCut;
            uniform vec2 uTexel;
            uniform vec4 uTint;
            uniform vec3 uJoint;
            uniform float uLaid;
            const bool depthOnly = false;
            // What the depth only program writes where it draws: the colour of a thing's outline
            // while Pipeline.drawOutlined draws the thing again. Nothing else reads its colour.
            uniform vec4 uOutline;
            uniform float uWet;
            // How much of the ground snow lies on, 0 to 1.
            uniform float uSnowLying;
            // How much snow lies on what grows: see GRASS_WIND.
            uniform float uPlantSnow;
            // 1 where a wet road, a polished floor and glass are mirrors, which the last pass traces: 0 outside the High graphics mode.
            uniform float uMirrors;
            // For the picture drawn from under the water (WaterMirror): the plane nothing under
            // which is in it, as the way it faces and how far along that it is. All 0 in any other picture.
            uniform vec4 uUnder;
            // That picture, for the eye's own to show where it draws water, and the plane the
            // water lies in: the way it faces and how far over it the eye is. All 0 with no such picture.
            uniform sampler2D uWaterPicture;
            uniform vec4 uWater;
            // How much of that picture's width and height this one takes up (one over WaterMirror.MARGIN).
            uniform float uWaterShare;
            // The part of that picture that was drawn, as its least and greatest x and y from -1 to 1.
            uniform vec4 uWaterRect;
            // How many of that picture's pixels there are to one of this one's, up and down.
            uniform float uWaterDensity;
            // How far off what water shows is taken to stand, in tiles, where a ripple or a rise of the water throws the eye's line aside: WaterMirror.THROW.
            uniform float uWaterThrow;
            // How rough the wind has the water: 1 in a light breeze.
            uniform float uRuffle;
            // 1 where water's ripples are all drawn whatever a pixel takes in and fade from 14 to 55
            // tiles off, as a wet road's do, and are as high in any wind: in Low.
            uniform float uPlainWater;
            // How much of its distance from the eye a pixel of this picture takes in, up and down.
            uniform float uPixel;
            varying vec2 vUv;
            varying vec4 vColor;
            varying vec3 vNormal;
            varying vec3 vWorld;
            varying float vCover;
            varying float vKind;
            varying float vMaterial;
            // For a floor of boards the picture lays: how many to a tile, and 1 where they run along y. 0 for anything else.
            varying float vBoards;
            varying float vLie;
            varying float vHung;
            varying vec2 vRoot;
            uniform vec4 uCutBox;
            uniform vec3 uCutNear;
            uniform float uLid;
            // The walls the game cuts away, for the picture drawn as the game's own view: see Cutaways.
            // uCutMarked is the block's first square, and the height its walls are cut up to, or 0 with no block.
            uniform float uCutRows[CUT_FLOATS];
            uniform vec3 uCutMarked;
            const float CUT_DIGIT[12] = float[12](1.0, 4.0, 16.0, 64.0, 256.0, 1024.0, 4096.0, 16384.0, 65536.0, 262144.0, 1048576.0, 4194304.0);

            // The game's marks on a square: 1 with its north wall cut away, 2 with its west wall, 3 with both.
            float cutMarks(vec2 square) {
                vec2 at = square - uCutMarked.xy;
                if (at.x < 0.0 || at.y < 0.0 || at.x >= CUT_SIDE || at.y >= CUT_SIDE) {
                    return 0.0;
                }
                float part = floor(at.x / 12.0);
                float digits = floor(uCutRows[int(at.y) * CUT_FLOATS_A_ROW + int(part)] / CUT_DIGIT[int(at.x - part * 12.0)]);
                return digits - 4.0 * floor(digits / 4.0);
            }

            // Whether a point is in a wall the game has cut away, or hangs on one. A square's north wall
            // runs along its north edge and its west wall along its west edge. A wall is taken by the way
            // it faces, or the end of one that stands would go with the cut wall it meets. Something
            // hung has faces every way, and goes by the wall it is nearest. A wall's own ends run a
            // little past its square, into the corner. A wall's top faces up whichever wall it is,
            // and leans along its wall where the wall's two ends stand at different heights: it is
            // cut with any cut wall whose thickness it lies in, so the top of a straight run is cut
            // across the joints of its squares, and a counter's top against the wall keeps all but
            // the wall's own thickness.
            bool cutAway(vec3 at, vec3 facing, bool hung) {
                vec2 edge = floor(at.xy + 0.5), off = abs(at.xy - edge);
                bool northCut = mod(cutMarks(vec2(floor(at.x - 0.06), edge.y)), 2.0) > 0.5 || mod(cutMarks(vec2(floor(at.x + 0.06), edge.y)), 2.0) > 0.5;
                bool westCut = cutMarks(vec2(edge.x, floor(at.y - 0.06))) > 1.5 || cutMarks(vec2(edge.x, floor(at.y + 0.06))) > 1.5;
                if (!hung && abs(facing.z) > max(abs(facing.x), abs(facing.y))) {
                    return off.y < 0.06 && northCut || off.x < 0.06 && westCut;
                }
                float reach = hung ? 0.45 : 0.3;
                bool north = off.y < reach && (hung ? off.y < off.x : abs(facing.y) > abs(facing.x));
                bool west = off.x < reach && (hung ? off.x <= off.y : abs(facing.x) >= abs(facing.y));
                return north ? northCut : west && westCut;
            }
            uniform vec3 uHole;
            uniform float uHoleUp;
            uniform vec4 uHoleFrom;
            // The game's window through trees, in the picture's pixels: its middle, and half its
            // width and height. Its dither is laid in cells the size of a texel of the game's mask,
            // counted from the middle as the game's are. See CanopyWindow.
            uniform vec4 uCanopy;
            // The size of a texel of the game's masks, in the picture's pixels.
            uniform float uCanopyTexel;
            // The pointer's window while a firearm is aimed: its middle and radius, 0 for none.
            uniform vec3 uCanopyAim;
            // The trees the game draws see-through in its window, by square, and how much of each it draws.
            uniform vec3 uFaded[CANOPY_TREES];
            uniform int uFadedCount;
            varying vec3 vPlace;
            varying vec4 vBlade;

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

            // Whether the sun reaches a point of air, from the near map or else the far one, and
            // in the sun past both.
            float sunOnAir(vec3 p) {
                vec3 c = (uNearMatrix * vec4(p, 1.0)).xyz * 0.5 + 0.5;
                if (inside(c)) {
                    return shadow2D(uShadowNear, c).r;
                }
                c = (uFarMatrix * vec4(p, 1.0)).xyz * 0.5 + 0.5;
                return inside(c) ? shadow2D(uShadowFar, c).r : 1.0;
            }

            // Where a point under something asks a sun map about itself. A map holds every surface behind
            // where it is, by uSlopeOffset texels of the surface's slope in the map, and the lookup is
            // answered by the texels round it, a texel more, and those its soft edge spreads over. A sheet
            // lying almost along the light is far behind itself, so a point closer under it than those
            // texels span reads as in front of it, and where nothing nearer the sun shades the point, as
            // in the sun: the top of every wall of a room under one flat sheet. So a point with something
            // over it in the map from above asks from no nearer under the highest thing there than the sun
            // map can tell the two apart. In a room under a pitched roof the ceiling is the sheet, and the
            // roof the map holds over a point 0.16 in from its wall has risen by as much over that: a point
            // in a room asks from that much lower again, by under. Out of doors that would take in what
            // stands in front of a wall, a bin or a table, as far down. A point with nothing over it, the
            // foot of a wall on the ground or a roof, which that map holds at or a little under itself, is
            // asked where it is, and so is one past that map's reach.
            vec3 sunAsked(vec3 from, float texels, float texelUp, float under) {
                vec3 c = (uRoofMatrix * vec4(from, 1.0)).xyz * 0.5 + 0.5;
                if (c.x <= 0.0 || c.x >= 1.0 || c.y <= 0.0 || c.y >= 1.0) {
                    return from;
                }
                float over = texture2D(uHeights, c.xy).r;
                if (over >= 1.0) {
                    return from;
                }
                float top = from.z + (c.z - over) * uRoofDeep;
                if (top <= vWorld.z + 0.001) {
                    return from;
                }
                return vec3(from.xy, min(from.z, top - (uSlopeOffset + 1.0 + texels) * texelUp - under));
            }

            const float RELIEF = 0.05;
            // How much more of a clear sky's light an upright face turned to the sun's side of it
            // has, and how much less one turned away, with the sun on the horizon.
            const float SKY_SIDE = 0.35;
            // A leaf of a crown: what it has of the crown's sun when turned edge on to it, and what more
            // when turned full to it; how much of the sun on its back shows through, and how
            // much more of it looking straight towards the sun; and how smooth its skin is.
            const float CROWN_CLEAR = 2.2;
            const float LEAF_SHADE = 0.5, LEAF_SUN = 1.15, LEAF_GLOW = 2.0, LEAF_GLOW_AGAINST = 0.6, LEAF_ROUGH = 0.42;
            // How wide a board of a boarded wall is, in tiles: about 15 cm.
            const float BOARD_WIDE = 0.15;
            // A brick and the mortar round it, a lapped board's show, and half the width of a
            // joint in brick and in stone, in tiles. Each divides sixty tiles a whole number
            // of times: see vPlace.
            const float BRICK_LONG = 0.2, BRICK_HIGH = 0.0625, BRICK_JOINT = 0.0045, LAP_HIGH = 0.125, STONE_JOINT = 0.008;
            // How far the foot of a lapped board stands out of the board under it, in tiles: about 12 mm.
            const float LAP_PROUD = 0.012;
            // A cast block, three courses of brick high and three bricks long, and half its joint.
            const float BLOCK_LONG = 0.6, BLOCK_HIGH = 0.1875, BLOCK_JOINT = 0.005;
            // 1 while timber() is making a post or a brace and not a wall of boards.
            float arcWhole = 0.0;

            // How much of the sun a surface throws at the eye: the microfacet model every
            // modern renderer uses (GGX facets, Schlick's Fresnel, Smith's shadowing). rough
            // is how rough the surface is, 0 a mirror and 1 chalk; f0 is how much it mirrors
            // seen square on, 0.04 for anything that is not metal.
            float glossed(vec3 n, vec3 v, vec3 l, float rough, float f0) {
                vec3 h = normalize(v + l);
                float nl = max(dot(n, l), 0.0), nv = max(dot(n, v), 0.001), nh = max(dot(n, h), 0.0), vh = clamp(dot(v, h), 0.0, 1.0);
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
            uniform vec4 uLantern;
            uniform vec3 uOpenLight;
            uniform float uDaylightKept;
            uniform float uRoomKept;
            """ + BEAMS + MuzzleFlash.GLSL + Fires.GLSL + """
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
            uniform sampler2D uSprigForm;
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

            """ + GROUND_NOISE + "const float TILE_JOINT = " + WallBuilder.TILE_JOINT_HALF + ";\n" + """

            // Whether a pixel is in a window of that middle and those half widths, whose dither
            // runs from edgeFrom to edgeTo of them.
            bool inCanopy(vec2 middle, vec2 size, float edgeFrom, float edgeTo) {
                vec2 off = gl_FragCoord.xy - middle;
                float reach = length(off / size);
                return reach < edgeTo && grain(floor(off / uCanopyTexel)) >= smoothstep(edgeFrom, edgeTo, reach);
            }

            // The light and dark of a picture of boards or of courses of shingle at a place on a
            // wall, a floor or a roof. Laid on as it is, the picture comes round again every pace
            // or two, and the same knot is seen all the way down a fence. So it is cut into
            // strips along its grain, at cut, which is a joint between two of its boards, and
            // each strip is slid along the grain its own amount, half of them turned end for
            // end, and is a shade lighter or darker than the next, by up to shades: none on a floor,
            // where a room seen from above showed as a chequer of lighter and darker blocks. upright is for a picture
            // whose grain runs up it. A strip is the same again every strips of them: the place
            // is counted from a corner that moves a whole number of pictures at a time. With
            // the light and dark comes how far the surface stands out there, 1 at its top.
            // across and down are how fast the place changes over the screen, asked of the
            // place as it was: slid, it jumps from one strip to the next, and moved for the
            // surface's depth (see relieve) it jumps at the lip of every hollow.
            vec2 laid(sampler2D picture, vec2 at, vec2 across, vec2 down, float cut, bool upright, float strips, float shades) {
                vec2 grain = upright ? at.yx : at;
                float strip = mod(floor(grain.y - cut), strips);
                vec2 own = fract(sin(vec2(strip * 12.9898 + 4.1, strip * 78.233 + 1.7)) * 43758.5453);
                grain.x = (own.y > 0.5 ? -grain.x : grain.x) + own.x;
                vec4 seen = texture2DGradARB(picture, upright ? grain.yx : grain, across, down);
                return vec2(dot(seen.rgb, vec3(0.299, 0.587, 0.114)) * (1.0 + shades * (fract(own.x * 5.0) - 0.5)), seen.a);
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

            // How long a streak the ripples a pixel cannot show draw a reflection out into, as a
            // multiple of how much they tilt the water.
            const float WATER_STREAK = 0.52;
            const int WATER_TAPS = 8;

            // What the water shows of a place, counted from the eye: its image in the water's
            // plane is found in the picture drawn from under the water where the eye's own
            // picture would draw it. For level water that is the pixel being drawn, whatever
            // way the eye is turned.
            // coarser says which level of the picture is read, 0 being the picture as drawn:
            // the level is named and not left to the card, which would go by how fast the
            // place read changes from one pixel to the next, and over a ripple that is fast.
            vec3 overWater(vec3 fromEye, float coarser) {
                vec3 image = fromEye - 2.0 * (dot(uWater.xyz, fromEye) + uWater.w) * uWater.xyz;
                vec4 seen = gl_ModelViewProjectionMatrix * vec4(image, 1.0);
                return texture2DLod(uWaterPicture, clamp(seen.xy / max(seen.w, 1e-4) * uWaterShare, uWaterRect.xy, uWaterRect.zw) * 0.5 + 0.5, coarser).rgb;
            }

            // How many of them fit across a tile, for the three sizes of ripple on water.
            const vec3 RIPPLES = vec3(1.3, 3.1, 7.7);

            // The height of water's surface: three sizes of ripple, each drifting its own way.
            // kept is how much of each size is there, 1 for all of it.
            float swellOf(vec2 p, vec3 kept) {
                float t = uSeconds;
                return patches(p * RIPPLES.x + vec2(t * 0.21, t * 0.13)) * 0.5 * kept.x
                        + patches(p * RIPPLES.y + vec2(-t * 0.33, t * 0.27)) * 0.3 * kept.y
                        + patches(p * RIPPLES.z + vec2(t * 0.41, -t * 0.52)) * 0.2 * kept.z;
            }

            float swell(vec2 p) {
                return swellOf(p, vec3(1.0));
            }

            """ + SKY_SEEN + """

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
                return mix(sky, land, 1.0 - smoothstep(roofs - 0.02, roofs + 0.02, line.z));
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
                float joint = 1.0 - smoothstep(0.018, 0.018 + wide + 1e-6, edge);
                float eased = 1.0 - smoothstep(0.0, 0.09, edge);
                lean = (part < 0.5 ? -1.0 : 1.0) * eased * seen;
                made *= mix(0.95, (1.0 - 0.8 * joint) * (1.0 - 0.1 * eased), seen);
                // The nails: two to a board at every rail, a rail a little over a pace above the last.
                float rail = (fract(up / 1.15 + 0.3) - 0.5) * 1.15;
                float nail = length(vec2((abs(part - 0.5) - 0.3) * BOARD_WIDE, rail));
                float soft = fwidth(up);
                float head = (1.0 - smoothstep(0.005, 0.005 + soft + 1e-6, nail)) * (1.0 - smoothstep(0.004, 0.012, soft)) * (1.0 - arcWhole);
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

            // A number from 0 to 1 for each brick of a wall, with no pattern from one brick to
            // the next: grain's, taken at whole numbers, falls into slanting rows.
            float facHash(vec2 p) {
                vec3 q = fract(vec3(p.xyx) * 0.1031);
                q += dot(q, q.yzx + 33.33);
                return fract((q.x + q.y) * q.z);
            }

            // What every masonry wall has had from the weather, far coarser than a brick: broad
            // patches a shade lighter and darker, and the rain's runs down it.
            float facWeather(float run, float up) {
                return 1.0 + 0.12 * (patches(vec2(run * 0.43, up * 0.37)) - 0.5) + 0.06 * (patches(vec2(run * 2.3, up * 0.21) + 5.0) - 0.5);
            }

            // A wall of brick, laid here and not painted: where along the wall the place is,
            // and how high. The game's picture of one is a few dots to a brick, and stood a
            // pace away each brick is a blurred slab the size of a shoe box. These are bricks
            // of a brick's size, a hand long and three fingers high, each course half a brick
            // along from the one under it. No two bricks were fired alike: each is its own
            // shade of the wall's colour, a little warmer or cooler, and one in ten was nearer
            // the fire and is dark. A brick's face is mottled and pitted, and its edges are
            // worn round. The mortar lies back between them. lean says how far the surface
            // turns at a brick's edge, along the wall and up it, so the sun picks the courses
            // out. Whatever is finer than a pixel is left as its mean.
            vec3 facBrick(vec3 body, vec3 mortar, float run, float up, vec2 size, float gapHalf, float vary, out vec2 lean) {
                vec2 soft = vec2(fwidth(run), fwidth(up));
                float course = up / size.y;
                float row = floor(course);
                float along = run / size.x + 0.5 * mod(row, 2.0);
                float which = mod(floor(along), 300.0), tier = mod(row, 960.0);
                vec2 part = vec2(fract(along), fract(course));
                vec2 edge = min(part, 1.0 - part) * size;
                // A joint under a pixel wide would be drawn in some pixels and missed in the
                // next: it thins out as it gets there, and the wall takes its share of it.
                vec2 seen = 1.0 - smoothstep(0.2, 0.55, soft / size);
                vec2 gap = 1.0 - smoothstep(vec2(gapHalf) - 0.5 * soft, vec2(gapHalf) + 0.5 * soft + 0.001, edge);
                float joint = max(gap.x * seen.x, gap.y * seen.y);
                float r1 = facHash(vec2(which, tier)), r2 = facHash(vec2(which + 17.0, tier + 5.0)), r3 = facHash(vec2(which + 41.0, tier + 29.0));
                float own = min(seen.x, seen.y) * vary;
                vec3 made = body * mix(1.0, (0.9 + 0.2 * r1) * (1.0 - 0.2 * step(0.93, r3)), own)
                        * mix(vec3(1.0), mix(vec3(1.05, 0.99, 0.93), vec3(0.96, 1.0, 1.05), r2), own);
                float near = 1.0 - smoothstep(0.003, 0.014, max(soft.x, soft.y));
                float mottle = patches(vec2(run * 27.0 + r1 * 9.0, up * 41.0 + r2 * 9.0)) * 0.6 + patches(vec2(run * 113.0, up * 131.0) + 7.0) * 0.4;
                float pit = smoothstep(0.74, 0.9, patches(vec2(run * 230.0 + r2 * 50.0, up * 250.0)));
                made *= 1.0 + near * (0.2 * (mottle - 0.5) - 0.2 * pit);
                vec2 eased = (1.0 - smoothstep(vec2(gapHalf), vec2(gapHalf + 0.007), edge)) * seen;
                lean = vec2(part.x < 0.5 ? -eased.x : eased.x, part.y < 0.5 ? -eased.y : eased.y) * (1.0 - joint);
                vec3 bed = mortar * (0.8 + 0.16 * patches(vec2(run * 61.0, up * 67.0) + 3.0));
                // What the joints that are too fine to draw take from the wall's colour.
                float lost = 0.3 * (1.0 - seen.y) + 0.09 * (1.0 - seen.x);
                return mix(mix(made, bed, joint), mortar * 0.88, lost * (1.0 - joint)) * facWeather(run, up);
            }

            // A wall of boards laid along it, each lapped over the one below: its foot stands
            // out and throws a line of shadow on the board under it, and its face leans back
            // from there to where the next board covers it. Each board is a few paces long,
            // and where two meet end to end there is a fine dark joint. The paint on it has
            // the brush's strokes along the board and a little dirt held under each lap.
            // How much of the sun reaches a lapped board past the foot of the one over it: see facLapped.
            float lapSun = 1.0;
            vec3 facLapped(vec3 body, float run, float up, out vec2 lean) {
                float board = up / LAP_HIGH;
                float tier = mod(floor(board), 480.0), part = fract(board);
                float soft = fwidth(up) / LAP_HIGH, softRun = fwidth(run);
                float seen = 1.0 - smoothstep(0.2, 0.55, soft);
                float r1 = facHash(vec2(tier, 3.0)), r2 = facHash(vec2(tier, 11.0));
                vec3 made = body * mix(1.0, 0.98 + 0.04 * r1, seen);
                // The foot of the board above stands LAP_PROUD out of this one and throws the sun's
                // shadow down it: a hair line in a low sun that faces the wall, a broad band when
                // the sun is high or comes along the wall. Far off, the board takes its share.
                vec3 outward = normalize(vNormal);
                outward = dot(outward, uEye - vWorld) < 0.0 ? -outward : outward;
                float into = dot(normalize(outward.xy + 1e-6), uLight.xy);
                float drop = into > 0.0 ? min(LAP_PROUD * max(uLight.z, 0.0) / (max(into, 0.02) * LAP_HIGH), 1.0) : 0.0;
                lapSun = mix(1.0 - drop, smoothstep(drop - soft - 0.002, drop + soft + 0.002, 1.0 - part), seen);
                // Under the foot of the board above: the crease the sky gets little into, and under that what the rain left.
                float shadow = smoothstep(1.0 - 0.06 - soft, 1.0 - 0.015, part);
                float grime = smoothstep(0.55, 1.0, part) * (0.5 + 0.5 * patches(vec2(run * 3.1, tier * 1.7)));
                float foot = 1.0 - smoothstep(0.0, 0.06 + soft, part);
                float butt = run / 7.5 + r2;
                float end = abs(fract(butt) - 0.5) * 7.5;
                float joint = (1.0 - smoothstep(0.0015, 0.0015 + softRun + 1e-6, 3.75 - end)) * (1.0 - smoothstep(0.004, 0.012, softRun)) * seen;
                float near = 1.0 - smoothstep(0.003, 0.014, max(softRun, fwidth(up)));
                float stroke = patches(vec2(run * 6.0 + tier * 3.3, part * 9.0 + tier)) * 0.6 + patches(vec2(run * 31.0, part * 23.0 + tier * 7.0)) * 0.4;
                made *= 1.0 + near * 0.07 * (stroke - 0.5);
                made *= mix(0.9, (1.0 - 0.5 * shadow) * (1.0 - 0.1 * grime) * (1.0 + 0.07 * foot) * (1.0 - 0.3 * joint), seen);
                lean = vec2(0.0, (0.22 - 0.9 * foot) * seen);
                return made * (1.0 + 0.5 * (facWeather(run, up) - 1.0));
            }

            // A wall of cut stone in courses, a deep course and a shallow one by turns, each
            // stone as long as it happened to be cut. No stone is the colour of the next, and
            // a stone's face is left rough from the chisel. The joints are wide and lie back.
            vec3 facStone(vec3 body, vec3 mortar, float run, float up, out vec2 lean) {
                vec2 soft = vec2(fwidth(run), fwidth(up));
                float band = up / 0.5;
                float within = fract(band) * 0.5;
                bool deep = within < 0.3;
                float high = deep ? 0.3 : 0.2, at = deep ? within : within - 0.3;
                float tier = mod(floor(band), 120.0) * 2.0 + (deep ? 0.0 : 1.0);
                float wide = deep ? 0.5 : 0.4, count = deep ? 120.0 : 150.0;
                float along = run / wide + facHash(vec2(tier, 7.0));
                float which = floor(along), x = fract(along);
                // Each joint between two stones stands a little to one side of where an even
                // spacing would put it.
                float left = (facHash(vec2(mod(which, count), tier)) - 0.5) * 0.5;
                float right = 1.0 + (facHash(vec2(mod(which + 1.0, count), tier)) - 0.5) * 0.5;
                if (x < left) {
                    which -= 1.0;
                    x += 1.0;
                    right = 1.0 + left;
                    left = (facHash(vec2(mod(which, count), tier)) - 0.5) * 0.5;
                } else if (x > right) {
                    which += 1.0;
                    x -= 1.0;
                    left = right - 1.0;
                    right = 1.0 + (facHash(vec2(mod(which + 1.0, count), tier)) - 0.5) * 0.5;
                }
                which = mod(which, count);
                vec2 low = vec2((x - left) * wide, at), far = vec2((right - x) * wide, high - at);
                vec2 edge = min(low, far);
                vec2 seen = 1.0 - smoothstep(0.2, 0.55, soft / vec2(wide, high));
                vec2 gap = 1.0 - smoothstep(vec2(STONE_JOINT) - 0.5 * soft, vec2(STONE_JOINT) + 0.5 * soft + 0.001, edge);
                float joint = max(gap.x * seen.x, gap.y * seen.y);
                float r1 = facHash(vec2(which + 3.0, tier)), r2 = facHash(vec2(which + 19.0, tier + 7.0));
                float own = min(seen.x, seen.y);
                vec3 made = body * mix(1.0, 0.86 + 0.26 * r1, own) * mix(vec3(1.0), mix(vec3(1.03, 1.0, 0.95), vec3(0.97, 1.0, 1.03), r2), own);
                float near = 1.0 - smoothstep(0.004, 0.02, max(soft.x, soft.y));
                float tooled = patches(vec2(run * 9.0 + r1 * 31.0, up * 11.0 + r2 * 31.0)) * 0.5 + patches(vec2(run * 37.0, up * 43.0) + r1 * 17.0) * 0.3
                        + patches(vec2(run * 150.0, up * 170.0)) * 0.2;
                made *= 1.0 + near * 0.34 * (tooled - 0.5);
                vec2 eased = (1.0 - smoothstep(vec2(STONE_JOINT), vec2(STONE_JOINT + 0.016), edge)) * seen;
                lean = vec2(low.x < far.x ? -eased.x : eased.x, low.y < far.y ? -eased.y : eased.y) * (1.0 - joint);
                vec3 bed = mortar * (0.78 + 0.16 * patches(vec2(run * 53.0, up * 59.0) + 3.0));
                float lost = 0.12 * (1.0 - own);
                return mix(mix(made, bed, joint), mortar * 0.85, lost * (1.0 - joint)) * facWeather(run, up);
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
                float out2 = (away - turn * 0.42) * 11.0;
                float ring = sin((away - turn * 0.42) * 46.0) * exp(-out2 * out2) * (1.0 - turn);
                float splash = (1.0 - smoothstep(0.0, 0.07, away)) * (1.0 - smoothstep(0.0, 0.22, turn));
                return vec2(ring, splash);
            }

            """;

    /** Depth for the surfaces that have it. Joined on after WORLD_FRAGMENT_PARTS when the shader is put together. */
    static final String WORLD_FRAGMENT_RELIEF = """
            // A wall of boards, a shingled roof, a gravel drive are drawn as flat sheets, and
            // a flat sheet gives itself away the moment the head moves: the gap between two
            // boards does not slide behind the board in front of it, and no stone hides the
            // foot of the stone beyond. Here a surface whose height is known at every point,
            // from its picture's fourth number or from how it is made, is given that depth.
            // The line of sight is followed down into the surface to where it first meets
            // it, and that place is the one drawn. From there the way to the sun is looked
            // along for a lip that stands in it, and the surface is tilted as its height
            // changes. Only near the eye: further off than a few paces a board's joint is
            // under a pixel deep.
            uniform float uRelief;
            // 1 where the mod's own textures are laid over the game's surfaces, 0 where a surface shows its sprite alone.
            uniform float uOwnTextures;
            // What the surface is: 1 boards side by side, 2 shingles, 3 a wooden floor, 4 planks, 5 open ground.
            float reliefKind = 0.0;
            // Where on the surface the pixel is, and how fast that changes over the screen.
            vec2 reliefBase = vec2(0.0), reliefAcross = vec2(0.0), reliefDown = vec2(0.0);
            // Open ground: where on each of its two copies of the picture, which copy, and how much of it is tarmac, gravel and earth.
            vec2 reliefOne = vec2(0.0), reliefTwo = vec2(0.0), reliefOneAcross = vec2(0.0), reliefOneDown = vec2(0.0), reliefTwoAcross = vec2(0.0), reliefTwoDown = vec2(0.0);
            float reliefWhich = 0.0;
            vec3 reliefShare = vec3(0.0);
            // What relieve found: how much of the depth is drawn at this distance, how deep the
            // place seen lies (0 at the top), how much of the sun gets to it past the
            // surface's own lips, and which way the surface leans there.
            float reliefNear = 0.0, reliefLow = 0.0, reliefShade = 1.0;
            vec3 reliefBend = vec3(0.0);

            // A floor of boards at a place on it, in tiles: vBoards of them to a tile, running
            // along y where vLie is 1, each a tile long and begun its own way along, so no two
            // ends meet across a joint. The light and dark, and with it how far the surface
            // stands out there: 0 in the joint between two boards or two ends. The wood is the
            // picture's, and only its grain: each board is a length of one of two boards of the
            // picture that run it from side to side with no end in them, begun anywhere along
            // and either way up, a shade lighter or darker than the next. The joints come every
            // whole tile, so they are the same wherever the place is counted from.
            vec2 floorBoards(vec2 at, vec2 across, vec2 down) {
                vec2 run = vLie > 0.5 ? at.yx : at, runAcross = vLie > 0.5 ? across.yx : across, runDown = vLie > 0.5 ? down.yx : down;
                // A count interpolated across the face may come a hair under the whole number it is.
                float count = floor(vBoards + 0.5);
                float over = run.y * count, board = floor(over), within = over - board;
                board = mod(board, 60.0 * count);
                float begun = fract(sin(board * 12.9898 + 4.1) * 43758.5453);
                float gone = run.x + begun, piece = mod(floor(gone), 60.0), inPiece = fract(gone);
                vec3 own = fract(sin(vec3(board * 78.233 + piece * 39.346 + 1.7, board * 11.135 + piece * 83.155 + 5.9, board * 37.719 + piece * 27.431 + 3.3)) * 43758.5453);
                vec2 band = own.z > 0.5 ? vec2(0.46, 0.16) : vec2(0.662, 0.12);
                vec2 on = vec2(run.x * 0.5 + own.x, band.x + band.y * (own.y > 0.5 ? within : 1.0 - within));
                vec4 seen = texture2DGradARB(uWood, on, vec2(runAcross.x * 0.5, runAcross.y * count * band.y), vec2(runDown.x * 0.5, runDown.y * count * band.y));
                // How fast the place changes over the screen across the boards and along them, each on its own:
                // seen end on, the joints between boards stay sharp far past where the ends blur.
                float fineAcross = length(vec2(runAcross.y, runDown.y)), fineAlong = length(vec2(runAcross.x, runDown.x));
                float side = min(within, 1.0 - within) / count, end = min(inPiece, 1.0 - inPiece);
                // Further off than a joint can be drawn, the floor is the mean of its boards and joints.
                float joint = max((1.0 - smoothstep(0.003, 0.008 + fineAcross, side)) * (1.0 - smoothstep(0.02, 0.06, fineAcross)),
                        (1.0 - smoothstep(0.003, 0.008 + fineAlong, end)) * (1.0 - smoothstep(0.02, 0.06, fineAlong)));
                return vec2(dot(seen.rgb, vec3(0.299, 0.587, 0.114)) * (0.94 + 0.12 * own.x) * (1.0 - 0.5 * joint), seen.a * (1.0 - joint));
            }

            // How far the surface stands out so far along it from the pixel's own place: 1 at its top, 0 at its deepest.
            float raisedAt(vec2 moved) {
                if (reliefKind < 1.5) {
                    // Boards butted edge to edge, each eased off at its edges: a groove at every joint.
                    float part = fract((reliefBase.x + moved.x) / BOARD_WIDE);
                    return smoothstep(0.008, 0.075, min(part, 1.0 - part));
                }
                if (reliefKind < 4.5) {
                    vec2 at = reliefBase + moved;
                    return reliefKind < 2.5 ? laid(uShingles, at * 0.55, reliefAcross * 0.55, reliefDown * 0.55, 0.1, false, 33.0, 0.14).y
                            : reliefKind < 3.5 ? (vBoards > 0.5 ? floorBoards(at, reliefAcross, reliefDown).y
                                    : laid(uWood, at * 0.45, reliefAcross * 0.45, reliefDown * 0.45, 0.44, false, 27.0, 0.14).y)
                            : laid(uPlanks, at * 0.7, reliefAcross * 0.7, reliefDown * 0.7, 0.648, true, 42.0, 0.14).y;
                }
                bool other = reliefWhich > 0.5;
                vec2 at = other ? reliefTwo + vec2(moved.x * 0.8 - moved.y * 0.6, moved.x * 0.6 + moved.y * 0.8) * 0.37 : reliefOne + moved * 0.83;
                vec2 across = other ? reliefTwoAcross : reliefOneAcross, down = other ? reliefTwoDown : reliefOneDown;
                float sunk = 0.0;
                if (reliefShare.x > 0.02) {
                    sunk += reliefShare.x * (1.0 - texture2DGradARB(uAsphalt, at, across, down).a);
                }
                if (reliefShare.y > 0.02) {
                    sunk += reliefShare.y * (1.0 - texture2DGradARB(uGravel, at, across, down).a);
                }
                if (reliefShare.z > 0.02) {
                    sunk += reliefShare.z * (1.0 - texture2DGradARB(uSoil, at, across, down).a);
                }
                return 1.0 - sunk;
            }

            // Gives a surface of that kind its depth, in tiles, at this pixel. along and up are
            // the two ways across the surface its place is counted in, and facing is the way
            // it faces, towards the eye. What comes back is how far along the surface the
            // place seen lies from the pixel's own; the rest is left in reliefNear, reliefLow,
            // reliefShade and reliefBend. The caller has set reliefBase, or for open ground
            // the numbers of its two copies.
            vec2 relieve(float kind, vec3 along, vec3 up, vec3 facing, float depth) {
                vec3 toEye = uEye - vWorld;
                float away = length(toEye);
                float near = uRelief * (1.0 - smoothstep(6.0, 10.0, max(away - uRangeFrom, 0.0)));
                reliefNear = near;
                reliefBend = vec3(0.0);
                if (near <= 0.0) {
                    return vec2(0.0);
                }
                reliefKind = kind;
                vec3 acrossScreen = dFdx(vPlace), downScreen = dFdy(vPlace);
                reliefAcross = vec2(dot(acrossScreen, along), dot(acrossScreen, up));
                reliefDown = vec2(dot(downScreen, along), dot(downScreen, up));
                vec3 look = toEye / away;
                depth *= near;
                // Seen along itself a surface would be followed for ever: past about 77 degrees from square on it gets no deeper.
                float square = max(abs(dot(look, facing)), 0.22);
                vec2 run = vec2(dot(look, along), dot(look, up)) * (-depth / square);
                // More steps the more slanting the look, for the line then crosses more of the surface.
                float steps = floor(mix(14.0, 5.0, square) + 0.5);
                vec2 moved = vec2(0.0);
                float ray = 0.0, bed = 1.0 - raisedAt(moved), gap = 0.0;
                for (int i = 0; i < 14; i++) {
                    if (ray >= bed || float(i) >= steps) {
                        break;
                    }
                    gap = bed - ray;
                    ray += 1.0 / steps;
                    moved += run / steps;
                    bed = 1.0 - raisedAt(moved);
                }
                // Between the last step above the surface and the first below it, where the two lines cross.
                float back = clamp((ray - bed) / max(ray - bed + gap, 1e-5), 0.0, 1.0);
                moved -= run / steps * back;
                float low = max(ray - back / steps, 0.0);
                // The lean, from the height a little way off each way: no nearer than a pixel covers, or it would glitter.
                float off = max(0.0025, 1.5 * max(length(reliefAcross), length(reliefDown)));
                float here = raisedAt(moved);
                vec2 slope = vec2(raisedAt(moved + vec2(off, 0.0)) - here, raisedAt(moved + vec2(0.0, off)) - here) * (depth / off);
                slope *= min(1.0, 1.2 / max(length(slope), 1e-4));
                reliefBend = along * slope.x + up * slope.y;
                // What is on top is in nothing's shade. What is down in a hollow looks up towards the sun past the lip.
                float sunUp = dot(uLight, facing);
                float shade = 1.0;
                if (low > 0.03 && sunUp > 0.0) {
                    vec2 climb = vec2(dot(uLight, along), dot(uLight, up)) * (depth / max(sunUp, 0.15));
                    float blocked = 0.0;
                    for (int i = 1; i < 5; i++) {
                        float rise = low * float(i) / 5.0;
                        blocked = max(blocked, low - rise - 1.0 + raisedAt(moved + climb * rise));
                    }
                    shade = 1.0 - smoothstep(0.0, 0.12, blocked);
                }
                reliefShade *= mix(1.0, shade, near);
                reliefLow = low * near;
                return moved;
            }

            """;

    static final String WORLD_FRAGMENT_MAIN_START = """
            void main() {
                vec4 texel = texture2D(uTexture, vUv);
                // How the height and how far the wind takes a vertex change over the screen, for a plant
                // drawn as a card in lying snow. Asked here, before any pixel is left out, where every
                // pixel of the screen's two by two takes part: further down the answer would decide what
                // is left out from neighbours that were left out themselves. The test of a uniform before
                // them is taken alike by every pixel of the two by two, so Low does not pay for them.
                vec2 riseScreen = vec2(0.0), coverScreen = vec2(0.0), pictureScreen = vec2(0.0);
                if (uPlantSnow > 0.0) {
                    riseScreen = vec2(dFdx(vWorld.z), dFdy(vWorld.z));
                    coverScreen = vec2(dFdx(vCover), dFdy(vCover));
                    pictureScreen = vec2(dFdx(vUv.y), dFdy(vUv.y));
                }
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
                    // The weights of the curve, and the pairs of dots the card is left to mix, are MJP's
                    // SampleTextureCatmullRom, used under its MIT licence: THIRD-PARTY-NOTICES.md.
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
                // pixel that covers more than a texel takes nine samples spread over what it
                // covers, each weighed by how solid it is, and is as solid as their mean.
                vec2 stepX = dFdx(vUv), stepY = dFdy(vUv);
                bool small = uTexel.x > 0.0 && max(dot(stepX, stepX), dot(stepY, stepY)) > 1.3 * uTexel.x * uTexel.x;
                // The most solid of those nine samples, or -1 for a pixel that took none: see the painted shadow below.
                float mostSolid = -1.0;
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
                            mostSolid = max(mostSolid, s.a);
                        }
                    }
                    texel = vec4(sum / max(solid, 0.001), solid / 9.0);
                }
                // The skin of a wall that faces away from the side its sprite was painted for
                // keeps the sprite's outline, its windows and doors, and nothing else of it:
                // the sprite's light and dark are the other side's wallpaper or boards. It is
                // the colour of the surface its own side has. uTint.a is 1 for paint and 2 for
                // boards lapped one over the next. From 4 up the wall is laid further down, but
                // for 10, a tile, a joint or a band of a tiled wall, each in a colour of its own,
                // glazed and even: WallSides.FINE.
                bool fineTint = abs(uTint.a - 10.0) < 0.5;
                // 11 is a band of tiles laid as one face (WallSides.TILED): the face's texture
                // coordinates are whole numbers at the middle of each joint along the wall and of
                // each course line up it, and a joint is TILE_JOINT either side of one in the world,
                // whatever size the tiles are. Where a joint is narrower than a pixel the wall is
                // the mean of tiles and joints, and nothing crawls as the eye moves.
                bool tiledTint = abs(uTint.a - 11.0) < 0.5;
                if (tiledTint) {
                    vec3 along = normalize(vNormal);
                    // Measured from the eye: the map position is a float step of 0.001 tile away from the
                    // origin, coarser than a pixel close to a wall, and its width would jump block to block.
                    vec2 world = vec2(dot(vPlace.xy, vec2(along.y, -along.x)), vPlace.z);
                    // On a flat face the coordinates go as the world does, so the two widths of a pixel give a tile's size.
                    vec2 worldPixel = max(fwidth(world), vec2(1e-5));
                    vec2 tileSize = worldPixel / max(fwidth(vUv), vec2(1e-6));
                    vec2 toJoint = abs(vUv - floor(vUv + 0.5)) * tileSize;
                    vec2 inJoint = 1.0 - smoothstep(TILE_JOINT - 0.5 * worldPixel, TILE_JOINT + 0.5 * worldPixel, toJoint);
                    vec2 share = clamp(2.0 * TILE_JOINT / max(tileSize, vec2(1e-4)), 0.0, 1.0);
                    inJoint = mix(inJoint, share, smoothstep(0.5 * TILE_JOINT, 2.0 * TILE_JOINT, worldPixel));
                    texel.rgb = mix(uTint.rgb, uJoint, max(inJoint.x, inJoint.y));
                }
                if (uTint.a > 0.5 && uTint.a < 3.5 || fineTint) {
                    texel.rgb = fineTint ? uTint.rgb : arcTone(uTint.rgb, vWorld);
                    if (uTint.a > 1.5 && uTint.a < 2.5) {
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
                // Which way a board's surface leans for the groove at its edge.
                vec3 bent = vec3(0.0);
                // A wall of brick, of lapped boards or of stone is laid here and not shown as
                // the game painted it: see facBrick. uTint.a is 4 for brick, 5 for lapped
                // boards, 6 for stone and 7 for blocks, uTint.rgb the colour most of the wall's sprite is
                // and uJoint the colour of its joints. What on the sprite is neither colour,
                // the casing round a window, stays as painted. climb is lean up the wall.
                float climb = 0.0;
                bool boarded = uTint.a > 7.5 && uTint.a < 8.5;
                if (uTint.a > 3.5 && !boarded && !fineTint && !tiledTint) {
                    vec3 facing = normalize(vNormal);
                    float run = dot(vPlace.xy, vec2(facing.y, -facing.x));
                    vec2 turned;
                    // From 12 up the sprite is another wall's, the paper of the room behind: it
                    // gives the wall its outline and none of its colour.
                    float how = uTint.a > 11.5 ? uTint.a - 8.0 : uTint.a;
                    // A block is a brick three times the size, and every one the same grey.
                    vec3 laid = how < 4.5 ? facBrick(uTint.rgb, uJoint, run, vPlace.z, vec2(BRICK_LONG, BRICK_HIGH), BRICK_JOINT, 1.0, turned)
                            : how < 5.5 ? facLapped(uTint.rgb, run, vPlace.z, turned)
                            : how < 6.5 ? facStone(uTint.rgb, uJoint, run, vPlace.z, turned)
                            : facBrick(uTint.rgb, uJoint, run, vPlace.z, vec2(BLOCK_LONG, BLOCK_HIGH), BLOCK_JOINT, 0.3, turned);
                    float own = uTint.a > 11.5 ? 1.0 : 1.0 - smoothstep(0.3, 0.45, min(length(texel.rgb - uTint.rgb), length(texel.rgb - uJoint)));
                    texel.rgb = mix(texel.rgb, laid, own);
                    lean = turned.x * own;
                    climb = turned.y * own;
                    lapSun = mix(1.0, lapSun, own);
                }
                arcWhole = vMaterial > 65.5 && vMaterial < 66.5 ? 1.0 : 0.0;
                if (uOwnTextures > 0.5 && (vMaterial > 51.5 && vMaterial < 52.5 || arcWhole > 0.5) && (uTint.a < 0.5 || boarded) && uTexel.x > 0.0) {
                    vec3 tone = vec3(0.299, 0.587, 0.114);
                    // The strokes run up the boards, so the colour is taken from far to either
                    // side of the place and a little above and below it. A wall is told the
                    // colour most of its sprite is instead (uTint.a is 8): near a wall's
                    // end, far to either side is the next sprite on the game's sheet.
                    vec3 round = texel.rgb;
                    float found = 1.0;
                    for (int i = 0; i < 12 && !boarded; i++) {
                        float far = i < 8 ? (i < 2 ? 3.0 : i < 4 ? 7.0 : i < 6 ? 11.0 : 16.0) : (i < 10 ? 6.0 : 14.0);
                        vec2 off = (i < 8 ? vec2(far, 0.0) : vec2(0.0, far)) * (mod(float(i), 2.0) < 0.5 ? 1.0 : -1.0) * uTexel;
                        vec4 beside = texture2D(uTexture, vUv + off);
                        float counts = step(0.5, beside.a);
                        round += beside.rgb * counts;
                        found += counts;
                    }
                    round = boarded ? uTint.rgb : round / found;
                    timbered = step(0.03, round.r - round.b) * step(round.b, round.g + 0.02)
                            * (1.0 - smoothstep(0.2, 0.34, abs(dot(texel.rgb - round, tone))));
                    vec3 facing = normalize(vNormal);
                    vec2 on = vec2(dot(vPlace.xy, vec2(facing.y, -facing.x)), vPlace.z);
                    // The joints between the boards are grooves: see relieve. A post is one piece and has none.
                    if (timbered > 0.5 && arcWhole < 0.5) {
                        reliefBase = on;
                        on += relieve(1.0, vec3(facing.y, -facing.x, 0.0), vec3(0.0, 0.0, 1.0), dot(facing, uEye - vWorld) < 0.0 ? -facing : facing, 0.009);
                        bent = reliefBend;
                    }
                    texel.rgb = mix(texel.rgb, timber(round, on.x, on.y, lean), timbered) * (1.0 - 0.4 * reliefLow);
                    // Where the groove is drawn in depth it turns the surface itself.
                    lean *= timbered * (1.0 - reliefNear);
                }
                bool grass = vKind > 7.5;
                // The game draws its furniture with a dark line round every part, which is what
                // makes a chair a drawing of a chair. A texel much darker than what lies round
                // it is such a line, and takes the colour round it, a little darkened: the
                // edge is still there, as a shade and not as ink. The same for whatever else
                // stands as a card: a mailbox, a lamp, a sign.
                if ((vMaterial > 2.5 && vMaterial < 3.5 && vBoards < 0.5 || vMaterial > 7.5 && vMaterial < 8.5 || vMaterial < 0.5 && vKind > 1.5 && vKind < 3.5) && uTexel.x > 0.0) {
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
                // The inside of a crown: see Flora.CORE. It is leaves in shade all over, no part
                // of it cut away, so whatever is looked at through the crown's sprigs is more
                // crown. Its texture coordinate's whole number is where on the plant's sprite
                // it takes its colour, as a sprig's is, and the part over it says which kind of
                // sprig its leaves are.
                bool core = vMaterial > 80.5 && vMaterial < 81.5;
                if (core) {
                    // Standing in a tree, the inside of its crown is not a wall round the eye.
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.7) {
                        discard;
                    }
                    vec2 cell = floor(vUv);
                    float which = floor(fract(vUv.x) * 8.0);
                    vec2 autumn = autumnOf(vRoot);
                    vec2 at = (cell + 0.5) / 2048.0;
                    vec2 wide = uTexel * 4.0;
                    vec4 sum = vec4(0.0);
                    for (int i = 0; i < 5; i++) {
                        vec2 off = i == 0 ? vec2(0.0) : i == 1 ? vec2(wide.x, 0.0) : i == 2 ? vec2(-wide.x, 0.0) : i == 3 ? vec2(0.0, wide.y) : vec2(0.0, -wide.y);
                        vec4 painted = texture2D(uTexture, at + off);
                        sum += vec4(painted.rgb * painted.a, painted.a);
                    }
                    if (sum.a < 0.5) {
                        discard;
                    }
                    vec3 season = autumnal(sum.rgb / sum.a, autumn.x, autumn.y);
                    season *= mix(1.0, 0.4 / max(dot(season, vec3(0.299, 0.587, 0.114)), 0.05), 0.6);
                    // The sprigs' picture laid over it by where it is, whichever way it faces:
                    // leaves where the picture has them and the dark between leaves where it has none.
                    vec3 faces = normalize(vNormal);
                    vec2 lies = (abs(faces.z) > 0.75 ? vPlace.xy : abs(faces.x) > abs(faces.y) ? vPlace.yz : vPlace.xz) * 2.1;
                    vec2 one = vec2(0.25, 0.5);
                    vec2 kindAt = vec2(mod(which, 4.0), floor(which / 4.0));
                    vec4 sprig = texture2DGradARB(uSprigs, (kindAt + 0.03 + 0.94 * fract(lies)) * one, dFdx(lies) * one, dFdy(lies) * one);
                    // A sprig's picture is a spray with nothing round it, and laid side by side its
                    // edges would rule the crown in squares: a second lot lies over the gaps of the first.
                    vec2 over = lies.yx * 1.37 + 0.41;
                    vec4 behind = texture2DGradARB(uSprigs, (kindAt + 0.03 + 0.94 * fract(over)) * one, dFdx(over) * one, dFdy(over) * one);
                    if (sprig.a < 0.5) {
                        sprig = vec4(behind.rgb * 0.8, behind.a);
                    }
                    // Only the leaves' light and dark is taken from the picture, never its colour:
                    // its twigs are brown, and in here a twig is a hand across.
                    float tone = clamp(dot(sprig.rgb / vec3(0.26, 0.42, 0.12), vec3(0.299, 0.587, 0.114)), 0.65, 1.3);
                    float leaf = smoothstep(0.3, 0.6, sprig.a);
                    vec3 leafy = season * mix(0.5, tone, leaf);
                    texel = vec4(clamp(leafy, 0.0, 1.0), 1.0);
                    small = false;
                }
                vec2 sprigAt = vec2(0.0);
                // How solid the sprig's picture is at the pixel: least at a leaf's edge.
                float sprigSolid = 1.0;
                if (crown) {
                    // A sprig of a crown: a branch tip in leaf, cut out of its card. Its texture
                    // coordinate is two things at once. The whole number is where on the
                    // plant's sprite the sprig takes its colour, so the crown is the green,
                    // gold or red the game has the tree at this time of year, and the part
                    // over it is where on the sprig the pixel is. See Flora.addCrown.
                    vec2 cell = floor(vUv);
                    vec2 on = clamp((vUv - cell - 0.25) * 2.0, 0.0, 1.0);
                    vec2 autumn = autumnOf(vRoot);
                    float each = grain(cell);
                    // Standing in a tree, the sprigs within reach are gone, a whole sprig at
                    // a time, and the view is of the wood beyond them.
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.22 + 0.3 * each) {
                        discard;
                    }
                    // A card seen along its own flat is a smear of leaves a pixel deep. It is left
                    // out before it gets there: the sprigs turned other ways are the crown from here.
                    vec3 lies = cross(dFdx(vPlace), dFdy(vPlace));
                    if (abs(dot(normalize(lies + vec3(1e-12)), normalize(uEye - vWorld))) < 0.22 + 0.16 * each) {
                        discard;
                    }
                    float which = floor(vMaterial - 71.5);
                    sprigAt = (vec2(mod(which, 4.0), floor(which / 4.0)) + on) * vec2(0.25, 0.5);
                    vec4 sprig = texture2D(uSprigs, sprigAt);
                    // Far off, the smaller copies of the picture have every leaf part solid,
                    // and cut at half the crown would thin to twigs.
                    float fine = max(fwidth(on.x), fwidth(on.y));
                    if (sprig.a < mix(0.5, 0.4, smoothstep(0.004, 0.02, fine))) {
                        discard;
                    }
                    sprigSolid = sprig.a;
                    vec2 at = (cell + 0.5) / 2048.0;
                    vec2 wide = uTexel * 4.0;
                    vec4 sum = vec4(0.0);
                    float solid = 0.0;
                    for (int i = 0; i < 5; i++) {
                        vec2 off = i == 0 ? vec2(0.0) : i == 1 ? vec2(wide.x, 0.0) : i == 2 ? vec2(-wide.x, 0.0) : i == 3 ? vec2(0.0, wide.y) : vec2(0.0, -wide.y);
                        vec4 painted = texture2D(uTexture, at + off);
                        sum += vec4(painted.rgb * painted.a, painted.a);
                        solid = i == 0 ? painted.a : solid;
                    }
                    // A sprig whose place on the sprite turns out to have nothing painted round it
                    // has no colour to take, and is not drawn black: it is not drawn. Nor is one
                    // whose place is painted faintly: that is the shade the game paints on the
                    // trunk and between the boughs, a dark wash and not a leaf.
                    if (sum.a < 0.5 || solid < 0.6) {
                        discard;
                    }
                    // A tree's leaves do not turn all at once: some sprigs are ahead of the rest and some behind.
                    float turned = autumn.y;
                    turned = turned > 0.0 ? clamp(turned + 0.32 * (grain(cell + 31.0) - 0.5), 0.0, 1.0) : 0.0;
                    vec3 season = autumnal(sum.rgb / sum.a, autumn.x, turned);
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
            """;

    /** The world's fragment shader from its grass on, up to where the light is worked out. */
    static final String WORLD_FRAGMENT_MAIN_GROWN = """
                // A strip of what Grass grows: the strip is the blade, and its own shape. Which
                // way its flat faces, and how much it shines.
                bool strip = vKind > 23.5;
                vec3 stripFace = vec3(0.0, 0.0, 1.0);
                float stripGloss = 1.0;
                if (strip) {
                    // A blade right against the eye is a green wall across the picture. Within
                    // arm's reach the grass thins away to nothing, a whole blade at a time.
                    float across = vBlade.x, up = vBlade.y, own = vBlade.z;
                    if ((length(uEye - vWorld) - uRangeFrom) < 0.4 + 0.45 * own) {
                        discard;
                    }
                    float form = floor(vBlade.w);
                    float vigour = clamp((vBlade.w - form - 0.05) / 0.9, 0.0, 1.0);
                    if (form < 2.5) {
                        across /= max(1.0 - up * up, 0.02);
                    }
                    if (form > 6.5) {
                        across /= 0.3 + 0.7 * up;
                        // A fan of turf: blades from one root, spreading as the strip widens,
                        // each its own height and each bowed its own way. Where a blade would
                        // be finer than a pixel the fan is fewer and broader blades, halving in
                        // number each time, since a blade under a pixel is there in one frame
                        // and gone in the next.
                        float slots = 12.0;
                        float fine = fwidth(across) * slots * 0.5;
                        slots = max(slots * exp2(-ceil(log2(max(fine / 0.3, 1.0)))), 1.0);
                        float place = (across * 0.5 + 0.5) * slots;
                        // The strip is 0.3 of its width at its foot: SpriteGeometry.FAN_FOOT.
                        float spread = 0.3 + 0.7 * up;
                        float each = floor(own * 4096.0);
                        bool found = false;
                        for (int k = -1; k <= 1; k++) {
                            float slot = floor(place) + float(k);
                            if (slot < 0.0 || slot > slots - 0.5 || found) {
                                continue;
                            }
                            float r = grain(vec2(slot + slots * 13.0, each));
                            float r2 = fract(r * 17.3 + 0.2), r3 = fract(r * 31.7 + 0.6);
                            // The tallest is as tall as the strip, the rest anything down to two fifths of it.
                            float reach = up / (0.4 + 0.6 * r2 * r2 + 0.3 * step(0.8, r3));
                            if (reach >= 1.0) {
                                continue;
                            }
                            float middle = slot + 0.5 + (r - 0.5) * 0.6 + (r3 - 0.5) * 2.2 * up * up / spread;
                            float wide = (0.15 + 0.09 * r3) / spread * (1.0 - reach * reach) * (1.0 + 0.5 * (1.0 - slots / 12.0));
                            if (abs(place - middle) < wide) {
                                found = true;
                                across = (place - middle) / wide;
                                up = reach;
                                own = r;
                            }
                        }
                        if (!found) {
                            discard;
                        }
                        form = 0.0;
                    }
                    // Three more numbers of the strip's own, from its one.
                    float ownA = fract(own * 7.31 + 0.13), ownB = fract(own * 13.7 + 0.57), ownC = fract(own * 29.3 + 0.31);
                    stripFace = normalize(cross(dFdx(vPlace), dFdy(vPlace)) + vec3(0.0, 0.0, 1e-9));
                    // The colour of the lawn here: the game's own green for the square and the
                    // season, pulled towards one green so a lawn is not a quilt of its squares,
                    // paler in drifts and browner where it does poorly.
                    float lit = dot(texel.rgb, vec3(0.3, 0.55, 0.15));
                    // A crop's stalk is coloured from a texel of its sprite, which paints shaded
                    // stalks near black and lit leaf edges near white: a strip is held to a
                    // middle brightness.
                    float held = clamp(lit, 0.2, 0.5);
                    texel.rgb *= held / max(lit, 1e-3);
                    lit = held;
                    vec3 lawn = ownA < 0.33 ? vec3(0.3, 0.47, 0.17) : ownA < 0.66 ? vec3(0.36, 0.5, 0.17) : vec3(0.43, 0.52, 0.19);
                    lawn = mix(lawn, vec3(0.44, 0.47, 0.2), 0.6 * smoothstep(0.5, 0.85, patches(vWorld.xy * 0.9 + 13.0)));
                    lawn = mix(lawn, vec3(0.5, 0.42, 0.24), 0.55 * (1.0 - vigour));
                    vec3 green = mix(texel.rgb, lawn * (lit / 0.38), 0.4);
                    vec3 strawy = vec3(0.66, 0.58, 0.34) * (lit / 0.38);
                    // Dark where the blades crowd at the root, light at the tip where the sun gets to them.
                    float risen = 0.8 + 0.5 * up;
                    if (form < 2.5) {
                        // A blade. It is folded along its middle, a shallow V: the rib is a
                        // dark line, and one half is turned a little more to the light than
                        // the other. Its tip is often scorched, and some blades are going over
                        // to straw from the tip down.
                        float rib = smoothstep(0.0, 0.22, abs(across));
                        float fold = 1.0 + 0.07 * across * (ownB > 0.5 ? 1.0 : -1.0);
                        float dry = smoothstep(0.86 - 0.4 * (1.0 - vigour), 1.0, ownC);
                        float scorched = smoothstep(0.55 + 0.4 * ownB, 1.0, up) * step(0.45, ownA);
                        vec3 blade = green;
                        if (form > 1.5) {
                            // Dead: bleached, greyer where it has lain longest.
                            blade = mix(strawy, vec3(0.5, 0.47, 0.38) * (lit / 0.38), ownA * 0.7);
                            stripGloss = 0.3;
                        } else if (form > 0.5) {
                            // Coarse grass is a bluer, darker green than turf, with pale veins along it.
                            blade = green * vec3(0.8, 0.95, 0.92) * (0.9 + 0.1 * sin(across * 14.0));
                        }
                        blade = mix(blade, strawy, max(dry * 0.75, scorched * 0.6));
                        texel.rgb = blade * (0.9 + 0.1 * rib) * fold * risen * (0.84 + 0.32 * ownB);
                    } else if (form < 3.5) {
                        // A stem in seed: green at the foot and straw from the middle up.
                        texel.rgb = mix(green * 0.9, strawy, smoothstep(0.15, 0.7, up)) * (0.75 + 0.4 * up) * (1.0 - 0.25 * abs(across));
                        stripGloss = 0.5;
                    } else if (form < 4.5) {
                        // A spike: a tight head rounded at both ends, its grains in slanting
                        // rows, with a fringe of bristles standing out all round on the kinds
                        // that have them.
                        float body = 0.5 * sqrt(max(sin(3.14159 * pow(clamp(up, 0.0, 1.0), 0.85)), 0.0));
                        float bristled = step(0.4, ownA);
                        float hair = step(0.62, fract(up * 26.0 - abs(across) * 3.0 + ownB)) * step(abs(across), min(body * 1.9, 0.98)) * bristled;
                        if (abs(across) > body && hair < 0.5) {
                            discard;
                        }
                        float grainy = 0.78 + 0.22 * sin((up * 30.0 + across * 5.0) * 2.0);
                        vec3 head = mix(vec3(0.6, 0.55, 0.3), vec3(0.52, 0.44, 0.36), ownC) * (lit / 0.38);
                        head = mix(head, green * 1.1, 0.5 * step(0.7, ownB));
                        texel.rgb = abs(across) > body ? head * 1.25 : head * grainy * (1.0 - 0.45 * abs(across) / max(body, 0.01));
                        stripGloss = 0.2;
                    } else if (form < 5.5) {
                        // A panicle: spikelets hanging by turns to either side of a hair-thin
                        // stalk, larger and further out towards the middle of the head.
                        float rows = 11.0;
                        float row = floor(up * rows), within = fract(up * rows);
                        float side = mod(row, 2.0) < 0.5 ? 1.0 : -1.0;
                        float reachOut = (0.25 + 0.6 * sin(3.14159 * clamp(up * 0.9 + 0.08, 0.0, 1.0))) * (0.7 + 0.3 * grain(vec2(row, floor(own * 91.0))));
                        vec2 from = vec2(across * side - reachOut, (within - 0.5) * 0.9);
                        float spikelet = length(vec2(from.x + from.y * 0.5, from.y) * vec2(1.0 / 0.13, 1.0 / 0.42));
                        float twig = step(abs(within - 0.5 - (across * side - reachOut) * 0.5), 0.07) * step(0.0, across * side) * step(across * side, reachOut);
                        float stalk = step(abs(across), 0.045);
                        if (spikelet > 1.0 && twig < 0.5 && stalk < 0.5) {
                            discard;
                        }
                        vec3 head = mix(vec3(0.68, 0.6, 0.36), vec3(0.56, 0.5, 0.4), ownC) * (lit / 0.38);
                        texel.rgb = spikelet <= 1.0 ? head * (1.15 - 0.4 * spikelet) : head * 0.8;
                        stripGloss = 0.2;
                    } else {
                        // Clover: three round leaflets about the end of the stem, each with the
                        // pale chevron across it.
                        vec2 p = vec2(across, up * 2.0 - 1.0);
                        float round = atan(p.y, p.x) + own * 6.2831853;
                        float third = mod(round, 2.0943951) - 1.0471976;
                        float far = length(p);
                        vec2 leaf = vec2(far * cos(third) - 0.5, far * sin(third));
                        float edge = length(leaf * vec2(1.0 / 0.44, 1.0 / 0.4));
                        if (edge > 1.0) {
                            discard;
                        }
                        float chevron = (1.0 - smoothstep(0.0, 0.1, abs(leaf.x + 0.02 + abs(leaf.y) * 0.55))) * 0.3;
                        float vein = 0.94 + 0.06 * smoothstep(0.0, 0.04, abs(leaf.y));
                        texel.rgb = mix(green * vec3(0.56, 0.8, 0.54), vec3(0.6, 0.72, 0.5) * (lit / 0.38), chevron) * vein * (1.1 - 0.3 * edge * edge);
                        up = 0.6;
                        stripGloss = 0.6;
                    }
                    texel.a = 1.0;
                    bladeUp = up;
                    bladeTurn = ownB;
                }
                if (grass && !strip) {
                    // A card of long grass far off: blades side by side along it, each its own height, width,
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
                    // Grass grows in clumps a hand or two across, taller in the middle of one.
                    float clump = patches(vWorld.xy * 2.7 + 5.0);
                    float tall = (0.25 + 0.75 * kind * kind) * (0.72 + 0.28 * clump);
                    // A blade bows over further the higher up it, to one side or the other.
                    float bow = (grain(vec2(blade, 3.0)) - 0.5) * 1.3 * up * up;
                    float reach = up / max(tall, 0.001);
                    // No blade is narrower at its root than the narrowest here, which is nearly
                    // three quarters of the widest: a finer one would be under a pixel sooner.
                    float wide = 0.36 + 0.14 * grain(vec2(blade, 17.0));
                    // The tallest have gone to seed: the top quarter of such a blade is a head,
                    // broader than the stalk under it and the colour of straw.
                    float head = kind > 0.88 ? sin(3.14159 * clamp((reach - 0.74) / 0.26, 0.0, 1.0)) : 0.0;
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
                            // A flower: petals round a heart, on a stalk. They are small, and
                            // from far off would be specks that come and go, so with distance
                            // fewer are drawn. Which step of it a flower is left out from, head
                            // and stalk together, its card's coordinate up says, in the twos
                            // above the 0 to 1 it runs over: Flora.FLOWER_THINNINGS of them.
                            float thinned = floor((vUv.y + 0.5) / 2.0);
                            q.y = clamp(vUv.y - 2.0 * thinned, 0.0, 1.0);
                            if ((length(uEye - vWorld) - uRangeFrom) > 9.0 + 5.0 * (thinned + 0.5) / 8.0) {
                                discard;
                            }
                            if (pick > 2.5) {
                                // The stalk is the middle quarter of its card, and never finer
                                // than a pixel: finer, it would be a row of dashes.
                                float off = abs(q.x - 0.5) * 2.0;
                                float thick = max(0.25, 0.6 * fwidth(off));
                                if (off > thick) {
                                    discard;
                                }
                                // Round, so darker to either side.
                                float side = off / thick;
                                texel = vec4(vec3(0.24, 0.4, 0.13) * (1.0 - 0.3 * side * side), 1.0);
                            } else {
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
                    }
                    small = false;
                }
                // The sprites were drawn for a view in which every level line runs two pixels
                // across for one down, so the edge of a door or a wall is a staircase of
                // two-pixel steps. Seen square on and close, the steps show as a saw edge. Taken
                // as the mean of two samples one texel apart across, each step is averaged with
                // the next and the edge comes out straight.
                vec2 halfTexel = vec2(0.5 * uTexel.x, 0.0);
                float cover = uTexel.x > 0.0 && !small && !crown && !core ? 0.5 * (texture2D(uTexture, vUv - halfTexel).a + texture2D(uTexture, vUv + halfTexel).a) : texel.a;
                // Water's sprite is a diamond stretched over its square with half its frame
                // clear, and the nine samples of a far pixel that reach past the square's edge
                // land in that clear: their mean came out either side of the cut from one pixel
                // to the next, holes all over far water. A far pixel of water is there if any
                // of its samples is solid, which keeps the shape of a piece of shore.
                if (small && vKind > 3.5 && !grass) {
                    cover = mostSolid;
                }
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
                    thrown = min(1.0, 1.6 * (0.13 + 0.87 * pow(max(1.0 - abs(dot(face, sight)), 0.0), 5.0)));
                    float through = mix(mix(0.9, 0.5, outside), 0.92, uDark);
                    glow = lamplit ? outside * uDark : 0.0;
                    veil = thrown + (1.0 - thrown) * (1.0 - through);
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
                // The game paints the shadow a thing throws on the ground into the thing's
                // sprite: black, a fifth covered. Stood up with the thing as a card, or wrapped
                // on its box, that shadow was a black sheet under every chair and bench. The
                // picture throws shadows of its own. Black that thin with nothing thicker
                // a texel to either side of it or two texels off cornerwise is such a shadow:
                // the soft edge of a black line has the line beside it. Far off, where the pixel is the mean of nine samples,
                // a black line among them is one sample that is solid, and a shadow has none.
                if (uLaid < 0.5 && uTexel.x > 0.0 && glazed < 0.5 && !(vMaterial > 15.5 && vMaterial < 51.5) && texel.a < 0.27
                        && max(texel.r, max(texel.g, texel.b)) < 0.04) {
                    float thickest = max(mostSolid, 0.0);
                    for (int i = 0; i < 8 && mostSolid < 0.0; i++) {
                        vec2 off = (i == 0 ? vec2(2.0, 2.0) : i == 1 ? vec2(-2.0, 2.0) : i == 2 ? vec2(2.0, -2.0) : i == 3 ? vec2(-2.0, -2.0)
                                : i == 4 ? vec2(1.0, 0.0) : i == 5 ? vec2(-1.0, 0.0) : i == 6 ? vec2(0.0, 1.0) : vec2(0.0, -1.0)) * uTexel;
                        thickest = max(thickest, texture2D(uTexture, vUv + off).a);
                    }
                    if (thickest < 0.27) {
                        discard;
                    }
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
                // Whether it is the glass of a television that is on, which shows a picture by its own light.
                bool showing = false;
                // A model is solid all over: what follows gives it its colour and leaves no pixel
                // of it out, so the pass that keeps only depth goes round it.
                if (model && !depthOnly) {
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
                    if (dyed && surface > 9.5 && surface < 10.5) {
                        showing = true;
                        own = texel.rgb;
                    } else if (dyed && surface > 15.5 && surface < 16.5) {
                        // A picture in its frame is the sprite's own painting of it. Where the
                        // sprite has nothing painted, at its ragged edge, the pane is the board
                        // behind a picture.
                        // One of the mod's own pictures on a board with no moulding of its own is
                        // laid a little inside the board's edge, and a place past the picture's edge
                        // is the frame round it. A sprite's painting never looks outside its texture.
                        pictured = true;
                        bool round = vUv.x < -0.002 || vUv.x > 1.002 || vUv.y < -0.002 || vUv.y > 1.002;
                        own = round ? vec3(0.33, 0.22, 0.15) : texel.a > 0.5 ? texel.rgb : vec3(0.2, 0.17, 0.14);
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
                        float sum = 0.0, counted = 0.0;
                        for (int i = 0; i < 9; i++) {
                            int row = i / 3;
                            vec4 near = texture2D(uTexture, vUv + vec2(float(i - 3 * row) - 1.0, float(row) - 1.0) * 6.0 * uTexel);
                            tap[i] = near.rgb;
                            tone[i] = near.a > 0.5 ? dot(near.rgb, vec3(0.299, 0.587, 0.114)) : -1.0;
                            sum += max(tone[i], 0.0);
                            counted += step(0.0, tone[i]);
                        }
                        if (counted > 0.5) {
                            float mean = sum / counted - 0.002;
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
                } else if (!model && cover < (upright ? min(uCut, 0.1) : uCut)) {
                    discard;
                }
                // A tree is drawn as the game draws it in its own view: inside the game's window
                // (see CanopyWindow) a tree the game fades shows as much of itself as the game
                // draws of it, the rest of its pixels left out, and the whole tree goes by its
                // own square. The mod's trees are their crowns, boles and ivy, the game's are
                // cards of its plant sheets, and a bush is none.
                bool tree = vRoot.x >= 0.0 && !(vMaterial > 84.5 && vMaterial < 85.5);
                if (tree && uCanopy.z > 0.0 && (inCanopy(uCanopy.xy, uCanopy.zw, CANOPY_EDGE_FROM, CANOPY_EDGE_TO)
                        || uCanopyAim.z > 0.0 && inCanopy(uCanopyAim.xy, uCanopyAim.zz, CANOPY_AIMED_FROM, CANOPY_AIMED_TO))) {
                    float shown = 1.0;
                    for (int i = 0; i < CANOPY_TREES; i++) {
                        if (i >= uFadedCount) {
                            break;
                        }
                        if (all(lessThan(abs(uFaded[i].xy - vRoot), vec2(0.25)))) {
                            shown = uFaded[i].z;
                            break;
                        }
                    }
                    if (grain(gl_FragCoord.xy + vec2(613.0, 211.0)) >= shown) {
                        discard;
                    }
                }
                // What else stands between the overhead eye and the viewer and is no part of a
                // building is opened in a round window about them, where the game lets its own
                // picture of such a thing be seen through: a cupboard, a fridge, a hedge. A
                // building in the way is the game's to open: it leaves out the floors over the
                // viewer's and cuts walls away, and the picture does as it does below.
                bool ofModel = vMaterial > 15.5 && vMaterial < 51.5;
                bool standing = vKind > 1.5 && vKind < 3.5;
                if (!tree && (ofModel || standing) && uHole.z > 0.0 && vWorld.z > uHoleUp && dot(vWorld, uHoleFrom.xyz) < uHoleFrom.w
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
                // As the game's own view, the walls cut down to a low course are the ones the
                // game cuts away. On the camera's way to the viewer's eye they are the walls of
                // the viewer's building that stand between the two.
                bool marked = uCutMarked.z > 0.0;
                if (vWorld.z > uCutAbove && (marked ? vWorld.z < uCutMarked.z : dot(vWorld.xy, uCutNear.xy) > uCutNear.z && boxed)) {
                    if ((ofModel ? vHung > 0.5 : !standing) && (!marked || cutAway(vWorld, vNormal, ofModel))) {
                        discard;
                    }
                }
            """;

    /** The rest of main: the whole of it in one constant is longer than a class file can hold. */
    static final String WORLD_FRAGMENT_MAIN_REST = """
                vec3 toEye = uEye - vWorld;
                // What stands as a card and is not water: see card below. Nor an overhead wire,
                // whose upright strip is all there is of it from the side.
                if (vKind > 1.5 && !(vKind > 3.5 && !grass) && !grass && !grown && !crown && !core && length(vNormal.xy) > 0.1 * length(vNormal)
                        && !(vMaterial > 11.5 && vMaterial < 12.5)) {
                    // A card seen edge on is a streak of smeared leaves. It thins out and goes
                    // before it gets there: the other cards of the fan are what is seen then.
                    float square = abs(dot(normalize(vNormal.xy + vec2(1e-5)), normalize(toEye.xy + vec2(1e-5))));
                    if (square < 0.2 + 0.25 * grain(gl_FragCoord.xy)) {
                        discard;
                    }
                }
                // Where the far land lies behind it, the built world thins out into it.
                if (thinnedOut(-toEye.xy)) {
                    discard;
                }
                if (uUnder.z > 0.0 && (vKind > 3.5 && !grass || dot(uUnder.xyz, vWorld) < uUnder.w)) {
                    discard;
                }
                // Ground spreading over its neighbour thins out towards its edge, and the edge
                // is ragged: tufts and patches, not a ruled fade.
                float shown = 1.0;
                if (uLaid > 0.5 && !grass && vMaterial < 56.5 && vCover < 0.999) {
                    float ragged = patches(vWorld.xy * 1.9) * 0.6 + patches(vWorld.xy * 6.1 + 17.0) * 0.4 - 0.5;
                    shown = smoothstep(0.38, 0.62, vCover + ragged * 3.0 * vCover * (1.0 - vCover));
                    // Where the spread ground does not show, nothing is drawn: what is laid writes
                    // depth, so a pixel of it left in would still hide whatever is laid on the
                    // same ground after it, and where nothing of the built world was drawn beneath
                    // it, far off, it kept the sky and the far land out and showed as a black dot.
                    if (shown < 0.03) {
                        discard;
                    }
                }
                // A plant that stands as a card under the open sky is in lying snow as far up as
                // the snow lies round it, and what of it is lower is left out, so the snow on the
                // ground behind shows in its place. Its vertices take the wind by how high they
                // stand over its foot, so the pixel stands as high as what it takes over how
                // fast that grows up the card. Less snow lies under trees, as on the ground: the
                // sky over the place is asked only of what is low enough to be under the deepest snow.
                // Only the game's own sprites and the bushes that stand in for them: a frond, a leaf
                // or a flower Flora builds as a card sways by how far it is along itself, not by how
                // high it stands. The depth and the sky are asked at the height of the plant's foot under the
                // pixel, and the sky only where the open sky's depth would bury it.
                if (uPlantSnow > 0.0 && vKind > 2.5 && vKind < 3.5 && vCover < 0.9995 && (vMaterial < 55.5 || vMaterial > 62.5 && vMaterial < 63.5)) {
                    float takes = -dot(coverScreen, riseScreen) / max(dot(riseScreen, riseScreen), 1e-12);
                    float rise = (1.0 - vCover) / max(takes, 1e-4);
                    vec3 foot = vec3(vWorld.xy, vWorld.z - rise);
                    vec3 footToEye = uEye - foot;
                    float away = length(footToEye);
                    float footPixel = uPixel * away * away / max(abs(footToEye.z), 0.1);
                    // snowShelter is still 0 here, so the first snowAround is the most the plant could be under, and the sky is asked only within it.
                    if (rise < SNOW_DEPTH * uSnowLying && rise < snowAround(vPlace.xy, footPixel)) {
                        snowShelter = (1.0 - skyOverAt(foot + vec3(0.0, 0.0, 0.1), max(away - uRangeFrom, 0.0))) / 0.62;
                        if (rise < snowAround(vPlace.xy, footPixel)) {
                            discard;
                        }
                    }
                }
                // Every pixel that is left out has been left out by here. Asked only where
                // things are, the program stops: what follows is the light, which is most of
                // its work. See Pipeline.drawWorld.
                if (depthOnly) {
                    gl_FragColor = uOutline;
                    return;
                }
                float away = length(toEye);
                vec3 view = toEye / away;
                float range = max(away - uRangeFrom, 0.0);
                // A sprite is one sheet with no back: the side being looked at is the lit one.
                vec3 n = normalize(vNormal);
                // A sprig of a crown faces the way the crown's outside does there, whichever side of its card is seen.
                if (dot(n, view) < 0.0 && !crown) {
                    n = -n;
                }
                // A sprig is a card, and the leaves on it are not: each is turned its own way
                // out of the card, domed, curled over at its edge and ribbed, and where two
                // lie one on the other less light comes through. The sprigs' second picture
                // has that for every pixel (pack.py, form): the slope across and down the
                // picture, and how much light passes.
                vec3 leafFace = n;
                float leafThrough = 1.0;
                if (crown) {
                    vec3 shape = texture2D(uSprigForm, sprigAt).rgb;
                    vec3 acrossX = dFdx(vPlace), acrossY = dFdy(vPlace);
                    vec3 flatOf = normalize(cross(acrossX, acrossY) + vec3(1e-12));
                    if (dot(flatOf, view) < 0.0) {
                        flatOf = -flatOf;
                    }
                    // Which ways in the world the picture's across and down run on this card.
                    vec3 r1 = cross(acrossY, flatOf), r2 = cross(flatOf, acrossX);
                    vec2 atX = dFdx(sprigAt), atY = dFdy(sprigAt);
                    vec3 right = r1 * atX.x + r2 * atY.x, down = r1 * atX.y + r2 * atY.y;
                    vec2 tip = shape.rg * 2.0 - 1.0;
                    leafFace = normalize(flatOf * sqrt(max(1.0 - dot(tip, tip), 0.05))
                            + (right * tip.x + down * tip.y) * inversesqrt(max(dot(right, right), dot(down, down)) + 1e-20));
                    leafThrough = shape.b;
                }
                // A tuft of grass is lit as the ground it grows from is, from above. Lit as the
                // card it is drawn on, each tuft is a flat pane brighter or darker than the
                // next, and a lawn is a heap of green blocks.
                if (grass) {
                    n = normalize(vec3(n.xy * 0.2, 1.0));
                }
                // A blade that is its own strip leans a little to the light as it really faces, so each stands out from the next.
                if (strip) {
                    n = normalize(vec3((dot(stripFace, view) < 0.0 ? -stripFace.xy : stripFace.xy) * 0.3, 1.0));
                }
                n = normalize(n - dent - bent);
                if (lean != 0.0) {
                    // A board's eased edge turns the surface along the wall, towards the joint.
                    n = normalize(n + vec3(vNormal.y, -vNormal.x, 0.0) / max(length(vNormal.xy), 0.001) * (0.55 * lean));
                }
                if (climb != 0.0) {
                    n = normalize(n + vec3(0.0, 0.0, 0.55 * climb));
                }

                // Open ground. The game has a handful of squares of each ground and lays them
                // side by side, so a road or a yard is the same square over and over. Real
                // ground is not: it is paler here and darker there over many paces, and close
                // to it is made of stones and grit that the sun catches. Both are worked out
                // from where the ground is on the map, so they never repeat and they stay put.
                float grit = 0.0;
                // How much of the ground's depth relieve has drawn: so much less is left to the lumps made up below.
                float raisedGround = 0.0;
                if (uOwnTextures > 0.5 && !model && !litter && vKind < 1.5 && vKind > 0.5 && n.z > 0.8) {
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
                    // Earth only where the game says the ground is earth: what is brown may as well be planks.
                    float earth = vMaterial > 3.5 && vMaterial < 51.5 ? 1.0 - stony - green : 0.0;
                    vec2 oneAcross = dFdx(one), oneDown = dFdy(one), twoAcross = dFdx(two), twoDown = dFdy(two);
                    // Stones stand up out of a drive and clods out of earth, a finger deep; tarmac
                    // is pitted, a seventh as deep. The place seen among them is found by relieve.
                    if (dark + loose + earth > 0.02) {
                        reliefShare = vec3(dark * 0.14, loose, earth * 0.8);
                        reliefOne = one;
                        reliefTwo = two;
                        reliefOneAcross = oneAcross;
                        reliefOneDown = oneDown;
                        reliefTwoAcross = twoAcross;
                        reliefTwoDown = twoDown;
                        reliefWhich = which;
                        vec2 moved = relieve(5.0, vec3(1.0, 0.0, 0.0), vec3(0.0, 1.0, 0.0), n, 0.022);
                        one += moved * 0.83;
                        two += vec2(moved.x * 0.8 - moved.y * 0.6, moved.x * 0.6 + moved.y * 0.8) * 0.37;
                        n = normalize(n - reliefBend);
                        raisedGround = reliefNear;
                    }
                    vec3 grain3 = dark * uMaterialGain.x * mix(texture2DGradARB(uAsphalt, one, oneAcross, oneDown).rgb, texture2DGradARB(uAsphalt, two, twoAcross, twoDown).rgb, which)
                            + loose * uMaterialGain.y * mix(texture2DGradARB(uGravel, one, oneAcross, oneDown).rgb, texture2DGradARB(uGravel, two, twoAcross, twoDown).rgb, which)
                            + paved * vec3(0.95 + 0.1 * patches(p * 7.3 + 13.0))
                            + vec3(1.0 - stony - green - earth) + earth * uMaterialGain.z * mix(texture2DGradARB(uSoil, one, oneAcross, oneDown).rgb, texture2DGradARB(uSoil, two, twoAcross, twoDown).rgb, which)
                            + green * uMaterialGain.w * mix(texture2DGradARB(uLawn, one, oneAcross, oneDown).rgb, texture2DGradARB(uLawn, two, twoAcross, twoDown).rgb, which);
                    // The picture's light and dark go over the game's colour everywhere. Where
                    // the ground is stone, which has no colour of its own to lose, the picture's
                    // colour comes too, as bright as the game painted the spot: tarmac and gravel
                    // are then the real thing and not a painting of it. Grass keeps the game's
                    // colour, which is the season's.
                    float fine = dot(grain3, vec3(0.299, 0.587, 0.114));
                    float bright = dot(texel.rgb, vec3(0.299, 0.587, 0.114));
                    texel.rgb = mix(texel.rgb * mix(1.0, fine, 0.9), grain3 * bright, 0.8 * (stony - paved)) * (1.0 - 0.35 * reliefLow);
                    if (paved > 0.0) {
                        // Two slabs to a tile, each row laid half a slab along from the last,
                        // no two quite the same grey, with a dark joint between them. Joints
                        // finer than a pixel are left out: they would only crawl.
                        vec2 slab = p * 2.0;
                        slab.x += 0.5 * mod(floor(slab.y), 2.0);
                        vec2 toJoint = min(fract(slab), 1.0 - fract(slab)) * 0.5;
                        vec2 soft = fwidth(p);
                        float seen = paved * (1.0 - smoothstep(0.06, 0.2, max(soft.x, soft.y)));
                        float joint = max(1.0 - smoothstep(0.008, 0.008 + soft.x + 1e-6, toJoint.x), 1.0 - smoothstep(0.008, 0.008 + soft.y + 1e-6, toJoint.y));
                        float tone = grain(floor(slab));
                        texel.rgb *= (1.0 + (tone - 0.5) * 0.08 * seen) * (1.0 - 0.3 * joint * seen);
                    }
                    float near = 1.0 - smoothstep(5.0, 16.0, range);
                    if (near > 0.0) {
                        float e = 0.012;
                        float here = stones(p), east = stones(p + vec2(e, 0.0)), south = stones(p + vec2(0.0, e));
                        // Tarmac and concrete are close to flat: only loose stone stands up in lumps.
                        float depth = (0.05 - 0.035 * stony + 0.135 * loose) * near * (1.0 - 0.85 * raisedGround);
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
                // So is what the mod builds plain, a ceiling, a soffit and the sides of an opening
                // through a wall, which is pale, and the walls of its quays and its gutters,
                // which are dark.
                if (vMaterial > 8.5 && vMaterial < 10.5) {
                    texel.rgb = vMaterial < 9.5 ? vec3(0.86, 0.86, 0.83) : vec3(0.2, 0.17, 0.14);
                }
                // A kerb is cast in lengths: pale concrete, a little uneven in its grey, with
                // a joint where one length ends and the next begins. It goes round corners,
                // so how far along it a point is cannot be had from the way it faces there:
                // the joints are where the map's own lines cross it, by whichever of the two
                // ways it runs more along (the texel's first number, see KerbBuilder), and its
                // grey is taken from where it stands. The texel's second number runs from 0 at
                // the foot to 1 at the top of the face, 1.5 round the arris and 2 on the top.
                // Road dirt gathers at the foot, the arris is worn paler and chipped, and the
                // top is weathered in patches.
                if (vMaterial > 63.5 && vMaterial < 64.5) {
                    float along = vUv.x < 0.5 ? vWorld.y : vWorld.x;
                    float poured = patches(vWorld.xy * 2.3 + vec2(0.0, vWorld.z * 11.0)) * 0.6 + patches(vWorld.xy * 19.0 + vec2(3.0, vWorld.z * 37.0 + 3.0)) * 0.4;
                    float toJoint = abs(fract(along / 1.5) - 0.5) * 1.5;
                    float joint = (1.0 - smoothstep(0.012, 0.012 + fwidth(along) + 1e-6, 0.75 - toJoint)) * (1.0 - smoothstep(0.06, 0.2, fwidth(along)));
                    float dirt = (1.0 - smoothstep(0.0, 0.45, vUv.y)) * (0.6 + 0.4 * patches(vec2(along * 3.1, 7.0)));
                    float arris = smoothstep(1.0, 1.25, vUv.y) * (1.0 - smoothstep(1.75, 2.0, vUv.y));
                    float chip = smoothstep(0.78, 0.86, patches(vec2(along * 9.0, vUv.y * 2.0 + 11.0))) * (1.0 - smoothstep(0.02, 0.05, fwidth(along)));
                    vec3 stone = vec3(0.8, 0.79, 0.75) * (0.9 + 0.2 * poured) * (1.0 + 0.08 * arris);
                    stone = mix(stone, vec3(0.38, 0.35, 0.31), 0.55 * dirt);
                    stone *= 1.0 - 0.14 * smoothstep(1.9, 2.0, vUv.y) * smoothstep(0.4, 0.8, patches(vWorld.xy * 6.0 + 17.0));
                    texel.rgb = stone * (1.0 - 0.35 * joint) * (1.0 - 0.3 * chip * smoothstep(0.8, 1.2, vUv.y));
                }
                if (uOwnTextures > 0.5 && (roofing || vMaterial > 1.5 && vMaterial < 3.5 || vMaterial > 5.5 && vMaterial < 8.5)) {
                    // The place is taken within sixty tiles, not on the whole map: ten thousand
                    // tiles out a number holds a thousandth of a tile and no finer, and a grain
                    // read at such places is ruled with stripes a pixel or two wide. Every
                    // picture here repeats a whole number of times in sixty tiles.
                    bool level = abs(n.z) > 0.7;
                    // Shingles are laid in courses along the eaves: on a slope that falls along x the picture is turned, its courses running along y.
                    bool turned = roofing && level && abs(n.x) > abs(n.y);
                    vec2 across = level ? (turned ? vPlace.yx : vPlace.xy) : vec2(dot(vPlace.xy, vec2(n.y, -n.x)), vPlace.z);
                    vec2 acrossX = dFdx(across), acrossY = dFdy(across);
                    // A roof's courses, the cracks of weathered planks and the seams of a floor have
                    // depth: see relieve. Not what stands as a card, a table's top or a fence's
                    // rail painted on a sheet: its outline is the sprite's, which would stay put.
                    float raisedKind = vKind > 1.5 ? 0.0 : roofing ? 2.0 : vMaterial > 2.5 && vMaterial < 3.5 ? 3.0 : vMaterial > 6.5 && vMaterial < 7.5 ? 4.0 : 0.0;
                    if (raisedKind > 0.5) {
                        reliefBase = across;
                        across += relieve(raisedKind, level ? (turned ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)) : vec3(n.y, -n.x, 0.0), level ? (turned ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)) : vec3(0.0, 0.0, 1.0), n,
                                raisedKind < 2.5 ? 0.014 : raisedKind < 3.5 ? 0.0015 : 0.01);
                        n = normalize(n - reliefBend);
                        texel.rgb *= 1.0 - 0.4 * reliefLow;
                    }
                    float fine = roofing ? laid(uShingles, across * 0.55, acrossX * 0.55, acrossY * 0.55, 0.1, false, 33.0, 0.14).x * uSurfaceGain.x
                            : vMaterial < 2.5 ? dot(texture2D(uPaint, across * 0.8).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.y
                            : vMaterial < 3.5 ? (vBoards > 0.5 ? floorBoards(across, acrossX, acrossY).x
                                    : laid(uWood, across * 0.45, acrossX * 0.45, acrossY * 0.45, 0.44, false, 27.0, level ? 0.0 : 0.14).x) * uSurfaceGain.z
                            : vMaterial < 6.5 ? dot(texture2DArray(uBark, vec3(across * vec2(1.6, 0.8), 0.0)).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.x
                            : vMaterial < 7.5 ? laid(uPlanks, across * 0.7, acrossX * 0.7, acrossY * 0.7, 0.648, true, 42.0, 0.14).x * uTimberGain.y
                            : dot(texture2D(uCarpet, across * 1.6).rgb, vec3(0.299, 0.587, 0.114)) * uTimberGain.z;
                    float much = roofing ? 0.85 : vMaterial < 2.5 ? 0.3 : vMaterial < 3.5 ? 0.7 : 0.8;
                    // What is painted white is paint over the wood: its grain barely shows.
                    if (vMaterial > 2.5 && vMaterial < 3.5) {
                        much *= 1.0 - 0.75 * smoothstep(0.6, 0.85, min(texel.r, min(texel.g, texel.b)));
                    }
                    texel.rgb *= mix(1.0, fine, much);
                }
                // Wallpaper with a figure: a sprig printed over and over, each row half a step along
                // from the one below, a little darker than the paper and a little greener or redder.
                // Further off than the print can be drawn, the paper is the mean of it.
                if (vMaterial > 69.5 && vMaterial < 70.5) {
                    vec2 run = vec2(dot(vPlace.xy, vec2(n.y, -n.x)), vPlace.z) / 0.17;
                    float row = floor(run.y);
                    vec2 cell = vec2(fract(run.x + 0.5 * mod(row, 2.0)), fract(run.y)) - 0.5;
                    float stem = (1.0 - smoothstep(0.012, 0.03, abs(cell.x + 0.06 * sin(cell.y * 9.0)))) * step(abs(cell.y + 0.05), 0.3);
                    float bloom = 1.0 - smoothstep(0.07, 0.1, length(cell - vec2(0.0, 0.27)) * (1.0 + 0.25 * cos(5.0 * atan(cell.y - 0.27, cell.x))));
                    float leaves = 1.0 - smoothstep(0.035, 0.055, length(vec2((abs(cell.x) - 0.1) * 0.6 + 0.6 * (cell.y + 0.02), cell.y + 0.02 - 0.5 * (abs(cell.x) - 0.1))));
                    float drawn = 1.0 - smoothstep(0.08, 0.3, fwidth(run.x) + fwidth(run.y));
                    texel.rgb = mix(texel.rgb, texel.rgb * vec3(0.72, 0.84, 0.7), max(stem, leaves) * drawn);
                    texel.rgb = mix(texel.rgb, texel.rgb * vec3(1.0, 0.72, 0.76), bloom * drawn);
                    texel.rgb *= mix(0.97, 1.0, drawn) * mix(1.0, dot(texture2D(uPaint, run * 0.14).rgb, vec3(0.299, 0.587, 0.114)) * uSurfaceGain.y, 0.3);
                }
                // A curtain the picture has hung has folds of its own, and is its weave close to.
                if (vMaterial > 68.5 && vMaterial < 69.5) {
                    texel.rgb *= mix(1.0, wrapped(uCloth, vWorld, n, 23.0) * uClothGain, 0.22);
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
                bool card = !water && vKind > 1.5;
                float open = grass ? vKind - (strip ? 24.0 : 16.0) : water ? vKind - 4.0 : card ? vKind - 2.0 : vKind;
                // How much of the sky's light gets to the place. Under trees it is thinned and
                // green, not gone. A crown is the leaves the sky is seen through, not something under them.
                float skyOver = open > 0.5 && !crown ? skyOverAt(vWorld + n * 0.1, range) : 1.0;
                // Snow that has settled. It lies on whatever is under the open sky and faces up:
                // the ground, a roof, the top of a wall or a car, and it is a surface of its
                // own there, with a shape and a depth: see SnowShader. It comes in patches that
                // spread and join as more of it lies, and goes the same way. Grass stands up
                // through thin snow and is buried by deep, and a crown carries a dusting on
                // what of it faces the sky.
                float snowed = 0.0;
                // How much ground a pixel covers here: what is finer than that is not drawn.
                float snowFoot = max(length(dFdx(vPlace.xy)), length(dFdy(vPlace.xy)));
                if (uSnowLying > 0.0 && open > 0.5 && !water && !model && (uPlantSnow > 0.0 || !(strip || grass || crown || core || card))) {
                    // Less of it gets under a tree or the eaves of a house than falls in the open.
                    snowShelter = (1.0 - skyOver) / 0.62;
                    if (strip || grass) {
                        // A blade stands in the snow to the snow's depth and is white that far
                        // up. What shows over the snow is winter grass, bleached by the frost,
                        // with snow caught on a blade here and there.
                        float lies = snowCover(vPlace.xy, snowFoot);
                        float depth = lies * (0.15 + 0.6 * uSnowLying);
                        // A strip stands in the snow already, what is under it out of sight: see
                        // snowStood. A card of far grass cannot, and is white as far up as the snow lies.
                        float buried = strip ? 0.0 : (1.0 - smoothstep(depth - 0.1, depth + 0.1, bladeUp)) * min(lies * 4.0, 1.0);
                        float caught = lies * uSnowLying * smoothstep(0.55, 0.8, snowNoise(vPlace.xy * 14.0, vec2(840.0), 12.0).x) * snowDrawn(snowFoot, 14.0);
                        texel.rgb = mix(texel.rgb, vec3(1.15, 1.05, 0.8) * dot(texel.rgb, vec3(0.299, 0.587, 0.114)), 0.6 * lies);
                        snowed = clamp(max(buried, 0.85 * caught), 0.0, 1.0);
                        texel.rgb = mix(texel.rgb, SNOW_WHITE, snowed);
                    } else if (crown) {
                        snowed = snowOnSprig(n.z, leafFace.z, sprigSolid, grain(floor(vUv)), vMaterial > 74.5 && vMaterial < 76.5);
                        texel.rgb = mix(texel.rgb, SNOW_WHITE, snowed);
                    } else if (core) {
                        snowed = snowOnMass(n.z);
                        texel.rgb = mix(texel.rgb, SNOW_WHITE, snowed);
                    } else if (card) {
                        // A plant that stands as a card: its vertices take the wind by how high they are, and nothing else that is a card takes any.
                        float take = 1.0 - vCover;
                        if (take > 0.0005) {
                            // How far along the picture a tile up the card is, by the card's own slope on the screen.
                            float upPicture = dot(pictureScreen, riseScreen) / max(dot(riseScreen, riseScreen), 1e-12);
                            // How solid the picture is over the pixel: as far up as the snow on a top is thick, and far enough
                            // up that a gap between two leaves of one mass is not taken for the top of it. Neither looks
                            // further than a dozen texels: the next sprite on the game's sheet can be that close.
                            float band = uTexel.x > 0.0 ? texture2D(uTexture, vUv + vec2(0.0, clamp(upPicture * (0.03 + 0.12 * uSnowLying), -6.0 * uTexel.y, 6.0 * uTexel.y))).a : 1.0;
                            float clear = uTexel.x > 0.0 ? texture2D(uTexture, vUv + vec2(0.0, clamp(upPicture * 0.4, -12.0 * uTexel.y, 12.0 * uTexel.y))).a : 1.0;
                            // Leaves and not bark: of the game's own sprite, by its colour, and of Flora's none on a twig.
                            float most = max(texel.r, max(texel.g, texel.b));
                            bool twig = vMaterial > 60.5 && vMaterial < 61.5 && floor(vUv.x / 2.0) > 3.5;
                            float leafy = twig ? 0.0 : vMaterial > 5.5 && vMaterial < 6.5 || vMaterial < 0.5 ? smoothstep(0.25, 0.45, (max(texel.r, texel.g) - texel.b) / max(most, 0.001)) : 1.0;
                            // A clump is a few texels of the picture across, so it stays on the plant as the card turns, and round, not a square of texels.
                            float each = uTexel.x > 0.0 ? snowNoise(vUv / (uTexel * 5.0), vec2(4096.0), 31.0).x : 0.5;
                            snowed = snowOnCard(band, clear, smoothstep(0.05, 0.6, view.z), leafy, smoothstep(0.1, 0.36, dot(texel.rgb, vec3(0.299, 0.587, 0.114))), each);
                            texel.rgb = mix(texel.rgb, SNOW_WHITE, snowed);
                        }
                    } else {
                        // The way the surface itself faces: what it is made of has bent n by now, a stone at a time.
                        vec3 level = normalize(vNormal);
                        if (dot(level, view) < 0.0) {
                            level = -level;
                        }
                        // A kerb's face the snow has banked into a slope (see the world's vertex
                        // shader) is lit as a bank is shaped: level where it leaves the pavement's
                        // edge and where it meets the street, steepest between, so neither its top
                        // nor its foot shows as a crease. Its texel's second number runs from 0 at
                        // the foot to 1 at the top of the face; above that is the arris and the top. The slope is measured on vPlace, which holds a
                        // place on the map finer than vWorld does.
                        if (vMaterial > 63.5 && vMaterial < 64.5) {
                            vec3 facet = normalize(cross(dFdx(vPlace), dFdy(vPlace)));
                            facet = facet.z < 0.0 ? -facet : facet;
                            if (vUv.y <= 1.0 && facet.z > 0.15 && length(facet.xy) > 1e-4) {
                                float rise = 2.0 * vUv.y - 1.0;
                                float lean = length(facet.xy) / facet.z * 1.5 * (1.0 - rise * rise);
                                level = normalize(vec3(normalize(facet.xy) * lean, 1.0));
                            }
                        }
                        // Over grass and rough ground snow lies in lumps, over a road or a roof nearly smooth.
                        float most = max(texel.r, max(texel.g, texel.b));
                        float lumpy = 1.0 + 1.4 * smoothstep(0.1, 0.3, (texel.g - max(texel.r, texel.b)) / max(most, 0.001));
                        vec2 lain = vPlace.xy;
                        if (uRelief > 0.5 && level.z > 0.9) {
                            lain += snowSunk(lain, view, lumpy, 1.0 - smoothstep(6.0, 10.0, range));
                        }
                        // A bole, a limb and the ivy on a bole stay bare: the snow that stays on bark, a
                        // streak up the weather side or a line along a limb, reads at a distance as white
                        // paint and as an outline round the wood.
                        float up = vMaterial > 56.5 && vMaterial < 58.5 ? 0.0 : smoothstep(0.35, 0.8, level.z);
                        if (uOwnTextures > 0.5) {
                            snowed = up > 0.0 ? snowLaid(vPlace.xy, lain, lumpy, snowFoot, vWorld.z) * up : 0.0;
                            // The ground just clear of the lip is wet from it, and dark.
                            texel.rgb = mix(texel.rgb * (1.0 - 0.3 * snowMelt * up), snowWhite(), snowed);
                            // What the ground under it is made of no longer shapes the surface: the snow's own shape does.
                            n = normalize(mix(n, level, snowed) - vec3(snowForm.yz, 0.0) * snowed);
                        } else {
                            // The game's own snow, as Low draws it: patches of plain white with no shape, no marks and no glitter.
                            snowed = snowCover(vPlace.xy, snowFoot) * up;
                            texel.rgb = mix(texel.rgb, SNOW_WHITE, snowed);
                            n = normalize(mix(n, level, snowed));
                        }
                        grit *= 1.0 - snowed;
                    }
                }
                // A sprite is paint on a flat sheet. Its light and dark say where the thing it
                // shows stands proud and where it is sunk, the mortar between bricks, the gaps
                // between boards, the blades of grass, so the sheet is lit as if it were
                // raised where it is light. The sun then picks the surface out as it moves, and
                // a wall or a lawn stops being a photograph of one. It fades with distance,
                // where it would only shimmer.
                // Less of it where relieve has tilted the surface by its real height.
                float relief = water || grass || crown ? 0.0 : RELIEF * (1.0 - smoothstep(14.0, 38.0, range)) * (1.0 - 0.6 * timbered) * (1.0 - 0.75 * reliefNear) * (1.0 - snowed);
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
                float facing = clamp(dot(n, uLight), 0.0, 1.0);
                if (snowed > 0.0 && !card) {
                    // Snow lets the sun a little way in and passes it round: a slope of it turned from the sun goes dim by degrees and not at a line.
                    facing = mix(facing, clamp((dot(n, uLight) + 0.25) / 1.25, 0.0, 1.0), snowed);
                }
                // How much sun a leaf of a crown passes from its far side to the one seen.
                float leafBehind = 0.0;
                if (crown) {
                    // A crown is a round mass of leaves: bright where it is turned to the sun,
                    // going over to shade round its side, and never black, for the light
                    // comes through and round the leaves.
                    float round = 0.3 + 0.7 * pow(clamp(0.5 + 0.5 * dot(n, uLight), 0.0, 1.0), 1.5);
                    // And within that each leaf has the sun on the side seen, or edge on, or
                    // on its back and glowing through: no two leaves of a sprig alike.
                    float turned = dot(leafFace, uLight);
                    facing = round * (LEAF_SHADE + LEAF_SUN * max(turned, 0.0));
                    // The sun on a leaf's back reaches the underside of a crown as well as its sunny side.
                    leafBehind = (0.5 + 0.5 * round) * max(-turned, 0.0) * leafThrough;
                } else if (card) {
                    // A card stands in for something with depth and leaves, a bush or a tree, which
                    // the sun gets into from any side: it is lit whichever way the card faces.
                    facing = 0.5 + 0.4 * facing;
                }
                if (snowed > 0.0 && card) {
                    // Snow on a plant lies open to the sky whichever way the leaf under it is
                    // turned, and is lit as the snow on the ground beside it is.
                    facing = mix(facing, clamp((dot(normalize(vec3(n.xy * 0.35, 1.0)), uLight) + 0.25) / 1.25, 0.0, 1.0), snowed);
                }
                float sun = 0.0;
                float reached = 0.0;
                if (facing > 0.0) {
                    // The cards of a tree's fan stand in each other's shade and move in the wind,
                    // and their shadows on one another would flicker all over the tree. A card
                    // asks about the shade a little way towards the sun, past its own fan.
                    // Further off the surface than the soft edge is wide, or a level surface shades itself.
                    // A sprig on the side of its crown the sun is on asks further off still, clear of
                    // the crown it is the outside of: its shade is other trees', not its own.
                    vec4 from = vec4(vWorld + n * 0.16 + (card ? uLight * (0.9 + (crown ? CROWN_CLEAR * max(dot(n, uLight), 0.0) : 0.0)) : vec3(0.0)), 1.0);
                    // Everything in a room, both skins of a wall, and a wall under an eave or an awning. Out
                    // of doors a face turned up, the ground under a bush, would lose the offset that keeps it
                    // from shading itself, and a card stands in a crown whose top is over it: each is asked as
                    // it stands. Which way a face is turned is the face's own, not as relief has bent it pixel
                    // by pixel. Seen from overhead a building the game has opened has no roof in the maps, and
                    // its walls are asked as they stand too.
                    bool roofed = (open < 0.5 || abs(normalize(vNormal).z) < 0.5 && !card) && !water && uCutMarked.z <= 0.0;
                    float beneath = open < 0.5 ? 0.16 * uRoofPitch : 0.0;
                    vec3 c = (uNearMatrix * from).xyz * 0.5 + 0.5;
                    bool inNear = inside(c);
                    if (inNear) {
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
                        if (roofed) {
                            c = (uNearMatrix * vec4(sunAsked(from.xyz, soft, uSunTexelUp.x, beneath), 1.0)).xyz * 0.5 + 0.5;
                            // Lowered out past the near map's edge, it is asked of the far map.
                            inNear = inside(c);
                        }
                        if (inNear) {
                            reached = shadowAt(uShadowNear, c, soft);
                        }
                    }
                    if (!inNear) {
                        if (roofed) {
                            from.xyz = sunAsked(from.xyz, 1.6, uSunTexelUp.y, beneath);
                        }
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
                        // The snow on it passes none: in shade it is in shade.
                        reached = mix(mix(0.42, 1.0, reached), reached, snowed);
                    }
                    // A stone's foot is in the shade of the next stone, a groove of its own lip, and a
                    // lapped board under the foot of the one above.
                    reached *= reliefShade * lapSun;
                    sun = reached * facing;
                }
                // As the game's own view the picture has the roof and the floors above off a
                // building the game opens, and the sun falls straight into its rooms. The game
                // shows a room by its light alone: none in one the player has not seen into,
                // less in one they cannot see now. So what is under a roof has the sun there by
                // as much as the game's light for its square, and a room the game has dark is dark.
                if (uCutMarked.z > 0.0 && open < 0.5) {
                    sun *= pow(clamp(max(vColor.r, max(vColor.g, vColor.b)), 0.0, 1.0), 2.2);
                }

                // The game's light for the square is the light that is there without the sun
                // falling straight on it. Out of doors that light comes from the sky, more of it
                // onto what faces up and bluer than the sun. An upright face sees half the sky,
                // which is what lets the sun stand out on a wall; one turned down sees the
                // ground. A card stands for a plant the sky gets into from every side.
                vec3 fromSky = mix(vec3(1.0), vec3(0.72, 0.80, 0.95) * (card ? 0.72 + 0.28 * mix(n.z, 1.0, snowed) : 0.55 + (n.z > 0.0 ? 0.45 : 0.17) * n.z), open);
                if (crown) {
                    // A leaf turned up has more of the sky on it than one turned down.
                    fromSky *= mix(0.88 + 0.16 * leafFace.z, 1.0, snowed);
                }
                fromSky *= skyOver;
                // Which way the face itself is turned, towards the eye, as relief has not bent it.
                vec3 face = normalize(vNormal);
                face = dot(face, view) < 0.0 ? -face : face;
                float sided = card || crown ? 0.0 : (1.0 - abs(face.z)) * open;
                // A clear sky is brightest round the sun, so a face turned to the sun's side of it
                // has more of the sky's light than one turned to the deep blue opposite: two
                // shaded walls at a corner are not one tone. Cloud evens the sky out, and a high
                // sun has no side.
                fromSky *= 1.0 + SKY_SIDE * dot(normalize(face.xy + 1e-6), uLight.xy) * sided * (1.0 - 0.8 * uCover) * smoothstep(-0.05, 0.1, uLight.z);
                if (model) {
                    // The game's light for a square comes from no direction, and a sprite needs
                    // none: its shading is painted on. A model lit the same from every side is
                    // a flat shape of one colour. A room's light comes mostly from above, off
                    // the ceiling and down through the windows, and a little more from one side
                    // than the other, so a model takes most of it on what faces up.
                    fromSky *= mix(0.66 + 0.34 * n.z + 0.1 * dot(n.xy, vec2(0.6, -0.8)), 1.0, open);
                }
                // Interpolated corner-light differences can round below zero at a dark corner.
                vec3 given = pow(max(vColor.rgb, vec3(0.0)), vec3(2.2));
                vec3 held = vec3(0.0);
                if (uLantern.w > 0.0 && (open > 0.5 || uDaylightKept < 1.0)) {
                    // A light the character carries that throws no beam: a lantern, a candle.
                    // The game lights every square round it by where the square lies on the
                    // ground, the same from the grass to the top of a tree and from no side,
                    // and lit as the sky lights it a wood at midnight is a wood at noon. What
                    // a square within the light's reach has over the open ground's own light
                    // came from it: it falls from the hand, on the side that faces it, less
                    // with distance. A torch that throws a beam is not this: see torchAt.
                    float within = 1.0 - smoothstep(uLantern.w, uLantern.w + 3.0, length(vWorld.xy - uLantern.xy));
                    vec3 over = open > 0.5 ? max(given - pow(uOpenLight, vec3(2.2)), 0.0) * within : vec3(0.0);
                    // In the evening and before dawn the game still has every square at full
                    // daylight, in the light and out of it, and the picture has taken that
                    // daylight down (see Evening): nothing is left over for the lantern to be
                    // read from. By as much as the daylight is held back, it lights whatever
                    // is within its reach, with nothing known of what stands in its way.
                    vec3 lit = max(over, vec3((1.0 - uDaylightKept) * within));
                    vec3 to = vWorld - uLantern.xyz;
                    float far2 = dot(to, to);
                    vec3 dir = to * inversesqrt(max(far2, 0.0001));
                    float turned = card ? 0.75 : 0.2 + 0.8 * max(dot(n, -dir), 0.0);
                    held = lit * vec3(1.0, 0.93, 0.8) * (TORCH_GIVES * turned / (1.0 + 0.035 * far2));
                    given -= over;
                }
                // The game holds its daylight at full until dusk, when the picture's sun is
                // already down: see Evening. A square's light up to the open ground's is that
                // daylight, and what it has over it came from a lamp.
                // A room has less of it: see Evening.ROOM_DAYLIGHT.
                given -= min(given, pow(uOpenLight, vec3(2.2))) * (1.0 - mix(uRoomKept, uDaylightKept, clamp(open, 0.0, 1.0)));
                vec3 lightHere = given * fromSky + held + pow(uLightColor, vec3(2.2)) * (2.1 * sun);
                // Lightning lights all the sky, and what is under the open sky by it.
                lightHere += uFlashSky * (open * skyOver * (0.6 + 0.4 * max(n.z, 0.0)));
                // The ground in the sun throws its light back up at whatever stands on it. A
                // wall turned from the sun is lit by the street in front of it, and without
                // that the shaded side of every building is a flat dark slab. Only ground the sun
                // is on throws it: a wall turned from a low sun stands in its own long shadow and
                // has little from the ground, where at noon its shadow is short and the street
                // beyond it is bright. Asked of the air a pace and two paces out from the face.
                float groundLit = 1.0;
                if (sided > 0.5) {
                    groundLit = 0.5 * (sunOnAir(vWorld + vec3(face.xy, 0.0) * 1.0) + sunOnAir(vWorld + vec3(face.xy, 0.0) * 2.5));
                }
                lightHere += pow(uLightColor, vec3(2.2)) * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - abs(n.z)) * open * groundLit);
                vec3 color = pow(texel.rgb, vec3(2.2)) * lightHere;
                if (card) {
                    // A leaf lets the sun through. Looked at against the light, foliage and
                    // grass glow from behind, yellower than they are lit from in front, and
                    // that glow is most of what a tree looks like on a bright day.
                    float against = pow(max(dot(-view, uLight), 0.0), 1.5);
                    vec3 leaf = pow(texel.rgb, vec3(2.2)) * vec3(1.25, 1.2, 0.55);
                    // A blade lets most through near its tip, where it is thinnest.
                    // A crown's leaves each pass what their own turn and thickness let them, and
                    // a little more, as all foliage does, straight towards the eye.
                    float through = grass ? 0.55 + 0.9 * bladeUp : crown ? LEAF_GLOW_AGAINST * leafThrough : core ? 0.2 : 1.0;
                    // Snow on a leaf lets none of it through.
                    color += leaf * pow(uLightColor, vec3(2.2)) * ((2.8 * through * against + LEAF_GLOW * leafBehind) * reached * step(0.0, uLight.z) * (1.0 - snowed));
                    if (crown && reached > 0.0) {
                        // The upper skin of a leaf is waxed, and throws the sun off as a
                        // white glint where the leaf is turned just so. Far off, where a
                        // leaf is under a pixel, the glints would only crawl.
                        color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * step(0.0, uLight.z) * (1.0 - smoothstep(16.0, 34.0, range)) * (1.0 - snowed)
                                * glossed(leafFace, view, uLight, LEAF_ROUGH, 0.05));
                    }
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
                        if (strip) {
                            // The strip's own flat, rounded a little from one edge to the other as a folded blade is.
                            vec3 flatOf = dot(stripFace, view) < 0.0 ? -stripFace : stripFace;
                            vec3 breadth = normalize(cross(flatOf, vec3(0.0, 0.0, 1.0)) + vec3(1e-5, 0.0, 0.0));
                            facet = normalize(flatOf + breadth * (vBlade.x * 0.45));
                        }
                        color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * (strip ? stripGloss : 1.0) * (0.25 + 0.75 * bladeUp) * (1.0 - snowed) * glossed(facet, view, uLight, 0.42, 0.04));
                    }
                    // Deep in the plant the leaves stand in each other's light: darker towards
                    // the foot of a card than at its top.
                    color *= strip ? 1.0 : mix(0.72 + 0.28 * clamp(vCover < 1.0 ? 1.0 - vCover : 1.0, 0.0, 1.0), 1.0, snowed);
                }

                // Water, and ground that rain has wetted, show the sky: little of it looking
                // straight down, nearly all of it at a glancing angle, and the sun as a streak
                // of light. Wet ground is darker than dry, and holds the most water where it
                // pools, which is in a hollow here and there and not over a third of a street.
                // Snow that lies has no water standing on it: it is wet only where the ground
                // shows through.
                float shine = 0.0;
                float ripple = 0.0;
                if (water) {
                    shine = 1.0 - foam;
                    ripple = 0.05 * uRuffle;
                    color *= 0.55;
                } else if (!card && uWet > 0.0 && open > 0.5 && n.z > 0.85) {
                    float bare = uWet * (1.0 - snowed);
                    float pool = smoothstep(0.66, 0.8, patches(vWorld.xy * 0.45));
                    shine = bare * (0.1 + 0.7 * pool);
                    ripple = 0.012 * pool;
                    color *= 1.0 - 0.3 * bare;
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
                    // Blood is as glossy as it is wet.
                    if (vMaterial > 84.5 && vMaterial < 85.5) {
                        rough = mix(0.7, 0.3, bloodWet);
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
                    // Snow is pale and nearly as dull as anything is: taken for pale paint, a field of it had a sheen.
                    rough = mix(rough, 0.82, snowed);
                    rough = mix(rough, 0.18, shine);
                    color += pow(uLightColor, vec3(2.2)) * (2.1 * reached * glossed(n, view, uLight, rough, mirrors));
                    if (snowed > 0.0 && uLight.z > 0.0 && uOwnTextures > 0.5) {
                        // And it glitters: see snowGlints.
                        color += pow(uLightColor, vec3(2.2)) * (SNOW_GLINT * snowed * reached * snowGlints(vPlace.xy, n, view, uLight, sqrt(length(dFdx(vPlace.xy)) * length(dFdy(vPlace.xy)))));
                    }
                }
                color += pow(texel.rgb, vec3(2.2)) * beams(vWorld, n, card ? 1.0 : 0.0);
                color += pow(texel.rgb, vec3(2.2)) * flashLight(vWorld, n);
                // The game's light of a square stops at its walls, and the firelight here does not: it
                // falls only where the game has a square lit over the open ground's light, as it has
                // round a fire, and is given back from the way the fire is and as it flickers. Only a
                // fire the character can see from where they stand lights at all (Fires.gather), so
                // what is seen of a wall it lights is on the fire's side.
                vec3 lampLit = max(pow(max(vColor.rgb, vec3(0.0)), vec3(2.2)) - pow(uOpenLight, vec3(2.2)), 0.0);
                color += pow(texel.rgb, vec3(2.2)) * fireLight(vWorld, n) * clamp(dot(lampLit, vec3(0.3, 0.5, 0.2)) * 12.0, 0.0, 1.0);
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
                // A set that is on is seen by the light of its own tube, by day and in the dark
                // alike, and the room's light on its glass is next to nothing beside it.
                // What a set shows is a tube's picture: lines across it, a band of hum rolling up it, no colour
                // on a set that has none, and snow where what comes in is interference. glass is the place on
                // the glass, where vUv is the place on the part of the picture the glass shows.
                vec2 glass = (vUv - uTube.xy) / uTube.z + 0.5;
                float lineWide = fwidth(glass.y) * 240.0;
                if (showing) {
                    vec3 tube = texel.rgb;
                    if (uTube.w > 1.5) {
                        vec2 speck = floor(glass * vec2(213.0, 160.0)) + mod(floor(uSeconds * 30.0), 64.0) * vec2(17.0, 29.0);
                        float snow = fract(sin(dot(speck, vec2(12.9898, 78.233))) * 43758.5453);
                        tube = vec3(0.08 + 0.8 * snow * snow);
                    } else if (uTube.w > 0.5) {
                        tube = vec3(dot(tube, vec3(0.299, 0.587, 0.114)));
                    }
                    // The lines are left out where they are finer than the pixels that would show them.
                    float lines = 0.5 + 0.5 * cos(glass.y * 480.0 * 3.14159265);
                    tube *= 1.0 - 0.24 * lines * clamp(1.0 - 1.6 * lineWide, 0.0, 1.0);
                    tube *= 1.0 + 0.05 * sin((glass.y * 1.3 - uSeconds * 0.6) * 6.2831853);
                    color = 0.1 * color + pow(tube, vec3(2.2)) * 1.2;
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
                    // Far off a pixel takes in many ripples, and what it shows is their mean,
                    // which is level water. Tilted by whichever ripple the pixel's middle fell
                    // on, the far shore's reflection breaks up into dashes that crawl. On
                    // water a size of ripple goes as the stretch of water a pixel takes in,
                    // which seen at a glance is long, comes up to the size of one of them.
                    float spot = away * uPixel / max(dot(view, n), 0.02);
                    bool sized = water && uPlainWater < 0.5;
                    vec3 kept = sized ? 1.0 - smoothstep(0.25, 0.6, spot * RIPPLES) : vec3(1.0);
                    float level = swellOf(vWorld.xy, kept);
                    vec2 wave = vec2(swellOf(vWorld.xy + vec2(e, 0.0), kept) - level, swellOf(vWorld.xy + vec2(0.0, e), kept) - level) * (9.0 / e) * 0.1;
                    float near = sized ? 1.0 : 1.0 - smoothstep(14.0, 55.0, range);
                    vec3 surface = normalize(vec3(n.xy + wave * (ripple * near) + rings, n.z));
                    vec3 mirrored = reflect(-view, surface);
                    mirrored.z = abs(mirrored.z);
                    float glance = 0.03 + 0.97 * pow(1.0 - clamp(dot(view, surface), 0.0, 1.0), 5.0);
                    vec3 shows;
                    if (water && uWater.z > 0.0) {
                        // The ripples a pixel cannot show are still there: each throws the eye's
                        // line a little higher or lower than level water would, and what the
                        // pixel shows is all of those at once, the far bank drawn out towards
                        // the eye in a streak, the longer for the more the wind has roughened it.
                        // The streak is read at so many places along it, each from a picture
                        // as coarse as they are far apart.
                        float drawn = ripple * dot(1.0 - kept, RIPPLES * vec3(0.5, 0.3, 0.2)) / dot(RIPPLES, vec3(0.5, 0.3, 0.2)) * WATER_STREAK;
                        vec2 toward = view.xy / max(length(view.xy), 1e-4);
                        float coarser = log2(max(4.0 * drawn / uPixel * uWaterDensity * uWaterShare / float(WATER_TAPS), 1.0));
                        if (drawn > 0.0) {
                            shows = vec3(0.0);
                            for (int i = 0; i < WATER_TAPS; i++) {
                                vec3 tipped = normalize(surface + vec3(toward * (drawn * ((float(i) + 0.5) * 2.0 / float(WATER_TAPS) - 1.0)), 0.0));
                                vec3 thrown = reflect(-view, tipped);
                                thrown.z = abs(thrown.z);
                                shows += overWater(thrown * uWaterThrow - toEye, coarser);
                            }
                            shows /= float(WATER_TAPS);
                        } else {
                            shows = overWater(mirrored * uWaterThrow - toEye, 0.0);
                        }
                    } else {
                        shows = ridges(mirrored, atmosphere(mirrored));
                    }
                    color = mix(color, shows * (0.35 + 0.65 * open), glance * shine);
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
                    // A lamp burning behind a window lights the dust on the glass, warm and
                    // faint. The room is what shows lit, through the pane: laid over all of
                    // it, the lamp's light was a bright sheet that hid a room far dimmer.
                    color += vec3(1.0, 0.72, 0.4) * (0.6 * glow * soiled / veil);
                }
                // Towards a low sun the air itself is lit: what stands far off in that quarter
                // is veiled in gold, and the nearer for the lower the sun is.
                vec3 air = atmosphere(-view);
                color = mix(color, air, distanceHaze(-view, range));
                // The world ends by fading into what stands behind it, which is the hills and
                // not bare air: faded to air, its last tiles show as dashes against them.
                // And it ends where the loaded map does, which at the wheel of a car at speed is
                // further off ahead than behind: the game loads ahead of it.
                // With the far land behind it, it has thinned out instead: see thinnedOut.
                float ends = uBeyond > 0.5 ? 0.0 : max(smoothstep(uFogStart, uFogEnd, range), smoothstep(uLoaded.z - (uFogEnd - uFogStart), uLoaded.z, length(toEye.xy + uLoaded.xy)));
                if (ends > 0.0) {
                    color = mix(color, ridges(-view, air), ends);
                }
                // Fog, rain and snow hide what stands far off in them: see Eyesight. Where the world
                // has ended in the hills, they hide what they hide of the sky that way.
                if (uVeil.x > 0.0) {
                    color = mix(color, veilColor(-view), max(veiling(range), ends * skyVeiling(-view)));
                }
                // What is laid over the ground is mixed in by its alpha. For everything else the
                // alpha says how much of a mirror the surface is, 1 for none, which the last
                // pass reads to trace what the mirror shows.
                // A varnished floor mirrors a little too: the window across the room, the legs of
                // a chair.
                float polish = vMaterial > 2.5 && vMaterial < 3.5 ? 0.22 : vMaterial > 10.5 && vMaterial < 11.5 ? 0.3 : 0.0;
                // A window pane mirrors whichever way it faces, and so does the glass of a screen.
                // So does a mirror in its frame, and more than either.
                float pane = glazed > 0.5 ? 0.6 : showing ? 0.0 : surface > 9.5 && surface < 10.5 ? 0.4
                        : surface > 15.5 && surface < 16.5 && !pictured ? 0.55 : 0.0;
                // Where water shows the world by the picture drawn from under it, it is no mirror for the last pass to trace.
                gl_FragColor = vec4(color, sheet ? veil : uLaid > 0.5 ? texel.a * shown : 1.0 - uMirrors * max(max(water && uWater.z > 0.0 ? 0.0 : shine, polish) * step(0.8, n.z), pane));
            }
            """;

    /**
     * For the world's fragment main, after the texel is read: the game's blood (see BloodBuilder).
     * The game's picture of a splat is a few dozen dots of pure red, each wholly there or not,
     * and from the ground a dot is a finger across: magnified it was a grid of soft red squares.
     * Where it lies is read from those dots smoothed over their neighbours, and cut at a ragged
     * line that is fine in the world's own measure, so its edge is sharp and uneven however
     * near it is. It is blood's colour and not the game's red: dark crimson just spilt, glossy
     * while it is wet, and brown and dull as it dries, which the game's shade of each splat,
     * darker the drier it is, says. It hides what it lies on where it is thick, and less as it
     * dries, as the game's cover does. Without the mod's own textures (OwnTextures, and Low) it
     * is the game's own: its red, as much of it as the game shows, mixed in by the game's share
     * of what is shown, which is 1 - (1 - a)^2.2 of the light the picture mixes.
     */
    static final String BLOOD_MAIN = """
                float bloodWet = 0.0;
                if (vMaterial > 84.5 && vMaterial < 85.5 && uOwnTextures < 0.5) {
                    texel.rgb *= vec3(vRoot.x, vRoot.y, vRoot.y);
                    texel.a = 1.0 - pow(1.0 - clamp(texel.a * vCover, 0.0, 1.0), 2.2);
                } else if (vMaterial > 84.5 && vMaterial < 85.5) {
                    float lies = texel.a;
                    if (uTexel.x > 0.0) {
                        lies = 0.0;
                        for (int i = -1; i <= 1; i++) {
                            for (int j = -1; j <= 1; j++) {
                                lies += texture2D(uTexture, vUv + vec2(float(i), float(j)) * uTexel).a * (2.0 - abs(float(i))) * (2.0 - abs(float(j)));
                            }
                        }
                        lies /= 16.0;
                    }
                    vec2 on = vWorld.xy + vWorld.z * vec2(0.71, 1.37);
                    float ragged = patches(on * 23.0) * 0.6 + patches(on * 61.0) * 0.4;
                    float cut = lies + (ragged - 0.5) * 0.3;
                    float soft = max(fwidth(cut), 0.02);
                    float there = smoothstep(0.42 - soft, 0.42 + soft, cut);
                    float fresh = smoothstep(0.15, 0.75, vRoot.x);
                    bloodWet = fresh * smoothstep(0.5, 0.75, lies);
                    // Thicker where more of it lies, and darker there.
                    float thick = smoothstep(0.45, 0.9, lies);
                    vec3 spilt = mix(vec3(0.42, 0.025, 0.02), vec3(0.3, 0.012, 0.01), thick);
                    texel.rgb = mix(vec3(0.17, 0.07, 0.05), spilt, fresh) * (0.85 + 0.3 * ragged);
                    texel.a = there * clamp(vCover / 0.27, 0.0, 1.0) * (0.6 + 0.3 * thick);
                }
            """;
    static final String WORLD_FRAGMENT_MAIN = WORLD_FRAGMENT_MAIN_START.concat(RoadPaint.GLSL_MAIN).concat(BLOOD_MAIN).concat(WORLD_FRAGMENT_MAIN_GROWN).concat(WORLD_FRAGMENT_MAIN_REST);

    static final String SHADOW_VERTEX = """
            #version 120
            uniform mat4 uMatrix;
            varying vec2 vUv;
            uniform vec3 uWind;
            varying float vLeaves;
            varying float vSolid;
            varying vec3 vAt;
            // 1 while the map drawn is the one read as what stands over the ground and keeps the sky off it.
            uniform float uFromAbove;
            """ + GRASS_WIND + MODEL_STANDS + FACED + """
            void main() {
                if (uModel > 0.5) {
                    // A model is solid all over and nothing of it blows about. It is drawn with
                    // the white texture, of which any place will do.
                    vec3 on = over(gl_Vertex.xyz);
                    vUv = vec2(0.0);
                    vLeaves = 0.0;
                    vSolid = 1.0;
                    vAt = vec3(uPlace.xy + on.xy, on.z);
                    gl_Position = uMatrix * vec4(vAt, 1.0);
                    return;
                }
                vUv = gl_MultiTexCoord0.xy;
                float material = floor((gl_Color.a + 0.5) / 32.0);
                // How much of a mass of leaves is there to throw a shadow: a crown's shadow fan says, in its colour, how much of its crown is on the tree.
                vLeaves = material > 79.5 && material < 80.5 ? clamp(gl_Color.r, 0.0, 1.0) : material > 4.5 && material < 5.5 || material > 62.5 && material < 63.5 ? 1.0 : 0.0;
                // A floor or a ceiling is whole however many clear pixels its sprite has: see SpriteGeometry.SHEET.
                vSolid = gl_MultiTexCoord1.z > 5.5 ? 1.0 : 0.0;
                // A crown's sprigs take their colour from the sprite and are no part of its outline: the fan that stands with them throws the shadow.
                if (material > 71.5 && material < 79.5 || material > 80.5 && material < 81.5) {
                    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                    return;
                }
                // A strip of grass carries its height over its root and the way to it, not a facing nor how a card stands: see SpriteGeometry.addStrip.
                if (mod(gl_Color.a, 32.0) > 23.5) {
                    vAt = gl_Vertex.xyz;
                    float edge = floor((gl_Normal.x + 3.0) * 0.5) - 1.0;
                    gl_Position = uMatrix * vec4(swayed(gl_Vertex.xyz, fract(gl_Normal.z), gl_Normal.x - 2.0 * edge), 1.0);
                    return;
                }
                vec2 right;
                vec3 lies;
                vec4 stands = faced(gl_Vertex, right, lies);
                stands.xyz += lies;
                vAt = stands.xyz;
                // An overhead wire swings where the view has it, and so does a bulb that hangs on a cord: see swung.
                if (material > 11.5 && material < 12.5 || material > 81.5 && material < 82.5 || material > 13.5 && material < 14.5 && dot(gl_Normal, gl_Normal) > 1.004) {
                    // A wire keeps neither the sky's light nor the weather off what is under it,
                    // and that map is read at places tiles away from the one being lit: in it
                    // a thing as thin and high as a wire is cover over every place that reads
                    // a texel under it, in lines far finer than anything it could shelter.
                    if (uFromAbove > 0.5) {
                        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                        return;
                    }
                    vec2 across;
                    float swing;
                    wireOff(gl_MultiTexCoord0.xy, across, swing);
                    vAt = swung(stands.xyz, length(gl_Normal) - 1.0, across, swing);
                    gl_Position = uMatrix * vec4(vAt, 1.0);
                    return;
                }
                gl_Position = uMatrix * blown(stands, gl_Normal);
            }
            """;

    static final String SHADOW_FRAGMENT = """
            #version 120
            uniform sampler2D uTexture;
            varying vec2 vUv;
            varying float vLeaves;
            varying float vSolid;
            varying vec3 vAt;
            uniform vec3 uSunWay;
            void main() {
                if (vSolid < 0.5 && texture2D(uTexture, vUv).a < 0.5) {
                    discard;
                }
                // A crown is not a lid. The sun comes through the gaps between its boughs in
                // patches the size of a hand to the size of a table, and the ground under a
                // tree is dappled, not dark.
                if (vLeaves > 0.0) {
                    // A gap is where the ray of light meets the ground, so that it is a gap
                    // through every bough the ray passes and not through one of them. A crown
                    // that has lost leaves has more gaps, in the same places from frame to frame.
                    vec2 lands = vAt.xy - uSunWay.xy * (vAt.z / max(uSunWay.z, 0.15));
                    vec2 cell = floor(lands * 1.3), fine = floor(lands * 4.1);
                    float gap = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453) * 0.6
                            + fract(sin(dot(fine, vec2(39.346, 11.135))) * 43758.5453) * 0.4;
                    if (gap > 0.6 * vLeaves) {
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
     *
     * Where a drop has got to is never the wind times the clock: the wind changes, and every
     * drop would be somewhere else. uFall is how far the air has carried the rain so far and
     * the seconds it has been falling, both kept by Wind, which only move on, and uAir is how
     * fast the air moves now, which is what slants the streak.
     *
     * The drops are the same drops whoever looks: the box repeats over the whole map, and
     * uBox says which of it to draw. Round the eye that is the one box the eye is in. From
     * far overhead it is the boxes over the ground in the picture, each drawn in its turn
     * with its place among them in uTile, and a streak is no thinner than uThin, which is
     * what the picture there can show. uLong is how long the eye takes a drop in for, in
     * seconds, which is the streak's length over the drop's speed.
     */
    static final String RAIN_VERTEX = """
            #version 120
            uniform vec3 uEye;
            uniform vec3 uFall;
            uniform vec2 uAir;
            uniform float uRain;
            uniform vec4 uBox;
            uniform vec2 uTile;
            uniform float uThin;
            uniform float uLong;
            // Half a streak's least width for each tile it is from the eye, or 0 from far overhead, where uThin holds it.
            uniform float uPerPixel;
            // How fast the eye is going, tiles a second: a drop is seen streaked along the way it goes past the eye.
            uniform vec3 uMoves;
            varying vec3 vWorld;
            varying vec2 vIn;
            varying float vShare;

            const float BOX = 14.0;
            const float HIGH = 12.0;

            void main() {
                vec3 seed = gl_Vertex.xyz;
                float corner = gl_Vertex.w;
                // A whole number of sixteenths of ten tiles a second: see Wind.RAIN_CLOCK.
                float speed = 0.625 * (18.0 + floor(8.0 * fract(seed.x * 37.0 + seed.y * 91.0)));
                vec3 p = vec3(seed.xy * 2.0 * BOX + uFall.xy, seed.z * HIGH - speed * uFall.z);
                p.xy = mod(p.xy - uBox.xy + BOX, 2.0 * BOX) - BOX + uBox.xy + uTile;
                p.z = mod(p.z - uBox.z + uBox.w, HIGH) - uBox.w + uBox.z;
                vec3 past = vec3(uAir, -speed) - uMoves;
                vec3 way = normalize(past);
                vec3 side = normalize(cross(way, uEye - p));
                // A streak thinner than a dot of the picture flickers: it is drawn a dot wide and as much fainter.
                float thin = max(uThin, uPerPixel * distance(uEye, p));
                vShare = uThin / thin;
                float along = corner > 1.5 ? 1.0 : 0.0;
                float across = corner > 0.5 && corner < 2.5 ? 1.0 : -1.0;
                vec3 at = p - way * (uLong * length(past) * along) + side * (across * thin);
                vWorld = at;
                vIn = vec2(across, along);
                // The harder it rains the more of the drops there are.
                gl_Position = fract(seed.z * 17.0 + seed.x * 5.0) > uRain ? vec4(2.0, 2.0, 2.0, 1.0) : gl_ModelViewProjectionMatrix * vec4(at, 1.0);
            }
            """;

    /**
     * Snow as flakes in the world, in the same box of air as the rain and from the same
     * vertices. A flake falls at a tile or two a second, so the air carries it much further
     * than it falls: in any wind snow goes by slantwise, and in a gale nearly level. Each
     * also wanders from side to side as it comes down, a whole number of times in the
     * eighty seconds its clock runs, and turns a face of its own size to the eye.
     */
    static final String SNOW_VERTEX = """
            #version 120
            uniform vec3 uEye;
            uniform vec3 uFall;
            uniform vec2 uAir;
            uniform float uSnow;
            uniform vec4 uBox;
            uniform vec2 uTile;
            uniform float uThin;
            uniform float uLong;
            // How fast the eye is going, tiles a second: see the rain's.
            uniform vec3 uMoves;
            varying vec3 vWorld;
            varying vec2 vIn;

            const float BOX = 14.0;
            const float HIGH = 12.0;

            void main() {
                vec3 seed = gl_Vertex.xyz;
                float corner = gl_Vertex.w;
                // A whole number of hundredths of fifteen tiles a second: see Wind.SNOW_CLOCK.
                float speed = 0.15 * (6.0 + floor(7.0 * fract(seed.x * 37.0 + seed.y * 91.0)));
                float turn = uFall.z * 0.0785398;
                float often = 20.0 + floor(40.0 * fract(seed.y * 53.0 + seed.z * 17.0));
                // It wanders less the harder the wind drives it.
                float wide = (0.06 + 0.14 * fract(seed.z * 29.0 + seed.x * 3.0)) / (1.0 + 0.15 * length(uAir));
                vec2 wander = vec2(sin(turn * often + seed.x * 40.0), cos(turn * (often + 7.0) + seed.z * 40.0)) * wide;
                vec3 p = vec3(seed.xy * 2.0 * BOX + uFall.xy + wander, seed.z * HIGH - speed * uFall.z);
                p.xy = mod(p.xy - uBox.xy + BOX, 2.0 * BOX) - BOX + uBox.xy + uTile;
                p.z = mod(p.z - uBox.z + uBox.w, HIGH) - uBox.w + uBox.z;
                vec3 toEye = normalize(uEye - p);
                vec3 side = normalize(cross(vec3(0.0, 0.0, 1.0), toEye) + vec3(1e-5, 0.0, 0.0));
                vec3 over = cross(toEye, side);
                // A flake stays a flake: drawn out along the way it goes past the eye, by a hard wind or met
                // at speed from a car, by no more than its own width, so a fast one is a little long and never a streak.
                vec3 going = vec3(uAir, -speed) - uMoves;
                float along = corner > 1.5 ? 1.0 : -1.0;
                float across = corner > 0.5 && corner < 2.5 ? 1.0 : -1.0;
                float size = max(0.014 + 0.016 * fract(seed.x * 71.0 + seed.z * 13.0), uThin);
                vec3 smear = going * (0.5 * uLong);
                float smeared = length(smear);
                smear *= smeared > size ? size / smeared : 1.0;
                vec3 at = p + side * (across * size) + over * (along * size) + smear * along;
                vWorld = at;
                vIn = vec2(across, along);
                // The harder it snows the more of the flakes there are.
                gl_Position = fract(seed.z * 17.0 + seed.x * 5.0) > uSnow ? vec4(2.0, 2.0, 2.0, 1.0) : gl_ModelViewProjectionMatrix * vec4(at, 1.0);
            }
            """;

    /**
     * Whether a place in the air is inside a building the picture has taken the roof off, where
     * the map of roofs has nothing to keep the rain out with. Seen as the game's own view those
     * are the buildings the game opens: uShelter holds, for each square of the block that starts
     * at uShelterAt.xy, how many storeys of rooms stand on it, uShelterAt.z is one over the
     * block's side, or 0 when there is no block, and uShelterAt.w is the height the texture's
     * most stands for. On the camera's way to the eye it is the
     * viewer's own building, whose box is uLidBox. uSeenOn, in the rain and the snow, is the
     * way the camera far overhead looks and the height of the viewer's floor, or nothing.
     */
    static final String SHELTERED = """
            uniform sampler2D uShelter;
            uniform vec4 uShelterAt;
            uniform vec4 uLidBox;

            bool sheltered(vec3 at) {
                if (at.x > uLidBox.x && at.x < uLidBox.z && at.y > uLidBox.y && at.y < uLidBox.w) {
                    return true;
                }
                vec2 square = (at.xy - uShelterAt.xy) * uShelterAt.z;
                return uShelterAt.z > 0.0 && square.x > 0.0 && square.x < 1.0 && square.y > 0.0 && square.y < 1.0
                        && at.z < texture2D(uShelter, square).r * uShelterAt.w;
            }

            // Whether a place in the air is seen against a sheltered floor: the line of sight
            // through it, which goes the way `way`, comes down on a room at the height `level`.
            // From far overhead most of the rain seen over an opened room falls in front of the
            // building or over where its roof was, and the game's own view has none there.
            bool shelteredWhereSeen(vec3 at, vec3 way, float level) {
                vec3 on = at - way * ((at.z - level) / way.z);
                return sheltered(vec3(on.xy, level + 0.1));
            }
            """;

    static final String SNOW_FRAGMENT = """
            #version 120
            const float FLAKE_IN_BEAM = 5.0;
            uniform sampler2DShadow uRoof;
            uniform mat4 uRoofMatrix;
            uniform vec3 uTint;
            uniform vec3 uEye;
            uniform float uSeated;
            uniform float uShown;
            uniform vec4 uSeenOn;
            // 1 where a drop or a flake is kept out of rooms and from under roofs; 0 seen from the camera outside the viewer,
            // where it falls on everything as the game's own view has it.
            uniform float uSheltering;
            varying vec3 vWorld;
            varying vec2 vIn;
            """ + BEAMS + SHELTERED + """

            void main() {
                // The vehicle the viewer sits in has a roof the map of roofs knows nothing of.
                if (uSeated > 0.5 && distance(vWorld.xy, uEye.xy) < 0.9 && vWorld.z < uEye.z + 0.3) {
                    discard;
                }
                if (uSheltering > 0.5 && (sheltered(vWorld) || (uSeenOn.z < 0.0 && shelteredWhereSeen(vWorld, uSeenOn.xyz, uSeenOn.w)))) {
                    discard;
                }
                // A flake under a roof never fell there.
                vec3 c = (uRoofMatrix * vec4(vWorld, 1.0)).xyz * 0.5 + 0.5;
                float open = uSheltering > 0.5 && c.x > 0.0 && c.x < 1.0 && c.y > 0.0 && c.y < 1.0 ? shadow2D(uRoof, c).r : 1.0;
                float body = 1.0 - smoothstep(0.35, 1.0, length(vIn));
                // Snow in a beam is what a driver sees of a night's snowfall: each flake a spark coming at the glass.
                vec3 dir;
                float lit = 0.0;
                for (int i = 0; i < 2; i++) {
                    if (uBeamAt[i].w > 0.0) {
                        lit += beamAt(i, vWorld, dir);
                    }
                }
                if (uTorchAt.w > 0.0) {
                    lit += TORCH_GIVES * torchAt(vWorld, dir);
                }
                // A flake throws a beam back the way it came, towards a driver sat just behind the lamp: in the beam it
                // is far brighter than the snow on the road the same beam lights.
                gl_FragColor = vec4(uTint + vec3(1.0, 0.94, 0.82) * (lit * FLAKE_IN_BEAM), body * open * uShown);
            }
            """;

    static final String RAIN_FRAGMENT = """
            #version 120
            uniform sampler2DShadow uRoof;
            uniform mat4 uRoofMatrix;
            uniform vec3 uTint;
            uniform vec3 uEye;
            uniform float uSeated;
            uniform float uShown;
            uniform vec4 uSeenOn;
            // 1 where a drop or a flake is kept out of rooms and from under roofs; 0 seen from the camera outside the viewer,
            // where it falls on everything as the game's own view has it.
            uniform float uSheltering;
            varying vec3 vWorld;
            varying vec2 vIn;
            varying float vShare;
            """ + BEAMS + SHELTERED + """

            void main() {
                // The vehicle the viewer sits in has a roof the map of roofs knows nothing of.
                if (uSeated > 0.5 && distance(vWorld.xy, uEye.xy) < 0.9 && vWorld.z < uEye.z + 0.3) {
                    discard;
                }
                if (uSheltering > 0.5 && (sheltered(vWorld) || (uSeenOn.z < 0.0 && shelteredWhereSeen(vWorld, uSeenOn.xyz, uSeenOn.w)))) {
                    discard;
                }
                // The map of what is overhead, seen from straight above: a drop under a roof
                // never fell there.
                vec3 c = (uRoofMatrix * vec4(vWorld, 1.0)).xyz * 0.5 + 0.5;
                float open = uSheltering > 0.5 && c.x > 0.0 && c.x < 1.0 && c.y > 0.0 && c.y < 1.0 ? shadow2D(uRoof, c).r : 1.0;
                float body = (1.0 - vIn.x * vIn.x) * smoothstep(0.0, 0.25, vIn.y) * (1.0 - smoothstep(0.5, 1.0, vIn.y));
                // A drop falling through a beam is a bright streak: it is most of what rain
                // looks like from behind the wheel at night.
                vec3 dir;
                float lit = 0.0;
                for (int i = 0; i < 2; i++) {
                    if (uBeamAt[i].w > 0.0) {
                        lit += beamAt(i, vWorld, dir);
                    }
                }
                if (uTorchAt.w > 0.0) {
                    lit += TORCH_GIVES * torchAt(vWorld, dir);
                }
                gl_FragColor = vec4(uTint + vec3(1.0, 0.92, 0.76) * (lit * 1.4), body * open * uShown * vShare);
            }
            """;

    /**
     * For air: whether a point has something over it, from the map from above, and the sun on
     * it. A sun map holds a ceiling sheet behind itself by the slope offset, so air close under a
     * ceiling, and in the thin gap over the top of a wall, reads as in the sun. Air in a room is
     * asked at itself and AIR_BAND lower: a shaft through a window is lit at both, the grain along
     * the top of a wall is not.
     */
    static final String ROOFED_AIR = """
            uniform sampler2D uHeights;
            uniform mat4 uRoofMatrix;
            uniform float uRoofDeep;

            const float AIR_BAND = 0.06;

            bool underRoof(vec3 p) {
                vec3 c = (uRoofMatrix * vec4(p, 1.0)).xyz * 0.5 + 0.5;
                if (c.x <= 0.0 || c.x >= 1.0 || c.y <= 0.0 || c.y >= 1.0) {
                    return false;
                }
                float over = texture2D(uHeights, c.xy).r;
                return over < 1.0 && (c.z - over) * uRoofDeep > 0.001;
            }

            // The sun on a point of air, from the near map or else the far one, or `beyond` past both.
            float sunInAir(vec3 p, float beyond) {
                vec3 c = (uNearMatrix * vec4(p, 1.0)).xyz * 0.5 + 0.5;
                if (inside(c)) {
                    return shadow2D(uShadowNear, c).r;
                }
                c = (uFarMatrix * vec4(p, 1.0)).xyz * 0.5 + 0.5;
                return inside(c) ? shadow2D(uShadowFar, c).r : beyond;
            }

            float sunInAir(vec3 p, bool roofed, float beyond) {
                float sun = sunInAir(p, beyond);
                return roofed && sun > 0.0 ? min(sun, sunInAir(p - vec3(0.0, 0.0, AIR_BAND), beyond)) : sun;
            }
            """;

    /**
     * Dust in the air round the eye, from the same vertices as the rain: a speck of it shows
     * only where the sun, a torch or headlights reach it, so it is seen in a shaft through a
     * window and not in the shade beside it. Each speck drifts on the room's slow air, a whole
     * number of times in the hour SkyState.seconds runs, and settles a whole number of box
     * heights in that hour, so the clock wraps unseen. A flake turns as it drifts and catches
     * the light now and then.
     */
    static final String DUST_VERTEX = """
            #version 120
            uniform vec3 uEye;
            uniform float uClock;
            // Half a speck's least width for each tile it is from the eye: a dot of the picture.
            uniform float uPerPixel;
            // Which of the layers drawn from the one set of vertices this is: each moves the seeds.
            uniform float uLayer;
            varying vec2 vIn;
            varying vec3 vWorld;
            varying float vShare;

            const float WIDE = 4.0;
            const float HIGH = 2.6;
            const float UNDER = 1.6;
            const float SIZE = 0.0012;
            const float TURN = 6.2831853 / 3600.0;

            void main() {
                vec3 seed = fract(gl_Vertex.xyz + uLayer * vec3(0.3183099, 0.618034, 0.4142136));
                float corner = gl_Vertex.w;
                float t = uClock * TURN;
                // Each way about once in thirty to ninety seconds, a few centimetres a second.
                vec3 often = vec3(40.0) + floor(vec3(80.0, 80.0, 60.0) * fract(seed.yzx * vec3(91.0, 17.0, 3.0) + seed.xyz * vec3(37.0, 53.0, 29.0)));
                vec3 wander = vec3(sin(t * often.x + seed.y * 40.0), cos(t * often.y + seed.z * 40.0), 0.6 * sin(t * often.z + seed.x * 40.0)) * 0.08;
                float sinks = 1.0 + floor(3.0 * fract(seed.x * 13.0 + seed.z * 7.0));
                vec3 p = seed * vec3(2.0 * WIDE, 2.0 * WIDE, HIGH) + wander - vec3(0.0, 0.0, sinks * HIGH * uClock / 3600.0);
                p.xy = mod(p.xy - uEye.xy + WIDE, 2.0 * WIDE) - WIDE;
                p.z = mod(p.z - uEye.z + UNDER, HIGH) - UNDER;
                // Faded in from the box's sides, where a speck wraps round.
                float edge = min(WIDE - max(abs(p.x), abs(p.y)), min(p.z + UNDER, HIGH - UNDER - p.z));
                p += uEye;
                float far = distance(uEye, p);
                vec3 toEye = (uEye - p) / max(far, 1e-4);
                vec3 side = normalize(cross(vec3(0.0, 0.0, 1.0), toEye) + vec3(1e-5, 0.0, 0.0));
                vec3 over = cross(toEye, side);
                vec2 at = vec2(corner > 0.5 && corner < 2.5 ? 1.0 : -1.0, corner > 1.5 ? 1.0 : -1.0);
                // Drawn a dot wide and as much fainter, as the rain is.
                float size = max(SIZE, uPerPixel * far);
                float glint = pow(0.5 + 0.5 * sin(t * (2000.0 + floor(3000.0 * fract(seed.z * 71.0 + seed.y * 13.0))) + seed.x * 60.0), 4.0);
                vShare = (SIZE / size) * (SIZE / size) * smoothstep(0.0, 0.5, edge) * (0.3 + 0.7 * glint);
                vIn = at;
                vWorld = p;
                vec3 v = p + side * (at.x * size) + over * (at.y * size);
                gl_Position = far < 0.12 ? vec4(2.0, 2.0, 2.0, 1.0) : gl_ModelViewProjectionMatrix * vec4(v, 1.0);
            }
            """;

    static final String DUST_FRAGMENT = """
            #version 120
            uniform sampler2DShadow uShadowNear;
            uniform sampler2DShadow uShadowFar;
            uniform mat4 uNearMatrix;
            uniform mat4 uFarMatrix;
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uGlow;
            uniform float uShown;
            varying vec2 vIn;
            varying vec3 vWorld;
            varying float vShare;
            """ + BEAMS + """

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }
            """ + ROOFED_AIR + """

            void main() {
                float r2 = dot(vIn, vIn);
                if (r2 > 1.0) {
                    discard;
                }
                float sun = sunInAir(vWorld, underRoof(vWorld), 0.0);
                // Dust sends most of what it catches on the way the light was going.
                float mu = max(dot(normalize(vWorld - uEye), uLight), 0.0);
                vec3 lit = uGlow * (sun * (0.3 + 2.5 * pow(mu, 6.0)));
                vec3 dir;
                float beamed = 0.0;
                for (int i = 0; i < 2; i++) {
                    if (uBeamAt[i].w > 0.0) {
                        beamed += beamAt(i, vWorld, dir);
                    }
                }
                if (uTorchAt.w > 0.0) {
                    beamed += TORCH_GIVES * torchAt(vWorld, dir);
                }
                lit += vec3(1.0, 0.92, 0.76) * beamed;
                gl_FragColor = vec4(lit * ((1.0 - r2) * vShare * uShown), 1.0);
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

            // Where what the picture shows at a place in it is, seen from the eye: x to the right, y up, z ahead, in tiles.
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
                // Before any pixel leaves: a slope across the screen is taken from the pixel's
                // neighbours, which must all have got this far.
                vec3 p = placeAt(uv);
                vec3 n = normalize(cross(dFdx(p), dFdy(p)));
                if (texture2D(uDepth, uv).r >= 0.9999995) {
                    gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                    return;
                }
                // Turned to face the eye, which is not the same as turned to point back along
                // the way the eye looks: a ceiling or a side wall looked along has next to no
                // part along that way, and which sign it had went by the last digit. It
                // changed with every frame, and the whole surface went light and dark.
                if (dot(n, p) > 0.0) {
                    n = -n;
                }
                float turn = 6.2831853 * fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
                // Every length below is one of the world's: half a pace, the width of a room.
                // What is held a hand's breadth from the eye, a weapon, a hand, a scope, is
                // smaller than any of them, and searched over those lengths it is shaded by
                // the whole picture, in grains. Nearer than a pace and a half the lengths
                // shrink with the distance, as if the thing and the eye were that much larger.
                float small = min(p.z / 1.4, 1.0);
                float reach = RADIUS * small / (p.z * uTan.y) * 0.5;
                float blocked = 0.0;
                for (int i = 0; i < SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(SAMPLES);
                    float a = turn + along * 15.1;
                    vec2 off = vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (along * reach);
                    vec3 v = (placeAt(uv + off) - p) / small;
                    float far2 = dot(v, v);
                    blocked += max(dot(v, n) - 0.04, 0.0) / (far2 + 0.03) * step(far2, RADIUS * RADIUS * 3.0) * shown(uv + off);
                }
                float open = clamp(1.0 - blocked * (1.5 / float(SAMPLES)), 0.0, 1.0);
                // The same again over the width of a room: the corner where two walls and a
                // ceiling meet, the ground under a porch, the foot of a hedge. It is what makes
                // a room a hollow and not six lit cards.
                float broad = 0.0;
                float roomy = ROOM_RADIUS * small / (p.z * uTan.y) * 0.5;
                for (int i = 0; i < ROOM_SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(ROOM_SAMPLES);
                    float a = turn + 2.1 + along * 19.3;
                    vec2 off = vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (sqrt(along) * roomy);
                    vec3 v = (placeAt(uv + off) - p) / small;
                    float far2 = dot(v, v);
                    broad += max(dot(v, n) - 0.05, 0.0) / (far2 + 0.15) * step(far2, ROOM_RADIUS * ROOM_RADIUS * 2.0) * shown(uv + off);
                }
                open *= clamp(1.0 - broad * (2.0 / float(ROOM_SAMPLES)), 0.4, 1.0);
                // Light does not stop at the first thing it falls on. A sunlit lawn throws
                // green up under the eaves, a red wall warms the path beside it. What stands
                // in the picture within a few paces of this point, on the side it faces, lends
                // it some of its own light.
                vec3 lent = vec3(0.0);
                float wide = BOUNCE_RADIUS * small / (p.z * uTan.y) * 0.5;
                for (int i = 0; i < BOUNCE_SAMPLES; i++) {
                    float along = (float(i) + 0.5) / float(BOUNCE_SAMPLES);
                    float a = turn + 1.3 + along * 23.7;
                    vec2 at = uv + vec2(cos(a) * uTan.y / uTan.x, sin(a)) * (sqrt(along) * wide);
                    vec3 v = (placeAt(at) - p) / small;
                    float far2 = dot(v, v);
                    float faces = max(dot(v, n), 0.0) / sqrt(far2 + 0.0001) * shown(at);
                    lent += min(texture2D(uScene, at).rgb, vec3(4.0)) * (faces * step(far2, BOUNCE_RADIUS * BOUNCE_RADIUS * 4.0) / (1.0 + far2));
                }
                lent *= 1.6 / float(BOUNCE_SAMPLES) * (1.0 - smoothstep(25.0, 50.0, p.z));
                // The shadow maps cannot hold what is small and close: the shade a blade of grass
                // or the leg of a chair throws where it meets the ground. A ray is walked from
                // this point towards the sun through the picture's depth, and if it passes
                // behind something near by, that something is in the sun's way.
                // Not on a surface the sun only grazes. A ray along such a wall runs a finger's
                // width off it for its whole length, and every board and brick course of the
                // wall is then in its way or not by a hair, differently in each frame: the
                // wall flickered in patches. Such a surface has next to no sun on it to lose.
                float sunOn = smoothstep(0.08, 0.3, dot(n, uSunEye));
                if (uSunEye.z > -2.0 && sunOn > 0.0) {
                    float reach = min(0.5, 0.03 * p.z + 0.12) * small;
                    float begin = fract(turn * 0.159 + 0.37);
                    float shaded = 0.0;
                    for (int i = 0; i < 10; i++) {
                        vec3 q = p + n * (0.03 * small) + uSunEye * (reach * (float(i) + begin) / 10.0);
                        vec2 at = q.xy / (q.z * uTan) * 0.5 + 0.5;
                        float behind = (q.z - placeAt(at).z) / small;
                        // By how far behind, and not by whether: a ray that only just passes
                        // behind something shades a little, so the shade has no edge to jump.
                        shaded = max(shaded, smoothstep(0.02, 0.07, behind) * (1.0 - smoothstep(0.25, 0.35, behind)) * step(0.5, shown(at)));
                    }
                    open *= 1.0 - 0.38 * shaded * sunOn;
                }
                // Kept as a part of a whole: the target holds numbers from 0 to 1.
                gl_FragColor = vec4(lent / (1.0 + lent), mix(open, 1.0, smoothstep(35.0, 65.0, p.z)));
            }
            """;

    /** Averages a half-size pass, the occlusion, the lit air or the clouds, over four by four of its pixels, which takes the grain of its sampling out. */
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
            // How much light a tile of fog, rain or snow takes out of a line of sight: what is
            // lit far off in it is seen through the air between.
            uniform float uThick;
            // How much more a room's still air holds for light to catch than the open air's uMist:
            // the dust that makes a shaft through a window.
            uniform float uRoomAir;
            // For each square of the block from uRoomAirAt.xy, the floor of its lowest room and the
            // ceiling of its highest, both 0 where it has none: see RoomAir. uRoomAirAt.z is one
            // over the block's side, or 0 when there is no block.
            uniform sampler2D uRoomAirMap;
            uniform vec3 uRoomAirAt;
            """ + BEAMS + """

            const int STEPS = 24;
            const float REACH = 48.0;

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }
            """ + ROOFED_AIR + """

            // Air under an awning, a porch roof or a tree is under something from above too, and out of doors.
            bool inRoom(vec3 p) {
                vec2 square = (p.xy - uRoomAirAt.xy) * uRoomAirAt.z;
                if (uRoomAirAt.z <= 0.0 || square.x <= 0.0 || square.x >= 1.0 || square.y <= 0.0 || square.y >= 1.0) {
                    return false;
                }
                vec2 span = texture2D(uRoomAirMap, square).rg;
                return p.z >= span.x && p.z < span.y;
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
                float along = length(ray);
                for (int i = 0; i < STEPS; i++) {
                    float at = (float(i) + start) * stride;
                    float through = exp(-uThick * at * along);
                    vec4 p = vec4(uEye + ray * at, 1.0);
                    bool roofed = underRoof(p.xyz);
                    float sun = sunInAir(p.xyz, roofed, 1.0);
                    // The air thins with height, and there is none to speak of under the ground.
                    lit += sun * exp(-max(p.z, 0.0) * 0.12) * through * (roofed && inRoom(p.xyz) ? uMist + uRoomAir : uMist);
                    for (int b = 0; b < 2; b++) {
                        if (uBeamAt[b].w > 0.0) {
                            beamed += beamInAir(b, p.xyz) * through;
                        }
                    }
                    if (uTorchAt.w > 0.0) {
                        vec3 dir;
                        beamed += TORCH_GIVES * torchAt(p.xyz, dir) * through;
                    }
                }
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
                vec3 caught = glow * (lit / float(STEPS) * ahead * along * towards);
                // A beam barely shows in clear night air, on the dust in it, and more in mist.
                caught += vec3(1.0, 0.92, 0.76) * (beamed / float(STEPS) * ahead * along * (0.0004 + 0.2 * uMist));
                gl_FragColor = vec4(caught, 1.0);
            }
            """;

    /**
     * The game draws its characters with its own shader, in the colours a screen shows, under
     * the game's light for where they stand. They are drawn into a picture of their own first,
     * against the world's depth, and this pass then puts them into the picture that holds
     * light: under the sun where the shadow maps say it reaches them, behind the haze.
     *
     * The source is held in pieces, here, in CabinShader and in CharacterDraw, and joined when
     * Pipeline loads: a class file holds no one string of more than 65535 bytes, and the whole
     * of it is longer.
     */
    static final String FIGURE_LIGHT_PARTS = "#version 120\n" + Sky.ATMOSPHERE + ENDS + """
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
            // the wheel, by sign, and the top of the dashboard. uDoorMirror is the middle of a
            // door mirror's housing, along, out from the middle line and up, the same each side,
            // and uDoorView what of the picture behind its glass shows (Cabins.DOOR_VIEW_MIDDLE).
            // uLamps is whether the engine runs and whether the headlights are on.
            uniform vec3 uSeat;
            uniform float uSteer;
            uniform vec4 uTrim;
            uniform float uFittings;
            uniform vec4 uScreen;
            uniform vec2 uDash;
            uniform vec3 uDoorMirror;
            uniform vec3 uDoorView;
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
            // 0 with the Reflections row off: the picture behind is not drawn, and a mirror is plain glass.
            uniform float uBehindShown;
            uniform vec3 uGauges;
            uniform sampler2D uVinyl;
            uniform sampler2D uCloth;
            uniform sampler2D uJersey;
            uniform vec2 uWearGain;
            // 1 where the mod's own textures give skin and cloth their grain, 0 where a figure shows the game's texture alone.
            uniform float uWornGrain;
            uniform float uMoves;
            uniform vec4 uRoot[16];
            uniform float uRoots;
            uniform vec2 uCabinGain;
            uniform vec3 uOwnAmbient;
            uniform float uShines;
            uniform float uFigureAmbient;
            uniform sampler2D uFacing;
            uniform float uHaveFacing;
            uniform float uRaining;
            uniform float uTime;
            """ + BEAMS + MuzzleFlash.GLSL + Fires.GLSL + """
            // The vehicle sat in as its body is drawn this frame, in the world: the way it
            // faces, the side to the left of that and its own up. On its springs it tips
            // and rolls, and what the picture builds in it tips and rolls with the body.
            uniform vec3 uBodyAhead;
            uniform vec3 uBodyAside;
            uniform vec3 uBodyAbove;
            uniform sampler2D uRepainted;
            uniform float uWriteNear;

            // A way in the world as the vehicle sat in has it, along it, across it and up
            // it, and such a way back in the world.
            vec3 inCabin(vec3 v) {
                return vec3(dot(v, uBodyAhead), dot(v, uBodyAside), dot(v, uBodyAbove));
            }

            vec3 fromCabin(vec3 v) {
                return uBodyAhead * v.x + uBodyAside * v.y + uBodyAbove * v.z;
            }

            bool inside(vec3 c) {
                return c.x > 0.01 && c.x < 0.99 && c.y > 0.01 && c.y < 0.99 && c.z < 1.0;
            }

            float noiseAt(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
                float b = fract(sin(dot(cell + vec2(1.0, 0.0), vec2(12.9898, 78.233))) * 43758.5453);
                float c = fract(sin(dot(cell + vec2(0.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                float d = fract(sin(dot(cell + vec2(1.0, 1.0), vec2(12.9898, 78.233))) * 43758.5453);
                return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
            }

            // The driver's place is laid out at one and a half times the size a vehicle is
            // drawn at, and is brought down to it where it is traced.
            const float CABIN = 0.6667;

            // Distances to simple shapes, and the smooth joining of two of them, by the formulas Inigo
            // Quilez sets out (THIRD-PARTY-NOTICES.md, Acknowledgements).
            float block(vec3 v, vec3 halves, float round) {
                vec3 d = abs(v) - halves;
                return length(max(d, 0.0)) + min(max(d.x, max(d.y, d.z)), 0.0) - round;
            }

            float rod(vec3 v, vec3 a, vec3 b, float width) {
                vec3 along = b - a;
                return length(v - a - along * clamp(dot(v - a, along) / dot(along, along), 0.0, 1.0)) - width;
            }

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
            // What the part last painted is made of: 0 grained like leather, 1 woven cloth;
            // how strongly its grain shows; and how much of a mirror it is.
            float gWoven = 0.0, gGrain = 0.6, gGloss = 0.4;
            // How much of it is a lit display, which glows with the instruments.
            float gGlow = 0.0;

            // Parts past the first seven: 8 a door mirror, 9 what stands between the seats,
            // 10 the boot of a lever on the floor, 11 a post up the middle of the screen. The
            // rest are the glasshouse and the furnishings the picture builds.
            // While set, what is far from a place is only said to be at least so far off,
            // which is all a line of sight walking through the cabin asks of it: at 1, what
            // is more than 0.04 off, and at less, what is further off in proportion.
            float gRoughly = 0.0;

            // The driver's end of a cabin, which the game's model has as a six-sided ring on
            // a flat board: a wheel with a padded rim, a hub and its spokes, the stalks
            // behind it, the shroud of its column, a hooded binnacle for the instruments and
            // the mirror on the screen. p is measured from the eye along the vehicle, across
            // it and up; uWheel is the wheel's middle and radius and uWheelAxis the way its
            // column runs. Returns how far p is from the nearest of them, and in part which:
            // 1 rim, 2 hub, 3 spoke, 4 stalk, 5 shroud, 6 binnacle, 7 mirror.
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

            // The face of a round instrument, at p from its middle with its rim at 1. right
            // is its kind: 0 the speedometer, 1 the rev counter with the fuel gauge in its
            // foot, 2 a small fuel gauge, 3 a small gauge with its needle at the middle.
            // uGauges is the road speed, the engine's speed and the fuel, each as a part of
            // the gauge's full scale. Glow is how much of the colour is lit from behind.
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

            // A wiper goes through this much of a turn and back, in so many seconds.
            const float WIPER_SWEEP = 1.92, WIPER_BEAT = 1.5;

            // Where a place on the front screen is to one of its two wipers: x how far round
            // the blade's sweep it is, nought where the blade is parked to 1, and under
            // nought where the blade does not come, y how far from the wiper's foot, in tiles.
            vec2 bladeTo(vec2 on, int i) {
                vec2 rel = on - vec2((i == 0 ? -0.62 : 0.06) * gScreenHalf, -0.30);
                float far2 = length(rel);
                float q = atan(rel.y, rel.x) / WIPER_SWEEP;
                return vec2(q < 0.02 || q > 1.0 || far2 < 0.05 ? -1.0 : q, far2);
            }

            // What the wipers have done to a place on the front screen: x how many times a
            // blade has passed it since uTime last began again, both blades counted, and y
            // how many seconds ago the last was, 1000 where no blade comes.
            vec2 wipedAt(vec2 on) {
                float reach = 0.66 * gScreenHalf;
                float beats = uTime / WIPER_BEAT;
                float phase = fract(beats);
                vec2 found = vec2(0.0, 1000.0);
                for (int i = 0; i < 2; i++) {
                    vec2 to = bladeTo(on, i);
                    if (to.x < 0.0 || to.y >= reach) {
                        continue;
                    }
                    // A blade passes on its way up and again on its way back.
                    float up = 0.5 * to.x, back = 1.0 - up;
                    found.x += 2.0 * floor(beats) + step(up, phase) + step(back, phase);
                    found.y = min(found.y, min(fract(phase - up), fract(phase - back)) * WIPER_BEAT);
                }
                return found;
            }

            // Where a place on the front screen is to its two wipers. They are parked along
            // its foot, pointing away from the driver's side, and sweep up together through
            // a little over a right angle and back. x is 1 where a blade wipes, y 1 on the
            // line the blades leave at the end of their reach, z how long since a blade
            // last passed, 0 just now to 1 long enough for the rain to be back, and w 1 on
            // a blade itself while it rains.
            vec4 sweepAt(vec2 on) {
                float reach = 0.66 * gScreenHalf;
                float phase = fract(uTime / WIPER_BEAT);
                float at = phase < 0.5 ? 2.0 * phase : 2.0 - 2.0 * phase;
                vec4 found = vec4(0.0, 0.0, 1.0, 0.0);
                for (int i = 0; i < 2; i++) {
                    vec2 to = bladeTo(on, i);
                    float q = to.x, far2 = to.y;
                    if (q < 0.0) {
                        continue;
                    }
                    float past = (far2 - reach) / 0.012;
                    found.y = max(found.y, exp(-past * past));
                    if (far2 < reach) {
                        found.x = 1.0;
                        float since = phase < 0.5 ? (q < at ? phase - q * 0.5 : phase + q * 0.5) : (q > at ? phase - (1.0 - q * 0.5) : phase - q * 0.5);
                        found.z = min(found.z, clamp(since * WIPER_BEAT / 1.1, 0.0, 1.0));
                        found.w = max(found.w, 1.0 - smoothstep(0.004, 0.011, abs(q - at) * WIPER_SWEEP * far2));
                    }
                }
                found.y *= 1.0 - found.x;
                return found;
            }

            vec4 flared(vec4 glass, vec3 look, float dirt);
            """;

    /**
     * The rest of the people's light up to its main, from the rain and snow on a vehicle's panes on:
     * Pipeline joins it on after FIGURE_LIGHT_PARTS. As one constant with it the shader is longer than a class file can hold.
     */
    static final String FIGURE_LIGHT_PANES = GlassRain.GLSL + """

            // A pane seen from the seat, over the world behind it: the sky it mirrors, more at
            // a glancing angle, and the dust and dried rain on it, which stay put on the glass
            // as the car moves, and the rain on it: see GlassRain. on is the place on the
            // pane, in tiles.
            vec4 pane(vec2 on, vec3 n, vec3 view, float swept) {
                float dust = smoothstep(0.35, 0.85, noiseAt(on * 5.0) * 0.55 + noiseAt(on * 15.0 + 3.0) * 0.3 + noiseAt(on * 61.0) * 0.15);
                float runs = smoothstep(0.62, 0.95, noiseAt(vec2(on.x * 46.0, on.y * 2.2)));
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
                    glass = rained(glass, on, swept, wiper);
                }
                if (uSnowing > 0.0) {
                    glass = snowed(glass, on, swept, wiper);
                }
                return flared(glass, -view, dirt);
            }

            vec2 paneOut(vec3 o, vec3 d);

            // The pane a line of sight from the seat leaves the cabin by: the screens lean back
            // and the side windows stand nearly upright. With the driver's place laid out, the
            // place on it is where the line meets that pane's glass, counted in the vehicle from
            // the driver's eye with no lean (uSeat), so a lean of the head leaves what is on the
            // glass where it is. With none, the panes are taken to stand a set way off the eye.
            vec4 paneSeen(vec3 out3) {
                vec3 line = inCabin(out3);
                float along = line.x, across = line.y;
                bool ends;
                vec3 at;
                if (uInside > 0.5 && uWheel.w > 0.0) {
                    float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                    vec2 leaves = paneOut(vec3(uSeat.x, (uSeat.y - uWheel.y) * toward, uSeat.z), line * vec3(1.0, toward, 1.0));
                    ends = leaves.y < 0.5;
                    at = (uSeat + line * leaves.x) * CABIN;
                } else {
                    ends = abs(along) > abs(across);
                    at = line * (ends ? 0.9 / max(abs(along), 0.2) : 0.5 / max(abs(across), 0.2));
                }
                vec3 faces = fromCabin(ends ? normalize(vec3(-sign(along), 0.0, 0.75)) : normalize(vec3(0.0, -sign(across), 0.12)));
                return pane(vec2(ends ? at.y : at.x + 7.0, at.z), faces, -out3, ends && along > 0.0 ? 1.0 : 0.0);
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
                vec2 apart = pixels / uSize;
                vec3 east = placeAt(uv + vec2(apart.x, 0.0)) - at, west = at - placeAt(uv - vec2(apart.x, 0.0));
                vec3 north = placeAt(uv + vec2(0.0, apart.y)) - at, south = at - placeAt(uv - vec2(0.0, apart.y));
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
                vec2 apart = pixels / uSize;
                vec3 east = partAt(uv + vec2(apart.x, 0.0)) - at, west = at - partAt(uv - vec2(apart.x, 0.0));
                vec3 north = partAt(uv + vec2(0.0, apart.y)) - at, south = at - partAt(uv - vec2(0.0, apart.y));
                vec3 across = dot(east, east) < dot(west, west) ? east : west;
                vec3 up = dot(north, north) < dot(south, south) ? north : south;
                vec3 square = cross(across, up);
                float size = length(square);
                return size > 1e-12 ? square / size : vec3(0.0);
            }

            // What the picture of the world behind the vehicle shows at a place in it, and
            // the sky where the world stops.
            vec3 behindAt(vec2 on) {
                if (uBehindShown < 0.5) {
                    // One even sheen of the sky behind, faint: what is left of a mirror with nothing in it.
                    return atmosphere(normalize(fromCabin(vec3(-1.0, 0.0, 0.25)))) * 0.3;
                }
                vec3 seen = texture2D(uBehind, on).rgb;
                if (seen.r < -0.5) {
                    seen = atmosphere(normalize(fromCabin(vec3(-1.0, (0.5 - on.x) * 0.6, (on.y - 0.5) * 0.17 + 0.02))));
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
                // For the steadying pass, beside the picture where there is such a place: what
                // is lit here moves by itself.
                gl_FragData[1] = vec4(uMoves);
                vec2 uv = gl_FragCoord.xy / uSize;
                vec4 figure = texture2D(uFigure, uv);
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 ray = uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y);
                // The instruments in the dash behind the wheel, seen through its rim.
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
                    vec3 line = inCabin(d);
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
                    vec3 line = inCabin(d);
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
                        wheelFaces = fromCabin(slope);
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
                        if (!open && kind < 3.5 || inCabin(out3).z < -0.3 && open) {
                            discard;
                        }
                        gl_FragData[0] = paneBuilt(out3);
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
                if (figure.a < 0.5 && !wheel && !dial && !bonnetSeen) {
                    // From a seat the vehicle is all round the eye, so where the game drew
                    // none of it above the sills there is an opening with a pane in it.
                    vec3 out3 = normalize(ray);
                    if (uGlassPass < 0.5 || inCabin(out3).z < -0.3) {
                        discard;
                    }
                    gl_FragData[0] = paneSeen(out3);
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
                    // Taken from the pixels beside it by reading them: the slope across the
                    // screen that dFdx gives is not defined in a branch its neighbours may not take.
                    vec3 square = partNormalAt(uv, partAt(uv), 1.0);
                    square = dot(square, square) > 0.5 ? square : vec3(0.0, 0.0, 1.0);
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
                    n = -fromCabin(uWheelAxis);
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
                    vec3 q = uSeat + inCabin(to) / CABIN - uWheel.xyz;
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
                    vec3 local = uSeat + inCabin(to);
                    vec3 faces = abs(inCabin(n));
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
                        float low = uParted.x > 0.5 ? step(local.z, uScreen.y - 0.02) : step(dot(at - uEye, uBodyAbove), -0.06);
                        figure.rgb = mix(figure.rgb, uDashColor * (0.35 + most * 3.2), (1.0 - woven) * dark * 0.6 * low);
                        grained = mix(0.9 * dark, 0.45, woven);
                    }
                    vec3 grain = texture2D(uVinyl, lie * 2.6).rgb * uCabinGain.x;
                    if (builtCabin && woven > 0.5) {
                        // Cloth is a nap of short fibres, and on a seat or a door a weave of
                        // threads over it, which is let go where a thread is finer than a pixel.
                        float nap = dot(texture2D(uCarpet, lie * 8.0).rgb, vec3(0.299, 0.587, 0.114)) * uCarpetGain;
                        vec2 thread = lie / 0.0045;
                        float fine = clamp(1.0 - 1.4 * max(fwidth(thread.x), fwidth(thread.y)), 0.0, 1.0);
                        float weave = 1.0 - 0.3 * fine * abs(sin(3.1416 * thread.x) * sin(3.1416 * thread.y));
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
                    float shown = uWornGrain * (1.0 - smoothstep(4.0, 11.0, ahead * length(ray)));
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
                if (cabin > 0.5 && !builtCabin && (uHavePanes > 0.5 || dot(at - uEye, uBodyAbove) > -0.23) && !wheel && !dial && !bonnetSeen) {
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
                        vec3 to = inCabin(at - uEye) / CABIN;
                        // The place on the pane is the vehicle's, counted from the driver's eye
                        // with no lean (uSeat): a lean leaves the drops and the wipers' arcs where they are.
                        vec3 on3 = to + uSeat;
                        vec3 faces = inCabin(n);
                        bool ends = abs(faces.x) > abs(faces.y);
                        gl_FragData[0] = pane(vec2(ends ? on3.y : on3.x + 7.0, on3.z), n, view, ends && to.x > 0.0 ? 1.0 : 0.0);
                        gl_FragDepth = 0.0;
                        return;
                    }
                } else if (uGlassPass > 0.5) {
                    discard;
                }
                vec3 sunLight = pow(uLightColor, vec3(2.2));
                float facing = clamp((dot(n, uLight) + 0.25) / 1.25, 0.0, 1.0);
                vec3 fromSky = mix(vec3(0.42, 0.40, 0.36), vec3(0.72, 0.80, 0.95), 0.5 + 0.5 * n.z);
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
                    vec3 place = uSeat + inCabin(to);
                    vec3 turned = inCabin(n);
                    float shade = 1.0;
                    if (wheel) {
                        // What stands close before a built part keeps the light off it.
                        vec3 out3 = inCabin(wheelFaces);
                        shade = 0.4;
                        for (int probe = 0; probe < 2; probe++) {
                            float off = probe == 0 ? 0.025 : 0.08;
                            shade += 0.3 * clamp(wheelAt(wheelPlace + out3 * off) / off, 0.0, 1.0);
                        }
                    }
                    color = cabinLit(place, turned, inCabin(view), pow(figure.rgb, vec3(2.2)), sheen, skyGives, sunOut, shade, sunLight, inCabin(uLight));
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
                vec3 lamps = beams(at, n, 0.25) * (1.0 - cabin) + flashLight(at, n) + fireLight(at, n);
                color += pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * lamps;
                if (wheel && wheelPart > 6.5 && wheelPart < 7.5 && abs(wheelPlace.y - uMirror.y) < 0.112 && abs(wheelPlace.z - uMirror.z) < 0.029 && wheelPlace.x < uMirror.x) {
                    // The glass shows the world behind, left for right, and the sky where the
                    // world stops.
                    vec2 on = vec2(0.5 - (wheelPlace.y - uMirror.y) / 0.224, 0.5 + (wheelPlace.z - uMirror.z) / 0.058);
                    color = behindAt(on);
                }
                if (wheel && wheelPart > 7.5 && wheelPart < 8.5) {
                    float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                    float c = (wheelPlace.y - uWheel.y) * toward - abs(uDash.x);
                    // 1 on the driver's side and -1 on the other.
                    float side = c < 0.0 ? 1.0 : -1.0;
                    vec3 housed = vec3(wheelPlace.x - uDoorMirror.x, abs(c) - uDoorMirror.y, wheelPlace.z - uDoorMirror.z);
                    if (abs(housed.y) < 0.08 && abs(housed.z) < 0.04 && housed.x < -0.03) {
                        // A door mirror's glass: the road behind on its own side, out of the
                        // same picture as the mirror on the screen, and the vehicle's own
                        // flank down its inner edge.
                        float across = -toward * side * housed.y;
                        vec2 on = vec2(0.5 - toward * side * uDoorView.x + across / 0.08 * uDoorView.y, 0.5 + housed.z / 0.04 * 0.42);
                        float edge = 0.08 - 0.16 * uDoorView.z;
                        float flank = smoothstep(edge - 0.004, edge, -housed.y + (housed.z + 0.04) * 0.05);
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
                    vec3 dials = uEye + fromCabin(vec3(0.62, 0.0, -0.46)) * CABIN;
                    vec3 to = dials - at;
                    float far2 = dot(to, to);
                    float given = 0.022 / (0.009 + far2) * (0.25 + 0.75 * max(dot(n, to * inversesqrt(max(far2, 0.0001))), 0.0));
                    color += (pow(figure.rgb / max(figure.a, 0.001), vec3(2.2)) * 0.6 + 0.12) * uGlowColor * (given * uInstruments);
                }
                // The trim of a cabin is cloth and dull plastic, whatever the body outside is.
                // In the modes that mirror nothing, paint takes the sky and the sun as cloth does.
                float lacquer = uPainted * (1.0 - roofed) * uShines;
                float glancing = 0.04 + 0.96 * pow(clamp(1.0 - dot(n, view), 0.0, 1.0), 5.0);
                // The bonnet is seen from the seat at a glance, where lacquer mirrors most and
                // shows least of the paint under it.
                color *= 1.0 - 0.45 * glancing * bonnet * uShines;
                color += skyBack * mix(0.22 * glance, 0.1 + 0.6 * glance, lacquer) * (1.0 - 0.7 * roofed) * (1.0 - 0.6 * bonnet) * (1.0 - housed);
                // Cloth and skin scatter the sun and do not mirror it: with a hard glint a sleeve is a
                // length of rubber hose.
                color += sunLight * (pow(max(dot(back, uLight), 0.0), mix(48.0, 260.0, lacquer)) * mix(0.45 * (1.0 - 0.85 * bare), 3.0, lacquer) * sun) * (1.0 - housed);
                // The sky lies along it, and the sun in it is a broad soft light round the
                // small hard one.
                color += bonnet * uShines * (skyBack * (0.3 * glancing) + sunLight * (pow(max(dot(back, uLight), 0.0), 22.0) * 0.5 * sun));
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
                        around += uFlashSky * ((0.6 + 0.4 * max(n.z, 0.0)) * outdoors);
                    }
                    color = dressed(pow(figure.rgb, vec3(2.2)), n, view, strand, worn, gloss, low, uLight, sunLight * sun, around, lamps, skyBack);
                }

                // A vehicle's lamps that are on shine whatever light it stands in.
                color += lampLight(uv);
                float range = max(ahead * length(ray) - uRangeFrom, 0.0);
                // With the far land behind them, figures past where the built world ends are left out as it is: see thinnedOut.
                if (thinnedOut(ray.xy * ahead)) {
                    discard;
                }
                float ends = uBeyond > 0.5 ? 0.0 : max(smoothstep(uFogStart, uFogEnd, range), smoothstep(uLoaded.z - (uFogEnd - uFogStart), uLoaded.z, length(ray.xy * ahead - uLoaded.xy)));
                float haze = max(ends, distanceHaze(normalize(ray), range));
                color = mix(color, atmosphere(normalize(ray)), haze);
                if (uVeil.x > 0.0) {
                    color = mix(color, veilColor(normalize(ray)), max(veiling(range), ends * skyVeiling(normalize(ray))));
                }
                // Alpha says how much of a mirror the surface is, 1 for none: the last pass
                // traces what the bonnet's lacquer shows of the world over it.
                gl_FragData[0] = vec4(color, 1.0 - 0.5 * bonnet * uShines);
                // The viewer's own body and vehicle are drawn with a depth of their own. Here
                // it is put into the picture's depth, in the picture's own measure, so that
                // what is worked out from depth afterwards, the shade in corners and the
                // steadying over time, sees them and not the ground behind them.
                gl_FragDepth = uWriteNear > 0.0 ? 0.5 + 0.5 * (uFar + uWriteNear - 2.0 * uWriteNear * uFar / ahead) / (uFar - uWriteNear) : gl_FragCoord.z;
            }
            """;

    static final String DEVELOP = "#version 120\n" + Sky.ATMOSPHERE + """
            uniform sampler2D uScene;
            uniform sampler2D uBloom;
            uniform sampler2D uDepth;
            uniform vec3 uWithout;
            uniform sampler2D uOcclusion;
            uniform sampler2D uVolume;
            uniform vec2 uSize;
            // The size of the passes drawn at half the picture's: the shade and the lit air.
            uniform vec2 uLow;
            uniform float uExposure;
            // The picture's style, see Style: how far it is bent towards an S, how full its
            // colours are and how dark its corners; how much of the glow and of the sun's glare
            // in the lens is shown; and what its dark and its bright are multiplied by.
            uniform vec3 uGrade;
            uniform vec2 uLens;
            uniform vec3 uShade;
            uniform vec3 uSunlit;
            uniform vec2 uSunAt;
            uniform vec3 uRayColor;
            uniform float uAspect;
            uniform float uTime;

            uniform vec2 uTan;
            uniform float uNear;
            uniform float uFar;
            uniform vec3 uUp;
            // What is outlined, drawn in the colour of its line over nothing (Pipeline.drawOutlined),
            // and whether anything is.
            uniform sampler2D uOutlines;
            uniform float uOutlined;
            // The eye's axes in the world.
            uniform vec3 uAcross;
            uniform vec3 uUpward;
            uniform vec3 uAhead;
            // Fog over the game's own view. uFog is how thick it lies and how far it has drifted,
            // uFogViewer is where the viewer's feet are, uFogFrom is the place at the middle of
            // the picture counted from there, and uFogSpan is half the picture's width and
            // height at that place, in tiles.
            uniform vec3 uFog;
            uniform vec3 uFogFrom;
            uniform vec3 uFogViewer;
            uniform vec2 uFogSpan;
            uniform vec3 uFogColor;
            // How narrow the character's view has become, 0 to 1: see Eyesight.narrowed.
            uniform float uNarrowed;
            // As the world's: what the air hides is counted from this far off.
            uniform float uRangeFrom;
            """ + SHELTERED + """

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
            // finds nothing and the sky stays. p is where the pixel is and square stands square to
            // the surface there: main works both out for every pixel, since a slope across the
            // screen taken only where a pixel is a mirror has no neighbour to be taken from at the
            // mirror's rim.
            vec3 walked(vec2 uv, vec4 scene, vec3 p, vec3 square) {
                float mirror = 1.0 - scene.a;
                if (mirror < 0.02) {
                    return scene.rgb;
                }
                vec3 line = normalize(p);
                // Which way the mirror faces is read off the picture's depth: a floor faces up,
                // a window pane faces out of its wall.
                vec3 facing = normalize(square);
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
                float glance = 0.04 + 0.96 * pow(1.0 - clamp(dot(-line, facing), 0.0, 1.0), 5.0);
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
                // picture, clouds and all, and that is what
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
            vec3 mirrored(vec2 uv, vec4 scene, vec3 p, vec3 square) {
                float near = 1.0 - smoothstep(45.0, 80.0, p.z);
                return near > 0.0 ? mix(scene.rgb, walked(uv, scene, p, square), near) : scene.rgb;
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
                    float disc = 1.0 - smoothstep(size * 0.6, size, length(off));
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

            // A half-size pass read at a full-size pixel: of the four of its dots round the
            // pixel, mostly those as far off as the pixel is. Read as the plain mean of the four,
            // the air and the shade that belong to what is far off lie over the edge of whatever
            // is held near the eye, a weapon or a hand, in blocks two pixels across.
            vec4 ofItsOwnDepth(sampler2D low, vec2 uv, float ahead) {
                vec2 at = uv * uLow - 0.5;
                vec2 whole = floor(at), part = at - whole;
                vec4 sum = vec4(0.0);
                float total = 0.0;
                for (int i = 0; i < 4; i++) {
                    vec2 corner = vec2(mod(float(i), 2.0), floor(float(i) / 2.0));
                    vec2 dot1 = (whole + corner + 0.5) / uLow;
                    float w = mix(1.0 - part.x, part.x, corner.x) * mix(1.0 - part.y, part.y, corner.y);
                    w = (w + 0.01) / (0.02 + abs(placeAt(dot1).z - ahead) / ahead);
                    sum += texture2D(low, dot1) * w;
                    total += w;
                }
                return sum / total;
            }

            // Banks of fog as a pattern over the map that comes round every 32 squares of its
            // lattice, so that the drift it is carried by can start again from nothing.
            float banks(vec2 q) {
                vec2 cell = floor(q), f = fract(q);
                f = f * f * (3.0 - 2.0 * f);
                vec2 next = mod(cell + 1.0, 32.0);
                cell = mod(cell, 32.0);
                return mix(mix(hash(cell), hash(vec2(next.x, cell.y)), f.x), mix(hash(vec2(cell.x, next.y)), hash(next), f.x), f.y);
            }

            // How much of the picture the fog hides at a pixel, seen from far overhead, where
            // every line of sight comes down through the fog at the same slant whatever it
            // lands on. The game's own view has its fog as a sheet over everything with a
            // clearing round the player, and so has this: a sheet at the height of the
            // viewer's feet, thin for a few tiles round them, thickening to banks that drift on
            // the wind, and none over a room the picture has opened. Where the line of sight
            // crosses the sheet is worked out from the pixel alone and not from the picture's
            // depth: placed by that depth, the fog had runs of pixels over grass left clear.
            float fogged(vec2 ndc) {
                vec3 from = uFogFrom + uAcross * (ndc.x * uFogSpan.x) + uUpward * (ndc.y * uFogSpan.y);
                from -= uAhead * (from.z / uAhead.z);
                vec3 at = from + uFogViewer;
                if (sheltered(at + vec3(0.0, 0.0, 0.1))) {
                    return 0.0;
                }
                vec2 q = (at.xy + uFog.yz) / 8.0;
                float bank = mix(banks(q), banks(q * 2.0 + 11.0), 0.35);
                float thick = smoothstep(2.5, 7.0, length(from.xy) + (bank - 0.5) * 3.0);
                return uFog.x * mix(0.16, 0.8 + 0.18 * bank, thick);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec3 p = placeAt(uv);
                vec3 square = cross(dFdx(p), dFdy(p));
                float ahead = p.z;
                // A pass that is switched off has drawn nothing into its picture, and what a
                // picture nothing was drawn into holds is the driver's to choose: it is not read.
                vec4 around = vec4(0.0, 0.0, 0.0, 1.0);
                if (uWithout.x < 0.5) {
                    around = ofItsOwnDepth(uOcclusion, uv, ahead);
                }
                vec4 here = texture2D(uScene, uv);
                // The world's shader has already hidden in the fog, rain or snow as much of the
                // surface as the air between hides, and the shade, the lent light and what the
                // surface mirrors are hidden by as much: laid whole over the veiled colour, the
                // shade drew every window frame, roof edge and tree of a building lost in thick
                // fog as dark lines through it.
                float seen = 1.0 - veiling(max(length(p) - uRangeFrom, 0.0));
                vec3 c = mix(here.rgb, mirrored(uv, here, p, square), seen) * mix(1.0, around.a, 0.62 * seen);
                // The light lent by what stands near, taken on by the surface in its own colour.
                vec3 lent = around.rgb / max(1.0 - around.rgb, vec3(0.02));
                float own = max(here.r, max(here.g, here.b));
                c += lent * (here.rgb / max(own, 0.02)) * (0.22 * seen);
                if (uRayColor.r + uRayColor.g + uRayColor.b > 0.0) {
                    c += shafts(uv);
                }
                if (uWithout.y < 0.5) {
                    c += ofItsOwnDepth(uVolume, uv, ahead).rgb;
                }
                if (uFog.x > 0.0) {
                    c = mix(c, uFogColor, fogged(uv * 2.0 - 1.0));
                }
                if (uLens.y > 0.0) {
                    c += flare(uv) * uLens.y;
                }
                c += texture2D(uBloom, uv).rgb * (uLens.x * (1.0 - uWithout.z));
                if (uNarrowed > 0.0) {
                    // A narrowed view: what is off to the side goes dark, from the corners in.
                    float aside = length((uv - 0.5) * vec2(uAspect, 1.0) * 2.0);
                    c *= 1.0 - 0.8 * uNarrowed * smoothstep(mix(1.3, 0.35, uNarrowed), mix(1.9, 1.0, uNarrowed), aside);
                }
                c = film(c * uExposure);
                c = pow(c, vec3(1.0 / 2.2));
                c = mix(c, c * c * (3.0 - 2.0 * c), uGrade.x);
                // In the Vivid style a clear day photographs warm where the sun falls and cool
                // in the shade, with its greens and blues full.
                float grey = dot(c, vec3(0.2126, 0.7152, 0.0722));
                c = mix(vec3(grey), c, uGrade.y);
                c *= mix(uShade, uSunlit, smoothstep(0.25, 0.8, grey));
                vec2 fromCentre = uv - 0.5;
                c *= 1.0 - dot(fromCentre, fromCentre) * uGrade.z;
                c += (hash(gl_FragCoord.xy + uTime * 61.0) - 0.5) / 160.0;
                if (uOutlined > 0.5) {
                    // What is outlined has a line round it, where it meets what is not it, in
                    // its own colour, and is a little lighter all over. The line is as wide on
                    // a large picture as on a small one.
                    vec2 apart = vec2(uSize.y / 400.0) / uSize;
                    vec4 here = texture2D(uOutlines, uv);
                    vec4 right = texture2D(uOutlines, uv + vec2(apart.x, 0.0)), left = texture2D(uOutlines, uv - vec2(apart.x, 0.0));
                    vec4 up = texture2D(uOutlines, uv + vec2(0.0, apart.y)), down = texture2D(uOutlines, uv - vec2(0.0, apart.y));
                    float edge = max(max(abs(right.a - here.a), abs(left.a - here.a)), max(abs(up.a - here.a), abs(down.a - here.a)));
                    // Outside the thing the line takes the colour of the side that is it.
                    vec3 line = here.a > 0.5 ? here.rgb : (right.rgb * right.a + left.rgb * left.a + up.rgb * up.a + down.rgb * down.a)
                            / max(right.a + left.a + up.a + down.a, 1.0);
                    c = mix(c + vec3(0.05, 0.045, 0.03) * here.a, line, 0.9 * edge);
                }
                gl_FragColor = vec4(c, 1.0);
            }
            """;

    /**
     * The picture steadied over time. Leaves in the wind, the grain of the soft shadows and of
     * the thin edges of cards all change from one frame to the next, and what changes every
     * frame is seen as flicker. Each pixel is mixed with what was at the same place in the
     * world a frame ago: where that place was on the last frame's picture is worked out from
     * the pixel's depth and where the eye then was.
     *
     * What the last frame had there is only kept where it was the same thing. Two checks say
     * so. The last frame's depth there has to be the depth the place then had: where a person
     * has walked on, the ground behind them was not in the last frame at all, and takes
     * nothing from it. And its colour is drawn in to the colours round the pixel now, by how
     * much they vary, so what has changed its look where it stands goes with the new look.
     */
    static final String STEADY = """
            #version 120
            uniform sampler2D uScene;
            uniform sampler2D uBefore;
            uniform sampler2D uDepth;
            uniform sampler2D uAheadBefore;
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
            uniform vec3 uAimEye;
            uniform vec3 uAimRight;
            uniform vec3 uAimUp;
            uniform vec3 uAimForward;
            uniform vec3 uAimEyeBefore;
            uniform vec3 uAimRightBefore;
            uniform vec3 uAimUpBefore;
            uniform vec3 uAimForwardBefore;
            uniform float uKept;
            uniform float uOwnWithin;
            uniform float uHeldWithin;
            uniform sampler2D uMoves;
            uniform vec4 uMover[16];
            uniform vec4 uMoverBefore[16];
            uniform float uMovers;

            // The picture kept from before, read at a place between its pixels. Read as the
            // mean of the four pixels round the place, each frame of a moving head blurs what
            // the last one kept, and the whole picture goes soft while the head moves and
            // sharp when it stops: with a tracked head, which never quite stops, it pulses.
            // Read through a curve fitted to the sixteen pixels round the place, it keeps
            // its edges. The sixteen are had from nine reads by letting the card mix pairs.
            // The weights of the curve, and the pairs of dots the card is left to mix, are MJP's
            // SampleTextureCatmullRom, used under its MIT licence: THIRD-PARTY-NOTICES.md.
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

            // How far ahead of the eye what a pixel shows is, in tiles.
            float aheadAt(vec2 uv) {
                float z = texture2D(uDepth, uv).r * 2.0 - 1.0;
                return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear));
            }

            // A colour as its brightness and two differences of colour, with the brightness
            // pressed into nought to one. The scene's light has no top to it: mixed as it is,
            // one glint outweighs everything it is mixed with, and the range of colours round
            // a pixel with a glint in it takes anything.
            vec3 pressed(vec3 c) {
                c /= 1.0 + dot(c, vec3(0.25, 0.5, 0.25));
                return vec3(dot(c, vec3(0.25, 0.5, 0.25)), dot(c, vec3(0.5, 0.0, -0.5)), dot(c, vec3(-0.25, 0.5, -0.25)));
            }

            vec3 unpressed(vec3 c) {
                c = vec3(c.x + c.y - c.z, c.x + c.z, c.x - c.y - c.z);
                return c / max(1.0 - dot(c, vec3(0.25, 0.5, 0.25)), 0.001);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec2 px = 1.0 / uSize;
                vec4 now = texture2D(uScene, uv);
                float own = aheadAt(uv);
                gl_FragData[1] = vec4(own);
                // An edge's pixel shows what is before the edge in one frame and what is behind
                // it in the next, as the picture is drawn a fraction of a pixel aside. It goes
                // with the nearest thing of the pixel and the four beside it, so the edge of a
                // thing is looked for where the thing was.
                vec2 nearest = uv;
                float ahead = own, steep = 0.0;
                for (int i = 0; i < 4; i++) {
                    vec2 beside = uv + (i == 0 ? vec2(px.x, 0.0) : i == 1 ? vec2(-px.x, 0.0) : i == 2 ? vec2(0.0, px.y) : vec2(0.0, -px.y));
                    float there = aheadAt(beside);
                    steep = max(steep, abs(there - own));
                    if (there < ahead) {
                        ahead = there;
                        nearest = beside;
                    }
                }
                vec2 ndc = nearest * 2.0 - 1.0;
                vec3 at = uEye + (uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y)) * ahead;
                // What is held in the hands is not a thing of the world that stood still. It
                // stands in the aim's view: it turns as the mouse turns the view and stays put
                // on the picture, and it stays behind as the head turns from the aim. Where it
                // was is where the same place of the aim's view then was.
                vec3 stood = at;
                float moves = texture2D(uMoves, nearest).r;
                bool lost = false;
                if (ahead < uHeldWithin) {
                    vec3 inAim = at - uAimEye;
                    stood = uAimEyeBefore + uAimRightBefore * dot(inAim, uAimRight) + uAimUpBefore * dot(inAim, uAimUp) + uAimForwardBefore * dot(inAim, uAimForward);
                } else if (moves > 0.25) {
                    // A person or a vehicle goes as a whole: where it stood and how it was
                    // turned a frame ago say where this point of it then was. It is the
                    // nearest of those noted whose reach the point is in, and with none this
                    // is one of a crowd past the nearest sixteen, of which nothing is known.
                    float off = moves > 0.75 ? 1.5 : 4.5;
                    lost = true;
                    // Fire moves within itself and goes nowhere: a person beside it says nothing of it (FireDraw.MOVES).
                    float movers = abs(moves - 0.375) < 0.06 ? 0.0 : uMovers;
                    for (int i = 0; i < 16; i++) {
                        if (float(i) < movers) {
                            float far = distance(at.xy, uMover[i].xy);
                            if (far < off) {
                                off = far;
                                lost = false;
                                float turned = uMoverBefore[i].w - uMover[i].w;
                                vec3 limb = at - uMover[i].xyz;
                                stood = uMoverBefore[i].xyz + vec3(cos(turned) * limb.x - sin(turned) * limb.y, sin(turned) * limb.x + cos(turned) * limb.y, limb.z);
                            }
                        }
                    }
                }
                vec3 from = stood - uEyeBefore;
                float was = dot(from, uForwardBefore);
                vec2 moved = vec2(dot(from, uRightBefore) / (was * uTan.x), dot(from, uUpBefore) / (was * uTan.y)) * 0.5 + 0.5 - nearest;
                vec2 before = uv + moved;
                // The vehicle the viewer rides in goes with the eye: where it was in the world a
                // frame ago says nothing of where it was in the picture.
                if (uKept <= 0.0 || own < uOwnWithin || was <= uNear || before.x < 0.0 || before.x > 1.0 || before.y < 0.0 || before.y > 1.0) {
                    gl_FragData[0] = now;
                    return;
                }
                // Whether the last frame showed the same thing there: one of the four pixels
                // round the place was as far ahead of the eye then as this place was. A slope
                // seen along its length is further off by the pixel, so the depths beside this
                // pixel say how much is allowed.
                float allowed = 0.03 * was + 2.0 * steep;
                vec2 corner = floor(before * uSize - 0.5) + 0.5;
                bool same = false;
                for (int i = 0; i < 4; i++) {
                    float then = texture2D(uAheadBefore, (corner + vec2(float(i / 2), float(i - i / 2 * 2))) * px).r;
                    same = same || abs(then - was) <= allowed;
                }
                if (!same) {
                    gl_FragData[0] = now;
                    return;
                }
                vec3 mine = pressed(now.rgb);
                vec3 sum = vec3(0.0), squares = vec3(0.0), least = mine, most = mine;
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        vec3 near = pressed(texture2D(uScene, uv + vec2(float(x), float(y)) * px).rgb);
                        sum += near;
                        squares += near * near;
                        least = min(least, near);
                        most = max(most, near);
                    }
                }
                // Held to the colours round the pixel now. What moves by itself also moves
                // within itself, a leg past a leg, and is held closer: no further from the
                // mean of those colours than they vary. Held that close, a fine line of the
                // still world came out fainter (the picture's measure of detail fell by a
                // tenth), so the world is given the whole range.
                vec3 low = least, high = most;
                if (moves > 0.25) {
                    vec3 mean = sum / 9.0;
                    vec3 varies = sqrt(max(squares / 9.0 - mean * mean, vec3(0.0)));
                    low = max(least, mean - varies);
                    high = min(most, mean + varies);
                }
                vec3 middle = 0.5 * (low + high), reach = 0.5 * (high - low) + 0.00001;
                vec3 held = pressed(sharply(before)) - middle;
                vec3 over = abs(held) / reach;
                held = middle + held / max(1.0, max(over.x, max(over.y, over.z)));
                // Read between its pixels the kept picture comes back a little softer, and it is
                // read again every frame: kept at 93 parts in 100, a line three pixels wide is
                // left with a fifth of its contrast while the view turns, and the whole
                // picture is a blur. What one read leaves of such a line is known from how far
                // between pixels it falls, 1 - 0.312 sin^2 of that part of a pixel each way.
                // No more is kept than leaves the line four fifths of its contrast: with a
                // read leaving p of it, a share k kept leaves (1 - k) / (1 - k p). Asked for
                // nine tenths, a slow turn flickered more than with no steadying at all.
                vec2 between = sin(3.14159265 * fract(moved * uSize));
                float passes = (1.0 - 0.312 * between.x * between.x) * (1.0 - 0.312 * between.y * between.y);
                float kept = min(lost ? 0.5 : uKept, 0.2 / (1.0 - 0.8 * passes));
                gl_FragData[0] = vec4(unpressed(mix(mine, held, kept)), now.a);
            }
            """;

    /**
     * Smooths the stair-steps of edges, looking along each edge for how far to blend. The blend
     * is the FXAA pass of glsl-fxaa, used under its BSD and MIT licences: the notices are in
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

    /** The far land: see FarLand. A vertex is where it is on the map, as the world's are, and the eye's place is taken off it here. */
    static final String FAR_VERTEX = """
            #version 120
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec4 vColor;
            varying float vUp;
            uniform vec3 uEye;

            void main() {
                vWorld = gl_Vertex.xyz;
                vNormal = gl_Normal;
                vColor = gl_Color;
                vUp = gl_MultiTexCoord0.x;
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(gl_Vertex.xyz - uEye, 1.0);
            }
            """;

    /**
     * What is beyond the loaded map is lit as open ground of the built world is, by the same
     * sun, sky and clouds, and goes into the same air, so that where the one thins out into the
     * other the two are one country. It has no shadows but the clouds'. The far land and the far
     * trees are lit by this alike: farLit, of a surface's colour where it is and the way it faces,
     * with how much of it is water.
     */
    static final String FAR_LIGHT = """
            uniform vec3 uEye;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform vec3 uOpenLight;
            uniform float uDaylightKept;
            uniform float uCover;
            uniform vec2 uDrift;
            // How far from the eye, along the ground, the far land itself ends.
            uniform float uReach;

            vec3 farLit(vec3 world, vec3 n, vec3 surface, float water) {
                vec3 toEye = uEye - world;
                float away = length(toEye);
                vec3 view = toEye / away;
                float facing = max(dot(n, uLight), 0.0);
                // The clouds' shadows, as the built world has them. Past where one can be told
                // from the next, the land is as dull as they make all of it.
                vec2 under = (world.xy + uLight.xy / max(uLight.z, 0.25) * 30.0) / 22.0 + uDrift * 6.0;
                float body = patches(under) * 0.6 + patches(under * 2.7 + 7.0) * 0.4;
                float shaded = smoothstep(-0.06, 0.06, body - (1.12 - uCover));
                shaded = mix(shaded, clamp((uCover - 0.62) / 0.5, 0.0, 1.0), smoothstep(250.0, 500.0, away));
                float reached = 1.0 - 0.65 * shaded;
                vec3 sunLight = pow(uLightColor, vec3(2.2));
                vec3 lightHere = pow(uOpenLight, vec3(2.2)) * uDaylightKept * vec3(0.72, 0.80, 0.95) * (0.72 + 0.28 * n.z)
                        + sunLight * (2.1 * reached * facing)
                        + sunLight * vec3(0.9, 0.86, 0.74) * (0.3 * max(uLight.z, 0.0) * (1.0 - abs(n.z)))
                        + uFlashSky * (0.6 + 0.4 * max(n.z, 0.0));
                vec3 color = surface * lightHere;
                if (water > 0.0) {
                    vec3 mirrored = reflect(-view, vec3(0.0, 0.0, 1.0));
                    float glance = 0.03 + 0.97 * pow(1.0 - clamp(view.z, 0.0, 1.0), 5.0);
                    color = mix(color, ridges(mirrored, atmosphere(mirrored)), glance * water);
                }

                vec3 air = atmosphere(-view);
                color = mix(color, air, distanceHaze(-view, away));
                // The far land ends too, in what the sky's own shader draws where nothing is: the hills, and under the skyline the air.
                float ends = smoothstep(uReach * 0.85, uReach, length(toEye.xy));
                if (ends > 0.0) {
                    vec3 behind = mix(ridges(-view, air), atmosphere(vec3(-view.xy, 0.0)), 1.0 - smoothstep(-0.04, 0.0, -view.z));
                    color = mix(color, behind, ends);
                }
                if (uVeil.x > 0.0) {
                    color = mix(color, veilColor(-view), max(veiling(away), ends * skyVeiling(-view)));
                }
                return color;
            }
            """;

    /** The far land: see FarLand. */
    static final String FAR_FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + ENDS + GROUND_NOISE + FAR_LIGHT + """
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec4 vColor;
            varying float vUp;
            uniform sampler2D uPicture;
            // Tiles a side of the picture, which comes round again that often.
            uniform float uPictureSide;
            uniform float uSnowLying;
            uniform float uStorey;

            void main() {
                vec3 toEye = uEye - vWorld;
                if (notYetIn(-toEye.xy, vColor.a > 0.5)) {
                    discard;
                }
                vec3 n = normalize(vNormal);
                vec3 surface;
                float water = 0.0;
                if (vColor.a < 0.5) {
                    // The land: the game's own picture of its map, a pixel a tile. Seen from
                    // near enough that a tile is many pixels of this picture, each tile keeps
                    // its own colour to near its edge, where laid on as it is every street was
                    // a smear into the grass beside it.
                    vec2 at = vWorld.xy - 0.5;
                    vec2 part = fract(at);
                    part = part * part * (3.0 - 2.0 * part);
                    vec4 texel = texture2D(uPicture, (floor(at) + 0.5 + part) / uPictureSide);
                    surface = pow(texel.rgb, vec3(2.2));
                    water = texel.a;
                    // The picture has one colour for a field. Near to, the ground is not that even.
                    float fine = max(length(dFdx(vWorld.xy)), length(dFdy(vWorld.xy)));
                    float uneven = patches(vWorld.xy * 0.9) * 0.6 + patches(vWorld.xy * 3.7 + 11.0) * 0.4 - 0.5;
                    surface *= 1.0 + 0.3 * uneven * (1.0 - water) * (1.0 - smoothstep(0.15, 0.6, fine));
                    surface = mix(surface, pow(vec3(0.82, 0.845, 0.89), vec3(2.2)), min(uSnowLying * 2.0, 1.0) * (1.0 - water));
                } else if (n.z > 0.5) {
                    surface = pow(vColor.rgb, vec3(2.2)) * (0.9 + 0.2 * patches(vWorld.xy * 0.7));
                    surface = mix(surface, pow(vec3(0.82, 0.845, 0.89), vec3(2.2)), min(uSnowLying * 2.0, 1.0));
                } else {
                    // A wall, with a window to each bay of two tiles on every floor. Too far off to
                    // tell one window from the next, a wall is the colour all of it comes to.
                    float along = dot(vWorld.xy, vec2(-n.y, n.x)) * 0.5;
                    float up = vUp / uStorey;
                    float bay = fract(along), storey = fract(up);
                    float pane = step(0.22, bay) * step(bay, 0.78) * step(0.3, storey) * step(storey, 0.74) * step(0.0, vUp);
                    float fine = max(fwidth(along), fwidth(up));
                    pane = mix(pane, 0.56 * 0.44 * step(0.0, vUp), smoothstep(0.2, 0.55, fine));
                    surface = mix(pow(vColor.rgb, vec3(2.2)), vec3(0.03, 0.04, 0.05), 0.85 * pane);
                }
                gl_FragColor = vec4(farLit(vWorld, n, surface, water), 1.0);
            }
            """;

    /**
     * A tree beyond the loaded map: see FarTrees. One mesh of every tree, a crown turned round
     * the foot with a bole under it, is stood at each tree's place, turned and sized a little by
     * its own number, and made into its kind by the kind's row of uKinds (FarTreeLooks): the bole's
     * radius, the crown's lowest and highest leaves over the foot, and how far out the crown reaches
     * at each of its bands. A vertex of the mesh is how far up its part it is, its angle round the
     * foot, and its part: 0 the bole, 1 the crown, 2 the crown's foot, 3 its top.
     */
    static final String FAR_TREE_VERTEX = "#version 120\n" + Seasons.GLSL + AUTUMN + """
            attribute vec4 aTree;
            uniform sampler2D uKinds;
            uniform float uKindRows;
            uniform vec3 uEye;
            // The season and how far through it, and 1 where the trees follow the game's own schedule: see Seasons.gameLeafOn.
            uniform vec2 uSeason;
            uniform float uGameSchedule;
            // How much of its leaves the tree has on it: see Seasons.leafOn.
            varying float vLeaf;
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec3 vColor;
            // How much of the sky and sun reaches this part of the tree: a crown shades its own lower leaves.
            varying float vReached;
            // The place on the tree itself, in tiles from its foot, and the tree's own number: what its clumps of leaves are fixed to.
            varying vec4 vOnTree;
            // 1 on the crown, 0 on the bole.
            varying float vLeafy;

            vec4 kindTexel(float row, float texel) {
                return texture2D(uKinds, vec2((texel + 0.5) / %ROW%.0, (row + 0.5) / uKindRows));
            }

            float reachAt(float kind, float up) {
                float band = clamp(up * %BANDS%.0 - 0.5, 0.0, %BANDS%.0 - 1.0);
                float lower = floor(band);
                return mix(kindTexel(kind, 4.0 + lower).r, kindTexel(kind, 4.0 + min(lower + 1.0, %BANDS%.0 - 1.0)).r, band - lower);
            }

            void main() {
                float kind = floor(aTree.w);
                float own = fract(aTree.w);
                float turn = own * 6.2831853;
                float grown = 0.9 + 0.2 * fract(own * 7.31);
                vec4 sizes = kindTexel(kind, 2.0);
                float up = gl_Vertex.x;
                float angle = gl_Vertex.y + turn;
                float part = gl_Vertex.z;
                vec2 outward = vec2(cos(angle), sin(angle));
                vec3 local;
                // A kind that drops its leaves does it as a tree of the built world does, each tree on days of its own.
                vec4 seasonal = kindTexel(kind, 3.0);
                float mine = fract(own * 53.17);
                bool deciduous = seasonal.r > 0.5;
                bool games = uGameSchedule > 0.5;
                vLeaf = !deciduous ? 1.0 : games ? gameLeafOn(uSeason.x, uSeason.y, mine) : leafOn(uSeason.x, uSeason.y, mine);
                float turned = !deciduous ? 0.0 : games ? gameTurned(uSeason.x, uSeason.y, mine) : leafTurned(uSeason.x, uSeason.y, mine);
                if (part < 0.5) {
                    local = vec3(outward * sizes.x, up * sizes.y * 1.1);
                    vNormal = vec3(outward, 0.0);
                    vColor = kindTexel(kind, 1.0).rgb;
                    vReached = 0.55;
                    vLeafy = 0.0;
                } else {
                    float reach = part > 1.5 ? 0.0 : reachAt(kind, up);
                    // A crown stands out in lumps, each tree's its own. A vertex is told by its ring and
                    // side counted whole, so every triangle that shares it moves it alike, the last
                    // side's angle of 2 pi with the first's of 0 included.
                    float side = mod(floor(gl_Vertex.y * 10.1859164 + 0.5), 64.0);
                    float lump = fract(sin(dot(vec3(floor(up * 64.0 + 0.5), side, own * 977.0), vec3(12.9898, 78.233, 37.719))) * 43758.5453);
                    // Bare, a crown is its twigs, which do not reach as far out as its leaves did.
                    reach *= (0.82 + 0.36 * lump) * mix(0.75, 1.0, vLeaf);
                    local = vec3(outward * reach, mix(sizes.y, sizes.z, up));
                    // The crown faces the way its outline leans: up where it narrows to its top, down
                    // where it widens from its foot. With every side turned the same way up, the sky's
                    // light and the snow lay on the whole crown alike.
                    float slope = (reachAt(kind, min(up + 0.1, 1.0)) - reachAt(kind, max(up - 0.1, 0.0))) / (0.2 * max(sizes.z - sizes.y, 0.01));
                    vNormal = part > 2.5 ? vec3(0.0, 0.0, 1.0) : part > 1.5 ? vec3(0.0, 0.0, -1.0) : normalize(vec3(outward, -slope));
                    // No two trees of a kind are quite one colour.
                    vec3 leaves = autumnal(kindTexel(kind, 0.0).rgb, fract(own * 41.3) < 0.25 ? seasonal.b : seasonal.g, turned);
                    // Seen from as far off as these, a crown thinning is ever more the grey of its twigs.
                    vec3 twigs = mix(kindTexel(kind, 1.0).rgb, vec3(0.46, 0.43, 0.41), 0.45);
                    vColor = mix(twigs, leaves, smoothstep(0.0, 0.7, vLeaf)) * (0.82 + 0.3 * fract(own * 13.7)) * vec3(1.0, 0.94 + 0.12 * fract(own * 29.3), 1.0);
                    vReached = part > 1.5 && part < 2.5 ? 0.4 : mix(0.5, 1.0, up);
                    vLeafy = 1.0;
                }
                vOnTree = vec4(local * grown, own);
                vWorld = aTree.xyz + local * grown;
                gl_Position = gl_ProjectionMatrix * gl_ModelViewMatrix * vec4(vWorld - uEye, 1.0);
            }
            """.replace("%ROW%", Integer.toString(FarTreeLooks.ROW)).replace("%BANDS%", Integer.toString(Flora.CROWN_BANDS));

    /**
     * The far trees are lit as the far land is (FAR_LIGHT), come in where the built world thins
     * out as its buildings do (ENDS), and end at uTreeReach along the ground, pixel by pixel over
     * the last stretch before it.
     *
     * A crown is clumps of leaves fixed to its tree, with sky between its outer clumps and,
     * through a gap, the shaded inside of its far side, as a crown of the built world's has.
     * Where a pixel spans more than a clump the crown is drawn whole, in the shade its clumps
     * come to: gaps a pixel or less across would crawl as the head moves.
     */
    static final String FAR_TREE_FRAGMENT = "#version 120\n" + Sky.ATMOSPHERE + ENDS + GROUND_NOISE + FAR_LIGHT + """
            varying vec3 vWorld;
            varying vec3 vNormal;
            varying vec3 vColor;
            varying float vReached;
            varying vec4 vOnTree;
            varying float vLeafy;
            varying float vLeaf;
            uniform float uTreeReach;
            uniform float uSnowLying;

            void main() {
                vec3 toEye = uEye - vWorld;
                if (notYetIn(-toEye.xy, true)) {
                    discard;
                }
                float along = length(toEye.xy);
                float dither = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453);
                if (along > uTreeReach - 40.0 * dither) {
                    discard;
                }
                vec3 n = normalize(vNormal);
                vec3 surface = pow(vColor, vec3(2.2)) * vReached;
                if (vLeafy > 0.5) {
                    vec3 q = vOnTree.xyz + vOnTree.w * 97.0;
                    float fine = max(length(dFdx(vOnTree.xyz)), length(dFdy(vOnTree.xyz)));
                    float shown = 1.0 - smoothstep(0.3, 0.8, fine);
                    // Clumps of leaves at two sizes.
                    float clumps = patches(vec2(q.x + 0.7 * q.z, q.y - 0.6 * q.z) * 1.6) * 0.6 + patches(vec2(q.y + 0.5 * q.z, q.x + 0.8 * q.z) * 3.7) * 0.4;
                    float facing = dot(n, normalize(toEye));
                    // A crown that has lost leaves has lost clumps of them, the same clumps whichever way it is looked at.
                    if (clumps < (0.12 + 0.55 * smoothstep(0.35, 0.9, 1.0 - abs(facing)) + 0.4 * (1.0 - vLeaf)) * shown) {
                        discard;
                    }
                    // Facing away from the eye is the far side of the crown, seen from within it.
                    float inside = smoothstep(0.0, -0.25, facing);
                    n = mix(n, -n, inside);
                    float clump = smoothstep(0.3, 0.75, clumps);
                    surface *= mix(0.85, 0.35 + clump, shown) * mix(1.0, 0.35, inside);
                    // Snow lies on the tops of the clumps as on a crown of the built world's (snowOnMass), not over the whole crown.
                    float top = smoothstep(0.1, 0.8, n.z) * (1.0 - inside);
                    float lain = smoothstep(0.05, 0.45, uSnowLying * (0.3 + 1.1 * top) - 0.25 * (1.0 - clump) * shown);
                    surface = mix(surface, pow(vec3(0.82, 0.845, 0.89), vec3(2.2)), 0.85 * top * lain * mix(0.6, clump, shown));
                } else {
                    surface = mix(surface, pow(vec3(0.82, 0.845, 0.89), vec3(2.2)), min(uSnowLying * 2.0, 1.0) * max(n.z, 0.0));
                }
                gl_FragColor = vec4(farLit(vWorld, n, surface, 0.0), 1.0);
            }
            """;
}
