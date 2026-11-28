package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** {@code /markt} und Standschild: alle Stände mit Preisen, mieten/kaufen, der eigene Stand, Verdienst-Infos. */
public class MarketScreen extends MarktScreen {
    private enum Page { STALLS, MINE, EVENT, INFO }

    private int board;

    private Page page = Page.STALLS;
    private String selected = "";
    private int periods = 1;
    private String helperName = "";

    MarketScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, 96);
        if (!v.focus.isEmpty()) selected = v.focus;
        if ("mine".equals(v.page)) page = Page.MINE;
        if (selected.isEmpty() && !v.plots.isEmpty()) selected = v.plots.getFirst().id;
    }

    @Override
    String screenId() {
        return "market";
    }

    @Override
    protected void afterUpdate() {
        if ("mine".equals(view.page)) page = Page.MINE;
        else if ("stalls".equals(view.page)) page = Page.STALLS;
        if (plot(selected) == null && !view.plots.isEmpty()) selected = view.plots.getFirst().id;
        armed = "";
    }

    private Views.PlotLine plot(String id) {
        for (Views.PlotLine p : view.plots) if (p.id.equals(id)) return p;
        return null;
    }

    private List<Views.PlotLine> mine() {
        List<Views.PlotLine> l = new ArrayList<>();
        for (Views.PlotLine p : view.plots) if (p.mine || p.helper) l.add(p);
        return l;
    }

    private void go(Page p) {
        page = p;
        offset = 0;
        armed = "";
        view.message = "";
        rebuildWidgets();
    }

    // ------------------------------------------------------------------ Aufbau

    private int listW() {
        return Math.min(160, (cw - 2 * PAD) * 2 / 5);
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14) / ROW);
    }

    @Override
    protected int listTotal() {
        return page == Page.STALLS ? view.plots.size() : 0;
    }

    @Override
    protected void build() {
        addNav(0, new KitNav(0, 0, 0, KitIcon.MAP, "Stände", () -> String.valueOf(view.plots.size()), () -> page == Page.STALLS, () -> go(Page.STALLS)));
        addNav(1, new KitNav(0, 0, 0, KitIcon.BOX, "Mein Stand", () -> mine().isEmpty() ? "" : String.valueOf(mine().size()),
                () -> page == Page.MINE, () -> go(Page.MINE)));
        addNav(2, new KitNav(0, 0, 0, KitIcon.BOLT, "Event", () -> eventRunning() ? "●" : "", () -> page == Page.EVENT, () -> go(Page.EVENT)));
        addNav(3, new KitNav(0, 0, 0, KitIcon.PLUS, "Verdienen", () -> "", () -> page == Page.INFO, () -> go(Page.INFO)));
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        switch (page) {
            case STALLS -> buildStalls();
            case EVENT -> buildEvent();
            case MINE -> buildMine();
            default -> { }
        }
    }

    private void buildStalls() {
        int x = cx + PAD, y = cy + PAD, lw = listW();
        int rows = listRows();
        pager(x + lw, y - 2, view.plots.size(), rows);
        for (int i = 0; i < rows && offset + i < view.plots.size(); i++) {
            Views.PlotLine p = view.plots.get(offset + i);
            boolean sel = p.id.equals(selected);
            KitButton b = new KitButton(x, y + 14 + i * ROW, lw, 20, Component.literal(KitDraw.fit(p.name, lw - 22)), null,
                    sel ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, bt -> {
                selected = p.id;
                periods = 1;
                armed = "";
                rebuildWidgets();
            });
            addRenderableWidget(b.accent(accent()));
        }
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        int dx = x + lw + 10, dw = cx + cw - PAD - dx, by = cy + ch - PAD - 20;
        boolean free = "FREE".equals(p.status);
        boolean canRent = (free || p.mine) && !"OWNED".equals(p.status) && ("RENT".equals(p.mode) || "BOTH".equals(p.mode)) && p.bookable > 0;
        boolean canBuy = (free || (p.mine && "RENTED".equals(p.status))) && ("BUY".equals(p.mode) || "BOTH".equals(p.mode));
        if (canRent) {
            int sx = dx;
            addRenderableWidget(KitButton.icon(sx, by - 24, 20, KitIcon.MINUS, "Weniger", b -> { periods = Math.max(1, periods - 1); rebuildWidgets(); }));
            addRenderableWidget(KitButton.icon(sx + 70, by - 24, 20, KitIcon.PLUS, "Mehr", b -> { periods = Math.min(p.bookable, periods + 1); rebuildWidgets(); }));
            periods = Math.max(1, Math.min(periods, p.bookable));
        }
        if (canRent || canBuy) payToggle(dx + dw - 60, by - 24, 60);
        if (eventRunning() && view.event.vote && !free && !p.mine && !p.helper && !"LOCKED".equals(p.status)) {
            boolean mine = p.name.equals(view.event.myVote);
            KitButton v = addRenderableWidget(KitButton.primary(dx, by, dw, mine ? "★ Deine Stimme" : "★ Für diesen Stand stimmen", b -> {
                Views.Action a = Views.Action.of("vote");
                a.plot = p.id;
                send(a);
            }).accent(accent()));
            v.active = !mine;
        }
        int bw = canRent && canBuy ? (dw - 4) / 2 : dw;
        int bx = dx;
        if (canRent) {
            String label = (p.mine ? "Verlängern " : "Mieten ") + money(p.rentTenths * periods);
            addRenderableWidget(KitButton.primary(bx, by, bw, KitDraw.fit(label, bw - 8), b -> {
                Views.Action a = Views.Action.of("rent");
                a.plot = p.id;
                a.count = periods;
                a.cash = cash;
                send(a);
            }).accent(accent()));
            bx += bw + 4;
        }
        if (canBuy) {
            String label = "Kaufen " + money(p.buyTenths);
            if (armed.equals("buy:" + p.id)) label = "Wirklich kaufen?";
            String key = "buy:" + p.id;
            addRenderableWidget(KitButton.second(bx, by, bw, KitDraw.fit(label, bw - 8), b -> {
                if (!armed.equals(key)) { armed = key; rebuildWidgets(); return; }
                armed = "";
                Views.Action a = Views.Action.of("buy");
                a.plot = p.id;
                a.cash = cash;
                send(a);
            }));
        }
    }

    private void buildMine() {
        List<Views.PlotLine> mine = mine();
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        if (mine.isEmpty()) {
            addRenderableWidget(KitButton.primary(x, y + 40, 120, "Zu den Ständen", b -> go(Page.STALLS)).accent(accent()));
            return;
        }
        Views.PlotLine p = plot(selected) != null && (plot(selected).mine || plot(selected).helper) ? plot(selected) : mine.getFirst();
        selected = p.id;
        if (mine.size() > 1) {
            int tx = x + w;
            for (int i = mine.size() - 1; i >= 0; i--) {
                Views.PlotLine m = mine.get(i);
                int bw = Math.min(80, font.width(m.name) + 12);
                tx -= bw;
                addRenderableWidget(new KitButton(tx, y - 2, bw, 14, Component.literal(KitDraw.fit(m.name, bw - 6)), null,
                        m.id.equals(p.id) ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, b -> { selected = m.id; rebuildWidgets(); }).accent(accent()));
                tx -= 3;
            }
        }
        int col = mineCol(x, w);
        int bw = (col - x - 12) / 2;
        int by = y + 70;
        if (p.mine) {
            addRenderableWidget(KitButton.primary(x, by, bw, "Kasse leeren", b -> {
                Views.Action a = Views.Action.of("collect");
                a.plot = p.id;
                send(a);
            }).accent(accent())).active = p.till > 0;
        }
        addRenderableWidget(KitButton.second(x + bw + 4, by, bw, p.shopOpen ? "Theke zu" : "Theke auf", b -> {
            Views.Action a = Views.Action.of("shop_toggle");
            a.plot = p.id;
            send(a);
        }));
        addRenderableWidget(KitButton.second(x, by + 22, bw, "Theke kaufen", b -> {
            Views.Action a = Views.Action.of("buy_counter");
            a.plot = p.id;
            a.cash = cash;
            send(a);
        }));
        if (p.mine && "RENTED".equals(p.status) && p.bookable > 0) {
            addRenderableWidget(KitButton.second(x + bw + 4, by + 22, bw, "+1 " + view.periodName, b -> {
                Views.Action a = Views.Action.of("rent");
                a.plot = p.id;
                a.count = 1;
                a.cash = cash;
                send(a);
            }));
        }
        payToggle(x, by + 44, bw);
        if (!p.mine) return;
        boolean bought = "OWNED".equals(p.status);
        confirm(x + bw + 4, by + 44, bw, bought ? "Zurückgeben" : "Kündigen", "cancel:" + p.id, () -> {
            Views.Action a = Views.Action.of("cancel");
            a.plot = p.id;
            send(a);
        });
        // Helfer: vorhandene per Klick entfernen, neue per Namen hinzufügen
        int hx = col, hw = x + w - col, hy = y + 14;
        int fit = Math.max(0, (cy + ch - PAD - hy - 22) / ROW);
        for (int i = 0; i < p.helpers.size() && i < fit; i++) {
            String h = p.helpers.get(i);
            addRenderableWidget(new KitButton(hx, hy + i * ROW, hw, 20, Component.literal(KitDraw.fit(h, hw - 26)), KitIcon.CLOSE,
                    KitButton.Style.SECOND, b -> {
                Views.Action a = Views.Action.of("helper_remove");
                a.plot = p.id;
                a.name = h;
                send(a);
            }));
        }
        int iy = hy + Math.min(fit, p.helpers.size()) * ROW;
        input(hx, iy, hw - 24, "Spielername", helperName, 16, t -> helperName = t);
        addRenderableWidget(KitButton.icon(hx + hw - 20, iy, 20, KitIcon.PLUS, "Helfer hinzufügen", b -> {
            Views.Action a = Views.Action.of("helper_add");
            a.plot = p.id;
            a.name = helperName;
            helperName = "";
            send(a);
        }));
    }

    private int mineCol(int x, int w) {
        return x + Math.max(200, w * 3 / 5);
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        switch (page) {
            case STALLS -> drawStalls(g, x, y, w);
            case MINE -> drawMine(g, x, y, w);
            case INFO -> drawInfo(g, x, y, w);
            case EVENT -> drawEvent(g, x, y, w);
        }
        footer(g);
    }

    static int statusColor(String status) {
        return switch (status) {
            case "FREE" -> KitColors.GREEN_TEXT;
            case "RENTED" -> KitColors.AMBER_TEXT;
            case "OWNED" -> 0xFF8DB8FF;
            default -> KitColors.DANGER;
        };
    }

    static String statusText(Views.PlotLine p, String periodName) {
        return switch (p.status) {
            case "FREE" -> "frei";
            case "RENTED" -> p.mine ? "deiner · noch " + duration(p.remaining) : "vermietet an " + p.ownerName;
            case "OWNED" -> p.mine ? "gehört dir" : "gehört " + p.ownerName;
            default -> "nicht zu vergeben";
        };
    }

    private void drawStalls(GuiGraphicsExtractor g, int x, int y, int w) {
        int lw = listW();
        KitDraw.label(g, "Stände", x, y);
        if (view.plots.isEmpty()) {
            KitDraw.wrapped(g, Component.literal("Die Marktleitung hat noch keine Stände eingezeichnet."), x, y + 16, w, KitColors.TEXT_SOFT);
            return;
        }
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        int dx = x + lw + 10, dw = x + w - dx;
        KitDraw.well(g, dx - 4, y - 2, dw + 4, ch - 2 * PAD - 52);
        int ty = y + 2;
        KitDraw.drawTitle(g, KitDraw.fit(p.name, dw - 8), dx + 2, ty, KitColors.TEXT);
        ty += 20;
        text(g, KitDraw.fit(statusText(p, view.periodName), dw - 8), dx + 2, ty, statusColor(p.status));
        ty += 12;
        if (!p.note.isEmpty()) {
            ty += KitDraw.wrapped(g, Component.literal(p.note), dx + 2, ty, dw - 8, y + ch - 2 * PAD - 56, KitColors.TEXT_SOFT);
            ty += 2;
        }
        text(g, "Größe " + p.size + " · bei " + p.pos, dx + 2, ty, KitColors.MUTED);
        ty += 14;
        boolean rent = "RENT".equals(p.mode) || "BOTH".equals(p.mode), buy = "BUY".equals(p.mode) || "BOTH".equals(p.mode);
        if (rent) {
            text(g, "Miete", dx + 2, ty, KitColors.TEXT_SOFT);
            right(g, money(p.rentTenths) + " / " + view.periodName, dx + dw - 6, ty, KitColors.TEXT);
            ty += 11;
            text(g, "höchstens " + p.maxPeriods + " " + (p.maxPeriods == 1 ? view.periodName : view.periodNamePlural) + " im Voraus", dx + 2, ty, KitColors.FAINT);
            ty += 12;
        }
        if (buy) {
            text(g, "Kauf (bis Event-Ende)", dx + 2, ty, KitColors.TEXT_SOFT);
            right(g, money(p.buyTenths), dx + dw - 6, ty, KitColors.TEXT);
            ty += 12;
        }
        if (p.mine || p.helper) text(g, p.mine ? "Das ist dein Stand." : "Du hilfst an diesem Stand.", dx + 2, ty, KitColors.GREEN_TEXT);
        int by = cy + ch - PAD - 20;
        boolean free = "FREE".equals(p.status);
        boolean canRent = (free || p.mine) && !"OWNED".equals(p.status) && rent && p.bookable > 0;
        if (canRent) {
            String n = periods + " " + (periods == 1 ? view.periodName : view.periodNamePlural);
            g.text(font, n, dx + 45 - font.width(n) / 2, by - 18, KitColors.TEXT, false);
        } else if (!free && !p.mine) {
            text(g, "Preise folgen dem Wertkurs der Bank.", dx, by + 6, KitColors.FAINT);
        }
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my, float d) {
        if (page != Page.STALLS) return;
        int x = cx + PAD, y = cy + PAD, lw = listW();
        int rows = listRows();
        for (int i = 0; i < rows && offset + i < view.plots.size(); i++) {
            Views.PlotLine p = view.plots.get(offset + i);
            KitDraw.dot(g, x + lw - 11, y + 14 + i * ROW + 8, statusColor(p.status));
        }
    }

    private void drawMine(GuiGraphicsExtractor g, int x, int y, int w) {
        List<Views.PlotLine> mine = mine();
        if (mine.isEmpty()) {
            KitDraw.label(g, "Mein Stand", x, y);
            KitDraw.wrapped(g, Component.literal("Du hast noch keinen Stand. Such dir unter „Stände“ einen freien aus – mieten "
                    + "geht tageweise, manche kann man auch kaufen."), x, y + 14, w, KitColors.TEXT_SOFT);
            return;
        }
        Views.PlotLine p = plot(selected);
        if (p == null) return;
        int col = mineCol(x, w), cw2 = col - x - 12;
        KitDraw.drawTitle(g, KitDraw.fit(p.name, cw2 - (mine.size() > 1 ? 0 : 0)), x, y - 2, KitColors.TEXT);
        int ty = y + 18;
        text(g, KitDraw.fit(statusText(p, view.periodName), cw2), x, ty, statusColor(p.status));
        ty += 12;
        String[][] rows = {
                {"Kasse", money(p.till)},
                {"Umsatz", money(p.salesTotal)},
                {"Theke", p.offers + " Angebote · " + (p.shopOpen ? "offen" : "zu")},
                {p.mine && p.refund > 0 ? ("OWNED".equals(p.status) ? "Rückkauf" : "Erstattung") : "", p.mine && p.refund > 0 ? money(p.refund) : ""}};
        for (String[] r : rows) {
            if (r[0].isEmpty()) continue;
            text(g, r[0], x, ty, KitColors.TEXT_SOFT);
            right(g, r[1], x + cw2, ty, KitColors.TEXT);
            ty += 10;
        }
        if (p.mine) {
            KitDraw.label(g, "Helfer", col, y);
        } else {
            KitDraw.wrapped(g, Component.literal("Du hilfst am Stand von " + p.ownerName + ". Kasse leeren und kündigen kann nur "
                    + p.ownerName + "."), col, y, x + w - col, KitColors.TEXT_SOFT);
        }
    }

    private boolean eventRunning() {
        return view.event != null && ("OPENING".equals(view.event.phase) || "OPEN".equals(view.event.phase));
    }

    private void buildEvent() {
        if (view.event == null || view.event.boards.isEmpty()) return;
        int x = cx + PAD, w = cw - 2 * PAD, col = x + w / 2 + 6, y = cy + PAD;
        int n = view.event.boards.size();
        board = Math.floorMod(board, n);
        addRenderableWidget(KitButton.icon(x + w - 32, y - 2, 14, KitIcon.ARROW_LEFT, "Vorige Liste", b -> { board--; rebuildWidgets(); }));
        addRenderableWidget(KitButton.icon(x + w - 14, y - 2, 14, KitIcon.ARROW_RIGHT, "Nächste Liste", b -> { board++; rebuildWidgets(); }));
    }

    private void drawEvent(GuiGraphicsExtractor g, int x, int y, int w) {
        Views.EventInfo e = view.event;
        if (e == null) return;
        int col = x + w / 2 + 6, lw = col - x - 10;
        boolean run = eventRunning();
        KitDraw.drawTitle(g, KitDraw.fit(run ? "Geöffnet!" : "FINALE".equals(e.phase) ? "Siegerehrung" : "Geschlossen", lw), x, y - 2,
                run ? KitColors.GREEN_TEXT : KitColors.TEXT);
        int ty = y + 18;
        if (run) {
            text(g, KitDraw.fit("Noch " + duration(e.remaining) + " · Markt #" + e.round, lw), x, ty, KitColors.TEXT_SOFT);
            ty += 10;
            g.fill(x, ty, x + lw, ty + 3, KitColors.OUTLINE);
            g.fill(x, ty, x + (int) (lw * Math.min(1.0, e.remaining / (double) e.total)), ty + 3, accent());
            ty += 8;
        } else if (!e.next.isEmpty()) {
            text(g, KitDraw.fit("Nächste Öffnung: " + e.next, lw), x, ty, KitColors.TEXT_SOFT);
            ty += 12;
        }
        KitDraw.label(g, "Öffnungszeiten", x, ty);
        ty += 11;
        if (e.schedule.isEmpty()) {
            text(g, "werden angekündigt", x, ty, KitColors.FAINT);
            ty += 10;
        }
        for (int i = 0; i < e.schedule.size() && i < 3; i++) {
            text(g, KitDraw.fit(e.schedule.get(i), lw), x, ty, KitColors.TEXT);
            ty += 10;
        }
        ty += 4;
        KitDraw.label(g, "Mitmachen", x, ty);
        ty += 11;
        String[] lines = {
                e.lottery ? "Losbude: " + e.myTickets + " Lose · Topf " + money(e.pot) : "",
                e.vote ? (e.myVote.isEmpty() ? "Schönster Stand: noch keine Stimme" : "Deine Stimme: " + e.myVote) : "",
                e.wichteln ? (e.wichtelGift.isEmpty() ? "Wichteln: noch kein Geschenk" : "Wichteln: " + e.wichtelGift) : "",
                e.giftReward > 0 ? "Geschenkesuche: je " + money(e.giftReward) : ""};
        for (String l : lines) {
            if (l.isEmpty() || ty > cy + ch - PAD - 9) continue;
            text(g, KitDraw.fit(l, lw), x, ty, KitColors.TEXT_SOFT);
            ty += 10;
        }
        // rechts: Bestenliste oder letztes Ergebnis
        if (!run && !"FINALE".equals(e.phase) && !e.lastResults.isEmpty()) {
            KitDraw.label(g, "Letztes Event", col, y);
            int ry = y + 12;
            for (int i = 1; i < e.lastResults.size() && ry <= cy + ch - PAD - 9; i++) {
                text(g, KitDraw.fit(e.lastResults.get(i).trim(), x + w - col), col, ry, KitColors.TEXT_SOFT);
                ry += 10;
            }
            return;
        }
        if (e.boards.isEmpty()) return;
        Views.Board b = e.boards.get(Math.floorMod(board, e.boards.size()));
        KitDraw.label(g, KitDraw.fit(b.title, x + w - col - 40), col, y);
        int ry = y + 14;
        if (b.lines.isEmpty()) text(g, "noch niemand", col, ry, KitColors.FAINT);
        for (String l : b.lines) {
            text(g, KitDraw.fit(l, x + w - col), col, ry, ry == y + 14 ? KitColors.AMBER_TEXT : KitColors.TEXT);
            ry += 11;
        }
    }

    private void drawInfo(GuiGraphicsExtractor g, int x, int y, int w) {
        KitDraw.label(g, "Geld verdienen auf dem Markt", x, y);
        String[][] items = {
                {"Eigener Stand", "Theke in den Stand, Ware in eine Kiste daneben – Verkäufe landen in der Standkasse."},
                {"Ankauf", "Holz, Kohle, Gebäck, Fisch … Je mehr heute verkauft wurde, desto weniger zahlt er."},
                {"Auftragsbrett", "Aufträge von Spielern, dazu Marktaufträge, wenn der Markt welche aushängt."},
                {"Räumdienst", "Schnee in den Gassen schippen, bezahlt bis zum Tageslimit."}
        };
        int ty = y + 14;
        for (String[] it : items) {
            if (ty > cy + ch - PAD - 20) break;
            text(g, it[0], x, ty, accent());
            ty += 10;
            ty += KitDraw.wrapped(g, Component.literal(it[1]), x, ty, w, cy + ch - PAD, KitColors.TEXT_SOFT) + 3;
        }
    }
}
