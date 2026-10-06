// CameraUnlock.ini: the committed file is the table's fresh render, a first start creates it,
// the tracking mode, the yaw mode and the aim mode save, and End's toggle never does.
//
// `--render-config <path>` writes the committed file instead (pixi run render-config).

#include "config.h"

#include <windows.h>

#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <filesystem>
#include <fstream>
#include <iterator>
#include <stdexcept>
#include <string>
#include <vector>

using namespace pzht;

namespace {

namespace cfg = cameraunlock::config;
namespace fs = std::filesystem;

int g_failures = 0;

void Check(bool ok, const std::string& what) {
    if (!ok) {
        std::printf("FAIL: %s\n", what.c_str());
        ++g_failures;
    }
}

std::string ReadBytes(const fs::path& path) {
    std::ifstream in(path, std::ios::binary);
    if (!in) throw std::runtime_error("cannot read " + path.string());
    return std::string(std::istreambuf_iterator<char>(in), std::istreambuf_iterator<char>());
}

void WriteBytes(const fs::path& path, const std::string& bytes) {
    std::ofstream out(path, std::ios::binary | std::ios::trunc);
    if (!out) throw std::runtime_error("cannot write " + path.string());
    out.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
}

// The file a first start creates.
std::string Rendered() {
    return cfg::RenderCanonicalFresh(MakeConfigTable(), {kConfigDisplayName});
}

std::string Committed() {
    return ReadBytes(fs::path(PZHT_COMMITTED_CONFIG));
}

bool Holds(const std::string& bytes, const std::string& line) {
    return bytes.find("\r\n" + line + "\r\n") != std::string::npos;
}

// A scratch game folder, and a Defaults.ini of its own beside it that the first load creates.
struct Scratch {
    fs::path root;
    fs::path game;
    fs::path defaults;

    explicit Scratch(const std::wstring& name) {
        wchar_t temp[MAX_PATH];
        GetTempPathW(MAX_PATH, temp);
        root = fs::path(temp) / (L"project-zomboid-config-tests-" + std::to_wstring(GetCurrentProcessId())) / name;
        fs::remove_all(root);
        game = root / L"ProjectZomboid";
        fs::create_directories(game);
        fs::create_directories(root / L"user");
        defaults = root / L"user" / L"CameraUnlock" / L"Defaults.ini";
    }
    ~Scratch() {
        std::error_code ignored;
        fs::remove_all(root, ignored);
    }
    Scratch(const Scratch&) = delete;
    Scratch& operator=(const Scratch&) = delete;

    cfg::ConfigOwnerOptions<Config> Options() const {
        return MakeConfigOwnerOptions(ConfigPath().wstring(), cfg::DefaultsFile::At(defaults.wstring()));
    }
    fs::path ConfigPath() const { return game / kConfigFileName; }
};

std::vector<std::string> Listing(const fs::path& dir) {
    std::vector<std::string> names;
    for (const fs::directory_entry& entry : fs::directory_iterator(dir)) names.push_back(entry.path().filename().string());
    std::sort(names.begin(), names.end());
    return names;
}

std::string AllValues(const Config& c) {
    return cfg::RenderCanonical(MakeConfigTable(), c, {kConfigDisplayName});
}

void TestCommittedConfigIsRendered() {
    Check(Committed() == Rendered(), "config/CameraUnlock.ini is the table's fresh render (pixi run render-config)");
}

void TestFirstStartCreatesTheCommittedFile() {
    const Scratch s(L"first-start");
    const auto loaded = cfg::ConfigOwner<Config>(s.Options()).Load();
    Check(loaded.status == cfg::ConfigLoadStatus::Created, "a first start with no file is Created");
    Check(ReadBytes(s.ConfigPath()) == Committed(), "a first start creates config/CameraUnlock.ini's bytes as CameraUnlock.ini");
    Check(Listing(s.game) == std::vector<std::string>{"CameraUnlock.ini"}, "a first start creates CameraUnlock.ini and nothing else");
    Check(fs::exists(s.defaults), "a first start creates Defaults.ini");
    const auto table = MakeConfigTable();
    Check(AllValues(loaded.config) == AllValues(table.defaults()), "a first start runs on the built-in values");
    Check(loaded.config.udp_port == 4242, "a first start listens on 4242");
    Check(loaded.config.rotation_enabled && loaded.config.position_enabled, "a first start tracks rotation and position");
    Check(loaded.config.collision_enabled, "a first start stops a lean at walls");
    Check(loaded.config.lean_clamp.skin == 0.12f, "a first start holds the view 0.12 of a tile off a wall");
    Check(loaded.config.true_free_look_key_name == "Insert, Ctrl+Shift+U", "a first start binds Insert and Ctrl+Shift+U to the aim mode");

    const auto again = cfg::ConfigOwner<Config>(s.Options()).Load();
    Check(again.status == cfg::ConfigLoadStatus::Canonical && again.diagnostics.empty(),
          "the created file reads back as canonical with nothing to report");
    Check(ReadBytes(s.ConfigPath()) == Committed(), "and a second start rewrites nothing");
}

void TestTheModesSave() {
    const Scratch s(L"save");
    WriteBytes(s.ConfigPath(), Committed());

    cfg::ConfigOwner<Config> owner(s.Options());
    Check(owner.Load().status == cfg::ConfigLoadStatus::Canonical, "the committed file loads as canonical");
    const std::string defaultsBefore = ReadBytes(s.defaults);

    Check(owner.Save([](Config& c) { c.position_enabled = false; }).status == cfg::ConfigSaveStatus::Saved,
          "the tracking mode saves");
    std::string bytes = ReadBytes(s.ConfigPath());
    Check(Holds(bytes, "RotationEnabled=true") && Holds(bytes, "PositionEnabled=false"),
          "a tracking mode change writes both rows of the pair");

    Check(owner.Save([](Config& c) { c.world_space_yaw = false; }).status == cfg::ConfigSaveStatus::Saved,
          "the yaw mode saves");
    Check(Holds(ReadBytes(s.ConfigPath()), "WorldSpaceYaw=false"), "a yaw mode change writes WorldSpaceYaw");

    Check(owner.Save([](Config& c) {
              c.true_free_look = true;
              c.free_look_marker = true;
          }).status == cfg::ConfigSaveStatus::Saved,
          "the aim mode saves");
    bytes = ReadBytes(s.ConfigPath());
    Check(Holds(bytes, "TrueFreeLook=true") && Holds(bytes, "FreeLookMarker=true"),
          "an aim mode change writes the rows it changes");
    Check(Holds(bytes, "UdpPort=default") && Holds(bytes, "LightMultiplier=default"),
          "a save leaves the rows it did not change on default");

    const auto reloaded = cfg::ConfigOwner<Config>(s.Options()).Load();
    Check(reloaded.status == cfg::ConfigLoadStatus::Canonical && reloaded.diagnostics.empty(),
          "the saved file reads back as canonical with nothing to report");
    Check(reloaded.config.rotation_enabled && !reloaded.config.position_enabled && !reloaded.config.world_space_yaw &&
              reloaded.config.true_free_look && reloaded.config.free_look_marker && !reloaded.config.stock_sights,
          "the next start runs on the saved modes");
    Check(ReadBytes(s.defaults) == defaultsBefore, "a save leaves Defaults.ini as it was");
}

void TestFieldOfViewIsReadAgainWhileTheGameRuns() {
    const Scratch s(L"field-of-view");
    cfg::ConfigOwner<Config> owner(s.Options());
    const auto loaded = owner.Load();
    Check(loaded.config.field_of_view == kDefaultFieldOfView, "a first start draws 65 degrees from bottom to top");
    Check(Holds(ReadBytes(s.ConfigPath()), "FieldOfView=65.0"), "and the file it creates says so");
    Check(!owner.FileChanged(), "a file nobody has edited has not changed");

    const std::string created = ReadBytes(s.ConfigPath());
    const auto edit = [&](const std::string& value) {
        std::string bytes = created;
        const std::string row = "FieldOfView=65.0";
        bytes.replace(bytes.find(row), row.size(), "FieldOfView=" + value);
        WriteBytes(s.ConfigPath(), bytes);
        // The watch goes by the file's write time, and two writes can land on one tick of it.
        fs::last_write_time(s.ConfigPath(), fs::last_write_time(s.ConfigPath()) + std::chrono::seconds(2));
    };

    edit("90");
    Check(owner.FileChanged(), "an edit is seen");
    auto reloaded = owner.Reload();
    Check(reloaded.status == cfg::ConfigReloadStatus::Applied && reloaded.config && reloaded.config->field_of_view == 90.0f,
          "and the reload gives the new field of view");
    Check(reloaded.config && reloaded.config->udp_port == 4242, "with the rows nobody touched as they were");

    for (const char* refused : {"39", "111", "wide"}) {
        edit(refused);
        reloaded = owner.Reload();
        Check(reloaded.config && reloaded.config->field_of_view == kDefaultFieldOfView && !reloaded.diagnostics.empty(),
              std::string("a field of view of ") + refused + " is refused, reported and read as 65");
    }
}

void TestContentRowsAreOnAndReadAgainWhileTheGameRuns() {
    const Scratch s(L"content");
    cfg::ConfigOwner<Config> owner(s.Options());
    const auto loaded = owner.Load();
    const Config& c = loaded.config;
    Check(c.own_weapons && c.own_furniture && c.own_pictures && c.own_plants && c.own_textures && c.own_buildings && c.own_zombies && c.own_people,
          "a first start shows everything the mod made");
    const std::string created = ReadBytes(s.ConfigPath());
    for (const char* row : {"OwnWeapons", "OwnFurniture", "OwnPictures", "OwnPlants", "OwnTextures", "OwnBuildings", "OwnZombies", "OwnPeople"}) {
        Check(Holds(created, std::string(row) + "=true"), std::string("and the file it creates holds ") + row);
    }

    std::string bytes = created;
    const std::string row = "OwnWeapons=true";
    bytes.replace(bytes.find(row), row.size(), "OwnWeapons=false");
    WriteBytes(s.ConfigPath(), bytes);
    // The watch goes by the file's write time, and two writes can land on one tick of it.
    fs::last_write_time(s.ConfigPath(), fs::last_write_time(s.ConfigPath()) + std::chrono::seconds(2));
    Check(owner.FileChanged(), "an edit is seen");
    const auto reloaded = owner.Reload();
    Check(reloaded.status == cfg::ConfigReloadStatus::Applied && reloaded.config && !reloaded.config->own_weapons,
          "and the reload gives the row switched off");
    Check(reloaded.config && reloaded.config->own_furniture && reloaded.config->own_pictures && reloaded.config->own_plants &&
              reloaded.config->own_textures && reloaded.config->own_buildings && reloaded.config->own_zombies && reloaded.config->own_people,
          "with the rows nobody touched still on");
}

void TestQualityIsAutomaticAndReadAgainWhileTheGameRuns() {
    const Scratch s(L"quality");
    cfg::ConfigOwner<Config> owner(s.Options());
    const auto loaded = owner.Load();
    Check(loaded.config.quality == pzht::Quality::Automatic, "a first start finds its own level");
    std::string bytes = ReadBytes(s.ConfigPath());
    const std::string row = "Quality=Automatic";
    Check(Holds(bytes, row), "and the file it creates says so");
    bytes.replace(bytes.find(row), row.size(), "Quality=low");
    WriteBytes(s.ConfigPath(), bytes);
    // The watch goes by the file's write time, and two writes can land on one tick of it.
    fs::last_write_time(s.ConfigPath(), fs::last_write_time(s.ConfigPath()) + std::chrono::seconds(2));
    Check(owner.FileChanged(), "an edit is seen");
    const auto reloaded = owner.Reload();
    Check(reloaded.status == cfg::ConfigReloadStatus::Applied && reloaded.config && reloaded.config->quality == pzht::Quality::Low,
          "and the reload gives the level asked for, however it is capitalised");
}

void TestEndNeverSaves() {
    const Scratch s(L"end");
    const std::string committed = Committed();
    WriteBytes(s.ConfigPath(), committed);

    cfg::ConfigOwner<Config> owner(s.Options());
    Check(owner.Load().status == cfg::ConfigLoadStatus::Canonical, "the committed file loads as canonical");

    bool refused = false;
    try {
        owner.Save([](Config& c) { c.enable_on_startup = false; });
    } catch (const std::logic_error&) {
        refused = true;
    }
    Check(refused, "EnableOnStartup is not Writable, so the End toggle cannot persist");
    Check(ReadBytes(s.ConfigPath()) == committed, "a refused save writes nothing");
}

}  // namespace

int main(int argc, char** argv) {
    try {
        if (argc == 3 && std::strcmp(argv[1], "--render-config") == 0) {
            WriteBytes(argv[2], Rendered());
            return 0;
        }
        if (argc != 1) {
            std::printf("usage: %s [--render-config <path>]\n", argv[0]);
            return 2;
        }

        TestFirstStartCreatesTheCommittedFile();
        TestCommittedConfigIsRendered();
        TestTheModesSave();
        TestFieldOfViewIsReadAgainWhileTheGameRuns();
        TestContentRowsAreOnAndReadAgainWhileTheGameRuns();
        TestQualityIsAutomaticAndReadAgainWhileTheGameRuns();
        TestEndNeverSaves();
    } catch (const std::exception& e) {
        std::printf("FAIL: %s\n", e.what());
        return 1;
    }

    if (g_failures == 0) {
        std::printf("config tests: all passed\n");
        return 0;
    }
    std::printf("config tests: %d failure(s)\n", g_failures);
    return 1;
}
