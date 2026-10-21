<template>
  <section class="user-guide" aria-labelledby="user-guide-title">
    <header class="guide-header card glass">
      <div>
        <p class="guide-kicker">使用说明 · v{{ version }}</p>
        <h2 id="user-guide-title">{{ guide.title }}</h2>
        <p>{{ guide.introduction }}</p>
      </div>
      <el-button @click="downloadGuide">下载使用说明</el-button>
    </header>

    <div class="guide-search">
      <el-input
        v-model="query"
        clearable
        placeholder="搜索操作、入口或问题，例如：黑名单、自动入库、回收站"
        aria-label="搜索使用说明"
        :prefix-icon="Search"
      />
      <p role="status">共 {{ guide.sections.length }} 章，当前显示 {{ sections.length }} 章</p>
    </div>

    <el-empty v-if="!sections.length" description="没有找到相关说明，请尝试其他关键词">
      <el-button @click="query = ''">查看全部说明</el-button>
    </el-empty>
    <div v-else class="guide-layout">
      <nav class="guide-index card glass" aria-label="使用说明章节目录">
        <h3>章节目录</h3>
        <a
          v-for="section in sections"
          :key="section.id"
          :href="`#${section.id}`"
          @click.prevent="jumpTo(section.id)"
        >
          {{ section.title }}
        </a>
      </nav>
      <div class="guide-content">
        <article
          v-for="section in sections"
          :key="section.id"
          class="guide-chapter card glass"
          :aria-labelledby="section.id"
        >
          <h3 :id="section.id" tabindex="-1">{{ section.title }}</h3>
          <template v-for="(block, index) in section.blocks" :key="index">
            <h4 v-if="block.type === 'heading'">{{ block.text }}</h4>
            <component :is="block.ordered ? 'ol' : 'ul'" v-else-if="block.type === 'list'">
              <li v-for="(item, itemIndex) in block.items" :key="itemIndex">{{ item }}</li>
            </component>
            <p v-else>{{ block.text }}</p>
          </template>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import guideSource from '@/content/user-guide.md?raw'
import { version } from '../../package.json'
import { parseUserGuide, searchGuideSections } from '@/utils/userGuide'

const guide = parseUserGuide(guideSource)
const query = ref('')
const sections = computed(() => searchGuideSections(guide.sections, query.value))

function jumpTo(id: string) {
  const heading = document.getElementById(id)
  heading?.focus({ preventScroll: true })
  heading?.scrollIntoView({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'start',
  })
}

function downloadGuide() {
  const url = URL.createObjectURL(new Blob([guideSource], { type: 'text/markdown;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `汗牛充栋使用说明-v${version}.md`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<style scoped>
.user-guide {
  min-width: 0;
  color: var(--text-primary);
}
.guide-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  padding: 24px;
}
.guide-header h2 {
  margin: 8px 0 12px;
  font-size: 24px;
}
.guide-header p {
  max-width: 75ch;
  color: var(--text-secondary);
  line-height: 1.8;
}
.guide-kicker {
  font-size: 12px;
  font-weight: 600;
}
.guide-search {
  margin: 20px 0;
}
.guide-search p {
  margin: 8px 0 0;
  color: var(--text-secondary);
  font-size: 13px;
}
.guide-layout {
  display: grid;
  grid-template-columns: minmax(160px, 220px) minmax(0, 1fr);
  align-items: start;
  gap: 20px;
}
.guide-index {
  position: sticky;
  top: 24px;
  max-height: 70vh;
  overflow-y: auto;
  padding: 16px;
}
.guide-index h3 {
  margin: 0 0 12px;
  font-size: 15px;
}
.guide-index a {
  display: block;
  padding: 8px;
  border-radius: 8px;
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
  text-decoration: none;
  overflow-wrap: anywhere;
}
.guide-index a:hover,
.guide-index a:focus-visible {
  background: var(--primary-alpha-10);
  color: var(--primary);
}
.guide-index a:focus-visible,
.guide-chapter h3:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 3px;
}
.guide-content {
  display: grid;
  min-width: 0;
  gap: 20px;
}
.guide-chapter {
  padding: 24px;
  line-height: 1.9;
  overflow-wrap: anywhere;
}
.guide-chapter h3 {
  margin: 0 0 20px;
  font-size: 20px;
  scroll-margin-top: 28px;
}
.guide-chapter h4 {
  margin: 22px 0 10px;
  font-size: 16px;
}
.guide-chapter p,
.guide-chapter li {
  color: var(--text-secondary);
  font-size: 14px;
}
.guide-chapter ul,
.guide-chapter ol {
  padding-left: 24px;
}
.guide-chapter li + li {
  margin-top: 10px;
}
@media (max-width: 1100px) {
  .guide-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .guide-index {
    position: static;
    max-height: 240px;
  }
}
@media (max-width: 600px) {
  .guide-header {
    flex-direction: column;
  }
  .guide-header,
  .guide-chapter {
    padding: 16px;
  }
  .guide-chapter h3 {
    font-size: 18px;
  }
}
</style>
