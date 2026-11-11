import zipfile
import subprocess
import os
import shutil
import sys

JDK_HOME = "/home/agent/agent-work/bin/jdk-17"
SDK_BIN = "/home/agent/agent-work/bin/android-sdk/build-tools/34.0.0"
ANDROID_JAR = "/home/agent/agent-work/bin/android-sdk/platforms/android-34/android.jar"
PROJECT_ROOT = "/home/agent/agent-work/projects/watchawatch"
KEYSTORE = f"{PROJECT_ROOT}/watchawatch-release.keystore"
KEYSTORE_PASS = "watchawatch2026"

RES_DIR = f"{PROJECT_ROOT}/app/src/main/res"
MANIFEST_PATH = f"{PROJECT_ROOT}/app/src/main/AndroidManifest.xml"
STABLE_IDS = f"{PROJECT_ROOT}/app/build/intermediates/stable_resource_ids_file/debug/processDebugResources/stableIds.txt"
SRC_APK = f"{PROJECT_ROOT}/app/build/outputs/apk/debug/app-debug.apk"

COMPILED_RES = "/home/agent/agent-work/scratch/compiled_res.zip"
LINKED_APK = "/home/agent/agent-work/scratch/linked_manifest.apk"
NEW_CLASSES3 = "/home/agent/agent-work/scratch/dex_out/classes.dex"
UNALIGNED_APK = "/home/agent/agent-work/scratch/unaligned_v152.apk"
ALIGNED_APK = "/home/agent/agent-work/scratch/aligned_v152.apk"
FINAL_VERSIONED_APK = "/home/agent/agent-work/watchawatch-v1.5.2.apk"
FINAL_GENERIC_APK = "/home/agent/agent-work/watchawatch.apk"
PROJECT_COPY_APK = f"{PROJECT_ROOT}/watchawatch-v1.5.2.apk"

assert os.path.exists(SRC_APK), f"Missing {SRC_APK}"
assert os.path.exists(NEW_CLASSES3), f"Missing {NEW_CLASSES3}"
assert os.path.exists(RES_DIR), f"Missing {RES_DIR}"
assert os.path.exists(MANIFEST_PATH), f"Missing {MANIFEST_PATH}"
assert os.path.exists(STABLE_IDS), f"Missing {STABLE_IDS}"

os.environ["JAVA_HOME"] = JDK_HOME
os.environ["PATH"] = f"{JDK_HOME}/bin:" + os.environ.get("PATH", "")

print("1. Compiling resources via aapt2 compile...")
subprocess.run([f"{SDK_BIN}/aapt2", "compile", "--dir", RES_DIR, "-o", COMPILED_RES], check=True)

print("2. Linking manifest and resources via aapt2 link with --stable-ids...")
subprocess.run([
    f"{SDK_BIN}/aapt2", "link",
    "-I", ANDROID_JAR,
    "--min-sdk-version", "26",
    "--target-sdk-version", "34",
    "--manifest", MANIFEST_PATH,
    "--stable-ids", STABLE_IDS,
    "-o", LINKED_APK,
    COMPILED_RES
], check=True)

print("3. Reading linked files to inject into APK...")
linked_files = {}
with zipfile.ZipFile(LINKED_APK, 'r') as lz:
    for name in lz.namelist():
        if name == 'AndroidManifest.xml' or name.startswith('res/drawable') or name.startswith('res/mipmap') or name.startswith('res/xml'):
            linked_files[name] = lz.read(name)

# Ensure both res/drawable/ and res/drawable-v21/ have full vector implementations
if 'res/drawable-v21/ic_stat_watch.xml' in linked_files:
    valid_stat_vector = linked_files['res/drawable-v21/ic_stat_watch.xml']
    linked_files['res/drawable/ic_stat_watch.xml'] = valid_stat_vector
    linked_files['res/drawable-v21/ic_stat_watch.xml'] = valid_stat_vector
    print(f"Validated ic_stat_watch vector: {len(valid_stat_vector)} bytes")

if 'res/drawable-v21/ic_launcher_foreground.xml' in linked_files:
    valid_launcher_vector = linked_files['res/drawable-v21/ic_launcher_foreground.xml']
    linked_files['res/drawable/ic_launcher_foreground.xml'] = valid_launcher_vector
    linked_files['res/drawable-v21/ic_launcher_foreground.xml'] = valid_launcher_vector
    print(f"Validated ic_launcher_foreground vector: {len(valid_launcher_vector)} bytes")

exclude_from_src = set(linked_files.keys()) | {
    'classes3.dex',
    'AndroidManifest.xml',
    'res/drawable/ic_stat_watch.xml',
    'res/drawable-v21/ic_stat_watch.xml',
    'res/drawable/ic_launcher_foreground.xml',
    'res/drawable-v21/ic_launcher_foreground.xml'
}

print(f"4. Packaging APK from base {SRC_APK}...")
with zipfile.ZipFile(SRC_APK, 'r') as zin, zipfile.ZipFile(UNALIGNED_APK, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        fn = item.filename
        if fn.startswith('META-INF/') and (fn.endswith('.RSA') or fn.endswith('.SF') or fn.endswith('.MF')):
            continue
        if fn in exclude_from_src or fn.startswith('res/mipmap/'):
            continue
        data = zin.read(fn)
        zout.writestr(item, data)

    # Add new classes3.dex
    print("Adding classes3.dex...")
    with open(NEW_CLASSES3, 'rb') as f:
        zinfo = zipfile.ZipInfo('classes3.dex')
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, f.read())

    # Add all updated resources and manifest from linked_manifest
    print(f"Adding {len(linked_files)} updated manifest and resource files...")
    for fn, data in linked_files.items():
        if fn == "resources.arsc":
            continue
        zinfo = zipfile.ZipInfo(fn)
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, data)

print("5. Zipalign APK (4-byte alignment)...")
subprocess.run([f"{SDK_BIN}/zipalign", "-p", "-f", "4", UNALIGNED_APK, ALIGNED_APK], check=True)

print("6. Signing APK with v2 + v3 schemes via apksigner using release key...")
subprocess.run([
    f"{SDK_BIN}/apksigner", "sign",
    "--ks", KEYSTORE,
    "--ks-pass", f"pass:{KEYSTORE_PASS}",
    "--key-pass", f"pass:{KEYSTORE_PASS}",
    ALIGNED_APK
], check=True)

print("7. Verifying APK signature...")
res = subprocess.run([f"{SDK_BIN}/apksigner", "verify", "-v", ALIGNED_APK], stdout=subprocess.PIPE, text=True, check=True)
print("Signature verification:\n" + res.stdout)

print("8. Validating package badging (label and launcher icon)...")
badging = subprocess.run([f"{SDK_BIN}/aapt2", "dump", "badging", ALIGNED_APK], stdout=subprocess.PIPE, text=True, check=True).stdout
for line in badging.splitlines():
    if "icon" in line or "label" in line or "launchable" in line or "sdk" in line.lower():
        print("  " + line)

assert "application: label='WatchaWatch' icon='res/mipmap-anydpi-v26/ic_launcher.xml'" in badging, "Application icon missing or corrupt!"
assert "launchable-activity: name='com.watchawatch.app.MainActivity'  label='WatchaWatch' icon='res/mipmap-anydpi-v26/ic_launcher.xml'" in badging, "Launchable activity icon missing or corrupt!"
assert "sdkVersion:'26'" in badging, "sdkVersion 26 missing!"
assert "targetSdkVersion:'34'" in badging, "targetSdkVersion 34 missing!"
assert "targetSdkVersion < 4" not in badging, "Legacy implied permissions present!"

# Copy to final destinations
shutil.copy(ALIGNED_APK, FINAL_VERSIONED_APK)
shutil.copy(ALIGNED_APK, FINAL_GENERIC_APK)
shutil.copy(ALIGNED_APK, PROJECT_COPY_APK)

print(f"SUCCESS: Generated {FINAL_VERSIONED_APK} ({os.path.getsize(FINAL_VERSIONED_APK) / (1024*1024):.1f} MB)")
print(f"SUCCESS: Staged at {FINAL_GENERIC_APK}")
