FROM nginx:1.27-alpine

ENV API_PROXY_PASS=http://host.docker.internal:8080

COPY nginx.conf /etc/nginx/templates/default.conf.template
COPY dist /usr/share/nginx/html

EXPOSE 80
