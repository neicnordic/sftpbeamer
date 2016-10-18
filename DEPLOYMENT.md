Clone the repository:
```bash
git clone https://github.com/neicnordic/sftpbeamer.git
cd sftpbeamer
```
Edit the config property, this config file contains some parameters, which will be used by backend. You have to give correct value before you start the service.
```bash
src/main/resources/sftp.beamer.properties
```

```
#The host name or ip address of a server where this service will be running.
host=localhost
#The port this service will listen to.
http_verticle_port=8080
#Represents the number of http verticle instances, usually it could be equal to the number of cpu cores.
http_verticle_instance_number=1
#Represents the size of thread pool, a thread from this pool is usually used to execute the blocking task. 20 is the recommended default value.
worker_thread_pool_size=20
```

Edit the log4j2 config file, this file is used by log4j2. You can use this directly as default. For more detailed info, please refer to this [site](http://logging.apache.org/log4j/2.x/).
```bash
src/main/resources/log4j2.xml
```

Create an app.info.json file based on app.info.json.template file, this config file contains some parameters, which will be loaded once there is a user accessing the SFTP Beamer service.
```bash
src/main/resources/app.info.json.template
```

```
{
  "server": {
    "name": "Domain Name", #The domain name of a server where the SFTP Beamer is running
    "ws_port": 443, #The port for web socket connection. if ssl is true, this should be 443. Otherwise, this should be 80.
    "ssl": true
  },
  "hosts": {  #The host1 and host2 represent two host name of default SFTP/SSH servers, with which you are going to connect. 
    "host1": "Default SFTP/SSH HOST1",
    "host2": "Default SFTP/SSH HOST2"
  },
  "loginmodes": {   #otp means you are going to user one-time password as the second step authentication to connect. pw means you are going to use regular username/password to connect. HOST1 and HOST2 are the domain names above.
    "HOST1": "otp",
    "HOST2": "pw"
  }
}
```

Before building code, use the following command to install Java Development Kit and follow this [doc](http://maven.apache.org/install.html) to install Maven. For the specific version, please refer to [requirements](https://github.com/neicnordic/sftpbeamer/blob/master/requirements.txt).
```
yum install java-1.8.0-openjdk-devel.x86_64

```

Build code:
```
mvn clean package
```

Find the sfpbeamer.jar under target folder.

The SFTP Beamer can be deployed in any Linux distributions. But the testing environment we set up is based on CentOS 6.5.

We put [nginx](https://nginx.org/) in front of the SFTP Beamer as a http proxy server, and config https. About how to install nginx in Linux, please follow this [doc](https://www.nginx.com/resources/wiki/start/topics/tutorials/install/).

After installing nginx, edit nginx.conf:
```
server {
        listen 443;
        server_name domain_name;
        charset utf-8;

        ssl on;
        ssl_certificate /etc/nginx/ssl/nginx.crt;
        ssl_certificate_key /etc/nginx/ssl/nginx.key;

        location / {
            proxy_pass http://localhost:8080;
        }
        
        location = /sftp/ws {
            proxy_pass http://localhost:8080;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
        }
        
        location ^~ /sftp {
            proxy_pass http://localhost:8080;
        }
    }
```
Put sftp.beamer.properties and app.info.json next to sftpbeamer.jar.

Directly run the sftpbeamer.jar in the background:
```
nohup java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory sftpbeamer.jar &
```
Check the log under ./logs

Access the SFTP Beamer:
```
https://domain_name
```
