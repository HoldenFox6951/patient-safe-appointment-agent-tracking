package dev.clinic.agent.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Json {
    private final String source;
    private int cursor;

    private Json(String source) { this.source = source; }

    static Map<String, Object> object(String source) {
        Object value = new Json(source).readDocument();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON root is not an object");
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 32) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private Object readDocument() {
        Object value = readValue();
        space();
        if (cursor != source.length()) fail("trailing content");
        return value;
    }

    private Object readValue() {
        space();
        if (cursor >= source.length()) return fail("missing value");
        return switch (source.charAt(cursor)) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> readNumber();
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        cursor++;
        space();
        if (take('}')) return map;
        do {
            space();
            String key = readString();
            space();
            expect(':');
            map.put(key, readValue());
            space();
        } while (take(','));
        expect('}');
        return map;
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        cursor++;
        space();
        if (take(']')) return list;
        do { list.add(readValue()); space(); } while (take(','));
        expect(']');
        return list;
    }

    private String readString() {
        expect('"');
        StringBuilder out = new StringBuilder();
        while (cursor < source.length()) {
            char c = source.charAt(cursor++);
            if (c == '"') return out.toString();
            if (c != '\\') { out.append(c); continue; }
            if (cursor >= source.length()) return fail("unfinished escape");
            char escaped = source.charAt(cursor++);
            switch (escaped) {
                case '"', '\\', '/' -> out.append(escaped);
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (cursor + 4 > source.length()) return fail("unfinished unicode escape");
                    out.append((char) Integer.parseInt(source.substring(cursor, cursor + 4), 16));
                    cursor += 4;
                }
                default -> { return fail("invalid escape"); }
            }
        }
        return fail("unfinished string");
    }

    private Object readNumber() {
        int start = cursor;
        while (cursor < source.length() && "-+0123456789.eE".indexOf(source.charAt(cursor)) >= 0) cursor++;
        if (start == cursor) return fail("invalid value");
        String number = source.substring(start, cursor);
        try { return number.contains(".") || number.contains("e") || number.contains("E")
                ? Double.valueOf(number) : Long.valueOf(number); }
        catch (NumberFormatException e) { return fail("invalid number"); }
    }

    private Object literal(String text, Object value) {
        if (!source.startsWith(text, cursor)) return fail("invalid literal");
        cursor += text.length();
        return value;
    }

    private void space() { while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++; }
    private boolean take(char expected) { if (cursor < source.length() && source.charAt(cursor) == expected) { cursor++; return true; } return false; }
    private void expect(char expected) { if (!take(expected)) fail("expected " + expected); }
    private <T> T fail(String message) { throw new IllegalArgumentException(message + " at character " + cursor); }
}
