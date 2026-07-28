package io.github.togar2.pvp.collision;

import net.minestom.server.collision.Shape;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;

/**
 * The result of a physics simulation.
 *
 * @param newPosition             the new position of the entity
 * @param newVelocity             the new velocity of the entity
 * @param isOnGround              if the entity is on the ground
 * @param collisionX              if the entity collided on the X axis
 * @param collisionY              if the entity collided on the Y axis
 * @param collisionZ              if the entity collided on the Z axis
 * @param originalDelta           the velocity delta of the entity
 * @param collisionPoints         the points where the entity collided
 * @param collisionShapes         the shapes the entity collided with
 * @param collisionShapePositions the positions of the shapes the entity collided with
 * @param hasCollision            if the entity collided
 * @param sweepResult             sweep result of the collision
 * @param cached                  if the result was due to quickly exiting
 */
public record PhysicsResult(
		Pos newPosition,
		Vec newVelocity,
		boolean isOnGround,
		boolean collisionX,
		boolean collisionY,
		boolean collisionZ,
		Vec originalDelta,
		Point[] collisionPoints,
		Shape[] collisionShapes,
		Point[] collisionShapePositions,
		boolean hasCollision,
		SweepResult sweepResult,
		boolean cached
) {
	public PhysicsResult(Pos newPosition, Vec newVelocity, boolean isOnGround,
	                     boolean collisionX, boolean collisionY, boolean collisionZ,
	                     Vec originalDelta, Point[] collisionPoints, Shape[] collisionShapes,
	                     Point[] collisionShapePositions, boolean hasCollision, SweepResult sweepResult) {
		this(newPosition, newVelocity, isOnGround, collisionX, collisionY, collisionZ, originalDelta,
				collisionPoints, collisionShapes, collisionShapePositions, hasCollision, sweepResult, false);
	}
}
