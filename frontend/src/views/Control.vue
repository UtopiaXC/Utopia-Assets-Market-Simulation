<template>
  <div class="control-page">
    <!-- Header Controls -->
    <n-card class="control-card">
      <template #header>
        <n-space align="center">
          <n-icon size="24"><PlayCircle /></n-icon>
          <span>Simulation Control</span>
          <n-tag :type="statusType" size="small">{{ status.state }}</n-tag>
        </n-space>
      </template>
      
      <n-space vertical size="large">
        <!-- Simulation Name Input -->
        <n-form-item label="Simulation Name" label-placement="left" label-width="140px">
          <n-input 
            v-model:value="simulationName" 
            placeholder="Enter simulation name..."
            :disabled="isRunning || isPaused"
            style="width: 400px"
          />
        </n-form-item>
        
        <!-- Main Controls -->
        <n-space>
          <n-button type="success" :disabled="isRunning" @click="startSimulation">
            <template #icon><n-icon><Play /></n-icon></template>
            Start
          </n-button>
          <n-button type="warning" :disabled="!isRunning" @click="pauseSimulation">
            <template #icon><n-icon><Pause /></n-icon></template>
            Pause
          </n-button>
          <n-button type="info" :disabled="!isPaused" @click="resumeSimulation">
            <template #icon><n-icon><Play /></n-icon></template>
            Resume
          </n-button>
          <n-button type="error" :disabled="!hasSession" @click="stopSimulation">
            <template #icon><n-icon><Stop /></n-icon></template>
            Stop
          </n-button>
        </n-space>
        
        <!-- Progress -->
        <n-space vertical>
          <n-text>Progress: Day {{ status.currentDay }} (Step {{ status.currentStepInDay || 0 }} / {{ status.totalStepsPerDay || '-' }}) / {{ status.totalDays }}</n-text>
          <n-progress type="line" :percentage="status.progress" :height="20" indicator-placement="inside">
            {{ status.progress.toFixed(1) }}%
          </n-progress>
        </n-space>
        
        <!-- Speed Control - Revised -->
        <n-space align="center">
          <n-text>Step Delay:</n-text>
          <n-input-number 
            v-model:value="stepDelay" 
            :min="0" 
            :max="10000" 
            :step="100"
            style="width: 120px"
          >
            <template #suffix>ms</template>
          </n-input-number>
          
          <n-divider vertical />
          
          <n-text>Speed Multiplier:</n-text>
          <n-button-group>
            <n-button v-for="speed in speedPresets" :key="speed" 
              :type="currentSpeed === speed ? 'primary' : 'default'"
              @click="applySpeed(speed)">
              {{ speed }}x
            </n-button>
            <n-button 
              :type="currentSpeed === 0 ? 'primary' : 'default'"
              @click="applySpeed(0)">
              MAX
            </n-button>
          </n-button-group>
          
          <n-input-number 
            v-model:value="customSpeed" 
            :min="0.01" 
            :max="100" 
            :step="0.1" 
            style="width: 100px"
            :disabled="currentSpeed === 0"
          />
          <n-button @click="applySpeed(customSpeed)" :disabled="currentSpeed === 0">Apply</n-button>
        </n-space>
        
        <!-- Real-time Stats -->
        <n-grid :cols="4" :x-gap="12" v-if="hasSession">
          <n-gi>
            <n-statistic label="Market Index">
              {{ status.marketIndex.toFixed(2) }}
            </n-statistic>
          </n-gi>
          <n-gi>
            <n-statistic label="Active Agents">
              {{ status.activeAgents }}
            </n-statistic>
          </n-gi>
          <n-gi>
            <n-statistic label="Speed">
              {{ currentSpeed === 0 ? 'MAX' : currentSpeed + 'x' }}
            </n-statistic>
          </n-gi>
          <n-gi>
            <n-statistic label="Session">
              {{ status.sessionId ? status.sessionId.substring(0, 8) : 'N/A' }}
            </n-statistic>
          </n-gi>
        </n-grid>
      </n-space>
    </n-card>
    
    <!-- Tabs for Config and Events -->
    <n-tabs type="card" class="control-tabs">
      <!-- Configuration Tab -->
      <n-tab-pane name="config" tab="Configuration">
        <n-card>
          <n-form label-placement="left" label-width="180px">
            <n-grid :cols="2" :x-gap="24">
              <n-gi>
                <n-form-item label="Number of Stocks">
                  <n-input-number v-model:value="config.numStocks" :min="10" :max="200" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Simulation Days">
                  <n-input-number v-model:value="config.simulationDays" :min="100" :max="10000" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Total Agents">
                  <n-input-number v-model:value="config.totalAgents" :min="100" :max="50000" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Institutional Ratio">
                  <n-slider v-model:value="config.institutionalRatio" :min="0" :max="0.5" :step="0.01" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Index Base">
                  <n-input-number v-model:value="config.indexBase" :min="1000" :max="10000" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Scenario">
                  <n-select v-model:value="config.scenarioName" :options="scenarioOptions" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Minutes Per Step">
                  <n-input-number v-model:value="minutesPerStep" :min="1" :max="240" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Log Sample Interval">
                  <n-input-number v-model:value="config.logSampleInterval" :min="1" :max="100" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Holdings Snapshot Interval">
                  <n-input-number v-model:value="config.holdingsSnapshotInterval" :min="1" :max="100" />
                </n-form-item>
              </n-gi>
            </n-grid>
            <n-space justify="end">
              <n-button @click="loadDefaultConfig">Reset to Default</n-button>
            </n-space>
          </n-form>
        </n-card>
      </n-tab-pane>
      
      <!-- Event Injection Tab -->
      <n-tab-pane name="events" tab="Event Injection">
        <n-grid :cols="2" :x-gap="24">
          <!-- Inject New Event -->
          <n-gi>
            <n-card title="Inject Event">
              <n-form label-placement="top">
                <n-form-item label="Event Type">
                  <n-select v-model:value="newEvent.type" :options="eventTypeOptions" />
                </n-form-item>
                <n-form-item label="Target Day">
                  <n-input-number v-model:value="newEvent.targetDay" :min="1" placeholder="Leave 0 for immediate" />
                </n-form-item>
                
                <!-- Rate Cut/Hike Parameters -->
                <template v-if="newEvent.type === 'rate-cut' || newEvent.type === 'rate-hike'">
                  <n-form-item :label="newEvent.type === 'rate-cut' ? 'Liquidity Per Agent' : 'Liquidity Ratio'">
                    <n-input-number v-model:value="newEvent.liquidity" :min="0" />
                  </n-form-item>
                  <n-form-item :label="newEvent.type === 'rate-cut' ? 'Risk Boost' : 'Risk Drop'">
                    <n-input-number v-model:value="newEvent.risk" :min="0" :max="1" :step="0.05" />
                  </n-form-item>
                </template>
                
                <!-- Sector Parameters -->
                <template v-if="newEvent.type === 'sector-sentiment' || newEvent.type === 'sector-fundamental'">
                  <n-form-item label="Sector">
                    <n-select v-model:value="newEvent.sector" :options="sectorOptions" />
                  </n-form-item>
                  <n-form-item :label="newEvent.type === 'sector-sentiment' ? 'Sentiment Multiplier' : 'EPS Change'">
                    <n-input-number v-model:value="newEvent.value" :step="0.1" />
                  </n-form-item>
                </template>
                
                <n-button type="primary" block @click="injectEvent">
                  <template #icon><n-icon><Add /></n-icon></template>
                  Inject Event
                </n-button>
              </n-form>
            </n-card>
          </n-gi>
          
          <!-- Pending Events -->
          <n-gi>
            <n-card title="Scheduled Events">
              <n-empty v-if="pendingEvents.length === 0" description="No pending events" />
              <n-list v-else>
                <n-list-item v-for="event in pendingEvents" :key="event.eventId">
                  <n-thing :title="event.eventType" :description="event.description">
                    <template #header-extra>
                      <n-tag type="info">Day {{ event.targetDay }}</n-tag>
                    </template>
                  </n-thing>
                  <template #suffix>
                    <n-button quaternary type="error" @click="cancelEvent(event.eventId)">
                      <template #icon><n-icon><Close /></n-icon></template>
                    </n-button>
                  </template>
                </n-list-item>
              </n-list>
            </n-card>
          </n-gi>
        </n-grid>
        
        <!-- Event History -->
        <n-card title="Event History" style="margin-top: 16px;">
          <n-data-table :columns="eventHistoryColumns" :data="eventHistory" :max-height="200" />
        </n-card>
      </n-tab-pane>
      
      <!-- API Interfaces Tab -->
      <n-tab-pane name="api" tab="External API">
        <n-card>
          <n-alert type="info" title="External Integration APIs">
            The following APIs are available for external integration:
          </n-alert>
          
          <n-descriptions bordered :column="1" style="margin-top: 16px;">
            <n-descriptions-item label="FAVAR Matrix API">
              <n-text code>POST /api/control/favar</n-text>
              <n-text depth="3"> - Apply factor-augmented VAR intervention</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="EA Batch Events">
              <n-text code>POST /api/control/events (array)</n-text>
              <n-text depth="3"> - Inject batch events from evolutionary algorithm</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="LLM Context">
              <n-text code>GET /api/control/llm/context</n-text>
              <n-text depth="3"> - Get market context for LLM decision making</n-text>
            </n-descriptions-item>
            <n-descriptions-item label="Agent State">
              <n-text code>GET /api/control/llm/agent/{id}</n-text>
              <n-text depth="3"> - Get individual agent state for LLM</n-text>
            </n-descriptions-item>
          </n-descriptions>
          
          <n-divider />
          
          <n-text type="warning">
            These APIs are placeholders for future FAVAR model, Evolutionary Algorithm, and LLM integration.
            Implementation details will be added as the models are developed.
          </n-text>
        </n-card>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, h, watch } from 'vue'
import { 
  NCard, NSpace, NButton, NButtonGroup, NProgress, NText, NIcon, NTag,
  NTabs, NTabPane, NForm, NFormItem, NInputNumber, NSelect, NSlider,
  NGrid, NGi, NStatistic, NList, NListItem, NThing, NInput,
  NDataTable, NAlert, NDescriptions, NDescriptionsItem, NDivider, NEmpty,
  useMessage
} from 'naive-ui'
import { 
  Play, Pause, Stop, PlayCircle, Add, Close, Refresh, TrashOutline, CreateOutline
} from '@vicons/ionicons5'
import { controlApi } from '@/services/api' // simulationApi removed

const message = useMessage()

// Status polling
let pollInterval: number | null = null

// Generate default simulation name
function generateDefaultName() {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const hour = String(now.getHours()).padStart(2, '0')
  const minute = String(now.getMinutes()).padStart(2, '0')
  const second = String(now.getSeconds()).padStart(2, '0')
  return `sim_${year}${month}${day}_${hour}${minute}${second}`
}

// Reactive state
const simulationName = ref(generateDefaultName())

const status = ref({
  sessionId: null as string | null,
  state: 'NO_SESSION',
  currentDay: 0,
  totalDays: 1000,
  progress: 0,
  activeAgents: 0,
  marketIndex: 0,
  speedMultiplier: 1.0,
  lastError: null as string | null,
  currentStepInDay: 0,
  totalStepsPerDay: 0
})

const config = ref({
  numStocks: 50,
  simulationDays: 1000,
  totalAgents: 5000,
  institutionalRatio: 0.05,
  indexBase: 3000,
  scenarioName: 'TestScenario',
  stepsPerDay: 22, // Default from Config
  logSampleInterval: 1,
  holdingsSnapshotInterval: 10
})

const pendingEvents = ref<any[]>([])
const eventHistory = ref<any[]>([])

const newEvent = ref({
  type: 'rate-cut',
  targetDay: 0,
  liquidity: 1000000,
  risk: 0.1,
  sector: 'TECH',
  value: 1.5
})

// Speed control - revised
const stepDelay = ref(100) // Base delay in ms
const currentSpeed = ref(1) // 0 = MAX
const customSpeed = ref(1)
const speedPresets = [0.1, 1, 5, 10, 50, 100]

// Step Calculation
const minutesPerStep = ref(15)
watch(minutesPerStep, (val) => {
  if (val > 0) {
    // Formula: (4 hours * 60) / minutes
    config.value.stepsPerDay = Math.floor(240 / val)
  }
}, { immediate: true })

// Computed
const hasSession = computed(() => status.value.sessionId !== null)
const isRunning = computed(() => status.value.state === 'RUNNING')
const isPaused = computed(() => status.value.state === 'PAUSED')

const statusType = computed(() => {
  switch (status.value.state) {
    case 'RUNNING': return 'success'
    case 'PAUSED': return 'warning'
    case 'COMPLETED': return 'info'
    case 'ERROR': return 'error'
    default: return 'default'
  }
})

// Options
const scenarioOptions = [
  { label: 'Empty Scenario', value: 'EmptyScenario' },
  { label: 'Test Scenario', value: 'TestScenario' }
]

const eventTypeOptions = [
  { label: 'Rate Cut', value: 'rate-cut' },
  { label: 'Rate Hike', value: 'rate-hike' },
  { label: 'Sector Sentiment', value: 'sector-sentiment' },
  { label: 'Sector Fundamental', value: 'sector-fundamental' }
]

const sectorOptions = [
  { label: 'Technology', value: 'TECH' },
  { label: 'Healthcare', value: 'HEALTHCARE' },
  { label: 'Consumer', value: 'CONSUMER' },
  { label: 'Finance', value: 'FINANCE' },
  { label: 'Industry', value: 'INDUSTRY' }
]

const eventHistoryColumns = [
  { title: 'Event ID', key: 'eventId', width: 100 },
  { title: 'Type', key: 'eventType', width: 150 },
  { title: 'Day', key: 'targetDay', width: 80 },
  { title: 'Description', key: 'description' }
]

// Methods
async function loadStatus() {
  try {
    const data = await controlApi.getStatus() as any
    status.value = data
    currentSpeed.value = data.speedMultiplier
  } catch (e) {
    // Silent fail for polling
  }
}

async function loadPendingEvents() {
  try {
    const data = await controlApi.getPendingEvents() as any
    pendingEvents.value = data
  } catch (e) {
    // Silent fail
  }
}

async function loadEventHistory() {
  try {
    const data = await controlApi.getEventHistory() as any
    eventHistory.value = data
  } catch (e) {
    // Silent fail
  }
}

async function loadDefaultConfig() {
  try {
    const data = await controlApi.getDefaultConfig() as any
    config.value = { ...config.value, ...data }
    
    // Reset local inputs
    minutesPerStep.value = 15
    
    // Enforce step calculation (overwrite backend default of 22)
    config.value.stepsPerDay = Math.floor(240 / minutesPerStep.value)
    
    message.success('Configuration reset to defaults')
  } catch (e) {
    message.error('Failed to load default config')
  }
}

async function startSimulation() {
  try {
    const configWithName = {
      ...config.value,
      simulationName: simulationName.value,
      stepDelay: stepDelay.value
    }
    await controlApi.start(configWithName)
    message.success('Simulation started!')
    loadStatus()
  } catch (e: any) {
    message.error(e.response?.data?.error || 'Failed to start simulation')
  }
}

async function pauseSimulation() {
  try {
    await controlApi.pause()
    message.info('Simulation paused')
    loadStatus()
  } catch (e) {
    message.error('Failed to pause')
  }
}

async function resumeSimulation() {
  try {
    await controlApi.resume()
    message.success('Simulation resumed')
    loadStatus()
  } catch (e) {
    message.error('Failed to resume')
  }
}

async function stopSimulation() {
  try {
    await controlApi.stop()
    message.warning('Simulation stopped')
    loadStatus()
    // Generate new default name for next simulation
    simulationName.value = generateDefaultName()
  } catch (e) {
    message.error('Failed to stop')
  }
}

async function applySpeed(multiplier: number) {
  try {
    // Calculate actual delay: baseDelay / multiplier (0 = no delay)
    const actualMultiplier = multiplier === 0 ? 1000 : multiplier // 0 means MAX speed
    await controlApi.setSpeed(actualMultiplier)
    currentSpeed.value = multiplier
    if (multiplier === 0) {
      message.success('Speed set to MAX (no delay)')
    } else {
      message.success(`Speed set to ${multiplier}x`)
    }
  } catch (e) {
    message.error('Failed to set speed')
  }
}

async function injectEvent() {
  try {
    const { type, targetDay, liquidity, risk, sector, value } = newEvent.value
    
    switch (type) {
      case 'rate-cut':
        await controlApi.injectRateCut(targetDay, liquidity, risk)
        break
      case 'rate-hike':
        await controlApi.injectRateHike(targetDay, liquidity, risk)
        break
      case 'sector-sentiment':
        await controlApi.injectSectorSentiment(targetDay, sector, value)
        break
      case 'sector-fundamental':
        await controlApi.injectSectorFundamental(targetDay, sector, value)
        break
    }
    
    message.success('Event injected!')
    loadPendingEvents()
  } catch (e: any) {
    message.error(e.response?.data?.error || 'Failed to inject event')
  }
}

async function cancelEvent(eventId: string) {
  try {
    await controlApi.cancelEvent(eventId)
    message.info('Event cancelled')
    loadPendingEvents()
  } catch (e) {
    message.error('Failed to cancel event')
  }
}

// Lifecycle
onMounted(() => {
  loadStatus()
  loadDefaultConfig()
  loadPendingEvents()
  loadEventHistory()
  
  // Poll status every 500ms
  pollInterval = window.setInterval(() => {
    loadStatus()
    if (isRunning.value) {
      loadPendingEvents()
      loadEventHistory()
    }
  }, 500)
})

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval)
  }
})
</script>

<style scoped>
.control-page {
  padding: 16px;
}

.control-card {
  margin-bottom: 16px;
}

.control-tabs {
  margin-top: 16px;
}
</style>
