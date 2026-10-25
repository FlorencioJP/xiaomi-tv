#!/usr/bin/env python3
"""Re-enable everything tools/disable.py disabled (backup/disabled.txt).

  tools/restore.py            re-enable all of them
  tools/restore.py pkg ...    re-enable only these
"""
import os, sys
sys.path.insert(0, os.path.dirname(__file__))
from tv import connect, sh

LOG = os.path.join(os.path.dirname(__file__), "..", "backup", "disabled.txt")
pkgs = sys.argv[1:] or open(LOG).read().split()
dev = connect()
for p in pkgs:
    print(p, "->", sh(dev, f"pm enable --user 0 {p}").strip())
if not sys.argv[1:]:
    # full restore: Home key back to the stock launcher (no accessibility service was enabled before)
    print(sh(dev, "settings delete secure enabled_accessibility_services; settings put secure accessibility_enabled 0").strip())
