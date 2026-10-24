# 0.2.0 实测报告

日期：2026-10-24，Asia/Shanghai。包名 io.github.daily3minutes，versionCode2，versionName0.2.0。
测试只使用虚构记录，未提取用户数据库或个人回答。

## 当前版本结论

| 验证 | 实际结果 |
|---|---|
| Debug APK 构建 | PASS，实际生成 21,051,022 bytes |
| JVM | 46/46 PASS，0失败、0跳过 |
| 荣耀90 instrumentation | 28/28 PASS，0失败、0跳过，实际53.333秒 |
| Lint | PASS，0 errors、36 warnings、2 hints；未删除检查来隐藏警告 |
| 签名 | 当前APK的v2签名验证通过 |
| 覆盖升级 | 实际0.1.0→0.2.0，专用虚构私有文件保留并清理；不是读取用户记录的证明 |
| 安装与冷启动 | 覆盖安装成功，正常应用冷启动成功；见日志 |
| 远程CI/正式Release | NOT TESTED；本地源码未发布 |

APK SHA-256：`254f58b5c5753e311c2ee2bac13edacd0e461e66c6ae8b63b13c242717731b24`。
交付文件：`.local/delivery/daily-3-minutes-0.2.0-debug.apk`。
同一Debug签名、applicationId、Room schema1及JSON schema1继续使用，没有数据库schema变更。

## 本轮需求与改正

通过已连接的GitHub实际比较六个项目的许可和相关文件，逐项取舍见 [开源调研](OPEN_SOURCE_REVIEW.md)。
本轮增加以下12项JVM和4项真机页面回归，没有删除原34+24项测试：

| 改进 | 本轮证据 |
|---|---|
| 长交接可展开、收起、查看来源 | 真机页面操作展开/收起并进入来源日期；当天待保存输入实际提交到隔离Room |
| 搜索摘要直接显示命中回答 | 真机对数千字首问、第二问连续300换行及特殊%_中文搜索，实际显示第二问命中摘要；JVM6项覆盖第三问、字面符号、大小写、Emoji边界及空记录 |
| 宽横屏侧栏 | 真机使用820×360dp测试配置，检查今日/历史/设置均可见，跨页保存并重开；截图复查后修正矮窗口导航裁切 |
| 版本及开源入口 | 真机检查完整版本文本，展开来源列表，实际滚动到Now in Android及Quillpad；没有自动打开浏览器或上传内容 |
| 前台跨日补偿 | 接入STARTED期间的日期检查，后台停止；Clock测试覆盖月末提交、历史编辑/浏览月不跳走、备份推迟后的恢复 |
| 本地日边界 | JVM3项验证上海月末1ms边界、纽约23小时和25小时夏令时日；不假定一天永远24小时 |

JVM分组：BackupCodec15、DailyRules5、DayBoundary3、HistoryPreview6、ReminderTimes6、EditorConcurrency11。
真机分组：Repository8、独立文件关闭重开1、Editor UI7、原生组件宿主3、入口4、隐私默认值2、提醒与系统恢复3。

设备仍为荣耀90 REA-AN00，Android15/API35、MagicOS9.0.0，默认1200×2664/字体1.0。
测试配置覆盖只改变隔离页面尺寸或字体，不改手机全局显示设置。

## 核心验收范围

本轮完整重跑了数据、备份、并发保存、页面、原生组件及提醒测试。原始24条需求的逐项实测细节保存在
[0.1.0完整验收矩阵](TEST_REPORT_0_1_0.md)，其中荣耀桌面手动操作、SAF系统选择器往返、真实闹钟观测属于上版实测。
本轮没有把旧的手动证据伪装成新的操作；新增内容见上表。

- T01–T07：本轮重跑保存、日隔离、元数据、空记录、并发、文件重开与交接规则。覆盖安装另验证私有虚构文件保留；没有schema迁移，也没有读取个人记录比较。
- T08–T11：本轮重跑真实AppWidgetHost紧凑/宽版、长中文、两个入口和增改删导入后的渲染。荣耀Launcher2×2/4×2与多实例手动证据来自0.1.0，组件代码未在本轮更改。
- T12：受控调用真实系统Receiver的五种恢复分支仍通过；新增Clock与前台补偿。实际午夜/物理重启仍NOT TESTED。
- T13–T17：本轮重跑历史编辑/跨月、三字段搜索、JSON严格校验、冲突/幂等、事务回滚；SAF系统界面深度往返沿用0.1.0证据。
- T18–T20：本轮重跑权限/隐私默认值和入口；合并Manifest继续没有INTERNET。横屏配置测试新增侧栏与保存；未切飞行模式或遍历所有键盘。
- T21：本轮重跑2倍字体、原生无障碍三问名称及编辑动作。TalkBack实际朗读和Launcher系统大字号仍NOT TESTED。
- T22：本轮重跑真实NotificationManager发通知、同日去重、过期/未到期拒绝及恢复安排。0.1.0实际AlarmManager投递晚4694ms，未在本轮重复或推断长期可靠。
- T23–T24：本轮交付前重新扫描可发布工作文件、暂存区和本地可达Git历史；真实APK、散列、签名和安装均核验。正式签名和远程发布仍未执行。

## 失败修复与边界

增量编译两次未解析未改动文件的顶层符号，全量编译可通过；保留失败日志，设置kotlin.incremental=false后重新构建和回归，见DEPENDENCIES。没有移除主题或测试。
首次版本断言把局部版本号当成整段文本，实际节点为“每天3分钟  0.2.0”；改成精确断言完整文本后重跑全部28项。
截图复查发现矮横屏的第三个导航入口不完整，修正侧栏间距及系统栏重复留白，增加切页前后三个文字入口均可见断言，再次完整构建和回归。

Debug仍为试用签名。真实午夜、物理重启、TalkBack朗读、Launcher大字号、长期省电提醒及正式升级渠道未完成验收，见 [已知限制](KNOWN_LIMITATIONS.md)。

## 本轮日志与截图

[构建和28项设备测试](logs/verification-v020.txt) · [46项JVM](logs/unit-tests-v020.txt) ·
[覆盖升级](logs/upgrade-v020.txt) · [签名](logs/signature-v020.txt) · [冷启动](logs/cold-start-v020.txt) · [公开内容检查](logs/privacy-v020.txt)

- [长交接展开](screenshots/v020-handoff-expanded.png)
- [命中第二问的搜索](screenshots/v020-search-match.png)
- [宽横屏导航](screenshots/v020-wide-navigation.png)
- [开源来源列表](screenshots/v020-open-source.png)

收尾：保留日常应用和已有桌面组件；隔离测试的截图缓存、测试APK及临时宿主权限清理，提醒权限由脚本恢复。
本轮未改变手机全局时间、字号、方向锁定或USB常亮设置。
