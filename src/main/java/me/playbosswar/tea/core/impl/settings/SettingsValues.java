package me.playbosswar.tea.core.impl.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/** Dotted-path access to the maps and lists SnakeYAML loads. */
public final class SettingsValues {

    private SettingsValues() {
    }

    public static @Nullable Object get(@Nullable Object root, String path) {
        Object cur = root;
        for (String seg : path.split("\\.")) {
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(seg);
        }
        return cur;
    }

    /** Creates missing parents. A {@code null} value removes the key. */
    @SuppressWarnings("unchecked")
    public static void put(Map<String, Object> root, String path, @Nullable Object value) {
        String[] segs = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < segs.length - 1; i++) {
            Object next = cur.get(segs[i]);
            if (!(next instanceof Map)) {
                if (value == null) {
                    return;
                }
                next = new LinkedHashMap<String, Object>();
                cur.put(segs[i], next);
            } else if (!(next instanceof LinkedHashMap)) {
                next = new LinkedHashMap<>((Map<String, Object>) next);
                cur.put(segs[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        if (value == null) {
            cur.remove(segs[segs.length - 1]);
        } else {
            cur.put(segs[segs.length - 1], value);
        }
    }

    /** Copies maps and lists so a change can't leak into the original. */
    @SuppressWarnings("unchecked")
    public static <T> T deepCopy(T value) {
        if (value instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                out.put(String.valueOf(e.getKey()), deepCopy(e.getValue()));
            }
            return (T) out;
        }
        if (value instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            for (Object o : l) {
                out.add(deepCopy(o));
            }
            return (T) out;
        }
        return value;
    }

    /** Equality that treats {@code 5}, {@code 5L} and {@code 5.0} as the same number. */
    public static boolean same(@Nullable Object a, @Nullable Object b) {
        if (a instanceof Number x && b instanceof Number y) {
            return new BigDecimal(x.toString()).compareTo(new BigDecimal(y.toString())) == 0;
        }
        if (a instanceof Map<?, ?> x && b instanceof Map<?, ?> y) {
            if (x.size() != y.size()) {
                return false;
            }
            for (Map.Entry<?, ?> e : x.entrySet()) {
                if (!y.containsKey(e.getKey()) || !same(e.getValue(), y.get(e.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a instanceof List<?> x && b instanceof List<?> y) {
            if (x.size() != y.size()) {
                return false;
            }
            for (int i = 0; i < x.size(); i++) {
                if (!same(x.get(i), y.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return Objects.equals(a, b);
    }

    /** Like {@link #get} but also follows list indexes: {@code kits.runner.gear[2].amount}. */
    public static @Nullable Object at(@Nullable Object root, String path) {
        Object cur = root;
        for (String seg : path.split("\\.")) {
            int bracket = seg.indexOf('[');
            String key = bracket < 0 ? seg : seg.substring(0, bracket);
            if (!(cur instanceof Map<?, ?> m)) {
                return null;
            }
            cur = m.get(key);
            if (bracket >= 0) {
                int index = Integer.parseInt(seg.substring(bracket + 1, seg.length() - 1));
                if (!(cur instanceof List<?> l) || index < 0 || index >= l.size()) {
                    return null;
                }
                cur = l.get(index);
            }
        }
        return cur;
    }

    /**
     * Sets a value at a path with list indexes, creating maps on the way. At an
     * index equal to the list size the value is appended; a {@code null} value
     * removes the key or the list item.
     */
    @SuppressWarnings("unchecked")
    public static void setAt(Map<String, Object> root, String path, @Nullable Object value) {
        String[] segs = path.split("\\.");
        Object cur = root;
        for (int i = 0; i < segs.length; i++) {
            boolean last = i == segs.length - 1;
            String seg = segs[i];
            int bracket = seg.indexOf('[');
            String key = bracket < 0 ? seg : seg.substring(0, bracket);
            Map<String, Object> map = (Map<String, Object>) cur;
            if (bracket < 0) {
                if (last) {
                    if (value == null) {
                        map.remove(key);
                    } else {
                        map.put(key, value);
                    }
                    return;
                }
                Object next = map.get(key);
                if (!(next instanceof Map)) {
                    if (value == null) {
                        return;
                    }
                    next = new LinkedHashMap<String, Object>();
                    map.put(key, next);
                }
                cur = next;
                continue;
            }
            int index = Integer.parseInt(seg.substring(bracket + 1, seg.length() - 1));
            Object listObj = map.get(key);
            List<Object> list;
            if (listObj instanceof List<?> l) {
                list = (List<Object>) l;
            } else {
                list = new ArrayList<>();
                map.put(key, list);
            }
            if (last) {
                if (value == null) {
                    if (index < list.size()) {
                        list.remove(index);
                    }
                } else if (index < list.size()) {
                    list.set(index, value);
                } else if (index == list.size()) {
                    list.add(value);
                } else {
                    throw new IllegalArgumentException("no item " + (index + 1) + " in " + key);
                }
                return;
            }
            if (index >= list.size()) {
                throw new IllegalArgumentException("no item " + (index + 1) + " in " + key);
            }
            Object item = list.get(index);
            if (!(item instanceof Map)) {
                item = item instanceof String str ? new LinkedHashMap<>(Map.of("material", str))
                        : new LinkedHashMap<String, Object>();
                list.set(index, item);
            }
            cur = item;
        }
    }
}
