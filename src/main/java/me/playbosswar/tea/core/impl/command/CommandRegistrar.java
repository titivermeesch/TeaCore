package me.playbosswar.tea.core.impl.command;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

/**
 * Reflectively manipulates Bukkit's {@link SimpleCommandMap} so commands declared
 * in {@code plugin.yml} can be hidden at runtime when their feature is gated off
 * (e.g. {@code /chat} on a server with {@code lobby.enabled: true}).
 *
 * <p>Bukkit doesn't expose unregistration on its public API, so this helper goes
 * through {@code CraftServer#commandMap}. Tested against Spigot/Paper 1.20+; if
 * the field name ever changes the call logs a warning and falls back to leaving
 * the command in place — callers should still avoid wiring an executor in that
 * case so {@code /chat} prints just its usage line.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    /**
     * Hide a top-level command (and its aliases) from the command map. After this
     * call the command no longer appears in tab-complete or {@code /help}, and
     * Bukkit returns "Unknown command" when a player types it.
     */
    public static void unregister(String name, Logger logger) {
        try {
            Field mapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            mapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) mapField.get(Bukkit.getServer());
            Command cmd = commandMap.getCommand(name);
            if (cmd == null) return;
            cmd.unregister(commandMap);
            // SimpleCommandMap.getKnownCommands() isn't on the Spigot public API, so
            // reach into the protected field. Stripping it (plus aliases and the
            // plugin-namespaced "<plugin>:<name>" entries) is what actually hides
            // the command from tab-complete.
            Field knownField = SimpleCommandMap.class.getDeclaredField("knownCommands");
            knownField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<String, Command> known = (Map<String, Command>) knownField.get(commandMap);
            known.remove(name);
            for (String alias : cmd.getAliases()) known.remove(alias);
            known.keySet().removeIf(key -> key.endsWith(":" + name));
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to unregister command /" + name
                    + "; it will remain registered (likely with no executor).", e);
        }
    }
}
