<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import type { DraftFields, ExpenseDraft } from '../../api/assistant';
import type { LedgerCategory } from '../../api/ledger-categories';
import type { LedgerAccount } from '../../api/ledger-accounts';
import { formatCents, parseCents } from '../ledger/transaction';

const props = defineProps<{
  draft: ExpenseDraft;
  accounts: LedgerAccount[];
  categories: LedgerCategory[];
  name: string;
  busy: boolean;
  uncertain: boolean;
  writable: boolean;
}>();
const emit = defineEmits<{
  edit: [fields: DraftFields];
  finish: [action: 'confirm' | 'discard'];
  recover: [];
}>();
const editing = ref(false),
  amount = ref(''),
  fields = ref<DraftFields>({ ...props.draft.fields }),
  error = ref('');
const category = computed(
  () => props.categories.find((c) => c.code === fields.value.categoryCode)?.name,
);
const account = computed(() => props.accounts.find((a) => a.id === fields.value.accountId)?.name);
// 每次刷新以服务端版本及已保存字段为准。
watch(
  () => props.draft,
  (d) => {
    fields.value = { ...d.fields };
    amount.value = d.fields.amountCents == null ? '' : (d.fields.amountCents / 100).toFixed(2);
    editing.value = false;
    error.value = '';
  },
  { immediate: true },
);
function save() {
  const cents = amount.value.trim() ? parseCents(amount.value) : null;
  if (amount.value.trim() && !cents) {
    error.value = '请输入有效金额，最多两位小数。';
    return;
  }
  emit('edit', { ...fields.value, amountCents: cents });
}
function closeEditor() {
  fields.value = { ...props.draft.fields };
  amount.value =
    fields.value.amountCents == null ? '' : (fields.value.amountCents / 100).toFixed(2);
  editing.value = false;
  error.value = '';
}
</script>
<template>
  <view class="draft">
    <text class="title">待确认支出 · {{ name }}</text>
    <template v-if="editing">
      <label>金额（元）<input v-model="amount" type="digit" placeholder="请输入金额" /></label>
      <picker
        :range="categories"
        range-key="name"
        @change="fields.categoryCode = categories[Number($event.detail.value)].code"
      >
        <view>分类　{{ category || '选择分类' }} ›</view></picker
      >
      <picker
        mode="date"
        start="1900-01-01"
        :value="fields.bookedOn"
        @change="fields.bookedOn = $event.detail.value"
        ><view>日期　{{ fields.bookedOn }} ›</view></picker
      >
      <picker
        :range="['不选账户', ...accounts.map((a) => a.name)]"
        @change="fields.accountId = accounts[Number($event.detail.value) - 1]?.id ?? null"
        ><view>账户　{{ account || '不选账户' }} ›</view></picker
      >
      <label>备注<input v-model="fields.note" maxlength="100" /></label>
      <text v-if="error">{{ error }}</text>
      <button :disabled="busy" @click="closeEditor">取消修改</button
      ><button :disabled="busy || !writable" @click="save">更新草稿</button>
    </template>
    <template v-else>
      <text class="amount">{{
        draft.fields.amountCents == null ? '待补金额' : '¥' + formatCents(draft.fields.amountCents)
      }}</text>
      <text
        >{{ category || '待选分类' }} · {{ draft.fields.bookedOn }} ·
        {{ account || '未选账户' }}</text
      >
      <text v-if="draft.fields.note">{{ draft.fields.note }}</text>
      <button v-if="uncertain" :disabled="busy" @click="emit('recover')">核对草稿状态</button>
      <view v-else-if="writable"
        ><button :disabled="busy" @click="editing = true">修改</button>
        <button :disabled="busy" @click="emit('finish', 'discard')">放弃</button>
        <button
          class="primary"
          :disabled="busy || !draft.fields.amountCents || !draft.fields.categoryCode"
          @click="emit('finish', 'confirm')"
        >
          确认记账
        </button></view
      >
    </template>
  </view>
</template>
<style scoped>
.draft {
  margin: 12px 16px;
  padding: 14px;
  background: #fff;
  border-radius: 14px;
  color: #253b31;
}
.draft text,
.draft label,
.draft picker {
  display: block;
  margin: 7px 0;
}
.draft label,
.draft picker {
  padding: 6px 0;
  border-bottom: 1px solid #e3e9e1;
}
.title {
  font-size: 13px;
}
.amount {
  font-size: 24px;
  font-weight: 650;
}
.draft button {
  display: inline-block;
  color: #285841;
  padding: 7px 12px;
  margin: 4px 4px 0 0;
  font-size: 14px;
  line-height: 1.6;
  border-radius: 10px;
}
.draft .primary {
  background: #27543e;
  color: white;
}
</style>
