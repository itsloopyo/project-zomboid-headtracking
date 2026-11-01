package com.cameraunlock.zomboid;

/**
 * Sunlight through stained glass, in the colours of the glass (ColouredGlassLight). The sun's
 * maps hold depth alone, and the mod's own picture of a stained glass window (StainedGlass)
 * paints its glass at half cover, which they took for solid: no sun came through a church's
 * windows at all.
 *
 * With this on, the near sun map lets that glass through, and a picture beside it, half its size
 * each way, holds what share of each colour of the sun is left past the glass on its way to each
 * texel, and how far along the light the nearest of that glass is. The glass is drawn into it
 * with the sun map's depth as it stands, so glass behind whatever stops the sun first gives it no
 * colour, and its colours multiply where the sun crosses two panes. A place the near map has in
 * the sun and that lies past the glass is lit by the sun in that colour (sunThroughGlass), and
 * so is the room's air the sun crosses, as a coloured shaft.
 *
 * Only the mod's own pictures of the windows are drawn into it, and each of the chunks' draws of
 * that atlas is those windows alone, so the pass draws nothing, and its picture is not made,
 * where no such window is in the near map. The far map keeps the windows solid, as the picture
 * has no colour for it. Clear glass, a window's pane or a door's, gives the sun no colour and
 * is not drawn. Low draws the game's own sprites of the windows, whose glass dots the sun maps
 * already let through, uncoloured, as they always have.
 */
final class SunGlass {
    private SunGlass() {}

    /**
     * How much more than a half covered the atlas's texels the sun maps let through are, while the
     * windows are drawn into the picture: tools/glass/stained.py paints the glass at 128 of 255 and
     * the frame at 240, and the world's program takes what is covered less than 0.75 as glass.
     */
    static final float CUT = 0.25f;
    /**
     * As the world's program shows the window's own glass (PipelineShaders, STAINED_OWN_GAIN and
     * STAINED_OWN_THICK): what the glass lets through of its strongest colour is that colour's
     * share of white times GAIN, and of each other colour its share of the strongest to the power
     * THICK, so a pale streak is near white and a deep ruby passes red alone.
     */
    static final float GAIN = 1.6f, THICK = 2f;
    /** The atlas paints its leading near black: a texel darker than LEAD lets nothing through, and one lighter than twice that all it would. */
    static final float LEAD = 0.05f;
    /**
     * How much further along the light than the nearest glass, in the near map's depth from 0 to
     * 1 over twice its range, a place has to be to be lit through it. The picture holds that
     * depth in a half float, steps of 1/2048 between a half and 1.
     */
    static final float PAST = 0.001f;
    /** OpenGL's blend factor and equation the pass multiplies colours and keeps the nearest depth with. */
    static final int GL_DST_COLOR = 0x0306, GL_MIN = 0x8007;

    /** Whether the windows colour the sun in a frame: the row is on, the mode shades and lights (Medium, High), and the atlas is on the graphics card. */
    static boolean runs(boolean on, boolean shadedAndLit, int atlas) {
        return on && shadedAndLit && atlas != 0;
    }

    /** How many texels across the picture is, for sun maps of mapTexels across. */
    static int size(int mapTexels) {
        return mapTexels / 2;
    }

    /**
     * Draws the windows into the picture, over its clear of white at depth 1, with the colour
     * multiplied and the least depth kept. Its vertex shader is the sun maps' own (SHADOW_VERTEX).
     */
    static final String CAST_FRAGMENT = "#version 120\n"
            + "const float CUT = " + CUT + ", GAIN = " + GAIN + ", THICK = " + THICK + ", LEAD = " + LEAD + ";\n" + """
            uniform sampler2D uTexture;
            // The near sun map's depth, read as a number: what stops the sun first at each texel.
            uniform sampler2D uSolid;
            // One over this picture's texels across.
            uniform float uTexel;
            varying vec2 vUv;
            void main() {
                vec4 texel = texture2D(uTexture, vUv);
                // Outside the window there is nothing, and its frame is in the sun map already.
                if (texel.a < 0.1 || texel.a >= 0.5 + CUT) {
                    discard;
                }
                if (gl_FragCoord.z >= texture2D(uSolid, gl_FragCoord.xy * uTexel).r) {
                    discard;
                }
                vec3 linear = pow(texel.rgb, vec3(2.2));
                float strongest = max(max(linear.r, linear.g), max(linear.b, 1e-4));
                vec3 through = pow(linear / strongest, vec3(THICK)) * min(strongest * GAIN, 1.0);
                through *= smoothstep(LEAD, 2.0 * LEAD, dot(texel.rgb, vec3(0.299, 0.587, 0.114)));
                gl_FragColor = vec4(through, gl_FragCoord.z);
            }
            """;

    /**
     * The colour the sun is past the glass, for a place at c in the near map's texture space: white
     * where no glass lies between the sun and it. For a source that declares nothing named
     * uSunGlass or GLASS_PAST. While no window is in the near map the picture bound is one white
     * texel, at depth 1, which nothing lies past.
     */
    static final String GLSL = "const float GLASS_PAST = " + PAST + ";\n" + """
            uniform sampler2D uSunGlass;
            vec3 sunThroughGlass(vec3 c) {
                vec4 glass = texture2D(uSunGlass, c.xy);
                return c.z > glass.a + GLASS_PAST ? glass.rgb : vec3(1.0);
            }
            """;
}
