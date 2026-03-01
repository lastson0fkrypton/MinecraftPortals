package com.minecraftportals.portal;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.WorldSavePath;

public final class PortalStateTracker {
    private static final Map<UUID, EnumMap<PortalColor, PortalLocation>> PORTALS = new ConcurrentHashMap<>();
    private static final Map<PortalKey, PortalOwner> PORTAL_INDEX = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> TELEPORT_COOLDOWNS = new ConcurrentHashMap<>();
    private static final long TELEPORT_COOLDOWN_MS = 800L;
    private static final String SAVE_FILE_NAME = "minecraftportals_portals.txt";
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
        if (world.getBlockState(location.firstPos()).isOf(portalBlock)) {
            world.removeBlock(location.firstPos(), false);
        }
        if (world.getBlockState(location.secondPos()).isOf(portalBlock)) {
            world.removeBlock(location.secondPos(), false);
        }

        save(server);
    }

    public static void tryTeleport(ServerWorld fromWorld, BlockPos enteredPos, PortalColor enteredColor, Entity entity) {
        ensureLoaded(fromWorld.getServer());

        if (entity.getWorld().isClient() || entity.isRemoved()) {
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

        PortalLocation destination = pair.get(enteredColor.other());
        if (destination == null || !destination.worldKey().equals(fromWorld.getRegistryKey())) {
            return;
        }

        ServerWorld targetWorld = fromWorld.getServer().getWorld(destination.worldKey());
        if (targetWorld == null) {
            return;
        }

        if (!targetWorld.getBlockState(destination.firstPos()).isOf(enteredColor.other().block())) {
            return;
        }

        Vec3d center = Vec3d.ofCenter(destination.firstPos()).add(Vec3d.ofCenter(destination.secondPos())).multiply(0.5);
        Vec3d forward = Vec3d.of(destination.facing().getVector());
        double exitOffset = 0.72;
        double x = center.x + forward.x * exitOffset;
        double y = center.y - 0.35 + forward.y * exitOffset;
        double z = center.z + forward.z * exitOffset;
        float yaw = destination.facing().getAxis().isVertical() ? entity.getYaw() : destination.facing().asRotation();
        float pitch = entity.getPitch();

        if (entity instanceof ServerPlayerEntity player) {
            player.networkHandler.requestTeleport(x, y, z, yaw, pitch);
            player.setBodyYaw(yaw);
            player.setHeadYaw(yaw);
        } else {
            entity.requestTeleport(x, y, z);
            entity.setYaw(yaw);
            if (entity instanceof LivingEntity livingEntity) {
                livingEntity.setBodyYaw(yaw);
                livingEntity.setHeadYaw(yaw);
            }
        }

        entity.setVelocity(forward.x * 0.18, Math.max(entity.getVelocity().y, 0.02), forward.z * 0.18);
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
                if (parts.length != 8) {
                    continue;
                }

                UUID playerId = UUID.fromString(parts[0]);
                PortalColor color = PortalColor.valueOf(parts[1]);
                Identifier worldId = Identifier.of(parts[2]);
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4]);
                int z = Integer.parseInt(parts[5]);
                Direction facing = Direction.byName(parts[6]);
                if (facing == null) {
                    facing = Direction.NORTH;
                }

                RegistryKey<World> worldKey = RegistryKey.of(RegistryKeys.WORLD, worldId);
                BlockPos firstPos = new BlockPos(x, y, z);
                BlockPos secondPos = switch (facing.getAxis()) {
                    case Y -> firstPos.offset(Direction.SOUTH);
                    case X, Z -> firstPos.up();
                };
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
            lines.add("# playerUuid|color|world|x|y|z|facing|v1");

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
                        + location.facing().asString() + "|v1");
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
}
