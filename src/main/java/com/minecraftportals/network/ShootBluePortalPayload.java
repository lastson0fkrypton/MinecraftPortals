package com.minecraftportals.network;

import com.minecraftportals.MinecraftPortalsMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public record ShootBluePortalPayload(BlockPos blockPos, Direction side) implements CustomPayload {
    public static final Id<ShootBluePortalPayload> ID = new Id<>(MinecraftPortalsMod.id("shoot_blue_portal"));
    public static final PacketCodec<RegistryByteBuf, ShootBluePortalPayload> CODEC = PacketCodec.tuple(
        BlockPos.PACKET_CODEC,
        ShootBluePortalPayload::blockPos,
        Direction.PACKET_CODEC,
        ShootBluePortalPayload::side,
        ShootBluePortalPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
