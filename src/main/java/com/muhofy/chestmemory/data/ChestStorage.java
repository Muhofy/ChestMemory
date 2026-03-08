package com.muhofy.chestmemory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.muhofy.chestmemory.ChestMemoryMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ChestStorage {

    // ── Singleton ─────────────────────────────────────────────────────────
    private static final ChestStorage INSTANCE = new ChestStorage();
    public static ChestStorage getInstance() { return INSTANCE; }
    private ChestStorage() {}

    // ── State ─────────────────────────────────────────────────────────────
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<ChestRecord> chests = new ArrayList<>();
    private String currentWorld = null;

    // ── SearchResult inner class ──────────────────────────────────────────
    public static class SearchResult {
        public final ChestRecord chest;
        public final ChestItem   item;
        public final double      distance;

        public SearchResult(ChestRecord chest, ChestItem item, double distance) {
            this.chest    = chest;
            this.item     = item;
            this.distance = distance;
        }
    }

    // ── Init ──────────────────────────────────────────────────────────────
    public void init() {
        // İlk başlatma — dünya bağlanınca loadWorld() çağrılacak
        ChestMemoryMod.LOGGER.info("[ChestStorage] Initialized.");
    }

    // ── Dünya değişimi ────────────────────────────────────────────────────
    public void loadWorld(String worldName) {
        if (worldName.equals(currentWorld)) return;
        currentWorld = worldName;
        chests.clear();
        chests.addAll(readFromDisk(worldName));
        ChestMemoryMod.LOGGER.info("[ChestStorage] Loaded {} chests for world '{}'.", chests.size(), worldName);
    }

    public void unloadWorld() {
        currentWorld = null;
        chests.clear();
    }

    // ── CRUD ──────────────────────────────────────────────────────────────

    /** Koordinata göre güncelle, yoksa ekle */
    public ChestRecord addOrUpdate(int x, int y, int z, String dimension, List<ChestItem> items) {
        ChestRecord existing = getAt(x, y, z, dimension);
        if (existing != null) {
            existing.setItems(items);
            existing.touchUpdated();
            save();
            return existing;
        }
        ChestRecord rec = new ChestRecord(x, y, z, dimension);
        rec.setItems(items);
        chests.add(rec);
        save();
        return rec;
    }

    public void delete(String id) {
        chests.removeIf(c -> c.getId().equals(id));
        save();
    }

    public void rename(String id, String newName) {
        chests.stream()
              .filter(c -> c.getId().equals(id))
              .findFirst()
              .ifPresent(c -> { c.setCustomName(newName); save(); });
    }

    // ── Sorgular ─────────────────────────────────────────────────────────

    public ChestRecord getAt(int x, int y, int z, String dimension) {
        return chests.stream()
                     .filter(c -> c.getX() == x && c.getY() == y && c.getZ() == z
                                  && c.getDimension().equals(dimension))
                     .findFirst().orElse(null);
    }

    public List<ChestRecord> getAll() {
        return Collections.unmodifiableList(chests);
    }

    public List<ChestRecord> getByDimension(String dimension) {
        return chests.stream()
                     .filter(c -> c.isInDimension(dimension))
                     .collect(Collectors.toList());
    }

    /**
     * Tüm sandıklarda item displayName veya itemId içinde query geçiyorsa döndür.
     * Aktif boyuttakiler önce, mesafeye göre sıralı.
     */
    public List<SearchResult> searchItems(String query, String activeDimension, double px, double pz) {
        if (query == null || query.isBlank()) return List.of();
        String q = query.toLowerCase(Locale.ROOT).trim();

        List<SearchResult> results = new ArrayList<>();
        for (ChestRecord chest : chests) {
            for (ChestItem item : chest.getItems()) {
                boolean nameMatch = item.getDisplayName() != null
                        && item.getDisplayName().toLowerCase(Locale.ROOT).contains(q);
                boolean idMatch   = item.getItemId() != null
                        && item.getItemId().toLowerCase(Locale.ROOT).contains(q);
                if (nameMatch || idMatch) {
                    results.add(new SearchResult(chest, item, chest.distanceTo(px, pz)));
                }
            }
        }

        // Aktif boyut önce, sonra mesafe artan
        results.sort(Comparator
                .<SearchResult, Boolean>comparing(r -> !r.chest.isInDimension(activeDimension))
                .thenComparingDouble(r -> r.distance));

        return results;
    }

    /** Arama overlay için displayName üret (index numarası için liste sırasını kullan) */
    public String getDisplayName(ChestRecord rec) {
        int idx = chests.indexOf(rec) + 1;
        return rec.getDisplayName(idx);
    }

    // ── Disk I/O ──────────────────────────────────────────────────────────

    private void save() {
        if (currentWorld == null) return;
        writeToDisk(currentWorld, chests);
    }

    private Path getFilePath(String worldName) {
        // Güvenli dosya adı: özel karakterleri temizle
        String safe = worldName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
        return FabricLoader.getInstance().getConfigDir()
                           .resolve("chestmemory")
                           .resolve(safe)
                           .resolve("chests.json");
    }

    private List<ChestRecord> readFromDisk(String worldName) {
        Path file = getFilePath(worldName);
        if (!Files.exists(file)) return new ArrayList<>();

        try (Reader r = Files.newBufferedReader(file)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            Type listType  = new TypeToken<List<ChestRecord>>(){}.getType();
            List<ChestRecord> loaded = gson.fromJson(root.get("chests"), listType);
            return loaded != null ? loaded : new ArrayList<>();
        } catch (Exception e) {
            ChestMemoryMod.LOGGER.error("[ChestStorage] Failed to read chests.json — creating backup.", e);
            backupCorrupted(file);
            return new ArrayList<>();
        }
    }

    private void writeToDisk(String worldName, List<ChestRecord> data) {
        Path file = getFilePath(worldName);
        try {
            Files.createDirectories(file.getParent());
            JsonObject root = new JsonObject();
            root.add("chests", gson.toJsonTree(data));
            try (Writer w = Files.newBufferedWriter(file)) {
                gson.toJson(root, w);
            }
        } catch (IOException e) {
            ChestMemoryMod.LOGGER.error("[ChestStorage] Failed to write chests.json.", e);
        }
    }

    private void backupCorrupted(Path file) {
        try {
            Path bak = file.resolveSibling("chests.json.bak");
            Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(file);
            ChestMemoryMod.LOGGER.warn("[ChestStorage] Corrupted file backed up to chests.json.bak");
        } catch (IOException ex) {
            ChestMemoryMod.LOGGER.error("[ChestStorage] Backup failed.", ex);
        }
    }
}