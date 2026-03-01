package com.minecraftportals.portal;

import com.minecraftportals.block.PortalBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public final class PortalPlacement {
    private static final double MAX_RANGE = 64.0;

    private PortalPlacement() {
    }

    public static boolean shoot(ServerWorld world, PlayerEntity player, PortalColor color) {
        PortalStateTracker.clear(world.getServer(), player.getUuid(), color);

        BlockHitResult hit = raycast(player);
        if (hit == null) {
            return false;
        }

        return placePortal(world, player, hit.getBlockPos(), hit.getSide(), color);
    }

    public static boolean shootAtBlock(ServerWorld world, PlayerEntity player, BlockPos hitPos, Direction side, PortalColor color) {
        if (world.getBlockState(hitPos).isOf(color.block())) {
            PortalStateTracker.clear(world.getServer(), player.getUuid(), color);
            return shoot(world, player, color);
        }

        PortalStateTracker.clear(world.getServer(), player.getUuid(), color);
        return placePortal(world, player, hitPos, side, color);
    }

    private static BlockHitResult raycast(PlayerEntity player) {
        HitResult hit = player.raycast(MAX_RANGE, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return (BlockHitResult) hit;
    }

    private static boolean placePortal(ServerWorld world, PlayerEntity player, BlockPos hitPos, Direction side, PortalColor color) {
        if (side.getAxis().isVertical()) {
            return false;
        }

        BlockPos bottom = hitPos.offset(side);
        Direction facing = side;
        Direction.Axis axis = facing.getAxis();

        if (!canPlace(world, bottom, side)) {
            return false;
        }

        placePortalBlocks(world, bottom, axis, facing, color.block());
        PortalStateTracker.put(world.getServer(), player.getUuid(), color, new PortalStateTracker.PortalLocation(world.getRegistryKey(), bottom, facing));

        world.playSound(null, bottom, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.6F, color == PortalColor.BLUE ? 1.4F : 0.8F);
        return true;
    }

    private static void placePortalBlocks(ServerWorld world, BlockPos bottom, Direction.Axis axis, Direction facing, Block portalBlock) {
        BlockState lower = portalBlock.getDefaultState()
            .with(PortalBlock.HALF, DoubleBlockHalf.LOWER)
            .with(PortalBlock.AXIS, axis)
            .with(PortalBlock.FACING, facing);
        BlockState upper = portalBlock.getDefaultState()
            .with(PortalBlock.HALF, DoubleBlockHalf.UPPER)
            .with(PortalBlock.AXIS, axis)
            .with(PortalBlock.FACING, facing);

        world.setBlockState(bottom, lower, Block.NOTIFY_ALL);
        world.setBlockState(bottom.up(), upper, Block.NOTIFY_ALL);
    }

    private static boolean canPlace(ServerWorld world, BlockPos bottom, Direction side) {
        if (!world.getBlockState(bottom).isReplaceable() || !world.getBlockState(bottom.up()).isReplaceable()) {
            return false;
        }

        BlockPos backBottom = bottom.offset(side.getOpposite());
        BlockPos backTop = backBottom.up();
        return world.getBlockState(backBottom).isSideSolidFullSquare(world, backBottom, side)
            && world.getBlockState(backTop).isSideSolidFullSquare(world, backTop, side);
    }

}
