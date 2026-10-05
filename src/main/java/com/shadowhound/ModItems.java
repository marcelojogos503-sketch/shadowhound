package com.shadowhound;

import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ShadowHoundMod.MODID);

    public static final RegistryObject<Item> HOUND_EGG = ITEMS.register("shadow_hound_spawn_egg",
            () -> new ForgeSpawnEggItem(ModEntities.HOUND, 0x1A1A1A, 0x8B0000, new Item.Properties()));
}
