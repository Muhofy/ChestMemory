package com.muhofy.chestmemory.handler;

import com.muhofy.chestmemory.ChestMemoryMod;
import com.muhofy.chestmemory.config.ChestMemoryConfig;
import com.muhofy.chestmemory.data.ChestItem;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import com.muhofy.chestmemory.ui.ChestMemoryHud;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

public class ChestOpenHandler {

    private static boolean indexedThisOpen = false;

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof GenericContainerScreen containerScreen)) return;
            if (!(containerScreen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

            int rows = handler.getRows();
            if (rows != 3 && rows != 6) return;
            int slotCount = rows * 9;
            boolean isDouble = rows == 6;

            BlockPos hitPos = getTargetChestPos(client);
            if (hitPos == null) {
                ChestMemoryMod.LOGGER.warn("[ChestOpenHandler] Could not determine chest position.");
                return;
            }

            World world = client.world;
            if (world == null) return;
            String dimension = world.getRegistryKey().getValue().toString();

            // Double chest ise canonical (sol/küçük koordinat) pozisyonu bul
            BlockPos canonicalPos = isDouble ? getCanonicalPos(client, hitPos) : hitPos;

            indexedThisOpen = false;

            ScreenEvents.afterTick(screen).register(tickedScreen -> {
                if (indexedThisOpen) return;
                indexedThisOpen = true;
                indexChest(client, handler, canonicalPos, dimension, slotCount, isDouble);
            });
        });

        ChestMemoryMod.LOGGER.info("[ChestOpenHandler] Registered.");
    }

    private static void indexChest(MinecraftClient client,
                                   GenericContainerScreenHandler handler,
                                   BlockPos pos, String dimension,
                                   int slotCount, boolean isDouble) {
        List<ChestItem> newItems = new ArrayList<>();

        for (int i = 0; i < slotCount; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;
            newItems.add(new ChestItem(
                    i,
                    Registries.ITEM.getId(stack.getItem()).toString(),
                    stack.getCount(),
                    stack.getName().getString()
            ));
        }

        // Boş sandık — sessizce kaydet, toast yok
        if (newItems.isEmpty()) {
            ChestStorage.getInstance().addOrUpdate(
                    pos.getX(), pos.getY(), pos.getZ(), dimension, newItems, isDouble);
            ChestMemoryMod.LOGGER.info("[ChestOpenHandler] Empty chest at {}, indexed silently.", pos);
            return;
        }

        ChestStorage storage   = ChestStorage.getInstance();
        ChestRecord  existing  = storage.getAt(pos.getX(), pos.getY(), pos.getZ(), dimension);

        // İçerik değişmemişse atla
        if (existing != null && itemsEqual(existing.getItems(), newItems)) {
            ChestMemoryMod.LOGGER.info("[ChestOpenHandler] Chest at {} unchanged, skipping.", pos);
            return;
        }

        boolean     isNew  = existing == null;
        ChestRecord record = storage.addOrUpdate(
                pos.getX(), pos.getY(), pos.getZ(), dimension, newItems, isDouble);

        ChestMemoryMod.LOGGER.info("[ChestOpenHandler] {} {} chest at {} with {} items.",
                isNew ? "Indexed" : "Updated", isDouble ? "double" : "single", pos, newItems.size());

        if (ChestMemoryConfig.getInstance().toastEnabled) {
            String name = storage.getDisplayName(record);
            ChestMemoryHud.pushToast(
                    isNew ? "Sandık indexlendi" : "Sandık güncellendi",
                    name + " • " + newItems.size() + " item",
                    isNew ? ChestMemoryHud.ToastType.SUCCESS : ChestMemoryHud.ToastType.INFO);
        }
    }

    /**
     * Double chest'in iki bloğundan her zaman aynı (canonical) pozisyonu döndürür.
     * Minecraft'ta double chest: LEFT ve RIGHT olmak üzere iki blok.
     * Biz her zaman LEFT tarafı canonical kabul ediyoruz.
     * Eğer oyuncu RIGHT tarafa tıkladıysa, LEFT tarafın pozisyonunu hesapla.
     */
    private static BlockPos getCanonicalPos(MinecraftClient client, BlockPos hitPos) {
        if (client.world == null) return hitPos;

        var blockState = client.world.getBlockState(hitPos);
        if (!(blockState.getBlock() instanceof ChestBlock)) return hitPos;

        ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);

        // Tek sandık veya zaten LEFT — olduğu gibi kullan
        if (chestType == ChestType.SINGLE || chestType == ChestType.LEFT) return hitPos;

        // RIGHT ise — LEFT tarafını bul (komşu blok)
        // LEFT tarafın yönü: ChestBlock.getFacing() + ChestType yönünden hesaplanır
        var facing    = ChestBlock.getFacing(blockState);
        var leftDir   = chestType == ChestType.RIGHT ? facing.rotateYCounterclockwise() : facing.rotateYClockwise();
        BlockPos leftPos = hitPos.offset(leftDir);

        // Sol taraf gerçekten chest mi?
        if (client.world.getBlockEntity(leftPos) instanceof ChestBlockEntity) {
            return leftPos;
        }

        return hitPos; // fallback
    }

    private static boolean itemsEqual(List<ChestItem> a, List<ChestItem> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            ChestItem ia = a.get(i), ib = b.get(i);
            if (ia.getSlot()  != ib.getSlot())           return false;
            if (ia.getCount() != ib.getCount())          return false;
            if (!ia.getItemId().equals(ib.getItemId()))  return false;
        }
        return true;
    }

    private static BlockPos getTargetChestPos(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();
        if (client.world == null) return null;
        if (client.world.getBlockEntity(pos) instanceof ChestBlockEntity) return pos;
        return null;
    }
}