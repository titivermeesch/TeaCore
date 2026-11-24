package me.playbosswar.tea.core.impl.cloud.cloudnet3;

import de.dytanic.cloudnet.driver.CloudNetDriver;
import de.dytanic.cloudnet.driver.service.ServiceConfiguration;
import de.dytanic.cloudnet.driver.service.ServiceInfoSnapshot;
import de.dytanic.cloudnet.driver.service.ServiceLifeCycle;
import de.dytanic.cloudnet.driver.service.ServiceTask;
import de.dytanic.cloudnet.driver.service.ServiceTemplate;
import de.dytanic.cloudnet.driver.template.TemplateStorage;
import de.dytanic.cloudnet.ext.bridge.BridgeServiceProperty;
import de.dytanic.cloudnet.ext.bridge.player.IPlayerManager;
import de.dytanic.cloudnet.ext.bridge.player.executor.ServerSelectorType;
import de.dytanic.cloudnet.ext.bridge.server.BridgeServerHelper;
import de.dytanic.cloudnet.wrapper.Wrapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import me.playbosswar.tea.core.impl.cloud.CloudPlatform;
import org.bukkit.entity.Player;

/** {@link CloudPlatform} for CloudNet v3 (3.4.x) with its Bridge module. */
public final class CloudNet3Platform implements CloudPlatform {

    private final CloudNetDriver driver = CloudNetDriver.getInstance();

    private IPlayerManager players() {
        return driver.getServicesRegistry().getFirstService(IPlayerManager.class);
    }

    @Override
    public String name() {
        return "CloudNet v3";
    }

    @Override
    public String serviceName() {
        return Wrapper.getInstance().getServiceId().getName();
    }

    @Override
    public String taskName() {
        return Wrapper.getInstance().getServiceId().getTaskName();
    }

    @Override
    public void connect(Player player, String serviceName) {
        players().getPlayerExecutor(player.getUniqueId()).connect(serviceName);
    }

    @Override
    public void connectToTask(Player player, String taskName) {
        players().getPlayerExecutor(player.getUniqueId()).connectToTask(taskName, ServerSelectorType.LOWEST_PLAYERS);
    }

    @Override
    public List<Service> services(String taskName) {
        List<Service> out = new ArrayList<>();
        for (ServiceInfoSnapshot s : driver.getCloudServiceProvider().getCloudServices(taskName)) {
            ServiceLifeCycle life = s.getLifeCycle();
            if (life == ServiceLifeCycle.STOPPED || life == ServiceLifeCycle.DELETED) {
                continue;
            }
            out.add(new Service(s.getServiceId().getName(), s.getServiceId().getTaskName(),
                    life == ServiceLifeCycle.RUNNING
                            && s.getProperty(BridgeServiceProperty.IS_ONLINE).orElse(false),
                    s.getProperty(BridgeServiceProperty.ONLINE_COUNT).orElse(0)));
        }
        return out;
    }

    @Override
    public void startService(String taskName) {
        ServiceTask task = driver.getServiceTaskProvider().getServiceTask(taskName);
        if (task == null) {
            throw new IllegalArgumentException("No CloudNet task called " + taskName);
        }
        ServiceInfoSnapshot created = ServiceConfiguration.builder(task).build().createNewService();
        if (created != null) {
            created.provider().start();
        }
    }

    @Override
    public void stopService(String serviceName) {
        ServiceInfoSnapshot s = driver.getCloudServiceProvider().getCloudServiceByName(serviceName);
        if (s != null) {
            s.provider().stop();
        }
    }

    @Override
    public void publishState(boolean ingame, String extra, String motd, int maxPlayers) {
        BridgeServerHelper.setState(ingame ? "INGAME" : "LOBBY");
        BridgeServerHelper.setExtra(extra);
        BridgeServerHelper.setMotd(motd);
        if (maxPlayers > 0) {
            BridgeServerHelper.setMaxPlayers(maxPlayers);
        }
        BridgeServerHelper.updateServiceInfo();
    }

    @Override
    public boolean deploy(Path dir, String template) {
        String[] parts = template.split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Template must be Prefix/name or Prefix/name/storage: " + template);
        }
        String storageName = parts.length > 2 ? parts[2] : ServiceTemplate.LOCAL_STORAGE;
        TemplateStorage storage = driver.getTemplateStorage(storageName);
        if (storage == null) {
            throw new IllegalArgumentException("No CloudNet template storage called " + storageName);
        }
        ServiceTemplate target = new ServiceTemplate(parts[0], parts[1], storageName);
        if (!storage.has(target)) {
            storage.create(target);
        }
        return storage.deploy(dir, target);
    }
}
