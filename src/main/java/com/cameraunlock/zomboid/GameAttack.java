package com.cameraunlock.zomboid;

import java.util.List;
import zombie.AttackType;
import zombie.ai.states.SwipeStatePlayer;
import zombie.characters.IsoPlayer;
import zombie.core.skinnedmodel.advancedanimation.AdvancedAnimator;
import zombie.core.skinnedmodel.advancedanimation.AnimEvent;
import zombie.core.skinnedmodel.advancedanimation.LiveAnimNode;
import zombie.core.skinnedmodel.animation.AnimationTrack;
import zombie.inventory.types.WeaponType;

/** What the game says of an attack with what is held: which way it hits, how far through it is, and when it lands. Logic thread. */
final class GameAttack {
    private GameAttack() {}

    /** The game's name for the moment of its animation at which an attack lands. */
    private static final String LANDS = "AttackCollisionCheck";

    /**
     * How far through its animation of an attack the game is, and how far through that the attack lands, each
     * 0 to 1. The animation is the one it is playing that has a moment of landing. Attacks made one after
     * another have the last one's still fading as the next begins, and of the two it is the one less far through.
     * The game times an animation's moments by its primary track.
     *
     * Other things the game animates have a moment by the same name, a window being broken for one, and
     * they are not attacks: only the state the game makes attacks in counts.
     *
     * @return false while the game plays no attack
     */
    static boolean progress(IsoPlayer player, float[] out) {
        if (!player.isCurrentState(SwipeStatePlayer.instance())) {
            return false;
        }
        AdvancedAnimator animator = player.getAdvancedAnimator();
        boolean found = false;
        for (int layer = -1; layer < animator.getSubLayerCount(); layer++) {
            List<LiveAnimNode> nodes = (layer < 0 ? animator.getRootLayer() : animator.getSubLayerAt(layer)).getLiveAnimNodes();
            for (int node = 0; node < nodes.size(); node++) {
                LiveAnimNode live = nodes.get(node);
                AnimationTrack primary = null;
                for (int track = 0; track < live.getMainAnimationTracksCount() && primary == null; track++) {
                    if (live.getMainAnimationTrackAt(track).isPrimary) {
                        primary = live.getMainAnimationTrackAt(track);
                    }
                }
                if (primary == null || found && primary.getCurrentTimeFraction() >= out[0]) {
                    continue;
                }
                List<AnimEvent> events = live.getSourceNode().events;
                for (int event = 0; event < events.size(); event++) {
                    if (LANDS.equalsIgnoreCase(events.get(event).eventName)) {
                        found = true;
                        out[0] = primary.getCurrentTimeFraction();
                        out[1] = events.get(event).timePc;
                        break;
                    }
                }
            }
        }
        return found;
    }

    /** The way the game has chosen to hit with what is held. */
    static int way(IsoPlayer player) {
        if (player.isDoShove()) {
            return ViewModelMath.SHOVED;
        }
        if ("true".equalsIgnoreCase(player.GetVariable("AimFloorAnim"))) {
            return ViewModelMath.FLOORED;
        }
        // A knife is only ever stabbed with, whichever attack the game drew for it. A spear has an overhead blow of its own.
        WeaponType type = WeaponType.getWeaponType(player);
        if (type == WeaponType.KNIFE) {
            return ViewModelMath.THRUST;
        }
        AttackType how = player.getAttackType();
        if (how == AttackType.OVERHEAD) {
            return ViewModelMath.CHOPPED;
        }
        if (how == AttackType.UPPERCUT) {
            return ViewModelMath.RAISED;
        }
        return type == WeaponType.SPEAR ? ViewModelMath.THRUST : ViewModelMath.SWUNG;
    }
}
