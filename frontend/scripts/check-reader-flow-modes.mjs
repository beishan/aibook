import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const source = await readFile(new URL('../src/views/ReaderView.vue', import.meta.url), 'utf8')

assert.match(source, /type ReaderFlowMode = 'scroll' \| 'pagination'/,
  '阅读器应明确提供滚动和翻页两种模式')
assert.match(source, /class="reading-mode-segmented"[\s\S]*?role="radiogroup"/,
  '阅读模式应使用可访问的滑块分段控件')
assert.match(source, /@click="setReadingMode\(mode\.value\)"/,
  '每个模式按钮应直接选择目标模式')
assert.doesNotMatch(source, /togglePaginationMode/,
  '阅读模式不应继续使用容易反向切换的布尔开关')
assert.match(source, /const paginationPageSize = ref\(5\)/,
  '翻页容量应缓存为稳定状态')
assert.match(source, /const pageSize = paginationPageSize\.value/,
  '渲染当前页时不应同步读取 DOM 反复计算容量')
assert.match(source, /persistAutomaticPagination[\s\S]*?settings\.value\.paginationMode = mode === 'pagination'/,
  '长文本自动分页时点击翻页应固化翻页选择而不是退出分页')
assert.match(source, /if \(!isPaginationMode\.value\) return content\.value\.slice\(0, scrollRenderLimit\.value\)/,
  '滚动模式不应一次性渲染整本长文本')
assert.match(source, /scrollRenderLimit\.value \+ SCROLL_RENDER_BATCH_SIZE/,
  '滚动模式应接近末尾时分批追加正文')
assert.match(source, /await new Promise<void>\(resolve => requestAnimationFrame/,
  '跳转到远处段落时应跨动画帧渐进扩展，避免阻塞页面')
assert.match(source, /prefers-reduced-motion[\s\S]*?reading-mode-segmented-indicator/,
  '阅读模式滑块应遵守减少动态效果偏好')

console.log('Reader scroll/pagination mode checks passed.')
