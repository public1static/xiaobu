import { execFileSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const appId = process.env.XIAOBU_WECHAT_APP_ID ?? '';
const api = process.env.VITE_XIAOBU_WECHAT_API_BASE ?? '';
if (!/^wx[a-fA-F0-9]{16}$/.test(appId)) {
  throw new Error('Set the dedicated WeChat mini-program XIAOBU_WECHAT_APP_ID.');
}
if (!/^https:\/\/[A-Za-z0-9.-]+(?::[0-9]+)?\/?$/.test(api)) {
  throw new Error('Set VITE_XIAOBU_WECHAT_API_BASE to the approved HTTPS API origin.');
}

const frontend = fileURLToPath(new URL('../frontend/', import.meta.url));
execFileSync('npm', ['run', 'build:mp-weixin'], { cwd: frontend, stdio: 'inherit' });

const project = new URL('../frontend/dist/build/mp-weixin/project.config.json', import.meta.url);
const config = JSON.parse(readFileSync(project, 'utf8'));
config.appid = appId;
writeFileSync(project, `${JSON.stringify(config, null, 2)}\n`);
