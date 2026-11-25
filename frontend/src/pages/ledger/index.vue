<script setup lang="ts">
import { computed, ref } from 'vue';
import { listExpenses, saveExpense, type Expense } from '../../api/expenses';
import {
  closeLocalSession,
  getLocalSession,
  openLocalSession,
  type LocalSession,
  type PreviewAccount,
} from '../../api/local-session';
import {
  acceptLedgerInvitation,
  createLedgerInvitation,
  listJoinedLedgers,
  previewLedgerInvitation,
  type JoinedLedger,
  type LedgerInvitationPreview,
} from '../../api/ledger-invitations';
import { ApiRequestError } from '../../api/request';
import {
  CATEGORIES,
  FIRST_LEDGER_MONTH,
  categoryLabel,
  formatCents,
  localDay,
  parseCents,
  resolveLedgerMonth,
  shiftLedgerMonth,
  type CategoryCode,
} from './expense';

const MONTH_PREFERENCE_KEY = 'xiaobu.ledger.last-month';
const today = ref(localDay());
const currentMonth = computed(() => today.value.slice(0, 7));
const month = ref(readMonthPreference());

function readMonthPreference() {
  try {
    return resolveLedgerMonth(uni.getStorageSync(MONTH_PREFERENCE_KEY), currentMonth.value);
  } catch {
    return currentMonth.value;
  }
}
const monthText = computed(() => {
  const [year, value] = month.value.split('-');
  return `${year}年${Number(value)}月`;
});
const items = ref<Expense[]>([]);
const loading = ref(true);
const listError = ref('');
const savedMessage = ref('');
const open = ref(false);
const saving = ref(false);
const amount = ref('');
const date = ref(today.value);
const category = ref<CategoryCode | ''>('');
const note = ref('');
const fieldError = ref({ amount: '', category: '' });
const saveError = ref('');
const session = ref<LocalSession | null>(null);
const checkingSession = ref(true);
const loginOpen = ref(false);
const sessionBusy = ref(false);
const sessionError = ref('');
const sharedLedger = ref<JoinedLedger | null>(null);
const joinedLedgers = ref<JoinedLedger[]>([]);
const ledgerId = computed(() => sharedLedger.value?.ledgerId ?? session.value?.ledgerId);
const ledgerLabel = computed(() =>
  sharedLedger.value ? `${sharedLedger.value.inviterName}的账本` : '个人账',
);
const ledgerPickerOpen = ref(false);
const shareOpen = ref(false);
const shareLink = ref('');
const inviteBusy = ref(false);
const inviteError = ref('');
const inviteFeedback = ref('');
const incomingToken = ref(readInviteFromUrl());
const invitePreview = ref<LedgerInvitationPreview | null>(null);
const inviteLoading = ref(false);
let generation = 0;
let membershipGeneration = 0;

function readInviteFromUrl() {
  // #ifdef H5
  const fragment = window.location.hash.split('?')[1] ?? '';
  return new URLSearchParams(fragment).get('invite') ?? '';
  // #endif
  // #ifndef H5
  return '';
  // #endif
}

async function loadInvitePreview() {
  if (!incomingToken.value) return;
  inviteLoading.value = true;
  inviteError.value = '';
  try {
    invitePreview.value = await previewLedgerInvitation(incomingToken.value);
  } catch (error) {
    invitePreview.value = null;
    inviteError.value =
      error instanceof ApiRequestError && (error.status === 404 || error.status === 400)
        ? '这条邀请已失效或已被使用。'
        : '暂时无法查看邀请，请重试。';
  } finally {
    inviteLoading.value = false;
  }
}

function finishInvitation() {
  incomingToken.value = '';
  invitePreview.value = null;
  inviteError.value = '';
  // #ifdef H5
  const url = new URL(window.location.href);
  const [path, query] = url.hash.split('?');
  const params = new URLSearchParams(query);
  params.delete('invite');
  url.hash = params.size ? `${path}?${params}` : path;
  window.history.replaceState(window.history.state, '', url);
  // #endif
}

async function joinInvitation() {
  if (!session.value) {
    loginOpen.value = true;
    return;
  }
  if (inviteBusy.value || !invitePreview.value) return;
  inviteBusy.value = true;
  inviteError.value = '';
  try {
    const joined = await acceptLedgerInvitation(incomingToken.value);
    joinedLedgers.value = [
      ...joinedLedgers.value.filter((row) => row.ledgerId !== joined.ledgerId),
      joined,
    ];
    finishInvitation();
    selectLedger(joined);
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      resetSession(null);
      loginOpen.value = true;
      inviteError.value = '请重新登录后加入。';
    } else if (error instanceof ApiRequestError && error.status === 409) {
      inviteError.value = '不能接受自己发出的邀请。';
    } else {
      inviteError.value = '无法加入，这条邀请可能已失效。';
    }
  } finally {
    inviteBusy.value = false;
  }
}

async function openLedgerPicker() {
  const request = ++membershipGeneration;
  inviteError.value = '';
  ledgerPickerOpen.value = true;
  try {
    const ledgers = await listJoinedLedgers();
    if (request === membershipGeneration) joinedLedgers.value = ledgers;
  } catch {
    if (request === membershipGeneration) inviteError.value = '无法更新已加入的账本。';
  }
}

function selectLedger(ledger: JoinedLedger | null) {
  ledgerPickerOpen.value = false;
  if (sharedLedger.value?.ledgerId === ledger?.ledgerId) return;
  sharedLedger.value = ledger;
  items.value = [];
  savedMessage.value = '';
  open.value = false;
  void refresh();
}

async function generateInviteLink() {
  // #ifdef H5
  if (!session.value || sharedLedger.value || inviteBusy.value) return;
  inviteBusy.value = true;
  shareOpen.value = true;
  shareLink.value = '';
  inviteError.value = '';
  inviteFeedback.value = '';
  try {
    const invite = await createLedgerInvitation(session.value.ledgerId);
    const url = new URL(window.location.href);
    url.hash = `/?invite=${encodeURIComponent(invite.token)}`;
    shareLink.value = url.toString();
  } catch {
    inviteError.value = '生成邀请失败，请重试。';
  } finally {
    inviteBusy.value = false;
  }
  // #endif
}

async function copyInvitation() {
  // #ifdef H5
  try {
    await navigator.clipboard.writeText(shareLink.value);
    inviteFeedback.value = '链接已复制';
  } catch {
    inviteFeedback.value = '无法自动复制，请手动复制链接。';
  }
  // #endif
}

function resetSession(next: LocalSession | null) {
  ++generation;
  ++membershipGeneration;
  sharedLedger.value = null;
  joinedLedgers.value = [];
  ledgerPickerOpen.value = false;
  shareOpen.value = false;
  shareLink.value = '';
  session.value = next;
  items.value = [];
  listError.value = '';
  savedMessage.value = '';
  open.value = false;
  loading.value = false;
}

async function restoreSession() {
  // #ifdef H5
  try {
    resetSession(await getLocalSession());
    await refresh();
  } catch (error) {
    if (!(error instanceof ApiRequestError) || error.status !== 401) {
      sessionError.value = '无法连接联调服务，请稍后重试。';
    }
  } finally {
    checkingSession.value = false;
    void loadInvitePreview();
  }
  // #endif
  // #ifndef H5
  checkingSession.value = false;
  // #endif
}

async function signIn(account: PreviewAccount) {
  if (sessionBusy.value) return;
  sessionBusy.value = true;
  sessionError.value = '';
  try {
    resetSession(await openLocalSession(account));
    loginOpen.value = false;
    if (incomingToken.value) {
      await loadInvitePreview();
    } else {
      openExpenseForm();
    }
    await refresh();
  } catch {
    sessionError.value = '登录失败，请重试。';
  } finally {
    sessionBusy.value = false;
  }
}

async function signOut() {
  if (sessionBusy.value || saving.value) return;
  sessionBusy.value = true;
  sessionError.value = '';
  try {
    await closeLocalSession();
    resetSession(null);
  } catch {
    sessionError.value = '退出未确认，请重试。';
  } finally {
    sessionBusy.value = false;
  }
}

const total = computed(() =>
  items.value.reduce((value, expense) => value + expense.amountCents, 0),
);
const showTotals = computed(() => !loading.value && !listError.value);
const groups = computed(() => {
  const dates = new Map<string, Expense[]>();
  for (const expense of items.value) {
    const list = dates.get(expense.bookedOn) ?? [];
    list.push(expense);
    dates.set(expense.bookedOn, list);
  }
  return [...dates].map(([day, expenses]) => ({
    day,
    expenses,
    total: expenses.reduce((value, expense) => value + expense.amountCents, 0),
  }));
});

async function refresh() {
  const targetLedgerId = ledgerId.value;
  if (!targetLedgerId) return;
  const request = ++generation;
  const selectedMonth = month.value;
  loading.value = true;
  listError.value = '';
  try {
    const rows = await listExpenses(targetLedgerId, selectedMonth);
    if (request === generation) items.value = rows;
  } catch (error) {
    if (request !== generation) return;
    if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
      resetSession(null);
      sessionError.value = '当前会话已失效，请重新登录。';
    } else {
      listError.value = '流水加载失败，点此重试';
    }
  } finally {
    if (request === generation) loading.value = false;
  }
}

function selectMonth(value: string) {
  if (value !== month.value) {
    month.value = value;
    items.value = [];
  }
  try {
    // A preference only: the server remains the source of truth for the list.
    uni.setStorageSync(MONTH_PREFERENCE_KEY, value);
  } catch {
    // Keep month navigation available even when storage is disabled or full.
  }
}

function changeMonth(offset: -1 | 1) {
  today.value = localDay();
  const target = shiftLedgerMonth(month.value, offset);
  if (target < FIRST_LEDGER_MONTH || target > currentMonth.value) return;
  selectMonth(target);
  void refresh();
}

function newExpense() {
  if (checkingSession.value || sessionBusy.value || sharedLedger.value) return;
  if (!session.value) {
    loginOpen.value = true;
    sessionError.value = '';
    return;
  }
  openExpenseForm();
}

function openExpenseForm() {
  today.value = localDay();
  date.value = today.value;
  amount.value = '';
  category.value = '';
  note.value = '';
  fieldError.value = { amount: '', category: '' };
  saveError.value = '';
  savedMessage.value = '';
  open.value = true;
}

function closeExpense() {
  if (!saving.value) open.value = false;
}

async function submit() {
  const ledgerId = session.value?.ledgerId;
  if (saving.value || !ledgerId || sharedLedger.value) return;
  const cents = parseCents(amount.value);
  const selected = category.value;
  fieldError.value = {
    amount: cents === null ? '请输入大于 0 的金额，最多两位小数' : '',
    category: selected ? '' : '请选择分类',
  };
  if (cents === null || !selected) return;
  const payload = {
    amountCents: cents,
    categoryCode: selected,
    bookedOn: date.value,
    note: note.value.trim(),
  };
  // The current preview has no server-side replay contract.
  // #ifndef H5
  saveError.value = '此里程碑仅支持 H5 本地联调';
  return;
  // #endif
  saving.value = true;
  saveError.value = '';
  try {
    const stored = await saveExpense(ledgerId, payload);
    today.value = localDay();
    const nextMonth = stored.bookedOn.slice(0, 7);
    const current = nextMonth === month.value ? items.value : [];
    selectMonth(nextMonth);
    items.value = [stored, ...current.filter((row) => row.id !== stored.id)].sort(
      (a, b) => b.bookedOn.localeCompare(a.bookedOn) || b.id.localeCompare(a.id),
    );
    open.value = false;
    savedMessage.value = '已记一笔';
    await refresh();
  } catch (error) {
    if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
      resetSession(null);
      sessionError.value = '当前会话已失效，请重新登录。';
      return;
    }
    saveError.value =
      error instanceof ApiRequestError && error.status === 400
        ? error.message
        : '保存结果未确认。请先返回刷新流水，避免重复记账。';
  } finally {
    saving.value = false;
  }
}

const showDay = (day: string) =>
  day === today.value ? '今天' : `${Number(day.slice(5, 7))}月${Number(day.slice(8))}日`;

void restoreSession();
</script>

<template>
  <view class="shell">
    <view class="head">
      <view class="eyebrow"><text class="brand-dot" /> 我的账本</view>
      <view class="head-line">
        <text class="brand">小簿</text>
        <view v-if="session" class="session-tools">
          <text>{{ session.displayName }}</text>
          <button class="text-button" :disabled="sessionBusy" @click="signOut">退出</button>
        </view>
      </view>
    </view>

    <view class="main">
      <text v-if="sessionError && !loginOpen" class="error" role="alert">{{ sessionError }}</text>
      <view v-if="checkingSession" class="empty" role="status">正在检查登录状态</view>
      <view v-else-if="!session" class="guest">
        <text class="guest-title">你的个人账本</text>
        <text>登录后才能查看和记录个人流水。</text>
        <!-- #ifdef H5 -->
        <text class="preview-label">云电脑联调 · 仅测试身份</text>
        <!-- #endif -->
        <!-- #ifndef H5 -->
        <text class="preview-label">微信登录暂未接入</text>
        <!-- #endif -->
      </view>
      <template v-else>
        <view class="summary">
          <view class="summary-line">
            <text class="summary-label">收支结余</text>
            <view class="month-switcher">
              <button
                class="month-arrow"
                role="button"
                aria-label="上个月"
                :disabled="month <= FIRST_LEDGER_MONTH"
                :aria-disabled="month <= FIRST_LEDGER_MONTH"
                :tabindex="month <= FIRST_LEDGER_MONTH ? -1 : 0"
                @click="changeMonth(-1)"
                @keydown.enter.prevent="changeMonth(-1)"
                @keydown.space.prevent="changeMonth(-1)"
              >
                ‹
              </button>
              <text class="period" role="status">{{ monthText }}</text>
              <button
                class="month-arrow"
                role="button"
                aria-label="下个月"
                :disabled="month >= currentMonth"
                :aria-disabled="month >= currentMonth"
                :tabindex="month >= currentMonth ? -1 : 0"
                @click="changeMonth(1)"
                @keydown.enter.prevent="changeMonth(1)"
                @keydown.space.prevent="changeMonth(1)"
              >
                ›
              </button>
            </view>
          </view>
          <view class="balance">
            <template v-if="showTotals">
              <text class="yuan">{{ total ? '−¥' : '¥' }}</text>{{ formatCents(total) }}
            </template>
            <text v-else aria-label="暂无汇总">···</text>
          </view>
          <view class="metrics">
            <view
              ><text>收入</text><text>{{ showTotals ? '¥0.00' : '···' }}</text></view
            >
            <view
              ><text>支出</text
              ><text>{{ showTotals ? `¥${formatCents(total)}` : '···' }}</text></view
            >
          </view>
        </view>
        <view class="section-title">
          <text>流水</text>
          <view class="ledger-actions">
            <button class="text-button" @click="openLedgerPicker">{{ ledgerLabel }} · 切换</button>
            <button v-if="!sharedLedger" class="text-button" @click="generateInviteLink">
              邀请只读成员
            </button>
          </view>
        </view>
        <text v-if="savedMessage" class="sr-only" role="status">{{ savedMessage }}</text>
        <button v-if="listError" class="empty retry" @click="refresh">{{ listError }}</button>
        <view v-else-if="loading && !items.length" class="empty">正在加载流水</view>
        <view v-else-if="!items.length" class="empty">这段时间还没有流水</view>
        <view v-for="group in groups" :key="group.day" class="day">
          <view class="day-head"
            ><text>{{ showDay(group.day) }}</text
            ><text>支出 ¥{{ formatCents(group.total) }}</text></view
          >
          <view class="rows">
            <view v-for="row in group.expenses" :key="row.id" class="row">
              <view class="row-icon" aria-hidden="true"
                ><image :src="`/static/ledger/${row.categoryCode}.svg`"
              /></view>
              <view class="row-copy"
                ><text>{{ categoryLabel(row.categoryCode) }}</text
                ><text v-if="row.note">{{ row.note }}</text></view
              >
              <text class="row-amount">−¥{{ formatCents(row.amountCents) }}</text>
            </view>
          </view>
        </view>
      </template>
    </view>

    <!-- #ifdef H5 -->
    <view class="dock">
      <view v-if="sharedLedger" class="readonly-banner">只读账本 · 不能新增或修改流水</view>
      <button v-else class="primary" :disabled="checkingSession || sessionBusy" @click="newExpense">
        ＋ 记一笔
      </button>
    </view>

    <view v-if="ledgerPickerOpen && !incomingToken" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="切换账本" aria-modal="true">
        <view class="sheet-head">
          <text>切换账本</text>
          <button class="close" aria-label="关闭账本列表" @click="ledgerPickerOpen = false">
            ×
          </button>
        </view>
        <button class="primary secondary" @click="selectLedger(null)">我的个人账</button>
        <button
          v-for="joined in joinedLedgers"
          :key="joined.ledgerId"
          class="primary secondary"
          @click="selectLedger(joined)"
        >
          {{ joined.inviterName }}的账本 · 只读
        </button>
        <text v-if="inviteError" class="error" role="alert">{{ inviteError }}</text>
      </view>
    </view>

    <view v-if="shareOpen && !incomingToken" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="邀请只读成员" aria-modal="true">
        <view class="sheet-head">
          <text>邀请只读成员</text>
          <button
            class="close"
            aria-label="关闭邀请"
            :disabled="inviteBusy"
            @click="shareOpen = false"
          >
            ×
          </button>
        </view>
        <text class="preview-label"
          >对方接受后，可查看此账本的所有流水，但不能修改。链接仅供本地测试，24
          小时有效，只能加入一人。</text
        >
        <input
          v-if="shareLink"
          class="invitation-link"
          :value="shareLink"
          readonly
          aria-label="邀请链接"
        />
        <button v-if="shareLink" class="primary" @click="copyInvitation">复制链接</button>
        <text v-if="inviteBusy" class="preview-label" role="status">正在生成邀请</text>
        <text v-if="inviteFeedback" class="preview-label" role="status">{{ inviteFeedback }}</text>
        <text v-if="inviteError" class="error" role="alert">{{ inviteError }}</text>
        <button v-if="inviteError" class="primary secondary" @click="generateInviteLink">
          重新生成
        </button>
      </view>
    </view>

    <view v-if="incomingToken && !loginOpen && !checkingSession" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="账本邀请" aria-modal="true">
        <view class="sheet-head">
          <text>账本邀请</text>
          <button
            class="close"
            aria-label="关闭邀请"
            :disabled="inviteBusy"
            @click="finishInvitation"
          >
            ×
          </button>
        </view>
        <text v-if="inviteLoading" class="preview-label" role="status">正在查看邀请</text>
        <template v-else-if="invitePreview">
          <text class="guest-title">{{ invitePreview.inviterName }}的账本</text>
          <text class="preview-label"
            >权限：只读。接受前看不到账本内容；加入后可随时切回个人账。</text
          >
          <button class="primary" :disabled="inviteBusy" @click="joinInvitation">
            {{ !session ? '登录后加入' : invitePreview.accepted ? '进入账本' : '接受邀请' }}
          </button>
        </template>
        <text v-if="inviteError" class="error" role="alert">{{ inviteError }}</text>
      </view>
    </view>

    <view v-if="loginOpen" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="测试登录" aria-modal="true">
        <view class="sheet-head">
          <text>测试登录</text>
          <button
            class="close"
            aria-label="取消登录"
            :disabled="sessionBusy"
            @click="loginOpen = false"
          >
            ×
          </button>
        </view>
        <text class="preview-label">选择测试身份。两个身份各有独立的个人账本。</text>
        <button class="primary" :disabled="sessionBusy" @click="signIn('alpha')">
          以测试甲登录
        </button>
        <button class="primary secondary" :disabled="sessionBusy" @click="signIn('beta')">
          以测试乙登录
        </button>
        <text v-if="sessionError" class="error" role="alert">{{ sessionError }}</text>
      </view>
    </view>
    <!-- #endif -->

    <view v-if="open" class="overlay">
      <view class="sheet" role="dialog" aria-label="记一笔支出" aria-modal="true">
        <view class="sheet-head"
          ><text>记一笔</text
          ><button class="close" aria-label="关闭记账" :disabled="saving" @click="closeExpense">
            ×
          </button></view
        >
        <view class="kind">支出</view>
        <view class="amount-field">
          <text class="field-label">支出金额 <text class="star">*</text></text>
          <view class="amount-line"
            ><text>¥</text
            ><input
              v-model="amount"
              type="digit"
              :maxlength="11"
              placeholder="0.00"
              @input="fieldError.amount = ''"
              aria-label="支出金额"
          /></view>
          <text v-if="fieldError.amount" class="error" role="alert">{{ fieldError.amount }}</text>
        </view>
        <view class="category-head">分类 <text class="star">*</text></view>
        <scroll-view scroll-x class="category-rail">
          <view class="category-inner">
            <button
              v-for="item in CATEGORIES"
              :key="item[0]"
              class="category"
              :class="{ selected: category === item[0] }"
              :aria-pressed="category === item[0]"
              @click="
                category = item[0];
                fieldError.category = '';
              "
            >
              <image :src="`/static/ledger/${item[0]}.svg`" aria-hidden="true" /><text>{{
                item[1]
              }}</text>
            </button>
          </view>
        </scroll-view>
        <text v-if="fieldError.category" class="error" role="alert">{{ fieldError.category }}</text>
        <view class="meta">
          <picker
            mode="date"
            :value="date"
            :end="today"
            start="1900-01-01"
            @change="date = $event.detail.value"
          >
            <view class="meta-row"
              ><text>日期 <text class="star">*</text></text
              ><text>{{ date === today ? '今天 · ' : '' }}{{ date }} ›</text></view
            >
          </picker>
          <view class="meta-row"><text>账本</text><text>个人账</text></view>
          <view class="meta-row"
            ><text>备注</text
            ><input v-model="note" :maxlength="100" placeholder="选填" aria-label="备注"
          /></view>
        </view>
        <text v-if="saveError" class="error" role="alert">{{ saveError }}</text>
        <button class="primary save" :disabled="saving" @click="submit">
          {{ saving ? '保存中…' : '保存账目' }}
        </button>
      </view>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
