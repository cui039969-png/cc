# 备份与恢复 / Backup and restore

1. 有录音时选择“导出完整备份（文字和录音）”；只需文字时可选“导出 JSON 备份”。选择可信位置，等待成功提示，核对日期数量、上次备份时间和类型。
   Choose Export full backup (text and audio) for recordings, or Export JSON backup for text only. Select a trusted location and check the date count, last-backup time and format after success.
2. 把文件传输到新手机，安装本应用，按文件类型选择“恢复完整备份（ZIP）”或“导入 JSON 备份”。
   Transfer the file to the new phone and choose Restore full backup (ZIP) or Import JSON backup to match its format.
3. 先查看新增和冲突日期；默认跳过已有日期，覆盖需要再次确认。坏文件整体拒绝；事务失败整体回滚。
   Preview added and conflicting dates. Existing dates are skipped by default; overwrite needs another confirmation. Invalid files are rejected in full and transaction failures roll back.
4. 恢复后重新设置语言、提醒，并在需要时连接 Google、核对任务关联。
   Set language and reminders again, then reconnect Google and check links if needed.

JSON 为明文，包含日期、三个回答、创建和更新时间。不包含 Google 授权、同步关联、任务完成状态及应用偏好。
JSON is plaintext and includes dates, three answers and creation/update timestamps. It excludes Google authorization, sync links, completion and app preferences.

v0.5完整ZIP包含原有schemaVersion 1的`journal.json`、录音元数据`manifest.json`以及`recordings/<UUID>.m4a`。最多1000段录音，音频合计512MiB，单段20MiB。也支持只有录音、没有文字的日期。ZIP不是加密文件，和JSON一样需要妥善保管；不包含Google授权、任务关联、完成状态或应用偏好。

The v0.5 full ZIP contains the unchanged schemaVersion 1 `journal.json`, recording metadata in `manifest.json`, and `recordings/<UUID>.m4a`. It supports up to 1,000 clips, 512 MiB of audio total and 20 MiB per clip, including audio-only dates. ZIP is not encrypted. It excludes Google authorization, task links, completion and app preferences.

完整恢复默认跳过冲突日期及其录音。明确覆盖时替换该日文字，并按录音ID合并，保留本机已有音频；相同ID但元数据不同会拒绝。检查路径、大小与SHA-256后先准备音频文件，再用一个数据库事务提交文字和录音元数据。正常导入异常会回滚数据库并删除新文件；强制终止/掉电后的孤立文件会整理，但不声称文件系统与数据库在掉电时具备联合原子提交。

Full restore skips conflicting dates and their audio by default. Confirmed overwrite replaces text and merges recording IDs while retaining existing local audio. A reused ID with different metadata is rejected. Paths, sizes and SHA-256 are checked before files are staged and text/audio metadata commit in one database transaction. Ordinary import failures roll back metadata and remove new files. Orphan files from abrupt termination are reconciled; a crash-atomic transaction across both filesystem and database is not claimed.

格式保持 schemaVersion 1。限制为10MiB、10000条、每字段20000个Unicode码点。与上游 FrictionFreeDiary 的备份不兼容。
The format stays at schemaVersion 1: 10 MiB, 10,000 entries, 20,000 Unicode code points per field. It is not compatible with FrictionFreeDiary backups.

导出在保存当前编辑之后进行，文件成功关闭后才记为成功。失败文件可能不完整，应重新导出；取消不记为成功。不提供自动云备份，系统自动备份已关闭。
Export follows a save of current edits and records success only after the file closes. Failed files may be incomplete and should be exported again; cancellation is not success. No automatic cloud backup is provided and Android system backup is disabled.
