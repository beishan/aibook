import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'

const [pipeline, guide] = await Promise.all([
  readFile(new URL('../Jenkinsfile', import.meta.url), 'utf8'),
  readFile(new URL('../docs/jenkins-nas-deployment.md', import.meta.url), 'utf8'),
])

assert.match(
  pipeline,
  /options\s*\{[\s\S]*?skipDefaultCheckout\(true\)/,
  'the pipeline must disable the hidden Declarative checkout',
)

const checkoutStage = pipeline.match(/stage\('Checkout'\)\s*\{([\s\S]*?)\n\s*stage\('Validate'\)/)?.[1]
assert.ok(checkoutStage, 'the named Checkout stage must exist')
assert.match(checkoutStage, /timeout\(time: 10, unit: 'MINUTES'\)/, 'checkout must have a bounded timeout')
assert.match(checkoutStage, /retry\(3\)\s*\{[\s\S]*?deleteDir\(\)[\s\S]*?git branch: 'main'/, 'GitHub checkout must retry from a clean workspace inside the named stage')

assert.match(guide, /读取 Jenkinsfile/, 'the deployment guide must explain the SCM bootstrap boundary')
assert.match(guide, /SCM checkout retry count/, 'the deployment guide must document bootstrap checkout retries')

console.log('Jenkins checkout runs once in a named, bounded, retryable stage')
