# 动效知识来源与适用边界

> 这份文档回答两个问题：我们的动效参数**依据什么**、以及**哪些依据我其实没验证到**。
> 后者比前者重要 —— 把"没验证"写成"有依据"，后面所有决策都会建在沙子上。

## 一、用到的原则（可判定，已落进代码）

| 原则 | 具体化 | 落点 |
| --- | --- | --- |
| 空间与效果要分开处理 | 位移类（转场、工具栏）用"进快出慢"，纯效果类（淡入、按压）用更短的时长 | `JmMotionTable`：`navForward` 300 / `navBack` 260 |
| **退出必须比进入快** | 退出是"撤销"，用户已知目的地 | 比例 0.72 / 0.87 / 0.83 / 0.75，`JmMotionTest` 逐对断言 |
| 距离越大，开头越要给速度 | hero 用 emphasized（起步快、收尾长） | `hero` token；桌面端 `Motion.Standard` 恰好同曲线 |
| 线性只属于"进度未知" | 骨架扫光用线性，其余禁止 | `JmMotionTest.linearIsOnlyForLoading` |
| 尊重"减少动画" | 不是关掉动效，而是退化成 120ms 交叉淡入 | `ReduceMotion.kt` + `reducedMotion()` |
| 按压反馈用**尺寸**而非颜色 | 网格里颜色会被相邻卡片稀释 | `MotionFeedback.kt`（0.97 缩放） |

## 二、我**没有**验证到的部分（如实）

1. **Material 3 的具体 spring 参数没拿到**。m3.material.io 的 token 页是 JS 渲染的，抓取只返回标题；
   `androidx.compose.material3.MotionScheme` 在当前版本是 **internal**，测试代码编译不过。
   所以本项目**没有**照抄任何官方数值 —— 所有参数是我们自己定的，靠单测约束一致性与帧率实测约束性能。
2. **Apple HIG 的 motion 参数没有引用**。曾检索到相关二手材料（WWDC 转述、第三方设计系统笔记），
   但没有原文可核，因此不作为依据。
3. **Compose 官方 `SharedTransitionLayout` 的最佳实践**只按 API 行为推断（`BoundsTransform` 需成对时间线），
   未见官方性能指引。
4. **`SpringSpec` 一处都没用**（除既有物理弹簧）。原因：当前版本 `MotionScheme` 不可用，
   而自定义 spring 的停稳时间难预测；在"帧率预算紧张 + 无法逐项测量"的阶段，tween 更可控。

## 三、外部资料（仅作方向参考，未用于确定数值）

- Material Design 3 · Easing and duration tokens —— <https://m3.material.io/styles/motion/easing-and-duration/tokens-specs>（页面需 JS，未取到正文）
- Compose 动画自定义文档 —— <https://developer.android.com/develop/ui/compose/animation/customize>
- 项目内既有约定 —— `docs/MOTION.md`（Android 主题里的曲线体系、桌面端 `Motion.kt`）

## 四、结论

本项目的动效语言是**自洽的**（三套风格各自派生、比例统一、有单测守门），但它**不是"按官方规格实现"的**，
而是"按可判定的原则自建"。任何"应该改成官方参数"的建议，需要先能拿到官方数值再谈；
在此之前，改动应当以**帧率实测与用户观感**为准。
