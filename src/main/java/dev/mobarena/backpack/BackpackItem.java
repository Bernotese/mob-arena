package dev.mobarena.backpack;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

public final class BackpackItem {
    public static final String TAG = "mobarena:team_backpack";
    private BackpackItem() { }

    public static ItemStack create() {
        ItemStack stack = new ItemStack(Items.BUNDLE);
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(TAG, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Team Backpack"));
        stack.remove(DataComponents.BUNDLE_CONTENTS);
        return stack;
    }
}
