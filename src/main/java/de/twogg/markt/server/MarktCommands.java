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
                .then(literal("handbuch").executes(c -> {
                    ServerPlayer p = player(c);
                    if (p == null) return 0;
                    de.twogg.markt.content.guide.GuideBook.give(p);
                    c.getSource().sendSuccess(() -> Component.literal("Das Markt-Handbuch liegt in deinem Inventar – Rechtsklick zum Lesen."), false);
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
                .then(laden().requires(admin))
                .then(lager().requires(admin))
                .then(advent().requires(admin))
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

    private static final SuggestionProvider<CommandSourceStack> LAEDEN =
            (ctx, b) -> SharedSuggestionProvider.suggest(MarktRuntime.data().laeden.keySet(), b);

    private static de.twogg.markt.core.Laden ladenArg(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "laden");
        de.twogg.markt.core.Laden l = MarktRuntime.data().laeden.get(id);
        if (l == null) c.getSource().sendFailure(Component.literal("Unbekannter Laden: " + id + " (/markt laden liste)"));
        return l;
    }

    private static int say(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    /**
     * {@code /markt laden …} (seit 0.4.0): Server-Läden mit festen Preisen für NPCs (z. B. Storyteller: Aktion
     * „Befehl“ mit {@code markt laden oeffnen <id> {player}}).
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> laden() {
        var id = argument("laden", StringArgumentType.word()).suggests(LAEDEN);
        return literal("laden")
                .then(literal("liste").executes(c -> {
                    if (MarktRuntime.data().laeden.isEmpty()) return say(c, "Keine Läden. Anlegen: /markt laden neu <id> <name>");
                    for (var l : MarktRuntime.data().laeden.values()) {
                        say(c, l.id + " – " + l.name + (l.open ? "" : " (geschlossen)") + " · " + l.sells.size() + " Verkauf, " + l.buys.size() + " Ankauf, "
                                + l.waren.size() + " Waren mit Lager" + (LagerService.isLager(l) ? " (Brandts Lager, aus der Config)" : ""));
                    }
                    return 1;
                }))
                .then(literal("neu").then(argument("laden", StringArgumentType.word())
                        .then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                            String err = LadenService.create(StringArgumentType.getString(c, "laden"), StringArgumentType.getString(c, "name"));
                            if (err != null) { c.getSource().sendFailure(Component.literal(err)); return 0; }
                            return say(c, "Laden angelegt. Ware in die Hand: /markt laden verkauf|ankauf "
                                    + StringArgumentType.getString(c, "laden") + " <preis> [stück]");
                        }))))
                .then(literal("info").then(id.executes(c -> {
                    var l = ladenArg(c);
                    if (l == null) return 0;
                    say(c, l.id + " – " + l.name + (l.subtitle.isEmpty() ? "" : " · " + l.subtitle) + (l.open ? "" : " (geschlossen)"));
                    for (var o : l.sells) say(c, " " + o.id + " Verkauf: " + o.count + "× " + o.name + " für " + MarktRuntime.fmt(o.price) + " · " + o.sold + "× verkauft");
                    for (var w : l.waren) say(c, " " + w.id + " Ware: " + w.name + " · " + w.unit + " Stück, Grundpreis " + MarktRuntime.fmt(w.base)
                            + " · Bestand " + w.stock + "/" + w.capacity + " (Soll " + w.target + ") · jetzt " + MarktRuntime.fmt(
                            de.twogg.markt.core.StockMath.nextSellPrice(w.base, w.stock, w.target, w.capacity, w.unit)) + " / Ankauf "
                            + MarktRuntime.fmt(de.twogg.markt.core.StockMath.nextBuyPrice(w.base, w.stock, w.target, w.capacity, l.spread, w.unit)));
                    if (!l.waren.isEmpty()) say(c, " Lieferung jede Woche " + l.deliveryDay + " " + l.deliveryTime + " · Ankauf = Preis × " + l.spread);
                    for (var b : l.buys) say(c, " " + b.id + " Ankauf: " + b.count + "× " + b.name + " für " + MarktRuntime.fmt(b.price)
                            + (b.dailyLimit > 0 ? " · " + b.dailyLimit + "/Tag" : "") + " · " + b.bought + "× angekauft");
                    return 1;
                })))
                .then(literal("verkauf").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("preis", DoubleArgumentType.doubleArg(0.1, 1_000_000))
                                .executes(c -> sellCmd(c, 1))
                                .then(argument("stueck", IntegerArgumentType.integer(1, 576)).executes(c -> sellCmd(c, IntegerArgumentType.getInteger(c, "stueck")))
                                        .then(argument("item", StringArgumentType.greedyString()).executes(c -> sellItemCmd(c)))))))
                .then(literal("ankauf").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("preis", DoubleArgumentType.doubleArg(0.1, 1_000_000))
                                .executes(c -> buyCmd(c, 1, 0))
                                .then(argument("stueck", IntegerArgumentType.integer(1, 576))
                                        .executes(c -> buyCmd(c, IntegerArgumentType.getInteger(c, "stueck"), 0))
                                        .then(argument("tageslimit", IntegerArgumentType.integer(0, 10000))
                                                .executes(c -> buyCmd(c, IntegerArgumentType.getInteger(c, "stueck"), IntegerArgumentType.getInteger(c, "tageslimit")))
                                                .then(argument("item", StringArgumentType.greedyString()).executes(c -> buyItemCmd(c))))))))
                .then(literal("ware").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("preis", DoubleArgumentType.doubleArg(0.1, 1_000_000))
                        .then(argument("einheit", IntegerArgumentType.integer(1, 576))
                        .then(argument("lager", IntegerArgumentType.integer(1, 1_000_000))
                        .then(argument("soll", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> {
                            ServerPlayer p = player(c);
                            var l = ladenArg(c);
                            if (p == null || l == null) return 0;
                            return say(c, LadenService.putWare(p.level().registryAccess(), l, p.getMainHandItem(), null,
                                    DoubleArgumentType.getDouble(c, "preis"), IntegerArgumentType.getInteger(c, "einheit"),
                                    IntegerArgumentType.getInteger(c, "lager"), IntegerArgumentType.getInteger(c, "soll")));
                        })))))))
                .then(literal("import").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("datei", StringArgumentType.word()).executes(c -> {
                            var l = ladenArg(c);
                            if (l == null) return 0;
                            for (String line : LadenService.importFile(c.getSource().registryAccess(), l, StringArgumentType.getString(c, "datei"))) say(c, line);
                            return 1;
                        }))))
                .then(literal("lieferung").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN).executes(c -> {
                    var l = ladenArg(c);
                    if (l == null) return 0;
                    LadenService.deliver(l);
                    return say(c, "Lieferung für " + l.name + ": alle Waren bis zum Soll aufgefüllt" + (l.trimSurplus ? ", Überschuss zurück." : " (Überschuss bleibt)."));
                })))
                .then(literal("lieferzeit").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("tag", StringArgumentType.word()).suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"), b))
                        .then(argument("zeit", StringArgumentType.string()).executes(c -> {
                            var l = ladenArg(c);
                            if (l == null) return 0;
                            l.deliveryDay = StringArgumentType.getString(c, "tag").toUpperCase(java.util.Locale.ROOT);
                            l.deliveryTime = StringArgumentType.getString(c, "zeit");
                            MarktRuntime.markDirty();
                            return say(c, "Lieferung jede Woche " + l.deliveryDay + " " + l.deliveryTime + " (" + l.zone + ").");
                        })))))
                .then(literal("bestand").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("zeile", StringArgumentType.word())
                        .then(argument("stueck", IntegerArgumentType.integer(0, 1_000_000)).executes(c -> {
                            var l = ladenArg(c);
                            if (l == null) return 0;
                            var w = l.ware(StringArgumentType.getString(c, "zeile"));
                            if (w == null) { c.getSource().sendFailure(Component.literal("Keine Ware mit dieser ID.")); return 0; }
                            w.stock = Math.min(w.capacity, IntegerArgumentType.getInteger(c, "stueck"));
                            MarktRuntime.markDirty();
                            return say(c, w.name + ": Bestand " + w.stock + "/" + w.capacity + ".");
                        })))))
                .then(literal("entfernen").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("zeile", StringArgumentType.word()).executes(c -> {
                            var l = ladenArg(c);
                            if (l == null) return 0;
                            String z = StringArgumentType.getString(c, "zeile");
                            if (!LadenService.remove(l, z)) { c.getSource().sendFailure(Component.literal("Keine Zeile " + z + " (/markt laden info " + l.id + ")")); return 0; }
                            return say(c, "Zeile " + z + " entfernt.");
                        }))))
                .then(literal("untertitel").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            var l = ladenArg(c);
                            if (l == null) return 0;
                            l.subtitle = StringArgumentType.getString(c, "text");
                            MarktRuntime.markDirty();
                            return say(c, "Untertitel gesetzt.");
                        }))))
                .then(literal("auf").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN).executes(c -> openState(c, true))))
                .then(literal("zu").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN).executes(c -> openState(c, false))))
                .then(literal("loeschen").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN).executes(c -> {
                    var l = ladenArg(c);
                    if (l == null) return 0;
                    MarktRuntime.data().laeden.remove(l.id);
                    MarktRuntime.markDirty();
                    return say(c, "Laden " + l.id + " gelöscht.");
                })))
                .then(literal("oeffnen").then(argument("laden", StringArgumentType.word()).suggests(LAEDEN)
                        .executes(c -> {
                            ServerPlayer p = player(c);
                            return p == null ? 0 : openFor(c, List.of(p));
                        })
                        .then(argument("spieler", EntityArgument.players()).executes(c -> openFor(c, EntityArgument.getPlayers(c, "spieler"))))));
    }

    /** {@code /markt lager} (seit 0.6.0): Brandts Lager – Stand des Abgleichs, Abgleich, Nachschub sofort. */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lager() {
        return literal("lager")
                .executes(MarktCommands::lagerInfo)
                .then(literal("abgleich").executes(c -> {
                    LagerService.sync();
                    return lagerInfo(c);
                }))
                .then(literal("nachschub").executes(c -> {
                    var cfg = MarktRuntime.config().lager;
                    var l = cfg == null ? null : MarktRuntime.data().laeden.get(cfg.laden);
                    if (l == null || !LagerService.isLager(l)) {
                        c.getSource().sendFailure(Component.literal("Brandts Lager ist nicht eingerichtet (lager.enabled, lager.laden)."));
                        return 0;
                    }
                    LadenService.deliver(l);
                    c.getSource().sendSuccess(() -> Component.literal("Nachschub für " + l.name + ": alle Waren bis zum Wochenbestand aufgefüllt."), true);
                    return 1;
                }));
    }

    private static int lagerInfo(CommandContext<CommandSourceStack> c) {
        List<String> r = LagerService.report();
        if (r.isEmpty()) return say(c, "Brandts Lager: noch kein Abgleich (/markt lager abgleich).");
        say(c, "Brandts Lager: " + r.getFirst());
        for (String line : r.subList(1, r.size())) c.getSource().sendSuccess(() -> Component.literal(" " + line).withStyle(ChatFormatting.GOLD), false);
        var cfg = MarktRuntime.config().lager;
        if (cfg != null) say(c, " Waren und Preise: /markt laden info " + cfg.laden);
        return 1;
    }

    /**
     * {@code /markt advent …} (seit 0.6.0): Inhalte der Türchen bearbeiten. Gespeichert in
     * {@code config/twogg-markt/markt.json} unter {@code event.adventDays}.
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> advent() {
        var tag = argument("tag", IntegerArgumentType.integer(1, 24));
        return literal("advent")
                .executes(MarktCommands::adventList)
                .then(literal("liste").executes(MarktCommands::adventList))
                .then(literal("item").then(argument("tag", IntegerArgumentType.integer(1, 24)).executes(c -> adventAct(c, "advent_item"))))
                .then(literal("leeren").then(argument("tag", IntegerArgumentType.integer(1, 24)).executes(c -> adventAct(c, "advent_clear"))))
                .then(literal("muenzen").then(argument("tag", IntegerArgumentType.integer(1, 24))
                        .then(argument("coins", DoubleArgumentType.doubleArg(0, 10_000)).executes(c -> {
                            Views.Action a = Views.Action.of("advent_coins");
                            a.count = IntegerArgumentType.getInteger(c, "tag");
                            a.value = DoubleArgumentType.getDouble(c, "coins");
                            return admin(c, a);
                        }))))
                .then(literal("text").then(tag
                        .then(argument("text", StringArgumentType.greedyString()).executes(c -> {
                            Views.Action a = Views.Action.of("advent_text");
                            a.count = IntegerArgumentType.getInteger(c, "tag");
                            a.text = StringArgumentType.getString(c, "text");
                            return admin(c, a);
                        }))))
                .then(literal("standard")
                        .then(literal("alle").executes(c -> {
                            Views.Action a = Views.Action.of("advent_default");
                            a.count = 0;
                            return admin(c, a);
                        }))
                        .then(argument("tag", IntegerArgumentType.integer(1, 24)).executes(c -> adventAct(c, "advent_default"))))
                .then(literal("zuruecksetzen")
                        .then(literal("alle").executes(c -> admin(c, Views.Action.of("advent_reopen"))))
                        .then(argument("spieler", EntityArgument.player()).executes(c -> {
                            Views.Action a = Views.Action.of("advent_reopen");
                            a.name = EntityArgument.getPlayer(c, "spieler").getGameProfile().name();
                            return admin(c, a);
                        })));
    }

    private static int adventAct(CommandContext<CommandSourceStack> c, String action) {
        Views.Action a = Views.Action.of(action);
        a.count = IntegerArgumentType.getInteger(c, "tag");
        return admin(c, a);
    }

    private static int adventList(CommandContext<CommandSourceStack> c) {
        var cfg = MarktRuntime.config();
        say(c, "Adventskalender (" + (cfg.event.advent ? "an" : "aus") + ") – ändern: /markt advent item|muenzen|text|leeren|standard <tag>");
        var reg = c.getSource().registryAccess();
        for (int day = 1; day <= 24; day++) {
            Views.AdventDoor door = AdminService.adventDoor(reg, cfg.advent(day), day);
            String line = String.format("%2d. %s", day, door.text);
            c.getSource().sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int sellCmd(CommandContext<CommandSourceStack> c, int count) {
        ServerPlayer p = player(c);
        var l = ladenArg(c);
        if (p == null || l == null) return 0;
        return say(c, LadenService.addSell(p, l, DoubleArgumentType.getDouble(c, "preis"), count));
    }

    private static ItemStack itemArg(CommandContext<CommandSourceStack> c) {
        String id = StringArgumentType.getString(c, "item").trim();
        var item = de.twogg.markt.data.Stacks.item(id.contains(":") ? id : "minecraft:" + id);
        if (item == null) {
            c.getSource().sendFailure(Component.literal("Unbekanntes Item: " + id));
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    private static int sellItemCmd(CommandContext<CommandSourceStack> c) {
        var l = ladenArg(c);
        ItemStack st = itemArg(c);
        if (l == null || st.isEmpty()) return 0;
        return say(c, LadenService.addSell(c.getSource().registryAccess(), st, l, DoubleArgumentType.getDouble(c, "preis"),
                IntegerArgumentType.getInteger(c, "stueck")));
    }

    private static int buyItemCmd(CommandContext<CommandSourceStack> c) {
        var l = ladenArg(c);
        ItemStack st = itemArg(c);
        if (l == null || st.isEmpty()) return 0;
        return say(c, LadenService.addBuy(c.getSource().registryAccess(), st, l, DoubleArgumentType.getDouble(c, "preis"),
                IntegerArgumentType.getInteger(c, "stueck"), IntegerArgumentType.getInteger(c, "tageslimit")));
    }

    private static int buyCmd(CommandContext<CommandSourceStack> c, int count, int limit) {
        ServerPlayer p = player(c);
        var l = ladenArg(c);
        if (p == null || l == null) return 0;
        return say(c, LadenService.addBuy(p, l, DoubleArgumentType.getDouble(c, "preis"), count, limit));
    }

    private static int openState(CommandContext<CommandSourceStack> c, boolean open) {
        var l = ladenArg(c);
        if (l == null) return 0;
        l.open = open;
        MarktRuntime.markDirty();
        return say(c, l.name + (open ? " ist geöffnet." : " ist geschlossen."));
    }

    private static int openFor(CommandContext<CommandSourceStack> c, java.util.Collection<ServerPlayer> to) {
        String id = StringArgumentType.getString(c, "laden");
        int n = 0;
        for (ServerPlayer p : to) {
            String err = LadenService.open(p, id);
            if (err != null) { c.getSource().sendFailure(Component.literal(err)); return 0; }
            n++;
        }
        return n;
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
