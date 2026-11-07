package com.straycat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.straycat.common.MessageType;
import com.straycat.common.ResultCode;
import com.straycat.entity.CatProfile;
import com.straycat.entity.FeedingRecord;
import com.straycat.entity.FeedingTask;
import com.straycat.entity.SysUser;
import com.straycat.exception.BusinessException;
import com.straycat.mapper.CatProfileMapper;
import com.straycat.mapper.FeedingRecordMapper;
import com.straycat.mapper.FeedingTaskMapper;
import com.straycat.mapper.SysUserMapper;
import com.straycat.service.CoinService;
import com.straycat.service.FeedingTaskService;
import com.straycat.service.NotifyService;
import com.straycat.util.CatLifeStatus;
import com.straycat.util.GeoDistance;
import com.straycat.util.NotifyTemplates;
import com.straycat.vo.FeedingRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FeedingTaskServiceImpl extends ServiceImpl<FeedingTaskMapper, FeedingTask> implements FeedingTaskService {

    private static final int MAX_CHECKIN_IMAGES = 3;
    private static final int RECORD_LIMIT = 5;
    private static final int MAX_RECORD_QUERY = 50;
    private static final int MY_RECORDS_PAGE = 20;
    private static final int RECORD_PENDING = 0;
    private static final int RECORD_APPROVED = 1;
    private static final int RECORD_REJECTED = 2;

    private static final int TASK_PENDING = 0;
    private static final int TASK_ACCEPTED = 1;
    /**
     * 已打卡、等管理员审核
     */
    private static final int TASK_CHECKED_IN = 2;
    private static final int TASK_DONE = 3;
    /**
     * 一次审核通过给志愿者的信用积分
     */
    private static final int CREDIT_REWARD = 1;
    /**
     * 打赏生成的任务默认按日常投喂，打卡半径 100 米
     */
    private static final int DEFAULT_TASK_TYPE = 0;
    private static final int DEFAULT_RADIUS = 100;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private FeedingRecordMapper feedingRecordMapper;

    @Resource
    private SysUserMapper sysUserMapper;

    @Resource
    private CatProfileMapper catProfileMapper;

    @Resource
    private CoinService coinService;

    @Resource
    private NotifyService notifyService;

    @Override
    public Long createTask(FeedingTask task) {
        task.setStatus(TASK_PENDING);
        save(task);
        moveCat(task.getCatId(), CatLifeStatus.FEEDING);
        return task.getId();
    }

    @Override
    public Long createTaskFromTip(CatProfile cat, Long tipId, Long sponsorId, Integer coinAmount) {
        if (cat == null || cat.getLatitude() == null || cat.getLongitude() == null) {
            log.info("猫咪缺少坐标，打赏未生成投喂任务: catId={}, tipId={}", cat == null ? null : cat.getId(), tipId);
            return null;
        }
        FeedingTask task = new FeedingTask();
        task.setCatId(cat.getId());
        task.setTaskType(DEFAULT_TASK_TYPE);
        task.setSponsorId(sponsorId);
        task.setTipId(tipId);
        task.setAmount(coinAmount == null ? null : BigDecimal.valueOf(coinAmount));
        task.setLatitude(cat.getLatitude());
        task.setLongitude(cat.getLongitude());
        task.setRadius(DEFAULT_RADIUS);
        return createTask(task);
    }

    @Override
    public void acceptTask(Long taskId, Long volunteerId) {
        String lockKey = "lock:feeding:task:" + taskId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("请勿重复操作");
            }

            FeedingTask task = getById(taskId);
            if (task == null) {
                throw new BusinessException("任务不存在");
            }
            if (task.getStatus() != TASK_PENDING) {
                throw new BusinessException("任务已被接单");
            }

            task.setVolunteerId(volunteerId);
            task.setStatus(TASK_ACCEPTED);
            updateById(task);

            if (task.getSponsorId() != null) {
                String catName = catNameOf(task.getCatId());
                String volunteerName = notifyService.displayName(volunteerId);
                notifyService.send(MessageType.TASK_ACCEPTED, task.getSponsorId(), taskId,
                        catName + " 的投喂任务已由「" + volunteerName + "」接单，打卡后可在投喂详情查看",
                        NotifyTemplates.feeding(catName, volunteerName, LocalDateTime.now()));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public void checkIn(Long taskId, Long volunteerId, double latitude, double longitude, String images) {
        String lockKey = "lock:feeding:checkin:" + taskId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            if (!lock.tryLock(0, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("请勿重复提交");
            }

            FeedingTask task = getById(taskId);
            if (task == null) {
                throw new BusinessException("任务不存在");
            }
            if (task.getStatus() != TASK_ACCEPTED) {
                throw new BusinessException("任务状态不正确");
            }
            if (!volunteerId.equals(task.getVolunteerId())) {
                throw new BusinessException("只有接单的人才可以打卡");
            }

            double distance = GeoDistance.metersBetween(
                    latitude, longitude,
                    task.getLatitude().doubleValue(), task.getLongitude().doubleValue()
            );

            if (distance > task.getRadius()) {
                throw new BusinessException("距离投喂点过远(" + (int) distance + "米)，请在有效范围内打卡");
            }

            FeedingRecord record = new FeedingRecord();
            record.setTaskId(taskId);
            record.setCatId(task.getCatId());
            record.setVolunteerId(volunteerId);
            record.setCheckInTime(LocalDateTime.now());
            record.setCheckInLatitude(BigDecimal.valueOf(latitude));
            record.setCheckInLongitude(BigDecimal.valueOf(longitude));
            record.setDistance((int) distance);
            record.setFoodUsed(task.getFoodType());
            record.setImages(cleanImages(images));
            record.setStatus(0);
            record.setCreatedAt(LocalDateTime.now());
            feedingRecordMapper.insert(record);

            task.setStatus(TASK_CHECKED_IN);
            updateById(task);
            moveCat(task.getCatId(), CatLifeStatus.FEEDING);
            coinService.grant(volunteerId, CoinService.CHECKIN_REWARD, CoinService.BIZ_CHECKIN,
                    taskId, "完成投喂任务打卡");

            String catName = catNameOf(task.getCatId());
            String volunteerName = notifyService.displayName(volunteerId);
            LocalDateTime checkInAt = LocalDateTime.now();
            if (task.getSponsorId() != null) {
                notifyService.send(MessageType.RECORD_SUBMITTED, task.getSponsorId(), taskId,
                        catName + " 已完成投喂打卡，管理员审核通过后任务结束",
                        NotifyTemplates.feeding(catName, volunteerName, checkInAt));
            }
            notifyService.sendToAdmins(MessageType.RECORD_PENDING, record.getId(),
                    volunteerName + " 提交的 " + catName + " 投喂打卡等待审核",
                    NotifyTemplates.feeding(catName, volunteerName, checkInAt));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作失败");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public List<FeedingRecordVO> listRecordsByCat(Long catId) {
        LambdaQueryWrapper<FeedingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedingRecord::getCatId, catId)
                .eq(FeedingRecord::getStatus, RECORD_APPROVED)
                .orderByDesc(FeedingRecord::getCheckInTime)
                .last("LIMIT " + RECORD_LIMIT);
        return toVOs(feedingRecordMapper.selectList(wrapper));
    }

    @Override
    public List<FeedingRecordVO> listRecords(Integer status, Integer limit) {
        int size = (limit == null || limit <= 0) ? RECORD_LIMIT : Math.min(limit, MAX_RECORD_QUERY);
        LambdaQueryWrapper<FeedingRecord> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(FeedingRecord::getStatus, status);
        }
        wrapper.orderByDesc(FeedingRecord::getCheckInTime).last("LIMIT " + size);
        return toVOs(feedingRecordMapper.selectList(wrapper));
    }

    @Override
    public Page<FeedingRecordVO> listMyRecords(Long volunteerId, Integer pageNum, Integer pageSize) {
        int size = (pageSize == null || pageSize <= 0) ? MY_RECORDS_PAGE : Math.min(pageSize, MAX_RECORD_QUERY);
        LambdaQueryWrapper<FeedingRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(FeedingRecord::getVolunteerId, volunteerId)
                .orderByDesc(FeedingRecord::getCheckInTime);
        Page<FeedingRecord> source = feedingRecordMapper.selectPage(new Page<>(pageNum == null ? 1 : pageNum, size), wrapper);
        Page<FeedingRecordVO> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(toVOs(source.getRecords()));
        return target;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRecord(Long recordId, Long reviewerId, Integer status, String comment) {
        if (recordId == null) {
            throw new BusinessException(ResultCode.PARAM_MISSING.getCode(), "缺少打卡记录 id");
        }
        if (status == null || (status != RECORD_APPROVED && status != RECORD_REJECTED)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "审核结果只能是已通过或已拒绝");
        }
        FeedingRecord record = feedingRecordMapper.selectById(recordId);
        if (record == null) {
            throw new BusinessException("打卡记录不存在");
        }
        if (reviewerId.equals(record.getVolunteerId())) {
            throw new BusinessException("不能审核自己的打卡记录");
        }
        if (!Objects.equals(record.getStatus(), RECORD_PENDING)) {
            throw new BusinessException("该打卡记录已审核过");
        }
        record.setStatus(status);
        record.setReviewerId(reviewerId);
        record.setReviewComment(comment);
        record.setReviewedAt(LocalDateTime.now());
        feedingRecordMapper.updateById(record);

        if (status == RECORD_APPROVED) {
            finishTask(record);
            if (record.getVolunteerId() != null) {
                sysUserMapper.addCredit(record.getVolunteerId(), CREDIT_REWARD);
                log.info("打卡审核通过: record={}, volunteer={}, 信用积分 +{}", recordId, record.getVolunteerId(), CREDIT_REWARD);
            }
        } else {
            // 驳回后退回「已接单」，让接单人重新打卡
            FeedingTask task = taskOf(record);
            if (task != null && Objects.equals(task.getStatus(), TASK_CHECKED_IN)) {
                task.setStatus(TASK_ACCEPTED);
                updateById(task);
            }
        }

        FeedingTask reviewedTask = taskOf(record);
        Long taskIdForPage = reviewedTask == null ? null : reviewedTask.getId();
        String catName = catNameOf(record.getCatId());
        String volunteerName = notifyService.displayName(record.getVolunteerId());
        Map<String, String> wxData = NotifyTemplates.feeding(catName, volunteerName, record.getCheckInTime());
        if (status == RECORD_APPROVED) {
            notifyService.send(MessageType.RECORD_APPROVED, record.getVolunteerId(), taskIdForPage,
                    catName + " 的投喂打卡已通过，信用积分 +" + CREDIT_REWARD, wxData);
        } else {
            String reason = comment == null || comment.isBlank() ? "请在有效范围内重新打卡" : comment;
            notifyService.send(MessageType.RECORD_REJECTED, record.getVolunteerId(), taskIdForPage,
                    catName + " 的投喂打卡未通过：" + reason, wxData);
        }
    }

    /**
     * 审核通过即任务完成，只有走到这一步才写 completedAt
     */
    private void finishTask(FeedingRecord record) {
        FeedingTask task = taskOf(record);
        if (task == null) {
            return;
        }
        task.setStatus(TASK_DONE);
        task.setCompletedAt(record.getReviewedAt() == null ? LocalDateTime.now() : record.getReviewedAt());
        updateById(task);
    }

    private FeedingTask taskOf(FeedingRecord record) {
        return record.getTaskId() == null ? null : getById(record.getTaskId());
    }

    /**
     * 投喂行为联动猫咪生活状态，不在允许流转内就保持原样
     */
    private void moveCat(Long catId, int target) {
        if (catId == null) {
            return;
        }
        CatProfile cat = catProfileMapper.selectById(catId);
        if (cat == null || !CatLifeStatus.canMove(cat.getLifeStatus(), target)) {
            return;
        }
        cat.setLifeStatus(target);
        cat.setUpdatedAt(LocalDateTime.now());
        catProfileMapper.updateById(cat);
    }

    private List<FeedingRecordVO> toVOs(List<FeedingRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = new HashMap<>();
        List<Long> volunteerIds = records.stream().map(FeedingRecord::getVolunteerId).filter(Objects::nonNull).distinct().toList();
        if (!volunteerIds.isEmpty()) {
            for (SysUser user : sysUserMapper.selectBatchIds(volunteerIds)) {
                names.put(user.getId(), user.getNickname());
            }
        }
        Map<Long, CatProfile> cats = new HashMap<>();
        List<Long> catIds = records.stream().map(FeedingRecord::getCatId).filter(Objects::nonNull).distinct().toList();
        if (!catIds.isEmpty()) {
            catProfileMapper.selectBatchIds(catIds).forEach(cat -> cats.put(cat.getId(), cat));
        }
        Map<Long, FeedingTask> tasks = loadTasks(records);

        return records.stream().map(record -> {
            CatProfile cat = cats.get(record.getCatId());
            FeedingTask task = record.getTaskId() == null ? null : tasks.get(record.getTaskId());
            FeedingRecordVO vo = new FeedingRecordVO();
            vo.setId(record.getId());
            vo.setTaskId(record.getTaskId());
            vo.setCatId(record.getCatId());
            vo.setCatName(cat == null ? "" : cat.getName());
            vo.setLocationDesc(cat == null ? null : cat.getLocationDesc());
            vo.setVolunteerName(names.getOrDefault(record.getVolunteerId(), "好心人"));
            vo.setCheckInTime(record.getCheckInTime());
            vo.setDistance(record.getDistance());
            vo.setFoodUsed(record.getFoodUsed());
            vo.setFoodAmount(foodAmount(task));
            vo.setImages(splitImages(record.getImages()));
            vo.setStatus(record.getStatus());
            vo.setReviewComment(record.getReviewComment());
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 打赏委托的单看认捐数量，人工派单看自备猫粮数量
     */
    private Integer foodAmount(FeedingTask task) {
        if (task == null) {
            return null;
        }
        if (task.getAmount() != null) {
            return task.getAmount().intValue();
        }
        return task.getFoodQuantity();
    }

    private Map<Long, FeedingTask> loadTasks(Collection<FeedingRecord> records) {
        List<Long> taskIds = records.stream()
                .map(FeedingRecord::getTaskId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return listByIds(taskIds).stream()
                .collect(Collectors.toMap(FeedingTask::getId, task -> task));
    }

    @Override
    public FeedingTask getTaskDetail(Long taskId) {
        FeedingTask task = getById(taskId);
        if (task != null) {
            fillDisplayInfo(List.of(task));
        }
        return task;
    }

    @Override
    public void fillDisplayInfo(List<FeedingTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        Map<Long, CatProfile> cats = loadCats(tasks);
        for (FeedingTask task : tasks) {
            CatProfile cat = cats.get(task.getCatId());
            if (cat != null) {
                task.setCatName(cat.getName());
                task.setLocationDesc(cat.getLocationDesc());
            }
        }
        fillVolunteerInfo(tasks);
    }

    @Override
    public Page<FeedingTask> listTasks(Integer pageNum, Integer pageSize, Integer status, Long sponsorId, String keyword) {
        return this.baseMapper.selectTaskPage(
                new Page<>(pageNum, pageSize),
                keyword == null || keyword.isBlank() ? null : keyword.trim(),
                status,
                sponsorId
        );
    }

    /**
     * 接单人身份，让打赏人知道这一单是谁在跑、他做过多少单
     */
    private void fillVolunteerInfo(List<FeedingTask> tasks) {
        List<Long> volunteerIds = tasks.stream()
                .map(FeedingTask::getVolunteerId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (volunteerIds.isEmpty()) {
            return;
        }
        Map<Long, SysUser> users = new HashMap<>();
        for (SysUser user : sysUserMapper.selectBatchIds(volunteerIds)) {
            users.put(user.getId(), user);
        }
        Map<Long, Integer> doneCounts = countDoneTasks(volunteerIds);
        for (FeedingTask task : tasks) {
            SysUser user = users.get(task.getVolunteerId());
            if (user == null) {
                continue;
            }
            task.setVolunteerName(user.getNickname());
            task.setVolunteerRole(user.getRole());
            task.setVolunteerDoneCount(doneCounts.getOrDefault(user.getId(), 0));
        }
    }

    /**
     * 一次 GROUP BY 拿到这几个志愿者各自的已完成单数，免得按任务逐个查
     */
    private Map<Long, Integer> countDoneTasks(Collection<Long> volunteerIds) {
        QueryWrapper<FeedingTask> wrapper = new QueryWrapper<>();
        wrapper.select("volunteer_id AS volunteerId", "COUNT(*) AS doneCount")
                .eq("status", TASK_DONE)
                .in("volunteer_id", volunteerIds)
                .groupBy("volunteer_id");
        Map<Long, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : listMaps(wrapper)) {
            Object id = row.get("volunteerId");
            Object count = row.get("doneCount");
            if (id != null) {
                counts.put(Long.valueOf(id.toString()), count == null ? 0 : Integer.parseInt(count.toString()));
            }
        }
        return counts;
    }

    /**
     * 一次查回这批任务涉及的猫咪，个别 id 查不到也不影响其余任务
     */
    private Map<Long, CatProfile> loadCats(Collection<FeedingTask> tasks) {
        List<Long> catIds = tasks.stream()
                .map(FeedingTask::getCatId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (catIds.isEmpty()) {
            return Map.of();
        }
        return catProfileMapper.selectBatchIds(catIds).stream()
                .collect(Collectors.toMap(CatProfile::getId, cat -> cat));
    }

    private String catNameOf(Long catId) {
        if (catId == null) {
            return "猫咪";
        }
        CatProfile cat = catProfileMapper.selectById(catId);
        return cat == null || cat.getName() == null ? "猫咪" : cat.getName();
    }

    /**
     * 只收我们自己生成的 /uploads/ 相对路径，并截到 3 张，免得这个字段被拿来塞外链。
     */
    private String cleanImages(String images) {
        if (images == null || images.isBlank()) {
            return null;
        }
        List<String> kept = Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(path -> path.startsWith("/uploads/"))
                .limit(MAX_CHECKIN_IMAGES)
                .collect(Collectors.toList());
        return kept.isEmpty() ? null : String.join(",", kept);
    }

    private List<String> splitImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        return Arrays.stream(images.split(","))
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .collect(Collectors.toList());
    }
}
