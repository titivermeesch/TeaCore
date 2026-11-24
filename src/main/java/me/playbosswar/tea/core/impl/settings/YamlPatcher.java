package me.playbosswar.tea.core.impl.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Changes one value in a YAML document by editing only the lines of that value,
 * so comments and layout elsewhere survive. Saving through Bukkit's
 * YamlConfiguration would drop every comment on servers before 1.18.
 *
 * <p>Handles block-style mappings. When the path runs into a value written in
 * flow style ({@code reward: { coins: 100 }}) or a scalar, the whole value of
 * that key is rewritten instead.
 */
public final class YamlPatcher {

    private static final Pattern KEY_LINE = Pattern.compile(
            "^( *)(\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^']|'')*'|[^\\s#'\"\\-][^:#]*?|-[^\\s][^:#]*?)\\s*:(?:\\s+(.*))?$");
    private static final Pattern PLAIN_KEY = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_\\-.]*");

    private YamlPatcher() {
    }

    /**
     * @param value a String, Number, Boolean, Map or List; {@code null} removes the key
     */
    public static String set(String text, String path, @Nullable Object value) {
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\n", -1)));
        boolean trailingNewline = text.endsWith("\n");
        if (trailingNewline) {
            lines.remove(lines.size() - 1);
        }
        String[] segs = path.split("\\.");
        apply(lines, segs, 0, 0, lines.size(), 0, text, value);
        String out = String.join("\n", lines);
        return trailingNewline || out.isEmpty() ? out + "\n" : out;
    }

    private static void apply(List<String> lines, String[] segs, int depth, int start, int end, int indent,
                              String original, @Nullable Object value) {
        String key = segs[depth];
        int at = findKey(lines, start, end, indent, key);
        if (at < 0) {
            if (value == null) {
                return;
            }
            Object nested = value;
            for (int i = segs.length - 1; i > depth; i--) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put(segs[i], nested);
                nested = m;
            }
            int insertAt = lastContentLine(lines, start, end) + 1;
            if (insertAt <= start && start > 0) {
                insertAt = start;
            }
            lines.addAll(insertAt, render(" ".repeat(indent), quoteKey(key), nested, null));
            return;
        }
        Matcher m = KEY_LINE.matcher(lines.get(at));
        if (!m.matches()) {
            throw new IllegalStateException("not a key line: " + lines.get(at));
        }
        String rawKey = m.group(2);
        String rest = m.group(3) == null ? "" : m.group(3);
        String inline = stripComment(rest);
        String comment = rest.substring(inline.length());
        int blockEnd = blockEnd(lines, at, end, indent);
        boolean last = depth == segs.length - 1;
        boolean blockValue = inline.isEmpty();

        if (!last && blockValue && blockEnd > at + 1 && !isSequence(lines, at + 1, blockEnd)) {
            int childIndent = indentOf(lines.get(firstContentLine(lines, at + 1, blockEnd)));
            if (childIndent > indent) {
                apply(lines, segs, depth + 1, at + 1, blockEnd, childIndent, original, value);
                return;
            }
        }

        Object newValue = value;
        if (!last) {
            Object current = SettingsValues.get(load(original),
                    String.join(".", Arrays.copyOfRange(segs, 0, depth + 1)));
            if (current != null && !(current instanceof Map)) {
                throw new IllegalArgumentException(String.join(".", Arrays.copyOfRange(segs, 0, depth + 1))
                        + " is not a section");
            }
            Map<String, Object> subtree = current == null ? new LinkedHashMap<>()
                    : SettingsSchema.copy((Map<?, ?>) SettingsValues.deepCopy(current));
            SettingsValues.put(subtree, String.join(".", Arrays.copyOfRange(segs, depth + 1, segs.length)), value);
            newValue = subtree;
        }
        for (int i = blockEnd - 1; i >= at; i--) {
            lines.remove(i);
        }
        if (newValue != null) {
            boolean keepComment = !comment.isBlank() && !(newValue instanceof Map<?, ?> nm && !nm.isEmpty())
                    && !(newValue instanceof List<?> nl && !nl.isEmpty());
            lines.addAll(at, render(" ".repeat(indent), rawKey, newValue, keepComment ? comment : null));
        }
    }

    // ------------------------------------------------------------- rendering

    private static List<String> render(String pad, String key, Object value, @Nullable String comment) {
        List<String> out = new ArrayList<>();
        if (value instanceof Map<?, ?> m && !m.isEmpty() || value instanceof List<?> l && !l.isEmpty()) {
            out.add(pad + key + ":");
            for (String line : dump(value).split("\n")) {
                if (!line.isEmpty()) {
                    out.add(pad + "  " + line);
                }
            }
            return out;
        }
        String scalar = value instanceof Map ? "{}" : value instanceof List ? "[]" : scalar(value);
        out.add(pad + key + ": " + scalar + (comment == null ? "" : comment));
        return out;
    }

    static String scalar(Object value) {
        if (value instanceof Boolean || value instanceof Number) {
            return String.valueOf(value);
        }
        String dumped = dump(String.valueOf(value)).trim();
        if (dumped.contains("\n")) {
            return "\"" + String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n") + "\"";
        }
        return dumped;
    }

    private static String dump(Object value) {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setIndent(2);
        options.setWidth(Integer.MAX_VALUE);
        options.setAllowUnicode(true);
        return new Yaml(options).dump(value);
    }

    private static String quoteKey(String key) {
        return PLAIN_KEY.matcher(key).matches() && !key.equals("true") && !key.equals("false")
                && !key.equals("null") && !key.equals("yes") && !key.equals("no")
                ? key : scalar(key);
    }

    // --------------------------------------------------------------- scanning

    public static Object load(String text) {
        Object doc = new Yaml().load(text);
        return doc == null ? new LinkedHashMap<String, Object>() : doc;
    }

    private static int findKey(List<String> lines, int start, int end, int indent, String key) {
        for (int i = start; i < end; i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line) || indentOf(line) != indent) {
                continue;
            }
            Matcher m = KEY_LINE.matcher(line);
            if (m.matches() && unquote(m.group(2)).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** Exclusive end of the lines that belong to the key at {@code at}, trailing comments excluded. */
    private static int blockEnd(List<String> lines, int at, int end, int indent) {
        int last = at;
        for (int i = at + 1; i < end; i++) {
            String line = lines.get(i);
            if (isBlankOrComment(line)) {
                continue;
            }
            int ind = indentOf(line);
            boolean sequenceAtSameIndent = ind == indent && line.trim().startsWith("-");
            if (ind > indent || sequenceAtSameIndent) {
                last = i;
            } else {
                break;
            }
        }
        return last + 1;
    }

    private static boolean isSequence(List<String> lines, int start, int end) {
        int first = firstContentLine(lines, start, end);
        return first >= 0 && lines.get(first).trim().startsWith("-");
    }

    private static int firstContentLine(List<String> lines, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!isBlankOrComment(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int lastContentLine(List<String> lines, int start, int end) {
        for (int i = end - 1; i >= start; i--) {
            if (!isBlankOrComment(lines.get(i))) {
                return i;
            }
        }
        return start - 1;
    }

    private static boolean isBlankOrComment(String line) {
        String t = line.trim();
        return t.isEmpty() || t.startsWith("#");
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        return i;
    }

    /** The value part of {@code rest} without a trailing {@code # comment}. Quotes are respected. */
    static String stripComment(String rest) {
        boolean single = false;
        boolean dbl = false;
        for (int i = 0; i < rest.length(); i++) {
            char c = rest.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
            } else if (c == '"' && !single && (i == 0 || rest.charAt(i - 1) != '\\')) {
                dbl = !dbl;
            } else if (c == '#' && !single && !dbl && (i == 0 || rest.charAt(i - 1) == ' ')) {
                int cut = i;
                while (cut > 0 && rest.charAt(cut - 1) == ' ') {
                    cut--;
                }
                return rest.substring(0, cut);
            }
        }
        return rest.stripTrailing();
    }

    private static String unquote(String key) {
        if (key.length() >= 2 && key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        if (key.length() >= 2 && key.startsWith("'") && key.endsWith("'")) {
            return key.substring(1, key.length() - 1).replace("''", "'");
        }
        return key.trim();
    }
}
