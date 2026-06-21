import { createApp } from "vue";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import App from "./App.vue";
import "tdesign-vue-next/es/style/index.css";
// 组件库按组件拆分了 scoped 样式（data-v-*），不会随 JS 自动注入；
// 需整体导入，否则 KAlert 图标、对话框结构等会失去尺寸约束而错乱。
// 用相对路径绕过包 exports 限制（exports 仅暴露 . 与 ./components/*）。
import.meta.glob("../node_modules/@kk-2004/ui-components/dist/es/components/**/*.css", {
  eager: true,
});
import "./style.css";

createApp(App).use(ElementPlus).mount("#app");
