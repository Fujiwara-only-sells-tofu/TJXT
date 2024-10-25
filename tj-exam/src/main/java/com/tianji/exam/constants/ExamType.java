package com.tianji.exam.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;
import com.tianji.common.enums.BaseEnum;
import lombok.Getter;

@Getter
public enum ExamType implements BaseEnum {
    // 1：考试 2：练习
    EXAM(1, "考试"),
    PRACTICE(2, "练习");
    @JsonValue
    @EnumValue
    int value;
    String desc;

    ExamType(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }


    public int getValue() {
        return value;
    }

    public static ExamType of(Integer value) {
        if (value == null) {
            return null;
        }
        for (ExamType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ExamType value: " + value);
    }
}


