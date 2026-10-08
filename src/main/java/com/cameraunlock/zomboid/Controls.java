package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import zombie.GameTime;
import zombie.characters.CharacterInputKeyBinding;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.physics.BallisticsController;
import zombie.iso.IsoUtils;
import zombie.iso.Vector2;
import zombie.iso.Vector3;

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
     * A controller's aim is its aim button with its aiming stick pushed, the stick being
     * where the game aims. In first person the stick turns the view and the aim is where
     * the view looks, so the button alone is the aim.
     */
    public boolean aimKey(boolean stock) {
        boolean wasFiltered = filteredAim, wasAttack = attackDown;
        IsoPlayer player = view.player();
        boolean captured = view.firstPerson.lookCaptured() && player != null;
        boolean pad = captured && View.onController(player);
        if (pad) {
            stock = CharacterJoypadButtonBinding.Aim.isDown(player.getJoypadBind());
        }
        stockAim = stock;
        if (captured && player.getVehicle() == null && !togglesAim(player)) {
            attackDown = pad ? CharacterJoypadButtonBinding.Attack.isDown(player.getJoypadBind())
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
     * Where players fight, a character walked sideways or backwards is held in the game's
     * aiming stance, which faces it the way the view does: see Online.bodyFacesTheView. The
     * sights stay down. They follow the player's own aim input alone.
     */
    private boolean turnedToTheView(IsoPlayer player) {
        return player != null && player.getVehicle() == null && view.firstPerson.movingAside() && Online.bodyFacesTheView(player);
    }

    private boolean turnedOnly;

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
        return aim.attack(stock, player.isAimingFirearmEquipped());
    }

    /** Once a frame of the game's logic. */
    void frame() {
        aim.frame();
    }

    /**
     * Whether a firearm's sights are up: the player's own aim input, never the
     * aim an attack click implies, with a firearm in hand and on foot.
     */
    boolean sightsUp(IsoPlayer player) {
        boolean aiming = stockAim || togglesAim(player) && player.isAiming();
        return aiming && view.firstPerson.lookCaptured() && player.getVehicle() == null && player.isAimingFirearmEquipped();
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
     * Called where the game's ballistics have just worked out where the muzzle is and which
     * way it points, which is where they start a shot from and the way they send it when
     * they have found nothing under the reticle, and the way a shotgun's spread is laid.
     *
     * With a firearm's sights up a lean moves the weapon with the eye, and the shot starts
     * from where the weapon is drawn: the muzzle is moved by what of the lean the weapon has.
     *
     * The way it points is the game's own model's barrel, which the first person view does
     * not draw: held low and across the body at the hip, most of a second in coming up to the
     * aim, and some degrees off it when it is up. The weapon the view draws points at the
     * aim point at the hip and with the sights up alike, so that is the way the shot goes.
     */
    public void muzzle(BallisticsController ballistics) {
        IsoPlayer player = view.player();
        if (!view.firstPerson.lookCaptured() || player == null || player.getBallisticsController() != ballistics) {
            return;
        }
        if (FirstPerson.TRACE && filteredAim) {
            sayBallistics(ballistics);
        }
        float[] carried = view.headTracking.carried();
        Vector3 muzzle = ballistics.getMuzzlePosition();
        muzzle.x += carried[0];
        muzzle.y += carried[1];
        muzzle.z += carried[2] / ViewCamera.LEVEL_HEIGHT;
        Vector3 direction = ballistics.getMuzzleDirection();
        direction.x = aimPointX - muzzle.x;
        direction.y = aimPointY - muzzle.y;
        direction.z = aimPointLevel - muzzle.z;
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
            java.util.List<?> hits = (java.util.List<?>) IsoPlayer.class.getMethod("getHitInfoList").invoke(player);
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
