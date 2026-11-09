import zipfile
import subprocess
import os

src_apk = "/home/agent/agent-work/projects/watchawatch/app/build/outputs/apk/debug/app-debug.apk"
new_classes3 = "/home/agent/agent-work/scratch/dex_out/classes.dex"
new_manifest = "/home/agent/agent-work/scratch/apk_staging/AndroidManifest.xml"
unaligned_apk = "/home/agent/agent-work/scratch/unaligned_new.apk"
aligned_apk = "/home/agent/agent-work/scratch/aligned_new.apk"

print(f"Reading {src_apk}...")
with zipfile.ZipFile(src_apk, 'r') as zin, zipfile.ZipFile(unaligned_apk, 'w', compression=zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        fn = item.filename
        # Exclude signatures, old classes3.dex, and old manifest
        if fn.startswith('META-INF/') and (fn.endswith('.RSA') or fn.endswith('.SF') or fn.endswith('.MF')):
            continue
        if fn == 'classes3.dex' or fn == 'AndroidManifest.xml':
            continue
        data = zin.read(fn)
        # Store uncompressed for .arsc and already compressed formats or according to original flag
        zout.writestr(item, data)

    # Now add new classes3.dex
    print("Adding new classes3.dex...")
    with open(new_classes3, 'rb') as f:
        # store uncompressed for dex files (recommended for Android ART performance)
        zinfo = zipfile.ZipInfo('classes3.dex')
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, f.read())

    # Add new AndroidManifest.xml
    print("Adding new AndroidManifest.xml...")
    with open(new_manifest, 'rb') as f:
        zinfo = zipfile.ZipInfo('AndroidManifest.xml')
        zinfo.compress_type = zipfile.ZIP_DEFLATED
        zout.writestr(zinfo, f.read())

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
