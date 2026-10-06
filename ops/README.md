# Ops

Standalone infrastructure pieces that live outside the app itself.

| Path | What it is |
| --- | --- |
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

## Keeping Postgres off the internet

Docker publishes container ports by writing its own rules ahead of ufw, so a
database mapped as `5432:5432` is reachable from the internet even with ufw
denying everything. Bind it to loopback instead:

```yaml
ports:
  - "127.0.0.1:5432:5432"
```

`sudo netstat -antp | grep 5432` should show `127.0.0.1:5432`, never
`0.0.0.0:5432`.
