package com.defne.blockspawner.util;

import org.bukkit.Location;
import org.bukkit.World;

public record LocationKey(String world, int x, int y, int z) {
    public static LocationKey from(Location location) {
        World world = location.getWorld();
        if (world == null) {
            throw new IllegalArgumentException("Location world cannot be null");
        }
        return new LocationKey(world.getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    public String serialized() {
        return world + ":" + x + ":" + y + ":" + z;
    }
}
