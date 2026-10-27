# 设计图生成提示词

生成方式：内置 ImageGen，全部使用 `01-reference.png` 作为参考编辑图。

## 共用约束

```text
Use case: ui-mockup. EDIT the supplied reference screenshot to produce ONE detailed desktop reader UI state. Image 1 is the authoritative edit target, preserve its visual identity and spatial layout. 1536x1024 landscape screenshot. Same Chinese app 汗牛充栋, same book 人间词话 by 王国维, same small watercolor book cover, exact same top global navigation with search and 书架/阅读/笔记/书摘, same left sidebar about 380px wide, same thin pale blue-gray borders, cool white reading canvas, restrained cobalt-blue selection, generous Chinese serif text with UI sans-serif labels, same bottom previous/next chapter and reading progress footer. Keep top and footer positions. Left sidebar starts with book identity and below it the four tabs 目录/书签/笔记/划线; refine these four tabs into one restrained macOS-style sliding segmented selector with blue selected pill. No vertical tool rail. No warm cream, sage green, plant illustrations, gold, or decorative textures. All Chinese labels crisp. Keep the composition close to the reference and edit only what the requested state needs.
```

## 02-search

```text
STATE: book-internal search open. Top right reader search icon active. Under book metadata in left sidebar show heading 书内搜索 with a close icon and field containing 境界; below show 找到 12 处结果 and a scrollable list of results with chapter names, page locations and 2-line snippets, search matches lightly blue. Current hit in main reading text has a stronger blue highlight. Global search in top navigation remains separate from this search. Full body text remains readable.
```

## 03-bookmarks

```text
STATE: left sidebar 书签 tab selected. Below show + 添加当前书签 button, 6 个书签 small count, and tidy bookmark list entries with chapter title, short excerpt, page number and saved date, ellipsis for actions. One bookmark selected pale blue. Main body at matching chapter; upper reader bookmark icon blue filled; a tiny unobtrusive 已添加书签 success message near it. Keep body text quiet, no selection toolbar.
```

## 04-notes

```text
STATE: left sidebar 笔记 tab selected with scrollable annotation list, subtle chapter labels, quoted excerpts and short personal notes. Select first note. On right open a 340px wide annotation editor drawer with heading 编辑笔记, close button, quoted selected text, multiline editable personal note, and bottom 取消 / 保存笔记 buttons. Reflow the center reading column to fit, no overlay covering text. Maintain readable Chinese type and whitespace. No invented tags or AI features.
```

## 05-highlights

```text
STATE: left sidebar 划线 tab selected with color-filter dots and list of highlighted passages with chapter and location. Main reading paragraph selected pale blue exactly like reference. A compact floating contextual toolbar immediately below selected text has four muted highlight color dots, 写笔记, 复制, and overflow icon; delicate blue focus edge. Sidebar active item corresponds to selected passage. Maintain generous text spacing and legible selection. No editing drawer in this state.
```

## 06-settings

```text
STATE: reader Aa control active; open a polished 340px right side 阅读设置 drawer without covering center text, close X and subtle dividers. Show font family 思源宋体 dropdown, font size stepper 22 px, line-height slider 1.8, page width segmented narrow/medium/wide, reading mode segmented 翻页/滚动, theme swatches 日间/纸张/夜间, 显示阅读进度 toggle. Blue accents and rounded sliding segmented controls. Small footer 偏好设置随账户保存. Left sidebar still shows table of contents, center reading column slightly narrower.
```

## 07-fullscreen

```text
STATE: full-screen immersive reading. Hide the app global navigation AND book sidebar, expand cool-white reading canvas across frame, keep a centered ~920px readable Chinese serif column, book title 人间词话 in a discreet top header, chapter title 第一章 词之境界. Show tiny upper-right 退出全屏 icon/button. On mouse movement reveal a compact pale blue-gray floating bottom toolbar with 目录/书签/笔记/Aa/退出全屏 and a thin chapter progress row below. Keep visual DNA of original reference; no vertical rail or warm palette.
```

