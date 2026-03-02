# RNDIS Quick Toggle

通过 WiFi ADB 快速切换 Android 设备的 RNDIS（USB 网络共享）功能。

## 功能特性

- 一键切换 RNDIS USB 网络共享
- 创建桌面快捷方式，快速访问
- 添加通知栏快捷开关（Quick Settings Tile）
- 显示当前 RNDIS 状态
- 支持通过广播触发切换
- 无需 Root 权限，使用 WiFi ADB

## 系统要求

- Android 7.0 (API 24) 或更高版本
- WiFi ADB 连接（必需）
- 支持 RNDIS 的 Android 设备

## 构建说明

### 使用 GitHub Actions

项目已配置 GitHub Actions 自动构建，推送到 `main` 或 `develop` 分支时会自动触发构建。

### 本地构建

1. 克隆仓库：
```bash
git clone <repository-url>
cd short
```

2. 构建 Debug APK：
```bash
./gradlew assembleDebug
```

3. 构建 Release APK：
```bash
./gradlew assembleRelease
```

构建完成后，APK 文件位于 `app/build/outputs/apk/` 目录。

## 使用说明

### 前置准备

详细的 WiFi ADB 设置步骤请参考 [WiFi ADB 设置指南](WIFI_ADB_SETUP.md)

1. 启用开发者选项
2. 启用 USB 调试
3. 通过 USB 连接电脑
4. 在电脑上执行：`adb tcpip 5555`
5. 断开 USB，通过 WiFi 连接：`adb connect <设备IP>:5555`

### 应用配置

1. 安装 APK 到 Android 设备
2. 打开应用
3. 配置 ADB 连接信息（默认：127.0.0.1:5555）
4. 点击"Test Connection"测试连接
5. 点击"Save Settings"保存配置

### 使用方式

1. 点击"Toggle RNDIS"按钮切换 RNDIS
2. 点击"Create Shortcut"创建桌面快捷方式
3. 点击"Add Quick Settings Tile"添加通知栏快捷开关
4. 通过快捷方式或通知栏开关快速切换 RNDIS

### 添加通知栏快捷开关

- 点击应用中的"Add Quick Settings Tile"按钮
- 或者手动添加：
  1. 从屏幕顶部向下滑动打开通知栏
  2. 点击编辑图标（铅笔或加号）
  3. 找到"RNDIS Toggle"并拖动到快速设置面板
  4. 点击开关即可快速切换 RNDIS

## 技术栈

- Kotlin
- Android SDK 34
- Gradle 8.2
- Material Design Components

## 权限说明

- `CHANGE_NETWORK_STATE` - 修改网络状态
- `ACCESS_NETWORK_STATE` - 访问网络状态
- `INTERNET` - 网络访问（用于 WiFi ADB 连接）
- `ACCESS_WIFI_STATE` - 访问 WiFi 状态
- `STATUS_BAR_SERVICE` - 状态栏服务（用于快速设置 Tile）

## 注意事项

- 此应用需要 WiFi ADB 连接才能正常工作
- 使用前请确保已正确配置 WiFi ADB
- 不同设备的 RNDIS 实现可能有所不同
- 使用前请备份重要数据

## 许可证

MIT License
