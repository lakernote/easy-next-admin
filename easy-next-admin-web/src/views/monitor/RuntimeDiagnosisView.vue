<template>
  <section class="observability-frame" aria-label="Easy Observability 运行诊断">
    <iframe
      class="observability-frame__content"
      src="/easy-observability/"
      title="Easy Observability 运行诊断"
      loading="eager"
      referrerpolicy="same-origin"
    />
  </section>
</template>

<style scoped>
.observability-frame {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--ea-border);
  border-radius: 10px;
  background: #f4f6f9;
}

.observability-frame__content {
  display: block;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  border: 0;
  background: #f4f6f9;
}
</style>
