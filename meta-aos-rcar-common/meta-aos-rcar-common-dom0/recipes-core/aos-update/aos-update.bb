SUMMARY = "Target to generate AOS update bundle"
LICENSE = "Apache-2.0"

# Inherit

inherit metadata-generator bundle-generator rootfs-image-generator

# Variables

AOS_BUNDLE_DIR ?= "${DEPLOY_DIR}/update"
AOS_BUNDLE_FILE ?= "${AOS_UNIT_MODEL}-${AOS_UNIT_VERSION}-${DOMAIN_NAME}-${AOS_BUNDLE_IMAGE_VERSION}.tar"

AOS_BUNDLE_DOM0_TYPE ?= ""
AOS_BUNDLE_DOMD_TYPE ?= ""
AOS_BUNDLE_RH840_TYPE ?= ""

AOS_BUNDLE_OSTREE_REPO ?= "${DEPLOY_DIR}/update/repo"

DOMD_DEPLOY_DIR = "${EXTERNALSRC_pn-domd}"

# Dependencies

do_create_bundle[depends] += "core-image-thin-initramfs:do_${BB_DEFAULT_TASK}"
do_create_bundle[cleandirs] = "${BUNDLE_WORK_DIR}"
do_create_bundle[dirs] = "${AOS_BUNDLE_DIR}"
do_create_dom0_image[cleandirs] = "${WORKDIR}/rootfs_dom0"
do_prepare_rootfs[cleandirs] = "${WORKDIR}/rootfs_domx"

# Configuration

BUNDLE_DOM0_ID = "${AOS_UNIT_MODEL}-${AOS_UNIT_VERSION}-${DOMAIN_NAME}-dom0"
BUNDLE_DOMD_ID = "${AOS_UNIT_MODEL}-${AOS_UNIT_VERSION}-${DOMAIN_NAME}-domd"
BUNDLE_RH850_ID = "${AOS_UNIT_MODEL}-${AOS_UNIT_VERSION}-${DOMAIN_NAME}-rh850"

BUNDLE_DOM0_DESC = "Dom0 image"
BUNDLE_DOMD_DESC = "DomD image"
BUNDLE_RH850_DESC = "RH850 image"

ROOTFS_IMAGE_DIR = "${BUNDLE_WORK_DIR}"
ROOTFS_EXCLUDE_FILES = "var/*"

DOM0_IMAGE_FILE = "${AOS_BUNDLE_DOM0_ID}-${AOS_BUNDLE_DOM0_TYPE}-${AOS_DOM0_IMAGE_VERSION}.gz"
DOMD_IMAGE_FILE = "${AOS_BUNDLE_DOMD_ID}-${AOS_BUNDLE_DOMD_TYPE}-${AOS_DOMD_IMAGE_VERSION}.squashfs"
RH850_IMAGE_FILE = "${AOS_BUNDLE_RH850_ID}-${AOS_BUNDLE_RH850_TYPE}-${AOS_RH850_IMAGE_VERSION}.gz"

DOM0_PART_SIZE = "128"
DOM0_PART_LABEL = "boot"

# Tasks

python do_create_metadata() {
    components_metadata = []
    
    if d.getVar("AOS_BUNDLE_DOM0_TYPE") == "full":
        components_metadata.append(create_component_metadata(d.getVar("BUNDLE_DOM0_ID"), d.getVar("DOM0_IMAGE_FILE"),
            d.getVar("AOS_DOM0_IMAGE_VERSION"), d.getVar("BUNDLE_DOM0_DESC")))
    elif d.getVar("AOS_BUNDLE_DOM0_TYPE"):
        bb.fatal("Wrong dom0 image type: %s" % d.getVar("AOS_BUNDLE_DOM0_TYPE"))

    if d.getVar("AOS_BUNDLE_DOMD_TYPE"):
        install_dep = {}
        annotations = {}

        if d.getVar("AOS_BUNDLE_DOMD_TYPE") == "incremental":
            install_dep = create_dep(d.getVar("BUNDLE_DOMD_ID"), d.getVar("AOS_DOMD_REF_VERSION"))
            annotations = {"type": "incremental"}
        elif d.getVar("AOS_BUNDLE_DOMD_TYPE") == "full":
            annotations = {"type": "full"}
        else:
            bb.fatal("Wrong domd image type: %s" % d.getVar("AOS_BUNDLE_DOMD_TYPE"))

        components_metadata.append(create_component_metadata(d.getVar("BUNDLE_DOMD_ID"), d.getVar("DOMD_IMAGE_FILE"),
            d.getVar("AOS_DOMD_IMAGE_VERSION"), d.getVar("BUNDLE_DOMD_DESC"), install_dep, None, annotations))

    if d.getVar("AOS_BUNDLE_RH850_TYPE") == "full":
        components_metadata.append(create_component_metadata(d.getVar("BUNDLE_RH850_ID"), d.getVar("RH850_IMAGE_FILE"),
            d.getVar("AOS_RH850_IMAGE_VERSION"), d.getVar("BUNDLE_RH850_DESC")))
    elif d.getVar("AOS_BUNDLE_RH850_TYPE"):
        bb.fatal("Wrong RH850 image type: %s" % d.getVar("AOS_BUNDLE_RH850_TYPE"))

    write_image_metadata(d.getVar("BUNDLE_WORK_DIR"), components_metadata)
}

do_create_dom0_image() {
    if [ -z ${AOS_BUNDLE_DOM0_TYPE} ]; then
        exit 0
    fi

    dom0_root="${DEPLOY_DIR}/images/${MACHINE}"

    image=`find $dom0_root -name Image`
    uinitramfs=`find $dom0_root -name uInitramfs`
    aos=`find $dom0_root -name aos`

    domd_root=${DOMD_DEPLOY_DIR}

    xendtb=`find $domd_root -name "${XT_XEN_DTB_NAME}"`
    xenpolicy=`find $domd_root -name xenpolicy-${XT_DOMD_MACHINE}`
    xenuimage=`find $domd_root -name xen-uImage`

    cp -Lf $image ${WORKDIR}/rootfs_dom0/Image
    cp -Lf $uinitramfs ${WORKDIR}/rootfs_dom0/uInitramfs
    cp -Lf $xendtb ${WORKDIR}/rootfs_dom0/xen.dtb
    cp -Lf $xenpolicy ${WORKDIR}/rootfs_dom0/xenpolicy
    cp -Lf $xenuimage ${WORKDIR}/rootfs_dom0/xen

    mkdir ${WORKDIR}/rootfs_dom0/aos
    cp -Lrf $aos/* ${WORKDIR}/rootfs_dom0/aos/

    dd if=/dev/zero of=${WORKDIR}/dom0.ext4 bs=1M count=${DOM0_PART_SIZE}
    mkfs.ext4 -F -L ${DOM0_PART_LABEL} -E root_owner=0:0 -d ${WORKDIR}/rootfs_dom0 ${WORKDIR}/dom0.ext4

    gzip < ${WORKDIR}/dom0.ext4 > ${BUNDLE_WORK_DIR}/${DOM0_IMAGE_FILE}
}

do_prepare_rootfs() {
    if [ -z ${ROOTFS_IMAGE_TYPE} ]; then
        exit 0
    fi

    tar -C ${WORKDIR}/rootfs_domx -xjf ${ROOTFS_SOURCE_ARCHIVE}
}

python do_create_domd_image() {
    d.setVar("AOS_ROOTFS_OSTREE_REPO", os.path.join(d.getVar("AOS_BUNDLE_OSTREE_REPO"), d.getVar("BUNDLE_DOMD_ID")))
    d.setVar("AOS_ROOTFS_IMAGE_TYPE", d.getVar("AOS_BUNDLE_DOMD_TYPE"))
    d.setVar("AOS_ROOTFS_IMAGE_VERSION", d.getVar("AOS_DOMD_IMAGE_VERSION"))
    d.setVar("AOS_ROOTFS_REF_VERSION", d.getVar("AOS_DOMD_REF_VERSION"))
    d.setVar("AOS_ROOTFS_IMAGE_FILE", d.getVar("DOMD_IMAGE_FILE"))
    d.setVar("AOS_ROOTFS_SOURCE_DIR", os.path.join(d.getVar("WORKDIR"),"rootfs_domx"))
    d.setVar("AOS_ROOTFS_SOURCE_ARCHIVE", os.path.join(d.getVar("DOMD_DEPLOY_DIR"), \
        "{}-{}.tar.bz2".format(d.getVar("XT_DOMD_IMAGE"), d.getVar("XT_DOMD_MACHINE"))))
    
    bb.build.exec_func("do_prepare_rootfs", d)
    bb.build.exec_func("do_create_rootfs_image", d)
}

do_create_rh850_image() {
    if [ -z ${AOS_BUNDLE_RH850_TYPE} ]; then
        exit 0
    fi

    if [ ! -f ${AOS_RH850_IMAGE_PATH} ]; then
        bbfatal "RH850 image ${AOS_RH850_IMAGE_PATH} not found"
    fi

    gzip < ${AOS_RH850_IMAGE_PATH} > ${BUNDLE_WORK_DIR}/${RH850_IMAGE_FILE}
}
 
python do_create_bundle() {
    if not d.getVar("AOS_BUNDLE_DOM0_TYPE") and not d.getVar("AOS_BUNDLE_DOMD_TYPE") and not d.getVar("BUNDLE_RH840_TYPE"):
        bb.fatal("There are no componenets to add to the bundle")

    bb.build.exec_func("do_create_metadata", d)
    bb.build.exec_func("do_create_dom0_image", d)
    bb.build.exec_func("do_create_domd_image", d)
    bb.build.exec_func("do_create_rh850_image", d)
    bb.build.exec_func("do_pack_bundle", d)
}

addtask create_bundle after do_compile before do_build
