# Qwen3-TTS 0.6B 串流 TTS 服務建置指南

使用 RTX 4060 Ti 架設 Qwen3-TTS 串流語音合成服務，提供 OpenAI 相容 API。

## 概述

Qwen3-TTS 是阿里通義團隊 2026 年 1 月發佈的開源 TTS 模型。

| 特性 | 說明 |
|------|------|
| 授權 | Apache 2.0 (商用友好) |
| 串流架構 | Dual-Track 混合串流生成 |
| 首包延遲 | 97ms |
| 支援語言 | 中/英/日/韓/德/法/俄/葡/西/義 (10 種) |
| 中英混讀 | 穩定，超越 SeedTTS / GPT-4o |

### 為何選擇 0.6B

| 模型 | 參數量 | VRAM 需求 | RTX 4060 Ti | 即時性 |
|------|--------|-----------|-------------|--------|
| 0.6B | 6 億 | 4-6 GB | 充裕 | RTF ~0.85-1.15 |
| 1.7B | 17 億 | 6-8 GB | 勉強 / 不建議 | RTF 較差 |

0.6B 在 RTX 4060 Ti (8GB VRAM) 上可流暢運行串流推理。

### 模型變體

| 模型名稱 | 功能 |
|----------|------|
| `Qwen3-TTS-12Hz-0.6B-Base` | 語音克隆 (提供參考音頻即可複製音色) |
| `Qwen3-TTS-12Hz-0.6B-CustomVoice` | 9 種預設音色 + 自然語言風格控制 |

建議兩個都下載，根據使用場景選擇。

---

## 硬體需求

| 項目 | 最低要求 | 建議配置 |
|------|---------|---------|
| GPU | RTX 3060 (8GB) | RTX 4060 Ti (8GB+) |
| VRAM | 6 GB | 8 GB |
| 系統記憶體 | 16 GB | 32 GB |
| 磁碟空間 | 10 GB (模型+環境) | 20 GB |
| 作業系統 | Ubuntu 22.04 x64 | Ubuntu 22.04 x64 |
| NVIDIA 驅動 | 535+ | 550+ |
| CUDA | 12.1+ | 12.8 |

---

## 建置步驟

### 步驟 1: 安裝 NVIDIA 驅動

```bash
# 檢查現有驅動
nvidia-smi

# 如果未安裝或版本過舊，安裝推薦驅動
sudo apt update
sudo apt install -y nvidia-driver-550
sudo reboot

# 重啟後驗證
nvidia-smi
# 應顯示 RTX 4060 Ti 和 CUDA Version: 12.x
```

### 步驟 2: 安裝 uv

```bash
# 安裝 uv (Rust 實作的高速 Python 套件管理器)
curl -LsSf https://astral.sh/uv/install.sh | sh

# 重新載入 PATH
source $HOME/.local/bin/env

# 驗證安裝
uv --version
```

### 步驟 3: 建立 Python 環境

```bash
# 建立專案目錄
mkdir -p ~/Projects/qwen3-tts
cd ~/Projects/qwen3-tts

# 建立 Python 3.12 虛擬環境
uv venv --python 3.12

# 啟用環境
source .venv/bin/activate

# 安裝 PyTorch (CUDA 12.8)
uv pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128

# 驗證 CUDA 可用
python -c "import torch; print(f'PyTorch: {torch.__version__}'); print(f'CUDA: {torch.cuda.is_available()}'); print(f'GPU: {torch.cuda.get_device_name(0)}')"
```

預期輸出：
```
PyTorch: 2.6.x
CUDA: True
GPU: NVIDIA GeForce RTX 4060 Ti
```

### 步驟 4: 安裝 Qwen3-TTS

```bash
cd ~/Projects/qwen3-tts
source .venv/bin/activate

# 安裝系統依賴 (音頻處理)
sudo apt install -y sox libsox-fmt-all libsndfile1-dev

# 安裝 qwen-tts 套件
uv pip install qwen-tts soundfile

# 安裝 FlashAttention 2 (效能提升 30-40%，強烈建議)
# 前置依賴: 編譯工具 + Python 開發標頭檔
# Ubuntu 22.04 預設無 Python 3.12，需加 deadsnakes PPA
sudo add-apt-repository ppa:deadsnakes/ppa -y
sudo apt update
sudo apt install -y build-essential python3.12-dev

# flash-attn 的 setup.py 需要 wheel，但未宣告為 build dependency
uv pip install wheel
uv pip install flash-attn --no-build-isolation

# 如果記憶體不足導致編譯失敗：
# 卸載 ninja 回退到 make (ninja 忽略 MAX_JOBS，會用盡所有核心)
# uv pip uninstall ninja
# MAX_JOBS=4 uv pip install flash-attn --no-build-isolation
```

### 步驟 5: 下載模型

```bash
# 安裝 HuggingFace CLI
uv pip install "huggingface_hub[cli]"

# 建立模型存放目錄
mkdir -p ~/Projects/qwen3-tts/models

# 下載 0.6B-Base (語音克隆)
huggingface-cli download Qwen/Qwen3-TTS-12Hz-0.6B-Base \
  --local-dir ~/Projects/qwen3-tts/models/Qwen3-TTS-12Hz-0.6B-Base

# 下載 0.6B-CustomVoice (預設音色)
huggingface-cli download Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice \
  --local-dir ~/Projects/qwen3-tts/models/Qwen3-TTS-12Hz-0.6B-CustomVoice
```

> 如果下載速度慢，可設定 HuggingFace 鏡像：
> ```bash
> export HF_ENDPOINT=https://hf-mirror.com
> ```

### 步驟 6: 快速測試

建立測試腳本 `~/Projects/qwen3-tts/test_tts.py`：

```python
import os
import torch
import soundfile as sf
from qwen_tts import Qwen3TTSModel

print(f"CUDA available: {torch.cuda.is_available()}")
print(f"GPU: {torch.cuda.get_device_name(0)}")
print(f"VRAM: {torch.cuda.get_device_properties(0).total_memory / 1024**3:.1f} GB")

# 模型路徑 (Python 不會自動展開 ~，需用 expanduser)
model_path = os.path.expanduser(
    "~/Projects/qwen3-tts/models/Qwen3-TTS-12Hz-0.6B-CustomVoice"
)

# 載入 CustomVoice 模型
# 如果未安裝 flash-attn，將 attn_implementation 改為 "sdpa" 或移除該參數
# "without specifying a torch dtype" 警告可忽略，qwen_tts 內部會正確傳遞 dtype
model = Qwen3TTSModel.from_pretrained(
    model_path,
    device_map="cuda:0",
    dtype=torch.bfloat16,
    attn_implementation="flash_attention_2",
)

# 使用預設音色合成中文
wavs, sr = model.generate_custom_voice(
    text="你好，這是通義千問語音合成測試。歡迎使用開源閱讀。",
    language="Chinese",
    speaker="Vivian",
    instruct="用溫柔自然的語調朗讀"
)

# 儲存為 WAV
sf.write("test_output.wav", wavs[0], sr)
print("Test completed! Output: test_output.wav")
```

```bash
cd ~/Projects/qwen3-tts
source .venv/bin/activate
python test_tts.py

# 如果成功會產生 test_output.wav
# 可用 aplay test_output.wav 播放 (如果有音頻設備)
```

---

## 部署 OpenAI 相容 API 伺服器

### 安裝 API 伺服器

```bash
cd ~/Projects/qwen3-tts
source .venv/bin/activate

# 克隆 OpenAI 相容 FastAPI 伺服器
git clone https://github.com/groxaxo/Qwen3-TTS-Openai-Fastapi.git api-server
cd api-server

# 安裝 API 依賴
uv pip install -e ".[api]"
```

### 配置

建立環境配置檔 `~/Projects/qwen3-tts/api-server/.env`：

```bash
# 伺服器設置
HOST=0.0.0.0
PORT=8880
WORKERS=1

# TTS 後端
TTS_BACKEND=official

# 模型路徑 (使用本地路徑加快啟動)
# 如果不設定，會自動從 HuggingFace 下載
HF_HOME=~/Projects/qwen3-tts/models
```

### 啟動伺服器

```bash
cd ~/Projects/qwen3-tts
source .venv/bin/activate
cd api-server
python -m api.main
```

預期輸出：
```
INFO:     Uvicorn running on http://0.0.0.0:8880 (Press CTRL+C to quit)
INFO:     Model loaded successfully
```

### API 端點

| 方法 | 端點 | 說明 |
|------|------|------|
| POST | `/v1/audio/speech` | 生成語音 (OpenAI 相容) |
| GET | `/v1/models` | 列出可用模型 |
| GET | `/v1/voices` | 列出可用音色 |
| GET | `/health` | 健康檢查 |
| GET | `/docs` | Swagger API 文件 |

### 使用範例

**cURL 串流生成：**

```bash
curl -X POST http://localhost:8880/v1/audio/speech \
  -H "Content-Type: application/json" \
  -d '{
    "model": "qwen3-tts",
    "voice": "Vivian",
    "input": "開源閱讀，讓閱讀更自由。今天我們來聽一段精彩的故事。",
    "response_format": "mp3",
    "speed": 1.0
  }' \
  --output speech.mp3
```

**Python 用戶端 (使用 OpenAI SDK)：**

```python
from openai import OpenAI

client = OpenAI(
    base_url="http://localhost:8880/v1",
    api_key="not-needed"
)

# 非串流
response = client.audio.speech.create(
    model="qwen3-tts",
    voice="Vivian",
    input="你好，歡迎使用語音朗讀功能。",
    response_format="mp3",
    speed=1.0
)
response.stream_to_file("output.mp3")

# 串流
with client.audio.speech.with_streaming_response.create(
    model="qwen3-tts",
    voice="Vivian",
    input="這是一段串流語音合成的測試。",
    response_format="mp3"
) as response:
    response.stream_to_file("stream_output.mp3")
```

### CustomVoice 預設音色

| 音色名稱 | 說明 |
|----------|------|
| Vivian | 女聲，多語言，溫柔自然 |
| Serena | 女聲 |
| Ono_Anna | 女聲 (日語) |
| Sohee | 女聲 (韓語) |
| Dylan | 男聲 |
| Eric | 男聲 |
| Ryan | 男聲 |
| Aiden | 男聲 |
| Uncle_Fu | 男聲 (中文) |

可透過 `instruct` 參數控制語氣風格，例如：
- `"用溫柔的語氣朗讀"`
- `"用興奮激動的語調說"`
- `"用平靜沉穩的聲音緩慢朗讀"`

---

## Systemd 服務配置

建立服務檔案 `/etc/systemd/system/qwen3-tts.service`：

```ini
[Unit]
Description=Qwen3-TTS OpenAI-Compatible API Server
After=network.target

[Service]
Type=simple
User=legado
Group=legado
WorkingDirectory=%h/Projects/qwen3-tts/api-server

# 使用 uv 虛擬環境啟動
ExecStart=%h/Projects/qwen3-tts/.venv/bin/python -m api.main

# 環境變數
Environment="HOST=0.0.0.0"
Environment="PORT=8880"
Environment="WORKERS=1"
Environment="TTS_BACKEND=official"
Environment="HF_HOME=%h/Projects/qwen3-tts/models"
Environment="CUDA_VISIBLE_DEVICES=0"

# 重啟策略
Restart=on-failure
RestartSec=15

# GPU 存取需要的權限
SupplementaryGroups=video render

# 日誌
StandardOutput=journal
StandardError=journal
SyslogIdentifier=qwen3-tts

[Install]
WantedBy=multi-user.target
```

> Systemd 直接呼叫 venv 內的 Python，無需 shell wrapper 或 source activate。
> `%h` 是 systemd 的 specifier，會自動展開為 `User=` 指定用戶的家目錄。

```bash
# 安裝服務
sudo cp qwen3-tts.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable qwen3-tts
sudo systemctl start qwen3-tts

# 查看狀態
sudo systemctl status qwen3-tts

# 查看日誌
sudo journalctl -u qwen3-tts -f
```

### 防火牆設置

```bash
# 如需遠端存取
sudo ufw allow 8880/tcp
```

---

## 與 legado-server 整合

legado-server 已內建 TTS 代理功能，前端閱讀頁面可直接朗讀書籍內容。

### 架構

```
瀏覽器 ──POST /tts/speech──→ legado-server (:8080) ──proxy──→ Qwen3-TTS API (:8880)
         ←── audio/mpeg ─────    (Ktor HttpClient)    ←─stream──     (GPU 推理)
```

### 配置

在 legado-server 啟動時設定 `TTS_URL` 環境變數：

```bash
# 環境變數方式
TTS_URL=http://10.243.2.3:8880 java -jar legado-server-all.jar

# 或在 systemd service 中設定
Environment="TTS_URL=http://10.243.2.3:8880"
```

### 使用方式

1. 開啟書籍章節閱讀頁面
2. 點擊左側工具列「朗讀」按鈕
3. 在浮動控制列中選擇音色、調整語速
4. 點擊播放，系統會逐段朗讀並高亮當前段落

### API 端點

legado-server 提供以下 TTS 代理端點：

| 方法 | 端點 | 說明 |
|------|------|------|
| POST | `/tts/speech` | 文字轉語音（接收 `{text, voice, speed, instruct}`，回傳 audio/mpeg） |
| GET | `/tts/voices` | 取得可用音色列表 |
| GET | `/tts/health` | 檢查 TTS 伺服器狀態 |

**測試代理是否正常：**

```bash
curl -X POST http://localhost:8080/tts/speech \
  -H "Content-Type: application/json" \
  -d '{"text":"你好，測試語音合成","voice":"Vivian","speed":1.0}' \
  --output test.mp3
# 確認 test.mp3 可正常播放
```

---

## 故障排除

### CUDA 不可用

```bash
# 檢查驅動
nvidia-smi

# 檢查 PyTorch CUDA
python -c "import torch; print(torch.cuda.is_available())"

# 如果為 False，重新安裝 PyTorch
uv pip install torch torchvision --index-url https://download.pytorch.org/whl/cu128
```

### VRAM 不足 (OOM)

```bash
# 查看 GPU 記憶體使用
nvidia-smi

# 解決方案:
# 1. 確保沒有其他程序佔用 GPU
# 2. 使用 0.6B 而非 1.7B 模型
# 3. 減少 batch size (WORKERS=1)
# 4. 安裝 FlashAttention 2 可減少 20-25% VRAM 使用
```

### FlashAttention 安裝失敗

```bash
# 確保安裝了編譯工具
sudo apt install -y build-essential

# 確保 wheel 已安裝 (flash-attn 的隱式 build dependency)
uv pip install wheel

# 如果記憶體不足 (OOM killed)，需卸載 ninja 回退到 make
# ninja 忽略 MAX_JOBS 和 taskset，會用盡所有核心
uv pip uninstall ninja
MAX_JOBS=2 uv pip install flash-attn --no-build-isolation

# 如果仍然失敗，可以不裝 (效能會降低但功能正常)
```

### 模型下載失敗

```bash
# 使用鏡像站
export HF_ENDPOINT=https://hf-mirror.com
huggingface-cli download Qwen/Qwen3-TTS-12Hz-0.6B-Base \
  --local-dir ~/Projects/qwen3-tts/models/Qwen3-TTS-12Hz-0.6B-Base

# 或手動下載後放到指定目錄
```

### API 伺服器無回應

```bash
# 檢查服務狀態
sudo systemctl status qwen3-tts

# 查看詳細日誌
sudo journalctl -u qwen3-tts -n 50

# 手動啟動排查
cd ~/Projects/qwen3-tts
source .venv/bin/activate
cd api-server
python -m api.main

# 測試連線
curl http://localhost:8880/health
```

---

## 效能調優

### 啟用 torch.compile (建議)

設定環境變數：

```bash
Environment="TORCH_COMPILE=1"
```

可進一步降低推理延遲。

### GPU 電源管理

```bash
# 設定 GPU 為最高效能模式
sudo nvidia-smi -pm 1
sudo nvidia-smi -pl 160  # RTX 4060 Ti 功耗限制 (W)
```

### 監控

```bash
# 即時監控 GPU 使用率
watch -n 1 nvidia-smi

# 查看 TTS 服務日誌
sudo journalctl -u qwen3-tts -f
```

---

## 參考資料

- [QwenLM/Qwen3-TTS - GitHub 官方倉庫](https://github.com/QwenLM/Qwen3-TTS)
- [Qwen3-TTS 技術報告 (arXiv)](https://arxiv.org/abs/2601.15621)
- [Qwen3-TTS-12Hz-0.6B-Base - HuggingFace](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-0.6B-Base)
- [Qwen3-TTS-12Hz-0.6B-CustomVoice - HuggingFace](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice)
- [Qwen3-TTS-Openai-Fastapi - OpenAI 相容 API 伺服器](https://github.com/groxaxo/Qwen3-TTS-Openai-Fastapi)
