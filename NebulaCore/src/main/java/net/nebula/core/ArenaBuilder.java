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
        tower(world, -30, -4, Material.RED_STAINED_GLASS);
        tower(world, -30, 4, Material.RED_STAINED_GLASS);
        tower(world, 30, -4, Material.BLUE_STAINED_GLASS);
        tower(world, 30, 4, Material.BLUE_STAINED_GLASS);

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
            set(world, pos[0], FLOOR_Y + 2, pos[1], Material.IRON_BARS);
        }

        world.setSpawnLocation(0, FLOOR_Y + 2, 0);
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
        clear(world, -34, 88, -34, 34, 112, 34);

        // Layered overworld-style CPvP field: bedrock foundation, two stone
        // layers, then a broad grass surface with scattered natural stone patches.
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                set(world, x, 93, z, Material.BEDROCK);
                set(world, x, 94, z, Material.STONE);
                set(world, x, 95, z, Material.STONE);
                set(world, x, 96, z, Material.GRASS_BLOCK);
            }
        }

        java.util.Random terrain = new java.util.Random(72121L);
        for (int patch = 0; patch < 52; patch++) {
            int cx = terrain.nextInt(51) - 25;
            int cz = terrain.nextInt(51) - 25;
            int radius = 1 + terrain.nextInt(3);
            Material surface = patch % 3 == 0 ? Material.COBBLESTONE
                    : (patch % 3 == 1 ? Material.STONE_BRICKS : Material.MOSSY_COBBLESTONE);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x*x + z*z <= radius*radius && (cx+x)*(cx+x)+(cz+z)*(cz+z) < 27*27) {
                        set(world, cx+x, 96, cz+z, surface);
                    }
                }
            }
        }

        // Low stone outcrops add cover without turning the arena into a maze.
        for (int[] p : new int[][]{{-5,-8},{5,8},{-8,5},{8,-5},{0,-18},{0,18}}) {
            disk(world, p[0], p[1], 97, 2, Material.STONE);
            set(world, p[0], 98, p[1], Material.MOSSY_COBBLESTONE);
        }

        // Symmetrical starting pads and a small central obsidian marker.
        disk(world, -14, 0, 96, 3, Material.POLISHED_DEEPSLATE);
        disk(world, 14, 0, 96, 3, Material.POLISHED_DEEPSLATE);
        set(world, -14, 97, 0, Material.SEA_LANTERN);
        set(world, 14, 97, 0, Material.SEA_LANTERN);
        disk(world, 0, 0, 96, 2, Material.OBSIDIAN);
        set(world, 0, 97, 0, Material.AMETHYST_BLOCK);

        // Visible bedrock rim plus a strict vanilla world border.
        for (int v = -30; v <= 30; v++) {
            for (int y = 97; y <= 101; y++) {
                set(world, -30, y, v, Material.BEDROCK);
                set(world, 30, y, v, Material.BEDROCK);
                set(world, v, y, -30, Material.BEDROCK);
                set(world, v, y, 30, Material.BEDROCK);
            }
        }

        world.setSpawnLocation(0, 97, 0);
        world.setTime(6000);
        world.setStorm(false);
        world.setThundering(false);
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(60);
        world.getWorldBorder().setDamageAmount(10.0);
        world.getWorldBorder().setDamageBuffer(0.0);
        world.getWorldBorder().setWarningDistance(4);
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
            set(world, x, FLOOR_Y - 1, -2, Material.IRON_BARS);
            set(world, x, FLOOR_Y - 1, 2, Material.IRON_BARS);
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
