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
./gradlew build          # build/libs/twogg-markt-0.1.0+26.1.2.jar (+ -sources.jar)
```

Neue Economy-Version: Jar aus `../twogg-economy/build/libs/` nach `libs/` kopieren und `economy_version` in
`gradle.properties` anpassen. Zur Laufzeit muss die Economy als eigene Mod im `mods`-Ordner liegen.

## Tests

```
./gradlew test
```

`MarketMathTest` (13 Tests): €→Coins beim Kurs, Verlängern, buchbare Zeiträume, Erstattung, Gebühr, Ankaufpreis und
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
einer fremden Theke und blättert durch alle Seiten der Marktverwaltung. Jeder Schritt wird auf dem Server geprüft
(Kontostände, Bargeld, Kasse, Inventare). 23 Screenshots unter `build/run/clientGameTest/screenshots/`.

Kit-Knöpfe sind `AbstractButton`; der Test drückt sie über eine eigene Hilfe (`press`), Eingabefelder findet er über
den Hinweistext (`type`).

## Texturen

```
python3 tools/textures.py
```

Erzeugt alle 16×16-Texturen und `icon.png` deterministisch (Pillow). Modelle, Blockstates und Sprachdateien liegen
unter `src/main/resources/assets/twogg_markt/`.

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
