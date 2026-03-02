# GitHub Actions 签名配置说明

为了在 GitHub Actions 中自动签名 Release APK，您需要在 GitHub 仓库的 Secrets 中配置以下密钥：

## 需要配置的 Secrets

1. **KEYSTORE_BASE64**
   - 将您的 keystore 文件转换为 Base64 编码字符串
   - 生成命令：`base64 -i release.keystore | pbcopy` (macOS) 或 `certutil -encode release.keystore output.txt` (Windows)

2. **KEYSTORE_PASSWORD**
   - 您的 keystore 密码

3. **KEY_PASSWORD**
   - 您的密钥密码

4. **KEY_ALIAS**
   - 您的密钥别名

## 如何生成 Keystore

如果您还没有 keystore，可以使用以下命令生成：

```bash
keytool -genkey -v -keystore release.keystore -alias my-key-alias -keyalg RSA -keysize 2048 -validity 10000
```

## 配置步骤

1. 在 GitHub 仓库页面，点击 `Settings` -> `Secrets and variables` -> `Actions`
2. 点击 `New repository secret`
3. 分别添加上述四个 secrets
4. 推送代码到 `main` 分支，GitHub Actions 将自动构建并签名 APK

## 注意事项

- 请妥善保管您的 keystore 文件和密码，不要泄露
- 一旦丢失 keystore 文件，您将无法更新已发布的应用
- 建议将 keystore 文件备份到安全的地方
