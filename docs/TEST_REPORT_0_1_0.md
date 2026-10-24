# 实测报告

日期：2026-10-24，Asia/Shanghai。所有记录样例均为明确标记的虚构数据。

## 结论与产物

- `assembleDebug`：PASS，实际生成 APK。
- Pure JVM：34/34 PASS，0 skipped。
- 荣耀90 instrumentation：24/24 PASS，0 skipped，实际运行34.233秒。
- 真实AlarmManager探测：PASS，系统实际调用ReminderReceiver并发出通知，本次观测延迟4694ms。
- `lintDebug`：PASS，0 errors、35 warnings、2 hints。保留了依赖版本提示、target35和旧系统忽略新组件属性等警告，未以删除检查或测试来隐藏失败。
- 真实荣耀 Launcher：两个实例、2×2与4×2布局、应用进程结束后的今日/历史入口、长中文目标显示已实测。
- 未运行 Robolectric、Pixel/模拟器、远程 GitHub CI 或正式签名 Release；未公开仓库或发布 APK。

Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，20,886,249 bytes。
SHA-256：`501494abc70793954dee9a73578f1faa046a4c38a3b708c04a59437bc90555d1`。
包名 `io.github.daily3minutes`，versionCode1，versionName0.1.0。
APK v2签名验证通过；Debug签名只用于本次试用，不能视为长期升级渠道。

## 环境与证据分层

荣耀90 REA-AN00，Android15/API35，MagicOS9.0.0，1200×2664，字体1.0。
Launcher：`com.hihonor.android.launcher/.unihome.UniHomeLauncher`，16.0.20.221。
横屏实测窗口2664×1200。JDK17/Gradle8.13/SDK36工具见 [环境报告](ENVIRONMENT.md)。

| 层次 | 实际执行 |
|---|---|
| JVM | BackupCodecTest15、DailyRulesTest5、ReminderTimesTest6、EditorConcurrencyTest8 |
| 荣耀设备数据与页面 | Repository8、数据库关闭重开1、Compose页面及原生无障碍3、入口4、隐私默认值2、提醒与恢复3 |
| 荣耀设备原生组件宿主 | 3项；实际AppWidgetHost/RemoteViews，紧凑、宽版、数千中文字符、两按钮、增改删及导入刷新 |
| 荣耀 Launcher | 额外手动UI/ADB操作；两个实例同时显示，随后移除额外实例，将保留实例由2×2调整为4×2 |
| 模拟器与Pixel Launcher | NOT TESTED |

Instrumentation使用测试专用 Application 和内存数据库，文件持久化测试只操作单独命名的虚构测试数据库。
系统文件选择器测试只导出本次输入的三个英文虚构回答，另导入明确标记的中文虚构样例。
不读取用户已有记录或提取个人数据库。公开截图只截取本应用组件区域。

原生宿主140×150dp和280×150dp并不等于桌面格数。荣耀实际2×2宿主外框564×666px，
组件内容512×614px；4×2宿主外框1128×666px，内容1076×614px，已经分别测量。
采用Responsive布局：小尺寸摘要2行，宽尺寸3行；真实桌面调整后布局能切换。

## T01–T24 验收矩阵

PASS只适用于右侧列出的证据；同一项内未执行的分支单独标为NOT TESTED。

| 编号 | 结果 | 证据及边界 |
|---|---|---|
| T01 三字段保存与重启 | PASS | 荣耀实际填写三框、保存、停止进程、覆盖安装、冷启动后逐项比较；Compose另测保存/搜索/重开 |
| T02 日期隔离/时区 | PASS | Room两日独立；注入Clock/ZoneId验证跨日和历史键不被重写；未改手机全局日期 |
| T03 时间戳 | PASS | Room更新保留createdAt、updatedAt递增；JSON往返元数据深比 |
| T04 空值及删除确认 | PASS | Room不新建全空、保留已存在空记录；真实UI删除入口有确认步骤 |
| T05 编辑切换/旋转 | PASS | 8项并发测试含防抖、延迟旧读取、导航、后台、失败重试与导入竞态；荣耀横屏后逐项确认三个已保存回答 |
| T06 覆盖安装/迁移 | PASS；迁移NOT TESTED | 荣耀同签名APK覆盖安装后保留三个回答；v1关闭重开通过。首版无旧schema，不声称已测试真实版本迁移 |
| T07 交接算法 | PASS | JVM和真实Room覆盖无历史、隔日、漏日、最近目标空、今日已存在；真实Launcher标注2026-10-23来源 |
| T08 添加及多实例 | PASS | 一键请求、系统确认，荣耀桌面两个实例同时渲染；之后清理额外实例 |
| T09 尺寸/长中文 | PASS；组件大字体NOT TESTED | 荣耀桌面2×2/4×2及2560字中文目标截断；原生宿主另测数千中文字符。未改变手机系统字号验证Launcher |
| T10 冷启动入口 | PASS | 杀死本应用进程并确认已退出后，从荣耀组件分别点击今日和历史，等待并断言对应页面；非后台Activity跳板 |
| T11 提交后刷新 | PASS | 原生宿主等待新增、修改、删除、JSON导入后的真实文本变化；事务失败断言零刷新。荣耀SAF导入后桌面显示长中文来源目标 |
| T12 午夜/系统广播/重启 | PASS（受控恢复）；真实午夜/重启NOT TESTED | 直接调用实际Receiver的BOOT_COMPLETED、DATE_CHANGED、TIME_SET、TIMEZONE_CHANGED、MY_PACKAGE_REPLACED分支，逐项断言提醒重排及恢复任务入队；不是系统真实广播送达或物理重启证据 |
| T13 历史日历/编辑 | PASS | Compose跨月/回本月、记录重开；荣耀点击过去日期编辑虚构记录 |
| T14 搜索 | PASS | Room三字段中文、英文大小写、%、_、引号、反斜杠、倒序；UI搜索“特殊%_中文” |
| T15 导出清空再导入 | PASS | Room往返深比；荣耀实际SAF导出→界面删除全部虚构记录→SAF导入→再次导出，逐项比较全部字段及时间戳一致 |
| T16 冲突/确认/取消/幂等 | PASS | Room跳过/覆盖/重复导入；荣耀预览新增1冲突1，取消后仍为新增1，覆盖二次确认可返回，默认跳过保持今日回答 |
| T17 非法备份/回滚 | PASS | Codec15项含10MiB边界、10000条、字段限额、坏UTF8、重复键、重复日期、坏时间、未来schema和深嵌套；Room触发器制造中途失败后整事务回滚 |
| T18 拒绝权限/离线 | PASS；飞行模式NOT TESTED | 荣耀拒绝通知后核心数据保持可用；合并Manifest无INTERNET，应用不具备联网权限；未切换手机全局网络 |
| T19 隐私与Manifest | PASS | 安装包权限实测、无INTERNET/位置/联系人/相机/存储根权限，备份关闭；无广告/遥测依赖。AndroidX合并的WAKE_LOCK/网络状态/前台服务声明不代表应用启用常驻服务 |
| T20 Android15布局 | PASS；全键盘矩阵NOT TESTED | 荣耀横竖屏、系统栏与底部导航；实际输入法提交三个字段。未遍历所有输入法及软件键盘高度 |
| T21 大字体/无障碍 | PASS；TalkBack朗读NOT TESTED | 荣耀Compose2倍字体可滚动填写、跨月；原生AccessibilityNodeInfo逐框验证问题名称，并通过ACTION_SET_TEXT写入隔离样例。未开启TalkBack逐项朗读 |
| T22 通知开关/权限 | PASS；长期省电NOT TESTED | 真实权限开启/拒绝/关闭；实际Receiver验证私密通用正文、过期/未到期不发、同日去重；另由真实非精确AlarmManager触发通知，本次晚4694ms。未验证长期省电延迟或固定21点的长期可靠性 |
| T23 公开内容隔离 | PASS | 扫描可发布工作文件、暂存区与本地可达Git对象；14项扫描器自测；仅一个本地可达上游提交，浅克隆之前历史未审计 |
| T24 构建产物 | PASS；Release NOT TESTED | APK真实存在、大小/散列/签名验证、荣耀安装及冷启动；无正式签名和商店分发测试 |

## 复现与日志

Windows运行 `scripts/build.ps1 -DeviceTests`。设备脚本覆盖安装、直接运行Instrumentation，
临时授权自己的组件测试宿主并在finally撤销；不会在测试结束后卸载日常使用的应用。
以输出 `OK (24 tests)` 判定通过，不能只依赖ADB命令退出码。
`scripts/build.ps1 -AlarmProbe` 在完整设备测试后额外等待真实闹钟；12分钟为观察上限，未观察到通知不能推断为违反精确时间承诺。
提醒测试只保存并恢复提醒偏好及自己的通用通知；通知权限撤销会结束应用进程，因此由外层脚本在测试完成后恢复权限。
构建及测试脱敏输出：[verification.txt](logs/verification.txt)。
JVM逐项结果：[unit-tests.txt](logs/unit-tests.txt)。
Launcher冷启动：[launcher-smoke.txt](logs/launcher-smoke.txt)。
SAF及手机操作记录：[device-smoke.txt](logs/device-smoke.txt)。
真实闹钟投递：[alarm-probe.txt](logs/alarm-probe.txt)。
隐私扫描：[privacy-scan.txt](logs/privacy-scan.txt)。

首次失败及修复：新Compose基线的AAR要求AGP9.1/SDK37，改用固定兼容BOM；中文路径的JDK测试类加载问题改用核实过的英文Junction；
JSON排序断言误匹配导出头日期改为匹配记录date字段；成功但全部跳过的导入也刷新Widget，更新对应回调断言。
原生Widget PendingIntent跳转后，MagicOS上的ActivityScenario清理观察状态滞留PAUSED，
改为Instrumentation持有真实Activity、释放宿主并检查isDestroyed；保留所有渲染与按钮断言，完整复跑通过。

## 截图

- [荣耀今日页（已清空虚构记录）](screenshots/honor-today-empty.png)
- [荣耀2×2](screenshots/honor-launcher-compact.png)
- [荣耀4×2](screenshots/honor-launcher-wide.png)
- [荣耀4×2长中文目标](screenshots/honor-launcher-wide-long-goal.png)
- [原生宿主紧凑](screenshots/widget-compact-host.png)、[宽版](screenshots/widget-wide-host.png)、[长中文](screenshots/widget-long-goal-host.png)

截图不含其他桌面组件或私人记录。提醒与省电、重启、真实午夜、TalkBack和正式升级渠道的限制见 [KNOWN_LIMITATIONS](KNOWN_LIMITATIONS.md)。

收尾：虚构记录及共享存储测试JSON已删除；测试APK和临时宿主权限已移除，提醒关闭，通知权限恢复初始状态。
恢复了USB测试常亮与原先的竖屏锁定设置；手机保留主应用及一个4×2桌面组件。
