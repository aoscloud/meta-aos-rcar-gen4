IMAGE_INSTALL += " \
    pciutils \
    devmem2 \
    iccom-support \
    optee-test \
"

IMAGE_INSTALL += "iproute2 tcpdump nvme-cli"

IMAGE_INSTALL += " kernel-module-nvme-core kernel-module-nvme"

# System compomnents
IMAGE_INSTALL += " \
    openssh \
"

# Aos components
IMAGE_INSTALL += " \
    aos-communicationmanager \
    aos-iamanager \
    aos-servicemanager \
    aos-updatemanager \
    aos-vis \
"

# Enable RO rootfs
IMAGE_FEATURES_append = " read-only-rootfs"

# Aos related tasks

ROOTFS_POSTPROCESS_COMMAND_append += "set_board_model; set_rootfs_version;"

set_board_model() {
    install -d ${IMAGE_ROOTFS}/etc/aos

    echo "${BOARD_MODEL}" > ${IMAGE_ROOTFS}/etc/aos/board_model
}

set_rootfs_version() {
    install -d ${IMAGE_ROOTFS}/etc/aos

    echo "VERSION=\"${DOMD_IMAGE_VERSION}\"" > ${IMAGE_ROOTFS}/etc/aos/version
}
