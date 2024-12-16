# openjdk21-jre - Running java server
# ncurses - Paper server asks for infocmp
# libstdc++ - Paper spark wants this https://spark.lucko.me/docs/misc/Using-async-profiler
apk add \
 openjdk21-jre \
 ncurses \
 libstdc++

# Paper server asks for udev
setup-devd udev