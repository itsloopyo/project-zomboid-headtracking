package com.cameraunlock.zomboid;

/**
 * What a rifle scope does to light, as numbers: the three scopes the game has, and the
 * geometry every telescopic sight shares. The picture of the world seen in the eyepiece is
 * drawn and lit elsewhere; this says how wide it is, how big it looks, and from where it can
 * be seen at all.
 *
 * A scope set for a distant target sends out of its eyepiece, for each point of the world, a
 * bundle of parallel rays. So which point of the world the eye sees along a line depends only
 * on the angle of that line to the scope's axis, not on where the eye is: the angle in the
 * world is the angle at the eye over the magnification. Where the eye is decides whether it
 * sees anything. Every bundle crosses the axis in a small disc behind the eyepiece, the exit
 * pupil, which is the objective lens made small by the magnification and stands the eye
 * relief behind the last lens. A line of sight that misses that disc shows the black inside of
 * the tube. With the eye on the disc the whole field is lit. Off to one side a shadow comes in
 * from the other, and too far back the field closes to a tunnel.
 *
 * Too near is where this scope parts from a real one, whose field closes there as well. A
 * viewer who leans in to the eyepiece is looking closer, so nearer than the eye relief the
 * picture is whole and grows with the glass it is seen in.
 *
 * Lengths are metres and angles are tangents. The game's rifles are drawn smaller than life,
 * and a scope on one is drawn to match, so there are two measures: the scope's own, in which
 * its glass and its eye relief are what they would be on a bench, and the world's, in which
 * the viewer's head moves. How far off the exit pupil an eye may be is a matter between the
 * head and the eye's own pupil, and is kept in the world's measure: a head that has moved four
 * millimetres has lost as much of the picture as it would behind the real thing.
 */
final class ScopeOptics {
    private ScopeOptics() {}

    /**
     * One of the game's scopes.
     *
     * @param part      the game's name for the weapon part
     * @param power     how many times larger it shows the world than the unaided eye sees it
     * @param objective the radius of the clear glass of its front lens
     * @param length    from the back of the eyepiece to the front of the objective bell
     */
    record Glass(String part, float power, float objective, float length) {
        /** The radius of the disc behind the eyepiece that all its light passes through. */
        float exitPupil() {
            return objective / power;
        }
    }

    static final Glass[] KNOWN = {
            new Glass("Base.x2Scope", 2f, 0.012f, 0.24f),
            new Glass("Base.x4Scope", 4f, 0.016f, 0.29f),
            new Glass("Base.x8Scope", 8f, 0.022f, 0.34f),
    };

    /** The scope the game calls by this name, or null: a red dot and a set of night sights are weapon parts too. */
    static Glass of(String part) {
        for (Glass glass : KNOWN) {
            if (glass.part.equals(part)) {
                return glass;
            }
        }
        return null;
    }

    /** How far behind the eyepiece's glass the exit pupil stands. */
    static final float RELIEF = 0.09f;
    /** The radius of the eyepiece's clear glass. */
    static final float EYE_LENS = 0.019f;
    /** Half the angle the whole picture fills at the eye: the edge of the field stop inside the tube. */
    static final float FIELD = 0.2f;
    /** The radius of the pupil of the viewer's own eye. */
    static final float PUPIL = 0.003f;
    /**
     * The picture is drawn this much wider than the field, for the part of it the lens bends
     * in from outside the field at its edge.
     */
    static final float MARGIN = 1.08f;

    /**
     * How many times larger the scope shows the world than the picture round it does. The
     * picture round it is itself drawn larger while a firearm is aimed, by one over the zoom,
     * and the scope's power is counted from the unaided view, so the two are not multiplied.
     *
     * @param zoom the tangent the frame is drawn with over the tangent the player set
     */
    static float magnification(Glass glass, float zoom) {
        return glass.power * zoom;
    }

    /**
     * How much of a turn of the mouse reaches the aim behind a scope, over and above what the
     * zoom of the picture round it already takes off. The aim is taken with the scope's
     * picture, which shows the world its power times larger than the unaided view, so with the
     * sights up the two together make the turn one over that power: a hand's movement carries
     * the reticle as far across what it is on as it carries the crosshair at the hip. It
     * comes in as the sights come up, with the zoom.
     *
     * An eye nearer than the eye relief sees the picture larger again, and the turn is that
     * much smaller.
     *
     * @param glass the scope fitted, or null
     * @param zoom  the tangent the frame is drawn with over the tangent the player set
     * @param aimed that ratio with the sights fully up
     * @param grown how many times larger the picture is for the eye's being near: see grown
     */
    static float turn(Glass glass, float zoom, float aimed, float grown) {
        if (glass == null) {
            return 1f;
        }
        float raised = Math.clamp((1f - zoom) / (1f - aimed), 0f, 1f);
        return 1f + (1f / (glass.power * aimed * grown) - 1f) * raised;
    }

    /** Half the angle of the world the scope's picture is drawn with, top to bottom and side to side alike. */
    static float pictureTan(Glass glass, float zoom) {
        return FIELD * MARGIN / magnification(glass, zoom);
    }

    /** An eye is never counted nearer the eyepiece's glass than this, nor one in front of it as anything else. */
    static final float NEAREST_EYE = 0.001f;

    /**
     * How many times larger the picture is to an eye this far behind the eyepiece's glass, in
     * the scope's measure, than it is from the eye relief. From there and further back, once.
     * Nearer, it grows as the glass does, so that the picture fills as much of the glass as
     * it does from the eye relief.
     */
    static float grown(float behind) {
        return Math.max(RELIEF / Math.max(behind, NEAREST_EYE), 1f);
    }

    /** Half the angle the picture fills at an eye this far behind the eyepiece's glass: the field stop's, grown. */
    static float field(float behind) {
        return FIELD * grown(behind);
    }

    /**
     * How much of the light of one line of sight reaches the eye: 1 with the line through the
     * middle of the exit pupil, 0 once the eye's own pupil is clear of it. An eye nearer than
     * the eye relief loses none for being near.
     *
     * @param acrossX how far from the axis the eye is, to one side, and acrossY the other way across, in the scope's measure
     * @param behind  how far behind the eyepiece's glass the eye is, in the scope's measure
     * @param slopeX  the line of sight's angle to the axis, the same two ways
     * @param drawn   the world's metres to one of the scope's
     */
    static float lit(Glass glass, float acrossX, float acrossY, float behind, float slopeX, float slopeY, float drawn) {
        float back = Math.max(behind - RELIEF, 0f);
        float atX = acrossX + slopeX * back, atY = acrossY + slopeY * back;
        float off = (float) Math.sqrt(atX * atX + atY * atY) * drawn;
        float whole = Math.abs(glass.exitPupil() - PUPIL), none = glass.exitPupil() + PUPIL;
        float t = Math.clamp((off - whole) / (none - whole), 0f, 1f);
        return 1f - t * t * (3f - 2f * t);
    }

    /**
     * Whether any of the picture can be seen from where the eye is, so that a frame in which
     * the eyepiece is black from edge to edge need not draw the world a second time.
     */
    static boolean shows(Glass glass, float acrossX, float acrossY, float behind, float drawn) {
        if (behind <= 0f) {
            return false;
        }
        // The line of sight nearest the middle of the exit pupil is the one that leans most
        // towards it, and no line leans more than the field or the eyepiece's rim lets it.
        float most = Math.min(FIELD, (EYE_LENS + (float) Math.hypot(acrossX, acrossY)) / behind);
        float off = (float) Math.hypot(acrossX, acrossY) - most * Math.max(behind - RELIEF, 0f);
        return off * drawn < glass.exitPupil() + PUPIL;
    }

    /**
     * How far a lean along the aim may take the eye towards the eyepiece, in the world's
     * measure: from the eye relief to short of the glass.
     *
     * @param drawn the world's metres to one of the scope's
     * @param clear how near the glass the eye may come, in the world's measure
     */
    static float forwardStop(float drawn, float clear) {
        return Math.max(RELIEF * drawn - clear, 0f);
    }

    /**
     * How a scope is fixed to the weapon it is on: the matrix from the scope's own axes, in
     * metres, to those of the weapon's model. It is the weapon's transform undone, then the
     * part's, then metres made the model's units.
     *
     * @param part          where the game has the scope in its character's axes, the sixteen floats read by rows
     * @param weapon        the same for the weapon
     * @param unitsPerMetre the model's units to a metre
     * @param out           the matrix, by columns
     */
    static void mount(float[] part, float[] weapon, float unitsPerMetre, float[] out) {
        float m00 = weapon[0], m01 = weapon[1], m02 = weapon[2], m10 = weapon[4], m11 = weapon[5], m12 = weapon[6], m20 = weapon[8], m21 = weapon[9], m22 = weapon[10];
        float det = m00 * (m11 * m22 - m12 * m21) - m01 * (m10 * m22 - m12 * m20) + m02 * (m10 * m21 - m11 * m20);
        float i00 = (m11 * m22 - m12 * m21) / det, i01 = (m02 * m21 - m01 * m22) / det, i02 = (m01 * m12 - m02 * m11) / det;
        float i10 = (m12 * m20 - m10 * m22) / det, i11 = (m00 * m22 - m02 * m20) / det, i12 = (m02 * m10 - m00 * m12) / det;
        float i20 = (m10 * m21 - m11 * m20) / det, i21 = (m01 * m20 - m00 * m21) / det, i22 = (m00 * m11 - m01 * m10) / det;
        for (int column = 0; column < 4; column++) {
            float x = part[column], y = part[4 + column], z = part[8 + column];
            float scale = unitsPerMetre;
            if (column == 3) {
                x -= weapon[3];
                y -= weapon[7];
                z -= weapon[11];
                scale = 1f;
            }
            out[column * 4] = (i00 * x + i01 * y + i02 * z) * scale;
            out[column * 4 + 1] = (i10 * x + i11 * y + i12 * z) * scale;
            out[column * 4 + 2] = (i20 * x + i21 * y + i22 * z) * scale;
            out[column * 4 + 3] = column == 3 ? 1f : 0f;
        }
    }

    /**
     * Where a weapon is held with the sights up so that the eye is on its scope's axis, the
     * eye relief behind the eyepiece's glass: the two numbers ViewModelMath.hold places a
     * firearm by.
     *
     * @param mount   how the scope is fixed to the weapon
     * @param weapon  where the game has the weapon in its character's axes, read by rows
     * @param origin  the weapon's grip, in the character's axes at the scale the game draws them
     * @param forward the way the barrel points there, and top the way the weapon's top faces
     * @param end     the muzzle
     * @param scale   the character's units to tiles
     * @param out     how high the barrel's line is from the eye, which is below it and so under
     *                nought, and how far ahead of the eye the grip is
     */
    static void sighted(float[] mount, float[] weapon, float finalScale, float[] origin, float[] forward, float[] top, float[] end, float scale, float[] out) {
        float y = ScopeModel.EYE_GLASS_IN - RELIEF, z = ScopeModel.AXIS_UP;
        float mx = mount[4] * y + mount[8] * z + mount[12], my = mount[5] * y + mount[9] * z + mount[13], mz = mount[6] * y + mount[10] * z + mount[14];
        float eyeX = (weapon[0] * mx + weapon[1] * my + weapon[2] * mz + weapon[3]) * finalScale;
        float eyeY = (weapon[4] * mx + weapon[5] * my + weapon[6] * mz + weapon[7]) * finalScale;
        float eyeZ = (weapon[8] * mx + weapon[9] * my + weapon[10] * mz + weapon[11]) * finalScale;
        float length = (float) Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
        float fx = forward[0] / length, fy = forward[1] / length, fz = forward[2] / length;
        float along = top[0] * fx + top[1] * fy + top[2] * fz;
        float ux = top[0] - along * fx, uy = top[1] - along * fy, uz = top[2] - along * fz;
        length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        out[0] = -scale * ((eyeX - end[0]) * ux + (eyeY - end[1]) * uy + (eyeZ - end[2]) * uz) / length;
        out[1] = -scale * ((eyeX - origin[0]) * fx + (eyeY - origin[1]) * fy + (eyeZ - origin[2]) * fz);
    }
}
