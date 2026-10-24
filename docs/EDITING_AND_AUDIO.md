# 独立编辑与录音 / Full-page editing and audio

每天的三张问题卡片分别打开独立编辑页。上方月份可前后切换，日期行可左右滑动；点日期会先保存当前回答，再打开对应日期。日期下面的三个页签切换问题。返回先回到当天概览。

Each question card opens its own full-page editor. Use the month controls and horizontal day rail to select a date. Current edits are saved before switching dates or question tabs. Back returns to the day's overview.

长文在单个编辑区内滚动。横屏、键盘打开或大字体造成空间不足时，整页也可以上下滚动，保留至少260dp的编辑区域。每个回答仍最多20000个Unicode码点；请确认“已保存”。

Long answers scroll inside the editor. On short screens or at large font sizes, the page also scrolls and keeps a 260dp editor. The limit remains 20,000 Unicode code points per answer. Confirm the Saved status after important changes.

## 两种语音功能 / Two voice features

| 功能 / Feature | 使用方式 / Behavior |
| --- | --- |
| 开始录音 / Record audio | 主动点击后请求麦克风权限；保存可回放的本机AAC/M4A附件，绑定开始时的日期和问题 / Requests microphone permission only after a tap; saves a playable local AAC/M4A attachment linked to the original date and question |
| 语音转文字 / Dictate text | 调用设备的系统识别服务，结果先预览，再追加到回答；不覆盖原文 / Uses the system recognition service; review the result before appending it without replacing existing text |

录音最长10分钟、单文件最多20MiB。结束、切换问题或日期、离开应用时会结束并尝试保存；过短、权限被撤销或空间不足可能保存失败，界面会提示。没有后台常驻录音。录音列表可以回放、停止和确认删除；删除单日记录会同时删除该日录音。

Clips are limited to 10 minutes and 20 MiB each. Stopping, changing question/date or leaving the app ends capture and attempts to save it. Very short clips, revoked permission or insufficient storage can fail, with an error message. There is no background recording service. Expand Audio to play, stop or confirm deletion. Deleting a day also deletes its audio.

录音文件不会自动转写，系统识别也不会自动保存一段音频；这两个按钮分别提供所需能力。系统识别会优先请求离线，但服务提供方可能忽略该请求并联网。没有可用识别服务时保留打字，并提示使用键盘话筒。拒绝麦克风权限不影响文字记录。

Saved audio is not automatically transcribed, and system dictation does not automatically save an audio attachment. The two buttons provide separate capabilities. Offline recognition is requested, but the provider may use the network. If unavailable, typing remains available and the app suggests the keyboard microphone. Denying microphone access does not block typing.

没有弹窗式识别入口时会改用系统RecognitionService，并显示“正在听写”，可结束或取消，最长等待30秒；离开应用取消识别。系统服务本身是否支持当前语言、离线模型和任意第三方应用，由设备提供方决定。

Without a recognition Activity, the app uses the system RecognitionService with a Listening dialog, Finish and Cancel, and a 30-second limit. Leaving the app cancels recognition. Supported languages, offline models and access by third-party apps depend on the device's provider.

## 桌面组件 / Home-screen widget

上栏保留最近过去日期的交接，下栏是今天。下栏可上下滑动查看多行文字；点正文打开对应问题，点“更多”选择问题后录音或转文字。建议把组件拉高到三行或更高。两行高度仍可用，阅读区域较小。

The upper panel keeps the previous handoff; the lower panel shows today. Scroll the lower panel vertically to read multiline answers. Tap answer text to open its question; More opens question choices for recording or dictation. A three-row or taller widget gives more reading space; compact two-row layouts remain supported.

Android桌面长按由启动器用于移动、调整大小或移除组件。应用用明确的“更多”入口提供快捷操作。组件每问最多展示前4000个码点，并显示打开全文入口，避免超出系统组件传输限制；应用保存的正文没有因此截断。

The launcher owns long-press for moving, resizing or removing widgets. App actions use the explicit More entry. A widget displays up to 4,000 code points per question, with an Open the app link for longer answers, to bound system widget payloads. Stored answers are not truncated.

录音请使用完整ZIP备份；原有JSON仅保存文字。详见[备份恢复 / Backup and restore](BACKUP.md)。
