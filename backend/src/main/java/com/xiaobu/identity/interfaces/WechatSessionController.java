package com.xiaobu.identity.interfaces;

import com.aegisid.foundation.web.api.ApiResponse;
import com.xiaobu.identity.application.WechatSessionApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("wechat-preview")
@RequestMapping("/api/v1/wechat-session")
public class WechatSessionController {

  private final WechatSessionApplicationService sessions;
  private final WechatSessionWebMapper mapper;

  WechatSessionController(WechatSessionApplicationService sessions, WechatSessionWebMapper mapper) {
    this.sessions = sessions;
    this.mapper = mapper;
  }

  @PostMapping
  public ApiResponse<OpenWechatSessionResponse> openWechatSession(
      @Valid @RequestBody OpenWechatSessionRequest request) {
    return ApiResponse.success(mapper.toResponse(sessions.openWechatSession(request.code())));
  }

  @GetMapping
  public ApiResponse<WechatSessionResponse> getWechatSession(HttpServletRequest request) {
    return ApiResponse.success(mapper.toResponse(LedgerSessionContext.require(request)));
  }

  @DeleteMapping
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void closeWechatSession(HttpServletRequest request) {
    sessions.closeWechatSession(request.getHeader("Authorization"));
  }
}
