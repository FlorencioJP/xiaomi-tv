# Xiaomi TV (MiTV-MOOR2) — cleanup + Clean Home launcher

TV: Xiaomi `MiTV-MOOR2`, Android 11 / Google TV, Ethernet `192.168.0.238`.
Every script talks to the TV through `.venv` (Python `adb-shell`), because macOS blocks plain `adb` on the local network.
A Pixel is often plugged into this Mac by USB: never use plain `adb` here.

## What was changed

- **Apps turned off** (not uninstalled) with `pm disable-user --user 0`. The list is in `backup/disabled.txt`.
  Still off: analytics, statistic, feedback, Mi channel, Mi TV+, OEM tab, demo, manual, TV manager, Netflix,
  Google Assistant (katniss + tv.assistant), Chromecast (mediashell), Google TV films, Play Games.
- **Kept on purpose:** `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith`.
  They are in the log, but turning them off froze the TV. Leave them on.
- `com.xm.webcontent` is turned back on by the firmware at every boot. Not worth fighting.
- Full original state: `backup/original/` (packages, settings, props).

## Re-enable apps

```sh
.venv/bin/python tools/restore.py                    # everything + stock Home key
.venv/bin/python tools/restore.py com.netflix.ninja  # just one app
```

## The launcher (`launcher/`, package `com.cleantv.home`)

Build, test and install (needs JDK 21):

```sh
tools/install.sh            # build + install + take the Home key
tools/install.sh --no-home  # build + install only
```

Home key: Google TV ignores the normal "default home" setting, so an accessibility service
(`HomeKeyService`, "Bouton Accueil") catches the Home key and opens Clean Home.
Side effect: holding Volume + only moves one step. Press it several times instead.
To give the Home key back: `tools/restore.py` (full restore) or turn off "Bouton Accueil"
in Settings → Accessibility.

Change things in `launcher/app/src/main/res/values/config.xml`, then run `tools/install.sh`:

- `dock_apps`: dock order (package names, first = left).
- `columns`: tiles per row (also the dock size).
- `power_on_home`: `true` = open the home screen when the TV wakes up.
- `home_component`: which home the Home key and power-on open. Now `com.omarchytv.home/.MainActivity`
  (OmarchyTV, installed from its signed 2.12.0-beta release APK, SHA-256 checked).
  Empty, or OmarchyTV not installed = Clean Home. OmarchyTV's own "set as home" step does not work on
  this Google TV (see above), which is why the Home key goes through this service.
  OmarchyTV listens on port 47800; it answers 401 to anything not signed by a paired PC.

Wallpaper: delete `res/drawable/background.xml`, add `res/drawable-nodpi/background.jpg` (1920×1080).

The gear (top right, press Up from the dock) holds: Inputs (HDMI 1–3, AV, Live TV), network, Settings.

## Debugging is closed

On 2026-10-25 USB and network debugging were turned off (`adb_enabled 0`); port 5555 refuses connections.
To use these scripts again: Settings → System → Developer options → USB debugging on, accept the key on the TV.
If Python gets "No route to host" while `nc` reaches the TV, macOS Local Network privacy is blocking Python:
allow it in System Settings → Privacy & Security → Local Network, or relay through `nc` and set
`TV_HOST=127.0.0.1 TV_PORT=<relay port>`.

## Good to know

- `screencap` / `screenrecord` hang on this firmware. Check the screen with `.venv/bin/python tools/tv.py --ui`.
- Right after a restart, the Bluetooth remote needs ~10 s to reconnect before keys work.
- Storage was 95% full. Caches were trimmed and updates of disabled apps removed.
- Private DNS: if it was turned on, undo with
  `.venv/bin/python tools/tv.py "settings put global private_dns_mode opportunistic"`.
