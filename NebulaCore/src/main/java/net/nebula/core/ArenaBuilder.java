package net.nebula.core;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;

/**
 * Deterministic, code-built arena maps. Re-running either builder restores
 * the original layout, which makes arena regeneration independent of ZIP files.
 */
public final class ArenaBuilder {
    private static final int FLOOR_Y = 99;

    private ArenaBuilder() {}

    public static void buildBedWars(World world) {
        if (world == null) return;
        clear(world, -38, 92, -30, 38, 120, 30);

        // Red and blue home islands, plus the central emerald island.
        island(world, -24, 0, Material.RED_CONCRETE, Material.RED_TERRACOTTA);
        island(world, 24, 0, Material.BLUE_CONCRETE, Material.BLUE_TERRACOTTA);
        island(world, 0, 0, Material.CYAN_TERRACOTTA, Material.PRISMARINE_BRICKS);

        // Three-block-wide bridges between the bases and middle.
        bridge(world, -17, -8, Material.RED_WOOL);
        bridge(world, 8, 17, Material.BLUE_WOOL);

        placeBed(world, -29, FLOOR_Y + 1, 0, Material.RED_BED, BlockFace.EAST);
        placeBed(world, 29, FLOOR_Y + 1, 0, Material.BLUE_BED, BlockFace.WEST);

        // Team spawn pads and small themed structures.
        pad(world, -24, -6, Material.RED_CONCRETE);
        pad(world, 24, 6, Material.BLUE_CONCRETE);
        tower(world, -32, -7, Material.RED_STAINED_GLASS);
        tower(world, -32, 7, Material.RED_STAINED_GLASS);
        tower(world, 32, -7, Material.BLUE_STAINED_GLASS);
        tower(world, 32, 7, Material.BLUE_STAINED_GLASS);

        // Mid-island ring and generator centerpiece.
        disk(world, 0, 0, FLOOR_Y, 6, Material.CYAN_CONCRETE);
        disk(world, 0, 0, FLOOR_Y + 1, 3, Material.LIGHT_BLUE_CONCRETE);
        set(world, 0, FLOOR_Y + 1, 0, Material.EMERALD_BLOCK);
        for (int[] pos : new int[][]{{-5,-5},{-5,5},{5,-5},{5,5}}) {
            set(world, pos[0], FLOOR_Y + 1, pos[1], Material.SEA_LANTERN);
        }

        // Decorative outer markers.
        for (int[] pos : new int[][]{{-24,-10},{-24,10},{24,-10},{24,10}}) {
            set(world, pos[0], FLOOR_Y + 1, pos[1], Material.LANTERN);
            set(world, pos[0], FLOOR_Y + 2, pos[1], Material.CHAIN);
        }

        world.setSpawnLocation(0, FLOOR_Y + 1, 0);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(100);
    }

    public static void buildDuel(World world) {
        if (world == null) return;
        clear(world, -24, 92, -24, 24, 120, 24);

        // Symmetrical circular CPvP-style fighting platform.
        disk(world, 0, 0, FLOOR_Y - 1, 17, Material.POLISHED_DEEPSLATE);
        disk(world, 0, 0, FLOOR_Y, 15, Material.DEEPSLATE_TILES);
        ring(world, 0, 0, FLOOR_Y, 16, Material.PURPLE_CONCRETE);
        ring(world, 0, 0, FLOOR_Y + 1, 16, Material.IRON_BARS);

        // Checker pattern inside the arena.
        for (int x = -13; x <= 13; x++) {
            for (int z = -13; z <= 13; z++) {
                if (x * x + z * z <= 13 * 13) {
                    Material tile = ((x + z) & 1) == 0
                            ? Material.DEEPSLATE_TILES : Material.POLISHED_BLACKSTONE;
                    set(world, x, FLOOR_Y, z, tile);
                }
            }
        }

        // Spawn pads at opposite ends.
        disk(world, -10, 0, FLOOR_Y, 3, Material.PURPLE_CONCRETE);
        disk(world, 10, 0, FLOOR_Y, 3, Material.CYAN_CONCRETE);
        set(world, -10, FLOOR_Y + 1, 0, Material.SEA_LANTERN);
        set(world, 10, FLOOR_Y + 1, 0, Material.SEA_LANTERN);

        // Four corner pillars and a central marker.
        for (int[] pos : new int[][]{{-12,-12},{-12,12},{12,-12},{12,12}}) {
            pillar(world, pos[0], pos[1], FLOOR_Y + 1, 4, Material.PURPLE_CONCRETE);
            set(world, pos[0], FLOOR_Y + 5, pos[1], Material.END_ROD);
        }
        set(world, 0, FLOOR_Y + 1, 0, Material.AMETHYST_BLOCK);
        set(world, 0, FLOOR_Y + 2, 0, Material.END_ROD);

        // Low boundary wall keeps the duel area readable and contained.
        for (int x = -15; x <= 15; x++) {
            for (int z = -15; z <= 15; z++) {
                int r2 = x * x + z * z;
                if (r2 >= 14 * 14 && r2 <= 15 * 15) {
                    set(world, x, FLOOR_Y + 1, z, Material.PURPLE_STAINED_GLASS);
                    set(world, x, FLOOR_Y + 2, z, Material.PURPLE_STAINED_GLASS);
                }
            }
        }

        world.setSpawnLocation(0, FLOOR_Y + 2, 0);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(64);
    }

    private static void island(World world, int cx, int cz, Material top, Material edge) {
        disk(world, cx, cz, FLOOR_Y - 2, 8, Material.STONE_BRICKS);
        disk(world, cx, cz, FLOOR_Y - 1, 8, edge);
        disk(world, cx, cz, FLOOR_Y, 7, top);
        ring(world, cx, cz, FLOOR_Y, 8, Material.SMOOTH_STONE);
        disk(world, cx, cz, FLOOR_Y + 1, 2, Material.POLISHED_ANDESITE);
    }

    private static void bridge(World world, int fromX, int toX, Material material) {
        for (int x = fromX; x <= toX; x++) {
            for (int z = -1; z <= 1; z++) set(world, x, FLOOR_Y, z, material);
            set(world, x, FLOOR_Y - 1, -2, Material.CHAIN);
            set(world, x, FLOOR_Y - 1, 2, Material.CHAIN);
        }
    }

    private static void pad(World world, int x, int z, Material material) {
        disk(world, x, z, FLOOR_Y + 1, 2, material);
        set(world, x, FLOOR_Y + 2, z, Material.SEA_LANTERN);
    }

    private static void tower(World world, int x, int z, Material glass) {
        pillar(world, x, z, FLOOR_Y + 1, 4, glass);
        set(world, x, FLOOR_Y + 5, z, Material.END_ROD);
    }

    private static void placeBed(World world, int x, int y, int z, Material type, BlockFace facing) {
        Block foot = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x + facing.getModX(), y, z + facing.getModZ());
        BlockData footData = type.createBlockData();
        BlockData headData = type.createBlockData();
        if (footData instanceof Bed footBed && headData instanceof Bed headBed) {
            footBed.setFacing(facing);
            footBed.setPart(Bed.Part.FOOT);
            headBed.setFacing(facing);
            headBed.setPart(Bed.Part.HEAD);
            foot.setBlockData(footBed, false);
            head.setBlockData(headBed, false);
        } else {
            foot.setType(type, false);
        }
    }

    private static void disk(World world, int cx, int cz, int y, int radius, Material material) {
        int r2 = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= r2) set(world, cx + x, y, cz + z, material);
            }
        }
    }

    private static void ring(World world, int cx, int cz, int y, int radius, Material material) {
        int outer = radius * radius;
        int inner = (radius - 1) * (radius - 1);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int r2 = x * x + z * z;
                if (r2 <= outer && r2 >= inner) set(world, cx + x, y, cz + z, material);
            }
        }
    }

    private static void pillar(World world, int x, int z, int baseY, int height, Material material) {
        for (int y = baseY; y < baseY + height; y++) set(world, x, y, z, material);
    }

    private static void clear(World world, int minX, int minY, int minZ,
                              int maxX, int maxY, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    set(world, x, y, z, Material.AIR);
                }
            }
        }
    }

    private static void set(World world, int x, int y, int z, Material material) {
        world.getBlockAt(x, y, z).setType(material, false);
    }
}
