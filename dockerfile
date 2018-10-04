FROM openjdk:8-jre-alpine

RUN adduser -D -g '' sftpbeamer
USER sftpbeamer

#Set the path for jar file in the container
ENV SFTPBEAMER_HOME /home/sftpbeamer
WORKDIR $SFTPBEAMER_HOME


ENV SFTPBEAMER_JAR sftpbeamer.jar


EXPOSE 8080

#Copy the jar to the container
COPY target/$SFTPBEAMER_JAR $SFTPBEAMER_HOME/

#Copy the config files to the container
COPY target/classes/app.info.json $SFTPBEAMER_HOME/
COPY target/classes/sftp.beamer.properties $SFTPBEAMER_HOME/

#Launch the service
CMD java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -server $SFTPBEAMER_JAR