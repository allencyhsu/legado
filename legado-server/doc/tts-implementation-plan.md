# legado-server TTS 朗讀功能實作計畫

## Context

legado-server 是基於 Ktor 的本地書籍閱讀 Web 服務，前端使用 Vue 3 + TypeScript。使用者希望在閱讀頁面加入 TTS 朗讀功能，利用已部署在 `http://10.243.2.3:8880` 的 Qwen3-TTS OpenAI 相容 API 進行語音合成。

需要解決的核心問題：
- 前端無法直接跨域存取 TTS 伺服器 → 後端新增代理路由
- 閱讀頁面需要新增朗讀控制 UI → 前端新增 TTS 播放元件
- 章節文字需要逐段送出 TTS 並串流播放 → 前端實作音頻佇列與預載機制

## 架構設計

```
瀏覽器 (Vue 3)                    legado-server (Ktor :8080)           Qwen3-TTS (:8880)
┌──────────────┐                  ┌──────────────────────┐            ┌──────────────┐
│ TtsPlayer    │──POST /tts/speech──→│ TtsRoutes.kt       │──proxy──→│ /v1/audio/speech │
│ (fetch blob) │←── audio/mpeg ─────│ (Ktor HttpClient)  │←─stream──│              │
│              │                  │                      │            │              │
│ ChapterContent│                  │                      │            │              │
│ (段落高亮)    │                  └──────────────────────┘            └──────────────┘
└──────────────┘
```

**設計決策：**
1. **逐段合成** — 章節內容已按 `\n+` 分段，每段獨立送 TTS（段落通常 < 500 字，延遲低）
2. **Blob 播放** — 用 `fetch` 取得完整音頻 blob → `URL.createObjectURL` → `<audio>` 播放（簡單可靠）
3. **預載機制** — 播放當前段時預先下載下一段音頻，無感銜接
4. **後端代理** — 避免 CORS，且 TTS 伺服器地址可透過環境變數配置

---

## 實作步驟

### 第一步：後端 — 新增 TTS 代理路由

**檔案變更：**

1. **`legado-server/build.gradle.kts`** — 新增 Ktor HttpClient 依賴
   ```kotlin
   implementation("io.ktor:ktor-client-cio:$ktorVersion")
   implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
   ```

2. **`legado-server/src/main/kotlin/io/legado/server/routes/TtsRoutes.kt`** — 新建
   - `POST /tts/speech` — 接收 JSON `{ text, voice?, speed?, instruct? }`
   - 用 Ktor HttpClient 轉發至 TTS 伺服器的 `/v1/audio/speech`
   - 組裝 OpenAI 格式請求體（補上 model, response_format 等固定欄位）
   - 串流轉發回應（`Content-Type: audio/mpeg`）
   - `GET /tts/voices` — 代理 `/v1/voices` 端點

3. **`legado-server/src/main/kotlin/io/legado/server/Application.kt`** — 註冊路由
   - 新增環境變數 `TTS_URL`（預設 `http://10.243.2.3:8880`）
   - 在 routing 區塊加入 `ttsRoutes(ttsUrl)`

### 第二步：前端 — TTS API 層

**檔案變更：**

4. **`modules/web/src/api/api.ts`** — 新增 TTS API 函式
   ```typescript
   const ttsSpeak = (text: string, voice: string, speed: number, instruct?: string) =>
     ajax.post('/tts/speech', { text, voice, speed, instruct }, { responseType: 'blob' })

   const ttsVoices = () => ajax.get('/tts/voices')
   ```

### 第三步：前端 — TTS 狀態管理

**檔案變更：**

5. **`modules/web/src/store/ttsStore.ts`** — 新建 Pinia store
   - 狀態：`status`（idle/loading/playing/paused）、`currentParagraph`（當前段落索引）
   - 設定：`voice`（預設 Vivian）、`speed`（預設 1.0）、`instruct`（預設空）
   - Actions：
     - `play(paragraphs: string[], startIndex?: number)` — 開始朗讀
     - `pause()` / `resume()` / `stop()` — 控制播放
     - 內部管理 Audio 物件、blob URL 生命週期、預載邏輯
   - TTS 設定存 localStorage（不同步後端，因為是客戶端特定設定）

### 第四步：前端 — TTS 播放器元件

**檔案變更：**

6. **`modules/web/src/components/TtsPlayer.vue`** — 新建
   - 浮動控制列：播放/暫停按鈕、停止按鈕、進度指示（x/n 段）
   - 音色選擇下拉（Vivian, Serena, Dylan, Eric, Ryan, Aiden, Uncle_Fu, Ono_Anna, Sohee）
   - 語速調整（0.5 ~ 2.0，步進 0.25）
   - 風格指令輸入（instruct，可選）

### 第五步：前端 — 整合到閱讀頁面

**檔案變更：**

7. **`modules/web/src/views/BookChapter.vue`** — 整合 TTS
   - 左側工具列新增「朗讀」按鈕
   - 引入 TtsPlayer 元件，傳入當前章節的段落文字
   - 監聽 ttsStore.currentParagraph 變化，自動捲動到對應段落
   - 章節結尾自動跳轉下一章繼續朗讀

8. **`modules/web/src/components/ChapterContent.vue`** — 段落高亮
   - 新增 prop `highlightParagraph: number`（-1 表示不高亮）
   - 當段落 index 匹配時加上 `.tts-active` CSS class
   - 新增 `.tts-active` 樣式（淡色背景底色標記當前朗讀段落）

### 第六步：建構與部署

9. 前端 build → 複製到 legado-server 靜態資源目錄
10. 後端 shadowJar 重新打包

---

## 關鍵檔案清單

| 操作 | 檔案路徑 |
|------|----------|
| 修改 | `legado-server/build.gradle.kts` |
| 新建 | `legado-server/src/main/kotlin/io/legado/server/routes/TtsRoutes.kt` |
| 修改 | `legado-server/src/main/kotlin/io/legado/server/Application.kt` |
| 修改 | `modules/web/src/api/api.ts` |
| 新建 | `modules/web/src/store/ttsStore.ts` |
| 新建 | `modules/web/src/components/TtsPlayer.vue` |
| 修改 | `modules/web/src/views/BookChapter.vue` |
| 修改 | `modules/web/src/components/ChapterContent.vue` |
| 修改 | `modules/web/src/web.d.ts`（如需擴展 config 型別）|

---

## 驗證方式

1. **後端代理測試：**
   ```bash
   curl -X POST http://localhost:8080/tts/speech \
     -H "Content-Type: application/json" \
     -d '{"text":"你好，測試語音合成","voice":"Vivian","speed":1.0}' \
     --output test.mp3
   # 確認 test.mp3 可正常播放
   ```

2. **前端整合測試：**
   - 開啟任意書籍章節
   - 點擊「朗讀」按鈕
   - 確認逐段朗讀、段落高亮、自動捲動
   - 測試暫停/繼續/停止
   - 切換音色和語速
   - 章節結束後自動跳轉下一章繼續朗讀
