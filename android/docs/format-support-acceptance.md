# Android 离线格式支持验收清单

## 自动化验证

- [x] MOBI、AZW3 扩展名与 MIME 类型可导入。
- [x] Markdown 标题分章、无标题正文、列表、引用、代码与链接可转换为阅读章节。
- [x] PDF 页码进度、恢复页约束、渲染尺寸与 Bitmap 缓存预算有单元测试。
- [x] PDF 页码、页面偏移与缩放值具备实体映射与存取单元测试。
- [x] MOBI/AZW3 DRM、损坏、缺失、空间不足错误映射为安全的用户提示。
- [x] MOBI/AZW3 解析结果按源文件 SHA-256 与解析器版本缓存，源文件变化后自动失效。
- [x] libmobi 原生库可为 arm64-v8a、armeabi-v7a、x86_64 构建。
- [x] Room 12→13 迁移 instrumentation 测试已使用真实 v12 导出 schema 编译并组装测试 APK。
- [x] 官方 libmobi v0.12 的 KF7、KF8/AZW3、多媒体、DRM 与损坏样本已校验来源和 SHA-256，并组装原生解析 instrumentation 测试 APK。
- [x] 运行时生成 PDF 的打开/渲染与损坏 PDF 失败路径已组装 instrumentation 测试 APK。
- [x] MOBI/AZW3 每次读取都会核对当前源文件 SHA-256；正文、图片和封面缓存均校验哈希，损坏后自动重建。
- [x] Markdown 导入与解析运行在 I/O dispatcher；扫描目录可离线复制相对图片，并限制为 128 个、总 64 MiB、单个 20 MiB。
- [x] PDF 缩放按倍率请求高分辨率页面，页内位置可按页恢复；三页渲染窗口与单图预算共同受堆内存预算约束。

> 已检测到 Android 设备，但设备以 `INSTALL_FAILED_USER_RESTRICTED` 拒绝安装测试 APK（0 个用例启动）；以上 instrumentation 测试仅确认编译与组装成功，尚未执行 connected tests。需在手机端允许通过 USB 安装/确认安装后重试。

## 真机冒烟验证

- [ ] 未加密 PDF：打开、连续上下翻页、双指缩放、页码滑杆、页书签、退出后恢复。
- [ ] Room 12→13 迁移在设备/模拟器上保留已有书籍与阅读进度。
- [ ] 密码 PDF：显示不支持密码保护的提示，不崩溃。
- [ ] Markdown：进入现有文本阅读器，目录、主题、搜索、书签、高亮和进度正常。
- [ ] Markdown 相对图片：通过“扫描目录”导入后离线显示；普通文件选择器对无法证明目录关系的资源安全跳过。
- [ ] 无 DRM KF7/MOBI：正文、标题分章和至少一张内嵌图片可见，第二次打开命中缓存。
- [ ] 无 DRM KF8/AZW3：正文、目录和至少一张内嵌图片可见，第二次打开命中缓存。
- [ ] DRM MOBI/AZW3：显示 DRM 不支持提示，不留下有效缓存。
- [ ] 删除 MOBI/AZW3 书籍后，对应 `files/parsed-books/<bookId>` 缓存被清理。

## 明确不在本版范围

DOC、DOCX、CBR、CBZ、KFX、AZW4；PDF 文字选择、高亮、批注、全文搜索、表单、签名和双页模式。
