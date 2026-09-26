package io.github.avacadowizard120.mobamputation.content;

import io.github.avacadowizard120.mobamputation.MobAmputation;
import io.github.avacadowizard120.mobamputation.effect.BleedingEffect;
import io.github.avacadowizard120.mobamputation.effect.LimbRegrowthEffect;
import io.github.avacadowizard120.mobamputation.item.BandageItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

//? if fabric || quilt {
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
//?}
//? if forge {
/*import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.brewing.BrewingRecipeRegisterEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.item.CreativeModeTabs;
*///?}
//? if neoforge {
/*import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.world.item.CreativeModeTabs;
*///?}

/** Loader-neutral accessors plus the small loader-specific registration seam. */
public final class MobAmputationContent {
    //? if forge {
    /*private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.ITEMS, MobAmputation.MOD_ID
    );
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS, MobAmputation.MOD_ID
    );
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(
            net.minecraftforge.registries.ForgeRegistries.POTIONS, MobAmputation.MOD_ID
    );
    private static final RegistryObject<BandageItem> FORGE_BANDAGE = ITEMS.register(
            "bandage", () -> new BandageItem(new Item.Properties().stacksTo(16))
    );
    private static final RegistryObject<MobEffect> FORGE_BLEEDING = EFFECTS.register(
            "bleeding", BleedingEffect::new
    );
    private static final RegistryObject<MobEffect> FORGE_LIMB_REGROWTH_EFFECT = EFFECTS.register(
            "limb_regrowth", LimbRegrowthEffect::new
    );
    private static final RegistryObject<Potion> FORGE_LIMB_REGROWTH_POTION = POTIONS.register(
            "limb_regrowth",
            () -> new Potion("limb_regrowth", new MobEffectInstance(
                    FORGE_LIMB_REGROWTH_EFFECT.getHolder().orElseThrow()
            ))
    );
    *///?}
    //? if neoforge {
    /*private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MobAmputation.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.MOB_EFFECT, MobAmputation.MOD_ID
    );
    private static final DeferredRegister<Potion> POTIONS = DeferredRegister.create(
            net.minecraft.core.registries.Registries.POTION, MobAmputation.MOD_ID
    );
    private static final DeferredItem<BandageItem> NEO_BANDAGE = ITEMS.register(
            "bandage", () -> new BandageItem(new Item.Properties().stacksTo(16))
    );
    private static final DeferredHolder<MobEffect, BleedingEffect> NEO_BLEEDING = EFFECTS.register(
            "bleeding", BleedingEffect::new
    );
    private static final DeferredHolder<MobEffect, LimbRegrowthEffect> NEO_LIMB_REGROWTH_EFFECT = EFFECTS.register(
            "limb_regrowth", LimbRegrowthEffect::new
    );
    private static final DeferredHolder<Potion, Potion> NEO_LIMB_REGROWTH_POTION = POTIONS.register(
            "limb_regrowth",
            () -> new Potion("limb_regrowth", new MobEffectInstance(NEO_LIMB_REGROWTH_EFFECT))
    );
    *///?}
    //? if fabric || quilt {
    private static BandageItem bandage;
    private static Holder.Reference<MobEffect> bleeding;
    private static Holder.Reference<MobEffect> limbRegrowthEffect;
    private static Holder.Reference<Potion> limbRegrowthPotion;
    //?}

    public static void initializeCommon() {
        //? if fabric || quilt {
        if (bandage != null) {
            return;
        }
        bandage = Registry.register(
                BuiltInRegistries.ITEM,
                id("bandage"),
                new BandageItem(new Item.Properties().stacksTo(16))
        );
        bleeding = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, id("bleeding"), new BleedingEffect());
        limbRegrowthEffect = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT, id("limb_regrowth"), new LimbRegrowthEffect()
        );
        limbRegrowthPotion = Registry.registerForHolder(
                BuiltInRegistries.POTION,
                id("limb_regrowth"),
                new Potion("limb_regrowth", new MobEffectInstance(limbRegrowthEffect))
        );
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> builder.addMix(
                Potions.AWKWARD, Items.GOLDEN_APPLE, limbRegrowthPotion
        ));
        registerFabricClientContent();
        //?}
    }

    //? if fabric || quilt {
    private static void registerFabricClientContent() {
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FOOD_AND_DRINKS)
                    .register(entries -> entries.accept(bandage));
        }
    }
    //?}

    //? if forge {
    /*public static void initializeForge() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        EFFECTS.register(bus);
        POTIONS.register(bus);
        bus.addListener(MobAmputationContent::addForgeCreativeItem);
        MinecraftForge.EVENT_BUS.addListener(MobAmputationContent::registerForgeBrewing);
    }

    private static void addForgeCreativeItem(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(FORGE_BANDAGE);
        }
    }

    private static void registerForgeBrewing(BrewingRecipeRegisterEvent event) {
        event.getBuilder().addMix(
                Potions.AWKWARD,
                Items.GOLDEN_APPLE,
                FORGE_LIMB_REGROWTH_POTION.getHolder().orElseThrow()
        );
    }
    *///?}

    //? if neoforge {
    /*public static void initializeNeoForge(IEventBus bus) {
        ITEMS.register(bus);
        EFFECTS.register(bus);
        POTIONS.register(bus);
        bus.addListener(MobAmputationContent::addNeoForgeCreativeItem);
        NeoForge.EVENT_BUS.addListener(MobAmputationContent::registerNeoForgeBrewing);
    }

    private static void addNeoForgeCreativeItem(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FOOD_AND_DRINKS) {
            event.accept(NEO_BANDAGE.get());
        }
    }

    private static void registerNeoForgeBrewing(RegisterBrewingRecipesEvent event) {
        event.getBuilder().addMix(Potions.AWKWARD, Items.GOLDEN_APPLE, NEO_LIMB_REGROWTH_POTION);
    }
    *///?}

    public static BandageItem bandage() {
        //? if fabric || quilt {
        return bandage;
        //?}
        //? if forge {
        /*return FORGE_BANDAGE.get();*/
        //?}
        //? if neoforge {
        /*return NEO_BANDAGE.get();*/
        //?}
    }

    public static Holder<MobEffect> bleeding() {
        //? if fabric || quilt {
        return bleeding;
        //?}
        //? if forge {
        /*return FORGE_BLEEDING.getHolder().orElseThrow();*/
        //?}
        //? if neoforge {
        /*return NEO_BLEEDING;*/
        //?}
    }

    public static Holder<MobEffect> limbRegrowthEffect() {
        //? if fabric || quilt {
        return limbRegrowthEffect;
        //?}
        //? if forge {
        /*return FORGE_LIMB_REGROWTH_EFFECT.getHolder().orElseThrow();*/
        //?}
        //? if neoforge {
        /*return NEO_LIMB_REGROWTH_EFFECT;*/
        //?}
    }

    public static Holder<Potion> limbRegrowthPotion() {
        //? if fabric || quilt {
        return limbRegrowthPotion;
        //?}
        //? if forge {
        /*return FORGE_LIMB_REGROWTH_POTION.getHolder().orElseThrow();*/
        //?}
        //? if neoforge {
        /*return NEO_LIMB_REGROWTH_POTION;*/
        //?}
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MobAmputation.MOD_ID, path);
    }

    private MobAmputationContent() {
    }
}
