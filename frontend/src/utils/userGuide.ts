export type GuideBlock =
  | { type: 'heading' | 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }

export interface GuideSection {
  id: string
  title: string
  blocks: GuideBlock[]
}

export interface UserGuide {
  title: string
  introduction: string
  sections: GuideSection[]
}

/** Render only the handbook's fixed text format; HTML is always ordinary text. */
export function parseUserGuide(source: string): UserGuide {
  const guide: UserGuide = { title: '', introduction: '', sections: [] }
  let section: GuideSection | undefined

  for (const rawLine of source.split(/\r?\n/)) {
    const line = rawLine.trim()
    if (!line) continue
    if (line.startsWith('# ')) {
      guide.title = line.slice(2)
    } else if (line.startsWith('## ')) {
      section = {
        id: `user-guide-section-${guide.sections.length + 1}`,
        title: line.slice(3),
        blocks: [],
      }
      guide.sections.push(section)
    } else if (!section) {
      guide.introduction += `${guide.introduction ? '\n' : ''}${line}`
    } else if (line.startsWith('### ')) {
      section.blocks.push({ type: 'heading', text: line.slice(4) })
    } else {
      const item = line.match(/^(?:(\d+)\. |(-) )(.*)$/)
      if (item) {
        const ordered = Boolean(item[1])
        const previous = section.blocks[section.blocks.length - 1]
        if (previous?.type === 'list' && previous.ordered === ordered) {
          previous.items.push(item[3])
        } else {
          section.blocks.push({ type: 'list', ordered, items: [item[3]] })
        }
      } else {
        section.blocks.push({ type: 'paragraph', text: line })
      }
    }
  }
  return guide
}

const normalizeSearch = (text: string) => text.normalize('NFKC').toLocaleLowerCase()

export function searchGuideSections(sections: GuideSection[], query: string): GuideSection[] {
  const words = normalizeSearch(query).trim().split(/\s+/).filter(Boolean)
  if (!words.length) return sections
  return sections.filter(section => {
    const text = normalizeSearch([
      section.title,
      ...section.blocks.map(block => block.type === 'list' ? block.items.join(' ') : block.text),
    ].join(' '))
    return words.every(word => text.includes(word))
  })
}
