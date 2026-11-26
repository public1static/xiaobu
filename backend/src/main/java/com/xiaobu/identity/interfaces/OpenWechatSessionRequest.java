package com.xiaobu.identity.interfaces;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

record OpenWechatSessionRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,128}") String code) {}
