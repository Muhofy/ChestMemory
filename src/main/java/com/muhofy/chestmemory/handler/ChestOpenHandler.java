package com.muhofy.chestmemory.handler;

import com.muhofy.chestmemory.ChestMemoryMod;
import com.muhofy.chestmemory.config.ChestMemoryConfig;
import com.muhofy.chestmemory.data.ChestItem;
import com.muhofy.chestmemory.data.ChestRecord;
import com.muhofy.chestmemory.data.ChestStorage;
import com.muhofy.chestmemory.ui.ChestMemoryHud;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.block.entity.ChestBlockEntity;
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

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            // Sadece GenericContainerScreen — 3x9 sandık
            if (!(screen instanceof GenericContainerScreen containerScreen)) return;
            if (!(containerScreen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) return;

            // Sadece 27 slotlu sandıkları al (çift sandık 54 slot)
            // Her ikisini de destekliyoruz: 27 (tek) ve 54 (çift)
            int rows = handler.getRows();
            if (rows != 3 && rows != 6) return;
            int slotCount = rows * 9;

            // Sandığın BlockPos'unu al — oyuncunun hedef aldığı blok
            BlockPos chestPos = getTargetChestPos(client);
            if (chestPos == null) {
                ChestMemoryMod.LOGGER.warn("[ChestOpenHandler] Could not determine chest position.");
                return;
            }

            // Boyutu al
            World world = client.world;
            if (world == null) return;
            String dimension = world.getRegistryKey().getValue().toString();

            // Slotları oku — sunucu bu noktada sync'lemiş olmalı
            // Eğer henüz sync olmadıysa screen'in AFTER_INIT'i bekleyeceğiz
            ScreenEvents.afterTick(screen).register(tickedScreen -> {
                // Sadece bir kere çalıştır
                ScreenEvents.afterTick(tickedScreen); // unregister için placeholder
                indexChest(client, handler, chestPos, dimension, slotCount);
            });
        });

        ChestMemoryMod.LOGGER.info("[ChestOpenHandler] Registered.");
    }

    private static void indexChest(MinecraftClient client,
                                   GenericContainerScreenHandler handler,
                                   BlockPos pos, String dimension, int slotCount) {
        List<ChestItem> items = new ArrayList<>();

        for (int i = 0; i < slotCount; i++) {
            Slot slot = handler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            String itemId      = Registries.ITEM.getId(stack.getItem()).toString();
            String displayName = stack.getName().getString();
            int count          = stack.getCount();

            items.add(new ChestItem(i, itemId, count, displayName));
        }

        ChestStorage storage = ChestStorage.getInstance();
        boolean isNew = storage.getAt(pos.getX(), pos.getY(), pos.getZ(), dimension) == null;

        ChestRecord record = storage.addOrUpdate(
                pos.getX(), pos.getY(), pos.getZ(), dimension, items
        );

        ChestMemoryMod.LOGGER.info("[ChestOpenHandler] {} chest at {} with {} items.",
                isNew ? "Indexed" : "Updated", pos, items.size());

        // Toast tetikle
        if (ChestMemoryConfig.getInstance().toastEnabled) {
            String name = storage.getDisplayName(record);
            if (isNew) {
                ChestMemoryHud.pushToast(
                        "chestmemory.toast.indexed",
                        name + " • " + items.size() + " item",
                        ChestMemoryHud.ToastType.SUCCESS
                );
            } else {
                ChestMemoryHud.pushToast(
                        "chestmemory.toast.updated",
                        name + " • " + items.size() + " item",
                        ChestMemoryHud.ToastType.INFO
                );
            }
        }
    }

    /**
     * Oyuncunun crosshair'inin baktığı bloğun pozisyonunu döndürür.
     * Sandık açılırken hedef sandık bloğu olmalıdır.
     */
    private static BlockPos getTargetChestPos(MinecraftClient client) {
        if (client.crosshairTarget == null) return null;
        if (client.crosshairTarget.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = ((BlockHitResult) client.crosshairTarget).getBlockPos();

        // Hedef blok gerçekten bir sandık mı doğrula
        if (client.world == null) return null;
        if (client.world.getBlockEntity(pos) instanceof ChestBlockEntity) {
            return pos;
        }

        return null;
    }
}