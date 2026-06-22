<template>
  <el-avatar class="user-avatar" :size="size" :src="src">
    {{ initial }}
  </el-avatar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { userAvatarInitial, userAvatarSrc, type UserAvatarSubject } from '@/features/profile/avatar'

const props = withDefaults(defineProps<{
  user?: UserAvatarSubject
  size?: number
}>(), {
  size: 32
})

const src = computed(() => userAvatarSrc(props.user))
const initial = computed(() => userAvatarInitial(props.user))
</script>

<style scoped>
.user-avatar {
  flex: 0 0 auto;
  background: #eff6ff;
  color: var(--ea-primary);
  font-weight: 700;
}

.user-avatar :deep(img) {
  object-fit: cover;
}
</style>
