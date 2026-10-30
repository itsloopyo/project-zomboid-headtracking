package com.cameraunlock.zomboid;

import zombie.core.textures.Texture;
import zombie.iso.SpriteDetails.IsoObjectType;

/**
 * The stairs of a chunk being built. One to a frame of the world's picture, and it writes into that frame's meshes.
 */
final class StairBuilder {
    private final WorldScene.Frame build;

    StairBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /**
     * A flight is three squares, each a third of a level, rising towards -x (W) or -y (N): see
     * Stairs. Each square is built as the four steps its sprite paints, every tread and riser
     * showing what the sprite has there, and what carries them is built with them: see Flights.
     * Returns false, adding nothing, for a sprite that is not a stair.
     */
    boolean addStair(SpriteGeometry.Mesh mesh, Texture texture, IsoObjectType type, int x, int y, int z) {
        int third = WorldScene.thirdOf(type);
        if (third < 0) {
            return false;
        }
        boolean west = WorldScene.climbsWest(type);
        // A flight the sprite paints with a side is closed: risers, a string down each side
        // from the steps to the underside, the underside itself, and a back under the top
        // step. A fire escape or a flight of logs has no side painted. Its treads are slabs
        // with air between them, and what the sprite shows through the gaps is not on them.
        // The side the sprite shows is the one towards the game's view, south of a flight
        // that climbs west and east of one that climbs north.
        float middle = third + 0.5f, band = middle / 3f - Stairs.STRING / 2f;
        float sideA = west ? 0.5f : 1f, sideB = west ? 1f : 0.5f;
        boolean closed = build.painted(texture, sideA, sideB, band);
        // Without the mod's buildings an open flight is its sprite's four steps, each riser showing what the sprite has there.
        float riser = closed || !Own.buildings() ? Stairs.RISE : Flights.TREAD;
        // A closed timber flight's treads are laid in the picture's own boards, one to a tread,
        // in the colour of the wood: the sprite's few texels of a tread drawn a whole step deep
        // were a streaky blur. The boards are counted from the world's place, so the treads of
        // flights side by side run on into each other with no seam where they meet.
        SpriteGeometry.Mesh treads = closed && Own.textures(build.playerIndex) && Stairs.timber(texture.getName()) ? treads(texture) : null;
        float made = build.frame.material;
        int added = 0;
        for (int step = 0; step < Stairs.STEPS; step++) {
            // The tread runs from its riser at near back to far, the way the flight climbs.
            // A sprite stops short along the low edge of its square, where the game draws the
            // next square's sprite over it, and its lowest step is cut ragged there. That step
            // shows what the sprite has for the one above it: the steps of a flight are alike.
            int shown = Math.max(step, 1);
            float near = 1f - shown * Stairs.GOING, far = near - Stairs.GOING, high = Stairs.tread(third, shown);
            float back = (shown - step) * Stairs.GOING;
            build.geometry.shownA = west ? back : 0f;
            build.geometry.shownB = west ? 0f : back;
            build.geometry.shownUp = (step - shown) * Stairs.RISE;
            if (treads != null) {
                build.frame.material = WorldScene.boardsOf(Stairs.STEPS, west);
            }
            SpriteGeometry.Mesh tread = treads != null ? treads : mesh;
            added += west ? build.geometry.addFace(tread, build.frame, x, y, z, far, 0f, high, Stairs.GOING, 0f, 0f, 0f, 1f, 0f)
                    : build.geometry.addFace(tread, build.frame, x, y, z, 0f, far, high, 0f, Stairs.GOING, 0f, 1f, 0f, 0f);
            build.frame.material = made;
            added += west ? build.geometry.addFace(mesh, build.frame, x, y, z, near, 0f, high - riser, 0f, 1f, 0f, 0f, 0f, riser)
                    : build.geometry.addFace(mesh, build.frame, x, y, z, 0f, near, high - riser, 1f, 0f, 0f, 0f, 0f, riser);
        }
        build.geometry.shownA = build.geometry.shownB = build.geometry.shownUp = 0f;
        if (added == 0) {
            return true;
        }
        if (!closed && !Own.buildings()) {
            return true;
        }
        if (!closed) {
            // What carries the treads is in the mean of what the sprite paints. The build
            // that first meets the sprite has no colour for it yet and is made again.
            float[] colour = SpriteColours.of(texture);
            if (colour == null) {
                if (SpriteColours.unread(texture)) {
                    build.building.waitFor(texture);
                }
                return true;
            }
            SpriteGeometry.Mesh plain = build.solid.get(WorldScene.PLAIN, WallSides.key(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2], WallSides.FLAT));
            plain.open = build.frame.open;
            plain.sway = 0f;
            build.flightOn(plain, x, y, z, west, third);
            // Whether another flight is joined to a side is read from a square that may be another chunk's.
            build.building.plain = false;
            boolean joinedLow = build.flightThirdAt(west ? x : x - 1, west ? y - 1 : y, z, west) == third;
            boolean joinedHigh = build.flightThirdAt(west ? x : x + 1, west ? y + 1 : y, z, west) == third;
            // The posts are the mod's own. Under a fire escape the map stands a post of its own, which is left out where these are built: see underAFlightsHead.
            build.flights.open(third, !build.hangsInAir, joinedLow, joinedHigh);
            return true;
        }
        mesh.open = build.frame.open + 32f * build.frame.material;
        mesh.sway = 0f;
        float u = SpriteGeometry.textureU(build.frame, sideA, sideB), v = SpriteGeometry.textureV(build.frame, sideA, sideB, band);
        float[] c = build.reveal;
        for (int side = 0; side <= 1; side++) {
            for (int step = 0; step < Stairs.STEPS; step++) {
                float near = 1f - step * Stairs.GOING, far = near - Stairs.GOING, high = Stairs.tread(third, step);
                float under = Stairs.underside(third + step * Stairs.GOING), underFar = Stairs.underside(third + (step + 1) * Stairs.GOING);
                stairCorner(c, 0, x, y, z, west, near, side, high);
                stairCorner(c, 1, x, y, z, west, far, side, high);
                stairCorner(c, 2, x, y, z, west, far, side, underFar);
                stairCorner(c, 3, x, y, z, west, near, side, under);
                mesh.lightFlat(build.frame);
                SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
            }
        }
        // The underside lies on the floor as far as the second riser of the flight.
        float from = Math.max(third, Stairs.GOING), foot = 1f - (from - third);
        stairCorner(c, 0, x, y, z, west, foot, 0f, Stairs.underside(from));
        stairCorner(c, 1, x, y, z, west, 0f, 0f, Stairs.underside(third + 1f));
        stairCorner(c, 2, x, y, z, west, 0f, 1f, Stairs.underside(third + 1f));
        stairCorner(c, 3, x, y, z, west, foot, 1f, Stairs.underside(from));
        mesh.lightFlat(build.frame);
        SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        if (third == 2) {
            stairCorner(c, 0, x, y, z, west, 0f, 0f, 1f);
            stairCorner(c, 1, x, y, z, west, 0f, 1f, 1f);
            stairCorner(c, 2, x, y, z, west, 0f, 1f, Stairs.underside(3f));
            stairCorner(c, 3, x, y, z, west, 0f, 0f, Stairs.underside(3f));
            mesh.lightFlat(build.frame);
            SpriteGeometry.addQuad(mesh, c, u, v, build.frame.red, build.frame.green, build.frame.blue);
        }
        return true;
    }

    /**
     * The mesh a timber flight's treads are laid on, plain in the mean colour of what the sprite
     * paints, or null for a sprite with no colour: the treads are then the sprite's own. One
     * still to be read leaves the build unsigned, and it is made again once the colour is in.
     */
    private SpriteGeometry.Mesh treads(Texture texture) {
        float[] colour = SpriteColours.of(texture);
        if (colour == null) {
            if (SpriteColours.unread(texture)) {
                build.building.waitFor(texture);
            }
            return null;
        }
        SpriteGeometry.Mesh treads = build.solid.get(WorldScene.PLAIN, WallSides.laid(colour[WallSides.MEAN], colour[WallSides.MEAN + 1], colour[WallSides.MEAN + 2], 0f, 0f, 0f, WallSides.FINE));
        treads.sway = 0f;
        return treads;
    }

    /** A corner of a flight's own faces: along is how far down the square from its high edge, across how far over it, high in levels above its floor. */
    private void stairCorner(float[] c, int corner, int x, int y, int z, boolean west, float along, float across, float high) {
        float a = west ? along : across, b = west ? across : along;
        WorldScene.set(c, corner, x + a, y + b, (z + high) * ViewCamera.LEVEL_HEIGHT + build.geometry.liftAt(a, b));
    }
}
