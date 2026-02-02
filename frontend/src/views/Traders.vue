<script setup lang="ts">
import { ref, watch, onMounted, h, computed } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NCard, 
  NDataTable, 
  NInput,
  NSpin,
  NButton,
  NTag,
  NSelect,
  NSpace
} from 'naive-ui'
import { useSimulationStore } from '@/stores/simulation'
import { traderApi } from '@/services/api'

const router = useRouter()
const store = useSimulationStore()

// Data
const loading = ref(false)
const traders = ref<any[]>([])
const searchQuery = ref('')
const selectedType = ref<string | null>(null)
const filteredTraders = ref<any[]>([])

// Trader type options
const typeOptions = computed(() => {
  const types = new Set(traders.value.map(t => t.traderType))
  return [
    { label: 'All Types', value: null },
    ...Array.from(types).map(t => ({ label: t, value: t }))
  ]
})

// Table columns with sortable columns
const columns = [
  { 
    title: 'ID', 
    key: 'traderId', 
    width: 80,
    sorter: (a: any, b: any) => a.traderId - b.traderId
  },
  { 
    title: 'Type', 
    key: 'traderType', 
    width: 120,
    sorter: (a: any, b: any) => a.traderType.localeCompare(b.traderType)
  },
  { 
    title: 'Status', 
    key: 'isActive', 
    width: 100,
    sorter: (a: any, b: any) => (a.isActive ? 1 : 0) - (b.isActive ? 1 : 0),
    render: (row: any) => h(NTag, { 
      type: row.isActive ? 'success' : 'error',
      size: 'small'
    }, { default: () => row.isActive ? 'Active' : 'Inactive' })
  },
  { 
    title: 'Total Assets', 
    key: 'totalAssets', 
    width: 140,
    sorter: (a: any, b: any) => a.totalAssets - b.totalAssets,
    render: (row: any) => formatLargeNumber(row.totalAssets)
  },
  { 
    title: 'Savings', 
    key: 'privateSavings', 
    width: 120,
    sorter: (a: any, b: any) => (a.privateSavings || 0) - (b.privateSavings || 0),
    render: (row: any) => formatLargeNumber(row.privateSavings)
  },
  { 
    title: 'Cash', 
    key: 'cash', 
    width: 120,
    sorter: (a: any, b: any) => (a.cash || 0) - (b.cash || 0),
    render: (row: any) => formatLargeNumber(row.cash)
  },
  { 
    title: 'Stocks', 
    key: 'stockValue', 
    width: 120,
    sorter: (a: any, b: any) => (a.stockValue || 0) - (b.stockValue || 0),
    render: (row: any) => formatLargeNumber(row.stockValue)
  },
  {
    title: 'Action',
    key: 'action',
    width: 100,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        type: 'primary',
        onClick: () => router.push({ name: 'traderDetail', params: { traderId: row.traderId } })
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
    const data = await traderApi.getList(store.currentSimulation, store.currentDay)
    traders.value = data as any[]
    applyFilters()
  } catch (error) {
    console.error('Failed to fetch traders:', error)
  } finally {
    loading.value = false
  }
}

function applyFilters() {
  let result = traders.value
  
  // Type filter
  if (selectedType.value) {
    result = result.filter(t => t.traderType === selectedType.value)
  }
  
  // Search filter
  if (searchQuery.value) {
    const lowerQuery = searchQuery.value.toLowerCase()
    result = result.filter(t => 
      t.traderId.toString().includes(lowerQuery) ||
      t.traderType.toLowerCase().includes(lowerQuery)
    )
  }
  
  filteredTraders.value = result
}

// Watch for filter changes
watch([searchQuery, selectedType], () => {
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
    <h1 style="margin-bottom: 24px">Trader Analysis</h1>
    
    <template v-if="!store.isConnected">
      <NCard>
        <p>Please select a simulation from the dropdown in the header to view trader data.</p>
      </NCard>
    </template>
    
    <template v-else>
      <NCard>
        <template #header>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>Trader List (Day {{ store.currentDay }})</span>
            <NSpace>
              <NSelect
                v-model:value="selectedType"
                :options="typeOptions"
                placeholder="Filter by Type"
                style="width: 160px"
                clearable
              />
              <NInput 
                v-model:value="searchQuery"
                placeholder="Search Trader ID or Type..."
                style="width: 250px"
                clearable
              />
            </NSpace>
          </div>
        </template>
        
        <NSpin :show="loading">
          <NDataTable
            :columns="columns"
            :data="filteredTraders"
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
