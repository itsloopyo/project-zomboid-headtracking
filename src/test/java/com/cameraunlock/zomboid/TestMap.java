package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import zombie.core.properties.PropertyContainer;
import zombie.core.textures.Texture;
import zombie.iso.IsoCell;
import zombie.iso.SpriteDetails.IsoObjectType;
import zombie.iso.sprite.IsoSprite;

/**
 * A piece of the map a test puts together, and a frame of the mod's picture built over it, so
 * a test reaches the builders' own code where it reads the map: see zombie.iso.IsoCell. Roof
 * tiles are put straight into what Roofs holds of the roofs it has read, as fitted, since the
 * stand-in sprites have no pictures for Roofs to read them from.
 */
final class TestMap {
    final IsoCell cell = new IsoCell();
    final WorldScene.Frame build;
    /** A texture with something painted on it, as a wall's sprite has. */
    final Texture painted = new Texture(7, 4, 4);

    TestMap() {
        WorldScene world = new WorldScene(null);
        build = world.next();
        build.cell = cell;
        build.building = new WorldScene.ChunkMeshes(0);
        build.placed = build.building.placed;
        build.frame.scale = 2f;
        // A test that calls a builder without the build lighting the frame from the map builds in the frame's light as it is, on a site as a builder's light is.
        LightSiteTests.sited(build.frame);
        // Each site a builder puts is worked out again by the frame, as a build has it (Frame.addSquares).
        WorldScene.sites.evaluator = build;
        build.roofBuilder.newBuild();
        Roofs.forget();
        WorldScene.solid.get(WorldScene.PLAIN).clear();
        painted.paint(0, 0, 4, 4);
    }

    /** A sprite of that name and type, with those properties: flags are named with no value. */
    IsoSprite sprite(String name, IsoObjectType type, String... flags) {
        Map<String, String> properties = new HashMap<>();
        for (String flag : flags) {
            properties.put(flag, "");
        }
        return new IsoSprite(name, new PropertyContainer(properties), type, painted);
    }

    /** A sprite of that name and type, with those properties, given as names and values in turn. */
    IsoSprite spriteWith(String name, IsoObjectType type, Texture texture, String... namesAndValues) {
        Map<String, String> properties = new HashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            properties.put(namesAndValues[i], namesAndValues[i + 1]);
        }
        return new IsoSprite(name, new PropertyContainer(properties), type, texture);
    }

    /** A wall of the map on the north or the west edge of the square, with a picture. */
    void wall(int x, int y, int z, boolean north) {
        cell.square(x, y, z).add(sprite(north ? "walls_exterior_house_01_1" : "walls_exterior_house_01_0", IsoObjectType.wall, north ? "WallN" : "WallW"));
    }

    /** A roof's sprite on the square, as the map has its tiles: a roof tile's type. */
    void roofSprite(int x, int y, int z, String name, String... flags) {
        cell.square(x, y, z).add(sprite(name, IsoObjectType.WestRoofB, flags));
    }

    /** The tile of a roof as the fit left it, held as Roofs holds one it has read, with the texture of its sprite, which is put on its square. */
    void roofTile(RoofFit.Tile tile, Texture texture) {
        roofSprite(tile.x, tile.y, tile.z, "roofs_02_0");
        Roofs.Roof roof = new Roofs.Roof();
        write(roof, "kept", Integer.MAX_VALUE / 2);
        Map<Long, Roofs.Laid> laid = read(null, Roofs.class, "laid");
        laid.put(RoofFit.key(tile.x, tile.y, tile.z), new Roofs.Laid(tile, texture, roof));
    }

    /** Runs a test with every row of [Content] on, and puts back the rows as they were. */
    static void withEverything(Checks.Test test) throws Throwable {
        int rows = Own.rows();
        Own.set(Own.ALL);
        try {
            test.run();
        } finally {
            Own.set(rows);
        }
    }

    /** What the frame's build wrote into the mesh of things drawn without a texture. */
    static SpriteGeometry.Mesh plain() {
        return WorldScene.solid.get(WorldScene.PLAIN);
    }

    @SuppressWarnings("unchecked")
    static <T> T read(Object owner, String name) {
        return read(owner, owner.getClass(), name);
    }

    @SuppressWarnings("unchecked")
    static <T> T read(Object owner, Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return (T) field.get(owner);
        } catch (ReflectiveOperationException unreadable) {
            throw new IllegalStateException("the test reads " + type.getSimpleName() + "." + name, unreadable);
        }
    }

    static void write(Object owner, String name, Object value) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(owner, value);
        } catch (ReflectiveOperationException unwritable) {
            throw new IllegalStateException("the test writes " + owner.getClass().getSimpleName() + "." + name, unwritable);
        }
    }
}
