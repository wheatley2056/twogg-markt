package de.twogg.markt.content;

import de.twogg.markt.server.Selections;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

/**
 * Marktplaner (nur Admins): Linksklick auf einen Block setzt Ecke 1, Rechtsklick Ecke 2. Danach im Admin-Fenster
 * oder mit {@code /markt stand neu <name>} den Stand anlegen. Solange man ihn hält, sieht man alle Stände als Umriss.
 */
public class PlannerItem extends Item {
    public PlannerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() instanceof ServerPlayer sp) {
            Selections.corner(sp, ctx.getClickedPos(), 2);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        out.accept(Component.literal("Linksklick: Ecke 1 · Rechtsklick: Ecke 2").withStyle(ChatFormatting.GRAY));
        out.accept(Component.literal("Dann /markt admin → „Stand aus Auswahl“").withStyle(ChatFormatting.DARK_GRAY));
    }
}
