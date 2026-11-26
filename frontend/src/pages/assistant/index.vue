<script setup lang="ts">
import ChatScroll from './ChatScroll.vue';
import LedgerIcon from '../../components/LedgerIcon.vue';
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
    <view class="bar">
      <button aria-label="返回" @click="back">
        <LedgerIcon class="page-icon" src="/static/assistant/back.svg" />
      </button>
      <view class="heading"
        >小簿<text>{{ name }}</text></view
      >
      <button aria-label="历史对话" :disabled="busy || denied" @click="histories()">
        <LedgerIcon class="page-icon" src="/static/assistant/history.svg" />
      </button>
      <button aria-label="新对话" :disabled="busy || denied" @click="fresh">
        <LedgerIcon class="page-icon" src="/static/assistant/new.svg" />
      </button>
    </view>
    <ChatScroll v-if="result" class="thread">
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
    </ChatScroll>
    <ChatScroll v-else class="thread" :anchor="anchor">
      <button v-if="older" :disabled="busy" @click="previousTurns">更早的消息</button>
      <view v-if="!turns.length && !denied" class="welcome">
        <view class="hero"
          ><LedgerIcon class="page-icon" src="/static/assistant/mark-light.svg"
        /></view>
        <text class="welcome-title">今天想记点什么？</text>
        <button
          v-for="prompt in ['本月收支', '本月餐饮支出']"
          :key="prompt"
          :disabled="busy"
          @click="send(prompt)"
        >
          <LedgerIcon class="page-icon" src="/static/assistant/search.svg" />{{ prompt
          }}<text class="arrow">›</text>
        </button>
        <button v-if="writable" :disabled="busy" @click="send('昨天买菜')">
          <LedgerIcon class="page-icon" src="/static/ledger/vant/EDIT_DARK.svg" />昨天买菜<text
            class="arrow"
            >›</text
          >
        </button>
      </view>
      <view v-for="m in turns" :key="m.sequence" :id="'msg-' + m.sequence" class="turn">
        <text class="mine">{{ m.question }}</text><view class="response"
          ><LedgerIcon class="page-icon" src="/static/assistant/mark.svg" /><view class="answer"
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
              <button
                v-if="m.answer.statement.totalCount"
                class="answer-details"
                :disabled="busy"
                @click="entries(m.sequence)"
              >
                查看 {{ m.answer.statement.totalCount }} 笔明细 ›
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
    </ChatScroll>
    <text v-if="error" class="error" role="alert">{{ error }}</text>
    <view v-if="!result && !denied" class="composer"
      ><text v-if="input.trim()" class="caption"
        >自由提问会将问题和必要的查询条件发送给 DeepSeek。</text
      >
      <view
        ><input
          v-model="input"
          maxlength="500"
          placeholder="问问这个账本"
          :disabled="busy"
          @confirm="send(input)" /><button
          :aria-label="busy ? '查询中' : '发送'"
          :disabled="busy || !input.trim()"
          @click="send(input)"
        >
          <LedgerIcon class="page-icon" src="/static/assistant/send.svg" /></button></view
    ></view>
    <view v-if="showHistory" class="history-overlay" @click.self="showHistory = false">
      <view class="history-sheet" role="dialog" aria-modal="true" aria-label="历史对话">
        <view class="history-head"
          ><text>历史对话</text
          ><button aria-label="关闭历史对话" @click="showHistory = false">×</button></view
        >
        <ChatScroll class="history-list"
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
        </ChatScroll>
      </view>
    </view>
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
  --green: #176a57;
  --muted: #536660;
  --line: #e4eae5;
  --ink: #182623;
  height: 100vh;
  height: 100dvh;
  display: flex;
  flex-direction: column;
  max-width: 560px;
  margin: auto;
  background: #fafbf8;
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
  padding: 8px 12px;
}
.bar {
  border-bottom: 1px solid #dde5dc;
  padding-top: calc(8px + env(safe-area-inset-top));
  gap: 4px;
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
.turn,
.row {
  margin: 18px 16px;
}
.bar button {
  display: grid;
  place-items: center;
  width: 40px;
  height: 42px;
}
.bar .page-icon,
.welcome button .page-icon {
  width: 20px;
  height: 20px;
}
.welcome {
  max-width: 340px;
  margin: calc(18px + clamp(32px, 11vh, 96px)) auto 24px;
  padding: 0 16px;
}
.hero {
  display: grid;
  place-items: center;
  width: 54px;
  height: 54px;
  border-radius: 19px;
  background: #205d49;
}
.hero .page-icon {
  width: 30px;
  height: 30px;
}
.welcome-title {
  display: block;
  font-size: 25px;
  font-weight: 700;
  margin: 16px 0 24px;
}
.welcome button,
.row {
  display: flex;
  align-items: center;
  gap: 12px;
  text-align: left;
  background: white;
  border: 1px solid #dce6df;
  border-radius: 12px;
  margin-top: 8px;
  padding: 13px 14px;
  color: inherit;
  font-size: 15px;
}
.arrow {
  margin-left: auto;
  color: #63756a;
}
.response {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  margin-top: 16px;
}
.response .page-icon {
  flex: none;
  width: 18px;
  height: 18px;
  margin-top: 3px;
}
.answer {
  min-width: 0;
  flex: 1;
  line-height: 1.6;
  font-size: 15px;
}
.mine {
  display: block;
  margin-left: auto;
  width: fit-content;
  max-width: 88%;
  box-sizing: border-box;
  overflow-wrap: anywhere;
  background: #deeee3;
  padding: 10px 14px;
  border-radius: 16px 16px 4px 16px;
  white-space: pre-wrap;
}
.card {
  margin: 10px 0;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
}
.answer text,
.card text {
  white-space: pre-wrap;
  display: block;
  margin-bottom: 6px;
}
.composer {
  background: transparent;
  padding: 0 14px max(12px, env(safe-area-inset-bottom));
}
.composer .caption {
  margin: 4px 2px;
}
.composer > view {
  background: white;
  height: 50px;
  box-sizing: border-box;
  padding: 2px 2px 2px 14px;
  border: 1px solid #d6e0d9;
  border-radius: 17px;
}
.composer input {
  flex: 1;
  min-width: 0;
}
.composer button {
  width: 44px;
  height: 44px;
  padding: 0;
  display: grid;
  place-items: center;
}
.composer button .page-icon {
  width: 18px;
  height: 18px;
  padding: 7px;
  background: #176a57;
  border-radius: 11px;
}
.composer button[disabled] {
  opacity: 0.4;
  background: transparent;
}
.history-overlay {
  position: fixed;
  inset: 0;
  z-index: 5;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.45);
}
.history-sheet {
  background: white;
  border-radius: 16px 16px 0 0;
  width: 100%;
  max-width: 480px;
  box-sizing: border-box;
  padding: 18px 18px max(18px, env(safe-area-inset-bottom));
}
.history-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 18px;
  font-weight: 700;
}
.history-head button {
  font-size: 22px;
}
.history-list {
  max-height: 60vh;
}
.history-list .row {
  margin: 6px 0;
}
.error {
  color: #993b33;
  padding: 8px 16px;
}
.answer .answer-details {
  padding: 4px 0;
  text-align: left;
  color: #176a57;
}
</style>
