# NovaAuth (Purpur 1.21.x)

NovaAuth is an AuthMeReloaded-style authentication plugin for offline-mode servers.
It provides registration/login, bcrypt password hashing, sessions, and restrictions
that prevent gameplay until authentication.

This version includes:
- Limbo/Auth world teleport while unauthenticated
- Freeze + Blindness while unauthenticated
- Login/Register reminders every 5 seconds (configurable)

## Requirements
- Purpur/Paper 1.21.x
- Java 21

## Build
- `./gradlew shadowJar`
Jar output:
- `build/libs/NovaAuth-1.1.0-all.jar`

## Installation
1. Put the jar into `plugins/`
2. Start the server once to generate config
3. Edit `plugins/NovaAuth/config.yml` as needed
4. Restart

## Commands
Player:
- `/register <password> <passwordConfirm>` (aliases: /reg)
- `/login <password>` (alias: /l)
- `/logout`
- `/changepassword <old> <new> <newConfirm>` (aliases: /changepw, /cpw)

Admin:
- `/unregister <player>` (permission: novaauth.admin.unregister)
- `/novaauthreload` (permission: novaauth.admin.reload)

## Notes
- Intended for offline-mode authentication setups.
- If behind a proxy, either configure IP forwarding properly or disable `session.ip-check`.
- Passwords passed as command arguments can be exposed by some logging setups; consider adjusting server logging.

## Data storage
SQLite file:
- `plugins/NovaAuth/data.db`

Table `users`:
- uuid TEXT PRIMARY KEY
- name TEXT
- password_hash TEXT
- registered_at INTEGER
- last_login INTEGER
- last_ip TEXT
