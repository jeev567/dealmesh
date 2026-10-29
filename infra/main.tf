terraform {
  required_version = ">= 1.5.0"
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.0" }
  }
}
provider "aws" { region = var.aws_region }

data "aws_ami" "al2023" {
  most_recent = true
  owners = ["amazon"]
  filter {
    name = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
  filter {
    name = "virtualization-type"
    values = ["hvm"]
  }
}

resource "aws_security_group" "dealmesh" {
  name_prefix = "dealmesh-demo-"
  description = "DealMesh demo HTTPS, HTTP and restricted SSH"
  vpc_id = var.vpc_id

  ingress {
    description = "HTTP for redirect and ACME"
    from_port = 80
    to_port = 80
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "HTTPS"
    from_port = 443
    to_port = 443
    protocol = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
  ingress {
    description = "SSH from operator"
    from_port = 22
    to_port = 22
    protocol = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }
  egress {
    from_port = 0
    to_port = 0
    protocol = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
  tags = { Name = "dealmesh-demo-sg" }
}

resource "aws_instance" "dealmesh" {
  ami = data.aws_ami.al2023.id
  instance_type = var.instance_type
  subnet_id = var.public_subnet_id
  vpc_security_group_ids = [aws_security_group.dealmesh.id]
  key_name = var.ssh_key_name
  associate_public_ip_address = true
  user_data = file("${path.module}/user-data.sh")
  root_block_device {
    volume_size = 20
    volume_type = "gp3"
    encrypted = true
  }
  metadata_options {
    http_endpoint = "enabled"
    http_tokens = "required"
  }
  tags = { Name = "dealmesh-demo" }
}
