package com.cameraunlock.zomboid;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import zombie.iso.BuildingDef;
import zombie.iso.IsoMetaCell;
import zombie.iso.IsoMetaGrid;
import zombie.iso.RoomDef;

/**
 * Which air round the viewer is in a room of a building, and so holds the room's dust for the
 * sun through a window to catch: see PipelineShaders.VOLUME. The map from above has something
 * over the air under an awning, a porch roof or a tree as well, and that air is out of doors.
 *
 * A block of SIDE by SIDE squares, and for each the height of the floor of the lowest room over
 * it and of the ceiling of the highest, on the land as the picture stands it. Where no room is
 * both are 0. Made on the logic thread and never written again, so the render thread reads one
 * whole.
 */
final class RoomAir {
    static final int SIDE = 64;
    /**
     * How far the viewer goes from the block's middle before another is made round them, in
     * squares. Room air is only asked for under the map from above, which reaches
     * Pipeline.ROOF_EXTENT (20) either way from the viewer, and SIDE / 2 - STRAY is no less.
     */
    private static final int STRAY = 12;

    /** The block's first square. */
    final int x, y;
    /** Two floats a square, a row of SIDE squares after another, from the block's first. */
    final ByteBuffer spans = ByteBuffer.allocateDirect(SIDE * SIDE * 2 * Float.BYTES).order(ByteOrder.nativeOrder());
    private final Terrain terrain;
    private final long heightsVersion;

    private RoomAir(int middleX, int middleY, Terrain terrain) {
        x = middleX - SIDE / 2;
        y = middleY - SIDE / 2;
        this.terrain = terrain;
        heightsVersion = heightsVersion(terrain, x, y);
    }

    /** The rooms of every building round a square, each from its floor to its ceiling. */
    static RoomAir round(IsoMetaGrid grid, Terrain terrain, int middleX, int middleY) {
        RoomAir made = new RoomAir(middleX, middleY, terrain);
        int fromCellX = Math.floorDiv(made.x, FarCells.CELL), toCellX = Math.floorDiv(made.x + SIDE - 1, FarCells.CELL);
        int fromCellY = Math.floorDiv(made.y, FarCells.CELL), toCellY = Math.floorDiv(made.y + SIDE - 1, FarCells.CELL);
        for (int cellX = fromCellX; cellX <= toCellX; cellX++) {
            for (int cellY = fromCellY; cellY <= toCellY; cellY++) {
                IsoMetaCell cell = grid.getCellData(cellX, cellY);
                if (cell == null) {
                    continue;
                }
                for (BuildingDef building : cell.buildings) {
                    for (RoomDef room : building.rooms) {
                        for (RoomDef.RoomRect rect : room.rects) {
                            made.cover(rect.x, rect.y, rect.w, rect.h, room.level);
                        }
                    }
                }
            }
        }
        return made;
    }

    private void cover(int fromX, int fromY, int wide, int deep, int level) {
        for (int row = Math.max(fromY - y, 0); row < Math.min(fromY + deep - y, SIDE); row++) {
            for (int column = Math.max(fromX - x, 0); column < Math.min(fromX + wide - x, SIDE); column++) {
                float lift = terrain.lift(x + column + 0.5f, y + row + 0.5f, level);
                float floor = level * ViewCamera.LEVEL_HEIGHT + lift, ceiling = floor + ViewCamera.LEVEL_HEIGHT;
                int at = (row * SIDE + column) * 2 * Float.BYTES, up = at + Float.BYTES;
                boolean empty = spans.getFloat(at) == 0f && spans.getFloat(up) == 0f;
                spans.putFloat(at, empty ? floor : Math.min(spans.getFloat(at), floor));
                spans.putFloat(up, empty ? ceiling : Math.max(spans.getFloat(up), ceiling));
            }
        }
    }

    /** Whether the viewer has gone too far from the block, or the land it was stood on has been worked out again since. */
    boolean staleFor(Terrain now, int squareX, int squareY) {
        return now != terrain || Math.abs(squareX - x - SIDE / 2) > STRAY || Math.abs(squareY - y - SIDE / 2) > STRAY
                || heightsVersion(now, x, y) != heightsVersion;
    }

    /** One number for the versions of the heights of every chunk the block covers. */
    private static long heightsVersion(Terrain terrain, int x, int y) {
        long sum = 17;
        for (int wx = Math.floorDiv(x, WorldScene.CHUNK_TILES); wx <= Math.floorDiv(x + SIDE - 1, WorldScene.CHUNK_TILES); wx++) {
            for (int wy = Math.floorDiv(y, WorldScene.CHUNK_TILES); wy <= Math.floorDiv(y + SIDE - 1, WorldScene.CHUNK_TILES); wy++) {
                sum = sum * 31 + terrain.versionIfKept(wx, wy);
            }
        }
        return sum;
    }
}
