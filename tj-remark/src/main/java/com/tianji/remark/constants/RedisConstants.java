package com.tianji.remark.constants;

public interface RedisConstants {
    //接口里面写的变量默认都是常量


    /*给业务点赞的用户集合的KEY前缀，后缀是业务id*/
    String LIKE_BIZ_KEY_PREFIX = "likes:set:biz:";
    /*业务点赞数统计的KEY前缀，后缀是业务类型*/
    String LIKES_TIMES_KEY_PREFIX = "likes:times:type:";
}