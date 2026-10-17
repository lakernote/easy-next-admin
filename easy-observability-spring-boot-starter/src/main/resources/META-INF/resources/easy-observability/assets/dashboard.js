const api = '/api/easy-observability'
const byId = id => document.getElementById(id)
const labels = { HTTP: 'HTTP', JOB: 'Job', KAFKA_CONSUMER: 'Kafka 消费', REDIS: 'Redis', DATABASE: '数据库', SQL: '数据库', TRANSACTION: '事务', REMOTE: '远程调用', ELASTICSEARCH: 'Elasticsearch', KAFKA_PRODUCER: 'Kafka 生产' }
let requestStats = []
let traces = []
let selectedSource = 'ALL'
let seriesCatalog = []
let liveMetrics = []
let seriesCache = new Map()
let selectedSeriesKeys = new Set()
let seriesSelectionInitialized = false
const seriesLimit = 4
const recentPageSize = 30
let recentPage = 0

function safeText(value) { return value == null || value === '' ? '—' : String(value) }
function formatNumber(value, suffix = '') {
  const number = Number(value)
  return Number.isFinite(number) && number >= 0 ? `${number.toLocaleString(undefined, { maximumFractionDigits: 2 })}${suffix}` : '—'
}
function formatBytes(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '—'
  if (bytes < 1024) return `${formatNumber(bytes)} B`
  if (bytes < 1048576) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / 1048576).toFixed(1)} MiB`
}
function formatMetricValue(name, value) {
  if (name === 'process.cpu.usage' || name === 'system.cpu.usage') {
    return `${(Number(value) * 100).toLocaleString(undefined, { maximumFractionDigits: 2, minimumFractionDigits: 2 })}%`
  }
  if (name === 'jvm.memory.used') return formatBytes(value)
  return formatNumber(value)
}
function formatTime(value) {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? safeText(value) : date.toLocaleString()
}
function esc(value) { return String(value ?? '').replaceAll('\\', '\\\\').replaceAll('"', '\\"') }
async function getJson(path) {
  const headers = { Accept: 'application/json' }
  let token = typeof window.EasyObservabilityAuth?.token === 'function' ? window.EasyObservabilityAuth.token() : ''
  if (!token) {
    try {
      const raw = localStorage.getItem('easy-next-admin-web-auth') || sessionStorage.getItem('easy-next-admin-web-auth')
      token = raw ? JSON.parse(raw).accessToken || '' : ''
    } catch { /* Standalone installations may use same-origin cookies. */ }
  }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${api}${path}`, { headers })
  if (response.status === 401 || response.status === 403) throw new Error('监控数据访问受限，请登录或配置 ObservabilityAccessPolicy')
  if (!response.ok) throw new Error(`读取监控数据失败：${response.status}`)
  return response.json()
}
function td(row, value, className = '') {
  const cell = document.createElement('td')
  cell.textContent = safeText(value)
  if (className) cell.className = className
  row.append(cell)
  return cell
}
function makeClickable(row, label, action) {
  row.classList.add('clickable')
  row.tabIndex = 0
  row.setAttribute('aria-label', label)
  row.addEventListener('click', action)
  row.addEventListener('keydown', event => {
    if (event.key !== 'Enter' && event.key !== ' ') return
    event.preventDefault()
    action()
  })
}
function periodStart() { return Date.now() - Number(byId('period').value) * 3600000 }
function inSelectedPeriod(trace) { return new Date(trace.finishedAt).getTime() >= periodStart() }
function showNotice(message = '') { byId('notice').textContent = message; byId('notice').hidden = !message }

function renderStats() {
  const visible = selectedSource === 'ALL' ? requestStats : requestStats.filter(item => item.source === selectedSource)
  const count = visible.reduce((sum, item) => sum + Number(item.count || 0), 0)
  const errors = visible.reduce((sum, item) => sum + Number(item.errorCount || 0), 0)
  const totalMs = visible.reduce((sum, item) => sum + Number(item.totalDurationMs || 0), 0)
  byId('total-count').textContent = formatNumber(count)
  byId('mean-duration').textContent = formatNumber(count ? totalMs / count : 0, ' ms')
  byId('error-rate').textContent = `${(count ? errors / count * 100 : 0).toFixed(2)}%`
  const rows = byId('entry-rows')
  rows.replaceChildren()
  const sumDuration = visible.reduce((sum, item) => sum + Number(item.totalDurationMs || 0), 0)
  for (const item of visible) {
    const row = document.createElement('tr')
    row.title = '查看保留的单次调用'
    makeClickable(row, `查看 ${labels[item.source] || item.source} ${item.operation} 的调用记录`, () => openEntryDetails(item))
    td(row, `${labels[item.source] || item.source} · ${item.operation}`)
    td(row, formatNumber(item.count), 'number')
    const shareCell = td(row, `${sumDuration ? (item.totalDurationMs / sumDuration * 100).toFixed(1) : '0.0'}%`, 'share')
    const bar = document.createElement('div'); bar.className = 'share-line'
    const fill = document.createElement('i'); fill.style.width = `${sumDuration ? Math.min(100, item.totalDurationMs / sumDuration * 100) : 0}%`; bar.append(fill); shareCell.append(bar)
    td(row, formatNumber(item.meanDurationMs, ' ms'), 'number')
    td(row, formatNumber(item.maxDurationMs, ' ms'), 'number')
    td(row, formatNumber(item.errorCount), 'number')
    td(row, `${Number(item.errorRate || 0).toFixed(2)}%`, 'number')
    td(row, formatNumber(item.meanCpuTimeMs, ' ms'), 'number')
    td(row, formatBytes(item.meanAllocatedBytes), 'number')
    rows.append(row)
  }
  byId('entry-empty').hidden = visible.length > 0
}

function entryOperationCandidates(trace) {
  return [
    `${trace.method || ''} · ${trace.name || ''}`,
    `${trace.name || ''} · ${trace.method || ''}`,
    `${trace.method || ''} ${trace.name || ''}`,
    trace.name || '', trace.method || ''
  ].map(value => value.trim().toLocaleLowerCase()).filter(Boolean)
}
function openEntryDetails(item) {
  const matches = traces.filter(trace => trace.sourceType === item.source
    && entryOperationCandidates(trace).some(candidate => candidate === item.operation.trim().toLocaleLowerCase()))
  byId('entry-dialog-title').textContent = `${labels[item.source] || item.source} · ${item.operation}`
  byId('entry-dialog-note').textContent = `累计 ${formatNumber(item.count)} 次；以下是当前 Trace 保留窗口中匹配的 ${matches.length} 条调用。`
  const rows = byId('entry-trace-rows'); rows.replaceChildren()
  for (const trace of matches.slice(0, 100)) {
    const row = document.createElement('tr'); makeClickable(row, `查看 Trace ${trace.traceId}`, () => {
      byId('entry-dialog').close(); openTrace(trace)
    })
    td(row, trace.error ? '错误' : trace.slow ? '慢' : '正常', `status ${trace.error ? 'error' : trace.slow ? 'slow' : 'ok'}`)
    td(row, formatNumber(trace.status), 'number'); td(row, formatNumber(trace.durationMs, ' ms'), 'number')
    td(row, trace.traceId, 'mono'); td(row, formatTime(trace.finishedAt)); rows.append(row)
  }
  if (!matches.length) {
    const row = document.createElement('tr'); const cell = document.createElement('td')
    cell.colSpan = 5; cell.className = 'empty'; cell.textContent = '当前保留窗口没有匹配的 Trace；累计指标仍可用。'
    row.append(cell); rows.append(row)
  }
  byId('entry-dialog').showModal()
}

async function openTrace(trace) {
  byId('dialog-title').textContent = `${labels[trace.sourceType] || trace.sourceType || 'Trace'} · ${trace.method || ''} ${trace.name || ''}`.trim()
  byId('trace-facts').replaceChildren()
  const facts = [
    `状态 ${trace.status ?? '—'}`, `耗时 ${formatNumber(trace.durationMs, ' ms')}`,
    `CPU ${formatNumber(trace.cpuTimeMs, ' ms')}`, `分配内存 ${formatBytes(trace.allocatedBytes)}`,
    `线程 ${formatNumber(trace.threadId)}`, `Trace ID ${trace.traceId || '—'}`,
    `完成 ${formatTime(trace.finishedAt)}`
  ]
  for (const fact of facts) { const span = document.createElement('span'); span.textContent = fact; byId('trace-facts').append(span) }
  byId('trace-tree').textContent = '正在读取调用树和下游明细…'
  byId('operation-rows').replaceChildren()
  byId('trace-dialog').showModal()
  let detail = trace
  try { detail = await getJson(`/traces/${encodeURIComponent(trace.traceId)}`) || trace }
  catch (error) { byId('trace-tree').textContent = error?.message || '读取 Trace 明细失败'; return }
  byId('trace-tree').textContent = detail.tree || '此请求未触发慢或错误，未保存完整调用树；已采集的下游操作见下表。'
  const operationRows = byId('operation-rows'); operationRows.replaceChildren()
  for (const { operation, depth } of orderOperations(detail.operations || [])) {
    const row = document.createElement('tr')
    td(row, labels[operation.type] || operation.type)
    const operationName = `${depth ? `${'　'.repeat(depth)}↳ ` : ''}${operation.name || ''}${operation.detail ? ` · ${operation.detail}` : ''}`
    td(row, operationName)
    td(row, formatNumber(operation.durationMs, ' ms'), 'number')
    td(row, formatNumber(operation.cpuTimeMs, ' ms'), 'number')
    td(row, formatBytes(operation.allocatedBytes), 'number')
    td(row, `${operation.threadName || '—'} (${formatNumber(operation.threadId)})`)
    td(row, operation.error ? '错误' : operation.slow ? '慢' : '正常', operation.error ? 'status error' : operation.slow ? 'status slow' : 'status ok')
    const errorCell = td(row, [operation.errorType, operation.errorMessage].filter(Boolean).join(': '))
    if (operation.stackTrace) {
      const details = document.createElement('details')
      const summary = document.createElement('summary'); summary.textContent = '查看异常堆栈'
      const stack = document.createElement('pre'); stack.textContent = operation.stackTrace
      details.append(summary, stack); errorCell.append(details)
    }
    operationRows.append(row)
  }
}

function orderOperations(operations) {
  const byId = new Map(operations.filter(item => item.spanId).map(item => [item.spanId, item]))
  const children = new Map()
  const roots = []
  const ordered = [...operations].sort((left, right) => new Date(left.finishedAt).getTime() - new Date(right.finishedAt).getTime())
  for (const operation of ordered) {
    const parentId = operation.parentSpanId
    if (parentId && parentId !== operation.spanId && byId.has(parentId)) {
      const siblings = children.get(parentId) || []
      siblings.push(operation)
      children.set(parentId, siblings)
    } else roots.push(operation)
  }
  const result = []
  const visited = new Set()
  const visit = (operation, depth) => {
    if (operation.spanId && visited.has(operation.spanId)) return
    if (operation.spanId) visited.add(operation.spanId)
    result.push({ operation, depth })
    for (const child of children.get(operation.spanId) || []) visit(child, depth + 1)
  }
  roots.forEach(root => visit(root, 0))
  ordered.forEach(operation => {
    if (!operation.spanId || !visited.has(operation.spanId)) visit(operation, 0)
  })
  return result
}

function traceRow(trace, target) {
  const row = document.createElement('tr')
  makeClickable(row, `查看 Trace ${trace.traceId}`, () => openTrace(trace))
  td(row, `${labels[trace.sourceType] || trace.sourceType || '入口'} · ${trace.method || ''} ${trace.name || ''}`.trim())
  if (target === 'slow') {
    td(row, formatNumber(trace.durationMs, ' ms'), 'number')
    td(row, formatNumber(trace.cpuTimeMs, ' ms'), 'number')
    td(row, formatBytes(trace.allocatedBytes), 'number')
    td(row, formatNumber(trace.threadId), 'number')
  } else {
    td(row, formatNumber(trace.status), 'number')
    const root = (trace.operations || []).find(op => op.type === trace.sourceType)
    td(row, root ? [root.errorType, root.errorMessage].filter(Boolean).join(': ') : trace.tree?.split('\n').find(line => /ERROR|HTTP_5/.test(line)) || '—')
    td(row, formatNumber(trace.durationMs, ' ms'), 'number')
    td(row, formatNumber(trace.threadId), 'number')
  }
  td(row, formatTime(trace.finishedAt))
  return row
}
function renderIncidentTables() {
  const selected = traces.filter(inSelectedPeriod)
  const slowMatches = selected.filter(trace => trace.slow)
  const errorMatches = selected.filter(trace => trace.error)
  const slow = slowMatches.slice(0, 50)
  const errors = errorMatches.slice(0, 50)
  byId('slow-count').textContent = formatNumber(slowMatches.length)
  byId('failure-count').textContent = formatNumber(errorMatches.length)
  byId('slow-total').textContent = `(${formatNumber(slowMatches.length)}${slowMatches.length > 50 ? '，显示前 50 条' : ''})`
  byId('error-total').textContent = `(${formatNumber(errorMatches.length)}${errorMatches.length > 50 ? '，显示前 50 条' : ''})`
  byId('slow-rows').replaceChildren(...slow.map(trace => traceRow(trace, 'slow')))
  byId('error-rows').replaceChildren(...errors.map(trace => traceRow(trace, 'error')))
  byId('slow-empty').hidden = slowMatches.length > 0
  byId('error-empty').hidden = errorMatches.length > 0
  const pageCount = Math.max(1, Math.ceil(traces.length / recentPageSize))
  recentPage = Math.min(recentPage, pageCount - 1)
  const start = recentPage * recentPageSize
  const recent = traces.slice(start, start + recentPageSize)
  const rows = byId('trace-rows'); rows.replaceChildren()
  for (const trace of recent) {
    const row = document.createElement('tr')
    makeClickable(row, `查看 Trace ${trace.traceId}`, () => openTrace(trace))
    const result = td(row, trace.error ? '错误' : trace.slow ? '慢' : '正常', `status ${trace.error ? 'error' : trace.slow ? 'slow' : 'ok'}`)
    td(row, `${labels[trace.sourceType] || trace.sourceType || '入口'} · ${trace.method || ''} ${trace.name || ''}`.trim())
    td(row, formatNumber(trace.status), 'number'); td(row, formatNumber(trace.durationMs, ' ms'), 'number')
    td(row, trace.traceId, 'mono'); td(row, formatTime(trace.finishedAt)); rows.append(row)
  }
  byId('trace-empty').hidden = recent.length > 0
  byId('trace-page').textContent = traces.length ? `${start + 1}–${start + recent.length} 条，共 ${traces.length} 条（最多读取最近 500 条）` : ''
  byId('trace-prev').disabled = recentPage <= 0
  byId('trace-next').disabled = recentPage >= pageCount - 1
}

function seriesTitle(series) {
  const tags = Object.entries(series.tags || {}).map(([key, value]) => `${key}=${value}`).join(', ')
  return `${series.name}${tags ? ` · ${tags}` : ''}`
}
function seriesKey(series) {
  const tags = Object.entries(series.tags || {}).sort(([left], [right]) => left.localeCompare(right))
  return `${series.name}|${tags.map(([key, value]) => `${key}=${value}`).join('&')}`
}
function chooseSeries() {
  const liveKeys = new Set(liveMetrics.map(seriesKey))
  const currentSeries = seriesCatalog.filter(series => liveKeys.has(seriesKey(series)))
  const find = (name, predicate = () => true) => currentSeries.find(item => item.name === name && predicate(item))
    || seriesCatalog.find(item => item.name === name && predicate(item))
  const priority = [
    find('process.cpu.usage'),
    find('jvm.memory.used', item => item.tags?.area === 'heap'),
    find('system.cpu.usage'),
    find('hikaricp.connections.active'),
    find('http.server.requests')
  ].filter(Boolean)
  const seen = new Set()
  return priority.filter(item => { const key = seriesTitle(item); if (seen.has(key)) return false; seen.add(key); return true }).slice(0, seriesLimit)
}
function renderSeriesPicker() {
  if (!seriesSelectionInitialized) {
    selectedSeriesKeys = new Set(chooseSeries().map(seriesKey))
    seriesSelectionInitialized = true
  }
  const options = byId('trend-options')
  const query = byId('trend-search').value.trim().toLocaleLowerCase()
  const filtered = seriesCatalog.filter(series => !query || seriesTitle(series).toLocaleLowerCase().includes(query))
  const fragment = document.createDocumentFragment()
  for (const series of filtered) {
    const key = seriesKey(series)
    const label = document.createElement('label')
    label.className = 'trend-option'
    const checkbox = document.createElement('input')
    checkbox.type = 'checkbox'
    checkbox.value = key
    checkbox.checked = selectedSeriesKeys.has(key)
    checkbox.disabled = !checkbox.checked && selectedSeriesKeys.size >= seriesLimit
    const text = document.createElement('span')
    text.textContent = seriesTitle(series)
    label.append(checkbox, text)
    fragment.append(label)
  }
  options.replaceChildren(fragment)
  byId('trend-selection-note').textContent = `${selectedSeriesKeys.size}/${seriesLimit} 项已选择${filtered.length < seriesCatalog.length ? ` · 显示 ${filtered.length}/${seriesCatalog.length} 项` : ''}`
}
function drawChart(card, points, measurement, name, colorClass = '') {
  const ns = 'http://www.w3.org/2000/svg'
  const svg = document.createElementNS(ns, 'svg'); svg.setAttribute('viewBox', '0 0 420 150'); svg.setAttribute('preserveAspectRatio', 'none')
  for (let y = 18; y <= 128; y += 36) { const line = document.createElementNS(ns, 'line'); line.setAttribute('x1', '32'); line.setAttribute('x2', '412'); line.setAttribute('y1', String(y)); line.setAttribute('y2', String(y)); line.setAttribute('class', 'chart-grid'); svg.append(line) }
  const values = points.map(point => Number(point.measurements?.[measurement])).filter(Number.isFinite)
  const path = document.createElementNS(ns, 'path'); path.setAttribute('class', `chart-line ${colorClass}`)
  if (values.length > 1) {
    const min = Math.min(...values); const max = Math.max(...values); const range = max - min || 1
    const d = values.map((value, index) => `${index ? 'L' : 'M'} ${32 + index * 380 / (values.length - 1)} ${128 - (value - min) / range * 108}`).join(' ')
    path.setAttribute('d', d); svg.append(path)
    const high = document.createElementNS(ns, 'text'); high.setAttribute('x', '1'); high.setAttribute('y', '21'); high.setAttribute('class', 'chart-label'); high.textContent = formatMetricValue(name, max); svg.append(high)
    const low = document.createElementNS(ns, 'text'); low.setAttribute('x', '1'); low.setAttribute('y', '128'); low.setAttribute('class', 'chart-label'); low.textContent = formatMetricValue(name, min); svg.append(low)
  } else {
    const msg = document.createElementNS(ns, 'text'); msg.setAttribute('x', '110'); msg.setAttribute('y', '74'); msg.setAttribute('class', 'chart-label'); msg.textContent = '等待首个采样点'; svg.append(msg)
  }
  card.append(svg)
}
async function renderTrends() {
  const grid = byId('trend-grid'); grid.replaceChildren()
  const selected = seriesCatalog.filter(series => selectedSeriesKeys.has(seriesKey(series))).slice(0, seriesLimit)
  if (!selected.length) { const p = document.createElement('p'); p.className = 'empty'; p.textContent = '当前没有可显示的 Micrometer 历史指标。'; grid.append(p); return }
  for (let index = 0; index < selected.length; index++) {
    const series = selected[index]
    const key = seriesTitle(series)
    const card = document.createElement('article'); card.className = 'trend-card'
    const heading = document.createElement('h3'); heading.textContent = key; card.append(heading)
    grid.append(card)
    let points = seriesCache.get(key)
    if (!points) {
      // The longest visible range is 30 days. RRD4J uses at most 30-minute points there,
      // so 5,000 points cover the full chart without transferring the API's 50,000-point cap.
      const params = new URLSearchParams({ name: series.name, tags: JSON.stringify(series.tags || {}), limit: '5000' })
      points = await getJson(`/metrics/history?${params}`)
      seriesCache.set(key, points)
    }
    const measurement = (series.measurements || []).includes('VALUE') ? 'VALUE' : series.measurements?.[0]
    const from = periodStart()
    const windowPoints = points.filter(point => new Date(point.capturedAt).getTime() >= from
      && !(series.name === 'jvm.memory.used' && Number(point.measurements?.[measurement]) <= 0)).slice().reverse()
    drawChart(card, windowPoints, measurement, series.name, ['','alt','green',''][index])
  }
}

async function refresh() {
  byId('refresh').disabled = true; showNotice('')
  try {
    const [statsResult, tracesResult, catalogResult, storageResult, metricsResult] = await Promise.allSettled([
      getJson('/request-statistics?source=ALL&limit=500'), getJson('/traces?limit=500'), getJson('/metrics/series'),
      getJson('/metrics/storage'), getJson('/metrics?limit=2000')
    ])
    const failed = []
    if (statsResult.status === 'fulfilled') { requestStats = statsResult.value; renderStats() } else failed.push(statsResult.reason?.message)
    if (tracesResult.status === 'fulfilled') { traces = tracesResult.value; renderIncidentTables() } else failed.push(tracesResult.reason?.message)
    if (catalogResult.status === 'fulfilled') { seriesCatalog = catalogResult.value; seriesCache.clear(); renderSeriesPicker() } else failed.push(catalogResult.reason?.message)
    if (storageResult.status === 'fulfilled' && storageResult.value) byId('instance').textContent = `当前实例 · RRD ${formatBytes(storageResult.value.storageBytes)}`
    if (metricsResult.status === 'fulfilled') liveMetrics = metricsResult.value
    else { liveMetrics = []; failed.push(metricsResult.reason?.message) }
    if (catalogResult.status === 'fulfilled') await renderTrends()
    if (failed.length) showNotice(failed.filter(Boolean).join('；'))
    byId('updated').textContent = `更新于 ${new Date().toLocaleTimeString()}`
  } catch (error) { showNotice(error?.message || '加载监控报告失败') }
  finally { byId('refresh').disabled = false }
}

byId('refresh').addEventListener('click', refresh)
byId('trace-prev').addEventListener('click', () => { recentPage = Math.max(0, recentPage - 1); renderIncidentTables() })
byId('trace-next').addEventListener('click', () => { recentPage++; renderIncidentTables() })
byId('close-entry-dialog').addEventListener('click', () => byId('entry-dialog').close())
byId('trend-search').addEventListener('input', renderSeriesPicker)
byId('trend-options').addEventListener('change', event => {
  const checkbox = event.target.closest('input[type="checkbox"]')
  if (!checkbox) return
  if (checkbox.checked) selectedSeriesKeys.add(checkbox.value)
  else selectedSeriesKeys.delete(checkbox.value)
  renderSeriesPicker()
  renderTrends().catch(error => showNotice(error?.message || '读取指标趋势失败'))
})
byId('period').addEventListener('change', () => { renderIncidentTables(); renderTrends().catch(error => showNotice(error.message)) })
byId('entry-filters').addEventListener('click', event => {
  const button = event.target.closest('button[data-source]')
  if (!button) return
  selectedSource = button.dataset.source
  byId('entry-filters').querySelectorAll('button').forEach(item => item.classList.toggle('selected', item === button))
  renderStats()
})
byId('close-dialog').addEventListener('click', () => byId('trace-dialog').close())
refresh()
