// SPDX-License-Identifier: Apache-2.0
package io.github.daily3minutes.ui

data class OpenSourceProject(val name: String, val url: String, val detail: String)

/** Public project links only. Opening one delegates to the user's browser. */
val openSourceProjects = listOf(
    OpenSourceProject("Tasks.org", "https://github.com/tasks/tasks",
        "GPL-3.0 · 本轮对照历史菜单和筛选组织，仅作产品参考，未移植代码。"),
    OpenSourceProject("Kizitonwose Calendar", "https://github.com/kizitonwose/Calendar",
        "MIT · 对比日历状态与折叠交互，保留本项目月历，未引入新依赖。"),
    OpenSourceProject("AndroidX Compose Samples", "https://github.com/androidx/androidx",
        "Apache-2.0 · 核对抽屉与列表滚动样例，独立实现历史菜单和日历恢复入口。"),
    OpenSourceProject("FrictionFreeDiary", "https://github.com/scottwdavis/FrictionFreeDiary",
        "MIT · 工程、主题和月历基础；保留原作者许可。"),
    OpenSourceProject("Android Platform Samples", "https://github.com/android/platform-samples",
        "Apache-2.0 · 组件工具栏基础；对比长文本布局。"),
    OpenSourceProject("Now in Android", "https://github.com/android/nowinandroid",
        "Apache-2.0 · 参考横屏导航与设置中展示开源来源的方式。"),
    OpenSourceProject("Compose Samples", "https://github.com/android/compose-samples",
        "Apache-2.0 · 参考输入状态、焦点和键盘交互。"),
    OpenSourceProject("Favorite Apps Widget", "https://github.com/chrimaeon/favorite-apps-widget",
        "Apache-2.0 · 对比组件入口、长文字摘要和主题。"),
    OpenSourceProject("Quillpad", "https://github.com/quillpad/quillpad",
        "GPL-3.0 · 仅参考公开功能介绍中的搜索和备份体验，未移植代码。"),
    OpenSourceProject("Fossify Notes", "https://github.com/FossifyOrg/Notes",
        "GPL-3.0 · 仅参考公开功能介绍中的简洁记录体验，未移植代码。"),
)
