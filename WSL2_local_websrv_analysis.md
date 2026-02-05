# Legado 轉換為 WSL2 本地書籍服務可行性分析

## 專案概述

**目標**: 將 Legado 改成一個在 WSL2 下運行的服務，讀取本地書籍目錄，透過瀏覽器看小說、聽小說。

## 可行性評估摘要

| 評估項目 | 結論 |
|---------|------|
| **技術可行性** | ✅ **高度可行** |
| **可重用程式碼** | ~70-80% (核心邏輯 + 現有 Web 元件) |
| **需重寫程式碼** | ~20-30% (Android 平台層) |
| **預估工作量** | 4-8 週 (單人全職) / MVP: 2-3 週 |
| **推薦方案** | Kotlin/JVM + Ktor + 現有 Vue 前端 |

---

## 1. 為什麼這個方案高度可行？

### 現有可重用元件

| 元件 | 狀態 | 說明 |
|------|------|------|
| **Web API** | ✅ 已存在 | NanoHTTPD 實作的完整 REST API |
| **Vue 前端** | ✅ 已存在 | 完整的書架 + 閱讀器 UI |
| **書籍解析** | ✅ 90% 可用 | EPUB/MOBI/TXT 解析器幾乎無 Android 依賴 |
| **HTTP TTS** | ✅ 可移植 | 基於 HTTP 的 TTS 引擎 |
| **規則引擎** | ✅ 可重用 | XPath/CSS/Regex/JSONPath 解析 |

### 現有 Web API 端點

已經實作的 API (在 `app/src/main/java/io/legado/app/web/HttpServer.kt`):

```
GET  /getBookshelf          # 取得書架
GET  /getChapterList        # 取得章節列表
GET  /getBookContent        # 取得章節內容
POST /saveBookProgress      # 儲存閱讀進度
GET  /cover                 # 取得封面圖片
POST /addLocalBook          # 上傳本地書籍
```

### 現有 Vue 前端功能

位於 `modules/web/src/` 和 `app/src/main/assets/web/vue/`:

- **BookShelf.vue** - 書架頁面
- **BookChapter.vue** - 閱讀器頁面
- **ReadSettings.vue** - 閱讀設定 (字體、主題、間距)
- **PopCatalog.vue** - 目錄彈窗
- 7 種閱讀主題 (含夜間模式)
- 自訂字體支援

---

## 2. 架構方案

### 推薦: Kotlin/JVM + Ktor

```
┌─────────────────────────────────────────────────────────┐
│                    瀏覽器 (Chrome/Edge)                  │
│  ┌─────────────────────────────────────────────────┐   │
│  │            Vue.js 前端 (現有)                     │   │
│  │  • 書架頁面  • 閱讀器  • 設定  • TTS 控制        │   │
│  └─────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
                            │ HTTP/WebSocket
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   WSL2 服務 (Kotlin/JVM)                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │  Ktor 伺服器 │  │ 書籍解析器   │  │  TTS 代理服務   │ │
│  │  (HTTP API) │  │ EPUB/TXT/   │  │  (HTTP 串流)    │ │
│  │             │  │ MOBI        │  │                 │ │
│  └─────────────┘  └─────────────┘  └─────────────────┘ │
│  ┌─────────────┐  ┌─────────────────────────────────┐  │
│  │  SQLite DB  │  │       本地檔案系統監控           │  │
│  │  (H2/Exposed)│  │       /mnt/d/Books/            │  │
│  └─────────────┘  └─────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 技術棧

| 層級 | 技術選擇 | 理由 |
|------|---------|------|
| **HTTP 伺服器** | Ktor 3.0 | Kotlin 原生，輕量，協程支援 |
| **資料庫** | SQLite + Exposed | 相容現有 Room schema |
| **書籍解析** | 現有 modules/book | 直接重用，無修改 |
| **前端** | 現有 Vue 應用 | 直接重用，無修改 |
| **TTS** | HTTP 代理 + Web Audio | 代理現有 HTTP TTS 端點 |
| **檔案監控** | java.nio.file.WatchService | 監控書籍目錄變化 |

---

## 3. 需要的工作

### 階段 1: 核心服務 (2-3 週)

| 工作項目 | 時間 | 說明 |
|---------|------|------|
| 建立 Ktor 專案 | 2 天 | Gradle Kotlin DSL，依賴配置 |
| 移植 HTTP API | 3 天 | 從 NanoHTTPD 轉 Ktor routes |
| 移植書籍解析 | 3 天 | 解耦 Android 依賴 (File vs Uri) |
| SQLite 資料層 | 2 天 | Exposed ORM，Room schema 相容 |
| 目錄掃描 | 1 天 | 遞迴掃描 + 自動匯入 |
| 整合 Vue 前端 | 1 天 | 靜態資源服務 |

### 階段 2: TTS 功能 (1-2 週)

| 工作項目 | 時間 | 說明 |
|---------|------|------|
| HTTP TTS 代理 | 2 天 | 代理百度/阿里等 TTS API |
| 前端 TTS 控制 | 2 天 | Web Audio API 播放 |
| 字幕同步 | 2 天 | 段落高亮 + 進度追蹤 |

### 階段 3: 優化 (1-2 週)

| 工作項目 | 時間 | 說明 |
|---------|------|------|
| 檔案監控 | 1 天 | WatchService 自動更新 |
| 快取優化 | 1 天 | 章節內容快取 |
| 效能調優 | 2 天 | 大型書籍載入優化 |

---

## 4. 需要解耦的 Android 依賴

### 書籍解析層

| 檔案 | Android 依賴 | 替代方案 |
|------|-------------|---------|
| `LocalBook.kt` | `android.net.Uri` | `java.io.File` |
| | `DocumentFile` | `java.nio.file.Path` |
| `EpubFile.kt` | `ParcelFileDescriptor` | `RandomAccessFile` |
| | `BitmapFactory` | ImageIO 或移除 |
| `TextFile.kt` | `appDb`, `appCtx` | 依賴注入 |

### 可直接重用的模組

```
modules/book/           # EPUB/MOBI/UMD 解析 (100% 可用)
modules/rhino/          # JavaScript 引擎 (100% 可用)
model/analyzeRule/      # 規則引擎 (95% 可用)
utils/                  # 工具類 (90% 可用)
  - EncodingDetect.kt
  - Utf8BomUtils.kt
  - StringUtils.kt
```

---

## 5. 專案結構

```
legado-server/
├── src/main/kotlin/
│   ├── Application.kt           # Ktor 入口
│   ├── routes/
│   │   ├── BookRoutes.kt        # /getBookshelf, /getChapterList...
│   │   ├── TtsRoutes.kt         # /tts/audio, /tts/config
│   │   └── StaticRoutes.kt      # Vue 靜態資源
│   ├── service/
│   │   ├── BookService.kt       # 書籍管理邏輯
│   │   ├── LocalBookService.kt  # 本地書籍掃描/解析
│   │   └── TtsService.kt        # TTS 代理
│   ├── parser/                  # 從 Legado 移植
│   │   ├── TextFile.kt
│   │   ├── EpubFile.kt
│   │   └── MobiFile.kt
│   └── data/
│       ├── Database.kt          # Exposed 配置
│       └── entities/            # Book, Chapter, etc.
├── src/main/resources/
│   └── web/                     # Vue 前端 (複製自 Legado)
└── build.gradle.kts
```

---

## 6. TTS 實作方案

### 瀏覽器端 TTS 架構

```
┌─────────────────────────────────────────┐
│            瀏覽器 (前端)                 │
│  ┌─────────────────────────────────┐   │
│  │  Web Speech API (備選)           │   │
│  │  或                              │   │
│  │  <audio> + 串流播放              │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
              │ GET /tts/audio?text=xxx
              ▼
┌─────────────────────────────────────────┐
│         WSL2 服務 (TTS 代理)            │
│                                         │
│  1. 接收文字請求                         │
│  2. 呼叫 HTTP TTS API (百度/阿里)        │
│  3. 串流回傳音訊資料                     │
└─────────────────────────────────────────┘
```

### 兩種方案

| 方案 | 優點 | 缺點 |
|------|------|------|
| **Web Speech API** | 無需後端，瀏覽器原生 | 語音品質一般，需要網路 |
| **HTTP TTS 代理** | 高品質語音，可離線快取 | 需要設定 TTS 服務 |

**建議**: 先實作 Web Speech API (零依賴)，後續加入 HTTP TTS 支援

---

## 7. 執行方式

### WSL2 服務啟動

```bash
# 編譯
./gradlew shadowJar

# 執行
java -jar legado-server.jar \
  --port=8080 \
  --books=/mnt/d/Books \
  --db=./data/legado.db

# 瀏覽器訪問
http://localhost:8080
```

### Docker 部署 (可選)

```dockerfile
FROM eclipse-temurin:21-jre
COPY legado-server.jar /app/
VOLUME /books
EXPOSE 8080
CMD ["java", "-jar", "/app/legado-server.jar", "--books=/books"]
```

---

## 8. 對比 Windows 原生應用

| 項目 | WSL2 服務方案 | WinUI 3 原生應用 |
|------|-------------|-----------------|
| **工作量** | 4-8 週 | 8-12 個月 |
| **程式碼重用** | 70-80% | 0% (需完全重寫) |
| **技術棧** | Kotlin (同語言) | C# (不同語言) |
| **前端** | 現有 Vue | 需重新開發 XAML |
| **部署** | 單一 JAR 檔 | MSIX 安裝包 |
| **跨平台** | WSL2/Linux/macOS | 僅 Windows |

---

## 9. 結論

### 可行性: ✅ 高度可行

**核心優勢**:
- **70-80% 程式碼可重用** - 書籍解析、規則引擎、前端 UI
- **工作量小** - MVP 僅需 2-3 週
- **技術棧一致** - 繼續使用 Kotlin，學習成本低
- **現成前端** - Vue 閱讀器已經完整

**建議開發順序**:

```
Week 1-2: MVP
├── Ktor HTTP 伺服器
├── 本地書籍掃描 (TXT/EPUB)
├── 基本 API (/getBookshelf, /getChapterList, /getBookContent)
└── 整合 Vue 前端

Week 3-4: 完善
├── MOBI 支援
├── 閱讀進度同步
├── 目錄監控 (自動更新)
└── Web Speech API TTS

Week 5-6: 進階 (可選)
├── HTTP TTS 代理
├── 多使用者支援
└── WebDAV 同步
```

### 快速驗證

如果只想快速測試，可以:
1. 直接在 WSL2 中安裝 Android 模擬器 (Waydroid)
2. 或使用現有 Legado 的 Web 服務功能 (需要 Android 設備)
