# ✦ NEBULA NETWORK

<details>
<summary><b>§5§l NEBULA NETWORK</b> — Paper 1.21.11</summary>

**A neon-purple Minecraft network** with a colorful floating-island lobby, skinned NPCs, network ranks, a live sidebar and playtime leaderboard, two Survival worlds, and generated game arenas.

</details>

## Current build

- Paper 1.21.11 with Java 21
- GitHub Actions manual start, up to five hours per run
- Playit tunnel for a public Java server address
- Kubj's free Colorful Lobby map is fetched from the creator's PlanetMinecraft page at server startup when the download is available; a custom floating-island lobby is generated automatically if the site blocks the request
- Citizens player-model NPCs with profile skins, spawn Speed II and Fire Resistance, and a movement/world-border boundary around the lobby island
- Rank-aware TAB header/footer with Java heap usage, an on-screen sidebar, and a top-five playtime leaderboard
- Protected lobby building area, damage/food/drop protection
- `/spawn`, `/menu`, `/nebula`, `/settings`, `/rank`, and admin-only `/setlobby`
- Two generated Survival worlds: `survival_1` and `survival_2`
- Custom BedWars sky-island map in `bedwars_1` with red/blue bases, beds, bridges, and a central island
- Custom circular Duel PvP map in `duel_arena` with two spawn pads and a protected boundary
- Arena maps regenerate on server startup and when the last player leaves an arena
- `/bedwars` and `/duel` enter the map previews; `/arenareset [bedwars|duel|all]` manually restores a map (operator or OWNER/ADMIN rank)

**Testing status:** GitHub Actions build validation is required after each change. Live multiplayer behavior, server-list icon rendering, map download, NPC skin fetch, and message/settings flows still need a real server session to verify. Lucky Pillars remains a placeholder.

## One-time setup

1. Read the [Minecraft EULA](https://www.minecraft.net/eula). Only continue if you agree to it.
2. Create a Playit account and a TCP tunnel that forwards to local port `25565`. The Playit agent documentation is at [playit-cloud/playit-agent](https://github.com/playit-cloud/playit-agent).
3. In this repository, open **Settings → Secrets and variables → Actions → New repository secret**.
4. Name the secret `PLAYIT_SECRET` and paste your Playit agent secret as its value. Never commit or share this secret.
5. Open **Actions → Run Nebula Network → Run workflow**.
6. Confirm the EULA checkbox and select up to five hours.
7. Wait for the workflow to start. Check the Playit logs in the workflow run if the tunnel does not connect.

The public address is shown by the Playit agent logs. Share that address with Java Edition players.

## Network features\n\n- Branded server-list MOTD and `server-icon.png` Nebula emblem.\n- Expanded `/settings` menu: name tags, player visibility, lobby particles, menu sounds, sidebar and private-message preferences.\n- `/tell`, `/msg`, `/w`, `/r` and `/reply` private messaging.\n- `/profile` player card with network rank, overall ELO, mode ELO, kills, deaths and ping.\n- Separate ELO counters are tracked for Survival, Duels and BedWars when player kills occur in those worlds; other game modes need their own match-result integration before competitive ELO can be considered complete.\n\n## Commands

| Command | Purpose |
| --- | --- |
| `/spawn` | Return to lobby |
| `/menu` | Open game selector |
| `/nebula` | Open game selector |
| `/setlobby` | Save current location as lobby (operator or OWNER/ADMIN rank) |
| `/settings` | Open personal lobby settings |\n| `/tell <player> <message>` / `/msg` / `/w` | Send a private message |\n| `/r <message>` / `/reply` | Reply to your last private message |\n| `/profile [player]` | View a network profile (other players: staff only) |
| `/rank` | View your network rank |
| `/rank <player> <OWNER\|ADMIN\|MVP\|VIP\|MEMBER>` | Set an online player's network rank (console, OWNER or ADMIN) |
| `/bedwars` | Enter the BedWars map preview |
| `/duel` | Enter the Duel map preview |
| `/mobhunt [join|leave|status]` | Join or manage the Mob Hunt queue |
| `/arenareset [bedwars\|duel\|all]` | Regenerate arena map(s) (operator permission) |

## Notes and limits

- This is temporary hosting. The server stops when the selected run time ends or the GitHub Actions job is stopped.
- GitHub-hosted runner availability and account usage limits apply; a five-hour run is not guaranteed.
- This is a Java Edition server. Bedrock players need a separately configured compatibility bridge; it is not included.
- `online-mode=true` is enabled so Citizens can fetch real player skin profiles. Players need authenticated Java accounts; offline/cracked launchers will not be able to join.
- Citizens is downloaded and loaded as a required dependency for the player-model NPCs.
- GitHub Actions caches world folders and plugin rank data between runs on a best-effort basis. This is not a guaranteed permanent world backup.
- Do not store world saves, plugin binaries, or tunnel secrets in the repository.
- The world import step uses the creator's official [Colorful Lobby by Kubj](https://www.planetminecraft.com/project/colorful-lobby-100x100-by-kubj/) download. If PlanetMinecraft blocks the automated request, NebulaCore generates a fallback floating island instead. The first live run must confirm which one loaded.
- The arena structures are generated by NebulaCore; downloaded map files are not required. Arena modifications are discarded when the last player leaves that arena.
