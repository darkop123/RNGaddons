# RNGaddons

Clientseitige Fabric-Mod für **Minecraft 26.1.2** (Hypixel SkyBlock): RNG-lastige Grinds sollen mehr Spaß machen.
Rein kosmetisch: keine Mixins, keine Netzwerkpakete, ändert nie Ergebnisse, Coins oder Items.

## Funktionen
| Funktion | Was sie macht |
|---|---|
| **Archfiend Dice** | Ersetzt den Würfelwurf von Archfiend Dice und High Class Archfiend Dice durch einen Slotmaschinen-Overlay. 6 → Gold-Jackpot (+15M / +100M), 7 → Archfiend-Dye-Jackpot, 1–5 → neutrale Walzen. Angezeigt wird nur das vom Server bestätigte Ergebnis. |

Weitere Funktionen kommen jeweils als eigener Ordner unter `features/` und als eigener Abschnitt im Menü dazu.

## Installation
JAR plus **Fabric API** in den `mods`-Ordner (Fabric Loader ≥ 0.19.5, Java 25). **Mod Menu** ist optional
und fügt den Zahnrad-Knopf hinzu. Die alte `archfiend-slots-1.0.0.jar` muss raus, sonst startet das Spiel mit einem
Hinweis nicht. Deine alten Einstellungen werden beim ersten Start automatisch übernommen.

## Menü und Befehle
- `/rngaddons` öffnet das Menü (oder **Mods → RNGaddons → Zahnrad**). Aufbau wie bei SkyHanni: links Abschnitte pro
  Funktion, rechts Karten mit Titel, Beschreibung und Schalter. Änderungen wirken sofort und werden gespeichert.
- `/rngaddons archfiend demo <normal|high> <1-7>` – lokale Vorschau (deutlich als „DEMO“ beschriftet)
- `/rngaddons archfiend capture <true|false>` – Chatzeilen während eines Wurfs in `latest.log` schreiben
- `/rngaddons archfiend status` – Zustand anzeigen (zur Fehlersuche)
- `/rngaddons reload` – `config/rngaddons.json` neu laden

## Verifizierungsstatus
| Punkt | Status |
|---|---|
| Logik (Parser, Zustandsautomat, Timeline) | `SelfTest`, 359 Checks |
| Echte Hypixel-Zeilen 1–5, „Nice!“-6, High Class, „You do not have enough coins…“ | in den Tests, im Spiel gesehen |
| Wortlaut einer echten 7 (Dye) | **noch nicht bestätigt** |
| Menü (1.1.0) | gegen die offiziellen 26.1.2-Docs geschrieben, im Spiel noch nicht getestet |

## Wie das Ergebnis erkannt wird (Archfiend Dice)
1. **Start:** Rechtsklick mit einem Würfel (Item-ID `ARCHFIEND_DICE` / `HIGH_CLASS_ARCHFIEND_DICE`) startet die
   Walzen sofort. Fällt der Klick aus, startet die Zeile „Your … Archfiend Dice rolled a N!“ die Animation selbst.
2. **Ergebnis:** nur aus Serverzeilen, nie aus Spielerchat, nur bei genau einer Zahl 1–7; sonst neutral.
3. **Ablehnung** („You do not have enough coins…“, Cooldown) bricht still ab; **Timeout** endet neutral, nie als Gewinn.
4. Während einer laufenden Animation wird ein weiterer Wurf nicht angezeigt (damit nichts falsch zugeordnet wird).

## Projektaufbau
```
com/rngaddons/
  RNGaddonsClient.java        Einstieg: Config laden, Funktionen starten, /rngaddons
  ModMenuIntegration.java     Zahnrad in Mod Menu
  config/                     ModConfig (ein Abschnitt pro Funktion), ConfigManager, ConfigScreen (Menü)
  features/archfiend/         Archfiend Dice: Feature-Klasse, DiceDetector, Gfx, SoundController
    core/   reine Logik (kein Minecraft)      render/   Slotmaschine, Effekte (kein Minecraft)
```
Eine neue Funktion: Ordner `features/<name>/` anlegen, in `ModConfig` ein Feld ergänzen, in `RNGaddonsClient`
starten und in `ConfigScreen.PAGES` eine Seite dazuschreiben.

## Bauen (GitHub Actions)
Jeder Upload baut automatisch: **Actions** → letzter Lauf → **Artifacts** → `rngaddons-jar`.
Lokal: `gradle selfTest` und `gradle build -x test` (JDK 25, Gradle 9.4).

## Neue Version veröffentlichen
1. In `gradle.properties` `mod_version` erhöhen (Bugfix 1.1.0 → 1.1.1, neue Funktion → 1.2.0, großer Umbau → 2.0.0).
2. Hochladen und warten, bis der Build grün ist.
3. **Releases → Draft a new release** → Tag `v1.2.0` (muss zur Version passen) → Titel und Änderungen →
   **Publish release**. GitHub Actions baut dann die JAR und hängt sie nach 1–2 Minuten automatisch an.

## Hypixel-Regeln
Nicht geprüft und nicht garantiert. Die Mod liest nur, was der Client ohnehin hat, sendet nichts und automatisiert
nichts; Nutzung auf eigene Verantwortung, aktuelle Hypixel-Regeln beachten.
