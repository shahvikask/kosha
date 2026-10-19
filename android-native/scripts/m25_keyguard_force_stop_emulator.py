#!/usr/bin/env python3
"""API35 emulator-only OS Keyguard cancellation and real app force-stop smoke gate.
No financial fixtures, vault credentials, UI dumps or temporary PIN are logged.
Requires a fresh disposable emulator and an installed debug app.
"""
import secrets
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

PKG = "in.growurdhan.redevelopment"
ACTIVITY = PKG + "/.MainActivity"

def adb(*args, timeout=30):
    try:
        return subprocess.run(["adb", *args], check=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True, timeout=timeout).stdout
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired):
        # Do not print exception arguments: one command holds a temporary PIN.
        raise AssertionError("M25 Android shell step failed (arguments redacted)") from None

def screen():
    # UIAutomator reads the REAL Android foreground accessibility hierarchy.
    adb("shell", "uiautomator", "dump", "/sdcard/m25-window.xml", timeout=40)
    raw = adb("exec-out", "cat", "/sdcard/m25-window.xml")
    root = ET.fromstring(raw)
    return [node.attrib for node in root.iter("node")]

def matching(nodes, name):
    return [n for n in nodes if name in n.get("text", "") or
            name in n.get("content-desc", "")]

def wait_for(predicate, label, seconds=35):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        try:
            result = predicate()
            if result:
                return result
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(2)
    raise AssertionError("M25 OS gate failed: " + label)

def tap(node):
    import re
    coords = list(map(int, re.findall("[0-9]+", node["bounds"])))
    assert len(coords) == 4
    adb("shell", "input", "tap", str((coords[0] + coords[2]) // 2),
        str((coords[1] + coords[3]) // 2))

def locked_screen():
    nodes = screen()
    return bool(matching(nodes, "Verify this device")) and not matching(nodes, "Private vault unlocked")

def keyguard_visible():
    nodes = screen()
    # Must show the actual system UI: do not count the GrowUrDhan lock screen.
    texts = " ".join(n.get("text", "") + " " + n.get("content-desc", "") for n in nodes)
    system_prompt = any(label in texts for label in (
        "Confirm your device credential", "Confirm your PIN",
        "Enter PIN", "Enter your PIN", "Use PIN", "Verify it's you"))
    focus = adb("shell", "dumpsys", "window", "windows")
    return system_prompt and PKG not in focus.split("mCurrentFocus=")[-1].splitlines()[0]

def find_and_tap(label):
    # Compose text fields may be offscreen or hidden by the keyboard. Unlike a
    # blind BACK (which may navigate away), only dismiss when IME is visible.
    for attempt in range(9):
        nodes = matching(screen(), label)
        if nodes:
            tap(nodes[0])
            return
        if attempt < 8:
            ime = adb("shell", "dumpsys", "input_method")
            if "mInputShown=true" in ime or "mShowRequested=true" in ime:
                adb("shell", "input", "keyevent", "KEYCODE_BACK")
            # Scroll through the Compose Column; avoid dragging from nav bar.
            adb("shell", "input", "swipe", "540", "1550", "540", "430", "420")
            time.sleep(0.6)
    raise AssertionError("M25 OS gate failed: UI control " + label)

def fill(label, value):
    # Synthetic values ONLY. Android input commands are captured, never printed.
    find_and_tap(label)
    adb("shell", "input", "text", value)
    adb("shell", "input", "keyevent", "KEYCODE_BACK")

def finish_real_device_challenge(pin):
    find_and_tap("Verify this device")
    wait_for(keyguard_visible, "real system credential challenge before account operation")
    adb("shell", "input", "text", pin)
    adb("shell", "input", "keyevent", "KEYCODE_ENTER")
    wait_for(lambda: matching(screen(), "Device verified. Enter your account credentials."),
             "confirmed real device credential")

def unlock_synthetic_vault(pin):
    finish_real_device_challenge(pin)
    find_and_tap("New account? Register")
    fill("Email", "m25synthetic@example.org")
    fill("First name", "Synthetic")
    fill("Last name", "Owner")
    fill("Password", "StrongPass123!x")
    fill("First answer", "BlueSchool")
    fill("Second answer", "BlueCat")
    find_and_tap("Create private vault")
    wait_for(lambda: matching(screen(), "I have saved the recovery code"),
             "one-time synthetic recovery code presentation", seconds=65)
    find_and_tap("I have saved the recovery code")
    wait_for(locked_screen, "recovery code acknowledgement locks account")
    finish_real_device_challenge(pin)
    # Compose keeps the entered email while recovering from code acknowledgement.
    fill("Password", "StrongPass123!x")
    find_and_tap("Unlock private vault")
    wait_for(lambda: matching(screen(), "Private vault unlocked"),
             "synthetic SQLCipher vault unlocked", seconds=50)

def main():
    apk = Path('app/build/outputs/apk/debug/app-debug.apk')
    assert apk.is_file(), 'M25 debug APK missing after instrumentation'
    # connectedAndroidTest may uninstall the target package during cleanup.
    adb('install', '-r', str(apk), timeout=90)
    pin = str(secrets.randbelow(900000) + 100000)
    installed_pin = False
    try:
        adb("shell", "locksettings", "set-pin", pin)
        installed_pin = True
        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        adb("shell", "input", "keyevent", "KEYCODE_MENU")
        adb("shell", "am", "force-stop", PKG)
        adb("shell", "am", "start", "-n", ACTIVITY)
        wait_for(locked_screen, "initial locked UI")
        node = matching(screen(), "Verify this device")[0]
        tap(node)
        wait_for(keyguard_visible, "genuine Android Keyguard prompt")
        adb("shell", "input", "keyevent", "KEYCODE_BACK")
        # Some API35 Keyguard layouts consume the first BACK to dismiss the PIN keyboard.
        # Only issue a second BACK while the real system prompt remains present.
        time.sleep(1)
        if keyguard_visible():
            adb("shell", "input", "keyevent", "KEYCODE_BACK")
        wait_for(locked_screen, "canceled credential must stay locked")
        print("M25_REAL_EMULATOR_KEYGUARD_CANCEL_PASS")
        adb("shell", "am", "force-stop", PKG)
        assert not adb("shell", "sh", "-c", "pidof " + PKG + " || true").strip(), "app survived force-stop"
        adb("shell", "am", "start", "-n", ACTIVITY)
        wait_for(locked_screen, "force-stopped app must relaunch locked")
        print("M25_REAL_EMULATOR_FORCE_STOP_RELAUNCH_PASS")
        unlock_synthetic_vault(pin)
        adb("shell", "am", "force-stop", PKG)
        assert not adb("shell", "sh", "-c", "pidof " + PKG + " || true").strip(), "unlocked app survived force-stop"
        adb("shell", "am", "start", "-n", ACTIVITY)
        wait_for(locked_screen, "formerly unlocked vault relaunch must be locked")
        assert not matching(screen(), "Private vault unlocked")
        print("M25_REAL_EMULATOR_UNLOCKED_VAULT_FORCE_STOP_PASS")
    finally:
        if installed_pin:
            adb("shell", "locksettings", "clear", "--old", pin)
        adb("shell", "rm", "-f", "/sdcard/m25-window.xml")

if __name__ == "__main__":
    main()
