package com.cameraunlock.zomboid;

import java.lang.reflect.Field;
import java.util.List;
import zombie.core.skinnedmodel.advancedanimation.AnimLayer;
import zombie.core.skinnedmodel.advancedanimation.LiveAnimNode;

/**
 * How a character goes over into its ragdoll.
 *
 * The game moves a character into a ragdoll as into any other animation: the ragdoll's node
 * blends in over its own blend time (0.2 s on the falls a shot ends in) while what was playing
 * blends out over its own (0.35 s from a standing zombie's idle). The ragdoll starts from that
 * same pose, moving as it moved, so the blend hides nothing but the physics. Measured on a zombie
 * killed by a shotgun at 3 tiles: 72 ms after the hit the ragdoll weighed 0.20 and the idle 0.91
 * in the pose drawn, and with the ragdoll taken in at once the idle still weighed 0.59 at 141 ms.
 * The body is drawn standing on for a third of a second and the shot looks like an animation.
 * Taken over at once both ways, the body reacts on the frame it is hit. A car that knocks a zombie
 * down sends it into a ragdoll the same way. Only while someone plays in first person: the game's
 * own view stays as the game has it.
 */
public final class Ragdolls {
    private Ragdolls() {}

    /** Called with how long the game would blend into an animation node as it starts. */
    public static float blendIn(float stock, LiveAnimNode node) {
        return node.getSourceNode().isRagdoll() && anyoneInFirstPerson() ? 0f : stock;
    }

    /** Called with how long the game would blend an animation node out as something else takes over. */
    public static float blendOut(float stock, LiveAnimNode node) {
        return !node.getSourceNode().isRagdoll() && anyoneInFirstPerson() && ragdollIn(node) ? 0f : stock;
    }

    private static Field nodeLayer;

    /** Whether a ragdoll has come in among the animations of the node's own layer. */
    private static boolean ragdollIn(LiveAnimNode node) {
        List<LiveAnimNode> live = layerOf(node).getLiveAnimNodes();
        for (int i = 0; i < live.size(); i++) {
            if (live.get(i).getSourceNode().isRagdoll()) {
                return true;
            }
        }
        return false;
    }

    private static AnimLayer layerOf(LiveAnimNode node) {
        try {
            if (nodeLayer == null) {
                nodeLayer = LiveAnimNode.class.getDeclaredField(Links.NODE_LAYER.name());
                nodeLayer.setAccessible(true);
            }
            return (AnimLayer) nodeLayer.get(node);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("the layer an animation node is in could not be read", failure);
        }
    }

    static boolean anyoneInFirstPerson() {
        for (int i = 0; i < View.COUNT; i++) {
            View view = View.of(i);
            if (view != null && view.firstPerson.active()) {
                return true;
            }
        }
        return false;
    }
}
