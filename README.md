# EVOPS 基础工作区

这是 springboot的骨架项目。

技术栈：Java 8、Spring Boot 2.7、MyBatis-Plus、H2 本地文件数据库、Shiro、Thymeleaf。

启动前准备：

1. 执行 `mvn -q -DskipTests compile` 验证骨架构建。
2. 执行 `mvn spring-boot:run` 启动应用；Spring Boot 会自动执行 `src/main/resources/schema.sql`。
3. H2 数据文件默认写入工作区 `data/evops`。如需修改路径，编辑 `application.yml` 中的 `jdbc:h2:file:` 连接串。

> 交付副本约定路径为根目录 `..\..\docs\schema\evops.sql`（即 `/docs/schema/evops.sql`）。
> 当前环境根目录不可写，副本放在工作区内 `docs/schema/evops.sql`，与 `src/main/resources/schema.sql` 保持一致。

## 考试机构考场与准考证建档工作区（exam-1）

覆盖四步：**报名 → 编排 → 发证 → 查档**。所有接口在 Shiro 保护下，使用 HTTP Basic 认证：
`curl -u bootstrap:bootstrap ...`；经办人通过请求头 `X-Operator` 记录（缺省 `system`）。

约定：
- 所有响应统一为 `{success, code, message, data}`；失败返回可读状态码与中文原因（含 401/404），不返回空白。
- **批量动作分两步**：先调 `*/preview` 看影响范围（可处理/跳过逐条列出），确认后再调执行接口。
- **阶段结果**（`t_stage_result`）与**最终结论**（`t_final_conclusion`）分表保存。
- **版本可追溯**：快照只追加；版本选择只切当前标记；回退按快照还原并生成新的 ROLLBACK 版本，原版本与事件链（CREATE/SELECT/ROLLBACK）都保留。

### 1. 建档：考点 / 考场 / 批次

- `POST /api/sites`、`GET /api/sites`：考点（编码、名称、位置、联系人）。
- `POST /api/rooms`、`GET /api/rooms?siteId=`：考场（考场号、楼栋、楼层、**容量**、位置描述）。
- `POST /api/batches`、`GET /api/batches`：考试批次（归属考点、科目、考试时间）。

### 2. 报名（名册挂进批次，支持勘误留痕）

- `POST /api/registrations/import/preview` → `POST /api/registrations/import`：批量导入，报名号撞号**整批拒收**。
- `POST /api/registrations/{id}/corrections`：勘误。旧值写入 `t_registration_correction`，不抹历史；主表记新值并累加更正次数。
- `GET /api/registrations/{id}/corrections`：查更正痕迹。
- `GET /api/registrations/search/by-name?name=`：按姓名查名册（包含匹配，重名全返回）。
- `GET /api/registrations?batchId=`：批次名册。

### 3. 编排（按批次生成准考证，一人一条）

- `POST /api/arrange/preview` → `POST /api/arrange`：
  按考场容量顺序排座，证号规则 `ZK + 批次编码 + 4 位流水`。
  证号唯一、批次内（考场+座位）唯一，**撞号直接拒收**；容量不足整批拒收、不落半条；已编排批次不允许重复编排。

### 4. 发证（打证、发放两步，各留经办与钟点）

- 打证：`POST /api/tickets/print/preview` → `POST /api/tickets/print`（PENDING→PRINTED，记 printed_by/printed_at）。
- 发放：`POST /api/tickets/issue/preview` → `POST /api/tickets/issue`（PRINTED→ISSUED，记 issued_by/issued_at）。
- 目标可用 `{"batchId":...}` 整批，或 `{"ticketNos":[...]}` 指定；状态不能跳步，重复操作逐条跳过并给原因。
- 证书（证面）打印不在本版范围，下版再做。

### 5. 查档（证号、考场、座位一人一条）

- `GET /api/query/by-ticket-no?ticketNo=`：按证号精确查。
- `GET /api/query/by-name?name=`：按姓名查（含重名）。
- `GET /api/query/by-batch?batchId=`：按批次列全部。
- 注意：查询参数含中文时需做 URL 编码（`curl -G --data-urlencode "name=张三"`）。

### 6. 阶段结果 / 最终结论

- `GET  /api/batches/{id}/progress/stages`：各阶段结果。
- `POST /api/batches/{id}/progress/conclusion`、`GET .../conclusion`：生成 / 查看最终结论（每批次一条）。
- `GET  /api/batches/{id}/progress/overview`：阶段结果与最终结论作为两个字段分开返回。

### 7. 工作区版本（版本选择 / 状态回退可追溯）

- `POST /api/workspace/versions?label=&remark=`：创建快照（自动成为当前版本）。
- `GET  /api/workspace/versions`、`GET /api/workspace/versions/current`、`GET /api/workspace/versions/{no}`。
- `POST /api/workspace/versions/{no}/select`：版本选择（只切当前标记，业务数据不变）。
- `POST /api/workspace/versions/{no}/rollback?remark=`：按该版本快照还原，生成新的 ROLLBACK 版本，原版本保留。
- `GET  /api/workspace/versions/events?versionNo=`：CREATE/SELECT/ROLLBACK 事件链。
