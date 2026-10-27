package zombie.core;

import zombie.core.textures.TextureDraw;

public final class SpriteRenderer {
    public static final SpriteRenderer instance = make();

    private static SpriteRenderer make() {
        throw new UnsupportedOperationException("reference stub");
    }

    public TextureDraw drawGeneric(TextureDraw.GenericDrawer drawer) {
        throw new UnsupportedOperationException("reference stub");
    }
    public int getRenderingPlayerIndex() { throw new UnsupportedOperationException("reference stub"); }
    public zombie.core.sprite.SpriteRenderState getRenderingState() { throw new UnsupportedOperationException("reference stub"); }
    public int getRenderStateIndex() { throw new UnsupportedOperationException("reference stub"); }
}
