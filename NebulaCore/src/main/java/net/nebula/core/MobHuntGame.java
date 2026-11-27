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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
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
    private static final int ROUND_SECONDS = 120;
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

    public MobHuntGame(JavaPlugin plugin, Location lobby) {
        this.plugin = plugin;
        this.lobby = lobby.clone();
        this.npcKey = new NamespacedKey(plugin, "nebula_mobhunt_npc");
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
                "3 rounds • Random hunter each round", "Hiders become real mob disguises", "Tag-only game: no player damage"));
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
            default -> player.sendMessage(PREFIX + "Use /mobhunt [join|leave|status].");
        }
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
        if (match != null) {
            player.sendMessage(PREFIX + ChatColor.RED + "A Mob Hunt match is already running.");
            return;
        }
        if (player.getWorld().getName().equals("bedwars_1") || player.getWorld().getName().equals("duel_arena")) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave your current minigame before joining Mob Hunt.");
            return;
        }
        if (queue.contains(id)) {
            player.sendMessage(PREFIX + "You are already waiting for Mob Hunt.");
            return;
        }
        if (queue.size() >= MAX_PLAYERS) {
            player.sendMessage(PREFIX + ChatColor.RED + "The Mob Hunt queue is full (8 players).");
            return;
        }
        queue.add(id);
        broadcastQueue();
        if (queue.size() >= MIN_PLAYERS && queueTask == null) {
            startQueueCountdown(20);
        } else if (queue.size() >= 3 && queueTask != null && !queueAccelerated) {
            queueSeconds = 10;
            queueAccelerated = true;
            broadcast(ChatColor.GOLD + "3 players joined! Mob Hunt now starts in 10 seconds.");
        }
    }

    private void leaveQueue(Player player) {
        boolean removed = queue.remove(player.getUniqueId());
        if (removed) {
            player.sendMessage(PREFIX + "You left the Mob Hunt queue.");
            broadcastQueue();
        } else if (match != null && match.players.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "You cannot leave during a round. Use /spawn after the match.");
        } else {
            player.sendMessage(PREFIX + "You are not in the Mob Hunt queue.");
        }
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
        if (players.size() < MIN_PLAYERS) {
            for (Player player : players) player.sendMessage(PREFIX + ChatColor.RED + "Not enough players. Queue again.");
            return;
        }
        Match next = new Match();
        match = next;
        for (Player player : players) {
            next.players.add(player.getUniqueId());
            next.snapshots.put(player.getUniqueId(), new PlayerSnapshot(player));
            next.scores.put(player.getUniqueId(), 0);
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Mob Hunt match started! 3 rounds. Stay inside the village border.");
        }
        startRound(next);
    }

    private void startRound(Match active) {
        if (match != active || active.ending) return;
        active.round++;
        active.roundEnded = false;
        if (active.round > ROUNDS) {
            finishMatch(active);
            return;
        }
        active.cancelRoundTasks();
        active.aliveHiders.clear();
        active.tagged.clear();
        active.selectedTools.clear();

        List<UUID> candidates = new ArrayList<>(active.players);
        candidates.removeIf(id -> Bukkit.getPlayer(id) == null || !Bukkit.getPlayer(id).isOnline());
        if (candidates.size() < MIN_PLAYERS) {
            finishMatch(active);
            return;
        }
        active.hunter = candidates.get(random.nextInt(candidates.size()));
        active.hunterLocked = true;
        active.secondsLeft = ROUND_SECONDS;
        Player hunter = Bukkit.getPlayer(active.hunter);
        for (UUID id : candidates) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            resetForRound(player);
            if (id.equals(active.hunter)) {
                player.teleport(new Location(world, 0.5, world.getHighestBlockYAt(0, 0) + 1.0, 0.5));
                player.setWalkSpeed(0.0f);
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, HUNTER_HIDE_SECONDS * 20, 0, false, false, true));
                player.sendTitle(ChatColor.RED + "YOU ARE THE HUNTER",
                        ChatColor.GRAY + "You are frozen and blinded for 15 seconds", 5, 45, 10);
                giveHunterBaseKit(player);
                openHunterTools(player);
                player.sendMessage(PREFIX + "Choose exactly 2 utility items before the 15-second release.");
            } else {
                active.aliveHiders.add(id);
                String mob = MOB_TYPES.get(random.nextInt(MOB_TYPES.size()));
                active.mobForms.put(id, mob);
                applyDisguise(player, mob);
                player.teleport(randomVillageSpawn());
                giveHiderKit(player);
                applyMobAbility(player, mob);
                player.sendTitle(ChatColor.GREEN + "HIDE!",
                        ChatColor.GRAY + "Your mob form: " + mob.replace('_', ' '), 5, 45, 10);
                player.sendMessage(PREFIX + ChatColor.GREEN + "You are disguised as " + ChatColor.AQUA
                        + mob.replace('_', ' ') + ChatColor.GRAY + ". Hide in the village!");
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
                    currentHunter.sendTitle(ChatColor.GOLD + "GO!", ChatColor.GRAY + "Find and tag the hiders!", 0, 25, 5);
                    currentHunter.sendMessage(PREFIX + "Your two selected tools are ready. Right-click a hider nearby to tag them.");
                }
            }, HUNTER_HIDE_SECONDS * 20L);
        }
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
                for (UUID id : active.players) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null && p.isOnline()) {
                        p.sendActionBar(ChatColor.LIGHT_PURPLE + "ROUND " + active.round + "/" + ROUNDS
                                + ChatColor.GRAY + " • " + ChatColor.WHITE + active.secondsLeft + "s left"
                                + ChatColor.GRAY + " • Hiders: " + ChatColor.AQUA + active.aliveHiders.size());
                    }
                }
                updateHunterCompass(active);
                active.secondsLeft--;
            }
        }.runTaskTimer(plugin, 20L, 20L);
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
        player.getInventory().setItem(7, new ItemStack(Material.ENDER_PEARL, 3));
        player.getInventory().setItem(8, new ItemStack(Material.COOKED_BEEF, 5));
    }

    private void giveHiderKit(Player player) {
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

    private void applyMobAbility(Player player, String mob) {
        switch (mob) {
            case "GHAST", "BAT", "BEE" -> {
                player.setAllowFlight(true);
                player.setFlying(true);
                player.setFlySpeed(0.08f);
            }
            case "RABBIT", "FROG", "SLIME", "MAGMA_CUBE" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ROUND_SECONDS * 20, 1, false, false, true));
            case "WOLF", "FOX", "CAT" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_SECONDS * 20, 1, false, false, true));
            case "TURTLE", "AXOLOTL" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, ROUND_SECONDS * 20, 0, false, false, true));
            case "WARDEN" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, ROUND_SECONDS * 20, 0, false, false, true));
            default -> { }
        }
    }

    private void updateHunterCompass(Match active) {
        Player hunter = Bukkit.getPlayer(active.hunter);
        if (hunter == null || !hunter.isOnline() || !hunter.getWorld().equals(world)) return;
        Player nearest = null;
        double distance = Double.MAX_VALUE;
        for (UUID id : active.aliveHiders) {
            Player hider = Bukkit.getPlayer(id);
            if (hider == null || !hider.isOnline() || !hider.getWorld().equals(world)) continue;
            double d = hunter.getLocation().distanceSquared(hider.getLocation());
            if (d < distance) { distance = d; nearest = hider; }
        }
        if (nearest != null) hunter.setCompassTarget(nearest.getLocation());
    }

    private void endRound(Match active, boolean hunterWon) {
        if (match != active || active.roundEnded || active.ending) return;
        active.roundEnded = true;
        active.cancelRoundTasks();
        if (hunterWon) {
            active.scores.merge(active.hunter, 1, Integer::sum);
            broadcastToMatch(active, ChatColor.GOLD + nameOf(active.hunter) + " tagged every hider and wins the round!");
        } else {
            for (UUID id : active.aliveHiders) active.scores.merge(id, 1, Integer::sum);
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
        broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "MOB HUNT WINNER: "
                + ChatColor.YELLOW + (winnerNames.isBlank() ? "No winner" : winnerNames)
                + ChatColor.GRAY + " • " + high + " point(s)");
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            DisguiseAPI.undisguiseToAll(player);
            PlayerSnapshot snapshot = active.snapshots.get(id);
            if (snapshot != null) snapshot.restoreAtLobby(player, lobby);
            else player.teleport(lobby);
            player.sendTitle(ChatColor.GOLD + "MOB HUNT OVER",
                    ChatColor.GRAY + "Winner: " + (winnerNames.isBlank() ? "No winner" : winnerNames), 5, 70, 15);
        }
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
        int[][] spots = {
                {-23, -23}, {23, -23}, {-23, 23}, {23, 23},
                {-10, -28}, {10, 28}, {-28, 10}, {28, -10},
                {0, -30}, {30, 0}, {0, 30}, {-30, 0}
        };
        int[] spot = spots[random.nextInt(spots.length)];
        int y = world.getHighestBlockYAt(spot[0], spot[1]) + 1;
        return new Location(world, spot[0] + 0.5, y, spot[1] + 0.5);
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
    public void onHunterTag(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player hider)) return;
        Match active = match;
        if (active == null || active.ending || active.roundEnded) return;
        Player hunter = event.getPlayer();
        if (!hunter.getUniqueId().equals(active.hunter) || !active.aliveHiders.contains(hider.getUniqueId())) return;
        if (!hunter.getWorld().equals(world) || !hider.getWorld().equals(world)) return;
        if (hunter.getLocation().distanceSquared(hider.getLocation()) > 9.0) return;
        event.setCancelled(true);
        active.aliveHiders.remove(hider.getUniqueId());
        active.tagged.add(hider.getUniqueId());
        DisguiseAPI.undisguiseToAll(hider);
        hider.setAllowFlight(false);
        hider.setFlying(false);
        hider.closeInventory();
        hider.sendTitle(ChatColor.RED + "TAGGED!", ChatColor.GRAY + "You are out for this round.", 5, 35, 10);
        hunter.sendMessage(PREFIX + ChatColor.GREEN + "You tagged " + hider.getName() + "!");
        broadcastToMatch(active, ChatColor.YELLOW + hider.getName() + " has been tagged. "
                + active.aliveHiders.size() + " hider(s) remain.");
        if (active.aliveHiders.isEmpty()) endRound(active, true);
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
        if (!(event.getEntity() instanceof Player player)) return;
        Match active = match;
        if (active != null && active.players.contains(player.getUniqueId())) event.setCancelled(true);
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
        if (queue.remove(id) && queue.size() < MIN_PLAYERS) cancelQueueCountdown();
        Match active = match;
        if (active == null || !active.players.contains(id)) return;
        active.players.remove(id);
        active.aliveHiders.remove(id);
        if (active.players.size() < MIN_PLAYERS) {
            abortMatch(active, "Not enough players remain. The match has ended.");
        } else if (id.equals(active.hunter)) {
            if (!active.aliveHiders.isEmpty()) endRound(active, false);
        } else if (active.aliveHiders.isEmpty()) {
            endRound(active, true);
        }
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
        world.setDifficulty(Difficulty.NORMAL);
        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        int base = Math.max(world.getMinHeight() + 4, world.getHighestBlockYAt(0, 0));
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++) {
                world.getBlockAt(x, base, z).setType(Material.GRASS_BLOCK, false);
                world.getBlockAt(x, base - 1, z).setType(Material.DIRT, false);
                world.getBlockAt(x, base - 2, z).setType(Material.STONE, false);
                for (int y = base + 1; y <= base + 16; y++) world.getBlockAt(x, y, z).setType(Material.AIR, false);
                if (Math.abs(x) <= 3 || Math.abs(z) <= 3) {
                    world.getBlockAt(x, base, z).setType(Material.PATH_BLOCK, false);
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

    private static final class Match {
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
        void cancelRoundTasks() {
            if (releaseTask != null) releaseTask.cancel();
            if (roundTask != null) roundTask.cancel();
            releaseTask = null;
            roundTask = null;
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
