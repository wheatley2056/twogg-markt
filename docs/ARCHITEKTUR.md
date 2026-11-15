# Architektur: TwoGG Markt

## Überblick

```
Client (src/client)                       Server (src/main)
┌───────────────────────┐   Action   ┌──────────────────────────────────────────────┐
│ MarketScreen  (market)│ ─────────► │ TwoggMarkt.handle → StallService / ShopService │
│ ShopScreen    (shop)  │            │   BuyerService / BoardService / AdminService   │
│ BuyerScreen   (buyer) │ ◄───────── │        │ Sessions (Fenster, Block, Abstand)    │
│ BoardScreen   (board) │    View    │        ▼                                       │
│ AdminScreen   (admin) │            │ Pay ──► de.twogg.economy.api.Economy           │
└───────────────────────┘            │ Protection (Fabric-Events)  SnowService        │
                                     │ Outlines (Partikel, Hinweise)  Snapshots (NBT) │
                                     │ MarktRuntime: MarktConfig + MarktData + Store  │
                                     └──────────────────────────────────────────────┘
```

Grundsatz wie bei der Economy: **Der Server entscheidet alles; der Client zeigt nur an und schickt Wünsche.**

## Pakete

```
de.twogg.markt
├── TwoggMarkt            ModInitializer: Inhalte, Pakete, Schutz, Events, Befehle; verteilt Aktionen nach Fenster
├── MarktRuntime          laufender Zustand, Takt (10/100/600 Ticks), Speichern, Tageswechsel, Kurs/Format aus der Economy
├── core/                 ohne Minecraft, getestet in MarketMathTest
│   ├── Box               Quader in einer Dimension (contains, intersects, Größe)
│   ├── Plot, Offer       Stand mit Status, Besitzer, Helfern, Angeboten, Kasse
│   ├── Job               Auftrag (Markt oder Spieler)
│   ├── MarktConfig       Einstellungen + Weihnachtsmarkt-Vorlage + sanitize
│   ├── MarktData         Weltdaten + sanitize, Hilfen (plotAt, zoneAt, addPending)
│   ├── MarktEvent        Event-Zustand (Phase, Statistik, Stimmen, Lose, Wichtelgeschenke, Ergebnisse)
│   ├── Schedule          Öffnungszeiten parsen, aktives und nächstes Fenster
│   ├── Melodies          gemeinfreie Lieder als Notenblock-Notenfolgen
│   └── MarketMath        €→Coins, Mietzeiträume, Erstattung, Gebühr, Ankaufpreis, Abklingen, Ziehen, Losziehung,
│                         Wichtel-Zuordnung (niemand bekommt sein eigenes), Rangliste, Format
├── data/
│   ├── Store             JSON lesen/schreiben (atomar), kaputte Weltdaten wegsichern
│   └── Stacks            ItemStack ↔ JSON über ItemStack.CODEC (mit Registry-Kontext)
├── content/
│   ├── ModContent        Blöcke market_counter, stall_sign, buyer_station, job_board; Item market_planner; Kreativ-Tab
│   ├── MarktBlock        drehbarer Block, Rechtsklick → Service
│   └── PlannerItem       Rechtsklick = Ecke 2 (Ecke 1 über AttackBlockCallback)
├── mixin/                Invoker für private Setter von Display/TextDisplay (Hologramme)
├── compat/AtmosCompat    TwoGG Atmos per Reflection (optional)
├── net/
│   ├── Payloads          twogg_markt:view (S2C), twogg_markt:action (C2S), JSON bis 262 144 Zeichen
│   └── Views             View (ein Typ für alle Fenster, Feld screen), Zeilen-Klassen, Action
└── server/
    ├── Sessions          offenes Fenster pro Spieler (+ Block, Dimension, ≤ 8 Blöcke), Drossel für Ansichten
    ├── Send              Kopf jeder Ansicht (Konto, Bargeld, Kurs, vorgemerkt), Versand
    ├── Pay               charge (Konto/bar), payout (sonst vormerken), later, settle
    ├── StallService      Fenster „market“, mieten/kaufen/kündigen/Kasse/Helfer, Ablauf und Warnungen
    ├── ShopService       Fenster „shop“, Angebote, Kauf aus den Lagerkisten des Stands
    ├── BuyerService      Fenster „buyer“, Ankauf mit fallendem Preis, Abklingen
    ├── BoardService      Fenster „board“, Marktaufträge ziehen, liefern, aushängen, Abholfach, Ablauf
    ├── SnowService       Räumdienst (Abbau-Event), gesetzter Schnee zählt nicht
    ├── Protection        Bauschutz in Ständen über Fabric-Events
    ├── Outlines          Umriss-Partikel, Hinweis beim Betreten
    ├── Selections        Planer-Auswahl pro Admin (im Speicher)
    ├── Snapshots         Leerzustand sichern/herstellen
    ├── Holograms         TextDisplays über Ständen und Bestenliste-Tafel, Aufräumen verwaister beim Laden
    ├── EventService      Phasen, Öffnungszeiten, Bossbar, Musik, Atmos, Statistik, Abstimmung, Siegerehrung, Feuerwerk
    ├── ActivityService   Fenster „lottery“, „advent“, „wichtel“; versteckte Geschenke
    ├── Levels            Dimension aus ID
    ├── AdminService      Fenster „admin“ und alle Admin-Aktionen (auch für /markt-Befehle)
    └── MarktCommands     /markt …
de.twogg.markt.client     (src/client)
├── TwoggMarktClient      Empfänger, Toolbox-Kachel „Markt“ (adminOnly)
├── MarktScreen           Basis: receive (öffnen oder aktualisieren), Felder mit Einheit, Blättern, Bestätigungs-Knopf
└── MarketScreen, ShopScreen, BuyerScreen, BoardScreen, ActivityScreen, AdminScreen
```

## Geldfluss

Der Markt bucht **nur über die öffentliche Economy-API** (`Economy.charge`, `chargeCash`, `pay`, `giveCash`). Da die
API kein Konto-zu-Konto zwischen beliebigen (auch offline) Spielern kennt, hält der Markt Beträge dazwischen selbst:

| Vorgang | Economy | Markt |
|---|---|---|
| Kunde kauft an der Theke | `charge`/`chargeCash` (vernichtet) | `plot.till += Preis − Gebühr` |
| Besitzer leert die Kasse | `pay`/`giveCash` (neu) | `till = 0` |
| Spieler hängt Auftrag aus | `charge` | Belohnung im Auftrag |
| Jemand liefert | `pay` an den Lieferanten | Ware ins Abholfach |
| Auszahlung klappt nicht (offline, kein Charakter) | – | `pending[uuid]`, gezahlt bei Login/Fenster |

Netto bleibt die Geldmenge gleich (bis auf Marktgebühr, Erstattungsabschläge und Mieten, die Geld vernichten, sowie
Ankauf, Aufträge und Räumdienst, die neues Geld schaffen). Das passt zum Wertkurs der Economy: Mieten und Gebühren
stützen den Kurs, Nebenverdienste drücken ihn leicht.

## Netzwerk

| ID | Richtung | Inhalt |
|---|---|---|
| `twogg_markt:view` | S→C | `Views.View`, `screen` = `market` / `shop` / `buyer` / `board` / `admin`. Ist dasselbe Fenster (gleicher Stand) offen, wird es aktualisiert, sonst geöffnet. |
| `twogg_markt:action` | C→S | `Views.Action`: `a`, `screen`, `plot`, `id`, `text`, `name`, `count`, `amount` (Zehntel), `value`, `cash`, `flag` |

| Fenster | Aktionen `a` |
|---|---|
| market | `refresh`, `close`, `rent` (plot, count, cash), `buy`, `cancel`, `collect`, `helper_add`/`helper_remove` (name), `shop_toggle`, `buy_counter` |
| shop | `refresh`, `close`, `buy` (id, count, cash), `offer_add` (amount, count; Ware aus der Hand), `offer_edit`, `offer_remove`, `toggle`, `collect` |
| buyer | `refresh`, `close`, `sell` (id = Item-ID, flag = alle) |
| board | `refresh`, `close`, `deliver` (id), `post` (count, amount, text, cash; Item aus der Hand), `cancel`, `pickup` |
| lottery | `refresh`, `close`, `buy` (count, cash) |
| advent | `refresh`, `close`, `open` (heutiges Türchen) |
| wichtel | `refresh`, `close`, `give` (Stapel aus der Hand), `take`, `pickup` |
| market, shop | zusätzlich `vote` |
| admin | `open`, `refresh`, `close`, `sel_expand`, `sel_clear`, `plot_create`, `plot_*`, `zone_create`, `zone_delete`, `buyer_add`/`_edit`/`_remove`, `jobs_reroll`, `job_remove`, `setting`, `reload` |

Absicherung: Block-Fenster gelten nur mit offener Sitzung am selben Block (≤ 8 Blöcke, Block noch da). Ein spätes
`close` eines alten Fensters beendet nur die Sitzung desselben Fenstertyps. Ansichten ohne Aktion höchstens alle
200 ms. Admin-Aktionen prüfen `Commands.LEVEL_GAMEMASTERS` bei jedem Paket. Fehlende Felder führen nicht zu
Ausnahmen; Ausnahmen in Aktionen werden geloggt und nicht weitergereicht.

## Bauschutz

| Event | Prüfung |
|---|---|
| `PlayerBlockBreakEvents.BEFORE` | Block im Stand → `canBuild` |
| `AttackBlockCallback` | Planer in der Hand → Ecke 1 setzen; sonst `canBuild` |
| `UseBlockCallback` | Markt-Blöcke frei; sonst geklickter Block und (mit Item in der Hand) der Zielblock; Theke nur im eigenen Stand |
| `UseItemCallback` | Eimer: angepeilter Block und Nachbar |
| `UseEntityCallback`, `AttackEntityCallback` | Nicht-Lebewesen und Rüstungsständer im Stand |

`canBuild`: kein Stand → ja; Admin → ja; Besitzer/Helfer eines vergebenen Stands → ja; freier Stand → nur wenn
`protectFree = false`. Nicht abgedeckt: Explosionen, Kolben und Flüssigkeiten von außen.

## Takt

| Alle | Was |
|---|---|
| 1 Tick | Musik |
| 10 Ticks | Umrisse (Planer, `/markt zeigen`), Hinweis beim Betreten |
| 20 Ticks | Event-Phasen, Öffnungszeiten, Ankündigungen, Bossbar, Atmos |
| 100 Ticks | Hologramme, Tageswechsel, Ablauf und Warnungen, neue Marktaufträge, Ablauf von Spieleraufträgen, Ankauf-Abklingen |
| 600 Ticks | Speichern, wenn sich etwas geändert hat |

Alles läuft im Server-Thread.
