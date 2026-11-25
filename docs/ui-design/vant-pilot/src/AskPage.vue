<script setup>
import { ref, computed, nextTick, watch } from 'vue';
import { Icon as VanIcon, Popup as VanPopup } from 'vant';
import AssistantMark from './AssistantMark.vue';
import AssistantVoice from './AssistantVoice.vue';
import { amountText } from './ledger.mjs';
const props = defineProps({ messages: Array, sessions: Array, guest: Boolean, writable: Boolean });
const emit = defineEmits(['back', 'ask', 'confirm', 'history', 'new', 'row', 'modify', 'login']);
const draft = defineModel('draft', { type: String, default: '' });
const voice = ref(false),
  historyOpen = ref(false),
  thread = ref(null);
const prompts = ['这个月支出多少', '昨天微信买菜 68'];
// 新消息和保存结果显示在最近的对话位置。
watch(
  () => props.messages,
  async () => {
    await nextTick();
    if (thread.value) thread.value.scrollTop = thread.value.scrollHeight;
  },
  { deep: true, immediate: true },
);
// 语音不可用时直接聚焦当前会话的输入框。
async function typeInstead() {
  voice.value = false;
  await nextTick();
  document.querySelector('.assistant-frame input')?.focus();
}
function send(text) {
  if (text.trim()) {
    emit('ask', text.trim());
    draft.value = '';
  }
}
const recent = computed(() => [...props.sessions].reverse());
</script>
<template>
  <main class="assistant-page">
    <div class="assistant-frame">
      <header>
        <button aria-label="返回" @click="emit('back')"><VanIcon name="arrow-left" /></button>
        <h1>小簿</h1>
        <button aria-label="历史对话" title="历史对话" @click="historyOpen = true">
          <VanIcon name="clock-o" /></button
        ><button aria-label="新对话" title="新对话" @click="emit('new')">
          <svg
            class="input-symbol"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path
              d="M6 3.5h12a3 3 0 0 1 3 3v9a3 3 0 0 1-3 3h-7l-5.5 3v-3H6a3 3 0 0 1-3-3v-9a3 3 0 0 1 3-3Z"
            />
            <path d="M9 11h6m-3-3v6" />
          </svg>
        </button>
      </header>
      <section ref="thread" class="thread" aria-live="polite">
        <div v-if="!messages.length" class="welcome">
          <span class="hero-mark"><AssistantMark /></span>
          <h2>今天想记点什么？</h2>
          <div class="prompts">
            <button v-for="(q, i) in prompts" :key="q" @click="send(q)">
              <VanIcon :name="i ? 'edit' : 'search'" />{{ q }}<VanIcon name="arrow" />
            </button>
          </div>
        </div>
        <article v-for="m in messages" :key="m.id">
          <p class="mine">{{ m.question }}</p>
          <div class="response">
            <AssistantMark />
            <div>
              <p v-if="m.answer">{{ m.answer }}</p>
              <details v-if="m.matches?.length" class="answer-rows">
                <summary>查看 {{ m.matches.length }} 笔明细</summary>
                <button v-for="(r, i) in m.matches" :key="i" @click="emit('row', r.id)">
                  {{ r.label }} <span>{{ r.value }}</span>
                </button>
              </details>
              <section v-if="m.card" class="confirm-card">
                <small>{{
                  m.card.type === 'refund' ? '原单退款' : m.card.category + ' · 支出'
                }}</small><strong>¥{{ amountText(m.card.amount) }}</strong>
                <p>{{ m.card.date }}{{ m.card.account ? ' · ' + m.card.account : '' }}</p>
                <p v-if="m.card.type === 'refund'">
                  原单 {{ m.card.category }} · 退款后实付 ¥{{ amountText(m.card.net) }}
                </p>
                <footer v-if="m.saved">
                  <span>已保存</span><button @click="emit('row', m.rowId)">查看流水 ›</button>
                </footer>
                <footer v-else-if="writable">
                  <button @click="emit('modify', m)">修改</button
                  ><button class="solid" @click="emit('confirm', m)">
                    {{ m.card.type === 'refund' ? '确认退款' : '保存账目' }}
                  </button>
                </footer>
                <footer v-else-if="guest">
                  <span>示例预览</span><button @click="emit('login')">登录后记一笔 ›</button>
                </footer>
                <footer v-else>只读</footer>
              </section>
            </div>
          </div>
        </article>
      </section>
      <form :class="{ 'voice-mode': voice }" @submit.prevent="send(draft)">
        <button type="button" :aria-label="voice ? '切换键盘' : '切换语音'" @click="voice = !voice">
          <svg v-if="voice" class="input-symbol" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect
              x="2"
              y="5"
              width="20"
              height="14"
              rx="3"
              stroke="currentColor"
              stroke-width="1.6"
            />
            <path
              d="M6 9h.2m3.7 0h.2m3.7 0h.2m3.7 0h.2M6 12h.2m3.7 0h.2m3.7 0h.2m3.7 0h.2M8 15h8"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            /></svg
          ><svg
            v-else
            class="input-symbol"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            aria-hidden="true"
          >
            <rect x="9" y="2.5" width="6" height="12" rx="3" />
            <path d="M5.5 11a6.5 6.5 0 0 0 13 0M12 18v3m-3 0h6" />
          </svg></button
        ><AssistantVoice
          v-if="voice"
          v-slot="{ listening, cancelled }"
          @keyboard="typeInstead"
          @text="send"
          ><span class="voice-bars" aria-hidden="true"><i /><i /><i /><i /></span
          >{{ listening ? (cancelled ? '松开取消' : '松开发送') : '按住说话' }}</AssistantVoice
        ><input
          v-else
          v-model="draft"
          placeholder="问点什么，或记一笔…"
          aria-label="输入消息"
          maxlength="200"
        /><button v-if="!voice" type="submit" aria-label="发送" :disabled="!draft.trim()">
          <VanIcon name="arrow-up" />
        </button>
      </form>
    </div>
  </main>
  <VanPopup v-model:show="historyOpen" position="bottom" round class="assistant-history"
    ><h2>历史对话</h2>
    <p v-if="!recent.length">还没有对话</p>
    <button
      v-for="s in recent"
      :key="s.id"
      @click="
        emit('history', s.id);
        historyOpen = false;
      "
    >
      {{ s.messages[0]?.question }}<VanIcon name="arrow" /></button
  ></VanPopup>
</template>
<style scoped>
.assistant-page {
  position: fixed;
  inset: 0;
  z-index: 9;
  background: #f6f8f4;
  color: var(--ink);
}
.assistant-frame {
  display: flex;
  flex-direction: column;
  max-width: 560px;
  margin: auto;
  height: 100%;
  background: #fafbf8;
}
.assistant-page button,
.assistant-history button {
  font: inherit;
  color: inherit;
  background: transparent;
  border: 0;
  cursor: pointer;
}
.assistant-frame header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
  gap: 4px;
}
.assistant-frame header button,
.assistant-frame form button {
  min-width: 40px;
  height: 42px;
  font-size: 20px;
}
.assistant-frame h1 {
  flex: 1;
  font-size: 18px;
}
.thread {
  flex: 1;
  overflow: auto;
  padding: 18px 16px;
}
.welcome {
  margin: clamp(40px, 14vh, 130px) auto 0;
  max-width: 340px;
}
.hero-mark {
  display: inline-grid;
  place-items: center;
  background: #e4f0e9;
  color: #176a57;
  width: 54px;
  height: 54px;
  border-radius: 17px;
  font-size: 24px;
}
.welcome h2 {
  font-size: 25px;
  margin: 16px 0 24px;
}
.prompts {
  display: grid;
  gap: 8px;
}
.prompts button,
.assistant-history button {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 13px 14px;
  background: white;
  border: 1px solid var(--line);
  border-radius: 12px;
  text-align: left;
}
.prompts i:last-child,
.assistant-history i {
  margin-left: auto;
  color: var(--muted);
}
article {
  margin-bottom: 20px;
}
.mine {
  margin: 0 0 16px auto;
  width: fit-content;
  max-width: 88%;
  padding: 10px 14px;
  border-radius: 16px 16px 4px;
  background: #deeee3;
}
.response {
  display: flex;
  gap: 12px;
  align-items: baseline;
}
.response > .assistant-mark {
  flex: none;
  font-size: 14px;
  color: var(--green);
}
.response > div {
  flex: 1;
  min-width: 0;
}
.response p {
  font-size: 15px;
  line-height: 1.6;
  margin: 0;
}
.confirm-card {
  background: white;
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 14px;
}
.confirm-card small,
.confirm-card p {
  color: var(--muted);
  font-size: 13px;
}
.confirm-card strong {
  display: block;
  font-size: 30px;
  margin: 6px 0;
}
.confirm-card footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  border-top: 1px solid var(--line);
  padding-top: 8px;
  color: var(--green);
}
.confirm-card button {
  min-height: 38px;
}
.confirm-card .solid {
  background: var(--green);
  color: white;
  padding: 0 16px;
  border-radius: 10px;
}
.assistant-frame form {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 8px 12px max(12px, env(safe-area-inset-bottom));
  background: white;
  padding: 4px 6px;
  border: 1px solid #ccd9cf;
  border-radius: 24px;
}
.assistant-frame input,
.assistant-frame .voice-trigger {
  min-width: 0;
  flex: 1;
  border: 0;
  background: transparent;
  font: inherit;
}
.assistant-frame form button[type='submit'] {
  background: var(--green);
  border-radius: 50%;
  color: white;
}
.assistant-frame form button:disabled {
  opacity: 0.4;
}
.assistant-history {
  max-width: 480px;
  inset-inline: 0;
  margin: auto;
  padding: 18px;
  max-height: 70vh;
}
.assistant-history h2 {
  font-size: 18px;
  margin-bottom: 12px;
}
.assistant-history button {
  width: 100%;
  margin: 6px 0;
}
.answer-rows {
  margin-top: 8px;
  font-size: 14px;
  color: var(--green);
}
.answer-rows summary {
  cursor: pointer;
}
.answer-rows button {
  width: 100%;
  display: flex;
  justify-content: space-between;
  text-align: left;
  align-items: center;
  border-bottom: 1px solid var(--line);
  min-height: 42px;
}
.assistant-frame form {
  box-sizing: border-box;
  height: 50px;
  flex: none;
  padding: 2px;
  gap: 4px;
  margin-inline: 14px;
  border: 1px solid #d6e0d9;
  border-radius: 17px;
}
.assistant-frame form > button:first-child,
.assistant-frame form > button[type='submit'] {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  flex: none;
  padding: 0;
}
.input-symbol {
  width: 21px;
  height: 21px;
  vertical-align: middle;
}
.assistant-frame form > button[type='submit'] {
  background: transparent;
  color: white;
}
.assistant-frame form > button[type='submit'] i {
  display: grid;
  place-items: center;
  background: var(--green);
  width: 32px;
  height: 32px;
  border-radius: 11px;
  font-size: 17px;
}
.assistant-frame .voice-mode > button:first-child {
  position: relative;
  color: var(--muted);
}
.voice-mode > button:first-child::after {
  content: '';
  position: absolute;
  right: -2px;
  width: 1px;
  height: 18px;
  background: var(--line);
}
.assistant-frame .voice-mode :deep(.voice-pad) {
  border-radius: 13px;
  background: transparent;
  color: var(--ink);
  font-size: 15px;
  min-height: 44px;
  height: 44px;
}
.assistant-frame .voice-mode :deep(.voice-pad[data-listening='true']) {
  background: var(--green);
  color: white;
}
.voice-bars {
  color: var(--green);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  height: 18px;
}
.voice-bars i {
  width: 2px;
  height: 6px;
  border-radius: 2px;
  background: currentColor;
}
.voice-bars i:nth-child(2) {
  height: 15px;
}
.voice-bars i:nth-child(3) {
  height: 11px;
}
.voice-pad[data-listening='true'] .voice-bars {
  color: inherit;
}
.welcome {
  margin-top: clamp(32px, 11vh, 96px);
}
.hero-mark {
  border-radius: 19px;
  background: #205d49;
  color: #f2f8ee;
  --wave-color: #205d49;
}
</style>
