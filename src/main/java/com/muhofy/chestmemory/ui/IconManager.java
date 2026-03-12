package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.ChestMemoryMod;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages custom PNG icons (16x16) for ChestMemory UI.
 *
 * Icon files live at:
 *   assets/chestmemory/textures/gui/icons/<name>.png
 *
 * If a PNG is not found / fails to load, a unicode fallback is used instead.
 *
 * Registered icons (add more as needed):
 *   "chest"        → 📦  fallback
 *   "chest_double" → 🗄  fallback
 *   "navigate"     → 📍  fallback
 *   "delete"       → 🗑  fallback
 *   "rename"       → ✏   fallback
 *   "dim_overworld"→ 🌿  fallback
 *   "dim_nether"   → 🔥  fallback
 *   "dim_end"      → ⭐  fallback
 *   "search"       → 🔍  fallback
 *   "arrived"      → ✅  fallback
 */
public class IconManager {

    // ── Singleton ─────────────────────────────────────────────────────────
    private static final IconManager INSTANCE = new IconManager();
    public static IconManager get() { return INSTANCE; }
    private IconManager() {}

    // ── Icon registry ─────────────────────────────────────────────────────
    private static final Map<String, IconDef> ICONS = new HashMap<>();

    static {
        reg("chest",         "📦");
        reg("chest_double",  "🗄");
        reg("navigate",      "📍");
        reg("delete",        "🗑");
        reg("rename",        "✏");
        reg("dim_overworld", "🌿");
        reg("dim_nether",    "🔥");
        reg("dim_end",       "⭐");
        reg("search",        "🔍");
        reg("arrived",       "✅");
    }

    private static void reg(String name, String fallback) {
        Identifier id = Identifier.of(ChestMemoryMod.MOD_ID,
                "textures/gui/icons/" + name + ".png");
        ICONS.put(name, new IconDef(id, fallback));
    }

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Draws a 16x16 icon at (x, y).
     * Uses PNG if the texture exists, otherwise draws the unicode fallback.
     *
     * @param ctx     DrawContext
     * @param name    Icon name (e.g. "chest", "navigate")
     * @param x       Top-left X
     * @param y       Top-left Y
     * @param color   Tint color for PNG (use 0xFFFFFFFF for no tint).
     *                Unicode fallback ignores this.
     */
    public void draw(DrawContext ctx, String name, int x, int y, int color) {
        IconDef def = ICONS.get(name);
        if (def == null) return;

        if (textureExists(def.id)) {
            ctx.drawTexture(
                    net.minecraft.client.render.RenderLayer::getGuiTextured,
                    def.id,
                    x, y, 0, 0,
                    16, 16,
                    16, 16
            );
        } else {
            // Unicode fallback — drawn with shadow at center of 16x16 area
            net.minecraft.client.MinecraftClient mc =
                    net.minecraft.client.MinecraftClient.getInstance();
            if (mc.textRenderer != null) {
                ctx.drawTextWithShadow(mc.textRenderer,
                        net.minecraft.text.Text.literal(def.fallback),
                        x, y + 2, color);
            }
        }
    }

    /**
     * Convenience: draw with white tint (no tint).
     */
    public void draw(DrawContext ctx, String name, int x, int y) {
        draw(ctx, name, x, y, 0xFFFFFFFF);
    }

    /**
     * Returns the unicode fallback string for an icon.
     * Useful when you only need the text form (e.g. for buttons).
     */
    public String fallback(String name) {
        IconDef def = ICONS.get(name);
        return def != null ? def.fallback : "?";
    }

    /**
     * Returns true if a custom PNG exists for this icon name.
     */
    public boolean hasPng(String name) {
        IconDef def = ICONS.get(name);
        return def != null && textureExists(def.id);
    }

    // ── Internal ──────────────────────────────────────────────────────────

    /**
     * Checks whether the texture resource exists in the resource pack / mod jar.
     * Caches results to avoid repeated lookups.
     */
    private final Map<Identifier, Boolean> existsCache = new HashMap<>();

    private boolean textureExists(Identifier id) {
        return existsCache.computeIfAbsent(id, k -> {
            try {
                net.minecraft.client.MinecraftClient mc =
                        net.minecraft.client.MinecraftClient.getInstance();
                if (mc.getResourceManager() == null) return false;
                return mc.getResourceManager().getResource(k).isPresent();
            } catch (Exception e) {
                return false;
            }
        });
    }

    // ── Record ────────────────────────────────────────────────────────────
    private record IconDef(Identifier id, String fallback) {}
}