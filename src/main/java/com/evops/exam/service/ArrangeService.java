package com.evops.exam.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.common.BusinessException;
import com.evops.common.OperatorHolder;
import com.evops.exam.common.ExamConstants;
import com.evops.exam.dto.ArrangeRequest;
import com.evops.exam.entity.AdmissionTicket;
import com.evops.exam.entity.ExamBatch;
import com.evops.exam.entity.ExamRoom;
import com.evops.exam.entity.Registration;
import com.evops.exam.mapper.AdmissionTicketMapper;
import com.evops.exam.mapper.ExamRoomMapper;
import com.evops.exam.vo.ActionPreview;
import com.evops.exam.vo.ActionResult;
import com.evops.exam.vo.AffectedItem;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 编排：按批次把已报名名册排进考点下各考场，一人一条准考证（证号、考场、座位）。
 * 证号撞号 / 座位撞号直接拒收（数据库唯一约束兜底）。
 */
@Service
public class ArrangeService {

    private final AdmissionTicketMapper ticketMapper;
    private final ExamRoomMapper roomMapper;
    private final ExamBatchService batchService;
    private final ExamSiteService siteService;
    private final RegistrationService registrationService;
    private final StageResultService stageResultService;

    public ArrangeService(AdmissionTicketMapper ticketMapper,
                          ExamRoomMapper roomMapper,
                          ExamBatchService batchService,
                          ExamSiteService siteService,
                          RegistrationService registrationService,
                          StageResultService stageResultService) {
        this.ticketMapper = ticketMapper;
        this.roomMapper = roomMapper;
        this.batchService = batchService;
        this.siteService = siteService;
        this.registrationService = registrationService;
        this.stageResultService = stageResultService;
    }

    // ---------- 预检：展示编排影响范围与座位方案 ----------

    @Transactional(readOnly = true)
    public ActionPreview previewArrange(ArrangeRequest request) {
        Plan plan = buildPlan(request);

        List<AffectedItem> items = new ArrayList<>();
        int actionable = 0;
        int skip = 0;
        for (PlanLine line : plan.lines) {
            boolean ok = line.room != null;
            String note = ok
                    ? String.format("%s 考场 %s 第 %d 号座，证号 %s",
                            line.siteName, line.room.getRoomNo(), line.seatNo, line.ticketNo)
                    : "考位不足，无法编排（需增加考场或容量）";
            if (ok) {
                actionable++;
            } else {
                skip++;
            }
            items.add(new AffectedItem(line.reg.getRegNo(), line.reg.getName(),
                    line.reg.getStatus(), ok, note));
        }

        ActionPreview preview = new ActionPreview();
        preview.setAction("ARRANGE");
        preview.setTotalScanned(plan.lines.size());
        preview.setActionableCount(actionable);
        preview.setSkipCount(skip);
        preview.setSummary(String.format("批次 %s：报名 %d 人，可用考位 %d 个，可编排 %d 人，未排下 %d 人",
                plan.batch.getCode(), plan.lines.size(), plan.totalCapacity, actionable, skip));
        preview.setItems(items);
        return preview;
    }

    // ---------- 执行编排 ----------

    @Transactional
    public ActionResult arrange(ArrangeRequest request) {
        Plan plan = buildPlan(request);
        String operator = OperatorHolder.current();

        // 已有准考证不允许重复编排，避免覆盖已打印 / 已发放状态
        Long existing = ticketMapper.selectCount(
                new QueryWrapper<AdmissionTicket>().eq("batch_id", plan.batch.getId()));
        if (existing != null && existing > 0) {
            throw new BusinessException("ALREADY_ARRANGED",
                    "批次 " + plan.batch.getCode() + " 已编排过，不能重复编排；如需调整请回退到编排前版本");
        }

        List<String> affected = new ArrayList<>();
        int seq = 1;
        for (PlanLine line : plan.lines) {
            if (line.room == null) {
                // 容量不足整批拒收，不落半条
                throw new BusinessException("CAPACITY_EXCEEDED",
                        String.format("考位不足：报名 %d 人，可用考位 %d 个，缺口 %d 个",
                                plan.lines.size(), plan.totalCapacity,
                                plan.lines.size() - plan.totalCapacity));
            }
            AdmissionTicket ticket = new AdmissionTicket();
            ticket.setTicketNo(line.ticketNo);
            ticket.setRegistrationId(line.reg.getId());
            ticket.setBatchId(plan.batch.getId());
            ticket.setSiteId(plan.batch.getSiteId());
            ticket.setRoomId(line.room.getId());
            ticket.setSeatNo(line.seatNo);
            ticket.setStatus(ExamConstants.TICKET_PENDING);
            try {
                ticketMapper.insert(ticket);
            } catch (DuplicateKeyException e) {
                // 证号撞号 / 同批次考场座位撞号：直接拒收，事务回滚
                throw new BusinessException("TICKET_COLLISION",
                        "准考证撞号（证号或座位冲突），整批拒收：" + line.ticketNo);
            }

            Registration reg = line.reg;
            reg.setStatus(ExamConstants.REG_ARRANGED);
            registrationService.updateRegistration(reg);

            affected.add(ticket.getTicketNo());
            seq++;
        }

        plan.batch.setStatus(ExamConstants.BATCH_ARRANGED);
        batchService.updateBatch(plan.batch);

        stageResultService.record(plan.batch.getId(), ExamConstants.STAGE_ARRANGEMENT,
                affected.size(),
                String.format("编排 %d 人，使用考场 %d 间，考位 %d 个",
                        affected.size(), plan.roomCount, plan.totalCapacity),
                operator);

        ActionResult result = new ActionResult();
        result.setAction("ARRANGE");
        result.setAffectedCount(affected.size());
        result.setSkipCount(0);
        result.setOperator(operator);
        result.setExecutedAt(LocalDateTime.now().toString());
        result.setAffectedKeys(affected);
        result.setSkipped(new ArrayList<>());
        return result;
    }

    // ---------- 编排方案构建（预检与执行共用，保证两者一致） ----------

    private Plan buildPlan(ArrangeRequest request) {
        ExamBatch batch = batchService.getBatch(request.getBatchId());
        siteService.getSite(batch.getSiteId());

        List<Registration> regs = registrationService.listByBatch(batch.getId());
        if (regs.isEmpty()) {
            throw new BusinessException("NO_REGISTRATION", "批次下还没有报名名册，无法编排：" + batch.getCode());
        }

        List<ExamRoom> rooms = roomMapper.selectList(new QueryWrapper<ExamRoom>()
                .eq("site_id", batch.getSiteId())
                .eq("status", "ENABLED")
                .orderByAsc("room_no"));
        if (rooms.isEmpty()) {
            throw new BusinessException("NO_ROOM", "考点下没有启用的考场，无法编排");
        }

        // 指定考场顺序时校验：必须全部属于本考点且不重不漏
        if (request.getRoomOrder() != null && !request.getRoomOrder().isEmpty()) {
            List<String> order = request.getRoomOrder();
            if (new HashSet<>(order).size() != order.size()) {
                throw new BusinessException("ROOM_ORDER_DUP", "指定的考场顺序存在重复考场号");
            }
            List<ExamRoom> ordered = new ArrayList<>();
            for (String roomNo : order) {
                ExamRoom hit = null;
                for (ExamRoom room : rooms) {
                    if (room.getRoomNo().equals(roomNo.trim())) {
                        hit = room;
                        break;
                    }
                }
                if (hit == null) {
                    throw new BusinessException("ROOM_NOT_IN_SITE",
                            "考场不属于本批次考点或未启用：" + roomNo);
                }
                ordered.add(hit);
            }
            // 未列入顺序的启用考场追加在后面，容量仍可用
            Set<String> picked = new HashSet<>(order);
            for (ExamRoom room : rooms) {
                if (!picked.contains(room.getRoomNo())) {
                    ordered.add(room);
                }
            }
            rooms = ordered;
        }

        int totalCapacity = 0;
        for (ExamRoom room : rooms) {
            totalCapacity += room.getCapacity();
        }

        String siteName = siteService.getSite(batch.getSiteId()).getName();

        Plan plan = new Plan();
        plan.batch = batch;
        plan.rooms = rooms;
        plan.roomCount = rooms.size();
        plan.totalCapacity = totalCapacity;
        plan.siteName = siteName;

        int roomIndex = 0;
        int seatNo = 1;
        int seq = 1;
        for (Registration reg : regs) {
            // 跳到下一间还有空位的考场
            while (roomIndex < rooms.size() && seatNo > rooms.get(roomIndex).getCapacity()) {
                roomIndex++;
                seatNo = 1;
            }
            PlanLine line = new PlanLine();
            line.reg = reg;
            line.siteName = siteName;
            if (roomIndex < rooms.size()) {
                line.room = rooms.get(roomIndex);
                line.seatNo = seatNo;
                line.ticketNo = generateTicketNo(batch, seq);
                seatNo++;
            } else {
                line.room = null;
            }
            plan.lines.add(line);
            seq++;
        }
        return plan;
    }

    /**
     * 证号规则：ZK + 批次编码 + 4 位流水。批次编码已唯一，故证号全局唯一；
     * 唯一约束 uk_ticket_no 再兜底，撞号直接拒收。
     */
    private String generateTicketNo(ExamBatch batch, int seq) {
        return "ZK" + batch.getCode() + "-" + String.format("%04d", seq);
    }

    private static class Plan {
        private ExamBatch batch;
        private List<ExamRoom> rooms;
        private int roomCount;
        private int totalCapacity;
        private String siteName;
        private final List<PlanLine> lines = new ArrayList<>();
    }

    private static class PlanLine {
        private Registration reg;
        private ExamRoom room;
        private int seatNo;
        private String ticketNo;
        private String siteName;
    }
}
