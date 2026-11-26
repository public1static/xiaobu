<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { useLedgerCategoryColumns } from './category-layout';
import LedgerCategoryTiles from './LedgerCategoryTiles.vue';

type Choice = readonly [code: string, name: string];
const props = defineProps<{
  items: readonly Choice[];
  selected: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{ (event: 'select', code: string): void }>();

const columns = useLedgerCategoryColumns();
const pageSize = computed(() => columns.value * 2);
const pages = computed(() =>
  Array.from({ length: Math.ceil(props.items.length / pageSize.value) }, (_, page) =>
    props.items.slice(page * pageSize.value, (page + 1) * pageSize.value),
  ),
);
const page = ref(0);
const selectedPage = computed(() =>
  Math.max(
    0,
    Math.floor(props.items.findIndex(([code]) => code === props.selected) / pageSize.value),
  ),
);
watch(selectedPage, (value) => (page.value = value), { immediate: true });
watch(pages, (rows) => {
  page.value = Math.min(page.value, Math.max(0, rows.length - 1));
});

function pageChanged(event: { detail: { current: number } }) {
  page.value = event.detail.current;
}
</script>

<template>
  <swiper
    v-if="pages.length > 1"
    class="category-pages"
    :current="page"
    :indicator-dots="true"
    indicator-active-color="#176a57"
    @change="pageChanged"
  >
    <swiper-item v-for="(itemsOnPage, index) in pages" :key="index">
      <LedgerCategoryTiles
        :items="itemsOnPage"
        :columns="columns"
        :selected="selected"
        :disabled="disabled"
        @select="emit('select', $event)"
      />
    </swiper-item>
  </swiper>
  <LedgerCategoryTiles
    v-else
    :items="items"
    :columns="columns"
    :selected="selected"
    :disabled="disabled"
    @select="emit('select', $event)"
  />
</template>

<style scoped>
.category-pages {
  height: 108px;
}
</style>
