#pragma once

#include <cameraunlock/config/config_owner.h>
#include <cameraunlock/config/config_table.h>
#include <cameraunlock/config/defaults_file.h>
#include <cameraunlock/config/head_tracking_config.h>

#include <cstdint>
#include <string>

namespace pzht {

inline constexpr const char* kConfigFileName = "CameraUnlock.ini";
// The game's name as cameraunlock-core's data/games.json spells it.
inline constexpr const char* kConfigDisplayName = "Project Zomboid";

// The first person view's field of view in degrees, from the bottom of the picture to the top.
// FieldOfView.java holds the same default.
inline constexpr float kDefaultFieldOfView = 65.0f;
inline constexpr float kMinFieldOfView = 40.0f;
inline constexpr float kMaxFieldOfView = 110.0f;

// The graphics mode: how much of the mod's own picture is drawn. Quality.java holds the same numbers.
enum class Quality : std::int32_t { Automatic = 0, Low = 1, Medium = 2, High = 3 };

struct Config : cameraunlock::HeadTrackingConfig {
    float field_of_view = kDefaultFieldOfView;
    Quality quality = Quality::Automatic;
    // What the mod made itself and shows in first person in place of the game's own. Each is
    // a row of [Content], and with a row off the game's own is drawn there.
    bool own_weapons = true;
    bool own_furniture = true;
    bool own_pictures = true;
    bool own_plants = true;
    bool own_textures = true;
    bool own_buildings = true;
    bool own_zombies = true;
    bool own_people = true;

    Config() {
        // Map tiles. It has to exceed the distance from the eye to the corners of the first
        // person view's near plane (WorldScene.NEAR_CORNER_DISTANCE on the Java side).
        lean_clamp.skin = 0.12f;
    }
};

// The rows of CameraUnlock.ini. The tracking mode pair, WorldSpaceYaw and the three aim mode
// rows are Writable: their hotkeys save. End changes the session only. FieldOfView, Quality and
// the rows of [Content] are the mod's own, and the ones read again while the game runs.
cameraunlock::config::ConfigTable<Config> MakeConfigTable();

// The owner's options for the file at `path`. The mod passes DefaultsFile::PerUser() and a test
// a scratch file.
cameraunlock::config::ConfigOwnerOptions<Config> MakeConfigOwnerOptions(const std::wstring& path,
                                                                        cameraunlock::config::DefaultsFile defaults);

// Where Defaults.ini is for the DLL this is linked into: the player's own in the one that ships
// (src/defaults_per_user.cpp), a scratch file in the one the Java tests load
// (tests/defaults_for_tests.cpp).
cameraunlock::config::DefaultsFile BridgeDefaults();

}  // namespace pzht
