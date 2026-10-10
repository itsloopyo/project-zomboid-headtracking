package zombie.core.textures;

/**
 * The tests' stand-in for the game's texture: an id, a size and which of its pixels are
 * painted, as the game's mask holds them. See zombie.core.properties.PropertyContainer.
 */
public class Texture {
    private final int id, width, height;
    private final boolean[] mask;

    /** A texture of that size with nothing painted on it. */
    public Texture(int id, int width, int height) {
        this.id = id;
        this.width = width;
        this.height = height;
        mask = new boolean[width * height];
    }

    /** Paints the pixels from (x0, y0) up to but not including (x1, y1). */
    public void paint(int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                mask[y * width + x] = true;
            }
        }
    }

    public int getID() {
        return id;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getWidthOrig() {
        return width;
    }

    public int getHeightOrig() {
        return height;
    }

    public float getOffsetX() {
        return 0f;
    }

    public float getOffsetY() {
        return 0f;
    }

    public float getXStart() {
        return 0f;
    }

    public float getXEnd() {
        return 1f;
    }

    public float getYStart() {
        return 0f;
    }

    public float getYEnd() {
        return 1f;
    }

    public boolean isMaskSet(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height && mask[y * width + x];
    }

    public String getName() {
        return "texture " + id;
    }
}
