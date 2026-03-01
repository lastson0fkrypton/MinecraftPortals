package com.minecraftportals.item;

import com.minecraftportals.MinecraftPortalsMod;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public final class ModItems {
    public static final Item PORTAL_GUN = register("portal_gun", new Item(new Item.Settings().maxCount(1)));

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, MinecraftPortalsMod.id(name), item);
    }

    public static void register() {
    }
}
