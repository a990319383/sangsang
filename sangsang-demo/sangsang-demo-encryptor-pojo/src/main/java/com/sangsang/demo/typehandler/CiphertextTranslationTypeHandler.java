package com.sangsang.demo.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 密文翻译TypeHandler
 * 用于验证pojo模式入参加密后，仍然会调用业务自定义TypeHandler
 *
 * @author liutangqi
 * @date 2026/10/28 11:15
 */
public class CiphertextTranslationTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType) throws SQLException {
        //模拟业务TypeHandler对密文进行翻译，实际业务中可替换为自己的转换逻辑
        ps.setString(i, parameter.toUpperCase(Locale.ROOT));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return rs.getString(columnName);
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return rs.getString(columnIndex);
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return cs.getString(columnIndex);
    }
}
