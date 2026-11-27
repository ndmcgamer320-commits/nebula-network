package net.nebula.core;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent network stats, simple PvP ELO, rate limits and staff review flags.
 * Review flags are moderation signals, not proof of cheating.
 */
public final class NebulaNetworkFeatures implements Listener {
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA "
            + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration data;
    private final Map<UUID, Deque<Long>> chatTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> commandTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    public NebulaNetworkFeatures(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "network-data.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private String path(UUID id) { return "players." + id; }
    private int number(UUID id, String key, int fallback) {
        return data.getInt(path(id) + "." + key, fallback);
    }
    private void save() {
        try { data.save(file); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save network stats: " + ex.getMessage()); }
    }
    private void touch(Player player) {
        data.set(path(player.getUniqueId()) + ".name", player.getName());
    }
    public int getElo(UUID id) { return number(id, "elo", 1000); }

    private boolean limited(Player player, Map<UUID, Deque<Long>> buckets, int max, long windowMs, String type) {
        long now = System.currentTimeMillis();
        Deque<Long> q = buckets.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > windowMs) q.removeFirst();
            q.addLast(now);
            if (q.size() <= max) return false;
        }
        flag(player, type);
        mutedUntil.put(player.getUniqueId(), now + 10000L);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) player.sendMessage(PREFIX + ChatColor.RED + "Slow down. Chat/command cooldown: 10 seconds.");
        });
        return true;
    }

    private void flag(Player player, String reason) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            UUID id = player.getUniqueId();
            touch(player);
            String base = path(id);
            data.set(base + ".flags", number(id, "flags", 0) + 1);
            data.set(base + ".lastFlag", reason + " @ " + new Date());
            save();
            for (Player staff : Bukkit.getOnlinePlayers()) {
                if (staff.hasPermission("nebulacore.admin")) {
                    staff.sendMessage(PREFIX + ChatColor.RED + "Activity flag: " + ChatColor.WHITE
                            + player.getName() + ChatColor.GRAY + " (" + reason + ")");
                }
            }
            plugin.getLogger().warning("Activity flag for " + player.getName() + ": " + reason);
        });
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        long until = mutedUntil.getOrDefault(player.getUniqueId(), 0L);
        if (System.currentTimeMillis() < until) {
            event.setCancelled(true);
            return;
        }
        if (limited(player, chatTimes, 5, 5000L, "chat burst")) {
            event.setCancelled(true);
            return;
        }
        String message = event.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("discord.gg/") || message.contains("http://") || message.contains("https://")) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage(PREFIX + ChatColor.RED + "Links are blocked in public chat. Ask staff if you need help.");
                flag(player, "blocked link");
            });
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (System.currentTimeMillis() < mutedUntil.getOrDefault(player.getUniqueId(), 0L)) {
            event.setCancelled(true);
            player.sendMessage(PREFIX + ChatColor.RED + "Command cooldown active.");
            return;
        }
        String command = event.getMessage().toLowerCase(Locale.ROOT);
        if (command.startsWith("/msg ") || command.startsWith("/tell ") || command.startsWith("/w ")) return;
        if (limited(player, commandTimes, 10, 5000L, "command burst")) event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerKill(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        UUID victimId = victim.getUniqueId();
        touch(victim);
        data.set(path(victimId) + ".deaths", number(victimId, "deaths", 0) + 1);
        if (killer != null && !killer.equals(victim)) {
            UUID killerId = killer.getUniqueId();
            touch(killer);
            data.set(path(killerId) + ".kills", number(killerId, "kills", 0) + 1);
            data.set(path(killerId) + ".elo", getElo(killerId) + 15);
            data.set(path(victimId) + ".elo", Math.max(0, getElo(victimId) - 10));
        }
        save();
    }

    private boolean isAdmin(CommandSender sender) {
        return sender.hasPermission("nebulacore.admin");
    }

    public boolean handleCommand(CommandSender sender, String command, String[] args) {
        if (command.equalsIgnoreCase("stats")) {
            if (args.length > 0 && !isAdmin(sender) && (!(sender instanceof Player p) || !p.getName().equalsIgnoreCase(args[0]))) {
                sender.sendMessage(PREFIX + ChatColor.RED + "You can only view your own stats.");
                return true;
            }
            Player online = args.length == 0 ? (sender instanceof Player p ? p : null) : Bukkit.getPlayerExact(args[0]);
            if (online == null && args.length > 0) {
                sender.sendMessage(PREFIX + ChatColor.RED + "That player must be online for this stats view.");
                return true;
            }
            if (online == null) { sender.sendMessage(PREFIX + "Usage: /stats <player>"); return true; }
            UUID id = online.getUniqueId();
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ " + online.getName() + " STATS ✦");
            sender.sendMessage(ChatColor.GRAY + "ELO: " + ChatColor.AQUA + getElo(id));
            sender.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.GREEN + number(id, "kills", 0)
                    + ChatColor.GRAY + " | Deaths: " + ChatColor.RED + number(id, "deaths", 0));
            sender.sendMessage(ChatColor.GRAY + "Activity flags: " + ChatColor.YELLOW + number(id, "flags", 0));
            return true;
        }
        if (command.equalsIgnoreCase("leaderboard")) {
            String type = args.length == 0 ? "elo" : args[0].toLowerCase(Locale.ROOT);
            if (!Set.of("elo", "kills").contains(type)) {
                sender.sendMessage(PREFIX + "Usage: /leaderboard [elo|kills]");
                return true;
            }
            List<String> ids = new ArrayList<>(data.getConfigurationSection("players") == null
                    ? Set.of() : data.getConfigurationSection("players").getKeys(false));
            ids.sort((a,b) -> Integer.compare(data.getInt("players." + b + "." + type, type.equals("elo") ? 1000 : 0),
                    data.getInt("players." + a + "." + type, type.equals("elo") ? 1000 : 0)));
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "✦ TOP " + type.toUpperCase(Locale.ROOT) + " ✦");
            int place = 1;
            for (String raw : ids) {
                if (place > 10) break;
                String name = data.getString("players." + raw + ".name", "Unknown");
                int value = data.getInt("players." + raw + "." + type, type.equals("elo") ? 1000 : 0);
                sender.sendMessage(ChatColor.GOLD + "#" + place++ + " " + ChatColor.WHITE + name
                        + ChatColor.GRAY + " — " + ChatColor.AQUA + value);
            }
            if (ids.isEmpty()) sender.sendMessage(ChatColor.GRAY + "No stats recorded yet.");
            return true;
        }
        if (command.equalsIgnoreCase("sus")) {
            if (!isAdmin(sender)) { sender.sendMessage(PREFIX + ChatColor.RED + "Admin permission required."); return true; }
            if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
                sender.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "✦ PLAYER ACTIVITY REVIEW ✦");
                int shown = 0;
                if (data.getConfigurationSection("players") != null) {
                    for (String raw : data.getConfigurationSection("players").getKeys(false)) {
                        int flags = data.getInt("players." + raw + ".flags", 0);
                        if (flags < 1) continue;
                        sender.sendMessage(ChatColor.YELLOW + data.getString("players." + raw + ".name", raw)
                                + ChatColor.GRAY + " — flags: " + flags + ", last: "
                                + data.getString("players." + raw + ".lastFlag", "unknown"));
                        shown++;
                    }
                }
                if (shown == 0) sender.sendMessage(ChatColor.GRAY + "No activity flags recorded.");
                sender.sendMessage(ChatColor.DARK_GRAY + "Flags are review signals, not proof of cheating.");
                return true;
            }
            if (args[0].equalsIgnoreCase("check") && args.length == 2) {
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(PREFIX + "Player must be online."); return true; }
                UUID id = target.getUniqueId();
                sender.sendMessage(PREFIX + target.getName() + " | ELO " + getElo(id)
                        + " | kills " + number(id,"kills",0) + " | deaths " + number(id,"deaths",0)
                        + " | flags " + number(id,"flags",0) + " | ping " + target.getPing()
                        + "ms | world " + target.getWorld().getName());
                return true;
            }
            if (args[0].equalsIgnoreCase("clear") && args.length == 2) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (!target.hasPlayedBefore() && !target.isOnline()) { sender.sendMessage(PREFIX + "Player record not found."); return true; }
                data.set(path(target.getUniqueId()) + ".flags", 0);
                data.set(path(target.getUniqueId()) + ".lastFlag", "cleared by " + sender.getName());
                save();
                sender.sendMessage(PREFIX + "Activity flags cleared for " + args[1] + ".");
                return true;
            }
            sender.sendMessage(PREFIX + "Usage: /sus [list|check <player>|clear <player>]");
            return true;
        }
        return false;
    }
}
