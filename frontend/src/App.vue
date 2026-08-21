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
}
* { box-sizing: border-box; }
html, body, #app { min-height: 100%; margin: 0; }
body { color: var(--color-ink); background: var(--color-bg); font: 14px/1.55 var(--font-sans); }
button, input, select, textarea { font: inherit; }
button { border: 1px solid var(--color-line-strong); border-radius: 8px; background: transparent; color: var(--color-ink); cursor: pointer; }
button:hover:not(:disabled) { border-color: var(--color-primary); color: var(--color-primary); }
button:disabled { cursor: not-allowed; opacity: .45; }
input, select, textarea { border: 1px solid var(--color-line-strong); border-radius: 8px; background: var(--color-paper); color: var(--color-ink); }
input:focus, select:focus, textarea:focus { border-color: var(--color-primary); box-shadow: 0 0 0 3px rgba(77, 107, 254, .14); outline: none; }
</style>
