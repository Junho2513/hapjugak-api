#!/bin/bash
#
# 무중단 배포.
#
# 전에는 `docker compose up -d api` 한 줄이었습니다. 돌고 있던 컨테이너를 내리고
# 새로 띄우므로, 새 JVM 이 뜨는 20여 초 동안 사이트가 끊겼습니다.
#
# 지금은 이렇게 합니다.
#   1. 지금 안 쓰는 쪽(blue↔green)에 새 컨테이너를 띄운다 — 옛 것은 계속 서비스 중
#   2. 그쪽에 /api/actuator/health 를 물어 UP 이 될 때까지 기다린다
#   3. UP 이 아니면 새 것만 버리고 끝낸다 — 사이트는 옛 것이 그대로 서비스한다
#   4. UP 이면 nginx 가 보는 포트를 바꾸고 reload 한다
#   5. 화면 파일을 복사한다
#   6. 옛 컨테이너를 내린다
#
# 2번을 넣는 것이 요점입니다. 포트가 열렸다는 것과 요청을 받을 준비가 됐다는 것은
# 다릅니다. JVM 이 뜨고 Flyway 가 돌고 DB 연결이 잡히기까지 20초쯤 걸리는데,
# 그 사이에 트래픽을 넘기면 사용자가 503 을 봅니다.
#
# 3번은 덤으로 따라온 소득입니다. 끊김을 없애려고 시작했는데, 결과적으로
# 「배포가 실패해도 사이트가 안 죽는다」가 같이 생겼습니다.
#
# ── 이 방식이 거는 제약 하나 ─────────────────────────────────────────
# 4번과 6번 사이에 옛 코드와 새 코드가 잠깐 함께 돕니다. 그 동안 옛 코드는
# 이미 바뀐 스키마 위에서 돕니다. 마이그레이션은 새 컨테이너가 뜰 때 돌기 때문입니다.
#
# 칼럼을 더하거나 NOT NULL 을 푸는 변경은 옛 코드가 견딥니다.
# 칼럼이나 테이블을 지우는 변경은 그 몇 십 초 동안 옛 컨테이너가 터집니다.
#
# 그런 변경은 두 번에 나눠서 합니다.
#   배포 A — 새 칼럼을 더하고, 코드가 양쪽을 다 읽게 한다
#   배포 B — 옛 칼럼을 지운다 (이때는 아무도 그걸 안 본다)
#
set -euo pipefail

cd "$HOME/hapjugak-api"

# 배포가 겹쳐 돌지 않게 잠급니다.
exec 9>/tmp/hapjugak-deploy.lock
flock -n 9 || { echo "이미 배포 중"; exit 0; }

log(){ echo "$(date '+%F %T') $*" | tee -a "$HOME/deploy.log"; }

UPSTREAM_FILE=/etc/nginx/conf.d/hapjugak-upstream.conf
HEALTH_TRIES=40        # 3초 × 40 = 2분. 이 기계에서 평소 20여 초 걸립니다.
HEALTH_GAP=3

write_upstream(){
  printf 'upstream hapjugak_api {\n    server 127.0.0.1:%s;\n}\n' "$1" > "$UPSTREAM_FILE"
}

# ── 지금 어느 쪽이 서비스 중인가 ─────────────────────────────────────
# 컨테이너 목록이 아니라 nginx 가 보고 있는 포트를 봅니다. 트래픽이 실제로
# 어디로 가는지가 정답이고, 지난 배포가 중간에 끊겼으면 컨테이너는 둘 다 떠 있을
# 수도 있습니다.
CUR_PORT=$(grep -oP 'server\s+127\.0\.0\.1:\K[0-9]+' "$UPSTREAM_FILE" 2>/dev/null || true)
if [ "$CUR_PORT" = "8081" ]; then
  OLD=green; NEW=blue;  NEW_PORT=8080
else
  OLD=blue;  NEW=green; NEW_PORT=8081
  CUR_PORT=8080
fi

git fetch origin --quiet
TARGET=$(git rev-parse origin/master)
# 이미지 이름은 .env 에서 읽습니다. 적혀 있지 않으면 아래 값을 씁니다.
# 깃허브 계정 이름을 바꾸면 GHCR 네임스페이스도 따라 옮겨가므로,
# 그때는 코드를 고치지 않고 서버의 .env 한 줄만 바꾸면 됩니다.
#
# sed 를 쓰는 이유: grep 은 못 찾으면 1 을 돌려준다. 이 스크립트는 set -e 와
# pipefail 로 돌아서, 그 1 이 파이프라인 전체의 종료코드가 되고 스크립트가
# 그 자리에서 죽는다. 아무 메시지도 남기지 않는다 — log() 를 부르기 전이라
# 로그에도 화면에도 안 찍힌다. 실제로 그렇게 한 번 멎었고,
# 「배포를 눌렀는데 아무 일도 안 일어난다」로 보였다.
#
# sed -n 은 찾은 게 없어도 0 을 돌려준다. 「없을 수도 있는 것」을 찾을 때는
# 없음을 실패로 치지 않는 도구를 써야 한다.
IMAGE_NAME=""
if [ -f .env ]; then
  IMAGE_NAME=$(sed -n 's/^IMAGE_NAME=//p' .env | tr -d '[:space:]')
fi
IMAGE_NAME="${IMAGE_NAME:-ghcr.io/saae1101/hapjugak-api}"
IMAGE="$IMAGE_NAME:$TARGET"

log "배포 시작 ${TARGET:0:7} ($OLD:$CUR_PORT → $NEW:$NEW_PORT)"

# 이미지가 올라와 있는지 먼저 확인합니다.
# 커밋만 받고 옛 이미지로 교체하면 화면과 서버가 어긋납니다.
if ! docker manifest inspect "$IMAGE" > /dev/null 2>&1; then
  log "이미지가 아직 없습니다: $IMAGE"
  exit 1
fi

git reset --hard "$TARGET" --quiet

# ── 1. 새 쪽을 띄운다 ────────────────────────────────────────────────
# 지난 배포가 중간에 끊겨 찌꺼기가 남아 있을 수 있으니 먼저 치웁니다.
docker compose rm -sf "api-$NEW" > /dev/null 2>&1 || true
IMAGE_TAG="$TARGET" docker compose up -d "api-$NEW"

# ── 2. 떴는지 묻는다 ─────────────────────────────────────────────────
ok=0
for _ in $(seq 1 $HEALTH_TRIES); do
  if curl -fsS --max-time 2 "http://127.0.0.1:$NEW_PORT/api/actuator/health" 2>/dev/null \
       | grep -q '"status":"UP"'; then
    ok=1
    break
  fi
  sleep $HEALTH_GAP
done

# ── 3. 안 떴으면 새 것만 버린다 ──────────────────────────────────────
if [ "$ok" -ne 1 ]; then
  log "새 컨테이너가 $((HEALTH_TRIES * HEALTH_GAP))초 안에 뜨지 않았습니다."
  log "되돌립니다 — 사이트는 $OLD:$CUR_PORT 가 그대로 서비스합니다."
  {
    echo "──── 실패한 컨테이너의 마지막 로그 ────"
    docker compose logs --tail 60 "api-$NEW" 2>&1 || true
    echo "────────────────────────────────────"
  } >> "$HOME/deploy.log"
  docker compose rm -sf "api-$NEW" > /dev/null 2>&1 || true
  exit 1
fi

# ── 4. 트래픽을 넘긴다 ───────────────────────────────────────────────
# reload 는 이미 붙어 있는 연결을 끊지 않습니다. 옛 워커가 하던 요청을 마치고
# 스스로 물러나고, 새 요청만 새 설정으로 갑니다.
write_upstream "$NEW_PORT"
if ! sudo nginx -t > /dev/null 2>&1; then
  log "nginx 설정이 잘못됐습니다. 되돌립니다"
  write_upstream "$CUR_PORT"
  docker compose rm -sf "api-$NEW" > /dev/null 2>&1 || true
  exit 1
fi
sudo systemctl reload nginx

# ── 5. 화면을 복사한다 ───────────────────────────────────────────────
# 일부러 여기서 합니다. 먼저 복사하면 새 화면이 옛 서버와 짝지어 2분 가까이 돕니다.
# 여기서 하면 어긋나는 창이 1초가 됩니다.
cp frontend/index.html frontend/privacy.html frontend/oauth.html /var/www/hapjugak/
# 소셜 로그인 버튼의 공식 로고들. 빠뜨리면 버튼에서 아이콘만 깨진 채 뜨고,
# 화면 코드는 멀쩡하므로 원인을 찾는 데 한참 걸린다.
mkdir -p /var/www/hapjugak/img
cp -r frontend/img/. /var/www/hapjugak/img/

# ── 6. 옛 쪽을 내린다 ────────────────────────────────────────────────
docker compose rm -sf "api-$OLD" > /dev/null 2>&1 || true
docker image prune -f > /dev/null

log "배포 완료 ${TARGET:0:7} ($NEW:$NEW_PORT)"
