package com.cameraunlock.zomboid;

import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.entity.components.spriteconfig.SpriteConfigManager;
import zombie.iso.IsoCell;
import zombie.iso.IsoDirections;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;
import zombie.ui.UIManager;

/**
 * Where the game's drag cursors are in first person: the ghost of something to build, Walk To,
 * Place Item, moving furniture, Cut down Tree, Dig Furrow. The game puts one on the square its
 * own view has under the mouse (IsoCell.doBuildingInternal reads UIManager.getPickedTile), and
 * Place Item and the furniture cursor turn the mouse into a place on the floor themselves
 * (screenToIsoX and screenToIsoY). That view is under the picture, and while the look has the
 * mouse the mouse does not move. So while a drag is up and the picture is played in, the place
 * is where the line through the reticle meets the world, or with the pointer out where the
 * line through the pointer does, and where there is no such line the drag is put nowhere.
 *
 * Logic thread.
 */
final class DragAim {
    /** How far along a line that meets nothing nearer a drag is put, in tiles. */
    static final float REACH = 20f;
    /** How far back from a wall the line meets a drag is put, in tiles, so it is on the square in front of the wall. */
    static final float SHORT_OF_A_WALL = 0.05f;

    private final Vector2 tile = new Vector2();
    final Aimed aimed = new Aimed();

    /** The drag cursor a player has up, or null. */
    static KahluaTable of(int playerIndex) {
        IsoCell cell = IsoWorld.instance == null ? null : IsoWorld.instance.currentCell;
        return cell == null ? null : cell.getDrag(playerIndex);
    }

    /** The square the game has a drag cursor on, or null where it has none yet. */
    static IsoGridSquare squareOf(KahluaTable drag) {
        return drag.rawget("square") instanceof IsoGridSquare square ? square : null;
    }

    /** Whether the game would put down what is dragged where it is. */
    static boolean fitsOf(KahluaTable drag) {
        return Boolean.TRUE.equals(drag.rawget("canBeBuild"));
    }

    /** Whether a drag is put on a tree's bole where a line meets it: only Cut down Tree is, which is put on a tree. */
    static boolean onBoles(KahluaTable drag) {
        return "ISChopTreeCursor".equals(drag.rawget("Type"));
    }

    /** Whether a drag is put by a window a line meets: only a barricade is, which the game puts on a window. */
    static boolean onWindows(KahluaTable drag) {
        return drag.rawget("objectInfo") instanceof SpriteConfigManager.ObjectInfo info && barricades(info.getScript().getOnIsValid());
    }

    /** Whether a build's test of where it can go is one of the game's barricades', by the test's name. */
    static boolean barricades(String onIsValid) {
        return onIsValid != null && onIsValid.startsWith("BuildRecipeCode.barricade.");
    }

    /** Which way what is dragged is put, as far as the picture shows it: the edge of its square it stands on, or the way its front faces. */
    enum Facing { NONE, NORTH_EDGE, WEST_EDGE, NORTH, EAST, SOUTH, WEST }

    /** The most lines facingLines gives. */
    static final int MOST_FACING_LINES = 3;
    /** How high the frame on the edge a wall, a fence, a door or a window is put on stands over its floor: low, so it is in the picture while the eye looks down at the square. */
    static final float EDGE_FRAME_HIGH = 0.25f * ViewCamera.LEVEL_HEIGHT;
    /** The arrow of the way a piece faces, in tiles from its square's middle: its tip out along the way, and its wings' feet along and across it. */
    private static final float ARROW_TIP = 0.4f, ARROW_FOOT = 0.05f, ARROW_WING = 0.3f;

    /**
     * Which way what a drag shows is put: the build window's piece by the first tile of the face
     * it is turned to (ISBuildIsoEntity.getFace, which R steps through), the furniture cursor's by
     * the sprite it shows, and an older build cursor's by the sprite its getSprite chose.
     */
    static Facing facingOf(KahluaTable drag) {
        IsoSprite sprite = shownOf(drag);
        if (sprite == null) {
            return Facing.NONE;
        }
        IsoDirections way = sprite.getFacing();
        return facing(Edges.north(sprite.getProperties(), sprite.getType()), Edges.west(sprite.getProperties(), sprite.getType()),
                way == null ? null : way.name());
    }

    private static IsoSprite shownOf(KahluaTable drag) {
        if (drag.rawget("face") instanceof SpriteConfigManager.FaceInfo face) {
            String name = firstSpriteOf(face);
            return name == null ? null : IsoSpriteManager.instance.namedMap.get(name);
        }
        if (drag.rawget("objectSprite") instanceof IsoSprite sprite) {
            return sprite;
        }
        return drag.rawget("chosenSprite") instanceof String name ? IsoSpriteManager.instance.namedMap.get(name) : null;
    }

    /** The tile of a face on the drag's own square first, as the game lays a face from that square on. */
    private static String firstSpriteOf(SpriteConfigManager.FaceInfo face) {
        for (int z = 0; z < face.getzLayers(); z++) {
            for (int x = 0; x < face.getWidth(); x++) {
                for (int y = 0; y < face.getHeight(); y++) {
                    SpriteConfigManager.TileInfo tile = face.getTileInfo(x, y, z);
                    if (tile != null && tile.getSpriteName() != null) {
                        return tile.getSpriteName();
                    }
                }
            }
        }
        return null;
    }

    /**
     * Which way a sprite is put: on the edge its flags put it on (Edges), and otherwise the way
     * its Facing says its front is turned, one of N, E, S and W.
     */
    static Facing facing(boolean onNorthEdge, boolean onWestEdge, String way) {
        if (onNorthEdge) {
            return Facing.NORTH_EDGE;
        }
        if (onWestEdge) {
            return Facing.WEST_EDGE;
        }
        if (way == null) {
            return Facing.NONE;
        }
        return switch (way) {
            case "N" -> Facing.NORTH;
            case "E" -> Facing.EAST;
            case "S" -> Facing.SOUTH;
            case "W" -> Facing.WEST;
            default -> Facing.NONE;
        };
    }

    /**
     * The lines that show a facing on a square, into lines as from x, y, height over the floor to
     * x, y, height, in tiles, and how many: for an edge the two posts and the top of a frame
     * standing on it, and for a way an arrow on the floor from the square's middle, north up the
     * map (y less) and east across it (x more).
     */
    static int facingLines(Facing facing, int squareX, int squareY, float[] lines) {
        if (facing == Facing.NONE) {
            return 0;
        }
        if (facing == Facing.NORTH_EDGE || facing == Facing.WEST_EDGE) {
            float toX = facing == Facing.NORTH_EDGE ? squareX + 1f : squareX, toY = facing == Facing.NORTH_EDGE ? squareY : squareY + 1f;
            line(lines, 0, squareX, squareY, 0f, squareX, squareY, EDGE_FRAME_HIGH);
            line(lines, 1, toX, toY, 0f, toX, toY, EDGE_FRAME_HIGH);
            line(lines, 2, squareX, squareY, EDGE_FRAME_HIGH, toX, toY, EDGE_FRAME_HIGH);
            return 3;
        }
        float wayX = facing == Facing.EAST ? 1f : facing == Facing.WEST ? -1f : 0f;
        float wayY = facing == Facing.SOUTH ? 1f : facing == Facing.NORTH ? -1f : 0f;
        float middleX = squareX + 0.5f, middleY = squareY + 0.5f;
        float tipX = middleX + wayX * ARROW_TIP, tipY = middleY + wayY * ARROW_TIP;
        float footX = middleX + wayX * ARROW_FOOT, footY = middleY + wayY * ARROW_FOOT;
        line(lines, 0, footX - wayY * ARROW_WING, footY + wayX * ARROW_WING, 0f, tipX, tipY, 0f);
        line(lines, 1, footX + wayY * ARROW_WING, footY - wayX * ARROW_WING, 0f, tipX, tipY, 0f);
        return 2;
    }

    private static void line(float[] lines, int index, float fromX, float fromY, float fromHigh, float toX, float toY, float toHigh) {
        int at = index * 6;
        lines[at] = fromX;
        lines[at + 1] = fromY;
        lines[at + 2] = fromHigh;
        lines[at + 3] = toX;
        lines[at + 4] = toY;
        lines[at + 5] = toHigh;
    }

    /**
     * Puts a player's drag cursor away as the game's Esc does (ToggleEscapeMenu): its close
     * first, where it has one, and the furniture cursor by its own exitCursor, which also takes
     * down the panel it shows beside it.
     */
    static void putAway(KahluaTable drag, int playerIndex) {
        Object exit = UIManager.tableget(drag, "exitCursor");
        if (exit != null) {
            call(exit, drag);
            return;
        }
        Object close = UIManager.tableget(drag, "close");
        if (close != null) {
            call(close, drag);
        }
        IsoWorld.instance.currentCell.setDrag(null, playerIndex);
    }

    private static void call(Object function, KahluaTable drag) {
        Object[] result = LuaManager.caller.pcall(UIManager.getDefaultThread(), function, new Object[] {drag});
        if (result.length == 0 || !Boolean.TRUE.equals(result[0])) {
            throw new IllegalStateException("putting the drag cursor " + drag.rawget("Type") + " away failed: " + java.util.Arrays.toString(result));
        }
    }

    /**
     * How far along a line a drag is put: where the line first meets a wall, a floor or a
     * ceiling, and no further than REACH.
     *
     * @param met the distance to what the line met, infinity for nothing
     */
    static float along(float met) {
        return Math.min(met, REACH);
    }

    /**
     * Where a drag is put across a wall's face that a line met, so it is on the square the line
     * came from: back off the wall's edge, which is at a whole tile, and not back along the line,
     * which at a shallow angle or far out on the map goes on through the edge.
     *
     * @param at where the line met the wall, across its face, in tiles
     * @param way the line's way across the face
     */
    static float offWall(float at, float way) {
        return Math.round(at) - Math.signum(way) * SHORT_OF_A_WALL;
    }

    /** Puts the drag at a place on the map, in tiles. */
    void aim(float atX, float atY) {
        aimed.aim(atX, atY);
        tile.x = (float) Math.floor(atX);
        tile.y = (float) Math.floor(atY);
    }

    /**
     * Called with the tile the game's own view has under the mouse.
     *
     * @param inFirstPerson whether the picture is played in by a mouse player, for as long as
     *        first person is on, so a long frame, after which the picture reads as not drawn
     *        until the next, does not hand the game the tile under the hidden mouse
     */
    Vector2 tile(Vector2 stock, boolean inFirstPerson) {
        return switch (aimed.put(inFirstPerson)) {
            case STOCK -> stock;
            case AIMED -> stock == null ? null : tile;
            case NOWHERE -> null;
        };
    }

    /** Called with the place across the map the game's own view has at a point on the screen. */
    float isoX(float stock, boolean inFirstPerson) {
        return aimed.onTheFloor(inFirstPerson) ? aimed.x : stock;
    }

    /** The same, down the map. */
    float isoY(float stock, boolean inFirstPerson) {
        return aimed.onTheFloor(inFirstPerson) ? aimed.y : stock;
    }

    /** Where a drag is handed: the game's own square, the one aimed at, or none. */
    enum Put { STOCK, AIMED, NOWHERE }

    /** Where the picture has put the drag up. */
    static final class Aimed {
        /** Whether a place has been found for the drag up, and whether this frame found none. */
        private boolean placed, lost;
        private float x, y;

        void aim(float atX, float atY) {
            x = atX;
            y = atY;
            placed = true;
            lost = false;
        }

        /** A drag is up and this frame has no line to put it on: it is put nowhere, and its place on the floor stays where it was. */
        void lose() {
            lost = true;
        }

        /** No drag is up, or the picture is not played in. */
        void forget() {
            placed = false;
            lost = false;
        }

        Put put(boolean inFirstPerson) {
            if (!inFirstPerson) {
                return Put.STOCK;
            }
            return placed && !lost ? Put.AIMED : Put.NOWHERE;
        }

        /** Whether a place on the floor the game turns the mouse into is the aimed one. */
        boolean onTheFloor(boolean inFirstPerson) {
            return inFirstPerson && placed;
        }

        float x() {
            return x;
        }

        float y() {
            return y;
        }
    }
}
