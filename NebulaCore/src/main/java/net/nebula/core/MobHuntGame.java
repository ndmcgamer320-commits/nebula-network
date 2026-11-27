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
import org.bukkit.Registry;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
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
import java.io.File;
import java.io.IOException;
import java.util.stream.Collectors;

/**
 * Non-damage hide-and-seek mode. Hiders are visually disguised as real mobs;
 * the hunter tags them by interacting at close range. No player damage is used.
 */
public final class MobHuntGame implements Listener {
    private static final String WORLD_NAME = "mob_hunt";
    private static final String PRACTICE_WORLD_NAME = "mob_hunt_practice";
    private static final String MENU_TITLE = ChatColor.DARK_PURPLE + "Mob Hunt";
    private static final String PICK_TITLE = ChatColor.DARK_PURPLE + "Choose 2 Hunt Tools";
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD
            + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 8;
    private static final int ROUNDS = 3;
    private static final int ROUND_SECONDS = 300;
    private static final int HUNTER_LIVES = 3;
    private static final int MOB_PICK_SECONDS = 10;
    private static final int CAMP_LOW_LIMIT = 5;
    private static final int CAMP_HIGH_LIMIT = 12;
    private static final String MOB_PICK_TITLE = ChatColor.DARK_GREEN + "Choose Your Mob";
    private static final String MOB_SEARCH_TITLE = ChatColor.DARK_AQUA + "Mob Search";
    private static final String ABILITY_TITLE = ChatColor.DARK_PURPLE + "Mob Ability Cooldowns";
    private static final String SETTINGS_TITLE = ChatColor.DARK_AQUA + "Mob Hunt Settings";
    private static final String STATS_TITLE = ChatColor.DARK_GREEN + "Mob Hunt Statistics";
    private static final int HUNTER_HIDE_SECONDS = 15;
    private static final List<String> MOB_TYPES = List.of(
            "PIG", "COW", "SHEEP", "CHICKEN", "WOLF", "CAT", "FOX", "RABBIT", "VILLAGER",
            "IRON_GOLEM", "SNOW_GOLEM", "HORSE", "DONKEY", "MULE", "LLAMA", "POLAR_BEAR",
            "OCELOT", "MOOSHROOM", "PARROT", "BAT", "SPIDER", "CAVE_SPIDER", "ENDERMAN",
            "CREEPER", "ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY", "SLIME",
            "MAGMA_CUBE", "GHAST", "WARDEN", "GOAT", "PANDA", "BEE", "FROG", "TURTLE",
            "AXOLOTL", "CAMEL", "SNIFFER", "ALLAY", "ARMADILLO", "BLAZE", "PHANTOM",
            "SILVERFISH", "ENDERMITE", "PILLAGER", "VINDICATOR", "EVOKER", "WITHER_SKELETON", "PIGLIN",
            "HOGLIN", "STRIDER", "DOLPHIN", "SHULKER", "BREEZE", "BOGGED"
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
    private World practiceWorld;
    private final List<Arena> arenas = new ArrayList<>();
    private final Map<UUID, Match> practiceSessions = new HashMap<>();
    private final Map<UUID, PlayerSnapshot> practiceSnapshots = new HashMap<>();
    private BukkitTask queueTask;
    private int queueSeconds;
    private boolean queueAccelerated;
    private Match match;
    private boolean ready;
    private int arenaBaseY;
    private int arenaCenterX;
    private int arenaCenterZ;
    private final Set<UUID> searchingMob = new LinkedHashSet<>();
    private final File statsFile;
    private final org.bukkit.configuration.file.YamlConfiguration stats;
    private final Map<UUID, Integer> spectatorTargets = new HashMap<>();

    public MobHuntGame(JavaPlugin plugin, Location lobby) {
        this.plugin = plugin;
        this.lobby = lobby.clone();
        this.npcKey = new NamespacedKey(plugin, "nebula_mobhunt_npc");
        this.statsFile = new File(plugin.getDataFolder(), "mobhunt-stats.yml");
        this.stats = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(statsFile);
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
            creator.type(WorldType.NORMAL);
            creator.seed(8675309123456789L);
            creator.generateStructures(true);
            world = Bukkit.createWorld(creator);
        }
        if (world == null) {
            plugin.getLogger().severe("Mob Hunt world could not be created.");
            return;
        }
        buildVillage();
        createPracticeWorld();
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
                "Queue with at least 2 players", "20-second start countdown", "3+ players: 10-second countdown", "Round 2-3 mob choice: 10 seconds"));
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
            case "settings" -> openSettings(player);
            case "stats" -> openStats(player);
            case "spectate" -> cycleSpectator(player);
            case "practice" -> { if (args.length > 1 && args[1].equalsIgnoreCase("leave")) leavePractice(player); else startPractice(player); }
            default -> player.sendMessage(PREFIX + "Use /mobhunt [join|leave|status|settings|stats|spectate|practice [leave]].");
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
        if (practiceSessions.containsKey(id)) { player.sendMessage(PREFIX + ChatColor.RED + "Leave practice first with /mobhunt practice leave."); return; }
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
        Arena selectedArena = selectRandomArena();
        if (selectedArena != null) activateArena(selectedArena);
        Match next = new Match();
        next.arenaName = selectedArena == null ? "Village" : selectedArena.name;
        match = next;
        for (Player player : players) {
            next.players.add(player.getUniqueId());
            next.snapshots.put(player.getUniqueId(), new PlayerSnapshot(player));
            next.scores.put(player.getUniqueId(), 0);
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Mob Hunt match started! Map: " + next.arenaName + ". 3 rounds.");
        }
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
        active.hunterLives = HUNTER_LIVES;

        List<UUID> candidates = new ArrayList<>(active.players);
        candidates.removeIf(id -> Bukkit.getPlayer(id) == null || !Bukkit.getPlayer(id).isOnline());
        if (candidates.size() < MIN_PLAYERS) {
            finishMatch(active);
            return;
        }
        active.hunter = candidates.get(random.nextInt(candidates.size()));
        active.hunterLocked = true;
        active.secondsLeft = ROUND_SECONDS;
        active.hunterLives = HUNTER_LIVES;
        Player hunter = active.hunter == null ? null : Bukkit.getPlayer(active.hunter);
        for (UUID id : candidates) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            resetForRound(player);
            if (id.equals(active.hunter)) {
                player.teleport(randomVillageSpawn());
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
                    assignMob(active, player, MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
                } else {
                    active.mobPickPending.add(id);
                    openMobPicker(player, "");
                    player.sendTitle(ChatColor.GREEN + "CHOOSE YOUR MOB",
                            ChatColor.GRAY + "You have 10 seconds to pick any mob", 5, 45, 10);
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
        active.mobPickEndsAt = System.currentTimeMillis() + MOB_PICK_SECONDS * 1000L;
        active.mobPickTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (match != active || active.roundEnded || active.ending) return;
            for (UUID pending : new ArrayList<>(active.mobPickPending)) {
                Player hider = Bukkit.getPlayer(pending);
                if (hider != null) assignMob(active, hider, MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
            }
        }, MOB_PICK_SECONDS * 20L);
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
                        if (active.mobPickPending.contains(id)) {
                            int remaining = Math.max(0, (int) Math.ceil((active.mobPickEndsAt - System.currentTimeMillis()) / 1000.0));
                            p.sendActionBar(ChatColor.GREEN + "CHOOSE YOUR MOB • " + remaining + "s");
                        } else {
                            p.sendActionBar(ChatColor.LIGHT_PURPLE + "ROUND " + active.round + "/" + ROUNDS
                                    + ChatColor.GRAY + " • " + ChatColor.WHITE + active.secondsLeft + "s left"
                                    + ChatColor.GRAY + " • Hiders: " + ChatColor.AQUA + active.aliveHiders.size()
                                    + ChatColor.GRAY + " • Hunter lives: " + ChatColor.RED + active.hunterLives
                                    + ChatColor.DARK_GRAY + " • " + ChatColor.YELLOW + active.arenaName);
                        }
                    }
                }
                updateHunterCompass(active);
                updateProximityWarnings(active);
                updateAntiCamping(active);
                updateLastHiderMode(active);
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
        player.setGameMode(GameMode.SURVIVAL);
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
            // Keep the disguise visible to other players, but hide the self-disguise
            // from the hider's own client. This preserves the normal Steve first-person
            // camera, hand rendering, mouse look and player movement without a mob model
            // clipping through the camera.
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
            case "GHAST", "BAT", "BEE", "PHANTOM", "ALLAY" -> {
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
            case "WITHER_SKELETON" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, ROUND_SECONDS * 20, 0, false, false, true));
            case "STRAY" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, ROUND_SECONDS * 20, 0, false, false, true));
            case "CREEPER" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ROUND_SECONDS * 20, 0, false, false, true));
            case "ENDERMAN" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_SECONDS * 20, 1, false, false, true));
            case "IRON_GOLEM" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, ROUND_SECONDS * 20, 0, false, false, true));
            case "VILLAGER", "ALLAY" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_SECONDS * 20, 0, false, false, true));
            case "HORSE", "DONKEY", "MULE", "LLAMA", "STRIDER" ->
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_SECONDS * 20, 1, false, false, true));
            case "BLAZE", "PHANTOM" -> {
                player.setAllowFlight(true);
                player.setFlySpeed(0.06f);
            }
            default -> { }
        }
    }

    private void assignMob(Match active, Player player, String mob) {
        active.mobPickPending.remove(player.getUniqueId());
        active.mobForms.put(player.getUniqueId(), mob);
        applyDisguise(player, mob);
        applyMobAbility(player, mob);
        player.sendTitle(ChatColor.GREEN + "MOB SELECTED", ChatColor.GRAY + pretty(mob), 3, 30, 8);
        player.sendMessage(PREFIX + ChatColor.GREEN + "Your mob form: " + ChatColor.AQUA + pretty(mob));
    }
    private void openMobPicker(Player player, String filter) {
        filter = filter == null ? "" : filter.trim().replace(' ', '_');
        Inventory inv = Bukkit.createInventory(null, 54, MOB_PICK_TITLE);
        int slot = 0;
        for (String mob : MOB_TYPES) {
            if (!matchesMobFilter(mob, filter)) continue;
            Material icon;
            try { icon = Material.valueOf(mob + "_SPAWN_EGG"); } catch (IllegalArgumentException ex) { icon = Material.PAPER; }
            if (slot >= 45) break;
            inv.setItem(slot++, named(icon, ChatColor.GREEN + pretty(mob), "Click to choose this mob", mobDescription(mob), "Passive traits apply automatically"));
        }
        inv.setItem(45, named(Material.GRASS_BLOCK, ChatColor.GREEN + "Passive", "Show peaceful and neutral mobs"));
        inv.setItem(46, named(Material.IRON_SWORD, ChatColor.RED + "Hostile", "Show hostile mobs"));
        inv.setItem(47, named(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Special", "Show rare and utility mobs"));
        inv.setItem(48, named(Material.CHEST, ChatColor.YELLOW + "All Mobs", "Show the full mob list"));
        inv.setItem(49, named(Material.COMPASS, ChatColor.AQUA + "Search mobs", "Click, then type a mob name in chat", "Current filter: " + (filter.isBlank() ? "all mobs" : filter)));
        inv.setItem(53, named(Material.BARRIER, ChatColor.RED + "Random mob", "Choose a random mob"));
        player.openInventory(inv);
    }

    private String mobDescription(String mob) {
        return switch (mob) {
            case "WITHER_SKELETON" -> "Hits apply Wither to the hunter";
            case "BEE" -> "Can fly • sting ability";
            case "SPIDER", "CAVE_SPIDER" -> "Climbs walls automatically";
            case "STRAY" -> "Hits slow the hunter";
            case "HUSK" -> "Hits apply Hunger";
            case "BLAZE" -> "Fire resistance • flame burst";
            case "GHAST", "BAT", "PHANTOM" -> "Can fly";
            case "IRON_GOLEM" -> "High resistance • ground slam";
            case "TURTLE" -> "Water breathing • defensive trait";
            case "FOX" -> "Fast movement • pounce";
            case "WARDEN" -> "Dark vision • sonic pulse";
            default -> "Unique mob trait and special ability";
        };
    }

    private boolean matchesMobFilter(String mob, String filter) {
        if (filter == null || filter.isBlank() || filter.equalsIgnoreCase("all")) return true;
        String value = mob.toLowerCase(java.util.Locale.ROOT);
        String f = filter.toLowerCase(java.util.Locale.ROOT);
        if (f.equals("passive")) return !Set.of("CREEPER","ZOMBIE","HUSK","DROWNED","SKELETON","STRAY","SPIDER","CAVE_SPIDER","ENDERMAN","BLAZE","PHANTOM","WARDEN","SILVERFISH","ENDERMITE","PILLAGER","VINDICATOR","EVOKER","HOGLIN","BOGGED","MAGMA_CUBE","GHAST").contains(mob);
        if (f.equals("hostile")) return Set.of("CREEPER","ZOMBIE","HUSK","DROWNED","SKELETON","STRAY","SPIDER","CAVE_SPIDER","ENDERMAN","BLAZE","PHANTOM","WARDEN","SILVERFISH","ENDERMITE","PILLAGER","VINDICATOR","EVOKER","HOGLIN","BOGGED","MAGMA_CUBE","GHAST").contains(mob);
        if (f.equals("special")) return Set.of("WARDEN","ALLAY","BREEZE","SHULKER","SNIFFER","SNIFFER","ARMADILLO","CAMEL","MOOSHROOM","STRIDER","GOAT","AXOLOTL","DOLPHIN").contains(mob);
        return value.contains(f);
    }
    @EventHandler
    public void onMobPickerClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !event.getView().getTitle().equals(MOB_PICK_TITLE)) return;
        event.setCancelled(true);
        Match active = practiceSessions.getOrDefault(player.getUniqueId(), match);
        boolean practice = practiceSessions.containsKey(player.getUniqueId());
        if (active == null || active.roundEnded || (!practice && !active.mobPickPending.contains(player.getUniqueId()))) return;
        int slot = event.getRawSlot();
        if (slot == 45 || slot == 46 || slot == 47 || slot == 48) {
            String category = slot == 45 ? "passive" : slot == 46 ? "hostile" : slot == 47 ? "special" : "";
            openMobPicker(player, category);
            return;
        }
        if (slot == 49) {
            searchingMob.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(PREFIX + ChatColor.AQUA + "Type a mob name in chat to search (example: warden). You have 10 seconds total.");
            return;
        }
        if (slot == 53) {
            assignMob(active, player, MOB_TYPES.get(random.nextInt(MOB_TYPES.size())));
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
        if (!searchingMob.remove(id)) return;
        event.setCancelled(true);
        String query = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Match active = practiceSessions.getOrDefault(id, match);
            Player player = Bukkit.getPlayer(id);
            if (player != null && active != null && (practiceSessions.containsKey(id) || active.mobPickPending.contains(id))) {
                String normalized = query.toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
                boolean found = MOB_TYPES.stream().anyMatch(m -> m.contains(normalized));
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
        for (UUID id : new ArrayList<>(active.aliveHiders)) {
            Player hider = Bukkit.getPlayer(id);
            if (hider == null || !hider.isOnline() || !hider.getWorld().equals(world)) continue;
            if (hider.getLocation().getY() < arenaBaseY - CAMP_LOW_LIMIT) {
                Location safe = hider.getLocation().clone();
                safe.setY(world.getHighestBlockYAt(safe.getBlockX(), safe.getBlockZ()) + 1.0);
                hider.teleport(safe);
                hider.sendMessage(PREFIX + ChatColor.RED + "Anti-camping: underground hiding is disabled.");
            } else if (hider.getLocation().getY() > arenaBaseY + CAMP_HIGH_LIMIT && hunter != null) {
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
    }
    private void updateLastHiderMode(Match active) {
        if (active.aliveHiders.size() != 1 || active.lastHiderAnnounced) return;
        active.lastHiderAnnounced = true;
        UUID id = active.aliveHiders.iterator().next();
        Player last = Bukkit.getPlayer(id);
        broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "LAST HIDER MODE! " + ChatColor.YELLOW + (last == null ? "One hider remains!" : last.getName() + " is the final hider!"));
        if (last != null) {
            last.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ROUND_SECONDS * 20, 0, false, false, true));
            last.sendTitle(ChatColor.GOLD + "LAST HIDER", ChatColor.GRAY + "Survive until the timer ends!", 5, 50, 10);
        }
    }

    private String abilityName(String mob) {
        return switch (mob) {
            case "WARDEN" -> "Sonic Pulse"; case "CREEPER" -> "Creeper Burst"; case "GHAST" -> "Ghast Fireball";
            case "BEE" -> "Sting"; case "SPIDER", "CAVE_SPIDER" -> "Web Dash";
            case "SLIME", "MAGMA_CUBE", "FROG", "RABBIT" -> "Mega Jump";
            case "WOLF", "FOX", "CAT" -> "Pounce";
            case "VILLAGER", "ALLAY" -> "Emerald Escape";
            case "IRON_GOLEM" -> "Iron Slam";
            case "HORSE", "DONKEY", "MULE", "LLAMA" -> "Stampede";
            case "BLAZE" -> "Flame Burst";
            default -> "Mob Strike";
        };
    }
    private long abilityCooldown(String mob) {
        return switch (mob) { case "WARDEN", "GHAST", "CREEPER", "IRON_GOLEM" -> 15000L; case "BEE", "SPIDER", "CAVE_SPIDER", "BLAZE" -> 10000L; default -> 8000L; };
    }
    private void incrementStat(UUID id, String key, int amount) {
        String path = "players." + id;
        stats.set(path + ".name", nameOf(id));
        stats.set(path + "." + key, stats.getInt(path + "." + key, 0) + amount);
        try { stats.save(statsFile); }
        catch (IOException ex) { plugin.getLogger().warning("Could not save Mob Hunt stats: " + ex.getMessage()); }
    }

    private void openStats(Player player) {
        String path = "players." + player.getUniqueId();
        Inventory inv = Bukkit.createInventory(null, 27, STATS_TITLE);
        inv.setItem(10, named(Material.DIAMOND, ChatColor.GREEN + "Match Wins", "Wins: " + stats.getInt(path + ".matchWins", 0)));
        inv.setItem(12, named(Material.IRON_SWORD, ChatColor.RED + "Eliminations", "Eliminations: " + stats.getInt(path + ".eliminations", 0)));
        inv.setItem(14, named(Material.CLOCK, ChatColor.AQUA + "Matches Played", "Matches: " + stats.getInt(path + ".matchesPlayed", 0)));
        inv.setItem(16, named(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "Round Wins", "Rounds: " + stats.getInt(path + ".roundWins", 0)));
        player.openInventory(inv);
    }

    private void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SETTINGS_TITLE);
        String base = "mobhunt.player-settings." + player.getUniqueId() + ".";
        boolean particles = plugin.getConfig().getBoolean(base + "particles", true);
        boolean sounds = plugin.getConfig().getBoolean(base + "sounds", true);
        inv.setItem(11, named(particles ? Material.GLOWSTONE_DUST : Material.GRAY_DYE, ChatColor.YELLOW + "Ability Particles: " + (particles ? "ON" : "OFF"), "Click to toggle ability particles."));
        inv.setItem(13, named(usesSwapHands(player) ? Material.FEATHER : Material.ENDER_PEARL,
                ChatColor.LIGHT_PURPLE + "Ability Trigger: " + (usesSwapHands(player) ? "F / Swap Hands" : "Right Click"),
                "Click to switch between F and right-click."));
        inv.setItem(15, named(sounds ? Material.NOTE_BLOCK : Material.GRAY_DYE, ChatColor.AQUA + "Ability Sounds: " + (sounds ? "ON" : "OFF"), "Click to toggle ability sounds."));
        inv.setItem(22, named(Material.BARRIER, ChatColor.RED + "Close", "Close settings."));
        player.openInventory(inv);
    }

    private void createPracticeWorld() {
        practiceWorld = Bukkit.getWorld(PRACTICE_WORLD_NAME);
        if (practiceWorld == null) {
            WorldCreator creator = new WorldCreator(PRACTICE_WORLD_NAME);
            creator.type(WorldType.FLAT);
            creator.generateStructures(false);
            practiceWorld = Bukkit.createWorld(creator);
        }
        if (practiceWorld == null) {
            plugin.getLogger().warning("Mob Hunt practice world could not be created.");
            return;
        }
        practiceWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        practiceWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        practiceWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        practiceWorld.setTime(6000L);
        practiceWorld.setStorm(false);
        int y = Math.max(80, practiceWorld.getMinHeight() + 10);
        for (int x = -18; x <= 18; x++) {
            for (int z = -18; z <= 18; z++) {
                practiceWorld.getBlockAt(x, y - 1, z).setType(Material.SMOOTH_STONE, false);
                for (int h = 0; h < 8; h++) practiceWorld.getBlockAt(x, y + h, z).setType(Material.AIR, false);
            }
        }
        practiceWorld.setSpawnLocation(0, y, 0);
        practiceWorld.getWorldBorder().setCenter(0.5, 0.5);
        practiceWorld.getWorldBorder().setSize(64.0);
    }

    private void startPractice(Player player) {
        if (practiceWorld == null) {
            player.sendMessage(PREFIX + ChatColor.RED + "Practice world is unavailable.");
            return;
        }
        if (match != null || queue.contains(player.getUniqueId())) {
            player.sendMessage(PREFIX + ChatColor.RED + "Leave the match queue or wait for the current match to finish first.");
            return;
        }
        UUID id = player.getUniqueId();
        if (practiceSessions.containsKey(id)) {
            openMobPicker(player, "");
            return;
        }
        practiceSnapshots.put(id, new PlayerSnapshot(player));
        Match session = new Match();
        session.players.add(id);
        session.aliveHiders.add(id);
        practiceSessions.put(id, session);
        player.closeInventory();
        player.teleport(new Location(practiceWorld, 0.5, practiceWorld.getSpawnLocation().getY(), 0.5));
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.setGameMode(GameMode.SURVIVAL);
        player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setFoodLevel(20);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.getInventory().setItem(8, named(Material.NETHER_STAR, ChatColor.GREEN + "Mob Ability", "Right-click or use your configured key"));
        player.sendTitle(ChatColor.LIGHT_PURPLE + "MOB HUNT PRACTICE", ChatColor.GRAY + "Choose a mob to test its traits", 5, 45, 10);
        openMobPicker(player, "");
    }

    private void leavePractice(Player player) {
        UUID id = player.getUniqueId();
        Match session = practiceSessions.remove(id);
        PlayerSnapshot snapshot = practiceSnapshots.remove(id);
        searchingMob.remove(id);
        DisguiseAPI.undisguiseToAll(player);
        for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setFlySpeed(0.1f);
        if (snapshot != null) snapshot.restoreAtLobby(player, lobby, plugin);
        else if (plugin instanceof NebulaCore core) core.returnPlayerToLobby(player);
        player.sendMessage(PREFIX + ChatColor.GREEN + "You left Mob Hunt practice.");
    }

    private void openPracticeGuide(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "Mob Hunt Practice");
        inv.setItem(11, named(Material.SPAWNER, ChatColor.GREEN + "Ability Training", "Use /mobhunt join to enter a live test match.", "Abilities are available to hiders during a round.", "Use /mobhunt settings to customize effects."));
        inv.setItem(15, named(Material.BOOK, ChatColor.AQUA + "Ability Guide", "Spider: automatic wall climb • Warden: sonic pulse", "Iron Golem: slam • Fox: pounce • Bee: sting", "Blaze: flame burst • Villager: escape dash"));
        player.openInventory(inv);
    }

    private void cycleSpectator(Player player) {
        Match active = match;
        if (active == null || !active.players.contains(player.getUniqueId()) || active.aliveHiders.contains(player.getUniqueId()) || player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage(PREFIX + ChatColor.RED + "Spectator cycling is available after you are eliminated.");
            return;
        }
        List<UUID> targets = new ArrayList<>(active.aliveHiders);
        if (targets.isEmpty()) { player.sendMessage(PREFIX + "No active hiders to spectate."); return; }
        int index = (spectatorTargets.getOrDefault(player.getUniqueId(), -1) + 1) % targets.size();
        spectatorTargets.put(player.getUniqueId(), index);
        Player target = Bukkit.getPlayer(targets.get(index));
        if (target != null) {
            player.setSpectatorTarget(target);
            player.sendMessage(PREFIX + ChatColor.AQUA + "Spectating " + target.getName() + " • /mobhunt spectate to switch");
        }
    }

    private void openAbilityGui(Player player) {
        Match active = practiceSessions.getOrDefault(player.getUniqueId(), match);
        if (active == null || (!practiceSessions.containsKey(player.getUniqueId()) && !active.aliveHiders.contains(player.getUniqueId()))) return;
        String mob = active.mobForms.getOrDefault(player.getUniqueId(), "PIG");
        long remain = Math.max(0L, active.abilityReadyAt.getOrDefault(player.getUniqueId(), 0L) - System.currentTimeMillis());
        Inventory inv = Bukkit.createInventory(null, 27, ABILITY_TITLE);
        inv.setItem(11, named(Material.NETHER_STAR, ChatColor.GREEN + "Use " + abilityName(mob), "Mob: " + pretty(mob), remain == 0 ? "Ready now!" : "Cooldown: " + (remain / 1000.0) + "s"));
        inv.setItem(15, named(Material.CLOCK, ChatColor.AQUA + "Cooldown Status", remain == 0 ? "Ability ready" : "Available in " + (remain / 1000.0) + " seconds"));
        player.openInventory(inv);
    }
    private void useMobAbility(Player player, Match active) {
        UUID id = player.getUniqueId();
        String mob = active.mobForms.getOrDefault(id, "PIG");
        long now = System.currentTimeMillis(), readyAt = active.abilityReadyAt.getOrDefault(id, 0L);
        if (now < readyAt) { player.sendMessage(PREFIX + ChatColor.RED + "Ability cooldown: " + ((readyAt - now + 999) / 1000) + "s."); openAbilityGui(player); return; }
        active.abilityReadyAt.put(id, now + abilityCooldown(mob));
        Player hunter = Bukkit.getPlayer(active.hunter);
        switch (mob) {
            case "WARDEN" -> {
                Location start = player.getEyeLocation();
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.2f, 0.8f);
                if (hunter != null && hunter.getWorld().equals(world)
                        && hunter.getLocation().distanceSquared(player.getLocation()) <= 100) {
                    Location end = hunter.getEyeLocation();
                    org.bukkit.util.Vector line = end.toVector().subtract(start.toVector());
                    double length = line.length();
                    if (length > 0.01) {
                        org.bukkit.util.Vector step = line.normalize().multiply(0.65);
                        Location point = start.clone();
                        for (double d = 0; d < length; d += 0.65) {
                            player.getWorld().spawnParticle(org.bukkit.Particle.SONIC_BOOM, point, 1, 0, 0, 0, 0);
                            point.add(step);
                        }
                    }
                    hunter.damage(6.0, player);
                    org.bukkit.util.Vector push = hunter.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (push.lengthSquared() > 0.001) hunter.setVelocity(push.normalize().multiply(0.8).setY(0.25));
                    hunter.getWorld().spawnParticle(org.bukkit.Particle.SONIC_BOOM, end, 1);
                }
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
                player.getWorld().spawnParticle(org.bukkit.Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 18, 0.45, 0.5, 0.45, 0.03);
            }
            case "CREEPER" -> {
                player.getWorld().createExplosion(player.getLocation(), 0f, false, false);
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 25) hunter.damage(5.0, player);
            }
            case "GHAST" -> { Fireball fireball = player.launchProjectile(Fireball.class); fireball.setIsIncendiary(false); fireball.setYield(0f); }
            case "BEE" -> {
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 16) {
                    hunter.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 0)); hunter.damage(2.0, player);
                }
            }
            case "SPIDER", "CAVE_SPIDER" -> { player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 2)); player.setVelocity(player.getLocation().getDirection().multiply(1.1).setY(0.35)); }
            case "SLIME", "MAGMA_CUBE", "FROG", "RABBIT" -> {
                player.setVelocity(player.getLocation().getDirection().multiply(0.8).setY(0.9));
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 9) hunter.damage(3.0, player);
            }
            case "WOLF", "FOX", "CAT" -> {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 20, 0.45, 0.2, 0.45, 0.04);
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 9) hunter.damage(3.0, player);
            }
            case "IRON_GOLEM" -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, player.getLocation().add(0, 0.2, 0),
                        35, 0.7, 0.15, 0.7, Material.IRON_BLOCK.createBlockData());
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.8f);
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 25) {
                    hunter.damage(5.0, player);
                    org.bukkit.util.Vector push = hunter.getLocation().toVector().subtract(player.getLocation().toVector());
                    if (push.lengthSquared() > 0.001) hunter.setVelocity(push.normalize().multiply(0.65).setY(0.35));
                }
            }
            case "VILLAGER", "ALLAY" -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 24, 0.6, 0.7, 0.6, 0.05);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                player.setVelocity(player.getLocation().getDirection().multiply(0.85).setY(0.25));
            }
            case "BLAZE" -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.FLAME, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.02);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 25) {
                    hunter.damage(4.0, player);
                    hunter.setFireTicks(30);
                }
            }
            case "HORSE", "DONKEY", "MULE", "LLAMA", "STRIDER" -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 25, 0.45, 0.15, 0.45, 0.05);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2));
                player.setVelocity(player.getLocation().getDirection().multiply(1.25).setY(0.3));
            }
            default -> {
                player.getWorld().spawnParticle(org.bukkit.Particle.CRIT, player.getLocation().add(0, 1, 0), 18, 0.45, 0.5, 0.45, 0.1);
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 9) hunter.damage(3.0, player);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1));
            }
        }
        if (!mob.equals("WARDEN")) {
            if (plugin.getConfig().getBoolean("mobhunt.player-settings." + id + ".particles", true)) player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT, player.getLocation().add(0, 1, 0), 18, 0.5, 0.6, 0.5, 0.25);
            player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.3f);
        }
        if (plugin.getConfig().getBoolean("mobhunt.player-settings." + id + ".sounds", true)) player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.55f, 1.4f);
        player.sendMessage(PREFIX + ChatColor.GREEN + abilityName(mob) + " used!");
    }
    private boolean usesSwapHands(Player player) {
        return plugin.getConfig().getString("mobhunt.player-settings." + player.getUniqueId() + ".trigger", "RIGHT_CLICK")
                .equalsIgnoreCase("SWAP_HANDS");
    }

    @EventHandler
    public void onAbilityKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Match active = practiceSessions.getOrDefault(player.getUniqueId(), match);
        if (active == null || (!practiceSessions.containsKey(player.getUniqueId()) && !active.aliveHiders.contains(player.getUniqueId())) || !usesSwapHands(player)) return;
        event.setCancelled(true);
        useMobAbility(player, active);
    }

    @EventHandler
    public void onAbilityItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        Player player = event.getPlayer();
        Match active = practiceSessions.getOrDefault(player.getUniqueId(), match);
        if (active == null || (!practiceSessions.containsKey(player.getUniqueId()) && !active.aliveHiders.contains(player.getUniqueId()))
                || !event.getAction().isRightClick() || usesSwapHands(player)) return;
        boolean blankHand = item == null || item.getType().isAir();
        boolean abilityItem = item != null && item.hasItemMeta()
                && item.getItemMeta().getDisplayName() != null
                && ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Mob Ability");
        if (!blankHand && !abilityItem) return;
        event.setCancelled(true);
        useMobAbility(player, active);
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

    private void updateProximityWarnings(Match active) {
        Player hunter = Bukkit.getPlayer(active.hunter);
        if (hunter == null || !hunter.isOnline() || !hunter.getWorld().equals(world)) return;
        for (UUID id : active.aliveHiders) {
            Player hider = Bukkit.getPlayer(id);
            if (hider == null || !hider.isOnline() || !hider.getWorld().equals(world)) continue;
            double distance = hunter.getLocation().distanceSquared(hider.getLocation());
            if (distance <= 12.25) {
                hider.sendActionBar(ChatColor.RED + "" + ChatColor.BOLD + "HUNTER VERY CLOSE!");
                if (plugin.getConfig().getBoolean("mobhunt.player-settings." + id + ".sounds", true))
                    hider.playSound(hider.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.35f, 0.65f);
            } else if (distance <= 100) hider.sendActionBar(ChatColor.GOLD + "Hunter is nearby...");
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
            incrementStat(active.hunter, "roundWins", 1);
            broadcastToMatch(active, ChatColor.GOLD + nameOf(active.hunter) + " eliminated every hider and wins the round!");
        } else {
            for (UUID id : active.aliveHiders) { active.scores.merge(id, 1, Integer::sum); incrementStat(id, "roundWins", 1); }
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
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (match == active && !active.ending && active.roundEnded) startRound(active);
            }, 100L);
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
        for (UUID id : active.players) incrementStat(id, "matchesPlayed", 1);
        for (UUID id : winners) incrementStat(id, "matchWins", 1);
        broadcastToMatch(active, ChatColor.GOLD + "" + ChatColor.BOLD + "MOB HUNT WINNER: "
                + ChatColor.YELLOW + (winnerNames.isBlank() ? "No winner" : winnerNames)
                + ChatColor.GRAY + " • " + high + " point(s)");
        for (UUID id : active.players) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) continue;
            DisguiseAPI.undisguiseToAll(player);
            // Clear all temporary match state before restoring the lobby snapshot.
            for (PotionEffect effect : player.getActivePotionEffects()) player.removePotionEffect(effect.getType());
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setWalkSpeed(0.2f);
            player.setFlySpeed(0.1f);
            if (plugin instanceof NebulaCore core) {
                // Use the network's single lobby-return path so every player gets
                // the same compass/clock loadout and refreshed HUD after every match.
                core.returnPlayerToLobby(player);
            } else {
                PlayerSnapshot snapshot = active.snapshots.get(id);
                if (snapshot != null) snapshot.restoreAtLobby(player, lobby, plugin);
                else player.teleport(lobby);
                player.updateInventory();
            }
            player.sendTitle(ChatColor.GOLD + "MOB HUNT OVER",
                    ChatColor.GRAY + "Winner: " + (winnerNames.isBlank() ? "No winner" : winnerNames), 5, 70, 15);
        }
        active.ending = true;
        match = null;
        regenerateVillageArena();
    }

    private void regenerateVillageArena() {
        if (world == null) return;
        // Players have already been returned to the lobby. Recreate the 11x11
        // chunk arena from its fixed world seed, restoring broken/placed blocks.
        List<int[]> chunks = new ArrayList<>();
        int centerChunkX = arenaCenterX >> 4;
        int centerChunkZ = arenaCenterZ >> 4;
        for (int x = centerChunkX - 5; x <= centerChunkX + 5; x++) {
            for (int z = centerChunkZ - 5; z <= centerChunkZ + 5; z++) {
                chunks.add(new int[]{x, z});
            }
        }
        new BukkitRunnable() {
            private int index = 0;
            @Override public void run() {
                if (world == null || !world.getPlayers().isEmpty()) {
                    cancel();
                    return;
                }
                if (index >= chunks.size()) {
                    cancel();
                    plugin.getLogger().info("Mob Hunt village arena regenerated from its fixed seed.");
                    return;
                }
                int[] chunk = chunks.get(index++);
                world.regenerateChunk(chunk[0], chunk[1]);
            }
        }.runTaskTimer(plugin, 2L, 1L);
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
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = arenaCenterX + random.nextInt(73) - 36;
            int z = arenaCenterZ + random.nextInt(73) - 36;
            int y = world.getHighestBlockYAt(x, z) + 1;
            Block floor = world.getBlockAt(x, y - 1, z);
            if (floor.getType().isSolid() && !floor.isLiquid()
                    && world.getBiome(x, y, z).name().equals("PLAINS")
                    && world.getBlockAt(x, y, z).isEmpty()
                    && world.getBlockAt(x, y + 1, z).isEmpty()) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }
        return new Location(world, arenaCenterX + 0.5, arenaBaseY + 1.0, arenaCenterZ + 0.5);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        if (title.equals(SETTINGS_TITLE)) {
            event.setCancelled(true);
            String base = "mobhunt.player-settings." + player.getUniqueId() + ".";
            if (event.getRawSlot() == 11) plugin.getConfig().set(base + "particles", !plugin.getConfig().getBoolean(base + "particles", true));
            else if (event.getRawSlot() == 13) plugin.getConfig().set(base + "trigger",
                    usesSwapHands(player) ? "RIGHT_CLICK" : "SWAP_HANDS");
            else if (event.getRawSlot() == 15) plugin.getConfig().set(base + "sounds", !plugin.getConfig().getBoolean(base + "sounds", true));
            else if (event.getRawSlot() == 22) { player.closeInventory(); return; }
            plugin.saveConfig();
            openSettings(player);
            return;
        }
        if (title.equals(STATS_TITLE) || title.equals(ChatColor.DARK_PURPLE + "Mob Hunt Practice")) { event.setCancelled(true); return; }
        if (!title.equals(MENU_TITLE)) return;
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
    public void onSpiderWallClimb(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Match active = practiceSessions.getOrDefault(player.getUniqueId(), match);
        if (active == null || active.roundEnded || !active.aliveHiders.contains(player.getUniqueId())) return;
        String mob = active.mobForms.get(player.getUniqueId());
        if (!"SPIDER".equals(mob) && !"CAVE_SPIDER".equals(mob)) return;
        Block feet = player.getLocation().getBlock();
        boolean touchingWall = false;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            if (feet.getRelative(face).getType().isSolid()
                    || feet.getRelative(face).getRelative(BlockFace.UP).getType().isSolid()) {
                touchingWall = true;
                break;
            }
        }
        if (!touchingWall) return;
        org.bukkit.util.Vector velocity = player.getVelocity();
        if (velocity.getY() < 0.22) velocity.setY(0.22);
        player.setVelocity(velocity);
        if (random.nextInt(4) == 0) {
            player.getWorld().spawnParticle(org.bukkit.Particle.BLOCK,
                    player.getLocation().add(0, 0.7, 0), 3, 0.2, 0.25, 0.2,
                    feet.getRelative(BlockFace.DOWN).getBlockData());
        }
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
        if (killer != null && active.players.contains(killer.getUniqueId())) incrementStat(killer.getUniqueId(), "eliminations", 1);
        UUID id = player.getUniqueId();
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
        event.setRespawnLocation(randomVillageSpawn());
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (match != active || active.ending) return;
            player.closeInventory();
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            player.getInventory().setItemInOffHand(null);
            if (match != active || active.roundEnded || active.ending) return;
            if (player.getUniqueId().equals(active.hunter)) {
                player.setGameMode(GameMode.SURVIVAL);
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
        if (practiceSessions.remove(id) != null) practiceSnapshots.remove(id);
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

        // Use a genuine vanilla-generated village in a normal overworld, not a
        // plugin-built collection of placeholder houses. The workflow recreates
        // this world from the fixed seed on each server start.
        Location origin = new Location(world, 0, 80, 0);
        // Plains only: avoid snowy/taiga/desert villages for this arena.
        List<String> villageKeys = List.of("village_plains");
        Location village = null;
        double nearestDistance = Double.MAX_VALUE;
        for (String key : villageKeys) {
            Structure structure = Registry.STRUCTURE.get(NamespacedKey.minecraft(key));
            if (structure == null) continue;
            StructureSearchResult result = world.locateNearestStructure(origin, structure, 64, false);
            if (result == null) continue;
            Location candidate = result.getLocation();
            double distance = candidate.distanceSquared(origin);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                village = candidate;
            }
        }
        if (village == null) {
            throw new IllegalStateException("No vanilla village found near the Mob Hunt seed; refusing to build a fake village.");
        }
        arenaCenterX = village.getBlockX();
        arenaCenterZ = village.getBlockZ();
        world.getChunkAt(arenaCenterX >> 4, arenaCenterZ >> 4).load(true);
        arenaBaseY = world.getHighestBlockYAt(arenaCenterX, arenaCenterZ);
        world.setSpawnLocation(arenaCenterX, arenaBaseY + 1, arenaCenterZ);

        WorldBorder border = world.getWorldBorder();
        border.setCenter(arenaCenterX + 0.5, arenaCenterZ + 0.5);
        border.setSize(160.0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(0.0);
        border.setWarningDistance(8);
        plugin.getLogger().info("Using naturally generated Minecraft village at "
                + arenaCenterX + ", " + arenaCenterZ + " in seed " + world.getSeed());
    }

    private static final class Match {
        String arenaName = "Village";
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
        int hunterLives = HUNTER_LIVES;
        BukkitTask mobPickTask;
        long mobPickEndsAt;
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

        void restoreAtLobby(Player player, Location lobby, JavaPlugin plugin) {
            player.closeInventory();
            player.teleport(lobby);
            // Restore on the next tick so lobby/world listeners cannot overwrite
            // the saved hotbar while the teleport event is still being processed.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
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
            });
        }

        private static ItemStack[] cloneItems(ItemStack[] input) {
            ItemStack[] output = new ItemStack[input.length];
            for (int i = 0; i < input.length; i++) output[i] = input[i] == null ? null : input[i].clone();
            return output;
        }
    }
}
