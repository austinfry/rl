# Fractured Archive

Elapsed-time room splits and a total timer for The Fractured Archive (Raids 4). The overlay lists each boss room and the raid total. It only records how long things have taken.

**Show splits** in the plugin settings panel is on by default. Turn it off to hide the overlay. The timer keeps running while the overlay is hidden.

A run is five bosses. Archive Guardian is first and Ocellus is last. The middle three are one choice from each pair: Bork and the Smorks or Ezekiel, Dreadnoth the Jellied or Moia, and Xarth or the Dagon'hai Trio.

## Running

From this directory:

```bash
./gradlew run
```

Log in with a Jagex account using the steps at <https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts>.

That task uses the dev client and turns debug logging on for this plugin (`src/test/resources/logback-test.xml`). Enter the raid and search the console for lines starting with `Candidate region`, `Candidate chat`, and `Candidate NPC`.

## TODOs to fill in

Search the project for `TODO`.

- `FracturedArchiveIds.REGION_IDS` — region ids from the candidate region lines. Once set, entering those regions starts the timer and leaving them freezes it.
- `FracturedArchiveIds.RAID_START_MESSAGES` — chat text that starts a run.
- `FracturedArchiveIds.RAID_COMPLETE_MESSAGES` — chat text that ends a run.
- `FracturedArchiveIds.ROOM_COMPLETE_MESSAGES` — optional chat text that ends the current room.
- `ArchiveBoss` — completion chat for each room. NPC ids are already taken from `net.runelite.api.gameval.NpcID`. A kill alias ends the room; a present alias only keeps the split running. The Dagon'hai room ends after three unshielded deaths. Ocellus ends when the P3 NPC dies. Move an id between those aliases if a despawn log shows a different form is the one that dies.

Until the chat and region lines are filled in, the timer starts when an Archive Guardian appears, and a room ends when its kill NPC despawns at 0 hitpoints. Other boss names are used only after a run has already started, so an overworld Bork does not start a timer. `./gradlew test` covers the timer rules.
