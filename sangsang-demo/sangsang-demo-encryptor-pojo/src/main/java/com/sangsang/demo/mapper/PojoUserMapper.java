package com.sangsang.demo.mapper;

import com.sangsang.demo.domain.dto.UserQueryDto;
import com.sangsang.demo.domain.dto.UserSaveDto;
import com.sangsang.demo.domain.dto.UserUpdateDto;
import com.sangsang.demo.domain.vo.UserResultMapVo;
import com.sangsang.demo.domain.vo.UserResultTypeVo;
import com.sangsang.demo.domain.vo.UserUnderLineVo;
import com.sangsang.demo.domain.vo.UserVo;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * @author liutangqi
 * @date 2026/3/30 17:54
 */
public interface PojoUserMapper {
    /**
     * 按用户名或电话模糊查询用户列表
     */
    List<UserVo> getUserList(UserQueryDto dto);

    /**
     * 统计用户总数
     */
    Long countUserList(UserQueryDto dto);

    /**
     * 根据ID查询用户
     */
    UserVo getUserById(@Param("id") Long id);

    /**
     * 新增用户
     */
    int insertUser(UserSaveDto dto);

    /**
     * 修改用户
     */
    int updateUser(UserUpdateDto dto);

    /**
     * 删除用户
     */
    int deleteUserById(@Param("id") Long id);

    /**
     * 测试特殊字段映射
     * 这里使用resultMap，其中column的大小写是随便写的
     *
     * @author liutangqi
     * @date 2026/8/14 16:06
     * @Param [dto]
     **/
    List<UserResultMapVo> getUserResultMapList();

    /**
     * 测试特殊字段映射
     * 这里使用resultType，其中映射类的属性大小写是随便写的
     *
     * @author liutangqi
     * @date 2026/8/14 16:06
     * @Param [dto]
     **/
    List<UserResultTypeVo> getUserResultTypeList();

    /**
     * 测试xml里面是下划线，实体类是下划线的映射，其中大小写是随便写的
     *
     * @author liutangqi
     * @date 2026/8/14 16:54
     * @Param []
     **/
    List<UserUnderLineVo> getUserUnderLineList();

    /**
     * 返回值是List<Map>
     *
     * @author liutangqi
     * @date 2026/8/21 15:31
     * @Param []
     **/
    List<Map<String, Object>> getUserMapList();

    /**
     * 测试返回值是List<String>
     *
     * @author liutangqi
     * @date 2026/8/21 15:31
     * @Param []
     **/
    List<String> getUserPhoneList();
}
