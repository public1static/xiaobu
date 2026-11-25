import { getWechatSessionToken, wechatPreviewApiBase } from './preview-transport';

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    readonly code?: string,
  ) {
    super(message);
  }
}

export function request<T>(path: string, method: 'GET' | 'POST' | 'PUT' | 'DELETE', data?: object) {
  let base = '';
  const header: Record<string, string> = { 'Content-Type': 'application/json' };
  // #ifdef MP-WEIXIN
  base = wechatPreviewApiBase();
  if (!base) return Promise.reject(new ApiRequestError(0, '尚未配置微信联调服务。'));
  const token = getWechatSessionToken();
  if (token) header.Authorization = `Bearer ${token}`;
  // #endif
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: `${base}/api/v1${path}`,
      method,
      data,
      header,
      success: ({ data: raw, statusCode }) => {
        if (statusCode === 204) {
          resolve(undefined as T);
          return;
        }
        const body = raw as { success?: boolean; data?: T; code?: string } | undefined;
        if (statusCode >= 200 && statusCode < 300 && body?.success && body.data != null) {
          resolve(body.data);
          return;
        }
        const message =
          statusCode === 400 ? '提交内容有误，请检查后重试。' : '暂时无法完成操作，请稍后再试。';
        reject(new ApiRequestError(statusCode, message, body?.code));
      },
      fail: () => reject(new ApiRequestError(0, '连接失败，请重试。')),
    });
  });
}
