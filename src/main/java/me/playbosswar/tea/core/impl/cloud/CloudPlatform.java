package me.playbosswar.tea.core.impl.cloud;

import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/** The implementation lives in its own source set and is only loaded inside that cloud. */
public interface CloudPlatform {

    record Service(String name, String task, boolean running, int players) {
    }

    String name();

    String serviceName();

    String taskName();

    void connect(Player player, String serviceName);

    /** Picks the emptiest running service. */
    void connectToTask(Player player, String taskName);

    /** Includes services still starting. */
    List<Service> services(String taskName);

    void startService(String taskName);

    void stopService(String serviceName);

    /** @param ingame true once no arena on this server can take players */
    void publishState(boolean ingame, String extra, String motd, int maxPlayers);

    /** Copies {@code dir}'s contents into a template ("Prefix/name" or "Prefix/name/storage"). */
    boolean deploy(Path dir, String template);

    /** Does not load the integration. */
    static @Nullable String detectServiceName() {
        try {
            Class<?> wrapper = Class.forName("de.dytanic.cloudnet.wrapper.Wrapper");
            Object instance = wrapper.getMethod("getInstance").invoke(null);
            Object id = instance.getClass().getMethod("getServiceId").invoke(instance);
            return (String) id.getClass().getMethod("getName").invoke(id);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            return null;
        }
    }

    static @Nullable CloudPlatform detect(Logger logger) {
        if (detectServiceName() == null) {
            return null;
        }
        try {
            return (CloudPlatform) Class.forName("me.playbosswar.tea.core.impl.cloud.cloudnet3.CloudNet3Platform")
                    .getConstructor().newInstance();
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.warning("Running in CloudNet, but its API couldn't be loaded (" + e
                    + "). Is the CloudNet Bridge module installed? Continuing without the CloudNet integration.");
            return null;
        }
    }
}
