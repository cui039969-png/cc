# v0.4.0-beta.1 验证进度 / Verification progress

2026-10-24。测试阶段，尚未完成全部验收。Testing stage; full acceptance is not complete.

| 检查 / Check | 实际结果 / Actual result |
| --- | --- |
| Debug、测试APK、Lint / Debug, test APK, lint | 构建通过；Lint 0错误、55警告、2提示 / Passed; 0 errors, 55 warnings, 2 hints |
| JVM单元测试 / JVM tests | 81项通过，0失败 / 81 passed, 0 failed |
| Release构建 / Release build | R8压缩构建通过，签名核验通过 / Minified build and signature verification passed |
| 覆盖安装 / In-place installation | 从GitHub下载的签名Release已在荣耀90覆盖安装，版本4 / 0.4.0-beta.1；非Debug，冷启动成功；未卸载或清除记录 / Downloaded signed Release upgraded Honor 90 in place, code4 / 0.4.0-beta.1; non-debuggable, cold start passed; no uninstall or data clearing |
| 独立签名探针 / Isolated signing probe | API35旧签名→新签名轮换通过，合成私有文件保留，探针已清理 / Rotation passed; synthetic private file survived; probe removed |
| 数据迁移 / Data migration | 真实v1文件→v2迁移通过，保留全部字段；另两项验证队列事务和拒绝过期云端写入 / Real v1→v2 file migration passed with all fields preserved; two more tests cover queue transactions and stale cloud writes |
| 真机回归 / Device regression | 荣耀90完整42项通过，0失败，85.742秒 / Honor 90 full suite: 42 passed, 0 failed, 85.742 seconds |
| 隐私扫描 / Sensitive-material scan | 14项自测通过；160个工作文件及288个本地暂存/历史blob范围未识别到秘密；仅发布全新审计快照历史 / 14 scanner assertions passed; no recognizable secrets in 160 working files and 288 local index/history blobs; only new audited snapshot history published |
| Google真实联调 / Live Google integration | 未配置公开客户端，未进行真实账号联调或审核 / No public client configured; live testing/review not performed |
| GitHub发行下载 / Release download | 测试发行已发布，重新下载APK、源码ZIP与SHA256SUMS，三者摘要通过；160个源码文件按Git换行规则核对与标签提交一致；已安装APK字节与公开下载一致 / Pre-release published; APK, source ZIP and checksums downloaded and verified; all160 source files match the tag after Git line-ending normalization; installed APK bytes match public download |
| 远程CI / Hosted CI | [40091570482](https://github.com/cui039969-png/cc/actions/runs/40091570482)通过：源码扫描、Debug/测试APK/Release构建、JVM测试及Lint；报告及开发Debug包已存为CI产物 / Passed: scan, Debug/test/Release builds, JVM tests and lint; reports and development Debug artifacts retained |
| 轮换后维护回归 / Maintenance after rotation | 使用仓库外私钥兼容签名的Debug及测试APK均覆盖安装成功，42项再次通过（86.951秒），随后恢复公开下载的Release并冷启动成功（1053ms） / Compatibly signed Debug/test APKs upgraded successfully; all42 tests passed again (86.951s); the public Release was then restored and cold-started (1053ms) |

测试只用合成记录、内存数据库和专用测试偏好；未读取用户日记。升级安装本身不代表逐条核对了用户真实记录。独立签名探针先通过，再安装公开下载的轮换签名Release；安装后仅检查包信息、启动状态和APK字节，不读取真实日记界面或数据库。
Tests use synthetic records, in-memory databases and isolated preferences. User journals were not read. An in-place installation is not a record-by-record audit. The isolated rotation probe passed before the public rotated Release was installed. Post-install checks read package/startup metadata and APK bytes, not real journal screens or databases.

完整界面回归针对同源码的Debug合成测试构建，Release另测压缩构建、签名、非调试标志、覆盖安装与冷启动。没有把Debug编译的替代Application注入混淆后的Release，避免测试隔离失效。
Full UI regression uses the same-source Debug synthetic test build. Release separately passes minification, signature, non-debuggable status, in-place installation and cold start. The Debug-compiled substitute Application is not injected into an obfuscated Release, avoiding broken test isolation.

灭屏问题采用仅测试窗口保持亮屏与测试前唤醒处理，不更改用户锁屏或超时设置。迁移测试从已导出的v1结构建立真实SQLite文件，交给Room执行生产迁移并验证v2结构。
Screen handling uses test-window keep-awake flags and a pre-test wake action, without changing lock or timeout settings. Migration tests create a real v1 SQLite file from the exported schema, then use Room's production migration and v2 validation.

初轮失败已定位并修复：迁移测试从测试APK读取结构文件；版本断言跟随构建版本；英文界面测试清理自己保存的合成记录，避免污染随后组件宿主的“今天未填写”前提。独立组件与迁移4项先通过，再完整42项通过；未放宽组件文本、布局、操作或超时断言。
Initial failures were repaired: schema assets are read from the test APK, the version assertion follows the build, and the English UI fixture removes its own synthetic entry before widget tests. Four targeted widget/migration checks passed, followed by the full 42-test suite. Widget text, layout, navigation and timeout assertions were retained.

未验证：Google公开账号接入、其他品牌真机、真实午夜、物理重启、长期省电提醒和完整TalkBack朗读。未测项目不等于通过。
Not verified: public Google access, other physical devices, actual midnight/reboot, long-term battery limits and complete spoken TalkBack. Untested is not passed.

发行标签 / Release tag: `v0.4.0-beta.1` → `cc74cdbd73920dd9c0b6f214d0e309d31a953aeb`。

| 文件 / File | SHA-256 |
| --- | --- |
| APK | `7fd799d400faadc41c5677b0ea7e89affdb6c330bb9c678c4237e90a6d933375` |
| Source ZIP | `24979e93baee46ee0b5ac3abb10071a4a1a89db6ba2dbe5904cb6fd29d60b689` |

历史报告 / Historical reports: [v0.3](TEST_REPORT_0_3_0.md), [v0.2](TEST_REPORT_0_2_0.md), [v0.1](TEST_REPORT_0_1_0.md).
