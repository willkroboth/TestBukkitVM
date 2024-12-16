set -e
cd "${0%/*}"

# Load options
RUNTYPE="run"
BUILD="TRUE"
SETUP="FALSE"
EXTRA_RUN_OPTIONS=()
while getopts dbsi flag
do
  case "$flag" in
    d) RUNTYPE="debug"
      EXTRA_RUN_OPTIONS=("${EXTRA_RUN_OPTIONS[@]}" -p 5005:5005);;
    b) BUILD="FALSE";;
    s) SETUP="TRUE";;
    i) EXTRA_RUN_OPTIONS=("${EXTRA_RUN_OPTIONS[@]}" -it --entrypoint /bin/sh)
  esac
done

# Build jar
if [ $BUILD == "TRUE" ]; then
  mvn clean package
fi

# Check if we need to generate the vm files
if [ $SETUP == "TRUE" ] || [ ! -d "./resources/runFiles/storage/" ]; then
  # Run vm setup container
  docker build ./ -t WillKroboth/test-bukkit-vm:setup --target setup
  docker run -it --privileged --name TestBukkitVM WillKroboth/test-bukkit-vm:setup

  # Copy storage files out so run image can use them
  echo Copying VM files out to host
  docker cp TestBukkitVM:/root/storage ./resources/runFiles/
  docker rm TestBukkitVM
fi

# Build vm run container
docker build ./ -t WillKroboth/test-bukkit-vm:$RUNTYPE --target $RUNTYPE
if [ $RUNTYPE == "run" ]; then
  # Export image to file
  docker image save -o ../main/resources/dockerRunVm.tar WillKroboth/test-bukkit-vm:run
fi

# Run vm run container
# NOTE: We need to run privileged to access virbr0.
#  Ideally we wouldn't have to do this, but I don't understand docker networking yet.
#  Perhaps for setup we need this to download packages to the VM,
#  but for run we could just disable networking so it doesn't need to access virbr0.
docker run "${EXTRA_RUN_OPTIONS[@]}" --rm --privileged --name TestBukkitVM WillKroboth/test-bukkit-vm:$RUNTYPE
