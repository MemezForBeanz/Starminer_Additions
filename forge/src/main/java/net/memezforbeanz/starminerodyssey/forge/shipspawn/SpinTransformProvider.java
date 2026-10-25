package net.memezforbeanz.starminerodyssey.forge.shipspawn;

import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShipTransformProvider;
import org.valkyrienskies.core.api.ships.properties.ShipTransform;

/**
 * Spins a ship about a fixed world axis through its centre of mass by handing VS2 the
 * next pose directly, so the ship is never integrated as a rigid body. Reporting the spin
 * as the ship's angular velocity keeps entity dragging and client interpolation smooth.
 * <p>
 * Called on the physics thread, so the step is measured on a real clock rather than the
 * game tick.
 */
final class SpinTransformProvider implements ServerShipTransformProvider {

    /** Longest step taken at once, so a stall doesn't snap the ship round. */
    private static final double MAX_STEP_SECONDS = 0.1;

    private final Vector3dc axis;
    private final double radiansPerSecond;
    private final Vector3dc omega;
    private long lastNanos;

    SpinTransformProvider(Vector3dc axis, double radiansPerSecond) {
        this.axis = axis;
        this.radiansPerSecond = radiansPerSecond;
        this.omega = new Vector3d(axis).mul(radiansPerSecond);
    }

    @Override
    @SuppressWarnings("deprecation") // ShipTransform.copy: still the one transform factory in both VS2 2.4.11 and 2.4.13
    public NextTransformAndVelocityData provideNextTransformAndVelocity(ShipTransform previous, ShipTransform current) {
        long now = System.nanoTime();
        double step = lastNanos == 0 ? 0 : Math.min((now - lastNanos) / 1e9, MAX_STEP_SECONDS);
        lastNanos = now;
        Quaterniond rotation = new Quaterniond()
                .fromAxisAngleRad(axis.x(), axis.y(), axis.z(), radiansPerSecond * step)
                .mul(current.getShipToWorldRotation())
                .normalize();
        ShipTransform next = current.copy(current.getPositionInWorld(), current.getPositionInShip(), rotation,
                current.getShipToWorldScaling());
        return new NextTransformAndVelocityData(next, new Vector3d(), omega);
    }
}
