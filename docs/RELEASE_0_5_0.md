# v0.5.0-beta.1 — 每天3分钟 / Daily 3 Minutes

## 中文

- 三问分别打开独立长文编辑页，上方横向日期行、月份控制和问题页签；切换前先保存。
- 桌面下栏支持多行和上下滑动，点正文进入对应问题；“更多”提供问题快捷入口。原有上栏交接、历史菜单、近七天列表保留。
- 添加本机录音附件、播放和确认删除；语音转文字支持系统弹窗及RecognitionService，结果确认后追加。
- 新增完整ZIP备份文字和录音，支持仅录音日期、冲突预览、默认跳过与失败回滚；原有JSON schemaVersion 1保持不变。
- 本机数据库升为schema 3，新增录音表，保留旧文字和任务同步表。更新简体中文/英文操作说明、开源对照、自主优化流程。

这是测试版。Google公开账号配置、审核与真实双向联调仍未完成。语音转文字依赖设备服务，可能联网；没有服务时可继续打字。自动化测试使用假采集器、生成AAC和合成识别结果，不声称实际人声采集或厂商识别准确率已经验收。

## English

- Each question opens a dedicated long-form editor with a horizontal day rail, month controls and question tabs. Edits save before switching.
- The widget's lower panel supports multiline vertical scrolling. Tapping text opens its question; More provides quick actions. Existing handoffs and history views remain.
- Adds local audio attachments, playback and confirmed deletion. Dictation supports recognition Activities and RecognitionService, with review before append.
- Full ZIP backup includes text and audio, audio-only dates, conflict preview, default skipping and failure rollback. JSON schemaVersion 1 remains unchanged.
- Local schema 3 adds recording metadata while preserving diary and sync tables. Updates Chinese/English instructions, source comparisons and the repeatable improvement workflow.

This is a pre-release. Public Google configuration, verification and live two-way integration remain incomplete. Dictation depends on the device service and may use the network; typing remains available without it. Automated tests use fake capture, generated AAC and synthetic recognition results. Live microphone capture and manufacturer speech accuracy are not claimed as accepted.

安装 / Install: Android 8.0+，包名 / application ID `io.github.daily3minutes`，版本号 / version code 5。覆盖安装，不卸载旧应用 / Upgrade in place without uninstalling.

附件栏目 / Assets: 安装APK / installable APK；对应源码ZIP / matching source ZIP；`SHA256SUMS.txt`。签名指纹和实测结果见[发布流程](https://github.com/cui039969-png/cc/blob/v0.5.0-beta.1/docs/PUBLISHING.md)及[测试报告](https://github.com/cui039969-png/cc/blob/main/docs/TEST_REPORT.md)。
