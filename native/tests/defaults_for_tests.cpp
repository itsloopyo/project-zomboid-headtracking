// Built into the test DLL in place of src/defaults_per_user.cpp, so the Java tests go through the
// bridge without reading or creating the developer's own Defaults.ini.

#include "bridge.h"
#include "config.h"

#include <string>

namespace {
std::wstring g_defaults_path;
}

// Names the Defaults.ini the next PzhtConfigLoad reads. A load before it throws.
PZHT_EXPORT void PzhtTestSetDefaultsPath(const wchar_t* path) {
    g_defaults_path = path;
}

namespace pzht {

cameraunlock::config::DefaultsFile BridgeDefaults() {
    return cameraunlock::config::DefaultsFile::At(g_defaults_path);
}

}  // namespace pzht
