cmake_minimum_required(VERSION 3.20)
project(ProjectZomboidHeadTracking LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_definitions(-DWIN32_LEAN_AND_MEAN -DNOMINMAX -D_CRT_SECURE_NO_WARNINGS)
# The C++ runtime is linked in, so the DLL loads on a machine without the redistributable.
set(CMAKE_MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")

set(CAMERAUNLOCK_BUILD_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(../cameraunlock-core/cpp ${CMAKE_BINARY_DIR}/cameraunlock-core)

# What the Java agent loads: cameraunlock-core's config owner and hotkey poller.
add_library(ProjectZomboidHeadTracking SHARED
    src/bridge.cpp
    src/config.cpp
    src/defaults_per_user.cpp
)
target_link_libraries(ProjectZomboidHeadTracking PRIVATE cameraunlock)
target_compile_options(ProjectZomboidHeadTracking PRIVATE /W4 /WX /permissive-)

# The same bridge reading a Defaults.ini its caller names, with core's zoom compensation beside
# it, for the Java tests. Never packaged.
add_library(ProjectZomboidHeadTrackingTest SHARED
    src/bridge.cpp
    src/config.cpp
    tests/defaults_for_tests.cpp
    tests/zoom_for_tests.cpp
)
target_include_directories(ProjectZomboidHeadTrackingTest PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_link_libraries(ProjectZomboidHeadTrackingTest PRIVATE cameraunlock)
target_compile_options(ProjectZomboidHeadTrackingTest PRIVATE /W4 /WX /permissive-)

# `config_tests --render-config <path>` is how `pixi run render-config` rewrites the committed file.
enable_testing()
add_executable(config_tests
    tests/config_tests.cpp
    src/config.cpp
)
target_include_directories(config_tests PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/src)
target_compile_definitions(config_tests PRIVATE
    PZHT_COMMITTED_CONFIG="${CMAKE_CURRENT_SOURCE_DIR}/../config/CameraUnlock.ini")
target_link_libraries(config_tests PRIVATE cameraunlock)
target_compile_options(config_tests PRIVATE /W4 /WX /permissive-)
add_test(NAME config_tests COMMAND config_tests)
