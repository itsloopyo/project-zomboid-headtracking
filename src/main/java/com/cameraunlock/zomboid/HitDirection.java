package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import zombie.ai.states.AttackNetworkState;
import zombie.ai.states.AttackState;
import zombie.ai.states.FakeDeadAttackState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.IsoCell;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;

/**
 * What the isometric view shows all round the player and first person leaves out: who hit them,
 * and a zombie close behind. A hit, or a zombie's attack on the player begun, is marked by a red
 * arc at the edge of the picture on the side the attacker is, which follows them as the view
 * turns and fades out over HitArcs.FADE_SECONDS. A zombie within HitArcs.NEAR_TILES out of view
 * that the game shows the player, with nothing between them in its way, puts a faint glow on that
 * edge, stronger the nearer it is and the further round behind.
 *
 * Where players can fight, another player who hit the viewer is marked only while the game
 * itself shows them to the viewer (Online), as it would draw them in its own view.
 *
 * The logic thread says what is marked and the render thread draws it over the finished picture.
 */
final class HitDirection {

    /** The most attackers whose arcs show at once: the oldest gives way to a new one. */
    private static final int MOST = 8;
    /** How far off a zombie can be and still be attacking the player, in tiles. */
    private static final float ATTACK_REACH = 3f;
    /** The arc's half width, in radians, and its thickness and the glow's, in pixels on a screen 1080 high. */
    private static final float ARC_HALF = (float) Math.toRadians(22), ARC_THICK = 12f, GLOW_HALF = (float) Math.toRadians(35), GLOW_THICK = 70f;
    /** How far out the ellipse they lie on reaches, as a share of half the picture each way. */
    private static final float REACH = 0.94f;
    private static final int SIDES = 24, DESIGN_HEIGHT = 1080;
    /** Whether the game shows a character to the viewer: the share of them it draws (WorldScene, CHARACTER_SHOWN_ALPHA). */
    private static final float SHOWN_ALPHA = 0.5f;

    private static final String VERTEX = """
            #version 120
            varying float vWeight;

            void main() {
                vWeight = gl_Vertex.z;
                gl_Position = vec4(gl_Vertex.xy, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform vec4 uColor;
            varying float vWeight;

            void main() {
                gl_FragColor = vec4(uColor.rgb, uColor.a * vWeight);
            }
            """;

    static final SmallProgram program = new SmallProgram("hit direction", VERTEX, FRAGMENT);

    private static final int GL_BLEND = 0x0BE2, GL_SRC_ALPHA = 0x0302, GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_DEPTH_TEST = 0x0B71, GL_CULL_FACE = 0x0B44, GL_TRIANGLE_STRIP = 0x0005;

    /** What is drawn: each mark's bearing and strength, hits first, then glows. Null when nothing is marked. */
    private record Marks(float[] bearings, float[] strengths, int hits, int count) {}

    private volatile Marks shown;

    /** Logic thread: who hit the player and when. */
    private final IsoGameCharacter[] attackers = new IsoGameCharacter[MOST];
    private final long[] hitAt = new long[MOST];
    private int count;
    /** The zombies attacking the player as last seen and now, so an attack is marked as it begins. */
    private IdentityHashMap<IsoZombie, Boolean> attacking = new IdentityHashMap<>(), attackingNow = new IdentityHashMap<>();
    /** The zombies glowing last frame and this one, for the development build's line as one starts. */
    private IdentityHashMap<IsoZombie, Boolean> glowing = new IdentityHashMap<>(), glowingNow = new IdentityHashMap<>();
    /** The most close zombies glowing at once: a crowd round the player glows as much with these. */
    private static final int MOST_GLOWS = 16;
    private final float[] glowBearings = new float[MOST_GLOWS], glowStrengths = new float[MOST_GLOWS];
    private int glows;

    /** Logic thread: the player was hit by someone. A second hit by the same attacker starts their arc again. */
    void hitBy(IsoGameCharacter attacker, long now) {
        for (int i = 0; i < count; i++) {
            if (attackers[i] == attacker) {
                hitAt[i] = now;
                said(attacker, "hit by");
                return;
            }
        }
        if (count == MOST) {
            int oldest = 0;
            for (int i = 1; i < count; i++) {
                if (hitAt[i] < hitAt[oldest]) {
                    oldest = i;
                }
            }
            drop(oldest);
        }
        attackers[count] = attacker;
        hitAt[count++] = now;
        said(attacker, "hit by");
    }

    private IsoPlayer saidFor;
    private ViewCamera.Pose saidFrom;

    private void said(IsoGameCharacter attacker, String what) {
        if (!DevBuild.ON || saidFrom == null) {
            return;
        }
        float bearing = HitArcs.bearing(saidFrom.forwardX, saidFrom.forwardY, saidFrom.upX, saidFrom.upY, saidFrom.rightX, saidFrom.rightY,
                attacker.getX() - saidFrom.eyeX, attacker.getY() - saidFrom.eyeY);
        Log.info(String.format(Locale.ROOT, "hit direction, player %d: %s %s %d, %.2f tiles off, bearing %.0f degrees, the %s edge",
                saidFor.getIndex() + 1, what, kind(attacker), attacker.getID(), (float) Math.hypot(attacker.getX() - saidFrom.eyeX, attacker.getY() - saidFrom.eyeY),
                Math.toDegrees(bearing), HitArcs.EDGE_NAMES[HitArcs.edge(bearing)]));
    }

    private static String kind(IsoGameCharacter character) {
        return character instanceof IsoZombie ? "zombie" : character instanceof IsoAnimal ? "animal" : character instanceof IsoPlayer ? "player" : "character";
    }

    private void drop(int at) {
        count--;
        attackers[at] = attackers[count];
        hitAt[at] = hitAt[count];
        attackers[count] = null;
    }

    /**
     * Logic thread, once a frame of the first person view: what is marked, from where the
     * picture is seen and how wide it is.
     *
     * @param tanHalfWide the tangent of half the picture's width
     */
    void look(IsoPlayer player, ViewCamera.Pose eye, float tanHalfWide, long now) {
        if (!Part.HIT_DIRECTION.on()) {
            hide();
            return;
        }
        saidFor = player;
        saidFrom = eye;
        int index = player.getIndex();
        IsoCell cell = IsoWorld.instance.currentCell;
        int floor = (int) Math.floor(player.getZ());
        glows = 0;
        ArrayList<IsoZombie> zombies = cell.getZombieList();
        for (int i = 0; i < zombies.size(); i++) {
            IsoZombie zombie = zombies.get(i);
            float toX = zombie.getX() - eye.eyeX, toY = zombie.getY() - eye.eyeY;
            if (Math.abs(toX) > ATTACK_REACH || Math.abs(toY) > ATTACK_REACH || zombie.isDead()) {
                continue;
            }
            if (zombie.getTarget() == player && (zombie.isCurrentState(AttackState.instance()) || zombie.isCurrentState(AttackNetworkState.instance())
                    || zombie.isCurrentState(FakeDeadAttackState.instance()))) {
                attackingNow.put(zombie, Boolean.TRUE);
                if (!attacking.containsKey(zombie)) {
                    hitBy(zombie, now);
                }
            }
            float tiles = (float) Math.hypot(toX, toY);
            // The game's own view shows the player no more than this: a zombie it has faded out, in the dark or out of its sight, gives no glow.
            if (tiles > HitArcs.NEAR_TILES || (int) Math.floor(zombie.getZ()) != floor || zombie.isFakeDead() || zombie.getAlpha(index) <= SHOWN_ALPHA) {
                continue;
            }
            float bearing = HitArcs.bearing(eye.forwardX, eye.forwardY, eye.upX, eye.upY, eye.rightX, eye.rightY, toX, toY);
            float strength = HitArcs.near(tiles, bearing, tanHalfWide);
            if (strength <= 0f || glows == MOST_GLOWS || blocked(cell, player, zombie)) {
                continue;
            }
            glowBearings[glows] = bearing;
            glowStrengths[glows++] = strength;
            if (DevBuild.ON) {
                glowingNow.put(zombie, Boolean.TRUE);
                if (!glowing.containsKey(zombie)) {
                    said(zombie, "close behind:");
                }
            }
        }
        IdentityHashMap<IsoZombie, Boolean> swap = attacking;
        attacking = attackingNow;
        attackingNow = swap;
        attackingNow.clear();
        swap = glowing;
        glowing = glowingNow;
        glowingNow = swap;
        glowingNow.clear();

        if (count + glows == 0) {
            shown = null;
            return;
        }
        boolean contested = Online.contested();
        float[] bearings = new float[count + glows], strengths = new float[bearings.length];
        int marks = 0;
        for (int i = count - 1; i >= 0; i--) {
            float fade = HitArcs.fade((now - hitAt[i]) / 1e9f);
            if (fade <= 0f) {
                drop(i);
                continue;
            }
            IsoGameCharacter attacker = attackers[i];
            if (attacker instanceof IsoPlayer other && !(attacker instanceof IsoAnimal)
                    && (other.isInvisible() || contested && other.getAlpha(index) <= SHOWN_ALPHA)) {
                continue;
            }
            float bearing = HitArcs.bearing(eye.forwardX, eye.forwardY, eye.upX, eye.upY, eye.rightX, eye.rightY, attacker.getX() - eye.eyeX, attacker.getY() - eye.eyeY);
            bearings[marks] = bearing;
            strengths[marks++] = fade;
        }
        int hits = marks;
        System.arraycopy(glowBearings, 0, bearings, marks, glows);
        System.arraycopy(glowStrengths, 0, strengths, marks, glows);
        marks += glows;
        shown = marks == 0 ? null : new Marks(bearings, strengths, hits, marks);
    }

    /**
     * Whether something between the player and a zombie stands in its way: a wall, a shut door,
     * which the game's own attack also stops at (AttackState), or a shut window, which it does not.
     */
    private static boolean blocked(IsoCell cell, IsoPlayer player, IsoZombie zombie) {
        LosUtil.TestResults line = LosUtil.lineClear(cell, (int) Math.floor(zombie.getX()), (int) Math.floor(zombie.getY()), (int) Math.floor(zombie.getZ()),
                (int) Math.floor(player.getX()), (int) Math.floor(player.getY()), (int) Math.floor(player.getZ()), false);
        return line == LosUtil.TestResults.Blocked || line == LosUtil.TestResults.ClearThroughClosedDoor || line == LosUtil.TestResults.ClearThroughWindow;
    }

    /** Nothing marked: the view is not first person, or the part is off. Who hit the player is forgotten. */
    void hide() {
        shown = null;
        if (count > 0 || !attacking.isEmpty() || !glowing.isEmpty()) {
            for (int i = 0; i < count; i++) {
                attackers[i] = null;
            }
            count = 0;
            attacking.clear();
            glowing.clear();
        }
        saidFrom = null;
        saidFor = null;
    }

    /** Draws the marks into the picture bound, whose viewport is the whole of a picture this many pixels each way. Render thread. */
    void draw(int width, int height) {
        Marks marks = shown;
        if (marks == null || !program.use()) {
            return;
        }
        GL11.glDisable(GL_DEPTH_TEST);
        GL11.glDisable(GL_CULL_FACE);
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int color = program.at("uColor");
        float unit = (float) height / DESIGN_HEIGHT;
        for (int i = marks.count - 1; i >= 0; i--) {
            boolean hit = i < marks.hits;
            if (hit) {
                GL20.glUniform4f(color, 0.9f, 0.06f, 0.04f, 0.85f * marks.strengths[i]);
                arc(marks.bearings[i], ARC_HALF, ARC_THICK * unit, false, width, height);
            } else {
                GL20.glUniform4f(color, 0.85f, 0.12f, 0.06f, 0.6f * marks.strengths[i]);
                arc(marks.bearings[i], GLOW_HALF, GLOW_THICK * unit, true, width, height);
            }
        }
        GL11.glDisable(GL_BLEND);
    }

    /**
     * A band along the ellipse just inside the edge of the picture, centred on a bearing, fading
     * to nothing at its ends; a glow fades inwards too, from its outer side.
     */
    private static void arc(float bearing, float half, float thick, boolean glow, int width, int height) {
        GL11.glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= SIDES; i++) {
            float along = -1f + 2f * i / SIDES;
            float at = bearing + along * half;
            float ends = 1f - along * along;
            float x = width / 2f * (1f + REACH * HitArcs.across(at)), y = height / 2f * (1f + REACH * HitArcs.up(at));
            // Inwards in pixels, toward the middle of the picture.
            float inX = width / 2f - x, inY = height / 2f - y, length = (float) Math.hypot(inX, inY);
            inX *= thick / length;
            inY *= thick / length;
            GL11.glVertex3f(x * 2f / width - 1f, y * 2f / height - 1f, ends);
            GL11.glVertex3f((x + inX) * 2f / width - 1f, (y + inY) * 2f / height - 1f, glow ? 0f : ends);
        }
        GL11.glEnd();
    }
}
