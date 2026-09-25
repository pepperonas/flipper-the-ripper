# Flipper the Ripper — product page. Vendored copy of /etc/nginx/sites-available/flipper-the-ripper.celox.io
# One certificate covers all three names; the two short names answer with a 301.

server {
    listen 80;
    listen [::]:80;
    server_name flipper-the-ripper.celox.io flippertheripper.celox.io ftr.celox.io;
    location /.well-known/acme-challenge/ { root /var/www/html; }
    location / { return 301 https://flipper-the-ripper.celox.io$request_uri; }
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name flippertheripper.celox.io ftr.celox.io;
    ssl_certificate     /etc/letsencrypt/live/flipper-the-ripper.celox.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/flipper-the-ripper.celox.io/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
    return 301 https://flipper-the-ripper.celox.io$request_uri;
}

server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name flipper-the-ripper.celox.io;
    ssl_certificate     /etc/letsencrypt/live/flipper-the-ripper.celox.io/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/flipper-the-ripper.celox.io/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    root /var/www/flipper-the-ripper.celox.io;
    index index.html;
    charset utf-8;
    charset_types text/plain text/css application/javascript application/json text/xml;

    # Security headers are repeated in every location that sets its own add_header
    # (an add_header in a block drops everything inherited).
    add_header Strict-Transport-Security "max-age=31536000" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
    add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'none'" always;

    # /download -> 302 to the newest APK; written by ftr-latest.py (glob: absent file is fine).
    include /etc/nginx/ftr-download*.conf;

    location = /latest.json {
        add_header Cache-Control "no-cache" always;
        add_header X-Content-Type-Options "nosniff" always;
    }
    location /assets/ {
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        add_header X-Content-Type-Options "nosniff" always;
    }
    location ~ \.(css|js)$ {
        add_header Cache-Control "public, max-age=31536000, immutable" always;
        add_header X-Content-Type-Options "nosniff" always;
    }
    location / {
        add_header Cache-Control "no-cache" always;
        add_header Strict-Transport-Security "max-age=31536000" always;
        add_header X-Content-Type-Options "nosniff" always;
        add_header Referrer-Policy "strict-origin-when-cross-origin" always;
        add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
        add_header Content-Security-Policy "default-src 'self'; img-src 'self' data:; style-src 'self'; script-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'none'" always;
        try_files $uri $uri/ =404;
    }
}
