#!/usr/bin/env python3
"""Talk to the TV over network ADB with the Python adb-shell client
(macOS Local Network privacy blocks the stock adb binary).

  tools/tv.py 'getprop ro.build.version.sdk'        run a shell command
  tools/tv.py --keys DPAD_RIGHT DPAD_DOWN            press remote keys
  tools/tv.py --screenshot shots/01.png [KEYS...]    press keys, then capture
  tools/tv.py --ui [KEYS...]                         press keys, then print on-screen texts + focus
  tools/tv.py --push local remote                    copy a file to the TV (base64 chunks, md5 checked)

TV_HOST / TV_PORT override the address (default 192.168.0.238:5555).
"""
import base64, hashlib, os, re, sys, time
from adb_shell.adb_device import AdbDeviceTcp
from adb_shell.auth.keygen import keygen
from adb_shell.auth.sign_pythonrsa import PythonRSASigner

HOST = os.environ.get("TV_HOST", "192.168.0.238")
PORT = int(os.environ.get("TV_PORT", "5555"))
KEY = os.path.expanduser("~/.android/adbkey")


def connect():
    if not os.path.exists(KEY):
        os.makedirs(os.path.dirname(KEY), exist_ok=True)
        keygen(KEY)
    signer = PythonRSASigner(open(KEY + ".pub").read(), open(KEY).read())
    dev = AdbDeviceTcp(HOST, PORT, default_transport_timeout_s=60)
    # long auth timeout: the first time, someone must accept the prompt on the TV
    dev.connect(rsa_keys=[signer], auth_timeout_s=60)
    return dev


def sh(dev, cmd, timeout=120):
    return dev.shell(cmd, timeout_s=timeout, read_timeout_s=timeout)


def press(dev, keys):
    for k in keys:
        sh(dev, f"input keyevent KEYCODE_{k}")
        time.sleep(0.6)


def ui(dev):
    """Print the visible texts and the focused element: the fallback when screencap is blocked."""
    sh(dev, "timeout -s KILL 40 uiautomator dump /data/local/tmp/ui.xml")
    xml = sh(dev, "cat /data/local/tmp/ui.xml")
    print("top:", sh(dev, "dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'").strip())
    for node in re.findall(r"<node [^>]*>", xml):
        a = dict(re.findall(r'(\S+?)="([^"]*)"', node))
        label = a.get("text") or a.get("content-desc")
        if a.get("focused") == "true":
            print("FOCUSED:", label or a.get("resource-id"), a.get("bounds"))
        elif label:
            print("  ", label, a.get("bounds"))


def screenshot(dev, out):
    # this MStar firmware's screencap can hang forever: cap it
    sh(dev, "rm -f /data/local/tmp/s.png; timeout -s KILL 20 screencap -p /data/local/tmp/s.png")
    b64 = sh(dev, "base64 /data/local/tmp/s.png | tr -d '\\n'")
    data = base64.b64decode(b64) if b64.strip() else b""
    if not data:
        sys.exit("empty screenshot (Live TV/HDMI/DRM apps can't be captured): "
                 "check `dumpsys activity activities | grep topResumedActivity`")
    os.makedirs(os.path.dirname(out) or ".", exist_ok=True)
    open(out, "wb").write(data)
    print(out, len(data), "bytes")


def push(dev, local, remote):
    data = open(local, "rb").read()
    b64 = base64.b64encode(data).decode()
    sh(dev, f"rm -f {remote}.b64")
    for i in range(0, len(b64), 60000):
        sh(dev, f"echo -n '{b64[i:i + 60000]}' >> {remote}.b64")
    sh(dev, f"base64 -d {remote}.b64 > {remote} && rm {remote}.b64")
    remote_md5 = sh(dev, f"md5sum {remote}").split()[0]
    if remote_md5 != hashlib.md5(data).hexdigest():
        sys.exit(f"md5 mismatch for {remote}")
    print(remote, len(data), "bytes, md5 ok")


def main(argv):
    dev = connect()
    if argv[:1] == ["--screenshot"]:
        press(dev, argv[2:])
        time.sleep(1)
        screenshot(dev, argv[1])
    elif argv[:1] == ["--ui"]:
        press(dev, argv[1:])
        time.sleep(1)
        ui(dev)
    elif argv[:1] == ["--keys"]:
        press(dev, argv[1:])
    elif argv[:1] == ["--push"]:
        push(dev, argv[1], argv[2])
    else:
        print(sh(dev, " ".join(argv)), end="")


if __name__ == "__main__":
    main(sys.argv[1:])
