package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;
import zombie.GameTime;
import zombie.characters.IsoZombie;
import zombie.core.utils.BoundedQueue;
import zombie.iso.IsoChunk;
import zombie.iso.IsoFloorBloodSplat;
import zombie.iso.IsoGridSquare;

/**
 * The blood of a cut-off limb: a spray of it on the floor round the zombie as the limb comes
 * off, and while a zombie with a limb off moves, a trail of drops behind it.
 *
 * Both are the game's own floor blood, laid where the game keeps it, so the game's view and the
 * mod's draw them both as they draw any blood. The spray stays as the game's blood does. The
 * trail is aged faster than the game dries blood, until it is as dark and faint as dried blood
 * gets, and then taken off the floor: the game never takes blood away, and a trail behind every
 * crawler would cover the map.
 *
 * Logic thread only.
 */
final class Bleeding {
    private Bleeding() {}

    /** How many splats a limb coming off throws round the zombie, and how far, in tiles. */
    static final int SPRAY = 9;
    static final float SPRAY_REACH = 0.9f;
    /** How far, in tiles, a zombie with a limb off moves between one drop of its trail and the next. */
    static final float DROP_EVERY = 0.35f;
    /** Game hours a drop of the trail takes to dry, and then to go. */
    static final double TRAIL_DRIES = 1.0, TRAIL_GOES = 1.5;
    /** The game's hours for blood to dry: a drop is aged by up to this to look dried. */
    private static final double GAME_DRIES = Blood.DRIES_IN_HOURS;
    /** The game's floor blood pictures: eight small, eight medium, four large. */
    private static final int SMALL = 0, MEDIUM = 8, LARGE = 16;

    private record Drop(IsoChunk chunk, IsoFloorBloodSplat splat, double born, int level) {}

    /** Where each cut zombie last dropped blood. */
    private static final Map<IsoZombie, float[]> lastDrop = new WeakHashMap<>();
    private static final ArrayList<Drop> trail = new ArrayList<>();
    private static final ArrayList<Object> cut = new ArrayList<>();
    private static final Random random = new Random();
    private static double aged;
    /** For the trace: when the trail was last described. */
    private static long said;

    /** Throws a limb's blood round a zombie as the limb comes off. */
    static void spray(IsoZombie zombie) {
        spray(zombie, SPRAY, SPRAY_REACH);
    }

    /** Throws {@code count} splats of blood round a zombie, out to {@code reach} tiles. */
    static void spray(IsoZombie zombie, int count, float reach) {
        IsoGridSquare square = zombie.getCurrentSquare();
        if (square == null) {
            return;
        }
        for (int i = 0; i < count; i++) {
            float angle = random.nextFloat() * (float) (2 * Math.PI), out = reach * (float) Math.sqrt(random.nextFloat());
            lay(zombie.getX() + out * (float) Math.cos(angle), zombie.getY() + out * (float) Math.sin(angle), zombie.getZ(),
                    i < 3 ? LARGE + random.nextInt(4) : MEDIUM + random.nextInt(8), false);
        }
    }

    /**
     * A mist of blood thrown out of a zombie at {@code high} tiles over its feet by a shot from
     * (fromX, fromY): the impacts' blood of a shot into flesh, {@code count} of them blown on the
     * way the shot went, spread to either side and nearly level, and {@code size} times a handgun
     * round's. Drawn only by the first person picture, as a shot's is, so its feet are
     * where the picture stands them, on the land as the picture lifts it (ImpactFinder.flesh).
     */
    static void mist(IsoZombie zombie, float high, int count, float size, float fromX, float fromY) {
        View view = View.first();
        if (zombie.getCurrentSquare() == null || view == null) {
            return;
        }
        float x = zombie.getX(), y = zombie.getY();
        float floor = zombie.getZ() * ViewCamera.LEVEL_HEIGHT + view.worldScene.terrain.lift(x, y, zombie.getZ());
        float away = (float) Math.atan2(y - fromY, x - fromX);
        for (int i = 0; i < count; i++) {
            float angle = away + (random.nextFloat() - 0.5f) * 2f * MIST_SPREAD, rise = MIST_LOWEST + (MIST_HIGHEST - MIST_LOWEST) * random.nextFloat();
            float flat = (float) Math.sqrt(1f - rise * rise);
            float nx = flat * (float) Math.cos(angle), ny = flat * (float) Math.sin(angle);
            Impacts.add(new Impacts.Struck(x + nx * 0.1f, y + ny * 0.1f, floor + high, nx, ny, rise, -nx, -ny, -rise, floor, Impacts.FLESH, size, false, 0f, 0f, 1f));
        }
    }

    /** How far to either side of the shot's way a mist is blown, in radians, and how far down and up, as the sine of it. */
    static final float MIST_SPREAD = 1.4f, MIST_LOWEST = -0.2f, MIST_HIGHEST = 0.35f;

    /** Blood where a piece lands. */
    static void splash(float x, float y, float z) {
        for (int i = 0; i < 3; i++) {
            lay(x + (random.nextFloat() - 0.5f) * 0.3f, y + (random.nextFloat() - 0.5f) * 0.3f, z, i == 0 ? LARGE + random.nextInt(4) : MEDIUM + random.nextInt(8), false);
        }
    }

    /** Called once a game tick: drops behind cut zombies that moved, and the trail aged and taken up. */
    static void tick(Wounds wounds) {
        double now = GameTime.getInstance().getWorldAgeHours();
        if (wounds.anyoneCut()) {
            cut.clear();
            wounds.cutZombies(cut);
            for (Object each : cut) {
                if (each instanceof IsoZombie zombie && !zombie.isDead() && zombie.getCurrentSquare() != null) {
                    float[] last = lastDrop.computeIfAbsent(zombie, z -> new float[] {zombie.getX(), zombie.getY()});
                    float dx = zombie.getX() - last[0], dy = zombie.getY() - last[1];
                    if (dx * dx + dy * dy >= DROP_EVERY * DROP_EVERY) {
                        last[0] = zombie.getX();
                        last[1] = zombie.getY();
                        lay(zombie.getX() + (random.nextFloat() - 0.5f) * 0.2f, zombie.getY() + (random.nextFloat() - 0.5f) * 0.2f, zombie.getZ(),
                                random.nextInt(3) == 0 ? MEDIUM + random.nextInt(8) : SMALL + random.nextInt(8), true);
                    }
                }
            }
        }
        synchronized (trail) {
            age(now);
        }
    }

    /** Ages the trail and takes up what has gone. Under the trail's lock. */
    private static void age(double now) {
        // Aged by the game hour: each step dries the trail by as much as the game's blood dries in hours, which is when both views draw it again.
        if (FirstPerson.TRACE && !trail.isEmpty() && System.nanoTime() - said > 1_000_000_000L) {
            said = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "bleeding: %d drops of trail, the oldest %.3f game hours old, the world %.3f hours", trail.size(), now - trail.get(0).born, now));
        }
        if (trail.isEmpty() || now - aged < 1.0 / 60.0) {
            return;
        }
        aged = now;
        int before = trail.size();
        for (Iterator<Drop> it = trail.iterator(); it.hasNext(); ) {
            Drop drop = it.next();
            double age = now - drop.born;
            if (age >= TRAIL_GOES || !drop.chunk.loaded) {
                if (drop.chunk.loaded) {
                    take(drop);
                }
                it.remove();
                continue;
            }
            float worldAge = (float) (drop.born - GAME_DRIES * Math.min(1.0, age / TRAIL_DRIES));
            if (Math.floor(now - worldAge) != Math.floor(now - drop.splat.worldAge)) {
                drop.splat.worldAge = worldAge;
                drop.chunk.invalidateRenderChunkLevel(drop.level, 1L);
            }
        }
        if (FirstPerson.TRACE && trail.size() != before) {
            Log.info(String.format(Locale.ROOT, "bleeding: %d drops of trail taken up, %d left", before - trail.size(), trail.size()));
        }
    }

    private static void lay(float x, float y, float z, int type, boolean fades) {
        IsoGridSquare square = zombie.iso.IsoWorld.instance.currentCell.getGridSquare((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
        if (square == null || square.getChunk() == null) {
            return;
        }
        IsoChunk chunk = square.getChunk();
        BoundedQueue<IsoFloorBloodSplat> splats = chunk.floorBloodSplats;
        int before = splats.size();
        IsoFloorBloodSplat last = before == 0 ? null : splats.get(before - 1);
        chunk.addBloodSplat(x, y, z, type);
        IsoFloorBloodSplat laid = splats.size() == 0 ? null : splats.get(splats.size() - 1);
        if (fades && laid != null && laid != last) {
            synchronized (trail) {
                trail.add(new Drop(chunk, laid, laid.worldAge, square.getZ()));
            }
        }
    }

    /** The trail's drops lifted off each chunk the game is writing to its save, to be put back once it has. */
    private static final Map<IsoChunk, List<IsoFloorBloodSplat>> lifted = new IdentityHashMap<>();

    /**
     * Called as the game writes a chunk to its save: the trail's drops on it are lifted off the
     * floor first. Saved, they would be the game's own blood after a load, which nothing takes up.
     */
    static void saving(IsoChunk chunk) {
        synchronized (trail) {
            List<IsoFloorBloodSplat> off = null;
            for (Drop drop : trail) {
                if (drop.chunk != chunk) {
                    continue;
                }
                BoundedQueue<IsoFloorBloodSplat> splats = chunk.floorBloodSplats;
                for (int i = splats.size() - 1; i >= 0; i--) {
                    if (splats.get(i) == drop.splat) {
                        splats.remove(i);
                        if (off == null) {
                            off = new ArrayList<>();
                        }
                        off.add(drop.splat);
                        break;
                    }
                }
            }
            if (off != null) {
                lifted.put(chunk, off);
            }
        }
    }

    /** Called once the game has written a chunk to its save, or thrown writing it: the drops lifted off it go back. */
    static void saved(IsoChunk chunk) {
        synchronized (trail) {
            List<IsoFloorBloodSplat> off = lifted.remove(chunk);
            if (off != null) {
                off.forEach(chunk.floorBloodSplats::add);
            }
        }
    }

    private static void take(Drop drop) {
        BoundedQueue<IsoFloorBloodSplat> splats = drop.chunk.floorBloodSplats;
        for (int i = splats.size() - 1; i >= 0; i--) {
            if (splats.get(i) == drop.splat) {
                splats.remove(i);
                drop.chunk.invalidateRenderChunkLevel(drop.level, 1L);
                return;
            }
        }
    }

}
