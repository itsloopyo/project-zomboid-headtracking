package com.cameraunlock.zomboid;

/**
 * The values CameraUnlock.ini holds for this run, as cameraunlock-core's config owner read them.
 * The hotkeys are not here: core's poller has them.
 *
 * @param collisionMargin how far off a wall the eye is held, in tiles. It has to exceed
 *                        WorldScene.NEAR_CORNER_DISTANCE, the distance to the near plane's corners.
 * @param fieldOfView     degrees from the bottom of the first person picture to the top, at the
 *                        start. FieldOfView holds the one in use, which follows the file.
 */
record Settings(int udpPort, int dataFreshnessMs, boolean enableOnStartup, boolean worldSpaceYaw,
        boolean rotationEnabled, boolean positionEnabled, boolean trueFreeLook, boolean stockSights,
        boolean collisionEnabled, float localSmoothing, float remoteSmoothing,
        float positionLimitX, float positionLimitY, float positionLimitYDown, float positionLimitZ,
        float positionLimitZBack, float collisionMargin, float collisionReleaseSmoothing, float lightMultiplier,
        float fieldOfView) {

    /**
     * What a first start with no CameraUnlock.ini and no Defaults.ini runs on. The unit tests
     * run on these, and one of them holds this copy to what core gives.
     */
    static final Settings BUILT_IN = new Settings(4242, 500, true, true, true, true, false, false, true,
            0f, 0.15f, 0.30f, 0.20f, 0.20f, 0.40f, 0.10f, 0.12f, 0.9f, 1.5f, FieldOfView.DEFAULT_DEGREES);

    /** Set once, by HeadTracking.start, before the game's threads run. */
    static Settings current = BUILT_IN;
}
