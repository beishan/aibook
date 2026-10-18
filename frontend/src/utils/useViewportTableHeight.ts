import { onBeforeUnmount, onMounted, onUpdated, ref, watch } from 'vue'

/** Keep a table's header and pagination within the viewport as surrounding controls resize. */
export function useViewportTableHeight() {
  const tablePanelRef = ref<HTMLElement>()
  const tableMaxHeight = ref<number>(Math.max(1, window.innerHeight))
  let observer: ResizeObserver | undefined
  let frame = 0

  function updateHeight() {
    frame = 0
    const panel = tablePanelRef.value
    const table = panel?.querySelector<HTMLElement>(':scope > .data-table')
    if (!panel || !table) return

    const viewport = window.visualViewport
    const viewportHeight = viewport?.height ?? window.innerHeight
    // Use the unscrolled position so scrolling the page never expands the table.
    let top = table.getBoundingClientRect().top + window.scrollY
    let bottomSpacing = 16
    for (let parent = panel.parentElement; parent; parent = parent.parentElement) {
      if (parent !== document.body && parent !== document.documentElement) {
        top += parent.scrollTop
      }
      bottomSpacing = Math.max(bottomSpacing, parseFloat(getComputedStyle(parent).paddingBottom) || 0)
    }

    const pagination = panel.querySelector<HTMLElement>(':scope > .discovery-pagination, :scope > .list-pagination')
    const panelStyle = getComputedStyle(panel)
    const reservedHeight = (pagination?.getBoundingClientRect().height ?? 0)
      + (parseFloat(panelStyle.paddingBottom) || 0) + bottomSpacing
    // On very short screens the controls can consume the viewport; retain a small scrollable body.
    const minimumHeight = Math.min(120, viewportHeight)
    tableMaxHeight.value = Math.floor(Math.min(
      viewportHeight,
      Math.max(minimumHeight, viewportHeight - top - reservedHeight),
    ))
  }

  function scheduleUpdate() {
    if (!frame) frame = window.requestAnimationFrame(updateHeight)
  }

  function observePanel() {
    observer?.disconnect()
    const panel = tablePanelRef.value
    if (panel && observer) {
      observer.observe(panel)
      for (const child of panel.children) {
        if (!child.classList.contains('data-table')) observer.observe(child)
      }
    }
    scheduleUpdate()
  }

  watch(tablePanelRef, observePanel, { flush: 'post' })
  onMounted(() => {
    observer = new ResizeObserver(scheduleUpdate)
    observePanel()
    window.addEventListener('resize', scheduleUpdate)
    window.visualViewport?.addEventListener('resize', scheduleUpdate)
  })
  onUpdated(observePanel)
  onBeforeUnmount(() => {
    observer?.disconnect()
    window.cancelAnimationFrame(frame)
    window.removeEventListener('resize', scheduleUpdate)
    window.visualViewport?.removeEventListener('resize', scheduleUpdate)
  })

  return { tablePanelRef, tableMaxHeight }
}
