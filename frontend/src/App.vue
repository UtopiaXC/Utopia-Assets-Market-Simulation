<script setup lang="ts">
import { h, computed, onMounted, ref, watch } from 'vue'
import { RouterView, useRouter, useRoute } from 'vue-router'
import { 
  NLayout, 
  NLayoutSider, 
  NLayoutContent,
  NLayoutHeader,
  NMenu, 
  NSpace,
  NSelect,
  NButton,
  NBadge,
  NSlider,
  NText,
  NIcon,
  NConfigProvider,
  darkTheme,
  lightTheme,
  NCard,
  NDropdown,
  NTooltip,
  NMessageProvider,
  NDialogProvider,
  GlobalThemeOverrides
} from 'naive-ui'
import {
  HomeOutline,
  TrendingUpOutline,
  StatsChartOutline,
  PeopleOutline,
  EarthOutline,
  LayersOutline,
  GitCompareOutline,
  RefreshOutline,
  PlayCircleOutline,
  SunnyOutline,
  MoonOutline,
  DesktopOutline,
  DocumentTextOutline
} from '@vicons/ionicons5'
import { useSimulationStore } from '@/stores/simulation'

const router = useRouter()
const route = useRoute()
const store = useSimulationStore()

// Theme management
type ThemeMode = 'light' | 'dark' | 'system'
const themeMode = ref<ThemeMode>('system')
const systemPrefersDark = ref(false)

// Detect system preference
function detectSystemTheme() {
  systemPrefersDark.value = window.matchMedia('(prefers-color-scheme: dark)').matches
}

// Computed actual theme
const isDark = computed(() => {
  if (themeMode.value === 'system') {
    return systemPrefersDark.value
  }
  return themeMode.value === 'dark'
})

const currentTheme = computed(() => isDark.value ? darkTheme : null)

// Theme overrides for light mode to improve readability
const themeOverrides = computed<GlobalThemeOverrides>(() => {
  if (isDark.value) {
    return {}
  }
  // Light mode overrides
  return {
    common: {
      primaryColor: '#2080f0',
      primaryColorHover: '#4098fc',
      primaryColorPressed: '#1060c9',
      bodyColor: '#f5f7f9',
      cardColor: '#ffffff',
      textColor1: '#1f2328',
      textColor2: '#424a53',
      textColor3: '#636c76',
      borderColor: '#d1d9e0',
      dividerColor: '#d8dee4',
    },
    Card: {
      borderRadius: '12px',
      boxShadow: '0 1px 3px rgba(0,0,0,0.1)',
    },
    Menu: {
      itemTextColor: '#424a53',
      itemIconColor: '#636c76',
      itemTextColorActive: '#2080f0',
      itemIconColorActive: '#2080f0',
    }
  }
})

// Theme toggle options
const themeOptions = [
  { label: 'Light', key: 'light' },
  { label: 'Dark', key: 'dark' },
  { label: 'System', key: 'system' }
]

function handleThemeChange(key: string) {
  themeMode.value = key as ThemeMode
  localStorage.setItem('theme-mode', key)
}

// Current theme icon
const themeIcon = computed(() => {
  if (themeMode.value === 'system') return DesktopOutline
  if (themeMode.value === 'dark') return MoonOutline
  return SunnyOutline
})

// Menu options
const menuOptions = computed(() => [
  {
    label: 'Dashboard',
    key: 'dashboard',
    icon: () => h(NIcon, null, { default: () => h(HomeOutline) })
  },
  {
    label: 'Market Overview',
    key: 'market',
    icon: () => h(NIcon, null, { default: () => h(TrendingUpOutline) }),
    disabled: !store.isConnected
  },
  {
    label: 'Stock Analysis',
    key: 'stocks',
    icon: () => h(NIcon, null, { default: () => h(StatsChartOutline) }),
    disabled: !store.isConnected
  },
  {
    label: 'Trader Analysis',
    key: 'traders',
    icon: () => h(NIcon, null, { default: () => h(PeopleOutline) }),
    disabled: !store.isConnected
  },
  {
    label: 'Macro Statistics',
    key: 'macro',
    icon: () => h(NIcon, null, { default: () => h(EarthOutline) }),
    disabled: !store.isConnected
  },
  {
    label: 'Sectors',
    key: 'sectors',
    icon: () => h(NIcon, null, { default: () => h(LayersOutline) }),
    disabled: !store.isConnected
  },
  {
    type: 'divider',
    key: 'd1'
  },
  {
    label: 'Results',
    key: 'results',
    icon: () => h(NIcon, null, { default: () => h(DocumentTextOutline) })
  },
  {
    label: 'Compare',
    key: 'compare',
    icon: () => h(NIcon, null, { default: () => h(GitCompareOutline) })
  },
  {
    label: 'Run Simulation',
    key: 'control',
    icon: () => h(NIcon, null, { default: () => h(PlayCircleOutline) })
  }
])

const selectedKey = computed(() => {
  const name = route.name as string
  if (name?.startsWith('stock')) return 'stocks'
  if (name?.startsWith('trader')) return 'traders'
  return name || 'dashboard'
})

// Simulation dropdown options
const simulationOptions = computed(() => 
  store.simulations.map(sim => ({
    label: sim.name,
    value: sim.name
  }))
)

function handleMenuUpdate(key: string) {
  router.push({ name: key })
}

async function handleSimulationSelect(value: string) {
  await store.selectSimulation(value)
}

async function refreshSimulations() {
  await store.fetchSimulations()
}

onMounted(async () => {
  // Load saved theme preference
  const savedTheme = localStorage.getItem('theme-mode') as ThemeMode | null
  if (savedTheme) {
    themeMode.value = savedTheme
  }
  
  // Detect system preference
  detectSystemTheme()
  
  // Listen for system theme changes
  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
    systemPrefersDark.value = e.matches
  })
  
  await store.fetchSimulations()
})
</script>

<template>
  <NConfigProvider :theme="currentTheme" :theme-overrides="themeOverrides">
    <NMessageProvider>
      <NDialogProvider>
        <NLayout has-sider style="height: 100vh">
          <!-- Sidebar -->
          <NLayoutSider
            bordered
            collapse-mode="width"
            :collapsed-width="64"
            :width="240"
            style="height: 100vh"
            content-style="display: flex; flex-direction: column;"
          >
            <!-- Logo -->
            <div style="padding: 20px; text-align: center; border-bottom: 1px solid var(--n-border-color)">
              <h2 style="margin: 0; color: var(--n-text-color)">Market Simulation</h2>
            </div>
            
            <!-- Menu -->
            <NMenu
              :options="menuOptions"
              :value="selectedKey"
              @update:value="handleMenuUpdate"
              style="flex: 1"
            />
            
            <!-- Theme Toggle + Version -->
            <div style="padding: 16px; text-align: center; border-top: 1px solid var(--n-border-color)">
              <NDropdown 
                :options="themeOptions" 
                @select="handleThemeChange"
                trigger="click"
              >
                <NButton quaternary circle size="small">
                  <template #icon>
                    <NIcon size="18">
                      <component :is="themeIcon" />
                    </NIcon>
                  </template>
                </NButton>
              </NDropdown>
              <div style="margin-top: 8px; opacity: 0.5">
                <small>v1.0.0</small>
              </div>
            </div>
          </NLayoutSider>
          
          <!-- Main Content -->
          <NLayout>
            <!-- Header -->
            <NLayoutHeader bordered style="padding: 12px 24px; display: flex; align-items: center; gap: 16px;">
              <!-- Simulation Selector -->
              <NSpace align="center" :wrap="false" style="flex: 1">
                <NText>Simulation:</NText>
                <NSelect
                  v-model:value="store.currentSimulation"
                  :options="simulationOptions"
                  placeholder="Select Simulation..."
                  style="width: 300px"
                  :loading="store.loading"
                  @update:value="handleSimulationSelect"
                />
                <NButton quaternary circle @click="refreshSimulations" :loading="store.loading">
                  <template #icon>
                    <NIcon><RefreshOutline /></NIcon>
                  </template>
                </NButton>
                
                <!-- Connection Status -->
                <NBadge 
                  :type="store.isConnected ? 'success' : 'default'"
                  :value="store.isConnected ? 'Connected' : 'Not Connected'"
                />
              </NSpace>
              
              <!-- Day Slider -->
              <NSpace v-if="store.isConnected" align="center" :wrap="false" style="width: 400px">
                <NText>Day {{ store.currentDay }}:</NText>
                <NSlider
                  v-model:value="store.currentDay"
                  :min="1"
                  :max="store.totalDays"
                  :step="1"
                  style="width: 200px"
                />
                <NText style="opacity: 0.6">/ {{ store.totalDays }}</NText>
              </NSpace>
            </NLayoutHeader>
            
            <!-- Content -->
            <NLayoutContent content-style="padding: 24px; background: var(--n-color);">
              <RouterView />
            </NLayoutContent>
          </NLayout>
        </NLayout>
      </NDialogProvider>
    </NMessageProvider>
  </NConfigProvider>
</template>

<style>
body {
  margin: 0;
  padding: 0;
}

/* Disable card hover lift animation globally */
.n-card {
  transition: none !important;
}
.n-card:hover {
  transform: none !important;
  box-shadow: 0 1px 3px rgba(0,0,0,0.1) !important; /* Keep original shadow or make it specific */
}
</style>

