package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.data.ChestItem;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * ChestRecordsScreen — kompakt popup (~500x340)
 * Sol panel: sandık listesi | Sağ panel: slot grid + butonlar
 */
public class ChestRecordsScreen extends Screen {

    // ── Popup dimensions ──────────────────────────────────────────────────
    private static final int POP_W   = 500;
    private static final int POP_H   = 340;
    private static final int LEFT_W  = 160;  // sol liste genişliği
    private static final int TITLE_H = 20;
    private static final int FOOT_H  = 28;
    private static final int ROW_H   = 34;
    private static final int SLOT_S  = 16;   // slot boyutu (küçük)
    private static final int SLOT_G  = 1;    // slot gap

    // ── Vanilla colors ────────────────────────────────────────────────────
    private static final int C_BG       = 0xFFC6C6C6;
    private static final int C_LT       = 0xFFFFFFFF;
    private static final int C_DK       = 0xFF555555;
    private static final int C_INSET    = 0xFF000000;
    private static final int C_INSET_LT = 0xFF373737;
    private static final int C_INSET_DK = 0xFFC6C6C6;
    private static final int C_SLOT_BG  = 0xFF8B8B8B;
    private static final int C_SLOT_LT  = 0xFF373737;
    private static final int C_SLOT_DK  = 0xFFFFFFFF;
    private static final int C_ROW_BG   = 0xFF8B8B8B;
    private static final int C_ROW_SEL  = 0xFF9E9E9E;
    private static final int C_TEXT     = 0xFFFFFFFF;
    private static final int C_TEXT_DK  = 0xFF3F3F3F;
    private static final int C_TEXT_GR  = 0xFFAAAAAA;
    private static final int C_TEXT_DIM = 0xFF666666;
    private static final int C_YELLOW   = 0xFFFFFF55;
    private static final int C_CYAN     = 0xFF55FFFF;
    private static final int C_GREEN    = 0xFF55AA00;
    private static final int C_RED      = 0xFFFF5555;

    // ── State ─────────────────────────────────────────────────────────────
    private List<ChestRecord> chests = new ArrayList<>();
    private int  selIdx        = 0;
    private int  scrollOffset  = 0;
    private int  renamingIdx   = -1;
    private boolean confirmDel = false;

    private TextFieldWidget  renameField;
    private ButtonWidget     btnNav, btnDel, btnYes, btnNo;
    private final List<ButtonWidget> rowBtns    = new ArrayList<>();
    private final List<ButtonWidget> renameBtns = new ArrayList<>();

    // Popup top-left
    private int px, py;

    public ChestRecordsScreen() {
        super(Text.translatable("chestmemory.records.title"));
    }

    // ── Init ──────────────────────────────────────────────────────────────
    @Override
    protected void init() {
        px = (width  - POP_W) / 2;
        py = (height - POP_H) / 2;
        refreshChests();
        buildActionButtons();
        buildRowButtons();
    }

    private void buildActionButtons() {
        int rpX  = px + LEFT_W + 3;
        int rpW  = POP_W - LEFT_W - 3;
        int btnY = py + POP_H - FOOT_H + (FOOT_H - 16) / 2;

        // İkon varsa PNG, yoksa unicode prefix
        IconManager im = IconManager.get();
        String navLabel = (im.hasPng("navigate") ? "  " : im.fallback("navigate") + "  ")
                        + Text.translatable("chestmemory.records.btn.navigate").getString();
        String delLabel = (im.hasPng("delete")   ? "  " : im.fallback("delete")   + "  ")
                        + Text.translatable("chestmemory.records.btn.delete").getString();

        btnNav = btn(Text.literal(navLabel),  b -> doNavigate(), rpX + 6,           btnY, 100, 16);
        btnDel = btn(Text.literal(delLabel),  b -> { confirmDel = true; syncBtns(); },
                     rpX + rpW - 94,           btnY, 88,  16);
        btnYes = btn(Text.translatable("chestmemory.records.btn.confirm_yes"),
                     b -> doDelete(),          rpX + rpW / 2 - 56, btnY, 52, 16);
        btnNo  = btn(Text.translatable("chestmemory.records.btn.confirm_no"),
                     b -> { confirmDel = false; syncBtns(); },
                     rpX + rpW / 2 + 2,        btnY, 52, 16);

        addDrawableChild(btnNav);
        addDrawableChild(btnDel);
        addDrawableChild(btnYes);
        addDrawableChild(btnNo);
        syncBtns();
    }

    private ButtonWidget btn(Text label, ButtonWidget.PressAction action,
                              int x, int y, int w, int h) {
        return ButtonWidget.builder(label, action).dimensions(x, y, w, h).build();
    }

    private void buildRowButtons() {
        rowBtns.forEach(this::remove);
        renameBtns.forEach(this::remove);
        rowBtns.clear();
        renameBtns.clear();

        int listY   = py + TITLE_H;
        int listH   = POP_H - TITLE_H - FOOT_H;
        int visible = listH / ROW_H;

        for (int i = 0; i < visible; i++) {
            final int pos = i;
            int ry = listY + i * ROW_H;

            // Satır seçme butonu (şeffaf)
            ButtonWidget sel = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + pos;
                if (idx < chests.size()) {
                    selIdx = idx;
                    cancelRename();
                    confirmDel = false;
                    syncBtns();
                }
            }).dimensions(px, ry, LEFT_W - 18, ROW_H).build();
            sel.setAlpha(0f);
            rowBtns.add(sel);
            addDrawableChild(sel);

            // Rename butonu (sağ kenarda, şeffaf)
            ButtonWidget ren = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + pos;
                if (idx < chests.size()) startRename(idx);
            }).dimensions(px + LEFT_W - 18, ry, 18, ROW_H).build();
            ren.setAlpha(0f);
            renameBtns.add(ren);
            addDrawableChild(ren);
        }
    }

    private void syncBtns() {
        boolean has = !chests.isEmpty();
        btnNav.visible = has && !confirmDel;
        btnDel.visible = has && !confirmDel;
        btnYes.visible = has &&  confirmDel;
        btnNo.visible  = has &&  confirmDel;
    }

    private void refreshChests() {
        chests = new ArrayList<>(ChestStorage.getInstance().getAll());
        if (selIdx >= chests.size()) selIdx = Math.max(0, chests.size() - 1);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        ctx.fill(0, 0, width, height, 0x88000000);
        // Outer bevel
        bevel(ctx, px - 2, py - 2, POP_W + 4, POP_H + 4);
        // Panel bg
        ctx.fill(px, py, px + POP_W, py + POP_H, C_BG);

        renderTitleBar(ctx);
        renderLeftPanel(ctx, mouseX, mouseY);
        renderDivider(ctx);
        renderRightPanel(ctx, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Title bar ─────────────────────────────────────────────────────────
    private void renderTitleBar(DrawContext ctx) {
        ctx.fill(px, py, px + POP_W, py + TITLE_H, C_SLOT_BG);
        ctx.fill(px, py + TITLE_H - 1, px + POP_W, py + TITLE_H, C_DK);

        // İkon (PNG veya unicode)
        IconManager im = IconManager.get();
        int iconX = px + 5, iconY = py + 2;
        if (im.hasPng("chest")) {
            im.draw(ctx, "chest", iconX, iconY);
        }
        int textX = im.hasPng("chest") ? iconX + 19 : iconX;
        String title = (im.hasPng("chest") ? "" : im.fallback("chest") + "  ")
                + Text.translatable("chestmemory.records.title").getString()
                + "  (" + chests.size() + ")";
        ctx.drawTextWithShadow(textRenderer, Text.literal(title),
                textX, py + (TITLE_H - textRenderer.fontHeight) / 2, C_TEXT);
    }

    // ── Left panel (sandık listesi) ───────────────────────────────────────
    private void renderLeftPanel(DrawContext ctx, int mouseX, int mouseY) {
        int listY   = py + TITLE_H;
        int listH   = POP_H - TITLE_H - FOOT_H;
        int visible = listH / ROW_H;

        ctx.fill(px, listY, px + LEFT_W, listY + listH, C_SLOT_BG);

        if (chests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.empty_list"),
                    px + 5, listY + 8, C_TEXT_DIM);
            ctx.fill(px, listY + listH, px + LEFT_W, listY + listH + 1, C_DK);
            return;
        }

        MinecraftClient mc   = MinecraftClient.getInstance();
        String activeDim     = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        double ppx           = mc.player != null ? mc.player.getX() : 0;
        double ppz           = mc.player != null ? mc.player.getZ() : 0;
        String diffStr       = Text.translatable("chestmemory.records.different_dimension").getString();
        String blkStr        = Text.translatable("chestmemory.chest.blk").getString();
        IconManager im       = IconManager.get();

        for (int i = scrollOffset; i < Math.min(chests.size(), scrollOffset + visible); i++) {
            ChestRecord rec = chests.get(i);
            int  ry   = listY + (i - scrollOffset) * ROW_H;
            boolean sel  = i == selIdx;
            boolean diff = !rec.isInDimension(activeDim);

            // Row bg
            ctx.fill(px, ry, px + LEFT_W, ry + ROW_H, sel ? C_ROW_SEL : C_SLOT_BG);

            // Seçili bevel
            if (sel) {
                ctx.fill(px, ry,          px + LEFT_W, ry + 1,       C_INSET_LT);
                ctx.fill(px, ry,          px + 1,      ry + ROW_H,   C_INSET_LT);
                ctx.fill(px, ry + ROW_H - 1, px + LEFT_W, ry + ROW_H, C_INSET_DK);
                ctx.fill(px + LEFT_W - 1, ry, px + LEFT_W, ry + ROW_H, C_INSET_DK);
                ctx.drawTextWithShadow(textRenderer, Text.literal("▶"),
                        px + 2, ry + (ROW_H - textRenderer.fontHeight) / 2, C_YELLOW);
            }

            // Sandık ikonu (16x16 veya unicode)
            int iconX = px + 13, iconY = ry + (ROW_H - 16) / 2;
            String chestIconName = rec.isDouble() ? "chest_double" : "chest";
            if (im.hasPng(chestIconName)) {
                im.draw(ctx, chestIconName, iconX, iconY,
                        diff ? 0xFF888888 : 0xFFFFFFFF);
            } else {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(im.fallback(chestIconName)),
                        iconX, iconY + 2,
                        diff ? C_TEXT_DIM : C_TEXT);
            }

            int textX = px + 33;

            // Rename field varsa çizme, yoksa isim çiz
            if (!(renamingIdx == i && renameField != null)) {
                String name = ChestStorage.getInstance().getDisplayName(rec);
                // Uzunsa kes
                String displayName = textRenderer.getWidth(name) > LEFT_W - 44
                        ? textRenderer.trimToWidth(name, LEFT_W - 50) + "…"
                        : name;
                ctx.drawTextWithShadow(textRenderer, Text.literal(displayName),
                        textX, ry + 6, diff ? C_TEXT_DIM : C_TEXT);
            }

            // Koordinat
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(rec.getX() + ", " + rec.getY() + ", " + rec.getZ()),
                    textX, ry + 16, diff ? C_TEXT_DIM : C_TEXT_GR);

            // Mesafe
            String dist = diff ? diffStr : ((int) rec.distanceTo(ppx, ppz)) + blkStr;
            ctx.drawTextWithShadow(textRenderer, Text.literal(dist),
                    textX, ry + 25, diff ? C_TEXT_DIM : C_CYAN);

            // Rename (✏) ikonu
            boolean hoverRen = mouseX >= px + LEFT_W - 18 && mouseX < px + LEFT_W
                    && mouseY >= ry && mouseY < ry + ROW_H;
            int renIconX = px + LEFT_W - 14, renIconY = ry + (ROW_H - 10) / 2;
            if (im.hasPng("rename")) {
                im.draw(ctx, "rename", renIconX - 2, renIconY - 3,
                        hoverRen ? 0xFFFFFFFF : 0xFF888888);
            } else {
                ctx.drawTextWithShadow(textRenderer, Text.literal(im.fallback("rename")),
                        renIconX, renIconY,
                        hoverRen ? C_TEXT : C_TEXT_DIM);
            }

            // Satır ayracı
            ctx.fill(px, ry + ROW_H - 1, px + LEFT_W, ry + ROW_H, C_DK);
        }

        // Alt ayraç
        ctx.fill(px, listY + listH, px + LEFT_W, listY + listH + 1, C_DK);
    }

    // ── Divider ───────────────────────────────────────────────────────────
    private void renderDivider(DrawContext ctx) {
        ctx.fill(px + LEFT_W,     py + TITLE_H, px + LEFT_W + 1, py + POP_H - FOOT_H, C_DK);
        ctx.fill(px + LEFT_W + 1, py + TITLE_H, px + LEFT_W + 2, py + POP_H - FOOT_H, C_LT);
    }

    // ── Right panel (slot grid + detay) ───────────────────────────────────
    private void renderRightPanel(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = px + LEFT_W + 3;
        int rpW = POP_W - LEFT_W - 3;
        int rpY = py + TITLE_H;

        footerDivider(ctx, rpX, rpW);

        if (chests.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.select_hint"),
                    rpX + rpW / 2, py + POP_H / 2 - 10, C_TEXT_DIM);
            return;
        }

        ChestRecord rec   = chests.get(selIdx);
        String      name  = ChestStorage.getInstance().getDisplayName(rec);
        int         slots = rec.getSlotCount(); // 27 veya 54
        int         rows  = slots / 9;

        // Sub-header
        ctx.fill(rpX, rpY, rpX + rpW, rpY + TITLE_H, C_SLOT_BG);
        ctx.fill(rpX, rpY + TITLE_H - 1, rpX + rpW, rpY + TITLE_H, C_DK);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name),
                rpX + 5, rpY + (TITLE_H - textRenderer.fontHeight) / 2, C_TEXT);

        // Dim badge
        String dimShort = shortDim(rec.getDimension());
        int badgeW = textRenderer.getWidth(dimShort) + 8;
        int badgeX = rpX + rpW - badgeW - 3;
        int badgeY = rpY + (TITLE_H - textRenderer.fontHeight) / 2 - 2;
        inset(ctx, badgeX, badgeY, badgeW, textRenderer.fontHeight + 4);
        ctx.drawTextWithShadow(textRenderer, Text.literal(dimShort),
                badgeX + 4, badgeY + 2, C_TEXT);

        // Slot label
        int bodyY = rpY + TITLE_H + 5;
        String slotLabel = Text.translatable(rec.isDouble()
                ? "chestmemory.records.slot_label_double"
                : "chestmemory.records.slot_label_single").getString();
        ctx.drawTextWithShadow(textRenderer, Text.literal(slotLabel),
                rpX + 5, bodyY, C_TEXT_DIM);
        bodyY += textRenderer.fontHeight + 3;

        // ── Slot grid ─────────────────────────────────────────────────────
        List<ChestItem> items = rec.getItems();
        int gridW = 9 * (SLOT_S + SLOT_G) - SLOT_G;

        // Grid'i sağ panel içinde ortala
        int gridX = rpX + (rpW - gridW) / 2;

        for (int slot = 0; slot < slots; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            // Çift sandıkta iki grup arasına 3px boşluk
            int extraY = (rec.isDouble() && row >= 3) ? 3 : 0;
            int sx = gridX + col * (SLOT_S + SLOT_G);
            int sy = bodyY + row * (SLOT_S + SLOT_G) + extraY;

            slotBox(ctx, sx, sy, SLOT_S);

            ChestItem ci = getItemForSlot(items, slot);
            if (ci != null) {
                ItemStack stack = buildStack(ci.getItemId());
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 1, sy + 1);
                    if (ci.getCount() > 1) {
                        String c = String.valueOf(ci.getCount());
                        ctx.drawTextWithShadow(textRenderer, Text.literal(c),
                                sx + SLOT_S - textRenderer.getWidth(c),
                                sy + SLOT_S - textRenderer.fontHeight + 1, C_YELLOW);
                    }
                }
                // Tooltip
                if (mouseX >= sx && mouseX < sx + SLOT_S && mouseY >= sy && mouseY < sy + SLOT_S)
                    ctx.drawTooltip(textRenderer,
                            Text.literal(ci.getDisplayName() + " ×" + ci.getCount()),
                            mouseX, mouseY);
            }
        }

        // Son güncelleme
        int metaY = bodyY + rows * (SLOT_S + SLOT_G) + (rec.isDouble() ? 6 : 3);
        String updStr = Text.translatable("chestmemory.records.last_updated").getString()
                + (rec.getLastUpdated() != null
                   ? rec.getLastUpdated().substring(0, Math.min(16, rec.getLastUpdated().length()))
                   : "?");
        ctx.drawTextWithShadow(textRenderer, Text.literal(updStr),
                rpX + 5, metaY, C_TEXT_DIM);

        // Onay mesajı
        if (confirmDel) {
            int msgY = py + POP_H - FOOT_H + 3;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.confirm_delete"),
                    rpX + rpW / 2, msgY, C_RED);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (mx >= px && mx < px + LEFT_W) {
            int visible = (POP_H - TITLE_H - FOOT_H) / ROW_H;
            scrollOffset = (int) Math.max(0,
                    Math.min(Math.max(0, chests.size() - visible), scrollOffset - vy));
            buildRowButtons();
            return true;
        }
        return super.mouseScrolled(mx, my, hx, vy);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (renamingIdx >= 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitRename();  return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE)                                  { cancelRename();  return true; }
            return super.keyPressed(input);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)                                      { close();         return true; }
        if (key == GLFW.GLFW_KEY_UP   && selIdx > 0)                          { selIdx--; confirmDel = false; syncBtns(); return true; }
        if (key == GLFW.GLFW_KEY_DOWN && selIdx < chests.size() - 1)          { selIdx++; confirmDel = false; syncBtns(); return true; }
        return super.keyPressed(input);
    }

    // ── Actions ───────────────────────────────────────────────────────────
    private void doNavigate() {
        if (chests.isEmpty()) return;
        ChestMemoryHud.setTarget(chests.get(selIdx));
        close();
    }

    private void doDelete() {
        if (chests.isEmpty()) return;
        ChestStorage.getInstance().delete(chests.get(selIdx).getId());
        confirmDel = false;
        refreshChests();
        buildRowButtons();
        syncBtns();
    }

    private void startRename(int idx) {
        cancelRename();
        renamingIdx = idx;
        ChestRecord rec = chests.get(idx);
        int listY = py + TITLE_H;
        int ry    = listY + (idx - scrollOffset) * ROW_H;
        renameField = new TextFieldWidget(textRenderer,
                px + 33, ry + 6, LEFT_W - 52, 10, null, Text.empty());
        renameField.setMaxLength(32);
        renameField.setText(rec.getCustomName() != null ? rec.getCustomName() : "");
        renameField.setFocused(true);
        renameField.setDrawsBackground(false);
        renameField.setEditableColor(0xFFFFFF);
        addDrawableChild(renameField);
    }

    private void commitRename() {
        if (renamingIdx < 0 || renameField == null) return;
        ChestStorage.getInstance().rename(chests.get(renamingIdx).getId(),
                renameField.getText().trim());
        refreshChests();
        cancelRename();
    }

    private void cancelRename() {
        if (renameField != null) { remove(renameField); renameField = null; }
        renamingIdx = -1;
    }

    // ── Draw helpers ──────────────────────────────────────────────────────
    /** Raised panel bevel */
    private void bevel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,     y,     x+w,   y+2,   C_LT);
        ctx.fill(x,     y,     x+2,   y+h,   C_LT);
        ctx.fill(x,     y+h-2, x+w,   y+h,   C_DK);
        ctx.fill(x+w-2, y,     x+w,   y+h,   C_DK);
    }

    /** Sunken inset */
    private void inset(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,     y,     x+w, y+h,   C_INSET);
        ctx.fill(x,     y,     x+w, y+1,   C_INSET_LT);
        ctx.fill(x,     y,     x+1, y+h,   C_INSET_LT);
        ctx.fill(x,     y+h-1, x+w, y+h,   C_INSET_DK);
        ctx.fill(x+w-1, y,     x+w, y+h,   C_INSET_DK);
    }

    /** Vanilla slot box */
    private void slotBox(DrawContext ctx, int x, int y, int s) {
        ctx.fill(x,     y,     x+s, y+s, C_SLOT_BG);
        ctx.fill(x,     y,     x+s, y+1, C_SLOT_LT);
        ctx.fill(x,     y,     x+1, y+s, C_SLOT_LT);
        ctx.fill(x,     y+s-1, x+s, y+s, C_SLOT_DK);
        ctx.fill(x+s-1, y,     x+s, y+s, C_SLOT_DK);
    }

    private void footerDivider(DrawContext ctx, int rpX, int rpW) {
        int fy = py + POP_H - FOOT_H;
        ctx.fill(rpX, fy, rpX + rpW, fy + 1, C_DK);
    }

    // ── Util ──────────────────────────────────────────────────────────────
    private ChestItem getItemForSlot(List<ChestItem> items, int slot) {
        for (ChestItem ci : items) if (ci.getSlot() == slot) return ci;
        return null;
    }

    private ItemStack buildStack(String itemId) {
        try { return Registries.ITEM.get(Identifier.of(itemId)).getDefaultStack(); }
        catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String shortDim(String dim) {
        if (dim == null) return "?";
        if (dim.contains("overworld")) return "Overworld";
        if (dim.contains("nether"))    return "Nether";
        if (dim.contains("end"))       return "The End";
        return dim.substring(dim.lastIndexOf(':') + 1);
    }
}