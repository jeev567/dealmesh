variable "aws_region" {
  type    = string
  default = "us-west-2"
}

variable "vpc_id" {
  type = string
}

variable "public_subnet_id" {
  type = string
}

variable "ssh_key_name" {
  type = string
}

variable "ssh_cidr" {
  type        = string
  description = "Your public IP in CIDR notation, e.g. 203.0.113.5/32"
}

variable "instance_type" {
  type    = string
  default = "t3.small"
}
