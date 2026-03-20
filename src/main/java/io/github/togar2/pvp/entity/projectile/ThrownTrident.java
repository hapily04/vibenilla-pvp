package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.enchantment.EntityGroup;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.utils.EntityUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.*;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.entity.metadata.projectile.ThrownTridentMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.sound.SoundEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class ThrownTrident extends AbstractArrow {
	private final ItemStack tridentItem;
	private boolean damageDone;
	private boolean hasStartedReturning;

	public ThrownTrident(@Nullable Entity shooter, ItemStack tridentItem, EnchantmentFeature enchantmentFeature) {
		super(shooter, EntityType.TRIDENT, enchantmentFeature);
		this.tridentItem = tridentItem;

		ThrownTridentMeta meta = ((ThrownTridentMeta) this.getEntityMeta());
		meta.setLoyaltyLevel((byte) tridentItem.get(DataComponents.ENCHANTMENTS).level(Enchantment.LOYALTY));

		meta.setHasEnchantmentGlint(!Objects.requireNonNull(tridentItem.get(DataComponents.ENCHANTMENTS))
				.enchantments().isEmpty());
	}

	@Override
	public void update(long time) {
		if (this.stuckTime > 4) this.damageDone = true;

		Entity shooter = this.getShooter();
		int loyalty = ((ThrownTridentMeta) this.getEntityMeta()).getLoyaltyLevel();
		if (loyalty > 0 && (this.damageDone || this.isNoClip()) && shooter != null) {
			if (shooter.isRemoved() || (shooter instanceof LivingEntity living && living.isDead())
					|| (shooter instanceof Player player && player.getGameMode() == GameMode.SPECTATOR)) {
				if (this.pickupMode == PickupMode.ALLOWED)
					EntityUtil.spawnItemAtLocation(this, this.tridentItem, 0.1);
                this.remove();
			} else {
				// Move towards owner
                this.setNoClip(true);
                this.setNoGravity(true);
				Vec vector = shooter.getPosition().add(0, shooter.getEyeHeight(), 0).asVec().sub(this.position);
                this.refreshPosition(this.position.add(0, vector.y() * 0.015 * loyalty, 0));
                this.setVelocity(this.velocity.mul(0.95).add(vector.normalize().mul(0.05 * loyalty)
						.mul(ServerFlag.SERVER_TICKS_PER_SECOND)));

				if (!this.hasStartedReturning) {
                    this.getViewersAsAudience().playSound(Sound.sound(
							SoundEvent.ITEM_TRIDENT_RETURN, Sound.Source.NEUTRAL,
							10.0f, 1.0f
					), this.position.x(), this.position.y(), this.position.z());
                    this.hasStartedReturning = true;
				}
			}
		}

		super.update(time);
	}

	@Override
	protected boolean canHit(Entity entity) {
		return !this.damageDone && super.canHit(entity);
	}

	@Override
	public boolean onHit(@NotNull Entity entity) {
		if (this.damageDone) return false;
		if (!(entity instanceof LivingEntity living)) return false;
		Entity shooter = this.getShooter();

		float damage = 8.0f + this.enchantmentFeature.getAttackDamage(this.tridentItem, EntityGroup.ofEntity(living));
		Damage damageObj = new Damage(DamageType.TRIDENT, this, shooter == null ? this : shooter, null, damage);
		if (living.damage(damageObj) && shooter instanceof LivingEntity livingShooter) {
            this.enchantmentFeature.onUserDamaged(living, livingShooter);
            this.enchantmentFeature.onTargetDamaged(livingShooter, living);
		}
        this.damageDone = true;

        this.setVelocity(this.velocity.mul(-0.01, -0.1, -0.01));
        this.getViewersAsAudience().playSound(Sound.sound(
				SoundEvent.ITEM_TRIDENT_HIT, Sound.Source.NEUTRAL,
				1.0f, 1.0f
		), this.position.x(), this.position.y(), this.position.z());

		return false;
	}

	@Override
	public boolean canBePickedUp(@Nullable Player player) {
		if (player == null) return true;
		if (this.getShooter() == player || this.getShooter() == null) {
			return super.canBePickedUp(player);
		} else return false;
	}

	@Override
	public boolean pickup(Player player) {
		return super.pickup(player)
				|| (this.isNoClip() && this.getShooter() == player && player.getInventory().addItemStack(this.tridentItem));
	}

	@Override
	protected void tickRemoval() {
		int loyalty = ((ThrownTridentMeta) this.getEntityMeta()).getLoyaltyLevel();
		if (this.pickupMode != PickupMode.ALLOWED || loyalty <= 0)
			super.tickRemoval();
	}

	@Override
	protected SoundEvent getDefaultSound() {
		return SoundEvent.ITEM_TRIDENT_HIT_GROUND;
	}

	@Override
	protected ItemStack getPickupItem() {
		return this.tridentItem;
	}
}
