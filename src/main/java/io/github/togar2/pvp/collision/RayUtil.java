package io.github.togar2.pvp.collision;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;

public final class RayUtil {
	private RayUtil() {}

	/**
	 * Checks whether a moving bounding box intersects a static bounding box, writing the
	 * intersection details to {@code finalResult} when the hit is nearer than the one it already
	 * holds.
	 *
	 * @param movingBoundingBox the bounding box being moved
	 * @param rayStart          the position the movement starts at
	 * @param rayDirection      the movement vector
	 * @param staticBoundingBox the bounding box being checked against
	 * @param staticOffset      the world position of the static bounding box
	 * @param finalResult       the sweep result to write the intersection details to
	 * @return true if an intersection nearer than the current result was found
	 */
	public static boolean checkIntersection(BoundingBox movingBoundingBox, Point rayStart, Point rayDirection,
	                                        BoundingBox staticBoundingBox, Point staticOffset,
	                                        SweepResult finalResult) {
		final var halfWidth = movingBoundingBox.width() / 2;
		final var halfHeight = movingBoundingBox.height() / 2;
		final var halfDepth = movingBoundingBox.depth() / 2;

		final var rayCenterX = rayStart.x() + movingBoundingBox.minX() + halfWidth;
		final var rayCenterY = rayStart.y() + movingBoundingBox.minY() + halfHeight;
		final var rayCenterZ = rayStart.z() + movingBoundingBox.minZ() + halfDepth;

		final var rayDirectionX = rayDirection.x();
		final var rayDirectionY = rayDirection.y();
		final var rayDirectionZ = rayDirection.z();

		final var staticMinX = staticBoundingBox.minX() + staticOffset.x();
		final var staticMinY = staticBoundingBox.minY() + staticOffset.y();
		final var staticMinZ = staticBoundingBox.minZ() + staticOffset.z();
		final var staticMaxX = staticBoundingBox.maxX() + staticOffset.x();
		final var staticMaxY = staticBoundingBox.maxY() + staticOffset.y();
		final var staticMaxZ = staticBoundingBox.maxZ() + staticOffset.z();

		final var expandedMinX = staticMinX - rayCenterX - halfWidth;
		final var expandedMinY = staticMinY - rayCenterY - halfHeight;
		final var expandedMinZ = staticMinZ - rayCenterZ - halfDepth;
		final var expandedMaxX = staticMaxX - rayCenterX + halfWidth;
		final var expandedMaxY = staticMaxY - rayCenterY + halfHeight;
		final var expandedMaxZ = staticMaxZ - rayCenterZ + halfDepth;

		final var signumRayX = Math.signum(rayDirectionX);
		final var signumRayY = Math.signum(rayDirectionY);
		final var signumRayZ = Math.signum(rayDirectionZ);

		var isHit = false;
		var percentage = Double.MAX_VALUE;
		var collisionFace = -1;

		if (rayDirectionX > 0) {
			var factor = epsilon(expandedMinX / rayDirectionX);

			if (factor < percentage) {
				var intersectionY = rayDirectionY * factor + rayCenterY;
				var intersectionZ = rayDirectionZ * factor + rayCenterZ;

				if (((intersectionY - rayCenterY) * signumRayY) >= 0
						&& ((intersectionZ - rayCenterZ) * signumRayZ) >= 0
						&& intersectionY >= staticMinY - halfHeight
						&& intersectionY <= staticMaxY + halfHeight
						&& intersectionZ >= staticMinZ - halfDepth
						&& intersectionZ <= staticMaxZ + halfDepth) {
					isHit = true;
					percentage = factor;
					collisionFace = 0;
				}
			}
		}

		if (rayDirectionX < 0) {
			var factor = epsilon(expandedMaxX / rayDirectionX);

			if (factor < percentage) {
				var intersectionY = rayDirectionY * factor + rayCenterY;
				var intersectionZ = rayDirectionZ * factor + rayCenterZ;

				if (((intersectionY - rayCenterY) * signumRayY) >= 0
						&& ((intersectionZ - rayCenterZ) * signumRayZ) >= 0
						&& intersectionY >= staticMinY - halfHeight
						&& intersectionY <= staticMaxY + halfHeight
						&& intersectionZ >= staticMinZ - halfDepth
						&& intersectionZ <= staticMaxZ + halfDepth) {
					isHit = true;
					percentage = factor;
					collisionFace = 0;
				}
			}
		}

		if (rayDirectionZ > 0) {
			var factor = epsilon(expandedMinZ / rayDirectionZ);

			if (factor < percentage) {
				var intersectionX = rayDirectionX * factor + rayCenterX;
				var intersectionY = rayDirectionY * factor + rayCenterY;

				if (((intersectionY - rayCenterY) * signumRayY) >= 0
						&& ((intersectionX - rayCenterX) * signumRayX) >= 0
						&& intersectionX >= staticMinX - halfWidth
						&& intersectionX <= staticMaxX + halfWidth
						&& intersectionY >= staticMinY - halfHeight
						&& intersectionY <= staticMaxY + halfHeight) {
					isHit = true;
					percentage = factor;
					collisionFace = 1;
				}
			}
		}

		if (rayDirectionZ < 0) {
			var factor = epsilon(expandedMaxZ / rayDirectionZ);

			if (factor < percentage) {
				var intersectionX = rayDirectionX * factor + rayCenterX;
				var intersectionY = rayDirectionY * factor + rayCenterY;

				if (((intersectionY - rayCenterY) * signumRayY) >= 0
						&& ((intersectionX - rayCenterX) * signumRayX) >= 0
						&& intersectionX >= staticMinX - halfWidth
						&& intersectionX <= staticMaxX + halfWidth
						&& intersectionY >= staticMinY - halfHeight
						&& intersectionY <= staticMaxY + halfHeight) {
					isHit = true;
					percentage = factor;
					collisionFace = 1;
				}
			}
		}

		if (rayDirectionY > 0) {
			var factor = epsilon(expandedMinY / rayDirectionY);

			if (factor < percentage) {
				var intersectionX = rayDirectionX * factor + rayCenterX;
				var intersectionZ = rayDirectionZ * factor + rayCenterZ;

				if (((intersectionZ - rayCenterZ) * signumRayZ) >= 0
						&& ((intersectionX - rayCenterX) * signumRayX) >= 0
						&& intersectionX >= staticMinX - halfWidth
						&& intersectionX <= staticMaxX + halfWidth
						&& intersectionZ >= staticMinZ - halfDepth
						&& intersectionZ <= staticMaxZ + halfDepth) {
					isHit = true;
					percentage = factor;
					collisionFace = 2;
				}
			}
		}

		if (rayDirectionY < 0) {
			var factor = epsilon(expandedMaxY / rayDirectionY);

			if (factor < percentage) {
				var intersectionX = rayDirectionX * factor + rayCenterX;
				var intersectionZ = rayDirectionZ * factor + rayCenterZ;

				if (((intersectionZ - rayCenterZ) * signumRayZ) >= 0
						&& ((intersectionX - rayCenterX) * signumRayX) >= 0
						&& intersectionX >= staticMinX - halfWidth
						&& intersectionX <= staticMaxX + halfWidth
						&& intersectionZ >= staticMinZ - halfDepth
						&& intersectionZ <= staticMaxZ + halfDepth) {
					isHit = true;
					percentage = factor;
					collisionFace = 2;
				}
			}
		}

		percentage *= 0.99999;

		if (isHit && percentage >= 0 && percentage <= finalResult.result) {
			finalResult.result = percentage;
			finalResult.normalX = 0;
			finalResult.normalY = 0;
			finalResult.normalZ = 0;

			if (collisionFace == 0) finalResult.normalX = 1;
			if (collisionFace == 1) finalResult.normalZ = 1;
			if (collisionFace == 2) finalResult.normalY = 1;

			return true;
		}

		return false;
	}

	private static double epsilon(double value) {
		return Math.abs(value) < Point.EPSILON ? 0 : value;
	}
}
