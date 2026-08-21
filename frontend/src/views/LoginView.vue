<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
const router = useRouter(); const auth = useAuthStore(); const username = ref(''); const password = ref('')
function submit() { auth.login({ token: 'demo-token', userId: username.value || 'demo-user', role: 'USER' }); router.push('/user') }
</script>
<template><section><h1>登录</h1><form @submit.prevent="submit"><input v-model="username" placeholder="用户名"><input v-model="password" type="password" placeholder="密码"><button>进入工作台</button></form></section></template>
