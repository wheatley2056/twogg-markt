# Changelog: TwoGG Markt

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
