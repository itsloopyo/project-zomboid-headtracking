package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import zombie.GameTime;
import zombie.characters.CharacterInputKeyBinding;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.iso.IsoUtils;
import zombie.iso.Vector2;

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
    private static final AimInput aim = new AimInput();
    private static boolean stockAim, filteredAim, attackDown;

    /** Where the clean aim meets the world, in the game's own axes: tiles and floor levels. */
    private static float aimPointX, aimPointY, aimPointLevel;

    /** A binding the game holds and what first person answers with in its place. */
    private static final class Substituted {
        Core.KeyBinding from, to;
    }

    private static final Map<String, Substituted> substituted = new HashMap<>();

    static {
        for (String name : KeySubstitutions.NAMES) {
            substituted.put(name, new Substituted());
        }
    }

    private Controls() {}

    /**
     * Called with what the game's aim input reads. The attack button holds
     * the aim input down, because the game takes an attack only from a
     * character whose aim was already held. With the game's toggle to aim
     * option on, every click would flip the toggle, so the input is left alone:
     * that player has an aim that stays up already.
     */
    public static boolean aimKey(boolean stock) {
        boolean wasFiltered = filteredAim, wasAttack = attackDown;
        stockAim = stock;
        IsoPlayer player = IsoPlayer.getInstance();
        if (FirstPerson.lookCaptured() && player != null && player.getVehicle() == null
                && !Core.getInstance().isToggleToAim()) {
            attackDown = CharacterInputKeyBinding.Attack.isKeyDownInWorld();
            filteredAim = aim.held(stock, attackDown, System.nanoTime());
        } else {
            aim.reset();
            attackDown = false;
            filteredAim = stock;
        }
        if (FirstPerson.TRACE && (filteredAim != wasFiltered || attackDown != wasAttack)) {
            Log.info("controls " + describe());
        }
        return filteredAim;
    }

    /**
     * Whether a firearm's sights are up: the player's own aim input, never the
     * aim an attack click implies, with a firearm in hand and on foot.
     */
    static boolean sightsUp(IsoPlayer player) {
        boolean aiming = stockAim || Core.getInstance().isToggleToAim() && player.isAiming();
        return aiming && FirstPerson.lookCaptured() && player.getVehicle() == null && player.isAimingFirearmEquipped();
    }

    /**
     * Called with whether the game's own view has an object under the cursor
     * for a click to act on. The held cursor is not something the player
     * points with, so nothing is.
     */
    public static boolean picked(boolean stock) {
        return !FirstPerson.lookCaptured() && stock;
    }

    /**
     * Called where the game asks whether the player is aiming before it hands
     * a click to the object under the cursor, which it does not do for someone
     * aiming.
     */
    public static boolean aimingForWorldClicks(boolean stock) {
        return FirstPerson.lookCaptured() || stock;
    }

    /**
     * Called with the direction the game aims in, which it works out from the
     * cursor at the height of the character's chest. The aim is the clean
     * view's direction, whatever height the view meets the world at.
     */
    public static Vector2 aimVector(Vector2 stock) {
        if (!FirstPerson.lookCaptured()) {
            return stock;
        }
        float yaw = FirstPerson.yaw();
        return stock.set((float) Math.cos(yaw), (float) Math.sin(yaw));
    }

    /**
     * Where the clean aim meets the world this frame.
     *
     * @param level the game's own height: floor levels above level 0
     */
    static void aimAt(float x, float y, float level) {
        aimPointX = x;
        aimPointY = y;
        aimPointLevel = level;
    }

    /**
     * Called where the game's ballistics read the screen column of the
     * reticle. They look for a target along the line through the reticle in
     * the game's own view, so the reticle they are given is where the aim
     * point falls in that view, worked out with the camera as it is at the
     * moment they ask.
     */
    public static int aimPointX(int stock) {
        return FirstPerson.lookCaptured() ? Math.round(IsoUtils.XToScreenExact(aimPointX, aimPointY, aimPointLevel, 0)) : stock;
    }

    /** Called where the game's ballistics read the screen row of the reticle. */
    public static int aimPointY(int stock) {
        return FirstPerson.lookCaptured() ? Math.round(IsoUtils.YToScreenExact(aimPointX, aimPointY, aimPointLevel, 0)) : stock;
    }

    /**
     * Called with the binding the game holds under a name. The escape menu
     * pauses a single player game, and its key page is read and saved through
     * the same lookup, so a paused game is always given the player's own.
     */
    public static Core.KeyBinding binding(Core.KeyBinding stock, String name) {
        Substituted held = substituted.get(name);
        if (held == null || !FirstPerson.active() || GameTime.isGamePaused()) {
            return stock;
        }
        if (held.from != stock) {
            KeySubstitutions.Keys keys = KeySubstitutions.substitute(name, stock.keyValue(), stock.altKey());
            held.from = stock;
            held.to = keys == null ? stock
                    : new Core.KeyBinding(stock.name(), keys.key(), keys.alt(), stock.shift(), stock.ctrl(), stock.alt());
        }
        return held.to;
    }

    /** The inputs as the game was last given them, for the trace. */
    static String describe() {
        return String.format(Locale.ROOT, "aim stock %b filtered %b attack %b sights zoom %.4f fov %.2f",
                stockAim, filteredAim, attackDown, FieldOfView.zoom(), FieldOfView.drawnDegrees());
    }
}
