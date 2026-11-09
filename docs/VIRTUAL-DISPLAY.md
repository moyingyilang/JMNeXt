# 虚拟屏与隔离截图（开发测试用）

这份文档解决一个具体问题：**自动测试拿到的画面不干净，或者测试操作会打扰用手机的人。**

ADB `screencap` 抓的是整块屏幕——通知、悬浮窗、状态栏、别的 App 全在里面；
分辨率还随设备变。做视觉回归或"这屏对不对"的判断时，这些全是噪声。

两条隔离路线，按代价从低到高：

| 路线 | 得到什么 | 需要什么 | 改动系统吗 |
| --- | --- | --- | --- |
| **A. 固定尺寸浮窗 + 按窗口裁剪** | 目标 App 的**原分辨率**画面，无通知栏 | 桌面自由窗口（freeform）可用 | 不改分辨率/DPI |
| **B. VirtualDisplay（虚拟屏）** | 一块**独立屏幕**里的画面，用户完全看不到 | root / Shizuku / 系统权限 | 不改（但需权限建屏） |

> 先说结论：日常做「这一屏对不对」的验证，**A 就够**，代价最小；
> 需要**后台跑**（用户同时在用手机）或要**完全隔离输入**时才上 B。

配套脚本：[`scripts/vd.sh`](../scripts/vd.sh)（抓帧 / 点击 / 断言有画面）。
已有的两个探针继续用：[`scripts/px_probe.py`](../scripts/px_probe.py)（像素取色）、
[`scripts/ui_probe.py`](../scripts/ui_probe.py)（控件树定位）。本文档补的是"画面从哪来"。

---

## 0. 一页速查

```bash
# 有没有虚拟屏、叫什么名字
dumpsys SurfaceFlinger --display-id | grep "Virtual display"

# 虚拟屏的 SF id（screencap 用）
dumpsys SurfaceFlinger --display-id | sed -n 's/.*Display \([0-9]*\) (Virtual display).*/\1/p'

# 虚拟屏的逻辑 id（input 用）——必须由 SF id 反查，不要猜
dumpsys display | grep -E "DisplayViewport|DisplayDeviceInfo"

# 抓帧 / 点击
screencap -d <SF id> -p vd.png
input -d <逻辑 id> tap 640 360

# 用脚本
./scripts/vd.sh info
./scripts/vd.sh check            # 抓帧并断言"有画面"
./scripts/vd.sh tap 640 360
./scripts/vd.sh capture /sdcard/vd.png
```

**三条铁律**

1. `screencap` 认 **SurfaceFlinger id**，`input` 认**逻辑 id**，两者不能互换；
2. 逻辑 id 必须由 SF id 反查得到，**查不到就失败**，绝不回落 0（否则会点到用户主屏幕上）；
3. 不碰 `wm size` / `wm density` —— 那会改掉整台手机的显示设置。

---

## 1. 路线 A：固定尺寸浮窗 + 按窗口裁剪

### 做法

```bash
# 1) 把目标 App 的任务塞进一个 16:9 的浮窗（逻辑坐标，注意桌面缩放）
TID=$(dumpsys activity activities | grep -o 'taskId=[0-9]*: <包名>' | grep -o '[0-9]*')
am task resize $TID 100 200 2944 1800

# 2) 取该任务的物理矩形
dumpsys window windows | grep -A20 '<包名>' | grep 'frame=\['

# 3) 抓物理屏 + 按矩形裁剪（不缩放 → 与原分辨率 1:1）
screencap -p /sdcard/full.png
```

裁剪得到的就是"只有这个 App"的画面，**且不改任何系统设置**。

### 实测（本机，桌面缩放 0.45）

```
窗口物理矩形   left=45 top=90 1280x720
裁切后尺寸     1280x720（1:1，无重采样）
```

把窗口的**逻辑**尺寸设成 `2844x1600`，乘 0.45 正好是物理 `1280x720` ——
截图与测试基准分辨率一致，图像比对不需要缩放容差。

### 适合 / 不适合

- ✅ 视觉回归、取色、单屏快照、要和人看到的完全一致
- ❌ 用户同时要用手机（浮窗会挡着）
- ❌ 需要"完全没有其他 App 干扰"的输入注入

---

## 2. 路线 B：VirtualDisplay

### 2.1 原理

```
VirtualDisplay（真正的一块 display，有 displayId + 自己的 Surface）
      │  目标 App 被移动进来，往这块 Surface 渲染
      ▼
Surface（帧缓冲）──────────────► screencap -d <SF id> 拿到的就是它
      │
      ▼
SurfaceFlinger 合成（可选：投到画中画小窗；也可以完全不显示）
```

### 2.2 建屏参数

```java
displayManager.createVirtualDisplay(
    "TestVD", 1280, 720, 240, surface,
    VIRTUAL_DISPLAY_FLAG_PUBLIC
  | VIRTUAL_DISPLAY_FLAG_PRESENTATION
  | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
  | VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH);
```

| flag | 缺了会怎样 |
| --- | --- |
| `PUBLIC` | 别的 App 无法被移动到这块屏 |
| `PRESENTATION` | 部分 ROM 上无法承载普通 Activity |
| `SUPPORTS_TOUCH` | **注入的触摸不生效** |

尺寸建议 **1280×720 / dpi 240**：主流布局高度、截图体积适中、16:9 便于比对。
dpi 会影响 dp 尺寸与布局断点（1280×720 @240dpi = 853×480dp），改 dpi 等于换了一档布局。

### 2.3 把 App 搬进虚拟屏

```bash
am start --display <逻辑 id> -n <pkg>/<activity>
```

实测在 **root / shell / system** 身份下都会被拒：

```
java.lang.SecurityException: ... launchDisplayId ...
```

这是 Android 的 display 权限模型，不是参数写错。**"有 root 就能把任意 App 拉到虚拟屏上"是错的。**

可行路径：

| 路径 | 说明 |
| --- | --- |
| App 自己建 VD 并把内容/任务跑在上面 | 需要改 App，或用支持该模式的壳（如 MaaFwApp 这类"后台跑在虚拟屏"的外壳） |
| 系统签名 / 平台密钥 | 需要自签 ROM 或厂商配合 |
| Shizuku + 厂商放开的接口 | 机型相关，不可移植 |

---

## 3. 两个 id、三个坐标空间

### 3.1 两个 id

| id | 从哪来 | 谁认它 |
| --- | --- | --- |
| **SurfaceFlinger display id** | `dumpsys SurfaceFlinger --display-id` | `screencap -d` |
| **逻辑 display id** | `dumpsys display` / `dumpsys activity activities` | `input -d`、`am start --display` |

混用一定失败：

```
$ screencap -d 25 -p out.png          # 25 是逻辑 id
Error: Invalid display id 25          # screencap 只认 SF id

$ input -d 11529215049578793303 tap 10 10   # 这是 SF id
Error: Invalid arguments for display ID
```

### 3.2 实测输出格式

```
$ dumpsys SurfaceFlinger --display-id
Display 4630946338122044307 (HWC display 0): port=147 pnpId=QCM ... displayName=""
Display 11529215049578793303 (Virtual display): displayName="TestVD"
```

- 物理屏：`(HWC display 0)`，`displayName` 为空；
- 虚拟屏：`(Virtual display): displayName="<建屏时的名字>"` ← **靠名字认屏**。

```
$ dumpsys display | grep -E "mViewports=|DisplayDeviceInfo\{"
mViewports=[DisplayViewport{type=INTERNAL, ..., displayId=0,
             uniqueId='local:4630946338122044307', ...}]
DisplayDeviceInfo{"内置屏幕": uniqueId="local:4630946338122044307", 1440 x 3200, ...}
```

映射规则：

| SF id | 逻辑 uniqueId 前缀 | 逻辑 id |
| --- | --- | --- |
| 物理屏 | `local:` | `0` |
| 虚拟屏 | `virtual:` | 系统分配（本机见过 17/19/23/25…） |

**反查方法**：先用名字拿到 SF id，再去 `dumpsys display` 里找包含该 SF id 的
`DisplayDeviceInfo` 块，取同一块的 `displayId`。`scripts/vd.sh` 已实现，
查不到直接报错退出。

> ⚠️ 不要用"取最后一个 `mDisplayId=`"这种写法——那通常是物理屏 0。
> 这类"拿不到就回落默认值"的代码，正是"点击打到用户主屏幕"事故的成因。

### 3.3 确认 App 在哪块屏

```bash
dumpsys activity activities | grep -E "^ *Display #|realActivity"
```

形如：

```
Display #25 (activities from top to bottom):
  * Task{... <包名>/<Activity>}
```

### 3.4 坐标空间

| 空间 | 用在 | 注意 |
| --- | --- | --- |
| 目标屏像素坐标 | `screencap -d` 的图、`input -d` 的坐标 | 与那块屏的 width×height 一致 |
| 物理屏坐标 | 物理屏截图、不带 `-d` 的 `input` | 不要与上者混用 |
| dp 坐标 | 布局、控件树 bounds | 需按 dpi 换算 |

---

## 4. 抓帧与"有画面"的硬断言

```bash
screencap -d <SF id> -p /sdcard/vd.png     # PNG，尺寸 = 该屏分辨率
```

耗时约 **100–300 ms/帧**（1280×720），做 UI 自动化足够，做 60fps 采集不够。

**别只看"亮度 > 0"**：深色页面亮度可能只有 20–30。用三个指标一起判：

| 指标 | 黑屏 | 正常画面 |
| --- | --- | --- |
| 平均亮度 | 0.0 | 160–200（含大面积浅色 UI） |
| 非黑像素占比 | 0.0% | > 90% |
| 颜色数 | 1 | 数千以上 |

`scripts/vd.sh check` 用的判据：`亮度 > 50 且 颜色数 > 100`。

```python
from PIL import Image
import numpy as np
a = np.asarray(Image.open('/sdcard/vd.png').convert('RGB'))
print(a.mean(), (a.sum(axis=2) >= 30).mean() * 100, len(np.unique(a.reshape(-1, 3), axis=0)))
```

---

## 5. 注入输入

```bash
input -d <逻辑 id> tap X Y
input -d <逻辑 id> swipe X1 Y1 X2 Y2 300
input -d <逻辑 id> keyevent KEYCODE_BACK
```

- 坐标是**目标屏的像素坐标**；
- 需要 root / shell 身份；
- 动手写批量脚本前，先做**单点验证**：点一下，量前后帧差
  （实测：有效点击帧差 **72.6**，无响应区 **0.08**）。

`scripts/ui_probe.py` 用控件树取 bounds 定位，比坐标更耐得住旋转/分辨率变化；
两者配合：**虚拟屏/浮窗负责"画面干净"，控件树负责"点得准"**。

---

## 6. 黑屏排查清单（按命中率排序）

虚拟屏建出来了、App 也在上面，但抓到的全黑：

| # | 症状 | 根因 | 处理 |
| --- | --- | --- | --- |
| 1 | `--display-id` 里根本没有 Virtual display 条目 | 外壳配置让它在物理屏上跑，**没建屏** | 关掉"前台模式"，强制走虚拟屏 |
| 2 | 屏在、App 在，全黑 | App 把画面渲染到了**物理 display**（3D/Unity 类常见） | 改走路线 A：抓物理屏 + 按窗口裁剪 |
| 3 | 只有开头几帧黑 | 目标 Surface 还没提交第一帧 | 建屏后等 1–2 s，或轮询到亮度达标 |
| 4 | 画面有但点不动 | 建屏时缺 `SUPPORTS_TOUCH` | 补 flag |
| 5 | 黑帧但 App 在动 | 抓到了**另一块同名旧屏**（没销毁） | 按 `displayName` + 建屏时间区分 |
| 6 | 进程直接崩 | 在**非 App 进程**里加载依赖 JNI/ART 的库 | 这类库只能跑在 App 进程里 |

第 2 条有实测对照（同一时刻、同台设备）：

```
物理屏窗口裁剪：1280x720，平均亮度 28.1，4096 种颜色   ← 正常
虚拟屏抓帧    ：1280x720，平均亮度 0.0，唯一颜色 (0,0,0) × 921600  ← 全黑
```

后来换了一套启动链/参数，同一个 App 在虚拟屏上抓到 **亮度 168.0、非黑 99.9%**。
说明虚拟屏本身没问题，**黑不黑取决于 App 是否真的往你给的 Surface 渲染**。

---

## 7. 安全红线

| 禁止 | 后果 |
| --- | --- |
| `wm size 1280x720` | 整台手机显示尺寸被覆盖，用户界面立刻变形 |
| `wm density N` | 全局 DPI 覆盖 |
| `settings put global enable_freeform_support 1` | 改变桌面/分屏行为 |
| `settings put global overlay_display_devices` | 额外建模拟屏，干扰 id 判定 |
| 对不确定的 display 注入输入 | **会点到用户正在用的屏幕上** |

需要 1:1 画面时，走路线 A（窗口裁剪），不要动系统分辨率。

**会注入输入的脚本必须带闸门**：没有 `ALLOW_INPUT=1` 一律拒绝执行；
逻辑 id 反查失败直接退出，不回落默认值。

---

## 8. 与本项目现有脚本的关系

| 脚本 | 负责 | 用在虚拟屏上要不要改 |
| --- | --- | --- |
| `scripts/vd.sh` | 找屏、抓帧、点击、断言有画面 | —— |
| `scripts/px_probe.py` | 截图取色、区域平均色、圆角/描边判断 | 需要把截图来源从 `screencap` 换成 `screencap -d <SF id>` |
| `scripts/ui_probe.py` | 控件树定位、点击、等待元素 | 不需要改（`uiautomator` 抓的是当前前台屏） |

视觉回归的推荐组合：

1. 用 `vd.sh capture`（或路线 A 的窗口裁剪）拿到**干净、固定分辨率**的帧；
2. 用 `px_probe.py` 的思路量像素级事实（背景色、卡片填充、描边）；
3. 用 `ui_probe.py` 做交互（点哪个控件由控件树决定，不写死坐标）。

---

## 9. 本机基准数据

| 项 | 值 |
| --- | --- |
| 设备 / 系统 | 小米 24122RKC7C / Android 17（SDK 37） |
| 物理屏 | 1440×3200，density 600，freeform 桌面缩放 0.45 |
| PAGE_SIZE | 4096 |
| 虚拟屏参数（可用） | 1280×720 @ dpi 240 |
| 虚拟屏抓帧（正常） | 亮度 **168.0**，非黑 **99.9%** |
| 物理屏窗口裁剪 | 1280×720，1:1，亮度 28.1（深色界面） |
| 抓帧耗时 | 100–300 ms/帧 |
| 单点点击帧差 | 有效 **72.6** / 无响应 **0.08** |
