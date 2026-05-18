import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import axios from 'axios'
import { simulationApi, marketApi } from '@/services/api'

export interface SimulationFile {
    name: string
    path: string
    lastModified: number
    size: number
}

export const useSimulationStore = defineStore('simulation', () => {
    // State
    const simulations = ref<SimulationFile[]>([])
    const currentSimulation = ref<string | null>(null)
    const currentDay = ref<number>(1)
    const totalDays = ref<number>(100)
    const loading = ref<boolean>(false)
    const error = ref<string | null>(null)

    // Getters
    const isConnected = computed(() => currentSimulation.value !== null)

    const currentSimulationInfo = computed(() => {
        if (!currentSimulation.value) return null
        return simulations.value.find(s => s.name === currentSimulation.value)
    })

    // Actions
    async function fetchSimulations() {
        loading.value = true
        error.value = null
        try {
            const data = await simulationApi.listSimulations()
            simulations.value = data as unknown as SimulationFile[]
        } catch (e: any) {
            if (axios.isCancel(e)) {
                return
            }
            error.value = e.message || 'Failed to fetch simulations'
            console.error('Failed to fetch simulations:', e)
        } finally {
            loading.value = false
        }
    }

    async function selectSimulation(fileName: string) {
        loading.value = true
        error.value = null
        try {
            currentSimulation.value = fileName

            // Get total days
            const daysData = await marketApi.getTotalDays(fileName) as unknown as { totalDays: number }
            totalDays.value = daysData.totalDays || 100
            currentDay.value = 1
        } catch (e: any) {
            if (axios.isCancel(e)) {
                return
            }
            error.value = e.message || 'Failed to connect to simulation'
            currentSimulation.value = null
            console.error('Failed to select simulation:', e)
        } finally {
            loading.value = false
        }
    }

    function setDay(day: number) {
        if (day >= 1 && day <= totalDays.value) {
            currentDay.value = day
        }
    }

    function disconnect() {
        currentSimulation.value = null
        currentDay.value = 1
        totalDays.value = 100
    }

    return {
        // State
        simulations,
        currentSimulation,
        currentDay,
        totalDays,
        loading,
        error,
        // Getters
        isConnected,
        currentSimulationInfo,
        // Actions
        fetchSimulations,
        selectSimulation,
        setDay,
        disconnect
    }
})
