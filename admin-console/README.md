# AIGC Platform Admin Console

基于 React 18 + TypeScript + Vite + Tailwind CSS 的 AIGC 微服务管理台，通过 **Spring Cloud Gateway (8080)** 统一调用后端服务。

## 设计方向

工业风暗色控制室（Industrial Control Room）：深色底、信号色状态指示、等宽字体展示技术指标，避免通用的 "AI 渐变紫" 审美。

## 快速开始

```bash
cd admin-console
npm install
npm run dev
```

开发服务器默认运行在 `http://localhost:3000`，并通过 Vite 代理将 `/api` 与 `/actuator` 转发到 `http://127.0.0.1:8080`。

## 调用路径

Gateway 配置了 `StripPrefix=2`，因此前端需要在网关前缀后再带一层后端自身的 `/api` 前缀：

| 后端服务 | 网关路由 | 前端调用 | 后端实际路径 |
| --- | --- | --- | --- |
| knowledge-parse-service | `/api/knowledge/**` | `/api/knowledge/api/document/parse-and-index` | `/api/document/parse-and-index` |
| ai-orchestration-service | `/api/ai/**` | `/api/ai/api/rag/chat/stream` | `/api/rag/chat/stream` |
| content-gen-service | `/api/content/**` | `/api/content/api/image/generate` | `/api/image/generate` |
| content-gen-service 查询 | `/api/content/**` | `/api/content/api/image/status/{taskId}` | `/api/image/status/{taskId}` |
| Gateway actuator | `/actuator/**` | `/actuator/gateway/routes` | — |

> 当前 `content-gen-service` 仅实现了图片生成，没有文本生成接口，因此管理台只提供“图片生成”页面。

生产环境可将构建产物 `dist/` 部署到 Nginx，并配置反向代理到网关：

```nginx
server {
    listen 80;
    root /var/www/admin-console/dist;
    location / {
        try_files $uri $uri/ /index.html;
    }
    location /api/ {
        proxy_pass http://gateway:8080/api/;
    }
    location /actuator/ {
        proxy_pass http://gateway:8080/actuator/;
    }
}
```

## 页面

- **总览**：服务健康、基础设施状态、网关路由统计
- **知识库**：拖拽上传文档，触发解析与向量入库
- **RAG 问答**：向 `ai-orchestration-service` 发起检索增强问答（流式输出）
- **图片生成**：提交图片生成任务到 Redis 异步队列

## 环境变量

```bash
VITE_API_BASE_URL=/api   # 生产环境可改为完整网关地址
```
