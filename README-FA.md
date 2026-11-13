<div align="left">

[English](README.md)

</div>

<div align="center">

<img src="docs/logo.png" width="96" alt="لوگوی Umimuko Music"/>

# Umimuko Music

**پلیر موسیقی تمیز و آفلاین برای ویندوز، مک‌او‌اس و لینوکس — بدون استریمینگ، بدون اکانت، فقط موسیقی شما.**

[![Release](https://img.shields.io/github/v/release/real-Phraser/umimuko-music?style=flat-square&color=e05c5c)](https://github.com/real-Phraser/umimuko-music/releases)
[![Windows](https://img.shields.io/badge/Windows-10%2B-0078d4?style=flat-square&logo=windows)](https://github.com/real-Phraser/umimuko-music/releases)
[![macOS](https://img.shields.io/badge/macOS-12%2B-000000?style=flat-square&logo=apple)](https://github.com/real-Phraser/umimuko-music/releases)
[![Linux](https://img.shields.io/badge/Linux-x86__64-FCC624?style=flat-square&logo=linux&logoColor=black)](https://github.com/real-Phraser/umimuko-music/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-7f52ff?style=flat-square&logo=kotlin)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/real-Phraser/umimuko-music?style=flat-square)](LICENSE)

</div>

---

## ✨ ویژگی‌ها

- 🎵 پشتیبانی از MP3، FLAC، AAC، OGG، WAV و بیشتر
- 📁 سازمان‌دهی بر اساس آلبوم، هنرمند، پلی‌لیست و پوشه
- 🏠 صفحه‌ی خانه با قفسه‌های پین‌شده، اخیراً پخش‌شده و اخیراً اضافه‌شده
- 🎬 صفحه‌ی فول‌اسکرین با انتقال fade
- 🪟 مینی‌پلیر شناور روی همه‌ی پنجره‌ها
- 🌙 دارک و لایت مود
- 🎨 رنگ accent پویا از روی کاور آلبوم
- 📋 صف پخش با قابلیت کشیدن و انتخاب چندتایی
- ❤️ آهنگ‌های موردعلاقه، پلی‌لیست، جستجو، شافل و ریپیت

---

## 📸 اسکرین‌شات‌ها

<div align="center">

| خانه | در حال پخش |
|:---:|:---:|
| ![Home](docs/home-dark.png) | ![Now Playing](docs/now-playing.png) |

| صف پخش | مینی‌پلیر / لایت مود |
|:---:|:---:|
| ![Queue](docs/queue.png) | ![Mini Player](docs/mini-player.png) |

</div>

---

## 🛒 دانلود

به صفحه‌ی **[Releases](https://github.com/real-Phraser/umimuko-music/releases)** بروید و فایل مناسب سیستم‌عامل خود را دانلود کنید:
- **ویندوز**: فایل `.msi` یا `.exe` (شامل موتور داخلی VLC).
- **مک‌او‌اس**: فایل `.dmg` (نیازمند نصب بودن VLC نسخه‌ی ۳).
- **لینوکس**: بسته‌های `.deb`، `.rpm` یا بسته‌ی آرچ لینوکس (`.pkg.tar.zst`).

---

## 🛠️ ساخت از سورس

نیاز به JDK 17 دارید.

### مک‌او‌اس و لینوکس
```bash
# اجرای مستقیم
./gradlew run

# ساخت نسخه پرتابل
./BUILD-PORTABLE.sh

# ساخت فایل نصبی (.dmg در مک، .deb/.rpm در لینوکس)
./BUILD-INSTALLERS.sh

# ساخت بسته آرچ لینوکس
./gradlew packageArchLinux
```

### ویندوز
```cmd
# اجرای مستقیم
.\gradlew.bat run

# ساخت نسخه پرتابل
.\BUILD-PORTABLE.cmd

# ساخت نصب‌کننده (.exe / .msi)
.\BUILD-INSTALLERS.cmd
```

> **نکته برای توسعه‌دهندگان ویندوز:** برای ساخت بسته‌های ویندوز نیاز به VLC نسخه‌ی ۳ (۶۴ بیتی) است. اسکریپت بیلد به صورت خودکار آن را دانلود می‌کند یا می‌توانید به شکل دستی آن را قرار دهید. برای توضیحات بیشتر [راهنمای راه‌اندازی VLC](docs/README-VLC-Setup.md) را ببینید.

---

## 🔧 ساخته‌شده با

- [Kotlin 2.2](https://kotlinlang.org) + [Compose Desktop 1.9](https://www.jetbrains.com/lp/compose-multiplatform/)
- [Material 3](https://m3.material.io/)
- [JAudioTagger](https://www.jthink.net/jaudiotagger/)
- [JNA](https://github.com/java-native-access/jna)

---

## 📄 لایسنس

این پروژه تحت شرایط فایل [LICENSE](LICENSE) منتشر شده.

---

<div align="center">

ساخته‌شده توسط **[Phraser](https://github.com/real-Phraser)**

سپاس بیکران از **[Genie](https://github.com/actually-genius)** و **[Ari](https://github.com/itsarian)**

[![Telegram](https://img.shields.io/badge/Telegram-@theCodePhraser-2CA5E0?style=flat-square&logo=telegram)](https://t.me/theCodePhraser)
[![GitHub](https://img.shields.io/badge/GitHub-real--Phraser-181717?style=flat-square&logo=github)](https://github.com/real-Phraser)

</div>
