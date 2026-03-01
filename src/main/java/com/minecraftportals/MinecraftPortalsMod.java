package com.minecraftportals;

import com.minecraftportals.block.ModBlocks;
import com.minecraftportals.item.ModItems;
import com.minecraftportals.network.ShootBluePortalPayload;
import com.minecraftportals.portal.PortalColor;
import com.minecraftportals.portal.PortalPlacement;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.minecraft.server.command.CommandManager.literal;

public class MinecraftPortalsMod implements ModInitializer {
    public static final String MOD_ID = "minecraftportals";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static String getModVersion() {
        return FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev-unknown");
    }

    @Override
    public void onInitialize() {
        String version = getModVersion();
        LOGGER.info("Initializing {} v{}", MOD_ID, version);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("portalversion")
                .executes(context -> {
                    context.getSource().sendFeedback(() -> Text.literal("minecraftportals v" + getModVersion()), false);
                    return 1;
                }));
        });

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
