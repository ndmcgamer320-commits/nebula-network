package net.nebula.core;

import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public final class NebulaCore extends JavaPlugin implements Listener {
    private Location lobby;
    private static final String PREFIX = ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA " + ChatColor.LIGHT_PURPLE + "» " + ChatColor.GRAY;

    @Override
    public void onEnable() {
        lobby = loadLobby();
        for (String name : List.of("survival_1", "survival_2", "bedwars_1", "bedwars_2", "pillars_1", "pillars_2")) {
            World world = Bukkit.getWorld(name);
            if (world == null) world = Bukkit.createWorld(new WorldCreator(name));
            if (world != null) world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        }
        buildLobby();
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("NebulaCore enabled. Use /setlobby at your lobby location.");
    }

    private void buildLobby() {
        if (lobby == null || lobby.getWorld() == null) return;
        World w = lobby.getWorld();
        int y = lobby.getBlockY() - 1;
        int cx = lobby.getBlockX(), cz = lobby.getBlockZ();
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) {
            for (int h = 1; h <= 12; h++) w.getBlockAt(cx + x, y + h, cz + z).setType(Material.AIR, false);
            Material floor = (Math.abs(x) == 16 || Math.abs(z) == 16) ? Material.PURPLE_CONCRETE : ((Math.abs(x) % 2 == 0 && Math.abs(z) % 2 == 0) ? Material.POLISHED_BLACKSTONE : Material.BLACK_CONCRETE);
            w.getBlockAt(cx + x, y, cz + z).setType(floor, false);
        }
        for (int[] p : new int[][]{{-14,-14},{-14,14},{14,-14},{14,14}}) {
            for (int h = 1; h <= 5; h++) w.getBlockAt(cx+p[0], y+h, cz+p[1]).setType(Material.PURPLE_STAINED_GLASS, false);
            w.getBlockAt(cx+p[0], y+6, cz+p[1]).setType(Material.END_ROD, false);
        }
        w.getBlockAt(cx+3, y+1, cz).setType(Material.BEACON, false);
    }

    private Location loadLobby() {
        if (!getConfig().contains("lobby.world")) return Bukkit.getWorlds().get(0).getSpawnLocation();
        World world = Bukkit.getWorld(getConfig().getString("lobby.world", "world"));
        if (world == null) return Bukkit.getWorlds().get(0).getSpawnLocation();
        return new Location(world, getConfig().getDouble("lobby.x"), getConfig().getDouble("lobby.y"),
                getConfig().getDouble("lobby.z"), (float)getConfig().getDouble("lobby.yaw"), (float)getConfig().getDouble("lobby.pitch"));
    }

    private boolean inLobby(Location loc) {
        if (lobby == null || loc.getWorld() == null || !loc.getWorld().equals(lobby.getWorld())) return false;
        return loc.distanceSquared(lobby) <= 10000;
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        e.joinMessage(PREFIX + ChatColor.WHITE + e.getPlayer().getName() + ChatColor.GRAY + " joined the network.");
        e.getPlayer().teleport(lobby);
        e.getPlayer().getInventory().setItem(4, item(Material.COMPASS, "Game Selector", List.of("Right-click to choose a game", "Use /menu anytime")));
        e.getPlayer().sendMessage(PREFIX + ChatColor.LIGHT_PURPLE + "Welcome to Nebula Network! " + ChatColor.GRAY + "Use /menu to choose a game.");
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
        if (inLobby(e.getPlayer().getLocation()) && e.getItem() != null && e.getItem().getType() == Material.COMPASS
                && (e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || e.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            openMenu(e.getPlayer());
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
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "NEBULA NETWORK");
        inv.setItem(10, item(Material.GRASS_BLOCK, "Survival Map 1", List.of("Click to enter Survival I")));
        inv.setItem(11, item(Material.MOSS_BLOCK, "Survival Map 2", List.of("Click to enter Survival II")));
        inv.setItem(13, item(Material.RED_BED, "BedWars", List.of("Two arena slots: Map 1 and Map 2", "Requires BedWars arena setup")));
        inv.setItem(15, item(Material.GOLD_BLOCK, "Lucky Pillars", List.of("Two arena slots: Map 1 and Map 2", "Requires Lucky Pillars arena setup")));
        inv.setItem(22, item(Material.NETHER_STAR, "Lobby", List.of("Return to the protected lobby")));
        player.openInventory(inv);
    }

    @EventHandler public void onMenuClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!ChatColor.stripColor(e.getView().getTitle()).equals("NEBULA NETWORK")) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getInventory().getSize()) return;
        if (slot == 10) go(p, "survival_1");
        if (slot == 11) go(p, "survival_2");
        if (slot == 13) p.sendMessage(PREFIX + "BedWars arenas need to be created in the BedWars plugin before matches can start.");
        if (slot == 15) p.sendMessage(PREFIX + "Lucky Pillars arenas need to be created in TheLuckPillars before matches can start.");
        if (slot == 22) p.teleport(lobby);
        p.closeInventory();
    }

    private void go(Player p, String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) { p.sendMessage(PREFIX + "That world is not ready."); return; }
        p.teleport(world.getSpawnLocation());
        p.sendMessage(PREFIX + "Welcome to " + ChatColor.LIGHT_PURPLE + worldName + ChatColor.GRAY + ".");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("setlobby")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            if (!p.hasPermission("nebulacore.admin")) { p.sendMessage(PREFIX + "No permission."); return true; }
            lobby = p.getLocation();
            getConfig().set("lobby.world", lobby.getWorld().getName());
            getConfig().set("lobby.x", lobby.getX()); getConfig().set("lobby.y", lobby.getY()); getConfig().set("lobby.z", lobby.getZ());
            getConfig().set("lobby.yaw", lobby.getYaw()); getConfig().set("lobby.pitch", lobby.getPitch());
            saveConfig();
            p.sendMessage(PREFIX + "Lobby spawn saved. The 100-block radius is protected.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("spawn")) {
            if (sender instanceof Player p) p.teleport(lobby);
            return true;
        }
        if (command.getName().equalsIgnoreCase("menu") || command.getName().equalsIgnoreCase("nebula")) {
            if (sender instanceof Player p) openMenu(p);
            return true;
        }
        return false;
    }
}
