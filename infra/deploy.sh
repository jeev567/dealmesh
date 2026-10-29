#!/usr/bin/env bash
set -euo pipefail

# Run from any directory on your Mac. Requires aws, python3, npm, and Java 21.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGION="${AWS_REGION:-us-west-2}"
PROFILE="${AWS_PROFILE:-dealseal-admin}"
INSTANCE_ID="${DEALMESH_INSTANCE_ID:-i-0d4c75cb981ecac57}"
ACCOUNT_ID="$(aws sts get-caller-identity --profile "$PROFILE" --query Account --output text)"
BUCKET="dealmesh-deploy-${ACCOUNT_ID}-${REGION}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
PREFIX="releases/${STAMP}"

echo "Deploying ONLY DealMesh instance: $INSTANCE_ID"
read -r -p "Continue? [y/N] " CONFIRM
[[ "$CONFIRM" == "y" || "$CONFIRM" == "Y" ]] || exit 0

cd "$ROOT/backend"
./gradlew clean bootJar
JAR="$(find build/libs -maxdepth 1 -name '*.jar' ! -name '*-plain.jar' | head -n 1)"
[[ -n "$JAR" ]] || { echo "No Spring Boot JAR found"; exit 1; }
cd "$ROOT/frontend"
npm ci
npm run build
tar -czf "$ROOT/frontend-dist.tar.gz" -C dist .

if ! aws s3api head-bucket --bucket "$BUCKET" --profile "$PROFILE" 2>/dev/null; then
  aws s3api create-bucket --bucket "$BUCKET" --region "$REGION" --create-bucket-configuration "LocationConstraint=$REGION" --profile "$PROFILE"
  aws s3api put-public-access-block --bucket "$BUCKET" --public-access-block-configuration BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true --profile "$PROFILE"
fi
aws s3 cp "$ROOT/backend/$JAR" "s3://$BUCKET/$PREFIX/dealmesh.jar" --region "$REGION" --profile "$PROFILE"
aws s3 cp "$ROOT/frontend-dist.tar.gz" "s3://$BUCKET/$PREFIX/frontend.tar.gz" --region "$REGION" --profile "$PROFILE"
rm -f "$ROOT/frontend-dist.tar.gz"

JAR_URL="$(aws s3 presign "s3://$BUCKET/$PREFIX/dealmesh.jar" --expires-in 3600 --region "$REGION" --profile "$PROFILE")"
WEB_URL="$(aws s3 presign "s3://$BUCKET/$PREFIX/frontend.tar.gz" --expires-in 3600 --region "$REGION" --profile "$PROFILE")"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/remote.sh" <<'REMOTE'
#!/usr/bin/env bash
set -euo pipefail
mkdir -p /opt/dealmesh /var/www/dealmesh /var/lib/dealmesh
curl --fail --location --silent --show-error "$JAR_URL" -o /opt/dealmesh/dealmesh.jar
curl --fail --location --silent --show-error "$WEB_URL" -o /tmp/dealmesh-frontend.tar.gz
rm -rf /var/www/dealmesh/*
tar -xzf /tmp/dealmesh-frontend.tar.gz -C /var/www/dealmesh
chown -R ec2-user:ec2-user /opt/dealmesh /var/www/dealmesh /var/lib/dealmesh
cat > /etc/systemd/system/dealmesh.service <<'SERVICE'
[Unit]
Description=DealMesh Spring Boot
After=network-online.target
Wants=network-online.target

[Service]
User=ec2-user
WorkingDirectory=/var/lib/dealmesh
Environment=DEALMESH_DB_URL=jdbc:sqlite:/var/lib/dealmesh/dealmesh.db
Environment=DEALMESH_FRONTEND_URL=http://dealmesh-demo.duckdns.org
Environment=JAVA_TOOL_OPTIONS=-Xms128m -Xmx768m
EnvironmentFile=-/etc/dealmesh.env
ExecStart=/usr/bin/java -jar /opt/dealmesh/dealmesh.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
SERVICE
cat > /etc/nginx/conf.d/dealmesh.conf <<'NGINX'
server {
    listen 80;
    server_name dealmesh-demo.duckdns.org _;
    root /var/www/dealmesh;
    index index.html;
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
    location /actuator/ {
        proxy_pass http://127.0.0.1:8080;
    }
    location / { try_files $uri $uri/ /index.html; }
}
NGINX
systemctl daemon-reload
systemctl enable --now dealmesh
nginx -t
systemctl reload nginx
systemctl --no-pager status dealmesh
REMOTE

# Put the temporary URLs into the script without shell interpretation.
python3 - "$TMP/remote.sh" "$JAR_URL" "$WEB_URL" <<'PY'
import pathlib, sys, shlex
p = pathlib.Path(sys.argv[1])
s = p.read_text()
s = s.replace('curl --fail --location --silent --show-error "$JAR_URL"', 'curl --fail --location --silent --show-error ' + shlex.quote(sys.argv[2]))
s = s.replace('curl --fail --location --silent --show-error "$WEB_URL"', 'curl --fail --location --silent --show-error ' + shlex.quote(sys.argv[3]))
p.write_text(s)
PY

# Send the script through SSM Run Command (no SSH, no inbound admin port).
python3 - "$TMP/remote.sh" "$TMP/parameters.json" <<'PY'
import base64, json, pathlib, sys
script = pathlib.Path(sys.argv[1]).read_bytes()
command = 'echo ' + base64.b64encode(script).decode() + ' | base64 -d | sudo bash'
pathlib.Path(sys.argv[2]).write_text(json.dumps({'commands':[command]}))
PY
COMMAND_ID="$(aws ssm send-command --instance-ids "$INSTANCE_ID" --document-name AWS-RunShellScript --parameters "file://$TMP/parameters.json" --region "$REGION" --profile "$PROFILE" --query 'Command.CommandId' --output text)"
echo "SSM command: $COMMAND_ID"
aws ssm wait command-executed --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --region "$REGION" --profile "$PROFILE" || true
aws ssm get-command-invocation --command-id "$COMMAND_ID" --instance-id "$INSTANCE_ID" --region "$REGION" --profile "$PROFILE" --query '{Status:Status,Output:StandardOutputContent,Error:StandardErrorContent}' --output json
echo "HTTP deployment target: http://dealmesh-demo.duckdns.org (HTTPS setup is separate)"
