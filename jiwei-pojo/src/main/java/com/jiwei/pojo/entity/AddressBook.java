package com.jiwei.pojo.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 收货地址表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("address_book")
public class AddressBook {
    @TableId(type = IdType.AUTO)
    private Long id;
    //用户ID
    private Long userId;
    //收货人
    private String consignee;
    //手机号
    private String phone;
    //性别
    private String sex;
    //省份编码
    private String provinceCode;
    //省份名称
    private String provinceName;
    //城市编码
    private String cityCode;
    //城市名称
    private String cityName;
    //区县编码
    private String districtCode;
    //区县名称
    private String districtName;
    //详细地址
    private String detail;
    //标签(家/公司/学校)
    private String label;
    //是否默认 1是 0否
    private Integer isDefault;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
