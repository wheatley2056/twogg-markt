package de.twogg.markt.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zugriff auf private Setter von {@link Display} (Hologramme). */
@Mixin(Display.class)
public interface DisplayAccessor {
    @Invoker("setBillboardConstraints")
    void markt$setBillboard(Display.BillboardConstraints constraints);

    @Invoker("setViewRange")
    void markt$setViewRange(float range);
}
