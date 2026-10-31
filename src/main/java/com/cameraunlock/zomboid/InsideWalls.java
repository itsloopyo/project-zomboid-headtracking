package com.cameraunlock.zomboid;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import zombie.core.properties.PropertyContainer;
import zombie.iso.sprite.IsoSprite;
import zombie.iso.sprite.IsoSpriteManager;

/**
 * What a piece of a house's inside wall is made of where the game names no material for it. The
 * game names Plaster for every plain length of inside wall, the pieces it paints as a wall
 * (PaintingType wall), and nothing for the pieces of the same walls round a doorway or a window,
 * for their corner posts, or for any piece of its sheets of inside window frames, which hold no
 * plain length at all (walls_interior_house_06 to 27). Those are the same plasterboard. So a wall
 * piece with no material, on a sheet any of whose wall pieces the game paints (PaintingType), is
 * made of what every wall piece it paints as a wall is made of. A sheet none of whose pieces is
 * painted keeps no material for them, as the tiles over block of walls_interior_bathroom_01 do,
 * and they stop a round. Any thread.
 */
final class InsideWalls {
    /** The sheets with a wall piece the game paints, by the sprite name up to its place. */
    private final Set<String> painted;
    /** What every wall piece the game paints as a wall is made of: null where they name more than one, or none. */
    final String material;

    private InsideWalls(Set<String> painted, String material) {
        this.painted = painted;
        this.material = material;
    }

    /** The paint and materials of every sprite the game has read. */
    static InsideWalls read(Iterable<IsoSprite> sprites) {
        Set<String> painted = new HashSet<>(), named = new HashSet<>();
        for (IsoSprite sprite : sprites) {
            PropertyContainer properties = sprite.getProperties();
            String paint = properties.get("PaintingType");
            if (paint == null || sprite.getName() == null || !WorldScene.isWall(properties, sprite.getType())) {
                continue;
            }
            painted.add(sheetOf(sprite.getName()));
            String made = properties.get("MaterialType");
            if (paint.equals("wall") && made != null) {
                named.add(made);
            }
        }
        return new InsideWalls(painted, named.size() == 1 ? named.iterator().next() : null);
    }

    /** What a sprite is made of: its MaterialType, or for a wall piece with none on a painted sheet, what the painted walls are made of. */
    String of(IsoSprite sprite) {
        PropertyContainer properties = sprite.getProperties();
        String made = properties.get("MaterialType");
        if (made != null || sprite.getName() == null || !WorldScene.isWall(properties, sprite.getType())) {
            return made;
        }
        return painted.contains(sheetOf(sprite.getName())) ? material : null;
    }

    private static String sheetOf(String name) {
        int place = name.lastIndexOf('_');
        return place < 0 ? name : name.substring(0, place);
    }

    /** The game's sprites, read once the first time a shot asks what a wall is made of: they are all read as the game starts. */
    private static final class Game {
        static final InsideWalls READ = read(IsoSpriteManager.instance.namedMap.values());

        static {
            if (DevBuild.ON) {
                Log.info(READ.material == null
                        ? String.format(Locale.ROOT, "inside walls: the %d sheets of painted wall pieces name no one material for their plain walls, and their pieces with none stop a round", READ.painted.size())
                        : String.format(Locale.ROOT, "inside walls: the wall pieces with no material on the %d sheets of painted wall pieces are %s", READ.painted.size(), READ.material));
            }
        }
    }

    /** What a sprite is made of, as of() says, by the game's own sprites. */
    static String materialOf(IsoSprite sprite) {
        return Game.READ.of(sprite);
    }
}
