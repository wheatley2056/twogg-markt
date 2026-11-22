package de.twogg.markt.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.twogg.markt.content.guide.BookPaginator;
import de.twogg.markt.content.guide.BookPaginator.Kind;
import de.twogg.markt.content.guide.BookPaginator.Line;
import de.twogg.markt.content.guide.BookPaginator.Page;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class BookPaginatorTest {

    private static String handbook() throws IOException {
        try (InputStream in = BookPaginatorTest.class.getResourceAsStream("/data/twogg_markt/handbuch/de_de.txt")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void wrapsWithinWidth() {
        List<String> lines = BookPaginator.wrap("Das ist ein recht langer Satz, der sicher auf mehrere Zeilen umbrochen werden muss.", false,
                BookPaginator.WIDTH, "");
        assertTrue(lines.size() >= 2);
        for (String l : lines) {
            assertTrue(BookPaginator.width(l, false) <= BookPaginator.WIDTH, l);
        }
        assertEquals("Das ist ein recht langer Satz, der sicher auf mehrere Zeilen umbrochen werden muss.", String.join(" ", lines));
    }

    @Test
    void splitsOverlongWords() {
        List<String> lines = BookPaginator.wrap("Donaudampfschifffahrtsgesellschaftskapitänsmützenabzeichen", true, BookPaginator.WIDTH, "");
        assertTrue(lines.size() >= 2);
        for (String l : lines) {
            assertTrue(BookPaginator.width(l, true) <= BookPaginator.WIDTH);
        }
    }

    @Test
    void chaptersStartNewPagesAndPagesFit() {
        BookPaginator.Book b = BookPaginator.paginate("# Eins\nText eins.\n# Zwei\n- Punkt a\n- Punkt b\n## Abschnitt\nMehr Text.");
        assertEquals(Map.of("Eins", 0, "Zwei", 1), b.chapters());
        assertEquals(2, b.pages().size());
        assertEquals(Kind.CHAPTER, b.pages().get(1).lines().get(0).kind());
        assertTrue(b.pages().get(1).lines().stream().anyMatch(l -> l.kind() == Kind.BULLET && l.text().startsWith("• Punkt a")));
    }

    @Test
    void wholeHandbookFitsTheBook() throws IOException {
        BookPaginator.Book b = BookPaginator.paginate(handbook());
        assertTrue(b.chapters().size() >= 6, "Kapitel: " + b.chapters().keySet());
        assertTrue(b.pages().size() + 2 <= 100, "höchstens 100 Seiten in einem Buch");
        for (Page p : b.pages()) {
            assertTrue(p.lines().size() <= BookPaginator.LINES, "zu viele Zeilen: " + p.lines());
            assertFalse(p.lines().isEmpty());
            assertFalse(p.lines().get(0).kind() == Kind.BLANK, "Seite beginnt mit Leerzeile");
            // Abschnittsüberschrift nie als letzte Zeile einer Seite
            assertFalse(p.lines().get(p.lines().size() - 1).kind() == Kind.SECTION, "Überschrift allein unten: " + p.lines());
            for (Line l : p.lines()) {
                boolean bold = l.kind() == Kind.CHAPTER || l.kind() == Kind.SECTION;
                assertTrue(BookPaginator.width(l.text(), bold) <= BookPaginator.WIDTH, "zu breit: " + l.text());
            }
        }
    }

    @Test
    void placeholdersAreAllKnown() throws IOException {
        Matcher m = Pattern.compile("\\{([a-zA-Z]+)}").matcher(handbook());
        java.util.Set<String> known = de.twogg.markt.content.guide.GuideBook.values(new MarktConfig()).keySet();
        while (m.find()) {
            assertTrue(known.contains(m.group(1)), "unbekannter Platzhalter {" + m.group(1) + "}");
        }
        String filled = BookPaginator.fill(handbook(), de.twogg.markt.content.guide.GuideBook.values(new MarktConfig()));
        assertTrue(filled.contains("5 % Marktgebühr"), "Gebühr eingesetzt");
        assertTrue(filled.contains("Anteile 50 / 30 / 20 %"));
        assertFalse(filled.matches("(?s).*\\{[a-zA-Z]+}.*"));
    }
}
