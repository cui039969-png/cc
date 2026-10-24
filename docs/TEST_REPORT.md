# v0.5.0-beta.1 测试报告 / Test report

2026-10-25，荣耀90 / Honor 90 REA-AN00，Android15 / API35，MagicOS9。测试版，Google公开接入尚未验收。

| 检查 / Check | 实际结果 / Actual result |
| --- | --- |
| Debug / 测试APK / Lint | 构建通过；Lint 0错误、51警告、2提示 / Passed; 0 errors, 51 warnings, 2 hints |
| JVM | 84通过，0失败 / 84 passed, 0 failed |
| 完整设备回归 / Full device regression | 55通过，0失败，141.013秒 / 55 passed, 0 failed, 141.013 seconds |
| 数据库 / Database | 真实v1→v3、v2→v3 SQLite迁移，旧三问、时间及全部同步列保留 / Real SQLite migration preserves all diary/timestamp/sync columns |
| 编辑 / Editor | 三问独立页、长文、问题/日期切换保存、历史回填、无障碍名称、大字体及横屏 / Full pages, long text, save-before-switch, history, accessibility names, large fonts and landscape |
| 组件 / Widget | 原生宿主140×150、280×150、280×260dp，多行真实上滑到后段正文、问题跳转和更多入口 / Native hosts, actual multiline scrolling, question links and More |
| 音频 / Audio | 假采集器验证前台结束保存；生成AAC经ZIP恢复后静音播放至完成 / Fake capture lifecycle and generated AAC ZIP restore/muted playback |
| 语音文字 / Dictation | 合成服务回调验证结果预览、追加和取消；荣耀检测到服务但没有识别Activity / Synthetic callbacks verify review, append and cancellation; Honor exposes a service but no recognition Activity |
| 备份 / Backup | 原有JSON与完整ZIP，默认冲突跳过、ID合并、仅录音日期、损坏和路径拒绝、数据库失败回滚及文件清理 / JSON/ZIP, conflicts, ID merge, audio-only dates, corruption/path rejection and rollback |
| Release | 生产包名、版本5、R8构建和Lint通过；非Debug；兼容原轮换签名 / Production ID/code5, minified build and lint passed; non-debuggable; compatible rotation signer |
| GitHub / 安装 / Delivery | 本报告提交时等待发布、下载及覆盖安装；最终结果会追加 / Publication, download and installation pending at this report commit; final results will be appended |

证据 / Evidence: [完整回归](logs/v050-regression.txt)、[JVM](logs/v050-unit-tests.txt)、[签名](logs/v050-release-signature.txt)、[编辑与音频](EDITING_AND_AUDIO.md)。APK SHA-256: `0dd9b012f9bf5fe30da6122e1aa76e8f096d6302ff099b87abf8f7d0cb11448b`。

回归安装与个人应用分开，ID为`io.github.daily3minutes.verification`；内存数据库、私有合成录音目录和假识别服务确保没有读个人日记或录下房间声音。迁移使用公开旧schema创建专门SQLite文件。未将Debug替代Application注入混淆Release。

Regression uses a separate verification installation, in-memory databases, isolated audio folders and fake recognition. Personal journals and ambient sound are not read or recorded. Migration uses dedicated SQLite files from exported schemas. The Debug substitute Application is not injected into an obfuscated Release.

修复过的失败包括横屏编辑区挤压、无障碍名称、旧测试的内联编辑假设、截图多根节点、听写测试未结束编辑造成的合成数据互串。一次并行Release压缩时的横屏返回等待超时也保留在本地失败日志；修正隔离后、构建结束再运行的最终结果如上，未通过放宽内容或保存断言来掩盖失败。

Repaired issues include squeezed landscape editing, missing accessible names, outdated inline-editor assumptions, multiple screenshot roots and a dictation fixture autosave race. A landscape return timeout during a concurrent Release build remains in local failure logs. Final regression ran after build completion; content/save assertions were retained.

未验收 / Not accepted: 真实麦克风人声采集、系统识别准确率/联网行为、Google真实授权和双向云端联调、其他品牌手机、完整TalkBack朗读、真实午夜/重启及长期省电。检测到服务和合成测试通过不等于这些已经完成。

Live microphone capture, system recognition accuracy/network behavior, live Google authorization/sync, other manufacturers, full spoken TalkBack and actual midnight/reboot/long-term power behavior remain unverified. Service discovery and synthetic tests do not establish those results.

历史报告 / Earlier reports: [v0.4](TEST_REPORT_0_4_0.md)、[v0.3](TEST_REPORT_0_3_0.md)。
