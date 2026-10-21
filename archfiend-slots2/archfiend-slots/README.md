# Archfiend Slots

Clientseitige Fabric-Mod für **Minecraft 26.1.2** (Hypixel SkyBlock). Ersetzt die Archfiend-Dice-Optik durch ein
Slotmaschinen-Overlay. Rein kosmetisch: keine Mixins, keine Netzwerkpakete, keine Servermod, ändert nie Würfelergebnis,
Coins oder Items.

## Verifizierungsstatus (ehrlich)

| Punkt | Status |
|---|---|
| Reine Logik (Parser, Zustandsautomat, Timeline, Beträge, Renderer-Logik) | **Getestet**: `SelfTest`, 336 Checks, Java 21 |
| Overlay-Optik | **Headless gerendert** (PNG-Vorschau über `PreviewMain`) und angesehen, nicht im Spiel |
| Gradle-Build gegen Minecraft 26.1.2 | **NICHT ausgeführt** (Build-Umgebung hatte weder Fabric-Maven noch JDK 25) |
| Minecraft-/Fabric-API-Aufrufe | **Nicht gegen echte 26.1.2-Klassen kompiliert**; nur gegen eigene Stubs auf Tippfehler geprüft |
| Start im Spiel, Hypixel-Test | **Nicht durchgeführt** |
| Echte Hypixel-Nachrichtenformate | **Unbekannt** (siehe unten) |

Alle Minecraft-Aufrufe liegen in 5 kleinen Dateien (`client/`). Falls der Build meckert, sind nur dort Namen anzupassen.

## Build

Voraussetzung: JDK 25.

```
gradle wrapper --gradle-version 9.4.0     # einmalig, erzeugt gradlew
./gradlew build                           # JAR: build/libs/archfiend-slots-1.0.0.jar
./gradlew selfTest                        # Logiktests
```
Versionen in `gradle.properties` (Loader 0.19.5, Fabric API 0.155.3+26.1.2, Loom `1.15-SNAPSHOT`). Bei Loom-Fehlern auf die
aktuelle Version von <https://fabricmc.net/develop/> wechseln.

## Installation
JAR plus Fabric API in `.minecraft/mods`, Fabric Loader ≥ 0.19.5, Java 25.

## Testen ohne Hypixel (Demo)
Im Spiel (Welt/Server beitreten):
```
/archfiendslots demo normal 4      normale Zahl (1-5)
/archfiendslots demo normal 6      Gold-Jackpot (+15,000,000)
/archfiendslots demo high 6        Gold-Jackpot (+100,000,000)
/archfiendslots demo normal 7      Archfiend-Dye-Jackpot
```
Die Demo ist deutlich mit „DEMO - NOT A REAL ROLL“ beschriftet und ignoriert alle Servernachrichten.

## Wie das Ergebnis erkannt wird
1. **Item:** `UseItemCallback` (nur lokaler Spieler) liest `custom_data.id`; exakt `ARCHFIEND_DICE` oder
   `HIGH_CLASS_ARCHFIEND_DICE`. Namens-Fallback nur ohne jede ID und standardmäßig aus.
2. **Start:** Overlay startet sofort, Walzen drehen, **Stop-Kommandos gibt es erst mit bestätigtem Ergebnis**.
3. **Ergebnis:** Chat-/Actionbar-Zeilen (`ClientReceiveMessageEvents.GAME`) werden nur ausgewertet, solange ein Wurf
   wartet. Akzeptiert wird eine Zeile nur, wenn sie kein Spielerchat ist (`Name: …`, `[Rang] Name: …`, Guild/Party/DM),
   eine „rolled … N“-Phrase mit genau einer Zahl 1-7 enthält und keine weitere Einzelziffer 1-7 (sonst mehrdeutig → weiter
   warten). Eine 7 kann zusätzlich über „rolled … Archfiend Dye“ erkannt werden (nicht bei Markt-Wörtern).
   Gesundheits- oder Coin-Zeilen allein zählen **nie**.
4. **Einmalig:** Ergebnis wird nur einmal pro Wurf übernommen. Neuer Wurf während einer Animation wird ignoriert; nach einer
   Ablehnung/Normalende 0,4 s, nach Timeout 2,5 s Sperre, damit späte Zeilen nie dem nächsten Wurf zugeordnet werden.
5. **Ablehnung** („not enough coins“, „cooldown“…) bricht still ab. **Timeout** (Standard 5 s): neutrale Walzen,
   „RESULT NOT DETECTED“, nie ein Gewinn. Spätere Zeilen lösen nichts aus.
6. Disconnect/Join/Weltverlust: alles sofort abgebrochen.

### ⚠ Wichtigste offene Frage
Der genaue Hypixel-Wortlaut der Würfelnachrichten ist **nicht dokumentiert** (Wiki nennt keine Texte; auch öffentliche
Mods führen 6/7 und High Class als unbestätigt). Die Standardmuster sind Vermutungen. Wenn bei dir „RESULT NOT DETECTED“
erscheint:
1. `/archfiendslots capture true`, würfeln, in `logs/latest.log` nach `[pending roll #` suchen.
2. In `config/archfiendslots.json` unter `extraResultPatterns` ein Regex eintragen, Gruppe 1 = Zahl 1-7, z. B.
   `"^Your dice rolled a ([1-7])!$"`; ggf. `extraRejectPatterns`. Dann `/archfiendslots reload`.

## Konfiguration (`config/archfiendslots.json`)
`enabled`, `durationSeconds` (2.0-3.0), `soundEnabled`, `soundVolume`, `particlesEnabled`, `screenShakeEnabled`,
`overlayScale` (0.5-2.0), `archfiendDiceEnabled`, `highClassArchfiendDiceEnabled`, `resultTimeoutMs`,
`allowNameFallback`, `debugLogMessages`, `extraResultPatterns`, `extraRejectPatterns`.
Fehlende Datei/Keys → Defaults; defekte Datei → `.broken`-Sicherung + Defaults. Keine Config-GUI (JSON ist dokumentiert).

## Dauer
Normal 3,0 s (Walzen stoppen bei 1,2 / 1,8 / 2,3 s), Gold 3,3 s, Dye 3,5 s. Verspätetes Ergebnis verschiebt die Stopps
(Animation wird entsprechend länger, max. bis Timeout).

## Architektur
```
core/    reine Logik, kein Minecraft: DiceType, Settings, RollResultParser, RollSession (Timeline), RollController (Zustand)
render/  reine Zeichenlogik über Interface Canvas: SlotMachineRenderer, WinEffectRenderer, SlotReel, ParticleSystem, Sprites
client/  dünne MC-Schicht: ArchfiendSlotsClient, Gfx (Canvas -> GuiGraphicsExtractor), DiceDetector, SoundController, ConfigManager
test/    SelfTest (Logik), PreviewMain + ImageCanvas (PNG-Vorschau)
```
Entscheidungen: keine Mixins (Fabric-Events reichen); Pixel-Art im Code statt Texturen (keine Textur-API, nichts zu
laden/freigeben); Animation = reine Funktion der Zeit (`System.nanoTime`), daher FPS-unabhängig; feste Partikelpools
(max. 96), nichts bleibt nach dem Ende; kein Input wird abgefangen, `UseItemCallback` gibt immer `PASS` zurück;
Renderfehler schalten das Overlay ab statt zu crashen. Sounds: vanilla UI-Sounds, abschaltbar.

## Hypixel-Regeln / Risiko
Nicht geprüft und nicht garantiert. Die Mod liest nur Chat/Itemdaten, die der Client ohnehin hat, sendet nichts und
automatisiert nichts, ist aber trotzdem eine Mod; Nutzung auf eigene Verantwortung und vorher die aktuellen Hypixel-Regeln
prüfen.

## Bekannte Einschränkungen
- Wortlaut der Ergebnisnachrichten unbestätigt (s. o.); bei Abweichung neutrales Ende statt Gewinn.
- Ein zweiter Wurf während einer laufenden Animation wird nicht visualisiert.
- Eine Nachricht vom Server, die eine Ablehnung wie ein Fehler formuliert, die nicht in den Mustern steht, führt zum Timeout.
- Dye-Grafik ist eine eigene Stilisierung, nicht die Originaltextur.
- Sounds nutzen vanilla-Ereignisse; Lautstärke zusätzlich über Minecraft-Master/Ui-Regler.

## JAR ohne lokale Installation bauen (GitHub Actions)
1. Neues GitHub-Repository anlegen, den **Inhalt** dieses Ordners hochladen (inkl. `.github/`).
2. Reiter **Actions** → „Build mod“ läuft automatisch (oder „Run workflow“).
3. Fertigen Lauf öffnen → unter **Artifacts** `archfiend-slots-jar` herunterladen. Darin liegt die JAR.
Schlägt der Lauf fehl, bitte das Log schicken; die Fehler betreffen dann nur die MC-Aufrufe in `client/`.
