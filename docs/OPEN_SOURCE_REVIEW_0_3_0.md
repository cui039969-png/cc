# 0.3.0 历史页与双栏桌面卡片：开源对照

调研日期：2026-10-24。先通过已连接的 GitHub 插件读取仓库元数据、默认分支提交、实际许可文件，再阅读下表列出的内容。固定提交用于复核，不把搜索摘要或 Star 数当成实现证据。本文记录设计依据与验证目标，不代表这些目标已经通过测试；实际结果见 TEST_REPORT.md。

用户本轮要求：桌面卡片上栏展示昨日／上次交接、下栏进入今日填写；历史页默认上方月历、下方近七天交接；上滑让月历移出屏幕，保留显眼把手回到月历；历史页左上角三横菜单切换全部、近三天、近七天和近三十天。

## 实际比较的四个来源

| 来源与固定提交 | 实际阅读的文件 | 许可核对与决定 |
|---|---|---|
| [Tasks.org / tasks/tasks](https://github.com/tasks/tasks/tree/478c8e9a0198515ab5f727f983c33c412540fa56) | `README.md`、`LICENSE`、`kmp/src/commonMain/kotlin/org/tasks/compose/drawer/TaskListDrawer.kt`；递归目录用于定位文件 | GPL-3.0。用户只给出“TASK”名称，暂按 Android Tasks.org 理解，不能声称用户已确认就是此仓库。实际查看按组展示筛选入口、当前选中项和折叠标题的产品结构；仅作产品行为对照，不复制、改写或移植 GPL 实现到本工程。这里有账号、云同步和复杂筛选，不适合作为本地三问应用的新底座。 |
| [Kizitonwose Calendar](https://github.com/kizitonwose/Calendar/tree/c2711b3c7fdf027e9a6bad55be9a7d979a288699) | `README.md`、`LICENSE.md`、`docs/Compose.md`、`sample/src/main/java/com/kizitonwose/calendar/sample/compose/Example9PageAnimatedVisibility.kt` | MIT。对比月历／周历切换、日历状态与可组合标题；样例通过 AnimatedVisibility 切换两种日历并同步日期。用户要的是月历上滑退出、交接列表占满空间，不必增加周历或替换已测试的本地月历。本轮不引入该库、不复制样例。README 明示库版本可能通过传递依赖升级 Compose，避免为了菜单和滚动扩大依赖变更。 |
| [Android Compose Samples / Jetchat](https://github.com/android/compose-samples/tree/25631e8f544a9a1e3f01e296eaf1b4c28428196c) | `LICENSE`、`Jetchat/app/src/main/java/com/example/compose/jetchat/JetchatApp.kt`、`Jetchat/app/src/main/java/com/example/compose/jetchat/components/JetchatDrawer.kt` | Apache-2.0。对照由单一导航状态派生选中项、点击选项后关闭抽屉、分组标题和系统栏留白。仅采用组织原则，独立编写历史范围菜单；不引入聊天、头像、账号或 Navigation 3 迁移。 |
| [AndroidX 官方 Compose 样例](https://github.com/androidx/androidx/tree/53bc2d6eef5ae5438d07f0071dc6f698428df978) | `LICENSE.txt`；`compose/material3/material3/samples/src/main/java/androidx/compose/material3/samples/DrawerSamples.kt`；`compose/foundation/foundation/samples/src/main/java/androidx/compose/foundation/samples/LazyDslSamples.kt` | Apache-2.0。实际核对 `ModalNavigationDrawerSample` 的 DrawerState、ModalDrawerSheet、NavigationDrawerItem 与协程开关；LazyDsl 样例核对 LazyColumn、稳定 item key、derivedStateOf 读取首个可见索引／偏移、snapshotFlow 的适用范围。用工程已有 Material3/Foundation 实现，无须新第三方依赖。只使用本项目已解析依赖版本支持的 API，不直接跟进 AndroidX 主干新 API。 |

许可证原文：[Tasks.org](https://github.com/tasks/tasks/blob/478c8e9a0198515ab5f727f983c33c412540fa56/LICENSE)、[Calendar](https://github.com/kizitonwose/Calendar/blob/c2711b3c7fdf027e9a6bad55be9a7d979a288699/LICENSE.md)、[Compose Samples](https://github.com/android/compose-samples/blob/25631e8f544a9a1e3f01e296eaf1b4c28428196c/LICENSE)、[AndroidX](https://github.com/androidx/androidx/blob/53bc2d6eef5ae5438d07f0071dc6f698428df978/LICENSE.txt)。既有 FrictionFreeDiary MIT 底座与官方组件来源声明继续见 NOTICE.md、LICENSE-UPSTREAM。

## 对本次实现的具体指导

1. 三横按钮放在历史页左上角，语义标签明确为“历史范围菜单”。菜单项目显示全部记录、近三天、近七天、近三十天，明确标出当前项；选择后关闭菜单并更新同一份范围状态。今日页保持现有写入流程。
2. 默认范围为近七天。采用截至今天的自然日区间，近七天包含今天与之前六天，避免把“七条记录”误当“七天”；日期倒序排列，不把没有记录的日期伪造为已填写。区间说明直接显示起止日期。全部记录不限制日期，但不隐式改变数据。
3. 月历与交接列表在同一纵向 LazyColumn 中，月历作为头部项目，因此上滑会自然让月历退出。用 LazyListState 派生“月历是否已经离开”状态，在列表之外保留可见的“显示日历”把手；点击滚到头部，不能把恢复入口也放进已经滚走的内容。避免根据每个像素的滚动反复增删日历，导致列表跳动。
4. 交接卡片首先展示记录日期及该记录的“明天优先解决哪一件事”；必要时注明这是交给次日的事项，空目标明确显示未填写。卡片可进入该日期的完整记录，原有日历点日与搜索仍可使用。默认屏幕必须看得到“近七天交接”标题和列表起始，不能让搜索与大月历把整个新增区挤到屏幕外。
5. 桌面组件用两个上下分区区分上次交接与今日填写，并分别提供明确点击入口。昨日无记录时保持原规则：读取今天以前最近一条记录，显示真实来源日期，不把更早记录误标为昨天；没有记录时显示空状态。保留小尺寸与大尺寸布局、足够点击区域和长文本摘要。

这些是结合用户描述和四个来源后的工程判断；并非宣称任一开源项目已经提供相同的三问交接产品。

## 本轮需要验证的可见行为

- 在荣耀90打开历史页，首次就能看到三横菜单、月历、近七天交接标题及至少一条记录或空状态；菜单选择范围后列表和选中项一致。
- 七天列表不依赖当前日历月份，能覆盖月末、年末和闰日；三／七／三十天按日期边界筛选；全部记录与搜索仍可找到较早记录。
- 上滑月历隐藏后，交接列表可继续滚动；恢复把手始终可达，点击后回到月历；从记录返回以及横竖屏切换不破坏范围状态。
- 交接条目点击打开其真实日期；有记录但目标为空与没有记录的提示有区别；修改／删除／导入记录后列表和桌面卡片更新。
- 桌面两栏均可见、各自入口正确，小尺寸和长中文不互相覆盖；升级覆盖安装保留现有数据，测试使用隔离合成记录。

本调研不创建 GitHub 远端、不推送或发布，也不读取设备中的私人记录。
