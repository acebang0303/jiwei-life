package com.jiwei.market.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class SeckillBuyDTO implements Serializable {
    //秒杀活动ID
    private Long activityId;
    //菜品ID
    private Long dishId;
}
