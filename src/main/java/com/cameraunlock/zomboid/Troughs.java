package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Map;
import se.krka.kahlua.vm.KahluaTable;
import se.krka.kahlua.vm.KahluaTableIterator;
import zombie.Lua.LuaManager;
import zombie.core.textures.Texture;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.objects.IsoFeedingTrough;
import zombie.util.list.PZArrayList;

/** What the game paints in a farm trough, made: kept out of Farm, which the tests load without the game's classes. */
final class Troughs {
    private Troughs() {}

    /** How far below the top of what is in a trough its colour is taken, in metres, so as to fall inside it from the game's view. */
    private static final float FILL_DYE_DEPTH = 0.02f;
    /** Where across its square, each way, and in how many steps up to the rim, a trough's painting is looked at for its colour. */
    private static final float[] DYE_ACROSS = {0.5f, 0.3f, 0.7f, 0.15f, 0.85f};
    private static final int DYE_STEPS = 4;
    /** The game's tables in FeedingTroughDef of the sprites it paints in a trough, and what each one is: feed or water, under or from half full. */
    private static final String[][] OVERLAYS = {{"spriteFoodOverlay1", "_feed", "_low"}, {"spriteFoodOverlay2", "_feed", "_full"},
            {"spriteWaterOverlay1", "_water", "_low"}, {"spriteWaterOverlay2", "_water", "_full"}};
    private static Map<String, String[]> held;

    /**
     * Stands what the game paints in a trough in it: feed in lumps or water, coloured as the game paints it. A stock tank holds
     * it shallow or near its rim as the sprite the game paints over it is the one for under or from half full; a wooden
     * trough holds a layer just under its rim either way. Nothing stands while the game paints nothing. x and y are the
     * trough's first square and z its level, centreX and centreY where on that square the trough stands, foot the floor under it.
     */
    static void addFill(WorldScene.Frame build, Furniture piece, Models.Model model, int x, int y, int z, float centreX, float centreY, float foot) {
        boolean tank = model.name.startsWith("stock_tank");
        int wide = piece.grid == null ? 1 : piece.grid.getWidth(), deep = piece.grid == null ? 1 : piece.grid.getHeight();
        float floor = (tank ? Farm.TANK_FLOOR : Farm.TROUGH_FLOOR) * piece.scaleUp;
        float rim = model.height() * piece.scaleUp / ViewCamera.LEVEL_HEIGHT;
        SpriteGeometry.Frame frame = build.dyeFrame;
        frame.scale = build.frame.scale;
        // The game paints a little feed as a heap at one end, so the colour is taken from the first place on the trough's
        // squares, from the fill's top up to the rim, that the painting covers. Where it covers none it is nothing to see.
        for (int i = 0; i < wide; i++) {
            for (int j = 0; j < deep; j++) {
                IsoObject trough = troughOn(build, x + i, y + j, z);
                if (trough == null || trough.getOverlaySprite() == null) {
                    continue;
                }
                Texture painted = trough.getOverlaySprite().getTextureForCurrentFrame(trough.getDir());
                if (!WorldScene.usable(painted)) {
                    continue;
                }
                String[] what = held().get(trough.getOverlaySprite().getName());
                if (what == null) {
                    throw new IllegalStateException("the trough at " + (x + i) + "," + (y + j) + "," + z + " is painted with "
                            + trough.getOverlaySprite().getName() + ", which no table in FeedingTroughDef names");
                }
                Models.Model body = Models.get(model.name + what[1] + (tank ? what[2] : ""));
                WorldScene.frameOf(frame, trough, painted);
                float low = (floor + body.height() * piece.scaleUp - FILL_DYE_DEPTH) / ViewCamera.LEVEL_HEIGHT;
                for (int step = 0; step <= DYE_STEPS; step++) {
                    float high = low + (rim - low) * step / DYE_STEPS;
                    for (float a : DYE_ACROSS) {
                        for (float b : DYE_ACROSS) {
                            if (painted.isMaskSet(Math.round(SpriteGeometry.frameU(frame, a, b)), Math.round(SpriteGeometry.frameV(frame, a, b, high)))) {
                                build.placed.add(body, build.frame, x, y, x + centreX, y + centreY, foot + floor, piece.turns, piece.scale, piece.scaleUp,
                                        painted.getID(), 1 << (what[1].equals("_feed") ? Models.CLOTH : Models.ENAMEL), SpriteGeometry.textureU(frame, a, b),
                                        SpriteGeometry.textureV(frame, a, b, high), 0, 0f, 0f);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private static IsoObject troughOn(WorldScene.Frame build, int x, int y, int z) {
        IsoGridSquare square = build.cell.getGridSquare(x, y, z);
        if (square == null) {
            return null;
        }
        PZArrayList<IsoObject> objects = square.getObjects();
        for (int i = 0; i < objects.size(); i++) {
            if (objects.get(i) instanceof IsoFeedingTrough) {
                return objects.get(i);
            }
        }
        return null;
    }

    /** As the game leaves a save: the next one's Lua may be another mod list's, with another FeedingTroughDef. */
    static synchronized void forget() {
        held = null;
    }

    /** Every sprite the game paints in a trough of any kind, to what it is (OVERLAYS), read once a save from the game's FeedingTroughDef. */
    private static synchronized Map<String, String[]> held() {
        if (held == null) {
            Map<String, String[]> read = new HashMap<>();
            KahluaTableIterator kinds = ((KahluaTable) LuaManager.env.rawget("FeedingTroughDef")).iterator();
            while (kinds.advance()) {
                KahluaTable kind = (KahluaTable) kinds.getValue();
                for (String[] overlay : OVERLAYS) {
                    if (kind.rawget(overlay[0]) instanceof KahluaTable sprites) {
                        KahluaTableIterator each = sprites.iterator();
                        while (each.advance()) {
                            read.put((String) each.getValue(), overlay);
                        }
                    }
                }
            }
            held = read;
        }
        return held;
    }
}
