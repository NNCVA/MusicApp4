# MusicApp Wave 执行计划索引

状态：`DOING`

本目录把 [`../implementation-wave-plan.md`](../implementation-wave-plan.md) 中的 Wave 0–8 作为阶段门禁，并进一步拆成可独立提交、独立验证、独立回滚的执行单元。

## 状态标记

| 标记 | 含义 | 更新要求 |
|---|---|---|
| `TODO` | 尚未开始 | 前置单元全部为 `DONE` 后才能开始 |
| `DOING` | 正在执行 | 先填写开始时间和执行者；同一文件边界只允许一个单元处于此状态 |
| `DONE` | 实现及验证均完成 | 填写完成时间、提交和验证证据；不得仅因代码写完而标记 |
| `BLOCKED` | 当前无法继续 | 填写阻塞原因、已有证据和解除条件 |

Wave 状态由其执行单元推导：全部 `TODO` 时为 `TODO`；存在 `DOING` 时为 `DOING`；任一关键路径单元为 `BLOCKED` 时为 `BLOCKED`；全部执行单元与 Wave 门禁均通过后为 `DONE`。

## 最小执行单元

每个 `Wn-xx` 必须同时满足以下条件：

- 只有一个可描述的交付结果，不混合无依赖关系的功能。
- 文件边界明确，可形成单独提交并在失败时单独回滚。
- 至少有一项最窄自动化验证；平台行为无法用本地测试证明时，明确设备/API 级别。
- 完成条件只验证本单元，不借用后续 Wave 尚未实现的能力。

## 记录规则

开始执行单元时，将状态改为 `DOING`，并填写执行记录中的开始时间与执行者。验证通过后填写提交哈希、命令或设备证据，再改为 `DONE`；验证未通过或环境缺失时改为 `BLOCKED`，不得用“代码已写完”代替验证完成。

计划中的 Gradle 任务在 Wave 0 冻结前属于目标任务名；实际执行时必须以 `./gradlew tasks --all` 可发现的任务为准。设备测试没有可用设备、依赖或测试数据时应记录为 `BLOCKED`。

## 执行顺序

- [Wave 0：工程与质量底座](wave-0-engineering-foundation.md)
- [Wave 1：领域、Room 与设置事实来源](wave-1-domain-data-settings.md)
- [Wave 2：设计系统与应用壳层](wave-2-design-system-app-shell.md)
- [Wave 3：媒体库纵向闭环](wave-3-media-library.md)
- [Wave 4：播放内核与系统媒体](wave-4-playback-system-media.md)
- [Wave 5：播放器 UI、队列与歌词](wave-5-player-ui-lyrics.md)
- [Wave 6：分类浏览、播放列表与历史](wave-6-library-features.md)
- [Wave 7：设置、Aero、数据管理与关于](wave-7-settings-aero-about.md)
- [Wave 8：发布硬化与首版验收](wave-8-release-acceptance.md)

## 单元执行记录模板

```text
状态：TODO
开始时间：—
完成时间：—
执行者：—
提交：—
验证证据：—
阻塞原因：—
```
