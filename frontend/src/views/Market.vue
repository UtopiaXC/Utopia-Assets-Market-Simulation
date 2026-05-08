<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NCard, 
  NGrid, 
  NGridItem, 
  NDataTable, 
  NStatistic,
  NSpin,
  NSpace,
  NButton
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
import { marketApi } from '@/services/api'
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

const router = useRouter()
const store = useSimulationStore()

// Data
const { loading, data: marketData, fetch: fetchMarket } = useCancellableFetch<any>()

// K-line chart option
const klineOption = computed(() => {
  if (!marketData.value?.klineData) return {}
  
  const data = marketData.value.klineData
  const days = data.map((d: any) => d.day)
  const ohlc = data.map((d: any) => [d.open, d.close, d.low, d.high])
  const volumes = data.map((d: any) => d.volume)
  
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' }
    },
    legend: {
      data: ['Market Index', 'Volume'],
      textStyle: { color: '#888' }
    },
    grid: [
      { left: '5%', right: '5%', top: '10%', height: '55%' },
      { left: '5%', right: '5%', top: '72%', height: '18%' }
    ],
    xAxis: [
      { type: 'category', data: days, boundaryGap: true, gridIndex: 0 },
      { type: 'category', data: days, boundaryGap: true, gridIndex: 1 }
    ],
    yAxis: [
      { scale: true, gridIndex: 0, splitLine: { lineStyle: { color: '#333' } } },
      { scale: true, gridIndex: 1, splitLine: { lineStyle: { color: '#333' } } }
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: 0, end: 100 },
      { type: 'slider', xAxisIndex: [0, 1], bottom: 0 }
    ],
    series: [
      {
        name: 'Market Index',
        type: 'candlestick',
        data: ohlc,
        itemStyle: {
          color: '#26a69a',
          color0: '#ef5350',
          borderColor: '#26a69a',
          borderColor0: '#ef5350'
        }
      },
      {
        name: 'Volume',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: volumes,
        itemStyle: { color: 'rgba(100, 149, 237, 0.5)' }
      }
    ]
  }
})

// Top stocks table columns
const topStocksColumns = [
  { title: 'Rank', key: 'rank', width: 60 },
  { title: 'Stock ID', key: 'stockId', width: 100 },
  { title: 'Sector', key: 'sector', width: 100 },
  { 
    title: 'Close', 
    key: 'close', 
    width: 100,
    render: (row: any) => row.close?.toFixed(2)
  },
  { 
    title: 'Turnover', 
    key: 'turnover', 
    width: 120,
    render: (row: any) => formatLargeNumber(row.turnover)
  },
  { 
    title: 'Volume', 
    key: 'volume', 
    width: 120,
    render: (row: any) => formatLargeNumber(row.volume)
  },
  { 
    title: 'Turnover Rate', 
    key: 'turnoverRate', 
    width: 120,
    render: (row: any) => (row.turnoverRate * 100).toFixed(2) + '%'
  },
  {
    title: 'Action',
    key: 'action',
    width: 80,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        type: 'primary',
        quaternary: true,
        onClick: () => router.push({ name: 'stockDetail', params: { stockId: row.stockId } })
      }, { default: () => 'View' })
    }
  }
]

import { h } from 'vue'

function formatLargeNumber(n: number): string {
  if (n === null || n === undefined) return '-'
  if (Math.abs(n) > 1e12) return (n / 1e12).toFixed(2) + ' T'
  if (Math.abs(n) > 1e9) return (n / 1e9).toFixed(2) + ' B'
  if (Math.abs(n) > 1e6) return (n / 1e6).toFixed(2) + ' M'
  if (Math.abs(n) > 1e3) return (n / 1e3).toFixed(2) + ' K'
  return n.toFixed(2)
}

async function fetchData() {
  if (!store.currentSimulation) return
  await fetchMarket((signal) => marketApi.getOverview(store.currentSimulation!, store.currentDay, signal))
}

// Watch for changes
watch(() => [store.currentSimulation, store.currentDay], () => {
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
    <h1 style="margin-bottom: 24px">Market Overview</h1>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header to view market data.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <!-- Day Stats Cards -->
        <NGrid :cols="5" :x-gap="16" :y-gap="16" style="margin-bottom: 24px">
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Close" 
                :value="marketData?.dayDetail?.close?.toFixed(2) || '-'"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Volume" 
                :value="formatLargeNumber(marketData?.dayDetail?.volume)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Turnover" 
                :value="formatLargeNumber(marketData?.dayDetail?.turnover)"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Turnover Rate" 
                :value="((marketData?.dayDetail?.turnoverRate || 0) * 100).toFixed(2) + '%'"
              />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard>
              <NStatistic 
                label="Social Wealth Pool" 
                :value="formatLargeNumber(marketData?.dayDetail?.socialWealthPool)"
              />
            </NCard>
          </NGridItem>
        </NGrid>
        
        <!-- K-line Chart -->
        <NCard title="Market Index Trend" style="margin-bottom: 24px">
          <VChart 
            :option="klineOption" 
            style="height: 450px" 
            autoresize
          />
        </NCard>
        
        <!-- Top Active Stocks -->
        <NCard title="Top 10 Active Stocks (by Turnover)">
          <NDataTable
            :columns="topStocksColumns"
            :data="marketData?.topActiveStocks || []"
            :bordered="false"
            size="small"
          />
        </NCard>
      </NSpin>
    </template>
  </div>
</template>
