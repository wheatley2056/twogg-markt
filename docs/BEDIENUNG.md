# Bedienung: TwoGG Markt

## Grundbegriffe

| Begriff | Bedeutung |
|---|---|
| **Stand** | Ein vom Admin eingezeichneter Quader (Boden bis Dach). Kann frei, vermietet, verkauft oder gesperrt sein. |
| **Zeitraum** | Mieteinheit, Standard ein Tag Echtzeit (`stalls.periodMinutes = 1440`). |
| **Standkasse** | Einnahmen der Verkaufstheke. Der Markt hält sie, bis der Besitzer sie leert. |
| **Helfer** | Spieler, die der Besitzer einträgt. Sie bauen mit, öffnen Kisten und verwalten die Theke, leeren aber nicht die Kasse. |
| **Konto / Bar** | Bezahlt wird über TwoGG Economy: vom Konto (Standard) oder mit Bargeld aus dem Inventar. Umschalten mit dem Knopf „Konto“/„Bar“. Bargeld lässt sich in den Regeln abschalten. |

Preise für Stände und die Theke stehen in **€** und werden zum aktuellen Wertkurs der Bank in Coins umgerechnet
(aufgerundet auf 0,1). Fällt der Kurs, wird die Miete in Coins teurer. Preise an Theken setzen die Spieler direkt in Coins.

## Für Spieler

### Markt-Handbuch (seit 0.3.0)

Ein eigenes Buch (Item `twogg_markt:handbuch`): Rechtsklick öffnet eine **Doppelseite** mit Ledereinband und Pergament.
Links die Titelseite, rechts das **Inhaltsverzeichnis zum Anklicken**; danach Kapitel zu Stand, Theke, Geld ohne Stand,
Bezahlen, Weihnachtsmarkt, Befehlen und Tipps – mit **Bildern aus dem Spiel** (Klick vergrößert ein Bild). Am rechten
Rand stecken farbige **Lesezeichen** je Kapitel. Blättern: „◀ zurück“ / „weiter ▶“, Pfeiltasten, A/D, Bild ↑/↓ oder
Mausrad; Pos1 springt zum Anfang. Die Zahlen im Text (Gebühr, Preise, Grenzen) schickt der Server aus der aktuellen
Config mit. Jeder bekommt es beim ersten Betreten einmal (`guide.giveOnJoin`), `/markt handbuch` gibt jederzeit ein
neues; auch im Kreativ-Tab.

Text: `src/main/resources/assets/twogg_markt/handbuch/de_de.txt` (Auszeichnung `# Kapitel`, `## Abschnitt`, `- Punkt`,
`![bild] Unterschrift`, Platzhalter wie `{fee}`). Bilder: `textures/gui/handbuch/<bild>.png` (16:9, 384 × 216), erzeugt von
`tools/handbuch_bilder.py` aus den Screenshots des Spieltests (`./gradlew runClientGameTest`, dann das Skript).

### Markt öffnen

| Weg | Was passiert |
|---|---|
| `/markt` | Standübersicht, für alle Spieler |
| Rechtsklick aufs **Standschild** | dieselbe Übersicht, der Stand vor dem Schild ist vorausgewählt |
| `/markt zeigen` | Stände in 48 Blöcken Umkreis 30 Sekunden lang als Partikel-Umriss |
| Stand betreten | Hinweis über der Hotbar („Stand „…“ – frei · Miete 1,5 WLC/Tag · /markt“); freie Stände blinken kurz als Umriss |

### Seiten der Standübersicht

| Seite | Inhalt |
|---|---|
| **Stände** | Liste aller Stände (Punkt = Status), rechts der gewählte: Beschreibung, Größe, Lage, Miete pro Zeitraum, Kaufpreis. Unten Zeiträume wählen (−/+), Konto/Bar, „Mieten …“ bzw. „Verlängern …“ und „Kaufen …“ (zweiter Klick bestätigt). |
| **Mein Stand** | Status und Restzeit, Kasse, Umsatz, Angebote. Knöpfe: Kasse leeren, Theke auf/zu, Theke kaufen (weitere Theke), +1 Zeitraum, Kündigen bzw. Zurückgeben (zweiter Klick bestätigt). Rechts die Helfer: anklicken entfernt, Namen tippen + „+“ fügt hinzu (nur Spieler, die online sind). |
| **Verdienen** | Kurzübersicht über alle Verdienstmöglichkeiten auf dem Markt |

Regeln beim Mieten und Kaufen:

- Pro Spieler höchstens `stalls.maxPerPlayer` Stände gleichzeitig (Standard 1).
- Mieten: höchstens `maxPeriods` Zeiträume im Voraus (pro Stand, Standard 7). Verlängern hängt hinten an.
- Ein gemieteter Stand kann gekauft werden; die restliche Miete verfällt.
- Beim ersten Anmieten oder Kaufen gibt es eine **Verkaufstheke** ins Inventar.
- **Kündigen** (Miete): Erstattung `rentRefundPercent` (50 %) der ganzen, noch nicht angefangenen Zeiträume.
- **Zurückgeben** (Kauf): Der Markt zahlt `buybackPercent` (50 %) des Kaufpreises zurück.
- Kurz vor Ablauf kommt eine Nachricht (1 Stunde und 10 Minuten vorher). Danach ist der Stand frei, die Kasse wird
  ausgezahlt (offline: beim nächsten Login), Angebote und Helfer werden gelöscht.

### Bauen im Stand

Besitzer und Helfer bauen und bauen ab wie überall. Für alle anderen sind Blöcke, Kisten, Türen, Rahmen und
Rüstungsständer im Stand gesperrt („Das ist der Stand von …“). Tiere und Monster lassen sich weiter angreifen.
Die Markt-Blöcke (Theke, Standschild, Ankauf, Auftragsbrett) kann jeder benutzen. Die Verkaufstheke lässt sich nur im
eigenen Stand aufstellen.

### Verkaufstheke

1. Theke in den eigenen Stand stellen. Ware in **Truhen, Fässer oder Shulkerkisten innerhalb des Stands** legen.
2. Rechtsklick auf die Theke → Seite **Verwalten**: die Ware in die Hand nehmen, **Preis** (Coins pro Kauf) und
   **Stück** (pro Kauf) eintragen, **Anbieten**. Bis zu 12 Angebote (`shop.maxOffers`).
3. Ein Angebot anklicken füllt die Felder; **Ändern** übernimmt Preis und Stück, **Entfernen** löscht es.
4. Kopfzeile: **Schließen/Öffnen** (geschlossene Theken verkaufen nicht), **Kasse leeren** (nur der Besitzer; mit „Bar“
   als Bargeld).

Angebote vergleichen Item **und alle Daten** (Name, Verzauberung …): Verkauft wird nur Ware, die genau der Vorlage
entspricht. Die Spalte „Vorrat“ zeigt, wie viele Käufe die Kisten noch hergeben.

**Käufer** sehen alle Angebote mit Vorrat, wählen eins, stellen die Menge (−/+) und Konto/Bar ein und drücken
„Kaufen · Betrag“. Die Ware kommt ins Inventar (was nicht passt, fällt vor die Füße). Vom Betrag geht die Marktgebühr
(`shop.feePercent`, 5 %, mindestens 0,1) ab; sie wird vernichtet. Der Rest landet in der Standkasse, der Besitzer
bekommt eine Nachricht über der Hotbar. Am eigenen Stand kann man nicht kaufen.

### Ankauf

Rechtsklick auf die **Ankaufstelle**. Jede Zeile: Item, „n Stück → Preis“, wie viele Bündel man dabeihat und wie
viele man heute noch verkaufen darf. **1×** verkauft ein Bündel, **Alle** so viele wie möglich. Bezahlt wird aufs
Konto (oder bar, `buyer.payCash`).

- Der Preis fällt mit jedem verkauften Bündel (alle Spieler zusammen) linear bis auf `minFactor` (30 %) beim
  Tagesbedarf und erholt sich in `recoverMinutes` (24 Stunden) wieder. Grün = voller Preis, gelb = unter der Hälfte.
- Gezählt werden nur unbeschädigte Stapel aus dem Hauptinventar; Namen und Daten sind egal.
- Fällt ein Preis auf 0, kauft der Markt das Item an diesem Tag nicht mehr.

### Auftragsbrett

| Seite | Inhalt |
|---|---|
| **Aufträge** | Marktaufträge und Aufträge anderer Spieler: Titel, Belohnung, „n× Item · dabei x · noch y von z“. **Liefern** nimmt die Items und zahlt die Belohnung aufs Konto. Jeder kann jeden Marktauftrag einmal erledigen, solange Plätze frei sind. Eigene Aufträge zeigen **Zurückziehen**. |
| **Aushängen** | Ware in die Hand nehmen, Stück, Belohnung und optional einen Titel eintragen, Konto/Bar, **Auftrag aushängen**. Die Belohnung wird sofort abgebucht und hinterlegt. Höchstens `jobs.maxPerPlayer` (3) Aufträge, Belohnung bis `jobs.maxReward` (200). |
| **Abholfach** | Ware, die jemand für die eigenen Aufträge geliefert hat. **Alles abholen** legt sie ins Inventar. |

- Marktaufträge wechseln alle `jobs.refreshMinutes` (24 Stunden), es hängen `jobs.active` (4) gleichzeitig.
- Spieleraufträge laufen nach `jobs.playerJobHours` (72 Stunden) ab; Zurückziehen oder Ablauf zahlt die Belohnung
  zurück (offline: beim nächsten Login).
- Geliefert werden unbeschädigte Stapel mit der richtigen Item-ID; Daten wie Namen sind egal. Was geliefert wird,
  landet unverändert im Abholfach.

### Räumdienst

In **Räumzonen** zahlt der Markt fürs Schneeschippen: Jede abgebaute Schneeschicht oder jeder Schneeblock zählt, nach
`snow.blocksPerPay` (8) Blöcken gibt es `snow.pay` (0,1), höchstens `snow.dailyMax` (3) pro Tag. Über der Hotbar steht
der Zähler. Nur im Überlebensmodus. Selbst gesetzter Schnee zählt nicht. Nachschub kommt durch Schneefall
(`/weather`, Schneebiome); Schnee häuft sich laut Spielregel `snowAccumulationHeight` an.

### Vorgemerkte Beträge

Kann der Markt nicht auszahlen (Spieler offline oder ohne aktiven Charakter), merkt er den Betrag vor und zahlt ihn
beim nächsten Login oder beim Öffnen eines Markt-Fensters aus. Die Fußzeile der Fenster zeigt „vorgemerkt …“.

### Hologramme

Über jedem Stand schwebt eine Schrift: Name, „frei“ mit Miete und Kaufpreis bzw. „Stand von …“, Beschreibung und
Angebote (geöffnet/geschlossen). Während des Events steht dort auch die Zahl der Stimmen. Die Hologramme aktualisieren
sich alle 5 Sekunden und sind für alle sichtbar, auch ohne Mod. Ausschalten: Regel „Hologramme“.

## Weihnachtsmarkt-Event

### Ablauf

| Phase | Was passiert |
|---|---|
| geschlossen | Stände lassen sich mieten. Mit `event.onlyDuringEvent` verkaufen Theken, Ankauf und Auftragsbrett nur im Event. |
| Ankündigung | Bei festen Öffnungszeiten 15, 5 und 1 Minute vorher im Chat |
| **Eröffnung** | Titel „Weihnachtsmarkt – ist eröffnet!“, Glocken, Bossbar, Schneefall; 60 Sekunden |
| **geöffnet** | Bossbar mit Restzeit, Weihnachtslieder (Glockenspiel) und Atmos-Stimmung im Marktgelände, Losbude mit Wichteln, Geschenkesuche, Abstimmung; 5 Minuten vor Schluss eine Erinnerung |
| **Siegerehrung** | Losziehung, Wichtelgeschenke werden verteilt, Preise je Kategorie, Ergebnisse im Chat und auf der Tafel, Feuerwerk; 90 Sekunden, dann schließt der Markt |

Gestartet wird per `/markt event start [minuten]`, in der Marktverwaltung (Seite **Event**) oder automatisch nach
`event.schedule`. Jedes Fenster der Öffnungszeiten startet nur einmal (auch wenn ein Admin es vorher beendet).

### Mitmachen

| Aktion | Wie |
|---|---|
| **Losbude** | Lose kaufen (0,5 WLC = 5 €, höchstens 10). Bei der Siegerehrung werden 80 % des Topfs ausgeschüttet: 50 / 30 / 20 % an drei verschiedene Gewinner, Chance je nach Zahl der Lose. Der Rest bleibt beim Markt (wird vernichtet). |
| **Wichteln** (Losbude, Seite „Wichteln“) | Den Stapel in der Hand abgeben (einer pro Spieler, zurücknehmen bis zur Siegerehrung). Bei der Siegerehrung bekommt jeder ein fremdes Geschenk ins Abholfach (abholen an der Losbude oder am Auftragsbrett). |
| **Geschenkesuche** | Admins verstecken „Versteckte Geschenke“. Rechtsklick: 0,5 WLC, einmal pro Geschenk und Spieler und Event. Zählt für die Bestenliste. |
| **Adventskalender** | 1.–24. Dezember ein Türchen pro Tag und Spieler (Coins und/oder Items laut `event.adventDays`). Läuft unabhängig vom Event. Verpasste Tage bleiben zu. |
| **Abstimmen** | „★ Für diesen Stand stimmen“ in der Standübersicht oder „★ Abstimmen“ an der Theke. Eine Stimme pro Spieler, umentscheiden geht, nicht für den eigenen Stand. |

### Bestenliste und Preise

Kategorien: **Schönster Stand** (Stimmen, Preis an den Besitzer), **Bester Umsatz** (Verkäufe an der eigenen Theke),
**Fleißigste Helfer** (erledigte Aufträge), **Räumdienst** (geräumte Schneeblöcke), **Geschenkesuche**. Gezählt
wird nur während des Events. Platz 1–3 bekommen `event.prizes` (10 / 5 / 2,5 WLC). Wer offline ist, bekommt den
Preis beim nächsten Login. Die Seite **Event** in `/markt` zeigt Restzeit, Öffnungszeiten, eigene Lose, Stimme,
Wichtelgeschenk und die Bestenlisten (Pfeile blättern); nach dem Event das letzte Ergebnis.

Die **Bestenliste-Tafel** (`/markt event tafel hier`) ist ein Hologramm, das alle 10 Sekunden die Kategorie wechselt
und nach dem Event die Ergebnisse zeigt.

### Atmosphäre

- **Marktgelände** (`/markt gelaende setzen` mit Planer-Auswahl): Nur dort laufen Musik und Atmos-Stimmung (plus
  8 Blöcke Rand). Ohne Gelände gilt der ganze Server.
- **Musik:** O Tannenbaum, Stille Nacht, Jingle Bells als Glockenspiel (gemeinfrei), mit Pausen. Lautstärke über die
  Klangkategorie „Musikblöcke/Plattenspieler“ des Spielers.
- **TwoGG Atmos** (optional): Preset `event.atmosPreset` (Standard `draussen_klar`) im Gelände.
- **Schneefall** während des Events, **Feuerwerk** bei der Siegerehrung (über dem Siegerstand und im Gelände).

## Für Admins (OP-Stufe 2)

### Stände einzeichnen

1. `/markt planer` (oder Kreativ-Tab „TwoGG Markt“). Linksklick auf einen Block = Ecke 1, Rechtsklick = Ecke 2. Die
   Ecken gehören zum Stand, also vom Boden bis unters Dach auswählen. Über der Hotbar steht die Größe und ob sich die
   Auswahl mit einem Stand überschneidet.
2. Mit dem Planer in der Hand sieht man alle Stände (Farbe = Status), Räumzonen (hellblau) und die Auswahl (weiß).
3. `/markt stand hoch <n>` oder „+1 hoch“/„+4 hoch“ in der Verwaltung erweitert die Auswahl nach oben.
4. „Stand anlegen“ (Seite **Neu**) oder `/markt stand neu <Name>`. Höchstens `stalls.maxVolume` Blöcke, keine
   Überschneidung mit anderen Ständen. Der Leerzustand wird gesichert (siehe KONFIGURATION).
5. Ein **Standschild** vor oder in den Stand stellen (bis 3 Blöcke daneben wird es dem nächsten Stand zugeordnet).

### Marktverwaltung

`/markt admin` oder die Kachel **Markt** in der TwoGG Toolbox. Der Server prüft bei jeder Aktion die Rechte.

| Seite | Inhalt |
|---|---|
| **Stände** | Liste; rechts Name, Beschreibung, Miete (€/Zeitraum), Kauf (€), Voraus (max. Zeiträume) → **Speichern**. **Art** schaltet Miete + Kauf → nur Miete → nur Kauf → gesperrt. **Hin** (Teleport), **Zeigen** (Umriss 15 s), **Sichern** (Leerzustand neu), **Reset** (auf Leerzustand), **Räumen** (Besitzer raus, Kasse an ihn), **+1 Zeitraum gratis**, **Stand löschen**. Endgültige Knöpfe brauchen einen zweiten Klick. |
| **Neu** | Auswahl (Ecken, Größe, Überschneidung), Name, Miete und Kauf in € (leer = 15 € / 400 €), **Stand anlegen**, **Als Räumzone**, Auswahl erweitern oder löschen |
| **Ankauf** | Posten wählen → Preis (Coins pro Bündel), Bündel (Stück), Bedarf (Bündel pro Tag) → **Ändern** / **Entfernen**. **Neu aus Hand** legt einen Posten für das Item in der Hand an. |
| **Aufträge** | Alle Aufträge (★ = von Spielern). **Neue Marktaufträge** würfelt neu, **Auftrag entfernen** (Spieleraufträge: Belohnung geht zurück) |
| **Räumzonen** | Liste, **Zone löschen** |
| **Event** | Start (Minuten), Siegerehrung jetzt, Abbrechen (Lose und Geschenke zurück), Gelände setzen/weg, Tafel hier/weg, Schalter Musik, Bossbar, Handel nur im Event, Schneefall, Feuerwerk, Atmos-Preset |
| **Regeln** | Schalter und Zahlen aus `markt.json`, sofort gespeichert. **Werte 1/2** blättert, **Neu laden** liest die Datei neu. |

### Befehle

| Befehl | Recht | Wirkung |
|---|---|---|
| `/markt` | alle | Standübersicht |
| `/markt zeigen` | alle | Umrisse 30 Sekunden |
| `/markt handbuch` | alle | Markt-Handbuch ins Inventar |
| `/markt helfer add <spieler>` / `entfernen <name>` | alle (Besitzer) | Helfer im eigenen Stand |
| `/markt admin` | OP 2 | Marktverwaltung |
| `/markt planer` | OP 2 | Marktplaner ins Inventar |
| `/markt stand neu <name>` | OP 2 | Stand aus der Auswahl |
| `/markt stand hoch <blöcke>` | OP 2 | Auswahl nach oben erweitern |
| `/markt stand liste` / `info <id>` | OP 2 | Übersicht im Chat |
| `/markt stand miete <id> <€>` / `kaufpreis <id> <€>` | OP 2 | Preise |
| `/markt stand vorausmiete <id> <zeiträume>` | OP 2 | Höchstens so viele Zeiträume im Voraus |
| `/markt stand art <id> miete\|kauf\|beides\|gesperrt` | OP 2 | Art |
| `/markt stand beschreibung <id> <text>` | OP 2 | Text für Spieler |
| `/markt stand vergeben <id> <spieler> [zeiträume]` | OP 2 | Kostenlos vergeben: mit Zeiträumen als Miete, ohne als Kauf |
| `/markt stand raeumen <id>` | OP 2 | Besitzer entfernen, Kasse an ihn |
| `/markt stand sichern <id>` / `zuruecksetzen <id>` | OP 2 | Leerzustand sichern / herstellen |
| `/markt stand tp <id>` / `loeschen <id>` | OP 2 | Teleport / Stand löschen |
| `/markt zone neu <name>` / `loeschen <id>` / `liste` | OP 2 | Räumzonen |
| `/markt auftraege neu` | OP 2 | Marktaufträge neu würfeln |
| `/markt event start [minuten]` / `stop` / `abbrechen` / `status` | OP 2 | Event starten, Siegerehrung jetzt, ohne Preise abbrechen, Stand |
| `/markt event tafel hier` / `weg` | OP 2 | Bestenliste-Hologramm |
| `/markt gelaende setzen` / `weg` | OP 2 | Marktgelände aus der Planer-Auswahl |
| `/markt reload` / `speichern` | OP 2 | Einstellungen neu lesen / Weltdaten speichern |

Admins dürfen in allen Ständen bauen.

### Blöcke und Items

| Block/Item | Wer stellt auf | Abbauen |
|---|---|---|
| Verkaufstheke | Spieler im eigenen Stand | Besitzer und Helfer (Holz) |
| Standschild, Ankaufstelle, Auftragsbrett, Losbude, Adventskalender, Verstecktes Geschenk | Admins | nur im Kreativmodus (wie das Bankterminal) |
| Marktplaner | – | Item, nur für Admins nutzbar |

Platz beim Aufstellen: Die Blöcke zeigen mit der Vorderseite zum Spieler, der sie setzt. Theke (Markise),
Ankaufstelle (Preistafel), Auftragsbrett und Losbude (Dach) sind knapp zwei Blöcke hoch – darüber einen Block
freilassen; anklicken kann man den unteren Teil. Der **Adventskalender** ist ein Häuschen für die Wand: vor eine Wand
setzen (Rückseite an die Wand), er ist gut 1,5 Blöcke breit und 2 Blöcke hoch. Das **Geschenk** ist klein und lässt
sich gut in Ecken, unter Tannen oder neben Kisten verstecken.

Wichtelbaum und Lichterkette gab es nur in 0.2.0. Wurden sie schon gesetzt, verschwinden sie beim Laden der Welt
(Minecraft ersetzt unbekannte Blöcke durch Luft).

Es gibt keine Rezepte. Weitere Theken kaufen Spieler unter „Mein Stand“ (`shop.counterPrice`, 20 €).
