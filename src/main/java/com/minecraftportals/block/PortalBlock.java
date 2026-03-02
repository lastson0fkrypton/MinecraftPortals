package com.minecraftportals.block;

import com.minecraftportals.portal.PortalColor;
import com.minecraftportals.portal.PortalStateTracker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class PortalBlock extends Block {
    public static final EnumProperty<DoubleBlockHalf> HALF = EnumProperty.of("half", DoubleBlockHalf.class);
    public static final EnumProperty<Direction.Axis> AXIS = EnumProperty.of("axis", Direction.Axis.class);
    public static final EnumProperty<Direction> FACING = EnumProperty.of("facing", Direction.class);
    private static final VoxelShape NORTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 15.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 16.0, 1.0);
    private static final VoxelShape EAST_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 1.0, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = Block.createCuboidShape(15.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape UP_SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = Block.createCuboidShape(0.0, 15.0, 0.0, 16.0, 16.0, 16.0);
    private static final double PLANE_TOLERANCE = 0.07;
    private final PortalColor color;

    public PortalBlock(PortalColor color, Settings settings) {
        super(settings);
        this.color = color;
        this.setDefaultState(getStateManager().getDefaultState()
            .with(HALF, DoubleBlockHalf.LOWER)
            .with(AXIS, Direction.Axis.Y)
            .with(FACING, Direction.NORTH));
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
            case UP -> UP_SHAPE;
            case DOWN -> DOWN_SHAPE;
        };
    }

    @Override
    protected void onStateReplaced(BlockState state, ServerWorld world, BlockPos pos, boolean moved) {
        BlockPos linked = findLinkedHalf(state, world, pos);
        if (linked != null) {
            world.setBlockState(linked, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
        }

        super.onStateReplaced(state, world, pos, moved);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient()) {
            BlockPos linked = findLinkedHalf(state, world, pos);
            if (linked != null) {
                world.setBlockState(linked, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
            }
        }

        return super.onBreak(world, pos, state, player);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity, EntityCollisionHandler handler, boolean canBreatheInWater) {
        if (world.isClient() || !(world instanceof ServerWorld serverWorld)) {
            return;
        }

        if (!isTouchingPortalTriggerVolume(state, pos, entity.getBoundingBox())) {
            return;
        }

        PortalStateTracker.tryTeleport(serverWorld, pos, this.color, entity);
    }

    private static boolean isTouchingPortalTriggerVolume(BlockState state, BlockPos pos, Box entityBox) {
        Direction facing = state.get(FACING);
        if (facing.getAxis().isVertical()) {
            return entityBox.intersects(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
        }

        double plane = switch (facing) {
            case NORTH -> pos.getZ() + 0.9375;
            case SOUTH -> pos.getZ() + 0.0625;
            case EAST -> pos.getX() + 0.0625;
            case WEST -> pos.getX() + 0.9375;
            case UP -> pos.getY() + 0.0625;
            case DOWN -> pos.getY() + 0.9375;
        };

        return switch (facing.getAxis()) {
            case X -> entityBox.maxX >= plane - PLANE_TOLERANCE && entityBox.minX <= plane + PLANE_TOLERANCE;
            case Y -> entityBox.maxY >= plane - PLANE_TOLERANCE && entityBox.minY <= plane + PLANE_TOLERANCE;
            case Z -> entityBox.maxZ >= plane - PLANE_TOLERANCE && entityBox.minZ <= plane + PLANE_TOLERANCE;
        };
    }

    private static BlockPos findLinkedHalf(BlockState state, World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.offset(direction);
            BlockState adjacent = world.getBlockState(adjacentPos);
            if (!adjacent.isOf(state.getBlock())) {
                continue;
            }

            if (adjacent.get(FACING) == state.get(FACING)
                && adjacent.get(AXIS) == state.get(AXIS)
                && adjacent.get(HALF) != state.get(HALF)) {
                return adjacentPos;
            }
        }

        return null;
    }
}
