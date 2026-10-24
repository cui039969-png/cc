# 备份与恢复 / Backup and restore

1. 旧手机设置中选择“导出 JSON 备份”，选择可信位置。等待成功提示，核对记录数量和上次备份时间。
   On the old phone, choose Export JSON backup, select a trusted location and wait for success. Check the count and last-backup time.
2. 把文件传输到新手机，安装本应用，选择“导入 JSON 备份”。
   Transfer the file to the new phone, install this app and choose Import JSON backup.
3. 先查看新增和冲突日期；默认跳过已有日期，覆盖需要再次确认。坏文件整体拒绝；事务失败整体回滚。
   Preview added and conflicting dates. Existing dates are skipped by default; overwrite needs another confirmation. Invalid files are rejected in full and transaction failures roll back.
4. 恢复后重新设置语言、提醒，并在需要时连接 Google、核对任务关联。
   Set language and reminders again, then reconnect Google and check links if needed.

JSON 为明文，包含日期、三个回答、创建和更新时间。不包含 Google 授权、同步关联、任务完成状态及应用偏好。
JSON is plaintext and includes dates, three answers and creation/update timestamps. It excludes Google authorization, sync links, completion and app preferences.

格式保持 schemaVersion 1。限制为10MiB、10000条、每字段20000个Unicode码点。与上游 FrictionFreeDiary 的备份不兼容。
The format stays at schemaVersion 1: 10 MiB, 10,000 entries, 20,000 Unicode code points per field. It is not compatible with FrictionFreeDiary backups.

导出在保存当前编辑之后进行，文件成功关闭后才记为成功。失败文件可能不完整，应重新导出；取消不记为成功。不提供自动云备份，系统自动备份已关闭。
Export follows a save of current edits and records success only after the file closes. Failed files may be incomplete and should be exported again; cancellation is not success. No automatic cloud backup is provided and Android system backup is disabled.
