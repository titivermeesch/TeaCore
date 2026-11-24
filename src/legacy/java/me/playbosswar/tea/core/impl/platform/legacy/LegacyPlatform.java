package me.playbosswar.tea.core.impl.platform.legacy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
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
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;

/** {@link Platform} for 1.8 - 1.12 servers (tested on Spigot 1.8.8). */
public final class LegacyPlatform implements Platform, me.playbosswar.tea.core.impl.platform.PlatformInternals {

    private final Plugin plugin;
    private final ServerVersion version;
    private final Nms nms;
    @SuppressWarnings("deprecation")
    private final Items items = new XItems((type, amount) -> {
        // 1.8: STRONG_/LONG_ prefixes are levels/extended flags on the old Potion class.
        boolean strong = type.startsWith("STRONG_");
        boolean longer = type.startsWith("LONG_");
        String base = type.replaceFirst("^(STRONG_|LONG_)", "");
        base = switch (base) {
            case "HEALING" -> "INSTANT_HEAL";
            case "HARMING" -> "INSTANT_DAMAGE";
            case "SWIFTNESS" -> "SPEED";
            case "LEAPING" -> "JUMP";
            case "REGENERATION" -> "REGEN";
            default -> base;
        };
        try {
            org.bukkit.potion.Potion potion = new org.bukkit.potion.Potion(org.bukkit.potion.PotionType.valueOf(base), strong ? 2 : 1);
            if (longer) potion.setHasExtendedDuration(true);
            return potion.toItemStack(amount);
        } catch (IllegalArgumentException e) {
            return null;
        }
    });
    private final Sounds sounds = new XSounds();

    public LegacyPlatform(Plugin plugin, ServerVersion version) {
        this.plugin = plugin;
        this.version = version;
        this.nms = new Nms();
    }

    @Override public ServerVersion version() { return version; }

    private final me.playbosswar.tea.core.impl.map.SchematicStore schematics = new LegacySchematics();

    @Override public me.playbosswar.tea.core.impl.map.SchematicStore schematics() { return schematics; }

    @Override
    public boolean supports(Feature feature) {
        return switch (feature) {
            case OFF_HAND, ATTACK_COOLDOWN, EFFECT_CLOUDS -> version.atLeast(9);
            case TEXT_DISPLAYS, HEX_COLORS, NEGATIVE_WORLD_HEIGHT, PARTICLE_OPTIONS -> false;
        };
    }

    private final ItemTags tags = new ItemTags() {
        private String k(String owner, String key) {
            return owner.toLowerCase(Locale.ROOT) + ":" + key.toLowerCase(Locale.ROOT);
        }

        @Override public ItemStack set(ItemStack item, String owner, String key, String value) {
            return nms.setItemTag(item, k(owner, key), value);
        }

        @Override public String get(ItemStack item, String owner, String key) {
            return item == null ? null : nms.getItemTag(item, k(owner, key));
        }

        @Override public ItemStack remove(ItemStack item, String owner, String key) {
            return nms.removeItemTag(item, k(owner, key));
        }

        @Override public void set(Entity entity, String owner, String key, String value) {
            entity.setMetadata(k(owner, key), new FixedMetadataValue(plugin, value));
        }

        @Override public String get(Entity entity, String owner, String key) {
            if (entity == null) return null;
            for (MetadataValue v : entity.getMetadata(k(owner, key))) {
                if (v.getOwningPlugin() == plugin) return v.asString();
            }
            return null;
        }
    };

    @Override public ItemTags tags() { return tags; }

    private final Holograms holograms = new Holograms() {
        @Override public Hologram create(Location location, List<String> lines) {
            return new StandHologram(location, lines);
        }
    };

    @Override public Holograms holograms() { return holograms; }

    private static final Map<String, String> EFFECTS = new HashMap<>();

    static {
        String[][] pairs = {
            {"FLAME", "FLAME"}, {"SOUL_FIRE_FLAME", "FLAME"}, {"SMOKE", "PARTICLE_SMOKE"},
            {"LARGE_SMOKE", "LARGE_SMOKE"}, {"CAMPFIRE_COSY_SMOKE", "LARGE_SMOKE"}, {"CLOUD", "CLOUD"},
            {"POOF", "EXPLOSION"}, {"EXPLOSION", "EXPLOSION_LARGE"}, {"EXPLOSION_EMITTER", "EXPLOSION_HUGE"},
            {"HEART", "HEART"}, {"HAPPY_VILLAGER", "HAPPY_VILLAGER"}, {"ANGRY_VILLAGER", "VILLAGER_THUNDERCLOUD"},
            {"CRIT", "CRIT"}, {"ENCHANTED_HIT", "MAGIC_CRIT"}, {"WITCH", "WITCH_MAGIC"}, {"PORTAL", "PORTAL"},
            {"END_ROD", "FIREWORKS_SPARK"}, {"FIREWORK", "FIREWORKS_SPARK"}, {"LAVA", "LAVA_POP"},
            {"NOTE", "NOTE"}, {"SNOWFLAKE", "SNOW_SHOVEL"}, {"ITEM_SNOWBALL", "SNOWBALL_BREAK"},
            {"SPLASH", "SPLASH"}, {"DRIPPING_WATER", "WATERDRIP"}, {"DRIPPING_LAVA", "LAVADRIP"},
            {"ENCHANT", "FLYING_GLYPH"}, {"INSTANT_EFFECT", "INSTANT_SPELL"}, {"EFFECT", "SPELL"},
            {"ENTITY_EFFECT", "POTION_SWIRL"}, {"ITEM_SLIME", "SLIME"}, {"DRAGON_BREATH", "PORTAL"},
            {"SQUID_INK", "LARGE_SMOKE"}, {"TOTEM_OF_UNDYING", "HAPPY_VILLAGER"}, {"SWEEP_ATTACK", "CRIT"},
            {"ELECTRIC_SPARK", "MAGIC_CRIT"}, {"SOUL", "PARTICLE_SMOKE"}, {"SCRAPE", "MAGIC_CRIT"},
            {"FLASH", "FIREWORKS_SPARK"}, {"SONIC_BOOM", "EXPLOSION_LARGE"},
        };
        for (String[] p : pairs) EFFECTS.put(p[0], p[1]);
    }

    private static Effect effect(String modern) {
        String name = EFFECTS.getOrDefault(modern.toUpperCase(Locale.ROOT), modern.toUpperCase(Locale.ROOT));
        try {
            return Effect.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private final Particles particles = new Particles() {
        @Override
        public void spawn(String name, Location at, int count, double dx, double dy, double dz, double speed, Player viewer) {
            Effect e = effect(name);
            if (e == null) return;
            if (viewer != null) {
                viewer.spigot().playEffect(at, e, 0, 0, (float) dx, (float) dy, (float) dz, (float) speed, count, 64);
            } else {
                at.getWorld().spigot().playEffect(at, e, 0, 0, (float) dx, (float) dy, (float) dz, (float) speed, count, 64);
            }
        }

        @Override
        public void dust(Location at, Color color, float size, int count, double spread, Player viewer) {
            // 1.8 colours redstone dust through the offsets (count 0, speed 1).
            float r = Math.max(0.001f, color.getRed() / 255f);
            float g = color.getGreen() / 255f;
            float b = color.getBlue() / 255f;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            for (int i = 0; i < Math.max(1, count); i++) {
                Location p = at.clone().add(rnd.nextGaussian() * spread, rnd.nextGaussian() * spread, rnd.nextGaussian() * spread);
                if (viewer != null) viewer.spigot().playEffect(p, Effect.COLOURED_DUST, 0, 0, r, g, b, 1f, 0, 64);
                else p.getWorld().spigot().playEffect(p, Effect.COLOURED_DUST, 0, 0, r, g, b, 1f, 0, 64);
            }
        }
    };

    @Override public Particles particles() { return particles; }

    private final EntityControl entities = new EntityControl() {
        @Override public void setAi(LivingEntity entity, boolean ai) { nms.setEntityByte(entity, "NoAI", ai ? 0 : 1); }
        @Override public void setInvulnerable(Entity entity, boolean v) { nms.setEntityByte(entity, "Invulnerable", v ? 1 : 0); }
        @Override public void setSilent(Entity entity, boolean v) { nms.setEntityByte(entity, "Silent", v ? 1 : 0); }
        @Override public void setGravity(Entity entity, boolean v) {
            if (entity instanceof ArmorStand stand) stand.setGravity(v);
        }
        @Override public void setCollidable(LivingEntity entity, boolean v) { /* 1.9+ */ }
        @Override public void setGlowing(Entity entity, boolean v) { /* 1.9+ */ }
        @Override public double maxHealth(LivingEntity entity) { return entity.getMaxHealth(); }

        @Override
        public double attribute(LivingEntity entity, String modernName) {
            return nms.attribute(entity, modernName);
        }

        @Override
        public void setAttributeBase(LivingEntity entity, String modernName, double value) {
            if (modernName.equalsIgnoreCase("MAX_HEALTH")) {
                entity.setMaxHealth(value); // keeps Bukkit's health scaling in step
                return;
            }
            nms.setAttributeBase(entity, modernName, value);
        }

        @Override
        public Player playerBehind(Entity damager) {
            if (damager instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player p) return p;
            if (damager instanceof org.bukkit.entity.TNTPrimed tnt && tnt.getSource() instanceof Player p) return p;
            return null;
        }
    };

    @Override public EntityControl entities() { return entities; }

    @Override public Items items() { return items; }

    @Override public Sounds sounds() { return sounds; }

    private final Worlds worlds = new Worlds() {
        @Override public int minHeight(World world) { return 0; }
        @Override public int maxHeight(World world) { return world.getMaxHeight(); }
        @Override public void explode(Location at, float power, boolean fire, boolean breakBlocks, Entity source) {
            nms.explode(at, power, fire, breakBlocks, source);
        }
        @Override public boolean isChunkGenerated(World world, int x, int z) { return world.loadChunk(x, z, false); }
        @Override public boolean isAir(org.bukkit.ChunkSnapshot s, int x, int y, int z) { return s.getBlockTypeId(x, y, z) == 0; }
        @Override public void setGameRule(World world, String rule, String value) { world.setGameRuleValue(rule, value); }

        @Override
        public WorldCreator voidWorld(String name) {
            // Superflat with no layers: an empty world.
            return new WorldCreator(name).type(WorldType.FLAT).generatorSettings("2;0;1;").generateStructures(false);
        }
    };

    @Override public Worlds worlds() { return worlds; }

    private final Players players = new Players() {
        @Override public String locale(Player p) { return p.spigot().getLocale(); }
        @Override public ItemStack mainHand(Player p) { return p.getItemInHand(); }
        @Override public void setMainHand(Player p, ItemStack item) { p.setItemInHand(item); }
        @Override public ItemStack[] storage(Player p) { return p.getInventory().getContents(); }
        @Override public void setStorage(Player p, ItemStack[] items) { p.getInventory().setContents(items); }
        @Override public boolean isMainHand(PlayerInteractEvent e) { return true; }
        @Override public boolean isMainHand(org.bukkit.event.player.PlayerInteractEntityEvent e) { return true; }

        @Override
        public void setVisible(Player viewer, Player target, boolean visible) {
            if (visible) viewer.showPlayer(target);
            else viewer.hidePlayer(target);
        }

        @Override
        public Block targetBlock(Player p, int range) {
            Block b = p.getTargetBlock((java.util.Set<Material>) null, range);
            return b == null || b.getType() == Material.AIR ? null : b;
        }
    };

    @Override public Players players() { return players; }

    private final me.playbosswar.tea.core.api.platform.Scoreboards scoreboards = new me.playbosswar.tea.core.api.platform.Scoreboards() {
        @Override
        public org.bukkit.scoreboard.Objective newObjective(org.bukkit.scoreboard.Scoreboard board, String name, String title) {
            org.bukkit.scoreboard.Objective o = board.registerNewObjective(name, "dummy");
            o.setDisplayName(title.length() > 32 ? title.substring(0, 32) : title);
            return o;
        }

        @Override
        public void setTeamColor(org.bukkit.scoreboard.Team team, org.bukkit.ChatColor color) {
            String prefix = color.toString();
            if (!prefix.equals(team.getPrefix())) team.setPrefix(prefix);
        }

        @Override public int maxAffixLength() { return 16; }
        @Override public int maxTitleLength() { return 32; }
    };

    @Override public me.playbosswar.tea.core.api.platform.Scoreboards scoreboards() { return scoreboards; }

    /** 1.8 doesn't say what a projectile hit: look along its path. */
    private final me.playbosswar.tea.core.api.platform.Projectiles projectiles = new me.playbosswar.tea.core.api.platform.Projectiles() {
        @Override
        public Entity hitEntity(org.bukkit.event.entity.ProjectileHitEvent e) {
            org.bukkit.entity.Projectile p = e.getEntity();
            Entity best = null;
            double bestD = 2.5 * 2.5;
            for (Entity other : p.getNearbyEntities(1.5, 1.5, 1.5)) {
                if (!(other instanceof LivingEntity) || other.equals(p.getShooter())) continue;
                double d = other.getLocation().add(0, 1, 0).distanceSquared(p.getLocation());
                if (d < bestD) { bestD = d; best = other; }
            }
            return best;
        }

        @Override
        public Block hitBlock(org.bukkit.event.entity.ProjectileHitEvent e) {
            org.bukkit.util.Vector dir = e.getEntity().getVelocity();
            if (dir.lengthSquared() < 1e-6) return null;
            org.bukkit.util.BlockIterator it = new org.bukkit.util.BlockIterator(e.getEntity().getWorld(),
                    e.getEntity().getLocation().toVector(), dir.normalize(), 0, 3);
            while (it.hasNext()) {
                Block b = it.next();
                if (b.getType().isSolid()) return b;
            }
            return null;
        }

        @Override
        public org.bukkit.block.BlockFace hitFace(org.bukkit.event.entity.ProjectileHitEvent e) {
            Block b = hitBlock(e);
            if (b == null) return null;
            org.bukkit.util.Vector d = e.getEntity().getVelocity();
            double ax = Math.abs(d.getX()), ay = Math.abs(d.getY()), az = Math.abs(d.getZ());
            if (ay >= ax && ay >= az) return d.getY() > 0 ? org.bukkit.block.BlockFace.DOWN : org.bukkit.block.BlockFace.UP;
            if (ax >= az) return d.getX() > 0 ? org.bukkit.block.BlockFace.WEST : org.bukkit.block.BlockFace.EAST;
            return d.getZ() > 0 ? org.bukkit.block.BlockFace.NORTH : org.bukkit.block.BlockFace.SOUTH;
        }
    };

    @Override public me.playbosswar.tea.core.api.platform.Projectiles projectiles() { return projectiles; }

    private final me.playbosswar.tea.core.api.platform.Effects effects = new me.playbosswar.tea.core.impl.platform.XEffects();

    @Override public me.playbosswar.tea.core.api.platform.Effects effects() { return effects; }

    @Override public boolean isPassable(Block block) { return !block.getType().isSolid(); }

    private static final class StandHologram implements Holograms.Hologram {
        private static final double GAP = 0.27;
        private final List<ArmorStand> stands = new ArrayList<>();
        private Location base;

        StandHologram(Location location, List<String> lines) {
            base = location.clone();
            setLines(lines);
        }

        @Override
        public void setLines(List<String> lines) {
            while (stands.size() > lines.size()) stands.remove(stands.size() - 1).remove();
            for (int i = 0; i < lines.size(); i++) {
                if (i >= stands.size()) {
                    ArmorStand s = base.getWorld().spawn(base.clone().add(0, (lines.size() - 1 - i) * GAP, 0), ArmorStand.class);
                    s.setVisible(false);
                    s.setGravity(false);
                    s.setMarker(true);
                    s.setSmall(true);
                    s.setCustomNameVisible(true);
                    stands.add(s);
                }
                stands.get(i).setCustomName(lines.get(i));
            }
        }

        @Override
        public void teleport(Location to) {
            base = to.clone();
            for (int i = 0; i < stands.size(); i++) {
                stands.get(i).teleport(base.clone().add(0, (stands.size() - 1 - i) * GAP, 0));
            }
        }

        @Override public Location location() { return base.clone(); }
        @Override public boolean isValid() { return !stands.isEmpty() && stands.get(0).isValid(); }
        @Override public void remove() { stands.forEach(Entity::remove); stands.clear(); }
    }
}
