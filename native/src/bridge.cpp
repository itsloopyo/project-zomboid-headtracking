#include "bridge.h"

#include "config.h"

#include <cameraunlock/input/hotkey_poller.h>
#include <cameraunlock/input/key_binding_registration.h>
#include <cameraunlock/input/key_bindings.h>

#include <atomic>
#include <cstring>
#include <exception>
#include <functional>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <utility>
#include <vector>

namespace {

namespace cfg = cameraunlock::config;
namespace input = cameraunlock::input;

std::mutex g_mutex;
std::unique_ptr<cfg::ConfigOwner<pzht::Config>> g_owner;
pzht::Config g_loaded;
std::string g_log;
// Never deleted. The JVM ends the process with the poller's thread still running, and a
// destructor that joins it would run while the C runtime is being torn down.
input::HotkeyPoller* g_poller = nullptr;
std::atomic<std::int32_t> g_pressed{0};

void AppendLog(const std::vector<std::string>& lines) {
    for (const std::string& line : lines) g_log.append(line).push_back('\n');
}

std::int32_t Guarded(const char* what, const std::function<std::int32_t()>& body) {
    const std::lock_guard<std::mutex> lock(g_mutex);
    try {
        return body();
    } catch (const std::exception& failure) {
        g_log.append(what).append(" failed: ").append(failure.what()).push_back('\n');
        return -1;
    } catch (...) {
        g_log.append(what).append(" failed: something that is not a std::exception was thrown\n");
        return -1;
    }
}

std::int32_t Save(const char* what, const std::function<void(pzht::Config&)>& change) {
    return Guarded(what, [&] {
        if (!g_owner) throw std::logic_error("PzhtConfigLoad has not run");
        const cfg::ConfigSaveResult saved = g_owner->Save(change);
        AppendLog(saved.log);
        if (saved.status != cfg::ConfigSaveStatus::Saved) g_log.append(saved.reason).push_back('\n');
        return static_cast<std::int32_t>(saved.status);
    });
}

std::int32_t OwnBits(const pzht::Config& c) {
    return (c.own_weapons ? kPzhtOwnWeapons : 0) | (c.own_furniture ? kPzhtOwnFurniture : 0) |
           (c.own_pictures ? kPzhtOwnPictures : 0) | (c.own_plants ? kPzhtOwnPlants : 0) |
           (c.own_textures ? kPzhtOwnTextures : 0) | (c.own_buildings ? kPzhtOwnBuildings : 0) |
           (c.own_zombies ? kPzhtOwnZombies : 0) | (c.own_people ? kPzhtOwnPeople : 0) | (c.own_arms ? kPzhtOwnArms : 0);
}

void Register(const char* row, const std::string& keys, std::int32_t bit) {
    const input::KeyBindingsParseResult parsed = input::ParseKeyBindings(keys);
    if (!parsed.ok()) throw std::runtime_error(std::string(row) + "=" + keys + ": " + parsed.error);
    input::RegisterKeyBindings(*g_poller, parsed.bindings, [bit] { g_pressed.fetch_or(bit); });
}

}  // namespace

std::int32_t PzhtSettingsBytes() {
    return static_cast<std::int32_t>(sizeof(PzhtSettings));
}

std::int32_t PzhtConfigLoad(const wchar_t* path, PzhtSettings* out) {
    return Guarded("loading the settings", [&] {
        g_owner = std::make_unique<cfg::ConfigOwner<pzht::Config>>(
            pzht::MakeConfigOwnerOptions(path, pzht::BridgeDefaults()));
        cfg::ConfigLoadResult<pzht::Config> loaded = g_owner->Load();
        AppendLog(loaded.log);
        g_loaded = std::move(loaded.config);

        const pzht::Config& c = g_loaded;
        out->udp_port = c.udp_port;
        out->data_freshness_ms = c.data_freshness_ms;
        out->enable_on_startup = c.enable_on_startup;
        out->world_space_yaw = c.world_space_yaw;
        out->rotation_enabled = c.rotation_enabled;
        out->position_enabled = c.position_enabled;
        out->true_free_look = c.true_free_look;
        out->stock_sights = c.stock_sights;
        out->collision_enabled = c.collision_enabled;
        out->local_smoothing = c.local_smoothing;
        out->remote_smoothing = c.remote_smoothing;
        out->position_limit_x = c.position.limit_x;
        out->position_limit_y = c.position.limit_y;
        out->position_limit_y_down = c.position.limit_y_down;
        out->position_limit_z = c.position.limit_z;
        out->position_limit_z_back = c.position.limit_z_back;
        out->collision_margin = c.lean_clamp.skin;
        out->collision_release_smoothing = c.lean_clamp.release_smoothing;
        out->light_multiplier = c.light.multiplier;
        out->field_of_view = c.field_of_view;
        out->own = OwnBits(c);
        out->quality = static_cast<std::int32_t>(c.quality);
        out->style = static_cast<std::int32_t>(c.style);
        out->frame_blending = c.frame_blending;
        return static_cast<std::int32_t>(loaded.status);
    });
}

std::int32_t PzhtConfigReloadLive(PzhtLive* out) {
    return Guarded("reading the settings again", [&]() -> std::int32_t {
        if (!g_owner) throw std::logic_error("PzhtConfigLoad has not run");
        if (!g_owner->FileChanged()) return 0;
        const cfg::ConfigReloadResult<pzht::Config> reloaded = g_owner->Reload();
        AppendLog(reloaded.log);
        if (!reloaded.config) return 0;
        out->field_of_view = reloaded.config->field_of_view;
        out->own = OwnBits(*reloaded.config);
        out->quality = static_cast<std::int32_t>(reloaded.config->quality);
        out->style = static_cast<std::int32_t>(reloaded.config->style);
        out->frame_blending = reloaded.config->frame_blending;
        return 1;
    });
}

std::int32_t PzhtConfigSaveTrackingMode(std::int32_t rotation, std::int32_t position) {
    return Save("saving the tracking mode", [=](pzht::Config& c) {
        c.rotation_enabled = rotation != 0;
        c.position_enabled = position != 0;
    });
}

std::int32_t PzhtConfigSaveWorldSpaceYaw(std::int32_t world) {
    return Save("saving the yaw mode", [=](pzht::Config& c) { c.world_space_yaw = world != 0; });
}

std::int32_t PzhtConfigSaveQuality(std::int32_t quality) {
    return Save("saving the graphics mode", [=](pzht::Config& c) { c.quality = static_cast<pzht::Quality>(quality); });
}

std::int32_t PzhtConfigSaveStyle(std::int32_t style) {
    return Save("saving the picture's style", [=](pzht::Config& c) { c.style = static_cast<pzht::Style>(style); });
}

std::int32_t PzhtConfigSaveAimMode(std::int32_t free_look, std::int32_t stock_sights) {
    return Save("saving the aim mode", [=](pzht::Config& c) {
        c.true_free_look = free_look != 0;
        c.free_look_marker = free_look != 0;
        c.stock_sights = stock_sights != 0;
    });
}

std::int32_t PzhtHotkeysStart() {
    return Guarded("starting the hotkeys", [] {
        if (!g_owner) throw std::logic_error("PzhtConfigLoad has not run");
        if (g_poller != nullptr) throw std::logic_error("the hotkeys are already started");
        g_poller = new input::HotkeyPoller();
        Register("ToggleKey", g_loaded.toggle_key_name, kPzhtToggle);
        Register("CycleTrackingModeKey", g_loaded.cycle_tracking_mode_key_name, kPzhtCycleTrackingMode);
        Register("YawModeKey", g_loaded.yaw_mode_key_name, kPzhtYawMode);
        Register("TrueFreeLookKey", g_loaded.true_free_look_key_name, kPzhtAimMode);
        Register("FirstPersonKey", g_loaded.first_person_key_name, kPzhtFirstPerson);
        Register("CycleQualityKey", g_loaded.cycle_quality_key_name, kPzhtCycleQuality);
        Register("CycleStyleKey", g_loaded.cycle_style_key_name, kPzhtCycleStyle);
        if (!g_poller->Start()) throw std::runtime_error("the hotkey poller did not start");
        return 0;
    });
}

std::int32_t PzhtHotkeysTake() {
    return g_pressed.exchange(0);
}

std::int32_t PzhtTakeLog(char* buffer, std::int32_t capacity) {
    const std::lock_guard<std::mutex> lock(g_mutex);
    const std::int32_t length = static_cast<std::int32_t>(g_log.size());
    if (length > 0 && capacity >= length) {
        std::memcpy(buffer, g_log.data(), g_log.size());
        g_log.clear();
    }
    return length;
}
