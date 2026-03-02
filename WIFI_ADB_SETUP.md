# WiFi ADB 设置指南

本指南将帮助您设置 WiFi ADB 连接，以便使用 RNDIS Quick Toggle 应用。

## 什么是 WiFi ADB？

WiFi ADB 允许您通过 WiFi 网络而不是 USB 线缆连接 Android 设备进行 ADB 调试。这使得应用可以在没有 Root 权限的情况下执行系统命令。

## 前置要求

- 一台 Android 设备（Android 7.0 或更高版本）
- 一台电脑（Windows、macOS 或 Linux）
- USB 数据线
- Android SDK Platform Tools（包含 `adb` 命令）

## 设置步骤

### 1. 启用开发者选项

1. 打开设备的"设置"
2. 进入"关于手机"
3. 连续点击"版本号" 7 次，直到看到"您已处于开发者模式"的提示

### 2. 启用 USB 调试

1. 返回"设置"主界面
2. 进入"开发者选项"
3. 找到"USB 调试"并启用它

### 3. 通过 USB 连接设备

1. 使用 USB 线将设备连接到电脑
2. 在设备上授权电脑进行 USB 调试
3. 在电脑上打开终端/命令提示符
4. 输入以下命令验证连接：
   ```bash
   adb devices
   ```
   您应该看到您的设备在列表中

### 4. 启用 TCP/IP 模式

在电脑上执行以下命令：
```bash
adb tcpip 5555
```

您应该看到类似以下的输出：
```
restarting in TCP mode port: 5555
```

### 5. 获取设备 IP 地址

1. 在设备上打开"设置"
2. 进入"网络和互联网" > "WiFi"
3. 点击当前连接的 WiFi 网络
4. 记下显示的 IP 地址（例如：192.168.1.100）

### 6. 通过 WiFi 连接

1. 断开 USB 线
2. 在电脑上执行以下命令（替换为您的设备 IP）：
   ```bash
   adb connect 192.168.1.100:5555
   ```

3. 验证连接：
   ```bash
   adb devices
   ```

您应该看到您的设备在列表中，状态为 `device`。

### 7. 配置应用

1. 打开 RNDIS Quick Toggle 应用
2. 在"ADB Host"字段中输入设备 IP 地址（127.0.0.1 表示本机）
3. 在"ADB Port"字段中输入端口号（默认 5555）
4. 点击"Test Connection"测试连接
5. 如果连接成功，点击"Save Settings"保存配置

## 常见问题

### 连接失败

**问题：** "ADB not connected"

**解决方案：**
1. 确认设备和电脑在同一 WiFi 网络
2. 检查防火墙设置
3. 尝试重新连接：`adb disconnect` 然后 `adb connect <IP>:5555`
4. 重启 ADB 服务：`adb kill-server` 然后 `adb start-server`

### 端口被占用

**问题：** 无法绑定到端口 5555

**解决方案：**
1. 使用其他端口：`adb tcpip 5556`
2. 在应用中更新端口号

### 连接不稳定

**问题：** 频繁断开连接

**解决方案：**
1. 确保设备保持唤醒状态
2. 检查 WiFi 信号强度
3. 尝试使用 5GHz WiFi 网络

## 自动连接脚本

### Windows (PowerShell)

创建 `connect-adb.ps1`：
```powershell
$deviceIP = "192.168.1.100"
$port = 5555
adb connect "$deviceIP`:$port"
adb devices
```

### macOS/Linux (Bash)

创建 `connect-adb.sh`：
```bash
#!/bin/bash
DEVICE_IP="192.168.1.100"
PORT=5555
adb connect $DEVICE_IP:$PORT
adb devices
```

## 安全建议

1. 仅在受信任的网络中使用 WiFi ADB
2. 使用完成后禁用 USB 调试
3. 定期更改端口号
4. 不要在公共 WiFi 网络中使用

## 恢复 USB 连接

如果需要恢复 USB 连接，只需：
1. 使用 USB 线连接设备
2. 在电脑上执行：`adb usb`
3. 验证连接：`adb devices`

## 高级配置

### 自定义端口

```bash
# 使用自定义端口
adb tcpip 5556
adb connect 192.168.1.100:5556
```

### 多设备连接

```bash
# 连接多个设备
adb connect 192.168.1.100:5555
adb connect 192.168.1.101:5555

# 列出所有设备
adb devices

# 指定设备执行命令
adb -s 192.168.1.100:5555 shell
```

## 故障排除

### 查看日志

```bash
# 查看 ADB 日志
adb logcat | grep adb

# 查看网络连接
adb shell netstat -an | grep 5555
```

### 重置 ADB

```bash
# 完全重置 ADB
adb kill-server
adb start-server
```

## 参考资源

- [Android 官方文档 - Wireless Debugging](https://developer.android.com/studio/command-line/adb#wireless)
- [ADB 命令参考](https://developer.android.com/studio/command-line/adb)

## 支持

如果遇到问题，请检查：
1. 设备和电脑是否在同一网络
2. 防火墙是否阻止连接
3. 设备是否启用了 USB 调试
4. ADB 版本是否最新
