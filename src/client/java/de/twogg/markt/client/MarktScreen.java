package de.twogg.markt.client;

import de.twogg.markt.net.Payloads;
import de.twogg.markt.net.Views;
import de.twogg.kit.KitButton;
import de.twogg.kit.KitColors;
import de.twogg.kit.KitDraw;
import de.twogg.kit.KitIcon;
import de.twogg.kit.KitScreen;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gemeinsame Basis der Markt-Fenster (TwoGG Kit). Der Server schickt eine {@link Views.View}; das Fenster zeigt sie an
 * und schickt Wünsche zurück. Kommt ein neuer Stand für dasselbe Fenster, wird es an Ort und Stelle aktualisiert.
 */
abstract class MarktScreen extends KitScreen {
    static final int ROW = 22;
    protected Views.View view;
    /** Gewählte Zeile und Blätter-Offset der Liste. */
    protected int offset;
    private static final Map<String, ItemStack> ITEMS = new HashMap<>();
    /** Bestätigung für endgültige Knöpfe: erst „Sicher?“, dann ausführen. */
    protected String armed = "";

    protected MarktScreen(Screen parent, Views.View view, String title, String subtitle, int sidebar) {
        super(parent, title, subtitle, false, sidebar);
        this.view = view;
    }

    /** Neuer Stand vom Server. */
    static void receive(String json) {
        Views.View v;
        try {
            v = Views.GSON.fromJson(json, Views.View.class);
        } catch (RuntimeException e) {
            return;
        }
        if (v == null || v.screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MarktScreen open && open.screenId().equals(v.screen) && open.sameContext(v)) {
            open.update(v);
            return;
        }
        MarktScreen s = switch (v.screen) {
            case "market" -> new MarketScreen(v);
            case "shop" -> new ShopScreen(v);
            case "buyer" -> new BuyerScreen(v);
            case "board" -> new BoardScreen(v);
            case "admin" -> new AdminScreen(mc.screen instanceof AdminScreen ? null : mc.screen, v);
            case "lottery", "advent" -> new ActivityScreen(v);
            default -> null;
        };
        if (s != null) mc.setScreen(s);
    }

    abstract String screenId();

    /** Gehört die neue Ansicht zu diesem offenen Fenster (z. B. dieselbe Theke)? */
    protected boolean sameContext(Views.View v) {
        return true;
    }

    protected void update(Views.View v) {
        this.view = v;
        afterUpdate();
        rebuildWidgets();
    }

    protected void afterUpdate() {
    }

    @Override
    protected int accent() {
        return view == null ? KitColors.BLUE : view.accent;
    }

    protected void send(Views.Action a) {
        a.screen = screenId();
        if (ClientPlayNetworking.canSend(Payloads.Action.TYPE)) {
            ClientPlayNetworking.send(new Payloads.Action(Views.GSON.toJson(a)));
        }
    }

    protected void send(String a) {
        send(Views.Action.of(a));
    }

    @Override
    public void removed() {
        Views.Action close = Views.Action.of("close");
        close.session = view.session;
        send(close);
        super.removed();
    }

    // ------------------------------------------------------------------ Helfer

    static String money(long tenths, String unit) {
        long abs = Math.abs(tenths);
        String s = (abs / 10) + (abs % 10 == 0 ? "" : "," + (abs % 10));
        return (tenths < 0 ? "-" : "") + s + (unit == null || unit.isBlank() ? "" : " " + unit);
    }

    String money(long tenths) {
        return money(tenths, view.unit);
    }

    static String euro(double v) {
        return String.format(Locale.GERMANY, "%.2f €", v);
    }

    static String num(double v) {
        if (v == Math.rint(v)) return String.valueOf((long) v);
        return String.format(Locale.ROOT, "%s", v).replace('.', ',');
    }

    static String duration(long ms) {
        if (ms <= 0) return "abgelaufen";
        long min = ms / 60_000;
        long d = min / 1440, h = (min % 1440) / 60, m = min % 60;
        if (d > 0) return d + " T " + h + " Std";
        if (h > 0) return h + " Std " + m + " Min";
        return Math.max(1, m) + " Min";
    }

    /** "12,5" → 125 Zehntel; -1 bei ungültig (höchstens eine Nachkommastelle). */
    static long parseTenths(String s) {
        try {
            String t = s.trim().replace(" ", "").replace(',', '.');
            if (t.isEmpty()) return -1;
            java.math.BigDecimal d = new java.math.BigDecimal(t);
            if (d.signum() < 0 || d.compareTo(java.math.BigDecimal.valueOf(1_000_000)) > 0) return -1;
            if (d.stripTrailingZeros().scale() > 1) return -1;
            return d.movePointRight(1).longValueExact();
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static double parseDouble(String s) {
        try {
            double d = Double.parseDouble(s.trim().replace(',', '.'));
            return Double.isFinite(d) ? d : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (RuntimeException e) {
            return -1;
        }
    }

    /** Item aus dem Server-JSON (mit Daten-Komponenten), zwischengespeichert. */
    static ItemStack stack(String json) {
        if (json == null || json.isEmpty()) return ItemStack.EMPTY;
        ItemStack cached = ITEMS.get(json);
        if (cached != null) return cached;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return ItemStack.EMPTY;
        ItemStack s;
        try {
            s = ItemStack.CODEC.parse(mc.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),
                    com.google.gson.JsonParser.parseString(json)).result().orElse(ItemStack.EMPTY);
        } catch (RuntimeException e) {
            s = ItemStack.EMPTY;
        }
        if (ITEMS.size() > 512) ITEMS.clear();
        ITEMS.put(json, s);
        return s;
    }

    protected void item(GuiGraphicsExtractor g, String json, int x, int y) {
        ItemStack s = stack(json);
        if (!s.isEmpty()) g.item(s, x, y);
    }

    /** Eingabefeld ohne Vanilla-Rahmen in einer Kit-Mulde ({@link #wells} zeichnet die Mulden). */
    protected EditBox input(int x, int y, int w, String hint, String value, int max, java.util.function.Consumer<String> onChange) {
        return input(x, y, w, hint, value, max, onChange, "");
    }

    /** Wie oben, mit fester Beschriftung rechts in der Mulde (z. B. „€“, „Stück“) – sichtbar auch mit Inhalt. */
    protected EditBox input(int x, int y, int w, String hint, String value, int max, java.util.function.Consumer<String> onChange, String unit) {
        int uw = unit.isEmpty() ? 0 : KitDraw.font().width(unit) + 4;
        EditBox box = new EditBox(KitDraw.font(), x + 5, y + 6, w - 10 - uw, 10, Component.literal(hint));
        box.setBordered(false);
        box.setMaxLength(max);
        box.setTextColor(KitColors.TEXT);
        box.setHint(Component.literal(hint).withColor(KitColors.FAINT));
        box.setValue(value == null ? "" : value);
        box.setResponder(onChange);
        wells.add(new Well(x, y, w, unit));
        return addRenderableWidget(box);
    }

    private record Well(int x, int y, int w, String unit) {}

    private final java.util.List<Well> wells = new java.util.ArrayList<>();

    @Override
    protected final void initWindow() {
        wells.clear();
        build();
    }

    /** Knöpfe und Felder anlegen (statt initWindow). */
    protected abstract void build();

    @Override
    protected void renderPanels(GuiGraphicsExtractor g, int mx, int my, float d) {
        for (Well w : wells) {
            KitDraw.well(g, w.x(), w.y(), w.w(), 20);
            if (!w.unit().isEmpty()) g.text(font, w.unit(), w.x() + w.w() - 4 - font.width(w.unit()), w.y() + 6, KitColors.FAINT, false);
        }
    }

    protected void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font, s, x, y, color, false);
    }

    protected void right(GuiGraphicsExtractor g, String s, int xRight, int y, int color) {
        g.text(font, s, xRight - font.width(s), y, color, false);
    }

    /** Knopf mit Bestätigung: erster Klick zeigt „Sicher?“, zweiter führt aus. */
    protected KitButton confirm(int x, int y, int w, String label, String key, Runnable run) {
        boolean on = armed.equals(key);
        return addRenderableWidget(KitButton.danger(x, y, w, on ? "Sicher?" : label, b -> {
            if (armed.equals(key)) {
                armed = "";
                run.run();
            } else {
                armed = key;
                rebuildWidgets();
            }
        }));
    }

    /** Blättern-Pfeile oben rechts über einer Liste. */
    protected void pager(int xRight, int y, int total, int rows) {
        if (total <= rows) {
            offset = 0;
            return;
        }
        offset = Math.max(0, Math.min(offset, total - rows));
        addRenderableWidget(KitButton.icon(xRight - 32, y, 14, KitIcon.ARROW_UP, "Zurück", b -> {
            offset = Math.max(0, offset - rows);
            rebuildWidgets();
        }));
        addRenderableWidget(KitButton.icon(xRight - 14, y, 14, KitIcon.ARROW_DOWN, "Weiter", b -> {
            offset = Math.min(Math.max(0, total - rows), offset + rows);
            rebuildWidgets();
        }));
    }

    protected int listTotal() {
        return 0;
    }

    protected int listRows() {
        return 1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int total = listTotal(), rows = listRows();
        if (total > rows && sy != 0) {
            offset = Math.max(0, Math.min(total - rows, offset + (sy < 0 ? 1 : -1)));
            rebuildWidgets();
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    /** Status links in der Fußzeile: Meldung oder Kontostand. */
    protected void footer(GuiGraphicsExtractor g) {
        if (view.message != null && !view.message.isEmpty()) {
            footerStatus(g, view.error ? KitColors.AMBER_TEXT : KitColors.GREEN_TEXT, view.message);
        } else if (view.hasAccount) {
            footerStatus(g, KitColors.FAINT, "Konto " + money(view.balance) + " · Bargeld " + money(view.cash)
                    + (view.pending > 0 ? " · vorgemerkt " + money(view.pending) : ""));
        } else {
            footerStatus(g, KitColors.AMBER_TEXT, "Kein Konto – spiele zuerst einen Charakter.");
        }
    }

    /** Konto/Bar-Umschalter als Knopf. */
    protected boolean cash;

    protected void payToggle(int x, int y, int w) {
        if (!view.allowCash) {
            cash = false;
            return;
        }
        addRenderableWidget(new KitButton(x, y, w, 20, Component.literal(cash ? "Bar" : "Konto"), cash ? KitIcon.BOX : KitIcon.LOCK,
                KitButton.Style.SECOND, b -> {
            cash = !cash;
            rebuildWidgets();
        }));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
