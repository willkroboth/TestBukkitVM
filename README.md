# TestBukkitVM

Work in progress for a system that allows testing Bukkit-based plugins on a real server. The idea is that we run the
server on a virtual machine and take memory snapshots at important points, like before plugins load & enable. Before
running a test, we roll back the VM to the desired state in the server startup process. This means tests don't interfere
with each other, and we can run a step in the server startup multiple times while only processing the stuff that
happened before it once. Hopefully it should also be easy to run tests on multiple server versions and flavors by just
switching out the server jar.

I'm not entirely sure if this will work out as perfectly as I imagine. Restoring a VM snapshot seems to take a bit, so
it might be very slow to reset for every test. We'll see how it goes!

I also have no idea what I'm doing :P. This is my first time using Docker or virtual machines, and computer networking
is very mystical. These seem like good things to learn though.

## TODO:

- Project management
    - Documentation so I'm not the only one who understands what all these files mean
    - Clean up logging
- Project functionality
    - Check if the Docker container works on other machines
    - Running a Minecraft server inside a virtual machine inside a docker container is *working*, but the server runs
      very slowly and often causes the JVM inside the VM to crash. Fix pls.
    - Create a plugin jar that communicates with the docker process to create snapshots when plugins are about to
      load/about to enable
    - Main/docker process should coordinate with plugin jar to run tests on the server

---

# Random research and planning

## Runtime plan:

- Load and run Docker Container
    - Docker container has necessary dependencies to start a VM (libvert whatever)
- Inside Docker Container:
    - Define Virtual Machine
    - Create files Spigot/Paper server
    - Add test management plugin
    - Start Virtual Machine
- Inside Virtual Machine:
    - Start Spigot Server
    - Test management plugin `onLoad` invoked
- Inside main process (or Docker container?)
    - Pauses VM and take snapshot
    - For each `onLoad` test:
        - Restore VM state when server just called `onLoad`
        - Setup for test
        - Unpause VM to run test
        - May want `onEnable` to complete to test effect during server runtime (e.g. config setting)
    - Unpause VM and run until test management plugin `onEnable` invoked
    - Pause VM and take snapshot
    - Repeat `onLoad` steps for `onEnable` tests
    - Repeat steps for server runtime tests
        - May simulate a Minecraft client to test result of actions on the server
    - Repeat for server shutdown tests
- Shutdown VM and repeat for different server versions

# Docker container requirements:

- Install native libvirt so JNA can access it
    - https://stackoverflow.com/a/19781422
    - Install here? https://ubuntu.pkgs.org/24.04/ubuntu-main-amd64/libvirt0_10.0.0-2ubuntu8_amd64.deb.html
- Install QEMU
    - https://www.qemu.org/download/#linux
    - Install like here? https://askubuntu.com/a/1256690
- Allow access to '/var/run/libvirt/libvirt-sock'
    - Socket is represented by that file, needs write/read permission
    - https://cets.seas.upenn.edu/answers/chmod.html
    - chmod a+rw /var/run/libvirt/libvirt-sock
- Make sure '/home/linuxbrew/.linuxbrew/var/run/libvirt/virtqemud-sock' exists?
    - https://unix.stackexchange.com/questions/715726/virsh-list-throw-error-failed-to-connect-socket-to-var-run-libvirt-virtqemud#comment1356212_715726
    - or avoid by setting legacy mode? https://unix.stackexchange.com/a/715795
    - Don't install libvirt with brew, seems to mess this up. Maybe because it was also installed normally?

# Create VM

Sources:

- https://unix.stackexchange.com/a/309792
- Explains bridge
  network https://www.dzombak.com/blog/2024/02/Setting-up-KVM-virtual-machines-using-a-bridged-network.html
- https://www.thegeekstuff.com/2014/10/linux-kvm-create-guest-vm/
    - Need to expose network bridge?
    - https://docs.redhat.com/en/documentation/red_hat_enterprise_linux/6/html/deployment_guide/s2-networkscripts-interfaces_network-bridge#s2-networkscripts-interfaces_network-bridge
- http://thomasmullaly.com/2014/11/16/the-list-of-os-variants-in-kvm/
- https://linux.die.net/man/1/virt-install
- Location install from iso needs to say where the kernel is located https://serverfault.com/a/1150590
- Console and extra-args parameters fix
  console https://github.com/virt-manager/virt-manager/issues/693#issuecomment-2379551125
- https://alpinelinux.org/downloads/
- Alpine instructions for creating a VM (not
  useful?) https://wiki.alpinelinux.org/wiki/KVM#Provision_an_Alpine_Linux_vm_with_virt-install

Install command:

```cmd
virt-install \
-n TestBukkitVM \
--os-variant=alpinelinux3.19 \
--cdrom="resources/alpine-virt-3.20.3-x86.iso" \
--ram=1024 \
--vcpus=2 \
--graphics none \
--console pty,target_type=serial
```

## Setup alpine on VM

- Snapshot name:"Login" description:"Logged in to machine"
- https://wiki.alpinelinux.org/wiki/Installation `setup-alpine`
    - Allow ssh and give root password "sshPassword"
        - TODO: Maybe uncessary, do we need ssh? Guest agent can run commands and transfer files
    - Include community apk repositories (for qemu guest agent)
- Install QEMU agent https://wiki.libvirt.org/Qemu_guest_agent.html

```
apk add qemu-guest-agent && rc-service qemu-guest-agent start
```

- Create snapshot name: `Setup` description: `Finished OS configuration`
- Use the `me.willkroboth.testbukkitvm.Main#finishMachineSetup` function to automatically finish setup
    - Now that we have manually configured the guest agent we can run commands and upload files from Java
