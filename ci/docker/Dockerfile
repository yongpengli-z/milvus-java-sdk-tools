FROM openjdk:11

COPY target/milvus-java-sdk-toos-1.0.jar  app.jar

ARG REVERSION=""
ARG GIT_REPO=""
ARG GIT_BRANCH=""
ARG GIT_COMMIT_HASH=""
LABEL GIT_COMMIT_HASH=${GIT_COMMIT_HASH}  GIT_BRANCH=${GIT_BRANCH} GIT_REPO=${GIT_REPO}

RUN bash -c 'touch /app.jar'
EXPOSE 9001
ENV JAVA_OPTS="\
-server \
-Dnetworkaddress.cache.ttl=60  \
-Dsun.net.inetaddr.ttl=60 \
-Xmx1g \
-Xms1g \
-Xmn256m \
-XX:SurvivorRatio=8 \
-XX:MetaspaceSize=128m \
-XX:MaxMetaspaceSize=256m \
-XX:+UseParallelGC \
-XX:ParallelGCThreads=4 \
-XX:+UseParallelOldGC \
-XX:+UseAdaptiveSizePolicy \
-XX:+PrintGCDetails \
-XX:+PrintTenuringDistribution \
-XX:+PrintGCTimeStamps \
-XX:+HeapDumpOnOutOfMemoryError \
-XX:HeapDumpPath=/ \
-Xloggc:/gc.log \
-XX:+UseGCLogFileRotation \
-XX:NumberOfGCLogFiles=5 \
-XX:GCLogFileSize=10M"

ENTRYPOINT java ${JAVA_OPTS} -Djava.security.egd=file:/dev/./urandom -jar /app.jar