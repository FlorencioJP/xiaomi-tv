#!/usr/bin/env python3
"""Save the TV's state before any change: tools/backup.py backup/<name>"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from tv import connect, sh

CMDS = {
    "packages.txt": "pm list packages -f",
    "packages-disabled.txt": "pm list packages -d",
    "packages-system.txt": "pm list packages -s",
    "packages-3rdparty.txt": "pm list packages -3",
    "getprop.txt": "getprop",
    "settings-global.txt": "settings list global",
    "settings-secure.txt": "settings list secure",
    "settings-system.txt": "settings list system",
    "home.txt": "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME; "
                "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME",
    "display.txt": "wm size; wm density; settings get secure display_density_forced",
    "network.txt": "dumpsys connectivity | grep -o 'Transports: [A-Z]*'; dumpsys usb | grep host_connected; "
                   "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u; ip -4 addr",
}

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
dev = connect()
for name, cmd in CMDS.items():
    open(os.path.join(out, name), "w").write(sh(dev, cmd))
    print(name, "ok")
