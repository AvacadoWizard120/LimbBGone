package io.github.avacadowizard120.mobamputation;

//? if forge {
/*import net.minecraftforge.fml.common.Mod;*/
//?}
//? if neoforge {
/*import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
*///?}

import io.github.avacadowizard120.mobamputation.network.MobAmputationNetworking;
import io.github.avacadowizard120.mobamputation.content.MobAmputationContent;

//? if forge {
/*@Mod(MobAmputation.MOD_ID)*/
//?}
//? if neoforge {
/*@Mod(MobAmputation.MOD_ID)
*///?}
public final class MobAmputationMod {
    public MobAmputationMod(
            //? if neoforge {
            /*IEventBus modBus
            *///?}
    ) {
        //? if forge {
        /*MobAmputationContent.initializeForge();
        MobAmputationNetworking.initializeForge();
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist == net.minecraftforge.api.distmarker.Dist.CLIENT) {
            io.github.avacadowizard120.mobamputation.client.MobAmputationConfigScreenRegistration.registerForge();
        }
        *///?}
        //? if neoforge {
        /*MobAmputationContent.initializeNeoForge(modBus);
        MobAmputationNetworking.initializeNeoForge(modBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            io.github.avacadowizard120.mobamputation.client.MobAmputationConfigScreenRegistration.registerNeoForge();
        }
        *///?}
        MobAmputation.initialize();
    }
}
