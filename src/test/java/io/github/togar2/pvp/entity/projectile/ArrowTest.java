package io.github.togar2.pvp.entity.projectile;

import io.github.togar2.pvp.enchantment.CombatEnchantments;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.testing.Env;
import net.minestom.testing.EnvTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnvTest
public final class ArrowTest {
    @Test
    public void piercingArrowHitsSeveralTargetsInOneTick(Env env) {
        CombatEnchantments.registerAll();
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_EFFECT)
                .build();

        var instance = env.createFlatInstance();
        var first = new LivingEntity(EntityType.ZOMBIE);
        first.setInstance(instance, new Pos(8.0, 40.0, 7.5)).join();
        first.setHealth(20.0F);
        var second = new LivingEntity(EntityType.ZOMBIE);
        second.setInstance(instance, new Pos(8.0, 40.0, 8.5)).join();
        second.setHealth(20.0F);

        var arrow = new Arrow(null, featureSet.get(FeatureType.EFFECT), featureSet.get(FeatureType.ENCHANTMENT));
        arrow.setPiercingLevel((byte) 1);
        arrow.setInstance(instance, new Pos(8.0, 41.0, 6.0)).join();
        arrow.setVelocity(new Vec(0.0, 0.0, 3.0 * ServerFlag.SERVER_TICKS_PER_SECOND));

        env.tick();

        assertTrue(first.getHealth() < 20.0F, "first target health " + first.getHealth());
        assertTrue(second.getHealth() < 20.0F, "second target health " + second.getHealth());
    }

    @Test
    public void arrowKeepsItsHeadingAcrossTheYawWrap(Env environment) {
        CombatEnchantments.registerAll();
        var featureSet = CombatFeatures.empty()
                .add(CombatFeatures.VANILLA_ENCHANTMENT)
                .add(CombatFeatures.VANILLA_EFFECT)
                .build();

        var instance = environment.createFlatInstance();
        var arrow = new Arrow(null, featureSet.get(FeatureType.EFFECT), featureSet.get(FeatureType.ENCHANTMENT));
        arrow.setInstance(instance, new Pos(8.0, 60.0, 8.0)).join();
        arrow.shootFromRotation(0.0F, 180.0F, 0.0F, 3.0, 0.0);
        arrow.setVelocity(arrow.getVelocity().add(0.3 * ServerFlag.SERVER_TICKS_PER_SECOND, 0.0, 0.0));

        for (var tick = 0; tick < 6; tick++) {
            environment.tick();
            var yaw = arrow.getPosition().yaw();
            assertTrue(Math.abs(Math.abs(yaw) - 180.0F) < 10.0F, "tick " + tick + " yaw " + yaw);
        }
    }
}
