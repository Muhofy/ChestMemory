package com.muhofy.chestmemory.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChestRecord {

    private String id;
    private String customName;
    private int x, y, z;
    private String dimension;
    private String lastUpdated;       // ISO-8601 string — Gson için
    private List<ChestItem> items;

    // ── Gson için boş constructor ─────────────────────────────────────────
    public ChestRecord() {
        this.items = new ArrayList<>();
    }

    public ChestRecord(int x, int y, int z, String dimension) {
        this.id          = UUID.randomUUID().toString();
        this.x           = x;
        this.y           = y;
        this.z           = z;
        this.dimension   = dimension;
        this.lastUpdated = LocalDateTime.now().toString();
        this.items       = new ArrayList<>();
    }

    // ── Yardımcı metodlar ─────────────────────────────────────────────────

    /** Sadece X-Z düzleminde mesafe (Y göz ardı) */
    public double distanceTo(double px, double pz) {
        double dx = this.x - px;
        double dz = this.z - pz;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** customName varsa onu, yoksa otomatik etiket döndür */
    public String getDisplayName(int autoIndex) {
        if (customName != null && !customName.isBlank()) return customName;
        return "Sandık #" + autoIndex;
    }

    public boolean isInDimension(String dim) {
        return dimension != null && dimension.equals(dim);
    }

    public void touchUpdated() {
        this.lastUpdated = LocalDateTime.now().toString();
    }

    // ── Getter / Setter ───────────────────────────────────────────────────

    public String getId()                        { return id; }
    public String getCustomName()                { return customName; }
    public int getX()                            { return x; }
    public int getY()                            { return y; }
    public int getZ()                            { return z; }
    public String getDimension()                 { return dimension; }
    public String getLastUpdated()               { return lastUpdated; }
    public List<ChestItem> getItems()            { return items; }

    public void setId(String id)                 { this.id = id; }
    public void setCustomName(String name)       { this.customName = name; }
    public void setX(int x)                      { this.x = x; }
    public void setY(int y)                      { this.y = y; }
    public void setZ(int z)                      { this.z = z; }
    public void setDimension(String dim)         { this.dimension = dim; }
    public void setLastUpdated(String ts)        { this.lastUpdated = ts; }
    public void setItems(List<ChestItem> items)  { this.items = items; }

    @Override
    public String toString() {
        return "ChestRecord{id='" + id + "', pos=[" + x + "," + y + "," + z + "]"
                + ", dim='" + dimension + "', items=" + items.size() + "}";
    }
}