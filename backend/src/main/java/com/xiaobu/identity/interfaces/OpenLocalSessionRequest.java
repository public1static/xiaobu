package com.xiaobu.identity.interfaces;

import jakarta.validation.constraints.NotBlank;

record OpenLocalSessionRequest(@NotBlank String account) {}
