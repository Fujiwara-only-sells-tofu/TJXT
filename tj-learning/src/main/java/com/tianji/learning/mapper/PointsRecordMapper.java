package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.PointsRecord;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 * 学习积分记录，每个月底清零 Mapper 接口
 * </p>
 *
 * @author 张辰逸
 * @since 2024-09-15
 */
public interface PointsRecordMapper extends BaseMapper<PointsRecord> {

    @Insert("CREATE TABLE `${tableName}`\n" +
            "        (\n" +
            "            `id`      BIGINT NOT NULL AUTO_INCREMENT COMMENT '积分记录表id',\n" +
            "            `user_id` BIGINT NOT NULL COMMENT '用户id',\n" +
            "            `type`    INT    NOT NULL COMMENT '积分方式：1-课程学习，2-每日签到，3-课程问答， 4-课程笔记，5-课程评价',\n" +
            "            `points`  INT    NOT NULL COMMENT '积分值',\n" +
            "            `create_time` DATETIME NOT NULL COMMENT '创建时间',\n" +
            "            PRIMARY KEY (`id`) USING BTREE,\n" +
            "            INDEX `idx_user_id` (`user_id`) USING BTREE\n" +
            "        )\n" +
            "            COMMENT ='赛季积分记录表'\n" +
            "            COLLATE = 'utf8mb4_0900_ai_ci'\n" +
            "            ENGINE = InnoDB\n" +
            "            ROW_FORMAT = DYNAMIC")
    void createPointsRecordTableBySeason(@Param("tableName") String tableName);
}
