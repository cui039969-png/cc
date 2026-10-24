# Windows 构建环境与设备审计
审计日期：2026-10-24（Asia/Shanghai）。

## 本机工具
| 项目 | 实测结果 |
|---|---|
| Windows | Windows11，10.0.26100.0，x64 |
| PowerShell | 7.6.5 |
| 内存 | 约15.2GiB；硬件虚拟化与Hypervisor可用 |
| JDK | Eclipse Temurin17.0.20.1+1；JAVA_HOME仅为构建进程设置 |
| Gradle | 8.13，Wrapper真实生成，固定distribution SHA256 |
| AGP/Kotlin/KSP | 8.13.2 /2.2.21 /2.2.21-2.0.4 |
| Android SDK | Platform35 revision2、Platform36 revision2 |
| Build Tools | 35.0.0 |
| Platform Tools | 37.0.1；ADB1.0.41 |
| SDK命令行工具 | 19.0（官方13114758 Windows包） |
| Android Studio | 常见安装位置和注册表未发现；本次使用命令行构建 |
| Emulator | 本次未安装/启动；不声称模拟器测试通过 |
| 磁盘 | 安装前C约224GiB、D约575GiB空闲，满足本次构建 |
| Maven连通性 | Google Maven的Compose BOM POM请求HTTP200；Gradle配置检查实跑成功 |

工具安装在 D:/Android 下专用目录，未卸载其他Java或修改全局环境变量。
首次获取工具遇到慢速和TLS中断，采用官方HTTPS源分片续传，完整ZIP解压成功。
JDK SHA256：e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0，与Adoptium API一致。
Gradle SHA256：20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78，与官方.sha256一致。
SDK命令行ZIP SHA256：98b565cb657b012dae6794cefc0f66ae1efb4690c699b78a614b4a6a3505b003（本机计算）。

## 命令与结果
- java -version：退出0。
- sdkmanager --licenses：退出0，构建所需SDK许可已接受。
- sdkmanager安装Platform35/36、Build Tools35.0.0、Platform Tools：退出0；逐项读取source.properties确认。
- Gradle Wrapper生成：退出0，BUILD SUCCESSFUL。
- gradlew --version：退出0。
- Gradle help配置检查：退出0，BUILD SUCCESSFUL。
- 完整APK、单测、Lint与设备测试结果见TEST_REPORT.md及docs/logs中的脱敏日志。
- 实际命令输出及退出码见[环境日志](logs/environment.txt)；用户名与设备序列号已替换。

## 设备连接与兼容
起初连接Redmi型号22127RK46C，Android16/API36，授权状态device。
物理分辨率1440×3200，当前覆盖分辨率1080×2400，字体1.0，Launcher com.miui.home，
版本RELEASE-6.01.05.2258-03201802。本项目包名此前未安装，未读取该机个人记录。

Platform Tools37 Windows默认USB后端未能识别该Redmi；设置进程级ADB_USB_LEGACY=1后恢复。
依据：https://developer.android.com/tools/releases/platform-tools （37.0.1 Windows说明）。
随后用户改接目标HONOR90并明确授权测试。Windows能识别HONOR90，但其WinUSB驱动注册了厂商接口GUID，
最初未暴露标准ADB接口。用户批准后，备份并为该手机的单个WinUSB接口补充标准ADB接口GUID，
重启该接口后ADB授权成功；没有替换驱动。备份和修复脚本仅存放于忽略的.local中，支持恢复。

目标荣耀90：REA-AN00，Android15/API35，MagicOS9.0.0，1200×2664，字体比例1.0。
桌面 com.hihonor.android.launcher/.unihome.UniHomeLauncher，版本16.0.20.221。
Debug APK实际安装成功，冷启动Status ok。后续设备与组件结果见TEST_REPORT.md。

## Git与隐私
上游提交ee79164ab5962a4ff9f61dca0c241aba7c74f654为浅克隆，仅本地可达历史接受扫描。
源代码获取后移除了origin，目前没有远程仓库；未创建GitHub仓库、推送或发布。
用户数据、签名密钥与原始设备日志不进入公开源代码；测试只使用虚构文本。
