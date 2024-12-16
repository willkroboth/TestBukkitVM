set -e
SCRIPT=$(realpath "$0")
SCRIPTPATH=$(dirname "$SCRIPT")
echo "$SCRIPTPATH"
cd "$SCRIPTPATH"

# Start libvirt daemon
libvirtd -d
virtlogd -d

# Run program
java -jar ./main.jar "$SCRIPTPATH"