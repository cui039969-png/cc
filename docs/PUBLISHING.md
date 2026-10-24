# 签名与发布 / Signing and publishing

## 私有材料 / Private material

签名私钥、口令、旧签名和轮换材料保存在仓库外，使用受限的本机目录并保留单独备份。不要把密钥、口令或个人备份上传 GitHub。
Keep signing keys, passwords, the legacy signer and rotation material outside the repository, in a restricted local directory with a separate backup. Never upload them or personal backups to GitHub.

维护脚本 `scripts/sign_release.ps1` 接受仓库外签名目录和 unsigned APK，先对齐，再使用旧＋新签名及 lineage 签名，最后核验。默认轮换起点 Android 13/API33；API26–32使用旧签名。轮换必须先在独立合成探针包验证，再覆盖安装生产应用。
The signing script accepts an external signing directory and unsigned APK, aligns it, signs with legacy/new keys and lineage, then verifies it. Rotation starts at API33; API26–32 use the legacy signer. Verify rotation with a separate synthetic probe before upgrading the production app.

目录文件名为 `legacy.keystore`、`release.p12`、`release-password.txt`、`lineage.bin`；新密钥别名 `daily3minutes`。这些文件只存在于维护者的私有目录，不随源码发布。备份在同一磁盘不能替代离线灾难恢复备份。
The script expects those four private files and the `daily3minutes` new-key alias. They are not shipped with source. A backup on the same disk is not an offline disaster-recovery backup.

## 发布顺序 / Release sequence

v0.5设备回归使用`./scripts/build.ps1 -Synthetic -DeviceTests`，安装ID为`io.github.daily3minutes.verification`。发布APK构建不加`SYNTHETIC_APPLICATION`，必须确认包名仍为`io.github.daily3minutes`。签名脚本默认输出通用候选文件；发布时明确传`-OutputApk`指定新版本文件，不覆盖历史发行包。

v0.5 device checks use the separate verification ID with `-Synthetic -DeviceTests`. Build the public APK without `SYNTHETIC_APPLICATION` and verify the production ID. Pass an explicit new versioned `-OutputApk` when signing; the default is a generic candidate and must not overwrite an old release.

1. 版本、中文英文说明、隐私和实际测试报告对应一致。测试版本不能标成正式版。
   Match versions, both language docs, privacy and actual test reports. Do not label betas stable.
2. 构建 Debug、Release，运行单元测试、Lint、合成设备测试与迁移测试；不清除真实记录。
   Build Debug/Release and run unit, lint, synthetic-device and migration tests without clearing real entries.
3. 运行敏感信息检查，打包源码，仅发布已审计快照，保留上游来源和许可。
   Scan for sensitive material and package an audited source snapshot with provenance and licenses.
4. 核对签名、包名、版本及非调试标志；升级探针成功后再升级手机。
   Verify signature, package, version and non-debuggable status, then upgrade the phone only after probe success.
5. 同一 GitHub Release 提供 APK、对应源码 ZIP、SHA256SUMS、双语更新说明和真实测试设备。
   Attach the APK, matching source ZIP, checksums, bilingual notes and actual tested devices to the same release.
6. 从 GitHub 重新下载并比对校验值和签名，再记录发布完成。Google 审核或联调未完成时明确保留测试阶段。
   Download again from GitHub, compare hashes/signature, then record completion. Keep the beta status while Google review or live testing is incomplete.

轮换后开发测试 APK 也必须保持兼容签名；默认旧 Debug 包未必能覆盖新签名，不能用卸载解决。
After rotation, development/test APKs also need compatible signing. An old default Debug APK may no longer upgrade the app; do not solve this by uninstalling.

维护者可使用 `./scripts/build.ps1 -DeviceTests -SigningDirectory <仓库外私有签名目录>`，让开发APK和测试APK都按相同lineage重新签名。此模式仍是Debug测试，不作为发行包；测试结束应重新覆盖安装已核验的Release包。
Maintainers can use the same command with an external private signing directory to sign both development and instrumentation APKs with the compatible lineage. These remain Debug test builds; reinstall the verified Release APK afterward.

## 公开证书指纹 / Public certificate fingerprints

以下是公开证书摘要，不包含私钥。包名为 `io.github.daily3minutes`。
These are public certificate digests, not private keys. Package: `io.github.daily3minutes`.

| 系统 / Platform | SHA-256 | SHA-1 (Google登记 / registration) |
| --- | --- | --- |
| Android 13+ / API33+ | `51f36b1985c6f65a5946bdb4691e74df864cf5b1c7271cd70357ee380321d0de` | `fa847deb9feb3e379d2f0bd13d666573e4b08359` |
| Android 8–12 / API26–32，旧签名兼容 / legacy compatibility | `be5d092c0e96d342af550eb542d6476f356f4b49a332d710ebcc18b70018d856` | `8102a06aa42563bd9bbd8ff99838011233f937e9` |

旧系统兼容签名来自早期Debug渠道，不等同于新正式密钥的私有保护强度。新安装及长期升级优先使用API33以上系统；不把历史Debug包作为正式发行。
The legacy compatibility signer comes from the earlier Debug channel and does not have the protection of the new private release key. Prefer API33+ for new installations and long-term upgrades; historical Debug packages are not official releases.
