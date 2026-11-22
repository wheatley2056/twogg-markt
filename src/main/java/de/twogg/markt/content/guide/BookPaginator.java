package de.twogg.markt.content.guide;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Setzt das Markt-Handbuch auf Buchseiten (ohne Minecraft): ein beschriebenes Buch zeigt 14 Zeilen zu 114 Pixeln. Die
 * Zeichenbreiten folgen der Standardschrift (Vorschub inklusive 1 px Abstand), fett ist 1 px breiter. Umbrochen wird an
 * Leerzeichen, zu lange Wörter werden geteilt.
 *
 * <p>Eingabe ist der Handbuchtext mit einfacher Auszeichnung: {@code # Kapitel} (neue Seite, im Inhaltsverzeichnis),
 * {@code ## Abschnitt} (fett, rutscht mit dem nächsten Absatz auf die nächste Seite, wenn unten kein Platz ist),
 * {@code - Punkt} (Aufzählung), Leerzeile = Absatz, sonst Fließtext.
 */
public final class BookPaginator {

    public static final int WIDTH = 114;
    public static final int LINES = 14;

    /** Art einer Zeile – bestimmt die Formatierung im Buch. */
    public enum Kind { CHAPTER, SECTION, BULLET, TEXT, BLANK }

    public record Line(Kind kind, String text) {
    }

    public record Page(List<Line> lines) {
    }

    /** Ergebnis: Seiten und die Startseite (0-basiert innerhalb dieser Seiten) je Kapitel. */
    public record Book(List<Page> pages, Map<String, Integer> chapters) {
    }

    private BookPaginator() {
    }

    // ------------------------------------------------------------------ Breiten

    /** Pixelbreite eines Zeichens in der Standardschrift (Vorschub). */
    public static int width(char c, boolean bold) {
        int w = switch (c) {
            case 'i', '.', ',', ':', ';', '!', '\'', '|' -> 2;
            case 'l', '`' -> 3;
            case 't', 'I', ' ', '(', ')', '[', ']', '{', '}', '*', '"' -> 4;
            case 'f', 'k', '<', '>' -> 5;
            case '@', '~' -> 7;
            case '•', '★', '❄', '€', '–', '—', '…' -> 8;
            default -> 6;
        };
        return w + (bold && c != ' ' ? 1 : 0);
    }

    public static int width(String s, boolean bold) {
        int w = 0;
        for (int i = 0; i < s.length(); i++) {
            w += width(s.charAt(i), bold);
        }
        return w;
    }

    /** Umbruch auf {@code max} Pixel; Folgezeilen beginnen mit {@code indent}. */
    public static List<String> wrap(String text, boolean bold, int max, String indent) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (width(candidate, bold) <= max) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                out.add(line.toString());
                line.setLength(0);
                line.append(indent);
            }
            // Wort passt auch allein nicht: Zeichen für Zeichen teilen
            String rest = word;
            while (width(line + rest, bold) > max) {
                int n = 0;
                while (n < rest.length() && width(line + rest.substring(0, n + 1), bold) <= max) {
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

    // ------------------------------------------------------------------ Seiten

    public static Book paginate(String source) {
        List<Page> pages = new ArrayList<>();
        Map<String, Integer> chapters = new LinkedHashMap<>();
        List<Line> cur = new ArrayList<>();
        List<String> para = new ArrayList<>();
        String[] src = source.replace("\r", "").split("\n", -1);
        for (int i = 0; i < src.length; i++) {
            String raw = src[i];
            String t = raw.strip();
            boolean special = t.isEmpty() || t.startsWith("#") || t.startsWith("- ");
            if (!special) {
                para.add(t);
                continue;
            }
            cur = flushParagraph(pages, cur, para);
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("## ")) {
                List<String> head = wrap(t.substring(3), true, WIDTH, "");
                // Überschrift nicht allein unten auf der Seite: mit Leerzeile + mind. zwei Zeilen danach
                int need = head.size() + 2 + (cur.isEmpty() ? 0 : 1);
                if (cur.size() + need > LINES) {
                    cur = newPage(pages, cur);
                }
                if (!cur.isEmpty()) {
                    cur.add(new Line(Kind.BLANK, ""));
                }
                for (String h : head) {
                    cur.add(new Line(Kind.SECTION, h));
                }
            } else if (t.startsWith("# ")) {
                if (!cur.isEmpty()) {
                    cur = newPage(pages, cur);
                }
                String title = t.substring(2).strip();
                chapters.put(title, pages.size());
                for (String h : wrap(title, true, WIDTH, "")) {
                    cur.add(new Line(Kind.CHAPTER, h));
                }
                cur.add(new Line(Kind.BLANK, ""));
            } else {
                List<String> lines = wrap("• " + t.substring(2).strip(), false, WIDTH, "   ");
                cur = add(pages, cur, Kind.BULLET, lines);
            }
        }
        cur = flushParagraph(pages, cur, para);
        if (!cur.isEmpty()) {
            pages.add(new Page(trimBlank(cur)));
        }
        return new Book(pages, chapters);
    }

    private static List<Line> flushParagraph(List<Page> pages, List<Line> cur, List<String> para) {
        if (para.isEmpty()) {
            return cur;
        }
        List<String> lines = wrap(String.join(" ", para), false, WIDTH, "");
        para.clear();
        return add(pages, cur, Kind.TEXT, lines);
    }

    private static List<Line> add(List<Page> pages, List<Line> cur, Kind kind, List<String> lines) {
        for (String l : lines) {
            if (cur.size() >= LINES) {
                cur = newPage(pages, cur);
            }
            cur.add(new Line(kind, l));
        }
        return cur;
    }

    private static List<Line> newPage(List<Page> pages, List<Line> cur) {
        pages.add(new Page(trimBlank(cur)));
        return new ArrayList<>();
    }

    private static List<Line> trimBlank(List<Line> lines) {
        List<Line> out = new ArrayList<>(lines);
        while (!out.isEmpty() && out.get(out.size() - 1).kind() == Kind.BLANK) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    /** Platzhalter {@code {name}} ersetzen; unbekannte bleiben stehen. */
    public static String fill(String text, Map<String, String> values) {
        String out = text;
        for (Map.Entry<String, String> e : values.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue());
        }
        return out;
    }
}
