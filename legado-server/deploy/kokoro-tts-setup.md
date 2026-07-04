# Kokoro-FastAPI TTS 服務建置指南

使用 Docker 快速部署 Kokoro-82M TTS 服務，提供 OpenAI 相容 API，支援真正的串流語音合成。

## 概述

Kokoro-82M 是 hexgrad 開發的輕量級開源 TTS 模型，透過 [Kokoro-FastAPI](https://github.com/remsky/Kokoro-FastAPI) 封裝為 OpenAI 相容 API。

| 特性 | 說明 |
|------|------|
| 模型大小 | **82M 參數**（極輕量） |
| 授權 | Apache 2.0 |
| 串流支援 | 真正的串流輸出，首段音頻 ~300ms (GPU) |
| 即時速率 | 35-100x 即時速率 (GPU) |
| 支援語言 | 英/中/日/法/西/印/義/葡 (8 種) |
| 輸出格式 | MP3, WAV, OPUS, FLAC, M4A, PCM |
| API 相容 | OpenAI `/v1/audio/speech` 完全相容 |

### Kokoro vs Qwen3-TTS 比較

| 項目 | Kokoro-82M | Qwen3-TTS 0.6B |
|------|------------|-----------------|
| 模型參數 | 82M | 600M |
| 部署方式 | Docker 一鍵部署 | Python 環境 + 依賴 |
| 串流 | 原生支援，首段 ~300ms | 需等整段生成 |
| GPU 速度 | 35-100x 即時速率 | RTF ~0.85-1.15 |
| 中文音色 | 8 種 (4F + 4M) | 9 種 (5F + 4M) |
| 音質 | 良好，82M 輕量模型 | 優秀，600M 大模型 |
| instruct 控制 | 不支援 | 支援自然語言風格控制 |
| VRAM 需求 | ~2 GB | 4-6 GB |
| CPU 推理 | 支援 (ONNX) | 不實用 |

**建議**：追求低延遲和串流體驗選 Kokoro；追求中文音質和風格控制選 Qwen3-TTS。

---

## 硬體需求

| 項目 | GPU 版本 | CPU 版本 |
|------|---------|---------|
| GPU | NVIDIA GPU + CUDA 12.x | 不需要 |
| VRAM | 2+ GB | — |
| 系統記憶體 | 8 GB | 16 GB |
| 磁碟空間 | ~5 GB (Docker image + 模型) | ~3 GB |
| Docker | Docker Engine 20.10+ | Docker Engine 20.10+ |
| NVIDIA Container Toolkit | 需要 | 不需要 |

---

## 部署方式一：Docker Run（最簡單）

### GPU 版本

```bash
# 前提：已安裝 NVIDIA 驅動和 NVIDIA Container Toolkit
docker run -d \
  --name kokoro-tts \
  --gpus all \
  -p 8880:8880 \
  --restart unless-stopped \
  ghcr.io/remsky/kokoro-fastapi-gpu:v0.2.4-master

# 模型會在首次啟動時自動下載（內建於映像中）
```

### CPU 版本

```bash
docker run -d \
  --name kokoro-tts \
  -p 8880:8880 \
  --restart unless-stopped \
  ghcr.io/remsky/kokoro-fastapi-cpu:v0.2.4-master
```

### 驗證服務

```bash
# 等待容器啟動完成（首次可能需要 1-2 分鐘）
docker logs -f kokoro-tts

# 健康檢查
curl http://localhost:8880/health

# 列出可用音色
curl http://localhost:8880/v1/audio/voices

# 測試語音合成
curl -X POST http://localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "kokoro",
    "voice": "zf_xiaoxiao",
    "input": "你好，這是語音合成測試。",
    "response_format": "mp3",
    "speed": 1.0
  }' \
  --output /tmp/test.mp3 \
  -w "\nHTTP %{http_code}, size: %{size_download} bytes\n"

# 播放測試（如有音頻設備）
# aplay /tmp/test.mp3  或  mpv /tmp/test.mp3
```

---

## 部署方式二：Docker Compose（建議）

### 安裝 NVIDIA Container Toolkit（GPU 版本需要）

```bash
# 添加 NVIDIA 套件來源
curl -fsSL https://nvidia.github.io/libnvidia-container/gpgkey \
  | sudo gpg --dearmor -o /usr/share/keyrings/nvidia-container-toolkit-keyring.gpg

curl -s -L https://nvidia.github.io/libnvidia-container/stable/deb/nvidia-container-toolkit.list \
  | sed 's#deb https://#deb [signed-by=/usr/share/keyrings/nvidia-container-toolkit-keyring.gpg] https://#g' \
  | sudo tee /etc/apt/sources.list.d/nvidia-container-toolkit.list

sudo apt update
sudo apt install -y nvidia-container-toolkit
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker

# 驗證
docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu22.04 nvidia-smi
```

### 建立部署目錄

```bash
mkdir -p ~/kokoro-tts
cd ~/kokoro-tts
```

### GPU 版 docker-compose.yml

```yaml
name: kokoro-tts-gpu
services:
  kokoro-tts:
    image: ghcr.io/remsky/kokoro-fastapi-gpu:v0.2.4-master
    container_name: kokoro-tts
    ports:
      - "8880:8880"
    environment:
      - PYTHONPATH=/app:/app/api
      - USE_GPU=true
      - PYTHONUNBUFFERED=1
      - API_LOG_LEVEL=INFO
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: all
              capabilities: [gpu]
    restart: unless-stopped
```

### CPU 版 docker-compose.yml

```yaml
name: kokoro-tts-cpu
services:
  kokoro-tts:
    image: ghcr.io/remsky/kokoro-fastapi-cpu:v0.2.4-master
    container_name: kokoro-tts
    ports:
      - "8880:8880"
    environment:
      - PYTHONPATH=/app:/app/api
      - PYTHONUNBUFFERED=1
      - API_LOG_LEVEL=INFO
    restart: unless-stopped
```

### 啟動服務

```bash
cd ~/kokoro-tts
docker compose up -d

# 查看日誌
docker compose logs -f
```

---

## 部署方式三：從原始碼建構

適用於需要自訂設定或開發除錯。

```bash
# 克隆倉庫
git clone https://github.com/remsky/Kokoro-FastAPI.git
cd Kokoro-FastAPI

# GPU 版本
cd docker/gpu
docker compose up --build

# 或 CPU 版本
cd docker/cpu
docker compose up --build
```

如需手動下載模型：

```bash
python docker/scripts/download_model.py --type pth   # GPU 版 (PyTorch)
python docker/scripts/download_model.py --type onnx  # CPU 版 (ONNX)
```

---

## 可用音色

### 中文 (Mandarin Chinese) — 語言碼：`z`

| 音色名稱 | 性別 | 說明 |
|----------|------|------|
| `zf_xiaobei` | 女 | 小北 |
| `zf_xiaoni` | 女 | 小妮 |
| `zf_xiaoxiao` | 女 | 小小 |
| `zf_xiaoyi` | 女 | 小伊 |
| `zm_yunjian` | 男 | 雲健 |
| `zm_yunxi` | 男 | 雲希 |
| `zm_yunxia` | 男 | 雲夏 |
| `zm_yunyang` | 男 | 雲陽 |

### 英文 (American English) — 語言碼：`a`

| 音色名稱 | 性別 |
|----------|------|
| `af_heart` | 女 |
| `af_alloy` | 女 |
| `af_bella` | 女 |
| `af_jessica` | 女 |
| `af_nova` | 女 |
| `af_river` | 女 |
| `af_sarah` | 女 |
| `af_sky` | 女 |
| `am_adam` | 男 |
| `am_echo` | 男 |
| `am_michael` | 男 |

### 日文 (Japanese) — 語言碼：`j`

| 音色名稱 | 性別 |
|----------|------|
| `jf_alpha` | 女 |
| `jf_gongitsune` | 女 |
| `jf_nezumi` | 女 |
| `jm_kumo` | 男 |

> 完整音色清單見 [VOICES.md](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)

### 混合音色

Kokoro 支援音色混合：

```
af_bella+af_sky        # 等權重混合
af_bella(2)+af_sky(1)  # 2:1 加權混合 (67%/33%)
```

---

## API 端點

| 方法 | 端點 | 說明 |
|------|------|------|
| POST | `/v1/audio/speech` | 語音合成（OpenAI 相容） |
| GET | `/v1/audio/voices` | 列出可用音色 |
| POST | `/v1/audio/voices/combine` | 混合音色 |
| GET | `/health` | 健康檢查 |
| GET | `/docs` | Swagger API 文件 |
| GET | `/web` | 內建 Web 介面 |

### 語音合成請求

```bash
curl -X POST http://localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "kokoro",
    "voice": "zf_xiaoxiao",
    "input": "開源閱讀，讓閱讀更自由。",
    "response_format": "mp3",
    "speed": 1.0
  }' \
  --output speech.mp3
```

### 串流語音合成

```bash
# 串流模式 - 邊生成邊回傳
curl -X POST http://localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "kokoro",
    "voice": "zf_xiaoxiao",
    "input": "這是一段串流語音合成的測試，音頻會邊生成邊回傳。",
    "response_format": "mp3",
    "speed": 1.0,
    "stream": true
  }' \
  --output stream_test.mp3
```

### Python 用戶端

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8880/v1",
    api_key="not-needed"
)

# 非串流
response = client.audio.speech.create(
    model="kokoro",
    voice="zf_xiaoxiao",
    input="你好，歡迎使用語音朗讀功能。",
    response_format="mp3",
    speed=1.0
)
response.stream_to_file("output.mp3")

# 串流
with client.audio.speech.with_streaming_response.create(
    model="kokoro",
    voice="zf_xiaoxiao",
    input="這是串流語音合成測試。",
    response_format="mp3"
) as response:
    response.stream_to_file("stream_output.mp3")
```

---

## 與 legado-server 整合

Kokoro-FastAPI 使用與 Qwen3-TTS 相同的 OpenAI 相容 API，legado-server **無需修改程式碼**。

### 架構

```
瀏覽器 ──POST /tts/speech──→ legado-server (:8080) ──proxy──→ Kokoro-FastAPI (:8880)
         ←── audio/mpeg ─────    (Ktor HttpClient)    ←─stream──   (GPU/CPU 推理)
```

### 配置

在 legado-server 啟動時指定 `TTS_URL`：

```bash
# 環境變數方式
TTS_URL=http://localhost:8880 java -jar legado-server-all.jar

# 或使用命令列參數
java -jar legado-server-all.jar /path/to/db 8080 /path/to/books http://localhost:8880
```

如果 Kokoro-FastAPI 部署在不同機器上：

```bash
TTS_URL=http://10.243.2.3:8880 java -jar legado-server-all.jar
```

### 更新前端音色列表

由於 Kokoro 的中文音色名稱與 Qwen3-TTS 不同，需更新前端 `TtsPlayer.vue` 的音色選項：

| Qwen3-TTS | Kokoro |
|-----------|--------|
| Vivian, Serena, Dylan... | zf_xiaoxiao, zf_xiaobei, zm_yunxi... |

> 注意：Kokoro 不支援 `instruct` 參數。前端的語氣控制欄位對 Kokoro 無效，但不影響功能。

### Systemd 服務

如需配合 systemd 管理 legado-server：

```ini
# /etc/systemd/system/legado-server.service 中設定
Environment="TTS_URL=http://localhost:8880"
```

---

## 效能基準

在 RTX 4060 Ti 16GB 上的測試數據（來自官方基準）：

| 指標 | 數值 |
|------|------|
| 即時速率 | 35-100x (GPU) |
| 首段音頻延遲 | ~300ms (GPU, chunk size 400) |
| Token 速率 | ~137 tokens/s |
| CPU 首段延遲 | ~3.5s (i7) / <1s (M3 Pro) |

---

## 管理操作

### 查看日誌

```bash
docker logs -f kokoro-tts
# 或
docker compose logs -f
```

### 重啟服務

```bash
docker restart kokoro-tts
# 或
docker compose restart
```

### 更新版本

```bash
# 拉取最新映像
docker pull ghcr.io/remsky/kokoro-fastapi-gpu:v0.2.4-master

# 重新建立容器
docker compose down
docker compose up -d
```

### 監控

```bash
# GPU 使用狀況
nvidia-smi

# 容器資源使用
docker stats kokoro-tts

# 內建除錯端點
curl http://localhost:8880/debug/system
curl http://localhost:8880/debug/storage
```

---

## 故障排除

### 容器無法啟動

```bash
# 查看完整日誌
docker logs kokoro-tts

# 常見原因:
# 1. GPU 版本未安裝 NVIDIA Container Toolkit
# 2. 端口 8880 被占用
# 3. Docker 版本過舊 (需 20.10+)
```

### GPU 無法使用

```bash
# 確認 NVIDIA Container Toolkit
docker run --rm --gpus all nvidia/cuda:12.8.0-base-ubuntu22.04 nvidia-smi

# 如果失敗，重新配置
sudo nvidia-ctk runtime configure --runtime=docker
sudo systemctl restart docker
```

### 語音合成失敗

```bash
# 測試 API 直接存取
curl http://localhost:8880/health

# 檢查可用音色
curl http://localhost:8880/v1/audio/voices

# 嘗試英文音色（排除中文特定問題）
curl -X POST http://localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{"model":"kokoro","voice":"af_heart","input":"Hello world","response_format":"mp3"}' \
  --output /tmp/test_en.mp3 \
  -w "\nHTTP %{http_code}, size: %{size_download}\n"
```

### 中文發音不正常

確保使用 `z` 開頭的中文專用音色：
- 女聲：`zf_xiaobei`, `zf_xiaoni`, `zf_xiaoxiao`, `zf_xiaoyi`
- 男聲：`zm_yunjian`, `zm_yunxi`, `zm_yunxia`, `zm_yunyang`

使用英文音色 (`af_*`, `am_*`) 讀中文會導致發音異常。

---

## 參考資料

- [Kokoro-FastAPI — GitHub](https://github.com/remsky/Kokoro-FastAPI)
- [Kokoro-82M — GitHub](https://github.com/hexgrad/kokoro)
- [Kokoro-82M — HuggingFace](https://huggingface.co/hexgrad/Kokoro-82M)
- [完整音色列表 — VOICES.md](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md)
- [Kokoro-FastAPI Wiki — Docker 部署](https://github.com/remsky/Kokoro-FastAPI/wiki/Setup-Docker)
