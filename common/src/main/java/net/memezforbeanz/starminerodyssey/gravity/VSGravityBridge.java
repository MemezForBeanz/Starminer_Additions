package net.memezforbeanz.starminerodyssey.gravity;

import net.memezforbeanz.gravityapi.EntityTags;
import net.memezforbeanz.gravityapi.api.GravityChangerAPI;
import net.memezforbeanz.gravityapi.api.GravityDirection;
import net.memezforbeanz.gravityapi.api.GravityFields;
import net.memezforbeanz.gravityapi.api.RotationParameters;
import net.memezforbeanz.gravityapi.compat.vs2.VS2Helper;
import net.memezforbeanz.gravityapi.compat.vs2.VS2Integration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.DoubleSupplier;
import java.util.function.Function;

/**
 * The one class that links against VS-Gravity (gravityapi), on both loaders: it compiles against
 * gravityapi-common and runs on the Forge or Fabric build of VS-Gravity.
 * <p>
 * Pulls are applied via {@code applyGravityDirectionEffect} on the entity's gravity
 * capability: called from a block entity tick it delay-applies at the entity's next
 * capability update, competes on priority, and resets automatically once out of the
 * field — full physics (rotated hitbox) for cardinal pulls, arbitrary-gravity handling
 * for ship-rotated vectors.
 */
public final class VSGravityBridge implements GravityBridge {

    @Override
    public boolean canChangeGravity(Entity entity) {
        return EntityTags.canChangeGravity(entity);
    }

    @Override
    public void applyCardinalPull(Entity entity, Direction pull, double priority) {
        GravityChangerAPI.getGravityComponent(entity)
                .applyGravityDirectionEffect(GravityDirection.fromDirection(pull), RotationParameters.getDefault(), priority);
    }

    @Override
    public void applyFieldPull(Entity entity, Vec3 pull, @Nullable Vec3 landingFace, boolean nearSurface,
                               double priority) {
        GravityChangerAPI.applyFieldGravityEffect(entity, pull, landingFace, nearSurface, priority);
    }

    @Override
    public void applyShipFieldPull(Entity entity, Vec3 pull, @Nullable Vec3 landingFace, boolean nearSurface,
                                   double priority) {
        GravityChangerAPI.applyShipFieldGravityEffect(entity, pull, landingFace, nearSurface, priority);
    }

    @Override
    public void applyVectorPull(Entity entity, Vec3 worldPull, double priority) {
        GravityChangerAPI.getGravityComponent(entity)
                .applyGravityDirectionEffect(GravityDirection.fromVector(worldPull), RotationParameters.getDefault(), priority);
    }

    @Override
    public void applyShipVectorPull(Entity entity, Vec3 worldPull, double priority) {
        // shipRelative never snaps to cardinal: cardinal physics can't see ship blocks, so on an axis-aligned ship the entity would fall through
        GravityChangerAPI.getGravityComponent(entity)
                .applyGravityDirectionEffect(GravityDirection.shipRelative(worldPull), RotationParameters.getDefault(), priority);
    }

    @Override
    public void registerGravitySampler(Level level, Object key, Function<Vec3, Vec3> sampler, DoubleSupplier priority) {
        GravityFields.register(level, key, sampler::apply, priority);
    }

    @Override
    public void unregisterGravitySampler(Level level, Object key) {
        GravityFields.unregister(level, key);
    }

    @Override
    public void settleGravity(Entity entity) {
        GravityChangerAPI.settleGravityNow(entity);
    }

    @Override
    public Vec3 gravityDown(Entity entity) {
        return GravityChangerAPI.getGravityDirectionVec(entity).getVector();
    }

    @Override
    public void applyStrengthOverride(Entity entity, double strength, double priority) {
        // VS-Gravity stands Ad Astra / Cosmic Horizons planet gravity down while it holds
        GravityChangerAPI.applyGravityStrengthOverride(entity, strength, priority);
    }

    @Override
    public AABB gravityEffectCollider(Entity entity, @Nullable Vec3 pullWorldDir, boolean onShip) {
        return GravityChangerAPI.getGravityEffectCollider(entity, pullWorldDir, onShip);
    }

    @Nullable
    @Override
    public AABB worldBoxToShipBox(Level level, BlockPos blockOnShip, AABB worldBox) {
        return VS2Helper.worldBoxToShipBox(level, blockOnShip, worldBox);
    }

    @Override
    public boolean isOnShip(Level level, BlockPos pos) {
        return VS2Integration.isVS2Loaded() && VS2Helper.getShipIdAtBlock(level, pos) != null;
    }

    @Nullable
    @Override
    public Vec3 shipToWorldPosition(Level level, BlockPos blockOnShip, Vec3 shipPos) {
        return VS2Helper.shipToWorldPosition(level, blockOnShip, shipPos);
    }

    @Nullable
    @Override
    public Vec3 worldToShipPosition(Level level, BlockPos blockOnShip, Vec3 worldPos) {
        return VS2Helper.worldToShipPosition(level, blockOnShip, worldPos);
    }

    @Nullable
    @Override
    public Vec3 shipToWorldDirection(Level level, BlockPos blockOnShip, Vec3 shipDir) {
        return VS2Helper.shipToWorldDirectionFromBlock(level, blockOnShip, shipDir);
    }

    @Nullable
    @Override
    public Vec3 worldToShipDirection(Level level, BlockPos blockOnShip, Vec3 worldDir) {
        return VS2Helper.worldToShipDirectionFromBlock(level, blockOnShip, worldDir);
    }
}
