<template>
  <div class="assistant-message-content">
    <template v-for="(block, blockIndex) in blocks" :key="`${block.type}-${blockIndex}`">
      <p v-if="block.type === 'paragraph'">
        <template v-for="(part, partIndex) in inlineParts(block.items[0] || '')" :key="partIndex">
          <strong v-if="part.strong">{{ part.text }}</strong>
          <span v-else>{{ part.text }}</span>
        </template>
      </p>
      <ul v-else-if="block.type === 'unordered'">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <template v-for="(part, partIndex) in inlineParts(item)" :key="partIndex">
            <strong v-if="part.strong">{{ part.text }}</strong>
            <span v-else>{{ part.text }}</span>
          </template>
        </li>
      </ul>
      <ol v-else-if="block.type === 'ordered'">
        <li v-for="(item, itemIndex) in block.items" :key="itemIndex">
          <template v-for="(part, partIndex) in inlineParts(item)" :key="partIndex">
            <strong v-if="part.strong">{{ part.text }}</strong>
            <span v-else>{{ part.text }}</span>
          </template>
        </li>
      </ol>
      <div
        v-else-if="block.type === 'table'"
        class="message-table-scroll"
        role="region"
        aria-label="助手回复表格"
        tabindex="0"
      >
        <table>
          <thead>
            <tr>
              <th v-for="(header, columnIndex) in block.headers" :key="columnIndex" scope="col">
                <template v-for="(part, partIndex) in inlineParts(header)" :key="partIndex">
                  <strong v-if="part.strong">{{ part.text }}</strong>
                  <span v-else>{{ part.text }}</span>
                </template>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in block.rows" :key="rowIndex">
              <td v-for="(cell, columnIndex) in row" :key="columnIndex">
                <template v-for="(part, partIndex) in inlineParts(cell)" :key="partIndex">
                  <strong v-if="part.strong">{{ part.text }}</strong>
                  <span v-else>{{ part.text }}</span>
                </template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface TextMessageBlock {
  type: 'paragraph' | 'unordered' | 'ordered'
  items: string[]
}

interface TableMessageBlock {
  type: 'table'
  headers: string[]
  rows: string[][]
}

type MessageBlock = TextMessageBlock | TableMessageBlock

interface InlinePart {
  text: string
  strong: boolean
}

const props = defineProps<{
  content: string
}>()

const blocks = computed(() => parseBlocks(props.content))

/**
 * 这里只解析聊天阅读最需要的段落、列表、表格和 **加粗**，所有内容都继续由 Vue 文本插值输出。
 * 不使用 v-html，因此模型回复中的标签或脚本只会作为普通文字展示。
 */
function parseBlocks(content: string): MessageBlock[] {
  const result: MessageBlock[] = []
  let paragraph: string[] = []
  const lines = content.split('\r\n').join('\n').split('\n')

  const flushParagraph = () => {
    if (!paragraph.length) return
    result.push({ type: 'paragraph', items: [paragraph.join('\n')] })
    paragraph = []
  }

  for (let lineIndex = 0; lineIndex < lines.length;) {
    const line = lines[lineIndex]
    const trimmed = line.trim()
    if (!trimmed) {
      flushParagraph()
      lineIndex++
      continue
    }

    const headers = parseTableCells(line)
    const separator = lineIndex + 1 < lines.length ? parseTableCells(lines[lineIndex + 1]) : null
    if (isTableHeader(headers, separator)) {
      flushParagraph()
      const rows: string[][] = []
      lineIndex += 2
      while (lineIndex < lines.length) {
        const cells = parseTableCells(lines[lineIndex])
        if (!cells || !lines[lineIndex].trim()) break
        rows.push(normalizeTableRow(cells, headers.length))
        lineIndex++
      }
      result.push({ type: 'table', headers, rows })
      continue
    }
    if (trimmed.startsWith('- ') || trimmed.startsWith('* ')) {
      flushParagraph()
      appendListItem(result, 'unordered', trimmed.slice(2).trim())
      lineIndex++
      continue
    }
    const orderedItem = orderedListItem(trimmed)
    if (orderedItem !== null) {
      flushParagraph()
      appendListItem(result, 'ordered', orderedItem)
      lineIndex++
      continue
    }
    paragraph.push(line)
    lineIndex++
  }
  flushParagraph()
  return result
}

function appendListItem(blocks: MessageBlock[], type: 'unordered' | 'ordered', item: string) {
  const previous = blocks[blocks.length - 1]
  if (previous?.type === type) {
    previous.items.push(item)
  } else {
    blocks.push({ type, items: [item] })
  }
}

function parseTableCells(line: string): string[] | null {
  let value = line.trim()
  if (!value.includes('|')) return null
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|') && !value.endsWith('\\|')) value = value.slice(0, -1)

  const cells: string[] = []
  let cell = ''
  for (let index = 0; index < value.length; index++) {
    const character = value[index]
    if (character === '\\' && value[index + 1] === '|') {
      cell += '|'
      index++
    } else if (character === '|') {
      cells.push(cell.trim())
      cell = ''
    } else {
      cell += character
    }
  }
  cells.push(cell.trim())
  return cells.length >= 2 ? cells : null
}

function isTableHeader(headers: string[] | null, separator: string[] | null): headers is string[] {
  return headers !== null
    && separator !== null
    && headers.length === separator.length
    && separator.every(cell => /^:?-{3,}:?$/.test(cell))
}

function normalizeTableRow(cells: string[], columnCount: number): string[] {
  return Array.from({ length: columnCount }, (_, index) => cells[index] || '')
}

function orderedListItem(line: string) {
  const separatorIndex = line.search(/[.)]/)
  if (separatorIndex <= 0 || line[separatorIndex + 1] !== ' ') return null
  const sequence = line.slice(0, separatorIndex)
  if (![...sequence].every(character => character >= '0' && character <= '9')) return null
  return line.slice(separatorIndex + 2).trim()
}

function inlineParts(value: string): InlinePart[] {
  const pieces = value.split('**')
  if (pieces.length < 3 || pieces.length % 2 === 0) {
    return [{ text: value, strong: false }]
  }
  return pieces
    .map((text, index) => ({ text, strong: index % 2 === 1 }))
    .filter(part => part.text.length > 0)
}
</script>

<style scoped>
.assistant-message-content {
  display: grid;
  gap: 8px;
  color: inherit;
  font-size: inherit;
  line-height: inherit;
  overflow-wrap: anywhere;
}

.assistant-message-content p,
.assistant-message-content ul,
.assistant-message-content ol {
  margin: 0;
}

.assistant-message-content p {
  white-space: pre-wrap;
}

.assistant-message-content ul,
.assistant-message-content ol {
  padding-left: 20px;
}

.assistant-message-content strong {
  font-weight: 700;
}

.message-table-scroll {
  max-width: 100%;
  overflow-x: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  overscroll-behavior-inline: contain;
}

.message-table-scroll:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 2px;
}

.message-table-scroll table {
  width: 100%;
  min-width: 520px;
  border-collapse: collapse;
  font-size: 14px;
  line-height: 1.5;
}

.message-table-scroll th,
.message-table-scroll td {
  padding: 9px 12px;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  text-align: left;
  vertical-align: top;
}

.message-table-scroll th:last-child,
.message-table-scroll td:last-child {
  border-right: 0;
}

.message-table-scroll tbody tr:last-child td {
  border-bottom: 0;
}

.message-table-scroll th {
  color: var(--el-text-color-primary);
  font-weight: 650;
  white-space: nowrap;
  background: var(--el-fill-color-light);
}

.message-table-scroll td {
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}

.message-table-scroll tbody tr:nth-child(even) td {
  background: var(--el-fill-color-extra-light);
}
</style>
