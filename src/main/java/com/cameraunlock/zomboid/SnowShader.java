package com.cameraunlock.zomboid;

/**
 * Snow that has settled, as the world's fragment shader draws it on the ground, on a roof and
 * on the top of anything else under the open sky.
 *
 * Lying snow is a surface of its own and not a coat of white paint on the one under it. It
 * has a thickness, so it ends in a rounded lip where the ground shows through. It lies in
 * long swells, in lumps over whatever it buried, in ripples the wind combed into it and
 * under a crust of grains, and each of those tilts it to the sun or away. Its hollows are
 * dimmer and bluer than its tops, for they see less of the sky and what they see is not the
 * sun. And it is made of crystals, a few of which are always turned just so to throw the sun
 * at the eye, other ones with every step.
 *
 * All of it is worked out from the place on the map, so it stays put, costs no picture on the
 * card and is as fine close to as from across a field.
 */
final class SnowShader {
    private SnowShader() {}

    /** Joined on after WORLD_FRAGMENT_RELIEF and before the world's main: it uses facHash and uSnowLying. */
    static final String GLSL = """
            // How high snow stands at its lip over what it lies on, and how far its swells,
            // its lumps, its wind ripples and the grains of its crust stand out of it, in tiles.
            const float SNOW_RIM = 0.035, SNOW_SWELL = 0.05, SNOW_LUMP = 0.02, SNOW_RIPPLE = 0.005, SNOW_CRUST = 0.0011;
            // Snow's slopes are gentle and the eye is used to seeing them under a low sun: they are lit this many times steeper than they are.
            const float SNOW_STEEP = 4.5;
            // How deep under its tops a lump's hollow is drawn, in tiles: see snowSunk.
            const float SNOW_DEEP = 0.05;
            // Snow on its tops and down in its hollows. Whiter than this, a field of it in the
            // sun is past what the picture can show and its shape is lost in the glare.
            const vec3 SNOW_WHITE = vec3(0.82, 0.845, 0.89), SNOW_HOLLOW = vec3(0.6, 0.69, 0.86);
            // How bright a crystal that throws the sun at the eye is, against the sun on white.
            const float SNOW_GLINT = 14.0;
            // What snowLaid found besides how much snow lies: the height of its surface in
            // tiles with the slope it is lit by, east and south, how far down in a hollow the
            // place is, 0 on a top and 1 at the bottom, how much of the ground just clear of
            // the snow's lip is wet from it, and how far a boot or a tyre has pressed the snow down, 0 to 1.
            vec3 snowForm = vec3(0.0);
            float snowHollow = 0.0, snowMelt = 0.0, snowTrodden = 0.0;
            // Set by the caller: how much of the sky is hidden from the place by what stands over it, 0 to 1.
            float snowShelter = 0.0;

            // Where the viewer has walked and driven since the snow came: see SnowPrints. A
            // footprint is its place, counted as vPlace is, with the way the foot pointed
            // and how high the ground it trod stands. A rut is a stretch of a vehicle's
            // path from one place to the next, down the middle between its wheels.
            // uSnowRutUp is how high the ground under each stretch stands, uSnowTrodBox
            // holds every mark, its least corner and its greatest, and uSnowTrod is how many
            // footprints there are and how many stretches of rut.
            uniform vec4 uSnowPrint[48];
            uniform vec4 uSnowRut[32];
            uniform float uSnowRutUp[32];
            uniform vec4 uSnowTrodBox;
            uniform vec2 uSnowTrod;
            // How deep a boot and a tyre sink, in tiles, and how far to either side of a vehicle's middle its wheels run.
            const float SNOW_TROD = 0.04, SNOW_HALF_TRACK = 0.5;
            // A mark is on the ground it was made on and not on the roof over it: within this of its height, half a storey, which a hillside stays inside from one end of a rut to the other.
            const float SNOW_SAME_FLOOR = 1.2;

            // How much of an unevenness of that many cells to a tile can be drawn where a pixel
            // covers foot tiles: finer than two pixels to a cell it would only crawl.
            float snowDrawn(float foot, float size) {
                return 1.0 - smoothstep(0.2, 0.5, foot * size);
            }

            // How far snow is pressed down at a place by a mark whose edge is at 1 of far:
            // -1 on its floor, a little over 0 on the lip thrown up round it, with how fast that changes as far does.
            vec2 snowDent(float far) {
                float t = clamp((far - 0.7) / 0.4, 0.0, 1.0);
                float lip = 0.25 * exp(-(far - 1.3) * (far - 1.3) / 0.05);
                return vec2(t * t * (3.0 - 2.0 * t) - 1.0 + lip, 6.0 * t * (1.0 - t) / 0.4 - lip * (far - 1.3) / 0.025);
            }

            // What walking and driving have done to the snow at a place on ground that
            // stands so high: how far it is pressed down, 0 untouched, -1 the floor of a
            // mark and a little over 0 on the lip round it, with how fast that changes east
            // and south, for a mark as deep as one tile. Of two marks the nearer counts. foot is
            // how much ground a pixel covers: a footprint is drawn from nearer than a rut, which is broader and longer.
            vec3 snowPressed(vec2 q, float floorUp, float foot) {
                vec3 pressed = vec3(0.0);
                if (q.x < uSnowTrodBox.x || q.y < uSnowTrodBox.y || q.x > uSnowTrodBox.z || q.y > uSnowTrodBox.w) {
                    return pressed;
                }
                float nearest = 2.0;
                float printed = snowDrawn(foot, 12.0), rutted = snowDrawn(foot, 4.0);
                for (int i = 0; i < 48; i++) {
                    if (float(i) >= uSnowTrod.x || printed <= 0.0) {
                        break;
                    }
                    vec4 print = uSnowPrint[i];
                    vec2 from = q - print.xy;
                    if (dot(from, from) > 0.06 || abs(floorUp - print.w) > SNOW_SAME_FLOOR) {
                        continue;
                    }
                    // A boot: the sole and the heel behind it, run together at the waist.
                    vec2 way = vec2(cos(print.z), sin(print.z));
                    vec2 on = vec2(dot(from, way), dot(from, vec2(-way.y, way.x)));
                    vec2 sole = (on - vec2(0.04, 0.0)) / vec2(0.09, 0.048), heel = (on + vec2(0.075, 0.0)) / vec2(0.05, 0.042);
                    float soleFar = max(length(sole), 1e-4), heelFar = max(length(heel), 1e-4);
                    float soles = exp(-6.0 * soleFar), heels = exp(-6.0 * heelFar);
                    float far = -log(soles + heels) / 6.0;
                    if (far < nearest) {
                        nearest = far;
                        vec2 dent = snowDent(far);
                        vec2 turn = (sole / soleFar / vec2(0.09, 0.048) * soles + heel / heelFar / vec2(0.05, 0.042) * heels) * (dent.y / (soles + heels));
                        pressed = vec3(dent.x, way * turn.x + vec2(-way.y, way.x) * turn.y) * printed;
                    }
                }
                for (int i = 0; i < 32; i++) {
                    if (float(i) >= uSnowTrod.y) {
                        break;
                    }
                    if (abs(floorUp - uSnowRutUp[i]) > SNOW_SAME_FLOOR) {
                        continue;
                    }
                    vec4 rut = uSnowRut[i];
                    vec2 along = rut.zw - rut.xy;
                    float run = dot(along, along);
                    vec2 from = q - rut.xy;
                    float share = dot(from, along) / run;
                    // A stretch runs a little past each of its ends, or a bend would open a gap between two of them on its outside.
                    if (share < -0.08 || share > 1.08) {
                        continue;
                    }
                    vec2 across = vec2(-along.y, along.x) * inversesqrt(run);
                    float off = dot(from, across);
                    // A tyre is two hands wide, and its tread stands in bars across the rut it leaves.
                    float far = abs(abs(off) - SNOW_HALF_TRACK) / 0.1;
                    if (far < nearest) {
                        nearest = far;
                        vec2 dent = snowDent(far);
                        // Counted along the stretch itself: counted on the map, the bars would jump whenever vPlace's corner moved.
                        float bars = 0.82 + 0.18 * smoothstep(-0.3, 0.3, sin(dot(from, along) * inversesqrt(run) * 70.0 + abs(off) * 40.0));
                        pressed = vec3(min(dent.x, 0.0) * bars + max(dent.x, 0.0), across * (dent.y / 0.1 * sign(off) * sign(abs(off) - SNOW_HALF_TRACK))) * rutted;
                    }
                }
                return pressed;
            }

            // Smooth noise at a place and how fast it changes each way. The corner vPlace is
            // counted from moves sixty tiles at a time, so the noise is made to come round
            // again every wrap of its own cells, which the caller makes a whole share of sixty tiles.
            vec3 snowNoise(vec2 p, vec2 wrap, float seed) {
                vec2 cell = floor(p), f = p - cell;
                vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
                vec2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
                float a = facHash(mod(cell, wrap) + seed), b = facHash(mod(cell + vec2(1.0, 0.0), wrap) + seed);
                float c = facHash(mod(cell + vec2(0.0, 1.0), wrap) + seed), d = facHash(mod(cell + vec2(1.0, 1.0), wrap) + seed);
                float k = a - b - c + d;
                return vec3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y, du * vec2(b - a + k * u.y, c - a + k * u.x));
            }

            // One size of unevenness, so many of its cells to a tile: sixty times that has to be a whole number.
            vec3 snowLayer(vec2 q, float size, float seed) {
                vec3 s = snowNoise(q * size, vec2(60.0 * size), seed);
                return vec3(s.x, s.yz * size);
            }

            // The same laid askew of the map's own lines, which the noise's cells would
            // otherwise square every patch and lump off along. turn is the cosine and sine
            // of the angle, 0.8 and 0.6 or 0.6 and 0.8: turned by either, sixty tiles
            // along the map are a whole number of twelfths of a tile along the noise, so
            // twelve times size has to be a whole number.
            vec3 snowAskew(vec2 q, float size, float seed, vec2 turn) {
                vec3 s = snowNoise(vec2(q.x * turn.x - q.y * turn.y, q.x * turn.y + q.y * turn.x) * size, vec2(12.0 * size), seed);
                return vec3(s.x, (s.y * turn.x + s.z * turn.y) * size, (s.z * turn.x - s.y * turn.y) * size);
            }

            // How thick the snow is at a place, in the measure its edge is found by, under 0.3
            // none and over 0.6 its full depth, with how fast that changes each way. It
            // comes in patches that spread and join as more of it lies, and it is thinner
            // where snowShelter says less of it fell.
            vec3 snowThick(vec2 q, float foot) {
                // What is too fine to draw at this distance counts as its own mean, or far snow would lie thicker than near.
                vec3 mean = vec3(0.5, 0.0, 0.0);
                vec3 broad = snowAskew(q, 0.75, 1.0, vec2(0.8, 0.6)) * 0.6 + mix(mean, snowLayer(q, 1.5, 12.0), snowDrawn(foot, 1.5)) * 0.24
                        + mix(mean, snowAskew(q, 3.0, 2.0, vec2(0.6, 0.8)), snowDrawn(foot, 3.0)) * 0.16;
                return vec3((uSnowLying * 1.4 - 0.2 - broad.x) / 0.4 + 0.5 - 1.3 * snowShelter, broad.yz / -0.4);
            }

            // The edge of a patch is ragged, for the ground it thins out over is not flat:
            // what is added to the thickness there, with how fast it changes each way.
            vec3 snowRagged(vec2 q, float foot) {
                return (snowAskew(q, 9.0, 3.0, vec2(0.6, 0.8)) - vec3(0.5, 0.0, 0.0)) * (0.2 * snowDrawn(foot, 9.0))
                        + (snowAskew(q, 31.0, 4.0, vec2(0.8, 0.6)) - vec3(0.5, 0.0, 0.0)) * (0.07 * snowDrawn(foot, 31.0));
            }

            // How much snow covers a place, 0 to 1, for what stands in it and does not need its shape: a blade of grass, a crown.
            float snowCover(vec2 q, float foot) {
                return smoothstep(0.3, 0.44, snowThick(q, foot).x + snowRagged(q, foot).x);
            }

            // How far the lumps stand up at a place, 0 to 1: what snowSunk looks into.
            float snowLumps(vec2 q) {
                return snowAskew(q, 2.5, 7.0, vec2(0.8, 0.6)).x * 0.7 + snowAskew(q, 6.25, 8.0, vec2(0.6, 0.8)).x * 0.3;
            }

            // Lumped snow has depth: a lump hides the foot of the one beyond it, and slides
            // against it as the head moves. This gives how far along the ground the place
            // seen lies from the pixel's own, for a look along view, which points at the eye.
            // lumpy is how much the lumps are raised over what the snow buried, and near how
            // much of the depth is drawn at this distance.
            vec2 snowSunk(vec2 q, vec3 view, float lumpy, float near) {
                vec2 run = view.xy / max(view.z, 0.4) * (-SNOW_DEEP * lumpy * near);
                vec2 moved = run * (1.0 - snowLumps(q));
                return run * (1.0 - snowLumps(q + moved));
            }

            // How much snow lies at a place, 0 to 1, with its shape left in snowForm,
            // snowHollow and snowMelt. lumpy is 1 over what was flat and more over grass and
            // rough ground, which snow lies on in lumps. foot is how much ground a pixel covers
            // there, and floorUp how high the ground stands, which tells whose footprints are on it.
            // seen is the place snowSunk found: the lumps are looked at there, and the snow's
            // edge and the marks in it where they are, or the lumps' depth would tear them.
            float snowLaid(vec2 q, vec2 seen, float lumpy, float foot, float floorUp) {
                vec3 thick = snowThick(q, foot);
                vec3 ragged = snowRagged(q, foot);
                // Its depth rounds off to nothing at the lip, less ragged than the lip's outline is.
                vec3 deep = thick + ragged * 0.4;
                float t = clamp((deep.x - 0.3) / 0.3, 0.0, 1.0);
                float body = t * t * (3.0 - 2.0 * t);
                vec2 bodySlope = deep.yz * (6.0 * t * (1.0 - t) / 0.3);
                vec3 swell = snowAskew(q, 0.25, 5.0, vec2(0.6, 0.8)) + snowLayer(q, 0.85, 6.0) * 0.5;
                vec2 at = q;
                q = seen;
                // What is too fine to draw at this distance counts as its own mean: a far field is not all hollow.
                vec3 mean = vec3(0.5, 0.0, 0.0);
                vec3 lump = mix(mean, snowAskew(q, 2.5, 7.0, vec2(0.8, 0.6)), snowDrawn(foot, 2.5)) * 0.7
                        + mix(mean, snowAskew(q, 6.25, 8.0, vec2(0.6, 0.8)), snowDrawn(foot, 6.25)) * 0.3;
                // The wind combs snow into ripples that lie across it, sharp along their
                // crests, in the open stretches between the swells and not everywhere.
                vec2 r = vec2(q.x * 0.8 - q.y * 0.6, q.x * 0.6 + q.y * 0.8);
                vec3 comb = snowNoise(r * vec2(13.0, 2.5), vec2(156.0, 30.0), 9.0);
                float side = comb.x > 0.5 ? -2.0 : 2.0;
                vec2 combSlope = comb.yz * vec2(13.0, 2.5) * side;
                float combed = smoothstep(0.45, 0.8, swell.x / 1.5) * snowDrawn(foot, 13.0);
                vec3 ripple = vec3(1.0 - abs(2.0 * comb.x - 1.0), combSlope.x * 0.8 + combSlope.y * 0.6, combSlope.y * 0.8 - combSlope.x * 0.6) * combed;
                vec3 crust = (mix(mean, snowLayer(q, 37.0, 10.0), snowDrawn(foot, 37.0)) + mix(mean, snowLayer(q, 97.0, 11.0), snowDrawn(foot, 97.0)) * 0.5) / 1.5;
                vec3 stands = swell * SNOW_SWELL + lump * (SNOW_LUMP * lumpy) + ripple * SNOW_RIPPLE + crust * (1.5 * SNOW_CRUST);
                // A boot or a tyre presses it down, as far as there is snow to press. Too far off to draw, the marks are not looked for.
                vec3 pressed = body * snowDrawn(foot, 4.0) > 0.0 ? snowPressed(at, floorUp, foot) * body : vec3(0.0);
                // The lip and the wall of a footprint are as steep as they are; everything gentler is lit steeper than it is.
                vec2 slope = bodySlope * (SNOW_RIM + stands.x) + stands.yz * (body * SNOW_STEEP) + pressed.yz * SNOW_TROD;
                snowForm = vec3((SNOW_RIM + stands.x) * body + pressed.x * SNOW_TROD, slope * min(1.0, 1.1 / max(length(slope), 1e-4)));
                // How far down, with a tyre's bars taken out of it: they are in the shape, and would dot the colour.
                snowTrodden = smoothstep(0.0, 0.7, -pressed.x);
                snowHollow = max(1.0 - clamp((lump.x * lumpy + ripple.x * 0.35 + crust.x * 0.2) / (lumpy + 0.35 * combed + 0.2), 0.0, 1.0), 0.6 * snowTrodden);
                // Thin snow is trodden through to the ground.
                float lies = smoothstep(0.3, 0.44, thick.x + ragged.x - 0.12 * snowTrodden);
                snowMelt = smoothstep(0.02, 0.3, thick.x + ragged.x) * (1.0 - lies);
                return lies;
            }

            // The colour of snow at a place found by snowLaid: white, a shade dimmer and bluer
            // down in its hollows, and greyer where it is packed hard on the floor of a footprint, whose walls stand in its light.
            vec3 snowWhite() {
                return mix(SNOW_WHITE, SNOW_HOLLOW, 0.75 * snowHollow * snowHollow) * (1.0 - 0.28 * snowTrodden);
            }

            // How much of the sun the crystals at a place throw at the eye. Each is a flat
            // face turned its own way out of the snow's surface, and shines only when that
            // way is the one halfway between the sun and the eye: one in a few dozen at a
            // time, and another one after a step. A crystal is drawn a pixel or two across
            // however far off it is, for what glitters across a field is not one grain but
            // the few among thousands that are turned right. foot is how much ground a pixel covers.
            float snowGlints(vec2 q, vec3 n, vec3 view, vec3 light, float foot) {
                vec3 h = normalize(view + light);
                float level = clamp(log2(max(foot * 256.0 * 1.6, 1.0)), 0.0, 9.0);
                float coarse = floor(level), part = level - coarse;
                float sum = 0.0;
                for (int i = 0; i < 2; i++) {
                    float size = 256.0 / exp2(coarse + float(i));
                    vec2 at = q * size;
                    vec2 cell = mod(floor(at), 60.0 * size);
                    float turn = 6.2831853 * facHash(cell + 17.0);
                    float tilt = 1.1 * sqrt(facHash(cell + 71.0));
                    vec3 facet = normalize(n + vec3(cos(turn), sin(turn), 0.0) * tilt);
                    float lit = smoothstep(0.992, 0.9995, dot(facet, h));
                    vec2 within = fract(at) - 0.5;
                    lit *= 1.0 - smoothstep(0.15, 0.45, length(within));
                    sum += lit * (i == 0 ? 1.0 - part : part);
                }
                // Past where a crystal's cell can be made a pixel wide there is nothing to draw one in.
                return sum * (1.0 - smoothstep(8.0, 9.0, level));
            }

            """;
}
