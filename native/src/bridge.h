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
};

// The bits PzhtHotkeysTake answers with.
inline constexpr std::int32_t kPzhtToggle = 1;
inline constexpr std::int32_t kPzhtCycleTrackingMode = 2;
inline constexpr std::int32_t kPzhtYawMode = 4;
inline constexpr std::int32_t kPzhtAimMode = 8;

PZHT_EXPORT std::int32_t PzhtSettingsBytes();

// Builds the owner of the file at `path` and loads it. `defaults_path` is null in the game, which
// reads the player's own Defaults.ini, and a scratch file in a test. Answers the owner's
// ConfigLoadStatus and fills `out` with the settings the session runs on.
PZHT_EXPORT std::int32_t PzhtConfigLoad(const wchar_t* path, const wchar_t* defaults_path, PzhtSettings* out);

// Each answers the owner's ConfigSaveStatus.
PZHT_EXPORT std::int32_t PzhtConfigSaveTrackingMode(std::int32_t rotation, std::int32_t position);
PZHT_EXPORT std::int32_t PzhtConfigSaveWorldSpaceYaw(std::int32_t world);
PZHT_EXPORT std::int32_t PzhtConfigSaveAimMode(std::int32_t true_free_look, std::int32_t free_look_marker,
                                               std::int32_t stock_sights);

// Puts the four key lists of the loaded file on core's poller and starts it. Answers 0.
PZHT_EXPORT std::int32_t PzhtHotkeysStart();
// The actions whose keys were pressed since the last call, as the bits above.
PZHT_EXPORT std::int32_t PzhtHotkeysTake();

// The lines the calls above left for the game's log, UTF-8, each ending in '\n'. Answers their
// length in bytes. They are copied to `buffer` and forgotten only when `capacity` holds them all.
PZHT_EXPORT std::int32_t PzhtTakeLog(char* buffer, std::int32_t capacity);
