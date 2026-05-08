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
  NTag,
  NTabs,
  NTabPane,
  NText,
  NAlert,
  NEmpty,
  NModal,
  NScrollbar
} from 'naive-ui'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { LineChart, GraphChart, BarChart } from 'echarts/charts'
import { 
  GridComponent, 
  TooltipComponent, 
  LegendComponent 
} from 'echarts/components'
import { useSimulationStore } from '@/stores/simulation'
import { traderApi } from '@/services/api'
import { useCancellableFetch } from '@/composables/useCancellableFetch'

use([
  CanvasRenderer, 
  LineChart,
  GraphChart,
  BarChart,
  GridComponent, 
  TooltipComponent, 
  LegendComponent
])

const route = useRoute()
const router = useRouter()
const store = useSimulationStore()

const traderId = computed(() => {
  const id = parseInt(route.params.traderId as string)
  return isNaN(id) ? undefined : id
})

// Data
const { loading, fetch: fetchTrader } = useCancellableFetch<any>()
const traderData = ref<any>(null)
const trades = ref<any[]>([])

// Social influence modal
const showInfluenceModal = ref(false)
const selectedTrade = ref<any>(null)
const selectedInfluenceData = ref<any[]>([])

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
      { name: 'Savings', type: 'line', stack: 'total', areaStyle: {},
        data: data.map((d: any) => d.privateSavings), itemStyle: { color: '#4CAF50' } },
      { name: 'Stocks', type: 'line', stack: 'total', areaStyle: {},
        data: data.map((d: any) => d.stockValue), itemStyle: { color: '#2196F3' } },
      { name: 'Cash', type: 'line', stack: 'total', areaStyle: {},
        data: data.map((d: any) => d.cash), itemStyle: { color: '#FFD700' } },
      { name: 'Frozen', type: 'line', stack: 'total', areaStyle: {},
        data: data.map((d: any) => d.reservedCash), itemStyle: { color: '#FF9800' } }
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
    yAxis: { type: 'value', min: 0, max: 1, splitLine: { lineStyle: { color: '#333' } } },
    series: [{ type: 'line', data: data.map((d: any) => d.riskTolerance), itemStyle: { color: '#E91E63' } }]
  }
})

// Social Network Graph from selected trade
const socialGraphOption = computed(() => {
  if (selectedInfluenceData.value.length === 0) return {}

  const influences = selectedInfluenceData.value

  // Central node
  const nodes: any[] = [{
    id: String(traderId.value),
    name: `Agent ${traderId.value}`,
    symbolSize: 60,
    itemStyle: { color: '#FF6B35', shadowBlur: 20, shadowColor: 'rgba(255, 107, 53, 0.5)' },
    label: { show: true, fontSize: 14, fontWeight: 'bold', color: '#fff' },
    x: 300, y: 300
  }]

  // Neighbor nodes
  const angleStep = (2 * Math.PI) / influences.length
  influences.forEach((inf: any, idx: number) => {
    const angle = angleStep * idx
    const radius = 200
    nodes.push({
      id: String(inf.neighborId),
      name: `Agent ${inf.neighborId}`,
      symbolSize: 20 + (inf.weight || 0) * 80,
      x: 300 + radius * Math.cos(angle),
      y: 300 + radius * Math.sin(angle),
      itemStyle: {
        color: (inf.similarity || 0) > 0.8 ? '#4CAF50' :
               (inf.similarity || 0) > 0.5 ? '#2196F3' : '#9E9E9E'
      },
      label: { show: true, fontSize: 10, color: '#bbb' }
    })
  })

  // Edges
  const links = influences.map((inf: any) => ({
    source: String(traderId.value),
    target: String(inf.neighborId),
    lineStyle: {
      width: 1 + (inf.weight || 0) * 8,
      color: (inf.similarity || 0) > 0.8 ? 'rgba(76, 175, 80, 0.6)' :
             (inf.similarity || 0) > 0.5 ? 'rgba(33, 150, 243, 0.6)' : 'rgba(158, 158, 158, 0.3)',
      curveness: 0.1
    }
  }))

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          const nId = parseInt(params.data.id)
          if (nId === traderId.value) return `<b>Agent ${nId}</b> (Current)`
          const inf = influences.find((i: any) => i.neighborId === nId)
          if (inf) {
            return `<b>Agent ${nId}</b><br/>` +
              `Similarity: ${(inf.similarity || 0).toFixed(3)}<br/>` +
              `Influence Weight: ${((inf.weight || 0) * 100).toFixed(1)}%<br/>` +
              `Belief: ${(inf.belief || 0).toFixed(2)}`
          }
        }
        return ''
      }
    },
    series: [{
      type: 'graph', layout: 'none', roam: true,
      data: nodes, links: links,
      lineStyle: { opacity: 0.9 },
      emphasis: { focus: 'adjacency', lineStyle: { width: 6 } },
      animation: true, animationDuration: 1500
    }]
  }
})

// Trade records table columns
const tradeColumns = [
  { title: 'Day', key: 'day', width: 60 },
  { title: 'Stock', key: 'stockId', width: 80 },
  { title: 'Side', key: 'side', width: 60,
    render: (row: any) => h(NTag, { 
      type: row.buyerId === traderId.value ? 'success' : 'error', size: 'small' 
    }, { default: () => row.buyerId === traderId.value ? 'BUY' : 'SELL' })
  },
  { title: 'Price', key: 'price', width: 80,
    render: (row: any) => row.price?.toFixed(2)
  },
  { title: 'Quantity', key: 'quantity', width: 80,
    render: (row: any) => row.quantity?.toLocaleString()
  },
  { title: 'Counterparty', key: 'counterparty', width: 100,
    render: (row: any) => {
      const cpId = row.buyerId === traderId.value ? row.sellerId : row.buyerId
      return h(NButton, {
        size: 'tiny', text: true, type: 'info',
        onClick: () => router.push({ name: 'traderDetail', params: { traderId: cpId } })
      }, { default: () => `Agent ${cpId}` })
    }
  },
  { title: 'Influence', key: 'influence', width: 80,
    render: (row: any) => {
      if (!row.influenceJson) return h(NText, { depth: 3 }, { default: () => '—' })
      return h(NButton, {
        size: 'tiny', type: 'primary', quaternary: true,
        onClick: () => showTradeInfluence(row)
      }, { default: () => 'View' })
    }
  }
]

// Holdings table columns
const holdingsColumns = [
  { title: 'Stock ID', key: 'stockId', width: 120 },
  { title: 'Quantity', key: 'quantity', width: 120,
    render: (row: any) => row.quantity?.toLocaleString() },
  { title: 'Price', key: 'price', width: 100,
    render: (row: any) => row.price?.toFixed(2) },
  { title: 'Value', key: 'marketValue', width: 120,
    render: (row: any) => formatLargeNumber(row.marketValue) },
  { title: 'Action', key: 'action', width: 80,
    render: (row: any) => h(NButton, {
      size: 'small', quaternary: true,
      onClick: () => router.push({ name: 'stockDetail', params: { stockId: row.stockId } })
    }, { default: () => 'View' })
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
  return pnl > 0 ? 'positive' : pnl < 0 ? 'negative' : ''
})

const dailyPnlText = computed(() => {
  const pnl = traderData.value?.currentMetrics?.dailyPnl
  if (pnl === null || pnl === undefined) return '-'
  return (pnl > 0 ? '+' : '') + formatLargeNumber(pnl)
})

function showTradeInfluence(trade: any) {
  selectedTrade.value = trade
  try {
    selectedInfluenceData.value = JSON.parse(trade.influenceJson) || []
  } catch (e) {
    selectedInfluenceData.value = []
  }
  showInfluenceModal.value = true
}

async function fetchData() {
  if (!store.currentSimulation || traderId.value === undefined) return
  const data = await fetchTrader(async (signal) => {
    const [detail, tradeData] = await Promise.all([
      traderApi.getDetail(store.currentSimulation!, traderId.value, store.currentDay, signal),
      traderApi.getAllTrades(store.currentSimulation!, traderId.value, signal)
    ])
    return { detail, tradeData }
  })
  
  if (data) {
    traderData.value = data.detail
    trades.value = (data.tradeData as any[]) || []
  }
}

watch(() => [store.currentSimulation, store.currentDay, traderId.value], () => {
  if (store.currentSimulation && traderId.value !== undefined) fetchData()
}, { immediate: true })

onMounted(() => {
  if (store.currentSimulation && traderId.value !== undefined) fetchData()
})
</script>

<template>
  <div>
    <NBreadcrumb style="margin-bottom: 16px">
      <NBreadcrumbItem @click="router.push({ name: 'traders' })">Traders</NBreadcrumbItem>
      <NBreadcrumbItem>{{ traderId !== undefined ? traderId : 'N/A' }}</NBreadcrumbItem>
    </NBreadcrumb>
    
    <div style="display: flex; align-items: center; gap: 16px; margin-bottom: 24px">
      <h1 style="margin: 0">Trader {{ traderId !== undefined ? traderId : 'N/A' }} Deep Dive</h1>
      <NTag :type="traderData?.isActive ? 'success' : 'error'" size="large">
        {{ traderData?.traderType || '-' }}
      </NTag>
    </div>
    
    <template v-if="!store.isConnected">
      <NCard><p>Please select a simulation from the dropdown in the header.</p></NCard>
    </template>
    
    <template v-else>
      <NSpin :show="loading">
        <!-- Metrics Cards -->
        <NGrid :cols="6" :x-gap="16" :y-gap="16" style="margin-bottom: 24px">
          <NGridItem><NCard>
            <NStatistic label="Total Assets" :value="formatLargeNumber(traderData?.currentMetrics?.totalAssets)" />
          </NCard></NGridItem>
          <NGridItem><NCard>
            <NStatistic label="Daily PnL"><span :class="dailyPnlClass">{{ dailyPnlText }}</span></NStatistic>
          </NCard></NGridItem>
          <NGridItem><NCard>
            <NStatistic label="Savings" :value="formatLargeNumber(traderData?.currentMetrics?.privateSavings)" />
          </NCard></NGridItem>
          <NGridItem><NCard>
            <NStatistic label="Cash" :value="formatLargeNumber((traderData?.currentMetrics?.cash || 0) + (traderData?.currentMetrics?.reservedCash || 0))" />
          </NCard></NGridItem>
          <NGridItem><NCard>
            <NStatistic label="Stocks" :value="formatLargeNumber(traderData?.currentMetrics?.stockValue)" />
          </NCard></NGridItem>
          <NGridItem><NCard>
            <NStatistic label="Risk Tolerance" :value="traderData?.currentMetrics?.riskTolerance?.toFixed(2) || '-'" />
          </NCard></NGridItem>
        </NGrid>
        
        <NTabs type="card">
          <!-- Portfolio Tab -->
          <NTabPane name="portfolio" tab="Portfolio">
            <NGrid :cols="2" :x-gap="16" :y-gap="16">
              <NGridItem>
                <NCard title="Asset Composition History" style="margin-bottom: 16px">
                  <VChart :option="assetOption" style="height: 300px" autoresize />
                </NCard>
                <NCard title="Risk Tolerance">
                  <VChart :option="riskOption" style="height: 200px" autoresize />
                </NCard>
              </NGridItem>
              <NGridItem>
                <NCard title="Current Holdings">
                  <NDataTable :columns="holdingsColumns" :data="traderData?.holdings || []"
                    :bordered="false" size="small" :max-height="540" />
                </NCard>
              </NGridItem>
            </NGrid>
          </NTabPane>
          
          <!-- Trade Details Tab -->
          <NTabPane name="trades" tab="Trade Records">
            <NAlert type="info" title="Trade Influence" style="margin-bottom: 12px">
              Click the View icon on any trade to see which agents influenced this trading decision.
            </NAlert>
            <NCard title="Trade Records">
              <NDataTable :columns="tradeColumns" :data="trades"
                :bordered="false" size="small" :max-height="500"
                :pagination="{ pageSize: 20 }" />
            </NCard>
          </NTabPane>
        </NTabs>
      </NSpin>
    </template>
    
    <!-- Social Influence Modal -->
    <NModal v-model:show="showInfluenceModal" preset="card" 
      :title="`Social Influence — Stock ${selectedTrade?.stockId} (Day ${selectedTrade?.day})`"
      style="width: 900px; max-width: 95vw;">
      <template v-if="selectedInfluenceData.length > 0">
        <NGrid :cols="2" :x-gap="16">
          <NGridItem>
            <NCard title="Influence Network" size="small">
              <VChart :option="socialGraphOption" style="height: 400px" autoresize />
            </NCard>
          </NGridItem>
          <NGridItem>
            <NCard title="Neighbor Details" size="small">
              <NScrollbar style="max-height: 380px">
                <NDataTable
                  :columns="[
                    { title: 'Neighbor', key: 'neighborId', width: 80 },
                    { title: 'Similarity', key: 'similarity', width: 80,
                      render: (row: any) => (row.similarity || 0).toFixed(3) },
                    { title: 'Weight', key: 'weight', width: 70,
                      render: (row: any) => ((row.weight || 0) * 100).toFixed(1) + '%' },
                    { title: 'Belief', key: 'belief', width: 80,
                      render: (row: any) => (row.belief || 0).toFixed(2) },
                    { title: '', key: 'action', width: 40,
                      render: (row: any) => h(NButton, {
                        size: 'tiny', text: true, type: 'info',
                        onClick: () => {
                          showInfluenceModal.value = false
                          router.push({ name: 'traderDetail', params: { traderId: row.neighborId } })
                        }
                      }, { default: () => '→' })
                    }
                  ]"
                  :data="selectedInfluenceData" :bordered="false" size="small" />
              </NScrollbar>
            </NCard>
          </NGridItem>
        </NGrid>
      </template>
      <template v-else>
        <NEmpty description="No influence data available for this trade." />
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.positive { color: #4CAF50; }
.negative { color: #F44336; }
</style>
