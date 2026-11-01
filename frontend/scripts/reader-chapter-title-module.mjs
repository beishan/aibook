import { readFile } from 'node:fs/promises'
import ts from 'typescript'

// Load the same Vite CSS-inline module in Node without adding a test dependency.
const css = await readFile(new URL('../src/reader/chapterTitle.css', import.meta.url), 'utf8')
const source = await readFile(new URL('../src/reader/chapterTitle.ts', import.meta.url), 'utf8')
const cssUrl = `data:text/javascript;base64,${Buffer.from(`export default ${JSON.stringify(css)}`).toString('base64')}`
const js = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText.replace('./chapterTitle.css?inline', cssUrl)
export const chapterTitleModuleUrl = `data:text/javascript;base64,${Buffer.from(js).toString('base64')}`
