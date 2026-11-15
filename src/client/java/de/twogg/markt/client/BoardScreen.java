package de.twogg.markt.client;

import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitNav;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Auftragsbrett: Aufträge liefern, eigene aushängen, gelieferte Ware abholen. */
public class BoardScreen extends MarktScreen {
    private enum Page { JOBS, POST, PICKUP }

    private Page page = Page.JOBS;
    private String countText = "16", rewardText = "", titleText = "";

    BoardScreen(Views.View v) {
        super(null, v, v.title, v.subtitle, 96);
    }

    @Override
    String screenId() {
        return "board";
    }

    @Override
    protected void afterUpdate() {
        armed = "";
    }

    private void go(Page p) {
        page = p;
        offset = 0;
        view.message = "";
        rebuildWidgets();
    }

    @Override
    protected int listRows() {
        return Math.max(1, (ch - 2 * PAD - 14) / 30);
    }

    @Override
    protected int listTotal() {
        return page == Page.JOBS ? view.jobs.size() : 0;
    }

    @Override
    protected void build() {
        addNav(0, new KitNav(0, 0, 0, KitIcon.GRID, "Aufträge", () -> String.valueOf(view.jobs.size()), () -> page == Page.JOBS, () -> go(Page.JOBS)));
        if (view.playerJobs) {
            addNav(1, new KitNav(0, 0, 0, KitIcon.PLUS, "Aushängen", () -> view.myJobs > 0 ? String.valueOf(view.myJobs) : "",
                    () -> page == Page.POST, () -> go(Page.POST)));
        }
        addNav(2, new KitNav(0, 0, 0, KitIcon.BOX, "Abholfach", () -> view.pickupCount > 0 ? String.valueOf(view.pickupCount) : "",
                () -> page == Page.PICKUP, () -> go(Page.PICKUP)));
        addFooterButton(KitButton.primary(0, 0, 70, "Fertig", b -> onClose()));
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        switch (page) {
            case JOBS -> {
                int rows = listRows();
                pager(x + w, y - 2, view.jobs.size(), rows);
                for (int i = 0; i < rows && offset + i < view.jobs.size(); i++) {
                    Views.JobLine j = view.jobs.get(offset + i);
                    int ry = y + 14 + i * 30 + 4;
                    if (j.mine) {
                        confirm(x + w - 84, ry, 84, "Zurückziehen", "cancel:" + j.id, () -> {
                            Views.Action a = Views.Action.of("cancel");
                            a.id = j.id;
                            send(a);
                        });
                    } else {
                        KitButton b = addRenderableWidget(KitButton.primary(x + w - 84, ry, 84, j.done ? "Erledigt" : "Liefern", bt -> {
                            Views.Action a = Views.Action.of("deliver");
                            a.id = j.id;
                            send(a);
                        }).accent(accent()));
                        b.active = !j.done && j.have >= j.count && j.remaining > 0;
                    }
                }
            }
            case POST -> {
                int fy = y + 70;
                input(x, fy, 62, "Stück", countText, 4, s -> countText = s, "Stk");
                input(x + 66, fy, 76, "Belohnung", rewardText, 8, s -> rewardText = s, view.unit);
                input(x + 146, fy, Math.max(80, w - 146), "Titel (optional)", titleText, 48, s -> titleText = s);
                payToggle(x, fy + 24, 62);
                KitButton post = addRenderableWidget(KitButton.primary(x + 66, fy + 24, 140, "Auftrag aushängen", b -> {
                    Views.Action a = Views.Action.of("post");
                    a.count = Math.max(1, parseInt(countText));
                    a.amount = parseTenths(rewardText);
                    a.text = titleText;
                    a.cash = cash;
                    send(a);
                }).accent(accent()));
                post.active = !view.hand.isEmpty() && parseTenths(rewardText) > 0 && parseInt(countText) > 0
                        && (view.maxJobs <= 0 || view.myJobs < view.maxJobs);
            }
            case PICKUP -> {
                KitButton b = addRenderableWidget(KitButton.primary(x, y + 100, 120, "Alles abholen", bt -> send("pickup")).accent(accent()));
                b.active = view.pickupCount > 0;
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        // „Aushängen“ freischalten, sobald die Felder passen (ohne die Felder neu zu bauen)
        if (page == Page.POST) {
            for (var c : children()) {
                if (c instanceof KitButton b && b.getMessage().getString().equals("Auftrag aushängen")) {
                    b.active = !view.hand.isEmpty() && parseTenths(rewardText) > 0 && parseInt(countText) > 0
                            && (view.maxJobs <= 0 || view.myJobs < view.maxJobs);
                }
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float d) {
        int x = cx + PAD, y = cy + PAD, w = cw - 2 * PAD;
        switch (page) {
            case JOBS -> {
                KitDraw.label(g, "Offene Aufträge" + (view.jobRefreshIn > 0 ? " · neue Marktaufträge in " + duration(view.jobRefreshIn) : ""), x, y);
                if (view.jobs.isEmpty()) KitDraw.wrapped(g, Component.literal("Gerade hängt nichts am Brett."), x, y + 16, w, KitColors.TEXT_SOFT);
                int rows = listRows();
                for (int i = 0; i < rows && offset + i < view.jobs.size(); i++) {
                    Views.JobLine j = view.jobs.get(offset + i);
                    int ry = y + 14 + i * 30;
                    KitDraw.well(g, x, ry, w - 90, 28);
                    item(g, j.item, x + 5, ry + 6);
                    int tx = x + 26, tw = w - 90 - 30;
                    String reward = money(j.reward);
                    text(g, KitDraw.fit(j.title, tw - font.width(reward) - 8), tx, ry + 4, KitColors.TEXT);
                    right(g, reward, x + w - 96, ry + 4, KitColors.GREEN_TEXT);
                    String sub = j.count + "× " + j.itemName + " · dabei " + j.have
                            + (j.system ? " · noch " + j.remaining + " von " + j.slots : " · von " + j.poster)
                            + (j.expiresIn > 0 && !j.system ? " · " + duration(j.expiresIn) : "");
                    text(g, KitDraw.fit(sub, tw), tx, ry + 16, j.have >= j.count ? KitColors.TEXT_SOFT : KitColors.FAINT);
                }
            }
            case POST -> {
                KitDraw.label(g, "Eigenen Auftrag aushängen", x, y);
                if (view.hand.isEmpty()) {
                    text(g, "Nimm ein Stück der gewünschten Ware in die Hand.", x, y + 18, KitColors.AMBER_TEXT);
                } else {
                    item(g, view.hand, x, y + 14);
                    text(g, "Gesucht: " + view.handName, x + 22, y + 18, KitColors.TEXT);
                }
                KitDraw.wrapped(g, Component.literal("Die Belohnung (höchstens " + money(view.maxReward) + ") wird beim Aushängen "
                        + "hinterlegt. Wer liefert, bekommt sie, die Ware kommt in dein Abholfach. Zurückziehen oder Ablauf: Geld zurück."),
                        x, y + 34, w, y + 68, KitColors.TEXT_SOFT);
                text(g, "Aufträge von dir: " + view.myJobs + (view.maxJobs > 0 ? " von " + view.maxJobs : ""), x, y + 120, KitColors.FAINT);
            }
            case PICKUP -> {
                KitDraw.label(g, "Abholfach · " + view.pickupCount + " Stück", x, y);
                if (view.pickup.isEmpty()) text(g, "Leer. Hier landet Ware, die jemand für deine Aufträge liefert.", x, y + 16, KitColors.TEXT_SOFT);
                int ty = y + 16;
                for (String s : view.pickup) {
                    text(g, s, x, ty, KitColors.TEXT);
                    ty += 11;
                }
            }
        }
        footer(g);
    }
}
