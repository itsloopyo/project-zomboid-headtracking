package com.cameraunlock.zomboid;

/**
 * How head tracking behaves while a firearm's sights are up, in the order the key cycles.
 * It is kept as three settings, TrueFreeLook, FreeLookMarker and StockSights.
 *
 * The same model as cameraunlock-core's AimMode, which exists in C++ and C# only.
 */
enum AimMode {
    /** A lean never takes the eye off the sights: the weapon and the shot come with the eye. */
    SIGHTS_LOCKED("Aim mode: sights locked"),
    /** The weapon stays put and the head moves round it, with a mark where the shot will land. */
    FREE_LOOK_MARKER("Aim mode: free look with marker"),
    /** The same with no mark. */
    TRUE_FREE_LOOK("Aim mode: true free look"),
    /** With the sights up the head's yaw, pitch and lean ease out and only its roll stays. */
    STOCK_SIGHTS("Aim mode: stock sights");

    final String label;

    AimMode(String label) {
        this.label = label;
    }

    /** Stock sights wins over the other two, and the marker means nothing without free look. */
    static AimMode decode(boolean trueFreeLook, boolean freeLookMarker, boolean stockSights) {
        if (stockSights) {
            return STOCK_SIGHTS;
        }
        if (!trueFreeLook) {
            return SIGHTS_LOCKED;
        }
        return freeLookMarker ? FREE_LOOK_MARKER : TRUE_FREE_LOOK;
    }

    boolean trueFreeLook() {
        return isFreeLook();
    }

    boolean freeLookMarker() {
        return this == FREE_LOOK_MARKER;
    }

    boolean stockSights() {
        return this == STOCK_SIGHTS;
    }

    AimMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** Whether the lean stays on the view through the aim. Stock sights is not free look. */
    boolean isFreeLook() {
        return this == FREE_LOOK_MARKER || this == TRUE_FREE_LOOK;
    }

    /** Whether the head's yaw, pitch and lean are eased out this frame, which is what the fade of stock sights is fed. */
    boolean stockSightsEngaged(boolean aiming) {
        return aiming && this == STOCK_SIGHTS;
    }
}
