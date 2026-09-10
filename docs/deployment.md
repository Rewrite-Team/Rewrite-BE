# OCI 수동 배포 가이드

이 문서는 Oracle Cloud Infrastructure(OCI)에 Rewrite 백엔드를 수동 배포하고 점검하는 절차를 정리한다. GitHub Actions CD 자동화는 [#113](https://github.com/Rewrite-Team/Rewrite-BE/issues/113)의 범위이며 이 문서에서는 다루지 않는다.

관련 기준은 `REQ-007`과 [Decision 101](decisions/persistence.md#decision-101-실행-프로필은-db와-인증-세부-프로필을-조합한다)이다. 제품 기능과 공개 API 계약은 변경하지 않는다.

## 현재 배포 기준

| 항목 | 값 |
|---|---|
| OCI 리전 | South Korea North (Chuncheon) |
| Compute shape | `VM.Standard.E2.1.Micro` |
| Compute 자원 | 1 OCPU, 1 GB RAM, 0.48 Gbps |
| 아키텍처 | `amd64` |
| 이미지 | Canonical Ubuntu 24.04 |
| 실행 사용자 | 기본 `ubuntu` 사용자 |
| Java | OpenJDK 21 |
| 데이터베이스 | PostgreSQL 17, 같은 인스턴스의 loopback에서만 연결 |
| Reverse proxy | Nginx |
| 도메인 | `playmcpfinder.store` |
| 애플리케이션 주소 | `127.0.0.1:8080` |

이 shape는 메모리가 1 GB이므로 애플리케이션 JVM heap을 384 MB로 제한하고 2 GB swap을 사용한다. 별도 애플리케이션 사용자는 만들지 않고 `ubuntu`로 실행하되 systemd의 권한 제한 옵션을 적용한다. `prod`는 사용자-facing 실행 profile이며 `db-postgres`, `auth-real`, `internal-tools-secured` 세부 profile을 함께 활성화한다.

```text
Internet
  -> Reserved Public IP
  -> OCI VCN public subnet
  -> TCP 80/443: Nginx
  -> 127.0.0.1:8080: Spring Boot
  -> 127.0.0.1:5432: PostgreSQL
```

## OCI 네트워크

1. Compute 인스턴스를 public subnet에 연결한다.
2. VCN에 Internet Gateway를 연결한다.
3. subnet이 사용하는 route table에 다음 규칙을 추가한다.
   - Destination: `0.0.0.0/0`
   - Target: Internet Gateway
4. Reserved Public IP를 인스턴스의 primary private IP에 할당한다.
5. DNS의 A record가 Reserved Public IP를 가리키게 한다.
6. Network Security Group 또는 Security List에는 다음 stateful ingress만 허용한다.

| Source | Protocol/port | 용도 |
|---|---|---|
| 관리자 공인 IP `/32` | TCP 22 | SSH |
| `0.0.0.0/0` | TCP 80 | HTTP 및 HTTPS redirect |
| `0.0.0.0/0` | TCP 443 | HTTPS |

TCP 8080과 5432 ingress는 만들지 않는다. 관리자 공인 IP가 변경되면 SSH 22의 source를 먼저 새 `/32` 값으로 교체한 뒤 기존 규칙을 제거한다.

Canonical OCI Ubuntu 이미지에는 OCI 기본 iptables 규칙이 있으므로 Nginx 설치 후 OS 방화벽에도 80과 443을 허용한다. 기존 규칙을 flush하지 않고 최종 `REJECT` 규칙보다 앞에 추가한다.

```bash
sudo iptables -I INPUT -p tcp --dport 80 -j ACCEPT
sudo iptables -I INPUT -p tcp --dport 443 -j ACCEPT
sudo apt-get install -y iptables-persistent
sudo netfilter-persistent save
```

적용 결과는 다음 명령으로 확인한다.

```bash
sudo iptables -L INPUT -n --line-numbers
sudo systemctl is-enabled netfilter-persistent
```

## 서버 기본 구성

패키지를 갱신하고 2 GB swap을 만든다.

```bash
sudo apt-get update
sudo apt-get upgrade -y
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

Java 21과 Nginx를 설치한다.

```bash
sudo apt-get install -y openjdk-21-jre-headless nginx curl ca-certificates
java -version
readlink -f "$(command -v java)"
```

PostgreSQL 공식 PGDG 저장소에서 PostgreSQL 17을 설치한다.

```bash
sudo install -d /usr/share/postgresql-common/pgdg
sudo curl --fail --output /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
  https://www.postgresql.org/media/keys/ACCC4CF8.asc
echo 'deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt noble-pgdg main' \
  | sudo tee /etc/apt/sources.list.d/pgdg.list
sudo apt-get update
sudo apt-get install -y postgresql-17
```

애플리케이션 role과 database를 만든다. 실제 비밀번호를 명령행 인자나 저장소 파일에 남기지 않는다.

```bash
sudo -u postgres psql
```

```sql
CREATE ROLE rewrite_app LOGIN;
\password rewrite_app
CREATE DATABASE rewrite OWNER rewrite_app;
\q
```

PostgreSQL이 외부 인터페이스가 아닌 loopback에서만 대기하는지 확인한다.

```bash
sudo systemctl enable --now postgresql
sudo ss -lntp | grep ':5432'
sudo -u postgres pg_isready
```

## 애플리케이션 파일과 환경변수

서버 디렉터리를 준비한다.

```bash
mkdir -p /home/ubuntu/rewrite/releases /home/ubuntu/rewrite/config
chmod 700 /home/ubuntu/rewrite/config
```

개발 PC에서 검증하고 실행 JAR을 만든다.

```bash
./gradlew clean check bootJar
```

생성한 `build/libs/rewrite-*.jar`를 commit SHA가 포함된 이름으로 업로드하고 `current.jar` symlink를 교체한다.

```bash
scp build/libs/rewrite-0.0.1-SNAPSHOT.jar \
  ubuntu@playmcpfinder.store:/home/ubuntu/rewrite/releases/rewrite-<commit-sha>.jar

ssh ubuntu@playmcpfinder.store \
  'ln -sfn /home/ubuntu/rewrite/releases/rewrite-<commit-sha>.jar /home/ubuntu/rewrite/current.jar'
```

`/home/ubuntu/rewrite/config/rewrite.env`에는 다음 키를 넣는다. `KEY=value` 형식을 사용하고 `export`를 붙이지 않는다. 실제 값은 이 파일에만 저장하며 저장소, 이슈, 로그에 노출하지 않는다.

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=8080
SERVER_FORWARD_HEADERS_STRATEGY=framework
DB_URL=jdbc:postgresql://127.0.0.1:5432/rewrite
DB_USERNAME=rewrite_app
DB_PASSWORD='replace-me'
OPENAI_API_KEY='replace-me'
AUTH_JWT_SECRET_BASE64='replace-me'
FRONTEND_ORIGIN=http://localhost:3000
FRONTEND_SUCCESS_URL=http://localhost:3000
FRONTEND_LOGIN_URL=http://localhost:3000/login
KAKAO_CLIENT_ID='replace-me'
KAKAO_CLIENT_SECRET='replace-me'
KAKAO_REDIRECT_URI=https://playmcpfinder.store/auth/kakao/callback
INTERNAL_TOOLS_USERNAME='replace-me'
INTERNAL_TOOLS_PASSWORD='replace-me'
```

프론트엔드가 배포되면 `FRONTEND_ORIGIN`, `FRONTEND_SUCCESS_URL`, `FRONTEND_LOGIN_URL`을 실제 HTTPS 프론트엔드 URL로 교체한다. JWT secret과 내부 도구 비밀번호는 각각 별도로 생성한다.

```bash
openssl rand -base64 64 | tr -d '\n'; echo
openssl rand -base64 32 | tr -d '\n'; echo
chmod 600 /home/ubuntu/rewrite/config/rewrite.env
```

## systemd 서비스

`/etc/systemd/system/rewrite.service`를 다음과 같이 만든다.

```ini
[Unit]
Description=Rewrite Spring Boot Backend
Wants=network-online.target
After=network-online.target postgresql.service
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/home/ubuntu/rewrite
EnvironmentFile=/home/ubuntu/rewrite/config/rewrite.env
ExecStart=/usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms128m -Xmx384m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError -jar /home/ubuntu/rewrite/current.jar
Restart=on-failure
RestartSec=10
TimeoutStopSec=30
SuccessExitStatus=143
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ProtectKernelTunables=true
ProtectKernelModules=true
ProtectControlGroups=true
RestrictSUIDSGID=true
LockPersonality=true
CapabilityBoundingSet=
AmbientCapabilities=

[Install]
WantedBy=multi-user.target
```

서비스를 적용하고 `prod` profile 및 loopback binding을 확인한다.

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now rewrite
sudo journalctl -u rewrite -n 100 --no-pager
sudo ss -lntp | grep ':8080'
```

로그에는 활성 profile이 `prod`로 표시되어야 하고 8080은 `127.0.0.1:8080`에서만 대기해야 한다. 환경변수 파일을 바꾼 경우 `daemon-reload`가 아니라 `sudo systemctl restart rewrite`로 프로세스를 다시 시작한다.

## Nginx와 TLS

Nginx의 `server_name`을 `playmcpfinder.store`로 설정한 뒤 HTTPS server의 `location /`에 다음 proxy 설정을 둔다.

```nginx
location / {
    proxy_pass http://127.0.0.1:8080;
    proxy_http_version 1.1;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-Host $host;
    proxy_set_header X-Forwarded-Port $server_port;

    proxy_buffering off;
    proxy_cache off;
    proxy_read_timeout 3600s;
    proxy_send_timeout 3600s;
}
```

`proxy_buffering off`는 SSE 응답을 Nginx가 모아서 전달하지 않게 한다. 설정 문법을 확인한 뒤 reload한다.

```bash
sudo nginx -t
sudo systemctl reload nginx
```

Certbot으로 인증서를 발급하고 HTTP 요청을 HTTPS로 redirect한다.

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d playmcpfinder.store
sudo certbot renew --dry-run
```

Certbot이 관리하는 인증서 경로와 SSL include 구문은 수동으로 제거하지 않는다.

## 수동 배포와 rollback

새 버전은 기존 JAR을 덮어쓰지 않고 `releases`에 추가한다. symlink를 새 JAR로 교체한 뒤 서비스를 재시작하고 로그와 외부 HTTPS 응답을 확인한다.

```bash
ln -sfn /home/ubuntu/rewrite/releases/rewrite-<new-commit-sha>.jar \
  /home/ubuntu/rewrite/current.jar
sudo systemctl restart rewrite
sudo systemctl is-active rewrite
sudo journalctl -u rewrite -n 100 --no-pager
curl -i --max-time 10 https://playmcpfinder.store/
```

배포가 실패하면 직전 JAR로 symlink를 되돌리고 다시 시작한다.

```bash
ln -sfn /home/ubuntu/rewrite/releases/rewrite-<previous-commit-sha>.jar \
  /home/ubuntu/rewrite/current.jar
sudo systemctl restart rewrite
```

## PostgreSQL 백업과 복구 절차

백업 파일은 `/var/backups/rewrite`에 두고 `postgres`만 읽을 수 있게 한다.

```bash
sudo install -d -o postgres -g postgres -m 700 /var/backups/rewrite
sudo -u postgres pg_dump \
  --format=custom \
  --dbname=rewrite \
  --file=/var/backups/rewrite/rewrite-<timestamp>.dump
```

복구가 필요하면 새 database에 먼저 복원해 내용을 확인한 뒤 운영 전환 여부를 결정한다.

```bash
sudo -u postgres createdb --owner=rewrite_app rewrite_restore_check
sudo -u postgres pg_restore \
  --dbname=rewrite_restore_check \
  /var/backups/rewrite/rewrite-<timestamp>.dump
```

이번 OCI 초기 구성에서는 사용자의 결정에 따라 실제 백업 생성과 별도 database 복구 검증을 수행하지 않았다. 따라서 운영 데이터를 보존하기 전에 백업 주기, 외부 보관 위치와 복구 리허설을 별도로 확정해야 한다.

## 검증 기록

2026-09-10에 다음 항목을 수동 검증했다.

- Reserved Public IP와 `playmcpfinder.store` A record 연결
- 관리자 노트북의 SSH 공개키 접속
- JDK 21 `amd64`, PostgreSQL, Nginx와 Rewrite 서비스 실행
- Rewrite의 `prod` profile 활성화와 `127.0.0.1:8080` binding
- Nginx를 통한 외부 HTTPS 요청과 애플리케이션의 `401 UNAUTHORIZED` JSON 응답
- 외부 TCP 80/443 접근 허용과 8080/5432 접근 차단
- 서버 재부팅 후 PostgreSQL, Rewrite, Nginx와 방화벽 규칙 자동 복구
- Nginx의 SSE용 buffering 비활성화 설정과 설정 문법

실제 SSE 이벤트 전달과 PostgreSQL 백업·복구는 사용자의 결정에 따라 검증하지 않았다.
