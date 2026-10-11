# FloatingClock 悬浮时钟

安卓悬浮时钟：在其他应用之上实时显示 `时:分:秒`，可拖动、可调字号、自动记忆位置。零第三方依赖（纯 android.* API），APK 约 28KB。

## 构建

```powershell
$env:JAVA_HOME='D:\Tools\jdk-17'
D:\Tools\gradle-8.5\bin\gradle.bat -p D:\Tools\FloatingClock :app:assembleRelease --offline
```

产物：`app\build\outputs\apk\release\app-release.apk`（debug 证书签名）

## 安装

```powershell
adb install -r app-release.apk
```

## 使用

1. 打开 App → 点「开启悬浮时钟」→ 系统会要求「显示在其他应用上层」授权 → 授权返回后时钟自动出现
2. 按住时钟拖动 = 移动位置；单击时钟 = 显示/隐藏控制条（− / + 调字号，✕ 关闭）
3. 位置与字号自动记忆；通知栏「退出」彻底停止

## 技术要点

- 前台服务（`foregroundServiceType="specialUse"`，兼容 Android 14 / API 34 强制类型声明）
- 悬浮窗 `TYPE_APPLICATION_OVERLAY`，整秒对齐刷新（`1050 - now % 1000`，无累积误差）
- 数字 `fontFeatureSettings="tnum"` 等宽，秒跳时宽度不抖动
- minSdk 26（Android 8.0）起支持

> AI生成
