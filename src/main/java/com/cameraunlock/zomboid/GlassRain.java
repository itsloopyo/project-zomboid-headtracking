package com.cameraunlock.zomboid;

/**
 * Rain on the panes of the vehicle the viewer sits in, as the cabin's fragment shader draws
 * it over the glass: drops that stand where they landed, drops that gather and run down, and
 * the mist at the foot of a pane no wiper reaches.
 *
 * A drop is a lens and not a mark. It is nearly clear, it shows the world upside down, so its
 * lower edge holds the sky and its upper edge the dark of the ground, and no two are one
 * shape or size: most are specks and a few are heavy enough to hang. uGlassRain says how much
 * of that the graphics mode pays for.
 */
final class GlassRain {
    private GlassRain() {}

    /** Spliced into the cabin's shader after hash21, noiseAt, atmosphere and wipedAt, and before pane. */
    static final String GLSL = """
            // 0 in Low, 1 in Medium, 2 in High: see Quality.glassRain.
            uniform float uGlassRain;
            // How the vehicle's change of speed pulls at the water on its glass: see GlassPull.
            // x is how far a run leans towards the front of the vehicle for each tile it falls,
            // y the same towards the side the panes are counted to, z the clock the runs on
            // the front screen go by and w the one for the back screen. Both begin again
            // every hour, as uTime does.
            uniform vec4 uGlassPull;

            // How much of the rain is back on glass a blade passed that many seconds ago.
            float backAfter(float since) {
                return min(since * 1.545, 1.0);
            }

            // What a drop gives the glass at p, a place on it counted in the drop's own radius
            // from its middle with up as up, d how far out p is as the drop is shaped: x is
            // the light it adds and y the light it takes. The sky is in its lower edge and
            // the ground in its upper, and the open sky glints off its skin.
            vec2 lens(vec2 p, float d) {
                float edge = smoothstep(0.2, 1.0, d);
                vec2 glint = p - vec2(-0.3, 0.34);
                return vec2(edge * clamp(0.25 - p.y * 1.5, 0.0, 1.0) + 0.8 * exp(-dot(glint, glint) * 16.0) + 0.1,
                        edge * clamp(0.2 + p.y * 1.5, 0.0, 1.0));
            }

            // The drops of one size that stand on a pane. A drop has a square of a grid to
            // itself and is somewhere in it, and the grid is turned so that no row of drops
            // lies along the pane. cells is how many squares there are to a tile, few how
            // many of them hold a drop in the hardest rain, least and most the radius of a
            // drop in squares, hang how far the heaviest sag, and arrived how much of the
            // rain is back at this pixel since a blade last passed. Each drop dries in its
            // own time and another lands somewhere else in its square. swept is 1 on the
            // front screen, where that is four times sooner, and where every pass of a
            // blade over a square leaves it to other drops than the ones it took, or each
            // sweep would uncover the same ones. shift
            // sets one size's grid off from the next, and grain is the width of a pixel on
            // the pane, in tiles.
            vec2 dropsOn(vec2 on, float shift, float cells, vec2 turn, float few, float least, float most, float hang, float arrived, float swept, float grain) {
                mat2 turned = mat2(turn.x, turn.y, -turn.y, turn.x);
                vec2 grid = turned * (on + shift) * cells;
                vec2 cell = floor(grid);
                vec2 from = fract(grid) - 0.5;
                // A drop is wiped and comes back whole, by when a blade passed the middle of
                // its square: one half of it is never one drop and the other half the next.
                float passes = 0.0;
                if (swept > 0.5) {
                    vec2 wiped = wipedAt(on - from * turned / cells);
                    passes = mod(wiped.x, 89.0);
                    arrived = min(arrived, uRaining * backAfter(wiped.y));
                }
                float own = hash21(cell + 17.3);
                // uTime begins again every hour: a drop's turns in an hour are a whole number,
                // so it is the same drop at the same age on both sides of that.
                float turns = floor((108.0 + 180.0 * own) * (swept > 0.5 ? 4.0 : 1.0));
                float age = uTime * turns / 3600.0 + own * 31.0;
                float which = floor(age);
                age -= which;
                which = mod(which, turns);
                vec2 seed = cell + which * vec2(7.13, 3.71) + passes * vec2(2.39, 5.83);
                float pick = hash21(seed);
                float landed = smoothstep(pick, pick + 0.06, few * arrived) * (1.0 - smoothstep(0.8, 1.0, age));
                if (landed <= 0.0) {
                    return vec2(0.0);
                }
                // More drops are small than large: the square of an even chance.
                float big = hash21(seed + 1.7);
                big *= big;
                float full = mix(least, most, big);
                float radius = full * (0.7 + 0.3 * smoothstep(0.0, 0.04, age));
                // A drop's outline goes out to one and a half of its radius where it bulges
                // and hangs, and all of it stays in its square.
                vec2 middle = (vec2(hash21(seed + 3.1), hash21(seed + 7.7)) - 0.5) * max(1.0 - 3.2 * full, 0.0);
                vec2 p = (from - middle) * turned / radius;
                // A pixel's width in radii: a drop is never sharper than the picture, and one
                // smaller than a pixel is fainter and not a flicker.
                float fine = grain * cells / radius;
                // No drop is a circle. Each is wider one way than the other, bulges on three
                // sides of its own, and the heavy ones hang: narrow above and full below.
                vec2 odd = vec2(hash21(seed + 11.3), hash21(seed + 13.9)) - 0.5;
                float sag = hang * big;
                p.x *= (1.0 + 0.5 * odd.x) * (1.0 + sag * (p.y + 0.4));
                p.y *= 1.0 - 0.35 * sag;
                float out2 = dot(p, p);
                float far2 = sqrt(out2);
                float d = far2 * (1.0 + 0.3 * (odd.y * (p.x * p.x - 3.0 * p.y * p.y) * p.x + odd.x * (3.0 * p.x * p.x - p.y * p.y) * p.y) / (out2 * far2 + 0.02));
                float soft = clamp(fine * 1.5, 0.12, 0.9);
                float inside = (1.0 - smoothstep(1.0 - soft, 1.0, d)) * landed / max(fine * 0.8, 1.0) * smoothstep(0.0, 0.03, 0.5 - max(abs(from.x), abs(from.y)));
                return inside * lens(p, d);
            }

            // Drops that have gathered enough to run down a pane. Each keeps to a lane of
            // its own, wanders a little as it goes, sticks and lets go, and leaves a line of
            // wet behind it that breaks up into beads and dries. on is the place on the pane
            // as the runs lean, time the clock they go by, and wiped what wipedAt says of
            // the place: where a blade passes, other drops gather and run after it than the
            // ones it took, and none has left more wet behind it than it has had time for.
            vec2 runsOn(vec2 on, float time, float arrived, vec2 wiped, float grain) {
                const float LANES = 17.0, HEAD = 0.004, TAIL = 0.24;
                float lane = floor(on.x * LANES);
                float own = hash21(vec2(lane, 5.0 + mod(wiped.x, 89.0) * 3.3));
                float trips = floor(252.0 + 360.0 * own);
                float clock = time * trips / 3600.0 + own * 17.0;
                float trip = floor(clock);
                float gone = clock - trip;
                trip = mod(trip, trips);
                float pick = hash21(vec2(lane, trip + 0.5));
                // Between two passes of a blade a run gets a hand's breadth and no further, so
                // more of them set off there, and from anywhere under the blades.
                bool wipes = wiped.y < 999.0;
                float most = wipes ? 0.95 : 0.75;
                // A run thins away as the rain eases, and is not there one frame and gone the next.
                float runs = smoothstep(pick, pick + 0.05, most * arrived);
                if (runs <= 0.0) {
                    return vec2(0.0);
                }
                float heavy = 0.7 + 0.6 * hash21(vec2(trip, lane));
                float middle = (lane + 0.5 + 0.5 * (pick / most - 0.5)) / LANES + 0.005 * sin(on.y * 21.0 + own * 40.0) + 0.002 * sin(on.y * 67.0 + own * 9.0);
                float fallen = gone + 0.014 * sin(gone * 44.0 + own * 6.0);
                float head = wipes ? 0.2 - fract(3.0 * fallen) * 0.5 : 0.65 - fallen * 1.5;
                vec2 p = vec2(on.x - middle, on.y - head) / (HEAD * heavy);
                float above = on.y - head;
                float fine = grain / (HEAD * heavy);
                // The head is a hanging drop.
                vec2 q = vec2(p.x * (1.0 + 0.3 * clamp(p.y + 0.4, 0.0, 3.0)), p.y * (p.y > 0.0 ? 0.6 : 1.0));
                float d = length(q);
                float soft = clamp(fine * 1.5, 0.12, 0.9);
                vec2 found = (1.0 - smoothstep(1.0 - soft, 1.0, d)) * lens(q, d);
                // The wet it leaves, narrower than the head and thinner the longer ago.
                float beads = mix(1.0, smoothstep(0.3, 0.62, noiseAt(vec2(lane * 7.0 + trip, on.y * 85.0))), smoothstep(0.02, 0.12, above));
                float wide = 0.55 - 0.25 * smoothstep(0.0, TAIL, above);
                float tail = step(0.0, above) * (1.0 - smoothstep(0.3 * TAIL, TAIL, above)) * (1.0 - smoothstep(wide - soft, wide + soft * 0.5, abs(p.x))) * beads;
                // How far the head has come since the blade passed.
                float laid = wiped.y * 1.5 * trips / 3600.0;
                tail *= 1.0 - smoothstep(0.7 * laid, laid + 0.0001, above);
                found = max(found, tail * vec2(0.5 + 0.35 * clamp(-p.x * 2.0, 0.0, 1.0), 0.45 * clamp(p.x * 2.0, 0.0, 1.0)));
                return found * runs / max(fine * 0.8, 1.0);
            }

            // A pane with the rain on it. on is the place on the pane in tiles, swept 1 on
            // the front screen, and wiper what sweepAt says of the place. On the front
            // screen the wipers go to and fro and the drops come back behind the blade.
            vec4 rained(vec4 glass, vec2 on, float swept, vec4 wiper) {
                float still = 1.0 - (swept > 0.5 ? wiper.x : 0.0);
                float arrived = uRaining * (swept > 0.5 ? min(wiper.z * 1.7, 1.0) : 1.0);
                float grain = length(fwidth(on));
                // What runs down a pane leans the way the vehicle's change of speed pulls it.
                // The side windows' places are counted along the vehicle, from 7, and the
                // screens' across it. On a screen, which leans, the same pull hurries a run
                // or holds it back.
                bool beside = on.x > 3.5;
                vec2 leant = vec2(on.x - (beside ? uGlassPull.x : uGlassPull.y) * (0.65 - on.y), on.y);
                vec2 wet;
                if (uGlassRain > 1.5) {
                    wet = 0.8 * dropsOn(on, 0.0, 118.0, vec2(0.8776, 0.4794), 0.6, 0.1, 0.24, 0.0, arrived, swept, grain)
                            + dropsOn(on, 3.7, 52.0, vec2(0.4536, 0.8912), 0.7, 0.1, 0.3, 0.35, arrived, swept, grain)
                            + dropsOn(on, 8.3, 25.0, vec2(-0.4161, 0.9093), 0.5, 0.1, 0.28, 0.45, arrived, swept, grain);
                    // Where the wipers clear the glass a run has only the time between two
                    // passes of a blade.
                    vec2 wiped = swept > 0.5 ? wipedAt(on) : vec2(0.0, 1000.0);
                    float time = beside ? uTime : swept > 0.5 ? uGlassPull.z : uGlassPull.w;
                    wet = max(wet, runsOn(leant, time, arrived, wiped, grain) * (1.0 - 0.7 * swept * still));
                } else if (uGlassRain > 0.5) {
                    wet = 0.9 * dropsOn(on, 0.0, 88.0, vec2(0.8776, 0.4794), 0.6, 0.1, 0.26, 0.0, arrived, swept, grain)
                            + dropsOn(on, 3.7, 33.0, vec2(0.4536, 0.8912), 0.7, 0.1, 0.3, 0.45, arrived, swept, grain);
                } else {
                    wet = dropsOn(on, 0.0, 40.0, vec2(0.4536, 0.8912), 0.75, 0.12, 0.3, 0.4, arrived, swept, grain);
                }
                // A drop holds the sky and, at night, the glare of the lamps.
                vec3 sky = atmosphere(vec3(0.0, 0.0, 1.0));
                vec3 held = sky * 1.3 + vec3(1.0, 0.92, 0.76) * (0.12 * swept * step(0.5, uBeamAt[0].w));
                float lit = wet.x * 1.1, dark = wet.y * 1.0;
                glass.rgb = mix(glass.rgb, held * 1.5, lit / (lit + glass.a));
                glass.a = min(glass.a + lit, 0.9);
                glass.rgb = mix(glass.rgb, held * 0.03, dark / (dark + glass.a));
                glass.a = min(glass.a + dark, 0.9);
                if (uGlassRain > 0.5 && uGlassRain < 1.5) {
                    // On the panes no wiper reaches the rain gathers and runs down in trickles.
                    float trickle = smoothstep(0.88, 0.97, noiseAt(vec2(leant.x * 190.0, on.y * 3.5 + uTime * 0.35))) * smoothstep(0.35, 0.6, noiseAt(vec2(leant.x * 9.0 + 4.0, on.y * 1.5 - uTime * 0.2)));
                    float running = trickle * uRaining * still * (1.0 - 0.7 * swept) * 0.3;
                    glass.rgb = mix(glass.rgb, held, running / (running + glass.a));
                    glass.a = min(glass.a + running * 0.5, 0.9);
                }
                // The glass mists over from its foot where the warm air inside meets it.
                float mist = uRaining * still * (1.0 - smoothstep(-0.32, 0.02 + 0.12 * noiseAt(on * 3.0), on.y)) * (0.5 + 0.5 * noiseAt(on * 11.0));
                glass.rgb = mix(glass.rgb, sky * 0.9 + 0.01, mist * 0.3 / (mist * 0.3 + glass.a));
                glass.a = min(glass.a + mist * 0.3, 0.92);
                return mix(glass, vec4(0.004, 0.004, 0.005, 1.0), wiper.w);
            }
            """;
}
