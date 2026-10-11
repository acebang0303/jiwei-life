package com.jiwei.market.couponshare.service;

import com.jiwei.pojo.entity.CouponTemplate;

public interface CouponShareService {
    /** 生成分享短链短码（longUrl = {base-url}/coupon-share?templateId={id}） */
    String createShareLink(Long templateId);
    /** 查券信息供 H5 公开展示 */
    CouponTemplate getShareInfo(Long templateId);
    /** 生成开发占位小程序码（带 templateId 文字的 SVG dataURL） */
    String generateMiniCode(Long templateId);
}
