package com.cameraunlock.zomboid;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Which show each of the game's television broadcasts and video tapes is, and how a set
 * shows one: the jar's /pictures/shows.txt, made by tools/pictures/schedule.py from the game's
 * own files. A show is a handful of shots, the pictures screen-name-0 and on. A set cuts from
 * one shot to another every few seconds, and through each the camera pushes in, pulls back
 * or pans, so the picture moves in every frame the game draws.
 *
 * A line of the file is tab separated: "show", a show's name and how many shots it has;
 * "channel", a frequency and the show of a broadcast on it the file does not list; "air", a
 * broadcast's id and its show; "tape", a tape's id and its show.
 */
final class Shows {
    /** The first two shows of the file: the one of no one programme, and interference, which has no shots. */
    static final int ANY = 0, SNOW = 1;
    /** About how many cuts a show makes before it makes the same ones again: a whole number of times round its shots. */
    static final int CUTS = 16;
    /** The shortest and the longest a shot is held. */
    static final long SHORTEST_NANOS = 2_000_000_000L, LONGEST_NANOS = 4_500_000_000L;
    /** The least of a shot's width the picture is of, pushed in furthest. */
    static final float CLOSEST = 0.78f;
    /** What cut fills: the shot, where across and down it the middle of the picture is, and how much of its width and height the picture is of. */
    static final int SHOT = 0, ACROSS = 1, DOWN = 2, OF = 3, CUT_FLOATS = 4;

    private final List<String> names = new ArrayList<>();
    private final List<Integer> shots = new ArrayList<>();
    private final Map<String, Integer> aired = new HashMap<>(), taped = new HashMap<>();
    private final Map<Integer, Integer> channels = new HashMap<>();
    /** For each show, when each of its cuts ends, counted from the start of a round of them, and the shot each is of. */
    private long[][] ends;
    private int[][] order;

    static Shows read(BufferedReader lines, String path) throws IOException {
        Shows read = new Shows();
        Map<String, Integer> numbers = new HashMap<>();
        for (String line = lines.readLine(); line != null; line = lines.readLine()) {
            String[] parts = line.split("\t");
            if (parts.length != 3) {
                throw new IOException(path + " has a line that is not three things: " + line);
            }
            if (parts[0].equals("show")) {
                numbers.put(parts[1], read.names.size());
                read.names.add(parts[1]);
                read.shots.add(Integer.parseInt(parts[2]));
                continue;
            }
            Integer show = numbers.get(parts[2]);
            if (show == null) {
                throw new IOException(path + " has a line for a show not listed before it: " + line);
            }
            switch (parts[0]) {
                case "channel" -> read.channels.put(Integer.parseInt(parts[1]), show);
                case "air" -> read.aired.put(parts[1], show);
                case "tape" -> read.taped.put(parts[1], show);
                default -> throw new IOException(path + " has a line that is no show, channel, broadcast or tape: " + line);
            }
        }
        if (read.names.size() <= SNOW || !read.names.get(ANY).equals("any") || !read.names.get(SNOW).equals("snow") || read.shots.get(ANY) == 0
                || read.shots.get(SNOW) != 0) {
            throw new IOException(path + " does not begin with the show of no one programme and then interference");
        }
        read.ends = new long[read.names.size()][];
        read.order = new int[read.names.size()][];
        for (int show = 0; show < read.names.size(); show++) {
            int shots = read.shots.get(show);
            if (shots > 0) {
                int cuts = shots * Math.max(1, Math.round(CUTS / (float) shots));
                read.ends[show] = new long[cuts];
                read.order[show] = new int[cuts];
                lay(show, shots, read.ends[show], read.order[show]);
            }
        }
        return read;
    }

    /**
     * One round of a show's cuts: how long each shot is held and which shot each is. The cuts
     * go round the shots a whole number of times, a step at a time that has no factor in
     * common with how many there are, so every shot has its turn, none comes twice running,
     * and the round's last is not its first.
     */
    private static void lay(int show, int shots, long[] ends, int[] order) {
        int step = 1;
        for (int tried = 0; shots > 2 && (tried == 0 || common(step, shots) != 1); tried++) {
            step = 1 + (int) (chance(show, tried, 1) * (shots - 1));
        }
        int shot = (int) (chance(show, 0, 6) * shots);
        long at = 0;
        for (int cut = 0; cut < ends.length; cut++) {
            at += SHORTEST_NANOS + (long) (chance(show, cut, 0) * (LONGEST_NANOS - SHORTEST_NANOS));
            ends[cut] = at;
            order[cut] = shot;
            shot = (shot + step) % shots;
        }
    }

    private static int common(int a, int b) {
        return b == 0 ? a : common(b, a % b);
    }

    /** A number from 0 up to 1 that is the same for the same three every time. */
    private static float chance(int a, int b, int c) {
        long mixed = (a * 0x9E3779B97F4A7C15L + b * 0xC2B2AE3D27D4EB4FL + c * 0x165667B19E3779F9L + 0x27D4EB2F165667C5L) * 0xBF58476D1CE4E5B9L;
        mixed = (mixed ^ mixed >>> 31) * 0x94D049BB133111EBL;
        return ((mixed ^ mixed >>> 29) >>> 40) / (float) (1L << 24);
    }

    int count() {
        return names.size();
    }

    String name(int show) {
        return names.get(show);
    }

    int shots(int show) {
        return shots.get(show);
    }

    /** The name in the jar of a shot of a show. */
    String picture(int show, int shot) {
        return "screen-" + names.get(show) + "-" + shot;
    }

    /**
     * The show of a broadcast by its id on a channel by its frequency. A broadcast the file
     * does not list, or none, is the channel's own show, and on a channel the file does not
     * list either it is the show of no one programme.
     */
    int aired(int frequency, String broadcast) {
        Integer show = broadcast == null ? null : aired.get(broadcast);
        if (show == null) {
            show = channels.get(frequency);
        }
        return show == null ? ANY : show;
    }

    /** The show of a tape by its id: the show of no one programme for a tape the file does not list. */
    int taped(String tape) {
        Integer show = taped.get(tape);
        return show == null ? ANY : show;
    }

    /**
     * What a set showing a show shows at a time: into out the shot, and what part of it the
     * picture is, which moves all the while the shot is held. Every set showing the show shows
     * the same. Not for a show with no shots.
     */
    void cut(int show, long nanos, float[] out) {
        long[] end = ends[show];
        long into = Math.floorMod(nanos, end[end.length - 1]);
        int cut = 0;
        while (into >= end[cut]) {
            cut++;
        }
        long from = cut == 0 ? 0 : end[cut - 1];
        float through = (into - from) / (float) (end[cut] - from);
        out[SHOT] = order[show][cut];
        // How the camera moves through a shot, by the cut: it pushes in on a place, pulls back from one, or pans across.
        float kind = chance(show, cut, 2);
        float fromOf, toOf, fromAcross, toAcross, fromDown, toDown;
        if (kind < 0.36f) {
            fromOf = 1f;
            toOf = CLOSEST;
            fromAcross = toAcross = chance(show, cut, 3);
            fromDown = toDown = chance(show, cut, 4);
        } else if (kind < 0.64f) {
            fromOf = CLOSEST;
            toOf = 1f;
            fromAcross = toAcross = chance(show, cut, 3);
            fromDown = toDown = chance(show, cut, 4);
        } else {
            fromOf = toOf = CLOSEST + 0.06f;
            boolean rightwards = chance(show, cut, 3) < 0.5f;
            fromAcross = rightwards ? 0f : 1f;
            toAcross = 1f - fromAcross;
            fromDown = chance(show, cut, 4);
            toDown = chance(show, cut, 5);
        }
        float of = fromOf + (toOf - fromOf) * through;
        // The middle keeps half the picture's width from the shot's edges, so the picture never looks past them.
        float room = 1f - of;
        out[OF] = of;
        out[ACROSS] = of / 2f + room * (fromAcross + (toAcross - fromAcross) * through);
        out[DOWN] = of / 2f + room * (fromDown + (toDown - fromDown) * through);
    }
}
