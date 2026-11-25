<script setup>
import SlotField from './SlotField.vue';
import { inject, computed, nextTick, ref, watch } from 'vue';
import { Icon as VanIcon } from 'vant';
import {
  householdOptions,
  resolveHousehold,
  preparePerson,
  normalizeContact,
  readGiftContacts,
  sameNameContacts,
} from './gift-options.mjs';
const bookStorage = inject('bookStorage');

const props = defineProps({
  label: { type: String, required: true },
  required: Boolean,
  rental: Boolean,
  family: Boolean,
  contacts: { type: Array, default: () => [] },
});
const model = defineModel({ type: Object, required: true });
const picker = ref(null),
  input = ref(null),
  distinctionInput = ref(null);
const open = ref(false),
  active = ref(-1),
  creating = ref(false),
  distinction = ref('');
const fieldError = ref(''),
  distinctionError = ref('');
const directory = ref([]);
let composing = false;
const storage = () => {
  try {
    return bookStorage;
  } catch {
    return null;
  }
};
const refresh = () => {
  const people = readGiftContacts(storage(), props.contacts);
  directory.value = props.family ? householdOptions(people) : people;
};
watch(() => props.contacts, refresh, { immediate: true });
const name = computed(() => model.value?.name || '');
// 只有多个完整同名的候选人才需要用户区分。
const found = computed(() => sameNameContacts(directory.value, name.value));
// 用亲友资料说明同名对象，候选和选中后共用相同的文字。
function contactDescription(item) {
  if (!item) return '';
  return [
    ...new Set(
      [
        item.distinction,
        item.household,
        item.householdNote,
        item.village,
        item.aliases?.length ? '别名：' + item.aliases.join('、') : '',
      ].filter(Boolean),
    ),
  ].join(' · ');
}
const selectedDetail = computed(() => {
  const item = model.value?.id && directory.value.find((person) => person.id === model.value.id);
  return (
    contactDescription(item || (found.value.length === 1 ? found.value[0] : null)) ||
    model.value?.distinction ||
    ''
  );
});
const inputError = computed(() => !creating.value && fieldError.value);
// 仅在有待确认的搜索词时展开候选浮层。
function openList() {
  if (creating.value || composing || model.value?.mode !== 'typed') return;
  refresh();
  open.value = found.value.length > 1;
}
function closeList() {
  open.value = false;
  active.value = -1;
}
function focus() {
  nextTick(() => input.value?.focus());
}
// 输入和清空搜索词时同步候选浮层。
function write(value) {
  if (composing || value === name.value) return;
  model.value = {
    name: value,
    id: '',
    distinction: '',
    mode: normalizeContact(value) ? 'typed' : 'none',
  };
  fieldError.value = '';
  distinctionError.value = '';
  creating.value = false;
  open.value = sameNameContacts(directory.value, value).length > 1;
  active.value = -1;
}
function choose(item) {
  model.value = {
    name: item.name,
    id: item.id,
    distinction: item.distinction || '',
    mode: 'existing',
  };
  creating.value = false;
  distinction.value = '';
  fieldError.value = '';
  distinctionError.value = '';
  input.value?.focus();
  closeList();
}
function clear() {
  model.value = { name: '', id: '', distinction: '', mode: 'none' };
  creating.value = false;
  distinction.value = '';
  fieldError.value = '';
  distinctionError.value = '';
  input.value?.focus();
  closeList();
}
function beginCreate() {
  if (!normalizeContact(name.value)) return;
  closeList();
  fieldError.value = '';
  distinctionError.value = '';
  distinction.value = '';
  creating.value = true;
  nextTick(() => distinctionInput.value?.focus());
}
function cancelCreate() {
  creating.value = false;
  distinction.value = '';
  distinctionError.value = '';
  fieldError.value = '';
  input.value?.focus();
  openList();
}
function onBlur(event) {
  if (event.relatedTarget && !picker.value?.contains(event.relatedTarget)) closeList();
}
function keydown(event) {
  if (event.isComposing || composing || event.keyCode === 229) return;
  if (event.key === 'Escape' && (open.value || creating.value)) {
    event.preventDefault();
    event.stopPropagation();
    if (creating.value) cancelCreate();
    else closeList();
    return;
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    const len = found.value.length;
    if (len < 2 || creating.value) return;
    event.preventDefault();
    openList();
    active.value =
      active.value < 0
        ? event.key === 'ArrowDown'
          ? 0
          : len - 1
        : (active.value + (event.key === 'ArrowDown' ? 1 : -1) + len) % len;
    nextTick(() =>
      picker.value
        ?.querySelector('#pilot-contact-option-' + active.value)
        ?.scrollIntoView({ block: 'nearest' }),
    );
  } else if (event.key === 'Enter' && (open.value || creating.value)) {
    event.preventDefault();
    if (found.value[active.value] && !creating.value) choose(found.value[active.value]);
    else if (!creating.value && model.value?.mode === 'typed')
      fieldError.value = props.family ? '请选择同名家庭' : '请选择同名联系人。';
    else if (!creating.value) closeList();
  }
}
// 先生成提交快照；取消、金额无效或存储失败都不留下独立的新联系人。
function prepare() {
  if (composing) {
    focus();
    return null;
  }
  if (!creating.value && model.value?.mode === 'legacy') return { contact: model.value };
  if (!name.value.trim()) return { contact: { name: '', id: '', distinction: '' } };
  refresh();
  let result;
  try {
    result = props.family
      ? resolveHousehold(
          readGiftContacts(storage(), props.contacts),
          model.value,
          creating.value,
          distinction.value,
        )
      : preparePerson(storage(), model.value, {
          rental: props.rental,
          createSame: creating.value,
          distinction: distinction.value,
        });
  } catch {
    result = { error: '联系人目录无法读取，请重试。' };
  }
  if (result.error) {
    if (creating.value) {
      distinctionError.value = result.error;
      nextTick(() => distinctionInput.value?.focus());
    } else {
      fieldError.value = result.error;
      open.value = !!result.ambiguous;
      focus();
    }
    return null;
  }
  return result;
}
defineExpose({ prepare, focus });
</script>

<template>
  <div ref="picker" class="pilot-entry-contact" @focusout="onBlur">
    <label for="pilot-entry-contact"
      >{{ label }}<span v-if="required" class="field-mark" aria-hidden="true">*</span></label
    >
    <SlotField
      name="person"
      :model-value="[name, model.id, creating].join('|')"
      :error-message="inputError || ''"
      :rules="[
        { validator: () => !required || !!name.trim(), message: '请输入' + label },
        {
          validator: () => creating || model.mode !== 'typed' || found.length < 2,
          message: family ? '请选择同名家庭' : '请选择同名联系人',
        },
      ]"
      ><div class="pilot-entry-contact__input" :class="{ 'contact-invalid': inputError }">
        <input
          id="pilot-entry-contact"
          ref="input"
          :value="name"
          maxlength="40"
          type="text"
          :placeholder="family ? '输入家庭名称' : rental ? '输入姓名或机构全称' : '输入姓名'"
          autocomplete="off"
          :aria-required="required"
          role="combobox"
          aria-autocomplete="list"
          aria-controls="pilot-contact-menu"
          :aria-expanded="open"
          :aria-invalid="!!inputError"
          :aria-activedescendant="
            open && active >= 0 ? 'pilot-contact-option-' + active : undefined
          "
          @focus="openList"
          @click="openList"
          @keydown="keydown"
          @input="write($event.target.value)"
          @compositionstart="
            composing = true;
            closeList();
          "
          @compositionend="
            composing = false;
            write($event.target.value);
          "
        />
        <button
          v-if="found.length"
          type="button"
          class="contact-same"
          @click="creating ? cancelCreate() : beginCreate()"
        >
          {{ creating ? '取消新增' : '新增同名' }}
        </button>
        <button
          v-if="name || model?.id"
          type="button"
          class="pilot-entry-contact__clear"
          aria-label="清空对象"
          @click="clear"
        >
          <VanIcon name="clear" />
        </button></div
    ></SlotField>
    <small v-if="selectedDetail && !open && !creating" class="pilot-entry-person-meta">{{
      selectedDetail
    }}</small>

    <div v-show="open" class="pilot-entry-contact__panel">
      <div
        id="pilot-contact-menu"
        class="pilot-entry-contact__options"
        role="listbox"
        :aria-label="family ? '家庭候选' : '联系人候选'"
      >
        <button
          v-for="(item, i) in found"
          :id="'pilot-contact-option-' + i"
          :key="item.id"
          tabindex="-1"
          type="button"
          role="option"
          :class="{ 'contact-active': active === i }"
          :aria-selected="model?.id === item.id"
          @pointerdown.prevent
          @click="choose(item)"
        >
          <span>{{ item.name }}</span><small v-if="contactDescription(item)">{{
            contactDescription(item)
          }}</small>
        </button>
      </div>
    </div>

    <div v-if="creating" class="pilot-entry-contact__new">
      <label for="pilot-new-distinction"
        >{{ family ? '区分说明' : '称呼或关系'
        }}<span class="field-mark" aria-hidden="true">*</span></label
      >
      <SlotField
        name="distinction"
        :model-value="distinction"
        :error-message="distinctionError"
        :rules="[
          {
            validator: (v) => !!normalizeContact(v),
            message: family ? '请输入家庭区分说明' : '请输入称呼或关系',
          },
          {
            validator: (v) =>
              !found.some((p) => normalizeContact(p.distinction) === normalizeContact(v)),
            message: family ? '此说明已用于同名家庭' : '该称呼或关系已被同名联系人使用',
          },
        ]"
        ><input
          id="pilot-new-distinction"
          ref="distinctionInput"
          v-model="distinction"
          type="text"
          maxlength="40"
          autocomplete="off"
          :placeholder="family ? '如：东村' : '如：大学同学'"
          aria-required="true"
          :aria-invalid="!!distinctionError"
          @input="distinctionError = ''"
          @keydown.esc.stop.prevent="cancelCreate"
        />
      </SlotField>
    </div>
  </div>
</template>

<style scoped>
.pilot-entry-contact {
  position: relative;
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  align-items: center;
  gap: 0 var(--field-gap);
  padding: 2px 13px;
  min-width: 0;
  border-bottom: 1px solid var(--entry-rule);
}
.pilot-entry-contact > :nth-child(n + 3) {
  grid-column: 1/-1;
  margin-bottom: 8px;
}
.pilot-entry-contact label {
  display: block;
  margin: 0;
  font-size: 14px;
}
.pilot-entry-contact__input {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  border-radius: 8px;
  padding: 0;
  border: 0;
  color: var(--entry-muted);
}
.pilot-entry-contact__input input,
.pilot-entry-contact__new input {
  min-width: 0;
  width: 100%;
  height: 42px;
  border: 0;
  outline: none;
  color: var(--entry-ink);
  font: inherit;
  font-size: 0.9rem;
  background: transparent;
}
.pilot-entry-contact__input.contact-invalid,
.pilot-entry-contact__new input[aria-invalid='true'] {
  border-color: var(--entry-danger);
}
.pilot-entry-contact__clear {
  border: 0;
  width: 44px;
  min-height: 42px;
  color: var(--entry-muted);
  background: transparent;
}
.pilot-entry-contact .pilot-entry-contact__panel {
  position: absolute;
  bottom: 100%;
  left: 10px;
  right: 10px;
  z-index: 2;
  margin: 0;
  background: white;
  box-shadow: 0 6px 22px #18372d24;
  border: 1px solid var(--entry-rule);
  border-radius: 9px;
  margin-top: 5px;
  overflow: hidden;
}
.pilot-entry-contact__options {
  max-height: 160px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.pilot-entry-contact__options > button,
.pilot-entry-contact__create {
  width: 100%;
  min-height: 44px;
  padding: 7px 9px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--entry-ink);
  border: 0;
  background: white;
  font: inherit;
  font-size: 0.9rem;
  text-align: left;
  overflow-wrap: anywhere;
}
.pilot-entry-contact__options > button + button,
.pilot-entry-contact__create:not(:first-child) {
  border-top: 1px solid var(--entry-rule);
}
.pilot-entry-contact__options > button[aria-selected='true'],
.pilot-entry-contact__options > button.contact-active,
.pilot-entry-contact__options > button:hover {
  color: var(--entry-accent);
  background: #edf7f3;
}
.pilot-entry-contact__options small,
.pilot-entry-person-meta {
  color: var(--entry-muted);
  font-size: 0.82rem;
}
.pilot-entry-contact__options small {
  text-align: right;
}
.pilot-entry-person-meta {
  display: block;
  margin-top: 4px;
}
.pilot-entry-contact__panel > p {
  padding: 4px 9px;
}
.pilot-entry-contact__create {
  justify-content: flex-start;
  color: var(--entry-accent);
  font-weight: 630;
}
.pilot-entry-contact .pilot-entry-contact__new {
  display: grid;
  grid-template-columns: var(--field-label) minmax(0, 1fr);
  align-items: center;
  gap: 0 var(--field-gap);
  border-top: 1px solid var(--entry-rule);
  margin: 0;
}
.pilot-entry-contact__new label {
  margin: 0;
}
.pilot-entry-contact__new .pilot-contact-required {
  color: var(--entry-danger);
}
.pilot-entry-contact__new p {
  grid-column: 1/-1;
  padding-bottom: 6px;
}
.pilot-entry-contact-error {
  color: var(--entry-danger);
  margin: 5px 0 0;
  font-size: 0.825rem;
  line-height: 1.4;
}
.pilot-entry-contact__input input {
  flex: 1;
  width: 0;
}
.contact-same {
  flex: none;
  border: 0;
  background: transparent;
  color: var(--entry-accent);
  font: inherit;
  font-size: 0.8rem;
  padding: 0 3px;
  min-height: 40px;
}
</style>
