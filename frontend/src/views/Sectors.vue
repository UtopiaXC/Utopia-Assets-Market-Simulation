<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { 
  NCard, 
  NGrid, 
  NGridItem, 
  NSpin
} from 'naive-ui'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart } from 'echarts/charts'
import { 
  GridComponent, 
  TooltipComponent, 
  LegendComponent 
} from 'echarts/components'
import { useSimulationStore } from '@/stores/simulation'
import { sectorApi } from '@/services/api'

// Register ECharts components
use([
  CanvasRenderer, 
  LineChart,
  GridComponent, 
  TooltipComponent, 
  LegendComponent
])

const store = useSimulationStore()

// Data
const loading = ref(false)
const sectorData = ref<any>(null)

// Sector colors
const sectorColors = [
  '#2196F3', '#4CAF50', '#FF9800', '#E91E63', '#9C27B0',
  '#00BCD4', '#FFEB3B', '#795548', '#607D8B', '#3F51B5'
]

// Market cap chart
const marketCapOption = computed(() => {
  if (!sectorData.value?.marketCapHistory) return {}
  
  const data = sectorData.value.marketCapHistory
  const sectors = [...new Set(data.map((d: any) => d.sector))]
  const days = [...new Set(data.map((d: any) => d.day))].sort((a, b) => a - b)
  
  return {
    backgroundColor: 'transparent',
    title: { text: 'Sector Market Cap', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { 
      data: sectors, 
      textStyle: { color: '#888' },
      type: 'scroll',
      bottom: 0
    },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '15%' },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: sectors.map((sector, idx) => ({
      name: sector,
      type: 'line',
      data: days.map(day => {
        const point = data.find((d: any) => d.day === day && d.sector === sector)
        return point?.value || 0
      }),
      itemStyle: { color: sectorColors[idx % sectorColors.length] }
    }))
  }
})

// PE ratio chart
const peOption = computed(() => {
  if (!sectorData.value?.peHistory) return {}
  
  const data = sectorData.value.peHistory
  const sectors = [...new Set(data.map((d: any) => d.sector))]
  const days = [...new Set(data.map((d: any) => d.day))].sort((a, b) => a - b)
  
  return {
    backgroundColor: 'transparent',
    title: { text: 'Sector Avg PE', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { 
      data: sectors, 
      textStyle: { color: '#888' },
      type: 'scroll',
      bottom: 0
    },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '15%' },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: sectors.map((sector, idx) => ({
      name: sector,
      type: 'line',
      data: days.map(day => {
        const point = data.find((d: any) => d.day === day && d.sector === sector)
        return point?.value || 0
      }),
      itemStyle: { color: sectorColors[idx % sectorColors.length] }
    }))
  }
})

async function fetchData() {
  if (!store.currentSimulation) return
  
  loading.value = true
  try {
    const data = await sectorApi.getStats(store.currentSimulation)
    sectorData.value = data
  } catch (error) {
    console.error('Failed to fetch sector data:', error)
  } finally {
    loading.value = false
  }
}

// Watch for changes
watch(() => store.currentSimulation, () => {
  if (store.currentSimulation) {
    fetchData()
  }
}, { immediate: true })

onMounted(() => {
  if (store.currentSimulation) {
    fetchData()
  }
})
</script>

<template>
  <div>
    <h1 style="margin-bottom: 24px">Sector Analysis</h1>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header to view sector data.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <NGrid :cols="2" :x-gap="16" :y-gap="16">
          <NGridItem>
            <NCard>
              <VChart :option="marketCapOption" style="height: 400px" autoresize />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <VChart :option="peOption" style="height: 400px" autoresize />
            </NCard>
          </NGridItem>
        </NGrid>
      </NSpin>
    </template>
  </div>
</template>
