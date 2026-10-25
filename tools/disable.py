#!/usr/bin/env python3
"""Disable packages for user 0, one pm call each, and log them for restore.py.

  tools/disable.py pkg1 pkg2 ...
"""
import os, re, sys
sys.path.insert(0, os.path.dirname(__file__))
from tv import connect, sh

LOG = os.path.join(os.path.dirname(__file__), "..", "backup", "disabled.txt")

# Android core and TV-critical packages: disabling these can stop the TV from booting
# or remove the remote/HDMI/keyboard. See the README for the reason behind each.
NEVER = re.compile(r"^(android|com\.android\.(systemui|shell|phone|settings|tv\.settings|permissioncontroller"
                   r"|packageinstaller|providers\.(?!calendar$|contacts$|userdictionary$|blockednumber$).*)"
                   r"|com\.(google\.)?android\.(permissioncontroller|packageinstaller|ext\.services|ext\.shared|modulemetadata)"
                   r"|com\.google\.android\.overlay\.modules.*"
                   r"|com\.android\.vending|com\.google\.android\.(gms|gsf|inputmethod\.latin|tv\.remote\.service)"
                   r"|com\.mediatek\.(tvinput|tv\.service)|com\.mitv\.livetv.*|mitv\.service|com\.mi\.bluetooth"
                   r"|com\.xiaomi\.android\.tvsetup\.partnercustomizer|.*fac(tory)?menu.*|.*\.fac\..*)$")

pkgs = sys.argv[1:]
bad = [p for p in pkgs if NEVER.match(p)]
if bad:
    sys.exit(f"refused, core/TV-critical package(s): {' '.join(bad)}")

dev = connect()
for p in pkgs:
    print(p, "->", sh(dev, f"pm disable-user --user 0 {p}").strip())

disabled = set(sh(dev, "pm list packages -d").replace("package:", "").split())
failed = [p for p in pkgs if p not in disabled]
done = [p for p in pkgs if p in disabled]
logged = set(open(LOG).read().split()) if os.path.exists(LOG) else set()
with open(LOG, "a") as f:
    f.writelines(p + "\n" for p in done if p not in logged)
if failed:
    sys.exit(f"NOT disabled: {' '.join(failed)}")
print(f"ok, {len(done)} disabled (logged in backup/disabled.txt)")
