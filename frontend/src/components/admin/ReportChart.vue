<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = defineProps({ option: { type: Object, required: true } })
const element = ref(null)
let chart
let resizeObserver

function render() {
  if (!element.value) return
  chart ??= echarts.init(element.value)
  chart.setOption(props.option, true)
}

function getDataURL() {
  return chart?.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: '#FFFFFF' })
}

defineExpose({ getDataURL })

onMounted(async () => {
  await nextTick()
  render()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => chart?.resize())
    resizeObserver.observe(element.value)
  }
})

watch(() => props.option, render, { deep: true })

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<template>
  <div ref="element" class="report-chart" aria-label="报表图表"></div>
</template>

<style scoped>
.report-chart { width: 100%; height: 280px; }
</style>
