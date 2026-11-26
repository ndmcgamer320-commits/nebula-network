# ✦ NEBULA NETWORK

<details>
<summary><b>§5§l NEBULA NETWORK</b> — Paper 1.21.11</summary>

**A neon-purple Minecraft network** with a protected lobby, two Survival worlds, and planned BedWars and Lucky Pillars arenas.

</details>

## Current build

- Paper 1.21.11 with Java 21
- GitHub Actions manual start, up to five hours per run
- Playit tunnel for a public Java server address
- Neon-purple lobby platform, protected building area, damage/food/drop protection
- `/spawn`, `/menu`, `/nebula`, and admin-only `/setlobby`
- Two generated Survival worlds: `survival_1` and `survival_2`
- Menu entries reserved for BedWars and Lucky Pillars

**Important:** BedWars and Lucky Pillars are not playable yet just because their menu entries exist. They need their respective compatible game plugins and two properly configured arena maps each. See [plugins/README.md](plugins/README.md). The current workflow builds the lobby and Survival starter worlds; it does not pretend to include finished arena maps.

## One-time setup

1. Read the [Minecraft EULA](https://www.minecraft.net/eula). Only continue if you agree to it.
2. Create a Playit account and a TCP tunnel that forwards to local port `25565`. The Playit agent documentation is at [playit-cloud/playit-agent](https://github.com/playit-cloud/playit-agent).
3. In this repository, open **Settings → Secrets and variables → Actions → New repository secret**.
4. Name the secret `PLAYIT_SECRET` and paste your Playit agent secret as its value. Never commit or share this secret.
5. Open **Actions → Run Nebula Network → Run workflow**.
6. Confirm the EULA checkbox and select up to five hours.
7. Wait for the workflow to start. Check the Playit logs in the workflow run if the tunnel does not connect.

The public address is shown by the Playit agent logs. Share that address with Java Edition players.

## Commands

| Command | Purpose |
| --- | --- |
| `/spawn` | Return to lobby |
| `/menu` | Open game selector |
| `/nebula` | Open game selector |
| `/setlobby` | Save current location as lobby (operator permission) |

## Notes and limits

- This is temporary hosting. The server stops when the selected run time ends or the GitHub Actions job is stopped.
- GitHub-hosted runner availability and account usage limits apply; a five-hour run is not guaranteed.
- This is a Java Edition server. Bedrock players need a separately configured compatibility bridge; it is not included.
- Do not store world saves, plugin binaries, or tunnel secrets in the repository.
- The first lobby is a generated starter build. You can improve it in-game while the server is running, but changes are not persistent between workflow runs unless you add a safe backup/restore process.
- The six world names are initialized on first startup; BedWars and Lucky Pillars still need arena imports and setup.
