package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import de.twogg.kit.KitToggle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Marktverwaltung: Stände anlegen (aus der Marktplaner-Auswahl), Preise und Art, räumen, zurücksetzen; Ankauf,
 * Aufträge, Räumzonen und Regeln. Jede Aktion prüft der Server.
 */
public class AdminScreen extends MarktScreen {
    private enum Page { STALLS, NEW, EVENT, BUYER, JOBS, ZONES, RULES }

    private static final String[] NAMES = {"Stände", "Neu", "Event", "Ankauf", "Aufträge", "Räumzonen", "Regeln"};
    private static final KitIcon[] ICONS = {KitIcon.MAP, KitIcon.PLUS, KitIcon.BOLT, KitIcon.BOX, KitIcon.GRID, KitIcon.CURSOR, KitIcon.GEAR};
    private static final String[] MODES = {"BOTH", "RENT", "BUY", "LOCKED"};
    private static final String[] MODE_NAMES = {"Miete + Kauf", "nur Miete", "nur Kauf", "gesperrt"};

    private Page page = Page.STALLS;
    private String selected = "";
    private String nameText = "", rentText = "", buyText = "", periodsText = "", noteText = "";
    private String newName = "", newRent = "", newBuy = "";
    private int rulesPage;
    private String minutesText = "", atmosText = null;
    private String priceText = "", bundleText = "", demandText = "";
    private String selBuyer = "", selJob = "", selZone = "";
    private final String[] settingText = new String[16];

    public AdminScreen(Screen parent) {
        this(parent, null);
        send("open");
    }

    AdminScreen(Screen parent, Views.View v) {
        super(parent, v == null ? new Views.View() : v, "Marktverwaltung", "Admin · TwoGG Markt", 96);
        if (v != null) afterUpdate();
    }

    @Override
    String screenId() {
        return "admin";
    }

    @Override
    protected void afterUpdate() {
        if (plot(selected) == null) {
            selected = view.plots.isEmpty() ? "" : view.plots.getFirst().id;
            fillPlot();
        }
        armed = "";
    }

    private Views.PlotLine plot(String id) {
        for (Views.PlotLine p : view.plots) if (p.id.equals(id)) return p;
        return null;
    }

    private void fillPlot() {
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        nameText = p.name;
        rentText = num(p.rentEuro);
        buyText = num(p.buyEuro);
        periodsText = String.valueOf(p.maxPeriods);
        noteText = p.note;
    }

    private void go(Page p) {
        page = p;
        offset = 0;
        armed = "";
        view.message = "";
        rebuildWidgets();
    }

    private Views.Action act(String a) {
        Views.Action x = Views.Action.of(a);
        x.plot = selected;
        return x;
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14) / ROW);
    }

    @Override
    protected int listTotal() {
        return switch (page) {
            case STALLS -> view.plots.size();
            case BUYER -> view.buyerItems.size();
            case JOBS -> view.jobs.size();
            case ZONES -> view.zones.size();
            default -> 0;
        };
    }

    private int listW() {
        return Math.min(150, (cw - 2 * PAD) * 2 / 5);
    }

    // ------------------------------------------------------------------ Aufbau

    @Override
    protected void build() {
        for (int i = 0; i < NAMES.length; i++) {
            Page p = Page.values()[i];
            int idx = i;
            addNav(i, new KitNav(0, 0, 0, ICONS[i], NAMES[i], () -> count(idx), () -> page == p, () -> go(p)));
        }
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        addFooterButton(KitButton.second(0, 0, 80, "Aktualisieren", b -> send("refresh")));
        if (!view.allowed) return;
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        switch (page) {
            case STALLS -> buildStalls(x, y, w);
            case NEW -> buildNew(x, y, w);
            case EVENT -> buildEvent(x, y, w);
            case BUYER -> buildBuyer(x, y, w);
            case JOBS -> buildJobs(x, y, w);
            case ZONES -> buildZones(x, y, w);
            case RULES -> buildRules(x, y, w);
        }
    }

    private String count(int idx) {
        return switch (idx) {
            case 0 -> String.valueOf(view.plots.size());
            case 2 -> view.event != null && ("OPEN".equals(view.event.phase) || "OPENING".equals(view.event.phase)) ? "●" : "";
            case 3 -> String.valueOf(view.buyerItems.size());
            case 4 -> String.valueOf(view.jobs.size());
            case 5 -> String.valueOf(view.zones.size());
            default -> "";
        };
    }

    /** Linke Auswahlliste mit Blättern. */
    private void list(int x, int y, int lw, List<String> ids, List<String> labels, String sel, java.util.function.Consumer<String> pick) {
        int rows = listRows();
        pager(x + lw, y - 2, ids.size(), rows);
        for (int i = 0; i < rows && offset + i < ids.size(); i++) {
            String id = ids.get(offset + i);
            addRenderableWidget(new KitButton(x, y + 14 + i * ROW, lw, 20, Component.literal(KitDraw.fit(labels.get(offset + i), lw - 10)), null,
                    id.equals(sel) ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, b -> pick.accept(id)).accent(accent()));
        }
    }

    private void buildStalls(int x, int y, int w) {
        int lw = listW();
        list(x, y, lw, view.plots.stream().map(p -> p.id).toList(), view.plots.stream().map(p -> p.name).toList(), selected, id -> {
            selected = id;
            fillPlot();
            armed = "";
            rebuildWidgets();
        });
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        int dx = x + lw + 10, dw = x + w - dx;
        int fy = y + 18;
        int nameW = (dw - 4) * 2 / 5;
        input(dx, fy, nameW, "Name", nameText, 32, t -> nameText = t);
        input(dx + nameW + 4, fy, dw - nameW - 4, "Beschreibung für Spieler", noteText, 60, t -> noteText = t);
        int third = (dw - 8) / 3;
        input(dx, fy + 22, third, "Miete", rentText, 8, t -> rentText = t, "€/" + view.periodName);
        input(dx + third + 4, fy + 22, third, "Kauf", buyText, 9, t -> buyText = t, "€");
        input(dx + 2 * (third + 4), fy + 22, third, "Voraus", periodsText, 3, t -> periodsText = t, "max");
        int by = fy + 44;
        int half = (dw - 4) / 2;
        addRenderableWidget(KitButton.primary(dx, by, half, "Speichern", b -> save(p)).accent(accent()));
        int mi = 0;
        for (int i = 0; i < MODES.length; i++) if (MODES[i].equals(p.mode)) mi = i;
        int next = (mi + 1) % MODES.length;
        addRenderableWidget(KitButton.second(dx + half + 4, by, half, "Art: " + MODE_NAMES[mi], b -> {
            Views.Action a = act("plot_mode");
            a.text = MODES[next];
            send(a);
        }));
        by += 22;
        int q = (dw - 12) / 4;
        addRenderableWidget(KitButton.second(dx, by, q, "Hin", b -> { send(act("plot_tp")); onClose(); }));
        addRenderableWidget(KitButton.second(dx + q + 4, by, q, "Zeigen", b -> send(act("plot_show"))));
        addRenderableWidget(KitButton.second(dx + 2 * (q + 4), by, q, "Sichern", b -> send(act("plot_snapshot"))));
        KitButton reset = confirm(dx + 3 * (q + 4), by, q, "Reset", "reset:" + p.id, () -> send(act("plot_reset")));
        reset.active = p.hasSnapshot;
        by += 22;
        if (!p.owner.isEmpty()) {
            confirm(dx, by, half, "Räumen", "evict:" + p.id, () -> send(act("plot_evict")));
            if ("RENTED".equals(p.status)) {
                addRenderableWidget(KitButton.second(dx + half + 4, by, half, "+1 " + view.periodName + " gratis", b -> {
                    Views.Action a = act("plot_extend");
                    a.count = 1;
                    send(a);
                }));
            }
        } else {
            confirm(dx, by, half, "Stand löschen", "del:" + p.id, () -> send(act("plot_delete")));
        }
    }

    private static final String[][] EVENT_TOGGLES = {{"music", "Musik"}, {"bossbar", "Bossbar"}, {"onlyDuringEvent", "Handel nur im Event"},
            {"weatherSnow", "Schneefall"}, {"fireworks", "Feuerwerk"}};

    private boolean eventFlag(String key) {
        Views.EventInfo e = view.event;
        if (e == null) return false;
        return switch (key) {
            case "music" -> e.music;
            case "bossbar" -> e.bossbar;
            case "onlyDuringEvent" -> e.onlyDuringEvent;
            case "weatherSnow" -> e.weatherSnow;
            case "fireworks" -> e.fireworks;
            default -> false;
        };
    }

    private void buildEvent(int x, int y, int w) {
        Views.EventInfo e = view.event;
        if (e == null) return;
        boolean run = "OPEN".equals(e.phase) || "OPENING".equals(e.phase);
        int ry = y + 14;
        input(x, ry, 60, String.valueOf(e.defaultMinutes), minutesText, 4, t -> minutesText = t, "min");
        addRenderableWidget(KitButton.primary(x + 64, ry, 60, "Start", b -> {
            Views.Action a = Views.Action.of("event_start");
            a.count = Math.max(0, parseInt(minutesText));
            send(a);
        }).accent(accent())).active = !run;
        addRenderableWidget(KitButton.second(x + 128, ry, 100, "Siegerehrung", b -> send("event_stop"))).active = run;
        KitButton abort = confirm(x + 232, ry, 76, "Abbrechen", "abort", () -> send("event_abort"));
        abort.active = !"CLOSED".equals(e.phase);
        ry += 24;
        addRenderableWidget(KitButton.second(x, ry, 96, "Gelände setzen", b -> send("area_set"))).active = view.selection != null && view.selection.complete;
        addRenderableWidget(KitButton.second(x + 100, ry, 76, "Gelände weg", b -> send("area_clear"))).active = !e.area.isEmpty();
        addRenderableWidget(KitButton.second(x + 180, ry, 70, "Tafel hier", b -> send("leaderboard_here")));
        addRenderableWidget(KitButton.second(x + 254, ry, 64, "Tafel weg", b -> send("leaderboard_remove"))).active = !e.leaderboard.isEmpty();
        int ty = y + 84, colW = (w - 8) / 2;
        for (int i = 0; i < EVENT_TOGGLES.length; i++) {
            String key = EVENT_TOGGLES[i][0];
            addRenderableWidget(new KitToggle(x + (i % 2) * (colW + 8) + colW - KitToggle.W, ty + (i / 2) * 14, Component.literal(EVENT_TOGGLES[i][1]),
                    () -> eventFlag(key), on -> {
                Views.Action a = Views.Action.of("setting");
                a.id = key;
                a.flag = on;
                send(a);
            }).accent(accent()));
        }
        if (atmosText == null) atmosText = e.atmosPreset;
        int ax = x + colW + 8, ayy = ty + 28 + 4;
        input(ax, ayy, colW - 30, "Atmos-Preset", atmosText, 32, t -> atmosText = t);
        addRenderableWidget(KitButton.second(ax + colW - 26, ayy, 26, "OK", b -> {
            Views.Action a = Views.Action.of("atmos");
            a.text = atmosText;
            send(a);
        }));
    }

    private void drawEvent(GuiGraphicsExtractor g, int x, int y, int w) {
        Views.EventInfo e = view.event;
        if (e == null) return;
        KitDraw.label(g, KitDraw.fit("Weihnachtsmarkt #" + e.round + " · " + e.status + (e.next.isEmpty() ? "" : " · nächste Öffnung " + e.next), w), x, y);
        int ty = y + 62;
        text(g, KitDraw.fit("Gelände: " + (e.area.isEmpty() ? "ganzer Server (Planer-Auswahl → „Gelände setzen“)" : e.area), w), x, ty, KitColors.TEXT_SOFT);
        text(g, KitDraw.fit("Bestenliste-Tafel: " + (e.leaderboard.isEmpty() ? "keine" : e.leaderboard) + " · Lose " + e.ticketsTotal
                + " · Topf " + money(e.pot) + " · Wichtel " + e.wichtelCount + " · Atmos " + (e.atmosLoaded ? "da" : "nicht installiert"), w),
                x, ty + 10, KitColors.MUTED);
        int colW = (w - 8) / 2;
        for (int i = 0; i < EVENT_TOGGLES.length; i++) {
            text(g, EVENT_TOGGLES[i][1], x + (i % 2) * (colW + 8), y + 84 + (i / 2) * 14 + 2, KitColors.TEXT_SOFT);
        }
    }

    private void save(Views.PlotLine p) {
        if (!nameText.equals(p.name)) { Views.Action a = act("plot_name"); a.text = nameText; send(a); }
        double r = parseDouble(rentText), k = parseDouble(buyText);
        int n = parseInt(periodsText);
        if (r >= 0 && r != p.rentEuro) { Views.Action a = act("plot_rent"); a.value = r; send(a); }
        if (k >= 0 && k != p.buyEuro) { Views.Action a = act("plot_buy"); a.value = k; send(a); }
        if (n > 0 && n != p.maxPeriods) { Views.Action a = act("plot_periods"); a.count = n; send(a); }
        if (!noteText.equals(p.note)) { Views.Action a = act("plot_note"); a.text = noteText; send(a); }
    }

    private void buildNew(int x, int y, int w) {
        Views.Selection s = view.selection;
        int fy = y + 76;
        int third = (w - 8) / 3;
        input(x, fy, third + third + 4, "Name des Stands / der Zone", newName, 32, t -> newName = t);
        input(x + 2 * (third + 4), fy, (third - 4) / 2, "Miete", newRent, 8, t -> newRent = t, "€");
        input(x + 2 * (third + 4) + (third - 4) / 2 + 4, fy, (third - 4) / 2, "Kauf", newBuy, 9, t -> newBuy = t, "€");
        boolean ok = s != null && s.complete;
        int by = fy + 26;
        KitButton create = addRenderableWidget(KitButton.primary(x, by, 120, "Stand anlegen", b -> {
            Views.Action a = Views.Action.of("plot_create");
            a.name = newName;
            a.value = Math.max(0, parseDouble(newRent));
            a.amount = Math.max(0, Math.round(parseDouble(newBuy) * 10));
            send(a);
            newName = "";
        }).accent(accent()));
        create.active = ok && (s.overlap == null || s.overlap.isEmpty());
        KitButton zone = addRenderableWidget(KitButton.second(x + 124, by, 110, "Als Räumzone", b -> {
            Views.Action a = Views.Action.of("zone_create");
            a.name = newName;
            send(a);
            newName = "";
        }));
        zone.active = ok;
        by += 24;
        for (int up : new int[]{1, 4}) {
            KitButton e = addRenderableWidget(KitButton.second(x + (up == 1 ? 0 : 74), by, 70, "+" + up + " hoch", b -> {
                Views.Action a = Views.Action.of("sel_expand");
                a.count = up;
                send(a);
            }));
            e.active = ok;
        }
        addRenderableWidget(KitButton.second(x + 148, by, 86, "Auswahl weg", b -> send("sel_clear"))).active = s != null;
    }

    private void buildBuyer(int x, int y, int w) {
        int lw = listW();
        list(x, y, lw, view.buyerItems.stream().map(l -> l.name).toList(),
                view.buyerItems.stream().map(l -> l.name.replace("minecraft:", "")).toList(), selBuyer, id -> {
            selBuyer = id;
            for (Views.BuyerLine l : view.buyerItems) {
                if (l.name.equals(id)) {
                    priceText = money(l.basePrice, "");
                    bundleText = String.valueOf(l.bundle);
                    demandText = String.valueOf(l.demand);
                }
            }
            armed = "";
            rebuildWidgets();
        });
        int dx = x + lw + 10, dw = x + w - dx;
        int third = (dw - 8) / 3;
        int fy = y + 70;
        input(dx, fy, third, "Preis", priceText, 8, s -> priceText = s, view.unit);
        input(dx + third + 4, fy, third, "Bündel", bundleText, 4, s -> bundleText = s, "Stk");
        input(dx + 2 * (third + 4), fy, third, "Bedarf", demandText, 5, s -> demandText = s, "/Tag");
        int by = fy + 24;
        if (!selBuyer.isEmpty()) {
            int half = (dw - 4) / 2;
            addRenderableWidget(KitButton.primary(dx, by, half, "Ändern", b -> {
                Views.Action a = Views.Action.of("buyer_edit");
                a.id = selBuyer;
                a.value = Math.max(0, parseDouble(priceText));
                a.count = Math.max(0, parseInt(bundleText));
                a.amount = Math.max(0, parseInt(demandText));
                send(a);
            }).accent(accent()));
            confirm(dx + half + 4, by, half, "Entfernen", "buyrm:" + selBuyer, () -> {
                Views.Action a = Views.Action.of("buyer_remove");
                a.id = selBuyer;
                selBuyer = "";
                send(a);
            });
            by += 24;
        }
        KitButton add = addRenderableWidget(KitButton.second(dx, by, dw, KitDraw.fit(view.hand.isEmpty() ? "Item in die Hand nehmen …"
                : "Neu aus Hand: " + view.handName, dw - 10), b -> {
            Views.Action a = Views.Action.of("buyer_add");
            a.value = Math.max(0, parseDouble(priceText));
            a.count = Math.max(1, parseInt(bundleText));
            send(a);
        }));
        add.active = !view.hand.isEmpty();
    }

    private void buildJobs(int x, int y, int w) {
        int lw = listW();
        list(x, y, lw, view.jobs.stream().map(j -> j.id).toList(),
                view.jobs.stream().map(j -> (j.system ? "" : "★ ") + j.title).toList(), selJob, id -> {
            selJob = id;
            armed = "";
            rebuildWidgets();
        });
        int dx = x + lw + 10, dw = x + w - dx;
        int by = cy + ch - PAD - 20;
        addRenderableWidget(KitButton.primary(dx, by, Math.min(dw, 150), "Neue Marktaufträge", b -> send("jobs_reroll")).accent(accent()));
        if (!selJob.isEmpty()) {
            confirm(dx, by - 24, Math.min(dw, 150), "Auftrag entfernen", "job:" + selJob, () -> {
                Views.Action a = Views.Action.of("job_remove");
                a.id = selJob;
                selJob = "";
                send(a);
            });
        }
    }

    private void buildZones(int x, int y, int w) {
        int lw = listW();
        list(x, y, lw, view.zones.stream().map(z -> z.id).toList(), view.zones.stream().map(z -> z.name).toList(), selZone, id -> {
            selZone = id;
            armed = "";
            rebuildWidgets();
        });
        int dx = x + lw + 10, dw = x + w - dx;
        if (!selZone.isEmpty()) {
            confirm(dx, y + 60, Math.min(dw, 140), "Zone löschen", "zone:" + selZone, () -> {
                Views.Action a = Views.Action.of("zone_delete");
                a.id = selZone;
                selZone = "";
                send(a);
            });
        }
    }

    // Regeln: Schalter oben, Zahlen darunter in zwei Spalten
    private static final String[][] TOGGLES = {
            {"protectFree", "Freie Stände schützen"}, {"allowCash", "Bargeld erlaubt"}, {"resetOnRelease", "Bei Freiwerden zurücksetzen"},
            {"buyerEnabled", "Ankauf offen"}, {"buyerPayCash", "Ankauf zahlt bar"}, {"jobsEnabled", "Auftragsbrett"},
            {"playerJobs", "Spieleraufträge"}, {"snowEnabled", "Räumdienst"}, {"holograms", "Hologramme"}};
    private static final String[][] NUMBERS = {
            {"periodMinutes", "Zeitraum (Min.)"}, {"maxPerPlayer", "Stände pro Spieler"}, {"maxHelpers", "Helfer pro Stand"},
            {"feePercent", "Marktgebühr %"}, {"buybackPercent", "Rückkauf %"}, {"rentRefundPercent", "Erstattung Miete %"},
            {"counterPrice", "Theke €"}, {"jobsActive", "Marktaufträge"}, {"jobsSlots", "Plätze pro Auftrag"},
            {"snowPay", "Räumlohn Coins"}, {"snowBlocksPerPay", "Blöcke pro Lohn"}, {"snowDailyMax", "Räumlohn/Tag"}};

    private boolean flag(String key) {
        Views.Settings s = view.settings;
        if (s == null) return false;
        return switch (key) {
            case "protectFree" -> s.protectFree;
            case "allowCash" -> s.allowCash;
            case "resetOnRelease" -> s.resetOnRelease;
            case "buyerEnabled" -> s.buyerEnabled;
            case "buyerPayCash" -> s.buyerPayCash;
            case "jobsEnabled" -> s.jobsEnabled;
            case "playerJobs" -> s.playerJobs;
            case "snowEnabled" -> s.snowEnabled;
            case "holograms" -> s.holograms;
            default -> false;
        };
    }

    private double number(String key) {
        Views.Settings s = view.settings;
        if (s == null) return 0;
        return switch (key) {
            case "periodMinutes" -> s.periodMinutes;
            case "maxPerPlayer" -> s.maxPerPlayer;
            case "maxHelpers" -> s.maxHelpers;
            case "feePercent" -> s.feePercent;
            case "buybackPercent" -> s.buybackPercent;
            case "rentRefundPercent" -> s.rentRefundPercent;
            case "counterPrice" -> s.counterPrice;
            case "jobsActive" -> s.jobsActive;
            case "jobsSlots" -> s.jobsSlots;
            case "snowPay" -> s.snowPay;
            case "snowBlocksPerPay" -> s.snowBlocksPerPay;
            case "snowDailyMax" -> s.snowDailyMax;
            default -> 0;
        };
    }

    private int numbersPerPage(int ny, int rhN) {
        return Math.max(2, (cy + ch - PAD - ny) / rhN * 2);
    }

    private int rulesRowH() {
        return ch < 230 ? 15 : 17;
    }

    private void buildRules(int x, int y, int w) {
        if (view.settings == null) return;
        int colW = (w - 8) / 2;
        int rh = 14;
        for (int i = 0; i < TOGGLES.length; i++) {
            String key = TOGGLES[i][0];
            int tx = x + (i % 2) * (colW + 8) + colW - KitToggle.W, ty = y + 12 + (i / 2) * rh;
            addRenderableWidget(new KitToggle(tx, ty, Component.literal(TOGGLES[i][1]), () -> flag(key), on -> {
                Views.Action a = Views.Action.of("setting");
                a.id = key;
                a.flag = on;
                send(a);
            }).accent(accent()));
        }
        int ny = y + 12 + ((TOGGLES.length + 1) / 2) * rh + 8;
        int rhN = rulesRowH() + 2;
        int per = numbersPerPage(ny, rhN);
        int first = rulesPage * per;
        if (first >= NUMBERS.length) { rulesPage = 0; first = 0; }
        if (per < NUMBERS.length) {
            int pages = (NUMBERS.length + per - 1) / per;
            addHeaderButton(KitButton.second(0, 0, 70, "Werte " + (rulesPage + 1) + "/" + pages, b -> {
                rulesPage = (rulesPage + 1) % pages;
                rebuildWidgets();
            }));
        }
        for (int k = 0; k < per && first + k < NUMBERS.length; k++) {
            int i = first + k;
            String key = NUMBERS[i][0];
            int idx = i;
            int nx = x + (k % 2) * (colW + 8), yy = ny + (k / 2) * rhN;
            if (settingText[idx] == null) settingText[idx] = num(number(key));
            var box = new net.minecraft.client.gui.components.EditBox(font, nx + colW - 74, yy + 3, 40, 10, Component.literal(key));
            box.setBordered(false);
            box.setMaxLength(8);
            box.setTextColor(KitColors.TEXT);
            box.setValue(settingText[idx]);
            box.setResponder(s -> settingText[idx] = s);
            addRenderableWidget(box);
            addRenderableWidget(new KitButton(nx + colW - 28, yy, 28, rhN - 2, Component.literal("OK"), null, KitButton.Style.SECOND, b -> {
                double v = parseDouble(settingText[idx]);
                if (v < 0) return;
                Views.Action a = Views.Action.of("setting");
                a.id = key;
                a.value = v;
                send(a);
                settingText[idx] = null;
            }));
        }
        addHeaderButton(KitButton.second(0, 0, 90, "Neu laden", b -> {
            java.util.Arrays.fill(settingText, null);
            send("reload");
        }));
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        if (!view.allowed) {
            KitDraw.wrapped(g, Component.literal("Die Marktverwaltung ist nur für Admins."), x, y, w, KitColors.AMBER_TEXT);
            footer(g);
            return;
        }
        switch (page) {
            case STALLS -> drawStalls(g, x, y, w);
            case NEW -> drawNew(g, x, y, w);
            case EVENT -> drawEvent(g, x, y, w);
            case BUYER -> drawBuyer(g, x, y, w);
            case JOBS -> drawJobs(g, x, y, w);
            case ZONES -> drawZones(g, x, y, w);
            case RULES -> drawRules(g, x, y, w);
        }
        footer(g);
    }

    private void drawStalls(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Stände", x, y);
        if (view.plots.isEmpty()) {
            KitDraw.wrapped(g, Component.literal("Noch keine Stände. Seite „Neu“: mit dem Marktplaner (/markt planer) zwei Ecken setzen."),
                    x, y + 16, w, KitColors.TEXT_SOFT);
            return;
        }
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        int dx = x + listW() + 10, dw = x + w - dx;
        String status = MarketScreen.statusText(p, view.periodName);
        if ("RENTED".equals(p.status)) status = "vermietet an " + p.ownerName + " · noch " + duration(p.remaining);
        if ("OWNED".equals(p.status)) status = "gekauft von " + p.ownerName;
        int titleW = Math.min(KitDraw.titleWidth(p.name), dw / 2);
        KitDraw.drawTitle(g, KitDraw.fit(p.name, dw / 2), dx, y - 2, KitColors.TEXT);
        text(g, KitDraw.fit(status, dw - titleW - 8), dx + titleW + 8, y + 2, MarketScreen.statusColor(p.status));
        int ty = y + 18 + 66 + 22 + 22 + 4;   // unter den Knöpfen
        String info = p.size + " · " + p.pos + " · = " + money(p.rentTenths) + " / " + money(p.buyTenths);
        if (ty + 9 <= cy + ch - PAD) text(g, KitDraw.fit(info, dw), dx, ty, KitColors.MUTED);
        ty += 11;
        String extra = !p.owner.isEmpty() ? "Kasse " + money(p.till) + " · Umsatz " + money(p.salesTotal) + " · " + p.offers + " Angebote"
                + (p.helpers.isEmpty() ? "" : " · Helfer " + String.join(", ", p.helpers))
                : !p.hasSnapshot ? "Kein Leerzustand gesichert – „Sichern“ speichert ihn." : "";
        if (!extra.isEmpty() && ty + 9 <= cy + ch - PAD) text(g, KitDraw.fit(extra, dw), dx, ty, p.owner.isEmpty() ? KitColors.AMBER_TEXT : KitColors.FAINT);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my, float d) {
        if (page != Page.STALLS || !view.allowed) return;
        int x = cx + PAD, y = cy + PAD, lw = listW();
        int rows = listRows();
        for (int i = 0; i < rows && offset + i < view.plots.size(); i++) {
            KitDraw.dot(g, x + lw - 11, y + 14 + i * ROW + 8, MarketScreen.statusColor(view.plots.get(offset + i).status));
        }
    }

    private void drawNew(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Neuer Stand oder Räumzone aus der Auswahl", x, y);
        Views.Selection s = view.selection;
        int ty = y + 14;
        if (s == null) {
            KitDraw.wrapped(g, Component.literal("Marktplaner holen (/markt planer oder Kreativ-Tab). Linksklick auf einen Block = Ecke 1, "
                    + "Rechtsklick = Ecke 2. Die Ecken gehören zum Stand (Boden bis Dach). Danach „Aktualisieren“."), x, ty, w, KitColors.TEXT_SOFT);
            return;
        }
        text(g, "Ecke 1: " + (s.a.isEmpty() ? "–" : s.a) + "    Ecke 2: " + (s.b.isEmpty() ? "–" : s.b), x, ty, KitColors.TEXT);
        ty += 12;
        if (s.complete) {
            text(g, "Größe " + s.size + " · " + s.volume + " Blöcke", x, ty, KitColors.TEXT_SOFT);
            ty += 12;
            if (s.overlap != null && !s.overlap.isEmpty()) text(g, "Überschneidet sich mit „" + s.overlap + "“", x, ty, KitColors.AMBER_TEXT);
            else text(g, "Frei – der Leerzustand wird beim Anlegen gesichert.", x, ty, KitColors.GREEN_TEXT);
        } else {
            text(g, "Noch eine Ecke setzen.", x, ty, KitColors.AMBER_TEXT);
        }
        text(g, "Preise in € – die Bank rechnet sie zum Tageskurs in " + view.unit + " um. Leer = Standard.", x, y + 64, KitColors.FAINT);
    }

    private void drawBuyer(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Ankauf", x, y);
        int dx = x + listW() + 10, dw = x + w - dx;
        Views.BuyerLine sel = null;
        for (Views.BuyerLine l : view.buyerItems) if (l.name.equals(selBuyer)) sel = l;
        if (sel == null) {
            KitDraw.wrapped(g, Component.literal("Posten links wählen zum Ändern. Neu: Item in die Hand nehmen, Coins und Stück "
                    + "eintragen, „Neu aus Hand“."), dx, y + 14, dw, KitColors.TEXT_SOFT);
            return;
        }
        item(g, sel.item, dx, y + 12);
        text(g, KitDraw.fit(sel.name, dw - 22), dx + 20, y + 16, KitColors.TEXT);
        text(g, sel.bundle + " Stück für " + money(sel.basePrice) + " · heute " + money(sel.price), dx, y + 32, KitColors.TEXT_SOFT);
        text(g, String.format(java.util.Locale.GERMANY, "verkauft %.1f von %d Bedarf · Spieler-Limit %d", sel.sold, sel.demand, sel.left),
                dx, y + 44, KitColors.MUTED);
    }

    private void drawJobs(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Aufträge · neue in " + duration(view.jobRefreshIn), x, y);
        int dx = x + listW() + 10, dw = x + w - dx;
        Views.JobLine sel = null;
        for (Views.JobLine j : view.jobs) if (j.id.equals(selJob)) sel = j;
        if (sel == null) {
            KitDraw.wrapped(g, Component.literal("Marktaufträge kommen aus dem Pool in config/twogg-markt/markt.json. ★ = von Spielern."),
                    dx, y + 14, dw, KitColors.TEXT_SOFT);
            return;
        }
        item(g, sel.item, dx, y + 12);
        text(g, KitDraw.fit(sel.title, dw - 22), dx + 20, y + 16, KitColors.TEXT);
        text(g, sel.count + "× " + sel.itemName, dx, y + 32, KitColors.TEXT_SOFT);
        text(g, "Belohnung " + money(sel.reward) + " · noch " + sel.remaining + " von " + sel.slots, dx, y + 44, KitColors.TEXT_SOFT);
        text(g, "von " + sel.poster + (sel.expiresIn > 0 ? " · läuft ab in " + duration(sel.expiresIn) : ""), dx, y + 56, KitColors.MUTED);
    }

    private void drawZones(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Räumzonen", x, y);
        int dx = x + listW() + 10, dw = x + w - dx;
        Views.ZoneLine sel = null;
        for (Views.ZoneLine z : view.zones) if (z.id.equals(selZone)) sel = z;
        if (sel == null) {
            KitDraw.wrapped(g, Component.literal("In Räumzonen bekommen Spieler Geld fürs Schneeschippen (Schneeschichten und -blöcke). "
                    + "Anlegen auf der Seite „Neu“ mit „Als Räumzone“. Für Nachschub sorgt Schneefall."), dx, y + 14, dw, KitColors.TEXT_SOFT);
            return;
        }
        text(g, sel.name, dx, y + 14, KitColors.TEXT);
        text(g, sel.size, dx, y + 26, KitColors.TEXT_SOFT);
        text(g, KitDraw.fit(sel.pos, dw), dx, y + 38, KitColors.MUTED);
    }

    private void drawRules(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Regeln (config/twogg-markt/markt.json)", x, y);
        if (view.settings == null) return;
        int colW = (w - 8) / 2;
        int rh = 14;
        for (int i = 0; i < TOGGLES.length; i++) {
            int tx = x + (i % 2) * (colW + 8), ty = y + 12 + (i / 2) * rh;
            text(g, KitDraw.fit(TOGGLES[i][1], colW - KitToggle.W - 6), tx, ty + 2, KitColors.TEXT_SOFT);
        }
        int ny = y + 12 + ((TOGGLES.length + 1) / 2) * rh + 8;
        int rhN = rulesRowH() + 2;
        int per = numbersPerPage(ny, rhN), first = rulesPage * per;
        for (int k = 0; k < per && first + k < NUMBERS.length; k++) {
            int i = first + k;
            int nx = x + (k % 2) * (colW + 8), yy = ny + (k / 2) * rhN;
            text(g, KitDraw.fit(NUMBERS[i][1], colW - 82), nx, yy + 4, KitColors.TEXT_SOFT);
            KitDraw.well(g, nx + colW - 78, yy, 46, rhN - 2);
        }
    }
}
