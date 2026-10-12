package com.cameraunlock.zomboid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates GLSL functions of floats written as a run of "if (A < B) { return E; }" and a last
 * "return E;", where each expression is numbers, the function's parameters, + - * /, brackets
 * and calls to abs, clamp, step, min, max and the other functions of the same text: so a test
 * can set what a shader works out against the Java it was written from.
 */
final class GlslFunctions {
    private record Function(List<String> parameters, List<String[]> branches, String last) {}

    private final Map<String, Function> functions = new HashMap<>();

    GlslFunctions(String source) {
        Matcher function = Pattern.compile("float (\\w+)\\(([^)]*)\\) \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(source);
        while (function.find()) {
            List<String> parameters = function.group(2).isBlank() ? List.of()
                    : java.util.Arrays.stream(function.group(2).split(",")).map(p -> p.strip().replaceFirst("^float ", "")).toList();
            String body = function.group(3);
            List<String[]> branches = new java.util.ArrayList<>();
            Matcher branch = Pattern.compile("if \\((.*?) < (.*?)\\) \\{\\s*return (.*?);\\s*\\}").matcher(body);
            int end = 0;
            while (branch.find()) {
                branches.add(new String[] {branch.group(1), branch.group(2), branch.group(3)});
                end = branch.end();
            }
            Matcher last = Pattern.compile("^\\s*return (.*?);\\s*$", Pattern.DOTALL).matcher(body.substring(end));
            if (!last.matches()) {
                throw new IllegalArgumentException("function " + function.group(1) + " does not end in one return: " + body.substring(end));
            }
            functions.put(function.group(1), new Function(parameters, branches, last.group(1)));
        }
    }

    float call(String name, float... arguments) {
        Function function = functions.get(name);
        if (function == null) {
            throw new IllegalArgumentException("no function " + name);
        }
        if (arguments.length != function.parameters.size()) {
            throw new IllegalArgumentException(name + " takes " + function.parameters.size() + " arguments");
        }
        Map<String, Float> values = new HashMap<>();
        for (int i = 0; i < arguments.length; i++) {
            values.put(function.parameters.get(i), arguments[i]);
        }
        for (String[] branch : function.branches) {
            if (new Expression(branch[0], values).value() < new Expression(branch[1], values).value()) {
                return new Expression(branch[2], values).value();
            }
        }
        return new Expression(function.last, values).value();
    }

    private final class Expression {
        private final String text;
        private final Map<String, Float> values;
        private int at;

        Expression(String text, Map<String, Float> values) {
            this.text = text;
            this.values = values;
        }

        float value() {
            float value = sum();
            skip();
            if (at != text.length()) {
                throw new IllegalArgumentException("left over in " + text + ": " + text.substring(at));
            }
            return value;
        }

        private void skip() {
            while (at < text.length() && text.charAt(at) == ' ') {
                at++;
            }
        }

        private boolean take(char c) {
            skip();
            if (at < text.length() && text.charAt(at) == c) {
                at++;
                return true;
            }
            return false;
        }

        private float sum() {
            float value = product();
            while (true) {
                if (take('+')) {
                    value += product();
                } else if (take('-')) {
                    value -= product();
                } else {
                    return value;
                }
            }
        }

        private float product() {
            float value = unary();
            while (true) {
                if (take('*')) {
                    value *= unary();
                } else if (take('/')) {
                    value /= unary();
                } else {
                    return value;
                }
            }
        }

        private float unary() {
            if (take('-')) {
                return -unary();
            }
            if (take('(')) {
                float value = sum();
                if (!take(')')) {
                    throw new IllegalArgumentException("no ) in " + text);
                }
                return value;
            }
            skip();
            int from = at;
            if (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.') {
                while (at < text.length() && (Character.isDigit(text.charAt(at)) || text.charAt(at) == '.')) {
                    at++;
                }
                return Float.parseFloat(text.substring(from, at));
            }
            while (at < text.length() && (Character.isLetterOrDigit(text.charAt(at)) || text.charAt(at) == '_')) {
                at++;
            }
            String name = text.substring(from, at);
            if (!take('(')) {
                Float value = values.get(name);
                if (value == null) {
                    throw new IllegalArgumentException("no value " + name + " in " + text);
                }
                return value;
            }
            List<Float> arguments = new java.util.ArrayList<>();
            if (!take(')')) {
                do {
                    arguments.add(sum());
                } while (take(','));
                if (!take(')')) {
                    throw new IllegalArgumentException("no ) after the arguments of " + name + " in " + text);
                }
            }
            float[] a = new float[arguments.size()];
            for (int i = 0; i < a.length; i++) {
                a[i] = arguments.get(i);
            }
            return switch (name) {
                case "abs" -> Math.abs(a[0]);
                case "clamp" -> Math.min(Math.max(a[0], a[1]), a[2]);
                case "step" -> a[1] < a[0] ? 0f : 1f;
                case "min" -> Math.min(a[0], a[1]);
                case "max" -> Math.max(a[0], a[1]);
                default -> call(name, a);
            };
        }
    }
}
