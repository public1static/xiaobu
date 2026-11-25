<script setup>
import RecordActions from './RecordActions.vue';
import {
  defineAsyncComponent,
  provide,
  computed,
  onMounted,
  onUnmounted,
  nextTick,
  ref,
  watch,
} from 'vue';
import {
  Button as VanButton,
  Calendar as VanCalendar,
  Cell as VanCell,
  CellGroup as VanCellGroup,
  ConfigProvider as VanConfigProvider,
  showConfirmDialog,
  Empty as VanEmpty,
  Icon as VanIcon,
  Popup as VanPopup,
  Search as VanSearch,
  showToast,
} from 'vant';
import RefundForm from './RefundForm.vue';
import BackupPage from './BackupPage.vue';
import RentalsPage from './RentalsPage.vue';
import { paymentPeriod, setRentPeriod } from './fixed-bills.mjs';
import RemindersPage from './RemindersPage.vue';
import { reminders } from './reminders.mjs';
import FixedBills from './FixedBills.vue';
import AccountsPage from './AccountsPage.vue';
import { readAccounts } from './accounts.mjs';
import EntryForm from './EntryForm.vue';
// 进入礼金业务时再加载亲友目录和拼音字典。
const ContactBook = defineAsyncComponent(() => import('./ContactBook.vue'));
import {
  readGiftContacts,
  matchGiftContacts,
  normalizeContact,
  GIFT_REASONS,
  GIFT_CONTACTS_KEY,
} from './gift-options.mjs';
import AssistantVoice from './AssistantVoice.vue';
import AssistantMark from './AssistantMark.vue';
import AskPage from './AskPage.vue';
import { loadCategoryCatalog } from './CategoryCatalog.mjs';
import { answerQuestion, expenseIntent } from './ask.mjs';
import {
  cashRows,
  refunded,
  saveRefund,
  KEY,
  asDate,
  localDay,
  validRange,
  monthRange,
  amountText,
  amountSigned,
  read,
  select,
  totals,
  groups,
  save,
  remove,
  restore,
} from './ledger.mjs';

const props = defineProps({ storage: Object, bookInfo: Object, actor: String, guest: Boolean });
const emit = defineEmits(['books', 'members', 'login']);
provide('bookStorage', props.storage);
// 当前角色决定账本内的写入入口，所有人看到相同的流水署名。
const writable = computed(() => props.bookInfo.members[props.actor] !== 'reader');
provide('bookWritable', writable);
const actorName = (id) =>
  props.guest ? '示例成员' : id === props.actor ? '我' : id === 'family' ? '家人' : '账本成员';
const today = localDay(new Date());
const book = ref(read(props.storage, today));
// 账户目录由当前账本与历史流水共同生成。
const accounts = computed(() => readAccounts(book.value));
const activeNames = computed(() => accounts.value.filter((a) => a.active).map((a) => a.name));
const scope = ref('personal'),
  query = ref('');
const period = ref(monthRange(today)),
  rangeOpen = ref(false),
  draftRange = ref(null);
const detailId = ref(''),
  entry = ref(false),
  editing = ref(null),
  recycleOpen = ref(false),
  askOpen = ref(false);
// 复记草稿只带入当前账本可见的资料，独立于编辑中的流水。
const repeatDraft = ref(null);
const undo = ref('');
// 对话按成员和账本隔离；刷新后从空白会话开始，历史仍可打开。
const chatKey = 'hezhang:assistant:' + props.actor + ':' + props.bookInfo.id;
function loadChats() {
  try {
    return props.guest ? [] : JSON.parse(localStorage.getItem(chatKey) || '[]');
  } catch {
    return [];
  }
}
const sessions = ref(loadChats()),
  chatId = ref(''),
  chatDraft = ref(''),
  changingCard = ref(null);
const messages = computed(() => sessions.value.find((s) => s.id === chatId.value)?.messages || []);
watch(
  sessions,
  (value) => {
    if (!props.guest)
      try {
        localStorage.setItem(chatKey, JSON.stringify(value));
      } catch {
        showToast('对话历史未保存');
      }
  },
  { deep: true },
);
// 空白会话直到发送首条消息才进入历史。
function newChat() {
  chatId.value = '';
  chatDraft.value = '';
}
let listScroll = 0;
const scopeLabel = computed(() => props.bookInfo.name);
// 业务面板与流水详情各自保留打开位置。
const contacts = ref(readGiftContacts(props.storage, []));
// 全屏业务工作区独立滚动，首页保留原位置。
const workspace = ref(''),
  initialPerson = ref(''),
  sourceDetail = ref('');
// 从业务内的流水查看姓名时保留原业务页面和返回位置。
const nestedPerson = ref(''),
  nestedRow = ref('');
// 回到应用或跨过午夜后，用新的本地日期重新归组。
const reminderDay = ref(today),
  reminderTarget = ref(null);
const reminderNow = ref(localMinute()),
  returnTask = ref('');
// 以分钟为单位刷新本地提醒，提前建立的事项只在触发后计数。
function localMinute() {
  const d = new Date();
  return (
    localDay(d) +
    'T' +
    String(d.getHours()).padStart(2, '0') +
    ':' +
    String(d.getMinutes()).padStart(2, '0')
  );
}
const agenda = computed(() =>
  reminders(book.value, reminderDay.value, undefined, { now: reminderNow.value }).filter(
    (i) => !i.recipient || i.recipient === props.actor || i.owner === props.actor,
  ),
);
// 角标与列表置顶区共享同一组待办编号。
const badgeKeys = computed(
  () =>
    new Set(
      agenda.value
        .filter((i) => !i.later && (!i.recipient || i.recipient === props.actor))
        .map((i) => i.key),
    ),
);
const badge = computed(() => badgeKeys.value.size);
function refreshReminderDay() {
  reminderDay.value = localDay(new Date());
  reminderNow.value = localMinute();
}
let dayTimer;
onMounted(() => {
  document.addEventListener('visibilitychange', refreshReminderDay);
  dayTimer = setInterval(refreshReminderDay, 30000);
});
onUnmounted(() => {
  document.removeEventListener('visibilitychange', refreshReminderDay);
  clearInterval(dayTimer);
});
// 由提醒进入业务后，返回时回到同一本账的待办列表。
function openReminder(item, fromTask = '') {
  returnTask.value = fromTask;
  reminderTarget.value = item.target;
  workspace.value = item.target.workspace;
}
function closeReminderBusiness() {
  workspace.value = reminderTarget.value ? 'reminders' : 'hub';
  reminderTarget.value = null;
}
let homeScroll = 0;
watch(workspace, (page, previous) => {
  if (page && !previous) homeScroll = window.scrollY;
  if (!page) nextTick(() => window.scrollTo(0, homeScroll));
});
const contactIndex = computed(() => new Map(contacts.value.map((person) => [person.id, person])));
const giftContacts = computed(() => contacts.value.filter((c) => c.giftMember !== false));
const contactMatches = computed(() =>
  query.value.trim() ? matchGiftContacts(giftContacts.value, query.value) : [],
);
// 通过编号显示最新资料，未关联的旧流水保留当时填写的姓名。
const visibleRows = computed(() =>
  book.value.rows.map((row) => {
    const person = contactIndex.value.get(row.gift?.contactId);
    return person
      ? { ...row, gift: { ...row.gift, person: person.name, distinction: person.distinction } }
      : row;
  }),
);
// 恢复后同步内存目录，并清理旧账本的临时筛选和会话。
function acceptBackup(next) {
  book.value = next;
  refreshContacts();
  query.value = '';
  period.value = monthRange(today);
  undo.value = '';
  detailId.value = '';
  sessions.value = [];
  newChat();
}
// 重新读取由管理面板或记账表单写入的联系人。
function refreshContacts() {
  contacts.value = readGiftContacts(props.storage, []);
}
watch(entry, (open) => {
  if (!open) refreshContacts();
});
// 从姓名直达联系人时，记住原流水详情。
function openContacts(id = '') {
  refreshContacts();
  if (workspace.value && id && detailId.value) {
    nestedPerson.value = id;
    nestedRow.value = detailId.value;
    detailId.value = '';
    return;
  }
  initialPerson.value = id;
  sourceDetail.value = detailId.value;
  detailId.value = '';
  workspace.value = 'gift';
}
// 关闭业务页时返回实际入口。
function leaveBusiness() {
  workspace.value = initialPerson.value ? '' : 'hub';
  initialPerson.value = '';
  if (sourceDetail.value) {
    detailId.value = sourceDetail.value;
    sourceDetail.value = '';
  }
}
const selected = computed(() =>
  select(cashRows(visibleRows.value), { period: period.value, scope: scope.value }),
);
const displayed = computed(() =>
  groups(select(selected.value, { query: query.value, scope: scope.value })),
);
const summary = computed(() => totals(selected.value));
const detail = computed(() => visibleRows.value.find((r) => r.id === detailId.value));
// 当前打开的退款草稿与原消费绑定，关闭详情时一并清理。
const refundDraft = ref(null);
// 退款明细按需展开，切换原单时收起。
const refundsExpanded = ref(false);
const refundTotal = computed(() => (detail.value ? refunded(detail.value) : 0));
watch(detailId, () => {
  refundDraft.value = null;
  refundsExpanded.value = false;
});
function openRefund(item) {
  refundDraft.value = item || {
    id: crypto.randomUUID(),
    date: today,
    account: activeNames.value.includes(detail.value.account) ? detail.value.account : '',
    amount: null,
  };
}
// 只在最新账本上保存，避免另一个窗口的退款被覆盖。
function recordRefund(item) {
  const next = saveRefund(book.value, detailId.value, item);
  if (!next) {
    showToast('请核对原单金额和退款日期');
    return;
  }
  if (writeFixedBill(next)) {
    if (changingCard.value) {
      Object.assign(changingCard.value.card, item, {
        net: detail.value.amount - refunded(detail.value),
      });
      finishCard(detailId.value);
      detailId.value = '';
    }
    refundDraft.value = null;
    showToast('退款已保存');
  }
}
// 撤销一笔误记退款时保留原消费及其余退款。
async function cancelRefund() {
  const id = detailId.value,
    refundId = refundDraft.value?.id;
  try {
    await showConfirmDialog({
      title: '撤销这笔退款？',
      message: '将重新计算实付金额和到账账户余额。',
      confirmButtonText: '撤销退款',
    });
  } catch {
    return;
  }
  const rows = book.value.rows.map((row) =>
    row.id === id ? { ...row, refunds: row.refunds.filter((r) => r.id !== refundId) } : row,
  );
  if (writeFixedBill({ ...book.value, rows })) {
    refundDraft.value = null;
    showToast('退款已撤销');
  }
}
const archived = (row) =>
  book.value.giftLists?.some((l) => l.archived && l.id === row?.gift?.listId);
const isGift = (row) => ['收礼', '随礼'].includes(row?.category);
const hiddenGift = computed(
  () =>
    scope.value === 'family' &&
    detail.value?.scope === 'family' &&
    !!detail.value?.gift &&
    !detail.value.gift.shared,
);
const editingRow = computed(() =>
  hiddenGift.value && editing.value
    ? { ...editing.value, note: '', gift: undefined }
    : editing.value,
);
const minDate = asDate(
  [
    monthRange(today)[0],
    ...book.value.rows.map((r) => r.date),
    ...book.value.trash.map((r) => r.date),
  ].sort()[0],
);
minDate.setFullYear(minDate.getFullYear() - 1);
const maxDate = new Date(asDate(today).getFullYear() + 1, 11, 31);
const dayLabel = (d) =>
  d
    .slice(5)
    .replace('-', '月')
    .replace(/^0|月0/g, (k) => (k === '月0' ? '月' : '')) + '日';
const dateLabel = (d) => (d === today ? '今天' : dayLabel(d));
const rangeLabel = computed(() => {
  const [from, to] = period.value,
    [monthFrom, monthTo] = monthRange(from);
  if (from === monthFrom && to === monthTo)
    return from.slice(0, 4) + '年' + Number(from.slice(5, 7)) + '月';
  const currentYear = today.slice(0, 4),
    showYear = from.slice(0, 4) !== currentYear || to.slice(0, 4) !== currentYear;
  const datePart = (d) => (showYear ? d.slice(0, 4) + '年' : '') + dayLabel(d);
  return from === to ? datePart(from) : datePart(from) + ' – ' + datePart(to);
});
const categoryIcons = {
  餐饮: 'fire-o',
  交通: 'logistics',
  买菜: 'shopping-cart-o',
  住房: 'wap-home-o',
  随礼: 'gift-o',
  收礼: 'gift-o',
  工资: 'balance-list-o',
  购物: 'bag-o',
  日用品: 'shop-o',
  娱乐: 'music-o',
  旅行: 'guide-o',
  医疗: 'medal-o',
  教育: 'notes-o',
};
const rangeDate = computed(() => period.value.map(asDate));
function write(next, person) {
  if (!next || !writable.value) return false;
  try {
    if (person?.before !== undefined) {
      if (props.storage.getItem(GIFT_CONTACTS_KEY) !== person.before) {
        refreshContacts();
        showToast('联系人资料已更新，请重新确认');
        return false;
      }
      const values = { [KEY]: JSON.stringify(next) };
      if (person.after !== person.before) values[GIFT_CONTACTS_KEY] = person.after;
      props.storage.batch(values);
      refreshContacts();
    } else props.storage.setItem(KEY, JSON.stringify(next));
    book.value = next;
    return true;
  } catch {
    showToast('这笔未保存，请检查浏览器存储空间');
    return false;
  }
}
// 提交固定账单前核对最新本地快照，避免另一窗口已付后再写一次。
function writeFixedBill(next, person) {
  const latest = read(props.storage, today);
  if (JSON.stringify(latest) !== JSON.stringify(book.value)) {
    book.value = latest;
    showToast('账本已更新，请重新查看本期状态');
    return false;
  }
  return write(next, person);
}
// 流水详情展示其关联的账单名称及所属月份。
const fixedPeriod = computed(() => {
  if (!detail.value || detail.value.kind !== 'expense') return '';
  const match = paymentPeriod(book.value, detail.value.id);
  return match && { label: match.bill.name + ' · ' + match.month, lease: match.lease };
});
// 纠正错关联时只撤销本期付款标记，原支出流水仍保留。
async function unlinkFixedBill() {
  const rowId = detailId.value;
  try {
    await showConfirmDialog({
      title: '解除本期关联？',
      message: '该期会回到待付，原支出流水保留。',
      confirmButtonText: '解除关联',
    });
  } catch {
    return;
  }
  const bills = book.value.fixedBills.map((b) => ({
    ...b,
    paid: Object.fromEntries(Object.entries(b.paid).filter(([, id]) => id !== rowId)),
  }));
  if (writeFixedBill({ ...book.value, fixedBills: bills })) {
    detailId.value = '';
    showToast('已解除关联');
  }
}
// 礼单与流水共用本地账本，一次写入保存一个操作。
function createGiftList(list) {
  const lists = book.value.giftLists || [];
  return write({
    ...book.value,
    giftLists: lists.some((item) => item.id === list.id)
      ? lists.map((item) => (item.id === list.id ? list : item))
      : [list, ...lists],
  });
}
// 从选择器新增的项目直接写入当前账本，返回原字段时按新值选中。
// 两个记礼入口共享当前账本的事由顺序，随整本备份一起保存。
provide('reasonDirectory', {
  values: computed(() => {
    const saved = book.value.giftReasons;
    if (!saved) return GIFT_REASONS;
    const known = new Set(saved.map(normalizeContact));
    return [...saved, ...GIFT_REASONS.filter((reason) => !known.has(normalizeContact(reason)))];
  }),
  save: (giftReasons) => write({ ...book.value, giftReasons }),
});
provide('choiceDirectory', {
  book,
  write,
  accounts,
  lists: computed(() => book.value.giftLists || []),
  createGiftList,
});
// 在当前账本一次写入亲友目录和更新后的流水，保留礼单、金额与记账人。
function mergeContacts(sourceId, targetId) {
  const target = contacts.value.find((p) => p.id === targetId),
    source = contacts.value.find((p) => p.id === sourceId);
  if (!target || !source || sourceId === targetId) return false;
  const affected = (r) => r.gift?.contactId === sourceId;
  if (
    [...book.value.rows, ...book.value.trash].some(
      (r) => affected(r) && book.value.giftLists?.some((l) => l.id === r.gift.listId && l.archived),
    )
  ) {
    showToast('请先重新开放相关礼单');
    return false;
  }
  const aliases = [
    ...new Set([...(target.aliases || []), source.name, ...(source.aliases || [])]),
  ].filter((n) => n !== target.name);
  const directory = contacts.value
    .filter((p) => p.id !== sourceId)
    .map((p) => (p.id === targetId ? { ...p, aliases } : p));
  const link = (r) =>
    affected(r)
      ? {
          ...r,
          gift: {
            ...r.gift,
            person: target.name,
            distinction: target.distinction,
            contactId: targetId,
          },
        }
      : r;
  const next = {
    ...book.value,
    rows: book.value.rows.map(link),
    trash: book.value.trash.map(link),
  };
  if (next.leases)
    next.leases = next.leases.map((l) =>
      l.partyId === sourceId ? { ...l, partyId: targetId } : l,
    );
  try {
    props.storage.batch({
      [KEY]: JSON.stringify(next),
      [GIFT_CONTACTS_KEY]: JSON.stringify({ version: 1, contacts: directory }),
    });
  } catch {
    showToast('合并未保存');
    return false;
  }
  book.value = next;
  refreshContacts();
  return true;
}
// 删除档案时将当前姓名保存在原流水上，最近删除中的流水也能独立恢复。
function deletePerson(id) {
  const person = contactIndex.value.get(id);
  if (!writable.value || !person) return false;
  const detach = (r) =>
    r.gift?.contactId === id
      ? {
          ...r,
          gift: { ...r.gift, person: person.name, distinction: person.distinction, contactId: '' },
        }
      : r;
  const next = {
    ...book.value,
    rows: book.value.rows.map(detach),
    trash: book.value.trash.map(detach),
  };
  const usedInLease = book.value.leases?.some((l) => l.partyId === id);
  const directory = usedInLease
    ? contacts.value.map((c) => (c.id === id ? { ...c, giftMember: false } : c))
    : contacts.value.filter((c) => c.id !== id);
  try {
    props.storage.batch({
      [KEY]: JSON.stringify(next),
      [GIFT_CONTACTS_KEY]: JSON.stringify({ version: 1, contacts: directory }),
    });
  } catch {
    showToast('删除亲友失败');
    return false;
  }
  book.value = next;
  refreshContacts();
  return true;
}
// 从礼单登记的收送记录直接写入主流水。
function postGift(row, person) {
  const next = save(book.value, { ...row, author: props.actor }, crypto.randomUUID());
  return write(next, person);
}
// 新建空白流水时清除上次带入的草稿。
defineExpose({ openEntry });
function openEntry() {
  if (props.guest) {
    emit('login', true);
    return;
  }
  if (!writable.value) return;
  repeatDraft.value = null;
  editing.value = null;
  entry.value = true;
}
function openRange() {
  draftRange.value = [...period.value];
  rangeOpen.value = true;
}
function stageRange(dates) {
  draftRange.value = dates.map((d) => (d ? localDay(d) : null));
}
function applyRange() {
  if (!validRange(draftRange.value)) return;
  period.value = [...draftRange.value];
  rangeOpen.value = false;
}
function setMonth() {
  period.value = monthRange(today);
  rangeOpen.value = false;
}
// 使用详情中的原始流水进入编辑。
function edit() {
  repeatDraft.value = null;
  editing.value = detail.value;
  entry.value = true;
}
// 从可见流水生成今天的草稿，保存时分配新的流水编号。
function repeatEntry() {
  const source = detail.value;
  repeatDraft.value = {
    ...source,
    id: undefined,
    date: localDay(new Date()),
    account: activeNames.value.includes(source.account) ? source.account : '',
    note: hiddenGift.value ? '' : source.note,
    gift: hiddenGift.value ? undefined : source.gift,
  };
  editing.value = null;
  entry.value = true;
}
// 新流水保存后回到能显示该笔记录的流水列表。
function saveEntry(input, person, rent) {
  input.author = editing.value?.author || (editing.value ? props.bookInfo.owner : props.actor);
  const id =
    editing.value?.id ??
    changingCard.value?.id ??
    globalThis.crypto?.randomUUID?.() ??
    `pilot-${Date.now()}-${Math.random()}`;
  if (
    editing.value?.homeId &&
    input.category === editing.value.category &&
    input.kind === editing.value.kind
  ) {
    input.homeId = editing.value.homeId;
    input.contractId = editing.value.contractId;
  }
  const next = save(
    book.value,
    hiddenGift.value && editing.value
      ? { ...input, note: editing.value.note, gift: editing.value.gift }
      : input,
    id,
  );
  if (!next && editing.value?.refunds?.length) {
    showToast('请保留支出类型，原付金额须覆盖已退款，消费日期须早于或等于退款日期');
    return;
  }
  if (!next) {
    showToast('账目保存失败，请重新打开后再试');
    return;
  }
  if (rent.apply) {
    const result = setRentPeriod(next, id, rent.target);
    if (result.error) {
      rent.failed(result.error);
      return;
    }
    if (!writeFixedBill(result.book, person)) return;
  } else if (!write(next, person)) return;
  if (changingCard.value) {
    Object.assign(changingCard.value.card, input);
    finishCard(id);
  }
  const fresh = !editing.value;
  entry.value = false;
  if (fresh) {
    detailId.value = '';
    repeatDraft.value = null;
    query.value = '';
    if (input.date < period.value[0] || input.date > period.value[1])
      period.value = monthRange(input.date);
  }
  showToast(fresh ? '已记一笔' : '已保存');
}
async function requestDelete() {
  const current = detail.value;
  try {
    await showConfirmDialog({
      title: '删除这笔？',
      message: current.refunds?.length
        ? '原支出和关联退款将一起移到最近删除。'
        : isGift(current)
          ? '礼单和事由也会移到最近删除，恢复时会一起回来。'
          : '删除后可在最近删除恢复。',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      confirmButtonColor: '#a54341',
    });
  } catch {
    return;
  }
  if (write(remove(book.value, current.id))) {
    detailId.value = '';
    undo.value = current.id;
  }
}
function restoreItem(id) {
  if (archived(book.value.trash.find((r) => r.id === id))) {
    showToast('请先重新开放礼单');
    return;
  }
  if (write(restore(book.value, id))) {
    if (undo.value === id) undo.value = '';
    showToast('已恢复');
  }
}
function openAsk() {
  listScroll = window.scrollY;
  askOpen.value = true;
}
async function closeAsk() {
  askOpen.value = false;
  await nextTick();
  window.scrollTo(0, listScroll);
  document.querySelector('.dock-assistant')?.focus({ preventScroll: true });
}
// 本地识别已支持的记账表达，未知指令只答复，不推断金额并写入。
function ask(q) {
  if (!chatId.value) {
    chatId.value = crypto.randomUUID();
    sessions.value.push({ id: chatId.value, messages: [] });
  }
  const message = { id: crypto.randomUUID(), question: q },
    day = asDate(today);
  const text = q.replace(/\s+/g, '');
  const expense = expenseIntent(
    text,
    loadCategoryCatalog(props.storage).expense,
    activeNames.value,
    messages.value.at(-1)?.pendingExpense,
  );
  const refund = text.match(/^(?:刚才那笔|这笔)?退(?:款|了)?(\d+(?:\.\d{1,2})?)(?:元|块钱|块)?$/);
  if (expense) {
    if (!expense.amount || !expense.category) {
      message.pendingExpense = expense;
      message.answer = !expense.amount ? '这笔花了多少钱？' : '这笔记在哪个支出分类？';
    } else {
      day.setDate(day.getDate() - ({ 昨天: 1, 前天: 2 }[expense.day] || 0));
      message.card = {
        type: 'expense',
        category: expense.category,
        date: localDay(day),
        account: expense.account || '',
        note: expense.note || '',
        amount: expense.amount,
      };
    }
  } else if (refund) {
    const prior = messages.value.findLast((m) => m.saved && m.rowId);
    const row = book.value.rows.find((r) => r.id === prior?.rowId);
    const amount = Math.round(Number(refund[1]) * 100);
    if (!row) message.answer = '请从原支出详情记退款，或在本对话中先保存一笔支出。';
    else if (amount <= 0 || amount > row.amount - refunded(row))
      message.answer = '本笔可退 ¥' + amountText(row.amount - refunded(row)) + '，请核对退款金额。';
    else
      message.card = {
        type: 'refund',
        category: row.category,
        original: row.id,
        amount,
        net: row.amount - refunded(row) - amount,
        date: today,
        account: row.account,
      };
  } else {
    let bounds = period.value;
    if (/上个月|上月/.test(text)) {
      day.setDate(1);
      day.setMonth(day.getMonth() - 1);
      bounds = monthRange(localDay(day));
    } else if (/本月|这个月/.test(text)) bounds = monthRange(today);
    const rows = select(cashRows(visibleRows.value), { period: bounds, scope: scope.value });
    const question = text.replace(/上个月|上月|本月|这个月/g, '');
    const result = answerQuestion(question, rows);
    message.answer = result;
    if (result.includes('¥')) {
      message.answer = bounds.join(' 至 ') + '，' + result;
      const category = rows.find((r) => question.includes(r.category))?.category;
      const kind = result.includes('结余') ? null : result.includes('支出') ? 'expense' : 'income';
      message.matches = rows
        .filter((r) => (!category || r.category === category) && (!kind || r.kind === kind))
        .map((r) => ({
          id: r.originalId || r.id,
          label: r.date + ' · ' + r.category,
          value: amountSigned(r),
        }));
    }
  }
  if (message.card && (!Number.isSafeInteger(message.card.amount) || message.card.amount <= 0)) {
    delete message.card;
    message.answer = '请输入有效金额。';
  }
  messages.value.push(message);
}
// 卡片确认使用原有账本写入逻辑，使用消息编号避免重复提交。
function confirmCard(m) {
  if (!writable.value || m.saved) return;
  const c = m.card;
  if (c.type === 'refund') {
    const original = book.value.rows.find((r) => r.id === c.original);
    if (!original || original.amount - refunded(original) - c.amount !== c.net) {
      showToast('原单金额已变动，请重新核对');
      return;
    }
  }
  const next =
    c.type === 'refund'
      ? saveRefund(book.value, c.original, {
          id: m.id,
          date: c.date,
          amount: c.amount,
          account: c.account,
        })
      : save(book.value, { ...c, kind: 'expense', scope: scope.value, author: props.actor }, m.id);
  if (!next) {
    showToast('金额或原单已变更，请重新核对');
    return;
  }
  if (writeFixedBill(next)) {
    m.saved = true;
    m.rowId = c.original || m.id;
  }
}
// 卡片需要更改时复用现有表单，保存后在原会话展示结果。
async function modifyCard(m) {
  if (!writable.value || m.saved) return;
  changingCard.value = m;
  if (m.card.type === 'refund') {
    if (!book.value.rows.some((r) => r.id === m.card.original)) {
      showToast('原单已删除');
      changingCard.value = null;
      return;
    }
    detailId.value = m.card.original;
    await nextTick();
    refundDraft.value = {
      id: m.id,
      date: m.card.date,
      account: m.card.account,
      amount: m.card.amount,
    };
  } else {
    editing.value = null;
    repeatDraft.value = { ...m.card, kind: 'expense', scope: scope.value };
    entry.value = true;
  }
}
function finishCard(id) {
  if (changingCard.value) {
    changingCard.value.saved = true;
    changingCard.value.rowId = id;
    changingCard.value = null;
  }
}
watch(entry, (open) => {
  if (!open) changingCard.value = null;
});
watch(refundDraft, (v, prev) => {
  if (!v && prev && changingCard.value) {
    changingCard.value = null;
    detailId.value = '';
  }
});
function voiceAsk(q) {
  openAsk();
  ask(q);
}
</script>

<template>
  <van-config-provider
    :theme-vars="{
      primaryColor: '#176a57',
      primaryColorLight: '#eaf4ee',
      textColor: '#172b25',
      background: '#f5f7f4',
      calendarSelectedDayBackground: '#176a57',
    }"
  >
    <div v-show="!askOpen && !workspace" class="pilot-shell">
      <header class="pilot-head">
        <div class="pilot-brand">
          <button
            class="pilot-kicker book-entry"
            @click="guest ? emit('login', false) : emit('books')"
          >
            <span class="pilot-brand-dot"></span>{{ bookInfo.name }} <van-icon name="arrow-down" />
          </button>
        </div>
        <div class="pilot-head-actions">
          <van-button
            v-if="!guest"
            icon="clock-o"
            round
            plain
            class="pilot-history"
            aria-label="最近删除"
            @click="recycleOpen = true"
          /><button
            class="reminder-bell"
            :aria-label="'待办，' + badge + ' 项待处理'"
            @click="
              refreshReminderDay();
              returnTask = '';
              workspace = 'reminders';
            "
          >
            <b>待办</b><span v-if="badge">{{ badge > 99 ? '99+' : badge }}</span>
          </button>
        </div>
      </header>

      <main class="pilot-main">
        <section class="pilot-summary" aria-label="所选日期内的账目汇总">
          <button type="button" class="pilot-period" @click="openRange">
            <van-icon name="calendar-o" />{{ rangeLabel }}<van-icon name="arrow-down" />
          </button>
          <div class="pilot-balance-label">收支结余</div>
          <div class="pilot-amount">
            <span>{{ summary.balance < 0 ? '−¥' : '¥' }}</span>{{
              amountText(Math.abs(summary.balance))
            }}
          </div>
          <dl class="pilot-metrics">
            <div>
              <dt><van-icon name="down" />收入</dt>
              <dd>¥{{ amountText(summary.income) }}</dd>
            </div>
            <div>
              <dt><van-icon name="back-top" />支出</dt>
              <dd>¥{{ amountText(summary.expense) }}</dd>
            </div>
          </dl>
        </section>
        <div class="pilot-toolbar">
          <van-search v-model="query" shape="round" placeholder="搜索流水" aria-label="搜索流水" />
        </div>
        <van-cell
          v-for="person in contactMatches"
          :key="person.id"
          icon="friends-o"
          :title="person.name"
          :value="person.distinction"
          is-link
          @click="openContacts(person.id)"
        />
        <div class="pilot-list">
          <section v-for="group in displayed" :key="group.day" class="pilot-day">
            <div class="pilot-day-head">
              <h2>
                {{ dateLabel(group.day)
                }}<small v-if="group.day === today">{{ dayLabel(group.day) }}</small>
              </h2>
              <span v-if="group.totals.expense">支出 ¥{{ amountText(group.totals.expense) }}</span>
            </div>
            <button
              v-for="r in group.rows"
              :key="r.id"
              type="button"
              class="pilot-row"
              @click="detailId = r.originalId || r.id"
            >
              <span class="pilot-row-icon" :class="'kind-' + r.kind"
                ><van-icon :name="categoryIcons[r.category] ?? 'balance-o'"
              /></span>
              <span class="pilot-row-copy"
                ><strong
                  >{{ r.category }}{{ r.originalId ? '退款' : ''
                  }}<template v-if="(scope !== 'family' || r.gift?.shared) && r.gift?.person">
                    · {{ r.gift.person }}</template
                  ></strong
                ><small
                  v-if="
                    scope === 'family' && r.gift && !r.gift.shared
                      ? r.account
                      : r.note || r.gift?.event || r.account
                  "
                  >{{
                    scope === 'family' && r.gift && !r.gift.shared
                      ? r.account
                      : r.originalId
                        ? r.account
                        : r.note || r.gift?.event || r.account
                  }}</small
                ></span
              >
              <span class="pilot-row-amount" :class="'kind-' + r.kind">{{
                amountSigned(r)
              }}</span><van-icon name="arrow" class="pilot-chevron" />
            </button>
          </section>
          <van-empty
            v-if="!displayed.length"
            :description="query ? '未找到匹配流水' : '这段时间没有记录'"
          />
        </div>
        <p class="pilot-fixture-note">{{ guest ? '示例数据，仅供浏览' : '仅保存在此浏览器' }}</p>
      </main>

      <div v-if="undo" class="pilot-undo" role="status">
        <span>已删除这笔</span
        ><van-button size="small" plain @click="restoreItem(undo)">撤销</van-button
        ><van-button icon="cross" size="small" plain aria-label="关闭提示" @click="undo = ''" />
      </div>

      <van-popup
        v-model:show="rangeOpen"
        position="bottom"
        round
        class="pilot-popover"
        safe-area-inset-bottom
      >
        <div class="pilot-sheet-head">
          <h2>选择日期</h2>
          <van-button icon="cross" round plain aria-label="取消" @click="rangeOpen = false" />
        </div>
        <van-calendar
          v-if="rangeOpen"
          :poppable="false"
          type="range"
          allow-same-day
          :show-title="false"
          :show-subtitle="true"
          :show-confirm="false"
          :min-date="minDate"
          :max-date="maxDate"
          :default-date="rangeDate"
          :style="{ height: 'min(50vh,380px)' }"
          @select="stageRange"
        />
        <div class="pilot-sheet-actions">
          <van-button plain round @click="setMonth">本月</van-button
          ><van-button type="primary" round :disabled="!validRange(draftRange)" @click="applyRange"
            >应用</van-button
          >
        </div>
      </van-popup>

      <van-popup
        teleport="body"
        :show="!!detail && !entry && !refundDraft"
        position="bottom"
        round
        class="pilot-popover"
        safe-area-inset-bottom
        @update:show="!$event && (detailId = '')"
      >
        <article v-if="detail" class="pilot-detail">
          <div class="pilot-sheet-head">
            <h2>账目详情</h2>
            <van-button icon="cross" round plain aria-label="关闭详情" @click="detailId = ''" />
          </div>
          <div class="pilot-detail-head">
            <div class="detail-caption">
              <span>{{ detail.category }}{{ refundTotal ? ' · 实付' : '' }}</span>
              <button
                v-if="
                  detail.kind === 'expense' &&
                  writable &&
                  !archived(detail) &&
                  refundTotal < detail.amount
                "
                class="refund-entry"
                @click="openRefund()"
              >
                记退款
              </button>
            </div>
            <strong>{{
              refundTotal ? '¥' + amountText(detail.amount - refundTotal) : amountSigned(detail)
            }}</strong>
            <div v-if="refundTotal" class="refund-breakdown">
              <span>原付 ¥{{ amountText(detail.amount) }}</span
              ><span>已退 ¥{{ amountText(refundTotal) }}</span>
            </div>
          </div>
          <div class="detail-fields">
            <van-cell-group :border="false" class="pilot-facts">
              <van-cell v-if="fixedPeriod" title="固定账单" :value="fixedPeriod.label" /><van-cell
                v-if="fixedPeriod?.lease"
                title="租住房屋"
                :value="book.rentalHomes?.find((h) => h.id === fixedPeriod.lease.homeId)?.name"
              /><van-cell
                v-if="fixedPeriod && writable"
                title="解除本期关联"
                is-link
                @click="unlinkFixedBill"
              /><van-cell title="日期" :value="detail.date" /><van-cell
                title="记账人"
                :value="actorName(detail.author || bookInfo.owner)"
              />
              <van-cell
                v-if="detail.homeId"
                title="房屋"
                :value="book.homes?.find((h) => h.id === detail.homeId)?.name"
              /><van-cell
                v-if="detail.contractId"
                title="合同对方"
                :value="book.contracts?.find((c) => c.id === detail.contractId)?.party"
              /><van-cell title="账户" :value="detail.account || '未指定'" /><van-cell
                v-if="detail.note && !hiddenGift"
                title="备注"
                :value="detail.note"
              />
            </van-cell-group>
            <section v-if="refundTotal" class="refund-records">
              <van-cell
                class="refund-toggle"
                tag="button"
                type="button"
                title="退款明细"
                :value="detail.refunds.length + ' 笔'"
                is-link
                :arrow-direction="refundsExpanded ? 'up' : 'down'"
                :aria-expanded="refundsExpanded"
                @click="refundsExpanded = !refundsExpanded"
              />
              <div v-if="refundsExpanded" class="refund-items">
                <van-cell
                  v-for="item in [...detail.refunds]
                    .reverse()
                    .sort((a, b) => b.date.localeCompare(a.date))"
                  :key="item.id"
                  :title="item.date"
                  :label="item.account || undefined"
                  :value="'+¥' + amountText(item.amount)"
                  :is-link="writable && !archived(detail)"
                  @click="writable && !archived(detail) && openRefund(item)"
                />
              </div>
            </section>
            <section
              v-if="isGift(detail) && !hiddenGift"
              class="pilot-detail-gift"
              aria-label="礼金信息"
            >
              <van-cell
                title="礼单"
                :value="
                  book.giftLists?.find((list) => list.id === detail.gift?.listId)?.name ||
                  (detail.kind === 'income' ? '日常收礼' : '日常送礼')
                "
              /><van-cell
                v-if="detail.gift?.person"
                :is-link="contactIndex.has(detail.gift.contactId)"
                @click="
                  contactIndex.has(detail.gift.contactId) && openContacts(detail.gift.contactId)
                "
                :title="detail.kind === 'income' ? '送礼人' : '随礼对象'"
                :value="[detail.gift.person, detail.gift.distinction].filter(Boolean).join(' · ')"
              /><van-cell v-if="detail.gift?.event" title="事由" :value="detail.gift.event" />
            </section>
          </div>
          <RecordActions
            v-if="writable"
            class="detail-footer"
            :options="[
              { name: '再记一笔', visible: !isGift(detail), run: repeatEntry },
              { name: '删除这笔', danger: true, disabled: archived(detail), run: requestDelete },
            ]"
          >
            <van-button :disabled="archived(detail)" type="primary" @click="edit">编辑</van-button>
          </RecordActions>
        </article>
      </van-popup>

      <van-popup
        teleport="body"
        :show="!!refundDraft && !!detail"
        position="bottom"
        round
        class="pilot-popover"
        safe-area-inset-bottom
        @update:show="!$event && (refundDraft = null)"
      >
        <RefundForm
          v-if="refundDraft && detail"
          :row="detail"
          :draft="refundDraft"
          :today="today"
          @remove="cancelRefund"
          @save="recordRefund"
          @close="refundDraft = null"
        />
      </van-popup>
      <van-popup
        teleport="body"
        v-model:show="entry"
        position="bottom"
        round
        class="pilot-popover pilot-entry-sheet"
        :close-on-click-overlay="false"
        safe-area-inset-bottom
      >
        <EntryForm
          :book="book"
          :lists="book.giftLists || []"
          v-if="entry"
          :accounts="activeNames"
          :row="repeatDraft || editingRow"
          :repeating="!!repeatDraft"
          :private-gift="hiddenGift"
          :today="today"
          scope="personal"
          :contacts="contacts"
          @cancel="entry = false"
          @save="saveEntry"
        />
      </van-popup>

      <van-popup
        v-model:show="recycleOpen"
        position="bottom"
        round
        class="pilot-popover"
        safe-area-inset-bottom
      >
        <div class="pilot-sheet-head">
          <h2>最近删除</h2>
          <van-button
            icon="cross"
            round
            plain
            aria-label="关闭最近删除"
            @click="recycleOpen = false"
          />
        </div>
        <div class="pilot-trash">
          <van-cell-group v-if="book.trash.length"
            ><van-cell
              v-for="item in book.trash"
              :key="item.id"
              :title="item.category + ' · ' + amountSigned(item)"
              :label="item.date"
              ><template #right-icon
                ><van-button v-if="writable" round plain size="small" @click="restoreItem(item.id)"
                  >恢复</van-button
                ></template
              ></van-cell
            ></van-cell-group
          ><van-empty v-else description="暂无删除记录" />
        </div>
      </van-popup>
    </div>
    <main v-if="workspace" class="business-screen" :key="workspace">
      <div class="business-body" :class="{ 'hub-body': workspace === 'hub' }">
        <template v-if="workspace === 'hub'"
          ><header class="hub-header"><h1>账本管理</h1></header>
          <div class="hub-links">
            <button class="business-link" @click="openContacts()">
              <van-icon name="gift-o" /><span
                ><strong>礼金往来</strong><small>往来 · 礼单 · 亲友</small></span
              ><van-icon name="arrow" />
            </button>
            <button class="business-link" @click="workspace = 'rentals'">
              <van-icon name="wap-home-o" /><span
                ><strong>租房</strong><small>房屋 · 合同 · 押金</small></span
              ><van-icon name="arrow" />
            </button>
            <button class="business-link" @click="workspace = 'fixed'">
              <van-icon name="calendar-o" /><span
                ><strong>固定账单</strong><small>周期付款与流水关联</small></span
              ><van-icon name="arrow" />
            </button>
            <button class="business-link" @click="workspace = 'accounts'">
              <van-icon name="balance-o" /><span
                ><strong>账户管理</strong><small>支付账户与收支流水</small></span
              ><van-icon name="arrow" />
            </button>
          </div>
          <div v-if="!guest" class="hub-links">
            <button v-if="!guest" class="business-link" @click="emit('members')">
              <van-icon name="friends-o" /><span
                ><strong>成员与邀请</strong
                ><small>{{ Object.keys(bookInfo.members).length }} 位成员</small></span
              ><van-icon name="arrow" />
            </button>
            <button
              v-if="actor === bookInfo.owner"
              class="business-link backup-link"
              @click="workspace = 'backup'"
            >
              <van-icon name="down" /><span><strong>备份与恢复</strong></span
              ><van-icon name="arrow" />
            </button>
          </div>
        </template>
        <RemindersPage
          :badge-keys="badgeKeys"
          :initial-task="returnTask"
          :book="book"
          :actor="actor"
          :write="writeFixedBill"
          :now="reminderNow"
          v-if="workspace === 'reminders'"
          :items="agenda"
          :today="reminderDay"
          @back="workspace = ''"
          @open="openReminder"
        />
        <BackupPage
          v-if="workspace === 'backup'"
          :book="book"
          @back="workspace = 'hub'"
          @restored="acceptBackup"
        />
        <RentalsPage
          v-bind="reminderTarget?.props || {}"
          :contacts="contacts"
          v-if="workspace === 'rentals'"
          :book="book"
          :today="reminderDay"
          :actor="actor"
          :write="writeFixedBill"
          @close="closeReminderBusiness"
          @row="detailId = $event"
        />
        <FixedBills
          v-bind="reminderTarget?.props || {}"
          v-if="workspace === 'fixed'"
          :book="book"
          :today="reminderDay"
          :actor="actor"
          :write="writeFixedBill"
          @back="closeReminderBusiness"
          @row="detailId = $event"
        />
        <AccountsPage
          v-if="workspace === 'accounts'"
          :book="book"
          :accounts="accounts"
          :scope="scope"
          :write="write"
          @close="workspace = 'hub'"
          @row="detailId = $event"
        />
        <ContactBook
          :delete-person="deletePerson"
          :merge-contacts="mergeContacts"
          default-scope="personal"
          :scope="scope"
          :accounts="activeNames"
          v-if="workspace === 'gift'"
          :contacts="giftContacts"
          :rows="visibleRows"
          :initial-id="initialPerson"
          :initial-list="reminderTarget?.props?.initialList"
          :lists="book.giftLists || []"
          :create-list="createGiftList"
          :post-gift="postGift"
          @changed="refreshContacts"
          @close="reminderTarget ? closeReminderBusiness() : leaveBusiness()"
          @row="detailId = $event"
        />
      </div>
    </main>
    <section v-if="nestedPerson" class="business-screen">
      <div class="business-body">
        <ContactBook
          :delete-person="deletePerson"
          :merge-contacts="mergeContacts"
          default-scope="personal"
          :scope="scope"
          :accounts="activeNames"
          :key="nestedPerson"
          :contacts="giftContacts"
          :rows="visibleRows"
          :initial-id="nestedPerson"
          :lists="book.giftLists || []"
          :create-list="createGiftList"
          :post-gift="postGift"
          @changed="refreshContacts"
          @close="
            nestedPerson = '';
            detailId = nestedRow;
            nestedRow = '';
          "
          @row="detailId = $event"
        />
      </div>
    </section>
    <nav
      v-if="
        (!workspace || workspace === 'hub') &&
        !askOpen &&
        !entry &&
        !detail &&
        !rangeOpen &&
        !recycleOpen
      "
      aria-label="主导航"
      class="pilot-action-dock"
    >
      <button :aria-current="!workspace ? 'page' : undefined" @click="workspace = ''">
        <van-icon name="orders-o" /><span>流水</span>
      </button>
      <AssistantVoice
        shortcut
        class="dock-assistant"
        @tap="openAsk"
        @keyboard="
          openAsk();
          nextTick(() => document.querySelector('.assistant-frame input')?.focus());
        "
        @text="voiceAsk"
        ><AssistantMark /><span>小簿</span></AssistantVoice
      >
      <button :aria-current="workspace === 'hub' ? 'page' : undefined" @click="workspace = 'hub'">
        <van-icon name="notes-o" /><span>账本</span>
      </button>
    </nav>
    <van-button
      v-if="
        !workspace &&
        !askOpen &&
        !entry &&
        !detail &&
        !rangeOpen &&
        !recycleOpen &&
        (writable || guest)
      "
      type="primary"
      round
      icon="edit"
      class="floating-entry"
      aria-label="记一笔"
      title="记一笔"
      @click="openEntry"
    />
    <AskPage
      v-if="askOpen"
      v-model:draft="chatDraft"
      :guest="guest"
      :writable="writable"
      :messages="messages"
      :sessions="sessions"
      @back="closeAsk"
      @ask="ask"
      @login="emit('login', true)"
      @confirm="confirmCard"
      @modify="modifyCard"
      @row="
        (id) => (book.rows.some((r) => r.id === id) ? (detailId = id) : showToast('流水已删除'))
      "
      @new="newChat"
      @history="
        (id) => {
          chatId = id;
          chatDraft = '';
        }
      "
    />
  </van-config-provider>
</template>

<style scoped>
.book-entry {
  background: transparent;
  border: 0;
  padding: 4px 0;
  cursor: pointer;
}
.pilot-head-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.pilot-head-ask {
  min-height: 38px;
  padding: 0 11px;
  color: #176a57;
  border-color: #d7e4dc;
  background: #f7faf7;
  font-weight: 650;
  white-space: nowrap;
}
.pilot-action-dock {
  position: fixed;
  z-index: 6;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: min(100%, 560px);
  display: grid;
  grid-template-columns: 1fr minmax(110px, 1fr) 1fr;
  align-items: center;
  gap: 8px;
  padding: 7px 16px max(9px, env(safe-area-inset-bottom));
  background: #fcfdfaf7;
  border-top: 1px solid #dce6df;
  backdrop-filter: blur(12px);
}
.pilot-action-dock > button:not(.pilot-record) {
  display: grid;
  justify-items: center;
  align-content: center;
  gap: 2px;
  min-height: 44px;
  background: none;
  border: 0;
  color: #63756a;
  font: inherit;
  font-size: 12px;
  line-height: 16px;
  cursor: pointer;
}
.pilot-action-dock > button:not(.pilot-record) i {
  font-size: 21px;
}
.pilot-action-dock > button[aria-current='page'] {
  color: #12654e;
  font-weight: 700;
}
.pilot-record {
  width: 100%;
  height: 42px;
  font-size: 15px;
  font-weight: 650;
}
.business-body.hub-body {
  display: flex;
  flex-direction: column;
  min-height: 100%;
  padding: 24px 18px calc(84px + env(safe-area-inset-bottom));
}
.hub-header h1 {
  font-size: 24px;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.hub-header {
  margin: 0 0 20px;
}
.reminder-bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--green);
  background: white;
  font-size: 20px;
}
.reminder-bell span {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 17px;
  padding: 0 3px;
  border-radius: 10px;
  background: #a04635;
  color: white;
  font: 600 11px/17px sans-serif;
}
.reminder-bell b {
  font-size: 13px;
  white-space: nowrap;
}
.reminder-bell {
  width: 48px;
  border-radius: 20px;
}
.pilot-detail-head {
  position: relative;
}
.detail-caption {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  font-size: 13px;
  font-weight: 600;
  color: #dfede5;
}
.refund-entry {
  position: absolute;
  top: 12px;
  right: 16px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid #ffffff52;
  border-radius: 18px;
  background: transparent;
  color: white;
  font: inherit;
  font-weight: 500;
}
.refund-breakdown {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 16px;
  margin-top: 3px;
  font-size: 13px;
  color: #dfede5;
  font-variant-numeric: tabular-nums;
}
.detail-fields {
  margin: 0 15px;
  background: white;
  border-radius: 0 0 14px 14px;
  overflow: hidden;
}
.pilot-detail .detail-fields :is(.pilot-facts, .refund-records, .pilot-detail-gift) {
  margin: 0;
  border: 0;
  border-radius: 0;
}
.detail-fields .van-cell {
  padding: 10px 14px;
  min-height: 44px;
  border: 0;
  border-top: 1px solid var(--line);
  text-align: start;
}
.detail-fields .van-cell::after {
  display: none;
}
.refund-toggle {
  appearance: none;
  font-family: inherit;
}
.refund-toggle:focus-visible {
  outline: 2px solid var(--green);
  outline-offset: -2px;
}
.refund-items {
  margin-inline-start: 16px;
}
.refund-items .van-cell::after {
  left: 14px;
  right: 14px;
}

.pilot-main {
  padding-bottom: 150px;
}
</style>

<style>
.business-screen {
  position: fixed;
  inset: 0;
  z-index: 5;
  overflow: auto;
  overscroll-behavior: contain;
  background: #f5f7f4;
  color: #172b25;
}
.business-body {
  max-width: 560px;
  min-height: 100%;
  box-sizing: border-box;
  margin: auto;
  padding: 12px 16px max(28px, env(safe-area-inset-bottom));
  background: var(--paper);
}
.business-link {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  border: 1px solid #dce6df;
  background: white;
  color: #19392c;
  padding: 17px 14px;
  border-radius: 14px;
  margin-bottom: 12px;
  text-align: left;
  font: inherit;
}
.business-link > .van-icon:first-child {
  font-size: 25px;
  background: #edf4ef;
  border-radius: 12px;
  padding: 10px;
}
.business-link span {
  flex: 1;
}
.business-link strong,
.business-link small {
  display: block;
}
.business-link small {
  margin-top: 4px;
  color: #617369;
}
.reminder-bell {
  position: relative;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--green);
  background: white;
  font-size: 20px;
}
.reminder-bell span {
  position: absolute;
  top: -4px;
  right: -4px;
  min-width: 17px;
  padding: 0 3px;
  border-radius: 10px;
  background: #a04635;
  color: white;
  font: 600 11px/17px sans-serif;
}
.reminder-bell b {
  font-size: 13px;
  white-space: nowrap;
}
.reminder-bell {
  width: 48px;
  border-radius: 20px;
}
.van-button.floating-entry {
  position: fixed;
  right: max(16px, calc((100vw - 560px) / 2 + 16px));
  bottom: calc(78px + env(safe-area-inset-bottom));
  z-index: 5;
  width: 48px;
  height: 48px;
  padding: 0;
  border-radius: 50%;
  font-size: 22px;
  box-shadow: 0 4px 12px #164b3a22;
}
.pilot-action-dock > button,
.pilot-action-dock > button.voice-shortcut.dock-assistant {
  display: grid;
  grid-template-rows: 26px 16px;
  justify-items: center;
  align-items: center;
  align-content: center;
  gap: 2px;
  min-height: 44px;
  line-height: 16px;
}
.pilot-action-dock > button.voice-shortcut.dock-assistant {
  background: transparent;
  color: #17634e;
  font-size: 12px;
  font-weight: 600;
}
.dock-assistant .assistant-mark {
  width: 28px;
  height: 28px;
}
.pilot-main {
  padding-bottom: 150px;
}
</style>
