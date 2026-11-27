<script setup lang="ts">
import { ref, watch } from 'vue';
import type { GiftContact } from '../../api/ledger-gifts';
import type { RentalLandlordDraft } from './rental-landlord';
import { useLedgerContactMatches } from './ledger-contact-matches';

const props = defineProps<{
  ledgerId: string;
  modelValue: RentalLandlordDraft;
  disabled?: boolean;
}>();
const emit = defineEmits<{ 'update:modelValue': [draft: RentalLandlordDraft] }>();
const focused = ref(false);
const { matches, count, pending, failure, search, more, retry } = useLedgerContactMatches(
  () => props.ledgerId,
);

function choose(item: GiftContact) {
  emit('update:modelValue', { name: item.name, contactId: item.id, distinction: item.distinction });
  focused.value = false;
}

function typeName(name: string) {
  emit('update:modelValue', { name, contactId: null, distinction: '', checking: !!name.trim() });
  search(name, (result) => {
    if (
      props.modelValue.name.trim() !== name.trim() ||
      props.modelValue.sameName ||
      props.modelValue.contactId
    )
      return;
    if (result.totalCount === 1) {
      const item = result.entries[0];
      emit('update:modelValue', { name, contactId: item.id, distinction: item.distinction });
    } else {
      emit('update:modelValue', {
        name,
        contactId: null,
        distinction: '',
        ambiguous: result.totalCount > 1,
      });
    }
  });
}
function addSameName() {
  emit('update:modelValue', {
    name: props.modelValue.name,
    contactId: null,
    distinction: '',
    sameName: true,
  });
  focused.value = false;
}
watch(
  () => props.ledgerId,
  () => {
    const prior = props.modelValue;
    if (prior.name && !prior.contactId && !prior.sameName) typeName(prior.name);
    else search(prior.name);
  },
  { immediate: true },
);
</script>

<template>
  <view class="landlord-field">
    <text class="landlord-label">出租方<text class="landlord-required">*</text></text>
    <view class="landlord-input">
      <input
        :value="modelValue.name"
        aria-label="出租方"
        role="combobox"
        :aria-expanded="focused && count > 1 && !modelValue.contactId && !modelValue.sameName"
        :disabled="disabled"
        maxlength="60"
        placeholder="输入姓名或机构全称"
        @focus="focused = true"
        @blur="focused = false"
        @input="
          focused = true;
          typeName($event.detail.value);
        "
      />
      <button
        v-if="count > 0"
        class="landlord-same"
        :disabled="disabled"
        @click="modelValue.sameName ? typeName(modelValue.name) : addSameName()"
      >
        {{ modelValue.sameName ? '取消新增' : '新增同名' }}
      </button>
      <button
        v-if="modelValue.name"
        class="landlord-clear"
        aria-label="清空出租方"
        :disabled="disabled"
        @click="typeName('')"
      >
        ⊗
      </button>
      <view
        v-if="focused && count > 1 && !modelValue.contactId && !modelValue.sameName"
        class="landlord-matches"
        role="listbox"
      >
        <button
          v-for="item in matches"
          :key="item.id"
          class="landlord-match"
          role="option"
          @mousedown.prevent
          @touchstart.stop.prevent="choose(item)"
          @click="choose(item)"
        >
          <text>{{ item.name }}</text><text class="landlord-muted">{{
            item.distinction || '无称呼'
          }}</text>
        </button>
        <button
          v-if="matches.length < count"
          class="landlord-same"
          :disabled="pending"
          @mousedown.prevent
          @click="more"
        >
          加载更多
        </button>
      </view>
    </view>
  </view>
  <text v-if="modelValue.contactId && modelValue.distinction" class="landlord-context">{{
    modelValue.distinction
  }}</text>
  <view v-if="modelValue.sameName" class="landlord-field">
    <text class="landlord-label">称呼或关系<text class="landlord-required">*</text></text>
    <input
      :value="modelValue.distinction"
      aria-label="出租方称呼或关系"
      :disabled="disabled"
      maxlength="60"
      placeholder="如：原房东、二房东"
      @input="emit('update:modelValue', { ...modelValue, distinction: $event.detail.value })"
    />
  </view>
  <view v-if="failure" class="landlord-load-error">
    <text>联系人查询失败</text><button @click="retry">重试</button>
  </view>
</template>

<style scoped>
.landlord-field {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: 8px;
  padding: 10px 13px;
  box-sizing: border-box;
  border-bottom: 1px solid #edf1ef;
}
.landlord-label {
  flex: 0 0 86px;
}
.landlord-required {
  color: #ad392d;
  margin-left: 2px;
}
.landlord-input {
  flex: 1;
  min-width: 0;
  display: flex;
  gap: 6px;
  align-items: center;
  position: relative;
}
.landlord-field input {
  flex: 1;
  min-width: 0;
  min-height: 23px;
  line-height: 23px;
}
.landlord-input button,
.landlord-load-error button {
  margin: 0;
  padding: 0 3px;
  font: inherit;
  color: #176a57;
  background: transparent;
  border: 0;
  white-space: nowrap;
}
.landlord-input button::after,
.landlord-load-error button::after {
  border: 0;
}
.landlord-input .landlord-same {
  font-size: 12px;
}
.landlord-input .landlord-clear {
  color: #61736b;
  min-height: 28px;
  line-height: 28px;
}
.landlord-matches {
  position: absolute;
  z-index: 4;
  inset-inline: 0;
  top: calc(100% + 5px);
  max-height: 240px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e4ece7;
  border-radius: 11px;
  box-shadow: 0 8px 18px rgba(20, 56, 45, 0.13);
}
.landlord-matches .landlord-match {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  min-height: 38px;
  padding: 8px;
  text-align: left;
  overflow-wrap: anywhere;
  white-space: normal;
}
.landlord-muted,
.landlord-context {
  color: #61736b;
  font-size: 12px;
}
.landlord-context {
  display: block;
  margin: 6px 13px 7px 107px;
}
.landlord-load-error {
  display: flex;
  gap: 8px;
  padding: 6px 13px;
  color: #ad392d;
  font-size: 13px;
}
</style>
