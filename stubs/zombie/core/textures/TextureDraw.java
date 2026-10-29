package zombie.core.textures;

public final class TextureDraw {
    public GenericDrawer drawer;

    public abstract static class GenericDrawer {
        public abstract void render();
    }
}
