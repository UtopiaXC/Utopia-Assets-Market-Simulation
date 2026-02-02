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
import { macroApi } from '@/services/api'

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
const macroData = ref<any>(null)

// Population chart
const populationOption = computed(() => {
  if (!macroData.value?.populationHistory) return {}
  
  const data = macroData.value.populationHistory
  return {
    backgroundColor: 'transparent',
    title: { text: 'Active Agents', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    grid: { left: '5%', right: '5%', top: '15%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: [{
      type: 'line',
      data: data.map((d: any) => d.count),
      itemStyle: { color: '#2196F3' },
      areaStyle: { color: 'rgba(33, 150, 243, 0.2)' }
    }]
  }
})

// Wealth structure chart
const wealthOption = computed(() => {
  if (!macroData.value?.wealthHistory) return {}
  
  const data = macroData.value.wealthHistory
  return {
    backgroundColor: 'transparent',
    title: { text: 'Macro Wealth Flow', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { 
      data: ['Social Pool', 'Savings', 'Market Liq'], 
      textStyle: { color: '#888' } 
    },
    grid: { left: '5%', right: '5%', top: '18%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: [
      {
        name: 'Social Pool',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.socialWealthPool),
        itemStyle: { color: '#9E9E9E' }
      },
      {
        name: 'Savings',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.savings),
        itemStyle: { color: '#4CAF50' }
      },
      {
        name: 'Market Liq',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.liquidity),
        itemStyle: { color: '#2196F3' }
      }
    ]
  }
})

// Agent type assets chart
const agentAssetsOption = computed(() => {
  if (!macroData.value?.agentTypeAssets) return {}
  
  const data = macroData.value.agentTypeAssets
  const types = [...new Set(data.map((d: any) => d.traderType))]
  const days = [...new Set(data.map((d: any) => d.day))].sort((a, b) => a - b)
  
  const colors = ['#2196F3', '#4CAF50', '#FF9800', '#E91E63', '#9C27B0']
  
  return {
    backgroundColor: 'transparent',
    title: { text: 'Total Assets by Agent Type', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { data: types, textStyle: { color: '#888' } },
    grid: { left: '5%', right: '5%', top: '18%', bottom: '10%' },
    xAxis: { type: 'category', data: days },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: types.map((type, idx) => ({
      name: type,
      type: 'line',
      data: days.map(day => {
        const point = data.find((d: any) => d.day === day && d.traderType === type)
        return point?.value || 0
      }),
      itemStyle: { color: colors[idx % colors.length] }
    }))
  }
})

// Agent type risk chart
const agentRiskOption = computed(() => {
  if (!macroData.value?.agentTypeRisk) return {}
  
  const data = macroData.value.agentTypeRisk
  const types = [...new Set(data.map((d: any) => d.traderType))]
  const days = [...new Set(data.map((d: any) => d.day))].sort((a, b) => a - b)
  
  const colors = ['#2196F3', '#4CAF50', '#FF9800', '#E91E63', '#9C27B0']
  
  return {
    backgroundColor: 'transparent',
    title: { text: 'Avg Risk Tolerance', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { data: types, textStyle: { color: '#888' } },
    grid: { left: '5%', right: '5%', top: '18%', bottom: '10%' },
    xAxis: { type: 'category', data: days },
    yAxis: { 
      type: 'value', 
      min: 0, 
      max: 1,
      splitLine: { lineStyle: { color: '#333' } } 
    },
    series: types.map((type, idx) => ({
      name: type,
      type: 'line',
      data: days.map(day => {
        const point = data.find((d: any) => d.day === day && d.traderType === type)
        return point?.value || 0
      }),
      itemStyle: { color: colors[idx % colors.length] }
    }))
  }
})

async function fetchData() {
  if (!store.currentSimulation) return
  
  loading.value = true
  try {
    const data = await macroApi.getStats(store.currentSimulation)
    macroData.value = data
  } catch (error) {
    console.error('Failed to fetch macro data:', error)
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
    <h1 style="margin-bottom: 24px">Macro Statistics</h1>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header to view macro data.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <NGrid :cols="2" :x-gap="16" :y-gap="16">
          <NGridItem>
            <NCard>
              <VChart :option="populationOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <VChart :option="wealthOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <VChart :option="agentAssetsOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <VChart :option="agentRiskOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
        </NGrid>
      </NSpin>
    </template>
  </div>
</template>
