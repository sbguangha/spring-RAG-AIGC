import axios, { AxiosError } from 'axios'
import type { ApiResponse, GatewayRoute } from '@/types'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('aigc_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResponse<unknown>>) => {
    const message = error.response?.data?.message || error.message || '未知错误'
    console.error('[API Error]', message)
    return Promise.reject(error)
  }
)

export default api

export const gatewayApi = {
  getRoutes: () => api.get<GatewayRoute[]>('/actuator/gateway/routes').catch(() => ({ data: [] })),
  health: () => api.get('/actuator/health').catch(() => ({ data: { status: 'UNKNOWN' } })),
}

export const knowledgeApi = {
  parseDocument: (file: File, withIndex = true) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('withIndex', String(withIndex))
    return api.post('/knowledge/api/document/parse', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  parseAndIndex: (file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/knowledge/api/document/parse-and-index', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}

export const aiApi = {
  streamChat: (query: string, sessionId?: string) => fetch('/api/ai/api/rag/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ query, sessionId }),
  }),
}

export const contentApi = {
  createImageTask: (prompt: string) => api.post('/content/api/image/generate', { prompt }),
  getImageTask: (taskId: string) => api.get(`/content/api/image/status/${taskId}`),
}
