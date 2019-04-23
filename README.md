# SFTP Beamer
[![Build Status](https://jenkins.norgene.no/buildStatus/icon?job=SFTP+Beamer%2Ffeature%252Fjenkinsfile)](https://jenkins.norgene.no/job/SFTP%20Beamer/job/feature%252Fjenkinsfile/)

SFTP Beamer is an open source web application, which is able to facilitate users
to manipulate files between two SFTP/SSH servers.

**Note:** SFTP Beamer is still in development. The basic functionality is there
and works, but it is still a bit rough around the edges, and we are currently
working on improving stability and security. Contributions and issue reports
are very welcome!

## Table of Contents
1. [Introduction](#introduction)
1. [Functionality](#functionality)
1. [System Overview](#overview)
1. [Security Considerations](#security)
1. [Development](#development)
1. [Deployment](#deployment)
1. [Docker Support](#docker)
1. [Copyright](#copyright)
1. [License](#license)
1. [Contributors](#contributors)

## Introduction <a name="introduction"></a>
The SFTP Beamer is initially motivated by the [Tryggve](https://wiki.neic.no/wiki/Tryggve) project. One of missions Tryggve project has is to help users easily use the existing services at the different Nordic countries. In Norway and Sweden, both of them have their own secure service for sensitive data. The built-in approach of importing/exporting data to/from the service is primitive and needs much manual labour, especially when manipulating data between the two services. In order to simplify this kind of task and make it user friendly, the SFTP Beamer comes out. But SFTP Beamer is not only designed to adapt for the secure services in Norway and Sweden, but also for a general server, which allows SFTP/SSH connection.

## Functionality <a name="functionality"></a>
The SFTP Beamer provides the following functions. After having connected to a server, right-click on the content area to show a functionality menu. For transfer, delete and rename functions, you have to left-click to select item(s) first. Besides, you are allowed to download a file or a folder as a zip file.

![Functionality menu](http://i.imgur.com/nhbVjSq.png)

- Connect to two SFTP/SSH servers at the same time
- Navigate the directory hierarchy
- Upload multiple local files to SFTP/SSH server
- Transfer multiple files/folders between two SFTP/SSH servers
- Send email notification when data transfer is done
- Delete multiple files/folders 
- Rename a file or folder
- Create a folder
- Download a file
- Download a folder as a zip file

## System Overview <a name="overview"></a>
In a word, SFTP Beamer as a web application is a proxy linking two remote SFTP/SSH servers.

The following image show how a user is using SFTP Beamer to interact with the two remote SFTP/SSH servers.

![How SFTP Beamer links a user with the two SFTP/SSH servers](http://i.imgur.com/EXBqhpZ.png)

## Security Considerations <a name="security"></a>
Because one of purposes SFTP Beamer has is to transfer sensitive data through secure service, how to make this system much more security is very important. So far, there has been several applied features to secure the system.

- The SFTP Beamer is using https.
- The SFTP Beamer never keeps the credential a user is using to connect to a SFTP/SSH server.
- The SFTP Beamer never caches the downloaded, uploaded or transferred data. The data will only pass by the memory of server where SFTP Beamer is running. 
- The SFTP Beamer is using session id to distinguish the different SFTP/SSH connections kept in the memory.

## Development <a name="development"></a>
The frontend of SFTP Beamer is based on JQuery, Bootstrap and several JQuery plugins, and the backend is developed by java and based on Vert.x framework. Besides, it's using Maven as a build tool. For the specific version requirement, please refer to [requirements](https://github.com/neicnordic/sftpbeamer/blob/master/requirements.txt).

## Deployment <a name="deployment"></a>
Please refer to [deployment guideline](https://github.com/neicnordic/sftpbeamer/blob/master/DEPLOYMENT.md) for more info.

## Docker Support <a name="docker"></a>
Now, we support dockerizing our application. Pull the dockerfile to a docker host, and run the following command to create a docker image.
```
docker build -f dockerfile -t sftpbeamer .
```
After having a docker image, launch a docker container by running the following command. You need to mount two host's directories to container. In the /home/sftpbeamer/conf, you need to provide the customized app.info.json and sftp.beamer.properties files. Besides, you need to provide a hostname for the container, and this hostname should be set in the sftp.beamer.properties file.
```
docker run  -d --mount type=bind,source=/host/path,target=/home/sftpbeamer/conf --mount type=bind,source=/host/path,target=/home/sftpbeamer/logs -h container.sftpbeamer -p 80:8080 sftpbeamer
```

## Copyright <a name="copyright"></a>
[Nordic e-Infrastructures Collaboration (NeIC)](http://neic.nordforsk.org)

## License <a name="license"></a>
MIT (see the [LICENSE file](https://github.com/neicnordic/sftpbeamer/blob/master/LICENSE) for more info)

## Contributors <a name="contributors"></a>
- [Xiaxi Li](http://github.com/xiaxi-li)
- [Johan Viklund](http://github.com/viklund)
- [Samuel Lampa](http://github.com/samuell)
