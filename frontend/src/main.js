import { createApp } from 'vue'
import { createPinia } from 'pinia'
import 'element-plus/es/components/message-box/style/css'
import App from './App.vue'
import router from './router'

createApp(App).use(createPinia()).use(router).mount('#app')
