package net.chickensmp.spawn;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Clean spawn for the Chicken SMP: /spawn with a short countdown (stops when you move or get hit), a cooldown and a
 * combat block, /setspawn, and automatic spawn teleports on first join, respawn and when falling into the void.
 */
public final class SpawnPlugin extends JavaPlugin implements Listener {
    private final MiniMessage mm = MiniMessage.miniMessage();

    /** A running /spawn countdown. */
    private record Pending(BukkitTask task, Location from) {}

    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        pending.values().forEach(p -> p.task().cancel());
        pending.clear();
    }

    // ------------------------------------------------------------------ config and messages

    private FileConfiguration cfg() {
        return getConfig();
    }

    private Component msg(String key, TagResolver... resolvers) {
        String raw = cfg().getString("messages." + key, key);
        return mm.deserialize(cfg().getString("messages.prefix", "") + raw, resolvers);
    }

    private Component plain(String key, TagResolver... resolvers) {
        return mm.deserialize(cfg().getString("messages." + key, key), resolvers);
    }

    /** The saved spawn, or null when none is set (or its world is not loaded). */
    private Location spawn() {
        String worldName = cfg().getString("spawn.world", "");
        if (worldName == null || worldName.isEmpty()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, cfg().getDouble("spawn.x"), cfg().getDouble("spawn.y"), cfg().getDouble("spawn.z"),
                (float) cfg().getDouble("spawn.yaw"), (float) cfg().getDouble("spawn.pitch"));
    }

    private void saveSpawn(Location l) {
        cfg().set("spawn.world", l.getWorld().getName());
        // centre on the block, rounded view so it looks tidy
        cfg().set("spawn.x", l.getBlockX() + 0.5);
        cfg().set("spawn.y", Math.round(l.getY() * 100) / 100.0);
        cfg().set("spawn.z", l.getBlockZ() + 0.5);
        cfg().set("spawn.yaw", (double) Math.round(l.getYaw() / 45f) * 45f);
        cfg().set("spawn.pitch", 0.0);
        saveConfig();
    }

    // ------------------------------------------------------------------ teleporting

    /** Teleports at once, with sound, particles and title. */
    private void teleportNow(Player player, Location target, String messageKey) {
        player.setFallDistance(0f);
        player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            Bukkit.getScheduler().runTask(this, () -> arrived(player, target, messageKey));
        });
    }

    private void arrived(Player player, Location target, String messageKey) {
        if (!player.isOnline()) return;
        if (cfg().getBoolean("effects.particles", true)) {
            target.getWorld().spawnParticle(Particle.REVERSE_PORTAL, target.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.05);
            target.getWorld().spawnParticle(Particle.END_ROD, target.clone().add(0, 0.2, 0), 12, 0.5, 0.1, 0.5, 0.01);
        }
        String sound = cfg().getString("effects.sound", "");
        if (sound != null && !sound.isEmpty()) {
            player.playSound(target, sound, (float) cfg().getDouble("effects.volume", 0.8), (float) cfg().getDouble("effects.pitch", 1.2));
        }
        if (cfg().getBoolean("effects.title", true)) {
            player.showTitle(Title.title(plain("title"), plain("subtitle"),
                    Title.Times.times(java.time.Duration.ofMillis(150), java.time.Duration.ofMillis(1100), java.time.Duration.ofMillis(400))));
        }
        if (messageKey != null) player.sendMessage(msg(messageKey));
    }

    /** /spawn for yourself: checks, then a countdown (or at once). */
    private void requestSpawn(Player player) {
        Location target = spawn();
        if (target == null) {
            player.sendMessage(msg("no-spawn"));
            return;
        }
        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(msg("already-waiting"));
            return;
        }
        boolean instant = player.hasPermission("chickensmp.spawn.instant");
        long now = System.currentTimeMillis();
        if (!instant) {
            long combat = combatUntil.getOrDefault(player.getUniqueId(), 0L) - now;
            if (combat > 0) {
                player.sendMessage(msg("in-combat", Placeholder.unparsed("seconds", String.valueOf((combat + 999) / 1000))));
                return;
            }
            long cool = cooldownUntil.getOrDefault(player.getUniqueId(), 0L) - now;
            if (cool > 0) {
                player.sendMessage(msg("cooldown", Placeholder.unparsed("seconds", String.valueOf((cool + 999) / 1000))));
                return;
            }
        }
        int delay = instant ? 0 : Math.max(0, cfg().getInt("teleport.delay", 3));
        if (delay == 0) {
            finish(player, target);
            return;
        }
        int totalTicks = delay * 20;
        int[] ticks = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!player.isOnline()) {
                cancel(player.getUniqueId());
                return;
            }
            if (ticks[0] >= totalTicks) {
                cancel(player.getUniqueId());
                finish(player, spawn() != null ? spawn() : target);
                return;
            }
            if (ticks[0] % 20 == 0) {
                int left = (totalTicks - ticks[0] + 19) / 20;
                player.sendActionBar(plain("countdown-actionbar", Placeholder.unparsed("seconds", String.valueOf(left))));
                player.playSound(player.getLocation(), "block.note_block.hat", 0.5f, 1.4f + 0.15f * (delay - left));
            }
            if (cfg().getBoolean("effects.particles", true) && ticks[0] % 2 == 0) {
                // a ring that rises around the player while waiting
                double progress = ticks[0] / (double) totalTicks;
                double angle = ticks[0] * 0.45;
                Location l = player.getLocation().add(Math.cos(angle) * 0.7, progress * 1.9, Math.sin(angle) * 0.7);
                player.getWorld().spawnParticle(Particle.END_ROD, l, 1, 0, 0, 0, 0);
            }
            ticks[0] += 1;
        }, 0L, 1L);
        pending.put(player.getUniqueId(), new Pending(task, player.getLocation().toBlockLocation()));
    }

    private void finish(Player player, Location target) {
        player.sendActionBar(Component.empty());
        teleportNow(player, target, "teleported");
        if (!player.hasPermission("chickensmp.spawn.instant")) {
            cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + cfg().getInt("teleport.cooldown", 5) * 1000L);
        }
    }

    private void cancel(UUID id) {
        Pending p = pending.remove(id);
        if (p != null) p.task().cancel();
    }

    private void abort(Player player, String messageKey) {
        if (!pending.containsKey(player.getUniqueId())) return;
        cancel(player.getUniqueId());
        player.sendActionBar(Component.empty());
        player.sendMessage(msg(messageKey));
        player.playSound(player.getLocation(), "block.note_block.bass", 0.6f, 0.7f);
    }

    // ------------------------------------------------------------------ commands

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "spawn" -> {
                if (args.length >= 1) {
                    if (!sender.hasPermission("chickensmp.spawn.others")) {
                        sender.sendMessage(msg("no-permission"));
                        return true;
                    }
                    Player target = Bukkit.getPlayerExact(args[0]);
                    if (target == null) {
                        sender.sendMessage(msg("player-not-found", Placeholder.unparsed("player", args[0])));
                        return true;
                    }
                    Location spawn = spawn();
                    if (spawn == null) {
                        sender.sendMessage(msg("no-spawn"));
                        return true;
                    }
                    cancel(target.getUniqueId());
                    teleportNow(target, spawn, "teleported");
                    sender.sendMessage(msg("sent-other", Placeholder.unparsed("player", target.getName())));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg("only-players"));
                    return true;
                }
                if (!player.hasPermission("chickensmp.spawn.use")) {
                    player.sendMessage(msg("no-permission"));
                    return true;
                }
                requestSpawn(player);
                return true;
            }
            case "setspawn" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(msg("only-players"));
                    return true;
                }
                Location l = player.getLocation();
                saveSpawn(l);
                l.getWorld().setSpawnLocation(l);
                player.sendMessage(msg("spawn-set",
                        Placeholder.unparsed("x", String.valueOf(l.getBlockX())),
                        Placeholder.unparsed("y", String.valueOf(l.getBlockY())),
                        Placeholder.unparsed("z", String.valueOf(l.getBlockZ())),
                        Placeholder.unparsed("world", l.getWorld().getName())));
                player.playSound(l, "entity.experience_orb.pickup", 0.7f, 1.2f);
                return true;
            }
            case "spawnreload" -> {
                reloadConfig();
                sender.sendMessage(msg("reloaded"));
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("spawn") && args.length == 1 && sender.hasPermission("chickensmp.spawn.others")) {
            String start = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(start)).sorted().collect(Collectors.toList());
        }
        return List.of();
    }

    // ------------------------------------------------------------------ events

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // void rescue in the spawn world
        if (cfg().getBoolean("void.rescue", true)) {
            Location spawn = spawn();
            World world = event.getTo().getWorld();
            if (spawn != null && world.equals(spawn.getWorld())
                    && event.getTo().getY() < world.getMinHeight() - cfg().getInt("void.below-min-height", 10)) {
                cancel(player.getUniqueId());
                teleportNow(player, spawn, "void-rescue");
                return;
            }
        }
        Pending p = pending.get(player.getUniqueId());
        if (p == null || !cfg().getBoolean("teleport.cancel-on-move", true)) return;
        Location to = event.getTo();
        if (to.getBlockX() != p.from().getBlockX() || to.getBlockY() != p.from().getBlockY() || to.getBlockZ() != p.from().getBlockZ()) {
            abort(player, "cancelled-move");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID && cfg().getBoolean("void.rescue", true)) {
            Location spawn = spawn();
            if (spawn != null && player.getWorld().equals(spawn.getWorld())) {
                event.setCancelled(true);
                cancel(player.getUniqueId());
                teleportNow(player, spawn, "void-rescue");
                return;
            }
        }
        if (cfg().getBoolean("teleport.cancel-on-damage", true)) abort(player, "cancelled-damage");
    }

    /** Hitting or being hit by another player blocks /spawn for a while. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPvp(EntityDamageByEntityEvent event) {
        Player attacker = event.getDamager() instanceof Player p ? p
                : event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2 ? p2 : null;
        if (attacker == null || !(event.getEntity() instanceof Player victim) || attacker.equals(victim)) return;
        long until = System.currentTimeMillis() + cfg().getInt("teleport.combat-block", 10) * 1000L;
        combatUntil.put(attacker.getUniqueId(), until);
        combatUntil.put(victim.getUniqueId(), until);
        abort(attacker, "cancelled-damage");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        boolean first = !player.hasPlayedBefore();
        if ((first && cfg().getBoolean("join.first-join", true)) || cfg().getBoolean("join.every-join", false)) {
            Location spawn = spawn();
            if (spawn != null) Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) teleportNow(player, spawn, null);
            }, 2L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!cfg().getBoolean("respawn.to-spawn", true) || event.isBedSpawn() || event.isAnchorSpawn()) return;
        Location spawn = spawn();
        if (spawn != null) event.setRespawnLocation(spawn);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        cancel(id);
        cooldownUntil.remove(id);
    }
}
