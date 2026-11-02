package com.sangsang.demo.domain.vo;

import com.sangsang.demo.strategies.CollisionFirstFieldEncryptorStrategy;
import com.sangsang.demo.strategies.CollisionSecondFieldEncryptorStrategy;
import com.sangsang.domain.annos.encryptor.PoJoResultEncryptor;
import lombok.Data;

/**
 * 多策略处理相同密文的碰撞复现结果
 *
 * @author liutangqi
 * @date 2026/11/2
 */
@Data
public class FieldEncryptorStrategyCollisionVo {

    @PoJoResultEncryptor(CollisionFirstFieldEncryptorStrategy.class)
    private String firstStrategyValue;

    @PoJoResultEncryptor(CollisionSecondFieldEncryptorStrategy.class)
    private String secondStrategyValue;
}
