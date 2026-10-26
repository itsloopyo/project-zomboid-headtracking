package com.cameraunlock.zomboid;

import zombie.iso.IsoDirections;
import zombie.iso.sprite.IsoSprite;

/**
 * The shutters either side of a window, which the game paints over the wall in five colours,
 * each colour a run of four places on its sheet: two on a west wall and then two on a north
 * one. White, blue and maroon are panelled (16 to 23, 32 to 35), tan and dark blue louvred
 * (24 to 31). Each hangs where its sprite paints it, as wide and as high, in the sprite's colour.
 */
final class Shutters {
    private static final String SHEET = "fixtures_windows_detailing_01_";
    private static final int FIRST = 16, LAST = 35, FIRST_LOUVRED = 24, LAST_LOUVRED = 31;

    private Shutters() {}

    /** The shutter a sprite is, or null for any other sprite. */
    static Furniture of(IsoSprite sprite, String name) {
        int place = Sheets.place(name, SHEET);
        if (place < FIRST || place > LAST) {
            return null;
        }
        IsoDirections away = (place - FIRST) % 4 < 2 ? IsoDirections.E : IsoDirections.S;
        String model = place >= FIRST_LOUVRED && place <= LAST_LOUVRED ? "shutter_louvred" : "shutter_panelled";
        return new Furniture(sprite, model, Furniture.turnsOf(away)).hang(WallHung.behind(away), false).stretched().dyedWhereHung(1 << Models.PAINT);
    }
}
