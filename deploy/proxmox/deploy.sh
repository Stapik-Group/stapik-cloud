#!/usr/bin/env bash
# Run ON THE PROXMOX HOST as root.
#
#   bash deploy.sh                    install (first run) or update (container exists)
#   bash deploy.sh backup             take a backup now (the stack is stopped for a moment)
#   bash deploy.sh list               list the backups of this container
#   bash deploy.sh restore [VOLID]    replace the container with a backup (default: the newest one)
#
# First run:  creates the LXC, installs Docker, clones the repo, generates
#             secrets and starts the whole stack.
#
# Later runs: UPDATE mode. Nothing is changed before a verified backup exists:
#               1. checks free space and storage, fetches the new code (read-only)
#               2. dumps the database (pg_dump) and stops the stack (consistent state)
#               3. backs up the whole LXC with vzdump and verifies the archive
#               4. resets the code, rebuilds the images, starts the stack
#               5. waits for the health checks of backend and admin panel
#             If something fails, the script puts things back by itself:
#               - before the new version started: previous code and stack are restored
#               - after the new version started (database migrations may have run):
#                 the whole LXC is restored from the backup from step 3
#             The existing .env is never touched, so passwords and the JWT secret stay stable.
#
# Optional environment variables:
#   BACKUP_STORAGE   Proxmox storage that accepts backups            (default: local)
#   BACKUP_MODE      vzdump mode: snapshot | suspend | stop          (default: snapshot)
#   BACKUPS_TO_KEEP  how many backups / database dumps to keep       (default: 3)
#   AUTO_ROLLBACK    restore the backup when the new version fails   (default: true)
#   HEALTH_TIMEOUT   seconds to wait for the stack after an update   (default: 240)
#   MIN_FREE_GB      required free disk space inside the container   (default: 3)
#   FORCE_REBUILD    rebuild even if the code is already up to date  (default: false)
#   SKIP_BACKUP      update without a backup - NOT recommended       (default: false)
#   ASSUME_YES       do not ask for confirmation on restore          (default: false)
set -Eeuo pipefail

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

# ---- Backup and safety settings (can be overridden from the environment) ----
BACKUP_STORAGE="${BACKUP_STORAGE:-local}"
BACKUP_MODE="${BACKUP_MODE:-snapshot}"
BACKUPS_TO_KEEP="${BACKUPS_TO_KEEP:-3}"
AUTO_ROLLBACK="${AUTO_ROLLBACK:-true}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-240}"
MIN_FREE_GB="${MIN_FREE_GB:-3}"
FORCE_REBUILD="${FORCE_REBUILD:-false}"
SKIP_BACKUP="${SKIP_BACKUP:-false}"
ASSUME_YES="${ASSUME_YES:-false}"
LOG_DIR="${LOG_DIR:-/var/log/stapik-cloud}"

ACTION="${1:-auto}"
RESTORE_VOLID="${2:-}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"

# Progress of the run, used to decide how to recover after a failure:
#   init -> quiesced (stack stopped) -> code_changed -> started (new version running) -> done
PHASE="init"
BACKUP_VOLID=""
OLD_REV=""
UPDATE_MODE=false
HAS_DEPLOYMENT=false

# ---- Helpers ----
log()  { echo "[$(date +%H:%M:%S)] $*"; }
warn() { echo "[$(date +%H:%M:%S)] WARNING: $*" >&2; }
die()  { echo "[$(date +%H:%M:%S)] ERROR: $*" >&2; exit 1; }

# Runs a docker compose command inside the container (arguments must not contain spaces).
compose() { pct exec "${CTID}" -- bash -c "cd ${APP_DIR}/deploy && docker compose $*"; }

[ "$(id -u)" -eq 0 ] || die "Run this script as root on the Proxmox host."
for required_command in pct pvesm vzdump flock; do
  command -v "${required_command}" > /dev/null || die "'${required_command}' not found - this script must run on a Proxmox VE host."
done

case "${ACTION}" in
  auto | backup | restore | list) ;;
  *) die "Unknown action '${ACTION}'. Usage: bash deploy.sh [backup | list | restore [VOLID]]" ;;
esac

# Everything is also written to a log file on the host. It survives a restore of the container.
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/deploy-${TIMESTAMP}.log"
exec > >(tee -a "${LOG_FILE}") 2>&1
log "Log file: ${LOG_FILE}"

# Only one run at a time - two parallel updates would corrupt each other.
exec 9> /var/lock/stapik-cloud-deploy.lock
flock -n 9 || die "Another deploy.sh run is already in progress."

# ---- Backups ----
require_backup_storage() {
  pvesm status --content backup | awk 'NR > 1 {print $1}' | grep -qx "${BACKUP_STORAGE}" \
    || die "Storage '${BACKUP_STORAGE}' does not accept backups. Set BACKUP_STORAGE to a storage with 'VZDump backup file' content (Datacenter > Storage)."
}

# Volume ids of this container's backups, oldest first (names contain a timestamp).
list_backups() {
  pvesm list "${BACKUP_STORAGE}" --content backup --vmid "${CTID}" | awk 'NR > 1 {print $1}' | sort
}

verify_backup() {
  local backup_path
  backup_path="$(pvesm path "$1")"
  [ -s "${backup_path}" ] || die "Backup file is missing or empty: ${backup_path}"
  if [[ "${backup_path}" == *.zst ]] && command -v zstd > /dev/null; then
    zstd -tq "${backup_path}" || die "Backup archive is corrupted: ${backup_path}"
  fi
}

create_backup() {
  require_backup_storage
  local newest_before
  newest_before="$(list_backups | tail -n 1)"

  log "Backing up LXC ${CTID} to '${BACKUP_STORAGE}' (mode: ${BACKUP_MODE})..."
  vzdump "${CTID}" --mode "${BACKUP_MODE}" --compress zstd --storage "${BACKUP_STORAGE}"

  BACKUP_VOLID="$(list_backups | tail -n 1)"
  if [ -z "${BACKUP_VOLID}" ] || [ "${BACKUP_VOLID}" = "${newest_before}" ]; then
    die "vzdump finished, but no new backup was found on '${BACKUP_STORAGE}'."
  fi
  verify_backup "${BACKUP_VOLID}"
  log "Backup verified: ${BACKUP_VOLID}"
}

prune_backups() {
  pvesm prune-backups "${BACKUP_STORAGE}" --vmid "${CTID}" --keep-last "${BACKUPS_TO_KEEP}" \
    || warn "Could not prune old backups - remove them manually if the storage fills up."
}

# Logical database dump kept inside the container (also part of the vzdump archive).
dump_database() {
  if ! compose ps --status running --services | grep -qx postgres; then
    warn "PostgreSQL is not running - skipping the logical dump (the LXC backup still contains the data)."
    return 0
  fi

  log "Dumping the database..."
  pct exec "${CTID}" -- bash -c '
    set -euo pipefail
    app_dir="$1"; timestamp="$2"; keep="$3"
    backups_dir="${app_dir}/backups"
    dump_file="${backups_dir}/pre-update-${timestamp}.sql.gz"
    mkdir -p "${backups_dir}"
    cd "${app_dir}/deploy"
    docker compose exec -T postgres sh -c "pg_dump -U \"\$POSTGRES_USER\" \"\$POSTGRES_DB\"" | gzip > "${dump_file}"
    test -s "${dump_file}"
    gzip -t "${dump_file}"
    ls -1t "${backups_dir}"/pre-update-*.sql.gz | tail -n +"$((keep + 1))" | xargs -r rm -f
    echo "Database dump: ${dump_file} ($(du -h "${dump_file}" | cut -f1))"
  ' _ "${APP_DIR}" "${TIMESTAMP}" "${BACKUPS_TO_KEEP}"
}

quiesce_stack() {
  log "Stopping the stack for a consistent backup..."
  PHASE="quiesced"
  compose stop
}

# ---- Container and stack helpers ----
ensure_apparmor_profile() {
  # Relax AppArmor for this container - required for Docker to work reliably
  # inside unprivileged LXC with current runc/containerd versions (they write
  # to net.ipv4.ip_unprivileged_port_start on every container start, which
  # the default AppArmor profile blocks). See:
  # https://github.com/opencontainers/runc/issues/4972
  grep -q '^lxc.apparmor.profile' "/etc/pve/lxc/${CTID}.conf" \
    || echo "lxc.apparmor.profile: unconfined" >> "/etc/pve/lxc/${CTID}.conf"
}

wait_for_container_network() {
  echo "Waiting for network inside the container..."
  for _ in $(seq 1 30); do
    if pct exec "${CTID}" -- getent hosts github.com > /dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  warn "No network inside the container after 60 seconds."
}

ensure_docker_installed() {
  if pct exec "${CTID}" -- command -v docker > /dev/null 2>&1; then
    return 0
  fi

  log "Installing Docker inside the container..."
  pct exec "${CTID}" -- bash -c '
    set -euo pipefail
    apt-get update
    apt-get install -y ca-certificates curl gnupg git
    install -m 0755 -d /etc/apt/keyrings
    curl -fsSL https://download.docker.com/linux/debian/gpg | gpg --dearmor --yes -o /etc/apt/keyrings/docker.gpg
    chmod a+r /etc/apt/keyrings/docker.gpg
    echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
    apt-get update
    apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
    systemctl enable docker
    systemctl start docker
  '
}

# Both the backend and the admin panel must answer; the backend needs time for database migrations.
wait_for_healthy() {
  log "Waiting up to ${HEALTH_TIMEOUT}s for the backend and the admin panel..."
  local deadline=$((SECONDS + HEALTH_TIMEOUT))
  while [ "${SECONDS}" -lt "${deadline}" ]; do
    if pct exec "${CTID}" -- bash -c \
      'curl -fsS -o /dev/null http://127.0.0.1:8080/api/v1/health && curl -fsS -o /dev/null http://127.0.0.1:3000/login' \
      > /dev/null 2>&1; then
      log "Backend and admin panel are healthy."
      return 0
    fi
    sleep 5
  done
  return 1
}

collect_diagnostics() {
  log "Container state and recent logs of the failed stack (also saved in ${LOG_FILE}):"
  compose ps -a || true
  compose logs --tail=100 --no-color || true
}

preflight_checks() {
  local memory_mb free_gb
  memory_mb="$(pct config "${CTID}" | awk '/^memory:/ {print $2}')"
  if [ -n "${memory_mb}" ] && [ "${memory_mb}" -lt 3072 ]; then
    warn "The container has ${memory_mb} MB of RAM. Building the Java and Next.js images is memory hungry; the stack is stopped during the update to leave room, but 4096 MB is safer."
  fi

  free_gb="$(pct exec "${CTID}" -- df --output=avail -BG / | tail -n 1 | tr -dc '0-9')"
  if [ -n "${free_gb}" ] && [ "${free_gb}" -lt "${MIN_FREE_GB}" ]; then
    die "Only ${free_gb} GB free inside the container (need ${MIN_FREE_GB} GB). Free space first (e.g. 'docker system prune') or lower MIN_FREE_GB."
  fi
}

# ---- Rollback ----
rollback_to_backup() {
  local volid="$1"
  log "ROLLBACK: restoring LXC ${CTID} from ${volid}..."
  pct stop "${CTID}" > /dev/null 2>&1 || true
  pct restore "${CTID}" "${volid}" --force 1 --storage "${STORAGE}"
  ensure_apparmor_profile
  pct start "${CTID}"
  wait_for_container_network
  # The backup was taken with the stack stopped on purpose, so it has to be started explicitly.
  compose up -d
  wait_for_healthy || warn "The restored stack is not healthy yet - check: pct exec ${CTID} -- bash -c 'cd ${APP_DIR}/deploy && docker compose ps'"
  log "Rollback finished. The container is back in the state from ${volid}."
}

recover_after_failure() {
  case "${PHASE}" in
    quiesced)
      log "Restarting the stack that was stopped for the backup..."
      compose up -d || warn "Could not restart the stack: pct exec ${CTID} -- bash -c 'cd ${APP_DIR}/deploy && docker compose up -d'"
      ;;
    code_changed)
      log "The new version never started - putting back the previous code (${OLD_REV}) and stack..."
      { pct exec "${CTID}" -- git -C "${APP_DIR}" reset --hard "${OLD_REV}" && compose up -d; } \
        || warn "Could not restore the previous stack automatically. Restore the backup: bash deploy.sh restore ${BACKUP_VOLID:-<volid>}"
      ;;
    started)
      collect_diagnostics
      if [ "${AUTO_ROLLBACK}" = true ] && [ -n "${BACKUP_VOLID}" ]; then
        rollback_to_backup "${BACKUP_VOLID}" \
          || warn "Automatic rollback failed. The backup is untouched - retry with: bash deploy.sh restore ${BACKUP_VOLID}"
      else
        warn "No automatic rollback. Restore the previous state with: bash deploy.sh restore ${BACKUP_VOLID:-<volid>}"
      fi
      ;;
  esac
}

on_exit() {
  local exit_code=$?
  trap - EXIT
  set +e
  if [ "${exit_code}" -ne 0 ]; then
    echo ""
    warn "Deploy failed (exit code ${exit_code}). Full log: ${LOG_FILE}"
    recover_after_failure
  fi
  exit "${exit_code}"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# ---- Actions: list / backup / restore ----
require_existing_container() {
  pct status "${CTID}" > /dev/null 2>&1 || die "Container ${CTID} does not exist."
  pct start "${CTID}" > /dev/null 2>&1 || true
}

if [ "${ACTION}" = "list" ]; then
  require_backup_storage
  echo "Backups of LXC ${CTID} on '${BACKUP_STORAGE}' (oldest first):"
  list_backups || true
  exit 0
fi

if [ "${ACTION}" = "backup" ]; then
  require_existing_container
  dump_database
  quiesce_stack
  create_backup
  compose up -d
  PHASE="done"
  prune_backups
  log "Backup complete: ${BACKUP_VOLID}"
  exit 0
fi

if [ "${ACTION}" = "restore" ]; then
  require_backup_storage
  volid="${RESTORE_VOLID:-$(list_backups | tail -n 1)}"
  [ -n "${volid}" ] || die "No backups of LXC ${CTID} found on '${BACKUP_STORAGE}'."
  verify_backup "${volid}"
  if [ "${ASSUME_YES}" != true ]; then
    read -r -p "This REPLACES LXC ${CTID} with ${volid}. Everything changed since then is lost. Type 'yes' to continue: " answer
    [ "${answer}" = "yes" ] || die "Aborted."
  fi
  rollback_to_backup "${volid}"
  exit 0
fi

# ---- Detect whether this is a fresh install or an update ----
if pct status "${CTID}" > /dev/null 2>&1; then
  echo "Container ${CTID} already exists — running in UPDATE mode."
  UPDATE_MODE=true
  pct start "${CTID}" > /dev/null 2>&1 || true
else
  echo "Container ${CTID} not found — running in FRESH INSTALL mode."
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

  ensure_apparmor_profile
  pct start "${CTID}"
fi

wait_for_container_network
ensure_docker_installed

if pct exec "${CTID}" -- test -d "${APP_DIR}/.git"; then
  HAS_DEPLOYMENT=true
fi

# ---- Update mode: check the situation and fetch the new code without changing anything ----
if [ "${UPDATE_MODE}" = true ] && [ "${HAS_DEPLOYMENT}" = true ]; then
  preflight_checks

  OLD_REV="$(pct exec "${CTID}" -- git -C "${APP_DIR}" rev-parse HEAD)"
  # This is a deployment target, not a dev clone — it is forced to match the remote branch exactly.
  pct exec "${CTID}" -- git -C "${APP_DIR}" fetch origin
  TARGET_REF="$(pct exec "${CTID}" -- bash -c "cd ${APP_DIR} && echo \"origin/\$(git symbolic-ref --short HEAD)\"")"
  NEW_REV="$(pct exec "${CTID}" -- git -C "${APP_DIR}" rev-parse "${TARGET_REF}")"

  if [ "${OLD_REV}" = "${NEW_REV}" ] && [ "${FORCE_REBUILD}" != true ] && wait_for_healthy; then
    log "Already up to date (${OLD_REV:0:10}) and healthy. Use FORCE_REBUILD=true to rebuild anyway."
    exit 0
  fi

  log "Updating ${OLD_REV:0:10} -> ${NEW_REV:0:10}"
  pct exec "${CTID}" -- git -C "${APP_DIR}" log --oneline "${OLD_REV}..${NEW_REV}" | head -n 20 || true
  LOCAL_CHANGES="$(pct exec "${CTID}" -- git -C "${APP_DIR}" status --short)"
  if [ -n "${LOCAL_CHANGES}" ]; then
    warn "Local changes in ${APP_DIR} will be overwritten by the update:"
    echo "${LOCAL_CHANGES}"
  fi

  if [ "${SKIP_BACKUP}" = true ]; then
    warn "SKIP_BACKUP=true - updating WITHOUT a backup. A failed update cannot be rolled back."
    sleep 5
  else
    dump_database
    quiesce_stack
    create_backup
  fi

  PHASE="code_changed"
  pct exec "${CTID}" -- git -C "${APP_DIR}" reset --hard "${TARGET_REF}"
elif [ "${HAS_DEPLOYMENT}" = true ]; then
  pct exec "${CTID}" -- git -C "${APP_DIR}" fetch origin
else
  # ---- Clone the repository ----
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
  pct exec "${CTID}" -- chmod 600 "${APP_DIR}/deploy/.env"
  rm -f "${ENV_FILE}"
  SECRETS_KEPT=false
fi

# ---- Build and (re)start the stack ----
# The images are built first: a failing build never touches the running (or stopped) previous version.
log "Building the images..."
compose build

# From here on the new version may run database migrations, so a failure means restoring the backup.
PHASE="started"
log "Starting the stack..."
compose up -d

if [ "${UPDATE_MODE}" = true ] && [ "${HAS_DEPLOYMENT}" = true ] || [ "${UPDATE_MODE}" = false ]; then
  wait_for_healthy || die "The stack did not become healthy within ${HEALTH_TIMEOUT}s."
fi

PHASE="done"
pct exec "${CTID}" -- docker image prune -f > /dev/null 2>&1 || true
if [ "${UPDATE_MODE}" = true ] && [ -n "${BACKUP_VOLID}" ]; then
  prune_backups
fi

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
if [ -n "${BACKUP_VOLID}" ]; then
  echo ""
  echo "Pre-update backup: ${BACKUP_VOLID}"
  echo "Restore it with:   bash deploy.sh restore ${BACKUP_VOLID}"
fi
echo "Log file:     ${LOG_FILE}"
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