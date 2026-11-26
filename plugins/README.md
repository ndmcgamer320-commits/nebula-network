# Game plugins and maps

NebulaCore is built by the workflow and provides the protected lobby, world creation, join routing and game selector.

## Important: minigame status

The workflow does **not** silently install random third-party plugin binaries. BedWars and Lucky Pillars require their own compatible game engines and arena configuration. The six worlds created by NebulaCore are clean starting worlds, not pre-built minigame maps.

- BedWars: ScreamingBedWars documents support for Paper/Spigot through 1.21.11: https://github.com/hemangsrr/ScreamingBedWars
- Lucky Pillars: TheLuckPillars documents testing on Paper 1.21.11: https://modrinth.com/mod/theluckpillars

Review each project's license, release files, setup instructions and compatibility before adding its JAR. Configure two arenas/maps inside the game plugin after importing or building the maps. Do not place downloaded plugin JARs in this Git repository; add them to the server at runtime or as workflow artifacts.
