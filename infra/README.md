# DealMesh AWS infrastructure

Independent EC2 deployment using the same single-instance pattern as CrowdRewards.

## Prerequisites
- AWS CLI authenticated in us-west-2
- Terraform installed
- Existing VPC, internet-routable public subnet, and EC2 SSH key
- Your current public IP for the SSH allowlist

## Provision
```bash
cd infra
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars with actual resource IDs and SSH settings
terraform init
terraform plan
# Review changes before running terraform apply
```

The instance runs Amazon Linux 2023, Java 21 and Nginx. It does not expose port 8080 publicly. Application deployment, systemd configuration, SQLite file permissions, DuckDNS updates, Certbot TLS setup, and PayPal callback configuration are separate steps.

## Domain
Create dealmesh-demo.duckdns.org in DuckDNS. After provisioning, update its A record to the public_ip Terraform output. Public IP may change when the instance is stopped and started; consider an Elastic IP for stability (additional AWS charges may apply).

Never commit terraform.tfvars, credentials, API keys, DuckDNS tokens, or Terraform state.
