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

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int LEFT_W    = 200;
    private static final int TITLE_H   = 26;
    private static final int ROW_H     = 44;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP  = 2;
    private static final int FOOTER_H  = 38;

    // ── Colors ────────────────────────────────────────────────────────────
    private static final int C_BG       = 0xFF111418;
    private static final int C_BG2      = 0xFF0e1014;
    private static final int C_LEFT     = 0xFF0e1014;
    private static final int C_BORDER   = 0xFF1e2228;
    private static final int C_ACCENT   = 0xFF4ade80;
    private static final int C_TEXT     = 0xFFd0d0d0;
    private static final int C_SUB      = 0xFF444444;
    private static final int C_YELLOW   = 0xFFf59e0b;
    private static final int C_BLUE     = 0xFF38bdf8;
    private static final int C_RED      = 0xFFf87171;
    private static final int C_DIM_DOT  = 0xFF333333;
    private static final int C_DIM_TEXT = 0xFF333333;
    private static final int C_SLOT_BG  = 0xFF1a1d24;

    // ── State ─────────────────────────────────────────────────────────────
    private List<ChestRecord> chests   = new ArrayList<>();
    private int selectedIndex          = 0;
    private int scrollOffset           = 0;
    private int renamingIndex          = -1;
    private TextFieldWidget renameField;
    private boolean confirmDelete      = false;

    private ButtonWidget btnNavigate, btnDelete, btnConfirmYes, btnConfirmNo;
    private final List<ButtonWidget> rowSelectBtns = new ArrayList<>();
    private final List<ButtonWidget> rowRenameBtns = new ArrayList<>();

    public ChestRecordsScreen() {
        super(Text.literal("Sandık Kayıtları"));
    }

    @Override
    protected void init() {
        refreshChests();
        buildActionButtons();
        buildRowButtons();
    }

    private void buildActionButtons() {
        int rp   = LEFT_W;
        int rpW  = width - LEFT_W;
        int btnY = height - FOOTER_H + (FOOTER_H - 22) / 2;

        btnNavigate = ButtonWidget.builder(Text.literal("📍  Yön Göster"), b -> navigateToSelected())
                .dimensions(rp + 12, btnY, 120, 22).build();

        btnDelete = ButtonWidget.builder(Text.literal("🗑  Kaydı Sil"),
                b -> { confirmDelete = true; updateButtons(); })
                .dimensions(rp + rpW - 118, btnY, 106, 22).build();

        btnConfirmYes = ButtonWidget.builder(Text.literal("✔  Evet, Sil"), b -> deleteSelected())
                .dimensions(rp + rpW / 2 - 62, btnY, 58, 22).build();

        btnConfirmNo = ButtonWidget.builder(Text.literal("✖  Hayır"),
                b -> { confirmDelete = false; updateButtons(); })
                .dimensions(rp + rpW / 2 + 4, btnY, 58, 22).build();

        addDrawableChild(btnNavigate);
        addDrawableChild(btnDelete);
        addDrawableChild(btnConfirmYes);
        addDrawableChild(btnConfirmNo);
        updateButtons();
    }

    private void buildRowButtons() {
        for (ButtonWidget b : rowSelectBtns) remove(b);
        for (ButtonWidget b : rowRenameBtns) remove(b);
        rowSelectBtns.clear();
        rowRenameBtns.clear();

        int visibleRows = (height - TITLE_H) / ROW_H;
        for (int i = 0; i < visibleRows; i++) {
            final int rowPos = i;
            int ry = TITLE_H + i * ROW_H;

            ButtonWidget selBtn = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + rowPos;
                if (idx < chests.size()) {
                    selectedIndex = idx;
                    cancelRenaming();
                    confirmDelete = false;
                    updateButtons();
                }
            }).dimensions(0, ry, LEFT_W - 24, ROW_H).build();
            selBtn.setAlpha(0f);
            rowSelectBtns.add(selBtn);
            addDrawableChild(selBtn);

            ButtonWidget renBtn = ButtonWidget.builder(Text.empty(), b -> {
                int idx = scrollOffset + rowPos;
                if (idx < chests.size()) startRenaming(idx);
            }).dimensions(LEFT_W - 24, ry, 24, ROW_H).build();
            renBtn.setAlpha(0f);
            rowRenameBtns.add(renBtn);
            addDrawableChild(renBtn);
        }
    }

    private void updateButtons() {
        boolean hasChests = !chests.isEmpty();
        btnNavigate.visible   = hasChests && !confirmDelete;
        btnDelete.visible     = hasChests && !confirmDelete;
        btnConfirmYes.visible = hasChests && confirmDelete;
        btnConfirmNo.visible  = hasChests && confirmDelete;
    }

    private void refreshChests() {
        chests = new ArrayList<>(ChestStorage.getInstance().getAll());
        if (selectedIndex >= chests.size()) selectedIndex = Math.max(0, chests.size() - 1);
    }

    // ── Render ────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, C_BG);
        renderLeftPanel(ctx, mouseX, mouseY);
        renderRightPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderLeftPanel(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, LEFT_W, height, C_LEFT);
        ctx.fill(LEFT_W - 1, 0, LEFT_W, height, C_BORDER);

        ctx.fill(0, 0, LEFT_W, TITLE_H, C_BG2);
        ctx.fill(0, TITLE_H, LEFT_W, TITLE_H + 1, C_BORDER);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("📦  SANDIKLAR (" + chests.size() + ")"),
                10, (TITLE_H - textRenderer.fontHeight) / 2, C_YELLOW);

        if (chests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Henüz sandık açılmadı."), 10, TITLE_H + 14, C_SUB);
            return;
        }

        MinecraftClient mc  = MinecraftClient.getInstance();
        String activeDim    = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        double px           = mc.player != null ? mc.player.getX() : 0;
        double pz           = mc.player != null ? mc.player.getZ() : 0;
        int visibleRows     = (height - TITLE_H) / ROW_H;

        for (int i = scrollOffset; i < Math.min(chests.size(), scrollOffset + visibleRows); i++) {
            ChestRecord rec = chests.get(i);
            int  ry         = TITLE_H + (i - scrollOffset) * ROW_H;
            boolean sel     = i == selectedIndex;
            boolean diff    = !rec.isInDimension(activeDim);

            if (sel) ctx.fill(0, ry, LEFT_W - 1, ry + ROW_H, 0x0A4ade80);
            if (sel) ctx.fill(0, ry, 2, ry + ROW_H, C_ACCENT);
            ctx.fill(0, ry + ROW_H - 1, LEFT_W - 1, ry + ROW_H, 0xFF141720);

            int dotX = 10, dotY = ry + ROW_H / 2 - 3;
            ctx.fill(dotX, dotY, dotX + 6, dotY + 6, diff ? C_DIM_DOT : C_ACCENT);

            // Double chest etiketi
            String nameLabel = ChestStorage.getInstance().getDisplayName(rec)
                    + (rec.isDouble() ? " §8[D]" : "");

            if (!(renamingIndex == i && renameField != null)) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(ChestStorage.getInstance().getDisplayName(rec)),
                        22, ry + 8, diff ? C_DIM_TEXT : (sel ? 0xFFFFFFFF : C_TEXT));
                // [D] rozeti ayrı renkte
                if (rec.isDouble()) {
                    ctx.drawTextWithShadow(textRenderer, Text.literal(" ⊞"),
                            22 + textRenderer.getWidth(ChestStorage.getInstance().getDisplayName(rec)),
                            ry + 8, diff ? C_DIM_TEXT : 0xFF555555);
                }
            }

            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(rec.getX() + ", " + rec.getY() + ", " + rec.getZ()),
                    22, ry + 20, diff ? C_DIM_TEXT : C_SUB);

            String dist = diff ? "Farklı boyut" : ((int) rec.distanceTo(px, pz)) + " blok";
            ctx.drawTextWithShadow(textRenderer, Text.literal(dist),
                    22, ry + 31, diff ? C_DIM_TEXT : C_BLUE);

            ctx.drawTextWithShadow(textRenderer, Text.literal("✏"),
                    LEFT_W - 18, ry + (ROW_H - textRenderer.fontHeight) / 2,
                    mouseX >= LEFT_W - 24 && mouseX < LEFT_W
                            && mouseY >= ry && mouseY < ry + ROW_H ? 0xFF888888 : 0xFF2a2a2a);
        }
    }

    private void renderRightPanel(DrawContext ctx, int mouseX, int mouseY) {
        int rp  = LEFT_W;
        int rpW = width - LEFT_W;

        if (chests.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Sandık seçin"),
                    rp + rpW / 2, height / 2, C_SUB);
            return;
        }

        ChestRecord rec  = chests.get(selectedIndex);
        String name      = ChestStorage.getInstance().getDisplayName(rec);
        int slotCount    = rec.getSlotCount(); // 27 veya 54
        int rows         = slotCount / 9;      // 3 veya 6
        String typeLabel = rec.isDouble() ? "ÇİFT SANDIK · " + slotCount + " SLOT"
                                          : "SANDIK · " + slotCount + " SLOT";
        String coords    = rec.getX() + ", " + rec.getY() + ", " + rec.getZ()
                + " · " + shortDim(rec.getDimension());

        // Header
        ctx.fill(rp, 0, width, TITLE_H, C_BG2);
        ctx.fill(rp, TITLE_H, width, TITLE_H + 1, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name + " — İçerik"),
                rp + 12, (TITLE_H - textRenderer.fontHeight) / 2, C_TEXT);

        int badgeW = textRenderer.getWidth(coords) + 12;
        int badgeX = width - badgeW - 10;
        int badgeY = (TITLE_H - textRenderer.fontHeight) / 2;
        ctx.fill(badgeX - 2, badgeY - 3, badgeX + badgeW, badgeY + textRenderer.fontHeight + 3, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal("📍 " + coords), badgeX + 2, badgeY, C_SUB);

        // Slot grid
        int gridX = rp + 14;
        int gridY = TITLE_H + 14;
        ctx.drawTextWithShadow(textRenderer, Text.literal(typeLabel), gridX, gridY, C_SUB);
        gridY += textRenderer.fontHeight + 6;

        List<ChestItem> items = rec.getItems();
        for (int slot = 0; slot < slotCount; slot++) {
            int col = slot % 9;
            int row = slot / 9;

            // Çift sandıkta iki yarıyı görsel olarak ayır (satır 3'ten sonra boşluk)
            int extraY = (rec.isDouble() && row >= 3) ? 4 : 0;

            int sx = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int sy = gridY + row * (SLOT_SIZE + SLOT_GAP) + extraY;

            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, C_SLOT_BG);
            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + 1, C_BORDER);
            ctx.fill(sx, sy, sx + 1, sy + SLOT_SIZE, C_BORDER);

            ChestItem ci = getItemForSlot(items, slot);
            if (ci != null) {
                ItemStack stack = getStack(ci.getItemId());
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 1, sy + 1);
                    if (ci.getCount() > 1) {
                        String c = String.valueOf(ci.getCount());
                        ctx.drawTextWithShadow(textRenderer, Text.literal(c),
                                sx + SLOT_SIZE - textRenderer.getWidth(c),
                                sy + SLOT_SIZE - textRenderer.fontHeight, C_YELLOW);
                    }
                }
                if (mouseX >= sx && mouseX < sx + SLOT_SIZE && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                    ctx.drawTooltip(textRenderer,
                            Text.literal(ci.getDisplayName() + " x" + ci.getCount()), mouseX, mouseY);
                }
            }
        }

        // Son güncelleme
        int lastY = gridY + rows * (SLOT_SIZE + SLOT_GAP) + (rec.isDouble() ? 8 : 4);
        String updated = "Son güncelleme: " + (rec.getLastUpdated() != null
                ? rec.getLastUpdated().substring(0, Math.min(16, rec.getLastUpdated().length()))
                : "bilinmiyor");
        ctx.drawTextWithShadow(textRenderer, Text.literal(updated), gridX, lastY, C_SUB);

        // Footer
        int footerY = height - FOOTER_H;
        ctx.fill(rp, footerY, width, footerY + 1, C_BORDER);
        ctx.fill(rp, footerY + 1, width, height, C_BG2);

        if (confirmDelete) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Bu sandık kaydını silmek istiyor musun?"),
                    rp + rpW / 2, footerY + (FOOTER_H - 20) / 2 - 2, C_RED);
        }
    }

    // ── Scroll ────────────────────────────────────────────────────────────
    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (mx < LEFT_W) {
            int visibleRows = (height - TITLE_H) / ROW_H;
            scrollOffset = (int) Math.max(0,
                    Math.min(chests.size() - visibleRows, scrollOffset - vy));
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
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitRename(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE)                                  { cancelRenaming(); return true; }
            return super.keyPressed(input);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)                                     { close(); return true; }
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
        int ry          = TITLE_H + (idx - scrollOffset) * ROW_H;
        renameField     = new TextFieldWidget(textRenderer,
                22, ry + 8, LEFT_W - 48, 12, null, Text.empty());
        renameField.setMaxLength(32);
        renameField.setText(current);
        renameField.setFocused(true);
        renameField.setDrawsBackground(false);
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