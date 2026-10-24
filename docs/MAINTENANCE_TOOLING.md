# 维护工具 / Maintenance tools

These are development tools. They are not bundled in the Android application.
以下仅为开发维护工具，不会打包进手机应用，也不会接触或上传个人日记。

| 工具 / Tool | 许可证 / License | 用途 / Purpose | 状态 / Status |
| --- | --- | --- | --- |
| [Windows-MCP](https://github.com/CursorTouch/Windows-MCP) | MIT | Windows 状态、应用与文件操作 / Windows state, apps and files | 当前会话已有接口，无需重复安装 / Already connected in this session |
| [Playwright MCP](https://github.com/microsoft/playwright-mcp) | Apache-2.0 | 对照浏览器自动化方案 / Browser automation reference | 已评估，未安装 / Reviewed, not installed |
| [MCPControl](https://github.com/claude-did-this/MCPControl) | MIT | 对照另一种 Windows 自动化方案 / Alternative Windows automation reference | 已评估，未安装 / Reviewed, not installed |
| [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) | Android SDK terms | 荣耀90安装与合成数据测试 / Honor 90 installation and synthetic tests | 复用既有环境 / Existing environment |
| GitHub connector | Service-specific terms | 仓库读取和源码提交 / Repository reads and source commits | 使用用户已连接的账号 / Uses the connected account |

## 环境自检 / Environment check

Run `./scripts/doctor.ps1 -Device` in PowerShell from the project folder.
运行项目内的 `./scripts/doctor.ps1 -Device`，检查构建工具、唯一已授权设备及 Google 服务组件是否存在。
自检不修改系统设置，不输出设备序列号、账号、密钥或数据库。
The check does not change system settings or print device serials, accounts, keys or databases.

## 操作入口 / Operation boundaries

- 源码、构建和检查优先使用项目脚本与 GitHub 接口；界面操作使用已连接的工具。
  Use project scripts and GitHub APIs for source and builds, and connected UI tools for UI operations.
- 上传 APK 使用发行页，APK 不作为源码文件提交。无法完成的上传或登录必须如实报告。
  Upload APKs as release assets rather than source files. Report upload or login blockers honestly.
- 登录、协议或授权若需要本人操作，在官方页面交接；不读取浏览器凭据库，不把凭据写进脚本。
  Hand off any required account login, terms or authorization on the official page. Never read browser credential stores or embed credentials in scripts.
- 日志与截图只使用合成记录，发布前运行 `scripts/check_sensitive.py --self-test`。
  Use synthetic records for logs and screenshots and run the sensitive-data check before publishing.

## 来源核对 / Source review

2026-10-24 通过 GitHub 接口核对上述三个开源仓库的 README 与许可证信息。
Windows-MCP 的 README 标明当前源码要求 Python 3.14+；本项目优先复用已连接服务，未更改全局 Python 或额外安装后台服务。
Reviewed the three repositories through GitHub on 2026-10-24. The current Windows-MCP source requires Python 3.14+.
This project reuses the connected service and does not replace global Python or add a background service.
