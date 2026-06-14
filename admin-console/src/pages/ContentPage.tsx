import { useState } from 'react'
import { Image as ImageIcon, Play, Clock } from 'lucide-react'
import { contentApi } from '@/services/api'

const statusMap: Record<string, string> = {
  PENDING: '排队中',
  GENERATING: '生成中',
  SUCCESS: '完成',
  FAILED: '失败',
  NOT_FOUND: '不存在',
}

export default function ContentPage() {
  const [prompt, setPrompt] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [tasks, setTasks] = useState<{ id: string; prompt: string; status: string }[]>([
    { id: 'task-demo', prompt: '未来城市夜景插画', status: 'SUCCESS' },
  ])

  const submit = async () => {
    if (!prompt.trim() || submitting) return
    setSubmitting(true)
    try {
      const res = await contentApi.createImageTask(prompt)
      const data = res.data as { taskId: string; prompt: string; status: string }
      setTasks((prev) => [
        { id: data.taskId, prompt: data.prompt, status: data.status },
        ...prev,
      ])
      setPrompt('')
    } finally {
      setSubmitting(false)
    }
  }

  const refreshTask = async (taskId: string) => {
    try {
      const res = await contentApi.getImageTask(taskId)
      const data = res.data as { taskId: string; prompt: string; status: string }
      setTasks((prev) =>
        prev.map((t) => (t.id === taskId ? { ...t, status: data.status } : t))
      )
    } catch {
      setTasks((prev) =>
        prev.map((t) => (t.id === taskId ? { ...t, status: 'FAILED' } : t))
      )
    }
  }

  return (
    <div className="animate-fade-in space-y-8">
      <header>
        <h1 className="font-display text-4xl font-bold tracking-tight text-ink-50">
          图片生成
        </h1>
        <p className="mt-2 text-ink-400">
          提交图片生成任务到 content-gen-service，任务由 Redis 队列异步消费。
        </p>
      </header>

      <section className="panel p-6">
        <label htmlFor="prompt-input" className="mb-3 block text-sm font-medium text-ink-300">
          提示词
        </label>
        <textarea
          id="prompt-input"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="描述你想生成的图片…"
          rows={5}
          className="w-full resize-none rounded-xl border border-ink-600 bg-ink-950/60 p-4 text-sm text-ink-100 placeholder-ink-500 outline-none ring-signal-cyan/20 transition-all focus:border-signal-cyan/50 focus:ring-2 focus:ring-signal-cyan/20"
        />

        <div className="mt-4 flex items-center justify-between">
          <div className="text-xs text-ink-500">
            请求路径：<span className="font-mono text-ink-400">/api/content/api/image/generate</span>
          </div>
          <button
            onClick={submit}
            disabled={submitting || !prompt.trim()}
            className="btn-primary"
          >
            <Play className="h-4 w-4" />
            {submitting ? '提交中…' : '提交任务'}
          </button>
        </div>
      </section>

      <section className="panel overflow-hidden">
        <div className="flex items-center justify-between border-b border-ink-700/60 px-6 py-4">
          <h2 className="font-display text-lg font-semibold text-ink-50">任务队列</h2>
          <button className="btn-secondary h-8 px-3 text-xs">
            <Clock className="h-3.5 w-3.5" />
            刷新
          </button>
        </div>
        <div className="divide-y divide-ink-700/40">
          {tasks.map((task) => (
            <div
              key={task.id}
              className="flex items-center justify-between px-6 py-4 hover:bg-ink-800/30"
            >
              <div className="flex items-center gap-4">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-ink-950/60 text-signal-amber">
                  <ImageIcon className="h-5 w-5" />
                </div>
                <div>
                  <div className="max-w-md truncate text-sm font-medium text-ink-200">{task.prompt}</div>
                  <div className="mt-0.5 flex items-center gap-3 text-xs text-ink-500">
                    <span className="font-mono">{task.id}</span>
                    <span>图片生成</span>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3">
                <span
                  className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${
                    task.status === 'SUCCESS'
                      ? 'bg-signal-emerald/10 text-signal-emerald'
                      : task.status === 'GENERATING'
                        ? 'bg-signal-cyan/10 text-signal-cyan'
                        : task.status === 'FAILED' || task.status === 'NOT_FOUND'
                          ? 'bg-signal-rose/10 text-signal-rose'
                          : 'bg-ink-700/30 text-ink-400'
                  }`}
                >
                  {statusMap[task.status] || task.status}
                </span>
                <button
                  onClick={() => refreshTask(task.id)}
                  className="text-xs text-signal-cyan hover:underline"
                >
                  刷新
                </button>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
