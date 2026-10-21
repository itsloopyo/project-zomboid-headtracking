package com.cameraunlock.zomboid;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import zombie.core.skinnedmodel.ModelManager;
import zombie.core.skinnedmodel.model.Model;

/**
 * What of the mod's skin for a body is see-through. The game's body models have two sheets in
 * them that hang from the hips to the shins, front and back, for the game to paint a skirt or a
 * dress on, and a skin leaves them see-through: a solid one shows them as a flap of flesh
 * between the legs. The mod's skins are JPEGs, with no see-through of their own, so where the
 * sheets lie on the picture is worked out from the game's own models as loaded, while a save
 * loads, and nothing of the models is kept in the jar.
 */
final class SkinCuts {
    private SkinCuts() {}

    static final int MALE = 0, FEMALE = 1, NO_BODY = -1;
    /** The size every skin of a body in the jar is. */
    static final int SIZE = 1024;

    /** Both bodies' cuts, MALE then FEMALE: null until the game's models have been read. */
    private static volatile CompletableFuture<byte[][]> cuts;

    /**
     * Which body the jar's skin at this place is for, or NO_BODY for one that is no person's.
     * The jar names each after the game's texture, in small letters.
     */
    static int bodyOf(String resource) {
        String name = resource.substring(resource.lastIndexOf('/') + 1);
        if (name.startsWith("m_zedbody") || name.startsWith("malebody")) {
            return MALE;
        }
        if (name.startsWith("f_zedbody") || name.startsWith("femalebody")) {
            return FEMALE;
        }
        return NO_BODY;
    }

    /** Whether the game has loaded both body models' meshes. */
    static boolean bodiesLoaded() {
        ModelManager models = ModelManager.instance;
        return models != null && loaded(models.maleModel) && loaded(models.femaleModel);
    }

    private static boolean loaded(Model model) {
        return model != null && model.mesh != null && model.mesh.vb != null;
    }

    /**
     * Reads the game's two body models and sets the cuts making on a thread of their own, the
     * first time it is called: render thread. False, with nothing done, while the game has not
     * loaded both.
     */
    static boolean begin() {
        if (cuts != null) {
            return true;
        }
        if (!bodiesLoaded()) {
            return false;
        }
        ModelManager models = ModelManager.instance;
        CharacterDraw.Shape male = shape(models.maleModel), female = shape(models.femaleModel);
        if (male == null || female == null) {
            return false;
        }
        boolean[] maleSkirt = skirt(male, models.maleModel.name), femaleSkirt = skirt(female, models.femaleModel.name);
        cuts = CompletableFuture.supplyAsync(() -> {
            long start = System.nanoTime();
            byte[][] made = {cut(male.uvs(), male.corners(), maleSkirt, SIZE, SIZE), cut(female.uvs(), female.corners(), femaleSkirt, SIZE, SIZE)};
            Log.info(String.format(Locale.ROOT, "the see-through of the bodies' skins is made from the game's models: %.0f ms", (System.nanoTime() - start) / 1e6));
            return made;
        }, work -> {
            Thread thread = new Thread(work, "CameraUnlock skin cuts");
            thread.setDaemon(true);
            thread.start();
        });
        return true;
    }

    /**
     * The cut of this body's skin, a byte a dot, 255 where the skin shows and 0 where it does
     * not, for a skin of this size. Waits for it to be made. Only once begin has said true.
     */
    static byte[] of(int body, int wide, int high) throws IOException {
        if (wide != SIZE || high != SIZE) {
            throw new IOException("a body's skin is " + wide + " by " + high + " and its see-through is made " + SIZE + " by " + SIZE);
        }
        try {
            return cuts.join()[body];
        } catch (CompletionException failure) {
            throw new IOException("the see-through of the bodies' skins could not be made", failure.getCause());
        }
    }

    private static CharacterDraw.Shape shape(Model model) {
        StringBuilder why = new StringBuilder();
        CharacterDraw.Shape shape = CharacterDraw.shapeOf(model, why);
        if (shape == null && !why.isEmpty()) {
            throw new IllegalStateException("the game's body model " + model.name + " cannot be read for where its skirt is: " + why);
        }
        if (shape != null && shape.uvs() == null) {
            throw new IllegalStateException("the game's body model " + model.name + " has no texture places");
        }
        return shape;
    }

    private static boolean[] skirt(CharacterDraw.Shape shape, String name) {
        boolean[] skirt = skirt(shape.places(), shape.corners());
        int count = 0;
        for (boolean each : skirt) {
            count += each ? 1 : 0;
        }
        int triangles = skirt.length;
        if (count == 0 || count * 2 >= triangles) {
            throw new IllegalStateException("the game's body model " + name + " has " + count + " of its " + triangles
                    + " triangles where its skirt was looked for: it is not laid out as the mod knows it");
        }
        Log.info("the game's body model " + name + " has its skirt in " + count + " of its " + triangles + " triangles");
        return skirt;
    }

    /**
     * Which of a body model's triangles are its skirt: the pieces of the model, joined by the
     * vertices their triangles share, with a vertex on the body's middle line below the fork
     * of the legs, where a body has nothing. Vertices are joined by number and never by place:
     * the sheets hang from points of the hips they share a place with.
     */
    static boolean[] skirt(float[] places, int[] corners) {
        int[] piece = new int[places.length / 3];
        for (int i = 0; i < piece.length; i++) {
            piece[i] = i;
        }
        for (int corner = 0; corner < corners.length; corner += 3) {
            int b = root(piece, corners[corner + 1]);
            piece[root(piece, corners[corner])] = b;
            piece[root(piece, corners[corner + 2])] = b;
        }
        boolean[] hung = new boolean[piece.length];
        for (int vertex = 0; vertex < piece.length; vertex++) {
            if (Math.abs(places[vertex * 3]) < 0.002f && places[vertex * 3 + 1] < 0.3f) {
                hung[root(piece, vertex)] = true;
            }
        }
        boolean[] skirt = new boolean[corners.length / 3];
        for (int triangle = 0; triangle < skirt.length; triangle++) {
            skirt[triangle] = hung[root(piece, corners[triangle * 3])];
        }
        return skirt;
    }

    private static int root(int[] piece, int i) {
        while (piece[i] != i) {
            piece[i] = piece[piece[i]];
            i = piece[i];
        }
        return i;
    }

    /**
     * The cut of a skin of this size: 0 on every dot the skirt's triangles cover, reaching a
     * dot's breadth past their edges so that no line of skin is left along them, and 255 on
     * the rest and on every dot inside one of the body's own triangles.
     */
    static byte[] cut(float[] uvs, int[] corners, boolean[] skirt, int wide, int high) {
        byte[] cut = new byte[wide * high];
        Arrays.fill(cut, (byte) 255);
        for (int pass = 0; pass < 2; pass++) {
            boolean ofSkirt = pass == 0;
            for (int triangle = 0; triangle < skirt.length; triangle++) {
                if (skirt[triangle] == ofSkirt) {
                    lay(cut, wide, high, uvs, corners, triangle, ofSkirt);
                }
            }
        }
        return cut;
    }

    /** Lays one triangle on the cut: a skirt's as 0 with the slack round it, a body's as 255 strictly inside it. */
    private static void lay(byte[] cut, int wide, int high, float[] uvs, int[] corners, int triangle, boolean ofSkirt) {
        int a = corners[triangle * 3], b = corners[triangle * 3 + 1], c = corners[triangle * 3 + 2];
        double x0 = uvs[a * 2] * (double) wide, y0 = uvs[a * 2 + 1] * (double) high;
        double x1 = uvs[b * 2] * (double) wide, y1 = uvs[b * 2 + 1] * (double) high;
        double x2 = uvs[c * 2] * (double) wide, y2 = uvs[c * 2 + 1] * (double) high;
        int left = (int) Math.max(0, Math.floor(Math.min(x0, Math.min(x1, x2)) - 1));
        int right = (int) Math.min(wide - 1, Math.ceil(Math.max(x0, Math.max(x1, x2)) + 1));
        int top = (int) Math.max(0, Math.floor(Math.min(y0, Math.min(y1, y2)) - 1));
        int bottom = (int) Math.min(high - 1, Math.ceil(Math.max(y0, Math.max(y1, y2)) + 1));
        double area = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2);
        if (Math.abs(area) < 1e-9 || right < left || bottom < top) {
            return;
        }
        double slack = ofSkirt ? 1.0 / Math.max(1.0, Math.sqrt(Math.abs(area))) : 0.0;
        byte value = ofSkirt ? 0 : (byte) 255;
        for (int y = top; y <= bottom; y++) {
            double py = y + 0.5;
            for (int x = left; x <= right; x++) {
                double px = x + 0.5;
                double w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / area;
                double w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / area;
                double w2 = 1.0 - w0 - w1;
                if (w0 >= -slack && w1 >= -slack && w2 >= -slack) {
                    cut[y * wide + x] = value;
                }
            }
        }
    }
}
