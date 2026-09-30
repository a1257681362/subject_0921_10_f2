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

## 出分落级与复核窗口工作区（grade-1）

把"问等级、要复核"这条线收进 evops-base 统一承接：两个岗共用一套规则与窗口口径。
卷面分按分数段落等级、**规则带版本**；**窗口起止跟批次配置走，不写死**；
窗口期内学员可提复核，**超窗自动关闭**；结论回写即通知，**改分前后都留痕**。
本批次先跑助考项目（`projectCode=ASSIST`），下周出分。

约定：
- 等级规则模块状态：`DRAFT 草稿 / ENABLED 启用 / DISABLED 停用`；
  规则版本状态：`DRAFT 草稿 / EFFECTIVE 生效中 / ARCHIVED 已归档`。
- 分数段冻结在**版本**里，发布即不可改；发新版时旧生效版本自动归档。
  分数段必须连续、不重叠、完整覆盖满分区间，否则建版本直接拒绝。
  已出成绩永久指向当时命中的版本，之后发新版不改历史等级。
- 出分是批量动作，同样先 `*/preview` 后执行；首次落级、重新出分、复核维持、复核改分
  都在 `t_score_change_log` 留一条前后值痕迹（只追加）。
- 窗口是否开放一律按当前钟点实时判定（`openNow`）：定时任务每分钟兜底落 `CLOSED`，
  即使任务没扫到，超窗也提不了复核（懒关闭）。窗口关闭只拦新申请，**窗内已提交的在办复核不作废**，
  仍可处理到结论，并通知学员"申请继续处理"。
- 结论回写、成绩变更、留痕、通知在同一事务完成；通知先落 `t_grade_notification`（无外发渠道，可查），
  后续接短信 / 站内信时扫 `PENDING` 推送。
- grade 的七张表已纳入工作区快照与回退范围。

### 8. 等级规则与版本

- `POST /api/grade/rules`、`GET /api/grade/rules?projectCode=ASSIST`、`GET /api/grade/rules/{id}`。
- `POST /api/grade/rules/{id}/disable`、`/enable`：停用 / 重新启用。
- `POST /api/grade/rules/{id}/versions`：新建草稿版本（body：`maxScore`、`bands:[{minScore,maxScore,level,label}]`）。
- `GET  /api/grade/rules/{id}/versions`、`GET /api/grade/rules/versions/{versionId}`（含解析后的分数段）。
- `POST /api/grade/rules/versions/{versionId}/publish`：发布，旧生效版本归档。
- `POST /api/grade/rules/versions/{versionId}/discard`：仅草稿可丢弃。
- `GET  /api/grade/rules/versions/{versionId}/grade?rawScore=`：试算卷面分命中的等级（对口径用）。

### 9. 批次复核窗口（随批次配置）

- `POST /api/grade/batches/{batchId}/window`：配置规则与窗口起止
  （`ruleId`、`windowStartAt`、`windowEndAt`；开始时间不填默认出分即开放，截止不填表示不限时）。
  出分后窗口锁定，不能再改起止。
- `GET  /api/grade/batches/{batchId}/window`：实时口径，含 `openNow`、关闭方式与原因。
- `POST /api/grade/batches/{batchId}/window/close?reason=`：人工提前关闭。
- `POST /api/grade/batches/{batchId}/window/auto-close/run`：立即跑一次超窗扫描（平时定时任务每分钟自动跑）。

### 10. 出分落级

- `POST /api/grade/scores/release/preview` → `POST /api/grade/scores/release`：
  body `{batchId, scores:[{regNo, rawScore}]}`；可带 `batchId` 做归属校验，也可不带按报名号各自落批次。
  名单内报名号重复整批拒收；不存在、跨批次、超出满分区间逐条跳过并给原因；
  有在办复核的学员不能重新出分。出分即记录出分钟点、开放窗口、发出分通知。
- `GET  /api/grade/scores?batchId=`：批次成绩与等级（含落级版本号）。
- `GET  /api/grade/scores/by-reg-no?regNo=`：按报名号查成绩（坐席答询用）。
- `GET  /api/grade/scores/{id}/change-logs`：改分前后痕迹。

### 11. 成绩复核

- `POST /api/grade/reviews/submit?regNo=`：学员窗口内提交（body：`requestReason`、`contactPhone`）；
  超窗 / 未出分 / 未开放分别给明确原因；同一学员同批次只允许一条在办申请。
  提交时快照当时窗口起止。
- `POST /api/grade/reviews/{id}/accept`：受理（SUBMITTED→PROCESSING）。
- `POST /api/grade/reviews/{id}/conclusion`：回写结论：
  - `{"action":"CONFIRM"}` 维持原判（照样留痕、通知）；
  - `{"action":"CHANGE","finalScore":72}` 复核改分，按**原落级版本**重新定级，前后分值 / 等级留痕；
  - `{"action":"REJECT"}` 不予受理，不动成绩。
- `GET  /api/grade/reviews?batchId=&status=`、`GET /api/grade/reviews/{id}`。
- `GET  /api/grade/reviews/{id}/notifications`：该条复核的受理 / 窗口关闭 / 结论通知。
