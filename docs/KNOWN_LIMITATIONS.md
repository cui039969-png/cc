# v0.5测试阶段已知限制 / Beta limitations

- Google公开客户端、审核和真实双向联调未完成；公开安装包保留明确的不可连接提示。
  Public Google configuration, review and live two-way testing remain incomplete. The public APK clearly disables unconfigured connection.
- 录音采集生命周期、生成AAC播放和备份已测；没有录下真实人声验证麦克风硬件，也未验收系统识别准确率、语言模型或联网行为。
  Capture lifecycle, generated AAC playback and backup are tested; live microphone hardware and system speech accuracy/models/network behavior are not accepted.
- 系统识别需要可用提供方；离线优先不是离线保证。荣耀90检测到RecognitionService但没有识别Activity，本版增加服务兼容入口；服务本身可能拒绝调用。
  A recognition provider is required; offline preference is not a guarantee. Honor 90 exposes RecognitionService without a recognition Activity, handled by this release's fallback. The provider may still reject access.
- 完整ZIP和原有JSON均不加密。ZIP最多1000段、音频512MiB，JSON不含录音；均不含Google授权、同步关联或完成状态。无Drive或自动云备份。
  ZIP and JSON are unencrypted. ZIP supports 1,000 clips/512 MiB audio; JSON excludes audio. Neither includes Google authorization, links or completion. No Drive/automatic cloud backup.
- 组件多行正文可被旁人看到。单问展示上限4000码点，超出后点开应用；保存的20000码点回答不截断。长按由启动器管理，应用快捷操作使用“更多”。
  Widgets expose text to bystanders. Per-question display is capped at 4,000 code points with an app link; stored 20,000-code-point answers are not truncated. The launcher owns long-press; app actions use More.
- 自动保存有800ms防抖，强制停止或掉电可能丢失未提交输入。音频文件系统与SQLite不具备跨系统掉电原子事务；正常恢复失败回滚，孤立文件整理。
  Autosave has an 800ms delay. Abrupt termination can lose uncommitted input. Audio files and SQLite do not form a shared crash-atomic transaction; ordinary restore errors roll back and orphan files are reconciled.
- 荣耀90合成回归55项、JVM84项通过；未测其他品牌、完整TalkBack朗读、真实午夜/物理重启、长期省电行为。Debug合成回归不能冒充Release逐项实测。
  Honor 90 synthetic regression (55) and JVM tests (84) passed. Other manufacturers, full spoken TalkBack, actual midnight/reboot and long-term battery behavior remain untested. Debug regression is not a claim of identical Release functional testing.

实际发布、下载和安装状态见[本版报告](TEST_REPORT.md)。历史情况见[v0.4](TEST_REPORT_0_4_0.md)、[v0.3](TEST_REPORT_0_3_0.md)，旧限制不作为当前结论。
See the current report for actual publication, download and installation status. Earlier reports describe earlier releases.
