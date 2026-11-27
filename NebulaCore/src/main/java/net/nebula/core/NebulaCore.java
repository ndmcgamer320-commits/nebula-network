package net.nebula.core;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.io.File;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.entity.EntityPickupItemEvent;
import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;
import org.bukkit.Particle;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.java.JavaPlugin;

public final class NebulaCore extends JavaPlugin implements Listener {
    private Location lobby;
    private final Map<UUID, Boolean> playersHidden = new HashMap<>();
    private final Random random = new Random();
    private NebulaGames games;
    private NebulaHud hud;
    private NebulaNetworkFeatures networkFeatures;
    private MobHuntGame mobHunt;
    private final Map<UUID, UUID> lastMessaged = new HashMap<>();
    private File lastSurvivalFile;
    private YamlConfiguration lastSurvival;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private static final String MENU_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA NETWORK";
    private static final String SETTINGS_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA SETTINGS";

    @Override
    public void onDisable() {
        if (mobHunt != null) mobHunt.shutdown();
    }

    @Override
    public void onEnable() {
        // Multiverse-Core creates and manages a dedicated empty lobby world.
        World lobbyWorld = Bukkit.getWorld("lobby");
        if (lobbyWorld == null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create lobby normal -g VoidWorldGenerator");
            lobbyWorld = Bukkit.getWorld("lobby");
        }
        if (lobbyWorld == null) {
            getLogger().severe("Multiverse could not create the void lobby. Check Multiverse-Core and VoidWorldGenerator.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        lobby = loadLobby();
        for (String name : List.of("survival_1", "survival_2", "bedwars_1", "bedwars_2",
                "pillars_1", "pillars_2", "duel_arena")) {
            World world = Bukkit.getWorld(name);
            if (world == null) world = Bukkit.createWorld(new WorldCreator(name));
            if (world != null) world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        }

        lastSurvivalFile = new File(getDataFolder(), "last-survival.yml");
        if (lastSurvivalFile.exists()) lastSurvival = YamlConfiguration.loadConfiguration(lastSurvivalFile);
        else lastSurvival = new YamlConfiguration();

        buildLobby();
        applyLobbyBorder();
        Bukkit.getPluginManager().registerEvents(this, this);
        games = new NebulaGames(this, lobby);
        games.enable();
        hud = new NebulaHud(this, lobby);
        hud.enable();
        networkFeatures = new NebulaNetworkFeatures(this);
        networkFeatures.enable();
        if (Bukkit.getPluginManager().isPluginEnabled("LibsDisguises")) {
            mobHunt = new MobHuntGame(this, lobby);
            mobHunt.enable();
        } else {
            mobHunt = null;
            getLogger().severe("Mob Hunt is disabled because LibsDisguises did not load. The network lobby and other games will continue.");
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            regenerateArena(Bukkit.getWorld("bedwars_1"), "bedwars_1");
            regenerateArena(Bukkit.getWorld("duel_arena"), "duel_arena");
            getLogger().info("Multiverse void lobby and separate game worlds are ready.");
        }, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) hud.refreshPlayer(p);
        getLogger().info("NebulaCore enabled with AuthMe authentication, Multiverse worlds and rank HUD.");
    }

    private void buildLobby() {
        if (lobby == null || lobby.getWorld() == null) return;
        World world = lobby.getWorld();
        int cx = lobby.getBlockX(), cz = lobby.getBlockZ();
        int y = Math.max(world.getMinHeight() + 20, lobby.getBlockY() - 1);
        for (int x = -44; x <= 44; x++) {
            for (int z = -44; z <= 44; z++) {
                if (x*x + z*z > 44*44) continue;
                world.getBlockAt(cx+x, y, cz+z).setType(Math.sqrt(x*x+z*z)>41 ? Material.MOSS_BLOCK : Material.GRASS_BLOCK, false);
                world.getBlockAt(cx+x, y-1, cz+z).setType(Material.DIRT, false);
                world.getBlockAt(cx+x, y-2, cz+z).setType(Material.COARSE_DIRT, false);
                world.getBlockAt(cx+x, y-3, cz+z).setType(Material.STONE, false);
                world.getBlockAt(cx+x, y-4, cz+z).setType(Material.DEEPSLATE, false);
                for (int h=1; h<=10; h++) world.getBlockAt(cx+x, y+h, cz+z).setType(Material.AIR, false);
            }
        }
        // A circular quartz-and-stone spawn plaza with four paths.
        for (int x=-12; x<=12; x++) for (int z=-12; z<=12; z++) {
            if (x*x+z*z<=12*12) world.getBlockAt(cx+x,y,cz+z).setType(((x+z)&1)==0?Material.QUARTZ_BLOCK:Material.POLISHED_DIORITE,false);
        }
        for (int i=-38;i<=38;i++) for (int lane=-2;lane<=2;lane++) {
            if (Math.abs(i)<=12) continue;
            world.getBlockAt(cx+i,y,cz+lane).setType(lane==0?Material.SMOOTH_STONE:Material.STONE_BRICKS,false);
            world.getBlockAt(cx+lane,y,cz+i).setType(lane==0?Material.SMOOTH_STONE:Material.STONE_BRICKS,false);
        }
        for (int x=-13;x<=13;x++) for (int z=-13;z<=13;z++) {
            int d=x*x+z*z;
            if (d>=11*11&&d<=13*13) world.getBlockAt(cx+x,y,cz+z).setType(((x+z)&1)==0?Material.PURPLE_CONCRETE:Material.CYAN_CONCRETE,false);
        }
        // Bright center marker and decorative tree canopy ring.
        world.getBlockAt(cx,y+1,cz).setType(Material.SEA_LANTERN,false);
        world.getBlockAt(cx,y+2,cz).setType(Material.AMETHYST_BLOCK,false);
        world.getBlockAt(cx,y+3,cz).setType(Material.END_ROD,false);
        int[][] trees={{-30,-24},{-24,30},{30,24},{24,-30},{-36,0},{36,0},{0,-36},{0,36}};
        for (int[] t:trees) {
            int tx=cx+t[0], tz=cz+t[1];
            for(int h=1;h<=5;h++) world.getBlockAt(tx,y+h,tz).setType(Material.OAK_LOG,false);
            for(int dx=-3;dx<=3;dx++) for(int dz=-3;dz<=3;dz++) for(int h=3;h<=7;h++)
                if(dx*dx+dz*dz+(h-5)*(h-5)<=13) world.getBlockAt(tx+dx,y+h,tz+dz).setType(Material.OAK_LEAVES,false);
            world.getBlockAt(tx,y+8,tz).setType(Material.GLOWSTONE,false);
        }
        world.setSpawnLocation(cx,y+1,cz);
        lobby=new Location(world,cx+0.5,y+1.0,cz+0.5);
    }

    private void applyLobbyBorder() {
        if (lobby==null||lobby.getWorld()==null) return;
        org.bukkit.WorldBorder border=lobby.getWorld().getWorldBorder();
        border.setCenter(lobby.getX(),lobby.getZ());
        border.setSize(84.0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(2.0);
        border.setWarningDistance(5);
    }

    private Location loadLobby() {
        World world = Bukkit.getWorld("lobby");
        if (world == null) world = Bukkit.getWorlds().get(0);
        if (getConfig().getString("lobby.world", "lobby").equals("lobby") && getConfig().contains("lobby.x")) {
            return new Location(world, getConfig().getDouble("lobby.x"), getConfig().getDouble("lobby.y"),
                    getConfig().getDouble("lobby.z"), (float) getConfig().getDouble("lobby.yaw"),
                    (float) getConfig().getDouble("lobby.pitch"));
        }
        Location spawn = world.getSpawnLocation();
        return new Location(world, spawn.getBlockX() + 0.5, spawn.getBlockY() + 1.0,
                spawn.getBlockZ() + 0.5, spawn.getYaw(), spawn.getPitch());
    }

    private boolean inLobby(Location loc) {
        if (lobby == null || loc.getWorld() == null || !loc.getWorld().equals(lobby.getWorld())) return false;
        return loc.distanceSquared(lobby) <= 10000;
    }

    private void updateTab(Player p) {
        String header = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ NEBULA NETWORK ✦\n"
                + ChatColor.LIGHT_PURPLE + "Survival  •  BedWars  •  Lucky Pillars  •  Duels";
        String footer = ChatColor.GRAY + "Online: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size()
                + ChatColor.DARK_GRAY + "  |  " + ChatColor.LIGHT_PURPLE + "play.nebula.network";
        p.setPlayerListHeaderFooter(header, footer);
        p.setPlayerListName(ChatColor.LIGHT_PURPLE + p.getName());
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        e.setJoinMessage(null);
        waitForAuthentication(player);
    }

    private void waitForAuthentication(Player player) {
        new BukkitRunnable() {
            private int waitedTicks = 0;
            @Override public void run() {
                if (!player.isOnline()) { cancel(); return; }
                AuthMeApi authMe = AuthMeApi.getInstance();
                if (authMe != null && authMe.isAuthenticated(player)) {
                    boolean resumedMobHunt = mobHunt != null && mobHunt.handleReconnect(player);
                    if (!resumedMobHunt) returnPlayerToLobby(player);
                    else if (hud != null) hud.refreshPlayer(player);
                    player.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Welcome to Nebula Network! " + ChatColor.GRAY + "Use /menu to choose a game.");
                    Bukkit.broadcastMessage(PREFIX + ChatColor.WHITE + player.getName() + ChatColor.GRAY + " joined the network.");
                    cancel();
                    return;
                }
                waitedTicks += 5;
                if (waitedTicks >= 1800) cancel();
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    public void returnPlayerToLobby(Player player) {
        if (player == null || !player.isOnline()) return;
        player.teleport(lobby);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline() || !player.getWorld().equals(lobby.getWorld())) return;
            giveLobbyLoadout(player);
            if (hud != null) {
                hud.refreshPlayer(player);
                hud.setNameTagsVisible(player, getPlayerSetting(player, "nametags", true));
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    hud.setNameTagsVisible(viewer, getPlayerSetting(viewer, "nametags", true));
                }
            }
            if (getPlayerSetting(player, "lobby-particles", true)) {
                player.spawnParticle(Particle.END_ROD, player.getLocation().clone().add(0, 1.0, 0), 12, 0.35, 0.5, 0.35, 0.01);
            }
        }, 2L);
    }

    private boolean getPlayerSetting(Player player, String key, boolean defaultValue) {
        return getConfig().getBoolean("player-settings." + player.getUniqueId() + "." + key, defaultValue);
    }

    private void setPlayerSetting(Player player, String key, boolean value) {
        getConfig().set("player-settings." + player.getUniqueId() + "." + key, value);
        saveConfig();
    }

    private void playSettingsSound(Player player) {
        if (getPlayerSetting(player, "menu-sounds", true)) {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.65f, 1.2f);
        }
    }

    private void giveLobbyLoadout(Player player) {
        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        player.getInventory().setItemInOffHand(null);
        player.setGameMode(org.bukkit.GameMode.ADVENTURE);
        player.getInventory().setItem(4, item(Material.COMPASS, "Game Selector",
                List.of("Right-click to choose a game", "Use /menu anytime")));
        player.getInventory().setItem(8, item(Material.CLOCK, "Settings",
                List.of("Right-click to customize your lobby")));
        player.updateInventory();
    }

    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        if (player.getWorld().equals(lobby.getWorld())) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                AuthMeApi authMe = AuthMeApi.getInstance();
                if (player.isOnline() && authMe != null && authMe.isAuthenticated(player)
                        && player.getWorld().equals(lobby.getWorld())) {
                    giveLobbyLoadout(player);
                    if (hud != null) {
                        hud.refreshPlayer(player);
                        hud.setNameTagsVisible(player, getPlayerSetting(player, "nametags", true));
                    }
                }
            }, 2L);
        }
        scheduleArenaRegeneration(e.getFrom());
    }

    @EventHandler public void onPlayerTeleport(PlayerTeleportEvent e) {
        if (e.getTo() == null || e.getFrom().getWorld().equals(e.getTo().getWorld())) return;
        saveSurvivalLocation(e.getPlayer(), e.getFrom());
    }

    private boolean isSurvivalWorld(World world) {
        return world != null && (world.getName().equals("survival_1") || world.getName().equals("survival_2"));
    }

    private void saveSurvivalLocation(Player player, Location location) {
        World world = location.getWorld();
        if (!isSurvivalWorld(world)) return;
        String path = player.getUniqueId() + "." + world.getName();
        lastSurvival.set(path + ".x", location.getX());
        lastSurvival.set(path + ".y", location.getY());
        lastSurvival.set(path + ".z", location.getZ());
        lastSurvival.set(path + ".yaw", location.getYaw());
        lastSurvival.set(path + ".pitch", location.getPitch());
        try {
            lastSurvival.save(lastSurvivalFile);
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not save last Survival location for " + player.getName() + ": " + ex.getMessage());
        }
    }

    private void saveSurvivalLocation(Player player, World world) {
        if (isSurvivalWorld(world)) saveSurvivalLocation(player, player.getLocation());
    }

    private Location getLastSurvivalLocation(Player player, World world) {
        String path = player.getUniqueId() + "." + world.getName();
        if (!lastSurvival.contains(path + ".x")) return world.getSpawnLocation();
        return new Location(world, lastSurvival.getDouble(path + ".x"), lastSurvival.getDouble(path + ".y"),
                lastSurvival.getDouble(path + ".z"), (float) lastSurvival.getDouble(path + ".yaw"),
                (float) lastSurvival.getDouble(path + ".pitch"));
    }

    @EventHandler public void onArenaQuit(PlayerQuitEvent e) {
        saveSurvivalLocation(e.getPlayer(), e.getPlayer().getWorld());
        scheduleArenaRegeneration(e.getPlayer().getWorld());
    }

    @EventHandler public void onLobbyInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player) || !inLobby(player.getLocation())) return;
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING
                && !player.hasPermission("nebulacore.admin")) e.setCancelled(true);
    }

    @EventHandler public void onLobbySwapHand(PlayerSwapHandItemsEvent e) {
        if (inLobby(e.getPlayer().getLocation())) e.setCancelled(true);
    }

    @EventHandler public void onLobbyPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player player && inLobby(player.getLocation())) e.setCancelled(true);
    }

    private void scheduleArenaRegeneration(World departed) {
        String name = departed.getName();
        if (!name.equals("bedwars_1") && !name.equals("duel_arena")) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!departed.getPlayers().isEmpty()) return;
            regenerateArena(departed, name);
            getLogger().info("Automatically regenerated empty arena: " + name);
        }, 20L);
    }

    @EventHandler public void onMobSpawn(CreatureSpawnEvent e) {
        String worldName = e.getLocation().getWorld().getName();
        boolean protectedZone = inLobby(e.getLocation()) || worldName.equals("bedwars_1") || worldName.equals("duel_arena");
        if (protectedZone && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler public void onLobbyBoundaryMove(org.bukkit.event.player.PlayerMoveEvent e) {
        Location to = e.getTo();
        if (to == null || lobby == null || to.getWorld() == null || !to.getWorld().equals(lobby.getWorld())) return;
        double dx = to.getX() - lobby.getX();
        double dz = to.getZ() - lobby.getZ();
        double safeRadius = 41.5;
        if (dx * dx + dz * dz > safeRadius * safeRadius) {
            e.setTo(e.getFrom());
            if (e.getFrom().distanceSquared(lobby) <= safeRadius * safeRadius) {
                e.getPlayer().sendActionBar(ChatColor.RED + "You cannot leave the lobby island.");
            }
        }
    }

    @EventHandler public void onBreak(BlockBreakEvent e) {
        if (inLobby(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("nebulacore.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + "The lobby is protected.");
        }
    }

    @EventHandler public void onPlace(BlockPlaceEvent e) {
        if (inLobby(e.getBlock().getLocation()) && !e.getPlayer().hasPermission("nebulacore.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(PREFIX + "The lobby is protected.");
        }
    }

    @EventHandler public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && inLobby(p.getLocation())) e.setCancelled(true);
    }

    @EventHandler public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && inLobby(p.getLocation())) e.setCancelled(true);
    }

    @EventHandler public void onDrop(PlayerDropItemEvent e) {
        if (inLobby(e.getPlayer().getLocation())) e.setCancelled(true);
    }

    @EventHandler public void onInteract(PlayerInteractEvent e) {
        if (inLobby(e.getPlayer().getLocation()) && e.getItem() != null
                && (e.getItem().getType() == Material.COMPASS || e.getItem().getType() == Material.CLOCK)
                && (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            if (e.getItem().getType() == Material.COMPASS) openMenu(e.getPlayer());
            else openSettings(e.getPlayer());
            return;
        }
        if (inLobby(e.getPlayer().getLocation()) && e.getClickedBlock() != null
                && !e.getPlayer().hasPermission("nebulacore.admin")) e.setCancelled(true);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE + name);
        meta.setLore(lore.stream().map(s -> ChatColor.GRAY + s).toList());
        stack.setItemMeta(meta);
        return stack;
    }

    private void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, MENU_TITLE);
        inv.setItem(10, item(Material.GRASS_BLOCK, "Survival Map 1", List.of("Click to enter Survival I")));
        inv.setItem(11, item(Material.MOSS_BLOCK, "Survival Map 2", List.of("Click to enter Survival II")));
        inv.setItem(13, item(Material.RED_BED, "BedWars", List.of("Queue for 1v1, 2v2 or 3v3", "Speak to the BedWars NPC")));
        inv.setItem(14, item(Material.IRON_SWORD, "CPvP Duels", List.of("Player queue or practice bot", "Custom layered grass arena")));
        inv.setItem(15, item(Material.GOLD_BLOCK, "Lucky Pillars", List.of("Arena setup required before matches")));
        inv.setItem(16, item(Material.ZOMBIE_HEAD, "Mob Hunt", List.of("Hide as a random mob in a village", "3 rounds • Random hunter each round")));
        inv.setItem(22, item(Material.NETHER_STAR, "Lobby", List.of("Return to the protected lobby")));
        player.openInventory(inv);
    }

    private void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, SETTINGS_TITLE);
        boolean nameTags = getPlayerSetting(player, "nametags", true);
        boolean hidden = playersHidden.getOrDefault(player.getUniqueId(), false);
        boolean particles = getPlayerSetting(player, "lobby-particles", true);
        boolean sounds = getPlayerSetting(player, "menu-sounds", true);
        boolean sidebar = getPlayerSetting(player, "scoreboard", true);
        boolean privateMessages = getPlayerSetting(player, "private-messages", true);
        inv.setItem(10, item(nameTags ? Material.NAME_TAG : Material.PAPER,
                "Player Name Tags: " + (nameTags ? "ON" : "OFF"), List.of("Show or hide player names above heads")));
        inv.setItem(11, item(hidden ? Material.GRAY_DYE : Material.LIME_DYE,
                "Player Visibility: " + (hidden ? "OFF" : "ON"), List.of("Click to toggle other players")));
        inv.setItem(12, item(particles ? Material.FIREWORK_STAR : Material.GUNPOWDER,
                "Lobby Particles: " + (particles ? "ON" : "OFF"), List.of("Toggle decorative lobby particles")));
        inv.setItem(13, item(sounds ? Material.NOTE_BLOCK : Material.BARRIER,
                "Menu Sounds: " + (sounds ? "ON" : "OFF"), List.of("Toggle menu click sounds")));
        inv.setItem(14, item(sidebar ? Material.MAP : Material.PAPER,
                "Sidebar Scoreboard: " + (sidebar ? "ON" : "OFF"), List.of("Show or hide your network sidebar")));
        inv.setItem(15, item(privateMessages ? Material.WRITABLE_BOOK : Material.BARRIER,
                "Private Messages: " + (privateMessages ? "ON" : "OFF"), List.of("Allow or block incoming /tell messages")));
        inv.setItem(16, item(Material.ENDER_PEARL, "Random Teleport", List.of("Use /rtp in Survival")));
        inv.setItem(17, item(Material.COMPASS, "Game Selector", List.of("Open the network game menu")));
        inv.setItem(31, item(Material.PLAYER_HEAD, "My Network Profile", List.of("View /profile and your network stats")));
        inv.setItem(40, item(Material.BARRIER, "Close", List.of("Close settings")));
        player.openInventory(inv);
    }

    @EventHandler public void onMenuClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (!title.equals(MENU_TITLE) && !title.equals(SETTINGS_TITLE)) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;
        if (title.equals(MENU_TITLE)) {
            if (slot == 10) { p.closeInventory(); go(p, "survival_1"); }
            else if (slot == 11) { p.closeInventory(); go(p, "survival_2"); }
            else if (slot == 13) { p.closeInventory(); games.openBedWarsMenu(p); }
            else if (slot == 14) { p.closeInventory(); games.openDuelMenu(p); }
            else if (slot == 15) {
                p.closeInventory();
                p.sendMessage(PREFIX + "Lucky Pillars is not configured yet.");
            } else if (slot == 16) { p.closeInventory(); mobHunt.openMenu(p); }
            else if (slot == 22) { p.closeInventory(); p.teleport(lobby); }
            return;
        }
        if (slot == 10) {
            boolean enabled = !getPlayerSetting(p, "nametags", true);
            setPlayerSetting(p, "nametags", enabled);
            if (hud != null) hud.setNameTagsVisible(p, enabled);
            p.sendMessage(PREFIX + "Player name tags: " + (enabled ? "ON" : "OFF"));
            playSettingsSound(p);
            openSettings(p);
        } else if (slot == 11) {
            boolean hidden = !playersHidden.getOrDefault(p.getUniqueId(), false);
            playersHidden.put(p.getUniqueId(), hidden);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                if (hidden) p.hidePlayer(this, other); else p.showPlayer(this, other);
            }
            p.sendMessage(PREFIX + "Player visibility: " + (hidden ? "OFF" : "ON"));
            playSettingsSound(p);
            openSettings(p);
        } else if (slot == 12) {
            boolean enabled = !getPlayerSetting(p, "lobby-particles", true);
            setPlayerSetting(p, "lobby-particles", enabled);
            playSettingsSound(p);
            openSettings(p);
        } else if (slot == 13) {
            boolean enabled = !getPlayerSetting(p, "menu-sounds", true);
            setPlayerSetting(p, "menu-sounds", enabled);
            if (enabled) playSettingsSound(p);
            openSettings(p);
        } else if (slot == 14) {
            boolean enabled = !getPlayerSetting(p, "scoreboard", true);
            setPlayerSetting(p, "scoreboard", enabled);
            if (hud != null) hud.setSidebarVisible(p, enabled);
            playSettingsSound(p);
            openSettings(p);
        } else if (slot == 15) {
            boolean enabled = !getPlayerSetting(p, "private-messages", true);
            setPlayerSetting(p, "private-messages", enabled);
            p.sendMessage(PREFIX + "Private messages: " + (enabled ? "ON" : "OFF"));
            playSettingsSound(p);
            openSettings(p);
        } else if (slot == 16) {
            p.closeInventory();
            p.performCommand("rtp");
        } else if (slot == 17) {
            playSettingsSound(p);
            openMenu(p);
        } else if (slot == 31) {
            p.closeInventory();
            p.performCommand("profile");
        } else p.closeInventory();
    }

    private void regenerateArena(World world, String arena) {
        if (world == null) return;
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Player)) entity.remove();
        }
        if (arena.equals("bedwars_1")) ArenaBuilder.buildBedWars(world);
        else if (arena.equals("duel_arena")) ArenaBuilder.buildDuel(world);
        if (games != null) games.onArenaRegenerated(arena);
    }

    private boolean resetArenaWorld(String arena) {
        World world = Bukkit.getWorld(arena);
        if (world == null) return false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) player.teleport(lobby);
        }
        regenerateArena(world, arena);
        return true;
    }

    private void go(Player p, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) { p.sendMessage(PREFIX + "That world is not ready."); return; }
        p.setGameMode(org.bukkit.GameMode.SURVIVAL);
        p.teleport(getLastSurvivalLocation(p, world));
        p.sendMessage(PREFIX + "Welcome to " + ChatColor.LIGHT_PURPLE + worldName + ChatColor.GRAY + ".");
    }

    public void teleportToSurvival(Player player, String worldName) {
        go(player, worldName);
    }

    private boolean hasNetworkAdmin(CommandSender sender) {
        return sender.hasPermission("nebulacore.admin") || (hud != null && hud.isAdminRank(sender));
    }

    private void randomTeleport(Player p) {
        World world = Bukkit.getWorld("survival_1");
        if (world == null) { p.sendMessage(PREFIX + "Survival is not ready."); return; }
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = random.nextInt(10001) - 5000;
            int z = random.nextInt(10001) - 5000;
            int y = world.getHighestBlockYAt(x, z) + 1;
            if (y <= world.getMinHeight() || y + 1 >= world.getMaxHeight()) continue;
            Block floor = world.getBlockAt(x, y - 1, z);
            Block feet = world.getBlockAt(x, y, z);
            Block head = world.getBlockAt(x, y + 1, z);
            if (!floor.getType().isSolid() || floor.isLiquid() || !feet.isEmpty() || !head.isEmpty()) continue;
            Location destination = new Location(world, x + 0.5, y, z + 0.5);
            p.teleport(destination);
            p.sendMessage(PREFIX + "Randomly teleported into Survival.");
            return;
        }
        p.sendMessage(PREFIX + "Couldn't find a safe location. Try /rtp again.");
    }


    private void sendNebulaHelp(CommandSender sender, String category) {
        String section = category == null ? "all" : category.toLowerCase(java.util.Locale.ROOT);
        sender.sendMessage(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "✦ NEBULA NETWORK HELP ✦");
        if (section.equals("all") || section.equals("player")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "PLAYER: " + ChatColor.GRAY
                    + "/spawn, /menu, /settings, /rtp, /bedwars, /duel, /mobhunt, /stats, /profile, /ping, /report, /leaderboard");
            sender.sendMessage(ChatColor.GRAY + "  /duel player | /duel queue | /duel bot | /duel accept | /duel deny | /duel leave");
            sender.sendMessage(ChatColor.GRAY + "  /rank — view your current network rank");
        }
        if (section.equals("all") || section.equals("arena")) {
            sender.sendMessage(ChatColor.GRAY + "  /mobhunt [join|leave|status] — enter the Mob Hunt queue");
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "ARENA: " + ChatColor.GRAY
                    + "/bedwars, /duel, /duel queue, /duel bot");
            sender.sendMessage(ChatColor.GRAY + "  Arena worlds: bedwars_1 and duel_arena");
        }
        if (section.equals("all") || section.equals("ranks")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "RANKS: " + ChatColor.GRAY
                    + "/rank — view your rank");
            sender.sendMessage(ChatColor.GRAY + "  /rank <player> <OWNER|ADMIN|MVP|VIP|MEMBER> — staff only");
        }
        if (section.equals("all") || section.equals("admin")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE + "ADMIN: " + ChatColor.GRAY
                    + "/setlobby, /arenareset [bedwars|duel|all], /nebula lagclear, /sus [list|check|clear]");
            sender.sendMessage(ChatColor.GRAY + "  /nebula help admin — show this section");
        }
        if (!List.of("all", "player", "arena", "admin", "ranks").contains(section)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "Categories: player, arena, admin, ranks.");
            return;
        }
        sender.sendMessage(ChatColor.DARK_GRAY + "Use /nebula help <category> for a section.");
    }

    private void clearDroppedItems(CommandSender sender) {
        if (!hasNetworkAdmin(sender)) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You need ADMIN or OWNER permission to clear dropped items.");
            return;
        }
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Item) {
                    entity.remove();
                    removed++;
                }
            }
        }
        sender.sendMessage(PREFIX + ChatColor.GREEN + "Lag clear complete. Removed " + removed
                + " dropped item entities. Player inventories and chests were not touched.");
        getLogger().info(sender.getName() + " cleared " + removed + " dropped item entities.");
    }


    private boolean handlePrivateMessage(CommandSender sender, String commandName, String[] args) {
        if (!(sender instanceof Player from)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player target;
        int messageStart;
        if (commandName.equals("reply")) {
            UUID last = lastMessaged.get(from.getUniqueId());
            target = last == null ? null : Bukkit.getPlayer(last);
            messageStart = 0;
            if (target == null) {
                from.sendMessage(PREFIX + ChatColor.RED + "Nobody to reply to.");
                return true;
            }
        } else {
            if (args.length < 2) {
                from.sendMessage(PREFIX + "Usage: /tell <player> <message> or /r <message>");
                return true;
            }
            target = Bukkit.getPlayerExact(args[0]);
            messageStart = 1;
            if (target == null) {
                from.sendMessage(PREFIX + ChatColor.RED + "That player is not online.");
                return true;
            }
        }
        if (target.equals(from)) {
            from.sendMessage(PREFIX + ChatColor.RED + "You cannot message yourself.");
            return true;
        }
        if (!getPlayerSetting(target, "private-messages", true)) {
            from.sendMessage(PREFIX + ChatColor.RED + "That player has private messages disabled.");
            return true;
        }
        String message = String.join(" ", java.util.Arrays.copyOfRange(args, messageStart, args.length)).trim();
        if (message.isEmpty()) {
            from.sendMessage(PREFIX + "Message cannot be empty.");
            return true;
        }
        lastMessaged.put(from.getUniqueId(), target.getUniqueId());
        lastMessaged.put(target.getUniqueId(), from.getUniqueId());
        target.sendMessage(ChatColor.DARK_PURPLE + "[MSG] " + ChatColor.LIGHT_PURPLE + from.getName()
                + ChatColor.GRAY + " → " + ChatColor.WHITE + message);
        from.sendMessage(ChatColor.DARK_PURPLE + "[MSG] " + ChatColor.LIGHT_PURPLE + "You → "
                + target.getName() + ChatColor.GRAY + ": " + ChatColor.WHITE + message);
        return true;
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (java.util.Set.of("msg", "reply").contains(commandName)) return handlePrivateMessage(sender, commandName, args);
        if (java.util.Set.of("stats", "leaderboard", "sus", "profile").contains(commandName)) {
            return networkFeatures.handleCommand(sender, command.getName(), args);
        }
        if (command.getName().equalsIgnoreCase("setlobby")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!hasNetworkAdmin(p)) { p.sendMessage(PREFIX + "No permission."); return true; }
            lobby = p.getLocation();
            getConfig().set("lobby.world", lobby.getWorld().getName());
            getConfig().set("lobby.x", lobby.getX()); getConfig().set("lobby.y", lobby.getY()); getConfig().set("lobby.z", lobby.getZ());
            getConfig().set("lobby.yaw", lobby.getYaw()); getConfig().set("lobby.pitch", lobby.getPitch());
            saveConfig();
            applyLobbyBorder();
            if (hud != null) hud.updateLobby(lobby);
            p.sendMessage(PREFIX + "Lobby spawn saved. The lobby border and effects were updated.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("spawn")) {
            if (sender instanceof Player p) returnPlayerToLobby(p);
            return true;
        }
        if (command.getName().equalsIgnoreCase("rtp")) {
            if (sender instanceof Player p) randomTeleport(p);
            else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("bedwars")) {
            if (sender instanceof Player p) games.handleBedWarsCommand(p, args); else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("mobhunt")) {
            if (sender instanceof Player p) {
                if (mobHunt == null) p.sendMessage(PREFIX + ChatColor.RED + "Mob Hunt is temporarily unavailable because its disguise dependency did not load.");
                else mobHunt.handleCommand(p, args);
            } else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("duel")) {
            if (sender instanceof Player p) games.handleDuelCommand(p, args); else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("arenareset")) {
            if (!hasNetworkAdmin(sender)) { sender.sendMessage(PREFIX + "No permission."); return true; }
            String target = args.length == 0 ? "all" : args[0].toLowerCase();
            if (!target.equals("all") && !target.equals("bedwars") && !target.equals("duel")) {
                sender.sendMessage(PREFIX + "Usage: /arenareset [bedwars|duel|all]");
                return true;
            }
            Bukkit.getScheduler().runTask(this, () -> {
                boolean success = true;
                if (target.equals("all") || target.equals("bedwars")) success &= resetArenaWorld("bedwars_1");
                if (target.equals("all") || target.equals("duel")) success &= resetArenaWorld("duel_arena");
                sender.sendMessage(PREFIX + (success ? "Arena regeneration complete: " + target : "Could not regenerate one of the arenas. Check the server log."));
            });
            return true;
        }
        if (command.getName().equalsIgnoreCase("rank")) {
            hud.handleRankCommand(sender, args);
            return true;
        }
        if (command.getName().equalsIgnoreCase("settings")) {
            if (sender instanceof Player p) openSettings(p);
            else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("menu")) {
            if (sender instanceof Player p) openMenu(p);
            else sender.sendMessage("Players only.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("nebula")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("help")) {
                sendNebulaHelp(sender, args.length > 1 ? args[1] : null);
                return true;
            }
            if (args.length > 0 && (args[0].equalsIgnoreCase("lagclear") || args[0].equalsIgnoreCase("clear"))) {
                clearDroppedItems(sender);
                return true;
            }
            if (sender instanceof Player p) openMenu(p);
            else sendNebulaHelp(sender, null);
            return true;
        }
        return false;
    }
}
