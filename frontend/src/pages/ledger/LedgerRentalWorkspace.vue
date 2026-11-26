<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  createRentalContract,
  generateRentalBill,
  linkRentalBill,
  listRentalBillPlans,
  listRentalContracts,
  listRentalHomes,
  rentalCycles,
  saveRentalHome,
  type RentalBillPlan,
  type RentalContract,
  type RentalCycle,
  type RentalHome,
} from '../../api/rentals';
import { ApiRequestError } from '../../api/request';
import LedgerPicker from '../../components/LedgerPicker.vue';
import LedgerIcon from '../../components/LedgerIcon.vue';
import LedgerFixedBills from './LedgerFixedBills.vue';
import { formatCents, ledgerToday, parseCents } from './transaction';

// #ifdef H5
import { showConfirmDialog } from 'vant';
import 'vant/es/dialog/style';
// #endif

const props = defineProps<{
  ledgerId: string;
  writable: boolean;
  covered: boolean;
  revision: number;
}>();
const emit = defineEmits<{ close: []; saved: []; detail: [id: string] }>();
const today = ledgerToday();
const workspaceStyle: Record<string, string> = {};
// #ifdef MP-WEIXIN
workspaceStyle.paddingTop = uni.getMenuButtonBoundingClientRect().bottom + 'px';
// #endif

type Screen =
  | 'homes'
  | 'contracts'
  | 'home'
  | 'homeForm'
  | 'contractForm'
  | 'chooseHome'
  | 'contract'
  | 'chooseBill'
  | 'configureBill'
  | 'periods';
const screen = ref<Screen>('homes');
const contractOrigin = ref<'home' | 'contracts'>('contracts');
const home = ref<RentalHome>();
const contract = ref<RentalContract>();
const formHome = ref<RentalHome>();
const formHomeEdit = ref(false);
const billFirstDueOn = ref('');
const billChoices = ['暂不设置', '生成固定账单', '关联已有固定账单'];
const setupChoices = billChoices.slice(1);
const setupMode = ref(0);
const selectedBill = ref<RentalBillPlan>();
const billPickerOrigin = ref<'contractForm' | 'configureBill'>('contractForm');
const billOptions = ref<RentalBillPlan[]>([]);
const billList = ref({ page: 0, total: 0, loading: false, error: false });
let billSequence = 0;

const homes = ref<RentalHome[]>([]);
const contracts = ref<RentalContract[]>([]);
const listState = ref({ page: 0, total: 0, loading: false, error: false });
const saving = ref(false);
const issue = ref<{ field: string; message: string }>();
let loadSequence = 0;
const homeDraft = ref({ name: '', address: '', note: '' });
const contractDraft = ref(emptyContract());

function emptyContract() {
  return {
    landlordName: '',
    startsOn: today,
    endsOn: '',
    rent: '',
    deposit: '',
    cycle: 'MONTHLY' as RentalCycle,
    firstDueOn: today,
    billMode: 0,
    note: '',
  };
}

const title = computed(
  () =>
    ({
      homes: '租房',
      contracts: '租房',
      home: home.value?.name,
      homeForm: formHomeEdit.value ? '编辑房屋' : '新增房屋',
      contractForm: '新增合同',
      chooseHome: '选择房屋',
      contract: '租房合同',
      configureBill: '设置房租账单',
      chooseBill: '选择已有房租账单',
      periods: '房租账期',
    })[screen.value],
);
const topAction = computed(() => {
  if (!props.writable) return '';
  if (screen.value === 'homes' || screen.value === 'contracts') return '新增';
  if (screen.value === 'home') return '编辑';
  return '';
});
const isForm = computed(() => screen.value === 'homeForm' || screen.value === 'contractForm');
const periodAmount = computed(() => {
  const cents = parseCents(contractDraft.value.rent);
  return cents == null ? 0 : cents * rentalCycles[contractDraft.value.cycle].months;
});
const rental = computed(() => {
  const row = contract.value;
  return row?.billId && row.firstDueOn && row.dueDayOfMonth
    ? {
        billId: row.billId,
        firstDueOn: row.firstDueOn,
        endsOn: row.endsOn,
        dueDayOfMonth: row.dueDayOfMonth,
        cycleMonths: rentalCycles[row.cycle].months,
      }
    : undefined;
});

function go(next: Screen) {
  loadSequence++;
  billSequence++;
  screen.value = next;
  issue.value = undefined;
  if (['homes', 'contracts', 'home', 'chooseHome'].includes(next)) void load();
  if (next === 'chooseBill') void loadBillOptions();
}

async function load(page = 0) {
  const seq = ++loadSequence;
  const listHomes = screen.value === 'homes' || screen.value === 'chooseHome';
  const homeId = screen.value === 'home' ? home.value?.id : undefined;
  if (!page) {
    if (listHomes) homes.value = [];
    else contracts.value = [];
  }
  listState.value = { ...listState.value, loading: true, error: false };
  try {
    if (listHomes) {
      const result = await listRentalHomes(props.ledgerId, page);
      if (seq !== loadSequence) return;
      if (
        page &&
        (result.totalCount !== listState.value.total ||
          result.entries.some((next) => homes.value.some((row) => next.id === row.id)))
      ) {
        await load();
        return;
      }
      homes.value = page ? [...homes.value, ...result.entries] : result.entries;
      listState.value = { page, total: result.totalCount, loading: false, error: false };
    } else {
      const result = await listRentalContracts(props.ledgerId, page, homeId);
      if (seq !== loadSequence) return;
      if (
        page &&
        (result.totalCount !== listState.value.total ||
          result.entries.some((next) => contracts.value.some((row) => next.id === row.id)))
      ) {
        await load();
        return;
      }
      contracts.value = page ? [...contracts.value, ...result.entries] : result.entries;
      listState.value = { page, total: result.totalCount, loading: false, error: false };
      if (homeId && home.value?.id === homeId) home.value.contractCount = result.totalCount;
    }
  } catch {
    if (seq === loadSequence) listState.value = { ...listState.value, loading: false, error: true };
  }
}
void load();

function showHome(row: RentalHome) {
  home.value = row;
  go('home');
}
function showContract(row: RentalContract) {
  contractOrigin.value = screen.value === 'home' ? 'home' : 'contracts';
  contract.value = row;
  go('contract');
}
function showHomeForm(edit = false) {
  formHomeEdit.value = edit;
  homeDraft.value =
    edit && home.value
      ? { name: home.value.name, address: home.value.address, note: home.value.note }
      : { name: '', address: '', note: '' };
  go('homeForm');
}
function showContractForm() {
  contractOrigin.value = screen.value === 'home' ? 'home' : 'contracts';
  formHome.value = contractOrigin.value === 'home' ? home.value : undefined;
  selectedBill.value = undefined;
  contractDraft.value = emptyContract();
  go('contractForm');
}
function chooseHome(row: RentalHome) {
  formHome.value = row;
  go('contractForm');
}
function runTopAction() {
  if (screen.value === 'home') showHomeForm(true);
  else if (screen.value === 'homes') showHomeForm();
  else showContractForm();
}
function back() {
  if (saving.value) return;
  if (screen.value === 'home') go('homes');
  else if (screen.value === 'homeForm') go(formHomeEdit.value ? 'home' : 'homes');
  else if (screen.value === 'contractForm' || screen.value === 'contract') go(contractOrigin.value);
  else if (screen.value === 'chooseHome') go('contractForm');
  else if (screen.value === 'configureBill') go('contract');
  else if (screen.value === 'chooseBill') go(billPickerOrigin.value);
  else emit('close');
}
function leaseStatus(row: RentalContract) {
  return today < row.startsOn ? '待入住' : today > row.endsOn ? '已到期' : '租期中';
}
function setStart(next: string) {
  if (contractDraft.value.firstDueOn === contractDraft.value.startsOn)
    contractDraft.value.firstDueOn = next;
  contractDraft.value.startsOn = next;
  issue.value = undefined;
}
function fail(field: string, message: string) {
  issue.value = { field, message };
}
function openBillSetup() {
  if (!contract.value || !props.writable) return;
  selectedBill.value = undefined;
  setupMode.value = 0;
  billFirstDueOn.value = contract.value.startsOn;
  go('configureBill');
}

watch(
  () => [
    contractDraft.value.startsOn,
    contractDraft.value.endsOn,
    contractDraft.value.cycle,
    formHome.value?.id,
  ],
  () => {
    selectedBill.value = undefined;
  },
);

function openBillPicker(origin: 'contractForm' | 'configureBill') {
  const row = origin === 'contractForm' ? contractDraft.value : contract.value;
  if (!row?.endsOn || row.endsOn < row.startsOn) {
    fail('end', '请先填写有效租期');
    return;
  }
  billPickerOrigin.value = origin;
  go('chooseBill');
}

async function loadBillOptions(page = 0) {
  const row = billPickerOrigin.value === 'contractForm' ? contractDraft.value : contract.value;
  if (!row) return;
  const seq = ++billSequence;
  if (!page) billOptions.value = [];
  billList.value = { ...billList.value, loading: true, error: false };
  try {
    const result = await listRentalBillPlans(props.ledgerId, row, page);
    if (seq !== billSequence) return;
    if (
      page &&
      (result.totalCount !== billList.value.total ||
        result.entries.some((option) =>
          billOptions.value.some((saved) => option.billId === saved.billId),
        ))
    ) {
      await loadBillOptions();
      return;
    }
    billOptions.value = page ? [...billOptions.value, ...result.entries] : result.entries;
    billList.value = { page, total: result.totalCount, loading: false, error: false };
  } catch {
    if (seq === billSequence) billList.value = { ...billList.value, loading: false, error: true };
  }
}

function selectBill(option: RentalBillPlan) {
  selectedBill.value = option;
  go(billPickerOrigin.value);
}

function setBillMode(index: number) {
  if (screen.value === 'configureBill') {
    setupMode.value = index;
    if (index === 1) openBillPicker('configureBill');
  } else {
    contractDraft.value.billMode = index;
    if (index === 2) openBillPicker('contractForm');
  }
}

async function confirmSeparateBill(
  row: { cycle: RentalCycle; startsOn: string; endsOn: string },
  origin: 'contractForm' | 'configureBill',
) {
  const { totalCount } = await listRentalBillPlans(props.ledgerId, row, 0);
  if (!totalCount) return true;
  const title = '已有可关联的住房账单';
  const message = '如果是同一笔房租，关联已有账单即可保留付款记录。';
  let confirmed = false;
  // #ifdef H5
  try {
    await showConfirmDialog({
      title,
      message,
      confirmButtonText: '另建一份',
      cancelButtonText: '选择已有账单',
    });
    confirmed = true;
  } catch {
    confirmed = false;
  }
  // #endif
  // #ifdef MP-WEIXIN
  confirmed = await new Promise<boolean>((resolve) =>
    uni.showModal({
      title,
      content: message,
      confirmText: '另建一份',
      cancelText: '选已有账单',
      success: ({ confirm }) => resolve(confirm),
      fail: () => resolve(false),
    }),
  );
  // #endif
  if (!confirmed) setBillMode(origin === 'contractForm' ? 2 : 1);
  return confirmed;
}

function saveBillFailure(error: unknown) {
  if (error instanceof ApiRequestError && error.code === 'XIAOBU_RENTAL_HOME_ALREADY_BILLED') {
    fail('bill', '这套房屋在该租期内已有房租账单。');
  } else if (error instanceof ApiRequestError && error.status === 409) {
    selectedBill.value = undefined;
    fail('bill', '账单或合同已变更，请重新选择。');
  } else {
    fail('bill', '账单未确认保存，请返回核对。');
  }
}

async function saveBillSetup() {
  const row = contract.value;
  if (!props.writable || saving.value || !row || row.billId) return;
  if (setupMode.value === 1 && !selectedBill.value) return fail('bill', '请选择房租账单');
  if (
    setupMode.value === 0 &&
    (billFirstDueOn.value < row.startsOn || billFirstDueOn.value > row.endsOn)
  ) {
    return fail('due', '首次付款日需在合同租期内');
  }
  saving.value = true;
  try {
    if (setupMode.value === 1 && selectedBill.value) {
      contract.value = await linkRentalBill(props.ledgerId, row.id, selectedBill.value.billId);
    } else {
      if (!(await confirmSeparateBill(row, 'configureBill'))) return;
      contract.value = await generateRentalBill(props.ledgerId, row.id, billFirstDueOn.value);
    }
    go('contract');
  } catch (error) {
    saveBillFailure(error);
  } finally {
    saving.value = false;
  }
}

async function save() {
  if (!props.writable || saving.value) return;
  issue.value = undefined;
  if (screen.value === 'homeForm') {
    if (!homeDraft.value.name.trim()) return fail('name', '请输入房屋名称');
    saving.value = true;
    try {
      const row = await saveRentalHome(
        props.ledgerId,
        homeDraft.value,
        formHomeEdit.value ? home.value?.id : undefined,
      );
      showHome(row);
    } catch (error) {
      if (error instanceof ApiRequestError && error.code === 'XIAOBU_RENTAL_HOME_DUPLICATE') {
        fail('name', '已有同名房屋');
      } else fail('', '未确认保存，请返回房屋列表核对。');
    } finally {
      saving.value = false;
    }
    return;
  }
  const row = contractDraft.value;
  const monthlyRentCents = parseCents(row.rent);
  const depositCents =
    !row.deposit.trim() || /^0{1,8}(?:\.0{1,2})?$/.test(row.deposit.trim())
      ? 0
      : parseCents(row.deposit);
  if (!formHome.value) return fail('home', '请选择房屋');
  if (!row.landlordName.trim()) return fail('landlord', '请输入出租方');
  if (!row.endsOn || row.endsOn < row.startsOn) return fail('end', '请选择不早于起租日的到期日');
  if (!monthlyRentCents || periodAmount.value > 9_999_999_999)
    return fail('rent', '请输入有效的月租，最多两位小数');
  if (depositCents == null || depositCents > 9_999_999_999)
    return fail('deposit', '请输入非负金额，最多两位小数');
  if (row.billMode === 2 && !selectedBill.value) return fail('bill', '请选择房租账单');
  if (row.billMode === 1 && (row.firstDueOn < row.startsOn || row.firstDueOn > row.endsOn)) {
    return fail('due', '首次付款日需在合同租期内');
  }
  saving.value = true;
  try {
    if (row.billMode === 1 && !(await confirmSeparateBill(row, 'contractForm'))) return;
    contract.value = await createRentalContract(props.ledgerId, {
      homeId: formHome.value.id,
      landlordName: row.landlordName,
      startsOn: row.startsOn,
      endsOn: row.endsOn,
      monthlyRentCents,
      depositCents,
      cycle: row.cycle,
      firstDueOn: row.billMode === 1 ? row.firstDueOn : null,
      ...(row.billMode === 2 && selectedBill.value
        ? { existingBillId: selectedBill.value.billId }
        : {}),
      note: row.note,
    });
    go('contract');
  } catch (error) {
    if (row.billMode) saveBillFailure(error);
    else fail('', '未确认合同保存，请返回合同列表核对。');
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <LedgerFixedBills
    v-if="screen === 'periods' && rental"
    :ledger-id="ledgerId"
    :covered="covered"
    :writable="writable"
    :revision="revision"
    :rental="rental"
    @close="go('contract')"
    @saved="emit('saved')"
    @detail="emit('detail', $event)"
  />
  <view v-else v-show="!covered" class="rental-workspace" :style="workspaceStyle">
    <view class="rental-header">
      <button aria-label="返回" class="rental-back" :disabled="saving" @click="back">‹</button>
      <text class="rental-title">{{ title }}</text>
      <button v-if="topAction" class="rental-action" @click="runTopAction">{{ topAction }}</button>
      <view v-else class="rental-space" />
    </view>
    <view class="rental-content">
      <view v-if="screen === 'homes' || screen === 'contracts'" class="rental-tabs">
        <button :class="{ active: screen === 'homes' }" @click="go('homes')">房屋</button>
        <button :class="{ active: screen === 'contracts' }" @click="go('contracts')">合同</button>
      </view>

      <view v-if="screen === 'home' && home" class="rental-card">
        <view v-if="home.address" class="rental-row"
          ><text>地址</text><text>{{ home.address }}</text></view
        >
        <view v-if="home.note" class="rental-row"
          ><text>备注</text><text>{{ home.note }}</text></view
        >
      </view>
      <view v-if="screen === 'home'" class="rental-heading">
        <text>租房合同</text>
        <button v-if="writable" @click="showContractForm">新增合同</button>
      </view>

      <template
        v-if="
          screen === 'homes' ||
          screen === 'chooseHome' ||
          screen === 'home' ||
          screen === 'contracts'
        "
      >
        <view v-if="screen === 'homes' || screen === 'chooseHome'" class="rental-card">
          <button
            v-for="item in homes"
            :key="item.id"
            class="rental-row"
            @click="screen === 'chooseHome' ? chooseHome(item) : showHome(item)"
          >
            <LedgerIcon class="rental-icon" src="/static/ledger/vant/HOUSING.svg" />
            <view class="rental-row-main"
              ><text>{{ item.name }}</text
              ><text v-if="item.address" class="rental-secondary">{{ item.address }}</text></view
            >
            <text class="rental-secondary">{{ item.contractCount }} 份合同</text
            ><text class="rental-arrow">›</text>
          </button>
          <text v-if="!homes.length && !listState.loading && !listState.error" class="rental-empty"
            >暂无租住房屋</text
          >
        </view>
        <view v-else class="rental-card">
          <button
            v-for="item in contracts"
            :key="item.id"
            class="rental-row"
            @click="showContract(item)"
          >
            <view class="rental-row-main">
              <text>{{ screen === 'home' ? item.landlordName : item.homeName }}</text>
              <text class="rental-secondary"
                >{{ screen === 'home' ? '' : item.landlordName + ' · ' }}{{ item.startsOn }} 至
                {{ item.endsOn }}</text
              >
            </view>
            <text class="rental-secondary">{{ leaseStatus(item) }}</text><text class="rental-arrow"
              >›</text
            >
          </button>
          <text
            v-if="!contracts.length && !listState.loading && !listState.error"
            class="rental-empty"
            >{{ screen === 'home' ? '这套房屋还没有合同' : '暂无租房合同' }}</text
          >
        </view>
        <text v-if="listState.loading" class="rental-empty">加载中…</text>
        <button v-else-if="listState.error" class="rental-more" @click="load()">
          加载失败，点击重试
        </button>
        <button
          v-else-if="
            (screen === 'homes' || screen === 'chooseHome' ? homes.length : contracts.length) <
            listState.total
          "
          class="rental-more"
          @click="load(listState.page + 1)"
        >
          加载更多
        </button>
      </template>

      <template v-if="screen === 'contract' && contract">
        <view class="rental-summary">
          <text>{{ leaseStatus(contract) }} · {{ contract.homeName }}</text>
          <view
            ><text class="rental-amount">¥{{ formatCents(contract.monthlyRentCents) }}</text
            ><text> / 月</text></view
          >
          <text>{{ contract.startsOn }} 至 {{ contract.endsOn }}</text>
        </view>
        <view class="rental-card">
          <view class="rental-row"
            ><text>付款约定</text
            ><text
              >{{ rentalCycles[contract.cycle].name }} · 每期 ¥{{
                formatCents(contract.periodAmountCents)
              }}</text
            ></view
          >
          <view class="rental-row"
            ><text>出租方</text><text>{{ contract.landlordName }}</text></view
          >
          <view v-if="contract.note" class="rental-row"
            ><text>备注</text><text>{{ contract.note }}</text></view
          >
          <button v-if="rental" class="rental-row" @click="go('periods')">
            <text class="rental-row-main">房租账期</text
            ><text class="rental-secondary">查看账期</text><text class="rental-arrow">›</text>
          </button>
          <button v-else-if="writable" class="rental-row" @click="openBillSetup">
            <text class="rental-row-main">设置房租账单</text><text class="rental-arrow">›</text>
          </button>
          <view v-else class="rental-row"
            ><text>房租账单</text><text class="rental-secondary">未设置</text></view
          >
          <view v-if="contract.depositCents" class="rental-row"
            ><text>约定押金</text><text>¥{{ formatCents(contract.depositCents) }}</text></view
          >
        </view>
      </template>

      <template v-if="screen === 'chooseBill'">
        <view class="rental-card">
          <button
            v-for="option in billOptions"
            :key="option.billId"
            class="rental-row"
            @click="selectBill(option)"
          >
            <view class="rental-row-main">
              <text>{{ option.name }}</text>
              <text class="rental-secondary">租期内首期 {{ option.firstDueOn }}</text>
            </view>
            <text class="rental-secondary">原计划 ¥{{ formatCents(option.amountCents) }}</text>
            <text class="rental-arrow">{{
              selectedBill?.billId === option.billId ? '✓' : '›'
            }}</text>
          </button>
          <text
            v-if="!billOptions.length && !billList.loading && !billList.error"
            class="rental-empty"
            >没有同周期且在租期内到期的住房账单</text
          >
        </view>
        <text v-if="billList.loading" class="rental-empty">加载中…</text>
        <button v-else-if="billList.error" class="rental-more" @click="loadBillOptions()">
          加载失败，点击重试
        </button>
        <button
          v-else-if="billOptions.length < billList.total"
          class="rental-more"
          @click="loadBillOptions(billList.page + 1)"
        >
          加载更多
        </button>
      </template>

      <template v-if="screen === 'configureBill' && contract">
        <view class="rental-card">
          <view class="rental-field"
            ><text>每期应付</text><text>¥{{ formatCents(contract.periodAmountCents) }}</text></view
          >
          <LedgerPicker
            label="房租账单"
            :value="setupMode"
            :range="setupChoices"
            @change="setBillMode(Number($event.detail.value))"
          >
            <view class="rental-field"
              ><text>房租账单</text><text>{{ setupChoices[setupMode] }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <button
            v-if="setupMode === 1"
            class="rental-field"
            @click="openBillPicker('configureBill')"
          >
            <text>选择账单<text class="rental-star">*</text></text>
            <text :class="{ 'rental-secondary': !selectedBill }">{{
              selectedBill?.name || '选择房租账单'
            }}</text><text class="rental-arrow">›</text>
          </button>
          <LedgerPicker
            v-else
            mode="date"
            label="首次付款日"
            :value="billFirstDueOn"
            :start="contract.startsOn"
            :end="contract.endsOn"
            @change="billFirstDueOn = $event.detail.value"
          >
            <view class="rental-field"
              ><text>首次付款日<text class="rental-star">*</text></text
              ><text>{{ billFirstDueOn }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <text v-if="issue?.field === 'due' || issue?.field === 'bill'" class="rental-error">{{
            issue.message
          }}</text>
        </view>
        <text v-if="setupMode === 1" class="rental-help"
          >保留原付款记录，租期内待付金额按合同计算。</text
        >
        <button class="rental-save" :disabled="saving" @click="saveBillSetup">
          {{ saving ? '保存中…' : setupMode === 1 ? '确认关联' : '生成固定账单' }}
        </button>
      </template>

      <template v-if="isForm">
        <view v-if="screen === 'homeForm'" class="rental-card">
          <view class="rental-field"
            ><text>房屋名称<text class="rental-star">*</text></text
            ><input
              v-model="homeDraft.name"
              aria-label="房屋名称"
              maxlength="40"
              placeholder="如：朝阳路公寓"
          /></view>
          <text v-if="issue?.field === 'name'" class="rental-error">{{ issue.message }}</text>
          <view class="rental-field"
            ><text>地址</text
            ><input
              v-model="homeDraft.address"
              aria-label="地址"
              maxlength="120"
              placeholder="选填"
          /></view>
          <view class="rental-field"
            ><text>备注</text
            ><input v-model="homeDraft.note" aria-label="备注" maxlength="100" placeholder="选填"
          /></view>
        </view>
        <view v-else class="rental-card">
          <button class="rental-field" @click="go('chooseHome')">
            <text>房屋<text class="rental-star">*</text></text
            ><text :class="{ 'rental-secondary': !formHome }">{{
              formHome?.name || '选择房屋'
            }}</text
            ><text class="rental-arrow">›</text>
          </button>
          <text v-if="issue?.field === 'home'" class="rental-error">{{ issue.message }}</text>
          <view class="rental-field"
            ><text>出租方<text class="rental-star">*</text></text
            ><input
              v-model="contractDraft.landlordName"
              aria-label="出租方"
              maxlength="40"
              placeholder="输入姓名或单位"
          /></view>
          <text v-if="issue?.field === 'landlord'" class="rental-error">{{ issue.message }}</text>
          <LedgerPicker
            mode="date"
            label="起租日"
            :value="contractDraft.startsOn"
            @change="setStart($event.detail.value)"
          >
            <view class="rental-field"
              ><text>起租日<text class="rental-star">*</text></text
              ><text>{{ contractDraft.startsOn }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <LedgerPicker
            mode="date"
            label="到期日"
            :value="contractDraft.endsOn || contractDraft.startsOn"
            @change="contractDraft.endsOn = $event.detail.value"
          >
            <view class="rental-field"
              ><text>到期日<text class="rental-star">*</text></text
              ><text :class="{ 'rental-secondary': !contractDraft.endsOn }">{{
                contractDraft.endsOn || '选择日期'
              }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <text v-if="issue?.field === 'end'" class="rental-error">{{ issue.message }}</text>
          <view class="rental-field"
            ><text>月租（元）<text class="rental-star">*</text></text
            ><input
              v-model="contractDraft.rent"
              aria-label="月租（元）"
              type="digit"
              placeholder="0.00"
          /></view>
          <text v-if="issue?.field === 'rent'" class="rental-error">{{ issue.message }}</text>
          <view class="rental-field"
            ><text>押金（元）</text
            ><input
              v-model="contractDraft.deposit"
              aria-label="押金（元）"
              type="digit"
              placeholder="选填"
          /></view>
          <text v-if="issue?.field === 'deposit'" class="rental-error">{{ issue.message }}</text>
          <view class="rental-field"
            ><text>付款周期<text class="rental-star">*</text></text
            ><view class="rental-cycle"
              ><button
                v-for="(item, key) in rentalCycles"
                :key="key"
                :class="{ active: contractDraft.cycle === key }"
                :aria-pressed="contractDraft.cycle === key"
                @click="contractDraft.cycle = key"
              >
                {{ item.name }}
              </button></view
            ></view
          >
          <view v-if="periodAmount > 0" class="rental-field"
            ><text>每期应付</text><text>¥{{ formatCents(periodAmount) }}</text></view
          >
          <LedgerPicker
            label="房租账单"
            :value="contractDraft.billMode"
            :range="billChoices"
            @change="setBillMode(Number($event.detail.value))"
          >
            <view class="rental-field"
              ><text>房租账单</text><text>{{ billChoices[contractDraft.billMode] }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <button
            v-if="contractDraft.billMode === 2"
            class="rental-field"
            @click="openBillPicker('contractForm')"
          >
            <text>选择账单<text class="rental-star">*</text></text>
            <text :class="{ 'rental-secondary': !selectedBill }">{{
              selectedBill?.name || '选择房租账单'
            }}</text><text class="rental-arrow">›</text>
          </button>
          <LedgerPicker
            v-if="contractDraft.billMode === 1"
            mode="date"
            label="首次付款日"
            :value="contractDraft.firstDueOn"
            @change="contractDraft.firstDueOn = $event.detail.value"
          >
            <view class="rental-field"
              ><text>首次付款日<text class="rental-star">*</text></text
              ><text>{{ contractDraft.firstDueOn }}</text
              ><text class="rental-arrow">›</text></view
            >
          </LedgerPicker>
          <text v-if="issue?.field === 'due' || issue?.field === 'bill'" class="rental-error">{{
            issue.message
          }}</text>
          <text v-else-if="contractDraft.billMode === 2" class="rental-help"
            >保留原付款记录，租期内待付金额按合同计算。</text
          >
          <view class="rental-field"
            ><text>备注</text
            ><input
              v-model="contractDraft.note"
              aria-label="备注"
              maxlength="100"
              placeholder="选填"
          /></view>
        </view>
        <text v-if="issue?.field === ''" class="rental-error">{{ issue.message }}</text>
        <button class="rental-save" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : screen === 'homeForm' ? '保存' : '保存合同' }}
        </button>
      </template>
    </view>
  </view>
</template>

<style scoped>
.rental-workspace {
  position: fixed;
  z-index: 30;
  inset: 0;
  overflow-y: auto;
  background: #f4f6f5;
  color: #21352e;
  font-size: 14px;
}
.rental-header {
  position: sticky;
  top: 0;
  z-index: 1;
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) 56px;
  align-items: center;
  padding: 0 10px;
  min-height: 52px;
  background: #fff;
  border-bottom: 1px solid #e8efeb;
}
.rental-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
  font-size: 17px;
  font-weight: 650;
}
.rental-workspace button {
  margin: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  line-height: 1.35;
}
.rental-workspace button::after {
  border: 0;
}
.rental-workspace .rental-back {
  justify-self: start;
  width: 42px;
  min-height: 44px;
  font-size: 28px;
}
.rental-workspace .rental-action,
.rental-heading button,
.rental-workspace .rental-more {
  min-height: 44px;
  color: #176a57;
}
.rental-content {
  width: min(100%, 540px);
  margin: auto;
  padding: 14px 14px calc(24px + env(safe-area-inset-bottom));
  box-sizing: border-box;
}
.rental-tabs,
.rental-cycle {
  display: flex;
  padding: 3px;
  gap: 3px;
  border-radius: 10px;
  background: #e8efeb;
}
.rental-tabs {
  margin-bottom: 12px;
}
.rental-tabs button,
.rental-cycle button {
  flex: 1;
  padding: 8px 2px;
  min-height: 36px;
  white-space: nowrap;
  border-radius: 8px;
}
.rental-cycle button {
  min-height: 32px;
  font-size: 12px;
}
.rental-tabs button.active,
.rental-cycle button.active {
  background: #176a57;
  color: white;
}
.rental-card {
  background: #fff;
  border-radius: 12px;
}
.rental-card:empty {
  display: none;
}
.rental-heading,
.rental-row,
.rental-field {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rental-heading,
.rental-row {
  justify-content: space-between;
}
.rental-heading {
  margin: 8px 0 2px;
  min-height: 44px;
  font-size: 16px;
  font-weight: 600;
}
.rental-row,
.rental-field,
.rental-workspace button.rental-row,
.rental-workspace button.rental-field {
  width: 100%;
  box-sizing: border-box;
  border-bottom: 1px solid #edf1ef;
  min-height: 48px;
  padding: 10px 13px;
  text-align: left;
}
.rental-row:last-child,
.rental-field:last-child {
  border-bottom: 0;
}
.rental-row-main,
.rental-field > :nth-child(2) {
  flex: 1;
  min-width: 0;
}
.rental-row-main {
  overflow-wrap: anywhere;
}
.rental-row-main .rental-secondary {
  display: block;
  margin-top: 4px;
}
.rental-secondary,
.rental-arrow {
  color: #61736b;
  font-size: 13px;
}
.rental-arrow {
  font-size: 18px;
}
.rental-icon {
  width: 22px;
  height: 22px;
}
.rental-empty {
  display: block;
  color: #61736b;
  padding: 22px 12px;
  text-align: center;
}
.rental-summary {
  border-radius: 16px;
  padding: 18px;
  margin-bottom: 14px;
  background: #164b3d;
  color: #dcebe3;
}
.rental-amount {
  color: white;
  font-size: 27px;
  font-weight: 650;
}
.rental-summary > view {
  margin: 7px 0;
}
.rental-card :deep(.ledger-picker-trigger),
.rental-card picker {
  box-sizing: border-box;
  border-bottom: 1px solid #edf1ef;
}
.rental-card :deep(.ledger-picker-trigger:last-child),
.rental-card picker:last-child {
  border-bottom: 0;
}
.rental-field > :first-child {
  flex: 0 0 86px;
}
.rental-field input {
  line-height: 23px;
  min-height: 23px;
}
.rental-star,
.rental-error {
  color: #ad392d;
}
.rental-star {
  margin-left: 2px;
}
.rental-help {
  display: block;
  padding: 9px 13px;
  color: #61736b;
  font-size: 12px;
}
.rental-error {
  display: block;
  margin: 5px 12px 9px;
  font-size: 12px;
}
.rental-workspace .rental-save {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  min-height: 44px;
  margin-top: 14px;
  border-radius: 24px;
  background: #176a57;
  color: white;
}
.rental-workspace button[disabled] {
  opacity: 0.55;
}
</style>
