/*
 * Phase 11 - the front end on S3 behind CloudFront.
 *
 * WHAT SHIPS IS `bankui/dist`, NOT THE DOCKER IMAGE. The nginx container built in Phase 9
 * never reaches production: its job was to serve static files and proxy /api, and CloudFront
 * does both. That is why bankui/Dockerfile has no VITE_API_BASE_URL build arg - the bundle is
 * environment-independent precisely so the same artifact can be served by either.
 *
 * The API is deliberately not deployed yet. Until it is, a fetch to /api falls through to the
 * SPA fallback below and returns index.html with a 200, so the browser tries to JSON.parse
 * HTML. That is a known intermediate state, not a defect to route around.
 */

terraform {
  required_version = ">= 1.5"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }
}

provider "aws" {
  region = var.region

  # Applied to everything this configuration creates. In a shared account with a nightly
  # cleanup job, an untagged resource is indistinguishable from abandoned - tags are what
  # make it possible to answer "is this still needed" without asking every person in the room.
  default_tags {
    tags = {
      Owner   = var.owner
      Project = "banking-app"
      Phase   = "11-cloud"
    }
  }
}

# ---------------------------------------------------------------------------- storage

resource "aws_s3_bucket" "site" {
  bucket = "${var.owner}-banking-frontend"
}

/*
 * The bucket is PRIVATE, and stays private. CloudFront reaches it through Origin Access
 * Control; nothing else can read it at all.
 *
 * The tutorial alternative - enable S3 static website hosting and make the bucket public -
 * works and is worse in a way that is easy to miss. It leaves a second, unprotected front
 * door: the S3 website endpoint. Anyone who finds it bypasses CloudFront entirely, and with
 * it every cache rule, every header, and later every WAF rule or access log. The origin
 * being unreachable except through the distribution is what makes the distribution's
 * behaviour the only behaviour.
 */
resource "aws_s3_bucket_public_access_block" "site" {
  bucket                  = aws_s3_bucket.site.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# OAC is how a distribution proves to S3 that a request came from it. Origin Access IDENTITY
# is the older mechanism still shown in most tutorials; OAC replaced it and is what new work
# should use.
resource "aws_cloudfront_origin_access_control" "site" {
  name                              = "${var.owner}-banking-frontend-oac"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# The only statement on the bucket: this one distribution may read. Note the condition - it
# names the distribution by ARN, so another distribution in this shared account cannot point
# itself at this bucket and be served.
data "aws_iam_policy_document" "site" {
  statement {
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.site.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.site.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "site" {
  bucket = aws_s3_bucket.site.id
  policy = data.aws_iam_policy_document.site.json
}

# ---------------------------------------------------------------------------- cdn

# AWS-managed cache policies, referenced by name rather than by the raw UUIDs most examples
# paste in. Same result, and it says what it means.
data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}

data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}

# Forwards the viewer's headers, query strings and cookies to the origin. The DEFAULT is to
# forward almost nothing, which for an API is fatal in a way that is hard to diagnose: strip
# Content-Type and every POST body arrives unlabelled, so Spring answers 415 to requests that
# are perfectly well formed. ExceptHostHeader rather than AllViewer so the origin still sees
# its own hostname rather than the distribution's.
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewerExceptHostHeader"
}

resource "aws_cloudfront_distribution" "site" {
  enabled             = true
  default_root_object = "index.html"
  comment             = "${var.owner} banking app front end"

  origin {
    domain_name              = aws_s3_bucket.site.bucket_regional_domain_name
    origin_id                = "s3-site"
    origin_access_control_id = aws_cloudfront_origin_access_control.site.id
  }

  /*
   * The API, on EC2. Referenced through the resource rather than hardcoded, so replacing the
   * instance updates the origin instead of silently breaking it - which matters here because
   * the account had no Elastic IP left and the address is therefore not stable.
   *
   * http-only: the instance terminates plain HTTP on 8080 with no certificate. That leaves
   * the CloudFront-to-origin hop UNENCRYPTED across the public internet, which is a real
   * weakness and an accepted one for a training deployment - viewer-to-CloudFront is HTTPS,
   * so a browser sees TLS, but the last leg is not protected. Closing it properly needs a
   * certificate on the instance, which needs a domain name.
   */
  origin {
    domain_name = aws_instance.api.public_dns
    origin_id   = "ec2-api"

    custom_origin_config {
      http_port              = 8080
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
    }
  }

  /*
   * The default behaviour is the CONSERVATIVE one: no caching, revalidate every time. That
   * is deliberate and it mirrors nginx.conf exactly - index.html is the file that NAMES the
   * current asset hashes, so caching it means a returning browser loads an old app pointing
   * at bundles that no longer exist. A deploy that appears to do nothing, then 404s.
   */
  default_cache_behavior {
    target_origin_id       = "s3-site"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.disabled.id
    compress               = true
  }

  # The other half of the pair. Vite fingerprints its output - index-SIhKNWSi.js - so a given
  # filename can never mean two different things, which is what makes a year-long cache safe.
  # Ordered behaviours are evaluated before the default and in the order written.
  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = "s3-site"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
  }

  # The SPA fallback. BrowserRouter uses the History API, so /customers/2/edit is a real GET
  # for a path with no object behind it - without this, a refresh or a shared link fails and
  # the router never loads.
  #
  # 403, not 404. A private bucket does not answer 404 for a missing key: revealing whether
  # an object exists is information S3 will not give a caller with no permission to read it.
  # Mapping 404 here - what most tutorials show - would silently do nothing.
  #
  # The trade, made deliberately and already made once in nginx.conf: a genuine typo now
  # returns the app with a 200 rather than an error, which is why App.jsx carries a
  # path="*" route. The server can no longer tell a typo from a valid client route.
  custom_error_response {
    error_code         = 403
    response_code      = 200
    response_page_path = "/index.html"
  }

  /*
   * /api/* goes to the instance, everything else to S3.
   *
   * allowed_methods lists every write verb. The default is GET and HEAD only, so without this
   * POST, PUT and DELETE are rejected BY CLOUDFRONT before they ever reach the API - a 403
   * that looks like the application refusing the request rather than the CDN.
   *
   * cached_methods stays GET/HEAD: CloudFront will not cache a POST regardless, and listing
   * write verbs there would be meaningless rather than harmful.
   *
   * CachingDisabled because an API response is not a static asset. Caching /api/v1/customers
   * would serve one user's list to the next request and hide every write that followed it.
   */
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "ec2-api"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    compress                 = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  # The default CloudFront certificate, valid for *.cloudfront.net. A custom domain would
  # need an ACM certificate in us-east-1 - which is why var.region is pinned there.
  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
