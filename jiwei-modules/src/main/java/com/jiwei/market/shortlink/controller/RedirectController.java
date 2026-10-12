package com.jiwei.market.shortlink.controller;

import com.jiwei.market.shortlink.service.ShortLinkService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.view.RedirectView;

/**
 * 短链重定向：302 以统计点击量。
 */
@Controller
public class RedirectController {

    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @GetMapping("/s/{code}")
    public RedirectView redirect(@PathVariable String code) {
        String target = shortLinkService.resolve(code);
        RedirectView view = new RedirectView(target);
        // 不把 model/路径变量拼进目标 URL，避免 @PathVariable 泄漏到 Location 头
        view.setExposeModelAttributes(false);
        view.setExpandUriTemplateVariables(false);
        return view;
    }
}
