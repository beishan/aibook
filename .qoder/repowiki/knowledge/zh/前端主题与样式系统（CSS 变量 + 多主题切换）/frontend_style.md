## 1. 体系概览
- 技术栈：Vue3 + Vite + TypeScript，UI 组件库使用 Element Plus，状态管理用 Pinia。
- 样式方案：以 CSS 自定义属性（CSS Variables）为核心的“设计令牌”体系，通过 data-theme 属性在根节点切换主题，实现运行时主题热切换。无 SCSS/Less/Tailwind 等预处理或原子化框架。

## 2. 核心文件与职责
- frontend/src/styles/base.css：全局基础样式与通用 UI 组件类（按钮、输入框、开关、标签、对话框、选项卡、消息提示、滚动条、动画等），全部基于 --spacing-*、--font-size-*、--radius-*、--shadow-*、--text-*、--surface-* 等基础 token。这些 token 定义在 :root 下，不随主题变化。
- frontend/src/styles/themes.css：按 [data-theme="modern" | "warm" | "natural"] 覆盖主题相关 token（主色、背景、阴影、毛玻璃参数等），每个主题块完整重定义一套视觉变量，确保主题间完全隔离。
- frontend/src/types/theme.ts：主题类型与元数据声明（ThemeId/LayoutType/ThemeDefinition），集中维护三个内置主题及其布局映射。
- frontend/src/stores/theme.ts：Pinia store，负责读取 localStorage、设置 document.documentElement.dataset.theme、计算当前主题定义及对应布局。
- frontend/src/layouts/ThemeLayoutWrapper.vue：根据当前主题的 layout 字段动态异步加载 Dock/Sidebar/Topbar 三种布局组件。
- frontend/src/main.ts：应用入口，顺序引入 base.css → themes.css，初始化 Pinia 后调用 themeStore.initTheme() 完成首屏主题恢复。
- frontend/package.json：依赖 Element Plus，并直接引入其默认样式；未配置任何 CSS 预处理器或 Tailwind。

## 3. 架构与约定
- 令牌分层：
  - 基础层（base.css）：间距、字号、字体族、过渡时长、圆角全值等“中性”token，所有主题共享。
  - 主题层（themes.css）：颜色、渐变、阴影、毛玻璃、导航背景等“可替换”token，按 data-theme 分组覆盖。
- 主题切换机制：theme store 将主题 id 写入 document.documentElement.dataset.theme，浏览器选择器自动匹配对应 CSS 块，配合 .theme-transition, .theme-transition * 的 transition 规则实现平滑过渡。
- 布局与主题绑定：每个主题在 ThemeDefinition 中声明 layout（sidebar/topbar/dock），ThemeLayoutWrapper 据此渲染不同外壳布局，使“主题=视觉+布局”的组合成为一等概念。
- 组件样式策略：页面级组件优先复用 base.css 提供的通用类（如 .btn、.input、.dialog、.tabs、.message），避免在每个 Vue 组件内重复写样式；Element Plus 作为补充组件库，其默认样式通过 element-plus/dist/index.css 全局引入。
- 持久化：当前主题 id 保存在 localStorage 键 ai-book-theme，应用启动时由 initTheme 恢复。

## 4. 开发者应遵循的规则
- 新增/修改主题时：仅在 themes.css 对应 [data-theme="xxx"] 块内覆盖 token，不要改动 base.css 中的基础 token；保持 token 命名一致，确保所有组件能正确消费新值。
- 使用 token 而非硬编码：组件样式中只引用 var(--xxx)，禁止出现具体颜色/尺寸字面量，以保证主题一致性。
- 主题扩展流程：先在 types/theme.ts 的 THEMES 数组注册新主题元数据与布局映射，再在 themes.css 添加同名 data-theme 块，最后确认布局组件存在并可被 ThemeLayoutWrapper 解析。
- 过渡体验：对需要跟随主题变化的元素包裹 .theme-transition 类，利用已有的 transition 规则获得平滑切换效果。
- 与 Element Plus 的协作：Element Plus 提供表单、表格、弹窗等复杂组件，但颜色/圆角/阴影仍应通过 CSS 变量驱动，以便与自定义主题保持一致；必要时可在主题块内覆盖 Element Plus 的 CSS 变量（如 --el-color-primary）以实现更深集成。
- 构建产物：Vite 打包时将两个 CSS 文件合并输出，无需额外 postcss/tailwind 配置；新增样式只需在 main.ts 中追加 import 即可生效。