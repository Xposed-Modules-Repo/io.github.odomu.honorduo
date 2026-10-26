# HonorDuo

[![版本](https://img.shields.io/badge/version-1.0.3-blue)](https://github.com/odomu/HonorDuo/releases/latest)
[![许可证](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)](https://github.com/LSPosed/LSPosed)
[![MagicOS](https://img.shields.io/badge/MagicOS-status%20bar-purple)](#要求)

将荣耀 MagicOS 状态栏右侧的 **Wi-Fi、移动信号、电池** 合并为一个仿 iPhone「Duo」风格的三合一字形模块。

全层（宿主层 Hook、图形渲染引擎、状态采样与设置界面）统一采用 Kotlin 实现，应用包名 `io.github.odomu.honorduo`。

<img src="docs/images/statusbar.png" width="100%" alt="状态栏实拍：三合一字形接管 Wi-Fi、信号与电池">

## 核心特性

- **三合一字形**：支持圆环与胶囊双形态，宽度申报即时响应，邻近图标自动平滑让位。
- **智能防烧屏**：专为 OLED 屏幕设计的 30 秒 3×3 离散网格微米级平移机制，结合边界安全钳制，保护屏幕且功耗极低。
- **灵动胶囊智能避让**：胶囊展开时基于屏幕物理坐标执行单组件碰撞检测，仅避让受遮挡组件，并智能恢复可用空间内被原生误杀的状态栏图标。
- **灵活分区与候选**：上、中、下三个区域支持自由点选组件，具备有序候选回退能力。
- **丰富可放置组件**：电量数字、Wi-Fi 弧、移动信号（支持卡一卡二拆分）、网络类型、充电闪电、功率、温度、帧率、时钟等均可独立放置或交还系统。
- **环外读数自适应堆叠**：移动信号支持移至环外，提供**圆点副卡**与**双排柱**两种样式，支持 **4 信号柱与 5 信号柱**自由切换及全维度比例自适应；功率、温度、电流、电压、帧率支持**顺序感知自适应堆叠**，按开启顺序智能拆分与两两堆叠，严格限制双行高度避免压缩字号。
- **多元时钟与硬件读数**：支持月/日、年/月/日、月日、年月日 4 种无歧义日期格式；支持电池高温阈值（30℃–60℃）与高温专属告警颜色配置。
- **全局开关完备覆盖**：模块总开关对状态栏、原生电池、图标抑制、通知中心天气与日期、时钟定制执行完备覆盖，关闭时即时无缝归还系统。
- **配置备份与独立恢复**：由专用控制器托管整份模块配置导出与导入，支持跨设备迁移并即时同步到宿主。
- **三段式卡片化界面**：「组件排布」、「功能设置」、「关于模块」三大专属 Tab，分类预览卡片直达专属配置抽屉，排版对称工整；独立关于页保留全屏动态背景。

## 安装与要求

### 要求

| 项目 | 说明 |
| :--- | :--- |
| **系统** | 荣耀 MagicOS |
| **Android** | 10 及以上（API 29+） |
| **框架** | 支持 libxposed **API 102** 的 LSPosed |
| **作用域** | 系统界面（`com.android.systemui`） |

### 安装步骤

1. 下载并安装最新版 `HonorDuo-*.apk`。
2. 打开 LSPosed 管理器，启用 **HonorDuo** 模块，并在作用域中勾选**系统界面**（`com.android.systemui`）。
3. 重启系统界面或重启手机即可生效。

## 致谢

- 感谢 [HyperDuo](https://github.com/yixing233/HyperDuo) 项目及其开源贡献者；
- 感谢 [Miuix](https://github.com/Design-Compute/Miuix) 提供的现代化界面组件支持；
- 感谢 [LSPosed](https://github.com/LSPosed/LSPosed) 与 [libxposed](https://github.com/libxposed) 框架生态。

## 许可

本项目基于 **GPL-3.0** 协议开源，详见 [LICENSE](LICENSE)。
