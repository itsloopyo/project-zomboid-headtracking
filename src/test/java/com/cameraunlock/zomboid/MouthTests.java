package com.cameraunlock.zomboid;

import static com.cameraunlock.zomboid.Checks.check;

/** Tests of how far a mouth opens: from a microphone's blocks of sound, and from another player's voice over voice chat. */
final class MouthTests {
    private MouthTests() {}

    /** One block of the microphone, 20 ms. */
    private static final float BLOCK = 0.02f;

    static void run() {
        loudnessIsTheRootMeanSquareOfTheSamples();
        silenceKeepsTheMouthShut();
        aSyllableOpensTheMouthAndItClosesAfter();
        aSteadyHumIsTheRoomsNoiseAndKeepsTheMouthShut();
        speechOverAHumStillOpensTheMouth();
        aVoiceOverChatOpensAndClosesTheMouth();
    }

    private static void loudnessIsTheRootMeanSquareOfTheSamples() {
        byte[] block = new byte[640];
        for (int i = 0; i < 320; i++) {
            int sample = i % 2 == 0 ? 16384 : -16384;
            block[2 * i] = (byte) sample;
            block[2 * i + 1] = (byte) (sample >> 8);
        }
        check(Math.abs(Microphones.Voice.loudness(block, block.length) - 0.5f) < 1e-6f,
                "a square wave at half of full scale, its negative half included, is 0.5 loud: " + Microphones.Voice.loudness(block, block.length));
        check(Microphones.Voice.loudness(new byte[640], 640) == 0f && Microphones.Voice.loudness(block, 0) == 0f,
                "silence, and a block with nothing in it, are 0");
    }

    private static void silenceKeepsTheMouthShut() {
        Microphones.Voice voice = new Microphones.Voice();
        float open = 0f;
        for (int i = 0; i < 200; i++) {
            open = Math.max(open, voice.step(decibels(-80f), BLOCK));
        }
        check(open == 0f, "four seconds of a quiet room never open the mouth: " + open);
    }

    private static void aSyllableOpensTheMouthAndItClosesAfter() {
        Microphones.Voice voice = new Microphones.Voice();
        for (int i = 0; i < 50; i++) {
            voice.step(decibels(-62f), BLOCK);
        }
        float open = 0f;
        for (int i = 0; i < 5; i++) {
            open = voice.step(decibels(-22f), BLOCK);
        }
        check(open > 0.9f, "a tenth of a second of a voice 40 dB over the room opens the mouth nearly wide: " + open);
        for (int i = 0; i < 15; i++) {
            open = voice.step(decibels(-62f), BLOCK);
        }
        check(open < 0.02f, "and three tenths of a second of the room again shuts it: " + open);
        float half = 0f;
        for (int i = 0; i < 5; i++) {
            half = voice.step(decibels(-40f), BLOCK);
        }
        check(half > 0.4f && half < 0.55f, "a word 22 dB over the room opens it about half way: " + half);
    }

    private static void aSteadyHumIsTheRoomsNoiseAndKeepsTheMouthShut() {
        Microphones.Voice voice = new Microphones.Voice();
        float open = 0f;
        for (int i = 0; i < 500; i++) {
            open = Math.max(open, voice.step(decibels(-35f), BLOCK));
        }
        check(open == 0f, "a hum 20 dB over the quietest a voice opens the mouth at, from the first block, never opens it: " + open);
    }

    private static void speechOverAHumStillOpensTheMouth() {
        Microphones.Voice voice = new Microphones.Voice();
        for (int i = 0; i < 100; i++) {
            voice.step(decibels(-40f), BLOCK);
        }
        float open = 0f;
        for (int i = 0; i < 5; i++) {
            open = voice.step(decibels(-15f), BLOCK);
        }
        check(open > 0.5f, "a voice 25 dB over a hum opens the mouth: " + open);
    }

    private static void aVoiceOverChatOpensAndClosesTheMouth() {
        float least = 1f, most = 0f;
        for (int step = 0; step < 2000; step++) {
            float open = Mouths.chatter(step * 0.005f, 7);
            least = Math.min(least, open);
            most = Math.max(most, open);
        }
        check(least == 0f && most > 0.7f && most <= 0.9f, "over ten seconds a voice over chat shuts the mouth and opens it to between 0.7 and 0.9: " + least
                + " to " + most);
        check(Mouths.chatter(1.234f, 7) != Mouths.chatter(1.234f, 8), "two players speaking at once do not move their mouths together");
    }

    /** A block's loudness at that many decibels of full scale. */
    private static float decibels(float level) {
        return (float) Math.pow(10, level / 20.0);
    }
}
