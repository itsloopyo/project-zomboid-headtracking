package com.cameraunlock.zomboid;

/**
 * How head tracking behaves while a firearm's sights are up, in the order the key cycles.
 * It is kept as three settings, TrueFreeLook, FreeLookMarker and StockSights.
 *
 * cameraunlock-core's AimMode has a fourth, free look with no marker. The marker here is the
 * game's own reticle, which is always drawn where the shot lands, so that mode would be this
 * one's free look again and is left out. A file that holds it is read as free look.
 */
enum AimMode {
    /** A lean never takes the eye off the sights: the weapon and the shot come with the eye. */
    SIGHTS_LOCKED("Aim mode: sights locked"),
    /** The weapon stays put and the head moves round it, with the game's reticle where the shot will land. */
    FREE_LOOK("Aim mode: free look with marker"),
    /** With the sights up the head's yaw, pitch and lean ease out and only its roll stays. */
    STOCK_SIGHTS("Aim mode: stock sights");

    final String label;

    AimMode(String label) {
        this.label = label;
    }

    /** Stock sights wins over free look. */
    static AimMode decode(boolean trueFreeLook, boolean stockSights) {
        if (stockSights) {
            return STOCK_SIGHTS;
        }
        return trueFreeLook ? FREE_LOOK : SIGHTS_LOCKED;
    }

    AimMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Whether the lean stays on the view through the aim. Stock sights is not free look. */
    boolean isFreeLook() {
        return this == FREE_LOOK;
    }

    /** Whether the head's yaw, pitch and lean are eased out this frame, which is what the fade of stock sights is fed. */
    boolean stockSightsEngaged(boolean aiming) {
        return aiming && this == STOCK_SIGHTS;
    }
}
