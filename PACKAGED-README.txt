Stock Lens Windows 版使用说明
==============================

1. 必须先完整解压 ZIP，不能直接在压缩包预览窗口中运行程序。
2. 优先双击“Stock Lens.exe”启动。如果出现 Failed to launch JVM，请改用“Start Stock Lens.cmd”。
3. 如仍无法启动，运行“Diagnose Startup.cmd”，窗口会显示具体缺失文件或 Java 错误。
4. 首次启动需要初始化 Java、AKShare 和本地数据库，可能需要 30-90 秒，请勿重复双击。
5. 启动完成后会自动打开浏览器；也可以手动访问 http://localhost:8082/。
6. 关闭浏览器不会退出应用。请在 Windows 右下角系统托盘找到 Stock Lens，点击 Exit 退出。
7. 用户数据保存在 %USERPROFILE%\.stock-lens\data，升级或替换应用目录不会删除数据。
8. 日志保存在 %USERPROFILE%\.stock-lens\logs。
   日志统一使用带 BOM 的 UTF-8 编码，可直接用 Windows 记事本打开。
9. 如果 Windows 防火墙询问网络访问，只需允许“专用网络”。本应用页面默认仅供本机访问。

行情、板块、业绩和排名日志无需配置 AI 密钥。
AI 研报功能需要由使用者自行配置 ZHIPU_API_KEY，分发包不会内置开发者密钥。
