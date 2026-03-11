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

public class ChestRecordsScreen extends Screen {

    // ── Popup dimensions ──────────────────────────────────────────────────
    private static final int POP_W    = 620;
    private static final int POP_H    = 400;
    private static final int LEFT_W   = 180;
    private static final int TITLE_H  = 22;
    private static final int FOOTER_H = 32;
    private static final int ROW_H    = 38;
    private static final int SLOT_S   = 18;
    private static final int SLOT_G   = 2;

    // ── Vanilla colors ────────────────────────────────────────────────────
    private static final int C_PANEL    = 0xFFC6C6C6;
    private static final int C_PANEL_LT = 0xFFFFFFFF;
    private static final int C_PANEL_DK = 0xFF555555;
    private static final int C_INSET_BG = 0xFF000000;
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
    private List<ChestRecord> chests  = new ArrayList<>();
    private int selectedIndex         = 0;
    private int scrollOffset          = 0;
    private int renamingIndex         = -1;
    private TextFieldWidget renameField;
    private boolean confirmDelete     = false;

    private ButtonWidget btnNavigate, btnDelete, btnConfirmYes, btnConfirmNo;
    private final List<ButtonWidget> rowSelectBtns = new ArrayList<>();
    private final List<ButtonWidget> rowRenameBtns = new ArrayList<>();

    // Popup top-left corner (computed in init)
    private int px, py;

    public ChestRecordsScreen() {
        super(Text.translatable("chestmemory.records.title"));
    }

    @Override
    protected void init() {
        px = (width  - POP_W) / 2;
        py = (height - POP_H) / 2;
        refreshChests();
        buildActionButtons();
        buildRowButtons();
    }

    // ── Buttons ───────────────────────────────────────────────────────────
    private void buildActionButtons() {
        int rightPanelX = px + LEFT_W + 4;
        int rightPanelW = POP_W - LEFT_W - 4;
        int btnY        = py + POP_H - FOOTER_H + (FOOTER_H - 20) / 2;

        btnNavigate   = vanillaButton(Text.translatable("chestmemory.records.btn.navigate"),
                b -> navigateToSelected(),
                rightPanelX + 8, btnY, 110, 20);
        btnDelete     = vanillaButton(Text.translatable("chestmemory.records.btn.delete"),
                b -> { confirmDelete = true; updateButtons(); },
                rightPanelX + rightPanelW - 108, btnY, 100, 20);
        btnConfirmYes = vanillaButton(Text.translatable("chestmemory.records.btn.confirm_yes"),
                b -> deleteSelected(),
                rightPanelX + rightPanelW / 2 - 60, btnY, 56, 20);
        btnConfirmNo  = vanillaButton(Text.translatable("chestmemory.records.btn.confirm_no"),
                b -> { confirmDelete = false; updateButtons(); },
                rightPanelX + rightPanelW / 2 + 4, btnY, 56, 20);

        addDrawableChild(btnNavigate);
        addDrawableChild(btnDelete);
        addDrawableChild(btnConfirmYes);
        addDrawableChild(btnConfirmNo);
        updateButtons();
    }

    private ButtonWidget vanillaButton(Text label, ButtonWidget.PressAction action,
                                       int x, int y, int w, int h) {
        return ButtonWidget.builder(label, action).dimensions(x, y, w, h).build();
    }

    private void buildRowButtons() {
        for (ButtonWidget b : rowSelectBtns) remove(b);
        for (ButtonWidget b : rowRenameBtns) remove(b);
        rowSelectBtns.clear();
        rowRenameBtns.clear();

        int listY     = py + TITLE_H;
        int listH     = POP_H - TITLE_H - FOOTER_H;
        int visible   = listH / ROW_H;

        for (int i = 0; i < visible; i++) {
            final int rowPos = i;
            int ry = listY + i * ROW_H;

            ButtonWidget sel = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + rowPos;
                if (idx < chests.size()) {
                    selectedIndex = idx;
                    cancelRenaming();
                    confirmDelete = false;
                    updateButtons();
                }
            }).dimensions(px, ry, LEFT_W - 20, ROW_H).build();
            sel.setAlpha(0f);
            rowSelectBtns.add(sel);
            addDrawableChild(sel);

            ButtonWidget ren = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + rowPos;
                if (idx < chests.size()) startRenaming(idx);
            }).dimensions(px + LEFT_W - 20, ry, 20, ROW_H).build();
            ren.setAlpha(0f);
            rowRenameBtns.add(ren);
            addDrawableChild(ren);
        }
    }

    private void updateButtons() {
        boolean has = !chests.isEmpty();
        btnNavigate.visible   = has && !confirmDelete;
        btnDelete.visible     = has && !confirmDelete;
        btnConfirmYes.visible = has && confirmDelete;
        btnConfirmNo.visible  = has && confirmDelete;
    }

    private void refreshChests() {
        chests = new ArrayList<>(ChestStorage.getInstance().getAll());
        if (selectedIndex >= chests.size()) selectedIndex = Math.max(0, chests.size() - 1);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        ctx.fill(0, 0, width, height, 0x88000000);

        // Outer bevel
        vanillaPanel(ctx, px - 2, py - 2, POP_W + 4, POP_H + 4);
        // Main panel bg
        ctx.fill(px, py, px + POP_W, py + POP_H, C_PANEL);

        renderTitleBar(ctx);
        renderLeftPanel(ctx, mouseX, mouseY);
        renderDivider(ctx);
        renderRightPanel(ctx, mouseX, mouseY);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderTitleBar(DrawContext ctx) {
        ctx.fill(px, py, px + POP_W, py + TITLE_H, C_SLOT_BG);
        ctx.fill(px, py + TITLE_H - 1, px + POP_W, py + TITLE_H, C_PANEL_DK);
        String title = "📦  " + Text.translatable("chestmemory.records.title").getString()
                + "  (" + chests.size() + ")";
        ctx.drawTextWithShadow(textRenderer, Text.literal(title),
                px + 8, py + (TITLE_H - textRenderer.fontHeight) / 2, C_TEXT);
    }

    private void renderLeftPanel(DrawContext ctx, int mouseX, int mouseY) {
        int listY  = py + TITLE_H;
        int listH  = POP_H - TITLE_H - FOOTER_H;
        int visible = listH / ROW_H;

        // List bg
        ctx.fill(px, listY, px + LEFT_W, listY + listH, C_SLOT_BG);

        if (chests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.empty_list"),
                    px + 6, listY + 10, C_TEXT_DIM);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String activeDim   = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        double ppx = mc.player != null ? mc.player.getX() : 0;
        double ppz = mc.player != null ? mc.player.getZ() : 0;
        String diffStr = Text.translatable("chestmemory.records.different_dimension").getString();
        String blkStr  = Text.translatable("chestmemory.chest.blk").getString();

        for (int i = scrollOffset; i < Math.min(chests.size(), scrollOffset + visible); i++) {
            ChestRecord rec = chests.get(i);
            int ry   = listY + (i - scrollOffset) * ROW_H;
            boolean sel  = i == selectedIndex;
            boolean diff = !rec.isInDimension(activeDim);

            // Row bg
            int rowBg = sel ? C_ROW_SEL : C_ROW_BG;
            ctx.fill(px, ry, px + LEFT_W, ry + ROW_H, rowBg);

            // Selected: sunken bevel
            if (sel) {
                ctx.fill(px,          ry,         px+LEFT_W, ry+1,        C_INSET_LT);
                ctx.fill(px,          ry,         px+1,      ry+ROW_H,    C_INSET_LT);
                ctx.fill(px,          ry+ROW_H-1, px+LEFT_W, ry+ROW_H,    C_INSET_DK);
                ctx.fill(px+LEFT_W-1, ry,         px+LEFT_W, ry+ROW_H,    C_INSET_DK);
                // Arrow
                ctx.drawTextWithShadow(textRenderer, Text.literal("▶"),
                        px + 3, ry + (ROW_H - textRenderer.fontHeight) / 2, C_YELLOW);
            }

            // Green/gray dot
            int dotX = px + 14, dotY = ry + ROW_H / 2 - 3;
            ctx.fill(dotX, dotY, dotX + 6, dotY + 6, diff ? 0xFF888888 : C_GREEN);
            ctx.fill(dotX, dotY, dotX + 6, dotY + 1, 0xFF000000); // dot border top
            ctx.fill(dotX, dotY, dotX + 1, dotY + 6, 0xFF000000); // dot border left

            // Name + double badge
            int textX = px + 24;
            if (!(renamingIndex == i && renameField != null)) {
                String dispName = ChestStorage.getInstance().getDisplayName(rec);
                ctx.drawTextWithShadow(textRenderer, Text.literal(dispName),
                        textX, ry + 6, diff ? C_TEXT_DIM : (sel ? C_TEXT : C_TEXT));
                if (rec.isDouble()) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal(" ⊞"),
                            textX + textRenderer.getWidth(dispName), ry + 6,
                            diff ? C_TEXT_DIM : C_TEXT_GR);
                }
            }

            // Coords
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(rec.getX() + ", " + rec.getY() + ", " + rec.getZ()),
                    textX, ry + 17, diff ? C_TEXT_DIM : C_TEXT_GR);

            // Distance
            String dist = diff ? diffStr : ((int) rec.distanceTo(ppx, ppz)) + blkStr;
            ctx.drawTextWithShadow(textRenderer, Text.literal(dist),
                    textX, ry + 27, diff ? C_TEXT_DIM : C_CYAN);

            // Edit button
            boolean hoverEdit = mouseX >= px + LEFT_W - 20 && mouseX < px + LEFT_W
                    && mouseY >= ry && mouseY < ry + ROW_H;
            ctx.drawTextWithShadow(textRenderer, Text.literal("✏"),
                    px + LEFT_W - 15, ry + (ROW_H - textRenderer.fontHeight) / 2,
                    hoverEdit ? C_TEXT : C_TEXT_DIM);

            // Row divider
            ctx.fill(px, ry + ROW_H - 1, px + LEFT_W, ry + ROW_H, C_PANEL_DK);
        }

        // Footer divider
        ctx.fill(px, listY + listH, px + LEFT_W, listY + listH + 1, C_PANEL_DK);
    }

    private void renderDivider(DrawContext ctx) {
        ctx.fill(px + LEFT_W,     py + TITLE_H,
                 px + LEFT_W + 1, py + POP_H - FOOTER_H, C_PANEL_DK);
        ctx.fill(px + LEFT_W + 1, py + TITLE_H,
                 px + LEFT_W + 2, py + POP_H - FOOTER_H, C_PANEL_LT);
    }

    private void renderRightPanel(DrawContext ctx, int mouseX, int mouseY) {
        int rpX = px + LEFT_W + 4;
        int rpW = POP_W - LEFT_W - 4;
        int rpY = py + TITLE_H;

        if (chests.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.select_hint"),
                    rpX + rpW / 2, py + POP_H / 2, C_TEXT_DIM);
            renderFooterDivider(ctx, rpX, rpW);
            return;
        }

        ChestRecord rec   = chests.get(selectedIndex);
        String name       = ChestStorage.getInstance().getDisplayName(rec);
        int slotCount     = rec.getSlotCount();
        int rows          = slotCount / 9;

        // Sub-header
        ctx.fill(rpX, rpY, rpX + rpW, rpY + TITLE_H, C_ROW_BG);
        ctx.fill(rpX, rpY + TITLE_H - 1, rpX + rpW, rpY + TITLE_H, C_PANEL_DK);

        ctx.drawTextWithShadow(textRenderer, Text.literal(name),
                rpX + 6, rpY + (TITLE_H - textRenderer.fontHeight) / 2, C_TEXT);

        // Coords badge (inset style)
        String coords = rec.getX() + ", " + rec.getY() + ", " + rec.getZ()
                      + "  " + shortDim(rec.getDimension());
        int badgeW = textRenderer.getWidth(coords) + 10;
        int badgeX = rpX + rpW - badgeW - 4;
        int badgeY = rpY + (TITLE_H - textRenderer.fontHeight) / 2;
        inset(ctx, badgeX - 2, badgeY - 3, badgeW + 4, textRenderer.fontHeight + 6);
        ctx.drawTextWithShadow(textRenderer, Text.literal(coords),
                badgeX + 2, badgeY, C_TEXT);

        // Slot label
        int bodyY = rpY + TITLE_H + 8;
        String slotLabel = Text.translatable(rec.isDouble()
                ? "chestmemory.records.slot_label_double"
                : "chestmemory.records.slot_label_single").getString();
        ctx.drawTextWithShadow(textRenderer, Text.literal(slotLabel),
                rpX + 6, bodyY, C_TEXT_DIM);
        bodyY += textRenderer.fontHeight + 5;

        // Slot grid
        List<ChestItem> items = rec.getItems();
        for (int slot = 0; slot < slotCount; slot++) {
            int col    = slot % 9;
            int row    = slot / 9;
            int extraY = (rec.isDouble() && row >= 3) ? 4 : 0;
            int sx     = rpX + 6 + col * (SLOT_S + SLOT_G);
            int sy     = bodyY + row * (SLOT_S + SLOT_G) + extraY;

            slotBox(ctx, sx, sy, SLOT_S);

            ChestItem ci = getItemForSlot(items, slot);
            if (ci != null) {
                ItemStack stack = getStack(ci.getItemId());
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 1, sy + 1);
                    if (ci.getCount() > 1) {
                        String c = String.valueOf(ci.getCount());
                        ctx.drawTextWithShadow(textRenderer, Text.literal(c),
                                sx + SLOT_S - textRenderer.getWidth(c),
                                sy + SLOT_S - textRenderer.fontHeight, C_YELLOW);
                    }
                }
                if (mouseX >= sx && mouseX < sx + SLOT_S && mouseY >= sy && mouseY < sy + SLOT_S)
                    ctx.drawTooltip(textRenderer,
                            Text.literal(ci.getDisplayName() + " x" + ci.getCount()), mouseX, mouseY);
            }
        }

        // Last updated
        int lastY = bodyY + rows * (SLOT_S + SLOT_G) + (rec.isDouble() ? 8 : 4);
        String updPfx = Text.translatable("chestmemory.records.last_updated").getString();
        String updVal = rec.getLastUpdated() != null
                ? rec.getLastUpdated().substring(0, Math.min(16, rec.getLastUpdated().length())) : "?";
        ctx.drawTextWithShadow(textRenderer, Text.literal(updPfx + updVal),
                rpX + 6, lastY, C_TEXT_DIM);

        // Confirm delete message
        if (confirmDelete) {
            int fY = py + POP_H - FOOTER_H + (FOOTER_H - 20) / 2 - 2;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.records.confirm_delete"),
                    rpX + rpW / 2, fY - 8, C_RED);
        }

        renderFooterDivider(ctx, rpX, rpW);
    }

    private void renderFooterDivider(DrawContext ctx, int rpX, int rpW) {
        int fy = py + POP_H - FOOTER_H;
        ctx.fill(rpX, fy, rpX + rpW, fy + 1, C_PANEL_DK);
    }

    // ── Scroll ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (mx >= px && mx < px + LEFT_W) {
            int listH = POP_H - TITLE_H - FOOTER_H;
            int visible = listH / ROW_H;
            scrollOffset = (int) Math.max(0,
                    Math.min(chests.size() - visible, scrollOffset - vy));
            buildRowButtons();
            return true;
        }
        return super.mouseScrolled(mx, my, hx, vy);
    }

    // ── Keyboard ──────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (renamingIndex >= 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitRename();  return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE)                                  { cancelRenaming(); return true; }
            return super.keyPressed(input);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)                                     { close();         return true; }
        if (key == GLFW.GLFW_KEY_UP   && selectedIndex > 0)                  { selectedIndex--; return true; }
        if (key == GLFW.GLFW_KEY_DOWN && selectedIndex < chests.size() - 1)  { selectedIndex++; return true; }
        return super.keyPressed(input);
    }

    // ── Actions ───────────────────────────────────────────────────────────
    private void navigateToSelected() {
        if (chests.isEmpty()) return;
        ChestMemoryHud.setTarget(chests.get(selectedIndex));
        close();
    }

    private void deleteSelected() {
        if (chests.isEmpty()) return;
        ChestStorage.getInstance().delete(chests.get(selectedIndex).getId());
        confirmDelete = false;
        refreshChests();
        buildRowButtons();
        updateButtons();
    }

    private void startRenaming(int idx) {
        cancelRenaming();
        renamingIndex = idx;
        ChestRecord rec = chests.get(idx);
        String current  = rec.getCustomName() != null ? rec.getCustomName() : "";
        int listY = py + TITLE_H;
        int ry    = listY + (idx - scrollOffset) * ROW_H;
        renameField = new TextFieldWidget(textRenderer,
                px + 24, ry + 6, LEFT_W - 46, 12, null, Text.empty());
        renameField.setMaxLength(32);
        renameField.setText(current);
        renameField.setFocused(true);
        renameField.setDrawsBackground(false);
        renameField.setEditableColor(0xFFFFFF);
        addDrawableChild(renameField);
    }

    private void commitRename() {
        if (renamingIndex < 0 || renameField == null) return;
        ChestStorage.getInstance().rename(chests.get(renamingIndex).getId(),
                renameField.getText().trim());
        refreshChests();
        cancelRenaming();
    }

    private void cancelRenaming() {
        if (renameField != null) { remove(renameField); renameField = null; }
        renamingIndex = -1;
    }

    // ── Vanilla draw helpers ──────────────────────────────────────────────
    private void vanillaPanel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,     y,     x+w,   y+2,   C_PANEL_LT);
        ctx.fill(x,     y,     x+2,   y+h,   C_PANEL_LT);
        ctx.fill(x,     y+h-2, x+w,   y+h,   C_PANEL_DK);
        ctx.fill(x+w-2, y,     x+w,   y+h,   C_PANEL_DK);
    }

    private void inset(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,     y,     x+w,   y+h,   C_INSET_BG);
        ctx.fill(x,     y,     x+w,   y+1,   C_INSET_LT);
        ctx.fill(x,     y,     x+1,   y+h,   C_INSET_LT);
        ctx.fill(x,     y+h-1, x+w,   y+h,   C_INSET_DK);
        ctx.fill(x+w-1, y,     x+w,   y+h,   C_INSET_DK);
    }

    private void slotBox(DrawContext ctx, int x, int y, int s) {
        ctx.fill(x,     y,     x+s,   y+s,   C_SLOT_BG);
        ctx.fill(x,     y,     x+s,   y+1,   C_SLOT_LT);
        ctx.fill(x,     y,     x+1,   y+s,   C_SLOT_LT);
        ctx.fill(x,     y+s-1, x+s,   y+s,   C_SLOT_DK);
        ctx.fill(x+s-1, y,     x+s,   y+s,   C_SLOT_DK);
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private ChestItem getItemForSlot(List<ChestItem> items, int slot) {
        for (ChestItem ci : items) if (ci.getSlot() == slot) return ci;
        return null;
    }

    private ItemStack getStack(String itemId) {
        try { return Registries.ITEM.get(Identifier.of(itemId)).getDefaultStack(); }
        catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String shortDim(String dim) {
        if (dim == null) return "?";
        if (dim.contains("overworld")) return "Overworld";
        if (dim.contains("nether"))    return "Nether";
        if (dim.contains("end"))       return "The End";
        return dim;
    }

    @Override public boolean shouldPause() { return false; }
}