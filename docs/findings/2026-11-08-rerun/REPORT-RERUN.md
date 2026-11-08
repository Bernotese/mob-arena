# Mob Arena – zweiter Real-Client-Test (2026-11-08, main e4c953e)

Setup wie Lauf 1: VM `mobtest`, Fabric 26.3, 3 echte Vanilla-26.3-Clients (Tester1–3). Neu: PulseAudio-Null-Sink, damit die Sounds von Tester1 mitgeschnitten und vermessen werden (nur Tester1 hat Ton an). Build vorher lokal: 144/144 GameTests grün. 3 Spiele, plus ein Server-Stop mitten in ARENA.
Abkürzungen über den Debug-Treiber: Pools füllen, Wellen setzen, Phasen überspringen, `clearwave`. Resistenz und Regeneration halten untätige Spieler am Leben.

## T12-Fixes
| Punkt | Ergebnis |
|---|---|
| Bug 1: auf dem Todesbildschirm über einen Phasenwechsel | **PASS.** Tester2 tot in PREP, kein Klick: bei WAVE_CONFIG lebt er automatisch, in ARENA im Survival-Modus; BLUE wird nicht fälschlich eliminiert. |
| Bug 2: Tod in PREP → Team-Oberwelt | **PASS** (Tod im Team-End und im Team-Nether → Team-Oberwelt). Hinweis: Der End-Respawn aus Lauf 1 war sehr wahrscheinlich ein Fehler meines Testaufbaus. Ich hatte den End-Portal-Block genau auf den PREP-Spawn gesetzt, also respawnte der Spieler direkt in einem Portal. Das passt zur Notiz des Orchestrators „Ursache nicht bewiesen“. |
| Todesnachrichten an alle | **PASS** (BLUE sieht Tode von RED und umgekehrt, auch im Server-Log) |
| „Go“-Titel | **PASS** (2, 1, „Go / Wave 1 · 2 mobs“). Die „3“ fehlt weiter, solange der Client noch das Arena-Terrain lädt. |
| Backpack Q / Strg+Q | **PASS** (bleibt in der Hotbar sichtbar) |
| Markierung des gewählten Wellen-Tabs | **Teilweise.** Nur im Tooltip-Namen („▶ Wave 2“), am Icon selbst ist nichts zu sehen. |
| Ergebnistitel verzögert | **PASS** („All waves cleared“, danach „Team RED wins“, Feuerwerk) |
| Lobby-Verteilung | **PASS** (3 Spieler auf 3 verschiedenen Blöcken) |
| „standing on air“ | **Weniger, aber nicht weg** (31 Meldungen in ~15 min, Lauf 1: 83). Kommt vor allem in ARENA, etwa alle 10 s bei stehenden Spielern. |
| Sounds | **PASS (gemessen).** Countdown-Ticks + Go bei Wellenstart (4 Sounds, je ~0,5 s, Spitze ca. −21 dBFS); Sudden-Death-Start; Abschluss-Sound bei Sieg (−20 dBFS), 2 s später Sieg-Sound mit Feuerwerk (5,8 s, −13 dBFS); Niederlage-Sound (7 s, −15 dBFS). Lautstärke wie Vanilla, nicht übersteuert. Einen „Welle geschafft“-Sound zwischen zwei Wellen habe ich nicht ausgelöst (keine normal abgeschlossene Welle). |

## Bisher nicht getestete Punkte
| Punkt | Ergebnis |
|---|---|
| Wellenmenü mit 2 Zuschauern gleichzeitig | **PASS** (Tester1 klickt, Tester3 sieht 20 → 10 / Welle 1: 10 sofort) |
| Server-Stop während ARENA | **PASS** (sauberer Stopp, keine Exception; nach dem Neustart keine Spiel-Level mehr, alle in der Lobby, Inventare leer) |
| Backpack in eine Truhe | NICHT GETESTET (mein Truhenaufbau ist zweimal schiefgegangen) |
| S15-Last (300 Zombies) | NICHT GETESTET |

## Neuer Bug
**[Hoch] Spieler, der während eines Phasenwechsels offline ist, bleibt nach Spielende in der gelöschten Arena hängen.**
Repro (2×): Teamspieler trennt die Verbindung in PREP (lebend oder auf dem Todesbildschirm), die Phase wechselt bis ARENA, der Spieler joint in ARENA wieder.
- Beim Rejoin (bzw. beim Sudden-Death-Start) loggt der Server: `Force-added player with duplicate UUID …` / `UUID of added entity already exists: ServerPlayer['Tester3' …, l='ServerLevel[world]', x=0.50, y=64.00, z=0.50, removed=DISCARDED]` / `Fetching packet for removed entity`.
- Während des Spiels wirkt alles normal (Survival in der Team-Arena).
- Nach dem Spielende sind alle anderen in der Lobby; dieser Spieler steht laut Server weiter in `red_arena_…` (ein bereits gelöschtes Level), im Abenteuermodus, ohne Bossleiste. Der Client zeigt noch die Arena und den Backpack, obwohl das Inventar auf dem Server leer ist (Screenshot 7). Erst ein Reconnect behebt das.
- Vermutete Stelle: neuer Join-Pfad aus T12b („Reset dead joiners before player registration“) bzw. Rejoin-Platzierung in die Arena, die zusätzlich zur Vanilla-Platzierung eine zweite Player-Entity einfügt. Im alten Jar (Lauf 1) gab es diese Warnung beim Rejoin in ARENA nicht, dort war der Spieler allerdings erst in ARENA offline gegangen.

## Verbesserungen
- Die „3“ des ersten Countdowns geht unter, solange der Client die Arena lädt. Den Countdown bei Welle 1 erst starten, wenn die Spieler geladen sind (oder 1 s Vorlauf geben).
- Den gewählten Wellen-Tab auch am Icon markieren (Glint oder anderes Item), nicht nur im Tooltip.
- Effekte werden beim Arena-Start und beim Sudden-Death-Start entfernt (für das Spiel richtig). Für Tests wäre ein Debug-Schalter „Effekte behalten“ praktisch.
