package dev.mobarena.wave;
import java.util.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
public final class EggIcons {
    private static final Map<String,ItemStack> CACHE=new HashMap<>();
    private EggIcons() {}
    public static EntityType<?> type(String id) { return BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id)); }
    public static ItemStack icon(String id) {
        return CACHE.computeIfAbsent(id,key->SpawnEggItem.byId(type(key)).map(ItemStack::new)
                .orElseGet(()->new ItemStack(Items.BARRIER))).copy();
    }
}
