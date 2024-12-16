# Re-detect the VM's NIC MAC address
#  Since we are likely restoring a snapshot created on a different machine,
#  the MAC address of the NIC has likely changed
modprobe -r virtio_net
modprobe virtio_net

# Restart networking
#  udhcpc will use the NIC MAC address to identify this client
#  We needed to detect the new NIC MAC address so the dhcp
#  server on the host gives us a unique ip address
/etc/init.d/networking restart