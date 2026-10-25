package net.memezforbeanz.starminerodyssey.forge.shipspawn;

import net.memezforbeanz.starminerodyssey.StarminerAdditions;
import net.memezforbeanz.starminerodyssey.forge.compat.CosmosData;
import net.memezforbeanz.starminerodyssey.forge.compat.StarlanceCompat;
import net.memezforbeanz.starminerodyssey.shipspawn.ShipBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Valkyrien Skies backend for the ship spawner. */
public final class ForgeShipBridge implements ShipBridge {

    /** Starlance teleports ships within 16 blocks of a planet down to it; 256 is its outer warning range. */
    private static final double PLANET_MARGIN = 256.0;

    @Nullable
    private static Constructor<?> teleportData;
    @Nullable
    private static Method teleportShip;

    @Override
    public boolean canPlace(ServerLevel level, BoundingBox box) {
        CosmosData cosmos = CosmosData.get();
        return cosmos == null || !cosmos.isNearPlanet(level, AABB.of(box), PLANET_MARGIN);
    }

    @Override
    public boolean supportsShips() {
        return true;
    }

    @Override
    public long spawnAsShip(ServerLevel level, StructureTemplate template, StructurePlaceSettings settings,
                            BlockPos nearBlock, Vec3 templateZero, Quaterniondc rotation,
                            @Nullable Quaterniondc tilt, RandomSource random) {
        VsiServerShipWorld world = VSGameUtilsKt.getShipObjectWorld(level);
        String dimension = VSGameUtilsKt.getDimensionId(level);
        ServerShip ship = null;
        try {
            // Created and filled in the shipyard, not assembled from world blocks: Starlance
            // allows this anywhere, like its ship creator stick
            ship = world.createNewShipAtBlock(new Vector3i(nearBlock.getX(), nearBlock.getY(), nearBlock.getZ()), false, 1.0,
                    dimension);
            ship.setStatic(true);
            Vector3i claim = ship.getChunkClaim().getCenterBlockCoordinates(VSGameUtilsKt.getYRange(level), new Vector3i());
            BlockPos shipOrigin = new BlockPos(claim.x, claim.y, claim.z);
            template.placeInWorld(level, shipOrigin, shipOrigin, settings, random, Block.UPDATE_CLIENTS);
            if (ship.getInertiaData().getShipMass() <= 0) {
                world.deleteShip(ship);
                return -1;
            }
            // Shipyard point shipOrigin + x must land on templateZero + R*x. The ship maps s to
            // positionInWorld + R*(s - COM), so the centre of mass goes to
            // templateZero + R*(COM - shipOrigin). A tilt then turns it about that point.
            Vector3dc centerOfMass = ship.getInertiaData().getCenterOfMassInShip();
            Vector3d worldPos = new Vector3d(centerOfMass)
                    .sub(shipOrigin.getX(), shipOrigin.getY(), shipOrigin.getZ())
                    .rotate(rotation)
                    .add(templateZero.x, templateZero.y, templateZero.z);
            Quaterniond pose = tilt != null ? new Quaterniond(tilt).mul(rotation) : new Quaterniond(rotation);
            teleport(world, ship, worldPos, new Vector3d(centerOfMass), pose, new Vector3d(), new Vector3d(), dimension);
            // Before Starlance's next tick, or the player who triggered the spawn gets its planet menu
            StarlanceCompat.exemptFromPlanetMenu(ship);
            return ship.getId();
        } catch (Exception e) {
            StarminerAdditions.LOGGER.error("Ship spawner failed to spawn a ship at {}", nearBlock, e);
            if (ship != null) {
                world.deleteShip(ship);
            }
            return -1;
        }
    }

    @Nullable
    @Override
    public Vec3 shipPosition(ServerLevel level, long shipId) {
        ServerShip ship = ship(level, shipId);
        if (ship == null) {
            return null;
        }
        Vector3dc pos = ship.getTransform().getPositionInWorld();
        return new Vec3(pos.x(), pos.y(), pos.z());
    }

    @Override
    public boolean setStatic(ServerLevel level, long shipId, boolean isStatic) {
        ServerShip ship = ship(level, shipId);
        if (ship == null || ship.isStatic() == isStatic) {
            return false;
        }
        ship.setStatic(isStatic);
        return true;
    }

    @Override
    public void kick(ServerLevel level, long shipId, Vec3 velocity, Vec3 omega) {
        ServerShip ship = ship(level, shipId);
        if (ship == null) {
            return;
        }
        ShipTransform transform = ship.getTransform();
        try {
            // Same pose, new velocities
            teleport(VSGameUtilsKt.getShipObjectWorld(level), ship, transform.getPositionInWorld(),
                    transform.getPositionInShip(), transform.getShipToWorldRotation(),
                    new Vector3d(velocity.x, velocity.y, velocity.z), new Vector3d(omega.x, omega.y, omega.z),
                    VSGameUtilsKt.getDimensionId(level));
        } catch (Exception e) {
            StarminerAdditions.LOGGER.warn("Couldn't set the velocity of ship {}", shipId, e);
        }
    }

    @Override
    public void spin(ServerLevel level, long shipId, Vec3 axis, double radiansPerSecond) {
        ServerShip ship = ship(level, shipId);
        if (ship != null && !(ship.getTransformProvider() instanceof SpinTransformProvider)) {
            ship.setTransformProvider(new SpinTransformProvider(new Vector3d(axis.x, axis.y, axis.z).normalize(),
                    radiansPerSecond));
        }
    }

    @Override
    public void maintain(ServerLevel level, long shipId) {
        ServerShip ship = ship(level, shipId);
        if (ship != null) {
            StarlanceCompat.exemptFromPlanetMenu(ship);
        }
    }

    @Override
    public void deleteShip(ServerLevel level, long shipId) {
        ServerShip ship = ship(level, shipId);
        if (ship != null) {
            // Clear its shipyard blocks too (no drops), so nothing is left behind there
            ShipAssembler.INSTANCE.deleteShip(level, ship, true, false);
        }
    }

    @Override
    public long shipIdAt(ServerLevel level, BlockPos pos) {
        if (!VSGameUtilsKt.isBlockInShipyard(level, pos)) {
            return -1;
        }
        ServerShip ship = VSGameUtilsKt.getShipManagingPos(level, pos);
        return ship == null ? -1 : ship.getId();
    }

    /**
     * VS core moved {@code ShipTeleportData} from {@code core.internal} (VS2 2.4.11) to
     * {@code core.api} (2.4.13), so a direct call only links against one of them. Resolve
     * the constructor and {@code teleportShip} overload at runtime instead.
     */
    private static void teleport(VsiServerShipWorld world, ServerShip ship, Vector3dc position, Vector3dc positionInShip,
                                 Quaterniondc rotation, Vector3dc velocity, Vector3dc omega, String dimension)
            throws ReflectiveOperationException {
        if (teleportData == null) {
            teleportData = Class.forName("org.valkyrienskies.core.impl.game.ShipTeleportDataImpl").getConstructor(
                    Vector3dc.class, Quaterniondc.class, Vector3dc.class, Vector3dc.class, String.class, Double.class,
                    Vector3dc.class);
        }
        Object data = teleportData.newInstance(position, rotation, velocity, omega, dimension, 1.0, positionInShip);
        if (teleportShip == null) {
            for (Method method : world.getClass().getMethods()) {
                if (method.getName().equals("teleportShip") && method.getParameterCount() == 2
                        && method.getParameterTypes()[1].isInstance(data)) {
                    teleportShip = method;
                    break;
                }
            }
            if (teleportShip == null) {
                throw new NoSuchMethodException("teleportShip(ServerShip, ShipTeleportData)");
            }
        }
        teleportShip.invoke(world, ship, data);
    }

    /** The ship, if it exists and is still in this level (Starlance can carry ships off). */
    @Nullable
    private static ServerShip ship(ServerLevel level, long shipId) {
        // getShipById searches every ship, loaded or not, so an unloaded ship isn't dropped
        Ship ship = ValkyrienSkies.getShipById(level, shipId);
        return ship instanceof ServerShip serverShip
                && VSGameUtilsKt.getDimensionId(level).equals(serverShip.getChunkClaimDimension())
                ? serverShip : null;
    }
}
