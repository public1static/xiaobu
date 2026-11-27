#!/usr/bin/env bash
# 本机 H5 联调服务，沿用项目专用的 Colima 数据卷。
set -eu
root=$(cd "$(dirname "$0")/.." && pwd -P)
state="$root/.local-run"
db=xiaobu-local-mysql
volume=xiaobu-local-mysql-data
url=http://127.0.0.1:18211
jar="$root/backend/target/xiaobu-backend-0.1.0-SNAPSHOT.jar"
uni="$root/frontend/node_modules/.bin/uni"
docker_local() { docker --context colima "$@"; }
fail() { echo "$*" >&2; exit 1; }
listener() { lsof -nP -t -iTCP:"$1" -sTCP:LISTEN 2>/dev/null | head -1; }

# 同时核对工作目录和启动命令，避免误停占用端口的其他项目。
owned() {
  local cwd cmd
  cwd=$(lsof -a -p "$1" -d cwd -Fn | sed -n "s/^n//p")
  cmd=$(ps -p "$1" -o command=)
  case "$2" in
    backend) [[ "$cwd" == "$root" && "$cmd" == *xiaobu-backend-0.1.0-SNAPSHOT.jar* && "$cmd" == *local-expense* ]] ;;
    frontend) [[ "$cwd" == "$root/frontend" && "$cmd" == *uni* ]] ;;
  esac
}

# 检查容器的持久配置，即使停止状态也能先核对监听地址和数据卷。
require_project_database() {
  local bindings storage
  bindings=$(docker_local container inspect -f '{{range index .HostConfig.PortBindings "3306/tcp"}}{{println .HostIp .HostPort}}{{end}}' "$db")
  storage=$(docker_local container inspect -f '{{range .Mounts}}{{if eq .Destination "/var/lib/mysql"}}{{.Name}}{{end}}{{end}}' "$db")
  [[ "$bindings" == "127.0.0.1 18325" && "$storage" == "$volume" ]] || fail "同名数据库容器的端口或数据卷与项目配置不符。"
}

database() {
  docker_local info >/dev/null 2>&1 || colima start
  if ! docker_local container inspect "$db" >/dev/null 2>&1; then
    if docker_local volume inspect "$volume" >/dev/null 2>&1; then
      fail "已有数据卷，但缺少对应容器，请先恢复原连接凭据。"
    fi
    MYSQL_PASSWORD=$(openssl rand -hex 24)
    MYSQL_ROOT_PASSWORD=$(openssl rand -hex 24)
    export MYSQL_PASSWORD MYSQL_ROOT_PASSWORD
    docker_local run -d --platform linux/amd64 --name "$db" -p 127.0.0.1:18325:3306 \
      -v "$volume:/var/lib/mysql" -e MYSQL_DATABASE=xiaobu_local -e MYSQL_USER=xiaobu \
      -e MYSQL_PASSWORD -e MYSQL_ROOT_PASSWORD mysql:5.7.44 >/dev/null
    unset MYSQL_ROOT_PASSWORD MYSQL_PASSWORD
  fi
  require_project_database
  docker_local start "$db" >/dev/null
  [[ "$(docker_local port "$db" 3306/tcp)" == 127.0.0.1:18325 ]] || fail "数据库端口映射与项目配置不符。"
  for ((i=0;i<90;i++)); do
    if docker_local exec "$db" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql -h127.0.0.1 -uxiaobu xiaobu_local -e "SELECT 1"' >/dev/null 2>&1; then return; fi
    sleep 1
  done
  fail "等待本地数据库就绪超时。"
}

start_app() {
  local name=$1 port=$2 pid i
  shift 2
  pid=$(listener "$port")
  if [[ -n "$pid" ]]; then
    owned "$pid" "$name" || fail "端口 $port 已被其他服务占用。"
    return
  fi
  nohup "$@" >>"$state/$name.log" 2>&1 </dev/null &
  pid=$!
  for ((i=0;i<90;i++)); do
    kill -0 "$pid" 2>/dev/null || fail "启动失败，请查看 $state/$name.log"
    [[ "$(listener "$port")" == "$pid" ]] && return
    sleep 1
  done
  kill "$pid" 2>/dev/null || true
  fail "等待 $name 超时，请查看 $state/$name.log"
}

# 在停止服务前准备启动产物，缺失的 JAR 使用后端项目配置打包。
prepare_start() {
  java_bin=$(resolve_java)
  if [[ ! -f "$jar" ]]; then
    command -v mvn >/dev/null || fail "缺少后端 JAR，请安装 Maven 后重试。"
    JAVA_HOME="${java_bin%/bin/java}" mvn -f "$root/backend/pom.xml" -DskipTests clean package
    [[ -f "$jar" ]] || fail "后端打包结束，但未找到 $jar。"
  fi
  [[ -x "$uni" ]] || npm ci --prefix "$root/frontend"
}

start() {
  database
  XIAOBU_DEV_DB_PASSWORD=$(docker_local exec "$db" printenv MYSQL_PASSWORD)
  export XIAOBU_DEV_DB_PASSWORD XIAOBU_DEV_DB_USER=xiaobu
  export XIAOBU_DEV_DB_URL="jdbc:mysql://127.0.0.1:18325/xiaobu_local?useSSL=false&serverTimezone=UTC"
  cd "$root"
  start_app backend 18125 "$java_bin" -jar "$jar" --spring.profiles.active=local-expense --server.port=18125
  unset XIAOBU_DEV_DB_PASSWORD
  cd frontend
  start_app frontend 18211 env CI=1 "$uni" --port 18211 --strictPort
  for ((i=0;i<30;i++)); do
    if [[ "$(curl --noproxy "*" -s --max-time 2 -o /dev/null -w "%{http_code}" "$url/api/v1/local-session")" == 401 ]]; then echo "$url/"; return; fi
    sleep 1
  done
  fail "前端代理尚未连通后端，请查看日志。"
}

resolve_java() {
  local candidate
  for candidate in "${XIAOBU_JAVA_HOME:-}/bin/java" "${JAVA_HOME:-}/bin/java" \
    "$(/usr/libexec/java_home -v 27 2>/dev/null || true)/bin/java" \
    "$HOME/Library/Application Support/ai.l0ng.openidea/tools/jdks"/temurin-27*/Contents/Home/bin/java; do
    if [[ -x "$candidate" ]] && "$candidate" -version 2>&1 | grep -q 'version "27[."]'; then echo "$candidate"; return; fi
  done
  fail "找不到 JDK 27，请设置 XIAOBU_JAVA_HOME。"
}

stop_app() {
  local pid i
  pid=$(listener "$2")
  [[ -n "$pid" ]] || return 0
  owned "$pid" "$1" || fail "端口 $2 由其他服务占用。"
  kill "$pid"
  for ((i=0;i<30;i++)); do
    kill -0 "$pid" 2>/dev/null || return 0
    sleep 1
  done
  fail "$1 尚未退出。"
}

stop() {
  stop_app frontend 18211
  stop_app backend 18125
  if docker_local container inspect "$db" >/dev/null 2>&1; then
    require_project_database
    docker_local stop "$db" >/dev/null
  fi
}

status() {
  local item port pid result
  for item in frontend backend; do
    port=18125
    [[ "$item" != frontend ]] || port=18211
    pid=$(listener "$port")
    result=stopped
    if [[ -n "$pid" ]]; then
      if owned "$pid" "$item"; then result="running (PID $pid)"; else result="port occupied"; fi
    fi
    echo "$item ($port) $result"
  done
  docker_local inspect -f "mysql {{.State.Status}}" "$db" 2>/dev/null || echo "mysql unavailable"
}

mkdir -p "$state"
case "${1:-status}" in
  start|restart|stop)
    mkdir "$state/lock" 2>/dev/null || fail "已有管理操作正在执行，请稍后重试。"
    trap 'rmdir "$state/lock"' EXIT
    if [[ "$1" != stop ]]; then prepare_start; fi
    case "$1" in start) start ;; restart) stop; start ;; stop) stop ;; esac ;;
  status) status ;;
  logs) tail -n 60 -F "$state/backend.log" "$state/frontend.log" ;;
  *) fail "用法：$0 {start|restart|stop|status|logs}" ;;
esac
