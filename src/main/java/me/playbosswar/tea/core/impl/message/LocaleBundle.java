package me.playbosswar.tea.core.impl.message;

import java.util.Map;

public record LocaleBundle(String locale, Map<String, String> entries) {

    public LocaleBundle {
        entries = Map.copyOf(entries);
    }

    public String get(String key) {
        return entries.get(key);
    }
}
