package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

import java.util.Random;

/**
 * Tests of the shape of a mouth: from a microphone's blocks of sound, made here as a voice is (a
 * buzz from the throat through the mouth's resonances), and from another player's voice over voice
 * chat.
 */
final class MouthTests {
    private MouthTests() {}

    /** One block of the microphone, 20 ms. */
    private static final int BLOCK = 320;
    private static final float SECONDS = BLOCK / (float) Voice.RATE;

    /** The first three formants of vowels as a man says them, in hertz. */
    private static final float[] AH = {730, 1090, 2440}, EE = {270, 2290, 3010}, OO = {300, 870, 2240}, OH = {500, 900, 2400};

    static void run() {
        loudnessIsTheRootMeanSquareOfTheSamples();
        silenceKeepsTheMouthShut();
        aSyllableOpensTheMouthAndItClosesAfter();
        aSteadyHumIsTheRoomsNoiseAndKeepsTheMouthShut();
        speechOverAHumStillOpensTheMouth();
        ahDropsTheJawWithTheLipsAsTheyAre();
        eeSpreadsTheLipsOverAJawNearlyShut();
        ooAndOhRoundTheLips();
        aHissKeepsTheTeethNearlyTogether();
        theLipsKeepTheirShapeBetweenSyllables();
        aVoiceOverChatOpensAndClosesTheMouthAndShapesItsLips();
        eeShowsBothRowsOfTeethWithADarkLineBetween();
        aWideMouthShowsTheTongueInTheMiddleOnly();
        aMouthNearlyShutShowsNoTongue();
    }

    private static void loudnessIsTheRootMeanSquareOfTheSamples() {
        byte[] block = new byte[640];
        for (int i = 0; i < 320; i++) {
            int sample = i % 2 == 0 ? 16384 : -16384;
            block[2 * i] = (byte) sample;
            block[2 * i + 1] = (byte) (sample >> 8);
        }
        check(Math.abs(Voice.loudness(block, block.length) - 0.5f) < 1e-6f,
                "a square wave at half of full scale, its negative half included, is 0.5 loud: " + Voice.loudness(block, block.length));
        check(Voice.loudness(new byte[640], 640) == 0f && Voice.loudness(block, 0) == 0f, "silence, and a block with nothing in it, are 0");
    }

    private static void silenceKeepsTheMouthShut() {
        Voice voice = new Voice();
        Sound room = new Sound();
        float open = 0f;
        for (int i = 0; i < 200; i++) {
            open = Math.max(open, step(voice, room.hiss(-80f)));
        }
        check(open == 0f, "four seconds of a quiet room never open the mouth: " + open);
    }

    private static void aSyllableOpensTheMouthAndItClosesAfter() {
        Voice voice = new Voice();
        Sound sound = new Sound();
        quiet(voice, sound, 50);
        float open = 0f;
        for (int i = 0; i < 6; i++) {
            open = step(voice, sound.vowel(AH, -22f));
        }
        check(open > 0.8f, "an eighth of a second of \"ah\" 40 dB over the room opens the mouth nearly wide: " + open);
        for (int i = 0; i < 15; i++) {
            open = step(voice, sound.hiss(-62f));
        }
        check(open < 0.02f, "and three tenths of a second of the room again shuts it: " + open);
        float half = 0f;
        for (int i = 0; i < 6; i++) {
            half = step(voice, sound.vowel(AH, -40f));
        }
        check(half > 0.35f && half < 0.6f, "an \"ah\" 22 dB over the room opens it about half way: " + half);
    }

    private static void aSteadyHumIsTheRoomsNoiseAndKeepsTheMouthShut() {
        Voice voice = new Voice();
        Sound sound = new Sound();
        float open = 0f;
        for (int i = 0; i < 500; i++) {
            open = Math.max(open, step(voice, sound.vowel(AH, -35f)));
        }
        check(open == 0f, "a hum 20 dB over the quietest a voice opens the mouth at, from the first block, never opens it: " + open);
    }

    private static void speechOverAHumStillOpensTheMouth() {
        Voice voice = new Voice();
        Sound sound = new Sound();
        for (int i = 0; i < 100; i++) {
            step(voice, sound.hiss(-40f));
        }
        float open = 0f;
        for (int i = 0; i < 6; i++) {
            open = step(voice, sound.vowel(AH, -15f));
        }
        check(open > 0.5f, "a voice 25 dB over a hum opens the mouth: " + open);
    }

    private static void ahDropsTheJawWithTheLipsAsTheyAre() {
        Voice voice = said(AH, 15);
        check(voice.open() > 0.85f && Math.abs(voice.lips()) < 0.2f, "\"ah\" drops the jaw nearly all the way and leaves the lips neither spread nor round: "
                + voice.open() + ", " + voice.lips());
    }

    private static void eeSpreadsTheLipsOverAJawNearlyShut() {
        Voice voice = said(EE, 15);
        check(voice.open() > 0.08f && voice.open() < 0.4f && voice.lips() > 0.7f, "\"ee\" opens the mouth a little and spreads the lips: " + voice.open()
                + ", " + voice.lips());
    }

    private static void ooAndOhRoundTheLips() {
        Voice oo = said(OO, 15), oh = said(OH, 15);
        check(oo.open() < 0.4f && oo.lips() < -0.7f, "\"oo\" opens the mouth a little and rounds the lips: " + oo.open() + ", " + oo.lips());
        check(oh.open() > oo.open() && oh.lips() < -0.4f, "\"oh\" opens it further, and rounds them too: " + oh.open() + ", " + oh.lips());
    }

    private static void aHissKeepsTheTeethNearlyTogether() {
        Voice voice = new Voice();
        Sound sound = new Sound();
        quiet(voice, sound, 50);
        for (int i = 0; i < 15; i++) {
            voice.step(sound.sharp(-25f), BLOCK * 2, SECONDS);
        }
        check(voice.open() > 0.05f && voice.open() < 0.2f && voice.lips() > 0.2f, "a hiss as loud as a word parts the lips a little and spreads them: "
                + voice.open() + ", " + voice.lips());
    }

    private static void theLipsKeepTheirShapeBetweenSyllables() {
        Voice voice = said(OO, 15);
        Sound sound = new Sound();
        for (int i = 0; i < 3; i++) {
            step(voice, sound.hiss(-62f));
        }
        check(voice.lips() < -0.5f, "60 ms of quiet after \"oo\" leaves the lips round: " + voice.lips());
        for (int i = 0; i < 25; i++) {
            step(voice, sound.hiss(-62f));
        }
        check(Math.abs(voice.lips()) < 0.05f && voice.open() < 0.01f, "and half a second of it lets them go as the mouth shuts: " + voice.lips());
    }

    private static void aVoiceOverChatOpensAndClosesTheMouthAndShapesItsLips() {
        float least = 1f, most = 0f, round = 0f, spread = 0f;
        for (int step = 0; step < 2000; step++) {
            float open = Mouths.chatter(step * 0.005f, 7), lips = Mouths.vowels(step * 0.005f, 7);
            least = Math.min(least, open);
            most = Math.max(most, open);
            round = Math.min(round, lips);
            spread = Math.max(spread, lips);
        }
        check(least == 0f && most > 0.7f && most <= 0.9f, "over ten seconds a voice over chat shuts the mouth and opens it to between 0.7 and 0.9: " + least
                + " to " + most);
        check(round < -0.4f && spread > 0.4f, "and rounds and spreads its lips: " + round + " to " + spread);
        check(Mouths.chatter(1.234f, 7) != Mouths.chatter(1.234f, 8), "two players speaking at once do not move their mouths together");
    }

    /** A Voice that has heard a quiet room and then a vowel for that many blocks, 37 dB over the room. */
    private static Voice said(float[] formants, int blocks) {
        Voice voice = new Voice();
        Sound sound = new Sound();
        quiet(voice, sound, 50);
        for (int i = 0; i < blocks; i++) {
            step(voice, sound.vowel(formants, -25f));
        }
        return voice;
    }

    /**
     * The posed height between the lips in the middle of the mouth, in the model as made: the strip
     * between the upper lip's front (0.8565) and the line where the lips meet (0.852) is 0.0045 shut,
     * and the jaw adds the drop of a point 0.063 ahead of its hinge turned by 0.17 radians wide open.
     * So "ah" (the jaw at 0.99) is about 0.0151, and "ee" (at 0.11, the lips spread, which drops the
     * middle of the lower lip 0.0015 more) about 0.0072.
     */
    private static final float EE_HEIGHT = 0.0072f, AH_HEIGHT = 0.0151f;

    private static void eeShowsBothRowsOfTeethWithADarkLineBetween() {
        GlslFunctions shader = new GlslFunctions(CharacterDraw.TEETH);
        check(shader.call("upperTeeth", 0.0005f) > 0.99f && shader.call("lowerTeeth", 0.0005f) > 0.99f,
                "with the lips spread for \"ee\" both rows of teeth show next to the lips");
        float darkest = 1f;
        for (float below = 0f; below <= EE_HEIGHT; below += 0.0001f) {
            float above = EE_HEIGHT - below;
            darkest = Math.min(darkest, shader.call("upperTeeth", below) + shader.call("lowerTeeth", above) + shader.call("tongue", above, EE_HEIGHT, 0f));
        }
        check(darkest < 0.01f, "and a dark line between the rows, with no tongue in it: " + darkest);
    }

    private static void aWideMouthShowsTheTongueInTheMiddleOnly() {
        GlslFunctions shader = new GlslFunctions(CharacterDraw.TEETH);
        float overTheTeeth = CharacterDraw.LOWER_TEETH + 0.001f;
        check(shader.call("tongue", overTheTeeth, AH_HEIGHT, 0f) > 0.99f, "\"ah\" shows the tongue over the lower teeth in the middle");
        check(shader.call("tongue", overTheTeeth, AH_HEIGHT, 0.8f) < 0.01f, "and not towards the corners");
        float below = CharacterDraw.UPPER_TEETH + 0.002f, above = AH_HEIGHT - below;
        check(shader.call("upperTeeth", below) + shader.call("lowerTeeth", above) + shader.call("tongue", above, AH_HEIGHT, 0f) < 0.01f,
                "and the dark of the mouth between the upper teeth and the tongue");
    }

    private static void aMouthNearlyShutShowsNoTongue() {
        GlslFunctions shader = new GlslFunctions(CharacterDraw.TEETH);
        float most = 0f;
        for (float above = 0f; above <= 0.0075f; above += 0.0001f) {
            most = Math.max(most, shader.call("tongue", above, 0.0075f, 0f));
        }
        check(most == 0f, "a mouth open a quarter of the way hides the tongue behind the teeth: " + most);
    }

    private static void quiet(Voice voice, Sound sound, int blocks) {
        for (int i = 0; i < blocks; i++) {
            step(voice, sound.hiss(-62f));
        }
    }

    private static float step(Voice voice, byte[] block) {
        voice.step(block, block.length, SECONDS);
        return voice.open();
    }

    /**
     * Sound as a microphone gives it, a block at a time: a room's hiss, a sharper one as "s" is,
     * or a vowel, a man's voice at 120 Hz through three resonances in turn.
     */
    private static final class Sound {
        private final Random random = new Random(4);
        private final double[][] filters = new double[3][2];
        private int sample;
        private double before;

        byte[] hiss(float decibels) {
            double[] made = new double[BLOCK];
            for (int i = 0; i < BLOCK; i++) {
                made[i] = random.nextGaussian();
            }
            return at(made, decibels);
        }

        byte[] sharp(float decibels) {
            double[] made = new double[BLOCK];
            for (int i = 0; i < BLOCK; i++) {
                double now = random.nextGaussian();
                made[i] = now - before;
                before = now;
            }
            return at(made, decibels);
        }

        byte[] vowel(float[] formants, float decibels) {
            double[] made = new double[BLOCK];
            for (int i = 0; i < BLOCK; i++, sample++) {
                double x = sample % (Voice.RATE / 120) == 0 ? 1 : 0;
                for (int f = 0; f < 3; f++) {
                    double bandwidth = 60 + 30 * f;
                    double radius = Math.exp(-Math.PI * bandwidth / Voice.RATE);
                    double b = 2 * radius * Math.cos(2 * Math.PI * formants[f] / Voice.RATE), c = -radius * radius;
                    double y = (1 - b - c) * x + b * filters[f][0] + c * filters[f][1];
                    filters[f][1] = filters[f][0];
                    filters[f][0] = y;
                    x = y;
                }
                made[i] = x + 0.001 * random.nextGaussian();
            }
            return at(made, decibels);
        }

        /** The samples, scaled to that loudness in decibels of full scale, as 16 bit samples little end first. */
        private static byte[] at(double[] made, float decibels) {
            double sum = 0;
            for (double s : made) {
                sum += s * s;
            }
            double scale = Math.pow(10, decibels / 20.0) / Math.sqrt(sum / made.length) * 32767;
            byte[] block = new byte[made.length * 2];
            for (int i = 0; i < made.length; i++) {
                int s = (int) Math.max(-32768, Math.min(32767, Math.round(made[i] * scale)));
                block[2 * i] = (byte) s;
                block[2 * i + 1] = (byte) (s >> 8);
            }
            return block;
        }
    }
}
