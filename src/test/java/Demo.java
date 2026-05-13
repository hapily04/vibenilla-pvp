import io.github.togar2.pvp.MinestomPvP;
import io.github.togar2.pvp.feature.CombatFeatures;
import io.github.togar2.pvp.feature.FeatureType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentEnum;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.FireworkExplosion;
import net.minestom.server.item.component.FireworkList;

import java.util.List;

void main() {
    var server = MinecraftServer.init();
    var instance = MinecraftServer.getInstanceManager().createInstanceContainer();
    instance.setExplosionSupplier(CombatFeatures.modernVanilla().get(FeatureType.EXPLOSION).getExplosionSupplier());

    MinecraftServer.getGlobalEventHandler()
            .addChild(MinestomPvP.events())
            .addListener(AsyncPlayerConfigurationEvent.class, event -> {
                var spawnPosition = new Pos(211.5D, 61.0D, 162.5D, -90.0F, 0.0F);
                event.setSpawningInstance(instance);
                event.getPlayer().setRespawnPoint(spawnPosition);
            })
            .addListener(PlayerSpawnEvent.class, event -> {
                var player = event.getPlayer();
                player.setGameMode(GameMode.CREATIVE);
                player.getInventory().addItemStack(ItemStack.of(Material.CROSSBOW));
                player.setEquipment(EquipmentSlot.OFF_HAND, createExplodingFireworkRocket());
            });

    MinecraftServer.getCommandManager().register(new Command("gamemode") {{
        var argument = ArgumentType.Enum("mode", GameMode.class).setFormat(ArgumentEnum.Format.LOWER_CASED);

        this.addSyntax((sender, context) -> {
            var player = (Player) sender;
            player.setGameMode(context.get(argument));
        }, argument);
    }});

    MinestomPvP.init();
    server.start("0.0.0.0", 25566);
}

private static ItemStack createExplodingFireworkRocket() {
    var explosion = new FireworkExplosion(
            FireworkExplosion.Shape.LARGE_BALL,
            List.of(new Color(255, 60, 45)),
            List.of(new Color(255, 220, 80)),
            true,
            true
    );
    var fireworks = new FireworkList(1, List.of(explosion));

    return ItemStack.of(Material.FIREWORK_ROCKET).with(DataComponents.FIREWORKS, fireworks);
}