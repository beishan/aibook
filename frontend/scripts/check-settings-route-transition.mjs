import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { parse } from '@vue/compiler-sfc'
import { baseParse, NodeTypes } from '@vue/compiler-dom'

const source = await readFile(new URL('../src/views/SettingsView.vue', import.meta.url), 'utf8')
const { descriptor } = parse(source)
const template = baseParse(descriptor.template.content)
const roots = template.children.filter(node =>
  node.type !== NodeTypes.COMMENT && !(node.type === NodeTypes.TEXT && !node.content.trim()),
)
assert.equal(roots.length, 1,
  'Settings must have one element root: route Transition mode=out-in can stall after a fragment leaves')
assert.equal(roots[0].type, NodeTypes.ELEMENT)
assert.equal(roots[0].tag, 'div')
for (const tag of ['ScraperProxyDialog', 'ScraperTrialDialog']) {
  assert(roots[0].children.some(node => node.type === NodeTypes.ELEMENT && node.tag === tag),
    `${tag} must remain inside the settings root so opening/closing it cannot change the route root`)
}
console.log('Settings route transition: single element root contains both scraper dialogs')

// Exercise the same out-in lifecycle as every application layout without a browser or API.
const { BaseTransition, createRenderer, h, nextTick, ref } = await import('vue')
const node = (type, text = '') => ({ type, text, children: [], parent: null })
const detach = child => {
  if (child.parent) {
    const siblings = child.parent.children
    siblings.splice(siblings.indexOf(child), 1)
    child.parent = null
  }
}
const renderer = createRenderer({
  createElement: type => node(type),
  createText: text => node('text', text),
  createComment: text => node('comment', text),
  setText: (target, text) => { target.text = text },
  setElementText: (target, text) => { target.text = text; target.children = [] },
  parentNode: target => target.parent,
  nextSibling: target => target.parent?.children[target.parent.children.indexOf(target) + 1] ?? null,
  patchProp() {},
  remove: detach,
  insert(child, parent, anchor = null) {
    detach(child)
    const index = anchor ? parent.children.indexOf(anchor) : -1
    parent.children.splice(index < 0 ? parent.children.length : index, 0, child)
    child.parent = parent
  },
})
const settings = { render: () => h(roots[0].tag, 'settings') }
const books = { render: () => h('main', 'books') }
const crawler = { render: () => h('section', 'crawler') }
const current = ref('settings')
const routes = { settings, books, crawler }
const container = node('container')
const app = renderer.createApp({
  render: () => h(BaseTransition, { mode: 'out-in', onLeave: (_element, done) => queueMicrotask(done) }, {
    default: () => h(routes[current.value]),
  }),
})
app.mount(container)
for (const destination of ['books', 'settings', 'crawler', 'settings', 'books']) {
  current.value = destination
  for (let tick = 0; tick < 4; tick++) await nextTick()
  assert(container.children.some(child => child.text === destination),
    `Navigation to ${destination} must display content without a reload`)
}
app.unmount()
console.log('Settings → books/crawler repeated out-in transitions display the destination without reload')
