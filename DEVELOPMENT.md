# 开发与构建

## 日常开发：前端热更新

先启动 Spring Boot 后端（默认 `http://localhost:8080`），再在另一个终端运行：

```powershell
cd frontend
npm run dev
```

浏览器访问 `http://localhost:5173/login`。Vite 会将以下请求代理到后端：

- `/auth`、`/account`、`/users`、`/roles`、`/permissions`
- `/chat`
- `/ws/chat` STOMP WebSocket

修改 `frontend/src` 下的 Vue、JavaScript 或 CSS 文件会由 Vite 热更新，不需要重新执行构建。后端地址可通过环境变量覆盖：

```powershell
$env:VITE_BACKEND_URL = 'http://192.168.1.20:8080'
$env:VITE_BACKEND_WS_URL = 'ws://192.168.1.20:8080'
npm run dev
```

## 生产打包

直接执行 Maven 打包即可自动完成前端构建并复制到 Spring Boot 静态资源目录：

```powershell
mvnw.cmd clean package
```

`application` 模块的 `frontend` Maven profile 已默认启用，会自动执行 `npm ci`、`npm run build`，然后将 `frontend/dist` 复制到 `application/target/classes/static/frontend`。

如果只需要开发后端、明确跳过前端生产构建，可以使用：

```powershell
mvnw.cmd -P-frontend -DskipTests compile
```

## IDEA 一键启动

项目已为当前 IDEA 工作区配置 `CustomerServiceChatApplication` 的 Before Launch：点击 Spring Boot 的运行按钮时，会先调用 `tools/start-vite-dev.cmd`。

脚本会检查 `5173` 端口：

- 未启动 Vite：后台启动 `npm run dev`
- Vite 已运行：不重复启动
- Spring Boot 随后继续按原配置启动

第一次打开项目或重新导入 IDEA 后，如果 IDEA 提示外部工具配置发生变化，请选择加载项目配置。运行后访问 `http://localhost:5173/login`。

由于 `.idea/workspace.xml` 是 IDEA 的本机配置文件并被 Git 忽略，这个 Before Launch 配置只保存在当前开发机；共享给其他开发者时，可让其在 Spring Boot 配置的 `Before launch` 中添加外部工具 `Start Vite Dev Server`，目标脚本为 `tools/start-vite-dev.cmd`。


`8080` 提供的是已经打包的静态文件；`5173` 提供 Vite 开发服务器和热更新页面。两者共用同一个后端 API，因此不会改变登录、聊天或 WebSocket 业务逻辑。
