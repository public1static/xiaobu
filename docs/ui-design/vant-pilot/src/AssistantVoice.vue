<script setup>
import { ref, onUnmounted } from 'vue';
import { Popup as VanPopup } from 'vant';
defineOptions({ inheritAttrs: false });
const props = defineProps({ shortcut: Boolean });
const emit = defineEmits(['tap', 'text', 'keyboard']);
const listening = ref(false),
  cancelled = ref(false),
  demo = ref(false),
  error = ref('');
const processing = ref(false),
  allowBrowser = ref(false),
  installing = ref(false);
const missingPack = ref(false);
let timer,
  deadline,
  origin,
  held = false,
  recognizer,
  micStream,
  transcript = '';
const Engine = window.SpeechRecognition || window.webkitSpeechRecognition;
const failures = {
  'not-allowed': '麦克风权限未开启，请在浏览器中允许使用麦克风。',
  'audio-capture': '没有可用的麦克风，请检查设备。',
  'no-speech': '没有听清，请按住再说一次。',
  'language-not-supported': '当前设备缺少中文语音识别包。',
  network: '语音识别连接失败，请重试。',
};
// 清理引擎并忽略上一次录音迟到的回调。
function release(abort = false) {
  clearTimeout(deadline);
  const r = recognizer;
  recognizer = null;
  micStream?.getTracks().forEach((track) => track.stop());
  micStream = null;
  if (r && abort)
    try {
      r.abort();
    } catch {}
}
// 每次松手只提交一次；收到结果即可结束等待，不依赖结束事件。
function finish() {
  processing.value = false;
  release(true);
  if (transcript.trim()) emit('text', transcript.trim());
  else {
    error.value ||= '没有识别到文字，请按住再说一次。';
    demo.value = true;
  }
}
async function begin() {
  held = true;
  cancelled.value = false;
  transcript = '';
  error.value = '';
  missingPack.value = false;
  if (!Engine) {
    error.value = '当前浏览器不支持语音识别。';
    demo.value = true;
    return;
  }
  let r;
  try {
    r = new Engine();
    if (!allowBrowser.value && !('processLocally' in r)) {
      error.value = '当前浏览器不支持设备端语音识别。';
      demo.value = true;
      return;
    }
    recognizer = r;
    if ('processLocally' in r) r.processLocally = !allowBrowser.value;
    r.lang = 'zh-CN';
    r.continuous = true;
    r.interimResults = false;
    r.onresult = (e) => {
      if (recognizer !== r) return;
      transcript = Array.from(e.results)
        .map((result) => result[0].transcript)
        .join('')
        .replace(/(\p{Script=Han})\s+(?=\p{Script=Han})/gu, '$1');
      if (processing.value) finish();
    };
    r.onerror = (e) => {
      if (recognizer !== r) return;
      missingPack.value =
        e.error === 'language-not-supported' &&
        !allowBrowser.value &&
        typeof Engine.install === 'function';
      error.value = failures[e.error] || '语音识别暂不可用，请重试。';
      release();
      if (processing.value) finish();
    };
    r.onend = () => {
      if (recognizer === r) {
        release();
        if (processing.value) finish();
      }
    };
    listening.value = true;
    if (r.processLocally && navigator.mediaDevices?.getUserMedia) {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false },
      });
      if (recognizer !== r || !listening.value) {
        stream.getTracks().forEach((track) => track.stop());
        return;
      }
      micStream = stream;
      r.start(stream.getAudioTracks()[0]);
    } else r.start();
  } catch (e) {
    if (r && recognizer !== r) return;
    release();
    processing.value = false;
    listening.value = false;
    error.value =
      e.name === 'NotAllowedError' ? failures['not-allowed'] : '无法启动麦克风，请检查浏览器权限。';
    demo.value = true;
  }
}
// 由用户主动安装中文识别包；成功后下一次按住即可使用本地识别。
async function installChinese() {
  if (installing.value) return;
  installing.value = true;
  try {
    if (!(await Engine.install({ langs: ['zh-CN'], processLocally: true }))) throw new Error();
    missingPack.value = false;
    error.value = '中文语音包已安装，请关闭后重新按住说话。';
  } catch {
    error.value = '中文语音包未安装成功，请重试或选择其他输入方式。';
  } finally {
    installing.value = false;
  }
}
// 仅在用户选择后由浏览器处理语音，录音仍需再次按住开始。
function enableBrowser() {
  allowBrowser.value = true;
  demo.value = false;
}
function down(e) {
  if (e.button !== 0 || processing.value) return;
  origin = e.clientY;
  held = false;
  cancelled.value = false;
  e.currentTarget.setPointerCapture(e.pointerId);
  timer = setTimeout(begin, props.shortcut ? 420 : 180);
}
function up(abort = false) {
  clearTimeout(timer);
  if (!listening.value) return;
  listening.value = false;
  if (abort || cancelled.value) {
    release(true);
    return;
  }
  if (!recognizer || transcript) {
    finish();
    return;
  }
  processing.value = true;
  deadline = setTimeout(() => {
    error.value = '识别超时，请重新说一次。';
    finish();
  }, 5000);
  try {
    recognizer.stop();
  } catch {
    finish();
  }
}
function click() {
  if (!held && !processing.value) {
    if (props.shortcut) emit('tap');
    else {
      error.value = '请按住说话，松开后发送。';
      demo.value = true;
    }
  }
  held = false;
}
onUnmounted(() => {
  clearTimeout(timer);
  release(true);
});
</script>
<template>
  <button
    type="button"
    v-bind="$attrs"
    class="voice-trigger"
    :class="shortcut ? 'voice-shortcut' : 'voice-pad'"
    :data-listening="listening"
    @pointerdown="down"
    @pointermove="cancelled = listening && origin - $event.clientY > 48"
    @pointerup="up()"
    @pointercancel="up(true)"
    @contextmenu.prevent
    @click="click"
  >
    <slot :listening="listening" :cancelled="cancelled" />
  </button>
  <Teleport to="body"
    ><div v-if="listening || processing" role="status" class="voice-hold">
      <b>▂ ▅ ▇ ▅ ▂</b
      >{{ processing ? '正在识别…' : cancelled ? '松开取消' : '松开发送 · 上滑取消' }}
    </div></Teleport
  >
  <VanPopup v-model:show="demo" teleport="body" position="bottom" round class="voice-demo"
    ><h3>语音输入</h3>
    <p>{{ error }}</p>
    <button v-if="missingPack" :disabled="installing" @click="installChinese">
      {{ installing ? '正在安装…' : '安装中文语音包' }}
    </button>
    <template v-if="Engine && !allowBrowser"
      ><p>使用浏览器识别时，语音可能由浏览器服务商在线处理。</p>
      <button @click="enableBrowser">启用浏览器语音识别</button></template
    >
    <button
      @click="
        demo = false;
        emit('keyboard');
      "
    >
      改用文字输入
    </button>
    <small>体验示例</small
    ><button
      v-for="q in ['这个月支出多少', '昨天微信买菜 68']"
      :key="q"
      @click="
        demo = false;
        emit('text', q);
      "
    >
      {{ q }}</button
    ><button @click="demo = false">取消</button></VanPopup
  >
</template>
<style scoped>
.voice-trigger {
  appearance: none;
  -webkit-appearance: none;
  cursor: pointer;
  background: transparent;
  border: 0;
  touch-action: none;
  user-select: none;
  -webkit-touch-callout: none;
  font: inherit;
  color: inherit;
}
.voice-hold {
  position: fixed;
  z-index: 3500;
  bottom: 22%;
  left: 50%;
  transform: translateX(-50%);
  border-radius: 14px;
  padding: 18px 22px;
  background: #173e33;
  color: white;
  text-align: center;
  pointer-events: none;
}
.voice-hold b {
  display: block;
  margin-bottom: 8px;
}
.voice-demo {
  max-width: 440px;
  inset-inline: 0;
  margin: auto;
  padding: 18px;
}
.voice-demo p {
  color: var(--muted);
  font-size: 14px;
}
.voice-demo button {
  min-height: 44px;
  display: block;
  width: 100%;
  background: white;
  border: 0;
  border-top: 1px solid var(--line);
  color: var(--green);
  font: inherit;
}
.voice-pad {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 9px;
  align-self: stretch;
  flex: 1;
  min-width: 0;
  min-height: 44px;
  border-radius: 17px;
  background: #e7f1e9;
  color: #175843;
  font-size: 15px;
  font-weight: 650;
}
.voice-pad[data-listening='true'] {
  background: #1b634e;
  color: white;
}
</style>
