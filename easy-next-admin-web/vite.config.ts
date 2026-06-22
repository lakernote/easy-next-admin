import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [vue()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      port: 5174,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true
        },
        '/storage': {
          target: apiProxyTarget,
          changeOrigin: true
        },
        '/swagger-ui.html': {
          target: apiProxyTarget,
          changeOrigin: true
        },
        '/swagger-ui': {
          target: apiProxyTarget,
          changeOrigin: true
        },
        '/v3/api-docs': {
          target: apiProxyTarget,
          changeOrigin: true
        }
      }
    },
    build: {
      chunkSizeWarningLimit: 1200,
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) {
              return undefined
            }

            if (id.includes('/element-plus/') || id.includes('/@element-plus/')) {
              return 'vendor-element-plus'
            }

            if (id.includes('/@logicflow/')) {
              return 'vendor-logicflow'
            }

            if (
              id.includes('/vue/') ||
              id.includes('/@vue/') ||
              id.includes('/vue-router/') ||
              id.includes('/pinia/')
            ) {
              return 'vendor-vue'
            }

            if (id.includes('/axios/')) {
              return 'vendor-axios'
            }

            if (id.includes('/echarts/') || id.includes('/zrender/')) {
              return 'vendor-echarts'
            }

            return 'vendor'
          }
        }
      }
    }
  }
})
