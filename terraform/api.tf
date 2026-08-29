/*
 * Phase 11, stage 2 - the API on EC2.
 *
 * NOT ECS, and not by preference: this account denies ecr:* and ecs:* outright, including
 * reads. EC2 is also what every teammate running a banking API in this account uses, and it
 * is the only option that runs the Phase 9 image unchanged - no Lambda adapter, no rewrite,
 * no cold start on a reviewer's first click.
 *
 * With ECR unavailable there is no registry to push to, so the instance clones the public
 * repository and builds the image itself. That turns a permissions dead end into one fewer
 * moving part: nothing has to be pushed anywhere, and the instance always builds what is on
 * the default branch.
 */

variable "app_branch" {
  description = "Branch the instance builds from. Pinned explicitly rather than relying on the repository default: `main` is two phases behind and has no Dockerfile, which is what the first boot discovered. An explicit branch also means a deploy cannot change meaning because somebody merged something."
  type        = string
  default     = "CI_CD"
}

variable "ssh_cidr" {
  description = "Where SSH may originate. A single address, not 0.0.0.0/0 - port 22 open to the internet is found by scanners within minutes."
  type        = string
  default     = "97.79.56.179/32"
}

# Latest Amazon Linux 2023, resolved at plan time. The usual approach is an SSM public
# parameter, which this account denies - so it is a filtered image lookup instead. most_recent
# with an owner filter matters: without the owner, the name pattern would match anything
# anybody published.
data "aws_ami" "al2023" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-2023.*-x86_64"]
  }
}

# CloudFront's published origin-facing address ranges, maintained by AWS. Referencing the
# list means the rule keeps working when those ranges change, which they do.
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "api" {
  name        = "${var.owner}-bankapi"
  description = "bankapi: SSH from one address, 8080 from CloudFront only"

  # The API has NO AUTHENTICATION yet - Phase 10 is unstarted. Opening 8080 to the world
  # would put unauthenticated CRUD on the public internet. Restricting ingress to CloudFront
  # means the distribution is the only way in, so the app is exactly as reachable as the site
  # and not one address more. This is a mitigation, not a substitute for the missing auth.
  ingress {
    description     = "HTTP from CloudFront edge locations only"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  ingress {
    description = "SSH from one address"
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = [var.ssh_cidr]
  }

  # Outbound is unrestricted because the instance genuinely needs the internet: GitHub to
  # clone, Docker Hub and Maven Central to build, and MongoDB Atlas to serve.
  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "api" {
  ami                    = data.aws_ami.al2023.id
  instance_type          = "t3.micro"
  key_name               = "${var.owner}-bankapi"
  vpc_security_group_ids = [aws_security_group.api.id]

  tags = { Name = "${var.owner}-bankapi" }

  # Without this, editing user_data updates the attribute in state and leaves the running
  # instance untouched - so the box keeps whatever it booted with while the config claims
  # otherwise. That is the same documented-path-with-no-referent problem, in infrastructure:
  # the declaration and the reality drift apart silently. true makes a bootstrap change
  # recreate the instance, which is the only way the script and the machine stay the same
  # thing.
  user_data_replace_on_change = true

  user_data = <<-BOOT
    #!/bin/bash
    set -eux

    # t3.micro has 1 GB of RAM, and a Maven build of a Spring Boot application inside Docker
    # will exhaust it and be killed by the OOM reaper - which presents as a build that dies
    # with no error, not as a memory message. 2 GB of swap costs nothing while idle and is
    # what makes building on the smallest instance possible at all.
    dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab

    dnf install -y docker git
    systemctl enable --now docker
    usermod -aG docker ec2-user

    git clone --depth 1 --branch ${var.app_branch} https://github.com/danielpcdesign/Bank_App.git /opt/src
    docker build -t bankapi:local /opt/src/bankapi

    mkdir -p /opt/bankapi

    # The service reads the connection string from a file this script deliberately does NOT
    # create. Putting it in user-data would work and would also place a freshly rotated
    # credential into instance metadata, readable by anything that can reach 169.254.169.254
    # from on the box. It is written once over SSH instead.
    cat > /etc/systemd/system/bankapi.service <<'UNIT'
    [Unit]
    Description=bankapi
    After=docker.service
    Requires=docker.service

    [Service]
    EnvironmentFile=/opt/bankapi/.env
    ExecStartPre=-/usr/bin/docker rm -f bankapi
    ExecStart=/usr/bin/docker run --rm --name bankapi -p 8080:8080 -e MONGODB_URI bankapi:local
    Restart=always
    RestartSec=10

    [Install]
    WantedBy=multi-user.target
    UNIT

    systemctl daemon-reload
    systemctl enable bankapi
    touch /opt/bankapi/BOOTSTRAP_COMPLETE
  BOOT
}

/*
 * NO ELASTIC IP. The account has exhausted its allocation - AddressLimitExceeded - which in a
 * shared training account with dozens of students is a quota, not a mistake to fix.
 *
 * The consequence is real and worth stating rather than discovering: the instance's public DNS
 * is tied to its current public IP, and STOPPING AND STARTING the instance assigns a new one,
 * which silently breaks the CloudFront origin pointing here. A reboot is fine; a stop/start is
 * not. If it ever happens, the origin has to be updated and the distribution redeployed.
 */

output "api_public_dns" {
  description = "CloudFront's origin, and the SSH target. NOT stable across a stop/start."
  value       = aws_instance.api.public_dns
}

output "api_ssh" {
  description = "Copy-paste to get onto the box."
  value       = "ssh -i ~/.ssh/${var.owner}-bankapi.pem ec2-user@${aws_instance.api.public_dns}"
}
