<script setup>
import AppShell from './components/common/AppShell.vue'
</script>

<template><AppShell><router-view /></AppShell></template>

<style>
* { box-sizing: border-box; }
html, body, #app { min-height: 100%; margin: 0; }
button, input, select, textarea { font: inherit; }
button { border-radius: 4px; }
</style>
