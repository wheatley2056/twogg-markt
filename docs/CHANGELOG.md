# Changelog: TwoGG Markt


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
