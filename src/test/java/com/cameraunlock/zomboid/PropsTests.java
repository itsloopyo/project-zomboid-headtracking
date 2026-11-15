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

/**
 * The batches of props (Props): each loads, was made by the scripts as they are, names only
 * models it has and overlays the game lays over the pieces they are named on, leaves no host of an overlay it makes
 * without its props, and stands its props on the piece. The game's overlays are src/test/data/overlays.tsv, which
 * tools/models/props/overlays.py writes from the game's own files.
 */
final class PropsTests {
    private PropsTests() {}

    static void run() {
        Checks.run("thePropBatchesTheBuildListedAreTheOnesInTheSources", PropsTests::thePropBatchesTheBuildListedAreTheOnesInTheSources);
        Checks.run("eachBatchWasMadeByItsScriptsAsTheyAre", PropsTests::eachBatchWasMadeByItsScriptsAsTheyAre);
        Checks.run("eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays", PropsTests::eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays);
        Checks.run("anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver", PropsTests::anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver);
        Checks.run("propsStandOnTheirPiece", PropsTests::propsStandOnTheirPiece);
        Checks.run("aBatchsLinesAreReadAsWritten", PropsTests::aBatchsLinesAreReadAsWritten);
        Checks.run("aBadLineIsRefused", PropsTests::aBadLineIsRefused);
    }

    private static final Path MODELS = Path.of("src", "main", "resources", "models"), SCRIPTS = Path.of("tools", "models", "props");

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
        try (var files = Files.list(MODELS)) {
            files.map(path -> path.getFileName().toString()).filter(name -> name.startsWith("props-") && name.endsWith(".txt"))
                    .forEach(name -> sources.add(name.substring("props-".length(), name.length() - ".txt".length())));
        }
        check(new TreeSet<>(Props.batches()).equals(sources), "the build listed the batches in " + MODELS + ": " + Props.batches() + " and " + sources);
        for (String batch : sources) {
            check(Files.isRegularFile(MODELS.resolve("props-" + batch + ".bin.gz")), batch + " has its models beside where they stand");
            check(Files.isRegularFile(SCRIPTS.resolve(batch + ".py")), batch + " has the script that makes it");
        }
    }

    /**
     * Each batch's two files are what tools/models/props/BATCH.py builds with props.py and furniture.py: the script writes
     * their SHA-256 and that of what it wrote to tools/models/props/BATCH.built. Blender builds them, and is no part of the
     * tests, so a prop changed in a script and not built again, or a piece changed under it in furniture.py, shows here.
     */
    private static void eachBatchWasMadeByItsScriptsAsTheyAre() throws IOException, NoSuchAlgorithmException {
        for (String batch : Props.batches()) {
            Map<String, String> built = new HashMap<>();
            for (String line : Files.readAllLines(SCRIPTS.resolve(batch + ".built"))) {
                String[] named = line.split(" ");
                built.put(named[0], named[1]);
            }
            Map<String, Path> files = Map.of(batch + ".py", SCRIPTS.resolve(batch + ".py"), "props.py", SCRIPTS.resolve("props.py"),
                    "furniture.py", Path.of("tools", "models", "furniture.py"), "props-" + batch + ".bin.gz", MODELS.resolve("props-" + batch + ".bin.gz"),
                    "props-" + batch + ".txt", MODELS.resolve("props-" + batch + ".txt"));
            check(built.keySet().equals(files.keySet()), batch + ".built names what its build reads and writes: " + built.keySet());
            for (Map.Entry<String, Path> file : files.entrySet()) {
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.getValue())));
                check(hash.equals(built.get(file.getKey())), file.getValue() + " is as the last build of " + batch + " had it: run tools/models/props/"
                        + batch + ".py in Blender and commit what it writes (" + hash + " against " + built.get(file.getKey()) + ")");
            }
        }
    }

    private static void eachBatchNamesOnlyItsOwnModelsAndTheGamesOverlays() {
        Map<String, Set<String>> tilesOf = new HashMap<>();
        for (Over over : overlays()) {
            tilesOf.computeIfAbsent(over.overlay(), key -> new TreeSet<>()).add(over.tile());
        }
        for (String batch : Props.batches()) {
            Props.Batch read = Props.read(batch);
            check(!read.dressings().isEmpty(), batch + " makes something");
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
     * where the piece has one: a host it is not made on would draw neither the props nor the game's picture.
     */
    private static void anOverlayABatchMakesIsMadeOnEveryPieceItLiesOver() {
        Map<String, List<Over>> byOverlay = new HashMap<>();
        for (Over over : overlays()) {
            byOverlay.computeIfAbsent(over.overlay(), key -> new ArrayList<>()).add(over);
        }
        int pieces = 0;
        for (String batch : Props.batches()) {
            for (var overlay : Props.read(batch).dressings().entrySet()) {
                Set<String> hosts = overlay.getValue().keySet();
                for (Over over : byOverlay.getOrDefault(overlay.getKey(), List.of())) {
                    Furniture piece = Furniture.known(TileRows.sprite(over.row()));
                    if (piece == null || piece.model == null) {
                        continue;
                    }
                    pieces++;
                    check(hosts.contains(piece.model.name), batch + ": " + overlay.getKey() + " lies over " + over.tile() + ", which is " + piece.model.name
                            + ", and it is made on " + hosts);
                    if (piece.withBasin != null) {
                        check(hosts.contains(piece.withBasin.name), batch + ": and over " + over.tile() + " with its basin, " + piece.withBasin.name);
                    }
                    check(Props.makes(overlay.getKey(), piece), "so the game's picture of " + overlay.getKey() + " is not stood over " + over.tile());
                }
            }
        }
        check(Props.batches().isEmpty() || pieces > 0, "the overlays the batches make lie over pieces the mod models: " + pieces);
    }

    /** A prop stands within its piece's footprint, or a hand beyond it for a door standing open, and no higher than a level over it. */
    private static void propsStandOnTheirPiece() {
        for (String batch : Props.batches()) {
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
        for (String bad : new String[] {"prop\to\th\tm\t1 2\thost\n", "prop\to\th\tm\t1 2 3\tred\n", "place\to\th\tm\n", "prop\to\th\tm\t1 2 3\n",
            "swap\to\th\tm\t0 0 0\nswap\to\th\tn\t0 0 0\n"}) {
            boolean refused = false;
            try {
                Props.parse("bad", new BufferedReader(new StringReader(bad)));
            } catch (IllegalStateException | IOException | NumberFormatException e) {
                refused = true;
            }
            check(refused, "refused: " + bad.replace('\n', '/'));
        }
    }
}
