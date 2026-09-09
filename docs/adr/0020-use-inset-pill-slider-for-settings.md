# 普通设置统一使用内嵌胶囊滑块

**Status**: accepted

淡入淡出时长、睡眠定时、歌词字号和歌词字重等普通数值设置统一使用共享的 `InsetPillSlider`：轨道与 Thumb 均为 `16dp`，活动值与未活动值共用胶囊轨道，白色 Thumb 保持在轨道内部，同时保留原有范围、步进、回调时机和 Material Slider 的无障碍语义。播放详情页的 `InteractiveThinProgressBar` 继续保持细长交互进度条，因为它拥有独立的 pending seek 与单次提交契约，不属于普通设置滑块。
