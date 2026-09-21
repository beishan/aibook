import assert from 'node:assert/strict'
import { readdir, readFile } from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { parse as parseTemplate, NodeTypes } from '@vue/compiler-dom'
import { parse as parseSfc } from '@vue/compiler-sfc'

const srcRoot = fileURLToPath(new URL('../src', import.meta.url))

async function collectVueFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map(async (entry) => {
    const entryPath = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectVueFiles(entryPath)
    return entry.isFile() && entry.name.endsWith('.vue') ? [entryPath] : []
  }))
  return nested.flat()
}

const files = await collectVueFiles(srcRoot)
const dialogsWithoutBodyMount = []
let dialogCount = 0

for (const file of files) {
  const source = await readFile(file, 'utf8')
  const { descriptor } = parseSfc(source, { filename: file })
  if (!descriptor.template) continue

  const templateAst = parseTemplate(descriptor.template.content)
  const visit = (node) => {
    if (node.type === NodeTypes.ELEMENT) {
      if (node.tag === 'el-dialog') {
        dialogCount += 1
        const mountedToBody = node.props.some(
          (prop) => prop.type === NodeTypes.ATTRIBUTE && prop.name === 'append-to-body',
        )
        if (!mountedToBody) {
          const line = descriptor.template.loc.start.line + node.loc.start.line - 1
          dialogsWithoutBodyMount.push(`${path.relative(srcRoot, file)}:${line}`)
        }
      }
      node.children.forEach(visit)
    }
  }
  templateAst.children.forEach(visit)
}

assert.ok(dialogCount > 0, 'dialog regression fixture must include Element Plus dialogs')
assert.deepEqual(
  dialogsWithoutBodyMount,
  [],
  `all Element Plus dialogs must mount outside filtered or transformed page containers:\n${dialogsWithoutBodyMount.join('\n')}`,
)

console.log(`All ${dialogCount} Element Plus dialogs mount their overlays on document.body`)
