package com.minecraftportals.portal;

import com.minecraftportals.block.ModBlocks;
import net.minecraft.block.Block;

public enum PortalColor {
    BLUE,
    ORANGE;

    public PortalColor other() {
        return this == BLUE ? ORANGE : BLUE;
    }

    public Block block() {
        return this == BLUE ? ModBlocks.BLUE_PORTAL : ModBlocks.ORANGE_PORTAL;
    }
}
