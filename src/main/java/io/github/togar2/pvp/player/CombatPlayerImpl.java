package io.github.togar2.pvp.player;

import io.github.togar2.pvp.utils.ViewUtil;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.DamageType;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityVelocityEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.utils.chunk.ChunkCache;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class CombatPlayerImpl extends Player implements CombatPlayer {
	private boolean velocityUpdate = false;
	private boolean horizontalCollision = false;
	private PhysicsResult previousPhysicsResult = null;

	public CombatPlayerImpl(@NotNull PlayerConnection playerConnection, GameProfile profile) {
		super(playerConnection, profile);
	}

	@Override
	public void setVelocity(@NotNull Vec velocity) {
		EntityVelocityEvent entityVelocityEvent = new EntityVelocityEvent(this, velocity);
		EventDispatcher.callCancellable(entityVelocityEvent, () -> {
			this.velocity = entityVelocityEvent.getVelocity();
            this.velocityUpdate = true;
		});
	}

	@Override
	public void setVelocityNoUpdate(Function<Vec, Vec> function) {
        this.velocity = function.apply(this.velocity);
	}

	@Override
	public void sendImmediateVelocityUpdate() {
		if (this.velocityUpdate) {
            this.velocityUpdate = false;
            this.sendPacketToViewersAndSelf(this.getVelocityPacket());
		}
	}

	@Override
	public boolean hasHorizontalCollision() {
		return this.horizontalCollision;
	}

	public boolean isOnGroundAfterTicks(int ticks) {
		if (this.vehicle != null) return false;

		final double tps = ServerFlag.SERVER_TICKS_PER_SECOND;
		Vec velocity = this.velocity.div(tps);
		Pos position = this.position;

		// Slow falling effect
		Aerodynamics aerodynamics = this.getAerodynamics();
		if (velocity.y() < 0 && this.hasEffect(PotionEffect.SLOW_FALLING))
			aerodynamics = aerodynamics.withGravity(0.01);

		// Do movementTick() calculations for the given amount of ticks
		var prevPhysicsResult = this.previousPhysicsResult;
		for (var tick = 0; tick < ticks; tick++) {
			var blockGetter = new ChunkCache(this.instance, this.currentChunk, Block.STONE);
			var physicsResult = PhysicsUtils.simulateMovement(position, velocity, this.boundingBox,
                    this.instance.getWorldBorder(), blockGetter, aerodynamics, this.hasNoGravity(), this.hasPhysics, this.onGround, this.isFlying(), prevPhysicsResult);
			prevPhysicsResult = physicsResult;

			if (physicsResult.isOnGround()) return true;

			velocity = physicsResult.newVelocity();
			position = physicsResult.newPosition();

			// Levitation effect
			TimedPotion levitation = this.getEffect(PotionEffect.LEVITATION);
			if (levitation != null) {
				velocity = velocity.withY(
						((0.05 * (double) (levitation.potion().amplifier() + 1) - (velocity.y())) * 0.2)
				);
			}
		}

		return false;
	}

	@Override
	protected void movementTick() {
		this.gravityTickCount = this.onGround ? 0 : this.gravityTickCount + 1;
		this.horizontalCollision = false;
		if (this.vehicle != null) return;

		final double tps = ServerFlag.SERVER_TICKS_PER_SECOND;

		// Slow falling effect
		Aerodynamics aerodynamics = this.getAerodynamics();
		if (this.velocity.y() < 0 && this.hasEffect(PotionEffect.SLOW_FALLING))
			aerodynamics = aerodynamics.withGravity(0.01);

		var blockGetter = new ChunkCache(this.instance, this.currentChunk, Block.STONE);
		var physicsResult = PhysicsUtils.simulateMovement(this.position, this.velocity.div(tps), this.boundingBox,
                this.instance.getWorldBorder(), blockGetter, aerodynamics, this.hasNoGravity(), this.hasPhysics, this.onGround, this.isFlying(), this.previousPhysicsResult);
		this.previousPhysicsResult = physicsResult;

		var finalChunk = ChunkUtils.retrieve(this.instance, this.currentChunk, physicsResult.newPosition());
		if (!ChunkUtils.isLoaded(finalChunk)) return;

		this.horizontalCollision = physicsResult.collisionX() || physicsResult.collisionZ();
		var oldHorizontalSpeed = this.velocity.div(tps).withY(0.0).length();
		var newHorizontalSpeed = physicsResult.newVelocity().withY(0.0).length();
        this.velocity = physicsResult.newVelocity().mul(tps);
		//onGround = physicsResult.isOnGround();

		this.handleFallFlyingCollision(physicsResult, oldHorizontalSpeed, newHorizontalSpeed);

		// Levitation effect
		TimedPotion levitation = this.getEffect(PotionEffect.LEVITATION);
		if (levitation != null) {
            this.velocity = this.velocity.withY(
					((0.05 * (double)
							(levitation.potion().amplifier() + 1)
							- (this.velocity.y() / tps)) * 0.2) * tps
			);
		}

		//TODO
		//if (!PlayerUtils.isSocketClient(this)) {
		//	refreshPosition(physicsResult.newPosition(), true, true);
		//}
        this.sendImmediateVelocityUpdate();
	}

	private void handleFallFlyingCollision(PhysicsResult physicsResult, double oldHorizontalSpeed, double newHorizontalSpeed) {
		if (!this.isFlyingWithElytra()) return;
		if (!physicsResult.collisionX() && !physicsResult.collisionZ()) return;

		var speedDifference = oldHorizontalSpeed - newHorizontalSpeed;
		var damage = (float) (speedDifference * 10.0 - 3.0);
		if (!(damage > 0.0F)) return;

		ViewUtil.viewersAndSelf(this).playSound(Sound.sound(
				damage > 4.0F ? SoundEvent.ENTITY_PLAYER_BIG_FALL : SoundEvent.ENTITY_PLAYER_SMALL_FALL,
				Sound.Source.PLAYER,
				1.0F,
				1.0F
		), this);

		this.damage(DamageType.FLY_INTO_WALL, damage);
	}
}
