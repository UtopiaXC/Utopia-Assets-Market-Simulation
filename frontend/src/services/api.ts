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
        config.cancelToken = cancelTokenSource.token
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
        if (axios.isCancel(error)) {// Suppress the console log for silent cancellation on tab changes
            return Promise.reject(error)
        }
        console.error('API Error:', error)
        return Promise.reject(error)
    }
)

let cancelTokenSource = axios.CancelToken.source()

export const cancelAllRequests = () => {
    cancelTokenSource.cancel('Operation canceled by the user due to tab/route change.')
    cancelTokenSource = axios.CancelToken.source()
}

// Simulation API
export const simulationApi = {
    listSimulations: (signal?: AbortSignal) => api.get('/simulations', { signal }),
    getSimulationInfo: (fileName: string, signal?: AbortSignal) => api.get(`/simulations/${fileName}/info`, { signal }),
    renameSimulation: (oldName: string, newName: string) =>
        api.post(`/simulations/${encodeURIComponent(oldName)}/rename`, { newName }),
    deleteSimulation: (name: string) =>
        api.delete(`/simulations/${encodeURIComponent(name)}`)
}

// Market API
export const marketApi = {
    getOverview: (dbFile: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/market`, { params: { day }, signal }),
    getKlineData: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/market/kline`, { signal }),
    getTotalDays: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/market/days`, { signal }),
    getTopStocks: (dbFile: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/market/top-stocks`, { params: { day }, signal })
}

// Stock API
export const stockApi = {
    getList: (dbFile: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/stocks`, { params: { day }, signal }),
    getDetail: (dbFile: string, stockId: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}`, { params: { day }, signal }),
    getHistory: (dbFile: string, stockId: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/history`, { signal }),
    getShareholders: (dbFile: string, stockId: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/shareholders`, { params: { day }, signal }),
    getTrades: (dbFile: string, stockId: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/stocks/${stockId}/trades`, { params: { day }, signal })
}

// Trader API
export const traderApi = {
    getList: (dbFile: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders`, { params: { day }, signal }),
    getDetail: (dbFile: string, traderId: number, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}`, { params: { day }, signal }),
    getHistory: (dbFile: string, traderId: number, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/history`, { signal }),
    getHoldings: (dbFile: string, traderId: number, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/holdings`, { params: { day }, signal }),
    getTrades: (dbFile: string, traderId: number, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/trades`, { params: { day }, signal }),
    getAllTrades: (dbFile: string, traderId: number, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/traders/${traderId}/trades/all`, { signal })
}

// Macro API
export const macroApi = {
    getStats: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/macro`, { signal }),
    getPopulation: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/macro/population`, { signal }),
    getWealth: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/macro/wealth`, { signal })
}

// Sector API
export const sectorApi = {
    getStats: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/sectors`, { signal }),
    getSectorList: (dbFile: string, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/sectors/list`, { signal }),
    getSectorStocks: (dbFile: string, sector: string, day: number = 1, signal?: AbortSignal) =>
        api.get(`/simulations/${dbFile}/sectors/${sector}/stocks`, { params: { day }, signal })
}

// Scenario API
export const scenarioApi = {
    list: (signal?: AbortSignal) => api.get('/scenarios', { signal })
}

// Simulation Control API
export const controlApi = {
    // Status
    getStatus: (signal?: AbortSignal) => api.get('/control/status', { signal }),
    getMetrics: (signal?: AbortSignal) => api.get('/control/metrics', { signal }),

    // Lifecycle
    start: (config?: any) => api.post('/control/start', config || {}),
    pause: () => api.post('/control/pause'),
    resume: () => api.post('/control/resume'),
    stop: () => api.post('/control/stop'),

    // Speed
    setSpeed: (multiplier: number) => api.post('/control/speed', null, { params: { multiplier } }),

    // Config
    getDefaultConfig: (signal?: AbortSignal) => api.get('/control/config/default', { signal }),
    getSectors: (signal?: AbortSignal) => api.get('/control/sectors', { signal }),

    // Policy Slot
    getCurrentPolicy: (signal?: AbortSignal) => api.get('/control/policy', { signal }),

    // Policy Event Injection
    injectPolicyEvent: (day: number, policyType: string, value: number, description?: string) =>
        api.post('/control/policy/inject', { day, policyType, value, description })
}

export default api
