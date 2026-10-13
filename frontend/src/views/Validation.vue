<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import {
  NCard,
  NGrid,
  NGridItem,
  NSelect,
  NSpace,
  NButton,
  NSpin,
  NStatistic,
  NTag,
  NAlert,
  NDivider,
  NText,
  NIcon
} from 'naive-ui'
import { RefreshOutline, AnalyticsOutline } from '@vicons/ionicons5'
import VChart from 'vue-echarts'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { ScatterChart, LineChart, BarChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  DataZoomComponent
} from 'echarts/components'
import { useSimulationStore } from '@/stores/simulation'
import { validationApi } from '@/services/api'
import { useCancellableFetch } from '@/composables/useCancellableFetch'
import { formatAxisNumber } from '@/utils/format'

// Register ECharts modules
use([
  CanvasRenderer,
  ScatterChart,
  LineChart,
  BarChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  MarkLineComponent,
  DataZoomComponent
])

const store = useSimulationStore()
const activeModule = ref<'fat_tails' | 'volatility_clustering'>('fat_tails')
const selectedStockId = ref<number | null>(-1)
const logScaleDensity = ref(true)

const { loading, data: validationData, fetch: fetchValidation } = useCancellableFetch<any>()

// Module options for top dropdown
const moduleOptions = [
  { label: 'Fat Tails Validation', value: 'fat_tails' },
  { label: 'Volatility Clustering Validation', value: 'volatility_clustering' }
]

// Stock options for dropdown
const stockSelectOptions = computed(() => {
  const options = [
    { label: 'Market Composite Index', value: -1 }
  ]
  if (validationData.value?.availableStocks) {
    validationData.value.availableStocks.forEach((s: any) => {
      options.push({
        label: s.name || s.code,
        value: s.id
      })
    })
  }
  return options
})

// Load data
async function loadData() {
  if (!store.currentSimulation) return
  await fetchValidation((signal) =>
    validationApi.getStylizedFacts(
      store.currentSimulation!,
      selectedStockId.value !== null && selectedStockId.value >= 0 ? selectedStockId.value : undefined,
      signal
    )
  )
}

watch(() => store.currentSimulation, () => {
  selectedStockId.value = -1
  loadData()
})

watch(selectedStockId, () => {
  loadData()
})

onMounted(() => {
  selectedStockId.value = -1
  loadData()
})

// ============================================================
// FAT TAILS CHARTS
// ============================================================

// 1. Normal Q-Q Plot
const qqPlotOption = computed(() => {
  const ft = validationData.value?.fatTails
  if (!ft || !ft.qqPlotPoints || ft.qqPlotPoints.length === 0) return {}

  const points = ft.qqPlotPoints
  const minVal = ft.qqReferenceLine?.[0] ?? -3.5
  const maxVal = ft.qqReferenceLine?.[1] ?? 3.5

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'Normal Q-Q Plot (Fat Tail Inverted S-Curve)',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'scatter') {
          return `Theoretical: ${params.value[0]?.toFixed(4)}<br/>Empirical (Std): ${params.value[1]?.toFixed(4)}`
        }
        return ''
      }
    },
    grid: {
      left: 55,
      right: 25,
      top: 50,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      name: 'Theoretical Quantile',
      nameLocation: 'middle',
      nameGap: 26,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    yAxis: {
      type: 'value',
      name: 'Standardized Sample Quantile',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: '45° Reference',
        type: 'line',
        data: [
          [minVal, minVal],
          [maxVal, maxVal]
        ],
        symbol: 'none',
        lineStyle: {
          color: '#e03131',
          width: 2,
          type: 'dashed'
        },
        silent: true
      },
      {
        name: 'Sample Quantiles',
        type: 'scatter',
        data: points,
        symbolSize: 5,
        itemStyle: {
          color: '#2080f0',
          opacity: 0.75
        }
      }
    ]
  }
})

// 2. Log Return Density vs Normal Fit
const densityPlotOption = computed(() => {
  const ft = validationData.value?.fatTails
  if (!ft || !ft.histogramBins || ft.histogramBins.length === 0) return {}

  const bins = ft.histogramBins
  const isLog = logScaleDensity.value

  const categories = bins.map((b: any) => {
    const pct = b.binCenter * 100
    if (Math.abs(pct) < 0.001) return '0.0%'
    return (pct > 0 ? '+' : '') + pct.toFixed(2) + '%'
  })

  const sampleData = bins.map((b: any) => {
    if (isLog) {
      return b.count > 0 ? b.density : null
    }
    return b.density
  })

  const normalData = bins.map((b: any) => {
    if (isLog) {
      return Math.max(1e-4, b.normalDensity ?? 0)
    }
    return b.normalDensity ?? 0
  })

  let minPositive = 1.0
  bins.forEach((b: any) => {
    if (b.density > 0 && b.density < minPositive) {
      minPositive = b.density
    }
  })
  const yMin = isLog ? Math.max(0.01, Math.pow(10, Math.floor(Math.log10(minPositive)))) : 0

  return {
    backgroundColor: 'transparent',
    title: {
      text: isLog
        ? 'Log Return Density (Semi-Log Scale: Fat Tails Above Normal)'
        : 'Log Return Distribution vs Fitted Normal N(μ, σ²)',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const dataIndex = params[0].dataIndex
        const bin = bins[dataIndex]
        if (!bin) return ''
        const rangeStr = `[${(bin.binStart * 100).toFixed(3)}%, ${(bin.binEnd * 100).toFixed(3)}%]`
        let html = `<strong>Bin: ${rangeStr}</strong><br/>`
        html += `Count: ${bin.count} (${((bin.count / (validationData.value?.sampleSize || 1)) * 100).toFixed(2)}%)<br/>`
        html += `Sample Density: ${bin.count > 0 ? bin.density.toFixed(2) : '0'}<br/>`
        html += `Normal Fit: ${(bin.normalDensity ?? 0).toFixed(2)}`
        return html
      }
    },
    legend: {
      data: ['Sample Density', 'Normal Fit N(μ, σ²)'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: 'Return',
      nameLocation: 'middle',
      nameGap: 26,
      data: categories,
      axisLabel: {
        interval: Math.max(1, Math.floor(bins.length / 7))
      }
    },
    yAxis: {
      type: isLog ? 'log' : 'value',
      name: isLog ? 'Density (Log₁₀)' : 'Density',
      logBase: 10,
      min: isLog ? yMin : 0,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: 'Sample Density',
        type: 'bar',
        barWidth: '75%',
        data: sampleData,
        itemStyle: {
          color: 'rgba(32, 128, 240, 0.45)',
          borderColor: '#2080f0',
          borderWidth: 1
        }
      },
      {
        name: 'Normal Fit N(μ, σ²)',
        type: 'line',
        data: normalData,
        smooth: true,
        symbol: 'none',
        lineStyle: {
          color: '#e03131',
          width: 2.2,
          type: 'dashed'
        }
      }
    ]
  }
})

// 3. Tail CCDF (Log-Log Complementary Cumulative Distribution)
const tailCcdfOption = computed(() => {
  const ft = validationData.value?.fatTails
  if (!ft || !ft.tailCcdfPoints || ft.tailCcdfPoints.length === 0) return {}

  const samplePoints = ft.tailCcdfPoints.map((p: any) => [p[0], p[1]])
  const normalPoints = ft.tailCcdfPoints.map((p: any) => [p[0], p[2]])

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'Tail Distribution P(|R| > x) on Log-Log Scale',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross' },
      formatter: (params: any) => {
        if (!params || params.length === 0) return ''
        const xVal = params[0].value[0]
        let html = `<strong>Return Magnitude |R|: ${(xVal * 100).toFixed(3)}%</strong><br/>`
        params.forEach((item: any) => {
          html += `${item.seriesName}: ${item.value[1]?.toExponential(3)}<br/>`
        })
        return html
      }
    },
    legend: {
      data: ['Empirical CCDF', 'Normal Gaussian CCDF'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'log',
      name: '|R| (Log₁₀)',
      nameLocation: 'middle',
      nameGap: 26,
      logBase: 10,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    yAxis: {
      type: 'log',
      name: 'P(|R| > x) (Log₁₀)',
      logBase: 10,
      min: 1e-4,
      max: 1,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: 'Empirical CCDF',
        type: 'line',
        data: samplePoints,
        symbolSize: 4,
        lineStyle: { width: 2.2, color: '#2080f0' },
        itemStyle: { color: '#2080f0' }
      },
      {
        name: 'Normal Gaussian CCDF',
        type: 'line',
        data: normalPoints,
        symbol: 'none',
        lineStyle: { width: 2, color: '#e03131', type: 'dashed' },
        itemStyle: { color: '#e03131' }
      }
    ]
  }
})

// 4. Daily Return Time Series & Outliers Timeline
const returnTimelineOption = computed(() => {
  const ft = validationData.value?.fatTails
  if (!ft || !ft.returnsTimeline || ft.returnsTimeline.length === 0) return {}

  const normalPts: any[] = []
  const outlierPts: any[] = []

  ft.returnsTimeline.forEach((pt: any) => {
    if (pt.isOutlier) {
      outlierPts.push([pt.day, pt.value])
    } else {
      normalPts.push([pt.day, pt.value])
    }
  })

  const threshold = 3.0 * (ft.stdDev || 0)

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'Daily Log Returns & Extreme Outlier Shocks (> 3σ)',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        return `Day ${params.value[0]}<br/>Return: ${(params.value[1] * 100).toFixed(4)}%`
      }
    },
    legend: {
      data: ['Regular Returns', 'Extreme Outliers (> 3σ)'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      name: 'Trading Day',
      nameLocation: 'middle',
      nameGap: 26,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    yAxis: {
      type: 'value',
      name: 'Log Return (R_t)',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => (v * 100).toFixed(2) + '%' }
    },
    series: [
      {
        name: 'Regular Returns',
        type: 'scatter',
        data: normalPts,
        symbolSize: 3.5,
        itemStyle: { color: 'rgba(32, 128, 240, 0.65)' },
        markLine: {
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#e03131', width: 1.5 },
          data: [
            { yAxis: threshold, name: '+3σ' },
            { yAxis: -threshold, name: '-3σ' },
            { yAxis: 0, lineStyle: { color: '#aaa', type: 'solid' } }
          ]
        }
      },
      {
        name: 'Extreme Outliers (> 3σ)',
        type: 'scatter',
        data: outlierPts,
        symbolSize: 7,
        itemStyle: { color: '#e03131', borderColor: '#ffffff', borderWidth: 1 }
      }
    ]
  }
})

// ============================================================
// VOLATILITY CLUSTERING CHARTS
// ============================================================

// 1. ACF of Raw Returns
const rawAcfOption = computed(() => {
  const vc = validationData.value?.volatilityClustering
  if (!vc || !vc.lags || vc.lags.length === 0) return {}

  const lags = vc.lags
  const acf = vc.rawReturnsAcf || []
  const upperBand = vc.upperConfidenceBand
  const lowerBand = vc.lowerConfidenceBand

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'ACF of Raw Returns (Weak-form Market Efficiency)',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' }
    },
    legend: {
      data: ['Raw Return ACF'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: 'Lag (Days)',
      nameLocation: 'middle',
      nameGap: 26,
      data: lags,
      axisLabel: { interval: 4 }
    },
    yAxis: {
      type: 'value',
      name: 'Autocorrelation',
      min: -0.4,
      max: 0.6,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: 'Raw Return ACF',
        type: 'bar',
        data: acf,
        barMaxWidth: 12,
        itemStyle: {
          color: (params: any) => {
            const val = params.value
            return val > upperBand || val < lowerBand ? '#e03131' : '#2080f0'
          }
        },
        markLine: {
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#888' },
          data: [
            { yAxis: upperBand, name: '+1.96/√N' },
            { yAxis: lowerBand, name: '-1.96/√N' },
            { yAxis: 0, lineStyle: { color: '#aaa', type: 'solid' } }
          ]
        }
      }
    ]
  }
})

// 2. ACF of Absolute & Squared Returns
const absAcfOption = computed(() => {
  const vc = validationData.value?.volatilityClustering
  if (!vc || !vc.lags || vc.lags.length === 0) return {}

  const lags = vc.lags
  const absAcf = vc.absReturnsAcf || []
  const sqAcf = vc.squaredReturnsAcf || []
  const upperBand = vc.upperConfidenceBand

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'ACF of Absolute & Squared Returns (Long Memory)',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['Absolute |R_t| ACF', 'Squared R_t² ACF'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      name: 'Lag (Days)',
      nameLocation: 'middle',
      nameGap: 26,
      data: lags,
      axisLabel: { interval: 4 }
    },
    yAxis: {
      type: 'value',
      name: 'Autocorrelation',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: 'Absolute |R_t| ACF',
        type: 'line',
        data: absAcf,
        smooth: true,
        symbolSize: 4,
        lineStyle: { width: 2.2, color: '#18a058' },
        itemStyle: { color: '#18a058' },
        markLine: {
          symbol: 'none',
          lineStyle: { type: 'dashed', color: '#888' },
          data: [
            { yAxis: upperBand, name: '+1.96/√N' },
            { yAxis: 0, lineStyle: { color: '#aaa', type: 'solid' } }
          ]
        }
      },
      {
        name: 'Squared R_t² ACF',
        type: 'line',
        data: sqAcf,
        smooth: true,
        symbolSize: 4,
        lineStyle: { width: 1.8, color: '#f59e0b', type: 'dashed' },
        itemStyle: { color: '#f59e0b' }
      }
    ]
  }
})

// 3. Conditional Volatility Dynamics Timeline
const volTimelineOption = computed(() => {
  const vc = validationData.value?.volatilityClustering
  if (!vc || !vc.volatilityTimeline || vc.volatilityTimeline.length === 0) return {}

  const absPoints = vc.volatilityTimeline.map((p: any) => [p.day, p.absReturn])
  const rollPoints = vc.volatilityTimeline.map((p: any) => [p.day, p.rollingVol])

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'Volatility Timeline: Daily |R_t| vs 20-Day Rolling Volatility',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      data: ['Daily |R_t|', '20-Day Rolling Volatility'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      name: 'Trading Day',
      nameLocation: 'middle',
      nameGap: 26,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    yAxis: {
      type: 'value',
      name: 'Volatility Magnitude',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => (v * 100).toFixed(2) + '%' }
    },
    series: [
      {
        name: 'Daily |R_t|',
        type: 'bar',
        barMaxWidth: 6,
        data: absPoints,
        itemStyle: { color: 'rgba(150, 150, 150, 0.4)' }
      },
      {
        name: '20-Day Rolling Volatility',
        type: 'line',
        data: rollPoints,
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 2.2, color: '#18a058' }
      }
    ]
  }
})

// 4. ARCH Lag-1 Phase Space & Regression Fit
const archScatterOption = computed(() => {
  const vc = validationData.value?.volatilityClustering
  if (!vc || !vc.archScatterPoints || vc.archScatterPoints.length === 0) return {}

  const points = vc.archScatterPoints
  const trend = vc.archTrendLine || []

  return {
    backgroundColor: 'transparent',
    title: {
      text: 'ARCH Lag-1 Phase Plot: e_{t}^2 vs e_{t-1}^2',
      left: 10,
      top: 10,
      textStyle: { fontSize: 13, fontWeight: 'bold' }
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesType === 'scatter') {
          return `e_{t-1}²: ${params.value[0]?.toExponential(3)}<br/>e_t²: ${params.value[1]?.toExponential(3)}`
        }
        return ''
      }
    },
    legend: {
      data: ['Variance Phase Points', 'OLS Trend Line'],
      top: 12,
      right: 15
    },
    grid: {
      left: 55,
      right: 25,
      top: 55,
      bottom: 45,
      containLabel: true
    },
    xAxis: {
      type: 'value',
      name: 'Lag-1 Squared Residual (e_{t-1}^2)',
      nameLocation: 'middle',
      nameGap: 26,
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    yAxis: {
      type: 'value',
      name: 'Current Squared Residual (e_t^2)',
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.25 } },
      axisLabel: { formatter: (v: number) => formatAxisNumber(v) }
    },
    series: [
      {
        name: 'Variance Phase Points',
        type: 'scatter',
        data: points,
        symbolSize: 4.5,
        itemStyle: { color: 'rgba(245, 158, 11, 0.7)' }
      },
      {
        name: 'OLS Trend Line',
        type: 'line',
        data: trend,
        symbol: 'none',
        lineStyle: { width: 2, color: '#e03131', type: 'dashed' },
        silent: true
      }
    ]
  }
})
</script>

<template>
  <div class="validation-container">
    <!-- Header Control Bar -->
    <NCard class="header-card" :bordered="false" size="small">
      <div class="header-wrapper">
        <div class="header-left">
          <NSpace align="center" size="small">
            <h2 class="title">Stylized Facts Validation</h2>
          </NSpace>
        </div>
        <div class="header-right">
          <NSpace align="center" size="medium">
            <NSelect
              v-model:value="activeModule"
              :options="moduleOptions"
              style="width: 240px;"
            />
            <NSelect
              v-model:value="selectedStockId"
              :options="stockSelectOptions"
              style="width: 240px;"
              placeholder="Select Asset Series"
            />
            <NButton type="primary" secondary @click="loadData">
              <template #icon>
                <NIcon><RefreshOutline /></NIcon>
              </template>
              Refresh
            </NButton>
          </NSpace>
        </div>
      </div>
    </NCard>

    <!-- Main Content Container with Loading Spinner -->
    <NSpin :show="loading">
      <div class="content-body" style="min-height: 520px;">
        <!-- Overview Status Bar -->
        <NCard v-if="validationData" class="overview-bar" size="small" :bordered="false">
          <NSpace justify="space-between" align="center">
            <NSpace align="center" size="large">
              <span><strong>Target:</strong> {{ validationData.targetName }}</span>
              <span><strong>Total Days:</strong> {{ validationData.totalDays }}</span>
              <span><strong>Sample Size (Returns):</strong> {{ validationData.sampleSize }}</span>
            </NSpace>
            <NSpace align="center">
              <NButton
                v-if="activeModule === 'fat_tails'"
                size="small"
                secondary
                :type="logScaleDensity ? 'primary' : 'default'"
                @click="logScaleDensity = !logScaleDensity"
              >
                {{ logScaleDensity ? 'Log Scale Density: ON' : 'Log Scale Density: OFF' }}
              </NButton>
            </NSpace>
          </NSpace>
        </NCard>

        <!-- ========================================== -->
        <!-- MODULE 1: FAT TAILS VALIDATION             -->
        <!-- ========================================== -->
        <template v-if="activeModule === 'fat_tails'">
          <!-- Fat Tails KPI Card -->
          <NCard title="Part I: Fat Tails (Excess Kurtosis & Normality)" :bordered="false" class="stat-card" style="margin-top: 14px;">
            <template #header-extra>
              <NTag
                :type="validationData?.fatTails?.isFatTail ? 'success' : 'warning'"
                round
                size="small"
              >
                {{ validationData?.fatTails?.isFatTail ? 'Fat Tails Confirmed' : 'Normal / Weak Tails' }}
              </NTag>
            </template>
            <NGrid :cols="7" :x-gap="12" :y-gap="12">
              <NGridItem>
                <NStatistic label="Excess Kurtosis">
                  <template #default>
                    <NText :type="validationData?.fatTails?.excessKurtosis > 0 ? 'success' : 'default'">
                      {{ validationData?.fatTails?.excessKurtosis?.toFixed(4) ?? '-' }}
                    </NText>
                  </template>
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Jarque-Bera Stat">
                  {{ validationData?.fatTails?.jbStat?.toFixed(2) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="JB Test p-value">
                  <template #default>
                    <NText :type="validationData?.fatTails?.jbPValue < 0.05 ? 'error' : 'default'">
                      {{ validationData?.fatTails?.jbPValue < 1e-4 ? '< 0.0001' : validationData?.fatTails?.jbPValue?.toFixed(4) ?? '-' }}
                    </NText>
                  </template>
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Skewness">
                  {{ validationData?.fatTails?.skewness?.toFixed(4) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Mean Return">
                  {{ validationData?.fatTails?.mean?.toFixed(6) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Volatility (Std Dev)">
                  {{ validationData?.fatTails?.stdDev?.toFixed(6) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Outliers (> 3σ)">
                  <template #default>
                    <NText :type="validationData?.fatTails?.outlierCount > 0 ? 'warning' : 'default'">
                      {{ validationData?.fatTails?.outlierCount ?? 0 }}
                    </NText>
                  </template>
                </NStatistic>
              </NGridItem>
            </NGrid>
            <NDivider style="margin: 12px 0;" />
            <NAlert
              :type="validationData?.fatTails?.isFatTail ? 'success' : 'info'"
              :bordered="false"
              size="small"
            >
              {{ validationData?.fatTails?.verdict || 'Calculating statistics...' }}
            </NAlert>
          </NCard>

          <!-- Fat Tails 4 Visualizations (2x2 Grid) -->
          <NGrid :cols="24" :x-gap="16" :y-gap="16" style="margin-top: 16px;">
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="qqPlotOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="densityPlotOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="tailCcdfOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="returnTimelineOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
          </NGrid>
        </template>

        <!-- ========================================== -->
        <!-- MODULE 2: VOLATILITY CLUSTERING VALIDATION -->
        <!-- ========================================== -->
        <template v-else-if="activeModule === 'volatility_clustering'">
          <!-- Volatility Clustering KPI Card -->
          <NCard title="Part II: Volatility Clustering (ARCH Effect & Memory)" :bordered="false" class="stat-card" style="margin-top: 14px;">
            <template #header-extra>
              <NTag
                :type="validationData?.volatilityClustering?.hasArchEffect ? 'success' : 'warning'"
                round
                size="small"
              >
                {{ validationData?.volatilityClustering?.hasArchEffect ? 'Clustering Confirmed' : 'No ARCH Effect' }}
              </NTag>
            </template>
            <NGrid :cols="6" :x-gap="12" :y-gap="12">
              <NGridItem>
                <NStatistic label="ARCH-LM Stat">
                  {{ validationData?.volatilityClustering?.archLmStat?.toFixed(2) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="ARCH-LM p-value">
                  <template #default>
                    <NText :type="validationData?.volatilityClustering?.archLmPValue < 0.05 ? 'error' : 'default'">
                      {{ validationData?.volatilityClustering?.archLmPValue < 1e-4 ? '< 0.0001' : validationData?.volatilityClustering?.archLmPValue?.toFixed(4) ?? '-' }}
                    </NText>
                  </template>
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="ARCH Test Lags">
                  {{ validationData?.volatilityClustering?.archLags ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Raw Return ACF(1)">
                  {{ validationData?.volatilityClustering?.rawReturnsAcf?.[0]?.toFixed(4) ?? '-' }}
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="Absolute ACF(1)">
                  <template #default>
                    <NText type="success">
                      {{ validationData?.volatilityClustering?.absReturnsAcf?.[0]?.toFixed(4) ?? '-' }}
                    </NText>
                  </template>
                </NStatistic>
              </NGridItem>
              <NGridItem>
                <NStatistic label="95% CI Band (±1.96/√N)">
                  ±{{ validationData?.volatilityClustering?.upperConfidenceBand?.toFixed(4) ?? '-' }}
                </NStatistic>
              </NGridItem>
            </NGrid>
            <NDivider style="margin: 12px 0;" />
            <NAlert
              :type="validationData?.volatilityClustering?.hasArchEffect ? 'success' : 'info'"
              :bordered="false"
              size="small"
            >
              {{ validationData?.volatilityClustering?.verdict || 'Calculating statistics...' }}
            </NAlert>
          </NCard>

          <!-- Volatility Clustering 4 Visualizations (2x2 Grid) -->
          <NGrid :cols="24" :x-gap="16" :y-gap="16" style="margin-top: 16px;">
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="rawAcfOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="absAcfOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="volTimelineOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
            <NGridItem :span="12">
              <NCard :bordered="false" class="chart-card">
                <VChart :option="archScatterOption" autoresize style="height: 380px; width: 100%;" />
              </NCard>
            </NGridItem>
          </NGrid>
        </template>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.validation-container {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.header-card {
  border-radius: 8px;
}

.header-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.header-left {
  display: flex;
  align-items: center;
}

.title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}

.overview-bar {
  border-radius: 8px;
  background-color: var(--n-color);
  margin-top: 14px;
}

.stat-card {
  border-radius: 8px;
}

.chart-card {
  border-radius: 8px;
}
</style>
