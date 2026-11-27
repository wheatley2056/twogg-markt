package de.twogg.markt.client;

import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import de.twogg.markt.net.Views;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Server-Laden (seit 0.4.0): Seite „Kaufen“ (feste Preise, unbegrenzter Vorrat) und Seite „Verkaufen“ (Ankauf mit
 * Tagesgrenze). Geöffnet von einem NPC per {@code /markt laden oeffnen}.
 */
public class LadenScreen extends MarktScreen {
    private boolean selling;
    private String selected = "";
    private int times = 1;

    LadenScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, 96);
        selling = v.offers.isEmpty() && !v.buyerItems.isEmpty();
        if (!v.offers.isEmpty()) selected = v.offers.getFirst().id;
    }

    @Override
    String screenId() {
        return "laden";
    }

    @Override
    protected boolean sameContext(Views.View v) {
        return v.title.equals(view.title);
    }

    @Override
    protected void afterUpdate() {
        if (offer(selected) == null && !view.offers.isEmpty()) selected = view.offers.getFirst().id;
    }

    private Views.OfferLine offer(String id) {
        for (Views.OfferLine o : view.offers) if (o.id.equals(id)) return o;
        return null;
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14 - (selling ? 0 : 26)) / ROW);
    }

    @Override
    protected int listTotal() {
        return selling ? view.buyerItems.size() : view.offers.size();
    }

    @Override
    protected void build() {
        addNav(0, new KitNav(0, 0, 0, KitIcon.BOX, "Kaufen", () -> String.valueOf(view.offers.size()), () -> !selling,
                () -> { selling = false; offset = 0; rebuildWidgets(); }));
        addNav(1, new KitNav(0, 0, 0, KitIcon.ARROW_RIGHT, "Verkaufen", () -> String.valueOf(view.buyerItems.size()), () -> selling,
                () -> { selling = true; offset = 0; rebuildWidgets(); }));
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int rows = listRows();
        pager(x + w, y - 2, listTotal(), rows);
        if (selling) {
            for (int i = 0; i < rows && offset + i < view.buyerItems.size(); i++) {
                Views.BuyerLine l = view.buyerItems.get(offset + i);
                int ry = y + 14 + i * ROW;
                boolean can = l.have > 0 && l.left != 0;
                addRenderableWidget(KitButton.second(x + w - 98, ry, 40, "1×", b -> sell(l.id, false))).active = can;
                addRenderableWidget(KitButton.primary(x + w - 54, ry, 54, "Alle", b -> sell(l.id, true)).accent(accent())).active = can;
            }
            return;
        }
        for (int i = 0; i < rows && offset + i < view.offers.size(); i++) {
            Views.OfferLine o = view.offers.get(offset + i);
            int ry = y + 14 + i * ROW;
            boolean sel = o.id.equals(selected);
            KitButton b = new KitButton(x, ry, w, 20, Component.empty(), null, sel ? KitButton.Style.PRIMARY : KitButton.Style.SECOND, bt -> {
                selected = o.id;
                times = 1;
                rebuildWidgets();
            });
            addRenderableWidget(b.accent(KitColors.darker(accent(), 0.35f)));
        }
        Views.OfferLine o = offer(selected);
        if (o == null) return;
        int by = cy + ch - PAD - 20;
        addRenderableWidget(KitButton.icon(x, by, 20, KitIcon.MINUS, "Weniger", b -> { times = Math.max(1, times - 1); rebuildWidgets(); }));
        addRenderableWidget(KitButton.icon(x + 60, by, 20, KitIcon.PLUS, "Mehr", b -> {
            times = Math.min(o.stock < 0 ? 64 : Math.max(1, Math.min(64, o.stock)), times + 1);
            rebuildWidgets();
        }));
        payToggle(x + 86, by, 60);
        int bw = Math.min(170, x + w - (x + 150));
        String label = (o.stock >= 0 && times > 1 ? "Kaufen · ab " : "Kaufen · ") + money(o.price * times);
        addRenderableWidget(KitButton.primary(x + w - bw, by, bw, KitDraw.fit(label, bw - 8), b -> {
            Views.Action a = Views.Action.of("buy");
            a.id = o.id;
            a.count = times;
            a.cash = cash;
            send(a);
        }).accent(accent())).active = o.stock != 0;
    }

    private void sell(String id, boolean all) {
        Views.Action a = Views.Action.of("sell");
        a.id = id;
        a.flag = all;
        send(a);
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        if (selling) {
            KitDraw.label(g, "Ankauf · Auszahlung " + (view.payCash ? "bar" : "aufs Konto"), x, y);
            if (view.buyerItems.isEmpty()) KitDraw.wrapped(g, Component.literal("Hier wird nichts angekauft."), x, y + 16, w, KitColors.TEXT_SOFT);
            int rows = listRows();
            for (int i = 0; i < rows && offset + i < view.buyerItems.size(); i++) {
                Views.BuyerLine l = view.buyerItems.get(offset + i);
                int ry = y + 14 + i * ROW;
                if (i % 2 == 0) g.fill(x, ry, x + w - 102, ry + 20, 0x18FFFFFF);
                item(g, l.item, x + 2, ry + 2);
                String price = l.bundle + " Stück → " + money(l.price);
                int right = x + w - 106;
                text(g, KitDraw.fit(l.name, right - (x + 22) - font.width(price) - 8), x + 22, ry + 1, KitColors.TEXT);
                String info = "dabei " + l.have + (!l.note.isEmpty() ? " · " + l.note : l.left >= 0 ? " · heute noch " + l.left : "");
                text(g, KitDraw.fit(info, right - (x + 22) - font.width(price) - 8), x + 22, ry + 11, l.have > 0 ? KitColors.MUTED : KitColors.FAINT);
                right(g, price, right, ry + 6, KitColors.TEXT);
            }
        } else {
            KitDraw.label(g, "Angebot", x, y);
            if (view.offers.isEmpty()) KitDraw.wrapped(g, Component.literal("Hier gibt es gerade nichts zu kaufen."), x, y + 16, w, KitColors.TEXT_SOFT);
            Views.OfferLine o = offer(selected);
            if (o != null) {
                int by = cy + ch - PAD - 20;
                String n = times + "×";
                g.text(font, n, x + 40 - font.width(n) / 2, by + 6, KitColors.TEXT, false);
            }
        }
        footer(g);
    }

    @Override
    protected void renderOverlay(GuiGraphicsExtractor g, int mx, int my, float d) {
        if (selling) return;
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int rows = listRows();
        for (int i = 0; i < rows && offset + i < view.offers.size(); i++) {
            Views.OfferLine o = view.offers.get(offset + i);
            int ry = y + 14 + i * ROW;
            item(g, o.item, x + 3, ry + 2);
            String price = o.count + "× für " + money(o.price);
            if (o.note.isEmpty()) {
                text(g, KitDraw.fit(o.name, w - 40 - font.width(price)), x + 24, ry + 6, KitColors.TEXT);
            } else {
                text(g, KitDraw.fit(o.name, w - 40 - font.width(price)), x + 24, ry + 1, KitColors.TEXT);
                text(g, KitDraw.fit(o.note, w - 40 - font.width(price)), x + 24, ry + 11, o.stock <= 0 ? KitColors.AMBER_TEXT : KitColors.MUTED);
            }
            right(g, price, x + w - 8, ry + 6, o.stock == 0 ? KitColors.AMBER_TEXT : KitColors.TEXT);
        }
    }
}
