package com.xiaobu.identity.infrastructure;

import com.xiaobu.identity.domain.WechatSession;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    componentModel = MappingConstants.ComponentModel.SPRING,
    unmappedTargetPolicy = ReportingPolicy.ERROR)
interface WechatSessionPersistenceMapper {

  WechatSession toDomain(WechatSessionEntity entity);

  WechatSessionEntity toNewEntity(WechatSession session);
}
