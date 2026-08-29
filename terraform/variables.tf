variable "owner" {
  description = "Prefix and tag for every resource. This is a SHARED training account - dozens of people's buckets sit alongside these, and a nightly cleanup job decides what to reap. Anything unowned is either someone else's or about to be deleted."
  type        = string
  default     = "daniel-palencia"
}

variable "region" {
  description = "us-east-1 is not arbitrary here. CloudFront is a global service, but any ACM certificate it uses must live in us-east-1 specifically - so putting everything there now avoids a split later when a custom domain arrives."
  type        = string
  default     = "us-east-1"
}
