package com.minecraftportals.portal;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;

public final class PortalStateTracker {
    private static final Map<UUID, EnumMap<PortalColor, PortalLocation>> PORTALS = new ConcurrentHashMap<>();
    private static final Map<PortalKey, PortalOwner> PORTAL_INDEX = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TELEPORT_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long TELEPORT_COOLDOWN_MS = 800L;
    private static final String SAVE_FILE_NAME = "minecraftportals_portals.txt";
    private static final int CLEANUP_LIMIT = 256;
    private static Path loadedWorldPath;

    private PortalStateTracker() {
    }

    public static void put(MinecraftServer server, UUID playerId, PortalColor color, PortalLocation location) {
        ensureLoaded(server);

        EnumMap<PortalColor, PortalLocation> byColor = PORTALS.computeIfAbsent(playerId, key -> new EnumMap<>(PortalColor.class));
        PortalLocation previous = byColor.put(color, location);
        if (previous != null) {
            removeIndex(playerId, color, previous);
        }

        index(playerId, color, location);
        save(server);
    }

    public static void clear(MinecraftServer server, UUID playerId, PortalColor color) {
        ensureLoaded(server);

        EnumMap<PortalColor, PortalLocation> byColor = PORTALS.get(playerId);
        if (byColor == null) {
            return;
        }

        PortalLocation location = byColor.remove(color);
        if (location == null) {
            return;
        }

        removeIndex(playerId, color, location);

        ServerWorld world = server.getWorld(location.worldKey());
        if (world == null) {
            return;
        }

        Block portalBlock = color.block();
        cleanupPortalCluster(world, location.firstPos(), portalBlock);
        cleanupPortalCluster(world, location.secondPos(), portalBlock);

        save(server);
    }

    public static void tryTeleport(ServerWorld fromWorld, BlockPos enteredPos, PortalColor enteredColor, Entity entity) {
        ensureLoaded(fromWorld.getServer());

        if (entity.getEntityWorld().isClient() || entity.isRemoved()) {
            return;
        }

        long now = System.currentTimeMillis();
        Long nextAllowed = TELEPORT_COOLDOWNS.get(entity.getUuid());
        if (nextAllowed != null && now < nextAllowed) {
            return;
        }

        PortalOwner owner = PORTAL_INDEX.get(new PortalKey(fromWorld.getRegistryKey(), enteredPos));
        if (owner == null || owner.color() != enteredColor) {
            return;
        }

        EnumMap<PortalColor, PortalLocation> pair = PORTALS.get(owner.playerId());
        if (pair == null) {
            return;
        }

        PortalLocation source = pair.get(enteredColor);
        if (source == null) {
            return;
        }

        PortalLocation destination = pair.get(enteredColor.other());
        if (destination == null) {
            return;
        }

        ServerWorld targetWorld = fromWorld.getServer().getWorld(destination.worldKey());
        if (targetWorld == null) {
            return;
        }

        if (!targetWorld.getBlockState(destination.firstPos()).isOf(enteredColor.other().block())
            || !targetWorld.getBlockState(destination.secondPos()).isOf(enteredColor.other().block())) {
            return;
        }

        PortalFrame sourceFrame = buildFrame(source);
        PortalFrame destinationFrame = buildFrame(destination);
        Vec3d entityCenter = entity.getBoundingBox().getCenter();
        Vec3d incomingVelocity = entity.getVelocity();

        Vec3d relativeToSource = entityCenter.subtract(sourceFrame.center());
        double relAlongSpan = relativeToSource.dotProduct(sourceFrame.spanAxis());
        double relAlongSide = relativeToSource.dotProduct(sourceFrame.sideAxis());

        double velIntoPortal = incomingVelocity.dotProduct(sourceFrame.entryNormal());
        double velAlongSpan = incomingVelocity.dotProduct(sourceFrame.spanAxis());
        double velAlongSide = incomingVelocity.dotProduct(sourceFrame.sideAxis());

        Vec3d outgoingVelocity = destinationFrame.exitNormal().multiply(Math.max(0.0, velIntoPortal))
            .add(destinationFrame.spanAxis().multiply(velAlongSpan))
            .add(destinationFrame.sideAxis().multiply(velAlongSide));

        double exitOffset = 0.55;
        Vec3d outPosition = destinationFrame.center()
            .add(destinationFrame.spanAxis().multiply(relAlongSpan))
            .add(destinationFrame.sideAxis().multiply(relAlongSide))
            .add(destinationFrame.exitNormal().multiply(exitOffset));

        double x = outPosition.x;
        double y = outPosition.y - (entity.getHeight() * 0.5);
        double z = outPosition.z;
        float yaw = destination.facing().getAxis().isVertical() ? entity.getYaw() : facingToYaw(destination.facing());
        float pitch = entity.getPitch();
        TeleportTarget teleportTarget = new TeleportTarget(targetWorld, new Vec3d(x, y, z), outgoingVelocity, yaw, pitch, TeleportTarget.NO_OP);

        if (entity instanceof ServerPlayerEntity player) {
            if (targetWorld == fromWorld) {
                player.networkHandler.requestTeleport(x, y, z, yaw, pitch);
            } else {
                player.teleportTo(teleportTarget);
            }
            player.setBodyYaw(yaw);
            player.setHeadYaw(yaw);
            player.setVelocity(outgoingVelocity);
            player.setOnGround(false);
            player.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(player));
        } else {
            Entity teleported = targetWorld == fromWorld ? entity : entity.teleportTo(teleportTarget);
            if (teleported == null) {
                return;
            }

            if (targetWorld == fromWorld) {
                teleported.requestTeleport(x, y, z);
            }

            teleported.setYaw(yaw);
            teleported.setVelocity(outgoingVelocity);
            if (teleported instanceof LivingEntity livingEntity) {
                livingEntity.setBodyYaw(yaw);
                livingEntity.setHeadYaw(yaw);
            }
        }

        TELEPORT_COOLDOWNS.put(entity.getUuid(), now + TELEPORT_COOLDOWN_MS);
    }

    private static void index(UUID playerId, PortalColor color, PortalLocation location) {
        PortalOwner owner = new PortalOwner(playerId, color);
        PORTAL_INDEX.put(new PortalKey(location.worldKey(), location.firstPos()), owner);
        PORTAL_INDEX.put(new PortalKey(location.worldKey(), location.secondPos()), owner);
    }

    private static void removeIndex(UUID playerId, PortalColor color, PortalLocation location) {
        PortalOwner owner = new PortalOwner(playerId, color);
        PORTAL_INDEX.remove(new PortalKey(location.worldKey(), location.firstPos()), owner);
        PORTAL_INDEX.remove(new PortalKey(location.worldKey(), location.secondPos()), owner);
    }

    private record PortalOwner(UUID playerId, PortalColor color) {
    }

    private record PortalKey(net.minecraft.registry.RegistryKey<World> worldKey, BlockPos pos) {
    }

    public record PortalLocation(net.minecraft.registry.RegistryKey<World> worldKey, BlockPos firstPos, BlockPos secondPos, Direction facing) {
    }

    private static void ensureLoaded(MinecraftServer server) {
        Path worldRoot = server.getSavePath(WorldSavePath.ROOT).toAbsolutePath().normalize();
        if (worldRoot.equals(loadedWorldPath)) {
            return;
        }

        PORTALS.clear();
        PORTAL_INDEX.clear();

        loadedWorldPath = worldRoot;
        Path file = getSaveFile(server);
        if (!Files.exists(file)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\|");
                if (parts.length != 8 && parts.length != 11) {
                    continue;
                }

                UUID playerId = UUID.fromString(parts[0]);
                PortalColor color = PortalColor.valueOf(parts[1]);
                Identifier worldId = Identifier.of(parts[2]);
                int firstX = Integer.parseInt(parts[3]);
                int firstY = Integer.parseInt(parts[4]);
                int firstZ = Integer.parseInt(parts[5]);
                Direction facing;
                try {
                    facing = Direction.valueOf(parts[6].toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    facing = Direction.NORTH;
                }

                RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
                BlockPos firstPos = new BlockPos(firstX, firstY, firstZ);
                BlockPos secondPos;

                if (parts.length == 11) {
                    int secondX = Integer.parseInt(parts[7]);
                    int secondY = Integer.parseInt(parts[8]);
                    int secondZ = Integer.parseInt(parts[9]);
                    secondPos = new BlockPos(secondX, secondY, secondZ);
                } else {
                    secondPos = switch (facing.getAxis()) {
                        case Y -> firstPos.offset(Direction.SOUTH);
                        case X, Z -> firstPos.up();
                    };
                }

                PortalLocation location = new PortalLocation(worldKey, firstPos, secondPos, facing);

                EnumMap<PortalColor, PortalLocation> byColor = PORTALS.computeIfAbsent(playerId, key -> new EnumMap<>(PortalColor.class));
                byColor.put(color, location);
            }

            rebuildIndex();
        } catch (Exception ignored) {
            PORTALS.clear();
            PORTAL_INDEX.clear();
        }
    }

    private static void save(MinecraftServer server) {
        try {
            Path file = getSaveFile(server);
            Files.createDirectories(file.getParent());

            List<String> lines = new ArrayList<>();
            lines.add("# playerUuid|color|world|firstX|firstY|firstZ|facing|secondX|secondY|secondZ|v2");

            for (Map.Entry<UUID, EnumMap<PortalColor, PortalLocation>> entry : PORTALS.entrySet()) {
                UUID playerId = entry.getKey();
                for (Map.Entry<PortalColor, PortalLocation> byColor : entry.getValue().entrySet()) {
                    PortalLocation location = byColor.getValue();
                    lines.add(playerId + "|"
                        + byColor.getKey().name() + "|"
                        + location.worldKey().getValue() + "|"
                        + location.firstPos().getX() + "|"
                        + location.firstPos().getY() + "|"
                        + location.firstPos().getZ() + "|"
                        + location.facing().asString() + "|"
                        + location.secondPos().getX() + "|"
                        + location.secondPos().getY() + "|"
                        + location.secondPos().getZ() + "|v2");
                }
            }

            Files.write(file, lines, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
        }
    }

    private static Path getSaveFile(MinecraftServer server) {
        return server.getSavePath(WorldSavePath.ROOT).resolve("data").resolve(SAVE_FILE_NAME);
    }

    private static void rebuildIndex() {
        PORTAL_INDEX.clear();
        for (Map.Entry<UUID, EnumMap<PortalColor, PortalLocation>> entry : PORTALS.entrySet()) {
            UUID playerId = entry.getKey();
            for (Map.Entry<PortalColor, PortalLocation> byColor : entry.getValue().entrySet()) {
                index(playerId, byColor.getKey(), byColor.getValue());
            }
        }
    }

    private static void cleanupPortalCluster(ServerWorld world, BlockPos start, Block portalBlock) {
        if (!world.getBlockState(start).isOf(portalBlock)) {
            return;
        }

        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        queue.add(start);

        int removed = 0;
        while (!queue.isEmpty() && removed < CLEANUP_LIMIT) {
            BlockPos pos = queue.removeFirst();
            if (!visited.add(pos)) {
                continue;
            }

            if (!world.getBlockState(pos).isOf(portalBlock)) {
                continue;
            }

            world.removeBlock(pos, false);
            removed++;

            for (Direction direction : Direction.values()) {
                queue.add(pos.offset(direction));
            }
        }
    }

    private static float facingToYaw(Direction direction) {
        return switch (direction) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 90.0F;
            case EAST -> -90.0F;
            case UP, DOWN -> 0.0F;
        };
    }

    private static PortalFrame buildFrame(PortalLocation location) {
        Vec3d firstCenter = Vec3d.ofCenter(location.firstPos());
        Vec3d secondCenter = Vec3d.ofCenter(location.secondPos());
        Vec3d center = firstCenter.add(secondCenter).multiply(0.5);

        Vec3d spanAxis = secondCenter.subtract(firstCenter);
        if (spanAxis.lengthSquared() < 1.0E-6) {
            spanAxis = new Vec3d(0.0, 1.0, 0.0);
        } else {
            spanAxis = spanAxis.normalize();
        }

        Vec3d exitNormal = Vec3d.of(location.facing().getVector()).normalize();
        Vec3d sideAxis = exitNormal.crossProduct(spanAxis);
        if (sideAxis.lengthSquared() < 1.0E-6) {
            sideAxis = Math.abs(exitNormal.y) > 0.8
                ? new Vec3d(1.0, 0.0, 0.0)
                : new Vec3d(0.0, 1.0, 0.0);
        } else {
            sideAxis = sideAxis.normalize();
        }

        spanAxis = sideAxis.crossProduct(exitNormal).normalize();
        Vec3d entryNormal = exitNormal.multiply(-1.0);
        return new PortalFrame(center, spanAxis, sideAxis, exitNormal, entryNormal);
    }

    private record PortalFrame(Vec3d center, Vec3d spanAxis, Vec3d sideAxis, Vec3d exitNormal, Vec3d entryNormal) {
    }
}
