<script setup>
import AppShell from './components/common/AppShell.vue'
</script>

<template><AppShell><router-view /></AppShell></template>

<style>
:root {
  --color-bg: #f7f8fa;
  --color-paper: #ffffff;
  --color-line: #e7e9ee;
  --color-line-strong: #d6dae2;
  --color-ink: #181d26;
  --color-primary: #4d6bfe;
  --color-primary-hover: #3a56e0;
  --color-muted: #6a7280;
  --color-faint: #9aa2ae;
  --color-success: #1f8a5f;
  --color-danger: #dc2626;
  --font-sans: "PingFang SC", "Microsoft YaHei", "Noto Sans SC", system-ui, sans-serif;
  --font-mono: "Cascadia Code", Consolas, "Courier New", monospace;
  --el-color-primary: #4d6bfe;
  --el-color-primary-light-3: #7d92fe;
  --el-color-primary-light-5: #a6b4fe;
  --el-color-primary-light-7: #cdd5ff;
  --el-color-primary-light-8: #e0e5ff;
  --el-color-primary-light-9: #f0f2ff;
  --el-color-primary-dark-2: #3a56e0;
  --el-color-success: #1f8a5f;
  --el-color-danger: #dc2626;
  --el-text-color-primary: #181d26;
  --el-text-color-regular: #6a7280;
  --el-border-color: #e7e9ee;
  --el-border-color-light: #e7e9ee;
  --el-border-radius-base: 8px;
  --el-font-family: var(--font-sans);
}
.el-button,
.el-input,
.el-select,
.el-form,
.el-alert { font-family: var(--font-sans); }
.el-button { border-radius: 8px; }
.el-input__wrapper,
.el-select__wrapper {
  min-height: 40px;
  padding: 1px 11px;
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--color-line-strong) inset;
  background: var(--color-paper);
}
.el-input__wrapper:hover,
.el-select__wrapper:hover { box-shadow: 0 0 0 1px var(--color-primary) inset; }
.el-input__wrapper.is-focus,
.el-select__wrapper.is-focused {
  box-shadow: 0 0 0 1px var(--color-primary) inset, 0 0 0 3px rgba(77, 107, 254, .14);
}
.el-form-item { margin-bottom: 14px; }
.el-form-item__label {
  height: auto;
  padding: 0;
  margin-bottom: 6px;
  color: var(--color-muted);
  font-size: 12px;
  line-height: 1.4;
}
.el-form-item__content { line-height: normal; }
.el-alert { border-radius: 8px; }
.el-button--primary { --el-button-bg-color: var(--color-primary); --el-button-border-color: var(--color-primary); --el-button-hover-bg-color: var(--color-primary-hover); --el-button-hover-border-color: var(--color-primary-hover); }


html, body, #app { min-height: 100%; margin: 0; }
body { color: var(--color-ink); background: var(--color-bg); font: 14px/1.55 var(--font-sans); }
button:not(.el-button), input:not(.el-input__inner), select, textarea:not(.el-textarea__inner) { font: inherit; }
button:not(.el-button) { border: 1px solid var(--color-line-strong); border-radius: 8px; background: transparent; color: var(--color-ink); cursor: pointer; }
button:not(.el-button):hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
button:not(.el-button):disabled { cursor: not-allowed; opacity: .45; }
input:not(.el-input__inner):not(.el-select__input), select, textarea:not(.el-textarea__inner) { border: 1px solid var(--color-line-strong); border-radius: 8px; background: var(--color-paper); color: var(--color-ink); }
input:not(.el-input__inner):not(.el-select__input):focus, select:focus, textarea:not(.el-textarea__inner):focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(77, 107, 254, .14); outline: none; }
</style>
