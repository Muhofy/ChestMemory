package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.ChestMemoryMod;
import com.muhofy.chestmemory.config.ChestMemoryConfig;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayDeque;
import java.util.Deque;

public class ChestMemoryHud {

    public enum ToastType { SUCCESS, INFO }

    private record Toast(String title, String subtitle, ToastType type,
                         long createdAt, long duration, int count) {
        float alpha(long now) {
            long e = now - createdAt;
            if (e < 150)            return e / 150f;
            if (e > duration - 300) return Math.max(0f, (duration - e) / 300f);
            return 1f;
        }
        // Sayaç güncellenince yeni Toast oluştur (süresi sıfırlanır)
        Toast withCount(int newCount) {
            return new Toast(title, subtitle, type, System.currentTimeMillis(), duration, newCount);
        }
    }

    private static final Deque<Toast> toasts     = new ArrayDeque<>();
    private static final int          MAX_TOASTS = 3;
    private static ChestRecord        activeTarget = null;

    private static final int C_BG       = 0xFF111418;
    private static final int C_BG2      = 0xFF0e1014;
    private static final int C_BORDER   = 0xFF1e2228;
    private static final int C_ACCENT_B = 0xFF38bdf8;
    private static final int C_TEXT     = 0xFFd0d0d0;
    private static final int C_SUB      = 0xFF555555;
    private static final int C_YELLOW   = 0xFFf59e0b;

    public static void register() {
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null) return;
            long now = System.currentTimeMillis();
            toasts.removeIf(t -> now - t.createdAt() >= t.duration());
            renderToasts(ctx, mc, now);
            if (activeTarget != null) renderCompass(ctx, mc);
        });
        ChestMemoryMod.LOGGER.info("[ChestMemoryHud] Registered.");
    }

    // ── API ───────────────────────────────────────────────────────────────

    /**
     * Aynı başlıkta zaten bir toast varsa sayacı artır (spam önleme).
     * Boş sandık (subtitle içinde "0 item" geçiyorsa) toast gösterme.
     */
    public static void pushToast(String title, String subtitle, ToastType type) {
        if (!ChestMemoryConfig.getInstance().toastEnabled) return;

        // Boş sandık kontrolü — "0 item" içeriyorsa gösterme
        if (subtitle != null && subtitle.contains("0 item")) return;

        // Aynı title'da mevcut toast var mı? Varsa sayacı artır
        for (Toast existing : toasts) {
            if (existing.title().equals(title)) {
                toasts.remove(existing);
                toasts.addLast(existing.withCount(existing.count() + 1));
                return;
            }
        }

        // Yeni toast
        toasts.addLast(new Toast(title, subtitle, type, System.currentTimeMillis(), 2500L, 1));
        while (toasts.size() > MAX_TOASTS) toasts.pollFirst();
    }

    public static void setTarget(ChestRecord rec) { activeTarget = rec; }
    public static ChestRecord getTarget()         { return activeTarget; }
    public static void clearTarget()              { activeTarget = null; }

    // ── Toast render ──────────────────────────────────────────────────────
    private static void renderToasts(DrawContext ctx, MinecraftClient mc, long now) {
        if (toasts.isEmpty()) return;

        int sw     = mc.getWindow().getScaledWidth();
        int sh     = mc.getWindow().getScaledHeight();
        int toastW = 180;
        int toastH = 30;
        int margin = 10;
        int gap    = 4;

        ChestMemoryConfig cfg = ChestMemoryConfig.getInstance();
        boolean bottom = cfg.toastPosition == ChestMemoryConfig.ToastPosition.BOTTOM_LEFT
                      || cfg.toastPosition == ChestMemoryConfig.ToastPosition.BOTTOM_RIGHT;
        boolean right  = cfg.toastPosition == ChestMemoryConfig.ToastPosition.TOP_RIGHT
                      || cfg.toastPosition == ChestMemoryConfig.ToastPosition.BOTTOM_RIGHT;

        int i = 0;
        for (Toast t : toasts) {
            float alpha = t.alpha(now);
            int   a     = (int)(alpha * 255);
            if (a <= 0) { i++; continue; }

            float slide    = Math.min(1f, (now - t.createdAt()) / 150f);
            int   slideOff = (int)((1f - slide) * (toastW + margin));

            int tx = right  ? sw - toastW - margin + slideOff : margin - slideOff;
            int ty = bottom ? sh - margin - toastH - i * (toastH + gap)
                            : margin + i * (toastH + gap);

            // Shadow
            ctx.fill(tx + 2, ty + 2, tx + toastW + 2, ty + toastH + 2, withA(0x000000, (int)(a * 0.4f)));
            // BG
            ctx.fill(tx, ty, tx + toastW, ty + toastH, withA(0x111418, a));
            // Border
            ctx.fill(tx,              ty,              tx + toastW, ty + 1,          withA(0x1e2228, a));
            ctx.fill(tx,              ty + toastH - 1, tx + toastW, ty + toastH,     withA(0x1e2228, a));
            ctx.fill(tx,              ty,              tx + 1,      ty + toastH,     withA(0x1e2228, a));
            ctx.fill(tx + toastW - 1, ty,              tx + toastW, ty + toastH,     withA(0x1e2228, a));
            // Accent bar
            int accent = t.type() == ToastType.SUCCESS ? 0x4ade80 : 0x38bdf8;
            ctx.fill(tx, ty, tx + 2, ty + toastH, withA(accent, a));

            // Icon
            String icon = t.type() == ToastType.SUCCESS ? "✔" : "ℹ";
            ctx.drawTextWithShadow(mc.textRenderer,
                    net.minecraft.text.Text.literal(icon),
                    tx + 6, ty + (toastH - mc.textRenderer.fontHeight) / 2,
                    withA(accent, a));

            // Title
            ctx.drawTextWithShadow(mc.textRenderer,
                    net.minecraft.text.Text.literal(t.title()),
                    tx + 18, ty + 6, withA(0xd0d0d0, a));

            // Subtitle
            if (t.subtitle() != null && !t.subtitle().isBlank()) {
                String sub = t.subtitle().length() > 24 ? t.subtitle().substring(0, 24) + "…" : t.subtitle();
                ctx.drawTextWithShadow(mc.textRenderer,
                        net.minecraft.text.Text.literal(sub),
                        tx + 18, ty + 17, withA(0x555555, a));
            }

            // Sayaç rozeti (x2, x3...) — sağ üst köşe
            if (t.count() > 1) {
                String badge = "x" + t.count();
                int bw = mc.textRenderer.getWidth(badge) + 6;
                int bx2 = tx + toastW - bw - 4;
                int by2 = ty + 4;
                ctx.fill(bx2 - 1, by2 - 1, bx2 + bw, by2 + mc.textRenderer.fontHeight + 1,
                        withA(0x1e2228, a));
                ctx.drawTextWithShadow(mc.textRenderer,
                        net.minecraft.text.Text.literal(badge),
                        bx2 + 2, by2, withA(accent, a));
            }

            // Progress bar
            float prog = 1f - Math.min(1f, (float)(now - t.createdAt()) / t.duration());
            ctx.fill(tx + 2, ty + toastH - 2,
                     tx + 2 + (int)((toastW - 4) * prog), ty + toastH - 1,
                     withA(accent, a));
            i++;
        }
    }

    // ── Compass render ────────────────────────────────────────────────────
    private static void renderCompass(DrawContext ctx, MinecraftClient mc) {
        double px   = mc.player.getX();
        double pz   = mc.player.getZ();
        double dist = activeTarget.distanceTo(px, pz);

        if (dist <= 5) {
            pushToast("Hedefe ulaştın!", ChestStorage.getInstance().getDisplayName(activeTarget), ToastType.SUCCESS);
            activeTarget = null;
            return;
        }

        ChestMemoryConfig cfg = ChestMemoryConfig.getInstance();
        int sw     = mc.getWindow().getScaledWidth();
        int margin = 8;
        int R      = 22;
        int cx     = cfg.compassPosition == ChestMemoryConfig.CompassPosition.TOP_RIGHT
                     ? sw - margin - R : margin + R;
        int cy     = margin + R;

        fillCircle(ctx, cx, cy, R + 2, 0x55000000);
        fillCircle(ctx, cx, cy, R + 1, 0xFF2a2d33);
        fillCircle(ctx, cx, cy, R,     0xFF0e1014);
        fillCircleRing(ctx, cx, cy, R - 1, R, 0xFF1e2228);

        float yaw = mc.player.getYaw();
        String[] cardinals     = {"N", "E", "S", "W"};
        double[] cardinalAngles = {Math.PI, Math.PI / 2, 0, -Math.PI / 2};
        double facingRad        = Math.toRadians(yaw);
        int labelR = R - 7;
        for (int i = 0; i < 4; i++) {
            double screenAngle = cardinalAngles[i] - (-facingRad - Math.PI / 2);
            int lx = cx + (int)(Math.cos(screenAngle) * labelR);
            int ly = cy + (int)(Math.sin(screenAngle) * labelR);
            int color = cardinals[i].equals("N") ? 0xFFf87171 : 0xFF3a3d44;
            ctx.drawTextWithShadow(mc.textRenderer,
                    net.minecraft.text.Text.literal(cardinals[i]),
                    lx - mc.textRenderer.getWidth(cardinals[i]) / 2,
                    ly - mc.textRenderer.fontHeight / 2,
                    color);
        }

        double targetWorldAngle  = Math.atan2(activeTarget.getZ() - pz, activeTarget.getX() - px);
        double playerFacingAtan2 = -facingRad - Math.PI / 2;
        double arrowAngle        = targetWorldAngle - playerFacingAtan2;
        int arrowR = R - 5;
        int ax = cx + (int)(Math.cos(arrowAngle) * arrowR);
        int ay = cy + (int)(Math.sin(arrowAngle) * arrowR);
        ctx.fill(ax - 2, ay - 2, ax + 2, ay + 2, C_ACCENT_B);
        int mx = cx + (int)(Math.cos(arrowAngle) * (arrowR / 2));
        int my = cy + (int)(Math.sin(arrowAngle) * (arrowR / 2));
        ctx.fill(mx - 1, my - 1, mx + 1, my + 1, 0xFF1a4a6a);
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF555555);

        String name    = ChestStorage.getInstance().getDisplayName(activeTarget);
        if (name.length() > 12) name = name.substring(0, 12) + "…";
        String distStr = (int)dist + "m";
        int labelY = cy + R + 4;
        int bgW    = Math.max(mc.textRenderer.getWidth(name), mc.textRenderer.getWidth(distStr)) + 6;
        ctx.fill(cx - bgW / 2, labelY - 1,
                 cx + bgW / 2, labelY + mc.textRenderer.fontHeight * 2 + 4, 0xCC0e1014);
        ctx.drawTextWithShadow(mc.textRenderer,
                net.minecraft.text.Text.literal(name),
                cx - mc.textRenderer.getWidth(name) / 2, labelY, C_TEXT);
        ctx.drawTextWithShadow(mc.textRenderer,
                net.minecraft.text.Text.literal(distStr),
                cx - mc.textRenderer.getWidth(distStr) / 2,
                labelY + mc.textRenderer.fontHeight + 2, C_YELLOW);
    }

    // ── Circle helpers ────────────────────────────────────────────────────
    private static void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int y = -r; y <= r; y++) {
            int hw = (int) Math.sqrt((double)(r * r - y * y));
            ctx.fill(cx - hw, cy + y, cx + hw, cy + y + 1, color);
        }
    }

    private static void fillCircleRing(DrawContext ctx, int cx, int cy, int r1, int r2, int color) {
        for (int y = -r2; y <= r2; y++) {
            int outerHw = (int) Math.sqrt(Math.max(0.0, (double)(r2 * r2 - y * y)));
            int innerHw = (y >= -r1 && y <= r1)
                    ? (int) Math.sqrt(Math.max(0.0, (double)(r1 * r1 - y * y))) : 0;
            if (outerHw > innerHw) {
                ctx.fill(cx - outerHw, cy + y, cx - innerHw, cy + y + 1, color);
                ctx.fill(cx + innerHw, cy + y, cx + outerHw, cy + y + 1, color);
            }
        }
    }

    private static int withA(int rgb, int a) {
        return (Math.min(255, Math.max(0, a)) << 24) | (rgb & 0x00FFFFFF);
    }
}