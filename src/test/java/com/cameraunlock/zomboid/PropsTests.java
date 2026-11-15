package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import zombie.iso.IsoObject;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteInstance;

/**
 * The batches of props (Props): each loads, was made by the scripts as they are on hosts the size the jar's are, names
 * only models it has and overlays the game lays over the pieces they are named on, leaves no host of an overlay it makes
 * without its props, draws a piece as one model whatever lies over it, and stands its props on the piece, from every
 * square of a piece on more than one. The game's overlays are src/test/data/overlays.tsv, which
 * tools/models/props/overlays.py writes from the game's own files.
 */
final class PropsTests {
    private PropsTests() {}

    static void run() {
        Checks.run("thePropBatchesTheBuildListedAreTheOnesInTheSources", PropsTests::thePropBatchesTheBuildListedAreTheOnesInTheSources);
        Checks.run("eachBatchWasMadeByItsScriptsAsTheyAre", PropsTests::eachBatchWasMadeByItsScriptsAsTheyAre);
        Checks.run("eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays", PropsTests::eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays);
        Checks.run("anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver", PropsTests::anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver);
        Checks.run("aPieceIsDrawnAsOneModelWhateverLiesOverIt", PropsTests::aPieceIsDrawnAsOneModelWhateverLiesOverIt);
        Checks.run("propsStandOnTheirPiece", PropsTests::propsStandOnTheirPiece);
        Checks.run("thePropsOfEverySquareOfAPieceStandWithIt", PropsTests::thePropsOfEverySquareOfAPieceStandWithIt);
        Checks.run("aBatchsLinesAreReadAsWritten", PropsTests::aBatchsLinesAreReadAsWritten);
        Checks.run("aBadLineIsRefused", PropsTests::aBadLineIsRefused);
        Checks.run("aBatchNamingAModelTheJarHasNotIsRefused", PropsTests::aBatchNamingAModelTheJarHasNotIsRefused);
    }

    private static final Path PROPS = Path.of("src", "main", "resources", "props"), SCRIPTS = Path.of("tools", "models", "props");

    /** One line of overlays.tsv: an overlay, a tile it may lie over, and that tile's row. */
    record Over(String overlay, String tile, String kind, String row) {}

    static List<Over> overlays() {
        List<Over> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Path.of("src", "test", "data", "overlays.tsv"), StandardCharsets.ISO_8859_1)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] cells = line.split("\t", -1);
                out.add(new Over(cells[0], cells[1], cells[2], cells[5]));
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        check(out.size() > 1000, "the game's overlays are read: " + out.size());
        return out;
    }

    private static void thePropBatchesTheBuildListedAreTheOnesInTheSources() throws IOException {
        Set<String> sources = new TreeSet<>();
        try (var files = Files.list(PROPS)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".txt"))
                    .forEach(name -> sources.add(name.substring(0, name.length() - ".txt".length())));
        }
        check(new TreeSet<>(Models.propBatches()).equals(sources), "the build listed the batches in " + PROPS + ": " + Models.propBatches() + " and " + sources);
        for (String batch : sources) {
            check(Files.isRegularFile(PROPS.resolve(batch + ".bin.gz")), batch + " has its models beside where they stand");
            check(Files.isRegularFile(SCRIPTS.resolve(batch + ".py")), batch + " has the script that makes it");
        }
    }

    /** The SHA-256 of each model's bytes in furniture.bin.gz, from its name's length to its plain shape's end, as props.py takes it. */
    static Map<String, String> furnitureHashes() throws IOException, NoSuchAlgorithmException {
        byte[] packed;
        try (java.io.InputStream in = new java.util.zip.GZIPInputStream(Files.newInputStream(Path.of("src", "main", "resources", "models", "furniture.bin.gz")))) {
            packed = in.readAllBytes();
        }
        java.nio.ByteBuffer data = java.nio.ByteBuffer.wrap(packed).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        check(data.getInt(0) == ('C' | 'U' << 8 | 'F' << 16 | 'M' << 24), "furniture.bin.gz is a file of models");
        int count = data.getInt(8), at = 12;
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < count; i++) {
            int start = at, size = data.get(at) & 0xff;
            String name = new String(packed, at + 1, size, StandardCharsets.US_ASCII);
            at += 1 + size + 28;
            for (int shape = 0; shape < 2; shape++) {
                int vertices = data.getInt(at), triangles = data.getInt(at + 4);
                at += 8 + vertices * 26 + triangles * 6;
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(packed, start, at - start);
            out.put(name, HexFormat.of().formatHex(digest.digest()));
        }
        check(at == packed.length, "furniture.bin.gz ends with its last model: " + at + " of " + packed.length);
        return out;
    }

    /**
     * Each batch's two files are what tools/models/props/BATCH.py builds with props.py: the script writes their SHA-256
     * and that of what it wrote to tools/models/props/BATCH.built, with the bounds of each host as it built it and the
     * SHA-256 of furniture.bin.gz's model of it. Blender builds them, and is no part of the tests, so a prop changed in a
     * script and not built again shows here, and so does a host furniture.py now builds otherwise than the batch stood its
     * props on: another size, or the same size with another door or handle.
     */
    private static void eachBatchWasMadeByItsScriptsAsTheyAre() throws IOException, NoSuchAlgorithmException {
        Map<String, String> shipped = furnitureHashes();
        for (String batch : Models.propBatches()) {
            Map<String, String> built = new HashMap<>();
            Map<String, float[]> hosts = new HashMap<>();
            for (String line : Files.readAllLines(SCRIPTS.resolve(batch + ".built"))) {
                String[] named = line.split(" ");
                if (named[0].equals("host")) {
                    check(named.length == 9, batch + ".built gives a host its name, six bounds and its model's hash: " + line);
                    float[] bounds = new float[6];
                    for (int k = 0; k < 6; k++) {
                        bounds[k] = Float.parseFloat(named[2 + k]);
                    }
                    hosts.put(named[1], bounds);
                    check(named[8].equals(shipped.get(named[1])), batch + " stood its props on " + named[1] + " as furniture.bin.gz had it then, and it has "
                            + "another model of it now (" + shipped.get(named[1]) + " against " + named[8] + "): run tools/models/props/" + batch + ".py again");
                } else {
                    built.put(named[0], named[1]);
                }
            }
            Map<String, Path> files = Map.of(batch + ".py", SCRIPTS.resolve(batch + ".py"), "props.py", SCRIPTS.resolve("props.py"),
                    batch + ".bin.gz", PROPS.resolve(batch + ".bin.gz"), batch + ".txt", PROPS.resolve(batch + ".txt"));
            check(built.keySet().equals(files.keySet()), batch + ".built names what its build reads and writes: " + built.keySet());
            for (Map.Entry<String, Path> file : files.entrySet()) {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.getValue())));
                check(hash.equals(built.get(file.getKey())), file.getValue() + " is as the last build of " + batch + " had it: run tools/models/props/"
                        + batch + ".py in Blender and commit what it writes (" + hash + " against " + built.get(file.getKey()) + ")");
            }
            Set<String> named = new TreeSet<>();
            Props.read(batch).dressings().values().forEach(byHost -> named.addAll(byHost.keySet()));
            check(hosts.keySet().containsAll(named), batch + ".built has the bounds of every host it names: " + named + " and " + hosts.keySet());
            for (Map.Entry<String, float[]> host : hosts.entrySet()) {
                Models.Model model = Models.get(host.getKey());
                float[] jar = {model.minX, model.minY, model.minZ, model.maxX, model.maxY, model.maxZ}, was = host.getValue();
                boolean same = true;
                for (int k = 0; k < 6; k++) {
                    same &= Math.abs(jar[k] - was[k]) < 0.0005f;
                }
                check(same, batch + " stood its props on " + host.getKey() + " as big as the jar's model of it, which is "
                        + java.util.Arrays.toString(jar) + " against " + java.util.Arrays.toString(was) + ": run tools/models/props/" + batch + ".py again");
            }
        }
    }

    private static void eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays() {
        Map<String, Set<String>> tilesOf = new HashMap<>();
        for (Over over : overlays()) {
            tilesOf.computeIfAbsent(over.overlay(), key -> new TreeSet<>()).add(over.tile());
        }
        for (String batch : Models.propBatches()) {
            Props.Batch read = Props.read(batch);
            check(!read.dressings().isEmpty(), batch + " makes something");
            Props.standsOnlyModelsTheJarHas(read);
            for (var overlay : read.dressings().entrySet()) {
                check(tilesOf.containsKey(overlay.getKey()), batch + ": " + overlay.getKey() + " is an overlay the game lays over furniture");
                for (var host : overlay.getValue().entrySet()) {
                    Models.Model piece = Models.get(host.getKey());
                    Props.Dressing dressing = host.getValue();
                    List<String> named = new ArrayList<>();
                    if (dressing.swap != null) {
                        named.add(dressing.swap);
                    }
                    dressing.props.forEach(prop -> named.add(prop.model()));
                    dressing.marks.forEach(marks -> named.addAll(List.of(marks.models())));
                    for (String model : named) {
                        check(model.startsWith(batch + "_"), batch + ": " + model + " is named for its batch, so no two batches' models share a name");
                        Models.get(model);
                    }
                    if (dressing.swap != null) {
                        Models.Model swap = Models.get(dressing.swap);
                        check(Math.abs(swap.height() - piece.height()) < 0.01f && Math.abs(swap.width() - piece.width()) < 0.01f
                                && Math.abs(swap.depth() - piece.depth()) < 0.06f, batch + ": " + dressing.swap + " is " + host.getKey() + " as big, with a door off");
                    }
                }
            }
        }
    }

    /**
     * Every tile an overlay a batch makes lies over is a piece of a host the batch makes it on, with its basin as well
     * where the piece has one: a host it is not made on would draw neither the props nor the game's picture. A stocked
     * fitting's goods and a case's cakes are the game's painting made already, and no batch dresses either.
     */
    private static void anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver() {
        Map<String, List<Over>> byOverlay = new HashMap<>();
        for (Over over : overlays()) {
            byOverlay.computeIfAbsent(over.overlay(), key -> new ArrayList<>()).add(over);
        }
        Map<String, String> rows = new HashMap<>();
        for (Over over : overlays()) {
            rows.put(over.tile(), over.row());
        }
        int pieces = 0;
        for (String batch : Models.propBatches()) {
            for (var overlay : Props.read(batch).dressings().entrySet()) {
                Set<String> hosts = overlay.getValue().keySet();
                for (Over over : byOverlay.getOrDefault(overlay.getKey(), List.of())) {
                    Furniture piece = pieceOf(over, rows);
                    if (piece == null || piece.model == null) {
                        continue;
                    }
                    if (piece.stocked || piece.displayCase) {
                        check(!Props.makes(overlay.getKey(), piece, piece.model), batch + ": " + over.tile() + " holds goods of its own, and " + overlay.getKey()
                                + " is not made on it");
                        continue;
                    }
                    pieces++;
                    // Every model addFurniture may draw the piece as: with its basin or without, and lit as a burn barrel or not.
                    for (boolean basin : new boolean[] {false, true}) {
                        for (boolean burning : new boolean[] {false, true}) {
                            Models.Model drawn = FurnishingBuilder.drawnAs(piece, basin, burning);
                            check(hosts.contains(drawn.name), batch + ": " + overlay.getKey() + " lies over " + over.tile() + ", which may be drawn as "
                                    + drawn.name + ", and it is made on " + hosts);
                            check(Props.makes(overlay.getKey(), piece, drawn), "so the game's picture of " + overlay.getKey() + " is not stood over "
                                    + over.tile() + " drawn as " + drawn.name);
                        }
                    }
                }
            }
        }
        check(Models.propBatches().isEmpty() || pieces > 0, "the overlays the batches make lie over pieces the mod models: " + pieces);
    }

    /**
     * The piece a tile is of. A tile that has a SpriteGridPos is a square of a piece on more than one, which the mod reads
     * from the grid the game lays them in: it is laid here with the tile beside it of the same sheet and facing whose
     * SpriteGridPos is the other square of two, when that makes a piece the mod models, and stands alone otherwise.
     */
    private static Furniture pieceOf(Over over, Map<String, String> rows) {
        Furniture alone = Furniture.known(TileRows.sprite(over.row()));
        String at = rowValue(over.row(), "|SpriteGridPos|");
        if (at == null) {
            return alone;
        }
        String sheet = over.tile().substring(0, over.tile().lastIndexOf('_') + 1);
        int number = Integer.parseInt(over.tile().substring(sheet.length()));
        String facing = rowValue(over.row(), "|Facing|");
        for (int next : new int[] {number + 1, number - 1}) {
            String other = rows.get(sheet + next);
            String there = other == null ? null : rowValue(other, "|SpriteGridPos|");
            if (there == null || there.equals(at) || !facing.equals(rowValue(other, "|Facing|"))) {
                continue;
            }
            String[] a = at.split(","), b = there.split(",");
            int width = Math.max(Integer.parseInt(a[0]), Integer.parseInt(b[0])) + 1, height = Math.max(Integer.parseInt(a[1]), Integer.parseInt(b[1])) + 1;
            IsoSprite mine = TileRows.sprite(over.row());
            TileRows.piece(width, height, mine, TileRows.sprite(other));
            Furniture laid = Furniture.known(mine);
            if (laid != null && laid.model != null) {
                return laid;
            }
        }
        return alone;
    }

    /** What follows the property in a row, up to the next bar, or null where the row has no such property. */
    private static String rowValue(String row, String property) {
        int at = row.indexOf(property);
        if (at < 0) {
            return null;
        }
        int from = at + property.length();
        int to = row.indexOf('|', from);
        return row.substring(from, to < 0 ? row.length() : to);
    }

    /**
     * An object has at most one overlay of TileOverlays (TileOverlays.updateTileOverlaySprite picks one entry and one
     * picture of it) and one of ContainerOverlays, and a piece on more than one square has those of each square. The
     * piece is drawn as one model, so wherever two of them could lie over it together they swap its host for the same model
     * or none: Props.swapOf refuses two, which this shows it does.
     */
    private static void aPieceIsDrawnAsOneModelWhateverLiesOverIt() {
        Map<String, List<Over>> byTile = new HashMap<>();
        for (Over over : overlays()) {
            byTile.computeIfAbsent(over.tile(), key -> new ArrayList<>()).add(over);
        }
        Map<String, Set<String>> swapsOfHost = new HashMap<>();
        Set<String> spread = new TreeSet<>();
        for (List<Over> on : byTile.values()) {
            Furniture piece = Furniture.known(TileRows.sprite(on.get(0).row()));
            if (piece == null || piece.model == null) {
                continue;
            }
            Map<String, Set<String>> byKind = new HashMap<>();
            for (Over over : on) {
                Props.Dressing dressing = Props.of(over.overlay(), piece.model.name);
                if (dressing != null && dressing.swap != null) {
                    byKind.computeIfAbsent(over.kind(), key -> new TreeSet<>()).add(dressing.swap);
                    swapsOfHost.computeIfAbsent(piece.model.name, key -> new TreeSet<>()).add(dressing.swap);
                }
            }
            for (String tile : byKind.getOrDefault("tile", Set.of())) {
                for (String container : byKind.getOrDefault("container", Set.of())) {
                    check(tile.equals(container), on.get(0).tile() + " may have " + tile + " and " + container + " swapped in for " + piece.model.name + " at once");
                }
            }
            if (piece.grid != null || piece.partner != null || on.get(0).row().contains("|SpriteGridPos|")) {
                spread.add(piece.model.name);
            }
        }
        for (String host : spread) {
            check(swapsOfHost.getOrDefault(host, Set.of()).size() <= 1, host + " stands on more than one square, whose overlays may swap it for "
                    + swapsOfHost.get(host) + " at once");
        }
        // Two overlays over one counter that swap it for different models: the doors of both left off, and the right's.
        IsoObject counter = new IsoObject(TileRows.sprite("fixtures_counters_01_3|"));
        Props.Square square = new Props.Square();
        square.object = counter;
        square.overlays.add(TileRows.sprite("fixtures_overlay_counters_01_2|"));
        square.overlays.add(TileRows.sprite("fixtures_overlay_counters_01_3|"));
        Models.Model host = Models.get("counter");
        boolean refused = false;
        try {
            Props.swapOf(List.of(square), 1, host);
        } catch (IllegalStateException e) {
            refused = e.getMessage().contains("drawn as both");
        }
        check(refused, "a counter under two overlays that would draw it as two models is refused");
        square.overlays.remove(1);
        check(Props.swapOf(List.of(square), 1, host) != null, "and under one of them it is swapped");
    }

    /** A prop stands within its piece's footprint, or a hand beyond it for a door standing open, and no higher than a level over it. */
    private static void propsStandOnTheirPiece() {
        for (String batch : Models.propBatches()) {
            for (var overlay : Props.read(batch).dressings().entrySet()) {
                for (var host : overlay.getValue().entrySet()) {
                    Models.Model piece = Models.get(host.getKey());
                    for (Props.Prop prop : host.getValue().props) {
                        Models.Model model = Models.get(prop.model());
                        float reach = 0.15f;
                        check(prop.x() + model.minX > piece.minX - reach && prop.x() + model.maxX < piece.maxX + reach && prop.y() + model.minY > piece.minY - reach
                                && prop.y() + model.maxY < piece.maxY + reach && prop.z() >= piece.minZ - 0.001f && prop.z() + model.height() < piece.maxZ + ViewCamera.LEVEL_HEIGHT,
                                batch + ": " + prop.model() + " stands on " + host.getKey() + " under " + overlay.getKey());
                    }
                    for (Props.Marks marks : host.getValue().marks) {
                        for (float s : new float[] {0f, 1f}) {
                            for (float t : new float[] {0f, 1f}) {
                                float x = marks.origin()[0] + s * marks.across()[0] + t * marks.along()[0], y = marks.origin()[1] + s * marks.across()[1] + t * marks.along()[1];
                                float z = marks.origin()[2] + s * marks.across()[2] + t * marks.along()[2];
                                check(x >= piece.minX - 0.01f && x <= piece.maxX + 0.01f && y >= piece.minY - 0.01f && y <= piece.maxY + 0.01f && z >= piece.minZ - 0.001f
                                        && z <= piece.maxZ + 0.01f, batch + ": the marks of " + overlay.getKey() + " are on " + host.getKey() + " at " + x + ", " + y + ", " + z);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * A piece on two squares stands from one of them, and what the game lays over the other stands with it, from that
     * square: the bath of two squares of a grid, and a large table of two the game ties with no grid, whose second
     * square stands nothing of its own.
     */
    private static void thePropsOfEverySquareOfAPieceStandWithIt() {
        String row = "|BlocksPlacement||CanScrap||CustomName|Bath|Facing|S|GroupName|Large Deluxe|IsMoveAble||ItemHeight|22|Material|Plumbing|"
                + "Material2|Pipes|PickUpTool|Wrench|PickUpWeight|100|PlaceTool|Wrench|solidtrans||SpriteGridPos|";
        IsoSprite head = TileRows.sprite("fixtures_bathroom_01_25" + row + "0,0"), foot = TileRows.sprite("fixtures_bathroom_01_24" + row + "0,1|IsGridExtensionTile|");
        TileRows.piece(1, 2, head, foot);
        Furniture bath = Furniture.known(head);
        check(bath != null && bath.model.name.equals("bath"), "the bath is the bath");
        IsoObject headObject = new IsoObject(head), footObject = new IsoObject(foot);
        IsoSprite soap = TileRows.sprite("fixtures_01_24|"), sponge = TileRows.sprite("fixtures_01_25|");
        headObject.attached.add(new IsoSpriteInstance(soap));
        footObject.attached.add(new IsoSpriteInstance(sponge));
        IsoObject[][] map = {{headObject, footObject}};
        List<Props.Square> squares = new ArrayList<>();
        int count = FurnishingBuilder.overlaidSquares(bath, headObject, 40, 70,
                (dx, dy, sprite) -> dx == 0 && dy >= 0 && dy < 2 && map[0][dy].getSprite() == sprite ? map[0][dy] : null, (dx, dy, name) -> null, new int[2], squares);
        check(count == 2, "both squares of the bath have what lies over them stood: " + count);
        check(count == 2 && squares.get(0).object == headObject && squares.get(0).x == 40 && squares.get(0).y == 70 && squares.get(0).overlays.get(0) == soap,
                "the head's, from its own square");
        check(count == 2 && squares.get(1).object == footObject && squares.get(1).x == 40 && squares.get(1).y == 71 && squares.get(1).overlays.get(0) == sponge,
                "and the foot's, from the square past it");
        footObject.attached.clear();
        check(FurnishingBuilder.overlaidSquares(bath, headObject, 40, 70, (dx, dy, sprite) -> dx == 0 && dy >= 0 && dy < 2 && map[0][dy].getSprite() == sprite ? map[0][dy] : null,
                (dx, dy, name) -> null, new int[2], squares) == 1, "a square with nothing over it has none stood");

        IsoSprite first = TileRows.sprite("test_table_first|"), second = TileRows.sprite("test_table_second|");
        Furniture table = new Furniture(first, "table_2", 0);
        table.partner = "test_table_second";
        table.partnerX = 1;
        IsoObject firstObject = new IsoObject(first), secondObject = new IsoObject(second);
        IsoSprite cup = TileRows.sprite("test_cup|");
        secondObject.attached.add(new IsoSpriteInstance(cup));
        count = FurnishingBuilder.overlaidSquares(table, firstObject, 10, 20, (dx, dy, sprite) -> dx == 0 && dy == 0 ? firstObject : null,
                (dx, dy, name) -> dx == 1 && dy == 0 && name.equals("test_table_second") ? secondObject : null, new int[2], squares);
        check(count == 1 && squares.get(0).object == secondObject && squares.get(0).x == 11 && squares.get(0).y == 20,
                "what lies over the second square of a table stands with the table, from the second square: " + count);
        // A square that stands nothing of its own is the piece its partner stands: one tied to a counter is the counter.
        String counterRow = overlays().stream().filter(over -> over.tile().equals("fixtures_counters_01_3")).findFirst().orElseThrow().row();
        IsoSprite counter = TileRows.sprite(counterRow);
        Furniture half = new Furniture(second, null, 0);
        half.partner = "fixtures_counters_01_3";
        half.partnerX = -1;
        half.follows = true;
        zombie.iso.sprite.IsoSpriteManager.instance.namedMap.put("fixtures_counters_01_3", counter);
        check(Props.makes("fixtures_overlay_counters_01_2", half, null), "a square tied to a counter is the counter, and its overlay is made on it");
        Furniture counterPiece = Furniture.known(counter);
        check(!Props.makes("fixtures_overlay_counters_01_2", counterPiece, Models.get("burn_barrel")),
                "and a piece is asked about as the model it is drawn as, not the model it is known by");
        zombie.iso.sprite.IsoSpriteManager.instance.namedMap.remove("fixtures_counters_01_3");
    }

    private static final String SAMPLE = """
            # a comment
            swap\tover_1\tcounter\tcounters_open\t0.1 0.2 0.3
            prop\tover_1\tcounter\tcounters_door\t0.5 -0.25 0.1\thost
            prop\tover_1\tcounter\tcounters_cup\t0 0 0.9\tover:32768:0.1,0.2,0.9
            marks\tover_1\tcounter\tcounters_a,counters_b\t-0.5 -0.3 0.9\t1 0 0\t0 0.6 0\t8 5\t32768
            """;

    private static void aBatchsLinesAreReadAsWritten() throws IOException {
        Props.Batch batch = Props.parse("sample", new BufferedReader(new StringReader(SAMPLE)));
        Props.Dressing dressing = batch.dressings().get("over_1").get("counter");
        check(dressing.swap.equals("counters_open") && dressing.swapAt[2] == 0.3f, "the swap is read with where it stands");
        check(dressing.props.size() == 2 && dressing.props.get(0).dye().equals(Props.HOST_DYE) && dressing.props.get(0).x() == 0.5f, "a prop coloured as its piece");
        Props.Prop cup = dressing.props.get(1);
        check(cup.dye().equals(Props.OVER_DYE) && cup.dyed() == 32768 && cup.dyeZ() == 0.9f, "a prop coloured from the overlay, and where");
        Props.Marks marks = dressing.marks.get(0);
        check(marks.models().length == 2 && marks.acrossCount() == 8 && marks.alongCount() == 5 && marks.along()[1] == 0.6f && marks.dyed() == 32768, "marks");
    }

    private static void aBadLineIsRefused() {
        String marks = "marks\to\th\ta\t0 0 0\t1 0 0\t0 1 0\t%s\t1\n";
        for (String bad : new String[] {"prop\to\th\tm\t1 2\thost\n", "prop\to\th\tm\t1 2 3\tred\n", "place\to\th\tm\n", "prop\to\th\tm\t1 2 3\n",
            "swap\to\th\tm\t0 0 0\nswap\to\th\tn\t0 0 0\n", marks.formatted("8.5 5"), marks.formatted("8 0"), marks.formatted("-2 5"), marks.formatted("8"),
            "marks\to\th\ta\t0 0 0\t1 0 0\t0 1 0\t8 5\t1\textra\n"}) {
            boolean refused = false;
            try {
                Props.parse("bad", new BufferedReader(new StringReader(bad)));
            } catch (IllegalStateException | IOException | NumberFormatException e) {
                refused = true;
            }
            check(refused, "refused: " + bad.replace('\n', '/'));
        }
    }

    private static void aBatchNamingAModelTheJarHasNotIsRefused() throws IOException {
        Props.Batch batch = Props.parse("sample", new BufferedReader(new StringReader("prop\tover_1\tcounter\tsample_missing_cup\t0 0 0.9\thost\n")));
        String said = null;
        try {
            Props.standsOnlyModelsTheJarHas(batch);
        } catch (IllegalStateException e) {
            said = e.getMessage();
        }
        check(said != null && said.contains("sample_missing_cup") && said.contains("/props/sample.txt"), "a prop the jar has no model of is refused, by name and file: " + said);
        Props.Batch host = Props.parse("sample", new BufferedReader(new StringReader("prop\tover_1\tsample_no_host\tcounter\t0 0 0\thost\n")));
        boolean refused = false;
        try {
            Props.standsOnlyModelsTheJarHas(host);
        } catch (IllegalStateException e) {
            refused = e.getMessage().contains("sample_no_host");
        }
        check(refused, "and so is a host it has none of");
    }
}
