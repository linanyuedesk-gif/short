# Multi Countdown Android App

功能：
- 多个倒计时同时运行（默认 3 个，可继续添加）。
- 每个倒计时支持独立时长设置（分钟/秒）。
- 每个倒计时同时显示环形进度和数字时间。
- 每个倒计时支持独立提示音选择（运行时模拟生成，不依赖外部音频文件）。
- 所有倒计时均为循环模式：倒计时结束后自动从头开始。
- 手势控制：单击圆环暂停，双击圆环立即重头开始并继续运行。
- 全屏沉浸式显示。

## GitHub Actions
工作流文件：`.github/workflows/build.yml`

触发：
- push
- pull_request
- 手动触发 workflow_dispatch

产物：
- `app-debug-apk`（`app/build/outputs/apk/debug/app-debug.apk`）
