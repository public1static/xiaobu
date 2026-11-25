<script setup>
import FieldClear from './FieldClear.vue';
import GiftListDefaults from './GiftListDefaults.vue';
import { computed, inject, nextTick, ref } from 'vue';
import {
  Form as VanForm,
  Popup as VanPopup,
  Icon as VanIcon,
  Field as VanField,
  Button as VanButton,
  Search as VanSearch,
} from 'vant';
import AccountsPage from './AccountsPage.vue';
import { normalizeContact } from './gift-options.mjs';
const props = defineProps({
  modelValue: String,
  resource: String,
  label: String,
  kind: String,
  scope: String,
  day: String,
  families: Array,
  exclude: String,
  required: Boolean,
});
const emit = defineEmits(['update:modelValue', 'list-defaults']);
const directory = inject('choiceDirectory'),
  writable = inject('bookWritable');
const open = ref(false),
  adding = ref(false),
  query = ref(''),
  name = ref(''),
  date = ref(''),
  reason = ref('');
const trigger = ref(null);
const noun = computed(() => ({ account: '账户', list: '礼单', family: '家庭' })[props.resource]);
const emptyLabel = computed(() =>
  props.resource === 'list'
    ? props.kind === 'income'
      ? '日常收礼'
      : '日常送礼'
    : props.resource === 'family'
      ? '未设置'
      : '选择账户',
);
// 选择已有记录时保留原先的停用账户，转账两端始终互斥。
const groups = computed(() => {
  if (props.resource === 'family') {
    const names = new Set([...props.families, ...(props.modelValue ? [props.modelValue] : [])]);
    return [['', [...names].map((n) => ({ value: n, name: n, icon: 'friends-o' }))]];
  }
  const lists = directory.lists.value.filter(
    (l) => !l.archived && l.kind === props.kind && (l.scope || 'personal') === props.scope,
  );
  return [
    ['', lists.map((l) => ({ value: l.id, name: l.name, detail: l.date, icon: 'records-o' }))],
  ];
});
const display = computed(() =>
  props.resource === 'account'
    ? props.modelValue || emptyLabel.value
    : groups.value.flatMap(([, items]) => items).find((i) => i.value === props.modelValue)?.name ||
      emptyLabel.value,
);
const matches = computed(() =>
  groups.value
    .map(([group, items]) => [
      group,
      items.filter((i) =>
        normalizeContact([group, i.name, i.detail].join(' ')).includes(
          normalizeContact(query.value),
        ),
      ),
    ])
    .filter(([, items]) => items.length),
);
// 每次从字段打开都先显示已选项，取消创建仅丢弃弹层内的草稿。
function show() {
  query.value = '';
  adding.value = false;
  open.value = true;
}
function start() {
  name.value = query.value.trim();
  date.value = '';
  reason.value = '';
  adding.value = true;
}
function choose(value) {
  if (props.resource === 'list' && value !== props.modelValue) {
    const lists = directory.lists.value;
    emit(
      'list-defaults',
      lists.find((l) => l.id === value),
      lists.find((l) => l.id === props.modelValue),
    );
  }
  emit('update:modelValue', value);
  open.value = false;
}
// 礼单继承收送方向；家庭名称随亲友资料保存。
function save() {
  if (!writable.value) return;
  const n = name.value.trim();
  if (props.resource === 'list') {
    const id = crypto.randomUUID();
    if (
      directory.createGiftList({
        id,
        name: n,
        scope: props.scope,
        date: date.value,
        defaultReason: reason.value,
        kind: props.kind,
      })
    )
      choose(id);
  } else {
    choose(props.families.find((f) => normalizeContact(f) === normalizeContact(n)) || n);
  }
}
</script>
<template>
  <div class="picker-with-clear">
    <button
      ref="trigger"
      type="button"
      class="choice-trigger"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="show"
    >
      <span>{{ label }}<span v-if="required" class="field-mark" aria-label="必填">*</span></span
      ><span
        class="picker-display"
        :class="{ 'is-placeholder': resource === 'account' && !modelValue }"
        ><span class="picker-text">{{ display }}</span
        ><VanIcon name="arrow"
      /></span>
    </button>
    <FieldClear
      v-if="!required && modelValue"
      :label="label"
      @clear="
        choose('');
        trigger?.focus({ preventScroll: true });
      "
    />
  </div>
  <VanPopup
    v-if="resource === 'account'"
    v-model:show="open"
    teleport="body"
    position="bottom"
    class="account-sheet"
    role="dialog"
    aria-modal="true"
    :aria-label="'选择' + label"
    @closed="nextTick(() => trigger?.focus({ preventScroll: true }))"
    @keydown.esc.stop="open = false"
  >
    <div v-if="open" class="business-body">
      <AccountsPage
        picking
        :book="directory.book.value"
        :accounts="directory.accounts.value"
        :write="directory.write"
        :chosen="modelValue"
        :required="required"
        :exclude="exclude"
        :label="label"
        @close="open = false"
        @choose="choose"
      />
    </div>
  </VanPopup>
  <VanPopup
    v-else
    v-model:show="open"
    teleport="body"
    round
    position="bottom"
    class="choice-sheet"
    role="dialog"
    aria-modal="true"
    :aria-label="(adding ? '新增' : '选择') + noun"
    @closed="nextTick(() => trigger?.focus({ preventScroll: true }))"
    @keydown.esc.stop="open = false"
  >
    <header>
      <button
        type="button"
        :aria-label="adding ? '返回选择' : '关闭'"
        @click="adding ? (adding = false) : (open = false)"
      >
        <VanIcon :name="adding ? 'arrow-left' : 'cross'" /></button
      ><strong>{{ adding ? '新增' + noun : '选择' + label }}</strong
      ><button v-if="!adding && writable" type="button" @click="start">新增</button>
    </header>
    <template v-if="!adding">
      <VanSearch
        v-if="resource === 'account' || groups.some(([, items]) => items.length > 8)"
        v-model="query"
        :placeholder="'搜索' + noun"
        clearable
      />
      <div class="choices">
        <button
          v-if="!required && !query"
          class="option"
          type="button"
          :aria-pressed="!modelValue"
          @click="choose('')"
        >
          {{ emptyLabel }}<VanIcon v-if="!modelValue" name="success" />
        </button>
        <section v-for="[group, items] in matches" :key="group">
          <h3 v-if="group">{{ group }}</h3>
          <button
            v-for="item in items"
            :key="item.value"
            type="button"
            class="option"
            :aria-pressed="modelValue === item.value"
            @click="choose(item.value)"
          >
            <i :style="{ '--tone': item.color || 'var(--green)' }"><VanIcon :name="item.icon" /></i
            ><span
              >{{ item.name }}<small v-if="item.detail">{{ item.detail }}</small></span
            ><VanIcon v-if="modelValue === item.value" name="success" />
          </button>
        </section>
        <p v-if="!matches.length && (query || required)">没有符合条件的{{ noun }}</p>
      </div>
    </template>
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-else
      class="choice-create"
      @submit="save"
    >
      <VanField
        name="name"
        :rules="[{ required: true, formatter: (v) => v.trim(), message: '请输入' + noun + '名称' }]"
        v-model="name"
        :label="noun + '名称'"
        maxlength="40"
        :placeholder="'输入' + noun + '名称'"
        required
      />
      <GiftListDefaults v-if="resource === 'list'" v-model:date="date" v-model:reason="reason" />
      <VanButton native-type="submit" type="primary" block round>{{
        resource === 'family' ? '使用此家庭' : '保存并选用'
      }}</VanButton>
    </VanForm>
  </VanPopup>
</template>
<style scoped>
.choice-trigger {
  width: 100%;
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  gap: var(--field-gap);
  align-items: center;
  min-height: 46px;
  padding: 10px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--ink);
  font: inherit;
  font-size: 14px;
  text-align: left;
}

.choice-sheet.van-popup {
  left: 0;
  right: 0;
  margin-inline: auto;
  width: min(100%, 560px);
  max-height: min(82dvh, 750px);
  display: flex;
  flex-direction: column;
  background: var(--page-bg);
  padding-bottom: env(safe-area-inset-bottom);
}
.choice-sheet header {
  flex: none;
  display: grid;
  grid-template-columns: 48px 1fr 48px;
  align-items: center;
  text-align: center;
  padding: 6px 12px;
  background: white;
}
.choice-sheet header button {
  min-height: 42px;
  background: transparent;
  color: var(--green);
  border: 0;
  font: inherit;
  font-size: 14px;
}
.choice-sheet h3 {
  font-size: 13px;
  color: var(--muted);
  margin: 14px 0 7px;
}
.choices,
.choice-create {
  overflow: auto;
  overscroll-behavior: contain;
  padding: 8px 16px 16px;
}
.option {
  width: 100%;
  min-height: 46px;
  display: flex;
  align-items: center;
  gap: 12px;
  border: 0;
  background: white;
  border-bottom: 1px solid var(--line);
  padding: 8px 12px;
  text-align: left;
  color: var(--ink);
  font: inherit;
  overflow-wrap: anywhere;
}
.option:first-of-type {
  border-radius: 12px 12px 0 0;
}
.option:last-of-type {
  border-radius: 0 0 12px 12px;
}
.option:only-of-type {
  border-radius: 12px;
}
.option > i:first-child:not(.van-icon) {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: color-mix(in srgb, var(--tone) 10%, white);
  color: var(--tone);
}
.option small {
  display: block;
  color: var(--muted);
  font-size: 12px;
}
.option > .van-icon {
  margin-left: auto;
}
.option[aria-pressed='true'],
.types button[aria-pressed='true'] {
  background: var(--mint);
  color: var(--green);
}
.types {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 5px;
}
.types button {
  min-height: 38px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: white;
  font: inherit;
  font-size: 14px;
}
.choice-create .van-button {
  margin-top: 16px;
}
.choice-create p {
  color: var(--danger);
}
.choices p {
  padding: 15px;
  color: var(--muted);
}

.account-sheet.van-popup {
  inset: 0;
  margin: auto;
  width: min(100%, 560px);
  height: 100dvh;
  max-height: 100dvh;
  background: var(--page-bg);
}
.account-sheet .van-search {
  padding: 0;
  background: transparent;
}
.account-sheet :deep(.van-search__content) {
  border-radius: 11px;
}
.choice-create :deep(.van-field) {
  padding-inline: 0;
  background: transparent;
}
.choice-create :deep(.van-cell::after) {
  left: 0;
  right: 0;
}
</style>
