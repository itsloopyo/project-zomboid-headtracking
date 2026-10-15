package com.cameraunlock.zomboid;

import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/**
 * The shape of a person's mouth as they are drawn, with TalkingMouths on: a player at this screen
 * by what they say into their own microphone (Microphones), and another player online while the
 * game's voice chat is playing them, which says only that they are speaking, so their mouth moves
 * as a voice's does without following its words. Anyone else's stays shut.
 *
 * The render thread asks, once for each person in each pass of the picture and once for each
 * part of a person the game draws itself (GameMouths).
 */
final class Mouths {
    /**
     * How a mouth is shaped: how far it is open, 0 to 1, and its lips, spread towards 1 ("ee",
     * "s") and rounded and pushed out towards -1 ("oo", "o").
     */
    record Shape(float open, float lips) {
        static final Shape SHUT = new Shape(0f, 0f);

        boolean moving() {
            return open > 0f || lips != 0f;
        }

        /**
         * How far apart the lips are drawn, 0 to 1: the jaw's drop, or a third of how far the
         * lips are spread or rounded, which part them in the middle however little the jaw
         * drops (lipped in CharacterDraw.JAW). The gap is as dark as an open mouth's.
         */
        float parted() {
            return Math.max(open, Math.abs(lips) / 3f);
        }
    }

    /** The syllables of a voice heard over voice chat: how often the mouth opens and closes, and how often the lips change shape, in times a second. */
    private static final float SYLLABLES = 4.6f, PHRASES = 1.3f, VOWELS = 1.9f;

    private static final long STARTED = System.nanoTime();
    private static volatile boolean on;
    /** For each other player whose voice has been heard: how far their mouth is open, how their lips are shaped, and when that was worked out. */
    private static final WeakHashMap<IsoPlayer, float[]> heard = new WeakHashMap<>();

    private Mouths() {}

    static void set(boolean talking) {
        on = talking;
    }

    static boolean on() {
        return on;
    }

    /** The shape of a person's mouth now. */
    static Shape of(IsoGameCharacter character) {
        if (!on || !(character instanceof IsoPlayer player)) {
            return Shape.SHUT;
        }
        if (player.isLocalPlayer()) {
            int at = player.getPlayerNum();
            return at >= 0 && at < View.COUNT ? Microphones.mouth(at) : Shape.SHUT;
        }
        float now = (System.nanoTime() - STARTED) / 1e9f;
        float[] mouth = heard.get(player);
        if (mouth == null) {
            if (!player.isSpeek) {
                return Shape.SHUT;
            }
            mouth = new float[] {0f, 0f, now};
            heard.put(player, mouth);
        }
        float seconds = Math.max(now - mouth[2], 0f);
        mouth[2] = now;
        float wanted = player.isSpeek ? chatter(now, player.getOnlineID()) : 0f;
        float time = wanted > mouth[0] ? Voice.OPENS : Voice.CLOSES;
        mouth[0] += (wanted - mouth[0]) * (1f - (float) Math.exp(-seconds / time));
        float lips = player.isSpeek ? vowels(now, player.getOnlineID()) : 0f;
        mouth[1] += (lips - mouth[1]) * (1f - (float) Math.exp(-seconds / Voice.SHAPES));
        return new Shape(mouth[0], mouth[1]);
    }

    /** How far a mouth speaking at that time is open: a syllable at a time, louder and quieter through a phrase, each player out of step with the next. */
    static float chatter(float seconds, int who) {
        float phase = who * 0.618f;
        float syllable = (float) Math.sin(2 * Math.PI * (SYLLABLES * seconds + phase));
        float phrase = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (PHRASES * seconds + 1.7f * phase));
        return Math.max(syllable, 0f) * (0.35f + 0.55f * phrase);
    }

    /** How the lips of a mouth speaking at that time are shaped: spread and rounded in turn, out of step with the syllables. */
    static float vowels(float seconds, int who) {
        float phase = who * 0.382f;
        return 0.7f * (float) Math.sin(2 * Math.PI * (VOWELS * seconds + phase)) * (float) Math.sin(2 * Math.PI * (0.61f * seconds + 2.3f * phase));
    }
}
