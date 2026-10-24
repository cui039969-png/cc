# v0.4 开源引用核对 / Open-source review

| 类别 / Category | 来源 / Source | 版本或依据 / Version or evidence | 用途 / Use |
| --- | --- | --- | --- |
| 改编 / Adapted | FrictionFreeDiary, MIT | ee79164ab5962a4ff9f61dca0c241aba7c74f654 | 原有工程/主题/月历；许可保留 / Existing project, theme, calendar; license retained |
| 改编 / Adapted | Android Platform Samples, Apache-2.0 | 177c3b92c387894648d0a2d42d238e265b05bbc3 | 组件基础 / Widget foundation |
| 依赖 / Dependency | AndroidX Credentials, Apache-2.0 | 1.5.0 | 可选账号选择 / Optional account selection |
| 依赖 / Dependency | Coroutines Play Services, Apache-2.0 | 1.9.0 | 等待 Google 异步结果 / Await Google tasks |
| 第三方服务 / Service component | Google Play services Auth / Google ID | 21.4.0 / 1.1.1 | 官方授权SDK，按Google条款，不标成全部开源 / Official authorization SDKs under Google terms |
| 对照 / Reviewed | android/identity-samples, Apache-2.0 | 2026-10-24读取README/LICENSE / README and license inspected | 对照登录结构，无需引入整个样例 / Sign-in reference; full app not imported |
| 对照 / Reviewed | openid/AppAuth-Android, Apache-2.0 | 2026-10-24读取README及许可元数据 / README and license metadata inspected | 比较OAuth方案，未添加依赖 / Compared OAuth approach; not a dependency |
| 产品参考 / Product reference | Tasks.org, GPL-3.0 | 478c8e9a0198515ab5f727f983c33c412540fa56 | 阅读发布、登录、备份组织；不复制GPL代码 / Reviewed release, auth and backup organization; no GPL code copied |
| 维护工具 / Dev tool | CursorTouch/Windows-MCP, MIT | 2026-10-24核对README/LICENSE.md / README and license inspected | 复用已连接的Windows接口，不打包进Android / Existing Windows tooling, not bundled |
| 对照 / Reviewed | microsoft/playwright-mcp, Apache-2.0; MCPControl, MIT | 2026-10-24核对仓库与许可证 / Repository/license review | 工具比较，未安装或打包 / Tool comparison; not installed or bundled |

Tasks.org 与 Google Tasks 服务不同。任务合并、数据库表和本应用JSON备份为独立实现，复用现有开源库。没有为了显示“引用开源”而引入额外备份框架。
Tasks.org and Google Tasks are different products. Merge logic, database tables and this app's JSON backup are independently implemented using existing libraries. No unnecessary backup framework was added merely for attribution.

来源：[Tasks.org](https://github.com/tasks/tasks)、[Android Identity Samples](https://github.com/android/identity-samples)、[AppAuth](https://github.com/openid/AppAuth-Android)、[Windows-MCP](https://github.com/CursorTouch/Windows-MCP)、[Playwright MCP](https://github.com/microsoft/playwright-mcp)、[MCPControl](https://github.com/claude-did-this/MCPControl)。
