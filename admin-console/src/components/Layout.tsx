import { NavLink, Outlet } from 'react-router-dom'
import {
  LayoutDashboard,
  FileText,
  MessageSquare,
  Sparkles,
  Activity,
  Terminal,
} from 'lucide-react'

const navItems = [
  { to: '/', icon: LayoutDashboard, label: '总览' },
  { to: '/knowledge', icon: FileText, label: '知识库' },
  { to: '/rag', icon: MessageSquare, label: 'RAG 问答' },
  { to: '/content', icon: Sparkles, label: '内容生成' },
]

export default function Layout() {
  return (
    <div className="flex min-h-screen font-body">
      <aside className="fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-ink-700/60 bg-ink-900/80 backdrop-blur-md">
        <div className="flex h-16 items-center gap-3 border-b border-ink-700/60 px-6">
          <div className="relative flex h-9 w-9 items-center justify-center rounded-lg bg-signal-cyan/10">
            <Terminal className="h-5 w-5 text-signal-cyan" />
            <span className="status-dot absolute right-1 top-1 bg-signal-emerald" />
          </div>
          <div className="flex flex-col">
            <span className="font-display text-lg font-bold leading-none tracking-tight text-ink-50">
              AIGC
            </span>
            <span className="text-[10px] font-medium uppercase tracking-[0.2em] text-ink-400">
              Control Room
            </span>
          </div>
        </div>

        <nav className="flex-1 space-y-1 px-3 py-6">
          {navItems.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end={item.to === '/'}
              className={({ isActive }) =>
                `nav-link ${isActive ? 'active' : ''}`
              }
            >
              <item.icon className="h-[18px] w-[18px]" />
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="border-t border-ink-700/60 p-4">
          <div className="panel flex items-center gap-3 px-4 py-3">
            <Activity className="h-4 w-4 text-signal-emerald" />
            <div className="flex flex-col">
              <span className="text-xs text-ink-400">网关状态</span>
              <span className="text-sm font-semibold text-signal-emerald">在线 · 8080</span>
            </div>
          </div>
        </div>
      </aside>

      <main className="ml-64 flex min-h-screen flex-1 flex-col">
        <div className="flex-1 p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
