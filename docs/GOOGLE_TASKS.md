# Google Tasks 接入与状态

[English](GOOGLE_TASKS.en.md)

## 当前状态

代码已经包含 Credential Manager 登录、Google AuthorizationClient 授权、任务请求、双向合并、离线队列和冲突界面。公开 Google Cloud 项目、客户端 ID、审核及真实账号联调尚未完成。未配置客户端的安装包明确禁用连接，不模拟成功。普通用户无需创建开发项目；本项目的发布者完成配置后再发布可连接版本。

## 用户操作

设置 → Google Tasks → 连接 → 预览近七天或全部目标 → 选择账号并授权。仅第三个回答及完成状态同步到独立 `Daily 3 Minutes` 列表，其他两个回答留在本地。任务说明中有来源日期标记，用于重试和重新关联，不包含另两个回答。

Google 的 `tasks` 权限允许管理所有任务，授权页面会如实这样显示；本应用只处理自己的标记和关联任务。当前不接入 Drive，不请求 Drive 权限。

断开连接保留本地和云端内容。撤销 Google 授权可在 Google 账号的“第三方应用和服务”中移除此应用。删除云端任务保留日记；清空或删除本地记录先解除关联，云端删除另行确认。

双方改动同一目标时展示两份供选择。恢复 JSON 后重新连接并核对日期关联，不能把任务同步当成完整日记备份。超出 1024 字符的目标不上传，不截断；修改任务日期不移动日记。

## 发布者配置

1. 在用户自己的 Google Cloud 账号新建独立项目，启用 Google Tasks API。
2. 填写应用名称、支持联系方式、首页、隐私政策、授权用途和外部用户范围；提供实际可访问且按 Google 要求验证所有权的页面。
3. 登记包名 `io.github.daily3minutes` 及实际 APK 的 SHA-1 签名。轮换包在 Android 13 以上使用新签名、旧系统使用旧签名，应登记两者。
4. 建立 Web 客户端供 Credential Manager 标识应用；本应用不部署服务器，不嵌入客户端密钥或刷新令牌。
5. 构建时使用 `-PGOOGLE_WEB_CLIENT_ID=<公开客户端ID>`；该 ID 不是密码。OAuth 客户端密钥、账号令牌、签名私钥不得进入仓库。
6. 先在测试范围内用专门合成任务验证，准备审核材料。通过所需审核和实测之前保留测试版标记。

自行构建者需要登记自己的签名和客户端，不应冒用维护者的应用身份。缺少配置不影响本地功能。

## 参考

[Android 官方授权指南](https://developer.android.com/identity/authorization)、[Tasks 权限](https://developers.google.com/workspace/tasks/auth)、[任务字段限制](https://developers.google.com/workspace/tasks/reference/rest/v1/tasks)、[公开授权审核](https://developers.google.com/identity/protocols/oauth2/production-readiness/sensitive-scope-verification)。
