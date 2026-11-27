package de.twogg.markt.content.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seitenaufteilung des Markt-Handbuchs (seit 0.3.0, eigenes Buch mit Bildern) – ohne Minecraft, die Schriftbreite kommt
 * von außen ({@link Measure}, im Client die echte Schrift). Seiten sind {@code width} × {@code height} Pixel groß, eine
 * Zeile ist {@link #LINE} hoch.
 *
 * <p>Auszeichnung im Text: {@code # Kapitel} (neue Seite, Inhaltsverzeichnis, Lesezeichen), {@code ## Abschnitt},
 * {@code - Punkt}, {@code ![bild] Bildunterschrift} (Bild im Seitenformat 16:9 aus
 * {@code textures/gui/handbuch/<bild>.png}), Leerzeile = Absatz, sonst Fließtext.
 */
public final class HandbookLayout {

    public static final int LINE = 10;
    /** Seitenverhältnis der Bilder (Breite : Höhe). */
    public static final double IMAGE_RATIO = 16.0 / 9.0;

    public interface Measure {
        int width(String text, boolean bold);
    }

    public enum Kind { CHAPTER, SECTION, BULLET, TEXT, IMAGE, CAPTION }

    /** Ein Element auf einer Seite: Art, Text (bei IMAGE der Bildname), y-Position und Höhe in Pixeln. */
    public record Item(Kind kind, String text, int y, int height) {
    }

    public record Page(List<Item> items, String chapter) {
    }

    public record Book(List<Page> pages, Map<String, Integer> chapters) {
    }

    private final Measure measure;
    private final int width;
    private final int height;
    private final List<Page> pages = new ArrayList<>();
    private final Map<String, Integer> chapters = new LinkedHashMap<>();
    private List<Item> cur = new ArrayList<>();
    private int y;
    private String chapter = "";

    private HandbookLayout(Measure measure, int width, int height) {
        this.measure = measure;
        this.width = width;
        this.height = height;
    }

    /** Text auf Seiten verteilen. */
    public static Book layout(String source, Measure measure, int width, int height) {
        HandbookLayout l = new HandbookLayout(measure, width, height);
        l.run(source);
        return new Book(List.copyOf(l.pages), Map.copyOf(l.chapters));
    }

    public static int imageHeight(int width) {
        return (int) Math.round(width / IMAGE_RATIO);
    }

    private void run(String source) {
        List<String> para = new ArrayList<>();
        for (String raw : source.replace("\r", "").split("\n", -1)) {
            String t = raw.strip();
            boolean special = t.isEmpty() || t.startsWith("#") || t.startsWith("- ") || t.startsWith("![");
            if (!special) {
                para.add(t);
                continue;
            }
            flush(para);
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("## ")) {
                List<String> head = wrap(t.substring(3), true, width, "");
                gap(4);
                // Überschrift nie allein unten: Platz für sie und zwei Zeilen
                if (y + (head.size() + 2) * LINE > height) {
                    newPage();
                }
                for (String h : head) {
                    put(Kind.SECTION, h, LINE);
                }
                y += 1;
            } else if (t.startsWith("# ")) {
                if (!cur.isEmpty()) {
                    newPage();
                }
                chapter = t.substring(2).strip();
                chapters.put(chapter, pages.size());
                for (String h : wrap(chapter, true, width, "")) {
                    put(Kind.CHAPTER, h, LINE + 2);
                }
                y += 6;
            } else if (t.startsWith("![")) {
                int close = t.indexOf(']');
                String name = close > 2 ? t.substring(2, close).strip() : "";
                String caption = close > 0 ? t.substring(close + 1).strip() : "";
                int ih = imageHeight(width);
                List<String> cap = caption.isEmpty() ? List.of() : wrap(caption, false, width, "");
                int need = ih + 3 + cap.size() * LINE;
                gap(3);
                if (y + need > height && !cur.isEmpty()) {
                    newPage();
                }
                put(Kind.IMAGE, name, ih);
                y += 2;
                for (String c : cap) {
                    put(Kind.CAPTION, c, LINE);
                }
                y += 3;
            } else {
                List<String> lines = wrap("• " + t.substring(2).strip(), false, width, "   ");
                for (String l : lines) {
                    lineOf(Kind.BULLET, l);
                }
            }
        }
        flush(para);
        if (!cur.isEmpty()) {
            pages.add(new Page(List.copyOf(cur), chapter));
        }
    }

    private void flush(List<String> para) {
        if (para.isEmpty()) {
            return;
        }
        gap(3);
        for (String l : wrap(String.join(" ", para), false, width, "")) {
            lineOf(Kind.TEXT, l);
        }
        para.clear();
    }

    private void gap(int px) {
        if (!cur.isEmpty()) {
            y += px;
        }
    }

    private void lineOf(Kind k, String text) {
        if (y + LINE > height) {
            newPage();
        }
        put(k, text, LINE);
    }

    private void put(Kind k, String text, int h) {
        cur.add(new Item(k, text, y, h));
        y += h;
    }

    private void newPage() {
        pages.add(new Page(List.copyOf(cur), chapter));
        cur = new ArrayList<>();
        y = 0;
    }

    /** Umbruch an Leerzeichen; zu lange Wörter werden geteilt; Folgezeilen beginnen mit {@code indent}. */
    public List<String> wrap(String text, boolean bold, int max, String indent) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (measure.width(candidate, bold) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0 && !line.toString().equals(indent)) {
                out.add(line.toString());
                line.setLength(0);
                line.append(indent);
            }
            String rest = word;
            while (measure.width(line + rest, bold) > max) {
                int n = 0;
                while (n < rest.length() && measure.width(line + rest.substring(0, n + 1), bold) <= max) {
                    n++;
                }
                if (n == 0) {
                    break;
                }
                out.add(line + rest.substring(0, n));
                line.setLength(0);
                line.append(indent);
                rest = rest.substring(n);
            }
            line.append(rest);
        }
        if (line.length() > 0 && !line.toString().equals(indent)) {
            out.add(line.toString());
        }
        return out;
    }
}
