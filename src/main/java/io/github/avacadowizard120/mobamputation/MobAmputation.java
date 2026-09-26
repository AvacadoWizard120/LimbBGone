package io.github.avacadowizard120.mobamputation;

import io.github.avacadowizard120.mobamputation.config.MobAmputationConfig;
import io.github.avacadowizard120.mobamputation.content.MobAmputationContent;
import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;

// Quilt Loader exposes Fabric's entrypoint API for compatibility.
//? if fabric || quilt {
import net.fabricmc.api.ModInitializer;
//?}

public final class MobAmputation
        //? if fabric || quilt {
        implements ModInitializer
        //?}
{
    public static final String MOD_ID = "mobamputation";

    private static boolean initialized;

    //? if fabric || quilt {
    @Override
    //?}
    public void onInitialize() {
        initialize();
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        MobAmputationConfig.load();
        // Forge/NeoForge content is registered on their mod event bus before
        // common initialization; direct vanilla registry writes are only for
        // Fabric-compatible loaders.
        //? if fabric || quilt {
        MobAmputationContent.initializeCommon();
        //?}
        MobAmputationNetworking.initializeCommon();
        initialized = true;
    }

    public MobAmputation() {
    }
}
