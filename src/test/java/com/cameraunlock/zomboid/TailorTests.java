package com.cameraunlock.zomboid;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Tests of what people wear being made finer: which pictures, with what fabric, and what the enlarging keeps. */
final class TailorTests {
    private TailorTests() {}

    static void run() {
        eachPictureIsMadeFinerWithWhatItIsMadeOf();
        aMaskAndWhatIsNotWornAreLeftAlone();
        aPictureFineEnoughIsLeftAlone();
        anEdgeBetweenTwoColoursStaysAnEdge();
        aPictureNotSquareIsEnlargedEachWayAlike();
        aGarmentsEdgeIsCutCleanAndAStainsFades();
        aLineOneDotBroadIsKept();
        thePaintWithinAColourIsEvenedOut();
        whatIsSeeThroughLendsNoColour();
        theFabricLightensAndDarkensAboutTheGamesColour();
        aGreyPictureIsReadAsPainted();
        everyFabricIsInTheJar();
        thePrintsAndStainsAreInTheJarWithWhatOfThemShows();
    }

    private static void thePrintsAndStainsAreInTheJarWithWhatOfThemShows() {
        String[] asked = {"shirtdecals/RockDECAL.png", "shirtdecals/Buisness_PizzaWhirledDECAL.png", "shirtdecals/spiffo8.png", "shirtdecals/TShirt_Music_TieDye_1.png",
            "shirtdecals/Culture_USAflagDECAL.png", "BloodTextures/BloodOverlay.png", "BloodTextures/GrimeOverlay.png"};
        for (String relative : asked) {
            String path = FineSkins.Jar.resource(relative);
            try (java.io.InputStream in = TailorTests.class.getResourceAsStream(path); java.io.InputStream cutIn = TailorTests.class.getResourceAsStream(FineSkins.Jar.cut(path))) {
                check(in != null && cutIn != null, "the jar has " + path + " and what of it shows");
                BufferedImage image = javax.imageio.ImageIO.read(in), cut = javax.imageio.ImageIO.read(cutIn);
                check(image.getWidth() == cut.getWidth() && image.getHeight() == cut.getHeight() && image.getWidth() >= 512, path + " and its cut are of one size, 512 dots or more");
                check(cut.getType() == BufferedImage.TYPE_BYTE_GRAY, "what shows of " + path + " is stored a byte a dot, so that what fades is read as it is: " + cut.getType());
                int[] shows = cut.getRaster().getSamples(0, 0, cut.getWidth(), cut.getHeight(), 0, (int[]) null);
                long all = 0;
                for (int each : shows) {
                    all += each;
                }
                double share = all / 255.0 / shows.length;
                check(share > 0.05 && share < 0.85, "some of " + path + " shows and some does not: " + share);
            } catch (IOException failure) {
                throw new AssertionError(path + " could not be read", failure);
            }
            check(FineSkins.Jar.row(relative) == Own.CLOTHES, relative + " answers to the clothes row");
        }
    }

    private static String fabric(String relative) {
        Tailor.Fabric made = Tailor.of(relative);
        return made == null ? "none" : made == Tailor.PLAIN ? "plain" : made.name();
    }

    private static void eachPictureIsMadeFinerWithWhatItIsMadeOf() {
        String[][] expected = {
            {"F_Hair_White.png", "hair"}, {"Hair_Blonde.png", "hair"},
            {"Clothes/Trousers_Mesh/TrousersMesh_DenimLight.png", "twill"},
            {"Clothes/Shirt_Tshirt_Textures/Tshirt_White.png", "knit"},
            {"Clothes/Shirt_Tshirt_Textures/Shirt_LumberjackRed.png", "weave"},
            {"Clothes/Jumper/Jumper_Roundneck.png", "wool"},
            {"Clothes/Jacket/Jacket_Leather_Black.png", "leather"},
            {"Clothes/Bag/DuffelBagBlue.png", "canvas"}, {"Clothes/Bag/Satchel_Leather.png", "leather"},
            {"Clothes/Shoes_Socks_Textres/WhiteSocks_Long.png", "knit"}, {"Clothes/Shoes_Socks_Textres/BlackShoes.png", "leather"},
            {"Clothes/Shoes_Socks_Textres/Trainers_Blue.png", "weave"},
            {"Clothes/Dress_Textures/Skirt_GarbageBag.png", "sleek"}, {"Clothes/Dress_Textures/DressSmall_Straps.png", "weave"},
            {"Clothes/BolierSuit/LongJohns.png", "knit"}, {"Clothes/BolierSuit/Boilersuit_Blue.png", "canvas"},
            {"patches/patches_chest_denim.png", "twill"}, {"patches/patches_back_leather.png", "leather"},
            {"ClothingJeans.png", "twill"}, {"Shirt_Something.png", "weave"},
            {"BloodTextures/BloodOverlay.png", "plain"}, {"BodyDmg/M_ZedDmg_BulletChest01.png", "plain"},
            {"shirtdecals/RockDECAL.png", "plain"}, {"Body/Stubble/M_Beard_Stubble.png", "plain"}, {"Body/Makeup/Makeup_0011_Clown1.png", "plain"},
            {"Clothes/Jewellery/Belly_Ring_Gold.png", "plain"}, {"Glasses_Normal.png", "plain"}, {"Necklace_Gold.png", "plain"},
            {"AviatorGlasses.png", "plain"}, {"SkiGoggles_Blue.png", "plain"}, {"Clothes/Armor/BoneMask.png", "plain"}, {"Clothes/Hazmat/Hazmat.png", "plain"},
            {"Clothes/Bag/ToolBox.png", "plain"}, {"Clothes/Bag/GuitarCase.png", "plain"}, {"Clothes/Bag/GarbageBag.png", "sleek"},
            {"Clothes/Hat/DustMask.png", "sleek"}, {"Clothes/Hat/BandanaMask_Red.png", "weave"}, {"Clothes/Hat/BeanyHatWhite.png", "wool"},
            {"Clothes/Jacket/Jacket_CowHolstein.png", "leather"}, {"Clothes/Jacket/Jacket_ShellSuit_Blue.png", "sleek"},
            {"Clothes/Gloves/DishGloves.png", "sleek"}, {"Clothes/Gloves/LeatherGloves_Black.png", "leather"}, {"Clothes/Gloves/FingerlessGloves_RagDenim.png", "weave"},
            {"Clothes/Apron/Apron_White.png", "canvas"}, {"Clothes/Apron/Apron_Leather.png", "leather"}, {"Clothes/Apron/apron_Tarp.png", "sleek"},
            {"Clothes/Trousers_Mesh/Tights_Black.png", "knit"}, {"Clothes/Shoes_Socks_Textres/Wellies_White.png", "sleek"},
            {"Clothes/Shirt_Tshirt_Textures/Shirt_FormalShortSleeve.png", "weave"}, {"Clothes/Dress_Textures/Dress.png", "weave"},
            {"F_Trousers_White.png", "twill"}, {"F_Blouse_White.png", "weave"},
        };
        for (String[] each : expected) {
            check(fabric(each[0]).equals(each[1]), each[0] + " is made finer with " + each[1] + ": " + fabric(each[0]));
        }
        check(FineSkins.Jar.row("Body/M_ZedBody02.png") == Own.ZOMBIES && FineSkins.Jar.row("Body/FemaleBody03.png") == Own.PEOPLE
                && FineSkins.Jar.row("Clothes/Hat/Hat_Beany.png") == Own.CLOTHES && FineSkins.Jar.row("F_Hair_White.png") == Own.CLOTHES,
                "and each answers to its own row of [Content]");
    }

    private static void aMaskAndWhatIsNotWornAreLeftAlone() {
        for (String left : new String[] {"Body/MaleBody01.png", "Body/M_ZedBody02_level1.png", "Body/Masks/Chest.png", "Body/Mask.png",
            "BloodTextures/BloodMaskChest.png", "HoleTextures/BloodMaskChest.png", "Clothes/Jacket/Masks/Chest.png", "Clothes/Mask_Jacket/LeftArm.png",
            "Clothes/Jumper/Roundneck/Chest.png", "Clothes/Jumper/Roundneck/Belt.png", "Clothes/Shirt_Tshirt_Textures/Bowling_Masks/Head.png",
            "Clothes/Dress_Textures/JudgeRobeMask/Dress.png", "Clothes/Jacket/Masks/Mask.png",
            "patches/patchesmask.png", "mask_transparency_player.png", "WorldItems/Axe.png", "weapons/1handed/ChairLeg.png", "WorldItems/HairDryer.png",
            "chainsaw.png", "BeerBottle.png", "DeerHeadress02.png", "Vehicles/vehicle_pickup.png"}) {
            check(Tailor.of(left) == null, left + " is left as the game has it: " + fabric(left));
        }
    }

    private static void aPictureFineEnoughIsLeftAlone() {
        check(Tailor.times(256, 256, false) == 4 && Tailor.times(128, 128, false) == 8 && Tailor.times(64, 64, false) == 8
                && Tailor.times(512, 512, false) == 2 && Tailor.times(128, 256, false) == 4, "a picture is enlarged to about 1024 dots, eight times at most");
        check(Tailor.times(300, 200, false) == 3 && Tailor.times(100, 100, false) == 8 && Tailor.times(200, 400, false) == 2,
                "by whole numbers, whatever its size");
        check(Tailor.times(256, 256, true) == 2 && Tailor.times(64, 64, true) == 8 && Tailor.times(512, 512, true) == 0 && Tailor.times(300, 200, true) == 0,
                "and to about 512 where the mod's pictures are read at half size, or left alone where that is under twice");
        check(Tailor.times(1024, 1024, false) == 0 && Tailor.times(888, 330, false) == 0 && Tailor.times(0, 0, false) == 0 && Tailor.times(2, 2, false) == 0
                && Tailor.times(1, 1, false) == 0, "one over 512 dots is left as it is, and so is one of a dot or two");
    }

    private static byte[] fine(int[] source, int wide, int high, int times, float[] laid, boolean edged) {
        ByteBuffer out = ByteBuffer.allocate(wide * times * high * times * 4);
        Tailor.fine(source, wide, high, times, laid, edged, out);
        check(!out.hasRemaining(), "every dot of the finer picture is written");
        return out.array();
    }

    /** Red, green and blue of the dot at a place in a finer picture so many dots wide. */
    private static int[] at(byte[] made, int wide, int x, int y) {
        int from = (y * wide + x) * 4;
        return new int[] {made[from] & 0xff, made[from + 1] & 0xff, made[from + 2] & 0xff, made[from + 3] & 0xff};
    }

    private static void anEdgeBetweenTwoColoursStaysAnEdge() {
        // Orange above and to the left of a slanting edge, teal below it, with a little paint noise in each.
        for (int times : new int[] {2, 4, 8}) {
            int wide = 12, high = 12, across = wide * times;
            int[] source = new int[wide * high];
            for (int y = 0; y < high; y++) {
                for (int x = 0; x < wide; x++) {
                    int noise = (x * 7 + y * 13) % 5 * 3;
                    source[y * wide + x] = x + y < 11 ? 0xff000000 | (200 + noise) << 16 | 120 << 8 | 20 : 0xff000000 | 20 << 16 | (120 + noise) << 8 | 200;
                }
            }
            byte[] made = fine(source, wide, high, times, null, true);
            int mixed = 0, orange = 0, teal = 0;
            for (int y = 0; y < high * times; y++) {
                for (int x = 0; x < across; x++) {
                    int[] dot = at(made, across, x, y);
                    boolean isOrange = dot[0] > 185 && dot[2] < 40 && Math.abs(dot[1] - 120) < 8, isTeal = dot[2] > 185 && dot[0] < 40 && Math.abs(dot[1] - 126) < 12;
                    orange += isOrange ? 1 : 0;
                    teal += isTeal ? 1 : 0;
                    mixed += isOrange || isTeal ? 0 : 1;
                    // Well away from the edge each side is its own colour, wherever on the picture it is.
                    float sx = (x + 0.5f) / times, sy = (y + 0.5f) / times;
                    check(sx + sy > 9.5f || isOrange, "above the edge is orange at " + x + "," + y + " enlarged " + times + " times");
                    check(sx + sy < 13.5f || isTeal, "and below it teal at " + x + "," + y + " enlarged " + times + " times");
                }
            }
            // Twelve dots of the game's lie along the edge. Enlarged plainly the mixture is a whole one of them broad,
            // which is times dots: here it is half that, and never under one dot.
            check(mixed <= 12 * times * Math.max(1, times / 2), "few dots along the edge are a mixture of the two, enlarged " + times + " times: " + mixed);
            check(orange > 50 * times * times && teal > 50 * times * times, "and both colours are there: " + orange + ", " + teal);
        }
    }

    private static void aPictureNotSquareIsEnlargedEachWayAlike() {
        // A dot of white on black, off the middle both ways.
        int wide = 12, high = 6, times = 4;
        int[] source = new int[wide * high];
        Arrays.fill(source, 0xff000000);
        source[4 * wide + 9] = 0xffffffff;
        byte[] made = fine(source, wide, high, times, null, true);
        int bestX = 0, bestY = 0, best = -1;
        for (int y = 0; y < high * times; y++) {
            for (int x = 0; x < wide * times; x++) {
                int light = at(made, wide * times, x, y)[1];
                if (light > best) {
                    best = light;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        check(bestX / times == 9 && bestY / times == 4, "a dot of the game's stays where it was: " + bestX + "," + bestY);
        // Its light falls off alike to either side of its own middle: enlarging does not shift the picture by part of a dot.
        int middleX = 9 * times + times / 2, middleY = 4 * times + times / 2;
        check(at(made, wide * times, middleX - 3, middleY)[1] == at(made, wide * times, middleX + 2, middleY)[1]
                && at(made, wide * times, middleX, middleY - 3)[1] == at(made, wide * times, middleX, middleY + 2)[1], "and is not shifted by part of a dot");
        check(at(made, wide * times, 2, 2)[1] == 0 && at(made, wide * times, 9 * times + 1, 2)[1] == 0, "and nothing of it is anywhere else");
    }

    private static void aGarmentsEdgeIsCutCleanAndAStainsFades() {
        int wide = 8, high = 8, times = 4;
        int[] source = new int[wide * high];
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < 4; x++) {
                source[y * wide + x] = 0xff808080;
            }
        }
        check(Tailor.edged(source), "a picture that is solid or see-through and nothing between ends at an edge");
        byte[] cloth = fine(source, wide, high, times, null, true), stain = fine(source, wide, high, times, null, false);
        int partlyCloth = 0, partlyStain = 0;
        for (int x = 0; x < wide * times; x++) {
            int clothShows = at(cloth, wide * times, x, 16)[3], stainShows = at(stain, wide * times, x, 16)[3];
            partlyCloth += clothShows > 8 && clothShows < 247 ? 1 : 0;
            partlyStain += stainShows > 8 && stainShows < 247 ? 1 : 0;
        }
        check(partlyCloth <= 3 && partlyStain >= 5, "a garment's edge is a dot or two broad, a stain's fades over more: " + partlyCloth + ", " + partlyStain);
        check(at(cloth, wide * times, 0, 16)[3] == 255 && at(cloth, wide * times, 31, 16)[3] == 0, "solid stays solid and see-through see-through");
        int[] fading = new int[64];
        for (int at = 0; at < fading.length; at++) {
            fading[at] = at % 8 * 36 << 24 | 0x400000;
        }
        check(!Tailor.edged(fading), "and one that fades is left to");
    }

    private static void aLineOneDotBroadIsKept() {
        // A strap one dot broad down the picture, and a stud of one dot beside it.
        for (int times : new int[] {2, 4, 8}) {
            int wide = 12, high = 12, across = wide * times;
            int[] source = new int[wide * high];
            for (int y = 0; y < high; y++) {
                source[y * wide + 3] = 0xff204080;
            }
            source[6 * wide + 8] = 0xffc0c0c0;
            check(Tailor.edged(source), "a strap and a stud are solid or nothing");
            byte[] made = fine(source, wide, high, times, null, true);
            int strap = 0, stud = 0;
            for (int x = 0; x < across; x++) {
                strap = Math.max(strap, at(made, across, x, 3 * times)[3]);
            }
            for (int y = 5 * times; y < 8 * times; y++) {
                for (int x = 7 * times; x < 10 * times; x++) {
                    stud = Math.max(stud, at(made, across, x, y)[3]);
                }
            }
            check(strap >= 250 && stud >= 200, "both still show, enlarged " + times + " times: " + strap + ", " + stud);
            int[] middle = at(made, across, 3 * times + times / 2, 3 * times);
            check(Math.abs(middle[0] - 0x20) <= 2 && Math.abs(middle[1] - 0x40) <= 2 && Math.abs(middle[2] - 0x80) <= 2, "in the strap's own colour: " + Arrays.toString(middle));
        }
    }

    private static void thePaintWithinAColourIsEvenedOut() {
        // One colour with paint noise of twelve steps in its red: evened out, far less is left. Two colours a hundred steps apart stay apart.
        int wide = 12, high = 12, times = 4;
        int[] source = new int[wide * high];
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                source[y * wide + x] = 0xff000000 | (120 + (x * 7 + y * 13) % 5 * 3) << 16 | 0x6040;
            }
        }
        byte[] made = fine(source, wide, high, times, null, true);
        int least = 255, most = 0;
        for (int y = 2 * times; y < 10 * times; y++) {
            for (int x = 2 * times; x < 10 * times; x++) {
                int red = at(made, wide * times, x, y)[0];
                least = Math.min(least, red);
                most = Math.max(most, red);
            }
        }
        check(most - least <= 6, "paint twelve steps apart comes out no more than six apart: " + least + " to " + most);
        check(Math.abs((least + most) / 2 - 126) <= 3, "about its own middle: " + least + " to " + most);
    }

    private static void whatIsSeeThroughLendsNoColour() {
        // The game's files have a colour where nothing shows: here one very like the cloth's, which only its not showing keeps out.
        int wide = 8, high = 8, times = 4;
        int[] source = new int[wide * high];
        for (int y = 0; y < high; y++) {
            for (int x = 0; x < wide; x++) {
                source[y * wide + x] = x < 4 ? 0xff808080 : 0x00a0a0a0;
            }
        }
        for (boolean edged : new boolean[] {true, false}) {
            byte[] made = fine(source, wide, high, times, null, edged);
            for (int x = 0; x < wide * times; x++) {
                int[] dot = at(made, wide * times, x, 16);
                if (dot[3] > 0) {
                    check(Math.abs(dot[0] - 128) <= 1 && Math.abs(dot[1] - 128) <= 1 && Math.abs(dot[2] - 128) <= 1,
                            "the colour at the edge is the cloth's alone at " + x + ": " + dot[0]);
                } else {
                    // Beside the edge the cloth's colour is carried on under what does not show, so that a draw that reads
                    // between two dots finds cloth there and not black. Further off nothing is written.
                    check(dot[0] == 0 || Math.abs(dot[0] - 128) <= 1, "and where nothing shows the colour is the cloth's or none at " + x + ": " + dot[0]);
                }
            }
        }
    }

    private static void theFabricLightensAndDarkensAboutTheGamesColour() {
        // A fabric of stripes two dots broad down it and four dots broad across it, a quarter lighter and darker than its middle.
        int size = 16;
        byte[] light = new byte[size * size];
        for (int at = 0; at < light.length; at++) {
            light[at] = (byte) (at % size / 2 % 2 == 0 ? 159 : 96);
        }
        float[] laid = Tailor.tile(light, size, 32, 2);
        check(laid.length == 16 * 16, "one repeat of a fabric across a picture of 32 dots that it crosses twice is 16 dots");
        float mean = 0f;
        for (float each : laid) {
            mean += each / laid.length;
        }
        check(Math.abs(mean - 1f) < 0.01f, "a fabric neither lightens nor darkens what it is laid on, taken all together: " + mean);
        check(laid[0] > 1.15f && laid[2] < 0.85f, "and each of its threads does");
        float[] coarse = Tailor.tile(light, size, 16, 4);
        check(coarse.length == 16 && Math.abs(coarse[0] - 1f) < 0.01f, "a repeat too small to show a thread is the mean of the threads in each of its dots: " + coarse[0]);
        int[] source = new int[8 * 8];
        Arrays.fill(source, 0xff6080a0);
        byte[] made = fine(source, 8, 8, 4, laid, true);
        int[] on = at(made, 32, 0, 5), between = at(made, 32, 2, 5), next = at(made, 32, 16, 21);
        check(on[0] > 0x60 + 10 && on[1] > 0x80 + 14 && on[2] > 0xa0 + 15, "the game's colour is kept and lightened on a thread: " + Arrays.toString(on));
        check(between[0] < 0x60 - 10 && between[1] < 0x80 - 14 && between[2] < 0xa0 - 15, "and darkened between two: " + Arrays.toString(between));
        check(Arrays.equals(on, next), "and the fabric repeats across and down the picture");
    }

    private static void aGreyPictureIsReadAsPainted() {
        BufferedImage grey = new BufferedImage(2, 1, BufferedImage.TYPE_BYTE_GRAY);
        grey.getRaster().setSample(0, 0, 0, 100);
        grey.getRaster().setSample(1, 0, 0, 200);
        int[] dots = Tailor.dots(grey);
        check(dots[0] == 0xff646464 && dots[1] == 0xffc8c8c8, "a picture stored as greys comes out with the numbers it has: " + Integer.toHexString(dots[0]));
        BufferedImage deep = new BufferedImage(1, 1, BufferedImage.TYPE_USHORT_GRAY);
        deep.getRaster().setSample(0, 0, 0, 100 << 8 | 7);
        check(Tailor.dots(deep)[0] == 0xff646464, "one with two bytes a grey by the upper of them: " + Integer.toHexString(Tailor.dots(deep)[0]));
        BufferedImage colour = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        colour.setRGB(0, 0, 0x80102030);
        check(Tailor.dots(colour)[0] == 0x80102030, "and one stored as colours as it is");
    }

    private static void everyFabricIsInTheJar() {
        for (String name : new String[] {"weave", "twill", "knit", "wool", "leather", "sleek", "canvas", "hair"}) {
            try {
                byte[] light = Tailor.light(name);
                long total = 0;
                double apart = 0;
                for (byte each : light) {
                    total += each & 0xff;
                }
                float mean = total / (float) light.length;
                for (byte each : light) {
                    apart += Math.abs((each & 0xff) - mean);
                }
                float spread = (float) (apart / light.length);
                check(light.length == Tailor.FABRIC_SIZE * Tailor.FABRIC_SIZE && Math.abs(mean - 127.5f) < 4f && spread > 22f && spread < 38f,
                        "the fabric " + name + " is 1024 dots a side, its middle half way up, with about a quarter of that in light and dark: " + mean + ", " + spread);
            } catch (IOException failure) {
                throw new AssertionError("the fabric " + name + " is not in the jar", failure);
            }
        }
    }

    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}
