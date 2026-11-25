import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const require = createRequire(import.meta.url);
const iconSource = readFileSync(require.resolve('@vant/icons/src/encode-woff2.less'), 'utf8');
const iconData = iconSource.match(/data:font\/woff2[^'"]+/)?.[0];
if (!iconData) throw new Error('Missing bundled Vant icon font.');

export default defineConfig({
  base: './',
  plugins: [
    {
      name: 'self-host-vant-icons',
      enforce: 'pre',
      transform(code, id) {
        if (!id.endsWith('/vant/lib/index.css')) return;
        return code.replace(
          /url\((?:https?:)?\/\/at\.alicdn\.com\/[^)]+\)/g,
          'url("' + iconData + '")',
        );
      },
    },
    vue(),
  ],
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
});
