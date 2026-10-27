package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import zombie.GameTime;
import zombie.characters.CharacterInputKeyBinding;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.animals.IsoAnimal;
import zombie.core.Core;
import zombie.core.physics.BallisticsController;
import zombie.core.physics.RagdollBodyPart;
import zombie.core.physics.WorldSimulation;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoUtils;
import zombie.iso.Vector2;
import zombie.iso.Vector3;
import zombie.scripting.objects.PhysicsHitReactionScript;

/**
 * What the game is told was pressed while the first person view holds the
 * mouse, so that the view plays like a first person game: the attack button
 * attacks at once, a click is never a click on the world under the held
 * cursor, a shot goes where the view points, and Left Shift sprints.
 *
 * Only the game's reading of its inputs changes. What an attack then does, and
 * every binding the player has saved, are the game's own. With the first
 * person view off, or the mouse out as a pointer, every hook here hands back
 * what it was given, except that the keys stay the first person ones for as
 * long as the view is played in.
 *
 * Everything runs on the game's logic thread.
 */
public final class Controls {
    final View view;

    Controls(View view) {
        this.view = view;
    }

    private final AimInput aim = new AimInput();
    private boolean stockAim, filteredAim, attackDown;
    private boolean interactDown, interactTaken;

    /** Where the clean aim meets the world, in the game's own axes: tiles and floor levels. */
    private float aimPointX, aimPointY, aimPointLevel;
    private final HitPart hitPart = new HitPart();

    /** A binding the game holds and what first person answers with in its place. */
    private static final class Substituted {
        Core.KeyBinding from, to;
    }

    private final Map<String, Substituted> substituted = new HashMap<>();

    {
        for (String name : KeySubstitutions.NAMES) {
            substituted.put(name, new Substituted());
        }
    }


    /**
     * Called with what the game's aim input reads. The attack button holds
     * the aim input down, because the game takes an attack only from a
     * character whose aim was already held. With the game's toggle to aim
     * option on, every click would flip the toggle, so the input is left alone:
     * that player has an aim that stays up already.
     *
     * A controller's aim is its aiming stick pushed: the stick is where the game aims. In
     * first person the stick turns the view and the aim is where the view looks, so the stick
     * is no aim. With a firearm in hand the sights are raised by the left trigger, as in
     * other first person games: by whatever the player has the game's shove on, which is
     * the left trigger, and which is kept from shoving for as long (see meleeKey).
     */
    public boolean aimKey(boolean stock) {
        boolean wasFiltered = filteredAim, wasAttack = attackDown;
        IsoPlayer player = view.player();
        boolean captured = view.firstPerson.lookCaptured() && player != null;
        boolean pad = captured && View.onController(player);
        if (pad) {
            stock = sightsOnTheShove(player);
        }
        stockAim = stock;
        boolean dragging = captured && !pad && DragAim.of(player.getPlayerNum()) != null;
        if (cancelHeld && !stock) {
            cancelHeld = false;
        }
        if (dragging || cancelHeld) {
            // The left button is the drag's, as in the game's own view, and the right one that put it away is no aim either.
            aim.reset();
            attackDown = false;
            filteredAim = false;
            turnedOnly = false;
            if (FirstPerson.TRACE && wasFiltered) {
                Log.info("controls " + describe());
            }
            return false;
        }
        if (captured && player.getVehicle() == null && !togglesAim(player)) {
            attackDown = pad ? padIsTheCharacters(player) && CharacterJoypadButtonBinding.Attack.isDown(player.getJoypadBind())
                    : CharacterInputKeyBinding.Attack.isKeyDownInWorld();
            filteredAim = aim.held(stock, attackDown, System.nanoTime());
        } else {
            aim.reset();
            attackDown = false;
            filteredAim = stock;
        }
        if (FirstPerson.TRACE && (filteredAim != wasFiltered || attackDown != wasAttack)) {
            Log.info("controls " + describe());
        }
        turnedOnly = !filteredAim && turnedToTheView(player);
        return filteredAim || turnedOnly;
    }

    /**
     * A character that is not aiming turns to the way it walks, so walked backwards it would
     * show its back to what the view looks at: in a mirror, to other players, and to the game's
     * own vision cone. Walked sideways or backwards it is held in the game's aiming stance, the
     * one way the game has of facing one way and moving another, raised to the pace the game
     * gives its walk (Sidestep). Jogging or sprinting, dragging a body, with the FacesTheView row
     * off or on a server, the game turns the character to the way it goes, as it would anyway.
     * The sights stay down. They follow the player's own aim input alone.
     */
    private boolean turnedToTheView(IsoPlayer player) {
        return Rules.facesTheView() && player != null && player.getVehicle() == null && view.firstPerson.movingAside()
                && !asksToRun(player) && !player.isDraggingCorpse();
    }

    private static final Field INPUT_STATE = inputState();

    private static Field inputState() {
        try {
            Field field = IsoPlayer.class.getDeclaredField("inputState");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's player input has changed: " + e, e);
        }
    }

    /**
     * Whether the game read a jog or a sprint from the player's input on its last reading of it,
     * the keys or its toggles to run and sprint. In the aiming stance the game keeps the character
     * at a walk, so it is the input that says so and not the character. The aim is asked for
     * before the game reads the run again, so a jog begun this frame holds the stance one more.
     */
    private static boolean asksToRun(IsoPlayer player) {
        try {
            IsoPlayer.InputState input = (IsoPlayer.InputState) INPUT_STATE.get(player);
            return input.isRunning() || input.sprinting;
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("the game's player input has changed: " + e, e);
        }
    }

    private boolean turnedOnly;
    /** Whether the aim button that put a drag cursor away is still held. */
    private boolean cancelHeld;

    /** A right press has put the drag cursor away (WorldMenu): the button is no aim until it is let go. */
    void dragPutAway() {
        cancelHeld = true;
    }

    /** Whether a controller's triggers are the character's: while it works one of the game's panels the game has it ignore the aim. */
    private static boolean padIsTheCharacters(IsoPlayer player) {
        return !player.getCharacterInputComponent().isJoypadIgnoreAim();
    }

    /** Whether a controller's shove input is raising a firearm's sights. */
    private boolean sightsOnTheShove(IsoPlayer player) {
        ViewModel.Held held = view.viewModel.held;
        return held != null && held.firearm() && padIsTheCharacters(player) && CharacterJoypadButtonBinding.Melee.isDown(player.getJoypadBind());
    }

    /**
     * Called with what the game's shove input reads. A controller's raises a firearm's
     * sights in first person, and is not a shove as well.
     */
    public boolean meleeKey(boolean stock) {
        IsoPlayer player = view.player();
        return stock && !(view.firstPerson.lookCaptured() && player != null && View.onController(player) && sightsOnTheShove(player));
    }

    /** Whether the game's toggle to aim option holds this player's aim: it is the keyboard's and the mouse's, and a controller has none. */
    private static boolean togglesAim(IsoPlayer player) {
        return !View.onController(player) && Core.getInstance().isToggleToAim();
    }

    /**
     * Whether the game has the character aiming for no reason but to face it the way the view
     * does: the player is neither aiming nor attacking. What the player is shown then is what
     * they are shown at the hip, the mod's own mark and not the game's aiming cursor.
     */
    boolean turnedOnly() {
        return turnedOnly;
    }

    /**
     * Called with what the game's attack button reads. A firearm's shot is held back until
     * the game has had the aim long enough to find what the shot is on: see AimInput.
     */
    public boolean attackKey(boolean stock) {
        IsoPlayer player = view.player();
        if (!view.firstPerson.lookCaptured() || player == null || player.getVehicle() != null || togglesAim(player)) {
            return stock;
        }
        if (!View.onController(player) && DragAim.of(player.getPlayerNum()) != null) {
            // The click is the drag's: the game reads it as its build button, which is its attack binding.
            return false;
        }
        return aim.attack(stock, player.isAimingFirearmEquipped());
    }

    /** Once a frame of the game's logic. */
    void frame() {
        aim.frame(WorldSimulation.instance.getBulletFrameNo());
    }

    /**
     * Whether a firearm's sights are up: the player's own aim input, never the
     * aim an attack click implies, with a firearm in hand, on foot or in a seat the
     * game lets it be fired from.
     */
    boolean sightsUp(IsoPlayer player) {
        boolean aiming = stockAim || togglesAim(player) && player.isAiming();
        return aiming && view.firstPerson.lookCaptured() && (player.getVehicle() == null || Ride.armedFromSeat(player)) && player.isAimingFirearmEquipped();
    }

    /**
     * Called with whether the game's own view has an object under the cursor
     * for a click to act on. The held cursor is not something the player
     * points with, and with the pointer out it points at the first person
     * picture, which does not show what the game's own view has under it. So
     * nothing is: the world is acted on through the middle of the view.
     */
    public boolean picked(boolean stock) {
        return !view.firstPerson.active() && stock;
    }

    /**
     * Called with whether the Interact key is down. A press with the mouse
     * turning the view goes first to what the middle of the view is on. When
     * something there takes it the game is not told of the key until it is
     * let go, or its own handler would act a second time on whatever the
     * character's body faces.
     */
    public boolean interactKey(boolean stock) {
        if (!stock) {
            interactDown = false;
            interactTaken = false;
            return false;
        }
        if (!interactDown) {
            interactDown = true;
            interactTaken = view.firstPerson.lookCaptured() && view.firstPerson.useAtCentre();
        }
        return !interactTaken;
    }

    /**
     * Called where the game asks whether the player is aiming before it hands
     * a click to the object under the cursor, which it does not do for someone
     * aiming.
     */
    public boolean aimingForWorldClicks(boolean stock) {
        return view.firstPerson.lookCaptured() || stock;
    }

    /**
     * Called with the direction the game aims in, which it works out from the
     * cursor at the height of the character's chest. The aim is the clean
     * view's direction, whatever height the view meets the world at.
     */
    public Vector2 aimVector(Vector2 stock) {
        if (!view.firstPerson.lookCaptured()) {
            return stock;
        }
        float yaw = view.firstPerson.yaw();
        return stock.set((float) Math.cos(yaw), (float) Math.sin(yaw));
    }

    /** How far past a firearm's range the aim is traced: the game measures the range from the muzzle, which is ahead of the eye. */
    private static final float PAST_RANGE = 1f;

    /**
     * How far the aim is traced: as far as the firearm in hand reaches, by the game's own sum
     * of its range, or WorldScene.REACH. The point the game's ballistics are given has to be on
     * whatever the aim meets out to that range, or the game finds no target there.
     */
    static float aimReach(IsoPlayer player) {
        return player.getPrimaryHandItem() instanceof HandWeapon weapon && weapon.isAimedFirearm()
                ? Math.max(WorldScene.REACH, weapon.getMaxRange(player) * weapon.getRangeMod(player) + PAST_RANGE)
                : WorldScene.REACH;
    }

    /**
     * Where the clean aim meets the world this frame.
     *
     * @param level the game's own height: floor levels above level 0
     */
    void aimAt(float x, float y, float level) {
        aimPointX = x;
        aimPointY = y;
        aimPointLevel = level;
    }

    /** Where the clean aim met the world when it was last placed, in the game's measure: x, y and floor levels, into out. */
    void aimPoint(float[] out) {
        out[0] = aimPointX;
        out[1] = aimPointY;
        out[2] = aimPointLevel;
    }

    /**
     * Called where the game's ballistics read the screen column of the
     * reticle. They look for a target along the line through the reticle in
     * the game's own view, so the reticle they are given is where the aim
     * point falls in that view, worked out with the camera as it is at the
     * moment they ask.
     */
    public int aimPointX(int stock) {
        return view.firstPerson.lookCaptured() ? Math.round(IsoUtils.XToScreenExact(aimPointX, aimPointY, aimPointLevel, 0)) : stock;
    }

    /** Called where the game's ballistics read the screen row of the reticle. */
    public int aimPointY(int stock) {
        return view.firstPerson.lookCaptured() ? Math.round(IsoUtils.YToScreenExact(aimPointX, aimPointY, aimPointLevel, 0)) : stock;
    }

    /**
     * Called where the game has just worked out where the muzzle is and which way it points.
     * That is where a shot starts, the line a target is let into the shot's hit list along,
     * the way a shot that found nothing is sent, and the way a shotgun's spread is laid.
     *
     * With a firearm's sights up a lean moves the weapon with the eye, and the shot starts
     * from where the weapon is drawn: the muzzle is moved by what of the lean the weapon has.
     *
     * The way it points is the game's own model's barrel, which the first person view does
     * not draw: held low and across the body at the hip, most of a second in coming up to the
     * aim, and some degrees off it when it is up. The weapon the view draws points at the
     * aim point at the hip and with the sights up alike, so that is the way the shot goes.
     */
    void muzzle(BallisticsController ballistics, Vector3 muzzle, Vector3 direction) {
        if (!view.firstPerson.lookCaptured()) {
            return;
        }
        if (FirstPerson.TRACE && filteredAim) {
            sayBallistics(ballistics);
        }
        float[] carried = view.headTracking.carried();
        muzzle.x += carried[0];
        muzzle.y += carried[1];
        muzzle.z += carried[2] / ViewCamera.LEVEL_HEIGHT;
        direction.x = aimPointX - muzzle.x;
        direction.y = aimPointY - muzzle.y;
        direction.z = aimPointLevel - muzzle.z;
    }

    /**
     * The part of a character a first person shot is on, for the game to hit: see {@link HitPart}.
     * {@link HitPart#NONE} leaves the game to decide as it does, which it does for a player who
     * could be fought here ({@link Online#aimPicksThePartOf}) and for an animal, whose skeleton is
     * not a person's. Each of a blast's pellets is on the part its own line is on.
     *
     * @param weapon what the shot is fired from
     * @param pellet how many of the shot's pellets the game has already dealt this character
     */
    int aimedPart(IsoGameCharacter target, HandWeapon weapon, int pellet) {
        if (!view.firstPerson.lookCaptured() || target instanceof IsoAnimal || !Online.aimPicksThePartOf(target instanceof IsoPlayer)) {
            return HitPart.NONE;
        }
        ViewCamera.Pose aim = view.firstPerson.aimView();
        // The game spreads a shot into pellets, each a hit of its own, for a weapon whose damage falls off with range, and for no other.
        if (weapon.isRangeFalloff()) {
            aim = pelletLine(aim, view.player().getBallisticsController(), target.getID(), pellet);
        }
        float standsUp = view.kerbSteps.up(view.worldScene.terrain, target, target.getX(), target.getY(), target.getZ());
        int part = hitPart.part(aim, standsUp, target);
        if (part != HitPart.NONE) {
            HitImpulse.aimed(target.getID(), aim, hitPart, part);
            if (target instanceof IsoZombie && weapon.getAmmoType() != null) {
                HitFlinch.hit(target, part, aim, hitPart, standsUp, PhysicsHitReactionScript.getImpulse(RagdollBodyPart.BODYPART_SPINE, weapon.getAmmoType()));
            }
        }
        if (FirstPerson.TRACE) {
            Log.info(String.format(Locale.ROOT, "shot part: %s of the character at (%.2f, %.2f)",
                    part == HitPart.NONE ? "none, the game's own" : RagdollBodyPart.values()[part].name(), target.getX(), target.getY()));
        }
        return part;
    }

    private final ViewCamera.Pose pelletAim = new ViewCamera.Pose();

    /**
     * The clean aim turned as far off the barrel's line as the {@code pellet}th pellet the game
     * sent into the character {@code target} went: the game casts each pellet of a blast from the
     * muzzle and keeps where it struck (id, x, height in the picture's measure, y, as
     * CombatManager.fireWeapon reads them, its tracers drawn to the same points). The aim
     * itself where the game has no such pellet: a character let in through a wall (Penetration)
     * was struck by none.
     */
    private ViewCamera.Pose pelletLine(ViewCamera.Pose aim, BallisticsController ballistics, int target, int pellet) {
        float[] spread = ballistics.getCachedBallisticsTargetSpreadData();
        int at = -1;
        for (int i = 0, found = 0; i < ballistics.getNumberOfCachedSpreadData() && at < 0; i++) {
            if ((int) spread[i * 4] == target && found++ == pellet) {
                at = i * 4;
            }
        }
        if (at < 0) {
            return aim;
        }
        Vector3 muzzle = ballistics.getMuzzlePosition(), barrel = ballistics.getMuzzleDirection();
        float wayX = spread[at + 1] - muzzle.x, wayY = spread[at + 3] - muzzle.y, wayUp = spread[at + 2] - muzzle.z * ViewCamera.LEVEL_HEIGHT;
        float barrelUp = barrel.z * ViewCamera.LEVEL_HEIGHT;
        float way = (float) Math.sqrt(wayX * wayX + wayY * wayY + wayUp * wayUp);
        float along = (float) Math.sqrt(barrel.x * barrel.x + barrel.y * barrel.y + barrelUp * barrelUp);
        float x = aim.forwardX + wayX / way - barrel.x / along, y = aim.forwardY + wayY / way - barrel.y / along, up = aim.forwardUp + wayUp / way - barrelUp / along;
        float length = (float) Math.sqrt(x * x + y * y + up * up);
        pelletAim.set(aim);
        pelletAim.forwardX = x / length;
        pelletAim.forwardY = y / length;
        pelletAim.forwardUp = up / length;
        return pelletAim;
    }

    /** Whether the part {@link #aimedPart} last found is a hand or a foot, which the game counts with the forearm or the calf. */
    boolean aimedEnd() {
        return hitPart.end;
    }

    /**
     * Called with the binding the game holds under a name. The escape menu's
     * key page is read and saved through the same lookup, so while that menu
     * is up the game is always given the player's own. Alone the game is
     * paused under the menu. On a server nothing pauses, and the menu itself
     * is asked for.
     */
    public Core.KeyBinding binding(Core.KeyBinding stock, String name) {
        Substituted held = substituted.get(name);
        if (held == null || !view.firstPerson.active() || GameTime.isGamePaused() || Panels.menuOpen()) {
            return stock;
        }
        if (held.from != stock) {
            KeySubstitutions.Keys keys = KeySubstitutions.substitute(name, stock.keyValue(), stock.altKey());
            // The answer first: a second asker that sees the new binding noted must find its answer there.
            held.to = keys == null ? stock
                    : new Core.KeyBinding(stock.name(), keys.key(), keys.alt(), stock.shift(), stock.ctrl(), stock.alt());
            held.from = stock;
        }
        return held.to;
    }

    private Field[] ballisticsFields;
    private long ballisticsSaid;

    /**
     * For the trace, four times a second while the aim is held: where the game's ballistics
     * have the muzzle, which way it points, where they take the aim to be and how many
     * targets they have. A shot that misses what the view is on is one of these being off,
     * and which one cannot be told from the picture.
     */
    private void sayBallistics(BallisticsController ballistics) {
        long now = System.nanoTime();
        if (now - ballisticsSaid < 250_000_000L) {
            return;
        }
        ballisticsSaid = now;
        try {
            if (ballisticsFields == null) {
                String[] names = {"muzzlePosition", "muzzleDirection", "isoAimingPosition", "targetPosition", "numberOfTargets", "numberOfCameraTargets"};
                Field[] found = new Field[names.length];
                for (int i = 0; i < names.length; i++) {
                    found[i] = BallisticsController.class.getDeclaredField(names[i]);
                    found[i].setAccessible(true);
                }
                ballisticsFields = found;
            }
            StringBuilder said = new StringBuilder("ballistics");
            for (Field field : ballisticsFields) {
                Object value = field.get(ballistics);
                said.append(' ').append(field.getName()).append(' ');
                if (value instanceof Vector3 v) {
                    said.append(String.format(Locale.ROOT, "(%.2f, %.2f, %.2f)", v.x, v.y, v.z));
                } else {
                    said.append(value);
                }
            }
            Log.info(said.append(String.format(Locale.ROOT, " aim point (%.2f, %.2f, %.2f)", aimPointX, aimPointY, aimPointLevel)).toString());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's ballistics have changed: " + e, e);
        }
    }

    /**
     * For the trace, at each shot: what the game's to-hit sum has to go on. The game has
     * made its list of what the shot can hit by now, each with its chance out of 100, and
     * the aiming delay is the part of that sum that falls while the aim is held.
     */
    void sayShot() {
        IsoPlayer player = view.player();
        try {
            Object delay = IsoPlayer.class.getMethod("getAimingDelay").invoke(player);
            Object moving = IsoPlayer.class.getMethod("getBeenMovingFor").invoke(player);
            List<?> hits = (List<?>) IsoPlayer.class.getMethod("getHitInfoList").invoke(player);
            StringBuilder said = new StringBuilder(String.format(Locale.ROOT, "shot: aim stock %b filtered %b attack %b isAiming %b aiming delay %s moving %s can hit %d",
                    stockAim, filteredAim, attackDown, player.isAiming(), delay, moving, hits.size()));
            for (int i = 0; i < hits.size(); i++) {
                Object hit = hits.get(i);
                said.append(String.format(Locale.ROOT, " [chance %d at %.2f tiles]", hit.getClass().getField("chance").getInt(hit),
                        Math.sqrt(hit.getClass().getField("distSq").getFloat(hit))));
            }
            Log.info(said.toString());
            ballisticsSaid = 0L;
            sayBallistics(player.getBallisticsController());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("the game's hit list has changed: " + e, e);
        }
    }

    /** The inputs as the game was last given them, for the trace. */
    String describe() {
        return String.format(Locale.ROOT, "aim stock %b filtered %b attack %b sights zoom %.4f fov %.2f",
                stockAim, filteredAim, attackDown, view.fieldOfView.zoom(), view.fieldOfView.drawnDegrees());
    }
}
