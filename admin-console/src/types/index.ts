export interface ServiceInfo {
  id: string
  name: string
  description: string
  routePrefix: string
  actuatorPath: string
  healthy: boolean | null
  latencyMs: number | null
  version: string
  instances: number
}

export interface GatewayRoute {
  route_id: string
  uri: string
  predicates: { name: string; args: Record<string, string> }[]
}

export interface DocumentParseTask {
  id: string
  fileName: string
  status: 'pending' | 'processing' | 'completed' | 'failed'
  chunks: number
  createdAt: string
}

export interface RagQaRecord {
  id: string
  question: string
  answer: string
  sources: string[]
  createdAt: string
}

export interface ContentTask {
  id: string
  type: 'image' | 'text'
  prompt: string
  status: 'queued' | 'running' | 'done' | 'error'
  createdAt: string
}

export type ApiResponse<T> = {
  code: number
  message: string
  data: T
}
