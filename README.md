# TwoGG Markt

Weihnachtsmarkt für TwoGG-Events (Fabric, Minecraft 26.1.2, Server und Client). Admins zeichnen **Stände** ein,
Spieler **mieten** sie für einen Zeitraum oder **kaufen** sie und verkaufen an ihrer **Verkaufstheke** an andere
Spieler. Dazu kommen Nebenverdienste neben der eigentlichen Arbeit auf Winterland: **Ankauf**, **Auftragsbrett** und
**Räumdienst**. Bezahlt wird mit [TwoGG Economy](../twogg-economy) (Konto oder Bargeld, Preise folgen dem Wertkurs).

Aktuelle Version: **0.2.2+26.1.2**

## Features

**Seit 0.2.0:** Hologramme über jedem Stand und ein eigenes **Weihnachtsmarkt-Event** (siehe unten).

- **Stände einzeichnen:** Mit dem **Marktplaner** zwei Ecken setzen (Linksklick, Rechtsklick), dann in der
  Marktverwaltung „Stand anlegen“ oder `/markt stand neu <Name>`. Hält ein Admin den Planer, sieht er alle Stände als
  farbige Umrisse (grün frei, gelb vermietet, blau verkauft, rot gesperrt). Beim Anlegen wird der Leerzustand gesichert.
- **Mieten oder kaufen:** pro Stand einstellbar (nur Miete, nur Kauf, beides, gesperrt). Miete pro Zeitraum (Standard:
  1 Tag Echtzeit), bis zu *n* Zeiträume im Voraus, verlängern jederzeit. Preise in €, die Bank rechnet sie zum Tageskurs
  in Coins um. Kündigen erstattet einen Teil der nicht angefangenen Zeiträume, gekaufte Stände kauft der Markt zurück.
- **Ablauf:** Warnung eine Stunde und zehn Minuten vorher, danach ist der Stand wieder frei. Die Standkasse geht an den
  Besitzer (auch offline: beim nächsten Login). Auf Wunsch wird der Stand auf den Leerzustand zurückgesetzt.
- **Bauschutz:** In vergebenen Ständen bauen, abbauen und Kisten öffnen nur Besitzer, Helfer und Admins. Freie Stände
  sind ebenfalls geschützt (abschaltbar). Außerhalb der Stände greift die Mod nicht ein.
- **Verkaufstheke:** Ware in Kisten, Fässer oder Shulkerkisten im Stand legen, an der Theke Angebote aus der Hand anlegen
  (Stückzahl und Preis pro Kauf). Käufer zahlen vom Konto oder bar. Eine **Marktgebühr** (Standard 5 %) wird vernichtet,
  der Rest landet in der **Standkasse**. Helfer dürfen mitverkaufen.
- **Ankauf:** Der Markt kauft Holz, Kohle, Gebäck, Fisch, Wolle … Der Preis fällt, je mehr heute schon verkauft wurde
  (alle Spieler zusammen), und erholt sich über den Tag. Dazu ein Tageslimit pro Spieler.
- **Auftragsbrett:** Spieler hängen **eigene Aufträge** aus – die Belohnung wird hinterlegt, die Ware landet im
  Abholfach des Auftraggebers. Dazu auf Wunsch täglich wechselnde **Marktaufträge** aus einem Pool, den Admins füllen
  (seit 0.6.0 ohne Beispielaufträge, das Brett startet leer).
- **Brandts Lager** (seit 0.6.0): Server-Laden für den NPC Brandt mit begrenztem Wochenbestand (für 42 Spieler),
  Preis nach Bestand, Rückkauf bis zum Lagerplatz, Nachschub jeden Sonntag 06:00. Liste und Preise in der Config.
- **Räumdienst:** In Räumzonen (Gassen, Plätze) zahlt der Markt fürs Schneeschippen, bis zum Tageslimit.
- **Hologramme:** Über jedem Stand schwebt Name, Status und Preis (frei) bzw. Besitzer und Angebote (vergeben),
  während des Events auch die Stimmen. Vanilla-Textanzeigen – sichtbar für alle, aktualisieren sich selbst.
- **Weihnachtsmarkt-Event:** Öffnungszeiten oder Start per Befehl, Eröffnung mit Titel und Glocken, Bossbar mit
  Restzeit, Weihnachtslieder (Glockenspiel) und Atmos-Stimmung im Marktgelände, Schneefall. Mitmachen: **Losbude**
  (Lose und Wichteln), **Geschenkesuche**, **Adventskalender** (1.–24. Dezember). Wettbewerb: **schönster Stand**
  (Abstimmung), bester Umsatz, fleißigste Helfer, Räumdienst, Geschenkesuche – mit **Siegerehrung**, Preisen,
  Feuerwerk und einer **Bestenliste-Tafel** als Hologramm.
- **Marktverwaltung** (Kit-GUI, Kachel in der TwoGG Toolbox oder `/markt admin`): Stände, Neu, Ankauf, Aufträge,
  Räumzonen, Regeln. Alles auch per `/markt …`-Befehl.

## Installation

| | |
|---|---|
| Minecraft | 26.1.2 (`~26.1.2`) |
| Fabric Loader | ≥ 0.17.0 (gebaut mit 0.20.1) |
| Fabric API | beliebig (gebaut mit 0.155.3+26.1.2) |
| Java | ≥ 25 |
| **TwoGG Kit** | Pflicht, ≥ 1.3.0 (gebaut gegen 1.3.1+26.1.2) |
| **TwoGG Economy** | Pflicht, ≥ 0.2.3 (gebaut gegen 0.2.4+26.1.2) |
| **TwoGG Server Companion** | Pflicht auf **dedizierten** Servern (`"companion": true`, wie die anderen TwoGG-Mods). Einzelspieler geht ohne. |
| TwoGG Roleplay | optional; dann gelten die Konten pro Charakter der Economy |
| Seite | **Server und alle Clients** |

1. `twogg-markt-0.2.2+26.1.2.jar` zusammen mit Kit, Economy und Fabric API in `mods/` auf dem Server und bei allen
   Spielern ablegen.
2. Beim ersten Start entsteht `config/twogg-markt/markt.json` mit der Weihnachtsmarkt-Vorlage.
3. Die Marktdaten liegen in der Welt unter `<welt>/twogg-markt/`.

## Schnellstart

**Admins** (OP 2):

```
/markt planer                      # Marktplaner holen
#   Linksklick auf einen Block = Ecke 1, Rechtsklick = Ecke 2 (Boden bis Dach des Stands)
/markt stand neu Glühweinbude      # Stand aus der Auswahl anlegen (Leerzustand wird gesichert)
/markt stand miete gluehweinbude 15        # 15 € pro Tag
/markt stand kaufpreis gluehweinbude 400   # 400 € Kaufpreis
/markt zone neu Hauptgasse         # Auswahl als Räumzone (Schneeschippen)
/markt admin                       # Marktverwaltung (oder Toolbox-Kachel „Markt“)
```

Dann aus dem Kreativ-Tab „TwoGG Markt“ **Standschilder** an die Stände, eine **Ankaufstelle** und ein
**Auftragsbrett** auf den Markt stellen.

**Event ausrichten:**

```
/markt gelaende setzen             # Planer-Auswahl = Marktgelände (Musik, Stimmung, Feuerwerk)
/markt event tafel hier            # Bestenliste-Hologramm über dir
/markt event start 120             # Markt für 120 Minuten eröffnen (Siegerehrung danach automatisch)
/markt event stop                  # vorzeitig: Siegerehrung jetzt
```

Dazu **Losbude** (Lose und Wichteln), **Adventskalender** und ein paar **versteckte Geschenke** aus dem Kreativ-Tab
aufstellen. Feste Öffnungszeiten (`event.schedule`, z. B. `"Fr 18:00-21:00"`) starten den Markt automatisch.

**Spieler:** `/markt` öffnet die Standübersicht (mieten, kaufen, eigener Stand, „Verdienen“), ein Rechtsklick aufs
Standschild ebenso. `/markt zeigen` umrandet die Stände in der Nähe für 30 Sekunden.

## Dokumentation

| Datei | Inhalt |
|---|---|
| [docs/BEDIENUNG.md](docs/BEDIENUNG.md) | Spieler und Admins: Stände, Theke, Ankauf, Aufträge, Räumdienst, alle Befehle und Rechte |
| [docs/KONFIGURATION.md](docs/KONFIGURATION.md) | `markt.json` mit allen Feldern, Weltdaten, Leerzustände |
| [docs/ARCHITEKTUR.md](docs/ARCHITEKTUR.md) | Pakete, Geldfluss über die Economy-API, Netzwerk, Schutz, Persistenz |
| [docs/ENTWICKLUNG.md](docs/ENTWICKLUNG.md) | Bauen, Tests, Client-Test im echten Spiel, Texturen |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | Versionsverlauf |

## Wie viel bringt was? (Winterland-Vorlage, Kurs 10 €)

| Quelle | Ertrag |
|---|---|
| Stechuhr (Icebreaker, zum Vergleich) | 1,5 WLC pro Stunde |
| Stand mieten | kostet 1,5 WLC pro Tag (15 €), kaufen 40 WLC (400 €) |
| Ankauf | z. B. 8 Fichtenstämme → 0,2 WLC, 1 Kürbiskuchen → 0,3 WLC; Preis fällt bei viel Verkauf bis auf 30 % |
| Marktauftrag | nur, wenn Admins Vorlagen in `jobs.pool` eintragen (seit 0.6.0 leer) |
| Brandts Lager | Rückkauf zu etwa 60 % des Verkaufspreises, bis der Lagerplatz voll ist (z. B. Diamant 2,3 WLC) |
| Räumdienst | 0,1 WLC je 8 geräumte Schneeblöcke, höchstens 3 WLC pro Tag |
| Eigener Stand | was die Kunden zahlen, minus 5 % Marktgebühr |
| Geschenkesuche (Event) | 0,5 WLC je gefundenem Geschenk |
| Siegerehrung (Event) | 10 / 5 / 2,5 WLC für Platz 1–3 in jeder Kategorie, Losbude: 80 % des Topfs |
| Adventskalender | 0,5–10 WLC oder Items pro Tag (Inhalte in der Marktverwaltung, Seite „Advent“) |

Alle Werte stehen in `config/twogg-markt/markt.json` bzw. pro Stand in der Marktverwaltung.

## Bauen (kurz)

```
cd ../twogg-kit && ./gradlew publishToMavenLocal
cd ../twogg-markt
./gradlew build      # → build/libs/twogg-markt-0.6.0+26.1.2.jar
./gradlew test       # Logik-Kern (MarketMathTest, EventTest)
xvfb-run -a ./gradlew runClientGameTest   # Durchgang im echten Spiel mit Screenshots
```

Die Economy liegt als Jar in `libs/` (wie der Storyteller bei Atmos).

## Lizenz

All Rights Reserved (siehe `fabric.mod.json`). Autor: wheatley, https://twogg.de
