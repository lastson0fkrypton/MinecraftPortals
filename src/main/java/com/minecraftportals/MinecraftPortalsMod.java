package com.minecraftportals;

import com.minecraftportals.block.ModBlocks;
import com.minecraftportals.item.ModItems;
import com.minecraftportals.network.ShootBluePortalPayload;
import com.minecraftportals.portal.PortalColor;
import com.minecraftportals.portal.PortalPlacement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;

public class MinecraftPortalsMod implements ModInitializer {
    public static final String MOD_ID = "minecraftportals";

    @Override
    public void onInitialize() {
        ModBlocks.register();
        ModItems.register();
        PayloadTypeRegistry.playC2S().register(ShootBluePortalPayload.ID, ShootBluePortalPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(ShootBluePortalPayload.ID, (payload, context) -> {
            context.server().execute(() -> {
                if (!context.player().getMainHandStack().isOf(ModItems.PORTAL_GUN)
                    && !context.player().getOffHandStack().isOf(ModItems.PORTAL_GUN)) {
                    return;
                }

                PortalPlacement.shootAtBlock(context.player().getServerWorld(), context.player(), payload.blockPos(), payload.side(), PortalColor.BLUE);
            });
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (!player.getStackInHand(hand).isOf(ModItems.PORTAL_GUN)) {
                return ActionResult.PASS;
            }

            return ActionResult.FAIL;
        });

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (!player.getStackInHand(hand).isOf(ModItems.PORTAL_GUN)) {
                return TypedActionResult.pass(player.getStackInHand(hand));
            }

            if (!world.isClient()) {
                PortalPlacement.shoot((ServerWorld) world, player, PortalColor.ORANGE);
            }

            return TypedActionResult.success(player.getStackInHand(hand), world.isClient());
        });
    }

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }
}
