# 文档索引

仓库根目录只有两份 README：`README.md` 面向用户，`module-README.md` 是发布到 LSPosed 模块仓的
首页。其余文档都在这里。

| 文件 | 内容 |
| --- | --- |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 开发指南。环境、构建、源码结构、加 Hook 与加设置项的步骤、调试与发版。**从这里开始改代码。** |
| [ARCHITECTURE.md](ARCHITECTURE.md) | 架构说明。字形宿主、状态采样、原生图标抑制、环外读数、Hook 清单、配置通道与验证方法。**多数「为什么这样写」的答案在这里。** |
| [RELEASE-NOTES-STYLE.md](RELEASE-NOTES-STYLE.md) | 发布说明撰写规则：固定三节结构、每条怎么写、不要写什么、写完自查表。写 `release-notes/` 下任何一份之前先看这个。 |

## 发布说明

`docs/release-notes/release-notes-<version>.md` 是本分支每次发版的 changelog 源文件，由
`release.ps1 -NotesFile` 读取，内容即 GitHub Release 正文。当前版本线从 **1.0.0** 开始：

| 文件 | 内容 |
| --- | --- |
| [release-notes-1.0.0.md](release-notes/release-notes-1.0.0.md) | 本分支首个版本。 |

## 素材

| 路径 | 内容 |
| --- | --- |
| `images/statusbar.png` | 状态栏实拍，三合一字形与原生图标并排的效果 |

## scripts/

| 脚本 | 用途 |
| --- | --- |
| `install.ps1` | 装 debug APK 到设备、打开设置页、跟进日志。`-InstallOnly` / `-LogOnly` 可拆开用。 |
| `release.ps1` | 发版主入口：构建 release APK、校验包内版本、推 tag、建 GitHub Release，末尾自动调 `release-module.ps1`。 |
| `release-module.ps1` | 把同一 APK 发到 LSPosed 模块仓。一般由 `release.ps1` 调用，主仓已发布时可单独补跑。 |

### 发版

```powershell
git push origin main
.\scripts\release.ps1 -Version 1.0.0 -NotesFile .\docs\release-notes\release-notes-1.0.0.md
```

`JAVA_HOME` 与 `ANDROID_HOME` 必须已设置，脚本不代设，缺了会直接报错。脚本只推 tag，**不推分支**，
所以 `git push` 要单独先做。
