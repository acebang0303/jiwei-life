package com.jiwei.market.seckill.reconcile;

import com.jiwei.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 对账手动触发与报告查看。
 */
@RestController
@RequestMapping("/api/admin/reconcile")
public class ReconcileController {

    private final ReconcileService reconcileService;

    public ReconcileController(ReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    @PostMapping("/run")
    public Result<Map<String, Object>> run(@RequestParam(defaultValue = "true") boolean autoFix) {
        return Result.success(reconcileService.runOnce(autoFix));
    }

    @GetMapping("/latest")
    public Result<Map<String, Object>> latest() {
        return Result.success(reconcileService.latest());
    }
}
