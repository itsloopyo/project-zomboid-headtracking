package zombie.core.skinnedmodel;

import zombie.core.textures.Texture;
import zombie.iso.objects.IsoDeadBody;

public final class DeadBodyAtlas {
    public static final DeadBodyAtlas instance = null;

    public BodyTexture getBodyTexture(IsoDeadBody body) { throw new UnsupportedOperationException("reference stub"); }
    public void render() { throw new UnsupportedOperationException("reference stub"); }

    public static final class BodyParams {
        public final java.util.HashMap<String, String> variables = null;
    }

    public static final class BodyTexture {
        public Texture getTexture() { throw new UnsupportedOperationException("reference stub"); }
        public float getRenderX(float x) { throw new UnsupportedOperationException("reference stub"); }
        public float getRenderY(float y) { throw new UnsupportedOperationException("reference stub"); }
        public float getRenderWidth() { throw new UnsupportedOperationException("reference stub"); }
        public float getRenderHeight() { throw new UnsupportedOperationException("reference stub"); }
    }
}
