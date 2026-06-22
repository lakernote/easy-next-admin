package com.laker.admin.module.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.laker.admin.module.audit.dto.AuditApiDailyVisitView;
import com.laker.admin.module.audit.dto.AuditApiTopIpView;
import com.laker.admin.module.audit.entity.AuditApiLog;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 日志 Mapper 接口
 * </p>
 *
 * @author easynext
 * @since 2021-08-16
 */
public interface AuditApiLogMapper extends BaseMapper<AuditApiLog> {


    //    @Select("select DATE_FORMAT(create_time,'%Y-%m-%d') date,count(*) value from audit_api_log where DATE_SUB(CURDATE(), INTERVAL 7 DAY) <= create_time  group by date ORDER BY create_time ")
    @Select("SELECT\n" +
            "\tdate,\n" +
            "\tcount(*) value\n" +
            "FROM\n" +
            "\t(\n" +
            "\tselect\n" +
            "\t\tDATE_FORMAT(create_time, '%Y-%m-%d') date\n" +
            "\tFROM\n" +
            "\t\taudit_api_log\n" +
            "\tWHERE\n" +
            "\t\tDATE_SUB(CURDATE(), INTERVAL 7 DAY) <= create_time\n" +
            "\t\tAND deleted = 0\n" +
            "\tORDER BY\n" +
            "\t\tcreate_time ) tmp\n" +
            "GROUP BY\n" +
            "\tdate\n" +
            "\t")
    List<AuditApiDailyVisitView> selectStatistics7Day();

    @Select("SELECT\n" +
            "\tw.ip,\n" +
            "\tcity,\n" +
            "\tcount( * ) \n" +
            "\tVALUE\t\n" +
            "FROM\n" +
            "\taudit_api_log w \n" +
            "WHERE\n" +
            "\tDATE_SUB( CURDATE( ), INTERVAL 1 day ) <= w.create_time \n" +
            "\tAND w.deleted = 0 \n" +
            "GROUP BY\n" +
            "\tw.ip,city \n" +
            "ORDER BY\n" +
            "\t\n" +
            "VALUE\n" +
            "DESC \n" +
            "LIMIT 10")
    List<AuditApiTopIpView> selectStatisticsVisitsTop10IP();

    @Select("SELECT count(DISTINCT ip) from audit_api_log where deleted = 0")
    int selectDistinctIp();

}
