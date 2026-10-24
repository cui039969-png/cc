# 隐私说明

「每天3分钟」把三问回答、记录日期和创建/修改时间保存在应用私有的本地 Room 数据库。本地功能无需账号，没有广告、遥测或 AI 服务。v0.4 增加可选 Google Tasks 接入，因此请求互联网及网络状态权限；不请求位置、联系人、相机、指纹、生物识别或存储根目录权限。数据库没有应用层加密或密码锁；设备的屏幕锁和操作系统应用隔离提供基础保护。

主动连接 Google 后，账号标识和邮箱用于区分本机同步关联；仅第三个回答、来源日期、任务日期及完成状态发送到 Google Tasks。另两个回答不会上传，不部署额外服务器，不把账号令牌或内容写入日志。SDK 使用的 Google 服务适用其条款。本应用只处理独立列表内自己的标记/关联任务，但 Google 提供的 tasks 授权本身允许管理全部任务，授权界面会这样显示。断开连接停止后续同步，保留两端内容；撤销 Google 端授权请使用账号第三方连接页面。当前公开配置和真实联调状态见 GOOGLE_TASKS.md。

系统自动备份关闭。卸载应用通常会删除应用私有记录；更换手机或卸载前，请主动使用设置中的 JSON 导出，并检查备份是否成功。删除单日记录需要明确确认；清空已有记录的三框会保存为空记录，不会隐式删除该日。新日期三框全空时不新建记录。

JSON 备份是明文，包含全部回答与时间戳，不包含 Google 授权、同步关联、任务完成状态或应用偏好。备份由用户通过系统文件选择器选择位置；如果选用第三方云盘文件提供方，其传输、存储及隐私规则由对应提供方决定。应用不接入 Drive，不自动上传备份。请妥善保管文件，避免发送给不信任的人；应用卸载也不会自动删除已经导出的文件。

双栏桌面组件上栏显示最近一次过去记录的明日目标和来源日期，下栏显示今天填写状态及首个非空回答的摘要；旁人可能在桌面上看到这些私人文本。应用没有另外的锁屏或密码功能。每日提醒默认关闭；只在用户主动开启时申请通知权限。提醒显示通用文案，不显示三问内容，关闭或拒绝权限不影响本地记录功能。

自动保存使用防抖，并提供即时保存按钮与状态提示。防抖尚未提交时发生强制停止、崩溃或掉电可能丢失最近输入；请在重要编辑后确认“已保存”。时区变化只改变“今天”的计算，不移动历史记录的日期键。

测试仅使用明确标注的虚构文本和独立测试数据库。源码、公开 schema 与协议示例不应包含真实记录、私人导出、设备数据库、设备日志、签名密钥或令牌。`scripts/check_sensitive.py` 在本地检查可发布文件、暂存区及当前可达 Git 历史的常见凭据模式、私密文件路径和记录备份 JSON；明确放在测试/fixture 目录并命名为 synthetic 的 JSON fixture 可发布。脚本不上传内容，也不输出匹配到的秘密值。它不能识别任意自然语言中的私人信息，浅克隆也不包含上游更早历史；发布前仍需人工审查变更和将要发布的完整历史。

## English

Answers, dates and timestamps are stored in the app-private Room database. Local features need no account. No ads, telemetry or AI service. v0.4 requests network access for optional Google Tasks; it does not request location, contacts, camera, biometric/fingerprint or broad storage access. There is no additional app-level database encryption or password lock.

After an explicit connection, account identifiers/email distinguish local sync mappings. Only the third answer, source/task dates and completion are sent to Google Tasks. The other answers are not uploaded, no extra server is deployed, and tokens/content are not logged. Google components are governed by Google's terms. The app operates only on its own linked/marked tasks, although Google's tasks permission permits management of all tasks. Disconnection stops future sync and keeps both sides. Revoke access in the Google Account's third-party connections. See GOOGLE_TASKS.en.md for actual availability.

System backup is disabled. JSON exports are plaintext and include all answers and timestamps, excluding Google authorization, sync links, completion and preferences. The user chooses the destination; any selected cloud file provider has its own transport/storage terms. This app does not integrate Drive or automatically upload backups. Uninstalling does not delete exported files.

Widgets show the previous goal and today's first-answer summary, potentially visible to other people. Reminders are off by default and show generic text rather than answers. Autosave has a delay; confirm Saved after important edits. Time-zone changes do not move stored date keys.

Tests use synthetic data and isolated databases/preferences. Public source must not contain personal records, backups, serials, tokens or keys. The scanner runs locally and cannot identify every kind of private natural-language content or audit unavailable shallow history; review the published snapshot as well.
