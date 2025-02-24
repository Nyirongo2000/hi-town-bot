# Hi Town Bot

Welcome!

Implement your bot in `Bot.kt`

# Ubuntu server setup guide

### 1. Install default set of dependencies

```bash
apt update
apt install default-jre nginx certbot python3-certbot-nginx
```

### 2. Configure Nginx

```bash
server {
    server_name <enter bot domain here>;

    location / {
        proxy_pass http://localhost:8080;
    }
}
```

### 3. Configure Certbot

```bash
certbot --nginx
service nginx restart
```

### 4. Run your bot

```bash
nohup java -jar '<enter bot name here>.jar' > log.txt 2> errors.txt < /dev/null &
```
### 5. Stop your bot

```bash
killall java
```