package me.playbosswar.tea.core.impl.platform.modern;

import com.cryptomorin.xseries.particles.XParticle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import me.playbosswar.tea.core.api.platform.EntityControl;
import me.playbosswar.tea.core.api.platform.Holograms;
import me.playbosswar.tea.core.api.platform.ItemTags;
import me.playbosswar.tea.core.api.platform.Items;
import me.playbosswar.tea.core.api.platform.Particles;
import me.playbosswar.tea.core.api.platform.Platform;
import me.playbosswar.tea.core.api.platform.Players;
import me.playbosswar.tea.core.api.platform.ServerVersion;
import me.playbosswar.tea.core.api.platform.Sounds;
import me.playbosswar.tea.core.api.platform.Worlds;
import me.playbosswar.tea.core.impl.platform.XItems;
import me.playbosswar.tea.core.impl.platform.XSounds;
import org.bukkit.Color;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ModernPlatform implements Platform, me.playbosswar.tea.core.impl.platform.PlatformInternals {

    private final Plugin plugin;
    private final ServerVersion version;
    private final Items items = new XItems((type, amount) -> {
        ItemStack item = new ItemStack(org.bukkit.Material.POTION, amount);
        if (item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta meta) {
            try {
                meta.setBasePotionType(org.bukkit.potion.PotionType.valueOf(type));
                item.setItemMeta(meta);
            } catch (IllegalArgumentException | NoSuchMethodError e) {
                return null;
            }
        }
        return item;
    });
    private final Sounds sounds = new XSounds();

    public ModernPlatform(Plugin plugin, ServerVersion version) {
        this.plugin = plugin;
        this.version = version;
        plugin.getServer().getPluginManager().registerEvents(new ModernListeners(), plugin);
    }

    @Override public ServerVersion version() { return version; }

    private final me.playbosswar.tea.core.impl.map.SchematicStore schematics = new ModernSchematics();

    @Override public me.playbosswar.tea.core.impl.map.SchematicStore schematics() { return schematics; }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case TEXT_DISPLAYS -> version.atLeast(19, 4);
            case OFF_HAND, ATTACK_COOLDOWN, EFFECT_CLOUDS -> true;
            case HEX_COLORS -> version.atLeast(16);
            case NEGATIVE_WORLD_HEIGHT -> version.atLeast(18);
            case PARTICLE_OPTIONS -> true;
        };
    }

    private final ItemTags tags = new ItemTags() {
        private NamespacedKey key(String owner, String key) {
            return new NamespacedKey(owner.toLowerCase(Locale.ROOT), key.toLowerCase(Locale.ROOT));
        }

        @Override
        public ItemStack set(ItemStack item, String owner, String key, String value) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().set(key(owner, key), PersistentDataType.STRING, value);
                item.setItemMeta(meta);
            }
            return item;
        }

        @Override
        public String get(ItemStack item, String owner, String key) {
            if (item == null || !item.hasItemMeta()) return null;
            return read(item.getItemMeta().getPersistentDataContainer(), key(owner, key));
        }

        @Override
        public ItemStack remove(ItemStack item, String owner, String key) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.getPersistentDataContainer().remove(key(owner, key));
                item.setItemMeta(meta);
            }
            return item;
        }

        @Override
        public void set(Entity entity, String owner, String key, String value) {
            entity.getPersistentDataContainer().set(key(owner, key), PersistentDataType.STRING, value);
        }

        @Override
        public String get(Entity entity, String owner, String key) {
            return entity == null ? null : read(entity.getPersistentDataContainer(), key(owner, key));
        }

        /** Also reads marks older TeaCore versions stored as other types. */
        private String read(org.bukkit.persistence.PersistentDataContainer pdc, NamespacedKey key) {
            if (!pdc.has(key)) return null;
            try {
                return pdc.get(key, PersistentDataType.STRING);
            } catch (IllegalArgumentException otherType) {
                return "1";
            }
        }
    };

    @Override public ItemTags tags() { return tags; }

    // Text displays (armor stands before 1.19.4).
    private final Holograms holograms = new Holograms() {
        @Override
        public Hologram create(Location location, List<String> lines) {
            if (!supports(Feature.TEXT_DISPLAYS)) {
                return new StandHologram(location, lines);
            }
            TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setPersistent(false);
                d.setShadowed(true);
                d.setDefaultBackground(false);
                d.setText(String.join("\n", lines));
            });
            return new Hologram() {
                @Override public void setLines(List<String> l) { display.setText(String.join("\n", l)); }
                @Override public void teleport(Location to) { display.teleport(to); }
                @Override public Location location() { return display.getLocation(); }
                @Override public boolean isValid() { return display.isValid(); }
                @Override public void remove() { display.remove(); }
            };
        }
    };

    @Override public Holograms holograms() { return holograms; }

    private static Optional<Particle> particle(String name) {
        return XParticle.of(name.toUpperCase(Locale.ROOT)).filter(XParticle::isSupported).map(XParticle::get);
    }

    private final Particles particles = new Particles() {
        @Override
        public void spawn(String name, Location at, int count, double dx, double dy, double dz, double speed, Player viewer) {
            particle(name).ifPresent(p -> {
                if (p.getDataType() != Void.class) return; // needs options: use dust()
                if (viewer != null) viewer.spawnParticle(p, at, count, dx, dy, dz, speed);
                else at.getWorld().spawnParticle(p, at, count, dx, dy, dz, speed, null, true);
            });
        }

        @Override
        public void dust(Location at, Color color, float size, int count, double spread, Player viewer) {
            particle("DUST").ifPresent(p -> {
                Particle.DustOptions opts = new Particle.DustOptions(color, size);
                if (viewer != null) viewer.spawnParticle(p, at, count, spread, spread, spread, 0, opts);
                else at.getWorld().spawnParticle(p, at, count, spread, spread, spread, 0, opts, true);
            });
        }
    };

    @Override public Particles particles() { return particles; }

    private final EntityControl entities = new EntityControl() {
        @Override public void setAi(LivingEntity entity, boolean ai) { entity.setAI(ai); }
        @Override public void setInvulnerable(Entity entity, boolean v) { entity.setInvulnerable(v); }
        @Override public void setSilent(Entity entity, boolean v) { entity.setSilent(v); }
        @Override public void setGravity(Entity entity, boolean v) { entity.setGravity(v); }
        @Override public void setGlowing(Entity entity, boolean v) { entity.setGlowing(v); }
        @Override public void setCollidable(LivingEntity entity, boolean v) { entity.setCollidable(v); }

        @Override
        public double maxHealth(LivingEntity entity) {
            AttributeInstance a = entity.getAttribute(maxHealthAttribute());
            return a == null ? 20.0 : a.getValue();
        }

        @Override
        public double attribute(LivingEntity entity, String modernName) {
            Attribute type = attributeType(modernName);
            AttributeInstance a = type == null ? null : entity.getAttribute(type);
            return a == null ? Double.NaN : a.getValue();
        }

        @Override
        public void setAttributeBase(LivingEntity entity, String modernName, double value) {
            Attribute type = attributeType(modernName);
            AttributeInstance a = type == null ? null : entity.getAttribute(type);
            if (a != null) {
                a.setBaseValue(value);
            }
        }

        @Override
        public Player playerBehind(Entity damager) {
            if (damager instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.AreaEffectCloud cloud && cloud.getSource() instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
            return null;
        }
    };

    /** Attributes lost their GENERIC_ prefix in 1.21.3; try both names. */
    private static Attribute attributeType(String modernName) {
        String name = modernName.toUpperCase(java.util.Locale.ROOT);
        for (String candidate : new String[] {name, "GENERIC_" + name, "PLAYER_" + name}) {
            try {
                return (Attribute) Attribute.class.getField(candidate).get(null);
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
        return null;
    }

    /** GENERIC_MAX_HEALTH was renamed MAX_HEALTH in 1.21.3. */
    private static Attribute maxHealthAttribute() {
        for (String name : new String[] {"MAX_HEALTH", "GENERIC_MAX_HEALTH"}) {
            try {
                return (Attribute) Attribute.class.getField(name).get(null);
            } catch (ReflectiveOperationException ignored) {
                // try the next name
            }
        }
        throw new IllegalStateException("no max health attribute");
    }

    @Override public EntityControl entities() { return entities; }

    @Override public Items items() { return items; }

    @Override public Sounds sounds() { return sounds; }

    private final Worlds worlds = new Worlds() {
        @Override public int minHeight(World world) { return world.getMinHeight(); }
        @Override public int maxHeight(World world) { return world.getMaxHeight(); }
        @Override public void explode(Location at, float power, boolean fire, boolean breakBlocks, Entity source) {
            at.getWorld().createExplosion(at, power, fire, breakBlocks, source);
        }
        @Override public boolean isChunkGenerated(World world, int x, int z) { return world.isChunkGenerated(x, z); }
        @Override public boolean isAir(org.bukkit.ChunkSnapshot s, int x, int y, int z) { return s.getBlockType(x, y, z).isAir(); }

        @Override
        @SuppressWarnings({"unchecked", "rawtypes"})
        public void setGameRule(World world, String rule, String value) {
            GameRule gr = GameRule.getByName(rule);
            if (gr == null) return;
            if (gr.getType() == Boolean.class) world.setGameRule(gr, Boolean.parseBoolean(value));
            else if (gr.getType() == Integer.class) world.setGameRule(gr, Integer.parseInt(value));
        }

        @Override
        public WorldCreator voidWorld(String name) {
            return new WorldCreator(name).generator(new ModernVoidGenerator());
        }
    };

    @Override public Worlds worlds() { return worlds; }

    private final Players players = new Players() {
        @Override public String locale(Player p) { return p.getLocale(); }
        @Override public ItemStack mainHand(Player p) { return p.getInventory().getItemInMainHand(); }
        @Override public void setMainHand(Player p, ItemStack item) { p.getInventory().setItemInMainHand(item); }
        @Override public ItemStack[] storage(Player p) { return p.getInventory().getStorageContents(); }
        @Override public void setStorage(Player p, ItemStack[] items) { p.getInventory().setStorageContents(items); }
        @Override public boolean isMainHand(PlayerInteractEvent e) { return e.getHand() == EquipmentSlot.HAND; }
        @Override public boolean isMainHand(org.bukkit.event.player.PlayerInteractEntityEvent e) { return e.getHand() == EquipmentSlot.HAND; }

        @Override
        public void setVisible(Player viewer, Player target, boolean visible) {
            if (visible) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }

        @Override public Block targetBlock(Player p, int range) { return p.getTargetBlockExact(range); }
    };

    @Override public Players players() { return players; }

    private final me.playbosswar.tea.core.api.platform.Scoreboards scoreboards = new me.playbosswar.tea.core.api.platform.Scoreboards() {
        @Override
        public org.bukkit.scoreboard.Objective newObjective(org.bukkit.scoreboard.Scoreboard board, String name, String title) {
            return board.registerNewObjective(name, org.bukkit.scoreboard.Criteria.DUMMY, title);
        }

        @Override
        public void setTeamColor(org.bukkit.scoreboard.Team team, org.bukkit.ChatColor color) {
            if (team.getColor() != color) team.setColor(color);
        }

        @Override public int maxAffixLength() { return 64; }
        @Override public int maxTitleLength() { return 128; }
    };

    @Override public me.playbosswar.tea.core.api.platform.Scoreboards scoreboards() { return scoreboards; }

    private final me.playbosswar.tea.core.api.platform.Projectiles projectiles = new me.playbosswar.tea.core.api.platform.Projectiles() {
        @Override public Entity hitEntity(org.bukkit.event.entity.ProjectileHitEvent e) { return e.getHitEntity(); }
        @Override public Block hitBlock(org.bukkit.event.entity.ProjectileHitEvent e) { return e.getHitBlock(); }
        @Override public org.bukkit.block.BlockFace hitFace(org.bukkit.event.entity.ProjectileHitEvent e) { return e.getHitBlockFace(); }
    };

    @Override public me.playbosswar.tea.core.api.platform.Projectiles projectiles() { return projectiles; }

    private final me.playbosswar.tea.core.api.platform.Effects effects = new me.playbosswar.tea.core.impl.platform.XEffects();

    @Override public me.playbosswar.tea.core.api.platform.Effects effects() { return effects; }

    @Override public boolean isPassable(Block block) { return block.isPassable(); }

    /** Armor-stand holograms for 1.13 - 1.19.3. */
    private static final class StandHologram implements Holograms.Hologram {
        private final List<org.bukkit.entity.ArmorStand> stands = new ArrayList<>();
        private Location base;

        StandHologram(Location location, List<String> lines) {
            this.base = location.clone();
            setLines(lines);
        }

        @Override
        public void setLines(List<String> lines) {
            while (stands.size() > lines.size()) stands.remove(stands.size() - 1).remove();
            for (int i = 0; i < lines.size(); i++) {
                Location at = base.clone().add(0, (lines.size() - 1 - i) * 0.27, 0);
                if (i >= stands.size()) {
                    org.bukkit.entity.ArmorStand s = base.getWorld().spawn(at, org.bukkit.entity.ArmorStand.class, a -> {
                        a.setVisible(false);
                        a.setMarker(true);
                        a.setGravity(false);
                        a.setPersistent(false);
                        a.setCustomNameVisible(true);
                    });
                    stands.add(s);
                }
                stands.get(i).setCustomName(lines.get(i));
            }
        }

        @Override
        public void teleport(Location to) {
            base = to.clone();
            for (int i = 0; i < stands.size(); i++) {
                stands.get(i).teleport(base.clone().add(0, (stands.size() - 1 - i) * 0.27, 0));
            }
        }

        @Override public Location location() { return base.clone(); }
        @Override public boolean isValid() { return !stands.isEmpty() && stands.get(0).isValid(); }
        @Override public void remove() { stands.forEach(Entity::remove); stands.clear(); }
    }
}
