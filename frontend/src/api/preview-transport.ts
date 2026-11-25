const TOKEN_KEY = 'xiaobu.wechat.session';
let memoryToken = '';

export function wechatPreviewApiBase() {
  // #ifdef MP-WEIXIN
  const base = String(import.meta.env.VITE_XIAOBU_WECHAT_API_BASE ?? '').trim();
  return /^https:\/\/[A-Za-z0-9.-]+(?::[0-9]+)?\/?$/.test(base) ? base.replace(/\/$/, '') : '';
  // #endif
  // #ifndef MP-WEIXIN
  return '';
  // #endif
}

export function getWechatSessionToken() {
  try {
    const stored = uni.getStorageSync(TOKEN_KEY);
    return typeof stored === 'string' && stored ? stored : memoryToken;
  } catch {
    return memoryToken;
  }
}

export function setWechatSessionToken(value: string) {
  memoryToken = value;
  try {
    if (value) uni.setStorageSync(TOKEN_KEY, value);
    else uni.removeStorageSync(TOKEN_KEY);
  } catch {
    // A preview session can continue in memory if storage is not available.
  }
}
