package com.example.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;

import java.util.OptionalInt;

public class ItemStackData {
    private final String itemName;
    private final String displayName;
    private final int count;
    private final int maxCount;
    private final OptionalInt slot;
    private final boolean isSelected;
    private final String customName;
    private final String lore;

    private ItemStackData(String itemName, String displayName, int count, int maxCount, OptionalInt slot, boolean isSelected, String customName, String lore) {
        this.itemName = itemName;
        this.displayName = displayName;
        this.count = count;
        this.maxCount = maxCount;
        this.slot = slot;
        this.isSelected = isSelected;
        this.customName = customName;
        this.lore = lore;
    }

    public static ItemStackData of(ItemStack itemStack, OptionalInt slot, boolean isSelected) {
        String customName = null;
        String lore = null;

        if (itemStack.contains(DataComponentTypes.CUSTOM_NAME)) {
            customName = itemStack.get(DataComponentTypes.CUSTOM_NAME).getString();
        }
        if (itemStack.contains(DataComponentTypes.LORE)) {
            lore = itemStack.get(DataComponentTypes.LORE).toString();
        }

        // 獲取英文物品名稱（使用 registry ID）
        String englishName = "unknown";
        var registry = MinecraftClient.getInstance().world.getRegistryManager().getOptional(RegistryKeys.ITEM);
        if (registry.isPresent()) {
            var itemId = registry.get().getId(itemStack.getItem());
            if (itemId != null) {
                englishName = itemId.getPath(); // 例如："diamond_sword"
            }
        }

        return new ItemStackData(
            englishName,
            itemStack.getName().getString(),
            itemStack.getCount(),
            itemStack.getMaxCount(),
            slot,
            isSelected,
            customName,
            lore
        );
    }

    public String getItemName() {
        return itemName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCount() {
        return count;
    }

    public int getMaxCount() {
        return maxCount;
    }

    public OptionalInt getSlot() {
        return slot;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public String getCustomName() {
        return customName;
    }

    public String getLore() {
        return lore;
    }

    @Override
    public String toString() {
        return "ItemStackData{" +
            "itemName='" + itemName + '\'' +
            ", displayName='" + displayName + '\'' +
            ", count=" + count +
            ", maxCount=" + maxCount +
            ", slot=" + slot.orElse(-1) +
            ", isSelected=" + isSelected +
            ", customName='" + customName + '\'' +
            ", lore='" + lore + '\'' +
            '}';
    }
}
