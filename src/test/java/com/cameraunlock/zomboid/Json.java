package com.cameraunlock.zomboid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads JSON text for a test: an object as a map in its keys' order, an array as a list, a string,
 * a number as a Double, a Boolean, and null. Anything that is not JSON, and a key an object holds
 * twice, throws with the place.
 */
final class Json {
    private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");
    private final String text;
    private int at;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Json json = new Json(text);
        Object value = json.value();
        json.blanks();
        if (json.at != text.length()) {
            throw json.wrong("more text after the value");
        }
        return value;
    }

    private IllegalArgumentException wrong(String what) {
        return new IllegalArgumentException("not JSON at character " + at + ": " + what);
    }

    private void blanks() {
        while (at < text.length() && " \t\r\n".indexOf(text.charAt(at)) >= 0) {
            at++;
        }
    }

    private boolean takes(String word) {
        if (!text.startsWith(word, at)) {
            return false;
        }
        at += word.length();
        return true;
    }

    private void expect(String word) {
        blanks();
        if (!takes(word)) {
            throw wrong("expected " + word);
        }
    }

    private Object value() {
        blanks();
        if (takes("{")) {
            return object();
        }
        if (takes("[")) {
            return array();
        }
        if (takes("\"")) {
            return string();
        }
        if (takes("true")) {
            return Boolean.TRUE;
        }
        if (takes("false")) {
            return Boolean.FALSE;
        }
        if (takes("null")) {
            return null;
        }
        Matcher number = NUMBER.matcher(text).region(at, text.length());
        if (!number.lookingAt()) {
            throw wrong("expected a value");
        }
        at = number.end();
        return Double.valueOf(number.group());
    }

    private Map<String, Object> object() {
        Map<String, Object> members = new LinkedHashMap<>();
        blanks();
        if (takes("}")) {
            return members;
        }
        do {
            expect("\"");
            String key = string();
            expect(":");
            if (members.containsKey(key)) {
                throw wrong("the key " + key + " twice in one object");
            }
            members.put(key, value());
            blanks();
        } while (takes(","));
        expect("}");
        return members;
    }

    private List<Object> array() {
        List<Object> items = new ArrayList<>();
        blanks();
        if (takes("]")) {
            return items;
        }
        do {
            items.add(value());
            blanks();
        } while (takes(","));
        expect("]");
        return items;
    }

    private String string() {
        StringBuilder read = new StringBuilder();
        while (true) {
            if (at >= text.length()) {
                throw wrong("a string with no end");
            }
            char next = text.charAt(at++);
            if (next == '"') {
                return read.toString();
            }
            if (next < 0x20) {
                throw wrong("a control character in a string");
            }
            if (next != '\\') {
                read.append(next);
                continue;
            }
            if (at >= text.length()) {
                throw wrong("a string with no end");
            }
            char escaped = text.charAt(at++);
            switch (escaped) {
                case '"', '\\', '/' -> read.append(escaped);
                case 'b' -> read.append('\b');
                case 'f' -> read.append('\f');
                case 'n' -> read.append('\n');
                case 'r' -> read.append('\r');
                case 't' -> read.append('\t');
                case 'u' -> {
                    if (at + 4 > text.length() || !text.substring(at, at + 4).matches("[0-9a-fA-F]{4}")) {
                        throw wrong("\\u without four hex digits");
                    }
                    read.append((char) Integer.parseInt(text.substring(at, at + 4), 16));
                    at += 4;
                }
                default -> throw wrong("the escape \\" + escaped);
            }
        }
    }
}
