# 开源调研与复用记录
调研日期：2026-10-24。不是对全部 GitHub 仓库的穷尽审计。

用户已选定“优先改造完整应用”，取代任务书原先的独立工程路线。实际底座：
https://github.com/scottwdavis/FrictionFreeDiary/commit/ee79164ab5962a4ff9f61dca0c241aba7c74f654
许可证：MIT，原文保留 LICENSE-UPSTREAM。

| 项目 | 读过的文件/范围 | 许可与决定 |
|---|---|---|
| FrictionFreeDiary | LICENSE、README.md、app/build.gradle.kts、gradle/libs.versions.toml、AndroidManifest.xml、data/local/DiaryDatabase.kt、ui/screens/calendar/CalendarScreen.kt、ui/screens/editor/EditorViewModel.kt、utils/JsonExporter.kt、ui/navigation/DiaryNavHost.kt | 采用工程、主题、月历和界面组织基础；MIT 归属保留。剔除 SQLCipher、生物识别、定位、图片、标签、多笔记本和外部备份格式。新数据模型与版本化 JSON 独立编写。 |
| re-ovo/ComposeDiary | LICENSE、README.md、app/build.gradle、dao/DiaryDao.kt、database/DiaryDB.kt、ui/page/Edit.kt | Apache-2.0；未采纳。SDK30、早期 Compose、缺月历/备份，升级工作更多。 |
| android/platform-samples | LICENSE、ToolBarAppWidget.kt（LongText 为任务书候选，未采用） | Apache-2.0；工具栏样例适配为 DailyWidget，保留原文件声明与修改说明。见 NOTICE.md 固定提交。 |
| chrimaeon/favorite-apps-widget | LICENSE、README 与候选组件用途 | Apache-2.0；未复制实现，产品是启动器而非每日交接。 |
| android/compose-samples | LICENSE（Jetcaster 路径为任务书候选，未复制） | Apache-2.0；未复制实现，底座已有 Room/Compose。 |
| rafsanjani/superdiary | LICENSE、README.md | MIT；未采纳，多平台实验项目及未完成界面扩大改造范围。 |
| SirionRazzer/Diary | LICENSE、README.md | MIT；未采纳，活动追踪与 Firebase 备份不符合每日三问。 |
| kenan-karic/NightDiary | LICENSE、README.md | Apache-2.0；未采纳，SQLDelight、多平台、多模块与评分统计增加偏离。 |
| Demizo/Daily_You | README.md（指向 LICENSE.txt 与附加条款） | Flutter/GPL3，未复制代码；目标仍采用 Kotlin 原生，避免更换技术栈。 |
| alisonthemonster/Presently | 根目录、README.md；根目录无 LICENSE | 未确认明确可复用授权；不复制。另含 Analytics/Crashlytics/Dropbox。 |
| Arcangel1994/DiaryAppCompose | 根目录、README.md；根目录无 LICENSE | 未确认明确授权，不复制；Firebase/Realm 服务不符合离线要求。 |

应用不访问远程服务；依赖下载只发生在开发构建期。最终依赖清单在 DEPENDENCIES.md。

## 0.2.0：通过已连接的 GitHub 重新比较多个项目

2026-10-24，实际使用 GitHub 插件读取六个公开项目的仓库元数据、默认分支当前提交、LICENSE 及下列源码或 README。
不是只查看项目名称或搜索摘要。许可与采用决定如下；本轮新增功能由本工程独立实现，未复制 GPL 源码。

| 项目与固定提交 | 本轮实际阅读 | 结论及落实 |
|---|---|---|
| [Android Platform Samples](https://github.com/android/platform-samples/tree/177c3b92c387894648d0a2d42d238e265b05bbc3) | LICENSE；`samples/user-interface/appwidgets/src/main/java/com/example/platform/ui/appwidgets/glance/layout/text/LongTextAppWidget.kt` | Apache-2.0。核对 Exact 与 Responsive 的内存/布局取舍：Exact 避免为多个尺寸持有多个视图，Responsive 便于固定两套断点。保留已经在荣耀实测的两断点 Responsive；本轮没有额外内存基准，不宣称更省内存。应用内补充长交接展开和来源入口，桌面继续摘要。 |
| [Now in Android](https://github.com/android/nowinandroid/tree/2982e63cb691ad49cd009aab43dc206fee6699c2) | LICENSE；`app/src/main/kotlin/com/google/samples/apps/nowinandroid/ui/NiaApp.kt`；`feature/settings/impl/src/main/kotlin/com/google/samples/apps/nowinandroid/feature/settings/impl/SettingsDialog.kt` | Apache-2.0。参考按可用空间切换导航及设置中的来源链接，独立使用 Material3 NavigationRail 与外部浏览器实现；不引入其账号、网络层、Google OSS Activity 或多模块架构。 |
| [Compose Samples](https://github.com/android/compose-samples/tree/25631e8f544a9a1e3f01e296eaf1b4c28428196c) | LICENSE；`Jetchat/app/src/main/java/com/example/compose/jetchat/conversation/UserInput.kt` | Apache-2.0。对比输入状态、焦点、键盘和可访问性组织。保留本工程已通过并发测试的写入门；新增从交接跳转前保存及横屏编辑导航测试。不引入聊天、附件、语音等无关交互。 |
| [Favorite Apps Widget](https://github.com/chrimaeon/favorite-apps-widget/tree/d0aea4c4dd2770722f29146ab111d9c1796c9d3f) | LICENSE；`app/src/main/java/com/cmgapps/android/personalappwidget/widget/FavoriteAppWidget.kt` | Apache-2.0。实际核对显式启动入口、文字 maxLines 与主题。现有组件已具备两按钮/摘要，保留；完整长目标在应用内可展开，不把大量正文塞进桌面。未复制其应用列表/数据库实现。 |
| [Quillpad](https://github.com/quillpad/quillpad/tree/c12ff24d15bad1852332164812f89c00177c2967) | LICENSE；README.md | GPL-3.0。只参考 README 的本地记录、搜索和备份产品取舍，不读入或移植 GPL 实现。改进本工程搜索命中摘要，继续使用自己的 schema1 JSON。 |
| [Fossify Notes](https://github.com/FossifyOrg/Notes/tree/25f6486727eabfb93b90beae17f4a6c2816c882b) | LICENSE；README.md | GPL-3.0。只参考简洁、快速记录的公开功能介绍，未移植代码，不引入彩色笔记、多笔记本等偏离固定三问的功能。 |

判断依据是实际功能、源码结构与许可，不按 Star 数挑选或宣称维护质量。
原 FrictionFreeDiary MIT 底座与既有官方工具栏归属继续保留，见 NOTICE.md。
应用设置新增“开源来源与参考”，逐项说明采用/参考的范围并链接到上述公开仓库；打开链接交给外部浏览器，应用仍没有 INTERNET 权限。

新问题核对与修复：长交接原来只有五行且不能展开；搜索结果原来将三问拼接后截前三行，可能隐藏实际命中的第二/第三问；
跨日原来依靠系统日期广播和恢复前台，文件选择器/写入期间被推迟后缺少前台补偿；横屏仍占用底部导航高度；设置版本为手写字符串。
0.2.0 分别增加展开/来源跳转、围绕命中的 Unicode 安全摘要、前台日期补偿与月视图跟随、宽横屏侧栏、生成版本及来源列表。
