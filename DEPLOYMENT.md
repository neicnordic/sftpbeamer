Before building the code, please make sure the java and maven are installed. For the specific version, please refer to [requirements](https://github.com/neicnordic/sftpbeamer/blob/master/requirements.txt).

Clone the repository:
```bash
git clone https://github.com/neicnordic/sftpbeamer.git
cd sftpbeamer
```
Edit the config property:
```bash
src/main/resources/sftp.beamer.properties
```
Edit the log4j2 config file:
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

For deploying this web application, we put [nginx](https://nginx.org/) in front of it as a http proxy server, and config https. The service can be deployed in any Linux distributions, in which Java and nginx can be installed. But the testing environment we set up is based on CentOS 6.5.

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

Edit sftp.beamer.properties:
```
host=localhost
http_verticle_port=8080
ssl=false
http_verticle_instance_number=1
worker_thread_pool_size=20
```
Edit app.info.json:
```
{
  "server": {
    "name": "Domain Name",
    "ws_port": 443,
    "ssl": true
  },
  "hosts": {
    "host1": "Default SFTP/SSH HOST1",
    "host2": "Default SFTP/SSH HOST2"
  },
  "loginmodes": {
    "HOST1": "otp",
    "HOST2": "pw"
  }
}
```
Directly run the sftpbeamer.jar in the background:
```
nohup java -jar -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.Log4j2LogDelegateFactory sftpbeamer.jar &
```
Check the log under ./logs

Access the SFTP Beamer:
```
https://domain_name
```
