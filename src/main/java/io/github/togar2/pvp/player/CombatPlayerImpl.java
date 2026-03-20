package io.github.togar2.pvp.player;

import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.collision.PhysicsUtils;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.entity.EntityVelocityEvent;
import net.minestom.server.instance.Chunk;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.utils.chunk.ChunkUtils;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public class CombatPlayerImpl extends Player implements CombatPlayer {
	private boolean velocityUpdate = false;
	private PhysicsResult previousPhysicsResult = null;

	public CombatPlayerImpl(@NotNull PlayerConnection playerConnection, GameProfile profile) {
		super(playerConnection, profile);

		// Default value is 2.0, but base value is 1.0 for players in vanilla
		// This is difficult to implement as a feature and assumed everyone using
		// this extension would want it to match vanilla
        this.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(1.0);
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
		PhysicsResult prevPhysicsResult = this.previousPhysicsResult;
		for (int i = 0; i < ticks; i++) {
			PhysicsResult physicsResult = PhysicsUtils.simulateMovement(position, velocity, this.boundingBox,
                    this.instance.getWorldBorder(), this.instance, aerodynamics, this.hasNoGravity(), this.hasPhysics, this.onGround, this.isFlying(), prevPhysicsResult);
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
		if (this.vehicle != null) return;

		final double tps = ServerFlag.SERVER_TICKS_PER_SECOND;

		// Slow falling effect
		Aerodynamics aerodynamics = this.getAerodynamics();
		if (this.velocity.y() < 0 && this.hasEffect(PotionEffect.SLOW_FALLING))
			aerodynamics = aerodynamics.withGravity(0.01);

		PhysicsResult physicsResult = PhysicsUtils.simulateMovement(this.position, this.velocity.div(tps), this.boundingBox,
                this.instance.getWorldBorder(), this.instance, aerodynamics, this.hasNoGravity(), this.hasPhysics, this.onGround, this.isFlying(), this.previousPhysicsResult);
		this.previousPhysicsResult = physicsResult;

		Chunk finalChunk = ChunkUtils.retrieve(this.instance, this.currentChunk, physicsResult.newPosition());
		if (!ChunkUtils.isLoaded(finalChunk)) return;

        this.velocity = physicsResult.newVelocity().mul(tps);
		//onGround = physicsResult.isOnGround();

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
}
