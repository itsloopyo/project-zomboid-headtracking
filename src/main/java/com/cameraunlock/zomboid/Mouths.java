package com.cameraunlock.zomboid;

import java.util.WeakHashMap;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;

/**
 * How far a person's mouth is open as they are drawn, with TalkingMouths on: a player at this
 * screen by how loudly they speak into their own microphone (Microphones), and another player
 * online while the game's voice chat is playing them, which says only that they are speaking, so
 * their mouth moves as a voice's does without following its words. Anyone else's stays shut.
 *
 * The render thread asks, once for each person in each pass.
 */
final class Mouths {
    /** The syllables of a voice heard over voice chat: how often the mouth opens and closes, in times a second. */
    private static final float SYLLABLES = 4.6f, PHRASES = 1.3f;

    private static final long STARTED = System.nanoTime();
    private static volatile boolean on;
    /** For each other player whose voice has been heard: how far their mouth is open, and when that was worked out. */
    private static final WeakHashMap<IsoPlayer, float[]> heard = new WeakHashMap<>();

    private Mouths() {}

    static void set(boolean talking) {
        on = talking;
    }

    static boolean on() {
        return on;
    }

    /** How far a person's mouth is open now, 0 to 1. */
    static float open(IsoGameCharacter character) {
        if (!on || !(character instanceof IsoPlayer player)) {
            return 0f;
        }
        if (player.isLocalPlayer()) {
            int at = player.getPlayerNum();
            return at >= 0 && at < View.COUNT ? Microphones.open(at) : 0f;
        }
        float now = (System.nanoTime() - STARTED) / 1e9f;
        float[] mouth = heard.get(player);
        if (mouth == null) {
            if (!player.isSpeek) {
                return 0f;
            }
            mouth = new float[] {0f, now};
            heard.put(player, mouth);
        }
        float seconds = Math.max(now - mouth[1], 0f);
        mouth[1] = now;
        float wanted = player.isSpeek ? chatter(now, player.getOnlineID()) : 0f;
        float time = wanted > mouth[0] ? Microphones.Voice.OPENS : Microphones.Voice.CLOSES;
        mouth[0] += (wanted - mouth[0]) * (1f - (float) Math.exp(-seconds / time));
        return mouth[0];
    }

    /** How far a mouth speaking at that time is open: a syllable at a time, louder and quieter through a phrase, each player out of step with the next. */
    static float chatter(float seconds, int who) {
        float phase = who * 0.618f;
        float syllable = (float) Math.sin(2 * Math.PI * (SYLLABLES * seconds + phase));
        float phrase = 0.5f + 0.5f * (float) Math.sin(2 * Math.PI * (PHRASES * seconds + 1.7f * phase));
        return Math.max(syllable, 0f) * (0.35f + 0.55f * phrase);
    }
}
