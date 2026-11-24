package me.playbosswar.tea.core.impl.command;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.SimpleCommandMap;

/**
 * Bukkit has no public API to unregister commands, so this reaches into
 * {@code CraftServer#commandMap} to hide plugin.yml commands whose feature is gated
 * off. If the field is missing it logs a warning and leaves the command in place.
 */
public final class CommandRegistrar {

    private CommandRegistrar() {}

    public static void unregister(String name, Logger logger) {
        try {
            Field mapField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            mapField.setAccessible(true);
            SimpleCommandMap commandMap = (SimpleCommandMap) mapField.get(Bukkit.getServer());
            Command cmd = commandMap.getCommand(name);
            if (cmd == null) return;
            cmd.unregister(commandMap);
            // knownCommands is protected and not on the public API. Removing the name, its
            // aliases and the "<plugin>:<name>" entries is what hides it from tab-complete.
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
