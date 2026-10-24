# v0.5 开源和产品对照 / Open-source and product review

2026-10-24至25日先读取仓库介绍与实际许可证，再实现本轮改动。以下前三项只作体验参考，没有复制实现或新增运行依赖。既有MIT上游和Apache-2.0声明继续保留，Google服务组件按原有第三方条款单列。

Read repository descriptions and actual licenses before implementation on 24–25 October 2026. The first three projects are experience references only: no implementation copied or runtime dependency added. Existing MIT and Apache-2.0 notices and the separate Google service-component classification remain in force.

| 来源 / Source | 核对提交 / Reviewed HEAD | 许可证 / License | 应用到本项目 / Application |
| --- | --- | --- | --- |
| [Quillpad](https://github.com/quillpad/quillpad) | c12ff24d15bad1852332164812f89c00177c2967 | GPL-3.0 | 录音附件、ZIP备份的产品组织 / Audio attachments and ZIP backup organization |
| [Fossify Notes](https://github.com/FossifyOrg/Notes) | 25f6486727eabfb93b90beae17f4a6c2816c882b | GPL-3.0 | 简洁记录、自动保存、可配置组件 / Simple writing, autosave and configurable widgets |
| [Markor](https://github.com/gsantner/markor) | 8f75f729a19ce3c39b0831603d3f842bb7fa9834 | Apache-2.0，实际LICENSE.txt / actual LICENSE.txt | 独立长文编辑 / Dedicated long-form editing |
| AndroidX Compose / Glance / Room | 版本继续见[依赖清单](DEPENDENCIES.md) / Versions unchanged | Apache-2.0 | 实际复用界面、滚动组件和数据库迁移 / Actual reused UI, scrolling widget and migration libraries |

[vivo官方原子笔记介绍](https://www.vivo.com.cn/vivo/vivopad)用于对照书写和录音入口的产品体验。不是开源代码来源，未声称在本机安装或操作过其应用。没有验证其他厂商的专有实现，不能把品牌功能宣传等同于本项目实测。

The [official vivo Atomic Notes presentation](https://www.vivo.com.cn/vivo/vivopad) informed the comparison of writing and recording entry points. It is not an open-source code source, and no hands-on test of that app is claimed. Other manufacturers' proprietary implementations were not validated.

实现使用已有开源库和Android平台API：[Glance滚动列表](https://developer.android.com/develop/ui/compose/glance/build-ui)、[MediaRecorder](https://developer.android.com/media/platform/mediarecorder)、[系统识别Intent](https://developer.android.com/reference/android/speech/RecognizerIntent)。录音、事务备份、日期与问题状态保护为本项目独立实现。没有把系统服务或厂商应用标为本项目的开源依赖。

Implementation uses existing libraries and Android platform APIs linked above. Recording lifecycle, transactional archives and date/question state guards are project implementations. Platform services and manufacturer apps are not presented as bundled open-source dependencies.

## 后续优化工具 / Tools for future improvement

| 项目 / Project | 核对结果 / Review | 选择 / Decision |
| --- | --- | --- |
| [Android testing-setup skill](https://github.com/android/skills/blob/main/testing/testing-setup/SKILL.md) | Google官方，Apache-2.0；目录显示约2400次安装 / Official Google, Apache-2.0; directory reported about 2,400 installs | 对照测试分层、内存数据库、尺寸和导航检查；未安装其整套框架改造 / Reviewed test layers, in-memory fixtures, sizes and navigation; broad framework replacement not installed |
| [Maestro](https://github.com/mobile-dev-inc/Maestro) | README支持Android真机、可重复YAML及内置MCP；Apache-2.0 / README supports physical Android, YAML flows and built-in MCP; Apache-2.0 | 后续跨应用端到端候选；本轮复用已验证Compose/Espresso宿主，未安装 / Candidate for future cross-app E2E; current verified Compose/Espresso harness reused, not installed |
| [Mobile MCP](https://github.com/mobile-next/mobile-mcp) | README和实际LICENSE核对，Apache-2.0；支持真机及遥测关闭配置 / README and actual license checked; physical devices and telemetry opt-out documented | 后续交互检查候选，未连接或安装，不宣称已启用 / Candidate for interactive checks; not installed or connected |

这些维护工具不打包进APK。已落地的可重复入口是[自查流程](SELF_REVIEW.md)和`scripts/self_review.ps1`，使用现有测试环境，测试安装与个人应用分开。

These development tools are not bundled in the APK. The implemented repeatable entry is the [self-review workflow](SELF_REVIEW.md) and `scripts/self_review.ps1`, using existing tooling and an installation separate from the personal app.
