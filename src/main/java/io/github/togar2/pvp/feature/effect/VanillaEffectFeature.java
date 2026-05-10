package io.github.togar2.pvp.feature.effect;

import io.github.togar2.pvp.entity.projectile.Arrow;
import io.github.togar2.pvp.events.PotionVisibilityEvent;
import io.github.togar2.pvp.feature.FeatureType;
import io.github.togar2.pvp.feature.RegistrableFeature;
import io.github.togar2.pvp.feature.config.DefinedFeature;
import io.github.togar2.pvp.feature.config.FeatureConfiguration;
import io.github.togar2.pvp.feature.food.ExhaustionFeature;
import io.github.togar2.pvp.feature.food.FoodFeature;
import io.github.togar2.pvp.potion.effect.CombatPotionEffect;
import io.github.togar2.pvp.potion.effect.CombatPotionEffects;
import io.github.togar2.pvp.potion.item.CombatPotionType;
import io.github.togar2.pvp.potion.item.CombatPotionTypes;
import io.github.togar2.pvp.utils.CombatVersion;
import io.github.togar2.pvp.utils.PotionFlags;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.MinecraftServer;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.LivingEntityMeta;
import net.minestom.server.entity.metadata.other.SlimeMeta;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.event.entity.EntityPotionAddEvent;
import net.minestom.server.event.entity.EntityPotionRemoveEvent;
import net.minestom.server.event.entity.EntityTickEvent;
import net.minestom.server.event.trait.EntityInstanceEvent;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.item.component.PotionContents;
import net.minestom.server.particle.Particle;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.PotionType;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.tag.Tag;
import net.minestom.server.utils.time.TimeUnit;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Vanilla implementation of {@link EffectFeature}
 */
public class VanillaEffectFeature implements EffectFeature, RegistrableFeature {
	public static final DefinedFeature<VanillaEffectFeature> DEFINED = new DefinedFeature<>(
			FeatureType.EFFECT, VanillaEffectFeature::new,
			FeatureType.EXHAUSTION, FeatureType.FOOD, FeatureType.VERSION
	);

	public static final Tag<Map<PotionEffect, Integer>> DURATION_LEFT = Tag.Transient("effectDurationLeft");
	public static final int DEFAULT_POTION_COLOR = 0xff385dc6;
	private static final double WIND_CHARGED_MIN_POWER = 3.0;
	private static final double WIND_CHARGED_RANDOM_POWER = 2.0;
	private static final int OOZING_SLIME_COUNT = 2;
	private static final int OOZING_SLIME_SIZE = 2;
	private static final int OOZING_SLIME_CHECK_RADIUS = 2;
	private static final int DEFAULT_MAX_ENTITY_CRAMMING = 24;

	private final FeatureConfiguration configuration;

	private ExhaustionFeature exhaustionFeature;
	private FoodFeature foodFeature;
	private CombatVersion version;

	public VanillaEffectFeature(FeatureConfiguration configuration) {
		this.configuration = configuration;
	}

	@Override
	public void initDependencies() {
		this.exhaustionFeature = this.configuration.get(FeatureType.EXHAUSTION);
		this.foodFeature = this.configuration.get(FeatureType.FOOD);
		this.version = this.configuration.get(FeatureType.VERSION);
	}

	@Override
	public void init(EventNode<EntityInstanceEvent> node) {
		node.addListener(EntityDeathEvent.class, event -> {
			if (event.getEntity() instanceof LivingEntity entity) {
				this.applyWindChargedBurst(entity);
				this.spawnOozingSlimes(entity);
			}

			event.getEntity().clearEffects();
		});

		node.addListener(EntityTickEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;
			Map<PotionEffect, Integer> potionMap = this.getDurationLeftMap(entity);

			for (TimedPotion potion : entity.getActiveEffects()) {
				potionMap.putIfAbsent(potion.potion().effect(), potion.potion().duration() - 1);
				int durationLeft = potionMap.get(potion.potion().effect());

				if (durationLeft > 0) {
					CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.potion().effect());
					int amplifier = potion.potion().amplifier();

					if (combatPotionEffect.canApplyUpdateEffect(durationLeft, amplifier)) {
						combatPotionEffect.applyUpdateEffect(entity, amplifier, this.exhaustionFeature, this.foodFeature);
					}

					potionMap.put(potion.potion().effect(), durationLeft - 1);
				}
			}

			if (entity instanceof Player player && player.hasEffect(PotionEffect.ABSORPTION) && player.getAdditionalHearts() <= 0) {
				player.removeEffect(PotionEffect.ABSORPTION);
			}

			//TODO keep track of underlying potions with longer duration
			if (potionMap.size() != entity.getActiveEffects().size()) {
				potionMap.keySet().removeIf(effect -> !entity.hasEffect(effect));
			}
		});

		node.addListener(EntityPotionAddEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;
			var potion = event.getPotion();

			if (this.isImmuneToPotion(entity, potion.effect())) {
				event.setCancelled(true);
				return;
			}

			entity.scheduler().scheduleNextProcess(() -> {
				if (!this.hasActivePotion(entity, potion)) return;

				var potionMap = this.getDurationLeftMap(entity);
				var infinite = potion.duration() == Potion.INFINITE_DURATION;
				potionMap.put(potion.effect(), infinite ? Integer.MAX_VALUE : potion.duration());

				var combatPotionEffect = CombatPotionEffects.get(potion.effect());
				combatPotionEffect.onApplied(entity, potion.amplifier(), this.version);

				this.updatePotionVisibility(entity);
			});
		});

		node.addListener(EntityPotionRemoveEvent.class, event -> {
			if (!(event.getEntity() instanceof LivingEntity entity)) return;

			CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(event.getPotion().effect());
			combatPotionEffect.onRemoved(entity, event.getPotion().amplifier(), this.version);

			//Delay update 1 tick because we need to have the removing effect removed
			MinecraftServer.getSchedulerManager()
					.buildTask(() -> this.updatePotionVisibility(entity))
					.delay(1, TimeUnit.SERVER_TICK)
					.schedule();
		});
	}

	private Map<PotionEffect, Integer> getDurationLeftMap(Entity entity) {
		Map<PotionEffect, Integer> potionMap = entity.getTag(DURATION_LEFT);
		if (potionMap == null) {
			potionMap = new ConcurrentHashMap<>();
			entity.setTag(DURATION_LEFT, potionMap);
		}
		return potionMap;
	}

	private boolean hasActivePotion(LivingEntity entity, Potion potion) {
		return entity.getActiveEffects().stream()
				.anyMatch(timedPotion -> timedPotion.potion() == potion);
	}

	private boolean isImmuneToPotion(LivingEntity entity, PotionEffect potionEffect) {
		var entityType = entity.getEntityType();

		return (entityType == EntityType.SILVERFISH && potionEffect == PotionEffect.INFESTED)
				|| (entityType == EntityType.SLIME && potionEffect == PotionEffect.OOZING);
	}

	private void applyWindChargedBurst(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.WIND_CHARGED)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var random = ThreadLocalRandom.current();
		var position = entity.getPosition().add(0.0, entity.getBoundingBox().height() / 2.0F, 0.0);
		var power = WIND_CHARGED_MIN_POWER + random.nextDouble(WIND_CHARGED_RANDOM_POWER);

		entity.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_SMALL, false, false,
				position.x(), position.y(), position.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));
		entity.sendPacketToViewersAndSelf(new ParticlePacket(
				Particle.GUST_EMITTER_LARGE, false, false,
				position.x(), position.y(), position.z(),
				0.0F, 0.0F, 0.0F,
				0.0F, 1
		));
		entity.getViewersAsAudience().playSound(Sound.sound(
				SoundEvent.ENTITY_WIND_CHARGE_WIND_BURST,
				entity instanceof Player ? Sound.Source.PLAYER : Sound.Source.HOSTILE,
				1.0F, 1.0F
		), entity);

		for (var nearbyEntity : instance.getNearbyEntities(position, power)) {

			if (!(nearbyEntity instanceof LivingEntity nearbyLiving)) {
				continue;
			}

			var direction = nearbyEntity.getPosition().asVec().sub(position.asVec());
			var distance = direction.length();

			if (distance <= 0.0 || distance > power) {
				continue;
			}

			var knockback = (1.0 - distance / power) * power;
			var knockbackVector = direction.normalize().mul(knockback);
			var velocity = nearbyLiving.getVelocity();

			nearbyLiving.setVelocity(velocity.add(
					knockbackVector.x() * ServerFlag.SERVER_TICKS_PER_SECOND,
					Math.abs(knockbackVector.y() + 0.3) * ServerFlag.SERVER_TICKS_PER_SECOND,
					knockbackVector.z() * ServerFlag.SERVER_TICKS_PER_SECOND
			));
		}
	}

	private void spawnOozingSlimes(LivingEntity entity) {
		if (!entity.hasEffect(PotionEffect.OOZING)) return;

		var instance = entity.getInstance();

		if (instance == null) return;

		var nearbySlimes = instance.getNearbyEntities(entity.getPosition(), OOZING_SLIME_CHECK_RADIUS).stream()
				.filter(nearbyEntity -> nearbyEntity.getEntityType() == EntityType.SLIME)
				.limit(DEFAULT_MAX_ENTITY_CRAMMING)
				.count();
		var spawnCount = Math.clamp(DEFAULT_MAX_ENTITY_CRAMMING - (int) nearbySlimes, 0, OOZING_SLIME_COUNT);
		var random = ThreadLocalRandom.current();

		for (var slimeNumber = 0; slimeNumber < spawnCount; slimeNumber++) {
			var slime = new Entity(EntityType.SLIME);

			if (slime.getEntityMeta() instanceof SlimeMeta slimeMeta) {
				slimeMeta.setSize(OOZING_SLIME_SIZE);
			}

			var position = new Pos(entity.getPosition().x(), entity.getPosition().y() + 0.5, entity.getPosition().z(), random.nextFloat(360.0F), 0.0F);
			slime.setInstance(instance, position);
		}
	}

	@Override
	public int getPotionColor(PotionContents contents) {
		if (contents.customColor() != null) {
			RGBLike rgbLike = contents.customColor();
			return PotionColorUtils.rgba(255, rgbLike.red(), rgbLike.green(), rgbLike.blue());
		} else if (contents.equals(PotionContents.EMPTY)) {
			return DEFAULT_POTION_COLOR;
		} else {
			Collection<Potion> effects = this.getAllPotions(contents);
			int color = PotionColorUtils.getPotionColor(effects);
			return color == -1 ? DEFAULT_POTION_COLOR : color;
		}
	}

	@Override
	public List<Potion> getAllPotions(PotionType potionType,
	                                  Collection<net.minestom.server.potion.CustomPotionEffect> customEffects) {
		// PotionType effects plus custom effects
		List<Potion> potions = new ArrayList<>();

		CombatPotionType combatPotionType = CombatPotionTypes.get(potionType);
		if (combatPotionType != null) potions.addAll(combatPotionType.getEffects(this.version));

		potions.addAll(customEffects.stream().map((customPotion) ->
				new Potion(Objects.requireNonNull(customPotion.id()),
						(byte)customPotion.amplifier(), customPotion.duration(),
						PotionFlags.create(
								customPotion.isAmbient(),
								customPotion.showParticles(),
								customPotion.showIcon()
						))).toList());

		return potions;
	}

	@Override
	public void updatePotionVisibility(LivingEntity entity) {
		boolean ambient;
		List<Particle> particles;
		boolean invisible;

		if (entity instanceof Player player && player.getGameMode() == GameMode.SPECTATOR) {
			ambient = false;
			particles = List.of();
			invisible = true;
		} else {
			Collection<TimedPotion> effects = entity.getActiveEffects();
			if (effects.isEmpty()) {
				ambient = false;
				particles = List.of();
				invisible = false;
			} else {
				ambient = true;
				particles = new ArrayList<>();

				for (TimedPotion potion : effects) {
					if (!potion.potion().isAmbient()) {
						ambient = false;
					}

					if (potion.potion().hasParticles()) {
						CombatPotionEffect effect = CombatPotionEffects.get(potion.potion().effect());
						particles.add(effect.getParticle(potion.potion()));
					}
				}

				invisible = entity.hasEffect(PotionEffect.INVISIBILITY);
			}
		}

		PotionVisibilityEvent potionVisibilityEvent = new PotionVisibilityEvent(entity, ambient, particles, invisible);
		EventDispatcher.callCancellable(potionVisibilityEvent, () -> {
			LivingEntityMeta meta = (LivingEntityMeta) entity.getEntityMeta();

			meta.setPotionEffectAmbient(potionVisibilityEvent.isAmbient());
			meta.setEffectParticles(potionVisibilityEvent.getParticles());
			meta.setInvisible(potionVisibilityEvent.isInvisible());
		});
	}

	@Override
	public void addArrowEffects(LivingEntity entity, Arrow arrow) {
		PotionContents potionContents = arrow.getPotion();

		CombatPotionType combatPotionType = CombatPotionTypes.get(potionContents.potion());
		if (combatPotionType != null) {
			for (Potion potion : combatPotionType.getEffects(this.version)) {
				CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
				if (combatPotionEffect.isInstant()) {
					combatPotionEffect.applyInstantEffect(arrow, null,
							entity, potion.amplifier(), 1.0, this.exhaustionFeature, this.foodFeature);
				} else {
					int duration = Math.max(potion.duration() / 8, 1);
					entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
				}
			}
		}

		if (potionContents.customEffects().isEmpty()) return;

		potionContents.customEffects().stream().map(customPotion ->
						new Potion(Objects.requireNonNull(customPotion.id()),
								(byte)customPotion.amplifier(), customPotion.duration(),
								PotionFlags.create(
										customPotion.isAmbient(),
										customPotion.showParticles(),
										customPotion.showIcon()
								)))
				.forEach(potion -> {
					CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
					if (combatPotionEffect.isInstant()) {
						combatPotionEffect.applyInstantEffect(arrow, null,
								entity, potion.amplifier(), 1.0, this.exhaustionFeature, this.foodFeature);
					} else {
						var duration = Math.max(potion.duration() / 8, 1);
						entity.addEffect(new Potion(potion.effect(), potion.amplifier(),
								duration, potion.flags()));
					}
				});
	}

	@Override
	public void addSplashPotionEffects(LivingEntity entity, PotionContents potionContents, double proximity,
	                                   @Nullable Entity source, @Nullable Entity attacker) {
		for (Potion potion : this.getAllPotions(potionContents)) {
			CombatPotionEffect combatPotionEffect = CombatPotionEffects.get(potion.effect());
			if (combatPotionEffect.isInstant()) {
				combatPotionEffect.applyInstantEffect(source, attacker,
						entity, potion.amplifier(), proximity, this.exhaustionFeature, this.foodFeature);
			} else {
				int duration = potion.duration();
				if (this.version.legacy()) duration = (int) Math.floor(duration * 0.75);
				duration = (int) (proximity * (double) duration + 0.5);

				if (duration > 20) {
					entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
				}
			}
		}
	}

	@Override
	public void addLingeringPotionEffects(LivingEntity entity, PotionContents potionContents,
	                                      @Nullable Entity source, @Nullable Entity attacker) {
		for (var potion : this.getAllPotions(potionContents)) {
			var combatPotionEffect = CombatPotionEffects.get(potion.effect());

			if (combatPotionEffect.isInstant()) {
				combatPotionEffect.applyInstantEffect(source, attacker,
						entity, potion.amplifier(), 0.5, this.exhaustionFeature, this.foodFeature);
			} else {
				var duration = Math.max(potion.duration() / 4, 1);
				entity.addEffect(new Potion(potion.effect(), potion.amplifier(), duration, potion.flags()));
			}
		}
	}
}
