package de.twogg.markt.core;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MarketMathTest {
    private static final long DAY = 1440 * MarketMath.MINUTE;

    @Test
    void euroPricesFollowRate() {
        assertEquals(15, MarketMath.priceTenths(15, 10));      // 15 € bei 10 €/Coin = 1,5
        assertEquals(17, MarketMath.priceTenths(15, 9));       // 1,666… → aufgerundet 1,7
        assertEquals(1, MarketMath.priceTenths(0.01, 10));     // mindestens 0,1
        assertEquals(0, MarketMath.priceTenths(0, 10));        // kostenlos bleibt kostenlos
        assertEquals(400, MarketMath.priceTenths(400, 10));
        assertEquals(20, MarketMath.priceTenths(20, 10), "keine Rundungsfalle bei glatten Werten");
    }

    @Test
    void rentExtendsFromEndOrNow() {
        long now = 1_000_000;
        assertEquals(now + 2 * DAY, MarketMath.extend(0, now, 2, DAY));
        assertEquals(now + 5 * DAY, MarketMath.extend(now + 3 * DAY, now, 2, DAY), "Verlängern hängt hinten an");
        assertEquals(now + DAY, MarketMath.extend(now - 10, now, 1, DAY), "abgelaufen: ab jetzt");
    }

    @Test
    void bookableCountsStartedPeriods() {
        long now = 0;
        assertEquals(7, MarketMath.bookable(0, now, 7, DAY));
        assertEquals(6, MarketMath.bookable(now + DAY, now, 7, DAY));
        assertEquals(5, MarketMath.bookable(now + DAY + 1, now, 7, DAY), "angefangener Tag zählt");
        assertEquals(0, MarketMath.bookable(now + 9 * DAY, now, 7, DAY));
    }

    @Test
    void refundOnlyForUnstartedPeriods() {
        long now = 0;
        assertEquals(2, MarketMath.unusedPeriods(now + 2 * DAY + 500, now, DAY));
        assertEquals(0, MarketMath.unusedPeriods(now + DAY - 1, now, DAY));
        assertEquals(0, MarketMath.unusedPeriods(now - 1, now, DAY));
        assertEquals(15, MarketMath.percent(30, 50));
        assertEquals(7, MarketMath.percent(15, 50), "abgerundet");
        assertEquals(0, MarketMath.percent(15, 0));
    }

    @Test
    void feeRoundsUpAndNeverExceedsAmount() {
        assertEquals(1, MarketMath.fee(10, 5));     // 0,05 → 0,1
        assertEquals(5, MarketMath.fee(100, 5));
        assertEquals(0, MarketMath.fee(100, 0));
        assertEquals(1, MarketMath.fee(1, 100));
        assertEquals(3, MarketMath.fee(3, 100));
    }

    @Test
    void buyerPriceFallsWithSupplyAndRecovers() {
        assertEquals(10, MarketMath.buyerPrice(1.0, 0, 10, 0.3));
        assertEquals(6, MarketMath.buyerPrice(1.0, 6, 10, 0.3), "linear: 1 − 0,7 × 0,6 = 0,58 → 5,8 → 6");
        assertEquals(2, MarketMath.buyerPrice(0.2, 1, 64, 0.3), "kleine Preise fallen nicht sofort auf die Hälfte");
        assertEquals(3, MarketMath.buyerPrice(1.0, 10, 10, 0.3));
        assertEquals(3, MarketMath.buyerPrice(1.0, 999, 10, 0.3), "nicht unter minFactor");
        assertEquals(0, MarketMath.buyerPrice(0.1, 10, 10, 0.3), "0,03 → 0: dann kauft der Markt nicht mehr");
        double sold = MarketMath.decay(10, 10, 720 * MarketMath.MINUTE, 1440);
        assertEquals(5, sold, 1e-9, "nach halber Erholungszeit die Hälfte");
        assertEquals(0, MarketMath.decay(10, 10, 3 * DAY, 1440));
    }

    @Test
    void pickDrawsDistinctTemplates() {
        List<MarktConfig.JobTemplate> pool = MarktConfig.winterlandJobPool();
        List<MarktConfig.JobTemplate> got = MarketMath.pick(pool, 4, new Random(42));
        assertEquals(4, got.size());
        Set<String> titles = new HashSet<>();
        for (MarktConfig.JobTemplate t : got) titles.add(t.title);
        assertEquals(4, titles.size());
        assertEquals(pool.size(), MarketMath.pick(pool, 999, new Random(1)).size());
    }

    @Test
    void formatAndSlug() {
        assertEquals("12,5", MarketMath.coins(125));
        assertEquals("3", MarketMath.coins(30));
        assertEquals("-0,4", MarketMath.coins(-4));
        assertEquals("gluehweinbude_am_tor", MarketMath.slug("Glühweinbude am Tor!"));
        assertEquals("2 T 3 Std", MarketMath.duration(2 * DAY + 3 * 60 * MarketMath.MINUTE + 5));
        assertEquals("abgelaufen", MarketMath.duration(0));
    }

    @Test
    void boxContainsAndIntersects() {
        Box a = Box.of("minecraft:overworld", 10, 64, 10, 0, 70, 0);
        assertEquals(0, a.minX);
        assertEquals(11 * 7 * 11, a.volume());
        assertTrue(a.contains("minecraft:overworld", 10, 70, 10));
        assertFalse(a.contains("minecraft:overworld", 11, 70, 10));
        assertFalse(a.contains("minecraft:the_nether", 5, 65, 5));
        assertTrue(a.intersects(Box.of("minecraft:overworld", 10, 70, 10, 20, 80, 20)), "Ecke berührt");
        assertFalse(a.intersects(Box.of("minecraft:overworld", 11, 64, 0, 20, 70, 10)));
        assertFalse(a.intersects(Box.of("minecraft:the_nether", 0, 64, 0, 10, 70, 10)));
    }

    @Test
    void plotStatusAndMembers() {
        Plot p = new Plot();
        long now = 1000;
        assertEquals(Plot.Status.FREE, p.status(now));
        p.owner = "u1";
        p.rentedUntil = now + 10;
        p.helpers.put("u2", "Helfer");
        assertEquals(Plot.Status.RENTED, p.status(now));
        assertTrue(p.member("u2", now));
        assertTrue(p.isOwner("u1", now));
        assertFalse(p.isOwner("u2", now));
        assertEquals(Plot.Status.FREE, p.status(now + 10), "abgelaufen = frei, auch vor dem Aufräumen");
        assertFalse(p.member("u1", now + 10), "abgelaufen: keine Rechte mehr");
        p.bought = true;
        assertEquals(Plot.Status.OWNED, p.status(now + 999_999));
        p.mode = Plot.Mode.LOCKED;
        p.release();
        assertEquals(Plot.Status.LOCKED, p.status(now));
        assertTrue(p.helpers.isEmpty());
    }

    @Test
    void configSanitizeRepairs() {
        Gson gson = new Gson();
        MarktConfig c = gson.fromJson("""
                {"stalls": {"periodMinutes": 0, "buybackPercent": 250},
                 "shop": {"feePercent": -3, "maxOffers": 999},
                 "buyer": {"items": [{"item": ""}, {"item": "minecraft:coal", "bundle": 0, "price": -1}]},
                 "jobs": {"pool": [null, {"item": "minecraft:apple", "count": 0, "reward": 0}]},
                 "snow": null}""", MarktConfig.class);
        c.sanitize();
        assertEquals(1, c.stalls.periodMinutes);
        assertEquals(100, c.stalls.buybackPercent);
        assertEquals(0, c.shop.feePercent);
        assertEquals(48, c.shop.maxOffers);
        assertEquals(1, c.buyer.items.size());
        assertEquals(1, c.buyer.items.getFirst().bundle);
        assertEquals(0, c.buyer.items.getFirst().price);
        assertEquals(1, c.jobs.pool.size());
        assertEquals("Lieferung", c.jobs.pool.getFirst().title);
        assertNotNull(c.snow);
        assertEquals("Weihnachtsmarkt", c.title);
    }

    @Test
    void defaultsAreSane() {
        MarktConfig c = MarktConfig.defaults();
        c.sanitize();
        assertFalse(c.buyer.items.isEmpty());
        assertTrue(c.jobs.pool.isEmpty(), "seit 0.6.0 ohne Beispielaufträge");
        assertFalse(c.lager.items.isEmpty());
        for (MarktConfig.BuyerItem i : c.buyer.items) assertTrue(i.item.startsWith("minecraft:"), i.item);
    }

    @Test
    void dataSanitizeFillsIds() {
        MarktData d = new Gson().fromJson("""
                {"plots": {"bude1": {"name": "", "box": {"dim": "minecraft:overworld", "minX": 0, "maxX": 3}}, "kaputt": null},
                 "jobs": [{"item": ""}, {"item": "minecraft:apple"}]}""", MarktData.class);
        d.sanitize();
        assertEquals(1, d.plots.size());
        Plot p = d.plots.get("bude1");
        assertEquals("bude1", p.id);
        assertEquals("bude1", p.name);
        assertNotNull(p.helpers);
        assertEquals(1, d.jobs.size());
        d.addPending("u", "Name", 15, "Kasse");
        d.addPending("u", "", 5, "Erstattung");
        assertEquals(20, d.pending.get("u").amount);
        assertEquals("Name", d.pending.get("u").name);
    }

    @Test
    void thekeLehntVerteuertesOderVerkleinertesAngebotAb() {
        // seit 0.5.2: angezeigt 16 Stück für 0,1 – inzwischen 1 Stück für 500
        assertTrue(MarketMath.offerWorse(1, 16, 5000, 1));
        assertTrue(MarketMath.offerWorse(1, 16, 2, 16));
        assertTrue(MarketMath.offerWorse(1, 16, 1, 8));
        assertFalse(MarketMath.offerWorse(1, 16, 1, 16));
        assertFalse(MarketMath.offerWorse(5, 16, 3, 32), "billiger oder mehr ist in Ordnung");
        assertFalse(MarketMath.offerWorse(0, 0, 5000, 1), "ältere Clients schicken nichts mit");
    }
}
