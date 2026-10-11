package com.jiwei.merchant.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.io.Serializable;
import java.util.List;

/**
 * 菜品新增/编辑DTO
 */
@Data
public class DishDTO implements Serializable {
    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String name;
    private String image;
    private String description;
    private BigDecimal price;
    private Integer status;
    private List<FlavorDTO> flavors;
}
