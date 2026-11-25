<script setup>
import { inject, ref } from 'vue';
import { Button as VanButton, Icon as VanIcon, showConfirmDialog } from 'vant';
import { makeBackup, validateBackup, restoreBackup, download } from './backup.mjs';
import { localDay } from './ledger.mjs';
const bookStorage = inject('bookStorage');
const props = defineProps({ book: Object });
const emit = defineEmits(['back', 'restored']);
// 导入文件先预览，再由用户确认覆盖。
const preview = ref(null),
  message = ref(''),
  filename = ref('');
// 导出当前账本与目录快照。
function backup() {
  try {
    download(
      '小生活记账簿-' + localDay(new Date()) + '.json',
      JSON.stringify(makeBackup(bookStorage, props.book), null, 2),
      'application/json',
    );
    message.value = '已生成备份文件';
  } catch (e) {
    message.value = e.message;
  }
}
// 读取选中的备份，不修改本机数据。
async function inspect(event) {
  preview.value = null;
  message.value = '';
  const file = event.target.files[0];
  if (!file) return;
  try {
    preview.value = validateBackup(JSON.parse(await file.text()));
    filename.value = file.name;
  } catch {
    message.value = '无法读取这份账本备份，请检查文件。';
  }
  event.target.value = '';
}
// 在明确确认后替换本地目录并刷新显示。
async function apply() {
  try {
    await showConfirmDialog({
      title: '替换本机账本？',
      message: '当前流水、账户、分类和亲友资料将被所选备份覆盖。',
      confirmButtonText: '确认恢复',
    });
  } catch {
    return;
  }
  try {
    restoreBackup(bookStorage, preview.value);
    emit('restored', preview.value.book);
    preview.value = null;
    message.value = '已恢复账本';
  } catch (e) {
    message.value = e.message;
  }
}
</script>
<template>
  <header class="business-header">
    <button aria-label="返回" @click="emit('back')"><VanIcon name="arrow-left" /></button>
    <h1>备份与恢复</h1>
  </header>
  <section class="backup-card">
    <h2>整本备份</h2>
    <p>包含流水、最近删除、账户、转账、固定账单、租房合同与押金、分类、礼单和亲友。</p>
    <VanButton block round type="primary" @click="backup">导出备份</VanButton>
  </section>
  <section class="backup-card">
    <h2>从文件恢复</h2>
    <label
      ><span>备份文件<span class="field-mark" aria-hidden="true">*</span></span
      ><input aria-required="true" type="file" accept=".json,application/json" @change="inspect"
    /></label>
    <div v-if="preview">
      <h3>{{ filename }}</h3>
      <p>
        {{ preview.book.rows.length }} 笔流水 · {{ preview.book.trash.length }} 笔最近删除 ·
        {{ preview.book.giftLists?.length || 0 }} 张活动礼单
      </p>
      <VanButton block round @click="apply">恢复此备份</VanButton>
    </div>
  </section>
  <p role="status">{{ message }}</p>
</template>
<style scoped>
section {
  background: white;
  border: 1px solid #dce7de;
  border-radius: 14px;
  padding: 16px;
  margin: 14px 0;
}
h2 {
  margin: 0;
  font-size: 17px;
}
p,
label {
  font-size: 14px;
  color: #50685b;
  line-height: 1.6;
}
label {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 14px;
}
input {
  max-width: 100%;
}
</style>
