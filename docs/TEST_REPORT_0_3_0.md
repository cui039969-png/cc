# 0.3.0 实测报告

日期：2026-10-24，Asia/Shanghai。versionCode3，versionName0.3.0；包名 io.github.daily3minutes。
本轮直接实现用户指出的缺项：双栏桌面卡片、历史默认近7天交接、三横范围菜单、上滑收起及点击恢复日历。

## 结果

| 项目 | 实际结果 |
|---|---|
| Debug APK | PASS，21,103,598 bytes；实际覆盖安装至荣耀90 |
| JVM | 54/54 PASS，0失败、0跳过 |
| 荣耀90设备测试 | 37/37 PASS，37项状态均成功，0跳过；实际91.686秒 |
| Lint | PASS，0 errors、36 warnings、2 hints；未删除检查或测试 |
| 签名与正常冷启动 | PASS；v2签名，正常Application启动成功 |
| 数据结构 | 沿用Room及JSON schema1，无迁移、无破坏性回退 |
| 升级 | 本轮开始实际确认手机0.2.0，随后0.3.0覆盖安装成功；专用虚构私有文件验证的是随后0.3.0同签名覆盖保留，不冒充0.2→0.3记录深比 |

APK SHA-256：`f2660ef7b80b7c225f72f86b86bebe6b56fe9c67bf3872ceaa2f95c9f043601e`。
交付位置：`.local/delivery/daily-3-minutes-0.3.0-debug.apk`。
未读取、导出或比较用户个人记录。安装与测试不通过卸载日常应用进行；临时测试APK在收尾移除。

## 本次要求逐项验证

| 用户可见要求 | 已执行的验证 |
|---|---|
| 桌面上栏昨天／最近过去交接，下栏今天填写 | 荣耀真实AppWidgetHost以140×150及280×150dp渲染，断言上下文字、日期和按钮实际可见且不裁切；下栏整区和填写按钮均进入今日，历史入口仍可用 |
| 今日内容更新不顶掉昨天交接 | 原生宿主实际等待今天新增、编辑、清空、删除、导入和覆盖后的状态变化，上栏始终为过去来源；另测漏写数日及最近空目标，不跳过最近记录 |
| 历史默认月历＋近7天 | 真机页面未滚动前断言日历、菜单、近7天标题及第一条真实目标均可见；分别检查今日目标、次日交接日期、空目标和未记录日期 |
| 三横菜单及全部/3/7/30天 | 实际点击菜单并检查四个选项可见、选择后更新范围；3天边界、30天跨月、全部较早记录及用户预写未来记录均检查 |
| 上滑日历离开，横条恢复 | 使用真实触摸swipeUp两次，断言月历不再显示、吸顶把手和“展开日历”可见；点击后月历与“收起日历”恢复 |
| 点击交接查看原日期 | 实际点击10月20日卡片，逐项比较三个回答，日期不改为今天 |
| 大字体与即时刷新 | 2倍字体打开菜单、切换范围并滚动查看；历史页停留时修改隔离Room目标，等待真实文本刷新 |
| 外部今日入口不残留菜单 | 真实菜单打开后调用与onNewIntent相同的externalDestination路径，等待今日页面，断言历史菜单选项不再可见且今日输入可用 |

新增8项HistoryRange JVM测试，覆盖含今天的自然日区间、闰日/跨月/跨年、缺失与空目标区分、全部模式不丢未来记录、排序及日期上下界。
设备新增7项历史页面测试，组件宿主从3项扩至5项，原有其他测试保留。
JVM分组：BackupCodec15、DailyRules5、DayBoundary3、HistoryPreview6、HistoryRange8、ReminderTimes6、EditorConcurrency11。
设备分组：Repository8、文件重开1、EditorUI7、HistoryUI7、WidgetHost5、WidgetDestination4、Privacy2、Reminder3。

## 环境与证据边界

荣耀90 REA-AN00，Android15/API35、MagicOS9.0.0。默认屏幕1200×2664，字体1.0；测试内容另覆盖2倍字体与宽横屏配置。
所有记录、图片内容为隔离内存Room中的虚构样例；未从正常应用数据库提取记录。
本轮新组件布局由荣耀设备上的真实原生宿主测试并截图；未截取含私人内容的正常Launcher。
0.1.0的荣耀Launcher2×2/4×2手动证据仍是上版证据，不冒充本轮新双栏的Launcher手动操作。
覆盖安装后正常应用启动会请求现有组件刷新，保留原组件实例。

开源研究通过GitHub实际查看Tasks.org、Calendar、Compose Samples及AndroidX官方样例；Tasks.org是对“TASK”的明确假设，GPL仅作产品对照。
另外核对既有官方组件源码与许可；未增加新依赖，见 [开源对照](OPEN_SOURCE_REVIEW_0_3_0.md)。

## 修正与保留限制

首轮54项JVM及36项设备测试已通过，但截图显示日历过高，首条目标正文在屏幕下方。
因此压缩普通字号日历的行高和留白，保留大字体可滚动，新增未滚动前首条目标可见断言。
只读复核还发现历史菜单可能跟随外部“今日”入口覆盖今日页；增加离开历史自动关闭，并补第37项设备回归。
上述修正后完整重新构建并运行本报告37项设备测试，没有删除失败测试或伪称旧截图为新结果。

实际午夜、物理重启、TalkBack朗读、Launcher系统大字号及长期省电可靠性仍未完成实测；正式Release签名和远程CI尚未执行。
旧版完整验收证据：[0.1.0](TEST_REPORT_0_1_0.md)、[0.2.0](TEST_REPORT_0_2_0.md)。

## 日志与截图

[构建与37项设备结果](logs/verification-v030.txt) · [54项JVM](logs/unit-tests-v030.txt) ·
[升级范围](logs/upgrade-v030.txt) · [签名](logs/signature-v030.txt) · [冷启动](logs/cold-start-v030.txt) · [公开内容扫描](logs/privacy-v030.txt)

- [默认月历＋近7天](screenshots/v030-history-default.png)
- [三横菜单](screenshots/v030-history-menu.png)
- [上滑后的交接区域与恢复把手](screenshots/v030-history-collapsed.png)
- [紧凑双栏组件](screenshots/v030-widget-compact.png)、[宽版今日已填写](screenshots/v030-widget-wide-today-saved.png)
- [漏写日期来源](screenshots/v030-widget-missed-day.png)、[最近空目标](screenshots/v030-widget-nearest-empty.png)

收尾保留日常应用及已有组件，清理本轮已知虚构截图缓存、测试APK和专用校验文件；临时组件宿主授权由脚本撤销、通知权限恢复初始状态。
本轮未改动手机全局时间、字号、方向锁定或USB常亮设置。
