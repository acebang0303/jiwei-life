package com.jiwei.merchant.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class DishPageQueryDTO implements Serializable {
    private Long merchantId;
    private Long categoryId;
    private String name;
    private Integer status;
    private Integer page = 1;
    private Integer pageSize = 10;
}
