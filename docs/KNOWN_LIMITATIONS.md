# v0.4 测试阶段已知限制 / Beta limitations

- Google公开客户端、审核和真实账号双向联调未完成；未配置的安装包不能连接Google。
  Public Google configuration, review and live two-way integration are incomplete; unconfigured builds cannot connect.
- 完整备份仅手动JSON；不包含Google关联、完成状态和偏好，不提供Drive备份。
  Full backup is manual JSON, excluding Google links, completion and preferences. No Drive backup.
- 正式发行、公开下载核对和生产应用签名轮换尚待完成，以当前测试报告为准。
  Formal release, public download verification and production key rotation remain pending; consult the current report.
- 荣耀90合成设备回归42项及JVM81项已通过；Google真实授权与云端联调仍未完成。
  Honor 90 synthetic-device regression (42 tests) and JVM tests (81) passed; live Google authorization and cloud integration remain incomplete.

## 以下为0.3历史限制，需结合当前报告 / Historical v0.3 limitations
- 自动保存防抖 800ms；进程在防抖内被终止仍可能丢失未提交输入。页面显示状态，提供即时保存。
- JSON 导出是明文，用户应选择可信存放位置。应用没有密码锁或数据库额外加密。
- 双栏桌面组件展示交接目标与今天回答摘要，旁人可见。
- MagicOS 及其他系统可能延迟日期组件刷新与每日通知；不使用精确闹钟或常驻前台服务。
- 已保存的全空记录会保留日期及元数据并显示“空记录”；显式删除仍需确认。
- 首版使用独立包名和 schema v1；不兼容原 FrictionFreeDiary 数据库/备份。
- 当前尚未公开源码、正式签名或应用商店分发，未配置线上升级服务。Debug APK 不构成长期升级渠道。
- 设备测试状态以 TEST_REPORT.md 实际证据为准，未测项目不能视为通过。
- 荣耀90已测2×2/4×2及冷启动入口；未测Pixel/模拟器、TalkBack逐项朗读、Launcher系统大字号、真实午夜/开机恢复和长期省电延迟。
- 已通过一次真实非精确闹钟通知投递及受控系统事件恢复测试；这不能替代物理重启、真实午夜或长期省电策略测试。
- Lint为0错误、36警告、2提示，包含依赖版本、KTX风格及未用资源建议；正式Release签名、商店安装和跨schema升级尚未验证。
- 0.2.0新增的前台跨日补偿、月末/夏令时与推迟恢复已有注入Clock测试；不等于实际等候午夜或物理重启。宽屏配置回归在荣耀设备上隔离覆盖尺寸，不等于每种平板/Launcher均实测。
- 0.3.0双栏组件在荣耀设备的真实原生宿主验证140×150/280×150dp和今日实时更新；本轮未截取可能含私人内容的实际Launcher。近7天包含今天；全部模式不隐藏预写未来日期。没有新增任务完成勾选或独立任务数据模型。
