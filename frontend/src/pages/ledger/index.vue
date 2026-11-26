<script setup lang="ts">
import { exampleDetails, exampleStatement } from './example-ledger';
import LedgerIcon from '../../components/LedgerIcon.vue';
import LedgerPicker from '../../components/LedgerPicker.vue';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { onLoad, onShareAppMessage } from '@dcloudio/uni-app';
import { closeWechatSession, getWechatSession, openWechatSession } from '../../api/wechat-session';
import { setWechatSessionToken, wechatPreviewApiBase } from '../../api/preview-transport';
import LedgerMembersSheet from './LedgerMembersSheet.vue';
import LedgerFixedBills from './LedgerFixedBills.vue';
import LedgerAccountsSheet from './LedgerAccountsSheet.vue';
import LedgerCategoriesSheet from './LedgerCategoriesSheet.vue';
import LedgerGiftWorkspace from './LedgerGiftWorkspace.vue';
import LedgerExtensionFields from './LedgerExtensionFields.vue';
import {
  extensionDraft,
  extensionErrorMessage,
  type TransactionEntryPreset,
  extensionPayload,
  supportsTransactionExtension,
  validateExtensionDraft,
} from './transaction-extension-ui';
import type {
  TransactionExtensionChange,
  TransactionExtensionDetail,
} from '../../api/transaction-extensions';
import LedgerCategoryGrid from './LedgerCategoryGrid.vue';
import { listLedgerCategories, type LedgerCategory } from '../../api/ledger-categories';
import LedgerSearchSheet from './LedgerSearchSheet.vue';
import LedgerTransactionDetailSheet from './LedgerTransactionDetailSheet.vue';
import LedgerRecycleSheet from './LedgerRecycleSheet.vue';
import type { LedgerAccount } from '../../api/ledger-accounts';
import {
  editLedgerTransaction,
  listLedgerTransactions,
  recordLedgerTransaction,
  type LedgerTransaction,
  type LedgerStatementPage,
  type LedgerTransactionFilter,
  type LedgerTransactionDetail,
} from '../../api/ledger-transactions';
import type { LocalSession, PreviewAccount } from '../../api/local-session';
// #ifdef H5
import { closeLocalSession, getLocalSession, openLocalSession } from '../../api/local-session';
// #endif
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
  categoriesFor,
  FIRST_LEDGER_MONTH,
  categoryLabel,
  formatCents,
  formatSignedCurrency,
  hasNextStatementPage,
  sameStatementDays,
  localDay,
  parseCents,
  resolveLedgerMonth,
  shiftLedgerMonth,
  type CategoryCode,
  type TransactionDirection,
} from './transaction';

// 自定义导航避开微信状态栏和右侧胶囊。
const headerStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
const menuButton = uni.getMenuButtonBoundingClientRect();
headerStyle.paddingTop = menuButton.top + 'px';
headerStyle.paddingRight = uni.getWindowInfo().windowWidth - menuButton.left + 8 + 'px';
// #endif

const MONTH_PREFERENCE_KEY = 'xiaobu.ledger.last-month';
const today = ref(localDay());
const currentMonth = computed(() => today.value.slice(0, 7));
const month = ref(currentMonth.value);

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
const items = ref<LedgerTransaction[]>([]);
const statement = ref<LedgerStatementPage | null>(null);
const appendError = ref('');
const loading = ref(true);
const listError = ref('');
const savedMessage = ref('');
const open = ref(false);
const saving = ref(false);
const amount = ref('');
const date = ref(today.value);
const direction = ref<TransactionDirection>('EXPENSE');
const directionLabel = computed(() => (direction.value === 'INCOME' ? '收入' : '支出'));
const categorySheetOpen = ref(false);
const categoryCatalog = ref<LedgerCategory[] | null>(null);
const categoryNames = computed(() =>
  Object.fromEntries((categoryCatalog.value ?? []).map((item) => [item.code, item.name])),
);
const directionCategories = computed<[string, string][]>(() => {
  const rows = categoryCatalog.value;
  if (!rows) return categoriesFor(direction.value).map(([code, name]) => [code, name]);
  const choices = rows.filter((item) => item.direction === direction.value && item.active);
  const current = rows.find(
    (item) => item.code === category.value && item.direction === direction.value,
  );
  if (current && !current.active) choices.push(current);
  return choices.map(({ code, name }) => [code, name]);
});
const category = ref<CategoryCode | ''>('');
const extensionDrafts = ref<Record<string, TransactionExtensionDetail>>({});
const capabilitySelection = computed(
  () =>
    categoryCatalog.value?.find(
      (item) => item.code === category.value && item.direction === direction.value,
    )?.capabilities ?? [],
);
const editableCapabilities = computed(() =>
  capabilitySelection.value.filter((capability) => supportsTransactionExtension(capability.type)),
);
function updateExtensionDraft(type: string, detail: TransactionExtensionDetail) {
  extensionDrafts.value = { ...extensionDrafts.value, [type]: detail };
}
const note = ref('');
const account = ref<Pick<LedgerAccount, 'id' | 'name'> | null>(null);
const accountView = ref<'manage' | 'select' | null>(null);
const ledgerHubOpen = ref(false);
const fixedBillsOpen = ref(false);
const fixedBillRevision = ref(0);
const giftWorkspaceOpen = ref(false);
const giftRevision = ref(0);
const entryPreset = ref<TransactionEntryPreset | null>(null);

function openGiftWorkspace() {
  giftWorkspaceOpen.value = true;
}

function startPresetEntry(preset: TransactionEntryPreset) {
  if (readOnly.value || !session.value) return;
  const presetCategory = categoryCatalog.value?.find(
    (item) =>
      item.active &&
      item.direction === preset.direction &&
      item.capabilities?.some(({ type }) => type === preset.type),
  );
  if (!presetCategory) {
    uni.showToast({ title: preset.unavailableMessage, icon: 'none' });
    return;
  }
  openTransactionForm();
  entryPreset.value = preset;
  direction.value = preset.direction;
  category.value = presetCategory.code;
  extensionDrafts.value = { [preset.type]: { ...preset.detail } };
  if (preset.defaultDate) applyExtensionDateDefault(preset.defaultDate);
}

// 礼单默认日期沿用记账表单可选的起止日期。
function applyExtensionDateDefault(value: string) {
  if (value >= '1900-01-01' && value <= today.value) date.value = value;
}

// 从同账本的助手确认卡返回时刷新明细和汇总。
function assistantExpenseSaved(book: string) {
  if (ledgerId.value === book) refreshAfterTransactionChange();
}
onMounted(() => uni.$on('assistant-expense-saved', assistantExpenseSaved));
onUnmounted(() => uni.$off('assistant-expense-saved', assistantExpenseSaved));
function onFixedBillUnlinked() {
  transactionDetailId.value = '';
  fixedBillRevision.value++;
}

function refreshAfterTransactionChange() {
  giftRevision.value++;
  void refresh();
}

// 保留流水页与筛选，聊天页通过当前账本重新鉴权。
function openAssistant() {
  if (ledgerId.value)
    uni.navigateTo({
      url:
        '/pages/assistant/index?ledger=' +
        encodeURIComponent(ledgerId.value) +
        '&name=' +
        encodeURIComponent(ledgerLabel.value) +
        '&writable=' +
        (readOnly.value ? '0' : '1'),
    });
}

function selectAccount(value: LedgerAccount | null) {
  account.value = value;
  accountView.value = null;
}
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
watch(ledgerId, async (target) => {
  categoryCatalog.value = null;
  categorySheetOpen.value = false;
  if (!target) return;
  try {
    const rows = await listLedgerCategories(target);
    if (target === ledgerId.value) categoryCatalog.value = rows;
  } catch {
    // Defaults keep the quick rail available; submission is always checked on the server.
  }
});

function applyCategoryCatalog(rows: LedgerCategory[]) {
  categoryCatalog.value = rows;
  if (
    category.value !== editing.value?.transaction.categoryCode &&
    rows.some((item) => item.code === category.value && !item.active)
  ) {
    category.value = '';
  }
}

function chooseCategory(code: string) {
  category.value = code;
  fieldError.value.category = '';
  categorySheetOpen.value = false;
}

const ledgerLabel = computed(() =>
  sharedLedger.value ? `${sharedLedger.value.inviterName}的账本` : '个人账',
);
const readOnly = computed(() => sharedLedger.value?.role === 'READ_ONLY');
const memberSheetOpen = ref(false);
const ledgerNotice = ref('');
const ledgerPickerOpen = ref(false);
const shareOpen = ref(false);
const shareLink = ref('');
const shareToken = ref('');
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

function syncInviteFromUrl() {
  const token = readInviteFromUrl();
  if (token === incomingToken.value) return;
  incomingToken.value = token;
  invitePreview.value = null;
  inviteError.value = '';
  if (token) void loadInvitePreview();
}

// #ifdef H5
onMounted(() => window.addEventListener('hashchange', syncInviteFromUrl));
onUnmounted(() => window.removeEventListener('hashchange', syncInviteFromUrl));
// #endif

async function loadInvitePreview() {
  const token = incomingToken.value;
  if (!token) return;
  inviteLoading.value = true;
  inviteError.value = '';
  try {
    const result = await previewLedgerInvitation(token);
    if (token === incomingToken.value) invitePreview.value = result;
  } catch (error) {
    if (token !== incomingToken.value) return;
    invitePreview.value = null;
    inviteError.value =
      error instanceof ApiRequestError && (error.status === 404 || error.status === 400)
        ? '这条邀请已失效或已被使用。'
        : '暂时无法查看邀请，请重试。';
  } finally {
    if (token === incomingToken.value) inviteLoading.value = false;
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
    beginSignIn();
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
      beginSignIn();
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
  const unchanged = sharedLedger.value?.ledgerId === ledger?.ledgerId;
  sharedLedger.value = ledger;
  if (unchanged) return;
  ledgerNotice.value = '';
  items.value = [];
  statement.value = null;
  savedMessage.value = '';
  transactionDetailId.value = '';
  open.value = false;
  void refresh();
}

async function generateInviteLink() {
  if (!session.value || sharedLedger.value || inviteBusy.value) return;
  inviteBusy.value = true;
  shareOpen.value = true;
  shareLink.value = '';
  shareToken.value = '';
  inviteError.value = '';
  inviteFeedback.value = '';
  try {
    const invite = await createLedgerInvitation(session.value.ledgerId);
    shareToken.value = invite.token;
    // #ifdef H5
    const url = new URL(window.location.href);
    url.hash = `/?invite=${encodeURIComponent(invite.token)}`;
    shareLink.value = url.toString();
    // #endif
  } catch {
    inviteError.value = '生成邀请失败，请重试。';
  } finally {
    inviteBusy.value = false;
  }
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
  fixedBillsOpen.value = false;
  ledgerHubOpen.value = false;
  giftWorkspaceOpen.value = false;
  sharedLedger.value = null;
  joinedLedgers.value = [];
  ledgerPickerOpen.value = false;
  shareOpen.value = false;
  memberSheetOpen.value = false;
  accountView.value = null;
  transactionDetailId.value = '';
  editing.value = null;
  recycleOpen.value = false;
  filterOpen.value = false;
  filter.value = { from: '', through: '', keyword: '' };
  searchText.value = '';
  ledgerNotice.value = '';
  shareLink.value = '';
  shareToken.value = '';
  month.value = next ? readMonthPreference() : currentMonth.value;
  session.value = next;
  items.value = [];
  statement.value = null;
  listError.value = '';
  savedMessage.value = '';
  open.value = false;
  loading.value = false;
}

async function restoreSession() {
  try {
    // #ifdef H5
    resetSession(await getLocalSession());
    // #endif
    // #ifdef MP-WEIXIN
    if (wechatPreviewApiBase()) resetSession(await getWechatSession());
    // #endif
    await refresh();
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      // #ifdef MP-WEIXIN
      setWechatSessionToken('');
      // #endif
    } else {
      sessionError.value = '无法连接联调服务，请稍后重试。';
    }
  } finally {
    checkingSession.value = false;
    void loadInvitePreview();
  }
}

// 只在从「记一笔」进入时接回表单；邀请及账本入口保留各自去向。
const resumeEntry = ref(false);
function beginSignIn(forEntry = false) {
  resumeEntry.value = forEntry;
  loginOpen.value = true;
  sessionError.value = '';
}

async function signInWithWechat() {
  if (sessionBusy.value) return;
  if (!wechatPreviewApiBase()) {
    sessionError.value = '尚未配置微信联调服务。';
    return;
  }
  sessionBusy.value = true;
  sessionError.value = '';
  try {
    resetSession(await openWechatSession());
    loginOpen.value = false;
    if (incomingToken.value) await loadInvitePreview();
    else if (resumeEntry.value) openTransactionForm();
    await refresh();
  } catch {
    sessionError.value = '微信登录未完成，请重试。';
    if (incomingToken.value) inviteError.value = sessionError.value;
  } finally {
    sessionBusy.value = false;
  }
}

// #ifdef H5
async function signIn(account: PreviewAccount) {
  if (sessionBusy.value) return;
  sessionBusy.value = true;
  sessionError.value = '';
  try {
    resetSession(await openLocalSession(account));
    loginOpen.value = false;
    if (incomingToken.value) {
      await loadInvitePreview();
    } else if (resumeEntry.value) {
      openTransactionForm();
    }
    await refresh();
  } catch {
    sessionError.value = '登录失败，请重试。';
  } finally {
    sessionBusy.value = false;
  }
}

// #endif

async function signOut() {
  if (sessionBusy.value || saving.value) return;
  sessionBusy.value = true;
  sessionError.value = '';
  try {
    // #ifdef H5
    await closeLocalSession();
    // #endif
    // #ifdef MP-WEIXIN
    await closeWechatSession();
    // #endif
    resetSession(null);
  } catch {
    sessionError.value = '退出未确认，请重试。';
  } finally {
    sessionBusy.value = false;
  }
}

const filter = ref<LedgerTransactionFilter>({ from: '', through: '', keyword: '' });
const filterOpen = ref(false);
const searchText = ref('');
const transactionDetailId = ref('');
const editing = ref<LedgerTransactionDetail | null>(null);
const editStale = ref(false);
const recycleOpen = ref(false);

function startEditing(detail: LedgerTransactionDetail) {
  accountView.value = null;
  const row = detail.transaction;
  transactionDetailId.value = '';
  entryPreset.value = null;
  editing.value = detail;
  extensionDrafts.value = Object.fromEntries(
    (detail.extensions ?? [])
      .filter(({ type }) => supportsTransactionExtension(type))
      .map(({ type, detail: value }) => [type, extensionDraft(type, value)]),
  );
  editStale.value = false;
  direction.value = row.direction;
  amount.value = (row.amountCents / 100).toFixed(2);
  date.value = row.bookedOn;
  category.value = row.categoryCode;
  note.value = row.note;
  account.value = row.accountId ? { id: row.accountId, name: detail.accountName ?? '账户' } : null;
  fieldError.value = { amount: '', category: '' };
  saveError.value = '';
  open.value = true;
}

function onTransactionDeleted() {
  transactionDetailId.value = '';
  savedMessage.value = '已移到最近删除';
  fixedBillRevision.value++;
  refreshAfterTransactionChange();
}
const filtered = computed(() => Boolean(filter.value.from || filter.value.keyword));

function applyFilter(value: LedgerTransactionFilter) {
  filter.value = value;
  searchText.value = value.keyword;
  filterOpen.value = false;
  items.value = [];
  statement.value = null;
  void refresh();
}

const examples = computed(() => exampleDetails(today.value));
const selectedExample = computed(() =>
  session.value
    ? undefined
    : examples.value.find((row) => row.transaction.id === transactionDetailId.value),
);
const visibleStatement = computed(() =>
  session.value ? statement.value : exampleStatement(examples.value, month.value, filter.value),
);
const visibleItems = computed(() =>
  session.value ? items.value : (visibleStatement.value?.entries ?? []),
);
const summary = computed(() => ({
  income: visibleStatement.value?.incomeCents ?? 0,
  expense: visibleStatement.value?.expenseCents ?? 0,
  balance: visibleStatement.value?.balanceCents ?? 0,
}));
const hasMore = computed(() => hasNextStatementPage(visibleStatement.value));
const showTotals = computed(() => visibleStatement.value !== null && !listError.value);
const groups = computed(() => {
  const dates = new Map<string, LedgerTransaction[]>();
  for (const transaction of visibleItems.value) {
    const list = dates.get(transaction.bookedOn) ?? [];
    list.push(transaction);
    dates.set(transaction.bookedOn, list);
  }
  const totals = new Map(visibleStatement.value?.days.map((day) => [day.bookedOn, day]));
  return [...dates].map(([day, transactions]) => ({
    day,
    transactions,
    income: totals.get(day)?.incomeCents ?? 0,
    expense: totals.get(day)?.expenseCents ?? 0,
  }));
});

async function refresh(append = false) {
  if (append && (loading.value || !hasMore.value || !statement.value)) return;
  const targetLedgerId = ledgerId.value;
  if (!targetLedgerId) return;
  const request = ++generation;
  const selectedMonth = month.value;
  loading.value = true;
  listError.value = '';
  appendError.value = '';
  const page = append ? (statement.value?.page ?? 0) + 1 : 0;
  if (!append) {
    statement.value = null;
    items.value = [];
  }
  try {
    const result = await listLedgerTransactions(targetLedgerId, selectedMonth, filter.value, page);
    if (request === generation) {
      const existing = new Set(append ? items.value.map((item) => item.id) : []);
      if (
        append &&
        (!sameStatementDays(statement.value?.days ?? [], result.days) ||
          result.entries.some((item) => existing.has(item.id)))
      ) {
        // Offset pages can shift after another member records or deletes an entry.
        await refresh();
        return;
      }
      items.value = append ? [...items.value, ...result.entries] : result.entries;
      statement.value = result;
    }
  } catch (error) {
    if (request !== generation) return;
    if (error instanceof ApiRequestError && error.status === 403 && sharedLedger.value) {
      returnToPersonalLedger();
    } else if (error instanceof ApiRequestError && error.status === 401) {
      resetSession(null);
      sessionError.value = '当前会话已失效，请重新登录。';
    } else if (append) {
      appendError.value = '加载更多失败，点此重试';
    } else {
      listError.value = '流水加载失败，点此重试';
    }
  } finally {
    if (request === generation) loading.value = false;
  }
}

function returnToPersonalLedger() {
  joinedLedgers.value = joinedLedgers.value.filter(
    (ledger) => ledger.ledgerId !== sharedLedger.value?.ledgerId,
  );
  selectLedger(null);
  ledgerNotice.value = '你已无法访问该共享账本，已返回个人账。';
}

function selectMonth(value: string) {
  if (value !== month.value) {
    month.value = value;
    items.value = [];
    statement.value = null;
  }
  if (!session.value) return;
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
  filter.value = { ...filter.value, from: '', through: '' };
  selectMonth(target);
  void refresh();
}

function startTransaction() {
  if (checkingSession.value || sessionBusy.value || readOnly.value) return;
  if (!session.value) {
    beginSignIn(true);
    return;
  }
  openTransactionForm();
}

function openTransactionForm() {
  entryPreset.value = null;
  editing.value = null;
  extensionDrafts.value = {};
  editStale.value = false;
  today.value = localDay();
  date.value = today.value;
  amount.value = '';
  direction.value = 'EXPENSE';
  category.value = '';
  note.value = '';
  account.value = null;
  fieldError.value = { amount: '', category: '' };
  saveError.value = '';
  savedMessage.value = '';
  open.value = true;
}

function selectDirection(value: TransactionDirection) {
  if (saving.value || value === direction.value) return;
  direction.value = value;
  category.value = '';
  fieldError.value.category = '';
}

function closeTransactionForm() {
  if (!saving.value) open.value = false;
}

function confirmExtensionRemoval(): Promise<boolean> {
  return new Promise((resolve) => {
    uni.showModal({
      title: '移除业务关联？',
      content: '新分类不支持这笔流水已有的业务明细。继续将移除关联，不会删除这笔流水。',
      confirmText: '移除',
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    });
  });
}

async function submit(continueEntry = false) {
  const targetLedgerId = ledgerId.value;
  if (saving.value || !targetLedgerId || readOnly.value || editStale.value) return;
  const cents = parseCents(amount.value);
  const selected = category.value;
  fieldError.value = {
    amount: cents === null ? '请输入大于 0 的金额，最多两位小数' : '',
    category: selected ? '' : '请选择分类',
  };
  if (cents === null || !selected) return;
  const previousExtensions = editing.value?.extensions ?? [];
  if (previousExtensions.length && categoryCatalog.value === null) {
    saveError.value = '分类未加载，请退出后重新打开，原业务明细不会被移除。';
    return;
  }
  const supported = new Set(capabilitySelection.value.map(({ type }) => type));
  const remove = previousExtensions.filter(({ type }) => !supported.has(type));
  if (
    capabilitySelection.value.some(
      ({ type, required }) => required && !supportsTransactionExtension(type),
    )
  ) {
    saveError.value = '当前版本不支持这项业务，升级后再试。';
    return;
  }
  for (const capability of editableCapabilities.value) {
    const issue = validateExtensionDraft(capability.type, extensionDrafts.value[capability.type], {
      fromPreset: entryPreset.value?.type === capability.type,
    });
    if (issue) {
      saveError.value = issue;
      return;
    }
  }
  const changes: TransactionExtensionChange[] = editableCapabilities.value
    .filter(({ type, required }) => required || extensionDrafts.value[type])
    .map(({ type }) => ({
      type,
      operation: 'UPSERT',
      detail: extensionPayload(type, extensionDrafts.value[type]),
    }));
  const payload = {
    direction: direction.value,
    amountCents: cents,
    categoryCode: selected,
    bookedOn: date.value,
    note: note.value.trim(),
    accountId: account.value?.id ?? null,
    extensions: changes,
  };
  // A transport failure does not prove the server did not save this entry.
  saving.value = true;
  saveError.value = '';
  try {
    if (remove.length) {
      const confirmed = await confirmExtensionRemoval();
      if (!confirmed) return;
      changes.push(...remove.map(({ type }) => ({ type, operation: 'REMOVE' as const })));
    }
    if (editing.value) {
      const target = editing.value;
      await editLedgerTransaction(targetLedgerId, target.transaction.id, target.version, payload);
      editing.value = null;
      open.value = false;
      filter.value = { from: '', through: '', keyword: '' };
      searchText.value = '';
      selectMonth(payload.bookedOn.slice(0, 7));
      savedMessage.value = '已更新流水';
      giftRevision.value++;
      await refresh();
      return;
    }
    const stored = await recordLedgerTransaction(targetLedgerId, payload);
    today.value = localDay();
    filter.value = { from: '', through: '', keyword: '' };
    searchText.value = '';
    const nextMonth = stored.bookedOn.slice(0, 7);
    selectMonth(nextMonth);
    open.value = false;
    savedMessage.value = '已记一笔';
    giftRevision.value++;
    const continuation = continueEntry ? entryPreset.value : null;
    if (continuation) {
      startPresetEntry(continuation);
      uni.showToast({ title: continuation.continuedMessage, icon: 'none' });
    }
    await refresh();
  } catch (error) {
    if (error instanceof ApiRequestError) {
      const message = extensionErrorMessage(
        [...previousExtensions.map(({ type }) => type), ...supported],
        error.code,
      );
      if (message) {
        saveError.value = message;
        return;
      }
    }
    if (error instanceof ApiRequestError && error.code === 'XIAOBU_TRANSACTION_HAS_REFUNDS') {
      saveError.value = '已有退款：只能保留支出，原付不能低于已退，付款日期不能晚于退款。';
      return;
    }
    if (
      error instanceof ApiRequestError &&
      error.code === 'XIAOBU_TRANSACTION_CHANGED' &&
      editing.value
    ) {
      editStale.value = true;
      saveError.value = '流水已变更，本次没有覆盖。请关闭后重新打开详情核对。';
      return;
    }
    if (error instanceof ApiRequestError && error.code === 'XIAOBU_TRANSACTION_MODIFY_DENIED') {
      editStale.value = true;
      saveError.value = '你已没有修改这笔流水的权限。';
      return;
    }
    if (
      error instanceof ApiRequestError &&
      error.code === 'XIAOBU_LEDGER_READ_ONLY' &&
      sharedLedger.value
    ) {
      sharedLedger.value = { ...sharedLedger.value, role: 'READ_ONLY' };
      saveError.value = '你的权限已变为只读，这笔账未保存。';
      return;
    }
    if (error instanceof ApiRequestError && error.status === 403 && sharedLedger.value) {
      returnToPersonalLedger();
      return;
    }
    if (error instanceof ApiRequestError && error.status === 401) {
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

// #ifdef H5
void restoreSession();
// #endif
// #ifdef MP-WEIXIN
onLoad((options) => {
  const token = options?.invite;
  incomingToken.value = typeof token === 'string' && /^[A-Za-z0-9_-]{43}$/.test(token) ? token : '';
  void restoreSession();
});
onShareAppMessage(() => ({
  title: '邀请你查看小簿账本',
  path: shareToken.value
    ? `/pages/ledger/index?invite=${encodeURIComponent(shareToken.value)}`
    : '/pages/ledger/index',
}));
// #endif
const showFloatingEntry = computed(
  () =>
    !readOnly.value &&
    !(
      open.value ||
      loginOpen.value ||
      ledgerPickerOpen.value ||
      shareOpen.value ||
      incomingToken.value ||
      memberSheetOpen.value ||
      accountView.value ||
      filterOpen.value ||
      transactionDetailId.value ||
      recycleOpen.value ||
      categorySheetOpen.value ||
      giftWorkspaceOpen.value ||
      ledgerHubOpen.value
    ),
);
const entryDateLabel = computed(() => {
  const [year, month, day] = date.value.split('-').map(Number);
  const shortDate = `${month}月${day}日`;
  if (date.value === today.value) return `今天 · ${shortDate}`;
  return year === Number(today.value.slice(0, 4)) ? shortDate : `${year}年${shortDate}`;
});
</script>

<template>
  <view class="shell">
    <view v-show="!ledgerHubOpen" class="head" :style="headerStyle">
      <button v-if="session" class="book-entry" aria-label="切换账本" @click="openLedgerPicker">
        <text class="brand-dot" />{{ ledgerLabel }}<text aria-hidden="true">⌄</text>
      </button>
      <button v-else class="book-entry" :disabled="checkingSession" @click="beginSignIn()">
        <text class="brand-dot" />示例账本<text aria-hidden="true">⌄</text>
      </button>
      <button
        v-if="session && !readOnly"
        class="recycle-button"
        aria-label="最近删除"
        @click="recycleOpen = true"
      >
        <LedgerIcon class="page-icon" src="/static/assistant/history.svg" />
      </button>
    </view>

    <view v-show="!ledgerHubOpen" class="main">
      <text v-if="ledgerNotice" class="ledger-notice" role="status">{{ ledgerNotice }}</text>
      <text v-if="sessionError && !loginOpen" class="error" role="alert">{{ sessionError }}</text>
      <view v-if="checkingSession" class="empty" role="status">正在检查登录状态</view>
      <template v-else>
        <view class="summary">
          <view class="summary-line">
            <text class="summary-label">{{ filtered ? '筛选结余' : '收支结余' }}</text>
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
              <button
                class="period period-button"
                role="button"
                aria-label="选择日期"
                @click="filterOpen = true"
              >
                {{ filter.from ? '自定义日期' : monthText }}⌄
              </button>
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
              <text class="yuan">{{ summary.balance < 0 ? '−¥' : '¥' }}</text>{{
                formatCents(Math.abs(summary.balance))
              }}
            </template>
            <text v-else aria-label="暂无汇总">···</text>
          </view>
          <view class="metrics">
            <view
              ><text>收入</text
              ><text>{{ showTotals ? `¥${formatCents(summary.income)}` : '···' }}</text></view
            >
            <view
              ><text>支出</text
              ><text>{{ showTotals ? formatSignedCurrency(summary.expense) : '···' }}</text></view
            >
          </view>
        </view>
        <view class="search-toolbar">
          <LedgerIcon class="page-icon" src="/static/assistant/search.svg" />
          <input
            v-model="searchText"
            :maxlength="80"
            placeholder="搜索流水备注"
            aria-label="搜索流水备注"
            @confirm="applyFilter({ ...filter, keyword: searchText.trim() })"
          />
          <button
            class="text-button"
            @click="applyFilter({ ...filter, keyword: searchText.trim() })"
          >
            搜索
          </button>
        </view>
        <view v-if="filtered" class="filter-bar">
          <text v-if="filter.from">{{ filter.from }} 至 {{ filter.through }}</text>
          <text v-if="filter.keyword">备注：{{ filter.keyword }}</text>
          <button class="text-button" @click="applyFilter({ from: '', through: '', keyword: '' })">
            清除筛选
          </button>
        </view>
        <text v-if="savedMessage" class="sr-only" role="status">{{ savedMessage }}</text>
        <button v-if="listError" class="empty retry" @click="refresh()">{{ listError }}</button>
        <view v-else-if="session && loading && !visibleItems.length" class="empty"
          >正在加载流水</view
        >
        <view v-else-if="!visibleItems.length" class="empty">{{
          filter.keyword ? '未找到匹配流水' : '这段时间还没有流水'
        }}</view>
        <view v-for="group in groups" :key="group.day" class="day">
          <view class="day-head">
            <text>{{ showDay(group.day) }}</text>
            <view class="day-totals">
              <text v-if="group.income">收入 ¥{{ formatCents(group.income) }}</text>
              <text v-if="group.expense">支出 {{ formatSignedCurrency(group.expense) }}</text>
            </view>
          </view>
          <view class="rows">
            <view
              v-for="row in group.transactions"
              :key="row.id"
              class="row"
              role="button"
              tabindex="0"
              @click="transactionDetailId = row.originalTransactionId || row.id"
              @keydown.enter.prevent="transactionDetailId = row.originalTransactionId || row.id"
              @keydown.space.prevent="transactionDetailId = row.originalTransactionId || row.id"
            >
              <view class="row-icon" aria-hidden="true">
                <LedgerIcon
                  class="page-icon"
                  :src="`/static/ledger/${row.categoryCode.startsWith('CUS_') ? 'OTHER' : row.categoryCode}.svg`"
                />
              </view>
              <view class="row-copy">
                <text
                  >{{
                    categoryNames[row.categoryCode] ||
                    categoryLabel(row.categoryCode, row.direction)
                  }}{{ row.originalTransactionId ? '退款' : '' }}</text
                >
                <text v-if="row.note">{{ row.note }}</text>
              </view>
              <text
                class="row-amount"
                :class="{ income: row.direction === 'INCOME', refund: row.originalTransactionId }"
              >
                {{ row.direction === 'INCOME' || row.originalTransactionId ? '+' : '−' }}¥{{
                  formatCents(row.amountCents)
                }} </text
              ><text class="row-chevron" aria-hidden="true">›</text>
            </view>
          </view>
        </view>
        <text v-if="!session" class="preview-label">示例数据，仅供浏览</text>
        <button
          v-if="hasMore && !listError"
          class="text-button statement-more"
          :disabled="loading"
          @click="refresh(true)"
        >
          {{ loading ? '正在加载…' : appendError || '加载更多' }}
        </button>
      </template>
    </view>

    <view v-if="session && !incomingToken && !giftWorkspaceOpen" class="ledger-bottom-nav">
      <button
        class="text-button"
        :class="{ 'is-current': !ledgerHubOpen }"
        :aria-current="!ledgerHubOpen ? 'page' : undefined"
        @click="ledgerHubOpen = false"
      >
        <LedgerIcon class="page-icon" src="/static/assistant/orders.svg" /><text>流水</text>
      </button>
      <button class="text-button" @click="openAssistant">
        <LedgerIcon class="page-icon" src="/static/assistant/mark.svg" /><text>小簿</text>
      </button>
      <button
        class="text-button"
        :class="{ 'is-current': ledgerHubOpen }"
        :aria-current="ledgerHubOpen ? 'page' : undefined"
        @click="ledgerHubOpen = true"
      >
        <LedgerIcon class="page-icon" src="/static/assistant/book.svg" /><text>账本</text>
      </button>
    </view>
    <view v-if="ledgerHubOpen && ledgerId" class="hub-page">
      <text class="hub-title">账本管理</text
      ><view class="meta">
        <button class="hub-link" @click="openGiftWorkspace">
          <LedgerIcon class="page-icon" src="/static/ledger/vant/GIFT_RECEIVED.svg" /><view
            ><text>礼金往来</text><text class="preview-label">往来 · 礼单 · 亲友</text></view
          ><text>›</text>
        </button>
        <button class="hub-link" @click="fixedBillsOpen = true">
          <LedgerIcon class="page-icon" src="/static/ledger/vant/BILL_CALENDAR.svg" /><view
            ><text>固定账单</text><text class="preview-label">周期付款与流水关联</text></view
          ><text>›</text>
        </button>
        <button class="hub-link" @click="accountView = 'manage'">
          <LedgerIcon class="page-icon" src="/static/ledger/vant/SALARY.svg" /><view
            ><text>账户管理</text><text class="preview-label">支付账户与收支流水</text></view
          ><text>›</text>
        </button> </view
      ><view v-if="!sharedLedger" class="meta"
        ><button class="hub-link" @click="memberSheetOpen = true">
          <LedgerIcon class="page-icon" src="/static/assistant/book.svg" /><view
            ><text>成员与邀请</text></view
          ><text>›</text>
        </button></view
      >
    </view>
    <view
      v-if="readOnly && !giftWorkspaceOpen && !ledgerHubOpen"
      class="dock readonly-banner with-nav"
      >只读账本 · 不能新增或修改流水</view
    >
    <button
      v-else-if="showFloatingEntry"
      class="floating-entry"
      aria-label="记一笔"
      title="记一笔"
      :disabled="checkingSession || sessionBusy"
      @click="startTransaction"
    >
      <LedgerIcon class="page-icon" src="/static/ledger/vant/EDIT.svg" />
    </button>

    <view v-if="ledgerPickerOpen && !incomingToken" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="切换账本" aria-modal="true">
        <view class="sheet-head sheet-child">
          <text>切换账本</text>
          <button class="close" aria-label="关闭账本列表" @click="ledgerPickerOpen = false">
            ×
          </button>
        </view>
        <button class="primary secondary sheet-child" @click="selectLedger(null)">
          我的个人账
        </button>
        <button
          v-for="joined in joinedLedgers"
          :key="joined.ledgerId"
          class="primary secondary sheet-child"
          @click="selectLedger(joined)"
        >
          {{ joined.inviterName }}的账本 · {{ joined.role === 'CAN_RECORD' ? '可记账' : '只读' }}
        </button>
        <view v-if="!sharedLedger" class="book-menu sheet-child">
          <button
            @click="
              ledgerPickerOpen = false;
              memberSheetOpen = true;
            "
          >
            成员
          </button>
          <button
            @click="
              ledgerPickerOpen = false;
              generateInviteLink();
            "
          >
            邀请只读成员
          </button>
        </view>
        <view v-if="session" class="book-menu sheet-child">
          <text>{{ session.displayName }}</text>
          <button
            :disabled="sessionBusy"
            @click="
              ledgerPickerOpen = false;
              signOut();
            "
          >
            退出登录
          </button>
        </view>
        <text v-if="inviteError" class="error sheet-child" role="alert">{{ inviteError }}</text>
      </view>
    </view>

    <LedgerMembersSheet
      v-if="memberSheetOpen && session && !sharedLedger"
      :ledger-id="session.ledgerId"
      @invite="
        memberSheetOpen = false;
        generateInviteLink();
      "
      @close="memberSheetOpen = false"
    />

    <view v-if="shareOpen && !incomingToken" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="邀请只读成员" aria-modal="true">
        <view class="sheet-head sheet-child">
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
        <text class="preview-label sheet-child"
          >对方接受后，可查看此账本的所有流水，但不能修改。链接仅供本地测试，24
          小时有效，只能加入一人。</text
        >
        <view class="meta sheet-child"
          ><view class="meta-row"><text>加入后权限</text><text>只读</text></view></view
        >
        <input
          v-if="shareLink"
          class="invitation-link sheet-child"
          :value="shareLink"
          readonly
          aria-label="邀请链接"
        />
        <button v-if="shareLink" class="primary sheet-child" @click="copyInvitation">
          复制链接
        </button>
        <!-- #ifdef MP-WEIXIN -->
        <button v-if="shareToken" class="primary sheet-child" open-type="share">
          邀请微信好友
        </button>
        <!-- #endif -->
        <text v-if="inviteBusy" class="preview-label sheet-child" role="status">正在生成邀请</text>
        <text v-if="inviteFeedback" class="preview-label sheet-child" role="status">{{
          inviteFeedback
        }}</text>
        <text v-if="inviteError" class="error sheet-child" role="alert">{{ inviteError }}</text>
        <button
          v-if="inviteError"
          class="primary secondary sheet-child"
          @click="generateInviteLink"
        >
          重新生成
        </button>
      </view>
    </view>

    <view v-if="incomingToken && !loginOpen && !checkingSession" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="账本邀请" aria-modal="true">
        <view class="sheet-head sheet-child">
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
        <text v-if="inviteLoading" class="preview-label sheet-child" role="status"
          >正在查看邀请</text
        >
        <template v-else-if="invitePreview">
          <text class="guest-title sheet-child">{{ invitePreview.inviterName }}的账本</text>
          <view class="meta sheet-child"
            ><view class="meta-row"><text>加入后权限</text><text>只读</text></view></view
          >
          <text class="preview-label sheet-child"
            >接受前看不到账本内容；加入后可随时切回个人账。</text
          >
          <button class="primary sheet-child" :disabled="inviteBusy" @click="joinInvitation">
            {{ !session ? '登录后加入' : invitePreview.accepted ? '进入账本' : '接受邀请' }}
          </button>
        </template>
        <text v-if="inviteError" class="error sheet-child" role="alert">{{ inviteError }}</text>
      </view>
    </view>

    <view v-if="loginOpen" class="overlay">
      <view class="sheet login-sheet" role="dialog" aria-label="登录小生活记账簿" aria-modal="true">
        <view class="sheet-head sheet-child">
          <text>登录小生活记账簿</text>
          <button
            class="close"
            aria-label="取消登录"
            :disabled="sessionBusy"
            @click="loginOpen = false"
          >
            ×
          </button>
        </view>
        <text class="preview-label sheet-child">{{
          incomingToken
            ? '登录后，确认是否加入账本。'
            : resumeEntry
              ? '登录后，在自己的账本记一笔。'
              : '登录后，进入自己的账本。'
        }}</text>
        <!-- #ifdef MP-WEIXIN -->
        <button class="primary sheet-child" :disabled="sessionBusy" @click="signInWithWechat">
          微信登录
        </button>
        <!-- #endif -->
        <!-- #ifdef H5 -->
        <text class="preview-label sheet-child">选择测试身份。两个身份各有独立的个人账本。</text>
        <button class="primary sheet-child" :disabled="sessionBusy" @click="signIn('alpha')">
          以测试甲登录
        </button>
        <button
          class="primary secondary sheet-child"
          :disabled="sessionBusy"
          @click="signIn('beta')"
        >
          以测试乙登录
        </button>
        <!-- #endif -->
        <button
          class="primary secondary sheet-child"
          :disabled="sessionBusy"
          @click="loginOpen = false"
        >
          {{ incomingToken ? '返回邀请' : '继续浏览示例' }}
        </button>
        <text v-if="sessionError" class="error sheet-child" role="alert">{{ sessionError }}</text>
      </view>
    </view>

    <view v-if="open" class="overlay entry-overlay">
      <view
        class="sheet entry-sheet"
        role="dialog"
        :aria-label="editing ? '编辑流水' : (entryPreset?.title ?? `记一笔${directionLabel}`)"
        aria-modal="true"
      >
        <view class="sheet-head sheet-child"
          ><text>{{ editing ? '编辑流水' : (entryPreset?.title ?? '记一笔') }}</text
          ><button
            class="close"
            aria-label="关闭记账"
            :disabled="saving"
            @click="closeTransactionForm"
          >
            ×
          </button></view
        >
        <view v-if="!entryPreset" class="entry-direction sheet-child">
          <text>收支类型<text class="star">*</text></text>
          <view class="direction-tabs" role="group" aria-label="收支类型">
            <button
              class="direction-tab"
              :class="{ selected: direction === 'EXPENSE' }"
              :aria-pressed="direction === 'EXPENSE'"
              :disabled="saving"
              @click="selectDirection('EXPENSE')"
            >
              支出
            </button>
            <button
              class="direction-tab"
              :class="{ selected: direction === 'INCOME' }"
              :aria-pressed="direction === 'INCOME'"
              :disabled="saving"
              @click="selectDirection('INCOME')"
            >
              收入
            </button>
          </view>
        </view>
        <LedgerExtensionFields
          class="sheet-child"
          v-for="capability in entryPreset ? editableCapabilities : []"
          :key="capability.type"
          :ledger-id="ledgerId!"
          :quick="Boolean(entryPreset)"
          :type="capability.type"
          :detail="extensionDrafts[capability.type] ?? {}"
          :direction="direction"
          :disabled="saving"
          @change="updateExtensionDraft(capability.type, $event)"
          @date-default="applyExtensionDateDefault"
        />
        <view class="amount-field sheet-child">
          <text class="field-label">{{ directionLabel }}金额<text class="star">*</text></text>
          <view class="amount-line"
            ><text>¥</text
            ><input
              v-model="amount"
              type="digit"
              :maxlength="11"
              placeholder="0.00"
              placeholder-class="entry-amount-placeholder"
              @input="fieldError.amount = ''"
              :aria-label="`${directionLabel}金额`"
          /></view>
          <text v-if="fieldError.amount" class="error" role="alert">{{ fieldError.amount }}</text>
        </view>
        <view v-if="!entryPreset" class="entry-categories sheet-child">
          <view class="category-head">
            <text>分类<text class="star">*</text></text>
            <button class="text-button" :disabled="saving" @click="categorySheetOpen = true">
              {{ sharedLedger ? '选择分类' : '分类管理' }} ›
            </button>
          </view>
          <LedgerCategoryGrid
            :items="directionCategories"
            :selected="category"
            :disabled="saving"
            @select="chooseCategory"
          />
          <text v-if="fieldError.category" class="error" role="alert">{{
            fieldError.category
          }}</text>
        </view>
        <view class="meta sheet-child">
          <LedgerPicker
            label="选择记账日期"
            mode="date"
            :value="date"
            :end="today"
            start="1900-01-01"
            @change="date = $event.detail.value"
          >
            <view class="meta-row"
              ><text>日期<text class="star">*</text></text
              ><view class="entry-field-value">
                <text>{{ entryDateLabel }}</text>
                <text class="entry-chevron" aria-hidden="true">›</text>
              </view></view
            >
          </LedgerPicker>
          <view v-if="sharedLedger" class="meta-row"
            ><text>账本</text><text>{{ ledgerLabel }}</text></view
          >
          <view class="meta-row">
            <text>账户</text>
            <view class="clear-field">
              <button
                class="text-button account-field clear-field-value"
                :disabled="saving"
                @click="accountView = 'select'"
              >
                <text :class="{ 'entry-placeholder': !account }">{{
                  account?.name ?? '选择账户'
                }}</text>
                <text class="entry-chevron" aria-hidden="true">›</text></button
              ><button
                v-if="account"
                class="clear-control"
                aria-label="清除账户"
                @click="account = null"
              >
                ⊗
              </button></view
            >
          </view>
          <view class="meta-row"
            ><text>备注</text
            ><view class="clear-field"
              ><input
                class="clear-field-value"
                v-model="note"
                :maxlength="100"
                aria-label="备注"
              /><button v-if="note" aria-label="清除备注" class="clear-control" @click="note = ''">
                ⊗
              </button></view
            ></view
          >
        </view>
        <LedgerExtensionFields
          class="sheet-child"
          v-for="capability in entryPreset ? [] : editableCapabilities"
          :key="capability.type"
          :ledger-id="ledgerId!"
          :quick="Boolean(entryPreset)"
          :type="capability.type"
          :detail="extensionDrafts[capability.type] ?? {}"
          :direction="direction"
          :disabled="saving"
          @change="updateExtensionDraft(capability.type, $event)"
          @date-default="applyExtensionDateDefault"
        />
        <text v-if="saveError" class="error sheet-child" role="alert">{{ saveError }}</text>
        <view v-if="entryPreset" class="quick-entry-actions sheet-child">
          <button class="primary secondary save" :disabled="saving || readOnly" @click="submit()">
            保存
          </button>
          <button class="primary save" :disabled="saving || readOnly" @click="submit(true)">
            保存并记下一位
          </button>
        </view>
        <button
          v-else
          class="primary save sheet-child"
          :disabled="saving || readOnly || editStale"
          @click="submit()"
        >
          {{ saving ? '保存中…' : '保存账目' }}
        </button>
      </view>
    </view>
    <LedgerAccountsSheet
      v-show="!transactionDetailId"
      :category-names="categoryNames"
      :revision="giftRevision"
      @row="transactionDetailId = $event"
      v-if="accountView && ledgerId"
      :ledger-id="ledgerId"
      :allow-create="!sharedLedger"
      :allow-transfer="!readOnly"
      :selected-id="account?.id"
      :selectable="accountView === 'select'"
      @close="accountView = null"
      @select="selectAccount"
    />
    <LedgerSearchSheet
      v-if="filterOpen"
      :filter="filter"
      :today="today"
      @close="filterOpen = false"
      @apply="applyFilter"
    />
    <LedgerTransactionDetailSheet
      v-if="transactionDetailId && (ledgerId || selectedExample)"
      :example="selectedExample"
      :browse-only="!!selectedExample"
      :allow-bill-unlink="!readOnly"
      :key="transactionDetailId"
      :ledger-id="ledgerId"
      :transaction-id="transactionDetailId"
      :category-names="categoryNames"
      @close="transactionDetailId = ''"
      @edit="startEditing"
      @deleted="onTransactionDeleted"
      @refunded="refreshAfterTransactionChange"
      @bill-unlinked="onFixedBillUnlinked"
    />
    <LedgerRecycleSheet
      v-if="recycleOpen && ledgerId"
      :ledger-id="ledgerId"
      @close="recycleOpen = false"
      :category-names="categoryNames"
      @restored="refreshAfterTransactionChange"
    />
    <LedgerFixedBills
      v-if="fixedBillsOpen && ledgerId"
      :key="ledgerId"
      :ledger-id="ledgerId"
      :writable="!readOnly"
      :revision="fixedBillRevision"
      :covered="Boolean(transactionDetailId)"
      @close="fixedBillsOpen = false"
      @saved="refreshAfterTransactionChange"
      @detail="transactionDetailId = $event"
    />
    <LedgerGiftWorkspace
      v-if="giftWorkspaceOpen && ledgerId"
      :key="ledgerId"
      :ledger-id="ledgerId"
      :writable="!readOnly"
      :revision="giftRevision"
      :covered="Boolean(open || transactionDetailId)"
      @close="giftWorkspaceOpen = false"
      @quick="startPresetEntry"
      @detail="transactionDetailId = $event"
    />
    <LedgerCategoriesSheet
      v-if="categorySheetOpen && ledgerId"
      :ledger-id="ledgerId"
      :direction="direction"
      :selected="category"
      :initial-catalog="categoryCatalog"
      :allow-manage="!sharedLedger"
      @close="categorySheetOpen = false"
      @select="chooseCategory"
      @catalog="applyCategoryCatalog"
    />
  </view>
</template>

<style scoped src="./ledger.css"></style>
