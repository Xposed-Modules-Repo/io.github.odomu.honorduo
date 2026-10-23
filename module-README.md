# HonorDuo

[![License](https://img.shields.io/badge/license-GPL--3.0-blue)](https://github.com/odomu/HonorDuo/blob/main/LICENSE)
[![Android](https://img.shields.io/badge/Android-10%2B-green)](https://developer.android.com)
[![LSPosed](https://img.shields.io/badge/LSPosed-API%20102-orange)](https://github.com/LSPosed/LSPosed)

将荣耀 MagicOS 状态栏右侧的 **Wi-Fi、移动信号、电池** 合并为一个仿 iPhone「Duo」风格的三合一字形。以 LSPosed 模块形式运行在系统界面（`com.android.systemui`）中，全层采用 Kotlin 实现。

<img src="https://cdn.jsdelivr.net/gh/odomu/HonorDuo@main/docs/images/statusbar.png" width="900" alt="状态栏实拍">

## 特性速览

- **三合一字形**：圆环与胶囊双形态，动态申报宽度并平滑让位。
- **智能防烧屏**：OLED 屏幕 30s 离散微移机制，结合边界钳制安全保护。
- **胶囊物理避让**：单组件物理坐标避让，智能恢复可用空间内被误杀的状态图标。
- **4柱 / 5柱信号**：支持 4 信号柱与 5 信号柱切换，具备等比自适应外形。
- **双卡独立网络类型**：高度严丝合缝撑满双卡跨度，中心对称对齐。
- **多元时钟与硬件读数**：4 种日期格式支持，电池高温告警阈值与专属颜色配置。
- **三大分类 Tab**：「组件排布」、「功能设置」、「关于模块」，预览卡片唤起抽屉。

## 安装指南

1. 安装 `HonorDuo-*.apk`。
2. 打开 LSPosed 管理器，启用 **HonorDuo** 模块，作用域勾选**系统界面**（`com.android.systemui`）。
3. 重启系统界面。

## 要求

| 项目 | 要求 |
| :--- | :--- |
| **系统** | 荣耀 MagicOS |
| **Android** | 10 及以上（API 29+） |
| **框架** | 支持 libxposed **API 102** 的 LSPosed |
| **作用域** | 系统界面（`com.android.systemui`） |

## 致谢

- 感谢 [HyperDuo](https://github.com/yixing233/HyperDuo) 项目及其开源贡献者；
- 感谢 [Miuix](https://github.com/Design-Compute/Miuix) 提供的现代化界面组件支持；
- 感谢 [LSPosed](https://github.com/LSPosed/LSPosed) 与 [libxposed](https://github.com/libxposed) 框架生态。

## 许可

基于 **GPL-3.0** 协议开源，全文见 [LICENSE](https://github.com/odomu/HonorDuo/blob/main/LICENSE)。
