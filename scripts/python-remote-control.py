"""Control check for CI: pair with the Android TV emulator using the androidtvremote2
Python library (a known-good client), reading the code off the screen like RealTvTest."""
import asyncio, re, subprocess, sys, tempfile, os
from androidtvremote2 import AndroidTVRemote

def screen_code():
    for _ in range(15):
        xml = subprocess.run(["adb", "exec-out", "uiautomator", "dump", "/dev/tty"], capture_output=True, text=True).stdout
        m = re.search(r'text="([0-9A-F]{6})"', xml)
        if m:
            return m.group(1)
        chars = "".join(re.findall(r'text="([0-9A-F])"', xml))
        if len(chars) == 6:
            return chars
        subprocess.run(["sleep", "1"])
    raise SystemExit("no code on screen")

async def main():
    d = tempfile.mkdtemp()
    remote = AndroidTVRemote("Python check", os.path.join(d, "cert.pem"), os.path.join(d, "key.pem"), "127.0.0.1")
    await remote.async_generate_cert_if_missing()
    await remote.async_start_pairing()
    await asyncio.sleep(2)
    code = screen_code()
    print("python: TV shows code", code)
    await remote.async_finish_pairing(code)
    print("python: paired")
    await remote.async_connect()
    print("python: connected, device info", remote.device_info)
    remote.send_key_command("HOME")
    remote.disconnect()

asyncio.run(main())
