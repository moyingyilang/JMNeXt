# JMNeXt 品牌资源

## 命名规则

`<对象>-<变体>[-<表面>]`

- 对象：`icon` / `adaptive` / `about` / `badge`
- 表面：`dark` / `light`，指用于深色还是浅色页面
- 全部小写、连字符分隔；PNG 的尺寸就是文件名（`512.png`）

目录不重复文件名里的信息：`png/icon/orange-dark/512.png` 而不是 `png/icon/jmnext-icon-orange-dark-512.png`。

## 目录

```
docs/branding/
├── README.md
├── svg/
│   ├── icon-orange-dark.svg            图标成品，深色底 + 橙加号
│   ├── icon-orange-light.svg           图标成品，浅色底 + 橙加号
│   ├── icon-cyan-dark.svg              图标成品，深色底 + 青加号
│   ├── icon-cyan-light.svg             图标成品，浅色底 + 青加号
│   ├── adaptive-foreground-orange.svg  自适应图标前景层（透明，橙）
│   ├── adaptive-foreground-cyan.svg    自适应图标前景层（透明，青）
│   ├── adaptive-monochrome.svg         自适应图标单色层（全白）
│   ├── about-header.svg                关于页头图，六边形徽章 1280 × 460
│   ├── about-title-dark.svg            关于页标题，白字 1280 × 300
│   ├── about-title-light.svg           关于页标题，深字 1280 × 300
│   ├── badge-kotlin-compose-dark.svg   Powered by Kotlin and Compose，深底 1280 × 160
│   ├── badge-kotlin-compose-light.svg  同上，浅底
│   ├── badge-qt-dark.svg               Smaller. Lighter. Faster!，深底 1280 × 220
│   ├── badge-qt-light.svg              同上，浅底
│   ├── badge-credit-dark.svg           制作署名，深底 1280 × 160
│   └── badge-credit-light.svg          同上，浅底
├── png/
│   ├── icon/
│   │   ├── orange-dark/    512 384 256 192 144 96 72 48 32 24
│   │   ├── orange-light/   同上
│   │   ├── cyan-dark/      同上
│   │   └── cyan-light/     同上
│   ├── about-title-dark.png      透明底，1280 × 300
│   ├── about-title-light.png     透明底，1280 × 300
│   ├── badge-kotlin-compose-dark.png   透明底，1280 × 160
│   ├── badge-kotlin-compose-light.png  透明底，1280 × 160
│   ├── badge-qt-dark.png              透明底，1280 × 220
│   ├── badge-qt-light.png             透明底，1280 × 220
│   ├── badge-credit-dark.png          透明底，1280 × 160
│   └── badge-credit-light.png         透明底，1280 × 160
└── preview/
    ├── icon-sheet.png      四套图标总览 + 小尺寸对照
    ├── about-header.png    关于页头图，深/浅两版
    ├── about-title.png     关于页标题，深/浅两版
    ├── badges.png          技术栈标牌，深/浅两版
    ├── badge-credit.png    制作署名，深/浅两版
    └── archive/            设计过程稿，不参与使用
```

## 图标规格

| 项 | 值 |
| --- | --- |
| 画布 | 108 × 108（Android 自适应图标网格） |
| 笔画 | 9.5，圆头圆角 |
| 加号 | 与 M 右腿同宽同轴（x=77） |
| 内容安全半径 | 36（圆形遮罩不裁切） |
| 底板圆角 | 24 |

## 配色

| 皮肤 | 底色 | 字母 | 强调色 | 强调色对比度 |
| --- | --- | --- | --- | --- |
| `orange-dark` | #0D1422 | #FFFFFF | #FF7B00 | 7.1:1 |
| `orange-light` | #EEF2F9 | #0C1626 | #FF7B00 | 2.3:1 |
| `cyan-dark` | #0D1422 | #FFFFFF | #22D3EE | 10.2:1 |
| `cyan-light` | #EEF2F9 | #0C1626 | #0891B2 | 3.3:1 |

`#FF7B00` 取自 JMComic_SRC 的 `static/css/index.css`（swiper 分页点、滚动条）。
浅色皮肤的强调色刻意压深一档：原青 `#22D3EE` 在 `#EEF2F9` 上只有 1.6:1。

## Android 自适应图标接法

1. `values/ic_launcher_background.xml` 改成对应底色（如 `#0D1422`）。
2. `mipmap-anydpi-v26/ic_launcher.xml`：
   - `background` 指向纯色
   - `foreground` 指向 `adaptive-foreground-*.svg` 转出的 VectorDrawable
   - `monochrome` 指向 `adaptive-monochrome.svg`
3. 旧位图按五档密度生成：108dp × 密度 → mdpi 108、hdpi 162、xhdpi 216、xxhdpi 324、xxxhdpi 432。

## 已知取舍

- **24px 下 M 会读成 N**：M 的 V 底是圆弧、深度只有 20，又被同宽加号盖住峰顶，剩余右斜线偏短。48px 以上正常。
- **浅色皮肤的橙加号只有 2.3:1**：与浅底青同样的病，只是没那么明显。
- **关于页标题是纯实色**：去掉了重影与投影，只剩超粗斜体 + 紧字距。
- **标牌文字宽度 1170 / 1280**：制作署名那一张左右只剩 55px 边距，再加内容就要降字号。
