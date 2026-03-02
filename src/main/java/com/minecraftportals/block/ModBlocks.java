package com.minecraftportals.block;

import com.minecraftportals.MinecraftPortalsMod;
import com.minecraftportals.portal.PortalColor;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public final class ModBlocks {
    public static final Block BLUE_PORTAL = register("blue_portal_thin", createPortalBlock("blue_portal_thin", PortalColor.BLUE, MapColor.BLUE));

    public static final Block ORANGE_PORTAL = register("orange_portal_thin", createPortalBlock("orange_portal_thin", PortalColor.ORANGE, MapColor.ORANGE));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, MinecraftPortalsMod.id(name), block);
    }

    private static Block createPortalBlock(String name, PortalColor color, MapColor mapColor) {
        Identifier id = MinecraftPortalsMod.id(name);
        RegistryKey<Block> key = RegistryKey.of(RegistryKeys.BLOCK, id);
        return new PortalBlock(color, AbstractBlock.Settings.create()
            .registryKey(key)
            .mapColor(mapColor)
            .strength(-1.0F, 3600000.0F)
            .dropsNothing()
            .noCollision()
            .nonOpaque()
            .luminance(state -> 12)
            .sounds(BlockSoundGroup.GLASS));
    }

    public static void register() {
    }
}
