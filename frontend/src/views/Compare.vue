<script setup lang="ts">
import { ref, computed } from 'vue'
import { 
  NCard, 
  NGrid, 
  NGridItem, 
  NSelect,
  NButton,
  NEmpty,
  NSpin,
  NSpace
} from 'naive-ui'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, BarChart } from 'echarts/charts'
import { 
  GridComponent, 
  TooltipComponent, 
  LegendComponent 
} from 'echarts/components'
import { useSimulationStore } from '@/stores/simulation'
import { marketApi, macroApi } from '@/services/api'
import { useCancellableFetch } from '@/composables/useCancellableFetch'

// Register ECharts components
use([
  CanvasRenderer, 
  LineChart,
  BarChart,
  GridComponent, 
  TooltipComponent, 
  LegendComponent
])

const store = useSimulationStore()

// Selected simulations
const selectedSimulations = ref<string[]>([])
const { loading, fetch: fetchComparison } = useCancellableFetch<any>()
const comparisonData = ref<any>({})

// Simulation options
const simulationOptions = computed(() => 
  store.simulations.map(s => ({ label: s.name, value: s.name }))
)

// Comparison colors
const compareColors = ['#2196F3', '#E91E63', '#4CAF50', '#FF9800', '#9C27B0', '#00BCD4', '#795548']

// Helper function for chart base config
function getBaseChartConfig(title: string) {
  return {
    backgroundColor: 'transparent',
    title: { text: title, textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    grid: { left: '8%', right: '5%', top: '18%', bottom: '12%' },
    xAxis: { type: 'category' as const, axisLabel: { color: '#888' } },
    yAxis: { type: 'value' as const, splitLine: { lineStyle: { color: '#333' } }, axisLabel: { color: '#888' } }
  }
}

// 1. Market Index Comparison
const indexCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.klineData
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => d.close),
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Market Index'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 2. Active Agents Comparison
const populationCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.populationHistory
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => d.count),
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Active Agents'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 3. Total Market Cap Comparison
const marketCapCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.klineData
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => (d.totalMarketCap || 0) / 1e9), // In billions
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Total Market Cap (Billion)'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 4. Trading Volume Comparison
const volumeCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.klineData
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => (d.volume || 0) / 1e6), // In millions
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true,
        areaStyle: { opacity: 0.1 }
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Trading Volume (Million Shares)'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 5. Turnover Rate Comparison
const turnoverCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.klineData
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => ((d.turnoverRate || 0) * 100).toFixed(2)), // In percentage
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Turnover Rate (%)'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 6. Social Wealth Pool Comparison
const wealthCompareOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const series: any[] = []
  let maxDays = 0
  
  selectedSimulations.value.forEach((sim, idx) => {
    const data = comparisonData.value[sim]?.wealthHistory
    if (data) {
      maxDays = Math.max(maxDays, data.length)
      series.push({
        name: sim.replace('.db', '').split('_').slice(0, 2).join('_'),
        type: 'line',
        data: data.map((d: any) => (d.socialWealth || 0) / 1e9), // In billions
        itemStyle: { color: compareColors[idx % compareColors.length] },
        smooth: true
      })
    }
  })
  
  return {
    ...getBaseChartConfig('Social Wealth Pool (Billion)'),
    legend: { data: series.map(s => s.name), textStyle: { color: '#888' } },
    xAxis: { type: 'category', data: Array.from({ length: maxDays }, (_, i) => i + 1), axisLabel: { color: '#888' } },
    series
  }
})

// 7. Final Statistics Bar Chart
const finalStatsOption = computed(() => {
  if (Object.keys(comparisonData.value).length === 0) return {}
  
  const simNames: string[] = []
  const finalIndex: number[] = []
  const finalAgents: number[] = []
  const finalMarketCap: number[] = []
  
  selectedSimulations.value.forEach((sim) => {
    const kline = comparisonData.value[sim]?.klineData
    const pop = comparisonData.value[sim]?.populationHistory
    
    if (kline && kline.length > 0) {
      simNames.push(sim.replace('.db', '').split('_').slice(0, 2).join('_'))
      const lastKline = kline[kline.length - 1]
      finalIndex.push(lastKline.close || 0)
      finalMarketCap.push((lastKline.totalMarketCap || 0) / 1e9)
    }
    if (pop && pop.length > 0) {
      finalAgents.push(pop[pop.length - 1].count || 0)
    }
  })
  
  return {
    backgroundColor: 'transparent',
    title: { text: 'Final Statistics Comparison', textStyle: { color: '#888' } },
    tooltip: { trigger: 'axis' },
    legend: { data: ['Final Index', 'Final Agents', 'Final Market Cap (B)'], textStyle: { color: '#888' } },
    grid: { left: '8%', right: '5%', top: '18%', bottom: '15%' },
    xAxis: { type: 'category', data: simNames, axisLabel: { color: '#888', rotate: 15 } },
    yAxis: [
      { type: 'value', name: 'Index / Agents', splitLine: { lineStyle: { color: '#333' } }, axisLabel: { color: '#888' } },
      { type: 'value', name: 'Market Cap (B)', splitLine: { show: false }, axisLabel: { color: '#888' } }
    ],
    series: [
      { name: 'Final Index', type: 'bar', data: finalIndex, itemStyle: { color: '#2196F3' } },
      { name: 'Final Agents', type: 'bar', data: finalAgents, itemStyle: { color: '#4CAF50' } },
      { name: 'Final Market Cap (B)', type: 'bar', yAxisIndex: 1, data: finalMarketCap, itemStyle: { color: '#FF9800' } }
    ]
  }
})

async function loadComparison() {
  if (selectedSimulations.value.length === 0) return
  
  await fetchComparison(async (signal) => {
    const newData: any = {}
    for (const sim of selectedSimulations.value) {
      const [klineData, populationData, wealthData] = await Promise.all([
        marketApi.getKlineData(sim, signal),
        macroApi.getPopulation(sim, signal),
        macroApi.getWealth(sim, signal)
      ])
      
      newData[sim] = { 
        klineData: klineData.klineData || [],
        populationHistory: populationData.population || [],
        wealthHistory: wealthData.wealth || []
      }
    }
    comparisonData.value = newData
    return newData
  })
}
</script>

<template>
  <div>
    <h1 style="margin-bottom: 24px">Compare Simulations</h1>
    
    <NCard title="Select Simulations to Compare" style="margin-bottom: 24px">
      <NSpace vertical>
        <NSpace>
          <NSelect
            v-model:value="selectedSimulations"
            :options="simulationOptions"
            multiple
            placeholder="Select up to 5 simulations..."
            style="width: 500px"
            :max-tag-count="3"
          />
          <NButton 
            type="primary" 
            @click="loadComparison"
            :loading="loading"
            :disabled="selectedSimulations.length === 0"
          >
            Compare
          </NButton>
        </NSpace>
        <p style="opacity: 0.6; margin: 0">
          Select multiple simulation results to compare their market performance and agent statistics.
        </p>
      </NSpace>
    </NCard>
    
    <template v-if="Object.keys(comparisonData).length === 0 && !loading">
      <NEmpty description="Select simulations and click Compare to view comparison charts" />
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <NGrid :cols="2" :x-gap="16" :y-gap="16">
          <!-- Market Index -->
          <NGridItem>
            <NCard title="Market Index">
              <VChart :option="indexCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Active Agents -->
          <NGridItem>
            <NCard title="Active Agents">
              <VChart :option="populationCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Total Market Cap -->
          <NGridItem>
            <NCard title="Total Market Cap">
              <VChart :option="marketCapCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Trading Volume -->
          <NGridItem>
            <NCard title="Trading Volume">
              <VChart :option="volumeCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Turnover Rate -->
          <NGridItem>
            <NCard title="Turnover Rate">
              <VChart :option="turnoverCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Social Wealth -->
          <NGridItem>
            <NCard title="Social Wealth">
              <VChart :option="wealthCompareOption" style="height: 300px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Final Statistics Bar Chart (full width) -->
          <NGridItem :span="2">
            <NCard title="Final Statistics">
              <VChart :option="finalStatsOption" style="height: 350px" autoresize />
            </NCard>
          </NGridItem>
        </NGrid>
      </NSpin>
    </template>
  </div>
</template>
