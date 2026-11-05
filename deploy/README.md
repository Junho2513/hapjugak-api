# 무중단 배포 — 서버에 한 번만 하는 설치

이 폴더의 파일들은 **서버에 올라가는 것**입니다. 애플리케이션 코드가 아니라서
`src` 밖에 따로 두었습니다. 저장소에 함께 두는 이유는, 서버에만 있던 `deploy.sh` 가
어느 날 사라지면 복구할 길이 없고 무엇이 바뀌었는지도 추적할 수 없기 때문입니다.

| 파일 | 서버에서의 자리 |
|---|---|
| `deploy.sh` | `/home/ubuntu/deploy.sh` |
| `hapjugak-upstream.conf` | `/etc/nginx/conf.d/hapjugak-upstream.conf` |
| `sudoers-hapjugak` | `/etc/sudoers.d/hapjugak-deploy` |

---

## 왜

전에는 `docker compose up -d api` 가 돌고 있던 컨테이너를 내리고 새로 띄웠습니다.
새 JVM 이 뜨는 20여 초 동안 사이트가 끊겼습니다.

이제 안 쓰는 쪽에 새 것을 먼저 띄우고, **떴는지 확인한 뒤**, nginx 가 보는 포트만
바꿉니다. `nginx -s reload` 는 이미 붙어 있는 연결을 끊지 않습니다.

끊김이 사라지는 것보다 큰 소득이 하나 더 있습니다. 새 컨테이너가 안 뜨면
**그것만 버리고 끝냅니다.** 옛 것이 그대로 서비스하므로 배포가 실패해도 사이트가
죽지 않습니다. 전에는 교체부터 하고 나서 실패를 알았습니다.

---

## 설치 (서버 터미널에서, 위에서 아래로)

아래 순서대로 하면 **설치하는 동안에도 사이트가 끊기지 않습니다.**
지금 돌고 있는 `hapjugak-api` 는 8080 을 쓰고 있으므로, 비어 있는 8081 에
새 것을 먼저 띄우고 넘긴 다음 옛 것을 내립니다.

### 1. 최신 코드를 받는다

```bash
cd /home/ubuntu/hapjugak-api
git fetch origin && git reset --hard origin/master
```

### 2. nginx 가 upstream 을 보도록 바꾼다

```bash
sudo cp deploy/hapjugak-upstream.conf /etc/nginx/conf.d/hapjugak-upstream.conf
sudo chown ubuntu:ubuntu /etc/nginx/conf.d/hapjugak-upstream.conf
```

`chown` 을 하는 이유: `deploy.sh` 가 `ubuntu` 로 돌면서 이 파일을 고쳐 써야 합니다.
`/etc/nginx` 안의 파일을 `ubuntu` 가 쓸 수 있게 하는 것이 마음에 걸릴 수 있는데,
`ubuntu` 는 이미 docker 그룹에 있어서 사실상 root 와 같은 일을 할 수 있습니다.
새로 열리는 권한은 없습니다.

그다음 `proxy_pass` 를 이름으로 바꿉니다.

```bash
sudo sed -i 's|proxy_pass http://127.0.0.1:8080;|proxy_pass http://hapjugak_api;|' \
  /etc/nginx/sites-enabled/hapjugak
sudo nginx -t
```

`nginx -t` 가 `syntax is ok / test is successful` 이라고 해야 다음으로 갑니다.
아니면 멈추고 알려주세요.

```bash
sudo systemctl reload nginx
```

여기까지는 아무것도 안 바뀝니다. `hapjugak_api` 가 8080 을 가리키고 있어서
전과 똑같이 돕니다. 사이트가 멀쩡한지 한 번 열어 보세요.

### 3. deploy.sh 를 바꾼다

```bash
cp /home/ubuntu/deploy.sh /home/ubuntu/deploy.sh.bak
cp deploy/deploy.sh /home/ubuntu/deploy.sh
chmod +x /home/ubuntu/deploy.sh
```

### 4. nginx reload 권한을 준다

`deploy.sh` 는 `ubuntu` 로 도는데 nginx reload 는 root 가 해야 합니다.
비밀번호 없이 **그 두 가지만** 할 수 있게 열어줍니다.

```bash
sudo cp deploy/sudoers-hapjugak /etc/sudoers.d/hapjugak-deploy
sudo chmod 0440 /etc/sudoers.d/hapjugak-deploy
sudo visudo -c
```

`parsed OK` 가 나와야 합니다. 안 나오면 바로 지우세요
(`sudo rm /etc/sudoers.d/hapjugak-deploy`). sudoers 가 깨지면 `sudo` 자체가
안 먹습니다.

확인:

```bash
sudo -n nginx -t
```

비밀번호를 안 묻고 `test is successful` 이 나오면 된 겁니다.

### 5. green 을 띄우고 넘긴다 (여기서도 안 끊깁니다)

```bash
cd /home/ubuntu/hapjugak-api
IMAGE_TAG=$(git rev-parse HEAD) docker compose up -d api-green
```

뜰 때까지 기다렸다가 확인합니다. 20초쯤 걸립니다.

```bash
curl -s http://127.0.0.1:8081/api/actuator/health
```

`{"status":"UP"}` 가 나와야 합니다. 안 나오면 30초 뒤 다시,
그래도 안 나오면 `docker compose logs --tail 50 api-green` 을 보고 멈추세요.

UP 이면 넘깁니다.

```bash
printf 'upstream hapjugak_api {\n    server 127.0.0.1:8081;\n}\n' \
  > /etc/nginx/conf.d/hapjugak-upstream.conf
sudo nginx -t && sudo systemctl reload nginx
```

사이트를 열어 멀쩡한지 봅니다.

### 6. 옛 컨테이너를 내린다

```bash
docker rm -f hapjugak-api
```

이제 green 하나만 떠 있고, 다음 배포는 blue 로 갑니다.

```bash
docker ps
```

`hapjugak-api-green` 과 `hapjugak-db` 두 개만 보이면 끝입니다.

### 7. 진짜로 무중단인지 확인한다

터미널 하나에서 1초마다 두드려 놓고, 다른 데서 아무 커밋이나 푸시해 봅니다.

```bash
while true; do
  printf '%s %s\n' "$(date +%T)" "$(curl -s -o /dev/null -w '%{http_code}' https://hapjugak.com/api/events)"
  sleep 1
done
```

배포가 도는 동안 `200` 만 쭉 나와야 합니다. 하나라도 `502` 나 `504` 가 섞이면
알려주세요. 끝내려면 `Ctrl+C`.

---

## 되돌리기

무언가 잘못되면 옛 방식으로 돌아갈 수 있습니다.

```bash
cp /home/ubuntu/deploy.sh.bak /home/ubuntu/deploy.sh
sudo sed -i 's|proxy_pass http://hapjugak_api;|proxy_pass http://127.0.0.1:8080;|' \
  /etc/nginx/sites-enabled/hapjugak
sudo nginx -t && sudo systemctl reload nginx
docker rm -f hapjugak-api-green hapjugak-api-blue 2>/dev/null
cd /home/ubuntu/hapjugak-api && IMAGE_TAG=$(git rev-parse HEAD) docker compose up -d api-blue
```

(옛 `deploy.sh` 는 `api` 라는 이름의 서비스를 찾으므로, 되돌릴 때는
`docker-compose.yml` 도 함께 되돌려야 합니다 — `git checkout <옛커밋> -- docker-compose.yml`.)

---

## 이 방식이 거는 제약

전환하는 몇 십 초 동안 **옛 코드가 새 스키마 위에서 돕니다.** 마이그레이션은
새 컨테이너가 뜰 때 돌기 때문입니다.

- 칼럼을 **더하거나** NOT NULL 을 **푸는** 변경 → 옛 코드가 견딥니다
- 칼럼이나 테이블을 **지우는** 변경 → 그 사이에 옛 컨테이너가 터집니다

그런 변경은 두 번에 나눠서 합니다.

1. **배포 A** — 새 칼럼을 더하고, 코드가 새것·옛것을 모두 읽게 한다
2. **배포 B** — 옛 칼럼을 지운다 (이때는 아무도 그걸 보지 않는다)

끊김을 없애는 대신 생긴 제약입니다. 같은 이야기를 `deploy.sh` 머리말에도
적어두었습니다 — 마이그레이션을 쓸 때 보게 되는 쪽은 그쪽이라서요.

---

## 메모리

이 기계는 RAM 이 909MB 입니다. 전환하는 몇 십 초 동안 JVM 두 개가 같이 뜨므로
스왑을 탑니다(스왑 2GB 가 잡혀 있습니다). 새 컨테이너가 뜨는 데 평소보다
오래 걸릴 수 있지만, 그동안 옛 것이 서비스하므로 사용자는 모릅니다.

정말 모자라면 커널이 무언가를 죽입니다. `docker-compose.yml` 에서 API 쪽
`oom_score_adj` 를 400 으로 올려, 커널이 **DB 보다 API 를 먼저** 고르게 해
두었습니다. API 가 죽으면 `restart: always` 가 다시 띄우지만 DB 는 그렇지
않습니다.
