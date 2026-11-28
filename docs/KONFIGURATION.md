# Konfiguration: TwoGG Markt

## Speicherorte

```
config/twogg-markt/
├── markt.json                 Einstellungen, Ankauf-Liste, Pool der Marktaufträge, Brandts Lager (Vorlage beim ersten Start)
└── markt.json.vor-0.6.0       Kopie der Datei vor der einmaligen Umstellung auf 0.6.0 (nur auf älteren Servern)

<welt>/twogg-markt/
├── markt.json                 Stände, Räumzonen, Aufträge, Zähler, vorgemerkte Beträge, Abholfächer
├── markt.json.broken-<zeit>   nur, wenn die Datei einmal unlesbar war (siehe unten)
├── markt.json.vor-0.6.0       Kopie vor der einmaligen Umstellung auf 0.6.0 (nur auf älteren Servern)
└── snapshots/<stand-id>.nbt   Leerzustand jedes Stands (Blöcke beim Einzeichnen)
```

Beide `markt.json` werden atomar geschrieben (erst `.tmp`, dann umbenennen). Die Weltdaten werden alle 30 Sekunden
gespeichert, wenn sich etwas geändert hat, außerdem bei jedem Welt-Speichern und beim Stopp.

Ändert ein Admin im Spiel Regeln oder den Ankauf (Marktverwaltung), wird die **ganze** Einstellungsdatei neu
geschrieben. Ist sie beim Laden fehlerhaft, gelten die Standardwerte und die Datei bleibt unangetastet; `/markt reload`
lässt in dem Fall alles beim Alten.

## config/twogg-markt/markt.json

Klasse `de.twogg.markt.core.MarktConfig`. Beim Laden läuft `sanitize()` und repariert offensichtliche Fehler (negative
Werte, leere Listeneinträge, Prozente außerhalb 0–100).

### Oberste Ebene

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `configVersion` | int | `2` | Stand der Datei (seit 0.6.0). Fehlt das Feld (Datei von vor 0.6.0), läuft beim Start einmal die Umstellung, siehe unten. Nicht von Hand ändern. |
| `title` | String | `"Weihnachtsmarkt"` | Fenstertitel der Standübersicht, Untertitel von Ankauf und Auftragsbrett |
| `subtitle` | String | `"Winterland"` | Untertitel der Standübersicht |
| `accent` | `#RRGGBB` | `"#EE5A4F"` | Akzentfarbe aller Markt-Fenster |

### `stalls`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `periodMinutes` | int | `1440` | Länge eines Mietzeitraums in Echtzeit-Minuten |
| `periodName` / `periodNamePlural` | String | `"Tag"` / `"Tage"` | Anzeige des Zeitraums |
| `maxPerPlayer` | int | `1` | Stände pro Spieler gleichzeitig (gemietet + gekauft), 0 = unbegrenzt |
| `maxHelpers` | int | `4` | Helfer pro Stand |
| `buybackPercent` | double | `50` | Rückkauf gekaufter Stände in % des Kaufpreises |
| `rentRefundPercent` | double | `50` | Erstattung beim Kündigen in % der ganzen, nicht angefangenen Zeiträume |
| `resetOnRelease` | boolean | `false` | Stand beim Freiwerden (Ablauf, Kündigung, Räumen, Löschen) auf den Leerzustand setzen. **Kisteninhalte gehen dabei verloren.** |
| `protectFree` | boolean | `true` | Freie und gesperrte Stände sind geschützt (nur Admins bauen) |
| `allowCash` | boolean | `true` | Bezahlen mit Bargeld erlaubt |
| `maxVolume` | int | `24576` | Größter Stand beim Einzeichnen in Blöcken (32 × 32 × 24) |
| `enterHint` | boolean | `true` | Hinweis über der Hotbar beim Betreten eines Stands |
| `holograms` | boolean | `true` | Schwebende Schrift über jedem Stand |
| `hologramOffset` | double | `0.6` | Höhe über der Oberkante des Stands |

### `shop`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `feePercent` | double | `5` | Marktgebühr auf jeden Verkauf, aufgerundet, mindestens 0,1. Wird vernichtet. |
| `maxOffers` | int | `12` | Angebote pro Stand (1–48) |
| `maxPrice` | double | `500` | Höchstpreis pro Kauf in Coins |
| `counterPrice` | double | `20` | Preis einer weiteren Verkaufstheke in € |

### `buyer`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `enabled` | boolean | `true` | Ankauf offen |
| `recoverMinutes` | int | `1440` | So lange dauert es, bis sich der Bedarf eines Items vollständig erholt hat |
| `minFactor` | double | `0.3` | Tiefster Preis (Anteil vom Grundpreis), erreicht beim vollen Tagesbedarf |
| `payCash` | boolean | `false` | Auszahlung bar statt aufs Konto |
| `items` | Liste | 14 Posten | siehe unten |

`items[]`: `item` (Item-ID), `name` (Anzeige, leer = Item-Name), `bundle` (Stück pro Ankauf), `price` (Coins pro
Bündel bei vollem Bedarf), `demand` (Bündel pro Tag zum vollen Preis), `dailyLimit` (Bündel pro Spieler und Tag, 0 =
unbegrenzt).

Preis pro Bündel: `round(price × 10 × (1 − (1 − minFactor) × min(1, verkauft / demand))) / 10`. „Verkauft“ zählt alle
Spieler zusammen und sinkt pro Minute um `demand / recoverMinutes`.

Winterland-Vorlage:

| Item | Bündel | Coins | Bedarf/Tag | Limit/Spieler |
|---|---|---|---|---|
| Fichtenholz | 8 | 0,1 | 64 | 32 |
| Kohle | 8 | 0,1 | 32 | 16 |
| Lebkuchen (Kekse) | 4 | 0,2 | 48 | 24 |
| Brot | 2 | 0,2 | 32 | 16 |
| Kürbiskuchen | 1 | 0,3 | 24 | 12 |
| Süßbeeren | 8 | 0,2 | 48 | 24 |
| Äpfel | 4 | 0,3 | 32 | 16 |
| Kabeljau | 2 | 0,3 | 32 | 16 |
| Lachs | 2 | 0,4 | 24 | 12 |
| Wolle (weiß) | 16 | 0,3 | 8 | 4 |
| Honigflasche | 1 | 0,4 | 16 | 8 |
| Kerzen | 2 | 0,4 | 16 | 8 |
| Eisenbarren | 1 | 0,3 | 32 | 16 |
| Eis | 8 | 0,2 | 32 | 16 |

### `jobs`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `enabled` | boolean | `true` | Auftragsbrett an |
| `active` | int | `4` | Marktaufträge gleichzeitig (0–12) |
| `slots` | int | `3` | So viele Spieler können denselben Marktauftrag erledigen (jeder einmal) |
| `refreshMinutes` | int | `1440` | Neue Marktaufträge nach so vielen Minuten |
| `playerJobs` | boolean | `true` | Spieler dürfen Aufträge aushängen |
| `maxPerPlayer` | int | `3` | Offene Aufträge pro Spieler |
| `playerJobHours` | int | `72` | Laufzeit von Spieleraufträgen |
| `maxReward` | double | `200` | Höchste Belohnung eines Spielerauftrags |
| `pool` | Liste | leer (seit 0.6.0) | `title`, `item`, `count`, `reward` (Coins), `weight` (Ziehgewicht, Standard 1) |

Vorlagen mit unbekannter Item-ID werden beim Ziehen übersprungen. Seit 0.6.0 ist der Pool anfangs **leer**: Das
Auftragsbrett zeigt nur Spieleraufträge, bis Admins Vorlagen eintragen, z. B.
`{"title": "Brennholz für die Glühweinbude", "item": "minecraft:spruce_log", "count": 32, "reward": 2.0, "weight": 1}`.
Die 16 Beispielvorlagen bis 0.5.2 stehen in `MarktConfig.winterlandJobPool()`.

### `snow`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `enabled` | boolean | `true` | Räumdienst an |
| `blocksPerPay` | int | `8` | Bezahlt wird je so viele geräumte Schneeschichten/-blöcke |
| `pay` | double | `0.1` | Lohn pro Paket in Coins |
| `dailyMax` | double | `3.0` | Höchstens so viel pro Spieler und Tag |

### `event`

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `enabled` | boolean | `true` | Automatischer Start nach Öffnungszeiten |
| `schedule` | Liste | leer | Öffnungszeiten: `"Fr 18:00-21:00"`, `"Sa,So 16:00-20:00"`, `"Mo-Mi 12:00-13:00"`, `"täglich 18:00-20:00"`, `"24.12. 16:00-19:00"`. Ende vor Beginn = über Mitternacht. Fehlerhafte Einträge werden ignoriert. Uhrzeit des Servers. |
| `defaultMinutes` | int | `120` | Dauer bei `/markt event start` ohne Angabe |
| `openingSeconds` / `finaleSeconds` | int | `60` / `90` | Länge von Eröffnung und Siegerehrung |
| `onlyDuringEvent` | boolean | `false` | Theken (für Kunden), Ankauf und Auftragsbrett nur im Event |
| `bossbar` | boolean | `true` | Bossbar mit Restzeit |
| `announceMinutes` | Liste | `[15, 5, 1]` | Ankündigungen vor dem Start nach Öffnungszeiten |
| `lottery` | boolean | `true` | Losbude |
| `ticketPrice` | double | `5.0` | Preis eines Loses in € |
| `maxTickets` | int | `10` | Lose pro Spieler und Event |
| `potPayoutPercent` | double | `80` | Anteil des Topfs, der ausgeschüttet wird |
| `lotteryShares` | Liste | `[50, 30, 20]` | Aufteilung auf Platz 1, 2, 3 … in % |
| `giftReward` | double | `0.5` | Coins pro gefundenem Geschenk |
| `wichteln` | boolean | `true` | Wichteln an der Losbude |
| `advent` | boolean | `true` | Adventskalender |
| `adventDays` | Liste | 24 Tage | `day` (1–24), `coins`, `item`, `count`, `text`, seit 0.6.0 `stack` (kompletter Stapel als ItemStack-JSON mit Anzahl und Daten, hat Vorrang vor `item`/`count`). Bearbeiten im Spiel: Marktverwaltung, Seite **Advent**, oder `/markt advent …` |
| `vote` | boolean | `true` | Abstimmung „schönster Stand“ |
| `prizes` | Liste | `[10, 5, 2.5]` | Coins für Platz 1, 2, 3 in jeder Kategorie |
| `atmosPreset` | String | `"draussen_klar"` | TwoGG-Atmos-Preset im Marktgelände; leer = aus |
| `music` | boolean | `true` | Weihnachtslieder im Gelände |
| `musicVolume` | float | `0.6` | Lautstärke |
| `musicPauseSeconds` | int | `40` | Pause zwischen Liedern |
| `musicTicksPerEighth` | int | `4` | Tempo (Ticks pro Achtel) |
| `weatherSnow` | boolean | `true` | Niederschlag während des Events (Schnee in kalten Biomen) |
| `fireworks` | boolean | `true` | Feuerwerk bei der Siegerehrung |

### `guide` (seit 0.3.0)

| Feld | Standard | Bedeutung |
|---|---|---|
| `giveOnJoin` | `true` | Jeder Spieler bekommt beim ersten Betreten des Servers einmal das **Markt-Handbuch** (gemerkt in den Weltdaten unter `guideGiven`). `/markt handbuch` gibt es jederzeit erneut. |

### `lager` – Brandts Lager (seit 0.6.0)

Der Laden `lager.laden` (Standard `materiallager`, geöffnet vom Storyteller-NPC Brandt mit
`markt laden oeffnen materiallager {player}`) führt genau die Waren aus `items` – mit Bestand, ohne unbegrenzten
Vorrat. Beim Start und bei `/markt reload` wird der Laden abgeglichen: Fehlt er, wird er angelegt; Waren aus der Liste
werden angelegt oder geändert (Bestand, Verkaufs- und Ankaufszähler bleiben), Waren, die aus der Liste gestrichen
wurden, verschwinden. Neue Waren starten beim Wochenbestand. Items, die der Server nicht kennt (Mod fehlt, Tippfehler),
werden übersprungen und im Log sowie bei `/markt lager` genannt. Feste Verkaufs- und Ankaufszeilen (`/markt laden
verkauf|ankauf`) sind in diesem Laden gesperrt.

| Feld | Typ | Standard | Bedeutung |
|---|---|---|---|
| `enabled` | boolean | `true` | Abgleich an. `false`: Der Laden bleibt, wie er ist (wird nicht mehr gepflegt). |
| `laden` | String | `"materiallager"` | ID des Ladens in den Weltdaten (a–z, 0–9, _) |
| `name` / `subtitle` | String | `"Materiallager"` / `"Brandts Lager"` | Fenstertitel und Untertitel (dahinter steht automatisch „Nachschub jeden Sonntag 06:00“) |
| `restockDay` / `restockTime` / `zone` | String | `"SUNDAY"` / `"06:00"` / `"Europe/Berlin"` | Wöchentlicher Nachschub. Ortszeit, auch über Sommer-/Winterzeit. War der Server zum Termin aus, kommt der Nachschub einmal beim nächsten Start. |
| `trimSurplus` | boolean | `false` | Nachschub füllt jede Ware bis zum Wochenbestand auf (nicht obendrauf). `false`: Was Spieler darüber hinaus verkauft haben, bleibt liegen. `true`: Überschuss geht mit dem Schiff zurück. |
| `spread` | double | `0.6` | Rückkauf = Verkaufspreis beim gleichen Bestand × `spread` (höchstens 0,95) |
| `capacityFactor` | double | `2.0` | Lagerplatz = Wochenbestand × Faktor, wenn eine Ware keinen eigenen `capacity` hat |
| `items` | Liste | 107 Waren | siehe unten |

`items[]`: `item` (Item-ID), optional `stack` (kompletter Stapel mit Daten, z. B. Verzauberungsbücher:
`{"id": "minecraft:enchanted_book", "count": 1, "components": {"minecraft:stored_enchantments": {"minecraft:mending": 1}}}`
– gehandelt wird dann nur genau dieses Item), `name` (Anzeige, leer = Item-Name), `unit` (Stück pro Kauf/Verkauf),
`price` (Coins für die erste Einheit, wenn der Bestand beim Wochenbestand steht), `weekly` (Wochenbestand in Stück),
`capacity` (Lagerplatz in Stück, 0 = `weekly × capacityFactor`).

**Preis nach Bestand** (wie seit 0.5.0): Beim Wochenbestand gilt `price`. Je weniger da ist, desto teurer (leer etwa
doppelt), je voller durch Verkäufe der Spieler, desto billiger (voller Lagerplatz etwa halb). Jede Einheit einzeln zum
Bestand nach dem Handel, gerundet zugunsten von Brandt – Kaufen und Zurückverkaufen bringt nie Gewinn. Spieler können
alles von der Liste an Brandt verkaufen, bis der Lagerplatz voll ist. Beim Abgleich prüft der Server, dass die
Ankaufstelle (`buyer.items`) je Stück nicht mehr zahlt, als Brandt mindestens verlangt (auch Roherz → Barren); sonst
steht eine „Warnung Geldpumpe“ im Log und bei `/markt lager`.

**Preisgefüge der Vorlage:** Stundenlohn an der Stechuhr 1,5 WLC, eine aktive Woche bringt etwa 30–40 WLC. Massenware
kostet Minuten (Bruchstein 0,4 je Stack), Rohstoffe liegen bei der Ankaufstelle (Rohes Eisen ≥ 0,3 je Stück, weil die
Ankaufstelle 0,3 je Eisenbarren zahlt), Seltenes kostet Stunden bis eine Arbeitswoche (Reparatur 25 WLC). Der
Wochenbestand reicht für 42 Spieler: Von allem unter 2 WLC je Einheit gibt es mindestens 42 Einheiten pro Woche,
Seltenes ist knapp (Reparatur 4, Schallplatten und Besätze je 2).

Vorlage (Preis = erste Einheit beim Wochenbestand, Rückkauf = erste Einheit, die Brandt beim Wochenbestand annimmt,
leer = Preis bei leerem Lager; alle Mengen in Stück, Preise in WLC je Einheit):

| Ware | Item | Einheit | Preis | Rückkauf | leer | Woche | Lagerplatz |
|---|---|---|---|---|---|---|---|
| Rohes Eisen | `raw_iron` | 8 | 4,8 | 2,7 | 9,4 | 640 | 1280 |
| Rohes Kupfer | `raw_copper` | 16 | 1,6 | 0,8 | 3 | 1024 | 2048 |
| Rohes Gold | `raw_gold` | 8 | 6,4 | 3,6 | 12,4 | 336 | 672 |
| Rohes Platin | `autumnal_winterly:raw_platinum` | 4 | 6 | 3,4 | 11,8 | 168 | 336 |
| Smaragd | `emerald` | 1 | 0,8 | 0,4 | 1,4 | 256 | 512 |
| Redstone-Staub | `redstone` | 32 | 1,2 | 0,6 | 2,2 | 2048 | 4096 |
| Lapislazuli | `lapis_lazuli` | 16 | 1,2 | 0,6 | 2,2 | 1024 | 2048 |
| Kohle | `coal` | 32 | 1 | 0,5 | 1,8 | 2048 | 4096 |
| Diamant | `diamond` | 1 | 4 | 2,3 | 7,8 | 84 | 168 |
| Faden | `string` | 16 | 0,6 | 0,2 | 1 | 1024 | 2048 |
| Glowstone-Staub | `glowstone_dust` | 16 | 1,2 | 0,6 | 2,2 | 672 | 1344 |
| Netherwarze | `nether_wart` | 16 | 0,8 | 0,4 | 1,4 | 672 | 1344 |
| Magmacreme | `magma_cream` | 4 | 1,2 | 0,6 | 2,2 | 168 | 336 |
| Lohenrute | `blaze_rod` | 1 | 0,8 | 0,4 | 1,4 | 128 | 256 |
| Netherquarz | `quartz` | 32 | 1,6 | 0,8 | 3 | 1344 | 2688 |
| Schleimball | `slime_ball` | 8 | 1,2 | 0,6 | 2,2 | 336 | 672 |
| Honigwabe | `honeycomb` | 8 | 3,2 | 1,8 | 6,2 | 336 | 672 |
| Amethystscherbe | `amethyst_shard` | 8 | 0,8 | 0,4 | 1,4 | 512 | 1024 |
| Leder | `leather` | 8 | 1,2 | 0,6 | 2,2 | 512 | 1024 |
| Feder | `feather` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Tintenbeutel | `ink_sac` | 8 | 0,8 | 0,4 | 1,4 | 336 | 672 |
| Ghastträne | `ghast_tear` | 1 | 2 | 1,1 | 3,8 | 32 | 64 |
| Drachenatem | `dragon_breath` | 1 | 3 | 1,7 | 5,8 | 32 | 64 |
| Ausgetrockneter Ghast | `dried_ghast` | 1 | 8 | 4 | 14,6 | 8 | 16 |
| Verzauberungsbuch: Reparatur | `enchanted_book` + Reparatur I | 1 | 25 | 10,5 | 42 | 4 | 8 |
| Verzauberungsbuch: Haltbarkeit III | `enchanted_book` + Haltbarkeit III | 1 | 6 | 3 | 11 | 8 | 16 |
| Verzauberungsbuch: Effizienz IV | `enchanted_book` + Effizienz IV | 1 | 5 | 2,4 | 9 | 8 | 16 |
| Verzauberungsbuch: Huschen III | `enchanted_book` + Huschen III | 1 | 20 | 5,9 | 28,2 | 2 | 4 |
| Schallplatte „Pigstep“ | `music_disc_pigstep` | 1 | 11,9 | 3,5 | 16,8 | 2 | 4 |
| Schallplatte „Tears“ | `music_disc_tears` | 1 | 15 | 4,4 | 21,2 | 2 | 4 |
| Rüstungsbesatz „Rippe“ | `rib_armor_trim_smithing_template` | 1 | 9,9 | 2,9 | 14 | 2 | 4 |
| Rüstungsbesatz „Schnauze“ | `snout_armor_trim_smithing_template` | 1 | 9,9 | 2,9 | 14 | 2 | 4 |
| Rüstungsbesatz „Turmspitze“ | `spire_armor_trim_smithing_template` | 1 | 15 | 4,4 | 21,2 | 2 | 4 |
| Chorusfrucht | `chorus_fruit` | 16 | 1,2 | 0,6 | 2,2 | 672 | 1344 |
| Shulkerschale | `shulker_shell` | 1 | 4 | 2,2 | 7,8 | 32 | 64 |
| Bannervorlage „Schnauze“ | `piglin_banner_pattern` | 1 | 4 | 1,6 | 6,6 | 4 | 8 |
| Zuckerrohr | `sugar_cane` | 32 | 0,6 | 0,2 | 1 | 1344 | 2688 |
| Weißer Farbstoff | `white_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Hellgrauer Farbstoff | `light_gray_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Grauer Farbstoff | `gray_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Schwarzer Farbstoff | `black_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Brauner Farbstoff | `brown_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Roter Farbstoff | `red_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Oranger Farbstoff | `orange_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Gelber Farbstoff | `yellow_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Hellgrüner Farbstoff | `lime_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Grüner Farbstoff | `green_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Türkiser Farbstoff | `cyan_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Hellblauer Farbstoff | `light_blue_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Blauer Farbstoff | `blue_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Violetter Farbstoff | `purple_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Magenta Farbstoff | `magenta_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Rosa Farbstoff | `pink_dye` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Kürbiskerne | `pumpkin_seeds` | 16 | 0,3 | 0,1 | 0,4 | 672 | 1344 |
| Melonenkerne | `melon_seeds` | 16 | 0,3 | 0,1 | 0,4 | 672 | 1344 |
| Rote-Bete-Samen | `beetroot_seeds` | 16 | 0,3 | 0,1 | 0,4 | 672 | 1344 |
| Winterbeeren-Samen | `autumnal_winterly:winterberry_seeds` | 16 | 0,6 | 0,2 | 1 | 672 | 1344 |
| Knochen | `bone` | 16 | 1,2 | 0,6 | 2,2 | 672 | 1344 |
| Lavaeimer | `lava_bucket` | 1 | 1,5 | 0,8 | 2,8 | 42 | 84 |
| Bruchstein | `cobblestone` | 64 | 0,4 | 0,1 | 0,6 | 10752 | 21504 |
| Kies | `gravel` | 64 | 0,6 | 0,2 | 1 | 4096 | 8192 |
| Sand | `sand` | 64 | 0,6 | 0,2 | 1 | 6144 | 12288 |
| Erde | `dirt` | 64 | 0,3 | 0,1 | 0,4 | 6144 | 12288 |
| Schwarzstein | `blackstone` | 64 | 0,8 | 0,4 | 1,4 | 4096 | 8192 |
| Netherrack | `netherrack` | 64 | 0,3 | 0,1 | 0,4 | 4096 | 8192 |
| Basalt | `basalt` | 64 | 0,8 | 0,4 | 1,4 | 4096 | 8192 |
| Tuffstein | `tuff` | 64 | 0,6 | 0,2 | 1 | 4096 | 8192 |
| Andesit | `andesite` | 64 | 0,5 | 0,2 | 0,8 | 4096 | 8192 |
| Diorit | `diorite` | 64 | 0,5 | 0,2 | 0,8 | 4096 | 8192 |
| Granit | `granite` | 64 | 0,5 | 0,2 | 0,8 | 4096 | 8192 |
| Eichenstamm | `oak_log` | 64 | 2 | 1,1 | 3,8 | 4096 | 8192 |
| Fichtenstamm | `spruce_log` | 64 | 2 | 1,1 | 3,8 | 4096 | 8192 |
| Birkenstamm | `birch_log` | 64 | 2 | 1,1 | 3,8 | 4096 | 8192 |
| Tropenbaumstamm | `jungle_log` | 64 | 2 | 1,1 | 3,8 | 2688 | 5376 |
| Akazienstamm | `acacia_log` | 64 | 2 | 1,1 | 3,8 | 2688 | 5376 |
| Schwarzeichenstamm | `dark_oak_log` | 64 | 2 | 1,1 | 3,8 | 2688 | 5376 |
| Mangrovenstamm | `mangrove_log` | 64 | 2,4 | 1,3 | 4,6 | 2688 | 5376 |
| Kirschstamm | `cherry_log` | 64 | 2,4 | 1,3 | 4,6 | 2688 | 5376 |
| Blasseichenstamm | `pale_oak_log` | 64 | 2,4 | 1,3 | 4,6 | 2688 | 5376 |
| Tannenstamm | `autumnal_winterly:fir_log` | 64 | 2,4 | 1,3 | 4,6 | 2688 | 5376 |
| Korkstamm | `arts_and_crafts:cork_log` | 64 | 2,4 | 1,3 | 4,6 | 2688 | 5376 |
| Eichenlaub | `oak_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Fichtenlaub | `spruce_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Birkenlaub | `birch_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Tropenbaumlaub | `jungle_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Akazienlaub | `acacia_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Schwarzeichenlaub | `dark_oak_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Mangrovenlaub | `mangrove_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Kirschlaub | `cherry_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Blasseichenlaub | `pale_oak_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Tannenlaub | `autumnal_winterly:fir_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Korklaub | `arts_and_crafts:cork_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Azaleenlaub | `azalea_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Blühendes Azaleenlaub | `flowering_azalea_leaves` | 64 | 0,6 | 0,2 | 1 | 2688 | 5376 |
| Weiße Wolle | `white_wool` | 16 | 0,8 | 0,4 | 1,4 | 1024 | 2048 |
| Kaolin | `autumnal_winterly:kaolin` | 64 | 0,8 | 0,4 | 1,4 | 2688 | 5376 |
| Gips | `arts_and_crafts:gypsum` | 64 | 0,8 | 0,4 | 1,4 | 2688 | 5376 |
| Calcit | `calcite` | 64 | 1 | 0,5 | 1,8 | 2688 | 5376 |
| Tonblock | `clay` | 64 | 1,6 | 0,8 | 3 | 2688 | 5376 |
| Keramik | `terracotta` | 64 | 1,6 | 0,8 | 3 | 2688 | 5376 |
| Speckstein | `arts_and_crafts:soapstone` | 64 | 0,8 | 0,4 | 1,4 | 2688 | 5376 |
| Glas | `glass` | 64 | 1,6 | 0,8 | 3 | 4096 | 8192 |
| Moosblock | `moss_block` | 64 | 1 | 0,5 | 1,8 | 2688 | 5376 |
| Roter Sand | `red_sand` | 64 | 0,8 | 0,4 | 1,4 | 2688 | 5376 |
| Seelensand | `soul_sand` | 64 | 1 | 0,5 | 1,8 | 2688 | 5376 |
| Seelenerde | `soul_soil` | 64 | 1 | 0,5 | 1,8 | 2688 | 5376 |
| Endstein | `end_stone` | 64 | 1,6 | 0,8 | 3 | 2688 | 5376 |

Mod-Items: `autumnal_winterly` = Autumnal & Winterly (Rohes Platin, Winterbeeren-Samen, Kaolin, Tanne – IDs aus der
Mod auf dem Server geprüft), `arts_and_crafts` = Arts & Crafts (Gips, Speckstein, Kork – **angenommene** IDs, bitte mit
`/markt lager` prüfen; falsche IDs werden übersprungen und lassen sich in `items` korrigieren).

## Umstellung auf 0.6.0 (einmalig beim ersten Start)

1. **Einstellungen** ohne `configVersion`: Kopie `config/twogg-markt/markt.json.vor-0.6.0`, dann bekommt die Datei
   den Abschnitt `lager` mit der Vorlage (nur, wenn es ihn noch nicht gibt), und aus `jobs.pool` werden die 16
   Beispielvorlagen der alten Vorlage entfernt (erkannt an Titel und Item; eigene Vorlagen bleiben). Danach wird die
   Datei mit `configVersion: 2` neu geschrieben. Alle anderen Werte (Ankauf, Adventskalender, Regeln …) bleiben.
2. **Weltdaten** mit `version` 1: Kopie `<welt>/twogg-markt/markt.json.vor-0.6.0`, dann werden Marktaufträge vom
   Brett genommen, deren Titel und Item aus der alten Beispielvorlage stammen (Spieleraufträge und Aufträge aus
   eigenen Vorlagen bleiben; wer schon geliefert hat, hat sein Geld). In Brandts Lager werden die festen
   Verkaufszeilen (unbegrenzter Vorrat) und festen Ankaufszeilen entfernt. `version` wird 2.
3. **Abgleich** von Brandts Lager: Waren aus `lager.items` werden angelegt (Bestand = Wochenbestand), der nächste
   Nachschub ist der kommende Sonntag 06:00.

Das Log zeigt jede Änderung (`Markt-Einstellungen: …`, `Markt-Weltdaten: …`, `Brandts Lager: …`). Zurück auf 0.5.2:
Server stoppen, die `.vor-0.6.0`-Dateien zurückkopieren.

## Weltdaten `<welt>/twogg-markt/markt.json`

Klasse `de.twogg.markt.core.MarktData`. Beträge in **Zehntel-Coins** (`long`), Zeiten in ms seit Epoch.

| Feld | Inhalt |
|---|---|
| `version` | Stand der Weltdaten (2 seit 0.6.0) |
| `laeden` | Server-Läden: ID → `name`, `subtitle`, `open`, `sells`/`buys` (feste Zeilen), `waren` (`id`, `item`, `name`, `unit`, `base` Zehntel bei Soll, `capacity`, `target`, `stock`, `sold`, `bought`, `managed` = aus `lager.items`), `spread`, `deliveryDay`/`deliveryTime`/`zone`, `lastDelivery`, `trimSurplus` |
| `plots` | Stand-ID → Stand: `name`, `note`, `box` (`dim`, `minX`…`maxZ`, Grenzen einschließlich), `mode` (`RENT`, `BUY`, `BOTH`, `LOCKED`), `rentPrice`/`buyPrice` (€), `maxPeriods`, `owner` (UUID), `ownerName`, `bought`, `rentedUntil`, `periodPaid`, `boughtFor`, `since`, `warned`, `helpers` (UUID → Name), `shopOpen`, `offers`, `till` (Kasse), `salesTotal`, `nextOffer` |
| `offers[]` | `id`, `item` (ItemStack als JSON mit allen Daten-Komponenten, Anzahl 1), `name`, `count`, `price` (Zehntel), `sold` |
| `zones` | Räumzonen: ID → `name`, `box` |
| `jobs` | Aufträge: `id` (`m…` Markt, `s…` Spieler), `system`, `title`, `item`, `count`, `reward`, `slots`, `doneBy` (UUIDs), `created`, `expires`, `poster`, `posterName` |
| `lastJobRefresh`, `nextJob` | Zeitpunkt der letzten Ziehung, nächste Auftragsnummer |
| `sold`, `soldUpdated` | Ankauf: verkaufte Bündel pro Item (klingen ab) |
| `day`, `daily` | Tageszähler pro Spieler (Ankauf je Item, Schnee); werden bei Datumswechsel (Serverzeit) geleert |
| `pending` | Vorgemerkte Auszahlungen pro UUID: `amount`, `name`, `reasons` |
| `pickup` | Abholfächer pro UUID: Liste von ItemStacks als JSON (Aufträge, Wichteln) |
| `event` | `phase` (`CLOSED`, `OPENING`, `OPEN`, `FINALE`), `round`, `startedAt`, `endsAt`, `phaseUntil`, `windowKey`, `stats` (UUID → `name`, `sales`, `snow`, `jobs`, `gifts`), `votes` (UUID → Stand), `tickets`, `pot`, `wichtel`, `giftsFound`, `lastResults`, `lastEnded` |
| `area` | Marktgelände (Box) oder null |
| `leaderboard`, `leaderboardHolo` | Ort und UUID der Bestenliste-Tafel |
| `adventYear`, `advent` | Jahr und geöffnete Türchen pro Spieler (`/markt advent zuruecksetzen` leert sie) |
| `plots.*.hologram` | UUID des Hologramms über dem Stand |

Ist die Datei unlesbar, wird sie als `markt.json.broken-<zeit>` weggesichert, der Markt startet leer und das Log zeigt
einen deutlichen Fehlerblock. Dann: Server stoppen, Datei reparieren, als `markt.json` zurücklegen, neu starten.

## Leerzustände `snapshots/<id>.nbt`

Beim Anlegen eines Stands (und mit „Sichern“) werden alle Blockzustände im Bereich als komprimiertes NBT gespeichert
(Palette + Index pro Block). **Nicht** gesichert werden Block-Daten wie Kisteninhalte oder Schildtexte.
„Reset“ bzw. `resetOnRelease` setzt nur Blöcke, die sich unterscheiden; Kisten werden vorher geleert, damit nichts
herausfällt. Passt der gesicherte Bereich nicht mehr zum Stand, wird nicht zurückgesetzt (Warnung im Log).

## Geld im Journal der Economy

Alle Buchungen laufen über die API von TwoGG Economy und stehen in deren Journal mit diesen Quellen:

| Quelle | Art | Wofür |
|---|---|---|
| `markt:miete`, `markt:kauf`, `markt:theke` | BURN / CASH_BURN | Miete, Standkauf, weitere Theke |
| `markt:stand` | BURN / CASH_BURN | Kauf an einer Verkaufstheke |
| `markt:kasse` | MINT / CASH_MINT | Standkasse leeren (auch bei Ablauf) |
| `markt:erstattung`, `markt:rueckkauf` | MINT | Kündigung, Rückkauf |
| `markt:ankauf` | MINT / CASH_MINT | Ankauf |
| `markt:auftrag` | BURN beim Aushängen, MINT bei Erledigung oder Rückgabe | Auftragsbrett |
| `markt:raeumdienst` | MINT | Räumdienst |
| `markt:gutschrift` | MINT | Vorgemerkte Beträge beim Login |
| `markt:lose` | BURN / CASH_BURN | Lose kaufen |
| `markt:event` | MINT | Lospreise, Siegerpreise |
| `markt:geschenk`, `markt:advent` | MINT | Geschenkesuche, Adventskalender |
| `markt:laden` | BURN / CASH_BURN beim Kauf, MINT / CASH_MINT beim Rückkauf | Server-Läden, auch Brandts Lager |

Die Marktgebühr erscheint nicht eigens: Der Käufer zahlt den vollen Preis (BURN), in die Kasse kommt der Betrag ohne
Gebühr (später MINT). Die Differenz verlässt den Umlauf.
