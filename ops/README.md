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

A changed port mapping only applies when the container is recreated — a
restart keeps the old one. The data lives in the named volume, so recreating
loses nothing — **provided the running container came from this compose
file.** If it was started some other way, `up` builds a second container on a
fresh, empty volume, and the API comes back pointed at a blank database.
Check first; this should print the backend directory:

```bash
docker inspect $(docker ps -q --filter publish=5432) \
  --format '{{ index .Config.Labels "com.docker.compose.project.working_dir" }}'
```

Then:

```bash
cd ~/InventoryApp/backend
docker compose up -d --force-recreate postgres
sudo netstat -antp | grep 5432     # want 127.0.0.1:5432, never 0.0.0.0:5432
```

### If it was ever exposed: change the password

The compose file's default credentials are `inventory` / `inventory`, and
automated scanners try exactly that kind of pair against every open 5432 they
find. A database that was published on `0.0.0.0` with the default password
should be treated as having been readable by anyone, and its password changed.

`POSTGRES_PASSWORD` is only read when the volume is first initialised, so the
change has to be made inside Postgres; editing the compose file does nothing
to an existing database:

```bash
NEW_PW="$(openssl rand -hex 24)"
docker compose exec postgres psql -U inventory -d inventory \
  -c "ALTER USER inventory WITH PASSWORD '$NEW_PW';"
echo "$NEW_PW"                     # copy it before the shell forgets it
```

Then put it in `DATABASE_URL` in `backend/.env` — hex only, so nothing in it
needs URL-escaping — and restart the API:

```
DATABASE_URL="postgresql://inventory:<NEW_PW>@localhost:5432/inventory"
```

```bash
sudo systemctl restart inventory-backend
curl -s http://127.0.0.1:4000/health
```

### Checking whether anyone got in

The bots that find open Postgres instances tend to leave traces: a dropped or
emptied schema with a single ransom table in its place, a new superuser role,
or a function created to run shell commands. Worth a look:

```bash
docker compose exec postgres psql -U inventory -d inventory -c '\dt'      # only the app's own tables
docker compose exec postgres psql -U inventory -d inventory -c '\du'      # only the inventory role
docker compose exec postgres psql -U inventory -d inventory -c '\df public.*'
docker compose exec postgres psql -U inventory -d inventory \
  -c 'SELECT count(*) FROM "Product";'                                    # the data is still there
docker compose logs postgres | grep -i 'authentication failed' | tail -40
```

A run of `password authentication failed` lines means it was being probed.
The log can only show the failures, though: Postgres doesn't record
successful connections unless `log_connections` is on, which it isn't in the
stock image — so a probe that guessed right leaves no line here at all. The
table, role and function listings above are the evidence that matters.
