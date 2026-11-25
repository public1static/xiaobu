<script setup>
import FieldClear from './FieldClear.vue';
import { computed, ref } from 'vue';
import { ActionSheet as VanActionSheet, Icon as VanIcon } from 'vant';
import { amountText } from './ledger.mjs';
const props = defineProps({ options: Array, selected: Object, date: String, hasPlan: Boolean });
const emit = defineEmits(['choose']);
const open = ref(false),
  trigger = ref(null);
// 当前付款月份优先，其他账期按与付款月的距离展示。
const monthIndex = (m) => Number(m.slice(0, 4)) * 12 + Number(m.slice(5, 7));
const choices = computed(() => {
  const anchor = monthIndex(props.date);
  return [...props.options].sort(
    (a, b) =>
      Math.abs(monthIndex(a.month) - anchor) - Math.abs(monthIndex(b.month) - anchor) ||
      a.due.localeCompare(b.due),
  );
});
const selectedItem = computed(() => choices.value.find(isSelected));
// 同名房屋也按账单编号和月份区分。
function isSelected(item) {
  return item.billId === props.selected?.billId && item.month === props.selected?.month;
}
// 只提交草稿选择，关闭面板不会改变任何流水。
function choose(item) {
  emit('choose', item);
  open.value = false;
}
</script>
<template>
  <div class="picker-with-clear">
    <button
      ref="trigger"
      class="rent-trigger pilot-entry-meta__row"
      type="button"
      aria-haspopup="dialog"
      @click="open = true"
    >
      <span>关联租房账单</span
      ><span class="picker-display" :class="{ 'is-placeholder': !selectedItem }"
        ><span class="picker-text">{{
          selectedItem
            ? selectedItem.home + ' · ' + selectedItem.month
            : selected
              ? '重新选择'
              : '选择账期'
        }}</span
        ><VanIcon name="arrow"
      /></span>
    </button>
    <FieldClear
      v-if="selected"
      label="关联租房账单"
      @clear="
        choose(null);
        trigger?.focus({ preventScroll: true });
      "
    />
  </div>
  <VanActionSheet
    v-model:show="open"
    teleport="body"
    title="关联租房账单"
    :style="{ maxWidth: '440px', left: 0, right: 0, marginInline: 'auto' }"
    @closed="trigger?.focus({ preventScroll: true })"
  >
    <div class="rent-choices">
      <button
        v-for="item in choices"
        :key="item.billId + item.month"
        type="button"
        :aria-pressed="isSelected(item)"
        @click="choose(item)"
      >
        <span
          ><strong>{{ item.home }}</strong
          ><small>{{ item.month }} 账期 · {{ item.due.slice(5) }} 到期</small></span
        ><b>¥{{ amountText(item.amount) }}</b
        ><VanIcon v-if="isSelected(item)" name="success" />
      </button>
      <p v-if="!choices.length">
        {{ hasPlan ? '暂无待付租房账单' : '请先在「租房 → 合同」中设置房租账单' }}
      </p>
      <button v-if="selected" type="button" class="clear" @click="choose(null)">清除关联</button>
    </div>
  </VanActionSheet>
</template>
<style scoped>
.rent-trigger {
  width: 100%;
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  gap: var(--field-gap);
  align-items: center;
  padding: 9px 13px;
  min-height: 46px;
  text-align: left;
  background: transparent;
  color: inherit;
  border: 0;
  border-bottom: 1px solid var(--entry-rule);
  font: inherit;
  font-size: 14px;
}
.rent-choices button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.rent-choices {
  padding: 0 16px 16px;
}
.rent-choices button {
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--ink);
  font: inherit;
  padding: 12px 8px;
  border-bottom: 1px solid var(--line);
}
.rent-choices button[aria-pressed='true'] {
  background: var(--mint);
  color: var(--green);
  border-radius: 10px;
}
.rent-choices strong,
.rent-choices small {
  display: block;
}
.rent-choices small,
.rent-choices p {
  color: var(--muted);
  font-size: 13px;
}
.rent-choices b {
  margin-left: auto;
  font-variant-numeric: tabular-nums;
}
.rent-choices p {
  text-align: center;
  padding: 14px;
}
.rent-choices .clear {
  justify-content: center;
  color: var(--green);
}
</style>
