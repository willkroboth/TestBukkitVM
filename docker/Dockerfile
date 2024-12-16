# syntax=docker/dockerfile:1
###################################
# Based on alpine b/c lightweight #
###################################
FROM alpine:3.20.3 AS base
WORKDIR /root/

######################
# Setup dependencies #
######################
# Java
RUN apk --no-cache add openjdk21-jre

# Libvirt: https://lunar.computer/news/kvm-qemu-libvirt-alpine/
RUN apk --no-cache add \
    qemu-img qemu-system-x86_64 \
    libvirt-daemon


##################
# Create base VM #
##################
FROM base AS setup

# Add virt-install
RUN apk --no-cache add virt-install

# Add files jar needs to run
COPY /resources/setupFiles/ ./

# Add jar file
COPY /target/docker-1.0-SNAPSHOT-Setup.jar ./main.jar

# Run program to create base VM
SHELL ["/bin/sh", "-c"]
CMD ./createVM.sh

###########
# Run VMs #
###########
FROM base AS run

# Add files jar needs to run
#  Order by least likely to update first and file size to take advantage of the cache more
COPY /resources/runFiles/storage/vms/ ./storage/vms/
COPY /resources/runFiles/storage/vmData/ ./storage/vmData/
COPY /resources/runFiles/storage/serverVersions/ ./storage/serverVersions/

COPY /resources/runFiles/containerEntrypoint.sh ./
COPY /resources/runFiles/testFile ./
COPY /resources/runFiles/storage/serverTemplate/ ./storage/serverTemplate/

# Add jar file
COPY /target/docker-1.0-SNAPSHOT-Run.jar ./main.jar

# Copy vm files
#  It would be cool if we could run the "createVM" script as part of the setup Docker build, and just
#  copy that into the run image. I haven't really figured out how to fully automate that process though,
#  and there are still issues with running libvirt inside the docker container by the dockerfile.
#COPY --from=setup ./storage/ ./storage/

# Run jar when container starts
SHELL ["/bin/sh", "-c"]
CMD "/root/containerEntrypoint.sh"

############
# Debug VM #
############
FROM run AS debug

# Java debug agent
ENV JAVA_TOOL_OPTIONS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
EXPOSE 5005