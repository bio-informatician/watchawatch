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

apk_filename = "watchawatch-v1.3.0.apk"
apk_path = os.path.expanduser(f"~/agent-work/{apk_filename}")
if not os.path.exists(apk_path):
    print(f"ERROR: APK not found at {apk_path}")
    sys.exit(1)

file_size_mb = os.path.getsize(apk_path) / (1024 * 1024)
print(f"Uploading {apk_filename} ({file_size_mb:.1f} MB) to Telegram...")

with open(apk_path, "rb") as f:
    apk_data = f.read()

boundary = "----WebKitFormBoundaryWatchaWatchUploadV130x7Z"
body = bytearray()

# chat_id
body.extend(f"--{boundary}\r\n".encode())
body.extend(b'Content-Disposition: form-data; name="chat_id"\r\n\r\n')
body.extend(f"{chat_id}\r\n".encode())

# caption (must be < 1024 chars for Telegram Bot API)
caption = (
    "⏱️ WatchaWatch v1.3.0 (Build 130)\n\n"
    "📌 برطرف‌سازی ۳ ایراد ویجت شناور و اعلان:\n\n"
    "1️⃣ مخفی شدن هوشمند فلوت هنگام بازگشت به برنامه:\n"
    "  • با لمس فلوت یا ورود به برنامه، ویجت شناور مخفی شده و فقط برنامه دیده می‌شود.\n"
    "  • هنگام مینیمایز یا خروج به صفحه اصلی، فلوت شناور دوباره نمایش داده می‌شود.\n\n"
    "2️⃣ امکان خاموش کردن و حذف فلوت درون خودش:\n"
    "  • دکمه سریع (✕) در گوشه ویجت برای بستن فوری.\n"
    "  • امکان کشیدن (درگ) به گوشه‌ها یا پایین صفحه برای حذف ویجت (Drop to remove).\n\n"
    "3️⃣ اصلاح آیکون ساعت نوار بالا و متن فلوت:\n"
    "  • اصلاح کامل آیکون بالای صفحه از مستطیل سیاه به ساعت استاندارد متریال.\n"
    "  • رفع متن طولانی مسیر فایل و نمایش عنوان کوتاه تسک به همراه ثانیه‌شمار.\n\n"
    "🔒 ۱۰۰٪ آفلاین، بدون اینترنت و ساین‌شده (v2+v3)"
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
