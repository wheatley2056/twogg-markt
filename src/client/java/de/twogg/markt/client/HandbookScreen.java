package de.twogg.markt.client;

import de.twogg.markt.TwoggMarkt;
import de.twogg.markt.content.guide.GuideBook;
import de.twogg.markt.content.guide.HandbookLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Markt-Handbuch (seit 0.3.0): ein eigenes Buch mit Doppelseiten – Ledereinband, Pergament, Titelseite, anklickbares
 * Inhaltsverzeichnis, Kapitel mit Bildern aus dem Spiel und Lesezeichen am Rand. Blättern mit den Pfeilen unten, den
 * Pfeiltasten oder dem Mausrad; ein Klick auf ein Bild vergrößert es.
 */
public final class HandbookScreen extends Screen {

    private static final Identifier BOOK = TwoggMarkt.id("textures/gui/handbuch/buch.png");
    private static final String TEXT = "/assets/twogg_markt/handbuch/de_de.txt";
    /** Größe der Bilder im Jar (16:9). */
    private static final int IMG_W = 384, IMG_H = 216;
    /** Buchgrafik 512 × 320; Seiten links 16…254, rechts 258…496, oben 12, unten 306. */
    private static final int TEX_W = 512, TEX_H = 320;

    private static final int INK = 0xFF3B2A1E;
    private static final int RED = 0xFF8E1B1B;
    private static final int GREEN = 0xFF2E6B3A;
    private static final int GOLD = 0xFFB8860B;
    private static final int FAINT = 0xFF7A6852;
    private static final int LINK = 0xFF1F3F7A;
    private static final int[] TABS = {0xFFB3262B, 0xFF2E7D46, 0xFFC28E2C, 0xFF2F5E9E, 0xFF7B3FA0, 0xFFB35A1F, 0xFF3A8C8C, 0xFF7A1F2E};

    private final Map<String, String> values;
    private final String source;
    private HandbookLayout.Book book;
    private List<String> chapters = List.of();
    /** Doppelseite: zeigt die Seiten 2·spread und 2·spread + 1 (Seite 0 = Titel, 1 = Inhalt, ab 2 die Kapitel). */
    private int spread;
    private int bx, by, bw, bh, pageW, pageH, leftX, rightX, top;
    private String zoom;
    private final List<Hit> hits = new ArrayList<>();

    private record Hit(int x0, int y0, int x1, int y1, Runnable action) {
        boolean in(double x, double y) {
            return x >= x0 && x < x1 && y >= y0 && y < y1;
        }
    }

    public HandbookScreen(Map<String, String> values) {
        super(Component.literal("Markt-Handbuch"));
        this.values = values == null ? Map.of() : values;
        this.source = GuideBook.fill(load(), this.values);
    }

    private static String load() {
        try (InputStream in = HandbookScreen.class.getResourceAsStream(TEXT)) {
            return in == null ? "# Handbuch\nDer Text fehlt." : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "# Handbuch\nDer Text ließ sich nicht lesen.";
        }
    }

    @Override
    protected void init() {
        bh = Math.min(height - 34, 250);
        bw = bh * TEX_W / TEX_H;
        if (bw > width - 40) {
            bw = width - 40;
            bh = bw * TEX_H / TEX_W;
        }
        bx = (width - bw) / 2 - 6;
        by = (height - bh) / 2 - 9;
        int margin = Math.max(8, bw * 12 / TEX_W);
        leftX = bx + bw * 16 / TEX_W + margin;
        rightX = bx + bw * 258 / TEX_W + margin;
        pageW = bw * 238 / TEX_W - 2 * margin;
        top = by + bh * 12 / TEX_H + margin - 2;
        pageH = bh * 294 / TEX_H - margin - 18;
        book = HandbookLayout.layout(source, (s, bold) -> bold
                ? font.width(Component.literal(s).withStyle(ChatFormatting.BOLD)) : font.width(s), pageW, pageH);
        chapters = new ArrayList<>(book.chapters().keySet());
        chapters.sort((a, b) -> Integer.compare(book.chapters().get(a), book.chapters().get(b)));
        spread = Math.min(spread, (pages() - 1) / 2);
    }

    /** Seiten insgesamt (Titel + Inhalt + Kapitel). */
    public int pages() {
        return 2 + book.pages().size();
    }

    public int spread() {
        return spread;
    }

    /** Zu einer Seite (0-basiert) blättern. */
    public void goTo(int page) {
        int s = Math.max(0, Math.min((pages() - 1) / 2, page / 2));
        if (s != spread) {
            spread = s;
            zoom = null;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1f));
        }
    }

    /** Erste Seite eines Kapitels (0-basiert, im ganzen Buch). */
    public int chapterPage(String chapter) {
        Integer p = book.chapters().get(chapter);
        return p == null ? 0 : p + 2;
    }

    public List<String> chapters() {
        return chapters;
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        super.extractRenderState(g, mx, my, delta);
        hits.clear();
        tabs(g, mx, my);
        g.blit(RenderPipelines.GUI_TEXTURED, BOOK, bx, by, 0f, 0f, bw, bh, TEX_W, TEX_H, TEX_W, TEX_H);
        page(g, 2 * spread, leftX, mx, my);
        page(g, 2 * spread + 1, rightX, mx, my);
        footer(g, mx, my);
        if (zoom != null) {
            int zw = Math.min(width - 40, (height - 40) * 16 / 9);
            int zh = zw * 9 / 16;
            int zx = (width - zw) / 2, zy = (height - zh) / 2;
            g.fill(0, 0, width, height, 0xC0000000);
            g.fill(zx - 3, zy - 3, zx + zw + 3, zy + zh + 3, 0xFFE9DCC0);
            g.blit(RenderPipelines.GUI_TEXTURED, image(zoom), zx, zy, 0f, 0f, zw, zh, IMG_W, IMG_H, IMG_W, IMG_H);
            g.text(font, "Klicken zum Schließen", zx, zy + zh + 6, 0xFFBBBBBB, false);
        }
    }

    private static Identifier image(String name) {
        return TwoggMarkt.id("textures/gui/handbuch/" + name + ".png");
    }

    private void page(GuiGraphicsExtractor g, int index, int x, int mx, int my) {
        if (index >= pages()) {
            return;
        }
        if (index == 0) {
            title(g, x);
        } else if (index == 1) {
            toc(g, x, mx, my);
        } else {
            for (HandbookLayout.Item it : book.pages().get(index - 2).items()) {
                int y = top + it.y();
                switch (it.kind()) {
                    case CHAPTER -> g.text(font, Component.literal(it.text()).withStyle(ChatFormatting.BOLD), x, y, RED, false);
                    case SECTION -> g.text(font, Component.literal(it.text()).withStyle(ChatFormatting.BOLD), x, y, GREEN, false);
                    case BULLET -> {
                        if (it.text().startsWith("• ")) {
                            g.text(font, "•", x, y, GOLD, false);
                            g.text(font, it.text().substring(2), x + font.width("• "), y, INK, false);
                        } else {
                            g.text(font, it.text(), x, y, INK, false);
                        }
                    }
                    case TEXT -> g.text(font, it.text(), x, y, INK, false);
                    case CAPTION -> g.text(font, Component.literal(it.text()).withStyle(ChatFormatting.ITALIC), x, y, FAINT, false);
                    case IMAGE -> {
                        int h = it.height();
                        boolean hover = mx >= x && mx < x + pageW && my >= y && my < y + h;
                        g.fill(x - 1, y - 1, x + pageW + 1, y + h + 1, hover ? 0xFFB8860B : 0xFF6B5641);
                        g.blit(RenderPipelines.GUI_TEXTURED, image(it.text()), x, y, 0f, 0f, pageW, h, IMG_W, IMG_H, IMG_W, IMG_H);
                        String name = it.text();
                        hits.add(new Hit(x, y, x + pageW, y + h, () -> zoom = name));
                    }
                }
            }
        }
        // Seitenzahl
        String num = String.valueOf(index + 1);
        int ny = by + bh * 306 / TEX_H - 14;
        int nx = x == leftX ? leftX : rightX + pageW - font.width(num);
        g.text(font, num, nx, ny, FAINT, false);
    }

    private void title(GuiGraphicsExtractor g, int x) {
        int cx = x + pageW / 2;
        int y = top + 6;
        center(g, Component.literal("❄  ✦  ❄"), cx, y, 0xFF2F6E8E);
        y += 16;
        g.pose().pushMatrix();
        g.pose().translate(cx, y);
        g.pose().scale(2f, 2f);
        Component t = Component.literal("Markt-").withStyle(ChatFormatting.BOLD);
        g.text(font, t, -font.width(t) / 2, 0, RED, false);
        Component t2 = Component.literal("Handbuch").withStyle(ChatFormatting.BOLD);
        g.text(font, t2, -font.width(t2) / 2, 10, RED, false);
        g.pose().popMatrix();
        y += 44;
        center(g, Component.literal(values.getOrDefault("title", "Weihnachtsmarkt")), cx, y, GREEN);
        y += 10;
        center(g, Component.literal(values.getOrDefault("subtitle", "")), cx, y, GREEN);
        y += 16;
        int iw = pageW - 36;
        int ih = HandbookLayout.imageHeight(iw);
        if (y + ih + 24 < top + pageH) {
            int ix = cx - iw / 2;
            g.fill(ix - 1, y - 1, ix + iw + 1, y + ih + 1, 0xFF6B5641);
            g.blit(RenderPipelines.GUI_TEXTURED, image("markt"), ix, y, 0f, 0f, iw, ih, IMG_W, IMG_H, IMG_W, IMG_H);
            y += ih + 8;
        }
        center(g, Component.literal("Umblättern: Pfeile unten,").withStyle(ChatFormatting.ITALIC), cx, y, FAINT);
        center(g, Component.literal("Pfeiltasten oder Mausrad").withStyle(ChatFormatting.ITALIC), cx, y + 10, FAINT);
    }

    private void center(GuiGraphicsExtractor g, Component c, int cx, int y, int color) {
        g.text(font, c, cx - font.width(c) / 2, y, color, false);
    }

    private void toc(GuiGraphicsExtractor g, int x, int mx, int my) {
        g.text(font, Component.literal("Inhalt").withStyle(ChatFormatting.BOLD), x, top, RED, false);
        int y = top + 18;
        for (int i = 0; i < chapters.size(); i++) {
            String ch = chapters.get(i);
            int page = chapterPage(ch);
            String num = String.valueOf(page + 1);
            boolean hover = mx >= x && mx < x + pageW && my >= y - 1 && my < y + 11;
            int color = hover ? RED : LINK;
            g.fill(x, y + 1, x + 4, y + 7, TABS[i % TABS.length]);
            String label = ch;
            int room = pageW - 8 - font.width(num) - 6;
            while (font.width(label) > room && label.length() > 3) {
                label = label.substring(0, label.length() - 2) + "…";
            }
            g.text(font, label, x + 8, y, color, false);
            int dotsFrom = x + 8 + font.width(label) + 3, dotsTo = x + pageW - font.width(num) - 3;
            for (int d = dotsFrom; d < dotsTo; d += 4) {
                g.fill(d, y + 7, d + 1, y + 8, FAINT);
            }
            g.text(font, num, x + pageW - font.width(num), y, color, false);
            hits.add(new Hit(x, y - 1, x + pageW, y + 11, () -> goTo(page)));
            y += 14;
        }
    }

    private void tabs(GuiGraphicsExtractor g, int mx, int my) {
        int x = bx + bw - 6;
        int y = by + 18;
        int step = Math.min(20, (bh - 30) / Math.max(1, chapters.size()));
        for (int i = 0; i < chapters.size(); i++) {
            String ch = chapters.get(i);
            int page = chapterPage(ch);
            boolean here = page / 2 == spread;
            boolean hover = mx >= x && mx < x + 18 && my >= y && my < y + step - 3;
            int w = here || hover ? 18 : 14;
            g.fill(x, y, x + w, y + step - 3, TABS[i % TABS.length]);
            g.fill(x, y + step - 4, x + w, y + step - 3, 0x40000000);
            g.text(font, String.valueOf(i + 1), x + w - 9, y + (step - 3 - 8) / 2, 0xFFFFFFFF, false);
            if (hover) {
                g.text(font, ch, x + w + 4, y + (step - 3 - 8) / 2, 0xFFFFFFFF, true);
            }
            hits.add(new Hit(x, y, x + 18, y + step - 3, () -> goTo(page)));
            y += step;
        }
    }

    private void footer(GuiGraphicsExtractor g, int mx, int my) {
        int y = by + bh + 5;
        boolean canBack = spread > 0, canFwd = 2 * spread + 2 < pages();
        arrow(g, "◀ zurück", bx + 14, y, canBack, mx, my, () -> goTo(2 * spread - 2));
        String fwd = "weiter ▶";
        arrow(g, fwd, bx + bw - 14 - font.width(fwd), y, canFwd, mx, my, () -> goTo(2 * spread + 2));
        String close = "Schließen";
        int cx = bx + bw / 2 - font.width(close) / 2;
        boolean hc = mx >= cx - 4 && mx < cx + font.width(close) + 4 && my >= y - 2 && my < y + 10;
        g.text(font, close, cx, y, hc ? 0xFFFFE08A : 0xFFE8D9B8, true);
        hits.add(new Hit(cx - 4, y - 2, cx + font.width(close) + 4, y + 10, this::onClose));
    }

    private void arrow(GuiGraphicsExtractor g, String s, int x, int y, boolean on, int mx, int my, Runnable r) {
        boolean hover = on && mx >= x - 4 && mx < x + font.width(s) + 4 && my >= y - 2 && my < y + 10;
        g.text(font, s, x, y, !on ? 0x60E8D9B8 : hover ? 0xFFFFE08A : 0xFFE8D9B8, true);
        if (on) {
            hits.add(new Hit(x - 4, y - 2, x + font.width(s) + 4, y + 10, r));
        }
    }

    // ------------------------------------------------------------------ Eingabe

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (zoom != null) {
            zoom = null;
            return true;
        }
        for (Hit h : List.copyOf(hits)) {
            if (h.in(e.x(), e.y())) {
                h.action().run();
                return true;
            }
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0 && zoom == null) {
            goTo(2 * spread + (sy < 0 ? 2 : -2));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        switch (e.key()) {
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_PAGE_DOWN, GLFW.GLFW_KEY_D -> {
                goTo(2 * spread + 2);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_PAGE_UP, GLFW.GLFW_KEY_A -> {
                goTo(2 * spread - 2);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                goTo(0);
                return true;
            }
            default -> {
                if (zoom != null && e.key() == GLFW.GLFW_KEY_ESCAPE) {
                    zoom = null;
                    return true;
                }
            }
        }
        return super.keyPressed(e);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
