# World One Python 方案设计文档

> 版本：0.1 (draft)
> 状态：设计讨论
> 基于：`aipp-protocol.md`、`widget-protocol.md`、`interaction-model.md`、`memory-design.md`、`aipp-prompt-architecture.md`、`aipp-skills-progressive-disclosure.md`、`widget-state-contract.md`

---

## 零、核心原则（不变）

Python 重写只替换**后端实现语言**，不改变任何协议定义：

- AIPP 协议（三份契约：Tools / Skills / Widgets）原封不动
- Widget 协议（canvas 指令 / open/patch/replace/close）原封不动
- 交互模型（6 种模式 / Session 池 / Task Panel / sys.*）原封不动
- Prompt 架构（六层提示词 / AAP-Pre/AAP-Post）原封不动
- Memory 系统（五大类型 / 三级 scope / consolidation）原封不动
- 前端 index.html 零改动——API 路径 `/api/*` 保持一致

唯一变的是：Java → Python，Spring Boot → FastAPI。

---

## 一、技术选型

| 关注点 | Java 当前方案 | Python 方案 | 选择理由 |
|--------|-------------|------------|---------|
| **Web 框架** | Spring Boot + Jetty | **FastAPI** | 原生 SSE（StreamingResponse）、async/await、Pydantic 校验、OpenAPI 自动生成 |
| **LLM 集成** | 手写 HTTP SSE 解析 | **litellm** | 统一 DeepSeek/OpenAI/Ollama 所有 provider，自带流式输出、超时、fallback |
| **MCP 客户端** | 无（future work） | **mcp 官方 SDK** | 只做 client 连接层，不绑架 Agent Loop |
| **数据校验** | AippAppSpec (Java) | **Pydantic** | 等效 AippAppSpec/AippWidgetSpec 验证，声明式 schema + JSON 序列化 |
| **配置管理** | application.yml + LLMConfigProperties | **pydantic-settings** | 支持 env var + YAML + 运行时覆盖，对应 Java 的三层优先级 |
| **会话持久化** | 内存 Map + PostgreSQL | **SQLite (aiosqlite)** | 单文件部署友好，无需外部 DB；Phase 2 可切 PostgreSQL |
| **前端** | index.html (Vanilla JS) | **同文件，零改动** | FastAPI StaticFiles 挂载 |
| **Memory 存储** | JdbcMemoryStore (PostgreSQL) | **SQLite → PostgreSQL** | Phase 1 用 SQLite 验证；Phase 2 切 PostgreSQL + pgvector |
| **异步任务** | CompletableFuture | **asyncio + BackgroundTasks** | FastAPI 内置 BackgroundTasks，consolidation 不阻塞响应 |

### 不选用

| 技术 | 不选原因 |
|------|---------|
| LangChain / LangGraph | Agent Loop 自写更可控，AIPP 协议太自定义 |
| CrewAI / AutoGen | 单 Agent 架构，多 Agent 框架完全多余 |
| Flask | 无原生 SSE，需手动实现，不如 FastAPI |
| Django | 太重，AIPP Host 是微服务不是全栈 Web |

---

## 二、项目结构

```
world-one-py/
├── app/
│   ├── main.py                  # FastAPI app: mount static + routes + lifespan
│   ├── config.py                # pydantic-settings: LLM, DB, port 等
│   ├── models/
│   │   ├── aipp.py              # AIPP 协议模型 (AippAppSpec, AippSkill, AippWidget, ToolDef)
│   │   ├── session.py           # Session data model (SessionData, SessionType)
│   │   ├── memory.py            # Memory record model (Memory, MemoryType, MemoryScope...)
│   │   ├── canvas.py            # Canvas command model (CanvasAction, WidgetCommand)
│   │   └── sse_event.py         # SSE event model (SSEEvent, SSEEventType)
│   ├── core/
│   │   ├── agent_loop.py        # GenericAgentLoop: LLM 调用 + tool 执行 + SSE 流
│   │   ├── context_composer.py  # 六层提示词装配 (Layer 0-6)
│   │   ├── skill_router.py      # Loop A 召回: 触发词 → embedding → LLM 消歧
│   │   ├── skill_executor.py    # Loop B 执行: playbook 加载 + tools 收窄
│   │   └── event_extractor.py   # 从 tool response 提取 canvas/session/html_widget 事件
│   ├── registry/
│   │   ├── app_registry.py      # AppRegistry: 扫描 ~/.ones/apps/ + 健康检查 + 拉取
│   │   ├── skill_catalog.py     # AippSkillCatalog: 四层索引 (universal/app/widget/view)
│   │   └── widget_registry.py   # WidgetRegistry: widget_type → manifest 映射
│   ├── memory/
│   │   ├── memory_store.py      # MemoryStore 接口 + SQLite 实现
│   │   ├── memory_loader.py     # MemoryLoader: 加载规则 (snapshot + 动态补充)
│   │   ├── memory_consolidator.py # LLMMemoryConsolidator: 异步 consolidation
│   │   ├── memory_agent_prompt.py # Memory Agent 提示词分层 (L0/L1a/L1b/L2)
│   │   └── memory_tools.py      # memory_* 工具实现 (7 个方法)
│   ├── session/
│   │   ├── session_store.py     # SessionStore: 内存 + SQLite 持久化
│   │   ├── message_store.py     # MessageHistoryStore: 双键 (agent_id, ui_id) 隔离
│   │   └── session_manager.py   # Session 生命周期: 创建/归一/关闭/归档
│   ├── api/
│   │   ├── chat.py              # POST /api/chat → SSE 流式响应
│   │   ├── sessions.py          # GET/DELETE /api/sessions, GET /api/sessions/{id}/messages
│   │   ├── registry_api.py      # /api/registry/* 端点
│   │   ├── proxy.py             # /api/proxy/app/{appId}/* 代理路由
│   │   ├── proxy_tools.py       # /api/proxy/tools/{name} ToolProxy
│   │   ├── apps_api.py          # /api/apps/{appId}/open
│   │   ├── settings_api.py      # GET/PUT /api/settings
│   │   ├── health.py            # GET /api/health
│   │   └── system_widgets.py    # /api/system/widgets/app-list 等
│   ├── widgets/
│   │   ├── sys_widgets.py       # sys.* 内置 Widget 处理 (confirm/alert/prompt/selection)
│   │   └── html_widget.py       # html_widget 内嵌卡片渲染
│   └── utils/
│       ├── sanitize.py          # sanitizeHistory() 兜底安全网
│       └── mcp_client.py        # MCP 客户端连接管理
├── static/
│   ├── index.html               # 直接复用 Java 版，零改动
│   ├── marked.min.js
│   └── (其他前端静态文件)
├── tests/
│   ├── test_aipp_compliance.py  # AippAppSpec/AippWidgetSpec 合规验证
│   ├── test_agent_loop.py       # Agent Loop 单元测试
│   ├── test_context_composer.py # 六层提示词装配测试
│   ├── test_session_isolation.py # 双键历史隔离测试
│   ├── test_memory.py           # Memory 加载/consolidation 测试
│   └── test_registry.py         # AppRegistry 发现/路由测试
├── pyproject.toml
├── requirements.txt
└── Dockerfile
```

---

## 三、核心模块设计

### 3.1 GenericAgentLoop（核心循环）

```python
# app/core/agent_loop.py

class GenericAgentLoop:
    def __init__(self, config, registry, context_composer, session_store, message_store):
        self.config = config
        self.registry = registry
        self.context_composer = context_composer
        self.session_store = session_store
        self.message_store = message_store

    async def run_turn(self, session_id: str, user_message: str, 
                       widget_view: dict | None = None) -> AsyncGenerator[SSEEvent, None]:
        """一次完整的用户对话轮次，yield SSE 事件流"""
        session = self.session_store.get(session_id)
        history = self.message_store.load_history(session.agent_id, session.ui_id)

        # 1. 装配上下文
        system_prompt, tool_defs, loaded_ids = await self.context_composer.compose(
            session, history, widget_view
        )

        # 2. 构建消息列表
        messages = self._build_messages(system_prompt, history, user_message)

        # 3. LLM 循环
        max_rounds = 10
        for round_idx in range(max_rounds):
            # 3a. 调用 LLM
            response = await litellm.acompletion(
                model=self.config.llm_model,
                messages=messages,
                tools=tool_defs,
                stream=True,
                api_key=self.config.llm_api_key,
                api_base=self.config.llm_base_url,
                timeout=self.config.llm_timeout,
            )

            # 3b. 流式处理
            stream_state = StreamState()
            for chunk in response:
                event = self._process_chunk(chunk, stream_state)
                if event:
                    yield event

            # 3c. 检查是否有 tool_call
            if stream_state.has_tool_calls:
                for tool_call in stream_state.tool_calls:
                    # 3d. 执行工具
                    tool_result = await self._execute_tool(session, tool_call)
                    # 3e. 提取 SSE 事件 (canvas/session/html_widget)
                    for sse_event in self.event_extractor.extract(tool_result, tool_call):
                        yield sse_event
                    # 3f. 把工具结果追加到 messages，继续循环
                    messages.append(tool_result.to_message())
                continue  # 继续下一次 LLM 调用
            else:
                break  # 无 tool_call，轮次结束

        # 4. 异步 consolidation
        asyncio.create_task(self._consolidate_async(session, loaded_ids))
```

**与 Java 版的关键差异：**
- Java 用 `CompletableFuture` + `while(loop)`, Python 用 `async for` + `yield`
- Java 手写 HTTP SSE 解析，Python 用 `litellm.acompletion(stream=True)` 直接获得流式 chunk
- Java 工具路由通过 `AppRegistry.proxyCall()`, Python 通过 `registry.call_tool()` — 底层都是 HTTP，但 Python 版用 `httpx.AsyncClient`

### 3.2 ContextComposer（六层提示词装配）

```python
# app/core/context_composer.py

class ContextComposer:
    async def compose(self, session, history, widget_view) -> ComposeResult:
        """返回 (system_prompt_str, tool_defs_list, loaded_ids_set)"""

        layers = []

        # Layer 0: Host 铁律 + AAP-Pre 聚合
        host_prompt = self._build_host_prompt()
        aap_pre = self._build_aap_pre(session)
        # 命中后替换: AAP-Post 替代 AAP-Pre
        aap_post = self._build_aap_post(session)
        base_layer = aap_post if aap_post else f"{host_prompt}\n---\n{aap_pre}"
        # canvas 激活时追加 Widget Manual + View Prompt
        if widget_view:
            widget_prompt = self._build_widget_prompt(widget_view)
            base_layer += f"\n---\n{widget_prompt}"
        layers.append(base_layer)

        # Layer 1: Memory 注入
        memory_result = await self.memory_loader.load(session)
        layers.append(memory_result.injection_text)
        loaded_ids = memory_result.loaded_ids

        # Layer 2: Session Entry Prompt (仅 task/event/app)
        if session.type in ('task', 'event', 'app'):
            layers.append(session.entry_prompt)

        # Layer 3: Widget llm_hint + Workspace (仅 canvas 激活)
        if widget_view:
            widget_hint = self.registry.widget_context_prompt(widget_view.widget_type)
            layers.append(widget_hint)

        # Layer 4: Skill Playbook (条件层，Router 选中时)
        if session.current_skill:
            layers.append(session.current_skill.playbook)

        # Layer 5: UI Hints (条件层，前端传 widget_view 时)
        if widget_view:
            ui_hints = self._build_ui_hints(widget_view)
            layers.insert(0, ui_hints)  # 最高优先级，前置

        # 拼接
        system_prompt = "\n\n---\n\n".join(layers)

        # Tool list 裁剪
        tool_defs = self._build_tool_defs(session, widget_view)

        return ComposeResult(system_prompt, tool_defs, loaded_ids)
```

**严格对齐 Java 版**：Layer 编号、拼接顺序、AAP-Post 替换 AAP-Pre 的逻辑、Widget/View scope 裁剪规则全部保持一致。

### 3.3 AppRegistry（应用发现与路由）

```python
# app/registry/app_registry.py

class AppRegistry:
    def __init__(self, config):
        self.config = config
        self.apps: dict[str, AppEntry] = {}
        self._http_client = httpx.AsyncClient()

    async def discover(self):
        """扫描 ~/.ones/apps/ 目录，拉取每个 app 的 manifest/tools/skills/widgets"""
        apps_dir = Path.home() / ".ones" / "apps"
        if not apps_dir.exists():
            return

        for app_dir in apps_dir.iterdir():
            if not app_dir.is_dir():
                continue
            manifest_path = app_dir / "manifest.json"
            if not manifest_path.exists():
                continue
            manifest = json.loads(manifest_path.read_text())
            base_url = manifest["api"]["base_url"]

            # 健康检查
            try:
                resp = await self._http_client.get(f"{base_url}/api/health", timeout=5)
                if resp.status_code != 200:
                    continue
            except:
                continue

            # 拉取三份契约
            tools_resp   = await self._http_client.get(f"{base_url}/api/tools")
            skills_resp  = await self._http_client.get(f"{base_url}/api/skills")
            widgets_resp = await self._http_client.get(f"{base_url}/api/widgets")

            entry = AppEntry(
                id=manifest["id"],
                name=manifest["name"],
                base_url=base_url,
                manifest=manifest,
                tools=tools_resp.json(),
                skills=skills_resp.json(),
                widgets=widgets_resp.json(),
            )
            self.apps[entry.id] = entry

    async def call_tool(self, app_id: str, tool_name: str, args: dict, context: dict) -> dict:
        """路由工具调用到对应 app 的 POST /api/tools/{name}"""
        entry = self.apps.get(app_id)
        if not entry:
            raise ToolNotFound(f"App {app_id} not registered")

        url = f"{entry.base_url}/api/tools/{tool_name}"
        resp = await self._http_client.post(url, json={"args": args, "_context": context})
        return resp.json()
```

**与 Java 版等效**：扫描目录、健康检查、三契约拉取、HTTP 路由调用。新增 `httpx.AsyncClient` 连接池复用（Java 版用 RestTemplate）。

### 3.4 Memory 系统

```python
# app/memory/memory_store.py (SQLite 实现)

class SqliteMemoryStore(MemoryStore):
    async def save(self, memory: Memory) -> str:
        """INSERT，返回 memory.id"""

    async def find_active(self, agent_id, user_id, scope, session_id=None,
                          workspace_id=None, memory_type=None) -> list[Memory]:
        """按 scope/type/importance 过滤，等效 JdbcMemoryStore.findActive"""

    async def supersede(self, old_id: str, new_memory: Memory) -> str:
        """更新 old.superseded_by，INSERT new"""

    async def promote(self, id: str, new_scope: MemoryScope, reason: str):
        """scope 升级"""

    async def load_snapshot(self, agent_id: str, user_id: str) -> str | None:
        """加载 Global Snapshot"""
```

**SQLite 表结构**：与 Java 版 PostgreSQL 表完全对齐（字段名、类型、索引一一对应），仅将 PostgreSQL 专有语法（`gen_random_uuid()`、`GIN`、`TIMESTAMPTZ`）替换为 SQLite 等效（`uuid()`、`FTS5`、`TEXT`）。

Phase 2 切 PostgreSQL 时，`MemoryStore` 接口不变，仅替换实现类。

### 3.5 Session 管理

```python
# app/session/session_store.py

class SessionStore:
    def __init__(self, db):
        self.db = db
        self._sessions: dict[str, SessionData] = {}  # 内存缓存

    def get(self, session_id: str) -> SessionData | None:
        return self._sessions.get(session_id)

    async def create(self, session_id: str, type: str, name: str, 
                     widget_type: str | None = None) -> SessionData:
        session = SessionData(id=session_id, type=type, name=name, ...)
        self._sessions[session_id] = session
        await self.db.persist_session(session)
        return session

    async def archive(self, session_id: str):
        """标记 session 为 completed/voided"""
```

**Session 类型对照**（与 Java 版一致）：

| SessionType | 创建方式 | 面板展示 |
|-------------|---------|---------|
| `conversation` | 用户新建 | Chat Panel |
| `app` | 进入 app main widget | 不进 Task Panel |
| `task` | LLM/用户发起 | Task Panel |
| `event` | 外部 POST /api/events | Task Panel |

---

## 四、API 端点（与 Java 版一一对应）

所有 `/api/*` 路径保持一致，前端零改动。

| 端点 | 方法 | 实现 | 说明 |
|------|------|------|------|
| `/api/chat` | POST | `chat.py` | SSE 流式响应，核心入口 |
| `/api/sessions` | GET | `sessions.py` | 返回 session 列表 |
| `/api/sessions/{id}` | GET | `sessions.py` | 单个 session 详情 |
| `/api/sessions/{id}/messages` | GET | `sessions.py` | 对话历史 |
| `/api/sessions/{id}` | DELETE | `sessions.py` | 删除 session |
| `/api/registry` | GET | `registry_api.py` | 已安装 app 列表 |
| `/api/registry/install` | POST | `registry_api.py` | 动态注册 app |
| `/api/registry/apps/{id}` | DELETE | `registry_api.py` | 卸载 app |
| `/api/proxy/app/{appId}/*` | ALL | `proxy.py` | App 代理 |
| `/api/proxy/tools/{name}` | POST | `proxy_tools.py` | ToolProxy (Widget 直调) |
| `/api/apps/{appId}/open` | POST | `apps_api.py` | App 直开 |
| `/api/settings` | GET/PUT | `settings_api.py` | LLM 配置 |
| `/api/health` | GET | `health.py` | 健康检查 |
| `/api/system/widgets/app-list` | GET | `system_widgets.py` | sys.app-list 静态资源 |

### SSE 事件格式（与 Java 版一致）

```python
# app/models/sse_event.py

class SSEEventType(str, Enum):
    TEXT_TOKEN = "text_token"
    TEXT = "text"
    TOOL_CALL = "tool_call"
    CANVAS = "canvas"
    HTML_WIDGET = "html_widget"
    SESSION = "session"
    THINKING = "thinking"
    ANNOTATION = "annotation"
    ERROR = "error"
    DONE = "done"

class SSEEvent(BaseModel):
    type: SSEEventType
    content: str | dict | None = None
```

**SSE 推送格式**：

```python
# FastAPI SSE 推送
async def stream_sse(events: AsyncGenerator[SSEEvent, None]):
    for event in events:
        data = json.dumps({"type": event.type, "content": event.content})
        yield f"data: {data}\n\n"

@router.post("/api/chat")
async def chat(request: ChatRequest):
    return StreamingResponse(
        stream_sse(agent_loop.run_turn(request.session_id, request.message, request.widget_view)),
        media_type="text/event-stream",
    )
```

---

## 五、MCP 集成

```python
# app/utils/mcp_client.py

class MCPClientManager:
    """管理多个 MCP Server 连接，与 AIPP skill 双源统一暴露给 LLM"""
    
    def __init__(self):
        self.clients: dict[str, MCPServerConnection] = {}

    async def connect(self, server_id: str, config: dict):
        """连接 MCP server (stdio/SSE transport)"""
        client = MCPClient(config)
        await client.connect()
        tools = await client.list_tools()
        self.clients[server_id] = MCPServerConnection(client, tools)

    async def call_tool(self, tool_name: str, args: dict) -> dict:
        """在所有 MCP server 中查找并调用工具"""
        for conn in self.clients.values():
            if tool_name in conn.tools_map:
                return await conn.client.call_tool(tool_name, args)
        raise ToolNotFound(tool_name)

    def get_tool_defs(self) -> list[dict]:
        """返回所有 MCP 工具的 OpenAI function-calling schema"""
        defs = []
        for conn in self.clients.values():
            for tool in conn.tools:
                defs.append({
                    "type": "function",
                    "function": {
                        "name": tool.name,
                        "description": tool.description,
                        "parameters": tool.inputSchema,
                    }
                })
        return defs
```

**统一工具源**：AIPP skill + MCP tool 合并成一个 `tools` list 传给 `litellm.acompletion()`，LLM 无感区分来源。路由时先查 AIPP Registry，再查 MCP Client。

---

## 六、部署

### 开发启动

```bash
pip install -r requirements.txt
python app/main.py
# → http://localhost:8090
# → 自动扫描 ~/.ones/apps/ 发现已安装 AIPP 应用
```

### Docker 部署

```dockerfile
FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app/ app/
COPY static/ static/
EXPOSE 8090
CMD ["python", "app/main.py"]
```

### 与 Java 版共存

Python 版与 Java 版使用相同前端、相同 API 端口、相同 `~/.ones/apps/` 目录。可独立运行或与 Java 版交替运行，切换只需改端口或停掉一方。

---

## 七、Phase 规划

### Phase 1 — 核心薄壳（对应 Java Phase 2 已完成的部分）

- [ ] FastAPI app + SSE 流式 `/api/chat`
- [ ] GenericAgentLoop: LLM 调用 + tool 路由 + SSE 事件提取
- [ ] AppRegistry: 扫描 `~/.ones/apps/` + HTTP 拉取 + 健清检查
- [ ] SessionStore + MessageHistoryStore (SQLite, 双键隔离)
- [ ] 6 层 ContextComposer (最小版: Layer 0/1/6)
- [ ] 前端零改动验证: index.html 直接复用

**验收标准**：浏览器打开 `http://localhost:8090`，对话能跑通，SSE 流式输出正常。

### Phase 2 — Memory 系统

- [ ] SqliteMemoryStore (全字段)
- [ ] MemoryLoader (加载规则: snapshot + 动态补充)
- [ ] LLMMemoryConsolidator (异步 consolidation)
- [ ] Memory Agent 提示词分层 (L0/L1a/L1b/L2)
- [ ] memory_* 工具 7 个方法
- [ ] Memory 管理 Widget (memory_view + memory-manager)

**验收标准**：对话后 memory 自动写入，刷新后记忆恢复。

### Phase 3 — Skill Progressive Disclosure

- [ ] AippSkillCatalog 四层索引
- [ ] SkillRouter (Loop A): 触发词 + embedding + LLM 消歧
- [ ] SkillExecutor (Loop B): playbook 加载 + tools 收窄
- [ ] Chain 机制: 执行完 chain 回 Loop A

**验收标准**：用户说"帮我登记新员工" → 召回 onboarding skill → playbook 执行。

### Phase 4 — MCP 集成

- [ ] MCPClientManager: 连接管理 + 工具发现
- [ ] AIPP skill + MCP tool 合并工具列表
- [ ] ToolProxy 路由: AIPP → MCP fallback

**验收标准**：安装一个 MCP server 后，LLM 能发现并调用其工具。

### Phase 5 — PostgreSQL 切换 + pgvector

- [ ] MemoryStore 切 PostgreSQL 实现
- [ ] pgvector 语义检索替代全文检索
- [ ] SkillRouter embedding 切 pgvector

**验收标准**：memory 语义检索命中率 ≥ 全文检索。

---

## 八、与 Java 版的对照表

| Java 模块 | Python 对应 | 说明 |
|-----------|------------|------|
| `WorldOneServer.java` | `app/main.py` | FastAPI lifespan 替代 Jetty embed |
| `GenericAgentLoop.java` | `app/core/agent_loop.py` | async generator 替代 while-loop |
| `DefaultContextComposer.java` | `app/core/context_composer.py` | 六层拼接逻辑一致 |
| `AppRegistry.java` | `app/registry/app_registry.py` | httpx 替代 RestTemplate |
| `AippSkillCatalog.java` | `app/registry/skill_catalog.py` | 四层索引一致 |
| `JdbcMemoryStore.java` | `app/memory/memory_store.py` | SQLite 替代 PostgreSQL (Phase 1) |
| `DefaultMemoryLoader.java` | `app/memory/memory_loader.py` | 加载规则一致 |
| `LLMMemoryConsolidator.java` | `app/memory/memory_consolidator.py` | asyncio.create_task 替代 CompletableFuture |
| `WorldOneChatController.java` | `app/api/chat.py` | StreamingResponse 替代 SseEmitter |
| `WorldOneSessionStore.java` | `app/session/session_store.py` | 内存缓存 + SQLite 持久化 |
| `MessageHistoryStore.java` | `app/session/message_store.py` | 双键隔离一致 |
| `AippAppSpec.java` | `app/models/aipp.py` (Pydantic) | 声明式 schema 验证 |
| `LLMConfigProperties.java` | `app/config.py` (pydantic-settings) | env + YAML + 运行时覆盖 |

---

## 九、关键设计决策

| # | 决策 | 结论 | 理由 |
|---|------|------|------|
| D1 | Agent Loop 自写 vs 框架 | **自写** | AIPP 协议太自定义，框架绑架成本高 |
| D2 | MCP 集成方式 | **只取 mcp SDK client 层** | 不需要全框架，只需 transport + list_tools/call_tool |
| D3 | Phase 1 DB | **SQLite** | 单文件部署，零配置；Phase 5 切 PostgreSQL |
| D4 | 前端改动 | **零改动** | API 端口对齐，直接复用 index.html |
| D5 | SSE 实现 | **FastAPI StreamingResponse** | 原生支持，无需第三方库 |
| D6 | 工具路由 | **AIPP Registry 优先，MCP fallback** | 保持 AIPP 协议为第一路径 |

---

## 十、风险与缓解

| 风险 | 缓解 |
|------|------|
| Python 版性能低于 Java 版 | litellm + httpx 异步并发；实测 SSE 流延迟 ≤ Java 版 |
| SQLite 并发写入瓶颈 | Phase 1 单用户场景足够；Phase 5 切 PostgreSQL |
| 前端与后端 API 不兼容 | 严格对齐 `/api/*` 路径；用 AIPP 合规测试验证 |
| MCP SDK 版本不稳定 | 只用 `list_tools` + `call_tool` 两个稳定 API；不依赖高级功能 |
| Memory consolidation 延迟 | asyncio.create_task 不阻塞响应；与 Java 版 CompletableFuture 等效 |

---

## 十一、开发体验对比

| 操作 | Java 版 | Python 版 |
|------|---------|----------|
| 启动 | `mvn package && java -jar worldone.jar` | `python app/main.py` |
| 热重载 | 无（需重启） | `uvicorn --reload app/main.py` |
| 依赖管理 | Maven pom.xml (50+ 行) | requirements.txt (15 行) |
| 构建 | `mvn clean package` (30s+) | 无构建步骤 |
| Docker 镜像 | ~200MB (JVM) | ~50MB (Python slim) |
| 测试 | JUnit + SpringBootTest | pytest + httpx.AsyncClient |
| IDE | IntelliJ | VSCode / 任意编辑器 |