package com.sangsang.demo.domain.vo;

import com.sangsang.demo.domain.bo.SqlLogBo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户列表查询响应，包含SQL日志
 *
 * @author liutangqi
 * @date 2026/3/31
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserListVo {
    /**
     * 用户列表
     */
    private List<UserVo> users;

    /**
     * sangsang改写前后的SQL对比列表
     */
    private List<SqlLogBo> sqlLogs;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码
     */
    private Integer pageNum;

    /**
     * 每页大小
     */
    private Integer pageSize;
}
