package io.github.togar2.pvp.entity.explosion;

import org.jetbrains.annotations.NotNull;

import io.github.togar2.pvp.damage.DamageTypeInfo;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.entity.metadata.other.EndCrystalMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;

public class CrystalEntity extends Entity {
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
	public void update(long time) {
		if (this.fire && !this.instance.getBlock(this.position).compare(Block.FIRE))
            this.instance.setBlock(this.position, Block.FIRE);
	}

	public boolean damage(@NotNull Damage damage) {
		if (this.isRemoved()) return false;

		// Save this.instance locally
		Instance instance = this.instance;
		var attacker = damage.getAttacker();
		if (instance.getExplosionSupplier() != null && !DamageTypeInfo.of(damage.getType()).explosive()) {
			var additionalData = CompoundBinaryTag.builder()
					.putString("sourceEntity", this.getUuid().toString());

			if (attacker != null) {
				additionalData.putString("causingEntity", attacker.getUuid().toString());
			}

			instance.explode((float) this.position.x(), (float) this.position.y(), (float) this.position.z(), 6.0f,
					additionalData.build());
		}

		this.remove();

		return true;
	}
}
