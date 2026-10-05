// Built into the test DLL only: cameraunlock-core's zoom compensation, for the Java test that
// holds ZoomCompensation.java to it. Core has no Java, so the mod's copy is a port.

#include "bridge.h"

#include <cameraunlock/camera/zoom_compensation.h>

PZHT_EXPORT float PzhtTestFovZoomFactor(float tan_half_fov, float tan_half_fov_base) {
    return cameraunlock::camera::FovZoomFactor(tan_half_fov, tan_half_fov_base);
}

PZHT_EXPORT float PzhtTestScaleAngleForZoom(float angle_deg, float factor) {
    return cameraunlock::camera::ScaleAngleForZoom(angle_deg, factor);
}

// `lean` is three floats, rewritten in place. `view_axis` is three floats.
PZHT_EXPORT void PzhtTestScaleLeanForZoom(float* lean, const float* view_axis, float factor) {
    const cameraunlock::math::Vec3 scaled = cameraunlock::camera::ScaleLeanForZoom(
        {lean[0], lean[1], lean[2]}, {view_axis[0], view_axis[1], view_axis[2]}, factor);
    lean[0] = scaled.x;
    lean[1] = scaled.y;
    lean[2] = scaled.z;
}
