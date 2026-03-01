package com.minecraftportals.block;

import com.minecraftportals.MinecraftPortalsMod;
import com.minecraftportals.portal.PortalColor;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;

public final class ModBlocks {
    public static final Block BLUE_PORTAL = register("blue_portal_thin", new PortalBlock(PortalColor.BLUE, AbstractBlock.Settings.create()
        .mapColor(MapColor.BLUE)
        .strength(-1.0F, 3600000.0F)
        .dropsNothing()
        .noCollision()
        .nonOpaque()
        .luminance(state -> 12)
        .sounds(BlockSoundGroup.GLASS)));

    public static final Block ORANGE_PORTAL = register("orange_portal_thin", new PortalBlock(PortalColor.ORANGE, AbstractBlock.Settings.create()
        .mapColor(MapColor.ORANGE)
        .strength(-1.0F, 3600000.0F)
        .dropsNothing()
        .noCollision()
        .nonOpaque()
        .luminance(state -> 12)
        .sounds(BlockSoundGroup.GLASS)));

    private ModBlocks() {
    }

    private static Block register(String name, Block block) {
        return Registry.register(Registries.BLOCK, MinecraftPortalsMod.id(name), block);
    }

    public static void register() {
    }
}
