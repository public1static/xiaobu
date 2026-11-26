<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  changeLedgerMemberRole,
  listLedgerMembers,
  removeLedgerMember,
  type LedgerMember,
} from '../../api/ledger-members';

const props = defineProps<{ ledgerId: string }>();
const emit = defineEmits<{ (event: 'invite' | 'close'): void }>();
const members = ref<LedgerMember[]>([]);
const busy = ref(false);
const loaded = ref(false);
const error = ref('');
const pendingRemoval = ref<LedgerMember | null>(null);

async function refresh() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    members.value = await listLedgerMembers(props.ledgerId);
    loaded.value = true;
  } catch {
    error.value = '成员列表加载失败，请重试。';
  } finally {
    busy.value = false;
  }
}

async function toggleRole(member: LedgerMember) {
  if (busy.value) return;
  const next = member.role === 'READ_ONLY' ? 'CAN_RECORD' : 'READ_ONLY';
  busy.value = true;
  error.value = '';
  try {
    await changeLedgerMemberRole(props.ledgerId, member.memberId, next);
    members.value = members.value.map((entry) =>
      entry.memberId === member.memberId ? { ...entry, role: next } : entry,
    );
  } catch {
    error.value = '权限修改未确认，请刷新成员列表。';
  } finally {
    busy.value = false;
  }
}

async function confirmRemoval() {
  if (busy.value || !pendingRemoval.value) return;
  const memberId = pendingRemoval.value.memberId;
  busy.value = true;
  error.value = '';
  try {
    await removeLedgerMember(props.ledgerId, memberId);
    members.value = members.value.filter((entry) => entry.memberId !== memberId);
    pendingRemoval.value = null;
  } catch {
    error.value = '移除未确认，请刷新成员列表。';
    pendingRemoval.value = null;
  } finally {
    busy.value = false;
  }
}

onMounted(refresh);
</script>

<template>
  <view class="overlay">
    <view class="sheet login-sheet" role="dialog" aria-label="成员管理" aria-modal="true">
      <view class="sheet-head">
        <text>成员管理</text>
        <button class="close" aria-label="关闭成员管理" :disabled="busy" @click="emit('close')">
          ×
        </button>
      </view>
      <text v-if="!loaded && busy" class="preview-label" role="status">正在加载成员</text>
      <text v-if="loaded && !members.length" class="preview-label">还没有加入的成员</text>
      <view v-for="member in members" :key="member.memberId" class="member-row">
        <view class="member-name">
          <text>{{ member.displayName }}</text>
          <text class="preview-label">{{ member.role === 'CAN_RECORD' ? '可记账' : '只读' }}</text>
        </view>
        <view v-if="!pendingRemoval" class="member-actions">
          <button class="text-button" :disabled="busy" @click="toggleRole(member)">
            {{ member.role === 'READ_ONLY' ? '设为可记账' : '改为只读' }}
          </button>
          <button
            class="text-button member-danger"
            :disabled="busy"
            @click="pendingRemoval = member"
          >
            移除
          </button>
        </view>
      </view>
      <view v-if="pendingRemoval" class="member-confirm">
        <text
          >移除{{ pendingRemoval.displayName }}后，对方无法继续查看本账本，已记流水会保留。</text
        >
        <button class="primary" :disabled="busy" @click="confirmRemoval">确认移除</button>
        <button class="primary secondary" :disabled="busy" @click="pendingRemoval = null">
          取消
        </button>
      </view>
      <button v-if="!pendingRemoval" class="primary" :disabled="busy" @click="emit('invite')">
        邀请只读成员
      </button>
      <text v-if="error" class="error" role="alert">{{ error }}</text>
      <button v-if="error && !pendingRemoval" class="text-button" :disabled="busy" @click="refresh">
        刷新成员列表
      </button>
    </view>
  </view>
</template>

<style scoped src="./ledger.css"></style>
<style scoped>
.member-row,
.member-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.member-row {
  padding: 8px 10px;
  border: 1px solid #e4eae5;
  border-radius: 10px;
}
.member-name,
.member-confirm {
  display: flex;
  flex-direction: column;
  gap: 5px;
}
.member-danger {
  color: #a23832;
}
.member-confirm {
  gap: 10px;
  color: #536660;
  font-size: 14px;
}
</style>
