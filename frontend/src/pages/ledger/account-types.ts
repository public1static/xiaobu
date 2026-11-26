// 账户用途决定用户输入的是可用资金、待还金额还是持仓市值。
export const typeGroups: Record<string, string[]> = {
  资金账户: ['微信', '支付宝', '银行卡', '现金', '电子钱包', '其他'],
  信用消费: ['京东白条', '花呗', '信用卡', '信用账户'],
  投资理财: ['基金', '股票', '其他理财'],
  预付储值: ['饭卡', '交通卡', '会员卡', '储值卡'],
};
// 图标和色彩同时用于挑选类型与识别已有账户。
export const symbols: Record<string, string> = {
  微信: 'chat-o',
  支付宝: 'balance-pay',
  银行卡: 'debit-pay',
  现金: 'gold-coin-o',
  电子钱包: 'balance-o',
  京东白条: 'bill-o',
  花呗: 'flower-o',
  信用卡: 'credit-pay',
  基金: 'chart-trending-o',
  股票: 'bar-chart-o',
  饭卡: 'shop-o',
  交通卡: 'logistics',
  会员卡: 'vip-card-o',
  储值卡: 'card',
};
export const palette: Record<string, string> = {
  微信: '#24865c',
  支付宝: '#2879bd',
  银行卡: '#525d99',
  现金: '#967228',
  京东白条: '#ac5445',
  花呗: '#387faa',
  基金: '#397f70',
  股票: '#906593',
};

// 账户用途对应的金额字段。
export function amountTitle(type: string) {
  if (typeGroups['信用消费'].includes(type)) return '当前待还';
  if (typeGroups['投资理财'].includes(type)) return '当前市值';
  return '当前余额';
}
