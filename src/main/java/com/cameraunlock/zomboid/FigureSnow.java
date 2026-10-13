package com.cameraunlock.zomboid;

/**
 * How much snow vehicles and people carry, worked out in the game's own hours, for the
 * shaders' snowOnVehicle and snowOnPerson (SnowShader).
 *
 * A vehicle carries snow on its top as the ground does, up to as much as lies on the ground:
 * it gathers it while snow falls on it out of doors, and loses it as the ground's thaws. One the
 * picture has not seen before has stood where it is through the winter and carries what the
 * ground does. A running engine warms the bonnet, which melts what is on it and what falls on
 * it, and the wipers and the heater keep the windscreen clear; at speed the wind takes the roof's
 * snow off, slowly. So a car driven and parked carries only what has fallen on it since.
 *
 * A person gathers a dusting while snow falls on them, more in a heavier fall, and sheds it
 * slowly out of doors and soon indoors. Zombies, and animals, are out in it all the time, and one
 * first seen carries what the fall gives; a player is taken to have stepped out just now.
 */
final class FigureSnow {
    private FigureSnow() {}

    /** Game hours a vehicle out of doors takes to gather most of the lying snow, in a fall of full intensity. */
    static final float CAR_GATHERS = 1.5f;
    /** Game hours a running engine takes to warm the bonnet, and a stopped one to cool. */
    static final float ENGINE_WARMS = 0.2f, ENGINE_COOLS = 0.5f;
    /** Of a full cover, how much a warm bonnet, and the heater on the windscreen, melt in a game hour. */
    static final float WARM_MELTS = 4f;
    /** Game hours the wipers take to clear most of the windscreen while the engine runs. */
    static final float WIPED = 0.02f;
    /** The speed in kilometres an hour over which the wind takes the roof's snow off, and over how many more it takes it off at once a game hour. */
    static final float BLOWN_FROM = 20f, BLOWN_PER = 40f;
    /** Game hours a person takes to gather most of a dusting in falling snow, to shed most of it out of doors once it stops, and indoors. */
    static final float PERSON_GATHERS = 0.4f, PERSON_SHEDS = 1.5f, PERSON_SHEDS_INDOORS = 0.1f;
    /** How much of the ground's lying snow a zombie first seen out of doors carries when none is falling. */
    static final float OUT_ALWAYS_KEEPS = 0.3f;

    /** Where in a vehicle's record each number is: snow on its roof, its bonnet, its windscreen, 0 to 1, and how warm its engine is, 0 to 1. */
    static final int ROOF = 0, BONNET = 1, SCREEN = 2, HEAT = 3, FLOATS = 4;

    /**
     * A vehicle the picture sees for the first time: out of doors, it has stood through the
     * snow there is and carries all of it, except on the bonnet and the windscreen while it runs.
     */
    static void carSeen(float[] car, float lying, boolean open, boolean running) {
        float carried = open ? Math.max(lying, 0f) : 0f;
        car[ROOF] = carried;
        car[BONNET] = running ? 0f : carried;
        car[SCREEN] = running ? 0f : carried;
        car[HEAT] = running ? 1f : 0f;
    }

    /**
     * A vehicle's record hours of game time on: lying is how much snow lies on the ground, 0 to
     * 1, and snowing how hard it falls, open whether the vehicle stands under the sky, running
     * whether its engine runs and speed how fast it goes in kilometres an hour.
     */
    static void carOn(float[] car, float lying, float snowing, boolean open, boolean running, float speed, float hours) {
        if (hours <= 0f) {
            return;
        }
        float most = Math.max(lying, 0f);
        float heat = running ? car[HEAT] + (1f - car[HEAT]) * (1f - (float) Math.exp(-hours / ENGINE_WARMS)) : car[HEAT] * (float) Math.exp(-hours / ENGINE_COOLS);
        float falls = open ? 1f - (float) Math.exp(-Math.max(snowing, 0f) * hours / CAR_GATHERS) : 0f;
        float melts = WARM_MELTS * heat * hours;
        float blown = Math.max(Math.abs(speed) - BLOWN_FROM, 0f) / BLOWN_PER;
        float roof = gathered(car[ROOF], most, falls) * (float) Math.exp(-blown * hours);
        float bonnet = gathered(car[BONNET], most, falls * (1f - heat)) - melts;
        float screen = running ? car[SCREEN] * (float) Math.exp(-hours / WIPED) - melts : gathered(car[SCREEN], most, falls * (1f - heat)) - melts;
        car[ROOF] = Math.clamp(roof, 0f, most);
        car[BONNET] = Math.clamp(bonnet, 0f, most);
        car[SCREEN] = Math.clamp(screen, 0f, most);
        car[HEAT] = heat;
    }

    /** What carried comes to with falls of the way to most gathered, and nothing taken off. */
    private static float gathered(float carried, float most, float falls) {
        return carried < most ? carried + (most - carried) * falls : carried;
    }

    /**
     * A person the picture sees for the first time, out of doors or not: one who is out in it
     * all the time carries what the fall gives them, and a little of what lies when none falls;
     * anyone else nothing yet.
     */
    static float personSeen(boolean outAlways, boolean outside, float snowing, float lying) {
        return outAlways && outside ? Math.clamp(Math.max(snowing, OUT_ALWAYS_KEEPS * lying), 0f, 1f) : 0f;
    }

    /**
     * How much snow is on a person who had dust on them, hours of game time on: gathered as hard
     * as it falls while they are out in it, and shed down to that otherwise. Where none falls and
     * none lies it is too warm for any to stay on them.
     */
    static float personOn(float dust, float snowing, float lying, boolean outside, float hours) {
        if (hours <= 0f) {
            return dust;
        }
        float falling = outside ? Math.clamp(snowing, 0f, 1f) : 0f;
        float kept = falling > dust ? dust + (falling - dust) * (1f - (float) Math.exp(-hours / PERSON_GATHERS))
                : Math.max(falling, dust * (float) Math.exp(-hours / (outside ? PERSON_SHEDS : PERSON_SHEDS_INDOORS)));
        return Math.min(kept, Math.max(Math.max(snowing, 0f), Math.max(lying, 0f)));
    }
}
