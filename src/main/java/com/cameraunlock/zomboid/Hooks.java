package com.cameraunlock.zomboid;

import fmod.javafmod;
import zombie.EffectsManager;
import zombie.characters.IsoGameCharacter;
import zombie.characters.IsoPlayer;
import zombie.characters.IsoZombie;
import zombie.characters.component.CharacterInputComponent;
import zombie.chat.ChatElement;
import zombie.core.SpriteRenderer;
import zombie.core.physics.BallisticsController;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.ModelSlotRenderData;
import zombie.input.AimingReticle;
import zombie.iso.IsoMovingObject;
import zombie.iso.IsoObjectPicker;
import zombie.iso.Vector2;
import zombie.ui.TextDrawObject;

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

    /** Called from IngameState.renderFrameInternal straight after the game has drawn a player's world. */
    public static void afterWorldRender() {
        IsoPlayer player = IsoPlayer.getInstance();
        if (player == null) {
            return;
        }
        int index = player.getIndex();
        int pressed = Tracking.pressedFor(index);
        if (index < View.COUNT) {
            if (index == 1) {
                Tracking.listenForSecond();
            }
            View.join(index).firstPerson.afterWorldRender(pressed);
        }
    }

    /** Called as the game leaves a save for its menu. */
    public static void leftSave(Object state) {
        Tracking.leftSave();
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

    /** Called with whether a character's Interact button is down. */
    public static boolean interactKey(boolean stock, CharacterInputComponent input) {
        View view = of(input);
        return view == null ? stock : view.controls.interactKey(stock);
    }

    /** Called with whether the game's own view has an object under the mouse pointer for a click to act on. */
    public static boolean picked(boolean stock) {
        return View.first().controls.picked(stock);
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

    /** Called where the game's ballistics have just worked out where a muzzle is and which way it points. */
    public static void muzzle(BallisticsController ballistics) {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            IsoPlayer player = view == null ? null : view.player();
            if (player != null && player.getBallisticsController() == ballistics) {
                view.controls.muzzle(ballistics);
                return;
            }
        }
    }

    /** Called with the binding the game holds under a name: the keyboard is the first player's. */
    public static zombie.core.Core.KeyBinding binding(zombie.core.Core.KeyBinding stock, String name) {
        return View.first().controls.binding(stock, name);
    }

    /** The view of the player whose part of the screen is being drawn. The thread that draws. */
    private static View drawn() {
        return View.of(SpriteRenderer.instance.getRenderingPlayerIndex());
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

    /** In place of the game's asking whether a thing that moves is on the screen of the player it is drawing. */
    public static boolean onScreen(IsoMovingObject moving) {
        View view = drawing();
        return view == null ? moving.isOnScreen() : view.farModels.onScreen(moving);
    }

    /** Called with what the game goes by as it draws a vehicle for a player. */
    public static boolean seen(boolean byTheGame) {
        View view = drawing();
        return view == null ? byTheGame : view.farModels.seen(byTheGame);
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

    /** In place of the game's own call where it sets a player's listener. */
    public static void listener(int index, float x, float y, float z, float goingX, float goingY, float goingZ,
            float forwardX, float forwardY, float forwardZ, float upX, float upY, float upZ) {
        View view = View.of(index);
        if (view == null) {
            javafmod.FMOD_Studio_Listener3D(index, x, y, z, goingX, goingY, goingZ, forwardX, forwardY, forwardZ, upX, upY, upZ);
        } else {
            view.hearing.listener(index, x, y, z, goingX, goingY, goingZ, forwardX, forwardY, forwardZ, upX, upY, upZ);
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

    /** Called where the game reads the look direction a torch shines along, with the player who carries it. */
    public static Vector2 lightDirection(Vector2 look, IsoPlayer carrier) {
        View view = View.of(carrier);
        return view == null ? look : view.headFollowLight.direction(view.firstPerson.sightVector(look));
    }

    /** In place of the game's starting of a muzzle flash, which it does at the one place a firearm's shot is taken. */
    public static void muzzleFlash(EffectsManager effects, IsoGameCharacter shooter, int light) {
        effects.startMuzzleFlash(shooter, light);
        View view = shooter instanceof IsoPlayer player ? View.of(player) : null;
        if (view != null) {
            view.viewModel.shot();
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    public static void pose(AnimationPlayer animation) {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null) {
                view.modelHead.pose(animation);
            }
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made. */
    public static void posed(AnimationPlayer animation) {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null) {
                view.modelHead.posed(animation);
            }
        }
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
