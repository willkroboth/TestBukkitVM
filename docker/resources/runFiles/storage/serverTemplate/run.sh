set -e
cd "${0%/*}"

# TODO: Paper tells me off for running the server as root, which is fair
#  I suppose it would be good to create a user that can only access the server files to run this
#  https://madelinemiller.dev/blog/root-minecraft-server/#how-to-prevent-it
java -Xmx1024M -Xms1024M -jar server.jar nogui
