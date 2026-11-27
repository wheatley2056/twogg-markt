# Konfiguration: TwoGG Markt

## Speicherorte

```
config/twogg-markt/
└── markt.json                 Einstellungen, Ankauf-Liste, Pool der Marktaufträge (Vorlage beim ersten Start)

<welt>/twogg-markt/
├── markt.json                 Stände, Räumzonen, Aufträge, Zähler, vorgemerkte Beträge, Abholfächer
├── markt.json.broken-<zeit>   nur, wenn die Datei einmal unlesbar war (siehe unten)
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
| `pool` | Liste | 16 Vorlagen | `title`, `item`, `count`, `reward` (Coins), `weight` (Ziehgewicht, Standard 1) |

Vorlagen mit unbekannter Item-ID werden beim Ziehen übersprungen.

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
| `adventDays` | Liste | 24 Tage | `day` (1–24), `coins`, `item`, `count`, `text` |
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

## Weltdaten `<welt>/twogg-markt/markt.json`

Klasse `de.twogg.markt.core.MarktData`. Beträge in **Zehntel-Coins** (`long`), Zeiten in ms seit Epoch.

| Feld | Inhalt |
|---|---|
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
| `adventYear`, `advent` | Jahr und geöffnete Türchen pro Spieler |
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

Die Marktgebühr erscheint nicht eigens: Der Käufer zahlt den vollen Preis (BURN), in die Kasse kommt der Betrag ohne
Gebühr (später MINT). Die Differenz verlässt den Umlauf.
