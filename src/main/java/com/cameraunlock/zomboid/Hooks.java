package com.cameraunlock.zomboid;

import zombie.iso.LosUtil;
import zombie.iso.IsoChunk;
import zombie.core.textures.Texture;
import zombie.iso.objects.IsoDeadBody;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.DeadBodyAtlas;
import fmod.fmod.FMODSoundEmitter;
import fmod.javafmod;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Stack;
import java.util.function.Supplier;
import zombie.CombatManager;
import zombie.Lua.LuaEventManager;
import zombie.EffectsManager;
import zombie.GameTime;
import zombie.characters.CharacterDiedListener;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.component.CharacterInputComponent;
import zombie.chat.ChatElement;
import zombie.combat.CombatConfigKey;
import zombie.core.SpriteRenderer;
import zombie.core.opengl.RenderSettings;
import zombie.core.opengl.RenderThread;
import zombie.core.physics.BallisticsController;
import zombie.core.physics.RagdollBodyPart;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstanceRenderData;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.input.AimingReticle;
import zombie.inventory.types.HandWeapon;
import zombie.iso.IsoGridSquare;
import zombie.iso.IsoLightSource;
import zombie.iso.IsoMetaGrid;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObject;
import zombie.iso.IsoObjectPicker;
import zombie.iso.IsoWorld;
import zombie.iso.Vector2;
import zombie.iso.Vector3;
import zombie.iso.weather.ThunderStorm;
import zombie.ui.TextDrawObject;
import zombie.ui.TextManager;
import zombie.ui.UIFont;
import zombie.vehicles.BaseVehicle;

/**
 * Where the game's patched code arrives: see Agent for which call of the game's reaches which
 * of these. Each hands what it was given to the view of the player it is for, and where that
 * player has no view answers as the game would have without the mod.
 *
 * Whose a call is, is told four ways. On the thread that draws, the game says whose part of
 * the screen it is drawing (SpriteRenderer.getRenderingPlayerIndex). On its logic thread there are three. While the game draws its world it goes through its
 * players one by one and names the one it is on (IsoPlayer.getInstance), and the hooks under
 * that drawing go by that. While it moves its players the call is made on something of one
 * player's, the player itself or its input or its ballistics, and that says whose. And the
 * mouse and the keyboard are the first player's.
 */
public final class Hooks {
    private Hooks() {}

    /** The view of the player the game is drawing the world for. Logic thread, under the game's own drawing. */
    private static View drawing() {
        IsoPlayer player = IsoPlayer.getInstance();
        return player == null ? null : View.of(player.getIndex());
    }

    /** The number of the player the game last drew the world for. */
    private static int lastDrawn;

    /** Called from IngameState.renderFrameInternal straight after the game has drawn a player's world. */
    public static void afterWorldRender() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) {
            return;
        }
        int index = player.getIndex();
        // The game draws its players in the order of their numbers, leaving out any that are gone or asleep.
        boolean first = index <= lastDrawn;
        lastDrawn = index;
        int pressed = Tracking.pressedFor(index, first);
        if (first && everyoneSleeps()) {
            // No frame comes while they sleep, and the keys pressed through the night are not all acted on at once as they wake.
            Tracking.dropTheNextPresses();
        }
        if (first) {
            Stall.beat();
            SoundMix.frame();
            for (int i = 0; i < View.COUNT; i++) {
                View other = View.of(i);
                if (other == null) {
                    continue;
                }
                if (other.player() == null) {
                    other.firstPerson.playerGone();
                } else if (other.player().isAsleep()) {
                    // Not drawn while asleep, so its frames are not the picture's: the players awake count them.
                    Quality.rest(i);
                }
            }
        }
        if (index < View.COUNT) {
            if (index > 0) {
                Tracking.listenFor(index);
            }
            View.join(index).firstPerson.afterWorldRender(pressed);
        }
    }

    /** Called from IngameState.renderFrameTextInternal straight after the game has written what people said over a player's picture. */
    public static void afterWorldText() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) {
            return;
        }
        View view = View.of(player.getIndex());
        if (view != null) {
            view.firstPerson.afterWorldText(player.getIndex());
        }
    }

    /** Whether every player at the screen is asleep: one awake still has their frames, and their keys. */
    private static boolean everyoneSleeps() {
        for (int i = 0; i < IsoPlayer.numPlayers; i++) {
            IsoPlayer each = IsoPlayer.players[i];
            if (each != null && !each.isAsleep()) {
                return false;
            }
        }
        return true;
    }

    /** Called when the game has loaded its Lua: at its start, and each time it loads it again. */
    public static void luaLoaded() {
        try {
            OptionsPage.register();
        } catch (RuntimeException failure) {
            // Thrown on, it would stop the game inside its own loading of its Lua.
            Log.error("the mod's settings are not on the game's options screen", failure);
        }
    }

    /**
     * Called on each of the loading screen's frames from the one the save's world is loaded on to
     * the click that begins it, with whether the game is still reading the map round the player.
     * It is returned as still reading while the mod's shader programs are being compiled, so that
     * the click that begins the save waits for them and none is compiled in play, and while the
     * far land round the players or the skins read ahead are still coming (FarLand.holdsLoading,
     * FineSkins.holdsLoading), each for no longer than one wait from when the map was read.
     */
    public static boolean worldLoaded(boolean streaming) {
        boolean shaders = false, content = false;
        for (int i = 0; i < View.COUNT; i++) {
            IsoPlayer player = IsoPlayer.players[i];
            if (player != null) {
                View view = View.join(i);
                shaders |= view.firstPerson.whileLoading(player);
                content |= view.pipeline.unprepared() || view.pipeline.contentWaited();
            }
        }
        if (!streaming) {
            try {
                FineSkins.rank();
            } catch (RuntimeException failure) {
                // Thrown on, it would stop the game's loading screen.
                Log.error("the skins the characters about the player wear could not be found, and none are read ahead", failure);
            }
            RenderThread.queueInvokeOnRenderContext(SKINS_WHILE_LOADING);
        }
        if (shaders != shadersHeld) {
            shadersHeld = shaders;
            Log.info(shaders ? "the save is not begun until the mod's shaders are compiled" : "the save's loading screen no longer waits for the mod's shaders");
        }
        if (streaming) {
            mapReadAt = 0;
            FarLand.loadingAfresh();
        } else if (mapReadAt == 0) {
            mapReadAt = System.nanoTime();
        }
        loadingHeld = shaders | (!streaming && (FarLand.holdsLoading(mapReadAt, mapReadAt + LOADING_WAIT_NANOS) | FineSkins.holdsLoading(content, mapReadAt + LOADING_WAIT_NANOS)));
        return streaming || loadingHeld;
    }

    private static boolean loadingHeld, shadersHeld;
    /** When the game had read the map round the players on this loading screen, 0 while it reads. */
    private static long mapReadAt;
    /** The longest the save's start waits, once the map is read, for all that the mod readies on the loading screen but its shaders. */
    private static final long LOADING_WAIT_NANOS = FarLand.waitNanos(45_000L);
    private static final Runnable SKINS_WHILE_LOADING = FineSkins::whileLoading;

    /**
     * In place of the loading screen's done where it decides whether to say "Click to Start": not
     * while the shaders are compiling, when the click is not taken. The game's update, which
     * runs worldLoaded, comes before its render in each frame.
     */
    public static boolean clickToStartShown(boolean done) {
        return done && !loadingHeld;
    }

    /**
     * What the save's start waits for, each a line while it goes on, or null once it is done. Only
     * the first still going on is written, so no line is seen to drop into the place of one that ended.
     */
    private static final List<Supplier<String>> LOADING_LINES = List.of(Pipeline::shaderProgress, FarLand::loadingProgress, FineSkins::progress);

    /** Called from GameLoadingState.render after the game's quick tip: says how far what the save's start waits for has come, while it goes on. */
    public static void loadingText() {
        for (Supplier<String> line : LOADING_LINES) {
            String progress = line.get();
            if (progress != null) {
                zombie.core.Core screen = zombie.core.Core.getInstance();
                // Where and as the game writes "Click to Start", which clickToStartShown holds back meanwhile.
                double y = screen.getScreenHeight() - 40 - TextManager.instance.getFontHeight(UIFont.NewSmall) - 5;
                TextManager.instance.DrawStringCentre(UIFont.NewLarge, screen.getScreenWidth() / 2.0, y, progress, 1.0, 1.0, 1.0, 1.0);
                return;
            }
        }
    }

    /** Called as the game begins to leave a save for its menu, or to quit, before it saves. */
    public static void leavingSave() {
        Stall.leaving();
        Rewind.leaving();
    }

    /** Called as the game leaves a save for its menu. */
    public static void leftSave(Object state) {
        Tracking.leftSave();
        mapReadAt = 0;
        FarLand.loadingAfresh();
        FineSkins.leftSave();
        Impacts.forget();
        Fires.forget();
        Pieces.forget();
        Corpses.forget();
        Bleeding.forget();
        LimbDecals.forget();
        SharedHeads.forget();
        Rewind.forget();
        HiddenWalls.forget();
        Troughs.forget();
        CarPhysics.forget();
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null) {
                view.firstPerson.leftSave();
            }
        }
    }

    /** Called with the direction a player's movement input asks for. */
    public static Vector2 moveVector(Vector2 move, IsoPlayer player) {
        View view = View.of(player);
        return view == null ? move : view.firstPerson.moveVector(move);
    }

    /** Called with the way the game takes a player to be looking when it works out what they can see. */
    public static Vector2 sightVector(Vector2 look, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? look : view.firstPerson.sightVector(look);
    }

    /** In place of the game's reading, for its screen shader, of how much to grey a player's view by: see ScreenGrey. */
    public static float screenDesaturation(RenderSettings.PlayerRenderSettings settings) {
        float stock = settings.getDesaturation();
        for (int i = 0; i < View.COUNT; i++) {
            if (RenderSettings.getInstance().getPlayerSettings(i) == settings) {
                View view = View.of(i);
                if (view == null) {
                    return stock;
                }
                return ScreenGrey.under(stock, Quality.plain(i), view.flight.shown(), view.pipeline.styledShown());
            }
        }
        throw new IllegalStateException("render settings of no player");
    }

    /** Whether the first person picture is drawn over the whole of the game's own view of the player it is drawing. */
    public static boolean coversTheGamesView() {
        View view = drawing();
        return view != null && view.firstPerson.coversTheGamesView();
    }

    /** In place of the game's reading of where a player's reticle or aim cursor is drawn, across the screen. */
    public static int reticleX(int playerIndex) {
        int stock = AimingReticle.getXA(playerIndex);
        View view = View.of(playerIndex);
        return view == null ? stock : view.firstPerson.reticleX(stock);
    }

    /** The same, down the screen. */
    public static int reticleY(int playerIndex) {
        int stock = AimingReticle.getYA(playerIndex);
        View view = View.of(playerIndex);
        return view == null ? stock : view.firstPerson.reticleY(stock);
    }

    /** Called as the game's window is about to show or hide its cursor: the mouse is the first player's. */
    public static boolean holdsMouse() {
        return View.first().firstPerson.holdsMouse();
    }

    /** Called at the end of the game's Mouse.update with the cursor column it read. */
    public static int cursorX(int raw) {
        return View.first().firstPerson.cursorX(raw);
    }

    /** Called at the end of the game's Mouse.update with the cursor row it read. */
    public static int cursorY(int raw) {
        return View.first().firstPerson.cursorY(raw);
    }

    /** Called with where the game has a player's aim on their part of the screen, across: the mouse, or for a controller what its stick has pushed it to. */
    public static int heldAcross(int stock, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? stock : view.firstPerson.heldAcross(stock);
    }

    /** The same, down. */
    public static int heldDown(int stock, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? stock : view.firstPerson.heldDown(stock);
    }

    private static View of(CharacterInputComponent input) {
        IsoPlayer player = input.tryGetECSOwnerEntityAs(IsoPlayer.class);
        return player == null ? null : View.of(player);
    }

    /** Called with what a character's aim input reads. */
    public static boolean aimKey(boolean stock, CharacterInputComponent input) {
        View view = of(input);
        return view == null ? stock : view.controls.aimKey(stock);
    }

    /** Called with what a character's attack button reads. */
    public static boolean attackKey(boolean stock, CharacterInputComponent input) {
        View view = of(input);
        return view == null ? stock : view.controls.attackKey(stock);
    }

    /** Called with what a character's shove input reads. */
    public static boolean meleeKey(boolean stock, CharacterInputComponent input) {
        View view = of(input);
        return view == null ? stock : view.controls.meleeKey(stock);
    }

    /** Called with whether a character's Interact button is down. */
    public static boolean interactKey(boolean stock, CharacterInputComponent input) {
        View view = of(input);
        return view == null ? stock : view.controls.interactKey(stock);
    }

    /** Called with whether the game's own view has an object under the mouse pointer for a click to act on. */
    public static boolean picked(boolean stock) {
        return View.first().controls.picked(stock);
    }

    /** Called with the tile the game's own view has under the mouse, where the game puts a player's drag cursor: see DragAim. */
    public static Vector2 dragTile(Vector2 stock, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? stock : view.dragAim.tile(stock, view.dragsInFirstPerson());
    }

    /** Called with the place across the map that the game's own view has at a point on a player's screen. */
    public static float screenToIsoX(float stock, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? stock : view.dragAim.isoX(stock, view.dragsInFirstPerson());
    }

    /** The same, down the map. */
    public static float screenToIsoY(float stock, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? stock : view.dragAim.isoY(stock, view.dragsInFirstPerson());
    }

    /**
     * Called in place of the game's telling its Lua that a mouse button went down or up where none
     * of its panels took it. The Lua is told first.
     */
    public static void mouseButton(String event, Object x, Object y) {
        LuaEventManager.triggerEvent(event, x, y);
        View.first().worldMenu.mouse(event, (Double) x, (Double) y);
    }

    /** Called where the game asks whether the player with the mouse is aiming before it hands a click to the object under the pointer. */
    public static boolean aimingForWorldClicks(boolean stock) {
        return View.first().controls.aimingForWorldClicks(stock);
    }

    /** Called with the direction the game aims a player in. */
    public static Vector2 aimVector(Vector2 stock, IsoPlayer player) {
        View view = View.of(player);
        return view == null ? stock : view.controls.aimVector(stock);
    }

    /** In place of the game's ballistics reading the screen column of a player's reticle. */
    public static int aimPointX(int playerIndex) {
        int stock = AimingReticle.getX(playerIndex);
        View view = View.of(playerIndex);
        return view == null ? stock : view.controls.aimPointX(stock);
    }

    /** The same for the screen row. */
    public static int aimPointY(int playerIndex) {
        int stock = AimingReticle.getY(playerIndex);
        View view = View.of(playerIndex);
        return view == null ? stock : view.controls.aimPointY(stock);
    }

    /** The part {@link #shotAt} found the hit being worked out is on, or none, and whether it is a hand or a foot of that part. */
    private static int aimedPart = HitPart.NONE;
    private static boolean aimedEnd;

    /**
     * How many of a blast's pellets each character the game let into its hit list has taken so
     * far, by the character's id. The game puts a character in the list once for each of its
     * pellets that found them, and works each out as a hit of its own: see Controls.aimedPart.
     */
    private static final HashMap<Integer, Integer> pelletsTaken = new HashMap<>();

    /**
     * Called as the game starts working out where a shot one character dealt another lands. The
     * weapon is null for damage a server sent and for an animal's bite, which no aim placed.
     */
    public static void shotAt(HandWeapon weapon, IsoGameCharacter wielder, IsoGameCharacter target) {
        View view = wielder instanceof IsoPlayer player ? View.of(player) : null;
        aimedPart = view == null || weapon == null ? HitPart.NONE : view.controls.aimedPart(target, weapon, pelletsTaken.getOrDefault(target.getID(), 0));
        aimedEnd = aimedPart != HitPart.NONE && view.controls.aimedEnd();
    }

    /** Called as the game starts working out which targets an attack can reach (CombatManager.calculateHitInfoList). */
    public static void hitListStarts() {
        Penetration.listStarts();
        pelletsTaken.clear();
    }

    /** Called with the game's line of sight test from a shooter to a target, as its firearm keeps or drops the target: see Penetration. */
    public static LosUtil.TestResults lineOfFire(LosUtil.TestResults seen, IsoGameCharacter shooter, HandWeapon weapon, IsoMovingObject target) {
        return Penetration.lineOfFire(seen, shooter, weapon, target);
    }

    /**
     * Called with the game's answer to whether its ballistics found a ranged target, where it drops the
     * target if not. A character behind a wall a round goes through is out of the ballistics' sight and
     * is kept all the same.
     */
    public static boolean validTarget(boolean found, IsoMovingObject target) {
        return found || Penetration.kept(target);
    }

    /** Called where the game deals a hit its damage: a target reached through a wall gets the share of it the round has left. */
    public static float hitThrough(IsoMovingObject target, HandWeapon weapon, IsoGameCharacter wielder, float damageSplit, boolean ignoreDamage, float modDelta) {
        float share = Penetration.shareOf(target.getID());
        if (FirstPerson.TRACE && share < Penetration.WHOLE) {
            Log.info(String.format(Locale.ROOT, "penetration: %d hit through what is in the way, share %.3f of %.4f", target.getID(), share, damageSplit));
        }
        float dealt = target.Hit(weapon, wielder, damageSplit * share, ignoreDamage, modDelta);
        // The game works the hit's part out once before the hit and again inside it: both are this pellet's.
        pelletsTaken.merge(target.getID(), 1, Integer::sum);
        return dealt;
    }

    /** Called where the game asks whether its overhead ray found the target, which decides whether it takes a part from that ray. */
    public static boolean cameraTarget(BallisticsController ballistics, int id) {
        return aimedPart != HitPart.NONE || ballistics.isCameraTarget(id);
    }

    /** Called where the game takes the part its overhead ray found. */
    public static int targetedBodyPart(BallisticsController ballistics, int id) {
        return aimedPart != HitPart.NONE ? aimedPart : ballistics.getCachedTargetedBodyPart(id);
    }

    /** What first person shots have done to zombies' limbs. */
    static final Wounds wounds = new Wounds();

    /**
     * Called as IsoZombie.hitConsequences returns: the hit has been taken off the zombie's health,
     * the game has said whether it falls, and nothing of the hit has been sent to a server yet.
     * {@link #shotAt} has just been called for this hit again from inside it, unless it came from
     * the network.
     */
    public static void zombieHit(IsoZombie zombie, HandWeapon weapon, IsoGameCharacter shooter, float damage, boolean fromNetwork) {
        if (fromNetwork || !Rules.wounds() || aimedPart == HitPart.NONE || !weapon.isRanged()) {
            return;
        }
        if (aimedPart == RagdollBodyPart.BODYPART_HEAD.ordinal()) {
            headShot(zombie, weapon, shooter, damage);
            return;
        }
        int limb = limbOf(aimedPart);
        if (limb < 0) {
            return;
        }
        float whole = damage / CombatManager.getInstance().getCombatConfig().get(CombatConfigKey.LEG_HIT_DAMAGE_SPLIT_MODIFIER);
        boolean upper = aimedPart == RagdollBodyPart.BODYPART_LEFT_UPPER_ARM.ordinal() || aimedPart == RagdollBodyPart.BODYPART_RIGHT_UPPER_ARM.ordinal()
                || aimedPart == RagdollBodyPart.BODYPART_LEFT_UPPER_LEG.ordinal() || aimedPart == RagdollBodyPart.BODYPART_RIGHT_UPPER_LEG.ordinal();
        int at = upper ? Wounds.UPPER : aimedEnd ? Wounds.END : Wounds.LOWER;
        int stage = wounds.shot(zombie, limb, whole, zombie.getHealth(), at, chance.nextFloat() < DEATH_CUTS);
        if (stage == Wounds.SOUND) {
            return;
        }
        String said;
        boolean leg = limb == Wounds.LEFT_LEG || limb == Wounds.RIGHT_LEG;
        int where = wounds.cut(zombie, limb);
        String joint = where == Wounds.UPPER ? leg ? "hip" : "shoulder" : where == Wounds.LOWER ? leg ? "knee" : "elbow" : leg ? "ankle" : "wrist";
        boolean bursts = stage == Wounds.SEVERED && chance.nextFloat() < LIMB_BURSTS;
        String cut = stage != Wounds.SEVERED ? "" : (bursts ? "burst at the " : "cut off at the ") + joint + ", ";
        if (stage == Wounds.SEVERED) {
            float high = zombie.isOnFloor() ? 0.2f : leg ? 0.45f : 1f;
            Stumps.bloody(zombie, limb, where);
            if (bursts) {
                Bleeding.mist(zombie, high, 7, 3f, shooter.getX(), shooter.getY());
                Bleeding.spray(zombie, Bleeding.SPRAY * 3, Bleeding.SPRAY_REACH * 1.8f);
                Pieces.burst(zombie, limb, where, shooter.getX(), shooter.getY());
            } else {
                Bleeding.spray(zombie);
                Bleeding.mist(zombie, high, 2, 1.5f, shooter.getX(), shooter.getY());
                Pieces.cut(zombie, limb, where, shooter.getX(), shooter.getY());
            }
        }
        if (zombie.isDead() || zombie.isCrawling()) {
            said = cut + (zombie.isDead() ? "as it dies" : "as it crawls");
        } else if (leg && stage >= Wounds.RUINED) {
            // Up as a crawler: ZombieOnGroundState makes a zombie marked to become one crawl as it
            // gets up. The game's own roll may have marked it already, which does nothing until it falls.
            // A leg ruined and still on gets up again on it as often as GETS_UP says, and shambles on.
            boolean crawls = stage == Wounds.SEVERED || chance.nextFloat() >= GETS_UP;
            if (crawls) {
                zombie.setBecomeCrawler(true);
            } else if (zombie.getWalkType() == null || !zombie.getWalkType().startsWith("slow")) {
                zombie.doShambler();
            }
            boolean standing = !zombie.isOnFloor();
            if (standing) {
                // Setting knockedDown alone is cleared by the shot's own hit reaction and the zombie
                // stays up (measured); knockDown also drops that reaction and reports the hit again,
                // as the game's own falls do.
                zombie.knockDown(false);
            }
            said = cut + (standing ? "ruined: it goes down" : "ruined: it is down") + (crawls ? " and gets up to crawl" : " and gets up again to shamble");
        } else if (leg) {
            // A walk type starting "slow" is a shambler's, as IsoZombie.getSpeedTypeFromWalkType reads it.
            boolean faster = zombie.getWalkType() == null || !zombie.getWalkType().startsWith("slow");
            if (faster) {
                zombie.doShambler();
            }
            said = faster ? "wounded: it shambles" : "wounded: it shambled already";
        } else {
            said = cut + (stage >= Wounds.RUINED ? "ruined: it hangs dead" : "wounded: it hangs");
        }
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "zombie %d's %s is %s (health %.2f)", zombie.getID(), LIMB_NAMES[limb], said, zombie.getHealth()));
        }
    }

    /** The key a body's mod data keeps where its limbs are cut under, as {@link Wounds#cuts} makes it: saved with the body. */
    static final String BODY_CUTS = "cameraunlockLimbCuts";

    /** Called as a body is made of a character that has died, once it is made. */
    public static void bodyMade(IsoDeadBody body, IsoGameCharacter died) {
        int cuts = died instanceof IsoZombie ? wounds.cuts(died) : 0;
        if (cuts != 0) {
            body.getModData().rawset(BODY_CUTS, (double) cuts);
        }
    }

    /** Called as the game has read what to draw a picture of a body from, from the body. */
    public static void bodyParams(DeadBodyAtlas.BodyParams params, IsoDeadBody body) {
        params.variables.remove(BODY_CUTS);
        if (body.hasModData() && body.getModData().rawget(BODY_CUTS) instanceof Double cuts) {
            params.variables.put(BODY_CUTS, Integer.toString(cuts.intValue()));
        }
    }

    /** Called with the name the game files a body's picture under: a body with a limb cut is not the picture of one without. */
    public static String bodyKey(String key, DeadBodyAtlas.BodyParams params) {
        String cuts = params.variables.get(BODY_CUTS);
        return cuts == null ? key : key + " cut " + cuts;
    }

    /** Called as the game has set up the model a body's picture is drawn from, before it is posed. */
    public static void bodyBaking(DeadBodyAtlas.BodyParams params, AnimatedModel model) {
        String cuts = params.variables.get(BODY_CUTS);
        Stumps.baking(model.getAnimationPlayer(), cuts == null ? 0 : Integer.parseInt(cuts));
    }

    /**
     * Called as the game has made the picture of a body from the model it posed for it, with
     * the game's entry for the picture and the way the model was turned: see Corpses.baked.
     */
    public static void bodyBaked(AnimatedModel model, Object entry, float angle) {
        if (Quality.corpseModelsAnywhere()) {
            Corpses.baked(model, entry, angle);
        }
    }

    /**
     * Called as the game is about to destroy a texture it combined from others, a character's
     * skin among them: true puts it off, see Pieces.keeps and Corpses.keeps.
     */
    public static boolean keepsTexture(Texture texture) {
        return Pieces.keeps(texture) || Corpses.keeps(texture);
    }

    /** Called as the game starts writing a chunk to its save, and once it has: see Bleeding.saving. */
    public static void chunkSaving(IsoChunk chunk) {
        Bleeding.saving(chunk);
    }

    public static void chunkSaved(IsoChunk chunk) {
        Bleeding.saved(chunk);
    }

    /** Called as the game has loaded a chunk's squares and put its things in the world, on its logic thread: see HiddenWalls.loaded. */
    public static void chunkLoaded(IsoChunk chunk) {
        HiddenWalls.loaded(chunk);
    }

    /**
     * Called as the game has every zone of the map it is loading, on the thread that loads it,
     * before the chunk streamer that adds zones of its own is made: see Country.read.
     */
    public static void zonesProcessed(IsoMetaGrid grid) {
        Country.read(grid);
    }

    /** Called as the game starts a tick of the world. */
    public static void worldUpdate() {
        Bleeding.tick(wounds);
        Pieces.tick();
        Rewind.tick();
    }

    /** Called at the start of IsoGameCharacter.Kill with what it was called with. Says whether to leave the Kill out: see Rewind. */
    public static boolean heldDeath(IsoGameCharacter dying, IsoGameCharacter killer, HandWeapon weapon, boolean gory, CharacterDiedListener listener) {
        return Rewind.holds(dying, killer, weapon, gory, listener);
    }

    /** Called at the start of IngameState.updateInternal, which saves the game as it leaves for the menu once Core.exiting is set. */
    public static void ingameUpdate() {
        if (zombie.core.Core.exiting) {
            Rewind.leaving();
        }
    }

    /** Called at the start of IsoZombie's preupdate, update and postupdate. Says whether to pass them over: see Rewind. */
    public static boolean zombiesStill() {
        return Rewind.zombiesStill();
    }

    /** How often a limb a shot takes off bursts in blood and shards rather than coming off whole. */
    static final float LIMB_BURSTS = 0.8f;
    /** How often a zombie's death by a shot to a limb takes that limb off, once the shot's damage would take it off a whole zombie. */
    static final float DEATH_CUTS = 0.3f;
    /** How often a zombie whose leg is ruined, and not cut off, gets up on it again rather than crawling. */
    static final float GETS_UP = 0.3f;
    /**
     * The least a killing head shot does for the head to come off or burst, and how often each.
     * The game leaves a dead zombie's health at nothing, so it is the shot's damage that is read:
     * a pistol's head shot did 1.4 and a critical one 7.5, and each pellet of a shotgun's 50 to 105.
     * A bullet seldom takes a head off whole; a heavy one bursts it now and then. A shotgun's
     * blast from within CLOSE_BLAST tiles bursts it most times and never takes it off whole.
     */
    static final float BURSTS_FROM = 20f, POPS = 0.04f, BURSTS = 0.12f;
    static final float CLOSE_BLAST = 5f, CLOSE_BLAST_BURSTS = 0.8f;
    /** How high a standing zombie's head is over its feet, in tiles, just under a standing eye (FirstPerson.EYE_HEIGHT_STANDING). */
    static final float HEAD_HIGH = 1.55f;
    private static final java.util.Random chance = new java.util.Random();

    /** A head shot that killed: one that left the zombie far enough below no health may burst its head. */
    private static void headShot(IsoZombie zombie, HandWeapon weapon, IsoGameCharacter from, float damage) {
        if (!zombie.isDead()) {
            return;
        }
        if (damage < BURSTS_FROM || !wounds.firstHeadShot(zombie)) {
            return;
        }
        // A blast's pellets each come here: the head is rolled for once, by the first hard enough.
        float roll = chance.nextFloat();
        boolean closeBlast = ImpactFinder.size(weapon) == ImpactFinder.PELLET
                && Math.hypot(zombie.getX() - from.getX(), zombie.getY() - from.getY()) <= CLOSE_BLAST;
        float pops = closeBlast ? 0f : POPS, bursts = closeBlast ? CLOSE_BLAST_BURSTS : BURSTS;
        String said;
        if (roll < pops) {
            wounds.burst(zombie);
            Bleeding.spray(zombie, Bleeding.SPRAY, Bleeding.SPRAY_REACH);
            Stumps.bloody(zombie, Wounds.HEAD, Wounds.UPPER);
            Pieces.cut(zombie, Wounds.HEAD, Wounds.UPPER, from.getX(), from.getY());
            Bleeding.mist(zombie, zombie.isOnFloor() ? 0.3f : HEAD_HIGH, 3, 1.5f, from.getX(), from.getY());
            said = "its head comes off";
        } else if (roll < pops + bursts) {
            wounds.burst(zombie);
            Bleeding.mist(zombie, zombie.isOnFloor() ? 0.3f : HEAD_HIGH, 7, 3f, from.getX(), from.getY());
            Pieces.burst(zombie, Wounds.HEAD, Wounds.UPPER, from.getX(), from.getY());
            Bleeding.spray(zombie, Bleeding.SPRAY * 3, Bleeding.SPRAY_REACH * 1.8f);
            Stumps.bloody(zombie, Wounds.HEAD, Wounds.UPPER);
            said = "its head bursts";
        } else {
            said = "its head holds";
        }
        if (DevBuild.ON) {
            Log.info(String.format(Locale.ROOT, "zombie %d killed by a head shot of %.2f: %s", zombie.getID(), damage, said));
        }
    }

    private static final String[] LIMB_NAMES = {"left arm", "right arm", "left leg", "right leg"};

    /** Called as the game starts making a new zombie of an object it kept from one that left the world. */
    public static void zombieReused(IsoZombie zombie) {
        wounds.forget(zombie);
        Bleeding.reused(zombie);
        Pieces.reused(zombie);
    }

    /** Which of {@link Wounds}' limbs a part of the game's is on, or -1. */
    private static int limbOf(int part) {
        if (part == RagdollBodyPart.BODYPART_LEFT_UPPER_ARM.ordinal() || part == RagdollBodyPart.BODYPART_LEFT_LOWER_ARM.ordinal()) {
            return Wounds.LEFT_ARM;
        }
        if (part == RagdollBodyPart.BODYPART_RIGHT_UPPER_ARM.ordinal() || part == RagdollBodyPart.BODYPART_RIGHT_LOWER_ARM.ordinal()) {
            return Wounds.RIGHT_ARM;
        }
        if (part == RagdollBodyPart.BODYPART_LEFT_UPPER_LEG.ordinal() || part == RagdollBodyPart.BODYPART_LEFT_LOWER_LEG.ordinal()) {
            return Wounds.LEFT_LEG;
        }
        if (part == RagdollBodyPart.BODYPART_RIGHT_UPPER_LEG.ordinal() || part == RagdollBodyPart.BODYPART_RIGHT_LOWER_LEG.ordinal()) {
            return Wounds.RIGHT_LEG;
        }
        return -1;
    }

    /** Called in place of the game working out where a character's muzzle is and which way it points. */
    public static float muzzle(BallisticsController ballistics, Vector3 position, Vector3 direction) {
        float stock = ballistics.calculateMuzzlePosition(position, direction);
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            IsoPlayer player = view == null ? null : view.player();
            if (player != null && player.getBallisticsController() == ballistics) {
                view.controls.muzzle(ballistics, position, direction);
                break;
            }
        }
        return stock;
    }

    /** Called with the binding the game holds under a name: the keyboard is the first player's. */
    public static zombie.core.Core.KeyBinding binding(zombie.core.Core.KeyBinding stock, String name) {
        return View.first().controls.binding(stock, name);
    }

    /** The view of the player whose part of the screen is being drawn. The thread that draws. */
    private static View drawn() {
        return View.of(SpriteRenderer.instance.getRenderingPlayerIndex());
    }

    /**
     * Called with whether the game draws someone seated in a vehicle, which no vehicle of the
     * game's own asks for. A player of this machine in first person with a weapon raised out of
     * their seat is drawn, so that the picture has the weapon in their hand to hold before the eye.
     */
    public static boolean showPassenger(boolean stock, IsoGameCharacter character) {
        if (stock || !(character instanceof IsoPlayer player)) {
            return stock;
        }
        View view = View.of(player);
        return view != null && view.firstPerson.active() && view.viewModel.held != null;
    }

    /**
     * Called with whether the window of a seated player's door is whole, as the game asks to
     * decide whether they may attack from the seat. A player of this machine who shoots through
     * the glass (Ride.shootsThroughGlass) is let fire, and the shot breaks it.
     */
    public static boolean seatGlassInTheWay(boolean stock, IsoPlayer player) {
        return stock && !Ride.shootsThroughGlass(player);
    }

    /**
     * Called with whether a driver aims, as the game asks to decide whether to take the
     * vehicle's controls from a driver raising a firearm. A player of this machine who drives
     * while aiming (Ride.drivesWhileAiming) keeps them.
     */
    public static boolean aimingLetsGoOfTheWheel(boolean aiming, IsoPlayer player) {
        return aiming && !Ride.drivesWhileAiming(player);
    }

    /**
     * Called with the ground a character's animation has moved it this frame, before the game
     * moves it. For a player of this machine walking ahead in first person it is the game's pace,
     * and held in the aiming stance while walked sideways or backwards (Controls.turnedOnly) it is
     * raised to that pace: see Sidestep.
     */
    public static Vector2 moved(Vector2 ground, IsoGameCharacter character) {
        if (!(character instanceof IsoPlayer player)) {
            return ground;
        }
        View view = View.of(player);
        float multiplier = GameTime.getInstance().getMultiplier();
        if (view == null || multiplier <= 0f) {
            return ground;
        }
        float pace = (float) Math.hypot(ground.x, ground.y) / multiplier;
        if (view.controls.turnedOnly() && player.isAiming()) {
            float raised = view.sidestep.raised(pace, view.firstPerson.keysAhead(), player.isSneaking());
            return ground.set(ground.x * raised, ground.y * raised);
        }
        if (pace > 0f && view.firstPerson.walkingAhead() && !player.isAiming() && !player.isRunning() && !player.isSprinting()
                && player.getVehicle() == null && !player.isDraggingCorpse()) {
            view.sidestep.walkedAhead(pace, player.isSneaking(), System.nanoTime());
        }
        return ground;
    }

    /** Called as the game has drawn a person or a vehicle into a player's part of the screen. */
    public static void drawn(ModelSlotRenderData slot) {
        View view = drawn();
        if (view != null) {
            view.characters.drawn(slot);
        }
    }

    /** Whether the game's own draw of each part of each person is left out, the picture covering the player's part of the screen it would go to. */
    public static boolean gamesOwnDrawIsCovered() {
        View view = drawn();
        return view != null && view.characters.gamesOwnDrawIsCovered();
    }

    /** In place of the game's draw of one part of a model, once it has taken the part's skin from the model: GameMouths draws it, a talking person's mouth open. */
    public static void partDrawn(Model model, ModelSlotRenderData slot, ModelInstanceRenderData part) {
        View view = drawn();
        if (view != null) {
            view.characters.wear(part);
        }
        GameMouths.draw(model, slot, part);
    }

    /** Called with the game's answer to whether the square a thing that moves stands on is drawn, which its cutaway says no to above the player's level. */
    public static boolean aboveTheCut(boolean byTheGame, IsoMovingObject moving) {
        View view = drawing();
        return byTheGame || view != null && view.farModels.aboveTheCut(moving);
    }

    /** In place of the game's asking whether a thing that moves is on the screen of the player it is drawing. */
    public static boolean onScreen(IsoMovingObject moving) {
        View view = drawing();
        return view == null ? moving.isOnScreen() : view.farModels.onScreen(moving);
    }

    /** The same for a player, whom the game draws apart from everything else that moves. */
    public static boolean playerOnScreen(IsoPlayer player) {
        View view = drawing();
        return view == null ? player.isOnScreen() : view.farModels.playerOnScreen(player);
    }

    /** After the game's IsoPlayer.updateLOS for a player of this machine: see LiveSight. */
    public static void lineOfSightDone(IsoPlayer player) {
        LiveSight.afterLineOfSight(player);
    }

    /** IsoGameCharacter.TestIfSeen's read of whether a player of this machine could see a character's square: see LiveSight. */
    public static boolean couldSee(boolean marked, IsoGameCharacter character, int index, IsoPlayer player) {
        return LiveSight.couldSee(marked, character, index, player);
    }

    /** The same test's read of whether the player sees the square: see LiveSight. */
    public static boolean canSee(boolean marked, IsoGameCharacter character) {
        return LiveSight.canSee(marked, character);
    }

    /** The cone of sight the game has worked out for a character, as LightingJNI.calculateVisionCone returns it: see LiveSight. */
    public static float visionCone(float cone, IsoGameCharacter character) {
        return LiveSight.coneWorkedOut(cone, character);
    }

    /** In place of the game's asking, as it draws another player on a server, whether the viewer sees them: see FarModels.playerDrawnWhole. */
    public static boolean seesPlayer(IsoPlayer viewer, IsoPlayer other) {
        View view = drawing();
        return view != null && view.farModels.playerDrawnWhole(other) || viewer.checkCanSeeClient(other);
    }

    /** Whether a vehicle knocked down the thing it met, so the game's own handling of the meeting is passed over. */
    public static boolean knockedDown(IsoObject thing, IsoObject by) {
        return by instanceof BaseVehicle vehicle && (CarSmash.struck(thing, vehicle) || CarSmash.brushedThrough(thing));
    }

    /** Called with what a thing a vehicle touches slows it by, where BaseVehicle.breakingObjects takes the most of them. */
    public static float slowFactor(float slows) {
        return Knockdown.slowFactor(Rules.vehiclesStock(), slows);
    }

    /** Asked as BaseVehicle.applyImpulseFromHitPlant begins: true where the plant gives way and the push is passed over. */
    public static boolean plantGivesWay() {
        return Knockdown.plantPushPassedOver(Rules.vehiclesStock(), CarSmash.slowing);
    }

    /** Called with what the game's off-road check reads where CarController.update sets the engine's force. */
    public static boolean offRoadEngine(boolean offRoad) {
        return Knockdown.engineHeldOffRoad(Rules.vehiclesStock(), offRoad);
    }

    /** Called as the game begins each update of a vehicle: see CarPhysics. */
    public static void vehicleUpdate(BaseVehicle vehicle) {
        CarPhysics.update(vehicle);
    }

    /** Called before each physics step for each vehicle: see CarPhysics.step. */
    public static void vehicleStepped(BaseVehicle vehicle) {
        CarPhysics.step(vehicle);
    }

    /** In place of CarController.update handing a vehicle's engine, brakes and steering to the physics. */
    public static void controlVehicle(int id, float engine, float brake, float steering) {
        CarPhysics.control(id, engine, brake, steering);
    }

    /** Knocks down what a vehicle is about to meet. */
    public static void meeting(BaseVehicle vehicle) {
        CarSmash.sweep(vehicle);
    }

    /** Called with what the game goes by as it draws a vehicle for a player. */
    public static boolean seen(boolean byTheGame, BaseVehicle vehicle) {
        View view = drawing();
        return view == null ? byTheGame : view.farModels.seen(byTheGame, vehicle);
    }

    /** In place of the game's asking how far a character shows to a player, as it chooses whether to pose it this tick. */
    public static float animationAlpha(IsoGameCharacter character, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? character.getAlpha(playerIndex) : view.farModels.animationAlpha(character, playerIndex);
    }

    /** In place of the game's asking where on a player's screen a zombie is, across. */
    public static int screenAcross(IsoZombie zombie, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? zombie.getScreenProperX(playerIndex) : view.farModels.screenAcross(zombie, playerIndex);
    }

    /** The same, down. */
    public static int screenDown(IsoZombie zombie, int playerIndex) {
        View view = View.of(playerIndex);
        return view == null ? zombie.getScreenProperY(playerIndex) : view.farModels.screenDown(zombie, playerIndex);
    }

    /** Called as the game ends its choice of which animals have a model. */
    public static void animalsCulled() {
        for (int playerIndex = 0; playerIndex < IsoPlayer.numPlayers; playerIndex++) {
            View view = View.of(playerIndex);
            if (view != null) {
                view.farModels.keepAnimalModels();
            }
        }
    }

    /**
     * In place of the game's own call where it sets a player's listener. The game numbers its
     * listeners by the players there are: with the first player gone, the second's is listener 0.
     */
    public static void listener(int listener, float x, float y, float z, float goingX, float goingY, float goingZ,
            float forwardX, float forwardY, float forwardZ, float upX, float upY, float upZ) {
        View view = null;
        for (int i = 0, there = 0; i < IsoPlayer.players.length && view == null; i++) {
            if (IsoPlayer.players[i] != null && there++ == listener) {
                view = View.of(i);
            }
        }
        if (view == null) {
            javafmod.FMOD_Studio_Listener3D(listener, x, y, z, goingX, goingY, goingZ, forwardX, forwardY, forwardZ, upX, upY, upZ);
        } else {
            view.hearing.listener(listener, x, y, z, goingX, goingY, goingZ, forwardX, forwardY, forwardZ, upX, upY, upZ);
        }
    }

    /**
     * Called with how far out the game takes its camera to be, as it hands that to its sounds.
     * It is one number for all that is heard, and the game takes it from its first player
     * alive, as this does.
     */
    public static float zoomedOut(float games) {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            IsoPlayer player = view == null ? null : view.player();
            if (player != null && !player.isDead()) {
                return view.hearing.zoomedOut(games);
            }
        }
        return games;
    }

    /** Called as a sound emitter of the game's starts one of its events. */
    public static void eventStarted(FMODSoundEmitter emitter, long event) {
        SoundMix.started(emitter, event);
    }

    /** Called as the game has FMOD call it back on an event instance's events. */
    public static void gameCallsBack(long event) {
        SoundMix.gameCallsBack(event);
    }

    /**
     * Called with how hidden the game takes a sound to be from its players, 1 for any square
     * outside what the game's own view of a player could see, as it hands that to the sound.
     * From the eye that is not hidden at all: what is behind the head is not muffled, and the
     * walls are put in by SoundMix. Where SoundMix puts nothing on the emitter's sounds, the game's
     * own stands.
     */
    public static float occluded(float games, FMODSoundEmitter emitter) {
        if (!SoundMix.wallsIn(emitter)) {
            return games;
        }
        float in = 0f;
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null && view.hearing.hearsRound()) {
                in = Math.max(in, view.hearing.share());
            }
        }
        return games * (1f - in);
    }

    /** Called where the game reads the look direction a torch shines along, with the player who carries it. */
    public static Vector2 lightDirection(Vector2 look, IsoPlayer carrier) {
        View view = View.of(carrier);
        return view == null ? look : view.headFollowLight.direction(view.firstPerson.sightVector(look));
    }

    /**
     * At the start of ThunderStorm.enqueueThunderEvent: the game has a strike at square (x, y), or
     * thunder with none, alone and from a server alike. Lit is false for a strike the player has
     * asked the game not to show lightning for.
     */
    public static void thunder(int x, int y, boolean strike, boolean lit, boolean rumble) {
        boolean hidden = !strike && rumble && !zombie.core.Core.getInstance().getOptionDisableLightningDuringStorms();
        Lightning.struck(x, y, strike && lit, hidden, System.nanoTime());
    }

    /** Before ThunderStorm.enqueueThunderEvent returns, once it has started its own flash for each player near enough. */
    public static void thunderTold(ThunderStorm storm) {
        Lightning.leaveTheGamesFlash(storm);
    }

    /** Before ShaderUnit.loadShaderFile returns: the source of one of the game's shaders as it was read from fileName. See VehicleLamps and GameMouths. */
    public static String shaderSource(String code, String fileName) {
        return GameMouths.source(VehicleLamps.source(code, fileName), fileName);
    }

    /** In place of the game's starting of a muzzle flash, which it does at the one place a firearm's shot is taken. */
    public static void muzzleFlash(EffectsManager effects, IsoGameCharacter shooter, int light) {
        Stack<IsoLightSource> lamps = IsoWorld.instance.currentCell.getLamppostPositions();
        int before = lamps.size();
        effects.startMuzzleFlash(shooter, light);
        View view = shooter instanceof IsoPlayer player ? View.of(player) : null;
        if (view != null) {
            if (lamps.size() > before) {
                view.torchLess.shot(lamps.peek());
            }
            view.viewModel.shot();
        }
    }

    /** Called as the game starts drawing a shot's tracer to where the shot ended: see ImpactFinder. */
    public static void tracer(IsoGameCharacter shooter, float range, float x, float y, float z, IsoGridSquare stopped) {
        if (shooter != null) {
            ImpactFinder.ended(shooter, range, x, y, z, stopped, true);
        }
    }

    /** Called as the game starts drawing a shot's tracer along the aim to the weapon's range. */
    public static void tracerToRange(IsoGameCharacter shooter, float range) {
        if (shooter != null) {
            ImpactFinder.ended(shooter, range, 0f, 0f, 0f, null, false);
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    public static void pose(AnimationPlayer animation) {
        HitFlinch.pose(animation);
        WoundPose.pose(animation, wounds);
        SharedHeads.pose(animation);
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null) {
                view.modelHead.pose(animation);
            }
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made. */
    public static void posed(AnimationPlayer animation) {
        // Put back in the opposite order to turning: what HitFlinch keeps is the game's own pose.
        SharedHeads.posed(animation);
        WoundPose.posed(animation);
        HitFlinch.posed(animation);
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null) {
                view.modelHead.posed(animation);
            }
        }
    }

    /**
     * Called from AnimationPlayer once the matrices of the bones in the model are made and the
     * ones the model is drawn with are marked to be made again from them, whether the bones were
     * posed by the character's own animations or copied from a skeleton shared by many.
     */
    public static void skinned(AnimationPlayer animation) {
        Stumps.cut(animation, wounds);
    }

    /** Called where the game has worked out where on the screen of the player it is drawing to write over a person. */
    public static void place(IsoGameCharacter character, Vector2 at) {
        View view = drawing();
        if (view != null) {
            view.nameTags.place(character, at);
        }
    }

    /** In place of the game's writing of a person's name. */
    public static void writeName(TextDrawObject name, double x, double y, boolean outlined) {
        View view = drawing();
        if (view == null) {
            name.AddBatchedDraw(x, y, outlined);
        } else {
            view.nameTags.writeName(name, x, y, outlined);
        }
    }

    /** In place of the game's writing of what a person has said. */
    public static void writeSpeech(ChatElement speech, int playerIndex, int x, int y, boolean ignoreRadioLines) {
        View view = View.of(playerIndex);
        if (view == null) {
            speech.renderBatched(playerIndex, x, y, ignoreRadioLines);
        } else {
            view.nameTags.writeSpeech(speech, playerIndex, x, y, ignoreRadioLines);
        }
    }

    /** In place of the game's asking what its own view has under the pointer, to find whose name to write. */
    public static IsoObjectPicker.ClickObject underPointer(IsoObjectPicker picker, int x, int y) {
        View view = drawing();
        return view == null ? picker.ContextPick(x, y) : view.nameTags.underPointer(picker, x, y);
    }
}
