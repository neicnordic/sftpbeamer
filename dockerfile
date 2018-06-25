FROM openjdk:8-jre-alpine
ENV SFTPBEAMER_JAR sftpbeamer.jar

#Set the path for jar file in the container
ENV SFTPBEAMER_HOME /home/sftpbeamer

EXPOSE 8080

#Copy the jar to the container
COPY target/$SFTPBEAMER_JAR $SFTPBEAMER_HOME/

#Launch the service
WORKDIR $SFTPBEAMER_HOME
ENTRYPOINT ["sh", "-c"]
CMD ["exec java -jar $SFTPBEAMER_JAR"]
