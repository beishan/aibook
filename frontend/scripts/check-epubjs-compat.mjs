import { createRequire } from 'node:module'
import epubModule, { Book, EpubCFI, Rendition } from 'epubjs'

const require = createRequire(import.meta.url)
const epubPackage = require('epubjs/package.json')
const xmldomPackage = require('@xmldom/xmldom/package.json')
const epubFactory = epubModule.default || epubModule

const assertFunction = (value, name) => {
  if (typeof value !== 'function') {
    throw new Error(`epub.js compatibility check failed: ${name} is unavailable`)
  }
}

assertFunction(epubFactory, 'default factory')
assertFunction(Book, 'Book export')
assertFunction(Book.prototype.renderTo, 'Book.renderTo')
assertFunction(Book.prototype.destroy, 'Book.destroy')
assertFunction(Rendition.prototype.display, 'Rendition.display')
assertFunction(Rendition.prototype.next, 'Rendition.next')
assertFunction(Rendition.prototype.prev, 'Rendition.prev')
assertFunction(Rendition.prototype.spread, 'Rendition.spread')
assertFunction(Rendition.prototype.destroy, 'Rendition.destroy')

const cfi = 'epubcfi(/6/2[chapter]!/4/1:0)'
if (new EpubCFI(cfi).toString() !== cfi) {
  throw new Error('epub.js compatibility check failed: CFI round-trip changed')
}

if (epubPackage.version !== '0.3.93') {
  throw new Error(`unexpected epub.js version: ${epubPackage.version}`)
}
if (xmldomPackage.version !== '0.8.15') {
  throw new Error(`unsafe @xmldom/xmldom version: ${xmldomPackage.version}`)
}

console.log(`epub.js ${epubPackage.version} API compatible; @xmldom/xmldom ${xmldomPackage.version} secured`)
