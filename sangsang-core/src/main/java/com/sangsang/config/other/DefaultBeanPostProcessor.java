package com.sangsang.config.other;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * 需要优先加载的类继承这个类即可，避免某些东西还未加载完成，@PostContruct里面的逻辑就先执行了
 * 为BeanPostProcessor 提供默认的实现，避免有些较低版本的项目没有default实现导致报错
 *
 * @author liutangqi
 * @date 2025/7/15 18:22
 */
public class DefaultBeanPostProcessor implements BeanPostProcessor {
    /**
     * 实现父类default方法，避免低版本不兼容，找不到实现类
     *
     * @author liutangqi
     * @date 2025/6/13 10:36
     * @Param [bean, beanName]
     **/
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

    /**
     * 实现父类default方法，避免低版本不兼容，找不到实现类
     *
     * @author liutangqi
     * @date 2025/6/13 10:36
     * @Param [bean, beanName]
     **/
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return bean;
    }

}
