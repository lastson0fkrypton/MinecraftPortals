package com.minecraftportals;

import com.minecraftportals.block.ModBlocks;
import com.minecraftportals.item.ModItems;
import com.minecraftportals.network.ShootBluePortalPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class MinecraftPortalsClient implements ClientModInitializer {
    private static boolean wasAttackPressed;
    private static boolean renderLayersRegistered;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!renderLayersRegistered) {
                BlockRenderLayerMap.putBlocks(BlockRenderLayer.TRANSLUCENT, ModBlocks.BLUE_PORTAL, ModBlocks.ORANGE_PORTAL);
                renderLayersRegistered = true;
            }

            if (client.player == null || client.world == null || client.currentScreen != null) {
                wasAttackPressed = false;
                return;
            }

            boolean attackPressed = client.options.attackKey.isPressed();
            if (attackPressed
                && !wasAttackPressed
                && client.player.getMainHandStack().isOf(ModItems.PORTAL_GUN)) {
                HitResult hit = client.player.raycast(64.0, 0.0F, false);
                if (hit instanceof BlockHitResult blockHit) {
                    ClientPlayNetworking.send(new ShootBluePortalPayload(blockHit.getBlockPos(), blockHit.getSide()));
                }
            }

            wasAttackPressed = attackPressed;
        });
    }
}
