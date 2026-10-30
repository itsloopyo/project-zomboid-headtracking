package com.cameraunlock.zomboid;

/**
 * Grass grown plant by plant. A lawn is not one thing repeated: it is fine turf with coarse
 * broad-bladed grass through it, tussocks that arch over from one root, stalks gone to seed
 * with a spike or a nodding panicle on top, dead straw lying among the green, and clover in
 * patches. Which of them grows where is told by slow patterns over the map, so one yard is
 * mostly turf, the next is rough and seedy, and a drift of clover runs across both.
 *
 * Every blade is its own strip of triangles, bent along its length, and every plant's place
 * and shape come from its place on the map, so the same plants stand there whenever the
 * square is built. Far from the eye there are fewer plants and each is broader, so a lawn
 * covers its ground at any distance without a blade finer than the picture can draw.
 */
final class Grass {
    /** What the picture draws a strip as. The world shader has the same numbers. */
    static final int BLADE = 0, BROAD = 1, STRAW = 2, STEM = 3, SPIKE = 4, PANICLE = 5, CLOVER = 6, TUFT = 7;
    /** Added to the form of a crop's strip, which takes its colour from a texel of the crop's sprite. The world shader has the same number. */
    static final int CROP = 8;
    /** A flower head: a crop's clover is drawn as a round flower in its texel's colour (Flowerbeds). The world shader has the same number. */
    static final int BLOOM = CLOVER + CROP;

    /** How well grass does at a point of the square being grown, 0 where it barely grows and 1 on open lawn. */
    interface Ground {
        float vigour(float a, float b);

        /** Whether anything grows at the point (a, b) of the square. Nothing does on a part of it that is street. */
        default boolean grows(float a, float b) {
            return true;
        }
    }

    /** Places a plant can stand on a square of lawn, along each side, and on a square of long grass. */
    private static final int LAWN_SITES = 11, RANK_SITES = 7;
    /** Within this many tiles of the eye every plant is grown, and past it fewer and broader ones. */
    private static final float FULL_WITHIN = 4f;
    /** A lawn's grass is shorter from the first of these distances and gone by the second: past it the ground's own picture is the lawn. */
    static final float LAWN_SHORTENS = 8f, LAWN_ENDS = 15.5f;
    /**
     * Seen from overhead as the game's own view, a lawn is grown to the edge of the picture with
     * at least this share of its plants, full height, however far it is from the eye. There every
     * square is drawn the same size, and a lawn that ended where it does in first person was a
     * ring round the character. Past about ten tiles the share is this whatever the distance, so
     * a chunk out there grows the same whenever it is made.
     */
    private static final float OVERHEAD_KEPT = 0.25f;
    /** Long grass is grown plant by plant as far as the second of these, its plants sinking away from the first: past that it is cards. */
    static final float RANK_THINS = 14f, RANK_ENDS = 24f;
    /** No blade is drawn broader than this many times its own width, however few are left. */
    private static final float BROADEST = 2.8f;
    /** The patterns run over this many tiles from one of their values to the next. */
    private static final int FIELD_SPAN = 5;

    private static final int SINES = 1024;
    private static final float[] SINE = new float[SINES];
    private static final float PER_TURN = SINES / (float) (2.0 * Math.PI);

    static {
        for (int i = 0; i < SINES; i++) {
            SINE[i] = (float) Math.sin(i * 2.0 * Math.PI / SINES);
        }
    }

    private Grass() {
    }

    /** The sine of an angle to a third of a degree, which is finer than any blade is bent to. */
    static float sin(float angle) {
        return SINE[Math.round(angle * PER_TURN) & SINES - 1];
    }

    static float cos(float angle) {
        return SINE[Math.round(angle * PER_TURN) + SINES / 4 & SINES - 1];
    }

    /** A slow pattern over the map, 0 to 1, the same wherever it is asked. */
    static float field(int x, int y, int salt) {
        int cellX = Math.floorDiv(x, FIELD_SPAN), cellY = Math.floorDiv(y, FIELD_SPAN);
        float partX = (x - cellX * FIELD_SPAN + 0.5f) / FIELD_SPAN, partY = (y - cellY * FIELD_SPAN + 0.5f) / FIELD_SPAN;
        partX = partX * partX * (3f - 2f * partX);
        partY = partY * partY * (3f - 2f * partY);
        float low = Flora.unit(Flora.mix(cellX, cellY, salt), 8) * (1f - partX) + Flora.unit(Flora.mix(cellX + 1, cellY, salt), 8) * partX;
        float high = Flora.unit(Flora.mix(cellX, cellY + 1, salt), 8) * (1f - partX) + Flora.unit(Flora.mix(cellX + 1, cellY + 1, salt), 8) * partX;
        return low * (1f - partY) + high * partY;
    }

    /**
     * The share of its plants grass grows, from the GrassDensity row's percentage. Where players
     * can fight it grows them all: grass that hides a player from the stock game's view hides
     * them from the mod's too.
     */
    static float density(int percent, boolean contested) {
        return contested ? 1f : percent / 100f;
    }

    /** What share of its plants a place this far from the eye, in tiles squared, still has. */
    static float kept(float away2) {
        if (away2 <= FULL_WITHIN * FULL_WITHIN) {
            return 1f;
        }
        // As the square of the distance falls, a little slower: the plants left are broader.
        float share = FULL_WITHIN * FULL_WITHIN / away2;
        return (float) Math.sqrt(share * Math.sqrt(share));
    }

    private static float smooth(float from, float to, float value) {
        float t = Math.clamp((value - from) / (to - from), 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    /**
     * Grows the lawn of the square (x, y) into mesh, between lowA and highA across it and lowB
     * and highB down it. The eye is at (eyeX, eyeY) and looks along (lookX, lookY): what is well
     * behind it is not grown, since nothing of it is in the picture. density is the share of its
     * plants grown (see density): the plants left are broader, as they are far off. overhead: the
     * frame is the game's own view from overhead (see OVERHEAD_KEPT).
     */
    static void lawn(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int x, int y, float eyeX, float eyeY,
            float lookX, float lookY, float lowA, float highA, float lowB, float highB, Ground ground, float density, boolean overhead) {
        float toX = x + 0.5f - eyeX, toY = y + 0.5f - eyeY;
        float away2 = toX * toX + toY * toY;
        if (!overhead && (away2 > LAWN_ENDS * LAWN_ENDS || behind(toX, toY, away2, lookX, lookY, 0.07f))) {
            return;
        }
        float away = (float) Math.sqrt(away2);
        float coarse = field(x, y, 211), seedy = field(x, y, 223), clovered = field(x, y, 227), mown = field(x, y, 229);
        // The same numbers at the square's four corners, so that a lawn does not change at the lines between squares.
        float kept00 = keptAt(x - eyeX, y - eyeY, overhead), kept10 = keptAt(x + 1 - eyeX, y - eyeY, overhead);
        float kept01 = keptAt(x - eyeX, y + 1 - eyeY, overhead), kept11 = keptAt(x + 1 - eyeX, y + 1 - eyeY, overhead);
        int steps = away < 3.6f ? 3 : away < 7f ? 2 : 1;
        float shortens = overhead ? 0f : 1f;
        for (int i = 0; i < LAWN_SITES; i++) {
            for (int j = 0; j < LAWN_SITES; j++) {
                int hash = Flora.mix(x * LAWN_SITES + i, y * LAWN_SITES + j, 7);
                float a = (i + Flora.unit(hash, 8)) / LAWN_SITES, b = (j + Flora.unit(hash, 16)) / LAWN_SITES;
                float kept = ((kept00 * (1f - a) + kept10 * a) * (1f - b) + (kept01 * (1f - a) + kept11 * a) * b) * density;
                float rank = Flora.unit(hash, 0);
                if (rank >= kept || a < lowA || a > highA || b < lowB || b > highB || !ground.grows(a, b)) {
                    continue;
                }
                float vigour = ground.vigour(a, b);
                if (Flora.unit(hash, 24) > 0.3f + 0.7f * vigour) {
                    continue;
                }
                float plantX = x + a - eyeX, plantY = y + b - eyeY;
                float far = (float) Math.sqrt(plantX * plantX + plantY * plantY);
                // A plant about to be left out as the eye draws away sinks into the ground first, and comes up out of it.
                float grown = Math.min((kept - rank) / (0.25f * kept), 1f) * (1f - shortens * smooth(LAWN_SHORTENS, LAWN_ENDS, far));
                if (grown < 0.05f) {
                    continue;
                }
                float broadens = Math.min(1f / (float) Math.sqrt(kept), BROADEST);
                int more = Flora.mix(hash, i, 3);
                geometry.plant(frame, x, y, a, b);
                float pick = Flora.unit(more, 0);
                float size = grown * (0.55f + 0.45f * vigour) * (0.75f + 0.5f * mown);
                // Clover where it has taken hold, low among the grass.
                if (clovered > 0.6f && pick < 0.3f * smooth(0.6f, 0.75f, clovered)) {
                    clover(geometry, mesh, frame, more, grown, broadens, vigour);
                    continue;
                }
                pick = Flora.unit(more, 8);
                if (vigour > 0.45f && pick < 0.006f + 0.07f * smooth(0.5f, 0.9f, seedy)) {
                    stalk(geometry, mesh, frame, more, (0.24f + 0.26f * Flora.unit(more, 16)) * size, broadens, steps, vigour);
                } else if (pick < 0.02f + 0.03f * coarse) {
                    tussock(geometry, mesh, frame, more, (0.2f + 0.2f * Flora.unit(more, 16)) * size, broadens, steps, vigour, 3);
                } else if (pick < 0.07f + 0.36f * smooth(0.35f, 0.85f, coarse)) {
                    broad(geometry, mesh, frame, more, size, broadens, steps, vigour);
                } else if (pick > 0.95f - 0.3f * (1f - vigour)) {
                    straw(geometry, mesh, frame, more, size, broadens, steps, vigour);
                } else {
                    // Turf grows in tufts a hand across: taller in the middle of one, and leaning out of it.
                    turf(geometry, mesh, frame, more, x + a, y + b, size, broadens, steps, vigour);
                }
            }
        }
        mesh.face(0f, 0f, 1f);
    }

    /**
     * Grows the long grass of the square (x, y): knee to waist high, mostly tussocks and stalks
     * in seed, fewer and broader with distance and gone by RANK_ENDS. density as for lawn.
     */
    static void rank(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int x, int y, float eyeX, float eyeY,
            float lookX, float lookY, float lowA, float highA, float lowB, float highB, Ground ground, float density) {
        float toX = x + 0.5f - eyeX, toY = y + 0.5f - eyeY;
        float away2 = toX * toX + toY * toY;
        if (behind(toX, toY, away2, lookX, lookY, 0.2f)) {
            return;
        }
        float away = (float) Math.sqrt(away2);
        float lasts = 1f - smooth(RANK_THINS, RANK_ENDS, away);
        float kept = kept(away2 * 0.8f) * density;
        float broadens = Math.min(1f / (float) Math.sqrt(kept), BROADEST);
        float seedy = field(x, y, 223);
        int steps = away < 4.5f ? 3 : away < 9f ? 2 : 1;
        for (int i = 0; i < RANK_SITES; i++) {
            for (int j = 0; j < RANK_SITES; j++) {
                int hash = Flora.mix(x * RANK_SITES + i, y * RANK_SITES + j, 11);
                float rank = Flora.unit(hash, 0);
                if (rank >= kept) {
                    continue;
                }
                float a = lowA + (highA - lowA) * (i + Flora.unit(hash, 8)) / RANK_SITES, b = lowB + (highB - lowB) * (j + Flora.unit(hash, 16)) / RANK_SITES;
                // Past the square's edge the clump is ragged: shorter the further out a plant stands.
                float out = Math.max(Math.max(-a, a - 1f), Math.max(-b, b - 1f));
                if (!ground.grows(Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f))) {
                    continue;
                }
                float vigour = ground.vigour(Math.clamp(a, 0f, 1f), Math.clamp(b, 0f, 1f));
                float size = Math.min((kept - rank) / (0.25f * kept), 1f) * (out > 0f ? 1f - 1.8f * out : 1f) * (0.6f + 0.4f * vigour) * lasts;
                if (size < 0.05f) {
                    continue;
                }
                int more = Flora.mix(hash, j, 5);
                geometry.plant(frame, x, y, a, b);
                float pick = Flora.unit(more, 8);
                float tall = 0.42f + 0.5f * Flora.unit(more, 16);
                if (pick < 0.14f + 0.2f * seedy) {
                    stalk(geometry, mesh, frame, more, (tall + 0.15f) * size, broadens, steps, vigour);
                } else if (pick > 0.88f) {
                    straw(geometry, mesh, frame, more, 2.6f * size, broadens, steps, vigour);
                } else {
                    tussock(geometry, mesh, frame, more, tall * size, broadens, steps, vigour, pick < 0.5f ? 3 : 1);
                }
            }
        }
        mesh.face(0f, 0f, 1f);
    }

    private static float keptAt(float toX, float toY, boolean overhead) {
        float kept = kept(toX * toX + toY * toY);
        return overhead ? Math.max(kept, OVERHEAD_KEPT) : kept;
    }

    /**
     * Whether a square that far from the eye is behind it, far enough round that none of it is
     * in the picture: further round than the angle whose cosine squared is round. A chunk far
     * off is built once in several frames, and the eye may have turned since.
     */
    private static boolean behind(float toX, float toY, float away2, float lookX, float lookY, float round) {
        if (away2 < 3f * 3f) {
            return false;
        }
        // Looking down at its feet the eye has no way it looks along the ground, and nothing is behind it.
        float along = toX * lookX + toY * lookY;
        return along < 0f && along * along > round * away2;
    }

    private static void turf(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float worldX, float worldY,
            float size, float broadens, int steps, float vigour) {
        // The tuft the blade belongs to: the nearest of a scatter of them, one to each third of a tile.
        float tuftX = worldX * 3f, tuftY = worldY * 3f;
        int cellX = (int) Math.floor(tuftX), cellY = (int) Math.floor(tuftY);
        int tuft = Flora.mix(cellX, cellY, 17);
        float outX = tuftX - cellX - (0.25f + 0.5f * Flora.unit(tuft, 0)), outY = tuftY - cellY - (0.25f + 0.5f * Flora.unit(tuft, 8));
        float strong = Flora.unit(tuft, 16);
        float near = Math.max(1f - 2.2f * (float) Math.sqrt(outX * outX + outY * outY), 0f);
        float turn = Flora.unit(more, 24) * 6.2831853f;
        // Out of the tuft's middle, and each blade a little its own way.
        float dirX = cos(turn) + 5f * strong * outX, dirY = sin(turn) + 5f * strong * outY;
        float length = (float) Math.sqrt(dirX * dirX + dirY * dirY) + 1e-4f;
        float tall = Flora.unit(more, 16);
        float high = (0.08f + 0.11f * tall + 0.12f * tall * tall * tall) * size * (1f + 0.55f * strong * near);
        // Most of a tuft stands. A third of it sprawls, and that is what hides the ground between the tufts.
        float sprawl = Flora.unit(more, 0);
        float lean = 0.05f + 1.15f * sprawl * sprawl * (1f - 0.6f * near), curl = 0.2f + 0.8f * Flora.unit(more, 4) * (0.5f + 0.5f * tall) * (1f - 0.6f * sprawl);
        // The bulk of it is a fan of blades from one root, cut out of one strip by the picture:
        // half a dozen blades for the corners of one.
        geometry.addStrip(mesh, frame, dirX / length, dirY / length, 0f, 0f, lean, curl, high, (0.07f + high * (0.5f + 0.5f * Flora.unit(more, 12))) * broadens,
                0f, steps, SpriteGeometry.FAN, TUFT, Flora.unit(more, 20), vigour);
        if (steps > 2 && (more & 0x300) == 0) {
            // And here and there a blade of its own, longer than the fan and standing clear of it.
            geometry.addStrip(mesh, frame, -dirY / length, dirX / length, 0f, 0f, 0.15f + 0.5f * Flora.unit(more, 4), 0.4f + 0.9f * Flora.unit(more, 0),
                    high * (1.1f + 0.5f * Flora.unit(more, 12)), (0.012f + 0.008f * Flora.unit(more, 16)) * broadens, 0.3f, steps, SpriteGeometry.POINTED, BLADE,
                    Flora.unit(more, 14), vigour);
        }
    }

    /** A blade of coarse grass: twice the width of turf, and arched right over. */
    private static void broad(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float size, float broadens,
            int steps, float vigour) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        geometry.addStrip(mesh, frame, cos(turn), sin(turn), 0f, 0f, 0.35f + 0.5f * Flora.unit(more, 0), 0.7f + 0.8f * Flora.unit(more, 4),
                (0.12f + 0.12f * Flora.unit(more, 16)) * size, (0.026f + 0.016f * Flora.unit(more, 12)) * broadens, Flora.unit(more, 20) - 0.5f, steps, SpriteGeometry.POINTED,
                BROAD, Flora.unit(more, 20), vigour);
    }

    /** A dead blade: bleached, and lying most of the way over. */
    private static void straw(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float size, float broadens,
            int steps, float vigour) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        geometry.addStrip(mesh, frame, cos(turn), sin(turn), 0f, 0f, 0.6f + 0.6f * Flora.unit(more, 0), 0.15f + 0.6f * Flora.unit(more, 4),
                (0.1f + 0.16f * Flora.unit(more, 16)) * size, (0.01f + 0.006f * Flora.unit(more, 12)) * broadens, 0.8f, Math.min(steps, 2), SpriteGeometry.POINTED, STRAW,
                Flora.unit(more, 20), vigour);
    }

    /**
     * A tussock: long blades from one root, standing up through the middle and arching over all
     * round. Most of it is fans of blades leaning out from the root, and a few blades of their
     * own hang out past those.
     */
    private static void tussock(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float high, float broadens,
            int steps, float vigour, int fans) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        for (int fan = 0; fan < fans; fan++) {
            int own = Flora.mix(more, fan, 13);
            float angle = turn + (fan + 0.5f * Flora.unit(own, 0)) * 6.2831853f / fans;
            float outer = fans == 1 ? 0.3f * Flora.unit(own, 8) : 0.35f + 0.65f * Flora.unit(own, 8);
            geometry.addStrip(mesh, frame, cos(angle), sin(angle), 0f, 0f, 0.08f + 0.5f * outer, 0.3f + 0.9f * outer, high * (1f - 0.3f * outer),
                    high * (0.4f + 0.3f * Flora.unit(own, 12)) * broadens, 0f, steps, SpriteGeometry.FAN, TUFT, Flora.unit(own, 20), vigour);
            if (steps > 2 || fan == 0 && steps > 1) {
                // A blade of its own, lying further out than the fan and hanging at its tip.
                float beside = angle + 0.9f + Flora.unit(own, 16);
                geometry.addStrip(mesh, frame, cos(beside), sin(beside), 0.01f, 0f, 0.35f + 0.5f * Flora.unit(own, 4), 0.8f + 0.9f * Flora.unit(own, 16),
                        high * (0.8f + 0.4f * Flora.unit(own, 4)), (0.013f + 0.009f * Flora.unit(own, 12)) * broadens, Flora.unit(own, 20) - 0.5f, steps,
                        SpriteGeometry.POINTED, (own & 0x7000000) == 0 ? STRAW : (own & 0x1000000) == 0 ? BLADE : BROAD, Flora.unit(own, 14), vigour);
            }
        }
    }

    /** A stalk gone to seed: a thin stem with a head on it, and a leaf or two at its foot. */
    private static void stalk(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float high, float broadens,
            int steps, float vigour) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        float dirX = cos(turn), dirY = sin(turn);
        float seed = Flora.unit(more, 20);
        geometry.addStrip(mesh, frame, dirX, dirY, 0f, 0f, 0.04f + 0.14f * Flora.unit(more, 0), 0.08f + 0.3f * Flora.unit(more, 4), high,
                0.0055f * broadens, 0f, Math.min(steps, 2), SpriteGeometry.EVEN, STEM, seed, vigour);
        float fromAlong = geometry.endAlong, fromUp = geometry.endUp, lean = geometry.endLean;
        if ((more & 0x3000) == 0) {
            // A panicle: an open head of spikelets on hair-thin branches, nodding over under its own weight.
            geometry.addStrip(mesh, frame, dirX, dirY, fromAlong, fromUp, lean + 0.25f, 0.7f + 0.5f * Flora.unit(more, 12), high * 0.34f + 0.03f,
                    (0.05f + 0.03f * Flora.unit(more, 16)) * Math.min(broadens, 1.6f), 0f, Math.min(steps, 2), SpriteGeometry.EVEN, PANICLE, seed, vigour);
        } else {
            // A spike: a tight bristly head the length of a finger.
            geometry.addStrip(mesh, frame, dirX, dirY, fromAlong, fromUp, lean, 0.25f * Flora.unit(more, 12), 0.045f + 0.05f * Flora.unit(more, 16) + 0.06f * high,
                    (0.02f + 0.008f * Flora.unit(more, 12)) * Math.min(broadens, 1.6f), 0f, 1, SpriteGeometry.EVEN, SPIKE, seed, vigour);
        }
        // The stalk's own leaf, part of the way up it, and one at its foot.
        geometry.addStrip(mesh, frame, -dirY, dirX, 0f, high * 0.3f, 0.5f + 0.4f * Flora.unit(more, 4), 0.8f, high * 0.4f, 0.011f * broadens, 0.4f,
                Math.min(steps, 2), SpriteGeometry.POINTED, BLADE, Flora.unit(more, 14), vigour);
        if (steps < 3) {
            return;
        }
        geometry.addStrip(mesh, frame, dirY, -dirX, 0f, 0f, 0.3f + 0.4f * Flora.unit(more, 12), 0.9f, high * 0.5f, 0.013f * broadens, -0.4f,
                Math.min(steps, 2), SpriteGeometry.POINTED, BLADE, Flora.unit(more, 10), vigour);
    }

    /** A sprig of clover: a few leaves of three, each held out flat on its own stem. */
    private static void clover(SpriteGeometry geometry, SpriteGeometry.Mesh mesh, SpriteGeometry.Frame frame, int more, float grown, float broadens,
            float vigour) {
        float turn = Flora.unit(more, 24) * 6.2831853f;
        int leaves = broadens > 1.5f ? 2 : 3 + (more >>> 4 & 1);
        for (int leaf = 0; leaf < leaves; leaf++) {
            int own = Flora.mix(more, leaf, 19);
            float angle = turn + leaf * 6.2831853f / leaves + Flora.unit(own, 0);
            float across = (0.04f + 0.022f * Flora.unit(own, 8)) * grown * Math.min(broadens, 1.8f);
            float out = 0.015f + 0.05f * Flora.unit(own, 16);
            geometry.addStrip(mesh, frame, cos(angle), sin(angle), out, (0.03f + 0.05f * Flora.unit(own, 4)) * grown, 1.1f + 0.35f * Flora.unit(own, 12), 0.2f,
                    across, across, 0f, 1, SpriteGeometry.EVEN, CLOVER, Flora.unit(own, 20), vigour);
        }
    }
}
