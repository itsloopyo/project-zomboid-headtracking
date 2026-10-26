package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import zombie.core.properties.PropertyContainer;
import zombie.iso.SpriteDetails.IsoFlagType;
import zombie.iso.sprite.IsoSprite;

/**
 * Where shots have struck, for the first person picture to show: what flies off a surface a
 * shot meets, by what the surface is made of, and the hole it leaves where the surface is one
 * the picture builds and that stays put.
 *
 * Whoever finds a shot's end (ImpactFinder, on the game's logic thread) adds it here, which is
 * when the impact starts, and the render thread takes what has been added at the start of a
 * frame: what flies is worked out on the graphics card from how long ago the shot struck. A shot
 * fired while no frame takes them, as in the game's own view, has thrown up what it will by its
 * own clock and not the frame's, and first person shows what is left of it, its hole at least.
 * Holes are kept for as long as the save is played, the oldest given up past MOST_HOLES.
 */
final class Impacts {
    /** What a surface is made of, as far as what a shot knocks out of it goes. */
    static final int NONE = -1, MASONRY = 0, WOOD = 1, METAL = 2, FLESH = 3, SOIL = 4, SNOW = 5, WATER = 6, GLASS = 7, SOFT = 8;
    static final int KINDS = 9;

    /** How many impacts are held while what flies from them is in the air: the longest lasts LONGEST seconds. */
    static final int MOST = 128;
    static final float LONGEST = 2.6f;
    /** How many holes are kept. */
    static final int MOST_HOLES = 400;

    /**
     * A shot that struck: where, in the picture's measure (x and y in tiles, up in tiles over
     * the flat map); the way the surface faces there; the way the shot was going; the height
     * of the floor under the place, where what falls comes to rest; what the surface is made
     * of; how big a mark the round makes, 1 for a handgun's; whether it leaves a hole; and the
     * way the grain or the boards of the surface run, along it.
     */
    record Struck(float x, float y, float up, float normalX, float normalY, float normalUp, float wayX, float wayY, float wayUp,
            float floor, int kind, float size, boolean hole, float grainX, float grainY, float grainUp) {}

    /** A shot that struck and when, by System.nanoTime. */
    private record Added(Struck struck, long at) {}

    private static final ArrayList<Added> added = new ArrayList<>();

    /** The impacts taken, in a ring: each with the time it struck and a number no other has had. Render thread. */
    static final Struck[] held = new Struck[MOST];
    static final long[] born = new long[MOST];
    static final int[] serial = new int[MOST];
    private static int next, numbered;

    /** The holes, in a ring, and how many of them there are. Render thread. */
    static final Struck[] holes = new Struck[MOST_HOLES];
    static int holeCount;
    private static int nextHole;
    /** Raised each time the holes change, for a drawing that keeps them to see it. */
    static int holesChanged;

    private Impacts() {}

    /** A shot has struck. Any thread. While no picture takes them, as in the game's own view, no more are held than there are places for holes. */
    static void add(Struck struck) {
        long at = System.nanoTime();
        synchronized (added) {
            if (added.size() == MOST_HOLES) {
                added.removeFirst();
            }
            added.add(new Added(struck, at));
        }
    }

    /** Takes what has struck since the last frame. Render thread. */
    static void take() {
        synchronized (added) {
            if (forgotten) {
                forgotten = false;
                added.clear();
                Arrays.fill(held, null);
                Arrays.fill(holes, null);
                holeCount = 0;
                nextHole = 0;
                holesChanged++;
            }
            for (int i = 0; i < added.size(); i++) {
                Added each = added.get(i);
                Struck struck = each.struck();
                held[next] = struck;
                born[next] = each.at();
                serial[next] = ++numbered;
                next = (next + 1) % MOST;
                if (struck.hole()) {
                    holes[nextHole] = struck;
                    nextHole = (nextHole + 1) % MOST_HOLES;
                    holeCount = Math.min(holeCount + 1, MOST_HOLES);
                    holesChanged++;
                }
            }
            added.clear();
        }
    }

    /** Set when the save the impacts and holes were in has been left, for the render thread to forget them. */
    private static volatile boolean forgotten;

    /** The save has been left: what struck in it is forgotten before the next frame is drawn. Any thread. */
    static void forget() {
        forgotten = true;
    }

    private static boolean gapSaid;

    /** Says once why shots leave no mark on this build of the game, if they do not. */
    static void sayGap() {
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.impactsGap();
            if (gap != null) {
                Log.info("shots leave no mark where they strike: " + gap);
            }
        }
    }

    /**
     * What a surface the game calls by one of its MaterialType words is made of. The game picks the
     * sound of a bullet striking by the same word. NONE for Default and for a word it does not have.
     */
    static int ofMaterialType(String word) {
        if (word == null) {
            return NONE;
        }
        return switch (word) {
            case "Concrete", "Plaster", "Stone", "Brick", "Cinderblock", "Ceramic" -> MASONRY;
            case "Wood", "Wood_Solid" -> WOOD;
            case "Metal", "Metal_Large", "Metal_Light", "Metal_Solid" -> METAL;
            case "Flesh", "Flesh_Hollow" -> FLESH;
            case "Dirt", "Grass", "Gravel", "Sand" -> SOIL;
            case "Snow" -> SNOW;
            case "Glass", "Glass_Light", "Glass_Solid" -> GLASS;
            case "Plastic", "Rubber", "Fabric", "Carpet" -> SOFT;
            default -> NONE;
        };
    }

    /**
     * What ground is made of by the game's FloorMaterial word for it, which its outdoor floors
     * have and its indoor ones do not: road and pavement are Road_01 to Road_07.
     */
    static int ofFloorMaterial(String word) {
        if (word == null) {
            return NONE;
        }
        if (word.startsWith("Road_")) {
            return MASONRY;
        }
        if (word.startsWith("Grass") || word.startsWith("Dirt") || word.equals("Clay") || word.equals("Sand") || word.equals("Burnt")) {
            return SOIL;
        }
        return word.equals("Water") ? WATER : NONE;
    }

    /** What a surface the picture draws as one of WorldScene's materials is made of. */
    static int ofDrawn(float material) {
        if (material == WorldScene.WOOD || material == WorldScene.PLANKS || material == WorldScene.BOARDS || material == WorldScene.BARK) {
            return WOOD;
        }
        if (material == WorldScene.CARPET || material == WorldScene.FLOOR_CARPET) {
            return SOFT;
        }
        if (material == WorldScene.EARTH) {
            return SOIL;
        }
        if (material == WorldScene.GLASS) {
            return GLASS;
        }
        if (material == WorldScene.WIRE || material == WorldScene.MESH) {
            return METAL;
        }
        return material == 0f ? NONE : MASONRY;
    }

    /**
     * What the surface a sprite shows is made of: the game's own word for it first, the
     * ground's word for a floor, water, then what the picture draws it as. A wall or a floor
     * that says nothing is plaster or concrete, which most of the game's are.
     */
    static int of(IsoSprite sprite, boolean isFloor) {
        if (sprite == null) {
            return MASONRY;
        }
        PropertyContainer properties = sprite.getProperties();
        int kind = ofMaterialType(properties.get("MaterialType"));
        if (kind == NONE && isFloor) {
            kind = ofFloorMaterial(properties.get("FloorMaterial"));
            if (kind == NONE && properties.has(IsoFlagType.water)) {
                kind = WATER;
            }
        }
        if (kind == NONE) {
            kind = ofDrawn(isFloor ? WorldScene.materialOf(sprite.getName(), true) : WorldScene.materialOf(sprite));
        }
        return kind == NONE ? MASONRY : kind;
    }

    /** Snow lies on open ground at least this deep, 0 to 1, for a shot into it to throw up snow. */
    static final float SNOW_SHOWS = 0.35f;

    /** What is thrown up from open ground with snow lying as deep as SkyState.snowLying says. */
    static int underSnow(int kind, float snowLying) {
        return snowLying >= SNOW_SHOWS && (kind == SOIL || kind == MASONRY || kind == SOFT) ? SNOW : kind;
    }

    /**
     * How far along a line the near face of a wall is. The wall is drawn as a skin either side of
     * the edge between two squares (WorldScene.WALL_HALF), the edge being `distance` along the
     * line, and `across` is how much of the line's unit way goes across the wall.
     */
    static float toSkin(float distance, float across) {
        return Math.max(distance - WorldScene.WALL_HALF / Math.max(Math.abs(across), 1e-4f), 0f);
    }

    /** Whether a shot leaves a hole in what it strikes: in what stays put and keeps a mark. */
    static boolean holed(int kind) {
        return kind == MASONRY || kind == WOOD || kind == METAL || kind == SOFT;
    }
}
