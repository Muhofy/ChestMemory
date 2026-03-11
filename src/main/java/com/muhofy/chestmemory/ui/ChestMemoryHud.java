package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.ChestMemoryMod;
import com.muhofy.chestmemory.config.ChestMemoryConfig;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChestMemoryHud {

    public enum ToastType { SUCCESS, INFO }

    private record Toast(String titleKey, String subtitle, ToastType type,
                         long createdAt, long duration, int count) {
        // Fade in: 400ms, görünür: ortası, fade out: son 500ms
        float alpha(long now) {
            long e = now - createdAt;
            if (e < 400)            return e / 400f;
            if (e > duration - 500) return Math.max(0f, (duration - e) / 500f);
            return 1f;
        }
        Toast withCount(int n) {
            return new Toast(titleKey, subtitle, type, System.currentTimeMillis(), duration, n);
        }
    }

    private static final Deque<Toast> toasts     = new ArrayDeque<>();
    private static final int          MAX_TOASTS = 3;
    private static ChestRecord        activeTarget = null;

    // ── Strip layout ──────────────────────────────────────────────────────
    private static final int STRIP_H       = 20;  // şerit yüksekliği
    private static final int STRIP_W       = 180; // görünür pencere genişliği
    private static final int TICK_SPACING  = 12;  // tick'ler arası piksel (360° → pixel)
    private static final int INFO_H        = 12;  // sandık adı + mesafe satırı

    // ── Colors (vanilla palette) ──────────────────────────────────────────
    private static final int C_STRIP_BG   = 0xCC000000;
    private static final int C_STRIP_BDR  = 0x33FFFFFF;
    private static final int C_TICK_MINOR = 0x55FFFFFF;
    private static final int C_TICK_MAJOR = 0xCCFFFFFF;
    private static final int C_NORTH      = 0xFFFF5555;
    private static final int C_MARKER     = 0xFF55FFFF;
    private static final int C_CENTER_MRK = 0xFFFFFF55;
    private static final int C_INFO_BG    = 0xCC000000;
    private static final int C_DIST       = 0xFFFFFF55;
    private static final int C_NAME       = 0xFFAAAAAA;

    // Toast colors
    private static final int C_TOAST_BG   = 0xEE1a1a1a;
    private static final int C_TOAST_BDR  = 0xFF2a2a2a;

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            long now = System.currentTimeMillis();
            toasts.removeIf(t -> now - t.createdAt() >= t.duration());
            if (activeTarget != null) renderStrip(ctx, mc);
            renderToasts(ctx, mc, now);
        });
        ChestMemoryMod.LOGGER.info("[ChestMemoryHud] Registered.");
    }

    // ── API ───────────────────────────────────────────────────────────────
    public static void pushToast(String titleKey, String subtitle, ToastType type) {
        if (!ChestMemoryConfig.getInstance().toastEnabled) return;
        if (subtitle != null && subtitle.contains("0 item")) return;
        for (Toast existing : toasts) {
            if (existing.titleKey().equals(titleKey)) {
                toasts.remove(existing);
                toasts.addLast(existing.withCount(existing.count() + 1));
                return;
            }
        }
        toasts.addLast(new Toast(titleKey, subtitle, type, System.currentTimeMillis(), 2500L, 1));
        while (toasts.size() > MAX_TOASTS) toasts.pollFirst();
    }

    public static void setTarget(ChestRecord rec) { activeTarget = rec; }
    public static ChestRecord getTarget()         { return activeTarget; }
    public static void clearTarget()              { activeTarget = null; }

    // ══════════════════════════════════════════════════════════════════════
    // STRIP COMPASS
    // ══════════════════════════════════════════════════════════════════════
    private static void renderStrip(DrawContext ctx, MinecraftClient mc) {
        double px   = mc.player.getX();
        double pz   = mc.player.getZ();
        double dist = activeTarget.distanceTo(px, pz);

        // 5 blok — ulaştı
        if (dist <= 5) {
            pushToast("chestmemory.toast.arrived",
                    ChestStorage.getInstance().getDisplayName(activeTarget),
                    ToastType.SUCCESS);
            activeTarget = null;
            return;
        }

        int sw = mc.getWindow().getScaledWidth();
        int stripX = (sw - STRIP_W) / 2;
        int stripY = 6;
        int totalH = STRIP_H + INFO_H + 2;

        // ── Strip background ──────────────────────────────────────────────
        ctx.fill(stripX,     stripY,     stripX + STRIP_W, stripY + STRIP_H, C_STRIP_BG);
        // border top/bottom
        ctx.fill(stripX,     stripY,     stripX + STRIP_W, stripY + 1,       C_STRIP_BDR);
        ctx.fill(stripX,     stripY + STRIP_H - 1, stripX + STRIP_W, stripY + STRIP_H, C_STRIP_BDR);
        // border left/right
        ctx.fill(stripX,     stripY,     stripX + 1,       stripY + STRIP_H, C_STRIP_BDR);
        ctx.fill(stripX + STRIP_W - 1, stripY, stripX + STRIP_W, stripY + STRIP_H, C_STRIP_BDR);

        // ── Player yaw → piksel offset ────────────────────────────────────
        // MC yaw: 0=S, 90=W, -90=E, ±180=N
        // Şeridi oyuncunun yönüne göre kaydır
        // 360° → TICK_SPACING * 36 piksel tam tur
        float yaw = mc.player.getYaw(); // -180..180
        // Normalize 0..360
        float yawNorm = ((yaw % 360) + 360) % 360; // 0=S,90=W,180=N,270=E

        // Şerit üzerinde: her derece = TICK_SPACING/10 piksel
        // Merkez noktası = oyuncunun yönü
        float pixPerDeg = TICK_SPACING / 10f;

        // ── Tick'leri çiz ─────────────────────────────────────────────────
        // Görünür aralık: ±(STRIP_W/2) piksel → ±(STRIP_W/2 / pixPerDeg) derece
        float halfVisibleDeg = (STRIP_W / 2f) / pixPerDeg;

        // Her 10 derecede minor tick, 45 derecede major tick, cardinal'lerde isim
        String[] cardinalNames = {"S","SW","W","NW","N","NE","E","SE"};
        float[]  cardinalDegs  = { 0,  45,  90, 135, 180, 225, 270, 315};

        int centerX = stripX + STRIP_W / 2;
        int tickBaseY = stripY + 3;

        for (float deg = yawNorm - halfVisibleDeg - 10;
             deg <= yawNorm + halfVisibleDeg + 10; deg++) {

            float normDeg = ((deg % 360) + 360) % 360;
            int   roundDeg = Math.round(normDeg);
            if (roundDeg % 360 != roundDeg) roundDeg = roundDeg % 360;

            // Piksel pozisyonu
            float delta = normDeg - yawNorm;
            // Wraparound düzelt
            if (delta > 180)  delta -= 360;
            if (delta < -180) delta += 360;
            int px2 = centerX + (int)(delta * pixPerDeg);
            if (px2 < stripX + 1 || px2 > stripX + STRIP_W - 2) continue;

            boolean isMajor    = (roundDeg % 45 == 0);
            boolean isCardinal = (roundDeg % 45 == 0);
            boolean isNorth    = (roundDeg == 180);

            if (roundDeg % 10 == 0) {
                int tickH  = isMajor ? 9 : 5;
                int tickCol = isMajor ? (isNorth ? C_NORTH : C_TICK_MAJOR) : C_TICK_MINOR;
                ctx.fill(px2, tickBaseY, px2 + 1, tickBaseY + tickH, tickCol);

                // Cardinal isim
                if (isCardinal) {
                    int ci = -1;
                    for (int i = 0; i < cardinalDegs.length; i++) {
                        if (Math.abs(cardinalDegs[i] - roundDeg) < 0.5f) { ci = i; break; }
                    }
                    if (ci >= 0) {
                        String lbl   = cardinalNames[ci];
                        int    lblX  = px2 - mc.textRenderer.getWidth(lbl) / 2;
                        int    lblY  = tickBaseY + tickH + 1;
                        int    lblC  = isNorth ? C_NORTH : C_TICK_MAJOR;
                        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(lbl), lblX, lblY, lblC);
                    }
                }
            }
        }

        // ── Hedef işaretçisi (sandık) ─────────────────────────────────────
        double targetWorldAngle = Math.toDegrees(
                Math.atan2(activeTarget.getX() - px, activeTarget.getZ() - pz));
        // atan2(dx,dz) → MC yaw convention'a uygun (0=S, 90=W...)
        float targetYaw  = (float)((-targetWorldAngle + 180) % 360);
        targetYaw = ((targetYaw % 360) + 360) % 360;

        float markerDelta = targetYaw - yawNorm;
        if (markerDelta >  180) markerDelta -= 360;
        if (markerDelta < -180) markerDelta += 360;

        int markerX = centerX + (int)(markerDelta * pixPerDeg);

        // Şerit içindeyse işaret çiz, dışındaysa ok göster
        if (markerX >= stripX + 2 && markerX <= stripX + STRIP_W - 3) {
            // Dikey çizgi
            ctx.fill(markerX, stripY + 1, markerX + 1, stripY + STRIP_H - 1, C_MARKER);
            ctx.fill(markerX - 1, stripY + 1, markerX, stripY + 4, withA(0x55FFFF, 100));
            ctx.fill(markerX + 1, stripY + 1, markerX + 2, stripY + 4, withA(0x55FFFF, 100));
            // Üstte sandık ikonu
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal("📦"),
                    markerX - 4, stripY - 10, 0xFFFFFFFF);
        } else {
            // Şerit dışında → kenar oku
            boolean left = markerDelta < 0;
            String arrow = left ? "◀" : "▶";
            int arrowX   = left ? stripX + 3 : stripX + STRIP_W - 8;
            ctx.drawTextWithShadow(mc.textRenderer, Text.literal(arrow),
                    arrowX, stripY + (STRIP_H - mc.textRenderer.fontHeight) / 2, C_MARKER);
        }

        // ── Merkez işaretçi (▼) ──────────────────────────────────────────
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("▼"),
                centerX - 2, stripY + STRIP_H - 1, C_CENTER_MRK);

        // ── Alt bilgi şeridi: isim + mesafe ──────────────────────────────
        String name    = ChestStorage.getInstance().getDisplayName(activeTarget);
        String unit    = Text.translatable("chestmemory.compass.unit").getString();
        String distStr = (int)dist + unit;
        String info    = name + "  ·  " + distStr;
        int infoW      = mc.textRenderer.getWidth(info) + 10;
        int infoX      = (sw - infoW) / 2;
        int infoY      = stripY + STRIP_H + 2;

        ctx.fill(infoX, infoY, infoX + infoW, infoY + INFO_H, C_INFO_BG);
        ctx.fill(infoX, infoY, infoX + infoW, infoY + 1, C_STRIP_BDR);

        // İsim + mesafe ayrı renk
        int nameW  = mc.textRenderer.getWidth(name);
        int sepW   = mc.textRenderer.getWidth("  ·  ");
        int textY  = infoY + (INFO_H - mc.textRenderer.fontHeight) / 2;
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(name),
                infoX + 5, textY, C_NAME);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal("  ·  "),
                infoX + 5 + nameW, textY, C_TICK_MINOR);
        ctx.drawTextWithShadow(mc.textRenderer, Text.literal(distStr),
                infoX + 5 + nameW + sepW, textY, C_DIST);
    }

    // ══════════════════════════════════════════════════════════════════════
    // TOAST — üst orta, fade in/out, sade metin
    // ══════════════════════════════════════════════════════════════════════
    private static void renderToasts(DrawContext ctx, MinecraftClient mc, long now) {
        if (toasts.isEmpty()) return;

        int sw      = mc.getWindow().getScaledWidth();
        // Şerit + bilgi çubuğu altından başla
        int baseY   = 6 + STRIP_H + INFO_H + 6;
        int gap     = 3;
        int padX    = 8;
        int padY    = 4;

        int i = 0;
        for (Toast t : toasts) {
            float alpha = t.alpha(now);
            int   a     = (int)(alpha * 255);
            if (a <= 0) { i++; continue; }

            // İçerik: "Sandık İndekslendi  •  Silah Sandığı  •  8 item"
            // Eğer sayaç varsa sona "  x3" ekle
            String titleStr = Text.translatable(t.titleKey()).getString();
            String line;
            if (t.subtitle() != null && !t.subtitle().isBlank()) {
                line = titleStr + "  •  " + t.subtitle();
            } else {
                line = titleStr;
            }
            if (t.count() > 1) line = line + "  x" + t.count();

            // Metin genişliğine göre kutu boyutu
            int textW  = mc.textRenderer.getWidth(line);
            int boxW   = textW + padX * 2;
            int boxH   = mc.textRenderer.fontHeight + padY * 2;
            int boxX   = (sw - boxW) / 2;
            int boxY   = baseY + i * (boxH + gap);

            // Arka plan — sadece yarı saydam siyah dikdörtgen
            ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, withA(0x000000, (int)(a * 0.65f)));

            // Sol renkli çizgi (accent)
            int accent = t.type() == ToastType.SUCCESS ? 0x55AA00 : 0x5555FF;
            ctx.fill(boxX, boxY, boxX + 2, boxY + boxH, withA(accent, a));

            // Metin — beyaz, gölgeli
            ctx.drawTextWithShadow(mc.textRenderer,
                    Text.literal(line),
                    boxX + padX, boxY + padY,
                    withA(0xFFFFFF, a));

            i++;
        }
    }

    // ── Util ──────────────────────────────────────────────────────────────
    private static int withA(int rgb, int a) {
        return (Math.min(255, Math.max(0, a)) << 24) | (rgb & 0x00FFFFFF);
    }
}