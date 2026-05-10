package io.github.togar2.pvp.feature.fall;

import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.feature.state.PlayerStateFeature;
import io.github.togar2.pvp.utils.FluidUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerStartFlyingWithElytraEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.registry.Registries;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link FallFeature}
 */
public class VanillaFallFeature implements FallFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaFallFeature> DEFINED = new DefinedFeature<>(
			FeatureType.FALL, VanillaFallFeature::new,
			VanillaFallFeature::initPlayer,
			FeatureType.PLAYER_STATE, FeatureType.ITEM_DAMAGE
	);

	public static final Tag<Double> FALL_DISTANCE = Tag.Transient("fallDistance");
	public static final Tag<Boolean> EXTRA_FALL_PARTICLES = Tag.Transient("extraFallParticles");
	public static final Tag<Integer> FALL_FLYING_TICKS = Tag.Integer("fallFlyingTicks");

	private final FeatureConfiguration configuration;

	private PlayerStateFeature playerStateFeature;
	private ItemDamageFeature itemDamageFeature;

	public VanillaFallFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.playerStateFeature = this.configuration.get(FeatureType.PLAYER_STATE);
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
	}

	public static void initPlayer(Player player, boolean firstInit) {
		player.setTag(FALL_DISTANCE, 0.0);
		player.setTag(FALL_FLYING_TICKS, 0);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerStartFlyingWithElytraEvent.class, event -> {
			var player = event.getPlayer();

			if (!this.canGlide(player)) {
				player.setFlyingWithElytra(false);
			}
		});

		node.addListener(PlayerTickEvent.class, event -> this.updateFallFlying(event.getPlayer()));

		// For living non-player entities, handle fall damage every tick
		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity livingEntity)) return;
			if (livingEntity instanceof Player) return;

			Pos previousPosition = livingEntity.getPreviousPosition();
            this.handleFallDamage(livingEntity, previousPosition, livingEntity.getPosition(), livingEntity.isOnGround());
		});

		// For players, handle fall damage on move event
		node.addListener(PlayerMoveEvent.class, event -> {
			Player player = event.getPlayer();
			if (this.playerStateFeature.isClimbing(player)) player.setTag(FALL_DISTANCE, 0.0);

            this.handleFallDamage(
					player, player.getPosition(),
					event.getNewPosition(), event.isOnGround()
			);
		});
	}

	private boolean canGlide(Player player) {
		if (player.isFlying()
				|| player.isOnGround()
				|| player.getVehicle() != null
				|| player.hasEffect(PotionEffect.LEVITATION)
				|| FluidUtil.isTouchingWater(player)) {
			return false;
		}

		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				return true;
			}
		}

		return false;
	}

	private boolean canContinueGliding(Player player) {
		if (player.isFlying()
				|| player.isOnGround()
				|| player.getVehicle() != null
				|| player.hasEffect(PotionEffect.LEVITATION)) {
			return false;
		}

		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				return true;
			}
		}

		return false;
	}

	private boolean canGlideUsing(ItemStack stack, EquipmentSlot slot) {
		var equippable = stack.get(DataComponents.EQUIPPABLE);

		return stack.has(DataComponents.GLIDER)
				&& equippable != null
				&& slot == equippable.slot()
				&& !this.nextDamageWillBreak(stack);
	}

	private void updateFallFlying(Player player) {
		if (!player.isFlyingWithElytra()) {
			player.setTag(FALL_FLYING_TICKS, 0);
			return;
		}

		if (!this.canContinueGliding(player)) {
			player.setFlyingWithElytra(false);
			player.setTag(FALL_FLYING_TICKS, 0);
			return;
		}

		var fallFlyingTicks = player.getTag(FALL_FLYING_TICKS) + 1;
		player.setTag(FALL_FLYING_TICKS, fallFlyingTicks);

		if (fallFlyingTicks % 20 != 0) {
			return;
		}

		var gliderSlots = new ArrayList<EquipmentSlot>();
		for (var slot : EquipmentSlot.values()) {
			if (this.canGlideUsing(player.getEquipment(slot), slot)) {
				gliderSlots.add(slot);
			}
		}

		if (gliderSlots.isEmpty()) {
			return;
		}

		var slot = gliderSlots.get(ThreadLocalRandom.current().nextInt(gliderSlots.size()));
		this.itemDamageFeature.damageEquipment(player, slot, 1);
	}

	private boolean nextDamageWillBreak(ItemStack stack) {
		if (stack.has(DataComponents.UNBREAKABLE)) return false;

		var maxDamage = stack.get(DataComponents.MAX_DAMAGE, 0);

		if (maxDamage <= 0) return false;

		return stack.get(DataComponents.DAMAGE, 0) + 1 >= maxDamage;
	}

	public void handleFallDamage(LivingEntity entity, Pos currPos, Pos newPos, boolean onGround) {
		double dy = newPos.y() - currPos.y();
		double fallDistance = this.getFallDistance(entity);

		if (FluidUtil.isTouchingWater(entity, newPos) || this.isTouchingSweetBerryBush(entity, newPos)) {
			entity.setTag(FALL_DISTANCE, 0.0);
			return;
		}

		if (this.isTouchingLava(entity, newPos)) {
			fallDistance *= 0.5;
			entity.setTag(FALL_DISTANCE, fallDistance);
		}

		if ((entity instanceof Player player && player.isFlying())
				|| entity.hasEffect(PotionEffect.LEVITATION)
				|| entity.hasEffect(PotionEffect.SLOW_FALLING) || dy > 0) {
			entity.setTag(FALL_DISTANCE, 0.0);
			return;
		}

		if (entity.isFlyingWithElytra() && entity.getVelocity().y() > -0.5) {
			entity.setTag(FALL_DISTANCE, 1.0);
			return;
		}

		if (!onGround) {
			if (dy < 0) entity.setTag(FALL_DISTANCE, fallDistance - dy);
			return;
		}

		Point landingPos = this.getLandingPos(entity, newPos);
		Block block = entity.getInstance().getBlock(landingPos);
		var adjustedFallDistance = this.adjustFallDistance(block, fallDistance);
		var damageModifier = this.getDamageModifier(block);

		if (entity.hasTag(EXTRA_FALL_PARTICLES) && entity.getTag(EXTRA_FALL_PARTICLES) && fallDistance > 0.0) {
			Vec position = landingPos.asVec().apply(Vec.Operator.FLOOR).add(0.5, 1, 0.5);
			int particleCount = (int) Math.max(0, Math.min(200, 50 * fallDistance));

			entity.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.BLOCK.withBlock(block),
					position.x(), position.y(), position.z(),
					0.3f, 0.3f, 0.3f,
					0.15f, particleCount
			));

			entity.removeTag(EXTRA_FALL_PARTICLES);
		}

		double safeFallDistance = entity.getAttributeValue(Attribute.SAFE_FALL_DISTANCE);
		if (adjustedFallDistance > safeFallDistance) {
			if (!block.isAir()) {
				double damageDistance = Math.floor(adjustedFallDistance + 1.0E-6 - safeFallDistance);
				double particleMultiplier = Math.min(0.2 + damageDistance / 15.0, 2.5);
				int particleCount = (int) (150 * particleMultiplier);

				entity.sendPacketToViewersAndSelf(new ParticlePacket(
						Particle.BLOCK.withBlock(block), false,
						false,
						newPos.x(), newPos.y(), newPos.z(),
						0, 0, 0,
						0.15f, particleCount
				));
			}
		}

		entity.setTag(FALL_DISTANCE, 0.0);

		if (entity instanceof Player player && player.getGameMode().invulnerable()) return;
		int damage = this.getFallDamage(entity, adjustedFallDistance, damageModifier);
		if (damage > 0) {
            this.playFallSound(entity, damage);
			entity.damage(DamageType.FALL, damage);
		}
	}

	public void playFallSound(LivingEntity entity, int damage) {
		boolean bigFall = damage > 4;

		entity.getViewersAsAudience().playSound(Sound.sound(
				bigFall ?
						SoundEvent.ENTITY_PLAYER_BIG_FALL :
						SoundEvent.ENTITY_PLAYER_SMALL_FALL,
				entity instanceof  Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0f, 1.0f
		), entity);
	}

	@Override
	public int getFallDamage(LivingEntity entity, double fallDistance) {
		return this.getFallDamage(entity, fallDistance, 1.0);
	}

	protected int getFallDamage(LivingEntity entity, double fallDistance, double damageModifier) {
		if (this.isFallDamageImmune(entity)) return 0;

		double safeFallDistance = entity.getAttributeValue(Attribute.SAFE_FALL_DISTANCE);
		return (int) Math.floor((fallDistance + 1.0E-6 - safeFallDistance)
				* damageModifier
				* entity.getAttributeValue(Attribute.FALL_DAMAGE_MULTIPLIER));
	}

	private boolean isFallDamageImmune(LivingEntity entity) {
		var entityTypeTag = MinecraftServer.process().entityType().getTag(Key.key("minecraft:fall_damage_immune"));

		if (entityTypeTag == null) return false;

		var key = entity.getEntityType().asKey();

		return key != null && entityTypeTag.contains(key);
	}

	@Override
	public double getFallDistance(LivingEntity entity) {
		return entity.hasTag(FALL_DISTANCE) ? entity.getTag(FALL_DISTANCE) : 0.0;
	}

	@Override
	public void resetFallDistance(LivingEntity entity) {
		entity.setTag(FALL_DISTANCE, 0.0);
	}

	@Override
	public void setExtraFallParticles(LivingEntity entity, boolean extraFallParticles) {
		if (extraFallParticles) entity.setTag(EXTRA_FALL_PARTICLES, true);
		else entity.removeTag(EXTRA_FALL_PARTICLES);
	}

	protected Point getLandingPos(LivingEntity livingEntity, Pos position) {
		Point offset = position.add(0, -0.2, 0);
		Instance instance = livingEntity.getInstance();

		if (instance == null) return offset;
		if (!instance.getBlock(offset).isAir()) return offset;

		Point offsetDown = offset.add(0, -1, 0);
		Block block = instance.getBlock(offsetDown);

		Registries registries = MinecraftServer.process();
		var fences = registries.blocks().getTag(Key.key("minecraft:fences"));
		var walls = registries.blocks().getTag(Key.key("minecraft:walls"));
		var fenceGates = registries.blocks().getTag(Key.key("minecraft:fence_gates"));

		var key = block.asKey();

		assert fences != null;
		assert walls != null;
		assert fenceGates != null;
		assert key != null;

		if (fences.contains(key)
				|| walls.contains(key)
				|| fenceGates.contains(key)) {
			return offsetDown;
		}

		return offset;
	}

	private double adjustFallDistance(Block block, double fallDistance) {
		if (this.isBed(block)) return fallDistance * 0.5;

		return fallDistance;
	}

	private double getDamageModifier(Block block) {
		if (block.compare(Block.SLIME_BLOCK)) return 0.0;
		if (block.compare(Block.HAY_BLOCK) || block.compare(Block.HONEY_BLOCK)) return 0.2;

		return 1.0;
	}

	private boolean isBed(Block block) {
		return block.key().value().endsWith("_bed");
	}

	private boolean isTouchingLava(LivingEntity entity, Pos position) {
		return this.isTouchingBlock(entity, position, Block.LAVA);
	}

	private boolean isTouchingSweetBerryBush(LivingEntity entity, Pos position) {
		return this.isTouchingBlock(entity, position, Block.SWEET_BERRY_BUSH);
	}

	private boolean isTouchingBlock(LivingEntity entity, Pos position, Block block) {
		var instance = entity.getInstance();

		if (instance == null) return false;

		return instance.getBlock(position).compare(block);
	}
}
