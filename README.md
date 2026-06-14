# 企业级智能知识库与多模态内容生成平台

> ⚠️ **免责声明**：本项目是在不到 2 天时间内，经过多轮快速迭代赶制出来的 Demo/POC。核心 RAG（检索增强生成）链路已经跑通，但代码、配置和文档上仍存在不少粗糙之处，不建议直接用于生产环境。后续会逐步补全测试、监控、优雅降级和生产化改造。

---

## 一、项目简介

本项目是一个基于 **Spring AI + Spring Cloud** 的企业级智能知识库与多模态内容生成平台，主要能力包括：

- **RAG 智能问答**：上传 PDF/TXT 文档，自动解析、切片、向量化并入库，支持基于检索上下文的流式问答。
- **知识库管理**：文档解析、语义切片、Embedding 生成、Milvus + Elasticsearch 双写。
- **内容生成**：图片生成任务队列（基于 Redis 异步消费）。
- **微服务治理**：基于 Spring Cloud Gateway 统一入口，Nacos 服务注册与发现。

项目从零到 RAG 跑通经历了多轮快速迭代，目前已经实现：

- ✅ 文档解析与切片
- ✅ Embedding 向量化（百炼 text-embedding-v4）
- ✅ Milvus + Elasticsearch 双写与混合检索
- ✅ 并发 RAG 检索、重排序、语义去重
- ✅ 流式调用豆包/DeepSeek 等大模型
- ✅ 前端 React 管理台（RAG 问答、知识库上传、内容生成）

已知待完善点：

- ⚠️ 文本清洗和切片策略较简单，对复杂格式文档支持有限
- ⚠️ 部分配置和错误处理仍需生产化打磨
- ⚠️ 单元测试和集成测试覆盖率不足
- ⚠️ 前端状态管理目前基于 localStorage，适合 Demo 场景

---

## 二、技术栈

### 后端

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17+ | 运行环境 |
| Spring Boot | 3.2.12 | 微服务基础框架 |
| Spring Cloud | 2023.0.3 | 微服务治理 |
| Spring Cloud Alibaba | 2023.0.1.0 | Nacos 注册/配置中心 |
| Spring AI | 1.0.1 | 向量存储、Embedding、ChatClient |
| Spring AI Alibaba | 1.0.0.4 | 阿里云模型适配 |
| Milvus SDK | 2.4.9 | 向量数据库客户端 |
| Elasticsearch Java Client | 8.18.1 | ES 向量/关键词检索 |
| Apache PDFBox | 3.0.2 | PDF 文本提取 |
| Lombok / Apache Commons | 常用版本 | 开发效率工具 |

### 前端

| 技术 | 版本 | 说明 |
|------|------|------|
| React | 18 | UI 框架 |
| TypeScript | 5.6 | 类型安全 |
| Vite | 8 | 构建工具 |
| Tailwind CSS | 3.4 | 样式框架 |
| React Router | 6 | 前端路由 |
| Axios | 1.6 | HTTP 客户端 |

### 基础设施

| 组件 | 版本 | 说明 |
|------|------|------|
| Milvus | 2.4.17 | 向量数据库 |
| Elasticsearch | 8.16.0 | 向量 + 关键词混合检索 |
| Redis | 7-alpine | 缓存、任务队列、历史对话 |
| Nacos | 2.3.0 | 服务注册与配置中心 |
| Docker & Docker Compose | - | 一键本地部署 |

### 大模型

- **Embedding**：阿里云百炼 `text-embedding-v4`（1024 维）
- **Chat**：火山方舟 Doubao / DeepSeek 等 OpenAI 兼容接口

---

## 三、项目结构

```text
.
├── admin-console/              # React 前端管理台
│   └── src/
│       ├── components/         # 公共组件
│       ├── pages/              # 页面（Dashboard / 知识库 / RAG / 内容生成）
│       └── services/           # API 封装
├── ai-orchestration-service/   # RAG 编排服务（检索、重排、流式生成）
├── content-gen-service/        # 内容生成服务（图片任务）
├── knowledge-parse-service/    # 知识解析服务（文档解析、切片、向量化入库）
├── spring-cloud-gateway/       # Spring Cloud 网关
├── docs/                       # 设计文档
├── docker-compose.yml          # 一键启动全部基础设施 + 微服务
├── .env                        # 环境变量（API Key 等敏感配置）
└── pom.xml                     # Maven 父工程
```

---

## 四、快速启动

### 方式一：Docker Compose 一键启动（推荐）

#### 1. 准备环境

- 安装 [Docker](https://docs.docker.com/get-docker/) 和 [Docker Compose](https://docs.docker.com/compose/install/)
- 确保端口 `8080、8001、8002、8003、8848、9848、6379、9200、19530、9000、9001` 未被占用

#### 2. 配置 API Key

复制或编辑项目根目录的 `.env` 文件，填入你的真实 API Key：

```env
# 火山方舟 / DeepSeek OpenAI 兼容接口
OPENAI_API_KEY=your-openai-api-key
OPENAI_BASE_URL=https://ark.cn-beijing.volces.com/api/v3
OPENAI_CHAT_MODEL=doubao-1-5-pro-32k-250115

# 阿里云百炼 Embedding
ALI_AI_API_KEY=your-bailian-api-key
ALI_AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
ALI_AI_EMBEDDINGS_PATH=/embeddings
OPENAI_EMBEDDING_MODEL=text-embedding-v4
```

#### 3. 启动全部服务

```bash
cd /path/to/spring-ai-RAG-aigc
docker-compose up -d
```

首次启动会拉取镜像并构建 Java 服务镜像，可能需要 5~15 分钟，取决于网络环境。

#### 4. 验证服务状态

```bash
# 查看容器运行状态
docker-compose ps

# 查看网关健康检查
curl http://localhost:8080/actuator/health
```

#### 5. 访问前端管理台

```text
http://localhost:8080/
```

> 前端管理台通过网关的静态资源路由或独立部署访问。如果是本地开发，也可以直接启动 `admin-console`：
>
> ```bash
> cd admin-console
> npm install
> npm run dev
> # 访问 http://localhost:3000
> ```

---

### 方式二：本地开发启动

如果你需要修改代码并本地调试，按以下步骤：

#### 1. 启动基础设施

```bash
docker-compose up -d redis nacos elasticsearch milvus-standalone etcd minio
```

#### 2. 编译后端服务

```bash
mvn clean package -DskipTests
```

#### 3. 依次启动微服务

```bash
# 知识解析服务
java -jar knowledge-parse-service/target/knowledge-parse-service-1.0.0-SNAPSHOT.jar

# RAG 编排服务
java -jar ai-orchestration-service/target/ai-orchestration-service-1.0.0-SNAPSHOT.jar

# 内容生成服务
java -jar content-gen-service/target/content-gen-service-1.0.0-SNAPSHOT.jar

# 网关
java -jar spring-cloud-gateway/target/spring-cloud-gateway-1.0.0-SNAPSHOT.jar
```

#### 4. 启动前端

```bash
cd admin-console
npm install
npm run dev
```

---

## 五、使用流程

### 1. 上传文档到知识库

进入前端 **知识库管理** 页面，拖拽或点击上传 PDF/TXT 文件。系统会：

1. 提取文本
2. 清洗与语义切片
3. 生成 Embedding
4. 写入 Milvus + Elasticsearch

### 2. RAG 问答

进入前端 **RAG 问答** 页面，输入问题，系统会：

1. 并发检索 Milvus 和 Elasticsearch
2. 去重、重排序
3. 拼接上下文和历史对话
4. 流式调用大模型生成答案

---

## 六、核心配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `MILVUS_COLLECTION_NAME` | `knowledge_chunks_v4` | Milvus 集合名 |
| `MILVUS_DIMENSION` | `1024` | 向量维度，需与 Embedding 模型输出一致 |
| `ES_INDEX_PREFIX` | `aigc_v4` | ES 索引前缀 |
| `rag.score-threshold` | `0.3`（Demo 已调低） | 向量检索相似度阈值，生产环境建议 `0.5~0.65` |
| `rag.top-k` | `5` | 最终返回的上下文文档数 |
| `vector.write.strict-mode` | `true` | 要求 Milvus + ES 双写均成功 |

> ⚠️ **注意**：写入端和查询端的 `MILVUS_COLLECTION_NAME` 与 `MILVUS_DIMENSION` 必须保持一致，否则检索会失败。

---

## 七、常见问题

### Q1: 上传文件后 Milvus 里没有数据？

- 检查 `knowledge-parse-service` 日志中是否有 `向量库写入完成` 或 `向量库写入失败`。
- 确认调用的是 `/api/document/parse-and-index` 接口。
- 使用 `check_milvus_detailed.py` 查看集合状态（注意指定 `db_name='aigc_knowledge'`）。

### Q2: RAG 检索召回为空？

- 检查 `rag.score-threshold` 是否过高，可先临时调低到 `0.3` 测试。
- 确认 Milvus collection 已加载（`load state: Loaded`）。
- 确认索引已建立（`indexed_rows > 0`）。
- 检查查询端和写入端是否连接到同一个 collection 和 database。

### Q3: 重启后数据还在吗？

只要没有执行 `docker-compose down -v` 或手动删除 Docker volume，Milvus/ES/Redis 的数据都会持久化保留。

---

## 八、后续优化方向

- [ ] 完善文本解析：支持 Word、Markdown、HTML 等更多格式
- [ ] 优化切片策略：基于标题、语义边界智能切片
- [ ] 引入重排序模型（Reranker）替代简单关键词重排
- [ ] 增加检索结果可解释性（展示引用来源）
- [ ] 完善监控与告警：接入 Prometheus + Grafana
- [ ] 补充单元测试、集成测试和契约测试
- [ ] 前端引入全局状态管理（如 Zustand/Redux Toolkit）
- [ ] 支持多租户、权限控制和文档版本管理

---

## 九、贡献与反馈

本项目目前主要用于个人学习、面试 Demo 和快速原型验证。如果你在使用过程中发现问题，欢迎提交 Issue 或 PR。

---

## 十、License

MIT License（或根据实际情况填写）
