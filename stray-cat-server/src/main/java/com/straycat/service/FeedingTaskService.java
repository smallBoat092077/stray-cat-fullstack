package com.straycat.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.straycat.entity.CatProfile;
import com.straycat.entity.FeedingTask;
import com.straycat.vo.FeedingRecordVO;

import java.util.List;

public interface FeedingTaskService extends IService<FeedingTask> {

    /**
     * 创建投喂任务
     */
    Long createTask(FeedingTask task);

    /**
     * 打赏即委托投喂：按猫咪坐标生成一条待接单任务。
     * 猫咪没有坐标时无法打卡，返回 null 而不抛异常，打赏本身要成立。
     */
    Long createTaskFromTip(CatProfile cat, Long tipId, Long sponsorId, Integer coinAmount);

    /**
     * 接单
     */
    void acceptTask(Long taskId, Long volunteerId);

    /**
     * 打卡
     */
    void checkIn(Long taskId, Long volunteerId, double latitude, double longitude, String images);

    /**
     * 某只猫咪最近的投喂打卡记录
     */
    List<FeedingRecordVO> listRecordsByCat(Long catId);

    /**
     * 任务详情，带上关联猫咪和接单人的展示信息
     */
    FeedingTask getTaskDetail(Long taskId);

    /**
     * 给一批任务补齐展示用的关联信息：猫咪名字地点 + 接单人身份与历史完成次数
     */
    void fillDisplayInfo(List<FeedingTask> tasks);

    /**
     * 当前用户作为志愿者的投喂打卡记录
     */
    Page<FeedingRecordVO> listMyRecords(Long volunteerId, Integer pageNum, Integer pageSize);

    /**
     * 按审核状态取打卡记录，status 为空取全部，给审核工作台用
     */
    List<FeedingRecordVO> listRecords(Integer status, Integer limit);

    /**
     * 审核投喂打卡记录：通过则任务转「已完成」并给志愿者加 1 信用积分，
     * 驳回则任务退回「已接单」等志愿者重新打卡；猫粮在打卡时已发，不受这里影响
     */
    void reviewRecord(Long recordId, Long reviewerId, Integer status, String comment);
}
