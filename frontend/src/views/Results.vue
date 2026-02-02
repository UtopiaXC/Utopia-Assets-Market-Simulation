<script setup lang="ts">
import { ref, onMounted, h } from 'vue'
import { 
  NCard, 
  NDataTable, 
  NButton, 
  NSpace, 
  NIcon, 
  NText, 
  NModal, 
  NInput,
  useMessage,
  useDialog
} from 'naive-ui'
import { 
  Refresh, 
  TrashOutline, 
  CreateOutline
} from '@vicons/ionicons5'
import { simulationApi } from '@/services/api'
import { useSimulationStore } from '@/stores/simulation'

const message = useMessage()
const dialog = useDialog()
const store = useSimulationStore()

// State
const loading = ref(false)
const simulationResults = ref<any[]>([])

// Rename State
const showRenameModal = ref(false)
const renameValue = ref('')
const currentRenameName = ref('')

// Columns
const columns = [
  { 
    title: 'Name', 
    key: 'name',
    sorter: 'default'
  },
  { 
    title: 'Days', 
    key: 'days',
    width: 100,
    sorter: (row1: any, row2: any) => row1.days - row2.days
  },
  { 
    title: 'Created', 
    key: 'created',
    sorter: (row1: any, row2: any) => new Date(row1.lastModified).getTime() - new Date(row2.lastModified).getTime()
  },
  { 
    title: 'Action', 
    key: 'action',
    width: 200,
    render: (row: any) => {
      return h(NSpace, null, {
        default: () => [
          h(NButton, {
            size: 'small',
            onClick: () => openRenameModal(row.name)
          }, { icon: () => h(NIcon, null, { default: () => h(CreateOutline) }) }),
          h(NButton, {
            size: 'small',
            type: 'error',
            ghost: true,
            onClick: () => deleteResult(row.name)
          }, { icon: () => h(NIcon, null, { default: () => h(TrashOutline) }) })
        ]
      })
    }
  }
]

async function loadResults() {
  loading.value = true
  try {
    const data = await simulationApi.listSimulations()
    simulationResults.value = data.map((sim: any) => ({
      name: sim.name,
      days: 0, // Backend doesn't provide this yet without opening DB
      created: new Date(sim.lastModified).toLocaleString(),
      lastModified: sim.lastModified
    }))
  } catch (error) {
    message.error('Failed to load simulation results')
  } finally {
    loading.value = false
  }
}

function openRenameModal(name: string) {
  currentRenameName.value = name
  renameValue.value = name.replace('.db', '')
  showRenameModal.value = true
}

async function confirmRename() {
  if (!renameValue.value) return
  
  try {
    await simulationApi.renameSimulation(currentRenameName.value, renameValue.value)
    message.success('Renamed successfully')
    showRenameModal.value = false
    loadResults()
    // Refresh global store list too
    store.fetchSimulations()
  } catch (error: any) {
    message.error(error.response?.data?.error || 'Rename failed')
  }
}

function deleteResult(name: string) {
  dialog.warning({
    title: 'Confirm Delete',
    content: `Are you sure you want to delete "${name}"? This cannot be undone.`,
    positiveText: 'Delete',
    negativeText: 'Cancel',
    onPositiveClick: async () => {
      try {
        await simulationApi.deleteSimulation(name)
        message.success('Deleted successfully')
        loadResults()
        store.fetchSimulations()
      } catch (error: any) {
        message.error(error.response?.data?.error || 'Delete failed')
      }
    }
  })
}

onMounted(() => {
  loadResults()
})
</script>

<template>
  <div>
    <h1 style="margin-bottom: 24px">Simulation Results</h1>
    
    <NCard>
      <template #header>
        <NSpace justify="space-between">
          <span>Manage Results</span>
          <NButton size="small" @click="loadResults" :loading="loading">
            <template #icon>
              <NIcon><Refresh /></NIcon>
            </template>
            Refresh
          </NButton>
        </NSpace>
      </template>
      
      <NDataTable
        :columns="columns"
        :data="simulationResults"
        :loading="loading"
        :pagination="{ pageSize: 10 }"
      />
    </NCard>
    
    <!-- Rename Modal -->
    <NModal
      v-model:show="showRenameModal"
      preset="dialog"
      title="Rename Simulation"
      positive-text="Confirm"
      negative-text="Cancel"
      @positive-click="confirmRename"
      @negative-click="showRenameModal = false"
    >
      <NInput 
        v-model:value="renameValue" 
        placeholder="Enter new name" 
        @keyup.enter="confirmRename"
      />
    </NModal>
  </div>
</template>
