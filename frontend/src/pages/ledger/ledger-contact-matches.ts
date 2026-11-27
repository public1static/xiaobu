import { onBeforeUnmount, ref } from 'vue';
import { listLedgerContactMatches, type LedgerContactMatches } from '../../api/ledger-contacts';
import type { GiftContact } from '../../api/ledger-gifts';

export function useLedgerContactMatches(ledger: () => string) {
  const matches = ref<GiftContact[]>([]);
  const count = ref(0);
  const pending = ref(false);
  const failure = ref(false);
  let sequence = 0;
  let nextTick: ReturnType<typeof setTimeout> | undefined;
  let name = '';
  let page = 0;
  let resolve: ((result: LedgerContactMatches) => void) | undefined;

  function reset() {
    sequence++;
    if (nextTick != null) clearTimeout(nextTick);
    matches.value = [];
    count.value = 0;
    pending.value = false;
    failure.value = false;
    page = 0;
    nextTick = undefined;
  }

  async function load(next: number, generation: number) {
    const book = ledger();
    pending.value = true;
    failure.value = false;
    try {
      const result = await listLedgerContactMatches(book, name, next);
      if (generation !== sequence || book !== ledger()) return;
      if (
        next &&
        (result.totalCount !== count.value ||
          result.entries.some((item) => matches.value.some((previous) => previous.id === item.id)))
      ) {
        await load(0, generation);
        return;
      }
      matches.value = next ? [...matches.value, ...result.entries] : result.entries;
      page = result.page;
      count.value = result.totalCount;
      if (!next) resolve?.(result);
    } catch {
      if (generation === sequence && book === ledger()) failure.value = true;
    } finally {
      if (generation === sequence && book === ledger()) pending.value = false;
    }
  }

  function search(input: string, callback?: (result: LedgerContactMatches) => void) {
    reset();
    name = input.trim();
    resolve = callback;
    if (!name) return;
    pending.value = true;
    const stamp = sequence;
    nextTick = setTimeout(() => {
      if (stamp === sequence) void load(0, stamp);
    }, 140);
  }

  function more() {
    if (!pending.value && matches.value.length < count.value) void load(page + 1, sequence);
  }

  function retry() {
    if (name && !pending.value) void load(page && matches.value.length ? page + 1 : 0, sequence);
  }

  onBeforeUnmount(reset);
  return { matches, count, pending, failure, reset, search, more, retry };
}
