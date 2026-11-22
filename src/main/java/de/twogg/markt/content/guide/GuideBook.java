package de.twogg.markt.content.guide;

import de.twogg.markt.MarktRuntime;
import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.core.MarktConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * Das Markt-Handbuch (seit 0.3.0): ein echtes beschriebenes Buch zum Durchblättern – Titelseite, anklickbares
 * Inhaltsverzeichnis, Kapitel aus {@code data/twogg_markt/handbuch/de_de.txt}. Zahlen (Gebühr, Preise, Grenzen) kommen
 * aus der aktuellen {@code markt.json}. Eigenes Aussehen über die Komponente {@code item_model}
 * ({@code twogg_markt:handbuch}); auf Vanilla-Clients ist es ein normales Buch. Lässt sich aufs Lesepult legen.
 */
public final class GuideBook {

    public static final String TEXT = "/data/twogg_markt/handbuch/de_de.txt";
    public static final String MARKER = "twogg_markt_handbuch";
    public static final String AUTHOR = "Marktleitung";

    private GuideBook() {
    }

    /** Handbuchtext aus dem Mod-Jar. */
    public static String source() {
        try (InputStream in = GuideBook.class.getResourceAsStream(TEXT)) {
            if (in == null) {
                return "# Handbuch\nDer Text fehlt im Mod-Jar.";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "# Handbuch\nDer Text ließ sich nicht lesen.";
        }
    }

    /** Werte für die Platzhalter im Text. */
    public static Map<String, String> values(MarktConfig c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("title", c.title);
        v.put("period", c.stalls.periodName);
        v.put("maxStalls", c.stalls.maxPerPlayer == 1 ? "einen Stand" : c.stalls.maxPerPlayer + " Stände");
        v.put("maxPerPlayer", String.valueOf(c.stalls.maxPerPlayer));
        v.put("maxHelpers", String.valueOf(c.stalls.maxHelpers));
        v.put("counterPrice", num(c.shop.counterPrice));
        v.put("rentRefund", num(c.stalls.rentRefundPercent));
        v.put("buyback", num(c.stalls.buybackPercent));
        v.put("maxOffers", String.valueOf(c.shop.maxOffers));
        v.put("fee", num(c.shop.feePercent));
        v.put("minFactor", num(c.buyer.minFactor * 100));
        v.put("jobsActive", String.valueOf(c.jobs.active));
        v.put("jobsMax", String.valueOf(c.jobs.maxPerPlayer));
        v.put("jobsMaxReward", num(c.jobs.maxReward));
        v.put("snowBlocks", String.valueOf(c.snow.blocksPerPay));
        v.put("snowPay", num(c.snow.pay));
        v.put("snowDaily", num(c.snow.dailyMax));
        v.put("ticket", num(c.event.ticketPrice));
        v.put("maxTickets", String.valueOf(c.event.maxTickets));
        v.put("pot", num(c.event.potPayoutPercent));
        v.put("giftReward", num(c.event.giftReward));
        int n = c.event.lotteryShares.size();
        v.put("winners", n == 1 ? "einen Gewinner" : n == 2 ? "zwei Gewinner" : n == 3 ? "drei Gewinner" : n + " Gewinner");
        v.put("shares", String.join(" / ", c.event.lotteryShares.stream().map(GuideBook::num).toList()));
        return v;
    }

    /** Zahl deutsch: 0,1 · 50 · 2,5. */
    static String num(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-9) {
            return String.valueOf((long) Math.rint(d));
        }
        return String.format(Locale.GERMANY, "%.2f", d).replaceAll("0+$", "").replaceAll(",$", "");
    }

    /** Seiten als Text-Komponenten (Titelseite, Inhalt, Kapitel). */
    public static List<Component> pages(MarktConfig c) {
        BookPaginator.Book book = BookPaginator.paginate(BookPaginator.fill(source(), values(c)));
        List<Component> out = new ArrayList<>();
        out.add(titlePage(c));
        // Inhaltsverzeichnis: 12 Einträge je Seite
        List<Map.Entry<String, Integer>> chapters = new ArrayList<>(book.chapters().entrySet());
        int tocPages = Math.max(1, (chapters.size() + 11) / 12);
        int first = 1 + tocPages; // Anzahl Seiten vor dem ersten Kapitel
        for (int t = 0; t < tocPages; t++) {
            MutableComponent toc = Component.empty();
            toc.append(Component.literal(t == 0 ? "Inhalt\n\n" : "Inhalt (weiter)\n\n").withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
            for (int i = t * 12; i < Math.min(chapters.size(), (t + 1) * 12); i++) {
                String name = chapters.get(i).getKey();
                int page = first + chapters.get(i).getValue() + 1; // 1-basiert
                toc.append(tocLine(name, page));
            }
            out.add(toc);
        }
        for (BookPaginator.Page p : book.pages()) {
            out.add(page(p));
        }
        return out;
    }

    private static Component tocLine(String name, int page) {
        String num = String.valueOf(page);
        int room = BookPaginator.WIDTH - BookPaginator.width(num, false) - 2;
        String label = name;
        while (BookPaginator.width(label + " .", false) > room && label.length() > 3) {
            label = label.substring(0, label.length() - 2) + "…";
        }
        StringBuilder dots = new StringBuilder(label).append(' ');
        while (BookPaginator.width(dots + ".", false) <= room) {
            dots.append('.');
        }
        Style link = Style.EMPTY.withColor(ChatFormatting.DARK_BLUE).withClickEvent(new ClickEvent.ChangePage(page))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Zu Seite " + num)));
        return Component.literal(dots + " " + num + "\n").withStyle(link);
    }

    private static Component titlePage(MarktConfig c) {
        MutableComponent t = Component.empty();
        t.append(Component.literal("\n"));
        t.append(centered("❄ ✦ ❄", false).withStyle(ChatFormatting.DARK_AQUA));
        t.append(Component.literal("\n\n"));
        t.append(centered("Markt-Handbuch", true).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
        t.append(Component.literal("\n\n"));
        t.append(centered(c.title, false).withStyle(ChatFormatting.DARK_GREEN));
        t.append(Component.literal("\n"));
        t.append(centered(c.subtitle, false).withStyle(ChatFormatting.DARK_GREEN));
        t.append(Component.literal("\n\n\n"));
        t.append(centered("Alles über Stände, Theken,", false).withStyle(ChatFormatting.DARK_GRAY));
        t.append(Component.literal("\n"));
        t.append(centered("Ankauf, Aufträge und den", false).withStyle(ChatFormatting.DARK_GRAY));
        t.append(Component.literal("\n"));
        t.append(centered("Weihnachtsmarkt.", false).withStyle(ChatFormatting.DARK_GRAY));
        t.append(Component.literal("\n\n"));
        t.append(centered("Umblättern →", false).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        return t;
    }

    private static MutableComponent centered(String s, boolean bold) {
        int pad = Math.max(0, (BookPaginator.WIDTH - BookPaginator.width(s, bold)) / 2 / BookPaginator.width(' ', false));
        return Component.literal(" ".repeat(pad) + s);
    }

    private static Component page(BookPaginator.Page p) {
        MutableComponent out = Component.empty();
        List<BookPaginator.Line> lines = p.lines();
        for (int i = 0; i < lines.size(); i++) {
            BookPaginator.Line l = lines.get(i);
            String nl = i + 1 < lines.size() ? "\n" : "";
            switch (l.kind()) {
                case CHAPTER -> out.append(Component.literal(l.text() + nl).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_RED));
                case SECTION -> out.append(Component.literal(l.text() + nl).withStyle(ChatFormatting.BOLD, ChatFormatting.DARK_GREEN));
                case BULLET -> {
                    if (l.text().startsWith("• ")) {
                        out.append(Component.literal("• ").withStyle(ChatFormatting.GOLD));
                        out.append(Component.literal(l.text().substring(2) + nl).withStyle(ChatFormatting.BLACK));
                    } else {
                        out.append(Component.literal(l.text() + nl).withStyle(ChatFormatting.BLACK));
                    }
                }
                case TEXT -> out.append(Component.literal(l.text() + nl).withStyle(ChatFormatting.BLACK));
                case BLANK -> out.append(Component.literal(nl));
            }
        }
        return out;
    }

    /** Das fertige Buch. */
    public static ItemStack create(MarktConfig c) {
        List<Filterable<Component>> pages = new ArrayList<>();
        for (Component p : pages(c)) {
            pages.add(Filterable.passThrough(p));
        }
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
                new WrittenBookContent(Filterable.passThrough("Markt-Handbuch"), AUTHOR, 0, pages, true));
        stack.set(DataComponents.ITEM_MODEL, TwoggMarkt.id("handbuch"));
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Ist das ein Markt-Handbuch? */
    public static boolean is(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBooleanOr(MARKER, false);
    }

    /** Ins Inventar legen (was nicht passt, fällt vor die Füße). */
    public static void give(ServerPlayer p) {
        ItemStack book = create(MarktRuntime.config());
        if (!p.getInventory().add(book)) {
            p.drop(book, false);
        }
    }
}
