package net.nebula.core;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Fireball;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Non-damage hide-and-seek mode. Hiders are visually disguised as real mobs;
 * the hunter tags them by interacting at close range. No player damage is used.
 */
public final class MobHuntGame implements Listener {
    private static final String WORLD_NAME = "mob_hunt";
    private static final String MENU_TITLE = ChatColor.DARK_PURPLE + "Mob Hunt";
    private static final String PICK_TITLE = ChatColor.DARK_PURPLE + "Choose 2 Hunt Tools";
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
            + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
    private static final int ROUNDS = 3;
    private static final int ROUND_SECONDS = 300;
    private static final int HUNTER_LIVES = 3;
    private static final int MOB_PICK_SECONDS = 15;
    private static final int CAMP_LOW_LIMIT = 5;
    private static final int CAMP_HIGH_LIMIT = 12;
    private static final String MOB_PICK_TITLE = ChatColor.DARK_GREEN + "Choose Your Mob";
    private static final String MOB_SEARCH_TITLE = ChatColor.DARK_AQUA + "Mob Search";
    private static final String ABILITY_TITLE = ChatColor.DARK_PURPLE + "Mob Ability Cooldowns";
    private static final String ADMIN_TITLE = ChatColor.DARK_PURPLE + "Mob Hunt Admin Settings";
    private String currentMap = "village";
    private static final int HUNTER_HIDE_SECONDS = 15;
    private static final List<String> MOB_TYPES = List.of(
            "PIG", "COW", "SHEEP", "CHICKEN", "WOLF", "CAT", "FOX", "RABBIT",
            "BAT", "SPIDER", "CAVE_SPIDER", "ENDERMAN", "CREEPER", "ZOMBIE",
            "SKELETON", "SLIME", "MAGMA_CUBE", "GHAST", "WARDEN", "GOAT",
            "PANDA", "BEE", "FROG", "TURTLE", "AXOLOTL", "CAMEL", "SNIFFER"
    );
    private static final List<Material> HUNTER_TOOLS = List.of(
            Material.SPYGLASS, Material.MAP, Material.FEATHER, Material.OAK_PLANKS,
            Material.LADDER, Material.TORCH, Material.WATER_BUCKET, Material.HAY_BLOCK,
            Material.SLIME_BALL, Material.SCAFFOLDING, Material.LANTERN, Material.CLOCK,
            Material.GLOWSTONE, Material.OAK_TRAPDOOR, Material.HONEYCOMB,
            Material.FLOWER_POT, Material.COMPASS, Material.PAPER
    );

    private final JavaPlugin plugin;
    private final Location lobby;
    private final NamespacedKey npcKey;
    private final Random random = new Random();
    private final LinkedHashSet<UUID> queue = new LinkedHashSet<>();
    private World world;
    private BukkitTask queueTask;
    private int queueSeconds;
    private boolean queueAccelerated;
    private Match match;
    private boolean ready;
    private int arenaBaseY;
    private final Set<UUID> searchingMob = ConcurrentHashMap.newKeySet();
    private final Map<UUID, UUID> partyInvites = new HashMap<>();
    private final Map<UUID, LinkedHashSet<UUID>> parties = new HashMap<>();
    private final Map<UUID, UUID> privateInvites = new HashMap<>();
    private final LinkedHashSet<UUID> privateRoster = new LinkedHashSet<>();
    private UUID privateHost;
    private final File recoveryFile;
    private final File historyFile;
    private final YamlConfiguration recovery;
    private final YamlConfiguration history;

    public MobHuntGame(JavaPlugin plugin, Location lobby) {
        this.plugin = plugin;
        this.lobby = lobby.clone();
        this.npcKey = new NamespacedKey(plugin, "nebula_mobhunt_npc");
        plugin.getDataFolder().mkdirs();
        this.recoveryFile = new File(plugin.getDataFolder(), "mobhunt-recovery.yml");
        this.historyFile = new File(plugin.getDataFolder(), "mobhunt-history.yml");
        this.recovery = YamlConfiguration.loadConfiguration(recoveryFile);
        this.history = YamlConfiguration.loadConfiguration(historyFile);
    }

    public void enable() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        if (!Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            plugin.getLogger().warning("Mob Hunt is unavailable: LibsDisguises did not load.");
            return;
        }
        world = Bukkit.getWorld(WORLD_NAME);
        if (world == null) {
            WorldCreator creator = new WorldCreator(WORLD_NAME);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            world = Bukkit.createWorld(creator);
        }
        if (world == null) {
            plugin.getLogger().severe("Mob Hunt world could not be created.");
            return;
        }
        buildVillage();
        spawnLobbyNpc();
        ready = true;
        plugin.getLogger().info("Mob Hunt is ready. Village arena: " + WORLD_NAME);
    }

    public void openMenu(Player player) {
        if (!ready) {
            player.sendMessage(PREFIX + ChatColor.RED + "Mob Hunt is currently unavailable. Please tell an admin.");
            return;
        }
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        inv.setItem(11, named(Material.COMPASS, ChatColor.GREEN + "Join Mob Hunt",
                "Queue with at least 2 players", "20-second start countdown", "3+ players: 10-second countdown"));
        inv.setItem(13, named(Material.PLAYER_HEAD, ChatColor.AQUA + "Match Information",
                "3 rounds • Random hunter each round", "5-minute combat survival", "3 hunter lives • Choose mobs in rounds 2-3"));
        inv.setItem(15, named(Material.BARRIER, ChatColor.RED + "Leave Queue",
                "Click to leave the waiting queue"));
        player.openInventory(inv);
    }

    public void handleCommand(Player player, String[] args) {
        if (!ready) {
            player.sendMessage(PREFIX + ChatColor.RED + "Mob Hunt is unavailable until its disguise dependency loads.");
            return;
        }
        if (args.length == 0) {
            openMenu(player);
            return;
        }
        switch (args[0].toLowerCase()) {
            case "join", "queue" -> joinQueue(player);
            case "leave", "cancel" -> leaveQueue(player);
            case "status" -> sendStatus(player);
            case "admin" -> { if (!player.hasPermission("nebulacore.admin")) { player.sendMessage(PREFIX + ChatColor.RED + "No permission."); return; } openAdminMenu(player); }
            case "party" -> handlePartyCommand(player, args);
            case "private" -> handlePrivateCommand(player, args);
            case "stats" -> showStats(player, args.length > 1 ? args[1] : player.getName());
            case "leaderboard", "top" -> showLeaderboard(player);
            case "history" -> showHistory(player);
            default -> player.sendMessage(PREFIX + "Use /mobhunt [join|leave|status|party|private|stats|leaderboard|history|admin].");
        }
    }

    private UUID partyLeaderOf(UUID member) {
        for (Map.Entry<UUID, LinkedHashSet<UUID>> entry : parties.entrySet()) {
            if (entry.getValue().contains(member)) return entry.getKey();
        }
        return null;
    }

    private void handlePartyCommand(Player player, String[] args) {
        UUID id = player.getUniqueId();
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "info";
        switch (sub) {
            case "invite" -> {
                if (args.length < 3) { player.sendMessage(PREFIX + "Usage: /mobhunt party invite <player>"); return; }
                UUID leader = partyLeaderOf(id);
                if (leader != null && !leader.equals(id)) { player.sendMessage(PREFIX + "Only your party leader can invite."); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || target.equals(player)) { player.sendMessage(PREFIX + ChatColor.RED + "That player is not online."); return; }
                LinkedHashSet<UUID> group = parties.computeIfAbsent(id, k -> new LinkedHashSet<>(Set.of(id)));
                if (group.size() >= MAX_PLAYERS) { player.sendMessage(PREFIX + ChatColor.RED + "Party is full (8 players)."); return; }
                partyInvites.put(target.getUniqueId(), id);
                target.sendMessage(PREFIX + ChatColor.AQUA + player.getName() + " invited you to a Mob Hunt party. Use /mobhunt party accept.");
                player.sendMessage(PREFIX + "Party invitation sent to " + target.getName() + ".");
                Bukkit.getScheduler().runTaskLater(plugin, () -> partyInvites.remove(target.getUniqueId(), id), 1200L);
            }
            case "accept" -> {
                UUID host = partyInvites.remove(id);
                if (host == null || Bukkit.getPlayer(host) == null) { player.sendMessage(PREFIX + ChatColor.RED + "You have no valid party invitation."); return; }
                LinkedHashSet<UUID> group = parties.computeIfAbsent(host, k -> new LinkedHashSet<>(Set.of(host)));
                if (group.size() >= MAX_PLAYERS) { player.sendMessage(PREFIX + ChatColor.RED + "That party is full."); return; }
                UUID existing = partyLeaderOf(id);
                if (existing != null) parties.get(existing).remove(id);
                group.add(id);
                player.sendMessage(PREFIX + ChatColor.GREEN + "You joined " + Bukkit.getPlayer(host).getName() + "'s party.");
                Player leaderPlayer = Bukkit.getPlayer(host);
                if (leaderPlayer != null) leaderPlayer.sendMessage(PREFIX + player.getName() + " joined your party.");
            }
            case "leave" -> {
                UUID leader = partyLeaderOf(id);
                if (leader == null) { player.sendMessage(PREFIX + "You are not in a party."); return; }
                LinkedHashSet<UUID> group = parties.get(leader);
                group.remove(id);
                if (id.equals(leader)) {
                    for (UUID member : new ArrayList<>(group)) {
                        Player online = Bukkit.getPlayer(member);
                        if (online != null) online.sendMessage(PREFIX + ChatColor.YELLOW + "The party was disbanded.");
                    }
                    parties.remove(leader);
                } else if (group.size() <= 1) parties.remove(leader);
                player.sendMessage(PREFIX + "You left the party.");
            }
            default -> {
                UUID leader = partyLeaderOf(id);
                if (leader == null) player.sendMessage(PREFIX + "No party. Use /mobhunt party invite <player>.");
                else player.sendMessage(PREFIX + "Party leader: " + nameOf(leader) + " • Members: "
                        + parties.getOrDefault(leader, new LinkedHashSet<>()).stream().map(this::nameOf).collect(Collectors.joining(", ")));
            }
        }
    }

    private void handlePrivateCommand(Player player, String[] args) {
        UUID id = player.getUniqueId();
        String sub = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "help";
        switch (sub) {
            case "create" -> {
                if (match != null) { player.sendMessage(PREFIX + ChatColor.RED + "A match is already active."); return; }
                if (!queue.isEmpty()) { player.sendMessage(PREFIX + ChatColor.RED + "Wait for the public queue to finish before opening a private lobby."); return; }
        if (privateHost != null && !privateHost.equals(id)) { player.sendMessage(PREFIX + ChatColor.RED + "Another private lobby is already open."); return; }
                privateHost = id;
                privateRoster.clear();
                privateRoster.add(id);
                player.sendMessage(PREFIX + ChatColor.GREEN + "Private Mob Hunt lobby created. Invite players, then use /mobhunt private start.");
            }
            case "invite" -> {
                if (!id.equals(privateHost)) { player.sendMessage(PREFIX + ChatColor.RED + "Create a private lobby first; only its host can invite."); return; }
                if (args.length < 3) { player.sendMessage(PREFIX + "Usage: /mobhunt private invite <player>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                if (target == null || target.equals(player)) { player.sendMessage(PREFIX + ChatColor.RED + "That player is not online."); return; }
                privateInvites.put(target.getUniqueId(), id);
                target.sendMessage(PREFIX + ChatColor.AQUA + player.getName() + " invited you to a private Mob Hunt. Use /mobhunt private accept.");
                player.sendMessage(PREFIX + "Private invitation sent.");
                Bukkit.getScheduler().runTaskLater(plugin, () -> privateInvites.remove(target.getUniqueId(), id), 1200L);
            }
            case "accept" -> {
                UUID host = privateInvites.remove(id);
                if (host == null || !host.equals(privateHost) || Bukkit.getPlayer(host) == null) {
                    player.sendMessage(PREFIX + ChatColor.RED + "You have no valid private-match invitation."); return;
                }
                if (privateRoster.size() >= MAX_PLAYERS) { player.sendMessage(PREFIX + ChatColor.RED + "Private lobby is full."); return; }
                privateRoster.add(id);
                player.sendMessage(PREFIX + ChatColor.GREEN + "You joined the private Mob Hunt lobby.");
                Bukkit.getPlayer(host).sendMessage(PREFIX + player.getName() + " accepted your invitation.");
            }
            case "start" -> startPrivateMatch(player);
            case "cancel", "close" -> {
                if (!id.equals(privateHost)) { player.sendMessage(PREFIX + ChatColor.RED + "Only the host can close this lobby."); return; }
                for (UUID member : privateRoster) {
                    Player online = Bukkit.getPlayer(member);
                    if (online != null && !online.equals(player)) online.sendMessage(PREFIX + ChatColor.YELLOW + "The private Mob Hunt lobby was closed.");
                }
                privateRoster.clear(); privateHost = null;
                player.sendMessage(PREFIX + "Private lobby closed.");
            }
            default -> player.sendMessage(PREFIX + "Private: /mobhunt private create|invite <player>|accept|start|cancel");
        }
    }

    private int configuredRoundSeconds() {
        return Math.max(60, Math.min(900, plugin.getConfig().getInt("mobhunt.round-seconds", ROUND_SECONDS)));
    }

    private int configuredHunterLives() {
        return Math.max(1, Math.min(5, plugin.getConfig().getInt("mobhunt.hunter-lives", HUNTER_LIVES)));
    }

    private int configuredMobPickSeconds() {
        return Math.max(5, Math.min(60, plugin.getConfig().getInt("mobhunt.mob-pick-seconds", MOB_PICK_SECONDS)));
    }

    private boolean antiCampingEnabled() {
        return plugin.getConfig().getBoolean("mobhunt.anti-camping", true);
    }

    private boolean randomEventsEnabled() {
        return plugin.getConfig().getBoolean("mobhunt.random-events", true);
    }

    private int campLowLimit() {
        return Math.max(3, Math.min(12, plugin.getConfig().getInt("mobhunt.camp-low-limit", CAMP_LOW_LIMIT)));
    }

    private int campHighLimit() {
        return Math.max(8, Math.min(24, plugin.getConfig().getInt("mobhunt.camp-high-limit", CAMP_HIGH_LIMIT)));
    }

    private boolean isInAnotherMinigame(Player player) {
        String name = player.getWorld().getName();
        return Set.of("bedwars_1", "bedwars_2", "duel_arena", "pillars_1", "pillars_2").contains(name);
    }

    private boolean isMobEnabled(String mob) {
        return !plugin.getConfig().getStringList("mobhunt.disabled-mobs").contains(mob);
    }

    private long configuredAbilityCooldown(String mob) {
        int seconds = plugin.getConfig().getInt("mobhunt.ability-cooldowns." + mob, (int) (abilityCooldown(mob) / 1000L));
        return Math.max(3, Math.min(120, seconds)) * 1000L;
    }

    private void showStats(Player viewer, String targetName) {
        org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        String path = "mobhunt.stats." + target.getUniqueId();
        viewer.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Mob Hunt stats for " + targetName);
        viewer.sendMessage(ChatColor.GRAY + "Matches: " + ChatColor.WHITE + plugin.getConfig().getInt(path + ".matches", 0)
                + ChatColor.GRAY + " • Match wins: " + ChatColor.GREEN + plugin.getConfig().getInt(path + ".wins", 0));
        viewer.sendMessage(ChatColor.GRAY + "Hunter round wins: " + ChatColor.RED + plugin.getConfig().getInt(path + ".hunter-round-wins", 0)
                + ChatColor.GRAY + " • Hider round wins: " + ChatColor.AQUA + plugin.getConfig().getInt(path + ".hider-round-wins", 0));
        viewer.sendMessage(ChatColor.GRAY + "Kills: " + ChatColor.WHITE + plugin.getConfig().getInt(path + ".kills", 0)
                + ChatColor.GRAY + " • Survival: " + ChatColor.AQUA + plugin.getConfig().getInt(path + ".survival-seconds", 0) + "s"
                + ChatColor.GRAY + " • Best: " + ChatColor.AQUA + plugin.getConfig().getInt(path + ".best-survival-seconds", 0) + "s");
        viewer.sendMessage(ChatColor.GRAY + "Best points: " + ChatColor.GOLD + plugin.getConfig().getInt(path + ".best-points", 0)
                + ChatColor.GRAY + " • Favorite mob: " + ChatColor.LIGHT_PURPLE + plugin.getConfig().getString(path + ".favorite-mob", "N/A"));
    }

    private void showHistory(Player viewer) {
        List<Map<?, ?>> records = history.getMapList("matches");
        if (records.isEmpty()) { viewer.sendMessage(PREFIX + "No completed Mob Hunt matches have been recorded yet."); return; }
        viewer.sendMessage(PREFIX + ChatColor.GOLD + "" + ChatColor.BOLD + "RECENT MOB HUNT MATCHES");
        int shown = 0;
        for (int i = records.size() - 1; i >= 0 && shown < 5; i--, shown++) {
            Map<?, ?> record = records.get(i);
            Object mapValue = record.get("map");
            Object winnerValue = record.get("winners");
            Object roundsValue = record.get("rounds");
            String map = mapValue == null ? "unknown" : String.valueOf(mapValue);
            String winners = winnerValue == null ? "No winner" : String.valueOf(winnerValue);
            String rounds = roundsValue == null ? "0" : String.valueOf(roundsValue);
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "#" + (shown + 1) + " " + ChatColor.WHITE + pretty(map)
                    + ChatColor.GRAY + " • Rounds: " + rounds + " • Winner: " + winners);
        }
    }

    private void showLeaderboard(Player viewer) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("mobhunt.stats");
        if (section == null || section.getKeys(false).isEmpty()) { viewer.sendMessage(PREFIX + "No Mob Hunt leaderboard records yet."); return; }
        List<String> ids = new ArrayList<>(section.getKeys(false));
        ids.sort(Comparator.comparingInt((String id) -> plugin.getConfig().getInt("mobhunt.stats." + id + ".wins", 0)).reversed()
                .thenComparing(Comparator.comparingInt((String id) -> plugin.getConfig().getInt("mobhunt.stats." + id + ".kills", 0)).reversed()));
        viewer.sendMessage(PREFIX + ChatColor.GOLD + "" + ChatColor.BOLD + "MOB HUNT LEADERBOARD");
        for (int i = 0; i < Math.min(5, ids.size()); i++) {
            UUID id;
            try { id = UUID.fromString(ids.get(i)); } catch (IllegalArgumentException ex) { continue; }
            String name = Bukkit.getOfflinePlayer(id).getName();
            viewer.sendMessage(ChatColor.LIGHT_PURPLE + "#" + (i + 1) + " " + ChatColor.WHITE + (name == null ? "Unknown" : name)
                    + ChatColor.GRAY + " • Wins: " + ChatColor.GREEN + plugin.getConfig().getInt("mobhunt.stats." + id + ".wins", 0)
                    + ChatColor.GRAY + " • Kills: " + plugin.getConfig().getInt("mobhunt.stats." + id + ".kills", 0));
        }
    }

    private void persistRecovery(UUID id, PlayerSnapshot snapshot) {
        String path = "players." + id;
        recovery.set(path + ".contents", serializeItems(snapshot.contents));
        recovery.set(path + ".armor", serializeItems(snapshot.armor));
        recovery.set(path + ".offhand", snapshot.offhand == null ? null : snapshot.offhand.serialize());
        recovery.set(path + ".gamemode", snapshot.gameMode.name());
        recovery.set(path + ".food", snapshot.food);
        recovery.set(path + ".saturation", snapshot.saturation);
        recovery.set(path + ".health", snapshot.health);
        recovery.set(path + ".allow-flight", snapshot.allowFlight);
        recovery.set(path + ".flying", snapshot.flying);
        recovery.set(path + ".walk-speed", snapshot.walkSpeed);
        recovery.set(path + ".fly-speed", snapshot.flySpeed);
        recovery.set(path + ".level", snapshot.level);
        recovery.set(path + ".exp", snapshot.exp);
        saveRecovery();
    }

    private List<Object> serializeItems(ItemStack[] items) {
        List<Object> serialized = new ArrayList<>();
        for (ItemStack item : items) serialized.add(item == null ? null : item.serialize());
        return serialized;
    }

    private ItemStack[] deserializeItems(List<?> input, int size) {
        ItemStack[] items = new ItemStack[size];
        if (input == null) return items;
        for (int i = 0; i < Math.min(size, input.size()); i++) {
            Object value = input.get(i);
            if (value instanceof ItemStack item) items[i] = item.clone();
            else if (value instanceof Map<?, ?> map) {
                Map<String, Object> typed = new LinkedHashMap<>();
                map.forEach((k, v) -> typed.put(String.valueOf(k), v));
                try { items[i] = ItemStack.deserialize(typed); } catch (IllegalArgumentException ignored) { }
            }
        }
        return items;
    }

    private void saveRecovery() {
        try { recovery.save(recoveryFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save Mob Hunt recovery data: " + ex.getMessage()); }
    }

    private void clearRecovery(UUID id) {
        recovery.set("players." + id, null);
        saveRecovery();
    }

    private boolean restorePendingRecovery(Player player) {
        ConfigurationSection section = recovery.getConfigurationSection("players." + player.getUniqueId());
        if (section == null) return false;
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setContents(deserializeItems(section.getList("contents"), inv.getSize()));
        inv.setArmorContents(deserializeItems(section.getList("armor"), 4));
        Object offhand = section.get("offhand");
        if (offhand instanceof Map<?, ?> map) {
            Map<String, Object> typed = new LinkedHashMap<>();
            map.forEach((k, v) -> typed.put(String.valueOf(k), v));
            try { inv.setItemInOffHand(ItemStack.deserialize(typed)); } catch (IllegalArgumentException ignored) { }
        }
        try { player.setGameMode(GameMode.valueOf(section.getString("gamemode", "ADVENTURE"))); }
        catch (IllegalArgumentException ignored) { player.setGameMode(GameMode.ADVENTURE); }
        player.setFoodLevel(section.getInt("food", 20));
        player.setSaturation((float) section.getDouble("saturation", 20.0));
        player.setHealth(Math.max(1.0, Math.min(section.getDouble("health", 20.0), player.getAttribute(Attribute.MAX_HEALTH).getValue())));
        player.setLevel(section.getInt("level", 0));
        player.setExp((float) section.getDouble("exp", 0.0));
        for (PotionEffect active : player.getActivePotionEffects()) player.removePotionEffect(active.getType());
        player.setWalkSpeed((float) section.getDouble("walk-speed", 0.2));
        player.setFlySpeed((float) section.getDouble("fly-speed", 0.1));
        boolean allowFlight = section.getBoolean("allow-flight", false);
        player.setAllowFlight(allowFlight);
        player.setFlying(allowFlight && section.getBoolean("flying", false));
        player.teleport(lobby);
        player.updateInventory();
        clearRecovery(player.getUniqueId());
        player.sendMessage(PREFIX + ChatColor.GREEN + "Your previous Mob Hunt session was recovered safely.");
        return true;
    }

    private void logMatch(String type, Match active, String detail) {
        plugin.getLogger().info("[MobHunt][" + type + "] " + detail + " | round=" + active.round
                + " | map=" + active.mapName + " | players=" + active.players);
    }

    private void saveMatchHistory(Match active, String winners) {
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<?, ?> stored : history.getMapList("matches")) {
            Map<String, Object> typed = new LinkedHashMap<>();
            stored.forEach((k, v) -> typed.put(String.valueOf(k), v));
            records.add(typed);
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("ended-at", System.currentTimeMillis());
        record.put("map", active.mapName);
        record.put("private", active.privateMatch);
        record.put("rounds", active.round);
        record.put("players", active.players.stream().map(UUID::toString).toList());
        record.put("winners", winners);
        record.put("scores", active.players.stream().collect(Collectors.toMap(UUID::toString, id -> active.scores.getOrDefault(id, 0))));
        records.add(record);
        while (records.size() > 50) records.remove(0);
        history.set("matches", records);
        try { history.save(historyFile); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save Mob Hunt history: " + ex.getMessage()); }
    }

    public boolean handleReconnect(Player player) {
        UUID id = player.getUniqueId();
        Match active = match;
        if (active == null) return restorePendingRecovery(player);
        if (!active.players.contains(id) || active.forfeited.contains(id)) return restorePendingRecovery(player);
        BukkitTask task = active.disconnectTasks.remove(id);
        if (task != null) task.cancel();
        active.disconnected.remove(id);
        resetForRound(player);
        player.teleport(new Location(world, 0.5, arenaBaseY + 1.0, 0.5));
        if (active.roundEnded) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(PREFIX + ChatColor.YELLOW + "You reconnected between rounds. You will rejoin when the next round begins.");
        } else if (id.equals(active.hunter)) {
            player.setWalkSpeed(active.hunterLocked ? 0.0f : 0.2f);
            if (active.hunterLocked) player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Math.max(1, active.secondsLeft) * 20, 0, false, false, true));
            giveHunterBaseKit(player);
            Set<Material> tools = active.selectedTools.getOrDefault(id, Collections.emptySet());
            for (Material tool : tools) player.getInventory().addItem(new ItemStack(tool));
        } else if (active.aliveHiders.contains(id) || !active.tagged.contains(id)) {
            active.aliveHiders.add(id);
            player.teleport(randomVillageSpawn());
            giveHiderKit(player);
            String mob = active.mobForms.get(id);
            if (mob != null) { applyDisguise(player, mob); applyMobAbility(player, mob); }
            else { active.mobPickPending.add(id); openMobPicker(player, ""); }
        } else {
            player.setGameMode(GameMode.SPECTATOR);
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + "Reconnected to your Mob Hunt match.");
        logMatch("RECONNECT", active, "player=" + player.getName());
        return true;
    }

    public void shutdown() {
        Match active = match;
        if (active == null) return;
        active.cancelRoundTasks();
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            PlayerSnapshot snapshot = active.snapshots.get(id);
            if (player != null && player.isOnline() && snapshot != null) {
                DisguiseAPI.undisguiseToAll(player);
                snapshot.restoreAtLobby(player, lobby);
                clearRecovery(id);
            }
        }
        logMatch("SHUTDOWN", active, "restored online players; offline recovery snapshots retained");
        match = null;
    }

    private void sendStatus(Player player) {
        if (match != null) {
            player.sendMessage(PREFIX + ChatColor.AQUA + "Mob Hunt round " + match.round
                    + "/" + ROUNDS + ChatColor.GRAY + " • Players: " + match.players.size());
        } else {
            player.sendMessage(PREFIX + ChatColor.AQUA + "Queue: " + queue.size()
                    + "/" + MAX_PLAYERS + ChatColor.GRAY + " • Minimum: " + MIN_PLAYERS
                    + (queueTask == null ? "" : " • Starts in " + queueSeconds + "s"));
        }
    }

    private void joinQueue(Player player) {
        UUID id = player.getUniqueId();
        if (match != null) { player.sendMessage(PREFIX + ChatColor.RED + "A Mob Hunt match is already running."); return; }
        if (isInAnotherMinigame(player)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current minigame before joining Mob Hunt."); return;
        }
        UUID leader = partyLeaderOf(id);
        if (leader != null && !leader.equals(id)) {
            player.sendMessage(PREFIX + ChatColor.YELLOW + "Your party leader must queue the party."); return;
        }
        LinkedHashSet<UUID> group = parties.getOrDefault(id, new LinkedHashSet<>(Set.of(id)));
        if (group.stream().anyMatch(queue::contains)) { player.sendMessage(PREFIX + "Your party is already queued."); return; }
        if (queue.size() + group.size() > MAX_PLAYERS) { player.sendMessage(PREFIX + ChatColor.RED + "Your party does not fit in the remaining queue slots."); return; }
        for (UUID member : group) {
            Player online = Bukkit.getPlayer(member);
            if (online == null || !online.isOnline()) { player.sendMessage(PREFIX + ChatColor.RED + "Every party member must be online to queue."); return; }
            if (isInAnotherMinigame(online)) {
                player.sendMessage(PREFIX + ChatColor.RED + "Every party member must leave their current minigame first."); return;
            }
        }
        queue.addAll(group);
        broadcastQueue();
        if (queue.size() >= MIN_PLAYERS && queueTask == null) startQueueCountdown(20);
        else if (queue.size() >= 3 && queueTask != null && !queueAccelerated) {
            queueSeconds = 10; queueAccelerated = true;
            broadcast(ChatColor.GOLD + "3 players joined! Mob Hunt now starts in 10 seconds.");
        }
    }

    private void leaveQueue(Player player) {
        UUID id = player.getUniqueId();
        UUID leader = partyLeaderOf(id);
        Set<UUID> group = leader == null ? Set.of(id) : parties.getOrDefault(leader, new LinkedHashSet<>(Set.of(id)));
        boolean removed = false;
        for (UUID member : group) removed |= queue.remove(member);
        if (removed) { player.sendMessage(PREFIX + "Your party left the Mob Hunt queue."); broadcastQueue(); }
        else if (match != null && match.players.contains(id)) player.sendMessage(PREFIX + ChatColor.RED + "You cannot leave during a round. Use /spawn after the match.");
        else player.sendMessage(PREFIX + "You are not in the Mob Hunt queue.");
        if (queue.size() < MIN_PLAYERS) cancelQueueCountdown();
    }

    private void startQueueCountdown(int seconds) {
        cancelQueueCountdown();
        queueSeconds = seconds;
        queueAccelerated = queue.size() >= 3;
        if (queueAccelerated) queueSeconds = 10;
        queueTask = new BukkitRunnable() {
            @Override public void run() {
                queue.removeIf(id -> Bukkit.getPlayer(id) == null || !Bukkit.getPlayer(id).isOnline());
                if (queue.size() < MIN_PLAYERS) {
                    cancelQueueCountdown();
                    broadcast(ChatColor.RED + "Mob Hunt countdown stopped: at least 2 players are required.");
                    return;
                }
                if (queue.size() >= 3 && !queueAccelerated) {
                    queueSeconds = 10;
                    queueAccelerated = true;
                    broadcast(ChatColor.GOLD + "3+ players are waiting. Countdown reduced to 10 seconds.");
                }
                for (UUID id : queue) {
                    Player queued = Bukkit.getPlayer(id);
                    if (queued != null) queued.sendActionBar(ChatColor.LIGHT_PURPLE + "Mob Hunt starts in "
                            + ChatColor.WHITE + queueSeconds + ChatColor.LIGHT_PURPLE + "s • "
                            + ChatColor.AQUA + queue.size() + " players");
                }
                if (queueSeconds <= 0) {
                    cancelQueueCountdown();
                    startMatch();
                    return;
                }
                queueSeconds--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelQueueCountdown() {
        if (queueTask != null) queueTask.cancel();
        queueTask = null;
        queueSeconds = 0;
        queueAccelerated = false;
    }

    private void broadcastQueue() {
        broadcast(ChatColor.AQUA + "Mob Hunt queue: " + queue.size() + "/" + MAX_PLAYERS
                + ChatColor.GRAY + " • Need at least 2 players.");
        for (UUID id : queue) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(PREFIX + "Waiting for other players...");
        }
    }

    private void broadcast(String message) {
        for (UUID id : queue) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) player.sendMessage(PREFIX + message);
        }
    }

    private void startMatch() {
        List<Player> players = queue.stream().map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline()).collect(Collectors.toCollection(ArrayList::new));
        queue.clear();
        beginMatch(players, false);
    }

    private void startPrivateMatch(Player host) {
        if (match != null) { host.sendMessage(PREFIX + ChatColor.RED + "Another Mob Hunt match is running."); return; }
        if (!queue.isEmpty()) { host.sendMessage(PREFIX + ChatColor.RED + "The public Mob Hunt queue is already active."); return; }
        if (!host.getUniqueId().equals(privateHost)) { host.sendMessage(PREFIX + ChatColor.RED + "Only the private-match host can start it."); return; }
        List<Player> players = privateRoster.stream().map(Bukkit::getPlayer)
                .filter(p -> p != null && p.isOnline()).collect(Collectors.toCollection(ArrayList::new));
        if (players.size() < MIN_PLAYERS) { host.sendMessage(PREFIX + ChatColor.RED + "Invite at least one more online player."); return; }
        privateRoster.clear(); privateHost = null;
        beginMatch(players, true);
    }

    private void beginMatch(List<Player> players, boolean privateMatch) {
        if (match != null) { players.forEach(p -> p.sendMessage(PREFIX + ChatColor.RED + "Another Mob Hunt match is already running.")); return; }
        if (players.size() < MIN_PLAYERS || players.size() > MAX_PLAYERS) {
            players.forEach(p -> p.sendMessage(PREFIX + ChatColor.RED + "Mob Hunt requires 2–8 online players."));
            return;
        }
        if (players.stream().anyMatch(this::isInAnotherMinigame)) {
            players.forEach(p -> p.sendMessage(PREFIX + ChatColor.RED + "Leave your current minigame before starting Mob Hunt."));
            return;
        }
        Match next = new Match();
        next.privateMatch = privateMatch;
        next.mapName = plugin.getConfig().getBoolean("mobhunt.random-map", true)
                ? (random.nextBoolean() ? "village" : "castle")
                : plugin.getConfig().getString("mobhunt.default-map", "village");
        if ("castle".equalsIgnoreCase(next.mapName)) buildCastle(); else buildVillage();
        match = next;
        for (Player player : players) {
            UUID id = player.getUniqueId();
            next.players.add(id);
            PlayerSnapshot snapshot = new PlayerSnapshot(player);
            next.snapshots.put(id, snapshot);
            persistRecovery(id, snapshot);
            next.scores.put(id, 0);
            next.survivalSeconds.put(id, 0);
            next.kills.put(id, 0);
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Mob Hunt match started! 3 rounds. Map: " + pretty(next.mapName)
                    + (privateMatch ? ChatColor.GOLD + " • Private match" : ""));
        }
        logMatch("START", next, "map=" + next.mapName + ", private=" + privateMatch);
        startRound(next);
    }

    private void startRound(Match active) {
        if (match != active || active.ending) return;
        active.round++;
        active.roundEnded = false;
        active.lastHiderAnnounced = false;
        if (active.round > ROUNDS) {
            finishMatch(active);
            return;
        }
        active.cancelRoundTasks();
        active.aliveHiders.clear();
        active.tagged.clear();
        active.selectedTools.clear();
        active.mobForms.clear();
        active.mobPickPending.clear();
        active.hunterLives = configuredHunterLives();
        active.hunterKillStreak = 0;
        active.hunterRoundWins.clear();
        active.hiderRoundWins.clear();
        active.randomEventCount = 0;
        active.abilityReadyAt.clear();
        world.getWorldBorder().setCenter(0.5, 0.5);
        world.getWorldBorder().setSize("castle".equals(active.mapName) ? 84.0 : 92.0);
        active.zoneShrunk = false;

        List<UUID> candidates = new ArrayList<>(active.players);
        candidates.removeIf(id -> Bukkit.getPlayer(id) == null || !Bukkit.getPlayer(id).isOnline());
        if (candidates.size() < MIN_PLAYERS) {
            finishMatch(active);
            return;
        }
        active.hunter = candidates.get(random.nextInt(candidates.size()));
        active.hunterLocked = true;
        active.secondsLeft = configuredRoundSeconds();
        active.hunterLives = configuredHunterLives();
        Player hunter = Bukkit.getPlayer(active.hunter);
        for (UUID id : candidates) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            resetForRound(player);
            if (id.equals(active.hunter)) {
                player.teleport(new Location(world, 0.5, arenaBaseY + 1.0, 0.5));
                player.setWalkSpeed(0.0f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, HUNTER_HIDE_SECONDS * 20, 0, false, false, true));
                player.sendTitle(ChatColor.RED + "YOU ARE THE HUNTER",
                        ChatColor.GRAY + "You are frozen and blinded for 15 seconds", 5, 45, 10);
                giveHunterBaseKit(player);
                openHunterTools(player);
                player.sendMessage(PREFIX + "Choose exactly 2 utility items before the 15-second release.");
            } else {
                active.aliveHiders.add(id);
                player.teleport(randomVillageSpawn());
                giveHiderKit(player);
                if (active.round == 1) {
                    assignMob(active, player, randomEnabledMob());
                } else {
                    active.mobPickPending.add(id);
                    openMobPicker(player, "");
                    player.sendTitle(ChatColor.GREEN + "CHOOSE YOUR MOB",
                            ChatColor.GRAY + "Round " + active.round + ": pick any mob", 5, 45, 10);
                }
            }
        }
        if (hunter != null) {
            active.releaseTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match != active || active.ending || active.roundEnded) return;
                active.hunterLocked = false;
                Player currentHunter = Bukkit.getPlayer(active.hunter);
                if (currentHunter != null) {
                    currentHunter.setWalkSpeed(0.2f);
                    currentHunter.removePotionEffect(PotionEffectType.DARKNESS);
                    Set<Material> chosenTools = active.selectedTools.computeIfAbsent(active.hunter, ignored -> EnumSet.noneOf(Material.class));
                    for (Material fallback : HUNTER_TOOLS) {
                        if (chosenTools.size() >= 2) break;
                        if (chosenTools.add(fallback)) currentHunter.getInventory().addItem(new ItemStack(fallback));
                    }
                    if (chosenTools.size() == 2) currentHunter.sendMessage(PREFIX + ChatColor.GREEN + "Your 2 tools are ready.");
                    currentHunter.sendTitle(ChatColor.GOLD + "GO!", ChatColor.GRAY + "Defeat the hiders!", 0, 25, 5);
                    currentHunter.sendMessage(PREFIX + "Your tools are ready. Defeat the hiders! You have 3 lives.");
                }
            }, HUNTER_HIDE_SECONDS * 20L);
        }
        active.mobPickTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match != active || active.roundEnded || active.ending) return;
            for (UUID pending : new ArrayList<>(active.mobPickPending)) {
                Player hider = Bukkit.getPlayer(pending);
                if (hider != null) assignMob(active, hider, randomEnabledMob());
            }
        }, configuredMobPickSeconds() * 20L);
        active.roundTask = new BukkitRunnable() {
            @Override public void run() {
                if (match != active || active.ending || active.roundEnded) {
                    cancel();
                    return;
                }
                if (active.secondsLeft <= 0) {
                    endRound(active, false);
                    cancel();
                    return;
                }
                for (UUID id : active.aliveHiders) {
                    Player survivor = Bukkit.getPlayer(id);
                    if (survivor != null && survivor.isOnline()) active.survivalSeconds.merge(id, 1, Integer::sum);
                }
                for (UUID id : active.players) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendActionBar(ChatColor.LIGHT_PURPLE + "ROUND " + active.round + "/" + ROUNDS
                                + ChatColor.GRAY + " • " + ChatColor.WHITE + active.secondsLeft + "s left"
                                + ChatColor.GRAY + " • Hiders: " + ChatColor.AQUA + active.aliveHiders.size()
                                + ChatColor.GRAY + " • Hunter lives: " + ChatColor.RED + active.hunterLives);
                    }
                }
                updateHunterCompass(active);
                updateAntiCamping(active);
                updateLastHiderMode(active);
                updateAfkTracking(active);
                if (randomEventsEnabled() && active.secondsLeft > 60 && active.secondsLeft % 90 == 0) triggerRandomEvent(active);
                if (active.secondsLeft <= 60 && !active.zoneShrunk && plugin.getConfig().getBoolean("mobhunt.shrinking-zone", true)) {
                    active.zoneShrunk = true;
                    world.getWorldBorder().setSize(24.0, 60L);
                    world.getWorldBorder().setDamageBuffer(0.0);
                    world.getWorldBorder().setDamageAmount(1.0);
                    broadcastToMatch(active, ChatColor.GOLD + "The final zone is shrinking!");
                }
                active.secondsLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
        logMatch("ROUND_START", active, "hunter=" + nameOf(active.hunter) + ", seconds=" + active.secondsLeft + ", lives=" + active.hunterLives);
        broadcastToMatch(active, ChatColor.LIGHT_PURPLE + "Round " + active.round + "/" + ROUNDS
                + " • A random player is the hunter this round.");
    }

    private void resetForRound(Player player) {
        DisguiseAPI.undisguiseToAll(player);
        player.closeInventory();
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        player.setGameMode(GameMode.ADVENTURE);
        player.setFallDistance(0);
        player.setFireTicks(0);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setSaturation(20f);
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
    }

    private void giveHunterBaseKit(Player player) {
        player.getInventory().setItem(0, named(Material.COMPASS, ChatColor.AQUA + "Hider Tracker",
                "Points toward the nearest hider"));
        player.getInventory().setItem(1, new ItemStack(Material.IRON_AXE));
        player.getInventory().setItem(7, new ItemStack(Material.ENDER_PEARL, 3));
        player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 5));
    }

    private void giveHiderKit(Player player) {
        player.getInventory().setItem(0, named(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Mob Ability", "Right-click to view ability and cooldown"));
        player.getInventory().setItem(7, new ItemStack(Material.ENDER_PEARL, 2));
        player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 5));
    }

    private void openHunterTools(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, PICK_TITLE);
        for (int i = 0; i < HUNTER_TOOLS.size(); i++) {
            Material material = HUNTER_TOOLS.get(i);
            inv.setItem(i, named(material, ChatColor.AQUA + pretty(material.name()),
                    "Choose this as one of your 2 tools"));
        }
        inv.setItem(49, named(Material.LIME_DYE, ChatColor.GREEN + "Pick 2 tools",
                "Selected: 0/2", "Click two different items"));
        player.openInventory(inv);
    }

    @EventHandler
    public void onHunterToolClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(PICK_TITLE)) return;
        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        Match active = match;
        if (active == null || !player.getUniqueId().equals(active.hunter)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !HUNTER_TOOLS.contains(clicked.getType())) return;
        Set<Material> selected = active.selectedTools.computeIfAbsent(player.getUniqueId(),
                ignored -> EnumSet.noneOf(Material.class));
        Material choice = clicked.getType();
        if (selected.contains(choice)) {
            player.sendMessage(PREFIX + ChatColor.RED + "Choose two different items.");
            return;
        }
        if (selected.size() >= 2) {
            player.sendMessage(PREFIX + "You have already picked your two tools.");
            return;
        }
        selected.add(choice);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Selected " + pretty(choice.name())
                + " (" + selected.size() + "/2).");
        if (selected.size() == 2) {
            for (Material material : selected) {
                player.getInventory().addItem(new ItemStack(material));
            }
            player.closeInventory();
        } else {
            event.getView().getTopInventory().setItem(49, named(Material.LIME_DYE,
                    ChatColor.GREEN + "Pick 2 tools", "Selected: 1/2", "Choose one more different item"));
        }
    }

    private void applyDisguise(Player player, String mobName) {
        try {
            DisguiseType type = DisguiseType.valueOf(mobName);
            MobDisguise disguise = new MobDisguise(type, true);
            disguise.setScalePlayerToDisguise(true);
            disguise.setModifyBoundingBox(true);
            disguise.setSelfDisguiseVisible(true);
            DisguiseAPI.disguiseToAll(player, disguise);
        } catch (IllegalArgumentException ex) {
            MobDisguise fallback = new MobDisguise(DisguiseType.PIG, true);
            fallback.setScalePlayerToDisguise(true);
            fallback.setModifyBoundingBox(true);
            fallback.setSelfDisguiseVisible(true);
            DisguiseAPI.disguiseToAll(player, fallback);
            plugin.getLogger().warning("Mob Hunt disguise type " + mobName + " is unavailable; using PIG.");
        }
    }

    private String randomEnabledMob() {
        List<String> enabled = MOB_TYPES.stream().filter(this::isMobEnabled).toList();
        return enabled.isEmpty() ? "PIG" : enabled.get(random.nextInt(enabled.size()));
    }

    private void applyMobAbility(Player player, String mob) {
        int duration = configuredRoundSeconds() * 20;
        switch (mob) {
            case "PIG" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
            case "COW" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
            case "SHEEP" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, false, true));
            case "CHICKEN" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0, false, false, true));
            case "WOLF" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 1, false, false, true));
            case "CAT" -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 1, false, false, true));
            case "FOX" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
            case "RABBIT" -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 2, false, false, true));
            case "BAT", "GHAST", "BEE" -> {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed(0.08f);
            }
            case "SPIDER" -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 1, false, false, true));
            case "CAVE_SPIDER" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
            case "ENDERMAN" -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, false, false, true));
            case "CREEPER" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
            case "ZOMBIE" -> player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, duration, 0, false, false, true));
            case "SKELETON" -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, false, false, true));
            case "SLIME", "FROG" -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 2, false, false, true));
            case "MAGMA_CUBE" -> player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, duration, 0, false, false, true));
            case "WARDEN" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
            case "GOAT" -> player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, duration, 1, false, false, true));
            case "PANDA" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, duration, 0, false, false, true));
            case "TURTLE" -> player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0, false, false, true));
            case "AXOLOTL" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, duration, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, false, false, true));
            }
            case "CAMEL" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, duration, 0, false, false, true));
            case "SNIFFER" -> player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, duration, 0, false, false, true));
            default -> { }
        }
    }

    private void assignMob(Match active, Player player, String mob) {
        active.mobPickPending.remove(player.getUniqueId());
        if (!isMobEnabled(mob)) { mob = randomEnabledMob(); }
        active.mobForms.put(player.getUniqueId(), mob);
        applyDisguise(player, mob);
        applyMobAbility(player, mob);
        player.sendTitle(ChatColor.GREEN + "MOB SELECTED", ChatColor.GRAY + pretty(mob), 3, 30, 8);
        plugin.getConfig().set("mobhunt.stats." + player.getUniqueId() + ".mob-uses." + mob,
                plugin.getConfig().getInt("mobhunt.stats." + player.getUniqueId() + ".mob-uses." + mob, 0) + 1);
        plugin.saveConfig();
        player.sendMessage(PREFIX + ChatColor.GREEN + "Your mob form: " + ChatColor.AQUA + pretty(mob));
    }
    private void openMobPicker(Player player, String filter) {
        filter = filter == null ? "" : filter.trim().replace(' ', '_');
        Inventory inv = Bukkit.createInventory(null, 54, MOB_PICK_TITLE);
        int slot = 0;
        for (String mob : MOB_TYPES) {
            if (!isMobEnabled(mob) || (!filter.isBlank() && !mob.toLowerCase().contains(filter.toLowerCase()))) continue;
            Material icon;
            try { icon = Material.valueOf(mob + "_SPAWN_EGG"); } catch (IllegalArgumentException ex) { icon = Material.PAPER; }
            if (slot >= 45) break;
            inv.setItem(slot++, named(icon, ChatColor.GREEN + pretty(mob), abilityName(mob), abilityDescription(mob),
                    "Ability cooldown: " + (configuredAbilityCooldown(mob) / 1000L) + "s"));
        }
        inv.setItem(49, named(Material.COMPASS, ChatColor.AQUA + "Search mobs", "Click, then type a mob name in chat", "Current filter: " + (filter.isBlank() ? "none" : filter)));
        inv.setItem(53, named(Material.BARRIER, ChatColor.RED + "Random mob", "Choose a random mob"));
        player.openInventory(inv);
    }
    @EventHandler
    public void onMobPickerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(MOB_PICK_TITLE)) return;
        event.setCancelled(true);
        Match active = match;
        if (active == null || active.roundEnded || !active.mobPickPending.contains(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot == 49) {
            searchingMob.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.AQUA + "Type a mob name in chat to search (example: warden).");
            return;
        }
        if (slot == 53) {
            assignMob(active, player, randomEnabledMob());
            player.closeInventory();
            return;
        }
        if (slot < 0 || slot >= 45) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        String title = ChatColor.stripColor(item.getItemMeta().getDisplayName());
        String chosen = MOB_TYPES.stream().filter(m -> pretty(m).equalsIgnoreCase(title)).findFirst().orElse(null);
        if (chosen != null) { assignMob(active, player, chosen); player.closeInventory(); }
    }
    @EventHandler
    public void onMobSearchChat(AsyncPlayerChatEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (!searchingMob.contains(id)) return;
        event.setCancelled(true);
        String query = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!searchingMob.remove(id)) return;
            Match active = match;
            Player player = Bukkit.getPlayer(id);
            if (player != null && active != null && active.mobPickPending.contains(id)) {
                String normalized = query.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
                boolean found = MOB_TYPES.stream().anyMatch(m -> isMobEnabled(m) && m.contains(normalized));
                if (found) openMobPicker(player, query);
                else {
                    player.sendMessage(PREFIX + ChatColor.RED + "No matching mob. Try a name such as wolf, bee, or warden.");
                    openMobPicker(player, "");
                }
            }
        });
    }
    private void updateAntiCamping(Match active) {
        Player hunter = Bukkit.getPlayer(active.hunter);
        if (!antiCampingEnabled()) {
            if (hunter != null) {
                for (ItemStack item : hunter.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.BOW && item.hasItemMeta()
                            && ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Anti-Camp Flame Bow")) {
                        hunter.getInventory().remove(item);
                    }
                }
            }
            return;
        }
        boolean anyHiderTooHigh = false;
        for (UUID id : new ArrayList<>(active.aliveHiders)) {
            Player hider = Bukkit.getPlayer(id);
            if (hider == null || !hider.isOnline() || !hider.getWorld().equals(world)) continue;
            if (hider.isFlying() && hider.getLocation().getY() > arenaBaseY + 8) {
                hider.setFlySpeed(0.025f);
                hider.sendActionBar(ChatColor.RED + "High-altitude limit: return to the village to regain flight speed.");
            } else if (hider.isFlying()) hider.setFlySpeed(0.08f);
            if (hider.getLocation().getY() < arenaBaseY - campLowLimit()) {
                Location safe = hider.getLocation().clone();
                safe.setY(arenaBaseY + 1.0);
                hider.teleport(safe);
                hider.sendMessage(PREFIX + ChatColor.RED + "Anti-camping: underground hiding is disabled.");
            } else if (hider.getLocation().getY() > arenaBaseY + campHighLimit()) {
                anyHiderTooHigh = true;
                if (hunter == null) continue;
                ItemStack bow = named(Material.BOW, ChatColor.GOLD + "Anti-Camp Flame Bow", "Power III • Flame", "Granted while a hider stays above the village.");
                bow.addEnchantment(Enchantment.POWER, 3);
                bow.addEnchantment(Enchantment.FLAME, 1);
                boolean has = Arrays.stream(hunter.getInventory().getContents()).filter(i -> i != null)
                    .anyMatch(i -> i.getType() == Material.BOW && i.hasItemMeta() && ChatColor.stripColor(i.getItemMeta().getDisplayName()).contains("Anti-Camp Flame Bow"));
                if (!has) {
                    hunter.getInventory().addItem(bow);
                    hunter.getInventory().addItem(new ItemStack(Material.ARROW, 16));
                }
            }
        }
        if (!anyHiderTooHigh && hunter != null) {
            for (ItemStack item : hunter.getInventory().getContents()) {
                if (item != null && item.getType() == Material.BOW && item.hasItemMeta()
                        && ChatColor.stripColor(item.getItemMeta().getDisplayName()).contains("Anti-Camp Flame Bow")) {
                    hunter.getInventory().remove(item);
                }
            }
        }
    }
    private void updateLastHiderMode(Match active) {
        if (active.aliveHiders.size() != 1 || active.lastHiderAnnounced) return;
        active.lastHiderAnnounced = true;
        UUID id = active.aliveHiders.iterator().next();
        Player last = Bukkit.getPlayer(id);
        broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "LAST HIDER MODE! " + ChatColor.YELLOW + (last == null ? "One hider remains!" : last.getName() + " is the final hider!"));
        if (last != null) {
            last.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, configuredRoundSeconds() * 20, 0, false, false, true));
            last.sendTitle(ChatColor.GOLD + "LAST HIDER", ChatColor.GRAY + "Survive until the timer ends!", 5, 50, 10);
        }
    }

    private String abilityName(String mob) {
        return switch (mob) {
            case "PIG" -> "Snout Sprint"; case "COW" -> "Thick Hide"; case "SHEEP" -> "Woolly Guard";
            case "CHICKEN" -> "Feather Fall"; case "WOLF" -> "Pack Dash"; case "CAT" -> "Silent Pounce";
            case "FOX" -> "Quick Escape"; case "RABBIT" -> "Bunny Leap"; case "BAT" -> "Night Flight";
            case "SPIDER" -> "Web Dash"; case "CAVE_SPIDER" -> "Venom Touch"; case "ENDERMAN" -> "Shadow Step";
            case "CREEPER" -> "Creeper Burst"; case "ZOMBIE" -> "Rotting Aura"; case "SKELETON" -> "Bone Shot";
            case "SLIME" -> "Mega Bounce"; case "MAGMA_CUBE" -> "Magma Bounce"; case "GHAST" -> "Ghast Fireball";
            case "WARDEN" -> "Sonic Pulse"; case "GOAT" -> "Mountain Ram"; case "PANDA" -> "Rolling Knockback";
            case "BEE" -> "Sting"; case "FROG" -> "Tonguebound Hop"; case "TURTLE" -> "Shell Guard";
            case "AXOLOTL" -> "Regeneration"; case "CAMEL" -> "Desert Dash"; case "SNIFFER" -> "Ancient Senses";
            default -> "Mob Strike";
        };
    }

    private String abilityDescription(String mob) {
        return switch (mob) {
            case "PIG" -> "Burst of speed to escape"; case "COW" -> "Brief resistance against a hit";
            case "SHEEP" -> "Short regeneration and a wool particle burst"; case "CHICKEN" -> "Slow falling and a high flutter jump";
            case "WOLF" -> "Fast directional dash"; case "CAT" -> "Quiet jump with a short speed burst";
            case "FOX" -> "Quick sideways escape"; case "RABBIT" -> "High leap to reach rooftops";
            case "BAT" -> "Brief controlled flight"; case "SPIDER" -> "Fast web-style dash";
            case "CAVE_SPIDER" -> "Slow the hunter at close range"; case "ENDERMAN" -> "Blink to a nearby safe location";
            case "CREEPER" -> "A harmless pulse that pushes the hunter back"; case "ZOMBIE" -> "Disorient the nearby hunter";
            case "SKELETON" -> "Fire a low-damage bone arrow"; case "SLIME" -> "Powerful bounce";
            case "MAGMA_CUBE" -> "Bounce with brief fire resistance"; case "GHAST" -> "Launch a small non-incendiary fireball";
            case "WARDEN" -> "Sonic pulse that pushes nearby hunter"; case "GOAT" -> "Charge forward and knock back";
            case "PANDA" -> "Short rolling push"; case "BEE" -> "Sting to briefly slow the hunter";
            case "FROG" -> "Long jump with a speed burst"; case "TURTLE" -> "Brief defensive resistance";
            case "AXOLOTL" -> "Restore a little health over time"; case "CAMEL" -> "Fast straight-line dash";
            case "SNIFFER" -> "Sense nearby danger with a short warning pulse";
            default -> "A short mobility boost";
        };
    }
    private long abilityCooldown(String mob) {
        return switch (mob) { case "WARDEN", "GHAST", "CREEPER" -> 15000L; case "BEE", "SPIDER", "CAVE_SPIDER" -> 10000L; default -> 8000L; };
    }
    private void openAbilityGui(Player player) {
        Match active = match;
        if (active == null || !active.aliveHiders.contains(player.getUniqueId())) return;
        String mob = active.mobForms.getOrDefault(player.getUniqueId(), "PIG");
        long remain = Math.max(0L, active.abilityReadyAt.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        Inventory inv = Bukkit.createInventory(null, 27, ABILITY_TITLE);
        inv.setItem(11, named(Material.NETHER_STAR, ChatColor.GREEN + "Use " + abilityName(mob), "Mob: " + pretty(mob),
                abilityDescription(mob), remain == 0 ? "Ready now!" : "Cooldown: " + ((remain + 999) / 1000L) + "s"));
        inv.setItem(15, named(Material.CLOCK, ChatColor.AQUA + "Cooldown Status", remain == 0 ? "Ability ready" : "Available in " + (remain / 1000.0) + " seconds"));
        player.openInventory(inv);
    }
    private void useMobAbility(Player player, Match active) {
        UUID id = player.getUniqueId();
        String mob = active.mobForms.getOrDefault(id, "PIG");
        long now = System.currentTimeMillis(), readyAt = active.abilityReadyAt.getOrDefault(id, 0L);
        if (now < readyAt) { player.sendMessage(PREFIX + ChatColor.RED + "Ability cooldown: " + ((readyAt - now + 999) / 1000) + "s."); openAbilityGui(player); return; }
        active.abilityReadyAt.put(id, now + configuredAbilityCooldown(mob));
        Player hunter = Bukkit.getPlayer(active.hunter);
        if (!isMobEnabled(mob)) { player.sendMessage(PREFIX + ChatColor.RED + "That mob's ability is disabled by an admin."); return; }
        boolean hunterNearby = hunter != null && hunter.isOnline() && hunter.getWorld().equals(world)
                && hunter.getLocation().distanceSquared(player.getLocation()) <= 100;
        switch (mob) {
            case "PIG" -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
            case "COW" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 1));
            case "SHEEP" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 80, 1));
            case "CHICKEN" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0)); player.setVelocity(player.getVelocity().setY(0.75)); }
            case "WOLF" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2)); player.setVelocity(player.getLocation().getDirection().multiply(1.0).setY(0.25)); }
            case "CAT" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 2)); player.setVelocity(player.getLocation().getDirection().multiply(0.7).setY(0.65)); }
            case "FOX" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2)); player.setVelocity(player.getLocation().getDirection().multiply(-0.7).setY(0.45)); }
            case "RABBIT" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 3)); player.setVelocity(player.getLocation().getDirection().multiply(0.5).setY(1.0)); }
            case "BAT" -> {
                player.setAllowFlight(true); player.setFlying(true); player.setFlySpeed(0.08f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (player.isOnline() && !player.getUniqueId().equals(active.hunter)) player.setFlying(false); }, 80L);
            }
            case "SPIDER" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2)); player.setVelocity(player.getLocation().getDirection().multiply(1.0).setY(0.4)); }
            case "CAVE_SPIDER" -> { if (hunterNearby) hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1)); player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1)); }
            case "ENDERMAN" -> {
                Location from = player.getLocation();
                Location to = from.clone().add(from.getDirection().normalize().multiply(7));
                int y = world.getHighestBlockYAt(to.getBlockX(), to.getBlockZ()) + 1;
                to.setY(y); if (y < arenaBaseY + 16) player.teleport(to);
                player.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 35, 0.5, 0.8, 0.5, 0.2);
            }
            case "CREEPER" -> {
                player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation(), 1);
                if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 25) {
                    hunter.setVelocity(hunter.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.0).setY(0.4));
                }
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_CREEPER_PRIMED, 0.8f, 1.4f);
            }
            case "ZOMBIE" -> { if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 36) hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0)); }
            case "SKELETON" -> {
                Arrow arrow = player.launchProjectile(Arrow.class);
                arrow.setDamage(2.0);
            }
            case "SLIME" -> { player.setVelocity(player.getLocation().getDirection().multiply(0.6).setY(0.95)); player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 24, 0.6, 0.2, 0.6, 0.1); }
            case "MAGMA_CUBE" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 100, 0)); player.setVelocity(player.getLocation().getDirection().multiply(0.6).setY(0.85)); }
            case "GHAST" -> { Fireball fireball = player.launchProjectile(Fireball.class); fireball.setIsIncendiary(false); fireball.setYield(0f); }
            case "WARDEN" -> {
                if (hunterNearby) { hunter.setVelocity(hunter.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.1).setY(0.4)); hunter.damage(2.0, player); }
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                player.getWorld().spawnParticle(Particle.SONIC_BOOM, player.getLocation(), 1);
            }
            case "GOAT" -> { player.setVelocity(player.getLocation().getDirection().multiply(1.5).setY(0.25)); if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 16) hunter.setVelocity(hunter.getLocation().getDirection().multiply(-0.8).setY(0.3)); }
            case "PANDA" -> { if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 25) hunter.setVelocity(hunter.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.0).setY(0.3)); player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 0)); }
            case "BEE" -> {
                if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 16) hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1));
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 12, 0.4, 0.4, 0.4, 0.05);
            }
            case "FROG" -> { player.setVelocity(player.getLocation().getDirection().multiply(0.9).setY(0.9)); player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_FROG_TONGUE, 0.8f, 1.3f); }
            case "TURTLE" -> player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 2));
            case "AXOLOTL" -> player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1));
            case "CAMEL" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 120, 1)); player.setVelocity(player.getLocation().getDirection().multiply(1.4).setY(0.15)); }
            case "SNIFFER" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 160, 0));
                player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation(), 18, 0.8, 0.5, 0.8, 0.05);
                if (hunterNearby && hunter.getLocation().distanceSquared(player.getLocation()) <= 64) player.sendMessage(PREFIX + ChatColor.AQUA + "You sense the hunter nearby!");
            }
            default -> player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + abilityName(mob) + " used!");
        logMatch("ABILITY", active, "player=" + player.getName() + ", mob=" + mob + ", ability=" + abilityName(mob));
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.5f);
    }
    @EventHandler
    public void onAbilityItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || !ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Mob Ability")) return;
        Match active = match;
        if (active == null || !active.aliveHiders.contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true); openAbilityGui(event.getPlayer());
    }
    @EventHandler
    public void onAbilityGuiClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(ABILITY_TITLE)) return;
        event.setCancelled(true);
        if (event.getRawSlot() != 11) return;
        Match active = match;
        if (active == null || !active.aliveHiders.contains(player.getUniqueId())) return;
        player.closeInventory(); useMobAbility(player, active);
    }

    private void updateHunterCompass(Match active) {
        Player hunter = Bukkit.getPlayer(active.hunter);
        if (hunter == null || !hunter.isOnline() || !hunter.getWorld().equals(world)) return;
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (UUID id : active.aliveHiders) {
            Player hider = Bukkit.getPlayer(id);
            if (hider == null || !hider.isOnline() || !hider.getWorld().equals(world)) continue;
            if (hider.isSneaking()) continue;
            double d = hunter.getLocation().distanceSquared(hider.getLocation());
            if (d < distance) { distance = d; nearest = hider; }
        }
        if (nearest != null) hunter.setCompassTarget(nearest.getLocation());
        else hunter.sendActionBar(ChatColor.AQUA + "Hiders are moving quietly or out of tracking range...");
    }

    private void endRound(Match active, boolean hunterWon) {
        if (match != active || active.roundEnded || active.ending) return;
        active.roundEnded = true;
        logMatch("ROUND_END", active, "hunterWon=" + hunterWon + ", survivors=" + active.aliveHiders);
        active.cancelRoundTasks();
        if (hunterWon) {
            active.hunterRoundWins.merge(active.hunter, 1, Integer::sum);
            active.scores.merge(active.hunter, 1, Integer::sum);
            broadcastToMatch(active, ChatColor.GOLD + nameOf(active.hunter) + " eliminated every hider and wins the round!");
        } else {
            for (UUID id : active.aliveHiders) {
                active.hiderRoundWins.merge(id, 1, Integer::sum);
                active.scores.merge(id, 1, Integer::sum);
            }
            broadcastToMatch(active, ChatColor.GREEN + "Time is up! Every hider who stayed hidden earns a point.");
        }
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                DisguiseAPI.undisguiseToAll(player);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.closeInventory();
                player.sendMessage(PREFIX + ChatColor.AQUA + "Scores: " + scoreLine(active));
            }
        }
        if (active.round >= ROUNDS) {
            finishMatch(active);
        } else {
            broadcastToMatch(active, ChatColor.LIGHT_PURPLE + "Next round begins in 5 seconds...");
            Bukkit.getScheduler().runTaskLater(plugin, () -> startRound(active), 100L);
        }
    }

    private void finishMatch(Match active) {
        if (active.ending) return;
        active.ending = true;
        active.cancelRoundTasks();
        int high = active.scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<UUID> winners = active.scores.entrySet().stream()
                .filter(entry -> entry.getValue() == high).map(Map.Entry::getKey).toList();
        String winnerNames = winners.stream().map(this::nameOf).collect(Collectors.joining(", "));
        plugin.getConfig().set("mobhunt.matches-completed", plugin.getConfig().getInt("mobhunt.matches-completed", 0) + 1);
        plugin.getConfig().set("mobhunt.total-rounds", plugin.getConfig().getInt("mobhunt.total-rounds", 0) + active.round);
        for (UUID id : active.players) {
            String path = "mobhunt.stats." + id;
            int points = active.scores.getOrDefault(id, 0);
            plugin.getConfig().set(path + ".matches", plugin.getConfig().getInt(path + ".matches", 0) + 1);
            plugin.getConfig().set(path + ".points", plugin.getConfig().getInt(path + ".points", 0) + points);
            plugin.getConfig().set(path + ".best-points", Math.max(points, plugin.getConfig().getInt(path + ".best-points", 0)));
            plugin.getConfig().set(path + ".kills", plugin.getConfig().getInt(path + ".kills", 0) + active.kills.getOrDefault(id, 0));
            int survival = active.survivalSeconds.getOrDefault(id, 0);
            plugin.getConfig().set(path + ".survival-seconds", plugin.getConfig().getInt(path + ".survival-seconds", 0) + survival);
            plugin.getConfig().set(path + ".best-survival-seconds", Math.max(survival,
                    plugin.getConfig().getInt(path + ".best-survival-seconds", 0)));
            if (winners.contains(id)) plugin.getConfig().set(path + ".wins", plugin.getConfig().getInt(path + ".wins", 0) + 1);
            plugin.getConfig().set(path + ".hunter-round-wins",
                    plugin.getConfig().getInt(path + ".hunter-round-wins", 0) + active.hunterRoundWins.getOrDefault(id, 0));
            plugin.getConfig().set(path + ".hider-round-wins",
                    plugin.getConfig().getInt(path + ".hider-round-wins", 0) + active.hiderRoundWins.getOrDefault(id, 0));
            ConfigurationSection mobUses = plugin.getConfig().getConfigurationSection(path + ".mob-uses");
            if (mobUses != null && !mobUses.getKeys(false).isEmpty()) {
                String favorite = mobUses.getKeys(false).stream().max(Comparator.comparingInt(mobUses::getInt)).orElse("N/A");
                plugin.getConfig().set(path + ".favorite-mob", pretty(favorite));
            }
            Player online = Bukkit.getPlayer(id);
            if (online != null && online.isOnline()) {
                PlayerSnapshot snapshot = active.snapshots.get(id);
                if (snapshot != null) snapshot.restoreAtLobby(online, lobby); else online.teleport(lobby);
                clearRecovery(id);
                online.sendTitle(ChatColor.GOLD + "MOB HUNT OVER",
                        ChatColor.GRAY + "Winner: " + (winnerNames.isBlank() ? "No winner" : winnerNames), 5, 70, 15);
            }
        }
        saveMatchHistory(active, winnerNames);
        for (BukkitTask task : active.disconnectTasks.values()) task.cancel();
        active.disconnectTasks.clear();
        plugin.saveConfig();
        broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "MOB HUNT WINNER: "
                + ChatColor.YELLOW + (winnerNames.isBlank() ? "No winner" : winnerNames)
                + ChatColor.GRAY + " • " + high + " point(s)");
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            DisguiseAPI.undisguiseToAll(player);
        }
        logMatch("END", active, "winners=" + winnerNames + ", scores=" + scoreLine(active));
        buildVillage();
        active.ending = true;
        match = null;
    }

    private void abortMatch(Match active, String reason) {
        if (active == null || active.ending) return;
        broadcastToMatch(active, ChatColor.RED + reason);
        finishMatch(active);
    }

    private void broadcastToMatch(Match active, String message) {
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) player.sendMessage(PREFIX + message);
        }
    }

    private String scoreLine(Match active) {
        return active.players.stream().map(id -> nameOf(id) + ": " + active.scores.getOrDefault(id, 0))
                .collect(Collectors.joining("  |  "));
    }

    private String nameOf(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "Disconnected player" : player.getName();
    }

    private Location randomVillageSpawn() {
        int[][] spots = "castle".equals(currentMap)
                ? new int[][]{{-28,-28},{28,-28},{-28,28},{28,28},{0,-30},{30,0},{0,30},{-30,0},{-14,0},{14,0},{0,-14},{0,14}}
                : new int[][]{{-10,-28},{10,28},{-28,10},{28,-10},{0,-30},{24,0},{0,30},{-24,0},{-12,0},{12,0},{0,-12},{0,12}};
        int[] spot = spots[random.nextInt(spots.length)];
        return new Location(world, spot[0] + 0.5, arenaBaseY + 1.0, spot[1] + 0.5);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(MENU_TITLE)) return;
        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot == 11) {
            player.closeInventory();
            joinQueue(player);
        } else if (slot == 15) {
            player.closeInventory();
            leaveQueue(player);
        }
    }

    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) return;
        String id = villager.getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (!"MOB_HUNT".equals(id)) return;
        event.setCancelled(true);
        openMenu(event.getPlayer());
    }

    @EventHandler
    public void onHunterFreeze(PlayerMoveEvent event) {
        Match active = match;
        if (active == null || active.ending || !active.hunterLocked
                || !event.getPlayer().getUniqueId().equals(active.hunter)) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            event.setTo(new Location(from.getWorld(), from.getX(), from.getY(), from.getZ(), to.getYaw(), to.getPitch()));
        }
    }

    @EventHandler
    public void onNoDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Match active = match;
        if (active == null || !active.players.contains(victim.getUniqueId())) return;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Player attacker = null;
            if (byEntity.getDamager() instanceof Player p) attacker = p;
            else if (byEntity.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player p) attacker = p;
            if (attacker != null && active.players.contains(attacker.getUniqueId())
                    && (active.aliveHiders.contains(attacker.getUniqueId()) || attacker.getUniqueId().equals(active.hunter))
                    && (active.aliveHiders.contains(victim.getUniqueId()) || victim.getUniqueId().equals(active.hunter))
                    && (attacker.getUniqueId().equals(active.hunter) != victim.getUniqueId().equals(active.hunter))) {
                if (attacker.getUniqueId().equals(victim.getUniqueId())) event.setCancelled(true);
                return;
            }
        }
        event.setCancelled(true);
    }
    @EventHandler
    public void onMobHuntDeath(PlayerDeathEvent event) {
        Match active = match;
        Player player = event.getEntity();
        if (active == null || !active.players.contains(player.getUniqueId()) || active.roundEnded) return;
        event.setKeepInventory(true);
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setDeathMessage(null);
        Player killer = player.getKiller();
        String cause = "";
        EntityDamageEvent lastDamage = player.getLastDamageCause();
        if (lastDamage instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile) cause = " with " + pretty(projectile.getType().name());
            else if (damager instanceof Player attacker) {
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                cause = " with " + (weapon.getType().isAir() ? "fists" : pretty(weapon.getType().name()));
            }
        }
        broadcastToMatch(active, ChatColor.RED + (killer == null ? player.getName() : killer.getName()) + ChatColor.GRAY + " eliminated " + ChatColor.YELLOW + player.getName() + ChatColor.GRAY + cause + "!");
        UUID id = player.getUniqueId();
        if (killer != null && !killer.getUniqueId().equals(id) && active.players.contains(killer.getUniqueId())) {
            UUID killerId = killer.getUniqueId();
            active.kills.merge(killerId, 1, Integer::sum);
            if (killerId.equals(active.hunter) && !id.equals(active.hunter)) {
                active.hunterKillStreak++;
                if (Set.of(2, 3, 5, 7).contains(active.hunterKillStreak)) {
                    broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "HUNTER STREAK: "
                            + ChatColor.YELLOW + active.hunterKillStreak + " eliminations!");
                }
            } else if (id.equals(active.hunter)) active.hunterKillStreak = 0;
        }
        logMatch("ELIMINATION", active, "victim=" + player.getName() + ", killer=" + (killer == null ? "environment" : killer.getName()) + cause);
        if (id.equals(active.hunter)) {
            active.hunterLives--;
            if (active.hunterLives <= 0) endRound(active, false);
            else broadcastToMatch(active, ChatColor.GOLD + "Hunter has " + active.hunterLives + " lives remaining.");
        } else if (active.aliveHiders.remove(id)) {
            active.tagged.add(id);
            DisguiseAPI.undisguiseToAll(player);
            updateLastHiderMode(active);
            if (active.aliveHiders.isEmpty()) endRound(active, true);
        }
    }
    @EventHandler
    public void onMobHuntRespawn(PlayerRespawnEvent event) {
        Match active = match;
        Player player = event.getPlayer();
        if (active == null || !active.players.contains(player.getUniqueId())) return;
        event.setRespawnLocation(new Location(world, 0.5, arenaBaseY + 1.0, 0.5));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (match != active || active.roundEnded || active.ending) return;
            if (player.getUniqueId().equals(active.hunter)) {
                player.setGameMode(GameMode.ADVENTURE);
                player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
                giveHunterBaseKit(player);
                player.sendTitle(ChatColor.RED + "HUNTER RESPAWN", ChatColor.GRAY + "" + active.hunterLives + " lives left", 5, 35, 10);
            } else {
                player.setGameMode(GameMode.SPECTATOR);
                player.sendTitle(ChatColor.GRAY + "SPECTATING", ChatColor.DARK_GRAY + "You are out this round.", 5, 35, 10);
            }
        });
    }

    @EventHandler
    public void onArenaBuildProtection(BlockBreakEvent event) {
        if (isMatchPlayer(event.getPlayer()) && event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler
    public void onArenaPlaceProtection(BlockPlaceEvent event) {
        if (isMatchPlayer(event.getPlayer()) && event.getBlock().getWorld().equals(world)) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isMatchPlayer(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onTeleportOut(PlayerTeleportEvent event) {
        Match active = match;
        if (active == null || active.ending || !active.players.contains(event.getPlayer().getUniqueId())) return;
        if (event.getFrom().getWorld().equals(world) && event.getTo() != null
                && !event.getTo().getWorld().equals(world)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + ChatColor.RED + "Finish the Mob Hunt match before leaving.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        queue.remove(id);
        searchingMob.remove(id);
        Match active = match;
        if (active == null || !active.players.contains(id) || active.ending) return;
        active.disconnected.add(id);
        logMatch("DISCONNECT", active, "player=" + event.getPlayer().getName() + ", grace=60s");
        BukkitTask old = active.disconnectTasks.remove(id);
        if (old != null) old.cancel();
        BukkitTask grace = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match != active || active.ending || !active.disconnected.remove(id)) return;
            active.forfeited.add(id);
            active.disconnectTasks.remove(id);
            active.aliveHiders.remove(id);
            logMatch("FORFEIT", active, "reconnect grace expired for " + id);
            if (id.equals(active.hunter)) endRound(active, false);
            else if (active.aliveHiders.isEmpty()) endRound(active, true);
        }, 1200L);
        active.disconnectTasks.put(id, grace);
    }

    private boolean isMatchPlayer(Player player) {
        return match != null && match.players.contains(player.getUniqueId());
    }

    private ItemStack named(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(Arrays.stream(lore).map(s -> ChatColor.GRAY + s).toList());
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(String name) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void spawnLobbyNpc() {
        for (Entity entity : lobby.getWorld().getEntities()) {
            if (entity.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) entity.remove();
        }
        Location spot = lobby.clone().add(8, 0, 0);
        Villager npc = (Villager) lobby.getWorld().spawnEntity(spot, EntityType.VILLAGER);
        npc.setAI(false);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setCollidable(false);
        npc.setPersistent(true);
        npc.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Mob Hunt");
        npc.setCustomNameVisible(true);
        npc.setProfession(Villager.Profession.LIBRARIAN);
        npc.setVillagerLevel(5);
        npc.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, "MOB_HUNT");
    }

    private void buildVillage() {
        currentMap = "village";
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        int base = 80;
        arenaBaseY = base;
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++) {
                world.getBlockAt(x, base, z).setType(Material.GRASS_BLOCK, false);
                world.getBlockAt(x, base - 1, z).setType(Material.DIRT, false);
                world.getBlockAt(x, base - 2, z).setType(Material.STONE, false);
                for (int y = base + 1; y <= base + 16; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) {
                    world.getBlockAt(x, base, z).setType(Material.DIRT_PATH, false);
                }
            }
        }
        buildHouse(-22, -22, base);
        buildHouse(22, -22, base);
        buildHouse(-22, 22, base);
        buildHouse(22, 22, base);
        buildHouse(-32, 0, base);
        buildHouse(32, 0, base);
        buildWell(base);
        buildMarket(base);
        world.setSpawnLocation(0, base + 1, 0);
        WorldBorder border = world.getWorldBorder();
        border.setCenter(0.5, 0.5);
        border.setSize(92.0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(0.0);
        border.setWarningDistance(5);
    }

    private void buildCastle() {
        buildVillage();
        currentMap = "castle";
        int base = arenaBaseY;
        // Clear village buildings and lay a ruined-stone courtyard before building the castle.
        for (int x = -42; x <= 42; x++) for (int z = -42; z <= 42; z++) {
            world.getBlockAt(x, base, z).setType((Math.abs(x) > 39 || Math.abs(z) > 39)
                    ? Material.MOSSY_STONE_BRICKS : Material.STONE_BRICKS, false);
            for (int y = base + 1; y <= base + 16; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
        }
        for (int x=-42;x<=42;x++) for (int z=-42;z<=42;z++) {
            if (Math.abs(x)==34 || Math.abs(z)==34) for (int y=base+1;y<=base+7;y++)
                if ((x+z+y)%11!=0) world.getBlockAt(x,y,z).setType(Material.STONE_BRICKS,false);
        }
        for (int[] p : new int[][]{{-34,-34},{34,-34},{-34,34},{34,34}}) {
            for (int y=base+1;y<=base+13;y++) for (int dx=-3;dx<=3;dx++) for (int dz=-3;dz<=3;dz++)
                if (Math.abs(dx)==3 || Math.abs(dz)==3) world.getBlockAt(p[0]+dx,y,p[1]+dz).setType(y%4==0?Material.CRACKED_STONE_BRICKS:Material.STONE_BRICKS,false);
            for (int dx=-4;dx<=4;dx++) for (int dz=-4;dz<=4;dz++) if (Math.abs(dx)==4 || Math.abs(dz)==4)
                world.getBlockAt(p[0]+dx,base+14,p[1]+dz).setType(Material.MOSSY_STONE_BRICKS,false);
        }
        for (int x=-10;x<=10;x++) for (int z=-10;z<=10;z++) if (Math.abs(x)==10 || Math.abs(z)==10)
            for (int y=base+1;y<=base+8;y++) if (!(Math.abs(x)==10 && z==0 && y<=base+3)) world.getBlockAt(x,y,z).setType(Material.DEEPSLATE_BRICKS,false);
        for (int x=-3;x<=3;x++) for (int z=-3;z<=3;z++) world.getBlockAt(x,base,z).setType(Material.POLISHED_DEEPSLATE,false);
        world.getBlockAt(0,base+1,0).setType(Material.CRYING_OBSIDIAN,false);
        world.getWorldBorder().setCenter(0.5,0.5); world.getWorldBorder().setSize(84.0); world.setSpawnLocation(0,base+1,0);
    }

    private void buildHouse(int cx, int cz, int base) {
        int half = 4;
        for (int x = cx - half; x <= cx + half; x++) {
            for (int z = cz - half; z <= cz + half; z++) {
                world.getBlockAt(x, base, z).setType(Material.OAK_PLANKS, false);
                for (int y = base + 1; y <= base + 5; y++) {
                    boolean wall = x == cx - half || x == cx + half || z == cz - half || z == cz + half;
                    if (wall) world.getBlockAt(x, y, z).setType(Material.OAK_PLANKS, false);
                    else world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
                world.getBlockAt(x, base + 6, z).setType(Material.DARK_OAK_PLANKS, false);
            }
        }
        for (int y = base + 1; y <= base + 2; y++) {
            world.getBlockAt(cx - half, y, cz).setType(Material.GLASS_PANE, false);
            world.getBlockAt(cx + half, y, cz).setType(Material.GLASS_PANE, false);
            world.getBlockAt(cx, y, cz - half).setType(Material.GLASS_PANE, false);
            world.getBlockAt(cx, y, cz + half).setType(Material.GLASS_PANE, false);
        }
        world.getBlockAt(cx, base + 1, cz - half).setType(Material.AIR, false);
        world.getBlockAt(cx, base + 2, cz - half).setType(Material.AIR, false);
        world.getBlockAt(cx - 1, base + 1, cz - half).setType(Material.OAK_DOOR, false);
        world.getBlockAt(cx + 1, base + 1, cz - half).setType(Material.OAK_DOOR, false);
        world.getBlockAt(cx, base + 3, cz).setType(Material.LANTERN, false);
        world.getBlockAt(cx - 3, base + 7, cz - 3).setType(Material.OAK_FENCE, false);
        world.getBlockAt(cx + 3, base + 7, cz + 3).setType(Material.OAK_FENCE, false);
    }

    private void buildWell(int base) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    world.getBlockAt(x, base + 1, z).setType(Material.COBBLESTONE_WALL, false);
                    world.getBlockAt(x, base + 2, z).setType(Material.OAK_FENCE, false);
                } else {
                    world.getBlockAt(x, base + 1, z).setType(Material.WATER, false);
                }
            }
        }
        world.getBlockAt(0, base + 2, 0).setType(Material.WATER, false);
        world.getBlockAt(0, base + 3, 0).setType(Material.LANTERN, false);
        world.getBlockAt(-3, base + 3, 0).setType(Material.OAK_FENCE, false);
        world.getBlockAt(3, base + 3, 0).setType(Material.OAK_FENCE, false);
        world.getBlockAt(0, base + 3, -3).setType(Material.OAK_FENCE, false);
        world.getBlockAt(0, base + 3, 3).setType(Material.OAK_FENCE, false);
    }

    private void buildMarket(int base) {
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (Math.abs(x) == 7 || Math.abs(z) == 7) world.getBlockAt(x, base + 1, z).setType(Material.OAK_FENCE, false);
            }
        }
        for (int x = -6; x <= 6; x++) world.getBlockAt(x, base + 4, -7).setType(Material.RED_WOOL, false);
        for (int x = -6; x <= 6; x++) world.getBlockAt(x, base + 4, 7).setType(Material.WHITE_WOOL, false);
        for (int x = -5; x <= 5; x++) {
            world.getBlockAt(x, base + 3, -7).setType(Material.OAK_FENCE, false);
            world.getBlockAt(x, base + 3, 7).setType(Material.OAK_FENCE, false);
        }
    }

    private void triggerRandomEvent(Match active) {
        active.randomEventCount++;
        int event = random.nextInt(4);
        switch (event) {
            case 0 -> {
                for (UUID id : active.aliveHiders) {
                    Player hider = Bukkit.getPlayer(id);
                    if (hider != null) hider.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, false, false, true));
                }
                broadcastToMatch(active, ChatColor.AQUA + "Random Event: " + ChatColor.WHITE + "Hider Sprint! Hiders gain a short speed boost.");
            }
            case 1 -> {
                Player hunter = Bukkit.getPlayer(active.hunter);
                if (hunter != null) hunter.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
                broadcastToMatch(active, ChatColor.LIGHT_PURPLE + "Random Event: " + ChatColor.WHITE + "Village Fog! The hunter briefly loses vision.");
            }
            case 2 -> {
                for (UUID id : active.aliveHiders) {
                    Player hider = Bukkit.getPlayer(id);
                    if (hider != null) hider.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 1, false, false, true));
                }
                broadcastToMatch(active, ChatColor.GREEN + "Random Event: " + ChatColor.WHITE + "Spring Hop! Hiders gain a short jump boost.");
            }
            default -> {
                for (UUID id : active.aliveHiders) {
                    Player hider = Bukkit.getPlayer(id);
                    if (hider != null) hider.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 160, 0, false, false, true));
                }
                broadcastToMatch(active, ChatColor.GOLD + "Random Event: " + ChatColor.WHITE + "Moonlight! Hiders gain temporary night vision.");
            }
        }
        logMatch("RANDOM_EVENT", active, "event=" + event);
    }

    private void updateAfkTracking(Match active) {
        for (UUID id : active.players) {
            Player p=Bukkit.getPlayer(id); if (p==null || !p.isOnline()) continue;
            Location old=active.lastLocations.get(id), now=p.getLocation();
            if (old==null || old.getWorld()!=now.getWorld() || old.distanceSquared(now)>0.04) {
                active.lastLocations.put(id,now.clone()); active.afkTicks.put(id,0); active.afkWarned.remove(id);
            } else if (active.afkTicks.merge(id,20,Integer::sum)>=1200 && active.afkWarned.add(id)) {
                p.sendMessage(PREFIX+ChatColor.RED+"AFK warning: move to stay active in this match.");
                p.sendTitle(ChatColor.RED+"AFK WARNING",ChatColor.GRAY+"Move to remain active",0,35,5);
            }
        }
    }

    private void openAdminMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, 54, ADMIN_TITLE);
        List<String> disabled = plugin.getConfig().getStringList("mobhunt.disabled-mobs");
        for (int i = 0; i < MOB_TYPES.size(); i++) {
            String mob = MOB_TYPES.get(i);
            boolean enabled = !disabled.contains(mob);
            Material icon;
            try { icon = Material.valueOf(mob + "_SPAWN_EGG"); } catch (IllegalArgumentException ex) { icon = Material.PAPER; }
            inv.setItem(i, named(enabled ? icon : Material.BARRIER,
                    (enabled ? ChatColor.GREEN : ChatColor.RED) + pretty(mob),
                    "Mob enabled: " + enabled, "Left-click: toggle mob",
                    "Right-click: change ability cooldown (" + plugin.getConfig().getInt("mobhunt.ability-cooldowns." + mob, (int)(abilityCooldown(mob)/1000L)) + "s)"));
        }
        boolean maps = plugin.getConfig().getBoolean("mobhunt.random-map", true);
        boolean zone = plugin.getConfig().getBoolean("mobhunt.shrinking-zone", true);
        boolean camping = antiCampingEnabled();
        boolean events = randomEventsEnabled();
        int seconds = configuredRoundSeconds(), lives = configuredHunterLives();
        int low = campLowLimit(), high = campHighLimit(), pick = configuredMobPickSeconds();
        inv.setItem(43, named(Material.CLOCK, ChatColor.AQUA + "Mob Pick Timer", "Current: " + pick + " seconds", "Click to cycle 10 / 15 / 20 / 30 / 45"));
        inv.setItem(44, named(Material.BOOK, ChatColor.LIGHT_PURPLE + "Mob Ability Settings", "Left-click a mob to enable/disable", "Right-click a mob to change its cooldown"));
        inv.setItem(45, named(maps ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.AQUA + "Random Map Rotation", "Status: " + (maps ? "ON" : "OFF")));
        inv.setItem(46, named(camping ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.AQUA + "Anti-Camping", "Status: " + (camping ? "ON" : "OFF")));
        inv.setItem(47, named(zone ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.AQUA + "Final Shrinking Zone", "Status: " + (zone ? "ON" : "OFF")));
        inv.setItem(48, named(Material.FEATHER, ChatColor.GOLD + "High-Altitude Limit", "Current: +" + high + " blocks", "Click to increase by 2 blocks"));
        inv.setItem(49, named(Material.CLOCK, ChatColor.LIGHT_PURPLE + "Round Duration", "Current: " + seconds + " seconds", "Click to cycle 180 / 300 / 420 / 600"));
        inv.setItem(50, named(Material.STONE, ChatColor.GOLD + "Underground Limit", "Current: -" + low + " blocks", "Click to increase by 2 blocks"));
        inv.setItem(51, named(Material.IRON_AXE, ChatColor.LIGHT_PURPLE + "Hunter Lives", "Current: " + lives, "Click to cycle 1–5"));
        inv.setItem(52, named(events ? Material.LIME_DYE : Material.GRAY_DYE, ChatColor.AQUA + "Random Events", "Status: " + (events ? "ON" : "OFF"), "Optional timed round events"));
        inv.setItem(53, named(Material.PAPER, ChatColor.GOLD + "Mob Hunt Statistics",
                "Completed: " + plugin.getConfig().getInt("mobhunt.matches-completed", 0),
                "Rounds: " + plugin.getConfig().getInt("mobhunt.total-rounds", 0), "Use /mobhunt leaderboard"));
        p.openInventory(inv);
    }

    @EventHandler public void onAdminMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p) || !e.getView().getTitle().equals(ADMIN_TITLE)) return;
        e.setCancelled(true);
        if (!p.hasPermission("nebulacore.admin")) return;
        int slot = e.getRawSlot();
        if (slot >= 0 && slot < MOB_TYPES.size()) {
            String mob = MOB_TYPES.get(slot);
            if (e.isRightClick()) {
                int old = plugin.getConfig().getInt("mobhunt.ability-cooldowns." + mob, (int)(abilityCooldown(mob)/1000L));
                plugin.getConfig().set("mobhunt.ability-cooldowns." + mob, old >= 60 ? 5 : old + 5);
            } else {
                List<String> disabled = new ArrayList<>(plugin.getConfig().getStringList("mobhunt.disabled-mobs"));
                if (disabled.contains(mob)) disabled.remove(mob); else disabled.add(mob);
                if (disabled.size() >= MOB_TYPES.size()) { p.sendMessage(PREFIX + ChatColor.RED + "At least one mob must remain enabled."); return; }
                plugin.getConfig().set("mobhunt.disabled-mobs", disabled);
            }
        } else if (slot == 43) {
            int[] choices = {10, 15, 20, 30, 45};
            int current = configuredMobPickSeconds(), next = choices[0];
            for (int i = 0; i < choices.length; i++) if (choices[i] == current) next = choices[(i + 1) % choices.length];
            plugin.getConfig().set("mobhunt.mob-pick-seconds", next);
        } else if (slot == 45) {
            plugin.getConfig().set("mobhunt.random-map", !plugin.getConfig().getBoolean("mobhunt.random-map", true));
        } else if (slot == 46) {
            plugin.getConfig().set("mobhunt.anti-camping", !antiCampingEnabled());
        } else if (slot == 47) {
            plugin.getConfig().set("mobhunt.shrinking-zone", !plugin.getConfig().getBoolean("mobhunt.shrinking-zone", true));
        } else if (slot == 48) {
            int next = campHighLimit() >= 24 ? 8 : campHighLimit() + 2;
            plugin.getConfig().set("mobhunt.camp-high-limit", next);
        } else if (slot == 49) {
            int[] choices = {180, 300, 420, 600};
            int current = configuredRoundSeconds(), next = choices[0];
            for (int i = 0; i < choices.length; i++) if (choices[i] == current) next = choices[(i + 1) % choices.length];
            plugin.getConfig().set("mobhunt.round-seconds", next);
        } else if (slot == 50) {
            int next = campLowLimit() >= 12 ? 3 : campLowLimit() + 2;
            plugin.getConfig().set("mobhunt.camp-low-limit", next);
        } else if (slot == 51) {
            plugin.getConfig().set("mobhunt.hunter-lives", configuredHunterLives() >= 5 ? 1 : configuredHunterLives() + 1);
        } else if (slot == 52) {
            plugin.getConfig().set("mobhunt.random-events", !randomEventsEnabled());
        } else return;
        plugin.saveConfig();
        openAdminMenu(p);
    }

    private static final class Match {
        final Map<UUID, Location> lastLocations = new HashMap<>();
        final Map<UUID, Integer> afkTicks = new HashMap<>();
        final Set<UUID> afkWarned = new LinkedHashSet<>();
        String mapName="village"; boolean zoneShrunk;
        final Set<UUID> players = new LinkedHashSet<>();
        final Set<UUID> aliveHiders = new LinkedHashSet<>();
        final Set<UUID> tagged = new LinkedHashSet<>();
        final Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
        final Map<UUID, Integer> scores = new HashMap<>();
        final Map<UUID, String> mobForms = new HashMap<>();
        final Map<UUID, Set<Material>> selectedTools = new HashMap<>();
        UUID hunter;
        int round;
        int secondsLeft;
        boolean hunterLocked;
        boolean roundEnded;
        boolean ending;
        BukkitTask releaseTask;
        BukkitTask roundTask;
        final Set<UUID> mobPickPending = new LinkedHashSet<>();
        final Map<UUID, Long> abilityReadyAt = new HashMap<>();
        final Map<UUID, Integer> survivalSeconds = new HashMap<>();
        final Map<UUID, Integer> kills = new HashMap<>();
        final Map<UUID, Integer> hunterRoundWins = new HashMap<>();
        final Map<UUID, Integer> hiderRoundWins = new HashMap<>();
        int randomEventCount;
        final Set<UUID> disconnected = new LinkedHashSet<>();
        final Set<UUID> forfeited = new LinkedHashSet<>();
        final Map<UUID, BukkitTask> disconnectTasks = new HashMap<>();
        boolean privateMatch;
        int hunterKillStreak;
        int hunterLives = HUNTER_LIVES;
        BukkitTask mobPickTask;
        boolean lastHiderAnnounced;
        void cancelRoundTasks() {
            if (releaseTask != null) releaseTask.cancel();
            if (roundTask != null) roundTask.cancel();
            if (mobPickTask != null) mobPickTask.cancel();
            releaseTask = null;
            roundTask = null;
            mobPickTask = null;
        }
    }

    private static final class PlayerSnapshot {
        private final ItemStack[] contents;
        private final ItemStack[] armor;
        private final ItemStack offhand;
        private final GameMode gameMode;
        private final Location location;
        private final int food;
        private final float saturation;
        private final double health;
        private final boolean allowFlight;
        private final boolean flying;
        private final float walkSpeed;
        private final float flySpeed;
        private final int level;
        private final float exp;
        private final List<PotionEffect> effects;

        PlayerSnapshot(Player player) {
            PlayerInventory inv = player.getInventory();
            contents = cloneItems(inv.getContents());
            armor = cloneItems(inv.getArmorContents());
            offhand = inv.getItemInOffHand() == null ? null : inv.getItemInOffHand().clone();
            gameMode = player.getGameMode();
            location = player.getLocation().clone();
            food = player.getFoodLevel();
            saturation = player.getSaturation();
            health = player.getHealth();
            allowFlight = player.getAllowFlight();
            flying = player.isFlying();
            walkSpeed = player.getWalkSpeed();
            flySpeed = player.getFlySpeed();
            level = player.getLevel();
            exp = player.getExp();
            effects = new ArrayList<>(player.getActivePotionEffects());
        }

        void restoreAtLobby(Player player, Location lobby) {
            player.closeInventory();
            player.teleport(lobby);
            PlayerInventory inv = player.getInventory();
            inv.clear();
            inv.setContents(cloneItems(contents));
            inv.setArmorContents(cloneItems(armor));
            inv.setItemInOffHand(offhand == null ? null : offhand.clone());
            player.setGameMode(gameMode);
            player.setFoodLevel(food);
            player.setSaturation(saturation);
            double max = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.max(1.0, Math.min(health, max)));
            player.setLevel(level);
            player.setExp(exp);
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            for (PotionEffect effect : effects) player.addPotionEffect(effect);
            player.setWalkSpeed(walkSpeed);
            player.setFlySpeed(flySpeed);
            player.setAllowFlight(allowFlight);
            player.setFlying(allowFlight && flying);
            player.updateInventory();
        }

        private static ItemStack[] cloneItems(ItemStack[] input) {
            ItemStack[] output = new ItemStack[input.length];
            for (int i = 0; i < input.length; i++) output[i] = input[i] == null ? null : input[i].clone();
            return output;
        }
    }
}
