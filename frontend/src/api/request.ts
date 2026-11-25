export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

export function request<T>(path: string, method: 'GET' | 'POST' | 'DELETE', data?: object) {
  return new Promise<T>((resolve, reject) => {
    uni.request({
      url: `/api/v1${path}`,
      method,
      data,
      header: { 'Content-Type': 'application/json' },
      success: ({ data: raw, statusCode }) => {
        if (statusCode === 204) {
          resolve(undefined as T);
          return;
        }
        const body = raw as { success?: boolean; data?: T } | undefined;
        if (statusCode >= 200 && statusCode < 300 && body?.success && body.data != null) {
          resolve(body.data);
          return;
        }
        const message =
          statusCode === 400 ? '提交内容有误，请检查后重试。' : '暂时无法完成操作，请稍后再试。';
        reject(new ApiRequestError(statusCode, message));
      },
      fail: () => reject(new ApiRequestError(0, '连接失败，请重试。')),
    });
  });
}
