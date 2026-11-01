package com.cameraunlock.zomboid;

import java.util.Objects;
import org.lwjglx.input.Keyboard;
import zombie.characters.CharacterJoypadButtonBinding;
import zombie.characters.IsoPlayer;
import zombie.core.Core;
import zombie.core.Translator;
import zombie.entity.GameEntity;
import zombie.input.JoypadButton;
import zombie.inventory.ItemContainer;
import zombie.iso.objects.IsoWorldInventoryObject;
import zombie.ui.TextManager;
import zombie.ui.UIFont;

/**
 * The label under the reticle that says what the Interact key would do now: "[E] Open Door",
 * "[E] Close Window   [Hold E] Climb through". It is worded from the decision the key itself
 * takes (FirstPerson.keyUse, CentreUse.decide), in the game's own translated words for each
 * action and the game's names for things, and it names the key as the player has bound it, or
 * the controller's button. It shows in first person only while the reticle is on something the
 * key acts on, and not while the pointer is out, the character aims or is busy with a timed
 * action. One for each player's view.
 *
 * The logic thread updates it with each frame it builds, and draws it where the game writes
 * over that player's finished picture.
 */
final class InteractLabel {
    /** The game's name for the Interact key binding. */
    private static final String BINDING = "Interact";
    /** How far under the reticle the label is written, in pixels on a screen 1080 high: clear of the crosshair at its most spread. */
    private static final float BELOW = 34f;
    private static final int DESIGN_HEIGHT = 1080;
    /** How far the dark edge round the words stands out, in pixels on a screen 1080 high. */
    private static final float EDGE = 1f;

    private final InteractWords.Fade fade = new InteractWords.Fade();
    /** What the line was last worded from, so it is worded again only when that changes. */
    private CentreUse.Use wordedTap, wordedHold;
    private String wordedKey, worded;

    /**
     * Takes what a tap and a hold of the key would do this frame, either null for nothing.
     * Logic thread.
     */
    void update(CentreUse.Use tap, CentreUse.Use hold, IsoPlayer player, long now) {
        String key = tap == null && hold == null ? null : keyName(player);
        if (!same(tap, wordedTap) || !same(hold, wordedHold) || !Objects.equals(key, wordedKey)) {
            wordedTap = tap;
            wordedHold = hold;
            wordedKey = key;
            worded = InteractWords.line(key, words(tap), words(hold));
            if (FirstPerson.TRACE && worded != null) {
                Log.info("interact label: " + worded);
            }
        }
        fade.update(worded, now);
    }

    /** Gone at once: the pointer is out, or first person is not drawing. */
    void hide() {
        fade.clear();
        wordedTap = null;
        wordedHold = null;
        worded = null;
    }

    /** The words shown now, or null. */
    String shown() {
        return fade.alpha() > 0f ? fade.text() : null;
    }

    /**
     * Writes it under the reticle, over the player's finished picture.
     *
     * @param x across the player's screen to the reticle, in pixels
     * @param y down it
     * @param height the player's screen's height, in pixels
     */
    void draw(int x, int y, int height) {
        String text = fade.text();
        float alpha = fade.alpha();
        if (text == null || alpha == 0f) {
            return;
        }
        float unit = (float) height / DESIGN_HEIGHT;
        double top = y + BELOW * unit, edge = Math.max(1f, EDGE * unit);
        // Dark round light, so it reads over a pale wall and a dark night alike.
        for (int i = 0; i < 4; i++) {
            TextManager.instance.DrawStringCentre(UIFont.Small, x + (i % 2 == 0 ? -edge : edge), top + (i < 2 ? -edge : edge), text, 0.0, 0.0, 0.0, 0.75 * alpha);
        }
        TextManager.instance.DrawStringCentre(UIFont.Small, x, top, text, 1.0, 1.0, 0.95, alpha);
    }

    private static boolean same(CentreUse.Use a, CentreUse.Use b) {
        return a == null ? b == null : b != null && a.does() == b.does() && a.object() == b.object() && a.container() == b.container();
    }

    /** What a use comes to in the game's words, or null where it does nothing to name. */
    private static String words(CentreUse.Use use) {
        if (use == null || use.does() == null) {
            return null;
        }
        InteractWords.Does does = use.does();
        String said = does.key == null ? null : Translator.getText(does.key);
        String name = !does.named ? null : switch (does) {
            case GRAB -> ((IsoWorldInventoryObject) use.object()).getItem().getDisplayName();
            case LOOT -> title(use.container());
            case OPEN_NAMED -> workstation(use);
            case MENU -> use.object().getTileName();
            default -> throw new IllegalStateException(does + " names nothing");
        };
        return InteractWords.action(does, said, name);
    }

    /** What the game's loot panel calls a container (ISInventoryPage): its own name, else its kind's title, else its kind. */
    private static String title(ItemContainer container) {
        if (container.getCustomName() != null) {
            return container.getCustomName();
        }
        String title = Translator.getTextOrNull("IGUI_ContainerTitle_" + container.getType());
        if (title == null) {
            title = Translator.getTextOrNull("IGUI_ContainerTitle_" + container.getDisplayType());
        }
        return title != null ? title : container.getType();
    }

    /** What the game's controller prompt calls a workstation's window (ISButtonPrompt): the workstation's own name, else its tile's. */
    private static String workstation(CentreUse.Use use) {
        String name = use.object().getSpriteConfig().getMultiSquareMaster().getEntityDisplayName();
        return name == null || name.equals(GameEntity.getDefaultEntityDisplayName()) ? use.object().getTileName() : name;
    }

    /** The Interact key as the player has it bound, or the controller's button for it, or null where it is bound to neither. */
    private static String keyName(IsoPlayer player) {
        if (View.onController(player)) {
            JoypadButton button = CharacterJoypadButtonBinding.Interact.getJoypadButton();
            return button == null ? null : Translator.getText(button.getNameTranslationKey());
        }
        Core.KeyBinding binding = Core.getInstance().getKeyBinding(BINDING);
        int code = binding.keyValue() != 0 ? binding.keyValue() : binding.altKey();
        return code == 0 ? null : Keyboard.getKeyName(code);
    }
}
