FROM alpine/git as clone
WORKDIR /app
RUN git clone https://github.com/neicnordic/sftpbeamer.git

FROM maven:3.5-jdk-8-alpine as build
WORKDIR /app
COPY --from=clone /app/sftpbeamer /app
RUN mvn package

FROM openjdk:8-jre-alpine
MAINTAINER Xiaxi Li <xiaxi.li@uib.no>

ENV SFTPBEAMER_HOME /home/sftpbeamer
ENV SFTPBEAMER_JAR sftpbeamer.jar

RUN adduser -D -g '' sftpbeamer
USER sftpbeamer
WORKDIR ${SFTPBEAMER_HOME}

EXPOSE 8080

RUN mkdir ${SFTPBEAMER_HOME}/conf && mkdir ${SFTPBEAMER_HOME}/logs

#Copy the jar to the container
COPY --from=build /app/target/${SFTPBEAMER_JAR} ${SFTPBEAMER_HOME}/

#Launch the service
ENTRYPOINT ["sh", "-c"]
CMD ["java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -server ${SFTPBEAMER_JAR}"]