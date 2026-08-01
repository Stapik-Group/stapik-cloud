#!/usr/bin/env bash
# Run ON THE PROXMOX HOST as root: bash deploy.sh
#
# First run:  creates the LXC, installs Docker, clones the repo, generates
#             secrets and starts the whole stack.
# Later runs: if a container with the same CTID already exists, this script
#             switches to UPDATE mode — it pulls the latest code, rebuilds
#             the images and restarts the stack, WITHOUT touching the
#             existing .env (so passwords/JWT secret stay stable).
set -euo pipefail

# ---- Configuration - adjust before running ----
CTID=200
HOSTNAME=stapik-cloud
STORAGE=local-lvm
DISK_SIZE=16
MEMORY=2048
CORES=2
BRIDGE=vmbr0
IP_CONFIG="dhcp"          # or e.g. "192.168.1.50/24,gw=192.168.1.1"
TEMPLATE_STORAGE=local
TEMPLATE=debian-12-standard_12.7-1_amd64.tar.zst
REPO_URL="https://github.com/Stapik-Group/stapik-cloud.git"
APP_DIR="/opt/stapik-cloud"

# ---- Detect whether this is a fresh install or an update ----
if pct status "${CTID}" > /dev/null 2>&1; then
  echo "Container ${CTID} already exists — running in UPDATE mode."
  UPDATE_MODE=true
  pct start "${CTID}" > /dev/null 2>&1 || true
else
  echo "Container ${CTID} not found — running in FRESH INSTALL mode."
  UPDATE_MODE=false
fi

if [ "${UPDATE_MODE}" = false ]; then
  # ---- Download template if missing ----
  if ! pveam list "${TEMPLATE_STORAGE}" | grep -q "${TEMPLATE}"; then
    pveam update
    pveam download "${TEMPLATE_STORAGE}" "${TEMPLATE}"
  fi

  # ---- Create and start the container ----
  # nesting=1,keyctl=1 are required for Docker to work inside an unprivileged LXC
  pct create "${CTID}" "${TEMPLATE_STORAGE}:vztmpl/${TEMPLATE}" \
    --hostname "${HOSTNAME}" \
    --cores "${CORES}" \
    --memory "${MEMORY}" \
    --swap 512 \
    --rootfs "${STORAGE}:${DISK_SIZE}" \
    --net0 "name=eth0,bridge=${BRIDGE},ip=${IP_CONFIG}" \
    --unprivileged 1 \
    --features nesting=1,keyctl=1 \
    --onboot 1

  # Relax AppArmor for this container - required for Docker to work reliably
  # inside unprivileged LXC with current runc/containerd versions (they write
  # to net.ipv4.ip_unprivileged_port_start on every container start, which
  # the default AppArmor profile blocks). See:
  # https://github.com/opencontainers/runc/issues/4972
  echo "lxc.apparmor.profile: unconfined" >> "/etc/pve/lxc/${CTID}.conf"

  pct start "${CTID}"
fi

echo "Waiting for network inside the container..."
for i in $(seq 1 30); do
  if pct exec "${CTID}" -- getent hosts github.com > /dev/null 2>&1; then
    break
  fi
  sleep 2
done

if [ "${UPDATE_MODE}" = false ]; then
  # ---- Install Docker inside the container ----
  pct exec "${CTID}" -- bash -c '
    set -euo pipefail
    apt-get update
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable docker
    systemctl start docker
  '
fi

# ---- Clone or update the repository ----
# This is a deployment target, not a dev clone — always force it to match
# the remote branch exactly, even after a force-push / rewritten history.
if pct exec "${CTID}" -- test -d "${APP_DIR}/.git"; then
  echo "Repository already present — resetting to match origin."
  pct exec "${CTID}" -- bash -c "cd ${APP_DIR} && git fetch origin && git reset --hard \"origin/\$(git symbolic-ref --short HEAD)\""
else
  pct exec "${CTID}" -- git clone "${REPO_URL}" "${APP_DIR}"
fi

# ---- Read the container's IP ----
CONTAINER_IP="$(pct exec "${CTID}" -- hostname -I | awk '{print $1}')"

# ---- Generate secrets only if .env doesn't already exist ----
if pct exec "${CTID}" -- test -f "${APP_DIR}/deploy/.env"; then
  echo "Existing .env found — keeping current secrets untouched."
  SECRETS_KEPT=true
else
  GENERATED_POSTGRES_PASSWORD="$(openssl rand -base64 24)"
  GENERATED_JWT_SECRET="$(openssl rand -base64 48)"
  GENERATED_ADMIN_PASSWORD="$(openssl rand -base64 18)"

  ENV_FILE="$(mktemp)"
  cat > "${ENV_FILE}" <<EOF
POSTGRES_DB=stapik_cloud
POSTGRES_USER=stapik
POSTGRES_PASSWORD=${GENERATED_POSTGRES_PASSWORD}
JWT_SECRET=${GENERATED_JWT_SECRET}
JWT_EXPIRATION_MINUTES=60
ADMIN_ALLOWED_ORIGINS=http://${CONTAINER_IP}:3000
ADMIN_BOOTSTRAP_USERNAME=admin
ADMIN_BOOTSTRAP_PASSWORD=${GENERATED_ADMIN_PASSWORD}
AUDIT_RETENTION_DAYS=365
EOF

  pct push "${CTID}" "${ENV_FILE}" "${APP_DIR}/deploy/.env"
  rm -f "${ENV_FILE}"
  SECRETS_KEPT=false
fi

# ---- Build and (re)start the stack ----
pct exec "${CTID}" -- bash -c "cd ${APP_DIR}/deploy && docker compose build && docker compose up -d"

echo ""
echo "=================================================================="
if [ "${UPDATE_MODE}" = true ]; then
  echo "Update complete. Stapik Cloud is running in LXC ${CTID} (${HOSTNAME})."
else
  echo "Done. Stapik Cloud is running in LXC ${CTID} (${HOSTNAME})."
fi
echo "Container IP: ${CONTAINER_IP}"
echo "Backend:      http://${CONTAINER_IP}:8080"
echo "Admin panel:  http://${CONTAINER_IP}:3000"
echo ""

if [ "${SECRETS_KEPT}" = false ]; then
  echo "Generated credentials (SAVE THESE NOW, shown only once):"
  echo "  POSTGRES_PASSWORD:       ${GENERATED_POSTGRES_PASSWORD}"
  echo "  JWT_SECRET:              ${GENERATED_JWT_SECRET}"
  echo "  ADMIN_BOOTSTRAP_USERNAME: admin"
  echo "  ADMIN_BOOTSTRAP_PASSWORD: ${GENERATED_ADMIN_PASSWORD}"
  echo ""
  echo "These values are also stored in ${APP_DIR}/deploy/.env inside the container."
  echo "Edit ADMIN_ALLOWED_ORIGINS there once you know your admin panel's domain."
else
  echo "Existing secrets were kept — see ${APP_DIR}/deploy/.env inside the"
  echo "container if you need to look them up again."
fi
echo "=================================================================="