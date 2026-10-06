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
    table.Local("General", "FieldOfView", &Config::field_of_view,
                cameraunlock::config::FloatCodec(kMinFieldOfView, kMaxFieldOfView),
                "Field of view of the first person view in degrees, from the bottom of the picture to\n"
                "the top: 40 to 110. A wider screen shows more to the sides. Project Zomboid has no\n"
                "field of view setting of its own. Aiming a firearm still narrows the view by the\n"
                "same ratio. Read again while the game runs, so a change shows as soon as the file\n"
                "is saved.");
    table.Local("General", "Quality", &Config::quality,
                cameraunlock::config::EnumCodec<Quality>{{"Automatic", Quality::Automatic},
                                                         {"Low", Quality::Low},
                                                         {"Medium", Quality::Medium},
                                                         {"High", Quality::High}},
                "The graphics mode: how much of the mod's own picture is drawn. F8 goes from one\n"
                "to the next, Low, Medium, High, and saves it here. Low is as near the game's\n"
                "own look as there is: the game's own view is the game's own picture, and first\n"
                "person is drawn with the game's own sprites, models and textures in place of\n"
                "everything the mod made, at three fifths of the pixels each way, sight to 48\n"
                "tiles, smaller shadow maps, and no shading of corners, lit air or depth in\n"
                "surfaces. Medium and High draw both views with everything the mod made. High:\n"
                "the whole picture. Medium: four fifths of the pixels each way, sight to 64\n"
                "tiles and smaller shadow maps. Automatic, which only this file can set: begins\n"
                "at High and draws less, as Medium and then Low do but still with everything the\n"
                "mod made, each time the picture is drawn fewer than 45 times a second for three\n"
                "seconds, until the game is closed. Read again while the game runs.")
        .Writable();
    table.Local("General", "Style", &Config::style,
                cameraunlock::config::EnumCodec<Style>{{"Vivid", Style::Vivid},
                                                       {"Natural", Style::Natural},
                                                       {"Bleak", Style::Bleak}},
                "How the mod's picture is developed, in every graphics mode. F9 goes from one to\n"
                "the next and saves it here. Vivid: strong contrast and full colours, cool in the\n"
                "shade and warm in the sun, with dark corners, glare off the sun and a glow round\n"
                "bright lights. Natural: the colours as they are lit, with no tint, less contrast,\n"
                "and less of the dark corners, the glare and the glow. Bleak: washed out colours\n"
                "and low contrast under a faint drab cast, with no glare off the sun, nearer the\n"
                "colours of the game's own view. The light, the weather and the hour are the same\n"
                "in all three. Read again while the game runs.")
        .Writable();
    table.Local("Content", "OwnWeapons", &Config::own_weapons, cameraunlock::config::BoolCodec(),
                "What the mod made itself and shows in the first person view in place of the game's\n"
                "own. Set a row to false and the game's own is drawn there instead, as the game or\n"
                "another mod has it. These rows are read again while the game runs, so a change\n"
                "shows as soon as the file is saved. The game's own view is never changed.\n"
                "true: the mod's models of the pump shotgun, the hunting rifle and rifle scopes.\n"
                "false: the game's own weapon and scope models, and no picture through the scope.");
    table.Local("Content", "OwnFurniture", &Config::own_furniture, cameraunlock::config::BoolCodec(),
                "true: the mod's models of furniture, appliances, street lamps, clocks and picture\n"
                "frames. false: the game's sprites of them.");
    table.Local("Content", "OwnPictures", &Config::own_pictures, cameraunlock::config::BoolCodec(),
                "true: the mod's photographs, paintings and posters on walls. false: the game's.");
    table.Local("Content", "OwnPlants", &Config::own_plants, cameraunlock::config::BoolCodec(),
                "true: the mod's leaves, tree trunks, boulders, grass, ferns, flowers and fallen\n"
                "leaves. false: the game's sprites of trees, bushes and grass.");
    table.Local("Content", "OwnTextures", &Config::own_textures, cameraunlock::config::BoolCodec(),
                "true: the mod's textures of tarmac, gravel, earth, lawn, wood, boards, roofing,\n"
                "paint, carpet, cloth and wallpaper. false: the colours of the game's sprites alone.");
    table.Local("Content", "OwnBuildings", &Config::own_buildings, cameraunlock::config::BoolCodec(),
                "true: windows, doors and curtains built by the mod, and the outside of a wall\n"
                "finished as its building is. false: the game's sprites of them, on both sides of\n"
                "the wall.");
    table.Local("Content", "OwnZombies", &Config::own_zombies, cameraunlock::config::BoolCodec(),
                "true: the mod's own skins for zombies near the eye, each with a lifelike face.\n"
                "false: the game's own painted skins.");
    table.Local("Content", "OwnPeople", &Config::own_people, cameraunlock::config::BoolCodec(),
                "true: the mod's own skins for your character and the other living, in the game's\n"
                "five skin tones, each with a lifelike face. false: the game's own painted skins.");
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
