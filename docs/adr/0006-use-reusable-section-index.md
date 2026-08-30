# 使用可复用的字母分组索引

> [!NOTE]
> **状态：已由 [0008-use-unified-right-gutter-overlay-and-fixed-index.md](0008-use-unified-right-gutter-overlay-and-fixed-index.md) 取代 (Superseded)**。统一由动态桶升级为固定 28 逻辑桶、并引入 RightGutterOverlay 与拖动气泡。

TracksScreen 按当前结果中实际存在的可索引文本属性首字符生成分组头，字母索引只展示这些分组；点击索引定位到对应分组头，滚动位置反向更新索引高亮，分组头吸顶并由下一分组推挤。日期和时长等不可索引排序保持平铺列表。索引条由独立的 Compose 组件承载，页面提供分组数据及定位、高亮交互回调；选择动态数据和页面与索引组件的边界，是为了让搜索、筛选和排序后的索引始终与结果一致并允许其他分组列表复用，代价是页面必须维护分组顺序与滚动交互之间的契约。
