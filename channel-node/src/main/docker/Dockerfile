FROM openjdk:8u141-jre
RUN mkdir /var/papyrus && mkdir /var/papyrus/channel-node && mkdir /var/papyrus/channel-node/bin

COPY files /var/papyrus/channel-node
EXPOSE 8080
ENTRYPOINT [\
"java",\
"-cp",\
"bin/channel-node.jar:bin/libs/*"]
CMD [\
"papyrus.channel.node.ChannelNodeApplication"]
WORKDIR /var/papyrus/channel-node
COPY jars /var/papyrus/channel-node/bin/
