package com.sangsang.test;

import com.sangsang.cache.encryptor.EncryptorInstanceCache;
import com.sangsang.cache.isolation.IsolationInstanceCache;
import com.sangsang.cache.fieldparse.TableCache;
import com.sangsang.cache.transformation.TransformationInstanceCache;
import com.sangsang.config.properties.*;
import com.sangsang.domain.constants.EncryptorPatternTypeConstant;
import com.sangsang.domain.constants.TransformationPatternTypeConstant;
import com.sangsang.encryptor.db.DefaultDBFieldEncryptorPattern;
import com.sangsang.strategy.TestDataIsolationStrategy;

import java.util.*;

/**
 * 用于测试时初始化项目缓存的工具类
 *
 * @author liutangqi
 * @date 2024/4/2 15:58
 */
public class CacheTestHelper {

    /**
     * 测试的时候，根据当前测试的配置文件，进行测试
     *
     * @author liutangqi
     * @date 2025/11/14 15:00
     * @Param [fieldProperties]
     **/
    public static void testInit(FieldProperties fieldProperties) throws Exception {
        //1.将指定包路径的表结构信息缓存起来
        TableCache.init(null, fieldProperties);

        //2.初始化DB模式默认的加解密算法
        for (String scanPackage : fieldProperties.getScanEntityPackage()) {
            EncryptorInstanceCache.mockInstance(scanPackage, new DefaultDBFieldEncryptorPattern(fieldProperties));
        }

        //3.初始化语法转换器
        new TransformationInstanceCache().init(fieldProperties);

        //4.初始化数据隔离策略
        new IsolationInstanceCache().init(fieldProperties, Arrays.asList(new TestDataIsolationStrategy()));
    }


    /**
     * 构建db模式的测试配置
     *
     * @author liutangqi
     * @date 2025/11/14 15:08
     * @Param []
     **/
    public static FieldProperties buildTestProperties() {
        FieldProperties fieldProperties = new FieldProperties();
        fieldProperties.setScanEntityPackage(Arrays.asList("com.sangsang.mockentity"));
        fieldProperties.setIdentifierQuote("`");
        fieldProperties.setCaseSensitive(false);
        //1.加解密配置
        //测试时，只有db模式需要缓存策略的bean，pojo模式不需要缓存，因为那个bean是在拦截器中使用的，我们测试没有测拦截器层面，所以这里将模式设置为db模式验证即可
        EncryptorProperties encryptorProperties = new EncryptorProperties();
        encryptorProperties.setPatternType(EncryptorPatternTypeConstant.DB);
        fieldProperties.setEncryptor(encryptorProperties);

        //2.数据隔离配置
        IsolationProperties isolationProperties = new IsolationProperties();
        fieldProperties.setIsolation(isolationProperties);

        //3.数据默认值配置
        FieldDefaultProperties fieldDefaultProperties = new FieldDefaultProperties();
        fieldProperties.setFieldDefault(fieldDefaultProperties);

        //4.语法转换配置
        TransformationProperties transformationProperties = new TransformationProperties();
        transformationProperties.setForcedLowercase(false);
        transformationProperties.setPatternType(TransformationPatternTypeConstant.MYSQL_2_DM);
        fieldProperties.setTransformation(transformationProperties);

        return fieldProperties;
    }
}