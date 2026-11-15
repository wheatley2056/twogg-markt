package de.twogg.markt.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Zugriff auf private Setter von {@link Display.TextDisplay} (Hologramme). */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {
    @Invoker("setText")
    void markt$setText(Component text);

    @Invoker("getText")
    Component markt$getText();

    @Invoker("setLineWidth")
    void markt$setLineWidth(int width);

    @Invoker("setBackgroundColor")
    void markt$setBackground(int argb);
}
