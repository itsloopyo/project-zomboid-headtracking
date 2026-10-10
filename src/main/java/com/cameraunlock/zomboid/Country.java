package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import zombie.iso.IsoMetaCell;
import zombie.iso.IsoMetaGrid;
import zombie.iso.zones.Zone;

/**
 * How much of the land round a place is open country, farmland or woods, as the game's map
 * zones it: 1 deep in farmland or forest, 0 in a town and where the map has no zone. The land
 * rises into hills of its own there (Terrain.upland). The zones are the game's whole map, read
 * once a map is loaded, so the answer is the same for the built world and the far land, and the
 * same from wherever it is asked. Any thread.
 */
final class Country {
    /** Tiles a side of a cell of the field, whose answer is at the cell's middle and read off between. */
    static final int STEP = 16;
    /** How far a cell's zones count round it, in cells: two passes of a box this many cells each way. */
    private static final int SPREAD = 6;
    /** The share of farmland and woods round a place where it begins to be country, and where it is all country. */
    private static final float WILD_FROM = 0.35f, WILD_WHOLE = 0.8f;
    /** The share of town round a place where country begins to give way, and where nothing is left of it. */
    private static final float TOWN_FROM = 0.05f, TOWN_WHOLE = 0.3f;
    /** What scrub and verges count for towards farmland and woods. */
    private static final float SCRUB = 0.25f;

    /** A zone of the map, as Country reads it: its type and its outline, x and y by turns. */
    record Shape(String type, int[] outline) {
        static Shape rectangle(String type, int x, int y, int w, int h) {
            return new Shape(type, new int[] {x, y, x + w, y, x + w, y + h, x, y + h});
        }
    }

    /** The field: cells from (fromX, fromY), wide by high, x first. */
    private record Field(int fromX, int fromY, int wide, int high, float[] cells) {}

    private static final Field NONE = new Field(0, 0, 0, 0, new float[0]);
    /** Before a map is loaded nothing is country. */
    private static volatile Field field = NONE;

    private Country() {}

    /** Reads the map's zones. Logic thread, as a map is loaded and before any height of it is asked for. */
    static void read(IsoMetaGrid grid) {
        long began = System.nanoTime();
        // The zones the map's cells carry, and those registered from its objects.lua (Lua's doMapZones), which grid.zones lists.
        HashSet<Zone> zones = new HashSet<>(grid.zones);
        for (int cellX = grid.getMinX(); cellX <= grid.getMaxX(); cellX++) {
            for (int cellY = grid.getMinY(); cellY <= grid.getMaxY(); cellY++) {
                IsoMetaCell cell = grid.getCellData(cellX, cellY);
                if (cell != null) {
                    cell.getZonesUnique(zones);
                }
            }
        }
        ArrayList<Shape> shapes = new ArrayList<>();
        for (Zone zone : zones) {
            // A zone a mod registers may have no type.
            if (zone.getType() == null || zone.getZ() != 0 || weight(zone.getType()) == 0f && !town(zone.getType())) {
                continue;
            }
            if (zone.isRectangle()) {
                shapes.add(Shape.rectangle(zone.getType(), zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()));
            } else if (zone.isPolygon()) {
                List<Integer> points = zone.getPointsToLua();
                int[] outline = new int[points.size()];
                for (int i = 0; i < outline.length; i++) {
                    outline[i] = points.get(i);
                }
                shapes.add(new Shape(zone.getType(), outline));
            }
        }
        set(shapes);
        Field read = field;
        Log.info(String.format(Locale.ROOT, "country: %d zones of farmland, woods and town of the map's %d read into %d by %d cells in %.0f ms",
                shapes.size(), zones.size(), read.wide, read.high, (System.nanoTime() - began) / 1e6));
    }

    /** Lays the field from the zones given, in place of the one there was. */
    static void set(List<Shape> shapes) {
        if (shapes.isEmpty()) {
            field = NONE;
            return;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (Shape shape : shapes) {
            for (int i = 0; i < shape.outline.length; i += 2) {
                minX = Math.min(minX, shape.outline[i]);
                maxX = Math.max(maxX, shape.outline[i]);
                minY = Math.min(minY, shape.outline[i + 1]);
                maxY = Math.max(maxY, shape.outline[i + 1]);
            }
        }
        // A margin of a spread's reach all round: the field falls to nothing within it, so past it nothing is country.
        int margin = 2 * SPREAD + 1;
        int fromX = Math.floorDiv(minX, STEP) - margin, fromY = Math.floorDiv(minY, STEP) - margin;
        int wide = Math.floorDiv(maxX, STEP) + margin + 1 - fromX, high = Math.floorDiv(maxY, STEP) + margin + 1 - fromY;
        float[] wild = new float[wide * high], town = new float[wide * high];
        for (Shape shape : shapes) {
            if (town(shape.type)) {
                fill(shape.outline, fromX, fromY, wide, high, town, 1f);
            } else {
                fill(shape.outline, fromX, fromY, wide, high, wild, weight(shape.type));
            }
        }
        spread(wild, wide, high);
        spread(town, wide, high);
        float[] cells = new float[wide * high];
        for (int at = 0; at < cells.length; at++) {
            cells[at] = Terrain.smooth((wild[at] - WILD_FROM) / (WILD_WHOLE - WILD_FROM)) * (1f - Terrain.smooth((town[at] - TOWN_FROM) / (TOWN_WHOLE - TOWN_FROM)));
        }
        field = new Field(fromX, fromY, wide, high, cells);
    }

    /** How much a zone of a type counts towards farmland and woods. */
    private static float weight(String type) {
        return switch (type) {
            case "Forest", "DeepForest", "FarmLand", "Farm", "Ranch" -> 1f;
            case "Vegitation" -> SCRUB;
            default -> 0f;
        };
    }

    private static boolean town(String type) {
        return "TownZone".equals(type) || "TrailerPark".equals(type);
    }

    /** Raises to value every cell whose middle the outline holds. */
    private static void fill(int[] outline, int fromX, int fromY, int wide, int high, float[] cells, float value) {
        int corners = outline.length / 2;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 1; i < outline.length; i += 2) {
            minY = Math.min(minY, outline[i]);
            maxY = Math.max(maxY, outline[i]);
        }
        float[] crossings = new float[corners];
        for (int j = Math.max(0, Math.floorDiv(minY - STEP / 2, STEP) - fromY); j < high; j++) {
            float y = (fromY + j) * STEP + STEP / 2f;
            if (y > maxY) {
                break;
            }
            int crossed = 0;
            for (int a = 0, b = corners - 1; a < corners; b = a++) {
                float ay = outline[a * 2 + 1], by = outline[b * 2 + 1];
                if (ay > y != by > y) {
                    crossings[crossed++] = outline[a * 2] + (y - ay) / (by - ay) * (outline[b * 2] - outline[a * 2]);
                }
            }
            Arrays.sort(crossings, 0, crossed);
            for (int k = 0; k + 1 < crossed; k += 2) {
                // The cells whose middles lie between two crossings, which is inside.
                int first = (int) Math.ceil((crossings[k] - STEP / 2f) / STEP) - fromX, last = (int) Math.floor((crossings[k + 1] - STEP / 2f) / STEP) - fromX;
                for (int i = Math.max(0, first); i <= Math.min(wide - 1, last); i++) {
                    cells[j * wide + i] = Math.max(cells[j * wide + i], value);
                }
            }
        }
    }

    /** Two passes of a box SPREAD cells each way, along x and then along y, the edge cell standing for what is past it. */
    private static void spread(float[] cells, int wide, int high) {
        float[] line = new float[Math.max(wide, high)];
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < high; j++) {
                box(cells, j * wide, 1, wide, line);
            }
            for (int i = 0; i < wide; i++) {
                box(cells, i, wide, high, line);
            }
        }
    }

    private static void box(float[] cells, int first, int stride, int count, float[] line) {
        for (int k = 0; k < count; k++) {
            line[k] = cells[first + k * stride];
        }
        double sum = 0;
        for (int k = -SPREAD; k <= SPREAD; k++) {
            sum += line[Math.clamp(k, 0, count - 1)];
        }
        for (int k = 0; k < count; k++) {
            cells[first + k * stride] = (float) (sum / (2 * SPREAD + 1));
            sum += line[Math.min(k + SPREAD + 1, count - 1)] - line[Math.max(k - SPREAD, 0)];
        }
    }

    /** How much of country (x, y) is in, 0 to 1. Any thread. */
    static float at(float x, float y) {
        Field now = field;
        float inX = (x - STEP / 2f) / STEP - now.fromX, inY = (y - STEP / 2f) / STEP - now.fromY;
        int i = (int) Math.floor(inX), j = (int) Math.floor(inY);
        if (i < 0 || j < 0 || i >= now.wide - 1 || j >= now.high - 1) {
            return 0f;
        }
        float a = inX - i, b = inY - j;
        float[] cells = now.cells;
        int at = j * now.wide + i;
        return (cells[at] * (1f - a) + cells[at + 1] * a) * (1f - b) + (cells[at + now.wide] * (1f - a) + cells[at + now.wide + 1] * a) * b;
    }
}
