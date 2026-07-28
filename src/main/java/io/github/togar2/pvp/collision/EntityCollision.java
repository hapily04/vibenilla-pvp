package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.EntityTracker;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class EntityCollision {
	private EntityCollision() {}

	public static List<EntityCollisionResult> checkCollision(EntityTracker entityTracker,
	                                                         BoundingBox boundingBox, Point point, Vec entityVelocity,
	                                                         double extendRadius, Function<Entity, Boolean> entityFilter,
	                                                         @Nullable PhysicsResult physicsResult) {
		var minimumResult = physicsResult != null ? physicsResult.sweepResult().result() : Double.MAX_VALUE;
		var result = new ArrayList<EntityCollisionResult>();

		var maxDistance = Math.pow(boundingBox.height() * boundingBox.height()
				+ boundingBox.depth() / 2 * boundingBox.depth() / 2
				+ boundingBox.width() / 2 * boundingBox.width() / 2, 1 / 3.0);
		var projectileDistance = entityVelocity.length();

		entityTracker.nearbyEntities(point, extendRadius + maxDistance + projectileDistance,
				EntityTracker.Target.ENTITIES, entity -> {
					if (!entityFilter.apply(entity)) return;
					if (!entity.hasEntityCollision()) return;

					if (entity.getBoundingBox().intersectBox(entity.getPosition().sub(point), boundingBox)) {
						result.add(new EntityCollisionResult(point.asPos(), entity, Vec.ZERO, 0));
						return;
					}

					var sweepResult = new SweepResult(minimumResult, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);
					var intersected = BlockCollision.intersectShapeSwept(entity.getBoundingBox(), point,
							entityVelocity, entity.getPosition(), boundingBox, sweepResult);

					if (intersected && sweepResult.result() < 1) {
						var collisionPoint = point.asPos().add(entityVelocity.mul(sweepResult.result()));
						var direction = new Vec(sweepResult.collidedPositionX,
								sweepResult.collidedPositionY, sweepResult.collidedPositionZ);
						result.add(new EntityCollisionResult(collisionPoint, entity, direction, sweepResult.result()));
					}
				});

		return result;
	}
}
