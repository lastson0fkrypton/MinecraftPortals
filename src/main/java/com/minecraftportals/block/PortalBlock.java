package com.minecraftportals.block;

import com.minecraftportals.portal.PortalColor;
import com.minecraftportals.portal.PortalStateTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class PortalBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.of("half", DoubleBlockHalf.class);
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("axis", Direction.Axis.class, Direction.Axis.X, Direction.Axis.Z);
    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final double PLANE_TOLERANCE = 0.07;
    private final PortalColor color;

    public PortalBlock(PortalColor color, Settings settings) {
        super(settings);
        this.color = color;
        this.setDefaultState(getStateManager().getDefaultState()
            .with(HALF, DoubleBlockHalf.LOWER)
            .with(AXIS, Direction.Axis.X)
            .with(FACING, Direction.EAST));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(HALF, AXIS, FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case SOUTH -> SOUTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> EAST_SHAPE;
        };
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (state.isOf(newState.getBlock())) {
            super.onStateReplaced(state, world, pos, newState, moved);
            return;
        }

        if (!world.isClient()) {
            DoubleBlockHalf half = state.get(HALF);
            BlockPos otherPos = half == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            BlockState otherState = world.getBlockState(otherPos);

            if (otherState.isOf(this) && otherState.get(HALF) != half) {
                world.removeBlock(otherPos, false);
            }
        }

        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient()) {
            BlockPos otherPos = state.get(HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
            BlockState otherState = world.getBlockState(otherPos);
            if (otherState.isOf(this) && otherState.get(HALF) != state.get(HALF)) {
                world.breakBlock(otherPos, false, player);
            }
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        if (!isTouchingPortalPlane(state, pos, entity.getBoundingBox())) {
            return;
        }

        BlockPos portalBottom = state.get(HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos;
        PortalStateTracker.tryTeleport(serverWorld, portalBottom, this.color, entity);
    }

    private static boolean isTouchingPortalPlane(BlockState state, BlockPos pos, Box entityBox) {
        Direction facing = state.get(FACING);
        double plane = switch (facing) {
            case NORTH -> pos.getZ() + 0.9375;
            case SOUTH -> pos.getZ() + 0.0625;
            case EAST -> pos.getX() + 0.0625;
            case WEST -> pos.getX() + 0.9375;
            default -> pos.getX() + 0.5;
        };

        return switch (facing.getAxis()) {
            case X -> entityBox.maxX >= plane - PLANE_TOLERANCE && entityBox.minX <= plane + PLANE_TOLERANCE;
            case Y -> false;
            case Z -> entityBox.maxZ >= plane - PLANE_TOLERANCE && entityBox.minZ <= plane + PLANE_TOLERANCE;
        };
    }
}
