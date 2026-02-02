<script setup lang="ts">
import { ref, watch, onMounted, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { 
  NCard, 
  NGrid, 
  NGridItem, 
  NDataTable, 
  NStatistic,
  NSpin,
  NButton,
  NBreadcrumb,
  NBreadcrumbItem,
  NTag
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
import { traderApi } from '@/services/api'

// Register ECharts components
use([
  CanvasRenderer, 
  LineChart,
  GridComponent, 
  TooltipComponent, 
  LegendComponent
])

const route = useRoute()
const router = useRouter()
const store = useSimulationStore()

const traderId = computed(() => parseInt(route.params.traderId as string))

// Data
const loading = ref(false)
const traderData = ref<any>(null)

// Asset composition chart
const assetOption = computed(() => {
  if (!traderData.value?.history) return {}
  
  const data = traderData.value.history
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { 
      data: ['Savings', 'Stocks', 'Cash', 'Frozen'], 
      textStyle: { color: '#888' } 
    },
    grid: { left: '5%', right: '5%', top: '15%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: [
      {
        name: 'Savings',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.privateSavings),
        itemStyle: { color: '#4CAF50' }
      },
      {
        name: 'Stocks',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.stockValue),
        itemStyle: { color: '#2196F3' }
      },
      {
        name: 'Cash',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.cash),
        itemStyle: { color: '#FFD700' }
      },
      {
        name: 'Frozen',
        type: 'line',
        stack: 'total',
        areaStyle: {},
        data: data.map((d: any) => d.reservedCash),
        itemStyle: { color: '#FF9800' }
      }
    ]
  }
})

// Risk tolerance chart
const riskOption = computed(() => {
  if (!traderData.value?.history) return {}
  
  const data = traderData.value.history
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: { 
      type: 'value', 
      min: 0, 
      max: 1,
      splitLine: { lineStyle: { color: '#333' } } 
    },
    series: [{
      type: 'line',
      data: data.map((d: any) => d.riskTolerance),
      itemStyle: { color: '#E91E63' }
    }]
  }
})

// Holdings table columns
const holdingsColumns = [
  { title: 'Stock ID', key: 'stockId', width: 120 },
  { 
    title: 'Quantity', 
    key: 'quantity', 
    width: 120,
    render: (row: any) => row.quantity?.toLocaleString()
  },
  { 
    title: 'Price', 
    key: 'price', 
    width: 100,
    render: (row: any) => row.price?.toFixed(2)
  },
  { 
    title: 'Value', 
    key: 'marketValue', 
    width: 120,
    render: (row: any) => formatLargeNumber(row.marketValue)
  },
  {
    title: 'Action',
    key: 'action',
    width: 80,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        quaternary: true,
        onClick: () => router.push({ name: 'stockDetail', params: { stockId: row.stockId } })
      }, { default: () => '🔍' })
    }
  }
]

function formatLargeNumber(n: number): string {
  if (n === null || n === undefined) return '-'
  if (Math.abs(n) > 1e12) return (n / 1e12).toFixed(2) + ' T'
  if (Math.abs(n) > 1e9) return (n / 1e9).toFixed(2) + ' B'
  if (Math.abs(n) > 1e6) return (n / 1e6).toFixed(2) + ' M'
  if (Math.abs(n) > 1e3) return (n / 1e3).toFixed(2) + ' K'
  return n.toFixed(2)
}

const dailyPnlClass = computed(() => {
  const pnl = traderData.value?.currentMetrics?.dailyPnl || 0
  if (pnl > 0) return 'positive'
  if (pnl < 0) return 'negative'
  return ''
})

const dailyPnlText = computed(() => {
  const pnl = traderData.value?.currentMetrics?.dailyPnl
  if (pnl === null || pnl === undefined) return '-'
  const prefix = pnl > 0 ? '+' : ''
  return prefix + formatLargeNumber(pnl)
})

async function fetchData() {
  if (!store.currentSimulation || !traderId.value) return
  
  loading.value = true
  try {
    const data = await traderApi.getDetail(store.currentSimulation, traderId.value, store.currentDay)
    traderData.value = data
  } catch (error) {
    console.error('Failed to fetch trader detail:', error)
  } finally {
    loading.value = false
  }
}

// Watch for changes
watch(() => [store.currentSimulation, store.currentDay, traderId.value], () => {
  if (store.currentSimulation && traderId.value) {
    fetchData()
  }
}, { immediate: true })

onMounted(() => {
  if (store.currentSimulation && traderId.value) {
    fetchData()
  }
})
</script>

<template>
  <div>
    <!-- Breadcrumb -->
    <NBreadcrumb style="margin-bottom: 16px">
      <NBreadcrumbItem @click="router.push({ name: 'traders' })">Traders</NBreadcrumbItem>
      <NBreadcrumbItem>{{ traderId }}</NBreadcrumbItem>
    </NBreadcrumb>
    
    <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px">
      <h1 style="margin: 0">👤 Trader {{ traderId }} Deep Dive</h1>
      <NTag :type="traderData?.isActive ? 'success' : 'error'" size="large">
        {{ traderData?.traderType || '-' }}
      </NTag>
    </div>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <!-- Metrics Cards -->
        <NGrid :cols="6" :x-gap="16" :y-gap="16" style="margin-bottom: 24px">
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Total Assets" 
                :value="formatLargeNumber(traderData?.currentMetrics?.totalAssets)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic label="Daily PnL">
                <span :class="dailyPnlClass">{{ dailyPnlText }}</span>
              </NStatistic>
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Savings" 
                :value="formatLargeNumber(traderData?.currentMetrics?.privateSavings)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Cash" 
                :value="formatLargeNumber((traderData?.currentMetrics?.cash || 0) + (traderData?.currentMetrics?.reservedCash || 0))"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Stocks" 
                :value="formatLargeNumber(traderData?.currentMetrics?.stockValue)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Risk Tolerance" 
                :value="traderData?.currentMetrics?.riskTolerance?.toFixed(2) || '-'"
              />
            </NCard>
          </NGridItem>
        </NGrid>
        
        <NGrid :cols="2" :x-gap="16" :y-gap="16">
          <!-- Charts Column -->
          <NGridItem>
            <NCard title="Asset Composition History" style="margin-bottom: 16px">
              <VChart :option="assetOption" style="height: 300px" autoresize />
            </NCard>
            <NCard title="Risk Tolerance">
              <VChart :option="riskOption" style="height: 200px" autoresize />
            </NCard>
          </NGridItem>
          
          <!-- Holdings Column -->
          <NGridItem>
            <NCard title="Current Holdings">
              <NDataTable
                :columns="holdingsColumns"
                :data="traderData?.holdings || []"
                :bordered="false"
                size="small"
                :max-height="540"
              />
            </NCard>
          </NGridItem>
        </NGrid>
      </NSpin>
    </template>
  </div>
</template>

<style scoped>
.positive { color: #4CAF50; }
.negative { color: #F44336; }
</style>
