#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
MusicApp 启动到歌曲列表刷新时间测量脚本

测量维度：
1. 端到端冷启动总耗时 (End-to-End Time to First Track Layout): 从发起 am start 到歌曲列表首项排版完成。
2. 系统首帧渲染时间 (System Displayed Time / TTID): 系统 ActivityTaskManager 记录的 Displayed 时间。
3. Compose 歌曲列表排版耗时 (Tracks First Track Layout Duration): Compose 页面内从加载开始到首个曲目 onGloballyPositioned 的耗时。
4. 歌曲加载增量耗时 (Delta Time): 首帧显示后到歌曲列表实际渲染完成的等待时间。
"""

import argparse
import datetime
import json
import math
import os
import re
import shutil
import subprocess
import sys
import time
from typing import Dict, List, Optional, Tuple


PROJECT_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DEFAULT_OUTPUT_DIR = os.path.join(PROJECT_ROOT, "benchmark_results")
DEFAULT_PACKAGE = "com.musicapp.player"
DEFAULT_ACTIVITY = "com.musicapp.player.MainActivity"
BENCHMARK_TAG = "BenchmarkTrace"
DISPLAYED_TAG = "ActivityTaskManager"


def find_adb(custom_path: Optional[str] = None) -> str:
    """寻找可用的 adb 路径"""
    if custom_path and os.path.isfile(custom_path) and os.access(custom_path, os.X_OK):
        return custom_path

    adb_in_path = shutil.which("adb")
    if adb_in_path:
        return adb_in_path

    # 尝试常见 Android SDK 路径
    candidates = [
        os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
        os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb"),
        os.path.join(os.environ.get("ANDROID_SDK_ROOT", ""), "platform-tools", "adb"),
        "/opt/android-sdk/platform-tools/adb",
    ]
    for c in candidates:
        if c and os.path.isfile(c) and os.access(c, os.X_OK):
            return c

    print("❌ 错误: 未找到 adb 命令。请确保 Android SDK platform-tools 在 PATH 中，或使用 --adb-path 参数指定。", file=sys.stderr)
    sys.exit(1)


def run_cmd(adb_bin: str, args: List[str], serial: Optional[str] = None, timeout: int = 10) -> Tuple[int, str, str]:
    """运行 adb 命令并返回 (returncode, stdout, stderr)"""
    cmd = [adb_bin]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    try:
        proc = subprocess.run(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            timeout=timeout,
            encoding="utf-8",
            errors="replace",
        )
        return proc.returncode, proc.stdout.strip(), proc.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "Command timed out"
    except Exception as e:
        return -1, "", str(e)


def check_device(adb_bin: str, serial: Optional[str] = None) -> str:
    """检查连接的设备并返回可用设备序列号"""
    code, stdout, _ = run_cmd(adb_bin, ["devices"], timeout=5)
    lines = [line.strip() for line in stdout.splitlines() if line.strip() and not line.startswith("List of devices")]
    devices = [line.split()[0] for line in lines if "\tdevice" in line or " device" in line]

    if not devices:
        print("❌ 错误: 未检测到已连接的 Android 设备或模拟器。请使用 adb devices 检查连接状态。", file=sys.stderr)
        sys.exit(1)

    if serial:
        if serial not in devices:
            print(f"❌ 错误: 指定的设备序列号 '{serial}' 未连接。当前在线设备: {devices}", file=sys.stderr)
            sys.exit(1)
        return serial

    if len(devices) > 1:
        print(f"ℹ️ 检测到多个设备: {devices}，默认使用第一个: {devices[0]} (可通过 -s 指定设备)")
    return devices[0]


def get_device_model(adb_bin: str, serial: str) -> str:
    """获取设备型号"""
    _, model, _ = run_cmd(adb_bin, ["shell", "getprop", "ro.product.model"], serial=serial)
    return model if model else serial


def check_app_installed(adb_bin: str, serial: str, package: str):
    """检查 App 是否已安装"""
    code, stdout, _ = run_cmd(adb_bin, ["shell", "pm", "path", package], serial=serial)
    if not stdout or "package:" not in stdout:
        print(f"❌ 错误: 目标应用 {package} 未安装在设备上。请先运行 ./gradlew :app:installDebug 安装应用。", file=sys.stderr)
        sys.exit(1)


def grant_audio_permissions(adb_bin: str, serial: str, package: str):
    """授予音频读取权限"""
    permissions = [
        "android.permission.READ_MEDIA_AUDIO",
        "android.permission.READ_EXTERNAL_STORAGE",
    ]
    for perm in permissions:
        run_cmd(adb_bin, ["shell", "pm", "grant", package, perm], serial=serial)


def measure_single_run(
    adb_bin: str,
    serial: str,
    package: str,
    activity: str,
    mode: str,
    timeout: float = 15.0,
) -> Optional[Dict]:
    """执行单次测量"""
    component = f"{package}/{activity}"

    # 1. 准备环境
    if mode == "cold":
        run_cmd(adb_bin, ["shell", "am", "force-stop", package], serial=serial)
        time.sleep(0.5)
    elif mode == "warm":
        # 先拉起到前台，再按 Home 回到后台
        run_cmd(adb_bin, ["shell", "input", "keyevent", "KEYCODE_HOME"], serial=serial)
        time.sleep(0.5)

    # 2. 清空 logcat 缓存
    run_cmd(adb_bin, ["shell", "logcat", "-c"], serial=serial)

    # 3. 启动 logcat 异步监听
    # 监听 BenchmarkTrace (歌曲列表排版) 和 ActivityTaskManager (系统首帧)
    logcat_cmd = [
        adb_bin,
        "-s",
        serial,
        "shell",
        "logcat",
        "-v",
        "time",
        "-s",
        f"{BENCHMARK_TAG}:I",
        f"{DISPLAYED_TAG}:I",
    ]
    logcat_proc = subprocess.Popen(
        logcat_cmd,
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="replace",
    )

    # 给 logcat 监听建立留微小时间
    time.sleep(0.05)

    # 4. 启动应用
    start_flags = ["-W"]
    if mode == "cold":
        start_flags.append("-S")

    t_host_start = time.perf_counter()
    code, stdout, stderr = run_cmd(
        adb_bin,
        ["shell", "am", "start"] + start_flags + ["-n", component],
        serial=serial,
        timeout=int(timeout),
    )

    # 从 am start -W 输出中提取 TotalTime / WaitTime
    am_total_time = None
    am_wait_time = None
    for line in stdout.splitlines():
        if line.startswith("TotalTime:"):
            try:
                am_total_time = float(line.split(":")[1].strip())
            except ValueError:
                pass
        elif line.startswith("WaitTime:"):
            try:
                am_wait_time = float(line.split(":")[1].strip())
            except ValueError:
                pass

    # 5. 读取 logcat 直到捕获 BenchmarkTrace 或超时
    benchmark_duration_ms = None
    track_count = None
    system_displayed_ms = None
    end_to_end_ms = None

    start_wait = time.time()
    try:
        while time.time() - start_wait < timeout:
            line = logcat_proc.stdout.readline()
            if not line:
                time.sleep(0.02)
                continue

            # 匹配系统 Displayed: ActivityTaskManager: Displayed com.musicapp.player/.MainActivity: +450ms
            if "Displayed" in line and activity in line:
                match = re.search(r"\+(\d+(?:\.\d+)?)ms", line)
                if match:
                    system_displayed_ms = float(match.group(1))

            # 匹配 BenchmarkTrace: TracksFirstTrackLaidOut duration_ms=123.4 track_count=50 start_ns=... end_ns=...
            if BENCHMARK_TAG in line and "TracksFirstTrackLaidOut" in line:
                t_event_received = time.perf_counter()
                end_to_end_ms = (t_event_received - t_host_start) * 1000.0

                dur_match = re.search(r"duration_ms=([\d\.]+)", line)
                if dur_match:
                    benchmark_duration_ms = float(dur_match.group(1))

                cnt_match = re.search(r"track_count=(\d+)", line)
                if cnt_match:
                    track_count = int(cnt_match.group(1))

                break
    finally:
        logcat_proc.terminate()
        logcat_proc.kill()

    # 如果系统 Displayed 没从 logcat 拿到，使用 am start -W 的 TotalTime
    if system_displayed_ms is None and am_total_time is not None:
        system_displayed_ms = am_total_time

    if end_to_end_ms is None:
        return None

    delta_ms = (end_to_end_ms - system_displayed_ms) if system_displayed_ms else None

    return {
        "mode": mode,
        "end_to_end_ms": round(end_to_end_ms, 2),
        "system_displayed_ms": round(system_displayed_ms, 2) if system_displayed_ms else None,
        "compose_layout_ms": round(benchmark_duration_ms, 2) if benchmark_duration_ms else None,
        "delta_ms": round(delta_ms, 2) if delta_ms else None,
        "track_count": track_count,
        "am_total_time": am_total_time,
        "am_wait_time": am_wait_time,
    }


def compute_stats(values: List[float]) -> Dict[str, float]:
    """计算统计指标（最小值、最大值、均值、中位数、P90、标准差）"""
    if not values:
        return {}
    sorted_vals = sorted(values)
    n = len(sorted_vals)
    mean_val = sum(sorted_vals) / n

    # 中位数
    if n % 2 == 1:
        median_val = sorted_vals[n // 2]
    else:
        median_val = (sorted_vals[n // 2 - 1] + sorted_vals[n // 2]) / 2.0

    # P90
    p90_idx = int(math.ceil(0.90 * n)) - 1
    p90_val = sorted_vals[max(0, min(p90_idx, n - 1))]

    # 标准差
    if n > 1:
        variance = sum((x - mean_val) ** 2 for x in sorted_vals) / (n - 1)
        std_dev = math.sqrt(variance)
    else:
        std_dev = 0.0

    return {
        "min": round(sorted_vals[0], 2),
        "max": round(sorted_vals[-1], 2),
        "mean": round(mean_val, 2),
        "median": round(median_val, 2),
        "p90": round(p90_val, 2),
        "std_dev": round(std_dev, 2),
    }


def format_summary_text(
    device: str,
    device_model: str,
    package: str,
    activity: str,
    mode: str,
    results: List[Dict],
    e2e_stats: Dict,
    disp_stats: Dict,
    compose_stats: Dict,
    delta_stats: Dict,
) -> str:
    """生成格式化文本报告"""
    lines = []
    lines.append("=" * 75)
    lines.append("🎵 MusicApp 启动到歌曲列表刷新时间性能测试报告")
    lines.append("=" * 75)
    lines.append(f"📱 目标设备 : {device} ({device_model})")
    lines.append(f"📦 目标应用 : {package}")
    lines.append(f"🚀 入口组件 : {activity}")
    lines.append(f"🔄 启动模式 : {'冷启动 (Cold Start)' if mode == 'cold' else '温启动 (Warm Start)'}")
    lines.append(f"🔢 有效样本 : {len(results)} 轮")
    lines.append("=" * 75)
    lines.append("\n📈 各轮次明细数据 (单位: ms)")
    lines.append("-" * 75)
    lines.append(f"{'轮次':^6} | {'端到端耗时':^14} | {'系统首帧(TTID)':^14} | {'UI歌曲排版耗时':^14} | {'曲目总数':^8}")
    lines.append("-" * 75)
    for idx, r in enumerate(results, 1):
        disp_str = f"{r['system_displayed_ms']:.1f}" if r["system_displayed_ms"] else "N/A"
        comp_str = f"{r['compose_layout_ms']:.1f}" if r["compose_layout_ms"] else "N/A"
        count_str = f"{r['track_count']}" if r["track_count"] is not None else "N/A"
        lines.append(f"{idx:^6} | {r['end_to_end_ms']:^14.1f} | {disp_str:^16} | {comp_str:^16} | {count_str:^8}")
    lines.append("=" * 75)
    lines.append("\n🎯 汇总统计结果 (单位: ms):")
    lines.append("-" * 75)
    lines.append(f"{'指标名称':<22} | {'Min':^8} | {'Max':^8} | {'Mean':^8} | {'Median':^8} | {'P90':^8} | {'StdDev':^8}")
    lines.append("-" * 75)
    lines.append(
        f"{'端到端总耗时 (E2E)':<22} | "
        f"{e2e_stats.get('min', 0):8.1f} | {e2e_stats.get('max', 0):8.1f} | "
        f"{e2e_stats.get('mean', 0):8.1f} | {e2e_stats.get('median', 0):8.1f} | "
        f"{e2e_stats.get('p90', 0):8.1f} | {e2e_stats.get('std_dev', 0):8.1f}"
    )
    if disp_stats:
        lines.append(
            f"{'系统首帧 (Displayed)':<22} | "
            f"{disp_stats.get('min', 0):8.1f} | {disp_stats.get('max', 0):8.1f} | "
            f"{disp_stats.get('mean', 0):8.1f} | {disp_stats.get('median', 0):8.1f} | "
            f"{disp_stats.get('p90', 0):8.1f} | {disp_stats.get('std_dev', 0):8.1f}"
        )
    if compose_stats:
        lines.append(
            f"{'歌曲排版 (Layout)':<22} | "
            f"{compose_stats.get('min', 0):8.1f} | {compose_stats.get('max', 0):8.1f} | "
            f"{compose_stats.get('mean', 0):8.1f} | {compose_stats.get('median', 0):8.1f} | "
            f"{compose_stats.get('p90', 0):8.1f} | {compose_stats.get('std_dev', 0):8.1f}"
        )
    if delta_stats:
        lines.append(
            f"{'首帧后等待增量 (Delta)':<22} | "
            f"{delta_stats.get('min', 0):8.1f} | {delta_stats.get('max', 0):8.1f} | "
            f"{delta_stats.get('mean', 0):8.1f} | {delta_stats.get('median', 0):8.1f} | "
            f"{delta_stats.get('p90', 0):8.1f} | {delta_stats.get('std_dev', 0):8.1f}"
        )
    lines.append("=" * 75)
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(
        description="MusicApp - 启动后到歌曲列表刷新时间自动化测量脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  # 默认进行 5 次冷启动测试并自动保存结果到 benchmark_results/
  python3 scripts/measure_startup_time.py

  # 指定测试 10 轮，并在测试前预热 1 轮
  python3 scripts/measure_startup_time.py -n 10 --warmup 1

  # 指定自定义输出目录
  python3 scripts/measure_startup_time.py --output-dir my_reports/

  # 不保存结果文件（仅终端打印）
  python3 scripts/measure_startup_time.py --no-save
""",
    )
    parser.add_argument("-p", "--package", default=DEFAULT_PACKAGE, help=f"应用包名 (默认: {DEFAULT_PACKAGE})")
    parser.add_argument("-a", "--activity", default=DEFAULT_ACTIVITY, help=f"入口 Activity (默认: {DEFAULT_ACTIVITY})")
    parser.add_argument("-s", "--serial", default=None, help="ADB 设备序列号 (多设备时必填)")
    parser.add_argument("-n", "--runs", type=int, default=5, help="测量轮数 (默认: 5)")
    parser.add_argument("-w", "--warmup", type=int, default=1, help="预热轮数 (默认: 1，不计入最终统计)")
    parser.add_argument("-m", "--mode", choices=["cold", "warm"], default="cold", help="启动模式: cold(冷启动) / warm(温启动) (默认: cold)")
    parser.add_argument("--timeout", type=float, default=15.0, help="单次测试超时时间(秒) (默认: 15.0)")
    parser.add_argument("--no-grant-permissions", action="store_true", help="跳过自动授予媒体读取权限")
    parser.add_argument("--output-dir", default=DEFAULT_OUTPUT_DIR, help=f"结果保存目录 (默认: {DEFAULT_OUTPUT_DIR})")
    parser.add_argument("--output-json", default=None, help="显式指定输出的 JSON 文件路径")
    parser.add_argument("--no-save", action="store_true", help="不自动保存结果到文件")
    parser.add_argument("--adb-path", default=None, help="自定义 adb 可执行文件路径")

    args = parser.parse_args()

    adb_bin = find_adb(args.adb_path)
    serial = check_device(adb_bin, args.serial)
    device_model = get_device_model(adb_bin, serial)
    check_app_installed(adb_bin, serial, args.package)

    if not args.no_grant_permissions:
        grant_audio_permissions(adb_bin, serial, args.package)

    print("=" * 75)
    print("🎵 MusicApp 启动到歌曲列表刷新时间性能测试")
    print("=" * 75)
    print(f"📱 目标设备 : {serial} ({device_model})")
    print(f"📦 目标应用 : {args.package}")
    print(f"🚀 入口组件 : {args.activity}")
    print(f"🔄 启动模式 : {'冷启动 (Cold Start)' if args.mode == 'cold' else '温启动 (Warm Start)'}")
    print(f"🔢 测试轮数 : {args.runs} 轮 (预热: {args.warmup} 轮)")
    print("=" * 75)

    # 预热轮次
    if args.warmup > 0:
        print(f"\n🔥 执行 {args.warmup} 轮预热...")
        for i in range(args.warmup):
            print(f"   [预热 {i + 1}/{args.warmup}] ...", end="", flush=True)
            res = measure_single_run(adb_bin, serial, args.package, args.activity, args.mode, args.timeout)
            if res:
                print(f" 完成 (端到端: {res['end_to_end_ms']}ms, 歌曲排版: {res['compose_layout_ms']}ms)")
            else:
                print(" ⚠️ 超时或未捕获到歌曲排版事件 (请确保设备媒体库中有音频文件)")
            time.sleep(1.5)

    # 正式测量
    print(f"\n📊 开始正式测试 (共 {args.runs} 轮)...")
    results = []
    for i in range(args.runs):
        print(f"   👉 轮次 {i + 1:2d}/{args.runs:2d} : 启动中...", end="", flush=True)
        res = measure_single_run(adb_bin, serial, args.package, args.activity, args.mode, args.timeout)
        if res:
            results.append(res)
            print(
                f" 完成! [端到端: {res['end_to_end_ms']:6.1f} ms | "
                f"系统首帧: {res['system_displayed_ms'] if res['system_displayed_ms'] else 0:5.1f} ms | "
                f"歌曲排版: {res['compose_layout_ms'] if res['compose_layout_ms'] else 0:5.1f} ms | "
                f"曲目数: {res['track_count']}]"
            )
        else:
            print(" ❌ 超时 (未在时限内捕获到 TracksFirstTrackLaidOut)")
        time.sleep(2.0)

    if not results:
        print("\n❌ 错误: 所有轮次均未成功捕获到歌曲列表刷新事件。")
        print("💡 排查建议:")
        print("   1. 检查设备媒体库是否有音乐曲目（如果是新模拟器，请先通过 adb push 几首 mp3 进设备）。")
        print("   2. 检查应用是否需要首次手动授予媒体权限。")
        print("   3. 检查 logcat 是否能输出 Tag 为 'BenchmarkTrace' 的日志。")
        sys.exit(1)

    # 统计指标
    e2e_stats = compute_stats([r["end_to_end_ms"] for r in results])
    disp_stats = compute_stats([r["system_displayed_ms"] for r in results if r["system_displayed_ms"] is not None])
    compose_stats = compute_stats([r["compose_layout_ms"] for r in results if r["compose_layout_ms"] is not None])
    delta_stats = compute_stats([r["delta_ms"] for r in results if r["delta_ms"] is not None])

    # 打印终端汇总报告
    summary_text = format_summary_text(
        device=serial,
        device_model=device_model,
        package=args.package,
        activity=args.activity,
        mode=args.mode,
        results=results,
        e2e_stats=e2e_stats,
        disp_stats=disp_stats,
        compose_stats=compose_stats,
        delta_stats=delta_stats,
    )
    print("\n" + summary_text)

    # 自动保存结果到新文件
    if not args.no_save:
        now = datetime.datetime.now()
        timestamp_str = now.strftime("%Y%m%d_%H%M%S")
        iso_timestamp = now.isoformat()

        output_dir = os.path.abspath(args.output_dir)
        os.makedirs(output_dir, exist_ok=True)

        if args.output_json:
            json_path = os.path.abspath(args.output_json)
        else:
            filename = f"startup_{timestamp_str}_{args.mode}_{len(results)}runs.json"
            json_path = os.path.join(output_dir, filename)

        export_data = {
            "timestamp": iso_timestamp,
            "device": {
                "serial": serial,
                "model": device_model,
            },
            "package": args.package,
            "activity": args.activity,
            "mode": args.mode,
            "total_runs": len(results),
            "runs_detail": [
                {
                    "run_index": idx,
                    **r,
                }
                for idx, r in enumerate(results, 1)
            ],
            "statistics": {
                "end_to_end": e2e_stats,
                "system_displayed": disp_stats,
                "compose_layout": compose_stats,
                "delta": delta_stats,
            },
        }

        with open(json_path, "w", encoding="utf-8") as f:
            json.dump(export_data, f, ensure_ascii=False, indent=2)

        # 同时保存格式化的文本摘要文件
        txt_path = os.path.splitext(json_path)[0] + ".txt"
        with open(txt_path, "w", encoding="utf-8") as f:
            f.write(summary_text + "\n")

        print("\n💾 测试结果已自动保存为新文件:")
        print(f"   📄 JSON 数据 : {json_path}")
        print(f"   📋 文本摘要 : {txt_path}")
        print(f"   📁 存储目录已由 .gitignore 自动忽略: {output_dir}")


if __name__ == "__main__":
    main()
