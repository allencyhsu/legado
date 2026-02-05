# Legado Server 部署指南

本指南說明如何將 legado-server 部署到 Ubuntu 22.04 x64 伺服器。

## 環境需求

| 項目 | 需求 |
|------|------|
| 作業系統 | Ubuntu 22.04 x64 (或其他 Linux 發行版) |
| Java | OpenJDK 17 或更高版本 |
| 記憶體 | 建議 512MB 以上 |
| 磁碟空間 | 100MB (程式) + 書籍空間 |
| 網路 | 開放 8080 端口 (可自訂) |

## 快速部署

### 方式一：使用安裝腳本

```bash
# 1. 構建 JAR 檔案 (在開發機器上)
./gradlew shadowJar

# 2. 將檔案複製到伺服器
scp build/libs/legado-server-all.jar user@server:/tmp/
scp deploy/install.sh user@server:/tmp/
scp deploy/legado-server.service user@server:/tmp/

# 3. 在伺服器上執行安裝腳本
ssh user@server
sudo bash /tmp/install.sh
```

### 方式二：手動部署

請參照下方「手動部署步驟」章節。

---

## 手動部署步驟

### 1. 安裝 Java 17

```bash
# 更新套件索引
sudo apt update

# 安裝 OpenJDK 17 (僅運行時)
sudo apt install -y openjdk-17-jre-headless

# 驗證安裝
java -version
```

### 2. 創建系統用戶和目錄

```bash
# 創建專用系統用戶
sudo useradd -r -s /bin/false legado

# 創建應用目錄
sudo mkdir -p /opt/legado-server/data

# 創建書籍目錄 (可自訂位置)
sudo mkdir -p /data/books

# 設置目錄權限
sudo chown -R legado:legado /opt/legado-server
sudo chown -R legado:legado /data/books
```

### 3. 部署 JAR 檔案

```bash
# 複製 JAR 檔案到應用目錄
sudo cp legado-server-all.jar /opt/legado-server/

# 設置權限
sudo chown legado:legado /opt/legado-server/legado-server-all.jar
```

### 4. 安裝 Systemd 服務

```bash
# 複製服務檔案
sudo cp legado-server.service /etc/systemd/system/

# 重新載入 systemd
sudo systemctl daemon-reload

# 啟用開機自動啟動
sudo systemctl enable legado-server

# 啟動服務
sudo systemctl start legado-server

# 查看狀態
sudo systemctl status legado-server
```

### 5. 配置防火牆 (如需遠端訪問)

```bash
# 使用 ufw
sudo ufw allow 8080/tcp

# 或使用 iptables
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
```

### 6. 上傳書籍

```bash
# 將書籍檔案上傳到書籍目錄
scp /path/to/books/*.epub user@server:/data/books/
scp /path/to/books/*.txt user@server:/data/books/

# 確保權限正確
sudo chown -R legado:legado /data/books
```

### 7. 驗證部署

```bash
# 測試 API
curl http://localhost:8080/getBookshelf

# 在瀏覽器訪問
# http://<server-ip>:8080
```

---

## 配置參數

服務啟動時可以指定以下參數：

```bash
java -jar legado-server-all.jar [port] [booksDir] [dbPath]
```

| 參數 | 預設值 | 說明 |
|------|--------|------|
| port | 8080 | HTTP 服務端口 |
| booksDir | /mnt/d/Books | 書籍存放目錄 |
| dbPath | ./data/legado.db | SQLite 資料庫路徑 |

### 修改服務配置

編輯 `/etc/systemd/system/legado-server.service` 中的 `ExecStart` 行：

```ini
ExecStart=/usr/bin/java -jar /opt/legado-server/legado-server-all.jar 8080 /data/books /opt/legado-server/data/legado.db
```

修改後重新載入並重啟：

```bash
sudo systemctl daemon-reload
sudo systemctl restart legado-server
```

---

## 服務管理命令

```bash
# 啟動服務
sudo systemctl start legado-server

# 停止服務
sudo systemctl stop legado-server

# 重啟服務
sudo systemctl restart legado-server

# 查看服務狀態
sudo systemctl status legado-server

# 查看即時日誌
sudo journalctl -u legado-server -f

# 查看最近 100 行日誌
sudo journalctl -u legado-server -n 100
```

---

## 目錄結構

```
/opt/legado-server/
├── legado-server-all.jar    # 主程式
└── data/
    └── legado.db            # SQLite 資料庫

/data/books/                  # 書籍存放目錄
├── book1.txt
├── book2.epub
└── ...
```

---

## 使用 Nginx 反向代理 (可選)

如果需要使用域名或 HTTPS，請參考 `nginx.conf.example`。

```bash
# 安裝 Nginx
sudo apt install -y nginx

# 複製配置
sudo cp nginx.conf.example /etc/nginx/sites-available/legado-server

# 啟用配置
sudo ln -s /etc/nginx/sites-available/legado-server /etc/nginx/sites-enabled/

# 測試配置
sudo nginx -t

# 重新載入 Nginx
sudo systemctl reload nginx
```

---

## 故障排除

### 服務無法啟動

```bash
# 查看詳細錯誤
sudo journalctl -u legado-server -e

# 手動運行測試
sudo -u legado java -jar /opt/legado-server/legado-server-all.jar
```

### 無法訪問 Web 界面

1. 確認服務正在運行：`sudo systemctl status legado-server`
2. 確認端口監聽：`sudo netstat -tlnp | grep 8080`
3. 確認防火牆設置：`sudo ufw status`

### 書籍未顯示

1. 確認書籍檔案在正確目錄：`ls -la /data/books/`
2. 確認檔案權限：`sudo chown -R legado:legado /data/books`
3. 確認檔案格式為 `.txt` 或 `.epub`
4. 重啟服務觸發重新掃描

### 資料庫問題

```bash
# 備份資料庫
sudo cp /opt/legado-server/data/legado.db /opt/legado-server/data/legado.db.bak

# 如需重建，刪除資料庫後重啟服務
sudo rm /opt/legado-server/data/legado.db
sudo systemctl restart legado-server
```

---

## 支援的書籍格式

| 格式 | 副檔名 | 說明 |
|------|--------|------|
| 純文字 | .txt | 支援自動編碼檢測 (UTF-8, GBK 等) |
| EPUB | .epub | 支援目錄和封面提取 |

---

## 備份與還原

### 備份

```bash
# 備份資料庫
sudo cp /opt/legado-server/data/legado.db /backup/legado-$(date +%Y%m%d).db

# 備份書籍目錄
sudo tar -czf /backup/books-$(date +%Y%m%d).tar.gz /data/books/
```

### 還原

```bash
# 停止服務
sudo systemctl stop legado-server

# 還原資料庫
sudo cp /backup/legado-20240101.db /opt/legado-server/data/legado.db
sudo chown legado:legado /opt/legado-server/data/legado.db

# 還原書籍
sudo tar -xzf /backup/books-20240101.tar.gz -C /

# 啟動服務
sudo systemctl start legado-server
```
