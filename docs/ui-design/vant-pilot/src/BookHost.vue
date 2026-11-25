<script setup>
import SelectRow from './SelectRow.vue';
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import {
  Popup as VanPopup,
  Form as VanForm,
  Button as VanButton,
  Cell as VanCell,
  Field as VanField,
  Icon as VanIcon,
  showConfirmDialog,
  showToast,
} from 'vant';
import { catalog, exampleStorage, forBook, INDEX } from './book-space.mjs';
import App from './App.vue';

// 身份按标签页保存，便于在两页分别演示邀请方和接收方。
const actor = ref(sessionStorage.getItem('hezhang:actor') === 'family' ? 'family' : 'me');
// 本地演示登录按标签页保留；游客视图与成员视图分别挂载。
const signedIn = ref(sessionStorage.getItem('hezhang:demo-login') === '1');
const sample = exampleStorage(),
  sampleInfo = { id: 'example', name: '示例账本', owner: 'me', members: { guest: 'reader' } };
const app = ref(null),
  loginOpen = ref(false),
  resumeEntry = ref(false);
const directory = ref(signedIn.value ? catalog(localStorage) : null),
  panel = ref(''),
  origin = ref(''),
  name = ref(''),
  role = ref('editor');
const allowed = computed(() => directory.value.books.filter((b) => b.members[actor.value]));
const current = computed(
  () => allowed.value.find((b) => b.id === directory.value.last[actor.value]) || allowed.value[0],
);
const store = computed(() => forBook(localStorage, current.value.id, actor.value));
const labels = { owner: '管理者', editor: '可记账', reader: '只读' };
// 分享入口只携带邀请码，落地页仅展示账本名和邀请权限。
const incoming = ref(new URL(location.href).searchParams.get('invite') || '');
const clock = ref(Date.now());
const inviteDirectory = ref(incoming.value ? catalog(localStorage) : null);
const offer = computed(() =>
  inviteDirectory.value?.invitations.find(
    (i) => i.code === incoming.value && i.expires > clock.value,
  ),
);
const offeredBook = computed(() =>
  inviteDirectory.value?.books.find((b) => b.id === offer.value?.book),
);
const memberName = (id) => (id === actor.value ? '我' : id === 'family' ? '家人' : '账本创建者');
const invitations = computed(() =>
  directory.value.invitations.filter((i) => i.book === current.value.id && i.expires > clock.value),
);
const selectedMember = ref('');
let timer;
// 清除已消费的邀请入口，刷新后留在当前账本。
function closeOffer() {
  incoming.value = '';
  const url = new URL(location.href);
  url.searchParams.delete('invite');
  window.history.replaceState(null, '', url);
}
const version = ref(0);
// 同一浏览器多个标签页同步目录及已保存的流水。
function refresh(e) {
  if (incoming.value && e.key === INDEX) inviteDirectory.value = catalog(localStorage);
  if (!signedIn.value) return;
  if (e.key === INDEX) {
    directory.value = catalog(localStorage);
    if (panel.value === 'invite' && Object.keys(current.value.members).length > 1)
      panel.value = 'members';
  }
  if (e.key === 'hezhang:book:' + current.value.id) version.value++;
}
onMounted(() => {
  window.addEventListener('storage', refresh);
  timer = setInterval(() => (clock.value = Date.now()), 1000);
});
onUnmounted(() => {
  clearInterval(timer);
  window.removeEventListener('storage', refresh);
});
// 只有用户确认登录后才打开个人目录，接回原先点击的操作。
function requestLogin(record = false) {
  resumeEntry.value = record;
  loginOpen.value = true;
}
async function login() {
  directory.value = catalog(localStorage);
  signedIn.value = true;
  sessionStorage.setItem('hezhang:demo-login', '1');
  loginOpen.value = false;
  panel.value = '';
  if (resumeEntry.value) {
    if (current.value.members[actor.value] === 'reader')
      choose(allowed.value.find((b) => b.owner === actor.value).id);
    await nextTick();
    app.value?.openEntry();
  }
}
// 退出只清除本次身份，保留用户已记的账。
function logout() {
  signedIn.value = false;
  sessionStorage.removeItem('hezhang:demo-login');
  sessionStorage.removeItem('hezhang:actor');
  actor.value = 'me';
  panel.value = '';
  directory.value = null;
}
// 每次目录操作从最新版本读取，再一次写入。
function update(action) {
  try {
    const next = catalog(localStorage);
    action(next);
    localStorage.setItem(INDEX, JSON.stringify(next));
    directory.value = next;
    return true;
  } catch (e) {
    showToast(e.message || '无法保存账本设置。');
    return false;
  }
}
// 访问账本前检查成员身份。
function choose(id) {
  if (
    update((d) => {
      if (!d.books.find((b) => b.id === id)?.members[actor.value]) throw Error('未加入该账本。');
      d.last[actor.value] = id;
    })
  )
    panel.value = '';
}
// 创建或更改自己管理的账本名称。
function saveName() {
  const text = name.value.trim();
  if (
    update((d) => {
      if (panel.value === 'rename') {
        const b = d.books.find((b) => b.id === current.value.id);
        if (b.owner !== actor.value) throw Error('需要管理者权限。');
        b.name = text;
      } else {
        const id = crypto.randomUUID();
        d.books.push({ id, name: text, owner: actor.value, members: { [actor.value]: 'owner' } });
        d.last[actor.value] = id;
      }
    })
  )
    panel.value = '';
}
// H5 以本地好友选择面板模拟分享，确认发送后才保存邀请。
const sharing = ref(false);
const friend = computed(() => (actor.value === 'me' ? '家人' : '账本创建者'));
// 收件卡片使用独立标签页，保留发送人的登录会话。
function cardUrl(code) {
  const url = new URL(location.href);
  url.searchParams.set('invite', code);
  return url.href;
}
// 未登录的新会话模拟收件人的微信身份，已登录的会话始终保留本人身份。
async function joinOffer() {
  const i = offer.value;
  if (!i) return;
  if (!signedIn.value) {
    actor.value = i.to;
    sessionStorage.setItem('hezhang:actor', actor.value);
    resumeEntry.value = false;
    await login();
  }
  if (actor.value !== i.to) {
    showToast('请使用收到邀请的账号加入');
    return;
  }
  accept(i.code);
}
// 邀请与接收人、权限及账本绑定，可撤销且只可接受一次。
function sendInvitation() {
  if (
    update((d) => {
      const b = d.books.find((b) => b.id === current.value.id),
        to = actor.value === 'me' ? 'family' : 'me';
      if (b.owner !== actor.value || b.members[to]) throw Error('该成员已加入或没有邀请权限。');
      d.invitations = d.invitations.filter((i) => i.book !== b.id || i.to !== to);
      d.invitations.push({
        book: b.id,
        to,
        role: role.value,
        code: crypto.randomUUID().slice(0, 8),
        expires: Date.now() + 86400000,
      });
    })
  ) {
    sharing.value = false;
    showToast('邀请已发送');
  }
}
function accept(token) {
  if (
    update((d) => {
      const i = d.invitations.find(
        (i) => i.code === token.trim() && i.to === actor.value && i.expires > Date.now(),
      );
      if (!i) throw Error('邀请无效或已过期。');
      const b = d.books.find((b) => b.id === i.book);
      b.members[actor.value] = i.role;
      d.last[actor.value] = b.id;
      d.invitations = d.invitations.filter((x) => x !== i);
    })
  ) {
    panel.value = '';
    closeOffer();
    showToast('已加入账本');
  }
}
// 修改权限或移除成员时保留原来的录入署名。
async function membership(who, value) {
  const id = current.value.id;
  if (!value) {
    try {
      await showConfirmDialog({
        title: who === actor.value ? '退出这本账？' : '移除这位成员？',
        message: '已经登记的流水保留在账本中。',
      });
    } catch {
      return;
    }
  }
  update((d) => {
    const b = d.books.find((b) => b.id === id);
    if (
      who === b.owner ||
      !b.members[who] ||
      (b.owner !== actor.value && (who !== actor.value || value))
    )
      throw Error('没有操作权限。');
    if (value) b.members[who] = value;
    else delete b.members[who];
  });
  if (!value && who === actor.value) panel.value = 'books';
}
// 从首页或账本管理页进入后按原路径返回。
function open(p) {
  origin.value = p;
  panel.value = p;
  name.value = '';
}
</script>
<template>
  <App
    v-if="!signedIn"
    key="example"
    guest
    :storage="sample"
    :book-info="sampleInfo"
    actor="guest"
    @login="requestLogin"
  />
  <App
    v-else
    ref="app"
    v-show="!panel"
    :key="actor + current.id + current.members[actor] + version"
    :storage="store"
    :book-info="current"
    :actor="actor"
    @books="open('books')"
    @members="open('members')"
  />
  <main v-if="signedIn && panel" class="business-screen">
    <div class="business-body book-settings">
      <header class="business-header">
        <button
          aria-label="返回"
          @click="
            panel = panel === 'invite' ? 'members' : ['new', 'rename'].includes(panel) ? origin : ''
          "
        >
          <VanIcon name="arrow-left" />
        </button>
        <h1>
          {{
            {
              books: '选择账本',
              members: '成员与邀请',
              invite: '邀请成员',
              new: '新建账本',
              rename: '修改名称',
            }[panel]
          }}
        </h1>
      </header>

      <template v-if="panel === 'books'">
        <VanCell
          v-for="b in allowed"
          :key="b.id"
          :title="b.name"
          :label="labels[b.members[actor]]"
          :value="b.id === current.id ? '当前' : ''"
          is-link
          @click="choose(b.id)"
        />
        <VanButton
          block
          round
          type="primary"
          @click="
            name = '';
            panel = 'new';
          "
          >新建账本</VanButton
        >
        <VanCell title="退出登录" is-link @click="logout" />
      </template>
      <template v-if="panel === 'members'">
        <VanCell
          title="当前账本"
          :value="current.name"
          :is-link="current.owner === actor"
          @click="current.owner === actor && ((name = current.name), (panel = 'rename'))"
        />
        <h3>账本成员 · {{ Object.keys(current.members).length }} 人</h3>
        <VanCell
          v-for="(r, who) in current.members"
          :key="who"
          :title="memberName(who)"
          :value="labels[r]"
          :is-link="current.owner === actor && who !== actor"
          @click="current.owner === actor && who !== actor && (selectedMember = who)"
        >
          <template #icon
            ><span class="member-avatar">{{ memberName(who)[0] }}</span></template
          >
        </VanCell>
        <VanButton
          v-if="current.owner === actor && Object.keys(current.members).length === 1"
          block
          round
          type="primary"
          @click="
            role = invitations[0]?.role || 'editor';
            panel = 'invite';
          "
          >{{ invitations.length ? '查看待接受邀请' : '邀请成员' }}</VanButton
        >
        <VanButton v-if="current.owner !== actor" block round @click="membership(actor, '')"
          >退出账本</VanButton
        >
        <VanPopup
          :show="!!selectedMember"
          @update:show="!$event && (selectedMember = '')"
          position="bottom"
          round
          class="login-sheet"
        >
          <h2>{{ memberName(selectedMember) }}</h2>
          <VanCell
            v-for="r in ['editor', 'reader']"
            :key="r"
            :title="labels[r]"
            :value="current.members[selectedMember] === r ? '已选' : ''"
            clickable
            @click="
              membership(selectedMember, r);
              selectedMember = '';
            "
          />
          <VanCell
            title="移除成员"
            clickable
            @click="
              membership(selectedMember, '');
              selectedMember = '';
            "
          />
        </VanPopup>
      </template>
      <template v-if="panel === 'invite' && current.owner === actor">
        <h3>{{ current.name }}</h3>
        <template v-if="!invitations.length"
          ><SelectRow
            v-model="role"
            label="成员权限"
            :options="[
              { value: 'editor', label: '可记账' },
              { value: 'reader', label: '只读' },
            ]"
          />
          <p>
            {{
              role === 'editor'
                ? '成员可查看和修改本账本的记录。'
                : '成员可查看本账本的记录和账户余额。'
            }}
          </p>
          <VanButton type="primary" block round @click="sharing = true"
            >邀请微信好友</VanButton
          ></template
        >
        <template v-for="i in invitations" :key="i.code"
          ><VanCell title="邀请状态" value="待接受" /><VanCell
            title="成员权限"
            :value="labels[i.role]"
          /><VanCell
            title="有效期至"
            :value="
              new Date(i.expires).toLocaleString('zh-CN', {
                month: 'numeric',
                day: 'numeric',
                hour: '2-digit',
                minute: '2-digit',
              })
            "
          />
          <a class="share-card" :href="cardUrl(i.code)" target="_blank" rel="noopener noreferrer"
            ><span>查看邀请卡片</span><VanIcon name="arrow"
          /></a>
          <VanButton
            block
            round
            @click="
              update((d) => {
                d.invitations = d.invitations.filter((x) => x.code !== i.code);
              })
            "
            >撤销邀请</VanButton
          >
        </template>
      </template>
      <VanPopup
        v-if="panel === 'invite'"
        v-model:show="sharing"
        round
        position="bottom"
        class="login-sheet"
        safe-area-inset-bottom
      >
        <h2>发送给微信好友</h2>
        <VanCell class="share-friend" :title="friend" value="发送" clickable @click="sendInvitation"
          ><template #icon
            ><span class="member-avatar">{{ friend[0] }}</span></template
          ></VanCell
        >
        <VanButton block round plain @click="sharing = false">取消</VanButton>
      </VanPopup>
      <VanForm
        scroll-to-error
        validate-trigger="onSubmit"
        class="managed-form"
        v-if="['new', 'rename'].includes(panel)"
        @submit="saveName"
        ><VanField
          name="bookName"
          :rules="[
            { required: true, formatter: (v) => String(v ?? '').trim(), message: '请输入账本名称' },
          ]"
          v-model="name"
          required
          label="账本名称"
          placeholder="如：我们家"
          maxlength="40"
        /><VanButton native-type="submit" round block type="primary">保存</VanButton></VanForm
      >
    </div>
  </main>
  <VanPopup
    :show="!!incoming"
    position="bottom"
    round
    class="login-sheet"
    @update:show="!$event && closeOffer()"
  >
    <template v-if="offer && offeredBook"
      ><h2>账本邀请</h2>
      <p>邀请你加入「{{ offeredBook.name }}」</p>
      <VanCell title="加入后权限" :value="labels[offer.role]" />
      <VanButton
        v-if="!signedIn || actor === offer.to"
        block
        round
        type="primary"
        @click="joinOffer"
        >{{ signedIn ? '加入账本' : '微信登录并加入' }}</VanButton
      >
      <p v-else>请由收到邀请的好友打开卡片。</p> </template
    ><template v-else
      ><h2>邀请已失效</h2>
      <p>请联系账本管理者重新邀请。</p></template
    >
    <VanButton block round plain @click="closeOffer">返回</VanButton>
  </VanPopup>
  <VanPopup
    v-model:show="loginOpen"
    position="bottom"
    round
    class="login-sheet"
    safe-area-inset-bottom
  >
    <h2>登录小生活记账簿</h2>
    <p>{{ resumeEntry ? '登录后，在自己的账本记一笔。' : '登录后，进入自己的账本。' }}</p>
    <VanButton block round type="primary" @click="login">微信登录</VanButton
    ><VanButton block round plain @click="loginOpen = false">继续浏览示例</VanButton>
  </VanPopup>
</template>
<style scoped>
.login-sheet {
  max-width: 440px;
  left: 0;
  right: 0;
  margin: auto;
  padding: 20px 16px;
}
.login-sheet h2 {
  font-size: 20px;
}
.login-sheet p {
  margin: 8px 0 18px;
  color: var(--muted);
}
.login-sheet .van-button + .van-button {
  margin-top: 8px;
}

.share-friend {
  margin: 12px 0;
}
.share-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 0 16px;
  background: white;
  color: var(--green);
  text-decoration: none;
}
.member-avatar {
  display: grid;
  place-items: center;
  align-self: center;
  margin-right: 12px;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #e9f2eb;
  color: #246252;
  font-weight: 600;
}
.business-body > .van-button,
form .van-button {
  margin-top: 12px;
}
h3 {
  font-size: 15px;
  margin: 24px 12px 8px;
}
</style>
