package com.jiwei.trade.controller.user;

import com.jiwei.common.result.Result;
import com.jiwei.trade.service.ShoppingCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * C端购物车接口
 * 主存储: Redis Hash  key=cart:{userId} field={dishId}_{flavor} 或 {setmealId}
 */
@RestController
@RequestMapping("/user/cart")
@Slf4j
@Tag(name = "购物车相关接口")
public class ShoppingCartController {

    @Autowired
    private ShoppingCartService shoppingCartService;

    /**
     * 添加购物车
     *
     * @param item
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "添加购物车")
    public Result<String> add(@RequestBody Map<String, Object> item) {
        log.info("添加购物车: {}", item);
        shoppingCartService.add(item);
        return Result.success();
    }

    /**
     * 查看购物车
     *
     * @return
     */
    @GetMapping("/list")
    @Operation(summary = "查看购物车")
    public Result<List<?>> list() {
        List<?> items = shoppingCartService.list();
        return Result.success(items);
    }

    /**
     * 修改购物车数量
     *
     * @param field
     * @param number
     * @return
     */
    @PutMapping("/{field}/number")
    @Operation(summary = "修改购物车数量")
    public Result<String> updateNumber(@PathVariable String field, @RequestBody Map<String, Integer> body) {
        Integer number = body.get("number");
        if (number == null) {
            return Result.error("number 不能为空");
        }
        shoppingCartService.updateNumber(field, number);
        return Result.success();
    }

    /**
     * 删除购物车条目
     *
     * @param field
     * @return
     */
    @DeleteMapping("/{field}")
    @Operation(summary = "删除购物车条目")
    public Result<String> delete(@PathVariable String field) {
        shoppingCartService.delete(field);
        return Result.success();
    }

    /**
     * 清空购物车
     *
     * @return
     */
    @DeleteMapping("/clear")
    @Operation(summary = "清空购物车")
    public Result<String> clear() {
        shoppingCartService.clear();
        return Result.success();
    }
}
