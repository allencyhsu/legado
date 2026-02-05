● 以下是在 WSL2 下建置 Legado 編譯環境的步驟：

1. 安裝 Java 17

sudo apt update
sudo apt install openjdk-17-jdk -y

# 驗證安裝
java -version

2. 安裝 Android SDK

# 建立 Android SDK 目錄
mkdir -p ~/android-sdk/cmdline-tools

# 下載 command-line tools
cd ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip commandlinetools-linux-11076708_latest.zip
mv cmdline-tools latest
rm commandlinetools-linux-11076708_latest.zip

3. 設定環境變數

# 加入 ~/.bashrc 或 ~/.zshrc
cat >> ~/.bashrc << 'EOF'
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
EOF

source ~/.bashrc

4. 安裝必要的 SDK 組件

# 接受授權
yes | sdkmanager --licenses

# 安裝編譯所需組件
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"

5. 設定專案 local.properties

cd /home/allen/projects/legado
echo "sdk.dir=$HOME/android-sdk" > local.properties

6. 編譯專案

# 給予 gradlew 執行權限
chmod +x ./gradlew

# 編譯 debug 版本
./gradlew assembleDebug

編譯完成後，APK 會在 app/build/outputs/apk/app/debug/ 目錄下。

常見問題

如果遇到記憶體不足，可調整 gradle.properties 中的 JVM 參數，或在指令加上：
./gradlew assembleDebug -Dorg.gradle.jvmargs="-Xmx4g"