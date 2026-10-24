# 贡献 / Contributing

问题与改进请使用 GitHub Issues，注明版本、设备/系统、复现步骤、期望与实际结果。截图仅用合成数据。
Use GitHub Issues with version, device/OS, reproduction steps, expected and actual behavior. Screenshots must use synthetic data.

提交前检查开源来源和实际许可，保留上游声明。同步修改中文和英文文档。不要提交个人日记、备份、设备序列号、令牌或签名材料。
Review actual source licenses, preserve notices and update both language docs. Do not submit personal journals, backups, device serials, tokens or signing material.

运行 Debug构建、JVM测试和Lint。数据库变化增加真实迁移与测试；禁止 destructive fallback。影响组件、导入、同步或升级的更改需要对应合成设备/并发测试。
Run Debug build, JVM tests and lint. Schema changes require real migration tests, never destructive fallback. Widget, import, sync and upgrade changes need relevant synthetic-device/concurrency coverage.

发布前运行 `python scripts/check_sensitive.py --self-test` 与 `python scripts/package_source.py`。CI 与设备实际结果分别记录，不以未运行的测试代替通过证据。
Before release, run both scripts. Record CI and device evidence separately; unrun tests are not passes.
