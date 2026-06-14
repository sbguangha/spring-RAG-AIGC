import { Routes, Route } from 'react-router-dom'
import Layout from '@/components/Layout'
import Dashboard from '@/pages/Dashboard'
import KnowledgePage from '@/pages/KnowledgePage'
import RagPage from '@/pages/RagPage'
import ContentPage from '@/pages/ContentPage'

function App() {
  return (
    <Routes>
      <Route path="/" element={<Layout />}>
        <Route index element={<Dashboard />} />
        <Route path="knowledge" element={<KnowledgePage />} />
        <Route path="rag" element={<RagPage />} />
        <Route path="content" element={<ContentPage />} />
      </Route>
    </Routes>
  )
}

export default App
