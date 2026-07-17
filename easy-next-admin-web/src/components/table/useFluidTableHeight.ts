import { nextTick, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'

interface FluidTableHeightOptions {
  minHeight?: number
  bottomGap?: number
  tableSelector?: string
  footerSelector?: string | false
}

const DEFAULT_MIN_HEIGHT = 280
const DEFAULT_BOTTOM_GAP = 16
const DEFAULT_FOOTER_HEIGHT = 56

export function useFluidTableHeight(panelRef: Ref<HTMLElement | undefined>, options: FluidTableHeightOptions = {}) {
  const tableHeight = ref(options.minHeight ?? DEFAULT_MIN_HEIGHT)
  let resizeObserver: ResizeObserver | undefined
  let frameId = 0

  function calculateTableHeight() {
    const panel = panelRef.value
    if (!panel || typeof window === 'undefined') return

    const table = panel.querySelector<HTMLElement>(options.tableSelector ?? '.admin-table')
    if (!table) return

    const footer = options.footerSelector === false
      ? undefined
      : panel.querySelector<HTMLElement>(options.footerSelector ?? '.table-footer')
    const footerHeight = options.footerSelector === false
      ? 0
      : footer?.getBoundingClientRect().height ?? DEFAULT_FOOTER_HEIGHT
    const viewportHeight = window.innerHeight || document.documentElement.clientHeight
    const bottomGap = options.bottomGap ?? DEFAULT_BOTTOM_GAP
    const tableTop = table.getBoundingClientRect().top
    const availableHeight = viewportHeight - bottomGap - tableTop - footerHeight - 2

    tableHeight.value = Math.max(options.minHeight ?? DEFAULT_MIN_HEIGHT, Math.floor(availableHeight))
  }

  function updateTableHeight() {
    void nextTick(() => {
      if (typeof window === 'undefined') return
      if (frameId) {
        window.cancelAnimationFrame(frameId)
      }
      frameId = window.requestAnimationFrame(() => {
        frameId = 0
        calculateTableHeight()
      })
    })
  }

  onMounted(() => {
    updateTableHeight()
    window.addEventListener('resize', updateTableHeight)
    if ('ResizeObserver' in window && panelRef.value) {
      resizeObserver = new ResizeObserver(updateTableHeight)
      resizeObserver.observe(panelRef.value)
    }
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', updateTableHeight)
    resizeObserver?.disconnect()
    if (frameId) {
      window.cancelAnimationFrame(frameId)
    }
  })

  return {
    tableHeight,
    updateTableHeight
  }
}
