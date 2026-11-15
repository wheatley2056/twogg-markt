package de.twogg.markt.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemeinfreie Weihnachtslieder als Notenfolgen für Notenblock-Klänge. Ton = Halbtöne über Fis3 (0–24, Bereich des
 * Notenblocks), Dauer in Achteln. Notation: {@code "E4:2"} = E4, Viertel; {@code "-:2"} = Pause.
 */
public final class Melodies {
    public record Note(int pitch, int eighths) {}

    public record Song(String name, List<Note> notes) {}

    private Melodies() {}

    // Muss vor SONGS stehen (statische Initialisierung in Reihenfolge)
    private static final String[] NAMES = {"F#3", "G3", "G#3", "A3", "A#3", "B3", "C4", "C#4", "D4", "D#4", "E4", "F4", "F#4",
            "G4", "G#4", "A4", "A#4", "B4", "C5", "C#5", "D5", "D#5", "E5", "F5", "F#5"};

    public static final List<Song> SONGS = List.of(
            song("Jingle Bells",
                    "E4:2 E4:2 E4:4 E4:2 E4:2 E4:4 E4:2 G4:2 C4:3 D4:1 E4:8 "
                            + "F4:2 F4:2 F4:3 F4:1 F4:2 E4:2 E4:2 E4:1 E4:1 E4:2 D4:2 D4:2 E4:2 D4:4 G4:4 "
                            + "E4:2 E4:2 E4:4 E4:2 E4:2 E4:4 E4:2 G4:2 C4:3 D4:1 E4:8 "
                            + "F4:2 F4:2 F4:3 F4:1 F4:2 E4:2 E4:2 E4:1 E4:1 G4:2 G4:2 F4:2 D4:2 C4:8"),
            song("O Tannenbaum",
                    "D4:2 G4:3 G4:1 G4:4 A4:2 B4:3 B4:1 B4:4 B4:2 A4:2 B4:2 C5:4 F#4:4 A4:2 G4:4 -:2 "
                            + "D5:2 D5:2 B4:2 E5:4 D5:2 D5:2 C5:2 C5:4 C5:2 C5:2 A4:2 D5:4 C5:2 C5:2 B4:2 B4:4 "
                            + "D4:2 G4:3 G4:1 G4:4 A4:2 B4:3 B4:1 B4:4 B4:2 A4:2 B4:2 C5:4 F#4:4 A4:2 G4:6"),
            song("Stille Nacht",
                    "G4:3 A4:1 G4:2 E4:6 G4:3 A4:1 G4:2 E4:6 D5:4 D5:2 B4:6 C5:4 C5:2 G4:6 "
                            + "A4:4 A4:2 C5:3 B4:1 A4:2 G4:3 A4:1 G4:2 E4:6 A4:4 A4:2 C5:3 B4:1 A4:2 G4:3 A4:1 G4:2 E4:6 "
                            + "D5:4 D5:2 F5:3 D5:1 B4:2 C5:6 E5:6 C5:3 G4:1 E4:2 G4:3 F4:1 D4:2 C4:12"));

    public static int pitch(String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return i;
        throw new IllegalArgumentException("Ton außerhalb des Notenblocks: " + name);
    }

    static Song song(String name, String notes) {
        List<Note> out = new ArrayList<>();
        for (String tok : notes.trim().split("\\s+")) {
            String[] p = tok.split(":");
            int len = Integer.parseInt(p[1]);
            out.add(new Note(p[0].equals("-") ? -1 : pitch(p[0]), len));
        }
        return new Song(name, List.copyOf(out));
    }

    /** Tonhöhe für den Klang: 2^((ton − 12) / 12), wie beim Notenblock. */
    public static float soundPitch(int pitch) {
        return (float) Math.pow(2.0, (pitch - 12) / 12.0);
    }
}
