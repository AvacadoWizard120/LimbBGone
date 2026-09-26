package io.github.avacadowizard120.mobamputation.mixin.client;

import net.minecraft.client.model.CreeperModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
        value = CreeperModel.class
        //? if forge {
        /*, remap = false*/
        //?}
)
public interface CreeperModelAccessor {
    //? if forge {
    /*@Accessor(value = "head", remap = false)*/
    //?} else {
    @Accessor("head")
    //?}
    ModelPart mobamputation$head();
}
