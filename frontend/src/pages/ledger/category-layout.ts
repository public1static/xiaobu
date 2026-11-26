import { computed, onMounted, onUnmounted, ref } from 'vue';

const MAX_SHELL_WIDTH = 560;
const GRID_INSET = 60;
const TILE_WIDTH = 44;
const TILE_GAP = 4;

export function useLedgerCategoryColumns() {
  const width = ref(uni.getWindowInfo().windowWidth);
  const update = ({ size }: { size: { windowWidth: number } }) => {
    width.value = size.windowWidth;
  };
  onMounted(() => uni.onWindowResize(update));
  onUnmounted(() => uni.offWindowResize(update));
  return computed(() => {
    const available = Math.min(width.value, MAX_SHELL_WIDTH) - GRID_INSET;
    return Math.max(1, Math.floor((available + TILE_GAP) / (TILE_WIDTH + TILE_GAP)));
  });
}
