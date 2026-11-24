package me.playbosswar.tea.core.impl.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.playbosswar.tea.core.api.placeholder.PlaceholderService;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Only loaded when PlaceholderAPI is installed. */
public final class TeaPlaceholderExpansion extends PlaceholderExpansion {

    private final PlaceholderService service;
    private final String version;

    public TeaPlaceholderExpansion(PlaceholderService service, String version) {
        this.service = service;
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "tea";
    }

    @Override
    public @NotNull String getAuthor() {
        return "PlayBossWar";
    }

    @Override
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        return service.resolve(player, params).orElse(null);
    }
}
