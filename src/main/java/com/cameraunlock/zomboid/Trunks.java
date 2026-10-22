package com.cameraunlock.zomboid;

import java.util.concurrent.ConcurrentHashMap;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.LosUtil.TestResults;
import zombie.iso.sprite.IsoSprite;
import zombie.util.list.PZArrayList;

/**
 * Trees as a shot meets them in first person. The game stops a shot anywhere on a tree's square:
 * its line of sight test is blocked leaving a square that holds a tree. The first person picture
 * draws the tree as a round bole in the middle of its square (PlantBuilder.addBole), so a shot
 * that passes the bole by is seen to go on, and with the ShotsPastTrees row on it does: what
 * stops it is the bole. A tree the picture draws no bole for, an evergreen whose needles hide
 * its trunk, and every tree on a server, stop a shot on the whole square, as the game has it.
 */
final class Trunks {
    private Trunks() {}

    /** The radius in tiles of the bole the picture stands for each tree sprite it has stood one for. Built on the builders' threads, read on the logic thread. */
    private static final ConcurrentHashMap<IsoSprite, Float> radii = new ConcurrentHashMap<>();

    static void stood(IsoSprite sprite, float radius) {
        radii.put(sprite, radius);
    }

    static void forget() {
        radii.clear();
    }

    /** The radius of the bole the picture draws on a square, or NaN where it draws none. */
    static float on(IsoGridSquare square) {
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            // The game keeps objects with no sprite on squares (rig run a1, 1733, 10885).
            IsoSprite sprite = objects.get(i).getSprite();
            Float radius = sprite == null ? null : radii.get(sprite);
            if (radius != null) {
                return radius;
            }
        }
        return Float.NaN;
    }

    /**
     * The game's test of an edge, asked from the square entered as LosUtil.lineClear asks it, with
     * the tree on the square left taken away where the picture draws that tree as a bole: then the
     * edge is what the test asked from the square left says, which does not see that square's own
     * tree, and the bole is met inside the square instead (see meets).
     */
    static TestResults past(TestResults seen, TestResults asked, float boleLeft) {
        return seen == TestResults.Blocked && asked != TestResults.Blocked && !Float.isNaN(boleLeft) ? asked : seen;
    }

    /**
     * How far along a line on the map, from (x, y) with the ground part (wayX, wayY) of its
     * direction, it first meets the bole of radius radius in the middle of square (squareX,
     * squareY), at or past from: positive infinity where it passes by. The distance is in the
     * units the direction's length gives, as GridTrace's.
     */
    static float meets(float x, float y, float wayX, float wayY, int squareX, int squareY, float radius, float from) {
        float awayX = x - (squareX + 0.5f), awayY = y - (squareY + 0.5f);
        float a = wayX * wayX + wayY * wayY;
        float b = awayX * wayX + awayY * wayY;
        float c = awayX * awayX + awayY * awayY - radius * radius;
        float under = b * b - a * c;
        if (a == 0f || under < 0f) {
            return Float.POSITIVE_INFINITY;
        }
        float root = (float) Math.sqrt(under);
        float near = (-b - root) / a, far = (-b + root) / a;
        if (near >= from) {
            return near;
        }
        // Started inside the bole: it is met at once.
        return far >= from ? from : Float.POSITIVE_INFINITY;
    }
}
