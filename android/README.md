# 汗牛充栋 Android 客户端

这是汗牛充栋的原生 Android 阅读客户端，面向安卓手机使用。

## 当前能力

- 原生 Kotlin + Jetpack Compose + Material 3 项目骨架
- Android 16 / API 36 编译目标
- Android 10 / API 29 最低支持
- 本地书籍导入入口
- 本地书架界面
- 阅读器基础设置界面
- OPDS 连接配置界面
- 纯 Kotlin 导入策略测试
- OPDS 1.2 Atom Feed 解析测试

## 打开方式

1. 使用 Android Studio 打开 `/Users/beibei/aiprojects/ai-book/android`
2. 安装 Android SDK Platform 36
3. 使用 JDK 17
4. 等待 Gradle 同步完成

## 常用命令

```bash
cd /Users/beibei/aiprojects/ai-book/android

# 运行单元测试
gradle test

# 构建 debug APK
gradle :app:assembleDebug
```

## OPDS 地址说明

安卓真机不能使用 `localhost` 访问电脑或 NAS。请在 OPDS 页面填写局域网地址，例如：

```text
http://192.168.1.100:8080/opds/
```

如果使用 Android 模拟器访问本机后端，可以使用：

```text
http://10.0.2.2:8080/opds/
```
