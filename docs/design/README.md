# 设计文档说明

本目录包含历史页面设计资料和少量仍在维护的通用规范。当前页面实现以 `app/` 下代码、测试和设备验证为准；历史页面资料已集中到 [`archive/`](archive/) 并不再作为页面开发或验收依据：

- 页面 PRD、`design-review-*` 以及 `implementation-spec.md`：保留作历史背景。
- 仍在维护的通用规范：
  - [`selection-and-toggle-controls.md`](selection-and-toggle-controls.md)：选择、Toggle、Radio 和整行交互语义。
  - [`resource-governance.md`](resource-governance.md)：图标、资源来源和许可证记录。
  - [`artist-splitting-rules.md`](artist-splitting-rules.md)：多艺术家合作分隔符正则、AC/DC 白名单与拆分匹配规范。
  - [`album-grouping-rules.md`](album-grouping-rules.md)：专辑应用层聚合、版本识别关键词、群星合辑判定与未知专辑规则。
  - [`audio-format-registry.md`](audio-format-registry.md)：受支持音频格式扩展名、MIME 别名矩阵与短音频准入门限。

若历史设计与当前代码冲突，以代码和测试结果为准，并在相关 ADR 中记录冲突与后续决定。
