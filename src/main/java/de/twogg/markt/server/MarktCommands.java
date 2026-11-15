package de.twogg.markt.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import de.twogg.markt.MarktRuntime;
import de.twogg.markt.content.ModContent;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.Plot;
import de.twogg.markt.net.Views;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * {@code /markt} für alle (Fenster, Umrisse, Helfer) und {@code /markt …} für Admins (Stufe 2). Die Admin-Befehle
 * laufen über dieselben Aktionen wie das Admin-Fenster.
 */
public final class MarktCommands {
    private MarktCommands() {}

    private static final SuggestionProvider<CommandSourceStack> PLOTS =
            (ctx, b) -> SharedSuggestionProvider.suggest(MarktRuntime.data().plots.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> ZONES =
            (ctx, b) -> SharedSuggestionProvider.suggest(MarktRuntime.data().zones.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> MODES =
            (ctx, b) -> SharedSuggestionProvider.suggest(List.of("miete", "kauf", "beides", "gesperrt"), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        java.util.function.Predicate<CommandSourceStack> admin = Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        d.register(literal("markt")
                .executes(c -> {
                    ServerPlayer p = player(c);
                    if (p != null) StallService.open(p, "");
                    return 1;
                })
                .then(literal("zeigen").executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    Outlines.show(p, "", 30_000);
                    c.getSource().sendSuccess(() -> Component.literal("Stände in der Nähe sind 30 Sekunden lang umrandet: grün frei, gelb vermietet, blau verkauft, rot gesperrt."), false);
                    return 1;
                }))
                .then(literal("helfer")
                        .then(literal("add").then(argument("spieler", EntityArgument.player()).executes(c -> helper(c, true))))
                        .then(literal("entfernen").then(argument("name", StringArgumentType.word()).executes(c -> helper(c, false)))))
                .then(literal("admin").requires(admin).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p != null) AdminService.open(p);
                    return 1;
                }))
                .then(literal("planer").requires(admin).executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    ItemStack s = new ItemStack(ModContent.PLANNER);
                    if (!p.getInventory().add(s)) p.drop(s, false);
                    c.getSource().sendSuccess(() -> Component.literal("Marktplaner: Linksklick Ecke 1, Rechtsklick Ecke 2, dann /markt stand neu <Name>."), false);
                    return 1;
                }))
                .then(literal("stand").requires(admin)
                        .then(literal("neu").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            ServerPlayer p = player(c);
                            if (p == null) return 0;
                            Views.Action a = Views.Action.of("plot_create");
                            a.name = StringArgumentType.getString(c, "name");
                            return admin(c, a);
                        })))
                        .then(literal("hoch").then(argument("bloecke", IntegerArgumentType.integer(1, 64)).executes(c -> {
                            Views.Action a = Views.Action.of("sel_expand");
                            a.count = IntegerArgumentType.getInteger(c, "bloecke");
                            return admin(c, a);
                        })))
                        .then(literal("liste").executes(MarktCommands::list))
                        .then(literal("info").then(argument("id", StringArgumentType.word()).suggests(PLOTS).executes(MarktCommands::info)))
                        .then(plotAction("loeschen", "plot_delete"))
                        .then(plotAction("raeumen", "plot_evict"))
                        .then(plotAction("zuruecksetzen", "plot_reset"))
                        .then(plotAction("sichern", "plot_snapshot"))
                        .then(plotAction("tp", "plot_tp"))
                        .then(literal("miete").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("euro", DoubleArgumentType.doubleArg(0)).executes(c -> plotValue(c, "plot_rent")))))
                        .then(literal("kaufpreis").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("euro", DoubleArgumentType.doubleArg(0)).executes(c -> plotValue(c, "plot_buy")))))
                        .then(literal("vorausmiete").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("zeitraeume", IntegerArgumentType.integer(1, 365)).executes(c -> {
                                    Views.Action a = Views.Action.of("plot_periods");
                                    a.plot = StringArgumentType.getString(c, "id");
                                    a.count = IntegerArgumentType.getInteger(c, "zeitraeume");
                                    return admin(c, a);
                                }))))
                        .then(literal("art").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("art", StringArgumentType.word()).suggests(MODES).executes(c -> {
                                    Views.Action a = Views.Action.of("plot_mode");
                                    a.plot = StringArgumentType.getString(c, "id");
                                    a.text = switch (StringArgumentType.getString(c, "art")) {
                                        case "miete" -> "RENT";
                                        case "kauf" -> "BUY";
                                        case "beides" -> "BOTH";
                                        case "gesperrt" -> "LOCKED";
                                        default -> "";
                                    };
                                    return admin(c, a);
                                }))))
                        .then(literal("beschreibung").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                                    Views.Action a = Views.Action.of("plot_note");
                                    a.plot = StringArgumentType.getString(c, "id");
                                    a.text = StringArgumentType.getString(c, "text");
                                    return admin(c, a);
                                }))))
                        .then(literal("vergeben").then(argument("id", StringArgumentType.word()).suggests(PLOTS)
                                .then(argument("spieler", EntityArgument.player())
                                        .executes(c -> assign(c, 0))
                                        .then(argument("zeitraeume", IntegerArgumentType.integer(1, 365))
                                                .executes(c -> assign(c, IntegerArgumentType.getInteger(c, "zeitraeume"))))))))
                .then(literal("zone").requires(admin)
                        .then(literal("neu").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            Views.Action a = Views.Action.of("zone_create");
                            a.name = StringArgumentType.getString(c, "name");
                            return admin(c, a);
                        })))
                        .then(literal("loeschen").then(argument("id", StringArgumentType.word()).suggests(ZONES).executes(c -> {
                            Views.Action a = Views.Action.of("zone_delete");
                            a.id = StringArgumentType.getString(c, "id");
                            return admin(c, a);
                        })))
                        .then(literal("liste").executes(c -> {
                            var zones = MarktRuntime.data().zones.values();
                            if (zones.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Keine Räumzonen."), false);
                            for (var z : zones) c.getSource().sendSuccess(() -> Component.literal(z.id + " – " + z.name + " · " + z.box), false);
                            return zones.size();
                        })))
                .then(literal("event").requires(admin)
                        .then(literal("start").executes(c -> admin(c, Views.Action.of("event_start")))
                                .then(argument("minuten", IntegerArgumentType.integer(1, 24 * 60)).executes(c -> {
                                    Views.Action a = Views.Action.of("event_start");
                                    a.count = IntegerArgumentType.getInteger(c, "minuten");
                                    return admin(c, a);
                                })))
                        .then(literal("stop").executes(c -> admin(c, Views.Action.of("event_stop"))))
                        .then(literal("abbrechen").executes(c -> admin(c, Views.Action.of("event_abort"))))
                        .then(literal("status").executes(c -> {
                            ServerPlayer p = player(c);
                            if (p == null) return 0;
                            var i = EventService.info(p, true);
                            c.getSource().sendSuccess(() -> Component.literal("Weihnachtsmarkt #" + i.round + ": " + i.status
                                    + (i.next.isEmpty() ? "" : " · nächste Öffnung " + i.next) + " · Lose " + i.ticketsTotal
                                    + " · Wichtelgeschenke " + i.wichtelCount), false);
                            return 1;
                        }))
                        .then(literal("tafel")
                                .then(literal("hier").executes(c -> admin(c, Views.Action.of("leaderboard_here"))))
                                .then(literal("weg").executes(c -> admin(c, Views.Action.of("leaderboard_remove"))))))
                .then(literal("gelaende").requires(admin)
                        .then(literal("setzen").executes(c -> admin(c, Views.Action.of("area_set"))))
                        .then(literal("weg").executes(c -> admin(c, Views.Action.of("area_clear")))))
                .then(literal("auftraege").requires(admin)
                        .then(literal("neu").executes(c -> admin(c, Views.Action.of("jobs_reroll")))))
                .then(literal("reload").requires(admin).executes(c -> admin(c, Views.Action.of("reload"))))
                .then(literal("speichern").requires(admin).executes(c -> {
                    MarktRuntime.save();
                    c.getSource().sendSuccess(() -> Component.literal("Marktdaten gespeichert."), true);
                    return 1;
                })));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> plotAction(String name, String action) {
        return literal(name).then(argument("id", StringArgumentType.word()).suggests(PLOTS).executes(c -> {
            Views.Action a = Views.Action.of(action);
            a.plot = StringArgumentType.getString(c, "id");
            return admin(c, a);
        }));
    }

    private static int plotValue(CommandContext<CommandSourceStack> c, String action) {
        Views.Action a = Views.Action.of(action);
        a.plot = StringArgumentType.getString(c, "id");
        a.value = DoubleArgumentType.getDouble(c, "euro");
        return admin(c, a);
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> c) {
        ServerPlayer p = c.getSource().getPlayer();
        if (p == null) c.getSource().sendFailure(Component.literal("Nur im Spiel."));
        return p;
    }

    private static int admin(CommandContext<CommandSourceStack> c, Views.Action a) {
        ServerPlayer p = player(c);
        if (p == null || !MarktRuntime.running()) return 0;
        try {
            String msg = AdminService.run(p, a);
            MarktRuntime.markDirty();
            if (!msg.isEmpty()) c.getSource().sendSuccess(() -> Component.literal(msg), true);
            StallService.refreshOpen();
            return 1;
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int helper(CommandContext<CommandSourceStack> c, boolean add) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = player(c);
        if (p == null) return 0;
        long now = MarktRuntime.now();
        Plot mine = null;
        for (Plot plot : MarktRuntime.data().plots.values()) if (plot.isOwner(p.getUUID().toString(), now)) { mine = plot; break; }
        if (mine == null) {
            c.getSource().sendFailure(Component.literal("Du hast keinen Stand."));
            return 0;
        }
        Views.Action a = Views.Action.of(add ? "helper_add" : "helper_remove");
        a.plot = mine.id;
        a.name = add ? EntityArgument.getPlayer(c, "spieler").getGameProfile().name() : StringArgumentType.getString(c, "name");
        var r = add ? StallService.helperAdd(p, mine, a.name) : StallService.helperRemove(p, mine, a.name);
        Component msg = Component.literal(r.msg()).withStyle(r.error() ? ChatFormatting.RED : ChatFormatting.GREEN);
        c.getSource().sendSuccess(() -> msg, false);
        return r.error() ? 0 : 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        long now = MarktRuntime.now();
        var plots = MarktRuntime.data().plots.values();
        if (plots.isEmpty()) c.getSource().sendSuccess(() -> Component.literal("Noch keine Stände. /markt planer, zwei Ecken setzen, /markt stand neu <Name>."), false);
        for (Plot plot : plots) {
            String s = plot.id + " – " + plot.name + " · " + status(plot, now) + " · " + plot.box.size();
            c.getSource().sendSuccess(() -> Component.literal(s), false);
        }
        return plots.size();
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        Plot plot = MarktRuntime.data().plots.get(StringArgumentType.getString(c, "id"));
        if (plot == null) {
            c.getSource().sendFailure(Component.literal("Diesen Stand gibt es nicht."));
            return 0;
        }
        long now = MarktRuntime.now();
        String[] lines = {
                plot.name + " (" + plot.id + ") – " + status(plot, now),
                "Bereich " + plot.box + " in " + plot.box.dim + " · " + plot.box.size(),
                "Art: " + AdminService.modeName(plot.mode) + " · Miete " + AdminService.euro(plot.rentPrice) + " (= "
                        + MarktRuntime.fmt(MarketMath.priceTenths(plot.rentPrice, MarktRuntime.rate())) + ") pro "
                        + MarktRuntime.config().stalls.periodName + " · Kauf " + AdminService.euro(plot.buyPrice) + " (= "
                        + MarktRuntime.fmt(MarketMath.priceTenths(plot.buyPrice, MarktRuntime.rate())) + ")",
                "Helfer: " + (plot.helpers.isEmpty() ? "–" : String.join(", ", plot.helpers.values()))
                        + " · Angebote " + plot.offers.size() + " · Kasse " + MarktRuntime.fmt(plot.till)
                        + " · Umsatz " + MarktRuntime.fmt(plot.salesTotal),
                "Leerzustand gesichert: " + (Snapshots.exists(plot.id) ? "ja" : "nein")
        };
        for (String l : lines) c.getSource().sendSuccess(() -> Component.literal(l), false);
        return 1;
    }

    static String status(Plot plot, long now) {
        return switch (plot.status(now)) {
            case FREE -> "frei";
            case LOCKED -> "gesperrt";
            case OWNED -> "gekauft von " + plot.ownerName;
            case RENTED -> "vermietet an " + plot.ownerName + " (noch " + MarketMath.duration(plot.rentedUntil - now) + ")";
        };
    }

    /** Admin vergibt einen Stand ohne Bezahlung: mit Zeiträumen als Miete, ohne als Kauf. */
    private static int assign(CommandContext<CommandSourceStack> c, int periods) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Plot plot = MarktRuntime.data().plots.get(StringArgumentType.getString(c, "id"));
        if (plot == null) {
            c.getSource().sendFailure(Component.literal("Diesen Stand gibt es nicht."));
            return 0;
        }
        ServerPlayer target = EntityArgument.getPlayer(c, "spieler");
        long now = MarktRuntime.now();
        if (plot.hasOwner()) StallService.release(plot, "an jemand anderen vergeben");
        plot.owner = target.getUUID().toString();
        plot.ownerName = target.getGameProfile().name();
        plot.since = now;
        plot.bought = periods <= 0;
        plot.rentedUntil = periods <= 0 ? 0 : MarketMath.extend(0, now, periods, MarktRuntime.periodMs());
        MarktRuntime.markDirty();
        ItemStack s = new ItemStack(ModContent.COUNTER_ITEM);
        if (!target.getInventory().add(s)) target.drop(s, false);
        target.sendSystemMessage(Component.literal("Die Marktleitung hat dir den Stand „" + plot.name + "“ gegeben"
                + (periods > 0 ? " (" + MarktRuntime.periods(periods) + ")." : ".")).withStyle(ChatFormatting.GREEN));
        c.getSource().sendSuccess(() -> Component.literal("„" + plot.name + "“ an " + target.getGameProfile().name() + " vergeben."), true);
        StallService.refreshOpen();
        return 1;
    }
}
