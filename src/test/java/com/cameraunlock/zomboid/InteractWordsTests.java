package com.cameraunlock.zomboid;

/** Tests of the words of the label under the reticle that says what the Interact key does, and of how it comes and goes. */
final class InteractWordsTests {
    private static final long MS = 1_000_000L;

    private InteractWordsTests() {}

    static void run() {
        Checks.run("eachActionIsTheGamesOwnWords", InteractWordsTests::eachActionIsTheGamesOwnWords);
        Checks.run("anActionOnAThingNamesIt", InteractWordsTests::anActionOnAThingNamesIt);
        Checks.run("theLineNamesTheKeyAndWhatHoldingItDoes", InteractWordsTests::theLineNamesTheKeyAndWhatHoldingItDoes);
        Checks.run("theLabelComesBackWholeOnlyOnTheSameThing", InteractWordsTests::theLabelComesBackWholeOnlyOnTheSameThing);
        Checks.run("theLabelFadesInQuicklyAndTakesNewWordsAtOnce", InteractWordsTests::theLabelFadesInQuicklyAndTakesNewWordsAtOnce);
        Checks.run("aStallDoesNotJumpTheFade", InteractWordsTests::aStallDoesNotJumpTheFade);
    }

    private static void check(boolean condition, String what) {
        Checks.check(condition, what);
    }

    private static void eachActionIsTheGamesOwnWords() {
        check(InteractWords.Does.OPEN_DOOR.key.equals("ContextMenu_Open_door") && InteractWords.Does.CLOSE_DOOR.key.equals("ContextMenu_Close_door"),
                "a door is opened and shut in the words the game's controller prompt uses");
        check(InteractWords.Does.OPEN_WINDOW.key.equals("ContextMenu_Open_window") && InteractWords.Does.CLOSE_WINDOW.key.equals("ContextMenu_Close_window"),
                "and a window");
        check(InteractWords.Does.OPEN_CURTAINS.key.equals("ContextMenu_Open_curtains") && InteractWords.Does.CLOSE_CURTAINS.key.equals("ContextMenu_Close_curtains"),
                "and curtains");
        check(InteractWords.Does.TURN_ON.key.equals("ContextMenu_Turn_On") && InteractWords.Does.TURN_OFF.key.equals("ContextMenu_Turn_Off"), "a light switch is turned on and off");
        check(InteractWords.Does.CLIMB_THROUGH.key.equals("ContextMenu_Climb_through"), "a window is climbed through");
        check(InteractWords.Does.GRAB.key.equals("ContextMenu_Grab") && InteractWords.Does.LOOT.key.equals("IGUI_Controller_Loot")
                && InteractWords.Does.DEVICE_OPTIONS.key.equals("IGUI_DeviceOptions"), "an item is grabbed, a container looted and a radio's options opened");
        for (InteractWords.Does does : InteractWords.Does.values()) {
            check(does.key != null || does.named, does + " has words: the game's, or the name of what it acts on");
        }
    }

    private static void anActionOnAThingNamesIt() {
        check(InteractWords.action(InteractWords.Does.OPEN_DOOR, "Open Door", null).equals("Open Door"), "an action on a door is the game's words alone");
        check(InteractWords.action(InteractWords.Does.GRAB, "Grab", "Water Bottle").equals("Grab Water Bottle"), "grabbing names the item");
        check(InteractWords.action(InteractWords.Does.LOOT, "Loot", "Freezer").equals("Loot Freezer"), "looting names the container's half");
        check(InteractWords.action(InteractWords.Does.OPEN_NAMED, null, "Carpentry Bench").equals("Carpentry Bench"), "a workstation is its name alone");
        check(InteractWords.action(InteractWords.Does.MENU, null, "Sink").equals("Sink" + InteractWords.MORE), "a thing whose menu opens is its name, with more to choose");
    }

    private static void theLineNamesTheKeyAndWhatHoldingItDoes() {
        check(InteractWords.line("E", "Open Door", null).equals("[E] Open Door"), "a tap alone: " + InteractWords.line("E", "Open Door", null));
        String both = InteractWords.line("E", "Close Window", "Climb through");
        check(both.equals("[E] Close Window   [Hold E] Climb through"), "a tap and a hold that does something else: " + both);
        check(InteractWords.line("E", null, "Climb through").equals("[Hold E] Climb through"), "a hold alone, through a hole");
        check(InteractWords.line("E", "Climb through", "Climb through").equals("[E] Climb through"), "a hold that does what the tap does is not said twice");
        check(InteractWords.line("A", "Open Door", null).equals("[A] Open Door"), "a controller's button is named as the key is");
        check(InteractWords.line(null, "Open Door", "Climb through").equals("Open Door   Hold: Climb through"), "with no key bound, the actions alone");
        check(InteractWords.line("E", null, null) == null, "nothing to do is no label");
    }

    private static void theLabelComesBackWholeOnlyOnTheSameThing() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        for (int i = 0; i < 20; i++, now += 16 * MS) {
            fade.update("[E] Open Door", true, now);
        }
        check(fade.alpha() == 1f, "on something for a while, it shows whole: " + fade.alpha());
        fade.update(null, false, now);
        check(fade.alpha() < 1f && "[E] Open Door".equals(fade.text()), "off it, it starts to fade at once: " + fade.alpha());
        now += 16 * MS;
        fade.update(null, false, now);
        now += 16 * MS;
        fade.update("[E] Open Door", true, now);
        check(fade.alpha() == 1f, "back on the same door across a gap, it is whole again at once: " + fade.alpha());
        for (int i = 0; i < 4; i++) {
            now += 16 * MS;
            fade.update(null, false, now);
        }
        float left = fade.alpha();
        now += 16 * MS;
        fade.update("[E] Open Door", false, now);
        check(left < 1f && fade.alpha() < 1f, "on another door with the same words it fades in from where it was, not whole at once: " + fade.alpha());
        for (int i = 0; i < 20; i++) {
            now += 16 * MS;
            fade.update("[E] Open Door", true, now);
        }
        long leftAt = now;
        boolean faded = false;
        for (; now - leftAt < InteractWords.Fade.OUT_NANOS + 32 * MS; now += 16 * MS) {
            fade.update(null, false, now);
            faded |= fade.alpha() > 0f && fade.alpha() < 1f;
        }
        check(faded, "it fades rather than vanishing");
        check(fade.text() == null && fade.alpha() == 0f, "and is gone within the fade out");
        now += InteractWords.Fade.HOLD_NANOS;
        fade.update("[E] Open Door", true, now);
        check(fade.alpha() < 1f, "back on the same door long after, it fades in again: " + fade.alpha());
        fade.clear();
        check(fade.text() == null && fade.alpha() == 0f, "cleared, it is gone at once");
    }

    private static void theLabelFadesInQuicklyAndTakesNewWordsAtOnce() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        fade.update("[E] Open Door", false, now);
        check("[E] Open Door".equals(fade.text()) && fade.alpha() < 1f, "it fades in, not at once");
        int frames = 0;
        while (fade.alpha() < 1f) {
            now += 16 * MS;
            fade.update("[E] Open Door", true, now);
            frames++;
        }
        check(frames * 16 * MS <= InteractWords.Fade.IN_NANOS + 16 * MS, "and is whole within the fade in: " + frames + " frames");
        fade.update("[E] Close Window", false, now + 16 * MS);
        check("[E] Close Window".equals(fade.text()) && fade.alpha() == 1f, "moved straight onto something else it says that at once");
    }

    private static void aStallDoesNotJumpTheFade() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        for (int i = 0; i < 20; i++, now += 16 * MS) {
            fade.update("[E] Open Door", true, now);
        }
        fade.update(null, false, now);
        now += 2_000 * MS;
        fade.update(null, false, now);
        check(fade.alpha() > 0f, "a two second frame is one step of the fade, not the whole of it: " + fade.alpha());
    }
}
