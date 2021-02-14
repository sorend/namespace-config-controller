
IMAGE = sorend/namespace-config-controller

default: help

# Builds docker image (requires docker)
all: build docker

# Builds java application (requires JDK 11)
build:
    ./gradlew build

# Create docker image
docker:
	docker build -t $(IMAGE):latest -f src/main/docker/Dockerfile.jvm .

# Push docker image
docker-deploy:
	echo "$(DOCKER_PASSWORD)" | docker login -u $(DOCKER_USERNAME) --password-stdin
	docker tag $(IMAGE):latest $(IMAGE):$(TRAVIS_TAG)
	docker push $(IMAGE):$(TRAVIS_TAG)

# Show this help prompt.
help:
	@ echo
	@ echo '  Usage:'
	@ echo ''
	@ echo '    make <target> [flags...]'
	@ echo ''
	@ echo '  Targets:'
	@ echo ''
	@ awk '/^#/{ comment = substr($$0,3) } comment && /^[a-zA-Z][a-zA-Z0-9_-]+ ?:/{ print "   ", $$1, comment }' $(MAKEFILE_LIST) | column -t -s ':' | sort
	@ echo ''
