FROM openjdk:8-jre-alpine

RUN useradd -ms /bin/bash sftpbeamer
USER sftpbeamer
WORKDIR $SFTPBEAMER_HOME


ENV SFTPBEAMER_JAR sftpbeamer.jar

#Set the path for jar file in the container
ENV SFTPBEAMER_HOME /home/sftpbeamer

EXPOSE 8080

#Copy the jar to the container
COPY target/$SFTPBEAMER_JAR $SFTPBEAMER_HOME/

#Copy the config files to the container
COPY target/classes/app.info.json $SFTPBEAMER_HOME/
COPY target/classes/sftp.bearmer.properties $SFTPBEAMER_HOME/

#Launch the service
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory -server -jar $SFTPBEAMER_JAR"]
