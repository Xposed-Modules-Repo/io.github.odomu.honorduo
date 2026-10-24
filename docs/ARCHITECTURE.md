# HonorDuo 架构说明

面向二次开发与问题定位。用户向的安装与设置说明见 [README](../README.md)，构建与改代码的步骤
见 [DEVELOPMENT.md](DEVELOPMENT.md)。

基线是荣耀 MagicOS 的系统界面（`com.android.systemui`）。下文的类名、字段名、资源名都可以在
真机反编译的 SystemUI 中核对。

## 一、总览

| 项 | 值 |
| --- | --- |
| 包名 / applicationId / namespace | `com.honor.duo` |
| 入口类 | `com.honor.duo.HonorDuoModule`（`META-INF/xposed/java_init.list` 同步） |
| 作用域 | `com.android.systemui`（`META-INF/xposed/scope.list`） |
| 源码目录 | `app/src/main/kotlin`，全 Kotlin，无 Java 源 |
| 字形宿主 | `HwSystemIcons` 图标行里的一个普通子视图，不创建窗口 |
| 电量 / 充电 / 省电 | `HwBatteryManager` 单例；取不到时回落到宿主自己的字段 |
| 墨色 | `HwStatusBarBatteryView.mTintColor`，回落到边框的 `mDefaultColor`，再回落到按夜间模式取的黑 / 白 |
| Wi-Fi / 移动信号 / 制式 | `HnSignalUtils.setIconForView` 与 `HwSignalUtil.setIconForView` 的资源名分类 |
| 每卡读数 | `SubscriptionManager` + `TelephonyManager`，按 2 秒限流 |
| 原生图标抑制 | 统一收口在 `View.setVisibility` |
| 设置存储 | `honorduo_settings`；框架 remote preferences 为权威源，广播为第二通道 |

模块只在这个进程里安装钩子：`HonorDuoModule.onPackageReady` 先比较包名，其他进程直接返回。

## 二、字形放在哪里

状态栏右侧的行是 `HwSystemIcons`，一个水平 `LinearLayout`。模块在它的 `onFinishInflate` /
`onAttachedToWindow` 里注册这一行，再以电池的父容器（`system_icons`）作为真正的插入点：

```
HwSystemIcons
  └ system_icons (LinearLayout)
      ├ 通知图标 / 网速槽 / signalIcons
      ├ 模块插入的环外读数
      ├ GlyphView（模块的字形）
      └ battery（模块置为 GONE）
```

`register()` 通过资源名 `battery` 找到电池，取 `battery.parent` 作为容器，并建立一个 `Row`：
行本身、容器、电池、该行自己的 `TrioState`，以及后续挂上去的字形、环外信号、性能读数、帧率与
制式视图。`mountGlyph()` 把 `GlyphView` 插到电池原来的位置，再把电池设为 `GONE`，行因此自己
合拢并预留字形的宽度。

这是一个真实子视图，不是覆盖层窗口，也不是电池的 drawable 槽：

1. **宽度由模块申报**。`GlyphView.onMeasure` 以 17dp 为基准高度，按 `TrioGeometry.hostAspect()`
   给出的宽高比计算宽度；胶囊会在主体上下各加一条开孔带。旁边的图标由 `LinearLayout` 正常排布，
   自然让位。
2. **画布、bounds 与裁剪都来自平台**。模块只往这一行里放一个自己画的视图，因此没有窗口需要在下拉
   通知栏、最近任务或充电动画岛接管时让位，也不会被平台当成可能劫持触摸的覆盖层。
3. **每帧清除被限制在自身图层**。`GlyphView` 使用 `LAYER_TYPE_HARDWARE`，`onDraw` 里的
   `PorterDuff.Mode.CLEAR` 不会擦掉窗口缓冲里的模糊背景。
4. **不是触摸目标**。`isClickable` / `isFocusable` 为 false，也无障碍焦点，避免影响状态栏自己的
   下拉手势判定。

### 2.1 三个位置与组件仲裁

`KEY_RING_LAYOUT`（存储名 `ring_layout`）把上、中、下三个位置保存成 `"top|middle|bottom"` 的
有序候选列表，每个位置内部按优先级排列组件 id。每一帧由 `TrioAppearance` 决定每个位置画什么：
取第一个**这一帧真的有内容**的候选；前面的候选没有内容（没连 Wi-Fi、没在充电）时，后面的顶上。
它只做决定，不画。

`TrioZones` 负责两件与位置有关的事：

- 给出三个位置在设计空间里的包围盒，并通过与渲染相同的变换做命中测试，供设置页点击编辑；
- 把已决定的组件编码 / 解码成槽位，并在用户改位置时求出新的布局。它不重新仲裁画什么，避免
  编辑器指向的组件和实际画出的组件不一致。

胶囊的仲裁与圆环共用同一份候选列表；圆环拒绝把信号点行放进中段（那里没有开口），胶囊则把
两排信号画在上下两个开口上。

## 三、状态采样

`TrioState` 是每一行的读取器。`GlyphView.onDraw` 每帧调用 `state.refresh()`，但真正昂贵的读取
都做了限流或事件化。

### 3.1 电量、充电与省电

`HwBatteryManager` 是单例，`HwStatusBarBatteryView.onBatteryUpdate()` 读的也是它。模块通过
`getInstance()` 取同一个对象，读 `mLevel`、`mIsCharging`、`mIsPowerSave`、`mIsQuickStatus`、
`mIsSuperQuickStatus`、`mIsTurboCharging`。三条快充字段任一为真即视为快充。

单例或字段取不到时，回落到宿主电池视图自己的 `mLevel` / `mIsCharging` / `mIsPowerSave`，不会
抛异常。低电量阈值来自 `TrioAppearance.lowThreshold`。

### 3.2 墨色

`HwStatusbarContents.refreshTint()` 把当前图标色写进 `HwStatusBarBatteryView.mTintColor`。
电池被模块置为 `GONE` 后，`TintUtil.updateTintWithArea` 不再遍历它的子视图，边框的
`mDefaultColor` 会停在旧值，因此读取顺序是：

1. `mTintColor`；
2. 边框 `BatteryTintView.mDefaultColor`；
3. 夜间模式白色 / 日间模式默认前景色。

调试日志打开时会打印 `ink: <source> -> <hex>`，其中的 `source` 就是这三者之一。

### 3.3 信号电平与网络制式

这套系统上所有信号图标最终都走两个静态入口：

- `HnSignalUtils.setIconForView`（pipeline 视图）；
- `HwSignalUtil.setIconForView`（经典 cluster）。

两个入口各挂一次，把第二个参数（资源 id）交给 `TrioState.noteSignalIcon` 分类。分类按资源名：

| 资源名 | 含义 |
| --- | --- |
| `stat_sys_wifi_signal_0..4` | Wi-Fi 0..4 格 |
| `stat_sys_wifi2*_signal_0..4` | Wi-Fi 2.4 / 5 / 6 / 7 变体，同样按尾数取档 |
| `stat_sys_signal_0..4` | 移动 4 格布局 |
| `stat_sys_signal_five_0..5`（可带 `_small` / `_fully`） | 移动 5 格布局，按比例换算到 4 档，满格仍是满格 |
| `stat_sys_data_connected_*` / `_fully_connected_*` | 网络制式标（5G / 5GA / 4G+ / 3G / 2G / H+ / E / G / 1X 等） |
| `stat_sys_signal_null` / `_flightmode` | 明确无信号，清空信号点 |
| 名字含 `activity_in` / `activity_out` / `activity_inout` | 数据活动方向 |

分类结果按资源 id 缓存，热路径上只查表。制式标签由 `typeOfName()` 从同一套资源名解析，因此
`5GA`、`4.5G`、`5G+` 这类框架 API 没有的写法也能显示。

Wi-Fi 的**有无**不依赖图标：`WifiPresence` 监听 `WifiManager` 的开关、网络与 RSSI 广播，并向
`ConnectivityManager` 查询当前网络；广播之间还有一个低频看门狗，用来补上 MagicOS 偶尔丢掉的
投递。图标被隐藏后可能不再重新绑定，所以连接后的电平也会从 `WifiManager.connectionInfo` 的 RSSI
重新计算。

### 3.4 每卡电平与制式

图标本身不区分 SIM（`setIconForView` 的参数里没有 subId），所以卡一 / 卡二的读数必须直接问
telephony：

- `SubscriptionManager.getActiveSubscriptionInfoList()` 用 `simSlotIndex` 定位槽位；
- `TelephonyManager.createForSubscriptionId(subId).getSignalStrength().getLevel()` 取 0..4；
- `getDefaultDataSubscriptionId()` 决定哪张卡是上网卡，聚合读数从它来；
- 每 2 秒最多轮询一次，只在真的需要每卡读数（双卡开关、第二排点或环外堆叠）时才触发。

制式优先读 ROM 自己的每槽表 `HwSignalUtil.sCurrentNetWorkTypes`（`HwMobileSignalController` 从
`TelephonyDisplayInfo` 写入，含 NSA override），读不到再退回 `TelephonyManager.dataNetworkType`。
图标带来的制式只能细化、不能推翻射频结论：当图标与射频家族不一致时，以射频为准。

`READ_PHONE_STATE` 被撤销或射频不可用时，该槽位保持 `-1`，界面回落到单排读数。

### 3.5 数据活动

`TelephonyCallback.DataActivityListener` 是精确来源，但在部分 ROM 上只投递给默认订阅，换卡或
权限被拒后会静默停止。`TrioState` 因此另有一个 1 秒一次的 `TelephonyManager.getDataActivity()`
轮询作为兜底；两者都只在值变化时触发重绘。

### 3.6 充电功率、温度与帧率

- `BatteryPresence` 监听粘性 `ACTION_BATTERY_CHANGED`，读温度与电压，再用
  `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` 读电流。功率取电流幅值 × 电压，即电池侧真实
  功率；电流未知时是 `POWER_UNKNOWN`，不是 0。
- `FpsTicker` 先读电源 / DRM 的 `measured_fps` sysfs 节点，读不到再用 `Choreographer` 统计实际
  渲染帧数，最后回落到屏幕刷新率。只有 `perfFps` 打开时才注册帧回调。
- `ClockTicker` 对齐分钟边界（开启秒时对齐秒边界），只在时间真的被画出来时才格式化。

## 四、抑制原生图标

原生 Wi-Fi、移动、电池与时钟都会被系统反复重新显示：

| 视图 | 谁会把它设成 VISIBLE |
| --- | --- |
| `wifi_combo` | Wi-Fi binder / `HnWifiViewBinder` 的连接状态变化 |
| `mobile_combo` | `HwSignalClusterView.applyHonor()`，所有 cluster 回调的终点 |
| `battery` | `HwStatusBarBatteryView.onBatteryUpdate()` 结尾 |
| `clock` | `HwStatusbarContents` 的布局与动画路径 |

逐个追调用点会漏，也会和系统自己的重排赛跑，所以抑制收口在 `View.setVisibility`：

1. 先让系统自己的写生效，再把这次写记进 `REQUESTED`（模块自己的强制写有
   `writingSuppressed` 标记，不会被误记）。
2. `enforceSuppression()` 在 id 已缓存后用几个 `int` 比较快速返回；命中的 id 只有
   `wifi_combo`、`mobile_combo`、`battery`、`clock`。
3. 只有视图确实位于本模块接管的容器里才继续。`insideOwnedContainer()` 会向上找父链，确认它属于
   已注册的图标行或状态栏 contents；呼叫提示、投屏提示等复用同 id 的布局因此不会被误伤。
4. 最后问唯一的 `owned(id, appearance)` 规则：
   - `wifi_combo` → `ownsWifi()`；
   - `mobile_combo` → `foldsMobile()`；
   - `battery` → 只要字形启用就归模块；
   - `clock` → `hidesStatusClock()`，或模块自己的状态栏时钟已经挂上。
5. 归模块的视图被强制设为 `GONE` 并记进 `HIDDEN` 弱引用表；规则不再拥有它时，`release()` 把它
   恢复到系统最后一次请求的可见性。

`setVisibility` 的系统写是事件驱动的，但系统不会对已经 `GONE` 的视图重复写 `GONE`，也不会对已经
可见的视图重复写 `VISIBLE`，所以还需要一次主动遍历：`applyIconVisibility()` 在设置变更、行挂载
与 contents 挂载时，从行和 contents 的根向下 `settle()`，把每个命中 id 的视图放到当前规则要求的
状态。行销毁后遗留的隐藏视图也会在这张弱引用表里被交还。

## 五、环外读数

环外读数是 `system_icons` 这个 `LinearLayout` 的普通子视图，和字形一样由行测量、由行预留宽度：

- `OutSignalView`：移动信号。`TrioRenderer.drawOutSignal()` 画四根递增圆头柱与副卡圆点；副卡读数
  存在时切换为两排，或按 `KEY_STACK_STYLE` 改成双排柱。每张卡的电平先由 `outSignalLevels()` 从
  telephony 与数据卡状态算出，再用 `nativeSlotLevels()` 从原生信号组每张卡的图标名覆盖——后者就是
  状态栏此刻画出的电平，不会比用户看到的更旧；原生组读不到时才保留 telephony 的结果。数据活动
  箭头由 `TrioState.sDataActivity` 驱动。
- `OutTypeLabel`：独立的制式标签，可放在信号读数外侧，也可由 `drawOutSignal` 画在信号柱上方
  （`STACK_LABEL_BESIDE`），或单独成一项（`STACK_LABEL_ALONE`）。
- `PerfLabel`：功率与温度。两行文本在同一块里，按可用高度自动缩小字号；温度未知时不显示，也可以
  设置为只在充电时显示。
- 帧率有两条路径：读数条还有第二行时并进去；两行都被占用（或根本没有读数条）时，另起一个
  `PerfLabel` 视图放在旁边，避免第三行把三样读数一起压小。

顺序由 `Prefs.KEY_OUT_ORDER`（存储名 `out_ring_order`）决定，值是三个槽位 id 的排列：
`OUT_SLOT_SIGNAL`、`OUT_SLOT_READOUTS`、`OUT_SLOT_TYPE`。列表是**离电池最近的在最前**；每次
重排都把视图重新插到字形之前，所以改变顺序会立即生效。视图首次挂载时会清空
`Row.outOrderApplied`，让下一轮重新排列，不会沿用视图创建时的旧位置。

尺寸按 dp 计算，不跟随行高，因此下拉通知栏让行变高时读数不会跟着放大；间距由各自的
`LinearLayout.LayoutParams` 边距决定，不会盖到旁边的图标。RTL 下由 `LinearLayout` 自动镜像。

## 六、Hook 清单

| 目标类 | 方法 | id | 作用 |
| --- | --- | --- | --- |
| `HwSystemIcons` | `onFinishInflate` | `honorduo-row-inflate` | 注册图标行 |
| `HwSystemIcons` | `onLayout` | `honorduo-row-layout` | 布局后请求一次同步（系统重新显示电池后再次隐藏） |
| `HwSystemIcons` | `onAttachedToWindow` | `honorduo-row-attach` | 注册 / 重新挂载 |
| `HwSystemIcons` | `onDetachedFromWindow` | `honorduo-row-detach` | 注销该行 |
| `HwStatusBarBatteryView` | `onBatteryUpdate` | `honorduo-battery-update` | 隐藏电池、同步该行、重绘 |
| `HwStatusBarBatteryView` | `onFinishInflate` | `honorduo-battery-inflate` | 取得首个真实 Context，挂上采样器与重载广播 |
| `View` | `setVisibility` | `honorduo-visibility` | 统一抑制 Wi-Fi / 移动 / 电池 / 时钟 |
| `HnSignalUtils` | `setIconForView`（2 参） | `honorduo-signal-new` | 采样 Wi-Fi / 移动电平、制式、数据活动 |
| `HwSignalUtil` | `setIconForView`（3 参） | `honorduo-signal-classic` | 同上，经典 cluster |
| `SignalUnitNormalView` | `updateView`（2 参） | `honorduo-unit-type` | 模块自绘制式时隐藏每卡原生制式标 |
| `HwStatusbarContents` | `onAttachedToWindow` | `honorduo-contents-attach` | 记录状态栏 contents，供时钟与图标遍历使用 |
| `HwStatusBarClock` | `onAttachedToWindow` | `honorduo-clock-face` | 采用系统字体、记录原生时钟、挂载模块时钟 |
| `HwStatusbarContents` | `refreshTint` | `honorduo-tint` | 深浅色切换后立即重绘 |

启动时打印 `HonorDuo installed, hooks=N`。某个方法在别的固件上改名时只打印
`skip <id>: method not found`，其余钩子照常工作。

## 七、配置通道

设置页与钩子进程通过两条路径同步：

1. **remote preferences（权威源）**。设置页把自己的 `SharedPreferences`（文件名
   `honorduo_settings`）里的值逐项写进框架的 remote group；`TrioConfig.install()` 在钩子进程里
   `getRemotePreferences(Prefs.NAME)`，注册单键变更监听。框架每变更一个键就回调一次，
   `applyKeyFrom()` 把该键的新值合进当前快照，再整份发布。
2. **重载广播（第二通道）**。设置页每次写入后发送 `com.honor.duo.action.RELOAD`，目标包固定为
   `com.android.systemui`，extras 是完整快照。`TrioConfig` 在电池 inflate 时用 application
   context 注册接收器；广播必须带 `KEY_ENABLED` 才会被接受，`fromBundle(extras, previous)` 以当前
   快照为底，因此发送方省略的键保持原值。

`TrioConfig` 发布时先替换 `TrioAppearance` 缓存、再替换快照，读到的快照不会配到旧规则。
监听器用强引用保存，因为唯一注册者是 `TrioHooks.install()` 里的 lambda，弱引用会在首次 GC 后
把重绘通知一起收走。

冷启动时 remote 表可能还是默认值，而框架不会为「没有写入」发回调，所以 `TrioConfig` 在安装后
5 秒和 20 秒各补读一次，只有内容真的变化才通知。

`TrioAppearance` 是绘制与抑制共同的唯一规则源：渲染器问它这一帧画什么，`TrioHooks` 问它谁该被
藏，设置界面问它哪些行有意义。它不可变、纯 Kotlin、不依赖 Android 类型，两个进程都能构建。

## 八、验证

构建：

```powershell
.\gradlew :app:assembleDebug
```

产物是 `app/build/outputs/apk/debug/app-debug.apk`。安装与日志：

```powershell
.\scripts\install.ps1
```

或手动：

```powershell
adb logcat -v time -s LSPosedFramework:* | Select-String HonorDuo
```

真机判据：

1. 状态栏右侧只显示三合一字形，原生 Wi-Fi / 信号 / 电池按开关状态正确隐藏或交还；
2. 下拉通知栏后行变高，字形与环外读数仍保持图标高度，不被拉长；
3. 切换圆环 / 胶囊时宽度立刻变化，旁边的图标跟着让位；
4. 充电、快充、低电量、省电四种状态的颜色与闪电正确；
5. 双卡时卡一 / 卡二两排点与系统信号格档位一致（五格布局已换算）；
6. 调整环外读数顺序后立即生效，读数之间不重叠、不盖住旁边的图标；
7. 时间放进字形后状态栏原时钟消失；开启秒 / 日期 / 星期后模块时钟正确出现并随分钟或秒刷新；
8. 深浅色背景切换后，字形墨色在 `refreshTint` 后立即跟随，不需要等电池或信号事件。

日志判据：

- `HonorDuo installed, hooks=N`：装上的钩子数；
- `skip <id>: method not found`：当前固件缺少该方法，对应功能自动跳过；
- 打开调试日志后另有 `ink: …`、`config changed, rows=…`、`remote pref changed: …` 等行。
