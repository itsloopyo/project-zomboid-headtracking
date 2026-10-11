package com.cameraunlock.zomboid;

/**
 * The sky's shader text: an atmosphere that follows the game's clock and
 * weather, the sun, the moon and stars, and clouds marched through a slab of
 * noise. Pipeline compiles it and draws it into the pixels the world left at
 * the far plane.
 *
 * ATMOSPHERE is shared with the world's shader, which fades the far world into
 * it, so the two cannot disagree about the colour of the sky behind a hill.
 */
final class Sky {
    private Sky() {}

    /**
     * The clear sky in direction d, in linear light. Colours arrive as display values, which
     * is how SkyState mixes them, and leave squared-up for the lighting maths.
     */
    static final String ATMOSPHERE = """
            uniform vec3 uSun;
            uniform vec3 uSunColor;
            uniform vec3 uZenith;
            uniform vec3 uHorizon;
            uniform vec3 uGlow;
            // Lightning's light over the whole sky, as light, and the way to where it is brightest:
            // the cloud a bolt came out of. Nought with no lightning. See Lightning.see.
            uniform vec3 uFlashSky;
            uniform vec3 uFlashWay;

            vec3 atmosphere(vec3 d) {
                float low = 1.0 - clamp(d.z, 0.0, 1.0);
                vec3 color = mix(uZenith, uHorizon, pow(low, 2.6));
                float mu = max(dot(d, uSun), 0.0);
                color += uGlow * (pow(mu, 2.5) * pow(low, 5.0) * 0.9 + pow(low, 14.0) * 0.25);
                color += uSunColor * (0.16 * pow(mu, 18.0) + 0.5 * pow(mu, 300.0));
                // A flash lights the air all round, the way to it most of all.
                return pow(max(color, vec3(0.0)), vec3(2.2)) + uFlashSky * (0.15 + pow(max(dot(d, uFlashWay), 0.0), 5.0));
            }

            // The air between the eye and what it looks at, and more of it towards a low sun, which shines
            // through a long way of air. The sun's, not whatever lights the scene: a bolt of lightning low
            // over the land is the light for a moment and would fill the picture with glare.
            float distanceHaze(vec3 direction, float range) {
                float sunward = pow(max(dot(direction, uSun), 0.0), 3.0)
                        * (1.0 - smoothstep(0.1, 0.75, uSun.z)) * step(0.0, uSun.z);
                return 1.0 - exp(-range * (0.0022 + 0.016 * sunward));
            }

            // How thick the air is between the eye and what it looks at, for fog, rain and snow:
            // x is how much light a tile of it takes out of a line of sight, y how deep it lies
            // over the eye, z how far round the eye it thins away. See Eyesight.
            uniform vec3 uVeil;
            // The light open ground has where the picture is: thick air is never darker than what
            // that light makes of it, whatever the sky behind it does.
            uniform vec3 uVeilLight;

            // The colour thick air is lit to: the sky's at the horizon that way, the sun's glow
            // in it included, with most of its colour taken out.
            vec3 veilColor(vec3 d) {
                vec3 air = atmosphere(normalize(vec3(d.xy, 0.0) + vec3(1e-5, 0.0, 0.0)));
                return max(mix(vec3(dot(air, vec3(0.3, 0.59, 0.11))), air, 0.35), uVeilLight);
            }

            // How much of what stands that far from the eye the air hides: see Eyesight.veiled.
            float veiling(float range) {
                float past = range - uVeil.z;
                float counted = past > 20.0 ? past : log(1.0 + exp(past));
                return 1.0 - exp(-uVeil.x * counted);
            }

            // How much of the sky that way the air hides: it is deepest along the ground.
            float skyVeiling(vec3 d) {
                return 1.0 - exp(-uVeil.x * uVeil.y / max(d.z, 0.05));
            }

            float ridgeHash(vec2 p) {
                return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
            }

            float ridgeLine(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                return mix(mix(ridgeHash(cell), ridgeHash(cell + vec2(1.0, 0.0)), f.x),
                        mix(ridgeHash(cell + vec2(0.0, 1.0)), ridgeHash(cell + vec2(1.0, 1.0)), f.x), f.y);
            }

            // The map ends, and the country does not: beyond it stand wooded hills, ridge
            // behind ridge, each paler and bluer than the one in front as the air between
            // takes its colour. They are a backdrop only, where no ground of the game's is.
            // Whatever shows the sky shows them, water as well, or a lake mirrors a sky the
            // picture does not have.
            vec3 ridges(vec3 d, vec3 color) {
                vec2 round = normalize(d.xy + vec2(1e-6));
                float rise = d.z / max(length(d.xy), 1e-4);
                // No ridge's edge is higher than 0.1185, and the last ridge's is nowhere lower
                // than 0.0025: over the one the line of sight clears every ridge, and under
                // the other it meets the last, whatever shape their crests have.
                if (rise > 0.12) {
                    return color;
                }
                vec3 air = atmosphere(vec3(d.xy, 0.0));
                vec3 wooded = pow(mix(uHorizon, uZenith, 0.6) * vec3(0.2, 0.3, 0.22), vec3(2.2));
                if (rise < 0.002) {
                    return mix(wooded, air, 0.42);
                }
                for (int ridge = 0; ridge < 3; ridge++) {
                    float nth = float(ridge);
                    vec2 at = round * (2.2 + 1.7 * nth) + 13.0 * nth;
                    float crest = ridgeLine(at) * 0.6 + ridgeLine(at * 3.1 + 5.0) * 0.28 + ridgeLine(at * 11.0) * 0.09 + ridgeLine(at * 47.0) * 0.03;
                    float high = (0.105 - 0.03 * nth) * crest * crest + 0.012 - 0.004 * nth;
                    float edge = 0.0015;
                    color = mix(color, mix(wooded, air, 0.82 - 0.2 * nth), 1.0 - smoothstep(high - edge, high + edge, rise));
                }
                return color;
            }
            """;

    /**
     * The clouds alone, drawn at half the picture's size and then softened: they have no hard
     * edges to lose, and marching every pixel of the picture through them costs four times as much. rgb is the light
     * the clouds send to the eye, a what is left of the sky behind them.
     */
    static final String CLOUDS = """
            #version 120
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec2 uTan;
            uniform vec2 uSize;
            uniform vec3 uLight;
            uniform vec3 uLightColor;
            uniform vec3 uZenith;
            uniform vec3 uHorizon;
            uniform float uCover;
            uniform vec2 uDrift;
            uniform sampler3D uNoise;
            // Lightning inside the cloud: the way to where it glows, and its light. See Lightning.see.
            uniform vec3 uBurstWay;
            uniform vec3 uBurstLight;

            const float BOTTOM = 1.0;
            const float TOP = 2.3;
            // How far round itself, in the cloud's measure, lightning inside it lights it.
            const float BURST_REACH = 0.9;
            const int STEPS = 48;
            const int LIGHT_STEPS = 5;

            // A pattern of offsets that never puts two alike next to each other, so the steps
            // of the march through the clouds show as an even grain and not as bands or clumps.
            float offset(vec2 p) {
                return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));
            }

            float density(vec3 p) {
                float h = (p.z - BOTTOM) / (TOP - BOTTOM);
                // Flat underneath and rounded on top, as a cloud that has risen to its base is.
                float shape = smoothstep(0.0, 0.08, h) * (1.0 - smoothstep(0.25, 1.0, h));
                // The noise of a cloud's body is at most 1 and its bank adds at most 0.17: where
                // the most there could be is under nought there is no cloud, and the noise is
                // not read. The tests stop a thousandth short, so rounding cannot change an answer.
                float most = shape - (1.0 - uCover);
                if (most <= -0.171) {
                    return 0.0;
                }
                vec3 q = vec3(p.xy * 0.17 + uDrift, p.z * 0.21);
                // A sky is not evenly sown with cloud. Over many miles there are banks of it
                // and open blue between, and within a bank the clouds are of every size.
                float bank = texture3D(uNoise, vec3(p.xy * 0.021 + uDrift * 0.4, 0.37)).g;
                if (most - (0.5 - bank) * 0.34 <= -0.001) {
                    return 0.0;
                }
                // The air a cloud forms in is turning over, which pulls its outline about.
                vec3 pulled = q + (texture3D(uNoise, q * 0.9 + 0.31).rgr - 0.5) * 0.22;
                float body = texture3D(uNoise, pulled).r * 0.68 + texture3D(uNoise, pulled * 2.3 + 0.17).g * 0.32;
                float d = body * shape - (1.0 - uCover) - (0.5 - bank) * 0.34;
                if (d <= 0.0) {
                    return 0.0;
                }
                // The edge of a cloud is where it is drying out into the air: it frays into
                // curls and wisps, coarse ones and fine ones, and the heart of it does not.
                // It moves with the cloud, and only a little faster: a cloud changes its shape
                // over minutes.
                float wear = texture3D(uNoise, q * 3.7 + vec3(uDrift * 0.3, 0.0)).g * 0.62
                        + texture3D(uNoise, q * 9.1 + vec3(uDrift * 0.6, 0.4)).r * 0.38;
                d -= wear * 0.34 * (1.0 - min(d * 3.2, 1.0));
                return clamp(d * 7.0, 0.0, 1.0);
            }

            void main() {
                vec2 ndc = gl_FragCoord.xy / uSize * 2.0 - 1.0;
                vec3 d = normalize(uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y));
                gl_FragColor = vec4(0.0, 0.0, 0.0, 1.0);
                if (d.z < 0.03) {
                    return;
                }
                float near = BOTTOM / d.z;
                float fade = 1.0 - smoothstep(9.0, 30.0, near);
                float stride = (TOP - BOTTOM) / d.z / float(STEPS);
                float t = near + stride * offset(gl_FragCoord.xy);
                float mu = max(dot(d, uLight), 0.0);
                float phase = 0.7 + 1.1 * pow(mu, 10.0);
                vec3 sunLight = pow(uLightColor, vec3(2.2)) * 2.6;
                // What reaches a cloud's shaded side: the sky, and the sun's light after it has
                // been passed about between the clouds, which is white and most of it.
                vec3 skyLight = pow(mix(uHorizon, uZenith, 0.55), vec3(2.2)) * 0.6 + sunLight * 0.3;
                float through = 1.0;
                vec3 light = vec3(0.0);
                // Where lightning glows in the cloud: a little above its base, the way the eye
                // sees the channel go into it. However far off, never past where the clouds fade.
                bool bursting = dot(uBurstLight, uBurstLight) > 0.0;
                vec3 burstAt = uBurstWay / max(uBurstWay.z, 0.04) * (BOTTOM + 0.3);
                burstAt *= min(1.0, 26.0 / length(burstAt));
                for (int i = 0; i < STEPS; i++) {
                    vec3 p = d * t;
                    float dens = density(p);
                    if (dens > 0.0) {
                        float depth = 0.0;
                        for (int j = 1; j <= LIGHT_STEPS; j++) {
                            depth += density(p + uLight * (0.11 * float(j)));
                        }
                        // The second term is light that has bounced about inside the cloud:
                        // it is why the shaded side of a cloud is grey and not black.
                        float direct = max(exp(-depth * 1.3), 0.45 * exp(-depth * 0.22));
                        float h = (p.z - BOTTOM) / (TOP - BOTTOM);
                        vec3 lit = sunLight * (direct * phase) + skyLight * (0.45 + 0.75 * h);
                        if (bursting) {
                            // Lightning's light scatters through the whole body of the cloud
                            // from where it is, and a little through all the deck.
                            // Unevenly: the cloud's own billows and hollows take it in or hide it.
                            vec3 off = p - burstAt;
                            float billows = texture3D(uNoise, p * 0.55 + vec3(uDrift, 0.21)).r;
                            lit += uBurstLight * (exp(-dot(off, off) / (BURST_REACH * BURST_REACH)) * (0.25 + 1.5 * billows * billows) + 0.02);
                        }
                        float taken = 1.0 - exp(-dens * stride * 9.0);
                        light += through * taken * lit;
                        through *= 1.0 - taken;
                        if (through < 0.02) {
                            break;
                        }
                    }
                    t += stride;
                }
                gl_FragColor = vec4(light * fade, 1.0 - (1.0 - through) * fade);
            }
            """;

    static final String FRAGMENT = "#version 120\n" + ATMOSPHERE + """
            uniform vec3 uRight;
            uniform vec3 uUp;
            uniform vec3 uForward;
            uniform vec2 uTan;
            uniform vec2 uSize;
            uniform vec3 uMoon;
            uniform float uNight;
            uniform float uTime;
            uniform sampler2D uClouds;

            float hash3(vec3 p) {
                return fract(sin(dot(p, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
            }

            float blotch(vec2 p) {
                vec2 cell = floor(p);
                vec2 f = fract(p);
                f = f * f * (3.0 - 2.0 * f);
                float a = hash3(vec3(cell, 7.0)), b = hash3(vec3(cell + vec2(1.0, 0.0), 7.0));
                float c = hash3(vec3(cell + vec2(0.0, 1.0), 7.0)), e = hash3(vec3(cell + vec2(1.0, 1.0), 7.0));
                return mix(mix(a, b, f.x), mix(c, e, f.x), f.y);
            }

            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                vec2 ndc = uv * 2.0 - 1.0;
                vec3 d = normalize(uForward + uRight * (ndc.x * uTan.x) + uUp * (ndc.y * uTan.y));
                vec3 color = atmosphere(d);
                float mu = max(dot(d, uSun), 0.0);
                color += pow(uSunColor, vec3(2.2)) * 60.0 * smoothstep(0.99988, 0.99996, mu);

                if (uNight > 0.0) {
                    float lit = smoothstep(0.0, 0.2, d.z) * uNight;
                    vec3 grid = d * 240.0;
                    float seed = hash3(floor(grid));
                    float star = step(0.9972, seed) * (1.0 - smoothstep(0.0, 0.5, length(fract(grid) - 0.5)));
                    color += vec3(0.85, 0.9, 1.0) * star * lit * (0.65 + 0.35 * sin(uTime * 2.5 + seed * 90.0));
                    // The moon is a ball of grey rock lit from the side: darker towards its rim,
                    // with the seas of old lava as darker patches, and a glow in the air round it.
                    float moon = dot(d, uMoon);
                    vec3 across = normalize(cross(uMoon, vec3(0.0, 0.0, 1.0)));
                    vec2 on = vec2(dot(d, across), dot(d, cross(uMoon, across))) / 0.021;
                    float out2 = dot(on, on);
                    if (moon > 0.0 && out2 < 1.0) {
                        float round = sqrt(1.0 - out2);
                        float seas = smoothstep(0.45, 0.7, blotch(on * 2.1 + 4.0) * 0.65 + blotch(on * 5.3) * 0.35);
                        float pits = blotch(on * 17.0);
                        float face = (0.45 + 0.55 * round) * (1.0 - 0.38 * seas) * (0.9 + 0.2 * pits);
                        color += vec3(0.95, 0.94, 0.9) * uNight * (2.4 * face * (1.0 - smoothstep(0.9, 1.0, out2)));
                    }
                    color += vec3(0.95, 0.96, 1.0) * uNight * (0.1 * pow(max(moon, 0.0), 300.0) + 0.03 * pow(max(moon, 0.0), 40.0));
                }

                vec4 c = texture2D(uClouds, uv);
                color = color * c.a + c.rgb;
                color = ridges(d, color);
                vec3 air = atmosphere(vec3(d.xy, 0.0));
                color = mix(color, air, 1.0 - smoothstep(-0.04, 0.0, d.z));
                if (uVeil.x > 0.0) {
                    color = mix(color, veilColor(d), skyVeiling(d));
                }
                gl_FragColor = vec4(color, 1.0);
            }
            """;
}
