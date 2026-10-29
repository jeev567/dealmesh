output "instance_id" {
  value = aws_instance.dealmesh.id
}

output "public_ip" {
  value = aws_instance.dealmesh.public_ip
}

output "ssm_command" {
  value = "aws ssm start-session --target ${aws_instance.dealmesh.id} --region ${var.aws_region} --profile dealseal-admin"
}

output "domain" {
  value = "dealmesh-demo.duckdns.org"
}
