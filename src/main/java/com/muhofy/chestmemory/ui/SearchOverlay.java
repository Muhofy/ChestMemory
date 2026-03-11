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

public class SearchOverlay extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────
    private static final int BOX_W       = 340;
    private static final int INPUT_H     = 24;
    private static final int ROW_H       = 32;
    private static final int FOOTER_H    = 18;
    private static final int MAX_RESULTS = 5;
    private static final int BOX_H       = INPUT_H + 1 + MAX_RESULTS * ROW_H + FOOTER_H;

    // ── Vanilla colors ────────────────────────────────────────────────────
    // Panel
    private static final int C_PANEL      = 0xFFC6C6C6;
    private static final int C_PANEL_LT   = 0xFFFFFFFF; // bevel light
    private static final int C_PANEL_DK   = 0xFF555555; // bevel dark
    // Inset (sunken input)
    private static final int C_INSET_BG   = 0xFF000000;
    private static final int C_INSET_LT   = 0xFF373737;
    private static final int C_INSET_DK   = 0xFFC6C6C6;
    // Slot
    private static final int C_SLOT_BG    = 0xFF8B8B8B;
    private static final int C_SLOT_LT    = 0xFF373737;
    private static final int C_SLOT_DK    = 0xFFFFFFFF;
    // Row
    private static final int C_ROW_BG     = 0xFF8B8B8B;
    private static final int C_ROW_SEL    = 0xFF9E9E9E;
    private static final int C_ROW_DIM    = 0xFF6B6B6B;
    // Text
    private static final int C_TEXT       = 0xFFFFFFFF;
    private static final int C_TEXT_DARK  = 0xFF3F3F3F;
    private static final int C_TEXT_GRAY  = 0xFFBBBBBB;
    private static final int C_TEXT_DIM   = 0xFF777777;
    private static final int C_YELLOW     = 0xFFFFFF55;
    private static final int C_CYAN       = 0xFF55FFFF;
    private static final int C_FOOTER_TXT = 0xFF555555;

    // ── State ─────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private List<ChestStorage.SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;
    private final List<ButtonWidget> resultButtons = new ArrayList<>();

    public SearchOverlay() {
        super(Text.translatable("chestmemory.search.placeholder"));
    }

    private int boxX() { return (width  - BOX_W) / 2; }
    private int boxY() { return (height - BOX_H) / 2; }

    @Override
    protected void init() {
        int bx = boxX(), by = boxY();
        searchField = new TextFieldWidget(textRenderer,
                bx + 22, by + (INPUT_H - textRenderer.fontHeight) / 2,
                BOX_W - 44, textRenderer.fontHeight + 2,
                null, Text.empty());
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.translatable("chestmemory.search.placeholder"));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setDrawsBackground(false);
        searchField.setEditableColor(0xFFFFFF);
        addDrawableChild(searchField);
        searchField.setFocused(true);
        setInitialFocus(searchField);
    }

    private void onSearchChanged(String query) {
        selectedIndex = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) {
            results = new ArrayList<>();
            refreshResultButtons();
            return;
        }
        String dim = mc.world.getRegistryKey().getValue().toString();
        results    = ChestStorage.getInstance().searchItems(query, dim, mc.player.getX(), mc.player.getZ());
        if (results.size() > MAX_RESULTS) results = results.subList(0, MAX_RESULTS);
        refreshResultButtons();
    }

    private void refreshResultButtons() {
        for (ButtonWidget b : resultButtons) remove(b);
        resultButtons.clear();
        int bx = boxX(), ry0 = boxY() + INPUT_H + 1;
        for (int i = 0; i < results.size(); i++) {
            final int idx = i;
            ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                selectedIndex = idx;
                activateSelected();
            }).dimensions(bx + 2, ry0 + idx * ROW_H, BOX_W - 4, ROW_H).build();
            btn.setAlpha(0f);
            resultButtons.add(btn);
            addDrawableChild(btn);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim
        ctx.fill(0, 0, width, height, 0x88000000);

        int bx = boxX(), by = boxY();

        // ── Outer bevel ───────────────────────────────────────────────────
        vanillaPanel(ctx, bx - 2, by - 2, BOX_W + 4, BOX_H + 4);

        // ── Panel bg ──────────────────────────────────────────────────────
        ctx.fill(bx, by, bx + BOX_W, by + BOX_H, C_PANEL);

        // ── Input row ─────────────────────────────────────────────────────
        // Sunken inset
        inset(ctx, bx + 4, by + 4, BOX_W - 8, INPUT_H - 8);
        // ▍ cursor icon
        ctx.drawTextWithShadow(textRenderer, Text.literal("▍"),
                bx + 8, by + (INPUT_H - textRenderer.fontHeight) / 2,
                0xFF555555);

        // Divider
        ctx.fill(bx + 2, by + INPUT_H, bx + BOX_W - 2, by + INPUT_H + 1, C_PANEL_DK);

        // ── Result area ───────────────────────────────────────────────────
        int ry0        = by + INPUT_H + 1;
        int resultAreaH = MAX_RESULTS * ROW_H;
        String q        = searchField != null ? searchField.getText() : "";

        if (q.isBlank()) {
            ctx.fill(bx + 2, ry0, bx + BOX_W - 2, ry0 + resultAreaH, C_SLOT_BG);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.search.hint"),
                    bx + BOX_W / 2, ry0 + (resultAreaH - textRenderer.fontHeight) / 2,
                    C_TEXT_DIM);
        } else if (results.isEmpty()) {
            ctx.fill(bx + 2, ry0, bx + BOX_W - 2, ry0 + resultAreaH, C_SLOT_BG);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable("chestmemory.search.empty"),
                    bx + BOX_W / 2, ry0 + (resultAreaH - textRenderer.fontHeight) / 2,
                    C_TEXT_DIM);
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            String activeDim   = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";

            for (int i = 0; i < MAX_RESULTS; i++) {
                int rowY = ry0 + i * ROW_H;

                if (i >= results.size()) {
                    // Boş satır
                    ctx.fill(bx + 2, rowY, bx + BOX_W - 2, rowY + ROW_H, C_SLOT_BG);
                    if (i < MAX_RESULTS - 1)
                        ctx.fill(bx + 2, rowY + ROW_H - 1, bx + BOX_W - 2, rowY + ROW_H, C_PANEL_DK);
                    continue;
                }

                ChestStorage.SearchResult r = results.get(i);
                boolean sel  = i == selectedIndex;
                boolean dim  = !r.chest.isInDimension(activeDim);

                int rowBg = dim ? C_ROW_DIM : (sel ? C_ROW_SEL : C_ROW_BG);
                ctx.fill(bx + 2, rowY, bx + BOX_W - 2, rowY + ROW_H, rowBg);

                // Seçili satır → sunken bevel
                if (sel) {
                    ctx.fill(bx+2, rowY,   bx+BOX_W-2, rowY+1,      C_INSET_LT);
                    ctx.fill(bx+2, rowY,   bx+3,        rowY+ROW_H,  C_INSET_LT);
                    ctx.fill(bx+2, rowY+ROW_H-1, bx+BOX_W-2, rowY+ROW_H, C_INSET_DK);
                    ctx.fill(bx+BOX_W-3, rowY, bx+BOX_W-2, rowY+ROW_H, C_INSET_DK);
                    // Seçili ok
                    ctx.drawTextWithShadow(textRenderer, Text.literal("▶"),
                            bx + 4, rowY + (ROW_H - textRenderer.fontHeight) / 2, C_YELLOW);
                }

                // Item slot
                int slotX = bx + 14, slotY = rowY + (ROW_H - 18) / 2;
                slotBox(ctx, slotX, slotY, 18);
                ItemStack stack = r.firstItem() != null
                        ? getStack(r.firstItem().getItemId()) : ItemStack.EMPTY;
                if (!stack.isEmpty() && !dim)
                    ctx.drawItem(stack, slotX + 1, slotY + 1);

                // Text
                int textX = slotX + 22;
                int nameY = rowY + 6;
                int subY  = rowY + 18;

                String name = r.firstItem() != null && r.firstItem().getDisplayName() != null
                        ? r.firstItem().getDisplayName()
                        : Text.translatable("chestmemory.item.unknown").getString();
                if (r.matchedItems.size() > 1) {
                    long types = r.matchedItems.stream().map(ChestItem::getItemId).distinct().count();
                    if (types > 1) name = types + Text.translatable("chestmemory.item.multi").getString();
                }
                String chestName = ChestStorage.getInstance().getDisplayName(r.chest);
                String sub       = chestName + "  " + r.chest.getX() + ", "
                                 + r.chest.getY() + ", " + r.chest.getZ();

                ctx.drawTextWithShadow(textRenderer, Text.literal(name),
                        textX, nameY, dim ? C_TEXT_DIM : C_TEXT);
                ctx.drawTextWithShadow(textRenderer, Text.literal(sub),
                        textX, subY, dim ? C_TEXT_DIM : C_TEXT_GRAY);

                // Count + dist (right)
                String countStr = r.totalCount + "x";
                String distStr  = dim
                        ? Text.translatable("chestmemory.records.different_dimension").getString()
                        : ((int) r.distance) + "m " + dirArrow(r.chest, mc);
                int rightX = bx + BOX_W - Math.max(
                        textRenderer.getWidth(countStr), textRenderer.getWidth(distStr)) - 8;
                ctx.drawTextWithShadow(textRenderer, Text.literal(countStr),
                        rightX, nameY, dim ? C_TEXT_DIM : C_YELLOW);
                ctx.drawTextWithShadow(textRenderer, Text.literal(distStr),
                        rightX, subY, dim ? C_TEXT_DIM : C_CYAN);

                // Row divider
                if (i < MAX_RESULTS - 1)
                    ctx.fill(bx + 2, rowY + ROW_H - 1, bx + BOX_W - 2, rowY + ROW_H, C_PANEL_DK);
            }
        }

        // ── Footer ────────────────────────────────────────────────────────
        int footerY = by + BOX_H - FOOTER_H;
        ctx.fill(bx + 2, footerY, bx + BOX_W - 2, footerY + 1, C_PANEL_DK);
        ctx.fill(bx + 2, footerY + 1, bx + BOX_W - 2, by + BOX_H - 2, C_SLOT_BG);

        int fx = bx + 8, fy = footerY + (FOOTER_H - textRenderer.fontHeight) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("↑↓"), fx, fy, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(" Seç  "),
                fx + textRenderer.getWidth("↑↓"), fy, C_FOOTER_TXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enter"),
                fx + textRenderer.getWidth("↑↓ Seç  "), fy, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(" Yön  "),
                fx + textRenderer.getWidth("↑↓ Seç  Enter"), fy, C_FOOTER_TXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Esc"),
                fx + textRenderer.getWidth("↑↓ Seç  Enter Yön  "), fy, C_TEXT);
        ctx.drawTextWithShadow(textRenderer, Text.literal(" Kapat"),
                fx + textRenderer.getWidth("↑↓ Seç  Enter Yön  Esc"), fy, C_FOOTER_TXT);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int key = input.key();
        if (key == GLFW.GLFW_KEY_ESCAPE)                                  { close(); return true; }
        if (key == GLFW.GLFW_KEY_UP)                                      { selectedIndex = Math.max(0, selectedIndex - 1); return true; }
        if (key == GLFW.GLFW_KEY_DOWN)                                    { selectedIndex = Math.min(results.size() - 1, selectedIndex + 1); return true; }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { activateSelected(); return true; }
        return super.keyPressed(input);
    }

    private void activateSelected() {
        if (results.isEmpty() || selectedIndex >= results.size()) return;
        ChestMemoryHud.setTarget(results.get(selectedIndex).chest);
        close();
    }

    @Override public boolean shouldPause() { return false; }

    // ── Vanilla draw helpers ──────────────────────────────────────────────

    /** Raised panel: light top-left, dark bottom-right */
    private void vanillaPanel(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,       y,       x+w,   y+2,   C_PANEL_LT); // top
        ctx.fill(x,       y,       x+2,   y+h,   C_PANEL_LT); // left
        ctx.fill(x,       y+h-2,   x+w,   y+h,   C_PANEL_DK); // bottom
        ctx.fill(x+w-2,   y,       x+w,   y+h,   C_PANEL_DK); // right
    }

    /** Sunken inset: dark top-left, light bottom-right */
    private void inset(DrawContext ctx, int x, int y, int w, int h) {
        ctx.fill(x,     y,     x+w, y+h,   C_INSET_BG); // bg
        ctx.fill(x,     y,     x+w, y+1,   C_INSET_LT); // top
        ctx.fill(x,     y,     x+1, y+h,   C_INSET_LT); // left
        ctx.fill(x,     y+h-1, x+w, y+h,   C_INSET_DK); // bottom
        ctx.fill(x+w-1, y,     x+w, y+h,   C_INSET_DK); // right
    }

    /** Vanilla slot box */
    private void slotBox(DrawContext ctx, int x, int y, int size) {
        ctx.fill(x,        y,        x+size, y+size, C_SLOT_BG);
        ctx.fill(x,        y,        x+size, y+1,    C_SLOT_LT);
        ctx.fill(x,        y,        x+1,    y+size, C_SLOT_LT);
        ctx.fill(x,        y+size-1, x+size, y+size, C_SLOT_DK);
        ctx.fill(x+size-1, y,        x+size, y+size, C_SLOT_DK);
    }

    private ItemStack getStack(String itemId) {
        try { return Registries.ITEM.get(Identifier.of(itemId)).getDefaultStack(); }
        catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String dirArrow(ChestRecord chest, MinecraftClient mc) {
        if (mc.player == null) return "";
        double angle = (Math.toDegrees(Math.atan2(
                chest.getZ() - mc.player.getZ(),
                chest.getX() - mc.player.getX())) + 360) % 360;
        if (angle < 22.5  || angle >= 337.5) return "→";
        if (angle < 67.5)  return "↘";
        if (angle < 112.5) return "↓";
        if (angle < 157.5) return "↙";
        if (angle < 202.5) return "←";
        if (angle < 247.5) return "↖";
        if (angle < 292.5) return "↑";
        return "↗";
    }
}