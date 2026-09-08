#!/bin/bash
set -e

DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$DIR"

export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "=========================================="
echo "   BiliResponsive 模块部署与调试脚本    "
echo "=========================================="

echo "[1/4] 检测 ADB 设备..."
DEVICES=$(adb devices | grep -v "List of devices" | grep "device$" | awk '{print $1}')
if [ -z "$DEVICES" ]; then
    echo "[x] 错误：未检测到已连接的 ADB 设备！"
    exit 1
fi
echo "[+] 成功连接设备: $DEVICES"

echo "[2/4] 编译 APK (assembleDebug)..."
./gradlew assembleDebug

APK_PATH="$DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -f "$APK_PATH" ]; then
    echo "[x] 错误：未找到生成的 APK: $APK_PATH"
    exit 1
fi

echo "[3/4] 安装 APK 到设备..."
adb install -r "$APK_PATH"
echo "[+] 安装成功！"

echo "[4/4] 模块过滤日志 (按 Ctrl+C 退出)..."
adb logcat -s "BiliResponsive" "LSPosed" "LSPosed-Bridge" "BiliResponsive-Config"
