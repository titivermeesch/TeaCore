package me.playbosswar.tea.core.impl.menu;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.inventory.ClickType;
import org.junit.jupiter.api.Test;

class MenuDefClickTest {

    private static MenuDef.ItemDef item(String yaml) throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(yaml);
        return MenuDef.ItemDef.parse(c);
    }

    @Test
    void shiftLeftAndRightBeatTheGenericShiftList() throws Exception {
        MenuDef.ItemDef def = item("""
                actions: [any]
                left-actions: [left]
                right-actions: [right]
                shift-actions: [shift]
                shift-left-actions: [shift-left]
                shift-right-actions: [shift-right]
                """);
        assertThat(def.actionsFor(ClickType.LEFT)).containsExactly("left");
        assertThat(def.actionsFor(ClickType.RIGHT)).containsExactly("right");
        assertThat(def.actionsFor(ClickType.SHIFT_LEFT)).containsExactly("shift-left");
        assertThat(def.actionsFor(ClickType.SHIFT_RIGHT)).containsExactly("shift-right");
        assertThat(def.actionsFor(ClickType.MIDDLE)).containsExactly("any");
    }

    @Test
    void shiftListCoversBothSidesWhenTheSpecificOnesAreMissing() throws Exception {
        MenuDef.ItemDef def = item("""
                left-actions: [left]
                shift-actions: [shift]
                """);
        assertThat(def.actionsFor(ClickType.SHIFT_LEFT)).containsExactly("shift");
        assertThat(def.actionsFor(ClickType.SHIFT_RIGHT)).containsExactly("shift");
        assertThat(def.actionsFor(ClickType.LEFT)).containsExactly("left");
    }
}
