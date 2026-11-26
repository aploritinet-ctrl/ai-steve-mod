package com.example.aisteve;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("aisteve")
public class AiSteveMod {
    public static final String ID = "aisteve";
    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, ID);

    public static final RegistryObject<EntityType<AiSteveEntity>> AI_STEVE = ENTITIES.register("ai_steve",
            () -> EntityType.Builder.of(AiSteveEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(ID, "ai_steve"))));

    public AiSteveMod(FMLJavaModLoadingContext ctx) {
        ENTITIES.register(ctx.getModBusGroup());
        EntityAttributeCreationEvent.BUS.addListener(e ->
                e.put(AI_STEVE.get(), AiSteveEntity.createAttributes().build()));
        if (FMLEnvironment.dist.isClient()) ClientHandler.init();
    }
}
