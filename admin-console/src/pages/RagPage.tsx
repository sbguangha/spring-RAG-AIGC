import { useState, useRef, useCallback } from 'react'
import { Send, Bot, User, Loader2, Square } from 'lucide-react'
import { aiApi } from '@/services/api'

interface Message {
  id: string
  role: 'user' | 'assistant'
  content: string
}

export default function RagPage() {
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const abortRef = useRef<AbortController | null>(null)
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      role: 'assistant',
      content: '你好，我已连接 ai-orchestration-service。可以基于已入库的知识库向我提问。',
    },
  ])

  const stop = useCallback(() => {
    abortRef.current?.abort()
    abortRef.current = null
    setLoading(false)
  }, [])

  const send = async () => {
    if (!input.trim() || loading) return
    const question = input.trim()
    setInput('')
    setMessages((prev) => [
      ...prev,
      { id: Date.now().toString(), role: 'user', content: question },
    ])
    setLoading(true)

    const assistantId = (Date.now() + 1).toString()
    setMessages((prev) => [
      ...prev,
      { id: assistantId, role: 'assistant', content: '' },
    ])

    abortRef.current = new AbortController()

    try {
      const res = await aiApi.streamChat(question)
      if (!res.ok || !res.body) {
        throw new Error(`服务返回 ${res.status}`)
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let done = false

      while (!done) {
        const { value, done: streamDone } = await reader.read()
        done = streamDone
        if (value) {
          const chunk = decoder.decode(value, { stream: true })
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantId
                ? { ...msg, content: msg.content + chunk }
                : msg
            )
          )
        }
      }
    } catch (err) {
      const errorMsg = err instanceof Error && err.name === 'AbortError'
        ? '已停止生成。'
        : '调用 RAG 服务失败，请检查网关路由、ai-orchestration-service 日志和模型配置。'
      setMessages((prev) =>
        prev.map((msg) =>
          msg.id === assistantId
            ? { ...msg, content: errorMsg }
            : msg
        )
      )
    } finally {
      abortRef.current = null
      setLoading(false)
    }
  }

  return (
    <div className="animate-fade-in flex h-[calc(100vh-8rem)] flex-col gap-6">
      <header>
        <h1 className="font-display text-4xl font-bold tracking-tight text-ink-50">
          RAG 问答
        </h1>
        <p className="mt-2 text-ink-400">
          向 ai-orchestration-service 发起检索增强问答，请求通过 /api/ai/** 路由。
        </p>
      </header>

      <div className="panel flex flex-1 flex-col overflow-hidden">
        <div className="flex-1 space-y-5 overflow-y-auto p-6">
          {messages.map((msg) => (
            <div
              key={msg.id}
              className={`flex gap-4 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}
            >
              <div
                className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-full ${
                  msg.role === 'user' ? 'bg-signal-violet/20' : 'bg-signal-cyan/20'
                }`}
              >
                {msg.role === 'user' ? (
                  <User className="h-4 w-4 text-signal-violet" />
                ) : (
                  <Bot className="h-4 w-4 text-signal-cyan" />
                )}
              </div>
              <div
                className={`max-w-2xl whitespace-pre-wrap rounded-2xl px-5 py-3 text-sm leading-relaxed ${
                  msg.role === 'user'
                    ? 'bg-signal-violet/15 text-ink-100'
                    : 'bg-ink-800/60 text-ink-100'
                }`}
              >
                {msg.content || (msg.role === 'assistant' ? '…' : '')}
              </div>
            </div>
          ))}
          {loading && (
            <div className="flex gap-4">
              <div className="flex h-9 w-9 items-center justify-center rounded-full bg-signal-cyan/20">
                <Loader2 className="h-4 w-4 animate-spin text-signal-cyan" />
              </div>
              <div className="rounded-2xl bg-ink-800/60 px-5 py-3 text-sm text-ink-300">
                正在检索向量库并生成答案…
              </div>
            </div>
          )}
        </div>

        <div className="border-t border-ink-700/60 p-4">
          <div className="flex items-center gap-3 rounded-xl border border-ink-600 bg-ink-950/60 px-4 py-3">
            <label htmlFor="rag-input" className="sr-only">
              输入问题
            </label>
            <input
              id="rag-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && send()}
              placeholder="输入问题，例如：如何配置 Milvus 向量维度？"
              className="flex-1 bg-transparent text-sm text-ink-100 placeholder-ink-500 outline-none"
            />
            {loading ? (
              <button
                onClick={stop}
                className="btn-secondary h-9 w-9 p-0"
                aria-label="停止生成"
              >
                <Square className="h-4 w-4 fill-current" />
              </button>
            ) : (
              <button
                onClick={send}
                disabled={!input.trim()}
                className="btn-primary h-9 w-9 p-0 disabled:opacity-50"
                aria-label="发送"
              >
                <Send className="h-4 w-4" />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
