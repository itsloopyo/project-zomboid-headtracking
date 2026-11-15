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

/**
 * What the game lays over a piece of furniture the mod models, made: the overlays TileOverlays sets out at random over a
 * piece and ContainerOverlays paints over a container, each a picture the game draws over the piece's sprite, stood on
 * the model as props of the mod's own.
 *
 * The props come in batches, one to a family of pieces, each a pair of files in the jar's models folder that
 * tools/models/props/BATCH.py makes: props-BATCH.bin.gz holds its models, which Models reads with the furniture's, and
 * props-BATCH.txt says what each overlay makes of each piece it lies on, one line a thing, tab separated:
 *
 *   swap       OVERLAY  HOST  MODEL  X Y Z   the piece is drawn as MODEL in place of HOST, a cupboard with its door left off
 *   prop       OVERLAY  HOST  MODEL  X Y Z  DYE   MODEL stands on the piece
 *   marks      OVERLAY  HOST  MODEL[,MODEL...]  OX OY OZ  UX UY UZ  VX VY VZ  ACROSS ALONG  DYED
 *              a stain, a scratch or a scuff stands at each of ACROSS by ALONG points spread over the parallelogram O,
 *              O + U, O + V, where the overlay paints that point, coloured as the overlay paints it there
 *
 * HOST is the name of the piece's model; a prop stands only on that one. Every place is in the host model's metres, from
 * the middle of its footprint on its floor with its back to +Y, as tools/models/furniture.py builds it, so a prop stands
 * wherever and however the piece is turned, scaled with it, and from the host's own geometry where the game gives the
 * piece no surface height. A prop's model, and a model swapped in, was made where it stands in that space, and (X, Y, Z)
 * is where its origin is: the middle of its footprint on its lowest point, as furniture.py's finish moves it. DYE is "host" for a prop coloured as the piece is, "plain" for one in its own
 * colours, or "over:MASK:X,Y,Z" for one whose surfaces in the bit MASK take the colour the overlay paints at (X, Y, Z).
 * A mark stands with the middle of its footprint at its point, its surfaces in DYED coloured from the overlay there.
 *
 * Where a piece's overlay has a batch here the props stand in place of the overlay's picture (FurnishingBuilder.standsOver),
 * and where it has none the picture still stands. All of it is the Own furniture switch's: with it off no piece is
 * modelled, and the game draws its own sprites and overlays.
 */
final class Props {
    private Props() {}

    /** The list of batches the build writes beside this class: see scripts/build.ps1. */
    private static final String BATCHES = "props-batches.txt";
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

    private static volatile List<String> batchNames;
    private static volatile Map<String, Map<String, Dressing>> made;

    /** The names of the batches of props the jar holds, in order. */
    static List<String> batches() {
        List<String> names = batchNames;
        if (names == null) {
            List<String> read = new ArrayList<>();
            try (InputStream in = Props.class.getResourceAsStream(BATCHES)) {
                if (in == null) {
                    throw new IllegalStateException("the jar has no " + BATCHES + " beside " + Props.class.getName() + ": the build writes it");
                }
                for (String line : new String(in.readAllBytes(), StandardCharsets.US_ASCII).split("\\R")) {
                    if (!line.isBlank()) {
                        read.add(line.trim());
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(BATCHES + " could not be read", e);
            }
            batchNames = names = List.copyOf(read);
        }
        return names;
    }

    static String modelsOf(String batch) {
        return "/models/props-" + batch + ".bin.gz";
    }

    static String placesOf(String batch) {
        return "/models/props-" + batch + ".txt";
    }

    /** Every batch's lines, by overlay and host, read once whichever threads ask. */
    private static Map<String, Map<String, Dressing>> made() {
        Map<String, Map<String, Dressing>> all = made;
        if (all == null) {
            synchronized (Props.class) {
                all = made;
                if (all == null) {
                    all = new HashMap<>();
                    for (String batch : batches()) {
                        for (Map.Entry<String, Map<String, Dressing>> overlay : read(batch).dressings().entrySet()) {
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

    /** One batch's file, read from the jar. */
    static Batch read(String batch) {
        String resource = placesOf(batch);
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
            String where = "props-" + batch + ".txt line " + number;
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
                    float[] counts = floats(cells[7], 2, where);
                    dressing(dressings, cells).marks.add(new Marks(cells[3].split(","), floats(cells[4], 3, where), floats(cells[5], 3, where),
                            floats(cells[6], 3, where), (int) counts[0], (int) counts[1], Integer.parseInt(cells[8])));
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

    /** Whether some batch makes the overlay on the piece, as the piece's own model or with its basin. */
    static boolean makes(String overlay, Furniture piece) {
        Map<String, Dressing> hosts = made().get(overlay);
        return hosts != null && (hosts.containsKey(piece.model.name) || piece.withBasin != null && hosts.containsKey(piece.withBasin.name));
    }

    /** The names of what the game lays over an object: TileOverlays' into its attached sprites, ContainerOverlays' as its overlay. */
    static List<String> overlaysOn(IsoObject object, List<String> out) {
        out.clear();
        List<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
        if (attached != null) {
            for (IsoSpriteInstance instance : attached) {
                if (instance != null && instance.parentSprite != null && instance.parentSprite.getName() != null) {
                    out.add(instance.parentSprite.getName());
                }
            }
        }
        IsoSprite overlay = object.getOverlaySprite();
        if (overlay != null && overlay.getName() != null) {
            out.add(overlay.getName());
        }
        return out;
    }

    /** What of the overlays named has a piece of host model drawn as another model, or null where none does. */
    static Dressing swapOf(List<String> overlays, Models.Model host) {
        Dressing swap = null;
        for (String overlay : overlays) {
            Dressing dressing = of(overlay, host.name);
            if (dressing != null && dressing.swap != null) {
                if (swap != null && !swap.swap.equals(dressing.swap)) {
                    throw new IllegalStateException(host.name + " is to be drawn as both " + swap.swap + " and " + dressing.swap + " under " + overlays);
                }
                swap = dressing;
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
        /** The square the object is on, which its overlay is drawn over, and where on the map the model's origin stands. */
        int objectX, objectY, objectZ;
        float centreX, centreY, foot;
        int turns;
        float scale, scaleUp;
        int dyeTexture, dyed, alsoDyed;
        float dyeU, dyeV, alsoU, alsoV;
    }

    private static final ThreadLocal<float[]> TURNED = ThreadLocal.withInitial(() -> new float[2]);
    private static final ThreadLocal<SpriteGeometry.Frame> OVER_FRAME = ThreadLocal.withInitial(SpriteGeometry.Frame::new);

    /**
     * Stands the props every overlay named makes of a piece whose model, before any swap, is host, where the piece
     * stands. object is the piece's, whose overlays' pictures give the props' colours; scale is the frame's sprite scale.
     */
    static void add(WorldScene.Frame build, IsoObject object, List<String> overlays, String host, Stand at, float spriteScale) {
        for (String overlay : overlays) {
            Dressing dressing = of(overlay, host);
            if (dressing == null) {
                continue;
            }
            Texture painted = null;
            SpriteGeometry.Frame frame = OVER_FRAME.get();
            if (needsPicture(dressing)) {
                painted = pictureOf(object, overlay);
                if (painted == null) {
                    continue;
                }
                frame.scale = spriteScale;
                WorldScene.frameOf(frame, object, painted);
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
                    float a = dyeAt[0] - at.objectX, b = dyeAt[1] - at.objectY, high = heightOnPicture(at, prop.dyeZ);
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
                        float a = mapX - at.objectX, b = mapY - at.objectY, high = heightOnPicture(at, z);
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

    /** The picture of the overlay of that name over the object, as the game draws it, or null where it has none to draw. */
    private static Texture pictureOf(IsoObject object, String overlay) {
        IsoSprite sprite = null;
        List<IsoSpriteInstance> attached = object.getAttachedAnimSprite();
        if (attached != null) {
            for (IsoSpriteInstance instance : attached) {
                if (instance != null && instance.parentSprite != null && overlay.equals(instance.parentSprite.getName())) {
                    sprite = instance.parentSprite;
                }
            }
        }
        if (sprite == null && object.getOverlaySprite() != null && overlay.equals(object.getOverlaySprite().getName())) {
            sprite = object.getOverlaySprite();
        }
        if (sprite == null) {
            throw new IllegalStateException(overlay + " was named as over the object and is not");
        }
        Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
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

    /** How high, in levels over the floor of the object's square, the overlay's picture paints a point z metres up the host model. */
    private static float heightOnPicture(Stand at, float z) {
        return (at.foot + z * at.scaleUp - at.objectZ * ViewCamera.LEVEL_HEIGHT) / ViewCamera.LEVEL_HEIGHT;
    }
}
