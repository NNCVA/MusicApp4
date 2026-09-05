# 使用应用层 AlbumGroupKey 进行同卷专辑聚合

## 背景与问题
Android 系统原生 `MediaStore.Audio.Media.ALBUM_ID` 为 MediaProvider 专辑身份标识。在实际设备中，多歌手/合唱标签（如“周杰伦”与“周杰伦 / 梁心颐”）、不同存储路径（如 `Music/` 与 `Download/`）或跨扫描批次入库时，MediaStore 会为同一张专辑分配不同的 `ALBUM_ID`。此前直接按 `(volumeName, ALBUM_ID)` 分组导致如《跨时代》、《魔杰座》被割裂为多张只有部分曲目的独立卡片。

## 决策
1. **应用层聚合**：同一存储卷内，按 Unicode 兼容规范化（NFKC、连续空格折叠、小写归一）专辑名并结合多歌手分隔符（`/`、`、`、`\`、`,`、`;`、`，`、`；`、`&`、`feat.`、`ft.`）进行兼容聚合；排斥发行年份与版本特征词（如 Live/Remix/Deluxe 等）冲突，防范主艺人传递性误合并；不同存储卷或同名但艺人冲突的集合保持分开。
2. **标识与路由契约稳定**：`AlbumSummary` 保留代表性 `AlbumId` 作为封面与播放来源，同时携带包含规范化标题、艺人签名、版本关键词和发行年份集合的编码 `AlbumGroupKey`。`AlbumDetailRoute` 传递该聚合键，并保留 `volumeName/mediaStoreId` 作为旧快照兼容回退；详情通过已分组摘要的 `trackIds` 精确取曲目并集。
3. **零破坏性存储迁移**：首版不修改 Room 实体表结构或数据库迁移，从既有 Room 曲目流实时计算派生，保证旧缓存与新扫描曲目即时生效。
