package com.cameraunlock.zomboid;

import java.util.Locale;

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

    /**
     * What every shader that draws snow shares, on the ground or on a person or a vehicle: its
     * colour and the noise its unevenness is made of.
     */
    static final String BASICS = """
            // Snow on its tops and down in its hollows. Whiter than this, a field of it in the
            // sun is past what the picture can show and its shape is lost in the glare.
            const vec3 SNOW_WHITE = vec3(0.82, 0.845, 0.89), SNOW_HOLLOW = vec3(0.6, 0.69, 0.86);

            // A number from 0 to 1 for each cell of the snow's unevenness, with no pattern from one cell to the next.
            // It is the hash12 of Hash without Sine, as the world shader's facHash is (THIRD-PARTY-NOTICES.md), which a vertex shader does not have.
            float snowHash(vec2 p) {
                vec3 q = fract(vec3(p.xyx) * 0.1031);
                q += dot(q, q.yzx + 33.33);
                return fract((q.x + q.y) * q.z);
            }

            // Smooth noise at a place and how fast it changes each way. The corner vPlace is
            // counted from moves sixty tiles at a time, so the noise is made to come round
            // again every wrap of its own cells, which the caller makes a whole share of sixty tiles.
            // The noise and its slopes are worked as Inigo Quilez sets them out (THIRD-PARTY-NOTICES.md, Acknowledgements).
            vec3 snowNoise(vec2 p, vec2 wrap, float seed) {
                vec2 cell = floor(p), f = p - cell;
                vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
                vec2 du = 30.0 * f * f * (f * (f - 2.0) + 1.0);
                float a = snowHash(mod(cell, wrap) + seed), b = snowHash(mod(cell + vec2(1.0, 0.0), wrap) + seed);
                float c = snowHash(mod(cell + vec2(0.0, 1.0), wrap) + seed), d = snowHash(mod(cell + vec2(1.0, 1.0), wrap) + seed);
                float k = a - b - c + d;
                return vec3(a + (b - a) * u.x + (c - a) * u.y + k * u.x * u.y, du * vec2(b - a + k * u.y, c - a + k * u.x));
            }

            """;

    /**
     * Where snow lies and how deep, for the world's fragment shader and for the vertex shaders
     * that stand grass in it. It uses uSnowLying, which the shader it is joined into declares.
     */
    static final String COVER = BASICS + """
            // How deep snow lies where it covers the ground, in tiles, for each 1 of uSnowLying: what grows there stands in it that far.
            const float SNOW_DEPTH = 0.45;
            // How much thinner the snow is for each 1 of snowShelter.
            const float SNOW_SHELTERED = 1.3;
            // Set by the caller: how much of the sky is hidden from the place by what stands over it, 0 to 1.
            float snowShelter = 0.0;

            // How much of an unevenness of that many cells to a tile can be drawn where a pixel
            // covers foot tiles: finer than two pixels to a cell it would only crawl.
            float snowDrawn(float foot, float size) {
                return 1.0 - smoothstep(0.2, 0.5, foot * size);
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
                return vec3((uSnowLying * 1.4 - 0.2 - broad.x) / 0.4 + 0.5 - SNOW_SHELTERED * snowShelter, broad.yz / -0.4);
            }

            // The edge of a patch is ragged, for the ground it thins out over is not flat:
            // what is added to the thickness there, with how fast it changes each way.
            vec3 snowRagged(vec2 q, float foot) {
                return (snowAskew(q, 9.0, 3.0, vec2(0.6, 0.8)) - vec3(0.5, 0.0, 0.0)) * (0.2 * snowDrawn(foot, 9.0))
                        + (snowAskew(q, 31.0, 4.0, vec2(0.8, 0.6)) - vec3(0.5, 0.0, 0.0)) * (0.07 * snowDrawn(foot, 31.0));
            }

            // How much snow covers a place where it is that thick, ragged edge and all, 0 to 1.
            float snowCovers(float thick) {
                return smoothstep(0.3, 0.44, thick);
            }

            // How much snow covers a place, 0 to 1, for what stands in it and does not need its shape: a blade of grass, a crown.
            float snowCover(vec2 q, float foot) {
                return snowCovers(snowThick(q, foot).x + snowRagged(q, foot).x);
            }

            """;

    /**
     * How deep a strip of grass stands in lying snow, for the world's vertex shader. Grass
     * throws no shadow: it is in none of the sun's or the lamps' maps. Joined on after COVER
     * and PipelineShaders.SKY_SEEN: it uses uEye, uRangeFrom and uPixel, which the shader it
     * is joined into declares.
     */
    static final String STOOD = """
            // How thick the snow is at a place, in snowCovers' measure: 0 with no snow lying,
            // and 0 where the world's fragment shader draws none on the ground there, which it
            // decides by the same things. open is whether the place's square is under the sky.
            // Less snow lies where the sky is hidden, as the fragment shader has it: see
            // skyOverAt. What of the snow's unevenness is too fine to draw at the place's
            // distance counts as its mean, by the ground a pixel of the view covers there:
            // uPixel of its distance across, and more along the view as the view lies flatter
            // to the ground; so does what is finer than finest, in tiles. The sky is only asked
            // about where snow would lie in the open.
            float snowThickAt(vec3 root, bool open, float finest) {
                if (uSnowLying <= 0.0 || !open) {
                    return 0.0;
                }
                vec3 toEye = uEye - root;
                float away = length(toEye);
                float foot = max(uPixel * away * away / max(abs(toEye.z), 0.1), finest);
                vec2 q = mod(root.xy, 60.0);
                snowShelter = 0.0;
                float thick = snowThick(q, foot).x + snowRagged(q, foot).x;
                if (thick <= 0.3) {
                    return 0.0;
                }
                float skyOver = skyOverAt(root + vec3(0.0, 0.0, 0.1), max(away - uRangeFrom, 0.0));
                return thick - SNOW_SHELTERED * (1.0 - skyOver) / 0.62;
            }

            // How deep a plant rooted at a place stands in the snow, in tiles: as deep as the
            // snow drawn on the ground at its root.
            float snowStood(vec3 root, bool open) {
                return SNOW_DEPTH * uSnowLying * snowCovers(snowThickAt(root, open, 0.0));
            }

            // How far a bank of snow runs out from the foot of a kerb's face, in tiles, before
            // its unevenness along the kerb. The foot's vertices are where the kerbs' field
            // crosses the sides of its eighth-of-a-tile cells (Kerbs.FINE), and the ragged edge
            // of a patch is finer than that: a bank asked of it would stand out across the street
            // at one vertex and not at the next, and the face between them would be a sharp dark
            // shard. The shadow program lets a kerb down by the snow at the same fineness.
            float snowBanked(vec3 root, bool open) {
                return SNOW_DEPTH * uSnowLying * snowCovers(snowThickAt(root, open, 0.125));
            }

            // A plant that stands that deep in snow, at one of its vertices: the whole of it is
            // let down by the snow's depth, so that what is under the snow is under the ground
            // and out of sight, and what stands out of the snow stands on it.
            // The snow is drawn on the ground and has no height of its own.
            vec3 snowedIn(vec3 at, float deep) {
                at.z -= deep;
                return at;
            }

            // The snow on what of a blade stands out of it weighs it over and down, each
            // blade its own way, the more the taller it stands and the deeper the snow it
            // stands in: none where no snow lies at its root. It is done after the wind,
            // which still moves the blade under its load.
            vec3 snowLaden(vec3 at, float high, float deep, float seed) {
                float turn = seed * 6.2831853;
                float bent = 0.45 * high * deep / SNOW_DEPTH;
                return vec3(at.xy + vec2(cos(turn), sin(turn)) * bent, at.z - bent);
            }

            """;

    /**
     * How high snow stands along the top of wood for each 1 of uSnowLying, in tiles: as high as
     * RIDGE_OF_THICK of the wood's radius there, and never higher than RIDGE_HIGH; how far out to
     * each side it reaches for each tile it stands; and how thick wood is, as a radius, that a
     * real fall frosts white nearly all round: all of it up to FINE_FROM, none from FINE_TO.
     */
    static final float RIDGE_HIGH = 0.06f, RIDGE_OF_THICK = 2.5f, RIDGE_WIDE = 0.5f, FINE_FROM = 0.005f, FINE_TO = 0.009f;
    /** How far under 0 wood that holds no snow of its own carries its thickness: see Flora.woodCode. */
    static final float UNSNOWED = 1f;

    /**
     * The ridge of snow along the top of wood: snow stands on a limb higher than the half-round
     * of its bark and a little wider, so from under it its edge shows white against the sky.
     * For the world's vertex shader, which raises it, the shadows', which cast it from the very
     * same snowAlongWood, and the world's fragment shader, which draws it white as far as
     * snowRidgeLain says, under cover or not: the ridge is the snow that lies there. It uses uSnowLying.
     */
    static final String RIDGE = String.format(Locale.ROOT, """
            const float SNOW_RIDGE_HIGH = %.4f, SNOW_RIDGE_OF_THICK = %.4f, SNOW_RIDGE_WIDE = %.4f, SNOW_FINE_FROM = %.4f, SNOW_FINE_TO = %.4f, SNOW_UNSNOWED = %.4f;
            // How far up wood faces at a place, as the ridge has it: 0 on what faces the side, a
            // bole's flank, or down, and 1 on the top of a level limb.
            float snowRidgeTop(vec3 facing) {
                return smoothstep(0.15, 0.75, facing.z);
            }
            // How much of the ridge lies at a place on wood whose top is so: the crest alone
            // with a light fall, a thin line, and as far as the ridge reaches with a deep one.
            float snowRidgeLain(float top) {
                return smoothstep(0.0, 0.2, top - 1.0 + min(uSnowLying * 2.5, 1.0));
            }
            // How far the ridge moves a vertex of wood that faces so and carries code for its
            // snow (SpriteGeometry.WOOD, Flora.woodCode), out of doors as outdoors says: up, and
            // out to its sides. held is what the fragment shader is told: how far up the wood
            // faces for its ridge and how thick it is, both 0 where it holds no snow of its own.
            vec3 snowAlongWood(vec3 facing, float code, bool outdoors, out vec2 held) {
                if (code < -0.5 * SNOW_UNSNOWED) {
                    held = vec2(0.0);
                    return vec3(0.0);
                }
                float top = snowRidgeTop(facing);
                held = vec2(top, abs(code));
                if (!outdoors || code <= 0.0 || top <= 0.0) {
                    return vec3(0.0);
                }
                float high = uSnowLying * min(SNOW_RIDGE_OF_THICK * code, SNOW_RIDGE_HIGH) * top * snowRidgeLain(top);
                return vec3(facing.xy * (SNOW_RIDGE_WIDE * high), high);
            }
            """, RIDGE_HIGH, RIDGE_OF_THICK, RIDGE_WIDE, FINE_FROM, FINE_TO, UNSNOWED);

    /** COVER and the snow's shape, joined on after WORLD_FRAGMENT_RELIEF and before the world's main: it uses facHash and uSnowLying. */
    static final String GLSL = COVER + RIDGE + String.format(Locale.ROOT, """
            // How high snow stands at its lip over what it lies on, and how far its swells,
            // its lumps, its wind ripples and the grains of its crust stand out of it, in tiles.
            const float SNOW_RIM = 0.035, SNOW_SWELL = 0.05, SNOW_LUMP = 0.02, SNOW_RIPPLE = 0.005, SNOW_CRUST = 0.0011;
            // Snow's slopes are gentle and the eye is used to seeing them under a low sun: they are lit this many times steeper than they are.
            const float SNOW_STEEP = 4.5;
            // How deep under its tops a lump's hollow is drawn, in tiles: see snowSunk.
            const float SNOW_DEEP = 0.05;
            // How bright a crystal that throws the sun at the eye is, against the sun on white.
            const float SNOW_GLINT = 14.0;
            // What snowLaid found besides how much snow lies: the height of its surface in
            // tiles with the slope it is lit by, east and south, how far down in a hollow the
            // place is, 0 on a top and 1 at the bottom, how much of the ground just clear of
            // the snow's lip is wet from it, and how far a boot or a tyre has pressed the snow down, 0 to 1.
            vec3 snowForm = vec3(0.0);
            float snowHollow = 0.0, snowMelt = 0.0, snowTrodden = 0.0;

            // Where the viewer has walked and driven since the snow came: see SnowPrints. A
            // footprint is its place, counted as vPlace is, with the way the foot pointed
            // and how high the ground it trod stands. A rut is a stretch of one wheel's
            // path from one place to the next, down the middle of its tyre.
            // uSnowRutUp is how high the ground under each stretch stands, four stretches to a vec4, uSnowTrodBox
            // holds every mark, its least corner and its greatest, and uSnowTrod is how many
            // footprints there are and how many stretches of rut.
            uniform vec4 uSnowPrint[%d];
            uniform vec4 uSnowRut[%d];
            uniform vec4 uSnowRutUp[%d];
            uniform vec4 uSnowTrodBox;
            uniform vec2 uSnowTrod;
            // How deep a boot and a tyre sink, and half a tyre's width, in tiles.
            const float SNOW_TROD = 0.04, SNOW_TYRE = 0.1;
            // A mark is on the ground it was made on and not on the roof over it: within this of its height, half a storey, which a hillside stays inside from one end of a rut to the other.
            const float SNOW_SAME_FLOOR = 1.2;

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
                for (int i = 0; i < %d; i++) {
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
                for (int i = 0; i < %d; i++) {
                    if (float(i) >= uSnowTrod.y) {
                        break;
                    }
                    if (abs(floorUp - uSnowRutUp[i / 4][i - 4 * (i / 4)]) > SNOW_SAME_FLOOR) {
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
                    float far = abs(off) / SNOW_TYRE;
                    if (far < nearest) {
                        nearest = far;
                        vec2 dent = snowDent(far);
                        // Counted along the stretch itself: counted on the map, the bars would jump whenever vPlace's corner moved.
                        float bars = 0.82 + 0.18 * smoothstep(-0.3, 0.3, sin(dot(from, along) * inversesqrt(run) * 70.0 + abs(off) * 40.0));
                        pressed = vec3(min(dent.x, 0.0) * bars + max(dent.x, 0.0), across * (dent.y / SNOW_TYRE * sign(off))) * rutted;
                    }
                }
                return pressed;
            }

            // Snow on what grows. None of it is a wash of white over the plant: it lies
            // where it would, by which way the surface faces and how open it is to the
            // sky, and comes sprig by sprig and clump by clump as more of it lies.

            // On a sprig of a crown. outward is how far up the crown's outside faces where
            // the sprig grows, 1 on a bough's top and under 0 beneath the crown; leafUp how
            // far up the leaf at the pixel faces on the side that is seen, so a leaf looked
            // at from below shows its dark underside; solid how solid the sprig's picture
            // is there, least at a leaf's edge, where the snow on the leaf's top shows from
            // below as a white rim; each is the sprig's own number. A bough of needles is
            // level and holds more than a spray of leaves does.
            float snowOnSprig(float outward, float leafUp, float solid, float each, bool needled) {
                float top = smoothstep(-0.1, 0.65, outward) * (needled ? 1.25 : 1.0);
                float lain = smoothstep(0.05, 0.5, uSnowLying * (0.25 + 0.95 * top) - 0.25 * each);
                float rim = top * (1.0 - smoothstep(0.55, 0.8, solid)) * 0.7;
                return min(lain * max(mix(0.2, 1.0, smoothstep(-0.05, 0.6, leafUp)), rim), 0.96);
            }

            // On bark and the ivy on it, by the way its surface faces, at q, a place on the map moved
            // by its height so that a limb over another does not carry the same clumps. What faces
            // nearly up, the top of a near-level limb, a root, a fork, holds lying snow in clumps that
            // join along it as more lies: a light fall leaves a thin line on the flattest tops only,
            // and steep wood none. What faces the way a blizzard came holds what it drove there.
            // ridgeTop is how far up the wood faces at the place as the ridge of snow along its
            // top has it (snowRidgeTop, as the vertex shader worked it out), 0 on ivy: the ridge
            // is white as far as snowRidgeLain says, under cover or not, as it stands. thick is the
            // wood's radius there, 0 on a bole, on ivy and on wood with no snow of its own: a twig
            // a real fall frosts white nearly all round, the thinner the whiter. The rest is less
            // under cover, and there is nothing where no snow lies.
            float snowOnBark(vec3 facing, vec2 q, float ridgeTop, float thick) {
                float clumps = snowNoise(q * 7.0, vec2(420.0), 41.0).x;
                float top = smoothstep(0.45, 0.9, facing.z);
                float lain = top * smoothstep(0.05, 0.35, uSnowLying * (0.3 + 0.9 * top) - 0.25 * clumps);
                float laden = ridgeTop > 0.0 ? snowRidgeLain(ridgeTop) : 0.0;
                float frost = thick > 0.0 ? 0.85 * (1.0 - smoothstep(SNOW_FINE_FROM, SNOW_FINE_TO, thick)) * smoothstep(0.05, 0.45, uSnowLying * (0.6 + 0.6 * ridgeTop)) : 0.0;
                float windward = dot(normalize(facing.xy + vec2(1e-5, 0.0)), uSnowPlaster) * (1.0 - smoothstep(0.6, 0.9, abs(facing.z)));
                float stuck = smoothstep(0.12, 0.5, windward - 0.3 * clumps);
                return min(max(laden, max(max(lain, frost), stuck) * clamp(1.0 - 0.8 * snowShelter, 0.0, 1.0)), 0.9);
            }

            // On the closed mass of leaves inside a crown, by how far up it faces.
            float snowOnMass(float up) {
                float top = smoothstep(0.1, 0.8, up);
                return 0.85 * top * smoothstep(0.05, 0.45, uSnowLying * (0.3 + 1.1 * top));
            }

            // How deep snow lies round the foot of a plant that stands as a card, in tiles: as
            // snowStood has it for a strip of grass, by the snow's thickness without its ragged
            // edge, which a card turned to face the eye would carry across itself as the eye moves.
            float snowAround(vec2 q, float foot) {
                return SNOW_DEPTH * uSnowLying * snowCovers(snowThick(q, foot).x);
            }

            // On a plant that stands as a card, the game's sprite of a tree or a bush. A card
            // has no way it faces but the eye's, so what of the plant faces the sky is read off
            // its picture: band is how solid the picture is a little way over the pixel, as far
            // as the snow on a top is thick, and clear how solid it is a good way over it, past
            // the gaps between the leaves of one mass. With nothing over it for both the pixel
            // is on the top of the crown or of a mass of leaves. above is how far
            // down on the plant the eye looks, 0 level with it or under it and 1 from high
            // over it: the game paints its plants as seen from above, and what is looked down
            // on is their tops, which is where the snow is. leafy is how far the pixel is leaves
            // and not bark, lit how far it is in the light and not a leaf in shade, and each
            // the number of the clump of leaves it is in. Snow lies on the tops as a crown of
            // the mod's own holds it (see snowOnSprig): clump by clump as more lies, little on
            // the sides and underneath, and none on leaves in shade nor on bark: snow along a bough
            // of a sprite reads as a white outline round the wood.
            float snowOnCard(float band, float clear, float above, float leafy, float lit, float each) {
                float open = clamp(1.0 - 0.8 * snowShelter, 0.0, 1.0);
                float top = max((1.0 - smoothstep(0.3, 0.7, band)) * (1.0 - smoothstep(0.3, 0.7, clear)), 0.85 * above);
                float lain = smoothstep(0.05, 0.5, uSnowLying * (0.15 + 1.05 * top) - 0.25 * each);
                return min(lain * leafy * lit * mix(0.55, 1.0, top) * open, 0.95);
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

            """, SnowPrints.PRINTS, SnowPrints.RUTS, SnowPrints.RUTS / 4, SnowPrints.PRINTS, SnowPrints.RUTS);

    /** The most of a person's upward faces snow covers: one who moves about holds no more than a dusting. */
    static final float DUSTING = 0.8f;

    /**
     * Snow settled on a person, for the fragment shaders that draw people for the picture to
     * light (CharacterDraw, OwnArms). How much has settled on the person drawn comes from
     * FigureSnow, as uSnowOn: 0 in Low and wherever none falls.
     */
    static final String ON_PEOPLE = BASICS + String.format(Locale.ROOT, """
            // How much snow has settled on the person drawn, 0 to 1, and the world's up as the eye sees it.
            uniform float uSnowOn;
            uniform vec3 uUpSeen;
            const float SNOW_DUSTING = %.4f;

            // How much snow lies at a point of a person, or of what they wear and hold, 0 to 1. up
            // is how far the surface there faces up, and at where the point is on the body as it
            // was made, so a flake stays where it settled as the body moves. Snow settles on what
            // faces up, the crown of the head or a hat, the shoulders, the tops of the arms and of
            // what is held, flake by flake and in little clumps that join as more settles.
            float snowOnPerson(float up, vec2 at) {
                if (uSnowOn <= 0.0) {
                    return 0.0;
                }
                float top = smoothstep(0.35, 0.85, up);
                float flakes = snowNoise(at * 24.0, vec2(4096.0), 51.0).x * 0.6 + snowNoise(at * 90.0, vec2(4096.0), 52.0).x * 0.4;
                return SNOW_DUSTING * top * smoothstep(0.1, 0.45, uSnowOn * (0.45 + 0.5 * top) - 0.55 * flakes);
            }

            """, DUSTING);

    /** The most vehicles the picture lays snow on at once, the nearest. */
    static final int MOST_CARS = 16;

    /**
     * Snow on vehicles, for the pass that lights the picture's people and vehicles. Which
     * vehicles stand where, and how much each carries, comes from Characters and FigureSnow.
     */
    static final String ON_VEHICLES = BASICS + String.format(Locale.ROOT, """
            // The vehicles drawn, uCars of them: the ground under each one's middle and half its length, the
            // way it faces and half its width, its own up and its height, and how much snow it
            // carries on its roof and the rest of its top, on its bonnet and on its windscreen, 0 to
            // 1 (see FigureSnow). uSatSnow is what the bonnet of the vehicle the viewer sits in carries.
            uniform float uCars;
            uniform vec4 uCarAt[%d];
            uniform vec4 uCarAhead[%d];
            uniform vec4 uCarUp[%d];
            uniform vec4 uCarSnow[%d];
            uniform float uSatSnow;

            // How much snow lies on a panel of a vehicle that faces up so far, where lain of it has
            // settled, 0 to 1, at on, a place counted on the vehicle in tiles, so the snow goes where
            // the vehicle goes. It holds on what is nearly level, a roof, a bonnet, a boot lid, and
            // the more has lain the steeper it holds, so a deep fall lies on a raked windscreen too.
            // Thin, it lies in patches with the paint between; deep, it is one slab.
            float snowOnPanel(float up, vec2 on, float lain) {
                if (lain <= 0.0) {
                    return 0.0;
                }
                float holds = smoothstep(0.55 - 0.25 * lain, 0.85 - 0.15 * lain, up);
                float lumps = snowNoise(on * 2.5, vec2(4096.0), 61.0).x * 0.65 + snowNoise(on * 9.0, vec2(4096.0), 62.0).x * 0.35;
                return smoothstep(0.05, 0.3, lain * holds * 1.3 - 0.3 * lumps);
            }

            // How much snow lies on a vehicle drawn at a point of it whose surface faces n, 0 to 1:
            // what its roof, its bonnet or its windscreen carries, by where on it the point is, and
            // none on the wheels, the sills and what is under them.
            float snowOnVehicle(vec3 at, vec3 n) {
                for (int i = 0; i < %d; i++) {
                    if (float(i) >= uCars) {
                        break;
                    }
                    vec3 ahead = uCarAhead[i].xyz, up = uCarUp[i].xyz;
                    vec3 from = at - uCarAt[i].xyz;
                    float along = dot(from, ahead), aside = dot(from, cross(up, ahead)), high = dot(from, up);
                    float halfLength = uCarAt[i].w, wide = uCarAhead[i].w, tall = uCarUp[i].w;
                    if (abs(along) > halfLength + 0.3 || abs(aside) > wide + 0.3 || high < -0.2 || high > tall + 0.4) {
                        continue;
                    }
                    // The roof is the top of it, whatever is under it; ahead of the roof is the
                    // bonnet, and between them the windscreen, steeper than the bonnet. Measured
                    // on a CarNormal from this ground: sills 0.1 to 0.2 up, tyre tops about 0.4,
                    // bonnet about 0.65, roof about 1.05 of its 1.18.
                    float roof = smoothstep(0.82, 0.9, high / tall);
                    float front = smoothstep(0.05, 0.3, along / halfLength) * (1.0 - roof);
                    float screen = front * (1.0 - smoothstep(0.75, 0.9, n.z));
                    vec4 carried = uCarSnow[i];
                    float lain = mix(mix(carried.x, carried.y, front), carried.z, screen);
                    return snowOnPanel(n.z, vec2(along, aside), lain) * smoothstep(0.4, 0.5, high);
                }
                return 0.0;
            }

            """, MOST_CARS, MOST_CARS, MOST_CARS, MOST_CARS, MOST_CARS);
}
