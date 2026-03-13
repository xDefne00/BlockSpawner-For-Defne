package com.defne.blockspawner.util;

import com.defne.blockspawner.model.SpawnerType;
import org.bukkit.Material;

import java.util.Locale;
import java.util.Optional;

public final class CustomSpawnerCodec {
    private static final String PREFIX = "custom|";

    private CustomSpawnerCodec() {
    }

    public static String encode(Material material, int interval, int amount, String name) {
        String safeName = name == null ? "Custom" : name.replace('|', '/').trim();
        if (safeName.isEmpty()) {
            safeName = "Custom";
        }
        return PREFIX + material.name() + "|" + Math.max(1, interval) + "|" + Math.max(1, amount) + "|" + safeName;
    }

    public static Optional<DecodedCustomSpawner> decode(String raw) {
        if (raw == null || !raw.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String[] parts = raw.split("\\|", 5);
        if (parts.length < 5) {
            return Optional.empty();
        }
        Material material = Material.matchMaterial(parts[1]);
        if (material == null || !material.isItem()) {
            return Optional.empty();
        }
        try {
            int interval = Math.max(1, Integer.parseInt(parts[2]));
            int amount = Math.max(1, Integer.parseInt(parts[3]));
            String name = parts[4].isBlank() ? "Custom" : parts[4];
            return Optional.of(new DecodedCustomSpawner(material, interval, amount, name));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    public static SpawnerType toSpawnerType(String raw, String fallbackId) {
        return decode(raw)
                .map(decoded -> new SpawnerType(fallbackId.toLowerCase(Locale.ROOT), decoded.material(), decoded.interval(), decoded.amount()))
                .orElse(null);
    }

    public record DecodedCustomSpawner(Material material, int interval, int amount, String name) {}
}
