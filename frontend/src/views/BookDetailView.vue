<template>
  <div class="book-detail-view">
    <!-- 加载中 -->
    <div v-if="loading" class="loading">
      <div class="loading-spinner"></div>
      <p>加载中...</p>
    </div>

    <!-- 书籍内容 -->
    <div v-else-if="book" class="book-content glass">
      <div class="detail-toolbar">
        <button class="back-btn" @click="$router.back()">
          <span>‹</span>
          <span>返回书库</span>
        </button>
        <div class="detail-toolbar-actions">
          <button class="btn" @click="showAddToListDialog = true">
            <span>📚</span>
            <span>加入书单</span>
          </button>
          <button class="btn" :class="{ active: book.onShelf }" @click="handleToggleShelf">
            <span>{{ book.onShelf ? '📚' : '➕' }}</span>
            <span>{{ book.onShelf ? '已在书架' : '加入书架' }}</span>
          </button>
          <el-dropdown
            trigger="click"
            @command="handleBookActionCommand"
            @visible-change="moreActionsOpen = $event"
          >
            <button
              class="btn more-actions-button"
              type="button"
              :aria-expanded="moreActionsOpen"
            >
              <span>更多操作</span>
              <ArrowDown
                class="more-actions-arrow"
                :class="{ 'is-open': moreActionsOpen }"
                aria-hidden="true"
              />
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="scrape" :disabled="scraping">
                  🔍 {{ scraping ? '刮削中...' : '刮削元数据' }}
                </el-dropdown-item>
                <el-dropdown-item command="cover" :disabled="downloadingCover">
                  🖼️ {{ downloadingCover ? '下载中...' : '下载封面' }}
                </el-dropdown-item>
                <el-dropdown-item
                  v-if="selectedVersion?.primaryVersion"
                  command="reparse-book"
                  :disabled="reparsing"
                >
                  🔄 {{ reparsing ? '解析中...' : '重新解析书籍' }}
                </el-dropdown-item>
                <el-dropdown-item command="repair">
                  🔧 内容修复
                </el-dropdown-item>
                <el-dropdown-item divided command="delete">
                  <span class="danger-menu-item">🗑️ 移入回收站</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <section class="book-hero">
        <aside class="cover-column">
          <div class="book-cover">
            <img
              v-if="book.coverUrl"
              :src="getCoverUrl(book.coverUrl)"
              alt="封面"
              class="cover-image"
              :class="{ 'is-hidden': isBookCoverHidden(book.id) }"
            />
            <div v-else class="no-cover">
              <span>{{ book.title.charAt(0) }}</span>
            </div>
            <BookCoverPrivacyButton
              v-if="book.coverUrl"
              :book-id="book.id"
              :book-title="book.title"
            />
          </div>
          <div class="cover-action-row">
            <button
              class="btn cover-action-button"
              type="button"
              :disabled="uploadingCover || randomizingCover"
              @click="coverInput?.click()"
            >
              <span aria-hidden="true">🖼️</span>
              <span>{{ uploadingCover ? '上传中...' : '修改封面' }}</span>
            </button>
            <button
              class="btn cover-action-button"
              type="button"
              :disabled="uploadingCover || randomizingCover"
              @click="handleRandomCover"
            >
              <span aria-hidden="true">🎲</span>
              <span>{{ randomizingCover ? '随机中...' : '随机封面' }}</span>
            </button>
          </div>
          <input
            ref="coverInput"
            class="cover-file-input"
            type="file"
            accept="image/jpeg,image/png,image/webp,image/gif"
            :disabled="uploadingCover"
            @change="handleCoverUpload"
          />
        </aside>

        <div class="book-summary">
          <div class="book-title-wrapper">
            <h1 v-if="!editingTitle" class="book-title">{{ book.title }}</h1>
            <div v-else class="title-edit-group">
              <input
                ref="titleInput"
                v-model="editTitleValue"
                class="title-edit-input"
                placeholder="请输入书名"
                :disabled="savingTitle"
                @keyup.enter="saveTitle"
                @keyup.escape="cancelEditTitle"
              />
              <div class="title-edit-actions">
                <button class="btn btn-primary btn-sm" :disabled="savingTitle" @click="saveTitle">
                  {{ savingTitle ? '保存中...' : '保存' }}
                </button>
                <button class="btn btn-sm" :disabled="savingTitle" @click="cancelEditTitle">
                  取消
                </button>
              </div>
            </div>
            <button
              v-if="!editingTitle"
              class="btn-edit-title"
              title="编辑书名"
              @click="startEditTitle"
            >
              编辑
            </button>
          </div>

          <div class="book-byline">
            <strong>{{ book.author || '未知作者' }}</strong>
            <span v-if="book.publisher">{{ book.publisher }}</span>
            <span v-if="book.publishDate">{{ book.publishDate }}</span>
          </div>

          <div class="book-badges">
            <span class="detail-badge primary">{{ selectedVersionFormat.toUpperCase() }}</span>
            <span v-if="versions.length > 1" class="detail-badge">
              {{ versions.length }} 个版本
            </span>
            <span v-if="book.language" class="detail-badge">{{ book.language }}</span>
            <span v-if="book.isbn" class="detail-badge">ISBN {{ book.isbn }}</span>
          </div>

          <div class="book-rating">
            <span class="rating-label">我的评分</span>
            <div class="rating-stars" aria-label="书籍评分">
              <button
                v-for="i in 5"
                :key="i"
                class="star"
                :class="{ active: i <= book.rating }"
                :aria-label="`${i} 星`"
                @click="setRating(i)"
              >
                ★
              </button>
            </div>
          </div>

          <div v-if="hasReadingProgress" class="current-reading-card">
            <div class="current-reading-content">
              <div class="current-reading-heading">
                <span>上次读到</span>
                <strong>{{ readingProgress?.totalProgress || 0 }}%</strong>
              </div>
              <strong class="current-reading-title">{{ currentReadingChapter }}</strong>
              <el-progress
                :percentage="readingProgress?.totalProgress || 0"
                :stroke-width="7"
                :show-text="false"
              />
            </div>
          </div>

          <div class="primary-actions">
            <button class="btn btn-primary btn-large read-button" @click="handleRead">
              <span>📖</span>
              <span>{{ hasReadingProgress ? '继续阅读' : '开始阅读' }}</span>
            </button>
            <button
              class="btn state-button"
              :class="{ active: book.isFavorite }"
              @click="handleToggleFavorite"
            >
              <span>{{ book.isFavorite ? '★' : '☆' }}</span>
              <span>{{ book.isFavorite ? '已收藏' : '收藏' }}</span>
            </button>
            <button
              class="btn state-button"
              :class="{ active: book.isWanted }"
              @click="handleToggleWanted"
            >
              <span>{{ book.isWanted ? '✓' : '＋' }}</span>
              <span>{{ book.isWanted ? '想读中' : '想读' }}</span>
            </button>
          </div>

          <div class="organization-panel">
            <div class="organization-row">
              <span class="organization-label">分类</span>
              <el-select
                class="category-select"
                :model-value="book.categoryId || ''"
                placeholder="未分类"
                filterable
                @change="handleCategoryChange"
              >
                <el-option label="未分类" value="" />
                <el-option
                  v-for="category in categoryStore.flatTree"
                  :key="category.id"
                  :value="category.id"
                  :label="`${'　'.repeat(category.depth)}${category.name}`"
                />
              </el-select>
            </div>
            <div class="organization-row">
              <span class="organization-label">标签</span>
              <el-select
                v-model="selectedTagIds"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                placeholder="添加标签"
                class="book-tag-select"
                :disabled="savingTags"
                @change="handleTagsChange"
              >
                <el-option
                  v-for="tag in tagStore.tags"
                  :key="tag.id"
                  :label="tag.name"
                  :value="tag.id"
                >
                  <span class="tag-option-dot" :style="{ backgroundColor: tag.color }"></span>
                  <span>{{ tag.name }}</span>
                </el-option>
              </el-select>
              <button
                class="btn btn-text tag-manage-link"
                type="button"
                @click="openCreateTagDialog"
              >
                新建标签
              </button>
            </div>
          </div>
        </div>
      </section>

      <section class="version-panel">
        <div class="version-panel-header">
          <div>
            <span class="section-eyebrow">阅读文件</span>
            <h2>选择版本</h2>
            <p>格式或内容不同的版本会分别保存阅读进度。</p>
          </div>
          <div class="version-header-actions">
            <button
              class="btn"
              type="button"
              :disabled="selectedVersionFormat !== 'txt'"
              :title="selectedVersionFormat !== 'txt' ? '第一期仅支持 TXT 转 EPUB' : '将当前 TXT 转换为 EPUB'"
              @click="openFormatConversion"
            >
              <span>⇄</span><span>转换格式</span>
            </button>
            <label class="btn version-upload-button" :class="{ disabled: uploadingVersion }">
              <span>＋</span>
              <span>{{ uploadingVersion ? '上传中...' : '添加版本' }}</span>
              <input
                type="file"
                accept=".txt,.epub,.pdf,.mobi,.azw3,.docx,.doc,.html,.htm,.md,.cbz,.cbr"
                :disabled="uploadingVersion"
                @change="handleVersionUpload"
              />
            </label>
          </div>
        </div>
        <div class="version-list">
          <div
            v-for="version in versions"
            :key="version.id"
            class="version-item"
            :class="{ active: version.id === selectedVersionId }"
            role="button"
            tabindex="0"
            @click="selectVersion(version.id)"
            @keyup.enter="selectVersion(version.id)"
            @keyup.space.prevent="selectVersion(version.id)"
          >
            <div class="version-format">{{ version.format.toUpperCase() }}</div>
            <div class="version-content">
              <strong>{{ version.displayName }}</strong>
              <span>
                {{ formatFileSize(version.fileSize) }}
                <template v-if="version.chapterCount != null">
                  · {{ version.chapterCount }} 章
                </template>
              </span>
            </div>
            <span v-if="version.primaryVersion" class="version-primary-badge">原始版本</span>
            <span v-if="version.id === selectedVersionId" class="version-selected-badge">
              当前选择
            </span>
            <button
              v-if="!version.primaryVersion"
              class="version-delete-button"
              title="移除该版本"
              @click.stop="deleteVersion(version)"
            >
              🗑️
            </button>
          </div>
        </div>
      </section>

      <!-- 内容区 -->
      <div class="book-body">
        <!-- macOS 26 风格滑块选项卡 -->
        <div class="detail-tabs-scroll">
          <div
            class="detail-segmented-tabs"
            role="tablist"
            aria-label="书籍信息"
            :style="{
              '--detail-tab-count': detailTabs.length,
              '--detail-tab-index': activeTabIndex,
            }"
          >
            <span class="detail-tab-slider" aria-hidden="true"></span>
            <button
              v-for="(tab, index) in detailTabs"
              :id="`detail-tab-${tab.key}`"
              :key="tab.key"
              type="button"
              role="tab"
              class="detail-tab-button"
              :class="{ active: activeTab === tab.key }"
              :aria-selected="activeTab === tab.key"
              :aria-controls="`detail-panel-${tab.key}`"
              :tabindex="activeTab === tab.key ? 0 : -1"
              @click="activeTab = tab.key"
              @keydown="handleDetailTabKeydown($event, index)"
            >
              <span>{{ tab.label }}</span>
              <span v-if="getDetailTabCount(tab.key)" class="tab-count">
                {{ getDetailTabCount(tab.key) }}
              </span>
            </button>
          </div>
        </div>

        <!-- 简介 -->
        <div
          id="detail-panel-description"
          v-show="activeTab === 'description'"
          class="tab-content"
          role="tabpanel"
          aria-labelledby="detail-tab-description"
        >
          <div class="book-description">
            <div class="description-panel-header">
              <div>
                <h2>内容简介</h2>
                <p>{{ editingDescription ? '修改这本书的简介，支持保留段落与换行。' : '了解这本书的主要内容。' }}</p>
              </div>
              <div v-if="editingDescription" class="description-edit-actions">
                <button
                  class="btn"
                  type="button"
                  :disabled="savingDescription"
                  @click="cancelEditDescription"
                >
                  取消
                </button>
                <button
                  class="btn btn-primary"
                  type="button"
                  :disabled="savingDescription"
                  @click="saveDescription"
                >
                  {{ savingDescription ? '保存中...' : '保存简介' }}
                </button>
              </div>
              <button
                v-else
                class="btn description-edit-button"
                type="button"
                @click="startEditDescription"
              >
                <span aria-hidden="true">✎</span>
                <span>编辑简介</span>
              </button>
            </div>
            <div v-if="editingDescription" class="description-editor">
              <el-input
                ref="descriptionInput"
                v-model="descriptionEditValue"
                type="textarea"
                :autosize="{ minRows: 8, maxRows: 18 }"
                placeholder="输入这本书的内容简介..."
                :disabled="savingDescription"
                aria-label="书籍简介"
                @keydown.ctrl.enter.prevent="saveDescription"
                @keydown.meta.enter.prevent="saveDescription"
                @keydown.esc.prevent="cancelEditDescription"
              />
              <span class="description-shortcut">Ctrl / ⌘ + Enter 保存 · Esc 取消</span>
            </div>
            <p v-else-if="book.description" class="book-description-text">{{ book.description }}</p>
            <div v-else class="no-description">
              <span>暂无简介</span>
              <button
                v-if="selectedVersion?.primaryVersion"
                class="btn btn-sm"
                :disabled="reparsing"
                @click="handleReparseBook"
              >
                🔄 {{ reparsing ? '解析中...' : '重新解析书籍' }}
              </button>
              <small v-if="selectedVersionFormat === 'epub'">
                可尝试从 EPUB 的简介章节中提取。
              </small>
            </div>
          </div>
        </div>

        <!-- 目录 -->
        <div
          id="detail-panel-toc"
          v-show="activeTab === 'toc'"
          class="tab-content"
          role="tabpanel"
          aria-labelledby="detail-tab-toc"
        >
          <div v-if="tocLoading" class="toc-loading">
            <div class="loading-spinner-small"></div>
            <span>正在读取书籍目录...</span>
          </div>
          <div v-else-if="tocError" class="toc-empty">
            <span class="toc-empty-icon">⚠️</span>
            <p>{{ tocError }}</p>
            <button class="btn btn-text" @click="loadToc">重新加载</button>
          </div>
          <div v-else-if="tocItems.length === 0" class="toc-empty">
            <span class="toc-empty-icon">📑</span>
            <p>这本书暂未解析出章节目录</p>
            <span class="toc-empty-hint">
              EPUB、TXT 和 Markdown 格式可通过“重新解析书籍”更新目录。
            </span>
          </div>
          <div v-else class="book-toc">
            <div class="toc-summary">
              <span>章节目录</span>
              <span>共 {{ tocItems.length }} 章</span>
            </div>
            <button
              v-for="(chapter, index) in paginatedTocItems"
              :key="`${chapter.index}-${chapter.href || chapter.title}`"
              class="toc-row"
              :class="{ 'is-current': isCurrentChapter(chapter) }"
              :style="{ paddingLeft: `${18 + (chapter.depth || 0) * 22}px` }"
              @click="openChapter(chapter)"
            >
              <span class="toc-number">
                {{ String((tocCurrentPage - 1) * tocPageSize + index + 1).padStart(2, '0') }}
              </span>
              <span class="toc-title">{{ chapter.title }}</span>
              <span class="toc-read-action">
                {{ isCurrentChapter(chapter) ? '阅读中' : '阅读 ›' }}
              </span>
            </button>
            <div class="toc-pagination">
              <el-pagination
                v-model:current-page="tocCurrentPage"
                v-model:page-size="tocPageSize"
                :page-sizes="tocPageSizeOptions"
                :total="tocItems.length"
                :pager-count="5"
                layout="total, sizes, prev, pager, next"
                background
                @size-change="handleTocPageSizeChange"
              />
            </div>
          </div>
        </div>

        <!-- 书签 -->
        <div
          id="detail-panel-bookmarks"
          v-show="activeTab === 'bookmarks'"
          class="tab-content"
          role="tabpanel"
          aria-labelledby="detail-tab-bookmarks"
        >
          <div v-if="bookmarksLoading" class="bookmarks-state">
            <div class="loading-spinner-small"></div>
            <span>正在读取书签...</span>
          </div>
          <div v-else-if="bookmarksError" class="bookmarks-state">
            <span class="bookmarks-state-icon">⚠️</span>
            <p>{{ bookmarksError }}</p>
            <button class="btn btn-text" type="button" @click="loadBookmarks">重新加载</button>
          </div>
          <div v-else-if="bookmarks.length === 0" class="bookmarks-state">
            <span class="bookmarks-state-icon">🔖</span>
            <p>这本书还没有书签</p>
            <span class="bookmarks-state-hint">阅读时可将当前位置加入书签。</span>
          </div>
          <div v-else class="detail-bookmark-list">
            <div class="detail-bookmark-summary">
              <span>我的书签</span>
              <span>共 {{ bookmarks.length }} 条</span>
            </div>
            <article
              v-for="bookmark in bookmarks"
              :key="bookmark.id"
              class="detail-bookmark-item"
            >
              <div class="detail-bookmark-icon" aria-hidden="true">🔖</div>
              <div class="detail-bookmark-content">
                <h3 :title="getBookmarkDisplayName(bookmark)">
                  {{ getBookmarkDisplayName(bookmark) }}
                </h3>
                <p v-if="getBookmarkSecondaryText(bookmark)">
                  {{ getBookmarkSecondaryText(bookmark) }}
                </p>
              </div>
              <div class="detail-bookmark-meta">
                <span class="bookmark-chapter-badge">
                  {{ getBookmarkChapterLabel(bookmark) }}
                </span>
                <time :datetime="bookmark.createdAt">
                  {{ formatDate(bookmark.createdAt) }}
                </time>
              </div>
            </article>
          </div>
        </div>

        <!-- 详细信息 -->
        <div
          id="detail-panel-info"
          v-show="activeTab === 'info'"
          class="tab-content"
          role="tabpanel"
          aria-labelledby="detail-tab-info"
        >
          <div class="info-panel-header">
            <div>
              <h2>书籍详细信息</h2>
              <p>{{ editingInfo ? '修改可编辑的书籍元数据，系统信息保持只读。' : '查看书籍元数据及文件信息。' }}</p>
            </div>
            <div v-if="editingInfo" class="info-edit-actions">
              <button class="btn" type="button" :disabled="savingInfo" @click="cancelEditInfo">
                取消
              </button>
              <button class="btn btn-primary" type="button" :disabled="savingInfo" @click="saveInfo">
                {{ savingInfo ? '保存中...' : '保存修改' }}
              </button>
            </div>
            <button v-else class="btn info-edit-button" type="button" @click="startEditInfo">
              <span aria-hidden="true">✎</span>
              <span>编辑</span>
            </button>
          </div>
          <div class="info-list grouped-list">
            <div class="info-item list-item">
              <span class="info-label">书名</span>
              <el-input
                v-if="editingInfo"
                v-model="infoEditForm.title"
                class="info-edit-control"
                maxlength="255"
                show-word-limit
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.title }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">作者</span>
              <el-input
                v-if="editingInfo"
                v-model="infoEditForm.author"
                class="info-edit-control"
                maxlength="255"
                placeholder="未知作者"
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.author || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">ISBN</span>
              <el-input
                v-if="editingInfo"
                v-model="infoEditForm.isbn"
                class="info-edit-control"
                maxlength="32"
                placeholder="无"
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.isbn || '无' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">出版社</span>
              <el-input
                v-if="editingInfo"
                v-model="infoEditForm.publisher"
                class="info-edit-control"
                maxlength="255"
                placeholder="未知出版社"
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.publisher || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">出版日期</span>
              <el-date-picker
                v-if="editingInfo"
                v-model="infoEditForm.publishDate"
                class="info-edit-control info-date-control"
                type="date"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
                placeholder="选择出版日期"
                clearable
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.publishDate || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">格式</span>
              <span class="info-value">{{ selectedVersionFormat.toUpperCase() }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">来源</span>
              <span class="info-value">{{ formatSourceType(book.sourceType) }}</span>
            </div>
            <div
              v-if="book.sourceType === 'DIRECTORY_SCAN' && book.sourcePath"
              class="info-item list-item"
            >
              <span class="info-label">扫描路径</span>
              <span class="info-value source-path">{{ book.sourcePath }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">语言</span>
              <el-input
                v-if="editingInfo"
                v-model="infoEditForm.language"
                class="info-edit-control"
                maxlength="50"
                placeholder="例如：zh-CN"
                :disabled="savingInfo"
              />
              <span v-else class="info-value">{{ book.language || '未知' }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">文件大小</span>
              <span class="info-value">{{ formatFileSize(selectedVersion?.fileSize) }}</span>
            </div>
            <div
              v-if="selectedVersion?.chapterCount !== undefined
                && selectedVersion?.chapterCount !== null"
              class="info-item list-item"
            >
              <span class="info-label">章节数</span>
              <span class="info-value">{{ selectedVersion.chapterCount }} 章</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">添加时间</span>
              <span class="info-value">{{ formatDate(book.createdAt) }}</span>
            </div>
            <div class="info-item list-item">
              <span class="info-label">更新时间</span>
              <span class="info-value">{{ formatDate(book.updatedAt) }}</span>
            </div>
          </div>
        </div>

        <!-- 笔记 -->
        <div
          id="detail-panel-notes"
          v-show="activeTab === 'notes'"
          class="tab-content"
          role="tabpanel"
          aria-labelledby="detail-tab-notes"
        >
          <div class="book-notes">
            <div class="notes-header">
              <h3>📝 读书笔记</h3>
              <p class="notes-hint">记录你的阅读心得和感悟</p>
            </div>
            <textarea
              v-model="notes"
              class="textarea"
              rows="8"
              placeholder="在这里写下你的读书笔记..."
            ></textarea>
            <div class="notes-actions">
              <button class="btn btn-primary" @click="handleSaveNotes">
                <span>💾</span>
                <span>保存笔记</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-else class="empty glass">
      <div class="empty-icon">📚</div>
      <p>书籍不存在</p>
      <button class="btn btn-primary" @click="$router.back()">返回书库</button>
    </div>

    <!-- 刮削对话框 -->
    <ScraperDialog
      ref="scraperDialog"
      :visible="showScraperDialog"
      @close="showScraperDialog = false"
      @refresh="loadBook"
    />

    <AddToBookListDialog
      :visible="showAddToListDialog"
      :book="book"
      @close="showAddToListDialog = false"
    />

    <el-dialog
      v-model="showCreateTagDialog"
      title="新建标签"
      width="min(440px, calc(100vw - 32px))"
      append-to-body
      destroy-on-close
      :close-on-click-modal="!creatingTag"
      :close-on-press-escape="!creatingTag"
      @closed="resetCreateTagForm"
    >
      <div class="create-tag-form">
        <label class="create-tag-field">
          <span>标签名称</span>
          <el-input
            v-model="newTagForm.name"
            maxlength="30"
            show-word-limit
            placeholder="例如：精品、待读、系列"
            :disabled="creatingTag"
            @keyup.enter="handleCreateTag"
          />
        </label>
        <fieldset class="create-tag-field create-tag-colors">
          <legend>标签颜色</legend>
          <div class="tag-color-palette" role="listbox" aria-label="选择标签颜色">
            <button
              v-for="color in tagPresetColors"
              :key="color"
              class="tag-color-swatch"
              :class="{ selected: newTagForm.color === color }"
              type="button"
              role="option"
              :style="{ backgroundColor: color }"
              :aria-label="color"
              :aria-selected="newTagForm.color === color"
              :disabled="creatingTag"
              @click="newTagForm.color = color"
            >
              <span v-if="newTagForm.color === color" aria-hidden="true">✓</span>
            </button>
          </div>
          <div class="tag-custom-color">
            <input
              v-model="newTagForm.color"
              class="tag-color-picker"
              type="color"
              aria-label="自定义标签颜色"
              :disabled="creatingTag"
            />
            <el-input
              v-model="newTagForm.color"
              maxlength="7"
              aria-label="标签颜色值"
              placeholder="#64748B"
              :disabled="creatingTag"
            />
            <span class="new-tag-preview" :style="newTagPreviewStyle">
              {{ newTagForm.name.trim() || '标签预览' }}
            </span>
          </div>
        </fieldset>
      </div>
      <template #footer>
        <button class="btn" type="button" :disabled="creatingTag" @click="showCreateTagDialog = false">
          取消
        </button>
        <button
          class="btn btn-primary"
          type="button"
          :disabled="creatingTag || !newTagForm.name.trim()"
          @click="handleCreateTag"
        >
          {{ creatingTag ? '创建中...' : '创建并添加' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown } from '@element-plus/icons-vue'
import { message, confirm } from '@/utils/message'
import { useBookStore } from '@/stores/book'
import { useCategoryStore } from '@/stores/category'
import { useTagStore } from '@/stores/tag'
import api from '@/utils/api'
import { scrapeBook, downloadCover } from '@/utils/scraper'
import { getCoverUrl } from '@/utils/cover'
import { formatChinaDateTime } from '@/utils/dateTime'
import ScraperDialog from '@/components/ScraperDialog.vue'
import AddToBookListDialog from '@/components/AddToBookListDialog.vue'
import BookCoverPrivacyButton from '@/components/BookCoverPrivacyButton.vue'
import { isBookCoverHidden } from '@/utils/imagePrivacy'

const route = useRoute()
const router = useRouter()
const bookStore = useBookStore()
const categoryStore = useCategoryStore()
const tagStore = useTagStore()

const book = ref<any>(null)
const loading = ref(true)
const notes = ref('')
type DetailTabKey = 'description' | 'toc' | 'bookmarks' | 'info' | 'notes'

const detailTabs: Array<{ key: DetailTabKey; label: string }> = [
  { key: 'description', label: '简介' },
  { key: 'toc', label: '目录' },
  { key: 'bookmarks', label: '书签' },
  { key: 'info', label: '详细信息' },
  { key: 'notes', label: '笔记' },
]
const activeTab = ref<DetailTabKey>('description')
const activeTabIndex = computed(() => Math.max(
  0,
  detailTabs.findIndex(tab => tab.key === activeTab.value),
))
const scraping = ref(false)
const reparsing = ref(false)
const downloadingCover = ref(false)
const uploadingCover = ref(false)
const randomizingCover = ref(false)
const coverInput = ref<HTMLInputElement | null>(null)
const showScraperDialog = ref(false)
const moreActionsOpen = ref(false)
const tocLoading = ref(false)
const tocError = ref('')

interface TocItem {
  index: number
  title: string
  href?: string
  startIndex?: number
  endIndex?: number
  depth?: number
}

interface ReadingProgress {
  currentChapter?: string
  currentChapterTitle?: string
  totalProgress?: number
  lastReadAt?: string
}

interface Bookmark {
  id: number
  title?: string
  excerpt?: string
  chapter?: string
  chapterIndex?: number
  createdAt: string
}

interface BookVersion {
  id: number
  displayName: string
  format: string
  fileSize?: number
  fileHash?: string
  primaryVersion: boolean
  chapterCount?: number
  createdAt?: string
}

const getDetailTabCount = (tab: DetailTabKey) => {
  if (tab === 'toc') return tocItems.value.length
  if (tab === 'bookmarks') return bookmarks.value.length
  return 0
}

const handleDetailTabKeydown = (event: KeyboardEvent, index: number) => {
  let nextIndex: number | null = null
  if (event.key === 'ArrowRight' || event.key === 'ArrowDown') {
    nextIndex = (index + 1) % detailTabs.length
  }
  if (event.key === 'ArrowLeft' || event.key === 'ArrowUp') {
    nextIndex = (index - 1 + detailTabs.length) % detailTabs.length
  }
  if (event.key === 'Home') nextIndex = 0
  if (event.key === 'End') nextIndex = detailTabs.length - 1
  if (nextIndex === null) return

  event.preventDefault()
  activeTab.value = detailTabs[nextIndex].key
  const tabButtons = (event.currentTarget as HTMLButtonElement)
    .parentElement?.querySelectorAll<HTMLButtonElement>('.detail-tab-button')
  tabButtons?.[nextIndex]?.focus()
}

const tocItems = ref<TocItem[]>([])
const readingProgress = ref<ReadingProgress | null>(null)
const bookmarks = ref<Bookmark[]>([])
const bookmarksLoading = ref(false)
const bookmarksError = ref('')
const versions = ref<BookVersion[]>([])
const selectedVersionId = ref<number | null>(null)
const uploadingVersion = ref(false)
const selectedVersion = computed(() =>
  versions.value.find(version => version.id === selectedVersionId.value) || null,
)
const selectedVersionFormat = computed(() =>
  selectedVersion.value?.format || book.value?.format || '',
)
const tocCurrentPage = ref(1)
const tocPageSize = ref(20)
const tocPageSizeOptions = [20, 50, 100]
const paginatedTocItems = computed(() => {
  const start = (tocCurrentPage.value - 1) * tocPageSize.value
  return tocItems.value.slice(start, start + tocPageSize.value)
})
const currentReadingChapter = computed(() => {
  const title = readingProgress.value?.currentChapterTitle?.trim()
  if (title) return title

  const legacyChapter = readingProgress.value?.currentChapter?.trim()
  if (legacyChapter && !legacyChapter.startsWith('epubcfi(')) return legacyChapter

  return '章节信息将在继续阅读后更新'
})
const hasReadingProgress = computed(() => Boolean(
  readingProgress.value?.lastReadAt
  || readingProgress.value?.currentChapterTitle
  || readingProgress.value?.currentChapter
  || (readingProgress.value?.totalProgress || 0) > 0,
))

// 书单相关
const showAddToListDialog = ref(false)
const scraperDialog = ref<InstanceType<typeof ScraperDialog> | null>(null)

// 编辑书名相关
const editingTitle = ref(false)
const editTitleValue = ref('')
const savingTitle = ref(false)
const editingDescription = ref(false)
const descriptionEditValue = ref('')
const savingDescription = ref(false)
const descriptionInput = ref<any>(null)
const editingInfo = ref(false)
const savingInfo = ref(false)
const infoEditForm = reactive({
  title: '',
  author: '',
  isbn: '',
  publisher: '',
  publishDate: '',
  language: '',
})
const selectedTagIds = ref<number[]>([])
const savingTags = ref(false)
const showCreateTagDialog = ref(false)
const creatingTag = ref(false)
const defaultTagColor = '#64748B'
const newTagForm = reactive({ name: '', color: defaultTagColor })
const tagPresetColors = [
  '#64748B', '#EF4444', '#F97316', '#F59E0B', '#22C55E', '#14B8A6',
  '#0EA5E9', '#3B82F6', '#6366F1', '#8B5CF6', '#D946EF', '#EC4899',
]
const newTagPreviewStyle = computed(() => {
  const color = /^#[0-9a-fA-F]{6}$/.test(newTagForm.color)
    ? newTagForm.color
    : defaultTagColor
  return {
    color,
    borderColor: `${color}88`,
    backgroundColor: `${color}16`,
  }
})

const loadBook = async () => {
  const id = Number(route.params.id)
  if (isNaN(id)) {
    loading.value = false
    return
  }

  try {
    book.value = await bookStore.fetchBookById(id)
    notes.value = book.value.notes || ''
    selectedTagIds.value = (book.value.tags || []).map((tag: any) => tag.id)
    await loadVersions()
    await Promise.all([loadToc(), loadReadingProgress(), loadBookmarks()])
    syncTocPageToCurrentChapter()
  } catch (error) {
    console.error('Failed to load book:', error)
  } finally {
    loading.value = false
  }
}

const loadVersions = async () => {
  if (!book.value) return
  const response = await api.get(`/api/books/${book.value.id}/versions`)
  versions.value = response.data || []
  const requestedVersionId = Number(route.query.versionId)
  const requestedVersion = versions.value.find(version => version.id === requestedVersionId)
  const currentVersion = versions.value.find(version => version.id === selectedVersionId.value)
  selectedVersionId.value = requestedVersion?.id
    || currentVersion?.id
    || versions.value.find(version => version.primaryVersion)?.id
    || versions.value[0]?.id
    || null
}

const loadReadingProgress = async () => {
  if (!book.value || !selectedVersionId.value) return
  try {
    const response = await api.get(`/api/reading-progress/book/${book.value.id}`, {
      params: { versionId: selectedVersionId.value },
    })
    readingProgress.value = response.data || null
  } catch (error) {
    readingProgress.value = null
    console.error('Failed to load reading progress:', error)
  }
}

const loadBookmarks = async () => {
  if (!book.value) return
  bookmarksLoading.value = true
  bookmarksError.value = ''
  try {
    const response = await api.get(`/api/books/${book.value.id}/bookmarks`)
    bookmarks.value = response.data || []
  } catch (error: any) {
    bookmarks.value = []
    bookmarksError.value = error.response?.data?.message || '书签读取失败'
  } finally {
    bookmarksLoading.value = false
  }
}

const normalizeBookmarkText = (value?: string) =>
  (value || '').replace(/\s+/g, ' ').trim()

const getBookmarkDisplayName = (bookmark: Bookmark) => {
  const title = normalizeBookmarkText(bookmark.title)
  if (title && title !== '书签') return title

  const excerpt = normalizeBookmarkText(bookmark.excerpt)
  if (excerpt) return excerpt.length > 72 ? `${excerpt.slice(0, 72)}…` : excerpt

  const chapter = normalizeBookmarkText(bookmark.chapter)
  return chapter || '未命名书签'
}

const getBookmarkSecondaryText = (bookmark: Bookmark) => {
  const title = normalizeBookmarkText(bookmark.title)
  const excerpt = normalizeBookmarkText(bookmark.excerpt)
  if (!excerpt || !title || title === '书签') return ''
  return excerpt.length > 110 ? `${excerpt.slice(0, 110)}…` : excerpt
}

const getBookmarkChapterIndex = (bookmark: Bookmark) => {
  if (bookmark.chapterIndex && bookmark.chapterIndex > 0) return bookmark.chapterIndex
  const chapter = normalizeBookmarkText(bookmark.chapter)
  if (!chapter) return undefined
  const index = tocItems.value.findIndex(item => normalizeBookmarkText(item.title) === chapter)
  return index >= 0 ? index + 1 : undefined
}

const getBookmarkChapterLabel = (bookmark: Bookmark) => {
  const chapterIndex = getBookmarkChapterIndex(bookmark)
  if (chapterIndex) return `第 ${chapterIndex} 章`
  return normalizeBookmarkText(bookmark.chapter) || '章节未知'
}

const isCurrentChapter = (chapter: TocItem) => {
  const title = readingProgress.value?.currentChapterTitle
    || (
      readingProgress.value?.currentChapter?.startsWith('epubcfi(')
        ? ''
        : readingProgress.value?.currentChapter
    )
  return Boolean(title && chapter.title.trim() === title.trim())
}

const syncTocPageToCurrentChapter = () => {
  const currentIndex = tocItems.value.findIndex(isCurrentChapter)
  if (currentIndex >= 0) {
    tocCurrentPage.value = Math.floor(currentIndex / tocPageSize.value) + 1
  }
}

const handleRead = () => {
  router.push({
    path: `/reader/${book.value.id}`,
    query: selectedVersionId.value
      ? { versionId: String(selectedVersionId.value) }
      : undefined,
  })
}

const openFormatConversion = () => {
  if (!book.value || !selectedVersionId.value || selectedVersionFormat.value !== 'txt') return
  router.push({
    path: '/format-conversion',
    query: { bookId: String(book.value.id), versionId: String(selectedVersionId.value) },
  })
}

const handleBookActionCommand = (command: string) => {
  switch (command) {
    case 'scrape':
      void handleScrape()
      break
    case 'cover':
      void handleDownloadCover()
      break
    case 'reparse-book':
      void handleReparseBook()
      break
    case 'repair':
      router.push(`/books/${book.value?.id}/repair`)
      break
    case 'delete':
      void handleDelete()
      break
  }
}

const loadToc = async () => {
  if (!book.value || !selectedVersionId.value) return
  tocLoading.value = true
  tocError.value = ''
  tocCurrentPage.value = 1
  try {
    const response = await api.get(`/api/books/${book.value.id}/toc`, {
      params: { versionId: selectedVersionId.value },
    })
    tocItems.value = response.data || []
  } catch (error: any) {
    tocItems.value = []
    tocError.value = error.response?.data?.message || '目录读取失败'
  } finally {
    tocLoading.value = false
  }
}

const handleTocPageSizeChange = () => {
  syncTocPageToCurrentChapter()
}

const openChapter = (chapter: TocItem) => {
  const query: Record<string, string> = selectedVersionFormat.value === 'epub' && chapter.href
    ? { chapterHref: chapter.href }
    : { chapterTitle: chapter.title }
  if (selectedVersionId.value) {
    query.versionId = String(selectedVersionId.value)
  }
  router.push({
    path: `/reader/${book.value.id}`,
    query,
  })
}

const selectVersion = async (versionId: number) => {
  if (versionId === selectedVersionId.value) return
  selectedVersionId.value = versionId
  readingProgress.value = null
  await router.replace({
    query: { ...route.query, versionId: String(versionId) },
  })
  await Promise.all([loadToc(), loadReadingProgress()])
  syncTocPageToCurrentChapter()
}

const handleVersionUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !book.value) return

  uploadingVersion.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await api.post(
      `/api/books/${book.value.id}/versions`,
      formData,
    )
    await loadVersions()
    await selectVersion(response.data.id)
    message.success('新版本已添加')
  } catch (error: any) {
    message.error(error.response?.data?.message || '版本上传失败')
  } finally {
    uploadingVersion.value = false
    input.value = ''
  }
}

const deleteVersion = async (version: BookVersion) => {
  const approved = await confirm(
    `确定移除版本“${version.displayName}”吗？\n\n只移除版本记录，不会删除原始文件。`,
  )
  if (!approved || !book.value) return

  try {
    await api.delete(`/api/books/${book.value.id}/versions/${version.id}`)
    const wasSelected = version.id === selectedVersionId.value
    await loadVersions()
    if (wasSelected && selectedVersionId.value) {
      await router.replace({
        query: { ...route.query, versionId: String(selectedVersionId.value) },
      })
      readingProgress.value = null
      await Promise.all([loadToc(), loadReadingProgress()])
      syncTocPageToCurrentChapter()
    }
    message.success('版本已移除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '移除版本失败')
  }
}

const handleToggleFavorite = async () => {
  try {
    book.value = await bookStore.toggleFavorite(book.value.id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggleWanted = async () => {
  try {
    book.value = await bookStore.toggleWanted(book.value.id)
    message.success('操作成功')
  } catch (error) {
    message.error('操作失败')
  }
}

const handleToggleShelf = async () => {
  try {
    book.value = book.value.onShelf
      ? await bookStore.removeFromShelf(book.value.id)
      : await bookStore.addToShelf(book.value.id)
    message.success(book.value.onShelf ? '已加入书架' : '已移出书架')
  } catch (error) {
    message.error('书架操作失败')
  }
}

const handleCategoryChange = async (value: number | string) => {
  try {
    book.value = await bookStore.updateBookCategory(
      book.value.id,
      value ? Number(value) : undefined,
    )
    message.success('分类已更新')
  } catch {
    message.error('分类更新失败')
  }
}

const handleTagsChange = async () => {
  const previousIds = (book.value.tags || []).map((tag: any) => tag.id)
  savingTags.value = true
  try {
    book.value = await bookStore.updateBookTags(book.value.id, selectedTagIds.value)
    selectedTagIds.value = (book.value.tags || []).map((tag: any) => tag.id)
    message.success('标签已更新')
    await tagStore.fetchTags()
  } catch (error: any) {
    selectedTagIds.value = previousIds
    message.error(error.response?.data?.message || '标签更新失败')
  } finally {
    savingTags.value = false
  }
}

const resetCreateTagForm = () => {
  newTagForm.name = ''
  newTagForm.color = defaultTagColor
}

const openCreateTagDialog = () => {
  resetCreateTagForm()
  showCreateTagDialog.value = true
}

const handleCreateTag = async () => {
  const name = newTagForm.name.trim()
  if (!name || creatingTag.value || !book.value) return
  if (!/^#[0-9a-fA-F]{6}$/.test(newTagForm.color)) {
    message.warning('请输入正确的颜色值，例如 #64748B')
    return
  }

  creatingTag.value = true
  try {
    const createdTag = await tagStore.createTag(name, newTagForm.color.toUpperCase())
    const nextTagIds = Array.from(new Set([...selectedTagIds.value, createdTag.id]))
    try {
      book.value = await bookStore.updateBookTags(book.value.id, nextTagIds)
      selectedTagIds.value = (book.value.tags || []).map((tag: any) => tag.id)
      await tagStore.fetchTags()
      message.success(`已新建并添加标签“${createdTag.name}”`)
    } catch (error: any) {
      await tagStore.fetchTags()
      message.warning(error.response?.data?.message || '标签已新建，但添加到当前书籍失败')
    }
    showCreateTagDialog.value = false
  } catch (error: any) {
    message.error(error.response?.data?.message || '标签创建失败，名称可能已存在')
  } finally {
    creatingTag.value = false
  }
}

const handleDelete = async () => {
  const result = await confirm(
    '确定将这本书移入回收站吗？\n\nNAS 上的原始文件不会被删除或移动，可随时恢复。',
    '移入回收站',
  )
  if (result) {
    try {
      await bookStore.deleteBook(book.value.id)
      message.success('已移入回收站')
      router.push('/books')
    } catch (error) {
      message.error('移入回收站失败')
    }
  }
}

const setRating = async (rating: number) => {
  try {
    book.value.rating = rating
    await bookStore.updateBookMetadata(book.value.id, { rating })
    message.success('评分已更新')
  } catch (error) {
    message.error('评分更新失败')
  }
}

const handleSaveNotes = async () => {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/books/${book.value.id}/notes`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ notes: notes.value })
    })
    const result = await response.json()
    if (result.success) {
      message.success('笔记保存成功')
    } else {
      message.error(result.message || '保存失败')
    }
  } catch (error) {
    message.error('保存失败')
  }
}

const handleScrape = async () => {
  if (!book.value) return

  showScraperDialog.value = true
  scraping.value = true

  // 等待对话框渲染
  await nextTick()

  if (scraperDialog.value) {
    scraperDialog.value.startScrape(async () => {
      const result = await scrapeBook(book.value.id)
      // 刷新书籍数据
      if (result.success && result.book) {
        book.value = result.book
      }
      return result
    })
  }

  scraping.value = false
}

// 编辑书名
const startEditTitle = () => {
  editTitleValue.value = book.value.title
  editingTitle.value = true
}

const cancelEditTitle = () => {
  editingTitle.value = false
  editTitleValue.value = ''
}

const saveTitle = async () => {
  if (!editTitleValue.value.trim()) {
    message.error('书名不能为空')
    return
  }

  if (editTitleValue.value === book.value.title) {
    editingTitle.value = false
    return
  }

  savingTitle.value = true
  try {
    const updatedBook = await bookStore.updateBookMetadata(book.value.id, {
      title: editTitleValue.value.trim()
    })
    book.value = updatedBook
    editingTitle.value = false
    message.success('书名修改成功')
  } catch (error) {
    message.error('书名修改失败')
  } finally {
    savingTitle.value = false
  }
}

const startEditDescription = async () => {
  descriptionEditValue.value = book.value?.description || ''
  editingDescription.value = true
  await nextTick()
  descriptionInput.value?.focus()
}

const cancelEditDescription = () => {
  descriptionEditValue.value = book.value?.description || ''
  editingDescription.value = false
}

const saveDescription = async () => {
  if (!book.value || savingDescription.value) return

  const description = descriptionEditValue.value.trim()
  if (description === (book.value.description || '').trim()) {
    editingDescription.value = false
    return
  }

  savingDescription.value = true
  try {
    book.value = await bookStore.updateBookMetadata(book.value.id, { description })
    descriptionEditValue.value = book.value.description || ''
    editingDescription.value = false
    message.success(description ? '书籍简介已保存' : '书籍简介已清除')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍简介保存失败')
  } finally {
    savingDescription.value = false
  }
}

const hydrateInfoEditForm = () => {
  if (!book.value) return
  Object.assign(infoEditForm, {
    title: book.value.title || '',
    author: book.value.author || '',
    isbn: book.value.isbn || '',
    publisher: book.value.publisher || '',
    publishDate: book.value.publishDate || '',
    language: book.value.language || '',
  })
}

const startEditInfo = () => {
  hydrateInfoEditForm()
  editingInfo.value = true
}

const cancelEditInfo = () => {
  hydrateInfoEditForm()
  editingInfo.value = false
}

const saveInfo = async () => {
  const title = infoEditForm.title.trim()
  if (!title) {
    message.warning('书名不能为空')
    return
  }

  savingInfo.value = true
  try {
    book.value = await bookStore.updateBookMetadata(book.value.id, {
      title,
      author: infoEditForm.author.trim(),
      isbn: infoEditForm.isbn.trim(),
      publisher: infoEditForm.publisher.trim(),
      publishDate: String(infoEditForm.publishDate || '').trim(),
      language: infoEditForm.language.trim(),
    })
    editingInfo.value = false
    hydrateInfoEditForm()
    message.success('书籍详细信息已保存')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍详细信息保存失败')
  } finally {
    savingInfo.value = false
  }
}

const handleDownloadCover = async () => {
  if (!book.value) return

  downloadingCover.value = true
  try {
    const result = await downloadCover(book.value.id)
    if (result.success) {
      message.success('封面下载成功')
      // 刷新书籍数据
      await loadBook()
    } else {
      message.error(result.message || '封面下载失败')
    }
  } catch (error: any) {
    message.error(error.response?.data?.message || '封面下载失败')
  } finally {
    downloadingCover.value = false
  }
}

const handleCoverUpload = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !book.value) return

  if (!['image/jpeg', 'image/png', 'image/webp', 'image/gif'].includes(file.type)) {
    message.warning('仅支持 JPG、PNG、WebP 或 GIF 图片')
    input.value = ''
    return
  }
  if (file.size > 10 * 1024 * 1024) {
    message.warning('封面图片不能超过10MB')
    input.value = ''
    return
  }

  uploadingCover.value = true
  try {
    book.value = await bookStore.uploadBookCover(book.value.id, file)
    message.success('书籍封面修改成功')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍封面修改失败')
  } finally {
    uploadingCover.value = false
    input.value = ''
  }
}

const handleRandomCover = async () => {
  if (!book.value) return
  randomizingCover.value = true
  try {
    book.value = await bookStore.randomizeBookCover(book.value.id)
    message.success('已随机更换书籍封面')
  } catch (error: any) {
    message.warning(error.response?.data?.message || '随机封面失败')
  } finally {
    randomizingCover.value = false
  }
}

const handleReparseBook = async () => {
  if (!book.value || !selectedVersion.value?.primaryVersion) return
  reparsing.value = true
  try {
    const result = await bookStore.reparseBook(book.value.id)
    await loadBook()
    message.success(result.message || '书籍重新解析完成')
  } catch (error: any) {
    message.error(error.response?.data?.message || '书籍重新解析失败')
  } finally {
    reparsing.value = false
  }
}

const formatFileSize = (bytes?: number) => {
  if (!bytes) return '未知'
  const units = ['B', 'KB', 'MB', 'GB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(2)} ${units[unitIndex]}`
}

const formatSourceType = (sourceType?: 'UPLOAD' | 'DIRECTORY_SCAN') => {
  if (sourceType === 'UPLOAD') return '上传'
  if (sourceType === 'DIRECTORY_SCAN') return '目录扫描'
  return '未知'
}

const formatDate = (dateStr?: string) => {
  if (!dateStr) return '未知'
  return formatChinaDateTime(dateStr)
}

onMounted(() => {
  loadBook()
  categoryStore.refresh()
  tagStore.fetchTags()
})
</script>

<style scoped>
.book-detail-view {
  max-width: 1120px;
  margin: 0 auto;
  padding: var(--spacing-lg) 0;
}

.category-select {
  min-width: 180px;
}

.detail-tabs-scroll {
  margin-bottom: var(--spacing-xl);
  overflow-x: auto;
  scrollbar-width: none;
}

.detail-tabs-scroll::-webkit-scrollbar {
  display: none;
}

.detail-segmented-tabs {
  --detail-tab-count: 5;
  --detail-tab-index: 0;
  position: relative;
  isolation: isolate;
  display: grid;
  grid-template-columns: repeat(var(--detail-tab-count), minmax(0, 1fr));
  min-width: 540px;
  padding: 5px;
  border: 1px solid color-mix(in srgb, white 66%, var(--border-color));
  border-radius: 17px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.48), rgba(229, 239, 251, 0.24));
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.88),
    inset 0 -1px 0 rgba(70, 92, 126, 0.1),
    0 10px 28px rgba(45, 63, 94, 0.1);
  backdrop-filter: blur(24px) saturate(180%);
  -webkit-backdrop-filter: blur(24px) saturate(180%);
}

.detail-tab-slider {
  position: absolute;
  z-index: -1;
  top: 5px;
  bottom: 5px;
  left: 5px;
  width: calc((100% - 10px) / var(--detail-tab-count));
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 13px;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.92), rgba(225, 239, 255, 0.7));
  box-shadow:
    0 7px 18px rgba(45, 65, 98, 0.16),
    inset 0 1px 0 rgba(255, 255, 255, 1),
    inset 0 -1px 0 rgba(90, 116, 153, 0.12);
  transform: translateX(calc(var(--detail-tab-index) * 100%));
  transition: transform 360ms cubic-bezier(0.22, 1, 0.36, 1);
}

.detail-tab-slider::after {
  position: absolute;
  inset: 1px 12% auto;
  height: 45%;
  border-radius: inherit;
  background: linear-gradient(rgba(255, 255, 255, 0.5), transparent);
  content: '';
  pointer-events: none;
}

.detail-tab-button {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  gap: 7px;
  padding: 8px 12px;
  border: 0;
  border-radius: 13px;
  background: transparent;
  color: var(--text-secondary);
  font: inherit;
  font-weight: 560;
  white-space: nowrap;
  cursor: pointer;
  transition: color 180ms ease, background-color 180ms ease;
}

.detail-tab-button:hover:not(.active) {
  background: rgba(255, 255, 255, 0.2);
  color: var(--text-primary);
}

.detail-tab-button.active {
  color: var(--text-primary);
  font-weight: 650;
}

.detail-tab-button:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: -2px;
}

:global(html[data-theme="macos26"]) .detail-segmented-tabs {
  border-color: rgba(255, 255, 255, 0.74);
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.58), rgba(221, 237, 255, 0.28));
  box-shadow:
    0 18px 38px rgba(48, 66, 100, 0.13),
    inset 0 1px 0 rgba(255, 255, 255, 0.96),
    inset 0 -1px 0 rgba(88, 112, 148, 0.11);
}

:global(html[data-theme="macos26"]) .detail-tab-slider {
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.94), rgba(222, 239, 255, 0.72));
  box-shadow:
    0 8px 22px rgba(48, 66, 100, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 1),
    inset 0 -1px 0 rgba(83, 113, 153, 0.14);
}

.tab-count {
  min-width: 20px;
  padding: 1px 6px;
  border-radius: 999px;
  color: var(--primary);
  font-size: 11px;
  background: var(--primary-alpha-10);
}

.toc-loading,
.toc-empty {
  display: flex;
  min-height: 180px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: var(--text-secondary);
}

.toc-empty p {
  margin: 0;
}

.toc-empty-icon {
  font-size: 34px;
}

.toc-empty-hint {
  font-size: 12px;
}

.book-toc {
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.toc-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 13px;
}

.toc-summary span:first-child {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.toc-row {
  display: grid;
  width: 100%;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 8px;
  padding-top: 13px;
  padding-right: 18px;
  padding-bottom: 13px;
  border: none;
  border-bottom: 1px solid var(--border-color-light);
  color: var(--text-primary);
  text-align: left;
  background: transparent;
  cursor: pointer;
  transition: background 0.15s ease, color 0.15s ease;
}

.toc-row:last-child {
  border-bottom: none;
}

.toc-row:hover {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.toc-row.is-current {
  color: var(--primary);
  background: var(--primary-alpha-10);
}

.toc-row.is-current .toc-title {
  font-weight: 600;
}

.toc-row.is-current .toc-read-action {
  opacity: 1;
  transform: translateX(0);
}

.toc-number {
  color: var(--text-tertiary);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.toc-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.toc-read-action {
  color: var(--text-secondary);
  font-size: 12px;
  opacity: 0;
  transform: translateX(-4px);
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.toc-row:hover .toc-read-action {
  opacity: 1;
  transform: translateX(0);
}

.toc-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 16px 18px;
  border-top: 1px solid var(--border-color);
  overflow-x: auto;
}

.toc-pagination :deep(.el-pagination) {
  flex-shrink: 0;
}

.book-tag-select {
  width: 240px;
}

.tag-option-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 8px;
  border-radius: 50%;
}

.tag-manage-link {
  padding: 4px 8px;
  white-space: nowrap;
}

.create-tag-form {
  display: grid;
  gap: 20px;
}

.create-tag-field {
  display: grid;
  min-width: 0;
  gap: 9px;
  margin: 0;
  padding: 0;
  border: 0;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
}

.create-tag-colors legend {
  margin-bottom: 9px;
  padding: 0;
}

.tag-color-palette {
  display: grid;
  grid-template-columns: repeat(6, 32px);
  gap: 10px;
}

.tag-color-swatch {
  display: grid;
  width: 32px;
  height: 32px;
  padding: 0;
  place-items: center;
  border: 2px solid transparent;
  border-radius: 8px;
  color: white;
  cursor: pointer;
  font-weight: 700;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.tag-color-swatch:hover:not(:disabled),
.tag-color-swatch.selected {
  border-color: var(--text-primary);
}

.tag-color-swatch:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.tag-custom-color {
  display: grid;
  grid-template-columns: 42px minmax(110px, 1fr) auto;
  align-items: center;
  gap: 10px;
  margin-top: 4px;
}

.tag-color-picker {
  width: 42px;
  height: 32px;
  padding: 2px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--surface-card);
  cursor: pointer;
}

.new-tag-preview {
  display: inline-flex;
  max-width: 140px;
  overflow: hidden;
  align-items: center;
  padding: 4px 10px;
  border: 1px solid;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 加载中和空状态 */
.loading,
.empty {
  text-align: center;
  padding: var(--spacing-xl) var(--spacing-lg);
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: var(--radius-lg);
  color: var(--text-secondary);
}

.loading-spinner {
  display: inline-block;
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin-bottom: var(--spacing-md);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.empty-icon {
  font-size: 64px;
  margin-bottom: var(--spacing-md);
  opacity: 0.5;
}

/* 书籍内容 */
.book-content {
  background: var(--surface-card);
  backdrop-filter: var(--glass-blur);
  -webkit-backdrop-filter: var(--glass-blur);
  border: var(--glass-border);
  border-radius: calc(var(--radius-lg) + 4px);
  padding: 28px 32px 32px;
}

.detail-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: 28px;
}

.detail-toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-sm);
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-xs);
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-full);
  background: var(--bg-secondary);
  color: var(--text-primary);
  font-size: var(--font-size-base);
  cursor: pointer;
  transition: all var(--transition-fast);
}

.back-btn:hover {
  background: var(--bg-tertiary);
}

.more-actions-button {
  min-width: 116px;
  gap: 7px;
  white-space: nowrap;
}

.more-actions-arrow {
  width: 13px;
  height: 13px;
  flex: 0 0 13px;
  color: var(--text-secondary);
  transition: transform var(--transition-fast), color var(--transition-fast);
}

.more-actions-button:hover .more-actions-arrow,
.more-actions-arrow.is-open {
  color: currentColor;
}

.more-actions-arrow.is-open {
  transform: rotate(180deg);
}

.danger-menu-item {
  color: var(--danger);
}

.book-hero {
  display: grid;
  grid-template-columns: 208px minmax(0, 1fr);
  align-items: start;
  gap: 36px;
  padding: 4px 4px 32px;
}

.cover-column {
  display: grid;
  gap: 16px;
}

.book-cover {
  position: relative;
  width: 208px;
  height: 292px;
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-lg);
}

.cover-action-row {
  display: grid;
  width: 208px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.cover-action-button {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding-right: 8px;
  padding-left: 8px;
  border-color: var(--primary-alpha-20);
  background: var(--surface-card);
  color: var(--primary);
  font-size: 12px;
  font-weight: 600;
}

.cover-action-button:hover:not(:disabled) {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

.cover-file-input {
  display: none;
}

.cover-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: filter 0.2s ease, transform 0.2s ease;
}

.cover-image.is-hidden {
  filter: blur(18px);
  transform: scale(1.12);
}

.no-cover {
  width: 100%;
  height: 100%;
  background: var(--primary-gradient);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 72px;
  font-weight: 600;
}

.book-summary {
  min-width: 0;
}

.book-title-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 10px;
}

.book-title {
  overflow-wrap: anywhere;
  font-size: clamp(30px, 4vw, 44px);
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
  line-height: 1.15;
  letter-spacing: -0.025em;
}

.btn-edit-title {
  flex-shrink: 0;
  margin-top: 8px;
  padding: 4px 8px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--text-tertiary);
  cursor: pointer;
  font-size: 12px;
  transition: all var(--transition-fast);
}

.btn-edit-title:hover {
  border-color: var(--border-color);
  background: var(--bg-secondary);
  color: var(--text-primary);
}

.title-edit-group {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--spacing-sm);
}

.title-edit-input {
  width: 100%;
  padding: 8px 12px;
  font-size: var(--font-size-2xl);
  font-weight: 600;
  border: 2px solid var(--primary);
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  color: var(--text-primary);
  outline: none;
}

.title-edit-input:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-20);
}

.title-edit-actions {
  display: flex;
  gap: var(--spacing-sm);
}

.btn-sm {
  padding: 6px 16px;
  font-size: var(--font-size-sm);
}

.book-byline {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
  min-height: 24px;
  margin-bottom: 14px;
  color: var(--text-secondary);
}

.book-byline strong {
  color: var(--text-primary);
  font-size: 16px;
}

.book-byline span {
  position: relative;
  font-size: 13px;
}

.book-byline span::before {
  position: absolute;
  left: -10px;
  color: var(--text-tertiary);
  content: "·";
}

.book-badges {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  margin-bottom: 20px;
}

.detail-badge {
  padding: 4px 9px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-full);
  background: var(--surface-card);
  color: var(--text-secondary);
  font-size: 11px;
}

.detail-badge.primary {
  border-color: var(--primary-alpha-30);
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-weight: 700;
}

.current-reading-card {
  max-width: 620px;
  margin-bottom: 20px;
  padding: 14px 16px;
  border: 1px solid var(--primary-alpha-20);
  border-radius: var(--radius-lg);
  background: var(--primary-alpha-10);
}

.current-reading-content {
  display: grid;
  min-width: 0;
  gap: 7px;
}

.current-reading-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  color: var(--text-secondary);
  font-size: 12px;
}

.current-reading-heading strong {
  color: var(--primary);
  font-variant-numeric: tabular-nums;
}

.current-reading-title {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.primary-actions {
  display: flex;
  align-items: stretch;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 22px;
}

.btn-large {
  padding: 12px 24px;
  font-size: 16px;
}

.read-button {
  min-width: 152px;
  justify-content: center;
}

.state-button {
  min-width: 92px;
  justify-content: center;
}

.state-button.active {
  border-color: var(--primary-alpha-30);
  background: var(--primary-alpha-10);
  color: var(--primary);
}

.organization-panel {
  display: grid;
  max-width: 660px;
  grid-template-columns: minmax(210px, 0.8fr) minmax(320px, 1.2fr);
  gap: 14px;
  padding: 14px 16px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-primary);
}

.organization-row {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}

.organization-label {
  flex-shrink: 0;
  color: var(--text-tertiary);
  font-size: 12px;
}

.organization-row .category-select,
.organization-row .book-tag-select {
  min-width: 0;
  flex: 1;
  width: auto;
}

.version-panel {
  margin-bottom: 30px;
  padding: 20px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-primary);
}

.version-panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-md);
  margin-bottom: 16px;
}

.version-panel-header h2 {
  margin: 2px 0 4px;
  color: var(--text-primary);
  font-size: 18px;
}

.version-panel-header p {
  margin: 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.section-eyebrow {
  color: var(--primary);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.version-upload-button {
  flex-shrink: 0;
}

.version-header-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.version-upload-button.disabled {
  opacity: 0.55;
  pointer-events: none;
}

.version-upload-button input {
  display: none;
}

.version-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
  gap: 10px;
}

.version-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  padding: 13px 14px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-card);
  cursor: pointer;
  transition: border-color var(--transition-fast),
    background var(--transition-fast);
}

.version-item:hover,
.version-item.active {
  border-color: var(--primary);
  background: var(--primary-alpha-10);
}

.version-item:focus-visible {
  outline: 2px solid var(--primary);
  outline-offset: 2px;
}

.version-format {
  display: flex;
  width: 52px;
  height: 36px;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-size: 11px;
  font-weight: 700;
}

.version-content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 3px;
  text-align: left;
}

.version-content strong {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.version-content span {
  color: var(--text-secondary);
  font-size: 11px;
}

.version-primary-badge,
.version-selected-badge {
  flex-shrink: 0;
  padding: 3px 7px;
  border-radius: var(--radius-full);
  font-size: 11px;
}

.version-primary-badge {
  background: var(--bg-secondary);
  color: var(--text-secondary);
}

.version-selected-badge {
  background: var(--primary);
  color: white;
}

.version-delete-button {
  flex-shrink: 0;
  padding: 5px;
  border: 0;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
}

.version-delete-button:hover {
  background: rgba(255, 59, 48, 0.1);
}

.btn-danger {
  color: var(--danger);
}

.btn-danger:hover {
  background: rgba(255, 59, 48, 0.1);
}

.book-rating {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  width: fit-content;
  margin-bottom: 20px;
  padding: 10px 8px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  background: var(--bg-primary);
}

.rating-label {
  color: var(--text-secondary);
  font-size: var(--font-size-sm);
}

.rating-stars {
  display: flex;
  gap: 4px;
}

.star {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--text-tertiary);
  font-size: 21px;
  line-height: 1;
  cursor: pointer;
  transition: all var(--transition-fast);
}

.star:hover {
  transform: scale(1.2);
}

.star.active {
  color: var(--warning);
}

/* 内容区 */
.book-body {
  border-top: 1px solid var(--border-light);
  padding-top: var(--spacing-xl);
}

.book-description {
  line-height: 1.8;
  color: var(--text-secondary);
  font-size: var(--font-size-base);
}

.description-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.description-panel-header h2,
.description-panel-header p {
  margin: 0;
}

.description-panel-header h2 {
  color: var(--text-primary);
  font-size: 17px;
  line-height: 1.4;
}

.description-panel-header p {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

.description-edit-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.description-edit-button {
  flex-shrink: 0;
  color: var(--primary);
}

.description-editor {
  display: grid;
  gap: 8px;
}

.description-editor :deep(.el-textarea__inner) {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: var(--bg-primary);
  color: var(--text-primary);
  font: inherit;
  line-height: 1.8;
  resize: vertical;
}

.description-shortcut {
  justify-self: end;
  color: var(--text-tertiary);
  font-size: 12px;
  line-height: 1.4;
}

.book-description-text {
  margin: 0;
  white-space: pre-line;
}

.no-description {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--spacing-sm);
  color: var(--text-tertiary);
}

.no-description small {
  color: var(--text-tertiary);
}

/* 书签 */
.bookmarks-state {
  display: flex;
  min-height: 190px;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 10px;
  color: var(--text-secondary);
  text-align: center;
}

.bookmarks-state p {
  margin: 0;
}

.bookmarks-state-icon {
  font-size: 34px;
}

.bookmarks-state-hint {
  color: var(--text-tertiary);
  font-size: 12px;
}

.detail-bookmark-list {
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  background: var(--surface-card);
}

.detail-bookmark-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-secondary);
  font-size: 13px;
}

.detail-bookmark-summary span:first-child {
  color: var(--text-primary);
  font-size: 15px;
  font-weight: 600;
}

.detail-bookmark-item {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 16px 18px;
  border-bottom: 1px solid var(--border-color-light);
}

.detail-bookmark-item:last-child {
  border-bottom: 0;
}

.detail-bookmark-icon {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 50%;
  background: var(--primary-alpha-10);
  font-size: 17px;
}

.detail-bookmark-content {
  min-width: 0;
}

.detail-bookmark-content h3,
.detail-bookmark-content p {
  margin: 0;
}

.detail-bookmark-content h3 {
  overflow: hidden;
  color: var(--text-primary);
  font-size: 14px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-bookmark-content p {
  display: -webkit-box;
  overflow: hidden;
  margin-top: 5px;
  color: var(--text-secondary);
  font-size: 12px;
  line-height: 1.5;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.detail-bookmark-meta {
  display: grid;
  justify-items: end;
  gap: 7px;
  color: var(--text-tertiary);
  font-size: 12px;
  white-space: nowrap;
}

.bookmark-chapter-badge {
  padding: 3px 8px;
  border-radius: 999px;
  background: var(--primary-alpha-10);
  color: var(--primary);
  font-weight: 600;
}

/* 信息列表 */
.info-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.info-panel-header h2,
.info-panel-header p {
  margin: 0;
}

.info-panel-header h2 {
  color: var(--text-primary);
  font-size: 17px;
}

.info-panel-header p {
  margin-top: 4px;
  color: var(--text-tertiary);
  font-size: 12px;
}

.info-edit-actions {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
}

.info-edit-button {
  flex-shrink: 0;
  color: var(--primary);
}

.info-list {
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: var(--spacing-md) var(--spacing-lg);
  border-bottom: 1px solid var(--border-light);
}

.info-item:last-child {
  border-bottom: none;
}

.info-label {
  width: 100px;
  color: var(--text-tertiary);
  font-size: var(--font-size-sm);
}

.info-value {
  flex: 1;
  color: var(--text-primary);
}

.info-edit-control {
  min-width: 0;
  flex: 1;
}

.info-date-control {
  width: 100%;
}

.source-path {
  min-width: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

/* 笔记 */
.book-notes {
  background: var(--bg-primary);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-radius: var(--radius-md);
  padding: var(--spacing-lg);
}

.notes-header {
  margin-bottom: var(--spacing-lg);
}

.notes-header h3 {
  font-size: var(--font-size-xl);
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 var(--spacing-sm) 0;
}

.notes-hint {
  font-size: var(--font-size-sm);
  color: var(--text-tertiary);
  margin: 0;
}

.book-notes .textarea {
  min-height: 200px;
  background: var(--bg-secondary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: var(--spacing-md);
  font-size: var(--font-size-base);
  line-height: 1.6;
  resize: vertical;
  width: 100%;
  box-sizing: border-box;
}

.book-notes .textarea:focus {
  border-color: var(--primary);
  box-shadow: 0 0 0 3px var(--primary-alpha-10);
  outline: none;
}

.notes-actions {
  margin-top: var(--spacing-md);
  display: flex;
  justify-content: flex-end;
}

.notes-actions .btn {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-sm);
}

/* 响应式 */
@media (max-width: 900px) {
  .book-hero {
    grid-template-columns: 180px minmax(0, 1fr);
    gap: 26px;
  }

  .book-cover {
    width: 180px;
    height: 254px;
  }

  .cover-action-row {
    width: 180px;
  }

  .organization-panel {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .book-detail-view {
    padding: var(--spacing-sm) 0;
  }

  .book-content {
    padding: 18px;
  }

  .detail-toolbar {
    align-items: stretch;
    flex-direction: column;
    margin-bottom: 24px;
  }

  .back-btn {
    align-self: flex-start;
  }

  .detail-toolbar-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .detail-toolbar-actions .btn,
  .more-actions-button {
    width: 100%;
    justify-content: center;
  }

  .book-hero {
    grid-template-columns: 1fr;
    gap: 24px;
    padding-right: 0;
    padding-left: 0;
  }

  .cover-column {
    justify-items: center;
  }

  .book-title-wrapper {
    justify-content: center;
    text-align: center;
  }

  .book-title {
    font-size: 30px;
  }

  .book-byline,
  .book-badges,
  .primary-actions {
    justify-content: center;
  }

  .current-reading-card {
    margin-right: auto;
    margin-left: auto;
    text-align: left;
  }

  .organization-panel {
    max-width: none;
  }

  .version-panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .version-upload-button {
    justify-content: center;
  }

  .version-header-actions {
    flex-direction: column;
  }

  .version-list {
    grid-template-columns: 1fr;
  }

  .version-item {
    gap: 9px;
    padding: 11px;
  }

  .version-primary-badge {
    display: none;
  }

  .toc-pagination {
    justify-content: flex-start;
    padding: 14px 12px;
  }

  .detail-bookmark-item {
    grid-template-columns: 32px minmax(0, 1fr);
    padding: 14px;
  }

  .detail-bookmark-icon {
    width: 32px;
    height: 32px;
  }

  .detail-bookmark-meta {
    grid-column: 2;
    justify-items: start;
    grid-template-columns: auto auto;
    align-items: center;
  }

  .info-panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .description-panel-header {
    align-items: stretch;
    flex-direction: column;
  }

  .description-edit-actions,
  .description-edit-button,
  .info-edit-actions,
  .info-edit-button {
    align-self: flex-end;
  }
}

@media (max-width: 480px) {
  .primary-actions {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }

  .read-button {
    grid-column: 1 / -1;
  }

  .organization-row {
    align-items: stretch;
    flex-direction: column;
  }

  .organization-label {
    text-align: left;
  }

  .tag-manage-link {
    align-self: flex-start;
  }

  .version-selected-badge {
    display: none;
  }

  .info-item {
    align-items: stretch;
    flex-direction: column;
    gap: 7px;
  }

  .info-label {
    width: auto;
  }
}

/* 弹窗样式 */
.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
}

.dialog {
  width: 420px;
  max-width: 90vw;
  max-height: 80vh;
  background: var(--surface-elevated);
  border-radius: var(--radius-xl);
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--spacing-lg);
  border-bottom: 1px solid var(--border-color-light);
  font-weight: 600;
  font-size: var(--font-size-lg);
  flex-shrink: 0;
}

.dialog-close {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-full);
  border: none;
  background: var(--bg-secondary);
  color: var(--text-secondary);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all var(--transition-fast);
}

.dialog-close:hover {
  background: var(--bg-tertiary);
  color: var(--text-primary);
}

.dialog-body {
  padding: var(--spacing-lg);
  overflow-y: auto;
  flex: 1;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--spacing-md);
  padding: var(--spacing-lg);
  border-top: 1px solid var(--border-color-light);
  flex-shrink: 0;
}

.loading-spinner-small {
  width: 20px;
  height: 20px;
  border: 2px solid var(--border-color);
  border-top-color: var(--primary);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .cover-image,
  .detail-tab-slider {
    transition: none;
  }
}
</style>
