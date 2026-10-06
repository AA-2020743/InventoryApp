# Ops

Standalone infrastructure pieces that live outside the app itself.

| Path | What it is |
| --- | --- |
| `redeploy.sh` | Pulls the current code, migrates, builds and restarts the API |
| `fetch-offsite-backup.sh` | Pulls a copy of the nightly backup to a machine other than the server |
| `nginx/inventory.conf` | The public entry point: TLS on 443, proxying to node on loopback |

## Serving the API on a domain

The Android app has no port logic: whatever goes in the **Server URL** field
is used verbatim as the base URL, so `https://example.com` means port 443 and
nothing else. The shape that makes that work:

```
phone → 443 (nginx, holds the certificate) → 127.0.0.1:4000 (node)
```

Node binds loopback only — set `HOST=127.0.0.1` in `backend/.env` — so the API
is unreachable except through nginx, and 4000 never needs to be open.

### Checklist

1. **DNS.** An `A` record for the domain (and `www`) pointing at the server's
   public IPv4, plus an `AAAA` record if it has a v6 address. Verify from
   somewhere other than the server, and force v4 when asking the server what
   its own address is — `curl -s ifconfig.me` answers over IPv6 where one
   exists, which is not the address an `A` record needs:

   ```bash
   dig +short A    alkheer-zadah.online
   dig +short AAAA alkheer-zadah.online
   curl -4 -s ifconfig.me    # on the server
   ```

   A registrar parking address (Namecheap's are `162.255.119.x`) means the
   record was never repointed, and no amount of server-side fixing will help.

   On Namecheap specifically, the bare domain usually ships as a **URL
   Redirect Record** on `@` rather than an A record. It cannot coexist with
   an A record on the same host, so it has to be deleted before `@` can
   point anywhere. A subdomain — `api` — sidesteps that entirely and is the
   easier name to give the app.

2. **Firewall.** Open 80 and 443. On a ufw host use ufw rather than raw
   iptables — a hand-inserted `iptables -I INPUT` rule is both easy to place
   in the wrong chain and gone at the next reboot unless
   `iptables-persistent` is installed:

   ```bash
   sudo ufw allow 80/tcp
   sudo ufw allow 443/tcp
   sudo ufw status verbose
   ```

3. **nginx.** Install `nginx/inventory.conf` as described in its header and
   remove the stock `default` site. The default server block answers any name
   nginx doesn't otherwise recognise, so with it enabled and no site of your
   own a perfectly good request returns nginx's own 404.

4. **Certificate.** `sudo certbot --nginx -d <domain> -d www.<domain>` once DNS
   resolves to this machine. The app rejects a self-signed certificate with
   the same "could not reach the server" message it shows for a dead host, so
   a cert that a browser merely warns about is a hard failure in the app.

### Narrowing a failed login

The app reports every `IOException` identically, so DNS failure, a closed
port, a timeout and a rejected certificate all read as *"Could not reach the
server."* Work outwards instead:

```bash
# node alive, on the server
curl -i http://127.0.0.1:4000/health

# nginx reaching node, on the server
curl -i http://127.0.0.1/health

# the whole path, from anywhere else
curl -i https://alkheer-zadah.online/health
```

`{"status":"ok"}` is the expected body at each step. The first one that
doesn't return it is the layer at fault: a 404 from nginx means the site
config isn't installed, a 502 means node is down, and a connection that never
opens from outside means DNS or the firewall.

## Deploying

```bash
cd ~/InventoryApp && ./ops/redeploy.sh
```

Deploys `main`; `BRANCH=some/branch ./ops/redeploy.sh` deploys something else.
`SERVICE` and `HEALTH_URL` override the unit name and the endpoint it checks.

It ends by polling `/health` until the API answers rather than reporting
`systemctl status`. systemd calls a unit active the moment the process is
spawned, which is before node has opened the port — so a service that starts
and then dies on a bad migration or a missing environment variable still
shows as "active (running)" for the couple of seconds a status check looks at
it. Polling the endpoint is the difference between knowing the deploy worked
and knowing it launched.

## Keeping Postgres off the internet

Docker publishes container ports by writing its own rules ahead of ufw, so a
database mapped as `5432:5432` is reachable from the internet even with ufw
denying everything. `backend/docker-compose.yml` binds it to loopback:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

A changed mapping only applies when the container is **recreated** — a
restart keeps the old one. If the database was ever published on `0.0.0.0`
with the default `inventory` / `inventory` credentials, treat it as having
been readable by anyone, and do the steps below **in this order**: recreating
the container discards its logs, so they're read first.

All commands run from `~/InventoryApp/backend`. (On older Docker, write
`docker-compose` where these say `docker compose`.)

### 1. Take a backup

Nothing below should touch the data, but this is the moment to have a copy.

```bash
docker compose exec -T postgres pg_dump -U inventory inventory > ~/pre-hardening-$(date +%F).sql
ls -lh ~/pre-hardening-*.sql          # should be well over a few KB
```

### 2. Look for signs anyone got in

The bots that find open Postgres instances tend to leave traces: a dropped or
emptied schema with a single ransom table in its place, a new superuser role,
or a function created to run shell commands.

```bash
docker compose exec postgres psql -U inventory -d inventory -c '\dt'      # only the app's own tables
docker compose exec postgres psql -U inventory -d inventory -c '\du'      # only the inventory role
docker compose exec postgres psql -U inventory -d inventory -c '\df public.*'
docker compose exec postgres psql -U inventory -d inventory \
  -c 'SELECT count(*) FROM "Product";'                                    # the data is still there
docker compose logs postgres | grep -i 'authentication failed' | tail -40
docker compose logs postgres | grep -i 'authentication failed' | wc -l
```

A run of `password authentication failed` lines means it was being probed.
The log can only show the failures, though: Postgres doesn't record
successful connections unless `log_connections` is on, which it isn't in the
stock image — so a probe that guessed right leaves no line here at all. The
table, role and function listings are the evidence that matters.

### 3. Recreate it on loopback

Recreating keeps the data, which lives in the named volume — **provided the
running container came from this compose file.** If it was started some other
way, `up` builds a second container on a fresh, empty volume and the API comes
back pointed at a blank database. This should print the backend directory:

```bash
docker inspect $(docker ps -q --filter publish=5432) \
  --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}'
```

Then:

```bash
docker compose up -d --force-recreate postgres
sudo netstat -antp | grep 5432     # want 127.0.0.1:5432, never 0.0.0.0:5432
```

### 4. Change the password

`POSTGRES_PASSWORD` is only read when the volume is first initialised, so the
change is made inside Postgres; editing the compose file does nothing to an
existing database. The backend's `DATABASE_URL` is the only other place that
holds it.

```bash
NEW_PW="$(openssl rand -hex 24)"
docker compose exec postgres psql -U inventory -d inventory \
  -c "ALTER USER inventory WITH PASSWORD '$NEW_PW';"
cp .env .env.before-password-change
sed -i "s#^DATABASE_URL=.*#DATABASE_URL=\"postgresql://inventory:$NEW_PW@127.0.0.1:5432/inventory\"#" .env
grep ^DATABASE_URL .env
sudo systemctl restart inventory-backend
sleep 3 && curl -s http://127.0.0.1:4000/health   # want {"status":"ok"}
```

The host is `127.0.0.1`, not `localhost`, on purpose. Postgres is now
published on IPv4 loopback only, and on a host where `localhost` resolves to
`::1` first, a `localhost` URL points at an address nothing listens on any
more - working only if the client happens to fall back to IPv4.

The password is hex, so nothing in it needs URL-escaping. If the health check
fails, `.env.before-password-change` has the old line — but the old password
no longer works, so the fix is to correct the new line, not restore the old.
Once it's healthy, delete the copy: `rm .env.before-password-change`.
