import { useEffect, useState } from 'react'
import {
  Database,
  Cpu,
  MessageSquare,
  Image as ImageIcon,
  Route,
  RefreshCw,
} from 'lucide-react'
import ServiceCard from '@/components/ServiceCard'
import StatCard from '@/components/StatCard'
import { gatewayApi } from '@/services/api'
import type { ServiceInfo } from '@/types'

const services: ServiceInfo[] = [
  {
    id: 'knowledge',
    name: 'knowledge-parse-service',
    description: '文档解析 / 切片 / 向量入库',
    routePrefix: '/api/knowledge',
    actuatorPath: '/actuator/health',
    healthy: null,
    latencyMs: null,
    version: '1.0.0',
    instances: 1,
  },
  {
    id: 'ai',
    name: 'ai-orchestration-service',
    description: 'RAG 检索 / 重排 / 大模型问答编排',
    routePrefix: '/api/ai',
    actuatorPath: '/actuator/health',
    healthy: null,
    latencyMs: null,
    version: '1.0.0',
    instances: 1,
  },
  {
    id: 'content',
    name: 'content-gen-service',
    description: '内容生成 / 图片任务队列',
    routePrefix: '/api/content',
    actuatorPath: '/actuator/health',
    healthy: null,
    latencyMs: null,
    version: '1.0.0',
    instances: 1,
  },
]

export default function Dashboard() {
  const [routeCount, setRouteCount] = useState(0)
  const [healthStatus, setHealthStatus] = useState('UNKNOWN')
  const [lastRefresh, setLastRefresh] = useState(new Date())

  const refresh = async () => {
    const [routes, health] = await Promise.all([
      gatewayApi.getRoutes(),
      gatewayApi.health(),
    ])
    setRouteCount(routes.data.length)
    setHealthStatus((health.data as { status: string }).status)
    setLastRefresh(new Date())
  }

  useEffect(() => {
    refresh()
    const timer = setInterval(refresh, 15000)
    return () => clearInterval(timer)
  }, [])

  return (
    <div className="animate-fade-in space-y-8">
      <header className="flex items-end justify-between">
        <div>
          <h1 className="font-display text-4xl font-bold tracking-tight text-ink-50">
            平台总览
          </h1>
          <p className="mt-2 max-w-2xl text-ink-400">
            通过 Spring Cloud Gateway 统一入口监控所有 AIGC 微服务、向量存储与任务队列。
          </p>
        </div>
        <button onClick={refresh} className="btn-secondary">
          <RefreshCw className="h-4 w-4" />
          刷新
        </button>
      </header>

      <section className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="文档切片"
          value="12.4K"
          unit="chunks"
          trend="较昨日 +8%"
          icon={Database}
          accent="cyan"
        />
        <StatCard
          label="RAG 问答"
          value="3.2K"
          unit="次"
          trend="平均延迟 420ms"
          icon={MessageSquare}
          accent="emerald"
        />
        <StatCard
          label="生成任务"
          value="846"
          unit="个"
          trend="队列中 12 个"
          icon={ImageIcon}
          accent="amber"
        />
        <StatCard
          label="网关路由"
          value={routeCount}
          unit="条"
          trend={healthStatus === 'UP' ? 'Gateway 健康' : 'Gateway 未知'}
          icon={Route}
          accent="violet"
        />
      </section>

      <section className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        {services.map((service) => (
          <ServiceCard key={service.id} service={service} />
        ))}
      </section>

      <section className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="panel p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-display text-lg font-semibold text-ink-50">
              基础设施状态
            </h2>
            <Cpu className="h-5 w-5 text-ink-500" />
          </div>
          <div className="space-y-3">
            {[
              { name: 'Nacos 注册中心', status: 'UP', port: '8848' },
              { name: 'Redis 缓存', status: 'UP', port: '6379' },
              { name: 'Elasticsearch', status: 'UP', port: '9200' },
              { name: 'Milvus 向量库', status: 'UP', port: '19530' },
            ].map((infra) => (
              <div
                key={infra.name}
                className="flex items-center justify-between rounded-lg border border-ink-700/40 bg-ink-950/40 px-4 py-3"
              >
                <div className="flex items-center gap-3">
                  <span className="status-dot bg-signal-emerald" />
                  <span className="text-sm font-medium text-ink-200">{infra.name}</span>
                </div>
                <div className="flex items-center gap-4">
                  <span className="font-mono text-xs text-ink-500">:{infra.port}</span>
                  <span className="rounded-full bg-signal-emerald/10 px-2 py-0.5 text-[10px] font-semibold uppercase text-signal-emerald">
                    {infra.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="panel p-6">
          <div className="mb-4 flex items-center justify-between">
            <h2 className="font-display text-lg font-semibold text-ink-50">
              最近网关日志
            </h2>
            <span className="text-xs text-ink-500">
              刷新于 {lastRefresh.toLocaleTimeString()}
            </span>
          </div>
          <div className="space-y-2 font-mono text-xs">
            {[
              '200 GET /api/ai/rag 420ms',
              '200 POST /api/knowledge/parse 1.2s',
              '200 GET /api/content/tasks 88ms',
              '429 GET /api/ai/rag 0ms',
              '200 POST /api/content/image 2.4s',
            ].map((log, idx) => (
              <div
                key={idx}
                className="flex items-center gap-3 rounded bg-ink-950/40 px-3 py-2 text-ink-300"
              >
                <span className={log.startsWith('200') ? 'text-signal-emerald' : 'text-signal-amber'}>
                  {log.split(' ')[0]}
                </span>
                <span className="text-ink-400">{log.split(' ').slice(1).join(' ')}</span>
              </div>
            ))}
          </div>
        </div>
      </section>
    </div>
  )
}
