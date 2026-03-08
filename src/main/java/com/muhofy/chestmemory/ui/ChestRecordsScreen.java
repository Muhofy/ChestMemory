package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.data.ChestItem;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class ChestRecordsScreen extends Screen {

    private static final int LEFT_W    = 220;
    private static final int TITLE_H   = 28;
    private static final int ROW_H     = 40;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP  = 2;
    private static final int BTN_H     = 20;

    private List<ChestRecord> chests = new ArrayList<>();
    private int selectedIndex        = 0;
    private int scrollOffset         = 0;
    private int renamingIndex        = -1;
    private TextFieldWidget renameField;
    private boolean confirmDelete    = false;

    private ButtonWidget btnNavigate, btnDelete, btnConfirmYes, btnConfirmNo;

    public ChestRecordsScreen() {
        super(Text.literal("Sandık Kayıtları"));
    }

    @Override
    protected void init() {
        refreshChests();
        int rp  = LEFT_W;
        int rpW = width - LEFT_W;
        int btnY = height - BTN_H - 10;

        btnNavigate   = ButtonWidget.builder(Text.literal("📍 Yön Göster"), b -> navigateToSelected())
                .dimensions(rp + 8, btnY, 100, BTN_H).build();
        btnDelete     = ButtonWidget.builder(Text.literal("🗑 Kaydı Sil"),
                b -> { confirmDelete = true; updateButtons(); })
                .dimensions(rp + rpW - 108, btnY, 100, BTN_H).build();
        btnConfirmYes = ButtonWidget.builder(Text.literal("✔ Evet"), b -> deleteSelected())
                .dimensions(rp + rpW / 2 - 54, btnY, 50, BTN_H).build();
        btnConfirmNo  = ButtonWidget.builder(Text.literal("✖ Hayır"),
                b -> { confirmDelete = false; updateButtons(); })
                .dimensions(rp + rpW / 2 + 4, btnY, 50, BTN_H).build();

        addDrawableChild(btnNavigate);
        addDrawableChild(btnDelete);
        addDrawableChild(btnConfirmYes);
        addDrawableChild(btnConfirmNo);
        updateButtons();
    }

    private void updateButtons() {
        btnNavigate.visible   = !confirmDelete;
        btnDelete.visible     = !confirmDelete;
        btnConfirmYes.visible = confirmDelete;
        btnConfirmNo.visible  = confirmDelete;
    }

    private void refreshChests() {
        chests = new ArrayList<>(ChestStorage.getInstance().getAll());
        if (selectedIndex >= chests.size()) selectedIndex = Math.max(0, chests.size() - 1);
    }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xFF2a2a2a);
        renderLeftPanel(ctx, mouseX, mouseY);
        renderRightPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderLeftPanel(DrawContext ctx, int mouseX, int mouseY) {
        ctx.fill(0, 0, LEFT_W, height, 0xFF1e1e1e);
        ctx.fill(LEFT_W - 2, 0, LEFT_W, height, 0xFF111111);
        ctx.fill(0, 0, LEFT_W, TITLE_H, 0xFF252525);
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("📦 SANDIKLAR (" + chests.size() + ")"), 8, 9, 0xFFFFAA00);
        ctx.fill(0, TITLE_H, LEFT_W, TITLE_H + 1, 0xFF111111);

        if (chests.isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Henüz sandık açılmadı."),
                    8, TITLE_H + 12, 0xFF555555);
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        String activeDim   = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
        double px          = mc.player != null ? mc.player.getX() : 0;
        double pz          = mc.player != null ? mc.player.getZ() : 0;
        int visibleRows    = (height - TITLE_H) / ROW_H;

        for (int i = scrollOffset; i < Math.min(chests.size(), scrollOffset + visibleRows); i++) {
            ChestRecord rec = chests.get(i);
            int ry          = TITLE_H + (i - scrollOffset) * ROW_H;
            boolean sel     = i == selectedIndex;
            boolean diff    = !rec.isInDimension(activeDim);

            if (sel) {
                ctx.fill(0, ry, LEFT_W - 2, ry + ROW_H, 0x22FFFFFF);
                ctx.fill(0, ry, 3, ry + ROW_H, 0xFF55FF55);
            }
            ctx.fill(0, ry + ROW_H - 1, LEFT_W - 2, ry + ROW_H, 0xFF333333);

            if (!(renamingIndex == i && renameField != null)) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal(ChestStorage.getInstance().getDisplayName(rec)),
                        6, ry + 6, diff ? 0xFF666666 : 0xFFFFFFFF);
            }
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(rec.getX() + ", " + rec.getY() + ", " + rec.getZ()),
                    6, ry + 18, 0xFF777777);
            String dist = diff ? "Farklı boyut" : ((int) rec.distanceTo(px, pz)) + " blok uzakta";
            ctx.drawTextWithShadow(textRenderer, Text.literal(dist),
                    6, ry + 28, diff ? 0xFF555555 : 0xFF55FFFF);
            ctx.drawTextWithShadow(textRenderer, Text.literal("✏"),
                    LEFT_W - 18, ry + ROW_H / 2 - 4, 0xFF888888);
        }
    }

    private void renderRightPanel(DrawContext ctx, int mouseX, int mouseY) {
        int rp  = LEFT_W;
        int rpW = width - LEFT_W;

        if (chests.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Sandık seçin"),
                    rp + rpW / 2, height / 2, 0xFF555555);
            return;
        }

        ChestRecord rec = chests.get(selectedIndex);
        String name     = ChestStorage.getInstance().getDisplayName(rec);
        String coords   = rec.getX() + ", " + rec.getY() + ", " + rec.getZ()
                + " • " + shortDim(rec.getDimension());

        ctx.fill(rp, 0, width, TITLE_H, 0xFF252525);
        ctx.drawTextWithShadow(textRenderer, Text.literal(name + " — İçerik"), rp + 8, 9, 0xFFFFAA00);
        ctx.drawTextWithShadow(textRenderer, Text.literal(coords),
                width - textRenderer.getWidth(coords) - 8, 9, 0xFF777777);
        ctx.fill(rp, TITLE_H, width, TITLE_H + 1, 0xFF111111);

        int gridX         = rp + 12;
        int gridY         = TITLE_H + 16;
        List<ChestItem> items = rec.getItems();

        for (int slot = 0; slot < 27; slot++) {
            int col = slot % 9;
            int row = slot / 9;
            int sx  = gridX + col * (SLOT_SIZE + SLOT_GAP);
            int sy  = gridY + row * (SLOT_SIZE + SLOT_GAP);

            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF373737);
            ctx.fill(sx, sy, sx + SLOT_SIZE, sy + 1, 0xFF1a1a1a);
            ctx.fill(sx, sy, sx + 1, sy + SLOT_SIZE, 0xFF1a1a1a);
            ctx.fill(sx + SLOT_SIZE - 1, sy, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF555555);
            ctx.fill(sx, sy + SLOT_SIZE - 1, sx + SLOT_SIZE, sy + SLOT_SIZE, 0xFF555555);

            ChestItem ci = getItemForSlot(items, slot);
            if (ci != null) {
                ItemStack stack = getStack(ci.getItemId());
                if (!stack.isEmpty()) {
                    ctx.drawItem(stack, sx + 1, sy + 1);
                    // Adet sayısını elle çiz (drawItemInSlot signature değişken, elle daha güvenli)
                    if (ci.getCount() > 1) {
                        ctx.drawTextWithShadow(textRenderer,
                                Text.literal(String.valueOf(ci.getCount())),
                                sx + SLOT_SIZE - textRenderer.getWidth(String.valueOf(ci.getCount())),
                                sy + SLOT_SIZE - textRenderer.fontHeight,
                                0xFFFFFFFF);
                    }
                }
            }

            if (ci != null && mouseX >= sx && mouseX < sx + SLOT_SIZE
                    && mouseY >= sy && mouseY < sy + SLOT_SIZE) {
                ctx.drawTooltip(textRenderer,
                        Text.literal(ci.getDisplayName() + " x" + ci.getCount()), mouseX, mouseY);
            }
        }

        String updated = "Son güncelleme: " + (rec.getLastUpdated() != null
                ? rec.getLastUpdated().substring(0, Math.min(16, rec.getLastUpdated().length()))
                : "bilinmiyor");
        ctx.drawTextWithShadow(textRenderer, Text.literal(updated),
                rp + 8, gridY + 3 * (SLOT_SIZE + SLOT_GAP) + 6, 0xFF555555);

        if (confirmDelete) {
            ctx.fill(rp, height - 50, width, height, 0xCC000000);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("Bu sandık kaydını silmek istiyor musun?"),
                    rp + rpW / 2, height - 44, 0xFFFF5555);
        }
    }

    // ── Mouse — 1.21.9+ signature ─────────────────────────────────────────

    @Override
    public boolean mouseClicked(Click click, boolean isDouble) {
        double mx = click.mouseX();
        double my = click.mouseY();

        if (mx < LEFT_W) {
            int visibleRows = (height - TITLE_H) / ROW_H;
            for (int i = scrollOffset; i < Math.min(chests.size(), scrollOffset + visibleRows); i++) {
                int ry = TITLE_H + (i - scrollOffset) * ROW_H;
                if (my >= ry && my < ry + ROW_H) {
                    if (mx >= LEFT_W - 20) { startRenaming(i); return true; }
                    selectedIndex = i;
                    cancelRenaming();
                    confirmDelete = false;
                    updateButtons();
                    return true;
                }
            }
        }
        return super.mouseClicked(click, isDouble);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hx, double vy) {
        if (mx < LEFT_W) {
            int visibleRows = (height - TITLE_H) / ROW_H;
            scrollOffset = (int) Math.max(0,
                    Math.min(chests.size() - visibleRows, scrollOffset - vy));
            return true;
        }
        return super.mouseScrolled(mx, my, hx, vy);
    }

    // ── Keyboard — 1.21.9+ signature ─────────────────────────────────────

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (renamingIndex >= 0) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { commitRename(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE)                                  { cancelRenaming(); return true; }
            return super.keyPressed(input);
        }
        if (key == GLFW.GLFW_KEY_ESCAPE)                                       { close(); return true; }
        if (key == GLFW.GLFW_KEY_UP && selectedIndex > 0)                     { selectedIndex--; return true; }
        if (key == GLFW.GLFW_KEY_DOWN && selectedIndex < chests.size() - 1)   { selectedIndex++; return true; }
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
        updateButtons();
    }

    private void startRenaming(int idx) {
        cancelRenaming();
        renamingIndex   = idx;
        ChestRecord rec = chests.get(idx);
        String current  = rec.getCustomName() != null ? rec.getCustomName() : "";
        int ry          = TITLE_H + (idx - scrollOffset) * ROW_H;
        renameField     = new TextFieldWidget(textRenderer, 6, ry + 6, LEFT_W - 24, 10,
                null, Text.empty());
        renameField.setMaxLength(32);
        renameField.setText(current);
        renameField.setFocused(true);
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

    // ── Yardımcılar ───────────────────────────────────────────────────────

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