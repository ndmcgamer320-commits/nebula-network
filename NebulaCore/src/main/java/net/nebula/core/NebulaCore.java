package net.nebula.core;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.io.File;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class NebulaCore extends JavaPlugin implements Listener {
    private Location lobby;
    private final Map<UUID, Boolean> playersHidden = new HashMap<>();
    private final Random random = new Random();
    private NebulaGames games;
    private NebulaHud hud;
    private boolean importedLobby;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;
    private static final String MENU_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA NETWORK";
    private static final String SETTINGS_TITLE = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA SETTINGS";

    @Override
    public void onEnable() {
        importedLobby = new File("lobby-imported.flag").isFile();
        lobby = loadLobby();
        for (String name : List.of("survival_1", "survival_2", "bedwars_1", "bedwars_2", "pillars_1", "pillars_2", "duel_arena")) {
            World world = Bukkit.getWorld(name);
            if (world == null) world = Bukkit.createWorld(new WorldCreator(name));
            if (world != null) world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        }
        if (!importedLobby) buildLobby();
        applyLobbyBorder();
        Bukkit.getPluginManager().registerEvents(this, this);
        games = new NebulaGames(this, lobby);
        games.enable();
        hud = new NebulaHud(this, lobby);
        hud.enable();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            regenerateArena(Bukkit.getWorld("bedwars_1"), "bedwars_1");
            regenerateArena(Bukkit.getWorld("duel_arena"), "duel_arena");
            getLogger().info("Network worlds loaded. Lobby HUD and NPCs enabled.");
        }, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) hud.refreshPlayer(p);
        getLogger().info("NebulaCore enabled with the imported/custom lobby, ranks and scoreboard.");
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
        border.setSize(110.0);
        border.setDamageBuffer(0.0);
        border.setDamageAmount(2.0);
        border.setWarningDistance(5);
    }

    private Location loadLobby() {
        World world=Bukkit.getWorld("world");
        if(importedLobby&&world!=null) return world.getSpawnLocation().clone().add(0.5,1.0,0.5);
        if(!getConfig().contains("lobby.world")) return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5,1,0.5);
        world=Bukkit.getWorld(getConfig().getString("lobby.world","world"));
        if(world==null) return Bukkit.getWorlds().get(0).getSpawnLocation().add(0.5,1,0.5);
        return new Location(world,getConfig().getDouble("lobby.x"),getConfig().getDouble("lobby.y"),
                getConfig().getDouble("lobby.z"),(float)getConfig().getDouble("lobby.yaw"),(float)getConfig().getDouble("lobby.pitch"));
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
        Player p = e.getPlayer();
        e.setJoinMessage(PREFIX + ChatColor.WHITE + p.getName() + ChatColor.GRAY + " joined the network.");
        p.teleport(lobby);
        p.getInventory().setItem(4, item(Material.COMPASS, "Game Selector", List.of("Right-click to choose a game", "Use /menu anytime")));
        p.getInventory().setItem(8, item(Material.CLOCK, "Settings", List.of("Right-click to customize your lobby")));
        p.sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Welcome to Nebula Network! " + ChatColor.GRAY + "Use /menu to choose a game.");
        hud.refreshPlayer(p);
        for (Player online : Bukkit.getOnlinePlayers()) hud.refreshPlayer(online);
    }

    @EventHandler public void onArenaExit(PlayerChangedWorldEvent e) {
        scheduleArenaRegeneration(e.getFrom());
    }

    @EventHandler public void onArenaQuit(PlayerQuitEvent e) {
        scheduleArenaRegeneration(e.getPlayer().getWorld());
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
        inv.setItem(22, item(Material.NETHER_STAR, "Lobby", List.of("Return to the protected lobby")));
        player.openInventory(inv);
    }

    private void openSettings(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, SETTINGS_TITLE);
        boolean hidden = playersHidden.getOrDefault(player.getUniqueId(), false);
        inv.setItem(11, item(hidden ? Material.GRAY_DYE : Material.LIME_DYE, "Player Visibility: " + (hidden ? "OFF" : "ON"),
                List.of("Click to toggle other players")));
        inv.setItem(13, item(Material.ENDER_PEARL, "Random Teleport", List.of("Use /rtp in Survival")));
        inv.setItem(15, item(Material.COMPASS, "Game Selector", List.of("Open the network game menu")));
        inv.setItem(22, item(Material.BARRIER, "Close", List.of("Close settings")));
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
            } else if (slot == 22) { p.closeInventory(); p.teleport(lobby); }
            return;
        }
        if (slot == 11) {
            boolean hidden = !playersHidden.getOrDefault(p.getUniqueId(), false);
            playersHidden.put(p.getUniqueId(), hidden);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p)) continue;
                if (hidden) p.hidePlayer(this, other); else p.showPlayer(this, other);
            }
            p.sendMessage(PREFIX + "Player visibility: " + (hidden ? "OFF" : "ON"));
            openSettings(p);
        } else if (slot == 13) {
            p.closeInventory();
            p.performCommand("rtp");
        } else if (slot == 15) openMenu(p);
        else p.closeInventory();
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
        p.teleport(world.getSpawnLocation());
        p.sendMessage(PREFIX + "Welcome to " + ChatColor.LIGHT_PURPLE + worldName + ChatColor.GRAY + ".");
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

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            if (sender instanceof Player p) {
                p.teleport(lobby);
                if (hud != null) hud.refreshPlayer(p);
            }
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
        if (command.getName().equalsIgnoreCase("menu") || command.getName().equalsIgnoreCase("nebula")) {
            if (sender instanceof Player p) openMenu(p);
            return true;
        }
        return false;
    }
}
