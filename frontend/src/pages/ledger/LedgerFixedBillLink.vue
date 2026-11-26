<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import {
  linkFixedBillPayment,
  listFixedBillCandidates,
  type FixedBillCandidate,
  type FixedBillPeriod,
} from '../../api/fixed-bills';
import { ApiRequestError } from '../../api/request';
import { categoryLabel, formatCents } from './transaction';

const props = defineProps<{
  ledgerId: string;
  billId: string;
  month: string;
  categoryNames: Record<string, string>;
}>();
const emit = defineEmits<{ linked: [result: FixedBillPeriod]; busy: [saving: boolean] }>();
const search = ref('');
const rows = ref<FixedBillCandidate[]>([]);
const chosen = ref<FixedBillCandidate>();
const error = ref('');
const notice = ref('');
const loading = ref(false);
const saving = ref(false);
const page = ref(0);
const total = ref(0);
let requestNumber = 0;
let delay: ReturnType<typeof setTimeout> | undefined;

async function load(target = 0) {
  const sequence = ++requestNumber;
  loading.value = true;
  error.value = '';
  if (!target) {
    page.value = 0;
    rows.value = [];
    total.value = 0;
    chosen.value = undefined;
  }
  try {
    const result = await listFixedBillCandidates(
      props.ledgerId,
      props.billId,
      props.month,
      search.value.trim(),
      target,
    );
    if (sequence !== requestNumber) return;
    if (
      target &&
      (total.value !== result.totalCount ||
        result.entries.some((entry) => rows.value.some((row) => row.id === entry.id)))
    ) {
      await load();
      return;
    }
    rows.value = target ? [...rows.value, ...result.entries] : result.entries;
    page.value = result.page;
    total.value = result.totalCount;
  } catch (failure) {
    if (sequence !== requestNumber) return;
    error.value =
      failure instanceof ApiRequestError && failure.status === 409
        ? '本期账单已付，请返回列表刷新。'
        : '支出流水加载失败，请重试。';
  } finally {
    if (sequence === requestNumber) loading.value = false;
  }
}

watch(search, () => {
  clearTimeout(delay);
  requestNumber++;
  loading.value = true;
  chosen.value = undefined;
  rows.value = [];
  notice.value = '';
  delay = setTimeout(() => void load(), 250);
});
onBeforeUnmount(() => {
  clearTimeout(delay);
  requestNumber++;
});
void load();

function choose(row: FixedBillCandidate) {
  chosen.value = row;
  notice.value = '';
}

async function confirm() {
  if (saving.value || loading.value || !chosen.value) return;
  saving.value = true;
  notice.value = '';
  emit('busy', true);
  try {
    const result = await linkFixedBillPayment(
      props.ledgerId,
      props.billId,
      props.month,
      chosen.value,
    );
    emit('linked', result);
  } catch (failure) {
    if (failure instanceof ApiRequestError && failure.status === 409) {
      await load();
      notice.value = '这笔流水或账期已变更，请核对后重新选择。';
    } else if (failure instanceof ApiRequestError && failure.status === 403) {
      notice.value = '你当前没有记账权限，未关联流水。';
    } else {
      notice.value = '未确认是否关联成功，请返回列表核对。';
    }
  } finally {
    saving.value = false;
    emit('busy', false);
  }
}
</script>

<template>
  <view class="link-search">
    <input
      v-model="search"
      aria-label="搜索支出流水"
      placeholder="搜索金额、分类、日期或备注"
      maxlength="100"
      :disabled="saving"
    />
    <button
      v-if="search"
      aria-label="清除搜索"
      class="link-clear"
      :disabled="saving"
      @click="search = ''"
    >
      ×
    </button>
  </view>
  <view v-if="rows.length" class="link-list">
    <button
      v-for="row in rows"
      :key="row.id"
      class="link-row"
      :class="{ chosen: chosen?.id === row.id }"
      :aria-pressed="chosen?.id === row.id"
      :disabled="saving"
      @click="choose(row)"
    >
      <view class="link-copy">
        <text>{{
          row.note || categoryNames[row.categoryCode] || categoryLabel(row.categoryCode, 'EXPENSE')
        }}</text>
        <text class="link-caption">{{
          [
            row.bookedOn,
            categoryNames[row.categoryCode] || categoryLabel(row.categoryCode, 'EXPENSE'),
            row.accountName,
          ]
            .filter(Boolean)
            .join(' · ')
        }}</text>
      </view>
      <text class="link-amount"
        >¥{{ formatCents(row.amountCents) }}{{ chosen?.id === row.id ? ' ✓' : '' }}</text
      >
    </button>
  </view>
  <text v-if="loading" class="link-note">加载中…</text>
  <button v-else-if="error" class="link-retry" @click="load(rows.length ? page + 1 : 0)">
    {{ error }}
  </button>
  <text v-else-if="!rows.length" class="link-note">没有可关联的支出流水</text>
  <button v-else-if="rows.length < total" class="link-retry" @click="load(page + 1)">
    加载更多
  </button>
  <text v-if="notice" class="link-note" role="status">{{ notice }}</text>
  <button class="link-submit" :disabled="!chosen || loading || saving" @click="confirm">
    {{ saving ? '正在关联…' : '确认关联' }}
  </button>
</template>

<style scoped>
.link-search {
  display: flex;
  align-items: center;
  gap: 8px;
  border-radius: 24px;
  padding: 0 14px;
  margin: 14px 0;
  min-height: 44px;
  background: white;
}
.link-search input {
  min-width: 0;
  flex: 1;
  font-size: 14px;
}
.link-clear,
.link-retry {
  background: transparent;
  border: 0;
  color: #176a57;
}
.link-clear {
  margin: 0;
  padding: 0;
  width: 32px;
  line-height: 40px;
}
.link-list {
  border-radius: 14px;
  overflow: hidden;
}
.link-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-height: 51px;
  margin: 0;
  padding: 10px 14px;
  border: 0;
  border-bottom: 1px solid #edf1ee;
  border-radius: 0;
  background: white;
  text-align: left;
  font-size: 14px;
  line-height: 1.45;
}
.link-row.chosen {
  background: #e7f1eb;
}
.link-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}
.link-caption {
  display: block;
  color: #607267;
  font-size: 12px;
  margin-top: 3px;
}
.link-amount {
  color: #176a57;
  flex: none;
}
.link-note {
  display: block;
  margin: 16px 0;
  text-align: center;
  color: #607267;
  font-size: 14px;
}
.link-retry {
  margin: 12px auto;
  font-size: 14px;
}
.link-submit {
  margin-top: 16px;
  min-height: 44px;
  line-height: 44px;
  border: 0;
  border-radius: 999px;
  background: #176a57;
  color: white;
  font-size: 15px;
}
.link-submit[disabled] {
  opacity: 0.55;
}
.link-row::after,
.link-submit::after,
.link-clear::after,
.link-retry::after {
  border: 0;
}
</style>
