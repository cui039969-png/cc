# 后续自主优化流程 / Repeatable improvement workflow

当用户要求“自己优化”时，先确认当前版本、未提交工作和实际反馈，再按此流程执行。每轮交付具体改动和证据；不能因为参考项目有某功能就引入无关账号、服务器或依赖。

When asked to improve the app, inspect the current version, outstanding changes and actual feedback first. Deliver concrete changes and evidence. A reference app's feature does not justify unrelated accounts, servers or dependencies.

1. 先对照至少两个适合当前问题的开源项目，读取实际许可证，记录版本/提交、借鉴点、未解决的问题和实际改编范围。专有产品只作体验参考。
   Compare at least two relevant open-source projects; inspect actual licenses and record revisions, lessons, gaps and adaptation scope. Proprietary products are experience references only.
2. 优先处理写作、保存、找回记录、备份和组件阅读的真实障碍。保持三问、历史菜单、离线使用、中英文和既有备份协议。
   Prioritize actual friction in writing, saving, finding entries, backups and widget reading. Preserve the three questions, history menu, offline use, languages and backup protocol.
3. 运行下面的入口，检查构建、JVM、Lint，以及独立测试安装中的合成真机回归。
   Run the entry below for builds, JVM tests, lint and synthetic regression in a separate test installation.
4. 查看合成截图，核对正常/大字体、横屏/键盘、中文/英文，以及组件长文真实滑动。界面测试与视觉检查分别记录，不能用截图代替行为断言。
   Inspect synthetic screenshots for font sizes, landscape/keyboard, both languages and actual widget scrolling. Record visual and behavioral checks separately.
5. 涉及数据库必须用旧版公开schema建立真实SQLite文件，再运行生产迁移；备份验证恢复、冲突、损坏与事务回滚。录音测试使用生成音频和假采集器，不录下房间声音。
   Migrations must start from real SQLite files built from exported schemas and run production migration code. Verify restore, conflicts, corruption and rollback. Use generated audio and fake capture, never ambient recording.
6. 发布前审查变更、日志和源码包，保留许可。通过后构建签名Release，创建新版本，重新下载核验，再覆盖升级；不卸载个人应用，不替换既有发行资产。
   Review changes, logs and source archives and preserve licenses. After checks pass, sign a Release, create a new version, download and verify it, then upgrade in place. Never uninstall the personal app or replace old release assets.

```powershell
./scripts/self_review.ps1 -Device
```

Python不在PATH或Windows只有商店占位入口时，加`-Python <已安装解释器路径>`。脚本不会自动安装Python或更改系统配置。
If Python is absent from PATH or resolves to a Windows Store alias, pass `-Python <installed interpreter path>`. The script does not install Python or change system settings.

手机回归始终使用`io.github.daily3minutes.verification`，生产包仍为`io.github.daily3minutes`。测试使用内存数据库和独立录音目录，不读取个人应用数据。Debug测试替代Application不得注入混淆后的Release。无设备时不加`-Device`，报告必须标明设备测试未执行。

Device regression always uses `io.github.daily3minutes.verification`; the production ID remains `io.github.daily3minutes`. Tests use in-memory databases and isolated audio folders. Never inject the Debug substitute Application into an obfuscated Release. Without a device omit `-Device` and explicitly report device checks as unrun.

Google授权、真实录音/系统语音服务、其他厂商和长期省电结果必须分别记录，不把未测标为通过。该流程按用户请求运行，不自动创建后台定时任务。

Report Google authorization, real microphone/system speech, other manufacturers and long-term power management separately. Untested is not passed. This workflow runs on request and does not create scheduled background tasks.
