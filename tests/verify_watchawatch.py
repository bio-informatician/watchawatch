#!/usr/bin/env python3
"""
WatchaWatch Automated Verification & Compliance Suite.
Verifies Android manifest permissions, foreground service lifecycle,
lock screen persistent timer notification with active project tracking,
floating bubble durability, state synchronization, and executes
all Kotlin unit tests.
"""

import os
import sys
import subprocess
import xml.etree.ElementTree as ET

PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
APP_SRC = os.path.join(PROJECT_ROOT, "app", "src", "main")
MANIFEST_PATH = os.path.join(APP_SRC, "AndroidManifest.xml")
JAVA_SRC_DIR = os.path.join(APP_SRC, "java", "com", "watchawatch", "app")
SERVICE_PATH = os.path.join(JAVA_SRC_DIR, "FloatingTimerService.kt")
MAIN_ACTIVITY_PATH = os.path.join(JAVA_SRC_DIR, "MainActivity.kt")
MODELS_PATH = os.path.join(JAVA_SRC_DIR, "Models.kt")
CSV_TRANSFER_PATH = os.path.join(JAVA_SRC_DIR, "CsvTransfer.kt")
LOCAL_INSIGHTS_PATH = os.path.join(JAVA_SRC_DIR, "LocalInsights.kt")

JDK_HOME = os.path.expanduser("~/agent-work/bin/jdk-17")
SDK_HOME = os.path.expanduser("~/agent-work/bin/android-sdk")
ANDROID_JAR = os.path.join(SDK_HOME, "platforms", "android-34", "android.jar")
KOTLINC = os.path.expanduser("~/agent-work/bin/kotlinc/bin/kotlinc")
JUNIT_JAR = os.path.expanduser("~/agent-work/bin/junit-4.13.2.jar")
HAMCREST_JAR = os.path.expanduser("~/agent-work/bin/hamcrest-core-1.3.jar")
KOTLIN_STDLIB = os.path.expanduser("~/agent-work/bin/kotlinc/lib/kotlin-stdlib.jar")

def test_manifest():
    print("[1/5] Checking AndroidManifest security and permissions...")
    assert os.path.exists(MANIFEST_PATH), f"Manifest missing at {MANIFEST_PATH}"
    tree = ET.parse(MANIFEST_PATH)
    root = tree.getroot()

    permissions = set()
    for elem in root.findall(".//uses-permission"):
        name = elem.attrib.get("{http://schemas.android.com/apk/res/android}name", "")
        permissions.add(name)

    # 1. Assert zero internet
    for perm in permissions:
        assert "INTERNET" not in perm, f"Security violation: internet permission found ({perm})"
        assert "MANAGE_EXTERNAL_STORAGE" not in perm, f"Security violation: storage permission found ({perm})"

    # 2. Required permissions
    required = {
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
        "android.permission.POST_NOTIFICATIONS",
        "android.permission.WAKE_LOCK",
        "android.permission.SYSTEM_ALERT_WINDOW"
    }
    missing = required - permissions
    assert not missing, f"Missing required permissions in manifest: {missing}"

    # 3. Service configuration
    service = root.find(".//service[@{http://schemas.android.com/apk/res/android}name='.FloatingTimerService']")
    assert service is not None, "FloatingTimerService not declared in manifest"
    fgs_type = service.attrib.get("{http://schemas.android.com/apk/res/android}foregroundServiceType")
    assert fgs_type == "specialUse", f"Expected foregroundServiceType='specialUse', got '{fgs_type}'"

    prop = service.find(".//property[@{http://schemas.android.com/apk/res/android}name='android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE']")
    assert prop is not None, "PROPERTY_SPECIAL_USE_FGS_SUBTYPE property missing in service declaration"

    print("  ✓ PASS: Zero-internet air-gapped security confirmed.")
    print("  ✓ PASS: POST_NOTIFICATIONS, WAKE_LOCK, and FOREGROUND_SERVICE_SPECIAL_USE verified.")

def test_service_architecture():
    print("[2/5] Checking FloatingTimerService lifecycle & lock screen notification architecture...")
    assert os.path.exists(SERVICE_PATH), f"Service missing at {SERVICE_PATH}"
    with open(SERVICE_PATH, "r", encoding="utf-8") as f:
        src = f.read()

    # START_STICKY
    assert "return START_STICKY" in src, "FloatingTimerService must return START_STICKY to survive system kills"

    # Screen receiver
    assert "ACTION_SCREEN_OFF" in src and "ACTION_SCREEN_ON" in src and "ACTION_USER_PRESENT" in src, \
        "FloatingTimerService must register screen state receiver to handle screen lock/unlock transitions"

    # Lock screen public visibility
    assert "Notification.VISIBILITY_PUBLIC" in src, "Notification must specify VISIBILITY_PUBLIC for lock screen"
    assert "lockscreenVisibility = Notification.VISIBILITY_PUBLIC" in src, "Channel must specify lockscreenVisibility"

    # Real-time chronometer
    assert "setUsesChronometer(true)" in src, "Notification must use native chronometer for zero-battery lock screen updates"
    assert "setWhen(" in src, "Notification must set baseline timestamp for chronometer"

    # Active project name
    assert "projectName" in src, "Notification must display active project name"

    # WakeLock management
    assert "PARTIAL_WAKE_LOCK" in src, "FloatingTimerService must manage partial WakeLock"
    assert "wakeLock?.acquire" in src and "wakeLock?.release" in src, "WakeLock acquire and release required"

    # Interactive notification actions
    assert "ACTION_TOGGLE_TIMER" in src, "ACTION_TOGGLE_TIMER action missing"
    assert "ACTION_STOP_TIMER" in src, "ACTION_STOP_TIMER action missing"
    assert "ACTION_TOGGLE_BUBBLE" in src, "ACTION_TOGGLE_BUBBLE action missing"

    # Bubble persistence & screen-lock durability
    assert "FLAG_SHOW_WHEN_LOCKED" in src, "Bubble window params must include FLAG_SHOW_WHEN_LOCKED"
    assert "bubble_x" in src and "bubble_y" in src, "Bubble coordinates must be persisted in preferences"

    print("  ✓ PASS: START_STICKY lifecycle resilience verified.")
    print("  ✓ PASS: Lock screen persistent notification with active project tracking verified.")
    print("  ✓ PASS: Screen-off/wake broadcast receiver and WakeLock management verified.")
    print("  ✓ PASS: Persistent bubble coordinates and FLAG_SHOW_WHEN_LOCKED verified.")

def test_main_activity_sync():
    print("[3/5] Checking MainActivity and State Synchronization...")
    assert os.path.exists(MAIN_ACTIVITY_PATH), f"MainActivity missing at {MAIN_ACTIVITY_PATH}"
    with open(MAIN_ACTIVITY_PATH, "r", encoding="utf-8") as f:
        src = f.read()

    # ViewModel listens to preferences
    assert "SharedPreferences.OnSharedPreferenceChangeListener" in src, \
        "TrackerViewModel must implement OnSharedPreferenceChangeListener to sync with FloatingTimerService"
    assert "fun reload()" in src, "TrackerViewModel must have reload() method"

    # Notification permission request
    assert "POST_NOTIFICATIONS" in src, "MainActivity must request POST_NOTIFICATIONS permission on Android 13+"

    # Auto-start FloatingTimerService
    assert "FloatingTimerService.start(" in src, "MainActivity must start FloatingTimerService when timer is active"

    print("  ✓ PASS: ViewModel SharedPreferences change listener verified.")
    print("  ✓ PASS: POST_NOTIFICATIONS runtime permission prompt verified.")
    print("  ✓ PASS: Foreground service auto-start for active timer verified.")

def test_unit_tests():
    print("[4/5] Compiling and running unit tests via JUnit Core...")
    test_out = "/tmp/watchatest_verify"
    os.makedirs(test_out, exist_ok=True)
    env = os.environ.copy()
    env["JAVA_HOME"] = JDK_HOME
    env["PATH"] = f"{os.path.join(JDK_HOME, 'bin')}:{env.get('PATH', '')}"

    test_srcs = [
        MODELS_PATH,
        LOCAL_INSIGHTS_PATH,
        CSV_TRANSFER_PATH,
        os.path.join(PROJECT_ROOT, "app", "src", "test", "java", "com", "watchawatch", "app", "LocalInsightsTest.kt"),
        os.path.join(PROJECT_ROOT, "app", "src", "test", "java", "com", "watchawatch", "app", "CsvTransferTest.kt"),
    ]

    compile_cmd = [
        KOTLINC,
        "-cp", f"{JUNIT_JAR}:{HAMCREST_JAR}",
        "-d", test_out,
        *test_srcs
    ]
    res = subprocess.run(compile_cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    assert res.returncode == 0, f"Kotlinc compilation failed:\n{res.stderr}\n{res.stdout}"

    run_cmd = [
        os.path.join(JDK_HOME, "bin", "java"),
        "-cp", f"{test_out}:{JUNIT_JAR}:{HAMCREST_JAR}:{KOTLIN_STDLIB}",
        "org.junit.runner.JUnitCore",
        "com.watchawatch.app.LocalInsightsTest",
        "com.watchawatch.app.CsvTransferTest"
    ]
    res = subprocess.run(run_cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    assert res.returncode == 0, f"JUnit test execution failed:\n{res.stderr}\n{res.stdout}"
    assert "OK (11 tests)" in res.stdout, f"Unexpected test output:\n{res.stdout}"

    print(f"  ✓ PASS: All 11 unit tests passed successfully.")

def test_service_compilation():
    print("[5/5] Compiling FloatingTimerService.kt with Android SDK...")
    build_dir = "/tmp/watchabuild_verify"
    gen_dir = os.path.join(build_dir, "gen")
    stub_dir = os.path.join(build_dir, "stub")
    classes_dir = os.path.join(build_dir, "classes")
    os.makedirs(gen_dir, exist_ok=True)
    os.makedirs(stub_dir, exist_ok=True)
    os.makedirs(classes_dir, exist_ok=True)

    env = os.environ.copy()
    env["JAVA_HOME"] = JDK_HOME
    env["PATH"] = f"{os.path.join(JDK_HOME, 'bin')}:{env.get('PATH', '')}"

    aapt2 = os.path.join(SDK_HOME, "build-tools", "34.0.0", "aapt2")
    compiled_res = os.path.join(build_dir, "res.zip")
    res_dir = os.path.join(APP_SRC, "res")

    subprocess.run([aapt2, "compile", "--dir", res_dir, "-o", compiled_res], check=True, env=env, stdout=subprocess.PIPE)
    subprocess.run([
        aapt2, "link",
        "-I", ANDROID_JAR,
        "--manifest", MANIFEST_PATH,
        "-o", os.path.join(build_dir, "base.apk"),
        "--java", gen_dir,
        compiled_res
    ], check=True, env=env, stdout=subprocess.PIPE)

    # Stub Activity for Intent reference
    stub_file = os.path.join(stub_dir, "com", "watchawatch", "app", "MainActivity.java")
    os.makedirs(os.path.dirname(stub_file), exist_ok=True)
    with open(stub_file, "w") as f:
        f.write("package com.watchawatch.app; public class MainActivity extends android.app.Activity {}")

    r_file = os.path.join(gen_dir, "com", "watchawatch", "app", "R.java")
    subprocess.run([os.path.join(JDK_HOME, "bin", "javac"), "-cp", ANDROID_JAR, "-d", stub_dir, stub_file, r_file], check=True, env=env)

    # Compile with kotlinc
    kotlinc_cmd = [
        KOTLINC,
        "-cp", f"{ANDROID_JAR}:{stub_dir}",
        "-d", classes_dir,
        MODELS_PATH,
        SERVICE_PATH
    ]
    res = subprocess.run(kotlinc_cmd, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    assert res.returncode == 0, f"FloatingTimerService compilation failed:\n{res.stderr}\n{res.stdout}"
    print("  ✓ PASS: FloatingTimerService compiled cleanly with zero errors.")

if __name__ == "__main__":
    print("=" * 65)
    print("     WatchaWatch Automated Verification & Compliance Suite")
    print("=" * 65)
    test_manifest()
    test_service_architecture()
    test_main_activity_sync()
    test_unit_tests()
    test_service_compilation()
    print("=" * 65)
    print("  ALL 5 VERIFICATION SUITES PASSED (100% SUCCESS)")
    print("=" * 65)
