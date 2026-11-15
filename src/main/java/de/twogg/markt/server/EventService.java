package de.twogg.markt.server;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.compat.AtmosCompat;
import de.twogg.markt.core.Box;
import de.twogg.markt.core.MarketMath;
import de.twogg.markt.core.MarktConfig;
import de.twogg.markt.core.MarktData;
import de.twogg.markt.core.MarktEvent;
import de.twogg.markt.core.Melodies;
import de.twogg.markt.core.Plot;
import de.twogg.markt.core.Schedule;
import de.twogg.markt.data.Stacks;
import de.twogg.markt.net.Views;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Der Weihnachtsmarkt als Event: Öffnungszeiten (automatisch) oder Start per Befehl, Eröffnung mit Titel und Glocken,
 * Bossbar mit Restzeit, Musik und Atmos-Stimmung im Marktgelände, Statistiken für die Bestenliste, im Finale
 * Losziehung, Wichteln und Siegerehrung mit Preisen und Feuerwerk.
 */
public final class EventService {
    private static final Random RND = new Random();
    private static final UUID BAR_ID = UUID.nameUUIDFromBytes("twogg_markt:event".getBytes());
    private static ServerBossEvent bar;
    private static final Set<String> ANNOUNCED = new HashSet<>();
    private static final Set<UUID> ATMOS = new HashSet<>();
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMANY);

    // Musik
    private static int song = -1, note, wait;

    private EventService() {}

    static MarktEvent ev() {
        return MarktRuntime.data().event;
    }

    static MarktConfig.Event cfg() {
        return MarktRuntime.config().event;
    }

    public static boolean running() {
        return MarktRuntime.running() && ev().running();
    }

    // ------------------------------------------------------------------ Takt

    /** Jede Sekunde. */
    public static void tick() {
        MarktEvent e = ev();
        MarktConfig.Event c = cfg();
        long now = MarktRuntime.now();
        LocalDateTime local = LocalDateTime.now();
        Schedule sched = new Schedule(c.schedule);
        switch (e.phase) {
            case CLOSED -> {
                if (!c.enabled) break;
                Schedule.Window w = sched.active(local);
                if (w != null && !w.key().equals(e.windowKey)) {
                    long end = w.end().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                    start(end, w.key(), null);
                    break;
                }
                Schedule.Window next = sched.next(local);
                if (next != null) {
                    long mins = java.time.Duration.between(local, next.start()).toMinutes() + 1;
                    for (int m : c.announceMinutes) {
                        if (mins == m && ANNOUNCED.add(next.key() + ":" + m)) {
                            broadcast(Component.literal("🎄 Der " + MarktRuntime.config().title + " öffnet in " + m
                                    + (m == 1 ? " Minute" : " Minuten") + "!").withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
            }
            case OPENING -> {
                if (now >= e.phaseUntil) {
                    e.phase = MarktEvent.Phase.OPEN;
                    MarktRuntime.markDirty();
                }
            }
            case OPEN -> {
                if (now >= e.endsAt) finale();
                else if (e.endsAt - now <= 5 * MarketMath.MINUTE && ANNOUNCED.add("ende:" + e.round)) {
                    broadcast(Component.literal("🔔 Noch 5 Minuten Weihnachtsmarkt – letzte Lose, letzte Stimmen!")
                            .withStyle(ChatFormatting.GOLD));
                }
            }
            case FINALE -> {
                if (now >= e.phaseUntil) close();
            }
        }
        updateBar(now);
        atmosphere();
    }

    /** Jeden Tick: Musik. */
    public static void musicTick() {
        if (!running() || !cfg().music) {
            song = -1;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        List<Melodies.Song> songs = Melodies.SONGS;
        if (song < 0 || song >= songs.size()) {
            song = RND.nextInt(songs.size());
            note = 0;
        }
        Melodies.Song s = songs.get(song);
        if (note >= s.notes().size()) {
            song = (song + 1) % songs.size();
            note = 0;
            wait = cfg().musicPauseSeconds * 20;
            return;
        }
        Melodies.Note n = s.notes().get(note++);
        wait = n.eighths() * cfg().musicTicksPerEighth - 1;
        if (n.pitch() < 0) return;
        float pitch = Melodies.soundPitch(n.pitch());
        for (ServerPlayer p : listeners()) {
            sound(p, SoundEvents.NOTE_BLOCK_BELL, cfg().musicVolume, pitch);
            if (note % 4 == 1) sound(p, SoundEvents.NOTE_BLOCK_CHIME, cfg().musicVolume * 0.35f, pitch);
        }
    }

    private static List<ServerPlayer> listeners() {
        MinecraftServer server = MarktRuntime.server();
        List<ServerPlayer> out = new ArrayList<>();
        Box area = MarktRuntime.data().area;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (area == null || inArea(p, area)) out.add(p);
        }
        return out;
    }

    static boolean inArea(ServerPlayer p, Box area) {
        return area.dim.equals(p.level().dimension().identifier().toString())
                && p.getBlockX() >= area.minX - 8 && p.getBlockX() <= area.maxX + 8
                && p.getBlockZ() >= area.minZ - 8 && p.getBlockZ() <= area.maxZ + 8;
    }

    private static void sound(ServerPlayer p, Holder<SoundEvent> s, float vol, float pitch) {
        p.connection.send(new ClientboundSoundPacket(s, SoundSource.RECORDS, p.getX(), p.getY() + 1, p.getZ(), vol, pitch, RND.nextLong()));
    }

    private static void atmosphere() {
        String preset = cfg().atmosPreset;
        boolean on = running() && !preset.isBlank() && AtmosCompat.loaded();
        Box area = MarktRuntime.data().area;
        for (ServerPlayer p : MarktRuntime.server().getPlayerList().getPlayers()) {
            boolean want = on && (area == null || inArea(p, area));
            if (want && ATMOS.add(p.getUUID())) {
                if (!AtmosCompat.set(p, preset, 4f)) ATMOS.remove(p.getUUID());
            } else if (!want && ATMOS.remove(p.getUUID())) {
                AtmosCompat.clear(p, 4f);
            }
        }
    }

    public static void forget(UUID id) {
        ATMOS.remove(id);
    }

    public static void join(ServerPlayer p) {
        if (bar != null && bar.isVisible()) bar.addPlayer(p);
        if (running()) {
            p.sendSystemMessage(Component.literal("🎄 Der " + MarktRuntime.config().title + " ist geöffnet! /markt")
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    // ------------------------------------------------------------------ Bossbar

    private static void updateBar(long now) {
        MarktEvent e = ev();
        boolean show = cfg().bossbar && e.phase != MarktEvent.Phase.CLOSED;
        if (!show) {
            if (bar != null) {
                bar.removeAllPlayers();
                bar.setVisible(false);
            }
            return;
        }
        if (bar == null) {
            bar = new ServerBossEvent(BAR_ID, Component.empty(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
        }
        bar.setVisible(true);
        for (ServerPlayer p : MarktRuntime.server().getPlayerList().getPlayers()) {
            if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        }
        String title = MarktRuntime.config().title;
        switch (e.phase) {
            case OPENING -> {
                bar.setName(Component.literal("🎄 " + title + " – Eröffnung!").withStyle(ChatFormatting.GOLD));
                bar.setColor(BossEvent.BossBarColor.YELLOW);
                bar.setProgress(1f);
            }
            case OPEN -> {
                long total = Math.max(1, e.endsAt - e.startedAt), left = Math.max(0, e.endsAt - now);
                bar.setName(Component.literal("🎄 " + title + " geöffnet – noch " + MarketMath.duration(left)).withStyle(ChatFormatting.WHITE));
                bar.setColor(left < 5 * MarketMath.MINUTE ? BossEvent.BossBarColor.YELLOW : BossEvent.BossBarColor.RED);
                bar.setProgress(Math.max(0f, Math.min(1f, left / (float) total)));
            }
            case FINALE -> {
                bar.setName(Component.literal("⭐ Siegerehrung auf dem " + title).withStyle(ChatFormatting.GOLD));
                bar.setColor(BossEvent.BossBarColor.GREEN);
                bar.setProgress(1f);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Ablauf

    /** Event starten: bis {@code endsAt} (ms). {@code key} = Öffnungszeiten-Fenster oder leer. */
    public static String start(long endsAt, String key, ServerPlayer by) {
        MarktEvent e = ev();
        if (e.running()) return "Der Markt ist schon geöffnet.";
        if (e.phase == MarktEvent.Phase.FINALE) close();
        long now = MarktRuntime.now();
        e.reset();
        e.round++;
        e.phase = MarktEvent.Phase.OPENING;
        e.startedAt = now;
        e.endsAt = Math.max(endsAt, now + MarketMath.MINUTE);
        e.phaseUntil = now + cfg().openingSeconds * 1000L;
        if (key != null) e.windowKey = key;
        MarktRuntime.markDirty();
        String title = MarktRuntime.config().title;
        title(Component.literal(title).withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                Component.literal("ist eröffnet! · bis " + HM.format(LocalDateTime.now().plusSeconds((e.endsAt - now) / 1000))).withStyle(ChatFormatting.GOLD));
        for (ServerPlayer p : MarktRuntime.server().getPlayerList().getPlayers()) {
            for (int i = 0; i < 3; i++) sound(p, SoundEvents.NOTE_BLOCK_BELL, 1f, Melodies.soundPitch(18 - i * 5));
            p.connection.send(new ClientboundSoundPacket(Holder.direct(SoundEvents.BELL_BLOCK), SoundSource.AMBIENT, p.getX(), p.getY(), p.getZ(), 1f, 1f, 1L));
        }
        broadcast(Component.literal("🎄 Der " + title + " ist eröffnet! Stände, Losbude, Wichteln, Geschenkesuche – /markt")
                .withStyle(ChatFormatting.GOLD));
        if (cfg().weatherSnow) {
            MarktRuntime.server().setWeatherParameters(0, (int) Math.min(Integer.MAX_VALUE, (e.endsAt - now) / 50 + 1200), true, false);
        }
        song = -1;
        wait = 100;
        TwoggMarkt.LOGGER.info("Weihnachtsmarkt-Event {} gestartet{}", e.round, by == null ? " (Öffnungszeiten)" : " von " + by.getGameProfile().name());
        StallService.refreshOpen();
        return "Der " + title + " ist eröffnet (bis " + HM.format(LocalDateTime.now().plusSeconds((e.endsAt - now) / 1000)) + ").";
    }

    /** Ende der Öffnungszeit: Ziehung, Wichteln, Siegerehrung. */
    public static void finale() {
        MarktEvent e = ev();
        if (!e.running()) return;
        e.phase = MarktEvent.Phase.FINALE;
        e.phaseUntil = MarktRuntime.now() + cfg().finaleSeconds * 1000L;
        List<String> results = new ArrayList<>();
        results.add("Weihnachtsmarkt #" + e.round + " – " + DAY.format(LocalDateTime.now()));
        results.addAll(lottery());
        results.addAll(wichteln());
        results.addAll(contest());
        e.lastResults = results;
        e.lastEnded = MarktRuntime.now();
        MarktRuntime.markDirty();
        title(Component.literal("Siegerehrung").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                Component.literal(MarktRuntime.config().title + " #" + e.round).withStyle(ChatFormatting.WHITE));
        broadcast(Component.literal("⭐ Siegerehrung auf dem " + MarktRuntime.config().title + " ⭐").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        for (int i = 1; i < results.size(); i++) broadcast(Component.literal(results.get(i)).withStyle(ChatFormatting.YELLOW));
        fireworks();
        TwoggMarkt.LOGGER.info("Weihnachtsmarkt-Event {} beendet: {}", e.round, results);
        StallService.refreshOpen();
    }

    /** Nach dem Finale: Markt zu. */
    public static void close() {
        MarktEvent e = ev();
        e.phase = MarktEvent.Phase.CLOSED;
        MarktRuntime.markDirty();
        broadcast(Component.literal("🎄 Der " + MarktRuntime.config().title + " schließt. Danke fürs Kommen!").withStyle(ChatFormatting.GOLD));
        updateBar(MarktRuntime.now());
        atmosphere();
        if (cfg().weatherSnow) MarktRuntime.server().overworld().resetWeatherCycle();
        StallService.refreshOpen();
    }

    /** Abbruch ohne Preise: Lose und Wichtelgeschenke gehen zurück. */
    public static void abort() {
        MarktEvent e = ev();
        if (e.phase == MarktEvent.Phase.CLOSED) return;
        long price = ticketPrice();
        for (Map.Entry<String, Integer> t : e.tickets.entrySet()) {
            long back = Math.min(e.pot, t.getValue() * price);
            if (back > 0) payTo(t.getKey(), back, "Losbude: Event abgebrochen");
        }
        for (MarktEvent.Gift g : e.wichtel) {
            MarktRuntime.data().pickup.computeIfAbsent(g.from, k -> new ArrayList<>()).add(g.item);
        }
        e.reset();
        e.lastResults = List.of("Weihnachtsmarkt #" + e.round + " wurde abgebrochen.");
        close();
    }

    // ------------------------------------------------------------------ Finale

    static long ticketPrice() {
        return MarketMath.priceTenths(cfg().ticketPrice, MarktRuntime.rate());
    }

    private static List<String> lottery() {
        MarktEvent e = ev();
        List<String> out = new ArrayList<>();
        if (!cfg().lottery || e.tickets.isEmpty()) return out;
        long payout = MarketMath.percent(e.pot, cfg().potPayoutPercent);
        List<Double> shares = cfg().lotteryShares;
        List<String> winners = MarketMath.drawLottery(e.tickets, shares.size(), RND);
        out.add("Losbude (" + e.tickets.values().stream().mapToInt(Integer::intValue).sum() + " Lose, Topf " + MarktRuntime.fmt(e.pot) + "):");
        for (int i = 0; i < winners.size(); i++) {
            long prize = MarketMath.percent(payout, shares.get(i));
            String w = winners.get(i);
            payTo(w, prize, "Losbude: Platz " + (i + 1));
            out.add("  " + (i + 1) + ". " + name(w) + " gewinnt " + MarktRuntime.fmt(prize));
        }
        e.pot = 0;
        return out;
    }

    private static List<String> wichteln() {
        MarktEvent e = ev();
        List<String> out = new ArrayList<>();
        if (!cfg().wichteln || e.wichtel.isEmpty()) return out;
        int[] to = MarketMath.wichtel(e.wichtel.size(), RND);
        for (int i = 0; i < to.length; i++) {
            MarktEvent.Gift g = e.wichtel.get(i);
            MarktEvent.Gift receiver = e.wichtel.get(to[i]);
            MarktRuntime.data().pickup.computeIfAbsent(receiver.from, k -> new ArrayList<>()).add(g.item);
            ServerPlayer p = online(receiver.from);
            if (p != null) {
                p.sendSystemMessage(Component.literal("🎁 Dein Wichtelgeschenk ist da! Abholen am Auftragsbrett (Abholfach) oder am Wichtelbaum.")
                        .withStyle(ChatFormatting.GREEN));
            }
        }
        out.add("Wichteln: " + e.wichtel.size() + " Geschenke verteilt – abholen im Abholfach.");
        e.wichtel.clear();
        return out;
    }

    /** Kategorien der Bestenliste. */
    record Category(String key, String title, Function<MarktEvent.Stats, Long> value, Function<Long, String> fmt) {}

    static final List<Category> CATEGORIES = List.of(
            new Category("sales", "Bester Umsatz", s -> s.sales, MarktRuntime::fmt),
            new Category("jobs", "Fleißigste Helfer", s -> s.jobs, v -> v + (v == 1 ? " Auftrag" : " Aufträge")),
            new Category("snow", "Räumdienst", s -> s.snow, v -> v + " Blöcke"),
            new Category("gifts", "Geschenkesuche", s -> s.gifts, v -> v + (v == 1 ? " Geschenk" : " Geschenke")));

    static Map<String, Long> values(Category c) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Map.Entry<String, MarktEvent.Stats> s : ev().stats.entrySet()) m.put(s.getKey(), c.value().apply(s.getValue()));
        return m;
    }

    /** Stimmen je Besitzer (der schönste Stand gewinnt für seinen Besitzer). */
    static Map<String, Long> voteValues() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Map.Entry<String, Long> v : ev().voteCount().entrySet()) {
            Plot p = MarktRuntime.data().plots.get(v.getKey());
            if (p != null) m.put(p.id, v.getValue());
        }
        return m;
    }

    private static List<String> contest() {
        List<String> out = new ArrayList<>();
        List<Double> prizes = cfg().prizes;
        long now = MarktRuntime.now();
        if (cfg().vote) {
            var top = MarketMath.top(voteValues(), Math.max(1, prizes.size()));
            if (!top.isEmpty()) out.add("Schönster Stand:");
            for (int i = 0; i < top.size(); i++) {
                Plot p = MarktRuntime.data().plots.get(top.get(i).getKey());
                long prize = i < prizes.size() ? MarketMath.tenths(prizes.get(i)) : 0;
                String owner = p.active(now) ? p.owner : "";
                if (!owner.isEmpty() && prize > 0) payTo(owner, prize, "Schönster Stand: Platz " + (i + 1));
                out.add("  " + (i + 1) + ". " + p.name + (owner.isEmpty() ? "" : " (" + p.ownerName + ")") + " – " + top.get(i).getValue() + (top.get(i).getValue() == 1 ? " Stimme" : " Stimmen")
                        + (prize > 0 && !owner.isEmpty() ? ", " + MarktRuntime.fmt(prize) : ""));
            }
        }
        for (Category c : CATEGORIES) {
            var top = MarketMath.top(values(c), Math.max(1, prizes.size()));
            if (top.isEmpty()) continue;
            out.add(c.title() + ":");
            for (int i = 0; i < top.size(); i++) {
                long prize = i < prizes.size() ? MarketMath.tenths(prizes.get(i)) : 0;
                String who = top.get(i).getKey();
                if (prize > 0) payTo(who, prize, c.title() + ": Platz " + (i + 1));
                out.add("  " + (i + 1) + ". " + name(who) + " – " + c.fmt().apply(top.get(i).getValue()) + (prize > 0 ? ", " + MarktRuntime.fmt(prize) : ""));
            }
        }
        return out;
    }

    private static void fireworks() {
        if (!cfg().fireworks) return;
        List<double[]> spots = new ArrayList<>();
        var top = MarketMath.top(voteValues(), 1);
        Box area = MarktRuntime.data().area;
        String dim = area != null ? area.dim : "minecraft:overworld";
        if (!top.isEmpty()) {
            Plot p = MarktRuntime.data().plots.get(top.getFirst().getKey());
            dim = p.box.dim;
            spots.add(new double[]{p.box.centerX(), p.box.maxY + 2, p.box.centerZ()});
        }
        if (area != null) {
            for (int i = 0; i < 4; i++) spots.add(new double[]{area.minX + RND.nextInt(Math.max(1, area.sizeX())), area.maxY + 1, area.minZ + RND.nextInt(Math.max(1, area.sizeZ()))});
        } else {
            for (ServerPlayer p : MarktRuntime.server().getPlayerList().getPlayers()) spots.add(new double[]{p.getX(), p.getY() + 3, p.getZ()});
        }
        ServerLevel level = Levels.of(dim);
        if (level == null) return;
        int[][] colors = {{0xE5484D, 0xFFFFFF}, {0xF2B13A, 0xFFE9A8}, {0x55E07A, 0xE5484D}};
        for (double[] s : spots) {
            int[] col = colors[RND.nextInt(colors.length)];
            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            rocket.set(DataComponents.FIREWORKS, new Fireworks(1 + RND.nextInt(2), List.of(new FireworkExplosion(
                    RND.nextBoolean() ? FireworkExplosion.Shape.STAR : FireworkExplosion.Shape.LARGE_BALL,
                    IntList.of(col), IntList.of(0xFFFFFF), true, true))));
            level.addFreshEntity(new FireworkRocketEntity(level, s[0], s[1], s[2], rocket));
        }
    }

    // ------------------------------------------------------------------ Statistik, Stimmen

    public static void recordSale(String owner, String name, long amount) {
        if (!running() || owner.isEmpty()) return;
        ev().stats(owner, name).sales += amount;
        MarktRuntime.markDirty();
    }

    public static void recordSnow(ServerPlayer p) {
        if (!running()) return;
        ev().stats(p.getUUID().toString(), p.getGameProfile().name()).snow++;
    }

    public static void recordJob(ServerPlayer p) {
        if (!running()) return;
        ev().stats(p.getUUID().toString(), p.getGameProfile().name()).jobs++;
    }

    public static void recordGift(ServerPlayer p) {
        if (!running()) return;
        ev().stats(p.getUUID().toString(), p.getGameProfile().name()).gifts++;
    }

    /** Stimme für den schönsten Stand. @return {Meldung, null=ok/sonst Fehler} */
    public static String[] vote(ServerPlayer p, String plotId) {
        if (!running()) return new String[]{"Abstimmen geht nur, solange der Weihnachtsmarkt geöffnet ist.", "x"};
        if (!cfg().vote) return new String[]{"Die Abstimmung ist abgeschaltet.", "x"};
        Plot plot = MarktRuntime.data().plots.get(plotId == null ? "" : plotId);
        long now = MarktRuntime.now();
        if (plot == null || !plot.active(now)) return new String[]{"Für diesen Stand kann man nicht stimmen.", "x"};
        if (plot.member(p.getUUID().toString(), now)) return new String[]{"Für den eigenen Stand stimmst du nicht.", "x"};
        String before = ev().votes.put(p.getUUID().toString(), plot.id);
        MarktRuntime.markDirty();
        if (plot.id.equals(before)) return new String[]{"Du hast schon für „" + plot.name + "“ gestimmt.", null};
        return new String[]{"Deine Stimme geht an „" + plot.name + "“" + (before != null ? " (vorher: anderer Stand)" : "") + ". ★", null};
    }

    // ------------------------------------------------------------------ Anzeige

    public static Views.EventInfo info(ServerPlayer p, boolean admin) {
        MarktEvent e = ev();
        MarktConfig.Event c = cfg();
        long now = MarktRuntime.now();
        Views.EventInfo i = new Views.EventInfo();
        i.phase = e.phase.name();
        i.round = e.round;
        i.status = switch (e.phase) {
            case CLOSED -> "geschlossen";
            case OPENING -> "Eröffnung!";
            case OPEN -> "geöffnet – noch " + MarketMath.duration(e.endsAt - now);
            case FINALE -> "Siegerehrung";
        };
        i.remaining = e.running() ? Math.max(0, e.endsAt - now) : 0;
        i.total = Math.max(1, e.endsAt - e.startedAt);
        for (String s : c.schedule) i.schedule.add(Schedule.pretty(s));
        Schedule.Window next = new Schedule(c.schedule).next(LocalDateTime.now());
        i.next = next == null ? "" : DAY.format(next.start());
        i.lottery = c.lottery;
        i.wichteln = c.wichteln;
        i.vote = c.vote;
        i.advent = c.advent;
        String me = p.getUUID().toString();
        i.myTickets = e.tickets.getOrDefault(me, 0);
        i.maxTickets = c.maxTickets;
        i.ticketsTotal = e.tickets.values().stream().mapToInt(Integer::intValue).sum();
        i.players = e.tickets.size();
        i.ticketPrice = ticketPrice();
        i.pot = e.pot;
        i.potPayout = MarketMath.percent(e.pot, c.potPayoutPercent);
        String vote = e.votes.get(me);
        Plot vp = vote == null ? null : MarktRuntime.data().plots.get(vote);
        i.myVote = vp == null ? "" : vp.name;
        for (MarktEvent.Gift g : e.wichtel) {
            if (g.from.equals(me)) i.wichtelGift = Stacks.fromJson(p.level().registryAccess(), g.item).getHoverName().getString();
        }
        i.wichtelCount = e.wichtel.size();
        i.boards = boards(5);
        i.lastResults = new ArrayList<>(e.lastResults);
        i.giftReward = MarketMath.tenths(c.giftReward);
        Box area = MarktRuntime.data().area;
        i.area = area == null ? "" : area.size() + " · " + area;
        Box lb = MarktRuntime.data().leaderboard;
        i.leaderboard = lb == null ? "" : lb.minX + " " + lb.minY + " " + lb.minZ;
        if (admin) {
            i.atmosPreset = c.atmosPreset;
            i.atmosLoaded = AtmosCompat.loaded();
            i.music = c.music;
            i.bossbar = c.bossbar;
            i.onlyDuringEvent = c.onlyDuringEvent;
            i.weatherSnow = c.weatherSnow;
            i.fireworks = c.fireworks;
            i.defaultMinutes = c.defaultMinutes;
        }
        return i;
    }

    static List<Views.Board> boards(int n) {
        List<Views.Board> out = new ArrayList<>();
        if (cfg().vote) {
            Views.Board b = new Views.Board();
            b.title = "Schönster Stand";
            int k = 1;
            for (var t : MarketMath.top(voteValues(), n)) {
                Plot p = MarktRuntime.data().plots.get(t.getKey());
                b.lines.add(k++ + ". " + p.name + " – " + t.getValue() + " ★");
            }
            out.add(b);
        }
        for (Category c : CATEGORIES) {
            Views.Board b = new Views.Board();
            b.title = c.title();
            int k = 1;
            for (var t : MarketMath.top(values(c), n)) b.lines.add(k++ + ". " + name(t.getKey()) + " – " + c.fmt().apply(t.getValue()));
            out.add(b);
        }
        return out;
    }

    /** Text der Bestenliste-Tafel; wechselt alle 10 Sekunden die Kategorie. */
    static Component leaderboardText() {
        List<Views.Board> boards = boards(5);
        MutableComponent t = Component.literal("⭐ Bestenliste · " + MarktRuntime.config().title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        if (ev().phase == MarktEvent.Phase.CLOSED) {
            t.append(Holograms.line(ev().lastResults.isEmpty() ? "Noch kein Event gelaufen" : "Letztes Event:", ChatFormatting.GRAY));
            for (int i = 1; i < ev().lastResults.size() && i < 9; i++) t.append(Holograms.line(ev().lastResults.get(i).trim(), ChatFormatting.WHITE));
            return t;
        }
        Views.Board b = boards.get((int) ((System.currentTimeMillis() / 10_000) % boards.size()));
        t.append(Holograms.line(b.title, ChatFormatting.YELLOW));
        if (b.lines.isEmpty()) t.append(Holograms.line("noch niemand", ChatFormatting.GRAY));
        for (String l : b.lines) t.append(Holograms.line(l, ChatFormatting.WHITE));
        return t;
    }

    // ------------------------------------------------------------------ Hilfen

    private static String name(String uuid) {
        MarktEvent.Stats s = ev().stats.get(uuid);
        if (s != null && !s.name.isBlank()) return s.name;
        ServerPlayer p = online(uuid);
        if (p != null) return p.getGameProfile().name();
        MarktData.Pending pend = MarktRuntime.data().pending.get(uuid);
        if (pend != null && !pend.name.isBlank()) return pend.name;
        for (Plot pl : MarktRuntime.data().plots.values()) if (uuid.equals(pl.owner)) return pl.ownerName;
        return uuid.substring(0, 8);
    }

    static ServerPlayer online(String uuid) {
        try {
            return MarktRuntime.server().getPlayerList().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static void payTo(String uuid, long amount, String reason) {
        if (amount <= 0) return;
        ServerPlayer p = online(uuid);
        if (p != null) {
            Pay.payout(p, amount, false, "markt:event", reason);
            p.sendSystemMessage(Component.literal("🎉 " + reason + ": " + MarktRuntime.fmt(amount)).withStyle(ChatFormatting.GOLD));
        } else {
            Pay.later(uuid, name(uuid), amount, reason);
        }
    }

    static void broadcast(Component c) {
        MarktRuntime.server().getPlayerList().broadcastSystemMessage(c, false);
    }

    private static void title(Component title, Component sub) {
        for (ServerPlayer p : MarktRuntime.server().getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }
}
