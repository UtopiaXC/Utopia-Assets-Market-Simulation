<template>
  <div class="control-container">
    <!-- 1. Mission Control Header & Status HUD -->
    <n-card class="mission-control-card" :bordered="false">
      <div class="hud-header">
        <div class="hud-title-group">
          <div class="title-with-badge">
            <h2 class="title-text">Simulation Control</h2>
            <n-tag :type="statusTagType" size="medium" round class="status-badge">
              <span class="status-dot" :class="{ 'pulsing': isRunning }"></span>
              {{ status.state }}
            </n-tag>
          </div>
          <div class="hud-subtitle">
            <span>Session: <code>{{ status.sessionId || 'None' }}</code></span>
            <n-divider vertical />
            <span>Target DB: <code>{{ (status.simulationName || simulationName) + '.db' }}</code></span>
          </div>
        </div>

        <!-- Master Actions -->
        <div class="hud-action-group">
          <n-button 
            type="primary" 
            size="large"
            :disabled="isRunning" 
            @click="startSimulation"
            class="start-btn"
          >
            <template #icon><n-icon><Play /></n-icon></template>
            {{ isPaused ? 'Restart' : 'Start Simulation' }}
          </n-button>

          <n-button 
            type="warning" 
            size="large"
            :disabled="!isRunning" 
            @click="pauseSimulation"
          >
            <template #icon><n-icon><Pause /></n-icon></template>
            Pause
          </n-button>

          <n-button 
            type="info" 
            size="large"
            :disabled="!isPaused" 
            @click="resumeSimulation"
          >
            <template #icon><n-icon><Play /></n-icon></template>
            Resume
          </n-button>

          <n-button 
            type="error" 
            size="large"
            :disabled="!hasSession" 
            @click="stopSimulation"
          >
            <template #icon><n-icon><Stop /></n-icon></template>
            Stop
          </n-button>

          <n-button 
            quaternary 
            size="large"
            :disabled="isRunning" 
            @click="resetSession"
            title="Clear session state and ready for a fresh configuration"
          >
            <template #icon><n-icon><Refresh /></n-icon></template>
            Reset
          </n-button>

          <n-button
            v-if="hasSession"
            type="success"
            secondary
            size="large"
            @click="openInAnalytics"
            class="analyze-btn"
          >
            <template #icon><n-icon><BarChart /></n-icon></template>
            Analyze View
          </n-button>
        </div>
      </div>

      <!-- Live Progress & Speed -->
      <div class="progress-section">
        <div class="progress-meta">
          <span class="meta-label">
            <strong>Day {{ status.currentDay }}</strong> / {{ displayTotalDays }} Days
            <span class="step-counter">(Step {{ status.currentStepInDay || 0 }} / {{ status.totalStepsPerDay || config.stepsPerDay }})</span>
          </span>
          <span class="meta-percentage">{{ displayProgress.toFixed(2) }}%</span>
        </div>
        <n-progress 
          type="line" 
          :percentage="Number(displayProgress.toFixed(2))" 
          :indicator-placement="'inside'" 
          :height="18"
          :processing="isRunning"
          :status="status.state === 'ERROR' ? 'error' : status.state === 'COMPLETED' ? 'success' : 'default'"
        >
          {{ displayProgress.toFixed(2) }}%
        </n-progress>

        <!-- Speed HUD -->
        <div class="speed-control-bar">
          <div class="speed-item">
            <span class="sub-label">Step Delay:</span>
            <n-input-number 
              v-model:value="stepDelay" 
              :min="0" 
              :max="5000" 
              :step="10"
              size="small"
              style="width: 120px"
              @update:value="applyDelayChange"
            >
              <template #suffix>ms</template>
            </n-input-number>
          </div>
          <n-divider vertical />
          <div class="speed-item">
            <span class="sub-label">Speed Multiplier:</span>
            <n-button-group size="small">
              <n-button 
                v-for="preset in [0.2, 1, 5, 20, 50]" 
                :key="preset"
                :type="currentSpeed === preset ? 'primary' : 'default'"
                @click="applySpeed(preset)"
              >
                {{ preset }}x
              </n-button>
              <n-button 
                :type="currentSpeed === 0 ? 'primary' : 'default'"
                @click="applySpeed(0)"
              >
                MAX (0ms)
              </n-button>
            </n-button-group>
          </div>
        </div>
      </div>

      <!-- Live Telemetry Stats -->
      <n-grid :cols="6" :x-gap="12" class="telemetry-grid">
        <!-- Card 1: Market Index + Speed indicator -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-header-row">
              <span class="card-label">Market Index</span>
              <n-tag size="tiny" type="info" round class="speed-tag">
                {{ stepDelay === 0 ? 'MAX (0ms)' : `${stepDelay}ms` }}
              </n-tag>
            </div>
            <div class="card-value highlight">
              {{ (status.marketIndex || config.indexBase).toFixed(2) }}
            </div>
            <div class="card-subtext" :class="(metrics.dayReturn || 0) >= 0 ? 'text-success' : 'text-warning'">
              Intraday: {{ ((metrics.dayReturn || 0) >= 0 ? '+' : '') + ((metrics.dayReturn || 0) * 100).toFixed(2) }}%
            </div>
          </div>
        </n-grid-item>

        <!-- Card 2: Active Agents -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-label">Active Agents</div>
            <div class="card-value">{{ status.activeAgents || config.totalAgents }}</div>
            <div class="card-subtext">Total: {{ config.totalAgents }}</div>
          </div>
        </n-grid-item>

        <!-- Card 3: Circuit Breaker with Live Numerical Value -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-label">Circuit Breaker</div>
            <div 
              class="card-value" 
              :class="metrics.circuitBreakerTriggered ? 'danger' : ((metrics.dayReturn || 0) >= 0 ? 'text-success' : 'text-warning')"
            >
              {{ metrics.circuitBreakerTriggered ? 'HALTED' : (metrics.dayReturn !== undefined ? ((metrics.dayReturn >= 0 ? '+' : '') + (metrics.dayReturn * 100).toFixed(2) + '%') : '0.00%') }}
            </div>
            <div class="card-subtext">
              Limit: ±{{ (((metrics.circuitBreakerThreshold !== undefined && metrics.circuitBreakerThreshold > 0) ? metrics.circuitBreakerThreshold : config.circuitBreakerThreshold) * 100).toFixed(1) }}%
            </div>
          </div>
        </n-grid-item>

        <!-- Card 4: Margin Calls -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-label">Margin Calls</div>
            <div class="card-value warning">{{ metrics.totalMarginCalls || 0 }}</div>
            <div class="card-subtext">Triggers</div>
          </div>
        </n-grid-item>

        <!-- Card 5: Liquidations -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-label">Liquidations</div>
            <div class="card-value danger">{{ metrics.totalForcedLiquidations || 0 }}</div>
            <div class="card-subtext">Forced Closes</div>
          </div>
        </n-grid-item>

        <!-- Card 6: Social Wealth Pool -->
        <n-grid-item>
          <div class="telemetry-card">
            <div class="card-label">Social Wealth Pool</div>
            <div class="card-value">{{ formatLargeNumber(metrics.socialWealthPool || 0) }}</div>
            <div class="card-subtext">{{ (config.socialPoolRatio * 100).toFixed(0) }}% Tax Pool</div>
          </div>
        </n-grid-item>
      </n-grid>
    </n-card>

    <!-- 2. Preset Configuration Dropdown Selector -->
    <n-card class="preset-card" :bordered="false" size="small">
      <div class="preset-container">
        <div class="preset-title">
          <span>Configuration Preset:</span>
        </div>
        <div class="preset-select-group">
          <n-select 
            v-model:value="selectedPresetId" 
            :options="presetSelectOptions" 
            :disabled="isRunning"
            placeholder="Select a preset configuration..."
            style="width: 380px"
            @update:value="onPresetChange"
          />
          <n-text depth="3" class="preset-desc-text">
            {{ selectedPresetDescription }}
          </n-text>
        </div>
      </div>
    </n-card>

    <!-- 3. Comprehensive Multi-Category Configuration Panels -->
    <n-tabs type="line" animated class="config-tabs">
      <!-- Tab 1: Market & Environment -->
      <n-tab-pane name="general" tab="General & Market">
        <n-card :bordered="false">
          <n-form label-placement="left" label-width="200px">
            <n-grid :cols="2" :x-gap="32">
              <n-grid-item>
                <n-form-item label="Simulation Name">
                  <n-input v-model:value="simulationName" placeholder="e.g. sim_experiment_01" :disabled="isRunning" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Market Scenario">
                  <n-select v-model:value="config.scenarioName" :options="scenarioOptions" :disabled="isRunning" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Total Simulation Days">
                  <n-input-number v-model:value="config.simulationDays" :min="10" :max="5000" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Trading Minutes / Step">
                  <n-input-number v-model:value="minutesPerStep" :min="1" :max="240" :disabled="isRunning" style="width: 100%">
                    <template #suffix>mins ({{ config.stepsPerDay }} steps/day)</template>
                  </n-input-number>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Number of Stocks">
                  <n-input-number v-model:value="config.numStocks" :min="5" :max="200" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Initial Index Base">
                  <n-input-number v-model:value="config.indexBase" :min="100" :max="10000" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Total Capital Pool">
                  <n-input-number v-model:value="config.totalCapitalPool" :min="1e7" :max="1e12" :step="1e8" :disabled="isRunning" style="width: 100%">
                    <template #prefix>$</template>
                  </n-input-number>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Social Wealth Pool Ratio">
                  <n-slider v-model:value="config.socialPoolRatio" :min="0" :max="0.5" :step="0.01" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.socialPoolRatio * 100).toFixed(0) }}%</span>
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Tab 2: Agent Demographics & Evolution -->
      <n-tab-pane name="agents" tab="Agents & Demographics">
        <n-card :bordered="false">
          <n-form label-placement="left" label-width="220px">
            <n-grid :cols="2" :x-gap="32">
              <n-grid-item>
                <n-form-item label="Total Agent Population">
                  <n-input-number v-model:value="config.totalAgents" :min="50" :max="20000" :step="100" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Institutional Ratio">
                  <n-slider v-model:value="config.institutionalRatio" :min="0.01" :max="0.40" :step="0.01" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.institutionalRatio * 100).toFixed(0) }}% (~{{ Math.round(config.totalAgents * config.institutionalRatio) }} inst)</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Institutional Capital Share">
                  <n-slider v-model:value="config.institutionalCapitalRatio" :min="0.10" :max="0.95" :step="0.05" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.institutionalCapitalRatio * 100).toFixed(0) }}%</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Retail Sub-Ratio (vs Noise)">
                  <n-slider v-model:value="config.retailSubRatio" :min="0.10" :max="0.90" :step="0.05" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.retailSubRatio * 100).toFixed(0) }}% Retail / {{ ((1 - config.retailSubRatio) * 100).toFixed(0) }}% Noise</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Trait Mutation Rate">
                  <n-input-number v-model:value="config.agentMutationRate" :min="0" :max="0.2" :step="0.005" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Mutation Gaussian StdDev">
                  <n-input-number v-model:value="config.agentMutationStdDev" :min="0.01" :max="0.3" :step="0.01" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Tab 3: Three-Factor Valuation Engine -->
      <n-tab-pane name="valuation" tab="Three-Factor Valuation">
        <n-card :bordered="false">
          <div class="sub-section-title">Macro Fundamental Multipliers & Sector Bonus</div>
          <n-grid :cols="4" :x-gap="16" style="margin-bottom: 20px;">
            <n-grid-item>
              <n-form-item label="P/B Multiplier (M_PB)" label-placement="top">
                <n-input-number v-model:value="config.pbMultiplier" :min="0.1" :max="10" :step="0.1" :disabled="isRunning" style="width: 100%" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item label="P/E Multiplier (M_PE)" label-placement="top">
                <n-input-number v-model:value="config.peMultiplier" :min="1" :max="100" :step="1" :disabled="isRunning" style="width: 100%" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item label="Tech Bonus" label-placement="top">
                <n-input-number v-model:value="config.sectorBonusTech" :min="0.5" :max="2.5" :step="0.05" :disabled="isRunning" style="width: 100%" />
              </n-form-item>
            </n-grid-item>
            <n-grid-item>
              <n-form-item label="Healthcare Bonus" label-placement="top">
                <n-input-number v-model:value="config.sectorBonusHealthcare" :min="0.5" :max="2.5" :step="0.05" :disabled="isRunning" style="width: 100%" />
              </n-form-item>
            </n-grid-item>
          </n-grid>

          <div class="sub-section-title">Trader Base Factor Weights Breakdown</div>
          <n-grid :cols="3" :x-gap="20">
            <!-- Institutional -->
            <n-grid-item>
              <n-card title="Institutional Trader" size="small" class="factor-group-card">
                <n-form label-placement="left" label-width="140px">
                  <n-form-item label="Fundamental:">
                    <n-slider v-model:value="config.valFundWeightInst" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Social:">
                    <n-slider v-model:value="config.valSocialWeightInst" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Trend:">
                    <n-slider v-model:value="config.valTrendWeightInst" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Lookback Days:">
                    <n-input-number v-model:value="config.valLookbackDaysInst" :min="1" :max="60" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                  <n-form-item label="Noise StdDev:">
                    <n-input-number v-model:value="config.valNoiseStdDevInst" :min="0" :max="0.5" :step="0.01" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                </n-form>
              </n-card>
            </n-grid-item>

            <!-- Retail -->
            <n-grid-item>
              <n-card title="Retail Trader" size="small" class="factor-group-card">
                <n-form label-placement="left" label-width="140px">
                  <n-form-item label="Fundamental:">
                    <n-slider v-model:value="config.valFundWeightRetail" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Social:">
                    <n-slider v-model:value="config.valSocialWeightRetail" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Trend:">
                    <n-slider v-model:value="config.valTrendWeightRetail" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Lookback Days:">
                    <n-input-number v-model:value="config.valLookbackDaysRetail" :min="1" :max="60" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                  <n-form-item label="Noise StdDev:">
                    <n-input-number v-model:value="config.valNoiseStdDevRetail" :min="0" :max="0.5" :step="0.01" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                </n-form>
              </n-card>
            </n-grid-item>

            <!-- Noise -->
            <n-grid-item>
              <n-card title="Noise Trader" size="small" class="factor-group-card">
                <n-form label-placement="left" label-width="140px">
                  <n-form-item label="Fundamental:">
                    <n-slider v-model:value="config.valFundWeightNoise" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Social:">
                    <n-slider v-model:value="config.valSocialWeightNoise" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Trend:">
                    <n-slider v-model:value="config.valTrendWeightNoise" :min="0" :max="1" :step="0.05" :disabled="isRunning" />
                  </n-form-item>
                  <n-form-item label="Lookback Days:">
                    <n-input-number v-model:value="config.valLookbackDaysNoise" :min="1" :max="60" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                  <n-form-item label="Noise StdDev:">
                    <n-input-number v-model:value="config.valNoiseStdDevNoise" :min="0" :max="0.5" :step="0.01" :disabled="isRunning" style="width: 100%" />
                  </n-form-item>
                </n-form>
              </n-card>
            </n-grid-item>
          </n-grid>
        </n-card>
      </n-tab-pane>

      <!-- Tab 4: Social Network & Reputation -->
      <n-tab-pane name="social" tab="Social & Reputation">
        <n-card :bordered="false">
          <n-form label-placement="left" label-width="240px">
            <n-grid :cols="2" :x-gap="32">
              <n-grid-item>
                <n-form-item label="Followed Institutions (|H_i|)">
                  <n-input-number v-model:value="config.socialInstNeighborsCount" :min="1" :max="20" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Followed Peers (|P_i|)">
                  <n-input-number v-model:value="config.socialPeerNeighborsCount" :min="0" :max="20" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Institutional Influence Share (rho)">
                  <n-slider v-model:value="config.socialInstInfluenceRatio" :min="0" :max="1" :step="0.05" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.socialInstInfluenceRatio * 100).toFixed(0) }}%</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Softmax Temperature (tau)">
                  <n-input-number v-model:value="config.socialSoftmaxTemperature" :min="0.05" :max="5.0" :step="0.05" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Min Attention Floor (eta)">
                  <n-slider v-model:value="config.socialMinWeightFloor" :min="0.05" :max="0.50" :step="0.05" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.socialMinWeightFloor * 100).toFixed(0) }}% (Floor: {{ ((config.socialMinWeightFloor / config.socialInstNeighborsCount) * 100).toFixed(1) }}%/inst)</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Alpha EMA Smoothing (lambda_m)">
                  <n-input-number v-model:value="config.socialAlphaEmaLambda" :min="0.5" :max="0.99" :step="0.01" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Reputation Warmup Days">
                  <n-input-number v-model:value="config.socialReputationWarmupDays" :min="1" :max="50" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Social Sensitivity Base (beta)">
                  <n-input-number v-model:value="config.socialSensitivityBeta" :min="0.05" :max="2.0" :step="0.05" :disabled="isRunning" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Tab 5: Policy Slots & Leverage -->
      <n-tab-pane name="policy" tab="Policy Slots & Leverage">
        <n-card :bordered="false">
          <n-form label-placement="left" label-width="220px">
            <n-grid :cols="2" :x-gap="32">
              <n-grid-item>
                <n-form-item label="Price Limit Ratio (L_limit)">
                  <n-slider v-model:value="config.priceLimitRatio" :min="0" :max="0.30" :step="0.01" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">±{{ (config.priceLimitRatio * 100).toFixed(0) }}%</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Circuit Breaker (Th_halt)">
                  <n-slider v-model:value="config.circuitBreakerThreshold" :min="0.01" :max="0.20" :step="0.005" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">±{{ (config.circuitBreakerThreshold * 100).toFixed(1) }}%</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Max Leverage Ratio (Lev_max)">
                  <n-slider v-model:value="config.maxLeverageRatio" :min="1" :max="10" :step="0.5" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ config.maxLeverageRatio.toFixed(1) }}x</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Settlement Cycle (N_settle)">
                  <n-select 
                    v-model:value="config.settlementDays" 
                    :options="[
                      { label: 'T+0 (Instant)', value: 0 },
                      { label: 'T+1 (Next Day)', value: 1 },
                      { label: 'T+2 (2 Days)', value: 2 },
                      { label: 'T+3 (3 Days)', value: 3 }
                    ]"
                    :disabled="isRunning" 
                    style="width: 100%"
                  />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Maintenance Margin Call">
                  <n-slider v-model:value="config.leverageMarginCallRatio" :min="1.0" :max="2.0" :step="0.05" :disabled="isRunning" style="width: 70%" />
                  <span class="slider-val">{{ (config.leverageMarginCallRatio * 100).toFixed(0) }}%</span>
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Daily Financing Interest">
                  <n-input-number v-model:value="config.leverageInterestRateDaily" :min="0.00005" :max="0.005" :step="0.0001" :disabled="isRunning" style="width: 100%">
                    <template #suffix>({{ (config.leverageInterestRateDaily * 365 * 100).toFixed(1) }}% / yr)</template>
                  </n-input-number>
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Tab 6: Logging & Performance I/O -->
      <n-tab-pane name="logging" tab="Logging & I/O">
        <n-card :bordered="false">
          <n-form label-placement="left" label-width="260px">
            <n-grid :cols="2" :x-gap="32">
              <n-grid-item>
                <n-form-item label="Log Granular Trade Records">
                  <n-switch v-model:value="config.logTrades" :disabled="isRunning" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Log Daily Agent Asset Snapshots">
                  <n-switch v-model:value="config.logAgentDaily" :disabled="isRunning" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Holdings Delta Mode">
                  <n-switch v-model:value="config.logHoldingsDelta" :disabled="isRunning" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Holdings Full Snapshot Interval">
                  <n-input-number v-model:value="config.holdingsSnapshotInterval" :min="1" :max="100" :disabled="isRunning" style="width: 100%">
                    <template #suffix>days</template>
                  </n-input-number>
                </n-form-item>
              </n-grid-item>
            </n-grid>
          </n-form>
        </n-card>
      </n-tab-pane>

      <!-- Tab 7: Dynamic Events Schedule -->
      <n-tab-pane name="events" tab="Scheduled Events">
        <n-card :bordered="false">
          <div class="sub-section-title">Schedule Regulatory Policies & Market Shocks</div>
          
          <n-space style="margin-bottom: 16px;">
            <n-radio-group v-model:value="eventCategory" name="event-category">
              <n-radio-button value="POLICY">Regulatory Policy Intervention</n-radio-button>
              <n-radio-button value="SHOCK">Exogenous Market Shock</n-radio-button>
            </n-radio-group>
          </n-space>

          <!-- Form for Policy -->
          <n-form v-if="eventCategory === 'POLICY'" label-placement="left" label-width="150px">
            <n-grid :cols="3" :x-gap="16">
              <n-grid-item>
                <n-form-item label="Trigger Day">
                  <n-input-number v-model:value="policyForm.day" :min="1" :max="displayTotalDays" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Policy Type">
                  <n-select v-model:value="policyForm.policyType" :options="policyTypeOptions" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Target Value">
                  <span v-if="policyForm.policyType === 'PRICE_LIMIT' || policyForm.policyType === 'CIRCUIT_BREAKER'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.30" :step="0.005" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">±{{ (policyForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'LEVERAGE'">
                    <n-slider v-model:value="policyForm.value" :min="1" :max="10" :step="0.5" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ policyForm.value.toFixed(1) }}x</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'SETTLEMENT'">
                    <n-input-number v-model:value="policyForm.value" :min="0" :max="5" :step="1" style="width: 120px"/>
                    <n-tag size="small" style="margin-left: 8px">T+{{ policyForm.value }}</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'BENCHMARK_RATE'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.20" :step="0.005" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ (policyForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'REQUIRED_RESERVE_RATIO'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.50" :step="0.01" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ (policyForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'STAMP_DUTY'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.01" :step="0.0005" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ (policyForm.value * 1000).toFixed(1) }}‰</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'CAPITAL_GAINS_TAX'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.50" :step="0.02" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ (policyForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'DIVIDEND_TAX'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="0.50" :step="0.02" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ (policyForm.value * 100).toFixed(1) }}%</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'DISCLOSURE_PERIOD'">
                    <n-input-number v-model:value="policyForm.value" :min="5" :max="250" :step="5" style="width: 120px"/>
                    <n-tag size="small" style="margin-left: 8px">{{ policyForm.value }} days</n-tag>
                  </span>
                  <span v-else-if="policyForm.policyType === 'OPINION_CENSORSHIP'">
                    <n-slider v-model:value="policyForm.value" :min="0" :max="1.0" :step="0.05" style="width: 120px; display: inline-block; margin-right: 8px;"/>
                    <n-tag size="small">{{ policyForm.value.toFixed(2) }}</n-tag>
                  </span>
                </n-form-item>
              </n-grid-item>
            </n-grid>
            <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
              <n-button type="primary" @click="injectPolicyEvent">
                Schedule Policy Event
              </n-button>
            </div>
          </n-form>

          <!-- Form for Market Shock -->
          <n-form v-else label-placement="left" label-width="150px">
            <n-grid :cols="4" :x-gap="16">
              <n-grid-item>
                <n-form-item label="Trigger Day">
                  <n-input-number v-model:value="shockForm.day" :min="1" :max="displayTotalDays" style="width: 100%" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Shock Type">
                  <n-select v-model:value="shockForm.shockType" :options="shockTypeOptions" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Target Sector">
                  <n-select v-model:value="shockForm.targetSector" :options="targetSectorOptions" />
                </n-form-item>
              </n-grid-item>
              <n-grid-item>
                <n-form-item label="Intensity & Decay">
                  <div style="display: flex; flex-direction: column; gap: 4px; width: 100%;">
                    <div>
                      <n-slider v-model:value="shockForm.magnitude" :min="0.05" :max="0.80" :step="0.05" style="width: 90px; display: inline-block; margin-right: 8px;"/>
                      <n-tag size="small">{{ (shockForm.magnitude * 100).toFixed(0) }}%</n-tag>
                    </div>
                    <div>
                      <n-input-number v-model:value="shockForm.halfLifeDays" :min="1" :max="50" style="width: 80px; display: inline-block; margin-right: 8px;" />
                      <n-tag size="small">{{ shockForm.halfLifeDays }}d half-life</n-tag>
                    </div>
                  </div>
                </n-form-item>
              </n-grid-item>
            </n-grid>
            <div style="display: flex; justify-content: flex-end; margin-top: 10px;">
              <n-button type="warning" @click="injectMarketShock">
                Schedule Market Shock
              </n-button>
            </div>
          </n-form>

          <n-divider style="margin: 20px 0 16px 0;" />
          <div class="sub-section-title">Scheduled Events List (Real-Time Status)</div>
          <n-data-table 
            :columns="eventColumns" 
            :data="scheduledEvents" 
            :bordered="false" 
            size="small" 
          />
        </n-card>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch, h } from 'vue'
import { useRouter } from 'vue-router'
import {
  NCard, NButton, NButtonGroup, NProgress, NText, NIcon, NTag,
  NTabs, NTabPane, NForm, NFormItem, NInputNumber, NSelect, NSlider,
  NGrid, NGridItem, NInput, NDivider, NDataTable, NSwitch,
  useMessage, useDialog
} from 'naive-ui'
import {
  Play, Pause, Stop, Refresh, Speedometer,
  Sparkles, BarChart
} from '@vicons/ionicons5'
import { controlApi, scenarioApi } from '@/services/api'
import { useSimulationStore } from '@/stores/simulation'

const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const store = useSimulationStore()

let pollInterval: number | null = null

function generateDefaultName() {
  const now = new Date()
  const y = now.getFullYear()
  const m = String(now.getMonth() + 1).padStart(2, '0')
  const d = String(now.getDate()).padStart(2, '0')
  const hh = String(now.getHours()).padStart(2, '0')
  const mm = String(now.getMinutes()).padStart(2, '0')
  const ss = String(now.getSeconds()).padStart(2, '0')
  return `sim_${y}${m}${d}_${hh}${mm}${ss}`
}

const simulationName = ref(generateDefaultName())

const status = ref({
  sessionId: null as string | null,
  simulationName: null as string | null,
  state: 'NO_SESSION',
  currentDay: 0,
  totalDays: 1000,
  progress: 0,
  activeAgents: 0,
  marketIndex: 0,
  speedMultiplier: 1.0,
  lastError: null as string | null,
  currentStepInDay: 0,
  totalStepsPerDay: 22
})

const metrics = ref({
  circuitBreakerTriggered: false,
  circuitBreakerThreshold: 0.07,
  dayReturn: 0.0,
  totalMarginCalls: 0,
  totalForcedLiquidations: 0,
  socialWealthPool: 0
} as Record<string, any>)

// Comprehensive Config State with stepDelay = 0 default
const config = ref({
  simulationName: simulationName.value,
  scenarioName: 'DefaultScenario',
  numStocks: 50,
  simulationDays: 1000,
  stepsPerDay: 22,
  indexBase: 3000,
  stepDelay: 0,

  totalCapitalPool: 1000000000,
  totalAgents: 1000,
  socialPoolRatio: 0.10,

  institutionalRatio: 0.05,
  institutionalCapitalRatio: 0.70,
  retailSubRatio: 0.60,
  noiseSubRatio: 0.40,
  agentMutationRate: 0.01,
  agentMutationStdDev: 0.05,

  priceLimitRatio: 0.10,
  circuitBreakerThreshold: 0.07,
  maxLeverageRatio: 2.0,
  settlementDays: 1,

  leverageMarginCallRatio: 1.30,
  leverageInterestRateDaily: 0.0003,
  leverageRequestProb: 0.05,

  pbMultiplier: 1.5,
  peMultiplier: 25.0,
  sectorBonusTech: 1.3,
  sectorBonusHealthcare: 1.2,
  sectorBonusConsumer: 1.1,
  sectorBonusFinance: 0.9,
  sectorBonusIndustry: 1.0,
  sectorBonusEnergy: 1.0,

  valFundWeightInst: 0.80,
  valSocialWeightInst: 0.10,
  valTrendWeightInst: 0.10,
  valNoiseStdDevInst: 0.05,
  valLookbackDaysInst: 20,

  valFundWeightRetail: 0.50,
  valSocialWeightRetail: 0.25,
  valTrendWeightRetail: 0.25,
  valNoiseStdDevRetail: 0.10,
  valLookbackDaysRetail: 10,

  valFundWeightNoise: 0.10,
  valSocialWeightNoise: 0.30,
  valTrendWeightNoise: 0.60,
  valNoiseStdDevNoise: 0.20,
  valLookbackDaysNoise: 5,

  socialTopK: 5,
  socialInstNeighborsCount: 3,
  socialPeerNeighborsCount: 3,
  socialInstInfluenceRatio: 0.70,
  socialSoftmaxTemperature: 0.50,
  socialMinWeightFloor: 0.20,
  socialAlphaEmaLambda: 0.95,
  socialVarianceEmaLambda: 0.95,
  socialReputationWarmupDays: 10,
  socialReputationScaleKappa: 1.0,
  socialSensitivityBeta: 0.30,
  socialRebuildInterval: 20,

  logTrades: true,
  logHoldingsDelta: true,
  logAgentDaily: true,
  logSampleInterval: 1,
  holdingsSnapshotInterval: 10
})

const minutesPerStep = ref(15)
watch(minutesPerStep, (val) => {
  if (val > 0) {
    config.value.stepsPerDay = Math.floor(240 / val)
  }
})

// Step delay defaults to 0
const stepDelay = ref(0)
const currentSpeed = ref(1)

// Scenarios: Display only class names (name)
const scenarioOptions = ref<{label: string, value: string}[]>([])
async function loadScenarios() {
  try {
    const data = await scenarioApi.list() as any[]
    scenarioOptions.value = data.map(s => ({
      label: s.name,
      value: s.name
    }))
  } catch (e) {
    scenarioOptions.value = [{ label: 'DefaultScenario', value: 'DefaultScenario' }]
  }
}

// Preset Definitions & Selection
const selectedPresetId = ref('baseline')

const presetSelectOptions = [
  { label: 'Standard Baseline (Balanced Equilibrium)', value: 'baseline' },
  { label: 'Speculative Mania & Herd (FOMO Frenzy)', value: 'bubble' },
  { label: 'Panic & Circuit Breaker (Crash Test)', value: 'crash' },
  { label: 'Institutional Rational Market (Value Dominated)', value: 'institutional' },
  { label: 'High-Speed Batch (Minimal I/O, Fast Execution)', value: 'fast_batch' }
]

const presets = [
  {
    id: 'baseline',
    name: 'Standard Baseline',
    desc: 'Academic balanced equilibrium with standard market structure',
    values: {
      numStocks: 50,
      totalAgents: 1000,
      simulationDays: 1000,
      institutionalRatio: 0.05,
      institutionalCapitalRatio: 0.70,
      retailSubRatio: 0.60,
      noiseSubRatio: 0.40,
      agentMutationRate: 0.01,
      agentMutationStdDev: 0.05,
      priceLimitRatio: 0.10,
      circuitBreakerThreshold: 0.07,
      maxLeverageRatio: 2.0,
      settlementDays: 1,
      leverageMarginCallRatio: 1.30,
      leverageInterestRateDaily: 0.0003,
      pbMultiplier: 1.5,
      peMultiplier: 25.0,
      sectorBonusTech: 1.3,
      sectorBonusHealthcare: 1.2,
      valFundWeightInst: 0.80,
      valSocialWeightInst: 0.10,
      valTrendWeightInst: 0.10,
      valFundWeightRetail: 0.50,
      valSocialWeightRetail: 0.25,
      valTrendWeightRetail: 0.25,
      valFundWeightNoise: 0.10,
      valSocialWeightNoise: 0.30,
      valTrendWeightNoise: 0.60,
      socialInstNeighborsCount: 3,
      socialPeerNeighborsCount: 3,
      socialInstInfluenceRatio: 0.70,
      socialSoftmaxTemperature: 0.50,
      socialMinWeightFloor: 0.20,
      socialAlphaEmaLambda: 0.95,
      socialReputationWarmupDays: 10,
      socialSensitivityBeta: 0.30,
      logTrades: true,
      logAgentDaily: true,
      logHoldingsDelta: true,
      holdingsSnapshotInterval: 10,
      stepDelay: 0
    }
  },
  {
    id: 'bubble',
    name: 'Speculative Mania & Herd',
    desc: 'Amplified retail participation, high social imitation, and leverage-fueled momentum',
    values: {
      numStocks: 50,
      totalAgents: 1500,
      simulationDays: 1000,
      institutionalRatio: 0.03,
      institutionalCapitalRatio: 0.50,
      retailSubRatio: 0.35,
      noiseSubRatio: 0.65,
      agentMutationRate: 0.03,
      agentMutationStdDev: 0.08,
      priceLimitRatio: 0.20,
      circuitBreakerThreshold: 0.10,
      maxLeverageRatio: 4.0,
      settlementDays: 0,
      leverageMarginCallRatio: 1.20,
      leverageInterestRateDaily: 0.0002,
      valFundWeightInst: 0.60,
      valSocialWeightInst: 0.20,
      valTrendWeightInst: 0.20,
      valFundWeightRetail: 0.15,
      valSocialWeightRetail: 0.45,
      valTrendWeightRetail: 0.40,
      valFundWeightNoise: 0.05,
      valSocialWeightNoise: 0.35,
      valTrendWeightNoise: 0.60,
      socialInstNeighborsCount: 5,
      socialPeerNeighborsCount: 5,
      socialInstInfluenceRatio: 0.85,
      socialSoftmaxTemperature: 0.25,
      socialMinWeightFloor: 0.10,
      socialAlphaEmaLambda: 0.90,
      socialReputationWarmupDays: 5,
      socialSensitivityBeta: 0.60,
      logTrades: true,
      logAgentDaily: true,
      logHoldingsDelta: true,
      holdingsSnapshotInterval: 10,
      stepDelay: 0
    }
  },
  {
    id: 'crash',
    name: 'Panic & Circuit Breaker',
    desc: 'Tight price halts, heavy leverage liquidation risks, and sharp panic reactions',
    values: {
      numStocks: 30,
      totalAgents: 1000,
      simulationDays: 1000,
      institutionalRatio: 0.05,
      institutionalCapitalRatio: 0.65,
      retailSubRatio: 0.50,
      noiseSubRatio: 0.50,
      priceLimitRatio: 0.05,
      circuitBreakerThreshold: 0.05,
      maxLeverageRatio: 3.5,
      settlementDays: 1,
      leverageMarginCallRatio: 1.45,
      valFundWeightInst: 0.70,
      valSocialWeightInst: 0.15,
      valTrendWeightInst: 0.15,
      valFundWeightRetail: 0.30,
      valSocialWeightRetail: 0.40,
      valTrendWeightRetail: 0.30,
      valFundWeightNoise: 0.05,
      valSocialWeightNoise: 0.20,
      valTrendWeightNoise: 0.75,
      socialInstNeighborsCount: 4,
      socialPeerNeighborsCount: 2,
      socialInstInfluenceRatio: 0.80,
      socialSoftmaxTemperature: 0.20,
      socialMinWeightFloor: 0.15,
      socialSensitivityBeta: 0.80,
      logTrades: true,
      logAgentDaily: true,
      logHoldingsDelta: true,
      holdingsSnapshotInterval: 10,
      stepDelay: 0
    }
  },
  {
    id: 'institutional',
    name: 'Institutional Rational Market',
    desc: 'Dominated by fundamental value investors with strict risk controls and low leverage',
    values: {
      numStocks: 50,
      totalAgents: 800,
      simulationDays: 1000,
      institutionalRatio: 0.20,
      institutionalCapitalRatio: 0.85,
      retailSubRatio: 0.70,
      noiseSubRatio: 0.30,
      agentMutationRate: 0.005,
      agentMutationStdDev: 0.02,
      priceLimitRatio: 0.10,
      circuitBreakerThreshold: 0.07,
      maxLeverageRatio: 1.2,
      settlementDays: 1,
      leverageMarginCallRatio: 1.50,
      valFundWeightInst: 0.90,
      valSocialWeightInst: 0.05,
      valTrendWeightInst: 0.05,
      valFundWeightRetail: 0.70,
      valSocialWeightRetail: 0.15,
      valTrendWeightRetail: 0.15,
      valFundWeightNoise: 0.20,
      valSocialWeightNoise: 0.30,
      valTrendWeightNoise: 0.50,
      socialInstNeighborsCount: 2,
      socialPeerNeighborsCount: 2,
      socialInstInfluenceRatio: 0.50,
      socialSoftmaxTemperature: 0.80,
      socialMinWeightFloor: 0.30,
      socialSensitivityBeta: 0.20,
      logTrades: true,
      logAgentDaily: true,
      logHoldingsDelta: true,
      holdingsSnapshotInterval: 10,
      stepDelay: 0
    }
  },
  {
    id: 'fast_batch',
    name: 'High-Speed Batch',
    desc: 'Minimal I/O and zero delay optimized for fast parameter exploration',
    values: {
      numStocks: 50,
      totalAgents: 1000,
      simulationDays: 1000,
      logTrades: false,
      logAgentDaily: false,
      logHoldingsDelta: true,
      holdingsSnapshotInterval: 50,
      stepDelay: 0
    }
  }
]

const selectedPresetDescription = computed(() => {
  const p = presets.find(item => item.id === selectedPresetId.value)
  return p ? p.desc : ''
})

function onPresetChange(presetId: string) {
  selectedPresetId.value = presetId
  const preset = presets.find(p => p.id === presetId)
  if (preset) {
    for (const [key, value] of Object.entries(preset.values)) {
      if (key === 'simulationDays') continue // preserve user-configured simulation days
      (config.value as any)[key] = value
    }
    if (preset.values.stepDelay !== undefined) {
      stepDelay.value = preset.values.stepDelay
    }
    message.success(`Applied configuration preset: ${preset.name}`)
  }
}

// Computed Total Days & Progress (Dynamically tracks running session)
const displayTotalDays = computed(() => {
  if (status.value.totalDays && status.value.totalDays > 0) {
    return status.value.totalDays
  }
  return config.value.simulationDays || 1000
})

const displayProgress = computed(() => {
  const total = displayTotalDays.value
  if (total > 0 && status.value.currentDay > 0) {
    return Math.min(100, Math.max(0, (status.value.currentDay / total) * 100))
  }
  return Math.min(100, Math.max(0, status.value.progress || 0))
})

// Computed Status
const hasSession = computed(() => status.value.sessionId !== null && status.value.state !== 'NO_SESSION')
const isRunning = computed(() => status.value.state === 'RUNNING')
const isPaused = computed(() => status.value.state === 'PAUSED')

const statusTagType = computed(() => {
  switch (status.value.state) {
    case 'RUNNING': return 'success'
    case 'PAUSED': return 'warning'
    case 'COMPLETED': return 'info'
    case 'ERROR': return 'error'
    default: return 'default'
  }
})

function formatLargeNumber(n: number) {
  if (n === null || n === undefined) return '-'
  if (Math.abs(n) >= 1e12) return (n / 1e12).toFixed(2) + ' T'
  if (Math.abs(n) >= 1e9) return (n / 1e9).toFixed(2) + ' B'
  if (Math.abs(n) >= 1e6) return (n / 1e6).toFixed(2) + ' M'
  if (Math.abs(n) >= 1e3) return (n / 1e3).toFixed(1) + ' K'
  return n.toFixed(0)
}

// Actions
async function loadStatus() {
  try {
    const data = await controlApi.getStatus() as any
    status.value = data
    if (data.speedMultiplier !== undefined) {
      currentSpeed.value = data.speedMultiplier
    }
    // Synchronize running session parameters into form view
    if (data.state === 'RUNNING' || data.state === 'PAUSED') {
      if (data.totalDays && data.totalDays > 0) {
        config.value.simulationDays = data.totalDays
      }
      if (data.simulationName) {
        simulationName.value = data.simulationName
      }
    }
  } catch (e) {}
}

async function loadMetrics() {
  try {
    const data = await controlApi.getMetrics() as any
    metrics.value = data
  } catch (e) {}
}

async function loadEvents() {
  try {
    const list = await controlApi.getEvents() as any[]
    scheduledEvents.value = list || []
  } catch (e) {}
}

async function startSimulation() {
  try {
    const payload = {
      ...config.value,
      simulationName: simulationName.value,
      stepDelay: stepDelay.value
    }
    await controlApi.start(payload)
    message.success(`Simulation started: ${simulationName.value}`)
    await loadStatus()
    await loadMetrics()
    await loadEvents()
  } catch (e: any) {
    message.error(e.response?.data?.error || 'Failed to start simulation')
  }
}

async function pauseSimulation() {
  try {
    await controlApi.pause()
    message.info('Simulation paused')
    await loadStatus()
  } catch (e) {
    message.error('Failed to pause')
  }
}

async function resumeSimulation() {
  try {
    await controlApi.resume()
    message.success('Simulation resumed')
    await loadStatus()
  } catch (e) {
    message.error('Failed to resume')
  }
}

async function stopSimulation() {
  dialog.warning({
    title: 'Stop Simulation',
    content: 'Are you sure you want to stop the active simulation session?',
    positiveText: 'Stop Now',
    negativeText: 'Cancel',
    onPositiveClick: async () => {
      try {
        await controlApi.stop()
        message.warning('Simulation stopped')
        await loadStatus()
        await store.fetchSimulations()
        await loadEvents()
      } catch (e) {
        message.error('Failed to stop simulation')
      }
    }
  })
}

async function resetSession() {
  try {
    await controlApi.reset()
    simulationName.value = generateDefaultName()
    config.value.simulationName = simulationName.value
    message.info('Session reset to idle state')
    await loadStatus()
    await store.fetchSimulations()
    await loadEvents()
  } catch (e) {
    message.error('Failed to reset session')
  }
}

async function applySpeed(multiplier: number) {
  try {
    if (multiplier === 0) {
      stepDelay.value = 0
      await controlApi.setSpeed(1000)
      message.success('Speed set to MAX (0ms delay)')
    } else {
      await controlApi.setSpeed(multiplier)
      currentSpeed.value = multiplier
      message.success(`Speed set to ${multiplier}x`)
    }
  } catch (e) {
    message.error('Failed to update speed')
  }
}

async function applyDelayChange(val: number | null) {
  if (val === null) return
  stepDelay.value = val
  if (val === 0) {
    currentSpeed.value = 0
  }
}

async function openInAnalytics() {
  const target = (status.value.simulationName || simulationName.value) + '.db'
  await store.switchToSimulation(target)
  message.success(`Loaded ${target} into workspace!`)
  router.push({ name: 'market' })
}

// Scheduled Events State
const eventCategory = ref<'POLICY' | 'SHOCK'>('POLICY')

const policyForm = ref({
  day: 100,
  policyType: 'CIRCUIT_BREAKER',
  value: 0.05,
  description: ''
})

const shockForm = ref({
  day: 100,
  shockType: 'SENTIMENT_CRASH',
  magnitude: 0.25,
  halfLifeDays: 5,
  targetSector: 'ALL',
  targetTraderId: -1,
  description: ''
})

const scheduledEvents = ref<any[]>([])

const policyTypeOptions = [
  { label: 'Price Limit (L_limit)', value: 'PRICE_LIMIT' },
  { label: 'Circuit Breaker (Th_halt)', value: 'CIRCUIT_BREAKER' },
  { label: 'Max Leverage (Lev_max)', value: 'LEVERAGE' },
  { label: 'Settlement (N_settle)', value: 'SETTLEMENT' },
  { label: 'Benchmark Interest Rate', value: 'BENCHMARK_RATE' },
  { label: 'Required Reserve Ratio (RRR)', value: 'REQUIRED_RESERVE_RATIO' },
  { label: 'Stamp Duty (Tax)', value: 'STAMP_DUTY' },
  { label: 'Capital Gains Tax (Tax)', value: 'CAPITAL_GAINS_TAX' },
  { label: 'Dividend Tax (Tax)', value: 'DIVIDEND_TAX' },
  { label: 'Disclosure Period (Days)', value: 'DISCLOSURE_PERIOD' },
  { label: 'Opinion Censorship Damping', value: 'OPINION_CENSORSHIP' }
]

const shockTypeOptions = [
  { label: 'Sentiment Crash (Panic Discount)', value: 'SENTIMENT_CRASH' },
  { label: 'Rating Downgrade (Growth Penalty)', value: 'RATING_DOWNGRADE' },
  { label: 'Liquidity Squeeze (Freeze & Margin Call)', value: 'LIQUIDITY_SQUEEZE' },
  { label: 'Capital Injection (Bailout / Social Pool)', value: 'CAPITAL_INJECTION' }
]

const targetSectorOptions = [
  { label: 'All Sectors (ALL)', value: 'ALL' },
  { label: 'Technology (TECH)', value: 'TECH' },
  { label: 'Healthcare (HEALTHCARE)', value: 'HEALTHCARE' },
  { label: 'Consumer (CONSUMER)', value: 'CONSUMER' },
  { label: 'Finance (FINANCE)', value: 'FINANCE' },
  { label: 'Industry (INDUSTRY)', value: 'INDUSTRY' },
  { label: 'Energy (ENERGY)', value: 'ENERGY' }
]

const eventColumns = [
  { title: '#', key: 'id', width: 60 },
  { title: 'Day', key: 'day', width: 85, render: (r: any) => `Day ${r.day}` },
  {
    title: 'Category',
    key: 'eventType',
    width: 100,
    render: (r: any) => {
      const isShock = r.eventType === 'SHOCK' || r.shockType !== undefined
      return h(
        NTag,
        {
          type: isShock ? 'error' : 'info',
          size: 'small',
          round: true
        },
        { default: () => isShock ? 'SHOCK' : 'POLICY' }
      )
    }
  },
  {
    title: 'Type',
    key: 'subtype',
    width: 170,
    render: (r: any) => r.subtype || r.policyType || r.shockType
  },
  {
    title: 'Parameters',
    key: 'value',
    width: 220,
    render: (r: any) => {
      if (r.eventType === 'SHOCK' || r.shockType) {
        const mag = ((r.magnitude || r.value || 0) * 100).toFixed(0) + '%'
        const hl = r.halfLifeDays ? `${r.halfLifeDays}d` : '-'
        const sec = r.targetSector || 'ALL'
        return `Intensity: ${mag} | Decay: ${hl} | Target: ${sec}`
      }
      const type = r.policyType || r.subtype
      const v = r.value !== undefined ? r.value : r.magnitude
      if (type === 'PRICE_LIMIT' || type === 'CIRCUIT_BREAKER') return `±${(v * 100).toFixed(1)}%`
      if (type === 'LEVERAGE') return `${v.toFixed(1)}x`
      if (type === 'SETTLEMENT') return `T+${v}`
      if (type === 'BENCHMARK_RATE' || type === 'REQUIRED_RESERVE_RATIO' || type === 'CAPITAL_GAINS_TAX' || type === 'DIVIDEND_TAX') return `${(v * 100).toFixed(1)}%`
      if (type === 'STAMP_DUTY') return `${(v * 1000).toFixed(1)}‰`
      if (type === 'DISCLOSURE_PERIOD') return `${v} days`
      if (type === 'OPINION_CENSORSHIP') return `Damping: ${v.toFixed(2)}`
      return String(v)
    }
  },
  { title: 'Description', key: 'description' },
  {
    title: 'Status',
    key: 'status',
    width: 120,
    render: (r: any) => {
      const isExecuted = r.status === 'EXECUTED' || r.executed
      return h(
        NTag,
        {
          type: isExecuted ? 'success' : 'warning',
          size: 'small',
          round: true
        },
        { default: () => isExecuted ? 'EXECUTED' : 'PENDING' }
      )
    }
  }
]

async function injectPolicyEvent() {
  try {
    await controlApi.injectPolicyEvent(
      policyForm.value.day,
      policyForm.value.policyType,
      policyForm.value.value,
      policyForm.value.description || undefined
    )
    message.success(`Policy event scheduled for Day ${policyForm.value.day}`)
    await loadEvents()
  } catch (e: any) {
    message.error('Failed to schedule policy: ' + (e.response?.data?.error || e.message))
  }
}

async function injectMarketShock() {
  try {
    await controlApi.injectMarketShock({
      day: shockForm.value.day,
      shockType: shockForm.value.shockType,
      magnitude: shockForm.value.magnitude,
      halfLifeDays: shockForm.value.halfLifeDays,
      targetSector: shockForm.value.targetSector,
      targetTraderId: shockForm.value.targetTraderId,
      description: shockForm.value.description || undefined
    })
    message.success(`Market shock scheduled for Day ${shockForm.value.day}`)
    await loadEvents()
  } catch (e: any) {
    message.error('Failed to schedule shock: ' + (e.response?.data?.error || e.message))
  }
}

onMounted(async () => {
  await loadScenarios()
  await loadStatus()
  await loadEvents()
  if (isRunning.value) {
    await loadMetrics()
  }

  pollInterval = window.setInterval(async () => {
    await loadStatus()
    if (isRunning.value || isPaused.value) {
      await loadMetrics()
      await loadEvents()
    }
  }, 600)
})

onUnmounted(() => {
  if (pollInterval) {
    clearInterval(pollInterval)
  }
})
</script>

<style scoped>
.control-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-bottom: 32px;
}

.mission-control-card {
  background: linear-gradient(135deg, rgba(32, 128, 240, 0.03) 0%, rgba(24, 160, 88, 0.03) 100%);
  border-radius: 12px;
}

.hud-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 20px;
}

.hud-title-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.title-with-badge {
  display: flex;
  align-items: center;
  gap: 12px;
}

.title-icon {
  color: #2080f0;
}

.title-text {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.5px;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
  margin-right: 6px;
}

.status-dot.pulsing {
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0% { transform: scale(0.95); opacity: 0.8; }
  50% { transform: scale(1.4); opacity: 1; }
  100% { transform: scale(0.95); opacity: 0.8; }
}

.hud-subtitle {
  font-size: 13px;
  opacity: 0.7;
}

.hud-subtitle code {
  background: rgba(128, 128, 128, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
}

.hud-action-group {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.start-btn {
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(32, 128, 240, 0.3);
}

.analyze-btn {
  font-weight: 600;
}

.progress-section {
  background: rgba(128, 128, 128, 0.06);
  padding: 14px 18px;
  border-radius: 8px;
  margin-bottom: 20px;
}

.progress-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.meta-label {
  font-size: 14px;
}

.step-counter {
  margin-left: 8px;
  opacity: 0.6;
  font-size: 12px;
}

.meta-percentage {
  font-weight: 700;
  font-size: 16px;
}

.speed-control-bar {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  flex-wrap: wrap;
}

.speed-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sub-label {
  font-size: 12px;
  opacity: 0.7;
}

.telemetry-grid {
  margin-top: 4px;
}

.telemetry-card {
  background: rgba(128, 128, 128, 0.05);
  border: 1px solid rgba(128, 128, 128, 0.1);
  padding: 12px 14px;
  border-radius: 8px;
  text-align: center;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  min-height: 84px;
}

.card-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.speed-tag {
  font-weight: 600;
  font-size: 10px;
}

.card-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  opacity: 0.65;
}

.card-value {
  font-size: 18px;
  font-weight: 700;
  margin: 2px 0;
}

.card-value.highlight {
  color: #2080f0;
}

.card-value.warning {
  color: #f0a020;
}

.card-value.danger {
  color: #d03050;
}

.text-success {
  color: #18a058;
}

.text-warning {
  color: #f0a020;
}

.card-subtext {
  font-size: 11px;
  opacity: 0.65;
  font-family: monospace;
}

.preset-card {
  background: rgba(128, 128, 128, 0.03);
  border-radius: 8px;
}

.preset-container {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.preset-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  opacity: 0.85;
}

.preset-select-group {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.preset-desc-text {
  font-size: 12px;
}

.config-tabs {
  background: transparent;
}

.sub-section-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
  opacity: 0.85;
  border-left: 3px solid #2080f0;
  padding-left: 8px;
}

.slider-val {
  margin-left: 12px;
  font-size: 12px;
  opacity: 0.8;
  font-family: monospace;
}

.factor-group-card {
  border-radius: 8px;
}
</style>
