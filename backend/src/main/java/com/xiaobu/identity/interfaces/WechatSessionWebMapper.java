package com.xiaobu.identity.interfaces;

import com.xiaobu.identity.application.OpenWechatSessionResult;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface WechatSessionWebMapper {

  OpenWechatSessionResponse toResponse(OpenWechatSessionResult result);

  WechatSessionResponse toResponse(LedgerSessionContext session);
}
