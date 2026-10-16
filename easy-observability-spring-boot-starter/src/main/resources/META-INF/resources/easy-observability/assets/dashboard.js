const api = '/api/easy-observability'
const traceRows = document.querySelector('#trace-rows')
const metricRows = document.querySelector('#metric-rows')
const notice = document.querySelector('#notice')
const dialog = document.querySelector('#trace-dialog')
let metrics = []
let traces = []
let requestStatistics = []
let selectedRequestSource = 'ALL'
let slowTraces = []
let errorTraces = []
let metricSeries = []
let selectedSeriesKeys = []
let historyRequestVersion = 0
const MAX_SELECTED_SERIES = 4
const incidentSources = [
  { key: 'ALL', label: '全部入口' },
  { key: 'HTTP', label: 'HTTP' },
  { key: 'JOB', label: 'Job' },
  { key: 'KAFKA_CONSUMER', label: 'Kafka 消费' },
  { key: 'OTHER', label: '其他独立 Trace' }
]
const selectedIncidentSource = { slow: 'ALL', error: 'ALL' }
const sourceLabels = {
  HTTP: 'HTTP', JOB: 'Job', KAFKA_CONSUMER: 'Kafka 消费', KAFKA_PRODUCER: 'Kafka 生产',
  KAFKA: 'Kafka', REDIS: 'Redis', TRANSACTION: '事务', REMOTE: '远程调用',
  DATABASE: '数据库', SQL: '数据库', OTHER: '其他'
}

function cell(value, className = '') {
  const element = document.createElement('td')
  element.textContent = value ?? '-'
  if (className) element.className = className
  return element
}

function setNotice(message = '') {
  notice.textContent = message
  notice.hidden = !message
}

async function getJson(path) {
  const headers = { Accept: 'application/json' }
  const hostToken = window.EasyObservabilityAuth?.token?.()
  let token = typeof hostToken === 'string' ? hostToken : ''
  if (!token) {
    try {
      const raw = localStorage.getItem('easy-next-admin-web-auth') || sessionStorage.getItem('easy-next-admin-web-auth')
      token = raw ? JSON.parse(raw).accessToken || '' : ''
    } catch { /* The dashboard can run without a browser-stored bearer token. */ }
  }
  if (token) headers.Authorization = `Bearer ${token}`
  const response = await fetch(`${api}${path}`, { headers })
  if (response.status === 401 || response.status === 403) {
    throw new Error('监控数据访问受限：请登录并配置 ObservabilityAccessPolicy，或仅在可信本地环境显式开启匿名访问')
  }
  if (!response.ok) throw new Error(`请求失败：${response.status}`)
  return response.json()
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function formatNumber(value, unit = '') {
  return Number.isFinite(Number(value)) && Number(value) >= 0
    ? `${Number(value).toLocaleString()}${unit}`
    : '-'
}

function formatBytes(value) {
  const bytes = Number(value)
  if (!Number.isFinite(bytes) || bytes < 0) return '-'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KiB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MiB`
}

function renderTraces(items) {
  traces = items
  traceRows.replaceChildren()
  document.querySelector('#trace-count').textContent = `${items.length} 条`
  document.querySelector('#trace-empty').hidden = items.length > 0
  for (const trace of items) {
    const row = document.createElement('tr')
    row.className = 'clickable'
    const result = document.createElement('td')
    const badge = document.createElement('span')
    badge.className = `pill ${trace.error ? 'error' : trace.slow ? 'slow' : 'ok'}`
    badge.textContent = trace.error ? '错误' : trace.slow ? '慢请求' : '正常'
    result.append(badge)
    row.append(result, cell(`${trace.method || ''} ${trace.name || ''}`.trim()), cell(String(trace.status)), cell(`${trace.durationMs} ms`), cell(formatNumber(trace.cpuTimeMs, ' ms')), cell(formatBytes(trace.allocatedBytes)), cell(formatNumber(trace.threadId)), cell(trace.traceId, 'mono'), cell(formatTime(trace.finishedAt), 'muted'))
    row.addEventListener('click', () => showTrace(trace))
    traceRows.append(row)
  }
}

function renderRequestStatistics(items) {
  requestStatistics = items
  const visible = selectedRequestSource === 'ALL'
    ? items : items.filter(item => item.source === selectedRequestSource)
  const body = document.querySelector('#request-rows')
  body.replaceChildren()
  document.querySelector('#request-count').textContent = `${visible.length} 项`
  document.querySelector('#request-empty').hidden = visible.length > 0
  const summary = document.querySelector('#request-summary')
  summary.replaceChildren()
  const count = visible.reduce((total, item) => total + item.count, 0)
  const errors = visible.reduce((total, item) => total + item.errorCount, 0)
  const totalDuration = visible.reduce((total, item) => total + item.totalDurationMs, 0)
  const maxDuration = Math.max(0, ...visible.map(item => item.maxDurationMs))
  const summaryItems = [
    ['调用总数', formatNumber(count)],
    ['平均耗时', formatNumber(count ? totalDuration / count : 0, ' ms')],
    ['最大耗时', formatNumber(maxDuration, ' ms')],
    ['错误率', `${(count ? errors / count * 100 : 0).toFixed(2)}%`]
  ]
  for (const [label, value] of summaryItems) {
    const card = document.createElement('div')
    card.className = 'request-summary-item'
    const title = document.createElement('span')
    title.textContent = label
    const number = document.createElement('strong')
    number.textContent = value
    card.append(title, number)
    summary.append(card)
  }
  for (const item of visible) {
    const row = document.createElement('tr')
    const errorRate = `${Number(item.errorRate || 0).toFixed(2)}%`
    row.append(cell(sourceLabels[item.source] || item.source), cell(item.operation), cell(formatNumber(item.count)),
      cell(formatNumber(item.meanDurationMs, ' ms')), cell(formatNumber(item.maxDurationMs, ' ms')),
      cell(formatNumber(item.errorCount)), cell(errorRate), cell(formatNumber(item.meanCpuTimeMs, ' ms')),
      cell(formatBytes(item.meanAllocatedBytes)))
    const trendCell = document.createElement('td')
    const trendButton = document.createElement('button')
    trendButton.className = 'trend-link'
    trendButton.type = 'button'
    trendButton.textContent = '查看'
    trendButton.addEventListener('click', () => showRequestTrend(item))
    trendCell.append(trendButton)
    row.append(trendCell)
    body.append(row)
  }
}

async function showRequestTrend(item) {
  const matching = metricSeries.filter(series => series.name === 'easy.operations.duration'
    && series.tags?.source === item.source && series.tags?.operation === item.operation)
  if (!matching.length) {
    setNotice('这项操作的历史趋势尚未采集；等待下一个指标采集周期后再查看')
    return
  }
  selectedSeriesKeys = matching.slice(0, MAX_SELECTED_SERIES).map(seriesKey)
  document.querySelector('#history-search').value = 'easy.operations.duration'
  renderSeries()
  document.querySelector('[data-tab="history"]').click()
  try {
    await loadHistory()
  } catch (error) {
    setNotice(error.message || '读取请求趋势失败')
  }
}

function renderIncidentRows(kind, items) {
  const prefix = kind === 'error' ? 'error' : 'slow'
  const body = document.querySelector(`#${prefix}-rows`)
  const selectedSource = selectedIncidentSource[kind]
  const searchedItems = filterIncidentTraces(kind, items)
  const visibleItems = selectedSource === 'ALL'
    ? searchedItems
    : selectedSource === 'OTHER'
      ? searchedItems.filter(item => !['HTTP', 'JOB', 'KAFKA_CONSUMER'].includes(rootSource(item)))
      : searchedItems.filter(item => rootSource(item) === selectedSource)
  renderIncidentSources(kind, searchedItems)
  body.replaceChildren()
  document.querySelector(`#${prefix}-count`).textContent = selectedSource === 'ALL'
    ? `${visibleItems.length} / ${items.length} 条` : `${visibleItems.length} / ${searchedItems.length} 条`
  const empty = document.querySelector(`#${prefix}-empty`)
  empty.hidden = visibleItems.length > 0
  empty.textContent = items.length ? '当前条件下没有匹配的 Trace。' : kind === 'error' ? '暂时没有错误 Trace。' : '暂时没有慢 Trace。'
  for (const trace of visibleItems) {
    const row = document.createElement('tr')
    row.className = 'clickable'
    const source = rootSource(trace)
    const operations = trace.operations || []
    const rootOperation = [...operations].filter(operation => operation.type === source)
      .sort((left, right) => Math.abs(new Date(left.finishedAt) - new Date(trace.finishedAt))
        - Math.abs(new Date(right.finishedAt) - new Date(trace.finishedAt)))[0]
    const errorOperation = operations.find(operation => operation.error) || rootOperation
    const cells = [cell(sourceLabels[source] || '其他'), cell(`${trace.method || ''} ${trace.name || ''}`.trim()),
      cell(String(trace.status)), cell(`${trace.durationMs} ms`), cell(formatNumber(trace.cpuTimeMs, ' ms')),
      cell(formatBytes(trace.allocatedBytes)), cell(formatNumber(trace.threadId))]
    if (kind === 'error') {
      cells.push(cell(errorOperation?.errorType || errorOperation?.errorMessage || '执行失败'))
    }
    cells.push(cell(trace.traceId, 'mono'), cell(formatTime(trace.finishedAt), 'muted'))
    row.append(...cells)
    row.addEventListener('click', () => showTrace({ traceId: trace.traceId }))
    body.append(row)
  }
}

function filterIncidentTraces(kind, items) {
  const prefix = kind === 'error' ? 'error' : 'slow'
  const keyword = document.querySelector(`#${prefix}-search`).value.trim().toLowerCase()
  const windowMinutes = Number(document.querySelector(`#${prefix}-window`).value)
  const minDuration = Number(document.querySelector(`#${prefix}-min-duration`).value)
  const cutoff = windowMinutes > 0 ? Date.now() - windowMinutes * 60 * 1000 : 0
  const filtered = items.filter(trace => {
    const finished = new Date(trace.finishedAt).getTime()
    if (cutoff && (!Number.isFinite(finished) || finished < cutoff)) return false
    if (Number.isFinite(minDuration) && minDuration > 0 && Number(trace.durationMs) < minDuration) return false
    if (!keyword) return true
    const operationText = (trace.operations || []).map(operation =>
      `${operation.name} ${operation.type} ${operation.errorType} ${operation.errorMessage} ${operation.detail}`
    ).join(' ')
    return `${trace.traceId} ${trace.name} ${trace.method} ${rootSource(trace)} ${operationText}`
      .toLowerCase().includes(keyword)
  })
  if (document.querySelector(`#${prefix}-sort`).value === 'slowest') {
    filtered.sort((left, right) => Number(right.durationMs) - Number(left.durationMs))
  } else {
    filtered.sort((left, right) => new Date(right.finishedAt) - new Date(left.finishedAt))
  }
  return filtered
}

function rootSource(trace) {
  return String(trace.sourceType || '').toUpperCase() || 'OTHER'
}

function renderIncidentSources(kind, items) {
  const root = document.querySelector(`#${kind === 'error' ? 'error' : 'slow'}-sources`)
  root.replaceChildren()
  const counts = new Map(incidentSources.map(source => [source.key, 0]))
  for (const item of items) {
    const source = rootSource(item)
    const bucket = ['HTTP', 'JOB', 'KAFKA_CONSUMER'].includes(source) ? source : 'OTHER'
    counts.set(bucket, (counts.get(bucket) || 0) + 1)
  }
  for (const source of incidentSources) {
    const button = document.createElement('button')
    button.type = 'button'
    button.className = `source-filter${selectedIncidentSource[kind] === source.key ? ' active' : ''}`
    button.setAttribute('aria-pressed', String(selectedIncidentSource[kind] === source.key))
    button.textContent = `${source.label} ${source.key === 'ALL' ? items.length : counts.get(source.key) || 0}`
    button.addEventListener('click', () => {
      selectedIncidentSource[kind] = source.key
      renderIncidentRows(kind, kind === 'error' ? errorTraces : slowTraces)
    })
    root.append(button)
  }
}

async function showTrace(trace) {
  document.querySelector('#dialog-title').textContent = trace.traceId || 'Trace 详情'
  document.querySelector('#trace-summary').replaceChildren()
  document.querySelector('#trace-waterfall').replaceChildren()
  document.querySelector('#trace-operations').replaceChildren()
  document.querySelector('#trace-tree').textContent = ''
  document.querySelector('#trace-operation-detail').hidden = true
  setTraceView('timeline')
  dialog.showModal()
  if (!trace.traceId) {
    renderTraceDetails(trace)
    return
  }
  try {
    const latest = await getJson(`/traces/${encodeURIComponent(trace.traceId)}`)
    renderTraceDetails(latest || trace)
  } catch (error) {
    renderTraceDetails(trace)
    setNotice(error.message || '读取 Trace 详情失败')
  }
}

function renderTraceDetails(trace) {
  const source = rootSource(trace)
  const summary = document.querySelector('#trace-summary')
  const status = trace.error ? '错误' : trace.slow ? '慢' : '正常'
  const entries = [
    ['入口', sourceLabels[source] || '其他'],
    ['结果', `${status} · ${trace.status ?? '-'}`],
    ['总耗时', formatNumber(trace.durationMs, ' ms')],
    ['CPU', formatNumber(trace.cpuTimeMs, ' ms')],
    ['分配内存', formatBytes(trace.allocatedBytes)],
    ['线程 ID', formatNumber(trace.threadId)],
    ['完成时间', formatTime(trace.finishedAt)]
  ]
  for (const [label, value] of entries) {
    const item = document.createElement('span')
    item.className = 'trace-summary-item'
    const strong = document.createElement('strong')
    strong.textContent = `${label}：`
    item.append(strong, document.createTextNode(value))
    summary.append(item)
  }
  document.querySelector('#trace-tree').textContent = trace.tree || ''
  renderTraceWaterfall(trace)
  renderTraceOperations(trace)
}

function renderTraceWaterfall(trace) {
  const root = document.querySelector('#trace-waterfall')
  const operations = [...(trace.operations || [])]
  const source = rootSource(trace)
  const rootOperation = operations.filter(operation => operation.type === source)
    .sort((left, right) => Math.abs(new Date(left.finishedAt) - new Date(trace.finishedAt))
      - Math.abs(new Date(right.finishedAt) - new Date(trace.finishedAt)))[0]
  if (rootOperation) operations.splice(operations.indexOf(rootOperation), 1)
  const rootItem = {
    type: source, name: `${trace.method || ''} ${trace.name || ''}`.trim(),
    durationMs: trace.durationMs, finishedAt: trace.finishedAt, threadId: trace.threadId,
    cpuTimeMs: trace.cpuTimeMs, allocatedBytes: trace.allocatedBytes,
    slow: trace.slow, error: trace.error, outcome: trace.error ? 'failure' : 'success',
    detail: '', errorType: '', errorMessage: '', lockInfo: '', stackTrace: ''
  }
  const ordered = [rootItem, ...operations.sort((left, right) =>
    new Date(left.finishedAt) - new Date(right.finishedAt))]
  const duration = Math.max(1, Number(trace.durationMs) || 0)
  const traceEnd = new Date(trace.finishedAt).getTime()
  const traceStart = traceEnd - duration
  for (const operation of ordered) {
    const row = document.createElement('div')
    row.className = 'waterfall-row'
    const name = document.createElement('div')
    name.className = 'waterfall-name'
    const type = document.createElement('strong')
    type.textContent = operation === rootItem ? '入口' : (sourceLabels[operation.type] || operation.type)
    const label = document.createElement('span')
    label.textContent = operation.name || '-'
    name.append(type, label)
    name.title = `${operation.type}: ${operation.name || ''}`
    const track = document.createElement('div')
    track.className = 'waterfall-track'
    const bar = document.createElement('div')
    bar.className = `waterfall-bar${operation.error ? ' is-error' : operation.slow ? ' is-slow' : ''}`
    const operationDuration = Math.max(0, Number(operation.durationMs) || 0)
    const end = operation === rootItem ? traceEnd : new Date(operation.finishedAt).getTime()
    const start = Number.isFinite(end) ? end - operationDuration : traceStart
    const left = operation === rootItem ? 0 : Math.max(0, Math.min(100, (start - traceStart) / duration * 100))
    const right = operation === rootItem ? 100 : Math.max(left + 0.6,
      Math.min(100, (end - traceStart) / duration * 100))
    bar.style.left = `${left}%`
    bar.style.width = `${Math.max(0.6, right - left)}%`
    track.append(bar)
    const cost = document.createElement('div')
    cost.className = 'waterfall-duration'
    cost.textContent = formatNumber(operationDuration, ' ms')
    row.append(name, track, cost)
    row.addEventListener('click', () => showOperationDetail(operation))
    root.append(row)
  }
}

function showOperationDetail(operation) {
  const detail = document.querySelector('#trace-operation-detail')
  const resources = `耗时=${formatNumber(operation.durationMs, ' ms')} CPU=${formatNumber(operation.cpuTimeMs, ' ms')} 分配内存=${formatBytes(operation.allocatedBytes)} 线程=${formatNumber(operation.threadId)}`
  const lines = [`[${operation.type}] ${operation.name || ''} (${operation.outcome || 'success'})`, resources]
  if (operation.detail) lines.push(`明细：${operation.detail}`)
  if (operation.errorType || operation.errorMessage) lines.push(`错误：${operation.errorType || ''} ${operation.errorMessage || ''}`)
  if (operation.lockInfo) lines.push(`锁：${operation.lockInfo}`)
  if (operation.stackTrace) lines.push(`堆栈：\n${operation.stackTrace}`)
  detail.textContent = lines.join('\n')
  detail.hidden = false
}

function renderTraceOperations(trace) {
  const root = document.querySelector('#trace-operations')
  for (const operation of trace.operations || []) {
    const card = document.createElement('article')
    card.className = 'trace-operation-card'
    const heading = document.createElement('div')
    heading.className = 'trace-operation-heading'
    const title = document.createElement('strong')
    title.textContent = `[${operation.type}] ${operation.name || '-'}`
    const badge = document.createElement('span')
    badge.className = `pill ${operation.error ? 'error' : operation.slow ? 'slow' : 'ok'}`
    badge.textContent = operation.error ? '错误' : operation.slow ? '慢' : '正常'
    const meta = document.createElement('span')
    meta.className = 'trace-operation-meta'
    meta.textContent = `${formatNumber(operation.durationMs, ' ms')} · CPU ${formatNumber(operation.cpuTimeMs, ' ms')} · ${formatBytes(operation.allocatedBytes)} · 线程 ${formatNumber(operation.threadId)}`
    heading.append(title, badge, meta)
    card.append(heading)
    const description = [operation.detail, operation.errorType && `错误类型：${operation.errorType}`,
      operation.errorMessage && `错误信息：${operation.errorMessage}`, operation.lockInfo && `锁：${operation.lockInfo}`]
      .filter(Boolean).join('\n')
    if (description) {
      const text = document.createElement('pre')
      text.textContent = description
      card.append(text)
    }
    if (operation.stackTrace) {
      const stack = document.createElement('pre')
      stack.textContent = operation.stackTrace
      card.append(stack)
    }
    root.append(card)
  }
  if (!trace.operations?.length) root.textContent = '没有保存操作明细。'
}

function setTraceView(view) {
  document.querySelectorAll('.trace-view').forEach(button =>
    button.classList.toggle('active', button.dataset.traceView === view))
  document.querySelectorAll('.trace-view-panel').forEach(panel => {
    panel.hidden = panel.id !== (view === 'timeline' ? 'trace-timeline'
      : view === 'tree' ? 'trace-tree-panel' : 'trace-operations-panel')
  })
}

function renderMetrics() {
  const keyword = document.querySelector('#metric-search').value.trim().toLowerCase()
  const rows = metrics.filter(metric => `${metric.name} ${JSON.stringify(metric.tags)}`.toLowerCase().includes(keyword))
  metricRows.replaceChildren()
  document.querySelector('#metric-count').textContent = `${rows.length} / ${metrics.length} 个指标`
  document.querySelector('#metric-empty').hidden = rows.length > 0
  for (const metric of rows) {
    const row = document.createElement('tr')
    const tags = Object.entries(metric.tags || {}).map(([key, value]) => `${key}=${value}`).join(', ') || '-'
    const values = Object.entries(metric.values || {}).map(([key, value]) => `${key}: ${Number(value).toLocaleString(undefined, { maximumFractionDigits: 4 })}`).join(' · ') || '-'
    row.append(cell(metric.name, 'mono'), cell(metric.type), cell(tags), cell(values))
    metricRows.append(row)
  }
}

function renderSeries() {
  const search = document.querySelector('#history-search').value.trim().toLowerCase()
  const list = document.querySelector('#history-series-list')
  const available = metricSeries.filter(series => seriesLabel(series).toLowerCase().includes(search))
  const availableKeys = new Set(metricSeries.map(seriesKey))
  selectedSeriesKeys = selectedSeriesKeys.filter(key => availableKeys.has(key))
  list.replaceChildren()
  document.querySelector('#history-selection-count').textContent = `已选 ${selectedSeriesKeys.length} / ${MAX_SELECTED_SERIES}`
  for (const series of available) {
    const key = seriesKey(series)
    const label = document.createElement('label')
    label.className = 'history-series-option'
    const checkbox = document.createElement('input')
    checkbox.type = 'checkbox'
    checkbox.checked = selectedSeriesKeys.includes(key)
    checkbox.addEventListener('change', () => {
      if (checkbox.checked && selectedSeriesKeys.length >= MAX_SELECTED_SERIES) {
        checkbox.checked = false
        setNotice(`一次最多比较 ${MAX_SELECTED_SERIES} 条指标趋势`)
        return
      }
      selectedSeriesKeys = checkbox.checked
        ? [...selectedSeriesKeys, key]
        : selectedSeriesKeys.filter(item => item !== key)
      document.querySelector('#history-selection-count').textContent = `已选 ${selectedSeriesKeys.length} / ${MAX_SELECTED_SERIES}`
      loadHistory().catch(error => setNotice(error.message || '读取指标趋势失败'))
    })
    const name = document.createElement('span')
    name.className = 'history-series-name mono'
    name.textContent = series.name
    const tags = document.createElement('span')
    tags.className = 'history-series-tags muted'
    tags.textContent = Object.entries(series.tags || {}).map(([tag, value]) => `${tag}=${value}`).join(', ') || '无标签'
    label.append(checkbox, name, tags)
    list.append(label)
  }
  if (!available.length) {
    const empty = document.createElement('div')
    empty.className = 'history-list-empty'
    empty.textContent = metricSeries.length ? '没有匹配的指标系列' : '暂无可用历史指标'
    list.append(empty)
  }
  renderHistoryCharts()
}

function seriesKey(series) {
  return JSON.stringify({ name: series.name, tags: series.tags || {} })
}

function seriesLabel(series) {
  const tags = Object.entries(series.tags || {}).map(([key, value]) => `${key}=${value}`).join(', ')
  return `${series.name} ${tags}`
}

function renderHistoryCharts() {
  const charts = document.querySelector('#history-charts')
  charts.replaceChildren()
  const selected = metricSeries.filter(series => selectedSeriesKeys.includes(seriesKey(series)))
  document.querySelector('#history-empty').hidden = selected.length > 0
  for (const series of selected) {
    const card = document.createElement('article')
    card.className = 'history-chart-card'
    const heading = document.createElement('div')
    heading.className = 'history-chart-heading'
    const title = document.createElement('div')
    title.className = 'history-chart-title'
    const name = document.createElement('strong')
    name.textContent = series.name
    const tags = document.createElement('span')
    tags.className = 'muted'
    tags.textContent = Object.entries(series.tags || {}).map(([key, value]) => `${key}=${value}`).join(', ') || '无标签'
    title.append(name, tags)
    const measurement = document.createElement('select')
    measurement.setAttribute('aria-label', `${series.name} 统计值`)
    for (const item of series.measurements || []) {
      const option = document.createElement('option')
      option.value = item
      option.textContent = item
      measurement.append(option)
    }
    const canvas = document.createElement('canvas')
    canvas.height = 220
    canvas.setAttribute('aria-label', `${series.name} 指标历史趋势`)
    heading.append(title, measurement)
    card.append(heading, canvas)
    charts.append(card)
    measurement.addEventListener('change', () => {
      drawHistory(canvas, series.history || [], measurement.value)
    })
    drawHistory(canvas, series.history || [], measurement.value)
  }
}

function drawHistory(canvas, points, measurement) {
  const context = canvas.getContext('2d')
  const bounds = canvas.getBoundingClientRect()
  const ratio = window.devicePixelRatio || 1
  canvas.width = Math.max(1, bounds.width * ratio)
  canvas.height = Math.max(1, bounds.height * ratio)
  context.scale(ratio, ratio)
  const width = bounds.width
  const height = bounds.height
  context.clearRect(0, 0, width, height)
  const hours = Number(document.querySelector('#history-window').value) || 24
  const end = Date.now()
  const start = end - hours * 60 * 60 * 1000
  const data = points.slice().reverse().map(point => ({
    time: new Date(point.capturedAt), value: Number(point.measurements?.[measurement])
  })).filter(point => Number.isFinite(point.value) && point.time.getTime() >= start && point.time.getTime() <= end)
  if (!data.length) {
    context.fillStyle = '#8290a3'
    context.font = '13px sans-serif'
    context.fillText('该时间范围暂无样本', 16, 32)
    return
  }
  const sampled = data.length > 600
    ? Array.from({ length: 600 }, (_, index) => data[Math.round(index * (data.length - 1) / 599)])
    : data
  const pad = { left: 64, right: 18, top: 20, bottom: 38 }
  const min = Math.min(...sampled.map(point => point.value))
  const max = Math.max(...sampled.map(point => point.value))
  const span = max - min || Math.max(Math.abs(max) * 0.1, 1)
  context.font = '12px sans-serif'
  context.strokeStyle = '#e6ebf2'
  context.fillStyle = '#718096'
  for (let line = 0; line <= 4; line++) {
    const y = pad.top + (height - pad.top - pad.bottom) * line / 4
    context.beginPath(); context.moveTo(pad.left, y); context.lineTo(width - pad.right, y); context.stroke()
    context.fillText((max - span * line / 4).toLocaleString(undefined, { maximumFractionDigits: 3 }), 4, y + 4)
  }
  context.strokeStyle = '#315fe8'
  context.lineWidth = 2
  context.beginPath()
  sampled.forEach((point, index) => {
    const x = pad.left + (width - pad.left - pad.right) * ((point.time.getTime() - start) / (hours * 60 * 60 * 1000))
    const y = pad.top + (height - pad.top - pad.bottom) * (max - point.value) / span
    if (index === 0) context.moveTo(x, y); else context.lineTo(x, y)
  })
  context.stroke()
  context.fillStyle = '#718096'
  context.fillText(formatTime(new Date(start)).slice(0, 16), pad.left, height - 10)
  context.fillText(formatTime(new Date(end)).slice(0, 16), Math.max(pad.left, width - 150), height - 10)
}

async function loadHistory() {
  const version = ++historyRequestVersion
  const selected = metricSeries.filter(series => selectedSeriesKeys.includes(seriesKey(series)))
  for (const series of selected) {
    const query = new URLSearchParams({ name: series.name, tags: JSON.stringify(series.tags || {}), limit: '50000' })
    series.history = await getJson(`/metrics/history?${query}`)
    if (version !== historyRequestVersion) return
  }
  renderHistoryCharts()
}

async function refresh() {
  setNotice('')
  document.querySelector('#refresh').disabled = true
  try {
    const [requestsResult, tracesResult, metricsResult, slowResult, errorsResult, seriesResult, storageResult] = await Promise.allSettled([
      getJson('/request-statistics?source=ALL&limit=500'), getJson('/traces?limit=100'), getJson('/metrics?limit=500'),
      getJson('/incidents?kind=slow&limit=500'), getJson('/incidents?kind=error&limit=500'),
      getJson('/metrics/series'), getJson('/metrics/storage')
    ])
    if (requestsResult.status === 'fulfilled') renderRequestStatistics(requestsResult.value)
    if (tracesResult.status === 'fulfilled') renderTraces(tracesResult.value)
    if (metricsResult.status === 'fulfilled') {
      metrics = metricsResult.value
      renderMetrics()
    }
    if (slowResult.status === 'fulfilled') {
      slowTraces = slowResult.value
      renderIncidentRows('slow', slowTraces)
    }
    if (errorsResult.status === 'fulfilled') {
      errorTraces = errorsResult.value
      renderIncidentRows('error', errorTraces)
    }
    if (seriesResult.status === 'fulfilled') {
      const cachedHistory = new Map(metricSeries.map(series => [seriesKey(series), series.history]))
      metricSeries = seriesResult.value
      for (const series of metricSeries) series.history = cachedHistory.get(seriesKey(series))
      if (!selectedSeriesKeys.length) {
        const defaults = [
          metricSeries.find(series => series.name === 'process.cpu.usage'),
          metricSeries.find(series => series.name === 'jvm.memory.used' && series.tags?.area === 'heap'),
          metricSeries.find(series => series.name === 'hikaricp.connections.active'),
          metricSeries.find(series => series.name === 'http.server.requests' && series.tags?.uri !== 'UNKNOWN')
        ].filter(Boolean)
        selectedSeriesKeys = [...new Set(defaults.map(seriesKey))].slice(0, MAX_SELECTED_SERIES)
      }
      renderSeries()
      if (selectedSeriesKeys.length) loadHistory().catch(error => setNotice(error.message || '读取指标趋势失败'))
    }
    if (storageResult.status === 'fulfilled') {
      const storage = storageResult.value
      document.querySelector('#history-storage').textContent = storage.enabled
        ? `${formatBytes(storage.storageBytes)} / ${formatBytes(storage.maxStorageBytes)} · ${storage.segmentCount} 个系列 · 保留 ${storage.retentionDays} 天`
        : '历史存储已关闭'
    }
    const failures = [requestsResult, tracesResult, metricsResult, slowResult, errorsResult, seriesResult, storageResult]
      .filter(result => result.status === 'rejected')
    if (failures.length) setNotice(failures.map(result => result.reason?.message || '读取失败').join('；'))
  } finally {
    document.querySelector('#refresh').disabled = false
  }
}

document.querySelector('#refresh').addEventListener('click', refresh)
document.querySelectorAll('[data-request-source]').forEach(button => button.addEventListener('click', () => {
  selectedRequestSource = button.dataset.requestSource
  document.querySelectorAll('[data-request-source]').forEach(item => item.classList.toggle('active', item === button))
  renderRequestStatistics(requestStatistics)
}))
document.querySelector('#metric-search').addEventListener('input', renderMetrics)
document.querySelector('#history-search').addEventListener('input', renderSeries)
document.querySelector('#history-window').addEventListener('change', renderHistoryCharts)
document.querySelectorAll('.incident-toolbar input, .incident-toolbar select').forEach(control => {
  control.addEventListener(control.tagName === 'INPUT' ? 'input' : 'change', () => {
    const kind = control.id.startsWith('error-') ? 'error' : 'slow'
    renderIncidentRows(kind, kind === 'error' ? errorTraces : slowTraces)
  })
})
document.querySelectorAll('.trace-view').forEach(button => button.addEventListener('click', () =>
  setTraceView(button.dataset.traceView)))
window.addEventListener('resize', () => {
  renderHistoryCharts()
  const activePanel = document.querySelector('[data-panel]:not([hidden])')?.dataset.panel
  if (activePanel === 'slow') renderIncidentRows('slow', slowTraces)
  if (activePanel === 'errors') renderIncidentRows('error', errorTraces)
})
document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach(item => item.classList.toggle('active', item === tab))
  document.querySelectorAll('[data-panel]').forEach(panel => { panel.hidden = panel.dataset.panel !== tab.dataset.tab })
  if (tab.dataset.tab === 'slow') renderIncidentRows('slow', slowTraces)
  if (tab.dataset.tab === 'errors') renderIncidentRows('error', errorTraces)
  if (tab.dataset.tab === 'requests') renderRequestStatistics(requestStatistics)
}))
refresh()
