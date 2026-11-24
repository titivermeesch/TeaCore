package me.playbosswar.tea.core.impl.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * A settings schema: which values of one YAML file staff may edit, grouped in
 * categories. Parsed from a {@code settings/*.yml} resource; the same map is
 * sent to other servers and the dashboard as JSON.
 *
 * <p>Paths inside a {@link Type#MAP} field address an entry and then a child
 * field ({@code kits.trickster.cost}); inside a {@link Type#LIST} field an index
 * comes first ({@code kits.trickster.gear[2].amount}).
 */
public record SettingsSchema(String id, String title, String file, String icon, Apply apply,
                             List<Category> categories, Map<String, Object> source) {

    public enum Type { BOOLEAN, INT, NUMBER, TEXT, ENUM, MATERIAL, TEXT_LIST, MAP, LIST }

    /** When a change takes effect. */
    public enum Apply { LIVE, NEXT_MATCH, RESTART }

    public record Category(String id, String title, String icon, List<Field> fields) { }

    /**
     * @param path          dotted path, relative to the map entry or list item for child fields
     * @param fields        child fields of a MAP entry or LIST item
     * @param newEntry      starting values of an entry added in a MAP field
     * @param fromInventory LIST of items that staff can fill from their inventory in game
     */
    public record Field(String path, Type type, String title, String help, String icon,
                        @Nullable Double min, @Nullable Double max, double step, List<String> options,
                        Apply apply, boolean secret, List<Field> fields, Map<String, Object> newEntry,
                        boolean fromInventory) {

        public boolean isContainer() {
            return type == Type.MAP || type == Type.LIST;
        }

        public Optional<Field> child(String relativePath) {
            return fields.stream().filter(f -> f.path.equals(relativePath)).findFirst();
        }
    }

    public enum Kind { VALUE, ENTRY, ITEM }

    /**
     * Where a path points.
     *
     * @param field     the field at the path; for ENTRY and ITEM the MAP or LIST field holding it
     * @param kind      a plain value, a whole MAP entry, or a whole LIST item
     * @param top       the top-level field the path starts in
     * @param listPath  the full path of the outermost LIST the path runs through, else {@code null};
     *                  changes inside a list are written as the whole list
     */
    public record Resolved(Field field, Kind kind, Field top, @Nullable String listPath) { }

    private static final Pattern ENTRY_ID = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Pattern MATERIAL = Pattern.compile("[A-Z0-9_]+");

    public static boolean validEntryId(String id) {
        return id != null && ENTRY_ID.matcher(id).matches();
    }

    public List<Field> fields() {
        List<Field> all = new ArrayList<>();
        for (Category c : categories) {
            all.addAll(c.fields);
        }
        return all;
    }

    public Optional<Category> category(String id) {
        return categories.stream().filter(c -> c.id.equals(id)).findFirst();
    }

    public Optional<Resolved> resolve(String path) {
        for (Field f : fields()) {
            Optional<Resolved> r = resolveIn(f, f, path, f.path, null);
            if (r.isPresent()) {
                return r;
            }
        }
        return Optional.empty();
    }

    private static Optional<Resolved> resolveIn(Field top, Field f, String path, String fullPath,
                                                @Nullable String listPath) {
        if (path.equals(f.path)) {
            return Optional.of(new Resolved(f, Kind.VALUE, top, listPath));
        }
        if (f.type == Type.MAP && path.startsWith(f.path + ".")) {
            String rest = path.substring(f.path.length() + 1);
            int dot = rest.indexOf('.');
            String entry = dot < 0 ? rest : rest.substring(0, dot);
            if (!validEntryId(entry)) {
                return Optional.empty();
            }
            if (dot < 0) {
                return Optional.of(new Resolved(f, Kind.ENTRY, top, listPath));
            }
            String inner = rest.substring(dot + 1);
            for (Field c : f.fields) {
                Optional<Resolved> r = resolveIn(top, c, inner, fullPath + "." + entry + "." + c.path, listPath);
                if (r.isPresent()) {
                    return r;
                }
            }
            return Optional.empty();
        }
        if (f.type == Type.LIST && path.startsWith(f.path + "[")) {
            String rest = path.substring(f.path.length() + 1);
            int close = rest.indexOf(']');
            if (close <= 0) {
                return Optional.empty();
            }
            try {
                Integer.parseInt(rest.substring(0, close));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
            String list = listPath != null ? listPath : fullPath;
            String after = rest.substring(close + 1);
            if (after.isEmpty()) {
                return Optional.of(new Resolved(f, Kind.ITEM, top, list));
            }
            if (!after.startsWith(".")) {
                return Optional.empty();
            }
            for (Field c : f.fields) {
                Optional<Resolved> r = resolveIn(top, c, after.substring(1), fullPath, list);
                if (r.isPresent()) {
                    return r;
                }
            }
        }
        return Optional.empty();
    }

    // ---------------------------------------------------------------- parsing

    public static SettingsSchema parse(Map<String, Object> raw) {
        String id = str(raw, "id", null);
        String file = str(raw, "file", null);
        if (id == null || file == null) {
            throw new IllegalArgumentException("a settings schema needs an id and a file");
        }
        Apply apply = apply(str(raw, "apply", null), Apply.LIVE);
        List<Category> categories = new ArrayList<>();
        for (Object o : list(raw.get("categories"))) {
            Map<String, Object> c = map(o);
            List<Field> fields = new ArrayList<>();
            for (Object f : list(c.get("fields"))) {
                fields.add(field(map(f), apply));
            }
            String cid = str(c, "id", String.valueOf(categories.size()));
            categories.add(new Category(cid, str(c, "title", cid), str(c, "icon", "PAPER"), List.copyOf(fields)));
        }
        return new SettingsSchema(id, str(raw, "title", id), file, str(raw, "icon", "COMPARATOR"), apply,
                List.copyOf(categories), raw);
    }

    private static Field field(Map<String, Object> f, Apply inherited) {
        String path = str(f, "path", null);
        if (path == null) {
            throw new IllegalArgumentException("a setting needs a path");
        }
        Type type;
        try {
            type = Type.valueOf(str(f, "type", "text").toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown type for " + path + ": " + f.get("type"));
        }
        Apply apply = apply(str(f, "apply", null), inherited);
        List<Field> children = new ArrayList<>();
        for (Object c : list(f.get("fields"))) {
            children.add(field(map(c), apply));
        }
        List<String> options = new ArrayList<>();
        for (Object o : list(f.get("options"))) {
            options.add(String.valueOf(o));
        }
        Object step = f.get("step");
        return new Field(path, type, str(f, "title", path), str(f, "help", ""), str(f, "icon", null),
                num(f.get("min")), num(f.get("max")),
                step instanceof Number n ? n.doubleValue() : (type == Type.NUMBER ? 0.1 : 1),
                List.copyOf(options), apply, Boolean.TRUE.equals(f.get("secret")), List.copyOf(children),
                f.get("new-entry") instanceof Map<?, ?> m ? copy(m) : Map.of(),
                Boolean.TRUE.equals(f.get("from-inventory")));
    }

    private static Apply apply(@Nullable String s, Apply fallback) {
        if (s == null) {
            return fallback;
        }
        return Apply.valueOf(s.toUpperCase(Locale.ROOT).replace('-', '_'));
    }

    // ------------------------------------------------------------- validation

    /**
     * @return the value in the form it is written to the file
     * @throws IllegalArgumentException with a message for staff when it doesn't fit
     */
    public static Object validate(Field field, Object value) {
        if (value == null) {
            throw new IllegalArgumentException(field.title + " needs a value");
        }
        return switch (field.type) {
            case BOOLEAN -> {
                if (value instanceof Boolean b) yield b;
                String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
                if (s.equals("true") || s.equals("on") || s.equals("yes")) yield true;
                if (s.equals("false") || s.equals("off") || s.equals("no")) yield false;
                throw new IllegalArgumentException(field.title + " is on or off");
            }
            case INT -> {
                BigDecimal d = decimal(field, value);
                if (d.stripTrailingZeros().scale() > 0) {
                    throw new IllegalArgumentException(field.title + " must be a whole number");
                }
                range(field, d.doubleValue());
                long l = d.longValueExact();
                yield l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE ? (Object) (int) l : (Object) l;
            }
            case NUMBER -> {
                double d = decimal(field, value).doubleValue();
                range(field, d);
                yield d == Math.rint(d) && Math.abs(d) < Integer.MAX_VALUE ? (Object) (int) d : (Object) d;
            }
            case TEXT -> {
                if (value instanceof Map || value instanceof List) {
                    throw new IllegalArgumentException(field.title + " is text");
                }
                yield String.valueOf(value);
            }
            case ENUM -> {
                String s = String.valueOf(value);
                yield field.options.stream().filter(o -> o.equalsIgnoreCase(s)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException(field.title + " must be one of " + field.options));
            }
            case MATERIAL -> {
                String s = String.valueOf(value).trim().toUpperCase(Locale.ROOT).replace(' ', '_');
                if (s.startsWith("MINECRAFT:")) s = s.substring("MINECRAFT:".length());
                if (!MATERIAL.matcher(s).matches()) {
                    throw new IllegalArgumentException(field.title + ": " + value + " is not a material name");
                }
                yield s;
            }
            case TEXT_LIST -> {
                if (!(value instanceof List<?> l)) {
                    throw new IllegalArgumentException(field.title + " is a list");
                }
                List<String> out = new ArrayList<>();
                for (Object o : l) {
                    if (o instanceof Map || o instanceof List) {
                        throw new IllegalArgumentException(field.title + " is a list of text");
                    }
                    out.add(String.valueOf(o));
                }
                yield out;
            }
            case MAP -> {
                if (!(value instanceof Map<?, ?> m)) {
                    throw new IllegalArgumentException(field.title + " is a set of entries");
                }
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) {
                    String key = String.valueOf(e.getKey());
                    if (!validEntryId(key)) {
                        throw new IllegalArgumentException("Ids use letters, digits, - and _ only: " + key);
                    }
                    out.put(key, validateEntry(field, e.getValue()));
                }
                yield out;
            }
            case LIST -> {
                if (!(value instanceof List<?> l)) {
                    throw new IllegalArgumentException(field.title + " is a list");
                }
                List<Object> out = new ArrayList<>();
                for (Object o : l) {
                    out.add(validateEntry(field, o));
                }
                yield out;
            }
        };
    }

    /** An entry of a MAP field or an item of a LIST field. Keys the schema doesn't know are kept. */
    public static Map<String, Object> validateEntry(Field container, Object value) {
        Map<String, Object> entry;
        if (value instanceof Map<?, ?> m) {
            entry = copy(m);
        } else if (value instanceof String s && container.child("material").isPresent()) {
            entry = new LinkedHashMap<>();
            entry.put("material", s);
        } else {
            throw new IllegalArgumentException(container.title + ": each entry has fields");
        }
        for (Field child : container.fields) {
            Object v = SettingsValues.get(entry, child.path);
            if (v != null) {
                SettingsValues.put(entry, child.path, validate(child, v));
            }
        }
        return entry;
    }

    private static BigDecimal decimal(Field field, Object value) {
        try {
            return new BigDecimal(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(field.title + " must be a number");
        }
    }

    private static void range(Field field, double d) {
        if (field.min != null && d < field.min) {
            throw new IllegalArgumentException(field.title + " can't be lower than " + plain(field.min));
        }
        if (field.max != null && d > field.max) {
            throw new IllegalArgumentException(field.title + " can't be higher than " + plain(field.max));
        }
    }

    public static String plain(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ---------------------------------------------------------------- helpers

    static String str(Map<String, Object> m, String key, String fallback) {
        Object v = m.get(key);
        return v == null ? fallback : String.valueOf(v);
    }

    private static @Nullable Double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : null;
    }

    static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    static Map<String, Object> map(Object o) {
        return o instanceof Map<?, ?> m ? copy(m) : Map.of();
    }

    static Map<String, Object> copy(Map<?, ?> m) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) {
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return out;
    }
}
