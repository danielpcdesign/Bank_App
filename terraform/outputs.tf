output "site_url" {
  description = "The URL to open. CloudFront serves HTTPS on its own domain with no certificate to configure."
  value       = "https://${aws_cloudfront_distribution.site.domain_name}"
}

output "bucket_name" {
  description = "Upload target for `bankui/dist`."
  value       = aws_s3_bucket.site.id
}

output "distribution_id" {
  description = "Needed to invalidate the cache after a deploy - see the note in main.tf."
  value       = aws_cloudfront_distribution.site.id
}
