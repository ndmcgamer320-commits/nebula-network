# Game plugins and maps

NebulaCore is built automatically by the workflow. It provides the protected lobby, game selector, and two generated Survival starter worlds.

## BedWars and Lucky Pillars

The selector has entries reserved for BedWars and Lucky Pillars, but these modes are **not playable yet**. Each requires:

1. A Paper-compatible minigame plugin that explicitly supports Paper 1.21.11.
2. Two arena maps designed for that plugin.
3. Arena configuration, team/spawn points, lobby settings, and game rules.
4. A test run with multiple players before opening the mode to everyone.

Do not download random plugin JARs or commit binary plugin files to this repository. Check each plugin's official project page, supported server versions, license, and setup documentation before using it. Import arena maps through the plugin's documented process.

The current workflow creates six named worlds: two Survival starter worlds, two empty BedWars world slots, and two empty Lucky Pillars world slots. The four game-mode worlds are only blank generated terrain; they are not finished arena maps and are not registered as playable matches.
