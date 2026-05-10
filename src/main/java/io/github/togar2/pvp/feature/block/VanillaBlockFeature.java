package io.github.togar2.pvp.feature.block;

import java.util.concurrent.ThreadLocalRandom;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import io.github.togar2.pvp.events.DamageBlockEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.cooldown.ItemCooldownFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.CombatVersion;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.entity.metadata.projectile.AbstractArrowMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.sound.SoundEvent;

/**
 * Vanilla implementation of {@link BlockFeature}
 */
public class VanillaBlockFeature implements BlockFeature {
	public static final DefinedFeature<VanillaBlockFeature> DEFINED = new DefinedFeature<>(
			FeatureType.BLOCK, VanillaBlockFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.ITEM_COOLDOWN, FeatureType.VERSION
	);

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private ItemCooldownFeature itemCooldownFeature;
	private CombatVersion version;

	public VanillaBlockFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.itemCooldownFeature = this.configuration.get(FeatureType.ITEM_COOLDOWN);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	protected boolean isPiercing(Damage damage) {
		Entity source = damage.getSource();
		if (source != null && source.getEntityMeta() instanceof AbstractArrowMeta) {
			return ((AbstractArrowMeta) source.getEntityMeta()).getPiercingLevel() > 0;
		}

		return false;
	}

	@Override
	public boolean isDamageBlocked(LivingEntity entity, Damage damage) {
		if (damage.getAmount() <= 0) return false;

		if (this.bypassesShield(damage) || this.isPiercing(damage)) return false;
		if (!(entity.getEntityMeta() instanceof LivingEntityMeta meta) || !meta.isHandActive()) return false;

		var blockingStack = entity.getItemInHand(meta.getActiveHand());
		var blocksAttacks = blockingStack.get(DataComponents.BLOCKS_ATTACKS);
		if (blocksAttacks == null) return false;

		if (entity instanceof Player blockingPlayer) {
			var blockDelayTicks = Math.round(blocksAttacks.blockDelaySeconds() * ServerFlag.SERVER_TICKS_PER_SECOND);
			if (blockingPlayer.getCurrentItemUseTime() < blockDelayTicks) return false;
		}

		if (this.version.legacy()) return true;

		if (damage.getSource() != null) {
			Pos attackerPos = damage.getSource().getPosition();
			Pos entityPos = entity.getPosition();

			Vec attackerPosVector = attackerPos.asVec();
			Vec entityRotation = entityPos.direction();
			Vec attackerDirection = entityPos.asVec().sub(attackerPosVector).normalize();
			attackerDirection = attackerDirection.withY(0);

			// Dot product is lower than zero when the angle between the vectors is >90 degrees
			return attackerDirection.dot(entityRotation) < 0.0;
		}

		return false;
	}

    private boolean bypassesShield(Damage damage) {
        var bypassesShield = MinecraftServer.process().damageType().getTag(Key.key("minecraft:bypasses_shield"));

        return bypassesShield != null && bypassesShield.contains(damage.getType());
    }

	@Override
	public boolean applyBlock(LivingEntity entity, Damage damage) {
		float amount = damage.getAmount();
		float resultingDamage = this.version.legacy() ? Math.max(0, (amount + 1) * 0.5f) : 0;

		DamageBlockEvent damageBlockEvent = new DamageBlockEvent(entity, amount, resultingDamage, false);
		EventDispatcher.call(damageBlockEvent);
		if (damageBlockEvent.isCancelled()) return false;
		damage.setAmount(damageBlockEvent.getResultingDamage());

		if (amount >= 3) {
			int shieldDamage = 1 + (int) Math.floor(amount);
			PlayerHand hand = ((LivingEntityMeta) entity.getEntityMeta()).getActiveHand();
            this.itemDamageFeature.damageEquipment(
					entity,
					hand == PlayerHand.MAIN ?
							EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND,
					shieldDamage
			);

			if (entity.getItemInHand(hand).isAir()) {
				((LivingEntityMeta) entity.getEntityMeta()).setHandActive(false);
				entity.getViewersAsAudience().playSound(Sound.sound(
						SoundEvent.ITEM_SHIELD_BREAK, Sound.Source.PLAYER,
						0.8f, 0.8f + ThreadLocalRandom.current().nextFloat(0.4f)
				));
			}
		}

		// Take shield hit (knockback and disabling)
		DamageTypeInfo info = DamageTypeInfo.of(damage.getType());
		if (!info.projectile() && damage.getAttacker() instanceof LivingEntity attacker)
            this.takeShieldHit(entity, attacker, damageBlockEvent.knockbackAttacker());

		return resultingDamage == 0;
	}

	protected void takeShieldHit(LivingEntity entity, LivingEntity attacker, boolean applyKnockback) {
		if (applyKnockback) {
			Pos entityPos = entity.getPosition();
			Pos attackerPos = attacker.getPosition();
			attacker.takeKnockback(0.5F,
					attackerPos.x() - entityPos.x(),
					attackerPos.z() - entityPos.z()
			);
		}

		if (!(entity instanceof Player player)) {
			return;
		}

		var weapon = attacker.getItemInMainHand().get(DataComponents.WEAPON);
		if (weapon != null && weapon.disableBlockingForSeconds() > 0.0F) {
			this.disableShield(
					player,
					Math.round(weapon.disableBlockingForSeconds() * ServerFlag.SERVER_TICKS_PER_SECOND)
			);
		}
	}

	protected void disableShield(Player player, int cooldownTicks) {
		var hand = player.getPlayerMeta().getActiveHand();
		this.itemCooldownFeature.setCooldown(player, player.getItemInHand(hand), cooldownTicks);

		// Shield disable status
		player.triggerStatus((byte) 30);
		player.triggerStatus((byte) 9);

		player.refreshActiveHand(false, hand == PlayerHand.OFF, false);
	}
}
