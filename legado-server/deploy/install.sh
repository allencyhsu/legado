#!/bin/bash
#
# Legado Server 安裝腳本
# 適用於 Ubuntu 22.04 x64 及其他基於 Debian 的發行版
#
# 使用方式:
#   sudo bash install.sh [JAR檔案路徑] [書籍目錄]
#
# 範例:
#   sudo bash install.sh
#   sudo bash install.sh /tmp/legado-server-all.jar
#   sudo bash install.sh /tmp/legado-server-all.jar /home/user/books
#

set -e

# 顏色定義
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 配置變數
APP_NAME="legado-server"
APP_USER="legado"
APP_DIR="/opt/legado-server"
DATA_DIR="${APP_DIR}/data"
DEFAULT_BOOKS_DIR="/data/books"
SERVICE_FILE="/etc/systemd/system/legado-server.service"

# 輸入參數
JAR_SOURCE="${1:-./legado-server-all.jar}"
BOOKS_DIR="${2:-$DEFAULT_BOOKS_DIR}"

# 輔助函數
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 檢查是否以 root 運行
check_root() {
    if [[ $EUID -ne 0 ]]; then
        log_error "此腳本需要 root 權限運行"
        log_info "請使用: sudo bash $0"
        exit 1
    fi
}

# 檢查 JAR 檔案是否存在
check_jar() {
    if [[ ! -f "$JAR_SOURCE" ]]; then
        log_error "找不到 JAR 檔案: $JAR_SOURCE"
        log_info "請確保 legado-server-all.jar 檔案存在"
        log_info "使用方式: sudo bash install.sh /path/to/legado-server-all.jar"
        exit 1
    fi
    log_info "找到 JAR 檔案: $JAR_SOURCE"
}

# 安裝 Java 17
install_java() {
    log_info "檢查 Java 環境..."

    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
        if [[ "$JAVA_VERSION" -ge 17 ]]; then
            log_info "Java $JAVA_VERSION 已安裝"
            return 0
        else
            log_warn "Java 版本 $JAVA_VERSION 過舊，需要 Java 17+"
        fi
    fi

    log_info "安裝 OpenJDK 17..."
    apt-get update
    apt-get install -y openjdk-17-jre-headless

    # 驗證安裝
    java -version
    log_info "Java 安裝完成"
}

# 創建系統用戶
create_user() {
    log_info "檢查系統用戶..."

    if id "$APP_USER" &>/dev/null; then
        log_info "用戶 $APP_USER 已存在"
    else
        log_info "創建系統用戶: $APP_USER"
        useradd -r -s /bin/false "$APP_USER"
    fi
}

# 創建目錄結構
create_directories() {
    log_info "創建目錄結構..."

    # 應用目錄
    mkdir -p "$APP_DIR"
    mkdir -p "$DATA_DIR"

    # 書籍目錄
    mkdir -p "$BOOKS_DIR"

    # 設置權限
    chown -R "$APP_USER:$APP_USER" "$APP_DIR"
    chown -R "$APP_USER:$APP_USER" "$BOOKS_DIR"

    log_info "目錄創建完成:"
    log_info "  應用目錄: $APP_DIR"
    log_info "  資料目錄: $DATA_DIR"
    log_info "  書籍目錄: $BOOKS_DIR"
}

# 部署 JAR 檔案
deploy_jar() {
    log_info "部署 JAR 檔案..."

    cp "$JAR_SOURCE" "$APP_DIR/legado-server-all.jar"
    chown "$APP_USER:$APP_USER" "$APP_DIR/legado-server-all.jar"
    chmod 644 "$APP_DIR/legado-server-all.jar"

    log_info "JAR 檔案已部署到: $APP_DIR/legado-server-all.jar"
}

# 安裝 Systemd 服務
install_service() {
    log_info "安裝 Systemd 服務..."

    # 檢查是否有本地服務檔案
    SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
    LOCAL_SERVICE="$SCRIPT_DIR/legado-server.service"

    if [[ -f "$LOCAL_SERVICE" ]]; then
        log_info "使用本地服務檔案: $LOCAL_SERVICE"
        cp "$LOCAL_SERVICE" "$SERVICE_FILE"
    else
        log_info "創建服務檔案..."
        cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Legado Server - Local Book Reading Service
Documentation=https://github.com/gedoor/legado
After=network.target

[Service]
Type=simple
User=$APP_USER
Group=$APP_USER
WorkingDirectory=$APP_DIR
ExecStart=/usr/bin/java -jar $APP_DIR/legado-server-all.jar 8080 $BOOKS_DIR $DATA_DIR/legado.db
Restart=on-failure
RestartSec=10
TimeoutStopSec=30
StandardOutput=journal
StandardError=journal
SyslogIdentifier=legado-server
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=$DATA_DIR $BOOKS_DIR

[Install]
WantedBy=multi-user.target
EOF
    fi

    # 如果書籍目錄不是預設值，更新服務檔案
    if [[ "$BOOKS_DIR" != "$DEFAULT_BOOKS_DIR" ]]; then
        log_info "更新服務配置中的書籍目錄..."
        sed -i "s|/data/books|$BOOKS_DIR|g" "$SERVICE_FILE"
    fi

    # 重新載入 systemd
    systemctl daemon-reload

    log_info "服務檔案已安裝: $SERVICE_FILE"
}

# 啟動服務
start_service() {
    log_info "啟動服務..."

    # 啟用開機自動啟動
    systemctl enable "$APP_NAME"

    # 啟動服務
    systemctl start "$APP_NAME"

    # 等待服務啟動
    sleep 3

    # 檢查狀態
    if systemctl is-active --quiet "$APP_NAME"; then
        log_info "服務啟動成功"
    else
        log_error "服務啟動失敗"
        log_info "請使用以下命令查看日誌:"
        log_info "  sudo journalctl -u $APP_NAME -e"
        exit 1
    fi
}

# 顯示安裝結果
show_result() {
    echo ""
    echo "=========================================="
    echo -e "${GREEN}Legado Server 安裝完成!${NC}"
    echo "=========================================="
    echo ""
    echo "服務狀態:"
    systemctl status "$APP_NAME" --no-pager -l || true
    echo ""
    echo "訪問方式:"
    echo "  本地: http://localhost:8080"

    # 嘗試獲取伺服器 IP
    SERVER_IP=$(hostname -I | awk '{print $1}')
    if [[ -n "$SERVER_IP" ]]; then
        echo "  遠端: http://$SERVER_IP:8080"
    fi

    echo ""
    echo "常用命令:"
    echo "  查看狀態:   sudo systemctl status $APP_NAME"
    echo "  查看日誌:   sudo journalctl -u $APP_NAME -f"
    echo "  重啟服務:   sudo systemctl restart $APP_NAME"
    echo "  停止服務:   sudo systemctl stop $APP_NAME"
    echo ""
    echo "書籍目錄: $BOOKS_DIR"
    echo "將 .txt 或 .epub 檔案放入此目錄後重啟服務即可"
    echo ""
}

# 主程序
main() {
    echo ""
    echo "=========================================="
    echo "Legado Server 安裝腳本"
    echo "=========================================="
    echo ""

    check_root
    check_jar
    install_java
    create_user
    create_directories
    deploy_jar
    install_service
    start_service
    show_result
}

# 執行主程序
main "$@"
