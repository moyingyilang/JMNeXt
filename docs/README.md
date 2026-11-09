# 文档索引

先说清楚该读哪一份，避免在几万字里迷路。

| 文档 | 给谁看 | 内容 |
| --- | --- | --- |
| [`STATE.md`](STATE.md) | **接手的人（或下一个会话）先看这份** | 现状总表：什么已发布、什么在飞、环境怎么进、构建与核对怎么做、发布流程、已验证与未验证的边界、未完成清单、踩过的坑 |
| [`requirements.md`](requirements.md) | 想核对"用户到底要求了什么" | 用户全部明确要求的归并表（七类 60 余条），逐条标注落实状态 |
| [`PARITY-AUDIT.md`](PARITY-AUDIT.md) | 想知道"还缺哪些 Android 功能" | 以代码为证据的逐屏对齐审计（严重 8 / 中等 11 / 轻微 8）+ 修复进度（含提交号）+ 接手必读 |
| [`SELFTUNE.md`](../desktop/SELFTUNE.md) | 要看自学习算法怎么设计与验证 | 调什么、看什么信号、适应度、算法形式、安全阀、离线回放实测表与限制 |
| [`engineering-notes.md`](engineering-notes.md) | 想了解历史工程细节 | 早期工程笔记（构建坑、静默失败坑、主题校验等），保留全文 |
| [`VIRTUAL-DISPLAY.md`](VIRTUAL-DISPLAY.md) | 要做**干净画面**的自动测试 | 两条隔离路线（固定尺寸浮窗裁剪 / VirtualDisplay）、两个 display id 的区别与反查、黑屏排查清单、安全红线；配 `scripts/vd.sh` |
| [`archive/parity-log.md`](archive/parity-log.md) | 想查某个问题的排查过程 | 开发日志全文（很长）。**其中多处已过时、且有明确写错处**，凡涉及"某功能是否已移植"一律以 PARITY-AUDIT 与代码为准 |
| [`../CHANGELOG.md`](../CHANGELOG.md) | 想看每个版本改了什么 | 版本索引 + 各版本说明（含未验证项声明） |
| [`../README.md`](../README.md) | 想安装与上手 | 面向使用者的说明（下载、运行、平台支持、自己构建） |
