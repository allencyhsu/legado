import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(import.meta.dirname, '..')
const bookChapter = fs.readFileSync(
  path.join(root, 'src/views/BookChapter.vue'),
  'utf8',
)
const packageJson = JSON.parse(
  fs.readFileSync(path.join(root, 'package.json'), 'utf8'),
)

const assertContains = (content, pattern, message) => {
  if (!pattern.test(content)) {
    console.error(message)
    process.exitCode = 1
  }
}

assertContains(
  bookChapter,
  /<button(?=[\s\S]*?class=["']mobile-chapter-hotspot previous["'])(?=[\s\S]*?v-if=["']miniInterface["'])(?=[\s\S]*?@click\.stop=["']toPreChapter["'])[\s\S]*?<\/button>/,
  'Mobile reading view must expose a left-bottom hotspot for previous chapter.',
)

assertContains(
  bookChapter,
  /<button(?=[\s\S]*?class=["']mobile-chapter-hotspot next["'])(?=[\s\S]*?v-if=["']miniInterface["'])(?=[\s\S]*?@click\.stop=["']toNextChapter["'])[\s\S]*?<\/button>/,
  'Mobile reading view must expose a right-bottom hotspot for next chapter.',
)

assertContains(
  bookChapter,
  /\.mobile-chapter-hotspot\s*\{[\s\S]*position:\s*fixed;[\s\S]*bottom:\s*0;[\s\S]*z-index:\s*90;[\s\S]*background:\s*transparent;[\s\S]*touch-action:\s*manipulation;/,
  'Mobile chapter hotspots must be transparent fixed touch targets below the toolbar layer.',
)

assertContains(
  bookChapter,
  /\.mobile-chapter-hotspot\.previous\s*\{[\s\S]*left:\s*0;/,
  'Previous chapter hotspot must be anchored to the left edge.',
)

assertContains(
  bookChapter,
  /\.mobile-chapter-hotspot\.next\s*\{[\s\S]*right:\s*0;/,
  'Next chapter hotspot must be anchored to the right edge.',
)

assertContains(
  bookChapter,
  /@media\s+screen\s+and\s+\(min-width:\s*777px\)[\s\S]*\.mobile-chapter-hotspot\s*\{[\s\S]*display:\s*none;/,
  'Chapter hotspots must stay hidden on desktop layouts.',
)

if (!packageJson.scripts?.['test:mobile-chapter-hotspots']) {
  console.error('package.json must expose test:mobile-chapter-hotspots.')
  process.exitCode = 1
}

if (process.exitCode) process.exit(process.exitCode)
