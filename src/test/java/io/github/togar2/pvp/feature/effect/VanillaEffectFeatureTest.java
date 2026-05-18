package io.github.togar2.pvp.feature.effect;

import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.potion.effect.CombatPotionEffects;
import io.github.togar2.pvp.utils.PotionFlags;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.event.EventNode;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class VanillaEffectFeatureTest {
    @Test
    public void windChargedKnocksBackNonLivingEntities(Env env) {
        var node = this.addEffectFeature();

        try {
            var instance = env.createFlatInstance();
            var entity = this.createLivingEntity(instance);
            var item = new ItemEntity(ItemStack.of(Material.STONE));
            item.setInstance(instance, new Pos(1.0, 40.0, 0.0)).join();
            entity.addEffect(new Potion(PotionEffect.WIND_CHARGED, 0, 100, PotionFlags.defaultFlags()));

            entity.kill();

            assertTrue(item.getVelocity().lengthSquared() > 0.0);
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    @Test
    public void oozingRespectsMaxEntityCramming(Env env) {
        var node = this.addEffectFeature();

        try {
            var instance = env.createFlatInstance();

            for (var slimeNumber = 0; slimeNumber < 24; slimeNumber++) {
                var slime = new Entity(EntityType.SLIME);
                slime.setInstance(instance, new Pos(0.0, 40.0, 0.0)).join();
            }

            var entity = this.createLivingEntity(instance);
            entity.addEffect(new Potion(PotionEffect.OOZING, 0, 100, PotionFlags.defaultFlags()));

            entity.kill();

            assertEquals(24, this.countEntities(instance, EntityType.SLIME));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(node);
        }
    }

    private EventNode<?> addEffectFeature() {
        CombatPotionEffects.registerAll();

        var node = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_EFFECT)
                .build()
                .createNode();
        MinecraftServer.getGlobalEventHandler().addChild(node);
        return node;
    }

    private LivingEntity createLivingEntity(Instance instance) {
        var entity = new LivingEntity(EntityType.ZOMBIE);
        entity.setInstance(instance, new Pos(0.0, 40.0, 0.0)).join();
        return entity;
    }

    private long countEntities(Instance instance, EntityType entityType) {
        return instance.getEntities().stream()
                .filter(entity -> entity.getEntityType() == entityType)
                .count();
    }
}
