package com.defne.blockspawner.model;

import org.bukkit.Material;

public record SpawnerType(String id, Material dropMaterial, int intervalSeconds, int amountPerCycle) {
}
