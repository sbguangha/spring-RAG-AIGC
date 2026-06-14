import { useState, useRef } from 'react'
import { Upload, FileText } from 'lucide-react'
import { knowledgeApi } from '@/services/api'

const mockTasks = [
  { id: 'doc-001', fileName: 'aigc-whitepaper.pdf', status: 'completed', chunks: 142, createdAt: '2024-06-14 10:23' },
  { id: 'doc-002', fileName: 'product-manual.docx', status: 'processing', chunks: 0, createdAt: '2024-06-14 11:05' },
  { id: 'doc-003', fileName: 'api-reference.md', status: 'failed', chunks: 0, createdAt: '2024-06-14 09:40' },
]

const statusMap: Record<string, { label: string; color: string }> = {
  pending: { label: '等待中', color: 'text-ink-400 bg-ink-700/30' },
  processing: { label: '解析中', color: 'text-signal-cyan bg-signal-cyan/10' },
  completed: { label: '已完成', color: 'text-signal-emerald bg-signal-emerald/10' },
  failed: { label: '失败', color: 'text-signal-rose bg-signal-rose/10' },
}

export default function KnowledgePage() {
  const [dragActive, setDragActive] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [uploadMessage, setUploadMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null)
  const inputRef = useRef<HTMLInputElement>(null)

  const handleUpload = async (file: File) => {
    setUploading(true)
    setUploadMessage(null)
    try {
      const response = await knowledgeApi.parseAndIndex(file)
      setUploadMessage({
        type: 'success',
        text: `上传成功：${file.name}，切片 ${response.data?.chunkCount ?? 0} 个，已提交向量库写入。`,
      })
    } catch (error) {
      setUploadMessage({
        type: 'error',
        text: `上传失败：${file.name}。请检查文件类型、模型配置和向量库写入日志。`,
      })
    } finally {
      setUploading(false)
    }
  }

  const onDrop = (e: React.DragEvent) => {
    e.preventDefault()
    setDragActive(false)
    if (e.dataTransfer.files?.[0]) {
      handleUpload(e.dataTransfer.files[0])
    }
  }

  return (
    <div className="animate-fade-in space-y-8">
      <header>
        <h1 className="font-display text-4xl font-bold tracking-tight text-ink-50">
          知识库管理
        </h1>
        <p className="mt-2 text-ink-400">
          上传文档并触发 knowledge-parse-service 进行解析、切片与 Milvus 向量入库。
        </p>
      </header>

      <section
        onDragOver={(e) => { e.preventDefault(); setDragActive(true) }}
        onDragLeave={() => setDragActive(false)}
        onDrop={onDrop}
        className={`panel flex flex-col items-center justify-center border-2 border-dashed px-8 py-14 transition-all ${
          dragActive
            ? 'border-signal-cyan bg-signal-cyan/5'
            : 'border-ink-600 hover:border-ink-500'
        }`}
      >
        <div className="mb-5 flex h-16 w-16 items-center justify-center rounded-2xl bg-ink-800">
          <Upload className="h-8 w-8 text-signal-cyan" />
        </div>
        <h3 className="font-display text-lg font-semibold text-ink-50">
          拖拽文档到此处，或
          <button
            onClick={() => inputRef.current?.click()}
            className="mx-1 text-signal-cyan hover:underline"
          >
            点击上传
          </button>
        </h3>
        <p className="mt-2 text-sm text-ink-500">
          支持 PDF、TXT（最大 50MB）
        </p>
        <input
          ref={inputRef}
          type="file"
          className="hidden"
          accept=".pdf,.txt"
          aria-label="上传文档"
          onChange={(e) => e.target.files?.[0] && handleUpload(e.target.files[0])}
        />
        {uploading && (
          <div className="mt-4 text-sm text-signal-cyan">正在上传并解析…</div>
        )}
        {uploadMessage && (
          <div className={`mt-4 text-sm ${
            uploadMessage.type === 'success' ? 'text-signal-emerald' : 'text-signal-rose'
          }`}
          >
            {uploadMessage.text}
          </div>
        )}
      </section>

      <section className="panel overflow-hidden">
        <div className="flex items-center justify-between border-b border-ink-700/60 px-6 py-4">
          <h2 className="font-display text-lg font-semibold text-ink-50">解析任务</h2>
          <button className="btn-secondary h-8 px-3 text-xs">查看 Milvus 集合</button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="bg-ink-950/60 text-xs uppercase tracking-wider text-ink-500">
              <tr>
                <th scope="col" className="px-6 py-3 font-medium">文档</th>
                <th scope="col" className="px-6 py-3 font-medium">状态</th>
                <th scope="col" className="px-6 py-3 font-medium">切片数</th>
                <th scope="col" className="px-6 py-3 font-medium">创建时间</th>
                <th scope="col" className="px-6 py-3 font-medium">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink-700/40">
              {mockTasks.map((task) => (
                <tr key={task.id} className="hover:bg-ink-800/30">
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-3">
                      <FileText className="h-4 w-4 text-signal-cyan" />
                      <span className="font-medium text-ink-200">{task.fileName}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <span className={`rounded-full px-2.5 py-0.5 text-xs font-medium ${statusMap[task.status].color}`}>
                      {statusMap[task.status].label}
                    </span>
                  </td>
                  <td className="px-6 py-4 font-mono text-ink-300">{task.chunks || '—'}</td>
                  <td className="px-6 py-4 text-ink-400">{task.createdAt}</td>
                  <td className="px-6 py-4">
                    <button className="text-xs text-signal-cyan hover:underline">
                      {task.status === 'failed' ? '重试' : '详情'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  )
}
