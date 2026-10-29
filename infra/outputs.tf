output "instance_id" { value = aws_instance.dealmesh.id }
output "public_ip" { value = aws_instance.dealmesh.public_ip }
output "ssh_command" { value = "ssh -i YOUR_KEY.pem ec2-user@${aws_instance.dealmesh.public_ip}" }
output "domain" { value = "dealmesh-demo.duckdns.org" }
