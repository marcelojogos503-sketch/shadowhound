package com.shadowhound;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ShadowHoundMod.MODID);

    public static final RegistryObject<EntityType<HoundEntity>> HOUND = ENTITIES.register("shadow_hound",
            () -> EntityType.Builder.of(HoundEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 0.85F)
                    .clientTrackingRange(10)
                    .build("shadow_hound"));
}
