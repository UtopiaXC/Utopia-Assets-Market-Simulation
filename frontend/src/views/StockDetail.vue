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
  NTabs,
  NTabPane,
  NBreadcrumb,
  NBreadcrumbItem
} from 'naive-ui'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { CandlestickChart, LineChart, BarChart } from 'echarts/charts'
import { 
  GridComponent, 
  TooltipComponent, 
  LegendComponent,
  DataZoomComponent 
} from 'echarts/components'
import { useSimulationStore } from '@/stores/simulation'
import { stockApi } from '@/services/api'
import { useCancellableFetch } from '@/composables/useCancellableFetch'

// Register ECharts components
use([
  CanvasRenderer, 
  CandlestickChart, 
  LineChart, 
  BarChart,
  GridComponent, 
  TooltipComponent, 
  LegendComponent,
  DataZoomComponent
])

const route = useRoute()
const router = useRouter()
const store = useSimulationStore()

const stockId = computed(() => route.params.stockId as string)

// Data
const { loading, data: stockData, fetch: fetchStock } = useCancellableFetch<any>()

// K-line chart option
const klineOption = computed(() => {
  if (!stockData.value?.history) return {}
  
  const data = stockData.value.history
  const days = data.map((d: any) => d.day)
  const ohlc = data.map((d: any) => [d.open, d.close, d.low, d.high])
  
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis', axisPointer: { type: 'cross' } },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '15%' },
    xAxis: { type: 'category', data: days },
    yAxis: { scale: true, splitLine: { lineStyle: { color: '#333' } } },
    dataZoom: [
      { type: 'inside', start: 0, end: 100 },
      { type: 'slider', bottom: 0 }
    ],
    series: [{
      type: 'candlestick',
      data: ohlc,
      itemStyle: {
        color: '#26a69a',
        color0: '#ef5350',
        borderColor: '#26a69a',
        borderColor0: '#ef5350'
      }
    }]
  }
})

// Valuation chart (PE/PB)
const valuationOption = computed(() => {
  if (!stockData.value?.history) return {}
  
  const data = stockData.value.history
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['PE (TTM)', 'PB Ratio'], textStyle: { color: '#888' } },
    grid: { left: '5%', right: '10%', top: '15%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: [
      { type: 'value', name: 'PE', splitLine: { lineStyle: { color: '#333' } } },
      { type: 'value', name: 'PB', position: 'right' }
    ],
    series: [
      {
        name: 'PE (TTM)',
        type: 'line',
        data: data.map((d: any) => d.peTtm),
        itemStyle: { color: '#ffa726' }
      },
      {
        name: 'PB Ratio',
        type: 'line',
        yAxisIndex: 1,
        data: data.map((d: any) => d.pbRatio),
        itemStyle: { color: '#66bb6a' },
        lineStyle: { type: 'dashed' }
      }
    ]
  }
})

// Liquidity chart (Volume/Turnover)
const liquidityOption = computed(() => {
  if (!stockData.value?.history) return {}
  
  const data = stockData.value.history
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { data: ['Volume', 'Turnover Rate'], textStyle: { color: '#888' } },
    grid: { left: '5%', right: '10%', top: '15%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: [
      { type: 'value', name: 'Volume', splitLine: { lineStyle: { color: '#333' } } },
      { type: 'value', name: 'Rate', position: 'right' }
    ],
    series: [
      {
        name: 'Volume',
        type: 'bar',
        data: data.map((d: any) => d.volume),
        itemStyle: { color: 'rgba(100, 149, 237, 0.5)' }
      },
      {
        name: 'Turnover Rate',
        type: 'line',
        yAxisIndex: 1,
        data: data.map((d: any) => d.turnoverRate),
        itemStyle: { color: '#ef5350' }
      }
    ]
  }
})

// Market cap chart
const mcapOption = computed(() => {
  if (!stockData.value?.history) return {}
  
  const data = stockData.value.history
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '5%', right: '5%', top: '10%', bottom: '10%' },
    xAxis: { type: 'category', data: data.map((d: any) => d.day) },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#333' } } },
    series: [{
      type: 'line',
      data: data.map((d: any) => d.totalMarketCap),
      areaStyle: { color: 'rgba(100, 149, 237, 0.3)' },
      itemStyle: { color: '#6495ed' }
    }]
  }
})

// Shareholders table columns
const shareholderColumns = [
  { title: 'Trader ID', key: 'traderId', width: 100 },
  { title: 'Type', key: 'traderType', width: 120 },
  { 
    title: 'Shares', 
    key: 'quantity', 
    width: 120,
    render: (row: any) => formatNumber(row.quantity)
  },
  { 
    title: 'Value', 
    key: 'value', 
    width: 120,
    render: (row: any) => formatLargeNumber(row.value)
  },
  {
    title: 'Action',
    key: 'action',
    width: 80,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        quaternary: true,
        onClick: () => router.push({ name: 'traderDetail', params: { traderId: row.traderId } })
      }, { default: () => 'View' })
    }
  }
]

function formatNumber(n: number): string {
  return n?.toLocaleString() || '-'
}

function formatLargeNumber(n: number): string {
  if (n === null || n === undefined) return '-'
  if (Math.abs(n) > 1e12) return (n / 1e12).toFixed(2) + ' T'
  if (Math.abs(n) > 1e9) return (n / 1e9).toFixed(2) + ' B'
  if (Math.abs(n) > 1e6) return (n / 1e6).toFixed(2) + ' M'
  if (Math.abs(n) > 1e3) return (n / 1e3).toFixed(2) + ' K'
  return n.toFixed(2)
}

async function fetchData() {
  if (!store.currentSimulation || !stockId.value) return
  await fetchStock((signal) => stockApi.getDetail(store.currentSimulation!, stockId.value, store.currentDay, signal))
}

// Watch for changes
watch(() => [store.currentSimulation, store.currentDay, stockId.value], () => {
  if (store.currentSimulation && stockId.value) {
    fetchData()
  }
}, { immediate: true })

onMounted(() => {
  if (store.currentSimulation && stockId.value) {
    fetchData()
  }
})
</script>

<template>
  <div>
    <!-- Breadcrumb -->
    <NBreadcrumb style="margin-bottom: 16px">
      <NBreadcrumbItem @click="router.push({ name: 'stocks' })">Stocks</NBreadcrumbItem>
      <NBreadcrumbItem>{{ stockId }}</NBreadcrumbItem>
    </NBreadcrumb>
    
    <h1 style="margin-bottom: 24px">Stock Analysis: {{ stockId }}</h1>

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
                label="Price" 
                :value="stockData?.currentMetrics?.close?.toFixed(2) || '-'"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="PE (TTM)" 
                :value="stockData?.currentMetrics?.peTtm?.toFixed(2) || '-'"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="PB Ratio" 
                :value="stockData?.currentMetrics?.pbRatio?.toFixed(2) || '-'"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Market Cap" 
                :value="formatLargeNumber(stockData?.currentMetrics?.totalMarketCap)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Volume" 
                :value="formatLargeNumber(stockData?.currentMetrics?.volume)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Turnover Rate" 
                :value="((stockData?.currentMetrics?.turnoverRate || 0) * 100).toFixed(2) + '%'"
              />
            </NCard>
          </NGridItem>
        </NGrid>
        
        <!-- K-line Chart -->
        <NCard title="Price Chart" style="margin-bottom: 24px">
          <VChart :option="klineOption" style="height: 350px" autoresize />
        </NCard>
        
        <!-- Indicator Tabs -->
        <NCard style="margin-bottom: 24px">
          <NTabs type="line">
            <NTabPane name="valuation" tab="Valuation (PE/PB)">
              <VChart :option="valuationOption" style="height: 250px" autoresize />
            </NTabPane>
            <NTabPane name="liquidity" tab="Liquidity (Vol/Turnover)">
              <VChart :option="liquidityOption" style="height: 250px" autoresize />
            </NTabPane>
            <NTabPane name="mcap" tab="Market Cap">
              <VChart :option="mcapOption" style="height: 250px" autoresize />
            </NTabPane>
          </NTabs>
        </NCard>
        
        <!-- Shareholders -->
        <NCard title="Top Shareholders">
          <NDataTable
            :columns="shareholderColumns"
            :data="stockData?.shareholders || []"
            :bordered="false"
            size="small"
            :max-height="300"
          />
        </NCard>
      </NSpin>
    </template>
  </div>
</template>
