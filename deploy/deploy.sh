#!/usr/bin/env bash
# =====================================================================================
# 绪见 · 裸机一键部署脚本（Linux，无 Docker 场景）
#
# 用法（在仓库根目录执行）：
#   bash deploy/deploy.sh build     # 只构建前端 dist 与后端 jar
#   bash deploy/deploy.sh install   # 安装 systemd 服务并启动（需要 root）
#   bash deploy/deploy.sh all       # 构建 + 安装（需要 root）
#
# 约定安装目录：/opt/seeyou/{backend,frontend}
# =====================================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_DIR="${INSTALL_DIR:-/opt/seeyou}"
SERVICE_NAME="seeyou-backend"

log()  { echo -e "\033[32m[deploy]\033[0m $*"; }
warn() { echo -e "\033[33m[warn]\033[0m $*"; }
die()  { echo -e "\033[31m[error]\033[0m $*" >&2; exit 1; }

do_build() {
  log "构建后端 jar（需要 JDK 17 + Maven）"
  command -v mvn >/dev/null || die "未找到 mvn，请先安装 Maven"
  (cd "$REPO_ROOT/see_you" && mvn -q package -DskipTests)
  JAR=$(ls "$REPO_ROOT"/see_you/target/*.jar | grep -v '\.jar\.original$' | head -1)
  [ -n "$JAR" ] || die "未找到构建产物"
  log "后端产物：$JAR"

  log "构建前端 dist（需要 Node 18+）"
  command -v npm >/dev/null || die "未找到 npm"
  (cd "$REPO_ROOT/frontend" && npm ci && npm run build)
  log "前端产物：$REPO_ROOT/frontend/dist"
}

do_install() {
  [ "$(id -u)" -eq 0 ] || die "install 需要 root，请用 sudo 执行"

  JAR=$(ls "$REPO_ROOT"/see_you/target/*.jar | grep -v '\.jar\.original$' | head -1)
  [ -n "$JAR" ] || die "请先执行 build"
  [ -d "$REPO_ROOT/frontend/dist" ] || die "请先执行 build"

  log "部署到 $INSTALL_DIR"
  mkdir -p "$INSTALL_DIR/backend" "$INSTALL_DIR/frontend"
  cp "$JAR" "$INSTALL_DIR/backend/app.jar"
  rm -rf "$INSTALL_DIR/frontend/dist"
  cp -r "$REPO_ROOT/frontend/dist" "$INSTALL_DIR/frontend/dist"

  # 上传目录：后端 upload.front-path 指向前端 dist，图片上传后 Nginx 能直接吐出去
  # 注意：dist 会被重新构建覆盖，因此把已上传文件单独存一份并软链进 dist
  mkdir -p "$INSTALL_DIR/uploads"
  for d in banner cover avatar bg img diary user video audio; do
    mkdir -p "$INSTALL_DIR/uploads/$d"
    ln -sfn "$INSTALL_DIR/uploads/$d" "$INSTALL_DIR/frontend/dist/$d"
  done

  log "写入 systemd 服务 $SERVICE_NAME"
  cat > "/etc/systemd/system/${SERVICE_NAME}.service" <<EOF
[Unit]
Description=SeeYou Backend
After=network.target mysql.service

[Service]
Type=simple
WorkingDirectory=$INSTALL_DIR/backend
EnvironmentFile=-$INSTALL_DIR/backend/backend.env
ExecStart=/usr/bin/java -jar $INSTALL_DIR/backend/app.jar --spring.profiles.active=prod
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

  if [ ! -f "$INSTALL_DIR/backend/backend.env" ]; then
    warn "未找到 $INSTALL_DIR/backend/backend.env，已生成模板，请填入真实值后重启服务"
    cat > "$INSTALL_DIR/backend/backend.env" <<EOF
# 必填：改成强随机值，泄露等于任何人可冒充任意用户
JWT_SECRET=__SET_JWT_SECRET__
DB_PASSWORD=__SET_DB_PASSWORD__
DB_URL=jdbc:mysql://127.0.0.1:3306/see_you?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
DB_USERNAME=root
AI_API_KEY=
UPLOAD_PATH=$INSTALL_DIR/backend/upload
FRONT_PATH=$INSTALL_DIR/frontend/dist
EOF
  fi

  systemctl daemon-reload
  systemctl enable "$SERVICE_NAME"
  systemctl restart "$SERVICE_NAME"
  log "后端已启动，端口 8081"

  log "Nginx 配置请手动执行："
  echo "  sudo cp $REPO_ROOT/deploy/nginx-standalone.conf /etc/nginx/conf.d/seeyou.conf"
  echo "  # 把其中的 root 改成 $INSTALL_DIR/frontend/dist"
  echo "  sudo nginx -t && sudo systemctl reload nginx"
}

case "${1:-}" in
  build)   do_build ;;
  install) do_install ;;
  all)     do_build; do_install ;;
  *)       echo "用法: bash deploy/deploy.sh {build|install|all}" ; exit 1 ;;
esac
