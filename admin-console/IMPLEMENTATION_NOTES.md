# AIGC 管理台实现说明

> 本文档用于向技术负责人/资深工程师同步 `admin-console/` 目录下的工作范围、关键决策与验证结果。

---

## 1. 背景与目标

项目已有 Spring Cloud 微服务集群：

- `spring-cloud-gateway` 监听 **8080**，统一路由 / 限流 / CORS
- `knowledge-parse-service`：文档解析、切片、向量入库
- `ai-orchestration-service`：RAG 检索 + 大模型流式问答
- `content-gen-service`：图片生成任务队列

目标是为该集群增加一个**可视化 React 管理台**，通过 Gateway 8080 统一调用后端，并提供工业风暗色 UI（非通用 AI 渐变审美）。

---

## 2. 架构决策

采用 **独立 SPA + Nginx 反代 Gateway** 模式：

```
浏览器 ──▶ Nginx (80/443) ──▶ spring-cloud-gateway:8080 ──▶ 下游微服务
                │
                └── 静态资源 (admin-console/dist)
```

理由：

1. 前后端职责清晰，Gateway 继续承担鉴权、限流、路由。
2. 复用现有 `/api/knowledge/**`、`/api/ai/**`、`/api/content/**` 三条路由。
3. Gateway 已开启 `globalcors`，本地开发跨域无障碍。
4. 后续若后台需要大量跨服务聚合，可再叠加 **Admin BFF**；目前直接走 Gateway。

---

## 3. 技术栈

- React 18 + TypeScript
- Vite 8
- React Router v6
- Tailwind CSS 3
- Axios（非流式接口）
- lucide-react（图标）

---

## 4. 目录结构

```text
admin-console/
├── index.html
├── package.json
├── vite.config.ts          # 开发代理到 127.0.0.1:8080
├── tailwind.config.js      # 自定义 ink / signal 色板 + 字体
├── postcss.config.js
├── tsconfig.json / tsconfig.node.json
├── .eslintrc.cjs
├── .env.example
├── README.md
├── public/favicon.svg
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── index.css             # 工业暗色主题 + 面板/按钮/状态点组件
    ├── components/
    │   ├── Layout.tsx        # 固定侧边栏 + 导航
    │   ├── ServiceCard.tsx   # 服务健康卡片
    │   └── StatCard.tsx      # 指标卡片
    ├── pages/
    │   ├── Dashboard.tsx     # 总览 / 基础设施 / 网关日志
    │   ├── KnowledgePage.tsx # 拖拽上传 / 解析任务列表
    │   ├── RagPage.tsx       # 流式 RAG 问答
    │   └── ContentPage.tsx   # 图片生成任务提交与状态查询
    ├── services/
    │   └── api.ts            # axios 实例 + 各服务 API
    └── types/
        └── index.ts
```

---

## 5. 关键修复（从静态审计到可运行）

初始脚手架存在多处**前端调用路径与后端 Controller 不匹配**，已逐一修复。

### 5.1 API 路径修正

Gateway 配置了 `StripPrefix=2`，前端需在网关前缀后再带一层后端自身的 `/api`：

| 功能 | 前端调用 | 后端实际路径 |
|---|---|---|
| 文档解析并入库 | `POST /api/knowledge/api/document/parse-and-index` | `/api/document/parse-and-index` |
| RAG 流式问答 | `POST /api/ai/api/rag/chat/stream` | `/api/rag/chat/stream` |
| 提交图片任务 | `POST /api/content/api/image/generate` | `/api/image/generate` |
| 查询图片任务 | `GET /api/content/api/image/status/{taskId}` | `/api/image/status/{taskId}` |

### 5.2 RAG 流式响应

后端 `RagController` 返回 `Flux<String>` + `TEXT_PLAIN`，不是 JSON。前端改用原生 `fetch` + `ReadableStream` 逐字读取并渲染。

### 5.3 请求字段修正

- RAG 请求体：从 `{ question }` 改为 `{ query }`（与 `RagRequest.query` 对齐）。
- 文档上传：追加 `withIndex=true`，上传后直接写入 Milvus/ES。

### 5.4 移除未实现功能

`content-gen-service` 当前只有图片生成 Controller，没有文本生成接口。管理台已移除“文本生成”Tab，只保留图片生成。

### 5.5 构建工具链

- 新增 `.eslintrc.cjs`，`npm run lint` 通过。
- 修复 `vite.config.ts` 中 ESM 直接使用 `__dirname` 的隐患。
- 移除未使用的 `clsx` / `tailwind-merge`。
- 升级 Vite 至 v8，`@vitejs/plugin-react` 至 v6，`@typescript-eslint/*` 至 v7。
- 修复模板字符串被误写为字面量转义导致的 TypeScript 编译失败。

---

## 6. 验证结果

```bash
cd admin-console
npm install          # 成功，npm audit 0 漏洞
npm run dev          # Vite v8.0.16 ready on localhost:3000
npm run build        # ✓ built in 938ms
npm run lint         # 通过
npm audit            # found 0 vulnerabilities
```

---

## 7. 已知局限与后续建议

| 项 | 说明 | 优先级 |
|---|---|---|
| 移动端适配 | 侧边栏固定 `w-64`，小屏会重叠 | 中 |
| 可访问性 | 部分表单可补充显式 `label` 与 `aria-label` | 中 |
| Gateway routes actuator | `/actuator/gateway/routes` 是否能返回取决于 Gateway actuator 配置，前端已有错误兜底 | 低 |
| 服务真实健康检查 | 当前为静态展示，后续可轮询各服务 `/actuator/health` | 中 |
| 任务列表实时刷新 | 当前为本地状态，可对接 `/api/content/api/image/status/{taskId}` 轮询 | 中 |

---

## 8. 如何本地运行

```bash
cd admin-console
npm install
npm run dev
```

浏览器打开 `http://localhost:3000`。

确保后端已启动：Nacos、Redis、Elasticsearch、Milvus、Gateway、三个业务服务。

---

## 9. Git 提交

本次工作已提交：

```text
commit bfb6dec
Author: geyongpan
Date:   2026-06-14

    feat(admin-console): 修复管理台 API 路径并完善构建工具链
```

共新增 26 个文件，包含完整的管理台前、后端调用对接与构建配置。
