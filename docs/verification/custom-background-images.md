# 自定义背景图片验证记录

日期：2026-10-09。分支：`feat/background_pic`。

## 实现范围

- 设置页「主界面」提供图片选择、已设置/失效状态与删除；系统单图选择器只接收图片，取消不改变原背景。重置设置会删除自定义背景。
- `BackgroundRepository` 在应用内部 `filesDir/background` 保存规范化 PNG 和选择元数据；后台校正 EXIF 方向、提取静态首帧、按原比例降采样。输入上限 100 MiB、128 MP，保存长边不超过 2560 px。新图和元数据完整写入、同步后才原子发布，失败和取消保留旧图。
- 启动时核对文件、摘要和可解码性；丢失/损坏时回退并标记失效；过期图片请求不能使新选择失效；清理临时文件和旧文件。删除或清理失败不能使页面崩溃，删除操作失败由设置提示。
- 根页面与 Mini 共用一个解码结果和窗口居中裁剪变换；Mini 补偿面板位移，包含系统底部区域，不按侧栏缩窄后的内容宽度重新裁剪。横竖屏与窗口调整重新计算，替换期间保留上次成功渲染的图。每块背景从同一根主题底色合成，避免半透明 PNG 在 Mini 重复叠加。
- 背景和 Mini 不加蒙层；既有设置、侧栏、扫描、文件夹卡片按限定作用域使用 50% 容器透明度，临时浮层保留实色。播放详情仍使用独立主题/Aero，均衡器两条入口不显示自定义图。
- Room 列表/详情和搜索未就绪时绘制纯色空白；专辑详情仅在自定义背景可用时停用局部封面装饰。

## 自动化覆盖

- JVM：窗口居中裁剪、旋转适配、Mini/系统底部区域的连续坐标、非法尺寸；尺寸/输入限额和元数据校验；设置删除失败与重置背景。
- Android Runtime 测试源码：真实图片持久化、替换、源文件删除、缺失/坏图恢复、IO/OOM/取消注入、EXIF 方向、降采样、动图首帧；选择行热区与独立删除、禁用状态；根背景与 Mini 像素连续性；纯色加载占位和专辑 hero 时序。

## 本次命令与结果

使用 Java 21 执行：

```shell
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon --console=plain
git diff --check
adb devices -l
```

首次 Gradle 被 `~/.gradle` 锁文件沙箱权限拦截，使用现有缓存的授权执行重试；编译阶段发现的搜索页 Spacer 缺失和 Runtime 测试多余导入已修复。独立静态 review 发现并修复了半透明 PNG 重复合成与睡眠定时弹层继承卡片透明范围，最终门禁在这些修复后执行。

消除新增图片缩放代码的 KTX 写法警告后，最终命令再次退出码为 0，`BUILD SUCCESSFUL in 1m`：

| 门禁 | 结果 |
| --- | --- |
| `testDebugUnitTest` | 117 个测试类、795 项测试，0 失败、0 错误、0 跳过 |
| `lintDebug` | 通过，0 错误；保留 6 项位于未改动文件的现有警告 |
| `assembleDebug` | 通过 |
| `assembleDebugAndroidTest` | 编译通过；未运行 Runtime 测试 |
| `git diff --check` | 通过 |

本轮新增 9 项 JVM 测试，Runtime 测试源码新增 18 项。最终 Gradle 输出：`/private/tmp/musicapp4-background-final-gate.log`；测试结果：`app/build/test-results/testDebugUnitTest/`；Lint 报告：`app/build/reports/lint-results-debug.html`。Debug APK：`app/build/outputs/apk/debug/app-debug.apk`。

`adb devices -l` 没有连接设备；本轮未执行 `connectedDebugAndroidTest`、实际图片选择器交互或设备视觉验收。测试 APK 编译与 Runtime 通过严格分开。

## 设备视觉验收范围

后续在设备验证浅/深色主题、竖屏/横屏、600/840dp 侧栏、抽屉开合、Mini 展开收起与手势导航/三键导航；确认 Mini 与页面裁剪连续、无图片蒙层、播放详情及均衡器隔离、重启保存和删除恢复。无蒙层模式下，文字辨识受用户所选图片影响。

API 依据：[Android Photo Picker](https://developer.android.com/training/data-storage/shared/photo-picker)、[Coil Compose](https://coil-kt.github.io/coil/compose/)。
