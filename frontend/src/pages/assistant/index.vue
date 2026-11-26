<script setup lang="ts">
import { computed, ref } from 'vue';
import { onLoad, onShow, onHide, onUnload } from '@dcloudio/uni-app';
import { assistantApi, chatRequestId, type Conversation, type Message } from '../../api/assistant';
import { ApiRequestError } from '../../api/request';
import type { LedgerStatementPage } from '../../api/ledger-transactions';
import ExpenseDraftCard from './ExpenseDraftCard.vue';
import { expenseIntent } from './expense-intent.mjs';
import { listLedgerAccounts, type LedgerAccount } from '../../api/ledger-accounts';
import { listLedgerCategories, type LedgerCategory } from '../../api/ledger-categories';
import type { ExpenseDraft, DraftFields } from '../../api/assistant';
import { formatCents } from '../ledger/transaction';
import LedgerTransactionDetailSheet from '../ledger/LedgerTransactionDetailSheet.vue';

const ledger = ref(''),
  name = ref(''),
  chat = ref<Conversation | null>(null),
  turns = ref<Message[]>([]);
const history = ref<Conversation[]>([]),
  showHistory = ref(false),
  moreHistory = ref(false),
  older = ref(false);
const input = ref(''),
  error = ref(''),
  busy = ref(false),
  denied = ref(false),
  reference = ref<number>();
const source = ref(''),
  result = ref<LedgerStatementPage | null>(null),
  selected = ref<number>(),
  anchor = ref('');
const api = computed(() => assistantApi(ledger.value));
const writable = ref(false),
  draft = ref<ExpenseDraft | null>(null),
  uncertain = ref(false);
const accounts = ref<LedgerAccount[]>([]),
  categories = ref<LedgerCategory[]>([]);
let epoch = 0,
  active = false;
let retry:
  { id: string; question: string; revision: number; chat: string; reply?: number } | undefined;

// 账本被撤权或会话失效时立即清除屏幕上的私人信息。
function clearPrivate() {
  denied.value = true;
  draft.value = null;
  accounts.value = [];
  categories.value = [];
  turns.value = [];
  history.value = [];
  chat.value = null;
  reference.value = undefined;
  result.value = null;
  source.value = '';
  showHistory.value = false;
  retry = undefined;
  input.value = '';
  error.value = '对话不可访问，请返回账本。';
}
function fail(e: unknown) {
  if (e instanceof ApiRequestError && e.code === 'XIAOBU_LEDGER_READ_ONLY') {
    writable.value = false;
    error.value = '权限已变为只读，这笔账未保存。';
    return;
  }
  if (e instanceof ApiRequestError && [401, 403, 404].includes(e.status)) {
    clearPrivate();
    return;
  }
  error.value =
    e instanceof ApiRequestError && e.code === 'XIAOBU_ASSISTANT_MODEL_UNAVAILABLE'
      ? '文字模型暂不可用，请重试或使用快捷问法。'
      : e instanceof ApiRequestError && e.status === 409
        ? '对话已有新回答，请重新打开历史后继续。'
        : '暂时无法完成，请重试。';
}
// 过期页面的请求结果不可重新填入当前屏幕。
async function run(action: (token: number) => Promise<void>) {
  if (busy.value || denied.value) return;
  const token = epoch;
  busy.value = true;
  error.value = '';
  try {
    await action(token);
  } catch (e) {
    if (token === epoch) fail(e);
  } finally {
    if (token === epoch) busy.value = false;
  }
}
function scrollLast() {
  const last = turns.value[turns.value.length - 1];
  anchor.value = last ? 'msg-' + last.sequence : '';
}
async function loadChat(c: Conversation, token: number) {
  const [rows, state] = await Promise.all([api.value.history(c.id), api.value.draft(c.id)]);
  if (token !== epoch) return;
  draft.value = state.draft;
  uncertain.value = false;
  chat.value = { ...c, revision: rows[rows.length - 1]?.sequence ?? 0 };
  turns.value = rows;
  older.value = (rows[0]?.sequence ?? 0) > 1;
  reference.value = undefined;
  retry = undefined;
  input.value = '';
  showHistory.value = false;
  scrollLast();
}
function fresh() {
  draft.value = null;
  uncertain.value = false;
  chat.value = null;
  turns.value = [];
  reference.value = undefined;
  retry = undefined;
  result.value = null;
  source.value = '';
  older.value = false;
  input.value = '';
  showHistory.value = false;
  error.value = '';
}
function histories(append = false) {
  return run(async (t) => {
    const rows = await api.value.list(append ? history.value.length : 0);
    if (t !== epoch) return;
    history.value = append ? [...history.value, ...rows] : rows;
    moreHistory.value = rows.length === 20;
    showHistory.value = true;
  });
}
function previousTurns() {
  return run(async (t) => {
    if (!chat.value || !turns.value.length) return;
    const rows = await api.value.history(chat.value.id, turns.value[0].sequence);
    if (t !== epoch) return;
    turns.value = [...rows, ...turns.value];
    older.value = (rows[0]?.sequence ?? 0) > 1;
  });
}
// 已识别的支出在本地按原型规则生成字段，缺项保留到下一轮。
function expenseFields(text: string): DraftFields | null {
  if (!writable.value || reference.value) return null;
  const old = draft.value?.fields;
  const category = categories.value.find((c) => c.code === old?.categoryCode)?.name;
  const account = accounts.value.find((a) => a.id === old?.accountId)?.name;
  const prev = old ? { category, account, amount: old.amountCents, note: old.note } : null;
  const parsed = expenseIntent(
    text,
    categories.value.map((c) => c.name),
    accounts.value.map((a) => a.name),
    prev,
  );
  if (!parsed) return null;
  if (
    old &&
    ((old.amountCents && parsed.amount !== old.amountCents) ||
      (category && parsed.category !== category))
  ) {
    throw new Error('请先修改、确认或放弃当前草稿');
  }
  const offset = parsed.day === '昨天' ? 1 : parsed.day === '前天' ? 2 : 0;
  const day = new Date(Date.now() + 8 * 3600000 - offset * 86400000).toISOString().slice(0, 10);
  return {
    bookedOn: parsed.day || !old ? day : old.bookedOn,
    amountCents: parsed.amount ?? null,
    categoryCode: categories.value.find((c) => c.name === parsed.category)?.code ?? null,
    accountId: accounts.value.find((a) => a.name === parsed.account)?.id ?? null,
    note: parsed.note ?? '',
  };
}
async function storeDraft(c: Conversation, t: number, q: string, fields: DraftFields) {
  try {
    await api.value.editDraft(c, draft.value, q, fields);
  } catch (e) {
    if (t === epoch && (!(e instanceof ApiRequestError) || [0, 409].includes(e.status)))
      uncertain.value = true;
    throw e;
  }
  if (t === epoch) await loadChat(c, t);
}
function editFields(fields: DraftFields) {
  return run(async (t) => {
    if (chat.value) await storeDraft(chat.value, t, '修改支出草稿', fields);
  });
}
function recover() {
  return run(async (t) => {
    if (chat.value) {
      const id = draft.value?.id;
      if (id) {
        const state = await api.value.draft(chat.value.id, id);
        if (t !== epoch) return;
        if (state.draft?.status === 'CONFIRMED') uni.$emit('assistant-expense-saved', ledger.value);
      }
      await loadChat(chat.value, t);
    }
  });
}
function finish(action: 'confirm' | 'discard') {
  return run(async (t) => {
    if (!chat.value || !draft.value || uncertain.value) return;
    const c = chat.value;
    uncertain.value = true;
    const result = await api.value.finishDraft(c.id, draft.value, action);
    if (result.status === 'CONFIRMED') uni.$emit('assistant-expense-saved', ledger.value);
    if (t === epoch) await loadChat(c, t);
  });
}
function send(text: string) {
  const q = text.trim();
  if (!q) return;
  return run(async (t) => {
    if (!chat.value) {
      const c = await api.value.create();
      if (t !== epoch) return;
      chat.value = c;
    }
    const c = chat.value;
    let fields;
    try {
      fields = expenseFields(q);
    } catch {
      error.value = '请先修改、确认或放弃当前草稿。';
      return;
    }
    if (fields) {
      if (uncertain.value) {
        error.value = '请先核对草稿状态。';
        return;
      }
      await storeDraft(c, t, q, fields);
      return;
    }
    if (!retry || retry.chat !== c.id || retry.question !== q || retry.reply !== reference.value)
      retry = {
        id: chatRequestId(),
        question: q,
        revision: c.revision,
        chat: c.id,
        reply: reference.value,
      };
    input.value = q;
    const msg = await api.value.ask(c.id, q, retry.revision, retry.id, retry.reply);
    if (t !== epoch) return;
    turns.value.push(msg);
    chat.value = { ...c, revision: msg.sequence };
    retry = undefined;
    reference.value = undefined;
    input.value = '';
    scrollLast();
  });
}
function entries(seq: number, page = 0) {
  return run(async (t) => {
    if (!chat.value) return;
    const rows = await api.value.entries(chat.value.id, seq, page);
    if (t !== epoch) return;
    selected.value = seq;
    result.value = rows;
  });
}
function back() {
  uni.navigateBack({ fail: () => uni.reLaunch({ url: '/pages/ledger/index' }) });
}
onLoad((options) => {
  ledger.value = String(options?.ledger ?? '');
  writable.value = options?.writable === '1';
  const label = String(options?.name ?? '当前账本');
  try {
    name.value = decodeURIComponent(label);
  } catch {
    name.value = label;
  }
});
onShow(() => {
  if (!ledger.value || active) return;
  active = true;
  denied.value = false;
  void run(async (t) => {
    const [rows, accts, cats] = await Promise.all([
      api.value.list(),
      listLedgerAccounts(ledger.value),
      listLedgerCategories(ledger.value),
    ]);
    if (t !== epoch) return;
    accounts.value = accts;
    categories.value = cats.filter(
      (c) => c.active && c.direction === 'EXPENSE' && !c.capabilities?.length,
    );
    if (t !== epoch) return;
    const c = chat.value ?? rows[0];
    if (c) await loadChat(c, t);
  });
});
function hide() {
  draft.value = null;
  accounts.value = [];
  categories.value = [];
  active = false;
  epoch++;
  busy.value = false;
  turns.value = [];
  history.value = [];
  result.value = null;
  source.value = '';
  showHistory.value = false;
}
onHide(hide);
onUnload(hide);
</script>

<template>
  <view class="chat">
    <view class="bar"
      ><button @click="back">‹ 返回</button
      ><view class="heading"
        >小簿<text>{{ name }}</text></view
      >
      <button :disabled="busy || denied" @click="histories()">历史</button
      ><button :disabled="busy || denied" @click="fresh">新建</button></view
    >
    <scroll-view v-if="showHistory" class="thread" scroll-y>
      <button @click="showHistory = false">返回对话</button
      ><text v-if="!history.length">暂无历史对话</text>
      <button
        v-for="c in history"
        :key="c.id"
        class="row"
        :disabled="busy"
        @click="run((t) => loadChat(c, t))"
      >
        {{ c.title }}
      </button>
      <button v-if="moreHistory" :disabled="busy" @click="histories(true)">更多历史</button>
    </scroll-view>
    <scroll-view v-else-if="result" class="thread" scroll-y>
      <button @click="result = null">‹ 返回回答</button>
      <view class="card"
        >当前匹配 {{ result.totalCount }} 笔 · 净支出 ¥{{ formatCents(result.expenseCents) }}</view
      >
      <button
        v-for="row in result.entries"
        :key="row.id"
        class="row"
        @click="source = row.originalTransactionId || row.id"
      >
        {{ row.bookedOn }} ·
        {{ row.originalTransactionId ? '退款' : row.direction === 'INCOME' ? '收入' : '支出' }} ¥{{
          formatCents(row.amountCents)
        }}
        {{ row.note }}
      </button>
      <button v-if="result.page > 0" :disabled="busy" @click="entries(selected!, result.page - 1)">
        上一页
      </button>
      <button
        v-if="(result.page + 1) * result.pageSize < result.totalCount"
        :disabled="busy"
        @click="entries(selected!, result.page + 1)"
      >
        下一页
      </button>
    </scroll-view>
    <scroll-view v-else class="thread" scroll-y :scroll-into-view="anchor">
      <button v-if="older" :disabled="busy" @click="previousTurns">更早的消息</button>
      <view v-if="!turns.length && !denied" class="welcome"
        ><text>今天想记点什么？</text>
        <button :disabled="busy" @click="send('本月收支')">本月收支 ›</button
        ><button :disabled="busy" @click="send('本月餐饮支出')">本月餐饮支出 ›</button
        ><button v-if="writable" :disabled="busy" @click="send('昨天买菜')">
          昨天买菜 ›
        </button></view
      >
      <view v-for="m in turns" :key="m.sequence" :id="'msg-' + m.sequence" class="turn">
        <text class="mine">{{ m.question }}</text><view class="card"
          ><text>{{ m.answer.text }}</text>
          <template v-if="m.answer.statement && m.answer.scope"
            ><text class="caption"
              >{{ m.answer.scope.plan.from }} 至 {{ m.answer.scope.plan.through }} ·
              {{
                [
                  m.answer.scope.plan.category,
                  m.answer.scope.plan.account,
                  m.answer.scope.plan.recordedByMe ? '我登记的' : '',
                  m.answer.scope.plan.direction === 'INCOME'
                    ? '收入'
                    : m.answer.scope.plan.direction === 'EXPENSE'
                      ? '支出'
                      : '',
                  m.answer.scope.plan.keyword,
                ]
                  .filter(Boolean)
                  .join(' · ') || '全部收支'
              }}</text
            >
            <text
              >收入 ¥{{ formatCents(m.answer.statement.incomeCents) }} · 净支出 ¥{{
                formatCents(m.answer.statement.expenseCents)
              }}</text
            >
            <text>结余 ¥{{ formatCents(m.answer.statement.balanceCents) }}</text>
            <text
              class="caption"
              v-for="(entry, index) in m.answer.statement.entries.slice(0, 2)"
              :key="entry.id"
            >
              第 {{ index + 1 }} 笔 · {{ entry.bookedOn }} ·
              {{
                entry.originalTransactionId
                  ? '退款'
                  : entry.direction === 'INCOME'
                    ? '收入'
                    : '支出'
              }}
              ¥{{ formatCents(entry.amountCents) }}
            </text>
            <button :disabled="busy" @click="entries(m.sequence)">
              查看 {{ m.answer.statement.totalCount }} 笔来源 ›
            </button>
            <button
              :disabled="busy"
              @click="
                reference = m.sequence;
                send('那上个月呢');
              "
            >
              那上个月呢
            </button>
            <button
              v-if="m.answer.statement.entries.length > 1"
              :disabled="busy"
              @click="
                reference = m.sequence;
                send('看第二笔');
              "
            >
              看第二笔
            </button>
          </template>
          <button
            v-for="choice in m.answer.choices"
            :key="choice.label"
            :disabled="busy"
            @click="
              reference = m.sequence;
              send(choice.label);
            "
          >
            {{ choice.label }}
          </button>
          <button v-if="m.answer.sourceId" @click="source = m.answer.sourceId">
            打开流水详情 ›
          </button></view
        ></view
      >
      <ExpenseDraftCard
        v-if="draft"
        :draft="draft"
        :name="name"
        :accounts="accounts"
        :categories="categories"
        :busy="busy"
        :uncertain="uncertain"
        :writable="writable"
        @finish="finish"
        @edit="editFields"
        @recover="recover"
      />
      <button v-else-if="uncertain" :disabled="busy" @click="recover">核对草稿状态</button>
    </scroll-view>
    <text v-if="error" class="error" role="alert">{{ error }}</text>
    <view v-if="!showHistory && !result && !denied" class="composer"
      ><text class="caption">自由提问会将问题和必要的查询条件发送给 DeepSeek。</text>
      <view
        ><input
          v-model="input"
          maxlength="500"
          placeholder="问问这个账本"
          :disabled="busy"
          @confirm="send(input)"
        /><button :disabled="busy || !input.trim()" @click="send(input)">
          {{ busy ? '查询中…' : '发送' }}
        </button></view
      ></view
    >
    <LedgerTransactionDetailSheet
      v-if="source"
      :key="source"
      :ledger-id="ledger"
      :transaction-id="source"
      browse-only
      @close="source = ''"
      @access-lost="clearPrivate"
    />
  </view>
</template>

<style scoped>
.chat {
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  background: #f5f6f1;
  color: #233b32;
  font-family: inherit;
}
.bar,
.composer > view {
  display: flex;
  align-items: center;
  gap: 8px;
}
.bar,
.composer {
  background: #fff;
  padding: 12px 16px;
}
.bar {
  border-bottom: 1px solid #dde5dc;
  padding-top: max(12px, env(safe-area-inset-top));
}
.heading {
  flex: 1;
  font-size: 18px;
  font-weight: 650;
}
.heading text,
.caption {
  display: block;
  color: #586d61;
  font-size: 12px;
}
.thread {
  flex: 1;
  min-height: 0;
}
button {
  color: #285841;
  border: 0;
  background: transparent;
  font-size: 14px;
  margin: 0;
  padding: 6px 10px;
  line-height: 1.5;
}
button::after {
  border: 0;
}
.welcome,
.turn,
.row {
  margin: 18px 16px;
}
.welcome text {
  font-size: 22px;
  font-weight: 650;
}
.welcome button,
.row {
  display: block;
  text-align: left;
  background: #fff;
  border-radius: 12px;
  margin-top: 10px;
  padding: 12px;
}
.mine {
  display: block;
  margin-left: auto;
  width: fit-content;
  background: #dbece0;
  padding: 10px 14px;
  border-radius: 15px;
  white-space: pre-wrap;
}
.card {
  margin: 10px 0;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
}
.card text {
  white-space: pre-wrap;
  display: block;
  margin-bottom: 6px;
}
.composer {
  padding-bottom: max(12px, env(safe-area-inset-bottom));
}
.composer input {
  flex: 1;
  min-width: 0;
  background: #f4f6f3;
  padding: 10px;
  border-radius: 12px;
}
.composer button {
  background: #27543e;
  color: white;
  border-radius: 12px;
}
.error {
  color: #993b33;
  padding: 8px 16px;
}
</style>
