package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Locale;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import se.krka.kahlua.vm.KahluaTable;
import zombie.Lua.LuaManager;
import zombie.ai.states.AttackNetworkState;
import zombie.ai.states.AttackState;
import zombie.ai.states.FakeDeadAttackState;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.animals.IsoAnimal;
import zombie.iso.IsoCamera;
import zombie.iso.IsoCell;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoWorld;
import zombie.iso.LosUtil;
import zombie.ui.UIElementInterface;
import zombie.ui.UIManager;
import zombie.vehicles.AttackVehicleState;
import zombie.vehicles.BaseVehicle;

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
    /** How far off a zombie can be and still be attacking the player, in tiles, on foot and at the vehicle they sit in, which reaches further from the seat. */
    private static final float ATTACK_REACH = 3f, VEHICLE_ATTACK_REACH = 6f;
    /** The arc's half width, in radians, and its thickness and the glow's, in pixels on a screen 1080 high. */
    private static final float ARC_HALF = (float) Math.toRadians(22), ARC_THICK = 12f, GLOW_HALF = (float) Math.toRadians(35), GLOW_THICK = 70f;
    /**
     * How far out the ellipse they lie on reaches, as a share of half the picture each way. Its
     * lower half stops short of the game's hotbar, which is drawn over the picture, by HOTBAR_GAP
     * pixels on a screen 1080 high.
     */
    private static final float REACH = 0.94f, HOTBAR_GAP = 8f;
    private static final String HOTBAR = "getPlayerHotbar";
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

    /**
     * What is drawn: where on the map each mark's character is and its strength, hits first, then
     * glows. Null when nothing is marked. Each bearing is taken as it is drawn, from the eye that
     * picture was built from, which can be another frame's than the one the marks were made in.
     */
    private static final class Marks {
        final float[] x = new float[MOST + MOST_GLOWS], y = new float[MOST + MOST_GLOWS], strengths = new float[MOST + MOST_GLOWS];
        int hits, count;
        /** The share of the picture's height the game's hotbar covers along its bottom, 0 while it is not shown. */
        float hotbar;
    }

    /** Three, so the logic thread fills one while the render thread may still be drawing either of the last two it was handed. */
    private final Marks[] buffers = {new Marks(), new Marks(), new Marks()};
    private int filling;
    private volatile Marks shown;

    /** Logic thread: who hit the player and when. */
    private final IsoGameCharacter[] attackers = new IsoGameCharacter[MOST];
    private final long[] hitAt = new long[MOST];
    /** Whether each zombie among them was in the cell's list of zombies this frame, or hit the player since the list was looked through. */
    private final boolean[] listed = new boolean[MOST];
    private int count;
    /** The zombies attacking the player last frame and this one, each with how the game had its swing, so each swing is marked as it begins. */
    private IdentityHashMap<IsoZombie, String> attacking = new IdentityHashMap<>(), attackingNow = new IdentityHashMap<>();
    private static final String SWING_STARTS = "start";
    /** The zombies the game has attack as a server says (AttackNetworkState), last frame and this one, for the development build's line as one starts. */
    private IdentityHashMap<IsoZombie, Boolean> network = new IdentityHashMap<>(), networkNow = new IdentityHashMap<>();
    /** The zombies glowing last frame and this one, for the development build's line as one starts. */
    private IdentityHashMap<IsoZombie, Boolean> glowing = new IdentityHashMap<>(), glowingNow = new IdentityHashMap<>();
    /** The most close zombies glowing at once: a crowd round the player glows as much with these. */
    private static final int MOST_GLOWS = 16;
    private final float[] glowX = new float[MOST_GLOWS], glowY = new float[MOST_GLOWS], glowStrengths = new float[MOST_GLOWS];
    private int glows;

    /**
     * Whether a zombie is attacking the player: going for them (AttackState, the AttackNetworkState
     * a server's zombie is in, or rising from lying fake dead), or at the vehicle they sit in
     * (AttackVehicleState, whose target is someone in it).
     */
    private static boolean attacks(IsoZombie zombie, IsoPlayer player, BaseVehicle seat) {
        IsoMovingObject target = zombie.getTarget();
        if (target == player) {
            return zombie.isCurrentState(AttackState.instance()) || zombie.isCurrentState(AttackNetworkState.instance())
                    || zombie.isCurrentState(FakeDeadAttackState.instance());
        }
        return seat != null && target instanceof IsoGameCharacter inside && inside.getVehicle() == seat && zombie.isCurrentState(AttackVehicleState.instance());
    }

    /** Logic thread: the player was hit by someone, or a zombie swung at them. Another by the same attacker starts their arc again. */
    void hitBy(IsoGameCharacter attacker, long now, String what) {
        for (int i = 0; i < count; i++) {
            if (attackers[i] == attacker) {
                hitAt[i] = now;
                listed[i] = true;
                said(attacker, what);
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
        listed[count] = true;
        hitAt[count++] = now;
        said(attacker, what);
    }

    private IsoPlayer saidFor;
    private ViewCamera.Pose saidFrom;

    private void said(IsoGameCharacter attacker, String what) {
        if (!DevBuild.ON || saidFrom == null) {
            return;
        }
        float bearing = HitArcs.bearing(saidFrom.forwardX, saidFrom.forwardY, saidFrom.forwardUp, saidFrom.upX, saidFrom.upY,
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
        listed[at] = listed[count];
        attackers[count] = null;
    }

    /** Logic thread: the game is making a zombie it held into another (IsoZombie.resetForReuse), which is no longer whoever hit the player. */
    void forget(IsoZombie zombie) {
        for (int i = count - 1; i >= 0; i--) {
            if (attackers[i] == zombie) {
                drop(i);
            }
        }
        attacking.remove(zombie);
        network.remove(zombie);
        glowing.remove(zombie);
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
        Arrays.fill(listed, false);
        BaseVehicle seat = player.getVehicle();
        float reach = seat == null ? ATTACK_REACH : VEHICLE_ATTACK_REACH;
        ArrayList<IsoZombie> zombies = cell.getZombieList();
        for (int i = 0; i < zombies.size(); i++) {
            IsoZombie zombie = zombies.get(i);
            for (int j = 0; j < count; j++) {
                listed[j] |= attackers[j] == zombie;
            }
            float toX = zombie.getX() - eye.eyeX, toY = zombie.getY() - eye.eyeY;
            if (Math.abs(toX) > reach || Math.abs(toY) > reach || zombie.isDead()) {
                continue;
            }
            if (attacks(zombie, player, seat)) {
                // The game sets the outcome to "start" as each swing begins (AttackState.enter, AttackNetworkState.enter) and to how it ended once it lands.
                String outcome = zombie.getAttackOutcome(), before = attacking.get(zombie);
                attackingNow.put(zombie, String.valueOf(outcome));
                if (!attacking.containsKey(zombie) || SWING_STARTS.equals(outcome) && !SWING_STARTS.equals(before)) {
                    hitBy(zombie, now, "attacked by");
                }
            }
            if (DevBuild.ON && zombie.isCurrentState(AttackNetworkState.instance())) {
                networkNow.put(zombie, Boolean.TRUE);
                if (!network.containsKey(zombie)) {
                    Log.info(String.format(Locale.ROOT, "hit direction, player %d: zombie %d attacks as the server says (AttackNetworkState), its target %s",
                            index + 1, zombie.getID(), zombie.getTarget() == player ? "this player" : String.valueOf(zombie.getTarget())));
                }
            }
            float tiles = (float) Math.hypot(toX, toY);
            // The game's own view shows the player no more than this: a zombie it has faded out, in the dark or out of its sight, gives no glow.
            if (tiles > HitArcs.NEAR_TILES || (int) Math.floor(zombie.getZ()) != floor || zombie.isFakeDead() || zombie.getAlpha(index) <= SHOWN_ALPHA) {
                continue;
            }
            float bearing = HitArcs.bearing(eye.forwardX, eye.forwardY, eye.forwardUp, eye.upX, eye.upY, toX, toY);
            float strength = HitArcs.near(tiles, bearing, tanHalfWide);
            if (strength <= 0f || glows == MOST_GLOWS || blocked(cell, player, zombie)) {
                continue;
            }
            glowX[glows] = zombie.getX();
            glowY[glows] = zombie.getY();
            glowStrengths[glows++] = strength;
            if (DevBuild.ON) {
                glowingNow.put(zombie, Boolean.TRUE);
                if (!glowing.containsKey(zombie)) {
                    said(zombie, "close behind:");
                }
            }
        }
        IdentityHashMap<IsoZombie, String> swings = attacking;
        attacking = attackingNow;
        attackingNow = swings;
        attackingNow.clear();
        IdentityHashMap<IsoZombie, Boolean> swap = network;
        network = networkNow;
        networkNow = swap;
        networkNow.clear();
        swap = glowing;
        glowing = glowingNow;
        glowingNow = swap;
        glowingNow.clear();

        if (count + glows == 0) {
            shown = null;
            return;
        }
        boolean contested = Online.contested();
        Marks filled = buffers[filling];
        float[] x = filled.x, y = filled.y, strengths = filled.strengths;
        int marks = 0;
        for (int i = count - 1; i >= 0; i--) {
            float fade = HitArcs.fade((now - hitAt[i]) / 1e9f);
            IsoGameCharacter attacker = attackers[i];
            if (fade <= 0f || attacker.isDead() || (attacker instanceof IsoZombie ? !listed[i] : !cell.getObjectList().contains(attacker))) {
                drop(i);
                continue;
            }
            if (attacker instanceof IsoPlayer other && !(attacker instanceof IsoAnimal)
                    && (other.isInvisible() || contested && other.getAlpha(index) <= SHOWN_ALPHA)) {
                continue;
            }
            x[marks] = attacker.getX();
            y[marks] = attacker.getY();
            strengths[marks++] = fade;
        }
        int hits = marks;
        System.arraycopy(glowX, 0, x, marks, glows);
        System.arraycopy(glowY, 0, y, marks, glows);
        System.arraycopy(glowStrengths, 0, strengths, marks, glows);
        marks += glows;
        if (marks == 0) {
            shown = null;
            return;
        }
        filled.hits = hits;
        filled.count = marks;
        filled.hotbar = hotbar(index);
        shown = filled;
        filling = (filling + 1) % buffers.length;
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

    private boolean hotbarTroubleSaid;
    private float hotbarSaid = -1f;

    /**
     * Logic thread, the one the game's Lua runs on: the share of the player's part of the screen
     * that the game's hotbar covers along its bottom, 0 while it is not shown. The game puts it at
     * the bottom of that part (ISHotbar.setSizeAndPosition), and the picture fills the part.
     */
    private float hotbar(int index) {
        Object[] found = LuaManager.caller.pcall(UIManager.getDefaultThread(), LuaManager.env.rawget(HOTBAR), Double.valueOf(index));
        Object bar = found.length > 1 ? found[1] : null;
        UIElementInterface shown = bar instanceof KahluaTable table && table.rawget("javaObject") instanceof UIElementInterface element ? element : null;
        if (found.length == 0 || !Boolean.TRUE.equals(found[0]) || bar != null && shown == null) {
            if (!hotbarTroubleSaid) {
                hotbarTroubleSaid = true;
                Log.info("the game's " + HOTBAR + "(" + index + ") gave no hotbar to keep the hit arcs above: " + Arrays.toString(found));
            }
            return 0f;
        }
        float covered = 0f;
        if (shown != null && shown.isVisible()) {
            float high = IsoCamera.getScreenHeight(index);
            covered = Math.max(0f, (float) ((IsoCamera.getScreenTop(index) + high - shown.getY()) / high));
        }
        if (DevBuild.ON && Math.abs(covered - hotbarSaid) > 0.002f) {
            hotbarSaid = covered;
            Log.info(String.format(Locale.ROOT, "hit direction, player %d: the game's hotbar covers the bottom %.3f of the picture", index + 1, covered));
        }
        return covered;
    }

    /** Nothing marked: the view is not first person, or the part is off. Who hit the player is forgotten. */
    void hide() {
        shown = null;
        if (count > 0 || !attacking.isEmpty() || !network.isEmpty() || !glowing.isEmpty()) {
            for (int i = 0; i < count; i++) {
                attackers[i] = null;
            }
            count = 0;
            attacking.clear();
            network.clear();
            glowing.clear();
        }
        saidFrom = null;
        saidFor = null;
    }

    /**
     * Draws the marks into the picture bound, whose viewport is the whole of a picture this many
     * pixels each way, seen from this eye. Render thread.
     */
    void draw(ViewCamera.Pose eye, int width, int height) {
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
        float below = Math.min(REACH, 1f - 2f * (marks.hotbar + HOTBAR_GAP / DESIGN_HEIGHT));
        for (int i = marks.count - 1; i >= 0; i--) {
            float bearing = HitArcs.bearing(eye.forwardX, eye.forwardY, eye.forwardUp, eye.upX, eye.upY, marks.x[i] - eye.eyeX, marks.y[i] - eye.eyeY);
            if (i < marks.hits) {
                GL20.glUniform4f(color, 0.9f, 0.06f, 0.04f, 0.85f * marks.strengths[i]);
                arc(bearing, ARC_HALF, ARC_THICK * unit, false, below, width, height);
            } else {
                GL20.glUniform4f(color, 0.85f, 0.12f, 0.06f, 0.6f * marks.strengths[i]);
                arc(bearing, GLOW_HALF, GLOW_THICK * unit, true, below, width, height);
            }
        }
        GL11.glDisable(GL_BLEND);
    }

    /**
     * A band along the ellipse just inside the edge of the picture, centred on a bearing, fading
     * to nothing at its ends; a glow fades inwards too, from its outer side. Below the middle the
     * ellipse reaches down only as far as below.
     */
    private static void arc(float bearing, float half, float thick, boolean glow, float below, int width, int height) {
        GL11.glBegin(GL_TRIANGLE_STRIP);
        for (int i = 0; i <= SIDES; i++) {
            float along = -1f + 2f * i / SIDES;
            float at = bearing + along * half;
            float ends = 1f - along * along;
            float x = width / 2f * (1f + REACH * HitArcs.across(at)), up = HitArcs.up(at), y = height / 2f * (1f + (up < 0f ? below : REACH) * up);
            float inX = width / 2f - x, inY = height / 2f - y, length = (float) Math.hypot(inX, inY);
            inX *= thick / length;
            inY *= thick / length;
            GL11.glVertex3f(x * 2f / width - 1f, y * 2f / height - 1f, ends);
            GL11.glVertex3f((x + inX) * 2f / width - 1f, (y + inY) * 2f / height - 1f, glow ? 0f : ends);
        }
        GL11.glEnd();
    }
}
