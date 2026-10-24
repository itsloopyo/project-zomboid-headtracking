package com.cameraunlock.zomboid;

import zombie.network.GameClient;
import zombie.network.ServerOptions;

/**
 * What changes when the game is joined to a server, where other people play beside the
 * viewer and none of them may have this mod.
 *
 * The view, the head and the keys are the viewer's own and play there as they do alone.
 * What another player could lose a fight to is held to what the game itself gives everyone,
 * and only where there can be a fight: on a server that allows players to hurt each other.
 * That is how other players are seen and how the aim lands; zombies, vehicles with no
 * other player in them, rooms and the land are shown as they are alone. Among friends on a
 * server without PvP nothing is held back.
 */
final class Online {
    private Online() {}

    /** Whether this game is joined to a server, a co-op host's own included. */
    static boolean joined() {
        return GameClient.client;
    }

    /**
     * Whether the viewer can fight another player here: the server allows it. The viewer's
     * own safety does not settle it. The game refuses a hit between two players only while
     * both have their safety on (CombatManager.checkPVP), so a viewer with theirs on can
     * still be hurt by, and hurt, a player with theirs off.
     */
    static boolean contested() {
        return joined() && ServerOptions.getInstance().pvp.getValue();
    }

    /** Whether a character is shown by the picture's own rules: anyone but another player where players can fight. */
    static boolean shownAsAlone(boolean player) {
        return !player || !contested();
    }

    /**
     * Whether another player is drawn as a zombie is: wherever they stand before the eye within the
     * draw distance, whatever the game's own screen and its sight for players say (the cone, the
     * server's HidePlayersBehindYou, a sneak unseen). Anywhere players cannot fight. A player the
     * game makes invisible to everyone, an admin's invisibility or ghost mode, stays the game's.
     */
    static boolean drawnAsFarAsZombies(boolean invisible) {
        return !invisible && !contested();
    }

    /**
     * Whether a lean may carry the weapon, and the start of the shot with it, while a firearm's
     * sights are up. Alone it does: the round leaves from the eye the player looks through.
     * Where players fight, the shot would start from somewhere no other player sees a weapon,
     * round a corner the body has not passed, so the lean across the aim eases out as the
     * sights come up.
     */
    static boolean leanCarriesTheShot() {
        return !contested();
    }

    /**
     * Whether a first person shot hits the part of the body the aim is on. The game finds the part
     * by a ray down through the reticle from its own overhead camera, which in first person mostly
     * finds none and rolls one. Against a player who could be fought that roll is the game's to
     * make, as it is for everyone shooting them, so a player is no likelier to be hit in the head
     * by someone with this mod.
     */
    static boolean aimPicksThePartOf(boolean player) {
        return !player || !contested();
    }

    /**
     * Whether a weapon made for both hands is taken back in both when the left hand lets go of
     * something beside it (Regrip). Not where players can fight: there the grip is the player's
     * to ask for, as it is for everyone.
     */
    static boolean regrips() {
        return !contested();
    }

    /**
     * Whether first person shots wound a zombie's limbs: a shot leg slows it and then drops it to
     * crawl, a shot arm hangs. Only alone. On a server the zombies are the server's, a co-op host's
     * included, which runs apart from the host's game and without this mod, and a zombie that one
     * player's game had made to limp would walk on for everyone else.
     */
    static boolean woundsTell() {
        return !joined();
    }
}
