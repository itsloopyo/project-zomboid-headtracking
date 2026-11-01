package com.cameraunlock.zomboid;

import java.util.Locale;
import zombie.core.opengl.ShaderProgram;
import zombie.core.skinnedmodel.advancedanimation.AnimatedModel;
import zombie.core.skinnedmodel.animation.AnimationPlayer;
import zombie.core.skinnedmodel.model.Model;
import zombie.core.skinnedmodel.model.ModelInstance;
import zombie.core.skinnedmodel.model.ModelMesh;
import zombie.core.skinnedmodel.model.SkinningBone;
import zombie.core.skinnedmodel.model.SkinningData;
import zombie.core.skinnedmodel.shader.Shader;
import zombie.core.textures.Texture;

/**
 * The character on the game's Customise Character screen, while CustomiseCharacterHead is on: its
 * head turns with the player's, and with TalkingMouths on its mouth moves with player 1's
 * microphone. The screen shows it in a UI3DModel panel, which poses and draws a model of its own
 * with no character in the world behind it, so ModelHead and GameMouths never reach it. A panel
 * that shows a character in the world, as the character screen in a save does, or an animal, is
 * left alone.
 *
 * The character faces the player, and turns as their reflection would: a head turned to the
 * player's right turns the character's to its own left, and a head tilted to the right tilts its
 * own to its left. Looking up and down is the same either way.
 *
 * In the menus nothing else asks core for the first player's pose, so the panel asks for it as it
 * poses. In a save the view's own frame does, every frame, and its answer is taken as it is.
 *
 * Posed on the logic thread inside UI3DModel.render, drawn on the render thread.
 */
final class CustomiseCharacter {
    private CustomiseCharacter() {}

    /** How long ago core may have been asked for the first player's pose before the panel asks itself. */
    private static final long ASKED_WITHIN_NANOS = 100_000_000L;
    private static final long SAID_EVERY_NANOS = 1_000_000_000L;

    /** The model being posed now, between the start and the end of its update, or null. Logic thread. */
    private static AnimatedModel posing;
    /** The model last posed as the character being made, which the render thread draws with its mouth moving. */
    private static volatile AnimatedModel shown;

    private static final float[] mirrored = new float[3];

    /** The game's types the turn is worked in, made the first time a head is turned: mirror is used where the game is not there. */
    private static final class Bones {
        static final HeadTurn TURN = new HeadTurn();
    }

    private static boolean bonesSaid;
    private static long headSaid, mouthSaid;

    /**
     * The tracker's yaw, pitch and roll, in radians in its own senses, as the character facing the
     * player turns them: into out.
     */
    static void mirror(float[] head, float[] out) {
        out[0] = -head[0];
        out[1] = head[1];
        out[2] = -head[2];
    }

    /** In place of the panel's update of its model, which poses it. */
    static void update(AnimatedModel model) {
        if (!Part.CUSTOMISE_CHARACTER_HEAD.on() || model.getCharacter() != null || model.getAnimalType() != null) {
            if (shown == model) {
                shown = null;
            }
            model.update();
            return;
        }
        shown = model;
        HeadTracking tracking = View.first().headTracking;
        if (!tracking.askedSince(System.nanoTime() - ASKED_WITHIN_NANOS)) {
            tracking.follow(true);
        }
        posing = model;
        try {
            model.update();
        } finally {
            posing = null;
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the bones are posed and the spine is twisted. */
    static void pose(AnimationPlayer animation) {
        AnimatedModel model = posing;
        if (model == null || model.getAnimationPlayer() != animation) {
            return;
        }
        float[] head = View.first().headTracking.headTurn();
        if (head == null) {
            return;
        }
        SkinningData skeleton = animation.getSkinningData();
        SkinningBone neck = skeleton.getBone(ModelHead.NECK), top = skeleton.getBone(ModelHead.HEAD);
        if (neck == null || top == null || neck.parent == null) {
            if (!bonesSaid) {
                bonesSaid = true;
                Log.info("the character on the Customise Character screen does not turn its head: its skeleton has no " + ModelHead.NECK
                        + " under a parent and " + ModelHead.HEAD);
            }
            return;
        }
        mirror(head, mirrored);
        Bones.TURN.turn(animation, neck, top, mirrored[0], mirrored[1], mirrored[2], ModelHead.NECK_SHARE);
        if (DevBuild.ON && System.nanoTime() - headSaid > SAID_EVERY_NANOS) {
            headSaid = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "the character on the Customise Character screen turns its head by yaw %.1f, pitch %.1f, roll %.1f degrees",
                    Math.toDegrees(mirrored[0]), Math.toDegrees(mirrored[1]), Math.toDegrees(mirrored[2])));
        }
    }

    /** Called from AnimationPlayer.updateAnimation_StandardAnimation once the matrices the model is drawn with are made: puts the bones back as the animations had them. */
    static void posed(AnimationPlayer animation) {
        Bones.TURN.putBack(animation);
    }

    /**
     * In place of AnimatedModel.DrawCharSingular's draw of a part's mesh, with the part's program
     * started. A part of the character being made is drawn with the mouth's shape set on the
     * program as GameMouths sets it, and the program is put back to a shut mouth after it.
     */
    static void draw(ModelMesh mesh, Shader shader, AnimatedModel model, AnimatedModel.AnimatedModelInstanceRenderData part) {
        Mouths.Shape mouth = model == shown && Mouths.on() && GameMouths.taken() && shader != null && GameMouths.PROGRAM.equals(shader.getName()) && !shader.isInstanced()
                ? Microphones.mouth(0) : Mouths.Shape.SHUT;
        ModelInstance instance = part.modelInstance;
        Model drawn = instance.model;
        float head = mouth.moving() && !drawn.isStatic ? CharacterDraw.headBone(drawn) : -1f;
        if (head < 0f) {
            mesh.Draw(shader);
            return;
        }
        Texture texture = instance.tex != null ? instance.tex : drawn.tex;
        boolean body = texture != null
                && CharacterDraw.wearOf(drawn.name, texture.getName(), texture.getClass().getSimpleName().equals("CharacterSmartTexture")) == CharacterDraw.BODY;
        ShaderProgram program = shader.getShaderProgram();
        program.setValue("uMouth", mouth.open());
        program.setValue("uLips", mouth.lips());
        program.setValue("uHeadBone", head);
        program.setValue("uMouthGap", body ? mouth.parted() : 0f);
        try {
            mesh.Draw(shader);
        } finally {
            program.setValue("uMouth", 0f);
            program.setValue("uLips", 0f);
            program.setValue("uHeadBone", -1f);
            program.setValue("uMouthGap", 0f);
        }
        if (DevBuild.ON && body && System.nanoTime() - mouthSaid > SAID_EVERY_NANOS) {
            mouthSaid = System.nanoTime();
            Log.info(String.format(Locale.ROOT, "the character on the Customise Character screen is drawn with its mouth open %.2f, lips %.2f",
                    mouth.open(), mouth.lips()));
        }
    }
}
