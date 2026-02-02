<script setup lang="ts">
import { ref, watch, onMounted, h, computed } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NCard, 
  NDataTable, 
  NInput,
  NSpin,
  NButton,
  NSelect,
  NSpace
} from 'naive-ui'
import { useSimulationStore } from '@/stores/simulation'
import { stockApi } from '@/services/api'

const router = useRouter()
const store = useSimulationStore()

// Data
const loading = ref(false)
const stocks = ref<any[]>([])
const searchQuery = ref('')
const selectedSector = ref<string | null>(null)
const filteredStocks = ref<any[]>([])

// Sector options - dynamically generated from data
const sectorOptions = computed(() => {
  const sectors = new Set(stocks.value.map(s => s.sector))
  return [
    { label: 'All Sectors', value: null },
    ...Array.from(sectors).map(s => ({ label: s, value: s }))
  ]
})

// Table columns with proper sorting
const columns = [
  { 
    title: 'Stock ID', 
    key: 'stockId', 
    width: 120,
    sorter: (a: any, b: any) => a.stockId.localeCompare(b.stockId)
  },
  { 
    title: 'Sector', 
    key: 'sector', 
    width: 120,
    sorter: (a: any, b: any) => a.sector.localeCompare(b.sector)
  },
  { 
    title: 'Price', 
    key: 'close', 
    width: 100,
    sorter: (a: any, b: any) => (a.close || 0) - (b.close || 0),
    render: (row: any) => row.close?.toFixed(2) || '-'
  },
  { 
    title: 'PE (TTM)', 
    key: 'peTtm', 
    width: 100,
    sorter: (a: any, b: any) => (a.peTtm || 0) - (b.peTtm || 0),
    render: (row: any) => row.peTtm?.toFixed(2) || '-'
  },
  { 
    title: 'Market Cap', 
    key: 'totalMarketCap', 
    width: 140,
    sorter: (a: any, b: any) => (a.totalMarketCap || 0) - (b.totalMarketCap || 0),
    render: (row: any) => formatLargeNumber(row.totalMarketCap)
  },
  {
    title: 'Action',
    key: 'action',
    width: 100,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        type: 'primary',
        onClick: () => router.push({ name: 'stockDetail', params: { stockId: row.stockId } })
      }, { default: () => 'Detail' })
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

async function fetchData() {
  if (!store.currentSimulation) return
  
  loading.value = true
  try {
    const data = await stockApi.getList(store.currentSimulation, store.currentDay)
    stocks.value = data as any[]
    applyFilters()
  } catch (error) {
    console.error('Failed to fetch stocks:', error)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  let result = stocks.value
  
  // Sector filter
  if (selectedSector.value) {
    result = result.filter(s => s.sector === selectedSector.value)
  }
  
  // Search filter
  if (searchQuery.value) {
    const lowerQuery = searchQuery.value.toLowerCase()
    result = result.filter(s => 
      s.stockId.toLowerCase().includes(lowerQuery) ||
      s.sector.toLowerCase().includes(lowerQuery)
    )
  }
  
  filteredStocks.value = result
}

// Watch for filter changes
watch([searchQuery, selectedSector], () => {
  applyFilters()
})

// Watch for simulation changes
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
    <h1 style="margin-bottom: 24px">Stock Analysis</h1>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header to view stock data.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NCard>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>Stock List (Day {{ store.currentDay }})</span>
            <NSpace>
              <NSelect
                v-model:value="selectedSector"
                :options="sectorOptions"
                placeholder="Filter by Sector"
                style="width: 160px"
                clearable
              />
              <NInput 
                v-model:value="searchQuery"
                placeholder="Search Stock ID or Sector..."
                style="width: 250px"
                clearable
              />
            </NSpace>
          </div>
        </template>
        
        <NSpin :show="loading">
          <NDataTable
            :columns="columns"
            :data="filteredStocks"
            :bordered="false"
            :pagination="{ pageSize: 20 }"
            :max-height="600"
            virtual-scroll
          />
        </NSpin>
      </NCard>
    </template>
  </div>
</template>
