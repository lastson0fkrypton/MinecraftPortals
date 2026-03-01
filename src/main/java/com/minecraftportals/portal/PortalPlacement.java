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
        BlockPos first = hitPos.offset(side);
        Direction facing = side;
        Direction.Axis pairAxis = facing.getAxis().isVertical()
            ? player.getHorizontalFacing().getAxis()
            : Direction.Axis.Y;

        Direction pairDirection = switch (pairAxis) {
            case X -> Direction.EAST;
            case Y -> Direction.UP;
            case Z -> Direction.SOUTH;
        };

        BlockPos second = first.offset(pairDirection);

        if (!canPlace(world, first, second, side)) {
            return false;
        }

        placePortalBlocks(world, first, second, pairAxis, facing, color.block());
        PortalStateTracker.put(world.getServer(), player.getUuid(), color, new PortalStateTracker.PortalLocation(world.getRegistryKey(), first, second, facing));

        world.playSound(null, first, SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.BLOCKS, 0.6F, color == PortalColor.BLUE ? 1.4F : 0.8F);
        return true;
    }

    private static void placePortalBlocks(ServerWorld world, BlockPos first, BlockPos second, Direction.Axis axis, Direction facing, Block portalBlock) {
        BlockState firstState = portalBlock.getDefaultState()
            .with(PortalBlock.HALF, DoubleBlockHalf.LOWER)
            .with(PortalBlock.AXIS, axis)
            .with(PortalBlock.FACING, facing);
        BlockState secondState = portalBlock.getDefaultState()
            .with(PortalBlock.HALF, DoubleBlockHalf.UPPER)
            .with(PortalBlock.AXIS, axis)
            .with(PortalBlock.FACING, facing);

        world.setBlockState(first, firstState, Block.NOTIFY_ALL);
        world.setBlockState(second, secondState, Block.NOTIFY_ALL);
    }

    private static boolean canPlace(ServerWorld world, BlockPos first, BlockPos second, Direction side) {
        if (!world.getBlockState(first).isReplaceable() || !world.getBlockState(second).isReplaceable()) {
            return false;
        }

        BlockPos backFirst = first.offset(side.getOpposite());
        BlockPos backSecond = second.offset(side.getOpposite());
        return world.getBlockState(backFirst).isSideSolidFullSquare(world, backFirst, side)
            && world.getBlockState(backSecond).isSideSolidFullSquare(world, backSecond, side);
    }

}
