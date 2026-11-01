package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;
import zombie.characters.IsoGameCharacter;
import zombie.iso.IsoObject;

/**
 * What a shotgun's blast does to a wooden door, a board fence or a wall of plasterboard it strikes.
 * The game fires a blast's pellets one after another in a tick, and the mod is told where each struck
 * (ImpactFinder). Once the tick is over the pellets that struck one thing are looked at together:
 * where enough of them landed close together they blow a hole through it, sized to how close they
 * were, and the rest leave a mark each on a door, as a single pellet does, or the round hole of a
 * pellet that went on through plasterboard. Far off the pellets have spread apart and only mark it.
 * Where a hole is blown, what it blew out flies from the far face along the blast's way: each pellet
 * of the hole throws pieces and dust from where it came out, bigger and faster than a round's, and
 * what the hole cuts free of the thing (Breakage) bursts out as it goes (fellOut).
 * Logic thread.
 */
final class Blasts {
    private Blasts() {}

    /** The fewest pellets that blow a hole, and how far from their middle each may have struck, in tiles. */
    private static final int FEWEST = 3;
    private static final float CLOSE = 0.12f;
    /** How much past the furthest pellet of a hole's the hole reaches, and the least and most its radius is, in tiles. */
    static final float BITE = 0.03f, SMALLEST = 0.05f, LARGEST = 0.16f;
    /** How big what a blown hole throws out of the far face is, as Impacts.Struck's size: beside a handgun's round, which is 1. */
    static final float BLOWN_OUT = 1.6f;
    /**
     * What a piece cut free throws out: a burst for each PIECE_AREA of it in square tiles, at the most
     * MOST_BURSTS a hole, each as big as Impacts.Struck's size says, 1 and up by PIECE_SIZE for each tile
     * across the piece is, to PIECE_LARGEST.
     */
    static final float PIECE_AREA = 0.02f, PIECE_SIZE = 4f, PIECE_LARGEST = 2.5f;
    static final int MOST_BURSTS = 6;

    /**
     * A pellet that struck a thing: who fired it, where on the thing (as BlastHoles measures it), the side it came from, the
     * radius of the round's hole it leaves where it went on through and no blast's hole takes it, 0 for none, that hole's
     * slant along and up (BlastHoles.SLANT_ALONG, SLANT_UP), and what it throws out of the far face where a hole it is of is blown.
     */
    record Struck(IsoGameCharacter shooter, IsoObject object, boolean north, float along, float up, int side, float round, float slantAlong, float slantUp,
            Impacts.Struck out) {}

    private static final ArrayList<Struck> struck = new ArrayList<>();

    /** A pellet struck a thing BlastHoles.blastable says a blast can blow a hole in: round and out as Struck has them. */
    static void pellet(IsoGameCharacter shooter, IsoObject object, boolean north, float along, float up, int side, float round, float slantAlong, float slantUp,
            Impacts.Struck out) {
        if (BlastHoles.through(object, north, along, up)) {
            return;
        }
        struck.add(new Struck(shooter, object, north, along, up, side, round, slantAlong, slantUp, out));
    }

    /** Called as a tick of the world starts: the pellets of the last tick's blasts are settled. */
    static void tick() {
        if (struck.isEmpty()) {
            return;
        }
        ArrayList<Struck> group = new ArrayList<>();
        while (!struck.isEmpty()) {
            Struck first = struck.getFirst();
            group.clear();
            for (int i = struck.size() - 1; i >= 0; i--) {
                Struck each = struck.get(i);
                if (each.shooter == first.shooter && each.object == first.object && each.north == first.north) {
                    group.add(each);
                    struck.remove(i);
                }
            }
            settle(group);
        }
    }

    /**
     * One blast's pellets on one thing: a hole where enough struck close together, with what it blew
     * out flying from the far face, a round's hole for each other that went through, a mark for each
     * other on a door.
     */
    private static void settle(ArrayList<Struck> group) {
        Cluster cluster = cluster(group);
        if (cluster != null) {
            Breakage.Pieces before = BlastHoles.fallen(cluster.object, cluster.north);
            BlastHoles.blast(cluster.object, cluster.north, cluster.along, cluster.up, cluster.radius, BlastHoles.newSeed(), group.getFirst().side);
            for (Struck member : cluster.members) {
                Impacts.add(member.out);
            }
            Struck first = cluster.members.getFirst();
            fellOut(cluster.object, cluster.north, before, first.out, first.along, first.up);
        }
        boolean door = DoorGlass.isDoor(group.getFirst().object);
        for (Struck each : group) {
            if (cluster != null && cluster.took(each)) {
                continue;
            }
            if (each.round > 0f) {
                BlastHoles.add(each.object, each.north, each.along, each.up, each.round, BlastHoles.newSeed(), BlastHoles.ROUND * each.side, each.slantAlong, each.slantUp);
            } else if (door) {
                BlastHoles.add(each.object, each.north, each.along, each.up, BlastHoles.MARK_RADIUS, BlastHoles.newSeed(), each.side);
            }
        }
    }

    /**
     * What a new hole in the object on its north or west edge cut free, beside what had fallen of it
     * before the hole (BlastHoles.fallen), bursts out of its far face as it goes: pieces and dust
     * from across the pieces that went, by how much went (see PIECE_AREA). at is what the shot threw
     * out of the far face where it went through, at along and up on the object as BlastHoles
     * measures them, which places the rest. Pieces cut free of the next wall along by a hole that
     * reaches over the join burst from the next shot that holes that one.
     */
    static void fellOut(IsoObject object, boolean north, Breakage.Pieces before, Impacts.Struck at, float along, float up) {
        ArrayList<Impacts.Struck> bursts = burstsOf(BlastHoles.fallen(object, north).rectangles, before, north, at, along, up, ThreadLocalRandom.current());
        for (Impacts.Struck burst : bursts) {
            Impacts.add(burst);
        }
        if (DevBuild.ON && !bursts.isEmpty()) {
            Log.info(String.format(Locale.ROOT, "a hole cut a piece free of the %s on the %s edge: %d bursts of size %.2f out of its far face",
                    DoorGlass.isDoor(object) ? "door" : object.getSprite() == null ? "thing" : object.getSprite().getName(), north ? "north" : "west",
                    bursts.size(), bursts.getFirst().size()));
        }
    }

    /**
     * The bursts fellOut throws for the pieces now fallen (Breakage.Pieces' rectangles, along in tiles
     * and up in levels) that had not fallen before, from places picked by random in them.
     */
    static ArrayList<Impacts.Struck> burstsOf(float[][] after, Breakage.Pieces before, boolean north, Impacts.Struck at, float along, float up, RandomGenerator random) {
        ArrayList<Impacts.Struck> bursts = new ArrayList<>();
        float[] areas = new float[after.length];
        float went = 0f;
        for (int k = 0; k < after.length; k++) {
            float[] r = after[k];
            areas[k] = before.inside((r[0] + r[2]) / 2f, (r[1] + r[5]) / 2f) ? 0f : (r[2] - r[0]) * (r[5] - r[1]) * ViewCamera.LEVEL_HEIGHT;
            went += areas[k];
        }
        if (went <= 0f) {
            return bursts;
        }
        int count = Math.clamp(Math.round(went / PIECE_AREA), 1, MOST_BURSTS);
        float size = Math.min(1f + PIECE_SIZE * (float) Math.sqrt(went), PIECE_LARGEST);
        for (int b = 0; b < count; b++) {
            // A place in what went, each piece as likely as its share of it.
            float pick = random.nextFloat() * went;
            int k = 0;
            for (int j = 0; j < after.length; j++) {
                if (areas[j] > 0f) {
                    k = j;
                    if ((pick -= areas[j]) <= 0f) {
                        break;
                    }
                }
            }
            float[] r = after[k];
            float a = r[0] + (r[2] - r[0]) * random.nextFloat() - along, u = (r[1] + (r[5] - r[1]) * random.nextFloat() - up) * ViewCamera.LEVEL_HEIGHT;
            bursts.add(new Impacts.Struck(at.x() + (north ? a : 0f), at.y() + (north ? 0f : a), at.up() + u, at.normalX(), at.normalY(), at.normalUp(),
                    at.wayX(), at.wayY(), at.wayUp(), Math.min(at.floor(), at.up() + u), at.kind(), size, false, at.grainX(), at.grainY(), at.grainUp()));
        }
        return bursts;
    }

    /** Where a blast's pellets on a thing blew a hole: its middle, its radius, and the pellets it took. */
    record Cluster(IsoObject object, boolean north, float along, float up, float radius, ArrayList<Struck> members) {
        /** Whether the hole took this very pellet: another that struck the same place is still a pellet of its own. */
        boolean took(Struck pellet) {
            for (Struck member : members) {
                if (member == pellet) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * The hole a blast's pellets on one thing blow, or null: the pellets furthest from the middle of
     * those left are let go one at a time until every one left is within CLOSE of their middle, and
     * a hole is blown where FEWEST or more are left. Distances are in tiles, up as well as along.
     */
    static Cluster cluster(ArrayList<Struck> group) {
        ArrayList<Struck> left = new ArrayList<>(group);
        while (left.size() >= FEWEST) {
            float along = 0f, up = 0f;
            for (Struck each : left) {
                along += each.along;
                up += each.up;
            }
            along /= left.size();
            up /= left.size();
            int furthest = -1;
            float most = -1f;
            for (int i = 0; i < left.size(); i++) {
                float d = distance(left.get(i), along, up);
                if (d > most) {
                    most = d;
                    furthest = i;
                }
            }
            if (most <= CLOSE) {
                float radius = Math.clamp(most + BITE, SMALLEST, LARGEST);
                if (DevBuild.ON) {
                    Log.info(String.format(Locale.ROOT, "a blast: %d of its %d pellets on the %s struck within %.3f tiles of their middle", left.size(), group.size(),
                            struckThing(group), most));
                }
                return new Cluster(group.getFirst().object, group.getFirst().north, along, up, radius, left);
            }
            left.remove(furthest);
        }
        if (DevBuild.ON && group.size() > 1) {
            Log.info(String.format(Locale.ROOT, "a blast: its %d pellets on the %s struck too far apart to blow a hole", group.size(), struckThing(group)));
        }
        return null;
    }

    /** What a blast's pellets struck, for the log: a door, or the sprite of the fence or the wall. */
    private static String struckThing(ArrayList<Struck> group) {
        IsoObject object = group.getFirst().object;
        return DoorGlass.isDoor(object) ? "door" : object.getSprite() == null ? "thing" : object.getSprite().getName();
    }

    private static float distance(Struck each, float along, float up) {
        float da = each.along - along, du = (each.up - up) * ViewCamera.LEVEL_HEIGHT;
        return (float) Math.sqrt(da * da + du * du);
    }

    /** Forgets the pellets not settled yet: the save is left. */
    static void forget() {
        struck.clear();
    }
}
