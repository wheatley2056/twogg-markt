package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Verkaufstheke. Käufer sehen die Angebote mit Vorrat und kaufen; Besitzer und Helfer legen auf der Seite „Verwalten“
 * Angebote aus der Hand an, ändern Preise und leeren die Kasse.
 */
public class ShopScreen extends MarktScreen {
    private boolean manage;
    private String selected = "";
    private int times = 1;
    private String priceText = "", countText = "1";

    ShopScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, v.member ? 96 : 0);
        manage = v.member && v.offers.isEmpty();
        if (!v.offers.isEmpty()) selected = v.offers.getFirst().id;
    }

    @Override
    String screenId() {
        return "shop";
    }

    @Override
    protected boolean sameContext(Views.View v) {
        return v.stall != null && view.stall != null && v.stall.id.equals(view.stall.id) && v.member == view.member;
    }

    @Override
    protected void afterUpdate() {
        if (offer(selected) == null && !view.offers.isEmpty()) selected = view.offers.getFirst().id;
        armed = "";
    }

    private Views.OfferLine offer(String id) {
        for (Views.OfferLine o : view.offers) if (o.id.equals(id)) return o;
        return null;
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14 - 26) / ROW);
    }

    @Override
    protected int listTotal() {
        return view.offers.size();
    }

    @Override
    protected void build() {
        if (view.member) {
            addNav(0, new KitNav(0, 0, 0, KitIcon.BOX, "Angebote", () -> String.valueOf(view.offers.size()), () -> !manage, () -> { manage = false; rebuildWidgets(); }));
            addNav(1, new KitNav(0, 0, 0, KitIcon.GEAR, "Verwalten", () -> "", () -> manage, () -> { manage = true; rebuildWidgets(); }));
        }
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        addFooterButton(KitButton.second(0, 0, 80, "Aktualisieren", b -> send("refresh")));
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int rows = listRows();
        pager(x + w, y - 2, view.offers.size(), rows);
        for (int i = 0; i < rows && offset + i < view.offers.size(); i++) {
            Views.OfferLine o = view.offers.get(offset + i);
            int ry = y + 14 + i * ROW;
            boolean sel = o.id.equals(selected);
            KitButton b = new KitButton(x, ry, w, 20, Component.empty(), null, sel ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, bt -> {
                selected = o.id;
                times = 1;
                priceText = money(o.price, "");
                countText = String.valueOf(o.count);
                armed = "";
                rebuildWidgets();
            });
            addRenderableWidget(b.accent(KitColors.darker(accent(), 0.35f)));
        }
        int by = cy + ch - PAD - 20;
        Views.OfferLine o = offer(selected);
        if (!manage && !view.member && view.event != null && view.event.vote
                && ("OPENING".equals(view.event.phase) || "OPEN".equals(view.event.phase))) {
            boolean voted = view.stall != null && view.stall.name.equals(view.event.myVote);
            addHeaderButton(KitButton.second(0, 0, 90, voted ? "★ Gestimmt" : "★ Abstimmen", b -> send("vote"))).active = !voted;
        }
        if (!manage) {
            if (o == null || view.member) return;
            addRenderableWidget(KitButton.icon(x, by, 20, KitIcon.MINUS, "Weniger", b -> { times = Math.max(1, times - 1); rebuildWidgets(); }));
            addRenderableWidget(KitButton.icon(x + 60, by, 20, KitIcon.PLUS, "Mehr", b -> { times = Math.min(Math.max(1, o.stock), times + 1); rebuildWidgets(); }));
            payToggle(x + 86, by, 60);
            int bw = Math.min(170, x + w - (x + 150));
            KitButton buy = addRenderableWidget(KitButton.primary(x + w - bw, by, bw, KitDraw.fit("Kaufen · " + money(o.price * times), bw - 8), b -> {
                Views.Action a = Views.Action.of("buy");
                a.id = o.id;
                a.count = times;
                a.cash = cash;
                // angezeigter Preis und Stückzahl: der Server bricht ab, wenn sich das Angebot inzwischen verschlechtert hat (seit 0.5.2)
                a.amount = o.price;
                a.value = o.count;
                send(a);
            }).accent(accent()));
            buy.active = o.stock >= times && view.stall.shopOpen;
            return;
        }
        // Verwalten: unten eine Zeile mit Preis/Stück und Knöpfen
        input(x, by, 70, "Preis", priceText, 8, s -> priceText = s, view.unit);
        input(x + 74, by, 52, "Stück", countText, 4, s -> countText = s, "Stk");
        int bx = x + 130;
        addRenderableWidget(KitButton.primary(bx, by, 70, "Anbieten", b -> {
            Views.Action a = Views.Action.of("offer_add");
            a.amount = parseTenths(priceText);
            a.count = Math.max(1, parseInt(countText));
            send(a);
        }).accent(accent())).active = !view.hand.isEmpty();
        bx += 74;
        if (o != null) {
            addRenderableWidget(KitButton.second(bx, by, 64, "Ändern", b -> {
                Views.Action a = Views.Action.of("offer_edit");
                a.id = o.id;
                a.amount = parseTenths(priceText);
                a.count = Math.max(0, parseInt(countText));
                send(a);
            }));
            bx += 68;
            if (bx + 70 <= x + w) {
                confirm(bx, by, 70, "Entfernen", "rm:" + o.id, () -> {
                    Views.Action a = Views.Action.of("offer_remove");
                    a.id = o.id;
                    send(a);
                });
            }
        }
        // Kasse und Öffnen oben rechts im Kopf der Liste
        if (view.owner) {
            addHeaderButton(KitButton.second(0, 0, 90, "Kasse leeren", b -> {
                Views.Action a = Views.Action.of("collect");
                a.cash = cash;
                send(a);
            })).active = view.stall.till > 0;
        }
        addHeaderButton(KitButton.second(0, 0, 70, view.stall.shopOpen ? "Schließen" : "Öffnen", b -> send("toggle")));
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        String head = manage ? "Angebote verwalten · Kasse " + money(view.stall.till) + " · Gebühr " + num(view.feePercent) + " %"
                : view.stall.shopOpen ? "Angebote" : "Angebote – die Theke ist gerade geschlossen";
        KitDraw.label(g, KitDraw.fit(head, w - 40), x, y);
        if (view.offers.isEmpty()) {
            String t = view.member ? "Noch keine Angebote. Ware in die Hand nehmen, Preis und Stückzahl eintragen, „Anbieten“. "
                    + "Verkauft wird aus Kisten, Fässern und Shulkerkisten in deinem Stand."
                    : "Hier gibt es gerade nichts zu kaufen.";
            KitDraw.wrapped(g, Component.literal(t), x, y + 16, w, KitColors.TEXT_SOFT);
        }
        int by = cy + ch - PAD - 20;
        Views.OfferLine o = offer(selected);
        if (!manage && o != null && !view.member) {
            String n = times + "×";
            g.text(font, n, x + 40 - font.width(n) / 2, by + 6, KitColors.TEXT, false);
        }
        if (manage) {
            String hand = view.hand.isEmpty() ? "In der Hand: nichts – Ware in die Hand nehmen" : "In der Hand: " + view.handName;
            text(g, KitDraw.fit("Preis pro Kauf · Stück pro Kauf · " + hand, w), x, by - 11, KitColors.FAINT);
        }
        footer(g);
    }

    /** Zeilen liegen auf Knöpfen: darum über den Widgets zeichnen. */
    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int rows = listRows();
        for (int i = 0; i < rows && offset + i < view.offers.size(); i++) {
            Views.OfferLine o = view.offers.get(offset + i);
            int ry = y + 14 + i * ROW;
            boolean sel = o.id.equals(selected);
            int fg = KitColors.TEXT;
            item(g, o.item, x + 3, ry + 2);
            String price = o.count + "× für " + money(o.price);
            String stock = o.stock <= 0 ? "ausverkauft" : "Vorrat " + o.stock;
            int rw = font.width(price) + font.width(stock) + 20;
            text(g, KitDraw.fit(o.name, w - 30 - rw), x + 24, ry + 6, fg);
            right(g, stock, x + w - 8, ry + 6, o.stock <= 0 ? KitColors.AMBER_TEXT : sel ? KitColors.TEXT_SOFT : KitColors.MUTED);
            right(g, price, x + w - 16 - font.width(stock), ry + 6, fg);
        }
    }
}
