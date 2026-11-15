package com.garthora.endvillage;

import com.garthora.endvillage.entity.ShulkerVillager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(EndVillageMod.MODID)
public class EndVillageMod {
    public static final String MODID = "endvillage";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MODID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MODID);

    public static final RegistryObject<EntityType<ShulkerVillager>> SHULKER_VILLAGER =
            ENTITY_TYPES.register("shulker_villager", () -> EntityType.Builder
                    .<ShulkerVillager>of(ShulkerVillager::new, MobCategory.MISC)
                    .fireImmune()
                    .sized(1.0F, 1.0F)
                    .clientTrackingRange(10)
                    .build("shulker_villager"));

    public static final RegistryObject<Item> SHULKER_VILLAGER_SPAWN_EGG =
            ITEMS.register("shulker_villager_spawn_egg",
                    () -> new ForgeSpawnEggItem(SHULKER_VILLAGER, 0x946794, 0x7FCC19, new Item.Properties()));

    public EndVillageMod() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ENTITY_TYPES.register(bus);
        ITEMS.register(bus);
        bus.addListener(this::onAttributes);
        bus.addListener(this::onCreativeTabs);
    }

    private void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SHULKER_VILLAGER.get(), Shulker.createAttributes().build());
    }

    private void onCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(SHULKER_VILLAGER_SPAWN_EGG);
        }
    }
}
