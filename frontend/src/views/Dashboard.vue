<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { 
  NCard, 
  NGrid, 
  NGridItem, 
  NDataTable, 
  NButton, 
  NEmpty,
  NStatistic,
  NSpin,
  NSpace,
  NText
} from 'naive-ui'
import { useSimulationStore } from '@/stores/simulation'

const router = useRouter()
const store = useSimulationStore()

// Simulation table columns
const columns = [
  {
    title: 'Name',
    key: 'name',
    ellipsis: { tooltip: true }
  },
  {
    title: 'Size',
    key: 'size',
    width: 100,
    render: (row: any) => formatBytes(row.size)
  },
  {
    title: 'Last Modified',
    key: 'lastModified',
    width: 180,
    render: (row: any) => new Date(row.lastModified).toLocaleString()
  },
  {
    title: 'Action',
    key: 'action',
    width: 120,
    render: (row: any) => {
      return h(NButton, {
        size: 'small',
        type: 'primary',
        onClick: () => handleConnect(row.name)
      }, { default: () => 'Load' })
    }
  }
]

import { h } from 'vue'

function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

async function handleConnect(fileName: string) {
  await store.selectSimulation(fileName)
  if (store.isConnected) {
    router.push({ name: 'market' })
  }
}

async function handleRefresh() {
  await store.fetchSimulations()
}

onMounted(async () => {
  await store.fetchSimulations()
})
</script>

<template>
  <div>
    <h1 style="margin-bottom: 24px">Utopia Market Simulation Dashboard</h1>
    
    <!-- Statistics Cards -->
    <NGrid :cols="4" :x-gap="16" :y-gap="16" style="margin-bottom: 24px">
      <NGridItem>
        <NCard>
          <NStatistic label="Available Simulations" :value="store.simulations.length">
            <template #suffix>files</template>
          </NStatistic>
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard>
          <NStatistic 
            label="Current Simulation" 
            :value="store.currentSimulation || '-'"
          />
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard>
          <NStatistic 
            label="Current Day" 
            :value="store.isConnected ? store.currentDay : '-'"
          >
            <template #suffix v-if="store.isConnected">/ {{ store.totalDays }}</template>
          </NStatistic>
        </NCard>
      </NGridItem>
      <NGridItem>
        <NCard>
          <NStatistic 
            label="Connection Status" 
            :value="store.isConnected ? 'Connected' : 'Disconnected'"
          />
        </NCard>
      </NGridItem>
    </NGrid>
    
    <!-- Simulation List -->
    <NCard title="Simulation Results" style="margin-bottom: 24px">
      <template #header-extra>
        <NButton @click="handleRefresh" :loading="store.loading">
          Refresh
        </NButton>
      </template>
      
      <NSpin :show="store.loading">
        <NDataTable
          v-if="store.simulations.length > 0"
          :columns="columns"
          :data="store.simulations"
          :bordered="false"
          :single-line="false"
        />
        <NEmpty v-else description="No simulation results found in output directory" />
      </NSpin>
    </NCard>
    
    <!-- Quick Start Guide -->
    <NCard title="Quick Start Guide">
      <NSpace vertical>
        <NText>1. Run a simulation using the Java StockMarketSim main class</NText>
        <NText>2. Click "Refresh" to update the simulation list</NText>
        <NText>3. Click "Load" on a simulation to connect and analyze</NText>
        <NText>4. Use the sidebar to navigate between analysis views</NText>
        <NText>5. Use the day slider in the header to explore different simulation days</NText>
      </NSpace>
    </NCard>
  </div>
</template>
