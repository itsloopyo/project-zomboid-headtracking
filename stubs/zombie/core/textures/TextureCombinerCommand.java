package zombie.core.textures;

import java.util.ArrayList;

public final class TextureCombinerCommand {
    public int x;
    public int y;
    public int w;
    public int h;
    public Texture mask;
    public Texture tex;
    public int blendSrc;
    public int blendDest;
    public int blendSrcA;
    public int blendDestA;
    public zombie.core.opengl.SmartShader shader;
    public ArrayList<TextureCombinerShaderParam> shaderParams;
}
