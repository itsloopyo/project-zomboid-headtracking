#pragma once

// What the Java agent calls. The agent cannot run cameraunlock-core, which is C++ and C#, so this
// DLL holds core's config owner and hotkey poller for it. Every function catches what it throws,
// puts the reason in the log TakeLog returns and answers -1: an exception cannot cross into Java.

#include <cstdint>

#define PZHT_EXPORT extern "C" __declspec(dllexport)

// NativeConfig.java reads this by offset. Every member is four bytes, so there is no padding.
struct PzhtSettings {
    std::int32_t udp_port;
    std::int32_t data_freshness_ms;
    std::int32_t enable_on_startup;
    std::int32_t world_space_yaw;
    std::int32_t rotation_enabled;
    std::int32_t position_enabled;
    std::int32_t true_free_look;
    std::int32_t free_look_marker;
    std::int32_t stock_sights;
    std::int32_t collision_enabled;
    float local_smoothing;
    float remote_smoothing;
    float position_limit_x;
    float position_limit_y;
    float position_limit_y_down;
    float position_limit_z;
    float position_limit_z_back;
    float collision_margin;
    float collision_release_smoothing;
    float light_multiplier;
    float field_of_view;
    // The rows of [Content] that are on, as the kPzhtOwn bits below.
    std::int32_t own;
    // The Quality row, as pzht::Quality numbers it.
    std::int32_t quality;
};

// What the rows read again while the game runs hold. NativeConfig.java reads this by offset.
struct PzhtLive {
    float field_of_view;
    std::int32_t own;
    std::int32_t quality;
};

// The bits of PzhtSettings::own and PzhtLive::own. Own.java holds the same numbers.
inline constexpr std::int32_t kPzhtOwnWeapons = 1;
inline constexpr std::int32_t kPzhtOwnFurniture = 2;
inline constexpr std::int32_t kPzhtOwnPictures = 4;
inline constexpr std::int32_t kPzhtOwnPlants = 8;
inline constexpr std::int32_t kPzhtOwnTextures = 16;
inline constexpr std::int32_t kPzhtOwnBuildings = 32;
inline constexpr std::int32_t kPzhtOwnZombies = 64;
inline constexpr std::int32_t kPzhtOwnPeople = 128;

// The bits PzhtHotkeysTake answers with.
inline constexpr std::int32_t kPzhtToggle = 1;
inline constexpr std::int32_t kPzhtCycleTrackingMode = 2;
inline constexpr std::int32_t kPzhtYawMode = 4;
inline constexpr std::int32_t kPzhtAimMode = 8;

PZHT_EXPORT std::int32_t PzhtSettingsBytes();

// Builds the owner of the file at `path` and loads it. Answers the owner's ConfigLoadStatus and
// fills `out` with the settings the session runs on.
PZHT_EXPORT std::int32_t PzhtConfigLoad(const wchar_t* path, PzhtSettings* out);

// FieldOfView, Quality and the rows of [Content] are read again while the game runs. When the file or
// Defaults.ini has changed since the owner last read or wrote it, reads both again, puts what
// they give those rows in `out` and answers 1. Answers 0, and leaves `out` alone, when nothing
// changed or the file cannot be read.
PZHT_EXPORT std::int32_t PzhtConfigReloadLive(PzhtLive* out);

// Each answers the owner's ConfigSaveStatus.
PZHT_EXPORT std::int32_t PzhtConfigSaveTrackingMode(std::int32_t rotation, std::int32_t position);
PZHT_EXPORT std::int32_t PzhtConfigSaveWorldSpaceYaw(std::int32_t world);
PZHT_EXPORT std::int32_t PzhtConfigSaveQuality(std::int32_t quality);
PZHT_EXPORT std::int32_t PzhtConfigSaveAimMode(std::int32_t true_free_look, std::int32_t free_look_marker,
                                               std::int32_t stock_sights);

// Puts the four key lists of the loaded file on core's poller and starts it. Answers 0.
PZHT_EXPORT std::int32_t PzhtHotkeysStart();
// The actions whose keys were pressed since the last call, as the bits above.
PZHT_EXPORT std::int32_t PzhtHotkeysTake();

// The lines the calls above left for the game's log, UTF-8, each ending in '\n'. Answers their
// length in bytes. They are copied to `buffer` and forgotten only when `capacity` holds them all.
PZHT_EXPORT std::int32_t PzhtTakeLog(char* buffer, std::int32_t capacity);
