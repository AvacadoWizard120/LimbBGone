package io.github.avacadowizard120.mobamputation.client;

/** Loader-specific mod-list integration for the config screen. */
public final class MobAmputationConfigScreenRegistration {
    //? if forge {
    /*public static void registerForge() {
        net.minecraftforge.fml.ModList.get().getModContainerById(
                io.github.avacadowizard120.mobamputation.MobAmputation.MOD_ID
        ).ifPresent(container -> container.registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                    (minecraft, parent) -> new MobAmputationConfigScreen(parent)
            )
        ));
    }
    *///?}

    //? if neoforge {
    /*public static void registerNeoForge() {
        net.neoforged.neoforge.client.gui.IConfigScreenFactory factory =
                (container, parent) -> new MobAmputationConfigScreen(parent);
        net.neoforged.fml.ModLoadingContext.get().getActiveContainer().registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                factory
        );
    }
    *///?}

    private MobAmputationConfigScreenRegistration() {
    }
}
