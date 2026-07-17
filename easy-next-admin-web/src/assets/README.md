# src/assets

这里放需要被前端代码 `import` 的本地资源，例如页面插图、可复用 SVG、需要参与 Vite 打包和哈希命名的图片。

不需要在代码里导入、只按固定 URL 访问的资源放到 `public/`。例如 favicon、apple-touch-icon 这类浏览器入口资源，放在 `public/` 后可以直接通过 `/favicon.ico` 访问。

当前目录保留 README，是为了让脚手架使用者明确 `src/assets` 和 `public` 的分工；没有业务图片时不需要强行放占位图。
