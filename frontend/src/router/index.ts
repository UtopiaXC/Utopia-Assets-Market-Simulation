import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'dashboard',
      component: () => import('../views/Dashboard.vue'),
      meta: { title: 'Dashboard' }
    },
    {
      path: '/market',
      name: 'market',
      component: () => import('../views/Market.vue'),
      meta: { title: 'Market Overview' }
    },
    {
      path: '/stocks',
      name: 'stocks',
      component: () => import('../views/Stocks.vue'),
      meta: { title: 'Stock Analysis' }
    },
    {
      path: '/stocks/:stockId',
      name: 'stockDetail',
      component: () => import('../views/StockDetail.vue'),
      meta: { title: 'Stock Detail' }
    },
    {
      path: '/traders',
      name: 'traders',
      component: () => import('../views/Traders.vue'),
      meta: { title: 'Trader Analysis' }
    },
    {
      path: '/traders/:traderId',
      name: 'traderDetail',
      component: () => import('../views/TraderDetail.vue'),
      meta: { title: 'Trader Detail' }
    },
    {
      path: '/macro',
      name: 'macro',
      component: () => import('../views/Macro.vue'),
      meta: { title: 'Macro Statistics' }
    },
    {
      path: '/sectors',
      name: 'sectors',
      component: () => import('../views/Sectors.vue'),
      meta: { title: 'Sector Analysis' }
    },
    {
      path: '/compare',
      name: 'compare',
      component: () => import('../views/Compare.vue'),
      meta: { title: 'Compare Simulations' }
    },
    {
      path: '/control',
      name: 'control',
      component: () => import('../views/Control.vue'),
      meta: { title: 'Simulation Control' }
    },
    {
      path: '/results',
      name: 'results',
      component: () => import('../views/Results.vue'),
      meta: { title: 'Manage Results' }
    }
  ]
})

// Update page title
router.beforeEach((to, from, next) => {
  document.title = `${to.meta.title || 'Utopia Market'} - Utopia Market Simulation`
  next()
})

export default router
