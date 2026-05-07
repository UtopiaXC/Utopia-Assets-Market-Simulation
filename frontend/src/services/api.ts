import axios from 'axios'

// Use relative URL path for same-origin requests
const api = axios.create({
    baseURL: '/api',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// Request interceptor
api.interceptors.request.use(
    config => {
        return config
    },
    error => {
        return Promise.reject(error)
    }
)

// Response interceptor
api.interceptors.response.use(
    response => {
        return response.data
    },
    error => {
        console.error('API Error:', error)
        return Promise.reject(error)
    }
)

// Simulation API
export const simulationApi = {
    listSimulations: () => api.get('/simulations'),
    getSimulationInfo: (fileName: string) => api.get(`/simulations/${fileName}/info`),
    renameSimulation: (oldName: string, newName: string) =>
        api.post(`/simulations/${encodeURIComponent(oldName)}/rename`, { newName }),
    deleteSimulation: (name: string) =>
        api.delete(`/simulations/${encodeURIComponent(name)}`)
}

// Market API
export const marketApi = {
    getOverview: (dbFile: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/market`, { params: { day } }),
    getKlineData: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/market/kline`),
    getTotalDays: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/market/days`),
    getTopStocks: (dbFile: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/market/top-stocks`, { params: { day } })
}

// Stock API
export const stockApi = {
    getList: (dbFile: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/stocks`, { params: { day } }),
    getDetail: (dbFile: string, stockId: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}`, { params: { day } }),
    getHistory: (dbFile: string, stockId: string) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/history`),
    getShareholders: (dbFile: string, stockId: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/shareholders`, { params: { day } }),
    getTrades: (dbFile: string, stockId: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/trades`, { params: { day } })
}

// Trader API
export const traderApi = {
    getList: (dbFile: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/traders`, { params: { day } }),
    getDetail: (dbFile: string, traderId: number, day: number = 1) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}`, { params: { day } }),
    getHistory: (dbFile: string, traderId: number) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/history`),
    getHoldings: (dbFile: string, traderId: number, day: number = 1) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/holdings`, { params: { day } }),
    getTrades: (dbFile: string, traderId: number, day: number = 1) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/trades`, { params: { day } })
}

// Macro API
export const macroApi = {
    getStats: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/macro`),
    getPopulation: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/macro/population`),
    getWealth: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/macro/wealth`)
}

// Sector API
export const sectorApi = {
    getStats: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/sectors`),
    getSectorList: (dbFile: string) =>
        api.get(`/simulations/${dbFile}/sectors/list`),
    getSectorStocks: (dbFile: string, sector: string, day: number = 1) =>
        api.get(`/simulations/${dbFile}/sectors/${sector}/stocks`, { params: { day } })
}

// Simulation Control API
export const controlApi = {
    // Status
    getStatus: () => api.get('/control/status'),
    getMetrics: () => api.get('/control/metrics'),

    // Lifecycle
    start: (config?: any) => api.post('/control/start', config || {}),
    pause: () => api.post('/control/pause'),
    resume: () => api.post('/control/resume'),
    stop: () => api.post('/control/stop'),

    // Speed
    setSpeed: (multiplier: number) => api.post('/control/speed', null, { params: { multiplier } }),

    // Config
    getDefaultConfig: () => api.get('/control/config/default'),
    getSectors: () => api.get('/control/sectors'),

    // Events
    getPendingEvents: () => api.get('/control/events/pending'),
    getEventHistory: () => api.get('/control/events/history'),
    cancelEvent: (eventId: string) => api.delete(`/control/events/${eventId}`),

    // Event Injection
    injectRateCut: (targetDay: number, liquidityPerAgent: number, riskBoost: number) =>
        api.post('/control/events/rate-cut', { targetDay, liquidityPerAgent, riskBoost }),
    injectRateHike: (targetDay: number, liquidityRatio: number, riskDrop: number) =>
        api.post('/control/events/rate-hike', { targetDay, liquidityRatio, riskDrop }),
    injectSectorSentiment: (targetDay: number, sector: string, multiplier: number) =>
        api.post('/control/events/sector-sentiment', { targetDay, sector, multiplier }),
    injectSectorFundamental: (targetDay: number, sector: string, epsChange: number) =>
        api.post('/control/events/sector-fundamental', { targetDay, sector, epsChange }),

    // LLM Context (placeholder)
    getLlmContext: () => api.get('/control/llm/context'),
    getAgentState: (agentId: number) => api.get(`/control/llm/agent/${agentId}`)
}

export default api

