package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Losbude (Seiten „Lose“ und „Wichteln“) und Adventskalender. */
public class ActivityScreen extends MarktScreen {
    private final String id;
    private int count = 1;
    /** Losbude: Seite Wichteln statt Lose. */
    private boolean wichtel;

    ActivityScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, "lottery".equals(v.screen) ? 96 : 0);
        this.id = v.screen;
        wichtel = v.event != null && !v.event.lottery && v.event.wichteln;
    }

    /** Was gerade angezeigt wird: lottery, wichtel oder advent. */
    private String mode() {
        return "lottery".equals(id) && wichtel ? "wichtel" : id;
    }

    @Override
    String screenId() {
        return id;
    }

    private Views.EventInfo ev() {
        return view.event == null ? new Views.EventInfo() : view.event;
    }

    private boolean running() {
        return "OPENING".equals(ev().phase) || "OPEN".equals(ev().phase);
    }

    @Override
    protected void build() {
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        if ("lottery".equals(id)) {
            int n = 0;
            if (ev().lottery) addNav(n++, new KitNav(0, 0, 0, KitIcon.BOX, "Lose", () -> ev().myTickets > 0 ? String.valueOf(ev().myTickets) : "",
                    () -> !wichtel, () -> { wichtel = false; view.message = ""; rebuildWidgets(); }));
            if (ev().wichteln) addNav(n, new KitNav(0, 0, 0, KitIcon.PLUS, "Wichteln", () -> ev().wichtelGift.isEmpty() ? "" : "1",
                    () -> wichtel, () -> { wichtel = true; view.message = ""; rebuildWidgets(); }));
        }
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD, by = cy + ch - PAD - 20;
        switch (mode()) {
            case "lottery" -> {
                int left = Math.max(0, ev().maxTickets - ev().myTickets);
                count = Math.max(1, Math.min(count, Math.max(1, left)));
                addRenderableWidget(KitButton.icon(x, by, 20, KitIcon.MINUS, "Weniger", b -> { count = Math.max(1, count - 1); rebuildWidgets(); }));
                addRenderableWidget(KitButton.icon(x + 60, by, 20, KitIcon.PLUS, "Mehr", b -> { count = Math.min(Math.max(1, left), count + 1); rebuildWidgets(); }));
                payToggle(x + 86, by, 60);
                int bw = Math.min(180, w - 150);
                KitButton buy = addRenderableWidget(KitButton.primary(x + w - bw, by, bw, KitDraw.fit(
                        (count == 1 ? "1 Los" : count + " Lose") + " kaufen · " + money(ev().ticketPrice * count), bw - 8), b -> {
                    Views.Action a = Views.Action.of("buy");
                    a.count = count;
                    a.cash = cash;
                    send(a);
                }).accent(accent()));
                buy.active = running() && left > 0;
            }
            case "advent" -> {
                int cols = 6, gap = 4, bw = (w - (cols - 1) * gap) / cols;
                int rows = 4, rh = Math.max(20, Math.min(30, (ch - 2 * PAD - 26) / rows));
                for (Views.AdventDoor d : view.advent) {
                    int i = d.day - 1, bx = x + (i % cols) * (bw + gap), byy = y + 14 + (i / cols) * rh;
                    boolean open = "OPEN".equals(d.state);
                    KitButton b = new KitButton(bx, byy, bw, rh - 4, Component.literal(String.valueOf(d.day)), null,
                            open ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, bt -> send("open"));
                    b.active = open;
                    addRenderableWidget(open ? b.accent(accent()) : b);
                }
            }
            case "wichtel" -> {
                int bw = (w - 8) / 3;
                KitButton give = addRenderableWidget(KitButton.primary(x, by, bw, "Geschenk abgeben", b -> send("give")).accent(accent()));
                give.active = running() && ev().wichtelGift.isEmpty() && !view.hand.isEmpty();
                KitButton take = addRenderableWidget(KitButton.second(x + bw + 4, by, bw, "Zurücknehmen", b -> send("take")));
                take.active = !ev().wichtelGift.isEmpty() && running();
                addRenderableWidget(KitButton.second(x + 2 * (bw + 4), by, bw, "Abholen", b -> send("pickup")));
            }
            default -> { }
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD, by = cy + ch - PAD - 20;
        Views.EventInfo e = ev();
        switch (mode()) {
            case "lottery" -> {
                KitDraw.label(g, running() ? "Weihnachtsmarkt #" + e.round + " · " + e.status : "Die Losbude öffnet mit dem Weihnachtsmarkt", x, y);
                KitDraw.well(g, x, y + 14, w, 54);
                text(g, "Im Topf", x + 8, y + 20, KitColors.MUTED);
                KitDraw.drawTitle(g, money(e.pot), x + 8, y + 30, KitColors.TEXT);
                right(g, "ausgeschüttet: " + money(e.potPayout), x + w - 8, y + 20, KitColors.TEXT_SOFT);
                right(g, e.ticketsTotal + " Lose von " + e.players + (e.players == 1 ? " Spieler" : " Spielern"), x + w - 8, y + 34, KitColors.TEXT_SOFT);
                right(g, "Deine Lose: " + e.myTickets + " von " + e.maxTickets, x + w - 8, y + 48, KitColors.GREEN_TEXT);
                KitDraw.wrapped(g, Component.literal("Ein Los kostet " + money(e.ticketPrice) + ". Gezogen wird bei der Siegerehrung am Ende des Markts – "
                        + "je mehr Lose, desto größer die Chance. Ein Teil des Topfs bleibt beim Markt."), x, y + 74, w, by - 4, KitColors.FAINT);
                String n = count + "×";
                g.text(font, n, x + 40 - font.width(n) / 2, by + 6, KitColors.TEXT, false);
            }
            case "advent" -> {
                String head = view.adventToday > 0 ? "Heute ist der " + view.adventToday + ". Dezember – ein Türchen pro Tag"
                        : "Der Kalender öffnet am 1. Dezember";
                KitDraw.label(g, head, x, y);
            }
            case "wichtel" -> {
                KitDraw.label(g, running() ? "Wichteln läuft · " + e.wichtelCount + (e.wichtelCount == 1 ? " Geschenk" : " Geschenke") + " abgegeben"
                        : "Wichteln beginnt mit dem Weihnachtsmarkt", x, y);
                int ty = y + 16;
                if (!e.wichtelGift.isEmpty()) {
                    text(g, "Dein Geschenk: " + e.wichtelGift, x, ty, KitColors.GREEN_TEXT);
                } else if (!view.hand.isEmpty()) {
                    item(g, view.hand, x, ty - 4);
                    text(g, "In der Hand: " + view.handName, x + 20, ty, KitColors.TEXT);
                } else {
                    text(g, "Nimm dein Geschenk in die Hand.", x, ty, KitColors.AMBER_TEXT);
                }
                KitDraw.wrapped(g, Component.literal("Gib ein Geschenk ab (den ganzen Stapel in deiner Hand). Bei der Siegerehrung "
                        + "werden alle Geschenke verteilt – niemand bekommt sein eigenes. Abholen hier oder im Abholfach am Auftragsbrett."),
                        x, ty + 18, w, by - 4, KitColors.TEXT_SOFT);
            }
            default -> { }
        }
        footer(g);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my, float d) {
        if (!"advent".equals(id)) return;
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int cols = 6, gap = 4, bw = (w - (cols - 1) * gap) / cols;
        int rows = 4, rh = Math.max(20, Math.min(30, (ch - 2 * PAD - 26) / rows));
        for (Views.AdventDoor door : view.advent) {
            int i = door.day - 1, bx = x + (i % cols) * (bw + gap), byy = y + 14 + (i / cols) * rh;
            if ("DONE".equals(door.state)) {
                if (!door.item.isEmpty()) item(g, door.item, bx + 2, byy + (rh - 4 - 16) / 2);
                else KitIcon.CHECK.draw(g, bx + 4, byy + (rh - 12) / 2, KitColors.GREEN_TEXT);
            } else if ("MISSED".equals(door.state)) {
                KitIcon.CLOSE.draw(g, bx + 4, byy + (rh - 12) / 2, KitColors.FAINT);
            }
        }
    }
}
