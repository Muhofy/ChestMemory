package com.muhofy.chestmemory.ui;

import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
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

public class SearchOverlay extends Screen {

    private static final int BOX_W       = 420;
    private static final int BOX_H       = 220;
    private static final int ROW_H       = 28;
    private static final int MAX_RESULTS = 6;
    private static final int INPUT_H     = 28;
    private static final int FOOTER_H    = 18;

    private TextFieldWidget searchField;
    private List<ChestStorage.SearchResult> results = new ArrayList<>();
    private int selectedIndex = 0;

    public SearchOverlay() {
        super(Text.literal("ChestMemory Search"));
    }

    @Override
    protected void init() {
        int bx = (width - BOX_W) / 2;
        int by = (height - BOX_H) / 2;
        searchField = new TextFieldWidget(textRenderer, bx + 30, by + 6, BOX_W - 38, INPUT_H - 8,
                null, Text.literal("Item ara..."));
        searchField.setMaxLength(64);
        searchField.setPlaceholder(Text.literal("Item ara..."));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setFocused(true);
        addDrawableChild(searchField);
    }

    private void onSearchChanged(String query) {
        selectedIndex = 0;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) { results = new ArrayList<>(); return; }
        String dim = mc.world.getRegistryKey().getValue().toString();
        results    = ChestStorage.getInstance().searchItems(query, dim, mc.player.getX(), mc.player.getZ());
        if (results.size() > MAX_RESULTS) results = results.subList(0, MAX_RESULTS);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0x88000000);
        int bx = (width - BOX_W) / 2;
        int by = (height - BOX_H) / 2;

        ctx.fill(bx - 2, by - 2, bx + BOX_W + 2, by + BOX_H + 2, 0xFF111111);
        ctx.fill(bx, by, bx + BOX_W, by + BOX_H, 0xD0000000);
        ctx.fill(bx, by, bx + BOX_W, by + INPUT_H, 0x22FFFFFF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("🔍"), bx + 8, by + 9, 0xFFAAAAAA);
        ctx.fill(bx, by + INPUT_H, bx + BOX_W, by + INPUT_H + 2, 0xFF444444);

        String query    = searchField != null ? searchField.getText() : "";
        int resultAreaY = by + INPUT_H + 2;

        if (query.isBlank() || results.isEmpty()) {
            String msg = query.isBlank() ? "Aramak istediğin itemi yaz..." : "Hiçbir sandıkta bulunamadı.";
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(msg),
                    bx + BOX_W / 2, resultAreaY + 16, 0xFF666666);
        } else {
            MinecraftClient mc = MinecraftClient.getInstance();
            String activeDim  = mc.world != null ? mc.world.getRegistryKey().getValue().toString() : "";
            for (int i = 0; i < results.size(); i++) {
                ChestStorage.SearchResult r = results.get(i);
                int ry      = resultAreaY + i * ROW_H;
                boolean sel = i == selectedIndex;
                boolean diff = !r.chest.isInDimension(activeDim);
                if (sel) {
                    ctx.fill(bx, ry, bx + BOX_W, ry + ROW_H, 0x22FFFFFF);
                    ctx.fill(bx, ry, bx + 3, ry + ROW_H, 0xFF55FF55);
                }
                ItemStack stack = getStack(r.item.getItemId());
                if (!stack.isEmpty()) ctx.drawItem(stack, bx + 6, ry + 5);
                ctx.drawTextWithShadow(textRenderer, Text.literal(r.item.getCount() + "x"),
                        bx + 28, ry + 10, diff ? 0xFF666666 : 0xFFFFAA00);
                String name   = ChestStorage.getInstance().getDisplayName(r.chest);
                String coords = name + "  " + r.chest.getX() + ", " + r.chest.getY() + ", " + r.chest.getZ();
                ctx.drawTextWithShadow(textRenderer, Text.literal(coords),
                        bx + 70, ry + 10, diff ? 0xFF555555 : 0xFFAAAAAA);
                String distStr = diff ? "Farklı boyut"
                        : ((int) r.distance) + " blok " + dirArrow(r.chest, mc);
                ctx.drawTextWithShadow(textRenderer, Text.literal(distStr),
                        bx + BOX_W - textRenderer.getWidth(distStr) - 8, ry + 10,
                        diff ? 0xFF555555 : 0xFF55FFFF);
            }
        }

        int footerY = by + BOX_H - FOOTER_H;
        ctx.fill(bx, footerY, bx + BOX_W, footerY + 1, 0xFF444444);
        ctx.drawTextWithShadow(textRenderer, Text.literal("↑↓ Seç   Enter Yön göster   Esc Kapat"),
                bx + 8, footerY + 5, 0xFF555555);
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

    // 1.21.9+ — mouseClicked(Click click, boolean isDouble)
    @Override
    public boolean mouseClicked(Click click, boolean isDouble) {
        int bx          = (width - BOX_W) / 2;
        int by          = (height - BOX_H) / 2;
        int resultAreaY = by + INPUT_H + 2;
        double mx = click.mouseX();
        double my = click.mouseY();
        for (int i = 0; i < results.size(); i++) {
            int ry = resultAreaY + i * ROW_H;
            if (mx >= bx && mx <= bx + BOX_W && my >= ry && my <= ry + ROW_H) {
                selectedIndex = i;
                activateSelected();
                return true;
            }
        }
        return super.mouseClicked(click, isDouble);
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
                chest.getZ() - mc.player.getZ(), chest.getX() - mc.player.getX())) + 360) % 360;
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