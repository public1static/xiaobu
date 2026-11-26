import type { LocalSession } from './local-session';
import { getWechatSessionToken, setWechatSessionToken } from './preview-transport';
import { request, ApiRequestError } from './request';

interface OpenWechatSession extends LocalSession {
  token: string;
}

function getLoginCode() {
  return new Promise<string>((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: (result) => {
        if (result.code) resolve(result.code);
        else reject(new Error('微信未返回登录凭证。'));
      },
      fail: () => reject(new Error('微信登录未完成。')),
    });
  });
}

export async function openWechatSession(): Promise<LocalSession> {
  const code = await getLoginCode();
  const { token, ...info } = await request<OpenWechatSession>('/wechat-session', 'POST', { code });
  setWechatSessionToken(token);
  return info;
}

export async function getWechatSession(): Promise<LocalSession> {
  if (!getWechatSessionToken()) throw new ApiRequestError(401, '请先登录。');
  return request<LocalSession>('/wechat-session', 'GET');
}

export async function closeWechatSession() {
  await request<void>('/wechat-session', 'DELETE');
  setWechatSessionToken('');
}
