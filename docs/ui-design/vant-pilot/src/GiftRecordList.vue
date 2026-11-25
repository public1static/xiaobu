<script setup>
import { ref, watch } from 'vue';
import { Cell as VanCell, Empty as VanEmpty } from 'vant';
import { amountSigned } from './ledger.mjs';
const props = defineProps({ rows: Array, label: Function });
defineEmits(['row']);
// 在完整筛选结果上分段显示，切换筛选时从第一段开始。
const pageSize = 20,
  limit = ref(pageSize);
watch(
  () => props.rows,
  () => {
    limit.value = pageSize;
  },
);
</script>
<template>
  <VanCell
    v-for="row in rows.slice(0, limit)"
    :key="row.id"
    class="movement"
    :class="row.kind"
    :icon="row.kind === 'income' ? 'down' : 'back-top'"
    :title="[row.gift?.person || row.category, row.gift?.distinction].filter(Boolean).join(' · ')"
    :value="amountSigned(row)"
    :label="[row.date, label(row), row.gift?.event, row.note].filter(Boolean).join(' · ')"
    is-link
    @click="$emit('row', row.id)"
  />
  <VanEmpty v-if="!rows.length" description="暂无符合条件的往来" />
  <button v-if="rows.length > limit" class="more" @click="limit += pageSize">
    加载更多（{{ limit }} / {{ rows.length }}）
  </button>
</template>
<style scoped>
.movement :deep(.van-cell__left-icon) {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  margin-right: 10px;
  border-radius: 10px;
  background: #f4e7dc;
  color: #895c44;
}
.movement.income :deep(.van-cell__left-icon) {
  background: #e3eee4;
  color: var(--accent);
}
.movement :deep(.van-cell__value) {
  flex: 0 0 auto;
  font-weight: 650;
}
.movement.income :deep(.van-cell__value) {
  color: var(--accent);
}
.more {
  display: block;
  margin: 12px auto;
  padding: 9px 16px;
  color: var(--accent);
  background: transparent;
  border: 0;
}
</style>
