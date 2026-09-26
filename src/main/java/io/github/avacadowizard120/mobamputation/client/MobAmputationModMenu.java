package io.github.avacadowizard120.mobamputation.client;

//? if fabric || quilt {
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
//?}

/** Optional Fabric/Quilt Mod Menu bridge; Mod Menu itself is not required. */
public final class MobAmputationModMenu
        //? if fabric || quilt {
        implements ModMenuApi
        //?}
{
    //? if fabric || quilt {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return MobAmputationConfigScreen::new;
    }
    //?}
}
