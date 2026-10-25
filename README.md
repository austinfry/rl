# Fractured Archive Splits

Elapsed-time boss splits and a raid timer for The Fractured Archive. The overlay shows how long each boss took and how long the raid has taken. It does not count attacks or show mechanics.

The game chat drives the timer:

- `Challenge started: <Boss>.` opens that boss's split. The first one also starts the raid timer.
- `Challenge complete: <Boss>. Duration: m:ss.xx. Total: m:ss.xx.` closes it. An optional `Personal best: m:ss.xx` is shown as Boss PB. `Total: m:ss.xx (new personal best)` uses that Duration as New Boss PB.

A completed boss row shows the game's Duration, Boss time (the plugin's measured time for that boss), and Boss PB or New Boss PB. The bottom of the overlay shows Boss total (the game's Total) next to Raid total (the plugin's measured time for the whole raid).

The timer stays at zero until the first `Challenge started` line, including while a boss is already in the room. Ocellus's complete message freezes the raid. Leaving the instance also freezes a timer that is already running.

## Show splits

**Show splits** is in the plugin settings and is on by default. Turn it off to hide the overlay. The timer keeps running while it is hidden, and the times are still there when you turn it back on.

## Running the development client

From this directory:

```bash
./gradlew run
```

Log in with a Jagex account using the steps at <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>.

`./gradlew test` covers the challenge-message parser and the timer rules.
