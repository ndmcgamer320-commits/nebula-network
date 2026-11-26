package net.nebula.core;

import java.util.*;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.*;
import org.bukkit.Statistic;

/** Rank, TAB, sidebar, spawn effects and skinned lobby NPC presentation. */
public final class NebulaHud implements Listener {
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private static final String LEADERBOARD_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "TOP PLAYTIME";
    private final NebulaCore plugin;
    private final Location lobby;
    private final NamespacedKey leaderboardKey;
    private final Map<Integer, String> npcActions = new HashMap<>();
    private final Map<UUID, Scoreboard> scoreboards = new HashMap<>();
    private final Map<UUID, List<String>> previousLines = new HashMap<>();

    public NebulaHud(NebulaCore plugin, Location lobby) {
        this.plugin = plugin;
        this.lobby = lobby.clone();
        this.leaderboardKey = new NamespacedKey(plugin, "playtime_leaderboard");
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, this::spawnLobbyNpcs, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (isLobby(player.getLocation())) applySpawnEffects(player);
                updateTab(player);
                updateSidebar(player);
            }
        }, 20L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboardHologram, 100L, 1200L);
    }

    private boolean isLobby(Location location) {
        return location.getWorld() != null && location.getWorld().equals(lobby.getWorld())
                && location.distanceSquared(lobby) <= 10000;
    }

    private void applySpawnEffects(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 240, 1, true, false, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 240, 0, true, false, true));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (isLobby(player.getLocation())) applySpawnEffects(player);
            updateTab(player);
            updateSidebar(player);
        }, 2L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isLobby(player.getLocation())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && isLobby(player.getLocation())) applySpawnEffects(player);
            }, 2L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation().getWorld() != null
                && event.getRespawnLocation().getWorld().equals(lobby.getWorld())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = event.getPlayer();
                if (player.isOnline() && isLobby(player.getLocation())) applySpawnEffects(player);
            }, 2L);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        scoreboards.remove(event.getPlayer().getUniqueId());
        previousLines.remove(event.getPlayer().getUniqueId());
    }

    public String getRank(Player player) {
        return normalizeRank(plugin.getConfig().getString("ranks." + player.getUniqueId(), "MEMBER"));
    }

    public boolean isAdminRank(CommandSender sender) {
        if (sender.hasPermission("nebulacore.admin")) return true;
        if (sender instanceof Player player) {
            String rank = getRank(player);
            return rank.equals("OWNER") || rank.equals("ADMIN");
        }
        return true; // server console
    }

    public void handleRankCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                sender.sendMessage(PREFIX + "Your rank: " + rankColor(getRank(player)) + getRank(player));
            } else {
                sender.sendMessage(PREFIX + "Usage: /rank <online-player> <OWNER|ADMIN|MVP|VIP|MEMBER>");
            }
            return;
        }
        if (!isAdminRank(sender)) {
            sender.sendMessage(PREFIX + "You need the admin permission to change ranks.");
            return;
        }
        if (args.length != 2) {
            sender.sendMessage(PREFIX + "Usage: /rank <online-player> <OWNER|ADMIN|MVP|VIP|MEMBER>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX + "That player must be online to change their rank.");
            return;
        }
        String rank = normalizeRank(args[1]);
        if (!Set.of("OWNER", "ADMIN", "MVP", "VIP", "MEMBER").contains(rank)) {
            sender.sendMessage(PREFIX + "Ranks: OWNER, ADMIN, MVP, VIP, MEMBER.");
            return;
        }
        plugin.getConfig().set("ranks." + target.getUniqueId(), rank);
        plugin.saveConfig();
        updateTab(target);
        updateSidebar(target);
        target.sendMessage(PREFIX + "Your rank is now " + rankColor(rank) + rank + ChatColor.GRAY + ".");
        sender.sendMessage(PREFIX + "Set " + target.getName() + "'s rank to " + rankColor(rank) + rank + ChatColor.GRAY + ".");
    }

    private String normalizeRank(String rank) {
        if (rank == null) return "MEMBER";
        return rank.trim().toUpperCase(Locale.ROOT);
    }

    private String rankColor(String rank) {
        return switch (rank) {
            case "OWNER" -> ChatColor.GOLD.toString();
            case "ADMIN" -> ChatColor.RED.toString();
            case "MVP" -> ChatColor.LIGHT_PURPLE.toString();
            case "VIP" -> ChatColor.AQUA.toString();
            default -> ChatColor.GRAY.toString();
        };
    }

    private String rankPrefix(String rank) {
        return switch (rank) {
            case "OWNER" -> ChatColor.GOLD + "[OWNER] ";
            case "ADMIN" -> ChatColor.RED + "[ADMIN] ";
            case "MVP" -> ChatColor.LIGHT_PURPLE + "[MVP] ";
            case "VIP" -> ChatColor.AQUA + "[VIP] ";
            default -> ChatColor.GRAY + "[MEMBER] ";
        };
    }

    private void updateTab(Player player) {
        String rank = getRank(player);
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        long max = runtime.maxMemory();
        String memory = gb(used) + "/" + gb(max) + " GB";
        String header = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ NEBULA NETWORK ✦\n"
                + ChatColor.LIGHT_PURPLE + "Survival  •  Games  •  Community";
        String footer = ChatColor.GRAY + "Online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size()
                + ChatColor.DARK_GRAY + "  |  " + ChatColor.AQUA + "RAM: " + memory
                + ChatColor.DARK_GRAY + "\n" + ChatColor.LIGHT_PURPLE + "Have a great time!";
        player.setPlayerListHeaderFooter(header, footer);
        player.setPlayerListName(rankPrefix(rank) + ChatColor.WHITE + player.getName());
    }

    private void updateSidebar(Player player) {
        UUID id = player.getUniqueId();
        Scoreboard board = scoreboards.computeIfAbsent(id, ignored -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective objective = board.getObjective("nebula");
        if (objective == null) {
            objective = board.registerNewObjective("nebula", "dummy", ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA");
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        List<String> old = previousLines.getOrDefault(id, List.of());
        for (String line : old) board.resetScores(line);

        Runtime runtime = Runtime.getRuntime();
        String worldName = player.getWorld().equals(lobby.getWorld()) ? "Lobby" : player.getWorld().getName();
        List<String> lines = List.of(
                ChatColor.DARK_GRAY + "----------------",
                ChatColor.GRAY + "Rank: " + rankColor(getRank(player)) + getRank(player),
                ChatColor.GRAY + "Online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size() + "/20",
                ChatColor.GRAY + "RAM: " + ChatColor.AQUA + gb(runtime.totalMemory() - runtime.freeMemory()) + "/" + gb(runtime.maxMemory()) + " GB",
                ChatColor.GRAY + "World: " + ChatColor.YELLOW + worldName,
                ChatColor.GRAY + "Playtime: " + ChatColor.GREEN + playtime(player.getStatistic(Statistic.PLAY_ONE_MINUTE)),
                ChatColor.DARK_PURPLE + "nebulanetwork"
        );
        int score = lines.size();
        for (String line : lines) objective.getScore(line).setScore(score--);
        previousLines.put(id, lines);
        if (player.getScoreboard() != board) player.setScoreboard(board);
    }

    private String gb(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / 1073741824.0);
    }

    private String playtime(int ticks) {
        int minutes = Math.max(0, ticks) / 1200;
        int hours = minutes / 60;
        return hours + "h " + (minutes % 60) + "m";
    }

    private int playtimeTicks(OfflinePlayer player) {
        try {
            return player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        } catch (IllegalArgumentException ignored) {
            return 0;
        }
    }

    private List<OfflinePlayer> topPlayers() {
        OfflinePlayer[] all = Bukkit.getOfflinePlayers();
        return Arrays.stream(all)
                .filter(player -> player.getName() != null)
                .sorted(Comparator.comparingInt(this::playtimeTicks).reversed())
                .limit(5)
                .toList();
    }

    private void openLeaderboard(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, LEADERBOARD_TITLE);
        List<OfflinePlayer> top = topPlayers();
        if (top.isEmpty()) {
            inventory.setItem(13, named(Material.BOOK, ChatColor.YELLOW + "No playtime records yet", "Play on the network to appear here."));
        } else {
            int[] slots = {10, 12, 14, 16, 22};
            for (int i = 0; i < top.size(); i++) {
                OfflinePlayer entry = top.get(i);
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(entry);
                meta.setDisplayName(ChatColor.GOLD + "#" + (i + 1) + " " + entry.getName());
                meta.setLore(List.of(ChatColor.GRAY + "Playtime: " + ChatColor.GREEN + playtime(playtimeTicks(entry))));
                head.setItemMeta(meta);
                inventory.setItem(slots[i], head);
            }
        }
        player.openInventory(inventory);
    }

    private ItemStack named(Material material, String name, String lore) {
        ItemStack stack = new ItemStack(material);
        var meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(ChatColor.GRAY + lore));
        stack.setItemMeta(meta);
        return stack;
    }

    private void refreshLeaderboardHologram() {
        World world = lobby.getWorld();
        if (world == null) return;
        for (Entity entity : world.getNearbyEntities(lobby, 40, 20, 40)) {
            if (entity.getPersistentDataContainer().has(leaderboardKey, PersistentDataType.BYTE)) entity.remove();
        }
        StringBuilder text = new StringBuilder(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "NEBULA LEADERBOARD\n")
                .append(ChatColor.GRAY).append("TOP PLAYTIME\n");
        List<OfflinePlayer> top = topPlayers();
        if (top.isEmpty()) {
            text.append(ChatColor.WHITE).append("Be the first to play!");
        } else {
            for (int i = 0; i < top.size(); i++) {
                OfflinePlayer entry = top.get(i);
                text.append(ChatColor.GOLD).append(i + 1).append(". ")
                        .append(ChatColor.WHITE).append(entry.getName())
                        .append(ChatColor.DARK_GRAY).append("  ")
                        .append(ChatColor.GREEN).append(playtime(playtimeTicks(entry)));
                if (i + 1 < top.size()) text.append('\n');
            }
        }
        TextDisplay display = world.spawn(lobby.clone().add(0, 5.0, -18.0), TextDisplay.class);
        display.setText(text.toString());
        display.setBillboard(Display.Billboard.CENTER);
        display.setSeeThrough(true);
        display.setShadowed(true);
        display.setBackgroundColor(Color.fromRGB(28, 12, 44));
        display.setPersistent(true);
        display.getPersistentDataContainer().set(leaderboardKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void spawnLobbyNpcs() {
        if (Bukkit.getPluginManager().getPlugin("Citizens") == null
                || !Bukkit.getPluginManager().getPlugin("Citizens").isEnabled()) {
            plugin.getLogger().warning("Citizens is missing; skinned lobby NPCs were not created.");
            return;
        }
        var registry = CitizensAPI.getNPCRegistry();
        for (NPC existing : new ArrayList<>(registry.getNPCs())) {
            if (existing.getName().startsWith("Nebula ")) existing.destroy();
        }
        npcActions.clear();
        createNpc("Nebula Survival", "Notch", lobby.clone().add(-6, 0, 4), "SURVIVAL");
        createNpc("Nebula Leaderboard", "jeb_", lobby.clone().add(-2, 0, 4), "LEADERBOARD");
        createNpc("Nebula Settings", "Alex", lobby.clone().add(2, 0, 4), "SETTINGS");
        createNpc("Nebula Network", "Steve", lobby.clone().add(6, 0, 4), "MENU");
        refreshLeaderboardHologram();
    }

    private void createNpc(String name, String skin, Location location, String action) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, name);
        npc.getOrAddTrait(SkinTrait.class).setSkinName(skin, true);
        npc.setProtected(true);
        npc.setUseMinecraftAI(false);
        npc.spawn(location);
        npcActions.put(npc.getId(), action);
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        String action = npcActions.get(event.getNPC().getId());
        if (action == null) return;
        Player player = event.getClicker();
        switch (action) {
            case "SURVIVAL" -> {
                World world = Bukkit.getWorld("survival_1");
                if (world != null) player.teleport(world.getSpawnLocation());
                else player.sendMessage(PREFIX + "Survival is not loaded.");
            }
            case "LEADERBOARD" -> openLeaderboard(player);
            case "SETTINGS" -> player.performCommand("settings");
            case "MENU" -> player.performCommand("menu");
            default -> player.sendMessage(PREFIX + "Welcome to Nebula Network.");
        }
    }
}
