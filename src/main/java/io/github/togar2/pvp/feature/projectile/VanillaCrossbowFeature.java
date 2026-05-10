package io.github.togar2.pvp.feature.projectile;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

import net.minestom.server.component.DataComponents;
import net.minestom.server.event.item.PlayerFinishItemUseEvent;
import org.jetbrains.annotations.Nullable;

import io.github.togar2.pvp.entity.projectile.AbstractArrow;
import io.github.togar2.pvp.entity.projectile.Arrow;
import io.github.togar2.pvp.entity.projectile.CustomEntityProjectile;
import io.github.togar2.pvp.entity.projectile.FireworkRocket;
import io.github.togar2.pvp.entity.projectile.SpectralArrow;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.effect.EffectFeature;
import io.github.togar2.pvp.feature.enchantment.EnchantmentFeature;
import io.github.togar2.pvp.feature.item.ItemDamageFeature;
import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.enchant.Enchantment;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;

/**
 * Vanilla implementation of {@link CrossbowFeature}
 */
public class VanillaCrossbowFeature implements CrossbowFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaCrossbowFeature> DEFINED = new DefinedFeature<>(
			FeatureType.CROSSBOW, VanillaCrossbowFeature::new,
			FeatureType.ITEM_DAMAGE, FeatureType.EFFECT, FeatureType.ENCHANTMENT, FeatureType.PROJECTILE_ITEM
	);

	private static final Tag<Boolean> START_SOUND_PLAYED = Tag.Transient("StartSoundPlayed");
	private static final Tag<Boolean> MID_LOAD_SOUND_PLAYED = Tag.Transient("MidLoadSoundPlayed");

	private final FeatureConfiguration configuration;

	private ItemDamageFeature itemDamageFeature;
	private EffectFeature effectFeature;
	private EnchantmentFeature enchantmentFeature;
	private ProjectileItemFeature projectileItemFeature;

	public VanillaCrossbowFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.itemDamageFeature = this.configuration.get(FeatureType.ITEM_DAMAGE);
		this.effectFeature = this.configuration.get(FeatureType.EFFECT);
		this.enchantmentFeature = this.configuration.get(FeatureType.ENCHANTMENT);
		this.projectileItemFeature = this.configuration.get(FeatureType.PROJECTILE_ITEM);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(PlayerUseItemEvent.class, event -> {
			ItemStack stack = event.getItemStack();
			if (stack.material() != Material.CROSSBOW) return;
			Player player = event.getPlayer();

			if (this.isCrossbowCharged(stack)) {
				// Make sure the animation event is not called, because this is not an animation
				event.setCancelled(true);

				stack = this.performCrossbowShooting(player, event.getHand(), stack, this.getCrossbowPower(stack), 1.0);
				player.setItemInHand(event.getHand(), this.setCrossbowProjectile(stack, null));
			} else {
				if (this.projectileItemFeature.getCrossbowProjectile(player) == null) {
					event.setCancelled(true);
				} else {
					event.setItemUseTime(this.getCrossbowUseDuration(stack));
					player.setTag(START_SOUND_PLAYED, false);
					player.setTag(MID_LOAD_SOUND_PLAYED, false);
				}
			}
		});

		node.addListener(PlayerTickEvent.class, event -> {
			Player player = event.getPlayer();

			// If not charging crossbow, return
			LivingEntityMeta meta = (LivingEntityMeta) player.getEntityMeta();
			if (!meta.isHandActive() || player.getItemInHand(meta.getActiveHand()).material() != Material.CROSSBOW)
				return;

			PlayerHand hand = player.getPlayerMeta().getActiveHand();
			ItemStack stack = player.getItemInHand(hand);

			int quickCharge = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.QUICK_CHARGE);

			long useTicks = player.getCurrentItemUseTime();
			double progress = useTicks / (double) this.getCrossbowChargeDuration(stack);

			Boolean startSoundPlayed = player.getTag(START_SOUND_PLAYED);
			Boolean midLoadSoundPlayed = player.getTag(MID_LOAD_SOUND_PLAYED);
			if (startSoundPlayed == null) startSoundPlayed = false;
			if (midLoadSoundPlayed == null) midLoadSoundPlayed = false;

			if (progress >= 0.2 && !startSoundPlayed) {
				SoundEvent startSound = this.getCrossbowStartSound(quickCharge);
				ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
						startSound, Sound.Source.PLAYER,
						0.5f, 1.0f
				), player);

				player.setTag(START_SOUND_PLAYED, true);
				player.setItemInHand(hand, stack);
			}

			SoundEvent midLoadSound = quickCharge == 0 ? SoundEvent.ITEM_CROSSBOW_LOADING_MIDDLE : null;
			if (progress >= 0.5F && midLoadSound != null && !midLoadSoundPlayed) {
				ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
						midLoadSound, Sound.Source.PLAYER,
						0.5f, 1.0f
				), player);

				player.setTag(MID_LOAD_SOUND_PLAYED, true);
				player.setItemInHand(hand, stack);
			}

			if (progress >= 1.0F && !this.isCrossbowCharged(stack)) {
				stack = this.loadCrossbowProjectiles(player, stack);
				if (stack == null || stack.isAir()) return;

				this.playCrossbowLoadingEndSound(player);
				player.setItemInHand(hand, stack);
			}
		});

		node.addListener(PlayerFinishItemUseEvent.class, event -> {
			Player player = event.getPlayer();
			ItemStack stack = event.getItemStack();
			if (stack.material() != Material.CROSSBOW) return;

			int quickCharge = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.QUICK_CHARGE);

			if (quickCharge < 6) {
				long useTicks = player.getCurrentItemUseTime();
				double power = this.getCrossbowPowerForTime(useTicks, stack);
				if (!(power >= 1.0F) || this.isCrossbowCharged(stack))
					return;
			}

			stack = this.loadCrossbowProjectiles(player, stack);
			if (stack == null) return;

			this.playCrossbowLoadingEndSound(player);

			player.setItemInHand(event.getHand(), stack);
		});
	}

	protected void playCrossbowLoadingEndSound(Player player) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
				SoundEvent.ITEM_CROSSBOW_LOADING_END, Sound.Source.PLAYER,
				1.0f, 1.0f / (random.nextFloat() * 0.5f + 1.0f) + 0.2f
		), player);
	}

	protected AbstractArrow createArrow(ItemStack stack, @Nullable Entity shooter) {
		if (stack.material() == Material.SPECTRAL_ARROW) {
			return new SpectralArrow(shooter, this.enchantmentFeature);
		} else {
			Arrow arrow = new Arrow(shooter, this.effectFeature, this.enchantmentFeature);
			arrow.setItemStack(stack);
			return arrow;
		}
	}

	protected double getCrossbowPower(ItemStack stack) {
		return this.crossbowContainsProjectile(stack, Material.FIREWORK_ROCKET) ? 1.6 : 3.15;
	}

	protected double getCrossbowPowerForTime(long ticks, ItemStack stack) {
		double power = ticks / (double) this.getCrossbowChargeDuration(stack);
		if (power > 1) {
			power = 1;
		}

		return power;
	}

	protected boolean isCrossbowCharged(ItemStack stack) {
		return stack.has(DataComponents.CHARGED_PROJECTILES) &&
				!Objects.requireNonNull(stack.get(DataComponents.CHARGED_PROJECTILES)).isEmpty();
	}

	protected ItemStack setCrossbowProjectile(ItemStack stack, @Nullable ItemStack projectile) {
		return stack.with(DataComponents.CHARGED_PROJECTILES, projectile == null ? List.of() : List.of(projectile));
	}

	protected ItemStack setCrossbowProjectiles(ItemStack stack, ItemStack projectile1,
	                                           ItemStack projectile2, ItemStack projectile3) {
		return stack.with(DataComponents.CHARGED_PROJECTILES, List.of(projectile1, projectile2, projectile3));
	}

	protected boolean crossbowContainsProjectile(ItemStack stack, Material projectile) {
		List<ItemStack> projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
		if (projectiles == null) return false;

		for (ItemStack itemStack : projectiles) {
			if (itemStack.material() == projectile) return true;
		}

		return false;
	}

	protected int getCrossbowUseDuration(ItemStack stack) {
		return this.getCrossbowChargeDuration(stack) + 3;
	}

	protected int getCrossbowChargeDuration(ItemStack stack) {
		int quickCharge = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.QUICK_CHARGE);
		return quickCharge == 0 ? 25 : 25 - 5 * quickCharge;
	}

	protected SoundEvent getCrossbowStartSound(int quickCharge) {
		return switch (quickCharge) {
			case 1 -> SoundEvent.ITEM_CROSSBOW_QUICK_CHARGE_1;
			case 2 -> SoundEvent.ITEM_CROSSBOW_QUICK_CHARGE_2;
			case 3 -> SoundEvent.ITEM_CROSSBOW_QUICK_CHARGE_3;
			default -> SoundEvent.ITEM_CROSSBOW_LOADING_START;
		};
	}

	protected ItemStack loadCrossbowProjectiles(Player player, ItemStack stack) {
		boolean multiShot = stack.get(DataComponents.ENCHANTMENTS).level(Enchantment.MULTISHOT) > 0;

		ItemStack projectileItem;
		int projectileSlot;

		ProjectileItemFeature.ProjectileItem projectile = this.projectileItemFeature.getCrossbowProjectile(player);
		if (projectile == null && player.getGameMode() == GameMode.CREATIVE) {
			projectileItem = Arrow.DEFAULT_ARROW;
			projectileSlot = -1;
		} else if (projectile != null) {
			projectileItem = projectile.stack();
			projectileSlot = projectile.slot();
		} else {
			// Should not happen
			return ItemStack.AIR;
		}

		if (multiShot) {
			stack = this.setCrossbowProjectiles(stack, projectileItem, projectileItem, projectileItem);
		} else {
			stack = this.setCrossbowProjectile(stack, projectileItem);
		}

		if (player.getGameMode() != GameMode.CREATIVE && projectileSlot >= 0) {
			player.getInventory().setItemStack(projectileSlot, projectileItem.withAmount(projectileItem.amount() - 1));
		}

		return stack;
	}

	protected ItemStack performCrossbowShooting(Player player, PlayerHand hand, ItemStack stack,
	                                            double power, double spread) {
		List<ItemStack> projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
		if (projectiles == null || projectiles.isEmpty()) return ItemStack.AIR;

		ItemStack projectile = projectiles.getFirst();
		if (!projectile.isAir()) {
            this.shootCrossbowProjectile(player, hand, stack, projectile, 1.0F, power, spread, 0.0F);
		}

		if (projectiles.size() > 2) {
			ThreadLocalRandom random = ThreadLocalRandom.current();
			boolean firstHighPitch = random.nextBoolean();
			float firstPitch = this.getRandomShotPitch(firstHighPitch, random);
			float secondPitch = this.getRandomShotPitch(!firstHighPitch, random);

			projectile = projectiles.get(1);
			if (!projectile.isAir()) {
                this.shootCrossbowProjectile(player, hand, stack, projectile, firstPitch, power, spread, -10.0F);
			}
			projectile = projectiles.get(2);
			if (!projectile.isAir()) {
                this.shootCrossbowProjectile(player, hand, stack, projectile, secondPitch, power, spread, 10.0F);
			}
		}

		return this.setCrossbowProjectile(stack, ItemStack.AIR);
	}

	protected void shootCrossbowProjectile(Player player, PlayerHand hand, ItemStack crossbowStack,
	                                       ItemStack projectile, float soundPitch,
	                                       double power, double spread, float yaw) {
		var firework = projectile.material() == Material.FIREWORK_ROCKET;

		if (firework) {
			var projectileEntity = new FireworkRocket(player, projectile, true);
			var position = player.getPosition().add(0, player.getEyeHeight() - 0.15, 0);
			this.shootProjectileEntity(projectileEntity, player, position, yaw, power, spread);
		} else {
			var arrow = this.getCrossbowArrow(player, crossbowStack, projectile);
			if (player.getGameMode() == GameMode.CREATIVE || yaw != 0.0) {
				arrow.setPickupMode(AbstractArrow.PickupMode.CREATIVE_ONLY);
			}

			var position = player.getPosition().add(0, player.getEyeHeight() - 0.1, 0);
			this.shootProjectileEntity(arrow, player, position, yaw, power, spread);
		}

        this.itemDamageFeature.damageEquipment(player, hand == PlayerHand.MAIN ?
				EquipmentSlot.MAIN_HAND : EquipmentSlot.OFF_HAND, firework ? 3 : 1);

		ViewUtil.viewersAndSelf(player).playSound(Sound.sound(
				SoundEvent.ITEM_CROSSBOW_SHOOT, Sound.Source.PLAYER,
				1.0f, soundPitch
		), player);
	}

	private void shootProjectileEntity(CustomEntityProjectile projectileEntity, Player player, Pos position,
	                                   float yaw, double power, double spread) {
		projectileEntity.setInstance(Objects.requireNonNull(player.getInstance()), position);
		var shotVector = this.getProjectileShotVector(player.getPosition(), yaw);
		projectileEntity.shoot(shotVector.x(), shotVector.y(), shotVector.z(), power, spread);
	}

	private Vec getProjectileShotVector(Pos position, float angle) {
		var viewVector = position.direction();
		var upVector = this.getUpVector(position);

		return this.rotateAroundAxis(viewVector, upVector, angle);
	}

	private Vec getUpVector(Pos position) {
		var pitch = Math.toRadians(position.pitch());
		var yaw = Math.toRadians(position.yaw());

		return new Vec(
				Math.sin(yaw) * Math.sin(pitch),
				Math.cos(pitch),
				-Math.cos(yaw) * Math.sin(pitch)
		);
	}

	private Vec rotateAroundAxis(Vec vector, Vec axis, float angle) {
		var radians = Math.toRadians(angle);
		var cos = Math.cos(radians);
		var sin = Math.sin(radians);
		var normalizedAxis = axis.normalize();

		return vector.mul(cos)
				.add(normalizedAxis.cross(vector).mul(sin))
				.add(normalizedAxis.mul(normalizedAxis.dot(vector) * (1.0 - cos)));
	}

	protected AbstractArrow getCrossbowArrow(Player player, ItemStack crossbowStack, ItemStack projectile) {
		AbstractArrow arrow = this.createArrow(projectile.withAmount(1), player);
		arrow.setCritical(true); // Player shooter is always critical
		arrow.setSound(SoundEvent.ITEM_CROSSBOW_HIT);

		int piercing = crossbowStack.get(DataComponents.ENCHANTMENTS).level(Enchantment.PIERCING);
		if (piercing > 0) {
			arrow.setPiercingLevel((byte) piercing);
		}

		return arrow;
	}

	protected float getRandomShotPitch(boolean high, ThreadLocalRandom random) {
		float base = high ? 0.63F : 0.43F;
		return 1.0F / (random.nextFloat() * 0.5F + 1.8F) + base;
	}
}
