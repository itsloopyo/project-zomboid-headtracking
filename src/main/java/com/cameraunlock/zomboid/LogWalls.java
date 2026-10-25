package com.cameraunlock.zomboid;

/**
 * A log cabin's walls: walls_logs_0, 4 and 8 and 10 on a square's west edge, 1, 5, 9 and 11 on its north edge, and 2 on
 * both, ten round logs to a level, a window or a door cut through 8 to 11. Their sprites flattened onto a wall's skins
 * were lapped boards with a plain corner. WallBuilder lays the logs over the skins a course at a time as the model
 * log_course, or log_piece where a door or a window cuts the course, and where two walls cross at a corner each runs on
 * past it as a log_end. A wall along y lies a log's radius lower than one along x, so the ends pass between
 * each other's logs.
 */
final class LogWalls {
    private LogWalls() {}

    private static final String SHEET = "walls_logs_";
    /** A log's radius, in tiles: ten logs to a level. */
    static final float RADIUS = ViewCamera.LEVEL_HEIGHT / 20f;
    /** How long a log_piece is: a share of the square's edge. */
    static final float PIECE = 1f / 16f;
    /** How far in from an end of the edge, as a share of it, a piece at the end is looked for on the sprite. */
    static final float END_IN = 0.05f;
    /** Where the sprites paint a log the wood takes its colour from: halfway along the edge, and halfway up, in levels. */
    static final float DYE_ALONG = 0.5f, DYE_HIGH = 0.5f;

    /** Whether a sprite is a length of log wall. */
    static boolean is(String name) {
        return name != null && switch (Sheets.place(name, SHEET)) {
            case 0, 1, 2, 4, 5, 8, 9, 10, 11 -> true;
            default -> false;
        };
    }
}
