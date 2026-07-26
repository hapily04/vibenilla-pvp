package io.github.togar2.pvp.utils;

import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockTags;

import java.util.Objects;

public class BlockUtil {
	public static boolean isClimbable(Block.Getter blockGetter, int blockX, int blockY, int blockZ, Block block) {
		var climbable = Block.staticRegistry().getTag(BlockTags.CLIMBABLE);

		if (climbable != null && climbable.contains(block)) return true;

		var trapdoors = Block.staticRegistry().getTag(BlockTags.TRAPDOORS);

		if (trapdoors == null || !trapdoors.contains(block)) return false;
		if (!"true".equals(block.getProperty("open"))) return false;

		var below = blockGetter.getBlock(blockX, blockY - 1, blockZ);

		return below.compare(Block.LADDER)
				&& Objects.equals(below.getProperty("facing"), block.getProperty("facing"));
	}

	public static boolean isClimbable(Block.Getter blockGetter, Point position) {
		return isClimbable(blockGetter, position.blockX(), position.blockY(), position.blockZ(),
				blockGetter.getBlock(position));
	}
}
