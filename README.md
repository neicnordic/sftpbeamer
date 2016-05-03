SFTP Beamer
===========

SFTP Beamer is an open source Web application for transferring ("beaming") over files between
two SFTP servers, some times referred to as "Managed File Transfer" or "MFT".

**Note:** SFTP Beamer is still in development. The basic functionality is there
and works, but it is still a bit rough around the edges, and we are currently
working on improving stability and security. Contributions and issue reports
are very welcome!

SFTP Beamer is built on [Java](https://www.java.com/) 8,
[Vert.x](http://vertx.io/) 3.2.1, [JQuery](https://jquery.com/),
[Bootstrap](http://getbootstrap.com/), and a bunch of other libraries.

Screenshot
----------
![Screenshot of the SFTP beamer dashboard, logged in to two servers](http://i.imgur.com/5q2nUSk.png)

A Screenshot of the SFTP beamer dashboard, logged in to two servers.

Getting started
---------------

Requirements:

- Java 8
- Maven 3

Clone the repository:

```bash
git clone https://github.com/neicnordic/sftpbeamer.git
cd sftpbeamer
```

Edit the config property:
```bash
cp src/main/resources/sftp.beamer.properties
```

Edit the log4j config file:
```bash
cp src/main/resources/log4j2.xml
```

Optional: Create the hostinfo file:
```bash
cp src/main/resources/webroot/static/js/hostinfo.json{.template,}
```
(Edit the file manually to your liking!)

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

Copyright
-------
[Nordic e-Infrastructures Collaboration (NeIC)](http://neic.nordforsk.org)

License
-------
MIT (see the [LICENSE file](https://github.com/neicnordic/sftpbeamer/blob/master/LICENSE) for more info)

Contributors
------------
- [Xiaxi Li](http://github.com/xiaxi-li)
- [Johan Viklund](http://github.com/viklund)
- [Samuel Lampa](http://github.com/samuell)