# BookReader

[中文](#中文) | [English](#english)

## 中文

一个简洁的 Android 本地电子书阅读器，支持 EPUB、PDF 和 CBZ 漫画。基于 [Readium](https://github.com/readium/kotlin-toolkit) 和 Jetpack Compose。

### 功能

- 导入本地 EPUB / PDF / CBZ
- 连接 OPDS 书库（如 Calibre-Web），支持局域网
- 书签、高亮、笔记、全文搜索、脚注
- 字体、字号、主题、亮度和翻页方式可调，可导入 TTF / OTF / WOFF 字体
- 阅读统计
- 备份与恢复（可包含书籍和字体文件）
- 中文 / English 界面

### 下载

在 [Releases](../../releases) 页面按 CPU 架构选择 APK：大多数手机选 `arm64-v8a`。需要 Android 7.0 及以上。

### 构建

需要 JDK 17+ 和 Android SDK（compileSdk 37）。

```bash
./gradlew assembleDebug
```

---

## English

A simple local ebook reader for Android, supporting EPUB, PDF and CBZ comics. Built on [Readium](https://github.com/readium/kotlin-toolkit) and Jetpack Compose.

### Features

- Import local EPUB / PDF / CBZ files
- Browse OPDS libraries (e.g. Calibre-Web), including on your local network
- Bookmarks, highlights, notes, full-text search, footnotes
- Adjustable font, size, theme, brightness and page turns; import TTF / OTF / WOFF fonts
- Reading stats
- Backup and restore (optionally with book and font files)
- Chinese / English UI

### Download

Pick the APK for your CPU on the [Releases](../../releases) page. Most phones need `arm64-v8a`. Requires Android 7.0 or later.

### Build

Requires JDK 17+ and the Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug
```
