import { ArrowUpRight, Server, AlertCircle } from 'lucide-react'
import type { ServiceInfo } from '@/types'

interface ServiceCardProps {
  service: ServiceInfo
}

export default function ServiceCard({ service }: ServiceCardProps) {
  const statusColor =
    service.healthy === null
      ? 'bg-ink-400'
      : service.healthy
        ? 'bg-signal-emerald'
        : 'bg-signal-rose'

  return (
    <div className="panel group p-5 transition-all hover:border-ink-600">
      <div className="mb-4 flex items-start justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg border border-ink-600 bg-ink-800/60">
            <Server className="h-5 w-5 text-signal-cyan" />
          </div>
          <div>
            <h3 className="font-display text-base font-semibold text-ink-50">
              {service.name}
            </h3>
            <p className="text-xs text-ink-400">{service.description}</p>
          </div>
        </div>
        <div className="flex items-center gap-2">
          <span className={`status-dot ${statusColor}`} />
          <span className="text-xs font-medium text-ink-300">
            {service.healthy === null ? '检测中' : service.healthy ? '健康' : '异常'}
          </span>
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="rounded-lg border border-ink-700/40 bg-ink-950/40 p-3">
          <span className="block text-[10px] uppercase tracking-wider text-ink-500">延迟</span>
          <span className="font-mono text-sm font-medium text-ink-100">
            {service.latencyMs ? `${service.latencyMs}ms` : '—'}
          </span>
        </div>
        <div className="rounded-lg border border-ink-700/40 bg-ink-950/40 p-3">
          <span className="block text-[10px] uppercase tracking-wider text-ink-500">实例</span>
          <span className="font-mono text-sm font-medium text-ink-100">{service.instances}</span>
        </div>
      </div>

      {service.healthy === false && (
        <div className="mt-4 flex items-center gap-2 rounded-lg border border-signal-rose/20 bg-signal-rose/10 px-3 py-2 text-xs text-signal-rose">
          <AlertCircle className="h-3.5 w-3.5" />
          服务未响应，请检查 Nacos 注册中心
        </div>
      )}

      <div className="mt-4 flex items-center justify-between border-t border-ink-700/40 pt-3">
        <span className="font-mono text-[10px] text-ink-500">{service.routePrefix}</span>
        <button className="btn-secondary h-8 px-3 text-xs">
          详情
          <ArrowUpRight className="h-3.5 w-3.5" />
        </button>
      </div>
    </div>
  )
}
