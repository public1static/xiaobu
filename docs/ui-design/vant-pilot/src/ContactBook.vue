<script setup>
import RecordActions from './RecordActions.vue';
import OptionalField from './OptionalField.vue';
import { applyListDefaults } from './gift-options.mjs';
import GiftListDefaults from './GiftListDefaults.vue';
import DateField from './DateField.vue';
import SelectRow from './SelectRow.vue';
import { inject, computed, nextTick, ref, watch, onBeforeUnmount } from 'vue';
import {
  ActionSheet as VanActionSheet,
  Form as VanForm,
  showToast,
  Button as VanButton,
  Cell as VanCell,
  Empty as VanEmpty,
  Field as VanField,
  showConfirmDialog,
  Search as VanSearch,
  Icon as VanIcon,
} from 'vant';
import { pinyin } from 'pinyin-pro';
import ChoiceField from './ChoiceField.vue';
import GiftRecordList from './GiftRecordList.vue';
import GiftReasonPicker from './GiftReasonPicker.vue';
import { householdKey } from './gift-options.mjs';
import GiftPersonPicker from './GiftPersonPicker.vue';
import { validDay, localDay, amountSigned, amountText, select, totals } from './ledger.mjs';
import { editGiftContact, normalizeContact, addFriend } from './gift-options.mjs';
const bookStorage = inject('bookStorage');
const writable = inject('bookWritable');

const props = defineProps({
  defaultScope: String,
  scope: String,
  accounts: Array,
  contacts: Array,
  rows: Array,
  initialId: String,
  initialList: String,
  lists: Array,
  createList: Function,
  postGift: Function,
  mergeContacts: Function,
  deletePerson: Function,
});
const emit = defineEmits(['close', 'changed', 'row']);
// 直接点姓名时从详情进入，账本入口从业务列表进入。
const screen = ref(props.initialList ? 'register' : props.initialId ? 'person' : 'business');
const personId = ref(props.initialId);
const search = ref('');
// 表单草稿只在保存时写入联系人目录。
const name = ref(''),
  relation = ref(''),
  error = ref('');
const person = computed(() => props.contacts.find((item) => item.id === personId.value));
// 亲友详情保留按年度和方向选择的查询范围。
// 快速记账保留礼单和日期以连续登记。
const quick = ref(false);
const year = ref(''),
  flow = ref(''),
  aliasText = ref(''),
  village = ref(''),
  duplicate = ref(''),
  listQuery = ref('');
// 亲友按当前家庭归组，家庭视图包含成员各自的往来。
const familyPicker = ref(null),
  household = ref({ name: '', mode: 'none' }),
  familyView = ref(false);
const members = computed(() =>
  props.contacts.filter(
    (p) => person.value?.household && householdKey(p) === householdKey(person.value),
  ),
);
// 当前查看范围与正在登记的流水归属独立，全部范围允许选任一本账。
const range = ref(props.scope || 'all'),
  draftScope = ref('personal');
const allowedRows = computed(() =>
  props.rows.filter((row) => row.scope !== 'family' || !row.gift || row.gift.shared),
);
const records = computed(() => {
  const ids = new Set(
    familyView.value && members.value.length ? members.value.map((p) => p.id) : [personId.value],
  );
  return select(
    allowedRows.value.filter((row) => ids.has(row.gift?.contactId)),
    { scope: range.value },
  );
});
// 本地目录没有数据库，按姓名拼音分组并缓存读音供搜索使用。
const directory = computed(() =>
  props.contacts
    .map((item) => {
      const syllables = pinyin(item.name, { toneType: 'none', type: 'array' });
      const initials = syllables
        .map((word) => word[0])
        .join('')
        .toUpperCase();
      return {
        item,
        initial: /^[A-Z]/.test(initials) ? initials[0] : '#',
        terms: [
          item.name,
          item.distinction,
          item.household,
          item.householdNote,
          item.village,
          ...(item.aliases || []),
          syllables.join(''),
          initials,
        ],
      };
    })
    .sort(
      (a, b) =>
        Number(a.initial === '#') - Number(b.initial === '#') ||
        a.initial.localeCompare(b.initial) ||
        a.item.name.localeCompare(b.item.name, 'zh-CN-u-co-pinyin'),
    ),
);
const letter = ref('');
// 右侧索引只定位，不改变搜索结果；触摸滑动和键盘点击共用定位逻辑。
const alphabet = [...'ABCDEFGHIJKLMNOPQRSTUVWXYZ', '#'];
const root = ref(null),
  hint = ref('');
let hintTimer;
function locate(key) {
  const target = root.value?.querySelector('[data-initial="' + key + '"]');
  if (!target) return;
  letter.value = key;
  hint.value = key;
  target.scrollIntoView({ block: 'start' });
  clearTimeout(hintTimer);
  hintTimer = setTimeout(() => {
    hint.value = '';
  }, 450);
}
function trackIndex(event) {
  if (event.type === 'pointerdown') event.currentTarget.setPointerCapture(event.pointerId);
  else if (!event.currentTarget.hasPointerCapture(event.pointerId)) return;
  const bounds = event.currentTarget.getBoundingClientRect();
  const index = Math.min(
    alphabet.length - 1,
    Math.max(0, Math.floor(((event.clientY - bounds.top) / bounds.height) * alphabet.length)),
  );
  locate(alphabet[index]);
}
onBeforeUnmount(() => clearTimeout(hintTimer));
// 子页面打开时从页首开始，回到名录后恢复之前浏览的位置。
let directoryScroll = 0;
watch(screen, async (next, previous) => {
  const panel = root.value?.closest('.business-screen');
  if (previous === 'list') directoryScroll = panel?.scrollTop || 0;
  await nextTick();
  if (panel) panel.scrollTop = next === 'list' ? directoryScroll : 0;
  hint.value = '';
});
const groups = computed(() => {
  const result = new Map();
  for (const entry of directory.value) {
    if (
      !entry.terms.some((term) => normalizeContact(term).includes(normalizeContact(search.value)))
    )
      continue;
    if (!result.has(entry.initial)) result.set(entry.initial, []);
    result.get(entry.initial).push(entry.item);
  }
  return result;
});
const years = computed(() => [...new Set(records.value.map((r) => r.date.slice(0, 4)))]);
const shown = computed(() =>
  records.value.filter(
    (row) =>
      (!year.value || row.date.startsWith(year.value)) && (!flow.value || row.kind === flow.value),
  ),
);
const summary = computed(() => totals(shown.value));
const titles = {
  business: '业务管理',
  list: '礼金联系人',
  person: '亲友详情',
  add: '新增亲友',
  edit: '编辑亲友',
  merge: '合并亲友',
};
// 礼金中心的三个一级视图共用同一份个人流水。
const tabs = { business: '往来', registers: '礼单', list: '亲友' };
const listId = ref(props.initialList || ''),
  personOrigin = ref('list');
// 默认礼单按收送方向归集未指定活动礼单的历史和新流水。
const dailyId = (scope, kind) => scope + '-daily-' + kind;
const defaultLists = ['personal'].flatMap((scope) =>
  ['income', 'expense'].map((kind) => ({
    id: dailyId(scope, kind),
    scope,
    kind,
    name: kind === 'income' ? '日常收礼' : '日常送礼',
    daily: true,
  })),
);
const allLists = computed(() =>
  [...defaultLists, ...props.lists].filter(
    (l) => range.value === 'all' || (l.scope || 'personal') === range.value,
  ),
);
// 老流水用方向和原账本归入默认礼单。
function effectiveListId(row) {
  const id = row.gift?.listId;
  return !id || ['daily-in', 'daily-out'].includes(id) ? dailyId(row.scope, row.kind) : id;
}
const currentList = computed(() => allLists.value.find((item) => item.id === listId.value));
const allGifts = computed(() => select(allowedRows.value, { scope: range.value, giftOnly: true }));
const listRows = computed(() =>
  allGifts.value.filter((row) => effectiveListId(row) === listId.value),
);
// 礼单搜索覆盖全量明细，分页只影响显示笔数。
const searchedListRows = computed(() =>
  listRows.value.filter((r) =>
    [r.gift?.person, r.gift?.distinction, r.note].some((v) =>
      normalizeContact(v).includes(normalizeContact(listQuery.value)),
    ),
  ),
);
const listTotal = computed(() => listRows.value.reduce((sum, row) => sum + row.amount, 0));
// 礼单封面从当前账本实时取得笔数和金额。
const registerStats = computed(() => {
  const stats = new Map();
  for (const row of allGifts.value) {
    const id = effectiveListId(row),
      value = stats.get(id) || { count: 0, amount: 0 };
    value.count++;
    value.amount += row.amount;
    stats.set(id, value);
  }
  return stats;
});
const flowTotals = computed(() => totals(allGifts.value));
// 新建礼单和登记礼金的草稿分别随页面初始化。
// 连续登记沿用上一笔的收付款账户。
const recordOrigin = ref('business');
const account = ref(''),
  reason = ref(''),
  note = ref('');
const listName = ref(''),
  day = ref(''),
  kind = ref('income'),
  amount = ref(''),
  guest = ref({}),
  guestPicker = ref(null),
  notice = ref('');
// 礼单名称独立于单笔事由。
function listLabel(row) {
  return allLists.value.find((item) => item.id === effectiveListId(row))?.name || '';
}
function back() {
  error.value = '';
  notice.value = '';
  if (
    (screen.value === 'register' && props.initialList) ||
    tabs[screen.value] ||
    (screen.value === 'person' && props.initialId)
  )
    emit('close');
  else
    screen.value = {
      merge: 'person',
      edit: 'person',
      add: 'list',
      person: personOrigin.value,
      register: 'registers',
      newList: 'registers',
      editList: 'register',
      record: recordOrigin.value,
    }[screen.value];
}
// 从姓名链接返回时保留来源礼单。
function openPerson(id) {
  year.value = '';
  flow.value = '';
  familyView.value = false;
  if (screen.value !== 'edit')
    personOrigin.value = screen.value === 'register' ? 'register' : 'list';
  personId.value = id;
  screen.value = 'person';
}
// 初始化有名称、方向和日期的礼单。
function startList() {
  draftScope.value = range.value === 'all' ? props.defaultScope : range.value;
  listName.value = '';
  day.value = '';
  reason.value = '';
  kind.value = 'income';
  error.value = '';
  screen.value = 'newList';
}
// 保存空礼单后直接进入登记名单。
function submitList() {
  const list = {
    id: screen.value === 'editList' ? listId.value : crypto.randomUUID(),
    scope: draftScope.value,
    name: listName.value.trim(),
    date: day.value,
    defaultReason: reason.value,
    kind: kind.value,
  };
  if (props.createList(list)) {
    listId.value = list.id;
    screen.value = 'register';
    error.value = '';
  }
}
// 已有活动礼单只调整名称和日期，保留原归属、方向和关联流水。
function editList() {
  const list = currentList.value;
  listName.value = list.name;
  day.value = list.date || '';
  reason.value = list.defaultReason || '';
  kind.value = list.kind;
  draftScope.value = list.scope || 'personal';
  error.value = '';
  screen.value = 'editList';
}
// 每次登记使用新的姓名和金额，沿用礼单日期与方向。
function startRecord(list, type, keepAccount = false, fast = false) {
  if (!keepAccount) {
    quick.value = fast;
    continueEntry.value = fast;
  }
  if (!keepAccount) {
    recordOrigin.value = screen.value;
    account.value = '';
    draftScope.value = list
      ? list.scope || 'personal'
      : range.value === 'all'
        ? props.defaultScope
        : range.value;
  }
  reason.value = list?.defaultReason || '';
  note.value = '';
  listId.value = list?.id || '';
  kind.value = list?.kind || type;
  day.value = list?.date || localDay(new Date());
  guest.value = { name: '', id: '', mode: 'none', distinction: '' };
  amount.value = '';
  error.value = '';
  notice.value = '';
  screen.value = 'record';
}
// 改变收送方向时回到对应的默认礼单，保留姓名、金额等草稿。
function changeDirection(next) {
  if (kind.value === next) return;
  chooseListDefaults(null, currentList.value);
  kind.value = next;
  listId.value = '';
}
// 每次保存只产生一笔流水，连续登记保留礼单上下文。
const continueEntry = ref(false);
async function submitRecord(again) {
  await nextTick();
  const cents = Math.round(Number(amount.value) * 100);
  const prepared = guestPicker.value.prepare();
  if (!prepared) return;
  const person = prepared.contact;
  const row = {
    kind: kind.value,
    amount: cents,
    date: day.value,
    scope: draftScope.value,
    note: note.value,
    account: account.value,
    category: kind.value === 'income' ? '收礼' : '随礼',
    gift: {
      shared: draftScope.value === 'family',
      event: reason.value,
      person: person.name,
      contactId: person.id,
      distinction: person.distinction,
      listId: currentList.value?.daily ? '' : listId.value,
    },
  };
  if (!props.postGift(row, prepared)) return;
  emit('changed');
  if (again) {
    const savedDay = day.value;
    startRecord(currentList.value, kind.value, true);
    day.value = savedDay;
    notice.value = '已记入，继续登记下一位';
    await nextTick();
    guestPicker.value?.focus();
  } else back();
}
// 明确确认后删除档案，流水保留姓名与金额。
async function removePerson() {
  try {
    await showConfirmDialog({
      title: '删除「' + person.value.name + '」？',
      message: '已有礼金流水保留姓名和金额。',
      confirmButtonText: '删除',
      confirmButtonColor: '#a54341',
    });
  } catch {
    return;
  }
  if (props.deletePerson(personId.value)) {
    if (props.initialId) emit('close');
    else screen.value = 'list';
  }
}
// 预览目标与来源后统一修改活跃流水和最近删除中的关联。
async function merge() {
  const source = props.contacts.find((p) => p.id === duplicate.value);
  if (!source) return;
  const count = props.rows.filter((r) => r.gift?.contactId === source.id).length;
  try {
    await showConfirmDialog({
      title: '合并亲友档案？',
      message:
        '将「' +
        source.name +
        ' · ' +
        (source.distinction || '无称呼') +
        '」的 ' +
        count +
        ' 笔往来并入「' +
        person.value.name +
        ' · ' +
        (person.value.distinction || '无称呼') +
        '」。最近删除中的关联也会更新。',
    });
  } catch {
    return;
  }
  if (props.mergeContacts(source.id, personId.value)) {
    screen.value = 'person';
    duplicate.value = '';
  }
}
// 归档礼单可继续查账、导出，重新开放后可继续登记。
const listMenu = ref(false);
// 归档礼单可从同一个管理入口重新开放，保留原登记记录。
const listActions = computed(() =>
  currentList.value?.archived
    ? [{ name: '重新开放礼单', key: 'archive' }]
    : [
        { name: '编辑礼单', key: 'edit' },
        { name: '归档礼单', key: 'archive' },
      ],
);
function manageList(action) {
  if (!writable.value || currentList.value?.daily) return;
  if (action.key === 'edit') editList();
  else toggleArchive();
}
function toggleArchive() {
  props.createList({ ...currentList.value, archived: !currentList.value.archived });
}
// 编辑时预填资料，新增时使用空草稿。
function openForm(editing) {
  household.value =
    editing && person.value.household
      ? {
          name: person.value.household,
          id: householdKey(person.value),
          distinction: person.value.householdNote || '',
          mode: 'existing',
        }
      : { name: '', mode: 'none' };
  aliasText.value = editing ? (person.value.aliases || []).join('、') : '';
  village.value = editing ? person.value.village || '' : '';
  name.value = editing ? person.value.name : '';
  relation.value = editing ? person.value.distinction : '';
  error.value = '';
  screen.value = editing ? 'edit' : 'add';
}
// 亲友档案同名时，称呼必须能定位到唯一一人。
const sameFriends = computed(() =>
  props.contacts.filter(
    (p) =>
      (screen.value !== 'edit' || p.id !== personId.value) &&
      normalizeContact(p.name) === normalizeContact(name.value),
  ),
);
// 和新增亲友使用同一份可见目录，保存时再核对最新数据。
function distinctRelation(value) {
  if (!sameFriends.value.length) return true;
  if (!normalizeContact(value)) return '请填写能区分同名亲友的称呼或关系';
  return (
    !sameFriends.value.some((p) => normalizeContact(p.distinction) === normalizeContact(value)) ||
    '该称呼或关系已被同名亲友使用'
  );
}
// 沿用记账表单的同名区分规则。
function savePerson() {
  const family = familyPicker.value.prepare()?.contact;
  if (!family) return;
  const input = {
    name: name.value,
    distinction: relation.value,
    household: family.name,
    householdNote: family.distinction,
    aliases: aliasText.value
      .split(/[、,，]/)
      .map((a) => a.trim())
      .filter(Boolean),
    village: village.value.trim(),
  };
  const result =
    screen.value === 'edit'
      ? editGiftContact(bookStorage, [], personId.value, input)
      : addFriend(bookStorage, input);
  if (result.error) {
    if (result.field === 'distinction') error.value = result.error;
    else showToast(result.error);
    return;
  }
  emit('changed');
  openPerson(result.contact.id);
}
// 仅在用户切换礼单时应用默认值，打开旧流水时保留实记内容。
function chooseListDefaults(list, previous) {
  const defaults = applyListDefaults(list, previous, day.value, reason.value, localDay(new Date()));
  day.value = defaults.date;
  reason.value = defaults.reason;
}
</script>
<template>
  <section ref="root" class="contact-book" :data-screen="screen">
    <header class="business-header">
      <button aria-label="返回" @click="back"><VanIcon name="arrow-left" /></button>
      <h2>
        {{
          tabs[screen]
            ? '礼金往来'
            : {
                register: currentList?.name,
                newList: '新建礼单',
                editList: '编辑礼单',
                record: quick ? '快速记账' : '新增往来',
              }[screen] || titles[screen]
        }}
      </h2>
      <button
        v-if="writable && screen === 'business'"
        aria-label="新增往来"
        @click="startRecord(null, 'income')"
      >
        新增
      </button>
      <button v-if="writable && screen === 'registers'" aria-label="新增礼单" @click="startList">
        新增
      </button>
      <button
        v-if="writable && screen === 'register' && currentList && !currentList.archived"
        aria-label="新增礼单往来"
        @click="startRecord(currentList)"
      >
        新增
      </button>
      <button v-if="writable && screen === 'person'" aria-label="编辑亲友" @click="openForm(true)">
        编辑
      </button>
      <button v-if="writable && screen === 'list'" aria-label="新增亲友" @click="openForm(false)">
        新增
      </button>
    </header>
    <nav v-if="tabs[screen]">
      <button
        v-for="(label, key) in tabs"
        :key="key"
        :aria-pressed="screen === key"
        @click="screen = key"
      >
        {{ label }}
      </button>
    </nav>
    <template v-if="screen === 'business'">
      <div class="ledger-totals">
        <div>
          <small>收到礼金</small><strong>¥{{ amountText(flowTotals.income) }}</strong>
        </div>
        <div>
          <small>送出礼金</small><strong>¥{{ amountText(flowTotals.expense) }}</strong>
        </div>
      </div>
      <GiftRecordList :rows="allGifts" :label="listLabel" @row="emit('row', $event)" />
    </template>
    <template v-if="screen === 'registers'">
      <div class="register-shelf">
        <button
          v-for="l in allLists"
          :key="l.id"
          class="register-cover"
          :class="l.kind"
          @click="
            listQuery = '';
            listId = l.id;
            screen = 'register';
          "
        >
          <span class="cover-icon"><VanIcon name="records-o" /></span>
          <span class="cover-title"
            ><strong>{{ l.name }}</strong
            ><small
              >{{ l.archived ? '已归档 · ' : ''
              }}{{
                [l.daily ? '默认礼单' : l.date, l.defaultReason]
                  .filter(Boolean)
                  .map((v) => v + ' · ')
                  .join('')
              }}{{ registerStats.get(l.id)?.count || 0 }} 笔</small
            ></span
          >
          <span class="cover-amount">¥{{ amountText(registerStats.get(l.id)?.amount || 0) }}</span
          ><VanIcon name="arrow" />
        </button>
      </div>
    </template>
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="screen === 'newList' || screen === 'editList'"
      @submit="submitList"
    >
      <div v-if="screen === 'newList'" class="direction-field">
        <span>收送方向<span class="field-mark" aria-hidden="true">*</span></span>
        <div class="field-segments" role="group" aria-label="收送方向（必填）">
          <button
            type="button"
            v-for="(label, k) in { income: '收礼', expense: '送礼' }"
            :key="k"
            :aria-pressed="kind === k"
            @click="kind = k"
          >
            {{ label }}
          </button>
        </div>
      </div>
      <VanField
        v-else
        label="收送方向"
        :model-value="kind === 'income' ? '收礼' : '送礼'"
        readonly
      />
      <VanField
        name="listName"
        :rules="[
          { required: true, formatter: (v) => String(v ?? '').trim(), message: '请输入礼单名称' },
        ]"
        v-model="listName"
        required
        label="礼单名称"
        maxlength="40"
        placeholder="如：宝宝满月宴"
      />
      <GiftListDefaults v-model:date="day" v-model:reason="reason" />

      <VanButton type="primary" block round native-type="submit">{{
        screen === 'editList' ? '保存修改' : '创建礼单'
      }}</VanButton></VanForm
    >
    <template v-if="screen === 'register' && currentList"
      ><div class="person-summary register-summary">
        <button
          v-if="writable && !currentList.daily"
          class="list-more"
          type="button"
          aria-label="更多礼单操作"
          aria-haspopup="dialog"
          @click="listMenu = true"
        >
          <VanIcon name="ellipsis" /></button
        ><small
          >{{
            [currentList.daily ? '默认礼单' : currentList.date, currentList.defaultReason]
              .filter(Boolean)
              .map((v) => v + ' · ')
              .join('')
          }}{{ listRows.length }} 笔</small
        >
        <h3>
          {{ currentList.kind === 'income' ? '共收到' : '共送出' }} ¥{{ amountText(listTotal) }}
        </h3>
      </div>
      <div v-if="writable && !currentList.archived" class="gift-actions">
        <VanButton round icon="edit" @click="startRecord(currentList, kind, false, true)"
          >快速记账</VanButton
        >
      </div>
      <VanActionSheet
        v-model:show="listMenu"
        teleport="body"
        :actions="listActions"
        cancel-text="取消"
        close-on-click-action
        :style="{ left: 0, right: 0, marginInline: 'auto', maxWidth: '440px' }"
        @select="manageList" />
      <VanSearch v-model="listQuery" placeholder="在礼单中找人或备注" />
      <GiftRecordList :rows="searchedListRows" :label="listLabel" @row="emit('row', $event)"
    /></template>
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="screen === 'record'"
      @submit="submitRecord(continueEntry)"
    >
      <div v-if="recordOrigin !== 'register'" class="direction-field">
        <span>收送方向<span class="field-mark" aria-hidden="true">*</span></span>
        <div class="field-segments" role="group" aria-label="收送方向（必填）">
          <button
            type="button"
            v-for="(label, k) in { income: '收礼', expense: '送礼' }"
            :key="k"
            :aria-pressed="kind === k"
            @click="changeDirection(k)"
          >
            {{ label }}
          </button>
        </div>
      </div>
      <VanField
        v-if="recordOrigin === 'register'"
        label="所属礼单"
        :model-value="currentList.name"
        readonly
      />
      <ChoiceField
        v-if="recordOrigin !== 'register'"
        v-model="listId"
        @list-defaults="chooseListDefaults"
        resource="list"
        label="所属礼单"
        :kind="kind"
        :scope="draftScope"
        :day="day"
      />
      <GiftPersonPicker
        required
        ref="guestPicker"
        v-model="guest"
        :contacts="contacts"
        :label="kind === 'income' ? '送礼人' : '随礼对象'"
      />
      <VanField
        name="amount"
        :rules="[
          {
            validator: (v) => /^\d{1,8}(?:\.\d{1,2})?$/.test(v) && Number(v) > 0,
            message: '请输入大于零的金额，最多两位小数',
          },
        ]"
        v-model="amount"
        required
        label="金额"
        type="number"
        placeholder="0.00"
      /><DateField
        name="date"
        :rules="[{ validator: validDay, message: '请选择日期' }]"
        v-model="day"
        required
        label="日期"
      />
      <ChoiceField v-model="account" resource="account" label="账户" />
      <GiftReasonPicker v-if="!quick" v-model="reason" />
      <OptionalField v-if="!quick" label="备注" v-model="note" maxlength="100" />
      <small role="status">{{ notice }}</small>
      <div class="gift-actions">
        <VanButton v-if="!quick" round native-type="submit" @click="continueEntry = false"
          >保存</VanButton
        ><VanButton round type="primary" native-type="submit" @click="continueEntry = true"
          >保存并记下一位</VanButton
        >
      </div></VanForm
    >
    <template v-if="screen === 'list'">
      <div class="directory-search">
        <VanSearch
          v-model="search"
          clearable
          placeholder="搜索姓名、称呼或拼音"
          @update:model-value="letter = ''"
        />
      </div>
      <aside
        v-if="groups.size"
        class="index-rail"
        aria-label="亲友拼音索引"
        @pointerdown="trackIndex"
        @pointermove="trackIndex"
      >
        <button
          v-for="key in alphabet"
          :key="key"
          :disabled="!groups.has(key)"
          :aria-label="'定位 ' + key"
          :aria-current="key === letter ? 'location' : undefined"
          @click="locate(key)"
        >
          {{ key }}
        </button>
      </aside>
      <span v-if="hint" class="index-hint" aria-hidden="true">{{ hint }}</span>
      <section v-for="[key, people] in groups" :key="key" :data-initial="key" class="people-group">
        <h3>{{ key }}</h3>
        <button
          v-for="item in people"
          :key="item.id"
          class="person-link"
          @click="openPerson(item.id)"
        >
          <span class="avatar">{{ Array.from(item.name)[0] }}</span><span class="person-copy"
            ><strong>{{ item.name }}</strong
            ><small v-if="item.distinction || item.household">{{
              [item.distinction, item.household, item.householdNote].filter(Boolean).join(' · ')
            }}</small></span
          ><VanIcon name="arrow" />
        </button>
      </section>
      <VanEmpty v-if="!groups.size" description="没有匹配的亲友" />
    </template>
    <template v-if="screen === 'person' && person">
      <div
        v-if="person.household"
        class="field-segments family-view"
        role="group"
        aria-label="查看范围"
      >
        <button :aria-pressed="!familyView" @click="familyView = false">本人</button
        ><button :aria-pressed="familyView" @click="familyView = true">对方家庭</button>
      </div>
      <div class="person-summary">
        <h3>
          {{
            familyView
              ? [person.household, person.householdNote].filter(Boolean).join(' · ')
              : person.name
          }}
          <small>{{
            familyView
              ? members.map((p) => p.name).join('、')
              : [person.distinction, person.household, person.householdNote]
                  .filter(Boolean)
                  .join(' · ')
          }}</small>
        </h3>
        <dl>
          <div>
            <dt>我送出</dt>
            <dd>¥{{ amountText(summary.expense) }}</dd>
          </div>
          <div>
            <dt>我收到</dt>
            <dd>¥{{ amountText(summary.income) }}</dd>
          </div>
        </dl>
      </div>
      <VanCell
        v-if="person.aliases?.length || person.village"
        :title="[...(person.aliases || []), person.village].filter(Boolean).join(' · ')"
      />
      <div class="filters">
        <SelectRow
          compact
          label="年份"
          v-model="year"
          :options="[
            { value: '', label: '全部年份' },
            ...years.map((y) => ({ value: y, label: y })),
          ]"
        /><SelectRow
          compact
          label="收送方向"
          v-model="flow"
          :options="[
            { value: '', label: '全部方向' },
            { value: 'income', label: '收礼' },
            { value: 'expense', label: '送礼' },
          ]"
        />
      </div>
      <GiftRecordList :rows="shown" :label="listLabel" @row="emit('row', $event)" />
    </template>
    <template v-if="screen === 'merge'"
      ><VanField
        label="保留档案"
        :model-value="[person.name, person.distinction].filter(Boolean).join(' · ')"
        readonly
      /><SelectRow
        v-model="duplicate"
        label="并入档案"
        required
        :options="
          contacts
            .filter((p) => p.id !== personId)
            .map((p) => ({
              value: p.id,
              label: [p.name, p.distinction].filter(Boolean).join(' · '),
            }))
        "
      /><VanButton block round type="primary" :disabled="!duplicate" @click="merge"
        >预览合并</VanButton
      ></template
    >
    <VanForm
      scroll-to-error
      validate-trigger="onSubmit"
      v-if="screen === 'add' || screen === 'edit'"
      @submit="savePerson"
    >
      <VanField
        name="name"
        :rules="[
          { required: true, formatter: (v) => String(v ?? '').trim(), message: '请输入姓名' },
        ]"
        v-model="name"
        @update:model-value="error = ''"
        label="姓名"
        required
        maxlength="40"
        placeholder="输入姓名"
      />
      <OptionalField
        v-model="aliasText"
        label="别名"
        maxlength="100"
        placeholder="多个称呼用顿号隔开"
      /><OptionalField v-model="village" label="地区或村组" maxlength="40" />
      <OptionalField
        name="relation"
        :error-message="error"
        @update:model-value="error = ''"
        v-model="relation"
        :required="!!sameFriends.length"
        :rules="[{ validator: distinctRelation }]"
        label="称呼或关系"
        maxlength="40"
        placeholder="如：大学同学"
      />
      <GiftPersonPicker
        ref="familyPicker"
        v-model="household"
        family
        label="对方家庭"
        :contacts="contacts"
      />
      <RecordActions
        :options="
          screen === 'edit' && writable
            ? [
                {
                  name: '合并重复亲友',
                  visible: contacts.length > 1,
                  run: () => {
                    duplicate = '';
                    screen = 'merge';
                  },
                },
                { name: '删除亲友', danger: true, run: removePerson },
              ]
            : []
        "
      >
        <VanButton type="primary" block native-type="submit">保存</VanButton>
      </RecordActions>
    </VanForm>
  </section>
</template>
<style scoped>
.person-summary.register-summary {
  position: relative;
  padding-right: 58px;
}
.list-more {
  position: absolute;
  top: 4px;
  right: 6px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  color: inherit;
  font-size: 22px;
}

.contact-book {
  --accent: var(--green);
  --paper: var(--surface);
  --entry-rule: var(--line);
  --entry-ink: var(--ink);
  --entry-muted: var(--muted);
  --entry-accent: var(--accent);
  --entry-danger: #9b4741;
  --van-primary-color: var(--accent);
  --van-cell-background: var(--paper);
  --van-cell-text-color: var(--ink);
  --van-cell-value-color: #555e53;
  --van-cell-label-color: var(--muted);
  --van-border-color: var(--line);
  color: var(--ink);
  min-height: 85dvh;
  padding-bottom: 24px;
  font-variant-numeric: tabular-nums;
}
.contact-book nav {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: var(--soft);
  border-radius: 13px;
  padding: 4px;
  gap: 4px;
  margin-bottom: 16px;
}
.contact-book nav button {
  min-height: 37px;
  background: transparent;
  border: 0;
  border-radius: 10px;
  color: var(--muted);
  font: inherit;
  font-size: 14px;
  cursor: pointer;
}
.contact-book nav button[aria-pressed='true'] {
  color: var(--accent);
  background: var(--paper);
  font-weight: 700;
  box-shadow: 0 1px 4px #39473716;
}
.ledger-totals,
.person-summary {
  border-radius: 16px;
  background: var(--green-deep);
  color: #fffdf4;
  padding: 19px 17px;
}
.ledger-totals {
  margin-bottom: 12px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.ledger-totals > div + div {
  border-left: 1px solid #ffffff35;
  padding-left: 14px;
}
.ledger-totals small,
.person-summary small,
.person-summary dt {
  color: #d5e3d7;
  font-size: 13px;
}
.ledger-totals strong,
.person-summary dd {
  display: block;
  font-size: clamp(17px, 5vw, 24px);
  font-weight: 650;
  overflow-wrap: anywhere;
  margin: 5px 0 0;
}
.person-summary h3 {
  margin: 4px 0;
  font-size: 22px;
  overflow-wrap: anywhere;
}
.person-summary h3 small {
  font-weight: 400;
}
.person-summary dl {
  display: flex;
  gap: 32px;
  margin: 16px 0 0;
}
.gift-actions,
.filters {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 12px 0;
}
.gift-actions > * {
  flex: 1;
  min-width: 0;
}
.filters select,
.family-select select {
  min-width: 0;
  max-width: 65%;
  border: 0;
  color: var(--ink);
  font: inherit;
  background: transparent;
}
.filters select {
  flex: 1;
  border: 1px solid var(--line);
  border-radius: 9px;
  padding: 8px 3px;
  background: var(--paper);
  font-size: 13px;
}
.contact-book :deep(.van-cell) {
  min-height: 46px;
  align-items: center;
}
.movement :deep(.van-cell__left-icon),
.cover-icon {
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  margin-right: 10px;
  border-radius: 10px;
  background: #f4e7dc;
  color: #895c44;
}
.movement.income :deep(.van-cell__left-icon),
.income .cover-icon {
  background: #e3eee4;
  color: var(--accent);
}
.movement :deep(.van-cell__value) {
  font-weight: 650;
}
.movement.income :deep(.van-cell__value) {
  color: var(--accent);
}
.register-shelf {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.register-cover {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 15px 10px;
  border: 1px solid var(--line);
  border-left: 4px solid #c99477;
  border-radius: 4px 13px 13px 4px;
  background: var(--paper);
  font: inherit;
  text-align: left;
  color: var(--ink);
}
.register-cover.income {
  border-left-color: #6e967e;
}
.cover-icon {
  flex: none;
  margin: 0;
  font-size: 18px;
}
.cover-title,
.person-copy {
  flex: 1;
  min-width: 0;
  overflow-wrap: anywhere;
}
.cover-title strong,
.person-copy strong {
  font-size: 16px;
}
.cover-title small,
.person-copy small {
  display: block;
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}
.cover-amount {
  font-size: 14px;
  font-weight: 600;
}
.register-cover > i:last-child,
.person-link > i {
  color: #9ba397;
  font-size: 13px;
}
.register-line {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
}
.register-line .van-button {
  border: 0;
  color: var(--ink);
  max-width: 65%;
}
.contact-book :deep(.van-search) {
  padding: 6px 0;
  background: transparent;
}
.contact-book :deep(.van-search__content) {
  background: var(--soft);
  border-radius: 11px;
}
.directory-search {
  position: sticky;
  top: 0;
  z-index: 2;
  background: var(--page-bg);
  padding-bottom: 6px;
}
.people-group {
  margin-right: 21px;
  scroll-margin-top: 58px;
}
.people-group h3 {
  font: 650 12px/24px system-ui;
  margin: 9px 0 2px 9px;
  color: #596b5f;
}
.person-link {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 64px;
  padding: 8px 10px;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: var(--paper);
  text-align: left;
  color: inherit;
  font: inherit;
}
.avatar {
  display: grid;
  place-items: center;
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 13px;
  background: #e1ece0;
  color: #45654c;
  font-size: 17px;
}
.people-group:nth-of-type(even) .avatar {
  background: #f1e6d9;
  color: #865f47;
}
.index-rail {
  position: fixed;
  z-index: 3;
  right: max(2px, calc((100vw - 560px) / 2 + 2px));
  top: 50%;
  transform: translateY(-45%);
  width: 27px;
  height: min(59dvh, 430px);
  display: grid;
  grid-template-rows: repeat(27, 1fr);
  touch-action: none;
  user-select: none;
}
.index-rail button {
  border: 0;
  padding: 0;
  background: transparent;
  color: var(--accent);
  font: 700 11px/1 system-ui;
  cursor: pointer;
  min-height: 0;
}
.index-rail button:disabled {
  color: #a6afa7;
  pointer-events: none;
}
.index-rail button[aria-current] {
  background: var(--accent);
  color: white;
  border-radius: 12px;
}
.index-hint {
  position: fixed;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  background: #214d42ed;
  color: white;
  border-radius: 15px;
  padding: 14px 23px;
  font: 650 28px system-ui;
  pointer-events: none;
  z-index: 4;
}
.contact-book form {
  border: 1px solid var(--line);
  border-radius: 15px;
  --label-width: var(--field-label);
  --van-field-label-width: var(--label-width);
  --van-field-label-margin-right: var(--field-gap);
  padding: 7px 16px 14px;
  background: var(--paper);
}
.contact-book form :deep(.van-field),
.contact-book form :deep(.van-cell) {
  padding-inline: 0;
}
.contact-book form > .van-button {
  margin-top: 15px;
}
.contact-book form p {
  color: #9b4741;
  font-size: 14px;
}
.family-select {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 2px;
  min-height: 46px;
  border-bottom: 1px solid var(--line);
  font-size: 14px;
}
.contact-book form .ask-answer {
  border-radius: 10px;
  background: #eef4ec;
  padding: 13px;
  color: var(--ink);
  line-height: 1.6;
}
.contact-book button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: -2px;
}

.contact-book form .family-select,
.contact-book form :deep(.pilot-entry-contact),
.reason-options {
  display: grid;
  grid-template-columns: var(--label-width) minmax(0, 1fr);
  column-gap: var(--field-gap);
  padding-inline: 0;
  font-size: 14px;
}
.contact-book form .family-select select {
  max-width: 100%;
  width: 100%;
  padding: 0;
}
.contact-book form :deep(.van-field__label) {
  position: relative;
}

.contact-book form :deep(.van-cell::after) {
  left: 0;
  right: 0;
}
.contact-book form :deep(.pilot-entry-contact > label) {
  margin: 0;
}
.contact-book form :deep(.pilot-entry-contact__input) {
  padding: 0;
  border: 0;
}
.contact-book form :deep(.pilot-entry-contact__input > .van-icon-search) {
  display: none;
}
.reason-options {
  padding-block: 10px;
  align-items: baseline;
}
.reason-options button {
  border: 1px solid #dce4dc;
  background: white;
  color: #43574d;
  border-radius: 16px;
  padding: 5px 12px;
  margin: 2px 5px 3px 0;
}
.reason-options button[aria-pressed='true'] {
  background: #e6f0e8;
  color: #175643;
  border-color: #689682;
}
.family-view {
  margin-block: 12px;
}
</style>
