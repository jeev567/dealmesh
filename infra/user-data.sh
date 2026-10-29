#!/bin/bash
set -euxo pipefail
dnf update -y
dnf install -y java-21-amazon-corretto-headless nginx git
mkdir -p /opt/dealmesh /var/www/dealmesh /var/lib/dealmesh
chown -R ec2-user:ec2-user /opt/dealmesh /var/www/dealmesh /var/lib/dealmesh
systemctl enable nginx
systemctl start nginx
