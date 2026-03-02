package com.minecraftportals.item;

import com.minecraftportals.MinecraftPortalsMod;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Item PORTAL_GUN = register("portal_gun", createPortalGun());

    private ModItems() {
    }

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, MinecraftPortalsMod.id(name), item);
    }

    private static Item createPortalGun() {
        Identifier id = MinecraftPortalsMod.id("portal_gun");
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return new Item(new Item.Settings()
            .registryKey(key)
            .maxCount(1));
    }

    public static void register() {
    }
}
