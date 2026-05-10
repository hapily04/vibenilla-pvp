package io.github.togar2.pvp.utils;

import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

import java.util.ArrayList;
import java.util.List;

public class FluidUtil {
	public static int getLevel(Block block) {
		String levelStr = block.getProperty("level");
		if (levelStr == null) return 8;
		int level = Integer.parseInt(levelStr);
		if (level >= 8) return 8; // Falling water
		return 8 - level;
	}

	public static double getHeight(Block block) {
		int level = getLevel(block);
		return switch (level) {
			case 1 -> 0.25;
			case 2 -> 0.375;
			case 3 -> 0.5;
			case 4 -> 0.625;
			case 5 -> 0.75;
			default -> 1;
		};
	}

	public static boolean isTouchingWater(Entity entity, Block block, int blockY) {
		if (!block.compare(Block.WATER) && !block.compare(Block.BUBBLE_COLUMN)
				&& !"true".equals(block.getProperty("waterlogged"))) return false;
		if (entity.getPosition().y() + entity.getBoundingBox().height() < blockY) return false;
		if (entity.getPosition().y() > (blockY + getHeight(block))) return false;
		return true;
	}

	record PairXZ(int x, int z) {}

	public static boolean isTouchingWater(Entity entity) {
		return isTouchingWater(entity, entity.getPosition());
	}

	public static boolean isTouchingWater(Entity entity, Pos position) {
		double x = position.x();
		int blockX = position.blockX();
		double z = position.z();
		int blockZ = position.blockZ();
		double y = position.y();
		int blockY = position.blockY();

		List<PairXZ> points = new ArrayList<>();
		points.add(new PairXZ(blockX, blockZ));

		if (x - blockX > 0.7) {
			if (z - blockZ > 0.7) {
				points.add(new PairXZ(blockX + 1, blockZ + 1));
			}
			points.add(new PairXZ(blockX + 1, blockZ));
		} else if (x - blockX < 0.2) {
			if (z - blockZ < 0.2) {
				points.add(new PairXZ(blockX - 1, blockZ - 1));
			}
			points.add(new PairXZ(blockX - 1, blockZ));
		}
		if (z - blockZ > 0.7) {
			if (x - blockX < 0.2) {
				points.add(new PairXZ(blockX - 1, blockZ + 1));
			}
			points.add(new PairXZ(blockX, blockZ + 1));
		} else if (z - blockZ < 0.2) {
			if (x - blockX > 0.7) {
				points.add(new PairXZ(blockX + 1, blockZ - 1));
			}
			points.add(new PairXZ(blockX, blockZ - 1));
		}

		Instance instance = entity.getInstance();
		assert instance != null;

		for (PairXZ pair : points) {
			Block block = instance.getBlock(pair.x(), blockY, pair.z());
			if (isTouchingWater(entity, block, blockY)) return true;
			block = instance.getBlock(pair.x(), blockY + 1, pair.z());
			if (isTouchingWater(entity, block, blockY + 1)) return true;

			if (y - blockY >= 2 - entity.getBoundingBox().height()) {
				block = instance.getBlock(pair.x(), blockY + 2, pair.z());
				if (isTouchingWater(entity, block, blockY + 2)) return true;
			}
		}

		return false;
	}

	public static boolean isTouchingWater(Player player) {
		return isTouchingWater((Entity) player);
	}

	public static boolean isInRain(Entity entity) {
		var instance = entity.getInstance();

		if (instance == null) return false;
		if (!instance.getWeather().isRaining()) return false;
		if (!instance.getCachedDimensionType().hasSkylight() || instance.getCachedDimensionType().hasCeiling()) return false;

		var position = entity.getPosition();

		if (isRainingAt(instance, position.blockX(), position.blockY(), position.blockZ())) return true;

		var topBlockY = CoordConversion.globalToBlock(position.y() + entity.getBoundingBox().maxY());

		return isRainingAt(instance, position.blockX(), topBlockY, position.blockZ());
	}

	public static boolean isRainingAt(Instance instance, int blockX, int blockY, int blockZ) {
		var chunk = instance.getChunkAt(blockX, blockZ);

		if (chunk == null) return false;

		var highestBlockY = chunk.motionBlockingHeightmap().getHeight(blockX, blockZ);

		return highestBlockY < blockY;
	}
}
