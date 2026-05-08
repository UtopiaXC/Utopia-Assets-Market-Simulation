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
        
        <!-- Speed Control -->
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
        </n-space>
        
        <!-- Real-time Stats -->
        <n-grid :cols="5" :x-gap="12" v-if="hasSession">
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
          <n-gi>
            <n-statistic label="Circuit Breaker">
              <n-tag :type="metrics.circuitBreakerTriggered ? 'error' : 'success'" size="small">
                {{ metrics.circuitBreakerTriggered ? 'TRIGGERED' : 'Normal' }}
              </n-tag>
            </n-statistic>
          </n-gi>
        </n-grid>
      </n-space>
    </n-card>
    
    <!-- Tabs for Config and Policy -->
    <n-tabs type="card" class="control-tabs">
      <!-- Configuration Tab -->
      <n-tab-pane name="config" tab="Configuration">
        <n-card>
          <n-form label-placement="left" label-width="180px">
            <n-grid :cols="2" :x-gap="24">
              <n-gi>
                <n-form-item label="Scenario Mode">
                  <n-select v-model:value="selectedScenario" :options="scenarioOptions" :disabled="hasSession" />
                </n-form-item>
              </n-gi>
              <n-gi></n-gi>
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
      
      <!-- Policy Slot Tab -->
      <n-tab-pane name="policy" tab="Policy Slots (P)">
        <n-grid :cols="2" :x-gap="24">
          <!-- Price Limits -->
          <n-gi>
            <n-card title="Price Limits (L_limit)" size="small">
              <n-form label-placement="top" style="margin-top: 12px;">
                <n-form-item label="Price Limit Ratio">
                  <n-space align="center">
                    <n-slider v-model:value="policy.priceLimitRatio" :min="0" :max="0.20" :step="0.01" style="width: 200px" :disabled="hasSession" />
                    <n-tag type="primary">±{{ (policy.priceLimitRatio * 100).toFixed(0) }}%</n-tag>
                  </n-space>
                </n-form-item>
              </n-form>
            </n-card>
          </n-gi>
          
          <!-- Circuit Breakers -->
          <n-gi>
            <n-card title="Circuit Breakers (Th_halt)" size="small">
              <n-form label-placement="top" style="margin-top: 12px;">
                <n-form-item label="Circuit Breaker Threshold">
                  <n-space align="center">
                    <n-slider v-model:value="policy.circuitBreakerThreshold" :min="0" :max="0.15" :step="0.005" style="width: 200px" :disabled="hasSession" />
                    <n-tag type="error">±{{ (policy.circuitBreakerThreshold * 100).toFixed(1) }}%</n-tag>
                  </n-space>
                </n-form-item>
              </n-form>
            </n-card>
          </n-gi>
          
          <!-- Leverage Restrictions -->
          <n-gi>
            <n-card title="Leverage Restrictions (Lev_max)" size="small">
              <n-form label-placement="top" style="margin-top: 12px;">
                <n-form-item label="Max Leverage Ratio">
                  <n-space align="center">
                    <n-slider v-model:value="policy.maxLeverageRatio" :min="1" :max="10" :step="0.5" style="width: 200px" :disabled="hasSession" />
                    <n-tag type="warning">{{ policy.maxLeverageRatio.toFixed(1) }}x</n-tag>
                  </n-space>
                </n-form-item>
              </n-form>
            </n-card>
          </n-gi>
          
          <!-- Settlement Limits -->
          <n-gi>
            <n-card title="Settlement Limits (N_settle)" size="small">
              <n-form label-placement="top" style="margin-top: 12px;">
                <n-form-item label="Settlement Days">
                  <n-space align="center">
                    <n-input-number v-model:value="policy.settlementDays" :min="0" :max="5" :step="1" style="width: 120px" :disabled="hasSession" />
                    <n-tag type="info">T+{{ policy.settlementDays }}</n-tag>
                  </n-space>
                </n-form-item>
              </n-form>
            </n-card>
          </n-gi>
        </n-grid>
        
        <!-- Leverage Metrics (when running) -->
        <n-card title="Leverage Metrics" size="small" style="margin-top: 16px;" v-if="hasSession">
          <n-grid :cols="3" :x-gap="16">
            <n-gi>
              <n-statistic label="Total Margin Calls">
                {{ metrics.totalMarginCalls || 0 }}
              </n-statistic>
            </n-gi>
            <n-gi>
              <n-statistic label="Total Forced Liquidations">
                {{ metrics.totalForcedLiquidations || 0 }}
              </n-statistic>
            </n-gi>
            <n-gi>
              <n-statistic label="Social Wealth Pool">
                {{ formatNumber(metrics.socialWealthPool || 0) }}
              </n-statistic>
            </n-gi>
          </n-grid>
        </n-card>
      </n-tab-pane>
      
      <!-- Scheduled Events Tab -->
      <n-tab-pane name="events" tab="Scheduled Events">
        <n-alert type="info" title="Dynamic Policy Events" style="margin-bottom: 16px;">
          Schedule future policy changes. Do not forget to include events from the script.
        </n-alert>
        
        <n-card title="Inject Policy Event" size="small">
          <n-form label-placement="left" label-width="140px">
            <n-grid :cols="2" :x-gap="24">
              <n-gi>
                <n-form-item label="Target Day">
                  <n-input-number v-model:value="eventForm.day" :min="Math.max(1, status.currentDay + 1)" :max="config.simulationDays" style="width: 100%" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Policy Type">
                  <n-select v-model:value="eventForm.policyType" :options="policyTypeOptions" />
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Value">
                  <span v-if="eventForm.policyType === 'PRICE_LIMIT' || eventForm.policyType === 'CIRCUIT_BREAKER'">
                    <n-slider v-model:value="eventForm.value" :min="0" :max="0.20" :step="0.005" style="width: 120px; display: inline-block; margin-right: 12px;"/>
                    <n-tag>{{ (eventForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="eventForm.policyType === 'LEVERAGE'">
                    <n-slider v-model:value="eventForm.value" :min="1" :max="10" :step="0.5" style="width: 120px; display: inline-block; margin-right: 12px;"/>
                    <n-tag>{{ eventForm.value.toFixed(1) }}x</n-tag>
                  </span>
                  <span v-else-if="eventForm.policyType === 'SETTLEMENT'">
                    <n-input-number v-model:value="eventForm.value" :min="0" :max="5" :step="1" style="width: 120px"/>
                  </span>
                </n-form-item>
              </n-gi>
              <n-gi>
                <n-form-item label="Description">
                  <n-input v-model:value="eventForm.description" placeholder="Optional description..." />
                </n-form-item>
              </n-gi>
            </n-grid>
            <n-space justify="end">
              <n-button type="primary" @click="injectEvent" :disabled="!hasSession">
                Inject Event
              </n-button>
            </n-space>
          </n-form>
        </n-card>
        
        <!-- Injected Events List -->
        <n-card title="Injected Events" size="small" style="margin-top: 16px;" v-if="injectedEvents.length > 0">
          <n-data-table :columns="eventColumns" :data="injectedEvents" :bordered="false" size="small" />
        </n-card>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import {
  NCard, NSpace, NButton, NButtonGroup, NProgress, NText, NIcon, NTag,
  NTabs, NTabPane, NForm, NFormItem, NInputNumber, NSelect, NSlider,
  NGrid, NGi, NStatistic, NInput, NAlert, NDivider, NDataTable,
  useMessage
} from 'naive-ui'
import {
  Play, Pause, Stop, PlayCircle
} from '@vicons/ionicons5'
import { controlApi, scenarioApi } from '@/services/api'

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

const metrics = ref({
  circuitBreakerTriggered: false,
  totalMarginCalls: 0,
  totalForcedLiquidations: 0,
  socialWealthPool: 0
} as Record<string, any>)

const config = ref({
  numStocks: 50,
  simulationDays: 1000,
  totalAgents: 5000,
  institutionalRatio: 0.05,
  indexBase: 3000,
  stepsPerDay: 22,
  logSampleInterval: 1,
  holdingsSnapshotInterval: 10
})

// Policy slots logic is removed from apply methods as requested
// Default applied on start via policy payload
const policy = ref({
  priceLimitRatio: 0.10,
  circuitBreakerThreshold: 0.07,
  maxLeverageRatio: 2.0,
  settlementDays: 1
})

// Speed control
const stepDelay = ref(100)
const currentSpeed = ref(1)
const speedPresets = [0.1, 1, 5, 10, 50, 100]

// Event scenario selection
const selectedScenario = ref('DefaultScenario')
const scenarioOptions = ref<{label: string, value: string}[]>([])

async function loadScenarios() {
  try {
    const data = await scenarioApi.list() as any[]
    scenarioOptions.value = data.map(s => ({
      label: `${s.name} - ${s.description}`,
      value: s.name
    }))
  } catch (e) {
    console.error('Failed to load scenarios')
  }
}

watch(selectedScenario, (val) => {
  config.value.scenarioName = val
})

// Step Calculation
const minutesPerStep = ref(15)
watch(minutesPerStep, (val) => {
  if (val > 0) {
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

// Format helpers
function formatNumber(n: number) {
  if (n >= 1e12) return (n / 1e12).toFixed(2) + 'T'
  if (n >= 1e9) return (n / 1e9).toFixed(2) + 'B'
  if (n >= 1e6) return (n / 1e6).toFixed(2) + 'M'
  if (n >= 1e3) return (n / 1e3).toFixed(2) + 'K'
  return n.toFixed(0)
}

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

async function loadMetrics() {
  try {
    const data = await controlApi.getMetrics() as any
    metrics.value = data
  } catch (e) {
    // Silent fail
  }
}

async function loadPolicy() {
  try {
    const data = await controlApi.getCurrentPolicy() as any
    policy.value = { ...policy.value, ...data }
  } catch (e) {
    // Silent fail
  }
}

async function loadDefaultConfig() {
  try {
    const data = await controlApi.getDefaultConfig() as any
    config.value = { ...config.value, ...data }
    selectedScenario.value = config.value.scenarioName || 'DefaultScenario'
    minutesPerStep.value = 15
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
      ...policy.value,
      simulationName: simulationName.value,
      stepDelay: stepDelay.value,
      initialEvents: injectedEvents.value.map(e => ({
        day: e.day,
        policyType: e.policyType,
        value: e.value,
        description: e.description
      }))
    }
    await controlApi.start(configWithName)
    message.success('Simulation started!')
    loadStatus()
    loadPolicy()
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
    simulationName.value = generateDefaultName()
  } catch (e) {
    message.error('Failed to stop')
  }
}

async function applySpeed(multiplier: number) {
  try {
    const actualMultiplier = multiplier === 0 ? 1000 : multiplier
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

// Event injection
const eventForm = ref({
  day: 100,
  policyType: 'CIRCUIT_BREAKER',
  value: 0.05,
  description: ''
})

const injectedEvents = ref<any[]>([])

const policyTypeOptions = [
  { label: 'Price Limit (L_limit)', value: 'PRICE_LIMIT' },
  { label: 'Circuit Breaker (Th_halt)', value: 'CIRCUIT_BREAKER' },
  { label: 'Leverage (Lev_max)', value: 'LEVERAGE' },
  { label: 'Settlement (N_settle)', value: 'SETTLEMENT' }
]

const eventColumns = [
  { title: 'Day', key: 'day', width: 80 },
  { title: 'Type', key: 'policyType', width: 140 },
  { title: 'Value', key: 'value', width: 100 },
  { title: 'Description', key: 'description' },
  { title: 'Source/Status', key: 'status', width: 120 }
]

async function injectEvent() {
  try {
    await controlApi.injectPolicyEvent(
      eventForm.value.day,
      eventForm.value.policyType,
      eventForm.value.value,
      eventForm.value.description || undefined
    )
    injectedEvents.value.push({
      ...eventForm.value,
      description: eventForm.value.description || `${eventForm.value.policyType} -> ${eventForm.value.value}`,
      status: 'Injected'
    })
    message.success(`Event scheduled: ${eventForm.value.policyType} on Day ${eventForm.value.day}`)
  } catch (e: any) {
    message.error('Failed to inject event: ' + (e.response?.data?.error || e.message))
  }
}

// Lifecycle
onMounted(() => {
  loadScenarios()
  loadStatus()
  loadDefaultConfig()
  loadPolicy()
  
  pollInterval = window.setInterval(() => {
    loadStatus()
    if (isRunning.value) {
      loadMetrics()
      loadPolicy()
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
