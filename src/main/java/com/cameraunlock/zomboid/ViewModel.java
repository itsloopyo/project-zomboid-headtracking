package com.cameraunlock.zomboid;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Stack;
import org.joml.Vector3f;
import zombie.characters.CharacterTimedActions.BaseAction;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.inventory.InventoryItem;
import zombie.inventory.types.HandWeapon;
import zombie.inventory.types.WeaponType;
import zombie.scripting.objects.ModelAttachment;
import zombie.scripting.objects.ModelScript;

/**
 * The weapon in the viewer's hands as a first person game shows it: low and to the right at
 * the hip, raised to the middle of the picture with the sights up, lagging a turn of the view,
 * swaying with each step, kicking with each shot, and swung across the view when it is
 * something to hit with.
 *
 * Nothing the game simulates is touched. The game goes on posing the character as it does for
 * its own view, and the shot leaves where it always did. Only where the picture draws the
 * weapon and the hands on it changes: they are moved from where the game's animation has
 * them to a place before the eye, and the arms behind the hands are the picture's own, from
 * where a person's shoulders are. Each hand is turned on what it holds to meet its arm.
 *
 * The game's logic thread works out how the weapon is held this frame and the render thread
 * reads the last of it whole.
 */
public final class ViewModel {
    final View view;

    ViewModel(View view) {
        this.view = view;
    }


    /**
     * How the weapon is held this frame.
     *
     * @param item      the game's name for what is held
     * @param itemId    the game's id of the very thing held, which another of the same kind does not share
     * @param firearm   whether it is aimed down its sights, as against swung
     * @param bothHands whether it is held in the left hand too
     * @param attack    how it is being hit with, or was last: one of ViewModelMath's ways
     * @param reached   how far an attack is to landing: 0 with none, 1 as it lands
     * @param past      how far past landing it is: 0 until it lands, up to 1
     * @param struck    whether it landed on something
     * @param aim       0 at the hip, 1 with the sights up
     * @param nearness  one over how far off the aim meets the world, in tiles: 0 with nothing in reach
     * @param lowered   0 held ready, 1 lowered to run
     * @param swayYaw   how far the weapon is behind a turn of the view to the right, radians
     * @param swayPitch the same for a turn upwards
     * @param bobX      where the step has it, to the right, tiles
     * @param bobY      and upwards
     * @param recoil    1 on the frame of a shot, falling to nought: how far the hip crosshair's ticks are thrown apart
     * @param kickPitch how far the last shots have thrown the muzzle up, radians
     * @param kickYaw   and over to the right, radians
     * @param kickRoll  and its top over to the left, radians
     * @param kickUp    how far they have thrown the weapon up, tiles
     * @param kickBack  and back towards the eye, tiles
     * @param dip       1 just after landing or crouching, falling to nought
     * @param skin      which of the game's skins the holder's body wears, 0 the palest
     * @param female    whether the holder is a woman
     * @param action    how the weapon's action is worked and how it is loaded: one of ArmActs' kinds
     * @param cycled    how long ago the shot was that the action is being worked after, in seconds, or less than nought
     * @param doing     what the holder is doing to the weapon besides holding it: one of ArmActs' doings
     * @param done      how long the holder has been doing that, in seconds
     * @param rounds    how many rounds the weapon holds now
     * @param spent     how many of them a revolver has fired and still holds, the game counting them among its rounds until they are thrown out
     * @param round     how far through loading one round the hand is, 0 to 1
     * @param handled   0 held ready, 1 brought up before the eye and tipped over to be loaded
     * @param pace      how long the game is taken to be over what the holder is doing, in seconds
     * @param seated    how long ago the game finished loading a magazine, in seconds, or less than nought
     * @param drawn     1 as a weapon is taken in hand, falling to nought as it comes up into the picture
     * @param worked    1 while the right hand works a bolt or a lever, nought otherwise, eased between
     * @param clip      whether the weapon has a magazine in it
     * @param out       how long ago the magazine left the weapon while it is being loaded or unloaded, in seconds, or less than nought if none has
     * @param fetching  how long a magazine has been on its way to the weapon, in seconds, or less than nought
     * @param fetchTakes how long the game is taken to be over that, in seconds
     * @param leftFree  whether the left hand holds nothing
     * @param swungAs   for something swung, which of MeleeHold's classes the game puts it in
     * @param own       for a firearm, the mod's own model that stands for it, as OwnModel.of said when this was made, or null
     */
    record Held(String item, int itemId, boolean firearm, boolean bothHands, int attack, float reached, float past, boolean struck, float aim, float nearness, float lowered,
            float swayYaw, float swayPitch, float bobX, float bobY, float recoil, float kickPitch, float kickYaw, float kickRoll, float kickUp, float kickBack,
            float dip, int skin, boolean female,
            int action, float cycled, int doing, float done, int rounds, int spent, float round, float handled, float pace, float seated, float drawn, float worked,
            boolean clip, float out, float fetching, float fetchTakes, boolean leftFree, int swungAs, OwnModel own) {
        /**
         * The mod's own model drawn for it: decided once, as this was made, so that where it is
         * held, the hands on it and what is drawn agree on the frame a model comes onto the
         * graphics card. Null where the game's is drawn, and once the model has been given back.
         */
        OwnModel ownDrawn() {
            return own != null && own.onCard ? own : null;
        }
    }

    /** What is held, or null with no weapon in hand, and while the hands are busy with a timed action. */
    volatile Held held;

    /**
     * A shove or a stamp made with nothing in either hand, which the mod's own arms show.
     *
     * @param way     one of BareHands' ways
     * @param reached as Held's
     * @param past    as Held's
     * @param struck  as Held's
     * @param skin    as Held's
     * @param female  as Held's
     */
    record Bare(int way, float reached, float past, boolean struck, int skin, boolean female) {}

    /** The shove or stamp under way with nothing in hand, or null. */
    volatile Bare bare;

    /**
     * A timed action the hands are busy with before the eye.
     *
     * @param kind   the way of working it is: one of TimedActs' kinds
     * @param low    whether it is worked at something on the floor
     * @param time   how long it has gone on, in seconds
     * @param done   how much of it the game has done, 0 to 1: its progress bar
     * @param up     0 with the hands below the picture, 1 up at the work
     * @param anim   the game's name for its animation of it
     * @param skin   which of the game's skins the body wears, 0 the palest
     * @param female whether the body is a woman's
     */
    record Task(int kind, boolean low, float time, float done, float up, String anim, int skin, boolean female) {}

    /** The timed action the hands are busy with, or null. While there is one, nothing is held up. */
    volatile Task task;

    /** The hands come up to a timed action, to within a twentieth, in three of these, and go down from it as fast. */
    private static final float TASK_SECONDS = 0.08f;
    /** Nearer than this to below the picture, hands leaving a timed action are gone. */
    private static final float TASK_GONE = 0.02f;
    private String taskAnim;
    private final HashSet<String> animsSaid = new HashSet<>();
    private int taskWearAsked;
    /** How far up the hands are left when one action follows another of another kind. */
    private static final float TASK_BETWEEN = 0.4f;
    private int taskKind;
    private boolean taskLow;
    private float taskTime, taskUp, taskDone;

    /** The muzzle's attachment in the game's model scripts. Its own z axis runs along the barrel. */
    private static final String MUZZLE = "muzzle";

    /** The crosshair's ticks come back together, to within a twentieth, in three of these: 150 ms. */
    static final float RECOIL_SECONDS = 0.05f;
    /** What a shove with nothing in hand is taken to weigh, for how hard it lands and how long it stops: see HitStop. */
    static final float BARE_WEIGHT = 1.5f;
    /** Shots faster than that do not throw them further apart than this. */
    private static final float RECOIL_MOST = 1.5f;
    /** What a shot does to the weapon. */
    final Kick kick = new Kick();
    private static final float LOWER_SECONDS = 0.12f;
    private static final float SWAY_SECONDS = 0.08f;
    /** How far behind the view the weapon is for each radian a second the view turns. */
    private static final float SWAY_LAG = 0.03f;
    private static final float SWAY_MOST = (float) Math.toRadians(4);
    private static final float BOB_SECONDS = 0.15f;
    /** The speed of a walk, in tiles a second: the bob is whole at it. */
    private static final float WALK_SPEED = 1.6f;
    private static final float BOB_MOST = 1.6f;
    /** How far a step carries, in tiles. */
    private static final float STRIDE = 0.75f;
    /** Further than this in a second is not walking: the character was put somewhere else. */
    private static final float FASTEST = 12f;
    private static final float DIP_FALLS = 0.12f, DIP_SHOWS = 0.04f;

    /** The barrel turns to a new aim point over this long: the point jumps from a doorpost to the far wall behind it. */
    private static final float CONVERGE_SECONDS = 0.1f;
    /** Nearer than this the aim point is taken to be this far off: a muzzle against a wall does not point across the view. */
    private static final float NEAREST_AIM = 1.5f;

    private float nearness;
    private float aim, lowered, swayYaw, swayPitch, bobAmount, bobPhase, recoil, dipTo, dip;
    private final AttackClock swing = new AttackClock();
    private int attack;
    private boolean struck, unshown, stamping;
    private final float[] swingNow = new float[2];
    private int kicks;
    private boolean placed, wasSneaking, wasFalling;
    private float lastYaw, lastPitch, lastX, lastY;
    private final float[] bob = new float[2];
    private static boolean gapSaid;
    private int wearAsked;

    /**
     * A pump action is worked after every shot: the hand on the fore-end draws it back and
     * shoves it forward again. The game's own animation of that is made for its distant view
     * and turns the hand about; here the hand keeps its hold and slides.
     */
    private static final float PUMP_AFTER = 0.16f, PUMP_BACK = 0.14f, PUMP_FORWARD = 0.16f;
    private static final float NOT_PUMPING = -1f;
    private boolean racked;
    private float sinceShot = NOT_PUMPING, cycled = NOT_PUMPING, done;
    /** The way of reloading the weapon last held had, as the game hands it over, and the kind of action that is. */
    private Object reloadTypeSeen;
    private int actionSeen = ArmActs.NO_ACTION;
    private int doing, roundsBefore;
    private float round, handled, seated = ArmActs.NOT_SEATING, sinceRound, roundTakes = ArmActs.ROUND_TAKES;
    private int actionBefore, roundsIn;
    private boolean clipBefore;
    private float out = ArmActs.NOT_SEATING, fetching = ArmActs.NOT_SEATING;
    /** For each kind of action, how long the game last took to bring a magazine to the weapon. */
    private final float[] fetchTakes = new float[9];
    {
        Arrays.fill(fetchTakes, ArmActs.FETCH_TAKES);
    }
    /** For each kind of action and each thing done to a weapon, how long the game last took over it. */
    private final float[][] took = new float[9][];
    {
        for (int kind = 0; kind < took.length; kind++) {
            took[kind] = ArmActs.TAKES.clone();
        }
    }
    private static final float HANDLE_SECONDS = 0.09f;
    /** A weapon is up, to within a twentieth, in three of these. */
    private static final float DRAW_SECONDS = 0.07f;
    private float drawn, worked;
    /** How far under the picture a weapon being put away goes before it is gone. */
    private static final float PUT_AWAY = 0.95f;
    private static final float WORK_SECONDS = 0.07f;
    private InventoryItem inHand;

    private boolean isSet(IsoPlayer player, String variable) {
        return "true".equalsIgnoreCase(player.GetVariable(variable));
    }
    /**
     * Which of MeleeHold's classes something swung is held as: the game's WeaponType, a stone by the animation the game
     * swings it with (the flint nodule and the stone, the only two), and something made for two hands as one-handed
     * things are while it is in one. A spear in one hand is still driven point first. Thrown things and the rest the
     * game names are held as one-handed things are.
     */
    private static int swungAs(HandWeapon weapon, boolean both) {
        if ("Stone".equalsIgnoreCase(weapon.getSwingAnim())) {
            return MeleeHold.STONE;
        }
        return switch (WeaponType.getWeaponType(weapon)) {
            case TWO_HANDED -> both ? MeleeHold.TWO_HANDED : MeleeHold.ONE_HANDED;
            case HEAVY -> both ? MeleeHold.HEAVY : MeleeHold.ONE_HANDED;
            case KNIFE -> MeleeHold.KNIFE;
            case SPEAR -> MeleeHold.SPEAR;
            default -> MeleeHold.ONE_HANDED;
        };
    }

    /** How far back the fore-end hand is, 0 to 1. Written by the logic thread, read where the weapon is placed. */
    volatile float pumped;
    /** How far back the fore-end hand was when the weapon was last placed, which the hands on it are placed for. Render thread. */
    float pumpedPlaced;

    /** How far back the hand is at a time after the shot: still, then back, then forward. */
    static float pumpAt(float seconds) {
        float back = (seconds - PUMP_AFTER) / PUMP_BACK;
        float forward = (seconds - PUMP_AFTER - PUMP_BACK) / PUMP_FORWARD;
        return ViewModelMath.smooth(back) - ViewModelMath.smooth(forward);
    }

    /** Called by the game straight after it starts its own muzzle flash, which is where a firearm's shot is taken. */
    public void shot() {
        if (FirstPerson.TRACE) {
            view.controls.sayShot();
        }
        if (held != null) {
            kicks++;
            view.muzzleFlash.fire();
            if (racked) {
                sinceShot = 0f;
                cycled = 0f;
            }
        }
    }

    /**
     * Works out how the weapon is held this frame. Logic thread.
     *
     * @param yaw      the clean view's yaw, radians
     * @param pitch    the clean view's pitch, radians
     * @param seconds  the length of the frame
     * @param sightsUp whether a firearm's sights are up
     * @param aimedAt  how far off the aim meets the world, in tiles, or infinity
     */
    void update(IsoPlayer player, float yaw, float pitch, float seconds, boolean sightsUp, float aimedAt) {
        if (busy(player, seconds)) {
            return;
        }
        // Seated, a firearm is in view only while its sights are up out of a seat the game lets
        // it be fired from, and is put away under the picture as they come down.
        boolean inSeat = player.getVehicle() != null;
        InventoryItem item = !inSeat || Ride.armedFromSeat(player) && player.isAimingFirearmEquipped() ? player.getPrimaryHandItem() : null;
        boolean away = inSeat && !sightsUp;
        if (inSeat && (!(item instanceof HandWeapon) || away && item != inHand)) {
            rest();
            return;
        }
        if (!(item instanceof HandWeapon weapon)) {
            // An attack under way was made with what was in hand.
            if (inHand != null) {
                unshown = true;
            }
            restHeld();
            bareHands(player, item == null && player.getSecondaryHandItem() == null, seconds);
            return;
        }
        bare = null;
        view.scope.fitted(weapon);
        // What is worn changes seldom, and asking makes a list for every garment.
        if ((wearAsked++ & 31) == 0) {
            Cabins.noteWear(player, view.ride.dress);
        }
        if (!gapSaid) {
            gapSaid = true;
            String gap = Agent.shotGap();
            if (gap != null) {
                Log.info("the held weapon has no recoil and no muzzle flash: " + gap);
            }
        }
        float x = player.getX(), y = player.getY();
        boolean sneaking = player.isSneaking(), falling = player.isFalling();
        // A weapon just taken in hand comes up from under the picture.
        if (item != inHand) {
            inHand = item;
            drawn = 1f;
            // What was being done to the weapon before is not being done to this one: an attack under way was the other's.
            unshown = true;
            sinceShot = NOT_PUMPING;
            cycled = NOT_PUMPING;
            seated = ArmActs.NOT_SEATING;
            out = ArmActs.NOT_SEATING;
            fetching = ArmActs.NOT_SEATING;
            clipBefore = false;
            roundsBefore = Integer.MAX_VALUE;
        }
        drawn = ViewModelMath.ease(drawn, away ? 1f : 0f, seconds, DRAW_SECONDS);
        if (away && drawn > PUT_AWAY) {
            rest();
            return;
        }
        if (!placed) {
            placed = true;
            lastYaw = yaw;
            lastPitch = pitch;
            lastX = x;
            lastY = y;
            wasSneaking = sneaking;
            wasFalling = falling;
        }
        aim = ViewModelMath.ease(aim, sightsUp ? 1f : 0f, seconds, FieldOfView.ZOOM_SECONDS);
        nearness = ViewModelMath.ease(nearness, 1f / Math.max(aimedAt, NEAREST_AIM), seconds, CONVERGE_SECONDS);
        lowered = ViewModelMath.ease(lowered, player.isSprinting() ? 1f : 0f, seconds, LOWER_SECONDS);
        float speed = 0f;
        if (seconds > 1e-4f) {
            float behindYaw = Math.clamp(-ViewModelMath.turned(lastYaw, yaw) / seconds * SWAY_LAG, -SWAY_MOST, SWAY_MOST);
            float behindPitch = Math.clamp(-(pitch - lastPitch) / seconds * SWAY_LAG, -SWAY_MOST, SWAY_MOST);
            swayYaw = ViewModelMath.ease(swayYaw, behindYaw, seconds, SWAY_SECONDS);
            swayPitch = ViewModelMath.ease(swayPitch, behindPitch, seconds, SWAY_SECONDS);
            speed = (float) Math.hypot(x - lastX, y - lastY) / seconds;
            if (speed > FASTEST) {
                speed = 0f;
            }
        }
        bobAmount = ViewModelMath.ease(bobAmount, Math.min(speed / WALK_SPEED, BOB_MOST), seconds, BOB_SECONDS);
        bobPhase = (float) ((bobPhase + speed * seconds / STRIDE * Math.PI) % (2 * Math.PI));
        ViewModelMath.bob(bobPhase, bobAmount, bob);
        recoil = Math.min(ViewModelMath.ease(recoil, 0f, seconds, RECOIL_SECONDS) + kicks, RECOIL_MOST);
        kick.step(seconds);
        if (kicks > 0) {
            float hard = Kick.fired(weapon.getRecoilDelay(), weapon.getMaxDamage()), braced = Kick.braced(weapon.getFullType(), aim);
            for (; kicks > 0; kicks--) {
                kick.fire(hard, braced);
            }
        }
        if (sneaking != wasSneaking || wasFalling && !falling) {
            dipTo = 1f;
        }
        dip = ViewModelMath.ease(dip, dipTo, seconds, DIP_SHOWS);
        dipTo = ViewModelMath.ease(dipTo, 0f, seconds, DIP_FALLS);
        boolean firearm = weapon.isAimedFirearm();
        swing.step(!firearm && GameAttack.progress(player, swingNow), swingNow[0], swingNow[1], seconds);
        if (swing.began) {
            attack = GameAttack.way(player);
            struck = false;
            // A stamp of the foot is not made with what is held.
            stamping = player.isDoStomp();
            unshown = stamping;
        }
        if (swing.landed) {
            struck = player.getLastHitCount() > 0;
            if (!stamping && struck && !unshown) {
                swing.stop(HitStop.seconds(weapon.getActualWeight()));
            }
        }
        float reached = unshown ? 0f : swing.reached(), past = unshown ? 0f : swing.past();
        lastYaw = yaw;
        lastPitch = pitch;
        lastX = x;
        lastY = y;
        wasSneaking = sneaking;
        wasFalling = falling;
        racked = firearm && weapon.isRackAfterShoot();
        if (sinceShot != NOT_PUMPING) {
            sinceShot += seconds;
            if (sinceShot > PUMP_AFTER + PUMP_BACK + PUMP_FORWARD) {
                sinceShot = NOT_PUMPING;
            }
        }
        pumped = sinceShot == NOT_PUMPING ? 0f : pumpAt(sinceShot);
        int action = ArmActs.NO_ACTION;
        if (firearm) {
            // Asked every frame, and naming the kind makes a string each time: it is named again only for another way of reloading.
            Object reloadType = weapon.getWeaponReloadType();
            if (reloadType != reloadTypeSeen) {
                reloadTypeSeen = reloadType;
                actionSeen = ArmActs.kind(String.valueOf(reloadType));
            }
            action = actionSeen;
        }
        if (cycled >= 0f) {
            cycled += seconds;
            if (cycled > ArmActs.CYCLE_LASTS) {
                cycled = NOT_PUMPING;
            }
        }
        boolean magazineFed = action == ArmActs.HANDGUN || action == ArmActs.BOLT;
        boolean clip = weapon.isContainsClip();
        // What the game's own animation of the holder is doing to the weapon, as it tells its animations.
        int now = isSet(player, "isRacking") ? ArmActs.RACKING : isSet(player, "isLoading") ? ArmActs.LOADING : isSet(player, "isUnloading") ? ArmActs.UNLOADING : ArmActs.HOLDING;
        if (now != doing) {
            // How long the game took over what has just ended is how long it is taken to take next time.
            if (doing != ArmActs.HOLDING && done > ArmActs.SHORTEST && done < ArmActs.LONGEST) {
                took[actionBefore][doing] = done;
            }
            // Loading over with a magazine in the weapon: it is pushed home. How long the game took to bring it is how long it is taken to take next time.
            seated = doing == ArmActs.LOADING && magazineFed && clip ? 0f : ArmActs.NOT_SEATING;
            if (seated == 0f && fetching > ArmActs.SHORTEST && fetching < ArmActs.LONGEST) {
                fetchTakes[actionBefore] = fetching;
            }
            doing = now;
            done = 0f;
            // Racked by hand, the action is worked as it is after a shot.
            if (now == ArmActs.RACKING && cycled < 0f) {
                cycled = 0f;
                if (racked) {
                    sinceShot = 0f;
                }
            }
        } else {
            done += seconds;
        }
        actionBefore = action;
        if (magazineFed && (doing == ArmActs.LOADING || doing == ArmActs.UNLOADING)) {
            out = clipBefore && !clip ? 0f : out < 0f ? out : out + seconds;
            if (!clip && doing == ArmActs.LOADING) {
                fetching = fetching < 0f ? 0f : fetching + seconds;
            }
        } else {
            out = ArmActs.NOT_SEATING;
            fetching = ArmActs.NOT_SEATING;
        }
        clipBefore = clip;
        if (seated >= 0f) {
            seated += seconds;
            if (seated > ArmActs.SEATED_FOR) {
                seated = ArmActs.NOT_SEATING;
            }
        }
        // A bolt or a lever is worked with the rifle off the eye: its hand passes where the eye was.
        boolean working = cycled >= 0f && (action == ArmActs.BOLT || action == ArmActs.BOLT_SINGLE || action == ArmActs.LEVER);
        worked = ViewModelMath.ease(worked, working ? 1f : 0f, seconds, WORK_SECONDS);
        // Loaded, or a pistol racked, the weapon is brought up where both hands can be seen at it.
        boolean brought = doing == ArmActs.LOADING || doing == ArmActs.UNLOADING || doing == ArmActs.RACKING && action == ArmActs.HANDGUN || seated >= 0f && seated < ArmActs.SEATED_UP_FOR;
        handled = ViewModelMath.ease(handled, brought ? 1f : 0f, seconds, HANDLE_SECONDS);
        int rounds = weapon.getCurrentAmmoCount();
        // Rounds go in at the game's own pace, which the first two to go in show.
        sinceRound += seconds;
        if (doing != ArmActs.LOADING) {
            sinceRound = 0f;
            roundsIn = 0;
        } else if (rounds > roundsBefore) {
            if (roundsIn++ > 0 && sinceRound > ArmActs.SHORTEST && sinceRound < ArmActs.LONGEST) {
                roundTakes = sinceRound;
            }
            sinceRound = 0f;
        }
        round = ArmActs.round(round, doing == ArmActs.LOADING, rounds > roundsBefore, seconds, roundTakes);
        roundsBefore = rounds;
        // A driver keeps the left hand on the wheel and holds the firearm in the right alone.
        boolean steering = Ride.inDriversSeat(player);
        held = new Held(item.getFullType(), item.getID(), firearm, !steering && item == player.getSecondaryHandItem(),
                attack, reached, past, struck, aim, nearness, lowered, swayYaw, swayPitch, bob[0], bob[1], recoil,
                kick.weaponPitch.at, kick.weaponYaw.at, kick.weaponRoll.at, kick.weaponUp.at, kick.weaponBack.at, dip,
                player.getHumanVisual().getSkinTextureIndex(), player.isFemale(), action, cycled, doing, done, rounds, firearm ? weapon.getSpentRoundCount() : 0, round, handled, took[action][doing], seated, drawn, worked, clip, out, fetching, fetchTakes[action], !steering && player.getSecondaryHandItem() == null, firearm ? MeleeHold.ONE_HANDED : swungAs(weapon, item == player.getSecondaryHandItem()),
                firearm ? OwnModel.of(item.getFullType()) : null);
    }

    /**
     * A frame with nothing in hand that is a weapon. A shove or a stamp still shakes the view and,
     * with both hands empty, is shown by the mod's own arms.
     */
    private void bareHands(IsoPlayer player, boolean empty, float seconds) {
        kick.step(seconds);
        swing.step(GameAttack.progress(player, swingNow), swingNow[0], swingNow[1], seconds);
        if (swing.began) {
            stamping = player.isDoStomp();
            attack = stamping ? BareHands.STAMPED : BareHands.SHOVED;
            struck = false;
            unshown = false;
        }
        if (swing.landed && !unshown) {
            struck = player.getLastHitCount() > 0;
            if (struck) {
                swing.stop(HitStop.seconds(BARE_WEIGHT));
            }
        }
        bare = empty && !unshown && swing.under()
                ? new Bare(attack, swing.reached(), swing.past(), struck, player.getHumanVisual().getSkinTextureIndex(), player.isFemale()) : null;
    }

    /**
     * Follows the timed action the game's animation of the holder says is under way, as it
     * sets PerformingAction for it, and returns whether the hands are busy with one. Reloading a
     * firearm is not one: the weapon's own working shows that. The hands come up as an action
     * starts and go down after it ends; while they are up the weapon is put away, and it comes
     * up again from under the picture once they are down.
     */
    private boolean busy(IsoPlayer player, float seconds) {
        String anim = player.getVehicle() == null && isSet(player, "IsPerformingAnAction") && !isSet(player, "isLoading")
                && !isSet(player, "isUnloading") && !isSet(player, "isRacking") ? player.GetVariable("PerformingAction") : null;
        int kind = anim == null ? TimedActs.NONE : TimedActs.kind(anim);
        if (kind != TimedActs.NONE) {
            if (!anim.equals(taskAnim)) {
                // One action straight after another: the hands drop part of the way and come up to the next.
                if (taskAnim != null && kind != taskKind) {
                    taskUp = Math.min(taskUp, TASK_BETWEEN);
                }
                if (animsSaid.add(anim)) {
                    Log.info("hands at a timed action, first time this session: " + anim + " (way of working " + kind + (TimedActs.low(anim) ? ", on the floor" : "") + ")");
                }
                taskAnim = anim;
                taskKind = kind;
                taskLow = TimedActs.low(anim);
                taskTime = 0f;
            } else {
                taskTime += seconds;
            }
            taskUp = ViewModelMath.ease(taskUp, 1f, seconds, TASK_SECONDS);
            Stack<BaseAction> actions = player.getCharacterActions();
            taskDone = actions.isEmpty() ? 0f : actions.get(0).getJobDelta();
        } else if (taskUp > 0f) {
            taskTime += seconds;
            taskUp = ViewModelMath.ease(taskUp, 0f, seconds, TASK_SECONDS);
            if (taskUp < TASK_GONE) {
                taskUp = 0f;
                taskAnim = null;
            }
        }
        if (taskUp == 0f) {
            task = null;
            return false;
        }
        putAway();
        // What the arms wear changes seldom, and asking makes a list for every garment.
        if ((taskWearAsked++ & 31) == 0) {
            Cabins.noteWear(player, view.ride.dress);
        }
        task = new Task(taskKind, taskLow, taskTime, taskDone, taskUp, taskAnim, player.getHumanVisual().getSkinTextureIndex(), player.isFemale());
        return true;
    }

    /** Nothing is held up, no attack is under way and the hands are at nothing: the next weapon starts from the hip, still. */
    void rest() {
        putAway();
        task = null;
        taskAnim = null;
        taskUp = 0f;
    }

    /** Nothing is held up and no attack is under way. */
    private void putAway() {
        restHeld();
        kick.rest();
        swing.reset();
        bare = null;
    }

    /** Nothing is held up: the next weapon starts from the hip, still. */
    private void restHeld() {
        view.scope.fitted(null);
        wearAsked = 0;
        sinceShot = NOT_PUMPING;
        cycled = NOT_PUMPING;
        doing = ArmActs.HOLDING;
        done = 0f;
        round = 0f;
        handled = 0f;
        seated = ArmActs.NOT_SEATING;
        out = ArmActs.NOT_SEATING;
        fetching = ArmActs.NOT_SEATING;
        clipBefore = false;
        inHand = null;
        drawn = 0f;
        worked = 0f;
        roundsBefore = Integer.MAX_VALUE;
        pumped = 0f;
        held = null;
        placed = false;
        aim = 0f;
        nearness = 0f;
        lowered = 0f;
        swayYaw = 0f;
        swayPitch = 0f;
        bobAmount = 0f;
        recoil = 0f;
        dipTo = 0f;
        dip = 0f;
        kicks = 0;
    }

    /** For the trace. */
    String describe() {
        Held now = held;
        Task doing = task;
        if (doing != null) {
            return String.format(Locale.ROOT, "hands at %s (kind %d%s) for %.2f s, %.2f done, up %.2f, %s", doing.anim(), doing.kind(), doing.low() ? ", low" : "",
                    doing.time(), doing.done(), doing.up(), view.characters.describeArms());
        }
        Bare empty = bare;
        if (now == null && empty != null) {
            return String.format(Locale.ROOT, "held nothing, bare way %d reached %.2f past %.2f struck %b, %s", empty.way, empty.reached, empty.past, empty.struck, view.characters.describeArms());
        }
        return now == null ? "held nothing" : String.format(Locale.ROOT, "held %s%s%s aim %.2f recoil %.2f bob phase %.2f at (%.3f, %.3f) lowered %.2f attack %d reached %.2f past %.2f struck %b, action %d cycled %.2f doing %d for %.2f of %.2f rounds %d round %.2f seated %.2f handled %.2f worked %.2f drawn %.2f clip %b out %.2f fetching %.2f of %.2f, %s",
                now.item, now.firearm ? " (firearm)" : "", now.bothHands ? " in both hands" : "", now.aim, now.recoil, bobPhase, now.bobX, now.bobY,
                now.lowered, now.attack, now.reached, now.past, now.struck, now.action, now.cycled, now.doing, now.done, now.pace, now.rounds, now.round, now.seated, now.handled, now.worked, now.drawn, now.clip, now.out, now.fetching, now.fetchTakes, view.characters.describeArms());
    }

    /** How far apart the hip crosshair's ticks stand: 0 standing still, more moving and just after a shot. */
    float spread() {
        Held now = held;
        return now == null ? 0f : Math.min(bobAmount, 1f) * 0.6f + Math.min(now.recoil, 1f);
    }

    // What follows is the render thread's.

    /**
     * Where a person's shoulders are from the eye, in the view's axes, which are
     * ViewModelMath's: x to the right, y up, z towards the viewer, in tiles. Below the eye, to
     * either side of it and a little behind it.
     */
    static final float[] RIGHT_SHOULDER = {0.34f, -0.62f, 0.16f}, LEFT_SHOULDER = {-0.34f, -0.62f, 0.16f};
    /**
     * The way each elbow drops. The right: down, and half as far out from the body, so the
     * forearm comes up to the grip from below and behind it. The left hand is out on the
     * fore-end, across the body: an elbow dropped straight down from there sits under the
     * hand, and the forearm is seen end on, a dome in the middle of the picture. It goes
     * out to the side.
     */
    static final float[] RIGHT_ELBOW = {0.5f, -1f, 0f}, LEFT_ELBOW = {-1f, -0.6f, 0f};

    /**
     * Where a firearm is held, in tiles from the eye: the line of its barrel to the right and
     * up, and its grip ahead. At the hip, and with the sights up, where the barrel is under the
     * middle of the picture by about the height of the sights above it.
     */
    private static final float HIP_X = 0.13f, HIP_Y = -0.14f;
    static final float HIP_AHEAD = 0.27f;
    /**
     * The same for a long gun in the mod's own hands, whose arms are the length arms are. The
     * hand under its fore-end is its arm's length from a shoulder beside the eye, and no
     * further, so the gun is held nearer: its butt stays by the shoulder it is fired from, and
     * its grip is as far ahead as it is with the sights up, and never nearer than HIP_NEAREST
     * nor further than HIP_AHEAD.
     */
    static final float HIP_LONG_X = 0.09f, HIP_LONG_Y = -0.12f, HIP_NEAREST = 0.19f;
    private static final float AIMED_X = 0f, AIMED_Y = -0.06f;
    static final float AIMED_AHEAD = 0.36f;
    /** At the hip a firearm is tipped over towards the middle. */
    private static final float HIP_CANT = (float) Math.toRadians(8);
    /**
     * How far a lean along the aim may take the eye towards the weapon while the sights are
     * up: to the rear sight, which is over the grip, less the near plane the weapon is drawn
     * with and a margin.
     */
    static final float FORWARD_STOP = AIMED_AHEAD - 0.08f - 0.02f;

    /**
     * The same for what is held now. The mod's own shotgun has no rear sight: with the sights
     * up the eye is over its comb and looks along the top of its rib, and leaning in carries
     * it on over the receiver, with nothing of the gun in its way, as far as the lean goes.
     * Behind a scope the eye is stopped short of the eyepiece's glass.
     */
    float forwardStop() {
        Held now = held;
        if (now != null && now.firearm && view.scope.fitted() != null) {
            return view.scope.forwardStop();
        }
        // The rifle's rear sight is on its barrel, further off than a lean reaches.
        return now != null && now.ownDrawn() != null ? Float.POSITIVE_INFINITY : FORWARD_STOP;
    }
    /**
     * Where a handgun and a long gun are held to be loaded: the line of the barrel to the right
     * and up and the grip ahead, in tiles from the eye, then how far the muzzle is raised and
     * swung to the right and the top tipped to the left, in radians.
     */
    static final float[] HANDLED_PISTOL = {0.06f, -0.06f, 0.34f, (float) Math.toRadians(24), (float) Math.toRadians(-14), (float) Math.toRadians(-34)};
    static final float[] HANDLED_LONG = {0.10f, -0.04f, 0.30f, (float) Math.toRadians(24), (float) Math.toRadians(-16), (float) Math.toRadians(-42)};
    /** Between the hip and the sights the weapon goes by way of a dip and a reach forward, at the most this far off the straight line. */
    private static final float RAISE_DIPS = 0.025f, RAISE_REACHES = 0.03f;
    /** Lowered to run: the muzzle down and across the body. */
    private static final float LOWERED_PITCH = (float) Math.toRadians(-35), LOWERED_YAW = (float) Math.toRadians(-25), LOWERED_DOWN = 0.07f;
    private static final float DIP_DOWN = 0.04f;
    /** How much of the way from the sights back to the hip a rifle comes while its bolt is worked. */
    private static final float WORKED_LOWERS = 0.7f;
    /** Where a weapon just taken in hand comes up from: this far under where it is held, in tiles, its muzzle this far down, in radians. */
    private static final float DRAWN_DOWN = 0.3f, DRAWN_PITCH = (float) Math.toRadians(-38);
    /** With the sights up the step moves the weapon this much of what it does at the hip. */
    private static final float BOB_AIMED = 0.3f;
    /** Along a firearm's mesh the barrel is y and its top is z; what is swung is taken to be made the same way. */
    private static final float[] MESH_ALONG = {0f, 1f, 0f}, MESH_TOP = {0f, 0f, 1f};

    private final float[] origin = new float[3], forward = new float[3], top = new float[3], end = new float[3];
    private final float[] along = new float[3], grip = new float[3], swung = new float[ViewModelMath.HOLD], sighted = new float[2];
    /** How far the body is turned to the right behind the weapon last placed, in radians. */
    float turned;
    /**
     * Where the left palm is held on a firearm: this far from the grip to the muzzle, and this
     * far under that line, in tiles. The game's animation moves the left hand about against
     * the weapon as the character turns, steps and settles, and an arm drawn to it there goes
     * wherever it goes.
     */
    private static final float FORE_END = 0.42f, FORE_END_BELOW = 0.035f;
    /** How far working the action tips the muzzle down, and draws the weapon back, in tiles, with the fore-end right back. */
    private static final float PUMP_DIPS = (float) Math.toRadians(3), PUMP_PULLS = 0.012f;
    /** How much of the way from grip to muzzle the fore-end travels. */
    static final float PUMP_TRAVEL = 0.12f;
    /**
     * Where the game's left hand is held on the weapon last placed, in the view: only with a muzzle. The mod's own hands are
     * placed by the weapon's own shape (ArmHolds).
     */
    final float[] foreEnd = new float[3];
    /** The matrix `place` last made with the weapon held still: without the kick of a shot, the sway and the step, the dip of a landing and a pump's stroke. */
    final float[] still = new float[16];
    /** The way the top of the weapon last placed faces in the view and the way it points, each of length one, and where its grip is. */
    final float[] over = new float[3], pointing = new float[3], gripSeen = new float[3];
    /** Where the muzzle of the weapon last placed is in the view, and whether it has one. */
    final float[] muzzle = new float[3];
    boolean hasMuzzle;
    /** Whether the weapon last placed is a firearm held by one hand's grip with its muzzle just ahead of it. */
    boolean handgun;
    private final HashSet<String> noMuzzleSaid = new HashSet<>();
    private final float[] meshTop = new float[3], meshAcross = new float[3];
    /** How what is swung is held, worked out once for each weapon taken in hand, and the one placed last, or null. */
    private MeleeHold.Fit fit, swungFit;
    /** Where the left fist is on what is swung, placed last, in both hands: ArmHolds.Weapon's `second`. */
    float secondFist = -ArmHolds.LOWER_FIST;
    /** Whether what was placed last is a spear: ArmHolds.Weapon's `spear`. */
    boolean spearHeld;
    /** A firearm whose muzzle is nearer its grip than this, in the game's units, is a handgun. */
    private static final float HANDGUN_WITHIN = 0.25f;
    /**
     * How large a handgun is drawn in the mod's own hands against how the game makes it. The
     * game's pistol has its muzzle 0.147 of its units from its grip and its pump gun 0.446,
     * which on guns the size guns are is about 0.16 m and 0.9 m.
     */
    static final float HANDGUN_FITS = 0.6f;

    /**
     * The matrix that draws the viewer's model with the weapon held before the eye. The weapon
     * is held by its pose of this frame, so nothing of the game's own animation of it against
     * the body shows: its swing is the picture's.
     *
     * @param weapon     where the game has the weapon, in its character's own axes: the sixteen
     *                   floats of the part's transform, read by rows
     * @param finalScale the scale the game draws the character's parts at
     * @param instance   the weapon's model
     * @param scale      the model's units to tiles
     * @param trueHands  whether the hands on the weapon are the mod's own, which are the size hands are
     * @param hand       the middle of the game's right hand, as `weapon` is given, for what is swung, or null: what is swung in
     *                   the game's own hands, or whose mesh says nothing, is held where that hand has it along the handle
     * @param swungShape what the mesh of something swung says of it, or null for a firearm and while there is nothing to say
     * @param fromAim    the matrix from the aim's axes to those of the view the frame is drawn
     *                   from: the weapon is held before the aim's eye, not the picture's
     * @param out        the matrix, from the model's axes to the view's
     * @return false while the game has no pose for the weapon
     */
    boolean place(float[] weapon, float finalScale, ModelInstance instance, Held how, float scale, boolean trueHands, float[] hand, MeleeHold.Shape swungShape,
            float[] fromAim, float[] out) {
        // Read once: the logic thread may write it again while the weapon is placed.
        float pumped = this.pumped;
        pumpedPlaced = pumped;
        origin[0] = weapon[3] * finalScale;
        origin[1] = weapon[7] * finalScale;
        origin[2] = weapon[11] * finalScale;
        ModelScript script = instance.modelScript;
        ModelAttachment barrel = how.firearm && script != null ? script.getAttachmentById(MUZZLE) : null;
        hasMuzzle = barrel != null;
        if (how.firearm && !hasMuzzle && noMuzzleSaid.add(how.item)) {
            Log.info("the model of " + how.item + " has no attachment called " + MUZZLE + ": the game's own arms hold it and its shots have no flash");
        }
        handgun = hasMuzzle && Math.hypot(barrel.getOffset().x, Math.hypot(barrel.getOffset().y, barrel.getOffset().z)) < HANDGUN_WITHIN;
        if (hasMuzzle) {
            Vector3f offset = barrel.getOffset(), rotate = barrel.getRotate();
            // The attachment is turned about x, then y, then z, and its own z is the barrel:
            // only the first two move that axis.
            double aboutX = Math.toRadians(rotate.x), aboutY = Math.toRadians(rotate.y);
            along[0] = (float) Math.sin(aboutY);
            along[1] = (float) (-Math.sin(aboutX) * Math.cos(aboutY));
            along[2] = (float) (Math.cos(aboutX) * Math.cos(aboutY));
            turned(weapon, along, forward);
            end[0] = (weapon[0] * offset.x + weapon[1] * offset.y + weapon[2] * offset.z + weapon[3]) * finalScale;
            end[1] = (weapon[4] * offset.x + weapon[5] * offset.y + weapon[6] * offset.z + weapon[7]) * finalScale;
            end[2] = (weapon[8] * offset.x + weapon[9] * offset.y + weapon[10] * offset.z + weapon[11]) * finalScale;
        } else {
            // Something swung runs along its mesh's long axis, which is y but for a mesh made along another.
            turned(weapon, how.firearm || swungShape == null ? MESH_ALONG : swungShape.along(), forward);
        }
        turned(weapon, MESH_TOP, top);
        int way = MeleeHold.way(how.swungAs, how.attack);
        float share = MeleeHold.share(how.reached, how.past, how.struck);
        swungFit = null;
        if (!how.firearm && swungShape != null) {
            // Its mesh's units in metres and in tiles, as the game draws the model: its script's scale is in the matrix.
            float unit = (float) Math.sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2]);
            float perUnit = unit / Characters.PER_METRE, tilesPerUnit = unit * finalScale * scale;
            // The picture's height as the player set it: what is held at rest is fitted inside it.
            float tan = (float) Math.tan(Math.toRadians(FieldOfView.degrees()) / 2);
            if (fit == null || !fit.fits(how.swungAs, swungShape, perUnit, tilesPerUnit, trueHands, tan)) {
                fit = MeleeHold.fit(how.swungAs, swungShape, perUnit, tilesPerUnit, trueHands, tan);
            }
            swungFit = fit;
            // It is turned about its handle so that its striking side leads the blow.
            MeleeHold.top(swungShape, way, share, meshTop);
            swungShape.across(meshTop[0], meshTop[2], meshAcross);
            turned(weapon, meshAcross, top);
        }
        if (swungFit != null && trueHands) {
            // In the mod's own hands it is held where its mesh says the hand goes on its handle.
            for (int axis = 0; axis < 3; axis++) {
                origin[axis] += forward[axis] * swungFit.hand() * finalScale;
            }
        } else if (hand != null) {
            // In the game's hands, or with a mesh that says nothing, it is held where the game's hand has it, which those hands are on.
            float long2 = forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2];
            float up = ((hand[0] - origin[0]) * forward[0] + (hand[1] - origin[1]) * forward[1] + (hand[2] - origin[2]) * forward[2]) / long2;
            for (int axis = 0; axis < 3; axis++) {
                origin[axis] += forward[axis] * up;
            }
        }
        // The bore is over the hand that holds it. The long guns' meshes have it on the side of their z and the
        // handguns' on the other, where their sights are fixed too: held by z a pistol is upside down.
        if (hasMuzzle && barrel.getOffset().z < 0f) {
            for (int axis = 0; axis < 3; axis++) {
                top[axis] = -top[axis];
            }
        }

        // The mod's own model has its sights where it was made with them: the eye is brought to
        // their line, which is that far over the bore at the size the model is drawn.
        OwnModel own = hasMuzzle ? how.ownDrawn() : null;
        // The game's handguns are made for its own broad hands, nearly twice the size a handgun
        // is beside its long guns. In hands the size hands are they are drawn the size they would be.
        float fits = trueHands && handgun ? HANDGUN_FITS : 1f;
        scale *= fits;
        float aimedY = AIMED_Y * fits, aimedAhead = AIMED_AHEAD;
        if (own != null) {
            float long3 = (float) Math.sqrt((end[0] - origin[0]) * (end[0] - origin[0]) + (end[1] - origin[1]) * (end[1] - origin[1])
                    + (end[2] - origin[2]) * (end[2] - origin[2]));
            aimedY = -own.sightLine * long3 * scale / own.muzzleFar;
            aimedAhead = own.eyeBehind * long3 * scale / own.muzzleFar;
        }
        // Behind a scope the eye is on the scope's axis, the eye relief behind its eyepiece.
        if (hasMuzzle && view.scope.sighted(weapon, finalScale, origin, forward, top, end, scale, sighted)) {
            aimedY = sighted[0];
            aimedAhead = sighted[1];
        }
        float atX, atY, ahead;
        float coming = trueHands ? how.drawn : 0f;
        // What shakes the weapon, apart from where it is held: the hand under its fore-end is placed on it as it is without these.
        float shakenPitch = how.swayPitch + how.kickPitch;
        float pitch = shakenPitch + how.lowered * LOWERED_PITCH + coming * DRAWN_PITCH;
        float yaw = how.swayYaw + how.lowered * LOWERED_YAW;
        float roll;
        float moveX = 0f, moveY = 0f, moveBack = 0f;
        turned = 0f;
        if (how.firearm) {
            // With the sights up the rifle comes most of the way down while its bolt is worked, and back up after.
            float raised = ViewModelMath.smooth(how.aim) * (1f - (trueHands ? how.worked * WORKED_LOWERS : 0f)), off = ViewModelMath.arc(raised);
            boolean nearer = trueHands && hasMuzzle && !handgun;
            float hipX = nearer ? HIP_LONG_X : HIP_X, hipY = nearer ? HIP_LONG_Y : HIP_Y;
            float hipAhead = nearer ? hipAhead(aimedAhead) : HIP_AHEAD;
            atX = hipX + (AIMED_X - hipX) * raised;
            atY = hipY + (aimedY - hipY) * raised - RAISE_DIPS * off;
            // Arms the length arms are do not reach a fore-end pushed further out on its way up.
            ahead = hipAhead + (aimedAhead - hipAhead) * raised + (nearer ? 0f : RAISE_REACHES) * off;
            // With the sights up the line of the sights runs along the aim. Off that place the
            // barrel is turned to point at where the aim meets the world, so a weapon at the
            // hip points at the reticle and not past it.
            yaw -= (float) Math.atan((atX - AIMED_X) * how.nearness);
            pitch -= (float) Math.atan((atY - aimedY) * how.nearness);
            roll = HIP_CANT * (1f - raised);
            // Brought up to be loaded: before the eye, its muzzle up and across and its top tipped
            // over to the right, which turns the well and the port towards the hand that fills them.
            float brought = trueHands ? ViewModelMath.smooth(how.handled) : 0f;
            float[] to = handgun ? HANDLED_PISTOL : HANDLED_LONG;
            atX += (to[0] - atX) * brought;
            atY += (to[1] - atY) * brought;
            ahead += (to[2] - ahead) * brought;
            pitch += to[3] * brought;
            yaw += to[4] * brought;
            roll += (to[5] - roll) * brought;
            // Working the action drags the muzzle down and the whole gun back a little, and lets it go again.
            pitch -= pumped * PUMP_DIPS;
            shakenPitch -= pumped * PUMP_DIPS;
            moveBack = pumped * PUMP_PULLS;
        } else {
            spearHeld = how.swungAs == MeleeHold.SPEAR;
            if (swungFit != null) {
                secondFist = swungFit.second();
                ViewModelMath.attack(way, how.reached, how.past, how.struck, swungFit.rest(), swung);
                // Something longer than a bat is swung so that its head lands on what it hits.
                swung[1] += swungFit.aimed()[way] * share;
            } else {
                secondFist = -ArmHolds.LOWER_FIST;
                ViewModelMath.attack(way, how.reached, how.past, how.struck, trueHands ? MeleeHold.UPRIGHT_HELD_IN_HAND : MeleeHold.UPRIGHT_HELD, swung);
            }
            pitch += swung[0];
            yaw += swung[1];
            roll = swung[2];
            atX = swung[3];
            atY = swung[4];
            ahead = swung[5];
            turned = swung[6];
        }
        if (!ViewModelMath.hold(origin, forward, top, hasMuzzle ? end : null, scale, atX, atY, ahead, out)) {
            return false;
        }
        float steady = 1f - (1f - BOB_AIMED) * how.aim;
        // The grip is where the weapon turns about: it is the hand.
        float gripX = atX, gripY = atY;
        if (hasMuzzle) {
            ViewModelMath.place(out, origin[0], origin[1], origin[2], grip);
            gripX = grip[0];
            gripY = grip[1];
        }
        if (hasMuzzle) {
            System.arraycopy(out, 0, still, 0, 16);
            ViewModelMath.turn(still, pitch - shakenPitch, yaw - how.swayYaw, roll, gripX, gripY, -ahead, 0f, -how.lowered * LOWERED_DOWN - coming * DRAWN_DOWN, 0f);
            ViewModelMath.onto(fromAim, still);
        }
        ViewModelMath.turn(out, pitch, yaw + how.kickYaw, roll + how.kickRoll, gripX, gripY, -ahead,
                moveX + how.bobX * steady, moveY + how.bobY * steady + how.kickUp - how.lowered * LOWERED_DOWN - how.dip * DIP_DOWN - coming * DRAWN_DOWN,
                moveBack + how.kickBack);
        ViewModelMath.onto(fromAim, out);
        ViewModelMath.turned(out, top, over);
        ViewModelMath.turned(out, forward, pointing);
        float tall = (float) Math.sqrt(over[0] * over[0] + over[1] * over[1] + over[2] * over[2]);
        float far = (float) Math.sqrt(pointing[0] * pointing[0] + pointing[1] * pointing[1] + pointing[2] * pointing[2]);
        for (int axis = 0; axis < 3; axis++) {
            over[axis] /= tall;
            pointing[axis] /= far;
        }
        ViewModelMath.place(out, origin[0], origin[1], origin[2], gripSeen);
        if (hasMuzzle) {
            ViewModelMath.place(out, end[0], end[1], end[2], muzzle);
            // The mod's own pump gun says where its fore-end is and how far it slides.
            boolean pump = own != null && own.pump;
            float rests = pump ? own.foreEnd : FORE_END, along = rests - (pump ? own.foreEndTravel : PUMP_TRAVEL) * pumped;
            ViewModelMath.place(out, origin[0] + (end[0] - origin[0]) * along, origin[1] + (end[1] - origin[1]) * along,
                    origin[2] + (end[2] - origin[2]) * along, foreEnd);
            for (int axis = 0; axis < 3; axis++) {
                // The hand on the mod's own model is laid round the fore-end, by its middle.
                if (!pump) {
                    foreEnd[axis] -= over[axis] * FORE_END_BELOW;
                }
            }
        }
        return true;
    }

    /** How far ahead of the eye the grip of a long gun in the mod's own hands is at the hip, whose grip is `aimedAhead` ahead with the sights up, in tiles. */
    static float hipAhead(float aimedAhead) {
        return Math.clamp(aimedAhead, HIP_NEAREST, HIP_AHEAD);
    }

    /** A direction of the weapon's own, in its character's axes. */
    private void turned(float[] weapon, float[] direction, float[] out) {
        out[0] = weapon[0] * direction[0] + weapon[1] * direction[1] + weapon[2] * direction[2];
        out[1] = weapon[4] * direction[0] + weapon[5] * direction[1] + weapon[6] * direction[2];
        out[2] = weapon[8] * direction[0] + weapon[9] * direction[1] + weapon[10] * direction[2];
    }
}
