# Mob Arena – dritter Real-Client-Test (2026-11-08, main ff7cf70)

Build lokal: 155/155 GameTests grün. VM `mobtest`, 3 echte 26.3-Clients, Server mit `-Dmobarena.debug=true -Dmobarena.debug.keepEffects=true`, Sounds über PulseAudio-Null-Sink mitgeschnitten. 2 Spiele.

## T13
| Punkt | Ergebnis |
|---|---|
| Vorbereitungsphase in der Arena (30 s) | **PASS.** Teams spawnen in ihrer Arena, Titel „Get ready“, Bossleiste „Get ready – 0:27 … 0:12“. ARENA-Start 13:56:41, erster Countdown-Ton 13:57:11 = 30 s. Mit `arenaPrepSeconds 10` sind es 10 s. |
| Erste „3“ im Countdown | **PASS** (sichtbar und hörbar: 3, 2, 1, „Go / Wave 1 · 2 mobs“) |
| Rejoin-Bug (in PREP offline, in ARENA zurück) | **PASS** (2×: einmal während der Vorbereitung, einmal mitten in Welle 1). Keine `duplicate UUID`-Warnung, nach Spielende Lobby, Survival→Abenteuer, Inventar leer. |
| Icon des gewählten Wellen-Tabs | **PASS** (der gewählte Tab wird zu einem Smaragdblock) |
| Diagnose „standing on air“ | **Gesammelt:** 47 Zeilen `MOBARENA_STANDING_ON_AIR` (21 in ARENA, 26 in SUDDEN_DEATH, keine in PREP und bei Teleports). Typischer Zustand: Spieler in der Luft (`onGround=false`, `deltaMovement.y` ±0,28) über dem Arenaboden, `ticksSinceLastModTeleport` 900+. Das sieht nach Rückstoß und Sprüngen durch Husks im Kampf aus, nicht nach fehlenden Blöcken. Datei: `standing_on_air_run3.log` |

## Gegenprobe T12
| Punkt | Ergebnis |
|---|---|
| Arena-Tod mit lebendem Teammate → Zuschauer → Respawn nach 5 s | PASS |
| Todesnachricht für alle (BLUE sieht den Tod von Tester3) | PASS |
| Sounds | PASS: 3 Countdown-Ticks + Go je Welle (−20 dBFS), „Welle geschafft“ (1,1 s), Sudden Death, Unentschieden (2,8 s), Sieg (5,8 s, −14 dBFS) |
| Ergebnis „Draw“ (Sudden Death gleichstand) | PASS (Titel und Chat) |
| Lobby nach Spielende, Inventare leer | PASS |

## Kleinigkeiten (keine Bugs)
- Ein Spieler, der in der Arena-Vorbereitung rejoint, wird genau auf den Team-Spawn gesetzt. Steht dort ein Teammate, stecken beide ineinander (Screenshot: Gesicht füllt das Bild). Leicht versetzen.
- Die Lobby-Verteilung beträgt nur 1 Block, die Spieler stehen Gesicht an Gesicht. 2–3 Blöcke wären angenehmer.

## Nicht getestet
Backpack in eine Truhe, S15-Last (300 Zombies).
