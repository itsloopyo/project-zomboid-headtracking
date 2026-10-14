package com.cameraunlock.zomboid;

import java.nio.FloatBuffer;
import java.util.Locale;
import zombie.characters.IsoPlayer;
import zombie.iso.IsoCell;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoWindow;
import zombie.util.list.PZArrayList;

/**
 * The viewer in the glass of a window. A mirror in a room shows them in a picture of its
 * own, with the rest of the room: see LookingGlass.
 *
 * What a pane shows of the room is traced in the picture itself, so it can
 * only show what the picture has in it, and in first person the viewer is not
 * in it: nearer than their own shoulders nothing of them is drawn. So they are
 * drawn once more, whole, as the glass would show them: the same model stood
 * on the far side of the glass's plane, which is the model as it stands seen
 * by an eye on the far side. That picture is lit as any person's is, and then
 * laid, thinly, over the openings of the windows in that wall. What glass
 * throws back is added to what is seen through it, so by day the street
 * outside drowns it and at night it is plain.
 *
 * A window the picture draws as its sprite hangs on the wall and not in it, so
 * there the wall hides nothing. The picture marks such a surface as a mirror
 * for its own tracing of the room, and that mark says where the viewer is laid
 * and how strongly. There they are not added but put in the place of what was
 * there. Where they are laid the mark is taken off, so the traced room is not
 * laid over them: they stand before it.
 *
 * One plane a frame: the nearest wall the viewer stands square before a pane
 * of, and every pane in it.
 */
final class OwnReflection {
    /** The most windows of one wall the viewer is shown in. */
    static final int MOST = 16;
    /** How far along the map from the viewer a window is looked for, in tiles. */
    private static final int REACH = 7;
    /** How far past the end of a window the line from the eye may meet the wall and the window still show some of the viewer, in tiles. */
    private static final float BESIDE = 0.6f;
    /** How far to the side of the picture's edge the place the viewer is seen at may be and some of them still be in the picture, in radians. */
    private static final float PAST_THE_EDGE = 0.35f;
    /** Nearer the glass's plane than this the eye is as good as in it, and there is no reflection to draw. */
    private static final float NEAREST = 0.1f;
    /** How much of the viewer a pane shows: what the picture has a window pane mirror of the room. */
    static final float THROWN = 0.45f;
    /**
     * How far before the glass the reflection is laid, in tiles: nearer the eye than the pane,
     * which would hide it, and further than the face of the wall the window is set in, which
     * hides it everywhere but in the opening.
     */
    private static final float BEFORE = 0.02f;
    /**
     * How far out from the wall's edge the reflection is laid for what hangs ON the wall, a
     * window drawn as its sprite: before all of it, and found again by what of the picture
     * under it is marked as a mirror.
     */
    private static final float BEFORE_HUNG = 0.2f;
    /** How far above the floor that begins: under it the floor itself, where it is polished, would take the reflection. */
    private static final float ABOVE_THE_FLOOR = 0.3f;
    /**
     * How much of the viewer is shown for each part of a mirror the picture has a surface
     * marked as: a window's glass, marked at rather more than a half, shows nearly all.
     * No surface is marked as more of a mirror than one over this.
     */
    static final float HUNG_THROWN = 1.5f;

    private static long said;

    private OwnReflection() {}

    /** The wall whose windows show the viewer, as a frame carries it from the thread that found it to the one that draws. */
    static final class Glass {
        boolean found;
        /** Whether the plane is one of constant y, the north edge of its squares. Otherwise of constant x, the west edge. */
        boolean north;
        /** The edge of the squares the wall stands on, and where the glass itself is. */
        int edge;
        float at;
        /** Where each window starts along the wall, a tile long each. */
        final float[] along = new float[MOST];
        int count;
        /** The height of the floor the windows stand on, and of the eye's side of the plane. */
        float foot;
        float side;
    }

    /**
     * Logic thread: the wall on the player's floor whose glass has the viewer in it, and the panes in that wall.
     *
     * @param tanHalfAcross the tangent of half the picture's field of view from side to side
     */
    static void find(IsoCell cell, IsoPlayer player, Terrain terrain, ViewCamera.Pose view, float tanHalfAcross, Glass into) {
        into.found = false;
        // The viewer is seen in the glass straight along the line that meets it squarely. With
        // that line outside the picture there is nothing of them to draw.
        float facingLeast = (float) Math.cos(Math.min(Math.atan(tanHalfAcross) + PAST_THE_EDGE, Math.PI / 2));
        int level = (int) Math.floor(player.getZ());
        int fromX = (int) Math.floor(view.eyeX), fromY = (int) Math.floor(view.eyeY);
        float nearest = Float.MAX_VALUE;
        for (int x = fromX - REACH; x <= fromX + REACH; x++) {
            for (int y = fromY - REACH; y <= fromY + REACH; y++) {
                IsoGridSquare square = cell.getGridSquare(x, y, level);
                if (square == null) {
                    continue;
                }
                PZArrayList<IsoObject> objects = square.getObjects();
                for (int i = 0; i < objects.size(); i++) {
                    if (!pane(objects.get(i), x, y)) {
                        continue;
                    }
                    // A flat mirror shows the viewer along the line that meets it squarely, so
                    // only a pane that line passes through, or passes within a body's width
                    // of, has them in it, and only if the view is turned towards that wall.
                    float off = paneEdge - (paneNorth ? view.eyeY : view.eyeX);
                    float side = off > 0f ? -1f : 1f;
                    float beside = paneNorth ? view.eyeX - x : view.eyeY - y;
                    float facing = (paneNorth ? view.forwardY : view.forwardX) * Math.signum(off);
                    if (Math.abs(off) < NEAREST || Math.abs(off) >= nearest
                            || beside < -BESIDE || beside > 1f + BESIDE || facing < facingLeast) {
                        continue;
                    }
                    nearest = Math.abs(off);
                    into.found = true;
                    into.north = paneNorth;
                    into.edge = paneEdge;
                    into.side = side;
                    into.at = paneEdge;
                }
            }
        }
        if (!into.found) {
            return;
        }
        into.count = 0;
        into.foot = level * ViewCamera.LEVEL_HEIGHT + terrain.lift(player.getX(), player.getY(), level);
        int middle = into.north ? fromX : fromY;
        for (int along = middle - REACH; along <= middle + REACH && into.count < MOST; along++) {
            if (paneOf(cell, along, into, level)) {
                into.along[into.count++] = along;
            }
        }
        if (FirstPerson.TRACE && System.nanoTime() - said > 1_000_000_000L) {
            said = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "glass: the wall at %s = %.2f, %d panes from %.0f, eye (%.2f, %.2f) on side %.0f, looking (%.2f, %.2f)",
                    into.north ? "y" : "x", into.at, into.count, into.count > 0 ? into.along[0] : 0f, view.eyeX, view.eyeY, into.side, view.forwardX, view.forwardY));
        }
    }

    /** Whether the square a place along the found wall, on the edge's own row of squares, has a pane in that wall. */
    private static boolean paneOf(IsoCell cell, int along, Glass wall, int level) {
        int x = wall.north ? along : wall.edge, y = wall.north ? wall.edge : along;
        IsoGridSquare square = cell.getGridSquare(x, y, level);
        if (square == null) {
            return false;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (pane(objects.get(i), x, y) && paneNorth == wall.north && paneEdge == wall.edge) {
                return true;
            }
        }
        return false;
    }

    /** What pane last found: whether its wall is a north edge, and which edge. */
    private static boolean paneNorth;
    private static int paneEdge;

    /**
     * Whether an object on the square (x, y) shows the viewer: a window with its glass in it
     * and shut, which stands in the wall on the square's own north or west edge and is glass
     * from both sides.
     */
    private static boolean pane(IsoObject object, int x, int y) {
        if (!(object instanceof IsoWindow window) || window.isSmashed() || window.isGlassRemoved() || window.IsOpen()) {
            return false;
        }
        paneNorth = window.getNorth();
        paneEdge = paneNorth ? y : x;
        return true;
    }

    /**
     * Has a view matrix, by columns, draw the world as the glass shows it: every point is
     * first put where it is seen in the glass, on the far side of the plane.
     */
    static void mirror(float[] view, Glass glass) {
        int axis = glass.north ? 4 : 0;
        for (int row = 0; row < 3; row++) {
            view[12 + row] += 2f * glass.at * view[axis + row];
            view[axis + row] = -view[axis + row];
        }
    }

    /**
     * The eye on the far side of the glass that sees the world as the glass shows it to the
     * real one. Its three axes are the real eye's, thrown back like everything else, so it is
     * of the other hand: what it has on its right the real eye has on its left.
     */
    static void mirror(ViewCamera.Pose camera, Glass glass, ViewCamera.Pose out) {
        out.set(camera);
        if (glass.north) {
            out.eyeY = 2f * glass.at - camera.eyeY;
            out.rightY = -camera.rightY;
            out.upY = -camera.upY;
            out.forwardY = -camera.forwardY;
        } else {
            out.eyeX = 2f * glass.at - camera.eyeX;
            out.rightX = -camera.rightX;
            out.upX = -camera.upX;
            out.forwardX = -camera.forwardX;
        }
    }

    /**
     * The openings the reflection is laid over, two triangles to a pane a tile wide, measured
     * from the eye. In the wall they stand a hair before the glass from the floor to the floor
     * above, and the wall's own face hides them outside the opening. Hung on the wall they
     * stand before everything that hangs there. Returns how many corners were written.
     */
    static int corners(Glass glass, ViewCamera.Pose camera, boolean hung, FloatBuffer into) {
        into.clear();
        float plane = glass.edge + glass.side * (hung ? BEFORE_HUNG : BEFORE);
        float foot = glass.foot - camera.eyeUp + (hung ? ABOVE_THE_FLOOR : 0f), head = glass.foot - camera.eyeUp + ViewCamera.LEVEL_HEIGHT;
        for (int pane = 0; pane < glass.count; pane++) {
            float from = glass.along[pane], to = from + 1f;
            corner(glass, camera, into, plane, from, foot);
            corner(glass, camera, into, plane, to, foot);
            corner(glass, camera, into, plane, to, head);
            corner(glass, camera, into, plane, from, foot);
            corner(glass, camera, into, plane, to, head);
            corner(glass, camera, into, plane, from, head);
        }
        into.flip();
        return glass.count * 6;
    }

    private static void corner(Glass glass, ViewCamera.Pose camera, FloatBuffer into, float plane, float along, float up) {
        into.put((glass.north ? along : plane) - camera.eyeX).put((glass.north ? plane : along) - camera.eyeY).put(up);
    }

    /** The reflection's picture, where the opening covers it, thinned to what glass throws back. */
    static final String SHOWN = """
            #version 120
            uniform sampler2D uSelf;
            uniform vec2 uSize;
            uniform float uThrown;
            uniform float uMark;
            uniform float uOnly;
            void main() {
                vec4 self = texture2D(uSelf, gl_FragCoord.xy / uSize);
                if (uOnly > 0.5 && self.a < 0.5) {
                    discard;
                }
                gl_FragColor = vec4(self.rgb * uThrown, uMark);
            }
            """;
}
