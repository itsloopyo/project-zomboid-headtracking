package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import zombie.GameTime;
import zombie.core.Core;
import zombie.core.textures.Texture;
import zombie.core.utils.BoundedQueue;
import zombie.iso.IsoChunk;
import zombie.iso.IsoDirections;
import zombie.iso.IsoFloorBloodSplat;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoObject;
import zombie.iso.IsoWallBloodSplat;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * The blood of a chunk being built, on its floors and its walls. One to a frame of the world's
 * picture, and it writes into that frame's meshes.
 *
 * The game keeps the blood on a floor in a list on each chunk and draws each splat as one
 * picture laid over the floor round where it was spilt, so a splat by a chunk's edge lies on
 * the next chunk's squares too. A level is read once for the chunk being built, from its own
 * chunk and the eight round it: every splat whose picture reaches the chunk's squares, each
 * filed under every square it reaches. Each square lays the part of each splat that is on it,
 * as the floor's own layers are laid, so a splat lies on a kerb, a slope and a floor of boards
 * as they lie and is cut where its square has no floor.
 *
 * The blood on a wall is a picture the game keeps on the wall itself and draws over it, as it
 * draws a stain.
 */
final class BloodBuilder {
    private final WorldScene.Frame build;

    /** The splats of the level read, each its picture laid on the floor (Blood.laid, on the map), and what it is drawn with. */
    private float[] corners = new float[64 * 4 * Blood.CORNER];
    private float[] reds = new float[64], greenBlues = new float[64], covers = new float[64];
    private Texture[] pictures = new Texture[64];
    /** The squares of the chunk each splat's picture reaches the bounds of: from x, to x, from y and to y, from the chunk's corner. */
    private int[] reach = new int[64 * 4];
    private int splats;
    /** The splats on each square of the chunk, as places in the arrays above: square s has those from bySquare[firstOf[s]] to bySquare[firstOf[s + 1]]. */
    private final int[] firstOf = new int[WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES + 1];
    private int[] bySquare = new int[64];
    private final int[] filled = new int[WorldScene.CHUNK_TILES * WorldScene.CHUNK_TILES];
    private final float[] laid = new float[4 * Blood.CORNER];
    private final float[] piece = new float[Blood.MOST_CORNERS * Blood.CORNER], spare = new float[Blood.MOST_CORNERS * Blood.CORNER];
    private final float[] triangle = new float[3 * Blood.CORNER];
    private final float[] tint = new float[2];
    private final float[] cover = new float[4];

    BloodBuilder(WorldScene.Frame build) {
        this.build = build;
    }

    /**
     * Reads the splats on level z that reach the chunk whose north west square is at (chunkX,
     * chunkY) from the chunks round it as the build has them (WorldScene.Frame.around), and
     * says what they are as a sum, for the chunk's signature. As the game's view: none at all
     * where the player has blood turned off, and of the rest only those its setting for how
     * much blood to draw keeps.
     */
    long read(int chunkX, int chunkY, int z) {
        splats = 0;
        Arrays.fill(firstOf, 0);
        int setting = Core.getInstance().getOptionBloodDecals();
        long sum = WorldScene.sum(WorldScene.SUM_FROM, setting);
        if (setting == 0) {
            return sum;
        }
        double now = GameTime.getInstance().getWorldAgeHours();
        // The pieces laid here dry on this chunk's minute, wherever their splats are kept: a splat
        // over the edge dries a minute apart on either side, and the chunk steps once an hour, not
        // once for each of the nine chunks whose splats reach it.
        double phase = Blood.phase(Math.floorDiv(chunkX, WorldScene.CHUNK_TILES), Math.floorDiv(chunkY, WorldScene.CHUNK_TILES));
        String[] kinds = IsoFloorBloodSplat.FLOOR_BLOOD_TYPES;
        if (picturesOfKinds.length != kinds.length) {
            picturesOfKinds = new Texture[kinds.length];
        } else {
            Arrays.fill(picturesOfKinds, null);
        }
        byte[] kept = IsoChunk.renderByIndex[setting - 1];
        for (int c = 0; c < build.around.length; c++) {
            IsoChunk chunk = build.around[c];
            if (chunk == null) {
                continue;
            }
            BoundedQueue<IsoFloorBloodSplat> list = chunk.floorBloodSplats;
            float originX = chunk.wx * WorldScene.CHUNK_TILES, originY = chunk.wy * WorldScene.CHUNK_TILES;
            for (int i = 0; i < list.size(); i++) {
                IsoFloorBloodSplat splat = list.get(i);
                if (splat.index >= 1 && splat.index <= kept.length && kept[splat.index - 1] == 0
                        || (int) Math.floor(splat.z) != z || splat.type < 0 || splat.type >= kinds.length) {
                    continue;
                }
                Texture picture = picturesOfKinds[splat.type];
                if (picture == null) {
                    picture = picture(kinds[splat.type]);
                    picturesOfKinds[splat.type] = picture;
                }
                if (!WorldScene.usable(picture)) {
                    continue;
                }
                Blood.laid(splat.x, splat.y, splat.z, Core.tileScale, picture.getWidth(), picture.getHeight(), picture.getOffsetX(), picture.getOffsetY(),
                        picture.getXStart(), picture.getYStart(), picture.getXEnd(), picture.getYEnd(), laid);
                float west = Float.MAX_VALUE, east = -Float.MAX_VALUE, north = Float.MAX_VALUE, south = -Float.MAX_VALUE;
                for (int k = 0; k < 4; k++) {
                    laid[k * Blood.CORNER] += originX;
                    laid[k * Blood.CORNER + 1] += originY;
                    west = Math.min(west, laid[k * Blood.CORNER]);
                    east = Math.max(east, laid[k * Blood.CORNER]);
                    north = Math.min(north, laid[k * Blood.CORNER + 1]);
                    south = Math.max(south, laid[k * Blood.CORNER + 1]);
                }
                int fromX = Math.max((int) Math.floor(west) - chunkX, 0), toX = Math.min((int) Math.floor(east) - chunkX, WorldScene.CHUNK_TILES - 1);
                int fromY = Math.max((int) Math.floor(north) - chunkY, 0), toY = Math.min((int) Math.floor(south) - chunkY, WorldScene.CHUNK_TILES - 1);
                if (fromX > toX || fromY > toY) {
                    continue;
                }
                float fresh = Blood.freshness(now, splat.worldAge, phase);
                Blood.floorTint(splat.x, splat.y, splat.type, fresh, tint);
                int at = add();
                System.arraycopy(laid, 0, corners, at * 4 * Blood.CORNER, 4 * Blood.CORNER);
                reach[at * 4] = fromX;
                reach[at * 4 + 1] = toX;
                reach[at * 4 + 2] = fromY;
                reach[at * 4 + 3] = toY;
                reds[at] = tint[0];
                greenBlues[at] = tint[1];
                covers[at] = Blood.floorCover(fresh);
                pictures[at] = picture;
                for (int x = fromX; x <= toX; x++) {
                    for (int y = fromY; y <= toY; y++) {
                        firstOf[x * WorldScene.CHUNK_TILES + y + 1]++;
                    }
                }
                sum = WorldScene.sum(WorldScene.sum(WorldScene.sum(WorldScene.sum(sum, Float.floatToRawIntBits(laid[0])), Float.floatToRawIntBits(laid[1])),
                        picture.getID()), Float.floatToRawIntBits(covers[at]));
            }
        }
        file();
        return sum;
    }

    /** One more splat in the arrays, made longer as they need to be: its place. */
    private int add() {
        if (splats == reds.length) {
            int more = splats * 2;
            corners = Arrays.copyOf(corners, more * 4 * Blood.CORNER);
            reds = Arrays.copyOf(reds, more);
            greenBlues = Arrays.copyOf(greenBlues, more);
            covers = Arrays.copyOf(covers, more);
            pictures = Arrays.copyOf(pictures, more);
            reach = Arrays.copyOf(reach, more * 4);
        }
        return splats++;
    }

    /** Files each splat read under every square of the chunk its picture's bounds reach. */
    private void file() {
        int squares = firstOf.length - 1;
        for (int s = 0; s < squares; s++) {
            firstOf[s + 1] += firstOf[s];
        }
        if (bySquare.length < firstOf[squares]) {
            bySquare = new int[Math.max(firstOf[squares], bySquare.length * 2)];
        }
        Arrays.fill(filled, 0);
        for (int at = 0; at < splats; at++) {
            for (int x = reach[at * 4]; x <= reach[at * 4 + 1]; x++) {
                for (int y = reach[at * 4 + 2]; y <= reach[at * 4 + 3]; y++) {
                    int s = x * WorldScene.CHUNK_TILES + y;
                    bySquare[firstOf[s] + filled[s]++] = at;
                }
            }
        }
    }

    /**
     * The picture of each kind of splat, by its place in IsoFloorBloodSplat.FLOOR_BLOOD_TYPES, as found once in a read:
     * from a build thread each is asked of the logic thread, and asked once a splat they were asked about
     * twenty thousand times a second where the streets are bloody.
     */
    private Texture[] picturesOfKinds = new Texture[0];

    /** The picture of a kind of splat, as the game's view takes it: from the sprites it keeps for them, made as it makes them the first time one is asked for. */
    private static Texture picture(String kind) {
        return BuildThreads.onLogicThread(() -> pictureHere(kind));
    }

    /** The same, on the logic thread: the game keeps the sprites in one map, which only that thread changes. */
    private static Texture pictureHere(String kind) {
        IsoSprite sprite = IsoFloorBloodSplat.spriteMap.get(kind);
        if (sprite == null) {
            sprite = IsoSprite.CreateSprite(IsoSpriteManager.instance);
            sprite.LoadSingleTexture(kind);
            IsoFloorBloodSplat.spriteMap.put(kind, sprite);
        }
        return sprite.getTextureForCurrentFrame(IsoDirections.N);
    }

    /**
     * Lays the part of every splat read that is on the square (x, y, z) over the square's
     * floor, a layer over the given number lying under it, and says how many layers that takes:
     * one where any blood is on the square. The splats share it: the picture draws blood
     * leaving its depth alone, each splat mixed over the last (Pipeline.drawBlood). The frame
     * is the square's, lit, and is given blood's material for the while.
     */
    int layFloor(int x, int y, int z, int localX, int localY, int under) {
        int square = localX * WorldScene.CHUNK_TILES + localY;
        int laidCount = 0;
        float material = build.frame.material;
        build.frame.material = WorldScene.BLOOD;
        for (int k = firstOf[square]; k < firstOf[square + 1]; k++) {
            int at = bySquare[k];
            System.arraycopy(corners, at * 4 * Blood.CORNER, laid, 0, 4 * Blood.CORNER);
            int count = Blood.cut(laid, x, y, piece, spare);
            if (count < 3) {
                continue;
            }
            SpriteGeometry.Mesh mesh = build.laid.get(pictures[at].getID(), WallSides.BLOOD);
            Arrays.fill(cover, covers[at]);
            build.geometry.cover = cover;
            mesh.rootX = reds[at];
            mesh.rootY = greenBlues[at];
            build.layers.lie(SpriteGeometry.LAYERS_UP, under + 1);
            for (int corner = 1; corner < count - 1; corner++) {
                System.arraycopy(piece, 0, triangle, 0, Blood.CORNER);
                System.arraycopy(piece, corner * Blood.CORNER, triangle, Blood.CORNER, 2 * Blood.CORNER);
                build.geometry.addGiven(mesh, build.frame, x, y, z, triangle, 0);
            }
            build.layers.none();
            mesh.rootX = mesh.rootY = SpriteGeometry.NO_ROOT;
            build.geometry.cover = null;
            laidCount++;
        }
        build.frame.material = material;
        return Math.min(laidCount, 1);
    }

    /**
     * Lays the blood the game keeps on the object of the square (x, y, z) over the wall it is on,
     * on the side of the wall the game's view shows: a picture made for a north wall on the
     * square's north edge, one for a west wall on its west. As the game's view, one made for the
     * other wall than the object stands along is not drawn.
     */
    void layOnWall(IsoObject object, boolean onNorth, boolean onWest, int x, int y, int z) {
        if (Core.getInstance().getOptionBloodDecals() == 0) {
            return;
        }
        ArrayList<IsoWallBloodSplat> splatsOnWall = object.wallBloodSplats;
        double now = GameTime.getInstance().getWorldAgeHours();
        double phase = Blood.phase(Math.floorDiv(x, WorldScene.CHUNK_TILES), Math.floorDiv(y, WorldScene.CHUNK_TILES));
        float material = build.frame.material;
        build.frame.material = WorldScene.BLOOD;
        for (int i = 0; i < splatsOnWall.size(); i++) {
            IsoWallBloodSplat splat = splatsOnWall.get(i);
            IsoSprite sprite = splat.sprite;
            if (sprite == null) {
                continue;
            }
            boolean north = splat.isNorthSprite();
            if (north ? !onNorth && onWest : onNorth && !onWest) {
                continue;
            }
            Texture texture = sprite.getTextureForCurrentFrame(object.getDir());
            if (!WorldScene.usable(texture)) {
                continue;
            }
            build.frameOf(object, texture);
            float fresh = Blood.freshness(now, splat.worldAge, phase);
            float dark = Blood.darkness(fresh);
            SpriteGeometry.Mesh mesh = build.laid.get(texture.getID(), WallSides.BLOOD);
            Arrays.fill(cover, Blood.wallCover(fresh));
            build.geometry.cover = cover;
            mesh.rootX = Blood.WALL_RED * dark;
            mesh.rootY = Blood.WALL_GREEN_BLUE * dark;
            // At least a layer off the skin, which is at the edge's first layer whether or not the wall took one of the square's.
            build.wallBuilder.addStain(mesh, x, y, z, 1 + (north ? build.northLayer++ : build.westLayer++), north ? 1f : 0f, north ? 0f : 1f);
            mesh.rootX = mesh.rootY = SpriteGeometry.NO_ROOT;
            build.geometry.cover = null;
        }
        build.frame.material = material;
    }

    /** What the blood on a wall is, as a sum for the chunk's signature: each picture and how dry it is. */
    static long onWall(long sum, IsoObject object) {
        ArrayList<IsoWallBloodSplat> splatsOnWall = object.wallBloodSplats;
        if (splatsOnWall == null || splatsOnWall.isEmpty()) {
            return sum;
        }
        double now = GameTime.getInstance().getWorldAgeHours();
        IsoGridSquare square = object.getSquare();
        double phase = Blood.phase(Math.floorDiv(square.getX(), WorldScene.CHUNK_TILES), Math.floorDiv(square.getY(), WorldScene.CHUNK_TILES));
        sum = WorldScene.sum(sum, Core.getInstance().getOptionBloodDecals());
        for (int i = 0; i < splatsOnWall.size(); i++) {
            IsoWallBloodSplat splat = splatsOnWall.get(i);
            sum = WorldScene.sum(WorldScene.sum(sum, System.identityHashCode(splat.sprite)), Float.floatToRawIntBits(Blood.freshness(now, splat.worldAge, phase)));
        }
        return sum;
    }
}
