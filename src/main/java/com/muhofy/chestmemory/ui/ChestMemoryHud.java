// ChestOpenHandler.java içinde — indexChest() metodunda
// pushToast çağrısından ÖNCE şunu kontrol et:
// Toast'u sadece sandık ekranı açıkken göster, kapanınca zaten kaybolur

// ChestMemoryHud.java — renderToasts() metodunu şöyle değiştir:
// Arkaplan rengi tamamen kaldırıldı, sadece accent çizgisi + yazı

private static void renderToasts(DrawContext ctx, MinecraftClient mc, long now) {
    if (toasts.isEmpty()) return;

    int sw    = mc.getWindow().getScaledWidth();
    int baseY = activeTarget != null ? (6 + STRIP_H + INFO_H + 6) : 8;
    int gap   = 3;
    int padX  = 8;
    int padY  = 3;

    int i = 0;
    for (Toast t : toasts) {
        float alpha = t.alpha(now);
        int   a     = (int)(alpha * 255);
        if (a <= 0) { i++; continue; }

        String titleStr = Text.translatable(t.titleKey()).getString();
        String line = (t.subtitle() != null && !t.subtitle().isBlank())
                ? titleStr + "  •  " + t.subtitle() : titleStr;
        if (t.count() > 1) line = line + "  ×" + t.count();

        int textW = mc.textRenderer.getWidth(line);
        int boxW  = textW + padX * 2 + 4; // +4 for accent line
        int boxH  = mc.textRenderer.fontHeight + padY * 2;
        int boxX  = (sw - boxW) / 2;
        int boxY  = baseY + i * (boxH + gap);

        // Arkaplan YOK — sadece sol accent çizgisi
        int accent = t.type() == ToastType.SUCCESS ? 0x55AA00 : 0x5555FF;
        ctx.fill(boxX, boxY, boxX + 2, boxY + boxH, withA(accent, a));

        // Metin — gölgeli beyaz, direkt oyun üstünde
        ctx.drawTextWithShadow(mc.textRenderer,
                Text.literal(line),
                boxX + padX, boxY + padY,
                withA(0xFFFFFF, a));
        i++;
    }
}