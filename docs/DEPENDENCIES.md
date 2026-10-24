# 固定依赖与兼容依据

## 0.4.0 补充 / Additions

Credential Manager及Google适配器1.5.0、Google ID1.1.1、Google Play services Auth21.4.0、Coroutines Play Services1.9.0已固定在版本目录。Google组件按其服务条款提供，不宣称所有二进制依赖均开源。未增加Drive、广告、遥测、服务器或通用备份框架。
The versions above are pinned in the catalog. Google components use their service terms and are not described as entirely open source. No Drive, ads, telemetry, backend or general backup framework was added.

配置以 gradle/libs.versions.toml 为唯一版本来源。

初始选择：AGP8.13.2 / Gradle8.13 / Kotlin2.2.21 / KSP2.2.21-2.0.4 / JDK17，
Compose BOM2025.10.01、Room2.8.5、Glance1.2.0、WorkManager2.10.1。
Core1.15.0、Lifecycle2.8.7、Activity1.9.3、Coroutines1.9.0、Serialization1.7.3 延续上游的已固定原生基线；
Material Icons固定1.7.6，仅用矢量图标。移除 SQLCipher、生物识别、Security Crypto、Coil、
Navigation Compose与多平台Adaptive依赖。

compileSdk36，targetSdk35，minSdk26。compileSdk升级用于现代AndroidX的AAR元数据兼容；
target仍维持Android15行为，未升级到AGP9。若实际构建发现基线版本不可获取/不兼容，
修复后的固定版本与确切原因追加在下方，不能将官方页面上列出的版本视作本机已验证。

官方依据：
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://github.com/google/ksp/releases/tag/2.2.21-2.0.4
- https://developer.android.com/jetpack/androidx/releases/room
- https://developer.android.com/jetpack/androidx/releases/glance
- https://developer.android.com/develop/ui/compose/bom
- https://developer.android.com/develop/background-work/services/alarms

2026-10-24 实际构建记录：最初尝试 BOM2026.09.00，其解析出的 Compose1.12.1 AAR 要求 compileSdk37 和 AGP9.1，
与保留 AGP8 的约束冲突。改为固定 BOM2025.10.01，并使用 compileSdk36；targetSdk35 保持不变。
0.1.0 的 assembleDebug、34 项 JVM 单测、lintDebug 均真实通过；0.2.0 增加到46项JVM单测及28项设备测试。
Lint 的新版本提示不影响这一已验证组合，详见 TEST_REPORT.md。

本机 JDK17 测试工作进程曾无法从中文类路径加载测试类。使用指向同一源码目录的英文 Junction 后恢复；
scripts/build.ps1 仅在该 Junction 的目标与当前工程一致时复用。其他机器可把工程解压到英文目录构建。
本机两项大体积编译器依赖采用官方源分片下载并校验发布者散列，保存在忽略的 .local 中；公开构建配置仍使用官方 Maven 仓库。

## 0.2.0 构建补充

本轮保留原有依赖版本。Windows工作目录/Junction中，Kotlin增量编译两次未解析未改动文件的顶层符号；Gradle clean后的全量编译正常。
因此工程设置 `kotlin.incremental=false`，以全量Kotlin编译保证重复修改可构建；Gradle任务缓存仍开启。代价是改动Kotlin时编译耗时增加。

## 0.3.0 构建补充

没有新增或升级第三方依赖。历史抽屉、吸顶列表和滚动状态使用现有Material3/Foundation；Calendar与Tasks.org只作对照，未引入其依赖或GPL实现。
原有构建基线实际通过54项JVM、37项荣耀设备测试及lintDebug，详见本轮测试报告。
