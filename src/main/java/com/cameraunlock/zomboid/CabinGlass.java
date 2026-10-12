package com.cameraunlock.zomboid;

/**
 * The glass of the vehicle a viewer sits in, as shader source: the dust and dried rain on a pane,
 * the sky it mirrors, the wipers' arcs, the rain on it (GlassRain) and the lamps of vehicles
 * coming the other way flaring in it. The pass that lights the viewer's own vehicle has it for the
 * game's own glass, and CabinDraw for the panes of the mod's own interiors.
 *
 * A shader that has it declares uTime, uRaining, uLight, uLightColor and uEye, has the sky's
 * atmosphere and the headlights' BEAMS, and puts NOISE before PANES and FLARED after.
 */
final class CabinGlass {
    private CabinGlass() {}

    static final String NOISE = """
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
            """;

    static final String PANES = """
            float hash21(vec2 p) {
                return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
            }

            // Half the width of the front screen, in tiles. A place on it is counted across
            // from its middle, the driver's side below nought, and up from 0.3 under its foot.
            float gScreenHalf = 0.72;
            // How far the wipers' blades reach from their feet, in tiles.
            float gWiperReach = 0.48;

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
                float reach = gWiperReach;
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
                float reach = gWiperReach;
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
            """ + GlassRain.GLSL + """

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

            """;

    static final String FLARED = """
            // The lamps of a vehicle that is coming the other way, in the glass: each a hard
            // point with a soft bloom round it, more of it the dirtier the glass, and a
            // streak across where the wipers have smeared it.
            vec4 flared(vec4 glass, vec3 look, float dirt) {
                vec3 light = vec3(0.0);
                for (int i = 0; i < 2; i++) {
                    vec3 to = uBeamAt[i].xyz - uEye;
                    float far2 = dot(to, to);
                    vec3 way = to * inversesqrt(max(far2, 1e-4));
                    // Only lamps that are lit, shine towards the eye and are over two tiles from it.
                    float towards = smoothstep(0.5, 0.95, dot(uBeamWay[i], -way));
                    if (uBeamAt[i].w <= 0.0 || towards <= 0.0 || far2 < 4.0) {
                        continue;
                    }
                    vec3 across = normalize(vec3(-uBeamWay[i].y, uBeamWay[i].x, 0.0)) * 0.42;
                    float strength = uBeamAt[i].w * towards / (1.0 + 0.012 * far2);
                    for (int lamp = 0; lamp < 2; lamp++) {
                        vec3 off = look - normalize(to + across * (lamp == 0 ? -1.0 : 1.0));
                        float round2 = dot(off, off);
                        float level = off.z * off.z;
                        light += vec3(1.0, 0.93, 0.78) * strength * (1.6 * exp(-round2 * 60000.0) + (0.05 + 0.3 * dirt) * exp(-round2 * 900.0)
                                + (0.02 + 0.1 * dirt) * exp(-level * 30000.0 - (round2 - level) * 25.0));
                    }
                }
                float more = clamp(max(light.r, max(light.g, light.b)), 0.0, 0.7);
                float cover = glass.a + (1.0 - glass.a) * more;
                return vec4((glass.rgb * glass.a + light) / max(cover, 1e-3), cover);
            }

            """;
}
