import assert from 'node:assert/strict'
import { readFile, mkdtemp, writeFile, chmod, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'

const dockerfile = await readFile(new URL('../frontend/Dockerfile', import.meta.url), 'utf8')
const compose = await readFile(new URL('../docker/docker-compose.yml', import.meta.url), 'utf8')
const pipeline = await readFile(new URL('../Jenkinsfile', import.meta.url), 'utf8')
assert.match(dockerfile, /ARG NPM_REGISTRY=https:\/\/registry.npmjs.org/)
assert.match(dockerfile, /--mount=type=cache,[^\n]*target=\/root\/\.npm/)
assert.match(dockerfile, /npm ci --registry="\$\{NPM_REGISTRY\}"/)
for (const flag of ['--prefer-offline', '--fetch-retries=5', '--fetch-timeout=300000', '--maxsockets=5']) assert.ok(dockerfile.includes(flag))
assert.match(compose, /args:\s*\n\s*NPM_REGISTRY: \$\{NPM_REGISTRY:-https:\/\/registry.npmjs.org\}/)
assert.match(pipeline, /name: 'NPM_REGISTRY'/)
assert.match(pipeline, /NPM_REGISTRY = "\$\{params.NPM_REGISTRY \?: ''\}"/)

const dir = await mkdtemp(join(tmpdir(), 'aibook-npm-build-'))
try {
  const docker = join(dir, 'docker')
  const envFile = join(dir, 'production.env')
  const calls = join(dir, 'calls.jsonl')
  await writeFile(docker, `#!/usr/bin/env node
const fs = require('node:fs');
fs.appendFileSync(process.env.NPM_BUILD_TEST_CALLS, JSON.stringify({ args: process.argv.slice(2), registry: process.env.NPM_REGISTRY }) + '\\n');
`)
  await chmod(docker, 0o755)
  for (const [parameter, configured, expected] of [
    ['', 'https://trusted.example/npm/', 'https://trusted.example/npm/'],
    ['  ', 'https://trusted.example/npm/', 'https://trusted.example/npm/'],
    [' https://parameter.example/ ', '', 'https://parameter.example/'],
    ['https://parameter.example/', 'https://trusted.example/npm/', 'https://parameter.example/'],
    ['', '', ''],
  ]) {
    await writeFile(envFile, `BOOKS_GID=1001\nBACKUP_GID=1001\nNPM_REGISTRY=${configured}\n`)
    await writeFile(calls, '')
    const result = spawnSync('bash', [new URL('../scripts/deploy.sh', import.meta.url).pathname, 'build', envFile], {
      encoding: 'utf8',
      env: { ...process.env, PATH: `${dir}:${process.env.PATH}`, NPM_REGISTRY: parameter,
        BOOKS_MOUNTS: '', BOOKS_GIDS: '', FONTS_PATH: '', FONTS_GID: '', FONT_MOUNTS: '', FONT_GIDS: '',
        NPM_BUILD_TEST_CALLS: calls },
    })
    assert.equal(result.status, 0, result.stderr)
    const log = (await readFile(calls, 'utf8')).trim().split('\n').map(line => JSON.parse(line))
    const build = log.find(call => call.args.includes('build'))
    assert.ok(build)
    assert.equal(build.registry, expected)
    assert.deepEqual(build.args.slice(-3), ['build', 'backend', 'frontend'])
    assert.ok(build.args.includes(envFile))
  }
} finally {
  await rm(dir, { recursive: true, force: true })
}
console.log('Frontend npm build: cache/retries, official default, Jenkins override and Secret file fallback passed')
