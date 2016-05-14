#SFTP Beamer
SFTP Beamer is an open source web application, which is able to facilitate users
to manipulate files between two SFTP/SSH servers.

**Note:** SFTP Beamer is still in development. The basic functionality is there
and works, but it is still a bit rough around the edges, and we are currently
working on improving stability and security. Contributions and issue reports
are very welcome!

##Table of Contents
1. [Introduction](#introduction)
1. [Functionality](#functionality)
1. [Getting Started](#start)
1. [Deployment](#deployment)
1. [Copyright](#copyright)
1. [License](#license)
1. [Contributors](#contributors)

##Introduction <a name="introduction"></a>
The SFTP Beamer is initially motivated by the [Tryggve](https://wiki.neic.no/wiki/Tryggve) project. One of missions Tryggve project has is to help users easily use the existing services at the different Nordic countries. In Norway and Sweden, both of them have their own secure service for sensitive data. The built-in approach of importing/exporting data to/from the service is primitive and needs much manual labour, especially when manipulating data between the two services. In order to simplify this kind of task and make it user friendly, the SFTP Beamer comes out.

SFTP Beamer is built on Java 8, Vert.x 3.2.1, JQuery, Bootstrap, and a bunch of other libraries. The following image shows how SFTP Beamer links a user with the two SFTP/SSH servers.

![How SFTP Beamer links a user with the two SFTP/SSH servers](http://i.imgur.com/EXBqhpZ.png)

##Functionality <a name="functionality"></a>
- Connect to SFTP/SSH server
- Navigate the directory hierarchy
- Upload local files to SFTP/SSH server
- Transfer files/folders between two SFTP/SSH servers
- Delete files/folders in the SFTP/SSH server

##Getting Started <a name="start"></a>
Prerequisite:
- Java 8
- Maven 3

Clone the repository:
```bash
git clone https://github.com/neicnordic/sftpbeamer.git
cd sftpbeamer
```
Edit the config property:
```bash
src/main/resources/sftp.beamer.properties
```
Edit the log4j config file:
```bash
src/main/resources/log4j2.xml
```
Create the app.info.json file and edit:
```bash
src/main/resources/app.info.json
```
Build code:
```
mvn clean package
```
Run application:
```
cd target
java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4jLogDelegateFactory sftpbeamer.jar
```
Navigate to the SFTP Beamer dashboard:
- http://localhost:8080

##Deployment <a name="deployment"></a>
For deploying this web application, we put nginx in front of it as a http proxy server, and config https.

After installing nginx, edit nginx.conf:
```
server {
        listen 443;
        server_name Domain_Name;
        charset utf-8;

        ssl on;
        ssl_certificate /etc/nginx/ssl/nginx.crt;
        ssl_certificate_key /etc/nginx/ssl/nginx.key;

        location / {
           proxy_pass http://localhost:8080;
        }

	      location ^~ /sftp {
            proxy_pass http://localhost:8080;
        }

        location /upload {
            proxy_pass http://localhost:8082;
        }

        location /ws {
       	    proxy_pass http://localhost:8081;
       	    proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
        }
    }
```
Put sftp.beamer.properties and app.info.json next to sftpbeamer.jar.

Edit sftp.beamer.properties:
```
host=localhost
http_verticle_port=8080
upload_verticle_port=8082
websocket_verticle_port=8081
ssl=false
```
Edit app.info.json:
```
{
  "server": {
    "name": "Domain Name",
    "ws_port": 443,
    "upload_port": 443,
    "ssl": true
  },
  "hosts": {
    "host1": "SFTP/SSH HOST1",
    "host2": "SFTP/SSH HOST2"
  },
  "loginmodes": {
    "HOST1": "otp",
    "HOST2": "pw"
  }
}
```
Directly run the sftpbeamer.jar in the background:
```
nohup java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4jLogDelegateFactory sftpbeamer.jar &
```
Access the SFTP Beamer:
```
https://domain_name
```

##Copyright <a name="copyright"></a>
[Nordic e-Infrastructures Collaboration (NeIC)](http://neic.nordforsk.org)

##License <a name="license"></a>
MIT (see the [LICENSE file](https://github.com/neicnordic/sftpbeamer/blob/master/LICENSE) for more info)

##Contributors <a name="contributors"></a>
- [Xiaxi Li](http://github.com/xiaxi-li)
- [Johan Viklund](http://github.com/viklund)
- [Samuel Lampa](http://github.com/samuell)
