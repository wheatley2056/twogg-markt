package de.twogg.markt.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventTest {
    // 2026-12-04 ist ein Freitag
    private static final LocalDateTime FR_19 = LocalDateTime.of(2026, 12, 4, 19, 0);

    @Test
    void scheduleWeekdays() {
        Schedule s = new Schedule(List.of("Fr 18:00-21:00", "Sa,So 16:00-20:00"));
        assertTrue(s.errors().isEmpty());
        Schedule.Window w = s.active(FR_19);
        assertNotNull(w);
        assertEquals(LocalDateTime.of(2026, 12, 4, 21, 0), w.end());
        assertNull(s.active(FR_19.withHour(17)));
        assertNull(s.active(FR_19.withHour(21)), "Ende ist ausschließlich");
        assertEquals(LocalDateTime.of(2026, 12, 5, 16, 0), s.next(FR_19).start(), "nächstes: Samstag");
        assertNotNull(s.active(LocalDateTime.of(2026, 12, 6, 16, 30)), "Sonntag");
    }

    @Test
    void scheduleOverMidnightDateAndDaily() {
        Schedule s = new Schedule(List.of("Sa 22:00-01:00"));
        assertNotNull(s.active(LocalDateTime.of(2026, 12, 6, 0, 30)), "Sonntag 0:30 gehört zu Samstag");
        assertNull(s.active(LocalDateTime.of(2026, 12, 6, 1, 0)));
        Schedule d = new Schedule(List.of("24.12. 16:00-19:00"));
        assertNotNull(d.active(LocalDateTime.of(2026, 12, 24, 17, 0)));
        assertNull(d.active(LocalDateTime.of(2026, 12, 23, 17, 0)));
        Schedule t = new Schedule(List.of("täglich 9:00-10:00", "Mo-Mi 12:00-13:00"));
        assertTrue(t.errors().isEmpty());
        assertNotNull(t.active(LocalDateTime.of(2026, 12, 2, 9, 30)));
        assertNotNull(t.active(LocalDateTime.of(2026, 12, 2, 12, 30)), "Mittwoch liegt in Mo-Mi");
        assertNull(t.active(LocalDateTime.of(2026, 12, 3, 12, 30)), "Donnerstag nicht");
    }

    @Test
    void scheduleKeysDifferPerDayAndErrorsCollected() {
        Schedule s = new Schedule(List.of("täglich 18:00-20:00", "Quatsch", "Fr 25:00-26:00"));
        assertEquals(2, s.errors().size());
        assertNotEquals(s.active(FR_19).key(), s.active(FR_19.plusDays(1)).key());
    }

    @Test
    void lotteryDrawsDistinctWinnersWeighted() {
        Map<String, Integer> t = new HashMap<>(Map.of("a", 1, "b", 1, "c", 98));
        int cFirst = 0;
        for (int i = 0; i < 200; i++) {
            List<String> w = MarketMath.drawLottery(t, 3, new Random(i));
            assertEquals(3, new HashSet<>(w).size());
            if (w.getFirst().equals("c")) cFirst++;
        }
        assertTrue(cFirst > 170, "viele Lose = gute Chancen: " + cFirst);
        assertEquals(1, MarketMath.drawLottery(Map.of("x", 2), 3, new Random(1)).size());
        assertTrue(MarketMath.drawLottery(Map.of(), 3, new Random(1)).isEmpty());
    }

    @Test
    void wichtelNobodyGetsOwn() {
        for (int n = 2; n < 12; n++) {
            for (int seed = 0; seed < 30; seed++) {
                int[] to = MarketMath.wichtel(n, new Random(seed));
                Set<Integer> seen = new HashSet<>();
                for (int i = 0; i < n; i++) {
                    assertNotEquals(i, to[i]);
                    assertTrue(seen.add(to[i]), "jeder bekommt genau ein Geschenk");
                }
            }
        }
        assertEquals(0, MarketMath.wichtel(1, new Random(1))[0], "allein: eigenes zurück");
    }

    @Test
    void topSortsAndSkipsZero() {
        var top = MarketMath.top(Map.of("a", 5L, "b", 9L, "c", 0L, "d", 5L), 3);
        assertEquals(List.of("b", "a", "d"), top.stream().map(Map.Entry::getKey).toList());
    }

    @Test
    void melodiesInNoteBlockRange() {
        assertEquals(3, Melodies.SONGS.size());
        for (Melodies.Song s : Melodies.SONGS) {
            assertTrue(s.notes().size() > 20, s.name());
            for (Melodies.Note n : s.notes()) assertTrue(n.pitch() >= -1 && n.pitch() <= 24);
        }
        assertEquals(1.0f, Melodies.soundPitch(12), 1e-6);
        assertEquals(2.0f, Melodies.soundPitch(24), 1e-6);
    }

    @Test
    void eventResetAndVotes() {
        MarktEvent e = new MarktEvent();
        e.votes.put("u1", "bude");
        e.votes.put("u2", "bude");
        e.votes.put("u3", "kiosk");
        assertEquals(2L, e.voteCount().get("bude"));
        e.stats("u1", "Anna").sales += 30;
        e.pot = 50;
        e.reset();
        assertTrue(e.votes.isEmpty() && e.stats.isEmpty() && e.pot == 0);
        assertFalse(e.running());
    }

    @Test
    void adventTemplateHas24Days() {
        MarktConfig c = MarktConfig.defaults();
        c.sanitize();
        for (int d = 1; d <= 24; d++) assertNotNull(c.advent(d), "Tag " + d);
        assertNull(c.advent(25));
    }
}
