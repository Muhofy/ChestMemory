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
    private static final int BOX_W       = 360;
    private static final int INPUT_H     = 28;
    private static final int ROW_H       = 36;
    private static final int FOOTER_H    = 20;
    private static final int MAX_RESULTS = 5;

    // ── Colors ────────────────────────────────────────────────────────────
    private static final int C_BG        = 0xFF111418;
    private static final int C_BG2       = 0xFF16191f;
    private static final int C_BORDER    = 0xFF1e2228;
    private static final int C_ACCENT    = 0xFF4ade80;
    private static final int C_TEXT      = 0xFFd0d0d0;
    private static final int C_SUB       = 0xFF555555;
    private static final int C_YELLOW    = 0xFFf59e0b;
    private static final int C_BLUE      = 0xFF38bdf8;
    private static final int C_DIM       = 0xFF2a2a2a;
    private static final int C_DIM_TEXT  = 0xFF333333;

    // ── State ─────────────────────────────────────────────────────────────
    private TextFieldWidget searchField;
    private List<ChestStorage.SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;
    private final List<ButtonWidget> resultButtons = new ArrayList<>();

    public SearchOverlay() {
        super(Text.literal("ChestMemory Search"));
    }

    @Override
    protected void init() {
        int bx = (width - BOX_W) / 2;
        int by = getBoxY();

        searchField = new TextFieldWidget(textRenderer,
                bx + 20, by + (INPUT_H - textRenderer.fontHeight) / 2,
                BOX_W - 80, textRenderer.fontHeight + 2,
                null, Text.empty());
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal("Item ara..."));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setDrawsBackground(false);
        addDrawableChild(searchField);

        // Focus'u hem widget'a hem Screen sistemine bildir
        searchField.setFocused(true);
        setInitialFocus(searchField);
    }

    private int getBoxY() {
        int totalH = INPUT_H + MAX_RESULTS * ROW_H + FOOTER_H;
        return (height - totalH) / 2;
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

        int bx          = (width - BOX_W) / 2;
        int resultAreaY = getBoxY() + INPUT_H + 1;

        for (int i = 0; i < results.size(); i++) {
            final int idx = i;
            ButtonWidget btn = ButtonWidget.builder(Text.empty(), b -> {
                selectedIndex = idx;
                activateSelected();
            }).dimensions(bx, resultAreaY + idx * ROW_H, BOX_W, ROW_H).build();
            btn.setAlpha(0f);
            resultButtons.add(btn);
            addDrawableChild(btn);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        ctx.fill(0, 0, width, height, 0x88000000);

        int bx          = (width - BOX_W) / 2;
        int by          = getBoxY();
        String query    = searchField != null ? searchField.getText() : "";
        int resultCount = results.size();
        int totalH      = INPUT_H + (query.isBlank() ? ROW_H : Math.max(1, resultCount) * ROW_H) + FOOTER_H;

        // Outer shadow
        ctx.fill(bx - 1, by - 1, bx + BOX_W + 1, by + totalH + 1, 0x44000000);

        // Main box bg
        ctx.fill(bx, by, bx + BOX_W, by + totalH, C_BG);

        // ── Input row ─────────────────────────────────────────────────────
        ctx.fill(bx, by, bx + BOX_W, by + INPUT_H, C_BG2);
        // Search icon
        ctx.drawTextWithShadow(textRenderer, Text.literal("⌕"), bx + 7, by + (INPUT_H - textRenderer.fontHeight) / 2, C_SUB);
        // ESC badge (right side)
        String badge = "ESC";
        int badgeX   = bx + BOX_W - textRenderer.getWidth(badge) - 10;
        int badgeY   = by + (INPUT_H - textRenderer.fontHeight) / 2;
        ctx.fill(badgeX - 4, badgeY - 2, badgeX + textRenderer.getWidth(badge) + 4, badgeY + textRenderer.fontHeight + 2, C_BORDER);
        ctx.drawTextWithShadow(textRenderer, Text.literal(badge), badgeX, badgeY, C_SUB);
        // Divider
        ctx.fill(bx, by + INPUT_H, bx + BOX_W, by + INPUT_H + 1, C_BORDER);

        // ── Results ───────────────────────────────────────────────────────
        int ry0 = by + INPUT_H + 1;

        if (query.isBlank()) {
            String hint = "Aramak istediğin itemi yaz...";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                    bx + BOX_W / 2, ry0 + (ROW_H - textRenderer.fontHeight) / 2, C_SUB);
        } else if (results.isEmpty()) {
            String hint = "Hiçbir sandıkta bulunamadı.";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(hint),
                    bx + BOX_W / 2, ry0 + (ROW_H - textRenderer.fontHeight) / 2, C_SUB);
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            String activeDim  = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";

            for (int i = 0; i < results.size(); i++) {
                ChestStorage.SearchResult r = results.get(i);
                int rowY    = ry0 + i * ROW_H;
                boolean sel = i == selectedIndex;
                boolean dim = !r.chest.isInDimension(activeDim);

                // Row hover/selected bg
                if (sel) ctx.fill(bx, rowY, bx + BOX_W, rowY + ROW_H, 0x0DFFFFFF);

                // Green left accent bar
                if (sel) ctx.fill(bx, rowY + 4, bx + 2, rowY + ROW_H - 4, C_ACCENT);

                // Item icon bg
                int iconX = bx + 8;
                int iconY = rowY + (ROW_H - 18) / 2;
                ctx.fill(iconX, iconY, iconX + 18, iconY + 18, dim ? C_DIM : C_BORDER);

                // Item icon — ilk eşleşen itemın ikonu
                ChestStorage.SearchResult res = r;
                ItemStack stack = res.firstItem() != null ? getStack(res.firstItem().getItemId()) : ItemStack.EMPTY;
                if (!stack.isEmpty() && !dim) ctx.drawItem(stack, iconX + 1, iconY + 1);

                // Name + sub
                int textX    = iconX + 22;
                int nameY    = rowY + 7;
                int subY     = rowY + 19;
                String name  = res.firstItem() != null && res.firstItem().getDisplayName() != null
                        ? res.firstItem().getDisplayName() : "Bilinmiyor";
                // Birden fazla farklı item türü varsa "Diamond, Iron..." göster
                if (res.matchedItems.size() > 1) {
                    long types = res.matchedItems.stream()
                            .map(ChestItem::getItemId).distinct().count();
                    if (types > 1) name = types + " farklı item";
                }
                String chest = ChestStorage.getInstance().getDisplayName(r.chest);
                String sub   = chest + " · " + r.chest.getX() + ", " + r.chest.getY() + ", " + r.chest.getZ();

                ctx.drawTextWithShadow(textRenderer, Text.literal(name), textX, nameY, dim ? C_DIM_TEXT : C_TEXT);
                ctx.drawTextWithShadow(textRenderer, Text.literal(sub),  textX, subY,  dim ? C_DIM_TEXT : C_SUB);

                // Count + distance (right side) — totalCount kullan
                String countStr = r.totalCount + "x";
                String distStr  = dim ? "Farklı boyut" : ((int) r.distance) + " blok " + dirArrow(r.chest, mc);
                int rightX      = bx + BOX_W - Math.max(textRenderer.getWidth(countStr), textRenderer.getWidth(distStr)) - 10;
                ctx.drawTextWithShadow(textRenderer, Text.literal(countStr), rightX, nameY, dim ? C_DIM_TEXT : C_YELLOW);
                ctx.drawTextWithShadow(textRenderer, Text.literal(distStr),  rightX, subY,  dim ? C_DIM_TEXT : C_BLUE);

                // Row divider
                if (i < results.size() - 1)
                    ctx.fill(bx, rowY + ROW_H, bx + BOX_W, rowY + ROW_H + 1, C_BORDER);
            }
        }

        // ── Footer ────────────────────────────────────────────────────────
        int footerY = by + totalH - FOOTER_H;
        ctx.fill(bx, footerY, bx + BOX_W, footerY + 1, C_BORDER);
        ctx.fill(bx, footerY + 1, bx + BOX_W, by + totalH, 0xFF0e1014);
        int fx = bx + 10;
        int fy = footerY + (FOOTER_H - textRenderer.fontHeight) / 2;
        ctx.drawTextWithShadow(textRenderer, Text.literal("↑↓"), fx, fy, C_SUB);
        ctx.drawTextWithShadow(textRenderer, Text.literal(" Seç   "), fx + textRenderer.getWidth("↑↓"), fy, 0xFF2a2a2a);
        ctx.drawTextWithShadow(textRenderer, Text.literal("Enter"), fx + textRenderer.getWidth("↑↓ Seç   "), fy, C_SUB);
        ctx.drawTextWithShadow(textRenderer, Text.literal(" Yön göster"), fx + textRenderer.getWidth("↑↓ Seç   Enter"), fy, 0xFF2a2a2a);

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

    private ItemStack getStack(String itemId) {
        try { return Registries.ITEM.get(Identifier.of(itemId)).getDefaultStack(); }
        catch (Exception e) { return ItemStack.EMPTY; }
    }

    private String dirArrow(ChestRecord chest, MinecraftClient mc) {
        if (mc.player == null) return "";
        double angle = (Math.toDegrees(Math.atan2(
                chest.getZ() - mc.player.getZ(),
                chest.getX() - mc.player.getX())) + 360) % 360;
        if (angle < 22.5 || angle >= 337.5) return "→";
        if (angle < 67.5)  return "↘";
        if (angle < 112.5) return "↓";
        if (angle < 157.5) return "↙";
        if (angle < 202.5) return "←";
        if (angle < 247.5) return "↖";
        if (angle < 292.5) return "↑";
        return "↗";
    }
}