# 开发指南

面向要改这个模块的人。架构与「为什么这样写」在 [ARCHITECTURE.md](ARCHITECTURE.md)，这里只讲
环境、构建、源码结构、加东西的步骤和验证命令。

## 环境

| 项 | 要求 |
| --- | --- |
| JDK | 21（`sourceCompatibility`、`targetCompatibility` 与 `jvmTarget` 都是 21） |
| Gradle | 仓库自带的 wrapper，版本见 `gradle/wrapper/gradle-wrapper.properties` |
| Android SDK | `compileSdk = 37`，`targetSdk = 37`，`minSdk = 29` |
| 构建插件 | AGP 9.3.0，Kotlin / Compose / Serialization 插件 2.3.21 |
| 设备 | 已启用 USB 调试、LSPosed 已激活的荣耀机 |

`local.properties` 里的 `sdk.dir` 指向本机 SDK，它不入库。`settings.gradle.kts` 把
`.tools/maven` 放在 `google()` 之前，仓库内已带的 `io.github.libxposed:*:102.0.0` 因此不需要联网
解析。

## 构建

```powershell
.\gradlew :app:assembleDebug
.\gradlew :app:assembleRelease
```

产物在：

- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release.apk`

debug 与 release 都使用 `.tools/debug.keystore` 签名，所以一个可以覆盖安装另一个。包名是
`io.github.odomu.honorduo`，与上游 HyperDuo 是两个不同的应用。

## 源码结构

源码根是 `app/src/main/kotlin/io/github/odomu/honorduo/`，全部为 Kotlin：

```
app/src/main/kotlin/io/github/odomu/honorduo/
├── HonorDuoModule.kt         框架入口；onPackageReady 只对 com.android.systemui 安装钩子
├── HonorDuoApp.kt            设置进程；连接 XposedService，断线重连后重放设置
├── hook/
│   ├── core/                 核心 Hook 与底层注入入口
│   │   ├── StatusHooks.kt        全部注入点总入口、行注册、原生图标抑制、环外视图挂载
│   │   ├── SystemBarHook.kt      状态栏着色与深浅色模式切换广播通知
│   │   ├── StatusBarIconHook.kt  原生状态栏图标查找与替换
│   │   ├── SignalIconHook.kt     移动数据活动监听与原生网络类型替换
│   │   ├── ShadeHook.kt          下拉通知栏原生日期文本与时间样式接管
│   │   └── Refl.kt               反射查找与读写的薄封装，静默回退
│   ├── statusbar/            状态栏图标行容器管理与时钟组件
│   │   ├── StatusBarRowManager.kt 图标行的注册 / 注销与环外视图排布
│   │   ├── StatusBarIconManager.kt 状态栏图标资源缓存与位图复用
│   │   ├── BarClockManager.kt     状态栏双行时钟的挂载与刷新
│   │   └── ClockTicker.kt         时间文本对齐跳动的全局广播与刷新分发
│   └── tracker/              硬件读数采集与系统状态追踪
│       ├── BatteryPresence.kt     原生电池与电量文本的原生可见性追踪
│       ├── FpsTicker.kt           帧率回调、sysfs 采样与节流
│       ├── WeatherManager.kt      天气数据抓取与缓存
│       ├── WifiPresence.kt        Wi-Fi 状态、图标与连接状态追踪
│       ├── BarVisibility.kt       状态栏可见性判定（全屏 / 锁屏时暂停刷新）
│       └── ScreenStateTracker.kt  屏幕亮灭监听
├── model/                    数据与设置快照模型
│   ├── Prefs.kt              键名、默认值、取值范围、ring_layout 编解码
│   ├── ModuleSettings.kt     不可变设置快照；从 prefs / Bundle 读取，序列化回 Bundle
│   ├── StatusAppearance.kt   纯逻辑渲染快照，裁决「这一帧画什么、谁该被藏」
│   ├── ModuleConfig.kt       钩子侧设置快照；remote preferences 监听与重载广播
│   └── StatusState.kt        电量 / 墨色 / Wi-Fi / 移动信号 / 制式 / 数据活动的采样与缓存
└── ui/                       设置界面与图形渲染（Compose + Miuix）
    ├── MainActivity.kt       Activity 入口
    ├── component/            可复用基础 UI 组件
    │   ├── AmbientMeshBackground.kt AGSL 动态渐变背景
    │   ├── GlassDropdown.kt         磨砂玻璃下拉选择器
    │   ├── NavigationDrag.kt        底部导航拖拽手势
    │   └── PreferenceControls.kt    偏好设置基础控件与列表项
    ├── control/              功能与组件控制面板
    │   ├── ContentControls.kt       组件开关与位置选择
    │   └── PartControls.kt          单个组件的尺寸 / 字重 / 颜色滑杆
    ├── dialog/               交互弹窗与抽屉
    │   ├── ColorDialog.kt           颜色选择弹窗
    │   ├── PartSettingsSheet.kt     组件独立参数配置抽屉
    │   ├── ReleaseNotesDialog.kt    更新日志弹窗
    │   ├── RestartDialog.kt         SystemUI 重启确认弹窗
    │   └── ZonePickerDialog.kt      分区组件选择弹窗
    ├── drawing/              字形排布与分区列表
    │   ├── DrawingOutsideSection.kt 环外读数排布设置
    │   ├── DrawingSceneSelector.kt  预览场景切换器
    │   └── PriorityListSection.kt   优先级顺序列表排布
    ├── manager/              业务控制器与服务
    │   ├── ConfigBackupManager.kt   配置导出与恢复（BackupManager 独立生命周期管理）
    │   ├── FontInstaller.kt         自定义字体文件安装与管理
    │   ├── RestartController.kt     通过 root shell 重启 com.android.systemui
    │   ├── SettingsRepository.kt    读写本地 prefs、推送框架、发送重载广播
    │   └── UpdateController.kt      应用内检查更新、GitHub Release 解析与下载
    ├── render/               矢量几何与图形渲染引擎
    │   ├── IconGeometry.kt          120×120 设计空间矢量几何常量
    │   ├── IconRenderer.kt          圆环 / 矩形 / 胶囊绘制及环外读数渲染
    │   ├── IconZones.kt             三个可点区域与组件仲裁
    │   └── WeatherIconDrawer.kt     天气图标矢量绘制
    ├── screen/               顶层独立页面
    │   ├── AboutPage.kt             独立关于页（全屏动态背景 + 品牌字形）
    │   ├── DrawingScreen.kt         字形放大预览、点位编辑、组件库
    │   ├── LogViewerPage.kt         日志页（筛选、清空、文件分享）
    │   ├── SettingsNavigation.kt    底部玻璃拟态导航栏
    │   ├── SettingsScreen.kt        主设置页面、顶栏与整体容器
    │   └── SettingsTabs.kt          各 Tab 页面内容集合
    └── view/                 原生自定义 View 容器
        ├── BarClockView.kt          状态栏双行时钟自定义 View
        ├── GlyphView.kt             状态栏图标行里的字形宿主视图
        ├── OutSignalView.kt         环外移动信号双排柱 / 圆点 View
        ├── OutTypeLabel.kt          环外独立网络制式文本 View
        ├── PerfLabel.kt             环外性能读数（功率、温度、电流、帧率）自适应 View
        └── StatusPreviewView.kt     设置界面的实时预览，复用 IconRenderer
```

`StatusHooks.kt` 里的 `Row` 表示一条状态栏图标行，保存行本身、真正装图标的容器、电池、该行的
`StatusState`，以及模块挂上去的字形、环外信号、性能读数、帧率与制式视图。

## 加一个 Hook

1. 在 `StatusHooks.install()` 里加一行 `hooked += hookXxx(module, cl)`，让它和其他注入点一起安装。
2. 用 `Refl.cls(...)` / `Refl.method(...)` / `Refl.resId(...)` 解析目标；底层基于 `HiddenApiBypass`
   与并发字典缓存，避免异常构造与反射拦截损耗。用 `hook(module, method, "honorduo-<名字>", hooker)`
   安装。`hook()` 在方法为 `null` 或框架拒绝时打印 `skip honorduo-<名字>: ...` 并返回 0，不会影响其他钩子。
3. `id` 一律以 `honorduo-` 开头，方便在框架日志里筛。
4. 钩子回调可能在任意线程触发。要改视图树时回到主线程：`row.row.post { ... }`，或先判断
   `Looper.myLooper() == Looper.getMainLooper()`。布局回调里不要直接增删视图。
5. 新行第一次出现时走 `register(row)`；行销毁时走 `drop(row)`。只在已经注册的行里操作，避免
   误改呼叫提示、投屏提示等复用同样 id 的布局。

## 加一个设置项

一条设置要走完下面五处，缺一处就会出现「能存不生效」或「能读没有 UI」：

1. **`Prefs.kt`**：加 `KEY_*` 与 `DEF_*`；有范围就再加 `MIN_*` / `MAX_*`。如果要按圆环 / 胶囊
   分别保存，把键加进 `STYLED_KEYS`，其余读取统一走 `styledKey(base, style)`。
2. **`ModuleSettings.kt`**：加字段，并接到 `defaults()`、`from(SharedPreferences)`、
   `fromBundle(Bundle)`、`toBundle()`、`copy()`、`applyKeyFrom(src, key)` 与 `equals()`。
   `equals()` 漏字段会让 Compose 认为快照没变、界面和预览都不刷新。
3. **`StatusAppearance.kt`**：把设置解析成绘制与抑制都能读的字段。渲染器和 `StatusHooks` 只读这个
   快照，不直接读 `ModuleSettings`。
4. **`ui/control/ContentControls.kt`** 或 **`ui/control/PartControls.kt`**：加开关、位置选择或滑杆；标签写进
   `res/values/strings.xml` 与 `res/values-en/strings.xml`。
5. **`ui/manager/SettingsRepository.kt`**：加 `setXxx`，先写应用自己的 `SharedPreferences`，再通过
   `io.github.odomu.honorduo.action.RELOAD` 广播。别忘了把新键补进 `syncAllToFramework()`，否则服务晚绑定时
   这次写入不会被重放。

改完设置后，钩子侧由 `ModuleConfig` 的单键监听或整包广播更新快照，再通知 `StatusHooks` 重绘。
`ModuleConfig` 还会在安装后 5 秒与 20 秒各补读一次，用来兜住冷启动时 remote preferences 尚未就绪的
情况。

## 改绘制

- 一切以 `IconGeometry` 的 120×120 设计空间为单位，`IconRenderer` 只在该空间里画。
- 改动墨迹盒相关的常量后，`IconGeometry.checkInkBox()` 会在类加载时校验；它抛异常说明字面量漂移了。
- 圆环、矩形、胶囊共用 `drawType` / `fitSize` 等底层绘制，改这些会同时影响三种布局，先确认
  是有意的。
- 每帧都在绘制路径上，避免分配：`Paint`、`RectF`、`Path` 都是静态复用对象。
- 设置界面的预览必须继续调用 `IconRenderer`，不要复制一份几何；预览与状态栏画出不同结果就是
  从这里开始的。

## 调试

模块日志走框架，启动时会打印 `HonorDuo installed, hooks=N`：

```powershell
adb logcat -v time -s LSPosedFramework:* | Select-String HonorDuo
```

也可以直接读 LSPosed 的日志目录：

```powershell
adb shell su 0 ls -t /data/adb/lspd/log/
adb shell su 0 grep -h "HonorDuo" /data/adb/lspd/log/modules_*.log
```

打开「调试日志」后还会看到：

- `ink: <source> -> <hex>`：这一帧的墨色取自哪里（`mTintColor` / `border` / `fallback`）；
- `config changed, rows=N enabled=… style=…`：设置变更被接收；
- `remote pref changed: <key> -> …`：框架推送了单个键；
- `reload broadcast: enabled=…`：设置页的整包广播到达。

某个功能没生效时先搜 `skip <id>: method not found`，它表示当前固件没有这个方法，对应功能被
主动跳过，而不是崩溃。

重启系统界面：

```powershell
adb shell "su -c 'killall com.android.systemui'"
```

或者用 `scripts/install.ps1`：它会装 debug APK、打开设置页，并在确认后清日志、重启系统界面、
跟进日志。只要其中一步时用 `-InstallOnly` 或 `-LogOnly`。设置页顶栏右侧也有重启按钮。

## 发版

版本号规则是 `major*10000 + minor*100 + patch`，由 `scripts/release.ps1` 计算并注入
`versionName` / `versionCode`：

```powershell
.\scripts\release.ps1 -Version 1.0.0 -NotesFile .\docs\release-notes\release-notes-1.0.0.md
```

脚本会：

1. 构建 `:app:assembleRelease`，把产物复制到 `dist/HonorDuo-<version>.apk`；
2. 用 `aapt2 dump badging` 核对 APK 内的版本号与传入值一致；
3. 推 tag 并创建 / 覆盖 GitHub Release；
4. 默认清理主仓的旧 Release（tag 保留），可用 `-KeepOldReleases` 关闭；
5. 调用 `scripts/release-module.ps1`，把同一 APK 与同一份说明发到 LSPosed 模块仓，并把
   `module-README.md` 同步为模块仓首页。

`JAVA_HOME` 与 `ANDROID_HOME` 必须已设置，缺了脚本会直接报错。脚本**只推 tag，不推分支**：

```powershell
git push origin main
.\scripts\release.ps1 -Version 1.0.0 -NotesFile .\docs\release-notes\release-notes-1.0.0.md
```

只想验证构建与版本注入、不创建 Release 时加 `-DryRun`；只发主仓时加 `-SkipModuleRepo`。

## 几个容易踩的点

- **电池被置为 GONE 后墨色会停住**：`TintUtil.updateTintWithArea` 不再遍历被隐藏电池的子视图，
  所以墨色优先读 `HwStatusBarBatteryView.mTintColor`（`refreshTint()` 会无条件写入），再回落到
  边框的 `mDefaultColor`。
- **每帧 CLEAR 要关在自己的图层里**：`GlyphView` 用 `LAYER_TYPE_HARDWARE` 把清除限制在自身，
  否则会擦掉下拉通知栏的模糊背景。
- **`const val` 会被内联**：字节码里查不到引用，判断某个常量「还有没有用到」只能看源码。
- **改包名就是换应用**：`applicationId` 一改，旧版无法覆盖升级，LSPosed 的作用域与设置也不会
  自动迁移。
- **不要手改设备上的 prefs 文件**：缺键或编码错误会让模块读到半套状态。改设置只走应用界面或
  重载广播。
