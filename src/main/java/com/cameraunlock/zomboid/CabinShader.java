package com.cameraunlock.zomboid;

/**
 * The parts of a cabin the picture builds itself, as shader source for the pass that lights
 * the viewer's own vehicle: the glasshouse over the driver and what furnishes it. They come
 * after that pass's own declarations and before its main, which names them ahead of use.
 */
final class CabinShader {
    private CabinShader() {}

    /**
     * Parts by number: 12 a screen pillar, 13 the rail over the screen, 14 the roof's lining,
     * 15 the rail over a door, 16 the pillar behind a front door, 17 a sun visor, 18 a grab
     * handle, 19 the pad over the dashboard, 20 the roll along a door's top, 21 an armrest,
     * 22 the mirror's foot on the screen.
     */
    static final String SOLIDS = """
            // How far out the glass of the doors is at the sills, and at the roof.
            vec2 widths() {
                float wide = abs(uDash.x) + uScreen.w;
                return vec2(wide, wide - 0.075);
            }

            // The height of the roof's lining over a place that far out from the middle.
            float liningAt(float out2) {
                float narrow = widths().y;
                return uHouse.y + 0.03 - 0.045 * out2 * out2 / (narrow * narrow);
            }

            // Where the pad over the dashboard begins, along: at the shelf's own edge, but
            // behind the instruments, which stand before it.
            float padFrom() {
                return min(max(uPad.x, uWheel.x + 0.3), uScreen.x - 0.08);
            }

            // How far round the rim from its top a hand holds it, in radians, and how much
            // of the way from square to the rim to straight on from the forearm it lies.
            const float HAND_PLACE = 1.08;
            const float HAND_SLANT = 0.6;

            // Where each hand has the rim, the hand on the low side of across first: the
            // place and the way along the rim there towards its top; the ways out of the
            // hand's back and across it towards the thumb; its knuckles, the two ends of
            // its thumb, and the wrist and the elbow behind it. Worked out once for a pixel.
            vec3 gGrip[2], gAlong[2], gBack[2], gThumbward[2], gKnuckles[2], gRoot[2], gTip[2], gWrist[2], gElbow[2];

            // The elbow of an arm from that shoulder to that wrist. It hangs down and out
            // between the two.
            vec3 elbowOf(vec3 shoulder, vec3 wrist, float way) {
                vec3 reach = wrist - shoulder;
                float far2 = min(length(reach), 0.69);
                vec3 to = normalize(reach);
                float first = (far2 * far2 + 0.37 * 0.37 - 0.33 * 0.33) / (2.0 * far2);
                vec3 pole = vec3(0.0, way * 0.55, -1.0);
                return shoulder + to * first + normalize(pole - to * dot(pole, to)) * sqrt(max(0.37 * 0.37 - first * first, 0.0));
            }

            // At ten and two with the wheel straight. The hands hold the rim and go round
            // with it all the way to full lock, which Cabins.STEERING_RATIO keeps short
            // enough that neither has to let go: a rim that slips under them reads as a
            // wheel turning faster than the hands that turn it.
            void poseArms() {
                vec3 rise = cross(uWheelAxis, vec3(0.0, 1.0, 0.0));
                for (int i = 0; i < 2; i++) {
                    float way = i == 0 ? -1.0 : 1.0;
                    float turn = 1.5708 - way * HAND_PLACE - uSteer;
                    vec3 outward = vec3(0.0, cos(turn), 0.0) + rise * sin(turn);
                    vec3 along = (vec3(0.0, -sin(turn), 0.0) + rise * cos(turn)) * way;
                    vec3 grip = uWheel.xyz + outward * (uWheel.w * uKit.x);
                    vec3 shoulder = vec3(-0.06, uWheel.y + way * 0.25, -0.27);
                    // The hand carries on from the forearm. A fist square to the rim would
                    // have the wrist bent sideways by all of the angle between the forearm
                    // and the rim, so the hand lies part of the way between the two and the
                    // rim crosses the palm at a slant for the rest. The forearm is taken to
                    // the rim first and then to the wrist that gives.
                    vec3 fore = normalize(grip - elbowOf(shoulder, grip, way));
                    vec3 lie = normalize(mix(normalize(fore - along * dot(fore, along)), fore, HAND_SLANT));
                    vec3 back = normalize(cross(along, lie));
                    back *= sign(dot(back, outward - uWheelAxis));
                    vec3 wrist = grip + back * (uKit.y + 0.017) - lie * 0.09;
                    vec3 thumbward = cross(back, lie);
                    thumbward *= sign(dot(thumbward, along));
                    gElbow[i] = elbowOf(shoulder, wrist, way);
                    gGrip[i] = grip;
                    gAlong[i] = along;
                    gBack[i] = back;
                    gThumbward[i] = thumbward;
                    gKnuckles[i] = grip + back * (uKit.y + 0.011) + lie * 0.012;
                    gRoot[i] = wrist + lie * 0.03 + thumbward * 0.03 - back * 0.006;
                    gTip[i] = grip + along * 0.062 + normalize(back - lie) * (uKit.y + 0.0105);
                    gWrist[i] = wrist;
                }
            }

            // Distances to simple shapes, and the smooth joining of two of them, by the formulas Inigo
            // Quilez sets out (THIRD-PARTY-NOTICES.md, Acknowledgements).
            float blend(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            // A limb from a to b, as thick as from at a and as to at b.
            float limb(vec3 v, vec3 a, vec3 b, float from, float to) {
                vec3 along = b - a;
                float t = clamp(dot(v - a, along) / dot(along, along), 0.0, 1.0);
                return length(v - a - along * t) - mix(from, to, t);
            }

            // A hand's back from a to b: wide across the hand and thin through it, as
            // halves at a and at b.
            float slab(vec3 v, vec3 a, vec3 b, vec3 across, vec3 through, vec2 from, vec2 to) {
                vec3 along = b - a;
                float t = clamp(dot(v - a, along) / dot(along, along), 0.0, 1.0);
                vec3 off = v - a - along * t;
                vec2 half2 = mix(from, to, t);
                vec3 way = normalize(along);
                return (length(vec3(dot(off, across) / half2.x, dot(off, through) / half2.y, dot(off, way) / half2.y)) - 1.0) * half2.y;
            }

            // The driver's hands on the rim and the arms behind them, from shoulders that
            // stay with the seat when the head leans. Parts 23 a hand and 24 a sleeve.
            float arms(vec3 p, float nearest, inout float part) {
                float round4 = length(p - vec3(0.24, uWheel.y, -0.38)) - 0.66;
                if (uArms < 0.5 || round4 > nearest) {
                    return nearest;
                }
                if (gRoughly * round4 > 0.04) {
                    return min(nearest, round4);
                }
                for (int i = 0; i < 2; i++) {
                    float way = i == 0 ? -1.0 : 1.0;
                    vec3 along = gAlong[i], wrist = gWrist[i], elbow = gElbow[i];
                    vec3 shoulder = vec3(-0.06, uWheel.y + way * 0.25, -0.27);
                    // Four fingers closed round the rim, the back of the hand from the
                    // knuckles over the rim to the wrist, and the thumb from the wrist's
                    // side of the palm up along the face of the rim the driver sees.
                    vec3 g = p - gGrip[i];
                    float run = dot(g, along);
                    float fingers = length(g - along * run) - (uKit.y + 0.0125 - 0.003 * cos(run * 314.16));
                    vec2 ends = vec2(fingers, abs(run) - 0.036);
                    fingers = length(max(ends, 0.0)) + min(max(ends.x, ends.y), 0.0) - 0.004;
                    float palm = slab(p, gKnuckles[i], wrist, gThumbward[i], gBack[i], vec2(0.041, 0.015), vec2(0.031, 0.02));
                    float thumb = limb(p, gRoot[i], gTip[i], 0.0145, 0.0105);
                    float hand = blend(blend(fingers, palm, 0.012), thumb, 0.01);
                    if (hand < nearest) { nearest = hand; part = 23.0; }
                    // A bare arm is thinner than one in a sleeve.
                    float clad = uSleeve.w > 0.75 ? 1.0 : 0.86;
                    float fore = limb(p, wrist + normalize(elbow - wrist) * 0.02, elbow, 0.034 * clad, 0.047 * clad);
                    if (fore < nearest) { nearest = fore; part = uSleeve.w > 0.75 ? 24.0 : 23.0; }
                    clad = uSleeve.w > 0.25 ? 1.0 : 0.86;
                    float upper = limb(p, elbow, shoulder, 0.049 * clad, 0.06 * clad);
                    if (upper < nearest) { nearest = upper; part = uSleeve.w > 0.25 ? 24.0 : 23.0; }
                }
                return nearest;
            }

            float built(vec3 p, float nearest, inout float part) {
                float mid = abs(uDash.x);
                float c = (p.y - uWheel.y) * (uDash.x < 0.0 ? -1.0 : 1.0);
                vec3 v = vec3(p.x, abs(c - mid), p.z);
                vec2 wide = widths();
                float belt = min(uScreen.y, uScreen.z);
                vec3 top = vec3(uHouse.x, wide.y, uHouse.y);
                float d;
                // The pillars stand from the sills up, and the roof with what hangs from it
                // is all above the heads of those inside.
                float under = belt - 0.03 - uHouse.z * 1.2 - p.z;
                if (gRoughly * under > 0.04) {
                    nearest = min(nearest, under);
                } else {
                    vec3 foot = vec3(uScreen.x - 0.02, wide.x - 0.005, belt - 0.03);
                    d = rod(v, foot, top, uHouse.z);
                    if (d < nearest) { nearest = d; part = 12.0; }
                    d = rod(v, vec3(uPad.y, wide.x - 0.005, belt - 0.03), vec3(uPad.y - 0.04, wide.y, uHouse.y), uHouse.z * 1.15);
                    if (d < nearest) { nearest = d; part = 16.0; }
                    float below = uHouse.y - 0.085 - p.z;
                    if (gRoughly * below > 0.04) {
                        nearest = min(nearest, below);
                    } else {
                        d = rod(v, vec3(uHouse.x + 0.012, 0.0, uHouse.y + 0.014), top, uHouse.z * 0.85);
                        if (d < nearest) { nearest = d; part = 13.0; }
                        // The roof ends at the rail over each door: past it is the door's glass.
                        d = max(max(liningAt(v.y) - p.z, p.x - uHouse.x), v.y - wide.y);
                        if (d < nearest) { nearest = d; part = 14.0; }
                        d = rod(v, top, vec3(uPad.y - 1.6, wide.y, uHouse.y), uHouse.z * 0.8);
                        if (d < nearest) { nearest = d; part = 15.0; }
                        // A visor each side, folded up against the lining behind the rail.
                        float deep = min(0.085, (uHouse.x - 0.1) * 0.5);
                        vec3 visor = vec3(p.x - (uHouse.x - 0.04 - deep), v.y - mid, p.z - (liningAt(mid) - 0.02));
                        visor.z += visor.x * 0.1;
                        d = block(visor, vec3(deep - 0.009, min(mid - 0.07, 0.2), 0.003), 0.009);
                        if (d < nearest) { nearest = d; part = 17.0; }
                        if (c > mid) {
                            // A handle over the passenger's door, on two feet.
                            vec3 from = vec3(uHouse.x - 0.42, wide.y - 0.055, uHouse.y - 0.035), to = vec3(uHouse.x - 0.24, wide.y - 0.055, uHouse.y - 0.035);
                            d = min(rod(v, from, to, 0.011), min(rod(v, from, from + vec3(-0.02, 0.04, 0.03), 0.012), rod(v, to, to + vec3(0.02, 0.04, 0.03), 0.012)));
                            if (d < nearest) { nearest = d; part = 18.0; }
                        }
                    }
                }
                // A mirror on each front door: a housing outside the glass, its face to the
                // back, on a short arm from a sail at the foot of the screen's pillar.
                vec3 housed = vec3(p.x - uDoorMirror.x, v.y - uDoorMirror.y, p.z - uDoorMirror.z);
                float round3 = length(housed + vec3(0.0, 0.07, 0.03)) - 0.26;
                if (gRoughly * round3 > 0.04) {
                    nearest = min(nearest, round3);
                } else {
                    float reach = uDoorMirror.y - wide.x;
                    d = min(block(housed, vec3(0.026, 0.084, 0.044), 0.016),
                            rod(housed, vec3(0.0, -0.08, -0.03), vec3(0.012, 0.012 - reach, -0.072), 0.016));
                    d = min(d, block(housed - vec3(0.03, 0.008 - reach, -0.078), vec3(0.07, 0.004, 0.03), 0.008));
                    if (d < nearest) { nearest = d; part = 8.0; }
                }
                // The pad over the dashboard, from its roll before the driver to the glass.
                float padTop = max(uDash.y, uScreen.y) + 0.004;
                float from = padFrom(), to = uScreen.x + 0.1;
                d = block(vec3(p.x - (from + to) * 0.5, v.y, p.z - (padTop - 0.034)), vec3((to - from) * 0.5 - 0.028, wide.x + 0.03, 0.006), 0.028);
                if (d < nearest) { nearest = d; part = 19.0; }
                // The roll along a door's top and the armrest under it are hard by the door.
                float beside = wide.x - 0.11 - v.y;
                if (gRoughly * beside > 0.04) {
                    nearest = min(nearest, beside);
                } else {
                    d = rod(v, vec3(uPad.y - 1.3, wide.x, uScreen.z - 0.012), vec3(uScreen.x - 0.03, wide.x, uScreen.z - 0.012), 0.026);
                    if (d < nearest) { nearest = d; part = 20.0; }
                    d = block(vec3(p.x - 0.14, v.y - (wide.x - 0.045), p.z - (uScreen.z - 0.26)), vec3(0.24, 0.028, 0.01), 0.022);
                    if (d < nearest) { nearest = d; part = 21.0; }
                }
                if (uTrade > 0.5 && uTrade < 3.5) {
                    // What a vehicle carries for its work stands on the pad in the middle
                    // of the dashboard: 25 its box, and 26 the handset of a radio beside it
                    // on a cord. A taxi's meter is a smaller box and has no radio.
                    vec3 box = uTrade > 2.5 ? vec3(0.036, 0.15, 0.046) : uTrade > 1.5 ? vec3(0.026, 0.065, 0.046) : vec3(0.03, 0.095, 0.042);
                    vec3 on = vec3(p.x - (from + box.x + 0.012), c - mid - (uTrade > 1.5 && uTrade < 2.5 ? 0.13 : 0.0), p.z - (padTop + box.z + 0.002));
                    d = block(on, box - 0.006, 0.006);
                    if (d < nearest) { nearest = d; part = 25.0; }
                    if (uTrade < 1.5 || uTrade > 2.5) {
                        vec3 held = vec3(from - 0.035, box.y + 0.06, padTop - 0.085);
                        vec3 q = vec3(p.x, c - mid, p.z) - held;
                        d = min(block(q, vec3(0.012, 0.02, 0.034), 0.008), rod(q, vec3(0.0, 0.0, -0.04), vec3(0.03, -0.05, -0.15), 0.005));
                        if (d < nearest) { nearest = d; part = 26.0; }
                    }
                } else if (uTrade > 3.5) {
                    // A racing car's cage of steel tube, part 27: a bar up each screen
                    // pillar, one over the screen, one along the roof each side, a hoop
                    // behind the seats and a bar across each door. Part 28 is the wings of
                    // the driver's seat either side of the helmet.
                    vec3 high = vec3(uHouse.x - 0.03, wide.y - 0.075, uHouse.y - 0.035), hoop = vec3(uPad.y - 0.1, wide.y - 0.075, uHouse.y - 0.035);
                    d = min(rod(v, vec3(uScreen.x - 0.13, wide.x - 0.1, belt - 0.6), high, 0.02), rod(v, vec3(high.x, 0.0, high.z), high, 0.02));
                    d = min(d, min(rod(v, high, hoop, 0.02), rod(v, vec3(uPad.y - 0.12, wide.x - 0.09, belt - 0.7), hoop, 0.02)));
                    d = min(d, rod(v, vec3(uScreen.x - 0.16, wide.x - 0.085, belt - 0.34), vec3(uPad.y - 0.12, wide.x - 0.085, belt - 0.08), 0.018));
                    if (d < nearest) { nearest = d; part = 27.0; }
                    d = block(vec3(p.x + 0.1, abs(p.y - uWheel.y) - 0.19, p.z + 0.04), vec3(0.075, 0.006, 0.095), 0.022);
                    if (d < nearest) { nearest = d; part = 28.0; }
                }
                return arms(p, nearest, part);
            }

            // One figure of a lit display in a cell from 0 to 1 each way, made of seven bars. The half
            // is for a power of two worked out a hair too large, which would lose a bar's bit.
            float numeral(vec2 p, float n) {
                float lit = n < 0.5 ? 119.0 : n < 1.5 ? 36.0 : n < 2.5 ? 93.0 : n < 3.5 ? 109.0 : n < 4.5 ? 46.0 : n < 5.5 ? 107.0 : n < 6.5 ? 123.0 : n < 7.5 ? 37.0 : n < 8.5 ? 127.0 : 111.0;
                float seen = 0.0;
                for (int i = 0; i < 7; i++) {
                    // Top, upper left, upper right, middle, lower left, lower right, bottom.
                    vec2 a = i == 0 ? vec2(0.3, 0.88) : i == 1 ? vec2(0.24, 0.84) : i == 2 ? vec2(0.76, 0.84) : i == 3 ? vec2(0.3, 0.5) : i == 4 ? vec2(0.24, 0.46) : i == 5 ? vec2(0.76, 0.46) : vec2(0.3, 0.12);
                    vec2 b = i == 0 || i == 3 || i == 6 ? a + vec2(0.4, 0.0) : a - vec2(0.0, 0.3);
                    vec2 bar = b - a;
                    float off = length(p - a - bar * clamp(dot(p - a, bar) / dot(bar, bar), 0.0, 1.0));
                    seen = max(seen, step(off, 0.055) * mod(floor((lit + 0.5) / exp2(float(i))), 2.0));
                }
                return seen;
            }

            // Instruments as a lit panel: the road speed in figures, the engine's speed as a
            // row of bars over them and the fuel as a column at the side. p runs -1 to 1.
            vec3 litFace(vec2 p, out float glow) {
                glow = 0.0;
                vec3 ink = vec3(0.35, 1.0, 0.8);
                float shown = floor(clamp(uGauges.x, 0.0, 1.5) * 160.0 + 0.5);
                float cell = floor((p.x + 0.45) / 0.3);
                vec2 in2 = vec2(fract((p.x + 0.45) / 0.3), (p.y + 0.75) / 1.05);
                float lit = 0.0;
                if (cell > -0.5 && cell < 2.5 && in2.y > 0.0 && in2.y < 1.0) {
                    float place = cell < 0.5 ? floor(shown / 100.0) : cell < 1.5 ? mod(floor(shown / 10.0), 10.0) : mod(shown, 10.0);
                    // No noughts before the number.
                    lit = cell < 0.5 && shown < 100.0 || cell < 1.5 && shown < 10.0 ? 0.0 : numeral(in2, place);
                }
                if (p.y > 0.5 && p.y < 0.82 && abs(p.x) < 0.9) {
                    float bar = (p.x + 0.9) / 1.8;
                    float on = step(bar, clamp(uGauges.y, 0.0, 1.0)) * step(0.25, fract(bar * 24.0));
                    lit = max(lit, on);
                    ink = bar > 0.8 ? vec3(1.0, 0.25, 0.1) : ink;
                    lit = max(lit, 0.12 * step(0.25, fract(bar * 24.0)));
                }
                if (p.x > 0.68 && p.x < 0.88 && p.y < 0.35 && p.y > -0.8) {
                    float level = (p.y + 0.8) / 1.15;
                    lit = max(lit, max(step(level, clamp(uGauges.z, 0.0, 1.0)), 0.12) * step(0.3, fract(level * 8.0)));
                    ink = uGauges.z < 0.15 ? vec3(1.0, 0.5, 0.1) : ink;
                }
                glow = lit;
                return mix(vec3(0.012, 0.02, 0.02), ink, lit);
            }

            // Which way round a rail a place on it is: 1 where it faces the way given, into
            // the cabin, and -1 where it faces the glass it stands against.
            float inward(vec3 v, vec3 a, vec3 b, vec3 way) {
                vec3 piece = b - a;
                vec3 off = v - a - piece * clamp(dot(v - a, piece) / dot(piece, piece), 0.0, 1.0);
                return dot(off, way) / max(length(off), 1e-4);
            }

            // A row of stitches beside a seam: 1 on a stitch. off is how far from the seam
            // and run how far along it.
            float stitches(float off, float run) {
                return step(abs(abs(off) - 0.007), 0.0016) * step(0.3, fract(run / 0.013));
            }

            vec3 builtPaint(vec3 p, float part) {
                float mid = abs(uDash.x);
                float c = (p.y - uWheel.y) * (uDash.x < 0.0 ? -1.0 : 1.0);
                vec3 v = vec3(p.x, abs(c - mid), p.z);
                vec2 wide = widths();
                float belt = min(uScreen.y, uScreen.z);
                // The lining of the roof and its pillars is a pale cloth that takes a little
                // of the seats' colour. Where a rail meets its glass it is black rubber.
                vec3 lining = mix(vec3(0.62, 0.61, 0.58), uSeatColor, 0.22);
                vec3 rubber = vec3(0.045, 0.045, 0.05);
                vec3 top = vec3(uHouse.x, wide.y, uHouse.y);
                gWoven = 1.0;
                gGrain = 0.22;
                gGloss = 0.03;
                if (part < 12.5) {
                    float in2 = inward(v, vec3(uScreen.x - 0.02, wide.x - 0.005, belt - 0.03), top, normalize(vec3(-0.55, -0.8, -0.2)));
                    gGloss = in2 < -0.1 ? 0.3 : 0.03;
                    return in2 < -0.1 ? rubber : lining * (abs(in2 + 0.06) < 0.04 ? 0.6 : 0.94);
                } else if (part < 13.5) {
                    float in2 = inward(v, vec3(uHouse.x + 0.012, 0.0, uHouse.y + 0.014), top, normalize(vec3(-0.6, 0.0, -0.8)));
                    return in2 < -0.25 ? rubber : lining * 0.94;
                } else if (part < 14.5) {
                    return lining;
                } else if (part < 15.5) {
                    float in2 = inward(v, top, vec3(uPad.y - 1.6, wide.y, uHouse.y), normalize(vec3(0.0, -0.8, -0.6)));
                    return in2 < -0.2 ? rubber : lining * 0.94;
                } else if (part < 16.5) {
                    float in2 = inward(v, vec3(uPad.y, wide.x - 0.005, belt - 0.03), vec3(uPad.y - 0.04, wide.y, uHouse.y), vec3(0.0, -1.0, 0.0));
                    return in2 < -0.15 ? rubber : lining * 0.94;
                } else if (part < 17.5) {
                    // A visor in the lining's cloth with a bound edge.
                    float deep = min(0.085, (uHouse.x - 0.1) * 0.5);
                    vec2 on = abs(vec2(p.x - (uHouse.x - 0.04 - deep), v.y - mid)) - vec2(deep, min(mid - 0.07, 0.2) + 0.009);
                    return lining * (max(on.x, on.y) > -0.012 ? 0.8 : 1.03);
                } else if (part < 18.5) {
                    gWoven = 0.0;
                    gGrain = 0.0;
                    gGloss = 0.35;
                    return lining * 0.72;
                }
                gWoven = 0.0;
                gGrain = 0.75;
                gGloss = 0.32;
                if (part < 19.5) {
                    // Padded and grained, with a sewn seam along the roll before the driver.
                    float from = padFrom();
                    float seam = p.x - from - 0.04;
                    vec3 pad = uDashColor * 0.97;
                    pad = mix(pad, uDashColor * 0.45, step(abs(seam), 0.0022));
                    return mix(pad, uDashColor * 1.25 + 0.06, stitches(seam, c));
                } else if (part < 20.5) {
                    float seam = v.y - wide.x + 0.004;
                    vec3 roll = uDashColor * 0.86;
                    roll = mix(roll, uDashColor * 0.4, step(abs(seam), 0.002) * step(uScreen.z - 0.012, p.z));
                    return mix(roll, uDashColor * 1.2 + 0.05, stitches(seam, p.x) * step(uScreen.z - 0.012, p.z));
                } else if (part < 21.5) {
                    // The armrest: a padded top, and before the driver's hand the switches of the windows.
                    vec3 rest = uDashColor * 0.8;
                    vec2 on = vec2(p.x - 0.27, v.y - (wide.x - 0.047));
                    if (c < mid && p.z > uScreen.z - 0.245 && abs(on.x) < 0.058 && abs(on.y) < 0.022) {
                        gGrain = 0.0;
                        gGloss = 0.55;
                        vec2 key = abs(fract(vec2(on.x / 0.058, on.y / 0.022) * 0.5 + 0.5) - 0.5);
                        rest = max(key.x, key.y) < 0.36 ? vec3(0.16, 0.16, 0.17) : vec3(0.03, 0.03, 0.034);
                    }
                    return rest;
                }
                if (part > 22.5) {
                    int hand = distance(p, gGrip[0]) < distance(p, gGrip[1]) ? 0 : 1;
                    vec3 along = gAlong[hand], grip = gGrip[hand], wrist = gWrist[hand];
                    if (part < 23.5) {
                        // Skin, darker in the creases between the fingers and paler over the knuckles.
                        gGrain = 0.2;
                        gGloss = 0.12;
                        vec3 g = p - grip;
                        float round2 = length(g - along * dot(g, along));
                        float crease = round2 < uKit.y + 0.02 ? 0.5 + 0.5 * cos(dot(g, along) * 314.16) : 1.0;
                        // The skin of a bare arm is the arm's own, whatever is on the hand.
                        vec3 skin = round2 > 0.1 && uSleeve.w < 0.75 ? uBare : uSkin;
                        return skin * (0.78 + 0.22 * crease) * vec3(1.0, 0.97 + 0.03 * crease, 0.95 + 0.05 * crease);
                    }
                    // A sleeve of plain cloth with a turned cuff at the wrist, or at the
                    // elbow where it is a short one.
                    gWoven = 1.0;
                    gGrain = 0.6;
                    gGloss = 0.03;
                    float up = uSleeve.w > 0.75 ? distance(p, wrist) : distance(p, gElbow[hand]) + 0.03;
                    return uSleeve.rgb * (up < 0.075 ? (up > 0.068 ? 0.6 : 1.12) : 1.0);
                }
                gGrain = 0.0;
                gGloss = 0.5;
                if (part > 27.5) {
                    gWoven = 1.0;
                    gGrain = 0.6;
                    gGloss = 0.03;
                    return uSeatColor * 0.9;
                } else if (part > 26.5) {
                    // Painted tube, with a sleeve of black foam where a head might meet it.
                    bool padded = p.z > uHouse.y - 0.08 && p.x < uHouse.x - 0.1 && p.x > uPad.y;
                    gGloss = padded ? 0.05 : 0.75;
                    return padded ? vec3(0.05, 0.05, 0.055) : vec3(0.72, 0.73, 0.75);
                } else if (part > 25.5) {
                    return vec3(0.05, 0.05, 0.055);
                } else if (part > 24.5) {
                    float from = padFrom();
                    float padTop = max(uDash.y, uScreen.y) + 0.004;
                    vec3 box = uTrade > 2.5 ? vec3(0.036, 0.15, 0.046) : uTrade > 1.5 ? vec3(0.026, 0.065, 0.046) : vec3(0.03, 0.095, 0.042);
                    vec2 on = vec2(c - mid - (uTrade > 1.5 && uTrade < 2.5 ? 0.13 : 0.0), p.z - (padTop + box.z + 0.002)) / box.yz;
                    vec3 shell = uTrade > 2.5 ? vec3(0.62, 0.63, 0.62) : vec3(0.06, 0.06, 0.065);
                    if (p.x > from + 0.016 || abs(on.x) > 0.9 || abs(on.y) > 0.82) {
                        return shell;
                    }
                    if (uTrade > 1.5 && uTrade < 2.5) {
                        // The fare in lit figures, and the flag's lamp beside it.
                        float cell = floor((on.x + 0.8) / 0.4);
                        vec2 in2 = vec2(fract((on.x + 0.8) / 0.4), on.y * 0.6 + 0.5);
                        float figure = cell > -0.5 && cell < 3.5 ? numeral(in2, cell < 0.5 ? 0.0 : cell < 1.5 ? 3.0 : cell < 2.5 ? 4.0 : 5.0) : 0.0;
                        gGlow = figure;
                        return mix(vec3(0.03, 0.015, 0.015), vec3(1.0, 0.16, 0.08), figure);
                    }
                    // A row of switches under a row of lamps: red and blue for the lights
                    // on the roof, amber for the rest.
                    float key = floor((on.x + 0.9) / 0.3);
                    vec2 in2 = vec2(fract((on.x + 0.9) / 0.3) - 0.5, on.y);
                    if (in2.y > 0.25) {
                        float lamp = step(length(vec2(in2.x * 0.3 * box.y, (in2.y - 0.5) * box.z)), 0.0055);
                        vec3 lens = key < 1.5 ? vec3(0.9, 0.1, 0.08) : key < 3.5 ? vec3(0.15, 0.3, 1.0) : vec3(0.95, 0.6, 0.1);
                        return mix(shell * 0.6, lens * 0.5, lamp);
                    }
                    float toggle = step(abs(in2.x), 0.16) * step(abs(in2.y + 0.25), 0.4);
                    gGloss = toggle > 0.5 ? 0.9 : 0.4;
                    return mix(shell * 0.6, vec3(0.6, 0.6, 0.62), toggle);
                }
                return vec3(0.07, 0.07, 0.075);
            }

            """;

    /**
     * The light in a cabin by day. It comes in through the glass: the sun past the roof, the
     * pillars and the doors, and the sky through each opening, most on what faces it and is
     * near it. Places and directions are along the vehicle, across it towards its middle and up.
     */
    static final String LIGHT = """
            // How near a line of light from o along d passes to the piece from a to b.
            float clears(vec3 o, vec3 d, vec3 a, vec3 b) {
                vec3 piece = b - a, from = o - a;
                float lean = dot(d, piece), long2 = dot(piece, piece);
                float flat2 = long2 - lean * lean;
                float s = flat2 > 1e-6 ? max((lean * dot(piece, from) - long2 * dot(d, from)) / flat2, 0.0) : 0.0;
                float t = clamp((lean * s + dot(piece, from)) / long2, 0.0, 1.0);
                vec3 on = a + piece * t;
                return length(o + d * max(dot(on - o, d), 0.0) - on);
            }

            // How much of the sun reaches a place in the cabin, the sun being that way.
            float sunInside(vec3 o, vec3 d) {
                if (d.z < 0.02) {
                    return 0.0;
                }
                float mid = abs(uDash.x);
                vec2 wide = widths();
                float belt = min(uScreen.y, uScreen.z);
                float t = (uHouse.y - o.z) / d.z;
                vec3 at = o + d * max(t, 0.0);
                // Under the roof, which begins at the rail over the screen and ends at the rails over the doors.
                float lit = 1.0 - (1.0 - smoothstep(-0.05, 0.05, at.x - uHouse.x)) * (1.0 - smoothstep(-0.05, 0.05, abs(at.y - mid) - wide.y));
                if (abs(d.y) > 0.001) {
                    // Through a door below its sill there is no way.
                    float reach = ((d.y > 0.0 ? mid + wide.x : mid - wide.x) - o.y) / d.y;
                    float high = o.z + d.z * reach;
                    if (reach > 0.0 && high < uHouse.y) {
                        lit *= smoothstep(-0.03, 0.05, high - uScreen.z);
                    }
                }
                if (d.x > 0.001) {
                    // Nor under the foot of the screen.
                    float reach = (uScreen.x + 0.08 - o.x) / d.x;
                    if (reach > 0.0) {
                        lit *= smoothstep(-0.07, 0.0, o.z + d.z * reach - max(uDash.y, uScreen.y));
                    }
                }
                for (int i = 0; i < 2; i++) {
                    float way = i == 0 ? -1.0 : 1.0;
                    vec3 foot = vec3(uScreen.x - 0.02, mid + way * (wide.x - 0.005), belt - 0.03), top = vec3(uHouse.x, mid + way * wide.y, uHouse.y);
                    lit *= smoothstep(uHouse.z * 0.7, uHouse.z * 1.5, clears(o, d, foot, top));
                    lit *= smoothstep(uHouse.z * 0.8, uHouse.z * 1.7, clears(o, d, vec3(uPad.y, mid + way * (wide.x - 0.005), belt - 0.03), vec3(uPad.y - 0.04, mid + way * wide.y, uHouse.y)));
                }
                return lit;
            }

            // The colour of the sky's light from a direction in the cabin, by day 1.
            vec3 skyFrom(float up) {
                return mix(vec3(0.42, 0.40, 0.36), vec3(0.72, 0.80, 0.95), 0.5 + 0.5 * up);
            }

            // The sky's light on a place in the cabin facing m, through the screen, the two
            // front windows and the back of the glasshouse.
            vec3 skyInside(vec3 o, vec3 m) {
                float mid = abs(uDash.x);
                vec2 wide = widths();
                float belt = min(uScreen.y, uScreen.z);
                // Below the sills a place sees less of every opening the deeper it lies.
                float sunk = clamp(1.0 - (belt - o.z) * 1.0, 0.22, 1.0);
                float forward = clamp(0.5 + 0.5 * (o.x + 0.3) / (uScreen.x + 0.3), 0.3, 1.0);
                float driver = clamp(1.0 - (o.y - (mid - wide.x)) / (2.0 * wide.x), 0.0, 1.0);
                vec3 screen = normalize(vec3(0.72, 0.0, 0.7));
                vec3 light = skyFrom(0.8) * (pow(clamp(0.5 + 0.5 * dot(m, screen), 0.0, 1.0), 1.6) * 1.25 * forward);
                light += skyFrom(0.35) * (pow(clamp(0.5 + 0.5 * dot(m, normalize(vec3(0.1, -1.0, 0.4))), 0.0, 1.0), 1.6) * 0.75 * mix(0.35, 1.0, driver));
                light += skyFrom(0.35) * (pow(clamp(0.5 + 0.5 * dot(m, normalize(vec3(0.1, 1.0, 0.4))), 0.0, 1.0), 1.6) * 0.75 * mix(1.0, 0.35, driver));
                light += skyFrom(0.4) * (pow(clamp(0.5 + 0.5 * dot(m, normalize(vec3(-1.0, 0.0, 0.45))), 0.0, 1.0), 1.6) * 0.3);
                return light * sunk;
            }

            // A place in the cabin lit: base is what it is made of, gloss how much of a mirror
            // it is, day how bright the sky is, sunOut whether the sun reaches the vehicle at
            // all, and shade how much the things round the place keep off it.
            vec3 cabinLit(vec3 p, vec3 faces, vec3 view, vec3 base, float gloss, float day, float sunOut, float shade, vec3 sunLight, vec3 sunWay) {
                float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                vec3 o = vec3(p.x, (p.y - uWheel.y) * toward, p.z);
                vec3 m = vec3(faces.x, faces.y * toward, faces.z);
                vec3 v = vec3(view.x, view.y * toward, view.z);
                vec3 d = vec3(sunWay.x, sunWay.y * toward, sunWay.z);
                // The darkest trim is still leather or plastic, and throws a little light back.
                vec3 stuff = base * 0.92 + 0.014;
                float sunIn = sunInside(o + m * 0.02, d) * sunOut;
                vec3 sky = skyInside(o, m) * day;
                // What the sun lays on the dashboard and the seats comes back off them, warm, onto the rest.
                vec3 thrown = (sunLight * (0.3 * sunOut * max(d.z, 0.0)) + skyFrom(0.5) * (0.22 * day)) * (0.6 + 0.4 * pow(uDashColor, vec3(2.2)) / max(dot(pow(uDashColor, vec3(2.2)), vec3(0.34)), 0.01))
                        * (0.75 - 0.25 * m.z);
                vec3 color = stuff * ((sky * 1.15 + thrown) * shade + sunLight * (1.9 * sunIn * max(dot(m, d), 0.0)));
                // Leather and hard trim show the windows as a sheen where they turn towards them.
                vec3 back = reflect(-v, m);
                float glancing = 0.04 + 0.96 * pow(clamp(1.0 - dot(m, v), 0.0, 1.0), 5.0);
                float window = smoothstep(-0.25, 0.3, back.z) * (1.0 - 0.75 * smoothstep(0.7, 0.95, back.z));
                color += skyFrom(back.z) * (day * gloss * glancing * window * shade * 1.6);
                vec3 half2 = normalize(d + v);
                color += sunLight * (pow(max(dot(m, half2), 0.0), mix(10.0, 120.0, gloss)) * gloss * (0.25 + gloss) * sunIn);
                return color;
            }

            """;

    /**
     * The glass of a built cabin, where a line of sight from the seat leaves by it, with the
     * lamp in the roof and how far along a line of sight anything built can be.
     */
    static final String GLASS = """
            // Where the lamp in the roof is: in the lining between the visors and the heads
            // of those in front, on the vehicle's middle line.
            vec3 domeAt() {
                return vec3(uHouse.x - 0.36, uWheel.y + uDash.x, liningAt(0.0) - 0.012);
            }

            // What the lamp in the roof gives a place in the cabin that faces m: a warm
            // light from overhead that thins with the square of the distance.
            vec3 domeLit(vec3 o, vec3 m) {
                vec3 to = domeAt() - o;
                float far2 = dot(to, to);
                float facing = max(dot(m, to * inversesqrt(max(far2, 1e-4))), 0.0);
                return vec3(1.0, 0.86, 0.62) * (uDome * 0.6 * (0.2 + 0.8 * facing) / (0.05 + far2));
            }

            // The lamp itself: a lens let into the lining, bright when it is lit.
            vec3 domeLamp(vec3 o, float part) {
                vec3 from = o - domeAt();
                if (part < 13.5 || part > 14.5 || abs(from.x) > 0.045 || abs(from.y) > 0.03) {
                    return vec3(0.0);
                }
                return vec3(1.0, 0.9, 0.7) * (uDome * 2.4 * (0.75 + 0.25 * cos(from.x * 400.0)));
            }

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

            // How far along a line of sight from o anything the picture builds can be: the
            // cabin is a box, and through the open of the windscreen nothing is past the glass.
            float cabinReach(vec3 o, vec3 d) {
                float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                float mid = abs(uDash.x);
                vec3 from = vec3(o.x, (o.y - uWheel.y) * toward - mid, o.z);
                vec3 way = vec3(d.x, d.y * toward, d.z);
                float out2 = widths().x + 0.32;
                vec3 low = vec3(uPad.y - 1.75, -out2, uWheel.z - 0.85), high = vec3(uScreen.x + 0.32, out2, uHouse.y + 0.1);
                vec3 slow = 1.0 / (abs(way) + 1e-5);
                vec3 leaves = (mix(low, high, step(0.0, way)) - from) * sign(way + 1e-6) * slow;
                float reach = max(min(leaves.x, min(leaves.y, leaves.z)), 0.0);
                float padTop = max(uDash.y, uScreen.y) + 0.004;
                vec3 foot = vec3(uScreen.x, 0.0, padTop - 0.02), rail = vec3(uHouse.x, 0.0, uHouse.y);
                float span = length(rail - foot);
                vec3 up = (rail - foot) / span;
                vec3 face = vec3(-up.z, 0.0, up.x);
                float leaving = dot(way, face);
                if (leaving < -0.001) {
                    float through = dot(foot - vec3(from.x, 0.0, from.z), face) / leaving;
                    vec3 hit = from + way * through;
                    float along = dot(vec3(hit.x, 0.0, hit.z) - foot, up);
                    if (along > 0.08 && along < span && abs(hit.y) < widths().x) {
                        reach = min(reach, through + 0.08);
                    }
                }
                return reach;
            }

            // The windscreen is a pane of its own, from the pad over the dashboard up to the
            // rail: the wipers sweep it from its foot, a band of tint runs under the rail,
            // and by day it shows the top of the dashboard lying under it, most where the
            // sun is on the pad and the pad is pale. The other panes are told by which way
            // the line of sight goes.
            // How far a line of sight from o going d, both with across counted towards the
            // vehicle's middle, goes to the glass it leaves by if not the windscreen, and 1 where
            // that is a side pane: a pane at the side stands just outside the roll of its door's
            // top, and the back of the glasshouse is further off than anything built.
            vec2 paneOut(vec3 o, vec3 d) {
                float mid = abs(uDash.x);
                float out2 = d.y > 0.0 ? mid + widths().x + 0.03 - o.y : o.y - mid + widths().x + 0.03;
                float side = out2 / max(abs(d.y), 0.001);
                float end = d.x < 0.0 ? (o.x - uPad.y + 1.9) / max(-d.x, 0.001) : 9.0;
                return vec2(min(side, end), side <= end ? 1.0 : 0.0);
            }

            vec4 paneBuilt(vec3 out3) {
                float toward = uDash.x < 0.0 ? -1.0 : 1.0;
                vec3 d = inCabin(out3) * vec3(1.0, toward, 1.0);
                vec3 o = vec3(uSeat.x, (uSeat.y - uWheel.y) * toward, uSeat.z);
                float mid = abs(uDash.x);
                gPaneFar = paneOut(o, d).x;
                float padTop = max(uDash.y, uScreen.y) + 0.004;
                vec3 foot = vec3(uScreen.x, 0.0, padTop - 0.02), rail = vec3(uHouse.x, 0.0, uHouse.y);
                float span = length(rail - foot);
                vec3 up = (rail - foot) / span;
                vec3 face = vec3(-up.z, 0.0, up.x);
                float leaving = dot(d, face);
                if (leaving > -0.001) {
                    return paneSeen(out3);
                }
                float reach = dot(foot - o, face) / leaving;
                vec3 hit = o + d * reach;
                float high = dot(hit - foot, up);
                if (high > span + 0.03 || abs(hit.y - mid) > widths().x + 0.05) {
                    return paneSeen(out3);
                }
                gPaneFar = reach;
                gScreenHalf = widths().x * CABIN;
                vec4 glass = pane(vec2((hit.y - mid) * CABIN, high * CABIN - 0.3), fromCabin(vec3(face.x, 0.0, face.z)), -out3, 1.0);
                float day = clamp(dot(atmosphere(vec3(0.0, 0.0, 1.0)), vec3(1.4)) + 0.05, 0.0, 1.0);
                vec3 thrown = reflect(d, face);
                vec3 land = hit + thrown * ((padTop - hit.z) / min(thrown.z, -0.01));
                if (land.x > padFrom() - 0.03 && land.x < uScreen.x + 0.1) {
                    vec3 sunWay = inCabin(uLight) * vec3(1.0, toward, 1.0);
                    vec3 pad = (pow(uDashColor, vec3(2.2)) * 0.92 + 0.014)
                            * (skyFrom(0.8) * (1.2 * day) + pow(uLightColor, vec3(2.2)) * (1.9 * sunInside(land + vec3(0.0, 0.0, 0.03), sunWay) * max(sunWay.z, 0.0)));
                    float veil = 0.7 * (0.04 + 0.96 * pow(clamp(1.0 + leaving, 0.0, 1.0), 5.0));
                    glass = vec4((glass.rgb * glass.a + pad * veil) / (glass.a + veil), min(glass.a + veil, 0.9));
                }
                float band = 1.0 - smoothstep(0.07, 0.24, span - high);
                return vec4(mix(glass.rgb, vec3(0.012, 0.05, 0.055) * day, band), mix(glass.a, 0.66, band));
            }

            """;
}
