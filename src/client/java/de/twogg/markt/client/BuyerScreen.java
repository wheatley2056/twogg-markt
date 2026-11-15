package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Ankaufstelle: was der Markt kauft, Preis heute, eigener Vorrat, Tagesgrenze. */
public class BuyerScreen extends MarktScreen {
    BuyerScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, 0);
    }

    @Override
    String screenId() {
        return "buyer";
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14) / ROW);
    }

    @Override
    protected int listTotal() {
        return view.buyerItems.size();
    }

    @Override
    protected void build() {
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        int rows = listRows();
        pager(x + w, y - 2, view.buyerItems.size(), rows);
        for (int i = 0; i < rows && offset + i < view.buyerItems.size(); i++) {
            Views.BuyerLine l = view.buyerItems.get(offset + i);
            int ry = y + 14 + i * ROW;
            boolean can = l.have > 0 && l.left != 0 && l.price > 0;
            String id = itemId(l);
            KitButton one = addRenderableWidget(KitButton.second(x + w - 98, ry, 40, "1×", b -> sell(id, false)));
            one.active = can;
            KitButton all = addRenderableWidget(KitButton.primary(x + w - 54, ry, 54, "Alle", b -> sell(id, true)).accent(accent()));
            all.active = can;
        }
    }

    /** Die Item-ID steht im Stapel-JSON unter "id". */
    private static String itemId(Views.BuyerLine l) {
        try {
            return com.google.gson.JsonParser.parseString(l.item).getAsJsonObject().get("id").getAsString();
        } catch (RuntimeException e) {
            return "";
        }
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
        KitDraw.label(g, "Der Markt kauft · Auszahlung " + (view.payCash ? "bar" : "aufs Konto"), x, y);
        if (view.buyerItems.isEmpty()) {
            KitDraw.wrapped(g, Component.literal("Heute kauft der Markt nichts an."), x, y + 16, w, KitColors.TEXT_SOFT);
        }
        int rows = listRows();
        for (int i = 0; i < rows && offset + i < view.buyerItems.size(); i++) {
            Views.BuyerLine l = view.buyerItems.get(offset + i);
            int ry = y + 14 + i * ROW;
            if (i % 2 == 0) g.fill(x, ry, x + w - 102, ry + 20, 0x18FFFFFF);
            item(g, l.item, x + 2, ry + 2);
            String price = l.bundle + " Stück → " + money(l.price);
            int pc = l.price >= l.basePrice ? KitColors.GREEN_TEXT : l.price * 2 >= l.basePrice ? KitColors.TEXT : KitColors.AMBER_TEXT;
            int right = x + w - 106;
            int priceW = font.width(price);
            text(g, KitDraw.fit(l.name, right - (x + 22) - priceW - 8), x + 22, ry + 1, KitColors.TEXT);
            String info = "dabei " + l.have + (l.left >= 0 ? " · heute noch " + l.left : "")
                    + (l.price < l.basePrice ? " · sonst " + money(l.basePrice) : "");
            text(g, KitDraw.fit(info, right - (x + 22)), x + 22, ry + 11, l.have > 0 ? KitColors.MUTED : KitColors.FAINT);
            right(g, price, right, ry + 6, pc);
        }
        footer(g);
    }
}
