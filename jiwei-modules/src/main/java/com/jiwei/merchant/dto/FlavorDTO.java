package com.jiwei.merchant.dto;

import lombok.Data;
import java.io.Serializable;

@Data
public class FlavorDTO implements Serializable {
    private String name;
    private String value;
}
