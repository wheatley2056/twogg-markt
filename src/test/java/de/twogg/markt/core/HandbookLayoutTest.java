package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.twogg.markt.content.guide.GuideBook;
import de.twogg.markt.content.guide.HandbookLayout;
import de.twogg.markt.content.guide.HandbookLayout.Item;
import de.twogg.markt.content.guide.HandbookLayout.Kind;
import de.twogg.markt.content.guide.HandbookLayout.Page;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class HandbookLayoutTest {

    /** Ungefähr die Standardschrift: 6 px je Zeichen, schmale Zeichen weniger, fett +1. */
    static final HandbookLayout.Measure FONT = (s, bold) -> {
        int w = 0;
        for (char c : s.toCharArray()) {
            w += switch (c) {
                case 'i', '.', ',', ':', ';', '!', '\'', '|' -> 2;
                case 'l' -> 3;
                case 't', 'I', ' ' -> 4;
                case 'f', 'k' -> 5;
                default -> 6;
            } + (bold ? 1 : 0);
        }
        return w;
    };
    static final int W = 140, H = 170;

    private static String handbook() throws IOException {
        try (InputStream in = HandbookLayoutTest.class.getResourceAsStream("/assets/twogg_markt/handbuch/de_de.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void chaptersSectionsAndImages() {
        HandbookLayout.Book b = HandbookLayout.layout("# Eins\nText.\n![bild] Unterschrift\n# Zwei\n## Abschnitt\n- Punkt", FONT, W, H);
        assertEquals(0, b.chapters().get("Eins"));
        assertEquals(1, b.chapters().get("Zwei"));
        List<Item> p0 = b.pages().get(0).items();
        assertTrue(p0.stream().anyMatch(i -> i.kind() == Kind.IMAGE && i.text().equals("bild") && i.height() == HandbookLayout.imageHeight(W)));
        assertTrue(p0.stream().anyMatch(i -> i.kind() == Kind.CAPTION && i.text().equals("Unterschrift")));
        assertTrue(b.pages().get(1).items().stream().anyMatch(i -> i.kind() == Kind.BULLET && i.text().equals("• Punkt")));
    }

    @Test
    void wholeHandbookFits() throws IOException {
        String src = GuideBook.fill(handbook(), GuideBook.values(new MarktConfig()));
        HandbookLayout.Book b = HandbookLayout.layout(src, FONT, W, H);
        assertTrue(b.chapters().size() >= 6, "Kapitel: " + b.chapters().keySet());
        for (Page p : b.pages()) {
            assertFalse(p.items().isEmpty());
            for (Item i : p.items()) {
                assertTrue(i.y() >= 0 && i.y() + i.height() <= H, "ragt über die Seite: " + i);
                if (i.kind() != Kind.IMAGE) {
                    boolean bold = i.kind() == Kind.CHAPTER || i.kind() == Kind.SECTION;
                    assertTrue(FONT.width(i.text(), bold) <= W, "zu breit: " + i.text());
                }
            }
            Item last = p.items().get(p.items().size() - 1);
            assertFalse(last.kind() == Kind.SECTION, "Überschrift allein unten: " + last);
        }
    }

    @Test
    void everyImageExistsAndPlaceholdersAreKnown() throws IOException {
        String src = handbook();
        Matcher img = Pattern.compile("(?m)^!\\[([a-z_]+)]").matcher(src);
        int n = 0;
        while (img.find()) {
            n++;
            Path p = Path.of("src/main/resources/assets/twogg_markt/textures/gui/handbuch/" + img.group(1) + ".png");
            assertTrue(Files.isRegularFile(p), "Bild fehlt: " + p);
        }
        assertTrue(n >= 10, "Bilder im Handbuch: " + n);
        assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/twogg_markt/textures/gui/handbuch/buch.png")));
        var known = GuideBook.values(new MarktConfig()).keySet();
        Matcher m = Pattern.compile("\\{([a-zA-Z]+)}").matcher(src);
        while (m.find()) {
            assertTrue(known.contains(m.group(1)), "unbekannter Platzhalter {" + m.group(1) + "}");
        }
        String filled = GuideBook.fill(src, GuideBook.values(new MarktConfig()));
        assertTrue(filled.contains("5 % Marktgebühr"));
        assertTrue(filled.contains("Anteile 50 / 30 / 20 %"));
    }
}
