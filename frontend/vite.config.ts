import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { cpSync, createReadStream, existsSync, statSync } from 'node:fs'
import { extname, resolve, sep } from 'node:path'

const pdfjsRoot = resolve(__dirname, 'node_modules/pdfjs-dist')
const pdfjsAssetDirectories = ['cmaps', 'iccs', 'standard_fonts', 'wasm']

const pdfjsAssets = () => ({
  name: 'pdfjs-local-assets',
  configureServer(server: any) {
    server.middlewares.use((request: any, response: any, next: () => void) => {
      const pathname = decodeURIComponent((request.url || '').split('?', 1)[0])
      const prefix = '/pdfjs-assets/'
      if (!pathname.startsWith(prefix)) return next()
      const relativePath = pathname.slice(prefix.length)
      const directory = relativePath.split('/', 1)[0]
      if (!pdfjsAssetDirectories.includes(directory)) {
        response.statusCode = 404
        return response.end()
      }
      const directoryRoot = resolve(pdfjsRoot, directory)
      const filePath = resolve(pdfjsRoot, relativePath)
      if (!filePath.startsWith(`${directoryRoot}${sep}`)
          || !existsSync(filePath)
          || !statSync(filePath).isFile()) {
        response.statusCode = 404
        return response.end()
      }
      const contentTypes: Record<string, string> = {
        '.bcmap': 'application/octet-stream',
        '.icc': 'application/vnd.iccprofile',
        '.pfb': 'application/octet-stream',
        '.ttf': 'font/ttf',
        '.wasm': 'application/wasm',
      }
      response.setHeader('Content-Type', contentTypes[extname(filePath)] || 'application/octet-stream')
      return createReadStream(filePath).pipe(response)
    })
  },
  writeBundle(options: any) {
    const outputDirectory = resolve(options.dir || 'dist', 'pdfjs-assets')
    pdfjsAssetDirectories.forEach(directory => {
      cpSync(resolve(pdfjsRoot, directory), resolve(outputDirectory, directory), {
        recursive: true,
      })
    })
  },
})

export default defineConfig({
  plugins: [vue(), pdfjsAssets()],
  resolve: {
    alias: {
      '@': resolve(__dirname, 'src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
