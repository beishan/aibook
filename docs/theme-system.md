# 多主题风格系统

## 概述

汗牛充栋现在支持三种不同的主题风格，每种主题都有独特的配色方案和页面布局：

| 主题 | 风格 | 布局 | 配色 |
|------|------|------|------|
| 现代简约 | Notion/Linear 风格 | 左侧可折叠侧边栏 | 蓝灰色系 |
| 暖色文艺 | 豆瓣/微信读书风格 | 顶部水平导航栏 | 暖棕/奶油色系 |
| 自然清新 | 柔和渐变风格 | 底部浮动 Dock | 绿色/大地色系 |

## 架构设计

### 主题切换机制

使用 `data-theme` 属性在 `<html>` 元素上设置当前主题：

```html
<html data-theme="modern"> <!-- 现代简约 -->
<html data-theme="warm">   <!-- 暖色文艺 -->
<html data-theme="natural"> <!-- 自然清新 -->
```

所有颜色通过 CSS 自定义属性控制，每个主题在 `[data-theme="..."]` 选择器下覆盖变量值。

### 布局切换机制

- `ThemeLayoutWrapper.vue` 读取当前主题，动态渲染对应布局组件
- 三种布局组件：`SidebarLayout.vue`、`TopbarLayout.vue`、`DockLayout.vue`
- 路由中使用 `ThemeLayoutWrapper` 替代原来的 `LayoutView`

### 主题存储

- Pinia store (`useThemeStore`) 管理当前主题
- localStorage 持久化主题选择
- 启动时自动从 localStorage 读取并应用

## 文件结构

```
frontend/src/
├── types/
│   └── theme.ts              # 主题类型定义
├── stores/
│   └── theme.ts              # 主题 Pinia store
├── styles/
│   ├── base.css              # 主题无关的基础样式
│   └── themes.css            # 三个主题的变量定义
├── layouts/
│   ├── ThemeLayoutWrapper.vue # 动态布局包装器
│   ├── SidebarLayout.vue     # 侧边栏布局（现代简约）
│   ├── TopbarLayout.vue      # 顶部栏布局（暖色文艺）
│   └── DockLayout.vue        # 底部 Dock 布局（自然清新）
└── views/
    └── SettingsView.vue      # 包含主题选择器 UI
```

## CSS 变量体系

每个主题定义以下变量：

### 颜色变量
- `--bg-page`, `--bg-page-gradient` - 页面背景
- `--surface-card`, `--surface-elevated`, `--surface-hover` - 表面色
- `--text-primary`, `--text-secondary`, `--text-tertiary` - 文本色
- `--primary`, `--primary-light`, `--primary-dark`, `--primary-gradient` - 主色
- `--success`, `--warning`, `--danger` - 语义色
- `--border-color`, `--border-color-light` - 边框色

### 效果变量
- `--glass-bg`, `--glass-blur`, `--glass-border`, `--glass-shadow` - 毛玻璃效果
- `--shadow-sm`, `--shadow-md`, `--shadow-lg` - 阴影
- `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-xl` - 圆角

### 布局变量
- `--nav-bg`, `--nav-border` - 导航栏样式

## 主题切换 API

```typescript
import { useThemeStore } from '@/stores/theme'

const themeStore = useThemeStore()

// 获取当前主题
console.log(themeStore.currentTheme) // 'natural'

// 切换主题
themeStore.setTheme('modern')

// 获取当前布局类型
console.log(themeStore.currentLayout) // 'sidebar'
```

## 添加新主题

1. 在 `types/theme.ts` 中添加新的 `ThemeId` 类型
2. 在 `styles/themes.css` 中添加新的 `[data-theme="..."]` 变量块
3. 创建对应的布局组件（如果需要新的布局方式）
4. 在 `layouts/ThemeLayoutWrapper.vue` 中注册新布局
5. 在 `stores/theme.ts` 的 `themes` 数组中添加主题定义

## 响应式设计

- **侧边栏布局**：移动端隐藏侧边栏，显示移动菜单按钮
- **顶部栏布局**：移动端隐藏水平导航，内容区最大化
- **底部 Dock 布局**：移动端缩小 Dock 尺寸

## 浏览器兼容性

- CSS 自定义属性：现代浏览器全支持
- `backdrop-filter`：需要 `-webkit-` 前缀支持 Safari
- `data-theme` 属性选择器：IE11+ 支持
