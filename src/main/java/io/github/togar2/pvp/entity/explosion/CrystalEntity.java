package io.github.togar2.pvp.entity.explosion;

import org.jetbrains.annotations.NotNull;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.metadata.other.EndCrystalMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public class CrystalEntity extends LivingEntity {
	private final boolean fire;

	public CrystalEntity(boolean fire, boolean showingBottom) {
		super(EntityType.END_CRYSTAL);
		this.fire = fire;
        this.setNoGravity(true);
        this.hasPhysics = false;
		((EndCrystalMeta) this.getEntityMeta()).setShowingBottom(showingBottom);
	}

	public CrystalEntity() {
		this(false, false);
	}

	@Override
	protected boolean shouldSendAttributes() {
		return false;
	}

	@Override
	public void update(long time) {
		if (this.fire && !this.instance.getBlock(this.position).compare(Block.FIRE))
            this.instance.setBlock(this.position, Block.FIRE);
	}

	@Override
	public boolean damage(@NotNull Damage damage) {
		if (this.isDead() || this.isRemoved())
			return false;
		if (this.isInvulnerable() || this.isImmune(damage.getType())) {
			return false;
		}

		// Set the last damage type since the event is not canceled
		this.lastDamage = damage;

		// Save this.instance locally
		Instance instance = this.instance;
        this.remove();
		if (instance.getExplosionSupplier() != null
				&& !DamageTypeInfo.of(damage.getType()).explosive()) {
			instance.explode((float) this.position.x(), (float) this.position.y(), (float) this.position.z(), 6.0f);
		}

		return true;
	}
}
