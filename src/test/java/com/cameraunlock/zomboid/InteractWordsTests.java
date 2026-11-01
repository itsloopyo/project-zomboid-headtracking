package com.cameraunlock.zomboid;

/** Tests of the words of the label under the reticle that says what the Interact key does, and of how it comes and goes. */
final class InteractWordsTests {
    private static final long MS = 1_000_000L;

    private InteractWordsTests() {}

    static void run() {
        Checks.run("eachActionIsTheGamesOwnWords", InteractWordsTests::eachActionIsTheGamesOwnWords);
        Checks.run("anActionOnAThingNamesIt", InteractWordsTests::anActionOnAThingNamesIt);
        Checks.run("theLineNamesTheKeyAndWhatHoldingItDoes", InteractWordsTests::theLineNamesTheKeyAndWhatHoldingItDoes);
        Checks.run("theLabelHoldsAcrossAGapAndThenFades", InteractWordsTests::theLabelHoldsAcrossAGapAndThenFades);
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

    private static void theLabelHoldsAcrossAGapAndThenFades() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        for (int i = 0; i < 20; i++, now += 16 * MS) {
            fade.update("[E] Open Door", now);
        }
        check(fade.alpha() == 1f, "on something for a while, it shows whole: " + fade.alpha());
        long seenAt = now - 16 * MS;
        for (; now - seenAt <= InteractWords.Fade.HOLD_NANOS; now += 16 * MS) {
            fade.update(null, now);
            check(fade.alpha() == 1f && "[E] Open Door".equals(fade.text()), "a gap no longer than the hold leaves it as it was, at " + (now - seenAt) / MS + " ms");
        }
        fade.update("[E] Open Door", now);
        check(fade.alpha() == 1f, "back on the door it never flickered");
        long gapFrom = now;
        boolean faded = false;
        for (; now - gapFrom < InteractWords.Fade.HOLD_NANOS + InteractWords.Fade.OUT_NANOS + 100 * MS; now += 16 * MS) {
            fade.update(null, now);
            faded |= fade.alpha() > 0f && fade.alpha() < 1f;
        }
        check(faded, "past the hold it fades rather than vanishing");
        check(fade.text() == null && fade.alpha() == 0f, "and then is gone");
        fade.update("[E] Open Door", now);
        fade.clear();
        check(fade.text() == null && fade.alpha() == 0f, "cleared, it is gone at once");
    }

    private static void theLabelFadesInQuicklyAndTakesNewWordsAtOnce() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        fade.update("[E] Open Door", now);
        check("[E] Open Door".equals(fade.text()) && fade.alpha() < 1f, "it fades in, not at once");
        int frames = 0;
        while (fade.alpha() < 1f) {
            now += 16 * MS;
            fade.update("[E] Open Door", now);
            frames++;
        }
        check(frames * 16 * MS <= InteractWords.Fade.IN_NANOS + 16 * MS, "and is whole within the fade in: " + frames + " frames");
        fade.update("[E] Close Window", now + 16 * MS);
        check("[E] Close Window".equals(fade.text()) && fade.alpha() == 1f, "moved straight onto something else it says that at once");
    }

    private static void aStallDoesNotJumpTheFade() {
        InteractWords.Fade fade = new InteractWords.Fade();
        long now = 0;
        for (int i = 0; i < 20; i++, now += 16 * MS) {
            fade.update("[E] Open Door", now);
        }
        fade.update(null, now);
        now += InteractWords.Fade.HOLD_NANOS + MS;
        fade.update(null, now);
        now += 2_000 * MS;
        fade.update(null, now);
        check(fade.alpha() > 0f, "a two second frame past the hold is one step of the fade, not the whole of it: " + fade.alpha());
    }
}
