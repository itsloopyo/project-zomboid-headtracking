package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

/**
 * Tests of the pictures the jar holds packed for the graphics card: that each is the one
 * tools/textures made from the sources now in src/textures, and that the mod reads them.
 * Run from the repository's root, where the sources and the list of what was made are.
 */
final class PictureTests {
    private PictureTests() {}

    private static final Path SOURCES = Path.of("src", "textures"), PACKED = Path.of("src", "main", "resources"), MADE = Path.of("tools", "textures", "made.txt");
    private static final int HEAD = 32;

    static void run() throws IOException {
        Checks.run("everyPackedPictureIsTheOneMadeFromItsSources", PictureTests::everyPackedPictureIsTheOneMadeFromItsSources);
        Checks.run("theBarksArePackedInTheOrderTheyAreCountedIn", PictureTests::theBarksArePackedInTheOrderTheyAreCountedIn);
        Checks.run("aPictureIsReadWholeOrWithoutItsLargestLevel", PictureTests::aPictureIsReadWholeOrWithoutItsLargestLevel);
        Checks.run("aFileThatIsNotAWholePictureIsRefused", PictureTests::aFileThatIsNotAWholePictureIsRefused);
        Checks.run("blocksUnpackAsTheReferenceUnpacksThem", PictureTests::blocksUnpackAsTheReferenceUnpacksThem);
        Checks.run("aPictureUnpackedIsItsSourceToWithinWhatPackingCosts", PictureTests::aPictureUnpackedIsItsSourceToWithinWhatPackingCosts);
        Checks.run("aBarkIsItsOwnSourceAtEveryLevel", PictureTests::aBarkIsItsOwnSourceAtEveryLevel);
        Checks.run("onlyTheWeaponsColourIsAScreens", PictureTests::onlyTheWeaponsColourIsAScreens);
    }

    /** What made.txt lists: each output with its SHA-256 and how it was made, and under it the sources it was made from, each with its SHA-256. */
    private record Made(String output, String digest, List<String[]> sources) {}

    private static List<Made> made() throws IOException {
        List<Made> all = new ArrayList<>();
        for (String line : Files.readAllLines(MADE)) {
            String[] parts = line.split("\t");
            if (line.startsWith("\t")) {
                all.get(all.size() - 1).sources.add(new String[] {parts[1], parts[2]});
            } else {
                all.add(new Made(parts[0], parts[1], new ArrayList<>()));
            }
        }
        return all;
    }

    private static String digest(Path file) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Set<String> filesUnder(Path folder, String ending) throws IOException {
        Set<String> found = new HashSet<>();
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path each : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                String name = folder.relativize(each).toString().replace('\\', '/');
                if (name.endsWith(ending)) {
                    found.add(name);
                }
            }
        }
        return found;
    }

    private static void everyPackedPictureIsTheOneMadeFromItsSources() throws IOException {
        String remake = ": run `python tools/textures/compress.py` and commit what it writes";
        Set<String> outputs = new HashSet<>(), sources = new HashSet<>();
        for (Made each : made()) {
            outputs.add(each.output);
            Path packed = PACKED.resolve(each.output);
            check(Files.isRegularFile(packed) && digest(packed).equals(each.digest), each.output + " is not the file made.txt says was made" + remake);
            for (String[] source : each.sources) {
                sources.add(source[0]);
                Path file = SOURCES.resolve(source[0]);
                check(Files.isRegularFile(file) && digest(file).equals(source[1]), source[0] + " has changed since " + each.output + " was made from it" + remake);
            }
        }
        check(filesUnder(PACKED, ".bc").equals(outputs), "the packed pictures in the jar's resources are not the ones made.txt lists: " + filesUnder(PACKED, ".bc") + remake);
        check(filesUnder(SOURCES, "").equals(sources), "src/textures holds files no packed picture is made from, or lacks one that is: " + filesUnder(SOURCES, ""));
    }

    private static void theBarksArePackedInTheOrderTheyAreCountedIn() throws IOException {
        for (Made each : made()) {
            if (each.output.equals("materials/bark.bc")) {
                List<String> packed = new ArrayList<>(), counted = new ArrayList<>();
                for (String[] source : each.sources) {
                    packed.add(source[0]);
                }
                for (String bark : Flora.BARKS) {
                    counted.add("materials/bark-" + bark + ".jpg");
                }
                check(packed.equals(counted), "the barks are packed in the order Flora counts them in: " + packed);
                return;
            }
        }
        check(false, "made.txt lists the barks");
    }

    private static void aPictureIsReadWholeOrWithoutItsLargestLevel() throws IOException {
        OffHeap.Block whole = Pictures.packed("/materials/vinyl.bc", false), half = Pictures.packed("/materials/vinyl.bc", true);
        ByteBuffer all = whole.bytes, less = half.bytes;
        check(all.getInt(8) == 1024 && all.getInt(12) == 1024 && all.getInt(20) == 11, "the vinyl is 1024 square with eleven levels");
        // A level of BC7 is sixteen bytes for every four texels by four.
        int largest = 256 * 256 * 16;
        check(less.getInt(8) == 512 && less.getInt(12) == 512 && less.getInt(20) == 10, "read at half size it is 512 square with ten: " + less.getInt(8));
        check(all.capacity() - less.capacity() == largest, "and is short by its largest level");
        check(all.slice(HEAD + largest, less.capacity() - HEAD).equals(less.slice(HEAD, less.capacity() - HEAD)), "the levels it has are the same bytes");
        check(all.getFloat(28) == less.getFloat(28) && all.getFloat(28) > 1f, "and its gain is the whole picture's");
        whole.free();
        half.free();
        OffHeap.Block barks = Pictures.packed("/materials/bark.bc", true);
        check(barks.bytes.getInt(16) == Flora.BARKS.length && barks.bytes.getInt(8) == 1024, "the barks are one behind another in one file");
        barks.free();
    }

    private static void aFileThatIsNotAWholePictureIsRefused() throws IOException {
        byte[] vinyl = Files.readAllBytes(PACKED.resolve("materials/vinyl.bc"));
        // The tests' classes are a folder on the class path: a file written there is one the jar has.
        Path folder = Path.of("build", "test-classes", "refused");
        Files.createDirectories(folder);
        byte[] renamed = vinyl.clone(), levels = vinyl.clone(), longer = Arrays.copyOf(vinyl, vinyl.length + 1);
        renamed[0] = 'X';
        levels[20] = 12;
        Files.write(folder.resolve("short.bc"), Arrays.copyOf(vinyl, vinyl.length - 1));
        Files.write(folder.resolve("shorter.bc"), Arrays.copyOf(vinyl, 1000));
        Files.write(folder.resolve("long.bc"), longer);
        Files.write(folder.resolve("renamed.bc"), renamed);
        Files.write(folder.resolve("levels.bc"), levels);
        Files.write(folder.resolve("head.bc"), Arrays.copyOf(vinyl, 20));
        refused("/refused/short.bc", false, "bytes after its head");
        refused("/refused/short.bc", true, "bytes after its head");
        refused("/refused/shorter.bc", true, "bytes after its head");
        refused("/refused/long.bc", false, "more than");
        refused("/refused/renamed.bc", false, "not one of the mod's packed pictures");
        refused("/refused/head.bc", false, "not one of the mod's packed pictures");
        refused("/refused/levels.bc", false, "makes no sense");
        refused("/materials/no-such.bc", false, "the jar has no");
    }

    private static void refused(String path, boolean half, String says) {
        try {
            Pictures.packed(path, half).free();
            check(false, path + " is refused");
        } catch (IOException refused) {
            check(refused.getMessage().contains(says), "the refusal of " + path + " says what is wrong with it: " + refused.getMessage());
        }
    }

    /** A block of every layout, 128 of each with every other bit drawn at random, against what Microsoft's decoder made of them. */
    private static void blocksUnpackAsTheReferenceUnpacksThem() throws IOException {
        ByteBuffer blocks = ByteBuffer.wrap(Files.readAllBytes(Path.of("src", "test", "data", "bc7", "blocks.bin")));
        byte[] reference = Files.readAllBytes(Path.of("src", "test", "data", "bc7", "texels.bin"));
        ByteBuffer texels = ByteBuffer.allocate(128 * 128 * 4);
        Bc7.unpack(blocks, 0, 128, 128, texels, 0);
        int differ = 0;
        for (int at = 0; at < reference.length; at++) {
            differ += texels.get(at) != reference[at] ? 1 : 0;
        }
        check(differ == 0, "BC7 blocks unpack to the reference's texels byte for byte: " + differ + " bytes differ");
        ByteBuffer corner = ByteBuffer.allocate(8 + 2 * 2 * 4 + 8);
        Bc7.unpack(blocks, 16, 2, 2, corner, 8);
        check(corner.getLong(0) == 0 && corner.getLong(24) == 0 && corner.get(8) == reference[4 * 4] && corner.get(8 + 2 * 4) == reference[(128 + 4) * 4],
                "a picture smaller than a block takes the block's corner and nothing is written outside it");
    }

    private static void aPictureUnpackedIsItsSourceToWithinWhatPackingCosts() throws IOException {
        BufferedImage source = ImageIO.read(SOURCES.resolve("materials/vinyl.jpg").toFile());
        OffHeap.Block packed = Pictures.packed("/materials/vinyl.bc", false), plain = Pictures.unpacked(packed);
        ByteBuffer texels = plain.bytes;
        check(texels.getInt(4) == 0 && texels.getInt(8) == 1024 && texels.getInt(20) == 11, "unpacked, a picture has the head of one of four bytes a texel");
        check(texels.capacity() == HEAD + (4 * 1024 * 1024 - 1) / 3 * 4, "and every level it had, down to one texel: " + texels.capacity());
        long squares = 0, light = 0;
        for (int y = 0; y < 1024; y++) {
            for (int x = 0; x < 1024; x++) {
                int argb = source.getRGB(x, y), at = HEAD + (y * 1024 + x) * 4;
                for (int part = 0; part < 3; part++) {
                    int was = argb >> 16 - part * 8 & 0xff, is = texels.get(at + part) & 0xff;
                    squares += (was - is) * (was - is);
                }
                light += 299 * (argb >> 16 & 0xff) + 587 * (argb >> 8 & 0xff) + 114 * (argb & 0xff);
            }
        }
        double error = Math.sqrt(squares / (3.0 * 1024 * 1024));
        check(error < 1.5, "the vinyl as the mod unpacks it is its source to within a level and a half of 255: " + error);
        float gain = 255f * 1000f * 1024 * 1024 / light;
        check(Math.abs(packed.bytes.getFloat(28) / gain - 1f) < 0.002f, "and the gain in its head is what its source's light comes to: " + packed.bytes.getFloat(28) + " against " + gain);
        packed.free();
        plain.free();
    }

    /** The root of the mean of the squares of how far two pictures' colours are apart, in levels of 255. */
    private static double apart(ByteBuffer one, int oneFrom, ByteBuffer other, int otherFrom, int texels) {
        long squares = 0;
        for (int texel = 0; texel < texels; texel++) {
            for (int part = 0; part < 3; part++) {
                int by = (one.get(oneFrom + texel * 4 + part) & 0xff) - (other.get(otherFrom + texel * 4 + part) & 0xff);
                squares += by * by;
            }
        }
        return Math.sqrt(squares / (3.0 * texels));
    }

    private static OffHeap.Block source(String name) throws IOException {
        BufferedImage image = ImageIO.read(SOURCES.resolve(name).toFile());
        OffHeap.Block texels = OffHeap.take(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                texels.bytes.putInt((y * image.getWidth() + x) * 4, argb & 0xff00ff00 | argb >> 16 & 0xff | (argb & 0xff) << 16);
            }
        }
        return texels;
    }

    /** The fourth bark of the eight, at the level below the largest: a file with its layers or its levels in another order holds some other picture there. */
    private static void aBarkIsItsOwnSourceAtEveryLevel() throws IOException {
        OffHeap.Block packed = Pictures.packed("/materials/bark.bc", true), plain = Pictures.unpacked(packed);
        packed.free();
        int side = 1024, layer = HEAD + Flora.BARK_BIRCH * side * side * 4;
        OffHeap.Block birch = source("materials/bark-birch.jpg"), oak = source("materials/bark-oak.jpg");
        OffHeap.Block halfBirch = Pictures.halved(birch.bytes, 2048, 2048), halfOak = Pictures.halved(oak.bytes, 2048, 2048);
        double own = apart(plain.bytes, layer, halfBirch.bytes, 0, side * side), others = apart(plain.bytes, layer, halfOak.bytes, 0, side * side);
        check(own < 2.0, "the birch's layer at half size is the birch's source halved, to within two levels of 255: " + own);
        check(others > 8.0, "and is not the oak's: " + others);
        // The level below that begins after all eight layers of this one.
        int next = HEAD + Flora.BARKS.length * side * side * 4 + Flora.BARK_BIRCH * (side / 2) * (side / 2) * 4;
        OffHeap.Block quarterBirch = Pictures.halved(halfBirch.bytes, side, side);
        double smaller = apart(plain.bytes, next, quarterBirch.bytes, 0, side * side / 4);
        check(smaller < 2.0, "and the level below is that halved again: " + smaller);
        for (OffHeap.Block each : new OffHeap.Block[] {plain, birch, oak, halfBirch, halfOak, quarterBirch}) {
            each.free();
        }
    }

    private static void onlyTheWeaponsColourIsAScreens() throws IOException {
        for (String name : filesUnder(PACKED, ".bc")) {
            OffHeap.Block packed = Pictures.packed("/" + name, true);
            boolean screens = name.equals("models/shotgun.colour.bc") || name.equals("models/rifle.colour.bc");
            check(packed.bytes.getInt(24) == (screens ? 1 : 0), name + " says its colour is a screen's only if it is a weapon's colour, as the graphics card was told before the pictures were packed");
            packed.free();
        }
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }
}
