# HonorDuo

[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](https://github.com/odomu/HonorDuo/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)](https://github.com/LSPosed/LSPosed)

把状态栏里的 **Wi-Fi、移动信号、电量** 合并成一个仿 iPhone「Duo」风格的三合一字形。以 LSPosed
模块的形式运行在荣耀 MagicOS 的系统界面里，宿主层与设置界面均为 Kotlin 实现。

<img src="https://cdn.jsdelivr.net/gh/odomu/HonorDuo@main/docs/images/statusbar.png" width="900" alt="状态栏实拍">

- **圆环**：电池环按电量点亮，环内有 Wi-Fi 弧、信号点、电量数字或充电闪电。
- **胶囊**：轮廓表示电量，两端开孔与内腔都能放组件。
- 上、中、下三个位置各是一份有序候选列表：前一项这一帧画不出来，由下一项顶上。
- 每个组件都能单独开关，或交还给系统；环外还能挂信号读数、网络类型、功率、温度与帧率。

## 安装

1. 安装 `HonorDuo-*.apk`。
2. LSPosed 管理器 → 启用 **HonorDuo**，作用域勾选**系统界面**（`com.android.systemui`）。
3. 重启系统界面。

包名 `com.honor.duo`，与上游 HyperDuo 可以并存安装，但**同一时间只应启用一个**。

## 要求

| 项目 | 要求 |
| --- | --- |
| 系统 | 荣耀 MagicOS |
| Android | 10 及以上（API 29+） |
| 框架 | 支持 libxposed **API 102** 的 LSPosed |
| 作用域 | 系统界面（`com.android.systemui`） |

## 链接

- 说明与截图：<https://github.com/odomu/HonorDuo>
- 问题反馈：<https://github.com/odomu/HonorDuo/issues>
- 最新版本：<https://github.com/odomu/HonorDuo/releases/latest>

## 许可

派生自 [HyperDuo](https://github.com/yixing233/HyperDuo)，以 **GPL-3.0** 发布：可自由使用、修改与
再分发，分发修改后的版本须以同一协议公开源码。不提供担保，全文见 [LICENSE](https://github.com/odomu/HonorDuo/blob/main/LICENSE)。
