<template>
  <div ref="containerRef" class="easy-chart" :aria-label="chartLabel" role="img"></div>
</template>

<script setup lang="ts">
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent
} from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

use([
  BarChart,
  LineChart,
  PieChart,
  GridComponent,
  LegendComponent,
  TitleComponent,
  TooltipComponent,
  TransformComponent,
  CanvasRenderer
])

const props = defineProps<{
  option: EChartsCoreOption
  chartLabel: string
}>()

const containerRef = ref<HTMLElement>()
let chart: ECharts | null = null
let resizeObserver: ResizeObserver | null = null

onMounted(async () => {
  await nextTick()
  if (!containerRef.value) {
    return
  }
  chart = init(containerRef.value)
  chart.setOption(props.option, true)
  resizeObserver = new ResizeObserver(() => chart?.resize())
  resizeObserver.observe(containerRef.value)
})

watch(
  () => props.option,
  (option) => {
    chart?.setOption(option, true)
  },
  { deep: true }
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
  chart = null
  resizeObserver = null
})
</script>

<style scoped>
.easy-chart {
  width: 100%;
  min-height: 280px;
}
</style>
