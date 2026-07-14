# 汗牛充栋 Android 客户端

这是汗牛充栋的原生 Android 阅读客户端，面向安卓手机使用。

## 当前能力

- 原生 Kotlin + Jetpack Compose + Material 3 项目骨架
- Android 16 / API 36 编译目标
- Android 10 / API 29 最低支持
- 本地导入并离线阅读 EPUB、TXT、HTML、Markdown、PDF、无 DRM 的 MOBI/AZW3
- 本地书架界面
- Markdown、MOBI、AZW3 统一使用现有文本阅读器的目录、主题、翻页、搜索、书签、高亮和进度
- PDF 支持连续分页、缩放、页码跳转、页书签和阅读进度恢复
- OPDS 连接配置界面
- 纯 Kotlin 导入策略测试
- OPDS 1.2 Atom Feed 解析测试

## 打开方式

1. 使用 Android Studio 打开 `/Users/beibei/aiprojects/ai-book/android`
2. 安装 Android SDK Platform 36 与 Android NDK `27.3.13750724`
3. 使用 JDK 17
4. 等待 Gradle 同步完成

## 常用命令

```bash
cd /Users/beibei/aiprojects/ai-book/android

# 运行单元测试
./gradlew test

# 构建 debug APK
./gradlew :app:assembleDebug
```

## 格式限制

- PDF 由 Android `PdfRenderer` 在设备上渲染；当前不支持加密 PDF、文字选择、高亮、批注和全文搜索。
- MOBI/AZW3 由随项目提供源码的 libmobi 0.12 在设备上解析，仅支持无 DRM 文件；KFX、AZW4 不在支持范围。
- 所有解析与阅读均可离线完成，不会上传书籍内容或加载书内远程资源。
- libmobi 来源、固定版本及 LGPL-3.0-or-later 许可证见 `third_party/libmobi/UPSTREAM.md` 与 `third_party/libmobi/COPYING`。

## OPDS 地址说明

安卓真机不能使用 `localhost` 访问电脑或 NAS。请在 OPDS 页面填写局域网地址，例如：

```text
http://192.168.1.100:8080/opds/
```

如果使用 Android 模拟器访问本机后端，可以使用：

```text
http://10.0.2.2:8080/opds/
```
