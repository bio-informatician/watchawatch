import os
import sys
import urllib.request
import urllib.error

env_path = os.path.expanduser("~/.telegram_env")
token, chat_id = None, None
if os.path.exists(env_path):
    with open(env_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line.startswith("TELEGRAM_BOT_TOKEN="):
                token = line.split("=", 1)[1].strip("\"'")
            elif line.startswith("TELEGRAM_ALLOWED_ID="):
                chat_id = line.split("=", 1)[1].strip("\"'")

if not token or not chat_id:
    print("ERROR: Telegram credentials not found")
    sys.exit(1)

apk_filename = "watchawatch-v1.5.1.apk"
apk_path = os.path.expanduser(f"~/agent-work/{apk_filename}")
if not os.path.exists(apk_path):
    print(f"ERROR: APK not found at {apk_path}")
    sys.exit(1)

file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
print(f"Uploading {apk_filename} ({file_size_mb:.1f} MB) to Telegram...")

with open(apk_path, "rb") as f:
    apk_data = f.read()

boundary = "----WebKitFormBoundaryWatchaWatchUploadV151x7K"
body = bytearray()

# chat_id
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="chat_id"\r\n\r\n')
body.extend(f"{chat_id}\r\n".encode())

# caption (must be < 1024 chars for Telegram Bot API)
caption = (
    "⏱️ WatchaWatch v1.5.1 (Build 151)\n\n"
    "🎯 رفع مشکل عدم نمایش آیکون در لیست برنامه‌ها (App Drawer / Launcher):\n\n"
    "1️⃣ همگام‌سازی کامل شناسه منابع با جدول پایدار (Stable Resource IDs):\n"
    "  • ارجاع مستقیم آیکون در مانیفست به شناسه واقعی mipmap/ic_launcher (0x7f080000).\n"
    "  • تعریف صریح آیکون و برچسب هم در سطح Application و هم در سطح MainActivity.\n"
    "  • اصلاح لینک لایه‌های Adaptive Icon (پس‌زمینه و پیش‌زمینه طلایی-زمردی).\n\n"
    "2️⃣ پوشش کامل انواع لانچرهای اندروید:\n"
    "  • قرارگیری آیکون استاندارد و گرد (ic_launcher_round) در تمامی تراکم‌ها (mdpi تا xxxhdpi).\n"
    "  • افزایش شماره نسخه به 151 جهت پاک‌سازی کش کشوی برنامه‌های گوشی.\n\n"
    "🔒 ۱۰۰٪ آفلاین، بدون اینترنت و ساین‌شده معتبر (v2+v3)"
)
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="caption"\r\n\r\n')
body.extend(caption.encode("utf-8") + b"\r\n")

# document
body.extend(f"--{boundary}\r\n".encode())
body.extend(f'Content-Disposition: form-data; name="document"; filename="{apk_filename}"\r\n'.encode())
body.extend(b"Content-Type: application/vnd.android.package-archive\r\n\r\n")
body.extend(apk_data)
body.extend(f"\r\n--{boundary}--\r\n".encode())

url = f"https://api.telegram.org/bot{token}/sendDocument"
req = urllib.request.Request(url, data=bytes(body))
req.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")

try:
    with urllib.request.urlopen(req, timeout=120) as resp:
        if resp.status == 200:
            print("TELEGRAM_DISPATCH_SUCCESS")
        else:
            print(f"TELEGRAM_HTTP_{resp.status}")
except urllib.error.HTTPError as e:
    err_body = e.read().decode("utf-8", errors="replace")
    print(f"TELEGRAM_HTTP_ERROR: {e.code} - {err_body}")
except Exception as e:
    print(f"TELEGRAM_ERROR: {e}")
