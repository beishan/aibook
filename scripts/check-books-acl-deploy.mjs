import assert from 'node:assert/strict'
import { mkdtemp, writeFile, chmod, readFile, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const directory = await mkdtemp(join(tmpdir(), 'aibook-acl-'))
try {
  const docker = join(directory, 'docker')
  const envFile = join(directory, 'production.env')
  const calls = join(directory, 'calls.jsonl')
  await writeFile(docker, `#!/usr/bin/env node
const fs = require('node:fs');
const args = process.argv.slice(2);
fs.appendFileSync(process.env.ACL_TEST_CALLS, JSON.stringify(args) + '\\n');
if (args.some(arg => arg.includes('stat -c %g /books'))) process.stdout.write(process.env.ACL_TEST_GID || '1001');
if (args[0] === 'inspect' && args.includes('--format')) process.stdout.write('healthy');
`)
  await chmod(docker, 0o755)
  await writeFile(envFile, 'BOOKS_GID=1001\nBACKUP_GID=1001\n')
  const run = async (enabled, gid) => {
    await writeFile(calls, '')
    const result = spawnSync('bash', [fileURLToPath(new URL('./deploy.sh', import.meta.url)), 'deploy', envFile, join(directory, 'state')], {
      encoding: 'utf8',
      env: {
        ...process.env,
        PATH: `${directory}:${process.env.PATH}`,
        BACKEND_IMAGE: 'aibook-backend:test',
        BOOKS_MOUNTS: '/vol2/书籍:/scanfolder/books:ro',
        BOOKS_GIDS: '',
        FONT_MOUNTS: '',
        FONT_GIDS: '',
        FONTS_PATH: '',
        FONTS_GID: '',
        AUTO_AUTHORIZE_BOOKS: enabled,
        ACL_TEST_GID: gid,
        ACL_TEST_CALLS: calls,
        HEALTH_RETRIES: '1',
      },
    })
    const commands = (await readFile(calls, 'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse)
    return { result, commands }
  }
  const disabled = await run('false', '1001')
  assert.equal(disabled.result.status, 0, disabled.result.stderr)
  assert.ok(!disabled.commands.some((args) => args.some((arg) => arg.includes('setfacl'))))
  const rejected = await run('true', '1002')
  assert.notEqual(rejected.result.status, 0)
  assert.match(rejected.result.stderr, /所属 GID 1002/)
  assert.ok(!rejected.commands.some((args) => args.some((arg) => arg.includes('find /books'))))
  const enabled = await run('true', '1001')
  assert.equal(enabled.result.status, 0, enabled.result.stderr)
  const grant = enabled.commands.find((args) => args.some((arg) => arg.includes('find /books')))
  assert.ok(grant)
  assert.ok(grant.includes('type=bind,src=/vol2/书籍,dst=/books,bind-recursive=disabled'))
  assert.ok(grant.includes('1001'))
  assert.match(grant.join(' '), /-xdev/)
  assert.match(grant.join(' '), /d:g:\$\{gid\}:rX/)
  console.log('附加书库 ACL 授权开关、组校验和作用范围检查通过')
} finally {
  await rm(directory, { recursive: true, force: true })
}
