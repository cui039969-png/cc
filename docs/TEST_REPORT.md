# v0.4.0-beta.1 验证进度 / Verification progress

2026-10-24。测试阶段，尚未完成全部验收。Testing stage; full acceptance is not complete.

| 检查 / Check | 实际结果 / Actual result |
| --- | --- |
| Debug、测试APK、Lint / Debug, test APK, lint | 构建通过；Lint 0错误、55警告、2提示 / Passed; 0 errors, 55 warnings, 2 hints |
| JVM单元测试 / JVM tests | 81项通过，0失败 / 81 passed, 0 failed |
| Release构建 / Release build | R8压缩构建通过，签名核验通过 / Minified build and signature verification passed |
| 覆盖安装 / In-place installation | 荣耀90已覆盖安装beta Debug版；未卸载或清除记录 / Beta Debug installed on Honor 90 without uninstalling or clearing entries |
| 独立签名探针 / Isolated signing probe | API35旧签名→新签名轮换通过，合成私有文件保留，探针已清理 / Rotation passed; synthetic private file survived; probe removed |
| 数据迁移 / Data migration | 真实v1文件→v2迁移通过，保留全部字段；另两项验证队列事务和拒绝过期云端写入 / Real v1→v2 file migration passed with all fields preserved; two more tests cover queue transactions and stale cloud writes |
| 真机回归 / Device regression | 荣耀90完整42项通过，0失败，85.742秒 / Honor 90 full suite: 42 passed, 0 failed, 85.742 seconds |
| 隐私扫描 / Sensitive-material scan | 14项自测通过；152个工作文件范围未识别到秘密 / 14 scanner assertions passed; no recognizable secrets in the 152-file working scope |
| Google真实联调 / Live Google integration | 未配置公开客户端，未进行真实账号联调或审核 / No public client configured; live testing/review not performed |
| GitHub发行下载 / Release download | 未完成 / Not completed |

测试只用合成记录、内存数据库和专用测试偏好；未读取用户日记。升级安装本身不代表逐条核对了用户真实记录。签名探针是独立测试包，不是假称已经把生产应用切到新签名。
Tests use synthetic records, in-memory databases and isolated preferences. User journals were not read. An in-place installation is not a record-by-record audit. The signing probe is separate; production signature rotation is not claimed yet.

灭屏问题采用仅测试窗口保持亮屏与测试前唤醒处理，不更改用户锁屏或超时设置。迁移测试从已导出的v1结构建立真实SQLite文件，交给Room执行生产迁移并验证v2结构。
Screen handling uses test-window keep-awake flags and a pre-test wake action, without changing lock or timeout settings. Migration tests create a real v1 SQLite file from the exported schema, then use Room's production migration and v2 validation.

初轮失败已定位并修复：迁移测试从测试APK读取结构文件；版本断言跟随构建版本；英文界面测试清理自己保存的合成记录，避免污染随后组件宿主的“今天未填写”前提。独立组件与迁移4项先通过，再完整42项通过；未放宽组件文本、布局、操作或超时断言。
Initial failures were repaired: schema assets are read from the test APK, the version assertion follows the build, and the English UI fixture removes its own synthetic entry before widget tests. Four targeted widget/migration checks passed, followed by the full 42-test suite. Widget text, layout, navigation and timeout assertions were retained.

未验证：Google公开账号接入、其他品牌真机、真实午夜、物理重启、长期省电提醒、完整TalkBack朗读和发行下载后安装。未测项目不等于通过。
Not verified: public Google access, other physical devices, actual midnight/reboot, long-term battery limits, complete spoken TalkBack and installation from a downloaded release. Untested is not passed.

历史报告 / Historical reports: [v0.3](TEST_REPORT_0_3_0.md), [v0.2](TEST_REPORT_0_2_0.md), [v0.1](TEST_REPORT_0_1_0.md).
