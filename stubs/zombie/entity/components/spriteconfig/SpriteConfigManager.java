package zombie.entity.components.spriteconfig;

public class SpriteConfigManager {
    public static class ObjectInfo {
        public zombie.scripting.entity.components.spriteconfig.SpriteConfigScript getScript() { throw new UnsupportedOperationException("reference stub"); }
    }

    public static class FaceInfo {
        public int getWidth() { throw new UnsupportedOperationException("reference stub"); }
        public int getHeight() { throw new UnsupportedOperationException("reference stub"); }
        public int getzLayers() { throw new UnsupportedOperationException("reference stub"); }
        public TileInfo getTileInfo(int x, int y, int z) { throw new UnsupportedOperationException("reference stub"); }
    }

    public static class TileInfo {
        public String getSpriteName() { throw new UnsupportedOperationException("reference stub"); }
    }
}
