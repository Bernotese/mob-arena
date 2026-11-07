package dev.mobarena.collect;
import java.util.Set;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SpawnEggItem;
public final class MobEligibility {
    private MobEligibility() {}
    public static boolean counts(LivingEntity entity, Set<String> blacklist) {
        return !(entity instanceof Player) && !blacklist.contains(EntityType.getKey(entity.getType()).toString())
                && SpawnEggItem.byId(entity.getType()).isPresent();
    }
}
