package me.playbosswar.tea.core.impl.menu.bedrock;

import java.util.List;
import java.util.function.IntConsumer;
import org.bukkit.entity.Player;

public interface BedrockBridge {

    record Form(String title, String content, List<String> buttons, IntConsumer onPick, Runnable onClose) { }

    boolean isBedrock(Player player);

    /** {@code onPick} (button index) or {@code onClose} runs on the main thread. */
    void send(Player player, Form form);
}
