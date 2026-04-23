package me.playbosswar.tea.core.impl.theme;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import me.playbosswar.tea.core.api.theme.Theme;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

public final class ThemeLoader {

    private ThemeLoader() {}

    public static Theme load(Path themeFile) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(themeFile.toFile());

        Map<String, String> styles = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection("styles");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) styles.put(key, value);
            }
        }

        String prefix = yaml.getString("prefix", "");
        return new ThemeImpl(styles, prefix);
    }
}
