# 设计文档入口

本目录只保留仍需跨页面复用的组件规范、资源治理和业务规则。页面行为的权威顺序是 `app/` 代码、自动化测试、实际设备验证；页面 PRD、首版设计 review 和实施规格在实现落地后移入 [`archive/`](archive/)，只用于追溯需求背景。

## 当前维护

- [`selection-and-toggle-controls.md`](selection-and-toggle-controls.md)：选择、Toggle、Radio 和整行交互语义。
- [`resource-governance.md`](resource-governance.md)：图标、资源来源和许可证记录。
- [`artist-splitting-rules.md`](artist-splitting-rules.md)：多艺术家合作分隔符、白名单与拆分匹配规范。
- [`album-grouping-rules.md`](album-grouping-rules.md)：专辑应用层聚合、版本识别、群星合辑和未知专辑规则。
- [`audio-format-registry.md`](audio-format-registry.md)：受支持音频格式、MIME 别名和短音频准入门限。

## 历史资料

`archive/` 包含页面 PRD、`design-review-*`、首版实现规格和已完成页面的冻结规格。历史资料与当前实现冲突时，以代码和测试结果为准；若取舍仍需确认，在对应 ADR 中记录冲突、决定和迁移影响。
