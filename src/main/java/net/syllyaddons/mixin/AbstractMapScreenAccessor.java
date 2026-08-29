package net.syllyaddons.mixin;

import com.wynntils.screens.maps.AbstractMapScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractMapScreen.class, remap = false)
public interface AbstractMapScreenAccessor {
    @Accessor("mapCenterX")
    float syllyaddons$getMapCenterX();

    @Accessor("mapCenterZ")
    float syllyaddons$getMapCenterZ();

    @Accessor("centerX")
    float syllyaddons$getCenterX();

    @Accessor("centerZ")
    float syllyaddons$getCenterZ();

    @Accessor("zoomRenderScale")
    float syllyaddons$getZoomRenderScale();

    @Accessor("renderX")
    float syllyaddons$getRenderX();

    @Accessor("renderY")
    float syllyaddons$getRenderY();

    @Accessor("renderWidth")
    float syllyaddons$getRenderWidth();

    @Accessor("renderHeight")
    float syllyaddons$getRenderHeight();
}
