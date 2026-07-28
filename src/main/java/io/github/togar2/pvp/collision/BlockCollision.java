package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.Shape;
import net.minestom.server.collision.ShapeImpl;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BlockCollision {
	public static final Point[] NO_COLLISION_POINTS = new Point[3];
	public static final Shape[] NO_COLLISION_SHAPES = new Shape[3];
	public static final Point[] NO_COLLISION_SHAPE_POSITIONS = new Point[3];

	private BlockCollision() {}

	/**
	 * Moves a bounding box with physics applied, checking against blocks.
	 * <p>
	 * Works by getting all the full blocks the bounding box could interact with. All bounding boxes
	 * inside those full blocks are checked for collisions.
	 */
	public static PhysicsResult handlePhysics(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                          Block.Getter blockGetter,
	                                          @Nullable PhysicsResult lastPhysicsResult,
	                                          boolean singleCollision) {
		if (velocity.isZero()) {
			return new PhysicsResult(entityPosition, Vec.ZERO, false, false, false, false,
					velocity, NO_COLLISION_POINTS, NO_COLLISION_SHAPES, NO_COLLISION_SHAPE_POSITIONS,
					false, SweepResult.NO_COLLISION);
		}

		final var cachedResult = cachedPhysics(velocity, entityPosition, blockGetter, lastPhysicsResult);

		if (cachedResult != null) return cachedResult;

		return stepPhysics(boundingBox, velocity, entityPosition, blockGetter, singleCollision);
	}

	/**
	 * Simulates collision physics as if the world had no blocks.
	 */
	public static PhysicsResult blocklessCollision(Pos entityPosition, Vec entityVelocity) {
		return new PhysicsResult(entityPosition.add(entityVelocity), entityVelocity, false,
				false, false, false, entityVelocity, NO_COLLISION_POINTS, NO_COLLISION_SHAPES,
				NO_COLLISION_SHAPE_POSITIONS, false, SweepResult.NO_COLLISION);
	}

	static boolean intersectShapeSwept(Shape shape, Point rayStart, Point rayDirection, Point shapePosition,
	                                   BoundingBox movingBoundingBox, SweepResult finalResult) {
		var hitBlock = false;

		for (var blockSection : boundingBoxes(shape)) {
			if (RayUtil.checkIntersection(movingBoundingBox, rayStart, rayDirection,
					blockSection, shapePosition, finalResult)) {
				finalResult.collidedPositionX = rayStart.x() + rayDirection.x() * finalResult.result;
				finalResult.collidedPositionY = rayStart.y() + rayDirection.y() * finalResult.result;
				finalResult.collidedPositionZ = rayStart.z() + rayDirection.z() * finalResult.result;
				finalResult.collidedShapeX = shapePosition.x();
				finalResult.collidedShapeY = shapePosition.y();
				finalResult.collidedShapeZ = shapePosition.z();
				finalResult.collidedShape = shape;
				hitBlock = true;
			}
		}

		return hitBlock;
	}

	private static List<BoundingBox> boundingBoxes(Shape shape) {
		if (shape instanceof ShapeImpl shapeImpl) return shapeImpl.boundingBoxes();
		if (shape instanceof BoundingBox boundingBox) return List.of(boundingBox);

		return List.of();
	}

	private static @Nullable PhysicsResult cachedPhysics(Vec velocity, Pos entityPosition,
	                                                     Block.Getter blockGetter,
	                                                     @Nullable PhysicsResult lastPhysicsResult) {
		if (lastPhysicsResult != null && lastPhysicsResult.collisionShapes()[1] instanceof ShapeImpl shape) {
			var currentBlock = blockGetter.getBlock(lastPhysicsResult.collisionShapePositions()[1],
					Block.Getter.Condition.TYPE);
			var lastBlockBoxes = shape.boundingBoxes();
			var currentBlockBoxes = boundingBoxes(currentBlock.collisionShape());

			if (lastPhysicsResult.collisionY()
					&& velocity.y() == lastPhysicsResult.originalDelta().y()
					&& currentBlockBoxes.equals(lastBlockBoxes)
					&& velocity.x() == 0 && velocity.z() == 0
					&& entityPosition.samePoint(lastPhysicsResult.newPosition())
					&& !lastBlockBoxes.isEmpty()) {
				if (lastPhysicsResult.cached()) return lastPhysicsResult;

				return new PhysicsResult(lastPhysicsResult.newPosition(), lastPhysicsResult.newVelocity(),
						lastPhysicsResult.isOnGround(), lastPhysicsResult.collisionX(), lastPhysicsResult.collisionY(),
						lastPhysicsResult.collisionZ(), lastPhysicsResult.originalDelta(), lastPhysicsResult.collisionPoints(),
						lastPhysicsResult.collisionShapes(), lastPhysicsResult.collisionShapePositions(),
						lastPhysicsResult.hasCollision(), lastPhysicsResult.sweepResult(), true);
			}
		}

		return null;
	}

	private static PhysicsResult stepPhysics(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                         Block.Getter blockGetter, boolean singleCollision) {
		final var finalResult = new SweepResult(1 - Point.EPSILON, 0, 0, 0, null, 0, 0, 0, 0, 0, 0);

		var collidedPoints = NO_COLLISION_POINTS;
		var collisionShapes = NO_COLLISION_SHAPES;
		var collisionShapePositions = NO_COLLISION_SHAPE_POSITIONS;

		var position = entityPosition;
		var remaining = velocity;

		while (true) {
			sweepBlocks(boundingBox, remaining, position, blockGetter, finalResult);
			var deltaX = finalResult.result * remaining.x();
			var deltaY = finalResult.result * remaining.y();
			var deltaZ = finalResult.result * remaining.z();

			if (Math.abs(deltaX) < Point.EPSILON) deltaX = 0;
			if (Math.abs(deltaY) < Point.EPSILON) deltaY = 0;
			if (Math.abs(deltaZ) < Point.EPSILON) deltaZ = 0;

			position = position.add(deltaX, deltaY, deltaZ);

			final int axis;

			if (finalResult.normalX != 0) axis = 0;
			else if (finalResult.normalY != 0) axis = 1;
			else if (finalResult.normalZ != 0) axis = 2;
			else break;

			if (collisionShapes == NO_COLLISION_SHAPES) {
				collidedPoints = new Point[3];
				collisionShapes = new Shape[3];
				collisionShapePositions = new Point[3];
			}

			collisionShapes[axis] = finalResult.collidedShape;
			collisionShapePositions[axis] = new Vec(finalResult.collidedShapeX, finalResult.collidedShapeY, finalResult.collidedShapeZ);
			collidedPoints[axis] = new Vec(finalResult.collidedPositionX, finalResult.collidedPositionY, finalResult.collidedPositionZ);

			if (singleCollision || (collisionShapes[0] != null && collisionShapes[1] != null && collisionShapes[2] != null))
				break;

			remaining = new Vec(
					axis == 0 ? 0 : remaining.x() - deltaX,
					axis == 1 ? 0 : remaining.y() - deltaY,
					axis == 2 ? 0 : remaining.z() - deltaZ);

			if (remaining.isZero()) break;

			finalResult.normalX = 0;
			finalResult.normalY = 0;
			finalResult.normalZ = 0;
			finalResult.result = 1 - Point.EPSILON;
		}

		final var foundX = collisionShapes[0] != null;
		final var foundY = collisionShapes[1] != null;
		final var foundZ = collisionShapes[2] != null;
		final var anyCollision = foundX || foundY || foundZ;
		final var allCollision = foundX && foundY && foundZ;

		final Vec newDelta;

		if (!anyCollision) {
			newDelta = velocity;
		} else if (allCollision) {
			newDelta = Vec.ZERO;
		} else {
			newDelta = new Vec(foundX ? 0 : velocity.x(), foundY ? 0 : velocity.y(), foundZ ? 0 : velocity.z());
		}

		return new PhysicsResult(position, newDelta,
				foundY && velocity.y() < 0,
				foundX, foundY, foundZ,
				velocity, collidedPoints, collisionShapes, collisionShapePositions,
				anyCollision, finalResult);
	}

	private static void sweepBlocks(BoundingBox boundingBox, Vec velocity, Pos entityPosition,
	                                Block.Getter blockGetter, SweepResult finalResult) {
		final var startX = entityPosition.x();
		final var startY = entityPosition.y();
		final var startZ = entityPosition.z();
		final var endX = startX + velocity.x();
		final var endY = startY + velocity.y();
		final var endZ = startZ + velocity.z();

		final var minX = (int) Math.floor(Math.min(startX, endX) + boundingBox.minX());
		final var minY = (int) Math.floor(Math.min(startY, endY) + boundingBox.minY());
		final var minZ = (int) Math.floor(Math.min(startZ, endZ) + boundingBox.minZ());
		final var maxX = (int) Math.floor(Math.max(startX, endX) + boundingBox.maxX());
		final var maxY = (int) Math.floor(Math.max(startY, endY) + boundingBox.maxY());
		final var maxZ = (int) Math.floor(Math.max(startZ, endZ) + boundingBox.maxZ());

		final var stepX = velocity.x() < 0 ? -1 : 1;
		final var stepY = velocity.y() < 0 ? -1 : 1;
		final var stepZ = velocity.z() < 0 ? -1 : 1;
		final var firstX = stepX > 0 ? minX : maxX;
		final var lastX = stepX > 0 ? maxX : minX;
		final var firstY = stepY > 0 ? minY : maxY;
		final var lastY = stepY > 0 ? maxY : minY;
		final var firstZ = stepZ > 0 ? minZ : maxZ;
		final var lastZ = stepZ > 0 ? maxZ : minZ;

		for (var blockX = firstX; blockX != lastX + stepX; blockX += stepX) {
			for (var blockY = firstY; blockY != lastY + stepY; blockY += stepY) {
				for (var blockZ = firstZ; blockZ != lastZ + stepZ; blockZ += stepZ) {
					checkBoundingBox(blockX, blockY, blockZ, velocity, entityPosition,
							boundingBox, blockGetter, finalResult);
				}
			}
		}
	}

	private static boolean checkBoundingBox(int blockX, int blockY, int blockZ,
	                                        Vec entityVelocity, Pos entityPosition, BoundingBox boundingBox,
	                                        Block.Getter blockGetter, SweepResult finalResult) {
		final var currentBlock = blockGetter.getBlock(blockX, blockY, blockZ, Block.Getter.Condition.TYPE);
		final var currentShape = currentBlock.collisionShape();

		final var currentCollidable = !currentShape.relativeEnd().isZero();
		final var currentShort = currentShape.relativeEnd().y() < 0.5;

		if (currentShort && shouldCheckLower(entityVelocity, entityPosition, blockX, blockY, blockZ)) {
			final var belowPosition = new Vec(blockX, blockY - 1, blockZ);
			final var belowBlock = blockGetter.getBlock(belowPosition, Block.Getter.Condition.TYPE);
			final var belowShape = belowBlock.collisionShape();

			final var currentPosition = new Vec(blockX, blockY, blockZ);

			if (belowShape.relativeEnd().y() > 1) {
				final var belowHit = intersectShapeSwept(belowShape, entityPosition, entityVelocity,
						belowPosition, boundingBox, finalResult);
				final var currentHit = currentCollidable && intersectShapeSwept(currentShape, entityPosition,
						entityVelocity, currentPosition, boundingBox, finalResult);

				return belowHit || currentHit;
			} else {
				return currentCollidable && intersectShapeSwept(currentShape, entityPosition, entityVelocity,
						currentPosition, boundingBox, finalResult);
			}
		}

		if (currentCollidable && intersectShapeSwept(currentShape, entityPosition, entityVelocity,
				new Vec(blockX, blockY, blockZ), boundingBox, finalResult)) {
			if (currentShort) {
				final var belowPosition = new Vec(blockX, blockY - 1, blockZ);
				final var belowBlock = blockGetter.getBlock(belowPosition, Block.Getter.Condition.TYPE);
				final var belowShape = belowBlock.collisionShape();

				if (belowShape.relativeEnd().y() > 1)
					intersectShapeSwept(belowShape, entityPosition, entityVelocity,
							belowPosition, boundingBox, finalResult);
			}

			return true;
		}

		return false;
	}

	private static boolean shouldCheckLower(Vec entityVelocity, Pos entityPosition,
	                                        int blockX, int blockY, int blockZ) {
		final var yVelocity = entityVelocity.y();

		if (yVelocity == 0) return Math.floor(entityPosition.y()) == blockY;

		final var xVelocity = entityVelocity.x();
		final var zVelocity = entityVelocity.z();

		if (xVelocity == 0 && zVelocity == 0)
			return yVelocity < 0 && blockY == Math.floor(entityPosition.y() + yVelocity);

		final var underYX = xVelocity != 0
				&& computeHeight(yVelocity, xVelocity, entityPosition.y(), entityPosition.x(), blockX) >= blockY;
		final var underYZ = zVelocity != 0
				&& computeHeight(yVelocity, zVelocity, entityPosition.y(), entityPosition.z(), blockZ) >= blockY;

		return underYX && underYZ;
	}

	private static double computeHeight(double yVelocity, double velocity,
	                                    double entityY, double position, int blockPosition) {
		final var slope = yVelocity / velocity;

		return slope * (blockPosition - position + (slope > 0 ? 1 : 0)) + entityY;
	}
}
