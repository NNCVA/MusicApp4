# 使用 Hilt 管理依赖生命周期

MusicApp 使用 Hilt 创建并注入 Application、Room、Repository、MediaLibraryService 与 ViewModel 所需依赖。播放器服务与多个功能页面共享进程级数据对象，集中作用域管理比手工单例和逐层工厂更能避免重复实例与生命周期错配，代价是接受 Hilt 的注解处理和框架约束。
