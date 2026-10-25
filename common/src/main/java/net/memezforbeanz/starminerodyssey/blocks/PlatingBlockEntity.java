package net.memezforbeanz.starminerodyssey.blocks;

import net.memezforbeanz.starminerodyssey.gravity.GravityBridge;
import net.memezforbeanz.starminerodyssey.gravity.PlanetGravityField;
import net.memezforbeanz.starminerodyssey.registry.ModBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gravity plating ticker: each mounted face projects a thin field onto the plate. The tiny volume gives plates top field priority (smallest field wins). */
public class PlatingBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity {

    public PlatingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.PLATING.get(), pos, state);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof PlatingBlock plating)) {
            return;
        }
        // Server drives all entities; the client only predicts players (matching the
        // field generators' split).
        Class<? extends Entity> type = level.isClientSide() ? Player.class : Entity.class;
        GravityBridge bridge = GravityBridge.get();
        boolean onShip = bridge.isOnShip(level, pos);
        double priority = PlanetGravityField.fieldPriority(plating.gravityEffectHeight);
        for (Direction plateDirection : PlatingBlock.getDirections(state)) {
            AABB localBox = plating.getGravityEffectBox(pos, plateDirection);
            applyPlateGravity(level, bridge, localBox, plateDirection, priority, onShip, type);
        }
    }

    private void applyPlateGravity(Level level, GravityBridge bridge, AABB localBox, Direction pull,
                                   double priority, boolean onShip, Class<? extends Entity> type) {
        Vec3 worldCenter = localBox.getCenter();
        if (onShip) {
            worldCenter = bridge.shipToWorldPosition(level, worldPosition, worldCenter);
            if (worldCenter == null) {
                return;
            }
        }
        // Plates reach at most ~1.3 blocks out of a 1-block cube; a generous fixed
        // search reach covers the rotated (on-ship) case too.
        AABB searchBox = AABB.ofSize(worldCenter, 0, 0, 0).inflate(2.5);
        for (Entity entity : level.getEntitiesOfClass(type, searchBox)) {
            if (!bridge.canChangeGravity(entity)) {
                continue;
            }
            AABB entityBox = entity.getBoundingBox();
            if (onShip) {
                Vec3 shipSpacePos = bridge.worldToShipPosition(level, worldPosition, entity.position());
                if (shipSpacePos == null) {
                    continue;
                }
                entityBox = entityBox.move(shipSpacePos.subtract(entity.position()));
            }
            if (!localBox.intersects(entityBox)) {
                continue;
            }
            if (onShip) {
                Vec3 worldDir = bridge.shipToWorldDirection(level, worldPosition, Vec3.atLowerCornerOf(pull.getNormal()));
                if (worldDir != null) {
                    bridge.applyVectorPull(entity, worldDir, priority);
                }
            } else {
                bridge.applyCardinalPull(entity, pull, priority);
            }
        }
    }
}
