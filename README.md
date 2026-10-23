# HonorDuo

[![版本](https://img.shields.io/badge/version-1.0.0-blue)](https://github.com/odomu/HonorDuo/releases/latest)
[![许可证](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)](https://github.com/LSPosed/LSPosed)
[![MagicOS](https://img.shields.io/badge/MagicOS-status%20bar-purple)](#要求)

把状态栏里的 **Wi-Fi、移动信号、电量** 合并成一个仿 iPhone「Duo」风格的三合一字形。

HonorDuo 是 [HyperDuo](https://github.com/yixing233/HyperDuo) 的荣耀移植分支，以 LSPosed 模块的
形式运行在 MagicOS 的系统界面里（`com.android.systemui`）。宿主层与设置界面已全部用 Kotlin 重写，
包名 `com.honor.duo`，版本线从 **1.0.0** 开始。

开启后，状态栏右侧原本分开的三个图标变成一个整体字形，两种样式二选一：

- **圆环**：外圈是电池环，缺口在下方，按电量点亮；环内是 Wi-Fi 弧、信号点、电量数字或闪电。
- **胶囊**：轮廓本身表示电量，两端开孔与内腔都能放组件，内腔可并排放置两样东西。

两种样式都把字形画成状态栏图标行里的普通子视图：宽度自行申报，旁边的图标按行规则让位。

<img src="docs/images/statusbar.png" width="100%" alt="状态栏实拍：三合一字形接管 Wi-Fi、信号与电池">

## 与 HyperDuo 的差异

本模块与上游是**两个不同的应用**，可以同时安装，但同一时间只应启用一个——两者都会改写同一块
状态栏；包名不同也意味着不能互相覆盖升级，设置与作用域不会互带。

| 维度 | HonorDuo 1.0.0 | HyperDuo 1.7.x |
| --- | --- | --- |
| 实现 | 宿主层、渲染、状态采样、设置界面统一 Kotlin | Java 宿主层 + Kotlin 设置界面 |
| 环外读数 | 圆点副卡 / 双排柱两种堆叠样式，制式双卡各一条 | 单一堆叠样式，制式单条 |
| 胶囊内边距 | 独立滑块（0–300%），只影响内容、不改宽度 | 内容固定贴内腔边缘 |
| 充电动画 | 呼吸 / 流光 / 电火花，速度可调 | 只有闪电本身 |
| 时钟 | 秒、年月日、星期、农历，附颜色与行距 | 仅字号与字重 |
| 帧率读数 | 有，可进环内或环外读数 | 无 |

## 功能

**字形与位置**

- 圆环与胶囊两种样式，切换后宽度立即变化，旁边的图标跟着让位。
- 上、中、下三个位置各是一份**有序候选列表**：排在前面的这一帧画不出来（没连 Wi-Fi、没在充电），
  由下一个顶上，那一片不会空着。
- 每样组件都能单独换成别的，或者**交还系统**：关掉 Wi-Fi 弧，原生图标回到原位置；关掉信号点，
  原生信号格回来。开关是「用三合一的画法」与「用系统原本的画法」之间的切换。

**可放置的组件**

| 组件 | 位置 |
| --- | --- |
| 电量数字（可带百分号） | 环内 / 胶囊内 |
| Wi-Fi 弧 | 环内 / 胶囊 |
| 移动信号点（可拆卡一、卡二两排） | 环内 / 胶囊 |
| 网络类型 | 环内 / 环外标签 |
| 充电闪电 | 环内 / 胶囊 |
| 充电功率、运行功率、电池温度、帧率 | 环内 / 环外读数 |
| 时间 | 环内 / 胶囊 / 状态栏时钟位 |

**环外读数**

移动信号可以移到电池旁边，两种堆叠样式：**圆点副卡**（主卡信号柱 + 副卡圆点行）与**双排柱**
（两卡各一排）。网络类型可以贴在信号上方，也可以单独显示——单独显示时双卡各一条、对齐成列。
充电功率、温度与帧率也能挂在环外，先后顺序可调。

**行为开关**

连上 Wi-Fi 时网络类型可自动让位；充电时环心可只显示电量百分比；温度可设成只在充电时出现；
Wi-Fi 弧有独立尺寸滑杆（60–160%）；胶囊可调长度、两格位置与内边距（内边距只影响内容，不改宽度）；
桌面图标可单独隐藏，LSPosed 里的设置页不受影响。

**时钟**

时间放进字形后原生时钟让位。模块也能接管状态栏时钟位，显示秒、年月日、星期与农历，并按状态栏
高度排成两行；时间没被放置时，系统时钟保持原样。

**设置界面**

只有两页。首页把图标放大画出来，点哪个部位就改哪个部位（开关、位置、尺寸、颜色）；关于页是激活
状态、版本、检查更新与重启按钮。

## 安装

1. 安装 `HonorDuo-*.apk`。
2. LSPosed 管理器 → 模块 → 启用 **HonorDuo**，作用域勾选 **系统界面（`com.android.systemui`）**。
3. 重启系统界面：LSPosed 里点「重启作用域」、重启手机，或点设置界面顶栏右侧的重启按钮（需 root）。

```sh
adb shell "su -c 'killall com.android.systemui'"
```

## 要求

| 项目 | 要求 |
| --- | --- |
| 系统 | 荣耀 MagicOS |
| Android | 10 及以上（API 29+） |
| 框架 | 支持 libxposed **API 102** 的 LSPosed |
| 作用域 | 系统界面（`com.android.systemui`） |

模块面向 MagicOS 的状态栏实现。别的固件里某个方法改名时，对应注入点会跳过并写日志，其余功能
照常工作，不会让系统界面崩溃。

## 常见问题

**改完设置要重启系统界面吗？** 不需要。设置经框架的远程配置通道与一条完整快照广播送到系统界面，
立即重绘；只有刚安装或状态界面异常时才需要重启。

**状态栏完全没变化？** 依次确认：模块已启用 → 作用域勾了「系统界面」→ 已重启过系统界面；都满足
仍无效时，检查是否有别的状态栏模块在改同一区域。

**显示「已激活」但状态栏是原样？** 连接正常只说明模块进程在跑，通常是没有勾选
`com.android.systemui` 作用域，模块没被注入。

**检查更新失败？** GitHub 在部分网络会被拦截。模块先走镜像、失败再回原地址；都不通时点「查看
仓库」手动查看。

## 排查

打开设置里的「调试日志」，然后：

```powershell
adb logcat -v time -s LSPosedFramework:* | Select-String HonorDuo
```

框架日志的 tag 是 **`LSPosedFramework`**。启动会打印 `HonorDuo installed, hooks=N`，注入点不匹配
时打印 `skip <id>: method not found`。

## 开发

构建、源码结构、加 Hook 与加设置项的步骤见 [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md)；字形宿主、
状态采样、原生图标抑制与配置通道的原理见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 支持

- 源码：<https://github.com/odomu/HonorDuo>
- 反馈：<https://github.com/odomu/HonorDuo/issues>
- 版本：<https://github.com/odomu/HonorDuo/releases/latest>

## 致谢与许可

派生自 [HyperDuo](https://github.com/yixing233/HyperDuo)，字形设计与交互来自原作者
[yixing233](https://github.com/yixing233)。设置界面基于 Compose + [Miuix](https://github.com/miuix-kotlin-multiplatform/miuix)，
毛玻璃与方圆形使用 kyant0 的 backdrop、shapes，线性图标取自 Lucide 2.2.1（ISC）。

以 **GPL-3.0** 发布，全文见 [LICENSE](LICENSE)：可自由使用、修改、再分发（含商业用途），但分发
修改后的版本须以同一协议公开源码。不提供担保。
