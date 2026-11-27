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
    private int arenaCenterX;
    private int arenaCenterZ;
    private final Set<UUID> searchingMob = new LinkedHashSet<>();

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
        Player hunter = Bukkit.getPlayer(active.hunter);
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
                        p.sendActionBar(ChatColor.LIGHT_PURPLE + "ROUND " + active.round + "/" + ROUNDS
                                + ChatColor.GRAY + " • " + ChatColor.WHITE + active.secondsLeft + "s left"
                                + ChatColor.GRAY + " • Hiders: " + ChatColor.AQUA + active.aliveHiders.size()
                                + ChatColor.GRAY + " • Hunter lives: " + ChatColor.RED + active.hunterLives);
                    }
                }
                updateHunterCompass(active);
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
            if (!filter.isBlank() && !mob.toLowerCase().contains(filter.toLowerCase())) continue;
            Material icon;
            try { icon = Material.valueOf(mob + "_SPAWN_EGG"); } catch (IllegalArgumentException ex) { icon = Material.PAPER; }
            if (slot >= 45) break;
            inv.setItem(slot++, named(icon, ChatColor.GREEN + pretty(mob), "Click to choose this mob", "Mob abilities apply automatically"));
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
            Match active = match;
            Player player = Bukkit.getPlayer(id);
            if (player != null && active != null && active.mobPickPending.contains(id)) {
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
            case "WOLF", "FOX", "CAT" -> "Pounce"; default -> "Mob Strike";
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
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 100) {
                    hunter.damage(6.0, player);
                    hunter.setVelocity(hunter.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(0.8));
                }
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1f);
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
                if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 9) hunter.damage(3.0, player);
            }
            default -> { if (hunter != null && hunter.getWorld().equals(world) && hunter.getLocation().distanceSquared(player.getLocation()) <= 9) hunter.damage(3.0, player); player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1)); }
        }
        player.sendMessage(PREFIX + ChatColor.GREEN + abilityName(mob) + " used!");
    }
    private boolean usesSwapHands(Player player) {
        return plugin.getConfig().getString("player-settings." + player.getUniqueId() + ".mobhunt-ability-trigger", "RIGHT_CLICK")
                .equalsIgnoreCase("SWAP_HANDS");
    }

    @EventHandler
    public void onAbilityKey(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        Match active = match;
        if (active == null || !active.aliveHiders.contains(player.getUniqueId()) || !usesSwapHands(player)) return;
        event.setCancelled(true);
        useMobAbility(player, active);
    }

    @EventHandler
    public void onAbilityItem(PlayerInteractEvent event) {
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta() || item.getItemMeta().getDisplayName() == null
                || !ChatColor.stripColor(item.getItemMeta().getDisplayName()).equals("Mob Ability")) return;
        Match active = match;
        if (active == null || !active.aliveHiders.contains(event.getPlayer().getUniqueId())) return;
        event.setCancelled(true);
        if (!event.getAction().isRightClick()) return;
        if (!usesSwapHands(event.getPlayer())) useMobAbility(event.getPlayer(), active);
        else openAbilityGui(event.getPlayer());
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
            broadcastToMatch(active, ChatColor.GOLD + nameOf(active.hunter) + " eliminated every hider and wins the round!");
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
