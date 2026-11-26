package com.xiaobu.identity.application;

public interface WechatIdentityGateway {

  String resolveActorId(String loginCode);
}
