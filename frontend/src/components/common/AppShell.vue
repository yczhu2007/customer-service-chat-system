<template>
  <main class="app-shell">
    <header class="app-header">
      <span class="brand-name">Customer Service Chat</span>
      <span class="brand-subtitle">SERVICE WORKSPACE</span>
    </header>
    <slot />
  </main>
</template>
<style scoped>
.app-shell { min-height: 100vh; background: var(--color-bg); }
.app-header { display: flex; align-items: baseline; gap: 10px; min-height: 54px; padding: 0 24px; border-bottom: 1px solid var(--color-line); background: var(--color-paper); }
.brand-name { color: var(--color-ink); font-size: 15px; font-weight: 700; }
.brand-subtitle { color: var(--color-faint); font: 10px/1 var(--font-mono); letter-spacing: .16em; }
</style>
