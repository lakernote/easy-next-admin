FROM nginx:1.27-alpine

# 容器内 Nginx 通过环境变量反向代理后端，默认指向宿主机 8080。
ENV API_PROXY_PASS=http://host.docker.internal:8080

# 前端镜像只负责托管 npm run build 后的 dist 静态文件。
COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY dist /usr/share/nginx/html

EXPOSE 80
