# Changelog: TwoGG Markt



## 0.6.0+26.1.2 – 28.11.2026 – Brandts Lager mit Wochenbestand, leeres Auftragsbrett, Adventskalender bearbeiten

- **Brandts Lager ohne unbegrenzten Vorrat.** Der Laden `materiallager` (Storyteller-NPC Brandt) wird jetzt aus der
  Config gepflegt (`lager` in `config/twogg-markt/markt.json`): genau die Verkaufsliste der Serverleitung, 107 Waren
  (Rohstoffe, Nether/End, vier Verzauberungsbücher mit echten Verzauberungen, Schallplatten Pigstep/Tears,
  Rüstungsbesätze Rippe/Schnauze/Turmspitze, Bannervorlage Schnauze, alle 16 Farbstoffe, Samen, Baustoffe, alle Stämme
  und Laubsorten inkl. Tanne und Kork). Jede Ware hat einen **Wochenbestand für 42 Spieler** und einen Preis, der zur
  Arbeit passt (Stundenlohn 1,5 WLC): Bruchstein 0,4 je Stack, Diamant 4, Reparatur 25 WLC. Tabelle in
  KONFIGURATION.md.
- **Nachschub jeden Sonntag 06:00 (Europe/Berlin)**, auch über Sommer-/Winterzeit: Jede Ware wird bis zum
  Wochenbestand **aufgefüllt, nicht obendrauf gelegt**. War der Server aus, kommt er einmal beim nächsten Start.
- **Verkaufen an Brandt:** Alles von der Liste, zu etwa 60 % des Verkaufspreises beim gleichen Bestand; das hebt den
  Bestand bis zum Lagerplatz (doppelter Wochenbestand), danach „Lager voll“. Überschuss bleibt beim Nachschub liegen
  (`lager.trimSurplus`). Der Schutz gegen die Geldpumpe aus 0.5.2 gilt unverändert; neu prüft der Server, dass die
  Ankaufstelle je Stück nie mehr zahlt, als Brandt mindestens verlangt (auch Roherz → Barren), und warnt sonst.
- Im Fenster steht beim Wochenbestand genau der Preis aus der Config (Grundpreis wird passend umgerechnet), dahinter
  „Nachschub jeden Sonntag 06:00“. Neu: `/markt lager` (Stand, nächster Nachschub, unbekannte Items),
  `/markt lager abgleich`, `/markt lager nachschub`. Feste Verkaufs-/Ankaufszeilen sind in Brandts Lager gesperrt.
- **Auftragsbrett startet leer:** keine Beispielaufträge mehr (`jobs.pool` ist in der Vorlage leer). Marktaufträge gibt
  es nur noch, wenn Admins Vorlagen eintragen; Spieleraufträge wie bisher.
- **Adventskalender bearbeiten:** Marktverwaltung, neue Seite **Advent** (Coins, Text, Item aus der Hand mit allen
  Daten, Item entfernen, Vorlage, alle neu öffnen) und `/markt advent [liste]`, `item <tag>`, `muenzen <tag> <coins>`,
  `text <tag> <text>`, `leeren <tag>`, `standard <tag>|alle`, `zuruecksetzen <spieler>|alle` (OP 2). Gespeichert in
  `event.adventDays`, neues Feld `stack` (kompletter Stapel).
- **Was beim ersten Start mit 0.6.0 passiert** (einmal, mit Kopie `markt.json.vor-0.6.0` von Einstellungen und
  Weltdaten):
  - Einstellungen bekommen `configVersion: 2` und den Abschnitt `lager` mit der Vorlage; die 16 Beispielvorlagen werden
    aus `jobs.pool` genommen (eigene Vorlagen bleiben). Ankauf, Adventskalender und Regeln bleiben, wie sie sind.
  - Weltdaten: Die Beispiel-Marktaufträge (auf dem Server die vier „m41–m44“) verschwinden vom Brett,
    Spieleraufträge bleiben. In Brandts Lager werden feste Verkaufszeilen (unbegrenzter Vorrat) und feste
    Ankaufszeilen entfernt.
  - Brandts Lager wird angelegt bzw. abgeglichen: alle Waren mit vollem Wochenbestand, nächster Nachschub am
    kommenden Sonntag 06:00. Fehlt eine Mod (Autumnal & Winterly, Arts & Crafts), werden deren Waren übersprungen und
    im Log genannt.
- Technisch: `core/Restock` (Termine, Auffüllen), `core/LagerCheck`, `server/LagerService`, `MarktConfig.migrate`,
  `MarktData.migrate`, `Laden.trimSurplus`, `Laden.Ware.managed`. `beispiele/materiallager.json` mit echten Mod-IDs
  (bleibt Beispiel für eigene Läden). Tests: `RestockTest` (Sonntag, Sommer-/Winterzeit, Lücke und doppelte Stunde,
  Nachholen nach Ausfall), `LagerTest` (Liste, Preise nach Seltenheit, 42 Spieler, Lagerplatz, keine Geldpumpe,
  Ankaufstelle), `MigrationTest` (Server-Stand 0.5.2), `AdventAdminTest`; Spieltest: leeres Brett, Brandts Lager
  (Kauf, Verkauf bis Lagerplatz, Nachschub), Adventskalender aus der Hand und per Befehl.

## 0.5.2+26.1.2 – 27.11.2026 – Geldpumpe im Lager-Laden geschlossen

- **Fix: Geldpumpe bei „Waren mit Lager“.** Kauf wurde zum Bestand vor der Entnahme, Ankauf zum Bestand vor dem
  Einlagern gerechnet – bei kleinen Lagern (z. B. Schallplatten: Lager 2, Soll 1) brachte Kaufen und sofort
  Zurückverkaufen +1,2 WLC je Runde. Jetzt gilt je Einheit der Preis beim Bestand nach dem Handel (Kauf nach der
  Entnahme, Ankauf nach dem Einlagern), auch Stück für Stück in Stapelkäufen. `spread` ist auf höchstens 0,95 begrenzt
  (beim Import, beim Laden der Weltdaten und in der Rechnung). Rundung auf 0,1 immer zugunsten des Ladens: Verkauf
  aufgerundet (bisher kaufmännisch), Ankauf abgerundet. Folge: günstige Waren kosten oft 0,1 mehr als bisher, kleine
  Lager beim Soll spürbar mehr. Fenster und `/markt laden info` zeigen den Preis der nächsten Einheit.
- **Fix: NaN/unendlich beim Import** (`spread`, `preis`) wird abgelehnt statt übernommen; fehlerhafte Einträge werden
  übersprungen und gemeldet. NaN im `spread` der Weltdaten wird beim Laden ersetzt; ein Fehler beim Schreiben von
  `markt.json` bricht den Server-Tick nicht mehr ab (wird geloggt).
- **Fix: Lockvogel an der Theke.** Der Kauf schickt den angezeigten Preis und die Stückzahl mit; ist das Angebot
  inzwischen teurer oder kleiner, bricht der Server ab und zeigt das neue Angebot. Ältere Clients schicken nichts mit
  und sind weiter ungeschützt (kein Protokollwechsel, sie funktionieren weiter).
- **Ankauf-Vorlage:** Fichtenholz 8 für 0,1 (bisher 0,2), Kohle 8 für 0,1 (bisher 4 für 0,2), Wolle 16 für 0,3
  (bisher 4 für 0,3) – nicht mehr als das Materiallager mindestens verlangt. Gilt nur für neu angelegte
  `config/twogg-markt/markt.json`.
- Tests: kein Gewinn aus Kauf/Rückverkauf und Verkauf/Rückkauf für jeden Bestand 0 … Lager und 1 … 64 Einheiten aller
  Waren aus `beispiele/`, Zufallsfolgen, Rundung, `spread`-Grenze, Ankauf-Vorlage gegen Materiallager, NaN beim Laden
  und Speichern, Theken-Prüfung.

## 0.5.1+26.1.2 – 24.11.2026 – Auftragsbrett oben klickbar

- **Fix: Auftragsbrett nur unten klickbar.** Das Modell ist zwei Blöcke hoch, Minecraft prüft Klicks aber nur im
  eigenen Blockfeld. Über dem Brett steht jetzt ein unsichtbarer Aufsatz (`twogg_markt:job_board_top`, ohne Item und
  Drop) mit Umriss für Pfosten, Brett, Hakenleiste und Dach; Rechtsklick dort öffnet das Brett darunter. Wird das Brett
  entfernt, verschwindet der Aufsatz; Abbauen des Aufsatzes (Kreativ) baut das Brett mit ab.
- Neu gesetzte Bretter bekommen den Aufsatz sofort (Platz darüber nötig). **Bereits stehende Bretter** werden beim
  ersten Rechtsklick (unten) nachgerüstet, wenn darüber Luft ist.
- Spieltest: Aufsatz vorhanden, mit Umriss, und ein Klick auf die obere Hälfte öffnet das Auftragsbrett.

## 0.5.0+26.1.2 – 23.11.2026 – Waren mit Lager und Preis nach Bestand

- **Waren mit Lager** im Laden: Grundpreis je Einheit, Lagerplatz, Sollbestand. Preis = Grundpreis × 2^((Soll − Bestand)
  / Lager · 2) – leer doppelt, voll halb; jede Einheit einzeln gerechnet. Ankauf = Preis × `spread` (0,6).
- **Lager voll = kein Ankauf** mehr; Geld des Händlers unbegrenzt. **Wöchentliche Lieferung** (Standard Montag 06:00,
  unsichtbar) setzt alle Waren auf Soll – auffüllen und Überschuss weg.
- `/markt laden ware|import|bestand|lieferung|lieferzeit`; Import aus JSON mit Item-ID oder vollem Stapel (Daten wie
  Verzauberungen), unbekannte Items werden gemeldet. Beispiel `beispiele/materiallager.json` (109 Waren).
- `StockMath` mit Tests; Spieltest: Import inkl. Verzauberungsbuch, Ankauf bis Lager voll, Preis fällt, Lieferung.
## 0.4.0+26.1.2 – 23.11.2026 – Server-Laden für NPCs

- **Laden**: feste Preise, unbegrenzter Vorrat, Verkauf und Ankauf (mit Tagesgrenze je Spieler), Fenster mit den Seiten
  Kaufen/Verkaufen (`LadenScreen`). Kein Block: `/markt laden oeffnen <id> <spieler>` – z. B. aus einer Storyteller-Seite
  („NPC angesprochen“ → Befehl als Konsole), so wird jeder Storyteller-NPC mit eigenem Skin zum Händler.
- Verwaltung per `/markt laden neu|verkauf|ankauf|info|liste|entfernen|untertitel|auf|zu|loeschen`; Ware aus der Hand
  oder per Item-ID. Daten in `markt.json` → `laeden`.
- Spieltest: Laden anlegen, per Konsole öffnen, 4 Fackeln kaufen, Kohle bis zur Tagesgrenze verkaufen.
## 0.3.0+26.1.2 (22.11.2026)

- **Markt-Handbuch:** eigenes Buch (Item `twogg_markt:handbuch`) mit Doppelseiten, Ledereinband und Pergament:
  Titelseite, anklickbares Inhaltsverzeichnis, farbige Lesezeichen je Kapitel, acht Kapitel (Willkommen, Stand, Theke,
  Geld ohne Stand, Bezahlen, Weihnachtsmarkt, Befehle, Tipps) mit 14 Bildern aus dem Spiel (Klick vergrößert).
  Blättern mit Pfeilen, Tasten oder Mausrad, Seitenumblättern-Klang. Zahlen kommen live aus der Server-Config. Beim
  ersten Betreten einmal ins Inventar (`guide.giveOnJoin`), sonst `/markt handbuch` oder Kreativ-Tab.
- **Neue Modelle** für alle Markt-Blöcke, detailliert im Blockbench-Stil mit eigenen Texturen (32 Texel je Block):
  Verkaufstheke mit Auslage (Apfelkiste, Lebkuchen, Preisschild), Tannengirlande und kleiner rot-weißer Markise mit
  Lichterkette; Standschild mit Goldrahmen, Schneedach, Tannenzweig und Laterne; Ankaufstelle mit Messingwaage,
  Holzkiste, Getreidesack und Preistafel unter einem Schneedach; Auftragsbrett als Pinnwand mit Zetteln, Nadeln,
  Kopfschild „AUFTRÄGE“, Girlande und Satteldach; Losbude als kleine Bude mit Losrad, Losglas und gestreiftem Dach;
  Adventskalender als Holzhäuschen für die Wand mit 24 nummerierten Türchen, Schneedach, Eiszapfen und Kamin;
  verstecktes Geschenk mit Schleife, Anhänger und kleinem Zweitgeschenk. Leuchtende Lichter/Laternen auch nachts,
  passende Item-Ansichten (GUI, Hand, Rahmen) und Partikel. Kollision und Umriss folgen den Modellen.
- Erzeugt von `tools/modelle.py` (deterministisch; prüft auf Z-Fighting, ganze Texel, doppelte Elemente,
  22,5°-Rotationen, Grenzen −16…32), Vorschau ohne Spiel mit `tools/modell_vorschau.py`. Ersetzt `tools/textures.py`.
- Spieltest-Bilder sind jetzt sauber (keine Meldungen/Chat, Weltbilder ohne HUD) – daraus macht
  `tools/handbuch_bilder.py` die Bilder des Handbuchs. Kulisse ist eine verschneite Weihnachtsmarkt-Gasse
  (`MarktKulisse`), am Ende eine Abnahme aller Modelle in verschiedenen Ausrichtungen bei Tag und Nacht.
- Technisch: `content/guide/` (`HandbookLayout` setzt Text und Bilder mit der echten Schrift auf die Seiten,
  `GuideBook`, `GuideService`), `client/HandbookScreen`, Ansicht `handbuch` mit den Werten, Weltdaten `guideGiven`,
  Unit-Test `HandbookLayoutTest`, Spieltest `HandbuchClientTest`.

## 0.2.2+26.1.2 (18.11.2026)

- Behoben: Nach einer Auszahlung (Standkasse, Ankauf, Aufträge, Geschenke, Gutschriften beim Login) wird der Markt
  sofort gespeichert. Vorher lag das Geld schon bei der Bank, der Markt merkte sich die geleerte Kasse aber erst beim
  nächsten Speichern (alle 30 s) – nach einem Absturz dazwischen wurde dieselbe Kasse ein zweites Mal ausgezahlt.
- Behoben: Ist `config/twogg-markt/markt.json` fehlerhaft, liegt jetzt eine Kopie `markt.json.broken-<Zeit>` daneben.
  Speichern im Admin-Fenster hätte die Handarbeit sonst mit den Standardwerten überschrieben.
- Behoben: „Kasse leeren“ an der Theke meldete „bar ausgezahlt“, obwohl Bargeld für Stände abgeschaltet war oder der
  Betrag nur vorgemerkt wurde; ist Bargeld aus, geht der Betrag jetzt aufs Konto.
- Spieltest startet ein Kit-Event (seit Economy 0.3 bucht die Bank nur in einem laufenden Event).
- Gebaut gegen TwoGG Kit 1.4.2.

## 0.2.1+26.1.2 (15.11.2026)

- Behoben: Hitboxen von Standschild, Auftragsbrett und Adventskalender drehen jetzt mit der Blickrichtung (vorher
  immer Nord-Ausrichtung, quer zum Modell).
- Entfernt: Wichtelbaum und Lichterkette. Wichteln läuft jetzt an der **Losbude** (zweite Seite „Wichteln“).
  Bereits gesetzte Blöcke verschwinden beim Laden der Welt.
- Behoben: Ein verspätetes „Fenster zu“ konnte die Sitzung eines gerade neu geöffneten Fensters desselben Typs
  beenden (Aktionen wurden dann still abgewiesen). Fenster haben jetzt eine Sitzungsnummer.

## 0.2.0+26.1.2 (15.11.2026)

**Hologramme und Weihnachtsmarkt-Event**

- Hologramme über jedem Stand (Vanilla-TextDisplay, für alle sichtbar): Name, Status, Preis bzw. Besitzer und
  Angebote, im Event die Stimmen.
- Event mit Öffnungszeiten oder Start per Befehl/Verwaltung: Eröffnung mit Titel und Glocken, Bossbar, Ankündigungen,
  Siegerehrung, Abbruch mit Rückgabe.
- Mitmachen: Losbude, Wichtelbaum, Geschenkesuche, Adventskalender (neue Blöcke).
- Wettbewerb: Abstimmung schönster Stand, Bestenlisten (Umsatz, Aufträge, Räumdienst, Geschenke), Preise für Platz 1–3,
  Feuerwerk, Bestenliste-Tafel als Hologramm.
- Atmosphäre: Weihnachtslieder als Glockenspiel, TwoGG-Atmos-Preset im Marktgelände, Schneefall, Lichterkette.
- Neue Seiten „Event“ in Standübersicht und Marktverwaltung, Regel „Hologramme“.
- 22 JUnit-Tests, Client-Test mit 32 Screenshots.

## 0.1.0+26.1.2 (15.11.2026)

Erste Fassung für den Weihnachtsmarkt auf Winterland.

- Stände mit dem Marktplaner einzeichnen, Umrisse nach Status, Leerzustand sichern und zurücksetzen.
- Mieten pro Zeitraum (Standard 1 Tag), verlängern, kündigen mit Teil-Erstattung, kaufen und zurückgeben; Preise in €
  zum Wertkurs der Economy; Warnungen vor Ablauf, Freigabe mit Auszahlung der Kasse.
- Bauschutz in Ständen, Helfer pro Stand.
- Verkaufstheke: Angebote aus der Hand, Verkauf aus Lagerkisten im Stand, Konto oder bar, Marktgebühr, Standkasse.
- Nebenverdienste: Ankauf mit fallendem Tagespreis, Auftragsbrett mit Markt- und Spieleraufträgen (Treuhand,
  Abholfach), Räumdienst in Räumzonen.
- Vorgemerkte Auszahlungen für Spieler, die offline sind oder keinen aktiven Charakter haben.
- Marktverwaltung im TwoGG Kit (Toolbox-Kachel „Markt“) und `/markt`-Befehle.
- 13 JUnit-Tests, Client-Test im echten Spiel mit 23 Screenshots.
