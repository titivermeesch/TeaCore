package me.playbosswar.tea.core.impl.menu.bedrock;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.floodgate.api.FloodgateApi;

/** Sends forms through Floodgate. Only created when the Floodgate plugin is enabled. */
public final class FloodgateBridge implements BedrockBridge {

    private final Plugin plugin;

    public FloodgateBridge(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean isBedrock(Player player) {
        return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
    }

    @Override
    public void send(Player player, Form form) {
        SimpleForm.Builder builder = SimpleForm.builder().title(form.title()).content(form.content());
        for (String button : form.buttons()) {
            builder.button(button);
        }
        // Floodgate answers on a network thread; menus run on the main thread.
        builder.validResultHandler(response -> Bukkit.getScheduler().runTask(plugin,
                () -> form.onPick().accept(response.clickedButtonId())));
        builder.closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, form.onClose()));
        FloodgateApi.getInstance().sendForm(player.getUniqueId(), builder.build());
    }
}
