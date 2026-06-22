import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import { setupPermissionDirective } from './directives/permission'
import './styles/index.css'

const app = createApp(App)

// 前端启动入口：全局能力只在这里注册，业务页面通过 router/store/api 解耦。
app.use(createPinia())
app.use(ElementPlus)
app.use(router)
setupPermissionDirective(app)
app.mount('#app')
