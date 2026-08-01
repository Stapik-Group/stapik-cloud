# Stapik Cloud

A self-hosted backend for the Stapik ecosystem of apps — document storage with versioning and conflict resolution, binary file storage, and a JWT-secured admin panel. Written in Java (Spring Boot) with a Next.js admin interface.

## Features

- **Document slots** — per-extension key-value document storage (`JSON`, `TEXT`, `BINARY`, `BINARY_COLLECTION`), with configurable versioning and per-slot size limits
- **Conflict resolution** — last-write-wins, optionally with a shadow copy preserving the discarded write-in version history
- **Version history** — every write is recorded; restore any previous version as the current document
- **Binary assets** — file upload/download/list per document slot, backed by local filesystem storage
- **API key authentication** — per-extension API keys (Argon2id-hashed, prefix lookup, optional IP allowlist, read-only/read-write scopes) for app-facing endpoints
- **Admin panel** — Next.js interface (JWT-secured) to manage extensions, document slots, API keys, browse document content and version history, and inspect the audit log
- **Audit log** — tracks administrative actions (extension/slot/key lifecycle) with a paginated, filterable view
- **Multilingual admin UI** — Polish, English and German interface with instant switching
- **OpenAPI-first** — two specs, one for app-facing endpoints (`x-api-key`), one for the admin panel (JWT), both used to generate server and client types

## Architecture

- **`backend/`** — Spring Boot 4, Java 21, Maven, PostgreSQL 16, Flyway migrations, package-by-feature, delegate pattern generated from OpenAPI specs
- **`admin/`** — Next.js 16 (App Router, React 19, Tailwind 4), acting as a BFF: the browser never talks to the backend directly, the admin's own API routes proxy requests and hold the JWT in an httpOnly cookie
- **`deploy/`** — Docker Compose stack (backend + PostgreSQL + admin panel) and a one-shot provisioning script for Proxmox VE (LXC + Docker)

## Dependencies

- Java 21, Maven
- Node.js 22, npm
- Docker & Docker Compose (for running the full stack)
- PostgreSQL 16 (provided via Docker Compose, or bring your own)

## Building

Backend:
```bash
cd backend
mvn clean package
```

Admin panel:
```bash
cd admin
npm ci
npm run generate:api-types   # regenerate TypeScript types from admin-api.yaml
npm run build
```

## Deployment

The `deploy/` directory contains everything needed to run the full stack (backend + PostgreSQL + admin panel) in a Docker Compose stack, including a script that provisions a Proxmox LXC container from scratch.

Quick start on a Proxmox host:
```bash
bash <(curl -fsSL https://raw.githubusercontent.com/Stapik-Group/stapik-cloud/master/deploy/proxmox/deploy.sh)
```

Running the same command again updates an existing installation (pulls the latest code, rebuilds, restarts) without touching existing secrets or data.

See [`deploy/README.md`](deploy/README.md) for the full walkthrough, configuration, and security notes.

## API

Two OpenAPI specifications live under `backend/src/main/resources/openapi/`:

- **`app-api.yaml`** — endpoints used by client apps (document slots, documents, versions, assets), authenticated with an `x-api-key` header
- **`admin-api.yaml`** — endpoints used by the admin panel (extensions, slots, keys, audit log), authenticated with a JWT bearer token

Both share a common error schema (`common/error.yaml`, RFC 7807 structured errors).

## Configuration

The backend is configured entirely through environment variables — see [`deploy/.env.example`](deploy/.env.example) for the full list (database credentials, JWT secret, admin bootstrap account, admin panel CORS origin, audit log retention).

## TODO

- [x] Document slots with versioning and conflict resolution
- [x] Binary asset storage
- [x] Admin panel with JWT auth
- [x] Audit log
- [x] Multilingual admin UI (PL/EN/DE)
- [x] Docker Compose deployment + Proxmox provisioning script
- [ ] Record the acting admin user (`actor`) in audit log entries
- [ ] Reverse proxy + TLS termination in front of the admin panel
- [ ] Translate remaining raw enum values in the admin UI (content type, conflict strategy, key scope)
- [ ] Extension export feature
- [ ] Rate limiting per key
- [ ] Saving document webhook
- [ ] Simple statistics and diagrams for key and storage usage.
- [ ] Auto backups to ZIP files
- [ ] End-to-end encryption 
- [ ] Push alerts for dangerous behavior (login failed, removing documents, restoring documents)