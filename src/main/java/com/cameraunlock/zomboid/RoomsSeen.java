package com.cameraunlock.zomboid;

/**
 * Whether the player has seen a room, taken from the room as a whole. The game marks a room's
 * squares seen one by one, as they come into the player's sight, and leaves patches unseen in a
 * room the player stands in. A square is lit by one rule while seen and another while not, so
 * read square by square those patches showed on the floor, walls and ceiling as blotches and
 * streaks. A room is seen when most of its squares on the map are.
 */
final class RoomsSeen {
    private RoomsSeen() {}

    /** Whether the player has seen the square at a place: SEEN, UNSEEN, or NONE where no square is loaded. */
    interface Squares {
        int at(int x, int y);
    }

    static final int NONE = -1, UNSEEN = 0, SEEN = 1;

    /** Whether most of the loaded squares of a room laid in the rectangles given, x, y, width and height in turn, are seen. */
    static boolean most(int[] rects, Squares squares) {
        int seen = 0, unseen = 0;
        for (int r = 0; r < rects.length; r += 4) {
            for (int y = rects[r + 1]; y < rects[r + 1] + rects[r + 3]; y++) {
                for (int x = rects[r]; x < rects[r] + rects[r + 2]; x++) {
                    int state = squares.at(x, y);
                    seen += state == SEEN ? 1 : 0;
                    unseen += state == UNSEEN ? 1 : 0;
                }
            }
        }
        return seen > unseen;
    }
}
