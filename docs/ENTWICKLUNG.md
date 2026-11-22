# Entwicklung: TwoGG Markt

## Voraussetzungen

| Werkzeug | Version |
|---|---|
| JDK | 25 |
| Gradle | Wrapper 9.9.0 |
| Fabric Loom | 1.19.4 (`net.fabricmc.fabric-loom`) |
| Minecraft | 26.1.2 |
| Fabric Loader / API | 0.20.1 / 0.155.3+26.1.2 |
| TwoGG Kit | 1.3.1+26.1.2 (`mavenLocal()`) |
| TwoGG Economy | 0.2.4+26.1.2 als Jar in `libs/` |
| JUnit | 5.11.4 |

`splitEnvironmentSourceSets()`: Server- und gemeinsamer Code in `src/main`, Client in `src/client`, Client-Test in
`src/gametest`.

## Bauen

```
cd ../twogg-kit && ./gradlew publishToMavenLocal
cd ../twogg-markt
./gradlew build          # build/libs/twogg-markt-0.2.2+26.1.2.jar (+ -sources.jar)
```

Neue Economy-Version: Jar aus `../twogg-economy/build/libs/` nach `libs/` kopieren und `economy_version` in
`gradle.properties` anpassen. Zur Laufzeit muss die Economy als eigene Mod im `mods`-Ordner liegen.

## Tests

```
./gradlew test
```

`MarketMathTest` (13 Tests) und `EventTest` (9 Tests: Öffnungszeiten mit Wochentagen, Datum, über Mitternacht und
Fehlern, gewichtete Losziehung, Wichteln ohne eigenes Geschenk, Rangliste, Melodien im Notenblock-Bereich,
Event-Zustand, Adventskalender-Vorlage). `MarketMathTest`: €→Coins beim Kurs, Verlängern, buchbare Zeiträume, Erstattung, Gebühr, Ankaufpreis und
Abklingen, Ziehen der Marktaufträge, Formatierung, `Box`, Status und Rechte eines Stands, `sanitize` von Einstellungen
und Weltdaten.

### Client-Test im echten Spiel

```
xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest
```

`MarktClientTest` baut eine Fläche, zeichnet zwei Stände ein (wie mit dem Planer), stellt ein Standschild auf und
öffnet es per Rechtsklick, mietet über die Oberfläche, legt an der Theke ein Angebot aus der Hand an, lässt einen
zweiten Spieler (FakePlayer) kaufen, leert die Kasse, prüft den Bauschutz (Fremder kann im Stand nicht abbauen,
außerhalb schon), verkauft am Ankauf, liefert einen Marktauftrag, hängt einen eigenen Auftrag aus, lässt ihn vom zweiten
Spieler erledigen, holt die Ware ab, räumt Schnee in einer Räumzone, lässt die Miete ablaufen, kauft als Kunde bar an
einer fremden Theke, prüft die Hologramme über beiden Ständen, startet ein Event (Losbude über die Oberfläche, zweiter
Spieler kauft Lose, Geschenk finden, Wichtelgeschenke von beiden über die Losbude, Abstimmen, Adventskalender, Event-Seite,
Bestenliste-Tafel) und prüft die Siegerehrung (Topf ausgeschüttet, Wichtelgeschenke getauscht, Preise auf dem Konto
bzw. vorgemerkt). Außerdem: Hitbox des Auftragsbretts dreht mit der Blickrichtung. Danach alle Seiten der Marktverwaltung. Jeder Schritt wird auf dem Server geprüft
(Kontostände, Bargeld, Kasse, Inventare). Gebaut wird in einer verschneiten Weihnachtsmarkt-Gasse (`MarktKulisse`:
Schneebiom, Buden aus Fichte, Laternen, Tannen, Hütte mit Adventskalender), damit die Weltbilder fürs Handbuch
taugen. Zum Schluss die **Abnahme der Modelle**: ein Schauraum mit allen Blöcken in verschiedenen Ausrichtungen,
Bilder bei Tag und Nacht aus drei Blickwinkeln, Nahaufnahmen und die Items im Inventar (`abnahme-*`).
Screenshots unter `build/run/clientGameTest/screenshots/`.

Kit-Knöpfe sind `AbstractButton`; der Test drückt sie über eine eigene Hilfe (`press`), Eingabefelder findet er über
den Hinweistext (`type`).

## Modelle und Texturen

```
python3 tools/modelle.py                          # alle Modelle, Texturen, Planer-Item, icon.png
python3 tools/modelle.py --vorschau /tmp/vorschau # dazu Vorschaubilder (Tag/Nacht, mehrere Blickwinkel)
python3 tools/modell_vorschau.py market_counter -o bild.png --yaw 35 --pitch 25
```

`tools/modelle.py` baut jedes Blockmodell aus Quadern mit einem Material je Fläche (Holz, Balken, Lack, Markise,
Metall, Schnee, Tanne, Stoff, Papier, Kork …, dazu Bilder wie Schild, Tafel, Türchen, Losrad) und schreibt
`models/block/<name>.json`, den Atlas `textures/block/<name>.png` (32 Texel je Block, jede sichtbare Fläche einzeln
gemalt, Muster in Modellkoordinaten, damit Fugen, Streifen und Bänder über Kanten weiterlaufen) und
`textures/block/<name>_partikel.png`. Verdeckte Flächen fallen automatisch weg. Danach wird geprüft und bei Fehlern
abgebrochen: Z-Fighting (gleich ausgerichtete, überlappende Flächen in einer Ebene), UVs auf ganzen Texeln
(Koordinaten im 0,5-px-Raster), doppelte Elemente, Rotationen nur ±22,5°/±45° um eine Achse, alles in −16…32.
Die Item-Ansicht (`display`) wird aus der Ausdehnung des Modells berechnet (GUI eingepasst und zentriert).
Leuchtende Teile (Lichterketten, Laternen) haben `light_emission: 15`.

Die Modelle zeigen mit der Vorderseite nach Norden; die Blockstates drehen sie, `MarktBlock` dreht die Formen aus
`ModContent` passend. Teile über dem Blockfeld (Markise, Dächer) haben keine Kollision.

Nach Änderungen an Modellen: Spieltest laufen lassen (Abnahme-Bilder `abnahme-*` und Kulisse prüfen), dann
`python3 tools/handbuch_bilder.py` für die Handbuch-Bilder.

## Konventionen (wie in den anderen TwoGG-Mods)

- Beträge als `long`-Zehntel, Preise für Stände in €, Umrechnung über `MarketMath.priceTenths` (aufgerundet).
- Texte für Spieler fest auf Deutsch im Code; Sprachdateien nur für Blöcke, Items und den Tab.
- JSON über Gson; Netzwerkpakete sind JSON-Strings.
- Geld nur über `de.twogg.economy.api.Economy`, nie direkt am Hauptbuch.

## Release

1. `mod_version` in `gradle.properties` erhöhen (`<version>+26.1.2`).
2. `./gradlew build test`, Client-Test laufen lassen.
3. Abschnitt in `docs/CHANGELOG.md`.
4. Jar an Server und alle Clients verteilen (zusammen mit Kit und Economy).
