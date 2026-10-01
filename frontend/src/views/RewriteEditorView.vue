<template>
  <div ref="rewriteWorkspaceRef" v-loading="loading" class="rewrite-workspace">
    <header v-if="project" class="workspace-header">
      <div>
        <router-link to="/rewrite" class="back-link">← 重写项目</router-link>
        <h1>{{ project.name }}</h1>
        <p>《{{ project.bookTitle }}》 · {{ project.versionName }} · {{ statusLabel(project.status) }} · 全书 {{ project.totalWordCount }} 字</p>
      </div>
      <div class="header-actions">
        <span class="save-indicator" :class="saveState">{{ saveLabel }}</span>
        <el-button :disabled="!chapter || !editable" @click="saveNow">保存</el-button>
        <el-button @click="openQuickJump">快速跳转 <kbd>⌘/Ctrl P</kbd></el-button>
        <el-button @click="openSearch">查找与替换</el-button>
        <el-button @click="openSnapshots">整书快照</el-button>
        <el-button @click="openMemos">项目资料</el-button>
        <el-button @click="exportOpen = true">导出</el-button>
        <el-button :disabled="!chapter" @click="toggleSource">{{ showSource ? '收起原文' : '原文对照' }}</el-button>
        <el-button :disabled="!chapter" @click="openHistory">历史记录</el-button>
        <el-button :disabled="!project" @click="openReader">阅读预览</el-button>
        <el-dropdown @command="handleProjectAction">
          <el-button>项目操作 ⌄</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rename">修改项目名称</el-dropdown-item>
              <el-dropdown-item command="description">编辑项目说明</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ACTIVE'" command="pause">暂停编辑</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'PAUSED'" command="resume">继续编辑</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ARCHIVED'" command="restore">恢复项目</el-dropdown-item>
              <el-dropdown-item v-if="['ACTIVE', 'PAUSED'].includes(project.status)" command="archive">归档项目</el-dropdown-item>
              <el-dropdown-item v-if="project.status === 'ACTIVE'" command="complete">完成重写</el-dropdown-item>
              <el-dropdown-item divided command="delete">删除项目及重写版本</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <div v-if="project" class="workspace-body">
      <aside class="chapter-sidebar">
        <div class="sidebar-heading">
          <strong>章节目录</strong>
          <div>
            <el-button v-if="editable" link type="primary" @click="addChapter">＋ 章节</el-button>
            <el-button v-if="editable" link @click="addVolume">＋ 卷</el-button>
          </div>
        </div>
        <p class="sidebar-progress">已完成 {{ project.completedCount }}/{{ project.chapterCount }} 章 · {{ project.progress }}%</p>
        <div v-if="editable" class="selection-tools">
          <el-button link @click="toggleSelectionMode">{{ selectionMode ? '退出批量选择' : '批量操作' }}</el-button>
          <template v-if="selectionMode">
            <el-button link @click="selectAllChapters">{{ selectedChapterIds.length === chapters.length ? '取消全选' : '全选' }}</el-button>
            <span v-if="selectedChapterIds.length">已选 {{ selectedChapterIds.length }} 章</span>
            <el-button v-if="selectedChapterIds.length" link @click="bulkSetStatus('COMPLETED')">标记完成</el-button>
            <el-button v-if="selectedChapterIds.length" link @click="bulkSetStatus('REVIEW')">设为待复查</el-button>
            <el-button v-if="selectedChapterIds.length" link @click="bulkSetVolume">移动到卷</el-button>
            <el-button v-if="selectedChapterIds.length" link @click="openRenumber">自动编号</el-button>
            <el-button v-if="selectedChapterIds.length" link @click="copySelectedChapterContent">复制正文</el-button>
            <el-button v-if="selectedChapterIds.length" link type="danger" @click="bulkDelete">批量删除</el-button>
          </template>
        </div>
        <nav aria-label="重写章节">
          <template v-for="(item, index) in chapters" :key="item.id">
            <strong
              v-if="item.volumeTitle && (index === 0 || item.volumeTitle !== chapters[index - 1].volumeTitle)"
              class="volume-heading"
            >{{ item.volumeTitle }}</strong>
            <div class="chapter-row">
              <input
                v-if="selectionMode"
                type="checkbox"
                :aria-label="`选择章节 ${item.title}`"
                :checked="selectedChapterIds.includes(item.id)"
                @click.stop
                @change="toggleChapterSelection(item.id, ($event.target as HTMLInputElement).checked)"
              />
              <button
                type="button"
                class="chapter-link"
                :class="{ selected: item.id === chapter?.id }"
                @click="selectChapter(item.id)"
              >
                <span class="chapter-title">{{ item.title }}</span>
                <small>
                  {{ chapterStatusLabel(item.status) }} · {{ item.wordCount }} 字
                  <span v-if="item.pendingMemoCount"> · 待办 {{ item.pendingMemoCount }}</span>
                </small>
              </button>
            </div>
          </template>
        </nav>
        <div v-if="editable" class="deleted-chapters">
          <el-button link @click="loadDeletedChapters">已删除章节（{{ deletedChapters.length }}）</el-button>
          <button
            v-for="item in deletedChapters"
            :key="item.id"
            type="button"
            class="restore-chapter-button"
            @click="restoreDeletedChapter(item.id)"
          >
            恢复 {{ item.title }}
          </button>
        </div>
      </aside>

      <main v-if="chapter" class="editor-panel">
        <div class="chapter-toolbar">
          <div class="chapter-title-row">
            <input
              v-model="chapterTitle"
              aria-label="章节标题"
              maxlength="500"
              :readonly="!editable"
              @blur="saveTitle"
              @keydown.enter.prevent="saveTitle"
            />
            <span>{{ chapter.wordCount }} 字</span>
          </div>
          <div v-if="editable" class="chapter-tools">
            <el-button size="small" @click="setVolume">所属卷</el-button>
            <el-button size="small" :disabled="chapterIndex === 0" @click="moveChapter(-1)">上移</el-button>
            <el-button size="small" :disabled="chapterIndex === chapters.length - 1" @click="moveChapter(1)">下移</el-button>
            <el-button size="small" @click="splitChapter">从光标拆分</el-button>
            <el-button size="small" :disabled="chapterIndex >= chapters.length - 1" @click="mergeNext">合并下一章</el-button>
            <el-button size="small" type="danger" plain :disabled="chapters.length <= 1" @click="deleteChapter">删除章节</el-button>
          </div>
        </div>

        <div v-if="editable" class="chapter-status-control" role="group" aria-label="章节状态">
          <span class="chapter-status-slider" :style="{ transform: `translateX(${chapterStatusIndex * 100}%)` }" />
          <button
            v-for="(state, index) in chapterStatuses"
            :key="state.key"
            type="button"
            :aria-pressed="chapter.status === state.key"
            :tabindex="chapter.status === state.key ? 0 : -1"
            @click="setChapterStatus(state.key)"
            @keydown="handleChapterStatusKeydown($event, index)"
          >
            {{ state.label }}
          </button>
        </div>

        <div v-if="recoveredDraft" class="draft-notice">
          已恢复此设备上次未保存的文字，请保存或重新加载服务器内容。
          <el-button link @click="reloadChapter">重新加载</el-button>
        </div>
        <div v-if="saveState === 'conflict'" class="conflict-notice">
          其他窗口修改了本章。当前文字保留在编辑器中；请先复制，再重新加载服务器内容。
          <el-button size="small" @click="copyDraft">复制本地内容</el-button>
          <el-button size="small" @click="reloadChapter">重新加载</el-button>
        </div>

        <div
          class="writing-surface"
          :class="{
            comparing: showSource && !showDiff,
            'diff-review-mode': showDiff,
          }"
        >
          <section v-if="showSource" v-show="!showDiff" class="source-pane">
            <div class="pane-label">
              <strong>源章节 · {{ chapter.sourceTitle || '无对应原文' }}</strong>
              <div class="source-actions">
                <el-button
                  v-if="chapter.hasSource && !showDiff"
                  link
                  @click="copySourceSelection"
                >复制选中文本</el-button>
                <el-button
                  v-if="editable && chapter.hasSource && !showDiff"
                  link
                  @click="insertSourceSelection"
                >插入到光标</el-button>
                <el-button v-if="chapter.hasSource" link @click="showDiff = true">
                  查看差异审阅
                </el-button>
              </div>
              <el-button v-if="editable && chapter.hasSource" link @click="restoreSource">恢复为原文</el-button>
            </div>
            <pre ref="sourceTextRef">{{ chapter.sourceContent || '这是新增章节，没有对应原文。' }}</pre>
          </section>
          <section ref="draftPaneRef" v-show="!showDiff" class="draft-pane">
            <div class="pane-label"><strong>重写正文</strong><span>自动保存约 1 秒</span></div>
            <div v-if="editable" class="rich-toolbar" role="toolbar" aria-label="正文格式">
              <el-button size="small" @click="toggleParagraph">段落</el-button>
              <el-button size="small" @click="toggleHeading(2)">小标题</el-button>
              <el-button size="small" :type="editor?.isActive('bold') ? 'primary' : 'default'" @click="toggleMark('bold')"><strong>B</strong></el-button>
              <el-button size="small" :type="editor?.isActive('italic') ? 'primary' : 'default'" @click="toggleMark('italic')"><em>I</em></el-button>
              <el-button size="small" :type="editor?.isActive('underline') ? 'primary' : 'default'" @click="toggleMark('underline')"><u>U</u></el-button>
              <el-button size="small" :type="editor?.isActive('strike') ? 'primary' : 'default'" @click="toggleMark('strike')"><s>S</s></el-button>
              <el-button size="small" :type="editor?.isActive('link') ? 'primary' : 'default'" @click="editLink">链接</el-button>
              <el-button size="small" @click="toggleBlock('blockquote')">引用</el-button>
              <el-button size="small" @click="toggleBlock('bulletList')">无序列表</el-button>
              <el-button size="small" @click="toggleBlock('orderedList')">有序列表</el-button>
              <el-button size="small" @click="clearFormatting">清除格式</el-button>
              <el-button size="small" @click="insertSceneBreak">场景分隔</el-button>
              <el-button size="small" :disabled="!editor?.can().undo()" @click="editor?.chain().focus().undo().run()">撤销</el-button>
              <el-button size="small" :disabled="!editor?.can().redo()" @click="editor?.chain().focus().redo().run()">重做</el-button>
            </div>
            <EditorContent v-if="editor" :editor="editor" class="rich-editor-surface" />
            <div v-else class="rich-editor-surface is-readonly" role="textbox" aria-readonly="true">{{ readableDraft }}</div>
          </section>
          <section v-show="showDiff" class="diff-review" aria-label="原文与重写稿差异审阅">
            <div class="diff-review-toolbar">
              <div class="diff-review-summary">
                <strong>差异审阅</strong>
                <span v-if="!sourceDiff.truncated">
                  新增 {{ sourceDiff.addedCount }} 行 · 删除 {{ sourceDiff.removedCount }} 行
                </span>
              </div>
              <div class="diff-review-actions">
                <el-button
                  size="small"
                  :disabled="!diffReviewChangeCount"
                  @click="navigateDiffReview(-1)"
                >上一处</el-button>
                <span v-if="diffReviewChangeCount" class="diff-review-position">
                  {{ diffReviewPosition < 0 ? '未定位' : diffReviewPosition + 1 }}
                  / {{ diffReviewChangeCount }} 处
                </span>
                <el-button
                  size="small"
                  :disabled="!diffReviewChangeCount"
                  @click="navigateDiffReview(1)"
                >下一处</el-button>
                <el-button size="small" type="primary" plain @click="showDiff = false">
                  返回编辑对照
                </el-button>
              </div>
            </div>
            <div v-if="!sourceDiff.truncated && sourceDiff.changeCount" class="diff-review-column-labels">
              <strong>原文</strong>
              <strong>重写正文</strong>
            </div>
            <p v-if="sourceDiff.truncated" class="diff-review-message">
              本章内容超过差异审阅计算上限，请返回原文对照模式查看。
            </p>
            <p v-else-if="!sourceDiff.changeCount" class="diff-review-message">
              原文与重写稿一致，暂无差异。
            </p>
            <div v-else ref="diffReviewRef" class="diff-review-content" />
          </section>
        </div>

        <footer class="editor-footer">
          <el-button :disabled="chapterIndex <= 0" @click="selectChapter(chapters[chapterIndex - 1].id)">← 上一章</el-button>
          <span>{{ chapterIndex + 1 }} / {{ chapters.length }}</span>
          <el-button :disabled="chapterIndex >= chapters.length - 1" @click="selectChapter(chapters[chapterIndex + 1].id)">下一章 →</el-button>
        </footer>
      </main>
      <div v-else class="editor-panel empty-panel">请选择章节</div>
    </div>
    <el-dialog v-model="historyOpen" title="章节历史" width="min(760px, 94vw)">
      <div class="history-dialog">
        <div class="history-list">
          <button
            v-for="item in history"
            :key="item.revision"
            type="button"
            :class="{ selected: selectedHistoryRevision === item.revision }"
            @click="showRevision(item.revision)"
          >
            修订 {{ item.revision }} · {{ item.reason }}
            <small>{{ item.createdAt }}</small>
          </button>
        </div>
        <div class="history-content-panel">
          <el-button
            v-if="selectedHistoryRevision !== null"
            link
            @click="historyShowDiff = !historyShowDiff"
          >{{ historyShowDiff ? '查看历史正文' : '与当前正文对比' }}</el-button>
          <div v-if="historyShowDiff && selectedHistoryRevision !== null" class="history-diff">
            <p v-if="historyDiff.truncated">正文过长，暂不生成字符差异；可查看历史正文并恢复。</p>
            <template v-else>
              <span
                v-for="(segment, index) in historyDiff.segments"
                :key="index"
                :class="`diff-segment--${segment.kind}`"
              >{{ segment.text }}</span>
            </template>
          </div>
          <pre v-else>{{ historyContent || '选择一条历史记录查看正文' }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="historyOpen = false">关闭</el-button>
        <el-button
          v-if="editable && selectedHistoryRevision !== null"
          type="primary"
          @click="restoreHistory"
        >恢复此修订</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="searchOpen" title="查找与替换" width="min(780px, 94vw)">
      <div class="rewrite-search">
        <el-input v-model="searchQuery" placeholder="查找正文内容" @keyup.enter="runSearch" />
        <el-input v-model="replacement" placeholder="替换为" />
        <div class="search-rule-controls">
          <el-select
            v-model="selectedSearchRuleName"
            clearable
            placeholder="已保存的规则"
            @change="applySearchRule"
          >
            <el-option v-for="rule in searchRules" :key="rule.name" :label="rule.name" :value="rule.name" />
          </el-select>
          <el-button :disabled="!searchQuery" @click="saveSearchRule">保存为规则</el-button>
          <el-button :disabled="!selectedSearchRuleName" @click="deleteSearchRule">删除规则</el-button>
        </div>
        <el-select v-model="searchScope" aria-label="查找范围">
          <el-option label="整本书" value="BOOK" />
          <el-option label="当前章节" value="CHAPTER" />
          <el-option v-if="chapter?.volumeTitle" label="当前卷" value="VOLUME" />
        </el-select>
        <div class="rewrite-search-options">
          <el-checkbox v-model="matchCase">区分大小写</el-checkbox>
          <el-checkbox v-model="wholeWord">全字匹配</el-checkbox>
          <el-checkbox v-model="regex">正则表达式</el-checkbox>
        </div>
        <div class="rewrite-search-actions">
          <el-button @click="runSearch">查找</el-button>
          <el-button type="primary" :disabled="!editable || !searchResult?.totalMatches" @click="applyReplace">
            全部替换（{{ searchResult?.totalMatches || 0 }}）
          </el-button>
        </div>
        <p v-if="searchResult" class="search-summary">
          共 {{ searchResult.totalMatches }} 处命中{{ searchResult.truncated ? '，结果已截断' : '' }}
        </p>
        <button
          v-for="item in searchResult?.results || []"
          :key="item.chapterId"
          type="button"
          class="search-result"
          @click="goToSearchResult(item.chapterId)"
        >
          <strong>{{ item.title }} · {{ item.matchCount }} 处</strong>
          <small v-for="excerpt in item.excerpts" :key="`before-${excerpt}`">原文：{{ excerpt }}</small>
          <small
            v-for="(sample, sampleIndex) in item.replacementSamples"
            :key="`after-${sampleIndex}`"
            class="replace-preview-sample"
          >替换后：{{ sample }}</small>
        </button>
      </div>
    </el-dialog>

    <el-dialog v-model="snapshotsOpen" title="整书快照" width="min(680px, 94vw)">
      <div class="snapshot-list">
        <p v-if="!snapshots.length">还没有整书快照。快照会保存当前章节顺序、正文、卷和来源关系。</p>
        <el-alert
          :title="`快照占用约 ${formatBytes(snapshotStorage.totalBytes)}；自动恢复点保留最近 ${snapshotStorage.automaticLimit} 个（当前 ${snapshotStorage.automaticCount} 个），命名快照不会自动清理（当前 ${snapshotStorage.namedCount} 个）。`"
          type="info"
          :closable="false"
        />
        <article v-for="item in snapshots" :key="item.id" class="snapshot-item">
          <div>
            <strong>{{ item.name }}</strong>
            <small>快照 {{ item.number }} · {{ item.createdAt }} · {{ item.automatic ? '自动恢复点' : '命名快照' }}</small>
          </div>
          <div class="snapshot-actions">
            <el-button @click="restoreSnapshot(item)">恢复</el-button>
            <el-button
              v-if="!item.automatic"
              type="danger"
              plain
              :disabled="!editable"
              @click="deleteSnapshot(item)"
            >删除</el-button>
          </div>
        </article>
      </div>
      <template #footer>
        <el-button @click="snapshotsOpen = false">关闭</el-button>
        <el-button type="primary" :disabled="!editable" @click="createSnapshot">创建快照</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="exportOpen" title="导出已保存内容" width="min(560px, 92vw)">
      <div class="export-settings">
        <label>
          导出格式
          <el-select v-model="exportFormat">
            <el-option label="纯文本（TXT）" value="txt" />
            <el-option label="Markdown（MD）" value="md" />
            <el-option label="EPUB 3 电子书" value="epub" />
          </el-select>
        </label>
        <el-checkbox v-model="exportIncludeMetadata" :disabled="exportFormat === 'epub'">
          包含书名和作者
        </el-checkbox>
        <span v-if="exportFormat === 'epub'" class="field-hint">
          EPUB 规范要求在书籍元数据中保留书名和作者。
        </span>
        <el-checkbox v-model="exportIncludeChapterTitles">包含章节标题</el-checkbox>
        <label>
          章节标题样式
          <el-select v-model="exportChapterTitleStyle" :disabled="!exportIncludeChapterTitles">
            <el-option label="使用当前标题" value="ORIGINAL" />
            <el-option label="第 1 章 · 标题" value="NUMBERED" />
          </el-select>
        </label>
        <label>
          章节间空行
          <el-input-number v-model="exportChapterSpacing" :min="1" :max="5" />
        </label>
      </div>
      <el-alert
        v-if="saveState !== 'saved'"
        title="当前有未保存修改。导出将使用服务器上最近一次成功保存的内容。"
        type="warning"
        :closable="false"
        show-icon
      />
      <template #footer>
        <el-button @click="exportOpen = false">取消</el-button>
        <el-button type="primary" @click="downloadExport">下载文件</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renumberOpen" title="章节自动编号" width="min(620px, 94vw)">
      <div class="renumber-settings">
        <el-select v-model="renumberStyle" aria-label="编号格式">
          <el-option label="第 1 章" value="ARABIC" />
          <el-option label="第一章" value="CHINESE" />
          <el-option label="Chapter 1" value="ENGLISH" />
          <el-option label="001." value="PADDED" />
        </el-select>
        <el-input-number v-model="renumberStart" :min="1" :max="9999" controls-position="right" />
      </div>
      <p class="field-hint">只替换标题开头可识别的章节编号；无法识别的标题会保留并列出。</p>
      <div class="renumber-preview">
        <article v-for="item in renumberPreview" :key="item.id" :class="{ skipped: item.skipped }">
          <span>{{ item.title }}</span>
          <span aria-hidden="true">→</span>
          <strong>{{ item.nextTitle || '无法识别，跳过' }}</strong>
        </article>
      </div>
      <template #footer>
        <el-button @click="renumberOpen = false">取消</el-button>
        <el-button type="primary" :disabled="!renumberPreview.some(item => !item.skipped)" @click="applyRenumber">
          应用编号
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="quickJumpOpen" title="快速跳转" width="min(560px, 92vw)">
      <div class="quick-jump">
        <el-input
          ref="quickJumpInput"
          v-model="quickJumpQuery"
          autofocus
          clearable
          placeholder="输入章节序号或标题"
          @keyup.enter="jumpToFirstMatch"
        />
        <button
          v-for="item in quickJumpMatches"
          :key="item.id"
          type="button"
          :class="{ selected: item.id === chapter?.id }"
          @click="jumpToChapter(item.id)"
        >
          <strong>{{ item.sortIndex + 1 }}. {{ item.title }}</strong>
          <small>{{ item.volumeTitle || '未分卷' }} · {{ item.wordCount }} 字</small>
        </button>
        <p v-if="!quickJumpMatches.length" class="field-hint">没有匹配的章节。</p>
      </div>
    </el-dialog>

    <el-dialog v-model="memosOpen" title="重写资料" width="min(720px, 94vw)">
      <div class="memo-workspace">
        <div v-if="!memoFormOpen" class="memo-type-control" role="group" aria-label="资料类型">
          <span :style="{ transform: `translateX(${memoType === 'GLOSSARY' ? '100%' : '0'})` }" />
          <button :aria-pressed="memoType === 'NOTE'" @click="changeMemoType('NOTE')">备注</button>
          <button :aria-pressed="memoType === 'GLOSSARY'" @click="changeMemoType('GLOSSARY')">资料条目</button>
        </div>
        <div v-if="memoFormOpen" class="memo-form">
          <el-input v-model="memoTitle" :placeholder="memoType === 'NOTE' ? '备注标题' : '术语或资料名称'" maxlength="200" />
          <el-input v-model="memoContent" type="textarea" :rows="8" resize="vertical" placeholder="记录正文，不会混入章节内容" />
          <el-select v-if="memoType === 'NOTE'" v-model="memoState" aria-label="备注处理状态">
            <el-option label="待处理" value="TODO" />
            <el-option label="已处理" value="DONE" />
          </el-select>
          <el-input
            v-else
            v-model="memoAliasesText"
            placeholder="别名（用逗号分隔，可留空）"
            maxlength="1100"
          />
          <el-select v-model="memoChapterId" clearable placeholder="关联章节（可选）">
            <el-option v-for="item in chapters" :key="item.id" :label="item.title" :value="item.id" />
          </el-select>
          <el-input-number v-model="memoAnchorPosition" :min="0" :max="25000000" controls-position="right" />
          <span class="field-hint">正文字符位置锚点；不需要定位时留空。</span>
        </div>
        <div v-else class="memo-list">
          <p v-if="!memos.length">还没有{{ memoType === 'NOTE' ? '备注' : '资料条目' }}。</p>
          <el-input
            v-if="memos.length"
            v-model="memoFilterQuery"
            clearable
            placeholder="搜索标题、别名和说明"
          />
          <article v-for="item in filteredMemos" :key="item.id" class="memo-card">
            <button type="button" class="memo-entry" @click="editMemo(item)">
              <strong>{{ item.title }}</strong>
              <small>
                {{ item.chapterTitle ? `《${item.chapterTitle}》 · ` : '' }}
                {{ item.type === 'NOTE' ? (item.state === 'DONE' ? '已处理' : '待处理') : (item.aliases || []).join('、') }}
              </small>
              <small>{{ item.content }}</small>
              <small>{{ item.updatedAt }}</small>
            </button>
            <el-button
              v-if="item.chapterId"
              link
              type="primary"
              @click="goToMemoChapter(item.chapterId!)"
            >跳转章节</el-button>
          </article>
        </div>
      </div>
      <template #footer>
        <el-button v-if="memoFormOpen" @click="memoFormOpen = false">返回列表</el-button>
        <el-button v-else @click="memosOpen = false">关闭</el-button>
        <el-button v-if="memoFormOpen && memoEditingId !== null" type="danger" plain @click="deleteMemo">删除</el-button>
        <el-button v-if="!memoFormOpen" type="primary" :disabled="!editable" @click="editMemo()">新增条目</el-button>
        <el-button v-else type="primary" :disabled="!editable" @click="saveMemo">保存资料</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { Editor, EditorContent } from '@tiptap/vue-3'
import StarterKit from '@tiptap/starter-kit'
import { Diff2HtmlUI } from 'diff2html/lib-esm/ui/js/diff2html-ui-base.js'
import 'diff2html/bundles/css/diff2html.min.css'
import api from '@/utils/api'
import { confirm, message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

interface RewriteProject {
  id: number
  bookId: number
  bookTitle: string
  rewriteVersionId: number
  name: string
  versionName: string
  description?: string
  status: string
  chapterCount: number
  completedCount: number
  progress: number
  totalWordCount: number
  currentChapterId: number
}

interface ChapterSummary {
  id: number
  title: string
  sortIndex: number
  status: string
  wordCount: number
  revision: number
  hasSource: boolean
  volumeTitle: string
  contentFormatVersion: number
  pendingMemoCount?: number
}

interface ChapterDetail extends ChapterSummary {
  content: string
  sourceTitle: string | null
  sourceContent: string | null
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.projectId)
const rewriteWorkspaceRef = ref<HTMLElement | null>(null)
const draftPaneRef = ref<HTMLElement | null>(null)
const project = ref<RewriteProject | null>(null)
const chapters = ref<ChapterSummary[]>([])
const deletedChapters = ref<ChapterSummary[]>([])
const chapter = ref<ChapterDetail | null>(null)
const editor = shallowRef<Editor | null>(null)
const sourceTextRef = ref<HTMLElement | null>(null)
const diffReviewRef = ref<HTMLElement | null>(null)
const diffReviewRows = ref<HTMLElement[]>([])
const diffReviewChangeCount = ref(0)
const diffReviewPosition = ref(-1)
const draftContent = ref('')
const chapterTitle = ref('')
const loading = ref(false)
const showSource = ref(false)
const showDiff = ref(false)
const recoveredDraft = ref(false)
const historyOpen = ref(false)
const history = ref<Array<{ revision: number; reason: string; createdAt: string }>>([])
const selectedHistoryRevision = ref<number | null>(null)
const historyContent = ref('')
const historyShowDiff = ref(false)
const searchOpen = ref(false)
const searchQuery = ref('')
const replacement = ref('')
interface RewriteSearchRule {
  name: string
  query: string
  replacement: string
  scope: 'BOOK' | 'CHAPTER' | 'VOLUME'
  matchCase: boolean
  wholeWord: boolean
  regex: boolean
}
const searchRules = ref<RewriteSearchRule[]>([])
const selectedSearchRuleName = ref('')
const searchScope = ref<'BOOK' | 'CHAPTER' | 'VOLUME'>('BOOK')
const matchCase = ref(false)
const wholeWord = ref(false)
const regex = ref(false)
const searchResult = ref<{
  totalMatches: number
  truncated: boolean
  results: Array<{
    chapterId: number
    title: string
    matchCount: number
    excerpts: string[]
    replacementSamples: string[]
  }>
} | null>(null)
const snapshotsOpen = ref(false)
const snapshots = ref<Array<{
  id: number
  number: number
  name: string
  automatic: boolean
  createdAt: string
}>>([])
const snapshotStorage = ref({
  totalBytes: 0,
  automaticCount: 0,
  automaticLimit: 30,
  namedCount: 0,
})
const exportOpen = ref(false)
const exportFormat = ref<'txt' | 'md' | 'epub'>('txt')
const exportIncludeMetadata = ref(true)
const exportIncludeChapterTitles = ref(true)
const exportChapterTitleStyle = ref<'ORIGINAL' | 'NUMBERED'>('ORIGINAL')
const exportChapterSpacing = ref(1)
const memosOpen = ref(false)
const memoType = ref<'NOTE' | 'GLOSSARY'>('NOTE')
const memos = ref<Array<{
  id: number
  type: 'NOTE' | 'GLOSSARY'
  title: string
  content: string
  state: 'TODO' | 'DONE'
  aliases: string[]
  chapterId: number | null
  chapterTitle: string
  anchorPosition: number | null
  updatedAt: string
}>>([])
const memoFormOpen = ref(false)
const memoFilterQuery = ref('')
const memoEditingId = ref<number | null>(null)
const memoTitle = ref('')
const memoContent = ref('')
const memoState = ref<'TODO' | 'DONE'>('TODO')
const memoAliasesText = ref('')
const memoChapterId = ref<number | undefined>()
const memoAnchorPosition = ref<number | undefined>()
const filteredMemos = computed(() => {
  const query = memoFilterQuery.value.trim().toLocaleLowerCase()
  if (!query) return memos.value
  return memos.value.filter(item => [
    item.title,
    item.content,
    ...item.aliases,
    item.chapterTitle,
  ].some(value => value.toLocaleLowerCase().includes(query)))
})
const selectionMode = ref(false)
const selectedChapterIds = ref<number[]>([])
const renumberOpen = ref(false)
const renumberStyle = ref<'ARABIC' | 'CHINESE' | 'ENGLISH' | 'PADDED'>('ARABIC')
const renumberStart = ref(1)
const quickJumpOpen = ref(false)
const quickJumpQuery = ref('')
const quickJumpInput = ref()
const saveState = ref<'saved' | 'dirty' | 'saving' | 'error' | 'conflict'>('saved')
const editable = computed(() => project.value?.status === 'ACTIVE')
const readableDraft = computed(() => {
  try {
    const document = JSON.parse(draftContent.value)
    if (document?.type === 'doc') return richDocumentText(document)
  } catch { /* Legacy chapters remain plain text. */ }
  return draftContent.value
})
const chapterIndex = computed(() => chapters.value.findIndex(item => item.id === chapter.value?.id))
const selectedChapters = computed(() => chapters.value.filter(item =>
  selectedChapterIds.value.includes(item.id)))
const renumberPreview = computed(() => {
  let number = renumberStart.value
  return selectedChapters.value.map(item => {
    const nextTitle = formatRenumberTitle(item.title, number, renumberStyle.value)
    if (nextTitle !== null) number++
    return { id: item.id, title: item.title, nextTitle, skipped: nextTitle === null }
  })
})
const quickJumpMatches = computed(() => {
  const query = quickJumpQuery.value.trim().toLocaleLowerCase()
  return chapters.value.filter((item, index) => !query
    || item.title.toLocaleLowerCase().includes(query)
    || String(index + 1).startsWith(query)).slice(0, 30)
})
const sourceDiff = computed(() => {
  if (!showDiff.value) {
    return { patch: '', truncated: false, changeCount: 0, addedCount: 0, removedCount: 0 }
  }
  const normalizeLines = (content: string) => {
    const normalized = content.replace(/\r\n?/g, '\n').replace(/\n$/, '')
    return normalized ? normalized.split('\n') : []
  }
  const before = normalizeLines(chapter.value?.sourceContent || '')
  const after = normalizeLines(readableDraft.value)
  const totalCharacters = before.reduce((sum, line) => sum + line.length, 0)
    + after.reduce((sum, line) => sum + line.length, 0)
  if (before.length > 2000 || after.length > 2000 || totalCharacters > 500_000) {
    return { patch: '', truncated: true, changeCount: 0, addedCount: 0, removedCount: 0 }
  }
  const width = after.length + 1
  const table = Array.from({ length: before.length + 1 }, () => new Uint16Array(width))
  for (let i = before.length - 1; i >= 0; i--) {
    for (let j = after.length - 1; j >= 0; j--) {
      table[i][j] = before[i] === after[j]
        ? table[i + 1][j + 1] + 1
        : Math.max(table[i + 1][j], table[i][j + 1])
    }
  }
  const lines: Array<{ kind: 'same' | 'added' | 'removed'; text: string }> = []
  let i = 0
  let j = 0
  while (i < before.length || j < after.length) {
    if (i < before.length && j < after.length && before[i] === after[j]) {
      lines.push({ kind: 'same', text: before[i] })
      i++
      j++
    } else if (i < before.length && (j >= after.length || table[i + 1][j] >= table[i][j + 1])) {
      lines.push({ kind: 'removed', text: before[i++] })
    } else if (j < after.length) {
      lines.push({ kind: 'added', text: after[j++] })
    }
  }
  const addedCount = lines.filter(line => line.kind === 'added').length
  const removedCount = lines.filter(line => line.kind === 'removed').length
  const oldRange = before.length ? `1,${before.length}` : '0,0'
  const newRange = after.length ? `1,${after.length}` : '0,0'
  const patchLines = [
    '--- a/chapter.txt',
    '+++ b/chapter.txt',
    `@@ -${oldRange} +${newRange} @@`,
    ...lines.map(line => `${line.kind === 'same' ? ' ' : line.kind === 'removed' ? '-' : '+'}${line.text}`),
  ]
  return {
    patch: patchLines.join('\n') + '\n',
    truncated: false,
    changeCount: addedCount + removedCount,
    addedCount,
    removedCount,
  }
})
const renderDiffReview = async () => {
  await nextTick()
  const target = diffReviewRef.value
  const diff = sourceDiff.value
  if (!showDiff.value || !target || diff.truncated || !diff.changeCount) return

  const review = new Diff2HtmlUI(target, diff.patch, {
    outputFormat: 'side-by-side',
    drawFileList: false,
    matching: 'none',
    matchingMaxComparisons: 2500,
    maxLineSizeInBlockForComparison: 5000,
    maxLineLengthHighlight: 10000,
    diffStyle: 'word',
    synchronisedScroll: true,
    highlight: false,
    fileListToggle: false,
    fileContentToggle: false,
    stickyFileHeaders: false,
    smartSelection: false,
    renderNothingWhenEmpty: true,
  })
  review.draw()

  const [oldSide, newSide] = Array.from(target.querySelectorAll('.d2h-file-side-diff'))
  const oldRows = Array.from(oldSide?.querySelectorAll<HTMLTableRowElement>('tbody tr') || [])
  const newRows = Array.from(newSide?.querySelectorAll<HTMLTableRowElement>('tbody tr') || [])
  diffReviewRows.value = oldRows.filter((row, index) =>
    row.querySelector('.d2h-del, .d2h-ins') || newRows[index]?.querySelector('.d2h-del, .d2h-ins'))
  diffReviewChangeCount.value = diffReviewRows.value.length
  diffReviewPosition.value = -1
}
const navigateDiffReview = (direction: -1 | 1) => {
  const rows = diffReviewRows.value
  const container = diffReviewRef.value
  if (!rows.length || !container) return

  const baseIndex = diffReviewPosition.value < 0
    ? direction > 0 ? -1 : 0
    : diffReviewPosition.value
  const nextIndex = (baseIndex + direction + rows.length) % rows.length
  const target = rows[nextIndex]
  const containerBounds = container.getBoundingClientRect()
  const targetBounds = target.getBoundingClientRect()
  const top = container.scrollTop + targetBounds.top - containerBounds.top
    - (container.clientHeight - targetBounds.height) / 2
  const behavior = window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth'
  container.scrollTo({ top: Math.max(0, top), behavior })
  diffReviewPosition.value = nextIndex
}
const historyDiff = computed(() => {
  if (!historyContent.value || historyContent.value.length + readableDraft.value.length > 500_000) {
    return { truncated: Boolean(historyContent.value), segments: [] }
  }
  const lines = addCharacterDiffSegments([
    { kind: 'removed' as const, text: historyContent.value },
    { kind: 'added' as const, text: readableDraft.value },
  ])
  return { truncated: false, segments: lines[0]?.segments || [] }
})

const addCharacterDiffSegments = (lines: Array<{
  kind: 'same' | 'added' | 'removed'
  text: string
}>) => {
  const result: Array<{
    kind: 'same' | 'added' | 'removed'
    text: string
    segments?: Array<{ kind: 'same' | 'added' | 'removed'; text: string }>
  }> = []
  let index = 0
  while (index < lines.length) {
    if (lines[index].kind === 'same') {
      result.push(lines[index])
      index++
      continue
    }
    const removed: typeof lines = []
    const added: typeof lines = []
    while (index < lines.length && lines[index].kind !== 'same') {
      if (lines[index].kind === 'removed') removed.push(lines[index])
      else added.push(lines[index])
      index++
    }
    const pairedCount = Math.min(removed.length, added.length)
    for (let pair = 0; pair < pairedCount; pair++) {
      const oldText = removed[pair].text
      const newText = added[pair].text
      let prefixLength = 0
      while (prefixLength < oldText.length && prefixLength < newText.length
        && oldText[prefixLength] === newText[prefixLength]) prefixLength++
      let suffixLength = 0
      while (suffixLength < oldText.length - prefixLength
        && suffixLength < newText.length - prefixLength
        && oldText[oldText.length - suffixLength - 1]
          === newText[newText.length - suffixLength - 1]) suffixLength++
      result.push({ kind: 'removed', text: oldText, segments: [
        { kind: 'same', text: oldText.slice(0, prefixLength) },
        { kind: 'removed', text: oldText.slice(prefixLength, oldText.length - suffixLength) },
        { kind: 'same', text: suffixLength ? oldText.slice(-suffixLength) : '' },
      ].filter(segment => segment.text) })
      result.push({ kind: 'added', text: newText, segments: [
        { kind: 'same', text: newText.slice(0, prefixLength) },
        { kind: 'added', text: newText.slice(prefixLength, newText.length - suffixLength) },
        { kind: 'same', text: suffixLength ? newText.slice(-suffixLength) : '' },
      ].filter(segment => segment.text) })
    }
    result.push(...removed.slice(pairedCount), ...added.slice(pairedCount))
  }
  return result
}
const chapterStatuses = [
  { key: 'NOT_STARTED', label: '未开始' },
  { key: 'WRITING', label: '重写中' },
  { key: 'REVIEW', label: '待复查' },
  { key: 'COMPLETED', label: '已完成' },
]
const chapterStatusIndex = computed(() => Math.max(0,
  chapterStatuses.findIndex(item => item.key === chapter.value?.status)))

const chineseNumber = (value: number) => {
  const digits = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
  const units = ['', '十', '百', '千']
  const text = String(value)
  let result = ''
  let zeroPending = false
  for (let index = 0; index < text.length; index++) {
    const digit = Number(text[index])
    const unitIndex = text.length - index - 1
    if (digit === 0) {
      zeroPending = result.length > 0
      continue
    }
    if (zeroPending) result += '零'
    zeroPending = false
    if (!(digit === 1 && unitIndex === 1 && result.length === 0)) result += digits[digit]
    result += units[unitIndex]
  }
  return result
}

const formatRenumberTitle = (
  title: string,
  number: number,
  style: typeof renumberStyle.value,
) => {
  if (number > 9999) return null
  const prefixPattern = /^(?:第\s*[0-9一二三四五六七八九十百千万零〇两]+\s*[章节回集部篇卷]|Chapter\s+\d+|\d{1,4}[.、:：)）\s_-]+)\s*/i
  const match = title.match(prefixPattern)
  if (!match) return null
  const remainder = title.slice(match[0].length).trimStart()
  const prefix = {
    ARABIC: `第${number}章 `,
    CHINESE: `第${chineseNumber(number)}章 `,
    ENGLISH: `Chapter ${number} `,
    PADDED: `${String(number).padStart(3, '0')}. `,
  }[style]
  return prefix + remainder
}
const saveLabel = computed(() => ({
  saved: '已保存', dirty: '有未保存修改', saving: '正在保存…',
  error: '保存失败', conflict: '版本冲突',
}[saveState.value]))
let saveTimer: ReturnType<typeof setTimeout> | undefined
let cursorSaveTimer: ReturnType<typeof setTimeout> | undefined
let diffReviewRenderTimer: ReturnType<typeof setTimeout> | undefined
let editorHeightFrame: number | undefined
let savingPromise: Promise<boolean> | null = null

const statusLabel = (status: string) => ({
  ACTIVE: '进行中', PAUSED: '已暂停', COMPLETED: '已完成', ARCHIVED: '已归档',
}[status] || status)
const chapterStatusLabel = (status: string) =>
  chapterStatuses.find(item => item.key === status)?.label || status
const draftKey = (chapterId: number) =>
  `aibook.rewriteDraft.${userStore.userInfo?.id || 'current'}.${projectId}.${chapterId}`

const storeDraft = (chapterId: number, content: string) => {
  try {
    sessionStorage.setItem(draftKey(chapterId), content)
  } catch {
    message.warning('本章内容超过浏览器临时草稿容量，请尽快连接服务器保存')
  }
}

const loadProject = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}`)
  project.value = data
}

const loadChapters = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/chapters`)
  chapters.value = data || []
  await loadProject()
  await loadDeletedChapters()
}

const loadDeletedChapters = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/deleted-chapters`)
  deletedChapters.value = data || []
}

const loadChapter = async (chapterId: number) => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/chapters/${chapterId}`)
  chapter.value = data
  chapterTitle.value = data.title
  draftContent.value = data.content
  saveState.value = 'saved'
  recoveredDraft.value = false
  const locallySaved = sessionStorage.getItem(draftKey(chapterId))
  if (locallySaved !== null && locallySaved !== data.content) {
    draftContent.value = locallySaved
    saveState.value = 'dirty'
    recoveredDraft.value = true
  }
  setEditorDocument(draftContent.value)
  const projectChapterId = project.value?.currentChapterId
  const resumePosition = projectChapterId === chapterId
    ? project.value?.currentChapterPosition || 0
    : 0
  if (resumePosition > 0 && editor.value) {
    const safePosition = Math.min(resumePosition, editor.value.state.doc.content.size)
    editor.value.commands.setTextSelection(safePosition)
  }
  if (projectChapterId !== chapterId) {
    const { data: updatedProject } = await api.patch(`/api/rewrite/projects/${projectId}`, {
      currentChapterId: chapterId,
      currentChapterPosition: 0,
    })
    project.value = updatedProject
  }
  if (locallySaved !== null && locallySaved !== data.content && data.contentFormatVersion !== 1) {
    draftContent.value = JSON.stringify(editor.value?.getJSON())
    storeDraft(chapterId, draftContent.value)
  }
}

const plainTextDocument = (text: string) => {
  const paragraphs = text.replace(/\r\n?/g, '\n').split(/\n\s*\n+/).filter(value => value.length > 0)
  const content = paragraphs.map(paragraph => ({
    type: 'paragraph',
    content: paragraph.split('\n').flatMap((line, index) => [
      ...(index ? [{ type: 'hardBreak' }] : []),
      ...(line ? [{ type: 'text', text: line }] : []),
    ]),
  }))
  return { type: 'doc', content: content.length ? content : [{ type: 'paragraph' }] }
}

const editorDocument = (content: string) => {
  try {
    const value = JSON.parse(content)
    if (value?.type === 'doc' && Array.isArray(value.content)) return value
  } catch { /* Plain text is converted to paragraphs below. */ }
  return plainTextDocument(content)
}

const setEditorDocument = (content: string) => {
  const document = editorDocument(content)
  if (editor.value) {
    editor.value.commands.setContent(document, { emitUpdate: false })
    editor.value.setEditable(editable.value)
    return
  }
  editor.value = new Editor({
    extensions: [StarterKit.configure({
      heading: { levels: [2, 3] },
      codeBlock: false,
      link: {
        autolink: false,
        linkOnPaste: false,
        openOnClick: false,
        isAllowedUri: isSafeEditorHref,
      },
      underline: {},
    })],
    content: document,
    editable: editable.value,
    editorProps: { attributes: {
      'aria-label': '章节正文',
      'aria-multiline': 'true',
      spellcheck: 'false',
      class: 'tiptap-content',
    } },
    onUpdate: ({ editor: activeEditor }) => {
      draftContent.value = JSON.stringify(activeEditor.getJSON())
      onContentInput()
    },
    onSelectionUpdate: ({ editor: activeEditor }) => {
      scheduleCurrentPositionSave(activeEditor.state.selection.from)
    },
  })
}

const richDocumentText = (node: any): string => {
  if (node?.type === 'text') return node.text || ''
  if (node?.type === 'hardBreak') return '\n'
  const separator = ['doc', 'blockquote', 'bulletList', 'orderedList'].includes(node?.type) ? '\n\n' : ''
  return (node?.content || []).map(richDocumentText).join(separator)
}

const toggleParagraph = () => editor.value?.chain().focus().setParagraph().run()
const toggleHeading = (level: 2 | 3) => editor.value?.chain().focus().toggleHeading({ level }).run()
const toggleMark = (mark: 'bold' | 'italic' | 'underline' | 'strike') =>
  editor.value?.chain().focus().toggleMark(mark).run()

const clearFormatting = () => editor.value?.chain().focus().unsetAllMarks().clearNodes().run()

const isSafeEditorHref = (href: string) => {
  if (/^#[A-Za-z0-9_.:-]+$/.test(href)) return true
  if (/^mailto:[^\s@]+@[^\s@]+$/i.test(href)) return true
  if (!/^https?:\/\//i.test(href)) return false
  try {
    const url = new URL(href)
    return (url.protocol === 'http:' || url.protocol === 'https:') && Boolean(url.host)
  } catch {
    return false
  }
}

const editLink = () => {
  if (!editor.value) return
  if (editor.value.isActive('link')) {
    const currentHref = editor.value.getAttributes('link').href as string | undefined
    const href = window.prompt('链接地址（仅支持 http、https、mailto 或书内锚点）', currentHref || '')
    if (href === null) return
    if (!href.trim()) {
      editor.value.chain().focus().extendMarkRange('link').unsetLink().run()
      return
    }
    if (!isSafeEditorHref(href.trim())) return message.warning('链接地址不安全或格式不受支持')
    editor.value.chain().focus().extendMarkRange('link').setLink({ href: href.trim() }).run()
    return
  }
  const href = window.prompt('链接地址（仅支持 http、https、mailto 或书内锚点）')
  if (!href?.trim()) return
  if (!isSafeEditorHref(href.trim())) return message.warning('链接地址不安全或格式不受支持')
  editor.value.chain().focus().setLink({ href: href.trim() }).run()
}
const toggleBlock = (block: 'blockquote' | 'bulletList' | 'orderedList') => {
  if (block === 'blockquote') editor.value?.chain().focus().toggleBlockquote().run()
  if (block === 'bulletList') editor.value?.chain().focus().toggleBulletList().run()
  if (block === 'orderedList') editor.value?.chain().focus().toggleOrderedList().run()
}
const insertSceneBreak = () => editor.value?.chain().focus().setHorizontalRule().run()

const reloadChapter = async () => {
  if (!chapter.value) return
  if (saveState.value !== 'saved') {
    const approved = await confirm(
      '重新加载将丢弃此设备尚未保存的文字。请先复制需要保留的内容。',
      '重新加载章节',
    )
    if (!approved) return
  }
  sessionStorage.removeItem(draftKey(chapter.value.id))
  await loadChapter(chapter.value.id)
}

const updateSummary = () => {
  if (!chapter.value) return
  const index = chapters.value.findIndex(item => item.id === chapter.value?.id)
  if (index >= 0) chapters.value[index] = { ...chapter.value }
}

const saveNow = async (): Promise<boolean> => {
  if (!chapter.value || !editable.value) return true
  if (saveTimer) clearTimeout(saveTimer)
  if (savingPromise) return savingPromise.then(() => saveNow())
  if (draftContent.value === chapter.value.content) {
    saveState.value = 'saved'
    return true
  }
  const activeChapter = chapter.value
  const content = draftContent.value
  saveState.value = 'saving'
  savingPromise = (async () => {
    try {
      const { data } = await api.put(
        `/api/rewrite/projects/${projectId}/chapters/${activeChapter.id}/content`,
        { content, contentFormatVersion: 1, revision: activeChapter.revision },
        { headers: { 'X-Suppress-Error-Toast': 'true' } },
      )
      if (chapter.value?.id !== activeChapter.id) return true
      chapter.value = data
      updateSummary()
      await loadProject()
      if (draftContent.value === content) {
        saveState.value = 'saved'
        recoveredDraft.value = false
        sessionStorage.removeItem(draftKey(activeChapter.id))
      } else {
        saveState.value = 'dirty'
      }
      return true
    } catch (error: any) {
      saveState.value = error.response?.status === 409 ? 'conflict' : 'error'
      storeDraft(activeChapter.id, draftContent.value)
      message.error(saveState.value === 'conflict' ? '章节已被其他窗口修改' : '保存失败，内容已留在本地草稿')
      return false
    } finally {
      savingPromise = null
    }
  })()
  return savingPromise
}

const onContentInput = () => {
  if (!chapter.value) return
  saveState.value = 'dirty'
  storeDraft(chapter.value.id, draftContent.value)
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(() => { void saveNow() }, 1000)
}

const selectChapter = async (chapterId: number): Promise<boolean> => {
  if (chapter.value?.id === chapterId) return true
  if (!(await saveNow())) return false
  await persistCurrentPosition()
  await loadChapter(chapterId)
  return true
}

const persistCurrentPosition = async (position?: number) => {
  if (!chapter.value || !project.value || !editor.value) return
  if (cursorSaveTimer) {
    clearTimeout(cursorSaveTimer)
    cursorSaveTimer = undefined
  }
  const currentPosition = position ?? editor.value.state.selection.from
  try {
    const { data } = await api.patch(`/api/rewrite/projects/${projectId}`, {
      currentChapterId: chapter.value.id,
      currentChapterPosition: currentPosition,
    })
    project.value = data
  } catch {
    // Chapter selection remains usable if cursor preference persistence is offline.
  }
}

const scheduleCurrentPositionSave = (position: number) => {
  if (!editable.value) return
  if (cursorSaveTimer) clearTimeout(cursorSaveTimer)
  cursorSaveTimer = setTimeout(() => {
    void persistCurrentPosition(position)
  }, 800)
}

const saveTitle = async () => {
  if (!chapter.value || !editable.value || chapterTitle.value === chapter.value.title) return
  if (!(await saveNow())) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { title: chapterTitle.value, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
}

const setChapterStatus = async (status: string) => {
  if (!chapter.value || !editable.value || chapter.value.status === status) return
  if (!(await saveNow())) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { status, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
  await loadProject()
}

const handleChapterStatusKeydown = (event: KeyboardEvent, index: number) => {
  const next = event.key === 'ArrowRight' ? (index + 1) % chapterStatuses.length
    : event.key === 'ArrowLeft' ? (index - 1 + chapterStatuses.length) % chapterStatuses.length : -1
  if (next < 0) return
  event.preventDefault()
  void setChapterStatus(chapterStatuses[next].key)
  ;(event.currentTarget as HTMLElement).parentElement?.querySelectorAll<HTMLButtonElement>('button')[next]?.focus()
}

const addChapter = async () => {
  if (!(await saveNow())) return
  const title = window.prompt('新章节标题', '新章节')
  if (!title?.trim()) return
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/chapters`, {
    title: title.trim(),
    volumeTitle: chapter.value?.volumeTitle || '',
  })
  await loadChapters()
  await loadChapter(data.id)
}

const addVolume = async () => {
  if (!(await saveNow())) return
  const volumeTitle = window.prompt('新卷标题', '新卷')
  if (!volumeTitle?.trim()) return
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/chapters`, {
    title: '新章节',
    volumeTitle: volumeTitle.trim(),
  })
  await loadChapters()
  await loadChapter(data.id)
}

const setVolume = async () => {
  if (!chapter.value || !(await saveNow())) return
  const volumeTitle = window.prompt('所属卷标题；留空则移出卷', chapter.value.volumeTitle || '')
  if (volumeTitle === null) return
  const { data } = await api.patch(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}`,
    { volumeTitle, revision: chapter.value.revision },
  )
  chapter.value = data
  updateSummary()
}

const deleteChapter = async () => {
  if (!chapter.value || !editable.value) return
  if (!(await confirm(`删除“${chapter.value.title}”？此操作会从阅读版本移除该章节。`, '删除章节'))) return
  if (!(await saveNow())) return
  const expectedRevision = chapter.value.revision
  const id = chapter.value.id
  await api.delete(`/api/rewrite/projects/${projectId}/chapters/${id}`, {
    params: { revision: expectedRevision },
  })
  sessionStorage.removeItem(draftKey(id))
  await loadChapters()
  if (chapters.value.length) await loadChapter(chapters.value[0].id)
}

const restoreDeletedChapter = async (chapterId: number) => {
  const deletedChapter = deletedChapters.value.find(item => item.id === chapterId)
  if (!deletedChapter) return
  if (!(await saveNow())) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/deleted-chapters/${chapterId}/restore`,
    { revision: deletedChapter.revision },
  )
  await loadChapters()
  await loadChapter(data.id)
}

const moveChapter = async (direction: number) => {
  if (!chapter.value || !(await saveNow())) return
  const order = chapters.value.map(item => item.id)
  const current = order.indexOf(chapter.value.id)
  const next = current + direction
  if (next < 0 || next >= order.length) return
  ;[order[current], order[next]] = [order[next], order[current]]
  await api.put(`/api/rewrite/projects/${projectId}/chapters/order`, order)
  await loadChapters()
}

const splitChapter = async () => {
  if (!chapter.value || !editor.value || !(await saveNow())) return
  const state = editor.value.state
  const position = state.doc.textBetween(0, state.selection.from, '\n\n').length
  const newTitle = window.prompt('新章节标题', '新章节')
  if (!newTitle?.trim()) return
  const request: Record<string, unknown> = {
    position,
    newTitle: newTitle.trim(),
    revision: chapter.value.revision,
  }
  if (chapter.value.contentFormatVersion === 1) {
    const splitAt = state.selection.from
    request.beforeContent = JSON.stringify(state.doc.copy(state.doc.content.cut(0, splitAt)).toJSON())
    request.afterContent = JSON.stringify(state.doc.copy(
      state.doc.content.cut(splitAt, state.doc.content.size),
    ).toJSON())
  }
  const beforeText = chapter.value.contentFormatVersion === 1
    ? richDocumentText(JSON.parse(String(request.beforeContent)))
    : chapter.value.content.slice(0, position)
  const afterText = chapter.value.contentFormatVersion === 1
    ? richDocumentText(JSON.parse(String(request.afterContent)))
    : chapter.value.content.slice(position)
  const preview = `原章节保留约 ${beforeText.length} 字：\n${previewSnippet(beforeText)}\n\n新章节“${newTitle.trim()}”承接约 ${afterText.length} 字：\n${previewSnippet(afterText)}`
  if (!(await confirm(preview, '拆分预览'))) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/split`,
    request,
  )
  await loadChapters()
  await loadChapter(data.id)
}

const mergeNext = async () => {
  if (!chapter.value || !(await saveNow())) return
  const next = chapters.value[chapterIndex.value + 1]
  if (!next) return
  const title = window.prompt(`合并“${chapter.value.title}”和“${next.title}”，请输入合并后标题`, chapter.value.title)
  if (!title?.trim()) return
  const { data: nextChapter } = await api.get(
    `/api/rewrite/projects/${projectId}/chapters/${next.id}`,
  )
  const nextText = nextChapter.contentFormatVersion === 1
    ? richDocumentText(JSON.parse(nextChapter.content))
    : nextChapter.content
  const mergedLength = readableDraft.value.length + nextText.length
  const preview = `合并后标题：“${title.trim()}”\n预计正文约 ${mergedLength} 字。\n\n当前章开头：\n${previewSnippet(readableDraft.value)}\n\n下一章开头：\n${previewSnippet(nextText)}`
  if (!(await confirm(preview, '合并预览'))) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/merge-next`,
    { title: title.trim(), revision: chapter.value.revision },
  )
  await loadChapters()
  await loadChapter(data.id)
}

const previewSnippet = (value: string, maxLength = 120) => {
  const compact = value.replace(/\s+/g, ' ').trim()
  return compact.length > maxLength ? `${compact.slice(0, maxLength)}…` : compact || '（空）'
}

const restoreSource = async () => {
  if (!chapter.value?.hasSource || !editable.value) return
  if (!(await confirm('将当前章节正文恢复为创建项目时的源章节内容？当前内容会保留恢复记录。', '恢复原文'))) return
  if (!(await saveNow())) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/restore-source`,
    { revision: chapter.value.revision },
  )
  chapter.value = data
  draftContent.value = data.content
  setEditorDocument(data.content)
  sessionStorage.removeItem(draftKey(data.id))
  saveState.value = 'saved'
  updateSummary()
  await loadProject()
}

const selectedSourceText = () => {
  const selection = window.getSelection()
  if (!selection || selection.isCollapsed || !sourceTextRef.value
      || !sourceTextRef.value.contains(selection.anchorNode)
      || !sourceTextRef.value.contains(selection.focusNode)) {
    return ''
  }
  return selection.toString()
}

const copySourceSelection = async () => {
  const selectedText = selectedSourceText()
  if (!selectedText) return message.warning('请先在原文区域选择文本')
  try {
    await navigator.clipboard.writeText(selectedText)
    message.success('原文选段已复制')
  } catch {
    message.error('复制原文选段失败，请检查剪贴板权限')
  }
}

const insertSourceSelection = () => {
  const selectedText = selectedSourceText()
  if (!selectedText) return message.warning('请先在原文区域选择要插入的文本')
  if (!editor.value) return
  const currentSelection = editor.value.state.selection
  const content = plainTextDocument(selectedText).content
  editor.value.chain().focus().insertContentAt({
    from: currentSelection.from,
    to: currentSelection.to,
  }, content).run()
  message.success('原文选段已插入工作稿，可撤销或继续编辑')
}

const openHistory = async () => {
  if (!chapter.value) return
  if (!(await saveNow())) return
  const { data } = await api.get(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions`,
  )
  history.value = data || []
  selectedHistoryRevision.value = null
  historyContent.value = ''
  historyOpen.value = true
}

const openSearch = async () => {
  if (!(await saveNow())) return
  searchResult.value = null
  selectedSearchRuleName.value = ''
  try {
    const { data } = await api.get('/api/user/preferences/rewrite-search-rules')
    searchRules.value = data || []
  } catch {
    searchRules.value = []
  }
  searchOpen.value = true
}

const applySearchRule = async (name: string) => {
  const rule = searchRules.value.find(item => item.name === name)
  if (!rule) return
  searchQuery.value = rule.query
  replacement.value = rule.replacement
  searchScope.value = rule.scope
  matchCase.value = rule.matchCase
  wholeWord.value = rule.wholeWord
  regex.value = rule.regex
  searchResult.value = null
  await runSearch()
}

const saveSearchRule = async () => {
  if (!searchQuery.value.trim()) return message.warning('请先填写查找内容')
  const name = window.prompt('规则名称')?.trim()
  if (!name) return
  const existing = searchRules.value.find(item => item.name.toLocaleLowerCase() === name.toLocaleLowerCase())
  if (existing && !(await confirm(`“${name}”已存在，是否覆盖？`, '保存查找替换规则'))) return
  const rule: RewriteSearchRule = {
    name,
    query: searchQuery.value,
    replacement: replacement.value,
    scope: searchScope.value,
    matchCase: matchCase.value,
    wholeWord: wholeWord.value,
    regex: regex.value,
  }
  const nextRules = existing
    ? searchRules.value.map(item => item.name === existing.name ? rule : item)
    : [...searchRules.value, rule]
  const { data } = await api.put('/api/user/preferences/rewrite-search-rules', nextRules)
  searchRules.value = data || []
  selectedSearchRuleName.value = name
  message.success('查找替换规则已保存到账户')
}

const deleteSearchRule = async () => {
  const name = selectedSearchRuleName.value
  if (!name || !(await confirm(`删除已保存规则“${name}”？`, '删除查找替换规则'))) return
  const nextRules = searchRules.value.filter(item => item.name !== name)
  const { data } = await api.put('/api/user/preferences/rewrite-search-rules', nextRules)
  searchRules.value = data || []
  selectedSearchRuleName.value = ''
  message.success('查找替换规则已删除')
}

const searchPayload = () => ({
  query: searchQuery.value,
  replacement: replacement.value,
  scope: searchScope.value,
  chapterId: chapter.value?.id,
  volumeTitle: chapter.value?.volumeTitle,
  matchCase: matchCase.value,
  wholeWord: wholeWord.value,
  regex: regex.value,
})

const runSearch = async () => {
  if (!searchQuery.value) return
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/search`, searchPayload())
  searchResult.value = data
}

const applyReplace = async () => {
  if (!searchResult.value || !(await saveNow())) return
  const approved = await confirm(
    `将在 ${searchResult.value.results.length} 个章节中替换 ${searchResult.value.totalMatches} 处，并为每个改动章节保留历史修订。`,
    '确认全部替换',
  )
  if (!approved) return
  const revisions = Object.fromEntries(searchResult.value.results.map(item => {
    const chapterSummary = chapters.value.find(chapterItem => chapterItem.id === item.chapterId)
    return [item.chapterId, chapterSummary?.revision]
  }))
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/replace`, {
    ...searchPayload(),
    revisions,
  })
  message.success(`已替换 ${data.matches} 处，修改 ${data.changedChapters} 章`)
  await loadChapters()
  if (chapter.value && data.chapters.some((item: ChapterSummary) => item.id === chapter.value?.id)) {
    await loadChapter(chapter.value.id)
  }
  await runSearch()
}

const goToSearchResult = async (chapterId: number) => {
  if (chapter.value?.id !== chapterId && !(await selectChapter(chapterId))) return
  searchOpen.value = false
  editor.value?.commands.focus()
}

const openSnapshots = async () => {
  if (!(await saveNow())) return
  await loadSnapshotInfo()
  snapshotsOpen.value = true
}

const loadSnapshotInfo = async () => {
  const [snapshotResponse, storageResponse] = await Promise.all([
    api.get(`/api/rewrite/projects/${projectId}/snapshots`),
    api.get(`/api/rewrite/projects/${projectId}/snapshots/storage`),
  ])
  snapshots.value = snapshotResponse.data || []
  snapshotStorage.value = storageResponse.data
}

const downloadExport = async () => {
  if (savingPromise && !(await savingPromise)) return
  if (saveState.value !== 'saved') {
    const approved = await confirm(
      '当前有未保存修改。导出会使用服务器上最近一次成功保存的内容，继续吗？',
      '导出已保存内容',
    )
    if (!approved) return
    if (saveTimer) clearTimeout(saveTimer)
  }

  try {
    const { data } = await api.get(`/api/rewrite/projects/${projectId}/export`, {
      params: {
        format: exportFormat.value,
        includeMetadata: exportIncludeMetadata.value,
        includeChapterTitles: exportIncludeChapterTitles.value,
        chapterTitleStyle: exportChapterTitleStyle.value,
        chapterSpacing: exportChapterSpacing.value,
      },
      responseType: 'blob',
    })
    const url = URL.createObjectURL(data)
    const anchor = document.createElement('a')
    anchor.href = url
    const baseName = (project.value?.versionName || '重写稿')
      .replace(/[\\/:*?"<>|]/g, '_')
      .trim() || '重写稿'
    anchor.download = `${baseName}.${exportFormat.value}`
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
    exportOpen.value = false
    message.success('导出文件已生成')
  } catch {
    message.error('导出失败，请稍后重试')
  }
}

const createSnapshot = async () => {
  if (!(await saveNow())) return
  const name = window.prompt('快照名称', `手动快照 ${new Date().toLocaleString()}`)
  if (!name?.trim()) return
  await api.post(`/api/rewrite/projects/${projectId}/snapshots`, { name: name.trim() })
  await loadSnapshotInfo()
  message.success('整书快照已保存')
}

const deleteSnapshot = async (snapshot: { id: number; name: string }) => {
  if (!(await confirm(`删除命名快照“${snapshot.name}”后无法从此快照恢复。`, '删除快照'))) return
  await api.delete(`/api/rewrite/projects/${projectId}/snapshots/${snapshot.id}`)
  await loadSnapshotInfo()
  message.success('命名快照已删除')
}

const formatBytes = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

const restoreSnapshot = async (snapshot: { id: number; name: string }) => {
  if (!(await saveNow())) return
  const { data: preview } = await api.get(
    `/api/rewrite/projects/${projectId}/snapshots/${snapshot.id}/preview`,
  )
  const approved = await confirm(
    `恢复“${snapshot.name}”将新增 ${preview.added} 章、移除 ${preview.removed} 章、移动 ${preview.moved} 章，并恢复 ${preview.changed} 章的标题、卷、状态或正文。\n\n新增：${(preview.addedTitles || []).join('、') || '无'}\n移除：${(preview.removedTitles || []).join('、') || '无'}\n\n当前内容会自动保存为恢复前快照。`,
    '恢复整书快照',
  )
  if (!approved) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/snapshots/${snapshot.id}/restore`,
  )
  await loadChapters()
  if (chapters.value.length) await loadChapter(chapters.value[0].id)
  snapshotsOpen.value = false
  message.success(`已恢复 ${data.restored} 章，恢复前快照也已保存`)
}

const openMemos = async () => {
  await loadMemos()
  memoFormOpen.value = false
  memoFilterQuery.value = ''
  memosOpen.value = true
}

const goToMemoChapter = async (chapterId: number) => {
  if (!(await selectChapter(chapterId))) return
  memosOpen.value = false
  editor.value?.commands.focus()
}

const loadMemos = async () => {
  const { data } = await api.get(`/api/rewrite/projects/${projectId}/memos`, {
    params: { type: memoType.value },
  })
  memos.value = data || []
}

const changeMemoType = async (type: 'NOTE' | 'GLOSSARY') => {
  memoType.value = type
  await loadMemos()
}

const editMemo = (item?: (typeof memos.value)[number]) => {
  memoEditingId.value = item?.id ?? null
  memoTitle.value = item?.title || ''
  memoContent.value = item?.content || ''
  memoState.value = item?.state || 'TODO'
  memoAliasesText.value = item?.aliases?.join('，') || ''
  memoChapterId.value = item?.chapterId ?? undefined
  memoAnchorPosition.value = item?.anchorPosition ?? undefined
  memoFormOpen.value = true
}

const saveMemo = async () => {
  if (!memoTitle.value.trim()) return message.warning('请填写标题')
  const payload = {
    type: memoType.value,
    title: memoTitle.value.trim(),
    content: memoContent.value,
    state: memoState.value,
    aliases: memoType.value === 'GLOSSARY'
      ? memoAliasesText.value.split(/[，,]/).map(alias => alias.trim()).filter(Boolean)
      : [],
    chapterId: memoChapterId.value ?? null,
    anchorPosition: memoAnchorPosition.value ?? null,
  }
  if (memoEditingId.value === null) {
    await api.post(`/api/rewrite/projects/${projectId}/memos`, payload)
  } else {
    await api.put(`/api/rewrite/projects/${projectId}/memos/${memoEditingId.value}`, payload)
  }
  await loadMemos()
  await loadChapters()
  memoFormOpen.value = false
  message.success('资料已保存，可在其他设备继续查看')
}

const deleteMemo = async () => {
  if (memoEditingId.value === null) return
  if (!(await confirm('删除后无法恢复此资料条目。', '删除资料'))) return
  await api.delete(`/api/rewrite/projects/${projectId}/memos/${memoEditingId.value}`)
  await loadMemos()
  await loadChapters()
  memoFormOpen.value = false
}

const toggleSelectionMode = () => {
  selectionMode.value = !selectionMode.value
  selectedChapterIds.value = []
}

const selectAllChapters = () => {
  selectedChapterIds.value = selectedChapterIds.value.length === chapters.value.length
    ? [] : chapters.value.map(item => item.id)
}

const toggleChapterSelection = (chapterId: number, selected: boolean) => {
  const ids = new Set(selectedChapterIds.value)
  if (selected) ids.add(chapterId)
  else ids.delete(chapterId)
  selectedChapterIds.value = [...ids]
}

const runBulkAction = async (
  action: 'STATUS' | 'VOLUME' | 'DELETE' | 'RENUMBER',
  value: string | null,
) => {
  if (!selectedChapterIds.value.length || !(await saveNow())) return
  const revisions = Object.fromEntries(selectedChapterIds.value.map(id => [
    id, chapters.value.find(item => item.id === id)?.revision,
  ]))
  const { data } = await api.post(`/api/rewrite/projects/${projectId}/chapters/bulk`, {
    chapterIds: selectedChapterIds.value,
    action,
    value,
    revisions,
  })
  const previousChapterId = chapter.value?.id
  selectedChapterIds.value = []
  await loadChapters()
  const nextChapterId = chapters.value.some(item => item.id === previousChapterId)
    ? previousChapterId : chapters.value[0]?.id
  if (nextChapterId) await loadChapter(nextChapterId)
  if (action === 'DELETE' && chapters.value.length === 0) chapter.value = null
  const skipped = (data.skippedTitles || []) as string[]
  const resultMessage = skipped.length
    ? `已编号 ${data.changedCount} 章，${skipped.length} 个标题无法识别并已跳过；恢复点已保存`
    : `已处理 ${data.changedCount} 章；恢复点已保存`
  message.success(resultMessage)
}

const bulkSetStatus = async (status: string) => {
  await runBulkAction('STATUS', status)
}

const bulkSetVolume = async () => {
  const volume = window.prompt('卷名称；留空可移出卷')
  if (volume === null) return
  await runBulkAction('VOLUME', volume)
}

const copySelectedChapterContent = async () => {
  if (!selectedChapterIds.value.length) return
  try {
    const { data } = await api.post<string>(
      `/api/rewrite/projects/${projectId}/chapters/copy`,
      { chapterIds: selectedChapterIds.value },
      { responseType: 'text' },
    )
    await navigator.clipboard.writeText(data)
    message.success(`已复制 ${selectedChapterIds.value.length} 章正文`)
  } catch {
    message.error('复制正文失败，请检查剪贴板权限后重试')
  }
}

const openRenumber = async () => {
  if (!(await saveNow())) return
  renumberStart.value = 1
  renumberOpen.value = true
}

const applyRenumber = async () => {
  const renameCount = renumberPreview.value.filter(item => !item.skipped).length
  if (!renameCount) return
  const approved = await confirm(
    `将按目录顺序为 ${renameCount} 个可识别的章节标题重新编号。操作前会自动保存整书恢复点。`,
    '应用章节编号',
  )
  if (!approved) return
  await runBulkAction('RENUMBER', `${renumberStyle.value}:${renumberStart.value}`)
  renumberOpen.value = false
}

const openQuickJump = async () => {
  if (!(await saveNow())) return
  quickJumpQuery.value = ''
  quickJumpOpen.value = true
  await nextTick()
  quickJumpInput.value?.focus?.()
}

const jumpToChapter = async (chapterId: number) => {
  if (!(await selectChapter(chapterId))) return
  quickJumpOpen.value = false
  editor.value?.commands.focus()
}

const jumpToFirstMatch = () => {
  const first = quickJumpMatches.value[0]
  if (first) void jumpToChapter(first.id)
}

const bulkDelete = async () => {
  if (!(await confirm(`将删除所选 ${selectedChapterIds.value.length} 章，整书快照会自动保存以便恢复。`, '批量删除章节'))) return
  await runBulkAction('DELETE', null)
}

const showRevision = async (revision: number) => {
  if (!chapter.value) return
  const { data } = await api.get(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions/${revision}`,
  )
  selectedHistoryRevision.value = revision
  historyShowDiff.value = false
  if (data.contentFormatVersion === 1) {
    try {
      historyContent.value = richDocumentText(JSON.parse(data.content))
    } catch {
      historyContent.value = '此修订的富文本内容无法读取。'
    }
  } else {
    historyContent.value = data.content
  }
}

const restoreHistory = async () => {
  if (!chapter.value || selectedHistoryRevision.value === null) return
  if (!(await confirm('恢复历史正文后会生成新修订，当前正文仍保留在历史中。', '恢复章节历史'))) return
  const { data } = await api.post(
    `/api/rewrite/projects/${projectId}/chapters/${chapter.value.id}/revisions/${selectedHistoryRevision.value}/restore`,
    { revision: chapter.value.revision },
  )
  chapter.value = data
  draftContent.value = data.content
  setEditorDocument(data.content)
  sessionStorage.removeItem(draftKey(data.id))
  saveState.value = 'saved'
  updateSummary()
  historyOpen.value = false
  await loadProject()
}

const toggleSource = () => {
  showSource.value = !showSource.value
  if (!showSource.value) showDiff.value = false
}
const openReader = async () => {
  if (!project.value || !(await saveNow())) return
  await router.push({ path: `/reader/${project.value.bookId}`,
    query: { versionId: String(project.value.rewriteVersionId) } })
}

const handleProjectAction = async (action: string) => {
  if (!project.value || !(await saveNow())) return
  if (action === 'rename' || action === 'description') {
    const oldValue = action === 'rename' ? project.value.name : project.value.description || ''
    const value = window.prompt(action === 'rename' ? '项目名称' : '项目说明', oldValue)
    if (value === null || (action === 'rename' && !value.trim())) return
    const { data } = await api.patch(`/api/rewrite/projects/${projectId}`,
      action === 'rename' ? { name: value.trim() } : { description: value.trim() })
    project.value = data
    return
  }
  if (action === 'delete') {
    const count = project.value.chapterCount
    const approved = await confirm(
      `将删除重写项目、重写版本和 ${count} 个有效章节，并清除该版本的阅读进度。源版本保留。此操作无法恢复。`,
      '删除项目及版本',
    )
    if (!approved) return
    await api.delete(`/api/rewrite/projects/${projectId}`, {
      params: { deleteVersion: true },
    })
    await router.push('/rewrite')
    return
  }
  if (action === 'archive' && !(await confirm('归档后项目从默认列表隐藏，已保存版本仍可阅读。', '归档项目'))) return
  if (action === 'complete' && !(await confirm('完成后章节将固化，若需继续修改须创建新的重写项目。', '完成重写'))) return
  const path = action === 'restore' ? 'pause' : action
  try {
    const { data } = await api.post(`/api/rewrite/projects/${projectId}/${path}`,
      path === 'complete' ? { force: false } : undefined,
      { headers: { 'X-Suppress-Error-Toast': 'true' } },
    )
    project.value = data
    message.success('项目状态已更新')
  } catch (error: any) {
    if (path !== 'complete' || error.response?.status !== 409) {
      message.error(error.response?.data?.message || '项目操作失败')
      return
    }
    const unfinished = project.value.chapterCount - project.value.completedCount
    if (!(await confirm(`仍有 ${unfinished} 章未标记完成。确定继续完成重写？`, '确认未完成章节'))) return
    const { data } = await api.post(`/api/rewrite/projects/${projectId}/complete`, { force: true })
    project.value = data
    message.success('重写版本已完成')
  }
}

const copyDraft = async () => {
  const text = editor.value?.state.doc.textBetween(0, editor.value.state.doc.content.size, '\n\n')
    || draftContent.value
  await navigator.clipboard.writeText(text)
  message.success('本地文字已复制')
}

const handleEditorKeydown = (event: KeyboardEvent) => {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void saveNow()
  }
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'p') {
    event.preventDefault()
    void openQuickJump()
  }
}

const updateEditorHeight = () => {
  if (showDiff.value) return

  const workspace = rewriteWorkspaceRef.value
  const editorSurface = draftPaneRef.value?.querySelector<HTMLElement>('.rich-editor-surface')
  if (!workspace || !editorSurface) return

  const bottomGap = 24
  const availableHeight = Math.floor(window.innerHeight - editorSurface.getBoundingClientRect().top - bottomGap)
  workspace.style.setProperty('--rewrite-editor-height', `${Math.max(220, availableHeight)}px`)
}

const scheduleEditorHeightUpdate = () => {
  if (editorHeightFrame !== undefined) window.cancelAnimationFrame(editorHeightFrame)
  editorHeightFrame = window.requestAnimationFrame(() => {
    editorHeightFrame = undefined
    updateEditorHeight()
  })
}

watch(
  [() => chapter.value?.id, showSource, showDiff, editable, recoveredDraft, () => saveState.value === 'conflict'],
  scheduleEditorHeightUpdate,
  { flush: 'post' },
)

watch(editable, value => editor.value?.setEditable(value))
watch(
  [showDiff, () => chapter.value?.sourceContent, readableDraft],
  ([visible]) => {
    if (visible) {
      if (diffReviewRenderTimer) clearTimeout(diffReviewRenderTimer)
      diffReviewRenderTimer = setTimeout(() => {
        diffReviewRenderTimer = undefined
        void renderDiffReview()
      }, 120)
      return
    }
    if (diffReviewRenderTimer) clearTimeout(diffReviewRenderTimer)
    diffReviewRenderTimer = undefined
    diffReviewRows.value = []
    diffReviewChangeCount.value = 0
    diffReviewPosition.value = -1
  },
  { flush: 'post' },
)

const beforeUnload = (event: BeforeUnloadEvent) => {
  if (saveState.value !== 'saved') {
    event.preventDefault()
  }
}

onBeforeRouteLeave(async () => {
  if (!(await saveNow())) return false
  await persistCurrentPosition()
  return true
})
onMounted(async () => {
  loading.value = true
  try {
    await loadChapters()
    if (chapters.value.length) {
      const preferred = chapters.value.find(item => item.id === project.value?.currentChapterId)
      await loadChapter(preferred?.id || chapters.value[0].id)
    }
  } finally {
    loading.value = false
  }
  window.addEventListener('resize', scheduleEditorHeightUpdate, { passive: true })
  window.addEventListener('beforeunload', beforeUnload)
  window.addEventListener('keydown', handleEditorKeydown)
  await nextTick()
  updateEditorHeight()
})
onBeforeUnmount(() => {
  if (saveTimer) clearTimeout(saveTimer)
  if (cursorSaveTimer) clearTimeout(cursorSaveTimer)
  if (diffReviewRenderTimer) clearTimeout(diffReviewRenderTimer)
  if (editorHeightFrame !== undefined) window.cancelAnimationFrame(editorHeightFrame)
  window.removeEventListener('resize', scheduleEditorHeightUpdate)
  window.removeEventListener('beforeunload', beforeUnload)
  window.removeEventListener('keydown', handleEditorKeydown)
  editor.value?.destroy()
})
</script>

<style scoped>
.rewrite-workspace {
  --rewrite-editor-height: clamp(220px, calc(100vh - 380px), 900px);
  max-width: 1600px;
  min-height: 75vh;
  margin: 0 auto;
  padding: 20px 22px 110px;
  color: var(--text-primary, #24342d);
}
.workspace-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 16px; padding: 12px 0 24px; }
.workspace-header h1 { margin: 8px 0 4px; font-size: 28px; }
.workspace-header p { margin: 0; color: var(--text-secondary, #748078); }
.back-link { color: var(--el-color-primary); text-decoration: none; }
.header-actions, .chapter-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.save-indicator { font-size: 12px; color: var(--text-secondary, #748078); }
.header-actions kbd { padding: 1px 4px; border: 1px solid var(--el-border-color); border-radius: 4px; font: inherit; font-size: 10px; }
.save-indicator.dirty, .save-indicator.error, .save-indicator.conflict { color: #bb6b29; }
.workspace-body { display: grid; grid-template-columns: minmax(210px, 250px) minmax(0, 1fr); gap: 18px; align-items: start; }
.chapter-sidebar, .editor-panel { border: 1px solid var(--el-border-color-light, #d8dfd9); border-radius: 18px; background: var(--el-bg-color, #fff); }
.chapter-sidebar { max-height: calc(100vh - 170px); overflow: auto; padding: 15px; }
.sidebar-heading { display: flex; align-items: center; justify-content: space-between; }
.sidebar-progress { margin: 9px 0 18px; font-size: 12px; color: var(--text-secondary, #748078); }
.chapter-sidebar nav { display: grid; gap: 5px; }
.chapter-row { display: flex; align-items: center; gap: 7px; min-width: 0; }
.chapter-row input { flex: 0 0 auto; accent-color: var(--el-color-primary); }
.chapter-row .chapter-link { flex: 1; min-width: 0; }
.selection-tools { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; margin: -8px 0 10px; }
.selection-tools span { color: var(--text-secondary, #748078); font-size: 11px; }
.chapter-link { display: grid; gap: 3px; width: 100%; padding: 10px 12px; border: 0; border-radius: 10px; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.chapter-link.selected { background: color-mix(in srgb, var(--el-color-primary) 13%, transparent); }
.chapter-link:hover { background: color-mix(in srgb, var(--el-color-primary) 8%, transparent); }
.chapter-title { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chapter-link small { color: var(--text-secondary, #748078); }
.volume-heading { display: block; padding: 12px 10px 4px; font-size: 12px; color: var(--el-color-primary); }
.deleted-chapters { display: grid; gap: 4px; margin-top: 14px; padding-top: 10px; border-top: 1px solid #8883; }
.restore-chapter-button { padding: 7px; border: 0; border-radius: 8px; background: #8881; color: inherit; text-align: left; cursor: pointer; }
.editor-panel { min-width: 0; padding: 22px; }
.chapter-toolbar { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.chapter-title-row { display: flex; align-items: center; gap: 10px; flex: 1; }
.chapter-title-row input { min-width: 120px; flex: 1; border: 0; border-bottom: 1px solid #8884; background: transparent; color: inherit; font-size: 22px; font-weight: 700; }
.chapter-title-row input:focus { outline: none; border-color: var(--el-color-primary); }
.chapter-title-row span { font-size: 12px; color: var(--text-secondary, #748078); }
.chapter-status-control { display: flex; position: relative; max-width: 480px; margin: 20px 0; padding: 4px; border-radius: 14px; background: rgba(128, 145, 135, .14); }
.chapter-status-slider { position: absolute; inset: 4px auto 4px 4px; width: calc((100% - 8px) / 4); border-radius: 10px; background: var(--el-bg-color, #fff); box-shadow: 0 2px 8px #0002; transition: transform .25s ease; }
.chapter-status-control button { z-index: 1; flex: 1; padding: 8px; border: 0; background: none; color: inherit; cursor: pointer; }
.chapter-status-control button[aria-pressed='true'] { font-weight: 700; }
.draft-notice, .conflict-notice { padding: 10px 14px; margin-bottom: 12px; border-radius: 10px; background: #fff1d8; color: #744711; }
.writing-surface { display: grid; grid-template-columns: 1fr; gap: 16px; }
.writing-surface.comparing {
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  grid-template-rows: auto auto var(--rewrite-editor-height);
}
.source-pane, .draft-pane { min-width: 0; }
.writing-surface.comparing .source-pane,
.writing-surface.comparing .draft-pane {
  display: grid;
  grid-row: 1 / 4;
  grid-template-rows: subgrid;
}
.writing-surface.comparing .source-pane { grid-column: 1; }
.writing-surface.comparing .draft-pane { grid-column: 2; }
.writing-surface.comparing .pane-label { grid-row: 1; }
.writing-surface.comparing .rich-toolbar { grid-row: 2; }
.pane-label { display: flex; justify-content: space-between; align-items: center; min-height: 32px; color: var(--text-secondary, #748078); font-size: 12px; }
.source-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.source-pane pre,
.rich-editor-surface {
  box-sizing: border-box;
  width: 100%;
  height: var(--rewrite-editor-height);
  min-height: 0;
  margin: 0;
  padding: 22px;
  border: 1px solid #8883;
  border-radius: 12px;
  background: var(--el-fill-color-lighter, #fafbf9);
  color: inherit;
  font-family: inherit;
  font-size: 16px;
  line-height: 1.9;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.source-pane pre { overflow: auto; }
.writing-surface.comparing .source-pane pre,
.writing-surface.comparing .rich-editor-surface {
  grid-row: 3;
  min-height: 0;
  max-height: none;
}
.writing-surface.comparing .rich-editor-surface { overflow: auto; }
.writing-surface.diff-review-mode {
  display: block;
}

.diff-review {
  min-width: 0;
}

.diff-review-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 12px;
}

.diff-review-summary,
.diff-review-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.diff-review-summary span,
.diff-review-position {
  color: var(--text-secondary, #748078);
  font-size: 12px;
}

.diff-review-column-labels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 8px;
  color: var(--text-secondary, #748078);
  font-size: 12px;
}

.diff-review-column-labels strong:last-child {
  padding-left: 10px;
}

.diff-review-message {
  margin: 0;
  padding: 18px;
  border: 1px solid var(--el-border-color-light, #d8dfd9);
  border-radius: 12px;
  background: var(--el-fill-color-lighter, #fafbf9);
  color: var(--text-secondary, #748078);
}

.diff-review-content {
  position: relative;
  contain: paint;
  max-height: clamp(320px, 55vh, 760px);
  overflow: auto;
  border: 1px solid var(--el-border-color-light, #d8dfd9);
  border-radius: 12px;
  background: var(--el-bg-color, #fff);
}

.diff-review-content :deep(.d2h-wrapper) {
  background: var(--el-bg-color, #fff);
  color: var(--text-primary, #24342d);
  font-family: inherit;
}

.diff-review-content :deep(.d2h-file-header) {
  display: none;
}

.diff-review-content :deep(.d2h-file-wrapper) {
  border: 0;
}

.diff-review-content :deep(.d2h-file-side-diff) {
  overflow-x: auto;
  overflow-y: hidden;
}

.diff-review-content :deep(.d2h-diff-table) {
  font-family: inherit;
  font-size: 14px;
}

.diff-review-content :deep(.d2h-code-side-linenumber) {
  min-width: 46px;
  width: 46px;
  color: var(--text-secondary, #748078);
}

.diff-review-content :deep(.d2h-code-side-line),
.diff-review-content :deep(.d2h-code-line-ctn) {
  white-space: pre;
}

.diff-review-content :deep(.d2h-code-line-prefix) {
  user-select: none;
}
.diff-segment--added { border-radius: 3px; background: color-mix(in srgb, var(--el-color-success) 20%, transparent); }
.diff-segment--removed { border-radius: 3px; background: color-mix(in srgb, var(--el-color-danger) 18%, transparent); text-decoration: line-through; }
.rich-toolbar { display: flex; flex-wrap: wrap; gap: 6px; padding: 8px 0; }
.rich-editor-surface {
  overflow: auto;
  outline-color: var(--el-color-primary);
}
.rich-editor-surface:focus-within { border-color: var(--el-color-primary); }
.rich-editor-surface.is-readonly { overflow: auto; }
.rich-editor-surface :deep(.tiptap-content) { min-height: 100%; outline: none; white-space: pre-wrap; overflow-wrap: anywhere; }
.rich-editor-surface :deep(.tiptap-content > :first-child) { margin-top: 0; }
.rich-editor-surface :deep(.tiptap-content > :last-child) { margin-bottom: 0; }
.rich-editor-surface :deep(blockquote) { margin: 1em 0; padding-left: 1em; border-left: 3px solid var(--el-border-color); color: var(--text-secondary, #748078); }
.rich-editor-surface :deep(ul), .rich-editor-surface :deep(ol) { padding-left: 1.6em; }
.rich-editor-surface :deep(hr) { margin: 1.5em auto; width: 30%; border: 0; border-top: 1px solid var(--el-border-color); }
.editor-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 20px; }
.empty-panel { min-height: 300px; display: grid; place-items: center; }
.history-dialog { display: grid; grid-template-columns: 210px minmax(0, 1fr); gap: 14px; min-height: 300px; }
.history-list { display: grid; align-content: start; gap: 6px; max-height: 50vh; overflow: auto; }
.history-list button { display: grid; gap: 4px; padding: 9px; border: 0; border-radius: 9px; background: #8881; color: inherit; text-align: left; cursor: pointer; }
.history-list button.selected { background: color-mix(in srgb, var(--el-color-primary) 18%, transparent); }
.history-list small { color: var(--text-secondary, #748078); }
.history-content-panel { display: grid; align-content: start; gap: 8px; min-width: 0; }
.history-dialog pre, .history-diff { box-sizing: border-box; margin: 0; padding: 16px; max-height: 50vh; overflow: auto; border-radius: 10px; background: var(--el-fill-color-lighter, #fafbf9); white-space: pre-wrap; overflow-wrap: anywhere; }
.rewrite-search { display: grid; gap: 12px; max-height: 65vh; overflow: auto; }
.search-rule-controls { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
.search-rule-controls :deep(.el-select) { min-width: 180px; flex: 1; }
.rewrite-search-options, .rewrite-search-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; }
.search-summary { margin: 4px 0 0; color: var(--text-secondary, #748078); }
.search-result { display: grid; gap: 6px; padding: 12px; border: 1px solid #8883; border-radius: 10px; background: var(--el-fill-color-lighter, #fafbf9); color: inherit; text-align: left; cursor: pointer; }
.search-result small { overflow: hidden; color: var(--text-secondary, #748078); text-overflow: ellipsis; white-space: nowrap; }
.search-result .replace-preview-sample { color: var(--el-color-success); }
.snapshot-list { display: grid; gap: 8px; max-height: 60vh; overflow: auto; }
.snapshot-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px; border: 1px solid #8883; border-radius: 10px; }
.snapshot-item div { display: grid; gap: 5px; }
.snapshot-item small { color: var(--text-secondary, #748078); }
.snapshot-actions { display: flex !important; grid-auto-flow: column; gap: 4px !important; }
.export-settings { display: grid; gap: 12px; margin-bottom: 16px; }
.export-settings label { display: grid; gap: 6px; color: var(--text-secondary, #748078); }
.export-settings :deep(.el-select) { width: 100%; }
.renumber-settings { display: grid; grid-template-columns: minmax(0, 1fr) 150px; gap: 12px; }
.renumber-preview { display: grid; gap: 6px; max-height: 42vh; margin-top: 12px; overflow: auto; }
.renumber-preview article { display: grid; grid-template-columns: minmax(0, 1fr) 24px minmax(0, 1fr); align-items: center; gap: 8px; padding: 9px; border: 1px solid var(--el-border-color-light); border-radius: 8px; }
.renumber-preview article span, .renumber-preview article strong { overflow-wrap: anywhere; }
.renumber-preview article.skipped strong { color: var(--el-color-warning); }
.quick-jump { display: grid; gap: 7px; max-height: 55vh; overflow: auto; }
.quick-jump button { display: grid; gap: 3px; padding: 10px 12px; border: 1px solid var(--el-border-color-light); border-radius: 9px; background: var(--el-fill-color-lighter); color: inherit; text-align: left; cursor: pointer; }
.quick-jump button.selected { border-color: var(--el-color-primary); background: color-mix(in srgb, var(--el-color-primary) 12%, transparent); }
.quick-jump small { color: var(--text-secondary); }
.memo-workspace { display: grid; gap: 14px; }
.memo-type-control { position: relative; display: grid; grid-template-columns: 1fr 1fr; padding: 4px; border-radius: 13px; background: var(--el-fill-color-light); }
.memo-type-control > span { position: absolute; inset: 4px auto 4px 4px; width: calc((100% - 8px) / 2); border-radius: 10px; background: var(--el-bg-color); box-shadow: 0 2px 8px #0002; transition: transform .25s ease; }
.memo-type-control button { position: relative; z-index: 1; padding: 9px; border: 0; background: transparent; color: inherit; cursor: pointer; }
.memo-type-control button[aria-pressed='true'] { font-weight: 700; }
.memo-list { display: grid; gap: 8px; max-height: 55vh; overflow: auto; }
.memo-card { display: flex; align-items: center; gap: 8px; padding: 8px; border: 1px solid #8883; border-radius: 10px; background: var(--el-fill-color-lighter); }
.memo-entry { display: grid; flex: 1; gap: 5px; min-width: 0; padding: 4px; border: 0; background: transparent; color: inherit; text-align: left; cursor: pointer; }
.memo-list small { overflow: hidden; color: var(--text-secondary, #748078); text-overflow: ellipsis; white-space: nowrap; }
.memo-form { display: grid; gap: 10px; }
@media (prefers-reduced-motion: reduce) { .memo-type-control > span { transition: none; } }
@media (max-width: 820px) {
  .workspace-body { grid-template-columns: 1fr; }
  .chapter-sidebar { max-height: 180px; }
  .writing-surface.comparing { grid-template-columns: 1fr; grid-template-rows: none; }
  .writing-surface.comparing .source-pane,
  .writing-surface.comparing .draft-pane { display: block; grid-column: auto; grid-row: auto; }
  .writing-surface.comparing .pane-label,
  .writing-surface.comparing .rich-toolbar,
  .writing-surface.comparing .source-pane pre,
  .writing-surface.comparing .rich-editor-surface { grid-row: auto; }
}
@media (prefers-reduced-motion: reduce) { .chapter-status-slider { transition: none; } }
</style>
