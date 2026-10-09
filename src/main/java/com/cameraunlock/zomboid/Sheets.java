package com.cameraunlock.zomboid;

/** A sprite is named for its sheet and its place on it, "camping_01_6": the place read back out of the name. */
final class Sheets {
    private Sheets() {}

    /** Which sprite of a sheet a name is, or -1 for a name that is not of the sheet. */
    static int place(String name, String sheet) {
        if (!name.startsWith(sheet) || name.length() == sheet.length() || name.length() > sheet.length() + 3) {
            return -1;
        }
        int place = 0;
        for (int i = sheet.length(); i < name.length(); i++) {
            char digit = name.charAt(i);
            if (digit < '0' || digit > '9') {
                return -1;
            }
            place = place * 10 + digit - '0';
        }
        return place;
    }

    /** The number a sprite's name ends in, its place on its sheet, or -1. */
    static int place(String name) {
        String last = name.substring(name.lastIndexOf('_') + 1);
        return !last.isEmpty() && last.length() <= 4 && last.chars().allMatch(Character::isDigit) ? Integer.parseInt(last) : -1;
    }
}
