package com.tianji.common.autoconfigure.mybatis;


import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DynamicTableNameInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({MybatisPlusInterceptor.class, BaseMapper.class})
public class MybatisConfig {

    /**
     * @deprecated 存在任务更新数据导致updater写入0或null的问题，暂时废弃
     * @see MyBatisAutoFillInterceptor 通过自定义拦截器来实现自动注入creater和updater
     */
    // @Bean
    // @ConditionalOnMissingBean
    public BaseMetaObjectHandler baseMetaObjectHandler(){
        return new BaseMetaObjectHandler();
    }

    //配置mybatis plus的拦截器链
    //DynamicTableNameInnerInterceptor 插件不是所有服务器都用到，目前只有learning服务声明了
    //@autowired(requied = false) 注入时声明非必须
    @Bean
    @ConditionalOnMissingBean
    public MybatisPlusInterceptor mybatisPlusInterceptor(@Autowired(required = false) DynamicTableNameInnerInterceptor dynamicTableNameInnerInterceptor) {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        if(dynamicTableNameInnerInterceptor != null){
            //说明生命了该拦截器，需要加入到mybatis plus的拦截器链中  该拦截器必须在分页拦截器的前面
            interceptor.addInnerInterceptor(dynamicTableNameInnerInterceptor);
        }

        PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);
        paginationInnerInterceptor.setMaxLimit(200L);
        interceptor.addInnerInterceptor(paginationInnerInterceptor);
        interceptor.addInnerInterceptor(new MyBatisAutoFillInterceptor());
        return interceptor;
    }
}
