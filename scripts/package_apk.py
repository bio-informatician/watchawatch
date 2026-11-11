import zipfile
import subprocess
import os
import shutil

src_apk = "/home/agent/agent-work/projects/watchawatch/app/build/outputs/apk/debug/app-debug.apk"
linked_apk = "/home/agent/agent-work/scratch/linked_manifest.apk"
new_classes3 = "/home/agent/agent-work/scratch/dex_out/classes.dex"
unaligned_apk = "/home/agent/agent-work/scratch/unaligned_v150.apk"
aligned_apk = "/home/agent/agent-work/scratch/aligned_v150.apk"
final_versioned_apk = "/home/agent/agent-work/watchawatch-v1.5.0.apk"
final_generic_apk = "/home/agent/agent-work/watchawatch.apk"

assert os.path.exists(src_apk), f"Missing {src_apk}"
assert os.path.exists(linked_apk), f"Missing {linked_apk}"
assert os.path.exists(new_classes3), f"Missing {new_classes3}"

# Read files from linked_apk to inject
linked_files = {}
with zipfile.ZipFile(linked_apk, 'r') as lz:
    for name in lz.namelist():
        if name == 'AndroidManifest.xml' or name.startswith('res/drawable') or name.startswith('res/mipmap'):
            linked_files[name] = lz.read(name)

# Ensure both res/drawable/ic_stat_watch.xml and res/drawable-v21/ic_stat_watch.xml exist with full vector
valid_stat_vector = linked_files.get('res/drawable-v21/ic_stat_watch.xml')
if valid_stat_vector:
    linked_files['res/drawable/ic_stat_watch.xml'] = valid_stat_vector
    linked_files['res/drawable-v21/ic_stat_watch.xml'] = valid_stat_vector
    print(f"Validated ic_stat_watch vector: {len(valid_stat_vector)} bytes")

exclude_from_src = set(linked_files.keys()) | {
    'classes3.dex',
    'AndroidManifest.xml',
    'res/drawable/ic_stat_watch.xml',
    'res/drawable-v21/ic_stat_watch.xml'
}

print(f"Reading {src_apk}...")
with zipfile.ZipFile(src_apk, 'r') as zin, zipfile.ZipFile(unaligned_apk, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        fn = item.filename
        if fn.startswith('META-INF/') and (fn.endswith('.RSA') or fn.endswith('.SF') or fn.endswith('.MF')):
            continue
        if fn in exclude_from_src:
            continue
        if fn.startswith('res/mipmap/'):
            continue
        data = zin.read(fn)
        zout.writestr(item, data)

    # Add new classes3.dex
    print("Adding new classes3.dex...")
    with open(new_classes3, 'rb') as f:
        zinfo = zipfile.ZipInfo('classes3.dex')
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, f.read())

    # Add all updated resources and manifest from linked_manifest
    print(f"Adding {len(linked_files)} updated manifest and resource files...")
    for fn, data in linked_files.items():
        zinfo = zipfile.ZipInfo(fn)
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, data)

print("Unaligned APK created.")

# Zipalign
sdk_bin = "/home/agent/agent-work/bin/android-sdk/build-tools/34.0.0"
subprocess.run([f"{sdk_bin}/zipalign", "-p", "-f", "4", unaligned_apk, aligned_apk], check=True)
print("Zipalign complete.")

# Apksigner
keystore = "/home/agent/agent-work/.android/debug.keystore"
os.environ["JAVA_HOME"] = "/home/agent/agent-work/bin/jdk-17"
os.environ["PATH"] = "/home/agent/agent-work/bin/jdk-17/bin:" + os.environ["PATH"]
subprocess.run([
    f"{sdk_bin}/apksigner", "sign",
    "--ks", keystore,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    aligned_apk
], check=True)
print("APK signing complete.")

# Verify
res = subprocess.run([f"{sdk_bin}/apksigner", "verify", "-v", aligned_apk], stdout=subprocess.PIPE, text=True, check=True)
print("Verification result:\n" + res.stdout)

# Copy to final destinations
shutil.copy(aligned_apk, final_versioned_apk)
shutil.copy(aligned_apk, final_generic_apk)
shutil.copy(aligned_apk, "/home/agent/agent-work/projects/watchawatch/watchawatch-v1.5.0.apk")

print(f"SUCCESS: Generated {final_versioned_apk} ({os.path.getsize(final_versioned_apk) / (1024*1024):.1f} MB)")
print(f"SUCCESS: Staged at {final_generic_apk}")
