package io.github.togar2.pvp.utils;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.CollisionUtils;
import net.minestom.server.collision.EntityCollisionResult;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.WorldBorder;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Function;

/**
 * The single point in this library which depends on {@link CollisionUtils}.
 * <p>
 * {@link CollisionUtils} is annotated {@code @ApiStatus.Internal} and its signatures change
 * between Minestom releases. Minestom exposes no supported replacement: {@code SweepResult}
 * declares no accessors, {@code ShapeImpl.ShapeData} is package private, and
 * {@code BlockCollision}, {@code EntityCollision} and {@code RayUtils} are package private, so
 * block collision cannot be reimplemented against public API. Routing every call through this
 * class keeps an upstream break to a single file.
 */
public final class CollisionUtil {
	private CollisionUtil() {}

	public static PhysicsResult handlePhysics(Block.Getter blockGetter, BoundingBox boundingBox,
	                                          Pos position, Vec velocity,
	                                          @Nullable PhysicsResult previousPhysicsResult,
	                                          boolean singleCollision) {
		return CollisionUtils.handlePhysics(blockGetter, boundingBox, position, velocity,
				previousPhysicsResult, singleCollision);
	}

	public static PhysicsResult blocklessCollision(Pos position, Vec velocity) {
		return CollisionUtils.blocklessCollision(position, velocity);
	}

	public static List<EntityCollisionResult> checkEntityCollisions(Instance instance, BoundingBox boundingBox,
	                                                                Point position, Vec velocity, double extendRadius,
	                                                                Function<Entity, Boolean> entityFilter,
	                                                                @Nullable PhysicsResult physicsResult) {
		return CollisionUtils.checkEntityCollisions(instance, boundingBox, position, velocity,
				extendRadius, entityFilter, physicsResult);
	}

	public static boolean hasLineOfSight(Instance instance, Point start, Point end) {
		var result = CollisionUtils.handlePhysics(instance, null, new BoundingBox(0.0, 0.0, 0.0),
				start.asPos(), end.sub(start).asVec(), null, false);

		return result.newPosition().samePoint(end, 1.0E-5);
	}

	public static Pos applyWorldBorder(WorldBorder worldBorder, Pos currentPosition, Pos newPosition) {
		var radius = worldBorder.diameter() / 2.0;
		var collisionX = newPosition.x() > worldBorder.centerX() + radius
				|| newPosition.x() < worldBorder.centerX() - radius;
		var collisionZ = newPosition.z() > worldBorder.centerZ() + radius
				|| newPosition.z() < worldBorder.centerZ() - radius;

		if (!collisionX && !collisionZ) return newPosition;

		return newPosition.withCoord(
				collisionX ? currentPosition.x() : newPosition.x(),
				newPosition.y(),
				collisionZ ? currentPosition.z() : newPosition.z()
		);
	}
}
