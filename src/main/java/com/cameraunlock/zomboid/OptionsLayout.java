package com.cameraunlock.zomboid;

import com.cameraunlock.core.CameraUnlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How the game's options screen (OptionsPage) shows the mod's settings: the order they come in
 * under titles of their own, the words each goes by, its tooltip, and the quick lists at the top
 * that set a class of them together.
 */
final class OptionsLayout {
    /**
     * A title on the page, what is said under it or null, and the settings under it: those named
     * in ids, in that order, then those of the file's section, if one is named, that no group names,
     * in core's order.
     */
    record Group(String title, String note, String section, String... ids) {}

    static final String QUICK = "Quick settings", HEAD_TRACKING = "Head tracking", FIRST_PERSON = "First person", GRAPHICS = "Graphics",
            CUSTOM_GRAPHICS = "Custom graphics", CONTENT = "Mod content", GAMEPLAY = "Gameplay tweaks", FIXES = "Game fixes",
            OPTIMIZATIONS = "Optimizations", SPLIT_SCREEN = "Split screen", VOICE = "Voice";

    /** The page from the top. OptionsPage adds the quick lists under the first title and the microphone lists under Voice. */
    static final Group[] PAGE = page();

    private static Group[] page() {
        List<Group> groups = new ArrayList<>(List.of(
                new Group(QUICK, "Each list below switches a whole class of settings on or off. A list shows Mixed when your settings match "
                        + "none of its lines.", null, Tracking.ENABLED_OPTION),
                new Group(HEAD_TRACKING, null, null, Tracking.TRACKING_MODE_OPTION, Tracking.AIM_MODE_OPTION, Tracking.YAW_MODE_OPTION,
                        Tracking.LOCAL_SMOOTHING_OPTION, Tracking.REMOTE_SMOOTHING_OPTION, Part.TORCH_FOLLOWS_HEAD.key, Tracking.LIGHT_MULTIPLIER_OPTION),
                new Group(FIRST_PERSON, null, "FirstPerson", Part.FIRST_PERSON.key, Config.firstPersonRow(0), Config.firstPersonInVehiclesRow(0),
                        "FieldOfView", "Crosshair", Part.HEAR_FROM_THE_EYE.key, Part.HEAD_LED_VIEW_WHEN_DOWN.key, Part.NAME_TAGS_AT_HEADS.key,
                        Part.QUICK_RAGDOLLS.key, Part.HIT_FLINCH.key, "HillHeight"),
                new Group(GRAPHICS, null, null, Config.qualityKey(0), Config.overheadQualityKey(0), Config.styleKey(0), Part.LIGHTNING_BOLTS.key,
                        Part.SHOT_IMPACTS.key, "MuzzleFlash", Part.ITEMS_AS_MODELS.key, Part.FINER_SKINS.key, Part.LEAVE_OUT_SCREEN_GREY.key,
                        Part.FAR_VEHICLES_AND_ZOMBIES.key, "HiddenWallDecor"),
                new Group(CUSTOM_GRAPHICS, "These show what player 1's first person graphics mode draws. Changing one switches that mode to Custom, "
                        + "which draws these for every player set to Custom, in first person or the isometric view.", "Graphics"),
                new Group(CONTENT, "Untick any of these to have the game's own in place of the mod's.", "Content"),
                new Group(GAMEPLAY, null, "Rules", "ShotsThroughWalls", "ShotsPastTrees", "ShotsBreakGlass", "ShotsBreakLamps", "BlastHoles", "ShotsWoundLimbs",
                        Part.SHOTS_FROM_VEHICLE_SEATS.key, "RegripTwoHandedWeapons", "FacesTheView", Part.DRIVE_WHILE_AIMING.key,
                        Part.HILLS_MOVE_VEHICLES.key, "VehiclesSmashThrough", Part.HAND_LIGHT_OUT_AT_THE_WHEEL.key, "ZombiesSeenAtOnce",
                        "HiddenWallDecorSaved"),
                new Group(FIXES, null, "Fixes"),
                new Group(OPTIMIZATIONS, null, "Optimizations"),
                new Group(SPLIT_SCREEN, null, null, "SplitScreen")));
        for (int player = 1; player < View.COUNT; player++) {
            groups.add(new Group("Player " + (player + 1), null, null, Config.qualityKey(player), Config.overheadQualityKey(player), Config.styleKey(player),
                    Config.firstPersonRow(player), Config.firstPersonInVehiclesRow(player)));
        }
        groups.add(new Group(VOICE, null, "Voice"));
        return groups.toArray(Group[]::new);
    }

    /** What each setting is called on the page, by the key of player 1's: core's words for the key are the file's, not a player's. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry(Tracking.ENABLED_OPTION, "Head tracking"),
            Map.entry(Tracking.TRACKING_MODE_OPTION, "Tracking mode"),
            Map.entry(Tracking.AIM_MODE_OPTION, "Aim mode"),
            Map.entry(Tracking.YAW_MODE_OPTION, "Horizon-relative head turns"),
            Map.entry(Tracking.LOCAL_SMOOTHING_OPTION, "Local smoothing"),
            Map.entry(Tracking.REMOTE_SMOOTHING_OPTION, "Remote smoothing"),
            Map.entry(Part.TORCH_FOLLOWS_HEAD.key, "Flashlight follows your head"),
            Map.entry(Tracking.LIGHT_MULTIPLIER_OPTION, "Flashlight follow amount"),
            Map.entry(Part.FIRST_PERSON.key, "First person view"),
            Map.entry("FirstPerson", "Load saves in first person"),
            Map.entry("FirstPersonInVehicles", "First person in vehicles"),
            Map.entry("FieldOfView", "Field of view"),
            Map.entry("Crosshair", "Crosshair"),
            Map.entry(Part.HEAR_FROM_THE_EYE.key, "First person sound"),
            Map.entry(Part.HEAD_LED_VIEW_WHEN_DOWN.key, "Follow your character's head when down"),
            Map.entry(Part.NAME_TAGS_AT_HEADS.key, "Names over heads"),
            Map.entry(Part.QUICK_RAGDOLLS.key, "Quick ragdolls"),
            Map.entry(Part.HIT_FLINCH.key, "Hit reactions"),
            Map.entry("HillHeight", "Hill height"),
            Map.entry("Quality", "First person graphics"),
            Map.entry("IsometricQuality", "Isometric graphics"),
            Map.entry("Style", "Picture style"),
            Map.entry(Part.LIGHTNING_BOLTS.key, "Lightning bolts"),
            Map.entry(Part.SHOT_IMPACTS.key, "Bullet impacts"),
            Map.entry("MuzzleFlash", "Muzzle flash"),
            Map.entry(Part.ITEMS_AS_MODELS.key, "Ground items as 3D models"),
            Map.entry(Part.FINER_SKINS.key, "Detailed skins"),
            Map.entry(Part.LEAVE_OUT_SCREEN_GREY.key, "Leave out the grey weather tint"),
            Map.entry(Part.FAR_VEHICLES_AND_ZOMBIES.key, "Distant vehicles and zombies"),
            Map.entry("HiddenWallDecor", "Pictures on hidden walls"),
            Map.entry("TreeQuality", "Trees"),
            Map.entry("GrassDensity", "Grass density"),
            Map.entry("GrassDistance", "Grass distance"),
            Map.entry("RenderScale", "Render scale"),
            Map.entry("ViewDistance", "View distance"),
            Map.entry("ZombiesAndPropsDrawDistance", "Zombie and prop distance"),
            Map.entry("FarTreeDistance", "Far tree distance"),
            Map.entry("RainOnGlass", "Rain on glass"),
            Map.entry("ShadingAndLight", "Shading and light"),
            Map.entry("Reflections", "Reflections"),
            Map.entry("ReflectionScale", "Reflection resolution"),
            Map.entry("FullSizeTextures", "Full size textures"),
            Map.entry("SurfaceDepth", "Surface depth"),
            Map.entry("SharpShadows", "Sharp shadows"),
            Map.entry("WholeFigureShadows", "Full figure shadows"),
            Map.entry("FrameBlending", "Frame blending"),
            Map.entry("Antialiasing", "Anti-aliasing"),
            Map.entry("LightDistance", "Lamp light distance"),
            Map.entry("CorpseModels", "Corpse models"),
            Map.entry("VanillaWeapons", "Weapons"),
            Map.entry("VanillaFurniture", "Furniture and fittings"),
            Map.entry("VanillaPictures", "Pictures and TV screens"),
            Map.entry("VanillaPlants", "Plants and rocks"),
            Map.entry("VanillaTextures", "Surface textures"),
            Map.entry("VanillaBuildings", "Windows, doors and fences"),
            Map.entry("VanillaZombies", "Zombie skins"),
            Map.entry("VanillaPeople", "People's skins"),
            Map.entry("VanillaArms", "Arms and hands"),
            Map.entry("VanillaClothes", "Clothes and hair"),
            Map.entry("VanillaAnimals", "Animal skins"),
            Map.entry("VanillaFire", "Smoke and firelight"),
            Map.entry("VanillaCabins", "Vehicle interiors"),
            Map.entry("ShotsThroughWalls", "Bullets through thin walls"),
            Map.entry("ShotsPastTrees", "Bullets past tree trunks"),
            Map.entry("ShotsBreakGlass", "Bullets break windows"),
            Map.entry("ShotsBreakLamps", "Bullets break lamps"),
            Map.entry("BlastHoles", "Shotguns blow holes in doors and fences"),
            Map.entry("ShotsWoundLimbs", "Limb damage"),
            Map.entry(Part.SHOTS_FROM_VEHICLE_SEATS.key, "Shoot from vehicle seats"),
            Map.entry("RegripTwoHandedWeapons", "Regrip two-handed weapons"),
            Map.entry("FacesTheView", "Face where you look"),
            Map.entry(Part.DRIVE_WHILE_AIMING.key, "Drive while aiming"),
            Map.entry(Part.HILLS_MOVE_VEHICLES.key, "Hills move vehicles"),
            Map.entry("VehiclesSmashThrough", "Vehicles smash through"),
            Map.entry(Part.HAND_LIGHT_OUT_AT_THE_WHEEL.key, "Hand light off when driving"),
            Map.entry("ZombiesSeenAtOnce", "Zombies show as soon as in sight"),
            Map.entry("HiddenWallDecorSaved", "Add mirrors and clocks to hidden walls"),
            Map.entry("Rewind30s", "Rewind 30 seconds on death"),
            Map.entry(Part.BURNT_BUILDING_FREEZE_FIX.key, "Burnt building freeze fix"),
            Map.entry(Part.CENTRE_THE_WINDOW.key, "Centre the window"),
            Map.entry(Part.CONTROLLER_CHOICE_FIX.key, "Controller selection fix"),
            Map.entry("SpreadLightUpdates", "Spread out light updates"),
            Map.entry(Part.CACHED_GAME_LOOKUPS.key, "Cache game lookups"),
            Map.entry(Part.SKIP_COVERED_GAME_DRAWING.key, "Skip drawing under first person"),
            Map.entry(Part.SKINS_COMPOSED_WHILE_WAITING.key, "Build skins between frames"),
            Map.entry(Part.CHUNK_CHANGE_COUNTS.key, "Count chunk changes"),
            Map.entry(Part.UNSEEN_ROOM_ZOMBIES_UNMODELLED.key, "Skip zombies in unseen rooms"),
            Map.entry(Part.HOLD_CHUNK_MAP_CENTRE.key, "Steady chunk loading"),
            Map.entry(Part.GRAPHICS_MEMORY_STEPS.key, "Fit to graphics memory"),
            Map.entry(Part.PREPARE_WHILE_LOADING.key, "Prepare while loading"),
            Map.entry(Part.SPOT_BY_SIGHT.key, "Sight from the first person view"),
            Map.entry(Part.BUILD_ON_WORKER_THREADS.key, "Build on worker threads"),
            Map.entry(Part.REBUILD_FOR_OWN_SPRITES.key, "Rebuild chunks only when needed"),
            Map.entry(Part.HIDDEN_WALL_READS_KEPT.key, "Cache hidden wall reads"),
            Map.entry(Part.BUILDING_FACTS_KEPT.key, "Cache building facts"),
            Map.entry("SplitScreen", "Split screen layout"),
            Map.entry(Config.TALKING_MOUTHS, "Talking mouths"));

    /** Tooltips that say on the page what the file's comment says for the file, by the key of player 1's. */
    private static final Map<String, String> TIPS = Map.of(
            Tracking.ENABLED_OPTION,
            "On: your tracker moves the first person view and turns your character's head in the isometric view. Off: head tracking "
                    + "is off. No key turns it on or off.",
            Tracking.TRACKING_MODE_OPTION,
            "Which head movements move the view. Rotation and position: turning and leaning your head. Rotation only: turning. "
                    + "Position only: leaning.",
            Tracking.AIM_MODE_OPTION,
            "What head tracking does while you aim down the sights. Sights locked: when you lean, your eye stays on the sights. "
                    + "Free look with marker: the weapon stays where it is and your head moves freely around it, and the game's own reticle "
                    + "shows where the shot will land. Stock sights: your head's turning and leaning ease out while you aim, so the sight "
                    + "picture is the game's own. Tilting still works.",
            "SplitScreen",
            "How two players share one screen: side by side, as in the game, or one above the other. Takes effect at once. With side "
                    + "by side, the mod leaves the game's split screen code unpatched from the next start.");

    /** The lines of a list where core's words for the row's choices are the file's. */
    private static final Map<String, String[]> LINES = Map.of("SplitScreen", new String[] {"Side by side", "One above the other"});

    /** The rows of [Content], which say whether the game's own is drawn: the page ticks each for the mod's own, so it is ticked for 0. */
    static final Set<String> INVERTED = Set.of(Config.VANILLA_KEYS);

    /** What the keys of players 2, 3 and 4's rows begin with. */
    private static final String[] PLAYER_PREFIXES = {"SecondPlayer", "ThirdPlayer", "FourthPlayer"};

    private OptionsLayout() {}

    /** The key of player 1's row a row of [Players] is the same as: SecondPlayerQuality is Quality. */
    static String basis(String id) {
        for (String prefix : PLAYER_PREFIXES) {
            if (id.startsWith(prefix)) {
                return id.substring(prefix.length());
            }
        }
        return id;
    }

    /** What a setting is called on the page. */
    static String label(CameraUnlock.Option described) {
        String label = LABELS.get(basis(described.id));
        return label != null ? label : described.label;
    }

    /** The lines a list shows for its choices, in core's order. */
    static String[] lines(CameraUnlock.Option described) {
        String[] lines = LINES.get(described.id);
        if (lines == null) {
            return described.choices;
        }
        if (lines.length != described.choices.length) {
            throw new IllegalStateException(described.id + " has " + described.choices.length + " choices and the page names " + lines.length);
        }
        return lines;
    }

    /**
     * A setting's tooltip on the page: the file's comment above its row, said for the page. A
     * comment's true and false are On and Off, the other settings it names are named as the page
     * names them, and a row of [Content] says the mod's own first, as its tick box is ticked for it.
     * Players 2, 3 and 4's rows have player 1's tooltips.
     *
     * @param options every setting of the page, whose page names stand for their keys
     */
    static String tip(CameraUnlock.Option described, CameraUnlock.Option[] options) {
        String id = basis(described.id);
        String tip = TIPS.get(id);
        if (tip == null) {
            CameraUnlock.Option own = described;
            if (!id.equals(described.id)) {
                own = Arrays.stream(options).filter(option -> option.id.equals(id)).findFirst()
                        .orElseThrow(() -> new IllegalStateException(described.id + " has no row " + id + " for player 1 on the page"));
            }
            tip = said(own.comment, options);
        }
        if (!INVERTED.contains(id)) {
            return tip;
        }
        int off = tip.indexOf(" Off: ");
        if (!tip.startsWith("On: ") || off < 0 || tip.indexOf(" Off: ", off + 1) >= 0) {
            throw new IllegalStateException(described.id + "'s comment is not one On and one Off, which its tick box shows the other way round");
        }
        return "On: " + tip.substring(off + " Off: ".length()) + " Off: " + tip.substring("On: ".length(), off);
    }

    private static final Pattern KEY = Pattern.compile("\\b[A-Z][a-z]+(?:[A-Z][a-z0-9]*)+\\b");

    /** A comment of the file as the page says it. */
    private static String said(String comment, CameraUnlock.Option[] options) {
        String said = comment.replace('\n', ' ');
        if (said.startsWith("true: ")) {
            said = "On: " + said.substring("true: ".length());
        }
        said = said.replace(" false: ", " Off: ").replace("the [Graphics] rows", "what you set under " + CUSTOM_GRAPHICS);
        Map<String, String> named = new HashMap<>();
        for (CameraUnlock.Option option : options) {
            named.put(option.id, label(option));
        }
        Matcher keys = KEY.matcher(said);
        StringBuilder out = new StringBuilder();
        while (keys.find()) {
            String label = named.get(keys.group());
            keys.appendReplacement(out, Matcher.quoteReplacement(label == null ? keys.group() : "\"" + label + "\""));
        }
        keys.appendTail(out);
        return out.toString();
    }

    /**
     * The places in options of the settings under each group of a page, in the order the page
     * shows them. Throws for a setting under no title or under two, and for a name that is no setting.
     */
    static int[][] of(Group[] page, CameraUnlock.Option[] options) {
        int[] groupOf = new int[options.length];
        Arrays.fill(groupOf, -1);
        Map<String, Integer> places = new HashMap<>();
        for (int option = 0; option < options.length; option++) {
            places.put(options[option].id, option);
        }
        for (int group = 0; group < page.length; group++) {
            for (String id : page[group].ids) {
                Integer option = places.get(id);
                if (option == null) {
                    throw new IllegalStateException(id + " is put under " + page[group].title + " on the options screen and is no setting there");
                }
                if (groupOf[option] >= 0) {
                    throw new IllegalStateException(id + " is under both " + page[groupOf[option]].title + " and " + page[group].title + " on the options screen");
                }
                groupOf[option] = group;
            }
        }
        int[][] layout = new int[page.length][];
        for (int group = 0; group < page.length; group++) {
            List<Integer> under = new ArrayList<>();
            for (String id : page[group].ids) {
                under.add(places.get(id));
            }
            for (int option = 0; option < options.length; option++) {
                if (groupOf[option] < 0 && options[option].section.equals(page[group].section)) {
                    groupOf[option] = group;
                    under.add(option);
                }
            }
            layout[group] = under.stream().mapToInt(Integer::intValue).toArray();
        }
        for (int option = 0; option < options.length; option++) {
            if (groupOf[option] < 0) {
                throw new IllegalStateException(options[option].id + " of [" + options[option].section + "] is under no title of the options screen");
            }
        }
        return layout;
    }

    /** The settings a line of a quick list puts on the page, by key, as the page holds them now (now, by key). */
    interface Targets {
        Map<String, Double> given(ToDoubleFunction<String> now);
    }

    /** A list at the top of the page that switches a class of settings: its key on the page, its label and tooltip, and its lines after Mixed. */
    record QuickList(String id, String label, String tip, String[] lines, List<Targets> sets) {}

    /** The first line of every quick list, which stands for the settings it switches holding none of its other lines, and sets nothing. */
    static final String MIXED = "Mixed";

    /**
     * The quick lists of a page, from the settings it shows. Each switches the tick boxes of a
     * class of them, and Everything switches every tick box on the page but those of Custom
     * graphics, head tracking off, talking mouths, which opens a microphone, horizon-relative head
     * turns and the view each player loads in and takes in vehicles, which are how a player plays
     * and not parts of the mod. Rewind30s, offered in a development build, is never switched on.
     */
    static List<QuickList> quickLists(CameraUnlock.Option[] options, int[][] layout) {
        List<String> graphics = ticks(options, layout, GRAPHICS), content = ticks(options, layout, CONTENT),
                gameplay = ticks(options, layout, GAMEPLAY), optimizations = ticks(options, layout, OPTIMIZATIONS), every = new ArrayList<>();
        Set<String> leftOut = Set.of(Tracking.ENABLED_OPTION, Config.TALKING_MOUTHS, Tracking.YAW_MODE_OPTION);
        for (int group = 0; group < layout.length; group++) {
            // The rows of Custom graphics are a picture, which a tick of one would switch player 1 to.
            if (PAGE[group].title.equals(CUSTOM_GRAPHICS)) {
                continue;
            }
            for (int option : layout[group]) {
                String id = options[option].id;
                if (options[option].kind == CameraUnlock.OPTION_BOOL && !leftOut.contains(id) && !isFirstPersonUse(id)) {
                    every.add(id);
                }
            }
        }
        return List.of(
                new QuickList("Everything", "Everything",
                        "On: everything the mod does, head tracking included. Off: everything but head tracking and talking mouths, so you can "
                                + "switch back on only what you want. Talking mouths, which uses your microphone, is left as it is. Some settings "
                                + "take effect only when the game next starts.",
                        new String[] {"On", "Off"},
                        List.of(now -> {
                            Map<String, Double> values = switched(every, true);
                            values.put(Tracking.ENABLED_OPTION, 1.0);
                            values.putAll(graphicsModesUp(now));
                            return values;
                        }, now -> {
                            Map<String, Double> values = switched(every, false);
                            values.putAll(graphicsModesLow());
                            values.put("SplitScreen", (double) SplitScreen.VERTICAL);
                            return values;
                        })),
                new QuickList("FirstPersonUse", "First person",
                        "On: the first person view is there to switch to with the first person key. In vehicles only: saves load in the "
                                + "isometric view, getting into a vehicle switches to first person and getting out switches back. Off: no "
                                + "first person view.",
                        new String[] {"On", "In vehicles only", "Off"},
                        List.of(now -> firstPersonUse(true, false), now -> firstPersonUse(true, true), now -> firstPersonUse(false, false))),
                new QuickList("EnhancedGraphics", "Enhanced graphics",
                        "On: the mod's own models, pictures and skins and every tick box under Graphics, with first person graphics at "
                                + "Automatic where they were at Low. Off: the game's own look, with first person and isometric graphics at "
                                + "Low. Some settings take effect only when the game next starts.",
                        new String[] {"On", "Off"},
                        List.of(now -> {
                            Map<String, Double> values = switched(graphics, true);
                            values.putAll(switched(content, true));
                            values.putAll(graphicsModesUp(now));
                            return values;
                        }, now -> {
                            Map<String, Double> values = switched(graphics, false);
                            values.putAll(switched(content, false));
                            values.putAll(graphicsModesLow());
                            return values;
                        })),
                new QuickList("GameplayTweaks", "Gameplay tweaks",
                        "Switches every setting under Gameplay tweaks on or off. Some take effect only when the game next starts.",
                        new String[] {"On", "Off"},
                        List.of(now -> switched(gameplay, true), now -> switched(gameplay, false))),
                new QuickList("OptimizationsAll", "Optimizations",
                        "Switches every setting under Optimizations on or off. Some take effect only when the game next starts.",
                        new String[] {"On", "Off"},
                        List.of(now -> switched(optimizations, true), now -> switched(optimizations, false))));
    }

    /** The line of a quick list whose settings the page holds now, 1 for the first after Mixed, or 0 for Mixed where it holds none. */
    static int lineHeld(QuickList list, ToDoubleFunction<String> now) {
        for (int line = 0; line < list.sets().size(); line++) {
            boolean holds = true;
            for (Map.Entry<String, Double> value : list.sets().get(line).given(now).entrySet()) {
                holds &= now.applyAsDouble(value.getKey()) == value.getValue();
            }
            if (holds) {
                return line + 1;
            }
        }
        return 0;
    }

    /** The tick boxes under a title, by key. */
    private static List<String> ticks(CameraUnlock.Option[] options, int[][] layout, String title) {
        for (int group = 0; group < PAGE.length; group++) {
            if (PAGE[group].title.equals(title)) {
                List<String> ids = new ArrayList<>();
                for (int option : layout[group]) {
                    if (options[option].kind == CameraUnlock.OPTION_BOOL) {
                        ids.add(options[option].id);
                    }
                }
                return ids;
            }
        }
        throw new IllegalStateException("the options screen has no title " + title);
    }

    /** Tick boxes all ticked or all unticked, as numbers of their rows: a row of [Content] is 0 where its box is ticked. Rewind30s is only ever unticked. */
    private static Map<String, Double> switched(List<String> ids, boolean on) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (String id : ids) {
            if (!(on && id.equals("Rewind30s"))) {
                values.put(id, on != INVERTED.contains(id) ? 1.0 : 0.0);
            }
        }
        return values;
    }

    /** Whether a key is a player's FirstPerson or FirstPersonInVehicles row. */
    private static boolean isFirstPersonUse(String id) {
        String own = basis(id);
        return own.equals(Config.firstPersonRow(0)) || own.equals(Config.firstPersonInVehiclesRow(0));
    }

    /**
     * What the First person list sets. On: first person, with no switch on getting into a
     * vehicle. In vehicles only: first person, every player loading in the isometric view and
     * going to first person on getting into a vehicle and back on getting out. Off: no first person.
     */
    private static Map<String, Double> firstPersonUse(boolean on, boolean inVehicles) {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put(Part.FIRST_PERSON.key, on ? 1.0 : 0.0);
        if (!on) {
            return values;
        }
        for (int player = 0; player < View.COUNT; player++) {
            if (inVehicles) {
                values.put(Config.firstPersonRow(player), 0.0);
            }
            values.put(Config.firstPersonInVehiclesRow(player), inVehicles ? 1.0 : 0.0);
        }
        return values;
    }

    /** Each player's first person graphics mode at Automatic where it is at Low, and left as it is where it is not. */
    private static Map<String, Double> graphicsModesUp(ToDoubleFunction<String> now) {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int player = 0; player < View.COUNT; player++) {
            String key = Config.qualityKey(player);
            values.put(key, now.applyAsDouble(key) == Quality.LOW ? Quality.AUTOMATIC : now.applyAsDouble(key));
        }
        return values;
    }

    /** Each player's first person and isometric graphics modes at Low, the game's own picture. */
    private static Map<String, Double> graphicsModesLow() {
        Map<String, Double> values = new LinkedHashMap<>();
        for (int player = 0; player < View.COUNT; player++) {
            values.put(Config.qualityKey(player), (double) Quality.LOW);
            values.put(Config.overheadQualityKey(player), 0.0);
        }
        return values;
    }
}
