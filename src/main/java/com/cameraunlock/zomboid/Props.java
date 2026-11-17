package com.cameraunlock.zomboid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import zombie.core.textures.Texture;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * What the game lays over a piece of furniture the mod models, made: the overlays TileOverlays sets out at random over a
 * piece and ContainerOverlays paints over a container, each a picture the game draws over the piece's sprite, stood on
 * the model as props of the mod's own.
 *
 * The props come in batches, one to a family of pieces, each a pair of files in the props folder of their own art jar
 * that tools/models/props/BATCH.py makes: BATCH.bin.gz holds its models, which Models reads with the furniture's, and
 * BATCH.txt says what each overlay makes of each piece it lies on, one line a thing, tab separated:
 *
 *   swap       OVERLAY  HOST  MODEL  X Y Z   the piece is drawn as MODEL in place of HOST, a cupboard with its door left off
 *   prop       OVERLAY  HOST  MODEL  X Y Z  DYE   MODEL stands on the piece
 *   marks      OVERLAY  HOST  MODEL[,MODEL...]  OX OY OZ  UX UY UZ  VX VY VZ  ACROSS ALONG  DYED
 *              a stain, a scratch or a scuff stands at each of ACROSS by ALONG points spread over the parallelogram O,
 *              O + U, O + V, where the overlay paints that point, coloured as the overlay paints it there
 *
 * HOST is the name of the model the piece is drawn as, a lit drum's burn_barrel and not its barrel; a prop stands only
 * on that one. Every place is in the host model's metres, from
 * the middle of its footprint on its floor with its back to +Y, as tools/models/furniture.py builds it, so a prop stands
 * wherever and however the piece is turned, scaled with it, and from the host's own geometry where the game gives the
 * piece no surface height. A prop's model, and a model swapped in, was made where it stands in that space, and (X, Y, Z)
 * is where its origin is: the middle of its footprint on its lowest point, as furniture.py's finish moves it. DYE is "host" for a prop coloured as the piece is, "plain" for one in its own
 * colours, or "over:MASK:X,Y,Z" for one whose surfaces in the bit MASK take the colour the overlay paints at (X, Y, Z).
 * A mark stands with the middle of its footprint at its point, its surfaces in DYED coloured from the overlay there.
 *
 * Where a piece's overlay has a batch here the props stand in place of the overlay's picture (FurnishingBuilder.standsOver),
 * and where it has none the picture still stands. A piece on more than one square has the overlays of each of its
 * squares, and stands the props of each from the square that stands its model. A stocked fitting and a display case
 * have goods of their own (ShopFittings, DisplayCases), and no batch dresses them. All of it is the Own furniture
 * switch's, and then Own.props': with furniture off no piece is modelled and the game draws its own sprites and
 * overlays, and with props off the game's pictures stand over the models as cards.
 */
final class Props {
    private Props() {}

    static final String HOST_DYE = "host", PLAIN_DYE = "plain", OVER_DYE = "over:";

    /** A prop on a piece: its model, where it stands, how it is coloured, as the batch's file says it. */
    record Prop(String model, float x, float y, float z, String dye, int dyed, float dyeX, float dyeY, float dyeZ) {}

    /** Marks spread over a face of a piece, as the batch's file says them. */
    record Marks(String[] models, float[] origin, float[] across, float[] along, int acrossCount, int alongCount, int dyed) {}

    /** What one overlay makes of one host: the model the host is drawn as in its place or null, its props and its marks. */
    static final class Dressing {
        String swap;
        final float[] swapAt = new float[3];
        final List<Prop> props = new ArrayList<>();
        final List<Marks> marks = new ArrayList<>();
    }

    /** A batch's file as read: its lines by overlay, then by host. */
    record Batch(String name, Map<String, Map<String, Dressing>> dressings) {}

    private static volatile Map<String, Map<String, Dressing>> made;

    /** Every batch's lines, by overlay and host, read once whichever threads ask. */
    private static Map<String, Map<String, Dressing>> made() {
        Map<String, Map<String, Dressing>> all = made;
        if (all == null) {
            synchronized (Props.class) {
                all = made;
                if (all == null) {
                    all = new HashMap<>();
                    for (String batch : Models.propBatches()) {
                        Batch read = read(batch);
                        standsOnlyModelsTheJarHas(read);
                        for (Map.Entry<String, Map<String, Dressing>> overlay : read.dressings().entrySet()) {
                            Map<String, Dressing> hosts = all.computeIfAbsent(overlay.getKey(), key -> new HashMap<>());
                            for (Map.Entry<String, Dressing> host : overlay.getValue().entrySet()) {
                                if (hosts.put(host.getKey(), host.getValue()) != null) {
                                    throw new IllegalStateException("two batches make " + overlay.getKey() + " on " + host.getKey() + ", the second " + batch);
                                }
                            }
                        }
                    }
                    made = all;
                }
            }
        }
        return all;
    }

    /** Refuses a batch whose file names a model, or a host, the jar's models have none of. */
    static void standsOnlyModelsTheJarHas(Batch batch) {
        for (Map.Entry<String, Map<String, Dressing>> overlay : batch.dressings().entrySet()) {
            for (Map.Entry<String, Dressing> host : overlay.getValue().entrySet()) {
                List<String> models = named(host.getValue());
                models.add(host.getKey());
                for (String model : models) {
                    if (!Models.has(model)) {
                        throw new IllegalStateException(Models.propPlacesOf(batch.name()) + " names " + model + " for " + overlay.getKey() + " on " + host.getKey()
                                + ", and the jar's models, " + Models.propModelsOf(batch.name()) + " among them, have none of that name");
                    }
                }
            }
        }
    }

    /** Every model a dressing names: what it swaps its host for, its props and its marks. */
    static List<String> named(Dressing dressing) {
        List<String> out = new ArrayList<>();
        if (dressing.swap != null) {
            out.add(dressing.swap);
        }
        for (Prop prop : dressing.props) {
            out.add(prop.model);
        }
        for (Marks marks : dressing.marks) {
            out.addAll(List.of(marks.models));
        }
        return out;
    }

    /** One batch's file, read from the jar. */
    static Batch read(String batch) {
        String resource = Models.propPlacesOf(batch);
        try (InputStream in = Props.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("the jar has no " + resource);
            }
            return parse(batch, new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII)));
        } catch (IOException e) {
            throw new UncheckedIOException(resource + " could not be read", e);
        }
    }

    static Batch parse(String batch, BufferedReader in) throws IOException {
        Map<String, Map<String, Dressing>> dressings = new HashMap<>();
        String line;
        int number = 0;
        while ((line = in.readLine()) != null) {
            number++;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cells = line.split("\t", -1);
            String where = batch + ".txt line " + number;
            switch (cells[0]) {
                case "swap" -> {
                    need(cells, 5, where);
                    Dressing dressing = dressing(dressings, cells);
                    if (dressing.swap != null) {
                        throw new IllegalStateException(where + ": " + cells[1] + " on " + cells[2] + " is swapped twice");
                    }
                    dressing.swap = cells[3];
                    System.arraycopy(floats(cells[4], 3, where), 0, dressing.swapAt, 0, 3);
                }
                case "prop" -> {
                    need(cells, 6, where);
                    float[] at = floats(cells[4], 3, where);
                    String dye = cells[5];
                    int dyed = 0;
                    float[] dyeAt = new float[3];
                    if (dye.startsWith(OVER_DYE)) {
                        String[] parts = dye.substring(OVER_DYE.length()).split(":");
                        if (parts.length != 2) {
                            throw new IllegalStateException(where + ": a dye from the overlay is over:MASK:X,Y,Z, not " + dye);
                        }
                        dyed = Integer.parseInt(parts[0]);
                        dyeAt = floats(parts[1].replace(',', ' '), 3, where);
                        dye = OVER_DYE;
                    } else if (!dye.equals(HOST_DYE) && !dye.equals(PLAIN_DYE)) {
                        throw new IllegalStateException(where + ": a prop's dye is host, plain or over:MASK:X,Y,Z, not " + dye);
                    }
                    dressing(dressings, cells).props.add(new Prop(cells[3], at[0], at[1], at[2], dye, dyed, dyeAt[0], dyeAt[1], dyeAt[2]));
                }
                case "marks" -> {
                    need(cells, 9, where);
                    String[] counts = cells[7].trim().split(" +");
                    if (counts.length != 2 || !counts[0].matches("[1-9][0-9]*") || !counts[1].matches("[1-9][0-9]*")) {
                        throw new IllegalStateException(where + ": marks are spread ACROSS by ALONG, two whole numbers over 0, not " + cells[7]);
                    }
                    dressing(dressings, cells).marks.add(new Marks(cells[3].split(","), floats(cells[4], 3, where), floats(cells[5], 3, where),
                            floats(cells[6], 3, where), Integer.parseInt(counts[0]), Integer.parseInt(counts[1]), Integer.parseInt(cells[8])));
                }
                default -> throw new IllegalStateException(where + " is a " + cells[0] + ", and a line is swap, prop or marks");
            }
        }
        return new Batch(batch, dressings);
    }

    private static Dressing dressing(Map<String, Map<String, Dressing>> dressings, String[] cells) {
        return dressings.computeIfAbsent(cells[1], key -> new HashMap<>()).computeIfAbsent(cells[2], key -> new Dressing());
    }

    private static void need(String[] cells, int count, String where) {
        if (cells.length != count) {
            throw new IllegalStateException(where + " has " + cells.length + " cells, and a " + cells[0] + " line has " + count);
        }
    }

    private static float[] floats(String cell, int count, String where) {
        String[] parts = cell.trim().split(" +");
        if (parts.length != count) {
            throw new IllegalStateException(where + ": " + count + " numbers wanted, and it has " + cell);
        }
        float[] out = new float[count];
        for (int i = 0; i < count; i++) {
            out[i] = Float.parseFloat(parts[i]);
        }
        return out;
    }

    /** What the overlay makes of a piece drawn as host, or null where no batch makes it. */
    static Dressing of(String overlay, String host) {
        Map<String, Dressing> hosts = made().get(overlay);
        return hosts == null ? null : hosts.get(host);
    }

    /**
     * Whether some batch makes the overlay on the piece, which is drawn as drawn (FurnishingBuilder.drawnAt), the model
     * addFurniture stands the props on. A square of a piece that stands nothing of its own, drawn null, is the piece the
     * square that stands it is, as that is drawn with its basin or without. A stocked fitting and a case are dressed by
     * goods of their own.
     */
    static boolean makes(String overlay, Furniture piece, Models.Model drawn) {
        Map<String, Dressing> hosts = made().get(overlay);
        Furniture stands = piece.model == null && piece.partner != null ? Furniture.known(IsoSpriteManager.instance.namedMap.get(piece.partner)) : piece;
        if (hosts == null || stands == null || stands.model == null || stands.stocked || stands.displayCase) {
            return false;
        }
        if (drawn != null) {
            return hosts.containsKey(drawn.name);
        }
        return hosts.containsKey(stands.model.name) || stands.withBasin != null && hosts.containsKey(stands.withBasin.name);
    }

    /** A square of a piece the props of whose overlays stand: its object, the square, and what the game lays over it. */
    static final class Square {
        IsoObject object;
        int x, y;
        final List<IsoSprite> overlays = new ArrayList<>();
    }

    /** What the game lays over an object: TileOverlays' into its attached sprites, ContainerOverlays' as its overlay. */
    static List<IsoSprite> overlaysOn(IsoObject object, List<IsoSprite> out) {
        out.clear();
        List<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
        if (attached != null) {
            for (int i = 0; i < attached.size(); i++) {
                IsoSpriteInstance instance = attached.get(i);
                if (instance != null && instance.parentSprite != null && instance.parentSprite.getName() != null) {
                    out.add(instance.parentSprite);
                }
            }
        }
        IsoSprite overlay = object.getOverlaySprite();
        if (overlay != null && overlay.getName() != null) {
            out.add(overlay);
        }
        return out;
    }

    /**
     * What of the overlays over the first count squares has a piece of host model drawn as another model, or null where
     * none does. Two that would draw it as different models are refused: PropsTests holds every batch to one.
     */
    static Dressing swapOf(List<Square> squares, int count, Models.Model host) {
        Dressing swap = null;
        for (int i = 0; i < count; i++) {
            for (IsoSprite overlay : squares.get(i).overlays) {
                Dressing dressing = of(overlay.getName(), host.name);
                if (dressing != null && dressing.swap != null) {
                    if (swap != null && !swap.swap.equals(dressing.swap)) {
                        throw new IllegalStateException(host.name + " is to be drawn as both " + swap.swap + " and " + dressing.swap + ", the second under "
                                + overlay.getName());
                    }
                    swap = dressing;
                }
            }
        }
        return swap;
    }

    /** Moves where a swapped model stands, from its host's (centreX, centreY, foot) in at, to where its own origin is. */
    static void moveToSwap(Dressing swap, int turns, float scale, float scaleUp, float[] at) {
        float[] turned = TURNED.get();
        Models.turn(turns, swap.swapAt[0] * scale, swap.swapAt[1] * scale, turned);
        at[0] += turned[0];
        at[1] += turned[1];
        at[2] += swap.swapAt[2] * scaleUp;
    }

    /** Where a piece stands, as FurnishingBuilder stands it, which its props stand by. */
    static final class Stand {
        SpriteGeometry.Frame light;
        int litX, litY, across, down;
        /** Where on the map the host model's origin stands. */
        float centreX, centreY, foot;
        /** How far the piece's model is raised off its square, as foot is measured: Furniture.raised. */
        float raised;
        int turns;
        float scale, scaleUp;
        int dyeTexture, dyed, alsoDyed;
        float dyeU, dyeV, alsoU, alsoV;
    }

    private static final ThreadLocal<float[]> TURNED = ThreadLocal.withInitial(() -> new float[2]);
    private static final ThreadLocal<SpriteGeometry.Frame> OVER_FRAME = ThreadLocal.withInitial(SpriteGeometry.Frame::new);

    /**
     * Whether the game has a picture to draw of every overlay over the first count squares whose props, on a piece of
     * host model, take their colours from it. Where it has not, the piece is not ready, as FurnishingBuilder.ready has
     * it, and the game's sprite and its overlays' pictures stand in its place until the square is built again.
     */
    static boolean picturesReady(List<Square> squares, int count, String host) {
        for (int i = 0; i < count; i++) {
            Square square = squares.get(i);
            for (IsoSprite overlay : square.overlays) {
                Dressing dressing = of(overlay.getName(), host);
                if (dressing != null && needsPicture(dressing) && pictureOf(square.object, overlay) == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Stands the props every overlay over a square of a piece makes of the piece, whose model before any swap is host,
     * where the piece stands. The overlays' pictures over the square give the props' colours, and the piece is stood
     * only once each is there to be read (picturesReady); spriteScale is the frame's sprite scale.
     */
    static void add(WorldScene.Frame build, Square square, String host, Stand at, float spriteScale) {
        for (IsoSprite overlay : square.overlays) {
            Dressing dressing = of(overlay.getName(), host);
            if (dressing == null) {
                continue;
            }
            Texture painted = null;
            if (needsPicture(dressing)) {
                painted = pictureOf(square.object, overlay);
                if (painted == null) {
                    throw new IllegalStateException(overlay.getName() + " over " + square.x + "," + square.y + " has no picture, which picturesReady found it had");
                }
            }
            SpriteGeometry.Frame frame = OVER_FRAME.get();
            if (painted != null) {
                frame.scale = spriteScale;
                WorldScene.frameOf(frame, square.object, painted);
            }
            for (Prop prop : dressing.props) {
                float[] map = place(at, prop.x, prop.y);
                float mapX = map[0], mapY = map[1];
                int texture = WorldScene.PLAIN, dyed = 0, alsoDyed = 0;
                float u = 0f, v = 0f, alsoU = 0f, alsoV = 0f;
                if (prop.dye.equals(HOST_DYE)) {
                    texture = at.dyeTexture;
                    dyed = at.dyed;
                    alsoDyed = at.alsoDyed;
                    u = at.dyeU;
                    v = at.dyeV;
                    alsoU = at.alsoU;
                    alsoV = at.alsoV;
                } else if (prop.dye.equals(OVER_DYE)) {
                    float[] dyeAt = place(at, prop.dyeX, prop.dyeY);
                    float a = dyeAt[0] - square.x, b = dyeAt[1] - square.y, high = heightOnPicture(at, prop.dyeZ);
                    texture = painted.getID();
                    dyed = prop.dyed;
                    u = SpriteGeometry.textureU(frame, a, b);
                    v = SpriteGeometry.textureV(frame, a, b, high);
                }
                build.placed.add(Models.get(prop.model), at.light, at.litX, at.litY, mapX, mapY, at.foot + prop.z * at.scaleUp, at.turns, at.scale, at.scaleUp,
                        texture, dyed, u, v, alsoDyed, alsoU, alsoV);
                if (at.across > 1 || at.down > 1) {
                    build.placed.span(at.across, at.down);
                }
            }
            for (Marks marks : dressing.marks) {
                for (int i = 0; i < marks.acrossCount; i++) {
                    for (int j = 0; j < marks.alongCount; j++) {
                        float s = (i + 0.5f) / marks.acrossCount, t = (j + 0.5f) / marks.alongCount;
                        float x = marks.origin[0] + s * marks.across[0] + t * marks.along[0];
                        float y = marks.origin[1] + s * marks.across[1] + t * marks.along[1];
                        float z = marks.origin[2] + s * marks.across[2] + t * marks.along[2];
                        float[] map = place(at, x, y);
                        float mapX = map[0], mapY = map[1];
                        float a = mapX - square.x, b = mapY - square.y, high = heightOnPicture(at, z);
                        if (!painted.isMaskSet(Math.round(SpriteGeometry.frameU(frame, a, b)), Math.round(SpriteGeometry.frameV(frame, a, b, high)))) {
                            continue;
                        }
                        Models.Model mark = Models.get(marks.models[(i * 31 + j * 17) % marks.models.length]);
                        build.placed.add(mark, at.light, at.litX, at.litY, mapX, mapY, at.foot + z * at.scaleUp, at.turns, at.scale, at.scaleUp, painted.getID(),
                                marks.dyed, SpriteGeometry.textureU(frame, a, b), SpriteGeometry.textureV(frame, a, b, high), 0, 0f, 0f);
                    }
                }
            }
        }
    }

    private static boolean needsPicture(Dressing dressing) {
        if (!dressing.marks.isEmpty()) {
            return true;
        }
        for (Prop prop : dressing.props) {
            if (prop.dye.equals(OVER_DYE)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The picture of an overlay over the object, as the game draws it, or null where it has none to draw. The sprite is
     * the one overlaysOn found, not looked up again: the game may take an overlay off while the build runs, a container
     * emptied as it is looted.
     */
    private static Texture pictureOf(IsoObject object, IsoSprite overlay) {
        Texture texture = overlay.getTextureForCurrentFrame(object.getDir());
        return WorldScene.usable(texture) ? texture : null;
    }

    /** The point (x, y) of the host model's floor, in the model's metres, on the map: into a scratch array. */
    private static float[] place(Stand at, float x, float y) {
        float[] turned = TURNED.get();
        Models.turn(at.turns, x * at.scale, y * at.scale, turned);
        turned[0] += at.centreX;
        turned[1] += at.centreY;
        return turned;
    }

    /**
     * How high, in levels over the floor of the object's square, the overlay's picture paints a point z metres up the host
     * model: as the piece's own sprite is painted, from the square's floor, whatever the ground under it is lifted by.
     */
    static float heightOnPicture(Stand at, float z) {
        return (z * at.scaleUp + at.raised) / ViewCamera.LEVEL_HEIGHT;
    }
}
