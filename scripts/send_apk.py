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

apk_path = os.path.expanduser("~/agent-work/watchawatch.apk")
if not os.path.exists(apk_path):
    print(f"ERROR: APK not found at {apk_path}")
    sys.exit(1)

file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
print(f"Uploading watchawatch.apk ({file_size_mb:.1f} MB) to Telegram...")

with open(apk_path, "rb") as f:
    apk_data = f.read()

boundary = "----WebKitFormBoundaryWatchaWatchUpload9xL7vB2k"
body = bytearray()

# chat_id
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="chat_id"\r\n\r\n')
body.extend(f"{chat_id}\r\n".encode())

# caption
caption = (
    "⏱️ نسخه جدید WatchaWatch (رفع مشکل نمایش 0m در بخش گزارش‌ها و دقت ثانیه‌ها)\n\n"
    "✅ رفع باگ نمایش 0m در گزارش‌ها (Reports) و تمام صفحات:\n"
    "  • علت: متد formatDuration قبلاً زمان را بر 60000 تقسیم می‌کرد و سشن‌ها یا مجموع‌های کمتر از ۱ دقیقه گرد به 0m می‌شدند، هرچند نمودارها و تعداد سشن‌ها مقادیر را درست می‌دیدند.\n"
    "  • اصلاح: زمان‌های زیر ۱ دقیقه اکنون با ثانیه دقیق (مانند 25s یا 45s) و سشن‌های کوتاه با دقیقه و ثانیه (مانند 1m 20s) نمایش داده می‌شوند.\n"
    "  • میانگین روزانه (Daily Average) و پروژه‌ها دیگر حتی برای زمان‌های کم صفر نمایش داده نمی‌شوند.\n"
    "  • تعداد نشست‌ها (Sessions) در تب Reports بر اساس سشن‌های هفته جاری فیلتر و محاسبه می‌شود.\n\n"
    "✅ امکانات نسخه قبل (صفحه قفل، کرونومتر زنده و دیالوگ کامل سشن‌ها) فعال است.\n"
    "🔒 ۱۰۰٪ آفلاین، بدون دسترسی اینترنت و ساین‌شده (v2+v3)"
)
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="caption"\r\n\r\n')
body.extend(caption.encode("utf-8") + b"\r\n")

# document
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="document"; filename="watchawatch.apk"\r\n')
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
