#!/usr/bin/env bash
# ==============================================================================
# MusicApp 启动到歌曲列表刷新耗时快速测量脚本 (Bash 版)
# 用法:
#   ./scripts/measure_startup_time.sh [轮数(默认5)] [冷启动/温启动: cold|warm(默认cold)]
# ==============================================================================

set -euo pipefail

PACKAGE="com.musicapp.player"
ACTIVITY="com.musicapp.player.MainActivity"
RUNS="${1:-5}"
MODE="${2:-cold}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${SCRIPT_DIR}/../benchmark_results"
mkdir -p "$OUTPUT_DIR"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
RESULT_FILE="${OUTPUT_DIR}/startup_${TIMESTAMP}_${MODE}_${RUNS}runs.log"

# 寻找 ADB 路径
ADB_BIN="${ADB_PATH:-}"
if [[ -z "$ADB_BIN" ]]; then
  if command -v adb >/dev/null 2>&1; then
    ADB_BIN="$(command -v adb)"
  elif [[ -f "${ANDROID_HOME:-}/platform-tools/adb" ]]; then
    ADB_BIN="${ANDROID_HOME}/platform-tools/adb"
  elif [[ -f "${ANDROID_SDK_ROOT:-}/platform-tools/adb" ]]; then
    ADB_BIN="${ANDROID_SDK_ROOT}/platform-tools/adb"
  elif [[ -f "$HOME/Library/Android/sdk/platform-tools/adb" ]]; then
    ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
  else
    echo "❌ 错误: 未找到 adb 命令，请配置 ANDROID_HOME 或 PATH。" >&2
    exit 1
  fi
fi

# 检查设备连接
DEVICE="$("$ADB_BIN" devices | grep -E '\bdevice\b' | head -n 1 | awk '{print $1}')"
if [[ -z "$DEVICE" ]]; then
  echo "❌ 错误: 未检测到已连接的 Android 设备或模拟器。" >&2
  exit 1
fi

echo "======================================================================" | tee "$RESULT_FILE"
echo "🎵 MusicApp 启动到歌曲列表刷新时间测试" | tee -a "$RESULT_FILE"
echo "======================================================================" | tee -a "$RESULT_FILE"
echo "📱 目标设备 : $DEVICE" | tee -a "$RESULT_FILE"
echo "📦 应用包名 : $PACKAGE" | tee -a "$RESULT_FILE"
echo "🔄 启动模式 : $MODE" | tee -a "$RESULT_FILE"
echo "🔢 测试轮数 : $RUNS 轮" | tee -a "$RESULT_FILE"
echo "======================================================================" | tee -a "$RESULT_FILE"

# 授予权限
"$ADB_BIN" -s "$DEVICE" shell pm grant "$PACKAGE" android.permission.READ_MEDIA_AUDIO >/dev/null 2>&1 || true
"$ADB_BIN" -s "$DEVICE" shell pm grant "$PACKAGE" android.permission.READ_EXTERNAL_STORAGE >/dev/null 2>&1 || true

for ((i=1; i<=RUNS; i++)); do
  if [[ "$MODE" == "cold" ]]; then
    "$ADB_BIN" -s "$DEVICE" shell am force-stop "$PACKAGE"
    sleep 0.5
  else
    "$ADB_BIN" -s "$DEVICE" shell input keyevent KEYCODE_HOME
    sleep 0.5
  fi

  # 清理 logcat
  "$ADB_BIN" -s "$DEVICE" logcat -c

  # 启动前记录时间 (以毫秒为单位)
  if date +%s%3N >/dev/null 2>&1; then
    T_START=$(date +%s%3N)
  else
    T_START=$(python3 -c "import time; print(int(time.time() * 1000))")
  fi

  # 启动应用
  START_ARGS=("-W")
  if [[ "$MODE" == "cold" ]]; then
    START_ARGS+=("-S")
  fi

  "$ADB_BIN" -s "$DEVICE" shell am start "${START_ARGS[@]}" -n "$PACKAGE/$ACTIVITY" > /dev/null 2>&1

  # 读取 logcat 中 BenchmarkTrace 结果
  LOG_OUTPUT=""
  WAIT_SECONDS=0
  while [[ $WAIT_SECONDS -lt 15 ]]; do
    LOG_OUTPUT="$("$ADB_BIN" -s "$DEVICE" logcat -d -s BenchmarkTrace:I | grep -m 1 "TracksFirstTrackLaidOut" || true)"
    if [[ -n "$LOG_OUTPUT" ]]; then
      break
    fi
    sleep 0.1
    WAIT_SECONDS=$((WAIT_SECONDS + 1))
  done

  if date +%s%3N >/dev/null 2>&1; then
    T_END=$(date +%s%3N)
  else
    T_END=$(python3 -c "import time; print(int(time.time() * 1000))")
  fi

  E2E_MS=$((T_END - T_START))

  if [[ -n "$LOG_OUTPUT" ]]; then
    DURATION_MS="$(echo "$LOG_OUTPUT" | sed -E 's/.*duration_ms=([0-9.]+).*/\1/')"
    TRACK_COUNT="$(echo "$LOG_OUTPUT" | sed -E 's/.*track_count=([0-9]+).*/\1/')"
    echo "✅ 轮次 $i/$RUNS: 端到端耗时 ~${E2E_MS} ms | UI排版耗时 ${DURATION_MS} ms | 曲目数 ${TRACK_COUNT}" | tee -a "$RESULT_FILE"
  else
    echo "⚠️ 轮次 $i/$RUNS: 超时未收到歌曲列表渲染事件 (端到端 ~${E2E_MS} ms)" | tee -a "$RESULT_FILE"
  fi
  sleep 1.5
done

echo "======================================================================" | tee -a "$RESULT_FILE"
echo "✨ 测试完成！结果已保存至: $RESULT_FILE" | tee -a "$RESULT_FILE"
echo "   更详细的多维度分析建议运行: python3 scripts/measure_startup_time.py -n $RUNS"
echo "======================================================================"
