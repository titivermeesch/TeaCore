package me.playbosswar.tea.core.impl.settings;

import java.lang.reflect.Constructor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.BaseConstructor;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * A {@link Yaml} that only builds maps, lists and scalars, never objects named
 * by a tag. SnakeYAML comes from the server, so the constructor is looked up at
 * runtime: 1.x has {@code SafeConstructor()}, 2.x only
 * {@code SafeConstructor(LoaderOptions)}.
 */
public final class SafeYaml {

    private SafeYaml() {
    }

    public static Yaml create() {
        return new Yaml(safeConstructor());
    }

    static BaseConstructor safeConstructor() {
        try {
            Class<?> optionsClass = Class.forName("org.yaml.snakeyaml.LoaderOptions");
            Constructor<SafeConstructor> c = SafeConstructor.class.getConstructor(optionsClass);
            return c.newInstance(optionsClass.getConstructor().newInstance());
        } catch (ReflectiveOperationException ignored) {
            // SnakeYAML 1.x before LoaderOptions, or 1.x where SafeConstructor doesn't take it yet
        }
        try {
            return SafeConstructor.class.getConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No usable SnakeYAML SafeConstructor", e);
        }
    }
}
