const api = '/api/easy-observability'
const traceRows = document.querySelector('#trace-rows')
const metricRows = document.querySelector('#metric-rows')
const notice = document.querySelector('#notice')
const dialog = document.querySelector('#trace-dialog')
let metrics = []
let traces = []
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

function renderIncidentRows(kind, items) {
  const prefix = kind === 'error' ? 'error' : 'slow'
  const body = document.querySelector(`#${prefix}-rows`)
  const selectedSource = selectedIncidentSource[kind]
  const visibleItems = selectedSource === 'ALL'
    ? items
    : selectedSource === 'OTHER'
      ? items.filter(item => !['HTTP', 'JOB', 'KAFKA_CONSUMER'].includes(rootSource(item)))
      : items.filter(item => rootSource(item) === selectedSource)
  renderIncidentSources(kind, items)
  body.replaceChildren()
  document.querySelector(`#${prefix}-count`).textContent = selectedSource === 'ALL'
    ? `${items.length} 条` : `${visibleItems.length} / ${items.length} 条`
  const empty = document.querySelector(`#${prefix}-empty`)
  empty.hidden = visibleItems.length > 0
  empty.textContent = items.length ? '当前入口暂无匹配记录。' : kind === 'error' ? '暂时没有错误 Trace。' : '暂时没有慢 Trace。'
  for (const trace of visibleItems) {
    const row = document.createElement('tr')
    row.className = 'clickable'
    const source = rootSource(trace)
    const rootOperation = (trace.operations || []).find(operation => operation.type === source)
    const cells = [cell(sourceLabels[source] || '其他'), cell(`${trace.method || ''} ${trace.name || ''}`.trim()),
      cell(String(trace.status)), cell(`${trace.durationMs} ms`), cell(formatNumber(trace.cpuTimeMs, ' ms')),
      cell(formatBytes(trace.allocatedBytes)), cell(formatNumber(trace.threadId))]
    if (kind === 'error') {
      cells.push(cell(rootOperation?.errorType || rootOperation?.errorMessage || '执行失败'))
    }
    cells.push(cell(trace.traceId, 'mono'), cell(formatTime(trace.finishedAt), 'muted'))
    row.append(...cells)
    row.addEventListener('click', () => showTrace({ traceId: trace.traceId }))
    body.append(row)
  }
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
  document.querySelector('#trace-tree').textContent = trace.tree || ''
  dialog.showModal()
  if (!trace.traceId) return
  try {
    const latest = await getJson(`/traces/${encodeURIComponent(trace.traceId)}`)
    const operations = latest?.operations || []
    const operationDetails = operations.map(operation => {
      const resources = `耗时=${formatNumber(operation.durationMs, ' ms')} CPU=${formatNumber(operation.cpuTimeMs, ' ms')} 分配内存=${formatBytes(operation.allocatedBytes)} 线程=${formatNumber(operation.threadId)}`
      const detail = operation.detail ? `\n    ${operation.detail}` : ''
      const failure = operation.errorMessage ? `\n    错误: ${operation.errorMessage}` : ''
      const locks = operation.lockInfo ? `\n    锁: ${operation.lockInfo}` : ''
      const stack = operation.stackTrace ? `\n${operation.stackTrace}` : ''
      return `[${operation.type}] ${operation.name} (${operation.outcome}) ${resources}${detail}${failure}${locks}${stack}`
    }).join('\n\n')
    const tree = latest?.tree || ''
    document.querySelector('#trace-tree').textContent = [tree, operationDetails && `操作资源明细\n${operationDetails}`].filter(Boolean).join('\n\n')
  } catch (error) {
    setNotice(error.message || '读取 Trace 详情失败')
  }
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
    const [tracesResult, metricsResult, slowResult, errorsResult, seriesResult, storageResult] = await Promise.allSettled([
      getJson('/traces?limit=100'), getJson('/metrics?limit=500'),
      getJson('/incidents?kind=slow&limit=500'), getJson('/incidents?kind=error&limit=500'),
      getJson('/metrics/series'), getJson('/metrics/storage')
    ])
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
      renderSeries()
    }
    if (storageResult.status === 'fulfilled') {
      const storage = storageResult.value
      document.querySelector('#history-storage').textContent = storage.enabled
        ? `${formatBytes(storage.storageBytes)} / ${formatBytes(storage.maxStorageBytes)} · ${storage.segmentCount} 个系列 · 保留 ${storage.retentionDays} 天`
        : '历史存储已关闭'
    }
    const failures = [tracesResult, metricsResult, slowResult, errorsResult, seriesResult, storageResult].filter(result => result.status === 'rejected')
    if (failures.length) setNotice(failures.map(result => result.reason?.message || '读取失败').join('；'))
  } finally {
    document.querySelector('#refresh').disabled = false
  }
}

document.querySelector('#refresh').addEventListener('click', refresh)
document.querySelector('#metric-search').addEventListener('input', renderMetrics)
document.querySelector('#history-search').addEventListener('input', renderSeries)
document.querySelector('#history-window').addEventListener('change', renderHistoryCharts)
window.addEventListener('resize', renderHistoryCharts)
document.querySelectorAll('.tab').forEach(tab => tab.addEventListener('click', () => {
  document.querySelectorAll('.tab').forEach(item => item.classList.toggle('active', item === tab))
  document.querySelectorAll('[data-panel]').forEach(panel => { panel.hidden = panel.dataset.panel !== tab.dataset.tab })
}))
refresh()
