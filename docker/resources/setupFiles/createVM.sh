set -e
SCRIPT=$(realpath "$0")
SCRIPTPATH=$(dirname "$SCRIPT")
echo "$SCRIPTPATH"
cd "$SCRIPTPATH"

########################
# Start libvirt daemon #
########################
libvirtd -d
virtlogd -d

#############
# Create VM #
#############
# Named pipe for automatically sending input to VM
#  https://unix.stackexchange.com/a/558666
#mkfifo vmConsole

VMNAME="TestBukkitVM"

# https://linux.die.net/man/1/virt-install
virt-install \
-n $VMNAME \
--os-variant=alpinelinux3.19 \
--cdrom="./alpine-virt-3.20.3-x86_64.iso" \
--disk size=2 \
--ram=2560 \
--vcpus=4 \
--graphics none \
--console pty,target_type=serial \
--noautoconsole
#--console pipe,source.path="$SCRIPTPATH/vmConsole" \ # pipe console


# I want to automate the vm creation
#  I feel like I should be able to pipe input to the console as if a person was typing it.
#  However, this doesn't seem to pause to wait for the vm to process input.
#  I also can't figure out how to inspect the vm state when it has a pipe console,
#  so I don't even know if it is passing the input properly.
#  Right now we're just pausing until the human finishes the required tasks.
## Named pipes: https://stackoverflow.com/a/4113995
## Login
#cat ./vmSetup/loginInstructions.sh > vmConsole
read -p "Please login to vm"
virsh suspend $VMNAME
virsh snapshot-create $VMNAME --xmlfile "./vmSetup/loginSnapshot.xml"

## Setup
virsh resume $VMNAME
read -p "Please setup vm"
#cat ./vmSetup/setupInstructions.sh > vmConsole
virsh suspend $VMNAME
virsh snapshot-create $VMNAME --xmlfile "./vmSetup/setupSnapshot.xml"

###############
# Run program #
###############
# Finish vm setup and export
java -jar ./main.jar $VMNAME "$SCRIPTPATH"