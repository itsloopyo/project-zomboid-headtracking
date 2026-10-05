#include "config.h"

#include <cameraunlock/config/head_tracking_config_table.h>

#include <utility>

namespace pzht {

cameraunlock::config::ConfigTable<Config> MakeConfigTable() {
    using cameraunlock::config::schema::Concept;
    cameraunlock::config::ConfigTable<Config> table = cameraunlock::config::HeadTrackingConfigTable<Config>(
        {Concept::UdpPort, Concept::EnableOnStartup, Concept::WorldSpaceYaw, Concept::RotationEnabled,
         Concept::DataFreshnessMs, Concept::LocalSmoothing, Concept::RemoteSmoothing, Concept::PositionEnabled,
         Concept::TrueFreeLook, Concept::FreeLookMarker, Concept::StockSights, Concept::PositionLimitX,
         Concept::PositionLimitY, Concept::PositionLimitYDown, Concept::PositionLimitZ, Concept::PositionLimitZBack,
         Concept::CollisionEnabled, Concept::CollisionMargin, Concept::CollisionReleaseSmoothing, Concept::ToggleKey,
         Concept::CycleTrackingModeKey, Concept::YawModeKey, Concept::TrueFreeLookKey, Concept::LightMultiplier});
    table.Select(Concept::WorldSpaceYaw).Writable()
        .Select(Concept::RotationEnabled).Writable()
        .Select(Concept::PositionEnabled).Writable()
        .Select(Concept::TrueFreeLook).Writable()
        .Select(Concept::FreeLookMarker).Writable()
        .Select(Concept::StockSights).Writable()
        .Select(Concept::CollisionMargin)
        .Comment("How far the view is held off a wall when you lean into it, in map tiles.");
    return table;
}

cameraunlock::config::ConfigOwnerOptions<Config> MakeConfigOwnerOptions(const std::wstring& path,
                                                                        cameraunlock::config::DefaultsFile defaults) {
    cameraunlock::config::ConfigOwnerOptions<Config> options;
    options.path = path;
    options.table = MakeConfigTable();
    options.header.display_name = kConfigDisplayName;
    options.defaults = std::move(defaults);
    return options;
}

}  // namespace pzht
