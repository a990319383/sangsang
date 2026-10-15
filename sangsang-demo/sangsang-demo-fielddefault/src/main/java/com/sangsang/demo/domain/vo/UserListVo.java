package com.sangsang.demo.domain.vo;

import com.sangsang.demo.domain.bo.SqlLogBo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserListVo {
    private List<UserVo> users;
    private List<SqlLogBo> sqlLogs;
    private Long total;
    private Integer pageNum;
    private Integer pageSize;
}
