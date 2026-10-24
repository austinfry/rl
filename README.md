# Fractured Archive

Elapsed-time room splits and a total timer for The Fractured Archive (Raids 4). The overlay lists each boss and the raid total. It only records how long things have taken.

Game chat drives the splits:

- `Challenge started: <Boss>.` opens that boss's split. The first one also starts the raid timer.
- `Challenge complete: <Boss>. Duration: m:ss.xx. Total: m:ss.xx. Personal best: m:ss.xx.` closes it.

A completed row shows the game's Duration, the plugin's measured Timer for that same window, and the personal best. The bottom of the overlay shows the game's Total next to the plugin's Timer. Names are matched loosely, so any of the eight bosses works, including a name that has not been seen before. Colour tags are ignored.

**Show splits** in the plugin settings panel is on by default. Turn it off to hide the overlay. The timer keeps running while the overlay is hidden.

A run is five bosses. Archive Guardian is first and Ocellus is last. The middle three are one choice from each pair: Bork and the Smorks or Ezekiel, Dreadnoth the Jellied or Moia, and Xarth or the Dagon'hai Trio. Ocellus's complete message freezes the timer.

Entering the raid, or seeing a boss spawn, does not start the overlay. The clock stays at zero until the first `Challenge started` line. Leaving the instance freezes a timer that is already running. `FracturedArchiveIds.REGION_IDS` is an optional list of region ids; filling it in does not start the timer, and leaving those regions freezes one that challenge chat already started.

## Running

From this directory:

```bash
./gradlew run
```

Log in with a Jagex account using the steps at <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>.

`./gradlew test` covers the challenge-message parser and the timer rules.
