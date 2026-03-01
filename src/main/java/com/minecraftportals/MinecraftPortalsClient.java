package com.minecraftportals;

import com.minecraftportals.block.ModBlocks;
import com.minecraftportals.item.ModItems;
import com.minecraftportals.network.ShootBluePortalPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

public class MinecraftPortalsClient implements ClientModInitializer {
    private static boolean wasAttackPressed;

    @Override
    public void onInitializeClient() {
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getTranslucent(), ModBlocks.BLUE_PORTAL, ModBlocks.ORANGE_PORTAL);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
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
