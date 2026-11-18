#!/usr/bin/env bash
# Source this helper only inside a Jenkins credentials binding.
set +x
export AWS_DEFAULT_REGION=eu-north-1
export AWS_REGION=eu-north-1
export AWS_PAGER=''
ECR_REGISTRY=425989919753.dkr.ecr.eu-north-1.amazonaws.com
AWS_CLI_IMAGE=public.ecr.aws/aws-cli/aws-cli@sha256:da6972cf22cea808302c59e861e7a59ad6c042bb4ae33e44a5f42483b3a46143
services=(aviation identity maintenance notification gateway)

aws_cli() {
  docker run --rm -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
    -e AWS_DEFAULT_REGION -e AWS_REGION -e AWS_PAGER "$AWS_CLI_IMAGE" "$@"
}

verify_publisher() {
  local identity
  identity=$(aws_cli sts get-caller-identity --query Arn --output text)
  [ "$identity" = arn:aws:iam::425989919753:user/flight-jenkins-publisher ] || {
    echo 'Unexpected AWS publishing identity; refusing to continue.' >&2; return 1;
  }
  echo 'Verified restricted flight-jenkins-publisher in AWS account 425989919753.'
}

validate_tag() {
  [[ "${IMAGE_TAG:-}" =~ ^[0-9a-f]{12}-[0-9]+$ ]] || {
    echo 'IMAGE_TAG must be the commit-build tag from a successful backend CI build.' >&2; return 1;
  }
}

write_release_manifest() {
  local service digest
  mkdir -p target
  : > target/release-images.tsv
  for service in "${services[@]}"; do
    digest=$(aws_cli ecr describe-images --repository-name "sauds-flight-system-$service" \
      --image-ids "imageTag=$IMAGE_TAG" --query 'imageDetails[0].imageDigest' --output text)
    [[ "$digest" =~ ^sha256:[0-9a-f]{64}$ ]] || { echo "Missing published digest: $service" >&2; return 1; }
    printf '%s\t%s/sauds-flight-system-%s@%s\n' "$service" "$ECR_REGISTRY" "$service" "$digest" >> target/release-images.tsv
  done
}
