package com.sangsang.typehandler;

import com.sangsang.domain.exception.FieldException;
import com.sangsang.domain.strategy.encryptor.FieldEncryptorStrategy;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * pojo模式入参加密TypeHandler
 * 仅在参数写入PreparedStatement前进行加密，其它行为委托给原TypeHandler处理
 *
 * @author liutangqi
 * @date 2026/10/28 10:30
 */
public class PoJoParamEncryptorTypeHandler implements TypeHandler<Object> {

    /**
     * 当前参数原本使用的TypeHandler
     */
    private final TypeHandler<Object> delegate;

    /**
     * 当前参数使用的加解密策略
     */
    private final FieldEncryptorStrategy<String> fieldEncryptorStrategy;

    /**
     * 当前参数在sql中的下标
     */
    private final int parameterIndex;

    /**
     * 当前参数的property
     */
    private final String property;

    @SuppressWarnings("unchecked")
    public PoJoParamEncryptorTypeHandler(TypeHandler<?> delegate,
                                         FieldEncryptorStrategy<String> fieldEncryptorStrategy,
                                         int parameterIndex,
                                         String property) {
        this.delegate = (TypeHandler<Object>) delegate;
        this.fieldEncryptorStrategy = fieldEncryptorStrategy;
        this.parameterIndex = parameterIndex;
        this.property = property;
    }

    @Override
    public void setParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType) throws SQLException {
        Object jdbcParameter = parameter;
        if (parameter instanceof String) {
            try {
                jdbcParameter = fieldEncryptorStrategy.encryption((String) parameter);
            } catch (Exception e) {
                throw new FieldException(String.format("pojo模式入参加密失败 parameterIndex:%s property:%s", parameterIndex, property), e);
            }
        }

        //继续使用原TypeHandler进行参数设置，兼容业务自定义TypeHandler
        delegate.setParameter(ps, i, jdbcParameter, jdbcType);
    }

    @Override
    public Object getResult(ResultSet rs, String columnName) throws SQLException {
        return delegate.getResult(rs, columnName);
    }

    @Override
    public Object getResult(ResultSet rs, int columnIndex) throws SQLException {
        return delegate.getResult(rs, columnIndex);
    }

    @Override
    public Object getResult(CallableStatement cs, int columnIndex) throws SQLException {
        return delegate.getResult(cs, columnIndex);
    }

    public TypeHandler<Object> getDelegate() {
        return delegate;
    }
}
