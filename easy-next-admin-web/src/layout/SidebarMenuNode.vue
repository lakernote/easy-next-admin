<template>
  <el-sub-menu v-if="item.children?.length" :index="sidebarMenuIndex(item)">
    <template #title>
      <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
      <span>{{ item.title }}</span>
    </template>
    <SidebarMenuNode v-for="child in item.children" :key="sidebarMenuIndex(child)" :item="child" :resolve-icon="resolveIcon" />
  </el-sub-menu>
  <el-menu-item v-else :index="sidebarMenuIndex(item)" :disabled="!item.path">
    <el-icon><component :is="resolveIcon(item.icon)" /></el-icon>
    <span>{{ item.title }}</span>
  </el-menu-item>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { sidebarMenuIndex, type SidebarMenuItem } from './sidebarMenus'

defineProps<{
  item: SidebarMenuItem
  resolveIcon: (name?: string) => Component
}>()
</script>
