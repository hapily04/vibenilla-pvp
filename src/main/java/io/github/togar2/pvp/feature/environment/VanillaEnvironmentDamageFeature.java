package io.github.togar2.pvp.feature.environment;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.utils.FluidUtil;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.CoordConversion;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.item.Material;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.tag.Tag;

import java.util.Set;

/**
 * Vanilla implementation of {@link EnvironmentDamageFeature}
 */
public final class VanillaEnvironmentDamageFeature implements EnvironmentDamageFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEnvironmentDamageFeature> DEFINED = new DefinedFeature<>(
			FeatureType.ENVIRONMENT_DAMAGE, VanillaEnvironmentDamageFeature::new,
			VanillaEnvironmentDamageFeature::initPlayer
	);

	private static final int MAX_AIR_SUPPLY = 300;
	private static final int DROWN_THRESHOLD = -20;
	private static final int FREEZE_MAX_TICKS = 140;
	private static final int FREEZE_DAMAGE_INTERVAL = 40;
	private static final int FIRE_DAMAGE_INTERVAL = 20;
	private static final int FIRE_IGNITE_TICKS = 8 * 20;
	private static final int LAVA_IGNITE_TICKS = 15 * 20;

	private static final Tag<Integer> AIR_SUPPLY = Tag.Integer("environmentAirSupply");
	private static final Tag<Integer> FREEZE_TICKS = Tag.Integer("environmentFreezeTicks");

	private static final Set<Material> FREEZE_IMMUNE_WEARABLES = Set.of(
			Material.LEATHER_BOOTS, Material.LEATHER_LEGGINGS,
			Material.LEATHER_CHESTPLATE, Material.LEATHER_HELMET
	);

	@SuppressWarnings("unused")
	public VanillaEnvironmentDamageFeature(FeatureConfiguration configuration) {
	}

	public static void initPlayer(Player player, boolean firstInit) {
		player.setTag(AIR_SUPPLY, MAX_AIR_SUPPLY);
		player.setTag(FREEZE_TICKS, 0);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerTickEvent.class, event -> this.handlePlayerTick(event.getPlayer()));

		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
			if (livingEntity instanceof Player) return;

			this.handleEntityTick(livingEntity);
		});
	}

	private void handlePlayerTick(Player player) {
		this.handleExtinguishing(player);
		this.handleFireDamage(player);
		this.handleLavaDamage(player);
		this.handleVoidDamage(player);
		this.handleDrowning(player);
		this.handleBlockContactDamage(player);
		this.handleFreezeDamage(player);
	}

	private void handleEntityTick(LivingEntity entity) {
		this.handleExtinguishing(entity);
		this.handleFireDamage(entity);
		this.handleVoidDamage(entity);
	}

	private void handleExtinguishing(LivingEntity entity) {
		if (!entity.isOnFire()) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		if (this.isInPowderSnow(instance, entity) || this.isTouchingWater(entity)
				|| this.isInRain(instance, entity) || this.isInWaterCauldron(instance, entity)) {
			this.extinguish(entity);
		}
	}

	private void handleFireDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		var position = entity.getPosition();
		var block = instance.getBlock(position);

		if (block.compare(Block.FIRE) || block.compare(Block.SOUL_FIRE)) {
			entity.setFireTicks(FIRE_IGNITE_TICKS);
			entity.damage(DamageType.IN_FIRE, 1.0F);
			return;
		}

		if (!entity.isOnFire()) return;

		if (entity.getFireTicks() % FIRE_DAMAGE_INTERVAL == 0 && !this.isInLava(entity)) {
			entity.damage(DamageType.ON_FIRE, 1.0F);
		}
	}

	private void handleLavaDamage(LivingEntity entity) {
		if (!this.isInLava(entity)) return;

		entity.setFireTicks(LAVA_IGNITE_TICKS);
		entity.damage(DamageType.LAVA, 4.0F);
	}

	private void handleVoidDamage(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		if (!instance.isInVoid(entity.getPosition())) return;

		entity.damage(DamageType.OUT_OF_WORLD, 4.0F);
	}

	private void handleDrowning(Player player) {
		if (player.getGameMode().invulnerable()) return;

		var instance = player.getInstance();

		if (instance == null) return;

		int airSupply = player.hasTag(AIR_SUPPLY) ? player.getTag(AIR_SUPPLY) : MAX_AIR_SUPPLY;

		if (this.isEyeInWater(player)) {
			var eyeBlock = instance.getBlock(
					player.getPosition().blockX(),
					(int) (player.getPosition().y() + player.getEyeHeight()),
					player.getPosition().blockZ()
			);

			if (eyeBlock.compare(Block.BUBBLE_COLUMN)) {
				airSupply = this.increaseAirSupply(airSupply);
			} else if (!this.hasWaterBreathing(player)) {
				airSupply = this.decreaseAirSupply(player, airSupply);

				if (airSupply <= DROWN_THRESHOLD) {
					airSupply = 0;
					player.damage(DamageType.DROWN, 2.0F);
				}
			}
		} else if (airSupply < MAX_AIR_SUPPLY) {
			airSupply = this.increaseAirSupply(airSupply);
		}

		player.setTag(AIR_SUPPLY, airSupply);
		player.getEntityMeta().setAirTicks(Math.max(airSupply, 0));
	}

	private void handleBlockContactDamage(Player player) {
		var instance = player.getInstance();

		if (instance == null) return;

		var position = player.getPosition();
		var boundingBox = player.getBoundingBox();

		int minX = (int) Math.floor(position.x() - boundingBox.width() / 2);
		int maxX = (int) Math.floor(position.x() + boundingBox.width() / 2);
		int minY = position.blockY();
		int maxY = (int) Math.floor(position.y() + boundingBox.height());
		int minZ = (int) Math.floor(position.z() - boundingBox.depth() / 2);
		int maxZ = (int) Math.floor(position.z() + boundingBox.depth() / 2);

		for (int blockX = minX; blockX <= maxX; blockX++) {
			for (int blockY = minY; blockY <= maxY; blockY++) {
				for (int blockZ = minZ; blockZ <= maxZ; blockZ++) {
					var block = instance.getBlock(blockX, blockY, blockZ);

					if (block.compare(Block.CACTUS)) {
						player.damage(DamageType.CACTUS, 1.0F);
					} else if (block.compare(Block.SWEET_BERRY_BUSH)) {
						this.handleBerryBushDamage(player, block);
					} else if (block.compare(Block.CAMPFIRE) || block.compare(Block.SOUL_CAMPFIRE)) {
						this.handleCampfireDamage(player, block);
					}
				}
			}
		}

		if (player.isOnGround()) {
			var belowBlock = instance.getBlock(position.add(0, -0.5, 0));

			if (belowBlock.compare(Block.MAGMA_BLOCK) && !player.isSneaking()) {
				player.damage(DamageType.HOT_FLOOR, 1.0F);
			}
		}
	}

	private void handleBerryBushDamage(Player player, Block block) {
		var ageProperty = block.getProperty("age");

		if (ageProperty == null || "0".equals(ageProperty)) return;

		Pos position = player.getPosition();
		Pos previousPosition = player.getPreviousPosition();
		double movementX = Math.abs(position.x() - previousPosition.x());
		double movementZ = Math.abs(position.z() - previousPosition.z());

		if (movementX >= 0.003 || movementZ >= 0.003) {
			player.damage(DamageType.SWEET_BERRY_BUSH, 1.0F);
		}
	}

	private void handleCampfireDamage(Player player, Block block) {
		var litProperty = block.getProperty("lit");

		if (!"true".equals(litProperty)) return;

		var fireDamage = block.compare(Block.SOUL_CAMPFIRE) ? 2.0F : 1.0F;
		player.damage(DamageType.CAMPFIRE, fireDamage);
	}

	private void handleFreezeDamage(Player player) {
		var instance = player.getInstance();

		if (instance == null) return;

		int freezeTicks = player.hasTag(FREEZE_TICKS) ? player.getTag(FREEZE_TICKS) : 0;
		boolean inPowderSnow = this.isInPowderSnow(instance, player);
		boolean canFreeze = this.canFreeze(player);

		if (inPowderSnow && canFreeze) {
			freezeTicks = Math.min(freezeTicks + 1, FREEZE_MAX_TICKS);
		} else {
			freezeTicks = Math.max(freezeTicks - 2, 0);
		}

		player.setTag(FREEZE_TICKS, freezeTicks);
		player.getEntityMeta().setTickFrozen(freezeTicks);

		if (freezeTicks >= FREEZE_MAX_TICKS
				&& player.getAliveTicks() % FREEZE_DAMAGE_INTERVAL == 0
				&& canFreeze) {
			player.damage(DamageType.FREEZE, 1.0F);
		}
	}

	private boolean canFreeze(Player player) {
		if (player.getGameMode() == GameMode.SPECTATOR) return false;

		for (var slot : EquipmentSlot.armors()) {
			var material = player.getEquipment(slot).material();

			if (FREEZE_IMMUNE_WEARABLES.contains(material)) return false;
		}

		return true;
	}

	private void extinguish(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return;

		this.lowerWaterCauldron(instance, entity);
		entity.setFireTicks(0);

		ViewUtil.viewersAndSelf(entity).playSound(Sound.sound(
				SoundEvent.ENTITY_GENERIC_EXTINGUISH_FIRE, Sound.Source.NEUTRAL,
				0.7F, 1.0F
		), entity);
	}

	private boolean isInLava(LivingEntity entity) {
		var instance = entity.getInstance();

		if (instance == null) return false;

		var position = entity.getPosition();
		var block = instance.getBlock(position);

		return block.compare(Block.LAVA);
	}

	private boolean isTouchingWater(LivingEntity entity) {
		return FluidUtil.isTouchingWater(entity);
	}

	private boolean isInPowderSnow(Instance instance, LivingEntity entity) {
		return instance.getBlock(entity.getPosition()).compare(Block.POWDER_SNOW);
	}

	private boolean isInRain(Instance instance, LivingEntity entity) {
		if (!instance.getWeather().isRaining()) return false;
		if (!instance.getCachedDimensionType().hasSkylight() || instance.getCachedDimensionType().hasCeiling()) return false;

		var position = entity.getPosition();
		var boundingBox = entity.getBoundingBox();

		if (this.isRainingAt(instance, position.blockX(), position.blockY(), position.blockZ())) return true;

		int topBlockY = CoordConversion.globalToBlock(position.y() + boundingBox.maxY());

		return this.isRainingAt(instance, position.blockX(), topBlockY, position.blockZ());
	}

	private boolean isRainingAt(Instance instance, int blockX, int blockY, int blockZ) {
		var chunk = instance.getChunkAt(blockX, blockZ);

		if (chunk == null) return false;

		var highestBlockY = chunk.motionBlockingHeightmap().getHeight(blockX, blockZ);

		return highestBlockY < blockY;
	}

	private boolean isInWaterCauldron(Instance instance, LivingEntity entity) {
		var position = entity.getPosition();
		var block = instance.getBlock(position);

		if (!block.compare(Block.WATER_CAULDRON)) return false;

		var levelProperty = block.getProperty("level");

		if (levelProperty == null) return false;

		int level = Integer.parseInt(levelProperty);
		double contentHeight = position.blockY() + (6.0 + level * 3.0) / 16.0;
		double feetY = position.y() + entity.getBoundingBox().minY();

		return feetY <= contentHeight;
	}

	private boolean lowerWaterCauldron(Instance instance, LivingEntity entity) {
		var position = entity.getPosition();
		var block = instance.getBlock(position);

		if (!block.compare(Block.WATER_CAULDRON)) return false;

		var levelProperty = block.getProperty("level");

		if (levelProperty == null) return false;

		int level = Integer.parseInt(levelProperty);

		if (level <= 1) {
			instance.setBlock(position, Block.CAULDRON);
		} else {
			instance.setBlock(position, block.withProperty("level", String.valueOf(level - 1)));
		}

		return true;
	}

	private boolean isEyeInWater(Player player) {
		var instance = player.getInstance();

		if (instance == null) return false;

		int eyeBlockX = player.getPosition().blockX();
		int eyeBlockY = (int) Math.floor(player.getPosition().y() + player.getEyeHeight());
		int eyeBlockZ = player.getPosition().blockZ();
		var eyeBlock = instance.getBlock(eyeBlockX, eyeBlockY, eyeBlockZ);

		return eyeBlock.compare(Block.WATER);
	}

	private boolean hasWaterBreathing(LivingEntity entity) {
		return entity.hasEffect(PotionEffect.WATER_BREATHING)
				|| entity.hasEffect(PotionEffect.CONDUIT_POWER);
	}

	private int decreaseAirSupply(LivingEntity entity, int currentSupply) {
		double oxygenBonus = entity.getAttributeValue(Attribute.OXYGEN_BONUS);

		if (oxygenBonus > 0.0 && Math.random() >= 1.0 / (oxygenBonus + 1.0)) {
			return currentSupply;
		}

		return currentSupply - 1;
	}

	private int increaseAirSupply(int currentSupply) {
		return Math.min(currentSupply + 4, MAX_AIR_SUPPLY);
	}
}
