package com.defne.blockspawner.model;

import java.util.UUID;

public class SpawnerInstance {
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final UUID owner;
    private volatile String typeId;
    private volatile int level;
    private volatile long nextSpawnMillis;

    public SpawnerInstance(String world, int x, int y, int z, String typeId, UUID owner, int level) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.typeId = typeId;
        this.owner = owner;
        this.level = Math.max(1, level);
        this.nextSpawnMillis = System.currentTimeMillis();
    }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public String world() { return world; }
    public int x() { return x; }
    public int y() { return y; }
    public int z() { return z; }
    public UUID owner() { return owner; }
    public String typeId() { return typeId; }
    public int level() { return level; }
    public long nextSpawnMillis() { return nextSpawnMillis; }

    public void setTypeId(String typeId) { this.typeId = typeId; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public void setNextSpawnMillis(long nextSpawnMillis) { this.nextSpawnMillis = nextSpawnMillis; }
}
