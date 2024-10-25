package com.tianji.learning.service;

import com.tianji.learning.domain.vo.SignResultVO;

import java.util.List;

public interface ISignRecordService {

    /*
    * 用户签到
    * */
    SignResultVO addSignRecords();

    /*
    * 查询签到记录
    * */
    Byte[] querySignRecords();
}
