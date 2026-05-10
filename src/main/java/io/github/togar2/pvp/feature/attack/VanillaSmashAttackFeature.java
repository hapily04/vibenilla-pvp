package io.github.togar2.pvp.feature.attack;

import io.github.togar2.pvp.enums.Tool;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.fall.FallFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;

/**
 * Vanilla implementation of {@link SmashAttackFeature}
 */
public class VanillaSmashAttackFeature implements SmashAttackFeature {
	public static final DefinedFeature<VanillaSmashAttackFeature> DEFINED = new DefinedFeature<>(
			FeatureType.SMASH_ATTACK, VanillaSmashAttackFeature::new,
			FeatureType.FALL, FeatureType.ENCHANTMENT
	);

	private static final float SMASH_ATTACK_FALL_THRESHOLD = 1.5F;
	private static final float SMASH_ATTACK_HEAVY_THRESHOLD = 5.0F;
	private static final double SMASH_ATTACK_KNOCKBACK_RADIUS = 3.5;
	private static final double SMASH_ATTACK_KNOCKBACK_POWER = 0.7;
	private static final double SMASH_ATTACK_VERTICAL_KNOCKBACK = 0.7;
	private static final float DENSITY_DAMAGE_PER_LEVEL = 0.5F;
	private static final float[] WIND_BURST_POWER_BY_LEVEL = {1.2F, 1.75F, 2.2F};
	private static final double WIND_BURST_RADIUS = 3.5;

	private final FeatureConfiguration configuration;

	private FallFeature fallFeature;
	private EnchantmentFeature enchantmentFeature;

	public VanillaSmashAttackFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.fallFeature = this.configuration.get(FeatureType.FALL);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
	}

	@Override
	public boolean canSmashAttack(LivingEntity attacker) {
		Tool tool = Tool.fromMaterial(attacker.getItemInMainHand().material());
		if (tool == null || !tool.isMace()) return false;

		double fallDistance = this.fallFeature.getFallDistance(attacker);
		return fallDistance > SMASH_ATTACK_FALL_THRESHOLD && !attacker.isFlyingWithElytra();
	}

	@Override
	public float getDamageBonus(LivingEntity attacker, LivingEntity target) {
		if (!this.canSmashAttack(attacker)) return 0.0F;

		double fallDistance = this.fallFeature.getFallDistance(attacker);
		double damage;

		if (fallDistance <= 3.0) {
			damage = 4.0 * fallDistance;
		} else if (fallDistance <= 8.0) {
			damage = 12.0 + 2.0 * (fallDistance - 3.0);
		} else {
			damage = 22.0 + (fallDistance - 8.0);
		}

		int densityLevel = this.enchantmentFeature.getEquipmentLevel(attacker, Enchantment.DENSITY);
		if (densityLevel > 0) {
			damage += DENSITY_DAMAGE_PER_LEVEL * densityLevel * fallDistance;
		}

		return (float) damage;
	}

	@Override
	public void applySmashAttack(LivingEntity attacker, LivingEntity target) {
		if (!this.canSmashAttack(attacker)) return;

		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		double fallDistance = this.fallFeature.getFallDistance(attacker);

		Vec velocity = attacker.getVelocity();
		attacker.setVelocity(new Vec(velocity.x(), 0.01 * tps, velocity.z()));

		boolean heavySmash = fallDistance > SMASH_ATTACK_HEAVY_THRESHOLD;
		if (target.isOnGround()) {
			if (attacker instanceof Player) {
				this.fallFeature.setExtraFallParticles(attacker, true);
			}

			SoundEvent sound = heavySmash ? SoundEvent.ITEM_MACE_SMASH_GROUND_HEAVY : SoundEvent.ITEM_MACE_SMASH_GROUND;
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					sound, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F, 1.0F
			), attacker);
		} else {
			ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
					SoundEvent.ITEM_MACE_SMASH_AIR, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
					1.0F, 1.0F
			), attacker);
		}

		this.applySmashKnockback(attacker, target, heavySmash);
		this.applyWindBurst(attacker);
		this.fallFeature.resetFallDistance(attacker);
	}

	private void applyWindBurst(LivingEntity attacker) {
		int windBurstLevel = this.enchantmentFeature.getEquipmentLevel(attacker, Enchantment.WIND_BURST);
		if (windBurstLevel <= 0) return;
		if (attacker instanceof Player player && player.isFlying()) return;

		float power = WIND_BURST_POWER_BY_LEVEL[Math.min(windBurstLevel, WIND_BURST_POWER_BY_LEVEL.length) - 1];

		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		Vec velocity = attacker.getVelocity();
		attacker.setVelocity(new Vec(
				velocity.x(),
				velocity.y() + power * tps,
				velocity.z()
		));

		Pos attackerPosition = attacker.getPosition();
		attacker.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_LARGE, false, false,
				attackerPosition.x(), attackerPosition.y() + 0.5, attackerPosition.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));

		ViewUtil.viewersAndSelf(attacker).playSound(Sound.sound(
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST, attacker instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0F, 1.0F
		), attacker);

		assert attacker.getInstance() != null;
		for (Entity nearbyEntity : attacker.getInstance().getNearbyEntities(attackerPosition, WIND_BURST_RADIUS)) {
			if (nearbyEntity == attacker) continue;
			if (!(nearbyEntity instanceof LivingEntity nearbyLiving)) continue;
			if (nearbyEntity.getEntityType() == EntityType.ARMOR_STAND) continue;

			Vec direction = nearbyEntity.getPosition().asVec().sub(attackerPosition.asVec());
			double directionLength = direction.length();
			if (directionLength <= 0 || directionLength > WIND_BURST_RADIUS) continue;

			double knockbackFactor = (1.0 - directionLength / WIND_BURST_RADIUS) * power;
			Vec knockbackVector = direction.normalize().mul(knockbackFactor);
			Vec nearbyVelocity = nearbyLiving.getVelocity();
			nearbyLiving.setVelocity(new Vec(
					nearbyVelocity.x() + knockbackVector.x() * tps,
					nearbyVelocity.y() + Math.abs(knockbackVector.y() + 0.3) * tps,
					nearbyVelocity.z() + knockbackVector.z() * tps
			));
		}
	}

	private void applySmashKnockback(LivingEntity attacker, LivingEntity target, boolean heavySmash) {
		Pos targetPosition = target.getPosition();
		double centerY = targetPosition.y() + 0.5;

		target.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.EXPLOSION, false, false,
				targetPosition.x(), centerY, targetPosition.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));

		int ringCount = 32;
		double ringRadius = 1.5;
		for (int particleIndex = 0; particleIndex < ringCount; particleIndex++) {
			double angle = (2.0 * Math.PI * particleIndex) / ringCount;
			double offsetX = Math.cos(angle) * ringRadius;
			double offsetZ = Math.sin(angle) * ringRadius;

			target.sendPacketToViewersAndSelf(new ParticlePacket(
					Particle.SWEEP_ATTACK, false, false,
					targetPosition.x() + offsetX, centerY, targetPosition.z() + offsetZ,
					(float) (offsetX * 0.2), 0.1F, (float) (offsetZ * 0.2),
					0.1F, 0
			));
		}

		assert target.getInstance() != null;
		int tps = ServerFlag.SERVER_TICKS_PER_SECOND;

		for (Entity nearbyEntity : target.getInstance().getNearbyEntities(target.getPosition(), SMASH_ATTACK_KNOCKBACK_RADIUS)) {
			if (nearbyEntity == attacker || nearbyEntity == target) continue;
			if (!(nearbyEntity instanceof LivingEntity nearbyLiving)) continue;
			if (nearbyEntity.getEntityType() == EntityType.ARMOR_STAND) continue;
			if (nearbyEntity instanceof Player nearbyPlayer && nearbyPlayer.getGameMode() == GameMode.SPECTATOR) continue;
			if (nearbyEntity instanceof Player nearbyPlayer && nearbyPlayer.getGameMode() == GameMode.CREATIVE && nearbyPlayer.isFlying()) continue;
			if (target.getPosition().distanceSquared(nearbyEntity.getPosition()) > SMASH_ATTACK_KNOCKBACK_RADIUS * SMASH_ATTACK_KNOCKBACK_RADIUS) continue;

			Vec direction = nearbyEntity.getPosition().asVec().sub(target.getPosition().asVec());
			double directionLength = direction.length();
			if (directionLength <= 0) continue;

			double knockbackResistance = nearbyLiving.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
			double knockbackPower = (SMASH_ATTACK_KNOCKBACK_RADIUS - directionLength)
					* SMASH_ATTACK_KNOCKBACK_POWER
					* (heavySmash ? 2 : 1)
					* (1.0 - knockbackResistance);

			if (knockbackPower <= 0) continue;

			Vec knockbackVector = direction.normalize().mul(knockbackPower);
			Vec nearbyVelocity = nearbyLiving.getVelocity();
			nearbyLiving.setVelocity(new Vec(
					nearbyVelocity.x() + knockbackVector.x() * tps,
					SMASH_ATTACK_VERTICAL_KNOCKBACK * tps,
					nearbyVelocity.z() + knockbackVector.z() * tps
			));
		}
	}
}
