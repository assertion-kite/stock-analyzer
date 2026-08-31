# Stock Lens 分发说明

## 推荐给普通用户：Windows 便携包

便携包内置 Java 运行时、AKShare 数据服务和 H2 本地数据库。用户不需要安装 Java、Python、MySQL 或 Maven。

构建机器首次准备：

```powershell
.\scripts\setup-akshare.ps1
.\.venv-akshare\Scripts\python.exe -m pip install pyinstaller
```

构建便携包：

```powershell
.\scripts\build-windows-distribution.ps1 -Version 1.0.0
```

输出目录：`target\distribution`

- `Stock-Lens-1.0.0-Windows-x64.zip`
- `Stock-Lens-1.0.0-Windows-x64.tar.gz`
- `Stock Lens\` 可直接运行的应用目录

发给朋友时优先发 ZIP。对方必须完整解压后再双击 `Stock Lens.exe`。如果原生启动器提示 `Failed to launch JVM`，可运行 `Start Stock Lens.cmd` 绕过原生启动器；`Diagnose Startup.cmd` 会显示具体错误。退出应用应使用 Windows 系统托盘中的 `Stock Lens > Exit`。

用户数据存储在：

```text
%USERPROFILE%\.stock-lens\data
```

日志存储在：

```text
%USERPROFILE%\.stock-lens\logs
```

升级应用时直接替换应用目录即可，不会覆盖用户数据。

## EXE 安装包

安装 WiX Toolset 3.x，并确保 `candle.exe` 和 `light.exe` 在 PATH 中，然后执行：

```powershell
.\scripts\build-windows-distribution.ps1 -Version 1.0.0 -Installer
```

安装器采用当前用户安装模式，不要求管理员权限，并创建桌面快捷方式和开始菜单入口。

## AI 功能配置

行情、板块和排名日志无需 AI 密钥。AI 研报功能需要在启动应用前配置 `ZHIPU_API_KEY`。不要把个人密钥打进公开分发包。

## 系统范围

当前构建脚本生成 Windows x64 包。macOS 和 Linux 需要分别在对应系统上执行平台专用打包，`jpackage` 不能跨平台生成安装包。
