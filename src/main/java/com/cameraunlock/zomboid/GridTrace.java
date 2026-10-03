package com.cameraunlock.zomboid;

/**
 * Walks a ray across the tile grid and finds the first tile edge it cannot
 * cross. The map is squares with walls on their edges, so every wall a ray can
 * meet is at a crossing from one square to the one beside it.
 */
final class GridTrace {
    /** Whether something stands on the edge between two squares that share a side. */
    interface Edges {
        boolean blocked(int fromX, int fromY, int toX, int toY);
    }

    /** Set by the last firstBlockedEdge that found one: true when the edge's normal runs along x. */
    boolean hitFacesX;

    /**
     * The distance along the ray to the first blocked edge, or positive
     * infinity when there is none within reach. The direction's two
     * components need not be a unit vector by themselves: they are the ground
     * part of a unit vector in three dimensions, and the distance returned is
     * along that vector.
     */
    float firstBlockedEdge(Edges edges, float startX, float startY, float directionX, float directionY, float reach) {
        int tileX = (int) Math.floor(startX);
        int tileY = (int) Math.floor(startY);
        int stepX = directionX > 0f ? 1 : -1;
        int stepY = directionY > 0f ? 1 : -1;
        float nextX = directionX == 0f ? Float.POSITIVE_INFINITY
                : ((directionX > 0f ? tileX + 1 : tileX) - startX) / directionX;
        float nextY = directionY == 0f ? Float.POSITIVE_INFINITY
                : ((directionY > 0f ? tileY + 1 : tileY) - startY) / directionY;
        float perTileX = directionX == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / directionX);
        float perTileY = directionY == 0f ? Float.POSITIVE_INFINITY : Math.abs(1f / directionY);

        while (true) {
            boolean crossX = nextX <= nextY;
            float distance = crossX ? nextX : nextY;
            if (distance > reach) {
                return Float.POSITIVE_INFINITY;
            }
            int toX = crossX ? tileX + stepX : tileX;
            int toY = crossX ? tileY : tileY + stepY;
            if (edges.blocked(tileX, tileY, toX, toY)) {
                hitFacesX = crossX;
                return distance;
            }
            tileX = toX;
            tileY = toY;
            if (crossX) {
                nextX += perTileX;
            } else {
                nextY += perTileY;
            }
        }
    }
}
