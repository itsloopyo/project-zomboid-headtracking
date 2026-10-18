package com.cameraunlock.zomboid;

import java.util.WeakHashMap;
import zombie.GameTime;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.IsoGridSquare;
import zombie.vehicles.BaseVehicle;

/**
 * The snow each vehicle and each person the picture draws carries, kept from frame to frame
 * by the game's hours: see FigureSnow. Asked on the render thread as each is drawn, in every
 * view and pass alike, and moved on by the game hours since it was last asked, so a second ask
 * in the same moment changes nothing. What the game unloads is let go with it. Nothing in Low,
 * which draws vehicles and people as the game does.
 */
final class SnowCarried {
    private SnowCarried() {}

    private static final class Kept {
        final float[] car = new float[FigureSnow.FLOATS];
        float dust;
        double at;
    }

    private static final WeakHashMap<BaseVehicle, Kept> cars = new WeakHashMap<>();
    private static final WeakHashMap<IsoGameCharacter, Kept> people = new WeakHashMap<>();
    private static final float[] NONE = new float[FigureSnow.FLOATS];

    /** What the vehicle carries as a view draws it, by FigureSnow's ROOF, BONNET and SCREEN: not to be changed by the caller. */
    static float[] on(int view, BaseVehicle vehicle) {
        double now = GameTime.getInstance().getWorldAgeHours();
        IsoGridSquare square = vehicle.getCurrentSquare();
        boolean open = square != null && square.isOutside();
        boolean running = vehicle.isEngineRunning();
        float lying = SkyState.snowLyingNow();
        Kept kept = cars.get(vehicle);
        if (kept == null) {
            kept = new Kept();
            FigureSnow.carSeen(kept.car, lying, open, running);
            kept.at = now;
            cars.put(vehicle, kept);
        } else {
            FigureSnow.carOn(kept.car, lying, SkyState.snowFallingNow(), open, running, vehicle.getCurrentSpeedKmHour(), (float) (now - kept.at));
            kept.at = now;
        }
        return Quality.plain(view) ? NONE : kept.car;
    }

    /** How much snow has settled on the person as a view draws them, 0 to 1. */
    static float on(int view, IsoGameCharacter person) {
        double now = GameTime.getInstance().getWorldAgeHours();
        IsoGridSquare square = person.getCurrentSquare();
        boolean outside = square != null && square.isOutside();
        float snowing = SkyState.snowFallingNow();
        Kept kept = people.get(person);
        if (kept == null) {
            kept = new Kept();
            kept.dust = FigureSnow.personSeen(!(person instanceof IsoPlayer) || person instanceof IsoAnimal, outside, snowing, SkyState.snowLyingNow());
            kept.at = now;
            people.put(person, kept);
        } else {
            kept.dust = FigureSnow.personOn(kept.dust, snowing, SkyState.snowLyingNow(), outside, (float) (now - kept.at));
            kept.at = now;
        }
        return Quality.plain(view) ? 0f : kept.dust;
    }
}
