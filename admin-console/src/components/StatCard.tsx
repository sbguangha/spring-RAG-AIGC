import { LucideIcon } from 'lucide-react'

interface StatCardProps {
  label: string
  value: string | number
  unit?: string
  trend?: string
  icon: LucideIcon
  accent: 'cyan' | 'amber' | 'emerald' | 'rose' | 'violet'
}

const accentMap = {
  cyan: 'text-signal-cyan border-signal-cyan/20 bg-signal-cyan/10',
  amber: 'text-signal-amber border-signal-amber/20 bg-signal-amber/10',
  emerald: 'text-signal-emerald border-signal-emerald/20 bg-signal-emerald/10',
  rose: 'text-signal-rose border-signal-rose/20 bg-signal-rose/10',
  violet: 'text-signal-violet border-signal-violet/20 bg-signal-violet/10',
}

export default function StatCard({
  label,
  value,
  unit,
  trend,
  icon: Icon,
  accent,
}: StatCardProps) {
  return (
    <div className="panel p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-xs font-medium uppercase tracking-wider text-ink-500">
          {label}
        </span>
        <div
          className={`flex h-8 w-8 items-center justify-center rounded-lg border ${accentMap[accent]}`}
        >
          <Icon className="h-4 w-4" />
        </div>
      </div>
      <div className="flex items-baseline gap-2">
        <span className="font-display text-3xl font-bold tracking-tight text-ink-50">
          {value}
        </span>
        {unit && (
          <span className="text-sm font-medium text-ink-400">{unit}</span>
        )}
      </div>
      {trend && (
        <div className="mt-2 text-xs text-ink-400">{trend}</div>
      )}
    </div>
  )
}
